# FLOW-006 — Unify populated card hierarchy

**Status:** Draft specification; implementation has not started. Policy choices are proposed defaults for review.
**Date:** 21 October 2026
**Source:** [App flow and UI/UX review](../../APP_FLOW_UX_REVIEW_2026-10-21.md), Visual directions 3, 4, and 5.
**Priority:** P2
**Plan:** [Implementation plan](../plans/2026-10-21-flow-006-content-card-hierarchy.md)
**Index:** [Task index and integration order](../../APP_FLOW_UX_REVIEW_2026-10-21-PLANS.md)

## Intent and success

Make personal content easier to scan by giving titles, body previews, metadata, and row actions stable visual roles. Success means the five acceptance scenarios below hold while the existing data and action guarantees remain intact.

## Evidence and current behavior

EventCard already uses 12 dp padding and 4 dp gaps; HabitTimelineCard uses 14 dp/5 dp, TodoRow has checkbox-specific spacing, and People/Places render their own cards. This is a presentation alignment task; already conforming components should be preserved.

The review contains a light emulator walkthrough, not a reproduction on the checked-out binary. This document does not claim new runtime verification or implementation.

## Dependencies

No hard prerequisite. Integrate after UX-019/UX-020 when changing EventCard call sites, and retain UX-021 type/gutter conventions.

## Global constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies.
- Localize changed copy in EN/FR/FA with matching resource names and numbered placeholders; preserve RTL and Gregorian storage/display policy.
- Keep semantic light/dark colors, interactive targets of at least 48 dp, and reachable content at 100%/130%/200% text in compact portrait and short landscape.
- Preserve private offline storage, consent boundaries, stable record/command IDs, original retry targets, accepted drafts, and Undo protections.
- Preserve unrelated edits, including the existing two-row limit and 4 dp internal gap in TodayHabitsCard.kt.
- Implement only this task and its declared prerequisites; no database migration, new provider permission, or dependency upgrade is included.

## Behavior and design decisions

1. Entry and catalog cards use 12 dp content padding, 4 dp metadata spacing, titleMedium for the content title, bodyMedium for preview text, and labelMedium/onSurfaceVariant for type/time/source metadata. Keep the existing Material surface palette and zero added decorative elevation.
2. Align HabitTimelineCard’s 14 dp/5 dp rhythm to 12 dp/4 dp. Keep its habit-specific completion text and source zone; numeric progress remains owned by FLOW-003.
3. Keep EventCard titles and body previews to their current two-line browse limits and preserve the original text in detail. Never truncate persisted data or drop memory/context/photo indicators.
4. Task titles remain bodyLarge beside the checkbox as an intentional form-control exception. Preserve TodoRow’s complete/open target separation and at least 48 dp target area; align supporting text to the title column without forcing checkbox rows into a generic clickable-card wrapper.
5. Names, notes, and metadata wrap within the shared reading width. A card tap keeps its actual detail/editor destination and labels from UX-020, and trailing memory/checkbox actions stay independently reachable. Settings navigation rows and Review metric layouts remain owned by UX-021 rather than this task.

## Interfaces and data ownership

Preserve EventCard, HabitTimelineCard, TodoRow, PeopleRoute/PeopleListContent, and PlacesListContent callback and identity contracts. Apply the listed Material roles and spacing at the existing sites; do not introduce a generic all-purpose card API or copy EventCard into other routes. If UX-020 has landed, retain its required openActionLabel parameter at every caller.

Only paths explicitly marked Create are new. Existing callbacks, field identities, and state owners remain the contract unless this section explicitly changes them. No stored record data is transformed by presentation changes.

## Acceptance scenarios

| ID | Case | Input/action | Required result |
|---|---|---|---|
| FLOW-006-AC-1 | `contentLeadsTheCard` | Inspect five populated entry/catalog cards. | Title/body hierarchy is clear and metadata is secondary with the specified Material roles. |
| FLOW-006-AC-2 | `existingDensityIsPreserved` | Use the existing EventCardTest compact and 130% fixtures. | Its populated-card viewport expectations still hold; no extra decorative padding displaces content. |
| FLOW-006-AC-3 | `secondaryActionsAreIndependent` | Activate one memory action and one task checkbox in existing fixtures. | The parent card does not also navigate and callback identity remains correct. |
| FLOW-006-AC-4 | `fullContentRemainsAvailable` | Open a card whose preview is ellipsized. | Detail retains the original complete text and context. |
| FLOW-006-AC-5 | `labelsWrapAtReadingWidth` | Inspect the existing RTL/long-label card fixture. | No control overlaps metadata or loses its readable/action label. |

## Scope boundary

No palette replacement, gradients, shadows, new card framework, record mutation, new truncation policy, card-to-editor routing changes, or redesign of Review metrics.

## Completion evidence

Use the matching plan's focused verification. A screenshot demonstrates appearance for its recorded build/configuration, not every device or assistive-technology state. Document unavailable checks as pending. No app tests, build, or implementation were performed while preparing this spec.
