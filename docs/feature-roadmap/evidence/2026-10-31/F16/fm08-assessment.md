# FM08 device-wide enforcement assessment — 31 October 2026

## Decision

Recommend keeping the proposed personal-phone v1 scope: Luma guards its own navigation and offers explicit shortcuts to selected apps. This is a scoped assessment recommendation, not confirmation that the original device-wide restriction has been delivered or that the user has approved the proposed defaults. The strict requirement remains **unmet**. If the product must block every non-selected app across Android, treat that as a separate managed-device product branch and revise the spec and plan before implementation.

The task/spec identify a personal phone as the proposed deployment and explicitly withhold authority to provision or reset a device. This assessment adopts that proposal as its working target. No separate user confirmation of a managed-device deployment or exact treatment of all system/emergency paths was present. The proposed personal-phone behavior applies only inside Luma. Whether the original request requires restriction outside Luma remains unresolved; this assessment records that requirement as unmet instead of treating the proposal as a user decision.

## Capability comparison

| Option | What it can enforce | What it cannot establish for this request | Assessment |
| --- | --- | --- | --- |
| Luma navigation guard and selected-app shortcuts | Keep navigation within Luma while a session runs; launch an app after an explicit user tap; persist the timer across the handoff. | Does not control Home, Recents, the notification shade, Android settings, app launches outside Luma, or force-stop. A selected app may open another app. | Existing personal-phone v1; useful as a focus workflow, not a device-wide allowlist. |
| Screen pinning | Pins the current task for an immersive display. | The device user can exit it. Android documents screen pinning separately from lock task mode; it is not a non-exitable multi-app policy. | Insufficient for strict enforcement. Do not silently call it a solution. |
| Usage access, app monitoring, overlays, or AccessibilityService | Usage access can expose usage statistics after a user grants special access in Settings. Accessibility APIs can observe/interact with UI for their permitted purpose. | Usage statistics observe use; they do not deny launches. Repeated background relaunch is not an enforcement contract and Android restricts background activity starts. Google Play forbids using Accessibility API to bypass platform controls or prevent disabling/uninstalling an app except under parental-control or enterprise-administration authority; it also requires declarations/disclosures and consent for non-accessibility-tool uses. | Not selected. It adds sensitive access, user setup, policy risk and bypass paths without providing the required managed allowlist. |
| DPC multi-app lock task on a fully managed device | A device policy controller allowlists packages for lock task mode. Android can suppress non-allowlisted apps and configurable system UI such as Home, Overview and notifications; a DPC can block non-allowlisted activities entering the locked task. | It does not constrain content or destinations inside an allowed package, and OS/system/privileged and emergency behavior must be designed and verified on the target device. An app allowlist is package-level. Device-specific behavior, allowed-app cross-links, restart/recovery and Pause/Stop escape need an end-to-end managed-device run. | Documented Android-native path identified for the strict package-level requirement. Requires a distinct managed-device deployment and implementation. Deferred from this personal-phone task. |

## Constraints for a future managed-device branch

- **Authority and setup:** `DevicePolicyManager.setLockTaskPackages()` and `setLockTaskFeatures()` require a device owner or an eligible affiliated profile owner (or the relevant privileged policy permission). An ordinary installed app cannot grant itself that authority. Android's dedicated-device setup guide provisions by factory-resetting and enrolling the device, recommending QR enrollment. The current task authorizes neither provisioning nor reset.
- **Distribution and enrollment:** Android documents a custom DPC/custom launcher, Android Management API, or a third-party EMM as deployment paths. The product would need an explicit managed-device onboarding, enrollment ownership/support model, and distribution decision. No consumer Play or EMM approval is inferred here.
- **System UI and emergency recovery:** Lock task features control Home, Overview, notification shade, system information, keyguard, and global actions. Their inclusion is configurable; disabling global actions can remove the power-button menu. A strict configuration must retain and test safe call/emergency, power, pause/stop, and administrator recovery paths rather than claim every OS escape is removed. Android 9/API 28 lets a DPC launch another app's activity into lock task; `LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK` was added in API 30 for blocking non-allowlisted activity starts within a locked task. Verify behavior on the actual target OS/OEM.
- **App links:** Package allowlisting does not filter web content or activities inside an allowed app. A browser or other selected package can still show content that is not separately allowlisted; cross-package deep links need the DPC activity-start policy and real fixture coverage.
- **Compatibility:** Lock task mode is documented from API 21 and DPC allowlisting for fully managed devices from API 21. Android 9/API 28 adds starting another app's activity into lock task mode, matching Luma's current minimum API. OEM/OS compatibility is not established by documentation and requires a device matrix.
- **Recovery:** Design an explicit exit after Pause/Stop and an independent administrator recovery path. Android documents that a DPC can remove the app from its allowlist to stop lock task, while the activity that entered lock task can call `stopLockTask()`. Test crashes, reboot, removed/uninstalled allowlisted apps, policy loss, and failed app launches before any enforcement claim.

## Repository boundary review

Read-only source inspection on 31 October 2026:

