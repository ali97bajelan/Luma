# Luma app lock — product requirements

Date: 31 October 2026 · Owner perspective: Product management · Priority: P1

Status: **PROPOSED PRODUCT SPEC — NOT IMPLEMENTED / NOT VALIDATED**

## 1. Purpose and decision status

Let people hand someone their phone or leave Luma open without exposing private history, while keeping it quick to record something new.

The user explicitly requested:

1. A lock option with a password the user sets in Luma.
2. Protection for selected parts, such as Memories, or the whole app.
3. A choice between entering the password each time and locking after a background interval, such as five minutes.
4. Adding a new entry without entering the password, including when the whole app is locked.

Everything marked as a recommendation below is a product proposal, not a recorded user decision. This document completes the requested product definition; it does not authorize implementation or certify security. Recovery remains a proposed decision pending user feedback.

Reviewed inputs: [original implementation plan](../plans/2026-10-26-roadmap-06-app-lock.md), [original specification](2026-10-26-roadmap-06-app-lock.md), and [original QA scenarios](../../feature-roadmap/06-app-lock-qa.md).

This is a separate revision proposal. Keep those documents intact as historical inputs. Before implementation, reconcile their conflicting requirements against an approved version of this document and update the implementation plan and QA mapping together. Do not combine incompatible rules from both versions.

## 2. Review of the original proposal

| Area | Original F06 proposal | Required revision |
| --- | --- | --- |
| Authentication | System biometric/device credential; app-specific PIN excluded | User-defined Luma password is the primary credential; works without a phone screen lock |
| Coverage | One gate around private app navigation | Whole app or multiple selected content sections, with protection across all surfaces |
| Frequency | Cold-start lock and background timeout | Define “every time” for both modes, plus precise timed behavior |
| Quick capture | Authentication before quick-capture destination | Allow restricted creation while locked; never grant read access as a side effect |
| Recovery | Phone credential setup or local reset | Define recovery for an independent Luma password |
| External previews | Existing detailed widget preference can remain | Hide selected protected data by default when enabling protection; explicitly explain launcher cache limitations |

Useful foundations to retain: no private first-frame flash, draft preservation, monotonic timeout accounting, cold-start locking, typed pending destinations, mutation revalidation, device-local credentials, localization and separate implementation/QA phases.

Source review found shared navigation and editor-result handling in `LifeOsApp.kt`, capture choices in `QuickCaptureSheet.kt`, and an existing `PrivacySettingsRoute.kt` with privacy/reset controls. Memory creation and editing share a view model that can load existing records, people and photos. Restricted creation therefore needs an explicit authorization mode; merely hiding a Back button or reusing an ordinary editor route is insufficient. This was a source review, not a runtime audit.

## 3. Product approach and scope

| Approach | Benefit | Tradeoff | Recommendation |
| --- | --- | --- | --- |
| Phone authentication only | Reuses device credentials | Does not provide the requested independent password | Retain only as a future optional convenience |
| One Luma password, configurable coverage, restricted capture | Meets all four requested behaviors; one credential to remember | Requires content-aware authorization and recovery | **Recommended for v1** |
| Separate passwords for each section | Independent credentials | More recovery burden and confusing cross-section flows | Defer |

V1 recommendation: lock off by default; one local password; Whole app or Selected sections; Every time or timed relock; manual Lock now; password-free restricted capture; offline recovery. All behavior works without an account or network.

Exclude from v1: individual-entry passwords, multiple passwords/profiles, decoy content, remote unlock/wipe, custom biometrics, and a claim of database/file encryption. App lock controls access through Luma; it is not a replacement for device security or encrypted backups. Biometric convenience may follow after password behavior is validated and must be explicitly enabled with the Luma password.

## 4. Setup, settings and credential management

Entry point: **Settings → Privacy → App lock**.

Setup sequence:

1. Explain: “Protect your saved information. You can still add new entries while locked.”
2. Create and confirm a password. Recommended policy: 8–128 Unicode characters, spaces allowed, no forced character classes, no silent trimming/truncation. Support paste, password managers and an accessible show/hide control. Password mismatch keeps setup incomplete.
3. Choose **Whole app** or **Selected sections**. Recommend Whole app as the initial selection; do not enable it before confirmation. Selected sections must contain at least one section.
4. Choose **Every time** (recommended default) or **After 1 minute / 5 minutes / 15 minutes away**. Five minutes directly supports the requested example. One timing policy applies to all selected sections in v1.
5. Complete the recovery setup described in section 10 and acknowledge its consequences.
6. Show the final coverage/timing summary and enable atomically. Failure or cancellation leaves lock disabled and removes incomplete credential material. On success, immediately enter the locked state so the chosen behavior is visible.

Settings after setup: status, coverage, protected sections, relock timing, Change password, recovery-code replacement, Lock now, screenshot protection, and Disable app lock. Explain that adding a new entry remains available.

Opening lock settings, changing any lock setting, disabling protection, changing the password, or replacing the recovery code requires fresh Luma-password verification, even during a grace period. A successful verification authorizes that one settings operation, not the whole browsing session. Cancel/failure changes nothing. Password change verifies the current password and confirms the replacement; it invalidates all unlock grants and locks protected content immediately. Disabling requires confirmation after verification, removes lock credentials/preferences and preserves personal records.

## 5. What can be protected

Protection follows **content and operations**, not just the tab that displays them. Navigation labels and a generic locked placeholder may remain visible. Protected titles, bodies, photos, names, dates, counts, search matches and derived summaries must not leak before unlock. Avoid one placeholder per record; it would disclose record counts.

Recommended selectable sections:

| Section | Includes | Cross-surface consequence while locked |
| --- | --- | --- |
| Memories | Direct memories, memory markers, attached media and memory metadata | Hide matching cards/previews in Today, Timeline, Calendar, Search, Review, person history and sharing |
| Journal | Journal entries, attachments and journal-derived summaries | Hide journal content wherever surfaced |
| Mood | Mood logs, notes, charts and trends | Hide mood values and derived statistics wherever surfaced |
| Tasks | Existing tasks, details, completion history and task reminders | Hide task content; completion/edit/delete require unlock |
| Habits | Definitions, logs, streaks, schedules and habit reminders | Hide habit lists and summaries; logging against an existing habit requires unlock |
| People | Directory, names/photos, relationship information and links to people | Hide people selectors and identity fields, including on otherwise visible entries |
| Focus | Session setup/history and related metrics | Hide saved sessions/configuration; active-session handling follows the rule below |
| Health & usage | Imported health/activity and device-usage views and derived metrics | Hide these records and related summaries; background collection follows existing permission settings |

Whole app covers every saved-data screen and operation, including settings, archives, backup/export, restore and sharing. Exceptions are the lock/recovery screens, static privacy/help text, and restricted creation. Ordinary settings navigation must not bypass the gate.

Selected sections leaves unrelated content usable. Mixed screens omit protected data and show one generic “Some information is locked” action, without protected item counts. Recalculate summaries from authorized data or hide a summary entirely with an explanation that it requires unlock; never present a partial metric as a complete total. Do not change Calendar's existing tasks/memories-only scope.

If an object belongs to several sections, all protected sections required to reveal it must be unlocked. A journal or mood entry marked as a memory inherits Memories protection for the entire marked entry and attachments, including its original detail route. A person-linked entry can show otherwise unprotected text but must redact protected person identity and relationships. Text manually written inside another entry is not automatically classified as People data; explain this limitation in help.

Recommended session rule: in Selected sections, unlock grants are per section. An explicit request for an object that needs several locked sections names those sections in one password prompt and grants only those sections on success. Adding a section to coverage immediately locks that section. Switching coverage modes clears all grants.

On a mixed screen, “Some information is locked” opens a choice of protected sections to unlock; it does not silently unlock everything. In Every time mode, a grant remains valid within the current top-level destination and its related list/detail/editor flow. Scrolling, pagination, changing a filter/date, or returning from a related detail does not revoke it. Switching top-level destinations (for example, Timeline → Today), leaving for settings, or entering restricted capture revokes all selected-section grants. Opening an unrelated detail revokes grants for sections that detail does not require. Returning afterward requires another password to reveal those sections. For example, unlocking Memories on Timeline survives opening and closing a memory detail, but does not survive opening an unrelated task detail. These rules apply to Search and other mixed lists too; merely having a memory somewhere in a list does not establish a permanent grant. Timed mode retains its foreground grants across these navigation changes until another documented revocation event.

