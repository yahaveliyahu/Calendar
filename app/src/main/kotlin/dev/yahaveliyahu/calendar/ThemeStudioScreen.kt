package dev.yahaveliyahu.calendar

import android.Manifest
import android.os.Build
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.yahaveliyahu.calendar_core.CalendarConfig
import dev.yahaveliyahu.calendar_core.CalendarEvent
import dev.yahaveliyahu.calendar_core.CalendarViewMode
import dev.yahaveliyahu.calendar_core.CellShape
import dev.yahaveliyahu.calendar_core.ChristianHolidayProvider
import dev.yahaveliyahu.calendar_core.GregorianCalendarSystem
import dev.yahaveliyahu.calendar_core.HebrewCalendarSystem
import dev.yahaveliyahu.calendar_core.HolidayRegistry
import dev.yahaveliyahu.calendar_core.JewishHolidayProvider
import dev.yahaveliyahu.calendar_view.HebrewCalendarView
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import androidx.compose.runtime.mutableIntStateOf

val SWATCHES = listOf(
    0xFF3F51B5.toInt(), 0xFF7986CB.toInt(), 0xFFE53935.toInt(), 0xFFFF4081.toInt(),
    0xFFFFC107.toInt(), 0xFF43A047.toInt(), 0xFF00897B.toInt(), 0xFF616161.toInt()
)

private val HEBREW_SCRIPT_LOCALE = Locale("iw")

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
                                CellShape.entries.forEach { shape ->
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
                        } else {
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