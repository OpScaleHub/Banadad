package com.github.opscalehub.dong.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.github.opscalehub.dong.share.PayTo.Check as C
import androidx.compose.ui.platform.LocalContext
import com.github.opscalehub.dong.BuildConfig
import com.github.opscalehub.dong.data.BuildingRestoreStatus
import com.github.opscalehub.dong.data.RestoreMode
import com.github.opscalehub.dong.data.RestorePreview
import com.github.opscalehub.dong.data.db.CashAccountEntity
import com.github.opscalehub.dong.data.db.CashKind
import com.github.opscalehub.dong.data.db.FundEntity
import com.github.opscalehub.dong.data.db.FundKind
import com.github.opscalehub.dong.data.newId
import com.github.opscalehub.dong.domain.model.CalendarSystem
import com.github.opscalehub.dong.domain.model.OccupancyBasis
import com.github.opscalehub.dong.i18n.Fa
import com.github.opscalehub.dong.i18n.Fmt
import com.github.opscalehub.dong.io.BackupCodec
import com.github.opscalehub.dong.io.ProblemCode
import com.github.opscalehub.dong.ui.AppViewModel
import com.github.opscalehub.dong.ui.Divider
import com.github.opscalehub.dong.ui.FormDialog
import com.github.opscalehub.dong.ui.Hint
import com.github.opscalehub.dong.ui.KeyValue
import com.github.opscalehub.dong.ui.NumberInput
import com.github.opscalehub.dong.ui.S
import com.github.opscalehub.dong.ui.ScreenList
import com.github.opscalehub.dong.ui.SectionCard
import com.github.opscalehub.dong.ui.SingleChoice
import com.github.opscalehub.dong.ui.TextInput
import com.github.opscalehub.dong.ui.rememberCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun problemText(c: ProblemCode) = when (c) {
    ProblemCode.NOT_A_BACKUP -> "این فایل، پشتیبان «دُنگ» نیست."
    ProblemCode.UNSUPPORTED_VERSION -> "این پشتیبان با نسخه جدیدتری از برنامه ساخته شده است."
    ProblemCode.CHECKSUM_MISMATCH -> "فایل آسیب دیده یا دستکاری شده است (کد کنترل مطابقت ندارد)."
    ProblemCode.MALFORMED -> "ساختار فایل نامعتبر است."
    ProblemCode.TOO_LARGE -> "حجم فایل بیش از حد مجاز است."
    ProblemCode.DUPLICATE_ID -> "شناسه تکراری در فایل وجود دارد."
    ProblemCode.BROKEN_REFERENCE -> "ارجاع نامعتبر بین داده‌های فایل وجود دارد."
    ProblemCode.UNBALANCED_ENTRY -> "سند مالی تراز نشده در فایل وجود دارد."
    ProblemCode.TRIAL_BALANCE -> "تراز آزمایشی فایل صفر نیست."
    ProblemCode.INVALID_VALUE -> "مقدار نامعتبر در فایل وجود دارد."
    ProblemCode.BAD_SNAPSHOT -> "نسخه ثابت یک دوره قطعی قابل خواندن نیست."
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val b by vm.building.collectAsState()
    val building = b ?: return
    val backupNow = rememberBackup(vm)
    val sendBackup = rememberSendBackup(vm)
    ScreenList {
        item {
            val scale by vm.ui.textScale.collectAsState()
            SectionCard(S.displayTitle) {
                Text(S.textSize, style = MaterialTheme.typography.labelLarge)
                SingleChoice(com.github.opscalehub.dong.ui.UiPrefs.SCALES, com.github.opscalehub.dong.ui.UiPrefs.SCALES.minBy { kotlin.math.abs(it - scale) },
                    { vm.ui.setTextScale(it) }) { S.textSizes[com.github.opscalehub.dong.ui.UiPrefs.SCALES.indexOf(it)] }
            }
        }
        item { BuildingSettingsCard(vm) }
        item { PaymentDetailsCard(vm) }
        item {
            SectionCard(S.backupTitle, subtitle = S.backupNote) {
                val last by vm.ui.lastBackup.collectAsState()
                KeyValue(S.backupLast, if (last == 0L) S.backupNone else Fmt.date(last, building.calendar))
                Button(onClick = backupNow, Modifier.fillMaxWidth()) { Text(S.backupNow) }
                OutlinedButton(onClick = sendBackup, Modifier.fillMaxWidth()) { Text(S.backupSendDirect) }
                Text(S.backupSendDirectHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RestoreButton(vm)
            }
        }
        item {
            var auto by remember { mutableStateOf(vm.updater.autoCheck) }
            SectionCard(S.updTitle) {
                KeyValue(S.version, Fmt.digits(BuildConfig.VERSION_NAME))
                com.github.opscalehub.dong.ui.SwitchRow(S.updAuto, auto, { auto = it; vm.updater.autoCheck = it }, supporting = S.updAutoHint)
                OutlinedButton(onClick = { vm.updater.check(manual = true) }, Modifier.fillMaxWidth()) { Text(S.updCheckNow) }
                com.github.opscalehub.dong.ui.UpdateCard(vm.updater, inSettings = true)
            }
        }
        item {
            SectionCard(S.privacyTitle) {
                Text(S.privacyBody, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

}

/**
 * Writes a backup of every building to a file the user picks (the system picker also offers
 * Google Drive), and remembers the day so the home screen stops reminding. Returns the launcher.
 */
@Composable
fun rememberBackup(vm: AppViewModel, onDone: () -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The saved file, until the manager has seen the confirmation (a snackbar alone was missed).
    var saved by remember { mutableStateOf<android.net.Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = vm.backup.export()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("no stream") }
                vm.ui.backedUp(vm.today)
                saved = uri
                onDone()
            } catch (e: Exception) {
                vm.message(S.unexpectedError)
            }
        }
    }
    saved?.let { uri ->
        val name = remember(uri) {
            try {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            } catch (e: Exception) { null }
        }
        // A copy on the same phone is lost with the phone: offer to send one elsewhere right away.
        com.github.opscalehub.dong.ui.ConfirmDialog(S.backupSavedTitle, S.backupSavedBody.format(name?.let { "\u2066$it\u2069" } ?: S.backupFile), { saved = null }, {
            saved = null
            try {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    clipData = android.content.ClipData.newRawUri(name ?: "", uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(send, S.backupSendCopy).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                vm.message(S.unexpectedError)
            }
        }, S.backupSendCopy, dismissText = S.backupKeepHere)
    }
    return { launcher.launch("dang-backup-${Fmt.date(vm.today, vm.building.value?.calendar ?: CalendarSystem.JALALI, persian = false).replace('/', '-')}.json") }
}

/**
 * Sends a backup straight to another app (Telegram, email, Drive) without saving it on the phone
 * first. Saving goes through the system file picker and the phone's media storage; when that is
 * stuck (seen on a real phone: a broken video kept the media service busy for days) the picker
 * never returns and only an empty file is left. This path needs neither.
 */
@Composable
fun rememberSendBackup(vm: AppViewModel): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            try {
                val text = vm.backup.export()
                val name = "dang-backup-${Fmt.date(vm.today, vm.building.value?.calendar ?: CalendarSystem.JALALI, persian = false).replace('/', '-')}.json"
                val file = withContext(Dispatchers.IO) {
                    java.io.File(java.io.File(context.cacheDir, "shared").apply { mkdirs() }, name).apply { writeText(text, Charsets.UTF_8) }
                }
                val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", file)
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    clipData = android.content.ClipData.newRawUri(name, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(send, S.backupSendDirect).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                // The file left the app; where it goes is the manager's choice, so it counts as a backup.
                vm.ui.backedUp(vm.today)
            } catch (e: Exception) {
                vm.message(S.unexpectedError)
            }
        }
    }
}

/** "Restore from backup" button with file picker, bounded read and the preview/confirm dialog. */
@Composable
fun RestoreButton(vm: AppViewModel, modifier: Modifier = Modifier.fillMaxWidth()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<RestorePreview?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        // Untrusted input: bounded read (no API-33 readNBytes), parsed as data only.
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            if (out.size() > BackupCodec.MAX_BYTES) error("too large")
                        }
                        String(out.toByteArray(), Charsets.UTF_8)
                    } ?: error("no stream")
                }
                preview = vm.backup.preview(text)
            } catch (e: Exception) {
                vm.message(problemText(ProblemCode.NOT_A_BACKUP))
            }
        }
    }

    OutlinedButton(onClick = { launcher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier) { Text(S.restore) }
    preview?.let { pv -> RestoreDialog(vm, pv) { preview = null } }
}

