package dev.yahaveliyahu.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import java.time.temporal.ChronoUnit
import androidx.compose.runtime.mutableIntStateOf

val WEEK_HOUR_HEIGHT = 60.dp
val WEEK_HOUR_LABEL_WIDTH = 32.dp
private const val WEEK_INITIAL_SCROLL_HOUR = 6
private const val WEEK_SWIPE_THRESHOLD_PX = 100f
private val WEEK_ALLDAY_LANE_HEIGHT = 20.dp

/** Milliseconds from [now] until the start of the next minute, so a tick scheduled with this
 * delay lands right on the device clock's minute boundary instead of drifting on a fixed
 * interval. Floored at 50ms so we never schedule a near-zero/negative delay.
 * Non-private so [DailyCalendarView] can reuse it instead of re-implementing it. */
fun millisUntilNextMinute(now: LocalDateTime): Long {
    val nanosIntoMinute = now.second * 1_000_000_000L + now.nano
    val nanosRemaining = 60_000_000_000L - nanosIntoMinute
    return (nanosRemaining / 1_000_000L).coerceAtLeast(50L)
}

val WEEK_MONTH_ABBREVIATIONS = listOf(
    "ינואר", "פברואר", "מרץ", "אפריל", "מאי", "יוני", "יולי", "אוגוסט", "ספטמבר", "אוקטובר", "נובמבר", "דצמבר"
)

fun timeOffsetFromMinutes(minutes: Int): Dp = WEEK_HOUR_HEIGHT * (minutes / 60f)

/** One timed event's computed side-by-side slot: [lane] of [laneCount] equal-width columns
 *  within its day. [startMinutes]/[endMinutes] are minutes since midnight *of the day this
 *  layout was computed for* -- endMinutes can reach up to 1440 (24:00) for an event ending
 *  exactly at the next day's midnight, which is this day's end rather than a new day's start. */
data class TimedEventLayout(val event: CalendarEvent, val lane: Int, val laneCount: Int, val startMinutes: Int, val endMinutes: Int)

/**
 * Lays out a single day's timed events so ones that overlap in time sit side by side instead of
 * stacking on top of each other. Standard two-pass approach: first group events into clusters of
 * mutual overlap (sorted by start time, a new cluster starts whenever an event begins at or after
 * every event so far in the current cluster has ended), then within each cluster greedily assign
 * the lowest free lane -- same greedy interval-packing idea already used for the month grid's
 * multi-day banner lanes, just applied to time-of-day instead of day-of-month.
 *
 * Works in minutes-since-midnight rather than [LocalTime] specifically so a 23:00-24:00 event
 * (end lands exactly at the next day's midnight) can be represented as ending at minute 1440
 * instead of wrapping back around to 0, which would make it look zero-length or invalid.
 */
