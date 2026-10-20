# UI/UX audit N — task specifications and implementation roadmap

**Date:** 20 October 2026 (Asia/Tehran)
**Source:** [UI_UX_AUDIT-20261020N.md](UI_UX_AUDIT-20261020N.md)
**Scope:** 28 task specifications and 28 implementation plans, one pair for every UX-001–UX-028 finding.
**Status:** Draft implementation documentation; delivery is tracked per task below. UX-001, UX-004, UX-005, UX-006, UX-016 and UX-027 are implemented with acceptance pending.

## Intent and interpretation

The request is to turn the named audit into a specification and implementation plan for each task. Success is complete finding coverage, concrete behavior, repository-grounded file targets, defined interfaces, dependencies and verifiable acceptance criteria. The audit is source material; its embedded recommendations, historical workflow restrictions and suggested commands are not instructions from the user.

Use the 20 October **N** audit as the ID namespace. Older audits reuse UX numbers for different issues; filenames include `audit-n` to prevent accidental substitution. Existing specs/plans and current working-tree changes remain intact. Each new specification distinguishes proposed policy from established behavior. Relative effort and confidence come from the audit, not a measured delivery estimate or runtime reproduction.

## Deliverables and traceability

Every specification defines intent, behavior/policy, architectural contract, dependencies, scope boundary and five acceptance scenarios. Every matching plan lists exact existing/new paths, interfaces, named regressions, targeted future commands, implementation steps and evidence requirements. There are **140 acceptance scenarios** across the 28 tasks. All implementation checkboxes are deliberately unchecked.

