package com.github.opscalehub.dong.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.opscalehub.dong.data.db.DongDatabase
import com.github.opscalehub.dong.domain.calendar.Jalali
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tester's report (0.10.1, a 19-unit building): set up as «تقسیم قبض‌ها» by mistake, residents
 * already paying the monthly charge, the month showed 0 for everyone and its deadline was the 10th
 * of the next month. A monthly charge can now be set after setup.
 */
@RunWith(AndroidJUnit4::class)
class MonthlyChargeLaterTest {
    private val db = DongDatabase.build(ApplicationProvider.getApplicationContext(), null)
    private val azar1 = Jalali.toEpochDay(1405, 9, 1)
    private val clock = FixedClock(azar1)
    private val r = Repos(db, clock)
    private val drafts = listOf(
        BuildingRepository.UnitDraft("الف", 1, 0, 3), BuildingRepository.UnitDraft("ب", 1, 0, 2), BuildingRepository.UnitDraft("پ", 2, 0, 4),
    )

    @After fun close() = db.close()

    @Test
    fun `a split-bills building gets a monthly charge from its open month, due by the 10th`() = runBlocking {
        val b = r.buildings.setup("آزمون", "", true, 1000, drafts, 0, 0, BuildingRepository.ChargePlan.SplitBills)
        val p = db.periodDao().periods(b).single()
        assertEquals("split bills: due after the month", p.endDate + 10, p.dueDate)
        val units = db.buildingDao().units(b).sortedBy { it.sortOrder }
        val cash = db.financeDao().cashAccounts(b).first().id
        r.finance.receivePayment(b, units[0].id, 15_000_000, cash, azar1)
        assertEquals(-15_000_000L, r.finance.balances(b).units.getValue(units[0].id))

        // ½ for the third unit: 2 + 2 + 1 halves.
        r.buildings.saveMonthlyCharge(b, BuildingRepository.ChargePlan.Fixed(perUnit = 15_000_000, unitWeights = listOf(2, 2, 1), weightScale = 2))
        val after = db.periodDao().periods(b).single()
        assertEquals("due by the 10th of the month itself", azar1 + 9, after.dueDate)
        val owed = r.finance.balances(b).units
        assertEquals("paid in full before the charge was set", 0L, owed.getValue(units[0].id))
        assertEquals(15_000_000L, owed.getValue(units[1].id))
        assertEquals(7_500_000L, owed.getValue(units[2].id))
        val d = ReportRepository(r).load(b)
        assertTrue(isBilled(d, after))
        assertEquals(3, billedUnits(d, after.id).size)
        assertTrue(SelfCheck.run(r, b).ok)
        // A unit with past debt pays: the payment clears the old debt first, and the app says so.
        r.finance.setUnitOpening(b, units[1].id, 4_000_000)
        r.finance.receivePayment(b, units[1].id, 5_000_000, cash, azar1)
        val d2 = ReportRepository(r).load(b)
        assertEquals(4_000_000L, Insights.paidToOlderDebt(d2, after.id))

        // Nothing charged when all parts are zero.
        try { r.buildings.saveMonthlyCharge(b, BuildingRepository.ChargePlan.Fixed()); fail() } catch (e: OpException) { }
        // Next month is made and charged on its own.
        clock.day = after.endDate + 1
        r.periods.ensureCurrentMonth(b)
        assertEquals(2, db.periodDao().periods(b).size)
        assertTrue(SelfCheck.run(r, b).ok)
    }

    @Test
    fun `the monthly charge mixes parts and can be changed later, from the open month only`() = runBlocking {
        // Owner's report: per unit + per person (+ per m²) together, and changeable after setup.
        val areas = listOf(BuildingRepository.UnitDraft("الف", 1, 10_000, 3), BuildingRepository.UnitDraft("ب", 1, 8_000, 2), BuildingRepository.UnitDraft("پ", 2, 6_000, 1))
        val b = r.buildings.setup("آزمون", "", true, 1000, areas, 0, 0, BuildingRepository.ChargePlan.SplitBills)
        val units = db.buildingDao().units(b).sortedBy { it.sortOrder }
        fun owed() = runBlocking { r.finance.balances(b).units.let { m -> units.map { m.getValue(it.id) } } }
        // Start with per person only (what the tester could set in 0.10.2).
        r.buildings.saveMonthlyCharge(b, BuildingRepository.ChargePlan.Fixed(perPerson = 3_000_000))
        assertEquals(listOf(9_000_000L, 6_000_000L, 3_000_000L), owed())
        assertEquals(BuildingRepository.ChargePlan.Fixed(perPerson = 3_000_000), r.buildings.monthlyPlan(b))
        // Add a fixed part with the third unit at ½, and a per-m² part: all three together.
        val mix = BuildingRepository.ChargePlan.Fixed(perUnit = 4_000_000, perPerson = 3_000_000, perM2 = 100_000, unitWeights = listOf(2, 2, 1), weightScale = 2)
        r.buildings.saveMonthlyCharge(b, mix)
        assertEquals(listOf(4_000_000L + 9_000_000 + 10_000_000, 4_000_000L + 6_000_000 + 8_000_000, 2_000_000L + 3_000_000 + 6_000_000), owed())
        assertEquals(mix, r.buildings.monthlyPlan(b))
        assertTrue(SelfCheck.run(r, b).ok)
        // Close this month, then drop the per-person part: the closed month keeps what it charged.
        val azar = db.periodDao().periods(b).single()
        r.periods.finalize(azar.id)
        val closed = owed()
        clock.day = azar.endDate + 1
        r.periods.ensureCurrentMonth(b)
        val dey = mix.copy(perPerson = 0)
        r.buildings.saveMonthlyCharge(b, dey)
        assertEquals(dey, r.buildings.monthlyPlan(b))
        val next = listOf(4_000_000L + 10_000_000, 4_000_000L + 8_000_000, 2_000_000L + 6_000_000)
        assertEquals(closed.zip(next) { a, c -> a + c }, owed())
        assertEquals(2, db.periodDao().periods(b).size)
        assertTrue(SelfCheck.run(r, b).ok)
        // Cash and funds still reconcile after all the re-posting.
        assertTrue(r.finance.balances(b).reconciles)
    }
}
