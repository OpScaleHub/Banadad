package com.github.opscalehub.dong.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.opscalehub.dong.data.PeriodRepository
import com.github.opscalehub.dong.data.SampleData
import com.github.opscalehub.dong.data.db.PeriodStatus
import com.github.opscalehub.dong.domain.allocation.ComponentPart
import com.github.opscalehub.dong.domain.period.Severity
import com.github.opscalehub.dong.i18n.Fa
import com.github.opscalehub.dong.i18n.Fmt
import com.github.opscalehub.dong.io.StatementRenderer
import com.github.opscalehub.dong.ui.AppViewModel
import com.github.opscalehub.dong.ui.Badge
import com.github.opscalehub.dong.ui.ClickRow
import com.github.opscalehub.dong.ui.ConfirmDialog
import com.github.opscalehub.dong.ui.DateInput
import com.github.opscalehub.dong.ui.Divider
import com.github.opscalehub.dong.ui.FormDialog
import com.github.opscalehub.dong.ui.Hint
import com.github.opscalehub.dong.ui.KeyValue
import com.github.opscalehub.dong.ui.ReasonDialog
import com.github.opscalehub.dong.ui.Routes
import com.github.opscalehub.dong.ui.S
import com.github.opscalehub.dong.ui.money
import com.github.opscalehub.dong.ui.ScreenList
import com.github.opscalehub.dong.ui.SectionCard
import com.github.opscalehub.dong.ui.TextInput
import com.github.opscalehub.dong.ui.rememberDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PeriodsScreen(vm: AppViewModel, nav: NavController) {
    val b by vm.building.collectAsState()
    val building = b ?: return
    val periods by vm.periods.collectAsState()
    var creating by remember { mutableStateOf(false) }

    WithFab(S.newPeriod, { creating = true }) {
        ScreenList {
            if (periods.isEmpty()) item { SectionCard { Hint(S.noPeriod) } }
            items(periods.size) { i ->
                val p = periods[i]
                SectionCard {
                    ClickRow({ nav.navigate(Routes.period(p.id)) }) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Hint("${Fmt.date(p.startDate, building.calendar)} — ${Fmt.date(p.endDate, building.calendar)}")
                        }
                        Badge(if (p.status == PeriodStatus.FINALIZED) S.finalized else S.draft,
                            if (p.status == PeriodStatus.FINALIZED) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer)
                    }
                }
            }
        }
    }
    if (creating) {
        // Default: the month after the latest period, or the current month.
        val m = periods.maxByOrNull { it.endDate }?.let { SampleData.monthAfter(it.endDate) } ?: SampleData.month(vm.today)
        var name by rememberSaveable { mutableStateOf(m.name) }
        val start = rememberDate(m.start, building.calendar)
        val end = rememberDate(m.end, building.calendar)
        // A fixed monthly charge is paid in advance, by the 10th; split bills 10 days after the month.
        val chargeItems by vm.chargeItems.collectAsState()
        val prepaid = chargeItems.any { it.active && it.inAdvance && it.mode != com.github.opscalehub.dong.domain.model.ChargeMode.WATER_TARIFF }
        val due = rememberDate(if (prepaid) m.start + com.github.opscalehub.dong.data.PREPAID_DUE_DAYS - 1 else m.end + 10, building.calendar)
        FormDialog(S.newPeriod, { creating = false }, {
            vm.run(S.saved, onSuccess = { creating = false }) { vm.repos.periods.create(building.id, name, start.value!!, end.value!!, due.value) }
        }, canConfirm = name.isNotBlank() && start.value != null && end.value != null && end.value!! >= start.value!!) {
            TextInput(S.periodName, name, { name = it })
            DateInput(S.startDate, start, vm.today)
            DateInput(S.endDate, end, vm.today)
            DateInput(S.dueDate, due, vm.today, allowEmpty = true)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeriodScreen(vm: AppViewModel, nav: NavController, periodId: String) {
    val b by vm.building.collectAsState()
    val building = b ?: return
    val periods by vm.periods.collectAsState()
    val units by vm.units.collectAsState()
    val p = periods.firstOrNull { it.id == periodId } ?: return
    val finalized = p.status == PeriodStatus.FINALIZED
    val result by derived(vm, periodId, p.status) { if (finalized) vm.repos.periods.effectiveResult(periodId) else vm.repos.periods.preview(periodId) }
    val revisions by derived(vm, periodId) { vm.repos.db.periodDao().revisions(periodId) }
    // Payments received in this month per unit, so a month with nothing charged yet still shows who paid.
    val entries by vm.entries.collectAsState()
    val paidIn by derived(vm, periodId, entries.size) { vm.reports.periodStatements(building.id, periodId).mapValues { it.value.payments } }
    val chargeItems by vm.chargeItems.collectAsState()
    val hasMonthlyCharge = chargeItems.any { it.active && it.inAdvance && it.mode != com.github.opscalehub.dong.domain.model.ChargeMode.WATER_TARIFF }
    var confirmFinalize by remember { mutableStateOf(false) }
    var correction by remember { mutableStateOf<PeriodRepository.CorrectionPreview?>(null) }
    var askReason by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    // After closing a month: the natural moment for a backup (once a month, with the books final).
    var offerBackup by remember { mutableStateOf(false) }
    val backupNow = rememberBackup(vm)
    var expanded by remember { mutableStateOf<String?>(null) }
    val names = vm.names()
    val digits = building.minorDigits
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportHtml = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        if (uri != null) scope.launch {
            try {
                val data = vm.reports.load(building.id)
                val html = StatementRenderer.html(data, p, vm.reports.periodStatements(building.id, p.id))
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(html.toByteArray()) } }
                vm.message(S.exported)
            } catch (e: Exception) {
                vm.message(S.unexpectedError)
            }
        }
    }

    ScreenList {
        item {
            SectionCard(p.name) {
                KeyValue(S.startDate, Fmt.longDate(p.startDate, building.calendar))
                KeyValue(S.endDate, Fmt.longDate(p.endDate, building.calendar))
                p.dueDate?.let { KeyValue(S.dueDate, Fmt.longDate(it, building.calendar)) }
                KeyValue(S.status, if (finalized) S.finalized else S.draft, strong = true)
                if (finalized) {
                    p.finalizedAt?.let { Hint("${S.finalized}: ${Fmt.dateTime(it, building.calendar)}، ${S.snapshotNote}") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!finalized) {
                        Button(onClick = { confirmFinalize = true }, enabled = result?.canFinalize == true) { Text(S.finalize) }
                        OutlinedButton(onClick = { editing = true }) { Text(S.editMonth) }
                    }
                    OutlinedButton(onClick = { exportHtml.launch("statement-${p.name}.html") }) { Text(S.exportStatement) }
                }
                if (!finalized && result?.canFinalize == false) Hint(S.cannotCloseYet, MaterialTheme.colorScheme.error)
                // Deleting the month is rare and destructive: a quiet text button, not a peer of «بستن ماه».
                if (!finalized) TextButton(onClick = { confirmDelete = true }) { Text(S.deleteDraft, color = MaterialTheme.colorScheme.error) }
                if (finalized) Button(onClick = { nav.navigate(com.github.opscalehub.dong.ui.Routes.send(p.id)) }, Modifier.fillMaxWidth()) { Text(S.sendBills) }
                SummaryShareButton(vm, building, p)
            }
        }
        val r = result
        // Nothing to charge (no monthly charge, no bill split among units): say why and what to do,
        // instead of «همه‌چیز آماده است» over a list of zeros.
        val empty = r != null && !finalized && r.total == 0L
        if (empty) item {
            SectionCard(S.emptyMonthTitle) {
                Hint(if (hasMonthlyCharge) S.emptyMonthBills else S.emptyMonthNoCharge)
                if (!hasMonthlyCharge) Button(onClick = { nav.navigate(com.github.opscalehub.dong.ui.Routes.CHARGE_ITEMS) }, Modifier.fillMaxWidth()) { Text(S.setMonthlyCharge) }
            }
        }
        if (r != null) {
            item {
                SectionCard(S.issues) {
                    val shown = r.issues.filter { it.severity != Severity.INFO || it.code.name.startsWith("UNALLOCATED") || it.code.name.startsWith("FUND_BORNE") }
                    if (!finalized && !empty && r.issues.none { it.blocking }) Hint(S.noIssues, MaterialTheme.colorScheme.primary)
                    IssueList(shown, names, onReadings = if (finalized) null else { { nav.navigate(com.github.opscalehub.dong.ui.Routes.READINGS) } })
                }
            }
            item {
                SectionCard(S.sources) {
                    KeyValue(S.chargesTotal, money(r.total), strong = true)
                    if (r.fundBorneTotal != 0L) KeyValue(S.fundBorne, money(r.fundBorneTotal))
                    r.sources.forEach { src ->
                        Divider()
                        ClickRow({ expanded = if (expanded == src.sourceId) null else src.sourceId }) {
                            Column(Modifier.weight(1f)) {
                                Text(src.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Hint(src.allocation.components.joinToString(" + ") { Fa.basisKind(it.basis) + if (it.part != ComponentPart.WHOLE) " (${Fa.part(it.part)})" else "" }
                                    .ifBlank { if (src.allocation.fundBorne != 0L) S.fundBorne else "" })
                            }
                            Text(money(src.allocation.amount))
                        }
                        if (expanded == src.sourceId) {
                            src.allocation.components.forEach { c ->
                                Hint("${Fa.basisKind(c.basis)} ${Fa.part(c.part)} — ${money(c.amount)}" + if (c.fundBorne != 0L) " (${S.fundBorne})" else "")
                                c.units.forEach { u ->
                                    Row(Modifier.fillMaxWidth()) {
                                        Text("${S.unit} ${names.unit(u.unitId)}: ${Fa.quantity(u.quantityKind, u.quantity)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                        Text(money(u.share) + if (u.roundingAdjustment != 0L) " *" else "", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            Hint(S.roundingFootnote)
                        }
                    }
                }
            }
            item {
                SectionCard(S.perUnitTotals, subtitle = if (paidIn.orEmpty().values.any { it != 0L }) S.perUnitWithPaid else null) {
                    val totals = r.unitTotals
                    val paid = paidIn.orEmpty()
                    units.forEach { u ->
                        val got = paid[u.id] ?: 0L
                        KeyValue("${S.unit} ${Fmt.ltr(u.label)}", money(totals[u.id] ?: 0) + if (got != 0L) "  (${S.paidShort} ${money(got)})" else "")
                    }
                }
            }
            if (r.water.meters.isNotEmpty()) {
                item {
                    SectionCard(S.waterOfPeriod, subtitle = S.unallocatedNote) {
                        KeyValue(S.mainConsumption, r.water.mainLiters?.let { "${Fmt.cubicMeters(it)} ${S.m3}" } ?: S.none)
                        KeyValue(S.unitsConsumption, "${Fmt.cubicMeters(r.water.unitSumLiters)} ${S.m3}")
                        KeyValue(S.unallocated, r.water.unallocatedLiters?.let { "${Fmt.cubicMeters(it)} ${S.m3}" } ?: S.none, strong = true)
                        r.water.unitLiters.forEach { (u, l) -> KeyValue("${S.unit} ${names.unit(u)}", l?.let { "${Fmt.cubicMeters(it)} ${S.m3}" } ?: S.none) }
                    }
                }
            }
        }
        if (finalized) {
            item {
                SectionCard(S.correction, subtitle = S.correctionHint) {
                    OutlinedButton(onClick = { vm.run { correction = vm.repos.periods.previewCorrection(periodId) } }) { Text(S.previewCorrection) }
                    correction?.let { c ->
                        if (c.diff.isEmpty()) Hint(S.noDifference) else {
                            c.diff.forEach { d -> KeyValue("${S.unit} ${names.unit(d.unitId)}، ${names.source(d.sourceId)}", if (d.amount > 0) Fmt.signed(true, money(d.amount)) else money(d.amount)) }
                            c.result.blockingIssues.forEach { Hint(Fa.issue(it, names), MaterialTheme.colorScheme.error) }
                            Button(onClick = { askReason = true }, enabled = c.result.canFinalize) { Text(S.applyCorrection) }
                        }
                    }
                    revisions?.takeIf { it.isNotEmpty() }?.let { revs ->
                        Divider()
                        Text(S.revisions, style = MaterialTheme.typography.labelLarge)
                        revs.forEach { rv -> Hint("${Fmt.number(rv.revision.toLong())}، ${Fmt.dateTime(rv.createdAt, building.calendar)}، ${rv.reason}") }
                    }
                }
            }
        }
    }
    if (confirmFinalize) {
        // The first close locks the starting balances: show them once more.
        val first = periods.none { it.status == PeriodStatus.FINALIZED }
        val opening by derived(vm, building.id, first) { if (first) vm.repos.finance.opening(building.id) to 0L else null }
        val warn = opening?.let { (o, _) ->
            val mf = com.github.opscalehub.dong.i18n.MoneyFormat.of(building)
            S.firstCloseWarn.format(mf.withUnit(o.cash.values.sum()), mf.withUnit(o.units.values.filter { it > 0 }.sum()), mf.withUnit(-o.units.values.filter { it < 0 }.sum()))
        } ?: ""
        ConfirmDialog(S.finalize, S.finalizeConfirm + warn, { confirmFinalize = false }, {
            confirmFinalize = false
            vm.run(S.finalizedOk, onSuccess = { offerBackup = vm.ui.lastBackup.value != vm.today }) { vm.repos.periods.finalize(periodId) }
        }, S.finalize)
    }
    if (offerBackup) {
        ConfirmDialog(S.backupTitle, S.backupAfterClose + "\n\n" + S.backupWhy, { offerBackup = false }, {
            offerBackup = false
            backupNow()
        }, S.backupNow)
    }
    if (askReason) {
        ReasonDialog(S.applyCorrection, S.correctionHint, { askReason = false }) { reason ->
            askReason = false
            vm.run(S.done, onSuccess = { correction = null }) { vm.repos.periods.applyCorrection(periodId, reason) }
        }
    }
    if (editing) {
        var name by rememberSaveable { mutableStateOf(p.name) }
        val start = rememberDate(p.startDate, building.calendar)
        val end = rememberDate(p.endDate, building.calendar)
        val due = rememberDate(p.dueDate, building.calendar)
        val ok = name.isNotBlank() && start.value != null && end.value != null && end.value!! >= start.value!! && (due.text.isBlank() || due.value != null)
        FormDialog(S.editMonth, { editing = false }, {
            vm.run(S.saved, onSuccess = { editing = false }) {
                vm.repos.periods.updateDraft(p.copy(name = name, startDate = start.value!!, endDate = end.value!!, dueDate = due.value))
            }
        }, canConfirm = ok) {
            TextInput(S.periodName, name, { name = it })
            DateInput(S.startDate, start, vm.today)
            DateInput(S.endDate, end, vm.today)
            DateInput(S.dueDate, due, vm.today, allowEmpty = true)
        }
    }
    if (confirmDelete) {
        // Most deletions were meant as edits (testers deleted the month to fix its dates).
        ConfirmDialog(S.deleteDraft, "${p.name}\n\n${S.editMonthHint}", { confirmDelete = false }, {
            confirmDelete = false
            vm.run(S.done, onSuccess = { nav.popBackStack() }) { vm.repos.periods.deleteDraft(periodId) }
        }, S.delete)
    }
}
