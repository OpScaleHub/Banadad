package com.github.opscalehub.dong.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Per-device display preferences (never in backups: they belong to the person holding the phone).
 * Only the text size: older managers need bigger text. (The simple/advanced switch and the
 * survey offer were removed in 0.6.0; their old keys are simply ignored.)
 */
class UiPrefs(context: Context) {
    private val p = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    /** Multiplies Android's own font scale; 1.0, 1.15 or 1.3. */
    private val _textScale = MutableStateFlow(p.getFloat(TEXT_SCALE, 1f))
    val textScale: StateFlow<Float> = _textScale.asStateFlow()
    fun setTextScale(v: Float) { p.edit().putFloat(TEXT_SCALE, v).apply(); _textScale.value = v }

    companion object {
        private const val TEXT_SCALE = "textScale"
        val SCALES = listOf(1f, 1.15f, 1.3f)
    }
}