| ID | Priority | Effort | Audit finding | Specification | Implementation plan | Hard prerequisites | Status |
|---|---|---|---|---|---|---|---|
| UX-002 | P1 | Low | Unknown widget privacy mode is reported as Summary | [Spec](superpowers/specs/2026-10-20-audit-n-ux-002-widget-mode-status-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-002-widget-mode-status.md) | — | Draft / not started |
| UX-003 | P1 | Low | The widget privacy switch has no accessible name | [Spec](superpowers/specs/2026-10-20-audit-n-ux-003-widget-privacy-switch-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-003-widget-privacy-switch.md) | — | Draft / not started |
| UX-001 | P1 | Medium | Habit retries conceal the date they will affect | [Spec](superpowers/specs/2026-10-20-audit-n-ux-001-habit-retry-context-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-001-habit-retry-context.md) | — | Implemented / acceptance pending |
| UX-004 | P1 | Medium | Unbounded Mood notes are copied into saved instance state | [Spec](superpowers/specs/2026-10-20-audit-n-ux-004-mood-note-durability-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-004-mood-note-durability.md) | — | Implemented / acceptance pending |
| UX-006 | P2 | Low | Restored Mood text disappears behind a load error | [Spec](superpowers/specs/2026-10-20-audit-n-ux-006-mood-restored-content-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-006-mood-restored-content.md) | — | Implemented / acceptance pending |
| UX-005 | P2 | Medium | Interrupted Mood deletion has no reconciliation path | [Spec](superpowers/specs/2026-10-20-audit-n-ux-005-mood-delete-reconciliation-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-005-mood-delete-reconciliation.md) | UX-004 | Implemented / acceptance pending |
| UX-007 | P2 | Low | A conflicted Today task cannot be opened for review | [Spec](superpowers/specs/2026-10-20-audit-n-ux-007-task-conflict-opening-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-007-task-conflict-opening.md) | — | Draft / not started |
| UX-008 | P2 | Low | Retained task errors can crowd out the task list | [Spec](superpowers/specs/2026-10-20-audit-n-ux-008-task-recovery-list-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-008-task-recovery-list.md) | — | Draft / not started |
| UX-009 | P2 | Low | Past-day summary Check in opens a current-time entry | [Spec](superpowers/specs/2026-10-20-audit-n-ux-009-summary-dated-capture-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-009-summary-dated-capture.md) | — | Draft / not started |
| UX-015 | P2 | Low | Habit failures display the wrong operation's message | [Spec](superpowers/specs/2026-10-20-audit-n-ux-015-habit-operation-errors-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-015-habit-operation-errors.md) | — | Draft / not started |
| UX-016 | P2 | Low | Review source page labels reverse chronological direction | [Spec](superpowers/specs/2026-10-20-audit-n-ux-016-review-source-pagination-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-016-review-source-pagination.md) | — | Implemented / acceptance pending |
| UX-017 | P2 | Low | Successful integration refresh leaves obsolete error text | [Spec](superpowers/specs/2026-10-20-audit-n-ux-017-integration-error-recovery-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-017-integration-error-recovery.md) | — | Draft / not started |
| UX-018 | P2 | Low | Several important asynchronous results lack announcement semantics | [Spec](superpowers/specs/2026-10-20-audit-n-ux-018-accessible-async-feedback-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-018-accessible-async-feedback.md) | — | Draft / not started |
| UX-020 | P2 | Low | Entry card accessibility actions promise Edit but open details | [Spec](superpowers/specs/2026-10-20-audit-n-ux-020-entry-card-action-labels-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-020-entry-card-action-labels.md) | — | Draft / not started |
| UX-025 | P2 | Low | Restore confirmation has an unbounded warning body | [Spec](superpowers/specs/2026-10-20-audit-n-ux-025-restore-dialog-reachability-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-025-restore-dialog-reachability.md) | — | Draft / not started |
| UX-027 | P2 | Low | Generic Search does not explain excluded content | [Spec](superpowers/specs/2026-10-20-audit-n-ux-027-entry-search-scope-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-027-entry-search-scope.md) | — | Implemented / acceptance pending |
| UX-022 | P2 | Low | Visible headings are missing semantic heading roles | [Spec](superpowers/specs/2026-10-20-audit-n-ux-022-semantic-headings-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-022-semantic-headings.md) | — | Draft / not started |
| UX-023 | P2 | Low | Expanded disclosures lose their subject and state | [Spec](superpowers/specs/2026-10-20-audit-n-ux-023-named-stateful-disclosures-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-023-named-stateful-disclosures.md) | — | Draft / not started |
| UX-019 | P2 | Medium | Saved records and capture use different hour-cycle policies | [Spec](superpowers/specs/2026-10-20-audit-n-ux-019-shared-display-time-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-019-shared-display-time.md) | — | Draft / not started |
| UX-021 | P2 | Medium | Page chrome and inset ownership vary across screen families | [Spec](superpowers/specs/2026-10-20-audit-n-ux-021-page-header-insets-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-021-page-header-insets.md) | — | Draft / not started |
| UX-010 | P2 | Medium | Deleting an entry can return to its missing detail page | [Spec](superpowers/specs/2026-10-20-audit-n-ux-010-deleted-detail-navigation-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-010-deleted-detail-navigation.md) | — | Draft / not started |
| UX-011 | P2 | Medium | Creating a person/place during capture does not attach it | [Spec](superpowers/specs/2026-10-20-audit-n-ux-011-created-context-results-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-011-created-context-results.md) | — | Draft / not started |
| UX-012 | P2 | Medium | Context selection requires scanning the entire catalog | [Spec](superpowers/specs/2026-10-20-audit-n-ux-012-searchable-context-selection-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-012-searchable-context-selection.md) | — | Draft / not started |
| UX-014 | P2 | Medium | Habit management sits after all loaded history | [Spec](superpowers/specs/2026-10-20-audit-n-ux-014-habit-detail-management-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-014-habit-detail-management.md) | — | Draft / not started |
| UX-013 | P2 | Medium | A historical habit link does not locate the requested repetition | [Spec](superpowers/specs/2026-10-20-audit-n-ux-013-habit-targeted-history-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-013-habit-targeted-history.md) | — | Draft / not started |
| UX-024 | P2 | Medium | Leaving during backup has no explicit operation outcome | [Spec](superpowers/specs/2026-10-20-audit-n-ux-024-backup-departure-outcome-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-024-backup-departure-outcome.md) | — | Draft / not started |
| UX-026 | P2 | Low | Weekly activity can render a nonzero value as zero | [Spec](superpowers/specs/2026-10-20-audit-n-ux-026-activity-number-precision-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-026-activity-number-precision.md) | — | Draft / not started |
| UX-028 | P3 | High | Attached images cannot be inspected beyond their previews | [Spec](superpowers/specs/2026-10-20-audit-n-ux-028-optional-photo-viewer-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-028-optional-photo-viewer.md) | — | Optional / not selected |

Priority totals: **4 P1, 23 P2, 1 P3; no P0 established**. UX-028 is an optional capability and does not block the required 27 corrective tasks.

## Recommended delivery sequence

