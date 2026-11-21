# App Lock Coverage Source Handoff

**Status: SOURCE COMPLETE — QA PENDING**

Spec: [App Lock coverage and Memory performance](../superpowers/specs/2026-11-21-app-lock-coverage-design.md)  
Plan: [App Lock Coverage Implementation Plan](../superpowers/plans/2026-11-21-app-lock-coverage.md)

## Candidate identity

- Base/source Git SHA: `43c32174c9b2bbd7f277f2ad58a050820cefab52`
- Source-only handoff diff SHA-256 (before QA fixture updates): `6d416acab006e519be4dd7168a4e39c53854c8273074f36adc23d038b793cf0d`
- Added source SHA-256:
  - `EventClassificationRow.kt`: `90edfd617315e3a8cba18cc3a1d1daf1b825bc6013367daea935db31bd64fa91`
  - `EventVisibility.kt`: `d50c3baf0802349f4d2697b6fff6c8096781cdea8725f87402b6dc727cf556f3`
  - `EventVisibilityQueries.kt`: `d9a34c2528ac502ddcbfbeeec7cd94d16f6f1984634ac3d5bd0fab897661e95d`
  - `EventVisibilityTest.kt`: `c7bd33411c6b53cba0158eb0bf7d50bcff02e4f7ff2f5b49e55d87ac1f858b01`
- The spec and plan are user-provided untracked task inputs; they are not included in the source-only diff digest.

## Implemented source

- The domain policy now has exactly `WHOLE_APP` and `MEMORY_SECTION`, with protected classifications derived internally. Editor creates use a separate serialized authorization operation.
- The authenticated local policy payload now writes the canonical format and translates legacy v1/v2 scope values conservatively. Broadened legacy policies retain a one-time notice until the user acknowledges it. Credential, recovery, retry, relock, screenshot, and generation data remain represented.
- App Lock settings display only Whole app and Memory section. The settings screen uses the existing Luma shared theme, components and spacing tokens and includes the localized migration notice.
- Memory-only capture remains write-only while locked; normal Task, Habit and standalone Mood creation stays available in Memory-section mode. Existing Memory creation and existing-record updates remain covered by serialized checks.
- The data layer exposes `EventVisibility.ALL/WITHOUT_MEMORY` and `EventVisibilityQueries`. Visibility is applied before page/count selection, sorting and detail hydration for mixed timeline, feed, search, review, calendar event and People/Places history reads. Daily and weekly aggregates exclude Memory contributions without publishing into the unfiltered daily cache.
- Protected repository facades gate query start and recheck the authorization epoch before publishing. Memory tag/catalog observers start only with access; stale Search tag filters are removed on revocation. Metadata-only classification supports route and commit checks without hydrating a full record.
- Application wiring supplies the visibility-aware data source to the protected facades. No event, archive, photo or draft schema/data rewrite was introduced.

## Source paths

- App wiring and routes: `app/src/main/java/com/luma/lifeos/LumaApplication.kt`, `MainActivity.kt`, `ui/LifeOsApp.kt`, `ui/privacy/AppLockSettingsRoute.kt`, `ui/privacy/AppLockViewModel.kt`, `ui/search/SearchViewModel.kt`.
- App Lock policy, migration, navigation/capture and protected facades: `app/src/main/java/com/luma/lifeos/lock/AppLockCoordinator.kt`, `DefaultLockCredentialService.kt`, `LocalLockStore.kt`, `LockActionGuard.kt`, `LockCredentialService.kt`, `ProtectedAggregateRepositories.kt`, `ProtectedCatalogRepositories.kt`, `ProtectedContentGateway.kt`, `ProtectedEventRepositories.kt`.
- Domain: `core/domain/src/main/kotlin/com/luma/lifeos/domain/lock/LockAccess.kt`, `LockPolicy.kt`, `ProtectedRecordClassifier.kt`.
- Room/data: `core/data/src/main/kotlin/com/luma/lifeos/data/LumaStorage.kt`, `dao/CatalogDao.kt`, `dao/LifeEventDao.kt`, `entity/EventClassificationRow.kt`, `habit/HabitTimelineSql.kt`, `repository/EventVisibility.kt`, `repository/EventVisibilityQueries.kt`, `repository/RoomDailySummaryRepository.kt`, `repository/RoomWeeklyReviewRepository.kt`, `review/ReviewSql.kt`, `search/SearchSql.kt`.
- Localization and existing test-source migration: `app/src/main/res/values/strings.xml`, `values-fr/strings.xml`, `values-fa/strings.xml`; `app/src/androidTest/java/com/luma/lifeos/AppNavigationTest.kt`, `lock/LocalLockStoreTest.kt`, `lock/LockCredentialIntegrationTest.kt`; `core/domain/src/test/kotlin/com/luma/lifeos/domain/lock/AppLockFixture.kt`, `LockPolicyTest.kt`.
- Current settings QA scenario: `docs/qa/scenarios/settings-integrations-widgets.md`.

## Static checks and Gradle results

- IDE inspections covered the changed Kotlin source and existing test-source edits: no errors remained after corrections. Remaining output consisted of style/complexity warnings.
- `git diff --check`: PASS.
- Repository search for `LockCoverage.SELECTED` and the former settings tag found no Kotlin/XML implementation matches.
- App Lock unit tests: 6/6 PASS. `SearchSqlHostTest`, `ReviewSqlHostTest`, and `ReliabilitySqlHostTest` PASS, including the filtered paging/count assertions and updated timeline bind fixture.
- `:core:domain:test`: PASS (UP-TO-DATE).
- `:core:data:testDebugUnitTest`: 43 tests, 4 failures in backup/archive validation (`BackupCodecTest`, `BackupSqlHostTest`, two `BackupValidationTest` cases). The remaining data tests pass.
- `:app:testProductionDebugUnitTest`: 352 tests, 11 failures outside the App Lock package: one French crop-format catalog assertion; eight Memory editor draft/photo restoration assertions; one Mood editor restoration assertion; and one Search detail-return scroll restoration assertion.
- `:app:assembleProductionDebugAndroidTest`: PASS; updated Focus and widget instrumentation sources compile.
- `:core:data:assembleDebugAndroidTest`: PASS; `EventVisibilityTest` compiles. This only assembles the test APK and does not execute it.
- `:app:lintProductionDebug`: PASS.
- Full verification command: `./gradlew --continue :core:domain:test :core:data:testDebugUnitTest :app:testProductionDebugUnitTest :app:assembleProductionDebugAndroidTest :app:lintProductionDebug` — exits nonzero because of the two unit-test tasks above. Follow-up commands: `./gradlew :core:data:testDebugUnitTest` (four backup failures remain) and `./gradlew :core:data:assembleDebugAndroidTest` (PASS). `adb devices -l` returned no connected devices, so package isolation could not be checked and Room instrumentation execution, connected UI tests, API matrix, accessibility/device checks and release-like performance tracing remain **NOT RUN**. No runtime or device performance result is claimed.

## QA mapping

Source coverage includes authorization and migration tests, host Search/Review SQL visibility checks, and Room fixtures for paging/count parity, Search, Review, habit feeds, filtered summaries, weekly review, People/Places histories and tag options. The new Room instrumentation test compiles but still requires an isolated device run. The plan's QA checklist remains open while unit suites fail and connected, accessibility, API 28/API 36, and performance evidence is outstanding. The numeric performance targets in the spec are unmeasured.
