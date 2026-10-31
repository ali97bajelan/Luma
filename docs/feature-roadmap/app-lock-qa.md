# App lock v2 — QA scenarios and execution ledger

Date: 31 October 2026 · Status: **ALL SCENARIOS NOT RUN**

[Spec](../superpowers/specs/2026-10-31-app-lock.md) · [Plan](../superpowers/plans/2026-10-31-app-lock.md) · [Board](../APP_LOCK_TASKS.md) · [Test data](app-lock-test-data.md) · [Fixture](fixtures/app-lock-v1.json) · [Shared workflow](WORKFLOW.md)

## Entry, isolation and result policy

This is the QA phase of the new app-lock package. Begin after **all T01–T08 implementation source handoffs**. Test adapters/test authoring, Gradle, schema export, builds/lint/test execution, device work and defect correction belong here. T02–T08 implementation never requires previous QA PASS. During the QA wave, a card needs its own and integrated source identity, not another card's QA status; a concrete defect may invalidate dependent evidence and block acceptance.

Use the JSON's synthetic data and test-only adapters from the test-data guide. Never import that logical JSON through Luma restore. Reset baseline between independent cases. All outcomes are NOT RUN until observed; allowed results are PASS, FAIL, BLOCKED and N/A with specific rationale. Host/compile success cannot stand in for native/manual cases. Privacy leaks, unauthorized mutation, credential bypass and lost data block acceptance.

Verify package and instrumentation targets from built APK metadata before install/reset. Planned isolated flavor is `appLockQa`, expected debug package `com.luma.lifeos.applockqa.qa`; do not assume suffixes prove isolation. Use a dedicated disposable API28/API36 AVD or explicitly disposable hardware. Never install over/reset the personal app or reuse Focus QA data without establishing it is disposable. Verify `core:data` test APK target separately. Keep real credentials out of artifacts.

For every run record source SHA + dirty/new-file patch hash; implementation handoff IDs; APK hash/variant/package/version; test runner target; JDK/Gradle/SDK; device/serial/API; fixture hash/media hashes; locale/zone/clock/boot/theme/window/font; exact commands/exits/JUnit counts; actual observations; defect/retest IDs. Evidence path: `docs/feature-roadmap/evidence/<actual-run-date>/F06-v2/`. Create evidence only during actual QA.

## Shared matrix and commands

For changed interaction families run EN/FR/FA × light/dark × (320dp portrait/font2.0, 411dp portrait/font1.0, 411dp landscape/font2.0, >=840dp/font1.0), including Persian RTL, IME/insets/final button reachability, system-theme transition, TalkBack human observation and hardware keyboard. Core lifecycle/capture/recovery/preview cases run on API28 and API36. Add API33+ notification permission denied/granted, provider-unavailable fallback and absent device credential. System biometrics are not a v1 feature; do not mark missing old-F06 biometric cases as coverage of the independent password.

Commands below are **future QA instructions, not executed results**. Reconcile task names with the T08 flavor source. Dependency/SDK failures are BLOCKED, not PASS. Use dedicated device selection and the shared isolation policy before C4. Run focused tests first, then affected gates; do not repeatedly rerun unchanged broad suites.

```sh
# C1 — domain rules
bash gradlew -PdomainOnly=true :core:domain:test
# C2 — app/data host tests
bash gradlew :app:testAppLockQaDebugUnitTest :core:data:testDebugUnitTest
# C3 — compile, instrumentation assembly, lint; QA also exports changed schemas
bash gradlew :app:assembleAppLockQaDebug :app:assembleAppLockQaDebugAndroidTest :app:lintAppLockQaDebug :core:data:assembleDebugAndroidTest :core:data:lintDebug
# C4 — ONLY on verified disposable app/test targets and dedicated device
bash gradlew :app:connectedAppLockQaDebugAndroidTest :core:data:connectedDebugAndroidTest
# C5 — isolated flavor minification; arrange isolated signing before any installation
bash gradlew :app:assembleAppLockQaRelease
```

If health/usage code changes, also execute their affected unit/lint suites from WORKFLOW. Required automated result: exit 0; named required tests actually executed with no errors/failures, skips justified, lint errors/fatals zero and new warnings classified. Native results need direct evidence even if these commands pass. For populated migration/archive cases preserve legacy schema JSON; export new schemas here and compare against source migrations.

