package com.muradgalayev.brainbuddy.domain.model

// the account's plan. Free until the server says otherwise, see public.user_plans
enum class Plan {
    Free,
    Plus;

    companion object {
        fun fromStored(value: String?): Plan = if (value == "plus") Plus else Free
    }
}

// what Plus unlocks. the key travels in the plan screen's route, so a lock can open the screen
// with its own feature highlighted
enum class PlanFeature(val key: String) {
    WorkspaceAi("workspace_ai"),
    CalendarAi("calendar_ai"),
    HealthConnect("health_connect"),
    CustomColors("custom_colors"),
    // starting a shared room. joining one you were invited to stays free
    FocusTogether("focus_together"),
    // every sound past rain and white noise, see AmbientSound.plus
    AmbientSounds("ambient_sounds");

    companion object {
        fun fromKey(key: String?): PlanFeature? = entries.firstOrNull { it.key == key }
    }
}
