package com.muradgalayev.brainbuddy.di

import com.muradgalayev.brainbuddy.data.ai.GeminiAiClient
import com.muradgalayev.brainbuddy.domain.ai.AiClient
import com.muradgalayev.brainbuddy.domain.ai.AiTool
import com.muradgalayev.brainbuddy.domain.ai.tools.CreateTodoTool
import com.muradgalayev.brainbuddy.domain.ai.tools.DeleteTodoTool
import com.muradgalayev.brainbuddy.domain.ai.tools.UpdateAppearanceTool
import com.muradgalayev.brainbuddy.domain.ai.tools.UpdateTodoTool
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    // Swap providers here: change GeminiAiClient to OpenAiClient/ClaudeClient.
    @Binds
    abstract fun bindAiClient(impl: GeminiAiClient): AiClient

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
}
