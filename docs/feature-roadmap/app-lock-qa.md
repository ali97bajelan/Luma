# App lock v2 — QA scenarios and execution ledger

Date: 1 November 2026 · Status: **ALOCK-QA IN PROGRESS; prior T01/T02 evidence retained; remaining scenarios and matrix cells under execution**

[Spec](../superpowers/specs/2026-10-31-app-lock.md) · [Plan](../superpowers/plans/2026-10-31-app-lock.md) · [Board](../APP_LOCK_TASKS.md) · [Test data](app-lock-test-data.md) · [Fixture](fixtures/app-lock-v1.json) · [Shared workflow](WORKFLOW.md)

## Entry, isolation and result policy

This is the single consolidated QA task for all app-lock implementation tasks. Begin after **all T01–T08 implementation source handoffs**. Test adapters/test authoring, Gradle, schema export, builds/lint/test execution, device work and defect correction belong here. T02–T08 implementation never requires previous QA PASS. Record all source handoffs and the integrated candidate identity. Reuse existing evidence where valid for that candidate; execute only missing or invalidated checks, recording shared scenario and matrix coverage once. A concrete defect may invalidate affected evidence and block acceptance.

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

## Traceability and execution ledger

Product AL-nn maps directly to AL-Qnn. Each row expands during QA into test/matrix/result/artifact rows; no applicable requirement can disappear because one shared case passed on a single device. Coverage details and test paths are in the consolidated QA task in the plan.

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
| AL-Q01 | PARTIAL (API36) | Current full app C4 passed the native credential cases `setupRejectsLengthVectorsPreservesExactInputAndUnlocksOffline`, `passwordPreservesSpacesAndDoesNotNormalizeUnicode`, `eightEmojiAreEightPasswordCharacters`, and `cancellationAndAtomicEnableFailureLeaveNoCredentialState`. On a fresh isolated install, setup showed Whole app/Every time defaults; blank submit exposed the expected 8–128-character validation; Back returned to Privacy and reopening showed blank fields/defaults. A new focused UI regression for the initial validation bounds passes 1/1 after the scroll-to-bottom fix. The valid UI setup path was not completed. | [Manual Q01 summary](evidence/2026-11-01/F06-v2/qa-manual-q01-summary.md); [RED bounds diagnostic](evidence/2026-11-01/F06-v2/c4-diagnostic-q01-no-scroll.log); [GREEN JUnit](evidence/2026-11-01/F06-v2/c4-green-q01-validation-visibility.xml); [current full app C4](evidence/2026-11-01/F06-v2/c4-app-q01-scroll-fix.xml) | The initial clipping defect is fixed and focused-retested. Later-stage cancellation, UI 7/129 boundaries, successful setup/recovery acknowledgement, enabled cold-start, and API28 coverage remain open. |
| AL-Q02 | PARTIAL (API36) | Native `setupRejectsLengthVectorsPreservesExactInputAndUnlocksOffline` passed. On a fresh isolated debug package, manual setup accepted matching synthetic Luma passwords; after force-stop and process relaunch the app displayed `Luma is locked`, a password-only Unlock dialog accepted the Luma password, and the app returned to Today. No Android credential prompt appeared; no device credential was added. The recovery value was acknowledged for the disposable flow and was not retained. The gate composition regression `wholeAppGateKeepsUnlockDialogAndSettingsNavigationAvailable` passes 1/1; it also checks covered Today semantics are absent while lock is active. | [Manual Q02 summary](evidence/2026-11-01/F06-v2/qa-manual-q02-summary.md); [dialog RED](evidence/2026-11-01/F06-v2/c4-red-app-lock-gate-dialog.log); [semantics RED](evidence/2026-11-01/F06-v2/c4-red-app-lock-gate-semantics.log); [focused GREEN JUnit](evidence/2026-11-01/F06-v2/c4-green-app-lock-gate-dialog.xml); [cleanup inventory](evidence/2026-11-01/F06-v2/qa-manual-q02-cleanup.txt) | This was app-process relaunch, not emulator reboot; network-offline, API28, and full device-credential matrix cells remain open. Current full C4 and C2/C3/C5 after the gate fix are recorded in the linked evidence. |
| AL-Q03 | BLOCKED | Domain classifier tests matched fixture record section sets and People-field classification. Actual Today/Timeline/Calendar/Search/Review/detail/media projections were not exercised. | [F06-v2 summary](evidence/2026-10-31/F06-v2/summary.md); C1 log | T04 projection integration remains untested. |
| AL-Q04 | BLOCKED | Domain policy tests covered fixture and single-section profiles; proof tests bound browse authority to the exact section request. Mixed-list UI chooser and actual protected projections were not exercised. | [F06-v2 summary](evidence/2026-10-31/F06-v2/summary.md); `c1-domain-tests.log`; `c2-host-tests-retest.log` | T03/T04 integration remains open. |
| AL-Q05 | PARTIAL (API36) | The clean isolated-package regression `quickCaptureWidgetOpensChooserWhenAppLockIsDisabled` first failed because Quick Capture was sent through `requestBrowse`, which rejects create-session destinations even with App Lock disabled. After the fix to skip browse authorization for create destinations, this chooser scenario passed 1/1 and displayed the capture chooser and its mood/journal/memory actions. The existing `dataWidgetCaptureUsesOneCurrentTimeChooserFromEitherProvider` regression also passed 1/1; both widget providers opened the chooser at the current date and all four tested create destinations completed navigation. The gate regression now verifies that, while `Luma is locked` is shown, the covered Today screen has no accessibility semantics and the Unlock dialog remains reachable. | [RED focused test/JUnit](evidence/2026-11-01/F06-v2/c4-red-quickcapture.log); [GREEN chooser JUnit](evidence/2026-11-01/F06-v2/c4-green-quickcapture.xml); [GREEN timestamp JUnit](evidence/2026-11-01/F06-v2/c4-green-widget-timestamp.xml); [gate semantics RED](evidence/2026-11-01/F06-v2/c4-red-app-lock-gate-semantics.log); [gate GREEN JUnit](evidence/2026-11-01/F06-v2/c4-green-app-lock-gate-dialog.xml); [dialog capture](evidence/2026-11-01/F06-v2/q05-app-lock-unavailable.png) | Still no first-frame screenshot/semantics matrix, onNewIntent, locked widget/notification, restored-stack or API28 coverage. |
| AL-Q06 | BLOCKED | Domain navigation tests checked grant retention/revocation rules; actual app navigation, backgrounding and re-entry flows were not run. | [F06-v2 summary](evidence/2026-10-31/F06-v2/summary.md); `c1-domain-tests.log` | T03 integration and shared matrix remain open. |
| AL-Q07 | BLOCKED | Domain timing tests passed exact T−1/T/T+1 boundaries and conservative invalid samples. Wall-clock change, process death, device lock/reboot and multi-window behavior were not exercised. | [F06-v2 summary](evidence/2026-10-31/F06-v2/summary.md); `c1-domain-tests.log` | API 28 and real lifecycle checks remain open. |
| AL-Q08 | BLOCKED | Capture was not exercised against actual grants, expiry, restricted capture or lifecycle transitions. | [F06-v2 summary](evidence/2026-10-31/F06-v2/summary.md) | T03/T05 integration remains open. |
| AL-Q09 | NOT RUN | — | — | — |
| AL-Q10 | NOT RUN | — | — | — |
| AL-Q11 | NOT RUN | — | — | — |
| AL-Q12 | NOT RUN | — | — | — |
| AL-Q13 | PARTIAL (API36) | Current full app C4 passed `sharedCooldownPersistsAcrossServiceRecreationAndBootChange`, including the fixture retry schedule, in-cooldown behavior, service recreation and injected boot-marker change. A physical reboot, protected capture and actual UI retry sequence were not exercised. | [Current app C4 JUnit](evidence/2026-11-01/F06-v2/c4-app-after-fixes.xml); [F06-v2 summary](evidence/2026-10-31/F06-v2/summary.md) | Device reboot and capture checks remain open. |
| AL-Q14 | PARTIAL (API36) | Current full app C4 passed `validatedSettingsProofChangesPolicyAndInvalidatesItsOldGeneration`; current host/native tests also cover one-use, purpose-bound proofs and exact browse-request binding. Settings UI cancellation/failure paths and the full credential/policy matrix were not run. | [Current app C4 JUnit](evidence/2026-11-01/F06-v2/c4-app-after-fixes.xml); [F06-v2 summary](evidence/2026-10-31/F06-v2/summary.md) | Manual settings and API28 coverage remain open. |
| AL-Q15 | PARTIAL (API36) | Current full app C4 passed `recoveryFailureInterruptedWriteAndSuccessPreserveOldThenReplaceCredentials`, covering wrong-code, interrupted replacement and successful recovery replacement behavior. Recovery-code display/acknowledgement UI and secret-screen handling were not completed. | [Current app C4 JUnit](evidence/2026-11-01/F06-v2/c4-app-after-fixes.xml); [F06-v2 summary](evidence/2026-10-31/F06-v2/summary.md) | Manual recovery UI and API28 coverage remain open. |
| AL-Q16 | NOT RUN | — | — | — |
| AL-Q17 | NOT RUN | — | — | — |
| AL-Q18 | NOT RUN | — | — | — |
| AL-Q19 | NOT RUN | — | — | — |
| AL-Q20 | NOT RUN | — | — | — |
| AL-Q21 | PARTIAL (API36) | Existing `LanguageFlowTest.journalDraftTextTagsAndDateSurviveLanguageChangeThenSaveAndDetail` passed 1/1 through Persian, French, and English, retaining the mixed-language draft/tag/date and saving to detail. Its journal tag toggle is scrolled into view before interaction. App Lock setup/unlock/recovery/settings, font-scale, RTL-layout, keyboard, TalkBack, dark-mode, and API28/API36 lock matrices remain untested. | [API36 connected JUnit](evidence/2026-11-01/F06-v2/c4-green-language-journal-scroll.xml) | Complete the App Lock-specific localization/accessibility matrix and human observations. |
| AL-Q22 | FAIL (API36; 22 failures) | Refreshed full app C4 JUnit has 279 cases: 256 passed, 22 failed, 1 skipped; Gradle console says 280 tests, retaining a one-test count discrepancy. Gate regression `wholeAppGateKeepsUnlockDialogAndSettingsNavigationAvailable` passes 1/1, including hidden Today semantics under the gate, Unlock/cancel and settings navigation. Q01 validation visibility, widget capture, journal readiness/multilingual journal, SearchFlow, and App Lock credential integration cases pass. The previous broad JournalCapture failure did not reproduce. Remaining failures span Memory/Mood/People/Place, SearchStates, calendar, habits, language navigation, Todo, accessibility and widgets. Core:data C4 remains 178/178 on unchanged data source. Current-source C2 passes 340/340; C3 debug/test APK assembly and lint complete; C5 minified release assembly completes and its temporary debug-signed copy starts on API36 and reaches App Lock setup. App lint reports 156 warnings and 11 informational findings, core:data lint is clean; R8 emitted Kotlin metadata compatibility warnings. C4 debug/test APK SHA-256 `a28f64c510265983576461daaa51c1110e053b72020a02dec6cbd163775e71f9` / `ae13ebb58d5e1727a479873aeffc57a11fc78560a4f0c02c0de205de3b3bab1e`. | [C4 gate-fix summary](evidence/2026-11-01/F06-v2/c4-app-gate-fix-summary.md), [C2/C3/C5 gate-fix summary](evidence/2026-11-01/F06-v2/c2-c3-c5-gate-fix-summary.md), [minified-release startup summary](evidence/2026-11-01/F06-v2/c5-gate-fix-release-smoke-summary.md), [full C4 Gradle log](evidence/2026-11-01/F06-v2/c4-app-gate-fix.log), [JUnit](evidence/2026-11-01/F06-v2/c4-app-gate-fix.xml), [C2/C3/C5 log](evidence/2026-11-01/F06-v2/c2-c3-c5-gate-fix.log), [gate regression GREEN](evidence/2026-11-01/F06-v2/c4-green-app-lock-gate-dialog.xml) | Triage/fix or justify the 22 remaining app C4 failures and resolve the console/JUnit count discrepancy. Complete locked-state release checks, remaining manual scenarios, API28/device matrix and security review before acceptance. |

## Exit gate

- [ ] T01–T08 source handoffs and all R01–R12 → case → assertion/test/manual mappings recorded.
- [ ] C1–C5 and affected regressions supported by valid evidence for the current candidate; no unexplained failures/errors/skips or lint findings.
- [ ] All applicable Q01–Q22 and required matrix/native/manual/security-review obligations PASS; blockers/N/A rationale visible.
- [ ] Fixture/media/archive/schema identities and independently checked data/permission/mutation oracles attached.
- [ ] Defects fixed within QA, affected evidence invalidated and only affected checks rerun.
- [ ] Board updated to exact observed scope; feature/release acceptance withheld while any required case remains unrun or blocked.
