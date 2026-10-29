# FM01 pure-domain QA record

Date: 30 October 2026 · Scope: **FM01 host-domain subset only** · Result: **PASS for the pure-domain subset; integrated scenarios remain blocked**

## Candidate and environment

- Base commit: `94490ad` (`feat(focus): add pure session timing and statistics contracts`).
- FM01 source/test patch SHA-256 (binary diff for `FocusTiming.kt` plus the two focus test files): `6b1b37646fe2ec40e66bc7a6700ae94558e9fd7e057550f51544efdad332e3ab`.
- Host: Linux x86_64, `en_US.UTF-8`, Asia/Tehran local timezone.
- JDK: OpenJDK 17.0.20.1 (2026-08-18 build).
- Gradle: repository wrapper, Gradle 9.1.0.
- Target: `core:domain` JVM unit tests. No APK, Android package, install, connected runner, schema generation, emulator, or physical-device action was involved. The API 36.1 emulator was not needed for this pure Kotlin target.
- Fixtures: deterministic in-test samples, including 28 October 2026 focus runs, Asia/Tehran midnight, America/New_York DST days, and 26 October 2026 ISO-week boundaries. No personal data or app installation was used.

## Automated result

Command:

```sh
bash gradlew --rerun-tasks -PdomainOnly=true :core:domain:test
```

Started at `2026-10-30T00:02:30+03:30`; completed at `2026-10-30T00:03:10+03:30`. Exit code: **0**. Gradle executed 4 tasks. The 24 domain test suites ran **157 tests: 0 failures, 0 errors, 0 skipped**. The new focus suites contain 19 tests (9 timing/transition and 10 statistics).

Raw output: [domain-tests-rerun.log](domain-tests-rerun.log). A separate first final invocation reported `UP-TO-DATE`, so it is not used as pass evidence; the recorded command above forced a fresh execution.

`git diff --check` exited 0. The new untracked source/evidence files also passed trailing-whitespace and final-newline checks, and the QA evidence links resolve.

Coverage includes:

- Start/Pause/Resume/Stop transitions; exact 20 active + 10 paused + 5 active = 25 minutes; stale/invalid transitions and terminal stop idempotency.
- Same-process vs new-process recovery, explicit recovery choices, paused recovery, changed boot identity, monotonic-clock regression, checked duration/timestamp overflow, and wall drift at 1,999/2,000/2,001 ms.
- Session A pause exclusion, Session B 10/20 minute local-midnight split, 23/25-hour DST days, half-open endpoints, ISO-week overlap, zero and interrupted contributions, duplicate snapshot normalization, and 90/60 comparison and one-decimal rounding/empty-baseline behavior.

## Defects and retests

1. The first wrapper invocation could not create `~/.gradle/...gradle-9.1.0-bin.zip.lck` because the cache is outside the workspace's writable roots. The requested QA command was rerun with access to the existing Gradle cache; no project configuration was changed.
2. Compilation then exposed a Kotlin syntax error in `resolveFocusRecovery`: a trailing comma after the `when` subject initializer. The comma was removed. This was the only production-code defect found in the FM01 host pass.
3. The first test execution exposed three fixture mistakes: a reversed test range and two recovery fixtures whose run snapshot did not include the credited checkpoint baseline. The fixtures were corrected; the production timing behavior was consistent with the checkpoint contract.
4. After those corrections, the fresh `--rerun-tasks` command above passed all 157 domain tests.

## Scenario scope decision

The host component assertions pass, but a pure-domain run cannot pass any full scenario that also requires storage, lifecycle, UI, or reports. QA01–04, QA06–09 and QA11 are recorded as **BLOCKED** in the scenario ledger until FM02/FM03/FM05/FM06 provide those layers. QA05, QA10 and QA12–15 remain **NOT RUN**. No whole-feature, native UI, migration, connected-test, performance, accessibility, or device result is claimed.

## Suggested commit message

```text
fix(focus): verify pure domain timing and statistics

Fix recovery transition syntax found by compilation and add deterministic QA coverage for pause exclusion, monotonic recovery, day/week overlap, DST, and comparison math. Record the FM01 domain pass while keeping integrated scenarios blocked until their storage and Android layers are implemented.
```
