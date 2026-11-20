# IP-I05 — First use and capture terminology Implementation Plan

> **For agentic workers** Use the applicable Superpowers implementation skill when this plan is authorized for execution. Steps use checkbox syntax for tracking.

**Goal** Help first-time users identify the right composer and save an entry using only required inputs.

**Architecture** Update the existing chooser and empty states, and use existing editor disclosures where the organization is already supported. A new onboarding route adds steps before first value and creates another state restoration path. Broader progressive disclosure depends on observed overload.

**Tech stack** Kotlin, Jetpack Compose, Luma components and existing route/state/repository contracts.

**Spec** [IP-I05 specification](../specs/2026-11-20-product-improvements-i05-first-use-capture-design.md) · [Task index](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md) · [IP-Q01 scenario](./2026-11-20-product-improvements-q01-emulator-qa.md#qa-05)

## Files and interfaces

Paths are from the project root. Unqualified sibling filenames share the preceding directory.

- `app/src/main/java/com/luma/lifeos/ui/QuickCaptureSheet.kt + app/src/main/java/com/luma/lifeos/ui/today/TodayCaptureActions.kt` — Chooser and contextual labels with current capture callbacks.
- `app/src/main/java/com/luma/lifeos/ui/memory/MemoryLibraryRoute.kt + MemoryEditorScreen.kt` — Memory empty state, required controls and remove-marker copy.
- `app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorScreen.kt` — Visible minimum fields, context disclosure and draft recovery.
- `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt` — Review route aliases and date handoff; preserve legacy event identities and restricted routing.
- Update string resources and plurals in `app/src/main/res/values/`, `values-fr/` and `values-fa/` for affected copy.

Use existing QuickCaptureSheet capture callbacks and LifeOsApp routing. Preserve existing editor/route signatures where possible and use the IP-I03 draft behavior.

## Global constraints

IP-I03 editor state and discard contract. Apply the [shared implementation contract](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md#shared-implementation-contract). Record source handoff before IP-Q01.

## Review focus

Optional selections may be collapsed at the time of restoration or validation failure. Keep the selected values and an accessible route to edit them.

## Implementation checklist

- [ ] **1.** List reachable chooser labels and first-use empty actions. Add localized outcome descriptions and use the existing date-aware capture callback.
- [ ] **2.** Keep validation and Save flow intact. Make optional details discoverable via existing disclosures, including visible selection summaries and forced reveal of a validation target when required.
- [ ] **3.** Check terminology at legacy aliases and promoted Memory confirmations. Keep legacy IDs, editor targets, and remove-marker behavior intact.
- [ ] **4.** Review source and localization against IP-I05-R1, IP-I05-R2, IP-I05-R3. Record the changed files, interface decisions, risks and source evidence for IP-Q01.

## QA handoff

- [ ] Map `chooserRoutesToExistingComposerAndDate`, `minimumInputsSave`, `unmarkPreservesLegacySource` to [QA-05](./2026-11-20-product-improvements-q01-emulator-qa.md#qa-05) for the final verification task. Extend the existing test surfaces where appropriate. Existing Mood/Memory editor and navigation tests.
- [ ] Record source status as **SOURCE COMPLETE — QA PENDING** after handoff and leave the QA result **NOT RUN** until IP-Q01 executes.

**Acceptance evidence** From empty first-launch data, enter Mood, Memory and Task via the chooser in EN/FR/FA. Save minimum Mood/Memory inputs, reopen a draft with optional context and navigate a seeded legacy Journal. Verify remove-marker retains the source and captured date matches the chosen date.
