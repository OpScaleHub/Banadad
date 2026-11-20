package com.github.opscalehub.dong.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.github.opscalehub.dong.data.db.ParkingSpaceEntity
import com.github.opscalehub.dong.data.db.UnitEntity
import com.github.opscalehub.dong.domain.allocation.AllocationComponent
import com.github.opscalehub.dong.domain.allocation.AllocationPolicy
import com.github.opscalehub.dong.domain.allocation.Basis
import com.github.opscalehub.dong.domain.allocation.BasisKind
import com.github.opscalehub.dong.domain.allocation.CommonWater
import com.github.opscalehub.dong.domain.allocation.Eligibility
import com.github.opscalehub.dong.domain.allocation.Portion
import com.github.opscalehub.dong.domain.allocation.SimpleBasis
import com.github.opscalehub.dong.domain.allocation.SpaceFilter
import com.github.opscalehub.dong.domain.model.ParkingRelation
import com.github.opscalehub.dong.i18n.Fa
import com.github.opscalehub.dong.i18n.Fmt

enum class PortionType { REMAINDER, PERCENT, FIXED }
enum class CommonChoice { PROPORTIONAL, SEPARATE_EQUAL, SEPARATE_OCCUPANTS, SEPARATE_AREA, FUND_BORNE }

private val editableBases = listOf(BasisKind.EQUAL, BasisKind.OCCUPANTS, BasisKind.AREA, BasisKind.CONSUMPTION, BasisKind.WEIGHTED, BasisKind.EXPLICIT, BasisKind.PARKING)

class ComponentState(c: AllocationComponent) {
    var portion by mutableStateOf(when (c.portion) { Portion.Remainder -> PortionType.REMAINDER; is Portion.Percent -> PortionType.PERCENT; is Portion.Fixed -> PortionType.FIXED })
    var portionText by mutableStateOf(when (val p = c.portion) {
        is Portion.Percent -> Fmt.digits((p.basisPoints / 100.0).toString().removeSuffix(".0"))
        is Portion.Fixed -> Fmt.money(p.amountMinor)
        Portion.Remainder -> ""
    })
    var basis by mutableStateOf(when (c.basis) {
        Basis.Equal -> BasisKind.EQUAL; Basis.Occupants -> BasisKind.OCCUPANTS; Basis.Area -> BasisKind.AREA
        is Basis.Consumption -> BasisKind.CONSUMPTION; is Basis.Weighted -> BasisKind.WEIGHTED
        is Basis.Explicit -> BasisKind.EXPLICIT; is Basis.Parking -> BasisKind.PARKING
    })
    var common by mutableStateOf((c.basis as? Basis.Consumption)?.common?.let {
        when (it) {
            CommonWater.Proportional -> CommonChoice.PROPORTIONAL
            CommonWater.FundBorne -> CommonChoice.FUND_BORNE
            is CommonWater.Separate -> when (it.basis) {
                SimpleBasis.EQUAL -> CommonChoice.SEPARATE_EQUAL; SimpleBasis.OCCUPANTS -> CommonChoice.SEPARATE_OCCUPANTS; SimpleBasis.AREA -> CommonChoice.SEPARATE_AREA
            }
        }
    } ?: CommonChoice.SEPARATE_EQUAL)
    private val parking = c.basis as? Basis.Parking
    var parkingFilter by mutableStateOf(parking?.filter ?: SpaceFilter.IN_USE)
    val relations = mutableStateListOf<ParkingRelation>().apply { addAll(parking?.relations ?: ParkingRelation.entries) }
    val spaceIds = mutableStateListOf<String>().apply { addAll(parking?.spaceIds ?: emptySet()) }
    var allUnits by mutableStateOf(c.eligibility == Eligibility.AllActive)
    val selected = mutableStateListOf<String>().apply { addAll((c.eligibility as? Eligibility.Selected)?.unitIds ?: emptySet()) }
    /** Multiplier scale of a weighted split (2 when some unit pays «½ برابر»); kept as loaded. */
    private val weightScale = (c.basis as? Basis.Weighted)?.scale ?: 1L
    /** unitId → text for weights or explicit amounts. */
    val perUnit = mutableStateMapOf<String, String>().apply {
        when (val b = c.basis) {
            is Basis.Weighted -> b.weights.forEach { (k, v) -> put(k, Fmt.digits(v.toString())) }
            is Basis.Explicit -> b.amounts.forEach { (k, v) -> put(k, Fmt.money(v)) }
            else -> Unit
        }
    }

