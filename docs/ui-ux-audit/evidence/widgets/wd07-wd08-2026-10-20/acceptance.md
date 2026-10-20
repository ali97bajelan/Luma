# WD07–WD08 focused acceptance — 20 October 2026

## Device and build

- API 36 emulator (`sdk_gphone64_x86_64`), Pixel Launcher, 1080×2400 px at 420 dpi.
- System locale `en-US`, font scale 1.0, light mode, portrait.
- Installed `com.luma.lifeos` 1.0.1 / version code 2 with an in-place `adb install -r`; app data was preserved.
- Before and after the update, Pixel Launcher widget IDs 8 (Habits) and 9 (Tasks) remained bound to their providers. The home-screen screenshot and accessibility hierarchy are retained here.

## WD07 — legacy capture transition

- The in-app Settings route promotes Habits and Tasks, omits Quick capture as an addable provider card, and explains how to remove an old capture placement manually. `WidgetSettingsRouteTest` passes 1/1.
- Quick capture remains registered. The Pixel Launcher picker shows **Luma Quick capture (legacy)** and the compatibility description; see [the picker screenshot](legacy-picker-api36.png).
- The provider’s Mood, Memory and Journal destinations still decode to their editor routes. The provider registration and route test passes 3/3.
- The update preserved the existing mixed Habits/Tasks placements. Their visible state is in [the post-update screenshot](mixed-widgets-api36-after.png), and their clickable nodes and bounds are in [the post-update accessibility hierarchy](mixed-widgets-api36-after.xml).
- No standalone Quick capture instance was pinned on this emulator before the update. The system picker entry and direct routes are verified, but an already-pinned capture-only instance has not yet been exercised through an upgrade.

## WD08 — measured renderer evidence

The API 36 `WidgetRendererAcceptanceTest` passes 19/19. The row-count probe covers 54 combinations: six eligible counts, three bounds, and no feedback / Undo feedback / Retry feedback. The recorded bound is 373×321dp, compact is 220×160dp, and expanded is 373×420dp. Each fixture includes a long mixed-script first title; ten eligible items provide the greater-than-eight case.

Visible rows at each surface are reported as **none / Undo / Retry**:

| Eligible items | Compact 220×160dp | Recorded 373×321dp | Expanded 373×420dp |
| ---: | ---: | ---: | ---: |
| 0 | 0 / 0 / 0 | 0 / 0 / 0 | 0 / 0 / 0 |
| 1 | 0 / 0 / 0 | 1 / 1 / 1 | 1 / 1 / 1 |
| 3 | 0 / 0 / 0 | 3 / 2 / 1 | 3 / 3 / 3 |
| 4 | 0 / 0 / 0 | 3 / 2 / 1 | 4 / 4 / 3 |
| 8 | 0 / 0 / 0 | 3 / 2 / 1 | 5 / 4 / 3 |
| 10 (>8 candidates) | 0 / 0 / 0 | 3 / 2 / 1 | 5 / 4 / 3 |

The compact shell keeps recovery actions ahead of rows. All rendered rows in the probe are complete and stay inside the row container. A separate 600×600dp renderer case displays all eight fetched candidates for Tasks and at least four for Habits. Raw output is in [the row-count log](row-counts-api36.txt) and the renderer test result is in [the test log](widget-renderer-api36.txt).

The capture renderer test passes across English, French and Persian at 100%, 130% and 200%, at 276×210, 350×240, 578×109 and 220×400dp. It verifies complete action targets, unbroken word boundaries, unclipped labels, and the accessible single-action fallback where needed. The same renderer suite checks summary/details, empty, loading, failure, Undo/Retry, long row titles and separate 48dp data-widget actions.

At the live Pixel Launcher density of 420 dpi, the accessibility hierarchy reports 126 px (48dp) height for the Habits title, Capture, Refresh, +1 and Tasks Complete targets. Capture is 194×126 px, Refresh and +1 are 126×126 px, and Complete is 201×126 px. Adjacent action bounds are separate; see the [parsed target bounds](target-bounds-api36.txt). These are measured host bounds; spoken TalkBack output and keyboard traversal were not tested.

Focused results: widget renderer 19/19, widget Settings 1/1, widget intents/provider 3/3, and widget store 3/3. The complete app JVM suite passes 262/262; `lintDebug`, debug APK assembly and Android-test APK assembly pass. Gradle output is retained in the [app gate log](app-gates-api36.txt), [final rebuild and unit-test log](final-build-tests.txt), and [final lint log](final-lint-api36.txt), with individual device test logs beside them.

## Remaining acceptance

- Identical-fixture before/after launcher screenshots are not available. The retained screenshots show the old review baseline and the current post-update mixed placement, but the underlying counts differ; they are not presented as a controlled comparison.
- API 28 and 35, a second launcher, actual landscape host rendering, duplicate instances of the same provider, and live system font/theme/locale changes remain untested.
- Manual TalkBack speech/focus and keyboard traversal remain untested. The accessibility XML verifies labels, focusable nodes and measured bounds only.
- The emulator had no pinned standalone Quick capture widget before the update, so capture-only placement survival across that update remains open.

WD07 implementation is in place, with the standalone placement upgrade check open. WD08 remains a partial acceptance record and a release gate.
