package dev.yahaveliyahu.calendar

import android.Manifest
import android.os.Build
import android.widget.NumberPicker
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.yahaveliyahu.calendar_core.CalendarConfig
import dev.yahaveliyahu.calendar_core.CalendarEvent
import dev.yahaveliyahu.calendar_core.CalendarSystem
import dev.yahaveliyahu.calendar_core.CellShape
import dev.yahaveliyahu.calendar_core.ChristianHolidayProvider
import dev.yahaveliyahu.calendar_core.GregorianCalendarSystem
import dev.yahaveliyahu.calendar_core.HebrewCalendarSystem
import dev.yahaveliyahu.calendar_core.HolidayRegistry
import dev.yahaveliyahu.calendar_core.JewishHolidayProvider
import dev.yahaveliyahu.calendar_view.HebrewCalendarView
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID
import androidx.compose.runtime.mutableIntStateOf


private val SWATCHES = listOf(
    0xFF3F51B5.toInt(), 0xFF7986CB.toInt(), 0xFFE53935.toInt(), 0xFFFF4081.toInt(),
    0xFFFFC107.toInt(), 0xFF43A047.toInt(), 0xFF00897B.toInt(), 0xFF616161.toInt()
)

private val REMINDER_OPTIONS = listOf(
    0 to "בשעת האירוע",
    10 to "10 דקות לפני",
    30 to "חצי שעה לפני",
    60 to "שעה לפני",
    1440 to "יום לפני"
)

private enum class ReminderUnit(val label: String, val minutesMultiplier: Int, val maxValue: Int) {
    MINUTES("דקות", 1, 600),
    HOURS("שעות", 60, 120),
    DAYS("ימים", 60 * 24, 28),
    WEEKS("שבועות", 60 * 24 * 7, 4)
}

private const val MAX_CUSTOM_REMINDERS = 5

/** Formats a reminder value (in minutes-before) that isn't one of the fixed [REMINDER_OPTIONS]
 *  -- i.e. one added via the custom picker -- picking whichever unit divides it evenly, so "3
 *  days before" reads as "3 ימים לפני" rather than "4320 דקות לפני". */
private fun formatCustomReminderLabel(minutes: Int): String {
    val week = 60 * 24 * 7
    val day = 60 * 24
    return when {
        minutes % week == 0 && minutes >= week -> {
            val n = minutes / week
            if (n == 1) "שבוע לפני" else "$n שבועות לפני"
        }
        minutes % day == 0 && minutes >= day -> {
            val n = minutes / day
            if (n == 1) "יום לפני" else "$n ימים לפני"
        }
        minutes % 60 == 0 && minutes >= 60 -> {
            val n = minutes / 60
            if (n == 1) "שעה לפני" else "$n שעות לפני"
        }
        else -> "$minutes דקות לפני"
    }
}

private val HEBREW_SCRIPT_LOCALE = Locale("iw")

private enum class CalendarViewMode { MONTH, YEAR, WEEK, DAY }

/** The Sunday that starts the week containing [date] -- same Sunday-first convention used
 *  throughout the app (month grid, annual mini-months). */
private fun startOfWeekContaining(date: LocalDate): LocalDate =
    date.minusDays((date.dayOfWeek.value % 7).toLong())

/** The next "round" hour for suggesting an event start time when creating an event for
 *  *today* (e.g. 16:53 -> 17:00) -- you can't create an event at a time that's already
 *  passed, so round up instead of always defaulting to a fixed hour.
 */
private fun nextRoundHour(from: LocalTime): LocalTime =
    if (from.minute == 0) LocalTime.of(from.hour, 0) else LocalTime.of(from.hour, 0).plusHours(1)