Performance checks use the shared 5-warmup/30-measurement protocol, representative hardware, baseline/candidate identities, raw samples and nearest-rank p95. Measure blank capture steps/time and first protected-frame/navigation behavior; restricted capture must add no authentication step. Record regressions rather than inventing thresholds or equating emulator latency to phone performance.

## Executable scenarios

Unless stated otherwise, precondition for each case is the freshly seeded baseline, offline mode, functioning local storage and no active Focus restriction. Repeat both coverage modes and relevant matrix cells. Named Q cases correspond one-to-one to product AL-01–AL-22. Each case records independent data/projection/permission assertions plus appropriate screenshots/video, semantic snapshots, JUnit/store observations or command logs. Credential/code screens require redacted evidence; inspect protection behavior without saving real secrets.

### AL-Q01 — Setup cancellation, validation and atomic enable

**Requirements/owners:** R01/R02/R03/R12; T02/T06. **Data:** disabled profile; password length/Unicode vectors.

**Actions:** Cancel each setup stage; submit mismatched passwords, 7/129 characters and empty Selected coverage; try valid 8/128/code-point vectors; inject failure before final commit; acknowledge recovery and complete valid setup.

**Oracle:** Invalid/cancelled/failed setup leaves Disabled, no partial credential state and all original records unchanged. Exact spaces/Unicode preserved. Only final successful confirmed transaction enables chosen policy and enters Locked. Evidence: store snapshots and setup UI flow.

### AL-Q02 — Independent offline password

**Requirements/owners:** R01/R02; T02/T06. **Data:** no phone screen lock; offline disposable device.

**Actions:** Enable and unlock with a Luma password; cancel/retry; restart without adding a device credential; complete actual generated recovery setup.

**Oracle:** Phone credential/network never required; successful Luma password alone grants requested access. Cancellation grants nothing; restart starts locked. Do not substitute a biometric prompt for this case.

### AL-Q03 — Section protection across projections

**Requirements/owners:** R03/R04/R06/R12; T01/T04. **Data:** seven fixture profiles plus each section-alone variant.

**Actions:** Inspect Today, Timeline, Calendar, Search results/facets/total, Review metrics, person history, original marked journal/mood detail and media URIs; unlock/revoke; inject a delayed pre-revocation list emission.

**Oracle:** Exact authorized IDs match independent fixture sets; no protected title/body/photo/name/date/count/tag/filter metadata or old-generation content appears. Marked source and media require Memories. People-only policy redacts identity/link fields while unrelated prose remains. Summaries are hidden or explicitly partial, and Calendar scope unchanged.

### AL-Q04 — Multi-section grants

**Requirements/owners:** R03/R04/R05; T01/T04. **Data:** Memories + Journal selected, journal_marked target; People independently locked.

**Actions:** Request marked journal with no grants, only Memories, only Journal, then both. Use mixed-list unlock section chooser. Attempt linked-person selection with People still locked.

**Oracle:** Both content grants needed for the marked journal; one explicit prompt can grant just those requested sections. No unrelated grants. Person links stay redacted without People authorization. Selecting a mixed list never silently grants every section.

### AL-Q05 — First frame and every private entry point

**Requirements/owners:** R06/R09/R12; T03/T04/T07. **Data:** Whole app Locked, cached previous screen, pending private destination.

**Actions:** Cold launch; onNewIntent; widget and notification launches; restored stack; direct media/detail route; exported widget configuration; Back/Cancel; delayed private result. Record actual first visible frames and accessibility tree.

**Oracle:** No protected frame or semantics before auth; static help/generic configuration has no records. Unlock opens valid intended destination once. Back/Cancel stays safe; malicious/unresolved routes do not fall through into private content.

### AL-Q06 — Every-time navigation boundaries

**Requirements/owners:** R05; T01/T03. **Data:** memories_locked, then relevant sections granted on Timeline.

**Actions:** Scroll/page/filter/change date; open/close related memory detail; open unrelated task detail; switch Timeline → Today; enter settings/capture; background/return; repeat Whole app departure.

**Oracle:** Related flow/scroll/filter preserves its grant. Unrelated detail revokes unneeded grants; top-level/settings/capture navigation revokes selected grants; reentry prompts. Background revokes all. Whole app does not prompt for each internal navigation while it remains visible.

