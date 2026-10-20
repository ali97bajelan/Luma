# Luma widgets: UX review and focused improvement plan

Reviewed: 20 October 2026. **WUX01–WUX04 source changes implemented; focused verification and acceptance are tracked below.** Scope: Android home-screen Habits, Tasks and Quick capture widgets, their placement flow and settings. Four changes are recommended, in delivery order below. Existing [WG08 release blockers](WIDGET_ACCEPTANCE_2026-10-20.md) remain open.

## Implementation status

The user approved implementation on 20 October 2026. The original findings and acceptance contracts below remain the review baseline. See [implementation and verification evidence](WIDGET_UX_IMPLEMENTATION_2026-10-20.md) for the delivered behavior and remaining acceptance.

| Task | Implementation | Acceptance |
| --- | --- | --- |
| WUX01 | Implemented: scrolling setup, safe action area, measured compact recovery, direct per-instance setup, task Retry labels | Focused configuration/renderer tests; broader live-launcher/privacy matrix remains open |
| WUX02 | Implemented: combined metadata, actual update date, header Refresh, hidden counts, 48dp navigation blocks, persistent adaptive feedback | Focused row/reapply/Undo tests; broad visual/accessibility comparison remains open |
| WUX03 | Implemented: three measured arrangements, no Open Luma tile, fit-based fallback | Fit-policy and localized large-text RemoteViews tests; live editor/draft/TalkBack matrix remains open |
| WUX04 | Implemented: fictional mode previews, readable localized picker previews, named Add actions, purpose/mode/ordinal instance names | Focused unsaved-preview tests; API 28 picker comparison and formative check remain open |

Implementation status does not close WG08 or establish release readiness.

## Recommendation

Keep the three widget types. Make Habits and Tasks feel like useful, compact lists; make Quick capture a dedicated three-action tool. Fix unreachable setup controls first. Remove duplicate dates, oversized utility-button treatments and Quick capture's **Open Luma** tile. Combine feedback and recovery into one compact, persistent area.

The current green/cream identity, readable labels, explicit habit **+1**, direct editor navigation and hidden-details default are good foundations. Preserve them. A combined Habits + Tasks widget would compete for the same limited row space and combine different date meanings: habit tracking time and task device dates. There is no user research demonstrating that this tradeoff would help. Improve the existing widgets before adding or replacing providers.

| Decision | Proposed outcome | Reason |
| --- | --- | --- |
| Habits | Keep; simplify shell and feedback | Progress and logging form one clear job. |
| Tasks | Keep; prioritize task titles, completion and Add task | Due work needs its own scope and recovery. |
| Quick capture | Keep; remove Open Luma tile; adapt three direct actions to space | Opening the app is already available through its icon. |
| Shared elements | Combine repeated date/freshness copy; consolidate feedback; reuse one visual system | Free space for useful content without combining data types. |
| Combined Today widget / extra variants | Defer | More picker choices and fewer rows per job are poor first investments. |

## Evidence and limits

Source reviewed at working-tree base `0ae71eb`: renderer, sizing policy, XML layouts/previews/provider metadata, English strings, configuration Activity, widget settings and existing specification/acceptance records. Findings below distinguish direct observations from source inspection and design judgments.

For fresh visual inspection, started `Medium_Phone_API_36.1` with `-read-only -no-snapshot-save`, installed the existing debug APK, and used Pixel Launcher. Android API 36; 1080×2400 px / 420 dpi; English; light theme. The APK SHA-256 was `53a972c4fa666f8c022484f4bca9abb3055ff9285fe4570a27ade97042a886f7`, matching the earlier acceptance record. It was not rebuilt for this review; these screenshots establish behavior of that APK, not a new source-build certification. The temporary emulator was shut down afterward.