@Composable
fun ThemeStudioScreen() {
    val context = LocalContext.current

    var theme by remember { mutableStateOf(AppStorage.loadTheme(context)) }
    var useHebrewAsPrimary by remember { mutableStateOf(AppStorage.loadUseHebrewPrimary(context)) }
    var jewishHolidaysOn by remember { mutableStateOf(AppStorage.loadJewishHolidaysOn(context)) }
    var christianHolidaysOn by remember { mutableStateOf(AppStorage.loadChristianHolidaysOn(context)) }
    var events by remember { mutableStateOf(AppStorage.loadEvents(context)) }

    var pendingViewDate by remember { mutableStateOf<LocalDate?>(null) }
    var pendingAddDate by remember { mutableStateOf<LocalDate?>(null) }
    var pendingAddTime by remember { mutableStateOf<LocalTime?>(null) }
    var showTimeJumpDialog by remember { mutableStateOf(false) }
    var calendarViewRef by remember { mutableStateOf<HebrewCalendarView?>(null) }
    var currentDisplayedAnchor by remember { mutableStateOf(LocalDate.now()) }
    var pendingMonthJump by remember { mutableStateOf<LocalDate?>(null) }
    var showEventSearchDialog by remember { mutableStateOf(false) }
    var viewingSearchResultEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var editingEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var showViewSwitchDialog by remember { mutableStateOf(false) }
    var calendarViewMode by remember { mutableStateOf(CalendarViewMode.MONTH) }
    var displayedAnnualYear by remember { mutableIntStateOf(LocalDate.now().year) }
    var displayedWeekStart by remember { mutableStateOf(startOfWeekContaining(LocalDate.now())) }
    var selectedWeekDate by remember { mutableStateOf(LocalDate.now()) }
    var displayedDay by remember { mutableStateOf(LocalDate.now()) }

    LaunchedEffect(theme) { AppStorage.saveTheme(context, theme) }
    LaunchedEffect(events) { AppStorage.saveEvents(context, events) }
    LaunchedEffect(useHebrewAsPrimary, jewishHolidaysOn, christianHolidaysOn) {
        AppStorage.saveToggles(context, useHebrewAsPrimary, jewishHolidaysOn, christianHolidaysOn)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        ReminderScheduler.ensureNotificationChannel(context)
        HolidayNotificationScheduler.ensureNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(jewishHolidaysOn, christianHolidaysOn) {
        HolidayNotificationScheduler.notifyIfHolidayToday(context, jewishHolidaysOn, christianHolidaysOn)
    }

    LaunchedEffect(Unit) {
        HolidayNotificationScheduler.enqueuePeriodicCheck(context)
    }

    val config = remember(useHebrewAsPrimary) {
        CalendarConfig(primaryCalendarSystem = if (useHebrewAsPrimary) HebrewCalendarSystem() else GregorianCalendarSystem())
    }

    val registry = remember(jewishHolidaysOn, christianHolidaysOn) {
        HolidayRegistry().apply {
            if (jewishHolidaysOn) enable(JewishHolidayProvider())
            if (christianHolidaysOn) enable(ChristianHolidayProvider())
        }
    }

    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Toolbar row above the calendar (and above its own month/year title) --
                // currently just the time-jump entry point; more buttons will join it here later.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .displayCutoutPadding()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    }
                }
                when (calendarViewMode) {
                    CalendarViewMode.YEAR -> {
                        AnnualCalendarView(
                            year = displayedAnnualYear,
                            theme = theme,
                            events = events,

                            onMonthClick = { month ->
                                pendingMonthJump = LocalDate.of(displayedAnnualYear, month, 1)
                                calendarViewMode = CalendarViewMode.MONTH
                            },

                            onSearchClick = {
                                showEventSearchDialog = true
                            },

                            onMenuClick = {
                                showViewSwitchDialog = true
                            },

                            onTodayClick = { displayedAnnualYear = LocalDate.now().year },

                            onYearChange = { newYear -> displayedAnnualYear = newYear },

                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    CalendarViewMode.WEEK -> {
                        WeeklyCalendarView(
                            weekStart = displayedWeekStart,
                            selectedDate = selectedWeekDate,
                            theme = theme,
                            events = events,

                            onSearchClick = {
                                showEventSearchDialog = true
                            },

                            onMenuClick = {
                                showViewSwitchDialog = true
                            },

                            onTodayClick = {
                                displayedWeekStart = startOfWeekContaining(LocalDate.now())
                                selectedWeekDate = LocalDate.now()
                            },

                            onDaySelected = { date -> selectedWeekDate = date },

                            onAddEventClick = { date, time ->
                                pendingAddDate = date
                                pendingAddTime = time
                            },

                            onEventClick = { event -> viewingSearchResultEvent = event },

                            onWeekChange = { newWeekStart -> displayedWeekStart = newWeekStart },

                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    CalendarViewMode.DAY -> {
                        DailyCalendarView(
                            date = displayedDay,
                            theme = theme,
                            events = events,

                            onSearchClick = {
                                showEventSearchDialog = true
                            },

                            onMenuClick = {
                                showViewSwitchDialog = true
                            },

                            onTodayClick = { displayedDay = LocalDate.now() },

                            onAddEventClick = { date, time ->
                                pendingAddDate = date
                                pendingAddTime = time
                            },

                            onEventClick = { event -> viewingSearchResultEvent = event },

                            onDayChange = { newDay -> displayedDay = newDay },

                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    CalendarViewMode.MONTH -> {
                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            factory = { ctx ->
                                HebrewCalendarView(ctx).apply { titleLocale = HEBREW_SCRIPT_LOCALE }.also { calendarViewRef = it }
                            },
                            update = { view ->
                                view.theme = theme
                                view.config = config
                                view.holidayRegistry = registry
                                view.setEvents(events)
                                view.refreshHolidays()
                                view.onDateSelectedListener = { date -> pendingViewDate = date }
                                view.onMonthChangedListener = { currentDisplayedAnchor = view.getDisplayedAnchor() }
                                pendingMonthJump?.let { target ->
                                    view.scrollToPeriod(target)
                                    pendingMonthJump = null
                                }
                            }
                        )
                    }
                }
                if (calendarViewMode == CalendarViewMode.MONTH) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val today = LocalDate.now()
                                        val (periodStart, periodEnd) = config.primaryCalendarSystem.monthBounds(currentDisplayedAnchor)
                                        if (today.isBefore(periodStart) || today.isAfter(periodEnd)) {
                                            calendarViewRef?.scrollToPeriod(today)
                                            resetLastJumpMemoryToToday(context)
                                        }
                                    },
                                    modifier = Modifier.size(40.dp),
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(LocalDate.now().dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge)
                                }
                                Button(onClick = { showTimeJumpDialog = true }) {
                                    Text("קפיצה בזמן")
                                }
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .clickable { showEventSearchDialog = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "חיפוש אירוע")
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .clickable { showViewSwitchDialog = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = "החלפת תצוגה")
                                }
                            }

                            SectionTitle("Primary calendar")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = !useHebrewAsPrimary, onClick = { useHebrewAsPrimary = false }, label = { Text("Gregorian (Jan, Feb...)") })
                                FilterChip(selected = useHebrewAsPrimary, onClick = { useHebrewAsPrimary = true }, label = { Text("עברי (תשרי, חשוון...)") })
                            }

                            SectionTitle("Colors")
                            ColorSwatchRow("Primary / header", theme.primaryColor) { theme = theme.copy(primaryColor = it) }
                            ColorSwatchRow("Today indicator", theme.todayIndicatorColor) { theme = theme.copy(todayIndicatorColor = it) }
                            ColorSwatchRow("Selected day", theme.selectedDayColor) { theme = theme.copy(selectedDayColor = it) }
                            ColorSwatchRow("Holiday / event chip", theme.holidayDotColor) { theme = theme.copy(holidayDotColor = it) }
                            ColorSwatchRow("Friday text", theme.fridayTextColor) { theme = theme.copy(fridayTextColor = it) }
                            ColorSwatchRow("Saturday text", theme.saturdayTextColor) { theme = theme.copy(saturdayTextColor = it) }

                            SectionTitle("Cell shape")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CellShape.values().forEach { shape ->
                                    FilterChip(selected = theme.cellShape == shape, onClick = { theme = theme.copy(cellShape = shape) }, label = { Text(shape.name) })
                                }
                            }

                            SectionTitle("Day text size: ${theme.dayTextSizeSp.toInt()}sp")
                            Slider(
                                value = theme.dayTextSizeSp,
                                valueRange = 10f..22f,
                                onValueChange = { theme = theme.copy(dayTextSizeSp = it) }
                            )

                            SectionTitle("Holidays shown")
                            LabeledCheckbox("Jewish holidays", jewishHolidaysOn) { jewishHolidaysOn = it }
                            LabeledCheckbox("Christian holidays", christianHolidaysOn) { christianHolidaysOn = it }

                            Spacer(Modifier.height(24.dp))

                        }
                    }
                }
            }

            val viewedDate = pendingViewDate
            if (viewedDate != null) {
                DayEventsDialog(
                    date = viewedDate,
                    dayEvents = events.filter { event ->
                        !event.isDeleted && run {
                            val eventStart = event.start.atZone(ZoneId.systemDefault()).toLocalDate()
                            val eventEnd = (event.end ?: event.start).atZone(ZoneId.systemDefault()).toLocalDate()
                            !viewedDate.isBefore(eventStart) && !viewedDate.isAfter(eventEnd)
                        }
                    },
                    primarySystem = config.primaryCalendarSystem,
                    onAddClick = {
                        if (viewedDate == LocalDate.now()) {
                            val now = LocalTime.now()
                            val rounded = nextRoundHour(now)
                            // 23:xx rounds up to 00:00 -- that's actually the start of tomorrow,
                            // not "00:00 today" (which would be before now / already passed).
                            pendingAddDate = if (rounded < now) viewedDate.plusDays(1) else viewedDate
                            pendingAddTime = rounded
                        } else {
                            // Future date: no "already passed" concern, keep the normal 9:00 default.
                            pendingAddDate = viewedDate
                            pendingAddTime = null
                        }
                        pendingViewDate = null
                    },
                    onEventClick = { event -> viewingSearchResultEvent = event },
                    onDeleteClick = { event ->
                        events = events.filter { it.id != event.id }
                        ReminderScheduler.cancelAll(context, event)
                    },
                    onDismiss = { pendingViewDate = null }
                )
            }

            val addingDate = pendingAddDate
            val eventBeingEdited = editingEvent
            if (addingDate != null || eventBeingEdited != null) {
                val formDate = addingDate ?: eventBeingEdited!!.start.atZone(ZoneId.systemDefault()).toLocalDate()
                val usedColorsToday = remember(formDate, events, eventBeingEdited) {
                    events.filter {
                        it.start.atZone(ZoneId.systemDefault()).toLocalDate() == formDate &&
                                !it.isDeleted &&
                                it.id != eventBeingEdited?.id
                    }.map { it.color }.toSet()
                }
                AddEventDialog(
                    date = formDate,
                    excludedColors = usedColorsToday,
                    primarySystem = config.primaryCalendarSystem,
                    editingEvent = eventBeingEdited,
                    defaultStartTime = pendingAddTime,
                    onDismiss = {
                        // Cancelling an edit returns to viewing that event's (unchanged) details,
                        // rather than dropping all the way back to the search results list.
                        if (eventBeingEdited != null) viewingSearchResultEvent = eventBeingEdited
                        pendingAddDate = null
                        pendingAddTime = null
                        editingEvent = null
                    },
                    onConfirm = { title, color, startDate, startTime, endDate, endTime, reminders, location, notes ->
                        if (eventBeingEdited != null) {
                            val updated = eventBeingEdited.copy(
                                title = title.ifBlank { "Untitled" },
                                start = startDate.atTime(startTime).atZone(ZoneId.systemDefault()).toInstant(),
                                end = endDate.atTime(endTime).atZone(ZoneId.systemDefault()).toInstant(),
                                color = color,
                                reminderMinutesBefore = reminders,
                                location = location,
                                notes = notes
                            )
                            events = events.map { if (it.id == updated.id) updated else it }
                            ReminderScheduler.cancelAll(context, eventBeingEdited)
                            ReminderScheduler.scheduleAll(context, updated)
                            if (reminders.isNotEmpty() && !ReminderScheduler.canScheduleExactAlarms(context)) {
                                ReminderScheduler.requestScheduleExactAlarmPermission(context)
                            }
                            viewingSearchResultEvent = updated
                        } else if (addingDate != null) {
                            val newEvent = CalendarEvent(
                                id = UUID.randomUUID().toString(),
                                title = title.ifBlank { "Untitled" },
                                start = startDate.atTime(startTime).atZone(ZoneId.systemDefault()).toInstant(),
                                end = endDate.atTime(endTime).atZone(ZoneId.systemDefault()).toInstant(),
                                color = color,
                                reminderMinutesBefore = reminders,
                                location = location,
                                notes = notes
                            )
                            events = events + newEvent
                            ReminderScheduler.scheduleAll(context, newEvent)
                            if (reminders.isNotEmpty() && !ReminderScheduler.canScheduleExactAlarms(context)) {
                                ReminderScheduler.requestScheduleExactAlarmPermission(context)
                            }
                        }
                        pendingAddDate = null
                        pendingAddTime = null
                        editingEvent = null
                    }
                )
            }

            if (showTimeJumpDialog) {
                TimeJumpDialog(
                    initialAnchor = LocalDate.now(),
                    onDismiss = { showTimeJumpDialog = false },
                    onJumpToDate = { date, switchToHebrewPrimary ->
                        if (switchToHebrewPrimary != null) useHebrewAsPrimary = switchToHebrewPrimary
                        calendarViewRef?.scrollToPeriod(date)
                    }
                )
            }

            if (showEventSearchDialog) {
                EventSearchDialog(
                    events = events,
                    primarySystem = config.primaryCalendarSystem,
                    onDismiss = { showEventSearchDialog = false },
                    onEventClick = { event -> viewingSearchResultEvent = event }
                )
            }

            if (showViewSwitchDialog) {
                ViewSwitchDialog(
                    onDismiss = { showViewSwitchDialog = false },
                    onSelectYear = { calendarViewMode = CalendarViewMode.YEAR; showViewSwitchDialog = false },
                    onSelectMonth = { calendarViewMode = CalendarViewMode.MONTH; showViewSwitchDialog = false },
                    onSelectWeek = { calendarViewMode = CalendarViewMode.WEEK; showViewSwitchDialog = false },
                    onSelectDay = { calendarViewMode = CalendarViewMode.DAY; showViewSwitchDialog = false }
                )
            }

            val detailEvent = viewingSearchResultEvent
            if (detailEvent != null) {
                EventDetailDialog(
                    event = detailEvent,
                    primarySystem = config.primaryCalendarSystem,
                    onDismiss = { viewingSearchResultEvent = null },
                    onEdit = { eventToEdit ->
                        editingEvent = eventToEdit
                        viewingSearchResultEvent = null
                    },
                    onDelete = { eventToDelete ->
                        events = events.filter { it.id != eventToDelete.id }
                        ReminderScheduler.cancelAll(context, eventToDelete)
                        viewingSearchResultEvent = null
                    }
                )
            }
        }
    }
}

