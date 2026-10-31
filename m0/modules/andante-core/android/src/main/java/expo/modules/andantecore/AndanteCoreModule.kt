package expo.modules.andantecore

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import expo.modules.interfaces.permissions.Permissions
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class AndanteCoreModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("AndanteCore")
    Events("onDataChanged")

    AsyncFunction("getTasks") {
      SpikeDatabase.get(appContext.applicationContext).dao().getTasks().map { task ->
        mapOf("id" to task.id, "title" to task.title, "status" to task.status)
      }
    }

    AsyncFunction("getPermissionStatus") {
      TestServices.permissions(appContext.applicationContext)
    }

    AsyncFunction("requestNotificationPermission") { promise: Promise ->
      val permissions = appContext.permissions
        ?: error("Expo's Android permission service is unavailable.")
      Permissions.askForPermissionsWithPermissionsManager(
        permissions,
        promise,
        Manifest.permission.POST_NOTIFICATIONS,
      )
    }

    AsyncFunction("requestExactAlarmAccess") {
      val context = appContext.applicationContext
      val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        .setData(Uri.parse("package:${context.packageName}"))
      appContext.currentActivity?.startActivity(intent)
        ?: throw IllegalStateException("No foreground activity is available.")
      "Opened exact alarm access settings."
    }

    AsyncFunction("requestBatteryOptimizationExemption") {
      val context = appContext.applicationContext
      val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(Uri.parse("package:${context.packageName}"))
      appContext.currentActivity?.startActivity(intent)
        ?: throw IllegalStateException("No foreground activity is available.")
      "Opened battery optimization settings."
    }

    AsyncFunction("runScenario") { testNumber: Int ->
      val context = appContext.applicationContext
      val result = TestServices.runScenario(context, testNumber)
      sendEvent("onDataChanged", mapOf("testNumber" to testNumber))
      result
    }
  }
}
