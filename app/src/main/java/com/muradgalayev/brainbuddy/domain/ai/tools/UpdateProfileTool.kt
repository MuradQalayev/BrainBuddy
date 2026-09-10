package com.muradgalayev.brainbuddy.domain.ai.tools

import com.muradgalayev.brainbuddy.data.repository.AuthRepository
import com.muradgalayev.brainbuddy.data.repository.UsernameTakenException
import com.muradgalayev.brainbuddy.domain.ai.AiTool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject

// updates the user's display name and/or username on the profiles table. handles the
// unique-username error gracefully, so the AI can suggest alternatives rather than fail silently
class UpdateProfileTool @Inject constructor(
    private val authRepository: AuthRepository,
) : AiTool {

    override val name: String = "update_profile"

    override val description: String =
        "Change the user's display name or username. Pass only the fields the user " +
            "asked to change. Usernames must be unique — if the tool returns " +
            "'username_taken', the caller should ask the user for a different one."

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("display_name") {
                put("type", "string")
                put("description", "New display name shown at the top of Settings.")
            }
            putJsonObject("username") {
                put(
                    "type",
                    "string",
                )
                put(
                    "description",
                    "New public handle. 3–20 letters/numbers/underscores. " +
                        "Must be unique across all users."
                )
            }
        }
    }

    override suspend fun execute(args: JsonObject): String {
        val newName = args["display_name"]?.jsonPrimitive?.content?.trim()
        val newUsername = args["username"]?.jsonPrimitive?.content?.trim()

        if (newName.isNullOrBlank() && newUsername.isNullOrBlank()) {
            return "Nothing to update. Ask the user which field they want to change."
        }

        val current = runCatching { authRepository.getProfile() }.getOrNull()
        val displayName = newName ?: current?.displayName.orEmpty()
        val username = newUsername ?: current?.username.orEmpty()

        if (displayName.isBlank()) return "Failed: display name can't be empty."

        return try {
            authRepository.updateProfile(
                displayName = displayName,
                username = username,
            )
            buildString {
                append("Updated profile — ")
                if (newName != null) append("name: '$newName'")
                if (newName != null && newUsername != null) append(", ")
                if (newUsername != null) append("username: '@$newUsername'")
                append(".")
            }
        } catch (e: UsernameTakenException) {
            "username_taken: '$newUsername' is already used by someone else."
        } catch (e: Exception) {
            "Failed: ${e.message ?: "unknown error"}"
        }
    }
}
