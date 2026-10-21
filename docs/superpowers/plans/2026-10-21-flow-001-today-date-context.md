# FLOW-001 — Make Today’s selected-date context explicit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for inline implementation. Use superpowers:subagent-driven-development only if delegation is explicitly selected. Steps use checkbox syntax to track implementation and verification separately.

**Goal:** Help a returning user distinguish the current day from an intentionally restored historical or future day without losing their browsing selection.

**Architecture:** Keep the existing nullable shared selected-date policy and current-day observer. Add a small notice in TodayScreen using TodayRoute’s existing deviceDate; the notice does not mutate selection or capture timestamps.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose/Material 3, Android lifecycle state and existing app modules; existing dependencies.

**Spec:** [FLOW-001 specification](../specs/2026-10-21-flow-001-today-date-context-design.md)

**Status:** Implementation complete; focused tests, build, and device verification pending at the user's request.

## Global Constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies.
- Localize changed copy in EN/FR/FA with matching resource names and numbered placeholders; preserve RTL and Gregorian storage/display policy.
- Keep semantic light/dark colors, interactive targets of at least 48 dp, and reachable content at 100%/130%/200% text in compact portrait and short landscape.
- Preserve private offline storage, consent boundaries, stable record/command IDs, original retry targets, accepted drafts, and Undo protections.
- Preserve unrelated edits, including the existing two-row limit and 4 dp internal gap in TodayHabitsCard.kt.
- Implement only this task and its declared prerequisites; no database migration, new provider permission, or dependency upgrade is included.

## Review Focus

Each condition maps to the matching spec acceptance case. For presentation-only conditions, its owning step records visual evidence in the same fixture; meaningful state/navigation changes use the named existing regression fixtures.

- **RF-1 → FLOW-001-AC-1:** Launch with no saved selection. Current local date is selected and no historical notice appears. Evidence owner: Task 1, Step 6.
- **RF-2 → FLOW-001-AC-2:** Restore an explicit earlier date and switch tabs back to Today. The date is retained and the visible notice names it. Evidence owner: Task 1, Step 6.
- **RF-3 → FLOW-001-AC-3:** Activate the existing Today action while viewing a prior day. Selection becomes following-current-day and the notice disappears. Evidence owner: Task 1, Step 6.
- **RF-4 → FLOW-001-AC-4:** Use Journal from a selected prior day. The existing selected-date/now choice still precedes capture; the notice makes no timestamp mutation. Evidence owner: Task 1, Step 6.
- **RF-5 → FLOW-001-AC-5:** Refresh the device day/zone while an explicit selection remains. Current-day comparison refreshes; explicit selection and habit tracking-zone semantics remain intact. Evidence owner: Task 1, Step 6.

## Dependencies and integration

None. UX-021 and UX-022 must retain this notice during later header migration.

## Task 1: Make Today’s selected-date context explicit

**Files and responsibility:**

- Create: `app/src/main/java/com/luma/lifeos/ui/today/TodayDateNotice.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/today/TodayScreen.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/today/TodayRoute.kt`
- Inspect; preserve policy: `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt`
- Inspect; preserve refresh: `app/src/main/java/com/luma/lifeos/ui/today/CurrentDay.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-fr/strings.xml`
- Modify: `app/src/main/res/values-fa/strings.xml`
- Existing verification fixture: `app/src/androidTest/java/com/luma/lifeos/TodayPersistenceTest.kt`
- Existing verification fixture: `app/src/androidTest/java/com/luma/lifeos/TodayScreenTest.kt`
- Existing direct-call migration: `app/src/androidTest/java/com/luma/lifeos/todo/TodayTasksSemanticsTest.kt`

**Interfaces:**

- Consumes the existing state, callbacks, and prerequisite contracts identified in the spec.
- Produces: Add @Composable internal fun TodayDateNotice(selectedDate: LocalDate, currentDate: LocalDate, modifier: Modifier = Modifier): Unit in ui/today/TodayDateNotice.kt. Add required deviceDate: LocalDate to TodayScreen and pass TodayRoute’s existing deviceDate at both call sites. Retain LifeOsApp selectedDate and all date mutation callbacks. Resource today_viewing_date has one %1$s date argument. Keep day_today/day_selected tags and add today_date_notice.
- Does not change repository schemas, stored content, or callback target identity.

- [ ] **Step 1: Extend the existing date assertions.** In TodayPersistenceTest/TodayScreenTest add the proposed currentDateHasNoNotice, restoredSelectionIsExplicit, and todayClearsSelection cases. Assert selected date, notice content, and the Today callback using the existing date fixtures; record the baseline result before changing rendering.

- [x] **Step 2: Add localized notice copy.** Add today_viewing_date with one numbered date placeholder in EN/FR/FA; use the existing localized full Gregorian-date formatter.

- [x] **Step 3: Create TodayDateNotice.** Implement the exact spec signature in TodayDateNotice.kt. Render no node when the dates match and one visible today_date_notice node when they differ; use the semantic tonal/text colors.

- [x] **Step 4: Thread current date to the screen.** Add the required deviceDate parameter to TodayScreen, pass the existing TodayRoute value in both branches, and migrate all direct screen test callers found by rg. Keep LifeOsApp selectedDate and rememberCurrentDay unchanged.

- [x] **Step 5: Place the notice in the date header.** Insert it immediately after DayNavigation, keeping the existing Today, previous/next, and date-picker actions. Preserve the selected-date-versus-now capture handler.

- [ ] **Step 6: Verify the focused date path.** Run the listed existing Today suites once and record AC-1–AC-5 results. Visit one prior date and activate Today once; use the same fixture for resume/date-context checks and record any specialized unexecuted case as pending.

The final verification step records a result for each linked acceptance case. An unexecuted condition stays pending; normal-flow evidence does not imply all recovery or adaptive conditions passed.

## Focused verification

Future targeted command (after the listed implementation and any assertion updates):

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.TodayPersistenceTest,com.luma.lifeos.TodayScreenTest
```

Expected: the selected existing test cases complete with zero failures. The command is not evidence until run. Do not create a broad new test harness for spacing or copy alone.

After the integrated UI batch, run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` once to verify call sites and resources; repeat only if new changes or failures require it. Do not run the full app walkthrough again for each small visual task. Reuse the existing five-record fixtures and enter no extra demo data for this plan.

- [x] **Integration review:** Compared the source diff with the behavior contract and acceptance cases. The existing selected-date policy and capture callback remain unchanged. No Gradle, instrumentation, build, or emulator verification was run at the user's request; those checks remain pending. Suggested message: `fix: flow-001 today date context`.

## Handoff

Deliver the changed behavior, focused evidence, and remaining limitations together. Implementation is recorded complete; acceptance-case runtime evidence remains pending because verification was not requested.
