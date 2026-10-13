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
- [ ] Write policy tests for explicit choice, regional language matching, unsupported/empty locale lists, invalid stored value, and multi-locale priority. Run focused JVM test RED.
- [ ] Implement policy and platform locale persistence, context configuration and settings radio choices; wire both Settings variants and rationale activity. Preserve saved-state lifecycle.
- [ ] Run policy tests GREEN and compile APKs. Add Android tests for persistence, locale layout/resources and actual activity state restoration.

### Task 2: Complete translated resources and formatting

Files: values/strings.xml; values-fa/strings.xml; values-fr/strings.xml; UI date formatting calls.
Interfaces: identical resource names and positional argument signatures in all three languages.
- [ ] Translate every string/plural (independent Persian/French files).
- [ ] Audit visible hardcoded text, stored built-in labels, date/number formatting and RTL assumptions; fix app-owned labels while keeping stored user strings.
- [ ] Verify XML completeness, argument parity, resource compilation, and lint.

### Task 3: Verify and document

Files: language unit/instrumentation tests; TASKS.md; docs/VALIDATION.md.
- [ ] Run full JVM suites, normal APK/test builds and lint; run focused device tests if a device is connected, using a disposable app ID.
- [ ] Fresh read-only review against T21, fix material findings and rerun affected checks.
- [ ] Record exact evidence and pending device acceptance; no passing runtime claim based on compilation.

## Execution ledger

Ruling: user's “do T21” approves the task just documented; proceed within that scope without another authorization question.
Ruling: keep the current workspace and no git mutations to preserve the user's immediately reviewable changes and restricted .git permissions.
