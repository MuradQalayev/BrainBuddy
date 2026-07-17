package com.muradgalayev.brainbuddy.di

import com.muradgalayev.brainbuddy.data.ai.DeepSeekAiClient
import com.muradgalayev.brainbuddy.domain.ai.AiClient
import com.muradgalayev.brainbuddy.domain.ai.AiTool
import com.muradgalayev.brainbuddy.domain.ai.tools.CreateCalendarEventTool
import com.muradgalayev.brainbuddy.domain.ai.tools.CreateTodoTool
import com.muradgalayev.brainbuddy.domain.ai.tools.DeleteCalendarEventTool
import com.muradgalayev.brainbuddy.domain.ai.tools.DeleteTodoTool
import com.muradgalayev.brainbuddy.domain.ai.tools.ListCalendarEventsForDateTool
import com.muradgalayev.brainbuddy.domain.ai.tools.ListTodosForDateTool
import com.muradgalayev.brainbuddy.domain.ai.tools.StartPomodoroTool
import com.muradgalayev.brainbuddy.domain.ai.tools.UpdateAdhdProfileTool
import com.muradgalayev.brainbuddy.domain.ai.tools.UpdateAppearanceTool
import com.muradgalayev.brainbuddy.domain.ai.tools.UpdateCalendarEventTool
import com.muradgalayev.brainbuddy.domain.ai.tools.UpdateProfileTool
import com.muradgalayev.brainbuddy.domain.ai.tools.UpdateTodoTool
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    // Swap providers here — one line. GeminiAiClient / DeepSeekAiClient / ClaudeClient / …
    @Binds
    abstract fun bindAiClient(impl: DeepSeekAiClient): AiClient

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
}
