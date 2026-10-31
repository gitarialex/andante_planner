# Andante Planner: decisions to settle and milestone prompts (M0, M1)

Setup before using these prompts:
1. Convert the spec to Markdown and save it as `docs/SPEC.md`.
2. Save `copilot-instructions.md` as `.github/copilot-instructions.md`.
3. Settle the decisions in Part A and write the answers into `docs/SPEC.md` (a short "Resolved" note in the right section is enough).

---

## Part A. Decisions to settle first

Each has a recommended default. Change any you disagree with.

| # | Question | Recommended default |
|---|----------|---------------------|
| 1 | **Undo on a recurring task.** The task has already rolled to its next occurrence when the user taps Undo. | Undo works on the most recent completion only. It reads the latest `done` row in CompletionHistory for the task, restores `dueDate` to that row's occurrence date, deletes the row, regenerates reminders and resets nudge counters. If completing finished the series (task was archived-bound), undo reopens it. No new field needed. |
| 2 | **Minimum Android version.** | Decide after M0. Default to whatever the chosen Expo SDK requires. |
| 3 | **Dark theme colours.** | Defer to M7, but build the UI against theme tokens from the start so dark is a token swap. |
| 4 | **UI component library.** | Decide in M0 test 10. Fallback: own small component set driven by the theme tokens in spec section 10. |
| 5 | **Spec section 16 assumptions 1 to 7.** | Accept all as written, unless you want different behaviour. 5 (task titles on the lock screen) is fine for a personal phone. |
| 6 | **Assumptions 8 and 9** (no-prompt exact alarms; minimum Android). | Confirm in M0 on the real phone. |
| 7 | **Completing a recurring task early** (before its due date). Spec 5.7 says the next due date is the first candidate *after today*. | Accept: completing early skips to the first candidate after today. Confirm this is what you want, since it can skip an occurrence you haven't reached yet. |

---

## Part B. Prompt for M0: reliability spike

Paste into Copilot agent mode in an empty folder. This is a **throwaway** project. Do not polish it.

```
Read .github/copilot-instructions.md, then docs/SPEC.md sections 9.1, 9.4, 9.6, 13 (M0) and Appendix D.

Goal: build a throwaway Expo development-build app that proves the riskiest parts of Andante Planner work on a real Android 16 phone, before any real app code is written. The pass criteria are in Appendix D.

Build:
1. A new Expo project (TypeScript) as a development build, with a local Kotlin Expo module named andante-core created with Expo's local-module tooling (check the current Expo docs for the exact command and config).
2. In Kotlin: a minimal Room database with one table, `test_task(id, title, status)`; a `scheduled_event` table (id, type, refId, fireAtUtc, state); a single-exact-alarm scheduler that registers one alarm for the earliest pending event; an AlarmReceiver that shows due events as notifications and registers the next alarm; an ActionReceiver for Done and Stop nudging buttons that updates the database; a BootReceiver, and receivers for time change, timezone change and package replaced, each triggering a rebuild; notification channels as in spec 8.2.
3. Permission helpers for POST_NOTIFICATIONS, exact alarms and battery-optimisation exemption, each with a function that reads the current status (spec 9.6, Appendix D test 9).
4. A nudge-chain test with configurable interval (default 2 minutes) and cap (default 4), with Stop nudging.
5. A minimal Jetpack Glance widget that reads the same database and completes an item on tap (test 7). Check the current Expo approach for adding a Glance widget (config plugin or manual native changes) and tell me what you chose.
6. A TypeScript test screen with one button per Appendix D test (1 to 9) and a visible log of results, plus a list that updates when the database changes (test 8).

Rules:
- Use the injectable-clock approach even in the spike, so the scheduler logic is reusable.
- Do not add any network dependency in the release build config.
- Where Android behaviour matters (exact alarm permission, Doze, boot, battery), consult current Android documentation, and mark in code comments what you could not verify.
- Do not build any real app screens.

Deliver: working code, a README listing how to build and install on the phone, and a RESULTS.md template with one row per Appendix D test (pass criterion, how to run it, result, notes) that I will fill in while testing on the device. Test 10 (UI library) is a manual evaluation: add a short section to RESULTS.md with the questions to answer, and do not decide it yourself.

At the end, list anything you were unsure about and anything that needs checking on the phone.
```

