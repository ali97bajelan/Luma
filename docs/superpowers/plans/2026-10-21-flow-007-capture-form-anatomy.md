# FLOW-007 — Standardize capture and editor form layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for inline implementation. Use superpowers:subagent-driven-development only if delegation is explicitly selected. Steps use checkbox syntax to track implementation and verification separately.

**Goal:** Give each editor a clear primary input and a predictable optional-details area while keeping Save reachable and accepted drafts safe.

**Architecture:** Reuse the validated header/inset and searchable-selector contracts. Add a caller-controlled optional-section component for Journal tags and Memory importance, then align field groups and responsive footers while preserving editor state and callbacks.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose/Material 3, Android lifecycle state and existing app modules; existing dependencies.

**Spec:** [FLOW-007 specification](../specs/2026-10-21-flow-007-capture-form-anatomy-design.md)

**Status:** Scoped implementation complete; UX-012/UX-021 integration and focused verification pending at the user's request.

## Global Constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies.
- Localize changed copy in EN/FR/FA with matching resource names and numbered placeholders; preserve RTL and Gregorian storage/display policy.
- Keep semantic light/dark colors, interactive targets of at least 48 dp, and reachable content at 100%/130%/200% text in compact portrait and short landscape.
- Preserve private offline storage, consent boundaries, stable record/command IDs, original retry targets, accepted drafts, and Undo protections.
- Preserve unrelated edits, including the existing two-row limit and 4 dp internal gap in TodayHabitsCard.kt.
- Implement only this task and its declared prerequisites; no database migration, new provider permission, or dependency upgrade is included.

## Review Focus

Each condition maps to the matching spec acceptance case. For presentation-only conditions, its owning step records visual evidence in the same fixture; meaningful state/navigation changes use the named existing regression fixtures.

- **RF-1 → FLOW-007-AC-1:** Open each ordinary editor once. The specified input order and field grouping are clear; simple required fields remain visible. Evidence owner: Task 1, Step 8.
- **RF-2 → FLOW-007-AC-2:** Enter a pending tag and use the existing Journal save/validation fixture. Collapsed/expanded state never drops the pending tag; rejected tag input expands and focuses its group. Evidence owner: Task 1, Step 8.
- **RF-3 → FLOW-007-AC-3:** Edit a memory with importance set, collapse the group, and save. The saved rating remains and the collapsed summary names it; clearing is still explicit. Evidence owner: Task 1, Step 8.
- **RF-4 → FLOW-007-AC-4:** Inspect the footer below and above the 360 dp content-width threshold with the keyboard open. Save stays reachable, actions remain at least 48 dp tall, and insets are applied once. Evidence owner: Task 1, Step 8.
- **RF-5 → FLOW-007-AC-5:** Use existing dirty/busy/marker-removal assertions after the layout change. Accepted drafts, close guards, confirmations, and source-preserving marker removal are unchanged. Evidence owner: Task 1, Step 8.

## Dependencies and integration

Requires UX-021’s header/inset contract and UX-012’s searchable context controls. Preserve UX-019 time policy and UX-023 disclosure names when integrated.

## Task 1: Standardize capture and editor form layout

**Files and responsibility:**

- Create: `app/src/main/java/com/luma/lifeos/ui/common/EditorOptionalSection.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/journal/JournalEditorScreen.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/memory/MemoryEditorScreen.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorScreen.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/todo/TodoEditorScreen.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/habits/HabitEditorRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/people/PersonEditorRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/places/PlaceEditorRoute.kt`
- Inspect/reuse: `app/src/main/java/com/luma/lifeos/ui/common/EditorTopBar.kt`
- Inspect/reuse: `app/src/main/java/com/luma/lifeos/ui/common/CaptureTimestampRow.kt`
- Inspect/reuse: `app/src/main/java/com/luma/lifeos/ui/common/DetailsDisclosure.kt`
- Modify only for changed labels: `app/src/main/res/values/strings.xml`
- Modify only for changed labels: `app/src/main/res/values-fr/strings.xml`
- Modify only for changed labels: `app/src/main/res/values-fa/strings.xml`
- Existing verification fixture: `app/src/androidTest/java/com/luma/lifeos/JournalEditorScreenTest.kt`
- Existing form-flow callsite updates: `app/src/androidTest/java/com/luma/lifeos/JournalCaptureTest.kt`
- Existing verification fixture: `app/src/androidTest/java/com/luma/lifeos/CaptureAccessibilityTest.kt`
- Existing verification fixture: `app/src/androidTest/java/com/luma/lifeos/MemoryCaptureTest.kt`
- Existing localized-flow callsite update: `app/src/androidTest/java/com/luma/lifeos/language/LanguageFlowTest.kt`

