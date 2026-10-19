# Luma prioritized improvement tasks

Prioritized backlog, 19 October 2026. Some tasks are now implemented; their status and verification are recorded under each task. Work on the remaining tasks within their stated scope. IDs belong to this audit; older `UX-01` etc. in 13–18 October documents are a different numbering scheme. Refer to tasks as `ui-ux-audit/UX-NN` in future work to prevent collisions.

Each task maps to the same-numbered finding in [AUDIT_REPORT.md](AUDIT_REPORT.md). [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) supplies exact presentation/copy conventions. Priority: P0 = critical task integrity/release gate; P1 = major usability/accessibility/recovery; P2 = consistency, friction and localized polish; P3 = optional refinement. A validation task does not claim the proposed defect has been observed.

Paths beginning `ui/` or `language/` are under `app/src/main/java/com/luma/lifeos/`; resources are `app/src/main/res/values{,-fa,-fr}/strings.xml`. Preserve existing translated/user-authored data. Complexity Low/Medium/High is relative scope, not an estimate in hours.

## Implementation order and dependencies

1. **Integrity and release baseline:** UX-01, UX-33. UX-33 diagnosis can be investigated independently; do not alter code merely to make stale assertions pass.
2. **Recovery:** UX-02/03/04/05/06/07. These can be scoped individually, but coordinate shared habit state files.
3. **Trust and accessibility:** UX-10/15/21/27/28/34/35; these do not depend on a complete visual-token migration.
4. **Shared contracts:** UX-13 inset validation → UX-11 pinned bars; UX-12 tokens → UX-14 responsive migration. Pilot on Today/Timeline/one editor, then migrate feature families.
5. **Flow and consistency:** UX-08/09/16/17/19/20/22/23/24/25/26. Bound long histories and profile performance under UX-18 after correctness is stable.
6. **Owner-selected optional work:** UX-29/30/31/32. P3 items do not block release. Search expansion and reminders in UX_OPPORTUNITIES.md need separate scope approval.

Shared-file coordination: avoid concurrent edits to `LifeOsApp.kt`, locale resource bundles, `HabitsViewModel.kt` and editor/photo state contracts. Keep each task a reviewable change; a large file does not justify unrelated refactoring. Existing pending specifications for search scope/privacy/backup status should be reconciled with this backlog rather than implemented twice.

## P0 — critical integrity and release gates

### UX-01 — Bind habit rows and actions to the displayed date

- **Problem / impact:** F01; old-day rows remain actionable beneath a new-day header, risking a misplaced personal record.
- **Affected:** Today Habits preview, Habits list/detail entry; `ui/habits/HabitsViewModel.kt` (`selectDate`, `observe`, `log`), `HabitUiState.kt`, `HabitsRoute.kt`, `HabitsScreen.kt`, `TodayHabitsCard.kt`.
- **Solution:** represent selection generation + loaded date explicitly. Clear mismatched rows or render them disabled with their old date visibly labeled. Cancel prior observations immediately on selection; ignore stale emissions. Require row date and active loaded date/generation to agree at Log/Open dispatch. No new pending log may use a stale row; already acknowledged pending operations retain their original immutable payload.
- **Complexity / dependencies:** Medium; none. Precedes habit polish and selected-log work.
- **Acceptance:** delay next-day observation; header changes but no old-day Log/Open works. Fail the new read: mismatch stays non-actionable and Retry targets the selected date. Rapid A→B→C switches ignore late A/B emissions. Past→future never enables prior-date Log. Same checks apply to Today and after recreation. Existing pending log retry remains idempotent with original date/zone.
- **Verification:** controllable-flow ViewModel regression for delayed/stale/failing emissions and mutation rejection; Compose test taps during transition and checks actual repository payload. Manual 320 dp/font-2.0 verifies loading/disabled reason without hidden Retry.
- **Status:** Complete (19 October 2026).
- **Results:** synchronous invalidation and selection generations protect Habits, Today and detail actions; stale emissions, errors, repeated A→B→A selection and delayed zone lookup are covered. Failed reads retain a selected-date Retry path. Saved pending-log retry keeps its original ID, date, zone and input. English, French and Persian loading copy was added.
- **Verification:** JVM suites passed: app 250, domain 126, data 25, usage 13 and Health Connect 6 tests. App lint and debug/test APK builds passed. Three focused Compose flows passed on disposable API 36 emulator `emulator-5562`, including date transition/read failure/retry and detail recovery at 320 dp and 2.0 font scale; a future date kept Log disabled and its reason visible. Rendered evidence: [Habits loading](evidence/ux-01/habits-loading.png), [Habits read error and Retry](evidence/ux-01/habits-error.png), [Habits future-date reason](evidence/ux-01/habits-future.png), [Today loading](evidence/ux-01/today-loading.png), [Today read error and Retry](evidence/ux-01/today-error.png), [Today future-date reason](evidence/ux-01/today-future.png). Existing `HabitCaptureFlowTest` also ran: 17/18 passed; its missing Log tag assertion for a future-start unscheduled row appears in the existing full app instrumentation failure register in `docs/TODO_ACCEPTANCE_2026-10-19.md`. The scoped UX-01 future-date case passed with a scheduled habit. TalkBack speech traversal was not part of this task’s verification.

### UX-33 — Diagnose the recorded full-suite failures before release acceptance