    fun toComponent(): AllocationComponent? {
        val portion: Portion = when (portion) {
            PortionType.REMAINDER -> Portion.Remainder
            PortionType.PERCENT -> {
                val pct = Fmt.latin(portionText).trim().toDoubleOrNull() ?: return null
                val bp = Math.round(pct * 100).toInt()
                if (bp !in 1..10_000) return null
                Portion.Percent(bp)
            }
            PortionType.FIXED -> Portion.Fixed(Fmt.parseMoney(portionText)?.takeIf { it >= 0 } ?: return null)
        }
        val basis: Basis = when (basis) {
            BasisKind.EQUAL -> Basis.Equal
            BasisKind.OCCUPANTS -> Basis.Occupants
            BasisKind.AREA -> Basis.Area
            BasisKind.CONSUMPTION -> Basis.Consumption(when (common) {
                CommonChoice.PROPORTIONAL -> CommonWater.Proportional
                CommonChoice.SEPARATE_EQUAL -> CommonWater.Separate(SimpleBasis.EQUAL)
                CommonChoice.SEPARATE_OCCUPANTS -> CommonWater.Separate(SimpleBasis.OCCUPANTS)
                CommonChoice.SEPARATE_AREA -> CommonWater.Separate(SimpleBasis.AREA)
                CommonChoice.FUND_BORNE -> CommonWater.FundBorne
            })
            BasisKind.WEIGHTED -> Basis.Weighted(perUnit.mapNotNull { (k, v) -> if (v.isBlank()) null else k to (Fmt.parseLong(v)?.takeIf { it >= 0 } ?: return null) }.toMap(), weightScale)
            BasisKind.EXPLICIT -> Basis.Explicit(perUnit.mapNotNull { (k, v) -> if (v.isBlank()) null else k to (Fmt.parseMoney(v)?.takeIf { it >= 0 } ?: return null) }.toMap())
            BasisKind.PARKING -> Basis.Parking(parkingFilter, relations.toSet(), spaceIds.toSet())
            BasisKind.WATER_TARIFF -> return null
        }
        return AllocationComponent(portion, basis, if (allUnits) Eligibility.AllActive else Eligibility.Selected(selected.toSet()))
    }
}

class PolicyEditorState(initial: AllocationPolicy) {
    var fundOnly by mutableStateOf(initial.fundOnly)
    val components = mutableStateListOf<ComponentState>().apply {
        addAll(initial.components.ifEmpty { listOf(AllocationComponent()) }.map { ComponentState(it) })
    }

    fun reset(p: AllocationPolicy) {
        fundOnly = p.fundOnly
        components.clear()
        components.addAll(p.components.ifEmpty { listOf(AllocationComponent()) }.map { ComponentState(it) })
    }

    /** null when any field is invalid. */
    fun toPolicy(): AllocationPolicy? {
        if (fundOnly) return AllocationPolicy.FUND_ONLY
        val comps = components.map { it.toComponent() ?: return null }
        return AllocationPolicy(false, comps)
    }
}

@Composable
fun rememberPolicyEditor(initial: AllocationPolicy) = remember { PolicyEditorState(initial) }

/** One-line human summary of a policy. */
fun policySummary(p: AllocationPolicy): String =
    if (p.fundOnly) S.fundOnly else p.components.joinToString(" + ") { c ->
        val portion = when (val x = c.portion) {
            Portion.Remainder -> ""
            is Portion.Percent -> Fmt.percentBp(x.basisPoints) + " "
            is Portion.Fixed -> Fmt.money(x.amountMinor) + " "
        }
        portion + Fa.basis(c.basis) + if (c.eligibility is Eligibility.Selected) " (${S.selectedUnits})" else ""
    }
