# App-wide visual redesign tasks

Approved 22 October 2026. Use the supplied Today screenshot for the light
appearance and the dark-screen overview below for the dark appearance. Redesign
both themes across the app while retaining the theme selector and existing
product behavior.

## Light screens overview

These are visual and content-hierarchy references. Example people, titles,
dates and measurements must come from saved data in the finished app. Do not
hard-code sample records or present metrics the app does not calculate.

### Habits and daily tasks — Today

- Show scheduled Water (`2 of 8`) and Neck Mobility (`1 of 5`) with smooth
  progress bars, clear status tags, tactile `+1` actions and the timezone badge
  (example: Asia/Tehran).
- Separate overdue, today and undated task cards, with clear urgency badges and
  clean checkboxes. Example Persian titles are `متن قرارداد`, `تستتتت`,
  `تسک سوم` and `تسک بدون زمان`.
- Include a Health Connect activity card (example: 36 steps) and a shortcut to
  the saved daily summary.

### Timeline and activity log

- Use a continuous chronological stream with icon badges for time-anchored habit
  completions. Examples: 13:59 Neck Mobility, 13:59 Water and 09:38 Morning Water.
- Pair editorial journal cards (example: “Afternoon quiet in the courtyard”)
  with available Health Connect step counts and a prominent Quick Capture action.

### Weekly review and analytics

- Show the active week and previous-period comparison (example: October 19–22
  versus October 12–15, 2026).
- Group the review into a week-at-a-glance summary, saved usage/Health Connect
  signals with mini charts, and people/place comparisons. Example values are
  four entries, 13 habit logs, 12,639 steps and 16h 41m screen time.
- Treat “100% streak” as illustrative only. Show a streak metric only if its
  calculation and source data are defined and implemented; otherwise use the
  existing habit/review metrics and mood check-in prompt.

### People, places and settings — More

- Present People and Places as a searchable segmented directory, with a `+ New
  person` action and example cards for Farshad, Maede and Tehran Metro.
- Keep System/Light/Dark theme choices, language selection, widget settings,
  on-device backup/restore and local privacy controls easy to find. Preserve
  English, Persian/RTL and French support.
- Explain that records stay on device and cloud sync is not provided. Make
  manual backup/export behavior clear, including that ZIP backups are unencrypted.

### Light design system

- Palette: warm paper surface (`#fff8f4`), terracotta ember (`#ba4d28`) and soft
  sage (`#526350`), mapped to Material 3 roles and checked for accessible contrast.
- Use rounded containers and expressive pill chips through shared shape tokens,
  Material 3 floating actions and Newsreader editorial serif headings. Keep data,
  controls and localized Persian text clear and readable.

## Dark screens overview

These are visual and content-hierarchy references. Values and records shown in
the finished app come from the user's saved data; example values below must not
be hard-coded as real records.

### Habits and daily tasks — Today

- Use a deep obsidian canvas (`#131413`) with elevated surfaces (`#1b1c1b`,
  `#262726`), sage mint (`#94b49f`) and terracotta ember (`#ff8c69`) for progress
  and action emphasis.
- Make Water progress easy to scan (`2 of 8 glasses`) and Neck Mobility
  (`1 of 5 sessions`), with tactile `+1` actions and an active timezone pill.
  The supplied example is Asia/Tehran.
- Group tasks into overdue, today and undated sections. Keep overdue badges,
  checkboxes and Persian task titles legible in dark surfaces. Example titles are
  `متن قرارداد`, `تستتتت` and `تسک سوم`.

### Timeline and activity log

- Use a chronological dark stream with clear event nodes for hydration,
  habit completions and Health Connect activity records, including midnight
  cadence baselines.
- Give journal entries an editorial card treatment that can accommodate a photo
  thumbnail and location metadata, with a visible Quick Capture action. The
  supplied example title is “Afternoon quiet in the courtyard.”

### Weekly review and analytics

- Show the selected review period and period-over-period changes with distinct,
  readable delta badges. The supplied period example is October 19–22, 2026.
- Use high-contrast charts for cadence, screen-time and activity data. Example
  values include 12,639 steps, 16h 41m of screen time and Tehran Metro transit;
  render actual saved metrics and unavailable states in the app.

