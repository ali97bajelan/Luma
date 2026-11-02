# Manual App Lock setup check — Q01 partial

- Device: `emulator-5554`, Android 16 / API36, 1080x2400 / 420 dpi.
- Package: isolated `com.luma.lifeos.applockqa.qa`, versionCode 8 / versionName `1.5.0-applockqa`.
- Opened More → Settings → Privacy → App lock settings on a fresh QA install. The setup form exposed Whole app and Every time defaults.
- Before the fix, submitting blank password fields exposed “Use a password between 8 and 128 characters.” only 11 px high at the viewport bottom; a vertical scroll moved it fully into view at 40 px high. The form remained on setup; no policy-enable confirmation occurred. The setup error now requests scroll-to-bottom when the error appears, and focused API36 regression `emptyAppLockSetupScrollsValidationIntoView` passes 1/1 against the fixed screen.
- Back returned to Privacy. Reopening setup showed blank password fields and the original defaults. The credential screen capture was black while foregrounded, consistent with `FLAG_SECURE`.
- The valid setup path was not completed. No recovery code or credential was retained. The isolated QA app and emulator UI dumps were removed after the check; package inventory confirmed `com.luma.lifeos` and `com.luma.lifeos.qa` remained installed.
- Native service boundary/cancellation/atomic-write tests are also covered by [current app C4 JUnit](c4-app-after-fixes.xml). Seven/eight-character, 129-character, cancellation at later setup stages, successful setup acknowledgement, enabled cold-start and API28 UI checks remain open.

Evidence: [initial blank validation semantics](qa-manual-q01-empty-validation-window.xml), [validation after scroll](qa-manual-q01-error-after-scroll-window.xml), [bounds comparison](qa-manual-q01-validation-scroll.txt), [RED bounds diagnostic](c4-diagnostic-q01-no-scroll.log), [GREEN regression JUnit](c4-green-q01-validation-visibility.xml), [cancel return semantics](qa-manual-q01-cancel-window.xml), [reopened blank setup semantics](qa-manual-q01-reopen-window.xml), [secure setup capture](qa-manual-app-lock-setup.png), [package cleanup](qa-manual-q01-cleanup.txt), [remaining package inventory](qa-manual-luma-packages-after.txt).
