# Luma — Prioritized Feature and Improvement Proposal

Date: 26 October 2026

Baseline: working-tree app version **1.2.2 (5)**

Status: proposed roadmap for review; no feature implementation is included.

Execution documents: [specifications, plans and QA for all 14 tasks](FEATURE_ROADMAP_2026-10-26.md). Each task separates source-only implementation from Gradle, automated, emulator/device and manual QA.

## Recommendation

Protect saved records, finish release acceptance, and improve retrieval before expanding the product. The highest-value new capabilities are private reminders, an optional app lock, and recurring tasks. These build on Luma's existing daily-use workflows while preserving its local-first purpose.

This ranking assumes the next goal is a dependable Android release for people recording private everyday information. No user interviews, usage analytics, market study, or new device benchmarks were performed. Importance is a product judgment based on repository evidence, potential data loss, breadth of benefit, and dependencies; it should be revisited after user feedback.

## Current product and constraints

The [app build configuration](../app/build.gradle.kts) uses Kotlin, Jetpack Compose, Java 17, compile/target API 35, minimum API 28, release shrinking, WorkManager, and separate domain, data, usage, and health modules. Version and SDK values describe the checked-out configuration, not a recommendation to keep or upgrade them.

Luma already provides mood check-ins, journals, memories, photos, people, places, counted habits, one-time tasks with optional due dates/times, widgets, daily summaries, weekly reviews, search, manual backup/restore, and optional usage/step imports. English, Persian, and French are already supported. These are foundations to improve, rather than proposed new features.

The latest [Calendar scope](CALENDAR_TASKS.md) is **scheduled tasks and saved memories only**. Habits remain available under More and through existing entry points. This proposal preserves that decision. Existing [release validation](VALIDATION.md) and feature-specific acceptance records contain unfinished checks; source completion does not establish release readiness.

All proposed features should work offline where possible, keep integrations optional, and preserve existing records and draft recovery. Any change to stored models needs migration and backup compatibility work. Account requirements, advertising, automatic uploads, continuous location tracking, and mandatory AI are outside this proposal.

## Ranking method

- **P0 — Release gate:** protect data and verify critical behavior before a broader release.
- **P1 — High importance:** remove substantial friction or strengthen privacy in everyday use.
- **P2 — Medium importance:** deepen value after the foundations and daily workflows are reliable.
- **P3 — Later investigation:** useful possibilities with more uncertainty or wider technical scope.

Rows are sorted from most to least important, including within each priority. Effort is relative: **S** means a contained change, **M** spans several screens or layers, and **L** involves substantial persistence, platform, or compatibility work. Estimates include relevant verification but are not calendar commitments. Confidence describes the strength of the evidence for prioritizing the work, not implementation certainty.

| Rank | Priority | Proposal | Category | Effort | Confidence | Why this position |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | P0 | Prove backup and restore resilience | Reliability improvement | L | High | Losing private life records is the most consequential failure. |
| 2 | P0 | Complete current release and Calendar acceptance | Quality improvement | M–L | High | Current changes have broader runtime and manual checks outstanding. |
| 3 | P1 | Make search fast and search entry content | Improvement + extension | L | High | Long-term records are valuable only when users can retrieve them. |
| 4 | P1 | Deliver optional habit and task reminders | Existing backlog + new feature | L | High | Tasks have due times but no alerts; habit reminders remain pending. |
| 5 | P1 | Add password-protected portable backups | Privacy feature | L | High | Current exports include private text and photos without encryption. |
| 6 | P1 | Add optional app lock and private previews | Privacy feature | M | Medium | Protects casual access to sensitive records on a shared or unlocked device. |
| 7 | P1 | Close accessibility and layout gaps | Usability improvement | M–L | High | Every core flow must remain usable with large text, RTL, and assistive input. |
| 8 | P2 | Add recurring tasks | New feature | L | Medium | Reduces repeated manual entry once reminder and date behavior are dependable. |
| 9 | P2 | Show backup freshness and offer backup nudges | Reliability + usability | S–M | Medium | Makes the existing manual backup easier to remember and understand. |
| 10 | P2 | Add monthly reflection and user-written review notes | New feature | M | Medium | Builds lasting value from the existing summaries and weekly review. |
| 11 | P2 | Add reusable capture templates | New feature | M | Medium | Speeds repeated journaling without adding permissions or services. |
| 12 | P2 | Add selective, readable exports | New feature | M | Medium | Lets users use chosen records outside a replacement-backup archive. |
| 13 | P3 | Extend optional health imports | Integration extension | L | Medium–low | Sleep/workout context could help reflection, but adds consent and source complexity. |
| 14 | P3 | Assess build and dependency modernization | Engineering improvement | M–L | Medium | Worth a controlled review; upgrades alone do not establish user benefit. |

