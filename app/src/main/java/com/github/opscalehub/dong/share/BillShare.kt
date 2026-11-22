package com.github.opscalehub.dong.share

import com.github.opscalehub.dong.i18n.Fmt
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import com.github.opscalehub.dong.DongApp
import com.github.opscalehub.dong.i18n.MoneyFormat
import com.github.opscalehub.dong.ui.S
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Content of one unit's bill; built by [com.github.opscalehub.dong.data.BillRepository]. */
data class Bill(
    val building: String,
    val unit: String,
    val period: String?,
    /** This period's charges by category name. */
    val lines: List<Pair<String, Long>>,
    val previous: Long,
    val paid: Long,
    /** Current balance: > 0 owed, < 0 credit. */
    val due: Long,
    val date: String,
    /** Charges of months after [period] (a late-sent bill), already in [due]. */
    val later: Long = 0,
    /** Credits, adjustments and refunds since the month began, signed as they move [due]. */
    val adjust: Long = 0,
    /** The month's payment deadline, formatted; shown while something is owed. */
    val dueDate: String? = null,
    /** Where to pay (building settings); blank = not shown. */
    val payAccount: String = "",
    val payHolder: String = "",
    /**
     * An open month's bills that are charged when the month closes (e.g. a repair the residents
     * prepay), by description. Not in [due]; shown apart in amber so residents can pay ahead.
     */
    val pending: List<Pair<String, Long>> = emptyList(),
    /** Special charges («شارژ فوق‌العاده») issued since the month began, by title; already in [due]. */
    val special: List<Pair<String, Long>> = emptyList(),
) {
    val pendingTotal: Long get() = pending.sumOf { it.second }
}

/** One resident payment, as a receipt the manager sends back. */
data class Receipt(
    val building: String,
    val unit: String,
    val amount: Long,
    val date: String,
    val method: String,
    val reference: String,
    /** The unit's balance now: > 0 still owed, < 0 credit. */
    val balance: Long,
    val today: String,
)

/** Where residents pay, for bills and messages. */
object PayTo {
    /**
     * A card number (16 digits) or IBAN (IR + 24 digits) in groups of four, kept left-to-right
     * inside Persian text (otherwise the bidi algorithm reverses the groups). Anything else is
     * returned as typed.
     */
    fun format(account: String): String {
        val raw = com.github.opscalehub.dong.i18n.Fmt.latin(account).filter { !it.isWhitespace() && it != '-' }.uppercase()
        val grouped = when {
            raw.length == 16 && raw.all { it.isDigit() } -> raw.chunked(4).joinToString(" ")
            raw.length == 26 && raw.startsWith("IR") && raw.drop(2).all { it.isDigit() } -> raw.chunked(4).joinToString(" ")
            else -> account.trim()
        }
        return "\u2066$grouped\u2069"
    }

    /** Card or IBAN as stored: Latin digits only, and «IR» in front of a 24-digit IBAN. */
    fun normalize(input: String): String {
        val raw = com.github.opscalehub.dong.i18n.Fmt.latin(input).uppercase().filter { it.isLetterOrDigit() }
        val digits = raw.filter { it.isDigit() }
        return if (raw.startsWith("IR") || digits.length == 24) "IR$digits" else digits
    }

    /** Six-digit card prefixes of Iranian banks (Shetab), to catch a number typed in the wrong order. */
    private val BINS = setOf(
        "603799", "589210", "627648", "627961", "603770", "628023", "627760", "502908", "627412", "622106", "639194",
        "502229", "639347", "627488", "502910", "621986", "639346", "639607", "636214", "502806", "502938", "603769",
        "610433", "991975", "627353", "585983", "589463", "627381", "505785", "636795", "504172", "505416", "606373",
        "628157", "505801", "639370", "639599", "507677", "606256", "504706", "636949", "585947", "639217",
    )

    private fun luhn(d: String): Boolean = d.reversed().mapIndexed { i, c ->
        val x = (c - '0') * (if (i % 2 == 1) 2 else 1); if (x > 9) x - 9 else x
    }.sum() % 10 == 0

    private fun ibanOk(iban: String): Boolean {
        val moved = iban.drop(4) + "1827" + iban.substring(2, 4) // I=18, R=27
        return java.math.BigInteger(moved).mod(java.math.BigInteger.valueOf(97)).toInt() == 1
    }

    enum class Check { OK, EMPTY, WRONG_LENGTH, BAD_CARD, UNKNOWN_BANK, BAD_IBAN }

    /**
     * Checks a card (16 digits, Luhn) or IBAN (IR + 24 digits, mod 97). [Check.UNKNOWN_BANK] is a
     * warning only: a valid card that starts with no Iranian bank prefix was most likely typed in
     * the wrong order (a right-to-left field shows the groups reversed, and testers "fixed" it).
     */
    fun check(account: String): Check {
        val n = normalize(account)
        val letters = com.github.opscalehub.dong.i18n.Fmt.latin(account).uppercase().removePrefix("IR").any { it.isLetter() }
        return when {
            account.isBlank() -> Check.EMPTY
            letters || n.isEmpty() -> Check.WRONG_LENGTH
            n.startsWith("IR") -> if (n.length != 26) Check.WRONG_LENGTH else if (ibanOk(n)) Check.OK else Check.BAD_IBAN
            n.length != 16 -> Check.WRONG_LENGTH
            !luhn(n) -> Check.BAD_CARD
            n.take(6) !in BINS -> Check.UNKNOWN_BANK
            else -> Check.OK
        }
    }

