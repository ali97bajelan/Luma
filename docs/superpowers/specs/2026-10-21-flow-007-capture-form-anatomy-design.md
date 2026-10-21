# FLOW-007 — Standardize capture and editor form layout

**Status:** Draft specification; implementation has not started. Policy choices are proposed defaults for review.
**Date:** 21 October 2026
**Source:** [App flow and UI/UX review](../../APP_FLOW_UX_REVIEW_2026-10-21.md), Visual direction 6; complements UX-012, UX-019, UX-021, and UX-023.
**Priority:** P2
**Plan:** [Implementation plan](../plans/2026-10-21-flow-007-capture-form-anatomy.md)
**Index:** [Task index and integration order](../../APP_FLOW_UX_REVIEW_2026-10-21-PLANS.md)

## Intent and success

Give each editor a clear primary input and a predictable optional-details area while keeping Save reachable and accepted drafts safe. Success means the five acceptance scenarios below hold while the existing data and action guarantees remain intact.

## Evidence and current behavior

The editors already share EditorTopBar, CaptureTimestampRow, 20 dp body gutters, scrollable content, and bottomBar imePadding. Journal tags are always expanded and its body currently starts at eight lines; Memory importance is always expanded. The task refines these existing components, not their repositories or draft stores.

The review contains a light emulator walkthrough, not a reproduction on the checked-out binary. This document does not claim new runtime verification or implementation.

## Dependencies

Requires UX-021’s header/inset contract and UX-012’s searchable context controls. Preserve UX-019 time policy and UX-023 disclosure names when integrated.

## Global constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies.
- Localize changed copy in EN/FR/FA with matching resource names and numbered placeholders; preserve RTL and Gregorian storage/display policy.
- Keep semantic light/dark colors, interactive targets of at least 48 dp, and reachable content at 100%/130%/200% text in compact portrait and short landscape.
- Preserve private offline storage, consent boundaries, stable record/command IDs, original retry targets, accepted drafts, and Undo protections.
- Preserve unrelated edits, including the existing two-row limit and 4 dp internal gap in TodayHabitsCard.kt.
- Implement only this task and its declared prerequisites; no database migration, new provider permission, or dependency upgrade is included.

## Behavior and design decisions

1. Use the validated UX-021 header/inset owner, a 20 dp form gutter and 12 dp spacing between field groups. Keep date/time compact before the primary input; apply no additional system or IME inset ownership.
2. Mood keeps score first, then emotions, with energy/stress/note/people/place in its existing optional context section. Journal keeps optional single-line title followed by a growing body with minLines = 6. Memory keeps optional title and a growing description with minLines = 4. Do not impose maximum body height or reduce existing accepted text limits.
3. Move Journal tags into a named, saveable Tags disclosure; keep selected tags summarized while collapsed. Retain tagText and the existing pending-tag flush/validation behavior. On a tag error, expand that group and bring its field into view before returning from Save.
4. Move Memory importance into a named optional disclosure, expanded initially for a saved importance value and with the selected rating summarized while collapsed. Preserve the 1–5 scale and tap-again-to-clear behavior. People/place/photo context stays behind its existing useful subject label and uses UX-012 selectors.
5. Task title, optional note and due date remain explicit; Habit name/unit/target/schedule/start date remain explicit with tracking-zone help available. Person/Place name and optional note remain simple visible fields. These four form families receive shared spacing/action alignment without new hidden required fields.
6. Keep the footer outside the scrolling body, own imePadding once, and retain all busy/dirty/save/delete guards. At available content width below 360 dp, stack secondary and primary actions with Save last and full width; at 360 dp or more, retain a wrapping-label row with Save taking remaining width. All actions remain at least 48 dp tall. Preserve direct-memory delete versus promoted-marker removal wording and confirmation behavior.

## Interfaces and data ownership

Preserve all seven editor callback signatures and ViewModel/draft contracts. Add @Composable internal fun EditorOptionalSection(@StringRes title: Int, tag: String, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, summary: (@Composable () -> Unit)? = null, content: @Composable () -> Unit): Unit in ui/common/EditorOptionalSection.kt. The caller owns expansion; the component exposes one subject-named toggle and expanded/collapsed state using the UX-023 copy/semantics contract. JournalEditorScreen owns saveable tagsExpanded and an initialized-once flag; initialize after state.ready from selected/pending tags, and expand before using the existing tagInputBringIntoViewRequester on rejection. MemoryEditorScreen owns equivalent importanceExpanded state initialized from draft importance only on its first ready state. Keep existing field/action tags and add journal_tags_toggle and memory_importance_toggle. Use localized plurals journal_tags_summary for the collapsed tag count and memory_importance_summary (“Importance: %1$s / 5”) for a selected rating. Required form state never lives inside the disclosure component.

Only paths explicitly marked Create are new. Existing callbacks, field identities, and state owners remain the contract unless this section explicitly changes them. No stored record data is transformed by presentation changes.

## Acceptance scenarios

| ID | Case | Input/action | Required result |
|---|---|---|---|
| FLOW-007-AC-1 | `primaryFieldsStayProminent` | Open each ordinary editor once. | The specified input order and field grouping are clear; simple required fields remain visible. |
| FLOW-007-AC-2 | `journalTagsRetainDraftAndError` | Enter a pending tag and use the existing Journal save/validation fixture. | Collapsed/expanded state never drops the pending tag; rejected tag input expands and focuses its group. |
| FLOW-007-AC-3 | `importanceRetainsItsValue` | Edit a memory with importance set, collapse the group, and save. | The saved rating remains and the collapsed summary names it; clearing is still explicit. |
| FLOW-007-AC-4 | `saveIsReachableWithKeyboard` | Inspect the footer below and above the 360 dp content-width threshold with the keyboard open. | Save stays reachable, actions remain at least 48 dp tall, and insets are applied once. |
| FLOW-007-AC-5 | `closeAndRemovalKeepTheirMeaning` | Use existing dirty/busy/marker-removal assertions after the layout change. | Accepted drafts, close guards, confirmations, and source-preserving marker removal are unchanged. |

## Scope boundary

No draft-store replacement, new text limits, automatic data deletion, photo viewer, tag normalization change, timestamp default change, or replacement of searchable-selector working-selection policy.

## Completion evidence

Use the matching plan's focused verification. A screenshot demonstrates appearance for its recorded build/configuration, not every device or assistive-technology state. Document unavailable checks as pending. No app tests, build, or implementation were performed while preparing this spec.
