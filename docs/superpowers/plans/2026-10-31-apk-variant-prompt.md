# Interactive APK Variant Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an interactive Gradle task that asks which APK variant to assemble, including a user-facing variant that inherits `defaultConfig`.

**Architecture:** Add a no-suffix `production` flavor alongside the existing QA flavors. The new task prompts for one of the six flavor/build-type variants and depends on the selected AGP assembly task; README documents the terminal entry point and output variants.

**Tech Stack:** Kotlin Gradle DSL, Android Gradle Plugin, Gradle wrapper.

**Spec:** [APK Variant Prompt Design](../specs/2026-10-31-apk-variant-prompt-design.md)

## Global Constraints

- Keep `defaultConfig.applicationId` as `com.luma.lifeos` for production.
- Preserve `appLockQa` and `focusQa` flavors and their application/version suffixes.
- Keep release signing and minification behavior unchanged.
- Prompt only when the new task is explicitly invoked from a terminal.
- Do not assemble or replace APKs as part of this implementation.
- Do not add or run tests for this change.

## Review Focus

- Production variants inherit the default package ID and version without suffixes.
- Both QA flavors retain their current package and version suffixes.
- The prompt lists all six production/QA debug/release variants and maps each selection to its exact `assemble<Variant>` task.
- Invalid input and end-of-input stop with a clear message rather than building an unintended variant.
- Android Studio's built-in Build APK behavior remains governed by the selected Build Variant; the new prompt is the explicit Gradle task.

---

### Task 1: Add production APK variant and interactive Gradle task

**Files:**
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: `:app:buildApk`, an interactive task offering `productionDebug`, `productionRelease`, `appLockQaDebug`, `appLockQaRelease`, `focusQaDebug`, and `focusQaRelease`.

- [ ] Add the `production` flavor to the existing `qa` dimension without application ID or version suffixes.
- [ ] Add `buildApk` so an explicit terminal invocation displays the six variants, validates the selection, and depends on the corresponding `assemble<Variant>` task.
- [ ] Handle invalid input and EOF as a clear cancellation; do not silently choose a variant.
- [ ] Review the Gradle configuration and task names statically. Do not run tests or assemble an APK.

### Task 2: Document the APK workflow

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: `:app:buildApk` from Task 1.

- [ ] Document `./gradlew :app:buildApk`, the six choices, the production package behavior, and that Android Studio's built-in APK action uses the currently selected Build Variant.