### AL-Q07 — Timed exact boundaries and cold restart

**Requirements/owners:** R05/R06; T01/T03. **Data:** every timeout vector, known grant at departure.

**Actions:** Return at T−1/T/T+1; change wall clock ±1 day; cold kill/relaunch inside grace; reboot; device lock; Lock now; rotation/theme/locale recreation; two visible windows and split-screen focus loss.

**Oracle:** T−1 retains grant, T/T+1 revoke; wall clock irrelevant. Cold process/device lock/Lock now always lock. Same-process recreation retains only valid grants, no prompt loop. Remaining visible Luma activity prevents false departure.

### AL-Q08 — Capture cannot extend grace

**Requirements/owners:** R05/R07/R08; T01/T03/T05. **Data:** five-minute capture vector, then Every time profile.

**Actions:** Enter/create/retry capture before deadline, leave it open through deadline, save afterward; repeatedly reopen; use own external picker; return to history.

**Oracle:** Original deadline remains 1,300,000 ms; no pause/extension/grant from capture. History is locked at expiry. Every-time capture revokes selected grants. Picker preserves current input but never revives expired browsing access.

### AL-Q09 — Five new-entry types while locked

**Requirements/owners:** R07; T05. **Data:** each newEntries input independently; Whole app and matching selected section locked.

**Actions:** Add from lock screen/placeholder/quick-capture widget; enter valid fields/new photos; save; inspect confirmation, destination and saved-result callbacks; repeat during cooldown.

**Oracle:** Zero password steps, one distinct new ID, original records unchanged, no saved-record lookup callback/grant. Generic confirmation with no private date/title/photo/count/Open/Undo. No old selectors/suggestions/templates. New Habit definition does not log against habit_water.

### AL-Q10 — Forged create authority, duplicate saves and races

**Requirements/owners:** R04/R06/R07; T04/T05. **Data:** fixed create operation IDs, existing record IDs, lost-ack fault.

**Actions:** Supply existing IDs via route/editor result; forge/reuse a create-session token; collide ID with another record; double-tap Save; lose acknowledgment after commit then retry; revoke/change policy from second window as save begins.

**Oracle:** No read/edit/delete escalation or informative collision; new operation writes exactly once. Guard ordering allows only a legitimately started commit, denies future operations after revocation. Exact original hashes and mutation counters prove no unauthorized writes.

### AL-Q11 — Private draft lifecycle and attachment ownership

**Requirements/owners:** R08; T05. **Data:** draft_old + draft_new, red/blue/green media, denied-picker/storage faults.

**Actions:** Rotate and return from own camera/picker; genuinely depart, lock device and kill process; choose Start new, unlock/resume older draft; cancel/remove new photo; retry failed save.

**Oracle:** Defined current-input visibility retained only across safe boundaries. Genuine departure/restart shows no draft content before unlock. New blank draft never overwrites older draft. All exact fields/media ownership survive; cleanup cannot delete saved red/blue attachments. Failed storage preserves recoverable input or reports failure without silent loss.

### AL-Q12 — Existing edit relock and stale operations

**Requirements/owners:** R04/R06/R08; T04/T05. **Data:** existing journal/memory edit draft and task revision.

**Actions:** Edit then expire/revoke; attempt Save/Delete while locked; cancel and retry auth; modify source revision from another window; resume; rotate during unlock.

**Oracle:** No protected commit before authorization; draft/media preserved privately; no edit-as-new path or duplicate save. Stale revision fails without overwriting the concurrent update; supported native recreation never grants on a restored boolean.

### AL-Q13 — Persisted shared cooldown

**Requirements/owners:** R02/R12; T02/T06. **Data:** cooldown vectors, alternating wrong password/code attempts.

**Actions:** Submit failures 1–11 after each delay; submit during cooldown; restart process/reboot/change wall clock; attempt restricted capture; finally verify correctly.

**Oracle:** Exact sequence 0/0/0/0/30/60/120/240/480/900/900 seconds. In-cooldown submission neither verifies nor increments. Restart cannot clear delay/counter; correct verification resets. No data erase/permanent lockout; capture remains available. Record actual reboot accounting decision from T02.

### AL-Q14 — Fresh operation proofs and settings changes

**Requirements/owners:** R01/R02/R03/R05; T02/T06.