Focus conflict: app lock governs data visibility; the existing Focus mode continues governing allowed navigation. Unlocking Luma must not exit or bypass Focus. Restricted capture is available when app lock alone blocks access; if an active Focus session separately prevents capture, preserve its existing exit rules and explain the restriction. Generic stop/emergency controls remain reachable without exposing session history or unrelated content. Ongoing timers/imports/reminders continue; app lock does not revoke permissions or stop background work.

## 6. Relock rules

User-facing wording: **“When should Luma ask again?”** with “Every time” or “After … away.” Avoid the ambiguous phrase “closing the app.” Away means no Luma activity is visible; switching between Luma activities is not leaving the app. Losing focus in split screen while Luma remains visible is not away. Explain that Lock now is available before handing over a device.

| Event | Every time | Timed mode |
| --- | --- | --- |
| Cold start, process death or device restart | Protected data starts locked | Protected data starts locked, even inside a former grace period |
| Enter whole app after going away | Password required | Reuse the grant only if away duration is below the interval |
| Enter a selected protected section | Password if that section has no current grant | Password if that section has no current grant |
| Leave selected section for unrelated content | Revoke that section's grant immediately | Keep that section's grant during the foreground session |
| Move between list/detail/editor within an unlocked section | No repeated prompt | No repeated prompt |
| App goes away | Revoke all grants immediately | Start one background deadline for existing grants |
| Return at interval minus 1 ms / exactly at interval | Already locked | Still unlocked / locked, respectively |
| Device locks, user selects Lock now, or credential changes | Revoke all grants immediately | Revoke all grants immediately |
| Rotation, theme/locale change or activity recreation in the same live process | Preserve valid grants; no prompt loop | Preserve grants and any existing deadline |

Timing uses monotonic elapsed time, never wall-clock time. Foreground inactivity by itself does not relock in v1. Timed mode deliberately means a section can remain unlocked while the user continues using Luma; explain this in settings. An ordinary return before expiry ends the away period; the next departure starts a new interval. Restricted capture is an exception: opening, saving, or retrying it never grants access, pauses a deadline, or refreshes a grace period. An already-expired grant stays expired while capture is open.

Transient authentication UI must not trigger a self-relocking loop. External pickers/camera trips preserve the editor draft, but still count as time away for browsing grants. Evaluate authorization again before revealing the returning screen.

Pending private destinations contain no visible preview. Successful unlock opens the intended destination once, only if still valid and authorized; cancellation returns to safe unprotected content or the whole-app lock screen. Back cannot reveal a previously protected frame. Never replay a destructive action automatically just because a destination unlocked.

## 7. Adding a new entry while locked

The lock screen and locked-section placeholder expose **Add new entry** as a first-class action, without a password prompt. The existing quick-capture widget must be able to reach the same restricted mode. An icon launch in Whole app mode shows the lock screen first, with this action visible.

| Allowed without unlock | Requires unlock |
| --- | --- |
| Start a blank Memory, Journal, Mood, Task or new Habit definition | Read, edit, delete, duplicate or share an existing record |
| Type new text, choose a date/time, set values for the new record | Browse history, search results, recent-entry suggestions or private templates |
| Add a new photo using the system picker/camera | Browse existing Luma photos or attachments |
| Edit the current unsaved input and remove its newly added photos | Select existing people/places/tasks/habits, view streaks or log against an existing habit |
| Save the newly created record once | Open the saved record or undo/edit it after saving |

V1 recommendation: no person/place linking or creation within restricted capture; offer “You can add people and places after unlocking.” This avoids an indirect directory leak and keeps this exception narrow. New Habit means creating a definition, not incrementing or overwriting an existing habit. Repeated-tap and retry behavior must use the same create-operation identity to avoid duplicate records. A collision with existing data produces a generic error without disclosing that record.

