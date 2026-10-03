package dev.behradhz.meowzix.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "listening_sessions")
data class ListeningSessionEntity(
    @PrimaryKey val id: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val initialMode: String,
)

@Entity(
    tableName = "listening_events",
    foreignKeys = [
        ForeignKey(entity = TrackEntity::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ListeningSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index("trackId"),
        Index("sessionId"),
        Index("occurredAtEpochMs"),
        Index(value = ["playbackInstanceId", "type"]),
        Index(value = ["outcomeKey"], unique = true),
        Index(value = ["eventSequence"], unique = true),
    ],
)
data class ListeningEventEntity(
    @PrimaryKey val id: String,
    val playbackInstanceId: String,
    val trackId: String,
    val sessionId: String,
    val type: String,
    val occurredAtEpochMs: Long,
    val localHour: Int,
    val dayOfWeek: Int,
    val timeBucket: String,
    val isWeekend: Boolean,
    val positionMs: Long?,
    val durationMs: Long?,
    val completionRatio: Double?,
    val initiatedBy: String,
    val playbackMode: String,
    @ColumnInfo(defaultValue = "0") val eventSequence: Long = 0L,
    /** Only final outcomes use this key; repeated intentional seeks/actions remain observable. */
    val outcomeKey: String? = null,
)

@Entity(tableName = "track_preference_stats")
data class TrackPreferenceStatsEntity(
    @PrimaryKey val trackId: String,
    val totalStarts: Int,
    val totalCompletions: Int,
    val earlySkips: Int,
    val lateSkips: Int,
    val manualSelections: Int,
    val replays: Int,
    val lastPlayedAtEpochMs: Long?,
)

@Entity(tableName = "track_time_preferences", primaryKeys = ["trackId", "timeBucket"])
data class TrackTimePreferenceEntity(
    val trackId: String,
    val timeBucket: String,
    val starts: Int,
    val completions: Int,
    val earlySkips: Int,
    val manualSelections: Int,
    val lastInteractionAtEpochMs: Long?,
)

/** Durable clock survives row deletion and VACUUM; playback identity remains the UUID. */
@Entity(tableName = "recommendation_event_clock")
data class RecommendationEventClockEntity(@PrimaryKey val id: Int = 1, val lastSequence: Long = 0L)