**Actions:** In a valid grace period open settings, change password/policy/timing/coverage, rotate code and disable; cancel/retry each; reuse an old/consumed/wrong-purpose proof and inject failed write.

**Oracle:** Fresh one-operation password proof required; settings-open proof is not blanket edit authority. Cancel/failure changes no state. Policy/credential changes revoke grants; switching coverage clears them; adding section locks it. Disable preserves records and removes protection material.

### AL-Q15 — Interrupted and successful recovery

**Requirements/owners:** R02/R10/R12; T02/T06. **Data:** old credential pair, replacement pair, before-commit fault.

**Actions:** Wrong code; correct code and mismatched replacement; interrupt before commit; retry old pair; finish successful replacement and try old password/code again.

**Oracle:** No history access from code alone. Interrupted recovery preserves former credentials. Success atomically installs new pair only after confirmation, consumes old code, invalidates grants and returns locked. Screenshots/semantics/logs do not reveal entered secrets or code.

### AL-Q16 — Previews, screenshots and semantic disclosure

**Requirements/owners:** R09/R12; T07. **Data:** previously detailed widgets/posted reminders, protected drafts and unlocked grants.

**Actions:** Enable/change coverage; inspect old/new notifications and actual launcher collections; unlock in-app; open Recents; toggle screenshots; inspect credential/code screens, photos and accessibility nodes.

**Oracle:** Refresh/redaction requested immediately; protected external previews generic even while app unlocked. Recents masked independently of screenshot toggle. Normal protected/capture screenshots follow toggle; credential/code screens always blocked. No hidden values in semantics. Record observed third-party cache limitations without claiming erasure.

### AL-Q17 — Protected existing-record external actions

**Requirements/owners:** R04/R09; T07. **Data:** valid/stale task commands, habit existing-log command, expiry vectors.

**Actions:** Tap Complete/Log while locked; cancel auth; retry then cancel action confirmation; finally authorize/confirm before expiry, replay; queue then change/delete source; evaluate at expiry; race with revocation.

**Oracle:** Cancel paths mutation delta 0; valid confirmed action changes expected record once with next revision/date, stale/expired/replayed actions no extra mutation. Log habit cannot use create exemption. No preview leak while pending; expired means now >= deadline.

### AL-Q18 — Sensitive operations, backup and restore

**Requirements/owners:** R10/R12; T08. **Data:** all supported archives, protected/fresh destination, restore fault and prior-record hashes.

**Actions:** Share/export/backup/restore/ordinary reset in both modes with no proof, cancelled proof and valid fresh purpose-bound proof; inspect actual archive members; restore into protected and unprotected installs; cancel/fail replacement.

**Oracle:** Fresh auth before sensitive operation; independent encryption/consent retained. No secret/policy/retry/runtime/draft/receipt transfer. Protected destination retains policy and starts restored content locked; fresh destination explicitly chooses protection/no-protection before showing records. Cancellation/failure preserves destination atomically. Existing draft/photo/hash/legacy upgrade assertions pass.

### AL-Q19 — Destructive forgotten-password reset

**Requirements/owners:** R10; T06/T08. **Data:** unavailable password/code, retained external backup, reset-rejected fault.

**Actions:** Open recovery erase without password, cancel each confirmation, reject reset request, then confirm both erase steps on disposable target.

**Oracle:** No read/export/disable-with-data permission. Cancel/rejected reset preserves lock/data and gives safe error. Accepted reset is not itself erasure evidence: verify empty fresh installation and removed records/media/drafts/credential state afterward. External backup unchanged. Ordinary settings reset still requires fresh auth.

### AL-Q20 — Focus and background coexistence

**Requirements/owners:** R11; T03/T08. **Data:** active Focus checkpoint, running import/timer/reminder; provider absent variant.

**Actions:** Relock during each, unlock, request capture while Focus guards navigation, use generic emergency stop; open Focus history/health/usage while protected.

**Oracle:** Saved data gated; existing work continues correctly. App unlock/capture does not bypass Focus; generic permitted emergency control reveals no history. Denied/unavailable provider remains a safe fallback and requests no new personal permissions.

### AL-Q21 — Localization and assistive use

**Requirements/owners:** R12; T06/T07/T08. **Data:** full UI matrix, Persian sample password/person, keyboard/password manager.

