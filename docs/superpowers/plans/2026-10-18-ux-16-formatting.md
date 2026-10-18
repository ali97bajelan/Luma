# UX-16 Formatting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consistent numbers, plurals and system-selected time presentation.
**Architecture:** Extend language helpers and migrate display callers; leave persistence/calculations intact.
**Tech Stack:** Kotlin, Compose, java.time, Android DateFormat/BidiFormatter, JUnit/Compose tests.
**Spec:** [Formatting design](../specs/2026-10-18-ux-16-formatting-design.md)

## Global Constraints

- Keep minSdk 28, compileSdk 35, targetSdk 35, JDK 17, AGP 8.7.3, Kotlin 2.2.10 and Compose BOM 2025.08.01.
- Add no product dependency, database migration, permission, network access, telemetry or AI service.
- English, Persian and French remain available offline; preserve user-authored text, stored timestamps and timezone rules.
- UX-01, UX-02 and UX-12 are excluded; preserve their existing behavior.
- Minimum interactive touch target: 48 × 48 dp; do not reduce configured text scale.

## Review Focus

- Time preference changes while paused: refresh on resume (Task 1).
- Persian digits with Latin zone IDs: isolate display only (Task 1).
- Plural categories 0/1/2: choose correct localized phrase (Task 2).
- Nonzero durations below one second: retain precise display (Task 2).
- Editor configuration changes: retain timestamp and dirty state (Task 2).

### Task 1: Shared formatters and live system convention

**Files:** Modify app/src/main/java/com/luma/lifeos/language/LocalizedFormats.kt; create language/DisplayTimePreference.kt and language/BidiDisplay.kt there. Modify app/src/test/java/com/luma/lifeos/language/LocalizedFormatsTest.kt; add app/src/androidTest/java/com/luma/lifeos/language/TimePreferenceFlowTest.kt.
**Interfaces:** Produce localizedTime(is24Hour: Boolean, locale: Locale = Locale.getDefault()): DateTimeFormatter, localizedDateTime(style: FormatStyle, is24Hour: Boolean, locale: Locale = Locale.getDefault()): DateTimeFormatter, rememberIs24Hour(): Boolean, isolateDisplayFragment(value: String): String. Preserve old overloads.

- [ ] Add tests: Persian 24-hour 09:05 equals “۰۹:۰۵”; en-US 12-hour morning includes AM marker; date stays Gregorian and toString() stays “2026-10-18”. Bidi-stripped output equals the original fragment.
- [ ] Run `bash gradlew :app:testDebugUnitTest --tests '*LocalizedFormatsTest' --offline`; new overload tests must fail before implementation.
- [ ] Implement helpers. Observe TIME_12_24 and refresh on resume/configuration. Add device case timePreferenceRefreshesOnResume; restore device setting in finally.
- [ ] Rerun focused JVM test and device class with the handoff's isolated-package procedure; expect zero failures.
- [ ] Commit task files with `feat(language): centralize display time convention`.

### Task 2: Migrate display and quantity consumers

**Files:** Modify HabitHistory.kt, HabitsScreen.kt, MoodTimeControls.kt, ReviewHabits.kt, BackupSettingsCard.kt and ordinary UI formatter callers found by rg under app/src/main/java. Modify all three values*/strings.xml catalogs and TranslationCatalogTest.kt. Extend language/LanguageFlowTest.kt and habits/HabitReviewFlowTest.kt.
**Interfaces:** Consume Task 1 helpers. Produce plurals habit_repetitions and habit_scheduled_days plus localized joining strings; retain habitUnitLabel(habit: HabitDefinition, quantity: Int): String.

- [ ] Add resource assertions for 0/1/2/10 in en/fa/fr, custom/default units and unchanged names. Add RTL fragment and editor configuration-change cases. Preserve DurationDisplayTest's small positive duration assertions.
- [ ] Run targeted language/habit tests and observe new presentation failures.
- [ ] Migrate generated date/time display and TimeInput to shared system convention; isolate mixed fragments; add quantities/arguments. Preserve values and count-domain wording.
- [ ] Run `bash gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --offline`. Tests/build must pass; both known habit_unit_default ImpliedQuantity errors must disappear. Record any other lint failures honestly. Run affected language/habit device classes.
- [ ] Commit with `fix(language): localize habit quantities and timestamps`; record manual RTL/time-preference evidence.

