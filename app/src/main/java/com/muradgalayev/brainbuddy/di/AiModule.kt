package com.muradgalayev.brainbuddy.di

import com.muradgalayev.brainbuddy.data.ai.MistralAiClient
import com.muradgalayev.brainbuddy.data.ai.PiiRedactor
import com.muradgalayev.brainbuddy.data.ai.RedactingAiClient
import com.muradgalayev.brainbuddy.domain.ai.AiClient
import com.muradgalayev.brainbuddy.domain.ai.AiTool
import com.muradgalayev.brainbuddy.domain.ai.tools.CreateCalendarEventTool
import com.muradgalayev.brainbuddy.domain.ai.tools.SplitCalendarEventTool
import com.muradgalayev.brainbuddy.domain.ai.tools.CreateTodoTool
import com.muradgalayev.brainbuddy.domain.ai.tools.ContactTool
import com.muradgalayev.brainbuddy.domain.ai.tools.DeleteCalendarEventTool
import com.muradgalayev.brainbuddy.domain.ai.tools.DeleteTodoTool
import com.muradgalayev.brainbuddy.domain.ai.tools.ExportCalendarToGoogleTool
import com.muradgalayev.brainbuddy.domain.ai.tools.FindCareTool
import com.muradgalayev.brainbuddy.domain.ai.tools.ListCalendarEventsForDateTool
import com.muradgalayev.brainbuddy.domain.ai.tools.ListTodosForDateTool
import com.muradgalayev.brainbuddy.domain.ai.tools.NavigateTool
import com.muradgalayev.brainbuddy.domain.ai.tools.StartPomodoroTool
import com.muradgalayev.brainbuddy.domain.ai.tools.SuggestTimeTool
import com.muradgalayev.brainbuddy.domain.ai.tools.UpdateAdhdProfileTool
import com.muradgalayev.brainbuddy.domain.ai.tools.UpdateAppearanceTool
import com.muradgalayev.brainbuddy.domain.ai.tools.UpdateNotificationSettingsTool
import com.muradgalayev.brainbuddy.domain.ai.tools.UpdateCalendarEventTool
import com.muradgalayev.brainbuddy.domain.ai.tools.UpdateProfileTool
import com.muradgalayev.brainbuddy.domain.ai.tools.UpdateTodoTool
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Qualifier
import javax.inject.Singleton

// marks the raw provider client. everything outside this module injects the plain AiClient,
// which is always the redacting wrapper, so no caller can reach a third-party model without
// passing through PiiRedactor
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UpstreamAiClient

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    // the single provider binding. Mistral isn't just the current pick, it's an EU-hosted one,
    // which is what keeps prompts out of a third-country transfer. anything bound here has to be
    // able to make the same claim, so this isn't a line to swap casually for whatever model
    // benchmarks best
    @Binds
    @UpstreamAiClient
    abstract fun bindUpstreamAiClient(impl: MistralAiClient): AiClient

    @Binds
    @IntoSet
    abstract fun bindCreateTodoTool(tool: CreateTodoTool): AiTool

    @Binds
    @IntoSet
    abstract fun bindUpdateTodoTool(tool: UpdateTodoTool): AiTool

    @Binds
    @IntoSet
    abstract fun bindDeleteTodoTool(tool: DeleteTodoTool): AiTool

    @Binds
    @IntoSet
    abstract fun bindUpdateAppearanceTool(tool: UpdateAppearanceTool): AiTool

    @Binds
    @IntoSet
    abstract fun bindCreateCalendarEventTool(tool: CreateCalendarEventTool): AiTool

    @Binds
    @IntoSet
    abstract fun bindUpdateCalendarEventTool(tool: UpdateCalendarEventTool): AiTool

    @Binds
    @IntoSet
    abstract fun bindDeleteCalendarEventTool(tool: DeleteCalendarEventTool): AiTool

    @Binds
    @IntoSet
    abstract fun bindSplitCalendarEventTool(tool: SplitCalendarEventTool): AiTool

    @Binds
    @IntoSet
    abstract fun bindStartPomodoroTool(tool: StartPomodoroTool): AiTool

    @Binds
    @IntoSet
    abstract fun bindUpdateProfileTool(tool: UpdateProfileTool): AiTool

    @Binds
    @IntoSet
    abstract fun bindListTodosForDateTool(tool: ListTodosForDateTool): AiTool

    @Binds
    @IntoSet
    abstract fun bindListCalendarEventsForDateTool(tool: ListCalendarEventsForDateTool): AiTool

    @Binds
    @IntoSet
    abstract fun bindUpdateAdhdProfileTool(tool: UpdateAdhdProfileTool): AiTool

    @Binds
    @IntoSet
    abstract fun bindNavigateTool(tool: NavigateTool): AiTool

    @Binds
    @IntoSet
    abstract fun bindExportCalendarToGoogleTool(tool: ExportCalendarToGoogleTool): AiTool

    @Binds
    @IntoSet
    abstract fun bindUpdateNotificationSettingsTool(tool: UpdateNotificationSettingsTool): AiTool

    @Binds
    @IntoSet
    abstract fun bindFindCareTool(tool: FindCareTool): AiTool

    @Binds
    @IntoSet
    abstract fun bindContactTool(tool: ContactTool): AiTool

    @Binds
    @IntoSet
    abstract fun bindSuggestTimeTool(tool: SuggestTimeTool): AiTool

    companion object {
        // the only AiClient the rest of the app can see
        @Provides
        @Singleton
        fun provideAiClient(
            @UpstreamAiClient upstream: AiClient,
            redactor: PiiRedactor,
        ): AiClient = RedactingAiClient(upstream, redactor)
    }
}
