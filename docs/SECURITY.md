# Security

## Threat model

| Threat | In scope | Mitigation |
|---|---|---|
| Another app reading Kairos Era data | Yes | Data lives only in app-private storage; no exported providers; receivers for our own intents are not exported |
| Data leaving the device silently | Yes | No INTERNET permission at all; no analytics, ads or crash SDKs; Android cloud backup and device transfer excluded |
| Personal text in logs | Yes | `SafeLog` only accepts numbers, booleans and enum names and prints `<redacted>` for anything else; exception messages are never logged; release builds strip debug logging |
| Lock-screen shoulder surfing | Yes | Notifications are `VISIBILITY_PRIVATE` with a generic public version |
| Someone with the unlocked phone | Partly | Optional app lock using device biometrics / credential is planned (phase 4) |
| A rooted or forensically imaged device | No | Out of scope; see encryption notes below |

## Current state (phase 1)

- Permissions: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`. Nothing else.
- Exported components: `MainActivity` (launcher) and `SystemEventReceiver`, which only acts on a fixed set of protected system broadcasts and validates the action.
- All `PendingIntent`s are `FLAG_IMMUTABLE`, explicit, and carry only a numeric id that is range-checked on receipt.
- Launch intents are parsed defensively (`MainActivity.parse`): unknown actions are ignored and dates are range-checked.
- Database queries are Room-generated with bound parameters. The one `LIKE` search escapes `%`, `_` and `\`.
- `allowBackup=false` plus explicit exclusion rules. See `docs/BACKUP_SECURITY.md`.
- R8 minification and resource shrinking are on for release builds.

## Encryption decision (to be implemented with backups and app lock)

App-private storage is already protected by Android's file-based encryption while the phone is locked. Field-level encryption with a Keystore key will be applied to the most sensitive free text (journal and notes) when the journal lands. Keys will live only in Android Keystore; nothing is hard-coded, stored in preferences, or committed. Backups will be encrypted with a user-chosen passphrase because Keystore keys cannot leave the device; the app will warn clearly that a lost passphrase means that backup can't be opened.

## Checks to run before each release

See the release checklist in `docs/TESTING.md`.
