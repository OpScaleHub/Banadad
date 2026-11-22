package com.github.opscalehub.dong.data

import com.github.opscalehub.dong.data.db.CategoryEntity
import com.github.opscalehub.dong.domain.allocation.AllocationPolicy
import com.github.opscalehub.dong.domain.allocation.AllocationResult
import com.github.opscalehub.dong.domain.ledger.AccountKind
import com.github.opscalehub.dong.domain.ledger.EntryType
import com.github.opscalehub.dong.domain.ledger.LedgerException
import com.github.opscalehub.dong.domain.ledger.PostingRules
import com.github.opscalehub.dong.domain.period.ChargeLine
import com.github.opscalehub.dong.domain.period.SourceType
import com.github.opscalehub.dong.i18n.Fmt

/** Name of the hidden expense type that carries special charges. */
const val SPECIAL_CHARGE = "شارژ فوق‌العاده"

/** One special charge as issued; [cancelled] when it was reversed. */
data class SpecialCharge(val entryId: String, val date: Long, val title: String, val total: Long, val shares: Map<String, Long>, val cancelled: Boolean)

/**
 * «شارژ فوق‌العاده» (an owners'-association special assessment): a one-off amount the units owe on
 * top of the monthly charge, for a big or unexpected job (an elevator repair, repainting), split by
 * a chosen basis. Units owe it from the day it is issued, so it is on their balance, bills and
 * reminders at once; the job itself is then recorded as an ordinary bill paid from the fund.
 * Posted as a CHARGE outside any month (periodId = null) under its own hidden type, so month
 * closing and month bills never count it twice. Cancelling reverses it (the journal keeps both).
 */
class SpecialChargeRepository(private val r: Repos) {

    /** The hidden «شارژ فوق‌العاده» type, created on first use. */
    suspend fun categoryId(buildingId: String, create: Boolean = false): String? {
        val exp = r.db.expenseDao()
        exp.categories(buildingId).firstOrNull { !it.active && it.name == SPECIAL_CHARGE }?.let { return it.id }
        if (!create) return null
        val c = CategoryEntity(newId(), buildingId, SPECIAL_CHARGE, AllocationPolicy.FUND_ONLY.encode(), r.finance.operatingFundId(buildingId),
            active = false, sortOrder = (exp.categories(buildingId).maxOfOrNull { it.sortOrder } ?: 0) + 1)
        exp.upsertCategory(c)
        return c.id
    }

    /** Who would owe what, with the same context as an expense split today (open month, else this month). */
    suspend fun preview(buildingId: String, amount: Long, policy: AllocationPolicy): AllocationResult =
        r.expenses.previewAllocation(buildingId, r.periods.draftFor(buildingId, r.clock.today())?.id, amount, policy)

    suspend fun issue(buildingId: String, title: String, amount: Long, policy: AllocationPolicy): String = r.tx {
        if (title.isBlank()) fail(OpError.INVALID_INPUT, "title")
        if (amount <= 0) fail(OpError.INVALID_AMOUNT)
        if (policy.fundOnly || policy.components.isEmpty()) fail(OpError.INVALID_INPUT, "policy")
        val alloc = preview(buildingId, amount, policy)
        if (!alloc.valid || alloc.allocatedTotal != amount) fail(OpError.INVALID_INPUT, "allocation")
        val cat = categoryId(buildingId, create = true)!!
        val fund = r.finance.operatingFundId(buildingId)
        val lines = alloc.shares.filterValues { it != 0L }.map { (unit, share) -> ChargeLine(unit, SourceType.CHARGE_ITEM, SPECIAL_CHARGE, cat, fund, share) }
        val postings = try { PostingRules.charges(lines) } catch (e: LedgerException) { fail(OpError.INVALID_AMOUNT, e.message ?: "") }
        val id = r.journal.post(buildingId, EntryType.CHARGE, r.clock.today(), postings, title.trim())
        r.journal.audit(buildingId, "ISSUE_SPECIAL_CHARGE", "journal_entry", id, "${title.trim()}: ${Fmt.money(amount)}")
        id
    }

    /** Issued special charges, newest first. */
    suspend fun list(buildingId: String): List<SpecialCharge> {
        val cat = categoryId(buildingId) ?: return emptyList()
        val fin = r.db.financeDao()
        val entries = fin.entries(buildingId)
        val reversed = entries.mapNotNull { it.reversesEntryId }.toSet()
        return entries.filter { it.type == EntryType.CHARGE && it.periodId == null }.mapNotNull { e ->
            val lines = fin.linesOf(e.id).filter { it.account == AccountKind.RECEIVABLE && it.categoryId == cat && it.unitId != null }
            if (lines.isEmpty()) null
            else SpecialCharge(e.id, e.entryDate, e.memo, lines.sumOf { it.amountMinor }, lines.groupBy { it.unitId!! }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }, e.id in reversed)
        }.sortedByDescending { it.date }
    }

    /** Takes a special charge back (e.g. issued by mistake): a reversal dated today, with a reason. */
    suspend fun cancel(buildingId: String, entryId: String, reason: String): String = r.tx {
        if (reason.isBlank()) fail(OpError.REASON_REQUIRED)
        val sc = list(buildingId).firstOrNull { it.entryId == entryId } ?: fail(OpError.NOT_FOUND)
        if (sc.cancelled) fail(OpError.ALREADY_REVERSED)
        val fin = r.db.financeDao()
        r.journal.post(buildingId, EntryType.REVERSAL, r.clock.today(), PostingRules.reversal(fin.linesOf(entryId).map { it.toPosting() }),
            "ابطال $SPECIAL_CHARGE «${sc.title}»: $reason", reversesEntryId = entryId)
    }
}
