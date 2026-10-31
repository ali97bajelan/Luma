# App lock v2 — QA test data

Date: 31 October 2026 · Status: **SYNTHETIC DATA DEFINED; SEED ADAPTER NOT AUTHORED; QA NOT RUN**

[Spec](../superpowers/specs/2026-10-31-app-lock.md) · [Plan](../superpowers/plans/2026-10-31-app-lock.md) · [Board](../APP_LOCK_TASKS.md) · [Scenarios](app-lock-qa.md) · [Fixture JSON](fixtures/app-lock-v1.json)

## Format and safe use

`fixtures/app-lock-v1.json` is version 1 of a deterministic **logical QA fixture**, not Luma backup JSON, a Room schema, a runnable seed adapter, or runtime evidence. Do not feed it to the app's restore/import flow. QA authors adapters in test source sets after the implementation wave. Adapters create records through trusted test-only repository access and create lock state through service APIs or controlled fault injection; no production fixture/debug bypass is allowed.

All passwords, recovery codes, names, notes, IDs and health/usage values are public synthetic vectors. Never use these credentials for real protection. Recovery strings are injected verifier test vectors, not claims about the production code format or entropy. Native recovery tests generate actual random codes and keep them out of logs/screenshots. Replace/redact any credential-bearing artifact before storing QA evidence.

IDs use UUIDv5 with URL namespace and name `https://luma.invalid/qa/app-lock/v1/<alias>`. The JSON records their final literal values. Freeze wall time at `2026-10-31T06:30:00Z`, zone Asia/Tehran and elapsed time 1,000,000 ms, boot `qa-boot-01`. Repeat with UTC, a changed wall clock, and a different boot. Native tests cannot fake real process/device behavior using host clocks; exercise those separately.

## Baseline record inventory

Each scenario starts from this fresh isolated baseline unless its own delta says otherwise. There are **14 root records**, plus nested markers/log dates and two saved-photo assets. New drafts/photos and the five proposed new records are separate from the baseline count. Root count is an internal oracle, never text to display on a locked screen.

| Alias | Content | Independent expected protection |
| --- | --- | --- |
| memory_private | Standalone memory, importance 4, saved red photo, linked person | Memories; People redacts the link independently |
| journal_public | Plain journal and fixture tag | Journal |
| journal_marked | Journal with memory marker, importance 5, blue photo | Journal + Memories, including original detail/media |
| mood_public | Score 3, energy 2, stress 4 | Mood |
| mood_marked | Score 1, energy 1, stress 5, memory marker | Mood + Memories |
| journal_person_linked | Plain journal with two people and one place | Journal; People redacts names/links only |
| health_activity | Synthetic imported walk, 900,000 ms | Health & usage; retain imported read-only behavior |
| usage_event | Synthetic imported usage, 600,000 ms | Health & usage; no actual installed package data |
| task_open | Due 31 October at 14:00, incomplete, fixed revision | Tasks |
| habit_water | Target 2 glasses daily, one log on 30 October | Habits |
| person_en | Synthetic English name/note | People |
| person_fa | Synthetic Persian name/note | People |
| place_fixture | Synthetic saved place | Unprotected under selected-section policies; protected by Whole app; still inaccessible in restricted capture |
| focus_saved | Completed session, credited 1,500,000 ms | Focus |

JSON kind names and field shapes are logical input, not guaranteed current enum/constructor names. The QA adapter maps them to current supported domain input (for example, the existing completed Focus representation) and records that mapping. Seed timestamps, content and relationships must match the literal JSON. Never derive expected protection from the production classifier.

Profiles in JSON contain independent visible/hidden **record aliases**. For `people_locked`, `memory_private` and `journal_person_linked` remain otherwise visible with identities/relationship fields removed. For `memories_locked`, exactly memory_private/journal_marked/mood_marked are hidden. For `whole_app_locked`, none of the 14 records is visible. `all_sections_locked` still leaves place_fixture unprotected; v1 has no separate Places option. Profile arrays are semantic record visibility, not a requirement to display all kinds on one screen. Calendar still shows only eligible tasks/memories.

Also run one parameterized policy for **each of the eight sections alone**, with all grants absent, then a valid matching grant, then revoked. Expected hidden sets: Memories = memory_private/journal_marked/mood_marked; Journal = all three journal aliases; Mood = both mood aliases; Tasks = task_open; Habits = habit_water; People = both people with linked identities redacted; Focus = focus_saved; Health & usage = health_activity/usage_event. For a record requiring multiple protected sections, one matching grant is insufficient.

## Credential and timer vectors

