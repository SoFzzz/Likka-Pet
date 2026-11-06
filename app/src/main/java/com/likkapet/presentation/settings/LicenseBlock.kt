package com.likkapet.presentation.settings

/** One display block of a reflowed license text. */
internal sealed interface LicenseBlock {
    /** A run of text shown as a single, freely wrapping paragraph. */
    data class Paragraph(
        val text: String,
    ) : LicenseBlock

    /** A line made only of dashes, shown as a divider instead of wrapping text. */
    data object Separator : LicenseBlock
}

private const val BYTE_ORDER_MARK = '﻿'
private const val MAX_HEADING_WORDS = 4
private val numberedConditionStart = Regex("""^\d+\)""")
private val sentencePunctuation = charArrayOf('.', ',', ';', ':')

/**
 * Reflows a hard-wrapped plain-text license (as shipped in `assets/licenses/`) for display in a
 * narrow dialog. Display-time only: the asset stays verbatim. Strips the BOM, normalizes line
 * endings, splits paragraphs on blank lines and joins the hard-wrapped lines inside each one,
 * keeping dash-only separators, short all-caps headings and numbered conditions on their own.
 */
internal fun reflowLicense(raw: String): List<LicenseBlock> {
    val lines =
        raw
            .removePrefix(BYTE_ORDER_MARK.toString())
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map { it.trim() }
    val blocks = mutableListOf<LicenseBlock>()
    val pending = mutableListOf<String>()

    fun flushParagraph() {
        if (pending.isNotEmpty()) blocks += LicenseBlock.Paragraph(pending.joinToString(" "))
        pending.clear()
    }

    lines.forEach { line ->
        when {
            line.isEmpty() -> {
                flushParagraph()
            }

            isSeparator(line) -> {
                flushParagraph()
                blocks += LicenseBlock.Separator
            }

            isHeading(line) -> {
                flushParagraph()
                blocks += LicenseBlock.Paragraph(line)
            }

            numberedConditionStart.containsMatchIn(line) -> {
                flushParagraph()
                pending += line
            }

            else -> {
                pending += line
            }
        }
    }
    flushParagraph()
    return blocks
}

private fun isSeparator(line: String): Boolean = line.all { it == '-' }

/** `PREAMBLE`, `PERMISSION & CONDITIONS`... but not the all-caps body lines of `DISCLAIMER`. */
private fun isHeading(line: String): Boolean =
    line.any { it.isLetter() } &&
        line.none { it.isLowerCase() } &&
        line.split(' ').size <= MAX_HEADING_WORDS &&
        line.last() !in sentencePunctuation
