# F15 — Memory and mood sharing QA scenarios

Date: 28 October 2026 · Status: **NOT RUN** · Priority: **P2 (proposed)**

[Roadmap](../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../superpowers/specs/2026-10-28-roadmap-15-memory-mood-sharing.md) · [Plan](../superpowers/plans/2026-10-28-roadmap-15-memory-mood-sharing.md) · [Shared workflow](WORKFLOW.md)

## Entry gate and evidence policy

This is **phase 2** of F15. Begin after the recorded F15-I01–I05 source handoff. Fixture/test authoring, Gradle/build/lint, automated execution, emulator/device operations and defect corrections occur only in this phase. This documentation session performed none of those actions.

Read the full spec and WORKFLOW.md first. Use an isolated package/AVD with disposable synthetic data. Record source SHA/patch hash, build/APK hash, toolchain, device/API, receiver app/version, locale/zone, clock, font/window/orientation/theme and actual command exit codes. Evidence belongs under `docs/feature-roadmap/evidence/<actual-run-date>/F15/` when QA runs. Never send a message to a real recipient as a QA step; inspect the destination compose screen and cancel.

## Independent fixture

Create and document expected IDs/values and literal expected payloads independently of production formatters:

| ID | Synthetic saved content |
| --- | --- |
| M1 | Standalone memory: title `A lovely afternoon 🌿`, multiline note containing `Alex` in free text; importance 5; linked person `Alex Sample`; place `Park Sample` with an address/coordinates; two disposable photos |
| M2 | Standalone memory with null title and optional links absent; separate variant with null memory note and legacy event-note fallback |
| U1 | Mood score 4, emotions `Calm`, `Grateful` in known order, energy 4, stress 2 and multiline note; linked person/place |
| U2 | Mood score 1, no emotions/note/energy/stress/links; enumerate scores 1–5 with expected EN/FR/FA labels |
| J1 | Journal marked as memory: distinctive original title/body/tags and marker-only note `Keep this moment`; variants with empty marker note and identical marker/source notes |
| UM1 | Mood marked as memory with different original mood note and marker note; same-note variant |
| X1 | Unmarked journal; activity; app usage; missing ID; malformed ID/mode; mismatched kind; saved graph with required typed details absent |
| B1 | Persian/French/emoji, combining characters, embedded newlines, punctuation such as `<b>`, `#`, brackets and backticks; multibyte payloads of exactly 65,536 and 65,537 UTF-8 bytes; blank-only and one visible character variants |
| L1 | Long marked-journal body near the existing 50,000-character limit that becomes oversize when source content is enabled |

Use fixed instants near local date boundaries, including a DST transition, and zones `Asia/Tehran` and `Europe/Paris`; derive expected occurrence dates independently. Keep record/update/link/photo hashes and a repository write-call spy for mutation checks. Prepare controllable read/launch failures, concurrent linked-name edits, marker removal/deletion, locale/zone changes and process recreation. App-lock-on fixtures apply only when F06 exists; otherwise record that integration branch N/A with source-supported rationale and keep unlocked lifecycle checks required.

## Coverage mapping

| Requirement | Scenarios | Initial result |
| --- | --- | --- |
| F15-R01 — Eligibility/reachability | QA01, QA04, QA08 | NOT RUN |
| F15-R02 — Privacy/review | QA01, QA02, QA04, QA07 | NOT RUN |
| F15-R03 — Localized format/bounds | QA02, QA03, QA06, QA08 | NOT RUN |
| F15-R04 — Handoff/freshness/lifecycle | QA04, QA05, QA06, QA07 | NOT RUN |
| F15-R05 — Accessibility/acceptance | QA05, QA06, QA08 | NOT RUN |

## Scenarios

### F15-QA01 — Saved eligibility and all entry paths

**Precondition:** Reset fixture, open unlocked isolated app; map Today/Timeline/Calendar/Review routes that reach saved mood/memory content.

**Actions:** Open M1/U1 event details; J1 journal details; saved memory/mood editors from current Calendar/Review paths. Open UM1 through mood detail and memory editor. Change an editor note, date, linked person/place or photo; attempt Share while dirty, saving/deleting or importing. Open a new unsaved capture and each X1 case. Cancel preview and return.

**Expected:** Loaded eligible saved entries expose Share. UM1 uses MOOD mode from mood detail and MEMORY mode from memory editor. J1 shares as memory; X1/new/loading/error content cannot launch. Dirty/busy/import states disable Share with accessible save-first guidance and never share an older saved version. Clean saved editor works without a write. Return preserves draft, scroll/navigation context and normal save/delete behavior. Calendar remains tasks/memories only.

**Evidence:** Route/action log, screenshots/semantics, selected request ID/mode and before/after record/draft hashes. Result: **NOT RUN**.

### F15-QA02 — Privacy defaults, source opt-in and manual text editing

**Precondition:** M1/U1/J1/UM1 loaded separately; expected default and opt-in output written independently.

