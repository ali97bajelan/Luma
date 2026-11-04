# Focus names, tags, and insights specification

Date: 2026-11-03 · Status: **Draft; FI01/FI03/FI04/FI05/FI06/FI07 source implementation complete; FI02 runtime verification pending**

[Plan](../plans/2026-11-03-focus-names-tags-insights.md) · [Tasks](../../FOCUS_INSIGHTS_TASKS.md) · [Existing focus specification](2026-10-28-roadmap-16-focus-mode.md)

## Intent and scope

Give each focus session a recognizable name and tag so users can see what they focused on and where their time went. This specification, plan and task ledger track the P0 implementation and its verification boundaries. Defaults below are recommendations; runtime/product acceptance remains pending as noted in the task ledger.

## Current code evidence

- `core/domain/src/main/kotlin/com/luma/lifeos/domain/focus/FocusSession.kt`: sessions and runs with credited active milliseconds, revisions, terminal/interrupted states; no name or tag.
- `FocusStatistics.kt` in that directory: terminal-only reports, half-open range overlap, duplicate protection, interrupted counts, and comparison rules. Preserve these semantics.
- `app/src/main/java/com/luma/lifeos/ui/focus/FocusScreen.kt`: idle/start, running, paused, recovery, and terminal content. `FocusHistoryViewModel.kt` pages history with generation-bound deletion.
- `app/src/main/java/com/luma/lifeos/focus/{FocusCoordinator,FocusRuntime,FocusViewModel}.kt`: shared actions, reconciliation, retained operation attempts and screen state. Metadata must travel through this path.
- `core/data/src/main/kotlin/com/luma/lifeos/data/focus/{FocusEntities,FocusDao,RoomFocusRepository}.kt`: Room persistence and command receipts.
- Room version is **9** in `data/db/LumaDatabase.kt`; portable backup schema is **7** in `data/backup/BackupArchive.kt`. Recheck at implementation time; earlier focus docs contain older baselines.
- Referenced `app/src/main/java/com/luma/lifeos/ui/privacy/AppLockSettingsRoute.kt` exposes Focus as a lock section. `ui/LifeOsApp.kt` already requests `LockSection.FOCUS` access. Extend those existing boundaries to new metadata/insights; no credential/settings redesign is required.

## Recommended features and priority

| Priority | Feature | User value |
| --- | --- | --- |
| P0 | Session name and one optional tag | Identify the work before starting; e.g. “Read chapter 4” / Study. |
| P0 | Summary by tag | See saved time, session count, and percentage for each category, including Untagged. |
| P0 | Longest session | See the named session with the most active time inside the selected period. |
| P0 | Period summary | Saved total, contributing sessions, average, and existing previous-period comparison. |
| P0 | Searchable, tag-filtered history | Find sessions by name or tag and inspect their durations. |
| P1 | Repeat a previous session | Prefill name/tag, then explicitly Start a new session. |
| P1 | Seven-day trend and best day | Reveal distribution using the same local-day rules. |
| Later | Optional daily goal and consistency streak | Help users who want targets; avoid pressure and arbitrary default goals. |

P1/later items are suggestions only and do not expand the P0 tasks. Existing weekly day bars/comparison remain available; do not duplicate them as a separate analytics system. No AI summary, task creation, task linking, Pomodoro, new permissions, network service, or enforcement change.

## Approaches considered

1. **One optional focus-specific tag per session (recommended):** simple editing, additive category totals, independent catalog. Reuse the existing `Tag.normalize` behavior but no journal/event associations. Leaves a small new catalog to maintain.
2. Multiple tags per session: more flexible classification, but full credit per tag causes overlapping totals, while fractional credit surprises users. Deferred until requested.
3. Shared journal tag catalog: reuses catalog UI/data, but introduces cross-feature tag deletion, backup and privacy interactions. Avoid for this first focus extension.

The P0 implementation plan assumes approach 1. FI06 follows that plan; multi-tag classification and a shared journal tag catalog remain deferred.

## Global constraints

- No emulator, device, Gradle, instrumentation, Android lint, or APK build commands for this work; use standalone source-level JVM tests and static checks only.
- Keep existing timer, checkpoint, recovery, navigation guard, generation, replay and deletion contracts.
- Preserve Kotlin/Compose architecture, existing SDK levels and dependencies; operate offline without new permissions.
- Supply English, French and Persian resources; support RTL, wrapping text and 48 dp minimum controls.
- Report runtime/UI/Room integration verification as NOT RUN; standalone tests cannot establish those guarantees.