- **Problem / impact:** F33/H; latest task acceptance reports 14 app and 2 data failures. A passing older README record is not a current release baseline.
- **Affected:** failure cause/result register and full APK runs in `docs/TODO_ACCEPTANCE_2026-10-19.md`; affected capture/habits/Review/summary/usage instrumentation and `TimelinePersistenceTest`; current implementation and fixtures only if diagnosis warrants it.
- **Solution:** reproduce named failures individually/by class on a clean disposable application ID; capture awaited state/semantics and logs without private content. Compare actual product contract, fixture setup and timing before deciding code vs test change. Verify newest-first Timeline order from requirements before changing assertions. Maintain a cause/result matrix in the future implementation evidence.
- **Complexity / dependencies:** Medium, may split diagnosed product defects into follow-up tasks; none. A release acceptance gate, not proof of 16 product defects.
- **Acceptance:** every reported failure has a reproduced/classified cause or explicit unresolved block; focused corrections have meaningful regression evidence; full app/data suites rerun with counts, skips, build identity and environment recorded. No failure is silently waived or fixed by arbitrary delays/text replacement.
- **Status:** Complete (19 October 2026).
- **Results:** The 14 app and 2 data baseline failures were classified and corrected against the current UI/data contracts. The root causes were stale state/copy/order assertions, missing accessible no-data feedback, and capture controls becoming actionable before disclosure state initialization. Two additional full-suite timing issues in locale restoration and saved-entry observation were also corrected with condition-based waits. UX-33 ends with clean full app/data APK runs; TD07 remains In progress for its separate TalkBack and acceptance gaps.
- **Verification:** On disposable API 36 `emulator-5562`, app instrumentation passed 169/169 and data instrumentation passed 145/145 with no skipped tests. The two Timeline cases also passed individually and as a class after aligning assertions with newest-first SQL order. Final APK hashes, durations, commands and the failure cause/result matrix are recorded in [TODO_ACCEPTANCE_2026-10-19.md](../TODO_ACCEPTANCE_2026-10-19.md). Gradle-managed `connectedDebugAndroidTest` remains unavailable because its UTP host-output artifact is uncached; UX-33 used the full direct-ADB APK workflow on disposable data.

## P1 — high-impact usability, trust and accessibility

### UX-02 — Keep habit results and failed Undo visible

- **Problem / impact:** F02; Today omits action errors and full Habits places Undo below all rows. Users cannot reliably know or reverse what happened.
- **Affected:** `TodayHabitsCard.kt`, `HabitsScreen.kt`, `HabitsViewModel.kt` Undo/result state; common feedback/SnackbarHost wiring as needed.
- **Solution:** use an operation-specific retained error adjacent to its relevant row/Undo target, independent of row order; concise success + Undo in visible snackbar or stable feedback area. Do not keep “Saved” as the only message after failed Undo. Clear success/error only when acknowledged, superseded or resolved.
- **Complexity / dependencies:** Medium; UX-01 for date-sensitive action ownership; can use existing AccessibleFeedback before UX-15 migration.
- **Acceptance:** first of ten habits gives visible confirmation/Undo without scrolling to list end. Failed deleteLog announces failure and keeps retry on Today/full list; count follows confirmed data. Error remains accessible if target leaves preview; repeated retry does not remove a different repetition.
- **Verification:** fake failed Undo then successful retry; Compose checks feedback outside scrolled rows; manual TalkBack/font-2.0 and two rapid completions verify correct target and focus.
- **Status:** In progress — implementation complete; acceptance verification pending (19 October 2026).
- **Results:** Successful habit logs use the shared app snackbar with an Undo action bound to the exact log ID. Dismissing the snackbar acknowledges the result. Failed undo or history deletion retains that log ID with an accessible error and retry action; pending removal is shown immediately. Today, Habits and habit detail keep feedback independent of row order, while counts remain driven by observed habit data. Copy is localized in English, French and Persian.
- **Verification:** `:app:compileDebugKotlin` passed, and `git diff --check` passed. Failure/retry behavior, rapid completions, TalkBack and font-scale checks were not run; those acceptance checks remain pending.

### UX-03 — Preserve Today task recovery when the row disappears

- **Problem / impact:** F03; only visible preview rows show `rowErrors`, hiding missing/conflict failures after data changes.
- **Affected:** `ui/todo/TodayTasksCard.kt`, `TodayTasksViewModel.kt`, `TodayTasksState.kt`; reuse full-list feedback behavior in `TodoListScreen.kt` rather than copy an incompatible model.
- **Solution:** render retained off-preview pending/error summaries with stable task title/operation and Dismiss plus safe Retry/Open as supported. Missing tasks cannot offer a retry that recreates them. Limit presentation height through a scrollable feedback region when several failures exist.
- **Complexity / dependencies:** Medium; none; shared semantics align with UX-15.
- **Acceptance:** completion racing with delete, due-date change, reordered fourth task and restored failure each retain understandable feedback. Dismiss clears only that error. Completion changes preview/count only from real data. Other Today modules remain usable and viewport is not consumed by multiple errors.
- **Verification:** ViewModel and Compose fixtures for all off-preview cases, including 3+ retained failures at 200% font; assert retry uses original revision/token and missing tasks stay missing.
- **Status:** In progress — implementation complete; acceptance verification pending (19 October 2026).
- **Results:** Today keeps task title, operation and classified failure with the saved action identity. Off-preview summaries remain visible during task loading/read errors, use safe Retry only for the retained failed intent, allow opening a conflicted task for review, and never retry a missing task. Multiple summaries live in a bounded scroll region, with localized English, French and Persian labels.
- **Verification:** `git diff --check` passed. ViewModel/Compose fixtures, 200% font, race/reorder/restoration behavior and device accessibility checks were not run; acceptance remains pending.

