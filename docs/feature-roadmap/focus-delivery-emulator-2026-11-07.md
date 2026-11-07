# Focus delivery — separate emulator and device test plan

Date: 2026-11-07 · Status: PLANNED — ALL CASES NOT RUN

[Tasks](../FOCUS_DELIVERY_TASKS_2026-11-07.md) · [Spec](../superpowers/specs/2026-11-07-focus-delivery-design.md) · [QA prerequisites](focus-delivery-qa-2026-11-07.md) · [Source plan](../superpowers/plans/2026-11-07-focus-delivery.md)

## Entry and isolation

This is Phase E, **after every implementation task and Q01–Q05 build/host prerequisites**. Never run emulator tests inside FD01–FD12. App/Room instrumentation authoring belongs to Q; execution belongs here. Missing prerequisites block this phase.

Before device work, load the available Android CLI skill and inspect supported Android Studio device tooling. Use Android Studio MCP for workspace/terminal operations; run shell commands and device interactions sequentially. Do not assume a device, serial or package from an earlier run.

- Use a dedicated disposable AVD with synthetic data only. Identify serial, API, boot identity, locale, timezone, font scale, navigation mode, app/test targets and candidate APK hashes.
- Inspect actual package IDs in Q04 artifacts. Debug must be com.luma.lifeos.qa; the minified validation artifact must use com.luma.lifeos.qa.minified. Do not install ordinary productionRelease over com.luma.lifeos.
- Verify core:data instrumentation's actual target package/sandbox before running it; never infer isolation from the app's debug suffix.
- Reset/restore, time changes, reboot, force-stop and process kill are authorized only on the disposable validation installation/AVD. Never use personal records or the user's real app installation.
- Keep separate fixtures or fresh snapshots for destructive cases. Restore clock/timezone/locale/font and notification state after each case.
- Baseline evidence: screenshot, saved-row count/totals and unrelated-fixture hashes. Compare after cleanup; do not delete pre-existing user sessions.
- Capture commands/exit codes, logs/XML, screenshots or recordings, SQL-derived expected totals and timestamps under the Q01 evidence directory. Redact synthetic secrets if needed; do not put real private names in logs.
- For long waits, use intervals no longer than 60 seconds and keep progress visible. Delay alone is never success; inspect resulting state.

## Required matrix

| Surface | Required coverage |
| --- | --- |
| Baseline session/task/history/restore journey | API 28 and one available modern API 35/36/36.1 |
| Notification boundary | API 33 permission deny/grant/revoke plus API 28 channel behavior |
| Target SDK behavior | API 35; add available API 36/36.1 observation |
| Lifecycle/recovery | Full native baseline on API 28 and one modern API; force-stop, process death and reboot recorded separately |
| Presentation | EN/FR/FA, light/dark, portrait/landscape, compact/large window, 1×/2× font on modern API; minimum-API smoke separately |
| Accessibility | TalkBack, keyboard navigation and IME behavior on modern API; keep Pause/Stop/Done reachable |
| Minified | E12 smoke and protected navigation on the separately isolated minified artifact |

Record each cell PASS/FAIL/BLOCKED/NOT RUN/N/A. Unsupported platform scope can be N/A with reason; unavailable tools/APIs are BLOCKED. API 36.1 is not assumed available. Do not extrapolate a single emulator pass to every matrix cell or physical Android vendor.

## E01 — First start, validation and scope comprehension

**Fixture:** empty Focus history plus ordinary synthetic task/tag catalog.

1. Open Focus from Today and More. Initial blank intention is neutral; Start is disabled.
2. Edit then blur an invalid intention; observe the error. Attempt invalid IME submission; no record is created.
3. Enter a valid intention with no tag and start count-up. Verify exactly one open session and correct metadata.
4. Start Timed with 25-minute convenience; separately boundary inputs 0/1/180/181. Only 1–180 is accepted.
5. Open tag disclosure: at most three recent choices inline, View tags accessible; keyboard does not hide Start.
6. Explain/observe scope: leaving Luma keeps counting; other apps remain available; shortcut configuration does not enforce device-wide blocking.

**Expected:** FD-A05; empty setup calm, invalid submissions safe, scope copy visible near Start. No claim of verified attention.

## E02 — Task entry, draft handling and concurrent changes

**Fixture:** Q03 Task K, 51+ searchable open tasks and literal percent/underscore titles.