| Evidence | What was actually observed |
| --- | --- |
| [Earlier populated Habits/Tasks launcher screenshot](ui-ux-audit/evidence/widgets/habits-tasks-undo-api36.png) | Duplicate dates; similarly emphasized controls; retained “Undo complete” area; relatively little space devoted to list content. This is earlier evidence, not a new action test. |
| [Fresh discovery screen](ui-ux-audit/evidence/widgets/ux-review-2026-10-20/discovery.png) | Three provider cards without visual examples. Intro and picker help mention only Habits/Tasks despite offering Quick capture. |
| [Quick capture pin preview](ui-ux-audit/evidence/widgets/ux-review-2026-10-20/capture-picker.png), [Habits pin preview](ui-ux-audit/evidence/widgets/ux-review-2026-10-20/habits-picker.png) | Placeholder bars replace readable example content. Habits preview depicts rows, while setup defaults to hiding details. |
| [Quick capture at 100%](ui-ux-audit/evidence/widgets/ux-review-2026-10-20/capture-home.png) | Four equally weighted tiles, including Open Luma. Mood opened a new Mood check-in editor; exited without saving. |
| [Quick capture at 200%](ui-ux-audit/evidence/widgets/ux-review-2026-10-20/capture-200-percent.png) | At the same placement, all three direct actions were replaced by Open quick capture. Source confirms the unconditional `fontScale >= 1.6f` fallback. |
| [Habits setup at 100%](ui-ux-audit/evidence/widgets/ux-review-2026-10-20/habits-setup.png), [portrait at 200%](ui-ux-audit/evidence/widgets/ux-review-2026-10-20/setup-portrait-200-percent.png) | Hidden details selected by default; Save/Cancel visible in these two portrait cases. No preview of the selected mode. |
| [Habits setup in landscape at 200%](ui-ux-audit/evidence/widgets/ux-review-2026-10-20/setup-landscape-200-percent.png) | Content cut off; Save/Cancel absent from the visible accessibility tree. No scrollable container. Required placement cannot be completed with the visible controls in this state. |

No fresh populated data-widget mutation tests, French/Persian checks, dark-mode checks or TalkBack speech tests were performed. Recovery findings are based on source branches, not injected live failures. Recommendations are expert design judgments; no usability study or adoption measurement has been completed.

## The four most important changes

| ID | Priority within this review | User outcome | Relative effort | Existing coverage |
| --- | --- | --- | --- | --- |
| WUX01 | First: usability blocker | Setup and recovery controls remain reachable | Medium | WG07/08; AC-08/10/11/12 |
| WUX02 | High: largest everyday benefit | More useful content, clearer hierarchy and compact Undo | Medium–large | WG05/06; AC-03/04/06/10 |
| WUX03 | High: protect capture speed | Three clear capture actions whenever they fit | Medium | WG01; AC-01/02/10/11 |
| WUX04 | High: make placement understandable | Preview matches the privacy choice and actual widget | Medium | WG07; AC-01/08/10/11 |

These priorities sequence this proposal; they do not replace the repository's release-priority definitions. Estimates describe scope, not delivery dates.

### WUX01 — Keep setup and recovery usable in constrained space

**Problem.** The landscape/200% setup failure is observed. `ConfigurationContent` uses a full-height, non-scrolling Column with Save/Cancel after the body. The same content implementation serves Tasks. Separately, `WidgetRenderer.renderAtSize()` sets Tasks' secondary button to Add task before entering the read-failure branch; that branch supplies no Refresh action. The setup-required message also retains section navigation instead of a direct configuration action. The row-sizing policy can reduce rows to zero, but does not make the fixed header/feedback/footer stack fit a short widget.

**Design contract.**

1. Put configuration text, privacy choice and preview in a scrolling body. Reserve a safe action area for Save/Cancel; stack actions if their translated labels do not fit. Keep both actions reachable with text scale, rotation and system insets. Save remains disabled during loading/saving; preserve existing cancellation and per-instance consent behavior.
2. Use explicit compact layouts when the normal widget shell cannot fit. Removing rows alone is insufficient. Reserve state text and the relevant recovery action before decorative spacing and optional secondary navigation. Never solve overflow by reducing text scale or touch targets.
3. Use state-specific actions:

| State | Visible information | Primary action | Secondary action when it fits |
| --- | --- | --- | --- |
| Setup required | Set up this widget | Set up → configuration for this live instance | Open Luma |
| Initial loading | Loading habits / Loading tasks; unknown count | Open section | — |
| Read failed, no safe snapshot | Couldn't update. Counts unavailable. | Refresh | Open habits / Open tasks |
| Stale action | This view is out of date. | Refresh | Open section |
| Failed mutation with retry token | Couldn't save this change. | Retry original action | Open section |
| Conflict / missing item | Item changed / Item unavailable | Open section to review | Dismiss |

