package dev.yahaveliyahu.calendar

import android.widget.NumberPicker
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import dev.yahaveliyahu.calendar_core.CalendarEvent
import dev.yahaveliyahu.calendar_core.CalendarSystem
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import androidx.compose.runtime.mutableIntStateOf

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventDialog(
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
    val selectedUnit = ReminderUnit.entries[selectedUnitIndex]
    val unitLabels = remember { ReminderUnit.entries.map { it.label }.toTypedArray() }

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
                                        quantity = quantity.coerceIn(1, ReminderUnit.entries[newVal].maxValue)
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