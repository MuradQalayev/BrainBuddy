package com.muradgalayev.brainbuddy.data.mapper

import com.muradgalayev.brainbuddy.data.local.entity.ActivityTimeStatEntity
import com.muradgalayev.brainbuddy.data.local.entity.SyncStatus
import com.muradgalayev.brainbuddy.data.remote.dto.ActivityTimeStatDto
import com.muradgalayev.brainbuddy.domain.scheduling.HabitTimeStat

fun ActivityTimeStatEntity.toStat(): HabitTimeStat = HabitTimeStat(
    sinSum = sinSum,
    cosSum = cosSum,
    weightSum = weightSum,
    durationWeightedSum = durationWeightedSum,
    sampleCount = sampleCount,
    lastObservedAt = lastObservedAt,
)

fun HabitTimeStat.toEntity(
    userId: String,
    habitKey: String,
    dayType: String,
    syncStatus: SyncStatus = SyncStatus.PENDING_INSERT,
): ActivityTimeStatEntity = ActivityTimeStatEntity(
    id = ActivityTimeStatEntity.idFor(userId, habitKey, dayType),
    userId = userId,
    habitKey = habitKey,
    dayType = dayType,
    sinSum = sinSum,
    cosSum = cosSum,
    weightSum = weightSum,
    durationWeightedSum = durationWeightedSum,
    sampleCount = sampleCount,
    lastObservedAt = lastObservedAt,
    syncStatus = syncStatus.name,
    lastModifiedAt = System.currentTimeMillis(),
)

fun ActivityTimeStatEntity.toDto(): ActivityTimeStatDto = ActivityTimeStatDto(
    id = id,
    userId = userId,
    habitKey = habitKey,
    dayType = dayType,
    sinSum = sinSum,
    cosSum = cosSum,
    weightSum = weightSum,
    durationWeightedSum = durationWeightedSum,
    sampleCount = sampleCount,
    lastObservedAt = lastObservedAt,
)

fun ActivityTimeStatDto.toEntity(): ActivityTimeStatEntity = ActivityTimeStatEntity(
    id = id,
    userId = userId,
    habitKey = habitKey,
    dayType = dayType,
    sinSum = sinSum,
    cosSum = cosSum,
    weightSum = weightSum,
    durationWeightedSum = durationWeightedSum,
    sampleCount = sampleCount,
    lastObservedAt = lastObservedAt,
    syncStatus = SyncStatus.SYNCED.name,
    lastModifiedAt = System.currentTimeMillis(),
)