### People, places and settings — More

- Use elevated directory cards for people and places, with search and filter
  controls where those features exist. The supplied examples are Farshad, Maede
  and Tehran Metro.
- Keep theme and language selection, on-device backup/export and local privacy
  controls easy to find. Show the selected Dark theme state clearly and the
  supported languages, including English, فارسی/RTL and French.
- Use local-data/privacy indicators that reflect the documented safeguards;
  don't imply hardware-backed encryption or encrypted backups.
- The supplied export example lists Markdown and ZIP. ZIP backup is supported;
  Markdown export is not currently implemented and must not appear as an active
  option unless it receives a separate implementation task.

### Dark design system

- Palette: obsidian background (`#131413`), elevated containers (`#1b1c1b`,
  `#262726`), terracotta glow (`#ff8c69`), sage mint (`#94b49f`) and warm cream
  typography (`#ede0d8`), mapped through Material 3 roles with verified contrast.
- Typography: editorial Newsreader serif headlines with a clean sans-serif for
  body copy, controls and data metrics. Bundle fonts for offline use and provide
  a suitable fallback for Persian/RTL text.

Status: pending, in progress, implemented (validation pending), or complete.
A task is complete only after its acceptance criteria have been verified.

## T30 — Shared visual system for light and dark themes

Status: pending

Scope: Material 3 color roles, type hierarchy, spacing, shapes and reusable
surface/component treatments for both screen overviews. Define purposeful light
and dark palettes, including warm paper (`#fff8f4`), light terracotta (`#ba4d28`),
light sage (`#526350`), obsidian, elevated charcoal, dark terracotta, dark sage
and warm cream colors. Preserve the existing System/Light/Dark setting. Use
bundled Newsreader serif headlines and sans-serif data typography with a
Persian/RTL-compatible fallback.

Acceptance: shared tokens and components express both references consistently
without screen-local substitute palettes. Text, icons, states, focus and disabled
controls remain legible and differentiated in both themes. Fonts work offline and
Persian/RTL content remains readable. The redesign does not change data,
navigation or feature behavior.

## T31 — Today screen redesign

Status: pending

Depends on: T30.

Scope: rebuild Today’s visual hierarchy around both references: date navigation
and tracking-zone context, quick capture actions, scheduled habit progress,
overdue/today/undated task groups, recent entries, Health Connect activity and
the saved daily summary. Use distinct habit progress colors and legible localized
task badges and checkboxes in both themes. Keep the existing navigation and entry
points, adapting them to Luma’s Material 3 components.

Acceptance: current and selected dates, habit logging/progress, task completion
and editing, capture shortcuts, recent-entry navigation and summary details retain
their current behavior. Empty, loading and error states use the same visual system.

## T32 — Redesign remaining app screens and flows

Status: pending

Depends on: T30.

Scope: apply the shared light and dark systems to all remaining user-facing
routes, including Timeline, Habits, Review, More, Settings, catalogs, capture/edit
forms, details, dialogs and sheets. Give Timeline a readable chronological stream
and editorial journal cards; give Review clear period comparisons and grouped
usage, health and people/place metrics; and give More searchable people/place
directories plus clear theme, language, widget, export and privacy controls.
Preserve each screen’s content hierarchy, navigation, actions and
privacy/integration explanations.

Acceptance: no user-facing route is left with an unrelated legacy palette or
component treatment. Existing actions, drafts, selected dates, access states and
content remain available; charts and activity cards reflect saved data and retain
unavailable states. Do not display a streak or other metric that the app does not
calculate. The redesign introduces no new permissions or data requirements.

## T33 — Visual redesign accessibility and release acceptance

Status: pending

Depends on: T31 and T32.

Scope: verify the redesigned routes in light and dark themes, English, French and
Persian/RTL, compact and expanded widths, and larger system font scales. Capture
dated emulator evidence and run relevant UI/accessibility regression checks.

Acceptance: layouts remain usable without clipped or obscured content, interactive
targets and contrast meet accessibility requirements, RTL order and localized text
remain correct, and all existing navigation and feature flows pass. Record any
unavailable device checks as NOT RUN rather than implying acceptance.
