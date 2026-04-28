package com.muradgalayev.brainbuddy.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // TODO: Replace these with your actual Supabase project credentials
    private const val SUPABASE_URL = "https://vksxhizgedzatiohhohk.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZrc3hoaXpnZWR6YXRpb2hob2hrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzcyNzc5OTAsImV4cCI6MjA5Mjg1Mzk5MH0.c7WIjV96HfJ9ZE6q6siC5cJhjK_4OMrcNQ3XDQaM9jM"

    @Singleton
    @Provides
    fun provideSupabaseClient(): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            install(Postgrest)
            install(Auth)
        }
    }
}
