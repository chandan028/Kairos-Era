# Notifications and time correctness

## The model

```
Reminder (belongs to a task)
  ↓  ReminderPlanner: next occurrence that is still open
ScheduledNotification row  (id = Android notification id = alarm request code)
  ↓
AlarmManager alarm  →  ReminderReceiver  →  notification  →  Done / Snooze / Reschedule
```

Each `(reminder, occurrence date)` maps to exactly one row in `scheduled_notifications`. The row id is used as the notification id and the PendingIntent request code, so rebuilding the schedule any number of times never creates duplicates.

Row statuses: `SCHEDULED`, `SNOOZED`, `SHOWING`, `DONE`, `DISMISSED`, `CANCELLED`.

## One entry point: rebuild

`NotificationScheduler.rebuild()` reads tasks, reminders, occurrence states and existing rows, asks the pure `ReminderPlanner` what should exist, and applies the difference:

- arms alarms that should exist (re-arming the same id is harmless),
- cancels alarms and notifications whose task or reminder was deleted, completed or changed,
- re-posts `SHOWING` notifications the system dropped (for example after a reboot).

It runs:

| Trigger | Where |
|---|---|
| App start (consistency check) | `KairosApp.onCreate` |
| Any task change (save, complete, move, trash, restore) | use cases' `onRemindersChanged` |
| Reboot | `BOOT_COMPLETED` |
| App update | `MY_PACKAGE_REPLACED` |
| Clock change | `TIME_SET` |
| Time-zone change | `TIMEZONE_CHANGED` |
| Date change | `DATE_CHANGED` |
| Language change (channel names) | `LOCALE_CHANGED` |
| Exact-alarm permission granted | `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` |
| An alarm fires (arms the next occurrence) | `ReminderReceiver` |

Nothing depends on an in-memory timer. Process death loses nothing because the database is the source of truth.

## Time rules

- Reminder times are floating local times: after a time-zone change, 08:00 still means 08:00 local.
- Daylight saving: a time inside a DST gap moves forward by the gap; in an overlap it fires once, at the first 08:00.
- A reminder missed while the phone was off still fires on boot if it is at most 12 hours late. Older ones are skipped, never fired in a burst.
- A reminder created after its time has already passed today does not fire immediately; the next occurrence is armed.

## Permissions and fallbacks

| Situation | Behaviour |
|---|---|
| Notifications denied | App works normally; Settings shows the state and a button to allow. Rows are still tracked so reminders appear once allowed. |
| Exact alarms not allowed (Android 12+) | Falls back to `setAndAllowWhileIdle`, which Android may deliver a few minutes late. Settings explains this and links to the permission screen. |
| Battery optimisation | Settings explains it and links to the system screen. The app never runs a background service. |

## Channels and sounds

Android owns each channel's sound and importance once it exists, and the user can change them in system settings. Kairos Era works with that model instead of overriding it: each built-in sound has its own channel (`reminder_default`, `reminder_bell`, `reminder_chime`, `reminder_focus`, `reminder_silent`) and a reminder picks a channel. Channels are created once; re-creating only refreshes translated names.

Built-in sounds (Kairos Bell, Soft Chime, Focus) are original tones synthesised by `tools/gen_sounds.py`. They are referenced by resource *name* (`android.resource://<package>/raw/kairos_bell`), never by numeric resource id, which is not stable across upgrades.

Custom sound files are not enabled yet. When they are, the file will be copied into app storage and exposed through a content URI, and any missing file will fall back to the default channel. The `CUSTOM` value already maps to the default channel so an unknown sound can never break a notification.

## Persistent reminders

A reminder marked "Keep until I act" is posted as an ongoing notification with three actions: Done, Snooze and Reschedule. This is a legitimate active state: the user asked to be kept aware until they act. There is no foreground service and no fake alarm clock. On Android 14+ the user can still swipe it away; that is respected (status `DISMISSED`, never re-posted).

On the lock screen, notifications show only "Kairos Era reminder" (the public version); the task title is visible only when unlocked.
