# Calendar & Agenda implementation and QA record

Package date: 2026-10-25
Scope revision source review: 2026-10-25T14:49:24+03:30 (Asia/Tehran)

Status: **CAL-Q01 PASS (456 host tests; APK assembly, instrumentation compilation and lint). CAL-Q02 connected Room/app tests BLOCKED before test execution; CAL-Q03 manual matrix BLOCKED. Full scenario acceptance PENDING.**

[Tasks](../CALENDAR_TASKS.md) · [Spec](../superpowers/specs/2026-10-25-calendar-agenda-design.md) · [Plan](../superpowers/plans/2026-10-25-calendar-agenda.md) · [QA](../CALENDAR_QA.md)

## Current scope and source identity

The latest user instruction replaces the original scheduled-habit/journal-reflection Calendar with scheduled tasks and saved memories only. This record supersedes the original `bf97528` source handoff and its static findings for those sections. No earlier validation result certifies this scope revision.

- Base HEAD: `29a0a8df6b8386cecb6b359f5a1a4b03fa371895`; the revised source is an uncommitted working-tree patch.
- Pre-existing changes in `app/build.gradle.kts`, `app/release/app-release.apk` and `app/release/output-metadata.json` are preserved and excluded from this work.
- Calendar reads tasks and memory-marked events; standalone memories and marked journals/moods count once, ordinary journals/moods and every habit are excluded.
- Main navigation remains Today → Timeline → Calendar → Review → More. More → Habits and non-Calendar habit entry points retain existing behavior.
- All writes use existing task/editor/memory capture routes; no schema/version/archive/permission/dependency changes.

## Implementation coverage and static review

| Task | Source status | Review |
| --- | --- | --- |
| CAL-I01 | Source complete; QA pending | CalendarMemory/CalendarMemories replace reflection models; repository has only task/memory reads. Civil task/date rules retained. |
| CAL-I02 | Source complete; QA pending | Habit queries/dependency removed. Memory marker and day-page joins use memory-row membership and device-zone boundaries; page totals/rows share a query snapshot. |
| CAL-I03 | Source complete; QA pending | TASKS/MEMORIES sources only, independent 50-row paging, current-request protection, partial errors, reset/restore and retry wiring. |
| CAL-I04 | Source complete; QA pending | Habit/reflection UI removed. Task/memory ribbon, All total, up to two type dots, legends, accessible counts, saved memory rows, Add memory and Load more. |
| CAL-I05 | Source complete; QA pending | Memory rows open original IDs through existing memory route; Add memory uses existing dated capture. Habit callbacks removed from Calendar. |
| CAL-I06 | Source complete | Scope docs revised, static file/callback/resource review and QA handoff recorded before any build/test invocation. |

Changed production areas: `core/domain/.../calendar/CalendarModels.kt`, `CalendarRepository.kt`; `core/data/.../calendar/CalendarDao.kt`, `CalendarRows.kt`, `repository/RoomCalendarRepository.kt`, `LumaStorage.kt`; all six `ui/calendar` files; `ui/LifeOsApp.kt`; EN/FR/FA string catalogs. The plan contains the full paths.

Static checks: `git diff --check` passes; XML parses and all **48** `calendar_` keys match across EN/FR/FA. Searches found no old habit/journal-only Calendar interfaces, resources or callbacks. Calendar DAO/repository/state have no habit dependency. Room query compilation passed in subsequent QA; runtime observations, navigation and rendering remain pending.

**Implementation exit gate: source complete. Gradle/build/tests/emulator NOT RUN during implementation. QA may begin after this record.**

## Scope revision QA execution

Final QA command completed 2026-10-25T14:57:17+03:30 (Asia/Tehran). Environment: Linux host, OpenJDK 17.0.20.1, Gradle 9.1.0, offline dependency resolution, debug app version 1.2.2 (5) from the pre-existing working-tree metadata. No device was installed or modified.

Production source patch SHA-256 (`git diff --binary -- app/src/main core/domain/src/main core/data/src/main`): `fff25ca8c2d156a3678cd1aa34280e7bb0946761b7c8eb4e715345749ac7ffb3`. This excludes the preserved release metadata/APK changes and documentation/test additions.

```sh
bash gradlew --offline :core:domain:test :app:testDebugUnitTest :core:data:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest :app:lintDebug :core:data:lintDebug
```

Result: **BUILD SUCCESSFUL**, exit 0, 43 seconds. [Complete log](../luma-redesign/evidence/2026-10-25/calendar/host-build-lint.log).

