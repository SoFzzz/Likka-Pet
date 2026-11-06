package com.likkapet.presentation.settings

import com.likkapet.presentation.settings.LicenseBlock.Paragraph
import com.likkapet.presentation.settings.LicenseBlock.Separator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LicenseTextTest {
    private val licensesDir = File("src/main/assets/licenses")

    private val sample =
        "﻿Copyright 2019 The Authors\r\n" +
            "\r\n" +
            "-----------\r\n" +
            "SIL OPEN FONT LICENSE Version 1.1\r\n" +
            "-----------\r\n" +
            "\r\n" +
            "PREAMBLE\r\n" +
            "The goals of the license are to\r\n" +
            "stimulate worldwide development, \r\n" +
            "and more.\r\n" +
            "\r\n" +
            "Second paragraph here.\r\n" +
            "\r\n" +
            "1) Neither the Font Software nor\r\n" +
            "its components may be sold.\r\n" +
            "\r\n" +
            "DISCLAIMER\r\n" +
            "THE FONT SOFTWARE IS PROVIDED \"AS IS\", WITHOUT\r\n" +
            "WARRANTY OF ANY KIND.\r\n" +
            "OTHER DEALINGS IN THE FONT SOFTWARE.\r\n"

    @Test
    fun `sample reflows into the expected blocks`() {
        assertEquals(
            listOf(
                Paragraph("Copyright 2019 The Authors"),
                Separator,
                Paragraph("SIL OPEN FONT LICENSE Version 1.1"),
                Separator,
                Paragraph("PREAMBLE"),
                Paragraph("The goals of the license are to stimulate worldwide development, and more."),
                Paragraph("Second paragraph here."),
                Paragraph("1) Neither the Font Software nor its components may be sold."),
                Paragraph("DISCLAIMER"),
                Paragraph(
                    "THE FONT SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND. " +
                        "OTHER DEALINGS IN THE FONT SOFTWARE.",
                ),
            ),
            reflowLicense(sample),
        )
    }

    @Test
    fun `bom and carriage returns are removed`() {
        val text = paragraphs(reflowLicense(sample)).joinToString("\n")
        assertFalse(text.contains('﻿'))
        assertFalse(text.contains('\r'))
    }

    @Test
    fun `real licenses keep their headings separate and the disclaimer body joined`() {
        realLicenses().forEach { (name, raw) ->
            val paragraphs = paragraphs(reflowLicense(raw))
            HEADINGS.forEach { heading -> assertTrue("$name: $heading", heading in paragraphs) }
            val disclaimerBody = paragraphs[paragraphs.indexOf("DISCLAIMER") + 1]
            assertTrue(name, disclaimerBody.startsWith("THE FONT SOFTWARE IS PROVIDED"))
            assertTrue(name, disclaimerBody.endsWith("OTHER DEALINGS IN THE FONT SOFTWARE."))
            (1..5).forEach { n -> assertTrue("$name: condition $n", paragraphs.any { it.startsWith("$n) ") }) }
        }
    }

    @Test
    fun `real licenses keep their separators as separators`() {
        realLicenses().forEach { (name, raw) ->
            val blocks = reflowLicense(raw)
            assertEquals(name, 2, blocks.count { it == Separator })
            assertTrue(name, paragraphs(blocks).none { it.contains("---") })
        }
    }

    @Test
    fun `real licenses lose no words`() {
        realLicenses().forEach { (name, raw) ->
            val reflowed = paragraphs(reflowLicense(raw)).joinToString(" ")
            assertEquals(name, words(raw.removePrefix("﻿")), words(reflowed))
        }
    }

    private fun realLicenses(): List<Pair<String, String>> =
        FontLicense.entries.map { license ->
            val file = File(licensesDir, license.assetPath.removePrefix("licenses/"))
            license.name to file.readText()
        }

    private fun paragraphs(blocks: List<LicenseBlock>): List<String> = blocks.filterIsInstance<Paragraph>().map { it.text }

    private fun words(text: String): List<String> = text.split(Regex("""\s+""")).filter { it.isNotEmpty() && !it.all { c -> c == '-' } }

    private companion object {
        val HEADINGS = listOf("PREAMBLE", "DEFINITIONS", "PERMISSION & CONDITIONS", "TERMINATION", "DISCLAIMER")
    }
}
