package com.kairosera.core.notifications

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import com.kairosera.R
import com.kairosera.domain.model.ReminderSound

/**
 * Android owns a channel's sound and importance once it exists: the user can change them in
 * system settings and the app must not fight that. So each built-in sound gets its own channel,
 * created once, and a reminder picks a channel instead of overriding sound per notification.
 * Re-running [ensure] only refreshes the (translated) names; it never resets the user's choices.
 */
object NotificationChannels {
    private const val GROUP_REMINDERS = "reminders"
    const val GENERAL = "general"

    private data class Spec(val id: String, val nameRes: Int, val rawName: String?, val silent: Boolean = false)

    private val specs = mapOf(
        ReminderSound.DEFAULT to Spec("reminder_default", R.string.channel_default, null),
        ReminderSound.KAIROS_BELL to Spec("reminder_bell", R.string.sound_kairos_bell, "kairos_bell"),
        ReminderSound.SOFT_CHIME to Spec("reminder_chime", R.string.sound_soft_chime, "soft_chime"),
        ReminderSound.FOCUS to Spec("reminder_focus", R.string.sound_focus, "focus_tone"),
        ReminderSound.SILENT to Spec("reminder_silent", R.string.sound_silent, null, silent = true),
    )

    /** Custom sounds are not wired up yet; they fall back to the default channel, never to a broken one. */
    fun channelFor(sound: ReminderSound): String = (specs[sound] ?: specs.getValue(ReminderSound.DEFAULT)).id

    fun soundUri(context: Context, sound: ReminderSound): Uri? =
        specs[sound]?.rawName?.let { rawUri(context, it) }

    /** By-name resource URI: stable across app upgrades, unlike a numeric resource id. */
    private fun rawUri(context: Context, name: String): Uri =
        Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/raw/$name")

    fun ensure(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannelGroup(NotificationChannelGroup(GROUP_REMINDERS, context.getString(R.string.channel_group_reminders)))
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        for (spec in specs.values) {
            val importance = if (spec.silent) NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(spec.id, context.getString(spec.nameRes), importance).apply {
                group = GROUP_REMINDERS
                description = context.getString(R.string.channel_reminders_description)
                when {
                    spec.silent -> setSound(null, null)
                    spec.rawName != null -> setSound(rawUri(context, spec.rawName), attrs)
                }
                enableVibration(!spec.silent)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
            nm.createNotificationChannel(channel)
        }
        nm.createNotificationChannel(
            NotificationChannel(GENERAL, context.getString(R.string.channel_general), NotificationManager.IMPORTANCE_LOW),
        )
    }
}
