# IP-Q01 — Integrated emulator QA specification

**Status** NOT RUN

[Task index](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md) · [QA plan and scenario matrix](../plans/2026-11-20-product-improvements-q01-emulator-qa.md) · [Shared QA workflow](../../feature-roadmap/WORKFLOW.md)

## Entry gate

All nine implementation handoffs, including the disclosure and encrypted archive slices of IP-I02, must be source complete. Each handoff identifies the revision/patch, affected paths, requirements and scenario. Record a reproducible pre-IP-I03 baseline from the handoff before building the candidate. Existing validation history is context for triage.

## Environment and fixtures

Use isolated Android Studio AVDs and disposable app/test data. Verify the APK and installed package identities before installation or running a connected task. The current debug configuration defines `com.luma.lifeos.qa`. Use independent synthetic installs for backup portability, one primary modern AVD (API 36 or a supported newer system image with notification runtime permissions) and an API 28 AVD. Use the supported calendar, localization, theme, font scale, window and accessibility matrix in the shared workflow.

Fixtures include empty and large multilingual libraries, protected and unprotected records, historical/current dates, 57 open due/overdue tasks and an undated task, legacy Journal/backup data, valid and missing temporary photos, paused/running Focus, differing tracking zones, and sparse/zero/partial-week review coverage. Record fixture versions and synthetic inputs.

## Acceptance

- **IP-Q01-R1** Run the host/compile/lint and connected acceptance gates on the exact candidate. Execute all nine manual emulator scenarios and relevant fixture/matrix edges. Native permission, SAF, background process and accessibility claims require emulator observations.
- **IP-Q01-R2** Trace each feature requirement to evidence using QA-01–QA-09. Mark each result PASS, FAIL, BLOCKED, NOT RUN or justified N/A. Keep baseline and candidate evidence distinct. Include command outputs, screenshots or accessibility observations, and record-level checks when needed.
- **IP-Q01-R3** Correct reproduced in-scope defects under IP-Q01, rerun the affected requirements and cross-feature checks, and record the final candidate identity. A discovery that changes a feature contract reopens the relevant handoff and its dependent handoffs before affected QA resumes. Privacy and integrity failures block acceptance.
- **IP-Q01-R4** Save one dated acceptance record. Accept only the scope with fresh candidate evidence and a complete required matrix. Record any environment limitation and the affected scenario. Physical-device delivery/performance and consented usability observations remain separate validation evidence.

Record execution under `docs/validation/product-improvements/<actual-run-date>/`. Keep screenshots, logs and exported test fixtures limited to synthetic data.
