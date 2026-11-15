# Android build verification

Date: 2026-11-14

## Temporary toolchain

- JDK: Eclipse Temurin 17.0.21+9, portable ZIP extracted under `%TEMP%\reptrack-android-toolchain`; SHA-256 matched Adoptium package metadata (`98518ecf554465057ee5509462631deeee374bc368c12a411d225cd29381f84b`).
- Android command-line tools: Windows archive `commandlinetools-win-15859902_latest.zip`; SHA-256 matched Android Developers' published checksum (`90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a`).
- SDK packages installed under the same temporary root: `platforms;android-36`, `build-tools;36.0.0`, and `platform-tools`. SDK package licenses were accepted for the temporary SDK.
- Gradle Wrapper: 8.11.1, with `GRADLE_USER_HOME` in `%TEMP%\reptrack-review-gradle`.
- No global Java/Android settings or project `local.properties` were added. No secrets or user databases were read.

## Build and tests

Ran from `android/` with temporary `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, and `GRADLE_USER_HOME`:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

Result: **BUILD SUCCESSFUL**, 67 tasks, exit code 0. Main Kotlin sources, JVM tests, and Android instrumented-test sources all compiled.

JVM unit results: **11 tests, 0 failures, 0 errors, 0 skipped** across `DiaryTransferTest` (3), `SecureExchangeTest` (1), and `TrainingSessionTest` (7).

Artifacts, ignored by `android/.gitignore`:

- `android/app/build/outputs/apk/debug/app-debug.apk` — 9,906,579 bytes.
- `android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` — 380,325 bytes.

The SDK command-line tools emitted a non-fatal warning that they support SDK XML through version 3 while encountering version 4. All requested build tasks nevertheless passed.

## Instrumented test availability

`adb devices -l` returned no attached devices. Windows `systeminfo` reports `VM Monitor Mode Extensions: Yes`, but `Virtualization Enabled in Firmware: No`. No BIOS, Hyper-V, or system setting was changed.

After the initial build, a temporary API 36 x86_64 AVD was created under `%TEMP%\reptrack-android-toolchain\avd`. The official Emulator 37.2.13 and `google_apis;x86_64` image were installed there. Emulator `-accel-check` reports the Android Emulator hypervisor driver is not installed. Attempts with `-accel off` left `emulator-5554` offline without boot completion; a subsequent `-accel off -gpu off -show-kernel` attempt exited with Windows status `0xC0000005`. The saved log for `-gpu off` says the configured in-guest renderer is unsupported by that image and falls back to lavapipe. No emulator process remains. `:app:connectedDebugAndroidTest` was not run.

Android's command-line documentation describes `-accel off` as unsupported and very slow software emulation, requiring `ANDROID_I_WANT_MY_TCG=yes`; the acceleration documentation says Windows acceleration uses WHPX or AEHD. With firmware virtualization disabled and the observed TCG boot/crash behavior, there is no confirmed software-only route for running this instrumented suite in the current environment. See [emulator command-line options](https://developer.android.com/studio/run/emulator-commandline) and [emulator hardware acceleration](https://developer.android.com/studio/run/emulator-acceleration).

After enabling virtualization in firmware or connecting an Android device, run the instrumented regression suite from `android/`:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.reptrack.DiaryRepositoryInstrumentedTest
```

Building the instrumented APK confirms compilation only; it does not confirm database behavior on an emulator/device.
