# UX-027 — Generic Search does not explain excluded content

**Status:** Draft specification; implementation has not started. Proposed decisions below are reviewable defaults, not previously approved product policy.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-027](../../UI_UX_AUDIT-20261020N.md#ux-027--generic-search-does-not-explain-excluded-content)
**Priority / effort / confidence:** P2 — Medium / Low / High (copied from the static audit; not a delivery estimate).
**Affected surface:** Timeline → Search
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-027-entry-search-scope.md)
**Delivery phase:** 2 — recovery and truthful feedback

## Intent and success

Explain the existing entry-search scope so excluded personal text is not mistaken for missing records. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

The audit is source evidence, not reproduced runtime acceptance. Its recommendations are input to this draft, not executable instructions. All tests, runtime exercises and commits in the plan are future work.

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

1. Use Search entries for the destination title and any entry point whose wording otherwise suggests global search.
2. Keep the exact supported fields and exclusions in scope help; do not promise journal body, note, habit-log or task matches.
3. Help is available in initial, empty/no-match and error states and is not only a transient announcement.
4. Preserve active criteria, clear/reset behavior, paging, query text and query performance. This is an independent copy/UI change.

## Design and contract

Change Search page title and add concise accessible scope help. Keep repository query/index behavior exactly as implemented.

Add search_entries_title = Search entries and search_scope_help = Matches entry titles, tags, people and places. Journal text, notes, habit logs and tasks aren't searched. Provide EN/FR/FA translations; SearchRoute renders help adjacent to the query field with normal readable semantics.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `titleNamesEntryScope` | Open Search from Timeline. | The destination says Search entries. |
| AC-2 | `scopeIncludesSupportedAndExcludedFields` | Read the help near the query. | It names titles/tags/people/places and excludes journal text/notes/habit logs/tasks. |
| AC-3 | `noMatchKeepsScopeHelp` | Search a phrase present only in a journal body. | No-match remains honest and scope help is still readable. |
| AC-4 | `supportedSearchIsUnchanged` | Search title, tag, person and place fixtures with active filters. | Existing matches, counts, criteria and paging remain unchanged. |
| AC-5 | `localizedHelpIsAccessible` | Read initial/error/no-match states in EN/FR/FA at 200% font. | Complete help remains reachable and does not replace error/Retry controls. |

## Scope boundary

No full-text indexing, body/note search, task/habit search or repository SQL change.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.

