# T19 Accessibility and Language Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete T19's accessibility and language slice by making primary navigation readable at large text sizes, validating TalkBack and screen-size behavior, and recording API 28/32 app-language behavior.

**Architecture:** Keep the existing `AppLanguages` preference/context design and the current primary navigation routes. Give navigation items a compact visual label where French copy needs it, while exposing the existing full localized label through Compose semantics. Reuse the UX acceptance checklist and existing language-flow instrumentation, then record only evidence from actual device runs.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android resources, Compose UI tests, AndroidJUnitRunner.

**Spec:** `docs/superpowers/specs/2026-10-17-t19-reliability-performance-accessibility-design.md` (Accessibility and language behavior)

## Global Constraints

- Use generated data in a disposable install; do not reset or overwrite existing user data.
- Test actual English, French, and Persian resource selection and Persian RTL.
- Cover 320 dp and normal phone widths, portrait and landscape, and font scales 1.0, 1.3, and 2.0.
- Verify keyboard navigation where applicable and TalkBack names, roles, states, heading traversal, status feedback, focus stability, and action reachability.
- French visible navigation labels remain readable, preserve the user's font scale, and retain each full localized accessible name.
- Exercise the app-language preference and activity-context path on API 28 and API 32.
- Make no dependency, permission, network, or product-scope changes.

## Review Focus

- A compact French label must retain the full existing localized tab name for accessibility; assert both visible text and the item content description.
- At 320 dp and font scale 2.0, labels must remain within the navigation item bounds and text must not be clipped; exercise the measured Compose layout.
- Switching between French, Persian, and English must update resource language and layout direction without losing the selected day or an active editor draft; use the existing `LanguageFlowTest` flows.
- An explicit legacy app-language choice must remain authoritative over a different device locale after activity recreation and process relaunch; run the language flow on API 28 and API 32.
- TalkBack focus and announcements must remain useful after dismissing an error, removing a filter, retrying a load, and navigating between details; record spoken output in the manual acceptance checklist.

---

### Task 1: Make primary navigation readable and fully named

**Files:**
- Modify: `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-fa/strings.xml`
- Modify: `app/src/main/res/values-fr/strings.xml`
- Create: `app/src/androidTest/java/com/luma/lifeos/ui/PrimaryNavigationAccessibilityTest.kt`

**Interfaces:**
- Consumes: Existing `Destination.label` resources, routes, selected state, and navigation callbacks.
- Produces: Each primary navigation item displays a localized visual label and exposes the existing full label as its accessible name. Route order and actions remain unchanged.

- [ ] **Step 1: Add a failing compact-layout and semantics test.** Extract the primary navigation bar to an `internal` composable that accepts the selected route, an `onNavigate(route)` callback, and a width modifier. Test it at 320 dp and font scale 2.0 with French resources selected. Assert the visible French labels are `Jour`, `Parcours`, `Contacts`, `Bilan`, and `Options`; each item has the corresponding existing full localized name (`Aujourd’hui`, `Chronologie`, `Personnes`, `Bilan`, `Paramètres`), tab role, selected state, and click action. Assert every label lays out in at most two lines without exceeding its item bounds. Add English and Persian assertions to ensure their visible labels and full names remain localized.
- [ ] **Step 2: Run the focused Compose test and confirm it fails on the current wrapping or semantic-name behavior.** Create `/tmp/luma-ux-validation.init.gradle` using the isolated application-ID snippet in `docs/UX_ACCEPTANCE_2026-10-14.md`. Run `bash gradlew -I /tmp/luma-ux-validation.init.gradle :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain`, install only the verified `com.luma.lifeos.uxvalidation` APKs, then run `adb -s emulator-5554 shell am instrument -w -r -e class com.luma.lifeos.ui.PrimaryNavigationAccessibilityTest com.luma.lifeos.uxvalidation.test/androidx.test.runner.AndroidJUnitRunner`. Expected: the test fails on the current French label layout or missing full-name semantics. If the connected task cannot resolve UTP, direct AndroidJUnitRunner is the documented fallback.
- [ ] **Step 3: Add the visual-label resources and preserve full semantics.** Keep the existing full strings as each item's content description. Add compact visible labels for French (`Jour`, `Parcours`, `Contacts`, `Bilan`, `Options`) and matching resource keys in English and Persian. Put discretionary line breaks at natural French syllable boundaries for `Parcours`, `Contacts`, and `Options`; allow up to two visible lines and preserve the configured font scale. Extract only the navigation bar needed for the constrained-layout test, and keep route restoration, tags, icons, selection, and navigation callbacks intact.
- [ ] **Step 4: Re-run the focused test at 320 dp/font scale 2.0 and verify the regression passes.** Confirm all five items retain full localized accessible names and that the visible labels fit. Also run `bash gradlew :app:testDebugUnitTest --tests 'com.luma.lifeos.language.TranslationCatalogTest' --no-daemon --console=plain` to verify the added keys exist in every bundle.
- [ ] **Step 5: Commit the isolated navigation change.**

### Task 2: Validate the legacy app-language branch

