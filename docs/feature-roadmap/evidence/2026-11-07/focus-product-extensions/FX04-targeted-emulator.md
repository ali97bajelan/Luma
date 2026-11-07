# FX04 targeted emulator check

Date: 2026-11-07 · Device: `emulator-5554` · Result: **PARTIAL — timer expiry smoke and deleted-session cleanup retest passed; full acceptance pending**

## Run

Used the already-installed Luma app. Android CLI reported `1.1.16717390`; ADB reported `1.0.41`. The installed package ID, build variant, APK hash, and source revision were not verified, and the app was not rebuilt or reinstalled.

Configured a synthetic one-minute timed session (`FX04 moke test`). The UI accepted the 1-minute minimum after showing the expected range validation for an empty duration. The active screen showed a countdown and Pause, Stop, and Emergency exit controls. With the screen off for 60 seconds, waking the emulator displayed **“Focus target reached and session saved”** and `01:00`. Focus history showed one credited run from 11:02:06 AM to 11:03:06 AM, total `01:00`.

Deleted the synthetic QA session using the confirmed history action. Existing history rows remained, and Today returned to its prior one-session count.

## Follow-up observation and retest

The initial cleanup reproduced a stale-state bug: after deleting the synthetic timed row and backing out of history, the cached completion screen still showed that session and displayed **“Could not load the latest saved reflection. Your focus time is safe.”** Selecting Done returned to Today without a crash.

The completion flow now treats a stable-generation lookup that finds no terminal row as deletion, clears only the matching cached terminal snapshot, and returns from Focus. The production source compiled, and Android Studio deployed `com.luma.lifeos.qa` to `emulator-5554`. I created a synthetic count-up session credited at `00:55`, opened history, confirmed deletion, and backed out. The app returned directly to Today, the pre-existing one-session total remained, and neither the stale completion screen nor the reflection-load error appeared.

## Coverage limits

This remains targeted evidence, not FX-Q03/FX-Q04 acceptance. Pause/resume, early Stop, 25-minute fixture T, background alert delivery, process death, force-stop, reboot, wall-clock changes, expiry races, Room persistence assertions, and the rest of integrated FX-QA remain untested. The focused instrumentation suite could not compile because unrelated test sources still have API mismatches in app-lock, Focus UI, and widget tests. The production compile and manual emulator retest passed; FX04 and integrated FX-QA remain partial/not run.

Temporary screenshots and the detailed ad-hoc run report are under `/tmp/android-journeys/fx04-20261107/`: [active timer](</tmp/android-journeys/fx04-20261107/screen_20.png>), [target reached](</tmp/android-journeys/fx04-20261107/screen_22.png>), [history detail](</tmp/android-journeys/fx04-20261107/screen_25.png>), [post-deletion completion error](</tmp/android-journeys/fx04-20261107/screen_28.png>), [final Today screen](</tmp/android-journeys/fx04-20261107/screen_29.png>), and [journey report](</tmp/android-journeys/fx04-20261107/report.md>).