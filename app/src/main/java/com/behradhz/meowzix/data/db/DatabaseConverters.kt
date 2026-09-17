package com.behradhz.meowzix.data.db

import androidx.room.TypeConverter
import com.behradhz.meowzix.core.model.SourceAvailability
import com.behradhz.meowzix.core.model.TrackSourceType

class DatabaseConverters {
    @TypeConverter
    fun trackSourceTypeToString(value: TrackSourceType): String = value.name

    @TypeConverter
    fun stringToTrackSourceType(value: String): TrackSourceType = TrackSourceType.valueOf(value)

    @TypeConverter
    fun sourceAvailabilityToString(value: SourceAvailability): String = value.name

    @TypeConverter
    fun stringToSourceAvailability(value: String): SourceAvailability = SourceAvailability.valueOf(value)
}
