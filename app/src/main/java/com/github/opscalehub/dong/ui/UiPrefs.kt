package com.github.opscalehub.dong.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Per-device display preferences (never in backups: they belong to the person holding the phone).
 * The text size (older managers need bigger text) and when this phone last made a backup. (The simple/advanced switch and the
 * survey offer were removed in 0.6.0; their old keys are simply ignored.)
 */
class UiPrefs(context: Context) {
    private val p = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    /** Multiplies Android's own font scale; 1.0, 1.15 or 1.3. */
    private val _textScale = MutableStateFlow(p.getFloat(TEXT_SCALE, 1f))
    val textScale: StateFlow<Float> = _textScale.asStateFlow()
    fun setTextScale(v: Float) { p.edit().putFloat(TEXT_SCALE, v).apply(); _textScale.value = v }

    /** When this phone last wrote a backup file (epoch day; 0 = never). Data lives only here, so this is per device. */
    private val _lastBackup = MutableStateFlow(p.getLong(LAST_BACKUP, 0))
    val lastBackup: StateFlow<Long> = _lastBackup.asStateFlow()
    fun backedUp(today: Long) { p.edit().putLong(LAST_BACKUP, today).apply(); _lastBackup.value = today }

    /** «بعداً» on the home reminder hides it until this epoch day. */
    private val _backupSnooze = MutableStateFlow(p.getLong(BACKUP_SNOOZE, 0))
    val backupSnooze: StateFlow<Long> = _backupSnooze.asStateFlow()
    fun snoozeBackup(until: Long) { p.edit().putLong(BACKUP_SNOOZE, until).apply(); _backupSnooze.value = until }

    companion object {
        private const val LAST_BACKUP = "lastBackupDay"
        private const val BACKUP_SNOOZE = "backupSnoozeDay"
        /** Days after which the home screen asks for a new backup. */
        const val BACKUP_EVERY = 30L
        /** «بعداً» waits this many days. */
        const val BACKUP_SNOOZE_DAYS = 7L

        /** Whether to remind: there is real data, no recent backup, and the reminder isn't snoozed. */
        fun backupDue(today: Long, last: Long, snoozedUntil: Long, hasData: Boolean): Boolean =
            hasData && today >= snoozedUntil && (last == 0L || today - last >= BACKUP_EVERY)

        private const val TEXT_SCALE = "textScale"
        val SCALES = listOf(1f, 1.15f, 1.3f)
    }
}