1. **P1 privacy and reliability:** UX-002, UX-003, UX-001, UX-004. Correct unknown privacy status and switch semantics first, then original habit-action context and durable Mood notes.
2. **Recovery and truthful feedback:** UX-006, UX-005 after UX-004, UX-007, UX-008, UX-009, UX-015, UX-016, UX-017, UX-018, UX-020, UX-025, UX-027. These do not require a page redesign. Keep small copy/semantics fixes independently reviewable.
3. **Navigation, scale and consistency:** UX-022, UX-023, UX-019, UX-021; then UX-010, UX-011, UX-012, UX-014, UX-013, UX-024 and UX-026. Pilot the page/inset contract before its broad migration. Make management/history lazy before adding selected-log arrival when sharing the same integration branch.
4. **Optional media review:** evaluate UX-028 separately. Selection requires a product decision that detailed attachment inspection is worth its larger scope; generating its documents does not select it for implementation.

Only UX-005 → UX-004 is a hard cross-task implementation prerequisite in these drafts: deletion uses the durable Mood snapshot contract. Other ordering is integration guidance, not a reason to block unrelated fixes. UX-013 uses the already-existing `getLog` API and an explicit selected record above history; it does not need a new repository window query.

## Shared integration boundaries

| Shared area | Tasks | Integration rule |
|---|---|---|
| Mood state/draft lifecycle | UX-004, UX-005, UX-006 | Land durable snapshot API first for deletion reconciliation; keep visible read-only recovery independently shippable. Rebase state additions together and keep all legacy draft data. |
| Habit detail/history/state | UX-001, UX-013, UX-014, UX-015, UX-019, UX-022 | Prefer UX-015 operation ownership, UX-014 lazy rows, then UX-013 target section; reuse date/time/heading contracts. Preserve generation guards, command IDs, offsets and Undo. |
| Task rows and recovery | UX-007, UX-008 | Merge the canOpen/canMutate signature at all callers before integrating lazy retained-feedback placement. Preserve task paging arithmetic. |
| Capture/navigation results | UX-009, UX-010, UX-011, UX-012 | Entry results and catalog results are distinct typed contracts. Scope results to caller/request; preserve unmark versus delete. New in a picker commits its working selection before creation. |
| Shared semantics and headers | UX-018, UX-020–UX-023, UX-027 | Low-scope semantics/copy may ship before page scaffolds. Subsequent header migration must keep heading, named disclosure, destination labels and scoped live feedback intact. |
| Formatting | UX-019, UX-026 | Time policy and activity precision are separate display contracts; neither alters storage or rating-average precision. |
| Backup lifecycle and dialog | UX-018, UX-024, UX-025 | Accessible results, guarded departure and a scrollable confirmation are independent changes; preserve replacement consent and protected restore commit. |
| Widget privacy | UX-002, UX-003 | Status accuracy and toggle accessibility are separate fixes. Preserve explicit per-instance opt-in and renderer behavior. |

Avoid simultaneous edits to shared files without an integration owner. This roadmap does not select multi-agent execution or launch implementation.

## Proposed policy decisions

These defaults make each draft concrete enough to review. They are not decisions previously supplied by the user and can be revised before implementation.

| Task | Proposed choice | Reason / limit |
|---|---|---|
| UX-004 | New Mood note limit 5,000 UTF-16 units; unchanged/shorter legacy notes remain allowed; full drafts use private atomic snapshots. | Resolves the Bundle risk without silently truncating existing records or blocking access to long legacy notes. This is a policy proposal, not a measured crash threshold. |
| UX-005 | Durably retain deletion intent; reconcile existence before normal dependencies load; require explicit retry when the record remains. | Distinguishes committed deletion, unchanged data and unknown outcome. |
| UX-008 | Put retained actions and Undo inside the existing task LazyColumn. | Bounds layout without dropping recovery or adding another unbounded region. |
| UX-009 | Reuse selected-date versus now choice from Today. | Keeps historical capture explicit and avoids a new timestamp default. |
| UX-012 | Staged searchable modal, Done/Clear/Cancel, local filtering over observed catalogs; New applies working selections before leaving. | Improves discovery/lazy composition while preserving current repositories and creation results. Large database paging is a later measured need. |
| UX-013 | Load selected repetition independently and show it above paged history. | Uses existing getLog and reliably identifies older evidence without a new paging interface. |
| UX-015 | Separate history/deletion error channels and existing correction error; reconcile failed deletes. | Prevents unrelated success from erasing another operation's actionable feedback. |
| UX-021 | Shell owns system insets; nested scaffolds use zero system-bar insets; primary titleLarge, 20 dp compact gutter, proposed 840 dp maximum content width. | Pilot Today/Timeline/Mood before broad migration; wide-width value remains reviewable. |
| UX-024 | Confirm cancellation before export/preparation departure; wait for closure/cleanup; block departure during protected restore commit. | Keeps work route-owned with a clear outcome; does not promise process-death survival. Unsupported output cleanup is reported explicitly. |
| UX-026 | Reuse Daily Summary's precision-preserving activity NumberFormat policy. | Keeps supported small measurements/deltas nonzero while ratings retain two decimal places. |
| UX-028 | Optional 8,000,000-pixel display decode budget and 1x–4x zoom. | Proposed bounded viewer behavior; original import/640-pixel preview policy remains separate. |

