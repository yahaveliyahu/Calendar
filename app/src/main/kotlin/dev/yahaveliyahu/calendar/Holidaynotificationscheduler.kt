package dev.yahaveliyahu.calendar

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.yahaveliyahu.calendar_core.ChristianHolidayProvider
import dev.yahaveliyahu.calendar_core.HolidayInfo
import dev.yahaveliyahu.calendar_core.HolidayRegistry
import dev.yahaveliyahu.calendar_core.JewishHolidayProvider
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Silent "<holiday> שמח!" notifications -- a separate channel from [ReminderScheduler]'s event
 * reminders, since a user may want to mute one but not the other.
 *
 * This has to run without the app being opened that day
 * [enqueuePeriodicCheck] instead registers a recurring [HolidayCheckWorker] via
 * WorkManager, which the OS itself keeps re-scheduling -- including across reboots -- with no
 * further app involvement needed. [notifyIfHolidayToday] is still called directly whenever the
 * app *is* opened too, purely so a holiday shows up immediately rather than waiting for the
 * next periodic run.
 */
object HolidayNotificationScheduler {
    const val CHANNEL_ID = "holiday_greetings_v2"
    private const val PERIODIC_WORK_NAME = "holiday_check_daily"

    fun ensureNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Holiday greetings",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "A silent greeting on the day of a Jewish or Christian holiday - no sound, just shows in the shade and lock screen"
            setSound(null, null)
            enableVibration(false)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** Registers the recurring background check, once. Safe to call on every app launch:
     *  [ExistingPeriodicWorkPolicy.KEEP] means if it's already registered from a previous
     *  launch, this is a no-op rather than creating a duplicate schedule. WorkManager itself
     *  (not this app) is responsible for re-arming it after a reboot. */
    fun enqueuePeriodicCheck(context: Context) {
        val request = PeriodicWorkRequestBuilder<HolidayCheckWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Checks whether any enabled holiday falls on today, and if so posts its greeting right
     *  away -- but only once per holiday per day, tracked in [AppStorage] so re-checking later
     *  the same day (whether from re-opening the app or from [HolidayCheckWorker] also running
     *  that day) doesn't repeat it. */
    fun notifyIfHolidayToday(context: Context, jewishOn: Boolean, christianOn: Boolean) {
        val today = LocalDate.now()
        val todayKey = today.toString()
        val alreadyNotified = if (AppStorage.loadLastHolidayNotificationDate(context) == todayKey) {
            AppStorage.loadNotifiedHolidayNamesToday(context)
        } else {
            emptySet()
        }

        val todaysHolidays = holidaysOn(today, today, jewishOn, christianOn)
        if (todaysHolidays.isEmpty()) return

        val stillToNotify = todaysHolidays.filterNot { it.name in alreadyNotified }
        if (stillToNotify.isEmpty()) return

        stillToNotify.forEach { postGreeting(context, "${it.providerId}:${it.name}", it.hebrewName) }
        AppStorage.saveHolidayNotificationState(context, todayKey, alreadyNotified + stillToNotify.map { it.name })
    }

    private fun holidaysOn(start: LocalDate, end: LocalDate, jewishOn: Boolean, christianOn: Boolean): List<HolidayInfo> {
        if (!jewishOn && !christianOn) return emptyList()
        val registry = HolidayRegistry().apply {
            if (jewishOn) enable(JewishHolidayProvider())
            if (christianOn) enable(ChristianHolidayProvider())
        }
        return registry.holidaysFor(start, end)
    }

    /** Posts the actual "<hebrewName> שמח!" notification.
     *  appears in the notification shade and lock screen, same as the channel is configured. */
    fun postGreeting(context: Context, holidayKey: String, hebrewName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("$hebrewName שמח!")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(holidayKey.hashCode(), notification)
    }
}