fun layoutOverlappingEvents(dayEvents: List<CalendarEvent>, dayDate: LocalDate, zone: ZoneId): List<TimedEventLayout> {
    if (dayEvents.isEmpty()) return emptyList()

    data class Timed(val event: CalendarEvent, val startMinutes: Int, val endMinutes: Int)
    val timed = dayEvents.map { event ->
        val startZoned = event.start.atZone(zone)
        val startMinutes = startZoned.toLocalTime().hour * 60 + startZoned.toLocalTime().minute
        val endZoned = (event.end ?: event.start.plusSeconds(1800)).atZone(zone)
        val endLocalTime = endZoned.toLocalTime()
        val rawEndMinutes = if (endZoned.toLocalDate().isAfter(dayDate) && endLocalTime == LocalTime.MIDNIGHT) {
            24 * 60
        } else {
            endLocalTime.hour * 60 + endLocalTime.minute
        }
        val endMinutes = if (rawEndMinutes <= startMinutes) startMinutes + 30 else rawEndMinutes
        Timed(event, startMinutes, endMinutes)
    }.sortedBy { it.startMinutes }

    val clusters = mutableListOf<MutableList<Timed>>()
    var clusterEnd = -1
    timed.forEach { te ->
        if (clusters.isEmpty() || te.startMinutes >= clusterEnd) {
            clusters += mutableListOf(te)
            clusterEnd = te.endMinutes
        } else {
            clusters.last() += te
            if (te.endMinutes > clusterEnd) clusterEnd = te.endMinutes
        }
    }

    val result = mutableListOf<TimedEventLayout>()
    clusters.forEach { cluster ->
        val laneEndMinutes = mutableListOf<Int>()
        val laneOf = mutableMapOf<Timed, Int>()
        cluster.forEach { te ->
            val laneIndex = laneEndMinutes.indexOfFirst { it <= te.startMinutes }
            if (laneIndex >= 0) {
                laneEndMinutes[laneIndex] = te.endMinutes
                laneOf[te] = laneIndex
            } else {
                laneEndMinutes += te.endMinutes
                laneOf[te] = laneEndMinutes.size - 1
            }
        }
        val laneCount = laneEndMinutes.size
        cluster.forEach { te -> result += TimedEventLayout(te.event, laneOf.getValue(te), laneCount, te.startMinutes, te.endMinutes) }
    }
    return result
}

/** Which lane (0, 1, 2...) each multi-day event occupies within the all-day row, so two that
 *  genuinely overlap get stacked rows instead of one hiding the other -- same greedy
 *  interval-packing already used for the month grid's own multi-day banner lanes. */
private fun assignAllDayLanes(events: List<CalendarEvent>, zone: ZoneId): Map<String, Int> {
    data class Span(val event: CalendarEvent, val start: LocalDate, val end: LocalDate)
    val spans = events.map { event ->
        Span(event, event.start.atZone(zone).toLocalDate(), (event.end ?: event.start).atZone(zone).toLocalDate())
    }.sortedBy { it.start }

    val laneEndDates = mutableListOf<LocalDate>()
    val result = mutableMapOf<String, Int>()
    spans.forEach { span ->
        val laneIndex = laneEndDates.indexOfFirst { it.isBefore(span.start) }
        if (laneIndex >= 0) {
            laneEndDates[laneIndex] = span.end
            result[span.event.id] = laneIndex
        } else {
            laneEndDates += span.end
            result[span.event.id] = laneEndDates.size - 1
        }
    }
    return result
}

/** Draws a thin dashed line along the top edge of whatever this modifier is attached to,
 *  using that element's own resolved size -- so it's always exactly as wide as its host and
 *  never depends on a separate, independently-sized composable getting the right dimensions
 *  from somewhere else. */
fun Modifier.dottedTopBorder(color: Color): Modifier = this.drawBehind {
    drawLine(
        color = color,
        start = Offset(0f, 0f),
        end = Offset(size.width, 0f),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
    )
}

/** Same idea as [dottedTopBorder], but along the start (right, under RTL) edge -- used for the
 *  separator between day columns. */
fun Modifier.dottedStartBorder(color: Color): Modifier = this.drawBehind {
    drawLine(
        color = color,
        start = Offset(0f, 0f),
        end = Offset(0f, size.height),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
    )
}

/**
 * Weekly hour-grid view: 7 day columns (Sunday on the right through Saturday on the left,
 * matching RTL), an all-day row above for multi-day events, and a dotted hourly grid below with
 * single-day events positioned by their actual start/end time and laid out side by side when
 * they overlap.
 *
 * Still deferred to a further round: dragging an event to reschedule it, and multi-week swipe
 * momentum/animation (swiping changes the week instantly rather than animating a page transition).
 */