4. Keep errors distinct from empty and zero states. Hide unsafe row actions after a failed read. If a safe previous snapshot is available, qualify it with its actual date/time; otherwise show unknown counts. Add task is useful in the normal Tasks state, but must not displace recovery after failure.
5. Correct task Retry accessibility wording: the current shared string says “Retry the previous habit action.” Name the appropriate habit/task action. Keep success, pending and error copy distinct.

**Implementation plan.** Change `WidgetConfigurationActivity.kt` body/action structure; add compact state variants to `widget_data.xml` or dedicated RemoteViews layouts; extend `WidgetLayoutPolicy.kt` to choose an entire shell as well as row count; update `WidgetRenderer.kt` state/action binding and `WidgetIntents.kt` for validated direct setup navigation. Add equivalent strings to `values`, `values-fr` and `values-fa`. Keep the existing transaction, command and Undo model.

**Acceptance.** Reproduce the captured landscape/200% case and verify Save/Cancel remain reachable. Verify both providers in portrait/landscape at 100/130/200%, including French and Persian. At 180×160 dp and the recorded short landscape bounds of approximately 578×109 dp, every state above has readable text and its primary action, without overlap. The live launcher must expose Refresh on Tasks read failure; Set up must open the correct instance; failed Retry must reuse its original token. Setup cancel must preserve the established first-placement/reconfiguration behavior.

### WUX02 — Give data and the next action visual priority

**Problem.** The populated evidence shows a title, full date, long summary, retained feedback, rows, a second full date/update line and two large filled footer buttons. Open, Refresh, Dismiss, +1 and Complete use similar filled shapes. A completed Undo result continues occupying a dedicated feedback row until dismissal. Source attaches navigation to the title TextView only, so a one-line title can have a target shorter than 48 dp even when the overall row is tall enough. Source also omits the specified hidden-row indication after selecting the visible prefix.

**Proposed composition.** This schematic shows hierarchy, not a promise that all rows fit every size. All example names are fictional.

```text
Habits                           [Refresh]
Oct 20 · 1 of 3 targets reached

Read                     0 / 1       [+1]
Walk                     Target reached

Updated 13:07
[View all habits · 1 more]
```

```text
Tasks                            [Refresh]
Oct 20 · 3 due today or overdue

Send invoice             [Complete]
Overdue · Oct 19
Book appointment         [Complete]
Due today

Updated 13:07
[View all tasks · 1 more]       [Add task]
```

**Detailed decisions.**

- Combine the date and scope/progress beneath the title where they fit; wrap naturally. Keep the habit tracking zone visible when it differs from the device zone. Do not relabel an old snapshot as Today or remove its date to make the layout shorter.
- Show the successful update time once, in a supporting style. Use “Updated 13:07” when its date matches the visible snapshot date; show the full update date/time when they differ. Derive freshness from the successful update instant, not from the tracked day. In compact variants it may share the scope line if the date and time remain readable. Leave manual Refresh available for both providers; use a small visual icon with a reserved 48×48 dp target and contextual accessible label.
- Keep 16sp names, 14sp supporting labels/actions and 12sp freshness text. Use the existing semantic light/dark palette. Make navigation tonal or plain rather than giving every control the same filled prominence; reserve stronger emphasis for +1, Complete and Add task. Maintain 48dp targets and visible pressed/focus states.
- Make the title/supporting-text block one navigation target at least 48dp high; keep the trailing mutation action separate. Never make the entire row completion-sensitive or overlap click areas. Keep +1 and Complete visibly explicit in this pass.
- Indicate omitted content through the existing View all action, such as “View all tasks · 4 more.” Compute hidden count from eligible rows minus actually visible rows. Reserve this action while measuring capacity; avoid introducing another footer line or a new list-order rule. Preserve existing habit ordering and overdue-first task scope/order.
- Replace the two-tier success panel with a compact persistent strip when space permits: `Task completed   [Undo]   [Dismiss]`, or `Repetition saved   [Undo]   [Dismiss]`. Each action retains a 48dp target. Stack the strip at narrow widths or larger text. Errors may use more space and take precedence over rows. Keep feedback independent of whether its row is still shown.
- After Undo, use the compact result “Undone” with Dismiss and no empty action band. Retain truthful results through refresh; do not time away an available Undo or hide failed recovery to keep the list looking tidy. A new accepted action continues to replace the previous result under the existing policy.

