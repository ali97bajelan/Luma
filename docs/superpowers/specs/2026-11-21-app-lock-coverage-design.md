# App Lock coverage and Memory performance

21 November 2026 · Draft specification

[Implementation plan](../plans/2026-11-21-app-lock-coverage.md) · [Existing App Lock requirements](2026-10-31-app-lock-product-requirements.md) · [Credential types](2026-11-02-app-lock-credential-types-design.md)

## Product behavior

The App Lock coverage selector has exactly two mutually exclusive choices. **Whole app** is the default for new setup.

| Coverage | Behavior |
| --- | --- |
| **Whole app** | Preserve the existing app-wide gate, authorization and lifecycle rules, including the existing restricted-capture and active Focus exceptions. |
| **Memory section** | Require an unlock to browse the Memory Library or read, edit or delete a saved memory wherever it appears. Protect Memory records, legacy journal entries, moods saved as memories, and their associated text, dates, tags, links, photos and drafts. Standalone moods, Tasks, Habits, Focus, People and Places catalogs, health and usage remain available. |

On or off is still an App Lock setting. Credential type, recovery, retry and cooldown, relock timing and screenshot preference keep their current behavior. A successful unlock gives a process-local foreground grant under the existing lifecycle rules. Leaving Memory within the same foreground session keeps the grant. Explicit Lock Now, device lock, process restart and the configured background relock invalidate it. A covered navigation request holds only the destination and parameters needed to resume it. Cancel returns to the originating public screen or More when no public back stack is available. Unlock resumes the requested destination once.

### Capture and sensitive actions

- In Memory section mode, an unlocked user can use normal capture. A locked user can start a new protected capture through the existing write-only restricted flow (including dated and widget capture). It cannot reveal existing Memory records, suggestions from protected catalogs, saved photos or existing private drafts. Retain the existing private draft and photo ownership rules, including authentication for protected draft resume. A standalone Mood, new Task or new Habit opens and saves through its normal editor. Normal create requests use the serialized write guard with the destination’s coverage and insert/idempotency checks. Restricted capture continues to require its own validated, create-only capability. Adding a memory marker to an existing mood requires the Memory grant, and moving between private and public capture paths keeps the draft, photo ownership and save validation intact.
- Keep fresh authentication for existing sensitive actions when App Lock is enabled, including share, export, backup, restore, reset and changing coverage. Browsing grants carry no authority across these operations. The scope setting controls in-app access and presentation. Backup and storage behavior follows the existing backup contract.
- While locked, mixed surfaces remain usable and omit protected entries and all artwork, labels, counts, markers, dates, summaries and accessibility semantics derived from those entries. Today, Timeline, Search, Calendar, Weekly Review and People/Places histories keep their public results and counts. Exclude protected contributions before calculating totals, mood averages and trends. Hide the Memory-only cards, markers and tag options or show a neutral lock state without a record count. A public Person or Place stays searchable while its protected history links are hidden. Search discards stale Memory tag filters on revocation and remains usable for public titles, People and Places.
- Memory-derived routes, photos, saved-state restorations and deferred results use the same protection as interactive navigation. Keep the current secure-window rules, including foreground screenshot protection on covered content when selected, secure authentication and recovery surfaces, and Recents protection. Widgets and notifications never inherit the foreground grant. Under Memory section, public Task and Habit previews and actions use their normal policy.

## Compatibility

| Existing store | Effective coverage after upgrade |
| --- | --- |
| App Lock off or policy absent | Off. New setup defaults to Whole app. |
| Whole app enabled | Whole app. |
| Selected scope containing only Memories, legacy Journal, or both | Memory section. |
| Any other valid enabled selected scope, including Mood-only and mixed scopes | Whole app. This preserves protection for every previously selected area. Show a one-time explanation at the next App Lock settings visit. An authenticated user can then choose Memory section. |
| Unreadable, unauthenticated or invalid persisted state | Existing unavailable/recovery route. Publish no protected content before policy load succeeds. |

Use the existing authenticated store and preserve credentials, recovery verifier, retry history, relock delay, screenshot choice and policy generation on read. Decode older layouts explicitly and write the canonical two-scope format only through the existing atomic store writer. Preserve migration disclosure until acknowledgement even if an unrelated security write occurs first. A user-initiated scope change follows current authentication and generation invalidation. Existing events, archives, tags, photos and drafts need no data rewrite.

