package expo.modules.andantecore

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.Executors

private const val PREFERENCES = "andante_m0"
private const val LOCAL_TIME_PREFIX = "local:"
private const val CHANNEL_REMINDERS = "reminders"
private const val CHANNEL_CRITICAL = "critical"
private const val CHANNEL_NUDGES = "nudges"
private const val CHANNEL_DIGEST = "digest"
private const val CHANNEL_REVIEW = "review"
internal const val ACTION_DONE = "expo.modules.andantecore.action.DONE"
internal const val ACTION_STOP_NUDGING = "expo.modules.andantecore.action.STOP_NUDGING"

object EventScheduler {
  private fun alarmIntent(context: Context): PendingIntent =
    PendingIntent.getBroadcast(
      context,
      501,
      Intent(context, AlarmReceiver::class.java)
        .setAction("expo.modules.andantecore.action.FIRE_ALARM")
        .setData(Uri.parse("andante-m0://alarm")),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

  fun rebuild(
    context: Context,
    clock: Clock = DeviceClock(),
    zoneId: ZoneId = ZoneId.systemDefault(),
  ) {
    val alarmManager = context.getSystemService(AlarmManager::class.java)
      ?: error("AlarmManager is unavailable.")
    val pendingIntent = alarmIntent(context)
    alarmManager.cancel(pendingIntent)

    val dao = SpikeDatabase.get(context).dao()
    val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    val pendingEvents = dao.getPendingEvents().map { event ->
      val localTime = preferences.getString(LOCAL_TIME_PREFIX + event.id, null)
        ?.let(LocalDateTime::parse)
        ?: Instant.ofEpochMilli(event.fireAtUtc).atZone(zoneId).toLocalDateTime()
      val updatedTime = localTime.atZone(zoneId).toInstant().toEpochMilli()
      dao.updatePendingEventTime(event.id, updatedTime)
      event.copy(fireAtUtc = updatedTime)
    }

    val earliest = pendingEvents.minByOrNull { it.fireAtUtc } ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
      throw SecurityException("Exact alarm access is not granted.")
    }
    // Allow-while-idle is required for Doze; delivery timing and OEM restrictions still need phone testing.
    alarmManager.setExactAndAllowWhileIdle(
      AlarmManager.RTC_WAKEUP,
      maxOf(earliest.fireAtUtc, clock.nowEpochMillis()),
      pendingIntent,
    )
  }

  fun cancelTaskEvents(context: Context, taskId: String) {
    SpikeDatabase.get(context).dao().cancelTaskEvents(taskId)
    rebuild(context)
  }

  fun cancelTaskNudges(context: Context, taskId: String) {
    SpikeDatabase.get(context).dao()
      .cancelTaskEventsOfType(taskId, SpikePlanner.EVENT_NUDGE)
    rebuild(context)
  }
}

object NotificationChannels {
  fun create(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(android.app.NotificationManager::class.java)
      ?: error("NotificationManager is unavailable.")
    manager.createNotificationChannels(
      listOf(
        android.app.NotificationChannel(
          CHANNEL_REMINDERS,
          "Reminders",
          android.app.NotificationManager.IMPORTANCE_HIGH,
        ),
        android.app.NotificationChannel(
          CHANNEL_CRITICAL,
          "Critical",
          android.app.NotificationManager.IMPORTANCE_HIGH,
        ).apply {
          enableVibration(true)
          vibrationPattern = longArrayOf(0, 250, 150, 250)
        },
        android.app.NotificationChannel(
          CHANNEL_NUDGES,
          "Nudges",
          android.app.NotificationManager.IMPORTANCE_DEFAULT,
        ),
        android.app.NotificationChannel(
          CHANNEL_DIGEST,
          "Digest",
          android.app.NotificationManager.IMPORTANCE_DEFAULT,
        ),
        android.app.NotificationChannel(
          CHANNEL_REVIEW,
          "Review",
          android.app.NotificationManager.IMPORTANCE_DEFAULT,
        ),
      ),
    )
  }
}

object NudgeState {
  private fun preferences(context: Context) =
    context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

  fun reset(context: Context, taskId: String) {
    preferences(context).edit()
      .putInt("sent:$taskId", 0)
      .putBoolean("stopped:$taskId", false)
      .putInt("cap:$taskId", SpikePlanner.DEFAULT_NUDGE_CAP)
      .putLong("interval:$taskId", SpikePlanner.DEFAULT_NUDGE_INTERVAL_MILLIS)
      .apply()
  }

  fun count(context: Context, taskId: String): Int =
    preferences(context).getInt("sent:$taskId", 0)

