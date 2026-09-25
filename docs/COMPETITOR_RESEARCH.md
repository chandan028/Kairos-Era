# Competitor Research

Internal note for Kairos Era. Checked on **2026-09-23**. Prices and free/paid boundaries are marked **verified** only where the vendor's own page (pricing page, help centre or Play Store listing) said so on that date. Anything else is marked **not verified**. Prices are in USD as the vendor showed them and may differ by region.

## At a glance

| Product | Account needed | Where data lives | Open source | Free tier (verified?) | Paid (verified?) |
|---|---|---|---|---|---|
| Todoist | Yes | Vendor cloud | No | Yes: 5 projects, 3 filters, automatic reminders only (verified) | Pro $7/mo or $60/yr (verified) |
| TickTick | Yes | Vendor cloud | No | Yes: 9 lists, 99 tasks/list, 2 reminders/task, 5 habits (verified) | $49.99/yr (verified) |
| Structured | Not verified | Device plus vendor sync | No | Yes, most features. No recurring tasks (verified) | Monthly, yearly or lifetime. Priced by region, amount not verified |
| Loop Habit Tracker | No | On device only | Yes (GPLv3) | Everything is free, no ads (verified) | None |
| Day One | Yes, for sync | Vendor cloud, end-to-end encrypted | No | Yes: 1 photo/entry, 1 device (verified) | $49.99/yr or $74.99/yr (verified) |
| Readwise / Reader | Yes | Vendor cloud, no end-to-end encryption | No | No. 30-day trial only (verified) | $5.59/mo or $9.99/mo, billed yearly (verified) |
| Bookmory | Not verified | Device, Google Drive backup, premium sync | No | Yes, with ads (verified) | Premium price not verified |
| Google Tasks / Keep | Yes (Google) | Google cloud | No | Free (no pricing page, not verified) | None |
| Samsung Reminder | Not verified | Device, optional Microsoft To Do sync | No | Preinstalled on Galaxy phones (not verified) | None |
| Habitica | Yes | Vendor cloud | Yes | Most features free (verified on Play) | Subscription price not verified |
| Notion | Yes | Vendor cloud, offline cache | No | Free plan: 5 MB uploads, 7-day history (verified) | Plus $10 per member per month (verified) |

## Product notes

### Todoist
1. **Features:** natural-language quick add, voice capture, list, board and calendar views, labels and filters, recurring tasks, location reminders, AI assistance.
2. **Free vs paid (verified):** the free plan has 5 personal projects, 3 filter views, 1 week of activity history and automatic reminders only. Pro adds custom reminders, the calendar layout, task duration and 150 filters. Pro costs $7/mo or $60/yr. Sources: https://www.todoist.com/pricing, https://www.todoist.com/help/articles/set-up-a-todoist-pro-subscription-PLxdCDaH9 (both checked 2026-09-23).
3. **UX strength:** very fast capture. Recurrence can be typed in plain language.
4. **UX weakness:** the vendor's help page says recurring reminders are not supported on Android and that full-screen urgent alarms are iOS-only. Play reviews complain that the free plan's reminders are too limited.
5. **Privacy:** needs an account. Data is stored in the vendor's cloud. The Play data-safety label lists personal and financial info plus 7 other data types. Closed source.
6. **What Kairos should do:** make reliable reminders on Android the core feature, with recurring and exact alarms. Never put basic reminders behind a paywall.

### TickTick
1. **Features:** tasks, calendar views, a habit tracker, a Pomodoro focus timer, an Eisenhower matrix, a timeline view and Wear OS support.
2. **Free vs paid (verified):** the free plan allows 9 lists, 99 tasks per list, 2 reminders per task and 5 habits. It includes basic calendar, widgets and themes. Premium costs $49.99/yr and raises the limits to 299 lists and 999 tasks, with unlimited habits, more calendar views and task duration. Source: https://ticktick.com/about/upgrade (checked 2026-09-23).
3. **UX strength:** covers a lot in one app, and widgets work on the free plan.
4. **UX weakness:** Play reviews say it is hard to learn and that repeat and reminder settings are hard to find. The vendor has a help page on reminders that don't fire, blaming Android battery and security settings.
5. **Privacy:** needs an account and syncs to the vendor's cloud. The Play label lists location and personal info. Closed source.
6. **What Kairos should do:** show the user whether reminders are healthy (exact-alarm permission, battery optimisation) in the app itself, and keep the habit tracker unlimited.