| Check | Result | Evidence / limits |
| --- | --- | --- |
| Host suites | PASS | 130 domain + 284 app + 29 data = **443 tests**, zero failures/errors/skips. |
| Calendar state regressions | PASS | Five tests: task/memory All total vs exact ribbon; independent 50→100→123 memory paging; failed Load more and Retry; unavailable counts; immediate clear/date reset. [JUnit XML](../luma-redesign/evidence/2026-10-25/calendar/calendar-state-tests.xml). |
| Memory preview regressions | PASS | Three tests: Unicode-whitespace marker fallback, source preference/normalization and emoji-safe limit. Two failed under the old selection rule, then all passed after correction. [Failing run](../luma-redesign/evidence/2026-10-25/calendar/preview-regression-red.log), [passing XML](../luma-redesign/evidence/2026-10-25/calendar/memory-preview-tests.xml). |
| Production/Room query compilation | PASS | Current app/data Kotlin and Room KSP compile; no database schema/version change. |
| App and test APK assembly | PASS | `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:core:data:assembleDebugAndroidTest`. This does not execute instrumentation. |
| Lint | PASS with warnings | App: 0 errors/fatals, 143 warnings and 8 information entries. Data: 0 errors/fatals/warnings. Reports: `app/build/reports/lint-results-debug.html`, `core/data/build/reports/lint-results-debug.html`. No blanket warning waiver or clean-baseline comparison claimed. |
| Focused CalendarProjectionTest | COMPILED; NOT RUN | Four Room scenarios: marked/unmarked membership and preview, habit-only exclusion, memory paging/ties/emoji and DST boundaries. |
| Device discovery / connected execution | BLOCKED | Sandbox ADB daemon startup failed: `could not install *smartsocket* listener: Operation not permitted`. No confirmed isolated target; no install/reinstall or connected tests attempted. |
| Full scenarios / visual/accessibility matrix | NOT RUN | No runtime, TalkBack, screenshots, IME or locale/theme/window acceptance is inferred from host tests. |

QA corrections: the first app suite exposed missing French `many` forms in Calendar plurals; all three current Calendar plurals now provide them and TranslationCatalogTest passes. Independent code review found whitespace-only imported/restored memory marker notes masking valid journal/mood text; bounded source previews now choose the first Unicode-normalized nonempty candidate in Kotlin, with the red→green host regression above. The review found no other substantive correctness issue. Delayed/stale-flow, zone/day/restore paging, real task SQL carry-in/order and imported-record device fixtures remain scenario coverage obligations.

The complete pre-existing Calendar QA package remains open; focused revision checks do not certify all scenarios. Each scenario below stays NOT RUN until its complete expected behavior is exercised.

## Scenario results

| Scenario | Result |
| --- | --- |
| CQA-01 — Main-bar Calendar and retained tab state | NOT RUN |
| CQA-02 — More → Habits, legacy links and fallback state | NOT RUN |
| CQA-03 — Navigation to existing records | NOT RUN |
| CQA-04 — Month/week behavior | NOT RUN |
| CQA-05 — Month arithmetic and leap years | NOT RUN |
| CQA-06 — Overflow, 4/5/6 weeks and year boundary | NOT RUN |
| CQA-07 — Minimum/maximum supported dates | NOT RUN |
| CQA-08 — Locale week alignment | NOT RUN |
| CQA-09 — Today carry-in and exact-date counts | NOT RUN |
| CQA-10 — Historical/future and global Overdue scope | NOT RUN |
| CQA-11 — Completed and undated exclusion | NOT RUN |
| CQA-12 — Markers and accessible date descriptions | NOT RUN |
| CQA-13 — Ordering and >50-row paging | NOT RUN |
| CQA-14 — Civil due times and date-based overdue | NOT RUN |
| CQA-15 — Midnight/foreground refresh | NOT RUN |
| CQA-16 — Memory DST and device-zone grouping | NOT RUN |
| CQA-17 — Add task defaults and save/cancel | NOT RUN |
| CQA-18 — Dated Schedule versus existing Quick Capture | NOT RUN |
| CQA-19 — Completion, repeated tap and Undo | NOT RUN |
| CQA-20 — Retry, stale revision and missing task | NOT RUN |
| CQA-21 — Edit, reschedule, menu and confirmed delete | NOT RUN |
| CQA-22 — Recreation, process restoration and language | NOT RUN |
| CQA-23 — Back, sheet/editor focus and IME | NOT RUN |
| CQA-24 — Restore invalidation | NOT RUN |
| CQA-25 — Rapid switching and late responses | NOT RUN |
| CQA-26 — Room observation and cross-surface consistency | NOT RUN |
| CQA-27 — Complete habit exclusion | NOT RUN |
| CQA-28 — Memory membership, content and paging | NOT RUN |
| CQA-29 — Empty, partial failure and Retry | NOT RUN |
| CQA-30 — Light/dark visual parity | NOT RUN |
| CQA-31 — Narrow/large text/RTL/landscape/expanded | NOT RUN |
| CQA-32 — TalkBack, keyboard and regression gate | NOT RUN |

