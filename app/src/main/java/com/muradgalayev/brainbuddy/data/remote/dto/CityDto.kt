package com.muradgalayev.brainbuddy.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CityDto(
    val id: String,
    val name: String,
    val region: String = "",
    val country: String = "IT",
    @SerialName("created_at")
    val createdAt: String? = null,
)

@Serializable
data class PlaceDto(
    val id: String,
    @SerialName("city_id")
    val cityId: String,
    val name: String,
    val category: String,
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val website: String = "",
    val hours: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val notes: String = "",
)