**Sizing plan.** Measure actual localized labels, rendered text weight, action widths and font padding. The current habit-row measurement assumes 64% text width; replace this with the width left after the actual +1 target and gap. Include summary padding and hidden-count/navigation space in the reserved height. Define compact, regular and expanded shells from measured fit, not launcher cell labels. Keep the three-row maximum; show fewer complete rows when feedback or long text requires it. A summary-only instance should use the compact composition without an artificial empty list area; let users resize down without changing consent.

**Implementation plan.** Update `widget_data.xml`, `widget_habit_row.xml`, `widget_task_row.xml`, `widget_feedback.xml`, semantic widget colors/drawables and renderer measurement/binding. Extend existing RemoteViews reapplication coverage to ensure switching layout/state clears obsolete children, actions and content descriptions. No storage migration or data-ranking change is required for this visual slice.

**Acceptance.** At the same bounds and font as the baseline, show at least the previous number of complete rows, while reducing repeated metadata and equal-weight utility buttons. Record before/after row count and screenshots rather than claiming a percentage improvement. Verify 0/1/3/>3 eligible items, two-line titles, reached/skipped habits, overdue tasks and a different habit zone. Complete a final visible task and reach a habit target: Undo must remain usable after the row disappears or changes state. Verify separate 48dp navigation/mutation targets and reading order on the actual launcher. At small bounds, WUX01's compact recovery contract takes precedence over row density.

### WUX03 — Make Quick capture a focused, adaptive three-action widget

**Problem.** Open Luma consumes one quarter of the current equal-tile layout without recording anything. At 200% text, a hard font-scale threshold removes all direct editor shortcuts even at placements that might fit a different arrangement. This adds a choice step for users who need larger text.

**Design contract.** Remove the Open Luma tile. Keep named Mood, Memory and Journal actions in a stable order. Use text first; small existing vector icons may reinforce meaning if they do not crowd the labels. Icons alone are insufficient.

```text
Wide:     [Mood]       [Memory]       [Journal]

Narrow:   [Mood]       [Memory]
          [       Journal      ]

Tall:     [Mood]
          [Memory]
          [Journal]
```

Choose the arrangement using the actual bounds and measured localized labels at the current text scale. Prefer the three direct actions whenever any arrangement fits with at least 48×48 dp targets and 8dp gaps. Only fall back to Open quick capture when none fits. Preserve the existing 3×2 initial placement for this first pass; validate a smaller default separately after the new arrangement is measured. Keep one provider rather than publishing extra sizes as separate widgets.

For a vertical layout, 24dp outer vertical padding + three 48dp minimum targets + two 8dp gaps already requires 184dp before additional text height. This is a fit calculation, not a universal breakpoint. A wide arrangement needs sufficient width for all three labels. At 200%, first try a suitable arrangement rather than rejecting all direct layouts because of font scale alone.

**Implementation plan.** Update `widget_quick_capture.xml`, compact/alternate layouts and `QuickCaptureWidgetProvider.kt`; replace `shouldUseCompactLayout()`'s unconditional font threshold with content-fit selection. Keep existing `WidgetDestination.Capture` routes, tap-time initialization and draft handling. Update capture previews after the layout settles.

**Acceptance.** At default and larger placements, test English/French/Persian and 100/130/200% text. Where three measured actions fit, all three open their matching new editor in one tap. Where they cannot fit, the single fallback remains readable and opens the existing capture chooser. Verify cold/warm launches, existing drafts, repeated taps, Cancel without saving, RTL action order and launcher TalkBack labels. No entry is saved by tapping the widget itself.

### WUX04 — Show what will be placed before asking users to commit

**Problem.** Picker previews are abstract bars, while the useful row actions require a separate opt-in. Configuration explains the difference in paragraphs without showing it. This makes the advertised tracking experience and the default summary appearance difficult to compare. The discovery intro/help omit Quick capture.

**Design contract.**