**Actions:** Open each preview fresh; inspect text before touching options. Toggle date/note, people/place, mood energy/stress and marked-memory source content one at a time. Edit outgoing text, attempt an option change, cancel regeneration then confirm it. Reopen a new session after all opt-ins. Exercise equal marker/source note variant.

**Expected:** Date/note default on; linked names/scales/source default off every new session. J1/UM1 in MEMORY mode omit original title/body/mood content until source opt-in. Source section is labeled; identical notes appear once. Structured tags/importance/addresses/coordinates/IDs/photo names/URIs never appear. Free-text `Alex` remains with accurate explanation; photos are explicitly not attached. Missing-value options are hidden. Cancel regeneration preserves options/text; confirm replaces edits with the newly formatted choices. Manual edits never change the saved entry or trigger hidden additions.

**Evidence:** Full synthetic preview/payload comparisons for each option combination, regeneration observations and unchanged saved hashes. Result: **NOT RUN**.

### F15-QA03 — Unicode, dates, missing fields and exact payload bounds

**Precondition:** M2/U2/B1/L1 loaded; clock/zone/locale/12–24-hour settings recorded. Expected labels/dates and independent UTF-8 lengths available.

**Actions:** Generate/edit text in EN/FR/FA, all five scores, absent fields and DST/boundary instants. Include punctuation/emoji/newlines. Enter blank text, exactly 65,536 UTF-8 bytes and 65,537 bytes, including multibyte boundaries. Enable original content for L1, then shorten it manually. Change locale/date policy or display zone while preview is open.

**Expected:** Generated previews end with `#Luma` as an editable paragraph; the exact edited preview is handed off without adding hidden content. Correct existing score labels and occurrence date in the frozen display zone, with localized time for mood shares and date only for memory shares; no zone identifier is added; user Unicode/newlines preserved and punctuation remains literal plain text. Missing fields do not produce `null` or empty labels. Exactly 65,536 nonblank bytes accepted; 65,537 or blank rejected before any chooser opens. Oversize text remains editable and untruncated; shortening enables explicit Share. Settings changes require explicit refresh instead of silently replacing text. Refresh uses current context/default choices.

**Evidence:** Independent byte counts, expected/actual strings, date calculations, validation/error and refresh observations. Result: **NOT RUN**.

### F15-QA04 — Snapshot freshness and read failures

**Precondition:** Preview U1/J1/M1; keep local outgoing edits; control repository timing/errors without personal data.

**Actions:** Edit a note or linked person's/place's name from another path; remove J1's memory marker; delete the selected event. Attempt final Share after each change. Fail initial load and final recheck, retry, then explicitly refresh an edited stale preview. Include a linked-name change that leaves event.updatedAt unchanged.

**Expected:** Final saved-graph/eligibility check catches every change, including unchanged event timestamp. No stale chooser launch. Deleted/ineligible record becomes unavailable; retryable reads preserve a safe retry path. Refresh only on explicit action discards old share edits/options and loads current content/defaults. A failed/stale read cannot accidentally reuse another event's content. No record or draft writes occur. Document the final-read boundary rather than claiming to retract receiver copies.

**Evidence:** Controlled change/read sequence, full graph diffs, blocked launches, refresh payload and mutation spy/hash results. Result: **NOT RUN**.

### F15-QA05 — Native chooser, cancellation and launch failures

**Precondition:** Isolated API28 and API36 targets; preview with known synthetic outgoing text; QA receiver or intent instrumentation and injectable launcher failures.

**Actions:** Confirm Share; inspect actual outer chooser and nested ACTION_SEND intent. Rapidly tap Share, cancel chooser, return and tap again. Exercise ActivityNotFoundException/security/transaction failures and a target environment without compatible receivers.

**Expected:** Exact `text/plain` ACTION_SEND/EXTRA_TEXT; no package, recipient, subject, stream, URI grants or extra entry data. One chooser per explicit confirmation; cancelling or returning never relaunches automatically. Retry requires another tap and current validation/freshness checks. Unavailable/failed launch has actionable recovery and retains preview. System-owned no-destination UI or caught launch failure leaves Luma usable without crash or false delivery claim; record the OS-specific presentation. No files, clipboard copies or new permissions created.

**Evidence:** Intent capture and payload equality, launch counts, native chooser screenshots, failure/cancel observations and permission/file inspection. Result: **NOT RUN**.

### F15-QA06 — Telegram and Google Messages receiving compatibility

**Precondition:** Compatible Telegram and Google Messages versions installed on isolated QA device with a disposable test setup. Record exact versions/setup and ensure no real message recipient is used. If absent/unusable, record the specific prerequisite and keep that app's subcase BLOCKED.

**Actions:** Separately choose Telegram and Google Messages from the real share sheet for M1/U1, edited multiline FA/emoji text and a near-bound payload. Choose only a disposable recipient if an app requires one to reach its compose field. Inspect composed content/attachment status and cancel without sending. Test offline Luma preparation and receiver availability independently.