1. From saved open task, select Focus on this task. Verify task link and editable name; no timer starts yet.
2. With unsaved changes, exercise Cancel, Discard and Save-and-continue; inject failed Save and verify no navigation.
3. Search picker with literal wildcard characters; inspect first/next page order and totals. Rapidly change queries; old results must not replace new ones.
4. Use the 150-code-point title. Draft remains invalid until explicitly shortened; no silent truncation.
5. Delete or complete the selected task before Start. Verify selection conflict and explicit Remove task, not silently unlinked Start.
6. Save 10-minute and 15-minute linked terminal fixtures; task saved total = 1,500,000 ms. Add paused/open activity; saved total stays unchanged.
7. Rename task; old session name stays unchanged. Delete task; history survives, link null, origin true.

**Expected:** FD-A01/A02. Confirm actual Room data/FK and invalidation through instrumentation, not screenshots alone.

## E03 — Completion, unlink, reflection and corrections

1. Save a count-up session: Session saved / Session ended with actual credited time. Reach Done before optional forms.
2. Save an early timed Stop: Stopped before target. Target expiry: Target reached. Recover interruption: Session interrupted.
3. Done without reflection; reopen detail and verify credit unchanged. Add/edit/skip outcomes and 280/281-code-point next steps, blanks/controls/Persian/emoji.
4. Inject reflection-save failure; Done and timer history remain usable; retry only reflection.
5. Take a 5-minute break explicitly, end it, customize 1/60 and reject 0/61; Cancel creates no break. Break adds no saved focus.
6. Explicitly mark linked task complete; simulate conflict/I/O loss and retry. Saved focus remains one record; Stop is not replayed.
7. Unlink terminal task; origin and run bytes remain unchanged; no reassignment control.
8. Edit name/tag and rename/merge tags, including a collision requiring named confirmation. Verify reports/history refresh without changed time.
9. Delete current terminal session and return. Preserve the 7 November cleanup regression: no stale completion/reflection error and no phantom cached row.

**Expected:** FD-A02/A06, carried FX-Q05/Q10 and cleanup regression. Capture independent record hashes/totals.

## E04 — Timer and native lifecycle

Use Q03 Timer T and Midnight C with independently expected totals.

1. Run/pause/resume/Stop and target expiry foreground; race Stop/Pause against expiry and repeat delivery. Exactly one terminal result/open-slot release.
2. Exercise screen off and background with a short synthetic target, with alerts granted and denied. Inspect saved canonical endpoint and credit rather than notification arrival time.
3. Rotate/recreate; navigate through selected app shortcut and back. Running credit continues, pauses exclude time.
4. Kill process; reopen to explicit gap recovery. Exercise Continue elapsed (capped) and End checkpoint (interrupted) on separate fixtures.
5. Force-stop separately; acknowledge alerts may not arrive; reopen and reconcile without invented on-time delivery.
6. Reboot and change wall clock on disposable AVD; conservative uncertainty behavior, no fabricated target reached.
7. Repeat while break is active: same-boot recreation resumes; reboot/uncertainty ends without credit; late cancelled callback cannot revive it.
8. Exercise generic Pause/Stop/emergency during storage/report error. No lock or failed auxiliary write strands the user.

**Expected:** FD-A06/A09 plus FX-Q03/Q04. For the full 25-minute fixture use deterministic instrumentation clock where appropriate; also run a real native timed screen-off case. Injected clocks alone do not certify Android alarms/lifecycle.

## E05 — Recent work, Repeat and Review selection

**Fixture:** Q03 Recent R with ordered UUIDs and Week W.

1. Idle setup offers one latest authorized positive-credit terminal item; zero-credit/open sessions are not candidates.
2. For equal end timestamps, verify UUID-descending tie order. Interrupted candidate carries its interrupted state.
3. Tap Repeat in empty draft: editable name/tag/timer and prior next-step context, zero new records until Start.
4. Dirty draft: Cancel preserves it byte-for-byte; Replace setup prefills. Repeat does not silently replace unresolved Start.
5. Delete/edit source between card load and action; authoritative reload produces current/unavailable result, not stale Start.
6. Legacy unnamed Repeat requires a valid name; deleted/completed source task clears copied link with explanation and preserves origin.
7. Existing open session wins over recent card/navigation; Repeat cannot overwrite it.
8. Review W excludes newest global item outside range and includes a positive cross-boundary run overlap. View session opens existing protected detail; no new dashboard.
9. Save/edit/delete/reflection changes invalidate the card; optional query error offers Retry without disabling Start/valid authorized totals.