### UX-04 — Make task conflict-load Retry actionable

- **Problem / impact:** F04; LOAD error with `conflict=true` displays an ineffective Retry.
- **Affected:** `TodoEditorViewModel.kt` (`retry`, `reload`, `load`), `TodoEditorScreen.kt` error/reload controls.
- **Solution:** distinguish approved reload retry from generic mutation retry. After the user confirms Reload latest, a failed read can be retried through that read path; retain the draft until a successful replacement is accepted. Do not bypass conflict protection or request discard again on every transient retry.
- **Complexity / dependencies:** Low; none.
- **Acceptance:** conflict → confirmed Reload latest → failed read → Retry actually reads and recovers. Repeated failure retains text/date and truthful copy. If latest record is gone, show missing state. Save/status/delete remain revision-safe throughout; Cancel before confirming reload preserves draft.
- **Verification:** deterministic repository fail-once test exercising the UI callback, then Compose recovery flow; no broad test duplication of unchanged editor behavior.

### UX-05 — Protect Mood draft restoration from oversized notes

- **Problem / impact:** F05; unbounded note enters SavedStateHandle and can exceed saved-state capacity. This is a structural risk; crash threshold is unmeasured.
- **Affected:** `ui/mood/MoodEditorViewModel.kt`, `MoodEditorScreen.kt`, `MoodUi.kt`; `core/domain/src/main/kotlin/com/luma/lifeos/domain/model/Mood.kt` if input policy is shared.
- **Solution:** new-edit limit 5,000 UTF-16 code units; for notes within the limit, reject oversized replacement while retaining last accepted text and specific inline error; counter near limit. Existing longer records remain readable/copyable; strictly shortening edits may remain over 5,000, but growth is rejected; never silently truncate. Keep over-limit legacy note drafts outside the Bundle in private draft storage with only a compact saved-state reference; cover cleanup/restore, and do not write the whole legacy note back to SavedStateHandle on load.
- **Complexity / dependencies:** Medium; none. Do not tighten journal or other text limits.
- **Acceptance:** paste over limit retains accepted content and announces limit; exact boundary accepted; correction clears validation. Existing long saved note opens intact and is never auto-rewritten; 12,000→11,000 accepted and retained outside the Bundle, 12,000→13,000 rejected with prior text retained. Background/recreate/process restoration preserves accepted score/context/note without oversized Bundle content.
- **Verification:** boundary/unicode/legacy tests plus oversized paste and process restoration on disposable installation. Measure Bundle payload rather than assuming total safety from a single field limit.

### UX-06 — Align Mood deletion and failed-load draft recovery

- **Problem / impact:** F06; lost completion after committed delete leads to repeated LOAD error; restored draft disappears while not ready.
- **Affected:** `MoodEditorViewModel.kt` (`delete`, `load`, persisted operation), `MoodEditorScreen.kt`; use Journal's existing reconciliation pattern as reference.
- **Solution:** persist delete intent/identity before write; on recreation reconcile missing source as completion only when supported by pending intent. Distinguish externally missing record from read error. Keep restored score/emotions/note/context visible read-only during failed reload, with Copy available for note text and Save disabled.
- **Complexity / dependencies:** Medium; coordinate with UX-05 to avoid competing draft state changes.
- **Acceptance:** committed delete + lost result exits once without retry loop; failed uncommitted delete retains record and retry; ordinary missing edit source has clear unavailable state. Failed read retains inspectable draft and dirty-close guard; successful retry restores editing without duplicate creates.
- **Verification:** simulated commit-then-lost-result/process restoration, true read failure and externally missing source; Compose checks visible retained note/disabled Save and one navigation result.

### UX-07 — Open the exact habit repetition selected in Timeline

- **Problem / impact:** F07; logId is passed but may be outside latest 50 and has no visible selection.
- **Affected:** `LifeOsApp.kt` habit route, `HabitDetailRoute.kt`, `HabitsViewModel.kt` (`refreshDetail`), `HabitHistory.kt`; habit repository/history query as needed.
- **Solution:** resolve requested log by ID and fetch a bounded neighboring history window or index; scroll to it after layout; selected text marker + tonal highlight + semantic selected state. Preserve selected target across recreation. No unbounded scan of all records in UI.
- **Complexity / dependencies:** Medium; UX-01 for safe date context.
- **Acceptance:** the 101st most recent log opens visible and distinguishable; correction/deletion acts on that ID. Missing/deleted target shows unavailable context and safe return; canceled edit returns to selected item. Ordinary detail without logId starts with normal recent history.
- **Verification:** repository fixture >150 logs across dates/zones; Compose deep-link/navigation and restoration checks; manual TalkBack arrival announcement, large text and delete-focus behavior.

### UX-10 — Explain Search's actual scope

- **Problem / impact:** F10; users can mistake empty results for absence of saved content.
- **Affected:** `ui/search/SearchRoute.kt`, localized strings; verify current scope in `core/data/src/main/kotlin/com/luma/lifeos/data/search/SearchSql.kt`.
- **Solution:** title Search entries; accessible What is searched? help with the exact text in DESIGN_SYSTEM.md; concise scope hint near input. Explicitly exclude body/notes/habit logs/tasks. Preserve SQL, query text, filters and initial/no-match distinctions.
- **Complexity / dependencies:** Low; none. Reconcile older pending search-scope recommendation instead of adding duplicate help.
- **Acceptance:** scope is discoverable before search and after no-match; English/French/Persian explain the same limits; title-only and linked-name hits still work. No body/habit/task search is implicitly introduced.
- **Verification:** resource/content review and one targeted Compose check for help visibility/dismissal while retaining query; use existing query tests for regression, no new tests mirroring static prose.

