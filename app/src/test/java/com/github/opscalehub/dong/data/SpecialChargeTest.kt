package com.github.opscalehub.dong.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.opscalehub.dong.data.db.DongDatabase
import com.github.opscalehub.dong.data.db.ExpenseEntity
import com.github.opscalehub.dong.data.db.PeriodStatus
import com.github.opscalehub.dong.domain.allocation.AllocationPolicy
import com.github.opscalehub.dong.domain.allocation.Basis
import com.github.opscalehub.dong.domain.calendar.Jalali
import com.github.opscalehub.dong.ui.halvesToWeights
import com.github.opscalehub.dong.ui.multiplierLabel
import com.github.opscalehub.dong.ui.weightToHalves
import com.github.opscalehub.dong.ui.weightedFromHalves
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * «شارژ فوق‌العاده» (special assessment): a one-off amount units owe on top of the monthly charge for a
 * big job (owner's case: the elevator repair the neighbours prepay). Owed from the day it is issued,
 * on bills as its own line, and the job itself is a bill paid from the fund. Also «½ برابر».
 */
@RunWith(AndroidJUnit4::class)
class SpecialChargeTest {
    private val db = DongDatabase.build(ApplicationProvider.getApplicationContext(), null)
    private val clock = FixedClock(Jalali.toEpochDay(1405, 8, 5))
    private val r = Repos(db, clock)
    private val drafts = listOf(
        BuildingRepository.UnitDraft("۱", 1, 6_000, 2), BuildingRepository.UnitDraft("۲", 1, 8_000, 3), BuildingRepository.UnitDraft("۳", 2, 8_000, 1),
    )

    @After fun close() = db.close()

    private suspend fun fixed(plan: BuildingRepository.ChargePlan.Fixed = BuildingRepository.ChargePlan.Fixed(BuildingRepository.FixedBasis.PER_UNIT, 5_000_000)) =
        r.buildings.setup("آزمون", "", true, 1000, drafts, 0, 0, plan, BuildingRepository.SharedBills(BuildingRepository.WaterSetup.SHARED))

    private suspend fun units(b: String) = db.buildingDao().units(b).sortedBy { it.sortOrder }
    private suspend fun owed(b: String) = units(b).map { r.finance.balances(b).units.getValue(it.id) }

    private suspend fun bill(b: String, cat: String, amount: Long, policy: AllocationPolicy = AllocationPolicy.FUND_ONLY, paid: Boolean = true): ExpenseEntity {
        val p = db.periodDao().periods(b).first { it.status == PeriodStatus.DRAFT }
        val c = db.expenseDao().categories(b).first { it.name == cat }
        val e = ExpenseEntity(newId(), b, p.id, c.id, c.defaultFundId!!, null, p.startDate + 3, cat, amount, policyJson = policy.encode(), createdAt = 0)
        r.expenses.create(e, if (paid) db.financeDao().cashAccounts(b).first().id else null)
        return e
    }

    @Test
    fun `a special charge is owed at once, shows on the bill, and the job is paid from the fund`() = runBlocking {
        val b = fixed()
        val p = db.periodDao().periods(b).single()
        val u = units(b)
        val id = r.special.issue(b, "پیش‌پرداخت تعمیر آسانسور", 30_000_000, AllocationPolicy.single(Basis.Equal))
        assertEquals("monthly charge + a third of the repair", listOf(15_000_000L, 15_000_000L, 15_000_000L), owed(b))
        val bl = r.bills.bill(b, u[0].id, p.id)
        assertEquals(listOf("پیش‌پرداخت تعمیر آسانسور" to 10_000_000L), bl.special)
        assertEquals("the month's own charge is unchanged", 5_000_000L, bl.lines.sumOf { it.second })
        assertEquals(0L, bl.later)
        assertEquals(15_000_000L, bl.due)
        // The repair itself, later, from the fund: nobody owes more.
        bill(b, "تعمیرات", 28_000_000)
        assertEquals(listOf(15_000_000L, 15_000_000L, 15_000_000L), owed(b))
        // Closing the month does not charge the special charge again.
        r.periods.finalize(p.id)
        assertEquals(listOf(15_000_000L, 15_000_000L, 15_000_000L), owed(b))
        val sc = r.special.list(b).single()
        assertEquals(id, sc.entryId)
        assertEquals(30_000_000L, sc.total)
        assertTrue(SelfCheck.run(r, b).ok)
        assertTrue(r.finance.balances(b).reconciles)
    }

    @Test
    fun `a unit exempt from the monthly charge still owes its share of a special charge`() = runBlocking {
        val b = fixed(BuildingRepository.ChargePlan.Fixed(perUnit = 5_000_000, unitWeights = listOf(1, 1, 0)))
        r.special.issue(b, "رنگ‌آمیزی", 9_000_000, AllocationPolicy.single(Basis.Equal))
        assertEquals(listOf(8_000_000L, 8_000_000L, 3_000_000L), owed(b))
        val p = db.periodDao().periods(b).single()
        assertEquals(3, billedUnits(ReportRepository(r).load(b), p.id).size + 0)
        assertTrue(SelfCheck.run(r, b).ok)
    }

    @Test
    fun `issued after the month was closed, it is on that month's bill and the bill still adds up`() = runBlocking {
        val b = fixed()
        val p = db.periodDao().periods(b).single()
        r.periods.finalize(p.id)
        clock.day = p.endDate   // still inside the closed month
        r.special.issue(b, "تعمیر آسانسور", 15_000_000, AllocationPolicy.single(Basis.Equal))
        val u = units(b)
        val bl = r.bills.bill(b, u[0].id, p.id)
        assertEquals(listOf("تعمیر آسانسور" to 5_000_000L), bl.special)
        assertEquals(r.finance.unitStatement(b, u[0].id).closing, bl.due)
        assertTrue(SelfCheck.run(r, b).ok)
    }

    @Test
    fun `a cancelled special charge leaves nothing owed and cannot be cancelled twice`() = runBlocking {
        val b = fixed()
        val id = r.special.issue(b, "اشتباهی", 6_000_000, AllocationPolicy.single(Basis.Equal))
        r.special.cancel(b, id, "صادر شده به اشتباه")
        assertEquals(listOf(5_000_000L, 5_000_000L, 5_000_000L), owed(b))
        assertTrue(r.special.list(b).single().cancelled)
        val p = db.periodDao().periods(b).single()
        assertTrue("nets to nothing on the bill", r.bills.bill(b, units(b)[0].id, p.id).special.isEmpty())
        try { r.special.cancel(b, id, "دوباره"); fail() } catch (e: OpException) { assertEquals(OpError.ALREADY_REVERSED, e.code) }
        try { r.special.issue(b, "", 1_000_000, AllocationPolicy.single(Basis.Equal)); fail() } catch (e: OpException) { }
        try { r.special.issue(b, "x", 1_000_000, AllocationPolicy.FUND_ONLY); fail() } catch (e: OpException) { }
        assertTrue(SelfCheck.run(r, b).ok)
    }

    @Test
    fun `half multipliers split a special charge and a monthly rate exactly`() = runBlocking {
        val basis = weightedFromHalves(listOf("a", "b", "c"), listOf(2, 2, 1))
        assertEquals(2L, basis.scale)
        assertEquals("whole multipliers stay as before", listOf(1L, 2L) to 1L, halvesToWeights(listOf(2, 4)))
        assertEquals(1L, weightToHalves(1, 2))
        assertEquals(4L, weightToHalves(2, 1))
        assertEquals("½ برابر", multiplierLabel(1))
        assertEquals("۱½ برابر", multiplierLabel(3))
        // A monthly charge of 5,000,000 per «۱ برابر»: the half unit pays 2,500,000.
        val (w, scale) = halvesToWeights(listOf(2, 2, 1))
        val b = fixed(BuildingRepository.ChargePlan.Fixed(perUnit = 5_000_000, unitWeights = w, weightScale = scale))
        assertEquals(listOf(5_000_000L, 5_000_000L, 2_500_000L), owed(b))
        // A special charge with a half share: 25,000,000 over 2 + 2 + 1 halves.
        r.special.issue(b, "آسانسور", 25_000_000, AllocationPolicy.single(weightedFromHalves(units(b).map { it.id }, listOf(2, 2, 1))))
        assertEquals(listOf(15_000_000L, 15_000_000L, 7_500_000L), owed(b))
        val old = AllocationPolicy.decode("""{"fundOnly":false,"components":[{"portion":{"type":"remainder"},"basis":{"type":"weighted","weights":{"a":2}},"eligibility":{"type":"all"}}]}""")
        assertEquals(1L, (old.components.single().basis as Basis.Weighted).scale)
    }

    @Test
    fun `a building that splits its actual bills shows an open month's split bill as pending`() = runBlocking {
        // The «تقسیم قبض‌ها» model (no monthly charge): bills are the charge, settled at closing.
        val b = r.buildings.setup("قبض", "", true, 1000, drafts, 0, 0, BuildingRepository.ChargePlan.SplitBills)
        bill(b, "تعمیرات", 30_000_000, AllocationPolicy.single(Basis.Equal), paid = false)
        val p = db.periodDao().periods(b).single()
        val bl = r.bills.bill(b, units(b)[0].id, p.id)
        assertEquals(listOf("تعمیرات" to 10_000_000L), bl.pending)
        assertEquals(0L, bl.due)
        r.periods.finalize(p.id)
        assertEquals(listOf(10_000_000L, 10_000_000L, 10_000_000L), owed(b))
        assertTrue(SelfCheck.run(r, b).ok)
    }

    @Test
    fun `setting a monthly charge moves the open month's split bills and the types to the fund`() = runBlocking {
        val b = r.buildings.setup("قبض", "", true, 1000, drafts, 0, 0, BuildingRepository.ChargePlan.SplitBills)
        bill(b, "تعمیرات", 9_000_000, AllocationPolicy.single(Basis.Equal))
        val moved = r.buildings.saveMonthlyCharge(b, BuildingRepository.ChargePlan.Fixed(perUnit = 5_000_000))
        assertEquals(1, moved)
        assertEquals(listOf(5_000_000L, 5_000_000L, 5_000_000L), owed(b))
        r.periods.finalize(db.periodDao().periods(b).single().id)
        assertEquals("nothing on top at closing", listOf(5_000_000L, 5_000_000L, 5_000_000L), owed(b))
        assertTrue(db.expenseDao().categories(b).filter { it.active }.all { r.expenses.allowedWithMonthlyCharge(b, it.id, AllocationPolicy.decode(it.defaultPolicyJson)) })
        assertTrue(SelfCheck.run(r, b).ok)
    }

    @Test
    fun `an old split bill in a building with a monthly charge is moved to the fund once, closed months untouched`() = runBlocking {
        // A 0.10.x book: a monthly charge, and a bill in the open month split among units on top of it.
        val b = fixed()
        val e = bill(b, "تعمیرات", 9_000_000)
        db.expenseDao().updateExpense(db.expenseDao().expense(e.id)!!.copy(policyJson = AllocationPolicy.single(Basis.Equal).encode()))
        val p = db.periodDao().periods(b).single()
        assertEquals(listOf("تعمیرات" to 3_000_000L), r.bills.bill(b, units(b)[0].id, p.id).pending)
        assertEquals(1, r.buildings.alignBillsWithMonthlyCharge(b))
        assertEquals("idempotent", 0, r.buildings.alignBillsWithMonthlyCharge(b))
        assertTrue(r.bills.bill(b, units(b)[0].id, p.id).pending.isEmpty())
        r.periods.finalize(p.id)
        assertEquals("only the monthly charge", listOf(5_000_000L, 5_000_000L, 5_000_000L), owed(b))
        assertTrue(SelfCheck.run(r, b).ok)
        // A building that splits its actual bills (no monthly charge) is left alone.
        val split = r.buildings.setup("قبض", "", true, 1000, drafts, 0, 0, BuildingRepository.ChargePlan.SplitBills)
        bill(split, "تعمیرات", 9_000_000, AllocationPolicy.single(Basis.Equal))
        assertEquals(0, r.buildings.alignBillsWithMonthlyCharge(split))
    }
}
