# Luma app flow and UI/UX review

- **Review date:** 21 October 2026
- **Source baseline:** `e7214fe` plus the existing local change in `TodayHabitsCard.kt`
- **Review type:** one-pass emulator walkthrough plus source review, reconciled with the prior audit
- **Live walkthrough:** completed on emulator-5554; normal flows only.

## Executive assessment

Luma has a clear product shape: a private life log with five primary destinations, direct daily capture, reusable people and places, lightweight habits and tasks, and a weekly reflection. The existing Material 3 foundation and calm green palette fit that purpose. The app will benefit more from consistent navigation, readable hierarchy, and dependable action feedback than from a new brand or a full redesign.

The main design debt is unevenness between flows. Similar pages use different headers and spacing; some recovery and navigation states can leave the user unsure which record or date an action affects; and a few controls communicate poorly to assistive technology. The strongest visual opportunity is to make the existing content easier to scan through shared page structure, clearer card hierarchy, and concise status copy.

The 20 October repository audit describes 28 issues. This review carries forward only the 16 findings still marked open and the one optional finding; rows marked implemented in the audit’s status index have been removed. It also adds live observations, redesign suggestions, and the five fictional entries added to each requested item type.

The [task index](APP_FLOW_UX_REVIEW_2026-10-21-PLANS.md) links a specification and implementation plan for every remaining finding and the additional design tasks.

## Scope and evidence

The requested one-pass walkthrough was completed on the running API 36 emulator (1080 × 2400, English UI, Asia/Tehran timezone). The device date was 21 October 2026; the installed Luma package reported versionCode 2. The installed binary was not matched to a source revision, so live observations describe that emulator build while source findings describe the checked-out worktree.

I visited Today, Timeline, Search, Habits, Weekly Review, More, Tasks, People & Places, Settings, Home-screen widgets, Language, Backup, Privacy, and Integrations. I completed one normal save flow for Mood, Journal, Memory, Habit, Task, Person, and Place; added five fictional entries in each category; logged one habit repetition; and completed one task. Search was queried once with `walk`. I read the review and settings surfaces without changing language, theme, widget placement, backup data, or provider access. No edge-case, destructive, restore, permission-grant, accessibility-service, or large-text testing was done. Journal tags/photos and optional provider imports were not exercised.

The five habits already present in the emulator were left intact. Five new habits were added, leaving ten total; the five tasks, five people, and five places were created during this walkthrough. The review dataset was entered on 21 October 2026; all entries and names are fictional. No emulator screenshots were added to the repository.

I also reviewed the current navigation graph and UI route inventory, the existing 20 October source audit, its implementation-status index, and the current change in `TodayHabitsCard.kt`. Findings below use these status labels:

- **Open:** the implementation index marks the work as not started.
- **Optional:** a capability enhancement, not a correction required for the core flows.

For the detailed source locations, acceptance criteria, and existing task plans, see [the 20 October audit](UI_UX_AUDIT-20261020N.md) and [its implementation index](UI_UX_AUDIT-20261020N-IMPLEMENTATION.md). The walkthrough below covers normal flows only; recovery, accessibility, permission, and edge-case acceptance remain outside its scope.

The existing uncommitted change in [TodayHabitsCard.kt](../app/src/main/java/com/luma/lifeos/ui/habits/TodayHabitsCard.kt) reduces the Today preview from three scheduled habits to two and reduces internal vertical gaps from 8 dp to 4 dp. It is preserved. The populated emulator view showed two habit rows and “View all”; adding a remaining-item count would make the hidden rows easier to discover.

## Flow coverage map

This records the actual one-pass walkthrough. “Visited” means the screen or normal path was opened once; it does not mean every option or recovery state was exhaustively tested.

| Area | Coverage | Result |
|---|---|---|
| Today and capture forms | Visited Today; saved 5 Mood, 5 Journal, and 5 Memory entries | Captures saved and appeared in Timeline/Review. Mood and Memory optional context controls were inspected. |
| Timeline and detail | Visited Timeline; opened and edited one Memory detail | Date/time, entry details, edit return, and populated list checked. |
| Search | Queried `walk` once | One match, “Morning walk”; field copy clearly states that notes, journal text, habit logs, and tasks are excluded. |
| Habits | Added 5 scheduled habits and logged one repetition | Habit list and normal save feedback checked. Five existing habits were preserved. |
| Tasks | Added 5 tasks and completed one | One task had a due date; four had none. Completed state and undo feedback appeared. |
| People & Places | Added 5 people and 5 places; linked one of each to a Mood entry | Catalog cards and form selectors checked. Selector options are inline chips without search. |
| Weekly Review | Opened Overview, section summaries, Mood, Memories, and People & Places | Review showed 15 entries, 1 habit log, Mood average 3.6/5, and the linked person/place. |
| More and Settings | Opened Tasks, People & Places, Settings, widgets, language, backup, privacy, and integrations | Read-only settings inspection; no widget, permission, language, theme, export, or restore action was triggered. |
| Not exercised | Tags, photo attachment, provider imports, widget placement, backup/restore, destructive actions, edge cases, accessibility services, and large text | Outside the requested light pass; no claim of acceptance for these paths. |

