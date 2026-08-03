package dev.yahaveliyahu.calendar

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.yahaveliyahu.calendar_core.ChristianHolidayProvider
import dev.yahaveliyahu.calendar_core.HebrewCalendarSystem
import dev.yahaveliyahu.calendar_core.HebrewDate
import dev.yahaveliyahu.calendar_core.HebrewMonth
import dev.yahaveliyahu.calendar_core.HolidayDefinition
import dev.yahaveliyahu.calendar_core.HolidayRegistry
import dev.yahaveliyahu.calendar_core.JewishHolidayProvider
import java.time.LocalDate
import androidx.compose.runtime.mutableIntStateOf

private data class HebrewMonthOption(val month: HebrewMonth, val label: String)

/** All 13 possible Hebrew month slots. ADAR_II only really exists in leap years -- validated
 *  against the chosen year at "Show" time rather than filtered out of the list up front, since
 *  the list is built before the user has necessarily finished typing a year. */
private val HEBREW_MONTH_OPTIONS = listOf(
    HebrewMonthOption(HebrewMonth.TISHREI, "תשרי"),
    HebrewMonthOption(HebrewMonth.CHESHVAN, "חשוון"),
    HebrewMonthOption(HebrewMonth.KISLEV, "כסלו"),
    HebrewMonthOption(HebrewMonth.TEVET, "טבת"),
    HebrewMonthOption(HebrewMonth.SHEVAT, "שבט"),
    HebrewMonthOption(HebrewMonth.ADAR, "אדר א'"),
    HebrewMonthOption(HebrewMonth.ADAR_II, "אדר ב'"),
    HebrewMonthOption(HebrewMonth.NISAN, "ניסן"),
    HebrewMonthOption(HebrewMonth.IYAR, "אייר"),
    HebrewMonthOption(HebrewMonth.SIVAN, "סיוון"),
    HebrewMonthOption(HebrewMonth.TAMMUZ, "תמוז"),
    HebrewMonthOption(HebrewMonth.AV, "אב"),
    HebrewMonthOption(HebrewMonth.ELUL, "אלול")
)

private val GREGORIAN_MONTH_NAMES = listOf(
    "ינואר", "פברואר", "מרץ", "אפריל", "מאי", "יוני",
    "יולי", "אוגוסט", "ספטמבר", "אוקטובר", "נובמבר", "דצמבר"
)

private val HEBREW_YEAR_FIELD_WIDTH: Dp = 110.dp

private val GREGORIAN_MONTH_DROPDOWN_WIDTH: Dp = 120.dp

private val HEBREW_MONTH_DROPDOWN_WIDTH: Dp = 100.dp

private val COMPACT_SHOW_BUTTON_PADDING = PaddingValues(horizontal = 12.dp, vertical = 6.dp)

/** Characters a Hebrew-letters year field is allowed to contain: the Hebrew alphabet itself,
 *  plus geresh/gershayim). Everything else -- digits included -- is rejected */
private val HEBREW_YEAR_ALLOWED_CHARS: Set<Char> =
    ('\u05D0'..'\u05EA').toSet() + setOf('\'', '"', '׳', '״')

private fun filterToHebrewYearLetters(input: String): String =
    input.filter { it in HEBREW_YEAR_ALLOWED_CHARS }

/** Overwrites the Time Jump screen's remembered Gregorian and Hebrew selections with today's date.  */
fun resetLastJumpMemoryToToday(context: Context) {
    val today = LocalDate.now()
    val hebrewSystem = HebrewCalendarSystem()
    val hebrewToday = hebrewSystem.toHebrewDate(today)
    val hebrewMonthIndex = HEBREW_MONTH_OPTIONS.indexOfFirst { it.month == hebrewToday.month }.let { if (it >= 0) it else 0 }

    AppStorage.saveLastGregorianJump(context, today.monthValue - 1, today.year.toString())
    AppStorage.saveLastHebrewJump(context, hebrewMonthIndex, hebrewSystem.hebrewYearLabel(hebrewToday.year))
}

/** All Jewish + Christian holiday types, always both, regardless of the app's "holidays shown"
 *  toggles -- this picker deliberately offers the full set every time it's opened. */
private fun allHolidayDefinitions(): List<HolidayDefinition> =
    JewishHolidayProvider().definitions() + ChristianHolidayProvider().definitions()

private fun holidayRegistryWithAllProviders(): HolidayRegistry =
    HolidayRegistry().apply {
        enable(JewishHolidayProvider())
        enable(ChristianHolidayProvider())
    }

private fun nextOccurrenceOf(definition: HolidayDefinition, from: LocalDate): LocalDate? =
    holidayRegistryWithAllProviders()
        .holidaysFor(from, from.plusYears(2))
        .firstOrNull { it.providerId == definition.providerId && it.name == definition.name }
        ?.date