@Composable
private fun BuildingSettingsCard(vm: AppViewModel) {
    val b by vm.building.collectAsState()
    val building = b ?: return
    var name by rememberSaveable(building.id) { mutableStateOf(building.name) }
    var address by rememberSaveable(building.id) { mutableStateOf(building.address) }
    var currencyCode by rememberSaveable(building.id) { mutableStateOf(building.currencyCode) }
    var currencyName by rememberSaveable(building.id) { mutableStateOf(building.currencyName) }
    val digits = rememberCount(building.minorDigits.toLong())
    val rounding = rememberCount(building.roundingIncrement)
    var occ by remember(building.id) { mutableStateOf(building.occupancyBasis) }
    var cal by remember(building.id) { mutableStateOf(building.calendar) }
    // Display only: amounts stay stored exactly; Toman = Rial ÷ 10.
    var toman by remember(building.id) { mutableStateOf(building.displayDivisor == 10L) }
    SectionCard(S.buildingSettings, subtitle = S.settingsNote) {
        TextInput(S.buildingName, name, { name = it })
        TextInput(S.address, address, { address = it })
        // Toman or Rial on screen (amounts are stored in Rial either way). Calendar, rounding and how
        // residents are counted keep the values set at setup: an Iranian building never changes them.
        Text(S.displayCurrency, style = MaterialTheme.typography.labelMedium)
        SingleChoice(listOf(true, false), toman, { toman = it }) { if (it) S.showToman else S.showRial }
        Button(onClick = {
            vm.run(S.saved) {
                vm.repos.buildings.updateSettings(building.copy(name = name.trim(), address = address.trim(), currencyCode = currencyCode.trim(),
                    currencyName = currencyName.trim(), minorDigits = digits.value!!.toInt(), roundingIncrement = rounding.value!!, occupancyBasis = occ, calendar = cal,
                    displayCurrencyName = if (toman) S.showToman else "", displayDivisor = if (toman) 10 else 1))
            }
        }, enabled = name.isNotBlank() && digits.valid && (digits.value ?: -1) in 0..4 && (rounding.value ?: 0) >= 1) { Text(S.save) }
        // Units are made by the setup wizard; adding one later is rare, so it lives here, not on the Units tab.
        HorizontalDivider()
        Hint(S.addUnitHint)
        var addingUnit by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { addingUnit = true }) { Text(S.addUnitLater) }
        if (addingUnit) UnitDialog(vm, null, building.id) { addingUnit = false }
    }
}

