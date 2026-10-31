package expo.modules.andantecore

import android.content.Context
import android.app.ActivityManager
import android.os.Build
import android.os.PowerManager
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

object TestServices {
  fun runScenario(context: Context, testNumber: Int): String {
    require(testNumber in 1..8) { "Unknown scenario number: $testNumber" }
    val task = TaskService.create(context, scenarioTitle(testNumber))
    val clock = DeviceClock()
    val zoneId = ZoneId.systemDefault()

    when (testNumber) {
      1 -> persistEvent(context, SpikePlanner(clock).alarm(
        UUID.randomUUID().toString(), task.id, 2 * 60 * 1000L,
      ), zoneId)
      2, 5 -> {
        for (index in 1..5) {
          persistEvent(
            context,
            SpikePlanner(clock).alarm(
              UUID.randomUUID().toString(),
              task.id,
              index * 2 * 60 * 1000L,
            ),
            zoneId,
          )
        }
      }
      3, 4 -> persistEvent(context, SpikePlanner(clock).alarm(
        UUID.randomUUID().toString(), task.id, 2 * 60 * 1000L,
      ), zoneId)
      6 -> {
        NudgeState.reset(context, task.id)
        persistEvent(
          context,
          SpikePlanner(clock).firstNudge(UUID.randomUUID().toString(), task.id),
          zoneId,
        )
      }
      7, 8 -> Unit
    }

    EventScheduler.rebuild(context, clock, zoneId)
    WidgetUpdater.refresh(context)
    return when (testNumber) {
      1 -> "One exact alarm scheduled for about 2 minutes from now. Repeat this test 5 times."
      2 -> "Five pending events scheduled 2, 4, 6, 8 and 10 minutes from now. Reboot without force-stopping."
      3 -> "Done-action notification scheduled for about 2 minutes from now."
      4 -> "Local-time alarm scheduled for about 2 minutes from now. Change timezone while it is pending."
      5 -> {
        val manager = context.getSystemService(PowerManager::class.java)
          ?: error("PowerManager is unavailable.")
        val activityManager = context.getSystemService(ActivityManager::class.java)
          ?: error("ActivityManager is unavailable.")
        "Five events scheduled. Power saver: ${manager.isPowerSaveMode}; battery exemption: ${manager.isIgnoringBatteryOptimizations(context.packageName)}; background restricted: ${Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && activityManager.isBackgroundRestricted}. Enable restrictions, then reboot and observe."
      }
      6 -> "Nudge 1 of 4 scheduled in 2 minutes. Stop nudging is available on its notification."
      7 -> "Widget task created. Add the Andante M0 Spike widget from the launcher's widget picker."
      else -> "Task created in Room. The task list refreshes from the Kotlin change event."
    }
  }

  fun persistEvent(
    context: Context,
    planned: PlannedTestEvent,
    zoneId: ZoneId,
  ) {
    val localTime = Instant.ofEpochMilli(planned.fireAtUtc)
      .atZone(zoneId)
      .toLocalDateTime()
    context.getSharedPreferences("andante_m0", Context.MODE_PRIVATE)
      .edit()
      .putString("local:${planned.id}", localTime.toString())
      .apply()
    SpikeDatabase.get(context).dao().insertEvent(
      ScheduledEvent(
        id = planned.id,
        type = planned.type,
        refId = planned.refId,
        fireAtUtc = planned.fireAtUtc,
        state = "pending",
      ),
    )
  }

  fun permissions(context: Context): Map<String, String> {
    val notificationsGranted =
      android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
        androidx.core.content.ContextCompat.checkSelfPermission(
          context,
          android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val alarmManager = context.getSystemService(android.app.AlarmManager::class.java)
      ?: error("AlarmManager is unavailable.")
    val powerManager = context.getSystemService(PowerManager::class.java)
      ?: error("PowerManager is unavailable.")
    val activityManager = context.getSystemService(ActivityManager::class.java)
      ?: error("ActivityManager is unavailable.")
    val exactGranted =
      android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
        alarmManager.canScheduleExactAlarms()
    return mapOf(
      "notifications" to if (notificationsGranted) "granted" else "not granted",
      "exactAlarms" to if (exactGranted) "granted" else "not granted",
      "ignoringBatteryOptimizations" to
        if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) "granted"
        else "not granted",
      "powerSaveMode" to if (powerManager.isPowerSaveMode) "active" else "inactive",
      "backgroundRestricted" to if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
        activityManager.isBackgroundRestricted
      ) "restricted" else "not restricted",
    )
  }

  private fun scenarioTitle(testNumber: Int): String =
    when (testNumber) {
      1 -> "Exact alarm check"
      2 -> "Reboot alarm check"
      3 -> "Notification Done action"
      4 -> "Timezone change check"
      5 -> "Battery restriction check"
      6 -> "Nudge chain check"
      7 -> "Widget completion check"
      else -> "Kotlin bridge round trip"
    }
}
