# UI, localization and accessibility

All cases **NOT RUN**. These cases inspect existing UI; they do not authorize a redesign. Use [fixtures](../fixtures.md) G, then each module's own editor fixtures. All record changes used for inspection are discarded unless a case explicitly says Save.

Acceptance source: repository AGENTS.md, [tokens](../../../app/src/main/java/com/luma/lifeos/ui/common/LumaUiTokens.kt), [components](../../../app/src/main/java/com/luma/lifeos/ui/common/LumaUiComponents.kt), [scaffold](../../../app/src/main/java/com/luma/lifeos/ui/common/AppPageScaffold.kt), [theme directory](../../../app/src/main/java/com/luma/lifeos/ui/theme), [current consistency contract](../../luma-redesign/ui-consistency-checklist.md). Use warm-paper/obsidian roles; historical green palette is not an oracle. Timer rings, charts, calendar markers, photo crop overlays retain intentional specialized presentation. Widgets use their separate contract.

## Configuration tasks

Set app language/theme in More > Settings; set system region, font/display size, orientation and navigation mode in Android Settings. For exact dp width/font 2.0 use a disposable emulator with that configuration, not a guessed pixel width. Record actual widthDp/heightDp/fontScale. A configuration unavailable on a device is BLOCKED, not passed.

| Matrix ID | Language / direction | Appearance | Window / font | Required surfaces |
| --- | --- | --- | --- | --- |
| U1 | English / LTR | Light | 411 × 891 dp / 1.0 | Every route and modal in coverage matrix |
| U2 | English / LTR | Dark | same / 1.0 | All five primary tabs, every editor family, Settings, details, photo viewer |
| U3 | French / LTR | Light | 320 dp / 2.0 | Primary nav, Calendar grid/filter, Review period/menu, all editor footers, Search filters, backup/lock dialogs |
| U4 | Persian / RTL | Dark | 320 dp / 2.0 | Same surfaces as U3 plus context/time pickers, task hierarchy, photos and Focus |
| U5 | Persian / RTL | Light | 411 dp / 1.0 | All five tabs, catalog, Search, settings, widget configuration |
| U6 | French / LTR | Dark | 411 dp / 1.0 | Capture editors, Review, Calendar, catalog, privacy/recovery |
| U7 | English / LTR | System | 411 dp / 1.0 | Toggle Android light→dark→light on Today, editor, sheet and Focus |
| U8 | Persian / RTL | System | 840 dp / 2.0 | Today, Calendar, Review, task/capture editors, picker/dialog, photo viewer |
| U9 | English / LTR | Light | 840 dp and portrait→landscape / 1.0 | Five tabs, editor with keyboard open, Search results and Focus |

For each required surface run UI-001..UI-006 where applicable and record a separate result keyed by case ID + matrix ID + surface. This parameterization reuses identical inspection actions, not one blanket pass for the whole app. Detailed feature behavior still comes from the owning scenario file.

## Surface execution steps

## UI-001 — Theme and shared design consistency

Priority: **P1** · Status: **NOT RUN** · Setup: **G; each required matrix surface**.

1. Open the surface; capture top and final scroll positions → text and actions readable on correct Light/Dark surfaces, no mismatched feature palette.
2. Open its modal/picker if available → background, typography, shapes and action treatment match shared components.
3. Change appearance through System where U7 applies → current draft/selection stays intact and surface recolors without unreadable text.

Persisted oracle: Preferences only; no saved G fields/counts changed. Record matrix ID, screen tag and screenshots before/after.

## UI-002 — Narrow window and font 2.0 reachability

Priority: **P1** · Status: **NOT RUN** · Setup: **G; U3/U4/U8**.

1. Open each required surface and scroll slowly from header to final action → no overlapping/trapped fields; meaningful labels wrap or are fully available to accessibility.
2. Open keyboard on last editor field; scroll to Save and Cancel → both remain reachable above keyboard/system bar.
3. On Calendar inspect all seven weekday/day columns; on Review inspect period/section controls → selected labels readable and actions operable; crop/grid targets must not silently become too small.

Persisted oracle: No record saved. Capture screenshot with keyboard and final action visible; discard inspection drafts.

## UI-003 — Localization and mixed direction

Priority: **P1** · Status: **NOT RUN** · Setup: **G plus string samples; U3–U6/U8**.

1. Set required language offline, reopen surface → all app-owned labels/errors/dialogs use that language; no resource keys or format placeholders.
2. In a supported title field enter QA سلام 123 Café 😀 → visual ordering, caret, selection and deletion remain usable.
3. Open date/time picker → numbers/date labels follow actual locale/calendar setting; changing display locale does not change stored ISO due date/instant.
4. Cancel draft → no sample record saved.