After running it, test on the phone, fill in `RESULTS.md`, and write the findings (permission route, minimum Android, fallbacks) back into `docs/SPEC.md` before M1.

---

## Part C. Prompt for M1: Kotlin core

Run this once M0 is signed off and Part A is settled.

```
Read .github/copilot-instructions.md and docs/SPEC.md sections 3, 5, 6, 9.2, 9.5, 12.1 and Appendix C. Also read the "Resolved" notes I added to the spec.

Goal (milestone M1): implement the Kotlin domain core with automated tests. No UI, no bridge, no AlarmManager, no notifications.

Structure:
- Keep domain logic pure Kotlin (no Android imports) with an injected Clock and ZoneId, so tests run on the JVM without a device.
- Put Room entities, DAOs and migrations in the db package behind repository interfaces that the domain depends on. Use in-memory fakes for domain tests and a separate set of Room tests for the DAOs and migrations.

Implement, in this order, committing after each step:
1. Room schema exactly as spec section 6.1: Task, Plan, Milestone, Note, ChecklistItem, Category, Tag, task_tag, note_tag, note_task_link, Reminder, ScheduledEvent, CompletionHistory, Settings. Include the indexes in 6.4, the constraints in 6.2, schemaVersion = 1 and a migration test harness ready for version 2.
2. ReminderPlanner (spec 5.1 and 5.2): default reminders per priority and task type, skipping past defaults, custom reminders, regeneration on priority change while keeping manual edits.
3. Overdue and NudgeEngine as pure functions (spec 5.3 to 5.5): is-overdue, next nudge time, cap handling, midnight_overdue behaviour for all-day tasks (12:05 a.m. nudge counts toward the cap, then 6:00 a.m.), Stop nudging, reset on reschedule.
4. RecurrenceService (spec 5.7 and 9.5): daily, weekly with weekdays (Sunday = 0), monthly with month-end clamping, custom interval; roll-forward after completion; missed-occurrence roll with a `missed` history row; series end.
5. TaskService: create, update, complete, undo (per the Resolved note), reschedule, delete (with the cascade table in 6.3), stopNudging, restoreFromArchive, plus the archive sweep logic (7 days).
6. PlanService and delete modes (plan only vs plan and tasks), milestone rules and validation, NoteService with links, Tag and Category services with case-insensitive uniqueness.
7. ScheduleBuilder: a pure function that, given all tasks, reminders, settings, the clock and the timezone, returns the full list of pending ScheduledEvents (reminder, nudge, midnight_overdue, digest, review, recurrence_roll, archive_sweep, backup_prompt). This is the "rebuild" in spec 9.4, without registering any alarm.

Tests (JUnit, fake clock):
- Every scenario in spec 12.1 for reminder planning, nudges, overdue, recurrence, time handling (timezone change, DST, clock moved), lifecycle, digest and review skip rules.
- Every worked example in Appendix C as an explicit test, using its exact dates and times as expected values.
- Delete cascades from 6.3.
- Room tests: DAO round trips, constraint failures, and one migration test scaffold.

Out of scope: AlarmManager, notifications, receivers, widget, backup/import, the TypeScript bridge, any UI.

Rules: if the spec is ambiguous, list the question and stop on that item instead of guessing. Do not add fields or tables that are not in the spec. Do not add dependencies other than Room, JUnit and a test clock helper without asking.

Definition of done: all tests pass; the Appendix C examples pass exactly; at the end, give me a list of any spec ambiguities you hit and what you assumed.
```
