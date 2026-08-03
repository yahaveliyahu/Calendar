package dev.yahaveliyahu.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.yahaveliyahu.calendar_core.CalendarEvent
import dev.yahaveliyahu.calendar_core.CalendarSystem
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * "חיפוש אירוע" -- opened from the magnifying-glass toolbar button. Filtering is a live
 * substring match against [CalendarEvent.title] (not prefix-only, matching what was asked
 * for: a letter anywhere in the name counts), re-run against the *whole* typed string on every
 * keystroke rather than incrementally narrowing an existing result set -- simpler, and just as
 * correct, since re-filtering the full list each time is cheap at any realistic event count.
 * Results are grouped by day (chronological, oldest first) with same-day events sharing one
 * layout. a non-empty query with no matches shows "לא נמצאו תוצאות" inline.
 */
@Composable
fun EventSearchDialog(
    events: List<CalendarEvent>,
    primarySystem: CalendarSystem,
    onDismiss: () -> Unit,
    onEventClick: (CalendarEvent) -> Unit
) {
    var query by remember { mutableStateOf("") }

    val activeEvents = remember(events) { events.filterNot { it.isDeleted } }
    val results = remember(activeEvents, query) {
        if (query.isBlank()) {
            emptyList()
        } else {
            activeEvents
                .filter { it.title.contains(query, ignoreCase = true) }
                .sortedBy { it.start }
        }
    }
    val groupedResults = remember(results) {
        results.groupBy { it.start.atZone(ZoneId.systemDefault()).toLocalDate() }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("חיפוש אירוע", style = MaterialTheme.typography.titleLarge)

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("שם האירוע") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    when {
                        query.isBlank() -> {
                            Text(
                                "הקלד שם אירוע כדי לחפש",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                        results.isEmpty() -> {
                            Text(
                                "לא נמצאו תוצאות",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 400.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                groupedResults.forEach { (date, dayEvents) ->
                                    item(key = date.toString()) {
                                        Column {
                                            Text(
                                                formattedDateLabel(date, primarySystem),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Surface(
                                                shape = MaterialTheme.shapes.medium,
                                                tonalElevation = 2.dp,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column {
                                                    dayEvents.forEachIndexed { index, event ->
                                                        EventResultRow(event = event, onClick = { onEventClick(event) })
                                                        if (index != dayEvents.lastIndex) HorizontalDivider()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text("סגור")
                    }
                }
            }
        }
    }
}

@Composable
private fun EventResultRow(event: CalendarEvent, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(event.color))
        )
        Text(event.title, modifier = Modifier.weight(1f), maxLines = 1)
        Text(
            formattedTimeLabel(event.start.atZone(ZoneId.systemDefault()).toLocalTime()),
            style = MaterialTheme.typography.titleSmall
        )
    }
}

/** The event detail screen, opened by tapping a search result. Shows the event's name, start
 *  date+time, end date+time, location, and notes (location/notes rows are omitted entirely
 *  when blank - e.g. events created before those fields existed - rather than showing an
 *  empty-looking row), plus three actions: עריכה (hands the event to [onEdit], which the
 *  caller is expected to open in the same form used to create events, pre-filled), שתף (builds
 *  and shares a real .ics file via [EventIcsSharer]), and מחק (asks for confirmation inline,
 *  then calls [onDelete] only once that's confirmed). */
@Composable
fun EventDetailDialog(
    event: CalendarEvent,
    primarySystem: CalendarSystem,
    onDismiss: () -> Unit,
    onEdit: (CalendarEvent) -> Unit,
    onDelete: (CalendarEvent) -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val startZoned = remember(event) { event.start.atZone(ZoneId.systemDefault()) }
    val endZoned = remember(event) { event.end?.atZone(ZoneId.systemDefault()) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(event.color))
                        )
                        Text(event.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    }

                    HorizontalDivider()

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        DateTimeColumn(
                            date = startZoned.toLocalDate(),
                            time = startZoned.toLocalTime(),
                            primarySystem = primarySystem,
                            modifier = Modifier.weight(1f)
                        )
                        /**
                         * AutoMirrored icons flip based on the current layout direction, but
                         * this arrow is meant to always point left (matching the reference
                         * design) regardless of the RTL context the rest of this dialog runs
                         * in - so layout direction is forced back to LTR just for this icon,
                         * which is what makes it resolve to its default left-pointing form.
                         */
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                        if (endZoned != null) {
                            DateTimeColumn(
                                date = endZoned.toLocalDate(),
                                time = endZoned.toLocalTime(),
                                primarySystem = primarySystem,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Text(
                                "--",
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (event.location.isNotBlank()) {
                        DetailField(icon = Icons.Default.LocationOn, label = "מיקום", value = event.location)
                    }
                    if (event.notes.isNotBlank()) {
                        DetailField(icon = Icons.AutoMirrored.Filled.Notes, label = "הערות", value = event.notes)
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        DetailActionButton(icon = Icons.Default.Edit, label = "עריכה", onClick = { onEdit(event) })
                        DetailActionButton(icon = Icons.Default.Share, label = "שתף", onClick = { EventIcsSharer.share(context, event) })
                        DetailActionButton(icon = Icons.Default.Delete, label = "מחק", onClick = { showDeleteConfirm = true })
                    }

                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text("סגור")
                    }
                }
            }

            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("למחוק את האירוע?") },
                    text = { Text("האם למחוק את \"${event.title}\"? לא ניתן לבטל פעולה זו.") },
                    confirmButton = {
                        Button(onClick = { showDeleteConfirm = false; onDelete(event) }) { Text("מחק") }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showDeleteConfirm = false }) { Text("ביטול") }
                    }
                )
            }
        }
    }
}

@Composable
private fun DetailActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DateTimeColumn(date: LocalDate, time: LocalTime, primarySystem: CalendarSystem, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(formattedDateLabel(date, primarySystem), style = MaterialTheme.typography.bodyMedium)
        Text(formattedTimeLabel(time), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun DetailField(icon: ImageVector, label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}