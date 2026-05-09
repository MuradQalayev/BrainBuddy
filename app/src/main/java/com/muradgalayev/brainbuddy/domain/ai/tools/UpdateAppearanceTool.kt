package com.muradgalayev.brainbuddy.domain.ai.tools

import com.muradgalayev.brainbuddy.data.local.FontMode
import com.muradgalayev.brainbuddy.data.local.FontSize
import com.muradgalayev.brainbuddy.data.local.ThemeMode
import com.muradgalayev.brainbuddy.data.repository.PreferencesRepository
import com.muradgalayev.brainbuddy.domain.ai.AiTool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject

class UpdateAppearanceTool @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : AiTool {

    override val name: String = "update_appearance"

    override val description: String =
        "Change the app's appearance. Any field can be omitted to leave it " +
            "unchanged. Use this when the user asks to switch theme, font, or " +
            "font size. Valid theme: Light|Dark|System. Valid font: " +
            "Classic|Modern|Rounded. Valid font_size: Small|Medium|Large."

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("theme") {
                put("type", "string")
                putJsonArray("enum") {
                    add("Light"); add("Dark"); add("System")
                }
                put("description", "Color scheme")
            }
            putJsonObject("font") {
                put("type", "string")
                putJsonArray("enum") {
                    add("Classic"); add("Modern"); add("Rounded")
                }
                put("description", "Typeface family")
            }
            putJsonObject("font_size") {
                put("type", "string")
                putJsonArray("enum") {
                    add("Small"); add("Medium"); add("Large")
                }
                put("description", "Text size")
            }
        }
    }

    override suspend fun execute(args: JsonObject): String {
        val changes = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        args["theme"]?.jsonPrimitive?.content?.let { raw ->
            parseEnum<ThemeMode>(raw)?.let {
                preferencesRepository.setThemeMode(it)
                changes += "theme to $it"
            } ?: run {
                warnings += "theme '$raw' is not available — valid options are " +
                    enumValues<ThemeMode>().joinToString { it.name }
            }
        }
        args["font"]?.jsonPrimitive?.content?.let { raw ->
            parseEnum<FontMode>(raw)?.let {
                preferencesRepository.setFontMode(it)
                changes += "font to $it"
            } ?: run {
                warnings += "font '$raw' is not available — valid options are " +
                    enumValues<FontMode>().joinToString { it.name }
            }
        }
        args["font_size"]?.jsonPrimitive?.content?.let { raw ->
            parseEnum<FontSize>(raw)?.let {
                preferencesRepository.setFontSize(it)
                changes += "font size to $it"
            } ?: run {
                warnings += "font size '$raw' is not available — valid options are " +
                    enumValues<FontSize>().joinToString { it.name }
            }
        }

        return when {
            changes.isEmpty() && warnings.isEmpty() -> "No appearance fields provided."
            changes.isEmpty() -> "Couldn't change anything: ${warnings.joinToString()}."
            warnings.isEmpty() -> "Set ${changes.joinToString()}."
            else -> "Set ${changes.joinToString()}. Skipped: ${warnings.joinToString()}."
        }
    }

    // Case-insensitive enum match so "dark" and "Dark" both work.
    private inline fun <reified T : Enum<T>> parseEnum(raw: String): T? =
        enumValues<T>().firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
}
