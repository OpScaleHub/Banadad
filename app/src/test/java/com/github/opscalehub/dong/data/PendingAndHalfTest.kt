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
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Owner's feedback (0.10.0): (1) a bill split among units in an open month (a repair the
 * neighbours prepay) must be visible on bills shared before the month closes, apart from what is
 * already owed; (2) a unit can pay «½ برابر».
 */
@RunWith(AndroidJUnit4::class)
class PendingAndHalfTest {
    private val db = DongDatabase.build(ApplicationProvider.getApplicationContext(), null)
    private val clock = FixedClock(Jalali.toEpochDay(1405, 8, 5))
    private val r = Repos(db, clock)
    private val drafts = listOf(
        BuildingRepository.UnitDraft("۱", 1, 6_000, 2), BuildingRepository.UnitDraft("۲", 1, 8_000, 3), BuildingRepository.UnitDraft("۳", 2, 8_000, 1),
    )

    @After fun close() = db.close()

    private suspend fun fixed(plan: BuildingRepository.ChargePlan.Fixed = BuildingRepository.ChargePlan.Fixed(BuildingRepository.FixedBasis.PER_UNIT, 5_000_000)) =
        r.buildings.setup("در راه", "", true, 1000, drafts, 0, 0, plan, BuildingRepository.SharedBills(BuildingRepository.WaterSetup.SHARED))

    private suspend fun repair(b: String, amount: Long, policy: AllocationPolicy): ExpenseEntity {
        val p = db.periodDao().periods(b).first { it.status == PeriodStatus.DRAFT }
        val cat = db.expenseDao().categories(b).first { it.name == "تعمیرات" }
        val e = ExpenseEntity(newId(), b, p.id, cat.id, cat.defaultFundId!!, null, p.startDate + 3, "پیش‌پرداخت تعمیر آسانسور", amount,
            policyJson = policy.encode(), createdAt = 0)
        r.expenses.create(e, null) // not paid to anyone yet
        return e
    }

    @Test
    fun `an open month's split bill shows on the bill as pending, apart from the balance`() = runBlocking {
        val b = fixed()
        repair(b, 30_000_000, AllocationPolicy.single(Basis.Equal))
        val p = db.periodDao().periods(b).single()
        val units = db.buildingDao().units(b).sortedBy { it.sortOrder }
        val bill = r.bills.bill(b, units[0].id, p.id)
        assertEquals("already charged: the monthly charge", 5_000_000L, bill.lines.sumOf { it.second })
        assertEquals("the balance does not include the repair yet", 5_000_000L, bill.due)
        assertEquals(listOf("پیش‌پرداخت تعمیر آسانسور" to 10_000_000L), bill.pending)
        // Closing charges it, and it is no longer pending.
        r.periods.finalize(p.id)
        val closed = r.bills.bill(b, units[0].id, p.id)
        assertTrue(closed.pending.isEmpty())
        assertEquals(15_000_000L, closed.due)
        assertTrue(SelfCheck.run(r, b).ok)
    }

    @Test
    fun `a repair recorded after the month was closed shows on the closed month's bill`() = runBlocking {
        // The owner's case: the month closed early, the repair is recorded before the next month starts.
        val b = fixed()
        val aban = db.periodDao().periods(b).single()
        r.periods.finalize(aban.id)
        val next = SampleData.monthAfter(aban.endDate)
        r.periods.create(b, next.name, next.start, next.end)
        repair(b, 30_000_000, AllocationPolicy.single(Basis.Equal))
        val units = db.buildingDao().units(b).sortedBy { it.sortOrder }
        val bill = r.bills.bill(b, units[0].id, aban.id)
        assertEquals(listOf("پیش‌پرداخت تعمیر آسانسور" to 10_000_000L), bill.pending)
        assertEquals("not owed before the next month closes", 5_000_000L, bill.due)
        assertTrue(SelfCheck.run(r, b).ok)
    }

    @Test
    fun `a unit exempt from the monthly charge still gets a bill for its pending share`() = runBlocking {
        val b = fixed(BuildingRepository.ChargePlan.Fixed(perUnit = 5_000_000, unitWeights = listOf(1, 1, 0)))
        repair(b, 9_000_000, AllocationPolicy.single(Basis.Equal))
        val p = db.periodDao().periods(b).single()
        val d = ReportRepository(r).load(b)
        assertEquals(3, billedUnits(d, p.id).size)
    }

    @Test
    fun `half multipliers split a bill and a monthly rate exactly`() = runBlocking {
        // Everyone 1×, unit 3 pays half: 2 + 2 + 1 halves.
        val basis = weightedFromHalves(listOf("a", "b", "c"), listOf(2, 2, 1))
        assertEquals(2L, basis.scale)
        assertEquals(listOf(2L, 2L, 1L) to 2L, halvesToWeights(listOf(2, 2, 1)))
        assertEquals("whole multipliers stay as before", listOf(1L, 2L) to 1L, halvesToWeights(listOf(2, 4)))
        assertEquals(1L, weightToHalves(1, 2))
        assertEquals(4L, weightToHalves(2, 1))
        assertEquals("½ برابر", multiplierLabel(1))
        assertEquals("۱½ برابر", multiplierLabel(3))

        // A monthly charge of 5,000,000 per «۱ برابر»: the half unit pays 2,500,000.
        val (w, scale) = halvesToWeights(listOf(2, 2, 1))
        val b = fixed(BuildingRepository.ChargePlan.Fixed(perUnit = 5_000_000, unitWeights = w, weightScale = scale))
        val units = db.buildingDao().units(b).sortedBy { it.sortOrder }
        val owed = r.finance.balances(b).units
        assertEquals(listOf(5_000_000L, 5_000_000L, 2_500_000L), units.map { owed.getValue(it.id) })

        // A bill split with a half share: 25,000,000 over 2 + 2 + 1 halves.
        val ids = units.map { it.id }
        repair(b, 25_000_000, AllocationPolicy.single(weightedFromHalves(ids, listOf(2, 2, 1))))
        val p = db.periodDao().periods(b).single()
        val d = ReportRepository(r).load(b)
        assertEquals(listOf(10_000_000L, 10_000_000L, 5_000_000L), ids.map { u -> Insights.pendingComposition(d, u, p.id).sumOf { it.second } })
        // Old policies (no scale in the JSON) still read as whole multipliers.
        val old = AllocationPolicy.decode("""{"fundOnly":false,"components":[{"portion":{"type":"remainder"},"basis":{"type":"weighted","weights":{"a":2}},"eligibility":{"type":"all"}}]}""")
        assertEquals(1L, (old.components.single().basis as Basis.Weighted).scale)
    }
}