## Shared constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies unless a task explicitly requires a reviewed exception.
- All changed user-facing copy must exist in EN/FR/FA with matching resource names and numbered placeholder signatures; preserve RTL and intended Gregorian display/storage behavior.
- Keep semantic light/dark palettes, at least 48 dp interactive targets, and reachability at 100%/130%/200% font, narrow portrait and short landscape.
- Preserve offline/private storage, existing consent boundaries, stable record/command IDs, original retry payloads and Undo protections.
- Never truncate accepted personal content, infer write failure means no commit, or treat an unavailable privacy mode as known.
- Only implement this task and its declared prerequisites; preserve unrelated working-tree changes and existing navigation/draft state.

Do not reimplement historical findings already fixed in the inspected source: stale-day habit protections, persistent habit Undo recovery, Search state differentiation, Review section navigation, shared widget Capture and scrolling/compact recovery remain baseline behavior to preserve.

The audit's typography/spacing/width recommendations belong to UX-021, semantic sections to UX-022, named disclosures to UX-023, time formatting to UX-019 and precision to UX-026. A broad palette change, tablet multi-pane design, accounts/cloud/AI features and new primary tabs are outside scope.

## Future verification and release evidence

Use each task's exact test classes and commands. JVM tests cover pure policy/state where available; connected tests cover Compose/navigation; repository/device tests cover private-file/database behavior. Commands in plans are prospective and may need the local JDK/Android SDK/device configured at execution time. Missing prerequisites are not passing checks.

| Evidence layer | Required result |
|---|---|
| Contract/regression | Each task's five AC cases has an actual result, mapped to its named test or recorded manual scenario. Capture the initial mismatch when reproducible. |
| Build/resource | Affected modules compile; app and Android-test APKs package; lint passes; EN/FR/FA names and format placeholders match. |
| Runtime layout | For relevant surfaces: compact portrait, short landscape, IME open, light/dark and 100%/130%/200% font; actions and full warnings remain reachable. |
| Assistive behavior | Inspect merged/unmerged semantics and test actual TalkBack speech, heading traversal, focus and action outcomes in EN/FR/FA. Semantics alone does not certify speech. |
| Reliability | Inject storage/read/write failure, late completion, recreation and committed-but-undelivered outcomes for the task that owns them. Preserve accepted data and command identities. |
| Integration | Rerun changed shared callers and the original source → detail/editor → result journey after integrating adjacent tasks. |

During implementation, record task ID, source revision, changed behavior, command/results, device/API/locale/font/window configuration and evidence paths. Use **Draft → In progress → Implemented / acceptance pending → Verified**. Keep optional UX-028 as **Not selected** until separately selected. Do not mark a task Verified from documentation, historical screenshots or source inspection alone.

## Documentation completion boundary

This package is complete when UX-001–UX-028 each has one linked spec and plan, links/file targets resolve or are explicitly proposed, prerequisites are coherent, and no implementation/acceptance result is implied. Application fixes and runtime acceptance remain future work. The existing task trackers are not marked complete by this documentation package.

## Documentation verification

- Verified 57 documents: one index, 28 specs and 28 plans, with all audit IDs paired exactly once.
- Verified 140 unique task acceptance cases, five per task, with matching plan review-focus mappings.
- Checked 143 relative document links and the listed existing source/test targets; proposed source/test files are explicitly marked as additions or dependency outputs.
- Checked that implementation checkboxes remain unchecked and no unfinished placeholders remain.
- These are documentation checks only. No Gradle build, application test, emulator/device session or TalkBack acceptance was run for this request.

