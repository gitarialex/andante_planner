package expo.modules.andantecore

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import java.util.UUID

@Entity(tableName = "test_task")
data class TestTask(
  @PrimaryKey val id: String,
  val title: String,
  val status: String,
)

@Entity(
  tableName = "scheduled_event",
  indices = [
    Index(value = ["state", "fireAtUtc"]),
    Index(value = ["refId", "state"]),
  ],
)
data class ScheduledEvent(
  @PrimaryKey val id: String,
  val type: String,
  val refId: String,
  val fireAtUtc: Long,
  val state: String,
)

@Dao
interface SpikeDao {
  @Insert(onConflict = OnConflictStrategy.ABORT)
  fun insertTask(task: TestTask)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  fun insertEvent(event: ScheduledEvent)

  @Query("SELECT * FROM test_task ORDER BY rowid DESC")
  fun getTasks(): List<TestTask>

  @Query("SELECT * FROM test_task WHERE id = :id LIMIT 1")
  fun getTask(id: String): TestTask?

  @Query("UPDATE test_task SET status = 'done' WHERE id = :id AND status = 'open'")
  fun completeTask(id: String): Int

  @Query("SELECT * FROM scheduled_event WHERE state = 'pending' ORDER BY fireAtUtc ASC")
  fun getPendingEvents(): List<ScheduledEvent>

  @Query("SELECT * FROM scheduled_event WHERE state = 'pending' AND fireAtUtc <= :now ORDER BY fireAtUtc ASC")
  fun getDueEvents(now: Long): List<ScheduledEvent>

  @Query("UPDATE scheduled_event SET fireAtUtc = :fireAt WHERE id = :id AND state = 'pending'")
  fun updatePendingEventTime(id: String, fireAt: Long)

  @Query("UPDATE scheduled_event SET state = :state WHERE id = :id AND state = 'pending'")
  fun transitionEvent(id: String, state: String): Int

  @Query("UPDATE scheduled_event SET state = 'cancelled' WHERE refId = :taskId AND state = 'pending'")
  fun cancelTaskEvents(taskId: String)

  @Query("UPDATE scheduled_event SET state = 'cancelled' WHERE refId = :taskId AND type = :type AND state = 'pending'")
  fun cancelTaskEventsOfType(taskId: String, type: String)

  @Query("SELECT COUNT(*) FROM scheduled_event WHERE state = 'pending'")
  fun countPendingEvents(): Int
}

@Database(
  entities = [TestTask::class, ScheduledEvent::class],
  version = 1,
  exportSchema = false,
)
abstract class SpikeDatabase : RoomDatabase() {
  abstract fun dao(): SpikeDao

  companion object {
    @Volatile
    private var instance: SpikeDatabase? = null

    fun get(context: Context): SpikeDatabase =
      instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
          context.applicationContext,
          SpikeDatabase::class.java,
          "andante-m0.db",
        ).build().also { instance = it }
      }
  }
}

object TaskService {
  fun create(context: Context, title: String): TestTask {
    require(title.isNotBlank()) { "Task title cannot be blank." }
    val task = TestTask(UUID.randomUUID().toString(), title, "open")
    SpikeDatabase.get(context).dao().insertTask(task)
    return task
  }

  fun complete(context: Context, taskId: String): Boolean {
    val dao = SpikeDatabase.get(context).dao()
    val changed = dao.completeTask(taskId) > 0
    if (changed) {
      EventScheduler.cancelTaskEvents(context, taskId)
      NudgeState.clear(context, taskId)
      androidx.core.app.NotificationManagerCompat.from(context)
        .cancel(taskId.hashCode())
    }
    return changed
  }
}