**Expected:** FD-A07/A08; validate Room query invalidation as well as Compose behavior.

## E06 — Invitations, permissions and actions

1. Fresh settings: invitations off. Enabling asks optional permission at that moment; visiting Focus does not.
2. Deny/grant/revoke permission, disable channel, then return. Status accurate; manual Focus usable.
3. Choose weekdays/time and observe one inexact scheduled occurrence. No exact-alarm prompt, network or background tick loop.
4. Deliver eligible occurrence; generic Open/Snooze 10 minutes/Disable only. Observe claim row and posted notification separately.
5. Tap Open behind lock: protected setup only, no auto-start. Dirty draft gets replacement confirmation; open Focus wins.
6. Snooze once; second/duplicate/stale token action does not reschedule repeatedly. Disable invalidates old revision/actions.
7. Quiet hours, RUNNING/PAUSED Focus, and active break each suppress a slot; no later burst of missed notifications.
8. Exercise malformed/wrong-generation/wrong-revision action intents with synthetic fixtures; no writes.
9. Configure task/habit notifications too; Focus reconciliation does not cancel their alarms or the timed-focus expiry alarm.

**Expected:** FD-A04. Permission/UI/alarm observations are native; a planner test alone is insufficient.

## E07 — Scheduling, system events and adverse delivery

1. Use 59/60/61-minute delivery fixtures: through exactly 60 eligible, 61 skipped. Snooze has its own due+60-minute deadline.
2. Use DST gap/overlap fixtures from Q03. Inspect planned instant and one-date consumed identity; do not claim native real-time DST delivery merely from injecting a planner.
3. On disposable AVD, exercise timezone/time changes, reboot, package replacement and permission return; reconcile without duplicate/catch-up delivery.
4. Simulate duplicate receiver/work delivery and process crash around claim/post. At-most-once posting attempt; a claim-before-crash may miss an alert but must not duplicate later.
5. Observe Doze/background delay separately. The expectation is accurate logical eligibility and credit, not exact physical alert timing.
6. Restore/reset while pending delivery/snooze; old generation action cannot post or mutate restored state.
7. Restore AVD system state and record it before the next case.

**Expected:** FD-A04/A09 and unchanged other reminder kinds. If a native scenario cannot be exercised, mark it BLOCKED rather than substituting a fake pass.

## E08 — Cross-section privacy and relock races

Use distinctive synthetic task-origin name/tag/next-step markers.

1. Exercise Tasks locked only, Focus locked only, both, whole-app lock and disabled-lock control.
2. Visit every entry: Task detail/picker, Today, More, Review, history, Repeat, tag manager, notification and pending navigation.
3. Task-origin session after task deletion/unlink remains protected by both sections.
4. Standalone Focus history remains visible with Focus access; mixed totals/goals/insights/context request unlock rather than partial-complete totals.
5. Protected tag names/suggestions/catalog do not leak; rename/merge checks all affected rows.
6. Relock while query, reflection, Repeat, picker or completion is suspended; no cached title/context or stale write appears afterward.
7. Unlock re-queries current state. Open protected session keeps generic timer/Pause/Stop/emergency.
8. Inspect notification/intent/log output for synthetic markers and screenshots/recents behavior according to existing lock policy.

**Expected:** FD-A03; app-side visibility and actual mutation guards both required.

## E09 — Migration, backup, restore and reset

Execute Q03-authored Room instrumentation on disposable targets and inspect independent SQL/hashes.

1. Migrate historic fixtures through current schema, including populated v7→current and v14→15. Preserve journal/unrelated rows; validate all expected tables/defaults/FKs/indexes.
2. Import archives 1–11 with correct version-specific defaults; round-trip archive 12 task links/origin, timers, reflection and goals.
3. Reject malformed/dangling/over-target archives in staging; live DB unchanged.
4. Export with paused/open Focus: terminal portable history only. Local invitation/break/runtime/receipts/aliases excluded.
5. During confirmed replacement queue stale Start/expiry/snooze/reflection/unlink/tag commands. Generation rotates; old callbacks do not affect restored records.
6. Reset only disposable installation; all new local/portable data and actions cleared. No unrelated app target changed.
7. Exercise actual Android file pickers for export/import and restore confirmation; host adapters do not certify this.
8. Recheck baseline unrelated records/photos and synthetic cleanup.

**Expected:** FD-A01/A03/A09. Do not label the seeded-journal failure fixed unless preservation is actually proven.

## E10 — Layout, accessibility and language

