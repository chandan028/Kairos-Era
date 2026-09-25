# Kairos Era

**Right time. A better you.**

A private, local-first Android app to plan your day, learn, move, read and reflect. No account, no cloud sync, no analytics, no ads, and no internet permission. Your data stays on your device.

Available in English and Kannada (switch in Settings). Works on phones, tablets and foldables, edge to edge.

## Status

Built in phases (see the product spec).

- **Phase 1:** project setup, architecture, theme, database, adaptive navigation, Home dashboard, Today planner (day / week / month), tasks with subtasks, tags, categories, priorities, repeat rules and multiple reminders, a notification engine that survives reboot, time and time-zone changes, Trash, Settings, onboarding, 365 original daily quotes, English and Kannada.
- **Phase 2:** one tracker engine for anything (10 templates including study and fitness, 9 field types, flexible frequency, streaks and a 5-week history), study plans with a topic tree and daily targets, and a Read tab with books, reading sessions, notes and highlights, and "what I learned / applied" insights. Home shows today's trackers and reading. Trash covers tasks, trackers and books. Database v2 with a tested upgrade from v1.
- **Redesign (0.3):** a calm "personal operating system" look: deep navy and warm cream with soft semantic colors, a Lora serif for headings, a sunrise splash, and rebuilt Home, Today (week strip, timeline, swipe to complete / reschedule / delete), Add Task, Track, Study, fitness quick log and Read screens. A full-screen daily quote, a Kairos toast for feedback with Undo, and four home-screen widgets: Motivation, Today's Tasks (tick tasks off from the home screen), Progress and Quick Add.
- **Onboarding (0.4):** five calm steps on a night-navy stage with a sunrise: what Kairos Era is, what matters to you (optional focus areas that start matching trackers), one thing for today, reminders (the Android permission is asked only when you tap Allow, and never twice), and a preview of your day. Progress is saved as you go, so leaving the app mid-way returns you to the same step.
- **Reflect (0.5):** a Journal tab (optional mood, "What happened today?", "What did I learn?", "What do I want to do tomorrow?", history by month), Statistics (7D / 30D / 90D / 1Y consistency with a clear definition of an active day, an activity map, your areas, momentum and personal milestones) and a Life Calendar (month grid with activity dots and journal markers, each day's story, a full-day timeline and a year view). Everything is derived from the data already stored; nothing is duplicated. Database v3 adds the journal with a tested upgrade from v2. Reading moved into More, where it sits beside Statistics and the calendar.
- **Privacy policy (0.5.1):** a full policy in English and Kannada under More › Privacy policy and Settings, bundled so it reads offline. `docs/privacy-policy.html` is generated from the same strings by `tools/gen_privacy_html.py`, so the published copy and the in-app copy always match.
- **Backup and app lock (0.6):** encrypted `.kairos` backup files (AES-256-GCM, passphrase you choose, saved wherever you pick), restore with a preview, a safety snapshot and Undo, and an optional app lock using the phone's fingerprint, face or screen lock after a delay you choose.
- **Diagnostics and hardening (0.7):** a crash report kept on the phone (versions and stack frames only, never messages or entries) that you can read and choose to email; a Diagnostics screen with permission checks and a database check; Delete all data behind two confirmations; the lock screen is its own window so it also covers open dialogs.
- **Next:** a polish pass.

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
