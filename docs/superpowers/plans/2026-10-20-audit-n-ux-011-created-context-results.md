# UX-011 — Creating a person/place during capture does not attach it Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use superpowers:subagent-driven-development only if the user selects delegation. Steps use checkbox syntax for tracking. Implementation was completed inline; no delegation or commit was performed.

**Goal:** Attach a person or place created during capture to the originating draft exactly once.

**Architecture:** Return typed catalog editor results via the calling back-stack entry's SavedStateHandle. Capture consumers attach by stable UUID; catalog consumers retain normal catalog navigation.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose/Material 3, lifecycle StateFlow and the existing Android/Room modules; existing dependencies.

**Spec:** [UX-011 specification](../specs/2026-10-20-audit-n-ux-011-created-context-results-design.md)

**Status:** Implemented / acceptance pending. Regression coverage was added but not run; Gradle, emulator and device acceptance remain pending per request. No commit was created. Relative effort: Medium.

## Global Constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies unless a task explicitly requires a reviewed exception.
- All changed user-facing copy must exist in EN/FR/FA with matching resource names and numbered placeholder signatures; preserve RTL and intended Gregorian display/storage behavior.
- Keep semantic light/dark palettes, at least 48 dp interactive targets, and reachability at 100%/130%/200% font, narrow portrait and short landscape.
- Preserve offline/private storage, existing consent boundaries, stable record/command IDs, original retry payloads and Undo protections.
- Never truncate accepted personal content, infer write failure means no commit, or treat an unavailable privacy mode as known.
- Only implement this task and its declared prerequisites; preserve unrelated working-tree changes and existing navigation/draft state.

## Review Focus

These five concrete failure modes are pinned to regression cases owned by this task. Coverage was added for idempotent selection, editor results, selection limits and capture creation; tests have not been run.

- **RF-1 → AC-1:** Create a person from Mood, Journal or Memory; deliver the result twice. The new UUID is selected exactly once and existing people remain selected. Covered by `createdPersonAttachesOnce` in the regression step below.
- **RF-2 → AC-2:** Create a place while another place is selected. The new place becomes the one selected place. Covered by `createdPlaceReplacesSelection` in the regression step below.
- **RF-3 → AC-3:** Create a person with 100 selected IDs. The catalog entry exists; no selection is dropped and the attachment limit is announced. Covered by `selectionLimitIsHonest` in the regression step below.
- **RF-4 → AC-4:** Return while dependencies are loading or the draft is restoring. The request stays pending and attaches after readiness; it is not lost or applied elsewhere. Covered by `resultWaitsForReadyCaller` in the regression step below.
- **RF-5 → AC-5:** Create from People & Places, cancel creation, or restore an old result after caller removal. No unrelated draft changes and no duplicate catalog creation occurs. Covered by `catalogAndCancelStayScoped` in the regression step below.

---

## Task 1: Deliver UX-011

**Files and responsibilities:**

- Modify: `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/people/PersonEditorViewModel.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/people/PersonEditorRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/places/PlaceEditorViewModel.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/places/PlaceEditorRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorViewModel.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/journal/JournalEditorViewModel.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/memory/MemoryEditorViewModel.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/journal/JournalEditorRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/memory/MemoryEditorRoute.kt`
- Create: `app/src/main/java/com/luma/lifeos/ui/navigation/CatalogEditorResult.kt`
- Extend test: `app/src/test/java/com/luma/lifeos/ui/people/PersonEditorTest.kt`
- Extend test: `app/src/test/java/com/luma/lifeos/ui/places/PlaceEditorTest.kt`
- Extend test: `app/src/androidTest/java/com/luma/lifeos/PeopleCaptureTest.kt`
- Extend test: `app/src/androidTest/java/com/luma/lifeos/PlaceCaptureTest.kt`
- Localized resources, only when copy changes: `app/src/main/res/values/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fr/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fa/strings.xml`

Keep changes in their current screen/state/repository responsibility. New files own only the interface or component named in the specification. If a listed existing file has changed since this plan, reconcile with the live code before editing; do not revert user changes to match the audit.

