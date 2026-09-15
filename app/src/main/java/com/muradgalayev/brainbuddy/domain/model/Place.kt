package com.muradgalayev.brainbuddy.domain.model

import kotlinx.serialization.Serializable
import com.muradgalayev.brainbuddy.R

@Serializable
data class City(
    val id: String,
    val name: String,
    val region: String = "",
    val country: String = "IT",
)

@Serializable
data class Place(
    val id: String,
    val cityId: String,
    val name: String,
    val category: PlaceCategory,
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val website: String = "",
    val hours: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val notes: String = "",
)

@Serializable
enum class PlaceCategory(val key: String, val label: String, @androidx.annotation.StringRes val labelRes: Int) {
    Hospital("hospital", "Hospital", R.string.care_hospital),
    Specialist("specialist", "Specialist", R.string.place_specialist),
    Pharmacy("pharmacy", "Pharmacy", R.string.care_pharmacy),
    Asl("asl", "ASL", R.string.place_asl),
    CentroAscolto("centro_ascolto", "Centro di ascolto", R.string.place_centro),
    SupportGroup("support_group", "Support group", R.string.care_support_group),
    Other("other", "Other", R.string.place_other);

    companion object {
        fun fromKey(key: String): PlaceCategory =
            entries.firstOrNull { it.key == key } ?: Other
    }
}