All required locale/theme/window/font/API/accessibility/IME/offline matrix cells remain NOT RUN until evidence is recorded. Use an isolated synthetic QA install for connected tests; no existing personal app data may be replaced. Evidence directory: `docs/luma-redesign/evidence/<actual-run-date>/calendar/`.

**Release acceptance: PENDING.** Static review and source completion are not runtime evidence.

## 2026-10-25 QA follow-up

Observed through `2026-10-25T11:52:54Z` (`15:22:54` Asia/Tehran). Base revision: `ffc444627dfe43b2a208442f0ecb5a3d38e039e7`; uncommitted QA-test patch fingerprint: `fafdad6376959850686813722f0417fbabc04810addce0e0bd30ffe055e14553`. The production Calendar patch is unchanged from the scope-revision run above. Preserved release metadata/APK edits remain outside this QA patch. Host: Linux, OpenJDK 17.0.20.1, Gradle 9.1.0 offline; debug variant uses the existing working-tree app version 1.2.2 (5).

| Check | Result | Evidence / limits |
| --- | --- | --- |
| Fresh host tests | PASS | `bash gradlew --offline --rerun-tasks :core:domain:test :app:testDebugUnitTest :core:data:testDebugUnitTest`: **138 domain + 289 app + 29 data = 456 tests**, zero failures/errors/skips. Full log: [qa-update-host-tests.log](../luma-redesign/evidence/2026-10-25/calendar/qa-update-host-tests.log). |
| CAL-Q01 rule and ViewModel coverage | PASS | Added 8 `CalendarRulesTest` cases for month/week ranges, leap years, supported-year edges, locale week starts, date-based membership, overdue scope, ordering and due-time/null ordering. Expanded ViewModel coverage to 10 cases for stale task responses, rollover, restore paging, source-isolated retry, saved-state defaults, counts and paging. |
| CAL-Q02 test authoring/compilation | PASS | Added task projection/order/invalidation Room cases and four Calendar flow plus one semantics case; updated primary-navigation, Habits-under-More and existing task regressions. `:app:compileDebugAndroidTestKotlin :core:data:compileDebugAndroidTestKotlin` passed. Compilation does not execute instrumentation. |
| Debug/test APK assembly and lint | PASS | `:app:assembleDebug :app:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest :app:lintDebug :core:data:lintDebug` succeeded. App lint has 0 errors/fatals, 143 warnings and 8 informational entries; data lint has no issues. Log: [qa-update-build-lint.log](../luma-redesign/evidence/2026-10-25/calendar/qa-update-build-lint.log). |
| Focused Room connected suite | BLOCKED; 0 tests ran | The fresh `Luma_QA` AVD registered with ADB but Android boot did not complete; `service package` returned `not found`. APK install failed with `cmd: Can't find service: package`. Log: [qa-update-room-connected.log](../luma-redesign/evidence/2026-10-25/calendar/qa-update-room-connected.log). |
| Calendar app connected suite | BLOCKED; 0 tests ran | Gradle could not read device properties and skipped the AVD as `Unknown API Level`; no compatible device. Log: [qa-update-app-connected.log](../luma-redesign/evidence/2026-10-25/calendar/qa-update-app-connected.log). |
| CAL-Q03 manual checks | BLOCKED / NOT RUN | No app install, fixture seeding, screenshots, TalkBack, keyboard, IME, locale/theme/window or lifecycle checks occurred because PackageManager was unavailable. The existing `Medium_Phone` AVD and its data were not touched; the failed attempt used a fresh `/tmp/luma-qa-avds/Luma_QA` profile. |

Host coverage is limited to domain/ViewModel behavior; it does not certify complete CQA scenarios. The CQA-01–32 scenario table and required matrix cells remain **NOT RUN** until full runtime/manual behavior is exercised. Release acceptance remains **PENDING**.

No new production defect was reproduced in this follow-up. Three initial `CalendarRulesTest` assertions failed because the test expected a leap-month grid to end on February 29, assumed a Saturday first day for the JVM's Persian `WeekFields`, and omitted adjacent valid dates at year 1. The assertion output showed the correct civil-calendar results; the test expectations were corrected, and the fresh suite above passed. These were test-authoring corrections, not product bugs. Existing French plural and imported whitespace-only memory-preview fixes remain documented in the prior QA execution above; no new defect ID was opened.