## Proposed scope and acceptance

### 1. Prove backup and restore resilience — P0

**Evidence:** [Backup format](BACKUP_FORMAT.md) documents validated replacement restore, private photo staging, rollback, and limits. It also explicitly leaves lifecycle, concurrency, and power-loss behavior pending. These are verification gaps, not proof that the current implementation loses data.

**Proposal:** Exercise interrupted export/restore, low storage, missing photos, malformed archives, concurrent photo cleanup, and process restart using synthetic records in an isolated installation. Make progress and recovery messages actionable wherever these checks reveal a gap. Preserve the existing preview and explicit replacement confirmation.

**Acceptance:** Failure before the replacement transaction commits preserves the original dataset. Restart after a committed replacement opens a consistent restored dataset with valid photo references. Cancelled validation makes no live changes. Corrupt archives are rejected, incomplete exports are never reported successful, and old supported archive versions retain their documented behavior.

**Dependencies / tradeoff:** Requires disposable device fixtures and failure injection. Stage this before adding further archive formats; the effort buys confidence in the current recovery path.

### 2. Complete current release and Calendar acceptance — P0

**Evidence:** The [25 October Calendar QA record](validation/2026-10-25-calendar-agenda.md) reports 456 passing host tests, successful APK assembly and lint, but broad connected and manual acceptance remains pending. A later focused habit-rendering check does not certify the complete Calendar matrix. Older [task acceptance](TODO_ACCEPTANCE_2026-10-19.md) also records unresolved Timeline suite failures that need fresh reproduction or evidence of resolution.

**Proposal:** Consolidate open acceptance work into a current release checklist linked to one source revision. Execute Calendar navigation, task completion/Undo, memory membership, paging, restore invalidation, midnight/timezone handling, real integration permission flows, and reset/reopen on disposable data. Reproduce older failures before deciding whether they require fixes.

**Acceptance:** Every required scenario has a dated pass or a clearly justified release decision. Evidence identifies the build, device, and dataset. Confirm release-build startup and core capture after shrinking. Do not infer runtime acceptance from test compilation or historical passes.

**Dependencies / tradeoff:** Needs a working isolated emulator/device and representative hardware. This delays scope expansion while reducing release uncertainty. Accessibility verification belongs here; any resulting product changes are tracked under rank 7.

### 3. Make search fast and search entry content — P1

**Evidence:** [Search SQL](../core/data/src/main/kotlin/com/luma/lifeos/data/search/SearchSql.kt) matches titles and linked catalog names using substring queries. Journal bodies and notes are outside the current lookup scope. [Recorded performance](VALIDATION.md) measured roughly 2.0–2.2 seconds per page for some 50,000-entry searches on an emulator; this is historical evidence, not a fresh measurement of today's build.