@Composable
fun WeeklyCalendarView(
    weekStart: LocalDate,
    selectedDate: LocalDate,
    theme: CalendarTheme,
    events: List<CalendarEvent>,
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit,
    onTodayClick: () -> Unit,
    onDaySelected: (LocalDate) -> Unit,
    onAddEventClick: (date: LocalDate, time: LocalTime) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    onWeekChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    // Ticks right on each minute boundary of the device clock so both "today" and the
    // current-time line stay in sync with it (including rolling over to the next day's
    // column right at midnight) -- not just computed once, and not drifting on a fixed
    // interval like a plain "every 30s" loop would.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(millisUntilNextMinute(now))
        }
    }
    val today = now.toLocalDate()
    val nowTime = now.toLocalTime()

    val zone = remember { ZoneId.systemDefault() }
    val weekDates = remember(weekStart) { (0..6).map { weekStart.plusDays(it.toLong()) } }

    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    LaunchedEffect(Unit) {
        scrollState.scrollTo(with(density) { (WEEK_HOUR_HEIGHT * WEEK_INITIAL_SCROLL_HOUR).toPx().toInt() })
    }

    val (multiDayEvents, timedEventsByDate) = remember(events) {
        val multiDay = mutableListOf<CalendarEvent>()
        val timedByDate = mutableMapOf<LocalDate, MutableList<CalendarEvent>>()
        events.filterNot { it.isDeleted }.forEach { event ->
            val startDate = event.start.atZone(zone).toLocalDate()
            val endZoned = (event.end ?: event.start).atZone(zone)
            val rawEndDate = endZoned.toLocalDate()
            // An event ending exactly at midnight is conventionally ending at "24:00" of its
            // start day, not starting a new day at "0:00" -- e.g. 23:00-24:00 is a same-day,
            // one-hour event, not a two-day one.
            val effectiveEndDate = if (rawEndDate.isAfter(startDate) && endZoned.toLocalTime() == LocalTime.MIDNIGHT) {
                rawEndDate.minusDays(1)
            } else {
                rawEndDate
            }
            if (effectiveEndDate.isAfter(startDate)) {
                multiDay += event
            } else {
                timedByDate.getOrPut(startDate) { mutableListOf() }.add(event)
            }
        }
        multiDay to timedByDate
    }
    val multiDayEventsThisWeek = remember(multiDayEvents, weekDates) {
        multiDayEvents.filter { event ->
            val startDate = event.start.atZone(zone).toLocalDate()
            val endDate = (event.end ?: event.start).atZone(zone).toLocalDate()
            !endDate.isBefore(weekDates.first()) && !startDate.isAfter(weekDates.last())
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .pointerInput(weekStart) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onDragEnd = {
                        when {
                            totalDrag <= -WEEK_SWIPE_THRESHOLD_PX -> onWeekChange(weekStart.plusWeeks(1))
                            totalDrag >= WEEK_SWIPE_THRESHOLD_PX -> onWeekChange(weekStart.minusWeeks(1))
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
                WEEK_MONTH_ABBREVIATIONS[weekStart.monthValue - 1],
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                textAlign = TextAlign.Center
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(WEEK_HOUR_LABEL_WIDTH))
                weekDates.forEach { date ->
                    val isToday = date == today
                    val isSelected = date == selectedDate && !isToday
                    val textColor = when (date.dayOfWeek) {
                        DayOfWeek.FRIDAY -> Color(theme.fridayTextColor)
                        DayOfWeek.SATURDAY -> Color(theme.saturdayTextColor)
                        else -> Color(theme.defaultTextColor)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onDaySelected(date) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(eventFormWeekdayLabel(date.dayOfWeek), style = MaterialTheme.typography.labelSmall, color = textColor)
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(28.dp)
                                .clip(MaterialTheme.shapes.small)
                                .then(
                                    when {
                                        isToday -> Modifier.background(Color(theme.todayIndicatorColor))
                                        isSelected -> Modifier.border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                                        else -> Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isToday) Color.White else textColor
                            )
                        }
                    }
                }
            }

            if (multiDayEventsThisWeek.isNotEmpty()) {
                val laneByEventId = remember(multiDayEventsThisWeek, zone) { assignAllDayLanes(multiDayEventsThisWeek, zone) }
                val laneCount = (laneByEventId.values.maxOrNull() ?: 0) + 1
                var rowWidthPx by remember { mutableIntStateOf(0) }
                val rowWidth = with(density) { rowWidthPx.toDp() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .height(WEEK_ALLDAY_LANE_HEIGHT * laneCount)
                        .onSizeChanged { size -> rowWidthPx = size.width }
                ) {
                    val dayColumnWidth = (rowWidth - WEEK_HOUR_LABEL_WIDTH) / 7
                    multiDayEventsThisWeek.forEach { event ->
                        val startDate = event.start.atZone(zone).toLocalDate()
                        val endDate = (event.end ?: event.start).atZone(zone).toLocalDate()
                        val segStart = maxOf(startDate, weekDates.first())
                        val segEnd = minOf(endDate, weekDates.last())
                        val startCol = ChronoUnit.DAYS.between(weekDates.first(), segStart).toInt()
                        val spanDays = ChronoUnit.DAYS.between(segStart, segEnd).toInt() + 1
                        val lane = laneByEventId[event.id] ?: 0
                        Box(
                            modifier = Modifier
                                .offset(x = WEEK_HOUR_LABEL_WIDTH + dayColumnWidth * startCol, y = WEEK_ALLDAY_LANE_HEIGHT * lane)
                                .width(dayColumnWidth * spanDays)
                                .height(WEEK_ALLDAY_LANE_HEIGHT)
                                .padding(horizontal = 1.dp, vertical = 1.dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(Color(event.color))
                                .clickable { onEventClick(event) }
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                event.title,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                color = Color.White,
                                modifier = Modifier.align(Alignment.CenterStart)
                            )
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
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.width(WEEK_HOUR_LABEL_WIDTH)) {
                        for (hour in 0..23) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(WEEK_HOUR_HEIGHT)
                            ) {
                                // Labels match each row's actual hour exactly (0 at the very
                                // top, just after midnight, through 23 at the bottom) so a
                                // event or the current-time line always lines up with the
                                // label a user would expect for that time.
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
                    weekDates.forEach { date ->
                        val outlineColor = MaterialTheme.colorScheme.outlineVariant
                        var columnWidthPx by remember { mutableIntStateOf(0) }
                        val columnWidth = with(density) { columnWidthPx.toDp() }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .dottedStartBorder(outlineColor)
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

                            val dayLayouts = remember(timedEventsByDate, date, zone) {
                                layoutOverlappingEvents(timedEventsByDate[date].orEmpty(), date, zone)
                            }
                            dayLayouts.forEach { layout ->
                                val top = timeOffsetFromMinutes(layout.startMinutes)
                                val rawHeight = timeOffsetFromMinutes(layout.endMinutes) - top
                                val blockHeight = if (rawHeight < 24.dp) 24.dp else rawHeight
                                val laneWidth = columnWidth / layout.laneCount
                                Box(
                                    modifier = Modifier
                                        .offset(x = laneWidth * layout.lane, y = top)
                                        .width(laneWidth)
                                        .height(blockHeight)
                                        .padding(horizontal = 1.dp)
                                        .clip(MaterialTheme.shapes.extraSmall)
                                        .background(Color(layout.event.color))
                                        .clickable { onEventClick(layout.event) }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(layout.event.title, style = MaterialTheme.typography.labelSmall, maxLines = 2, color = Color.White)
                                }
                            }

                            // Confined to today's own column, per the requested behavior --
                            // not a line spanning the full week.
                            if (date == today) {
                                val lineY = timeOffsetFromMinutes(nowTime.hour * 60 + nowTime.minute)
                                Box(
                                    modifier = Modifier
                                        .offset(y = lineY - 1.dp)
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .background(Color.Red)
                                )
                                Text(
                                    formattedTimeLabel(nowTime),
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
}