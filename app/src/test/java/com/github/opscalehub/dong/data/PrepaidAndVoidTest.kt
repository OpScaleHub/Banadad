package com.github.opscalehub.dong.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.opscalehub.dong.data.db.DongDatabase
import com.github.opscalehub.dong.data.db.ExpenseEntity
import com.github.opscalehub.dong.data.db.ExpenseStatus
import com.github.opscalehub.dong.data.db.PeriodStatus
import com.github.opscalehub.dong.domain.allocation.AllocationPolicy
import com.github.opscalehub.dong.domain.allocation.Basis
import com.github.opscalehub.dong.domain.calendar.Jalali
import com.github.opscalehub.dong.domain.ledger.EntryType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Testers' reports (0.4.7): a fixed monthly charge is paid in advance (by the 10th), a bill paid
 * from the pot must not be charged to the units again, and cancelling a bill must undo everything
 * that hangs off it in one step.
 */
@RunWith(AndroidJUnit4::class)
class PrepaidAndVoidTest {
    private val db = DongDatabase.build(ApplicationProvider.getApplicationContext(), null)
    private val clock = FixedClock(Jalali.toEpochDay(1405, 8, 5))
    private val r = Repos(db, clock)
    private val aban = Jalali.toEpochDay(1405, 8, 1)
    private val drafts = listOf(
        BuildingRepository.UnitDraft("۱", 1, 6_000, 2), BuildingRepository.UnitDraft("۲", 1, 8_000, 3), BuildingRepository.UnitDraft("۳", 2, 8_000, 1),
    )

    @After fun close() = db.close()

    private suspend fun fixed(water: BuildingRepository.WaterSetup = BuildingRepository.WaterSetup.METERED) =
        r.buildings.setup("پیش‌پرداخت", "", true, 1000, drafts, 0, 0, BuildingRepository.ChargePlan.Fixed(BuildingRepository.FixedBasis.PER_UNIT, 5_000_000),
            BuildingRepository.SharedBills(water))

    private suspend fun bill(b: String, name: String, amount: Long, policy: AllocationPolicy? = null, payNow: Boolean = true): ExpenseEntity {
        val p = db.periodDao().periods(b).first { it.status == PeriodStatus.DRAFT }
        val cat = db.expenseDao().categories(b).first { it.name == name }
        val e = ExpenseEntity(newId(), b, p.id, cat.id, cat.defaultFundId!!, null, p.startDate + 2, name, amount,
            policyJson = (policy ?: AllocationPolicy.decode(cat.defaultPolicyJson)).encode(), createdAt = 0)
        r.expenses.create(e, if (payNow) db.financeDao().cashAccounts(b).first().id else null)
        return e
    }

    private suspend fun owed(b: String) = r.finance.balances(b).units.values.sorted()

    @Test
    fun `the monthly charge is owed from the first day and due by the 10th`() = runBlocking {
        val b = fixed()
        val p = db.periodDao().periods(b).single()
        assertEquals(aban, p.startDate)
        assertEquals(aban + 9, p.dueDate)
        val charges = db.financeDao().entries(b).filter { it.type == EntryType.CHARGE }
        assertEquals(1, charges.size)
        assertEquals(aban, charges.single().entryDate)
        assertEquals(listOf(5_000_000L, 5_000_000L, 5_000_000L), owed(b))
        // Each unit can be billed at once.
        val d = ReportRepository(r).load(b)
        assertTrue(isBilled(d, p))
        assertEquals(3, billedUnits(d, p.id).size)
        val unit = db.buildingDao().units(b).first().id
        r.bills.markSent(p.id, unit, "test")
        assertEquals(5_000_000L, r.bills.bill(b, unit, p.id).lines.sumOf { it.second })
    }

    @Test
    fun `a water bill paid from the pot is not charged again at closing`() = runBlocking {
        val b = fixed()
        // The reported case: the water bill, paid at once from the pot, in a metered building.
        bill(b, "آب", 2_400_000)
        val p = db.periodDao().periods(b).single()
        r.periods.finalize(p.id)
        assertEquals("nothing on top of the monthly charge", listOf(5_000_000L, 5_000_000L, 5_000_000L), owed(b))
        assertEquals(1, db.financeDao().entries(b).count { it.type == EntryType.CHARGE })
        val bal = r.finance.balances(b)
        assertEquals(-2_400_000L, bal.cashTotal)
        assertTrue(bal.reconciles)
    }

    @Test
    fun `a bill chosen to be charged separately is added at closing on top of the monthly charge`() = runBlocking {
        val b = fixed()
        bill(b, "تعمیرات", 3_000_000, AllocationPolicy.single(Basis.Equal))
        r.periods.finalize(db.periodDao().periods(b).single().id)
        assertEquals(listOf(6_000_000L, 6_000_000L, 6_000_000L), owed(b))
        val charges = db.financeDao().entries(b).filter { it.type == EntryType.CHARGE }.sortedBy { it.entryDate }
        assertEquals(listOf(aban, Jalali.toEpochDay(1405, 8, 30)), charges.map { it.entryDate })
    }