### UX-13 — Validate and unify inset ownership

- **Problem / impact:** F13/V; outer and nested default Scaffolds/top bars handle insets inconsistently. Actual clipping/duplication is not established.
- **Affected:** `LifeOsApp.kt`, `common/EditorTopBar.kt`, `DetailTopBar.kt`, nested editor/Habits/Tasks Scaffolds and sticky action areas.
- **Solution:** first measure top/bottom content bounds on identified build. Apply outer-shell safe-area owner and consume padding; nested routes own bar/content spacing with zero duplicate system insets. Modal windows keep their own native handling. Remove arbitrary extra tail padding only when overlay/system coverage is correctly accounted for.
- **Complexity / dependencies:** Medium; none; precedes UX-11 migration. If measurement finds no defect, document passing ownership contract and avoid a speculative patch.
- **Acceptance:** one safe-area allowance, no duplicated status-bar spacer, no content under cutout/system navigation; keyboard never covers focused field or Save, and hides without persistent gap. FAB never conceals final actionable row. Back and selection survive rotation.
- **Verification:** API 28 and API 35/36, gesture/three-button, portrait/landscape, IME shown/hidden, light/dark, cutout emulator. Capture actual bounds/screens before and after; do not infer results from zero-inset source alone.

### UX-14 — Support constrained and wide windows

- **Problem / impact:** F14/V; no overall width policy, and several fixed horizontal groups may fail under constrained width/text scale.
- **Affected:** `LifeOsApp.kt`, `PrimaryNavigationBar.kt`, `DayNavigation.kt`, mood/importance scales, Task due-date row, Review evidence headers, page containers.
- **Solution:** <600 dp bottom nav; ≥600 dp native rail with same destination identities; content max 680 dp; 16/24 dp gutters. At 320–359 dp/font ≥1.5 stack controls/values; wrap five-score group with full minimum targets. Keep existing adaptive Habit/Review behavior. No forced dual-pane product/navigation rewrite.
- **Complexity / dependencies:** Medium; UX-12 tokens; UX-13 inset contract. Start with measured constrained-width failures.
- **Acceptance:** 320/360/411/600/840 dp, font 1.0/1.3/2.0 and maximum display size retain all critical controls with no overlapping targets; rail switches preserve stack/day/week/query. User text/errors wrap; Back remains visible. French and Persian preserve reading/action order.
- **Verification:** focused Compose width/font tests for branching controls and manual window/rotation/keyboard matrix; geometry assertions for distinct 48 dp target regions. Do not claim tablet quality from a phone screenshot.

### UX-15 — Standardize meaningful accessible feedback and headings

- **Problem / impact:** F15; asynchronous errors/results can be visually present without accessible notification or contextual actions.
- **Affected:** `common/AccessibleFeedback.kt`, Today/Timeline/Habits/Tasks load states, catalog/detail errors, `BackupSettingsCard.kt`, `LanguageSettingsCard.kt`, page/section headings.
- **Solution:** reuse polite live-region feedback; error semantics only on failures, success `isError=false`. Specific accessible Retry/View all labels; page/section heading roles; preserve native component roles and avoid duplicate contentDescription on ordinary text. Persistent failures remain reachable independent of transient snackbar.
- **Complexity / dependencies:** Medium; none; migrate per family. UX-02/03 use the same behavior.
- **Acceptance:** backup success/error and language-save failure announced once; genuine zero/loading/error states distinguishable; retries contextual in action list; page/section heading traversal works. No repeated announcements on recomposition/scroll, no focus hijack on metric refresh, stale error clears after recovery.
- **Verification:** semantics assertions for liveRegion/error/heading plus manual TalkBack across success→failure→recovery, keyboard and Switch Access checks on representative routes. Record speech results separately from semantics tests.

### UX-21 — Make each photo removal identifiable and preserve focus

- **Problem / impact:** F21; identical Remove labels are ambiguous in screen-reader action navigation.
- **Affected:** `ui/photos/PhotoGallery.kt` and localized indexed remove strings.
- **Solution:** accessible Remove photo N labels while retaining concise visible Remove; stable photo ID for operation/focus. After removal focus next photo, previous if last, otherwise Add photo. Do not announce destructive record deletion when removing only a draft attachment.
- **Complexity / dependencies:** Low; none; preserve leased-file and removal-failure safeguards.
- **Acceptance:** every remove target distinct in TalkBack buttons; correct photo removed; indices update after reorder/removal; failed removal retains image and actionable error; focus never drops to page start.
- **Verification:** 1/3/20 attachment Compose identity/removal semantics fixtures and manual TalkBack traversal/focus. No application data is removed during audit; future tests use disposable media.

## P2 — consistency, practical friction and state polish

### UX-08 — Use operation-specific habit detail failures

- **Affected/problem:** F08; `HabitDetailRoute.kt`, `HabitsViewModel.kt`, `HabitUiState.kt`; paging/delete failures falsely claim a correction was retained.
- **Solution/impact:** typed correction/history/delete/skip/management error state, truthful copy and adjacent action. Improves trust and directs useful recovery.
- **Complexity/dependencies:** Low; UX-02 error contract; coordinate with UX-07 history state.
- **Acceptance:** each failing operation names its own target and retry; correction draft preserved only when relevant; history Retry does not submit correction; ambiguous deletion reconciled before claiming unchanged data.
- **Verification:** independently fail each operation in state tests and one Compose recovery path for history vs correction.

