# UX-011 — Creating a person/place during capture does not attach it

**Status:** Implemented; acceptance pending. Regression coverage has been added but not run. Proposed decisions below remain reviewable defaults, not previously approved product policy.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-011](../../UI_UX_AUDIT-20261020N.md#ux-011--creating-a-personplace-during-capture-does-not-attach-it)
**Priority / effort / confidence:** P2 — Medium / Medium / High (copied from the static audit; not a delivery estimate).
**Affected surface:** Mood, Journal and Memory context creation
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-011-created-context-results.md)
**Delivery phase:** 3 — navigation, scale and consistency

## Intent and success

Attach a person or place created during capture to the originating draft exactly once. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

The audit is source evidence, not reproduced runtime acceptance. Its recommendations are input to this specification, not executable instructions. Gradle/emulator checks and runtime exercises remain pending; no commit was created.

## Dependencies

None. Coordinate overlapping files using the index's integration order.

## Global constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies unless a task explicitly requires a reviewed exception.
- All changed user-facing copy must exist in EN/FR/FA with matching resource names and numbered placeholder signatures; preserve RTL and intended Gregorian display/storage behavior.
- Keep semantic light/dark palettes, at least 48 dp interactive targets, and reachability at 100%/130%/200% font, narrow portrait and short landscape.
- Preserve offline/private storage, existing consent boundaries, stable record/command IDs, original retry payloads and Undo protections.
- Never truncate accepted personal content, infer write failure means no commit, or treat an unavailable privacy mode as known.
- Only implement this task and its declared prerequisites; preserve unrelated working-tree changes and existing navigation/draft state.

## Behavior and policy

1. Created from a picker auto-attaches only to its recorded requesting draft. Use addPerson rather than togglePerson so duplicate delivery cannot deselect someone.
2. Respect PersonTextLimits.SELECTION = 100. At the limit retain the new catalog entry, preserve selections and report that it was created but could not be attached.
3. Consume a result only after the draft is ready and its attachment state is persisted. Missing created entities produce recoverable feedback rather than dangling links.
4. A created place replaces the draft's previous single place selection. Cancel, edit, delete and catalog-origin creation do not auto-attach to unrelated capture drafts. Announce attached/limit outcomes politely.

## Design and contract

Return typed catalog editor results via the calling back-stack entry's SavedStateHandle. Capture consumers attach by stable UUID; catalog consumers retain normal catalog navigation.

CatalogEditorResult has Created(id: UUID), Saved(id: UUID), Deleted(id: UUID). Each result channel also records PERSON or PLACE and requestId: UUID. New addPerson(id: UUID): Boolean on each capture ViewModel is idempotent; existing setPlace(id: UUID?): Unit handles places. Navigation stores primitive result fields keyed to the caller entry/request.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `createdPersonAttachesOnce` | Create a person from Mood, Journal or Memory; deliver the result twice. | The new UUID is selected exactly once and existing people remain selected. |
| AC-2 | `createdPlaceReplacesSelection` | Create a place while another place is selected. | The new place becomes the one selected place. |
| AC-3 | `selectionLimitIsHonest` | Create a person with 100 selected IDs. | The catalog entry exists; no selection is dropped and the attachment limit is announced. |
| AC-4 | `resultWaitsForReadyCaller` | Return while dependencies are loading or the draft is restoring. | The request stays pending and attaches after readiness; it is not lost or applied elsewhere. |
| AC-5 | `catalogAndCancelStayScoped` | Create from People & Places, cancel creation, or restore an old result after caller removal. | No unrelated draft changes and no duplicate catalog creation occurs. |

## Scope boundary

No changes to the 100-person limit, automatic attachment of edited/deleted items or replacing unrelated navigation state.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.

