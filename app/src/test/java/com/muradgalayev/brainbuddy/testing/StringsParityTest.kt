package com.muradgalayev.brainbuddy.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Italian is a full second language, not a best effort, so a string that exists only in English
// is a bug: the Italian user sees an English line in the middle of an Italian screen. and a
// translation that drops or reorders a placeholder crashes String.format at runtime
class StringsParityTest {

    private val english = TestStrings.all("values") - "app_name"
    private val italian = TestStrings.all("values-it")

    @Test
    fun `every English string has an Italian translation`() {
        val missing = english.keys - italian.keys
        assertTrue("missing from values-it: $missing", missing.isEmpty())
    }

    @Test
    fun `no Italian string is left over from a removed English one`() {
        val orphaned = italian.keys - english.keys
        assertTrue("only in values-it: $orphaned", orphaned.isEmpty())
    }

    @Test
    fun `placeholders match in both languages`() {
        val placeholder = Regex("""%\d+\$[sd]""")
        english.forEach { (key, value) ->
            val it = italian[key] ?: return@forEach
            assertEquals(
                "placeholders differ for $key",
                placeholder.findAll(value).map { m -> m.value }.toSortedSet(),
                placeholder.findAll(it).map { m -> m.value }.toSortedSet(),
            )
        }
    }

    @Test
    fun `no Italian string is empty`() {
        val blank = italian.filterValues { it.isBlank() }.keys
        assertTrue("blank in values-it: $blank", blank.isEmpty())
    }
}
