# Calendar

An Android calendar library with **genuine Hebrew calendar support** — not a Gregorian grid with Hebrew numbers pasted on top, but a real molad-based Hebrew↔Gregorian conversion algorithm, traditional Hebrew letter-numerals (כ״ז, not "27"), and a full Jewish + Christian holiday calendar with historically accurate deferral rules (fasts that move off Shabbat, Yom Ha'atzmaut's day-of-week shifts, and so on).

Built as a multi-module Android Studio project, on top of a from-scratch implementation of the Hebrew calendar's own arithmetic.

## Modules

| Module | What it is |
|---|---|
| `calendar-core` | Pure Kotlin, **zero Android dependency**. Date conversion math, the theming model, and the holiday calendars. Compiles and unit-tests on a plain JVM — no emulator needed. |
| `calendar-view` | The actual library — `HebrewCalendarView`, a Canvas-drawn custom Android `View`. |
| `app` | A demo "Theme Studio" app: live theme editing, calendar-system switching, event management with reminders. |

## Features

- **Real Hebrew calendar math.** Molad-based Hebrew↔Gregorian conversion, verified against Hebcal/Chabad.org reference dates across a leap year, a common year, and a Hebrew-new-year boundary — not assumed correct, checked.
- **Two calendar systems, swappable at runtime.** Gregorian or Hebrew as the primary display. Hebrew mode organizes the grid by the *real* Hebrew month (its own start/end dates, its own 29- or 30-day length) — not a Gregorian month relabeled.
- **Hebrew letter-numerals.** Days and years render as כ״ז / תשפ״ו, following standard gematria rules, including the ט״ו/ט״ז substitution that avoids spelling a name of God.
- **A full holiday calendar, not just the headline ones.** Major Yamim Tovim, all four public fasts plus Ta'anit Esther (each with its own real deferral rule — most defer to Sunday if they'd land on Shabbat, but Ta'anit Esther uniquely defers *backward* to Thursday), and the modern Israeli holidays (Yom Ha'atzmaut, Yom HaZikaron, Yom HaShoah, Yom Yerushalayim), including their day-of-week-dependent date shifts. A second, independent Christian holiday provider proves the holiday system is genuinely pluggable, not Jewish-only.
- **RTL-aware.** Mirrors Sunday-on-the-right / Saturday-on-the-left under a Hebrew locale, matching how the native OS calendar renders.
- **Per-day event management.** Add and delete events with a title, start/end time, color, and multiple reminders. Reminders fire as real Android notifications.
- **Persisted locally.** Theme, calendar-system choice, and events survive an app restart (SharedPreferences + hand-rolled JSON — no external dependency, matching the "lightweight, drop-in" design goal of `calendar-core`).
- **Fully themeable.** Every color, cell shape, and text size is configurable, live-previewed in the included Theme Studio app.

## Watch the App in Action

▶️ **Demo video:** [Click here to watch the video]()

## Validation

`calendar-core` has zero Android dependency by design, which means its date-conversion algorithm was compiled and unit-tested completely independently of Android — before the custom `View` or the app even existed. See [`docs/ALGORITHM_VALIDATION.md`](docs/ALGORITHM_VALIDATION.md) for the exact reference dates it was checked against (Hebcal, Chabad.org, OU.org, USHMM), including a 6-year round-trip test and the modern-Israeli-holiday deferral rules.

## Architecture note

Calendar math (`calendar-core`) is deliberately separated from Android UI (`calendar-view`). That split is what makes the algorithm itself independently testable, and would let `calendar-core` be reused outside Android entirely (a backend service, a Kotlin Multiplatform target) without dragging in the Android SDK.

## 📂 Project Structure

Calendar/
├── app/ # Demo "Theme Studio" app: live theme editing, calendar-system switching, event management with reminders
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/dev/yahaveliyahu/calendar/ui/theme/
│   │   │   ├── Color.kt
│   │   │   ├── Theme.kt
│   │   │   └── Type.kt
│   │   ├── kotlin/dev/yahaveliyahu/calendar/
│   │   │   ├── AppStorage.kt
│   │   │   ├── MainActivity.kt
│   │   │   ├── ReminderReceiver.kt
│   │   │   ├── ReminderScheduler.kt
│   │   │   └── ThemeStudioScreen.kt
│   │   └── res/ # drawables, mipmap, values, xml
│   ├── src/androidTest/
│   ├── src/test/
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── calendar-core/ # Pure Kotlin, zero Android dependency. Date conversion math, the theming model, and the holiday calendars.
│   ├── src/main/kotlin/dev/yahaveliyahu/calendar_core/
│   │   ├── CalendarSystem.kt
│   │   ├── CalendarTheme.kt
│   │   ├── EventSerializer.kt
│   │   ├── HebrewMath.kt
│   │   ├── HebrewMonth.kt
│   │   ├── HolidayProvider.kt
│   │   └── ThemeSerializer.kt
│   ├── src/test/kotlin/dev/yahaveliyahu/calendar_core/
│   │   └── HebrewCalendarSystemTest.kt
│   └── build.gradle.kts
├── calendar-view/ # The actual library — HebrewCalendarView, a Canvas-drawn custom Android View.
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── kotlin/dev/yahaveliyahu/calendar_view/
│   │   │   └── HebrewCalendarView.kt
│   │   └── res/values/
│   │       └── attrs.xml
│   ├── src/androidTest/
│   ├── src/test/
│   ├── build.gradle.kts
│   ├── consumer-rules.pro
│   └── proguard-rules.pro
├── gradle/
│   ├── wrapper/
│   │   ├── gradle-wrapper.jar
│   │   └── gradle-wrapper.properties
│   └── libs.versions.toml     
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
└── settings.gradle.kts

## 📸 Screenshots

| Gregorian Month | Hebrew Month | 
|---|---|
| <img src="screenshots/gregorian_calenadr_month.jpeg" width="260" alt="Recipes"> | <img src="screenshots/hebrew_calendar_month.jpeg" width="260" alt="Detail Recipe">|


