# IP-I09 — Weekly Review reflection loop specification

**Priority** P2 · **Horizon** Later · **Status** SOURCE COMPLETE — QA PENDING

[Task index](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md) · [Proposal](../../20261118-Proposal.md) · [Implementation plan](../plans/2026-11-20-product-improvements-i09-weekly-reflection.md) · [Final emulator QA](../plans/2026-11-20-product-improvements-q01-emulator-qa.md#qa-09)

## Intent and current behavior

Help users interpret a review period and take one optional follow-up action.

WeeklyReviewRoute and ReviewOverview already expose equivalent elapsed comparisons, coverage and authorized sources through WeeklyReviewRepository. The app has existing Memory capture, Tasks/Habits destinations, and calendar/week-start display preferences.

## Scope

Place a short Review reflection section near period coverage. Show comparison ranges and available sample/coverage facts. Provide “What helped?” with Record a Memory, and “What would you change?” with separate Tasks and Habits actions. Apply this to Weekly Review when its repository is present. Keep the existing Memory Review fallback available. Show the proposed Memory capture date before navigation and keep the reviewed range visible as context.

Out of scope. This task excludes AI summaries, causal claims, new reflection entity, monthly dashboard or personal data upload.

## Requirements

- **IP-I09-R1** Label both compared ranges, including partial-week cutoff, with active week-start/calendar preferences. Surface available sample sizes/coverage with the existing metric states and distinguish sparse or unavailable data from observed zero. Protected source aggregates continue to apply the existing gates.
- **IP-I09-R2** Reflection actions are optional. Default new Memory capture from any review to the current civil date, visible in the action; the editor retains its existing date controls. Open Tasks on its normal list with a visible current-date context on the Review action. Open Habits at the explicitly shown reviewed through-date. Browsing historical review never silently changes a task due date or habit schedule.
- **IP-I09-R3** Use observational language without causal attribution. Keep existing source drill-down, retry, comparison semantics and privacy. A sparse review can expose prompts without creating synthetic values or implying poor performance.

## Implementation approach

Reuse existing review data and route callbacks. A new reflection entity adds storage, privacy and export obligations while Memory already supports the intended action. Use explicit today capture to distinguish occurrence date from the historically reviewed range.

## Acceptance

**QA-09** Use no data, sparse observed data, genuine zero and a dense partial week; switch week start and Gregorian/Persian. From a historical Review open source and Back, then save a Memory for the displayed date and open Tasks/Habits without date mutation. Recheck protected sources and observational copy.

**Product validation** In observed reviews, assess whether users can describe a useful observation or intended adjustment using the displayed evidence.

Apply the [shared implementation contract](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md#shared-implementation-contract). Runtime acceptance is owned by IP-Q01 after all handoffs.
