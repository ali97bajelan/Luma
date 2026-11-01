# Memory in More — feature specification

Date: 1 November 2026 · Revision 2: product-design review

Status: **Implemented at source level; behavior unverified.** The user authorized implementation on 1 November 2026. See the [implementation plan](../plans/2026-11-01-memory-in-more.md) for the execution record and verification limits.

## Product intent

Add **Memory** to **More** as a calm collection of moments worth keeping. The first visit should answer “What have I saved?”; returning visits should make it easy to find a moment, read it and record another.

Keep the requested singular **Memory** label in More and the page title. Use “memories” naturally in supporting copy. Reuse standalone memories and memory markers on journals/moods; never copy a marked source into a new event.

## Product-design review

| Finding in revision 1 | Consequence | Revision 2 decision |
| --- | --- | --- |
| Opens on the current month | An infrequent writer sees an empty page despite having older memories | Open a recent-first collection across all dates |
| Previous/next month is the only way back | Finding an old moment requires repeated taps through empty months | Group loaded records by month; offer Load older memories |
| Every card repeats status, source, title and timestamp | Metadata competes with the memory itself | Lead with meaningful text; use quiet source/date metadata and optional importance |
| Capture action may be in the header or content | Implementers must invent the hierarchy | One full-width Record a memory button beneath the page header |
| Saves outside the selected month can disappear from view | Saving feels uncertain | Remove the month filter; provide saved feedback with View for library capture |

The revised design needs a bounded recent-record query. This small repository addition is preferable to loading an unlimited date range or retaining a monthly filter solely because the existing repository supports it.

## Approaches considered

| Approach | Benefit | Tradeoff |
| --- | --- | --- |
| Link to weekly review | Smallest change | Limited history browsing |
| Monthly browser | Reuses range query unchanged | Empty first impressions and repeated date-navigation work |
| Recent-first collection with progressive loading — recommended | Shows saved content immediately and supports continuous browsing | Adds a bounded read contract and permission filtering before its database limit |

Month headings provide orientation without asking people to choose a date before seeing content. Existing Review and Calendar retain date-specific exploration. Search and a month picker are deferred.

## Existing foundations and constraints