The pass stayed with ordinary use and stopped when each result was visible. The findings below combine these live observations with the existing source audit; follow the audit’s separate acceptance plans for recovery and edge cases.

## Additional live observations

These are direct observations from this emulator pass, separate from the remaining source-audit findings below.

| Observation | User effect | Complete solution direction |
|---|---|---|
| The Today screen initially showed 18 Oct; after reopening Luma it showed 19 Oct, while the device date and the Today control showed 21 Oct. | A user can return to Today and unknowingly browse a past day, with the date row as the only clue. This may reflect the emulator’s saved selection, so confirm the intended resume policy before changing behavior. | Start Today on the current local date for a fresh session. If Luma intentionally restores a prior selection, label the state “Viewing [date]” and keep a clear, high-contrast Today action beside it. |
| Today’s empty Tasks card showed both “0 open tasks due today or earlier” and “No open tasks due today or earlier,” followed by Add task. | The repeated zero message uses space without adding meaning. | Keep one compact status sentence and one prominent Add task action; reserve counts for nonempty lists or integrate the count into the card title. |
| The Today title is compact, while Timeline uses a small “Luma” eyebrow and a much larger “Timeline” title. | Primary destinations feel like separate page families and the Timeline header takes more vertical space before its content. | Use the shared page-header system proposed in UX-021: one title size, consistent top inset, and a compact secondary label only when it adds context. |
| Habit cards show a small isolated dot beside numeric progress (for example, “0 of 10 times”). | The dot has no clear meaning and reads like a stray mark rather than progress. | Replace it with a visible, labeled progress track or remove it; retain the numeric value as the source of truth and add an accessible progress description. |
| The Today habit preview shows two rows and “View all,” without indicating how many scheduled habits are hidden. | A two-row preview is compact, but users may not know there are more habits below the fold or in the full list. | Keep the two-row limit and change the affordance to “View all (N)” or add “+N more,” using the total scheduled for the selected date. |
| Mood context choices for People and Place are shown as inline chips, with no search field; five options fit in the form during this pass. | The compact catalog works at this size but will push the save action farther down as the user’s contacts and places grow. | Keep selected chips visible, but open a searchable, lazy selector with selection count, Done, Clear, and Create actions, as specified in UX-012. |

The normal capture forms were legible and direct: Mood presents its five-point scale first, Journal and Memory prioritize the writing area, and Save remains reachable at the bottom. Search also makes its narrow index scope explicit, and Settings explains that app data stays local and provider access is optional. Preserve those strengths while tightening the shared page rhythm.

## Remaining findings and complete solution direction

The findings below are the items still marked open or optional in the implementation index. Each row describes the user impact and a complete solution direction.

### P1: privacy, date context, and draft safety

| ID / status | Issue and user effect | Recommended solution |
|---|---|---|
| **UX-002 — Open** · [Spec](superpowers/specs/2026-10-20-audit-n-ux-002-widget-mode-status-design.md) · [Plan](superpowers/plans/2026-10-20-audit-n-ux-002-widget-mode-status.md) | Widget settings can describe an unreadable configuration as Summary, which gives false confidence about what the launcher displays. | Model configuration as Known, Unconfigured, or Unavailable. On read failure show “Mode unavailable” with Retry/Configure. Say Summary or Details only after a successful read or acknowledged save. Keep the widget instance and its identity visible during recovery. |
| **UX-003 — Open** · [Spec](superpowers/specs/2026-10-20-audit-n-ux-003-widget-privacy-switch-design.md) · [Plan](superpowers/plans/2026-10-20-audit-n-ux-003-widget-privacy-switch.md) | The widget privacy switch is not clearly named for screen readers, so the privacy consequence may be unclear at the control. | Make the label and explanation one accessible switch target with a checked state and a concise name such as “Show names and row actions.” Avoid nested duplicate click targets. Preserve the current opt-in behavior. |

