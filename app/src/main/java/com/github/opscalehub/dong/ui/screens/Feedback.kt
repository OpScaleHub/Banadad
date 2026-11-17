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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.opscalehub.dong.BuildConfig
import com.github.opscalehub.dong.i18n.Fmt
import com.github.opscalehub.dong.share.BillShare
import com.github.opscalehub.dong.ui.AppViewModel
import com.github.opscalehub.dong.ui.Hint
import com.github.opscalehub.dong.ui.S
import com.github.opscalehub.dong.ui.ScreenList
import com.github.opscalehub.dong.ui.SectionCard
import com.github.opscalehub.dong.ui.TextInput

/** Device and app context appended to every feedback message; no building data. */
private fun context(vm: AppViewModel): String = listOf(
    "${S.fbApp}: ${Fmt.digits(BuildConfig.VERSION_NAME)} (${Fmt.digits(BuildConfig.VERSION_CODE.toString())})",
    "${S.fbAndroid}: ${Fmt.digits(Build.VERSION.RELEASE)}، ${S.fbPhone}: ${Build.MANUFACTURER} ${Build.MODEL}",
    "${S.fbTextSize}: ${Fmt.digits("${(vm.ui.textScale.value * 100).toInt()}")}٪، ${S.fbUnits}: ${Fmt.number(vm.units.value.size.toLong())}",
).joinToString("\n")

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

/** Opens the app's page in Cafe Bazaar to rate and review it (Bazaar edition only). */
private fun rateInBazaar(ctx: Context): Boolean = try {
    ctx.startActivity(Intent(Intent.ACTION_EDIT, Uri.parse("bazaar://details?id=${BuildConfig.APPLICATION_ID}")).setPackage("com.farsitel.bazaar"))
    true
} catch (e: ActivityNotFoundException) {
    false
}

/** «نظر یا گزارش مشکل»: a few guided questions, sent by email to the developer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedbackScreen(vm: AppViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val kinds = listOf(S.fbProblem, S.fbConfused, S.fbIdea, S.fbGood)
    var kind by rememberSaveable { mutableStateOf(kinds[0]) }
    var doing by rememberSaveable { mutableStateOf("") }
    var happened by rememberSaveable { mutableStateOf("") }

    ScreenList {
        if (BuildConfig.STORE_BAZAAR) item {
            SectionCard(S.fbRateTitle) {
                Hint(S.fbRateHint)
                OutlinedButton(onClick = { if (!rateInBazaar(ctx)) vm.message(S.fbNoBazaar) }, Modifier.fillMaxWidth()) { Text(S.fbRate) }
            }
        }
        item { SectionCard { Hint(S.fbIntro.format(BuildConfig.SUPPORT_EMAIL)) } }
        item {
            SectionCard(S.fbKind) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    kinds.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k, style = MaterialTheme.typography.bodyLarge) }) }
                }
                TextInput(S.fbDoing, doing, { doing = it }, singleLine = false)
                TextInput(S.fbHappened, happened, { happened = it }, singleLine = false)
            }
        }
        item {
            Button(onClick = {
                val text = buildString {
                    appendLine("${S.fbKind}: $kind")
                    if (doing.isNotBlank()) appendLine("${S.fbDoing} ${doing.trim()}")
                    if (happened.isNotBlank()) appendLine("${S.fbHappened} ${happened.trim()}")
                    appendLine("—")
                    append(context(vm))
                }
                try {
                    email(ctx, text)
                    nav.popBackStack()
                } catch (e: Exception) {
                    vm.message(S.unexpectedError)
                }
            }, Modifier.fillMaxWidth(), enabled = doing.isNotBlank() || happened.isNotBlank()) { Text(S.fbSend) }
        }
    }
}
