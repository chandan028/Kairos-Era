# Security

## Threat model

| Threat | In scope | Mitigation |
|---|---|---|
| Another app reading Kairos Era data | Yes | Data lives only in app-private storage; no exported providers; receivers for our own intents are not exported |
| Data leaving the device silently | Yes | No INTERNET permission at all; no analytics, ads or crash SDKs; Android cloud backup and device transfer excluded |
| Personal text in logs | Yes | `SafeLog` only accepts numbers, booleans and enum names and prints `<redacted>` for anything else; exception messages are never logged; release builds strip debug logging |
| Lock-screen shoulder surfing | Yes | Notifications are `VISIBILITY_PRIVATE` with a generic public version |
| Someone with the unlocked phone | Partly | Optional app lock (`core/security`): the phone's own fingerprint, face or screen lock via androidx.biometric, after a chosen delay away. The app holds no PIN or secret. On Android 13+ the recents preview is hidden while it is on. Widgets and notifications are not covered, and the Settings screen says so |
| A rooted or forensically imaged device | No | Out of scope; see encryption notes below |

## Current state (phase 1)

- Permissions: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`. Nothing else.
- Exported components: `MainActivity` (launcher) and `SystemEventReceiver`, which only acts on a fixed set of protected system broadcasts and validates the action.
- All `PendingIntent`s are `FLAG_IMMUTABLE`, explicit, and carry only a numeric id that is range-checked on receipt.
- Launch intents are parsed defensively (`MainActivity.parse`): unknown actions are ignored and dates are range-checked.
- Database queries are Room-generated with bound parameters. The one `LIKE` search escapes `%`, `_` and `\`.
- `allowBackup=false` plus explicit exclusion rules. See `docs/BACKUP_SECURITY.md`.
- R8 minification and resource shrinking are on for release builds.

## Encryption decisions

- **On the phone:** app-private storage, protected by Android's file-based encryption while the phone is locked. The app adds no layer of its own yet. Field-level Keystore encryption of journal and notes was considered and deferred: it would protect little against someone holding the unlocked phone (the app has to decrypt to show the text), and it would make the database impossible to recover if the Keystore entry is lost. The app lock covers that case instead. The privacy policy states this plainly.
- **Backups:** each `.kairos` file is AES-256-GCM encrypted with a key derived from a passphrase the person chooses (PBKDF2-HMAC-SHA256, 600,000 iterations, random 16-byte salt and 12-byte nonce per file, the header authenticated as associated data). The passphrase and key are never stored. A forgotten passphrase means that backup cannot be opened, and the app says so before saving. See `docs/BACKUP_SECURITY.md`.
- No key is hard-coded, stored in preferences or resources, or committed.

## Checks to run before each release

See the release checklist in `docs/TESTING.md`.
