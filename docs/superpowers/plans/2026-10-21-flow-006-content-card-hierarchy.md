# FLOW-006 — Unify populated card hierarchy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for inline implementation. Use superpowers:subagent-driven-development only if delegation is explicitly selected. Steps use checkbox syntax to track implementation and verification separately.

**Goal:** Make personal content easier to scan by giving titles, body previews, metadata, and row actions stable visual roles.

**Architecture:** Align existing card sites to the shared Material title/body/metadata roles. Preserve component-specific actions and already-conforming styling, with a deliberate bodyLarge exception for task titles beside checkboxes.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose/Material 3, Android lifecycle state and existing app modules; existing dependencies.

**Spec:** [FLOW-006 specification](../specs/2026-10-21-flow-006-content-card-hierarchy-design.md)

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

- **RF-1 → FLOW-006-AC-1:** Inspect five populated entry/catalog cards. Title/body hierarchy is clear and metadata is secondary with the specified Material roles. Evidence owner: Task 1, Step 6.
- **RF-2 → FLOW-006-AC-2:** Use the existing EventCardTest compact and 130% fixtures. Its populated-card viewport expectations still hold; no extra decorative padding displaces content. Evidence owner: Task 1, Step 6.
- **RF-3 → FLOW-006-AC-3:** Activate one memory action and one task checkbox in existing fixtures. The parent card does not also navigate and callback identity remains correct. Evidence owner: Task 1, Step 6.
- **RF-4 → FLOW-006-AC-4:** Open a card whose preview is ellipsized. Detail retains the original complete text and context. Evidence owner: Task 1, Step 6.
- **RF-5 → FLOW-006-AC-5:** Inspect the existing RTL/long-label card fixture. No control overlaps metadata or loses its readable/action label. Evidence owner: Task 1, Step 6.

## Dependencies and integration

No hard prerequisite. Integrate after UX-019/UX-020 when changing EventCard call sites, and retain UX-021 type/gutter conventions.

## Task 1: Unify populated card hierarchy

**Files and responsibility:**

- Modify if nonconforming: `app/src/main/java/com/luma/lifeos/ui/timeline/EventCard.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/timeline/HabitTimelineCard.kt`
- Inspect/action-preserving style: `app/src/main/java/com/luma/lifeos/ui/todo/TodoRow.kt`
- Modify if nonconforming: `app/src/main/java/com/luma/lifeos/ui/people/PeopleRoute.kt`
- Modify if nonconforming: `app/src/main/java/com/luma/lifeos/ui/places/PlacesRoute.kt`
- Existing verification fixture: `app/src/androidTest/java/com/luma/lifeos/EventCardTest.kt`
- Existing verification fixture: `app/src/androidTest/java/com/luma/lifeos/todo/TodoAccessibilityTest.kt`

**Interfaces:**

- Consumes the existing state, callbacks, and prerequisite contracts identified in the spec.
- Produces: Preserve EventCard, HabitTimelineCard, TodoRow, PeopleRoute/PeopleListContent, and PlacesListContent callback and identity contracts. Apply the listed Material roles and spacing at the existing sites; do not introduce a generic all-purpose card API or copy EventCard into other routes. If UX-020 has landed, retain its required openActionLabel parameter at every caller.
- Does not change repository schemas, stored content, or callback target identity.

- [x] **Step 1: Identify actual presentation mismatches.** Compare the five listed card sites to the spec’s 12 dp/4 dp rhythm and role map. Preserve already-conforming sites; no generic card framework is introduced.

- [x] **Step 2: Align entry metadata.** Use the specified metadata role/color in EventCard where needed. Keep title/body two-line previews, original data in detail, all context indicators, and the UX-020 action-label contract when present.

- [x] **Step 3: Align habit timeline spacing.** Change HabitTimelineCard from 14 dp/5 dp to 12 dp/4 dp. Retain completion text, source zone, and its existing navigation callback.

- [x] **Step 4: Align catalog cards where needed.** Apply the same name/note/metadata roles to People/Places only at nonconforming sites. Keep stable IDs and current catalog/detail actions.

- [x] **Step 5: Retain task control layout.** Keep TodoRow title bodyLarge, checkbox/open-target separation, and at least 48 dp targets. Align supporting text to the title column without adding a parent click wrapper.

- [ ] **Step 6: Verify the populated card fixture.** Run the listed existing EventCardTest cases once and record AC-1–AC-5 in one populated fixture. Preserve existing compact/130%/RTL viewport behavior and independent action checks; add no tests for static spacing values.

The final verification step records a result for each linked acceptance case. An unexecuted condition stays pending; normal-flow evidence does not imply all recovery or adaptive conditions passed.

## Focused verification

Future targeted command (after the listed implementation and any assertion updates):

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.EventCardTest
```

Expected: the selected existing test cases complete with zero failures. The command is not evidence until run. Do not create a broad new test harness for spacing or copy alone.

After the integrated UI batch, run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` once to verify call sites and resources; repeat only if new changes or failures require it. Do not run the full app walkthrough again for each small visual task. Reuse the existing five-record fixtures and enter no extra demo data for this plan.

- [x] **Integration review:** Compared the source diff with the behavior contract and acceptance cases. Existing card actions and identities remain unchanged. No Gradle, instrumentation, build, or emulator verification was run at the user's request; those checks remain pending. Suggested message: `fix: flow-006 content card hierarchy`.

## Handoff

Deliver the changed behavior, focused evidence, and remaining limitations together. Implementation is recorded complete; acceptance-case runtime evidence remains pending because verification was not requested.
