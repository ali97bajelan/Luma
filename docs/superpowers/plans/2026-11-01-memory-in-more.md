# Memory in More Implementation Plan

> **Execution record:** Implemented on explicit follow-up request. Read revision 2 of the spec; see the source handoff for touched files and verification limits.

**Goal:** Add Memory to More as a recent-first collection with month headings, progressive history browsing and existing capture/details.

**Architecture:** Extend the memory repository with a bounded newest-first query, filtering authorized source types before its database limit. Add one secondary Compose page using the protected facade, with a capture action, content-led cards and load-older footer. Reuse existing detail/editor, save feedback and app-lock infrastructure.

**Tech Stack:** Existing Kotlin, Compose/Material 3, Navigation Compose, Room, lifecycle-aware Flow and java.time; no new dependencies.

**Spec:** [Memory in More — revision 2](../specs/2026-11-01-memory-in-more-design.md)

**Status:** Source implemented; behavior unverified. Supersedes revision 1's monthly-browser plan.

## Global constraints

- No new dependency, permission, schema, migration or archive format.
- Secondary route `AppRoutes.MEMORIES = "memories"`; preserve the singular editor route, primary navigation and weekly Review.
- Initially display 30 records and query 31. Load older increases visible depth by 30, always requesting one extra authorized row.
- Authorize event types before the SQL limit and retain gateway projection afterward. No unbounded range query or raw count.
- Implementation tasks only: no testing/QA phase, test authoring, build/lint/test command, emulator/device work or release.
- Preserve pre-existing workspace edits, especially host navigation and resources.

## Review focus

- Locked source types cannot consume the query limit or influence month headings/footer.
- Initial empty/error and older-load errors have different presentation and recovery.
- Inserts/delete/unmark preserve a surviving record anchor rather than snapping to top.
- Saved backdated records are reachable through View; unsuccessful capture never shows success.
- Cards remain meaningful without titles and avoid repeated note/status content in every locale.

## File map

Paths are relative to the repository. App Kotlin paths in this table are under `app/src/main/java/com/luma/lifeos/`.

| Action | Path | Responsibility |
| --- | --- | --- |
| Modify | `core/domain/src/main/kotlin/com/luma/lifeos/domain/repository/MemoryRepository.kt` | Bounded library read contract |
| Modify | `core/data/src/main/kotlin/com/luma/lifeos/data/dao/LifeEventDao.kt` | Type-filtered descending limited query |
| Modify | `core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomMemoryRepository.kt` | Bounded query mapping |
| Modify | `lock/ProtectedEventRepositories.kt` | Authorization before limit and final projection |
| Modify | `lock/RestrictedCaptureService.kt` | Return the created event ID from protected capture |
| Modify | `ui/privacy/RestrictedCaptureRoute.kt` | Start the requested protected Memory capture and report its saved ID |
| Create | `ui/memory/MemoryLibraryState.kt` | Load depth, grouping and state |
| Create | `ui/memory/MemoryLibraryRoute.kt` | Page, cards, footer and reading position |
| Modify | `ui/more/MoreRoute.kt` | Memory entry |
| Modify | `ui/navigation/AppDestinations.kt` | Secondary route constant |
| Modify | `ui/LifeOsApp.kt` | Navigation, save feedback, lock/screenshot context |
| Modify | `lock/LockPendingDestination.kt` | Typed unlock destination |
| Modify | `lock/AppLockCoordinator.kt` | Destination section |
| Modify | `app/src/main/res/values/strings.xml` | English copy |
| Modify | `app/src/main/res/values-fr/strings.xml` | French copy |
| Modify | `app/src/main/res/values-fa/strings.xml` | Persian copy |

No rewrite of existing weekly review or detail/editor screens. Record any necessary extracted helper path in the handoff.

## Task 1 — Bounded, authorized library query

**Files:** Domain repository, DAO, Room repository and protected repository above.

**Interface produced:**

`fun observeLibrary(limit: Int, eventTypes: Set<EventType> = setOf(EventType.MEMORY, EventType.JOURNAL, EventType.MOOD)): Flow<List<LifeEventRecord>>` on `MemoryRepository`.

Returns at most limit saved-memory records, ordered by occurrence/event ID descending. Require positive limit and supported types; empty types emit an empty list without querying. The protected facade may narrow types but never widen them. Keep the existing range API unchanged.

