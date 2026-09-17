package com.behradhz.meowzix.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_media_sources",
    foreignKeys = [
        ForeignKey(
            entity = TrackSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackSourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["contentUri"], unique = true),
        Index(value = ["mediaStoreId"]),
    ],
)
data class LocalMediaSourceEntity(
    @PrimaryKey val trackSourceId: String,
    val mediaStoreId: Long,
    val contentUri: String,
    val relativePath: String?,
    val displayName: String?,
    val dateModifiedEpochSeconds: Long?,
)