1. Capture setup (collapsed/expanded tags, errors, task prefill, keyboard), running/paused/recovery, completion/custom break/reflection, recent card, Review and invitation settings.
2. EN/FR/FA, light/dark, portrait/landscape, compact/large window and 1×/2× font. Check mixed Latin/digits/Persian text, long names and duration formatting.
3. Start remains reachable above keyboard/insets; scroll works without clipped controls. No overlap with status/navigation bars.
4. TalkBack heading/action labels and focus order: intention/mode/tag/Start; saved result/Done before optional prompts.
5. Keyboard tab/IME interactions and accessible picker/dialog dismissal. Controls meet target sizes and mode labels wrap.
6. Pause/Stop/Done/emergency remain reachable without optional form interaction or report success.

**Expected:** FD-A05/A06/A09. Record each surface/matrix cell; resource parity alone is not presentation acceptance.

## E11 — Reports, goals, scale and failure states

1. Independently verify Q03 T/C/W/G totals and matching-weekday comparison, goal next-Monday activation and historical-zone preservation.
2. Confirm context only within current report range, highest-day tie earliest, and no winner for empty data.
3. Observe live invalidation on save/delete/tag merge/unlink/reflection and direct observeInsights path.
4. At 50k fixture: history/task picker bounded at 50, recent query one candidate, no full client scan.
5. Record hardware, 5 warmups + 30 measured samples, raw timings and nearest-rank p95. Carry existing proposed aggregate ≤300 ms and first page ≤500 ms targets; report emulator context and unmet targets honestly.
6. Inject query/storage errors: loading/error/empty/unlock differ, stale headings/content do not persist, valid independent controls remain usable.
7. Observe no invisible per-second DB writes, service, wake lock or polling introduced.

**Expected:** FD-A08/A09, FX-Q06/Q11. Emulator timing does not certify a physical-phone performance target.

## E12 — Integrated final candidate and minified smoke

1. Create task → Focus on task → editable valid intention/tag → timed session → Pause/Resume → target reached.
2. Explicit break → optional reflection → independent task completion → Repeat → second saved session.
3. Correct metadata/merge tag → unlink → delete task → confirm provenance/access and totals.
4. Recent work → Weekly Review context → detail → export/restore → verify independent totals and no restored invitations.
5. Repeat relevant steps with denied alerts and relock between operations.
6. Run Tasks/Habits reminder, navigation, backup, Today/Review and existing Focus regressions.
7. Run the core journey and protected entry routes on the isolated minified artifact; build success alone does not pass this.
8. Delete synthetic records/restore AVD settings and compare baseline hashes. Publish exact residual failures/skips/matrix gaps.

**Expected:** FD-A09; return evidence to Q06. Any E-phase fix requires affected Q checks and E12 again on the corrected candidate.

## Instrumentation commands, only in this phase

First confirm task inventory from Q04 and actual target isolation. Commands below reflect current production flavor:

```sh
./gradlew :core:data:connectedDebugAndroidTest
./gradlew :app:connectedProductionDebugAndroidTest
```

Use class filters for diagnosis, then required complete suites; inspect test XML/counts and ignored tests. Gradle success with no discovered cases is not a pass. Device selection and installation must use the verified dedicated serial. A missing AVD/test tool is BLOCKED; do not provision a personal phone as a shortcut.

## Result ledger

| Case | Initial result | Required evidence |
| --- | --- | --- |
| E01 setup | NOT RUN | screenshots, interactions, created-record count |
| E02 tasks | NOT RUN | navigation recording + Room assertions |
| E03 completion/corrections | NOT RUN | UI + independent run/task state |
| E04 lifecycle | NOT RUN | native logs/times + SQL, separate lifecycle cells |
| E05 recent/Review context | NOT RUN | query/invalidation assertions + UI |
| E06 invitations | NOT RUN | permission/channel/alarm/action record |
| E07 adverse scheduling | NOT RUN | system-event observations + occurrence identity |
| E08 privacy | NOT RUN | marker absence + current access/write assertions |
| E09 persistence | NOT RUN | migrations/archive/pickers + unrelated-record hashes |
| E10 presentation | NOT RUN | locale/font/window/accessibility matrix |
| E11 reports/scale | NOT RUN | independent totals, raw samples/query plans |
| E12 integrated/minified | NOT RUN | final-candidate full journey + regressions |
| Strict device-wide enforcement | DEFERRED/UNMET | outside personal-phone scope |

This document records no executed tests and grants no release acceptance by itself.