- [x] Add the contract. Add a transactional DAO query requiring a memory relation and permitted event types before `ORDER BY occurred_at DESC, id DESC LIMIT :limit`. Pass type names using existing Room conventions; no date restriction or count.
- [x] Implement `RoomMemoryRepository.observeLibrary` with existing graph mapping. Do not implement it by loading an unlimited range then taking a prefix.
- [x] In `ProtectedMemoryRepository`, derive readable requested types through existing `LockSnapshot.decide`/`LockRequest`: Memory requires Memories; Journal requires Memories + Journal; Mood requires Memories + Mood. Use effective grants, not policy flags alone.
- [x] Restart the limited delegate query on permission/authorization-epoch changes with `flatMapLatest`. Apply existing gateway projection, suppress stale-epoch emissions and return no private rows while access is unknown/blocked. The sentinel must pass the same authorization as visible rows.

**Deliverable:** Bounded protected collection prefix covering M02/M05. No schema change.

## Task 2 — Collection state and return position

**Create:** `app/src/main/java/com/luma/lifeos/ui/memory/MemoryLibraryState.kt`.

**Consumes:** Protected query from Task 1, restored visible limit, current zone and policy/session authorization epoch.

**Interfaces produced:**

- `MemoryLibraryUiState`: `InitialLoading`, `Empty`, `InitialError`, `Content(records: List<LifeEventRecord>, hasOlder: Boolean, olderLoad: MemoryOlderLoadState, observationError: Boolean)`.
- `MemoryOlderLoadState`: `Idle`, `Loading`, `Error`.
- `MemoryLibraryItem`: `MonthHeading(month: YearMonth)`, `Entry(record: LifeEventRecord)`.
- `memoryLibraryItems(records: List<LifeEventRecord>, zone: ZoneId): List<MemoryLibraryItem>`.
- A route-owned `MemoryLibraryStateHolder` exposing `state: StateFlow<MemoryLibraryUiState>`, `loadOlder()`, `retry()`, `close()`; constructor takes repository, initial visible limit, coroutine scope and `onCommittedLimitChanged: (Int) -> Unit`. Recreate/close it on authorization-epoch changes.

- [x] Begin at 30 visible/31 requested. Expose only the visible prefix and compute hasOlder from the sentinel. Load-older requests +30 once per tap cycle, guarding overflow and repeated taps.
- [x] Track committed and pending depth separately. Keep committed authorized content while a larger prefix loads. Commit depth on success; on failure retain the prefix, expose older-load Error and retry the same pending depth. Initial failures use InitialError.
- [x] Keep a live committed-prefix observation during failed/pending expansion so retained records can still change or disappear. A successful expansion replaces it. Cancel obsolete queries, propagate cancellation and clear private state when closing/recreating the holder; never keep a detached private snapshot after revocation.
- [x] If the live committed observation fails after returning data, retain its rows with a recoverable error and retry action; initial retry returns to loading before querying again.
- [x] Group by occurrence month in the display zone, with distinct keys `month:<YYYY-MM>` and event ID. Preserve descending record order; future records stay in their actual month.
- [x] Retain committed depth, authorization epoch, visible record ID/index, adjacent record IDs and scroll offset as primitive saveable metadata. Restore only after protected records arrive; use stable keys while mounted and fall back to a surviving neighbor after removal. Do not store records. Reset depth/anchor when the saved epoch differs from the current authorization epoch.

**Deliverable:** Initial/older-load states, live grouping and return-position behavior from M02/M05.

## Task 3 — Content-led page and localization

**Create:** `app/src/main/java/com/luma/lifeos/ui/memory/MemoryLibraryRoute.kt`.

**Modify:** The three resource files.

**Interface produced:**

`@Composable internal fun MemoryLibraryRoute(repository: MemoryRepository, today: LocalDate, zone: ZoneId, authorizationEpoch: Pair<Long, Long>, onBack: () -> Unit, onRecordMemory: () -> Unit, onEvent: (LifeEventRecord) -> Unit)`.

Mount only after the host gate permits reading. Epoch is the host's policy/session-generation pair; use stable zero values when no coordinator exists.

