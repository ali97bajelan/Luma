# IP-I06 — Findable and understandable Search specification

**Priority** P1 · **Horizon** Next · **Status** SOURCE COMPLETE — QA PENDING

[Task index](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md) · [Proposal](../../20261118-Proposal.md) · [Implementation plan](../plans/2026-11-20-product-improvements-i06-search-entry-points.md) · [Final emulator QA](../plans/2026-11-20-product-improvements-q01-emulator-qa.md#qa-06)

## Intent and current behavior

Make the existing entry search easy to reach and accurate about the content its text matching covers.

SearchRoute, SearchViewModel and ActiveSearchFilters already own query/filter states. SearchSql matches event titles, journal titles and linked tag/person/place names with optional date/tag/person/place filters. The app uses a separate Tasks title search. Existing protected repository/route behavior remains the access boundary.

## Scope

Add Today and Memory Library entry actions to the same Search destination used by Timeline, conditional on an available Search repository and existing app lock behavior. Label text matching based on SearchSql and offer a distinct route to Tasks title search without copying the entry query.

Out of scope. This task excludes new search index, cross-feature aggregation, body text indexing, semantic search or cloud integration.

## Requirements

- **IP-I06-R1** A new Search navigation entry starts with empty criteria from Today, Timeline and Memory Library. Opening a result and Back restores the same Search entry’s criteria, loaded pages and viewport where its authorization remains valid. Criteria are not carried across unrelated Search sessions.
- **IP-I06-R2** Text scope names titles and linked tag, person and place names. Date, tag, people and place filters remain visible and removable individually and via clear-all. Clear-all clears filters while preserving text, consistent with the existing separation of text and filters.
- **IP-I06-R3** Keep no library data, no matches, invalid date range and query failure distinct using authorized counts. When the lock changes, clear/redact stale results per existing policy. Keep working paging/retry, including return from detail and result invalidation after mutation or restore.

## Implementation approach

Reuse the existing route and repository with correct scope copy. A unified index would expand data ownership, migration and privacy risk beyond a reachability change.

## Acceptance

**QA-06** Seed title-only, body-only, tag/person/place-linked, protected and task-only records in EN/FR/FA. Verify three entry origins, fresh starts, per-filter/reset behavior, paging and restored viewport, invalid range, failure/retry and visibility after relock. Verify Tasks links lead to title search separately.

**Product validation** Observe retrieval success and time for known-item tasks in a large multilingual synthetic library.

Apply the [shared implementation contract](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md#shared-implementation-contract). Runtime acceptance is owned by IP-Q01 after all handoffs.