**Interfaces:**

- Consumes the current route/ViewModel/repository contracts in the files listed above; no new cross-task API is required.
- Produces: CatalogEditorResult has Created(id: UUID), Saved(id: UUID), Deleted(id: UUID). Each result channel also records PERSON or PLACE and requestId: UUID. New addPerson(id: UUID): Boolean on each capture ViewModel is idempotent; existing setPlace(id: UUID?): Unit handles places. Navigation stores primitive result fields keyed to the caller entry/request.
- Other consumers: migrate every caller affected by these interface changes; preserve callback target identity and existing readiness/busy guards.

- [x] **Step 1: Read the spec and confirm prerequisites.** Inspect each listed file and the working-tree diff; preserve unrelated edits. No implementation prerequisite is required. Classify missing tests as additions, not evidence of an already passing regression.

- [x] **Step 2: Add the behavior regressions and manual checks.** Regression coverage was added for scoped catalog publication, idempotent attachment, durable Mood draft snapshots and capture creation. The tests and device/manual checks listed here have not been run; locale, accessibility and full-flow acceptance remain pending.

| Case | Regression / evidence case | Fixture / action | Assertions |
|---|---|---|---|
| AC-1 | `createdPersonAttachesOnce` | Create a person from Mood, Journal or Memory; deliver the result twice. | The new UUID is selected exactly once and existing people remain selected. |
| AC-2 | `createdPlaceReplacesSelection` | Create a place while another place is selected. | The new place becomes the one selected place. |
| AC-3 | `selectionLimitIsHonest` | Create a person with 100 selected IDs. | The catalog entry exists; no selection is dropped and the attachment limit is announced. |
| AC-4 | `resultWaitsForReadyCaller` | Return while dependencies are loading or the draft is restoring. | The request stays pending and attaches after readiness; it is not lost or applied elsewhere. |
| AC-5 | `catalogAndCancelStayScoped` | Create from People & Places, cancel creation, or restore an old result after caller removal. | No unrelated draft changes and no duplicate catalog creation occurs. |

- [ ] **Step 3: Establish the regression.** Not run per request. The listed Gradle commands remain available for later acceptance; no failure/pass result is claimed.

```bash
./gradlew :app:testDebugUnitTest --tests 'com.luma.lifeos.ui.people.PersonEditorTest' --tests 'com.luma.lifeos.ui.places.PlaceEditorTest'
```

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.PeopleCaptureTest,com.luma.lifeos.PlaceCaptureTest
```

Device commands require a connected, configured test device/emulator. Locale/font/window and TalkBack settings are deliberate validation inputs; the command alone does not configure or certify that matrix. Do not install dependencies or alter the execution environment as part of this documentation task.

- [x] **Step 4: Implement the owning contract.** Expose created versus edited/deleted identity from both catalog editors and add request-scoped primitive result transport.

- [x] **Step 5: Connect the behavior.** Implement idempotent addPerson and ready-aware consumption in all three capture routes; persist attachment before acknowledging consumption.

- [x] **Step 6: Wire feedback and cover edge cases.** Add polite attachment/limit feedback and regression coverage for scoped publication, duplicate selection and durable draft attachment. The complete-flow exercises remain pending.

- [ ] **Step 7: Verify the task.** Not run per request. Targeted Gradle tests/build/lint and EN/FR/FA, theme, font/window and device acceptance remain pending; do not mark AC-1 through AC-5 verified.

- [ ] **Step 8: Review and integrate this task alone.** Inspect the diff against the spec and confirm every AC/RF has evidence. Stage only this task's hunks in existing modified files and its new files; do not stage the pre-existing dirty tree wholesale. If the execution workflow uses commits, commit with `fix: ux-011 created context results`. Update the index status only from actual evidence.

## Handoff

Implementation and regression coverage are present. Runtime and accessibility acceptance remain pending because Gradle/emulator/device checks were not run per request. No commit was created. Do not mark the finding Verified until the remaining checks are completed.

