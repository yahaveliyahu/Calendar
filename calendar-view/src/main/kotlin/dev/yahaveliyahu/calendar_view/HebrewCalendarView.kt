package dev.yahaveliyahu.calendar_view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Parcel
import android.os.Parcelable
import android.util.TypedValue
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import dev.yahaveliyahu.calendar_core.CalendarConfig
import dev.yahaveliyahu.calendar_core.CalendarEvent
import dev.yahaveliyahu.calendar_core.CalendarTheme
import dev.yahaveliyahu.calendar_core.CellShape
import dev.yahaveliyahu.calendar_core.HebrewCalendarSystem
import dev.yahaveliyahu.calendar_core.HolidayInfo
import dev.yahaveliyahu.calendar_core.HolidayRegistry
import dev.yahaveliyahu.calendar_core.SelectionMode
import dev.yahaveliyahu.calendar_core.SystemDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Custom View: draws a month grid on Canvas, organized by the ACTIVE primary calendar
 * system's own month boundaries (so Hebrew mode shows a real Hebrew month, e.g. Tammuz,
 * not a Gregorian month relabeled) -- see [dev.yahaveliyahu.calendar_core.CalendarSystem.monthBounds].
 * Supports tap-to-select, swipe-to-change-month, and jumping to arbitrary distant months.
 */
class HebrewCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var theme: CalendarTheme = CalendarTheme.LIGHT_DEFAULT
        set(value) { field = value; invalidate() }

    var config: CalendarConfig = CalendarConfig()
        set(value) { field = value; requestLayout(); invalidate() }

    var holidayRegistry: HolidayRegistry = HolidayRegistry()
        set(value) { field = value; invalidate() }

    var onDateSelectedListener: ((LocalDate) -> Unit)? = null
    /** Fired whenever the displayed period changes, with the PRIMARY calendar system's own
     *  label for that period's first day */
    var onMonthChangedListener: ((SystemDate) -> Unit)? = null

    /** The anchor date (any day within the currently displayed period) driving monthBounds(). */
    private var displayedAnchor: LocalDate = LocalDate.now()

    /** Public read of [displayedAnchor] -- lets a host app compare it against another date
     *  (e.g. "is today already visible on the grid, so a 'jump to today' button should be a
     *  no-op?") without needing to reconstruct a raw date out of [currentPeriodLabel]'s
     *  system-specific display labels. Combine with [onMonthChangedListener] to stay correct
     *  regardless of how the displayed period changed -- prev/next, a swipe, or scrollToPeriod
     *  called directly, since all of those already funnel through here. */
    fun getDisplayedAnchor(): LocalDate = displayedAnchor
    private val selectedDates = linkedSetOf<LocalDate>()
    private var events: List<CalendarEvent> = emptyList()
    private var eventsByDate: Map<LocalDate, List<CalendarEvent>> = emptyMap()
    private var holidaysByDate: Map<LocalDate, List<HolidayInfo>> = emptyMap()

    /** A multi-day event's span, precomputed once in [setEvents] rather than re-derived from
     *  Instants on every draw pass. */
    private data class MultiDaySpan(val event: CalendarEvent, val startDate: LocalDate, val endDate: LocalDate)
    private var multiDaySpans: List<MultiDaySpan> = emptyList()

    /** Which banner lane (0, 1, 2...) each multi-day event occupies, keyed by event id.
     *  Assigned once in [setEvents] via [assignLanes] rather than per-row, so an event that
     *  continues across a week boundary keeps the same lane in every row it appears in instead
     *  of jumping around, while non-overlapping events elsewhere in the month can still share a
     *  lane number. */
    private var laneByEventId: Map<String, Int> = emptyMap()

    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chipBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chipTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val overflowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var cellWidth = 0f
    private var cellHeight = 0f
    private var headerHeight = 0f
    private var titleHeight = 0f

    /** Locale used for the month/year title the view draws at its own top. Defaults to the
     *  device locale; a host app can force a specific one (e.g. Hebrew script always). */
    var titleLocale: Locale = Locale.getDefault()
        set(value) { field = value; invalidate() }

    /** True when the grid should render Sunday-on-the-right through Saturday-on-the-left,
     *  mirroring how the native calendar looks under a Hebrew/RTL locale. Driven by the
     *  view's resolved layout direction, same mechanism Android uses for everything else. */
    private val isRtl: Boolean
        get() = layoutDirection == LAYOUT_DIRECTION_RTL

    private fun displayColumn(logicalCol: Int): Int = if (isRtl) 6 - logicalCol else logicalCol

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val dx = e2.x - (e1?.x ?: e2.x)
            if (abs(dx) > cellWidth && abs(velocityX) > abs(velocityY)) {
                if (dx < 0) goToNextMonth() else goToPreviousMonth()
                return true
            }
            return false
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            dateAt(e.x, e.y)?.let { onDayTapped(it) }
            return true
        }
    })

    init {
        isClickable = true
        isFocusable = true
        attrs?.let { applyXmlAttributes(it) }
    }

    private fun applyXmlAttributes(attrs: AttributeSet) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.HebrewCalendarView)
        try {
            theme = theme.copy(
                primaryColor = a.getColor(R.styleable.HebrewCalendarView_hcv_primaryColor, theme.primaryColor),
                todayIndicatorColor = a.getColor(R.styleable.HebrewCalendarView_hcv_todayIndicatorColor, theme.todayIndicatorColor),
                selectedDayColor = a.getColor(R.styleable.HebrewCalendarView_hcv_selectedDayColor, theme.selectedDayColor),
                fridayTextColor = a.getColor(R.styleable.HebrewCalendarView_hcv_fridayTextColor, theme.fridayTextColor),
                saturdayTextColor = a.getColor(R.styleable.HebrewCalendarView_hcv_saturdayTextColor, theme.saturdayTextColor),
                defaultTextColor = a.getColor(R.styleable.HebrewCalendarView_hcv_defaultTextColor, theme.defaultTextColor),
                holidayDotColor = a.getColor(R.styleable.HebrewCalendarView_hcv_holidayDotColor, theme.holidayDotColor),
                cellShape = when (a.getInt(R.styleable.HebrewCalendarView_hcv_cellShape, 0)) {
                    1 -> CellShape.ROUNDED_SQUARE
                    else -> CellShape.CIRCLE
                }
            )
            if (a.getBoolean(R.styleable.HebrewCalendarView_hcv_useHebrewAsPrimary, false)) {
                config = config.copy(primaryCalendarSystem = HebrewCalendarSystem())
            }
        } finally {
            a.recycle()
        }
    }

    // ---- Public API ----

    /** Jump to any period (near or distant) containing [anchor], per the active calendar system. */
    fun scrollToPeriod(anchor: LocalDate) {
        displayedAnchor = anchor
        requestLayout()
        invalidate()
        onMonthChangedListener?.invoke(currentPeriodLabel())
    }

    /** Convenience overload for callers thinking in Gregorian terms. */
    fun scrollToMonth(month: YearMonth) = scrollToPeriod(month.atDay(1))

    fun goToNextMonth() = scrollToPeriod(config.primaryCalendarSystem.shiftMonths(displayedAnchor, 1))
    fun goToPreviousMonth() = scrollToPeriod(config.primaryCalendarSystem.shiftMonths(displayedAnchor, -1))

    /** The primary calendar system's label for the first day of the currently displayed period */
    fun currentPeriodLabel(locale: Locale = Locale.getDefault()): SystemDate {
        val periodStart = config.primaryCalendarSystem.monthBounds(displayedAnchor).first
        return config.primaryCalendarSystem.labelFor(periodStart, locale)
    }

    fun selectDate(date: LocalDate) {
        when (config.selectionMode) {
            SelectionMode.SINGLE -> {
                selectedDates.clear(); selectedDates.add(date)
            }
            SelectionMode.MULTIPLE -> {
                if (!selectedDates.remove(date)) selectedDates.add(date)
            }
            SelectionMode.RANGE -> {
                if (selectedDates.size >= 2) selectedDates.clear()
                selectedDates.add(date)
            }
        }
        invalidate()
        onDateSelectedListener?.invoke(date)
    }

    fun setEvents(newEvents: List<CalendarEvent>) {
        events = newEvents.filter { !it.isDeleted }
        val zone = java.time.ZoneId.systemDefault()
        val (multiDay, singleDay) = events.partition { event ->
            val startDate = event.start.atZone(zone).toLocalDate()
            val endDate = (event.end ?: event.start).atZone(zone).toLocalDate()
            endDate.isAfter(startDate)
        }
        eventsByDate = singleDay.groupBy {
            it.start.atZone(zone).toLocalDate()
        }.mapValues { (_, dayEvents) -> dayEvents.sortedBy { it.start } }
        multiDaySpans = multiDay.map { event ->
            MultiDaySpan(
                event = event,
                startDate = event.start.atZone(zone).toLocalDate(),
                endDate = (event.end ?: event.start).atZone(zone).toLocalDate()
            )
        }.sortedBy { it.startDate }
        laneByEventId = assignLanes(multiDaySpans)
        invalidate()
    }

    /** Greedy interval-graph coloring: walk spans oldest-start-first, and give each one the
     *  lowest lane index whose last-assigned span already ended before this one starts. This is
     *  the standard "minimum number of meeting rooms" packing -- applied here across the whole
     *  set of spans (not per displayed row), which is what keeps a single event's lane stable
     *  across every row it happens to be visible in. */
    private fun assignLanes(spans: List<MultiDaySpan>): Map<String, Int> {
        val laneLastEndDate = mutableListOf<LocalDate>()
        val result = mutableMapOf<String, Int>()
        spans.forEach { span ->
            val laneIndex = laneLastEndDate.indexOfFirst { it.isBefore(span.startDate) }
            if (laneIndex >= 0) {
                laneLastEndDate[laneIndex] = span.endDate
                result[span.event.id] = laneIndex
            } else {
                laneLastEndDate.add(span.endDate)
                result[span.event.id] = laneLastEndDate.size - 1
            }
        }
        return result
    }

    fun refreshHolidays() {
        val (periodStart, periodEnd) = config.primaryCalendarSystem.monthBounds(displayedAnchor)
        val start = periodStart.minusDays(35)
        val end = periodEnd.plusDays(35)
        holidaysByDate = holidayRegistry.holidaysFor(start, end).groupBy { it.date }
        invalidate()
    }

    private fun onDayTapped(date: LocalDate) {
        val (periodStart, periodEnd) = config.primaryCalendarSystem.monthBounds(displayedAnchor)
        if (date.isBefore(periodStart) || date.isAfter(periodEnd)) {
            scrollToPeriod(date)
        }
        selectDate(date)
        performClick()
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    // Layout math shared by measure / draw / hit-testing (kept in one place on purpose
    private fun periodBounds(): Pair<LocalDate, LocalDate> = config.primaryCalendarSystem.monthBounds(displayedAnchor)

    private fun gridStartFor(periodStart: LocalDate): LocalDate {
        val leadingOffset = (periodStart.dayOfWeek.value - config.firstDayOfWeek.value + 7) % 7
        return periodStart.minusDays(leadingOffset.toLong())
    }

    private fun rowsNeededFor(periodStart: LocalDate, periodEnd: LocalDate): Int {
        val leadingOffset = (periodStart.dayOfWeek.value - config.firstDayOfWeek.value + 7) % 7
        val daysInPeriod = ChronoUnit.DAYS.between(periodStart, periodEnd).toInt() + 1
        return ceil((leadingOffset + daysInPeriod) / 7.0).toInt()
    }

    /** One slot's worth of vertical space for a multi-day banner -- same height as a regular
     *  chip slot, so the two read as consistent sizes even though banners live in their own
     *  reserved area above the regular chip stack. */
    private fun bannerSlotHeight(): Float = cellHeight * 0.19f

    /** How many banner lanes are in use anywhere within [rowStart]..[rowEnd] -- i.e. the row's
     *  extra height, in slots, on top of the fixed day-number+regular-chips budget. 0 when no
     *  multi-day event touches this week at all. */
    private fun bannerLaneCountForRow(rowStart: LocalDate, rowEnd: LocalDate): Int {
        val lanesUsed = multiDaySpans
            .asSequence()
            .filter { !it.endDate.isBefore(rowStart) && !it.startDate.isAfter(rowEnd) }
            .mapNotNull { laneByEventId[it.event.id] }
        return (lanesUsed.maxOrNull() ?: -1) + 1
    }

    /** A row's actual on-screen height: the fixed per-row budget (day number + up to 2 regular
     *  chips, same as before multi-day events existed) plus one slot per banner lane this
     *  particular week needs. Grows without an artificial cap -- if several multi-day events
     *  genuinely overlap the same week, the row simply gets taller so every one of them still
     *  gets its own visible bar, rather than hiding any of them behind a "+N" indicator. */
    private fun rowHeight(rowStart: LocalDate, rowEnd: LocalDate): Float =
        cellHeight + bannerLaneCountForRow(rowStart, rowEnd) * bannerSlotHeight()

    /** Row heights for every row of the currently displayed grid, in order -- computed once and
     *  reused by measure, draw, and hit-testing so they can never drift out of sync with each other */
    private fun rowHeights(gridStart: LocalDate, rows: Int): List<Float> =
        (0 until rows).map { row ->
            val rowStart = gridStart.plusDays((row * 7).toLong())
            rowHeight(rowStart, rowStart.plusDays(6))
        }

    // ---- Measurement ----

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        cellWidth = width / 7f
        cellHeight = cellWidth * 1.35f
        headerHeight = cellWidth * 0.55f
        titleHeight = cellWidth * 0.8f
        val (periodStart, periodEnd) = periodBounds()
        val rows = rowsNeededFor(periodStart, periodEnd)
        val gridStart = gridStartFor(periodStart)
        val height = (titleHeight + headerHeight + rowHeights(gridStart, rows).sum()).toInt()
        setMeasuredDimension(width, height)
    }

    // ---- Drawing ----

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        refreshHolidaysIfNeeded()
        canvas.drawColor(theme.backgroundColor)

        dayPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, theme.dayTextSizeSp, resources.displayMetrics)
        headerPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, theme.headerTextSizeSp, resources.displayMetrics)
        titlePaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, theme.headerTextSizeSp * 1.6f, resources.displayMetrics)
        titlePaint.color = theme.primaryColor

        drawTitle(canvas)
        drawWeekdayHeader(canvas)
        drawMonthGrid(canvas)
    }

    /** The view draws its OWN title -- the month/year name it already computes for
     *  currentPeriodLabel() -- instead of relying on a host app to read that value out
     *  and re-render it in a separate, synchronized element. One less moving part. */
    private fun drawTitle(canvas: Canvas) {
        val label = currentPeriodLabel(titleLocale)
        val text = "${label.monthName} ${label.yearLabel}"
        val baselineY = titleHeight / 2f - (titlePaint.descent() + titlePaint.ascent()) / 2f
        canvas.drawText(text, width / 2f, baselineY, titlePaint)
    }

    private var lastHolidayRefreshAnchor: LocalDate? = null
    private fun refreshHolidaysIfNeeded() {
        if (lastHolidayRefreshAnchor != displayedAnchor) {
            lastHolidayRefreshAnchor = displayedAnchor
            refreshHolidays()
        }
    }

    private fun drawWeekdayHeader(canvas: Canvas) {
        val firstDow = config.firstDayOfWeek
        for (i in 0 until 7) {
            val dow = DayOfWeek.of((firstDow.value - 1 + i) % 7 + 1)
            val label = dow.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            val col = displayColumn(i)
            headerPaint.color = when (dow) {
                DayOfWeek.FRIDAY -> theme.fridayTextColor
                DayOfWeek.SATURDAY -> theme.saturdayTextColor
                else -> theme.defaultTextColor
            }
            canvas.drawText(label, cellWidth * col + cellWidth / 2f, titleHeight + headerHeight * 0.7f, headerPaint)
        }
    }

    private fun drawMonthGrid(canvas: Canvas) {
        val (periodStart, periodEnd) = periodBounds()
        val gridStart = gridStartFor(periodStart)
        val rows = rowsNeededFor(periodStart, periodEnd)
        val today = LocalDate.now()

        var rowTop = titleHeight + headerHeight
        for (row in 0 until rows) {
            val rowStart = gridStart.plusDays((row * 7).toLong())
            val rowEnd = rowStart.plusDays(6)
            val rowSpans = multiDaySpans.filter { !it.endDate.isBefore(rowStart) && !it.startDate.isAfter(rowEnd) }
            val laneCount = bannerLaneCountForRow(rowStart, rowEnd)
            val thisRowHeight = rowHeight(rowStart, rowEnd)

            for (col in 0 until 7) {
                val date = gridStart.plusDays((row * 7 + col).toLong())
                val pixelCol = displayColumn(col)
                val cellLeft = cellWidth * pixelCol
                val isInPeriod = !date.isBefore(periodStart) && !date.isAfter(periodEnd)
                drawDayCell(canvas, date, cellLeft, rowTop, isInDisplayedPeriod = isInPeriod, isToday = date == today, bannerLaneCount = laneCount)
            }

            // Every genuinely overlapping multi-day event in this row gets its own bar -- no
            // cap, no "+N" hiding any of them; that's exactly why the row grew taller above.
            rowSpans.forEach { span ->
                val lane = laneByEventId[span.event.id] ?: 0
                drawMultiDayBanner(canvas, span, rowStart, rowEnd, rowTop, lane)
            }

            rowTop += thisRowHeight
        }
    }

    private fun drawDayCell(
        canvas: Canvas,
        date: LocalDate,
        cellLeft: Float,
        cellTop: Float,
        isInDisplayedPeriod: Boolean,
        isToday: Boolean,
        bannerLaneCount: Int
    ) {
        val cx = cellLeft + cellWidth / 2f
        val markerRadius = cellWidth * 0.32f
        val markerCy = cellTop + markerRadius + cellHeight * 0.08f

        val isSelected = date in selectedDates
        val isFriday = date.dayOfWeek == DayOfWeek.FRIDAY
        val isSaturday = date.dayOfWeek == DayOfWeek.SATURDAY

        if (isSelected || isToday) {
            circlePaint.color = if (isSelected) theme.selectedDayColor else theme.todayIndicatorColor
            circlePaint.alpha = if (isInDisplayedPeriod) 255 else 90
            when (theme.cellShape) {
                CellShape.CIRCLE -> canvas.drawCircle(cx, markerCy, markerRadius, circlePaint)
                CellShape.ROUNDED_SQUARE -> {
                    val r = RectF(cx - markerRadius, markerCy - markerRadius, cx + markerRadius, markerCy + markerRadius)
                    canvas.drawRoundRect(r, markerRadius * 0.3f, markerRadius * 0.3f, circlePaint)
                }
            }
        }

        dayPaint.color = when {
            isSelected || isToday -> Color.WHITE
            isSaturday -> theme.saturdayTextColor
            isFriday -> theme.fridayTextColor
            else -> theme.defaultTextColor
        }
        dayPaint.alpha = if (isInDisplayedPeriod) 255 else 100

        val label = config.primaryCalendarSystem.labelFor(date)
        canvas.drawText(label.dayLabel, cx, markerCy + dayPaint.textSize * 0.35f, dayPaint)

        // Banner lanes (drawn separately, after all cells in the row) get their own dedicated,
        // growing space above the regular chip stack -- so regular chips always keep their
        // usual 2-slot budget regardless of how many multi-day events this row has.
        val bannerReserve = bannerLaneCount * bannerSlotHeight()
        drawEventAndHolidayChips(canvas, date, cellLeft, markerCy + markerRadius + bannerReserve, isInDisplayedPeriod)
    }

    private fun drawEventAndHolidayChips(canvas: Canvas, date: LocalDate, cellLeft: Float, startY: Float, isInDisplayedPeriod: Boolean) {
        val maxChips = 2
        val useHebrewNames = titleLocale.language == "iw" || titleLocale.language == "he"
        val dayHolidays = holidaysByDate[date].orEmpty().map { (if (useHebrewNames) it.hebrewName else it.name) to (it.colorHint ?: theme.holidayDotColor) }
        val dayEvents = eventsByDate[date].orEmpty().map { it.title to it.color }
        val items = dayHolidays + dayEvents
        if (items.isEmpty()) return

        val chipHeight = cellHeight * 0.16f
        val chipWidth = cellWidth * 0.92f
        val chipLeft = cellLeft + cellWidth * 0.04f
        chipTextPaint.textSize = chipHeight * 0.62f
        chipTextPaint.textAlign = Paint.Align.LEFT
        var y = startY + chipHeight * 0.35f

        items.take(maxChips).forEach { (text, color) ->
            chipBackgroundPaint.color = color
            chipBackgroundPaint.alpha = if (isInDisplayedPeriod) 220 else 90
            val rect = RectF(chipLeft, y, chipLeft + chipWidth, y + chipHeight)
            canvas.drawRoundRect(rect, chipHeight * 0.3f, chipHeight * 0.3f, chipBackgroundPaint)

            val ellipsized = TextUtils.ellipsize(text, chipTextPaint, chipWidth - chipHeight * 0.4f, TextUtils.TruncateAt.END)
            chipTextPaint.color = bestTextColorOn(color)
            canvas.drawText(ellipsized.toString(), chipLeft + chipHeight * 0.25f, y + chipHeight * 0.72f, chipTextPaint)
            y += chipHeight * 1.15f
        }

        if (items.size > maxChips) {
            overflowPaint.textSize = chipTextPaint.textSize * 0.85f
            overflowPaint.textAlign = Paint.Align.LEFT
            overflowPaint.color = theme.defaultTextColor
            canvas.drawText("+${items.size - maxChips}", chipLeft + chipHeight * 0.25f, y + chipHeight * 0.6f, overflowPaint)
        }
    }

    /** Draws one continuous bar for [span]'s portion of [rowStart]..[rowEnd], in its assigned
     *  [lane] -- rounded on whichever visual edge is the event's TRUE start/end, square on
     *  whichever edge just continues onto another (off-screen-this-row) week, so a
     *  week-spanning event reads as one uninterrupted bar across rows rather than looking like
     *  separate disconnected chips. */
    private fun drawMultiDayBanner(canvas: Canvas, span: MultiDaySpan, rowStart: LocalDate, rowEnd: LocalDate, rowTop: Float, lane: Int) {
        val segStart = maxOf(span.startDate, rowStart)
        val segEnd = minOf(span.endDate, rowEnd)
        val startCol = ChronoUnit.DAYS.between(rowStart, segStart).toInt()
        val endCol = ChronoUnit.DAYS.between(rowStart, segEnd).toInt()

        val startPixelCol = displayColumn(startCol)
        val endPixelCol = displayColumn(endCol)
        val leftPixelCol = minOf(startPixelCol, endPixelCol)
        val rightPixelCol = maxOf(startPixelCol, endPixelCol)
        val startIsOnLeft = startPixelCol <= endPixelCol

        val isTrueStart = segStart == span.startDate
        val isTrueEnd = segEnd == span.endDate
        val roundLeftEdge = if (startIsOnLeft) isTrueStart else isTrueEnd
        val roundRightEdge = if (startIsOnLeft) isTrueEnd else isTrueStart

        val markerRadius = cellWidth * 0.32f
        val bannerHeight = cellHeight * 0.16f
        val bannerTop = rowTop + markerRadius + cellHeight * 0.08f + markerRadius + lane * bannerSlotHeight()
        val bannerLeft = cellWidth * leftPixelCol + cellWidth * 0.04f
        val bannerRight = cellWidth * (rightPixelCol + 1) - cellWidth * 0.04f
        val rect = RectF(bannerLeft, bannerTop, bannerRight, bannerTop + bannerHeight)

        val leftRadius = if (roundLeftEdge) bannerHeight * 0.3f else 0f
        val rightRadius = if (roundRightEdge) bannerHeight * 0.3f else 0f
        val radii = floatArrayOf(
            leftRadius, leftRadius,
            rightRadius, rightRadius,
            rightRadius, rightRadius,
            leftRadius, leftRadius
        )

        chipBackgroundPaint.color = span.event.color
        chipBackgroundPaint.alpha = 220
        val path = Path().apply { addRoundRect(rect, radii, Path.Direction.CW) }
        canvas.drawPath(path, chipBackgroundPaint)

        // Only label the segment that contains the event's real start -- a week-spanning event
        // otherwise stays a plain colored bar on its continuation rows, rather than repeating
        // the title everywhere
        if (isTrueStart) {
            chipTextPaint.textSize = bannerHeight * 0.62f
            chipTextPaint.textAlign = Paint.Align.CENTER
            chipTextPaint.color = bestTextColorOn(span.event.color)
            val availableWidth = rect.width() - bannerHeight * 0.4f
            val ellipsized = TextUtils.ellipsize(span.event.title, chipTextPaint, availableWidth, TextUtils.TruncateAt.END)
            val centerX = (bannerLeft + bannerRight) / 2f
            canvas.drawText(ellipsized.toString(), centerX, bannerTop + bannerHeight * 0.72f, chipTextPaint)
        }
    }

    private fun bestTextColorOn(backgroundColor: Int): Int {
        val luminance = (0.299 * Color.red(backgroundColor) + 0.587 * Color.green(backgroundColor) + 0.114 * Color.blue(backgroundColor)) / 255.0
        return if (luminance > 0.6) Color.BLACK else Color.WHITE
    }

    private fun dateAt(x: Float, y: Float): LocalDate? {
        if (y < titleHeight + headerHeight) return null
        val pixelCol = (x / cellWidth).toInt().coerceIn(0, 6)
        val logicalCol = displayColumn(pixelCol)
        val (periodStart, periodEnd) = periodBounds()
        val rows = rowsNeededFor(periodStart, periodEnd)
        val gridStart = gridStartFor(periodStart)

        var rowTop = titleHeight + headerHeight
        var matchedRow = rows - 1
        for (row in 0 until rows) {
            val rowStart = gridStart.plusDays((row * 7).toLong())
            val thisRowHeight = rowHeight(rowStart, rowStart.plusDays(6))
            if (y < rowTop + thisRowHeight) {
                matchedRow = row
                break
            }
            rowTop += thisRowHeight
        }
        return gridStart.plusDays((matchedRow * 7 + logicalCol).toLong())
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        return SavedState(superState).also {
            it.displayedAnchorEpoch = displayedAnchor.toEpochDay()
            it.selectedDateEpochs = selectedDates.map { d -> d.toEpochDay() }
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            displayedAnchor = LocalDate.ofEpochDay(state.displayedAnchorEpoch)
            selectedDates.clear()
            selectedDates.addAll(state.selectedDateEpochs.map { LocalDate.ofEpochDay(it) })
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    private class SavedState : BaseSavedState {
        var displayedAnchorEpoch: Long = 0
        var selectedDateEpochs: List<Long> = emptyList()

        constructor(superState: Parcelable?) : super(superState)
        constructor(parcel: Parcel) : super(parcel) {
            displayedAnchorEpoch = parcel.readLong()
            val size = parcel.readInt()
            selectedDateEpochs = List(size) { parcel.readLong() }
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeLong(displayedAnchorEpoch)
            out.writeInt(selectedDateEpochs.size)
            selectedDateEpochs.forEach { out.writeLong(it) }
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(parcel: Parcel) = SavedState(parcel)
            override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
        }
    }
}