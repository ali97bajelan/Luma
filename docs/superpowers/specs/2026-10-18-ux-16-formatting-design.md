# UX-16: Consistent localized formatting

Status: implementation-ready proposal; prepared 18 October 2026. Product implementation has not started under this handoff.
Source: [18 October review](../../UI_UX_REVIEW_2026-10-18.md).
Shared brief and constraints: [handoff](../../UI_UX_IMPLEMENTATION_READY_2026-10-18.md).

## Intent and approach

Make generated dates, times, counts and units consistent in English, Persian and French. Extend existing language helpers. Existing helpers shape Persian digits, but HabitHistory bypasses them and localizedTime hardcodes 24-hour time.

Copy-only plural fixes leave inconsistent timestamps; a new formatting library adds unnecessary dependencies. Extend current helpers and migrate display callers.

## Display contract

- Civil dates remain Gregorian. Persian translation does not select a Persian calendar.
- Locale comes from app configuration. Generated Persian numbers use Persian digits; English/French use their locale's digits/separators. User names, unit labels, titles and package names are never transliterated.
- Time follows Android's effective 12/24-hour preference, without a new app preference. Observe configuration/time-format changes; use an explicit Boolean in pure formatter tests.
- Ordinary occurrence times display minutes; recorded-at and backup status may retain localized MEDIUM precision. Display precision never changes persisted Instants.
- Preserve existing nonzero-duration precision and exact-duration disclosure.
- Isolate zone IDs, offsets, package names and mixed-direction fragments with Android bidi utilities at presentation boundaries; never persist bidi controls.
- Quantity selection uses the numeric quantity, then a localized numeric argument. Add separate plurals for repetitions and scheduled days; join with a localized separator.
- Fix the existing Persian/French habit_unit_default ImpliedQuantity errors with grammatical forms and quantity placeholders; update callers. Do not suppress lint.

## Interfaces and files

Modify app/src/main/java/com/luma/lifeos/language/LocalizedFormats.kt:
- Keep localizedDate(style: FormatStyle): DateTimeFormatter.
- Add localizedTime(is24Hour: Boolean, locale: Locale = Locale.getDefault()): DateTimeFormatter.
- Keep no-argument localizedTime() as a compatibility overload; migrate all ordinary UI time callers.
- Add localizedDateTime(style: FormatStyle, is24Hour: Boolean, locale: Locale = Locale.getDefault()): DateTimeFormatter using localized date plus the chosen time convention; retain old overload for compatibility.

Create language/DisplayTimePreference.kt: @Composable fun rememberIs24Hour(): Boolean. Read DateFormat.is24HourFormat(LocalContext.current); update on resume, configuration and TIME_12_24 setting changes; unregister observers.
Create language/BidiDisplay.kt: fun isolateDisplayFragment(value: String): String using BidiFormatter.
Migrate ordinary UI display callers, especially HabitHistory.kt, MoodTimeControls.kt, timeline/EventCard.kt, review/ReviewHeader.kt and backup/BackupSettingsCard.kt; enumerate usages with rg before editing. Preserve date range's explicit Gregorian policy.
Update values/strings.xml, values-fa/strings.xml, values-fr/strings.xml and language tests.

## Acceptance

- The same morning instant follows the system's 12/24-hour choice in displays and TimeInput.
- Persian 24-hour 09:05 contains ۰۹:۰۵; persisted date remains 2026-10-18.
- Quantities 0, 1, 2 and 10 use correct repetition/day phrases in all three languages.
- Mixed Persian/Latin names retain their authored characters and readable order.
- Resume after a time-format change updates display without losing selected date or draft.
- No schema, query, timezone conversion or domain validation change.

