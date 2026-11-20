package com.bolt.pdf.ui.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection

internal enum class MdDirectionMode(val label: String) {
    AUTO("Auto"),
    RTL("RTL"),
    LTR("LTR"),
}

internal enum class TableAlign {
    DEFAULT,
    LEFT,
    CENTER,
    RIGHT,
}

internal enum class CalloutType(val title: String) {
    NOTE("Note"),
    TIP("Tip"),
    IMPORTANT("Important"),
    WARNING("Warning"),
    CAUTION("Caution"),
}

internal sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class CodeBlock(val language: String, val code: String) : MdBlock
    data class BlockQuote(val text: String) : MdBlock
    data class Callout(val type: CalloutType, val text: String) : MdBlock
    data class ListItem(val indentLevel: Int, val bullet: String, val text: String) : MdBlock
    data class TaskItem(val indentLevel: Int, val checked: Boolean, val text: String) : MdBlock
    data class Table(
        val headers: List<String>,
        val alignments: List<TableAlign>,
        val rows: List<List<String>>,
    ) : MdBlock
    data object Divider : MdBlock
}

internal fun resolveBlockDirection(block: MdBlock, mode: MdDirectionMode): LayoutDirection {
    return when (mode) {
        MdDirectionMode.RTL -> LayoutDirection.Rtl
        MdDirectionMode.LTR -> LayoutDirection.Ltr
        MdDirectionMode.AUTO -> when (block) {
            is MdBlock.Heading -> resolveTextDirection(block.text)
            is MdBlock.Paragraph -> resolveTextDirection(block.text)
            is MdBlock.BlockQuote -> resolveTextDirection(block.text)
            is MdBlock.Callout -> resolveTextDirection(block.text)
            is MdBlock.ListItem -> resolveTextDirection(block.text)
            is MdBlock.TaskItem -> resolveTextDirection(block.text)
            is MdBlock.Table -> resolveTextDirection((block.headers + block.rows.flatten()).joinToString(" "))
            is MdBlock.CodeBlock, MdBlock.Divider -> LayoutDirection.Ltr
        }
    }
}

internal fun resolveTextDirection(
    text: String,
    fallback: LayoutDirection = LayoutDirection.Ltr,
): LayoutDirection {
    for (ch in text) {
        when (Character.getDirectionality(ch)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE -> return LayoutDirection.Rtl

            Character.DIRECTIONALITY_LEFT_TO_RIGHT,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE -> return LayoutDirection.Ltr
        }
    }
    return fallback
}

internal fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    val lines = raw.replace("\r\n", "\n").split('\n')
    val blocks = mutableListOf<MdBlock>()
    var i = 0
    val paragraphBuffer = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraphBuffer.isNotEmpty()) {
            blocks.add(MdBlock.Paragraph(paragraphBuffer.joinToString(" ")))
            paragraphBuffer.clear()
        }
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            flushParagraph()
            val fence = if (trimmed.startsWith("~~~")) "~~~" else "```"
            val lang = trimmed.removePrefix(fence).trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith(fence)) {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MdBlock.CodeBlock(lang, codeLines.joinToString("\n")))
            i++
            continue
        }

        if (trimmed.isEmpty()) {
            flushParagraph()
            i++
            continue
        }

        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            flushParagraph()
            blocks.add(MdBlock.Divider)
            i++
            continue
        }

        val headingMatch = Regex("^(#{1,6})\\s+(.+)$").find(trimmed)
        if (headingMatch != null) {
            flushParagraph()
            blocks.add(MdBlock.Heading(headingMatch.groupValues[1].length, headingMatch.groupValues[2].trim()))
            i++
            continue
        }

        if (i + 1 < lines.size && trimmed.contains("|") && isTableSeparatorRow(lines[i + 1])) {
            flushParagraph()
            val headers = splitTableRow(trimmed)
            val alignments = parseTableAlignments(lines[i + 1], headers.size)
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size) {
                val rowTrimmed = lines[i].trim()
                if (rowTrimmed.isEmpty() || !rowTrimmed.contains("|") || rowTrimmed.startsWith("```")) break
                rows.add(splitTableRow(rowTrimmed))
                i++
            }
            blocks.add(MdBlock.Table(headers, alignments, rows))
            continue
        }

        if (trimmed.startsWith(">")) {
            flushParagraph()
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines.add(lines[i].trim().removePrefix(">").trimStart())
                i++
            }
            blocks.add(parseQuoteOrCallout(quoteLines))
            continue
        }

        val taskMatch = Regex("^(\\s*)[-*+]\\s+\\[([ xX])]\\s+(.+)$").find(line)
        if (taskMatch != null) {
            flushParagraph()
            val indent = (taskMatch.groupValues[1].length / 2).coerceAtMost(4)
            blocks.add(
                MdBlock.TaskItem(
                    indentLevel = indent,
                    checked = taskMatch.groupValues[2].equals("x", ignoreCase = true),
                    text = taskMatch.groupValues[3].trim()
                )
            )
            i++
            continue
        }

        val bulletMatch = Regex("^(\\s*)[-*+]\\s+(.+)$").find(line)
        if (bulletMatch != null) {
            flushParagraph()
            val indent = (bulletMatch.groupValues[1].length / 2).coerceAtMost(4)
            blocks.add(MdBlock.ListItem(indent, "•", bulletMatch.groupValues[2].trim()))
            i++
            continue
        }

        val orderedMatch = Regex("^(\\s*)(\\d+\\.)\\s+(.+)$").find(line)
        if (orderedMatch != null) {
            flushParagraph()
            val indent = (orderedMatch.groupValues[1].length / 2).coerceAtMost(4)
            blocks.add(MdBlock.ListItem(indent, orderedMatch.groupValues[2], orderedMatch.groupValues[3].trim()))
            i++
            continue
        }

        paragraphBuffer.add(trimmed)
        i++
    }
    flushParagraph()
    return blocks
}

