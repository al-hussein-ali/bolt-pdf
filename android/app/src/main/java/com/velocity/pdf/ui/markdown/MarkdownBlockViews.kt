package com.velocity.pdf.ui.markdown

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun MarkdownBlockView(
    block: MdBlock,
    directionMode: MdDirectionMode,
    codeBg: Color,
    codeColor: Color,
    linkColor: Color,
) {
    val layoutDir = resolveBlockDirection(block, directionMode)
    val textDir = if (layoutDir == LayoutDirection.Rtl) TextDirection.Rtl else TextDirection.Ltr

    when (block) {
        is MdBlock.Heading -> HeadingBlockView(block, layoutDir, textDir, codeBg, codeColor, linkColor)
        is MdBlock.Paragraph -> ParagraphBlockView(block, layoutDir, textDir, codeBg, codeColor, linkColor)
        is MdBlock.CodeBlock -> CodeBlockView(block)
        is MdBlock.BlockQuote -> BlockQuoteView(block, layoutDir, textDir, codeBg, codeColor, linkColor)
        is MdBlock.Callout -> CalloutBlockView(block, layoutDir, textDir, codeBg, codeColor, linkColor)
        is MdBlock.ListItem -> ListItemBlockView(block, layoutDir, textDir, codeBg, codeColor, linkColor)
        is MdBlock.TaskItem -> TaskItemBlockView(block, layoutDir, textDir, codeBg, codeColor, linkColor)
        is MdBlock.Table -> CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
            MarkdownTableView(block, textDir, codeBg, codeColor, linkColor)
        }
        MdBlock.Divider -> HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

@Composable
private fun HeadingBlockView(
    block: MdBlock.Heading,
    layoutDir: LayoutDirection,
    textDir: TextDirection,
    codeBg: Color,
    codeColor: Color,
    linkColor: Color,
) {
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

@Composable
private fun ParagraphBlockView(
    block: MdBlock.Paragraph,
    layoutDir: LayoutDirection,
    textDir: TextDirection,
    codeBg: Color,
    codeColor: Color,
    linkColor: Color,
) {
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

@Composable
private fun CodeBlockView(block: MdBlock.CodeBlock) {
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

@Composable
private fun BlockQuoteView(
    block: MdBlock.BlockQuote,
    layoutDir: LayoutDirection,
    textDir: TextDirection,
    codeBg: Color,
    codeColor: Color,
    linkColor: Color,
) {
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

@Composable
private fun CalloutBlockView(
    block: MdBlock.Callout,
    layoutDir: LayoutDirection,
    textDir: TextDirection,
    codeBg: Color,
    codeColor: Color,
    linkColor: Color,
) {
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

@Composable
private fun ListItemBlockView(
    block: MdBlock.ListItem,
    layoutDir: LayoutDirection,
    textDir: TextDirection,
    codeBg: Color,
    codeColor: Color,
    linkColor: Color,
) {
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

@Composable
private fun TaskItemBlockView(
    block: MdBlock.TaskItem,
    layoutDir: LayoutDirection,
    textDir: TextDirection,
    codeBg: Color,
    codeColor: Color,
    linkColor: Color,
) {
    CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (block.indentLevel * 16).dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = if (block.checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = if (block.checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 3.dp, end = 8.dp)
                    .size(18.dp)
            )
            Text(
                text = formatInlineMarkdown(block.text, codeBg, codeColor, linkColor),
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 24.sp,
                    textDirection = textDir,
                    textDecoration = if (block.checked) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (block.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.weight(1f)
            )
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
            TableRowView(
                cells = table.headers,
                alignments = table.alignments,
                columnCount = columnCount,
                background = headerBg,
                borderColor = borderColor,
                textDir = textDir,
                isHeader = true,
                codeBg = codeBg,
                codeColor = codeColor,
                linkColor = linkColor
            )
            HorizontalDivider(color = borderColor, thickness = 1.dp)

            table.rows.forEachIndexed { rowIdx, row ->
                TableRowView(
                    cells = row,
                    alignments = table.alignments,
                    columnCount = columnCount,
                    background = if (rowIdx % 2 == 0) evenRowBg else oddRowBg,
                    borderColor = borderColor,
                    textDir = textDir,
                    isHeader = false,
                    codeBg = codeBg,
                    codeColor = codeColor,
                    linkColor = linkColor
                )
                if (rowIdx < table.rows.lastIndex) {
                    HorizontalDivider(color = borderColor, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun TableRowView(
    cells: List<String>,
    alignments: List<TableAlign>,
    columnCount: Int,
    background: Color,
    borderColor: Color,
    textDir: TextDirection,
    isHeader: Boolean,
    codeBg: Color,
    codeColor: Color,
    linkColor: Color,
) {
    Row(
        modifier = Modifier
            .height(IntrinsicSize.Min)
            .background(background)
    ) {
        for (col in 0 until columnCount) {
            TableCellView(
                text = cells.getOrElse(col) { "" },
                align = alignments.getOrElse(col) { TableAlign.DEFAULT },
                textDir = textDir,
                isHeader = isHeader,
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
}

@Composable
private fun TableCellView(
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
    val cellDir = resolveTextDirection(
        text,
        if (textDir == TextDirection.Rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    )
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
