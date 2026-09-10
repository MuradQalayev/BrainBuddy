package com.muradgalayev.brainbuddy.data.remote

import com.muradgalayev.brainbuddy.data.remote.dto.MedicationDoseLogDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseMedicationDoseLogDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val table = "medication_dose_logs"

    suspend fun getAll(userId: String): List<MedicationDoseLogDto> =
        supabaseClient.from(table)
            .select {
                filter { eq("user_id", userId) }
            }
            .decodeList()

    suspend fun upsert(item: MedicationDoseLogDto) {
        supabaseClient.from(table).upsert(item)
    }
}