private fun parseQuoteOrCallout(quoteLines: List<String>): MdBlock {
    val firstLine = quoteLines.firstOrNull()?.trim().orEmpty()
    val calloutMatch = Regex("^\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]\\s*(.*)$", RegexOption.IGNORE_CASE)
        .find(firstLine)
        ?: return MdBlock.BlockQuote(quoteLines.joinToString("\n"))

    val type = CalloutType.valueOf(calloutMatch.groupValues[1].uppercase())
    val inlineRest = calloutMatch.groupValues[2].trim()
    val bodyLines = buildList {
        if (inlineRest.isNotEmpty()) add(inlineRest)
        addAll(quoteLines.drop(1))
    }
    return MdBlock.Callout(type, bodyLines.joinToString("\n").trim())
}

private fun isTableSeparatorRow(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.contains("-") || !trimmed.contains("|")) return false
    val cells = splitTableRow(trimmed)
    return cells.isNotEmpty() && cells.all { Regex("^:?-{3,}:?$").matches(it.trim()) }
}

private fun parseTableAlignments(line: String, expectedCols: Int): List<TableAlign> {
    val cells = splitTableRow(line.trim())
    return List(expectedCols) { idx ->
        val token = cells.getOrNull(idx)?.trim().orEmpty()
        when {
            token.startsWith(":") && token.endsWith(":") -> TableAlign.CENTER
            token.startsWith(":") -> TableAlign.LEFT
            token.endsWith(":") -> TableAlign.RIGHT
            else -> TableAlign.DEFAULT
        }
    }
}

private fun splitTableRow(line: String): List<String> {
    var s = line.trim()
    if (s.startsWith("|")) s = s.substring(1)
    if (s.endsWith("|") && !s.endsWith("\\|")) s = s.substring(0, s.length - 1)

    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var idx = 0
    while (idx < s.length) {
        if (s[idx] == '\\' && idx + 1 < s.length && s[idx + 1] == '|') {
            current.append('|')
            idx += 2
            continue
        }
        if (s[idx] == '|') {
            cells.add(current.toString().trim())
            current.clear()
        } else {
            current.append(s[idx])
        }
        idx++
    }
    cells.add(current.toString().trim())
    return cells
}

internal fun formatInlineMarkdown(
    rawText: String,
    codeBg: Color,
    codeColor: Color,
    linkColor: Color,
): AnnotatedString {
    val text = rawText.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    return buildAnnotatedString {
        var idx = 0
        while (idx < text.length) {
            when {
                text.startsWith("***", idx) || text.startsWith("___", idx) -> {
                    val marker = text.substring(idx, idx + 3)
                    val end = text.indexOf(marker, idx + 3)
                    if (end > idx + 3) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                        append(text.substring(idx + 3, end))
                        pop()
                        idx = end + 3
                        continue
                    }
                }
                text.startsWith("**", idx) || text.startsWith("__", idx) -> {
                    val marker = text.substring(idx, idx + 2)
                    val end = text.indexOf(marker, idx + 2)
                    if (end > idx + 2) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(text.substring(idx + 2, end))
                        pop()
                        idx = end + 2
                        continue
                    }
                }
                text.startsWith("~~", idx) -> {
                    val end = text.indexOf("~~", idx + 2)
                    if (end > idx + 2) {
                        pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                        append(text.substring(idx + 2, end))
                        pop()
                        idx = end + 2
                        continue
                    }
                }
                text[idx] == '*' -> {
                    val end = text.indexOf('*', idx + 1)
                    if (end > idx + 1) {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(text.substring(idx + 1, end))
                        pop()
                        idx = end + 1
                        continue
                    }
                }
                text[idx] == '_' && (idx == 0 || !text[idx - 1].isLetterOrDigit()) -> {
                    val end = text.indexOf('_', idx + 1)
                    if (end > idx + 1 && (end + 1 == text.length || !text[end + 1].isLetterOrDigit())) {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(text.substring(idx + 1, end))
                        pop()
                        idx = end + 1
                        continue
                    }
                }
                text[idx] == '`' -> {
                    val end = text.indexOf('`', idx + 1)
                    if (end > idx + 1) {
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBg,
                                color = codeColor
                            )
                        )
                        append(" ${text.substring(idx + 1, end)} ")
                        pop()
                        idx = end + 1
                        continue
                    }
                }
                text[idx] == '[' -> {
                    val closeBracket = text.indexOf(']', idx + 1)
                    if (closeBracket > idx + 1 && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                        val closeParen = text.indexOf(')', closeBracket + 2)
                        if (closeParen > closeBracket + 2) {
                            val label = text.substring(idx + 1, closeBracket)
                            val url = text.substring(closeBracket + 2, closeParen).trim()
                            pushLink(
                                LinkAnnotation.Url(
                                    url = url,
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            color = linkColor,
                                            textDecoration = TextDecoration.Underline
                                        )
                                    )
                                )
                            )
                            append(label)
                            pop()
                            idx = closeParen + 1
                            continue
                        }
                    }
                }
            }
            append(text[idx])
            idx++
        }
    }
}
