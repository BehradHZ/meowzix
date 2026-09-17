package dev.behradhz.meowzix.data.db

import androidx.room.TypeConverter
import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.TrackSourceType

class DbConverters {
    @TypeConverter fun sourceTypeToString(value: TrackSourceType): String = value.name
    @TypeConverter fun stringToSourceType(value: String): TrackSourceType = TrackSourceType.valueOf(value)
    @TypeConverter fun availabilityToString(value: SourceAvailability): String = value.name
    @TypeConverter fun stringToAvailability(value: String): SourceAvailability = SourceAvailability.valueOf(value)
}
