# IP-I07 — Habit dates, counts and schedule changes specification

**Priority** P2 · **Horizon** Later · **Status** SOURCE COMPLETE — QA PENDING

[Task index](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md) · [Proposal](../../20261118-Proposal.md) · [Implementation plan](../plans/2026-11-20-product-improvements-i07-habit-date-explanations.md) · [Final emulator QA](../plans/2026-11-20-product-improvements-q01-emulator-qa.md#qa-07)

## Intent and current behavior

Explain the civil date of a habit log and when a schedule edit changes future targets.

Habits are effective-dated and have a stored tracking zone. HabitEditorViewModel already computes an effectiveDate and the repository applies a schedule version. HabitHistory and the correction dialog retain dated log identity and recovery actions.

## Scope

Reuse a localized count-of-target presentation and show the actual affected date on in-app logging and correction. In schedule editing, show the existing effective date and earlier-history behavior when schedule fields change. Label the stored tracking zone when it differs from the device.

Out of scope. This task excludes streak rewards, history rewrite, habit rows in Calendar or changes to zone/DST eligibility rules.

## Requirements

- **IP-I07-R1** Each in-app habit logging/correction affordance identifies the affected civil date with the display calendar policy. A correction moving a log identifies the original and selected target dates. Show the actual tracking zone label when it differs. Preserve the launcher widget’s separate design contract and verify its existing behavior in QA.
- **IP-I07-R2** Show count of target for applicable days and keep unscheduled, paused, archived, partial and target-reached states distinct. Future dates remain subject to current logging restrictions.
- **IP-I07-R3** Schedule edits show the effective date used by the existing policy, including start date for a future habit and the next tracking-zone day for an active habit. Earlier history remains governed by previous versions. Name/unit-only edits do not imply a schedule change. Validate the displayed effective date in the write transaction. When the value changed across the tracking-zone day boundary, hold the edit, refresh the summary and require an explicit Save for the updated date.

## Implementation approach

Expose existing resolved date and schedule state through shared habit formatting. Recomputing schedule dates separately in composables risks a different day at midnight or after travel.

## Acceptance

**QA-07** With 2 of 3 logged, edit the target to 4 from a device zone on a different day, including a tracking-zone midnight and a DST edge. Verify the existing effective-date rule, unchanged earlier progress, dated correction and Undo. Check paused, unscheduled, archived and future-date controls.

**Product validation** Observe whether users can predict the effective date and correct a log while the device and tracking zones disagree.

Apply the [shared implementation contract](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md#shared-implementation-contract). Runtime acceptance is owned by IP-Q01 after all handoffs.
