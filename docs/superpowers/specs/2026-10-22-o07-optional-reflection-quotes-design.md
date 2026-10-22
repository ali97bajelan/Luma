# O07 — Optional — reflection shortcut and editorial quotes specification

Date: 2026-10-22  
Status: Optional / deferred; requires a separate scope decision before implementation.  
Depends on: T31 and T32-A

## Intent and sources

Offer a clearly defined reflection shortcut and static editorial footer without presenting invented personal insight.

References: today-light.html “Reflect”; Timeline quote footers. Read the [shared reference brief](../../luma-redesign/README.md) and [task index](../../UI_REDESIGN_TASKS.md) with this specification. The brief resolves inconsistencies and provides common tokens; it is normative for every task.

## Global constraints

- Native Android, Kotlin, Compose Material 3 and Navigation Compose; keep minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- Offline operation; no account, cloud sync, AI service, telemetry, new permissions or schema migration in required visual work.
- System/Light/Dark selection and English/French/Persian support remain available offline.
- Preserve existing route IDs, callbacks, saved state, data semantics, loading/error/retry/undo and accessibility tags unless an optional task explicitly changes its own contract.
- Use actual saved data; never ship reference people, dates, percentages, measurements, quotes or charts as user records.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL and system animation scale.
- Optional tasks default to deferred and are not required for redesign acceptance. No optional feature is authorized for implementation by this planning document.

## Screen and behavior contract

- Reflect opens the existing Journal editor using the same now/selected-date confirmation and draft behavior as Journal. It creates no record until Save and adds no new event type, AI service or automatic summary.
- If the fourth chip is included, label it “Reflect in journal” and preserve the existing Journal action so the difference is explicit. Treat this as an optional alternate entry point; omit it if a duplicate shortcut is not wanted.
- Use at most one bundled, translated, original editorial sentence per locale, identified as “Reflection prompt”. It is static editorial content, not attributed as the user’s own words or inferred from their records.
- Do not copy the reference quotation without confirmed reuse rights; do not invent source attribution. Footer remains secondary, accessible and clear of capture controls; it must not replace empty/error guidance.

## Implementation boundaries

- `app/src/main/java/com/luma/lifeos/ui/today/TodayCaptureActions.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/today/TodayScreen.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/timeline/TimelineRoute.kt` — existing integration surface.

Existing public/internal composable signatures remain compatible except additions explicitly named above. Existing repositories/view models own data and mutations; decorative components receive values and callbacks. New files named in the contract are proposed implementation files, not files already created by this documentation change. Consult the paired plan for test ownership.

## Acceptance scenarios

1. Shortcut respects selected-date capture rules and cancellation/draft restoration; it makes no silent write.
2. Quote/prompt works offline in all locales, is explicitly editorial and never claims a saved reflection or calculated cadence insight.

## Verification and completion

Use focused existing regression coverage plus visual/manual checks for the changed surfaces. Add tests for changed behavior and meaningful state/semantics risks; do not add tests that merely assert padding or duplicate token assignments. Required font/contrast/RTL/inset checks are acceptance work, not a reason to add fake data. Record PASS/FAIL/NOT RUN and evidence in the T33 ledger. No checks are claimed to have run in this specification.

[Implementation plan](../plans/2026-10-22-o07-optional-reflection-quotes.md)
