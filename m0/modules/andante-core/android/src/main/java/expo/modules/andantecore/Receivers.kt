package expo.modules.andantecore

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.concurrent.Executors

private val receiverExecutor = Executors.newSingleThreadExecutor()

class AlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val result = goAsync()
    receiverExecutor.execute {
      try {
        SpikeEvents.processDue(context.applicationContext)
      } catch (error: SecurityException) {
        Log.e("AndanteM0", "Could not process alarm because access is missing.", error)
      } finally {
        result.finish()
      }
    }
  }
}

class ActionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val taskId = intent.getStringExtra("taskId")
    if (taskId.isNullOrBlank()) {
      Log.e("AndanteM0", "Notification action did not include a task id.")
      return
    }
    val result = goAsync()
    receiverExecutor.execute {
      try {
        when (intent.action) {
          ACTION_DONE -> TaskService.complete(context.applicationContext, taskId)
          ACTION_STOP_NUDGING -> {
            NudgeState.stop(context.applicationContext, taskId)
            EventScheduler.cancelTaskNudges(context.applicationContext, taskId)
          }
          else -> Log.e("AndanteM0", "Unknown notification action: ${intent.action}")
        }
        intent.getIntExtra("notificationId", Int.MIN_VALUE)
          .takeIf { it != Int.MIN_VALUE }
          ?.let { androidx.core.app.NotificationManagerCompat.from(context).cancel(it) }
        WidgetUpdater.refresh(context.applicationContext)
      } catch (error: SecurityException) {
        Log.e("AndanteM0", "Notification action could not rebuild the alarm.", error)
      } finally {
        result.finish()
      }
    }
  }
}

class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    // Boot, time-change and OEM background behavior must be verified on each test device.
    val result = goAsync()
    receiverExecutor.execute {
      try {
        EventScheduler.rebuild(context.applicationContext)
      } catch (error: SecurityException) {
        Log.e(
          "AndanteM0",
          "Pending events remain stored, but exact-alarm access is not available.",
          error,
        )
      } finally {
        result.finish()
      }
    }
  }
}