- `app/src/main/AndroidManifest.xml` contains a `MAIN`/`LAUNCHER` `<queries>` declaration for launcher discovery. It has no `DeviceAdminReceiver`, DPC/admin component, `PACKAGE_USAGE_STATS`, or accessibility-service declaration.
- The read-only command `rg -n 'DevicePolicyManager|DeviceAdminReceiver|startLockTask|stopLockTask|UsageStatsManager|PACKAGE_USAGE_STATS|AccessibilityService|BIND_ACCESSIBILITY_SERVICE|<queries>|lockTaskMode' app core --glob '!**/build/**'` found no enforcement implementation; its only match was the existing manifest `<queries>` declaration.
- Package visibility controls which installed apps Luma can discover or interact with; the narrow launcher query is not a launch restriction or policy allowlist. `QUERY_ALL_PACKAGES` is unnecessary for the existing picker and is not proposed.
- No production code, manifest, dependency, permission, DPC provisioning, device reset, Gradle task, or test was run or changed for this assessment. A read-only emulator policy-state check was added during QA; no app UI or app data was touched.

## API 36 emulator read-only check

Run on the already-running **Medium Phone API 36.1** emulator (`emulator-5554`) on 31 October 2026. The system reports Android 16 / SDK 36 and fingerprint `google/sdk_gphone64_x86_64/emu64xa:16/BP41.250822.007/14042983:user/release-keys`. Both `com.luma.lifeos` and `com.luma.lifeos.qa` are installed.

Read-only checks:

```text
/home/bajelan/Android/Sdk/platform-tools/adb devices -l
/home/bajelan/Android/Sdk/platform-tools/adb -s emulator-5554 shell getprop ro.build.version.release
/home/bajelan/Android/Sdk/platform-tools/adb -s emulator-5554 shell getprop ro.build.version.sdk
/home/bajelan/Android/Sdk/platform-tools/adb -s emulator-5554 shell getprop ro.product.model
/home/bajelan/Android/Sdk/platform-tools/adb -s emulator-5554 shell getprop ro.build.fingerprint
/home/bajelan/Android/Sdk/platform-tools/adb -s emulator-5554 shell pm list packages | rg 'com\.luma\.lifeos'
/home/bajelan/Android/Sdk/platform-tools/adb -s emulator-5554 shell dumpsys device_policy | rg -i 'device owner|profile owner|owner package|lock task|locktask|mDeviceOwner' | head -80
/home/bajelan/Android/Sdk/platform-tools/adb -s emulator-5554 shell dumpsys activity activities | rg -i 'mLockTaskModeState|locktask|lock task' | head -30
```

Filtered device-policy/activity output showed `Device Owner Type: -1`, an empty `mLockTaskPackages` list, `mLockTaskModeState=NONE`, and no lock-task tasks. This confirms the emulator was not configured as a managed lock-task fixture. It does not exercise DPC enforcement, app-launch blocking, OEM behavior, or emergency recovery. No package was installed or removed, no setting or permission was changed, and no device reset or provisioning was attempted.

## QA15 result and limits

**PASS — scope/capability assessment only.** The recommendation was checked against current official Android documentation, a read-only source/manifest audit, and a read-only API 36 emulator policy-state inspection. Static inspection confirms the current source has no enforcement controller, and the emulator has no active device-owner lock-task policy. This verifies the scope claims and the emulator's test state; it does not prove behavior on a managed OEM device or validate a DPC implementation. No managed-device spike was run because it requires a separate managed-device setup and enforcement test.

Sources reviewed 31 October 2026:

- [Android lock task mode](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode) — DPC allowlisting, screen-pinning distinction, package launch, lock-task entry/exit and system UI features.
- [Android dedicated devices overview](https://developer.android.com/work/dpc/dedicated-devices) — fully managed deployment and factory-reset/enrollment steps.
- [DevicePolicyManager API reference](https://developer.android.com/reference/android/app/admin/DevicePolicyManager) — policy-authority requirements and lock-task package/feature APIs.
- [Android background activity launch restrictions](https://developer.android.com/guide/components/activities/secure-bal) — platform restrictions on background activity starts.
- [Android package visibility](https://developer.android.com/training/package-visibility) — app discovery filters and scoped `<queries>` declarations.
- [UsageStatsManager API reference](https://developer.android.com/reference/android/app/usage/UsageStatsManager) — usage APIs requiring user-granted `PACKAGE_USAGE_STATS` access.
- [Google Play sensitive API and Accessibility API policy](https://support.google.com/googleplay/android-developer/answer/16558241) — disclosure/consent and prohibited security-control bypass uses.

## Follow-up gate

Keep all messaging explicit that selected-app shortcuts are not an enforced allowlist and that Android controls and apps remain available during a personal-phone session. If the unmet device-wide requirement is still release-critical, the product owner must choose a managed-device deployment, target OS/OEM set, allowed system/emergency behavior, enrollment/distribution model, and recovery owner. Then revise the spec/plan, implement the DPC or select an EMM, and run QA15 on an authorized disposable managed device. Until those steps pass, F16 remains partial and the original device-wide restriction must not be marked complete.

Suggested commit message: `docs(focus): record FM08 enforcement QA results`
