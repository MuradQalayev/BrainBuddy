package com.muradgalayev.brainbuddy.domain.ai.local

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

// the matches are the easy half. what these mostly guard is the refusals, every case where the
// resolver has to hand the turn to the model instead of guessing, because a wrong local match
// performs an action nobody asked for and spends no tokens doing it, so there's no cost signal
// to notice it by
class LocalIntentResolverTest {

    private val resolver = LocalIntentResolver()

    private fun arg(text: String, key: String): String? =
        resolver.resolve(text)?.args?.get(key)?.jsonPrimitive?.content

    // matches

    @Test
    fun `font names are recognised on their own`() {
        assertEquals("OpenDyslexic", arg("opendyslexic", "font"))
        assertEquals("OpenDyslexic", arg("switch to open dyslexic", "font"))
        assertEquals("OpenDyslexic", arg("use the dyslexic font please", "font"))
        assertEquals("Atkinson", arg("change font to atkinson", "font"))
    }

    @Test
    fun `arial needs a verb because the word is too common alone`() {
        assertEquals("Arial", arg("set the font to arial", "font"))
        assertNull(resolver.resolve("arial"))
    }

    @Test
    fun `theme switches`() {
        assertEquals("Dark", arg("dark mode", "theme"))
        assertEquals("Light", arg("switch to light theme", "theme"))
        assertEquals("System", arg("use system theme", "theme"))
    }

    @Test
    fun `text size`() {
        assertEquals("Large", arg("make the text bigger", "font_size"))
        assertEquals("Small", arg("smaller text", "font_size"))
    }

    @Test
    fun `spacing`() {
        assertEquals("Relaxed", arg("more spacing", "text_spacing"))
        assertEquals("Loose", arg("loosest spacing", "text_spacing"))
    }

    @Test
    fun `a match carries a reply and the right tool`() {
        val intent = resolver.resolve("dark mode")
        assertNotNull(intent)
        assertEquals("update_appearance", intent!!.toolName)
        assertEquals(true, intent.reply.isNotBlank())
    }

    // refusals: these must reach the model

    @Test
    fun `questions are never actions`() {
        assertNull(resolver.resolve("how do i change the font?"))
        assertNull(resolver.resolve("how do i change the font"))
        assertNull(resolver.resolve("what font am i using"))
        assertNull(resolver.resolve("can you make the text bigger?"))
    }

    @Test
    fun `a second clause means more is being asked`() {
        assertNull(resolver.resolve("change the font to opendyslexic and add a todo for tomorrow"))
    }

    @Test
    fun `colour words in passing are not theme switches`() {
        // no 'mode' or 'theme', so these are conversation rather than commands
        assertNull(resolver.resolve("it was dark outside"))
        assertNull(resolver.resolve("i feel light today"))
    }

    @Test
    fun `bigger spacing is spacing, not size`() {
        // both rules can see this sentence, and size must yield to spacing
        assertEquals("Relaxed", arg("bigger spacing", "text_spacing"))
        assertNull(arg("bigger spacing", "font_size"))
    }

    @Test
    fun `anything with free text goes to the model`() {
        assertNull(resolver.resolve("add a todo call the pharmacy"))
        assertNull(resolver.resolve("gym tomorrow at 6"))
        assertNull(resolver.resolve("what's on my calendar"))
    }

    @Test
    fun `empty and junk input is refused`() {
        assertNull(resolver.resolve(""))
        assertNull(resolver.resolve("   "))
        assertNull(resolver.resolve("..."))
    }

    @Test
    fun `a bare size word without a subject is refused`() {
        // 'bigger' alone could be about anything on screen
        assertNull(resolver.resolve("bigger"))
        assertNull(resolver.resolve("make it bigger"))
    }
}
