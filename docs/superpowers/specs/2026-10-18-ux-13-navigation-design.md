# UX-13: Fixed secondary Back/title bars

Status: implementation-ready proposal; product changes pending.
Source: [review](../../UI_UX_REVIEW_2026-10-18.md); [brief](../../UI_UX_IMPLEMENTATION_READY_2026-10-18.md).

## Intent and approach

Back and title remain in a consistent fixed location while content scrolls. Extend DetailTopBar's existing behavior rather than invent a generic screen framework. Copy-only alignment leaves Privacy and saved-usage Back inside scroll content.

## Route inventory and scope

| Surface | Required treatment |
| --- | --- |
| Settings hub and SettingsSectionPage (language/backup/integrations) | Fixed DetailTopBar; content scrolls below |
| Search | Fixed DetailTopBar; query/filter controls remain below, results scroll |
| Privacy | Fixed bar outside scroll, busy navigation behavior shared with system Back |
| Habit detail | DetailTopBar, retaining correction-first close/dirty flow |
| People/Places catalogs opened as secondary destinations | Fixed bar; preserve create/switch-catalog actions |
| Review sources and saved app usage | Fixed bar outside list; retain period/source context in content |
| Existing event/journal/person/place detail routes | Already use shared pattern; verify no regression |
| Mood/Journal/Memory/Habit/Person/Place editors | Keep Close/Cancel and existing dirty/busy rules; do not replace with ordinary Back |
| Five primary tabs and Review fallback | No new secondary Back bar |
| Native Health rationale activity | Keep its standalone Close action; outer-Scaffold assumptions do not apply |

The HabitsScreen nullable onBack path should use the shared bar only when actually opened as a secondary route; primary Habits retains its primary heading/Add action.

## Shared contract

Extend DetailTopBar with actions: @Composable RowScope.() -> Unit = {} and backEnabled: Boolean = true after existing parameters, preserving edit action. Keep icon auto-mirroring resource, accessible Back name, full title semantics despite one-line visual ellipsis. Keep existing back test tags where present; add search_back, review_sources_back and review_usage_back.

Use each screen's Scaffold.topBar. LifeOsApp outer Scaffold owns system insets; DetailTopBar keeps WindowInsets(0,0,0,0). Content consumes only its inner scaffold padding once. Do not move navigation bars or add another status-bar spacer.

One close handler per route serves icon and system Back. Ordinary secondary routes pop once. Habit correction closes/asks discard first per UX-03; a picker/dialog handles Back before its parent route. Busy privacy reset cannot navigate while accepted/reset in progress: system Back and icon use the same guard. Other editor busy rules remain untouched.

No navigation graph, saved-state ownership, source order, search query/filter semantics or Search scope changes. No new generic ScreenScaffold abstraction.

## Acceptance

For every inventory route test long content and loading/empty/error states. Back is fixed after scroll; icon/system Back reach the same destination without double-pop. Verify dirty correction, busy reset, restored selected date and detail-return scroll. RTL arrow mirrors; full long title remains accessible. Existing UX-11 initial/empty/no-match states, reset behavior and 180/200 counter thresholds remain intact.

