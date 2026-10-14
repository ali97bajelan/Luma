# UX 14 and 16: accessible feedback and understandable capture

Status: proposed design, ready for review. Baseline `391e7a4`. Related: [handoff](../../UX_DEVELOPMENT_READY_2026-10-14.md), [plan](../plans/2026-10-14-ux-14-16-feedback-capture.md).

## Intent and scope

Make capture sections discoverable before expansion and make validation, failures and important updates discoverable near their field/action and through assistive technology. Finish editor/Privacy heading semantics and retain existing Review section semantics. Keep capture data, save eligibility, protected drafts, provider/reset boundaries and navigation unchanged.

The existing date/time controls already sit outside disclosures. Review section headings are implemented. The rejected-pending-tag save guard is implemented. These are regression constraints, not work to repeat.

## Brainstormed decisions

Use descriptive disclosures with the current grouping rather than splitting each editor into several new sections. This preserves restoration and avoids adding more taps. Use field-specific error identities rather than inferring which field failed from a generic TEXT enum. Use Compose semantics and polite live regions rather than direct accessibility speech calls; render feedback from state and let the platform announce it.

## Capture disclosure behavior and copy

| Editor | Collapsed action | Expanded action | One-line helper under action |
| --- | --- | --- | --- |
| Mood | Note and context | Hide note and context | Add a note, energy, stress, people or a place. |
| Journal | People, place and photos | Hide people, place and photos | Link people or a place, or attach photos. |
| Memory, direct or promoted | People, place and photos | Hide people, place and photos | Link people or a place, or attach photos. |

Mood has no photo control: do not advertise photos there. Mood score/emotions stay visible; note/energy/stress/context remain in the disclosure. Journal title/body/tags and memory title/note/importance stay visible. Date/time stays outside every disclosure; promoted memories retain their original noneditable timestamp.

Reuse the existing editor resource keys `mood_more_details`, `mood_fewer_details`, `capture_more_details`, `capture_fewer_details` with the copy above. Add `mood_context_help` and `capture_context_help`. Provide English, Persian and French translations in the same change. Preserve `mood_more`, `journal_more`, `memory_more`, remembered expansion state and current initial-expansion policy. Add localized `stateDescription` using the existing expanded/collapsed resources. Expanding/collapsing never clears input or links. A photo-import failure expands its capture context so the local error/retry action can be reached.

## Headings

Apply `semantics { heading() }` to the existing screen title and section title nodes; keep labels, descriptions and individual scale values as ordinary text.

- Shared editor title in `EditorTopBar.kt`.
- Capture timestamp in `MoodTimeControls.kt`.
- Mood question, emotions, energy and stress headings.
- Journal Tags; memory Original entry and Importance.
- People and place selector headings and the Photos editor heading.
- Privacy screen title; Local storage, Optional access, Backup and Delete local data headings. The shared `PrivacyPolicyContent` also serves the Health Connect rationale.
- Review overview heading; retain the already implemented Mood, Memories, People/Places, Usage and Activity heading semantics.

Person/place fields use their existing labels; do not manufacture section headings for two-field forms. Disclosure actions remain buttons with expanded state, not duplicate focusable headings.

## Field-associated validation

Track text-limit rejection by field in editor state, alongside the existing operational error enum:

| Feature | Field enum | State property | Limit |
| --- | --- | --- | --- |
| Journal | `JournalTextField { TITLE, BODY, TAG }` | `textErrors: Set<JournalTextField>` | title 200; body 50,000; tag 50; tags 50 |
| Memory | `MemoryTextField { TITLE, NOTE }` | `textErrors: Set<MemoryTextField>` | title 200; note 10,000 |
| Person | `PersonTextField { NAME, NOTE }` | `textErrors: Set<PersonTextField>` | name 200; note 10,000 |
| Place | `PlaceTextField { NAME, NOTE }` | `textErrors: Set<PlaceTextField>` | name 200; note 10,000 |

All sets default empty, making existing state constructors source compatible. Each ViewModel persists its enum names as an `ArrayList<String>` in its own SavedStateHandle key `text_error_fields`; restoration ignores unknown names. Rejected text is not persisted as draft content. Keep the last accepted value and mark only the triggering field. Clear a field flag when a different within-limit value is accepted for that field. Repeated callbacks with the retained same value and edits to unrelated fields do not clear it. Successful tag acceptance clears TAG even when the tag already exists. Keep existing generic enum TEXT for compatibility, but do not show its aggregate banner when specific field feedback is present.

Each invalid field exposes `isError = true`, localized supporting text and `semantics { error(message) }` on its own field node. Supporting error text has a polite live region only while present. Journal's local pending-tag length/count error combines with the ViewModel TAG flag; it retains unsaved input and the existing blocked-Save behavior. Correcting/retrying the tag must not clear TITLE/BODY flags.