@Composable
private fun DayEventsDialog(
    date: LocalDate,
    dayEvents: List<CalendarEvent>,
    primarySystem: CalendarSystem,
    onAddClick: () -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    onDeleteClick: (CalendarEvent) -> Unit,
    onDismiss: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<CalendarEvent?>(null) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(formattedDateLabel(date, primarySystem)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (dayEvents.isEmpty()) {
                        Text("אין אירועים עדיין")
                    } else {
                        dayEvents.sortedBy { it.start }.forEach { event ->
                            val eventStartZoned = event.start.atZone(ZoneId.systemDefault())
                            val eventEndZoned = (event.end ?: event.start).atZone(ZoneId.systemDefault())
                            val isMultiDay = eventStartZoned.toLocalDate() != eventEndZoned.toLocalDate()
                            val subtitle = if (isMultiDay) {
                                "${formattedDateLabel(eventStartZoned.toLocalDate(), primarySystem)}, ${formattedTimeLabel(eventStartZoned.toLocalTime())} - " +
                                        "${formattedDateLabel(eventEndZoned.toLocalDate(), primarySystem)}, ${formattedTimeLabel(eventEndZoned.toLocalTime())}"
                            } else {
                                formattedTimeLabel(eventStartZoned.toLocalTime())
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEventClick(event) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(event.color))) {}
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(event.title)
                                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "מחק",
                                    modifier = Modifier.clickable { pendingDelete = event }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = onAddClick) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("הוסף אירוע")
                }
            },
            dismissButton = { OutlinedButton(onClick = onDismiss) { Text("סגור") } }
        )

        val toDelete = pendingDelete
        if (toDelete != null) {
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("למחוק את האירוע?") },
                text = { Text("האם למחוק את \"${toDelete.title}\"? לא ניתן לבטל פעולה זו.") },
                confirmButton = {
                    Button(onClick = { onDeleteClick(toDelete); pendingDelete = null }) { Text("מחק") }
                },
                dismissButton = { OutlinedButton(onClick = { pendingDelete = null }) { Text("ביטול") } }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEventDialog(
    date: LocalDate,
    excludedColors: Set<Int>,
    primarySystem: CalendarSystem,
    editingEvent: CalendarEvent? = null,
    defaultStartTime: LocalTime? = null,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        color: Int,
        startDate: LocalDate,
        startTime: LocalTime,
        endDate: LocalDate,
        endTime: LocalTime,
        reminders: List<Int>,
        location: String,
        notes: String
    ) -> Unit
) {
    var title by remember(editingEvent) { mutableStateOf(editingEvent?.title ?: "") }
    var location by remember(editingEvent) { mutableStateOf(editingEvent?.location ?: "") }
    var notes by remember(editingEvent) { mutableStateOf(editingEvent?.notes ?: "") }
    val availableColors = remember(excludedColors) { SWATCHES.filterNot { it in excludedColors } }
    var color by remember(availableColors, editingEvent) {
        mutableIntStateOf(editingEvent?.color ?: availableColors.firstOrNull() ?: SWATCHES[0])
    }
    var selectedReminders by remember(editingEvent) {
        mutableStateOf(editingEvent?.reminderMinutesBefore?.toSet() ?: setOf())
    }
    var showCustomReminderDialog by remember { mutableStateOf(false) }

    var startDate by remember(editingEvent) {
        mutableStateOf(editingEvent?.start?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: date)
    }
    var startTime by remember(editingEvent) {
        mutableStateOf(editingEvent?.start?.atZone(ZoneId.systemDefault())?.toLocalTime() ?: defaultStartTime ?: LocalTime.of(9, 0))
    }
    var endDate by remember(editingEvent) {
        mutableStateOf(
            editingEvent?.end?.atZone(ZoneId.systemDefault())?.toLocalDate()
                ?: if (defaultStartTime != null && defaultStartTime.plusHours(1).isBefore(defaultStartTime)) {
                    // 23:xx + 1 hour wraps to the next day's 0:xx -- e.g. tapping the 23:00
                    // cell on 2.8 should default to an event ending 0:00 on 3.8, not "0:00
                    // today" which would look like it ends before it starts.
                    date.plusDays(1)
                } else {
                    date
                }
        )
    }
    var endTime by remember(editingEvent) {
        mutableStateOf(
            editingEvent?.end?.atZone(ZoneId.systemDefault())?.toLocalTime()
                ?: defaultStartTime?.plusHours(1)
                ?: LocalTime.of(10, 0)
        )
    }

    var activePicker by remember { mutableStateOf<EventDateTimeField?>(null) }

    val isRangeValid = remember(startDate, startTime, endDate, endTime) {
        endDate.atTime(endTime).isAfter(startDate.atTime(startTime))
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (editingEvent != null) "עריכת אירוע" else "הוסף אירוע") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("כותרת") })
                    if (availableColors.isEmpty()) {
                        Text(
                            "כל צבע כבר נמצא בשימוש על ידי אירוע אחר היום - מחק תחילה אחד, או השתמש שוב במשבצת האירוע הזו",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text("צבע האירוע")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            availableColors.forEach { swatch ->
                                Column(
                                    modifier = Modifier
                                        .size(if (swatch == color) 32.dp else 24.dp)
                                        .clip(CircleShape)
                                        .background(Color(swatch))
                                        .clickable { color = swatch }
                                ) {}
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        EventDateTimeColumn(
                            date = startDate,
                            time = startTime,
                            primarySystem = primarySystem,
                            onDateClick = { activePicker = EventDateTimeField.START_DATE },
                            onTimeClick = { activePicker = EventDateTimeField.START_TIME },
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                        EventDateTimeColumn(
                            date = endDate,
                            time = endTime,
                            primarySystem = primarySystem,
                            onDateClick = { activePicker = EventDateTimeField.END_DATE },
                            onTimeClick = { activePicker = EventDateTimeField.END_TIME },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (!isRangeValid) {
                        Text(
                            "זמן הסיום חייב להיות אחרי זמן ההתחלה",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("איפה האירוע?") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("תזכיר לי")
                    REMINDER_OPTIONS.forEach { (minutes, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(
                                checked = minutes in selectedReminders,
                                onCheckedChange = { checked ->
                                    selectedReminders = if (checked) selectedReminders + minutes else selectedReminders - minutes
                                }
                            )
                            Text(label)
                        }
                    }

                    val presetMinutes = remember { REMINDER_OPTIONS.map { it.first }.toSet() }
                    val customReminderMinutes = remember(selectedReminders, presetMinutes) {
                        selectedReminders.filterNot { it in presetMinutes }.sorted()
                    }
                    customReminderMinutes.forEach { minutes ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(
                                checked = true,
                                onCheckedChange = { checked -> if (!checked) selectedReminders = selectedReminders - minutes }
                            )
                            Text(formatCustomReminderLabel(minutes))
                        }
                    }

                    Row(
                        modifier = Modifier
                            .clickable { showCustomReminderDialog = true }
                            .padding(vertical = 6.dp)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("תזכורת מותאמת אישית", color = MaterialTheme.colorScheme.primary)
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("הערות") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = availableColors.isNotEmpty() && isRangeValid,
                    onClick = {
                        onConfirm(
                            title,
                            color,
                            startDate,
                            startTime,
                            endDate,
                            endTime,
                            selectedReminders.toList(),
                            location,
                            notes
                        )
                    }
                ) { Text(if (editingEvent != null) "שמור" else "הוסף") }
            },
            dismissButton = { OutlinedButton(onClick = onDismiss) { Text("ביטול") } }
        )

        when (activePicker) {
            EventDateTimeField.START_DATE -> DatePickerPopup(
                initialDate = startDate,
                onDismiss = { activePicker = null },
                onConfirm = { startDate = it }
            )
            EventDateTimeField.START_TIME -> TimePickerPopup(
                initialTime = startTime,
                onDismiss = { activePicker = null },
                onConfirm = { newStart ->
                    // Changing the start time always re-syncs the end time to exactly
                    // one hour later -- editable afterwards, but re-synced every time the
                    // start time itself is changed again.
                    startTime = newStart
                    val newEnd = newStart.plusHours(1)
                    endTime = newEnd
                    if (!endDate.atTime(newEnd).isAfter(startDate.atTime(newStart))) {
                        // Only happens when the new start is in the 23:00 hour, wrapping
                        // the +1h end past midnight -- bump the end date forward a day so
                        // the range stays valid instead of "ending" before it starts.
                        endDate = startDate.plusDays(1)
                    }
                }
            )
            EventDateTimeField.END_DATE -> DatePickerPopup(
                initialDate = endDate,
                onDismiss = { activePicker = null },
                onConfirm = { endDate = it }
            )
            EventDateTimeField.END_TIME -> TimePickerPopup(
                initialTime = endTime,
                onDismiss = { activePicker = null },
                onConfirm = { newEnd ->
                    // Editing the end time freely does NOT move the start time -- unless
                    // the new end would land at/before the current start, which would
                    // break the "start before end" rule; in that case pull the start back
                    // to exactly one hour before the new end.
                    endTime = newEnd
                    if (!endDate.atTime(newEnd).isAfter(startDate.atTime(startTime))) {
                        startTime = newEnd.minusHours(1)
                    }
                }
            )
            null -> Unit
        }

        if (showCustomReminderDialog) {
            CustomReminderDialog(
                onDismiss = { showCustomReminderDialog = false },
                onAdd = { minutesBefore ->
                    // Cap is on the total across fixed presets and custom ones combined -- e.g.
                    // if all 5 fixed options are already checked, no custom reminder can be
                    // added at all, since 5 is already the total, not "5 custom on top of them."
                    if (selectedReminders.size >= MAX_CUSTOM_REMINDERS) {
                        false
                    } else {
                        selectedReminders = selectedReminders + minutesBefore
                        showCustomReminderDialog = false
                        true
                    }
                }
            )
        }
    }
}

private enum class EventDateTimeField { START_DATE, START_TIME, END_DATE, END_TIME }

/** The "מותאם" reminder screen: two wheels side by side -- numbers on the right, unit
 *  (minutes/hours/days/weeks) on the left, matching Android's own NumberPicker wheel style
 *  (used directly via AndroidView rather than a bespoke Compose wheel, since that's both less
 *  work and a closer visual match than reimplementing wheel-snap physics). The number wheel's
 *  range depends on whichever unit is currently selected on the unit wheel. The "מותאם" button
 *  below adds the current selection and stays open so another custom reminder can be queued up
 *  right after, up to [MAX_CUSTOM_REMINDERS] total; past that it shows an inline limit message
 *  instead of a dialog that has to be reopened from the main form each time. */
@Composable
private fun CustomReminderDialog(onDismiss: () -> Unit, onAdd: (minutesBefore: Int) -> Boolean) {
    var selectedUnitIndex by remember { mutableIntStateOf(0) }
    var quantity by remember { mutableIntStateOf(1) }
    var limitReached by remember { mutableStateOf(false) }
    val selectedUnit = ReminderUnit.values()[selectedUnitIndex]
    val unitLabels = remember { ReminderUnit.values().map { it.label }.toTypedArray() }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("תזכורת מותאמת אישית") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Right wheel (composed first, so it lands on the right under RTL): the
                        // quantity, in whichever unit the left wheel is currently set to.
                        AndroidView(
                            modifier = Modifier.width(100.dp),
                            factory = { ctx ->
                                NumberPicker(ctx).apply {
                                    minValue = 1
                                    maxValue = selectedUnit.maxValue
                                    value = quantity
                                    setOnValueChangedListener { _, _, newVal -> quantity = newVal }
                                }
                            },
                            update = { picker ->
                                if (picker.maxValue != selectedUnit.maxValue) picker.maxValue = selectedUnit.maxValue
                                if (picker.value != quantity) picker.value = quantity
                            }
                        )
                        // Left wheel: the unit itself.
                        AndroidView(
                            modifier = Modifier.width(100.dp),
                            factory = { ctx ->
                                NumberPicker(ctx).apply {
                                    minValue = 0
                                    maxValue = unitLabels.size - 1
                                    displayedValues = unitLabels
                                    value = selectedUnitIndex
                                    setOnValueChangedListener { _, _, newVal ->
                                        selectedUnitIndex = newVal
                                        quantity = quantity.coerceIn(1, ReminderUnit.values()[newVal].maxValue)
                                    }
                                }
                            },
                            update = { picker ->
                                if (picker.value != selectedUnitIndex) picker.value = selectedUnitIndex
                            }
                        )
                    }

                    Text("$quantity ${selectedUnit.label} לפני האירוע", style = MaterialTheme.typography.bodyMedium)

                    TextButton(onClick = {
                        limitReached = !onAdd(quantity * selectedUnit.minutesMultiplier)
                    }) {
                        Spacer(Modifier.width(4.dp))
                        Text("הוסף")
                    }

                    if (limitReached) {
                        Text(
                            "לא ניתן להגדיר יותר מ-5 התראות",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = onDismiss) { Text("סגור") }
            }
        )
    }
}

