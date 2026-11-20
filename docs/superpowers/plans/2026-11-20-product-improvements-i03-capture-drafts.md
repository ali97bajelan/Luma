# IP-I03 — Dependable capture drafts Implementation Plan

> **For agentic workers** Use the applicable Superpowers implementation skill when this plan is authorized for execution. Steps use checkbox syntax for tracking.

**Goal** Preserve an interrupted Mood or Memory draft with its original date and valid attachments until save or explicit discard.

**Architecture** Align existing state paths and add bounded app-private persistence only if the Memory restoration guarantee needs it. A new general draft service would duplicate the Mood and restricted-capture mechanisms. Make cleanup conditional on actual ownership.

**Tech stack** Kotlin, Jetpack Compose, Luma components and existing route/state/repository contracts.

**Spec** [IP-I03 specification](../specs/2026-11-20-product-improvements-i03-capture-drafts-design.md) · [Task index](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md) · [IP-Q01 scenario](./2026-11-20-product-improvements-q01-emulator-qa.md#qa-03)

## Files and interfaces

Paths are from the project root. Unqualified sibling filenames share the preceding directory.

- `app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorViewModel.kt + MoodEditorScreen.kt + FileMoodDraftStore.kt` — Existing save identity, persistent status and recovery UI.
- `app/src/main/java/com/luma/lifeos/ui/memory/MemoryEditorViewModel.kt + MemoryEditorRoute.kt + MemoryEditorScreen.kt` — Entry identity, restoration/disclosure, save/retry and discard.
- `app/src/main/java/com/luma/lifeos/ui/photos/PhotoDraftSession.kt + app/src/main/java/com/luma/lifeos/lock/PhotoDraftAccessRegistry.kt` — Photo reattach, lease checks and cleanup; touch only if gap requires it.
- `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt` — Capture entry identity, lock/restore generation and return paths if affected.
- `core/domain/src/main/kotlin/com/luma/lifeos/domain/repository/MemoryRepository.kt` and `MoodRepository.kt` — Add a conditional editor save contract that carries the original baseline and returns a typed stale/missing result.
- `core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomMemoryRepository.kt` and `RoomMoodRepository.kt` — Compare the authorized original aggregate or an existing version token and apply the mutation within one transaction.
- `app/src/main/java/com/luma/lifeos/lock/ProtectedEventRepositories.kt` — Guard the conditional writes with the current access checks.
- Update string resources and plurals in `app/src/main/res/values/`, `values-fr/` and `values-fa/` for affected copy.

Use Mood’s existing saved draft/status and the Memory editor’s existing capture/event ID and PhotoDraftSession. Preserve openCapture’s authorization and initialDate handoff.

## Global constraints

Existing Mood/Memory editors, protected capture and photo ownership. IP-I05 and IP-I09 consume the editor behavior. Apply the [shared implementation contract](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md#shared-implementation-contract). Record source handoff before IP-Q01.

## Review focus

An interrupted create can have committed before the editor receives the acknowledgement. Resolve the existing ID before retry.

## Implementation checklist

- [ ] **1.** Map ownership and lifetimes for capture ID, source ID, occurrence instant, saved fields, pending save and photos for new/edit and lock/unlock. Record source-proven behavior and unverified transitions in the handoff. Identify a reproducible pre-IP-I03 SHA and dirty patch before the first change, including only versioned source and synthetic fixtures in the QA baseline.
- [ ] **2.** Fill source-confirmed gaps using the current stores and editor ViewModels. If a durable Memory payload is needed, keep bounded, versioned app-private no-backup staging keyed to the existing editor/session identity, gated on authorized restore. Keep the original editable baseline across recreation and add guarded conditional writes for Mood, direct Memory and promoted markers. Resuming a pending new capture uses the same-ID create contract. For conflicting payloads/missing original sources retain the draft and show explicit conflict/reload recovery.
- [ ] **3.** Expose restored/unsaved/storage-failed feedback and keep Save/Discard reachable. Keep photo cleanup scoped to the draft and the existing protected capture store separately owned.
- [ ] **4.** Review source and localization against IP-I03-R1, IP-I03-R2, IP-I03-R3. Record the changed files, interface decisions, risks and source evidence for IP-Q01.

## QA handoff

- [ ] Map `interruptedCaptureRestoresOccurrenceAndValidAttachments`, `saveRetryWithSameIdentityCreatesOneRecord`, `discardOnlyRemovesDraftOwnedPhotos` to [QA-03](./2026-11-20-product-improvements-q01-emulator-qa.md#qa-03) for the final verification task. Extend the existing test surfaces where appropriate. MemoryEditorTest; PhotoDraftSessionTest; existing Mood editor suites.
- [ ] Record source status as **SOURCE COMPLETE — QA PENDING** after handoff and leave the QA result **NOT RUN** until IP-Q01 executes.

**Acceptance evidence** Use new and edit Mood/Memory, non-current dates, valid/shared/missing photos and a sentinel saved record. Run rotate, language change, task-background process kill with Recents recreation, picker cancel, low storage, failed/duplicate save, stale edit, discard and lock/relock. Keep force-stop, task removal and device reboot outside the general SavedStateHandle recovery promise unless separately evidenced.
