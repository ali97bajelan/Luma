# APK Variant Prompt Design

## Intent

When generating an APK through the new Gradle entry point, ask which variant to
build. The user-facing APK must keep the package ID from `defaultConfig`
(`com.luma.lifeos`), while existing isolated QA builds remain available.

## Approaches

- Add a no-suffix `production` flavor and retain both QA flavors. This preserves
  QA variants and gives the user build the `defaultConfig` application ID. **Chosen.**
- Remove the QA flavor dimension and prompt only for debug or release. This
  removes the existing isolated QA variants.
- Keep only the current QA flavors and prompt between them. This cannot produce
  the requested user-facing package ID.

## Design

- Add `production` to the existing `qa` flavor dimension without an application
  ID or version suffix, so it inherits `defaultConfig` values.
- Add a terminal Gradle task that presents available production and QA APK
  variants, then assembles the selected variant.
- Document the task and variant/package distinction in the build instructions.
- Android Studio's built-in **Build APK** action remains controlled by its
  selected Build Variant; the prompt is available through the new Gradle task.

## Scope and constraints

Keep release signing and minification settings unchanged. Do not build or replace
an APK as part of this configuration change. Verification is limited to reviewing
the Gradle configuration and documentation; no tests or APK assembly are planned.
