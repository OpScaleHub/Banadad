package com.github.opscalehub.dong.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.core.content.FileProvider
import com.github.opscalehub.dong.BuildConfig
import com.github.opscalehub.dong.data.Diagnostics
import com.github.opscalehub.dong.data.Health
import com.github.opscalehub.dong.data.SelfCheck
import com.github.opscalehub.dong.share.BillShare
import com.github.opscalehub.dong.ui.AppViewModel
import com.github.opscalehub.dong.ui.Hint
import com.github.opscalehub.dong.ui.S
import com.github.opscalehub.dong.ui.ScreenList
import com.github.opscalehub.dong.ui.SectionCard
import com.github.opscalehub.dong.ui.SwitchRow
import com.github.opscalehub.dong.ui.TextInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Device, app and self-check context appended to every feedback message: versions, counts and
 * error codes only, no names, labels or amounts. Shown on screen before sending.
 */
private fun context(vm: AppViewModel, health: Health?): String = buildList {
    // Latin values are isolated left-to-right so they read correctly inside Persian lines.
    fun ltr(t: String) = "\u2066$t\u2069"
    add("${S.fbApp}: ${ltr("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")}")
    add("${S.fbAndroid}: ${ltr(Build.VERSION.RELEASE)}، ${S.fbPhone}: ${ltr("${Build.MANUFACTURER} ${Build.MODEL}")}")
    add("${S.fbTextSize}: ${(vm.ui.textScale.value * 100).toInt()}٪")
    if (health != null) {
        add("${S.fbCheck}: " + ltr(if (health.ok) "ok" else health.problems.joinToString(" ")))
        add(ltr(health.summary))
    }
    Diagnostics.recent().takeIf { it.isNotEmpty() }?.let { add("${S.fbErrors}: " + ltr(it.joinToString(" "))) }
}.joinToString("\n")

/**
 * Feedback goes by email to the support address published on the app's store page, so anyone
 * who installed the app can reach the developer. Without an email app, the share sheet is used.
 */
private fun email(ctx: Context, text: String) {
    val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(BuildConfig.SUPPORT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, S.fbSubject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        ctx.startActivity(mail)
    } catch (e: ActivityNotFoundException) {
        BillShare.shareText(ctx, "${S.fbTo}: ${BuildConfig.SUPPORT_EMAIL}\n\n$text")
    }
}

/** Same message with the building's backup file attached; any app that takes a file can send it. */
private fun emailWithFile(ctx: Context, text: String, file: File) {
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(BuildConfig.SUPPORT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, S.fbSubject)
        putExtra(Intent.EXTRA_TEXT, "${S.fbTo}: ${BuildConfig.SUPPORT_EMAIL}\n\n$text")
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = android.content.ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, S.fbSend).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Opens the app's page in Cafe Bazaar to rate and review it (Bazaar edition only). */
private fun rateInBazaar(ctx: Context): Boolean = try {
    ctx.startActivity(Intent(Intent.ACTION_EDIT, Uri.parse("bazaar://details?id=${BuildConfig.APPLICATION_ID}")).setPackage("com.farsitel.bazaar"))
    true
} catch (e: ActivityNotFoundException) {
    false
}

/**
 * «نظر یا گزارش مشکل»: a few guided questions, sent by email to the developer, with the building's
 * self-check result and, only if the manager turns it on, the building's backup file.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedbackScreen(vm: AppViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val kinds = listOf(S.fbProblem, S.fbConfused, S.fbIdea, S.fbGood)
    var kind by rememberSaveable { mutableStateOf(kinds[0]) }
    var doing by rememberSaveable { mutableStateOf("") }
    var happened by rememberSaveable { mutableStateOf("") }
    var attach by rememberSaveable { mutableStateOf(false) }
    val building by vm.building.collectAsState()
    val buildingId = building?.id
    val scope = rememberCoroutineScope()
    val health by produceState<Health?>(null, buildingId) {
        value = buildingId?.let { id -> withContext(Dispatchers.IO) { SelfCheck.run(vm.repos, id) } }
    }

    ScreenList {
        if (BuildConfig.STORE_BAZAAR) item {
            SectionCard(S.fbRateTitle) {
                Hint(S.fbRateHint)
                OutlinedButton(onClick = { if (!rateInBazaar(ctx)) vm.message(S.fbNoBazaar) }, Modifier.fillMaxWidth()) { Text(S.fbRate) }
            }
        }
        item { SectionCard { Hint(S.fbIntro.format(BuildConfig.SUPPORT_EMAIL)) } }
        if (buildingId != null) item {
            SectionCard(S.fbCheckTitle) {
                val h = health
                when {
                    h == null -> Hint(S.fbChecking)
                    h.ok -> Hint(S.fbCheckOk)
                    else -> Text(S.fbCheckBad, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            SectionCard(S.fbKind) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    kinds.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k, style = MaterialTheme.typography.bodyLarge) }) }
                }
                TextInput(S.fbDoing, doing, { doing = it }, singleLine = false)
                TextInput(S.fbHappened, happened, { happened = it }, singleLine = false)
                if (buildingId != null) SwitchRow(S.fbAttach, attach, { attach = it }, supporting = S.fbAttachHint)
            }
        }
        item {
            SectionCard(S.fbTech) { Hint(context(vm, health)) }
        }
        item {
            Button(onClick = {
                val text = buildString {
                    appendLine("${S.fbKind}: $kind")
                    if (doing.isNotBlank()) appendLine("${S.fbDoing} ${doing.trim()}")
                    if (happened.isNotBlank()) appendLine("${S.fbHappened} ${happened.trim()}")
                    appendLine("—")
                    append(context(vm, health))
                }
                scope.launch {
                    try {
                        if (attach && buildingId != null) {
                            val backup = vm.backup.export(listOf(buildingId))
                            val file = withContext(Dispatchers.IO) {
                                File(File(ctx.cacheDir, "shared").apply { mkdirs() }, "dang-backup-report.json").apply { writeText(backup) }
                            }
                            emailWithFile(ctx, text, file)
                        } else email(ctx, text)
                        nav.popBackStack()
                    } catch (e: Exception) {
                        vm.message(S.unexpectedError)
                    }
                }
            }, Modifier.fillMaxWidth(), enabled = doing.isNotBlank() || happened.isNotBlank() || health?.ok == false) { Text(S.fbSend) }
        }
    }
}
