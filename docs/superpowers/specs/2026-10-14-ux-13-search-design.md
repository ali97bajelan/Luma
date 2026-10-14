# UX 13: visible, removable search filters

Status: proposed design, ready for review. Baseline `391e7a4`. Related: [handoff](../../UX_DEVELOPMENT_READY_2026-10-14.md), [plan](../plans/2026-10-14-ux-13-search.md).

## Intent and scope

A person returning to Search can understand why the results are limited and remove a specific selection without opening the filter dialog. Keep the existing title/catalog lookup, query generation, paging, date semantics and SavedStateHandle restoration. Body search, a new search engine and performance work are outside scope.

## Current behavior

`SearchRoute.kt` displays a structured-filter count and `Clear all`. Names are shown only in `SearchFilters.kt`. `SearchViewModel.clear()` already clears text and all filters. `SearchInput` contains `text`, optional inclusive `from`/`through`, tag/person UUID sets and one optional place UUID. Catalog selections may remain after deletion. Dates can be invalid; that state intentionally suppresses queries.

## Design decisions

Prefer individual named chips over a single count or one chip per category: one tap removes one selection, and duplicate catalog names stay tied to their UUIDs. Keep the dialog for adding/changing selections. Show all selected chips in a wrapping section that scrolls with the result list; do not put a potentially 203-chip block in the fixed toolbar. Search text and filter/reset controls remain above that scrollable body.

Filter presentation order: From, Through, tags, people, place. Within a multi-selection category, sort UUID strings for a stable order while names load or change. Each chip has one removal action, a category/name label and a decorative close icon. A chip never opens its picker. Its full accessible name includes the category, full name, and removal intent even when the visible name is ellipsized.

| State/action | Required behavior |
| --- | --- |
| Selected known catalog ID | Show `Tag: Work`, `Person: Alex`, or `Place: Home` using the actual saved name |
| Selected ID while options load/fail | Show category and localized `Selection loading` / `Selection unavailable`, plus the first eight UUID characters; retain/remove by full UUID |
| ID absent from successfully loaded options | Show category and localized `Unavailable selection`, plus the first eight UUID characters; do not silently drop it |
| Duplicate names | Keep separate chips and removal actions keyed by category and UUID; include short UUID suffixes in visible and spoken labels only when names collide in that category |
| From/Through | Show inclusive endpoint label and localized medium date; removal clears only that endpoint |
| Remove chip | Immediately update saved input, reset result offset and request the replacement generation; preserve other inputs |
| Reset filters | Clear dates, tags, people and place; retain text verbatim; enable only when at least one structured filter is active |
| Clear all | Clear text and structured filters, preserving current `clear()` behavior; enable if text is nonempty or a structured filter exists |
| No structured filters | Show `No active filters`; still display the current text query in its field |
| Invalid date range | Keep both chips; retain current no-query behavior; allow fixing/removing either endpoint |
| Loading/error/no results | Keep active chips visible and removable; catalog-option errors must not disable removal |

There is no cap or hidden selection count replacing the names. The existing count may remain on Filters as a secondary summary. Chip labels wrap to at most two lines and ellipsize long user names; the complete accessible label stays available. The existing filter matching help and text-search scope remain visible.

## Components and contracts

Create `ui/search/ActiveSearchFilters.kt`:

- `internal enum class SearchFilterKind { FROM, THROUGH, TAG, PERSON, PLACE }`.
- `internal data class SearchFilterKey(val kind: SearchFilterKind, val id: UUID? = null)`; ID is present only for TAG/PERSON/PLACE.
- `internal data class ActiveSearchFilter(val key: SearchFilterKey, val date: LocalDate? = null, val name: String? = null, val duplicateName: Boolean = false)`; date exists only for date keys; name is the raw catalog name or null. Localized fallback state comes from `SearchState`, not stored strings.
- `internal fun activeSearchFilters(input: SearchInput, options: SearchOptions): List<ActiveSearchFilter>`: pure projection, stable order, no query mutation.
- `@Composable internal fun ActiveSearchFilters(state: SearchState, onRemove: (SearchFilterKey) -> Unit)` renders those descriptors.

Add `SearchInput.activeFilterCount: Int` and `SearchViewModel.clearFilters(): Unit`. Add `SearchViewModel.removeFilter(key: SearchFilterKey): Unit`: ID removal uses subtraction, never toggle; a stale removal cannot re-add an already removed selection. Removing a stale place key only clears the matching place. All operations use existing `update(...)` persistence, cancellation and offset resetting. Keep `clear(): Unit` unchanged.

`SearchRoute.kt` owns toolbar controls and inserts the named-filter section as the first `LazyColumn` item. Do not duplicate filter state in Compose. `SearchFilters.kt` uses the shared count and continues to edit the same ViewModel. The current keying/restoration strategy remains unless a failing regression requires a focused adjustment.

## Copy and accessibility

New resource contracts, translated in `values`, `values-fa` and `values-fr`:

| Key | English copy / arguments |
| --- | --- |
| `search_reset_filters` | Reset filters |
| `search_no_active_filters` | No active filters |
| `search_filter_named` | `%1$s: %2$s` (category, localized date or full catalog label) |
| `search_remove_filter` | `Remove %1$s filter: %2$s` |
| `search_selection_loading` | Selection loading |
| `search_selection_unavailable` | Selection unavailable |
| `search_filter_identity` | `%1$s (%2$s)` (name/fallback, short UUID) |

Reuse existing From, Through, Tags, People, Places and unavailable-selection resources as category/fallback inputs. Resolve resources at render time. Tags: `search_active_filters`, `search_filter_<kind>[_<full-uuid>]`, `search_reset_filters`, `search_no_active_filters`. Preserve `search_filters`, `search_clear` and all existing picker tags. Chips expose button role, a single click target and at least 48 dp touch height. RTL follows layout direction; IDs are technical disambiguators, not translated names. Error/result announcements are completed by the 14/16 unit.

## Acceptance and verification

- **S13.1:** After closing the dialog, every selected date/tag/person/place has a named chip outside it; no dialog reopen is needed to remove one.
- **S13.2:** Removal changes exactly the addressed filter, including same-name people and deleted catalog entries; repeated/stale removal is idempotent.
- **S13.3:** Reset filters retains text; Clear all clears it; no-filter state is explicit. Empty text with selected filters and text-only queries both work.
- **S13.4:** Chips restore after activity recreation and detail return; late old-query results cannot overwrite a removal/reset.
- **S13.5:** Invalid dates, option loading/failure/deletion and empty results keep a visible correction path.
- **S13.6:** At 320 dp width, scale 2.0 and Persian RTL, chips and toolbar actions remain reachable without an unscrollable header crowding out results.

Use pure projection and ViewModel tests for identities/reset/generations; Compose tests for actual chip removal and layouts; existing `SearchFlowTest` for real catalog selections and restoration. Keep body-only no-match coverage. Manual TalkBack validates spoken removal names and focus after deletion. No SQL/schema change is needed.
