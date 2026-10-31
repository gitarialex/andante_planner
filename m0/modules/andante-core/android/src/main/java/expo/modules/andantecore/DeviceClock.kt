package expo.modules.andantecore

class DeviceClock : Clock {
  override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
