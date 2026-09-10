package com.muradgalayev.brainbuddy.data.local.entity

import androidx.room.Entity
import androidx.room.Index

// a mode as stored on the device. overridesJson and scheduleJson keep the two structured parts
// as serialised blobs rather than as forty nullable columns: a mode's overrides are read and
// written whole and never queried across, nothing ever asks 'which modes silence to-do
// reminders', so columns would buy nothing and cost a schema migration every time a new
// controllable setting is added
@Entity(
    tableName = "app_modes",
    primaryKeys = ["userId", "id"],
    indices = [Index("userId")],
)
data class AppModeEntity(
    val id: String,
    val userId: String,
    val name: String,
    val icon: String,
    val accent: String?,
    val isBuiltIn: Boolean,
    val sortIndex: Int,
    val scheduleJson: String?,
    val overridesJson: String,
    val syncStatus: String = SyncStatus.PENDING_INSERT.name,
    val lastModifiedAt: Long = System.currentTimeMillis(),
)