- `MoreRoute.kt` uses existing choice rows; preserve the other More entries.
- `MemoryRepository.observe(TimeRange)` and `LifeEventDao.observeMemories` already return marked events. Preserve this API for the weekly Review.
- Production passes `ProtectedMemoryRepository` to `LifeOsApp`. Use that facade, including its `ProtectedContentGateway` projection.
- `ProtectedRecordClassifier` requires Memories for standalone memories, Memories + Journal for marked journals, and Memories + Mood for marked moods.
- `openEvent(record)` already distinguishes journal and generic details. Existing details/editors own reading, sharing, editing, delete and unmark.
- Follow [Luma's visual foundations](../../luma-redesign/README.md). Reuse theme tokens and the existing reading pane; no new dependency, permission, schema, migration or archive format.
- Preserve existing workspace changes, particularly `LifeOsApp.kt` and localized string files.

## Requirements

### M01 — Discoverability and navigation

- Add an always-present **Memory** row after Tasks and before People & places. If Tasks is absent, Memory follows the last available Focus/Habits row.
- Use `ic_bookmark_outline`, supporting copy **Revisit moments you've saved**, a trailing chevron and the current full-row More style. No private snippet, count or promotional treatment.
- Add secondary route `AppRoutes.MEMORIES = "memories"`; preserve the singular `memory?...` editor route. Do not add a primary navigation tab.
- Page title: **Memory**. Up/system Back return to the previous destination, falling back to More when necessary. Use `launchSingleTop` on entry and preserve the library back-stack entry beneath details/editors.

### M02 — Collection and progressive browsing

- Show authorized saved memories across all dates, ordered by occurrence descending and event ID descending as a stable tie breaker. Include future-dated records under their actual occurrence month, with **Future date** metadata.
- Initially show up to **30 records**. Query **31** to detect an extra authorized record. Each **Load older memories** tap increases the visible limit by **30**, requesting one extra again. No total count or unbounded history query.
- Filter permitted event types **before** applying the database limit. Inaccessible recent journals/moods must not crowd older accessible standalone memories out of the page or determine footer availability. Keep gateway projection as the final boundary.
- Group loaded records by localized full **month + year** in the current display zone. Do not show empty month headings. A partially loaded month continues in place when more records arrive.
- Show **Load older memories** only when the extra authorized record exists. While loading, retain current cards and show disabled **Loading older memories…** with progress. On failure retain authorized cards and show **Older memories couldn't be loaded** with **Try again**, retrying the same requested limit.
- Keep loaded depth and the visible record anchor when returning from details/cancelled capture, rotating or resizing. Save only depth and scroll metadata. Recreate record content through the protected repository after process recreation.
- Inserts, edits, delete/unmark and restored data refresh live. Preserve the visible item where possible; if removed, use its nearest surviving neighbor. Do not snap a reading user to the top. Recompute headings after zone/locale changes.
- Thirty is a loading increment, not a retention limit. Only retain the requested prefix plus its sentinel; do not preload the entire archive.

### M03 — Layout and card hierarchy

Reading order: Back/title → capture button → collection or state message → older-record footer. Use one scroll surface, with the capture button at its top. No decorative hero, count panel, introductory paragraph or floating overlay over the collection.

- Use `AppPageScaffold` and `LumaReadingPane`. Place one full-width filled **Record a memory** button as the first list item. Keep it in first-load, empty, error and populated states; no duplicate action in the app bar/empty state/global capture overlay.
- Use semantic month headings and existing list surfaces with a quiet outline/surface treatment.
- Primary card text: nonblank event title; otherwise the first nonblank line of the memory note; otherwise localized **Memory**, **Journal memory** or **Mood memory**, according to source type. Limit to two lines.
- Preview: up to three lines of the memory note. If the note supplied the heading, omit that first nonblank line from the preview. Preserve user line breaks and stored content; display derivation must not rewrite data. Missing notes create no placeholder gap.
- Do not pull the original journal body or mood note into the preview. Show quiet **From journal** or **From mood** metadata for marked sources; omit source metadata for standalone memories.
- Date sits below the content: localized day/month, with the year in the section heading. Full details retain time. Add **Future date** when appropriate; keep Luma's date policy.
- Show optional **Importance: n/5** as quiet text. Do not repeat the unconditional **Remembered moment** status in a collection already made entirely of memories; do not imply importance is an editable star/favorite.
- The whole card is one reading target with a trailing directional chevron, calling `openEvent(record)`. Read-only entries remain readable; existing detail rules govern actions.
- No inline Share/Edit/Delete/Unmark, photo thumbnails, people/place fields or attachment badges. Existing full details retain those features.

### M04 — Capture and return

- Record a memory opens the existing new-memory editor with its normal current-time default. Preserve validation, drafts and confirmations.
- Cancel returns to the previous collection position; no inserted placeholder.
- After successful new-memory capture originating from this library, use the existing save-feedback mechanism with **Memory saved** and **View**. View opens `event_detail/{savedId}` through normal protected navigation. Do not automatically open details or scroll the reader.
- A backdated record remains reachable through View even if beyond the loaded prefix. Carry only saved ID/capture-origin metadata, consume each result once and avoid duplicate generic save feedback.
- Cancelled, failed, deleted or unmarked outcomes never show success. Existing source-editor changes update the collection live; removing a marker must preserve its source event.

### M05 — Honest states and privacy

- Initial load: accessible progress in the collection area, retaining header/capture. Do not flash an empty state before a query finishes.
- Empty authorized collection: **No memories to show yet** and **Record a moment you want to keep. You can also save a journal entry or mood as a memory.** The existing top capture button is the only primary action; no older footer. This does not claim that no locked records exist.
- First-load error: **Memories couldn't be loaded**, **Try again to see your saved moments.**, and **Try again**. Do not present a failure as empty. Older-load errors retain authorized cards as M02 specifies.
- Classify the library route as `LockSection.MEMORIES` and retain a typed pending unlock destination. The static More row may remain visible while locked. Cancelling unlock from the gated library returns to More.
- Preserve additional Journal/Mood protection for marked sources. Never expose hidden counts, placeholders, headings, snippets or footer availability. Filter authorized source types before LIMIT and apply gateway projection after reading.
- On authorization changes, clear retained rows/anchors, reset depth to 30 and restart with the new permissions; stale pending loads cannot repopulate content. Privacy takes precedence over position retention.
- Include the route in aggregate screenshot protection and preserve its browse context through nested details/editors. While gated or authorization is unknown, mount the lock gate instead of private content.
- No raw repository in the UI, no bodies/titles in saved state/arguments/logs. Loading is local and needs no network permission.

### M06 — Visual and accessible interaction

- All new copy is resource-backed in English/French/Persian. Use `DisplayTimePolicy`, current display zone and Luma's existing Gregorian chronology.
- Reuse theme typography/colors: 600 dp maximum reading width, 20 dp side gutters, 24 dp between month sections, 16 dp card padding and 8 dp within-card spacing. Maintain one readable column on expanded windows.
- At least 48 dp targets. Allow scaling/wrapping; use logical start/end placement and mirrored directional icons for RTL. Decorative icons must not duplicate spoken labels.
- Month headings have heading semantics; each whole card has a meaningful accessible label/action. Announce errors/save feedback through existing helpers; preserve focus during live refresh and load-older.
- Tags: `more_memory`, `screen_memories`, `memory_library_list`, `memory_library_add`, `memory_library_retry`, `memory_library_load_older`, `memory_library_older_retry`, `memory_library_item_<eventId>`.

## Scope and delivery boundary

No search, filters, date picker, favorites, galleries, statistics, bulk operations, AI, new sharing flow or editor redesign. The bounded repository query and capture-result integration are required scope, with no persistence schema change.

This spec and its [implementation plan](../plans/2026-11-01-memory-in-more.md) describe the implemented source. There is no testing/QA phase, test authoring, build/lint/test command, emulator/device work or release task. Requirements describe intended behavior, not validated results. The user's no-testing instruction overrides the roadmap's general two-phase workflow for this request.
