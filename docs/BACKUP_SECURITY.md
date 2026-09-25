# Backup and data-safety decisions

## Android system backup: off

Kairos Era holds personal text (tasks, notes, and later journal and health values). Android's automatic cloud backup would copy that to a cloud account without the user making an explicit choice for this app, and device-to-device transfer would copy it without review.

Decision:

- `android:allowBackup="false"`.
- `data_extraction_rules.xml` (Android 12+) excludes every domain from both `cloud-backup` and `device-transfer`.
- `backup_rules.xml` (Android 11 and below) excludes every domain as a second line of defence.

Trade-off: a user who resets or replaces their phone does not get their data back automatically. That is why the app's own backup matters.

## The app's own backup (0.6.0)

Settings › Backup and restore (also in More). Code: `core/backup`.

- **Format:** one `.kairos` file. A fixed header (magic `KAIROSBK`, format version, KDF id, iteration count, salt, nonce) followed by AES-256-GCM ciphertext. The plaintext is gzip of a JSON manifest (`backupVersion`, `appVersion`, `schemaVersion`, `createdAt`, item counts, portable settings) and a SQLite copy of the database. Nothing about the contents is readable without the passphrase, and changing any byte, header included, makes opening fail.
- **Always encrypted.** A passphrase of at least 8 characters is required; the file will usually live outside the phone (a computer, a USB drive, a cloud folder), so an unencrypted option was left out on purpose. The key comes from PBKDF2-HMAC-SHA256 with 600,000 iterations and is never stored.
- **What is included:** every data table except `scheduled_notifications`, which is derived and rebuilt after a restore. Portable preferences (name, theme, sounds, snooze, Home layout, favourite quotes). Not included: onboarding progress, the app lock and the last-backup time, which belong to the phone.
- **Saved only where the person picks**, through Android's file picker. No storage permission.
- **Restore** decrypts and checks first without touching anything: the database copy is upgraded by the app's own Room migrations (so a backup from an older version works), then `integrity_check` and `foreign_key_check` must pass. A backup from a newer app version is refused. The person sees the backup's date, version and item counts before choosing **Replace my data**, and confirms once more.
- **Undo:** before replacing anything, the current data is written to `files/safety/before-restore.kairosdb` in private storage. **Undo last restore** puts it back. Only the latest snapshot is kept.
- **All or nothing:** the replacement runs in one database transaction with deferred foreign keys, so a failure leaves the current data exactly as it was.
- **Merge** (keeping both sets) is not offered yet: ids would clash across tables, and a wrong merge is worse than none. Restore with Undo covers moving to a new phone and going back to an earlier state.
- Tests: `BackupCryptoTest` (round trip, wrong passphrase, tampering, other files, format version) and `BackupManagerTest` (exact restore and undo, a version 2 backup upgraded on restore, newer and damaged backups refused without changes).

## Data-loss rules

- No destructive migrations. Every schema version ships a migration and a migration test; an unknown upgrade path fails loudly rather than wiping data.
- Deletion goes to Trash first. Permanent deletion always asks for confirmation. Nothing is cleaned up automatically.
- Multi-table changes (saving a task with its reminders, subtasks and tags; completing an occurrence) run in one transaction.