Persisted oracle: G unchanged; display choice only. Report untranslated strings with resource/screen and screenshot.

## UI-004 — Touch targets and distinguishable actions

Priority: **P1** · Status: **NOT RUN** · Setup: **G; required surfaces**.

1. Use Android Studio Layout Inspector/Compose semantics bounds to measure each actionable control in dp → at least 48 dp touch target, including icons, chips and repeated row actions.
2. Tap row body then its checkbox/delete/overflow individually → separate intended actions, no accidental parent navigation.
3. Inspect selected/unselected and error/disabled states → distinguishable beyond color alone where meaning requires it.

Persisted oracle: Browsing and canceled actions do not change data; restore any checkbox mutation immediately and verify same ID.

## UI-005 — Insets, scrolling and FAB clearance

Priority: **P1** · Status: **NOT RUN** · Setup: **G; gesture and three-button navigation**.

1. Open each required scrollable surface; scroll to final actionable row → no status/navigation bar clipping.
2. On Timeline/Review/Calendar, scroll to last action with capture/Schedule FAB present → FAB does not cover target; action remains tappable.
3. Open editor keyboard; rotate, then dismiss keyboard → footer and content return without doubled/absent insets.

Persisted oracle: No writes; selected date/draft remains coherent. Capture bounds/video of any covered target.

## UI-006 — Feedback and modal dismissal

Priority: **P1** · Status: **NOT RUN** · Setup: **each feature's valid/invalid fixture; required surfaces**.

1. Trigger the feature's documented empty/validation/error case → explanatory text with a useful action; no permanent spinner or generic empty success.
2. Open confirmation/picker, press Back → only top modal dismisses and unsaved selection follows that feature's Cancel contract.
3. Run its save/delete/undo case → feedback corresponds to actual persisted result; retry is reachable; transient feedback is not the sole way to recover an error.

Persisted oracle: Use owning feature case's exact oracle and cleanup; do not invent failures by disconnecting internet for local database writes.

## UI-007 — TalkBack end-to-end editing

Priority: **P1** · Status: **NOT RUN** · Setup: **G; TalkBack on**.

1. From Today swipe forward through header/navigation/capture → focus order logical; icons have meaningful names; selected tab/state announced.
2. Open QA due today using accessibility actions; edit title to QA spoken task, Save → field label/error/action announcements usable.
3. Reopen same task then restore title QA due today and Save; open delete confirmation and Cancel → focus returns to initiating control.

Persisted oracle: Still 5 tasks; original title restored, due/status unchanged. Record spoken output and focused bounds for failures; disable TalkBack afterward.

## UI-008 — Keyboard and switch-style navigation

Priority: **P2** · Status: **NOT RUN** · Setup: **G; external keyboard or emulator hardware input**.

1. Use Tab/Shift+Tab through Tasks, Search filter dialog and one capture editor → visible focus, logical order, no trap.
2. Use Enter/Space on focused actionable control → one action only; Escape/Back dismisses current modal.
3. Enter sample mixed-direction text and navigate away/cancel → input is not lost by accidental focus activation.

Persisted oracle: No saved changes; document unsupported input behavior separately from confirmed failures.

## UI-009 — Configuration change preserves validation and draft

Priority: **P1** · Status: **NOT RUN** · Setup: **G; U9 then U4**.

1. Open G-T1 editor; replace title with QA rotation draft and note with QA retained; do not Save.
2. Rotate portrait↔landscape or resize narrow↔840 dp → exact text/date remains; keyboard and footer operable.
3. Change app language/theme if flow permits → draft remains or an explicit discard confirmation protects it; return/cancel preserving original saved G-T1.

Persisted oracle: Original saved task title/due unchanged until explicit Save. Record any silent loss as a defect.

## UI-010 — Offline fonts and image security regression

Priority: **P1** · Status: **NOT RUN** · Setup: **G plus capture photo fixture; airplane mode**.

1. Cold open Today/Review and a Persian capture editor → bundled fonts render with readable Persian shaping, no font-network dependency.
2. Open photo viewer, zoom and crop overlay → image controls usable in both themes and RTL; image/crop geometry preserved.
3. Enable protection using LOCK scenario and repeat screenshot/recent-app check → enforce its specific screenshot policy, not an assumption that every route must always block screenshots.

Persisted oracle: No record mutations apart from the explicit lock test setup; restore lock, network and locale baseline.