### Structured
1. **Features:** a visual timeline for time-blocking, an inbox, subtasks, energy-based planning, an AI planner and widgets.
2. **Free vs paid (verified):** free covers the timeline, the inbox, notifications, widgets and sync. Pro adds recurring tasks, calendar and reminder import, the AI planner and custom notifications. Pro is sold monthly, yearly or once for life, with regional pricing (amount not verified). Source: https://help.structured.app/en/articles/1897986 (checked 2026-09-23).
3. **UX strength:** seeing the day as a timeline lowers decision fatigue. Reviewers say they rarely feel pushed to upgrade.
4. **UX weakness:** recurring tasks need Pro, so free users duplicate tasks by hand every day (Play review). AI planning chats are lost if you navigate away.
5. **Privacy:** the Play label says device IDs may be shared with third parties. Account and storage details were not verified. Closed source.
6. **What Kairos should do:** keep recurrence free. A timeline view of the day is worth borrowing as a pattern, without copying its look.

### Loop Habit Tracker
1. **Features:** a habit-strength score (a missed day weakens a habit but does not reset it), flexible schedules such as 3 times a week, measurable habits, one reminder per habit that can be checked or snoozed from the notification, widgets, and CSV or SQLite export.
2. **Free vs paid (verified):** free, no ads, no in-app purchases. GPLv3. Version 2.3.1 was released 2025-08-21. Sources: https://f-droid.org/packages/org.isoron.uhabits/, https://play.google.com/store/apps/details?id=org.isoron.uhabits, https://github.com/iSoron/uhabits (checked 2026-09-23).
3. **UX strength:** forgiving scoring and quick check-ins from widgets and notifications.
4. **UX weakness:** it only tracks habits. There are no tasks, no planner and no journal. Reviewers ask for weekly totals to count toward a streak (for example, 150 minutes a week) and for more colours.
5. **Privacy:** the gold standard. No account, no internet access, and the Play label says no data is collected.
6. **What Kairos should do:** match Loop's privacy stance and its forgiving scoring. Go further with weekly and monthly totals, and put habits in the same day view as tasks.

