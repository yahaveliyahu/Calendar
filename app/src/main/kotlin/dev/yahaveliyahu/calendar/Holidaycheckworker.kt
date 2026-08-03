package dev.yahaveliyahu.calendar

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Runs roughly once a day via WorkManager (registered by
 * [HolidayNotificationScheduler.enqueuePeriodicCheck]), whether or not the app is open or has
 * even been launched since the last reboot. Reads the user's holiday toggles straight from
 * [AppStorage] rather than from Compose state, since there's no running UI to read that from
 * out here - this can fire while the app process isn't alive at all.
 */
class HolidayCheckWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val jewishOn = AppStorage.loadJewishHolidaysOn(applicationContext)
        val christianOn = AppStorage.loadChristianHolidaysOn(applicationContext)
        HolidayNotificationScheduler.ensureNotificationChannel(applicationContext)
        HolidayNotificationScheduler.notifyIfHolidayToday(applicationContext, jewishOn, christianOn)
        return Result.success()
    }
}