- [x] Add resource-backed M01–M06 copy in EN/FR/FA, including source-specific fallbacks/labels, Future date, initial/older states and saved/View copy. Reuse identical existing capture/importance strings.
- [x] Build one LazyColumn under AppPageScaffold/LumaReadingPane: full-width filled Record a memory button, state content or month sections/cards, then footer. Use M06's exact spacing/reading width and existing theme tokens. No duplicate app-bar/global capture action.
- [x] Implement two-line primary text from title → first nonblank note line → source fallback. Show up to three preview lines, removing the line used as heading. Show quiet source/date metadata and optional Importance: n/5. Omit unconditional MemoryStatus, original source bodies and attachment/people/place content.
- [x] Make each card one reading target with mirrored chevron calling onEvent(record). Add section-heading/card semantics and spec tags; decorative icons have no spoken label.
- [x] Render exact initial loading/empty/error copy with capture available. Footer uses Load older memories, disabled loading progress or inline error/Try again. Preserve focus during list updates and announce errors through current accessible helpers.

**Deliverable:** M01–M03/M05–M06 presentation and copy. No month selector or month-navigation arrows.

## Task 4 — More, protected navigation and save feedback

**Modify:** App-root files `ui/more/MoreRoute.kt`, `ui/navigation/AppDestinations.kt`, `ui/LifeOsApp.kt`, `lock/LockPendingDestination.kt`, `lock/AppLockCoordinator.kt`.

**Consumes:** Task 3 page, existing protected memories/currentDay/openEvent, EntryEditorResult and host snackbar mechanism.

**Interfaces produced:** `AppRoutes.MEMORIES`, optional `onMemory: (() -> Unit)? = null` appended to MoreRoute, `LockPendingDestination.MemoryLibrary`.

- [x] Add the More row after Tasks with spec icon/copy/tag; production always supplies it. Register the secondary route and navigate with launchSingleTop, passing the protected repository, current date/zone and authorization epoch.
- [x] Wire cards to openEvent and capture to the normal new-memory editor when unlocked. When app lock is enabled, start the existing restricted Memory capture with its create-session capability. Keep the library back-stack entry/state beneath child destinations. Back pops or falls back to More; the library has no duplicate global capture overlay.
- [x] Store primitive library-capture origin metadata on the normal editor entry; carry the created event ID back only when a restricted Memory capture succeeds. Defer and retain that result while the Memory route is behind its lock gate. Show Memory saved with View after authorized content is visible, opening the saved record through normal protected navigation. Avoid duplicate generic saved feedback and leave other origins unchanged.
- [x] Keep reading position after save/cancel; do not auto-scroll/open details. Recheck access before honoring View; use existing missing-detail behavior if the record vanished. Deleted/Unmarked/cancelled/failed outcomes never show saved feedback.
- [x] Classify the typed pending destination and route context as Memories. Preserve additional Journal/Mood filtering from Task 1. Add the route to inherited top-level browse context and aggregate screenshot protection.
- [x] Mount the existing lock gate before private content. Cancelled unlock on the library returns to More. Preserve whole-app/restricted-capture rules, and clear state/depth/anchors when authorization changes.
- [x] Inspect M01–M06 coverage, exhaustive typed-destination handling, unchanged weekly Review and preservation of existing workspace changes.

**Deliverable:** More → Memory → reading/capture with clear confirmation and existing privacy controls.

## Source handoff

- [x] Record touched paths, extracted helpers and any departure from revision 2.
- [x] Perform source/document checks only: touched-diff whitespace, XML parsing/duplicate-name inspection and local links. These do not establish compilation/runtime correctness.
- [x] Mark **Source implemented; behavior unverified** only when Tasks 1–4 are actually implemented. Until then retain **Revised proposal, not executed**.

### Implementation handoff

- Touched the paths in the file map. Added `MemoryLibraryState.kt` for bounded load state/grouping and `MemoryLibraryRoute.kt` for the list/card presentation and saved scroll metadata.
- No dependency, permission, schema, migration, or archive change. Weekly Review and event detail/editor screens remain as they were.
- Lock-enabled library capture follows the existing restricted-capture contract; unlocked library capture opens the normal Memory editor. The restricted service now returns the created event ID so both paths can use the library's saved/View feedback.
- Restricted capture reports the actual saved capture kind; only Memory results from the library's requested Memory action produce library feedback. Pending feedback waits for the Memory gate to clear and rechecks access before View.
- The linked worktree setup was blocked by read-only `.git`; changes were made in the current checkout without modifying unrelated pre-existing files.
- Source/document checks only. No tests, build, lint, emulator/device, acceptance, or release verification was performed.

No test authoring/execution, builds, lint, device/emulator work, acceptance result or release task is included. Implementation is complete at source level; runtime behavior remains unverified.
