package dev.yahaveliyahu.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yahaveliyahu.calendar_core.CalendarEvent
import dev.yahaveliyahu.calendar_core.CalendarTheme
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.input.pointer.pointerInput

private val ANNUAL_MONTH_ABBREVIATIONS = listOf(
    "ינו'", "פבר'", "מרץ", "אפר'", "מאי", "יוני", "יולי", "אוג'", "ספט'", "אוק'", "נוב'", "דצמ'"
)

/** Sunday-first, matching the rest of the app's week-start convention. Index 5 = Friday,
 *  index 6 = Saturday -- used both for the header row and for picking each day cell's color. */
private val MINI_WEEKDAY_LABELS = listOf("א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳")

private val EVENT_DAY_HIGHLIGHT_COLOR = Color(0xFFFFD54F)

/** Net drag distance (px) needed before a horizontal swipe counts as "change the year" rather
 *  than an incidental touch/scroll  */
private const val SWIPE_THRESHOLD_PX = 100f

/**
 * Full-year overview as a 3x4 grid of standard mini month-calendars, matching the reference
 * design exactly rather than the earlier day-by-day row layout. Under forced RTL, LazyVerticalGrid
 * places item 0 in the rightmost column of each row, which is what gives January-top-right,
 * reading right to left then down (Jan, Feb, Mar / Apr, May, Jun / ...) without needing to
 * manually reorder anything.
 *
 * Tapping any month card calls [onMonthClick] with that month number -- the caller is expected
 * to switch back to month view and jump the calendar there, so the annual and monthly views act
 * as one linked pair rather than two disconnected screens.
 */
@Composable
fun AnnualCalendarView(
    year: Int,
    theme: CalendarTheme,
    events: List<CalendarEvent>,
    onMonthClick: (Int) -> Unit,
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit,
    onTodayClick: () -> Unit,
    onYearChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }

    /** Every date touched by a non-deleted event, expanding multi-day spans across every day
     *  they cover (not just their start date) -- same "which days does this event touch"
     *  logic used elsewhere for the month grid's own multi-day banner and DayEventsDialog. */
    val eventDates = remember(events) {
        events.filterNot { it.isDeleted }.flatMap { event ->
            val start = event.start.atZone(ZoneId.systemDefault()).toLocalDate()
            val end = (event.end ?: event.start).atZone(ZoneId.systemDefault()).toLocalDate()
            generateSequence(start) { d -> if (d.isBefore(end)) d.plusDays(1) else null }.toList()
        }.toSet()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {

        Column(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(year) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragEnd = {
                            when {
                                totalDrag <= -SWIPE_THRESHOLD_PX -> onYearChange(year + 1)
                                totalDrag >= SWIPE_THRESHOLD_PX -> onYearChange(year - 1)
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

                    IconButton(
                        onClick = onSearchClick
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(
                        onClick = onMenuClick
                    ) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Menu"
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    horizontal = 18.dp,
                    vertical = 16.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
            item(span = { GridItemSpan(3) }) {
                Text(
                    year.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center
                )
            }
            items(12) { index ->
                val month = index + 1
                MiniMonthCalendar(
                    year = year,
                    month = month,
                    monthLabel = ANNUAL_MONTH_ABBREVIATIONS[index],
                    isCurrentMonth = year == today.year && month == today.monthValue,
                    today = today,
                    eventDates = eventDates,
                    theme = theme,
                    onClick = { onMonthClick(month) }
                )
            }
        }
    }
}
}

@Composable
private fun MiniMonthCalendar(
    year: Int,
    month: Int,
    monthLabel: String,
    isCurrentMonth: Boolean,
    today: LocalDate,
    eventDates: Set<LocalDate>,
    theme: CalendarTheme,
    onClick: () -> Unit
) {
    val firstOfMonth = remember(year, month) { LocalDate.of(year, month, 1) }
    val daysInMonth = remember(firstOfMonth) { firstOfMonth.lengthOfMonth() }
    // DayOfWeek.value: Monday=1 .. Sunday=7. "% 7" turns that into a Sunday-first 0..6 offset.
    val startOffset = remember(firstOfMonth) { firstOfMonth.dayOfWeek.value % 7 }
    val totalCells = remember(startOffset, daysInMonth) {
        val raw = startOffset + daysInMonth
        ((raw + 6) / 7) * 7
    }

    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(1.dp)
    ) {
        Text(
            monthLabel,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrentMonth) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            MINI_WEEKDAY_LABELS.forEachIndexed { index, label ->
                val color = when (index) {
                    5 -> Color(theme.fridayTextColor)
                    6 -> Color(theme.saturdayTextColor)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
        for (rowStart in 0 until totalCells step 7) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val day = rowStart + col - startOffset + 1
                    if (day in 1..daysInMonth) {
                        val date = firstOfMonth.withDayOfMonth(day)
                        val textColor = when (col) {
                            5 -> Color(theme.fridayTextColor)
                            6 -> Color(theme.saturdayTextColor)
                            else -> Color(theme.defaultTextColor)
                        }
                        MiniDayCell(
                            day = day,
                            isToday = date == today,
                            hasEvent = date in eventDates,
                            textColor = textColor,
                            todayColor = Color(theme.todayIndicatorColor),
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(1.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniDayCell(
    day: Int,
    isToday: Boolean,
    hasEvent: Boolean,
    textColor: Color,
    todayColor: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            isToday -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(todayColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = day.toString(),
                        fontSize = 8.sp,
                        lineHeight = 8.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        color = Color.White
                    )
                }
            }
            hasEvent -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(EVENT_DAY_HIGHLIGHT_COLOR),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = day.toString(),
                        fontSize = 8.sp,
                        lineHeight = 8.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        color = Color.Black
                    )
                }
            }
            else -> {
                Text(
                    text = day.toString(),
                    fontSize = 8.sp,
                    lineHeight = 8.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    color = textColor
                )
            }
        }
    }
}
