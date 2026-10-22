# T31 implementation review — 2026-10-22

Status: **implemented (validation pending)**. This records source/build checks and a T33 handoff, not release acceptance.

Execution date: 2026-10-22, Asia/Tehran. Base revision: `a03eeb86a7a898d558e7ed7b16909af1ad5e1047`; implementation is an uncommitted working-tree diff on `main`. Build SHA/device/API/locale/theme/window/font scale: **N/A — no device run**. The required runtime matrix remains English/French/Persian, Light/Dark/System, 320 dp at 2.0, 411 × 891 dp at 1.0, and 840 dp width.

## Implemented scope

- Increased the Today page and recent-entry heading hierarchy while retaining the existing day picker, Today action and historical-date notice. The habit tracking-zone context now appears between date context and capture choices and uses only `HabitsUiState.trackingZone`.
- Kept the Mood, Journal and Memory capture actions and their date-confirmation callbacks. Their controls now use a pill shape and retain a 48 dp minimum target.
- Styled the bounded scheduled-habit preview as nested surfaces. Each row retains its name, exact count/target, unit, status, open action, separate +1 action, pending/retry/undo feedback and stable ID-based test tags. The progress fill remains clamped and keeps its existing confirmed-write animation; visible percentages format the clamped value (including 2/8 → 25% and 1/5 → 20%). Sage/ember accents derive deterministically from the saved habit ID, not a translated name or stored category/setting. The full scheduled count remains in View all.
- Grouped only the already-loaded Today task prefix under Overdue and Today headings using the existing `todoBucket` rule. The repository limit and full eligible count are unchanged. Existing row mutations, conflict recovery, retry/dismiss actions, View all and Add actions remain wired.
- Rounded and restyled recent-entry and saved-summary surfaces while retaining actual saved values, unavailable/error states, disclosure behavior and callbacks.
- Existing selected-date load guards and task visibility remain in `TodayRoute`; the Room Today query continues to select only open tasks with a due date on or before the device date. No optional O-task, dependency, permission, schema or privacy claim was added.

## Checks performed

| Check | Result | Evidence / limits |
| --- | --- | --- |
| `bash gradlew :app:assembleDebug :app:lintDebug --offline` | PASS | `BUILD SUCCESSFUL`; Kotlin compilation, debug APK assembly and lint completed. Existing Gradle configuration/deprecation warnings remain. No test task was invoked. |
| `git diff --check` | PASS | No patch whitespace errors. |
| Static contract review | PASS | Reviewed Today selected-date guards/capture callbacks, the three-row Today state limit, Room query eligibility (`completed_at IS NULL AND due_date <= :date`), habit row ID keys, separate log/open actions and off-preview recovery. This does not verify runtime behavior. |

## Checks not run

The user explicitly requested no Gradle tests or emulator. No unit/instrumented test task or emulator/device work was performed.

| Check | Result | Follow-up |
| --- | --- | --- |
| TodayScreen, persistence, day-navigation and habit date-selection regression tests | NOT RUN | T33: run the named scenarios in an authorized validation session. |
| Today task-flow and recovery tests; daily-summary flow tests | NOT RUN | T33: validate full totals, three-row grouping, conflict/retry/undo and saved-summary states. |
| Before/after captures and light/dark/RTL/locale/window/font-scale matrix | NOT RUN | T33: inspect actual rendered screens at the required widths, font scales and languages. |
| Runtime empty/loading/error/integration-disabled states, focus order, TalkBack, insets and action reachability | NOT RUN | T33: exercise on device and record actual build/device/configuration evidence. |

## Implementation decisions

- Keep grouping presentation-only over `TodayTasksState.Data.tasks`, and use the full repository total for the existing count label. Cost if wrong: a future repository contract change will require revisiting the displayed groups while keeping that full total authoritative.
- Derive the decorative habit accent from the stable saved ID, without associating color with habit meaning or translated name. Cost if wrong: accent assignments are arbitrary and may need a different deterministic rule.
- Follow the established T30 workspace flow: generate a task-specific commit message, but do not create a commit. Proposed message: `feat(ui): redesign Today screen hierarchy and previews`.

Final review: author self-review of the source diff and build/lint output; no independent reviewer was used. No release acceptance is claimed. T33 remains responsible for behavior tests, rendered visual/accessibility evidence and the required device matrix.