/** Card number / IBAN and holder printed on bills and reminders. */
@Composable
private fun PaymentDetailsCard(vm: AppViewModel) {
    val b by vm.building.collectAsState()
    val building = b ?: return
    // Stored without spaces; shown in groups of four, left to right, as it is printed on the card.
    var account by rememberSaveable(building.id) { mutableStateOf(com.github.opscalehub.dong.share.PayTo.normalize(building.payAccount)) }
    var holder by rememberSaveable(building.id) { mutableStateOf(building.payHolder) }
    val check = com.github.opscalehub.dong.share.PayTo.check(account)
    // A wrong length is normal while typing: a plain hint, not red, but nothing is saved until it is right.
    val incomplete = check == C.WRONG_LENGTH
    val accountError = when (check) {
        C.BAD_CARD -> S.payBadCard
        C.BAD_IBAN -> S.payBadIban
        else -> null
    }
    val holderError = if (com.github.opscalehub.dong.share.PayTo.looksLikeNumber(holder)) S.payHolderIsNumber else null
    SectionCard(S.payTitle, subtitle = S.payHint) {
        androidx.compose.material3.OutlinedTextField(
            value = account,
            onValueChange = { v -> account = com.github.opscalehub.dong.share.PayTo.normalize(v).take(26) },
            label = { Text(S.payAccount) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            // Numbers read left to right even inside a Persian screen; typing order = card order.
            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
            visualTransformation = GroupsOfFour,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            isError = accountError != null,
            supportingText = { Text(accountError ?: when { check == C.UNKNOWN_BANK -> S.payUnknownBank; incomplete -> S.payWrongLength; else -> S.payAccountHint }) },
        )
        TextInput(S.payHolder, holder, { holder = it }, error = holderError)
        if (check == C.OK || check == C.UNKNOWN_BANK) {
            com.github.opscalehub.dong.share.PayTo.line(account, holder)?.let { Hint(S.payPreview.format(it), MaterialTheme.colorScheme.primary) }
        }
        Button(onClick = {
            vm.run(S.saved) { vm.repos.buildings.updateSettings(building.copy(payAccount = account, payHolder = holder.trim())) }
        }, enabled = accountError == null && !incomplete && holderError == null && (account != building.payAccount || holder.trim() != building.payHolder)) { Text(S.save) }
    }
}

/** Shows a stored card/IBAN in groups of four («IR12 3456 …»), keeping the cursor in place. */
private object GroupsOfFour : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val raw = text.text
        val out = raw.chunked(4).joinToString(" ")
        val map = object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset + (if (offset == 0) 0 else (offset - 1) / 4)
            override fun transformedToOriginal(offset: Int) = (offset - offset / 5).coerceIn(0, raw.length)
        }
        return androidx.compose.ui.text.input.TransformedText(androidx.compose.ui.text.AnnotatedString(out), map)
    }
}

