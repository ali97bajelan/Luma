# T32 coordinator source review — 2026-10-22

Status: **blocked**. This source review reconciles route ownership for the T33 handoff; the later connected-test failures and remaining provider/rendered checks keep coordinator acceptance open. See the [latest validation recheck](t33-revalidation.md).

Follow-up validation (2026-10-22): T32-A and T32-B focused tests pass, including the EventCard viewport checks and provider-absent Review fallback cases. T30, T31, T32-C and T32-D still have full-suite failures; rendered/accessibility acceptance remains open.

Reviewed source revision: `6f6723341f723ff9c77c49704e5f8efc3e113f18` (`main`). The inventory covers the primary destinations in `AppDestinations.kt`, conditional and unconditional route declarations in `LifeOsApp.kt`, the Quick Capture sheet, significant dialogs and pickers, and the manifest-registered widget configuration activity. Details and planned capture paths are in the [route coverage ledger](../../route-coverage.md).

## Review results

- Every current app destination and conditional route declaration has an owner and provider condition in the ledger. The launcher RemoteViews widgets remain explicitly out of T32 route acceptance and link to their existing evidence.
- The shared `EventCard` remains owned by T32-A. Today, Review, catalog history and Search are listed as consumers under their route owners; no duplicate card implementation or conflicting ownership was found in the source records.
- The full connected run reports failures in T30, T31, T32-C and T32-D. T32-A/B focused tests pass; source implementation evidence remains distinct from visual/runtime acceptance.
- Provider-absent `review_sources` and `review_usage` now render localized unavailable states with Back actions and pass focused instrumentation. The remaining locale/theme/window and accessibility matrix is still NOT RUN.

## Checks not run

No Gradle task, Gradle test, emulator or device was run for this coordinator review, as requested. Route behavior, provider-state behavior, screenshots, Light/Dark/System appearance, English/French/Persian, RTL, window/font-scale and accessibility checks remain **NOT RUN**.

## Handoff

T32 coordination is blocked pending child failure isolation and acceptance. T33 remains blocked until the provider-absent Review routes are resolved or accepted with useful states and the required child behavior/rendered evidence is recorded. The latest validation run is documented in [t33-revalidation.md](t33-revalidation.md).
