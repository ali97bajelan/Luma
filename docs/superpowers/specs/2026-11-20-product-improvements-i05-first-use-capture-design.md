# IP-I05 — First use and capture terminology specification

**Priority** P1 · **Horizon** Next · **Status** PENDING

[Task index](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md) · [Proposal](../../20261118-Proposal.md) · [Implementation plan](../plans/2026-11-20-product-improvements-i05-first-use-capture.md) · [Final emulator QA](../plans/2026-11-20-product-improvements-q01-emulator-qa.md#qa-05)

## Intent and current behavior

Help first-time users identify the right composer and save an entry using only required inputs.

QuickCaptureSheet already maps capture to existing Mood/Memory/Task flows. In reachable navigation, the legacy Journal creation path resolves to Memory. Mood and Memory are separate models with existing optional sections, validation and localization.

## Scope

Add short outcome-oriented chooser copy for Mood, Memory and Task. Give relevant first-use empty states a direct capture action. Keep required inputs and Save visible and use existing disclosure affordances where optional details already compete, exposing selected options and restoring disclosure when addressing a field error.

Out of scope. This task excludes mandatory tutorial, onboarding permission prompts, revived Journal composer or historical data reclassification.

## Requirements

- **IP-I05-R1** People can tell the difference among recording how they feel, saving something worth keeping, and recording something to do. Use the Memory label for reachable creation, in English, French and Persian. Empty states route to the same existing composer's dated entry path.
- **IP-I05-R2** Mood saves with a selected score and the existing valid timestamp default. Direct Memory saves with a nonblank description and the existing timestamp default; title, importance, tags, people, place and photos remain optional. Keep optional selections discoverable, accessible and intact across collapse, retry and draft restoration.
- **IP-I05-R3** Legacy Journal entries preserve their identity, content and supported open/edit/delete behavior. In promoted Memory flows, distinguish removing the marker from deleting the source. Keep provider restrictions and the selected date explicit.

## Implementation approach

Update the existing chooser and empty states, and use existing editor disclosures where the organization is already supported. A new onboarding route adds steps before first value and creates another state restoration path. Broader progressive disclosure depends on observed overload.

## Acceptance

**QA-05** From empty first-launch data, enter Mood, Memory and Task via the chooser in EN/FR/FA. Save minimum Mood/Memory inputs, reopen a draft with optional context and navigate a seeded legacy Journal. Verify remove-marker retains the source and captured date matches the chosen date.

**Product validation** Observe correct unassisted composer choice and time to first saved entry in each language.

Apply the [shared implementation contract](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md#shared-implementation-contract). Runtime acceptance is owned by IP-Q01 after all handoffs.
