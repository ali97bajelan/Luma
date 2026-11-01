# App Lock Settings Placement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the App lock entry from Privacy to the top of the Settings hub, opening the existing app-lock settings route.

**Architecture:** Add an App lock navigation row directly below the Settings hub description and wire it to the existing destination. Remove the App lock card and navigation parameters from Privacy while retaining its enabled-state checks for protected data reset.

**Tech Stack:** Kotlin, Jetpack Compose, Android string resources, Navigation Compose.

**Spec:** `docs/superpowers/specs/2026-11-01-app-lock-settings-placement-design.md`

## Global Constraints

- Preserve the existing credential, recovery, and lock-policy flows.
- Keep Privacy's data-reset authorization and lock-state safeguards.
- Do not add or edit test files, and do not run tests, per the user's instruction.
- Reuse existing localized App lock title and description resources.

## Review Focus

- When app-lock settings are unavailable, the Settings hub must not show an unusable App lock row. Review the conditional rendering in `SettingsRoute`.
- When available, the row must navigate to the existing App lock route. Review the callback wiring in `LifeOsApp`.
- Removing the Privacy entry must not remove `appLockEnabled` reset gating or authorization. Review both reset confirmation and button enablement in `PrivacySettingsRoute`.

---

### Task 1: Move the App lock entry into the Settings hub

**Files:**
- Modify: `app/src/main/java/com/luma/lifeos/ui/settings/SettingsRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/privacy/PrivacySettingsRoute.kt`

**Interfaces:**
- `SettingsRoute` gains `appLockAvailable: Boolean = false` and `onAppLock: () -> Unit = {}` parameters.
- `PrivacySettingsRoute` and `PrivacySettingsContent` no longer accept or render the App lock entry; `appLockEnabled` remains for data-reset safeguards.

- [x] Add conditional App lock `SettingsRow` at the top of `SettingsRoute`, using `R.string.app_lock_settings_title`, `R.string.app_lock_privacy_description`, the `settings_app_lock` tag, and `onAppLock`.
- [x] Wire `appLockAvailable = lockCredentialService != null && appLockCoordinator != null` and navigation to `AppRoutes.APP_LOCK_SETTINGS` in the Settings destination in `LifeOsApp`.
- [x] Remove `appLockAvailable` and `onAppLock` plumbing and the App lock card from the Privacy route/content; retain reset authorization and enabled-state behavior.
- [x] Review the resulting source diff and call sites for stale Privacy entry-point references. Do not edit test files or run tests.