## Design

1. **Use the existing coordinator and authorize before loading data.** Keep internal classifications for operations that already depend on them. Expose two policy values. Add a bounded visibility-aware read path for mixed queries and keep the gateway checks at publication and mutation. This approach covers cross-surface access and avoids work on private rows while locked.
2. **Trim the selector and keep the current read pipeline.** This saves implementation effort but leaves mixed views materializing records just to discard them. Current Timeline and Search scan all candidate pages. Calendar and some aggregate flows subscribe to private data even while presenting a redacted result.

The proposed implementation uses the first approach. Memory Library already mounts only when the route gate allows it, loads a first prefix of 30 plus one sentinel, and closes its state holder on disposal. Reuse that pattern. Put the Memory boundary in the central classifier and in the SQL visibility predicate used before counts, sorting, paging and relation hydration. Match the existing Memory association for marked moods and cover both legacy Journal and Memory types. Handle an unexpected row with a Memory relation conservatively as covered. Route authorization that needs an event classification can use lightweight metadata (type and existence of Memory or People relations) before any full event graph is loaded. Missing or unresolved classification stays unavailable until a current decision is possible.

Drive relevant observers with distinct visibility and authorization inputs. Use the existing launch and lifecycle state instead of polling. For Memory-only observers and saved-photo preview requests, deny before starting the query, file read or decode. Cancel private flows and release private list state, buffered results and in-flight thumbnail work on revocation. Before publishing a result or committing an action, recheck the current authorization and applicable content ownership. Scope change must immediately remove stale content from rendered semantics even when an old query or image request finishes late. Keep filtered aggregate results out of the existing unfiltered daily-summary cache.

## Performance acceptance

Use the same seeded data, build configuration, compilation mode and device for each comparison. Use a release-like, isolated build on a representative low- or mid-tier API 31+ physical device. Record medians and p95 where specified from at least 20 repetitions per journey. Run functional acceptance on API 28 and API 36 as well.

| Scenario | Proposed release target |
| --- | --- |
| Cold launch to Today and first visible page of Timeline and Search | Memory section locked median ≤ candidate Off median + max(25 ms, 5% of candidate Off median), and apply the same ceiling against pre-change Off. Measure first usable page separately from first frame. |
| Scroll Timeline and Search | On a fixed journey, p95 frame CPU duration ≤ candidate Off p95 + max(1 ms, 5% of candidate Off p95). Record frame deadline overruns and trace any regression. |
| Open locked Memory Library | Gate visible within 150 ms p95 after a warm tap. Zero Library/Memory-only delegate subscriptions and zero saved-memory photo reads or decodes before authorization. |
| Successful unlock to first Library content | First 30 rows, empty state or actionable error visible within 300 ms p95 after verification succeeds. Photo placeholders are allowed. Report credential verification time separately. Reuse the 31-row initial query. |
| Large private history while locked | With a fixed public population and 1,000 then 10,000 private entries, hydrate zero private graphs for mixed pages and at most the requested visible page size. Run one count and one page selection per page request. Do not issue a page query loop or start a Memory-only observer while locked. Measure and report the count and selection cost separately. |
| Ten unlock/relock cycles | No duplicate active private collectors, no saved-photo work left running after cancellation settles and zero protected items in rendered state or semantics after revocation. Check for steadily growing retained lists or decoded images in a memory trace. |

Use distinct readiness markers, startup timing and frame timing for the applicable journeys. [Android Macrobenchmark metrics](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics)

## UI and release checks

Use the warm-paper/obsidian Luma theme, shared Material roles, typography and shapes, LumaUi tokens, AppPageScaffold and existing lock components. Preserve navigation, callbacks, drafts, validation, retry, Undo, test tags, scrolling, FAB clearance, insets and keyboard behavior. Verify System, Light and Dark, English/French/Persian and RTL, offline fonts, TalkBack, large text and targets of at least 48 dp. [Luma design system](../../luma-redesign/README.md) · [Route coverage](../../luma-redesign/route-coverage.md) · [UI consistency checklist](../../luma-redesign/ui-consistency-checklist.md)

Release requires migration and credential regression evidence, the full cross-surface locked and unlocked matrix, cancellation and race cases, restricted-capture regression and measured performance. This draft sets the proposed scope, migration behavior and performance targets. Measurements are pending.
