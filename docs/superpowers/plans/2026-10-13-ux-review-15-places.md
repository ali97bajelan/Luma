# UX Review 15: People and Places Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Places as discoverable as People and show the saved entries linked to each place.

**Architecture:** Keep the People primary destination and add a saveable People/Places selector. Extend PlaceRepository with a committed place-and-events history observation backed by the existing event-place relation; render the history in place details and open records through the current event callback.

**Tech Stack:** Kotlin, Room relations and transactions, Coroutines Flow, Jetpack Compose Material 3, current event/place domain models.

**Spec:** [docs/superpowers/specs/2026-10-13-ux-review-08-09-15-design.md](../specs/2026-10-13-ux-review-08-09-15-design.md), People and Places catalog section.

## Global Constraints

- Keep People and Places under the existing People primary destination; do not add a bottom-navigation item.
- Preserve catalog create/edit/delete behavior, capture selection behavior, empty/loading/error/retry states, and place coordinates.
- Place deletion removes catalog links only; it never deletes linked life events or their content.
- Show place history oldest first and open each item through the existing event action.
- Use existing tables and event-place links; do not add a migration, location permission, tracking, map, or dependency.
- Localize new selector copy in English, Persian, and French and preserve keyboard/TalkBack/RTL behavior.

## Review Focus

- Missing/deleted place: history emits Missing rather than stale records. Test in `PlacesStateTest.historyDistinguishesMissingFromLoadingAndError`.
- Event changes and link changes: the observed history refreshes as one committed snapshot. Test in `PlacePersistenceTest.placeHistoryTracksCommittedLinkAndEventChanges`.
- Chronology ties: events are oldest first and use `LifeEvent.chronologicalOrder` for stable equal-time order. Test in `PlacePersistenceTest.placeHistoryUsesStableChronologicalOrder`.
- Place deletion: linked event content remains after the catalog link is removed. Keep and extend `PlacePersistenceTest.placeDeletionKeepsEntireEventGraphExceptItsPlaceLink`.
- Catalog selection and return from place creation: Places remains selected after editor save/cancel and restoration. Test in `PlaceCatalogFlowTest.placesSelectionSurvivesCreationAndRestoration`.

---

### Task 1: Add transactional place history to domain and Room

**Files:**
- Modify: `core/domain/src/main/kotlin/com/luma/lifeos/domain/model/Place.kt`
- Modify: `core/domain/src/main/kotlin/com/luma/lifeos/domain/repository/PlaceRepository.kt`
- Create: `core/data/src/main/kotlin/com/luma/lifeos/data/entity/PlaceWithEvents.kt`
- Modify: `core/data/src/main/kotlin/com/luma/lifeos/data/dao/CatalogDao.kt`
- Modify: `core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomPlaceRepository.kt`
- Modify: `core/data/src/androidTest/kotlin/com/luma/lifeos/data/PlacePersistenceTest.kt`

**Interfaces:**
- Consumes: `Place`, `EventWithDetails.toRecord()`, and `LifeEvent.chronologicalOrder`.
- Produces: `data class PlaceHistory(val place: Place, val events: List<LifeEventRecord>)`; `PlaceRepository.observeHistory(id: UUID): Flow<PlaceHistory?>`; and transactional `CatalogDao.observePlaceHistory(id: String): Flow<PlaceWithEvents?>`.

- [ ] **Step 1: Add the failing Room history tests.** Assert one consistent PlaceHistory snapshot, oldest-first ordering with equal-time ID tie-break, live place rename, linked-event edits, link removal, null after place deletion, and event retention after deletion.
- [ ] **Step 2: Run the focused data instrumentation test.** Run `./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.data.PlacePersistenceTest`; expect the new repository API/test compile to fail.
- [ ] **Step 3: Add `PlaceHistory` and the `observeHistory` contract.** Keep the repository result null when its place row no longer exists.
- [ ] **Step 4: Add `PlaceWithEvents` and `CatalogDao.observePlaceHistory`.** Use `@Transaction` and a Room junction from `PlaceEntity.id` through `EventPlaceEntity.place_id` to `LifeEventEntity.id`, with `EventWithDetails` as the related graph.
- [ ] **Step 5: Implement `RoomPlaceRepository.observeHistory`.** Map the place and records in the same emitted snapshot and sort records with `LifeEvent.chronologicalOrder`.
- [ ] **Step 6: Run the focused Room tests.** Run `./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.data.PlacePersistenceTest` when a device is attached; expect green tests. Run `./gradlew :core:data:compileDebugKotlin` and `./gradlew :app:assembleDebug`.
- [ ] **Step 7: Commit the task.** Commit only place-history model, repository, DAO, relation, and persistence tests.

