# Accessible Feedback and Capture Disclosures Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete field/error/heading accessibility and identify optional capture content before expansion.

**Architecture:** Extend existing editor state with field rejection identities; share only visible feedback rendering. Keep current screens and operations, add semantics at the responsible node, and present local or fixed-action feedback according to the spec.

**Tech Stack:** Kotlin, SavedStateHandle, Compose Material 3 semantics, BringIntoViewRequester, JUnit 4, Compose instrumentation.

**Spec:** [UX 14/16 design](../specs/2026-10-14-ux-14-16-feedback-capture-design.md).

**Status:** Proposed plan; implementation has not started. Read the [master handoff](../../UX_DEVELOPMENT_READY_2026-10-14.md) and [acceptance checklist](../../UX_ACCEPTANCE_2026-10-14.md). Apply final Search wiring after UX13.

## Global Constraints

- Keep `minSdk = 28`, `compileSdk = 35`, `targetSdk = 35`, JDK 17, AGP 8.7.3, Kotlin 2.2.10 and Compose BOM 2025.08.01.
- Add no product dependency, database migration, permission, network access, telemetry or AI service.
- Keep English, Persian and French available offline; localize all new labels, dates, numbers and semantic descriptions.
- Preserve user-authored text, stable IDs, raw durations, occurrence timestamps and timezone rules.
- Preserve unsaved-change confirmations, rejected pending-tag save blocking, photo retention, read-only imported records and retry behavior.
- Missing observations stay unavailable; recorded zero stays zero; positive durations never display as zero.
- Every interactive control has an accessible name and at least a 48 dp touch target. Text wraps or ellipsizes without reducing the configured font scale.
- Automated semantics checks do not certify TalkBack speech. Manual acceptance remains pending until evidence is recorded.

## Review Focus

- A retained-value TextField callback must not erase rejected-edit feedback: Task 1 existing journal regression plus every field type.
- A second rejected field must not replace the first error or be cleared by unrelated edits: Task 1 multiple-field test.
- A save failure while scrolled to the bottom needs visible feedback with the keyboard open: Task 2 fixed-action test.
- A photo error under collapsed context must reveal its local retry without losing file leases: Task 2 photo failure test.
- A reset request accepted by Android must never be presented as verified deletion or replayed: Task 3 fake-reset test and existing PrivacyViewModel suite.

---

### Task 1: Field identities, correction and restoration

**Files:**
- Modify: `app/src/main/java/com/luma/lifeos/ui/journal/JournalEditorViewModel.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/memory/MemoryEditorViewModel.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/people/PersonEditorViewModel.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/places/PlaceEditorViewModel.kt`.
- Modify: corresponding existing `app/src/test/java/com/luma/lifeos/ui/journal/JournalEditorTest.kt`, `app/src/test/java/com/luma/lifeos/ui/memory/MemoryEditorTest.kt`, `app/src/test/java/com/luma/lifeos/ui/people/PersonEditorTest.kt`, `app/src/test/java/com/luma/lifeos/ui/places/PlaceEditorTest.kt`.

**Interfaces:**
- Consumes: existing field setters, ready/busy/completed checks, domain text limits and draft SavedStateHandle.
- Produces: feature-local field enums and `textErrors: Set<...>` on each existing state, as listed in the spec. Public setter/save signatures remain unchanged. Persist enum names under `text_error_fields` in each own handle.

- [ ] **Step 1: Add failing ViewModel tests using each suite's current fake repository/setup.** Name tests `rejectionIdentifiesFieldAndKeepsAcceptedText`, `sameValueAndUnrelatedEditKeepRejection`, `twoRejectedFieldsClearIndependently`, `recreationRestoresFieldFlags`, `unknownRestoredFieldNameIsIgnored`. Journal adds `acceptedTagClearsOnlyTagFlag`. Example assertions after constructing a ready journal VM using its existing fixture:

```kotlin
vm.setTitle("Accepted"); vm.setBody("Body")
vm.setTitle("x".repeat(201)); vm.setBody("x".repeat(50_001))
assertEquals("Accepted", vm.state.value.draft.title)
assertEquals("Body", vm.state.value.draft.body)
assertEquals(setOf(JournalTextField.TITLE, JournalTextField.BODY), vm.state.value.textErrors)
vm.setTitle("Accepted")
assertTrue(JournalTextField.TITLE in vm.state.value.textErrors)
vm.setTitle("Corrected")
assertEquals(setOf(JournalTextField.BODY), vm.state.value.textErrors)
```

Repeat relevant cases at memory title 201/note 10,001 and person/place name 201/note 10,001. Run scheduling as the existing tests do. Recreate each VM with its same handle; assert flags and accepted draft survive. Do not store rejected pasted content. Reject TAG at 51 chars or 51st distinct tag, accept a corrected/duplicate tag, assert other field flags remain. Existing pending-tag Save eligibility and operation-error regressions stay in place.

