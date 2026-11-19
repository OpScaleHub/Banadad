package com.github.opscalehub.dong.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.opscalehub.dong.data.db.DongDatabase
import com.github.opscalehub.dong.data.db.ExpenseEntity
import com.github.opscalehub.dong.data.db.ExpenseStatus
import com.github.opscalehub.dong.data.db.FundKind
import com.github.opscalehub.dong.data.db.PeriodStatus
import com.github.opscalehub.dong.domain.allocation.AllocationPolicy
import com.github.opscalehub.dong.domain.calendar.Jalali
import com.github.opscalehub.dong.domain.ledger.EntryType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * Fuzzing the bookkeeping: random sequences of what a manager can do (in any order, with odd
 * amounts and dates, including things the app should refuse), with the books checked after every
 * step. Each run is reproducible from its seed; a failure names the seed, the step and the action.
 *
 * Properties:
 *  - every action either succeeds or is refused with an [OpException] (no other exception);
 *  - a refused action changes nothing (same journal, same periods);
 *  - after every step [SelfCheck] is clean: the ledger balances, every unit statement builds and
 *    equals its balance, every bill asks for the balance;
 *  - money is conserved: cash = opening cash + payments − refunds − supplier payments (net of
 *    reversals), computed independently from the journal entry types.
 */
@RunWith(AndroidJUnit4::class)
class WorkflowFuzzTest {

    @Test fun `random manager sessions keep the books consistent`() = runBlocking {
        val seeds = (System.getProperty("fuzz.seeds")?.toIntOrNull() ?: 24)
        val steps = (System.getProperty("fuzz.steps")?.toIntOrNull() ?: 60)
        val from = System.getProperty("fuzz.from")?.toIntOrNull() ?: 1
        for (seed in from until from + seeds) run(seed, steps)
        // What the fuzzer exercised, for the test output (an action that is always refused tests nothing).
        println(tally.groupingBy { it }.eachCount().toSortedMap().entries.joinToString("\n") { "FUZZ ${it.key} ×${it.value}" })
    }

    private val tally = mutableListOf<String>()

