package expo.modules.andantecore

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.text.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object WidgetUpdater {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  fun refresh(context: Context) {
    scope.launch {
      TestTaskWidget().updateAll(context.applicationContext)
    }
  }
}

class TestTaskWidget : GlanceAppWidget() {
  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val task = SpikeDatabase.get(context).dao().getTasks().firstOrNull { it.status == "open" }
    provideContent {
      Column {
        Text(task?.title ?: "No open spike tasks")
        if (task != null) {
          Spacer(modifier = GlanceModifier.height(8.dp))
          Button(
            text = "Complete",
            onClick = actionRunCallback<CompleteWidgetTask>(
              actionParametersOf(CompleteWidgetTask.taskIdKey to task.id),
            ),
          )
        }
      }
    }
  }
}

class TestTaskWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = TestTaskWidget()
}

class CompleteWidgetTask : ActionCallback {
  override suspend fun onAction(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
  ) {
    val taskId = parameters[taskIdKey] ?: error("Widget action did not include a task id.")
    TaskService.complete(context, taskId)
    WidgetUpdater.refresh(context)
  }

  companion object {
    val taskIdKey = ActionParameters.Key<String>("taskId")
  }
}