1. In configuration, retain the default-off **Show names and row actions** switch, shorten the summary explanation and show a fictional preview of its current state. Use “Summary — names hidden” and “Details — names and actions visible” as clear mode explanations. The preview must never show real records before consent.
2. Keep the fact that summary counts/date are visible explicit. State the launcher-caching limitation concisely near the choice, with fuller existing privacy help available. Do not hide the consequence of enabling details behind a help link.
3. Use readable fictional picker content that matches the default summary layout and chosen size. Show a detailed example inside configuration when the user selects details. Keep static preview support for older Android versions; do not upgrade dependencies solely to add newer preview APIs.
4. Keep provider cards, but add small accurate previews and concise descriptions. Include all three in the intro/picker instructions. Use “Add Habits widget”, “Add Tasks widget” and “Add Quick capture widget” as accessible action names rather than three indistinguishable Add actions.
5. Name existing instances by their purpose and mode, such as “Habits · Summary” or “Tasks · Details.” For duplicates, add a local ordinal (“Habits 2”); keep Android numeric widget IDs out of primary product copy. Quick capture needs no per-instance configuration, so explain that boundary rather than pretending it has editable settings.

**Implementation plan.** Update configuration preview/copy, `WidgetSettingsRoute.kt`, all three `widget_preview_*.xml` assets or equivalent compatible preview resources, provider descriptions and localized strings. Make the preview body scroll with WUX01; adding a preview must not push Save/Cancel offscreen. Keep successful pin requests distinct from confirmed launcher placement.

**Acceptance.** Before Save, users can identify whether names/actions will appear. Switching the control updates only the fictional preview until saving. First placement, cancel, reconfiguration and multiple instances preserve current privacy behavior; hiding details still revokes old row/Undo actions. Compare preview against the actual placed widget on API 28 and API 35/36. Verify all three discovery choices, unsupported-pinning instructions, pending/confirmed placement copy and French/Persian layout. In a short formative check, ask users to choose a capture shortcut and a widget that hides names; observe their choices without explaining the modes first.

## Delivery and validation plan

Deliver four reviewable slices: **WUX01 → WUX02 → WUX03 → WUX04**. Final previews depend on the data/capture layouts, so finish them last. The UX choices intentionally revise parts of the original v1 specification: the four-tile capture layout, full-size footer treatment and feedback presentation. Update those specific specification sections alongside implementation; preserve action integrity, privacy and date contracts.

| Slice | Files / verification focus | Exit condition |
| --- | --- | --- |
| 1 — Reachability and recovery | Configuration, compact state layouts, state navigation; targeted tests for landscape/large text and Tasks read failure | Required setup and primary recovery can be completed in constrained layouts. |
| 2 — Data widgets | Shared shell/rows/feedback and measurement; extend existing renderer reapply tests | Better hierarchy; no lost rows relative to baseline; exact Undo and safe recovery retained. |
| 3 — Capture | Three-action arrangements and routing; content-fit cases and cold/warm smoke | Direct actions remain available wherever measured content fits. |
| 4 — Discovery | Fictional previews, mode explanation, localized settings | Preview, consent choice and resulting placement agree. |

For each slice, record source revision/APK identity, device/API/launcher, widget bounds in dp, locale/theme/font, state, screenshot and actual result. Keep this review's screenshots as **before** evidence. Run appropriate app unit tests/build/lint plus focused widget instrumentation; repeat storage/action regressions when affected. A renderer test does not establish real launcher accessibility or visual fit.

Minimum visual scenarios for the changed UI: API 28 and API 35/36; Pixel Launcher plus a second launcher family; en/fr/fa; LTR/RTL; light/dark; 100/130/200% text; minimum/default/expanded sizes; portrait and short landscape. Cover summary/details, long names, empty/loading/read error, Saved/Undo/Undone, failed Retry and setup-required states. Use the existing WG08 matrix for the broader lifecycle, privacy, replay and performance gates; do not close them based on these visual improvements.

Suggested success checks are: setup completed without rotating back or reducing text; capture reaches the intended editor in one tap when it fits; no utility control obscures a primary action; users can explain what summary mode exposes; no lost Undo or duplicate mutation. These are future acceptance goals, not measured outcomes of this review.

## Platform basis

The proposed visual choices are Luma-specific judgments. Platform constraints were checked against current official guidance:

- Use actual available sizes and compatible responsive layouts; cell dimensions vary by launcher. See [Android flexible widget layouts](https://developer.android.com/develop/ui/views/appwidgets/layouts).
- Reserve 48×48dp touch targets, with room between adjacent actions. See [Android touch target guidance](https://support.google.com/accessibility/android/answer/7101858?hl=en).
- Make previews reflect the real appearance, size and function, and avoid multiple providers whose only difference is presentation. See [Android widget discovery and promotion](https://developer.android.com/design/ui/mobile/guides/widgets/discovery-promotion?hl=en).