/** The single soonest holiday of any kind on or after [from] -- used only to pre-select a
 *  sensible default in the holiday dropdown before the user has chosen anything themselves. */
private fun nextUpcomingHoliday(from: LocalDate): HolidayDefinition? {
    val soonest = holidayRegistryWithAllProviders()
        .holidaysFor(from, from.plusYears(2))
        .firstOrNull() ?: return null
    return allHolidayDefinitions().firstOrNull { it.providerId == soonest.providerId && it.name == soonest.name }
}

/**
 * The "קפיצה בזמן" (time jump) screen: three always-visible sections -- jump to a Gregorian
 * month/year, jump to a Hebrew month/year, and jump to the next occurrence of any Jewish or
 * Christian holiday -- each with its own independent "הצג" button. Laid out right-to-left to
 * match how the rest of the dialog's Hebrew text reads. Each section resolves to a plain
 * [LocalDate] anchor and hands it to [onJumpToDate] along with which calendar system that
 * particular jump was expressed in -- `true` for the Hebrew section, `false` for the Gregorian
 * one, and `null` for the holiday section (which isn't tied to either). The caller is expected
 * to switch [dev.yahaveliyahu.calendar_view.HebrewCalendarView]'s primary display to match
 * (when non-null) and then call `scrollToPeriod` with the date.
 */
@Composable
fun TimeJumpDialog(
    initialAnchor: LocalDate,
    onDismiss: () -> Unit,
    onJumpToDate: (date: LocalDate, switchToHebrewPrimary: Boolean?) -> Unit
) {
    val hebrewSystem = remember { HebrewCalendarSystem() }
    val context = LocalContext.current

    var selectedGregorianMonthIndex by remember {
        val remembered = AppStorage.loadLastGregorianJump(context)
        mutableIntStateOf(remembered?.first ?: (initialAnchor.monthValue - 1))
    }
    var gregorianYearText by remember {
        val remembered = AppStorage.loadLastGregorianJump(context)
        mutableStateOf(remembered?.second ?: initialAnchor.year.toString())
    }
    var gregorianError by remember { mutableStateOf<String?>(null) }

    val initialHebrewDate = remember(initialAnchor) { hebrewSystem.toHebrewDate(initialAnchor) }
    var selectedHebrewMonthIndex by remember {
        val remembered = AppStorage.loadLastHebrewJump(context)
        val fallbackIdx = HEBREW_MONTH_OPTIONS.indexOfFirst { it.month == initialHebrewDate.month }
        mutableIntStateOf(remembered?.first ?: (if (fallbackIdx >= 0) fallbackIdx else 0))
    }
    var hebrewYearText by remember {
        val remembered = AppStorage.loadLastHebrewJump(context)
        mutableStateOf(remembered?.second ?: hebrewSystem.hebrewYearLabel(initialHebrewDate.year))
    }
    var hebrewError by remember { mutableStateOf<String?>(null) }

    val holidayDefinitions = remember { allHolidayDefinitions() }
    var selectedHolidayIndex by remember {
        val default = nextUpcomingHoliday(LocalDate.now())
        val idx = holidayDefinitions.indexOfFirst { it.providerId == default?.providerId && it.name == default.name }
        mutableIntStateOf(if (idx >= 0) idx else 0)
    }

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Text("קפיצה בזמן", style = MaterialTheme.typography.titleLarge)

                    MonthYearJumpRow(
                        title = "עבור לחודש לועזי",
                        months = GREGORIAN_MONTH_NAMES,
                        selectedMonthIndex = selectedGregorianMonthIndex,
                        onMonthSelected = { selectedGregorianMonthIndex = it },
                        yearText = gregorianYearText,
                        onYearChange = { gregorianYearText = it.filter(Char::isDigit); gregorianError = null },
                        yearKeyboardType = KeyboardType.Number,
                        error = gregorianError,
                        monthDropdownModifier = Modifier.width(GREGORIAN_MONTH_DROPDOWN_WIDTH),
                        onShow = {
                            val year = gregorianYearText.toIntOrNull()
                            if (year == null || year < 1) {
                                gregorianError = "נא להזין שנה תקינה"
                            } else {
                                AppStorage.saveLastGregorianJump(context, selectedGregorianMonthIndex, gregorianYearText)
                                onJumpToDate(LocalDate.of(year, selectedGregorianMonthIndex + 1, 1), false)
                                onDismiss()
                            }
                        }
                    )

                    MonthYearJumpRow(
                        title = "עבור לחודש עברי",
                        months = HEBREW_MONTH_OPTIONS.map { it.label },
                        selectedMonthIndex = selectedHebrewMonthIndex,
                        onMonthSelected = { selectedHebrewMonthIndex = it },
                        yearText = hebrewYearText,
                        onYearChange = { hebrewYearText = filterToHebrewYearLetters(it); hebrewError = null },
                        yearKeyboardType = KeyboardType.Text,
                        error = hebrewError,
                        yearFieldModifier = Modifier.width(HEBREW_YEAR_FIELD_WIDTH),
                        monthDropdownModifier = Modifier.width(HEBREW_MONTH_DROPDOWN_WIDTH),
                        onShow = {
                            val year = hebrewSystem.parseHebrewYearLetters(hebrewYearText)
                            val chosenMonth = HEBREW_MONTH_OPTIONS[selectedHebrewMonthIndex].month
                            when {
                                year == null -> hebrewError = "נא להזין שנה תקינה באותיות עבריות"
                                chosenMonth == HebrewMonth.ADAR_II && !hebrewSystem.isLeapYear(year) ->
                                    hebrewError = "השנה שהוזנה אינה מעוברת, ואין בה אדר ב'"
                                else -> {
                                    AppStorage.saveLastHebrewJump(context, selectedHebrewMonthIndex, hebrewYearText)
                                    onJumpToDate(hebrewSystem.toGregorian(HebrewDate(year, chosenMonth, 1)), true)
                                    onDismiss()
                                }
                            }
                        }
                    )

                    HorizontalDivider()

                    HolidayJumpRow(
                        title = "קפיצה לחג הבא",
                        holidayNames = holidayDefinitions.map { it.hebrewName },
                        selectedIndex = selectedHolidayIndex,
                        onSelect = { selectedHolidayIndex = it },
                        onShow = {
                            val definition = holidayDefinitions.getOrNull(selectedHolidayIndex)
                            val date = definition?.let { nextOccurrenceOf(it, LocalDate.now()) }
                            if (date != null) {
                                onJumpToDate(date, null)
                                onDismiss()
                            }
                        }
                    )

                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text("סגור")
                    }
                }
            }
        }
    }
}

