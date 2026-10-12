# T15 — Simple local lookup

Status: approved by the user; implemented with validation recorded in docs/VALIDATION.md.

## Agreed scope

Prioritize app speed and a small implementation. Search only saved titles,
tags, linked people and places, with optional dates. Journal bodies, notes,
emotion/mood filters, event-type filters and full-text indexing are out of scope
for this stage. Keep capture and browsing offline and permission-free.

## Query behavior

One text field matches a literal substring in an event title, journal title,
linked tag name, linked person name or linked place name. Match any of these
fields. Treat SQL wildcard characters as literal input using bound and escaped
LIKE arguments; expose no SQL or query-language operators. Matching follows
SQLite LIKE behavior: ASCII case-insensitivity and exact non-ASCII characters.
Trim outer whitespace before matching; blank text imposes no text constraint.

Optional structured filters select tags, people, one place and inclusive From/
Through dates. Different filter categories combine with AND; multiple selected
tags or people combine with OR within that category. Use catalog IDs to preserve
distinct identities. Deleted selections remain visibly unavailable until cleared
rather than silently broadening results. Dates use occurrence timestamps and real
civil-day boundaries in the displayed timezone, including DST. Reject an inverted
date range before querying.

Return each underlying event once, newest first, ordered by occurrence then UUID
descending. Preserve Timeline's established zero-duration app visibility rule.
Open results through the existing source detail/edit routes.

## Performance and storage

Reuse the current Room schema and occurrence/type/reverse-link indexes. Add no
FTS table, search-document cache, synchronization triggers, dependency or migration.
Add a small domain SearchRepository contract and a Room implementation. Compose
fixed SQL predicates with bound values and EXISTS for linked filters. Apply all
filters before LIMIT/OFFSET and relation loading. Do not read bodies to match text
or filter a loaded history in Kotlin.

Read 50 matching rows at a time, retaining at most 300 in the screen. Read row count
and page in one transaction on Dispatchers.IO. Bound text to 200 characters and
multiselects to 100 IDs per category. Debounce typing by 300 ms, cancel obsolete
queries and reject late results. Refresh after relevant source or catalog changes.

Substring matching can scan titles and linked names; existing indexes primarily
help date and relation restrictions. Measure this simple implementation before
considering additional indexes or a more elaborate search system. No speed claim
is based solely on the presence of indexes.

## Screen

A Search action on Timeline opens a dedicated route with Back, text input,
optional filters, Clear all and matching result count. Keep the five main tabs.
Search spans all dates until explicitly restricted; its date range is independent
of the selected Timeline day. Catalog pickers support name filtering and scrolling.

Reuse entry cards and existing navigation. Show occurrence dates for multi-day
results. Save input, selected IDs and window position through recreation and detail
navigation. Loading, no matches, invalid dates and errors have distinct states;
Retry retains all inputs. Quick Capture remains available. No query text or entry
content is logged.

## Validation

- JVM checks for literal input handling, query limits, date/DST boundaries,
  combined filters, cancellation, paging, retries and saved-state restoration.
- Room checks for titles and linked names, combined filters, literal percent/
  underscore/quotes, Persian names, duplicate links, deterministic ordering,
  zero-duration visibility, transactional count/page agreement and source edits.
  Verify body-only matches are excluded and current schema versions stay unchanged.
- Compose checks for entry-point navigation, combined controls, clear/retry,
  result-to-source navigation, edit/delete return and recreation.
- Measure first and later pages on 50,000 entries across five years, including
  selective and broad/no-match queries. Include relation mapping; record median,
  p95, device and build details. Target repository-page p95 below 300 ms after
  database initialization, then investigate any failures before claiming speed.
- Run applicable JVM suites, APK/test compilation, data lint and device checks on
  an already available emulator with an isolated validation app ID. Do not launch
  or reset an emulator or replace the user's installed app. Record unavailable
  checks and existing lint analyzer failures honestly.

Update TASKS.md to reflect this explicitly reduced T15 scope and document actual
implementation/results in README.md, ARCHITECTURE.md and docs/VALIDATION.md.
Preserve existing uncommitted work. T11/T13 validation follow-ups and later tasks
remain separate.