### Task 2: Render place history and expose Places from the People catalog

**Files:**
- Modify: `app/src/main/java/com/luma/lifeos/ui/places/PlacesState.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/places/PlacesRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/people/PeopleRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt`
- Modify: `app/src/test/java/com/luma/lifeos/ui/places/PlacesStateTest.kt`
- Update: `app/src/test/java/com/luma/lifeos/ui/places/PlaceEditorTest.kt` and the `PlacesStateTest` repository fake for `observeHistory`.
- Update: `app/src/androidTest/java/com/luma/lifeos/PlaceCaptureTest.kt` to enter Places through the catalog selector.
- Test: `app/src/androidTest/java/com/luma/lifeos/PlaceCatalogFlowTest.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-fa/strings.xml`
- Modify: `app/src/main/res/values-fr/strings.xml`

**Interfaces:**
- Consumes: Task 1 `PlaceRepository.observeHistory` and existing `PlacesRoute` list/editor callbacks.
- Produces: `PlaceHistoryState` with Loading, Error, Missing, and `Data(PlaceHistory)`; `observePlaceHistory(repository: PlaceRepository, id: UUID): Flow<PlaceHistoryState>`; a shared places-list content composable; and `PeopleRoute(people, places, onCreatePerson, onPerson, onCreatePlace, onPlace)` with saveable People/Places selection.

- [ ] **Step 1: Add failing state tests.** Assert history emissions map to Loading/Data/Missing/Error and cancellation propagates instead of becoming Error; update fake PlaceRepository implementations for the new method.
- [ ] **Step 2: Run focused state tests.** Run `./gradlew :app:testDebugUnitTest --tests 'com.luma.lifeos.ui.places.PlacesStateTest'`; expect the new state and fake method to be missing.
- [ ] **Step 3: Implement `PlaceHistoryState` and `observePlaceHistory`.** Mirror the existing place observation flow and preserve cancellation.
- [ ] **Step 4: Add the failing catalog/detail UI tests.** Verify the selector switches People/Places, each list keeps its own create and empty states, place detail shows history in chronological order, opening an entry invokes `onEvent`, and the Places selection survives creation return and saved-state restoration.
- [ ] **Step 5: Run the focused catalog instrumentation test.** Run `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.PlaceCatalogFlowTest`; expect selector and history nodes to be absent.
- [ ] **Step 6: Extract shared Places list content and add the saveable selector to `PeopleRoute`.** Keep the existing standalone `PlacesRoute` behavior for callers that need a Back action.
- [ ] **Step 7: Update `PlaceDetailRoute` to render `PlaceHistory` and route tapped records through `onEvent`.** Use a lazy list, oldest-first records, and the current loading/missing/error/retry behavior.
- [ ] **Step 8: Wire `LifeOsApp` callbacks and restore the Places selector after create/cancel.** Keep the existing place editor and detail route identities.
- [ ] **Step 9: Add localized selector labels and the place-history heading/empty text.** Check English, French, Persian RTL, keyboard navigation, and larger text.
- [ ] **Step 10: Run focused Room, state, UI, and app compile checks.** Run the Task 1 Room command, `./gradlew :app:testDebugUnitTest --tests 'com.luma.lifeos.ui.places.PlacesStateTest'`, and `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.PlaceCatalogFlowTest,com.luma.lifeos.PlaceCaptureTest` when a device is attached; expected result is green checks. Run `./gradlew :app:assembleDebug`.
- [ ] **Step 11: Commit the task.** Commit only catalog/detail UI, resources, wiring, fakes, and tests.