### P2: recovery, navigation, and form clarity

| ID / status | Issue and user effect | Recommended solution |
|---|---|---|
| **UX-010 — Open** · [Spec](superpowers/specs/2026-10-20-audit-n-ux-010-deleted-detail-navigation-design.md) · [Plan](superpowers/plans/2026-10-20-audit-n-ux-010-deleted-detail-navigation.md) | Deleting an entry can return to a detail route for the record that no longer exists. | Return to the originating list or a stable parent when deletion succeeds. Remove only the deleted detail destination from the back stack; preserve normal back navigation when the user cancels. Give the list a sensible focus/scroll anchor after removal. |
| **UX-012 — Open** · [Spec](superpowers/specs/2026-10-20-audit-n-ux-012-searchable-context-selection-design.md) · [Plan](superpowers/plans/2026-10-20-audit-n-ux-012-searchable-context-selection.md) | Selecting context requires scanning a long catalog, and option lists can make capture forms feel crowded. | Keep selected chips visible in the form and move the full choice set into a searchable, lazy selector. Provide selection count, Done, Clear, and Create actions; preserve in-progress selection while searching or creating an item. |
| **UX-013 — Open** · [Spec](superpowers/specs/2026-10-20-audit-n-ux-013-habit-targeted-history-design.md) · [Plan](superpowers/plans/2026-10-20-audit-n-ux-013-habit-targeted-history.md) | A Timeline/Review link to an older habit repetition may open history without locating the linked repetition. | Load the requested log independently of the current history page, identify it by stable ID, and scroll it into view with a visible selected state. Keep its original date and correction context attached. |
| **UX-014 — Open** · [Spec](superpowers/specs/2026-10-20-audit-n-ux-014-habit-detail-management-design.md) · [Plan](superpowers/plans/2026-10-20-audit-n-ux-014-habit-detail-management.md) | Habit management may be placed after a long history, making pause/edit/archive controls hard to find. | Give management a stable, early entry point on habit detail, above or outside the expandable history. Keep history lazy and retain the same habit identity when returning from management. |
| **UX-017 — Implementation complete; verification pending** · [Spec](superpowers/specs/2026-10-20-audit-n-ux-017-integration-error-recovery-design.md) · [Plan](superpowers/plans/2026-10-20-audit-n-ux-017-integration-error-recovery.md) | A successful Usage/Health settings refresh can leave stale error text on screen. | Separate configuration-read errors from permission, import, and refresh errors. Clear only the state that actually recovered, retain unrelated failures, and show the latest acknowledged result beside its action. |
| **UX-019 — Implementation complete; verification pending** · [Spec](superpowers/specs/2026-10-20-audit-n-ux-019-shared-display-time-design.md) · [Plan](superpowers/plans/2026-10-20-audit-n-ux-019-shared-display-time.md) | Capture and saved records can format time using different 12/24-hour conventions. | Use one localized display formatter that follows the device hour-cycle preference on every surface. Preserve stored timestamps, dates, zones, and precision; display the relevant zone where it affects interpretation. |
| **UX-020 — Open** · [Spec](superpowers/specs/2026-10-20-audit-n-ux-020-entry-card-action-labels-design.md) · [Plan](superpowers/plans/2026-10-20-audit-n-ux-020-entry-card-action-labels.md) | An accessibility action can announce Edit when activating the card actually opens a read-only detail page. | Name the card action for its real destination (“Open [entry type]”). Keep Edit as a separate action from detail when editing is allowed; imported/read-only records should never announce an unavailable edit. |

### P2: shared interaction and presentation consistency

