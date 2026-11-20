package com.github.opscalehub.dong.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.opscalehub.dong.i18n.Fmt

/** One row of the multiplier editor: what the person sees, and the floor for «بر اساس طبقه». */
data class MultiplierRow(val label: String, val floor: Int)

/**
 * The editor works in halves so a unit can pay «½ برابر» (common: everyone pays the same, one or
 * two units pay half). 0 = exempt, 1 = ½, 2 = ۱ برابر, 4 = ۲ برابر… Larger values (from «بر اساس
 * طبقه») still display.
 */
val MULTIPLIER_CHOICES = listOf(0L, 1L, 2L, 4L, 6L, 8L)

/** Halves of a stored weight ([scale] 1: whole multipliers, 2: halves). */
fun weightToHalves(weight: Long, scale: Long): Long = if (scale >= 2) weight * 2 / scale else weight * 2

/** Stored weights and scale for halves: whole multipliers (scale 1) unless some unit pays a half. */
fun halvesToWeights(halves: List<Long>): Pair<List<Long>, Long> =
    if (halves.all { it % 2 == 0L }) halves.map { it / 2 } to 1L else halves to 2L

/** A weighted split from per-unit halves, in [ids] order. */
fun weightedFromHalves(ids: List<String>, halves: List<Long>): com.github.opscalehub.dong.domain.allocation.Basis.Weighted {
    val (w, scale) = halvesToWeights(halves)
    return com.github.opscalehub.dong.domain.allocation.Basis.Weighted(ids.zip(w).toMap(), scale)
}

/** «۲ برابر»، «½ برابر»، «۱½ برابر» read naturally in Persian; a bare «×» flips around in right-to-left text. */
fun multiplierLabel(halves: Long): String {
    if (halves == 0L) return S.exempt
    val whole = halves / 2
    val n = when {
        halves % 2 == 0L -> Fmt.digits(whole.toString())
        whole == 0L -> "½"
        else -> Fmt.digits(whole.toString()) + "½"
    }
    return "$n ${S.times2}"
}

/**
 * Per-unit multipliers in plain terms: «½، ۱ برابر، ۲ برابر…» instead of raw weights. A unit at
 * «۲ برابر» pays twice what a «۱ برابر» unit pays. «بر اساس طبقه» sets each unit to its floor
 * number (ground floor 0 = exempt). [halves] and [onChange] count in halves (see [MULTIPLIER_CHOICES]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiplierEditor(rows: List<MultiplierRow>, halves: List<Long>, onChange: (List<Long>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Hint(S.multipliersHint)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onChange(rows.map { it.floor.coerceAtLeast(0).toLong() * 2 }) }) { Text(S.byFloor) }
            OutlinedButton(onClick = { onChange(rows.map { 2L }) }) { Text(S.allOne) }
        }
        rows.forEachIndexed { i, r ->
            val w = halves.getOrElse(i) { 2L }
            Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text("${r.label} — ${multiplierLabel(w)}", style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MULTIPLIER_CHOICES.forEach { c ->
                        FilterChip(selected = w == c, onClick = { onChange(halves.toMutableList().also { l -> while (l.size < rows.size) l += 2L; l[i] = c }) },
                            label = { Text(multiplierLabel(c)) })
                    }
                }
            }
        }
        if (halves.isNotEmpty() && halves.all { it == 0L }) Hint(S.multipliersAllZero, MaterialTheme.colorScheme.error)
    }
}
