# Fresh UI review — task catalog

**Date:** 21 October 2026
**Source:** [Fresh UI/UX review](APP_FLOW_UX_REVIEW_2026-10-21-FRESH.md)
**Status:** FRESH-001 through FRESH-006 implementations are in place; Gradle and emulator verification are deferred at the user's request.

This package develops the fresh review's recommended visual refresh. It preserves the current five destinations and product behavior. Earlier review documents and their task IDs are not prerequisites for this package.

- [Design specification](superpowers/specs/2026-10-21-fresh-ui-refresh-design.md)
- [Implementation plan](superpowers/plans/2026-10-21-fresh-ui-refresh.md)

## Tasks

| ID | Deliverable | Review findings | Dependencies | Specification | Implementation plan | Status |
| --- | --- | --- | --- | --- | --- | --- |
| FRESH-001 | Shared surfaces, typography roles, spacing, and accessible choice rows | 5, 6 | None | [Spec](superpowers/specs/2026-10-21-fresh-ui-refresh-design.md#fresh-001) | [Plan](superpowers/plans/2026-10-21-fresh-ui-refresh.md#fresh-001) | Implemented; verification deferred at user request |
| FRESH-002 | Compact Today previews and coherent habit presentation | 1, 2 | FRESH-001 | [Spec](superpowers/specs/2026-10-21-fresh-ui-refresh-design.md#fresh-002) | [Plan](superpowers/plans/2026-10-21-fresh-ui-refresh.md#fresh-002) | Implemented; verification deferred at user request |
| FRESH-003 | Timeline header with direct search and expandable view options | 3 | FRESH-001 | [Spec](superpowers/specs/2026-10-21-fresh-ui-refresh-design.md#fresh-003) | [Plan](superpowers/plans/2026-10-21-fresh-ui-refresh.md#fresh-003) | Implemented; Gradle and emulator verification deferred at user request |
| FRESH-004 | Modular Weekly Review overview with quieter setup information | 4 | FRESH-001 | [Spec](superpowers/specs/2026-10-21-fresh-ui-refresh-design.md#fresh-004) | [Plan](superpowers/plans/2026-10-21-fresh-ui-refresh.md#fresh-004) | Implemented; Gradle and emulator verification deferred at user request |
| FRESH-005 | Consistent More destinations and compact Quick Capture choices | 5 | FRESH-001 | [Spec](superpowers/specs/2026-10-21-fresh-ui-refresh-design.md#fresh-005) | [Plan](superpowers/plans/2026-10-21-fresh-ui-refresh.md#fresh-005) | Implemented; Gradle and emulator verification deferred at user request |
| FRESH-006 | Confirmed save acknowledgement and restrained habit progress motion | 6 | FRESH-001, FRESH-002 | [Spec](superpowers/specs/2026-10-21-fresh-ui-refresh-design.md#fresh-006) | [Plan](superpowers/plans/2026-10-21-fresh-ui-refresh.md#fresh-006) | Implemented; Gradle and emulator verification deferred at user request |

FRESH-001 supplies shared components. FRESH-002 through FRESH-005 can be delivered individually after that foundation; shared-file edits should have one implementation owner. FRESH-006 follows the habit presentation change. Integrated verification follows all six tasks.

## Coverage

| Fresh finding | Result |
| --- | --- |
| 1. Competing Today blocks | FRESH-002 reduces nested containers and keeps capture, habits, tasks, and entries in a stable order. |
| 2. Oversized habit preview | FRESH-002 fixes the compact layout threshold and removes redundant ordinary empty-status text from the visible compact row. |
| 3. Timeline controls dominate | FRESH-003 groups secondary options while keeping date, search, counts, and active app-usage context visible. |
| 4. Weekly Review feels continuous | FRESH-004 splits the existing overview into personal summary, latest memory, and saved signals; existing detailed sections remain. |
| 5. Inconsistent visual language | FRESH-001 defines reusable patterns; FRESH-002–005 apply them to the reviewed surfaces. |
| 6. Limited personal identity | FRESH-001 uses the existing warm tertiary color for reflection; FRESH-006 acknowledges confirmed actions without inventing milestones. |

## Source details affecting implementation

- `HabitRow` currently stacks its action when its local width is below 336 dp or font scale is at least 1.5. Nested padding can push a normal Today preview below that width. The compact variant needs its own layout rule; shrinking the touch target is not the remedy.
- Weekly Review already has detailed sections and section navigation below `ReviewOverview`. The work restructures the overview rather than rebuilding the report or adding a second section navigator.
- Today already limits scheduled habits to two and due/overdue tasks to three. These limits, full eligible counts, and retained recovery controls remain.
- `EntryEditorResult.Saved` already represents a completed save. It is the acknowledgement trigger; pressing Save or receiving a failed write is not.

## Completion criteria

Each task's acceptance scenarios must pass, followed by the plan's integrated compile, resource/lint, targeted regression, and emulator checks. Visual validation covers light/dark themes, English/French/Persian, large text, a narrow phone, and a wide window. Verification results must name what was actually checked and any unverified cases.

No application build, app tests, or new emulator walkthrough was run to generate this planning package. The earlier fresh review supplies the live observations; current source inspection supplies the implementation boundaries.

## Documentation checks

- Checked 24 local links and 40 explicit file paths; proposed new files are identified separately from existing source files.
- Confirmed six task mappings, 30 acceptance scenarios, 19 planned test-class targets, and identical global constraints in the specification and plan.
- Confirmed implementation steps remain unchecked and no application verification is claimed.