## FI-R01 — Names and tags

New sessions require a trimmed, nonblank, single-line name of 1–120 Unicode code points. Reject CR/LF and other ISO control characters; do not truncate. Names need not be unique. Names preserve letter case and internal spacing. Validation stays visible beside the field and Start remains disabled until valid.

A session may have zero or one tag. The tag field accepts an existing suggestion or creates one on successful Start. No seeded tags and no mandatory classification. Tag labels are trimmed, single-line, 1–50 UTF-16 code units, with ISO control characters rejected. Normalize identity using `Tag.normalize` (`trim().lowercase(Locale.ROOT)`); keep the first stored display label. Work/work resolves to one tag; Persian and emoji labels round-trip. Adding a draft tag does not persist a catalog record.

Proposed domain additions:

- `FocusMetadata(name: String?, tag: FocusTag?)`, `FocusTag(id: UUID, name: String)`; append `metadata: FocusMetadata = FocusMetadata(null, null)` to `FocusSession` for legacy construction compatibility.
- `FocusStartInput(name: String, tagName: String?)` with pure `normalizeFocusStartInput` validation. New repository starts require validated input; no default empty metadata overload may bypass this rule. Update every caller and fake.
- Old sessions and archives may have null names/tags. Render a localized “Focus session” and “Untagged”; never persist translated fallback strings. Empty names are invalid, even in imports; only legacy adapters may introduce null names.

Metadata is immutable after successful Start in P0. Show it in running, paused, recovery, completion, history, and longest-session views. Starting another session returns to an empty setup draft. Idle draft uses SavedStateHandle; preserve it on failed Start and configuration recreation, clear on successful Start or database replacement/reset. A retried Start uses the exact captured input/IDs/sample/context; changing a failed draft starts a new operation only after the prior outcome is reconciled.

## FI-R02 — Durable metadata and compatibility

Add nullable `name` and `tag_id` to `focus_session`; create `focus_tag(id, name, normalized_name)` with unique normalized name and a tag FK/index on sessions. Tag catalog is focus-only. Migration **9→10** preserves all existing sessions/runs/receipts with null metadata. Use Room-compatible table rebuild if needed for the FK, preserving existing indices and constraints. No destructive fallback. Check actual version before execution.

Start validates input, checks generation/receipt/open slot, reuses or creates a tag and writes session/run/receipt in one transaction. Invalid, stale or conflicting starts leave no orphan tags. Receipt payload identity includes normalized metadata; identical replay returns AlreadyApplied, changed metadata under the same command ID is invalid. Retained legacy receipt decoding remains valid under its original payload rules and must not silently accept a new payload.

Pause/resume/checkpoint/recovery preserve metadata. Deleting a session keeps tags used by other sessions. P0 exposes no tag rename/delete UI. Suggestions query only tags referenced by existing sessions, ordered by latest session start descending then normalized name ascending, limited to 10; unused catalog rows may remain stored. Reset clears them through the existing full reset path.

Portable archive schema **8** adds metadata and `focus_tag`, exporting only tags referenced by exported terminal sessions. Keep runtime/receipts/generation/tombstones device-local. Restore schemas 1–6 with no focus history, schema 7 focus history with null metadata; schema 8 validates names, tag normalization/uniqueness, FKs, lengths and terminal timing before replacing live data. Reject malformed archives without changing live data. Preserve metadata through interrupted-session export and current paused-export choices. Update backup format/privacy docs and Room schema snapshot. Schema snapshot generation is deferred because Gradle is prohibited in this handoff.

## FI-R03 — Insights calculations

Default period: Today in the current viewing zone. Offer Today, This week, and All time. Today and week use existing local date/ISO-week helpers; this week ends at next local midnight after today, matching existing partial-week reporting, with future days excluded. All time has no comparison. Existing comparison rules handle an empty previous baseline without a fabricated percentage.

Only terminal FINISHED/INTERRUPTED sessions contribute positive credited run overlap. Active/paused sessions remain separately labeled “In progress — not included in saved totals.” Use `focusRangeContributionMillis`; null range means full credited duration. Pauses, raw wall span, unobserved recovery gaps and duplicate snapshots never add time. Interrupted contributions remain marked.

