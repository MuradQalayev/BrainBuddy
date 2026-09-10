package com.muradgalayev.brainbuddy.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// the learned-timing row on the wire. aggregates only: the individual events that produced them
// already live in calendar_events, so this adds no new detail beyond roughly when, roughly how
// long, and how often
@Serializable
data class ActivityTimeStatDto(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("habit_key")
    val habitKey: String,
    @SerialName("day_type")
    val dayType: String,
    @SerialName("sin_sum")
    val sinSum: Double,
    @SerialName("cos_sum")
    val cosSum: Double,
    @SerialName("weight_sum")
    val weightSum: Double,
    @SerialName("duration_weighted_sum")
    val durationWeightedSum: Double,
    @SerialName("sample_count")
    val sampleCount: Int,
    @SerialName("last_observed_at")
    val lastObservedAt: Long,
    @SerialName("updated_at")
    val updatedAt: String? = null,
)
