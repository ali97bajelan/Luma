# UX 18: persistent Back and Edit on detail screens

Status: design approved; implementation complete on working tree based on HEAD `b52b9a7`; manual acceptance remains pending. Related: [handoff](../../UX_DEVELOPMENT_READY_2026-10-14.md), [plan](../plans/2026-10-14-ux-18-detail-navigation.md), [acceptance](../../UX_ACCEPTANCE_2026-10-14.md).

## Intent and scope

A person reading a long journal, event, person or place retains a visible way to return or intentionally edit. Keep existing routes, read-before-edit behavior, memory actions, history order, observation/retry and draft confirmations. This is a layout change; no detail repository or navigation contract changes.

## Current structure and decisions

Journal/Event use a scrollable Column with the Back/Edit Row inside it. Person/Place use a LazyColumn with the controls in the first item. All four therefore lose those controls while reading later content.

Use a fixed Material 3 top app bar in each detail's Scaffold. A sticky lazy-list header could fix two lists but would leave inconsistent behavior for journal/event reading; a generic screen framework would overcomplicate four routes. Reuse one small `DetailTopBar`, and retain each route's current content container.

## Interaction and layout

- The top bar title is the localized record type or catalog type: Journal, the current EventType label (Event while unresolved), Person, Place. Keep the full user-written title/name in the body; long user text does not displace controls.
- Navigation uses the existing auto-mirrored previous chevron with accessible label Back. Do not reuse the editor's close icon or Cancel semantics.
- The Edit action uses a compact localized `Edit` label. It is shown only when the current state permits editing. Its semantic label includes the type (`Edit journal`, existing person/place labels, or event type). Avoid the longer visible `Edit person`/`Edit place` strings crowding the title at large text sizes.
- Bar remains fixed; configure no enter-always/exit-until-collapsed scroll behavior. Status-bar insets and Scaffold content padding prevent overlap. Do not double-apply top insets under `LifeOsApp`'s outer Scaffold.
- Every bar action has a 48 dp minimum target and keyboard/TalkBack access. The title may ellipsize visually to one line but retains complete localized semantics. At 320 dp/scale 2.0 Back and Edit remain visible and operable.
- Journal/Event body scrolls independently with remembered ScrollState. Person/Place history remains lazy with stable event keys and remembered LazyListState. Moving the header must not reset content state on ordinary recomposition. Existing return/recreation behavior must not regress; this task does not invent a new cross-process scroll persistence subsystem.
- Loading, missing and error states always retain Back. Hide Edit when there is no loaded editable target. Retry stays in content.
- Journal: preserve `!loading && record?.event?.isEditable == true` Edit condition.
- Event: preserve `record.canEditContent`; imported usage/activity records stay read-only. Edit callback continues to pass the current loaded record, never a captured stale one.
- Person/Place: Edit is available only in their respective history Data state; after deletion/missing it disappears. Their detail-ready test tags move to the persistent header.
- Existing BackHandler and toolbar Back use the same callback. Returning goes to the invoking screen with its date/query/week/catalog state intact. Editing and saving returns through the current route behavior.
- Memory marking/editing remains in the reading body; do not duplicate its record or add Delete to the top bar.

## Components and file responsibilities

Create `ui/common/DetailTopBar.kt`:

`@Composable fun DetailTopBar(title: String, backTag: String, onBack: () -> Unit, editTag: String? = null, editLabel: String? = null, onEdit: (() -> Unit)? = null, modifier: Modifier = Modifier)`.

Back always renders. Render Edit only when `onEdit != null`; require editTag/editLabel in that case. `editLabel` supplies the full localized semantic name, while visible text uses the existing generic Edit resource if present, otherwise add `detail_edit` = `Edit`. Add localized `event_detail_title` = `Event`, `person_detail_title` = `Person`, and `place_detail_title` = `Place`; reuse `event_journal` and existing EventType labels for resolved records. Preserve `place_detail_back`, `journal_edit`, `event_edit`, `person_edit`, `place_edit`, `person_detail_ready/header`, `place_detail_ready/header`. Add `journal_detail_back`, `event_detail_back`, `person_detail_back` and `detail_top_bar`.

Modify `JournalDetailRoute.kt` and `EventDetailRoute.kt`: Scaffold topBar plus existing body Column with content padding; remove only the old navigation Row. Modify `PersonDetailRoute` inside `PeopleRoute.kt` and `PlaceDetailRoute` inside `PlacesRoute.kt`: Scaffold topBar plus current LazyColumn with content padding; remove only old first-item navigation Row. Do not split unrelated catalog list implementations. Existing `screen_*_detail` tags identify the screen container; tests must not assume it is the scrollable body. Add `<journal|event|person|place>_detail_content` tags to scrollable bodies for explicit scroll tests.

No changes to `LifeOsApp.kt` callbacks or route strings are expected. If nested inset behavior requires adjustment, make it narrowly and regression-test other secondary routes.

## Accessibility basis

Fixed bars use Material 3's [TopAppBar and Scaffold integration](https://developer.android.com/develop/ui/compose/components/app-bars), with app-specific localized actions. The editor continues to use its existing EditorTopBar. Detail and editor bars differ intentionally because leaving a reading view is Back while abandoning an editor can require confirmation.

## Acceptance and verification

- **N18.1:** Scroll a multi-screen journal, mood/direct-memory/imported event, person history and place history to the end; Back remains visible and functional. Edit remains visible when allowed.
- **N18.2:** Imported events, loading, missing and error states never expose an invalid Edit action, and always permit Back.
- **N18.3:** Back returns to the correct Today/Timeline/Search/Review/catalog context; toolbar and system Back agree. Existing read → edit → detail flow and memory actions work.
- **N18.4:** Live deletion/updates refresh edit availability; Retry recovers without losing navigation controls.
- **N18.5:** No content sits under the bar or system insets. Photo controls/history rows remain reachable, and large titles do not push actions away.
- **N18.6:** Persian RTL, 320 dp, landscape, scale 2.0, keyboard and TalkBack support both bar actions without duplicate focus targets.

Use a shared bar Compose test for action contract/insets and route tests with generated long content/history. Assert actions are actually displayed after scrolling, not merely present in the semantics tree. Keep existing navigation, capture and read-before-edit tests. Manual TalkBack checks spoken names and traversal after a long scroll.
