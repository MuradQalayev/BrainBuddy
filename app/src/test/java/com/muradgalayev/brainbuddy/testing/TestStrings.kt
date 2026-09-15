package com.muradgalayev.brainbuddy.testing

import com.muradgalayev.brainbuddy.R
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

// the real strings.xml files as lookups, for JVM tests that need to read copy without Robolectric.
// pass one wherever production code takes Context::getString, or to UiText.resolve(lookup)
object TestStrings {
    val en: (Int) -> String by lazy { lookup("values") }
    val it: (Int) -> String by lazy { lookup("values-it") }

    // every key in a file, for tests that check a whole language rather than one string
    fun all(dir: String): Map<String, String> = parse(resFile(dir))

    private val names: Map<Int, String> by lazy {
        R.string::class.java.fields.associate { it.getInt(null) to it.name }
    }

    private fun lookup(dir: String): (Int) -> String {
        val values = parse(resFile(dir))
        return { id ->
            val name = names[id] ?: error("no R.string field for id $id")
            values[name] ?: error("$name missing from $dir/strings.xml")
        }
    }

    // unit tests run from the module directory under Gradle and from the project root in some IDE
    // configurations
    private fun resFile(dir: String): File =
        listOf("src/main/res/$dir/strings.xml", "app/src/main/res/$dir/strings.xml")
            .map(::File)
            .firstOrNull { it.exists() }
            ?: error("can't find $dir/strings.xml from ${File(".").absolutePath}")

    private fun parse(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val node = nodes.item(i)
            node.attributes.getNamedItem("name").nodeValue to unescape(node.textContent)
        }
    }

    // the subset of aapt's escaping this project's resources use
    private fun unescape(raw: String): String = buildString {
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                when (val n = raw[i + 1]) {
                    'n' -> append('\n')
                    't' -> append('\t')
                    'u' -> {
                        append(raw.substring(i + 2, i + 6).toInt(16).toChar())
                        i += 4
                    }
                    else -> append(n)
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}
