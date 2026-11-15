package com.velocity.pdf.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class MarkdownDocumentState(
    val displayName: String,
    val content: String,
)

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class CodeBlock(val language: String, val code: String) : MdBlock
    data class BlockQuote(val text: String) : MdBlock
    data class ListItem(val indentLevel: Int, val bullet: String, val text: String) : MdBlock
    data object Divider : MdBlock
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownReaderScreen(
    document: MarkdownDocumentState,
    onBack: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val blocks = remember(document.content) { parseMarkdownBlocks(document.content) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                title = {
                    Column {
                        Text(
                            text = document.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = strings.markdownReader,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (blocks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = strings.emptyMarkdown,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val codeBg = MaterialTheme.colorScheme.surfaceContainerHigh
            val codeColor = MaterialTheme.colorScheme.primary
            val linkColor = MaterialTheme.colorScheme.primary

            SelectionContainer {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(blocks) { block ->
                        when (block) {
                            is MdBlock.Heading -> {
                                val style = when (block.level) {
                                    1 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                                    2 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                    3 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                    else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                }
                                Text(
                                    text = formatInlineMarkdown(block.text, codeBg, codeColor, linkColor),
                                    style = style.copy(textDirection = TextDirection.Content),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (block.level <= 2) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }

                            is MdBlock.Paragraph -> {
                                Text(
                                    text = formatInlineMarkdown(block.text, codeBg, codeColor, linkColor),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        lineHeight = 25.sp,
                                        textDirection = TextDirection.Content
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            is MdBlock.CodeBlock -> {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        if (block.language.isNotBlank()) {
                                            Text(
                                                text = block.language,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(bottom = 6.dp)
                                            )
                                        }
                                        Text(
                                            text = block.code,
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 13.sp,
                                                lineHeight = 20.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            modifier = Modifier.horizontalScroll(rememberScrollState())
                                        )
                                    }
                                }
                            }

                            is MdBlock.BlockQuote -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
                                        .padding(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(22.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = formatInlineMarkdown(block.text, codeBg, codeColor, linkColor),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 22.sp,
                                            textDirection = TextDirection.Content
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            is MdBlock.ListItem -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = (block.indentLevel * 16).dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = block.bullet,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        text = formatInlineMarkdown(block.text, codeBg, codeColor, linkColor),
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            lineHeight = 24.sp,
                                            textDirection = TextDirection.Content
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            MdBlock.Divider -> {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseMarkdownBlocks(raw: String): List<MdBlock> {
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

        if (trimmed.startsWith("```")) {
            flushParagraph()
            val lang = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
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
            blocks.add(
                MdBlock.Heading(
                    level = headingMatch.groupValues[1].length,
                    text = headingMatch.groupValues[2].trim()
                )
            )
            i++
            continue
        }

        if (trimmed.startsWith(">")) {
            flushParagraph()
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines.add(lines[i].trim().removePrefix(">").trimStart())
                i++
            }
            blocks.add(MdBlock.BlockQuote(quoteLines.joinToString("\n")))
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

private fun formatInlineMarkdown(
    text: String,
    codeBg: Color,
    codeColor: Color,
    linkColor: Color,
): AnnotatedString {
    return buildAnnotatedString {
        var idx = 0
        while (idx < text.length) {
            when {
                text.startsWith("***", idx) -> {
                    val end = text.indexOf("***", idx + 3)
                    if (end > idx + 3) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                        append(text.substring(idx + 3, end))
                        pop()
                        idx = end + 3
                        continue
                    }
                }
                text.startsWith("**", idx) -> {
                    val end = text.indexOf("**", idx + 2)
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
                            pushStyle(
                                SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                            append(text.substring(idx + 1, closeBracket))
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