- [ ] **Step 2: Run `bash gradlew :app:testDebugUnitTest --tests 'com.luma.lifeos.ui.journal.*' --tests 'com.luma.lifeos.ui.memory.*' --tests 'com.luma.lifeos.ui.people.*' --tests 'com.luma.lifeos.ui.places.*'`.** Expected initial missing field-type/property failures, then behavioral failures until identities persist/clear correctly.
- [ ] **Step 3: Implement state flags and field-specific rejection.** Add private `rejectText(field: JournalTextField): Unit`, `rejectText(field: MemoryTextField): Unit`, `rejectText(field: PersonTextField): Unit`, and `rejectText(field: PlaceTextField): Unit` in their corresponding ViewModels; accepted changes clear only that field when its value changes. Successful tag acceptance clears TAG. Preserve readiness guards and generic operational enums; prevent generic change/photo-update paths from erasing independent field flags. Restore known enum names and persist on each flag mutation.
- [ ] **Step 4: Re-run the same editor JVM command.** Expected PASS including photo draft/tag/rejection tests; no changed save eligibility beyond current rejected pending-tag guard.
- [ ] **Step 5: Commit task files.** Suggested message: `fix(editors): retain field-specific validation feedback`.

### Task 2: Capture headings, disclosures and local/action feedback

**Files:**
- Create: `app/src/main/java/com/luma/lifeos/ui/common/AccessibleFeedback.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/common/EditorTopBar.kt`, `app/src/main/java/com/luma/lifeos/ui/mood/MoodTimeControls.kt`, `app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorScreen.kt`, `app/src/main/java/com/luma/lifeos/ui/journal/JournalEditorScreen.kt`, `app/src/main/java/com/luma/lifeos/ui/memory/MemoryEditorScreen.kt`, `app/src/main/java/com/luma/lifeos/ui/people/PersonEditorRoute.kt`, `app/src/main/java/com/luma/lifeos/ui/places/PlaceEditorRoute.kt`, `app/src/main/java/com/luma/lifeos/ui/people/PersonPicker.kt`, `app/src/main/java/com/luma/lifeos/ui/places/PlacePicker.kt`, `app/src/main/java/com/luma/lifeos/ui/photos/PhotoGallery.kt`, all under `app/src/main/java/com/luma/lifeos/`.
- Modify: all three `app/src/main/res/values*/strings.xml` language bundles.
- Create: `app/src/androidTest/java/com/luma/lifeos/CaptureAccessibilityTest.kt`.
- Modify: `app/src/androidTest/java/com/luma/lifeos/JournalEditorScreenTest.kt`, `app/src/androidTest/java/com/luma/lifeos/MoodCaptureTest.kt`, `app/src/androidTest/java/com/luma/lifeos/JournalCaptureTest.kt`, `app/src/androidTest/java/com/luma/lifeos/MemoryCaptureTest.kt`, `app/src/androidTest/java/com/luma/lifeos/PhotoCaptureTest.kt`.

**Interfaces:**
- Consumes: Task 1 field flags, current error enums, existing callback slots and `PhotoEditor(..., importFailed: Boolean)`.
- Produces: `@Composable fun AccessibleFeedback(message: String, modifier: Modifier = Modifier, isError: Boolean = true)`, polite/error semantics; spec copy, heading/state semantics, field supporting errors and fixed-bottom operation feedback. Existing screen/route signatures stay unchanged.

- [ ] **Step 1: Add failing Compose tests.** Render ready states with createComposeRule/LifeOsTheme; use mutable state to transition into errors. Test `editorSectionsAreHeadings`, `disclosureNamesContentsAndKeepsTimestampVisible`, `fieldErrorsBelongToFieldNodes`, `saveFailureStaysVisibleWithScrolledBody`, `photoFailureRevealsContextAndRetry`, `timestampDialogHasOneAnnouncementOwner`. Use explicit matchers rather than pixel snapshots alone:

```kotlin
compose.onNodeWithTag("journal_body").assert(
    SemanticsMatcher.expectValue(SemanticsProperties.Error,
        "Use at most 50,000 characters. Your previous text has been kept."))
compose.onNodeWithTag("mood_more").assertTextContains("Note and context")
compose.onNodeWithTag("mood_date").assertIsDisplayed()
compose.onNodeWithTag("mood_more").performClick().assert(
    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
compose.onNodeWithTag("mood_note").assertIsDisplayed()
```

Resolve localized expected expanded-state text from resources, since existing copy may use different capitalization. For operation failures, enter a long body, scroll to bottom, set SAVE error, assert the message and Save action are displayed together. For photo failure, use existing PhotoCaptureTest fake failures and assert context expands, photo error/retry is visible and old attachment IDs remain. Inspect merged/unmerged nodes for exactly one polite owner per error; do not merge a whole form. Extend rejectedPendingTag test to assert field Error semantics and retained pending input with zero Save calls.

