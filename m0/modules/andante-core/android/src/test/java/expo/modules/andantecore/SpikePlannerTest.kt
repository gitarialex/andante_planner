package expo.modules.andantecore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpikePlannerTest {
  @Test
  fun plansEventsFromInjectedClock() {
    val clock = FakeClock(1_800_000_000_000L)
    val planner = SpikePlanner(clock)

    assertEquals(
      clock.nowEpochMillis() + 120_000L,
      planner.firstNudge("nudge-id", "task-id").fireAtUtc,
    )
    assertEquals(
      clock.nowEpochMillis() + 300_000L,
      planner.alarm("alarm-id", "task-id", 300_000L).fireAtUtc,
    )
  }

  @Test
  fun capsNudgesAndUsesConfiguredInterval() {
    val clock = FakeClock(1_800_000_000_000L)
    val planner = SpikePlanner(clock, intervalMillis = 30_000L, cap = 2)

    assertEquals(clock.nowEpochMillis() + 30_000L, planner.nextNudge("n2", "task", 1)?.fireAtUtc)
    assertNull(planner.nextNudge("n3", "task", 2))
  }

  private class FakeClock(var value: Long) : Clock {
    override fun nowEpochMillis(): Long = value
  }
}