**Interfaces:**

- Consumes the existing state, callbacks, and prerequisite contracts identified in the spec.
- Produces: Preserve all seven editor callback signatures and ViewModel/draft contracts. Add @Composable internal fun EditorOptionalSection(@StringRes title: Int, tag: String, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, summary: (@Composable () -> Unit)? = null, content: @Composable () -> Unit): Unit in ui/common/EditorOptionalSection.kt. The caller owns expansion; the component exposes one subject-named toggle and expanded/collapsed state using the UX-023 copy/semantics contract. JournalEditorScreen owns saveable tagsExpanded and an initialized-once flag; initialize after state.ready from selected/pending tags, and expand before using the existing tagInputBringIntoViewRequester on rejection. MemoryEditorScreen owns equivalent importanceExpanded state initialized from draft importance only on its first ready state. Keep existing field/action tags and add journal_tags_toggle and memory_importance_toggle. Use localized plurals journal_tags_summary for the collapsed tag count and memory_importance_summary (“Importance: %1$s / 5”) for a selected rating. Required form state never lives inside the disclosure component.
- Does not change repository schemas, stored content, or callback target identity.

- [ ] **Step 1: Extend the existing pending-tag regression.** In JournalEditorScreenTest extend rejectedPendingTagKeepsEditorOpenRetainsInputAndShowsFieldError to expect the Tags section expanded with the pending value intact after rejection. Record its baseline result; no replacement draft or test harness is introduced.

- [x] **Step 2: Create the controlled optional section.** Implement EditorOptionalSection with the exact spec signature, one subject-named toggle, expanded/collapsed semantics, optional collapsed summary, and content outside the toggle semantics node.

- [x] **Step 3: Move Journal tags into the controlled group.** Add saveable ready-once expansion initialization from selected/pending tags. Keep pending text and selected IDs outside the disclosure, show the localized tag-count summary when collapsed, and set body minLines to six.

- [x] **Step 4: Keep Journal rejection visible.** Preserve the existing pending-tag flush function. On rejection expand Tags before the existing bring-into-view/focus request, retain the input/error, and keep Save from losing the draft.

- [x] **Step 5: Group Memory importance.** Initialize saveable importance expansion only once after the first ready state, use the selected-rating summary when collapsed, and retain the four-line growing description plus 1–5 tap-again-to-clear behavior.

- [x] **Step 6: Align responsive footers.** Apply the spec’s below-360 dp stacked and 360 dp-or-wider wrapping-label row layout across the seven editors. Retain Save-last prominence, single IME ownership, 48 dp actions, busy/dirty guards, and distinct Delete/remove-marker confirmations.

- [x] **Step 7a: Align remaining form groups.** Use 20 dp gutters and 12 dp group spacing while keeping Mood score/emotions, Task due date, Habit schedule/zone/start date, and simple Person/Place fields explicit. Preserve the existing field/action tags and current selector state.

- [ ] **Step 7b: Integrate prerequisites.** Adopt UX-012 searchable selectors and UX-021 header/inset ownership when those prerequisite tasks are implemented. Both remain pending; this scoped change preserves the current selectors and header/inset owner.

- [ ] **Step 8: Verify the changed form contracts.** Run the listed Journal/CaptureAccessibility suites once and record AC-1–AC-5. Use each affected form’s existing normal-save scenario once with the review fixtures; inspect both footer widths/IME in one layout fixture, and label unexecuted specialized checks pending.

The final verification step records a result for each linked acceptance case. An unexecuted condition stays pending; normal-flow evidence does not imply all recovery or adaptive conditions passed.

## Focused verification

Future targeted command (after the listed implementation and any assertion updates):

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.JournalEditorScreenTest,com.luma.lifeos.CaptureAccessibilityTest
```

Expected: the selected existing test cases complete with zero failures. The command is not evidence until run. Do not create a broad new test harness for spacing or copy alone.

After the integrated UI batch, run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` once to verify call sites and resources; repeat only if new changes or failures require it. Do not run the full app walkthrough again for each small visual task. Reuse the existing five-record fixtures and enter no extra demo data for this plan.

- [x] **Integration review:** Compared the source diff with the implemented form contracts. Existing save/delete, dirty-state, draft, timestamp, and one-owner IME behavior remain in place. UX-012/UX-021 integration is pending; no Gradle, instrumentation, build, or emulator verification was run at the user's request. Suggested message: `fix: flow-007 capture form anatomy`.

## Handoff

Deliver the changed behavior, focused evidence, and remaining limitations together. The scoped form implementation is complete. UX-012/UX-021 integration and acceptance-case runtime evidence remain pending.
