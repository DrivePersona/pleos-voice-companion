# Pleos runtime startup

## Implemented entry points

- **Launcher Activity:** starts `CompanionRuntimeService` with `ACTION_SETTINGS`, then shows the existing explicit setup/recovery screen.
- **VR start:** `CompanionRuntime.beginVr()` hides setup state and shows the character when policy permits; the Activity finishes. The started service keeps the runtime alive independently of that Activity.
- **Android system boot:** `CompanionBootReceiver` handles `BOOT_COMPLETED` and starts the service with `ACTION_BACKGROUND`. It never launches an Activity. A sticky service restart with a null intent also restores the background session.

`startBackgroundSession()` marks settings as hidden before starting the runtime. On Automotive, the runtime defaults to driving until `CarUxRestrictions` supplies its current value. The overlay remains hidden while state is unknown or driving, and can appear only after parked is confirmed, the selected character pack is ready, VR is enabled, and overlay access is available. The selected character is restored from app preferences; its paired voice pack is derived from the character manifest.

## Platform boundary

The public [Pleos app structure and screen model](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model) describes foreground/background screen transitions. The public [Pleos app launch/debug guide](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/adb-command-reference-guide) describes launching an app through the app launcher or an explicit Activity. The reviewed public Pleos documentation does not define an ignition callback or guarantee that third-party apps receive `BOOT_COMPLETED` on every vehicle start.

Therefore, `BOOT_COMPLETED` is implemented as the standard Android cold-boot entry point and must be verified on the target image. Android lists this action as an implicit-broadcast exception ([broadcast exceptions](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions)); that does not make it a Pleos ignition signal. If the vehicle suspends and resumes Android without rebooting, this receiver will not run again. Automatic startup on every ignition still needs a Pleos product-level app launch contract or an explicit approved lifecycle hook.

## Background-service behavior and limits

The service is a regular started service with `START_STICKY`; no foreground-service notification or guessed service type was added. Android's [service guidance](https://developer.android.com/develop/background-work/services) documents background-service limits, so this implementation is sufficient only if target-image testing confirms the runtime remains active. Android may reclaim a non-foreground service under resource pressure, and sticky restart is best-effort. If the Pleos image stops the service while the device remains booted, the target platform's background-execution policy must be resolved before calling this production-ready.

The receiver runs only after Android boot and user unlock. Android also suppresses boot receivers for a force-stopped package until the user launches the app again. The launcher Activity remains available for explicit recovery and status checks. Missing `SYSTEM_ALERT_WINDOW` access is handled by the existing runtime gate; it does not open settings automatically or launch the Activity.

## Verification results — 2026-11-06

Build and unit-test results from this branch:

- `:app:testReviewDebugUnitTest`: **58 passed, 0 failed**.
- `:app:assembleReviewDebug`: **passed**.
- `:app:assembleInternalDebug`: **passed** using the already-published local Maven artifacts for the private pack SDKs. This isolated worktree had no GitHub Packages credentials; remote dependency resolution returned HTTP 401, and no credentials were added to the repository.

Pleos Connect v2.1.0 AVD (`Pleos_Connect_v2_1_0`, Android user 10, internal build, overlay app-op allowed):

- **Cold boot:** `BOOT_COMPLETED` was queued behind other system receivers and delivered after the boot queue progressed. `CompanionBootReceiver` started `CompanionRuntimeService` with `START_BACKGROUND`; logs recorded `runtime started`. The service was `startRequested=true`, `MainActivity` was not resumed, and exactly one `VoiceCompanionCharacterOverlay` appeared while the vehicle reported P. The selected GLB rendered over the Pleos HMI without a black surface or clipping. Evidence: [`issue36-pleos-recovered-final.png`](mockups/issue36-pleos-recovered-final.png).
- **Driving gate through the existing Virtual Console:** selected gear D in `http://127.0.0.1:8088/`. The console reported `DRIVE ACTIVE` at 0 km/h. The companion service remained started, the HMI stayed in front, and the overlay window count changed from 1 to 0. Evidence: [`issue36-pleos-driving.png`](mockups/issue36-pleos-driving.png).
- **Parked return:** selected P in the same console; it reported `PARKED`. The overlay was still absent in the first check about two seconds later, then returned as one window during the following six-second wait. This is a measured observation window, not a precise latency benchmark; the return delay should be tracked before production release. Evidence: [`issue36-pleos-parked-after-wait.png`](mockups/issue36-pleos-parked-after-wait.png).
- **Setup and character recovery:** tapping the visible character opened `MainActivity` and removed the overlay window. Pressing `VR 시작` finished the setup Activity and restored one overlay while the Pleos HMI remained resumed. Evidence: [`issue36-pleos-character-tap.png`](mockups/issue36-pleos-character-tap.png) and [`issue36-pleos-restore-final.png`](mockups/issue36-pleos-restore-final.png).
- **Duplicate check:** after the boot path and the Activity → `VR 시작` path, `dumpsys` showed one active `CompanionRuntimeService` record and one overlay window. A direct shell attempt to start the service twice was rejected because the service is `exported=false`; this was not counted as a repeated-start stress test.
- **Process-death test:** an app-only crash/restart was not isolated. The AVD disconnected during the `am crash` attempt, so it was cold-booted again; the boot receiver then restored the service and one overlay. This verifies boot recovery again, but does **not** verify Android's `START_STICKY` process-only restart path.

Issue #36 remains open for the unverified process-only sticky restart and a deliberate unknown-`CarUxRestrictions` startup check. Real-vehicle behavior and product approval for `SYSTEM_ALERT_WINDOW` also remain outside this AVD verification.

## Pleos AVD verification record

Use the Pleos Connect AVD only (not a phone AVD). Record the image version and capture a screenshot with the HMI visible, the selected character overlay present, and no companion Activity.

1. Install and launch the internal/debug build once. Select a character, enable VR, and verify that the selected character and overlay preference persist.
2. Grant overlay access in the emulator setup used for this test. The Pleos image used in earlier checks had no usable permission settings screen, so prior tests used `adb appops`; this is AVD configuration, not a product UI flow.
3. Cold-boot the AVD. Capture the boot/service log and confirm the receiver starts one runtime service without starting `MainActivity`.
4. **Verified for P ↔ D:** the existing Virtual Console's D control made the overlay disappear while HMI stayed visible; its P control restored one overlay after a short observed delay. The unknown restriction state was not isolated.
5. **Verified:** tapping the overlay opened the recovery Activity and hid the window; `VR 시작` closed the Activity and restored the overlay.

Useful diagnostics:

```powershell
adb logcat -s VoiceCompanionBoot:I VoiceCompanionService:I VoiceCompanion:I
adb shell dumpsys activity services com.example.voicecompanion
adb shell dumpsys window windows
```

Recorded AVD evidence:

- Pleos image / AVD: Pleos Connect v2.1.0 / `Pleos_Connect_v2_1_0` / Android user 10.
- Boot receiver and service log: `CompanionBootReceiver` started `START_BACKGROUND`; service record remained started; no companion Activity was top-resumed.
- HMI plus overlay screenshot: [`issue36-pleos-recovered-final.png`](mockups/issue36-pleos-recovered-final.png).
- Driving → parked behavior: D hid the overlay; P restored one overlay after a short delay. Unknown state not isolated.
- Duplicate service/window check: one service record and one window after boot and Activity recovery; repeated external service start was blocked by `exported=false`.
- Explicit Activity recovery check: character tap opened settings and hid overlay; `VR 시작` closed settings and restored the overlay.
- Remaining limitation: process-only `START_STICKY` restart, unknown restriction startup, real vehicle, and Pleos product approval for overlay access.
