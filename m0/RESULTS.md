# M0 reliability spike results

Fill in **Result** and **Notes** from tests on the physical phone. Do not treat
build success or emulator behavior as a pass for an on-device criterion.

| # | Pass criterion (SPEC Appendix D) | How to run | Result | Notes |
|---|---|---|---|---|
| 1 | Schedule an exact alarm 2 minutes ahead; turn the screen off; swipe the app away. A notification appears within 60 seconds of the target time, 5 times out of 5. | Grant notification and exact-alarm access. Tap **1. Exact alarm: +2 minutes**; turn off the screen and swipe the app from Recents. Repeat five times, recording target and arrival times. | Tested | All five alarms arrived on time|
| 2 | Schedule five alarms at different times, then reboot the phone. The boot receiver restores them and all five fire on time. | Tap **2. Five alarms, then reboot**. Reboot without force-stopping the app. Record each scheduled and actual fire time. | Tested | Alarms fired on time |
| 3 | Tap Done on a notification with the app closed, then open the app. The database shows the task done; the UI matches. | Tap **3. Done action with app closed**. Swipe the app from Recents. Tap Done on its notification, reopen the app, and verify status. | Tested | Status indicates that task is done after reopening the app |
| 4 | Change the phone's timezone with reminders pending. Reminders move to follow the new timezone. | Tap **4. Timezone change**. Change the system timezone while the event is pending. Verify its target remains at the same local wall-clock time in the new zone. | Tested | Target remained the same as the local wall-clock time in the new zone |
| 5 | Turn on battery saver and restrict the app's background use; run tests 1 and 2 again. Reminders still fire, or the health check detects and reports the restriction. | Tap **5. Battery restrictions** and record the displayed power-saver, app-restriction and battery-exemption signals. Enable battery saver/restrictions, then repeat Tests 1 and 2. | Tested | All alarms fired on time |
| 6 | Run a nudge chain of four nudges at 30-minute (shortened to 2-minute for the test) intervals. Exactly four nudges, correct spacing, then it stops; Stop nudging halts it at once. | Tap **6. Four-nudge chain**. Verify four notifications spaced two minutes apart and no fifth. On a fresh chain, tap **Stop nudging** and verify no later nudge appears. | Tested | Behaved as expected |
| 7 | Render a minimal widget reading from the same database. Shows data and a tap completes an item. | Tap **7. Refresh widget task**. Add the spike widget from the launcher and tap Complete. Verify task status in the app. | Not tested | Widget indicates "Can't show content" |
| 8 | Call the Kotlin module from TypeScript for create, read and change events. Round trip works and screens refresh on change. | Tap **8. Create/read/change event**. Verify the new Room row appears without restarting the screen. | Tested |Tapping the task creates a new row under the 'Tasks(Room via Kotlin)' section. The rows survive a restart  |
| 9 | Check permission flows for notifications, exact alarms and battery exemption on Android 16. Each can be requested and its status read back. | Tap **9. Permission status + notification request**. Use both settings buttons, grant/deny each permission, return to the app and record each reported status. | Tested | Each required permission can be requested and its status read back |

## Test 10: manual UI-library evaluation

- Does a Material 3 React Native component library support the Material Tonal
  look with the fixed palette in SPEC Section 10?
- If it does not, what fallback should be chosen?
- Which library or fallback was evaluated, and on which device/version?

**Decision/result:** Open; not evaluated by the spike implementation.
