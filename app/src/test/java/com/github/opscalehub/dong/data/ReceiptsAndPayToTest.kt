package com.github.opscalehub.dong.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.opscalehub.dong.data.db.DongDatabase
import com.github.opscalehub.dong.data.db.PeriodStatus
import com.github.opscalehub.dong.domain.calendar.Jalali
import com.github.opscalehub.dong.i18n.MoneyFormat
import com.github.opscalehub.dong.share.BillShare
import com.github.opscalehub.dong.share.Block
import com.github.opscalehub.dong.share.PayTo
import com.github.opscalehub.dong.share.ReceiptShare
import com.github.opscalehub.dong.ui.S
import com.github.opscalehub.dong.ui.UiPrefs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 0.7.0: where to pay on bills and reminders, payment receipts, undo of a payment, backup reminder. */
@RunWith(AndroidJUnit4::class)
class ReceiptsAndPayToTest {
    private val db = DongDatabase.build(ApplicationProvider.getApplicationContext(), null)
    private val r = Repos(db, FixedClock(Jalali.toEpochDay(1405, 7, 26)))
    private val money = MoneyFormat(0, 10, "تومان")

    @After fun close() = db.close()

    // Placeholder values only; not a real card or account.
    private val card = "1111222233334444"
    private val iban = "IR001111222233334444555566"

    @Test fun `card numbers and IBANs are grouped and kept left to right`() {
        assertEquals("⁦1111 2222 3333 4444⁩", PayTo.format(card))
        assertEquals("⁦1111 2222 3333 4444⁩", PayTo.format("۱۱۱۱-۲۲۲۲-۳۳۳۳-۴۴۴۴"))
        assertEquals("⁦IR00 1111 2222 3333 4444 5555 66⁩", PayTo.format(iban.lowercase()))
        assertEquals("⁦حساب صندوق⁩", PayTo.format(" حساب صندوق "))
    }

    @Test fun `reminder carries the deadline and where to pay only when known`() {
        val bare = PayTo.reminder("۱-۱", "ساختمان", "۱۰ تومان", null, "", "")
        assertEquals(S.reminderText.format("۱-۱", "ساختمان", "۱۰ تومان"), bare)
        assertNull(PayTo.line("", "کسی"))
        val full = PayTo.reminder("۱-۱", "ساختمان", "۱۰ تومان", "۱۴۰۵/۰۸/۱۰", card, "مدیر ساختمان")
        val lines = full.lines()
        assertEquals(3, lines.size)
        assertTrue(lines[1].contains("۱۴۰۵/۰۸/۱۰"))
        assertTrue(lines[2].contains("1111 2222 3333 4444") && lines[2].contains("مدیر ساختمان"))
    }

    @Test fun `bill shows the deadline and payment details while something is owed`() = runBlocking {
        val b = SampleData.createBuilding(r)
        SampleData.addDemoData(r, b)
        r.buildings.updateSettings(db.buildingDao().building(b)!!.copy(payAccount = card, payHolder = "مدیر ساختمان"))
        val closed = db.periodDao().periods(b).first { it.status == PeriodStatus.FINALIZED }
        val debtor = ReportRepository(r).load(b).balances.units.entries.first { it.value > 0 }.key
        val bill = r.bills.bill(b, debtor, closed.id)
        assertEquals(card, bill.payAccount)
        val rows = BillShare.blocks(bill, money).filterIsInstance<Block.Row>()
        assertTrue(rows.any { it.label == S.payAccount && it.value.contains("1111 2222 3333 4444") })
        assertTrue(rows.any { it.label == S.payHolder && it.value == "مدیر ساختمان" })
        if (closed.dueDate != null) assertTrue(rows.any { it.label == S.billDue })
        // A settled bill with nothing coming does not ask for money.
        val settled = BillShare.blocks(bill.copy(due = 0, pending = emptyList()), money).filterIsInstance<Block.Row>()
        assertFalse(settled.any { it.label == S.payAccount || it.label == S.billDue })
    }

    @Test fun `receipt shows the payment and the balance after it, and undo reverses it`() = runBlocking {
        val w = World().setup()
        val r = w.r
        val u = w.unit("1-1")
        val before = r.finance.unitStatement(w.b, u.id).closing
        val id = r.finance.receivePayment(w.b, u.id, 1_250_000, w.cash, w.start + 3, "TR-7", method = "کارت به کارت")
        val receipt = r.bills.receipt(id)
        assertEquals(1_250_000L, receipt.amount)
        assertEquals("کارت به کارت", receipt.method)
        assertEquals("TR-7", receipt.reference)
        assertEquals(before - 1_250_000, receipt.balance)
        val blocks = ReceiptShare.blocks(receipt, money)
        assertTrue(blocks.first() == Block.Title(S.receiptTitle))
        assertTrue(blocks.filterIsInstance<Block.Big>().single().value!!.contains("۱۲۵٬۰۰۰"))
        // Undo right after saving = a reversal; the balance is back where it was.
        r.finance.reverse(id, S.undoReason)
        assertEquals(before, r.finance.unitStatement(w.b, u.id).closing)
        w.db.close()
    }

    @Test fun `only resident payments have receipts`() = runBlocking {
        val w = World().setup()
        val e = w.r.finance.refund(w.b, w.unit("1-1").id, 1000, w.cash, w.start, "بازگشت")
        try { w.r.bills.receipt(e); org.junit.Assert.fail() } catch (x: OpException) { assertEquals(OpError.INVALID_INPUT, x.code) }
        w.db.close()
    }

    @Test fun `backup reminder waits for real data and respects later`() {
        val today = 20_000L
        assertFalse(UiPrefs.backupDue(today, 0, 0, hasData = false))
        assertTrue(UiPrefs.backupDue(today, 0, 0, hasData = true))
        assertFalse(UiPrefs.backupDue(today, today - 29, 0, hasData = true))
        assertTrue(UiPrefs.backupDue(today, today - 30, 0, hasData = true))
        assertFalse(UiPrefs.backupDue(today, 0, today + 1, hasData = true))
        assertTrue(UiPrefs.backupDue(today, 0, today, hasData = true))
    }
}