    @Test
    fun `changes during the month update the advance charge, and closing settles the difference`() = runBlocking {
        val b = fixed()
        val item = db.expenseDao().chargeItems(b).single()
        r.expenses.saveChargeItem(item.copy(amountMinor = 6_000_000))
        r.periods.refreshAdvances(b)
        assertEquals(listOf(6_000_000L, 6_000_000L, 6_000_000L), owed(b))
        // Unchanged: no new entries.
        val n = db.financeDao().entries(b).size
        r.periods.refreshAdvances(b)
        assertEquals(n, db.financeDao().entries(b).size)
        r.periods.finalize(db.periodDao().periods(b).single().id)
        assertEquals(listOf(6_000_000L, 6_000_000L, 6_000_000L), owed(b))
        assertTrue(r.finance.balances(b).reconciles)
    }

    @Test
    fun `the next month starts on its own first day and is charged`() = runBlocking {
        val b = fixed()
        assertNull("the current month exists", r.periods.ensureCurrentMonth(b))
        clock.day = Jalali.toEpochDay(1405, 9, 1)
        val next = r.periods.ensureCurrentMonth(b)!!
        val p = db.periodDao().period(next)!!
        assertEquals(clock.day, p.startDate)
        assertEquals(listOf(10_000_000L, 10_000_000L, 10_000_000L), owed(b))
        // Deleting that month takes its charge back.
        r.periods.deleteDraft(next)
        assertEquals(listOf(5_000_000L, 5_000_000L, 5_000_000L), owed(b))
        assertTrue(r.finance.balances(b).reconciles)
        // Months missed while the app was closed are all started and charged.
        clock.day = Jalali.toEpochDay(1405, 11, 2)
        r.periods.ensureCurrentMonth(b)
        assertEquals(4, db.periodDao().periods(b).size)
        assertEquals(listOf(20_000_000L, 20_000_000L, 20_000_000L), owed(b))
    }

    @Test
    fun `split-bills buildings are charged at closing as before`() = runBlocking {
        val b = r.buildings.setup("تقسیم", "", true, 1000, drafts, 0, 0, BuildingRepository.ChargePlan.SplitBills)
        val p = db.periodDao().periods(b).single()
        assertEquals(p.endDate + 10, p.dueDate)
        assertEquals(0, db.financeDao().entries(b).count { it.type == EntryType.CHARGE })
        assertNull(r.periods.ensureCurrentMonth(b))
    }

    @Test
    fun `cancelling a paid bill in an open month reverses its payment too`() = runBlocking {
        val b = r.buildings.setup("تقسیم", "", true, 1000, drafts, 0, 0, BuildingRepository.ChargePlan.SplitBills)
        val e = bill(b, "نظافت", 3_000_000)
        assertEquals(-3_000_000L, r.finance.balances(b).cashTotal)
        r.expenses.void(e.id, "دوبار ثبت شده")
        assertEquals(ExpenseStatus.VOIDED, db.expenseDao().expense(e.id)!!.status)
        val bal = r.finance.balances(b)
        assertEquals(0L, bal.cashTotal)
        assertEquals(0L, bal.payablesTotal)
        assertEquals(0L, r.expenses.paid(e.id))
        assertTrue(bal.reconciles)
        // Nothing left to charge: the month closes empty.
        val p = db.periodDao().periods(b).single()
        assertEquals(0L, r.periods.preview(p.id).total)
    }

    @Test
    fun `cancelling a bill of a closed month credits every unit its share`() = runBlocking {
        val b = r.buildings.setup("تقسیم", "", true, 1000, drafts, 0, 0, BuildingRepository.ChargePlan.SplitBills)
        val keep = bill(b, "نظافت", 3_000_000, AllocationPolicy.single(Basis.Equal))
        val wrong = bill(b, "آسانسور", 6_000_000, AllocationPolicy.single(Basis.Equal))
        val p = db.periodDao().periods(b).single()
        r.periods.finalize(p.id)
        assertEquals(listOf(3_000_000L, 3_000_000L, 3_000_000L), owed(b))
        clock.day = Jalali.toEpochDay(1405, 9, 3)
        r.expenses.void(wrong.id, "قبض ساختمان دیگر بود")
        assertEquals(listOf(1_000_000L, 1_000_000L, 1_000_000L), owed(b))
        val bal = r.finance.balances(b)
        assertEquals(-3_000_000L, bal.cashTotal) // the wrong bill's payment is back in the pot
        assertTrue(bal.reconciles)
        // The closed month's own entries are untouched; the credit is a revision dated today.
        val corr = db.financeDao().entries(b).single { it.type == EntryType.PERIOD_CORRECTION }
        assertEquals(clock.day, corr.entryDate)
        assertEquals(1, db.periodDao().revisions(p.id).size)
        assertEquals(3_000_000L, r.periods.effectiveResult(p.id).total)
        // A later correction does not bring the cancelled bill back.
        try { r.periods.applyCorrection(p.id, "بررسی"); fail("nothing to correct") } catch (e: OpException) { assertEquals(OpError.NOTHING_TO_CORRECT, e.code) }
        assertEquals(ExpenseStatus.ACTIVE, db.expenseDao().expense(keep.id)!!.status)
    }
}