Use a genuinely new identity; never accept an existing-record identifier from a deep link as a create capability. The restricted authorization permits only creation and operations on that session's new input/media. Enforce this in action/data access boundaries as well as navigation. A malicious route, restored back stack or manipulated editor result cannot turn creation into editing.

After Save: show a generic “Entry saved,” clear completed editor state, and return to the lock screen in Whole app mode or a safe unprotected destination in Selected sections. Do not display the title, photo, date, count, detail preview, or an “Open/Undo” action. Creating a record grants no read access. Newly created content inherits the user's coverage immediately.

On Cancel: confirm discard if needed; retain or clean up new attachments according to existing draft/photo ownership rules; never delete another record's media. On save failure: keep the current input, offer retry, and avoid details about existing records. Restricted creation remains available during password-attempt cooldown.

Draft rule: current capture input is visible during its uninterrupted editing session, rotation and its own picker/camera return. Following a genuine app departure, device lock or process death, preserve the draft privately but do not automatically reveal it to the next visitor. Offer **Unlock to resume draft** or **Start a new entry** without revealing its contents. Starting a new entry preserves the older draft separately; it must not silently overwrite it. After unlock, offer resume/discard explicitly. This may require extending existing single-draft storage; do not promise it works today.

Existing-record editors are always gated once their grant expires. Preserve their drafts through cancellation, rotation and relock; do not turn an existing edit into a password-free new entry. Saving protected existing content requires authorization at commit time.

## 8. Other entry points and disclosure controls

Apply the same policy before the first private frame and before every protected read/mutation, including restored navigation, widgets, notifications, media viewers, sharing previews and external intents. When stored credentials/policy cannot be read reliably, keep private content gated; offer retry/recovery and safe capture only if it can be isolated reliably.

Protected widget data is generic while locked; unprotected sections may continue showing their normal widgets. Enabling protection immediately requests refresh of affected widgets and replacement/redaction of already posted protected notifications. New notifications are generic while locked. Recommend keeping external protected previews generic even during an in-app unlock grant, so unlocking Luma does not expose history on a home/lock screen. A future explicit preview opt-in is outside v1.

Explain that launcher/OS caches may retain an earlier image; Luma cannot guarantee erasure of third-party cached previews. Do not claim old screenshots, exported files or already shared copies become protected.

Task completion, habit logging and other actions on existing protected records require unlock, including notification/widget buttons. Revalidate record identity, revision, expiry and authorization after unlock. Execute a confirmed valid action at most once; expired/stale actions show safe feedback and leave data unchanged. Never treat them as the new-entry exception.

Recents must hide protected content whenever protection is enabled, including while unlocked, and hide unsaved restricted input. Screenshot protection is a separate switch, recommended on by default, covering lock/password/recovery screens, protected content and restricted capture. Explain that disabling it permits captures of unlocked content; Recents masking remains required. Support EN/FR/FA, RTL, light/dark, large fonts, keyboard, password-manager use and screen-reader operation; accessibility output must not expose hidden content or announce password characters.

Exports, backup creation, sharing, restore/import and ordinary data reset can disclose or alter protected data. In either coverage mode, require fresh Luma-password verification for these operations whenever app lock is enabled; backup/export then follows its own encryption policy. The destructive forgotten-password reset in section 10 is the sole reset exception and grants no read, export or restore access. A password success is not consent to an unencrypted export. External shares already completed cannot be revoked by relocking.

## 9. Credential and session safeguards

Never persist plaintext/reversibly recoverable passwords, include credentials in logs/analytics/crash reports, or persist an “authenticated” flag. Store credential verification material and lock policy device-locally, outside user archives and automatic cloud/device transfer. Use an established password-verification design; engineering must select and review its derivation parameters, salt, local key protection and retry-state storage before implementation. This product document does not select cryptographic primitives.