  fun increment(context: Context, taskId: String): Int {
    val next = count(context, taskId) + 1
    preferences(context).edit().putInt("sent:$taskId", next).apply()
    return next
  }

  fun cap(context: Context, taskId: String): Int =
    preferences(context).getInt("cap:$taskId", SpikePlanner.DEFAULT_NUDGE_CAP)

  fun interval(context: Context, taskId: String): Long =
    preferences(context).getLong(
      "interval:$taskId",
      SpikePlanner.DEFAULT_NUDGE_INTERVAL_MILLIS,
    )

  fun isStopped(context: Context, taskId: String): Boolean =
    preferences(context).getBoolean("stopped:$taskId", false)

  fun stop(context: Context, taskId: String) {
    preferences(context).edit().putBoolean("stopped:$taskId", true).apply()
  }

  fun clear(context: Context, taskId: String) {
    preferences(context).edit()
      .remove("sent:$taskId")
      .remove("stopped:$taskId")
      .remove("cap:$taskId")
      .remove("interval:$taskId")
      .apply()
  }
}

object SpikeEvents {
  private val executor = Executors.newSingleThreadExecutor()

  fun processDue(context: Context) {
    val pending = SpikeDatabase.get(context).dao().getDueEvents(DeviceClock().nowEpochMillis())
    for (event in pending) {
      val dao = SpikeDatabase.get(context).dao()
      if (dao.transitionEvent(event.id, "fired") == 0) continue
      val task = dao.getTask(event.refId) ?: continue
      if (task.status != "open") continue

      val nudgeNumber = if (event.type == SpikePlanner.EVENT_NUDGE) {
        NudgeState.increment(context, task.id)
      } else {
        null
      }
      NotificationChannels.create(context)
      showNotification(context, event, task, nudgeNumber)

      if (event.type == SpikePlanner.EVENT_NUDGE &&
        nudgeNumber != null &&
        nudgeNumber < NudgeState.cap(context, task.id) &&
        !NudgeState.isStopped(context, task.id)
      ) {
        val next = SpikePlanner(
          DeviceClock(),
          NudgeState.interval(context, task.id),
          NudgeState.cap(context, task.id),
        ).nextNudge(UUID.randomUUID().toString(), task.id, nudgeNumber)
        if (next != null) {
          TestServices.persistEvent(context, next, ZoneId.systemDefault())
        }
      }
    }
    EventScheduler.rebuild(context)
  }

  private fun showNotification(
    context: Context,
    event: ScheduledEvent,
    task: TestTask,
    nudgeNumber: Int?,
  ) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      return
    }

    val isNudge = nudgeNumber != null
    val channel = if (isNudge) CHANNEL_NUDGES else CHANNEL_REMINDERS
    val content = if (isNudge) {
      "Overdue: ${task.title} · Nudge $nudgeNumber of ${NudgeState.cap(context, task.id)}"
    } else {
      task.title
    }
    val notificationId = if (isNudge) task.id.hashCode() else event.id.hashCode()
    val builder = NotificationCompat.Builder(context, channel)
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle(if (isNudge) "Overdue task" else "M0 alarm")
      .setContentText(content)
      .setStyle(NotificationCompat.BigTextStyle().bigText(content))
      .setAutoCancel(true)
      .setPriority(
        if (isNudge) NotificationCompat.PRIORITY_DEFAULT
        else NotificationCompat.PRIORITY_HIGH,
      )
      .setContentIntent(launchAppIntent(context, task.id))
      .addAction(
        0,
        "Done",
        actionPendingIntent(context, ACTION_DONE, task.id, notificationId, notificationId),
      )

    if (isNudge) {
      builder.addAction(
        0,
        "Stop nudging",
        actionPendingIntent(
          context,
          ACTION_STOP_NUDGING,
          task.id,
          notificationId + 1,
          notificationId,
        ),
      )
    }
    NotificationManagerCompat.from(context).notify(notificationId, builder.build())
  }

  private fun launchAppIntent(context: Context, taskId: String): PendingIntent? {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
      ?.putExtra("testTaskId", taskId)
      ?: return null
    return PendingIntent.getActivity(
      context,
      taskId.hashCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun actionPendingIntent(
    context: Context,
    action: String,
    taskId: String,
    requestCode: Int,
    notificationId: Int,
  ): PendingIntent =
    PendingIntent.getBroadcast(
      context,
      requestCode,
      Intent(context, ActionReceiver::class.java)
        .setAction(action)
        .putExtra("taskId", taskId)
        .putExtra("notificationId", notificationId),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