fun eventFormWeekdayLabel(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.SUNDAY -> "יום א׳"
    DayOfWeek.MONDAY -> "יום ב׳"
    DayOfWeek.TUESDAY -> "יום ג׳"
    DayOfWeek.WEDNESDAY -> "יום ד׳"
    DayOfWeek.THURSDAY -> "יום ה׳"
    DayOfWeek.FRIDAY -> "יום ו׳"
    DayOfWeek.SATURDAY -> "שבת"
}

/** Formats [date] the way it's actually written in whichever calendar system is currently
 *  primary -- numeric day.month.2-digit-year for Gregorian (how it's commonly written in
 *  Israel, e.g. "11.8.26"), or Hebrew day-letters + "ב" + Hebrew month name + Hebrew
 *  year-letters for Hebrew (e.g. "כ״ח באב ה׳תשפ״ו") -- rather than always formatting as
 *  Gregorian regardless of which system the app is actually displaying. Reuses
 *  [CalendarSystem.labelFor] for the Hebrew case instead of re-deriving Hebrew letters here,
 *  since that's the exact same logic the calendar grid itself already draws with. */
fun formattedDateLabel(date: LocalDate, primarySystem: CalendarSystem): String {
    val weekday = eventFormWeekdayLabel(date.dayOfWeek)
    val datePart = if (primarySystem is HebrewCalendarSystem) {
        val label = primarySystem.labelFor(date)
        "${label.dayLabel} ב${label.monthName} ${label.yearLabel}"
    } else {
        "${date.dayOfMonth}.${date.monthValue}.${date.year % 100}"
    }
    return "$weekday, $datePart"
}

