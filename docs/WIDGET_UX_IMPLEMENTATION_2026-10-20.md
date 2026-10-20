# Widget UX implementation and verification — 20 October 2026

WUX01–WUX04 are **implemented**. Focused automated checks pass. Full launcher/device/accessibility acceptance is **partial**, and **WG08 remains blocked / release no-go**. This record supplements the [approved UX review](WIDGET_UX_REVIEW_2026-10-20.md) and preserves the earlier [WG08 acceptance record](WIDGET_ACCEPTANCE_2026-10-20.md).

## Delivered changes

| Task | Source status | Delivered behavior | Acceptance still open |
| --- | --- | --- | --- |
| WUX01 | Complete | Configuration body scrolls; safe Save/Cancel area wraps. Measured compact shells reserve primary recovery at every height. Direct setup validates the live instance. Tasks read failure offers Refresh, unsafe rows disappear, Retry keeps its token and names the task action. Loading has unknown counts and section navigation. | Live-instance setup/cancel/reconfiguration and injected failure on real launchers; full device/locale/theme/accessibility matrix |
| WUX02 | Complete | Combined date/scope, one actual-success freshness label, header Refresh for both providers, hidden-row View all count, separate 48dp navigation blocks, measured mutation widths/bold titles, compact persistent Saved/Undo/Dismiss and Undone feedback. Reapplication clears old rows and feedback children. | Broad populated before/after launcher comparisons, actual reading/focus order, lifecycle/privacy regressions |
| WUX03 | Complete | Mood/Memory/Journal in stable order; measured wide/grid/tall arrangements; no Open Luma tile; chooser only when no direct arrangement fits. Existing routes, drafts and editor initialization are preserved. | New live-launcher cold/warm editor/draft/repeated-tap checks, fallback chooser and TalkBack |
| WUX04 | Complete | Fictional unsaved-choice previews, explicit summary/details explanations, localized readable static/Android 12+ picker previews, discovery examples and named Add actions, purpose/mode/local-ordinal instance labels. Quick capture explains that it needs no instance settings. | API 28 and second-launcher preview comparison, pin/reconfiguration/privacy checks, formative usability check |

No storage migration, command lifetime, ordering or editor-save behavior changed. The original widget specification now reflects the revised visual contracts.

## Verification environment and identity

- Base commit: `0ae71ebf008de9c4501cde0b5fb874395ea1d86f`; delivered source is the uncommitted working tree. Relevant source/resource digests: [source.sha256](ui-ux-audit/evidence/widgets/ux-implementation-2026-10-20/source.sha256).
- Debug APK SHA-256: `740624077356c792c650a826ba160b33b95504a6e9105bae8b73433a932f6dfe`.
- Test APK SHA-256: `8a9721ab274b4d40c1737d23b30b42ea8182d958c683a576926edbe96a91c3a9`.
- Temporary `Medium_Phone_API_36.1`, Android API 36, 1080×2400 px / 420 dpi, light theme. Started with `-read-only -no-snapshot-save`; no app-data clearing. The emulator was shut down after verification.
- Configuration screenshots exercise the production `ConfigurationContent` inside the Compose test Activity. Data screenshots apply the real RemoteViews renderer to fictional snapshots inside instrumentation. **Neither establishes a new Pixel Launcher interaction pass.** The original review's launcher screenshots remain before evidence.

## Observed results

