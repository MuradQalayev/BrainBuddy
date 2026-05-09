package com.muradgalayev.brainbuddy.domain.ai

import kotlinx.serialization.json.JsonObject

/**
 * A capability the AI can invoke. Each tool is a self-contained class
 * that owns its schema and execution logic. New capabilities = new AiTool
 * implementation registered into the multibinding set in AiModule.
 */
interface AiTool {
    /** Lowercase, snake_case. Sent to the model as the function name. */
    val name: String

    /** Human-readable purpose. Sent to the model so it knows when to call. */
    val description: String

    /**
     * JSON Schema for arguments — Gemini-compatible OpenAPI-ish subset.
     * Example:
     * {
     *   "type": "object",
     *   "properties": { "title": {"type":"string"} },
     *   "required": ["title"]
     * }
     */
    val parametersSchema: JsonObject

    /** Execute the tool and return a short result string for the model to read back. */
    suspend fun execute(args: JsonObject): String
}