Recommended online-attempt policy: after 5 consecutive incorrect password or recovery-code submissions, impose 30 seconds before another attempt; each subsequent failed attempt doubles the delay up to 15 minutes. Successful verification resets failures. Persist attempts and cooldown so process restart does not clear them; engineering must define reboot/clock-change accounting without an easy retry bypass. No automatic deletion or permanent lockout. Error text is generic; display the remaining delay accessibly. This limits attempts through Luma, not offline attacks on extracted data.

Clear typed secrets from UI state after submit/cancel/background; never restore them after process death. Password and recovery changes are atomic. Corrupted/missing credential material with protection still enabled must not silently disable protection. Credentials, failure state and grants have explicit reset/restore handling; authenticated capabilities never travel in intents or serialized drafts.

## 10. Forgotten password, restore and reset

**Recommended recovery, pending user choice:** generate a high-entropy recovery code during setup, display it once, allow deliberate copy/save, and ask the user to confirm they saved it outside Luma. Do not allow screenshots of that screen even if ordinary screenshot protection is off. Store only verification material. Explain that anyone with the code can replace the password; clear any copied recovery code on a best-effort basis without promising clipboard erasure on every device.

Forgot password offers **Use recovery code** and explains that support cannot retrieve a local password. Correct code authorizes setting/confirming a new password and issuing a replacement recovery code, not direct history access. Complete these changes atomically, consume the old code only on successful completion, invalidate all grants, and return to the locked screen. Interrupted recovery leaves the previous credentials/code usable. Normal code replacement requires fresh password verification and confirmation that the replacement was saved.

No password and no recovery code: no read bypass. Offer a separately confirmed **Erase local Luma data and start over** path using the existing reset flow, with a plain warning that records, media, drafts and lock setup will be deleted. This destructive recovery path is available without password verification, unlike ordinary reset in settings; require an initial erase choice and a second explicit destructive confirmation. It can only erase local data, never disable the password while retaining data or reveal records before deletion. Do not erase merely for failed attempts. Existing backups are separate and are not deleted by local reset. Phone authentication alone is not a recovery bypass in the recommended design.

Restore never imports the source device's password, recovery code, failure state, timing policy or unlock grants. Restoring into an already protected installation first requires fresh authentication and preserves its local protection settings; all restored data starts locked under those settings. Restoring into a fresh/unprotected installation requires an explicit choice to set up protection or continue without it **before restored content appears**. Warn that backups are not protected merely because app lock was enabled; their own encryption/password controls apply.

If the user chooses device-auth recovery instead, revise setup prerequisites, device-owner threat model and credential-removal cases before implementation. If the user chooses reset-only recovery, remove the recovery-code setup/rotation flows and make the no-recovery warning explicit. Do not ship competing recovery policies by accident.

## 11. Acceptance scenarios

All scenarios below are requirements for future QA, **NOT RUN**. Give the revised scenarios distinct IDs; the older F06-QA cases do not cover the new behavior completely.

