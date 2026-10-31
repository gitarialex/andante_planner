# Andante Planner: Copilot instructions

<!-- Save as .github/copilot-instructions.md. Keep it short: it is sent with every request. -->

Andante Planner is a personal, offline-first task planner for Android (iOS later), with tasks, plans, notes, four priority levels and multi-stage reminders. The full specification is `docs/SPEC.md` and is the source of truth. Decisions are numbered D-xx and features F-xx. If the spec is silent or conflicts with the code, stop and ask. Do not guess.

## Stack
- React Native + TypeScript as an Expo **development build** (not Expo Go).
- A local **Kotlin Expo module** (`modules/andante-core`) that owns the database, business rules, scheduling, notifications, receivers and the Glance widget.
- Room over SQLite. One database file, owned by Kotlin.
- Material Tonal look with a fixed palette (spec section 10). No wallpaper-based dynamic colour.

## Architecture rules (never break these)
1. **All rules that change data live in Kotlin only.** TypeScript presents and collects input. It never schedules alarms, never computes reminders or nudges, never applies business rules. Notification actions (Done, Snooze, Stop nudging) must work with the app closed, so they call the same Kotlin services the UI uses.
2. The module returns plain data objects. Never expose Room types to TypeScript.
3. Domain code takes an **injectable clock and timezone**. Never call `System.currentTimeMillis()` or `LocalDate.now()` inside domain logic.
4. Due dates and times are stored as **local wall-clock values** (`YYYY-MM-DD`, `HH:mm`). Fire times are UTC epoch ms, derived from those values and the phone's current timezone.
5. `scheduled_event` is derived data. It can always be deleted and rebuilt from tasks, reminders, settings and the clock. Keep **one** exact alarm registered, for the earliest pending event. Every event has an id and a state so duplicate broadcasts never show a notification twice.
6. Overdue is calculated from the due date and the clock. It is never stored.
7. IDs are UUID strings. Every schema change ships a tested migration.
8. **Never access Room on the main thread.** Use suspend DAO functions or
   Dispatchers.IO everywhere, including widget providers, receivers and
   notification action callbacks. For work that must outlive a BroadcastReceiver's
   onReceive, use goAsync() or WorkManager (check current Android docs).

## Behaviour rules that are easy to get wrong
- Priorities: critical, high, medium (default), low. Colours: #B3261E, #C2570C, #2B63C4, #3F7A47. Priority is never shown by colour alone.
- Default reminders, timed tasks: critical = 1 day, 3 h, 30 min before + at due time; high = 1 day, 1 h before + at due time; medium = 1 h before + at due time; low = at due time.
- Default reminders, all-day tasks: 6:00 a.m. on the due day. Critical also gets 8:00 p.m. the day before.
- A default reminder whose time has already passed is not scheduled.
- Overdue: a timed task when its due time passes; an all-day task at midnight after the due day.
- Nudges: every 30 min, cap 4. Critical: every 15 min, cap 8. Timed tasks: first nudge one interval after due time. All-day tasks: one nudge at 12:05 a.m. (counts toward the cap), the rest from 6:00 a.m. Nudges are created one at a time. Each replaces the previous nudge notification for that task.
- "Stop nudging" leaves the task open and overdue and sets `nudgingStopped`. Rescheduling resets `nudgesSent` and `nudgingStopped` and regenerates default reminders.
- Snooze creates a one-off reminder (default 10 min). It is not a nudge and does not change the due date.
- Recurring tasks: one row, repeats from the due date (not the completion date), end date is required. A missed occurrence stays overdue until the next occurrence date, then rolls forward and is recorded as missed. Monthly rules clamp to month end.
- Every task has a due date. Quick-add defaults to all-day, today, medium.
- Deletion is permanent with confirmation. There is no trash. Weeks start on Sunday. Date and time formats follow the phone.

## Constraints
- The **release build must not request the INTERNET permission**. No analytics, no network calls, no cloud SDKs.
- Do not add features from spec section 15 (deferred features).
- Do not add a new dependency without asking and explaining why. Prefer the platform and Expo APIs already in use.
- Accessibility: 48 dp touch targets, TalkBack labels, scalable text, 4.5:1 contrast.

## How to work
- Work on **one milestone at a time** (spec section 13). Do not start UI work before the Kotlin core and scheduling exit criteria are met.
- Write tests with the code. Domain tests use a fake clock and cover the scenarios in spec section 12.1 and Appendix C.
- Android permission and background-execution behaviour changes between versions. Check current Android and Expo documentation instead of relying on memory, and say when something has not been verified on a real device.
- Small commits, one concern each. State what you did and what you did not do at the end of each task.
