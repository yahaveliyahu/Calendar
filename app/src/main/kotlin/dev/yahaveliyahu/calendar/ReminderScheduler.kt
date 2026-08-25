package dev.yahaveliyahu.calendar

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.net.toUri
import dev.yahaveliyahu.calendar_core.CalendarEvent

/**
 * Schedules/cancels reminder notifications for a CalendarEvent's reminderMinutesBefore list.
 *
 * Uses setExactAndAllowWhileIdle() where possible, so a reminder fires at the actual requested
 * minute (including during Doze) rather than being allowed to slip by several minutes the way
 * plain set() permits. This needs the SCHEDULE_EXACT_ALARM permission on API 31+ (declared in
 * the manifest) -- unlike most runtime permissions, there's no in-app dialog for it; the user
 * grants it via a special Settings screen (ACTION_REQUEST_SCHEDULE_EXACT_ALARM), and can revoke
 * it there later too. [canScheduleExactAlarms] is exposed so a settings screen in this app can
 * check that and send the user there if it's off. If it's not granted (or the device is old
 * enough that the check doesn't even apply), this transparently falls back to inexact set() --
 * a late reminder is a far better failure mode than a crash or a silently-never-firing one.
 */
object ReminderScheduler {
    /** Whether this app can currently schedule exact alarms -- always true below API 31, since
     *  the SCHEDULE_EXACT_ALARM restriction doesn't exist on those versions at all. Exposed so
     *  a settings screen can check this and prompt the user to enable it if not. */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    /** Sends the user to the one place this permission can actually be granted: Android's own
     *  "Alarms & reminders" screen for this app (there's no in-app dialog for it, unlike most
     *  runtime permissions). No-op below API 31, since the permission doesn't exist there. */
    fun requestScheduleExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = "package:${context.packageName}".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** "_v2" is deliberate, not cosmetic: notification channel settings are permanent once
     *  created on a device -- the app can never change them afterward, only the user can, by
     *  hand, in system Settings. If this channel got created earlier (e.g. during testing)
     *  before sound was explicitly set below, every later code change here is silently ignored
     *  on that device. Bumping the ID forces Android to create a genuinely new channel with
     *  today's settings rather than reusing whatever got locked in the first time. */
    const val CHANNEL_ID = "calendar_event_reminders_v2"

    fun ensureNotificationChannel(context: Context) {
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Event reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reminders for calendar events you've added"
            setSound(soundUri, audioAttributes)
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun scheduleAll(context: Context, event: CalendarEvent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val useExact = canScheduleExactAlarms(context)

        event.reminderMinutesBefore.forEach { minutesBefore ->
            val triggerAtMillis = event.start.toEpochMilli() - minutesBefore * 60_000L
            if (triggerAtMillis <= System.currentTimeMillis()) return@forEach

            val pendingIntent = reminderPendingIntent(context, event, minutesBefore)
            try {
                if (useExact) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } catch (_: SecurityException) {
                // Some OEMs restrict alarms further still, even past the OS-level permission
                // check above; failing silently here is preferable to crashing the whole app.
            }
        }
    }

    fun cancelAll(context: Context, event: CalendarEvent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        event.reminderMinutesBefore.forEach { minutesBefore ->
            alarmManager.cancel(reminderPendingIntent(context, event, minutesBefore))
        }
    }

    private fun reminderPendingIntent(context: Context, event: CalendarEvent, minutesBefore: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_EVENT_ID, event.id)
            putExtra(ReminderReceiver.EXTRA_EVENT_TITLE, event.title)
            putExtra(ReminderReceiver.EXTRA_MINUTES_BEFORE, minutesBefore)
        }
        val requestCode = "${event.id}_$minutesBefore".hashCode()
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}