**Files:**
- Modify: `app/src/androidTest/java/com/luma/lifeos/language/LanguageFlowTest.kt` only if an API 28/32 regression is found.
- Modify: `app/src/androidTest/java/com/luma/lifeos/language/PlatformLanguageTest.kt` only if the existing launch/recreation coverage is insufficient.
- Update: `docs/UX_ACCEPTANCE_2026-10-14.md`
- Update: `docs/VALIDATION.md`
- Update: `TASKS.md` T21 validation note.

**Interfaces:**
- Consumes: `AppLanguages.select`, `AppLanguages.current`, the legacy SharedPreferences path, and the activity `attachBaseContext` wrapper.
- Produces: Recorded API 28 and API 32 results for language selection, activity recreation, process relaunch, resource language, RTL/LTR direction, selected-day preservation, and draft retention during a language change.

- [ ] **Step 1: Run the existing language instrumentation on disposable API 28 and API 32 installs.** Build isolated app and test APKs as in Task 1. On each API, run `adb -s <serial> shell am instrument -w -r -e class com.luma.lifeos.language.PlatformLanguageTest com.luma.lifeos.uxvalidation.test/androidx.test.runner.AndroidJUnitRunner` and repeat with `com.luma.lifeos.language.LanguageFlowTest`. Verify explicit French/Persian/English choices, changed layout direction, selected-day restoration, and editor draft/date retention during language changes. Expected: both classes report `OK` with no failures or assumptions. If API 28 cannot load the API 33 `LocaleManager` test class, move the guarded platform-specific test into an API 33-only class and keep the legacy test free of API 33 type references.
- [ ] **Step 2: Verify preference persistence after process stop and relaunch.** On each API level, select a language while the device locale differs, stop only the disposable package, relaunch it, and record the selected resource language and layout direction. Verify the app preference remains authoritative. Keep draft/day checks scoped to the language change and activity recreation; report their force-stop behavior separately if the platform does not restore activity state after an explicit force-stop.
- [ ] **Step 3: Add a targeted regression only for a failure found on API 28/32.** Pin the observed branch behavior in the owning language test class, then re-run that class on both legacy API levels and the JVM `AppLanguageTest` suite.
- [ ] **Step 4: Record exact device/API, app ID, locale, result, and evidence path in the acceptance checklist and validation record.** Leave the legacy check BLOCKED with the missing image/device if either API level cannot run.

### Task 3: Complete the outstanding TalkBack and screen-size acceptance

**Files:**
- Update: `docs/UX_ACCEPTANCE_2026-10-14.md`
- Modify app code and its owning test only for a concrete accessibility or layout defect found during the walkthrough.
- Update: `docs/VALIDATION.md` with the actual manual run and evidence.

**Interfaces:**
- Consumes: Existing routes, Compose semantics, generated fixtures, and the outstanding checklist rows.
- Produces: PASS, FAIL, BLOCKED, or NOT RUN results with device, API, app ID, locale, layout direction, width, orientation, font scale, keyboard/TalkBack state, and evidence path.

- [ ] **Step 1: Prepare a disposable validation install and generated fixture data.** Confirm the package ID before installation; preserve the existing AVD and installed app data. If the current ADB socket restriction persists, request the required sandbox access before device work.
- [ ] **Step 2: Run the outstanding checks whose acceptance depends on screen size, text scale, keyboard, landscape, insets, or route-control reachability.** Use the checklist's relevant steps for Timeline dense-day controls, entry read/edit routes, Review evidence, Settings navigation, mood anchors, rejected-tag recovery, Search filter layout, capture feedback/disclosures, Review comparison/source paging, and long detail routes. Cover actual English/French/Persian resources, Persian RTL, 320 dp and normal widths, portrait and landscape, and scales 1.0, 1.3, and 2.0. Record each exact result and any defect.
- [ ] **Step 3: Traverse the same applicable flows with TalkBack and the conventional keyboard.** Verify spoken names, roles, and states; headings; selected/disabled controls; error and polite status announcements; focus after removals, retries, and navigation; and reachability of actions. Confirm spoken output on device instead of inferring it from Compose semantics.
- [ ] **Step 4: Fix concrete defects within the scoped surface and add an owning regression before changing implementation.** Keep unrelated backup, reset, Health Connect, and Usage Access acceptance under T17/T18/T13. Re-run only the affected UI checks after each fix and repeat the associated manual step.
- [ ] **Step 5: Update each applicable checklist row with its result and evidence.** Preserve NOT RUN or BLOCKED for unavailable checks and restore any temporary device display, locale, keyboard, or assistive-technology settings after capture.

### Task 4: Reconcile the T19/T21 records

**Files:**
- Update: `REMAINING_TASKS.md`
- Update: `TASKS.md` T19 and T21 entries
- Update: `docs/VALIDATION.md`
- Update: `docs/UX_ACCEPTANCE_2026-10-14.md`

**Interfaces:**
- Consumes: Verified UI regressions and actual manual/device evidence from Tasks 1–3.
- Produces: Current records that distinguish completed accessibility/language checks from the still-open T19 performance work.

- [ ] **Step 1: Record implementation and evidence without claiming T19 complete.** Mark the French label behavior and completed accessibility/API checks using actual results. Keep T19 open while Timeline, Search, or Review performance acceptance remains.
- [ ] **Step 2: Review all changed records against the spec and run `git diff --check`.** Preserve the pre-existing staged APK and generated files.
- [ ] **Step 3: Commit only the accessibility/language code and acceptance records.**
