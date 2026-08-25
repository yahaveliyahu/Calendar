package dev.yahaveliyahu.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import dev.yahaveliyahu.calendar_core.CalendarEvent
import dev.yahaveliyahu.calendar_core.CalendarSystem
import java.time.LocalDate
import java.time.ZoneId


@Composable
fun DayEventsDialog(
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