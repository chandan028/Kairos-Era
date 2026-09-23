# Kairos Era

**Right time. A better you.**

A private, local-first Android app to plan your day, learn, move, read and reflect. No account, no cloud sync, no analytics, no ads. Your data stays on your device.

## Status

Work in progress, built phase by phase (see the build phases in the product spec).

- `domain/`: pure Kotlin module with the recurrence engine, reminder planner (reboot, time-change and time-zone safe, idempotent notification ids), daily quote selection and task use cases. Fully unit tested and buildable without the Android SDK.
- `app/`: the Jetpack Compose Android app (in progress).

## Building

Requirements: JDK 17+ and the Android SDK (platform 35). Then:

```
./gradlew :domain:test
./gradlew :app:assembleDebug
```