- Total: checked sum of positive contributions. Count: distinct sessions with positive contribution. Average: integer total/count rounded down to milliseconds; zero if no contributors. Format only at the UI boundary.
- Tag rows: group by tag UUID, null = Untagged; total, distinct contributor count, and percentage of the selected-period total rounded HALF_UP to one decimal. Rows sorted total descending then normalized tag name ascending, UUID ascending as final tie-break. Untagged uses an empty sort key. All rows sum exactly to total milliseconds; rounded percentages may not sum to 100.0%. Zero-time rows are omitted.
- Longest: maximal **in-period active contribution**, not wall span, uninterrupted run, or full duration outside the period. Tie-break by startedAt descending then UUID string descending. Include full active duration as separate detail when clipped by the period. Include interrupted sessions with their warning. No contributor means no record, not a zero-duration winner.
- History is independent of the insight selection: search/tag filters narrow history rows only; heading says “History filters”. Insight cards continue to summarize the whole selected period. This avoids suggesting a filtered subset is the overall total.

Example: a 30-minute Study session spanning 23:50–00:20 contributes 10 minutes yesterday and 20 today. A separate 25-minute Work session today makes today's total 45 minutes, count 2, average 22m30s; Work is today's longest at 25 minutes. Study=44.4%, Work=55.6%. All-time longest is Study at 30 minutes.

Compute across all matching saved sessions, never the current history page. Query a consistent repository snapshot; invalidate on Stop, interruption, deletion, restore and generation/zone/period change. Overflow or invalid snapshot becomes an unavailable report, never a wrapped value or fake zero.

## FI-R04 — Focus page and history

Idle layout: page title → name/tag setup → Start → period selector → summary metrics → longest card → tag breakdown → history link. Keep settings/history reachable when idle. Large tag breakdowns show five rows plus Show all; show text durations and percentages so color is never the sole encoding. All time still uses bounded history pages and cancellable background aggregation.

Running/recovery/paused layouts prioritize existing timer controls and display name/tag; do not introduce insight navigation through the running guard. Completion adds saved name/tag and existing duration. Insights must never disable Pause or Stop.

History shows name, tag/Untagged, date, credited duration, clipped contribution where relevant, and interrupted warning. Search matches a trimmed case-insensitive name substring using Locale.ROOT; a legacy null name does not match localized fallback text. Tag selector offers All, Untagged, and catalog tags. Combine period + search + tag with AND semantics. Reset page offset to zero on filter change; apply filters before counts/50-row paging, preserving existing stable order. Literal `%`/`_` in search must not become SQL wildcards. Retain history delete confirmation/conflict handling and refresh insight totals after deletion.

Show Loading, Ready(empty), Ready(data), Error/retry as distinct states. Cancel stale requests and bind results to query + generation; never display a previous filter/zone/restore result under a new heading. Metadata is private Focus content: use existing Focus access gates for insights, history, Today and Review, including cached labels. Never include name/tag in diagnostic logs or external timer notifications.

## Acceptance and verification boundaries

| ID | Acceptance | Owning task |
| --- | --- | --- |
| FI-A01 | Valid names/tags normalize; blank, control-character and oversized values reject; legacy null fallback stays display-only. | FI01 |
| FI-A02 | Metadata persists atomically and survives timer transitions; replay/conflict/generation rules include metadata without duplicate catalog writes. | FI02 |
| FI-A03 | Non-destructive migration and archive adapters preserve old history; malformed new archives cannot replace live data. | FI03 |
| FI-A04 | Tag totals conserve milliseconds; range clipping, ties, interruption, duplicate/conflicting input and overflow follow FI-R03. | FI04 |
| FI-A05 | Drafts/retries and period state are correct; metadata appears across session states; Pause/Stop remain reachable. | FI05 |
| FI-A06 | Search/tag filters apply before pagination, query changes reset offsets, and stale results are dropped. | FI06 |
| FI-A07 | Every new surface honors Focus privacy, resources cover EN/FR/FA, and verification limits are stated. | FI07 |

Code-only evidence may cover pure rules, models, query builders, archive adapters and state reducers. SQLite fixture tests may verify literal migration SQL, but do not prove Room generated adapter/schema validation. Actual Compose layout, app-lock integration, Android lifecycle and Room migration behavior remain NOT RUN under the requested constraint.
