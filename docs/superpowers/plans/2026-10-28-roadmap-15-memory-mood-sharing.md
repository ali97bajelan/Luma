# F15 — Memory and mood sharing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use delegation only if separately authorized. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Share one saved memory or mood as reviewed, editable plain text through Android's system share sheet.

**Architecture:** Reuse `TimelineRepository.get` for immutable saved-entry snapshots; keep sharing policy, formatting, state and the Android launcher under the app's sharing package. Entry routes pass only an event ID/mode to one preview flow. No new persistence or file export subsystem.

**Tech Stack:** Existing Kotlin, coroutines/StateFlow, Compose, ViewModel, resource localization, `DisplayTimePolicy`, Android ACTION_SEND/createChooser; no new dependency expected.

**Spec:** [Memory and mood sharing specification](../specs/2026-10-28-roadmap-15-memory-mood-sharing.md)

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [QA scenarios](../../feature-roadmap/15-memory-mood-sharing-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Global constraints

- Exactly two phases: implementation, then QA. During implementation: no Gradle/sync/resolution/build/lint/schema generation, test authoring/execution or emulator/device use.
- V1 shares exactly one saved eligible memory/mood as `text/plain`; image cards, photos, bulk selection, file exports and automatic sharing are deferred.
- Defaults: date/note on; people/place/energy/stress/source content off. Each new session starts from defaults. Importance, structured tags, addresses/coordinates and internal metadata are excluded.
- Final text must be nonblank and at most 65,536 UTF-8 bytes; never silently truncate. Mood shares include the localized occurrence time with the date; memory shares use the date only. Generated text ends with an editable `#Luma` paragraph. No unpreviewed fields/branding or sent/delivered claims.
- Preserve user edits/data, drafts, revision checks, navigation, photo ownership, EN/FR/FA/RTL/themes and supported platform baseline under WORKFLOW.md. No schema/archive version change, permission or dependency change expected.
- F15 is independent of F12. Coordinate F06 lock integration if present; do not invent a dependency on an unimplemented privacy API.

## Review focus

- A mood/journal marked as memory must not leak its original content by default: pin both source modes and same-note deduplication in QA01/QA02/Q01.
- Visible unsaved editor changes and pending imports must not be replaced by older saved text: pin clean/dirty eligibility and draft preservation in QA01/Q01.
- Linked-name changes, marker removal and locale/zone changes can bypass timestamp-only freshness checks: compare the complete graph/context and pin invalidation in QA04/Q01.
- Unicode byte size differs from character count, and long journal source content can exceed transport bounds: pin exact/over-bound and editable recovery in QA03/Q01.
- Chooser cancellation/recreation is not successful delivery or authorization to relaunch: pin consumed launches, rapid taps, process death and retry in QA05–QA07/Q01.

## File map and interfaces

Existing source anchors (modify only where required):

- `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt`: register request-ID/mode route and pass share callbacks to existing entry routes.
- `app/src/main/java/com/luma/lifeos/ui/common/DetailTopBar.kt`: optional accessible share action preserving existing callers; use overflow if actions would crowd the header.
- `app/src/main/java/com/luma/lifeos/ui/timeline/EventDetailRoute.kt`: loaded mood/memory share eligibility and request mode.
- `app/src/main/java/com/luma/lifeos/ui/journal/JournalDetailRoute.kt`: Share only when the loaded journal has a memory marker.
- `app/src/main/java/com/luma/lifeos/ui/memory/MemoryEditorRoute.kt`, `MemoryEditorScreen.kt`, `MemoryEditorViewModel.kt` in that directory: clean saved MEMORY entry point, busy/import gating and existing draft preservation.
- `app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorRoute.kt`, `MoodEditorScreen.kt`, `MoodEditorViewModel.kt` in that directory: clean saved MOOD entry point and draft gating.
- `app/src/main/java/com/luma/lifeos/language/DisplayTimePolicy.kt`, `app/src/main/java/com/luma/lifeos/ui/mood/MoodUi.kt`: consume current date/score mappings; avoid duplicate policies.
- `core/domain/src/main/kotlin/com/luma/lifeos/domain/repository/TimelineRepository.kt`, `core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomTimelineRepository.kt`: review committed saved-graph semantics; no new repository API expected.
- `app/src/main/res/values/strings.xml`, `values-fr/strings.xml`, `values-fa/strings.xml`: add preview, option, error, privacy and accessibility copy.

New production paths under `app/src/main/java/com/luma/lifeos/sharing/`:

| File | Responsibility / interfaces |
| --- | --- |
| `EntrySharePolicy.kt` | Request/kind/options/snapshot/context/error types; `loadEntryShareSnapshot(repository, request, zone)` and eligibility checks from the spec |
| `EntryShareFormatter.kt` | `formatEntryShare(snapshot, options, context): String`; `validateEntryShareText(text): EntryShareTextError?` |
| `EntryShareViewModel.kt` | State machine, immutable snapshot, regeneration, freshness checks, explicit one-shot launch consumption; depends on repository and injected context/lock state |
| `EntryShareRoute.kt` | Route/ViewModel wiring, current localized context, lifecycle and launch consumption; receives ID/mode only |
| `EntryShareScreen.kt` | Scrollable options, outgoing editable text, confirmation/error/refresh states and accessible actions |
| `AndroidTextShareLauncher.kt` | Exact text/plain ACTION_SEND chooser and `TextShareLaunchResult.OPENED/UNAVAILABLE`; never reports delivery |

Use the exact contract names/signatures in the spec; optional implementation helpers remain local. Reconcile this file map with the actual baseline before creating equivalents. Read current F06 integration before connecting its gate. Deep-copy graph collections used by a snapshot; compare graph equality and frozen display context, not only event.updatedAt, before launch.

QA-owned paths, **create/extend only in phase 2**:

- `app/src/test/java/com/luma/lifeos/sharing/EntryShareFormatterTest.kt`
- `app/src/test/java/com/luma/lifeos/sharing/EntryShareViewModelTest.kt`
- `app/src/androidTest/java/com/luma/lifeos/sharing/EntryShareFlowTest.kt`
- `app/src/androidTest/java/com/luma/lifeos/sharing/EntryShareIntentTest.kt`
- A QA-only isolated receiver fixture under `app/src/androidTest/` if needed to inspect actual payloads; it must not become a production share destination. Resolve fixture packaging in QA and record it.

## Phase 1 — Implementation

Status: **SOURCE COMPLETE — QA PENDING**. All source reviews refer to F15-R01–R05 and the review-focus list. No tests are authored/run here.

### F15-I01 — Saved-entry policy and deterministic text

**Produces:** Policy/snapshot/context types and formatter/validator consumed by I02–I04.

- [x] Implement spec contracts in `EntrySharePolicy.kt`; reuse `TimelineRepository.get`, validate kind/type/typed-details eligibility and freeze copied saved graph plus zone.
- [x] Implement `formatEntryShare` with exact defaults, source/marker separation, optional linked names, mood labels, date policy, section ordering and duplicate-note suppression. Output title/date, blank line, emotions, note, source, people, place, energy and stress where applicable; source mood energy/stress belong inside its labeled section. Omit absent sections and end generated text with `#Luma`.
- [x] Implement nonblank/65,536-byte UTF-8 validation independently of formatting; retain full oversize text for editing. Review all branches against QA01–QA03; no truncation, generated private metadata or repository writes.

### F15-I02 — Preview, edits and freshness state

**Consumes:** I01 snapshot/formatter/validator. **Produces:** Preview state/actions and consumed launch requests for I03/I04.

- [x] Implement `EntryShareViewModel` load/retry/unavailable/ready states, per-session options and editable outgoing text. Stage option changes while text is manually edited; apply/regenerate only on explicit confirmation, or cancel without changing options/text.
- [x] Add final validation, current graph/context/lock recheck, stale refresh and graph deletion/ineligibility handling. Failed load/launch preserves a usable retry path; refresh resets edits/options only after the user selects Refresh.
- [x] Define one-shot launch consumption: rapid taps cannot queue duplicate launches, return/cancel allows a new explicit tap and recreation never replays. Keep text/snapshots in memory only; process restart reloads request ID/mode with defaults. Lock activation clears private content and blocks launch until a fresh authenticated preview.
- [x] Source-review QA04–QA07 paths, read/write call sites and coroutine/lifecycle cancellation; do not swallow coroutine cancellation as a load error.

### F15-I03 — Android handoff adapter

**Consumes:** I02's explicitly confirmed, validated text. **Produces:** OPENED/UNAVAILABLE launch outcome.

- [x] Implement `AndroidTextShareLauncher.launch(text)` using ACTION_SEND, `text/plain`, exact EXTRA_TEXT and `Intent.createChooser`; set no explicit destination, recipients, subject, stream or URI permissions.
- [x] Catch expected activity/security/transaction launch failures without private-text logs; retain preview for retry. OPENED means only that the chooser was started. Avoid broad installed-app queries and app-specific SDKs.
- [x] Source-review intent construction and failure/return paths against QA05/QA06; no manifest/file-provider/storage permission changes are expected for text-only sharing.

### F15-I04 — Reachable localized preview and entry actions

**Consumes:** I01–I03. **Produces:** Connected saved-entry Share → preview → chooser flow.

- [x] Wire `entry_share/{eventId}/{kind}` (or equivalent consistent route) in `LifeOsApp.kt`; parse/validate kind/id and load by ID rather than copying content into navigation. Direct invalid requests show unavailable; they do not bypass the lock gate.
- [x] Add eligible detail actions and saved-editor actions from R01. In editors derive clean state from the existing draft/original comparison, including associations/photos/time; disable during any dirty/busy/import/completion state with save-first guidance. Do not add a new save/discard side effect.
- [x] Build `EntryShareRoute.kt`/`EntryShareScreen.kt`, all R05 states, options, full editable preview and explicit regenerate/refresh/share controls. Keep the final action visible above IME/insets and use existing adaptive/read-pane conventions.
- [x] Add EN/FR/FA resources and accessibility labels/state announcements. Show free-text-name and photo-exclusion explanations; do not advertise universal app support or delivery. Review all entry paths and preview state branches against QA01–QA08.

### F15-I05 — Source review and handoff

- [x] Review touched source/resources and every request/launch call site against R01–R05. Confirm no event/draft/catalog/photo writes, schema/archive changes, permissions, dependencies or F12 scope changes.
- [x] Run only source checks: `git diff --check`, local-link/file checks and XML parsing for changed resource files. These do not establish compilation/runtime correctness.
- [x] Record source SHA, dirty patch hash, touched paths, defaults/implementation decisions, source-only check outcomes, requirement-to-QA mapping and uncertainties. Leave all QA outcomes NOT RUN.
- [x] Mark **SOURCE COMPLETE — QA PENDING** only after I01–I04 and this handoff are complete. Documentation creation does not satisfy the source handoff.

### Implementation handoff — 28 October 2026

- Base Git SHA: `f6b5a118b5ed243e57cc357b67f0245c81cf1572`. The workspace contained pre-existing build/release and roadmap changes; they were preserved.
- Source dirty patch SHA-256: `fe5cb2718e19a53cd564c73f55ae9bfacc7871222799c8de9a60cf39a3b446c3`. Hash input: lexically sorted paths of the five `sharing/*.kt` files, `LifeOsApp.kt`, the event and journal detail routes, memory and mood editor routes/screens, and the three `values*/strings.xml` files. For each path hash UTF-8 path + NUL + `git diff --binary HEAD -- <path>` for tracked files (complete file bytes for untracked files) + NUL. Documentation and unrelated dirty files are excluded.
- File-map reconciliation: the preview screen and route are implemented together in `EntryShareRoute.kt`; no separate `EntryShareScreen.kt` was created. Source review was performed by the implementer, without an independent reviewer.
- The preview uses an in-memory ViewModel and a single navigation request containing ID and kind. Defaults are date/note on and all other fields off; marker source content requires opt-in. A refresh resets the session. The source comparison uses the transactional `LifeEventDao.getDetails` graph and display locale/zone. No F06 app-lock gate exists in this baseline. Built-in emotion labels use the existing localized vocabulary; custom names remain unchanged.
- Source-only checks: `git diff --check` passed; changed XML parsed and had no duplicate string names; local documentation links resolved; new sharing files had no trailing whitespace. These checks establish neither compilation nor behavior. No Gradle, tests, lint, emulator or device actions were run in this implementation phase.
- R01 maps to QA01/04/08; R02 to QA01/02/04/07; R03 to QA02/03/06/08; R04 to QA04/05/06/07; R05 to QA05/06/08. All remain NOT RUN. In particular, compilation, process recreation, fast taps, native chooser behavior, accessibility, large-font layouts, translations and receiver compatibility remain unverified until QA.

## Phase 2 — QA

Initial status: **NOT RUN**. Begin only after I01–I05 handoff and verification of an isolated application/test package and disposable fixture data, as required by WORKFLOW.md.

### F15-Q01 — Author fixtures and automated assertions

- [ ] Prepare the independent fixture and expected payloads in the [QA document](../../feature-roadmap/15-memory-mood-sharing-qa.md). Create the QA-owned suites above using existing harnesses; mock/fake receivers do not certify Telegram/Messages behavior.
- [ ] In `EntryShareFormatterTest`, pin `directMemoryDefaultsExcludeMetadata`, `moodDefaultsAndOptionalScales`, `markedJournalDoesNotCopySourceByDefault`, `markedMoodUsesRequestedMode`, `identicalNotesAppearOnce`, `unicodeNewlinesAndMissingFields`, and `utf8LimitAndBlankText`. Assert the generated `#Luma` ending and literal output/fields independently of production helpers; include 65,536 accepted / 65,537 rejected bytes and multibyte near-boundary strings.
- [ ] In `EntryShareViewModelTest`, pin `newSessionResetsOptIns`, `optionChangeRequiresRegenerationConfirmation`, `editedTextDoesNotWriteRecord`, `linkedNameChangeInvalidatesPreview`, `markerRemovalAndDeletionBlockLaunch`, `localeZoneChangeRequiresRefresh`, `rapidTapLaunchIsConsumedOnce`, `retryRequiresExplicitTap`, `lockClearsPrivateSession`, and `processRestartDoesNotRestoreTextOrLaunch`.
- [ ] In flow/intent suites, assert dirty/import/new-editor gating, navigation/draft preservation, exact outbound action/type/text with absent extras/grants, rotation behavior, injected launch failures and UI semantics. Use a real saved-graph fixture for freshness; record unchanged record/update/link/photo hashes before and after sharing/cancellation.
- [ ] For defects, capture the failing regression where feasible, correct production code here and rerun owning plus affected integration checks. Do not relax expectations to fit a defect.

### F15-Q02 — Build/lint and affected regression gates

Only during QA, use the repository wrapper and actual isolated variant per the shared workflow. Baseline templates:

```sh
bash gradlew :core:data:testDebugUnitTest :app:testDebugUnitTest
bash gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

- [ ] Record actual commands/toolchain/exit codes and JUnit/lint outputs: exit 0, intended tests executed with no failures/errors, skips explained, no lint errors/fatals and new warnings classified. Do not assume the template debug package is isolated.
- [ ] Run the new sharing instrumented suites and affected existing mood/memory/editor/detail/navigation/photo regressions on the isolated target, using shared connected-test commands adjusted to actual class names/variant/device. Document exact selected coverage.
- [ ] No schema/archive change is expected. If source introduced one, revise scope/handoff and apply the shared populated-upgrade/archive gates before acceptance; do not silently skip them.

### F15-Q03 — Native/manual scenarios and acceptance

- [ ] Execute F15-QA01–QA08 and required locale/theme/window/API/TalkBack/keyboard matrix cells. Exercise actual Android chooser, Telegram and Google Messages compose screens without sending messages or using personal accounts/data.
- [ ] Capture real app versions and payload observations; mark missing destination prerequisites BLOCKED, never PASS via a fake receiver. Test receiving-app text limitations and distinguish Luma payload correctness from destination behavior.
- [ ] Update the scenario ledger with evidence links, defects/retests and exact candidate identity. Fix and rerun affected cases; old runtime evidence invalidated by source changes must be identified.
- [ ] Mark feature complete only when all required assertions/native/manual gates pass for the exact candidate. Retain visible NOT RUN/BLOCKED gaps and state the exact validated scope.

Suggested future integration grouping: source handoff, then QA tests/fixes/evidence. Creating these documents does not authorize product implementation, commits or release now.
