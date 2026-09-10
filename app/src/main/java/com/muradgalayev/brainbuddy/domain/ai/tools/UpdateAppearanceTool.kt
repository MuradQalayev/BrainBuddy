package com.muradgalayev.brainbuddy.domain.ai.tools

import com.muradgalayev.brainbuddy.data.local.FontMode
import com.muradgalayev.brainbuddy.data.local.FontSize
import com.muradgalayev.brainbuddy.data.local.TextSpacing
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
    private val preferencesRepository: PreferencesRepository,
    private val modeManager: com.muradgalayev.brainbuddy.data.local.ModeManager,
) : AiTool {

    override val name: String = "update_appearance"

    override val description: String =
        "Change the app's appearance. Any field can be omitted to leave it " +
            "unchanged. Use this when the user asks to switch theme, font, " +
            "font size, or text spacing. Valid theme: Light|Dark|System. " +
            "Valid font: Arial|OpenDyslexic|Atkinson. Pick OpenDyslexic when the " +
            "user mentions dyslexia, Atkinson when they mention low vision. " +
            "Valid font_size: Small|Medium|Large. " +
            "Valid text_spacing: Normal|Relaxed|Loose — this opens up the gaps " +
            "between lines and letters without making the text bigger. Reach for it " +
            "when the user says text feels cramped, crowded, or dense, or that they " +
            "keep losing their place or re-reading the same line."

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
                // built from FontMode so a new typeface can't be added to the app and stay invisible to the
                // model, which is how OpenDyslexic went unreachable here for its entire life
                putJsonArray("enum") {
                    FontMode.entries.forEach { add(it.name) }
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
            putJsonObject("text_spacing") {
                put("type", "string")
                putJsonArray("enum") {
                    TextSpacing.entries.forEach { add(it.name) }
                }
                put("description", "Space between lines and letters, independent of size")
            }
        }
    }

    override suspend fun execute(args: JsonObject): String {
        modeManager.activeModeNow()?.let { mode ->
            return "Failed: Customization isn't available while ${mode.name} mode is active. " +
                "Turn it off or edit that mode first."
        }

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

        args["text_spacing"]?.jsonPrimitive?.content?.let { raw ->
            parseEnum<TextSpacing>(raw)?.let {
                preferencesRepository.setTextSpacing(it)
                changes += "text spacing to $it"
            } ?: run {
                warnings += "text spacing '$raw' is not available — valid options are " +
                    enumValues<TextSpacing>().joinToString { it.name }
            }
        }

        return when {
            changes.isEmpty() && warnings.isEmpty() -> "No appearance fields provided."
            changes.isEmpty() -> "Couldn't change anything: ${warnings.joinToString()}."
            warnings.isEmpty() -> "Set ${changes.joinToString()}."
            else -> "Set ${changes.joinToString()}. Skipped: ${warnings.joinToString()}."
        }
    }

    // case-insensitive enum match, so 'dark' and 'Dark' both work
    private inline fun <reified T : Enum<T>> parseEnum(raw: String): T? =
        enumValues<T>().firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
}
