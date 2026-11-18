package com.github.opscalehub.dong.data

import com.github.opscalehub.dong.data.db.PeriodStatus
import com.github.opscalehub.dong.domain.ledger.EntryType
import kotlinx.coroutines.CancellationException

/**
 * Result of [SelfCheck.run]. [problems] and [summary] hold codes and counts only (no names, labels
 * or amounts), so they can go in a problem report as they are.
 */
data class Health(val problems: List<String>, val summary: String) {
    val ok: Boolean get() = problems.isEmpty()
}

/**
 * The books' invariants, checked on the phone: the ledger balances, every unit's statement builds
 * and equals its balance, recent per-month statements build, and each unit's bill asks for its
 * balance. The same checks run after every step of WorkflowInvariantsTest. A failure means a bug,
 * not a user mistake; the app offers to send a report instead of showing wrong numbers silently.
 */
object SelfCheck {
    suspend fun run(r: Repos, buildingId: String): Health {
        val problems = mutableListOf<String>()
        suspend fun <T> attempt(what: String, block: suspend () -> T): T? = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            problems += "$what:${e.javaClass.simpleName}${Diagnostics.where(e)?.let { "@$it" } ?: ""}"
            null
        }
        val db = r.db
        val units = db.buildingDao().units(buildingId).sortedBy { it.sortOrder }
        val periods = db.periodDao().periods(buildingId).sortedBy { it.startDate }
        val entries = db.financeDao().entries(buildingId)

        // One report load gives balances and every unit's statement; if it fails, check piece by piece.
        val report = attempt("report") { ReportRepository(r).load(buildingId) }
        val bal = report?.balances ?: attempt("balances") { r.finance.balances(buildingId) }
        if (bal != null && !bal.reconciles) problems += "ledger-unbalanced"
        val billed = report?.let { lastBilled(it) }
        val current = periods.lastOrNull()
        units.forEachIndexed { i, u ->
            // Units are named by position, never by label.
            val tag = "unit${i + 1}"
            val s = report?.statements?.get(u.id) ?: attempt("$tag:statement") { r.finance.unitStatement(buildingId, u.id) }
            if (s != null && bal != null && s.closing != (bal.units[u.id] ?: 0L)) problems += "$tag:statement-mismatch"
            if (current != null) attempt("$tag:month-statement") { r.finance.unitStatement(buildingId, u.id, current.startDate, current.endDate) }
            if (report != null) {
                val bill = attempt("$tag:bill") { r.bills.bill(buildingId, u.id, billed?.id, report) }
                if (bill != null && s != null && bill.due != s.closing) problems += "$tag:bill-mismatch"
            }
        }

        val byType = entries.groupingBy { it.type }.eachCount()
        val summary = listOf(
            "units=${units.size}",
            "months=${periods.count { it.status == PeriodStatus.DRAFT }} open/${periods.count { it.status == PeriodStatus.FINALIZED }} closed",
            "prepaid=${attempt("prepaid") { r.periods.prepaid(buildingId) }}",
            "openingEditable=${attempt("opening") { r.finance.openingEditable(buildingId) }}",
            "entries=" + EntryType.entries.filter { (byType[it] ?: 0) > 0 }.joinToString(",") { "${it.name.lowercase()}:${byType[it]}" },
        ).joinToString(" ")
        return Health(problems.distinct(), summary)
    }
}

/**
 * Recent failures of screen computations, kept in memory only: exception type and the app source
 * line where it happened. Screens show "could not load" instead of a value; this lets a problem
 * report say why.
 */
object Diagnostics {
    private const val MAX = 10
    private val recent = ArrayDeque<String>()

    fun where(e: Throwable): String? = e.stackTrace.firstOrNull { it.className.startsWith("com.github.opscalehub.dong") }
        ?.let { "${it.fileName}:${it.lineNumber}" }

    @Synchronized fun note(e: Throwable) {
        val line = "${e.javaClass.simpleName}${where(e)?.let { "@$it" } ?: ""}"
        if (recent.lastOrNull() == line) return
        recent.addLast(line)
        while (recent.size > MAX) recent.removeFirst()
    }

    @Synchronized fun recent(): List<String> = recent.toList()
}
