package com.luma.lifeos.language

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

/** Exercise the formatting contract used by Android resources before packaging. */
class TranslationCatalogTest {
    private fun catalog(folder: String): Map<String, Element> {
        val file = File("src/main/res/$folder/strings.xml")
        assertTrue("Missing offline translation bundle: $folder", file.isFile)
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        return (0 until root.childNodes.length).mapNotNull { root.childNodes.item(it) as? Element }
            .associateBy { it.getAttribute("name") }
    }

    @Test fun everySupportedLanguageHasCompleteUsableFormattingAndPluralForms() {
        val english = catalog("values")
        val arguments = Regex("%(?:([0-9]+)\\$)?[-#+ 0,(]*[0-9]*(?:\\.[0-9]+)?([dsf])")
        for (tag in listOf("fa", "fr")) {
            val translated = catalog("values-$tag")
            assertEquals("Resource coverage for $tag", english.keys, translated.keys)
            for ((name, base) in english) {
                val localized = translated.getValue(name)
                assertEquals(name, base.tagName, localized.tagName)
                val originals = if (base.tagName == "plurals") {
                    listOf(base.getElementsByTagName("item").item(0).textContent)
                } else listOf(base.textContent)
                val variants = if (localized.tagName == "plurals") {
                    val items = localized.getElementsByTagName("item")
                    val quantities = (0 until items.length).map { (items.item(it) as Element).getAttribute("quantity") }
                    assertTrue("$tag/$name other plural", "other" in quantities)
                    assertTrue("$tag/$name one plural", "one" in quantities)
                    if (tag == "fr") assertTrue("French million plural for $name", "many" in quantities)
                    (0 until items.length).map { items.item(it).textContent }
                } else listOf(localized.textContent)
                val signature = arguments.findAll(originals.first()).map { it.value }.sorted().toList()
                for (variant in variants) {
                    assertTrue("Empty translation: $tag/$name", variant.isNotBlank())
                    assertEquals("Formatting arguments: $tag/$name", signature,
                        arguments.findAll(variant).map { it.value }.sorted().toList())
                    val inputs = arguments.findAll(originals.first()).map { match ->
                        when (match.groupValues[2]) { "d" -> 2; "f" -> 2.5; else -> "نام / nom" }
                    }.toList()
                    // Positional arguments in source order are ordered by index in production.
                    val indexed = arguments.findAll(originals.first()).toList()
                    val ordered = indexed.zip(inputs).sortedBy { it.first.groupValues[1].toIntOrNull() ?: 1 }
                        .distinctBy { it.first.groupValues[1] }.map { it.second }
                    String.format(Locale.forLanguageTag(tag), variant, *ordered.toTypedArray())
                }
            }
        }
    }
}
