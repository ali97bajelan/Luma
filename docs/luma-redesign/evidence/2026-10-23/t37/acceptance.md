# T37 Task editor implementation record

Date: 2026-10-23  
Status: **IMPLEMENTED (VALIDATION PENDING)**

The task editor now follows the reference hierarchy with the existing editorial header, a single-line focused title field, spacious optional notes, a rounded due-date card, and a 56 dp pill Save action. Change and conditional Remove appear below the localized date so long dates do not compete for the same row. Existing close/discard, validation/focus, date-picker, complete/reopen, delete, conflict/reload, missing-record, retry and pending-action branches remain in `TodoEditorScreen`; route, callback, ViewModel, repository, domain and test-tag contracts were not changed.

O09 tags and the unsupported “Scheduled for evening review” caption remain omitted. No reminder, recurrence, time-of-day or People/Place task behavior was added. Existing localized strings are reused; no resource catalog changes were needed.

Per user instruction, no Gradle task, test, emulator, rendered capture or TalkBack check was run. No build SHA or runtime evidence is available. Static source review confirmed that this change is confined to `TodoEditorScreen` and task documentation. `git diff --check` is recorded after the final diff review. T37 acceptance remains pending; existing T32-D blockers and the T33 release gate are unchanged.

| Scenario | Status | Evidence / follow-up |
| --- | --- | --- |
| T37-1 — No-date create; due-date set/remove/cancel and LocalDate restoration | NOT RUN | Runtime picker, recreation and Save behavior were not exercised. |
| T37-2 — Required/single-line title and 200/4,000 Unicode code-point boundaries | NOT RUN | Existing validation and code-point rules were left unchanged; boundary behavior was not exercised. |
| T37-3 — Edit Complete/Reopen/Delete, dirty confirmations, revision checks and one result | NOT RUN | Existing state and callback paths were retained; lifecycle and repository behavior were not exercised. |
| T37-4 — Loading, missing, conflict, save/status/delete failure, retry and pending actions | NOT RUN | Existing recovery branches remain in source; no failure or duplicate-action scenario was run. |
| T37-5 — Light/Dark, en/fr/fa, RTL, IME, TalkBack and required width/font matrix | NOT RUN | No device, screenshots or accessibility inspection were used. Planned captures are not evidence. |

Suggested commit message: `feat(ui): refine T37 task editor`