### Day One
1. **Features:** journaling with multiple journals, templates, prompts, an "on this day" view, a map, streaks, and export to PDF, JSON or plain text.
2. **Free vs paid (verified):** the free plan allows unlimited entries, 1 photo per entry and a single device, and includes end-to-end encryption. Silver costs $49.99/yr and adds sync, more media and audio. Gold costs $74.99/yr and adds AI reflection and summaries. Source: https://dayoneapp.com/pricing/ (checked 2026-09-23).
3. **UX strength:** polished, with good resurfacing of past entries.
4. **UX weakness:** Play reviews in July and August 2026 complain about a new editor that is hard to format with, and that you can't search inside a single entry. It also pushes streaks.
5. **Privacy:** sync needs an account. Sync is end-to-end encrypted, with the user keeping the key (https://dayoneapp.com/guides/day-one-sync/end-to-end-encryption-faq/). The Play label lists location and personal info. Closed source.
6. **What Kairos should do:** keep the journal optional and free of streaks. Encrypt local backups and let the user own the key. Consider linking journal entries to that day's tasks and books.

### Readwise / Readwise Reader
1. **Features:** collects highlights from many sources, spaced-repetition daily review, a read-it-later reader (articles, PDF, EPUB, RSS), an AI helper and export to note apps.
2. **Free vs paid (verified):** there is no free tier, only a 30-day trial. Lite costs $5.59/mo and Full (with Reader) $9.99/mo, both billed yearly. Source: https://readwise.io/pricing (checked 2026-09-23).
3. **UX strength:** resurfacing past highlights really does help recall.
4. **UX weakness:** it is built around highlights, not around what you did with them. It is subscription-only and cloud-first.
5. **Privacy:** needs an account and stores data in the cloud. The vendor's FAQ says it does not support end-to-end encryption (https://docs.readwise.io/faqs/privacy).
6. **What Kairos should do:** resurface the user's own "what I learned" and "what I applied" notes on a gentle schedule, stored entirely on the device.

### Bookmory (reading tracker)
1. **Features:** ISBN and barcode add, a reading timer, pages and time stats, daily and yearly goals, a reading streak, notes and quotes, OCR from photos, a random-note resurfacer, app lock and Google Drive backup.
2. **Free vs paid:** the Play listing (verified) says the free version shows ads and that Premium removes them and adds automatic multi-device sync and more themes. The Premium price is not verified; third-party sites say $3.49/mo or $30.99/yr. Source: https://play.google.com/store/apps/details?id=net.tonysoft.bookmory (checked 2026-09-23).
3. **UX strength:** clean, not a social network, strong stats.
4. **UX weakness:** reviews mention ads that crash the app. Notes are free-form, with no structure for why you started a book or what you applied from it.
5. **Privacy:** the Play label lists app activity and device IDs, and says "data can't be deleted". Backups go to Google's cloud. Closed source.
6. **What Kairos should do:** use structured insight prompts (why I started, key ideas, what I applied), have no ads, and keep backups on the device.

### Android built-ins: Google Tasks, Google Keep, Samsung Reminder
1. **Features:** Tasks has lists, subtasks, stars, due dates, notifications, a widget and Gmail integration. Keep has notes with time reminders and repeats, and those reminders also appear in Tasks and Calendar. Samsung Reminder has time and place reminders, memos and images.
2. **Free vs paid:** these apps have no pricing pages, so "free" is not verified. Google Tasks and Keep need a Google account (https://support.google.com/tasks/answer/7675772, checked 2026-09-23). Samsung Reminder is preinstalled; its cloud sync is to Microsoft To Do (https://www.samsung.com/us/support/answer/ANS10003651/, checked 2026-09-23).
3. **UX strength:** already on the phone, with very little friction.
4. **UX weakness:** a September 2026 Play review says Google Tasks reminders arrive 16 to 48 hours late. There are no habits, reading or reflection features. Google Tasks has no location reminders (Play review).
5. **Privacy:** the Google apps need an account and sync to Google's cloud. Samsung Reminder works on the device, with optional Microsoft sync.
6. **What Kairos should do:** beat them on reliability (exact alarms that survive reboots and time-zone changes, which the domain module already handles) while staying equally quick to add to.

### Habitica
1. **Features:** tasks, habits and recurring routines presented as a role-playing game, with an avatar, gold, pets, parties and challenges. Also reminders, widgets and Wear OS support.
2. **Free vs paid:** the Play listing (verified) calls it a free app with in-app purchases, and its Play header shows "Contains ads". The subscription price is not verified on an official page; a community wiki says $5/mo.
3. **UX strength:** the game loop keeps some users very engaged.
4. **UX weakness:** the game layer is noise for users who only want tracking. Missed routines cost points, which becomes pressure. Reviewers ask for a one-off purchase instead of a subscription.
5. **Privacy:** needs an account and syncs to the vendor's cloud. The Play label says personal and performance data may be shared. Open source.
6. **What Kairos should do:** give feedback without punishment. No penalties, no social pressure.

### Notion
1. **Features:** flexible databases, templates for habits and reading logs, AI agents and meeting notes.
2. **Free vs paid (verified):** the Free plan allows 5 MB uploads and keeps 7 days of page history. Plus costs $10 per member per month. Source: https://www.notion.com/pricing (checked 2026-09-23).
3. **UX strength:** users can build any tracker they like.
4. **UX weakness:** setup is slow, it is heavy on mobile, reminders are weak for personal routines, and the product is now AI- and team-first.
5. **Privacy:** needs an account and stores data in the cloud. Offline access is per page and limited to the first 50 database rows by default (https://www.notion.com/help/guides/working-offline-in-notion-everything-you-need-to-know).
6. **What Kairos should do:** offer Notion-like flexibility through a generic tracker engine with ready-made presets (habit, study, fitness) that work in 30 seconds with no setup.

## Feature gaps worth filling

- **Reliable reminders on Android, with self-diagnosis.** Competitors publish troubleshooting pages instead of fixing this. Kairos should show the state of exact-alarm permission, notification permission and battery optimisation, and re-schedule alarms after reboots and time or time-zone changes.
- **Recurring tasks and reminders free, with no caps.** Todoist, Structured and TickTick all limit or paywall these.
- **One day view for tasks, habits, trackers and reading.** No competitor puts all of these on the same timeline without cloud sync.
- **A generic tracker with forgiving, total-based goals.** Support "3 per week" and "150 minutes per week" targets and scoring where a lapse weakens progress rather than resetting it to zero.
- **Reading as insight, not a book count.** Prompt for why I started, what I learned and what I applied, and resurface applied insights later, fully on the device.
- **A journal that links to the day.** An optional entry that can reference the day's completed tasks, trackers and book notes. No streaks.
- **Encrypted local backup the user owns.** An encrypted export file with open, documented formats (JSON or CSV), plus a restore check. Other apps rely on vendor clouds or Google Drive.
- **Widgets that are actionable and free.** Check off tasks and habits and see today's quote from the home screen, without opening the app.
- **Open, readable exports for every module**, so users are never locked in.

## What we deliberately won't do

- No account, no login and no cloud sync. No third-party analytics, crash SDKs or ads.
- No gamification: no points, pets, levels or leaderboards.
- No streak guilt: no broken-chain messages and no shaming notifications. Misses weaken progress gently.
- No AI planner or cloud AI features, since they would need data to leave the device.
- No paywall on reminders, recurrence or data export.
- No social feed, sharing network or public profiles.
- No copying of competitor UI wording, icons or visual identity.