### UX-09 — Attach newly created context to the invoking editor

- **Affected/problem:** F09; `LifeOsApp.kt`, person/place editor completion contracts and mood/journal/memory routes. New context requires manual reselection.
- **Solution/impact:** return created ID through origin back-stack saved state, consume once, select/attach on return; ordinary catalog create unchanged. Cuts repeated searching/taps.
- **Complexity/dependencies:** Medium; none; integrate with UX-16 selectors later.
- **Acceptance:** each capture family creates person/place → returns with new item visibly selected and draft/scroll unchanged; cancel creates no relation; recreation consumes result once; 20-person limit explains inability to attach; deleted/unavailable result remains safe and removable.
- **Verification:** end-to-end Compose navigation for both entity types plus recreation/result-consumption and selection-limit fixture; persistence verifies one intended relation.

### UX-11 — Use one pinned secondary top-bar pattern

- **Affected/problem:** F11; Settings hub/sections, Privacy, Review sources/usage, Tasks/catalog routes and shared bars. Back placement/visibility varies.
- **Solution/impact:** extend existing DetailTopBar; consistent icon Back/title/action, fixed above content. Move verbose context into scroll body. Privacy routes both Back paths through same busy guard while confirmed OS request remains NonCancellable.
- **Complexity/dependencies:** Medium; UX-13; UX-12 visual tokens optional to initial migration.
- **Acceptance:** Back/title present at bottom of every secondary scroll; system and visible Back pop once or share blocking guard, active modal dismisses first; no double title/inset. All preexisting navigation/restored routes resolve.
- **Verification:** long-list/evidence/privacy Compose scroll tests and real gesture Back while fake reset is busy; real reset only under UX-33/device protocol with disposable data.

### UX-12 — Apply role-based typography, spacing and surfaces

- **Affected/problem:** F12; `ui/theme/Theme.kt`, shared spacing/shapes definitions, Today/Timeline/More/Settings/Review/card families. Same roles use different scale/gutters.
- **Solution/impact:** DESIGN_SYSTEM.md metrics: primary title 22/28, section 16/24; 16 dp phone gutter/card inset; 12 dp compact preview; 4/8/12/16/24/32 rhythm; native role-based Shapes. Preserve palette and content. Timeline specifically uses title + Search in one top bar, then date and counts/order; put the usage display switch and routine timezone explanation under named Display options while retaining meaningful zone mismatch context. Reduces visual relearning and header height.
- **Complexity/dependencies:** Medium; none; coordinate UX-11/13 rather than re-pad twice.
- **Acceptance:** same title/section/card roles consistent across five destinations; no arbitrary 5/14 dp gaps for equivalent roles; default native control internals untouched. Outlined navigation vs tonal content remains intentional; no per-screen hardcoded color palette introduced. All sampled text pairs retain contrast.
- **Verification:** source/token review + before/after rendered light/dark en/fa/fr comparisons. No unit tests for constants; geometry/screenshot review where layout changes materially.

### UX-16 — Replace eager capture option clouds with bounded selectors

- **Affected/problem:** F16; `PersonPicker.kt`, `PlacePicker.kt`, `JournalEditorScreen.kt` tag options and selector state. Large catalogs bury subsequent fields.
- **Solution/impact:** selected chips remain inline; Choose opens searchable lazy modal/full-screen selector with Done/Create/Clear. Preserve multi-person/tag and single-place semantics/limits. Reuse Search option presentation without coupling capture state to search criteria.
- **Complexity/dependencies:** Medium; UX-17 labels, UX-25 modal constraints; compatible with UX-09 create result.
- **Acceptance:** 1,000-option fixtures compose bounded visible choices, search by name works, selection survives query/recreation, unknown selected item removable, limits announced. No giant option FlowRow expands the form. Done/Create reachable with IME/font 2.0.
- **Verification:** lazy-composition/query/selection-limit tests; manual keyboard/TalkBack with long/duplicate names; measure actual fixture performance before claiming latency improvement.

### UX-17 — Disambiguate duplicate catalog names everywhere choices occur

- **Affected/problem:** F17; SearchOptionPicker/ActiveSearchFilters, PersonPicker/PlacePicker and people/place catalog rows. Distinct people/place IDs look identical; normalized tag names are unique and are not part of this proven duplication finding.
- **Solution/impact:** meaningful existing context if available; otherwise localized short identity suffix, initially 6 ID characters, extended until unique within same-name options. Never rename saved entities. Apply both selected/unselected visual and accessible labels.
- **Complexity/dependencies:** Medium; none; selector migration consumes shared label formatter.
- **Acceptance:** same-name people/places separately recognizable before selection, after selection and restoration; unknown options distinctly removable; suffix collision extends deterministically; user-authored text unchanged.
- **Verification:** duplicate/collision/mixed-script fixtures; TalkBack option/chip action traversal; localized formatting review.

### UX-18 — Bound linked histories and remeasure large-data paths

