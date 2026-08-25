package dev.yahaveliyahu.calendar_core

/**
 * Hand-rolled JSON for CalendarTheme -- deliberately no external JSON library dependency,
 * since CalendarTheme is a small flat data class of primitives and pulling in a full JSON
 * library for this would be overkill for a library whose whole pitch is "lightweight, drop-in".
 */
object ThemeSerializer {

    fun toJson(theme: CalendarTheme): String = buildString {
        append("{\n")
        append("  \"primaryColor\": ${theme.primaryColor},\n")
        append("  \"todayIndicatorColor\": ${theme.todayIndicatorColor},\n")
        append("  \"selectedDayColor\": ${theme.selectedDayColor},\n")
        append("  \"fridayTextColor\": ${theme.fridayTextColor},\n")
        append("  \"saturdayTextColor\": ${theme.saturdayTextColor},\n")
        append("  \"defaultTextColor\": ${theme.defaultTextColor},\n")
        append("  \"holidayDotColor\": ${theme.holidayDotColor},\n")
        append("  \"backgroundColor\": ${theme.backgroundColor},\n")
        append("  \"dayTextSizeSp\": ${theme.dayTextSizeSp},\n")
        append("  \"headerTextSizeSp\": ${theme.headerTextSizeSp},\n")
        append("  \"cellShape\": \"${theme.cellShape.name}\",\n")
        append("  \"fontFamily\": ${theme.fontFamily?.let { "\"$it\"" } ?: "null"},\n")
        append("  \"isDarkMode\": ${theme.isDarkMode}\n")
        append("}")
    }

    fun fromJson(json: String): CalendarTheme {
        val map = HashMap<String, String>()
        Regex("\"(\\w+)\"\\s*:\\s*(\"[^\"]*\"|[^,\\n}]+)").findAll(json).forEach { m ->
            map[m.groupValues[1]] = m.groupValues[2].trim().removeSurrounding("\"")
        }
        fun color(key: String, default: Int) = map[key]?.toIntOrNull() ?: default
        fun float(key: String, default: Float) = map[key]?.toFloatOrNull() ?: default
        fun bool(key: String, default: Boolean) = map[key]?.let { it == "true" } ?: default

        return CalendarTheme(
            primaryColor = color("primaryColor", CalendarTheme().primaryColor),
            todayIndicatorColor = color("todayIndicatorColor", CalendarTheme().todayIndicatorColor),
            selectedDayColor = color("selectedDayColor", CalendarTheme().selectedDayColor),
            fridayTextColor = color("fridayTextColor", CalendarTheme().fridayTextColor),
            saturdayTextColor = color("saturdayTextColor", CalendarTheme().saturdayTextColor),
            defaultTextColor = color("defaultTextColor", CalendarTheme().defaultTextColor),
            holidayDotColor = color("holidayDotColor", CalendarTheme().holidayDotColor),
            backgroundColor = color("backgroundColor", CalendarTheme().backgroundColor),
            dayTextSizeSp = float("dayTextSizeSp", CalendarTheme().dayTextSizeSp),
            headerTextSizeSp = float("headerTextSizeSp", CalendarTheme().headerTextSizeSp),
            cellShape = map["cellShape"]?.let { runCatching { CellShape.valueOf(it) }.getOrNull() } ?: CellShape.CIRCLE,
            fontFamily = map["fontFamily"]?.takeIf { it != "null" },
            isDarkMode = bool("isDarkMode", false)
        )
    }


    /** Generates paste-ready Kotlin the developer can drop straight into their code --
     *  this is what makes the Theme Studio a real dev tool instead of just a toy. */
    fun toKotlinSnippet(theme: CalendarTheme, variableName: String = "myTheme"): String = buildString {
        append("val $variableName = CalendarTheme(\n")
        append("    primaryColor = 0x${theme.primaryColor.toUInt().toString(16).uppercase(java.util.Locale.ROOT)}.toInt(),\n")
        append("    todayIndicatorColor = 0x${theme.todayIndicatorColor.toUInt().toString(16).uppercase(java.util.Locale.ROOT)}.toInt(),\n")
        append("    selectedDayColor = 0x${theme.selectedDayColor.toUInt().toString(16).uppercase(java.util.Locale.ROOT)}.toInt(),\n")
        append("    fridayTextColor = 0x${theme.fridayTextColor.toUInt().toString(16).uppercase(java.util.Locale.ROOT)}.toInt(),\n")
        append("    saturdayTextColor = 0x${theme.saturdayTextColor.toUInt().toString(16).uppercase(java.util.Locale.ROOT)}.toInt(),\n")
        append("    holidayDotColor = 0x${theme.holidayDotColor.toUInt().toString(16).uppercase(java.util.Locale.ROOT)}.toInt(),\n")
        append("    backgroundColor = 0x${theme.backgroundColor.toUInt().toString(16).uppercase(java.util.Locale.ROOT)}.toInt(),\n")
        append("    dayTextSizeSp = ${theme.dayTextSizeSp}f,\n")
        append("    cellShape = CellShape.${theme.cellShape.name},\n")
        append("    isDarkMode = ${theme.isDarkMode}\n")
        append(")")
    }
}
