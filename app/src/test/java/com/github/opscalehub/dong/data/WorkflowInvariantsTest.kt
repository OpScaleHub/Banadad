package com.github.opscalehub.dong.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.opscalehub.dong.data.db.DongDatabase
import com.github.opscalehub.dong.data.db.ExpenseEntity
import com.github.opscalehub.dong.data.db.FundKind
import com.github.opscalehub.dong.data.db.PeriodStatus
import com.github.opscalehub.dong.domain.allocation.AllocationPolicy
import com.github.opscalehub.dong.domain.calendar.Jalali
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A manager's real first weeks, step by step, with the books checked after every step: the ledger
 * balances, every unit's statement adds up and matches its balance, the report loads, and each unit's
 * bill can be built. Tester report (0.7.0): after deleting the open month and re-creating it, a unit's
 * debt from before the app showed nowhere (a cancelled advance charge broke every unit statement).
 */
@RunWith(AndroidJUnit4::class)
class WorkflowInvariantsTest {
    private val db = DongDatabase.build(ApplicationProvider.getApplicationContext(), null)
    private val clock = FixedClock(Jalali.toEpochDay(1405, 8, 5))
    private val r = Repos(db, clock)

    @After fun close() = db.close()

    private suspend fun check(b: String, step: String) {
        val bal = r.finance.balances(b)
        assertTrue("$step: ledger reconciles", bal.reconciles)
        val d = ReportRepository(r).load(b)
        val billed = lastBilled(d)
        for (u in db.buildingDao().units(b)) {
            val s = try { r.finance.unitStatement(b, u.id) } catch (e: IllegalStateException) { throw AssertionError("$step: unit ${u.label} statement", e) }
            assertEquals("$step: unit ${u.label} statement = balance", bal.units[u.id], s.closing)
            db.periodDao().periods(b).forEach { p -> r.finance.unitStatement(b, u.id, p.startDate, null) }
            val bill = r.bills.bill(b, u.id, billed?.id)
            assertEquals("$step: unit ${u.label} bill total", s.closing, bill.due)
        }
        // The same invariants, as the phone checks them.
        assertEquals("$step: self-check", emptyList<String>(), SelfCheck.run(r, b).problems)
    }

    @Test fun `first weeks of a fixed-charge building keep the books consistent`() = runBlocking {
        val drafts = listOf(BuildingRepository.UnitDraft("۱", 1, 6_000, 2), BuildingRepository.UnitDraft("۲", 1, 8_000, 3), BuildingRepository.UnitDraft("۳", 2, 8_000, 1))
        val b = r.buildings.setup("ساختمان", "", true, 1000, drafts, 50_000_000, 0, BuildingRepository.ChargePlan.Fixed(BuildingRepository.FixedBasis.PER_UNIT, 5_000_000))
        check(b, "setup")
        val (u1, u2, u3) = db.buildingDao().units(b).sortedBy { it.sortOrder }
        val cash = db.financeDao().cashAccounts(b).first().id
        val op = db.financeDao().funds(b).first { it.kind == FundKind.OPERATING }.id

        // The tester deleted the open month, entered a past debt, and made the month again.
        r.periods.deleteDraft(db.periodDao().periods(b).single().id)
        check(b, "month deleted")
        r.finance.setUnitOpening(b, u1.id, 3_000_000)
        check(b, "past debt without a month")
        val m = SampleData.month(clock.today())
        r.periods.create(b, m.name, m.start, m.end)
        check(b, "month made again")
        assertEquals(8_000_000L, r.finance.unitStatement(b, u1.id).closing)
        val bill = r.bills.bill(b, u1.id, db.periodDao().periods(b).single().id)
        assertEquals(3_000_000L, bill.previous)
        assertEquals(8_000_000L, bill.due)
        r.finance.setUnitOpening(b, u2.id, -1_000_000)
        check(b, "past credit with a month")

        // Day-to-day changes re-post the advance charge (cancel + new); payments, undo, corrections.
        r.buildings.setOccupancy(u3.id, m.start, 4)
        check(b, "occupancy changed")
        val pay = r.finance.receivePayment(b, u1.id, 2_000_000, cash, m.start + 3)
        check(b, "payment")
        r.finance.reverse(pay, "اشتباه")
        check(b, "payment undone")
        r.finance.receivePayment(b, u1.id, 8_000_000, cash, m.start + 4)
        r.finance.adjust(b, u3.id, 500_000, op, null, m.start + 5, "بدهی قبلی جاافتاده")
        check(b, "correction")
        r.finance.refund(b, u2.id, 1_000_000, cash, m.start + 6, "بازگشت پیش‌پرداخت")
        check(b, "refund")
        val cat = db.expenseDao().categories(b).first { it.active }
        val e = ExpenseEntity(newId(), b, db.periodDao().periods(b).single().id, cat.id, cat.defaultFundId!!, null, m.start + 7, "هزینه", 3_000_000,
            policyJson = AllocationPolicy.decode(cat.defaultPolicyJson).encode(), createdAt = 0)
        r.expenses.create(e, cash)
        check(b, "expense")
        r.expenses.void(e.id, "اشتباه")
        check(b, "expense cancelled")

        // Close the month, move to the next one (made and charged by itself), correct after closing.
        r.periods.finalize(db.periodDao().periods(b).single().id)
        check(b, "month closed")
        clock.day = m.end + 1
        r.periods.ensureCurrentMonth(b)
        check(b, "next month")
        assertEquals(2, db.periodDao().periods(b).size)
        assertEquals(1, db.periodDao().periods(b).count { it.status == PeriodStatus.DRAFT })
        r.finance.adjust(b, u2.id, 1_200_000, op, null, clock.day, "بدهی قبلی پس از بستن ماه")
        check(b, "correction after closing")
        r.periods.deleteDraft(db.periodDao().periods(b).first { it.status == PeriodStatus.DRAFT }.id)
        check(b, "next month deleted")
    }

    @Test fun `self-check reports a broken ledger by codes only, without names or amounts`() = runBlocking {
        val drafts = listOf(BuildingRepository.UnitDraft("واحد رضایی", 1, 6_000, 2))
        val b = r.buildings.setup("ساختمان", "", true, 1000, drafts, 50_000_000, 0, BuildingRepository.ChargePlan.Fixed(BuildingRepository.FixedBasis.PER_UNIT, 5_000_000))
        assertTrue(SelfCheck.run(r, b).ok)
        // A line that no correct operation can write: the ledger no longer balances.
        val entry = db.financeDao().entries(b).first()
        db.financeDao().insertLines(listOf(com.github.opscalehub.dong.data.db.JournalLineEntity(newId(), entry.id, b,
            com.github.opscalehub.dong.domain.ledger.AccountKind.entries.first(), 7_654_321)))
        val h = SelfCheck.run(r, b)
        assertTrue(h.problems.toString(), "ledger-unbalanced" in h.problems)
        val text = h.problems.joinToString() + h.summary
        assertTrue(text, text.none { it in '\u0600'..'\u06FF' } && "7654321" !in text)
    }

    @Test fun `both demo buildings pass the self-check`() = runBlocking {
        for (history in listOf(0, 10)) {
            val b = SampleData.createBuilding(r, history = history)
            SampleData.addDemoData(r, b, history = history)
            assertEquals("history=$history", emptyList<String>(), SelfCheck.run(r, b).problems)
        }
    }
}