| Check | Actual result | Evidence |
| --- | --- | --- |
| `bash gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline` | Passed; 259 app JVM tests, zero failures/errors; debug and test APKs packaged; lint 0 errors / 115 warnings | [Build log](ui-ux-audit/evidence/widgets/ux-implementation-2026-10-20/build.log), [unit summary](ui-ux-audit/evidence/widgets/ux-implementation-2026-10-20/unit-test-summary.txt), [lint report](ui-ux-audit/evidence/widgets/ux-implementation-2026-10-20/lint.txt) |
| Focused renderer/configuration instrumentation, English, landscape at 200% device text | 10/10 passed | [English log](ui-ux-audit/evidence/widgets/ux-implementation-2026-10-20/instrumentation-en-landscape-200.log) |
| Same focused suite, French | 10/10 passed | [French log](ui-ux-audit/evidence/widgets/ux-implementation-2026-10-20/instrumentation-fr-landscape-200.log) |
| Same focused suite, Persian / RTL | 10/10 passed | [Persian log](ui-ux-audit/evidence/widgets/ux-implementation-2026-10-20/instrumentation-fa-landscape-200.log) |
| Additional configuration suite, English, portrait / 100% | 2/2 passed | [Portrait log](ui-ux-audit/evidence/widgets/ux-implementation-2026-10-20/instrumentation-en-portrait-100.log), [screenshot](ui-ux-audit/evidence/widgets/ux-implementation-2026-10-20/config-en-portrait-100.png) |
| Controlled task-row comparison, English / 100% / 250×250dp | Previous regression asserted 1 complete task row; current renderer retains 1. No broad density percentage claimed. | [Fictional renderer image](ui-ux-audit/evidence/widgets/ux-implementation-2026-10-20/renderer-task-en-250x250.png) |
| Shared setup body, landscape / 200%, en/fr/fa | Save and Cancel remain displayed; Save disabled during loading; unsaved detail choice changes only fictional preview | [English](ui-ux-audit/evidence/widgets/ux-implementation-2026-10-20/config-en-landscape-200.png), [French](ui-ux-audit/evidence/widgets/ux-implementation-2026-10-20/config-fr-landscape-200.png), [Persian](ui-ux-audit/evidence/widgets/ux-implementation-2026-10-20/config-fa-landscape-200.png) |

Run focused tests directly after installing both APKs:

```bash
adb shell am instrument -w -r \
  -e class com.luma.lifeos.widget.WidgetRendererAcceptanceTest,com.luma.lifeos.widget.WidgetConfigurationContentTest \
  com.luma.lifeos.test/androidx.test.runner.AndroidJUnitRunner
```

The renderer tests measure native views at **180×160, 578×109 and 180×220dp**, at **100/130/200%** text in each app locale. They exercise setup, read failure, loading, normal summaries and every retained outcome, check message visibility and primary control bounds/minimum height, and include a long tracking-zone identifier. Capture checks exercise all three localized direct actions at 200% with wide/narrow/tall bounds. Other regressions cover task read-failure Refresh, task Retry wording, old-row removal with retained Undo, Undone without an empty action band, omitted count beyond the snapshot prefix and the actual update date.

## Defects found and corrected during implementation

- Initial compact vertical content could squeeze the primary target below 48dp. The shell now reserves its action area and measures full/short metadata variants.
- Fresh review found a fixed 220dp recovery cutoff. State shells now use measured fit at every height, with a 180×220dp regression.
- Fresh review found capture-grid measurement budgeting unequal row heights while XML divides space equally. The policy now reserves both equal-height rows; the unequal-label regression fails before the fix and passes after it.
- French compact section labels consumed recovery space. Compact Open uses a short visible label and a contextual accessibility description.
- Persian native layout exceeded the initial Latin-based line measurement. Measurement now uses active text locales, Android fallback-font line spacing and matching line-break behavior. The constrained text-visibility regression failed before the fix and passes afterward.

## Decisions and limits

At the tightest bounds, snapshot date/zone and primary recovery take priority over optional counts, freshness and secondary navigation. The compact normal shell may use the tracking UTC offset instead of a long IANA identifier; the regular shell retains full metadata. This is documented in the updated specification. It avoids shrinking text or targets, at the cost of less metadata until the user resizes or opens the section.

The tests do not certify launcher speech, real click-target ownership, live widget IDs, pin confirmations, cold/warm editor behavior, prior drafts, token replay/races, restore/remap, performance or a usability study. Dark mode, API 28, API 35 and a second launcher family were not newly exercised. Earlier storage/full-app acceptance limitations remain unchanged. Do not close WG08 from these source and focused-renderer results.