- [ ] **Step 2: Build/run targeted failing instrumentation in the isolated app.** Expected failures concern missing heading/error/state semantics and absent descriptive copy, not fixtures.
- [ ] **Step 3: Implement shared feedback and capture wiring.** Apply exact spec disclosure/help copy in all languages. Add headings at specified nodes, field Error/supportingText semantics and BringIntoViewRequester where needed. TIME owns local/dialog feedback; PHOTO owns PhotoEditor feedback and opens context. Show SAVE/DELETE/REMOVE messages above fixed bottom actions. Keep local tag input and save guard. Add polite feedback to picker errors/limits and unavailable photo previews; suppress equivalent generic banners.
- [ ] **Step 4: Run editor JVM tests and `TranslationCatalogTest`; build app/instrumentation and lint.** Run `CaptureAccessibilityTest,JournalEditorScreenTest,MoodCaptureTest,JournalCaptureTest,MemoryCaptureTest,PhotoCaptureTest` in the disposable app. Expected PASS and no new lint errors. Check 320 dp/scale 2.0/RTL and keyboard; record TalkBack as manual until tested.
- [ ] **Step 5: Commit task files.** Suggested message: `feat(capture): clarify optional context and expose accessible feedback`.

### Task 3: Privacy headings and settled status announcements

**Files:**
- Modify: `app/src/main/java/com/luma/lifeos/ui/privacy/PrivacySettingsRoute.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/search/SearchRoute.kt`, `app/src/main/java/com/luma/lifeos/ui/search/SearchFilters.kt`, `app/src/main/java/com/luma/lifeos/ui/search/SearchOptionPicker.kt`; `app/src/main/java/com/luma/lifeos/ui/review/ReviewOverview.kt`, `app/src/main/java/com/luma/lifeos/ui/review/WeeklyReviewRoute.kt`, `app/src/main/java/com/luma/lifeos/ui/review/ReviewSourcesRoute.kt`, `app/src/main/java/com/luma/lifeos/ui/review/ReviewUsageRoute.kt`; `app/src/main/java/com/luma/lifeos/ui/usage/UsageSettingsRoute.kt`; `app/src/main/java/com/luma/lifeos/ui/health/HealthSettingsCard.kt`, all under the same app main Java root.
- Create: `app/src/androidTest/java/com/luma/lifeos/FeedbackAccessibilityTest.kt`.
- Modify: existing `app/src/androidTest/java/com/luma/lifeos/privacy/PrivacySettingsFlowTest.kt`, `app/src/androidTest/java/com/luma/lifeos/SearchFlowTest.kt`, `app/src/androidTest/java/com/luma/lifeos/review/WeeklyReviewFlowTest.kt`, `app/src/androidTest/java/com/luma/lifeos/usage/UsageSettingsFlowTest.kt`, `app/src/androidTest/java/com/luma/lifeos/health/HealthSettingsFlowTest.kt` as needed for settled update assertions.
- Test: existing `app/src/test/java/com/luma/lifeos/privacy/PrivacyViewModelTest.kt`.

**Interfaces:**
- Consumes: Task 2 `AccessibleFeedback`, current route states and existing retries; UX13 named-filter section.
- Produces: Privacy/Review heading semantics, polite settled error/result/import/reset text and date-range Error semantics. No repository, permission or reset interface changes.

- [ ] **Step 1: Add failing semantic tests using fake services.** Test `privacySectionsSupportHeadingNavigation`, `resetAcceptanceAnnouncesRequestOnly`, `searchAnnouncesOnlyCurrentSettledCount`, `invalidDatesExposeErrorsOnEndpointControls`, `retryAndImportStatusArePolite`. Assert `SemanticsProperties.Heading` exists on four Privacy section titles; assert `SemanticsProperties.LiveRegion == LiveRegionMode.Polite` on error/status nodes. Inject fake reset returning accepted, click confirmation once, assert one call, existing request-accepted copy and no erasure-complete copy. Use existing fake search delayed generations to prove old counts are not announced. Capture settled usage/health outcome using fake integrations; do not grant provider permissions.
- [ ] **Step 2: Run targeted new instrumentation and `PrivacyViewModelTest`.** New semantic tests should fail on the unchanged UI; existing reset behavior tests must already pass.
- [ ] **Step 3: Apply the spec's announcement ownership table.** Mark Review overview and Privacy headings. Use AccessibleFeedback for relevant failure blocks and polite semantics on settled success/result text; attach date errors to date controls. Keep existing Material dialog semantics. Do not add announcements to per-frame progress, every keystroke or all list items. Do not change reset/permission calls or privacy policy content.
- [ ] **Step 4: Run `bash gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` and targeted suites listed above in the isolated app.** Expected PASS; review A14/C16 checklist and manually verify TalkBack heading traversal, disclosure state, correction/retry and no repeated announcements. Log checks not run as pending.
- [ ] **Step 5: Record findings 14/16 implementation status separately from manual acceptance and commit.** Suggested message: `feat(accessibility): complete privacy headings and settled feedback`.