| ID / status | Issue and user effect | Recommended solution |
|---|---|---|
| **UX-018 — Implementation complete; verification pending** · [Spec](superpowers/specs/2026-10-20-audit-n-ux-018-accessible-async-feedback-design.md) · [Plan](superpowers/plans/2026-10-20-audit-n-ux-018-accessible-async-feedback.md) | Important asynchronous results may not be announced, leaving screen-reader users unsure whether a save, import, language change, or mutation finished. | Give pending, success, and error states the right live-region semantics. Include the affected operation and target in persistent errors, announce success once, and keep recovery visible after a transient snackbar disappears. |
| **UX-021 — Open** · [Spec](superpowers/specs/2026-10-20-audit-n-ux-021-page-header-insets-design.md) · [Plan](superpowers/plans/2026-10-20-audit-n-ux-021-page-header-insets.md) | Headers, insets, and content gutters vary between page families, which makes related routes feel like separate apps. | Establish one shell-owned system-inset contract and shared page headers. Keep primary titles and secondary Back/title/actions pinned, use consistent compact gutters and reading widths, and let nested content own only its scroll and keyboard layout. Pilot on Today, Timeline, and one editor before broad migration. |
| **UX-022 — Open** · [Spec](superpowers/specs/2026-10-20-audit-n-ux-022-semantic-headings-design.md) · [Plan](superpowers/plans/2026-10-20-audit-n-ux-022-semantic-headings.md) | Some visible page or section titles lack heading semantics, making screen-reader navigation slower. | Mark the page title and meaningful section labels as headings in reading order. Avoid turning repeated card labels or decorative text into noisy headings. Keep the visual type hierarchy and semantic hierarchy aligned. |
| **UX-023 — Open** · [Spec](superpowers/specs/2026-10-20-audit-n-ux-023-named-stateful-disclosures-design.md) · [Plan](superpowers/plans/2026-10-20-audit-n-ux-023-named-stateful-disclosures.md) | Expanded disclosures can lose their subject and state from their accessible name. | Keep the subject in both states (for example, “Health details”) and expose expanded/collapsed state. Preserve the state through ordinary recomposition and make the entire disclosure a single reachable control. |
| **UX-024 — Open** · [Spec](superpowers/specs/2026-10-20-audit-n-ux-024-backup-departure-outcome-design.md) · [Plan](superpowers/plans/2026-10-20-audit-n-ux-024-backup-departure-outcome.md) | Leaving a Backup route during export or restore preparation can leave the operation’s outcome unclear. | Give each operation a clear route owner and status. Before leaving cancelable preparation, ask whether to cancel; during the protected restore commit, keep the user on the operation. Wait for cleanup and report completed, canceled, or failed with the resulting file/data state. |
| **UX-025 — Open** · [Spec](superpowers/specs/2026-10-20-audit-n-ux-025-restore-dialog-reachability-design.md) · [Plan](superpowers/plans/2026-10-20-audit-n-ux-025-restore-dialog-reachability.md) | The Restore confirmation warning can grow beyond the dialog viewport and hide important text or actions. | Use a height-bounded, scrollable warning area with title and Cancel/Restore controls kept reachable. Show archive date and replacement counts before confirmation, and keep the full replacement warning adjacent to the final action at large text sizes. |
| **UX-026 — Open** · [Spec](superpowers/specs/2026-10-20-audit-n-ux-026-activity-number-precision-design.md) · [Plan](superpowers/plans/2026-10-20-audit-n-ux-026-activity-number-precision.md) | Small but nonzero Weekly Review activity can be rounded to “0,” which looks like no activity occurred. | Use a precision-preserving localized number format and unit. Distinguish a true zero from a small measured amount; keep the current rounding policy for mood/rating averages unless separately justified. |
| **UX-028 — Optional; not selected** · [Spec](superpowers/specs/2026-10-20-audit-n-ux-028-optional-photo-viewer-design.md) · [Plan](superpowers/plans/2026-10-20-audit-n-ux-028-optional-photo-viewer.md) | Photo previews cannot be opened into a larger inspection view. | If user need is established, add a private full-screen viewer with fit-to-screen, bounded decode memory, zoom, and an obvious close action. Keep this separate from core fixes and preserve the stored original. |

## Visual redesign direction

Keep the existing Luma palette and Material 3 components. Make the app feel more finished by reducing local variation and giving personal content more room:

1. **Use one page rhythm.** Align page titles, Back behavior, content gutters, card spacing, and bottom navigation clearance. Keep primary destinations compact and pin Back/title/action bars on secondary pages.
2. **Give Today a clear first glance.** Put the selected date and one primary capture action first. Follow with a concise Habits/Tasks snapshot, then recent personal entries and the daily summary. Avoid repeating empty-state counts and introductory copy inside both the page and each card.
3. **Make cards content-led.** Use one clear title/value, short supporting text, and a named trailing action. Use tonal surfaces sparingly; use spacing and typography to separate sections instead of adding shadows, gradients, or decorative borders everywhere.
4. **Use a consistent type scale.** Page title, section title, content title, body, and metadata should have stable roles. Keep entry text comfortable to read and metadata secondary; do not shrink type to fit a dense row.
5. **Make habit and task actions unmistakable.** A row tap opens details; a separate 48 dp+ action logs or completes. On Today, show two scheduled habits if that density works, and make the remaining count discoverable with “View all (N)” or “+N more.” Keep pending and failed operations beside their actual item/date.
6. **Standardize form anatomy.** Use a compact date/time row, put the primary input early, keep optional context behind clear disclosures, attach validation to the relevant field, and keep Save reachable above the keyboard.
7. **Treat empty, success, and error as designed states.** Each empty view should have one sentence and one useful next action. Success should be brief and specific; errors should stay visible with a real retry. Avoid using color alone to convey any state.
8. **Retain adaptive behavior.** Let long labels wrap or stack, keep controls at accessible touch sizes, support EN/FR/FA and RTL, and constrain reading width on larger windows before considering multi-pane layouts.