**Proposal:** First profile current count/page queries and improve the existing lookup. Then extend it to journal bodies, memory notes, and task titles/notes with type filters and short matching snippets. Evaluate a local full-text index as one option; prototype Persian, French, accent, substring, and punctuation behavior before selecting it. Room supports FTS-backed entities, but index support alone does not establish the required matching semantics. [Android Room documentation](https://developer.android.com/training/data-storage/room/defining-data).

**Acceptance:** Establish a reproducible 50,000-entry benchmark on named hardware. Proposed target: p95 under 300 ms for repository first-page retrieval, excluding typing debounce. Preserve filter combinations, ordering, cancellation, and source navigation. Verify edits, deletes, and restores update results; run multilingual fixtures and migration/backup checks for any new index.

**Dependencies / tradeoff:** Rebaseline before optimizing. Content search and task inclusion require new query contracts; an FTS implementation may change substring behavior and must not silently narrow existing matches.

### 4. Deliver optional habit and task reminders — P1

**Evidence:** [Habits tasks](HABITS_TASKS.md) marks T27/T28 reminder work pending. [Task due-time behavior](BACKUP_FORMAT.md) stores floating civil times without scheduling alerts. Existing reminder tables are infrastructure, not a shipped notification system.

**Proposal:** Complete the existing habit planner and reminder backlog first. Reuse the platform delivery layer for optional task alerts, with snooze, completion, quiet hours, cancellation, and a clear enabled/blocked status. Default notification text should hide private names; users can opt into more detail. Ask for notification access when a user enables reminders. Android 13+ requires permission for ordinary notifications. [Android notification permission documentation](https://developer.android.com/develop/ui/compose/notifications/notification-permission).

**Acceptance:** Denial leaves capture and task/habit management usable. Completed/deleted items do not generate stale alerts; repeated actions do not duplicate writes. Verify reboot, timezone/DST changes, edited due times, restore, quiet hours, and revocation. State delivery limitations honestly. Keep restored reminders disabled until explicitly re-enabled.

**Dependencies / tradeoff:** Rank 2 and the existing reminder plan. Decide approximate versus exact delivery before implementation; do not promise alarm-clock precision as part of this proposal. Preserve date-based overdue behavior unless a separate product decision changes it.

### 5. Add password-protected portable backups — P1

**Evidence:** [Current archives](BACKUP_FORMAT.md) contain readable private content and photos. Existing hashes detect corruption but do not provide encryption or authenticity.

**Proposal:** Add an opt-in encrypted archive with password confirmation, clear recovery guidance, and authenticated encryption. Use established cryptographic primitives and a reviewed password-derivation design; AES-GCM is among Android's recommended cipher options. Retain readers for supported legacy archives. [Android cryptography guidance](https://developer.android.com/privacy-and-security/cryptography).

**Acceptance:** Restore works on a different device with the password and no original device key. Wrong passwords and modified ciphertext fail before any replacement. Passwords never enter logs or persistent preferences. Confirm interruption, archive-size limits, temporary-file cleanup, compatibility, and a clear warning that a forgotten password cannot be recovered by Luma.

**Dependencies / tradeoff:** Rank 1. Portable recovery cannot depend solely on a key confined to the original device. Password handling increases responsibility and needs focused review.

### 6. Add optional app lock and private previews — P1

**Evidence:** [Privacy settings](../app/src/main/java/com/luma/lifeos/ui/privacy/PrivacySettingsRoute.kt) explain storage/access and provide data reset, but do not offer an app lock. Widgets already default to summary mode; preserve that privacy choice.

**Proposal:** Offer system biometric/device-credential unlock, a configurable background timeout, and hidden content in recent-app previews. Ensure widget links and other app entry points reach the lock before exposing private records. Explain screenshot protection as a separate user choice.

**Acceptance:** Cold start and timeout lock correctly; cancellation never reveals content. Unlock resumes the intended screen without duplicate saves or lost drafts. Test device-credential changes and unavailable biometrics. Preserve intentionally public widget summaries and existing per-instance detail choices.

**Dependencies / tradeoff:** Requires a clear lock policy and entry-point inventory. An app lock protects UI access; encryption of the database and photo files would require a separate threat model and design.

### 7. Close accessibility and layout gaps — P1

**Evidence:** [UI redesign tasks](UI_REDESIGN_TASKS.md) and [Calendar acceptance](validation/2026-10-25-calendar-agenda.md) retain manual TalkBack, RTL, large-text, window, and keyboard checks. Shared editor components and three existing translations give this work broad reach.

**Proposal:** Use the acceptance findings to fix focus order, control announcements, clipping, contrast, error discovery, and editor actions obscured by the keyboard. Improve expanded-window layouts where current behavior hinders reading or editing. Preserve language and draft state during recreation.

**Acceptance:** Capture, save/cancel, completion/Undo, search, Calendar, backup, and privacy actions remain reachable at 200% font scale on narrow screens and in landscape. Verify English/French/Persian, RTL, light/dark themes, TalkBack, keyboard navigation, and resizable windows. Record human-observed results alongside automated checks.

**Dependencies / tradeoff:** Coordinate with rank 2. Prioritize proven obstacles over another broad visual redesign; actual scope and effort depend on the audit.

### 8. Add recurring tasks — P2

**Evidence:** The [task model](../core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoModels.kt) represents individual tasks with optional civil due dates/times. Recurrence is outside the current [Calendar scope](CALENDAR_TASKS.md).

**Proposal:** Start with daily, selected weekdays, and monthly recurrence. Separate a series from its occurrences so completion, skips, and rescheduling retain history. Make “this occurrence” versus “future occurrences” explicit. Reuse task views and completion behavior; habits remain a separate counted behavior feature.

**Acceptance:** Repeated taps and restart do not create duplicate occurrences. Define month-end, DST, travel, missed-occurrence, and series-edit rules before implementation. Verify occurrence changes across Today, Calendar, task lists, widgets, reminders, backup, and restore.

**Dependencies / tradeoff:** Ranks 2 and 4; needs domain/schema/archive changes. Begin with a small recurrence vocabulary to contain edge cases.

### 9. Show backup freshness and offer backup nudges — P2

**Evidence:** Backup is a manual Settings flow and Android automatic application backup is disabled in the [manifest](../app/src/main/AndroidManifest.xml).

**Proposal:** Display the last successfully completed export time, explain that it does not guarantee the external file still exists, and offer a dismissible in-app nudge when it becomes old. Make encrypted export available once rank 5 ships. Consider scheduled exports to a user-selected provider only as a later extension after provider reliability is demonstrated.

**Acceptance:** Failed/cancelled exports never advance the success timestamp. Restore does not imply a fresh backup of the current device. Nudges can be disabled and reveal no entry content. Destination loss has an actionable message.

**Dependencies / tradeoff:** Rank 1; optional notification nudges depend on rank 4. An in-app reminder is smaller and easier to trust than unattended export to arbitrary document providers.

### 10. Add monthly reflection and user-written review notes — P2

**Evidence:** Luma already has deterministic [weekly review models](../core/domain/src/main/kotlin/com/luma/lifeos/domain/review/WeeklyReview.kt) and source navigation. Historical review p95 was 364 ms against a 300 ms target in [validation](VALIDATION.md).

**Proposal:** First remeasure and improve weekly performance where needed. Add a monthly view with mood, habits, notable memories, and recorded activity/usage, plus an optional private reflection note. Keep metrics traceable to contributing records and make incomplete coverage visible.

**Acceptance:** Missing values remain distinct from zero, current partial periods use clear comparison rules, and changing source records refreshes derived values. Reflection notes survive restart and backup/restore. Avoid clinical conclusions or causal claims from correlations.

**Dependencies / tradeoff:** Ranks 2 and 3's performance discipline. Longer periods increase query cost; start with existing deterministic summaries rather than generated interpretations.

### 11. Add reusable capture templates — P2

**Evidence:** Journals already support reusable tags, and capture editors preserve drafts and optional context; see the [product overview](../README.md).

**Proposal:** Let users save simple journal prompt sets such as morning planning or evening reflection. Start with title/body scaffolding and optional tags, reachable from the existing capture flow. Offer a few optional starter templates with complete translations.

**Acceptance:** Applying or changing a template never silently overwrites draft text. Saved entries remain independent when a template changes. Templates round-trip through backup, work offline, and support Persian RTL and large text.

**Dependencies / tradeoff:** Editor recovery checks from rank 2. Keep the first version simple; avoid a general form builder.

### 12. Add selective, readable exports — P2

**Evidence:** The current [portable archive](BACKUP_FORMAT.md) is intended for replacing saved app data; it has no merge mode and is not a reading format.

**Proposal:** Export a selected date range or individual journal/memory entries as Markdown/text, with optional structured CSV for numeric observations. Preview included data and make names/photos optional. Treat readable export separately from restorable backup; do not promise re-import.

**Acceptance:** Exported Unicode, multiline text, timestamps/timezones, units, and missing values are unambiguous. Only selected records appear. Sharing follows an explicit user action; cancel/failure does not change records. Verify CSV escaping and spreadsheet formula handling if CSV is included.

**Dependencies / tradeoff:** Rank 1's safe file handling. Each additional output format adds compatibility and presentation work, so ship text/Markdown first.

### 13. Extend optional health imports — P3

**Evidence:** The [current product](../README.md) imports steps; sleep, workouts, and background health reads are deferred. Current native-provider consent/data acceptance still has open work.

**Proposal:** After verifying existing step imports on a real provider, investigate user demand for sleep and workout summaries. Keep permissions granular, each source opt-in, and retained-history behavior explicit. Add foreground import first; assess background access separately.

**Acceptance:** Repeated imports do not duplicate records, corrections are reconciled within a documented window, revocation stops reads, and unavailable data remains unavailable. Verify source overlap, midnight spans, timezone handling, restore, and review units with native provider fixtures.

**Dependencies / tradeoff:** Rank 2 and a separate platform/dependency compatibility check. This expands the sensitivity and complexity of imported data; do not bundle it with unrelated upgrades.

### 14. Assess build and dependency modernization — P3

**Evidence:** [App Gradle](../app/build.gradle.kts), the [version catalog](../gradle/libs.versions.toml), and [CI](../.github/workflows/android.yml) define the current SDK/plugin/library setup. The catalog notes constraints on newer Health Connect dependencies. Historical release validation records R8 metadata warnings; their current status needs rechecking.

**Proposal:** Audit compatibility, release shrinking, warnings, reproducible builds, and distribution requirements for the intended release channel. Build a staged upgrade plan using official release notes and compatibility tables. Prioritize changes with a concrete requirement, defect, or supported-device benefit.

**Acceptance:** Each upgrade preserves migration/restore behavior, language switching, optional integrations, widgets, and minified release startup. CI installs required SDK platforms explicitly and publishes useful test/lint artifacts. Report measured size/performance changes and unresolved warnings.

**Dependencies / tradeoff:** Rebaseline rank 2 before a broad toolchain change. This proposal does not select “latest” versions or assert current store eligibility. Escalate this item if a verified security issue, compatibility blocker, or release-channel deadline requires it.

## Suggested delivery order

| Stage | Scope | Exit condition |
| --- | --- | --- |
| A — Dependable release | Ranks 1–2; urgent fixes discovered under 7 or 14 | Recovery evidence and current release acceptance are recorded against the intended build. |
| B — Daily usefulness and privacy | Ranks 3–7 | Retrieval is benchmarked; reminders handle denial and stale actions; encrypted recovery and lock flows pass device checks. Ship each independently when ready. |
| C — Reduce effort and deepen reflection | Ranks 8–12 | Each chosen feature has defined behavior, migration/archive coverage where needed, and observable user value. |
| D — Investigate selectively | Ranks 13–14 | Demand or a concrete technical requirement justifies the scope before implementation. |

Priority does not require one large release. Rank 9's small in-app backup nudge can follow Stage A if capacity allows, without changing the overall importance ranking.

## Decisions to settle before implementation

1. Confirm whether the next milestone is a private daily-use build or a wider release; that changes the breadth of the rank 2 matrix.
2. Set the reminder delivery promise, quiet-hour defaults, and task-timezone policy. Keep current civil dates/times and date-based overdue semantics until explicitly changed.
3. Choose password/recovery expectations for encrypted archives and the precise app-lock timeout policy.
4. Validate demand for recurring tasks, monthly reflection, and health extensions with a few concrete user workflows before committing to the larger items.

Track success through reproducible benchmarks, isolated-device acceptance, and voluntary user feedback. No telemetry or analytics service is required by this roadmap.