    private suspend fun run(seed: Int, steps: Int) {
        val rnd = Random(seed)
        val db = DongDatabase.build(ApplicationProvider.getApplicationContext(), null)
        val clock = FixedClock(Jalali.toEpochDay(1405, 1 + rnd.nextInt(12), 1 + rnd.nextInt(29)))
        val r = Repos(db, clock)
        try {
            val prepaid = rnd.nextBoolean()
            val units = List(2 + rnd.nextInt(5)) { BuildingRepository.UnitDraft("${it + 1}", 1 + it / 2, 5_000 + rnd.nextLong(10_000), rnd.nextInt(5)) }
            val plan = if (prepaid) BuildingRepository.ChargePlan.Fixed(perUnit = 1_000_000L * (1 + rnd.nextInt(5)), perPerson = if (rnd.nextBoolean()) 200_000 else 0)
            else BuildingRepository.ChargePlan.SplitBills
            val b = r.buildings.setup("ساختمان", "", true, 1000, units, rnd.nextLong(0, 100_000_000), 0, plan)
            if (db.periodDao().periods(b).isEmpty()) { val m = SampleData.month(clock.today()); r.periods.create(b, m.name, m.start, m.end) }
            val unitIds = db.buildingDao().units(b).map { it.id }
            val cash = db.financeDao().cashAccounts(b).first().id
            val op = db.financeDao().funds(b).first { it.kind == FundKind.OPERATING }.id
            val log = mutableListOf<String>()

            fun amount() = when (rnd.nextInt(6)) { 0 -> 0L; 1 -> -rnd.nextLong(1, 1_000_000); 2 -> 1L; else -> rnd.nextLong(1, 20_000_000) }
            fun unit() = unitIds.random(rnd)
            suspend fun draft() = db.periodDao().periods(b).filter { it.status == PeriodStatus.DRAFT }.randomOrNull(rnd)
            suspend fun closed() = db.periodDao().periods(b).filter { it.status == PeriodStatus.FINALIZED }.randomOrNull(rnd)
            fun day() = clock.today() - rnd.nextLong(0, 40)

            val actions: List<Pair<String, suspend () -> Unit>> = listOf(
                "payment" to { r.finance.receivePayment(b, unit(), amount(), cash, day()) },
                "refund" to { r.finance.refund(b, unit(), amount(), cash, day(), "بازگشت") },
                "adjust" to { r.finance.adjust(b, unit(), amount(), op, null, day(), if (rnd.nextInt(5) == 0) "" else "اصلاح") },
                "opening" to { r.finance.setUnitOpening(b, unit(), amount() * (if (rnd.nextBoolean()) 1 else -1)) },
                "people" to { r.buildings.setOccupancy(unit(), day(), rnd.nextInt(-1, 7)) },
                "undo" to {
                    val e = db.financeDao().entries(b).filter { it.type != EntryType.REVERSAL }.randomOrNull(rnd)
                    if (e != null) r.finance.reverse(e.id, if (rnd.nextInt(6) == 0) "" else "اشتباه")
                },
                "expense" to {
                    val cat = db.expenseDao().categories(b).filter { it.active }.random(rnd)
                    val per = draft()
                    val e = ExpenseEntity(newId(), b, per?.id, cat.id, cat.defaultFundId ?: op, null, per?.let { it.startDate + rnd.nextLong(0, 28) } ?: day(),
                        "هزینه", amount(), policyJson = AllocationPolicy.decode(cat.defaultPolicyJson).encode(), createdAt = 0)
                    r.expenses.create(e, if (rnd.nextBoolean()) cash else null)
                },
                "pay supplier" to {
                    val e = db.expenseDao().expenses(b).filter { it.status == ExpenseStatus.ACTIVE }.randomOrNull(rnd)
                    if (e != null) r.expenses.paySupplier(e.id, if (rnd.nextBoolean()) e.amountMinor - r.expenses.paid(e.id) else amount(), cash, day())
                },
                "void expense" to {
                    val e = db.expenseDao().expenses(b).randomOrNull(rnd)
                    if (e != null) r.expenses.void(e.id, "ابطال")
                },
                "close month" to { draft()?.let { r.periods.finalize(it.id) } },
                "new month" to {
                    val last = db.periodDao().periods(b).maxOfOrNull { it.endDate }
                    val m = if (last != null) SampleData.monthAfter(last) else SampleData.month(clock.today())
                    r.periods.create(b, m.name, m.start, m.end)
                },
                "edit month" to {
                    draft()?.let { p ->
                        val s = p.startDate + rnd.nextLong(-3, 4)
                        r.periods.updateDraft(p.copy(startDate = s, endDate = maxOf(s, p.endDate + rnd.nextLong(-3, 4)), dueDate = if (rnd.nextBoolean()) null else s + rnd.nextLong(0, 30)))
                    }
                },
                "delete month" to { draft()?.let { r.periods.deleteDraft(it.id) } },
                "correct closed month" to { closed()?.let { r.periods.applyCorrection(it.id, "اصلاحیه") } },
                "time passes" to { clock.day += rnd.nextLong(1, 40); r.periods.ensureCurrentMonth(b); r.periods.refreshAdvances(b) },
            )

            for (step in 1..steps) {
                val (name, action) = actions.random(rnd)
                val before = db.financeDao().entries(b).size to db.periodDao().periods(b)
                val outcome = try {
                    action(); "ok"
                } catch (e: OpException) {
                    // Refused: nothing may have changed.
                    assertEquals("seed $seed step $step $name refused (${e.code}) but changed the books\n${log.joinToString("\n")}",
                        before, db.financeDao().entries(b).size to db.periodDao().periods(b))
                    "refused ${e.code}"
                } catch (e: Throwable) {
                    throw AssertionError("seed $seed step $step: $name crashed\n${log.joinToString("\n")}", e)
                }
                log += "$step $name: $outcome"
                val h = SelfCheck.run(r, b)
                if (!h.ok) throw AssertionError("seed $seed step $step after $name: ${h.problems}\n${log.joinToString("\n")}")
                checkCash(r, b, cash, "seed $seed step $step after $name\n${log.joinToString("\n")}")
            }
            tally.addAll(log.map { it.substringAfter(' ') })
        } finally {
            db.close()
        }
    }

    /** Cash in the books equals what the cash-moving entries say, counted from their types alone. */
    private suspend fun checkCash(r: Repos, b: String, cash: String, where: String) {
        val fin = r.db.financeDao()
        val entries = fin.entries(b).associateBy { it.id }
        val cashLines = fin.lines(b).filter { it.cashAccountId == cash && it.account == com.github.opscalehub.dong.domain.ledger.AccountKind.CASH }
        fun kind(id: String): EntryType { val e = entries.getValue(id); return if (e.type == EntryType.REVERSAL) entries[e.reversesEntryId]?.type ?: e.type else e.type }
        val allowed = setOf(EntryType.OPENING, EntryType.RESIDENT_PAYMENT, EntryType.REFUND, EntryType.SUPPLIER_PAYMENT, EntryType.EXPENSE, EntryType.CASH_TRANSFER)
        val stray = cashLines.filter { kind(it.entryId) !in allowed }
        assertEquals("$where: cash moved by ${stray.map { kind(it.entryId) }}", emptyList<Any>(), stray)
        assertEquals("$where: cash balance", cashLines.sumOf { it.amountMinor }, r.finance.balances(b).cash[cash] ?: 0L)
    }
}
