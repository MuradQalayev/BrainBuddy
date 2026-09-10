package com.muradgalayev.brainbuddy.domain.ai

import kotlinx.serialization.json.JsonObject

// a capability the AI can invoke. each tool is a self-contained class owning its schema and
// execution logic, and a new capability is a new AiTool registered into AiModule's set
interface AiTool {
    // lowercase, snake_case. sent to the model as the function name
    val name: String

    // human-readable purpose. sent to the model so it knows when to call
    val description: String

    // JSON Schema for arguments, the subset the OpenAI-compatible function-calling APIs accept
    val parametersSchema: JsonObject

    // execute the tool and return a short result string for the model to read back
    suspend fun execute(args: JsonObject): String
}