    /** True when a name field holds a number (a card typed into «به نام»). */
    fun looksLikeNumber(holder: String): Boolean = com.github.opscalehub.dong.i18n.Fmt.latin(holder).count { it.isDigit() } >= 8

    /** A line for reminder messages, or null when the building has no payment details. */
    fun line(account: String, holder: String): String? = when {
        account.isBlank() -> null
        holder.isBlank() -> S.payLine.format(format(account))
        else -> S.payLineHolder.format(format(account), holder.trim())
    }

    /** The reminder message: balance, the deadline if any, and where to pay. */
    fun reminder(unit: String, building: String, amount: String, due: String?, account: String, holder: String): String =
        listOfNotNull(S.reminderText.format(unit, building, amount), due?.let { S.dueLine.format(it) }, line(account, holder)).joinToString("\n")
}

/** A unit's bill as an image, and sharing it so the app learns that it went out. */
object BillShare {

    fun blocks(bill: Bill, money: MoneyFormat): List<Block> = buildList {
        add(Block.Title(S.billTitle + (bill.period?.let { " — $it" } ?: "")))
        add(Block.Subtitle("${bill.building}، ${S.unit} ${Fmt.ltr(bill.unit)}"))
        if (bill.lines.isNotEmpty()) {
            add(Block.Section(S.billThisMonth))
            bill.lines.forEach { (k, v) -> add(Block.Row(k, money.format(v))) }
            add(Block.Row(S.total, money.format(bill.lines.sumOf { it.second }), bold = true))
            add(Block.Rule)
        }
        if (bill.special.isNotEmpty()) {
            add(Block.Section(S.specialCharge))
            bill.special.forEach { (k, v) -> add(Block.Row(k, money.format(v))) }
            add(Block.Rule)
        }
        // previous + this month + special + later + adjust − paid = due, so the image always adds up.
        add(Block.Row(S.billPrevious, money.format(bill.previous)))
        if (bill.later != 0L) add(Block.Row(S.billLater, money.format(bill.later)))
        if (bill.adjust != 0L) add(Block.Row(S.billAdjust, com.github.opscalehub.dong.i18n.Fmt.signed(bill.adjust > 0, money.format(kotlin.math.abs(bill.adjust)))))
        add(Block.Row(S.billPaid, money.format(bill.paid)))
        add(Block.Big(
            when {
                bill.due > 0 -> S.billTotalDue
                bill.due < 0 -> S.billCredit
                else -> S.billSettled
            },
            if (bill.due != 0L) money.withUnit(kotlin.math.abs(bill.due)) else null,
            if (bill.due > 0) ImageDoc.RED else ImageDoc.TEAL,
        ))
        if (bill.due > 0 && bill.dueDate != null) add(Block.Row(S.billDue, bill.dueDate, bold = true))
        // Bills of the open month that are charged at closing: shown apart (amber), not in the total
        // above, so residents see what is coming and can pay it ahead.
        if (bill.pending.isNotEmpty()) {
            add(Block.Rule)
            add(Block.Section(S.billPending, ImageDoc.AMBER))
            add(Block.Note(S.billPendingNote, ImageDoc.AMBER))
            bill.pending.forEach { (k, v) -> add(Block.Row(k, money.format(v), color = ImageDoc.AMBER)) }
            if (bill.pending.size > 1) add(Block.Row(S.total, money.format(bill.pendingTotal), bold = true, color = ImageDoc.AMBER))
            val withPending = bill.due + bill.pendingTotal
            if (withPending > 0) add(Block.Row(S.billWithPending, money.withUnit(withPending), bold = true))
        }
        if ((bill.due > 0 || bill.due + bill.pendingTotal > 0) && bill.payAccount.isNotBlank()) {
            add(Block.Rule)
            add(Block.Section(S.payTo))
            add(Block.Row(S.payAccount, PayTo.format(bill.payAccount), bold = true))
            if (bill.payHolder.isNotBlank()) add(Block.Row(S.payHolder, bill.payHolder.trim()))
        }
        add(Block.Footer("${bill.date}، ${S.billFooter}"))
    }

    fun render(context: Context, bill: Bill, money: MoneyFormat): Bitmap = ImageDoc.render(context, blocks(bill, money))

    /**
     * Shares the bill. When it is for a closed [periodId], the app the user picks in the share
     * sheet is recorded as the bill having been sent (see [ChosenReceiver]); dismissing the sheet
     * records nothing.
     */
    fun shareBill(context: Context, bill: Bill, money: MoneyFormat, reminder: String, periodId: String? = null, unitId: String? = null) {
        val callback = if (periodId != null && unitId != null) {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val intent = Intent(context, ChosenReceiver::class.java).putExtra(EXTRA_PERIOD, periodId).putExtra(EXTRA_UNIT, unitId)
            PendingIntent.getBroadcast(context, (periodId + unitId).hashCode(), intent, flags).intentSender
        } else null
        ImageDoc.share(context, render(context, bill, money), "bill-${ImageDoc.asciiName(bill.unit, "unit")}", reminder, callback)
    }

