# Backup and data-safety decisions

## Android system backup: off

Kairos Era holds personal text (tasks, notes, and later journal and health values). Android's automatic cloud backup would copy that to a cloud account without the user making an explicit choice for this app, and device-to-device transfer would copy it without review.

Decision:

- `android:allowBackup="false"`.
- `data_extraction_rules.xml` (Android 12+) excludes every domain from both `cloud-backup` and `device-transfer`.
- `backup_rules.xml` (Android 11 and below) excludes every domain as a second line of defence.

Trade-off: a user who resets or replaces their phone does not get their data back automatically. That is why the app's own backup matters.

## The app's own backup (phase 4)

- Format: a `.kairos` file (zip) with `manifest.json` (`backupVersion`, `appVersion`, `createdAt`, `checksum`) and one JSON file per data type.
- Saved only where the user picks, through Android's file picker. No storage permission is needed.
- Import validates the checksum and version first, then offers **Merge**, **Restore** or **Cancel**. Restore first writes an automatic safety snapshot so it can be undone.
- Optional passphrase encryption; the app says plainly that a forgotten passphrase can't be recovered.

## Data-loss rules

- No destructive migrations. Every schema version ships a migration and a migration test; an unknown upgrade path fails loudly rather than wiping data.
- Deletion goes to Trash first. Permanent deletion always asks for confirmation. Nothing is cleaned up automatically.
- Multi-table changes (saving a task with its reminders, subtasks and tags; completing an occurrence) run in one transaction.