- **Affected/problem:** F18; core Person/Place graph queries/repositories, `PeopleRoute.kt`/`PlacesRoute.kt`; Search/Review current performance baseline. Full histories materialize/sort and old benchmarks miss targets.
- **Solution/impact:** two scoped steps: (a) measure current Search/Review and linked-history latency/memory on 50k-event disposable fixture; (b) introduce 50-row stable history pages, max 300 retained records, ordered by occurredAt then stable event ID with existing oldest-first meaning. Remove redundant UI sort; preserve live updates/anchor. Optimize Search/Review only after a current trace identifies cause.
- **Complexity/dependencies:** High; UX-33 contract diagnosis first. Do not change search scope or summary semantics for speed.
- **Acceptance:** bounded initial queries/window; no missing/duplicate events at equal times or mid-history writes; returning preserves anchor; deleted last page recovers. Report p50/p95 and memory with build/hardware/fixture. Recheck existing 300 ms Review and documented Search targets; explicitly record any miss rather than invent new acceptable thresholds.
- **Verification:** Room paging/order/live-write tests, long history UI return/scroll checks and repeatable profiling. Keep private note/body payload out of list projections when not displayed.

### UX-19 — Keep memory marker inputs near the source excerpt

- **Affected/problem:** F19; `MemoryEditorScreen.kt` promotion branch; original full body precedes note/importance.
- **Solution/impact:** source type/date/title + six-line excerpt; marker note/importance immediately next; explicit Expand original/Collapse original. Expanding full source occurs after editable marker fields to preserve access. Improves long-journal promotion.
- **Complexity/dependencies:** Low; none.
- **Acceptance:** 50,000-character source leaves marker fields promptly reachable; original remains fully readable; source title/time/body never modified by promotion; marker removal still preserves source.
- **Verification:** long source/blank source Compose and manual font-2.0/keyboard; existing promotion persistence regression remains passing.

### UX-20 — Distinguish attachment work from entry saving

- **Affected/problem:** F20; PhotoDraftSession, journal/memory editor states/buttons, PhotoEditor. Adding/removing photo says Saving.
- **Solution/impact:** operation kind enum or sealed state for adding/removing/saving; attachment-level indeterminate indicator; genuine Save only for record write. Preserve busy write guards. If provider read can safely cancel, expose Cancel adding photo and clean partial private files; otherwise do not promise cancelability.
- **Complexity/dependencies:** Medium; none; UX-21 labels compatible.
- **Acceptance:** slow provider → Adding photo; removal → Removing photo; database commit → Saving. Draft remains intact on failures, partial files never become attached, accepted photos retain leases; no duplicate Save during active photo operation.
- **Verification:** controllable slow/canceled/failed import/remove fakes; native photo picker cancellation and provider-delayed URI fixture on disposable app. Verify no false success or lost draft after recreation.

### UX-22 — Use one localized, device-aware time presentation policy

- **Affected/problem:** F22; `HabitHistory.kt`, `HabitTimelineCard.kt`, `language/LocalizedFormats.kt` and current time-display call sites. Hour cycle/digits vary.
- **Solution/impact:** shared setting-aware time/date-time display helper using Android 12/24-hour choice + app locale/digits; explicit recorded/display zone. Apply to history/Timeline/capture/Review where times are shown. Preserve ISO persistence, Gregorian dates, DST resolution and stored precision.
- **Complexity/dependencies:** Medium; none; no formatting-driven writes.
- **Acceptance:** same timestamp displays consistent digits/hour cycle in history/Timeline/correction en/fa/fr; changing system hour cycle updates after foreground return; offsets/zones remain accurate and raw IDs bidi-safe.
- **Verification:** formatter tests for both hour cycles/locales/DST overlap and device setting-return smoke check; compare persisted timestamps before/after.

### UX-23 — Clear only the integration error that actually recovered

- **Affected/problem:** F23; HealthSettingsViewModel/UsageSettingsViewModel configuration observers and UI Refresh callbacks. Successful refresh can leave stale action error.
- **Solution/impact:** independent configuration-observation vs mutation/access failures; successful config emission clears observation error only. Use actual UI `retryConfiguration` path in regression. Avoid implying Import succeeded because settings loaded.
- **Complexity/dependencies:** Low; none.
- **Acceptance:** fail config observation → recover repository → UI Refresh removes load error and preserves preference/history. Still-failing config remains error; prior import failure is not erased merely by config read. No refresh auto-enables access/import.
- **Verification:** fail-once observer tests for both integrations and callback-level Compose check. Existing Health test calling `vm.refresh()` alone is insufficient.

### UX-24 — Restructure privacy information around user decisions

- **Affected/problem:** F24; PrivacySettingsRoute, shared policy content, HealthRationaleActivity, strings, navigation callbacks. Accurate prose is difficult to scan.
- **Solution/impact:** visible local/no-account/optional-connections summary; named Records & photos, App usage, Health Connect and Backups disclosures, matching the pending privacy specification. Keep deletion scope/external-copy caveats persistently visible beside destructive action; add direct existing Integrations navigation. Rationale reuses policy without database initialization.
- **Complexity/dependencies:** Medium; UX-11 for fixed bar; reconcile earlier pending UX-14 spec before editing.
- **Acceptance:** user can locate Usage/Health revocation and exported-file retention by headings; imports-off vs permission revocation distinction intact. Preserve provider originals/photo copies/in-flight read/seven-date Health reconciliation/older retained snapshots qualifiers and reset accepted-vs-erased wording in all locales. No mandatory consent/onboarding added.
- **Verification:** copy-to-existing-policy traceability review; Compose disclosure/navigation; locale/large-text visual scan; launch rationale independently without data access.

### UX-25 — Keep complex modal content and actions reachable