This direction makes Luma calmer and more coherent without adding another tab, social layer, streak pressure, cloud dependency, or a new palette.

## Demo data entered

Five fictional entries were added to each requested item type on 21 October 2026. The People and Places rows also preserve the optional descriptions used in the forms. The app’s local emulator profile now contains these records; it was not reset. The pre-existing five habits were left in place, so the Habits section now has ten saved items.

### Mood — five check-ins

| Date | Score | Emotion / note |
|---|---:|---|
| 21 Oct | 4 / 5 | Calm — “A quiet start helped.” Linked to Sam Rivera and Riverside Path. |
| 21 Oct | 3 / 5 | Tired |
| 21 Oct | 5 / 5 | Excited |
| 21 Oct | 2 / 5 | Anxious |
| 21 Oct | 4 / 5 | Grateful |

### Journal — five entries

| Date | Title | Saved text |
|---|---|---|
| 21 Oct | Morning walk | The air felt fresh and I started the day with a clear head. |
| 21 Oct | A useful conversation | A short check in with a friend helped me think through the week. |
| 21 Oct | Trying a new recipe | A new dinner came together well and gave me an easy idea for later. |
| 21 Oct | Focus block | I saved a quiet hour and finished the task I kept postponing. |
| 21 Oct | Weekend plans | I made room for a walk, a book, and time with friends this weekend. |

### Memories — five highlights

| Date | Title | Saved description | Importance |
|---|---|---|---:|
| 21 Oct | First apartment | Warm light fell into the kitchen while we unpacked our first nights stuff. | 4 / 5 |
| 21 Oct | School play | My nerves eased as the first scene started and the audience smiled. | 5 / 5 |
| 21 Oct | Family picnic | We shared fruit under the trees and stayed outside until sunset. | 4 / 5 |
| 21 Oct | First concert | The room echoed with the opening notes and everyone sang along. | 5 / 5 |
| 21 Oct | Helping a neighbor | We carried the boxes upstairs and talked on the porch afterward. | 3 / 5 |

### Habits — five added trackers

| Name | Unit / target | Schedule |
|---|---|---|
| Drink water | glasses / 8 | Daily |
| Read | pages / 20 | Daily |
| Stretch | sessions / 1 | Weekdays |
| Walk outside | walks / 1 | Daily |
| Practice guitar | minutes / 15 | Tuesday, Thursday, Saturday |

One repetition was logged for Drink water. Review displayed one habit log for the period.

### Tasks — five items

| Title | Due date | Final state |
|---|---|---|
| Return library books | 22 Oct | Completed |
| Prepare weekly meal plan | None | Open |
| Refill desk supplies | None | Open |
| Send project notes | None | Open |
| Water the balcony plants | None | Open |

### People — five contacts

| Name | Optional context |
|---|---|
| Sam Rivera | Design teammate |
| Alex Morgan | Neighbor |
| Jamie Chen | Running group |
| Taylor Reed | Book club |
| Robin Singh | Former classmate |

### Places — five labels

| Name | Optional description |
|---|---|
| Riverside Path | Morning walking route |
| Northside Library | Reading and book returns |
| Cedar Corner Cafe | Quiet table near the window |
| Community Garden | Weekend volunteer spot |
| Studio Desk | Focused work space |

The single Review pass showed 15 saved entries, 1 habit log, a Mood average of 3.6 / 5 from 5 samples, and one linked person and place. The `walk` query returned one title match, Morning walk. Usage and Health Connect values were left off because neither provider had access; no imported measurements were fabricated.

## Verification limits

This was a light manual walkthrough, not a release acceptance run. It verified one ordinary route through each requested section and one save path for each form, using five realistic records per category to make the lists useful to inspect. No Gradle or automated test suite was run. Recovery, accessibility, permission, localization, and edge-case behavior remain outside this pass.
