package com.muradgalayev.brainbuddy.domain.model

data class City(
    val id: String,
    val name: String,
    val region: String = "",
    val country: String = "IT",
)

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

enum class PlaceCategory(val key: String, val label: String) {
    Hospital("hospital", "Hospital"),
    Specialist("specialist", "Specialist"),
    Pharmacy("pharmacy", "Pharmacy"),
    Asl("asl", "ASL"),
    CentroAscolto("centro_ascolto", "Centro di ascolto"),
    SupportGroup("support_group", "Support group"),
    Other("other", "Other");

    companion object {
        fun fromKey(key: String): PlaceCategory =
            entries.firstOrNull { it.key == key } ?: Other
    }
}
