package dev.yahaveliyahu.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.yahaveliyahu.calendar_core.CalendarEvent
import dev.yahaveliyahu.calendar_core.CalendarTheme
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private const val DAY_SWIPE_THRESHOLD_PX = 100f
private const val DAY_INITIAL_SCROLL_HOUR = 6

private fun dayFullWeekdayLabel(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.SUNDAY -> "יום ראשון"
    DayOfWeek.MONDAY -> "יום שני"
    DayOfWeek.TUESDAY -> "יום שלישי"
    DayOfWeek.WEDNESDAY -> "יום רביעי"
    DayOfWeek.THURSDAY -> "יום חמישי"
    DayOfWeek.FRIDAY -> "יום שישי"
    DayOfWeek.SATURDAY -> "שבת"
}

/**
 * Single-day hour-grid view -- essentially [WeeklyCalendarView] narrowed to one day column, and
 * deliberately reuses that file's lane-packing, dotted-grid-line, and time-offset math (made
 * non-private there for exactly this) rather than re-implementing the same logic here.
 */
@Composable
fun DailyCalendarView(
    date: LocalDate,
    theme: CalendarTheme,
    events: List<CalendarEvent>,
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit,
    onTodayClick: () -> Unit,
    onAddEventClick: (date: LocalDate, time: LocalTime) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    onDayChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    // Ticks right on each minute boundary of the device clock (see millisUntilNextMinute in
    // WeeklyCalendarView.kt) so the current-time line stays in sync with it instead of
    // drifting on a fixed interval.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(millisUntilNextMinute(now))
        }
    }
    val today = now.toLocalDate()
    val nowTime = now.toLocalTime()
    val isToday = date == today

    val zone = remember { ZoneId.systemDefault() }
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    LaunchedEffect(Unit) {
        scrollState.scrollTo(with(density) { (WEEK_HOUR_HEIGHT * DAY_INITIAL_SCROLL_HOUR).toPx().toInt() })
    }

    /**
     * Same split used by the week/month views: events spanning more than one day go in the
     * all-day row; a same-day event gets positioned by time in the hourly grid below.
     */
    val (multiDayEventsToday, timedEventsToday) = remember(events, date) {
        val multiDay = mutableListOf<CalendarEvent>()
        val timed = mutableListOf<CalendarEvent>()
        events.filterNot { it.isDeleted }.forEach { event ->
            val startDate = event.start.atZone(zone).toLocalDate()
            val endZoned = (event.end ?: event.start).atZone(zone)
            val rawEndDate = endZoned.toLocalDate()
            val effectiveEndDate = if (rawEndDate.isAfter(startDate) && endZoned.toLocalTime() == LocalTime.MIDNIGHT) {
                rawEndDate.minusDays(1)
            } else {
                rawEndDate
            }
            val touchesToday = !date.isBefore(startDate) && !date.isAfter(effectiveEndDate)
            if (touchesToday) {
                if (effectiveEndDate.isAfter(startDate)) multiDay += event else timed += event
            }
        }
        multiDay to timed
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .pointerInput(date) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onDragEnd = {
                        when {
                            totalDrag <= -DAY_SWIPE_THRESHOLD_PX -> onDayChange(date.plusDays(1))
                            totalDrag >= DAY_SWIPE_THRESHOLD_PX -> onDayChange(date.minusDays(1))
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount
                    }
                )
            }
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onTodayClick,
                    modifier = Modifier.size(25.dp),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(today.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                }
            }
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Text(
                WEEK_MONTH_ABBREVIATIONS[date.monthValue - 1],
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                textAlign = TextAlign.Center
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(MaterialTheme.shapes.small)
                        .then(if (isToday) Modifier.background(Color(theme.todayIndicatorColor)) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isToday) Color.White else Color(theme.defaultTextColor)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(dayFullWeekdayLabel(date.dayOfWeek), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.SentimentSatisfied, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (multiDayEventsToday.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    multiDayEventsToday.forEach { event ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(Color(event.color))
                                .clickable { onEventClick(event) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(event.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, color = Color.White)
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .dottedTopBorder(MaterialTheme.colorScheme.outlineVariant)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
                var columnWidthPx by remember { mutableIntStateOf(0) }
                val columnWidth = with(density) { columnWidthPx.toDp() }
                val outlineColor = MaterialTheme.colorScheme.outlineVariant

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.width(WEEK_HOUR_LABEL_WIDTH)) {
                        for (hour in 0..23) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(WEEK_HOUR_HEIGHT)
                            ) {
                                Text(
                                    hour.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 2.dp)
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .onSizeChanged { size -> columnWidthPx = size.width }
                    ) {
                        Column {
                            for (hour in 0..23) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(WEEK_HOUR_HEIGHT)
                                        .dottedTopBorder(outlineColor)
                                        .clickable { onAddEventClick(date, LocalTime.of(hour, 0)) }
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .dottedTopBorder(outlineColor)
                            )
                        }

                        val dayLayouts = remember(timedEventsToday, date, zone) {
                            layoutOverlappingEvents(timedEventsToday, date, zone)
                        }
                        dayLayouts.forEach { layout ->
                            val top = timeOffsetFromMinutes(layout.startMinutes)
                            val rawHeight = timeOffsetFromMinutes(layout.endMinutes) - top
                            val blockHeight = if (rawHeight < 28.dp) 28.dp else rawHeight
                            val laneWidth = columnWidth / layout.laneCount
                            Box(
                                modifier = Modifier
                                    .offset(x = laneWidth * layout.lane, y = top)
                                    .width(laneWidth)
                                    .height(blockHeight)
                                    .padding(horizontal = 2.dp)
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(Color(layout.event.color))
                                    .clickable { onEventClick(layout.event) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(layout.event.title, style = MaterialTheme.typography.bodySmall, maxLines = 3, color = Color.White)
                            }
                        }

                        if (isToday) {
                            val lineY = timeOffsetFromMinutes(nowTime.hour * 60 + nowTime.minute)
                            Box(
                                modifier = Modifier
                                    .offset(y = lineY - 1.dp)
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(Color.Red)
                            )
                            Text(
                                "%02d:%02d".format(nowTime.hour, nowTime.minute),
                                color = Color.Red,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.offset(y = lineY - 14.dp, x = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}