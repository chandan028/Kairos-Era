# Architecture

Kairos Era is a single Android app with no backend. Every byte of user data lives in the app's private storage.

## Layers

```
UI (Jetpack Compose screens)
  ↓
ViewModel (StateFlow of UiState: Loading / Ready / Error)
  ↓
Use cases (:domain, pure Kotlin)
  ↓
Repository interfaces (:domain)  ←  Room / DataStore implementations (:app data layer)
  ↓
Local storage (Room database, DataStore preferences, bundled assets)
```

## Modules

| Module | What it holds | Why separate |
|---|---|---|
| `:domain` | Models, recurrence engine, reminder planner, quote selection, validation, use cases, repository interfaces | Pure Kotlin with no Android dependency, so the time-critical logic is unit-tested on the JVM in seconds |
| `:app` | Room database, repositories, notification scheduler, Compose UI, resources | Everything that touches Android |

## Packages in `:app` (`com.kairosera`)

- `core/database` – Room entities, DAOs, the database and its migration list.
- `core/notifications` – `NotificationScheduler` (the only owner of alarms and notifications), channels, receivers.
- `core/settings` – DataStore preferences (theme, language-independent settings, Home card order).
- `core/diagnostics` – `SafeLog`, the only logging entry point (redacted by design).
- `core/ui` – theme and shared components.
- `data/repository` – Room implementations of the domain repositories plus mappers.
- `data/quotes` – loads the 365 bundled quotes.
- `data/sample` – default categories and optional example content (tasks, a fitness and a study tracker, a book).
- `data/tracker` – tracker templates (study, fitness, reading, habit, project, money, learning, health, personal, custom).
- `feature/*` – one package per screen group (home, planner, tasks, track, read, more, onboarding, widgets).
- `AppContainer` – manual dependency wiring; small enough that a DI framework isn't worth the dependency.

Features never read another feature's tables directly: they go through repositories and use cases.

## Key design decisions

- **Tasks are definitions; occurrences are computed.** A recurring task stores its rule once. Per-date state (done / moved) is stored in `occurrence_states`. The rule and history never overwrite each other, yesterday stays intact, and no "daily reset" ever deletes anything.
- **Dates are local calendar dates; reminder times are floating local times.** "08:00" means 08:00 wherever the phone is. Only trigger instants depend on the time zone, and they are recomputed on every rebuild.
- **The reminder schedule is derived, not remembered.** `ReminderPlanner` is a pure function of the database and the clock. See `docs/NOTIFICATIONS.md`.
- **Enums are stored by name, dates as epoch days, times as minutes of day.** Reordering an enum can't corrupt data and range queries use indexes.
- **One tracker engine.** Study, fitness, habits and custom trackers are the same thing: a tracker with typed fields (yes/no, number, decimal, minutes, pages, percent, rating, text, checklist) and a frequency (daily, some days, N per week, N per month). Templates only pre-fill fields. Values are stored one row per field per day (`tracker_values`), so editing a tracker never rewrites history, and removing a field only switches it off. The first field is the main goal: completing it completes the day. Streaks count days, weeks or months depending on the frequency; today not done yet never breaks a streak.
- **Study plans are separate from daily logs.** A study tracker has a topic tree (`study_topics`) and per-day targets (`study_targets`). Ticking a target records practice on its topic and moves it to "learning"; only the user marks a topic done.
- **Books are more than pages.** `books` holds progress plus the READ → UNDERSTAND → REMEMBER → APPLY insight fields; `reading_sessions` and `book_notes` hang off it. A session moves the bookmark in the same transaction and never past the last page.
- **Soft delete everywhere.** Deleting moves an item to Trash. Permanent deletion needs an explicit confirmation.
- **Adaptive layout.** `NavigationSuiteScaffold` shows a bottom bar on phones and a navigation rail on tablets, foldables and landscape. Content is capped at a readable width and centred. The app draws edge to edge and respects system bar and cutout insets.
- **Design tokens in three layers.** `Palette` holds raw colors; the semantic layer (the Material 3 color scheme plus `KairosColors`: brand, sun, card, line, track, muted and five tones — success green, info blue, motivation amber, learning purple, activity coral — each with a strong and a soft shade) is what components read through `Kairos.colors`. Components never use raw hex, so light and dark themes change in one place. Type uses few sizes: a Lora serif for heroes and headings, the system sans for everything else. Spacing (`Space`) and corner radii are fixed scales.
- **Feedback is a Kairos toast.** One `Toaster` (`LocalToaster`) above the navigation shows a navy capsule with a semantic icon (success, info, undo, error), an optional action such as Undo, and auto-dismiss. It is built on Material 3's `SnackbarHost`, so it is announced by screen readers and respects system timing.
- **Widgets are plain RemoteViews.** Four `AppWidgetProvider`s share one `WidgetSnapshot` read from the repositories. While the app process is alive and a widget is placed, `Widgets.startSync` follows the database so widgets change as soon as data does; otherwise they refresh every 30 minutes and on date, time-zone and language changes. Ticking a task in the widget goes through a non-exported receiver and the same `SetOccurrenceDone` use case as the app. No new permissions, no widget library.
- **Onboarding is resumable.** `OnboardingRepository` keeps the current step and answers in a separate small DataStore as they change, so process death or leaving the app returns to the same step. Finishing creates the first task (with a reminder only if the person allowed reminders and chose a time) and one starter tracker per chosen focus area (study, habit, daily movement), then marks onboarding done and clears the draft. The night-stage colors live in `Stage`, and the scenery is a vector `SunriseScene`.
- **Languages.** English and Kannada. The in-app switch uses AppCompat per-app locales, so it works on Android 8+ and appears in Android 13+'s per-app language settings. All UI text lives in `tools/strings_source.py`, which generates both `strings.xml` files and fails the build script if a key or placeholder is missing in either language.

## Dependencies

Each dependency in `gradle/libs.versions.toml` has a one-line reason. There are no analytics, ads, crash-reporting SDKs or network libraries.
