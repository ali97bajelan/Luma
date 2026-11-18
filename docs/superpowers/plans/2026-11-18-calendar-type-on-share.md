# Calendar type on share Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users choose Persian or Gregorian date formatting for one entry share without changing the app calendar preference.

**Architecture:** Add the selected `CalendarSystem` to `EntryShareOptions`, initialize it from `DisplayTimePolicy.calendarSystem`, and pass it to the share date/date-time formatters. Reuse the existing calendar system control and localized choice labels in the share preview; keep its selection within the share ViewModel state and existing edited-text confirmation flow.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, existing Luma UI components, `CalendarDateFormatter`, `DisplayTimePolicy`.

**Spec:** `docs/superpowers/specs/2026-11-18-calendar-type-on-share-design.md`

## Global Constraints

- The calendar choice affects only the current share and never writes the app calendar setting.
- Preserve the current app calendar as the initial choice.
- Keep share date/date-time timezone, locale, and 12/24-hour formatting behavior.
- Preserve share options, text edits and regeneration confirmation, privacy, validation, stale-state handling, authentication, navigation, and accessibility semantics.
- Use the warm-paper/obsidian theme, shared MaterialTheme roles and Luma sizing/spacing tokens.
- Keep English, French, Persian, RTL, System/Light/Dark and large-text support.
- Do not add or run tests unless requested.

## Review Focus

- With `includeDate` off, changing or retaining the calendar choice must not insert a date into generated text.
- With Persian selected under English, French or Persian app locale, the selected calendar must control the date system while locale controls localized output.
- Mood shares must keep the same event-local date/time zone and time format when the calendar system changes.
- A calendar change after manual text edits must use the existing regenerate confirmation before replacing text.
- A change to the app calendar preference while the share is open must retain existing stale-context behavior and reload using the then-current app calendar.

## File Structure

- Modify `app/src/main/java/com/luma/lifeos/sharing/EntrySharePolicy.kt`: add calendar system to the share options and text-formatting context.
- Modify `app/src/main/java/com/luma/lifeos/sharing/EntryShareFormatter.kt`: use the selected calendar when formatting included dates.
- Modify `app/src/main/java/com/luma/lifeos/sharing/EntryShareViewModel.kt`: initialize options from the current display calendar; preserve the existing `option` and regenerate behavior.
- Modify `app/src/main/java/com/luma/lifeos/sharing/EntryShareRoute.kt`: show the localized calendar choice alongside date inclusion and pass the selected calendar through formatting.
- Modify `app/src/main/res/values/strings.xml`, `values-fr/strings.xml`, and `values-fa/strings.xml`: add the localized share calendar type label.

---

### Task 1: Add a share-local calendar choice

**Files:**
- Modify `app/src/main/java/com/luma/lifeos/sharing/EntrySharePolicy.kt`
- Modify `app/src/main/java/com/luma/lifeos/sharing/EntryShareFormatter.kt`
- Modify `app/src/main/java/com/luma/lifeos/sharing/EntryShareViewModel.kt`
- Modify `app/src/main/java/com/luma/lifeos/sharing/EntryShareRoute.kt`
- Modify `app/src/main/res/values/strings.xml`
- Modify `app/src/main/res/values-fr/strings.xml`
- Modify `app/src/main/res/values-fa/strings.xml`

**Interfaces:**
- `EntryShareOptions.calendarSystem: CalendarSystem` carries the share-only choice.
- `EntryShareTextContext.calendarSystem: CalendarSystem` supplies the initial choice.
- `EntryShareTextContext.formatDate` and `formatMoodDateTime` accept the chosen `CalendarSystem` as a final parameter.
- `formatEntryShare(snapshot, options, context)` formats dates using `options.calendarSystem`.

- [x] Add `calendarSystem` to `EntryShareOptions`; add the active system and calendar-accepting formatter callbacks to `EntryShareTextContext`.
- [x] Update `formatEntryShare` to call the selected-system formatter only when `includeDate` is true.
- [x] Initialize ViewModel options with `EntryShareTextContext.calendarSystem`; retain `option()`'s edited-text confirmation for this new option.
- [x] Add `share_calendar_type` in English, French, and Persian resources; reuse `calendar_preference_persian` and `calendar_preference_gregorian` for choice labels.
- [x] Render `CalendarSystemControl` in the share preview when date inclusion is enabled, wired to `EntryShareOptions.calendarSystem` and the existing `model.option` flow.
- [x] In the route's formatter callbacks, use the selected calendar for date rendering while retaining `DisplayTimePolicy` locale, zone conversion and time formatting.
- [x] Review the changed Kotlin sources with Android Studio source analysis; confirm no calendar preference write was added.

### Task 2: Source review

**Files:** All files listed in Task 1.

- [x] Review date-disabled behavior, locale/calendar separation, mood time formatting, edited text confirmation, and stale app-preference behavior against the Review Focus list.
- [x] Inspect the final diff for preserved share callbacks, state, tags, and Luma UI tokens.

## Scope Note

No automated tests or build tasks are planned because verification was not requested. The implementation will retain existing validation and state behavior and receive Android Studio source analysis only.
