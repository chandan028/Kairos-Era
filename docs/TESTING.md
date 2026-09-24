# Testing

## Running

```
./gradlew :domain:test              # pure logic: recurrence, reminders, quotes, use cases
./gradlew :app:testDebugUnitTest    # JVM tests incl. a Robolectric end-to-end smoke test of real screens
./gradlew :app:assembleDebug
python3 tools/validate_quotes.py    # 365 quotes: count, days, length, categories, banned phrases, duplicates
python3 tools/gen_strings.py        # regenerates strings; fails if English and Kannada keys/placeholders differ
```

If Maven Central rate-limits your network, set `robolectricRepoUrl=<mirror>` in `~/.gradle/gradle.properties` for Robolectric's Android jars.

## What is covered today

| Area | Test |
|---|---|
| Recurrence (daily, weekly, weekdays, monthly clamping, yearly Feb 29, intervals, end date, count) | `RecurrenceEngineTest` |
| Reminder planning: idempotent ids, no duplicates, missed-reminder grace, DST gap, clock and time-zone changes, deleted task/reminder cancels, snoozed/showing rows kept | `ReminderPlannerTest` |
| Occurrence expansion and ordering, day progress | `OccurrenceExpanderTest` |
| Quote rotation (Sep 23 = #266, leap days), validator | `QuoteSelectorTest` |
| Tracker scoring (checkbox, targets, checklists, text never counts, disabled fields), streaks for daily / selected days / weekly targets, "missed yesterday", study progress by leaf topics, targets never mark a topic done, topic cycles, reading bookmark bounds and pages-per-day | `TrackerLogicTest` |
| Database v1 → v2 upgrade keeps tasks, reminders and occurrence state; Room validates the migrated schema; tracker entries replace a day atomically; removed fields keep history; Trash restore and delete-only-from-Trash; study target → topic practice; reading sessions move and restore the bookmark | `MigrationTest` (Robolectric) |
| First run, five-step onboarding (skip focus, first task, reminders declined, back keeps answers), Home, Today (timeline and calendar), create task, inline validation, Settings, Trash, Track (example trackers, logging a day, new tracker from a template, study cockpit), Read (current book, status changes), Home trackers | `AppSmokeTest` (Robolectric) |
| Kannada first screen, tablet landscape | `LocaleAndLayoutTest` (Robolectric) |

## Design review screenshots

`ScreenshotTour` walks every main screen with the example data and renders the four widgets, saving PNGs. It is skipped unless a folder is given:

```
./gradlew :app:testDebugUnitTest --tests '*ScreenshotTour*' -Pscreens=/some/dir
```

It captures the main window only, so bottom sheets (Quick add, template picker, Reschedule) are checked by hand.

## Planned with later phases

Backup export/import/corruption, app lock, and on-device instrumentation tests for notification permission denial, exact alarm unavailable and reboot recovery.

## Release checklist

- [ ] `:domain:test`, `:app:testDebugUnitTest` and `:app:lintRelease` pass
- [ ] `./gradlew detekt` clean or findings documented
- [ ] Quotes validator and strings generator pass
- [ ] `aapt2 dump badging` shows only the three expected permissions and no INTERNET
- [ ] Exported components reviewed in the merged manifest
- [ ] Backup rules unchanged or re-reviewed
- [ ] No secrets: `git grep -iE "api[_-]?key|secret|password|BEGIN (RSA|EC) PRIVATE"` is empty
- [ ] Release build minified, installs and runs; signed with a key kept outside the repo
- [ ] Airplane-mode pass over every screen
- [ ] By hand: dialogs with text fields (Log reading, Add note, Add topic, Add target, Edit field). Robolectric can't drive text fields inside dialog windows at phone densities, so these are not in `AppSmokeTest`
- [ ] Upgrade install over the previous release keeps every task, tracker and book
- [ ] By hand: onboarding on Android 13+ (Allow reminders shows the system dialog once; denying continues; a second pass never asks again), kill the app mid-onboarding and reopen to the same step, and the flow at the largest font size
- [ ] By hand, on a phone: swipe a Today card right to complete and left for Reschedule / Delete (Undo toast), long-press menu, the completion tick, the splash once per cold start, and each widget (add, tick a task, resize, dark mode, Kannada)
- [ ] Reminders survive reboot, time change and time-zone change on a real device