- Password lengths: 7 rejected, 8 accepted, 128 accepted, 129 rejected. Count Unicode code points; eight emoji are eight characters despite 16 UTF-16 units. Preserve leading/trailing spaces and distinguish NFC/NFD representations. Confirm mismatch never enables protection.
- Use the synthetic current, incorrect and replacement passwords only through test interfaces. Verify no real phone credential and no network are required. A newly generated recovery code is acknowledged before final setup.
- At departure 1,000,000 ms, each 60,000/300,000/900,000 ms interval has explicit before/at/after samples in JSON. At the exact deadline, access is locked. A ±86,400,000 ms wall-clock jump changes none of these outcomes.
- Five-minute capture: open at 1,120,000 ms, save at 1,240,000 ms; deadline remains 1,300,000 ms. Capture does not reset it. Cold-process start, new boot, device lock and Lock now revoke regardless of these times.
- Shared wrong-password/code counter: failures 1–4 have no imposed delay, failure 5 = 30,000 ms, then 60,000/120,000/240,000/480,000/900,000/900,000 ms. Advance past each cooldown before submitting the next eligible failure. In-cooldown submissions invoke no verification and increment nothing. Correct verification resets the count.

## Capture, drafts, media and commands

The five `newEntries` supply valid new Memory, Journal, Mood, Task and Habit-definition inputs, fixed distinct IDs and stable operation identities. Test each independently from baseline: first save creates exactly one record, repeated save or lost-ack retry creates no additional record, all prior records remain byte-for-byte equivalent at domain/archive level, no saved-record-read callback runs, and the action grants no read authority. New record bodies/titles must be absent from the post-save confirmation and accessibility tree.

`draft_old` is a hidden older journal draft; `draft_new` is current unsaved memory input with green photo. Starting blank preserves the older draft. Normal rotation and own camera/picker return retain current input; genuine departure/process death hides it. Unlock permits explicit resume/discard. Assert draft bodies, IDs, ownership and media hashes independently; do not use “screen exists” as a preservation oracle.

QA generates three opaque 16×16 PNG files per asset recipes in JSON (saved red, marked blue, new green), records SHA-256/file length and seeds proper ownership. Use system picker/camera only on the disposable device with synthetic images. Compare hashes/ownership before/after save failure, cancellation, attachment removal, restore and reset. Denied picker/camera permission leaves the draft intact. Photo generation/real-picker execution is future QA work, not delivered image evidence.

`complete_task_valid` uses the baseline revision and expires at 1,300,000 ms. Auth cancel has mutation delta 0. Successful auth plus explicit action confirmation before expiry changes incomplete → complete once and uses its next revision. Repeating the command yields no second mutation. `complete_task_stale` has the wrong prior revision and must leave the task unchanged; delete/change the task after queuing for additional stale cases. At `now >= expiresElapsedMs`, reject without mutation. `habit_log_existing` requires Habits unlock and confirmation; it is never a blank-create exception. Baseline log date 30 October remains unchanged when auth is cancelled.

## Fault and archive variants

Inject each named JSON fault at the narrow owning boundary, retaining an independent before/after snapshot. Required variants: verifier/policy write failure, interrupted recovery replacement, corrupt/unavailable lock store, late old-generation projection, unavailable draft storage, denied photo import, create committed with acknowledgment lost, restore interrupted before commit and rejected reset request. Clear the fault before retry; do not rewrite expected data to match observed behavior.

QA generates archive samples using existing supported exporters/adapters: current baseline, every supported legacy archive version, populated-upgrade database, malformed archive and interrupted replacement. Do not invent obsolete version numbers in this fixture. Inspect actual members: password/recovery verifiers, retry/policy/grant/proof/private-draft/create-receipt data must be absent. Separately verify backups obey their own encryption settings. Record generated file hashes and importer/exporter versions.

Additional variants: active Focus session with known checkpoint; an in-flight synthetic health import; pending reminder; missing health provider; notification permission denied; absent device screen lock; zero records; loaded cached list followed by revocation; two visible Luma windows; same-session media picker; ordinary home/app departure; all selected sections plus an unprotected Places route. Never grant health/usage access to the user's personal installation for this work.

## Reproducibility and cleanup

Before each independent case: confirm dedicated app/test package and device, seed baseline, hash fixture and media, record policy/grants/clock, and snapshot existing records. After the case: compare explicit expected IDs/values/hashes and mutation counts, record actual result, then reset only the confirmed disposable fixture. Capture baseline/candidate identity for regressions.

Calculate fixture hash at execution with `sha256sum docs/feature-roadmap/fixtures/app-lock-v1.json`; put the actual hash in each run's evidence ledger. A schema/fixture change increments fixtureVersion and invalidates affected evidence. This document defines data and expected outcomes; adapters/assets/archives are QA-owned deliverables and their absence is never a PASS.