fun formattedTimeLabel(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

/** One side (start or end) of the date/time row: date on top, time below, both tappable --
 *  matches the reference layout exactly (two such columns with an arrow between them). */
@Composable
private fun EventDateTimeColumn(
    date: LocalDate,
    time: LocalTime,
    primarySystem: CalendarSystem,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = onDateClick) { Text(formattedDateLabel(date, primarySystem)) }
        TextButton(onClick = onTimeClick) { Text(formattedTimeLabel(time), style = MaterialTheme.typography.titleMedium) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerPopup(initialDate: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text("אישור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerPopup(initialTime: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialTime.hour, initialMinute = initialTime.minute, is24Hour = true)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TimePicker(state = state)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text("ביטול") }
                    TextButton(onClick = {
                        onConfirm(LocalTime.of(state.hour, state.minute))
                        onDismiss()
                    }) { Text("אישור") }
                }
            }
        }
    }
}

@Composable
private fun ViewSwitchDialog(
    onDismiss: () -> Unit,
    onSelectYear: () -> Unit,
    onSelectMonth: () -> Unit,
    onSelectWeek: () -> Unit,
    onSelectDay: () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    ViewSwitchMenuItem(label = "שנה", icon = Icons.Default.DateRange, onClick = onSelectYear)
                    ViewSwitchMenuItem(label = "חודש", icon = Icons.Default.CalendarMonth, onClick = onSelectMonth)
                    ViewSwitchMenuItem(label = "שבוע", painter = painterResource(R.drawable.ic_week), onClick = onSelectWeek)
                    ViewSwitchMenuItem(label = "יום",  painter = painterResource(R.drawable.ic_day), onClick = onSelectDay)
                }
            }
        }
    }
}

@Composable
private fun ViewSwitchMenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ViewSwitchMenuItem(
    label: String,
    painter: Painter,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painter,
            contentDescription = null
        )

        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}


@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun LabeledCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}

@Composable
private fun ColorSwatchRow(label: String, selectedColor: Int, onSelect: (Int) -> Unit) {
    Column {
        Text(label)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SWATCHES.forEach { colorInt ->
                val isSelected = colorInt == selectedColor
                Column(
                    modifier = Modifier
                        .size(if (isSelected) 36.dp else 28.dp)
                        .clip(CircleShape)
                        .background(Color(colorInt))
                        .clickable { onSelect(colorInt) }
                ) {}
            }
        }
    }
}