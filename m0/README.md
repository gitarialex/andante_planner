# Andante Planner M0 reliability spike

This is a throwaway Expo development-build app. It is not the Andante Planner
application and must not be used as its production code.

## Requirements

- Windows 11, Node.js 24 and JDK 17.
- Android SDK and platform tools installed and available on `PATH`.
- An Android device connected with USB debugging enabled.

## Build and install locally

From this directory:

```powershell
npm install
npx expo run:android
```

This generates the Android project with Expo Prebuild, builds it locally,
installs it on the connected device and starts Metro. It does not use EAS or a
cloud build. To run Metro again after installation:

```powershell
npx expo start --dev-client
```

The app is a development client, not Expo Go. The development variant can use
the network permission required to load JavaScript from Metro. The Gradle
release manifest overlay removes `android.permission.INTERNET`; verify it on a
release manifest with:

```powershell
cd android
.\gradlew.bat :app:processReleaseMainManifest
```

No analytics, cloud SDK or network service is used by the spike.

## Phone checks

Open the app and complete Test 9 first. Grant notification permission and use
the two settings buttons to grant exact-alarm access and, if available, the
battery-optimization exemption. Return to the app to read each status back.

Each test button creates the test data and logs what was scheduled. Verify the
criteria in `RESULTS.md` on the phone. For Test 2, reboot without force-stopping
the app. For Test 3, swipe the app away from Recents, wait for the notification,
tap Done, then reopen the app. Do not use Android Settings' Force stop action:
Android suppresses boot broadcasts to force-stopped apps until they are opened
again.

Test 4 stores a local wall-clock target and rebuilds its UTC alarm after
`TIMEZONE_CHANGED`. Change the device timezone while that event is pending, then
verify that the event follows the same local wall-clock time in the new zone.
Test 5 is a manual battery-saver/background restriction check; the screen
reports the system restriction signals it can read, but cannot detect every
manufacturer-specific background policy.

For Test 7, add the **Andante M0 Spike** widget from the launcher's widget
picker. The Kotlin widget reads the Room database and completes its open task
through the same task service used by notification actions.

The app creates the five channels from SPEC Section 8.2: Reminders, Critical,
Nudges, Digest and Review. Critical, Digest and Review channels are present for
catalogue verification; this spike does not exercise all production
notification types.

## Glance integration choice

Expo's current local-module flow uses `npx create-expo-module@latest --local`
and autolinks the module under `modules/`. Glance requires Android widget
receivers, provider metadata and native Kotlin code, so this spike registers
those directly in the local module's Android manifest and resources. No
separate config plugin is used for Glance; the small app config plugin only
adds the release manifest permission-removal overlay.

The app uses React Native primitives for the temporary test controls. This is
not the Test 10 component-library evaluation; leave that decision open until
the manual evaluation is recorded in `RESULTS.md`.

## Android behavior not yet verified

The attached test phone reports Android 16 / API 36, manufacturer vivo, model
V2427. A successful local build or emulator run does not prove exact-alarm,
Doze, reboot, notification, timezone or manufacturer battery behavior on that
phone. Record actual outcomes and timing in `RESULTS.md`. No second-brand
device has been tested by this setup.

Official references consulted:

- [Expo local modules](https://docs.expo.dev/modules/get-started/)
- [Android alarms](https://developer.android.com/develop/background-work/services/alarms)
- [Exact-alarm permission changes](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)
- [Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Jetpack Glance](https://developer.android.com/develop/ui/compose/glance)
