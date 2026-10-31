package expo.modules.andantecore

fun interface Clock {
  fun nowEpochMillis(): Long
}

data class PlannedTestEvent(
  val id: String,
  val type: String,
  val refId: String,
  val fireAtUtc: Long,
)

class SpikePlanner(
  private val clock: Clock,
  private val intervalMillis: Long = DEFAULT_NUDGE_INTERVAL_MILLIS,
  private val cap: Int = DEFAULT_NUDGE_CAP,
) {
  fun alarm(id: String, taskId: String, delayMillis: Long): PlannedTestEvent =
    PlannedTestEvent(
      id = id,
      type = EVENT_ALARM,
      refId = taskId,
      fireAtUtc = clock.nowEpochMillis() + delayMillis,
    )

  fun firstNudge(id: String, taskId: String): PlannedTestEvent =
    PlannedTestEvent(
      id = id,
      type = EVENT_NUDGE,
      refId = taskId,
      fireAtUtc = clock.nowEpochMillis() + intervalMillis,
    )

  fun nextNudge(id: String, taskId: String, sent: Int): PlannedTestEvent? =
    if (sent >= cap) {
      null
    } else {
      PlannedTestEvent(
        id = id,
        type = EVENT_NUDGE,
        refId = taskId,
        fireAtUtc = clock.nowEpochMillis() + intervalMillis,
      )
    }

  companion object {
    const val DEFAULT_NUDGE_INTERVAL_MILLIS = 2 * 60 * 1000L
    const val DEFAULT_NUDGE_CAP = 4
    const val EVENT_ALARM = "alarm"
    const val EVENT_NUDGE = "nudge"
  }
}
