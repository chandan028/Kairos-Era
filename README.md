# Kairos Era

**Right time. A better you.**

A private, local-first Android app to plan your day, learn, move, read and reflect. No account, no cloud sync, no analytics, no ads, and no internet permission. Your data stays on your device.

Available in English and Kannada (switch in Settings). Works on phones, tablets and foldables, edge to edge.

## Status

Built in phases (see the product spec).

- **Phase 1 (this branch):** project setup, architecture, theme, database, adaptive navigation, Home dashboard, Today planner (day / week / month), tasks with subtasks, tags, categories, priorities, repeat rules and multiple reminders, a notification engine that survives reboot, time and time-zone changes, Trash, Settings, onboarding, 365 original daily quotes, English and Kannada.
- **Next:** generic tracker engine with study, fitness and reading templates; journal, statistics and calendar; widgets, backup/restore and app lock; diagnostics and hardening.

## Building

Requirements: JDK 17+ and the Android SDK (platform 35).

```
./gradlew :domain:test :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk` (package `com.kairosera.debug`, so it installs alongside a future release build).

## Docs

- [Architecture](docs/ARCHITECTURE.md)
- [Notifications and time correctness](docs/NOTIFICATIONS.md)
- [Security](docs/SECURITY.md)
- [Backup and data safety](docs/BACKUP_SECURITY.md)
- [Testing and release checklist](docs/TESTING.md)
- [Competitor research](docs/COMPETITOR_RESEARCH.md)
