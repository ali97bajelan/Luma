# Current-source minified-release smoke — API36

- Artifact: `appLockQaRelease` minified unsigned APK, SHA-256 `ba6ddfc0ae7ba03b25b893639d1aea270dd57665222428cc10be60a810ac10de`.
- A temporary copy was signed with the local Android debug key (certificate SHA-256 `773fe3131f919d63669fde840f4e46553f5d9b24d9a29771127c6585b58c1011`; signed-copy SHA-256 `fe348090254973e59738cb746e754756b04f2a3849b3129e58aced396a774ed3`). It was installed only as `com.luma.lifeos.applockqa`.
- On `emulator-5554` / Android 16 API36 (1080x2400, 420 dpi), the minified app launched `com.luma.lifeos.MainActivity` and displayed Today. Navigation through More → Settings → Privacy → App lock settings succeeded, and the setup screen showed Whole app / Every time plus both password fields.
- The current-source release smoke is **partial**: the startup and route-to-setup path passed, but this run has no usable observation for submitting setup, cold-starting into the release lock gate, or unlocking the release build. Do not count those as passed. The app-lock debug instrumentation gate regression and manual Q02 remain separate evidence.
- Cleanup removed the temporary signed APK, emulator dumps and isolated release package. Package inventory retained `com.luma.lifeos` and `com.luma.lifeos.qa`; the personal app process remained running. No recovery code was copied or retained in workspace evidence.

This smoke confirms current-source C5 startup and route reachability only; locked-state minified-release behavior remains open.
