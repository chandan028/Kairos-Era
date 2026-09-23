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
| First run, onboarding, Home, Today (day/week/month), create task, inline validation, Settings, Trash | `AppSmokeTest` (Robolectric) |

## Planned with later phases

Database migration tests (from the first schema change), backup export/import/corruption, trash restore, trackers, study and book progress, widget updates, app lock, and on-device instrumentation tests for notification permission denial, exact alarm unavailable and reboot recovery.

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
- [ ] Reminders survive reboot, time change and time-zone change on a real device
