package dev.yahaveliyahu.calendar

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.yahaveliyahu.calendar_core.CalendarConfig
import dev.yahaveliyahu.calendar_core.CalendarEvent
import dev.yahaveliyahu.calendar_core.CalendarTheme
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

private val SWATCHES = listOf(
    0xFF3F51B5.toInt(), 0xFF7986CB.toInt(), 0xFFE53935.toInt(), 0xFFFF4081.toInt(),
    0xFFFFC107.toInt(), 0xFF43A047.toInt(), 0xFF00897B.toInt(), 0xFF616161.toInt()
)

private val REMINDER_OPTIONS = listOf(
    0 to "At start time",
    10 to "10 minutes before",
    30 to "30 minutes before",
    60 to "1 hour before",
    1440 to "1 day before"
)

private val HEBREW_SCRIPT_LOCALE = Locale("iw")

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

    LaunchedEffect(theme) { AppStorage.saveTheme(context, theme) }
    LaunchedEffect(events) { AppStorage.saveEvents(context, events) }
    LaunchedEffect(useHebrewAsPrimary, jewishHolidaysOn, christianHolidaysOn) {
        AppStorage.saveToggles(context, useHebrewAsPrimary, jewishHolidaysOn, christianHolidaysOn)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        ReminderScheduler.ensureNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .displayCutoutPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    factory = { ctx ->
                        HebrewCalendarView(ctx).apply { titleLocale = HEBREW_SCRIPT_LOCALE }
                    },
                    update = { view ->
                        view.theme = theme
                        view.config = config
                        view.holidayRegistry = registry
                        view.setEvents(events)
                        view.refreshHolidays()
                        view.onDateSelectedListener = { date -> pendingViewDate = date }
                    }
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {

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

            val viewedDate = pendingViewDate
            if (viewedDate != null) {
                DayEventsDialog(
                    date = viewedDate,
                    dayEvents = events.filter { it.start.atZone(ZoneId.systemDefault()).toLocalDate() == viewedDate && !it.isDeleted },
                    onAddClick = { pendingAddDate = viewedDate; pendingViewDate = null },
                    onDeleteClick = { event ->
                        events = events.filter { it.id != event.id }
                        ReminderScheduler.cancelAll(context, event)
                    },
                    onDismiss = { pendingViewDate = null }
                )
            }

            val addingDate = pendingAddDate
            if (addingDate != null) {
                val usedColorsToday = remember(addingDate, events) {
                    events.filter { it.start.atZone(ZoneId.systemDefault()).toLocalDate() == addingDate && !it.isDeleted }
                        .map { it.color }.toSet()
                }
                AddEventDialog(
                    date = addingDate,
                    excludedColors = usedColorsToday,
                    onDismiss = { pendingAddDate = null },
                    onConfirm = { title, color, startTime, endTime, reminders ->
                        val newEvent = CalendarEvent(
                            id = UUID.randomUUID().toString(),
                            title = title.ifBlank { "Untitled" },
                            start = addingDate.atTime(startTime).atZone(ZoneId.systemDefault()).toInstant(),
                            end = addingDate.atTime(endTime).atZone(ZoneId.systemDefault()).toInstant(),
                            color = color,
                            reminderMinutesBefore = reminders
                        )
                        events = events + newEvent
                        ReminderScheduler.scheduleAll(context, newEvent)
                        pendingAddDate = null
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
    onAddClick: () -> Unit,
    onDeleteClick: (CalendarEvent) -> Unit,
    onDismiss: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<CalendarEvent?>(null) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(date.toString()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (dayEvents.isEmpty()) {
                        Text("No events yet.")
                    } else {
                        dayEvents.sortedBy { it.start }.forEach { event ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(event.color))) {}
                                val time = event.start.atZone(ZoneId.systemDefault()).toLocalTime()
                                Text("$time  ${event.title}", modifier = Modifier.weight(1f))
                                Text(
                                    "\uD83D\uDDD1",
                                    modifier = Modifier.clickable { pendingDelete = event }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = onAddClick) { Text("+ Add event") } },
            dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Close") } }
        )

        val toDelete = pendingDelete
        if (toDelete != null) {
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Delete event?") },
                text = { Text("Are you sure you want to delete \"${toDelete.title}\"? This can't be undone.") },
                confirmButton = {
                    Button(onClick = { onDeleteClick(toDelete); pendingDelete = null }) { Text("Delete") }
                },
                dismissButton = { OutlinedButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEventDialog(
    date: LocalDate,
    excludedColors: Set<Int>,
    onDismiss: () -> Unit,
    onConfirm: (title: String, color: Int, startTime: LocalTime, endTime: LocalTime, reminders: List<Int>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    val availableColors = remember(excludedColors) { SWATCHES.filterNot { it in excludedColors } }
    var color by remember(availableColors) { mutableStateOf(availableColors.firstOrNull() ?: SWATCHES[0]) }
    var selectedReminders by remember { mutableStateOf(setOf<Int>()) }

    val startTimeState = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = true)
    val endTimeState = rememberTimePickerState(initialHour = 10, initialMinute = 0, is24Hour = true)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add event") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(date.toString())
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title (emoji welcome)") })

                    if (availableColors.isEmpty()) {
                        Text(
                            "Every color is already used by another event today -- delete one first, or reuse this event's slot.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text("Color")
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

                    Text("Start time")
                    TimeInput(state = startTimeState)
                    Text("End time")
                    TimeInput(state = endTimeState)

                    Text("Remind me")
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
                }
            },
            confirmButton = {
                Button(
                    enabled = availableColors.isNotEmpty(),
                    onClick = {
                        onConfirm(
                            title,
                            color,
                            LocalTime.of(startTimeState.hour, startTimeState.minute),
                            LocalTime.of(endTimeState.hour, endTimeState.minute),
                            selectedReminders.toList()
                        )
                    }
                ) { Text("Add") }
            },
            dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
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