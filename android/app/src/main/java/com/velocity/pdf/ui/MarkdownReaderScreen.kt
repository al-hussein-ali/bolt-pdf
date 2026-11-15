package com.velocity.pdf.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class MarkdownDocumentState(
    val displayName: String,
    val content: String,
)

private enum class MdDirectionMode(val label: String) {
    AUTO("Auto"),
    RTL("RTL"),
    LTR("LTR"),
}

private enum class TableAlign {
    DEFAULT,
    LEFT,
    CENTER,
    RIGHT,
}

private enum class CalloutType(val title: String) {
    NOTE("Note"),
    TIP("Tip"),
    IMPORTANT("Important"),
    WARNING("Warning"),
    CAUTION("Caution"),
}

private sealed interface MdBlock {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownReaderScreen(
    document: MarkdownDocumentState,
    onBack: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val blocks = remember(document.content) { parseMarkdownBlocks(document.content) }
    var directionMode by remember(document.content) { mutableStateOf(MdDirectionMode.AUTO) }

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
                },
                actions = {
                    FilterChip(
                        selected = directionMode != MdDirectionMode.AUTO,
                        onClick = {
                            directionMode = when (directionMode) {
                                MdDirectionMode.AUTO -> MdDirectionMode.RTL
                                MdDirectionMode.RTL -> MdDirectionMode.LTR
                                MdDirectionMode.LTR -> MdDirectionMode.AUTO
                            }
                        },
                        label = {
                            Text(
                                text = directionMode.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    )
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
                        val layoutDir = resolveBlockDirection(block, directionMode)
                        val textDir = if (layoutDir == LayoutDirection.Rtl) {
                            TextDirection.Rtl
                        } else {
                            TextDirection.Ltr
                        }

                        when (block) {
                            is MdBlock.Heading -> {
                                CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
                                    val style = when (block.level) {
                                        1 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                                        2 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                        3 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                        else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                    }
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = formatInlineMarkdown(block.text, codeBg, codeColor, linkColor),
                                            style = style.copy(textDirection = textDir),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        if (block.level <= 2) {
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.outlineVariant,
                                                modifier = Modifier.padding(top = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            is MdBlock.Paragraph -> {
                                CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
                                    Text(
                                        text = formatInlineMarkdown(block.text, codeBg, codeColor, linkColor),
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            lineHeight = 25.sp,
                                            textDirection = textDir
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            is MdBlock.CodeBlock -> {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
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
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    textDirection = TextDirection.Ltr
                                                ),
                                                modifier = Modifier.horizontalScroll(rememberScrollState())
                                            )
                                        }
                                    }
                                }
                            }

                            is MdBlock.BlockQuote -> {
                                CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
                                            .padding(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(4.dp)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = formatInlineMarkdown(block.text, codeBg, codeColor, linkColor),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 22.sp,
                                                textDirection = textDir
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            is MdBlock.Callout -> {
                                val accentColor = when (block.type) {
                                    CalloutType.NOTE -> Color(0xFF3B82F6)
                                    CalloutType.TIP -> Color(0xFF10B981)
                                    CalloutType.IMPORTANT -> Color(0xFF8B5CF6)
                                    CalloutType.WARNING -> Color(0xFFF59E0B)
                                    CalloutType.CAUTION -> Color(0xFFEF4444)
                                }
                                val icon = when (block.type) {
                                    CalloutType.NOTE -> Icons.Default.Info
                                    CalloutType.TIP -> Icons.Default.Lightbulb
                                    CalloutType.IMPORTANT -> Icons.Default.ErrorOutline
                                    CalloutType.WARNING -> Icons.Default.WarningAmber
                                    CalloutType.CAUTION -> Icons.Default.ReportProblem
                                }
                                CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(accentColor.copy(alpha = 0.10f))
                                            .padding(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(4.dp)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(accentColor)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = accentColor,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = block.type.title,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = accentColor
                                                )
                                            }
                                            if (block.text.isNotBlank()) {
                                                Text(
                                                    text = formatInlineMarkdown(block.text, codeBg, codeColor, linkColor),
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        lineHeight = 22.sp,
                                                        textDirection = textDir
                                                    ),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            is MdBlock.ListItem -> {
                                CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
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
                                                textDirection = textDir
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            is MdBlock.TaskItem -> {
                                CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = (block.indentLevel * 16).dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            imageVector = if (block.checked) {
                                                Icons.Default.CheckBox
                                            } else {
                                                Icons.Default.CheckBoxOutlineBlank
                                            },
                                            contentDescription = null,
                                            tint = if (block.checked) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier
                                                .padding(top = 3.dp, end = 8.dp)
                                                .size(18.dp)
                                        )
                                        Text(
                                            text = formatInlineMarkdown(block.text, codeBg, codeColor, linkColor),
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                lineHeight = 24.sp,
                                                textDirection = textDir,
                                                textDecoration = if (block.checked) {
                                                    TextDecoration.LineThrough
                                                } else {
                                                    TextDecoration.None
                                                },
                                                color = if (block.checked) {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            is MdBlock.Table -> {
                                CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
                                    MarkdownTableView(
                                        table = block,
                                        textDir = textDir,
                                        codeBg = codeBg,
                                        codeColor = codeColor,
                                        linkColor = linkColor
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

@Composable
private fun MarkdownTableView(
    table: MdBlock.Table,
    textDir: TextDirection,
    codeBg: Color,
    codeColor: Color,
    linkColor: Color,
) {
    val columnCount = table.headers.size.coerceAtLeast(1)
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val headerBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val evenRowBg = MaterialTheme.colorScheme.surfaceContainerLowest
    val oddRowBg = MaterialTheme.colorScheme.surfaceContainerLow

    Surface(
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, borderColor),
        color = evenRowBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            // Header row
            Row(
                modifier = Modifier
                    .height(IntrinsicSize.Min)
                    .background(headerBg)
            ) {
                for (col in 0 until columnCount) {
                    val cellText = table.headers.getOrElse(col) { "" }
                    val align = table.alignments.getOrElse(col) { TableAlign.DEFAULT }
                    TableCell(
                        text = cellText,
                        align = align,
                        textDir = textDir,
                        isHeader = true,
                        codeBg = codeBg,
                        codeColor = codeColor,
                        linkColor = linkColor
                    )
                    if (col < columnCount - 1) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(borderColor)
                        )
                    }
                }
            }

            HorizontalDivider(color = borderColor, thickness = 1.dp)

            // Body rows
            table.rows.forEachIndexed { rowIdx, row ->
                Row(
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .background(if (rowIdx % 2 == 0) evenRowBg else oddRowBg)
                ) {
                    for (col in 0 until columnCount) {
                        val cellText = row.getOrElse(col) { "" }
                        val align = table.alignments.getOrElse(col) { TableAlign.DEFAULT }
                        TableCell(
                            text = cellText,
                            align = align,
                            textDir = textDir,
                            isHeader = false,
                            codeBg = codeBg,
                            codeColor = codeColor,
                            linkColor = linkColor
                        )
                        if (col < columnCount - 1) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(borderColor)
                            )
                        }
                    }
                }
                if (rowIdx < table.rows.lastIndex) {
                    HorizontalDivider(color = borderColor, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    align: TableAlign,
    textDir: TextDirection,
    isHeader: Boolean,
    codeBg: Color,
    codeColor: Color,
    linkColor: Color,
) {
    val textAlign = when (align) {
        TableAlign.LEFT -> TextAlign.Left
        TableAlign.CENTER -> TextAlign.Center
        TableAlign.RIGHT -> TextAlign.Right
        TableAlign.DEFAULT -> TextAlign.Start
    }
    val cellDir = resolveTextDirection(text, if (textDir == TextDirection.Rtl) LayoutDirection.Rtl else LayoutDirection.Ltr)
    val cellTextDir = if (cellDir == LayoutDirection.Rtl) TextDirection.Rtl else TextDirection.Ltr

    Box(
        modifier = Modifier
            .widthIn(min = 96.dp, max = 260.dp)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = when (align) {
            TableAlign.CENTER -> Alignment.Center
            TableAlign.LEFT -> Alignment.CenterStart
            TableAlign.RIGHT -> Alignment.CenterEnd
            TableAlign.DEFAULT -> Alignment.CenterStart
        }
    ) {
        Text(
            text = formatInlineMarkdown(text, codeBg, codeColor, linkColor),
            style = (if (isHeader) {
                MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            } else {
                MaterialTheme.typography.bodyMedium
            }).copy(
                lineHeight = 20.sp,
                textAlign = textAlign,
                textDirection = cellTextDir
            )
        )
    }
}

private fun resolveBlockDirection(block: MdBlock, mode: MdDirectionMode): LayoutDirection {
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
            is MdBlock.Table -> {
                val combined = (block.headers + block.rows.flatten()).joinToString(" ")
                resolveTextDirection(combined)
            }
            is MdBlock.CodeBlock, MdBlock.Divider -> LayoutDirection.Ltr
        }
    }
}

private fun resolveTextDirection(
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
            blocks.add(
                MdBlock.Heading(
                    level = headingMatch.groupValues[1].length,
                    text = headingMatch.groupValues[2].trim()
                )
            )
            i++
            continue
        }

        // GFM Table: header row + separator row (e.g. | --- | :---: | ---: |)
        if (i + 1 < lines.size && trimmed.contains("|") && isTableSeparatorRow(lines[i + 1])) {
            flushParagraph()
            val headers = splitTableRow(trimmed)
            val alignments = parseTableAlignments(lines[i + 1], headers.size)
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size) {
                val rowTrimmed = lines[i].trim()
                if (rowTrimmed.isEmpty() || !rowTrimmed.contains("|") || rowTrimmed.startsWith("```")) {
                    break
                }
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
            val firstLine = quoteLines.firstOrNull()?.trim().orEmpty()
            val calloutMatch = Regex("^\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]\\s*(.*)$", RegexOption.IGNORE_CASE)
                .find(firstLine)
            if (calloutMatch != null) {
                val type = CalloutType.valueOf(calloutMatch.groupValues[1].uppercase())
                val inlineRest = calloutMatch.groupValues[2].trim()
                val bodyLines = buildList {
                    if (inlineRest.isNotEmpty()) add(inlineRest)
                    addAll(quoteLines.drop(1))
                }
                blocks.add(MdBlock.Callout(type, bodyLines.joinToString("\n").trim()))
            } else {
                blocks.add(MdBlock.BlockQuote(quoteLines.joinToString("\n")))
            }
            continue
        }

        val taskMatch = Regex("^(\\s*)[-*+]\\s+\\[([ xX])]\\s+(.+)$").find(line)
        if (taskMatch != null) {
            flushParagraph()
            val indent = (taskMatch.groupValues[1].length / 2).coerceAtMost(4)
            val checked = taskMatch.groupValues[2].equals("x", ignoreCase = true)
            blocks.add(MdBlock.TaskItem(indent, checked, taskMatch.groupValues[3].trim()))
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

private fun isTableSeparatorRow(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.contains("-") || !trimmed.contains("|")) return false
    val cells = splitTableRow(trimmed)
    if (cells.isEmpty()) return false
    return cells.all { cell ->
        Regex("^:?-{3,}:?$").matches(cell.trim())
    }
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

private fun formatInlineMarkdown(
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
