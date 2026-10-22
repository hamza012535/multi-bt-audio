# Multi BT Audio hardware testing

## Status: NOT YET TESTED ON HARDWARE

This development session has no connected Tecno Spark 10 Pro. Neither CI success nor a routing API return proves simultaneous audible playback.

## Target
- Device: Tecno Spark 10 Pro
- OS: Android 13 / HiOS (reported target; verify exact build in Settings)
- Shizuku: start through Developer options > Wireless debugging, then authorize Multi BT Audio.

## Procedure
1. Install the debug APK from the GitHub Actions `android-debug-apk` artifact.
2. Pair and connect two Bluetooth speakers in Android settings.
3. Open the app, grant Nearby devices/Bluetooth permission, refresh, and select both.
4. Verify both speakers appear as connected outputs (A2DP or LE Audio).
5. Start Shizuku via Wireless debugging, authorize the app, and tap Enable Multi-Speaker.
6. Play continuous media. Confirm by listening that both speakers play at once. Record delay and sync drift.
7. Disconnect one speaker for 10 seconds, reconnect, and observe whether routing is reapplied within 15 seconds.
8. Record the exact app/notification diagnostic and logcat exception for failures.
9. Stop routing and check whether the preferred route is cleared.

## Result record
- Date/tester: NOT RECORDED
- CI build/commit: pending first workflow run
- Exact HiOS build: NOT VERIFIED
- Shizuku user service starts: NOT VERIFIED
- Two connected outputs detected: NOT VERIFIED
- Simultaneous audible playback: NOT VERIFIED
- Reconnect recovery: NOT VERIFIED
- Audio sync drift: NOT VERIFIED
- Vendor API failure: NOT VERIFIED

## Diagnostics
- `BLOCKED_OS`: Android below 12.
- `BLOCKED_API`: media strategy API not exposed.
- `BLOCKED_ROUTING_DENIED`: AudioService rejected the route.
- `BLOCKED_VENDOR_API`: hidden API, shell context, permission, or vendor implementation failed; capture logcat.
- `WAITING_OUTPUTS`: fewer than two selected speakers are exposed as connected outputs.

Only mark this feature hardware-verified after audible playback testing on the target phone. A green CI build proves compilation only.


## CI artifact and release pipeline

- Debug APK: repository Actions tab, workflow **Android CI**, artifact **android-debug-apk**.
- Android release: push a tag such as `android-v0.2.0`; workflow `.github/workflows/android-release.yml` publishes the APK and `SHA256SUMS.txt`.
- The repository already had a Windows `release.yml`; it is preserved to avoid breaking that pipeline. Android release has a separate workflow name.
- For a production signing key, configure repository Actions secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. Without these, the release variant is signed with the standard debug key for test installation only.