| ID | Scenario | Expected result |
| --- | --- | --- |
| AL-01 | Cancel setup, mismatched password, or persistence failure | Protection never partially enables; personal data unchanged |
| AL-02 | Enable without phone credential or network | Independent Luma password works; selected recovery setup completes |
| AL-03 | Select Memories only; inspect Timeline, Calendar, Search, Review, person history and marked journal detail | Protected information and its derived metadata are hidden everywhere; unrelated content remains usable |
| AL-04 | Select several sections; request a cross-section object | One prompt identifies required sections; only those grants are issued |
| AL-05 | Whole app mode: icon, notification, widget, restored route and photo viewer | Gate precedes private rendering; Cancel/Back reveals no protected history |
| AL-06 | Every time: scroll/filter a mixed list; open related/unrelated details; switch top-level destinations; background/return | Grants survive scrolling/filtering and related flows, revoke at the defined navigation/background boundaries, and require fresh password on reentry |
| AL-07 | Timed mode at T−1 ms, T and T+1 ms for every interval | Exact boundary; wall-clock change cannot extend a grant; cold start always locks |
| AL-08 | Enter restricted capture repeatedly during/after a grace period | No new grant or extended deadline; expired history remains locked |
| AL-09 | Create every allowed entry type while Whole app or its section is locked | No password; no saved-data suggestions; one new record; generic confirmation; return to safe destination |
| AL-10 | Manipulate capture IDs, Back, deep links, duplicate taps, Save retry and editor results | Cannot read/edit/delete old records or escape into private navigation; no duplicate creation |
| AL-11 | Capture draft: rotate, use camera, background, lock device, kill process | Defined draft visibility boundary; preserved private input; new blank capture does not overwrite it |
| AL-12 | Existing editor expires mid-edit; cancel/retry unlock | Draft/media preserved; protected write blocked until authorized; no duplicate save |
| AL-13 | Wrong password/code, cooldown, restart, reboot and clock changes | Generic errors; cooldown not trivially bypassed; no auto-wipe; new-entry capture still available |
| AL-14 | Cancel/succeed changing password, coverage, timing or disabling | Fresh verification; atomic changes; correct grant invalidation; personal records retained |
| AL-15 | Recovery succeeds, is interrupted, or uses consumed/invalid code | Only valid code authorizes replacement; atomic credential/code rotation; no history flash |
| AL-16 | Existing/new notifications, widgets, Recents, screenshots and accessibility tree | No protected disclosure; screenshot switch matches copy; cache limitation documented |
| AL-17 | Locked widget Complete or notification habit action; cancel then retry after record changes | No unauthorized mutation; stale actions rejected; valid confirmed action executes once |
| AL-18 | Export/share/backup/restore/reset in both coverage modes | Fresh authentication where specified; backup-specific controls retained; no credential transfer |
| AL-19 | Forgot credentials then confirm/cancel destructive reset | Clear warning; cancellation preserves everything; confirmation removes local data and lock setup |
| AL-20 | Active Focus, health import, timer or reminder crosses a relock boundary | Visibility protected; background work continues; unlock/capture does not bypass Focus restrictions |
| AL-21 | EN/FR/FA, RTL, large text, light/dark, keyboard and assistive technology | Setup, unlock, capture and recovery remain usable without private/secret output leaks |
| AL-22 | Corrupt preferences, unavailable credential store or interrupted initialization | Private content remains gated; no fallback to unlocked defaults |

## 12. Delivery, outcomes and remaining decisions

Recommended delivery sequence:

1. Approve the product decisions, especially recovery, section taxonomy, defaults, and the definition of Every time. Update original F06 plan/spec/QA traceability against this revision.
2. Define a shared authorization contract for content reads, grants, settings operations, restricted capture and external actions. Inventory every projection and entry point before relying on a navigation overlay.
3. Implement password setup/recovery and lifecycle rules, then section filtering and the isolated create flow; integrate drafts, previews, backup/reset and localization. Retain the repository's two-phase delivery contract: implementation source handoff first, QA/test authoring and runtime validation second.
4. Run revised acceptance coverage on supported Android versions, including API 28, with synthetic records only. Verify process death, actual first frames, native pickers, launcher widgets and snapshots on devices; source review cannot certify these behaviors.
5. Release only after all applicable acceptance cases pass and an engineering security review covers credential storage, retry persistence, recovery and authorization boundaries. Track blocked/unrun cases explicitly.

Success criteria: all four requested capabilities work together; zero protected information disclosed in the acceptance fixtures; zero unauthorized existing-record mutations; zero lost drafts/duplicate records in the lifecycle matrix; creation works offline without authentication. During QA compare the number of steps and time to save a blank entry with ordinary capture; restricted capture should add no authentication step. Do not add private usage analytics solely to measure this feature.

Remaining **product proposals for review**: recovery code versus device authentication/reset-only; eight selectable content sections; one password with per-section grants; Every time as default; 1/5/15-minute intervals; screenshot protection on by default; and no person/place linking during restricted creation. These are explicit recommendations, not blockers to producing this document.

Engineering owns exact module/interface names, password-verification parameters after security review, safe multi-draft storage, native screenshot/picker compatibility, cooldown accounting across reboot, and detailed performance budgets. Those decisions must preserve the behaviors above and be recorded before implementation. This documentation task runs no builds, tests, migrations or device operations and makes no application changes.