    fun shareText(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        context.startActivity(Intent.createChooser(send, S.sharedVia).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    internal const val EXTRA_PERIOD = "periodId"
    internal const val EXTRA_UNIT = "unitId"

    /** Told by the share sheet which app received a bill; records the bill as sent. */
    class ChosenReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val period = intent.getStringExtra(EXTRA_PERIOD) ?: return
            val unit = intent.getStringExtra(EXTRA_UNIT) ?: return
            val chosen = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT, android.content.ComponentName::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT)
            val pending = goAsync()
            val repos = (context.applicationContext as DongApp).container.repos
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    repos.bills.markSent(period, unit, chosen?.packageName ?: "share")
                } catch (e: Exception) {
                    android.util.Log.i("Dang", "bill share not recorded: ${e.javaClass.simpleName}")
                } finally {
                    pending.finish()
                }
            }
        }
    }
}

/** A receipt for one payment: amount, date, how, and what is still owed. */
object ReceiptShare {
    fun blocks(r: Receipt, money: MoneyFormat): List<Block> = buildList {
        add(Block.Title(S.receiptTitle))
        add(Block.Subtitle("${r.building}، ${S.unit} ${Fmt.ltr(r.unit)}"))
        add(Block.Big(S.receiptAmount, money.withUnit(r.amount), ImageDoc.TEAL))
        add(Block.Row(S.receiptDate, r.date))
        if (r.method.isNotBlank()) add(Block.Row(S.receiptMethod, r.method))
        if (r.reference.isNotBlank()) add(Block.Row(S.receiptRef, "\u2066${r.reference.trim()}\u2069"))
        add(Block.Rule)
        add(Block.Row(
            when { r.balance > 0 -> S.receiptRemaining; r.balance < 0 -> S.billCredit; else -> S.billSettled },
            if (r.balance != 0L) money.withUnit(kotlin.math.abs(r.balance)) else "",
            bold = true,
        ))
        add(Block.Footer("${r.today}، ${S.billFooter}"))
    }

    fun share(context: Context, r: Receipt, money: MoneyFormat) =
        ImageDoc.share(context, ImageDoc.render(context, blocks(r, money)), "receipt-${ImageDoc.asciiName(r.unit, "unit")}",
            S.receiptText.format(money.withUnit(r.amount), Fmt.ltr(r.unit), r.building))
}

/** The month at a glance for the building's group or channel: totals, expenses, optionally units. */
object SummaryShare {
    fun blocks(s: com.github.opscalehub.dong.data.MonthSummary, money: MoneyFormat, showUnits: Boolean, showDebts: Boolean): List<Block> = buildList {
        add(Block.Title("${S.summaryTitle} — ${s.period}"))
        add(Block.Subtitle(s.building))
        if (s.billed > 0) {
            val pct = (s.collected * 100 / s.billed).toInt()
            add(Block.Row(S.billed, money.format(s.billed)))
            add(Block.Bar("${S.collected} (${com.github.opscalehub.dong.i18n.Fmt.digits(pct.toString())}٪)", money.format(s.collected), s.collected.toFloat() / s.billed))
            add(Block.Row(S.remaining, money.format(s.billed - s.collected), bold = true))
            add(Block.Rule)
        }
        if (s.expenses.isNotEmpty()) {
            add(Block.Section(S.summaryExpenses))
            val max = s.expenses.maxOf { it.second }.toFloat()
            val top = s.expenses.take(7)
            top.forEach { (k, v) -> add(Block.Bar(k, money.format(v), v / max)) }
            val rest = s.expenses.drop(7).sumOf { it.second }
            if (rest > 0) add(Block.Bar(S.otherCategories, money.format(rest), rest / max))
            add(Block.Row(S.expensesTotal, money.format(s.expenses.sumOf { it.second }), bold = true))
            add(Block.Rule)
        }
        if (showUnits && s.units.isNotEmpty()) {
            add(Block.Section(if (showDebts) S.summaryUnitsWithDebt else S.summaryUnits))
            s.units.forEach { (label, charge, balance) ->
                val v = money.format(charge) + if (showDebts && balance > 0) "   (${S.owes} ${money.format(balance)})" else ""
                add(Block.Row("${S.unit} ${Fmt.ltr(label)}", v))
            }
            add(Block.Rule)
        }
        add(Block.Row(S.availableCash, money.format(s.cash)))
        if (s.reserve != 0L) add(Block.Row(S.reserveFund, money.format(s.reserve)))
        add(Block.Footer("${money.unitName}، ${s.date}، ${S.billFooter}"))
    }

    fun share(context: Context, s: com.github.opscalehub.dong.data.MonthSummary, money: MoneyFormat, showUnits: Boolean, showDebts: Boolean) =
        ImageDoc.share(context, ImageDoc.render(context, blocks(s, money, showUnits, showDebts)), "summary", null)
}