@Composable
private fun RestoreDialog(vm: AppViewModel, pv: RestorePreview, close: () -> Unit) {
    val modes = remember { mutableStateMapOf<String, RestoreMode>().apply {
        pv.buildings.forEach { put(it.buildingId, if (it.status == BuildingRestoreStatus.NEW) RestoreMode.ADD_MISSING else RestoreMode.SKIP) }
    } }
    var typed by rememberSaveable { mutableStateOf("") }
    val replacing = modes.values.any { it == RestoreMode.REPLACE }
    FormDialog(S.restorePreview, close, {
        vm.run(S.restoreDone, onSuccess = close) {
            val r = vm.backup.apply(pv, modes.toMap())
            pv.buildings.firstOrNull { modes[it.buildingId] != RestoreMode.SKIP }?.let { vm.select(it.buildingId) }
            vm.message("${S.restoreDone} ${Fmt.number(r.inserted.toLong())} ${S.records}")
        }
    }, canConfirm = pv.valid && modes.values.any { it != RestoreMode.SKIP } && (!replacing || typed.trim() == S.replaceWord), confirmText = S.applyRestore) {
        if (!pv.valid) {
            Text(S.restoreInvalid, color = MaterialTheme.colorScheme.error)
            pv.problems.forEach { Hint("• " + problemText(it.code), MaterialTheme.colorScheme.error) }
            return@FormDialog
        }
        Hint("${S.version} ${Fmt.digits(pv.header.appVersion)}، ${Fmt.dateTime(pv.header.createdAt)}")
        pv.buildings.forEach { bp ->
            Column {
                Text(bp.name, style = MaterialTheme.typography.titleSmall)
                Hint(when (bp.status) { BuildingRestoreStatus.NEW -> S.restoreNew; BuildingRestoreStatus.IDENTICAL -> S.restoreIdentical; BuildingRestoreStatus.CONFLICT -> S.restoreConflict })
                Hint("${Fmt.number(bp.records.toLong())} ${S.records}، ${Fmt.number(bp.newRecords.toLong())} ${S.newRecords}، ${Fmt.number(bp.differing.toLong())} ${S.differingRecords}")
                val options = buildList {
                    add(RestoreMode.SKIP)
                    if (bp.canAddMissing) add(RestoreMode.ADD_MISSING)
                    if (bp.status != BuildingRestoreStatus.NEW) add(RestoreMode.REPLACE)
                }
                SingleChoice(options, modes[bp.buildingId] ?: RestoreMode.SKIP, { modes[bp.buildingId] = it }) {
                    when (it) { RestoreMode.SKIP -> S.modeSkip; RestoreMode.ADD_MISSING -> S.modeAddMissing; RestoreMode.REPLACE -> S.modeReplace }
                }
            }
            Divider()
        }
        if (replacing) {
            Text(S.replaceWarn, color = MaterialTheme.colorScheme.error)
            TextInput(S.replaceWord, typed, { typed = it })
        }
    }
}

