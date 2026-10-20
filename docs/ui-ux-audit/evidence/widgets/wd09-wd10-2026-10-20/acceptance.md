# WD09–WD10 acceptance evidence

Captured 20 October 2026 on the isolated API 36 emulator. The before matrix was produced from source revision `e1d35d3`; the after matrix uses the current WD09/WD10 changes. Both APKs report version `1.0.1` (version code `2`). Fixtures are synthetic `WidgetSnapshot` values. No app database or personal records were read or changed.

## Device and capture method

The emulator is Google `sdk_gphone64_x86_64` (`emu64xa`), API 36, 420 dpi, `en-US`, font scale 1.0, light theme. Each run renders both widget providers at exact synthetic bounds of 220×160, 373×321 and 373×420 dp for totals 0, 1, 2, 3, 4, 8, 10, 50 and 500. The 54 image matrix includes long mixed-script titles, large habit counts, reached and skipped habits, and overdue tasks. A separate four-image matrix records Undo and Retry for both widgets at 373×321 dp.

Images come from `RemoteViews.apply` in the instrumentation test. The emulator's home-activity resolver was `com.android.settings`, and the fixture renderer did not attach widgets to a launcher host. These captures do not establish launcher-specific rendering. The recorded environment is in [before](baseline/environment.txt) and [after](after/environment.txt); matrix measurements and images are in [before fixtures](baseline/matrix/fixtures.csv) and [after fixtures](after/matrix/fixtures.csv), with feedback states in [before feedback](baseline/feedback/feedback.csv) and [after feedback](after/feedback/feedback.csv).

## Results

The before and after matrices have identical visible-row counts at each provider, total and bound: 0 rows in the 220×160 compact state; up to 3 at 373×321; up to 5 at 373×420. Undo retains 2 rows and Retry 1 row at 373×321 for both widgets in both runs. The current renderer still uses its existing eight-row candidate prefix; this evidence does not satisfy WD17 scrolling or reachability.

At 373 dp width and 420 dpi, each header gap is 21 px (8 dp). Before the change, title-to-Capture and Capture-to-Refresh gaps were both 0 px. Capture had a `TextView` value but its centered one-line layout was 1,048,576 px wide, leaving the label outside its target. Replacing horizontal single-line scrolling with `maxLines=1` makes the label paint in the resting view; a pixel assertion covers it. See a [final Tasks render](after/matrix/tasks-8-373x420-none-details.png).

The title cue now reports the eligible total (`8 tasks`, `50 habits`) rather than a viewport-derived “more not shown” count. Scope and dated successful freshness appear together above feedback and rows. The older-snapshot case includes both snapshot and update dates plus the tracking zone. The [older-snapshot acceptance test](../../../../../app/src/androidTest/java/com/luma/lifeos/widget/WidgetRendererAcceptanceTest.kt) is named `wd10GroupsScopeAndDatedFreshnessBeforeFeedbackAndRows`.

## Contrast checks

The Android `WidgetColorContrastAcceptanceTest` measures the semantic light/dark role colors and asserts at least 4.5:1 for text and 3:1 for focus indicators against the surface, mutation fill and pressed fill. Calculated ratios for those tested pairs are:

| Pair | Light | Dark |
| --- | ---: | ---: |
| Primary text / surface | 13.83:1 | 15.11:1 |
| Secondary text / surface | 7.70:1 | 9.13:1 |
| Utility text / surface | 7.06:1 | 14.10:1 |
| Mutation text / normal fill | 11.16:1 | 8.13:1 |
| Mutation text / pressed fill | 9.46:1 | 6.36:1 |
| Utility text / pressed fill | 4.83:1 | 5.83:1 |
| Overdue text / surface | 6.48:1 | 11.09:1 |
| Error text / surface | 7.60:1 | 10.64:1 |
| Focus ring / surface | 7.06:1 | 14.10:1 |
| Focus ring / normal fill | 5.70:1 | 7.45:1 |
| Focus ring / pressed fill | 4.83:1 | 5.83:1 |

Ratios establish the selected colors' contrast; they are not a manual launcher, TalkBack or keyboard-focus run.

## Verification

- `:app:assembleDebug`, `:app:assembleDebugAndroidTest` and `:app:testDebugUnitTest` passed; the JVM suite reports 262 tests.
- `WidgetRendererAcceptanceTest`: 23 tests passed, including the wide and stacked WD10 header checks, all nine en/fr/fa × 100/130/200% text-scale combinations, and a painted-pixel assertion for Capture.
- `WidgetColorContrastAcceptanceTest`: 1 test passed.
- `WidgetVisualBaselineTest`: 1 test passed; it captured 54 matrix images and 4 feedback images.

WD09 remains partial pending launcher-host capture and fixture-state coverage beyond the isolated matrix. WD10 remains partial pending launcher-host/assistive runs and the real scroll hint/collection supplied by WD17; its en/fr/fa 100/130/200% renderer-context matrix passes.