- **Affected/problem:** F25/V; restore preview, permission explanations, habit correction, timestamp/date dialogs, Search filters/options, task reload/delete dialogs.
- **Solution/impact:** first test constrained matrix; bound and scroll body, keep actions reachable; use full-screen selector where height/IME requires it. Avoid fixed 320 dp list plus unbounded header. Retain native picker controls and semantics.
- **Complexity/dependencies:** Medium; UX-13 modal inset contract; supports UX-14/16. Treat already passing modal layouts as retained behavior.
- **Acceptance:** 360 dp width, short landscape height, font 2.0, long French/Persian error text and docked IME expose entire content + confirm/cancel. Modal Back dismisses top layer once; closing filter picker preserves live criteria; destructive warnings never omitted to make content fit.
- **Verification:** Compose constrained-height/long-copy fixtures plus native keyboard/TalkBack date input/focus. Actual reset/restore only disposable test data; fake service suffices for layout validation.

### UX-26 — Reduce repeated setup and empty-preview prose

- **Affected/problem:** F26/O; TodayTasksCard/TodayHabitsCard, HabitsScreen zone note, ReviewOverview. Large duplicate empty/setup content crowds daily use.
- **Solution/impact:** one empty Tasks sentence + Add, nonzero count only; concise empty Habits + Add, keep View all; move routine tracking-zone explanation behind named details while always showing mismatched tracking day/zone; single overview Integrations action for all missing connected signals with explicit scope. Each full Review section keeps its contextual recovery.
- **Complexity/dependencies:** Low; UX-02/03 recovery must not be hidden by compaction; UX-12 tokens.
- **Acceptance:** no duplicated zero Tasks explanation; no loss of unknown/error/zero distinction; mismatch warning always visible; Today capture remains near top; Review setup optional and clear about both signals. Populated previews and no-scheduled-habits state retain correct controls.
- **Verification:** empty/one-item/many/error/offday/mismatched-zone rendered fixtures in three locales; compare initial viewport at font 1.0/2.0 without arbitrary pixel-count success claims.

### UX-27 — Match event card action hints and browse destinations

- **Affected/problem:** F27; EventCard, TodayScreen, LifeOsApp openEvent/editEvent and relevant search/review/history callers. Hint says Edit while route may be detail.
- **Solution/impact:** card primary action consistently Open [type] detail; explicit Edit is separate. Today mood/direct memory route through existing EventDetail, journals through JournalDetail. Keep memory marker shortcut specifically named; imported entries read-only.
- **Complexity/dependencies:** Medium; none; no new detail screen needed.
- **Acceptance:** same entry tapped from Today/Timeline/Search/catalog/Review opens same content detail; explicit Edit opens proper editor; Back returns to original surface/scroll. Screen-reader click hint names actual Open action; bookmark shortcut never toggles by accident.
- **Verification:** navigation tests for mood/journal/direct memory/imported record across callers; semantic click-label assertion. Product owner may preserve a separate clearly labeled quick-edit affordance, not inconsistent implicit behavior.

### UX-28 — Distinguish missing journal from failed journal read

- **Affected/problem:** F28; JournalDetailRoute and detail state model. Missing record becomes perpetual retryable error.
- **Solution/impact:** explicit Loading/Data/Missing/Error states like EventDetail; Missing says Entry no longer available with Back, no misleading Retry; actual Error retains Retry and context.
- **Complexity/dependencies:** Low; none.
- **Acceptance:** record removed externally/restore → Missing; transient read exception → Error then successful Retry; restored navigation does not crash; no stale Edit/Promote action for missing item.
- **Verification:** missing/read-failure/recovered flow tests plus Compose action availability; preserve source/photos during retry.

### UX-34 — Show task counts only from known snapshots

- **Affected/problem:** F34; TodoListScreen and TodoListUiState in TodoListViewModel.kt:42. Initial empty page total is formatted as known zero during loading/failure.
- **Solution/impact:** explicit loaded-snapshot state; initial count hidden or Loading tasks; prior successful count may remain with refresh-failed qualification. Accurate empty-state trust.
- **Complexity/dependencies:** Low; none; UX-26 handles only Today prose.
- **Acceptance:** initial delayed/failed load never asserts zero; successful empty snapshot shows zero; populated refresh failure retains qualified last count; tab changes do not briefly display the other tab's count as current.
- **Verification:** first-load delay/failure, true empty, tab switch and stale refresh tests; Compose checks count/state pairing.

### UX-35 — Name the filter in Review source context

- **Affected/problem:** F35; ReviewSourcesRoute/source context loading. Generic Person/Place/Emotion/Activity label hides which entity produced the list.
- **Solution/impact:** show type + human-readable entity/activity/unit/source in scrollable context under pinned bar. Resolve name via bounded context lookup or pass safe display metadata; stable ID remains query authority. If deleted, say Selected person/place unavailable with short identity suffix; keep already loaded evidence safely scoped.
- **Complexity/dependencies:** Medium; UX-11 context layout, UX-17 duplicate labels.
- **Acceptance:** two different people/places/emotions/activity groups distinguishable without returning; same-name entities distinct; missing entity does not silently switch filter; range/zone retained and all query IDs unchanged.
- **Verification:** route fixture for two same-type entities, duplicate names/deleted catalog/activity units; accessibility context label and state restoration checks.

## P3 — optional refinements

### UX-29 — Show qualified last-export history and link Backup from Privacy

