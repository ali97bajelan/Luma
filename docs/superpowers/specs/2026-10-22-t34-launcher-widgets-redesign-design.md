# T34 — Tasks and Habits launcher widget redesign specification

Date: 2026-10-22  
Status: Implementation in progress; T34-A/B/C implemented (validation pending), T34-D pending. Documentation does not establish runtime acceptance.
Task: [T34 in UI_REDESIGN_TASKS](../../UI_REDESIGN_TASKS.md#t34--tasks-and-habits-launcher-widgets)  
Plan: [Implementation plan](../plans/2026-10-22-t34-launcher-widgets-redesign.md)

## Intent and scope

Make the existing Tasks and Habits home-screen widgets match the supplied cream/sage light and charcoal/sage dark references. Users must still be able to scan saved records, scroll the collection, open a record or full list, capture, refresh, complete a task and log a habit. This is a visual extension of the UI redesign, using existing saved values and mutation contracts.

This specification defines the base scope for the two newly supplied widget reference pairs; existing Today, Timeline and Review work retains its status. Source implementation and validation progress are tracked in the implementation plan and task index.

Keep the RemoteViews implementation. A palette-only change would miss the inset row cards and progress tracks; a Glance rewrite would expand the work without serving the reference. The selected approach extends existing XML layouts and measured rendering. T32-C continues to own in-app widget settings/configuration; T34 owns launcher presentation. Shared drawable/color consumers include compact states, picker previews and the legacy Quick Capture widget, whose behavior must be preserved.

## Reviewed references and precedence

Both PNGs were visually inspected and both HTML files read against the Kotlin/XML implementation. Read with the [shared brief](../../luma-redesign/README.md).

| Source | Observed composition |
| --- | --- |
| [widgets-light.png](../../luma-redesign/widgets-light.png), 564 × 1280 | Cream rounded shells, pale inset rows, green count/capture pills, Persian task names, Complete actions, habit count/target and tracks |
| [widgets-dark.png](../../luma-redesign/widgets-dark.png), 706 × 1600 | Charcoal shells, outlined darker rows, mint/sage actions, warm text, ember overdue state and muted scroll rail |
| [widgets-light.html](../../luma-redesign/widgets-light.html) | `#F4F4EB` shell, translucent white rows, `#CCE8DB` actions, Plus Jakarta Sans/Vazirmatn intent, 28 px outer/16 px row radii, 8 px tracks |
| [widgets-dark.html](../../luma-redesign/widgets-dark.html) | `#1b1c1b` shell, `#242624` rows, `#304d3c`/`#c9ead4` action pair, `#94b49f` sage and `#ffb59f` overdue text |

Product/data contracts take precedence over prototype scripts. Matching PNGs govern composition; static HTML supplies colors and roles. Widget-specific roles below override the main-screen palette only inside launcher RemoteViews. Screenshot pixels and launcher grid labels are not dp specifications.

The dark HTML retains a light-oriented toggle script with `isDarkMode = false`; its class edits do not reliably define the dark design. Both HTMLs draw a static scrollbar without a real per-widget scrolling collection, render 0/5 at 4%, and simulate completion/increments in the DOM. Do not copy those behaviors. “4 habits” with three visible examples illustrates overflow, not a fixed count. The Habits Capture tooltip says “Add habit”; retain the app's current Quick Capture action and accessible name instead.

## Current implementation and boundaries

- `widget/WidgetRenderer.kt` owns header measurement, theme selection, collection row binding, overflow, empty/error/feedback states and text measurement. Titles already navigate; Capture and Refresh already have separate targets.
- `widget/WidgetCollectionFactory.kt` pages records by stable identity and now advertises four habit view types (light/dark × inline/stacked); tasks use one.
- The task and habit collection layouts now use inset row-card surfaces. Habit rows include a theme-specific progress track; count text and mutation eligibility continue to use saved data and existing tokens.
- `widget/WidgetLayoutPolicy.kt` and `WidgetCompactRenderer.kt` determine capacity and fallback. Card and track geometry is included in row measurements; never force the screenshot's three visible rows.
- `core/domain/.../widget/WidgetModels.kt` already supplies `Habit.count`, `target`, `skipped`, and `actionToken`; there is no saved emoji/icon field. Do not infer icons from habit names or expand the schema.
- Provider XML retains 250 × 250 dp initial minimum, 180 × 160 dp resize minimum, 4 × 3 target cells and horizontal/vertical resizing.

Paths above are relative to `app/src/main/java/com/luma/lifeos/` or `app/src/main/`, except the domain path. No repository, query, token, worker, receiver, route, permission or persistence change is required.

## Global constraints

- Keep Kotlin, Android RemoteViews/XML, minSdk 28, compileSdk 35, targetSdk 35 and JDK 17; no Glance migration or new dependency.
- Work offline; no account, network font, CDN, telemetry, new permission or schema migration.
- Preserve System/Light/Dark and English/French/Persian, including app theme overrides independent of system appearance.
- Preserve widget IDs/configuration, stable record IDs, paging, saved date/zone, routes, pending-intent identity, mutation eligibility and loading/error/retry/undo behavior.
- Render saved values only; no reference records, emoji inferred from names, sample dates, wallpaper, dock, status/navigation bars or theme toggle.
- Every interactive target is at least 48 × 48 dp; distinct targets have at least 8 dp separation.
- Normal text contrast is at least 4.5:1; large text and essential non-text indicators are at least 3:1. Status also has text.

## Visual contract

### Roles and geometry

Use opaque resources so wallpaper does not alter legibility. Row and badge roles are new resources; retain existing semantic names for existing roles.

| Role | Light | Dark |
| --- | --- | --- |
| `widget_surface_{light,dark}` | `#F4F4EB` | `#1B1C1B` |
| `widget_row_surface_{light,dark}` | `#FCFCF9` | `#242624` |
| `widget_text_primary_{light,dark}` | `#171717` | `#EDE0D8` |
| `widget_text_secondary_{light,dark}` | `#525252` | `#D0C3BC` |
| `widget_action_utility_{light,dark}` | `#0D3823` | `#C9EAD4` |
| `widget_mutation_fill_{light,dark}` | `#CCE8DB` | `#304D3C` |
| `widget_mutation_fill_pressed_{light,dark}` | `#BBDCCB` | `#3C5E4B` |
| `widget_mutation_text_{light,dark}` | `#0D3823` | `#C9EAD4` |
| `widget_count_fill_{light,dark}` | `#DDF5E8` | `#304D3C` |
| `widget_overdue_{light,dark}` | `#92400E` | `#FFB59F` |
| `widget_outline_{light,dark}` (decorative) | `#DEDFD9` | `#332F2E` |
| `widget_progress_track_{light,dark}` | `#DEDFD9` | `#131413` |
| `widget_progress_fill_{light,dark}` | `#28735B` | `#94B49F` |

Retain existing error and focus roles if measured against their new actual backgrounds. Count text uses the utility foreground. Capture uses the mutation-fill pair; Refresh/list title remain quieter utility targets with visible pressed/focus feedback. No blur, gradients, pulsing overdue dot or CSS shadow is required. Decorative outlines are not the sole affordance for an action or focus.

Outer radius: 28 dp. Row radius: 16 dp with a 1 dp decorative outline. Shell padding: 12 dp; row padding: 8 dp on all sides; row gap: 8 dp. Mutation/Capture radius: 24 dp, with width expanding to fit the localized label. Count badge: fully rounded, 8 dp horizontal and 2 dp vertical padding. Preserve existing title placement and the separate measured overflow hint below the header; reproducing the screenshot's inline hint is unnecessary at narrow sizes.

Keep current widget text metrics: header 18 sp bold, row title 16 sp bold, metadata/count/action 14 sp and count badge/hint 12 sp. Use the existing platform sans and Persian shaping fallback; bundled app fonts are reference intent, not a launcher font migration. Keep rendering and `TextPaint` measurement consistent. Allow two title lines, full accessible names and wrapping metadata. Exact numerical values must remain readable; the full record remains reachable through the title target.

### Tasks

Each row is one inset card containing saved title, the existing localized due label/date and the separate Complete button. Keep date ordering and due/overdue classification against `snapshot.date`. Keep existing action-token eligibility; do not dim or remove the row until the saved outcome is rendered. Preserve success/Undo and failure/Retry feedback.

Update width measurement for shell inset, both row insets, the localized action width and its gap. Measured row height is `max(48, measured title + measured due label) + 16 dp` with the existing title line limit. This keeps both title and action hit areas inside the card. Overflow and compact fallback must use the same geometry.

### Habits

Keep the existing inline-versus-stacked title/count decision inside a vertical text block, and add an 8 dp horizontal track 4 dp below that title/count block. Place the separate +1 action beside the block. New track ID: `widget_habit_row_progress`; retain all existing title/count/action IDs and fill-in intents.

Use a native determinate `ProgressBar`, maximum 1000. Add the pure interface `WidgetLayoutPolicy.habitProgressPermille(count: Int, target: Int): Int`: return 0 when target <= 0; otherwise clamp count into 0..target and compute `(clampedCount.toLong() * 1000L / target).toInt()`. Thus 0/5 → 0, 1/5 → 200, 2/8 → 250 and reached/over-target → 1000. This is presentation only; do not clamp saved count text. Hide the track for skipped habits or invalid targets and retain textual state. Keep +1 eligibility exactly as currently implemented.

The track is decorative to accessibility because the existing row description already announces full title and count/state. It must not add a focus stop. In RTL, mirror track direction with the row layout direction. Bind progress on every row render/reapply to avoid recycled values; do not animate optimistic increments.

Use explicit light/dark inline and stacked row resources with identical view IDs and native progress drawables, selected by the existing app-theme resolver. This avoids relying on host night resources when the user overrides the app theme. Retain the two light layout names; add `widget_habit_row_dark.xml` and `widget_habit_row_inline_dark.xml`. Factory habit view-type count becomes four; task count remains one. Keep stable IDs unchanged and verify refresh/theme switches against a real collection host.

Measure text width after shell/row padding, action and gap. Row height is `max(48, measured title/count block + 4 + 8) + 16 dp`; title/count block is max of inline heights or sum of stacked heights. When no row fits, preserve compact navigation/recovery instead of clipping or shrinking controls. Keep bounded first-page measurement and on-demand paging.

### All states and companion surfaces

Count badges show the current total with existing localized plurals only when known, positive and measured to fit, including badge padding. Loading keeps counts unknown. Show “Scroll for more” only when overflow and usable collection height justify it; retain the real ListView scrollbar rather than drawing a fixed thumb.

Restyle setup, loading, empty library/scope, read failure, success/Undo, Retry, stale/conflict/missing/invalid states with the same semantic roles while preserving current actions. Reapply must clear prior rows, track state and feedback as appropriate. Compact states retain whole labels and recovery access. Picker previews use clearly fictional content and the new surfaces; legacy Quick Capture keeps its routes, placements and action labels. In-app settings/configuration remain under T32-C.

## Acceptance and evidence

| ID | Required evidence | Owner |
| --- | --- | --- |
| W34-1 | Distinct list/Capture/Refresh targets and positive known count badge fit both themes; loading has no fabricated count | T34-A |
| W34-2 | Inset task cards retain localized due/overdue text, full accessible title, Complete identity, saved feedback and Undo/Retry | T34-B |
| W34-3 | Exact progress math, zero/target/over-target/invalid/skipped states, inline/stacked rows, and four collection layout types work across theme refresh | T34-C |
| W34-4 | Added insets/track are included in measured bounds; no hidden action, clipped count, false scroll hint or unusable fallback | T34-A/B/C |
| W34-5 | Expanded and compact/picker/legacy surfaces use measured color pairs, retain state/actions, and support RTL/TalkBack | T34-D |

Renderer matrix: both kinds, light/dark; counts 0, 1, 2, 3, 4, 8, 10, 50, 500; 220 × 160, 373 × 321 and 373 × 420 dp at en/1.0. Exercise 180 × 160, 180 × 220 and 578 × 109 dp with long French/Persian at font scales 1.3 and 2.0, plus both app overrides against opposite system themes. These are widget bounds, not app-window sizes.

Use isolated fixtures for mutations. Real launcher evidence on API 28, 31 and 36 must include initial placement, resize/rotation, list scrolling beyond the first page, theme/locale refresh, Complete/+1/Undo/Retry, and 48 dp target/TalkBack checks. Offscreen `RemoteViews.apply` captures cannot establish launcher collection scrolling or action delivery. Distinguish renderer and launcher evidence. A missing device/check is NOT RUN and keeps acceptance open.

Write `docs/luma-redesign/evidence/<actual-run-date>/t34/acceptance.md` during execution with build SHA, APK identity, device/API/launcher, theme/system override, locale, font scale, bounds, fixture and screenshot references. Do not overwrite historical widget evidence. T34 completion requires all five acceptance rows; existing WG08 and T30–T33 blockers retain their status. Overall redesign release acceptance requires both the original T33 gate and T34 acceptance.
