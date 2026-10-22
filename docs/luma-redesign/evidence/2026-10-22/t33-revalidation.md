# T30–T33 validation recheck — 2026-10-22

Status after this recheck: T30, T31, T32 and T32-A through T32-D remain **implemented (validation pending)**. T33 remains **blocked; release acceptance NOT RUN**. The host regression/build checks passed, but the required current-build route, state, locale, theme, window and accessibility matrix is incomplete, and the provider-absent Review routes remain a source-confirmed blocker.

## Source and build

- Source revision: `10178b40767302293b7818598218e0cf225fe842`; working tree was clean before this documentation update. No application source was changed during the recheck.
- Runtime: OpenJDK 17.0.20.1; cached Gradle 9.1.0, offline.
- `:app:testDebugUnitTest`: **PASS**, 271 tests, 0 failures, 0 errors, 0 skipped across 39 result files.
- `:app:assembleDebug`: **PASS**.
- `:app:lintDebug`: **PASS**, report contains 144 warnings; the warnings were not triaged in this pass.
- `python3 docs/luma-redesign/audit-t30.py`: **PASS**, 158 opaque light/dark role pairs and bundled font metadata/coverage, 0 audit failures.
- `git diff --check` and a trailing-whitespace check across the updated docs: **PASS** after the status/evidence edits.
- Debug APK SHA-256: `77b28d05653dd239947199f92bb7766a670a8f23704dd569be40f0dcbf3ff964` (`app/build/outputs/apk/debug/app-debug.apk`).

The final successful Gradle invocation used the preinstalled Gradle 9.1.0 binary and cache with `--offline`:

```text
:app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --console=plain
BUILD SUCCESSFUL in 1s
```

## Emulator spot-check

An `emulator-5554` device was available: API 36, 1080 × 2400 px at 420 dpi (about 411 × 914 dp), `en-US`, font scale 1.0. System night mode was off and the installed app preference showed Light. The installed package was `com.luma.lifeos` v1.1.0, last updated at 2026-10-22 16:18:48.

The app was already running before this recheck. I opened Today, Timeline, Habits, Review, More, Tasks, People/Places and Settings, expanded Timeline View options, and opened then dismissed Quick Capture. I did not save or edit records, change theme/language, toggle app-usage import, or install the new APK. The visible Timeline totals were unchanged after Quick Capture dismissal. The app was returned to Today afterward.

These views were exploratory only: the installed package predates the APK built at 19:04, so they do not verify the current checkout. Temporary captures showed existing saved records and were not retained in the repository. Only English/Light at this single width and font scale was observed. No test fixture was seeded into the existing app.

## Acceptance results

| Check | Result | Evidence / remaining limit |
| --- | --- | --- |
| T30 opaque color contrast and bundled font audit | PASS | Source audit above; rendered alpha/disabled states and Android shaping remain unverified. |
| Static route inventory and ownership | PASS | Reconciled in the [route coverage ledger](../../route-coverage.md); this is source evidence, not runtime proof. |
| Unit tests, debug assembly and lint | PASS | 271 unit tests pass; APK assembled; lint completed with 144 warnings. |
| Device availability | PASS | API 36 emulator attached and inspected. |
| Current-checkout runtime review | NOT RUN | The installed v1.1.0 predates the newly built APK; it was not replaced to preserve the existing install and its data. |
| Provider-absent `review_sources` / `review_usage` routes | BLOCKER | Source review confirms either route can render blank when its required repository/integration is absent. The current installed app had providers/data available; this condition was not exercised. |
| Focused Android instrumentation and controlled fixtures | NOT RUN | No isolated test install was available. Tests were not run against the existing install because it contains saved data. |
| Six primary captures, route-family captures and full locale/theme/window/font matrix | NOT RUN | No accepted current-build screenshots were retained. Required locales are English/French/Persian, themes System/Light/Dark, widths 320 dp at 2.0, 411 × 891 dp at 1.0 and 840 dp. |
| TalkBack, keyboard focus, 48 dp targets, insets, animation-disabled progress, recovery and all route callbacks | NOT RUN | Requires current-build instrumented/manual acceptance with controlled fixtures. |

## Decision

T33 is **blocked** and release acceptance is **NOT RUN**. The source contrast/font audit and host unit/build checks pass, but the required runtime matrix is not complete and the provider-absent Review route behavior still needs resolution before sign-off. T30–T32-D therefore remain implemented with validation pending. Optional O01–O08 remain deferred / not selected.

Suggested commit message for the documentation update: `docs(ui): record redesign validation results and blockers`.