- **Affected/problem/opportunity:** F29; BackupViewModel/BackupSettingsCard/private preferences, Privacy navigation. Returning user cannot see last successful local export.
- **Solution/impact:** installation-local timestamp after export stream write/flush/close succeeds; caption Last export completed on this device: [localized time]. Always qualify File availability is not checked. Add Open backup settings near reset advice. This is reassurance, not a file-existence/security guarantee.
- **Complexity/dependencies:** Medium; UX-15/24; reconcile pending older UX-18 backup-status spec.
- **Acceptance:** canceled picker/failed open/write/flush/close leaves prior history; export success survives relaunch. Metadata-save failure preserves truthful archive success with separate status failure. Restore neither imports nor overwrites export history; reset clears it; no filename/path retained unnecessarily.
- **Verification:** stream lifecycle failure matrix, preference persistence/relaunch, fake restore/reset boundaries; real picker test on disposable destination only.

### UX-30 — Preserve disclosure subject and expanded state

- **Affected/problem/opportunity:** F30; DetailsDisclosure, Usage/Health details, people/place choose toggles.
- **Solution/impact:** About app usage / About Health Connect / Choose people / Choose place subject remains in visible or accessible label in both states; stateDescription expanded/collapsed. Reduces ambiguity in assistive navigation.
- **Complexity/dependencies:** Low; none; UX-16 replacement selectors should not be built just to support this label fix.
- **Acceptance:** two simultaneously expanded integration sections remain distinguishable; recreation retains useful expansion; visible and spoken labels agree in all locales.
- **Verification:** semantic state check + TalkBack smoke review; no broad snapshot suite for text-only labels.

### UX-31 — Add a private photo inspection viewer

- **Affected/opportunity:** F31; PhotoGallery/PhotoRepository/AndroidPhotoCodec and optional viewer route/dialog. Fixed preview cannot reveal detail.
- **Solution/impact:** optional full-screen fit/zoom/pan with next/previous, indexed caption and Close, safely sampled original decoding; no sharing/upload default. Preserve editor return position/leases.
- **Complexity/dependencies:** Medium; UX-21 accessibility; separate owner approval for feature scope.
- **Acceptance:** portrait/landscape images fully inspectable, useful zoom detail without unbounded bitmap allocations; unavailable image recoverable; Back/Close returns to same photo; screen-reader controls provide alternatives to gestures; viewer holds no edit authority.
- **Verification:** large/rotated/corrupt images and low-memory scenarios, zoom/rotation lifecycle, TalkBack next/previous, private-data/no-network review.

### UX-32 — Explain photo failures with useful recovery

- **Affected/opportunity:** F32; PhotoDraftSession/PhotoEditor/import error boundary. Generic errors hide actionable reason.
- **Solution/impact:** typed oversize/unsupported-unreadable/provider-unavailable/storage failure mapped to localized copy and Retry/Choose another; no raw private paths/exception text. Keep generic safe fallback.
- **Complexity/dependencies:** Medium; UX-20 operation states; independent of viewer.
- **Acceptance:** each representative cause offers appropriate recovery; prior photos/draft retained; unavailable URI requires reselection rather than endless identical retry; no sensitive logging or new permissions.
- **Verification:** codec/provider/storage fakes, delayed native picker fixture, language/large-text review; preserve transaction/lease cleanup regression coverage.

## Verification matrix and handoff rules

This matrix specifies future checks, **not results from this audit**. Record build commit/APK identity, API/device, dimensions, locale, theme, font scale, fixture, action, expected/actual result and evidence location for each run.

| Dimension | Minimum future coverage |
| --- | --- |
| Window | 320/360/411 dp phones, 600/840 dp resizable emulator, short landscape, split-screen |
| Text/display | 1.0/1.3/2.0 font scale, maximum display size, long titles/error copy |
| Locale | English/French/Persian; RTL icons, raw zone/package identifiers, localized digits, 12/24-hour setting |
| Theme/system | System/Light/Dark, gesture/three-button, API 28 and API 35/36, keyboard/cutout |
| Assistive | TalkBack linear + heading/button navigation, Switch Access, hardware keyboard/D-pad, focus return |
| Async | Initial load, slow load, true empty, known zero, partial data, read failure, failed write, retry, stale emission, multiple failures |
| Lifecycle | Rotation/recreation, true process restoration, external picker return/cancel, interrupted commit, locale/theme switch |
| Data scale | Empty/small fixture, 150+ habit logs, 1,000 catalog options, 50k-event performance fixture, 20 photos |
| Privacy/native | No-permission core use; denied/revoked/unavailable/granted access with imports on/off; real file/photo picker; destructive cases only disposable app ID |

For implementation, run focused behavioral regressions first, then relevant JVM/Room/UI suites. Full suites are needed when shared state/navigation/persistence changes, and before release. Existing commands (adapt class selection to the actual test layout):

```sh
./gradlew :core:domain:test :app:testDebugUnitTest :core:data:testDebugUnitTest :integration:usage:testDebugUnitTest :integration:health:testDebugUnitTest
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest
./gradlew :app:lintDebug :core:data:lintDebug :integration:usage:lintDebug :integration:health:lintDebug
./gradlew :app:connectedDebugAndroidTest
./gradlew :core:data:connectedDebugAndroidTest
```

These commands were **not run by this documentation audit**. Use a writable permitted Gradle cache and disposable build/application identity per existing validation guidance; report environment blocks explicitly. Never run instrumentation that clears/restores/resets the owner's app. Do not grant real provider permissions solely to satisfy a layout test.

Definition of task completion: stated behavior implemented, relevant acceptance checks observed or explicitly blocked, no new unexplained failures, locale parity, source references/evidence recorded, and a bounded reviewer-readable diff. A test compiling is not a pass; semantic assertions are not a TalkBack test; old evidence is not a current-build result. Visual-only token/copy tasks need rendered verification rather than redundant unit tests for constants.