Use `editor_text_limit_error`: `Use at most %1$s characters. Your previous text has been kept.` Format the limit with the selected locale before passing it. Reuse the existing tag-specific limit message. Save behavior for the currently accepted title/body/note remains as today; this unit does not add a new save prohibition for a previously rejected paste. Empty required fields continue to govern Save enablement as before.

TIME feedback belongs to the timestamp controls and open date/time dialog, using the existing localized time error. Put `error(...)` on both date/time action nodes and render one local polite message. No error text is duplicated in the global banner. Invalid date ranges in Search appear beside the From/Through rows in its dialog and near the active date chips on the route, with error semantics on the endpoint controls. Keep the current suppression of invalid queries.

For an error triggered while its control is outside the viewport, bring that field/group into view using the existing BringIntoViewRequester pattern. Do not steal keyboard or accessibility focus just to announce status. Save failures have a visible correction/retry action near Save; do not force the user to scroll to the form's top.

## Operational feedback and update announcements

Create `ui/common/AccessibleFeedback.kt`:

`@Composable fun AccessibleFeedback(message: String, modifier: Modifier = Modifier, isError: Boolean = true)` renders the visible message with `liveRegion = LiveRegionMode.Polite`; error messages additionally set `error(message)`. It has no side effects, timer or direct speech API. Callers supply already localized text and control whether a message is present. Do not clear semantics on interactive descendants or merge whole forms into one node.

| Situation | Visible location and behavior |
| --- | --- |
| Editor LOAD | Body error and existing Retry; restored draft stays visible |
| Editor SAVE / DELETE / REMOVE | Feedback above the existing fixed bottom action row, with action/retry behavior unchanged |
| Journal/memory PHOTO import failure | `PhotoEditor` near Add/Retry inside expanded context; preserve attachments and file leases |
| Photo preview unavailable | Beside the unavailable preview and Retry; retain per-photo identity |
| People/place picker error or selection limit | Beside the existing picker Retry/limit text; no interruption for each selected chip |
| Search load/options failure | Beside the existing Retry; preserve chips/query. Do not conflate catalog loading with a deleted selection |
| Search results | Polite result-count update only for a completed current query; no announcements for every keystroke, spinner or stale generation |
| Review report/source/day-usage failure | Beside existing Retry, using the shared feedback message; retain selected period/day |
| Usage/health access or import outcome | Apply polite semantics to the existing settled status/error text; keep request/access controls and retention explanations intact |
| Privacy settings launch missing/reset failure | Message adjacent to the relevant action; preserve actual failure retry behavior |
| Privacy reset request accepted | Announce existing `privacy_reset_requested` politely. Never announce verified deletion: platform acceptance does not prove erasure |

Only one live region owns an error at a given visible layer. Date/time dialog feedback owns the announcement while open; its background duplicate is not a live region. Field supporting text owns validation; suppress corresponding generic banner. Keep alert-dialog semantics from Material rather than layering duplicate pane announcements. No assertive live region is needed. Existing statuses that are not important updates remain plain text.

## Accessibility basis

The semantics design follows Android's [heading, error, state and live-region guidance](https://developer.android.com/develop/ui/compose/accessibility/semantics). Use the APIs supported by the pinned Compose BOM; no accessibility dependency upgrade is required. Accessibility services determine the final spoken output, so inspect merged and unmerged nodes and verify speech on a device.

## Acceptance and verification

- **A14.1:** Heading navigation reaches the named editor/Privacy/Review sections without treating each label as a heading.
- **A14.2:** Each length rejection shows its own message, keeps the accepted draft and remains discoverable when revisiting that field. Two simultaneous rejections remain independently represented.
- **A14.3:** Same-value callbacks and unrelated edits do not erase feedback; accepted correction clears only its field. Activity recreation retains draft and field flags.
- **A14.4:** TIME, pending-tag and PHOTO failures have local feedback and correction actions. Rejected pending tags still block Save and retain their input.
- **A14.5:** Save/delete/remove failures stay visible near actions; settled results/import/reset outcomes expose polite semantics without repeated chatter or false deletion claims.
- **A14.6:** Privacy/permission/reset behavior is unchanged; fake reset tests prove no extra calls or confirmation bypass.
- **C16.1:** All three capture forms identify the contents before expansion and expose expanded/collapsed state.
- **C16.2:** A new user can find mood notes, journal/memory photos and date/time controls; collapsing and recreating do not lose draft content.
- **C16.3:** English/French/Persian, RTL, a docked keyboard, 320 dp width and font scale 2.0 keep feedback and controls reachable.

Use ViewModel tests for rejection identity/restoration and Compose semantics tests for headings/errors/live regions. Use fake repositories/reset in failure tests. Manual TalkBack must validate speech, heading traversal, focus stability, retries and disclosure state; semantics assertions alone do not close A14.