**Expected:** Luma offers a standard compatible text share with no app-specific integration; the destination receives the intended text. Record any receiver normalization/truncation/size constraints, including whether near-bound text is accepted; do not certify universal compatibility from an intent test. No photo/file attachment or hidden footer. Network/account requirements of the receiver are distinguished from offline preparation in Luma. Chooser opening/destination selection never shows a Luma message-sent claim. No message is actually sent.

**Evidence:** App versions, per-app/per-payload observations, synthetic compose screenshots and independent outgoing-intent text. Both app subcases required; fake receiver alone cannot pass this scenario. Result: **NOT RUN**.

### F15-QA07 — Recreation, process death, privacy gate and no mutation

**Precondition:** Edited preview with opt-in metadata; isolate app lifecycle and, if available, activate F06 with synthetic credentials.

**Actions:** Rotate during preview and while chooser is open; background/return; simulate process death then restore request. With F06 present, lock before loading, while preview is open and before launch; cancel authentication then unlock. Inspect persisted state/logs and compare event/update/catalog/photo/draft hashes after share/cancel/failure.

**Expected:** Configuration recreation retains an in-memory preview without duplicate chooser launch. Process death never restores private text/snapshot/launch token; reload creates a new default preview and requires explicit confirmation. Existing lock prevents private loading/rendering/launch; activation clears session, cancelled auth reveals nothing, successful auth requires new review. Chooser pause does not authorize replay; no promise to retract copies already shared. No private text in saved state/disk/logs/clipboard and no saved-data/draft mutation.

**Evidence:** Lifecycle/launch sequence, state-storage inspection with synthetic data, lock screenshots and exact before/after hashes. F06 branch N/A only if absent with rationale; lifecycle/mutation checks remain required. Result: **NOT RUN**.

### F15-QA08 — Accessibility, layout and affected regressions

**Precondition:** All R05 UI states reachable with deterministic fixtures; apply full shared EN/FR/FA × light/dark × window/font/orientation matrix and API28/API36 core coverage.

**Actions:** Navigate Share entry actions, options, long editable preview, keyboard/IME, regenerate/refresh confirmations and all errors with TalkBack and hardware keyboard. Include 320dp/font2.0, landscape/font2.0, large screen and a theme transition. Revisit save/delete, memory promotion/unmarking, photos, back navigation and F12 entry points if present.

**Expected:** Readable real RTL, localized copy and logical focus traversal; toggles have labels/states and errors are announced; controls have 48dp targets; no unreachable/clipped final action under IME/system bars. Disabled editor guidance is discoverable. Returning/cancelling preserves existing flows and drafts; F12 export scope and backup behavior remain unchanged. Full source/host checks do not substitute for TalkBack/native observation.

**Evidence:** Matrix-cell ledger/screenshots, human TalkBack/keyboard notes and affected regression results with exact candidate identity. Result: **NOT RUN**.

## Automated/manual ownership

The plan's Q01 names formatter/state/flow/intent suites and assertion obligations; they are not claimed to exist yet. Pure/state checks cover eligibility, formatting, privacy, regeneration, byte bounds, freshness and one-shot launch policy. Instrumented flows cover actual saved-graph reads, intent construction, draft preservation and native lifecycle. Actual receiver compose screens, TalkBack and keyboard observations remain manual/platform obligations.

No benchmark subsystem or fixed performance target is introduced by F15. Confirm that maximum-size preview/edit/validation stays responsive on the minimum supported test target; if performance is suspect, apply the shared measurement protocol and retain raw samples rather than claiming a pass from visual impression alone.

## Execution ledger

Add real evidence/matrix/subcase rows during execution; particularly separate Telegram and Google Messages observations. No outcomes are inferred from documentation creation.

| Case | Result | Actual observation | Evidence | Defect/retest |
| --- | --- | --- | --- | --- |
| F15-QA01 | NOT RUN | — | — | — |
| F15-QA02 | NOT RUN | — | — | — |
| F15-QA03 | NOT RUN | — | — | — |
| F15-QA04 | NOT RUN | — | — | — |
| F15-QA05 | NOT RUN | — | — | — |
| F15-QA06 | NOT RUN | — | — | — |
| F15-QA07 | NOT RUN | — | — | — |
| F15-QA08 | NOT RUN | — | — | — |

## Exit gate

- [ ] F15-R01–R05 mapped to passing assertion-level automated/native/manual evidence for the exact candidate.
- [ ] Required build/lint/affected unit/instrumented gates pass; skips/warnings classified and no unacknowledged source change invalidates results.
- [ ] Every required UI/API/accessibility cell and both Telegram/Messages receiver subcases executed; blocked/unrun scope stays explicit.
- [ ] No stale/private-content leak, unintended mutation, silent truncation, automatic relaunch or false delivery claim remains.
- [ ] Defects corrected and affected coverage rerun; acceptance states exact validated scope and any unresolved blocker without self-waiving it.