/** One "jump to month & year" section: title above, then a single row with the month dropdown,
 *  the year field, and its own "הצג" button -- composed in that order so that under the dialog's
 *  right-to-left layout direction the dropdown lands on the right (read first), the year field
 *  in the middle, and the button on the left (acted on last), matching the reference layout.
 *
 *  [yearFieldModifier] and [monthDropdownModifier] both default to `null`, meaning "share space
 *  evenly via weight(1f)" -- the original behavior. Neither can default directly to
 *  `Modifier.weight(1f)` in the function signature because `weight` is a `RowScope` extension
 *  function; it only resolves inside a `Row { }` lambda, not in a default-parameter expression
 *  evaluated outside any Row. So the fallback to `Modifier.weight(1f)` happens below, inside the
 *  `Row`, where `RowScope` is actually available. The Gregorian row overrides only
 *  [monthDropdownModifier] the Hebrew row overrides both, since its year field already uses a fixed width of
 *  its own.  */
@Composable
private fun MonthYearJumpRow(
    title: String,
    months: List<String>,
    selectedMonthIndex: Int,
    onMonthSelected: (Int) -> Unit,
    yearText: String,
    onYearChange: (String) -> Unit,
    yearKeyboardType: KeyboardType,
    error: String?,
    onShow: () -> Unit,
    yearFieldModifier: Modifier? = null,
    monthDropdownModifier: Modifier? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InlineDropdownField(
                selectedText = months[selectedMonthIndex],
                options = months,
                onSelect = onMonthSelected,
                modifier = monthDropdownModifier ?: Modifier.weight(1f)
            )
            OutlinedTextField(
                value = yearText,
                onValueChange = onYearChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = yearKeyboardType),
                isError = error != null,
                modifier = yearFieldModifier ?: Modifier.weight(1f)
            )
            Button(onClick = onShow, contentPadding = COMPACT_SHOW_BUTTON_PADDING) {
                Text("הצג", maxLines = 1)
            }
        }
        error?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The "jump to the next holiday" section: title above, then a row with the holiday dropdown
 *  and its own "הצג" button, same right-to-left ordering rationale as [MonthYearJumpRow]. */
@Composable
private fun HolidayJumpRow(
    title: String,
    holidayNames: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onShow: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            InlineDropdownField(
                selectedText = holidayNames.getOrElse(selectedIndex) { "" },
                options = holidayNames,
                onSelect = onSelect,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onShow) { Text("הצג") }
        }
    }
}

@Composable
private fun InlineDropdownField(
    selectedText: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selectedText, maxLines = 1)
                Text("▾")
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(index); expanded = false }
                )
            }
        }
    }
}