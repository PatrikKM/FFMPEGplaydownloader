package com.ffmpegplaydownloader.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ffmpegplaydownloader.app.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun ReadmeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var rawMarkdown by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var copiedFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("README.md").bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.readText()
                }
            }
        }
        result.onSuccess { content ->
            rawMarkdown = content
        }.onFailure { err ->
            loadError = err.message ?: "Nepodařilo se načíst dokumentaci README.md."
        }
    }

    LaunchedEffect(copiedFeedback) {
        if (copiedFeedback) {
            delay(2000L)
            copiedFeedback = false
        }
    }

    val allBlocks = remember(rawMarkdown) {
        rawMarkdown?.let(::parseMarkdownBlocks) ?: emptyList()
    }

    val filteredBlocks = remember(allBlocks, searchQuery) {
        filterMarkdownBlocks(allBlocks, searchQuery)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(16.dp),
            color = AppColors.BgSurface,
            border = BorderStroke(1.dp, AppColors.Border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "📖 README / Nápověda",
                            color = AppColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Dokumentace přibalená přímo v APK",
                            color = AppColors.TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Zavřít",
                            tint = AppColors.TextSecondary
                        )
                    }
                }

                // Live search input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "Hledat v README...",
                            color = AppColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = AppColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Vymazat hledání",
                                    tint = AppColors.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AppColors.BgMain,
                        unfocusedContainerColor = AppColors.BgMain,
                        focusedBorderColor = AppColors.PrimaryBlue,
                        unfocusedBorderColor = AppColors.Border,
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary,
                        cursorColor = AppColors.PrimaryBlue
                    )
                )

                // Body content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.BgElevated)
                        .border(1.dp, AppColors.Border, RoundedCornerShape(12.dp))
                ) {
                    when {
                        loadError != null -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Dokumentaci se nepodařilo načíst",
                                    color = AppColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = loadError.orEmpty(),
                                    color = AppColors.TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        rawMarkdown == null -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = AppColors.PrimaryBlue,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        filteredBlocks.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isBlank()) {
                                        "Soubor README.md je prázdný."
                                    } else {
                                        "Nenalezeny žádné shody pro „${searchQuery.trim()}“."
                                    },
                                    color = AppColors.TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        else -> {
                            SelectionContainer {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(
                                        items = filteredBlocks,
                                        key = { index, block -> "${index}_${block.hashCode()}" }
                                    ) { _, block ->
                                        MarkdownBlockView(block = block)
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val content = rawMarkdown.orEmpty()
                            if (content.isNotEmpty()) {
                                clipboardManager.setText(AnnotatedString(content))
                                copiedFeedback = true
                            }
                        },
                        enabled = !rawMarkdown.isNullOrEmpty(),
                        border = BorderStroke(
                            1.dp,
                            if (copiedFeedback) AppColors.PrimaryBlue else AppColors.Border
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (copiedFeedback) AppColors.PrimaryBlue else AppColors.TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (copiedFeedback) "Zkopírováno" else "Kopírovat",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.PrimaryBlue,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Zavřít",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

private sealed interface MarkdownBlock {
    val searchableText: String

    data class Heading(val level: Int, val text: String) : MarkdownBlock {
        override val searchableText: String get() = text
    }

    data class Paragraph(val text: String) : MarkdownBlock {
        override val searchableText: String get() = text
    }

    data class BulletItem(val indentLevel: Int, val text: String) : MarkdownBlock {
        override val searchableText: String get() = text
    }

    data class NumberedItem(val indentLevel: Int, val number: String, val text: String) : MarkdownBlock {
        override val searchableText: String get() = "$number. $text"
    }

    data class Blockquote(val text: String) : MarkdownBlock {
        override val searchableText: String get() = text
    }

    data class CodeBlock(val language: String, val code: String) : MarkdownBlock {
        override val searchableText: String get() = "$language\n$code"
    }

    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock {
        override val searchableText: String
            get() = buildString {
                if (headers.isNotEmpty()) appendLine(headers.joinToString(" "))
                rows.forEach { appendLine(it.joinToString(" ")) }
            }
    }

    data object Divider : MarkdownBlock {
        override val searchableText: String get() = ""
    }
}

private val BULLET_REGEX = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val NUMBERED_REGEX = Regex("""^(\s*)(\d+)\.\s+(.*)$""")
private val DIVIDER_REGEX = Regex("""^([-*_])\1{2,}$""")
private val TABLE_SEP_CELL_REGEX = Regex("""^:?-{2,}:?$""")

private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    if (markdown.isBlank()) return emptyList()

    val lines = markdown.replace("\r\n", "\n").replace("\r", "\n").split('\n')
    val blocks = mutableListOf<MarkdownBlock>()

    val paragraphLines = mutableListOf<String>()
    val quoteLines = mutableListOf<String>()
    val codeLines = mutableListOf<String>()
    val tableLines = mutableListOf<String>()
    var inCodeBlock = false
    var codeLanguage = ""

    fun flushParagraph() {
        if (paragraphLines.isNotEmpty()) {
            blocks += MarkdownBlock.Paragraph(paragraphLines.joinToString(" "))
            paragraphLines.clear()
        }
    }

    fun flushQuote() {
        if (quoteLines.isNotEmpty()) {
            blocks += MarkdownBlock.Blockquote(quoteLines.joinToString("\n"))
            quoteLines.clear()
        }
    }

    fun flushTable() {
        if (tableLines.isEmpty()) return
        val parsedRows = tableLines.map(::splitTableRow)
        tableLines.clear()
        if (parsedRows.isEmpty()) return

        val hasSeparatorAtSecond = parsedRows.size >= 2 && isSeparatorCells(parsedRows[1])
        val headers: List<String>
        val bodyRows: List<List<String>>
        if (hasSeparatorAtSecond) {
            headers = parsedRows[0]
            bodyRows = parsedRows.drop(2).filterNot(::isSeparatorCells)
        } else {
            headers = emptyList()
            bodyRows = parsedRows.filterNot(::isSeparatorCells)
        }

        val colCount = maxOf(headers.size, bodyRows.maxOfOrNull { it.size } ?: 0)
        if (colCount > 0) {
            val normalizedHeaders = if (headers.isNotEmpty()) {
                List(colCount) { idx -> headers.getOrElse(idx) { "" } }
            } else {
                emptyList()
            }
            val normalizedRows = bodyRows.map { row ->
                List(colCount) { idx -> row.getOrElse(idx) { "" } }
            }
            blocks += MarkdownBlock.Table(normalizedHeaders, normalizedRows)
        }
    }

    fun flushOpenTextBlocks() {
        flushParagraph()
        flushQuote()
        flushTable()
    }

    for (rawLine in lines) {
        val trimmed = rawLine.trim()

        if (inCodeBlock) {
            if (trimmed.startsWith("```")) {
                blocks += MarkdownBlock.CodeBlock(codeLanguage, codeLines.joinToString("\n"))
                codeLines.clear()
                codeLanguage = ""
                inCodeBlock = false
            } else {
                codeLines += rawLine
            }
            continue
        }

        if (trimmed.startsWith("```")) {
            flushOpenTextBlocks()
            inCodeBlock = true
            codeLanguage = trimmed.removePrefix("```").trim()
            codeLines.clear()
            continue
        }

        if (trimmed.isEmpty()) {
            flushOpenTextBlocks()
            continue
        }

        if (trimmed.startsWith("|")) {
            flushParagraph()
            flushQuote()
            tableLines += trimmed
            continue
        } else if (tableLines.isNotEmpty()) {
            flushTable()
        }

        if (DIVIDER_REGEX.matches(trimmed)) {
            flushOpenTextBlocks()
            blocks += MarkdownBlock.Divider
            continue
        }

        val headingLevel = parseHeadingLevel(trimmed)
        if (headingLevel > 0) {
            flushOpenTextBlocks()
            val headingText = trimmed.drop(headingLevel).trim()
            if (headingText.isNotEmpty()) {
                blocks += MarkdownBlock.Heading(headingLevel, headingText)
            }
            continue
        }

        if (trimmed.startsWith(">")) {
            flushParagraph()
            quoteLines += trimmed.removePrefix(">").trimStart()
            continue
        } else if (quoteLines.isNotEmpty()) {
            flushQuote()
        }

        val bulletMatch = BULLET_REGEX.matchEntire(rawLine)
        if (bulletMatch != null) {
            flushParagraph()
            val indent = (bulletMatch.groupValues[1].length / 2).coerceIn(0, 4)
            blocks += MarkdownBlock.BulletItem(indent, bulletMatch.groupValues[2].trim())
            continue
        }

        val numberedMatch = NUMBERED_REGEX.matchEntire(rawLine)
        if (numberedMatch != null) {
            flushParagraph()
            val indent = (numberedMatch.groupValues[1].length / 2).coerceIn(0, 4)
            blocks += MarkdownBlock.NumberedItem(
                indentLevel = indent,
                number = numberedMatch.groupValues[2],
                text = numberedMatch.groupValues[3].trim()
            )
            continue
        }

        paragraphLines += trimmed
    }

    if (inCodeBlock) {
        blocks += MarkdownBlock.CodeBlock(codeLanguage, codeLines.joinToString("\n"))
        codeLines.clear()
    }
    flushOpenTextBlocks()

    return blocks
}

private fun parseHeadingLevel(trimmedLine: String): Int {
    var count = 0
    while (count < trimmedLine.length && trimmedLine[count] == '#' && count < 4) {
        count++
    }
    if (count == 0) return 0
    return if (count < trimmedLine.length && trimmedLine[count].isWhitespace()) count else 0
}

private fun splitTableRow(trimmedLine: String): List<String> {
    var inner = trimmedLine
    if (inner.startsWith("|")) inner = inner.drop(1)
    if (inner.endsWith("|")) inner = inner.dropLast(1)
    if (inner.isBlank()) return emptyList()
    return inner.split('|').map { it.trim() }
}

private fun isSeparatorCells(cells: List<String>): Boolean {
    if (cells.isEmpty()) return false
    return cells.all { cell ->
        val c = cell.trim()
        c.isNotEmpty() && TABLE_SEP_CELL_REGEX.matches(c)
    }
}

private fun filterMarkdownBlocks(
    blocks: List<MarkdownBlock>,
    query: String
): List<MarkdownBlock> {
    val q = query.trim()
    if (q.isEmpty()) return blocks

    val result = mutableListOf<MarkdownBlock>()
    var activeHeading: MarkdownBlock.Heading? = null
    var activeHeadingEmitted = false

    for (block in blocks) {
        if (block is MarkdownBlock.Heading) {
            activeHeading = block
            activeHeadingEmitted = false
            if (block.searchableText.contains(q, ignoreCase = true)) {
                result += block
                activeHeadingEmitted = true
            }
            continue
        }

        if (block is MarkdownBlock.Divider) continue

        if (block.searchableText.contains(q, ignoreCase = true)) {
            if (!activeHeadingEmitted && activeHeading != null) {
                result += activeHeading
                activeHeadingEmitted = true
            }
            result += block
        }
    }

    return result
}

@Composable
private fun MarkdownBlockView(block: MarkdownBlock) {
    when (block) {
        is MarkdownBlock.Heading -> {
            val fontSize = when (block.level) {
                1 -> 18.sp
                2 -> 15.sp
                3 -> 13.5.sp
                else -> 13.sp
            }
            val textColor = when (block.level) {
                2 -> AppColors.PrimaryBlue
                else -> AppColors.TextPrimary
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (block.level <= 2) 4.dp else 2.dp)
            ) {
                Text(
                    text = buildMarkdownInlineText(block.text),
                    color = textColor,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    lineHeight = (fontSize.value + 5f).sp
                )
                if (block.level <= 2) {
                    Spacer(Modifier.height(3.dp))
                    HorizontalDivider(
                        color = if (block.level == 1) AppColors.PrimaryBlue.copy(alpha = 0.5f) else AppColors.Border,
                        thickness = 1.dp
                    )
                }
            }
        }

        is MarkdownBlock.Paragraph -> {
            Text(
                text = buildMarkdownInlineText(block.text),
                color = AppColors.TextSecondary,
                fontSize = 12.5.sp,
                lineHeight = 18.sp
            )
        }

        is MarkdownBlock.BulletItem -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (block.indentLevel * 12).dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "•",
                    color = AppColors.PrimaryBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = buildMarkdownInlineText(block.text),
                    color = AppColors.TextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        is MarkdownBlock.NumberedItem -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (block.indentLevel * 12).dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "${block.number}.",
                    color = AppColors.PrimaryBlue,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = buildMarkdownInlineText(block.text),
                    color = AppColors.TextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        is MarkdownBlock.Blockquote -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.BgSurface)
                    .border(1.dp, AppColors.Border, RoundedCornerShape(8.dp))
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(AppColors.PrimaryBlue)
                )
                Text(
                    text = buildMarkdownInlineText(block.text),
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }

        is MarkdownBlock.CodeBlock -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.BgMain)
                    .border(1.dp, AppColors.Border, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (block.language.isNotEmpty()) {
                    Text(
                        text = block.language.uppercase(),
                        color = AppColors.PrimaryBlue,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = block.code,
                        color = AppColors.TextPrimary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        is MarkdownBlock.Table -> {
            val colCount = maxOf(block.headers.size, block.rows.maxOfOrNull { it.size } ?: 0)
            if (colCount > 0) {
                val colWidths = List(colCount) { colIdx ->
                    val maxChars = maxOf(
                        block.headers.getOrNull(colIdx)?.length ?: 0,
                        block.rows.maxOfOrNull { it.getOrNull(colIdx)?.length ?: 0 } ?: 0
                    )
                    (maxChars * 6.5f + 24f).dp.coerceIn(110.dp, 280.dp)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.BgMain)
                        .border(1.dp, AppColors.Border, RoundedCornerShape(8.dp))
                        .horizontalScroll(rememberScrollState())
                ) {
                    Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                        if (block.headers.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AppColors.BgSurface)
                            ) {
                                block.headers.forEachIndexed { colIdx, cell ->
                                    Text(
                                        text = buildMarkdownInlineText(cell),
                                        color = AppColors.TextPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .width(colWidths[colIdx])
                                            .padding(horizontal = 10.dp, vertical = 7.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = AppColors.Border, thickness = 1.dp)
                        }
                        block.rows.forEachIndexed { rowIdx, row ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                row.forEachIndexed { colIdx, cell ->
                                    Text(
                                        text = buildMarkdownInlineText(cell),
                                        color = AppColors.TextSecondary,
                                        fontSize = 11.5.sp,
                                        lineHeight = 16.sp,
                                        modifier = Modifier
                                            .width(colWidths[colIdx])
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            if (rowIdx < block.rows.lastIndex) {
                                HorizontalDivider(
                                    color = AppColors.Border.copy(alpha = 0.6f),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }
                }
            }
        }

        MarkdownBlock.Divider -> {
            HorizontalDivider(
                color = AppColors.Border,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

private fun buildMarkdownInlineText(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    val len = text.length
    while (i < len) {
        // Inline code: `...`
        if (text[i] == '`') {
            val end = text.indexOf('`', startIndex = i + 1)
            if (end > i + 1) {
                val codeContent = text.substring(i + 1, end)
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = AppColors.PrimaryBlue,
                        background = AppColors.BgMain
                    )
                ) {
                    append(codeContent)
                }
                i = end + 1
                continue
            }
        }

        // Bold: **...**
        if (i + 1 < len && text[i] == '*' && text[i + 1] == '*') {
            val end = text.indexOf("**", startIndex = i + 2)
            if (end > i + 2) {
                val boldContent = text.substring(i + 2, end)
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                ) {
                    append(buildMarkdownInlineText(boldContent))
                }
                i = end + 2
                continue
            }
        }

        // Italic: *...*
        if (text[i] == '*') {
            val end = text.indexOf('*', startIndex = i + 1)
            if (end > i + 1) {
                val italicContent = text.substring(i + 1, end)
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(italicContent)
                }
                i = end + 1
                continue
            }
        }

        // Markdown link: [label](target)
        if (text[i] == '[') {
            val closeBracket = text.indexOf(']', startIndex = i + 1)
            if (closeBracket > i + 1 && closeBracket + 1 < len && text[closeBracket + 1] == '(') {
                val closeParen = text.indexOf(')', startIndex = closeBracket + 2)
                if (closeParen > closeBracket + 1) {
                    val label = text.substring(i + 1, closeBracket)
                    withStyle(
                        SpanStyle(
                            color = AppColors.PrimaryBlue,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append(label)
                    }
                    i = closeParen + 1
                    continue
                }
            }
        }

        append(text[i])
        i++
    }
}