**Actions:** Complete setup/unlock/capture/recovery/settings with font2.0, RTL, landscape/wide layouts, IME, dark/light switch, TalkBack and keyboard/paste/password manager.

**Oracle:** All actions reachable, errors/cooldowns understandable, RTL and focus order correct, no accidental secret announcements or hidden-content semantics. No password value persisted in test snapshots. Record human observations separately from automation.

### AL-Q22 — Corrupt/unavailable stores and integrated regressions

**Requirements/owners:** R06/R10/R12; T01–T04/T08. **Data:** corrupt/missing verifier with enabled policy, unreadable preferences, delayed old-generation projection, populated prior database and empty baseline variant.

**Actions:** Launch and retry initialization; inject faults during grant/policy updates; request capture when isolation is uncertain; run upgraded/legacy/current archive and existing navigation/capture/calendar/search/Focus suites on final source; run C5 minified artifact checks.

**Oracle:** Unknown/Error never becomes Disabled by default. Private content stays gated; capture enabled only when its isolation is reliable. Retry/recovery safe; no user data lost. Required existing behaviors survive, no new minification-only bypass. Record security review and complete matrix before feature acceptance.

## Traceability and initial execution ledger

Product AL-nn maps directly to AL-Qnn. Each row expands during QA into test/matrix/result/artifact rows; no applicable requirement can disappear because one shared case passed on a single device. Owner task details/test paths are in the plan.

| Requirement | Required cases |
| --- | --- |
| LOCK-R01 | Q01, Q02, Q14 |
| LOCK-R02 | Q01, Q02, Q13, Q14, Q15 |
| LOCK-R03 | Q01, Q03, Q04, Q14 |
| LOCK-R04 | Q03, Q04, Q10, Q12, Q17 |
| LOCK-R05 | Q04, Q06, Q07, Q08, Q14 |
| LOCK-R06 | Q03, Q05, Q07, Q10, Q12, Q22 |
| LOCK-R07 | Q08, Q09, Q10 |
| LOCK-R08 | Q08, Q11, Q12 |
| LOCK-R09 | Q05, Q16, Q17 |
| LOCK-R10 | Q15, Q18, Q19, Q22 |
| LOCK-R11 | Q20 |
| LOCK-R12 | Q01, Q03, Q05, Q13, Q15, Q16, Q18, Q21, Q22 |

| Case | Result | Actual observation | Evidence | Defect/retest |
| --- | --- | --- | --- | --- |
| AL-Q01 | NOT RUN | — | — | — |
| AL-Q02 | NOT RUN | — | — | — |
| AL-Q03 | NOT RUN | — | — | — |
| AL-Q04 | NOT RUN | — | — | — |
| AL-Q05 | NOT RUN | — | — | — |
| AL-Q06 | NOT RUN | — | — | — |
| AL-Q07 | NOT RUN | — | — | — |
| AL-Q08 | NOT RUN | — | — | — |
| AL-Q09 | NOT RUN | — | — | — |
| AL-Q10 | NOT RUN | — | — | — |
| AL-Q11 | NOT RUN | — | — | — |
| AL-Q12 | NOT RUN | — | — | — |
| AL-Q13 | NOT RUN | — | — | — |
| AL-Q14 | NOT RUN | — | — | — |
| AL-Q15 | NOT RUN | — | — | — |
| AL-Q16 | NOT RUN | — | — | — |
| AL-Q17 | NOT RUN | — | — | — |
| AL-Q18 | NOT RUN | — | — | — |
| AL-Q19 | NOT RUN | — | — | — |
| AL-Q20 | NOT RUN | — | — | — |
| AL-Q21 | NOT RUN | — | — | — |
| AL-Q22 | NOT RUN | — | — | — |

## Exit gate

- [ ] T01–T08 source handoffs and all R01–R12 → case → assertion/test/manual mappings recorded.
- [ ] C1–C5 and affected regressions actually executed on current candidate; no unexplained failures/errors/skips or lint findings.
- [ ] All applicable Q01–Q22 and required matrix/native/manual/security-review obligations PASS; blockers/N/A rationale visible.
- [ ] Fixture/media/archive/schema identities and independently checked data/permission/mutation oracles attached.
- [ ] Defects fixed within QA, affected evidence invalidated and owning/dependent regressions rerun.
- [ ] Board updated to exact observed scope; feature/release acceptance withheld while any required case remains unrun or blocked.
