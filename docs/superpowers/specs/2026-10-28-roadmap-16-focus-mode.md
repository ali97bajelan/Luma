# F16 — Focus mode specification

Date: 28 October 2026 · Priority: **P1 (proposed)** · Status: **DRAFT — DOCUMENTATION ONLY**

[Tasks](../../FOCUS_MODE_TASKS.md) · [Plan](../plans/2026-10-28-roadmap-16-focus-mode.md) · [QA scenarios](../../feature-roadmap/16-focus-mode-qa.md) · [Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Intent, requested behavior and proposed scope

Help a user concentrate, retain each focus session, and compare recorded focus time across days and weeks. The requested behavior is: save each session; show a total on Today and comparisons in Weekly Review; let the user select allowed apps in focus settings; keep the user on the focus screen until Pause or Stop, except for selected apps.

This package defines a **proposed personal-phone v1**, plus a separate enforcement investigation. The user requested tasks, a specification, a plan and QA scenarios; this package does not implement the feature or claim that proposed defaults were approved. In particular, the requested device-wide app restriction is **not fulfilled by the personal-phone navigation guard**. It remains a tracked scope decision in FM08, rather than silently weakening that requirement.

Proposed defaults: count-up stopwatch; no target or automatic finish; empty allowed-app selection; navigation guard always enabled while RUNNING; only explicitly ended/interrupted sessions contribute to saved reports; stopped sessions retain all positive active time; pause time is excluded. A stopwatch avoids introducing unrequested Pomodoro cycles, scheduled starts and alarm delivery. These can be separate extensions later.

## Approaches and Android feasibility

| Approach | What it can provide | Limits and proposed disposition |
| --- | --- | --- |
| Personal-phone navigation guard and app shortcuts | Guard all Luma navigation while running; launch selected apps from focus screen; reliable local timing/history | Proposed v1. Home, Recents, notification shade, system UI, other apps and force-stop remain under Android/user control. The shortcut list does not block nonselected apps across Android. |
| Opt-in personal-phone restriction integration | Investigate supported launcher/accessibility/device capabilities with explicit user setup | FM08 investigation only. No promised enforcement, automatic accessibility consent, overlay, foreground polling or assumed store approval. |
| Managed-device lock task mode | A DPC can allowlist Luma and selected packages on a provisioned managed device | Stronger restriction option; requires a different deployment/setup scope. Not a silent personal-phone default. |

Android distinguishes lock task mode from user-exitable screen pinning. A DPC must allowlist packages for lock task mode; calling `startLockTask()` without this setup does not supply a non-exitable multi-app kiosk. See [Android lock task mode](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode). Android also restricts background activity launches, so repeatedly forcing Luma on top is not the proposed design. See [background activity starts](https://developer.android.com/guide/components/activities/background-starts).

Discover selectable launcher apps with a narrow MAIN/LAUNCHER `<queries>` declaration and resolve the launch target again at tap time. Package visibility is filtered on newer Android versions; do not introduce QUERY_ALL_PACKAGES just for the picker. See [package visibility declarations](https://developer.android.com/training/package-visibility/declaring). A foreground service would require a suitable service type on Android 14+, rather than an arbitrary timer service declaration; v1 needs none. See [foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types). These platform references inform the proposed architecture, not a guarantee of OEM behavior.

## Current source anchors

- `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt`, `ui/navigation/AppDestinations.kt`, `ui/more/MoreRoute.kt`: shell, current Today → Timeline → Calendar → Review → More bar, capture sheets and secondary routes. Focus belongs under More and as a Today action; keep Calendar's current scope.
- `app/src/main/java/com/luma/lifeos/MainActivity.kt`, `LumaApplication.kt`: composition, resumed entry, `onNewIntent` and widget launch handling. A screen-only Back handler cannot guard these other paths.
- `app/src/main/java/com/luma/lifeos/ui/today/TodayRoute.kt`, `TodayScreen.kt`: selected date/zone, current-day actions, independently loading cards and restore generation.
- `app/src/main/java/com/luma/lifeos/ui/review/WeeklyReviewRoute.kt`, `WeeklyReviewState.kt`: period selection, report states and existing comparison copy.
- `core/domain/src/main/kotlin/com/luma/lifeos/domain/review/WeeklyReview.kt`: ISO Monday–Sunday `reviewPeriod`; current week compares matching weekdays, with today partial. Existing previous weekdays are whole days, not matched time of day.
- `core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomWeeklyReviewRepository.kt`: transactional report reads and invalidation flow. Focus metrics can be observed separately without changing event counts.
- `core/domain/src/main/kotlin/com/luma/lifeos/domain/summary/DailySummary.kt`, `core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomDailySummaryRepository.kt`: existing saved event summary/cache. Proposed v1 renders a separate Focus card, avoiding changes to this cache format.
- `core/data/src/main/kotlin/com/luma/lifeos/data/db/LumaDatabase.kt`, `data/LumaStorage.kt`: current Room version **7** and explicit repository injection. Verify versions again at execution.
- `core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupArchive.kt`, `RoomBackupService.kt`, `BackupValidation.kt`, `BackupSql.kt`: current archive schema **6**, table inventory, staged validation/replacement and legacy adapters. Room and archive versions are separate.
- `app/src/main/java/com/luma/lifeos/ui/settings/SettingsRoute.kt`, `ui/privacy/AppDataReset.kt`, `app/src/main/AndroidManifest.xml`: settings, reset and package visibility. Manifest currently removes FOREGROUND_SERVICE. Do not remove that policy for this v1.

## F16-R01 — Durable focus sessions and state transitions

Use standalone focus tables/repositories, not `LifeEvent`, mood, task completion, habit logs or app-usage imports. Offline operation needs no account or integration permission. At most one nonterminal session exists per installation, including across multiple activities/windows.

| Current state | Action | Result |
| --- | --- | --- |
| No open session | Start | Persist a session and initial run atomically, then show RUNNING. Start disabled until durable state loads. |
| RUNNING | Pause | Close the run and persist PAUSED before releasing navigation. |
| PAUSED | Resume | Persist a new run for the same session; return to RUNNING guard. |
| RUNNING or PAUSED | Stop | Atomically close any run, finalize FINISHED and release guard; show saved active time. |
| RUNNING | Unrecoverable clock/boot continuity | Finalize INTERRUPTED at last durable checkpoint; explain the unrecorded gap. Never resume automatically. |
| FINISHED or INTERRUPTED | Replayed action | Return already-applied or conflict; never create a second session or credit duplicate time. |

Pause stays on the focus screen initially with Resume, Stop and Back; navigating while paused is allowed and displays a Resume focus banner elsewhere. Start while paused offers Resume or Stop current session; it never starts a second session. Stop is immediate, reachable, and saves positive focus time; no discard confirmation traps the user. Zero-duration sessions remain in history as `<1 second`/zero as appropriate, but do not increase duration or contributing-session counts. Failed Start leaves setup intact; failed Pause/Stop retains the authoritative state, shows retry and does not claim success. Emergency exit can release Luma's navigation guard after a storage failure, with clear “session not saved” guidance; it does not fabricate a saved Pause/Stop. Retain an in-memory emergency marker and attempt a device-local preference marker independent of Room. On next recovery, an emergency marker ends the run at its last durable checkpoint as INTERRUPTED. If both stores failed, show before exit that the durable timer may remain running; any new-process recovery of RUNNING state requires the explicit recovery choice below, so the gap is never silently accepted as saved focus.

Proposed persistence:

| Table | Purpose and important fields |
| --- | --- |
| `focus_session` | UUID, state, revision, raw started/ended wall timestamps, created/updated timestamps, terminal reason, recovered/uncertain flag. No editable notes/task linkage in v1. |
| `focus_run` | UUID, session FK/cascade, ordinal, canonical start UTC, canonical end UTC nullable, credited milliseconds, unique `(session_id, ordinal)`. Each RUNNING span is one row; pauses have no run rows. |
| `focus_runtime` | Singleton slot and session FK, monotonic start/checkpoint, raw wall checkpoint, boot identity, owning process UUID, recovery-required state and revision. Device-local, never portable. Enforces one open session transactionally. |
| `focus_command_receipt` | Device-local command UUID/payload/outcome and database generation; latest 100 retained. Independent of the active singleton and session cascade, so Stop/deletion does not remove its receipt. |
| `focus_deleted_session` | Device-local terminal-session UUID tombstones in the current database generation. Prevent an evicted old Start from recreating a deleted session. Clear only when replacement/reset rotates generation. |

Maintain a durable device-local database generation UUID independently of active runtime; every mutation carries its captured generation. Replacement/reset rotates generation atomically with database replacement/clearing, and cancels pending coordinator work. Old-generation writes fail before touching data even if cancellation races. Tombstones are not exported; restore replays cannot carry valid old-generation write authority. Confirmed terminal deletion creates its tombstone in the deletion transaction.

Store integer milliseconds and stable UUID identities. Terminal total equals the sum of its runs. Validate nonnegative durations, canonical end = canonical start + credited milliseconds, run/session identity, terminal/open consistency and overflow at both write and backup boundaries. Terminal rows cannot be reopened. Archive adapters must validate the same rules. Device-local allowed packages live separately in preferences; they are not session content.

## F16-R02 — Timing, lifecycle and recovery

Android [`SystemClock.elapsedRealtime()`](https://developer.android.com/reference/android/os/SystemClock) supplies duration, including device sleep, within a boot; inject an equivalent pure domain sample. UI ticks display derived time and never increment the saved total themselves. While a run is active, the stopwatch keeps counting when the screen turns off, a selected app opens, or the user leaves via Android controls. It measures an explicitly running session, not proven attention or foreground-app compliance. Show this in setup/history guidance.

Persist on Start/Pause/Resume/Stop, foreground checkpoint every **5 seconds**, and lifecycle background transition. Do not poll app usage, run periodic background work, request a wake lock, start a foreground service or require notification permission just to keep elapsed time. Background gaps use the saved monotonic anchor when continuity is valid. No background notification or exact completion alarm is promised for this count-up v1.

Reconcile before navigation/action replay:

1. Same boot identity and nondecreasing monotonic clock: reconstruct the open run from saved monotonic start. Ordinary resume in the same process continues counting. On new-process recovery of RUNNING state, present “Continue including elapsed time” or “End at last saved checkpoint”; do not accept actions/navigation or finalize the unobserved gap until the user chooses. The first choice explicitly accepts the reconstructed span; the second finalizes INTERRUPTED at the checkpoint. This also handles force-stop and a lost emergency marker. A background process is not required for this arithmetic.
2. Changed/unavailable boot identity, regressed monotonic clock or wall-clock discontinuity: credit only through last durable checkpoint, finalize INTERRUPTED and require a new explicit Start. Never credit shutdown time or guess a missing interval. A paused session remains paused across reboot; it has no open run to recover.
3. Detect wall discontinuity when the difference between raw wall delta and monotonic delta exceeds **2 seconds** at a sample. Do not pause for a timezone change alone. Conservatively use the previous durable checkpoint for anomalous runs and label the gap; normal NTP-sized corrections below the threshold use canonical elapsed projection. This can omit background time since the last checkpoint and must be disclosed, rather than presented as complete focus time.

Canonical run timestamps use wall time at run start plus valid monotonic duration; wall-clock sampling never subtracts credited time. Keep raw observed timestamps separately for diagnostics/display. Ordinary activity recreation/language changes preserve the same running session ID. Do not silently credit both a reconstructed open run and its checkpoint as separate intervals. On an ambiguous committed mutation, reconcile by stable command ID before retrying.

Every checkpoint must match database generation, session/run identity and action revision. Within a valid boot, accept only a strictly newer monotonic checkpoint; equal samples are no-ops and older samples cannot overwrite newer state. If a newer checkpoint commits before an older Pause/Stop sample, the action returns Conflict and resamples; it cannot shorten credited time. Terminal transitions, checkpoint advance and singleton/receipt changes serialize in transactions. A post-Stop checkpoint never reopens a run. UI calls share one coordinator, but database ordering is authoritative across windows/races.

## F16-R03 — Focus screen, settings and allowed apps

Entry points: Today Focus card with Start/Resume, More → Focus with history, and Settings → Focus mode. Running screen shows elapsed time, Pause, Stop and selected-app shortcuts; no primary bar, Quick Capture, settings or unrelated navigation is actionable. Back/predictive Back/hardware Back shows “Pause or stop focus to leave” with reachable Pause/Stop controls. No punishment, forced delay or hidden Stop. Focus settings are editable only with no running session; paused edits apply on the next Resume.

Use a shell-level guard after loading authoritative open-session state. It intercepts primary/secondary navigation, capture, widget launch into Luma, notification routes, restored back stack and `onNewIntent`, rather than just popping the current route. Queue at most one pending Luma destination and offer it after Pause/Stop; never replay a capture/save/mutation automatically. Existing drafts remain intact. While initial load/recovery fails, show a neutral loading/error gate with retry and emergency exit; do not flash underlying private content or guess that no session exists.

An allowed app opens only on an explicit shortcut tap while RUNNING. Save elapsed state before handoff; revalidate that its package is selected and currently launchable, then resolve a launcher intent. No timer pause on successful handoff; returning restores the running focus screen. Catch missing/disabled/security launch failures without crashing or losing time. Deduplicate multiple launcher activities by package. Omit Luma itself, unlaunchable apps and work-profile apps from the personal-profile picker; explain the profile scope. Show app label with package identity where names collide. Persist stable package names, resolve labels/icons on device, and mark stale selections unavailable with Remove. Empty default is valid; there is no default broad allowlist. Changes from another window are read before launch. Selection means permitted shortcut access in v1, not proof that Android is blocking everything else.

System Home/Recents/shade, incoming calls, emergency functions, lock screen and user force-stop remain available. Never continually relaunch from the background. If F06 app lock exists when implemented, authenticate before exposing focus/history; Stop/Pause reachability must be reviewed with its gate. No new sensitive timer broadcast/action receiver is needed; future external commands require their own replay/privacy design.

## F16-R04 — Today totals and weekly comparison

Saved totals include FINISHED and INTERRUPTED sessions' **credited** positive run time. Mark interrupted contributors and omitted gaps in the detail view. RUNNING/PAUSED time appears separately as “In progress — not included in saved totals”; Stop commits it into saved totals exactly once. No estimation of attention or subtraction based on usage imports.

For a selected local date in the viewing `ZoneId`, split every canonical run against `[date.atStartOfDay(zone), nextDate.atStartOfDay(zone))`. Sum overlap milliseconds, not session wall span. A run 23:50–00:20 contributes 10 minutes to the first date and 20 to the next. Compute local day boundaries independently; never assume 24 hours. A timezone change re-buckets historical intervals consistently with Today/Review, preserving total elapsed time. Historic session start timezone may be displayed as context but is not the aggregation zone.

Today gets an independent Focus card: saved focus time, count of distinct positive-time contributing sessions, Start/Resume, View sessions and an in-progress line when applicable. On a historical selected date show that date's total/history but Start always starts **now**, never backdates a session. Empty success is `0 min` and “No saved focus sessions”; load failure is unavailable with retry, never zero. Do not alter existing daily summary cache, entry counts, Timeline, Calendar or task/habit state.

Weekly Review gets saved focus total, seven date bars (only elapsed dates in a current week), contributing-session count, previous comparison total, absolute difference and a source action to focus history for the exact period/zone. Reuse `reviewPeriod` ISO Monday–Sunday and matching weekdays; do not impose locale-specific week starts. Current week/today are partial and previous matching weekdays are full recorded days, matching existing Review semantics. Future bars are unavailable. Weekly session count is distinct session IDs contributing positive time in the range, not the sum of daily counts. Never invent a global “completed sessions” count that disagrees with this definition.

For current `C` and previous `P` milliseconds: delta = `C-P`; percent = `100*(C-P)/P` only when `P>0`. For `P=0,C>0`, show “No focus time recorded in comparison period” and absolute increase; for both zero show “No change” with no percent. Round displayed percentage to one decimal; sum milliseconds before formatting duration to whole minutes, showing `<1 min` for 1–59,999 ms. Percentage and delta use unrounded values. Examples: 90 min vs 60 min → +30 min, +50.0%; 30 vs 60 → −30 min, −50.0%; 0 vs 60 → −60 min, −100.0%. A failed comparison query is unavailable, never a missing-data zero.

History is paged in 50-row pages with stable `(started_at DESC, id DESC)` order, detail runs, active/paused/terminal state, credited duration and recovery reason. Period drill-down uses overlap, not start date only; labels each row's contribution within the selected range separately from its full-session total. Session history supports confirmed deletion of terminal sessions only; deletion and report invalidation are atomic. No manual duration edits or manual backfill in v1.

## F16-R05 — Portability, privacy, performance and delivery

Export terminal sessions/runs only, with schema adapters and validation; exclude open-session content, runtime/boot/monotonic values, emergency flags and allowed-app preferences. If a paused session exists, explain that it is not included; offer Stop to include it or explicitly continue without it. Running navigation is guarded, so Pause/Stop precedes entry to backup. Never silently finalize a session to export it.

Replacement restore requires stopping an existing RUNNING/PAUSED session or explicitly discarding it as part of the existing replacement confirmation; cancel preserves it. Restore terminal history only; reject malformed durations, duplicate identities, orphan runs and portable RUNNING/PAUSED state before replacement. Legacy archives contain no focus history: replacement restore yields empty focus history, not a merge with device history. Clear runtime and invalidate focus observers after replacement; keep device-local settings according to existing preference policy, never enable stronger restriction from imported content. Reset deletes sessions/runs/runtime/preferences and prevents later stale writes from resurrecting data. F01/F05 schema/inventory changes must be integrated serially.

No network permission, telemetry, cloud service, AI, installed-app upload, Usage access dependency, accessibility permission, device-owner enrollment, DND permission or new dependency is required for personal-phone v1. Preserve minimum API **28**, compile/target **35** and JDK **17**. Keep `core:domain` Android/Room-free. EN/FR/FA, actual Persian RTL, light/dark, large text, keyboard, TalkBack and system insets apply to all new states. App names must not expose private package lists in logs/public previews.

Aggregate/query overlapping indexed run rows in Room and avoid loading all history into Compose. Proposed acceptance targets: query+mapping p95 ≤ **300 ms** for a 50,000-session fixture and ≤ **500 ms** for first history page on named representative hardware; compare with recorded baseline using the shared performance protocol. These are targets, not measured claims. No per-second database writes; UI ticks may run once per second only while visible. Reuse explicit constructor injection, not a new feature module or DI framework.

## Proposed interfaces

New declarations below do not exist yet. Place pure types in `domain/focus/FocusSession.kt`, timing/transitions in `FocusTiming.kt`, aggregation in `FocusStatistics.kt`, and repository contract in `FocusRepository.kt`.

```kotlin
data class FocusTimeSample(val wall: Instant, val elapsedMillis: Long, val bootId: String?)
data class FocusWriteContext(val databaseGeneration: UUID, val processInstanceId: UUID)
enum class FocusState { RUNNING, PAUSED, FINISHED, INTERRUPTED }
enum class FocusAction { PAUSE, RESUME, STOP }
data class FocusCommand(val commandId: UUID, val sessionId: UUID,
    val expectedRevision: Long, val action: FocusAction, val sample: FocusTimeSample,
    val context: FocusWriteContext)
interface FocusRepository {
    suspend fun currentGeneration(): UUID
    fun observeOpen(): Flow<FocusSession?>
    suspend fun start(commandId: UUID, sessionId: UUID, sample: FocusTimeSample, context: FocusWriteContext): FocusWriteResult
    suspend fun apply(command: FocusCommand): FocusWriteResult
    suspend fun reconcile(sample: FocusTimeSample, context: FocusWriteContext): FocusWriteResult
    suspend fun resolveRecovery(sessionId: UUID, expectedRevision: Long,
        choice: FocusRecoveryChoice, sample: FocusTimeSample, context: FocusWriteContext): FocusWriteResult
    suspend fun checkpoint(sessionId: UUID, expectedRevision: Long, sample: FocusTimeSample, context: FocusWriteContext): FocusWriteResult
    fun observeStatistics(range: TimeRange): Flow<FocusStatistics>
    suspend fun history(range: TimeRange?, offset: Int, limit: Int): FocusPage
    suspend fun delete(sessionId: UUID, expectedRevision: Long, context: FocusWriteContext): FocusWriteResult
}
```

`FocusSession` contains immutable session/run snapshots and revision; `FocusStatistics` contains milliseconds, distinct contributing count and interrupted-contributor count. `FocusPage` contains rows, offset, total; range rows expose full duration and range contribution. `FocusWriteResult` is Applied(snapshot or deleted ID), AlreadyApplied(snapshot or deleted ID), Conflict(current snapshot), or typed Invalid/Unavailable/RecoveryRequired. `FocusRecoveryChoice` is CONTINUE_ELAPSED or END_AT_CHECKPOINT. Coordinator generates one process UUID per application process; `reconcile` detects an owner change for RUNNING state and atomically marks recovery required before updating ownership. Reject checkpoints/actions until explicit resolution; process death during the prompt must not clear that durable requirement. Unknown boot/anomaly and emergency-marker paths finalize conservatively instead. Fresh actions capture `currentGeneration()` and the process UUID; an old queued action must never refresh its generation automatically. Each retained command ID has one payload; a reused ID with a different payload is Invalid. Persist command receipts in the independent local receipt table, retaining latest **100**; older replays still cannot reopen terminal rows/create duplicate or tombstoned UUIDs. Checkpoints do not advance the user-action revision; actual transitions do. All writes/receipts/singleton changes share the owning transaction. Resolve the exact Kotlin result wrappers during FM01 without changing these semantics.

`FocusPreferences` in app exposes observed `Set<String>` package selection and suspend replacement; `AllowedFocusApps` resolves selectable apps and explicit launch outcomes. `FocusCoordinator` owns samples/reconciliation/checkpoints; `FocusNavigationGuard` consumes authoritative open state and handles routing only. Neither guard nor Compose calculates saved statistics independently.

## Scope decisions and exclusions

- FM08 must resolve whether the user needs strict device-wide restrictions before the feature can be advertised as satisfying the full original restriction request. Personal-phone shortcuts alone cannot close it.
- No Pomodoro cycles, countdown target, alarms, scheduled focus, goals/streaks, mood capture, task linkage, widgets, automatic app blocking, automatic DND, website/domain filtering, attention scoring or cloud synchronization in this proposal.
- Tests, schema export, builds, lint, runtime actions and evidence are phase 2 work under the shared workflow. Every new scenario starts NOT RUN.
- Before implementation, reconcile current source paths/schema versions, chosen enforcement scope and proposed stopwatch/background counting semantics with the execution request. Documentation review does not authorize product changes.
