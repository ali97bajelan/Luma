# T21 App Language Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans for locale integration and verification; independent translation resources use superpowers:dispatching-parallel-agents.

**Goal:** Implement the approved T21 in TASKS.md: saved English/Persian/French app language selection without losing user data or drafts.
**Architecture:** Retain ComponentActivity and existing saved-state navigation. A small locale policy resolves supported languages; a context wrapper applies locale/layout before activity creation. Android 13+ uses LocaleManager persistence; older devices use private preferences and explicit activity recreation. Resource files contain all translations.
**Tech Stack:** Kotlin, Android API 28–35, Jetpack Compose, existing dependencies only.
**Spec:** TASKS.md, T21.

## Global Constraints

- English, Persian and French only; supported device locale before explicit choice, otherwise English.
- Preserve stored records, chosen dates, user text and editor drafts. No schema migration or permission change.
- Offline translations, Persian RTL, locale-aware numbers/dates/plurals/accessibility labels.
- Work in the user's current workspace; preserve preexisting release/schema/fixture artifacts. No commit or installation into the user's app.

## Review Focus

- Android 28–32 restart uses the saved choice, independent of system changes.
- Android 33+ native Settings changes remain authoritative, including returning to device default.
- Multiple device locales resolve to the first supported language, unsupported lists to English.
- Configuration changes preserve navigation and saved capture fields.
- All translations preserve format argument types and escaped apostrophes; every language is packaged offline.

### Task 1: Language policy, persistence and Settings

Files: app/src/main/java/com/luma/lifeos/language/{AppLanguage,AppLanguages,LanguageSettingsCard}.kt; MainActivity.kt; HealthRationaleActivity.kt; LifeOsApp.kt; UsageSettingsRoute.kt; manifest, locales_config.xml, app build configuration.
Interfaces: AppLanguage.resolve(selected: String?, deviceLanguages: List<String>): AppLanguage; AppLanguages.wrap(Context): Context; AppLanguages.select(Activity, AppLanguage): Boolean.
- [x] Reuse existing policy tests for explicit choice, regional language matching, unsupported/empty locale lists, invalid stored value, and multi-locale priority; verify the partial implementation.
- [x] Verify existing platform locale persistence, context configuration and settings radio choices, both Settings variants and rationale activity; retain saved-state lifecycle.
- [x] Run policy tests GREEN and compile APKs. Add Android tests for persistence, locale layout/resources and actual activity state restoration.

### Task 2: Complete translated resources and formatting

Files: values/strings.xml; values-fa/strings.xml; values-fr/strings.xml; UI date formatting calls.
Interfaces: identical resource names and positional argument signatures in all three languages.
- [x] Translate every string/plural (independent Persian/French files).
- [x] Audit visible hardcoded text, stored built-in labels, date/number formatting and RTL assumptions; fix app-owned labels while keeping stored user strings.
- [x] Verify XML completeness, argument parity, resource compilation, and lint.

### Task 3: Verify and document

Files: language unit/instrumentation tests; TASKS.md; docs/VALIDATION.md.
- [x] Run full JVM suites, normal APK/test builds and lint; run focused device tests using a disposable app ID on a temporary read-only emulator.
- [x] Fresh read-only review against T21; no Critical/Important findings.
- [x] Record exact evidence and remaining platform follow-ups; runtime claims backed by device execution.

## Execution ledger

Ruling: user's “do T21” approves the task just documented; proceed within that scope without another authorization question.
Ruling: keep the current workspace and no git mutations to preserve the user's immediately reviewable changes and restricted .git permissions.

2026-10-13: existing implementation and policy tests retained. Catalog regression
failed on missing Persian bundle; all 381 resource names now match with usable
formatting. Built-in vocabulary regressions failed on missing canonical mapping,
then passed with stable UUID/name and native-source checks. Nine new JVM cases;
full suite 277/277 passes. Five new native cases pass on API 36.1; initial picker
test ambiguity corrected by limiting Cancel to its dialog. Separate process/device
locale probes and visual checks resolve review's restart/picker/bidi evidence gaps.

Ruling: API 28–32 persistence is reviewed and policy-tested; no legacy system image
is available for native execution. Retain this as a platform follow-up, with
current acceptance verified on API 36.1. No dependency/schema/permission change.

Final: minor (deferred): French bottom navigation wraps words at font scale 1.3;
controls remain reachable and labels visible. Carry responsive label polish into T19.

Final: read-only independent review found no Critical/Important defect. Normal
debug and instrumentation APKs rebuilt after disposable-ID checks; lint zero errors.
