package app.k9mail.library.signatureeditor

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import net.thunderbird.components.ui.bolt.atom.DividerHorizontal
import net.thunderbird.components.ui.bolt.atom.DividerVertical
import net.thunderbird.components.ui.bolt.atom.Surface
import net.thunderbird.components.ui.bolt.atom.button.ButtonIcon
import net.thunderbird.components.ui.bolt.atom.button.ButtonIconColors
import net.thunderbird.components.ui.bolt.atom.button.ButtonIconDefaults
import net.thunderbird.components.ui.bolt.atom.button.ButtonText
import net.thunderbird.components.ui.bolt.atom.icon.Icons
import net.thunderbird.components.ui.bolt.atom.text.TextBodySmall
import net.thunderbird.components.ui.bolt.atom.textfield.TextFieldOutlined
import net.thunderbird.components.ui.bolt.organism.AlertDialog
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * WYSIWYG signature editor aligned with formatting that modern Outlook (Windows),
 * Gmail, and Apple Mail reliably render: text styles, colors, sizes, web-safe fonts,
 * lists, alignment, links, horizontal rules, and inline PNG/JPEG images (capped/scaled
 * to 2 MiB). Navigation and network loads are blocked inside the editor WebView.
 */
@Suppress("LongMethod")
@Composable
fun SignatureHtmlEditor(
    html: String,
    onHtmlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.signature_editor_label),
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showFontFamilyDialog by remember { mutableStateOf(false) }
    var linkUrl by remember { mutableStateOf("https://") }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val dataUri = uri.toInlineImageDataUri(context) ?: return@rememberLauncherForActivityResult
        webView?.evaluateJavascript(
            "window.SignatureEditor.insertImage(${dataUri.toJsString()});",
            null,
        )
    }

    fun runCommand(command: String, value: String? = null) {
        val js = if (value == null) {
            "window.SignatureEditor.command(${command.toJsString()});"
        } else {
            "window.SignatureEditor.command(${command.toJsString()}, ${value.toJsString()});"
        }
        webView?.evaluateJavascript(js, null)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("signature_html_editor"),
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half),
    ) {
        TextBodySmall(
            text = label,
            color = BoltTheme.colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = BoltTheme.spacings.double),
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = BoltTheme.spacings.double)
                .border(
                    width = 1.dp,
                    color = BoltTheme.colors.outlineVariant,
                    shape = BoltTheme.shapes.small,
                ),
            shape = BoltTheme.shapes.small,
            color = BoltTheme.colors.surfaceContainerLow,
            contentColor = BoltTheme.colors.onSurface,
        ) {
            Column {
                FormattingToolbar(
                    onCommand = { command, value -> runCommand(command, value) },
                    onInsertLink = { showLinkDialog = true },
                    onTextColor = { showColorDialog = true },
                    onFontSize = { showFontSizeDialog = true },
                    onFontFamily = { showFontFamilyDialog = true },
                    onInsertImage = {
                        imagePicker.launch(arrayOf("image/png", "image/jpeg"))
                    },
                )

                DividerHorizontal(color = BoltTheme.colors.outlineVariant)

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = EDITOR_CANVAS_COLOR,
                    contentColor = EDITOR_CANVAS_TEXT_COLOR,
                    shape = BoltTheme.shapes.extraSmall,
                ) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(EDITOR_HEIGHT_DP.dp)
                            .testTag("signature_html_editor_webview"),
                        factory = { ctx ->
                            createSignatureEditorWebView(
                                context = ctx,
                                initialHtml = html,
                                onHtmlChange = onHtmlChange,
                            ).also { created ->
                                created.contentDescription = "signature_html_editor_webview"
                                created.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                webView = created
                            }
                        },
                        update = { view ->
                            webView = view
                        },
                    )
                }
            }
        }
    }

    if (showLinkDialog) {
        LinkDialog(
            linkUrl = linkUrl,
            onLinkUrlChange = { linkUrl = it },
            onConfirm = {
                val safeUrl = linkUrl.trim()
                if (safeUrl.startsWith("https://") ||
                    safeUrl.startsWith("http://") ||
                    safeUrl.startsWith("mailto:")
                ) {
                    webView?.evaluateJavascript(
                        "window.SignatureEditor.insertLink(${safeUrl.toJsString()});",
                        null,
                    )
                }
                showLinkDialog = false
                linkUrl = "https://"
            },
            onDismiss = {
                showLinkDialog = false
                linkUrl = "https://"
            },
        )
    }

    if (showColorDialog) {
        ColorDialog(
            onPick = { hex ->
                runCommand("foreColor", hex)
                showColorDialog = false
            },
            onDismiss = { showColorDialog = false },
        )
    }

    if (showFontSizeDialog) {
        FontSizeDialog(
            onPick = { size ->
                runCommand("fontSize", size)
                showFontSizeDialog = false
            },
            onDismiss = { showFontSizeDialog = false },
        )
    }

    if (showFontFamilyDialog) {
        FontFamilyDialog(
            onPick = { family ->
                runCommand("fontName", family)
                showFontFamilyDialog = false
            },
            onDismiss = { showFontFamilyDialog = false },
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.evaluateJavascript("window.SignatureEditor && window.SignatureEditor.flush();", null)
            webView?.destroy()
            webView = null
        }
    }
}

@Composable
private fun FormattingToolbar(
    onCommand: (String, String?) -> Unit,
    onInsertLink: () -> Unit,
    onTextColor: () -> Unit,
    onFontSize: () -> Unit,
    onFontFamily: () -> Unit,
    onInsertImage: () -> Unit,
) {
    val iconColors = ButtonIconDefaults.buttonIconColors(
        contentColor = BoltTheme.colors.onSurface,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(
                horizontal = BoltTheme.spacings.half,
                vertical = BoltTheme.spacings.quarter,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StyleToolbarButtons(onCommand = onCommand, colors = iconColors)
        ToolbarDivider()
        TypographyToolbarButtons(
            onFontFamily = onFontFamily,
            onFontSize = onFontSize,
            onTextColor = onTextColor,
            colors = iconColors,
        )
        ToolbarDivider()
        ListToolbarButtons(onCommand = onCommand, colors = iconColors)
        ToolbarDivider()
        AlignToolbarButtons(onCommand = onCommand, colors = iconColors)
        ToolbarDivider()
        InsertToolbarButtons(
            onCommand = onCommand,
            onInsertLink = onInsertLink,
            onInsertImage = onInsertImage,
            colors = iconColors,
        )
    }
}

@Composable
private fun StyleToolbarButtons(
    onCommand: (String, String?) -> Unit,
    colors: ButtonIconColors,
) {
    ToolbarIconButton(
        onClick = { onCommand("bold", null) },
        imageVector = Icons.Outlined.FormatBold,
        contentDescription = stringResource(R.string.signature_editor_bold),
        colors = colors,
        modifier = Modifier.testTag("signature_editor_bold"),
    )
    ToolbarIconButton(
        onClick = { onCommand("italic", null) },
        imageVector = Icons.Outlined.FormatItalic,
        contentDescription = stringResource(R.string.signature_editor_italic),
        colors = colors,
        modifier = Modifier.testTag("signature_editor_italic"),
    )
    ToolbarIconButton(
        onClick = { onCommand("underline", null) },
        imageVector = Icons.Outlined.FormatUnderlined,
        contentDescription = stringResource(R.string.signature_editor_underline),
        colors = colors,
        modifier = Modifier.testTag("signature_editor_underline"),
    )
    ToolbarIconButton(
        onClick = { onCommand("strikeThrough", null) },
        imageVector = Icons.Outlined.FormatStrikethrough,
        contentDescription = stringResource(R.string.signature_editor_strikethrough),
        colors = colors,
        modifier = Modifier.testTag("signature_editor_strikethrough"),
    )
}

@Composable
private fun TypographyToolbarButtons(
    onFontFamily: () -> Unit,
    onFontSize: () -> Unit,
    onTextColor: () -> Unit,
    colors: ButtonIconColors,
) {
    // Web-safe fonts / inline styles survive Gmail, Outlook, and Apple Mail.
    ToolbarIconButton(
        onClick = onFontFamily,
        imageVector = Icons.Outlined.Description,
        contentDescription = stringResource(R.string.signature_editor_font_family),
        colors = colors,
        modifier = Modifier.testTag("signature_editor_font_family"),
    )
    ToolbarIconButton(
        onClick = onFontSize,
        imageVector = Icons.Outlined.FormatSize,
        contentDescription = stringResource(R.string.signature_editor_font_size),
        colors = colors,
        modifier = Modifier.testTag("signature_editor_font_size"),
    )
    ToolbarIconButton(
        onClick = onTextColor,
        imageVector = Icons.Outlined.FormatColorText,
        contentDescription = stringResource(R.string.signature_editor_text_color),
        colors = colors,
        modifier = Modifier.testTag("signature_editor_text_color"),
    )
}

@Composable
private fun ListToolbarButtons(
    onCommand: (String, String?) -> Unit,
    colors: ButtonIconColors,
) {
    ToolbarIconButton(
        onClick = { onCommand("insertUnorderedList", null) },
        imageVector = Icons.Outlined.FormatListBulleted,
        contentDescription = stringResource(R.string.signature_editor_bulleted_list),
        colors = colors,
        modifier = Modifier.testTag("signature_editor_bulleted_list"),
    )
    ToolbarIconButton(
        onClick = { onCommand("insertOrderedList", null) },
        imageVector = Icons.Outlined.FormatListNumbered,
        contentDescription = stringResource(R.string.signature_editor_numbered_list),
        colors = colors,
        modifier = Modifier.testTag("signature_editor_numbered_list"),
    )
}

@Composable
private fun AlignToolbarButtons(
    onCommand: (String, String?) -> Unit,
    colors: ButtonIconColors,
) {
    ToolbarIconButton(
        onClick = { onCommand("justifyLeft", null) },
        imageVector = Icons.Outlined.FormatAlignLeft,
        contentDescription = stringResource(R.string.signature_editor_align_left),
        colors = colors,
        modifier = Modifier.testTag("signature_editor_align_left"),
    )
    ToolbarIconButton(
        onClick = { onCommand("justifyCenter", null) },
        imageVector = Icons.Outlined.FormatAlignCenter,
        contentDescription = stringResource(R.string.signature_editor_align_center),
        colors = colors,
        modifier = Modifier.testTag("signature_editor_align_center"),
    )
    ToolbarIconButton(
        onClick = { onCommand("justifyRight", null) },
        imageVector = Icons.Outlined.FormatAlignRight,
        contentDescription = stringResource(R.string.signature_editor_align_right),
        colors = colors,
        modifier = Modifier.testTag("signature_editor_align_right"),
    )
}

@Composable
private fun InsertToolbarButtons(
    onCommand: (String, String?) -> Unit,
    onInsertLink: () -> Unit,
    onInsertImage: () -> Unit,
    colors: ButtonIconColors,
) {
    ToolbarIconButton(
        onClick = onInsertLink,
        imageVector = Icons.Outlined.Link,
        contentDescription = stringResource(R.string.signature_editor_insert_link),
        colors = colors,
        modifier = Modifier.testTag("signature_editor_link"),
    )
    ToolbarIconButton(
        onClick = onInsertImage,
        imageVector = Icons.Outlined.Image,
        contentDescription = stringResource(R.string.signature_editor_insert_image),
        colors = colors,
        modifier = Modifier.testTag("signature_editor_image"),
    )
    ToolbarIconButton(
        onClick = { onCommand("insertHorizontalRule", null) },
        imageVector = Icons.Outlined.HorizontalRule,
        contentDescription = stringResource(R.string.signature_editor_horizontal_rule),
        colors = colors,
        modifier = Modifier.testTag("signature_editor_horizontal_rule"),
    )
}

@Composable
private fun ToolbarDivider() {
    DividerVertical(
        modifier = Modifier
            .height(TOOLBAR_DIVIDER_HEIGHT_DP.dp)
            .padding(horizontal = BoltTheme.spacings.half),
        color = BoltTheme.colors.outlineVariant,
    )
}

@Composable
private fun ToolbarIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String,
    colors: ButtonIconColors,
    modifier: Modifier = Modifier,
) {
    ButtonIcon(
        onClick = onClick,
        imageVector = imageVector,
        contentDescription = contentDescription,
        colors = colors,
        modifier = modifier,
    )
}

@Composable
private fun LinkDialog(
    linkUrl: String,
    onLinkUrlChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = stringResource(R.string.signature_editor_link_label),
        confirmText = stringResource(R.string.signature_editor_save),
        dismissText = stringResource(android.R.string.cancel),
        onConfirmClick = onConfirm,
        onDismissClick = onDismiss,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("signature_editor_link_dialog"),
    ) {
        TextFieldOutlined(
            value = linkUrl,
            onValueChange = onLinkUrlChange,
            label = stringResource(R.string.signature_editor_link_label),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ColorDialog(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = stringResource(R.string.signature_editor_text_color),
        confirmText = stringResource(android.R.string.cancel),
        onConfirmClick = onDismiss,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("signature_editor_color_dialog"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default),
        ) {
            TEXT_COLORS.forEach { (hex, color) ->
                Box(
                    modifier = Modifier
                        .size(COLOR_SWATCH_DP.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, BoltTheme.colors.outline, CircleShape)
                        .clickable { onPick(hex) }
                        .testTag("signature_editor_color_$hex"),
                )
            }
        }
    }
}

@Composable
private fun FontSizeDialog(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = stringResource(R.string.signature_editor_font_size),
        confirmText = stringResource(android.R.string.cancel),
        onConfirmClick = onDismiss,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("signature_editor_font_size_dialog"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half)) {
            FONT_SIZES.forEach { (labelRes, value) ->
                ButtonText(
                    text = stringResource(labelRes),
                    onClick = { onPick(value) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signature_editor_font_size_$value"),
                )
            }
        }
    }
}

@Composable
private fun FontFamilyDialog(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = stringResource(R.string.signature_editor_font_family),
        confirmText = stringResource(android.R.string.cancel),
        onConfirmClick = onDismiss,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("signature_editor_font_family_dialog"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half)) {
            FONT_FAMILIES.forEach { family ->
                ButtonText(
                    text = family,
                    onClick = { onPick(family) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signature_editor_font_family_$family"),
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createSignatureEditorWebView(
    context: android.content.Context,
    initialHtml: String,
    onHtmlChange: (String) -> Unit,
): WebView {
    return SignatureEditorWebViewFactory.create(
        context = context,
        initialHtml = initialHtml,
        onHtmlChange = onHtmlChange,
    ).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }
}

private fun android.net.Uri.toInlineImageDataUri(context: android.content.Context): String? {
    return try {
        context.contentResolver.openInputStream(this)?.use { input ->
            val bytes = input.readBytes()
            val mime = context.contentResolver.getType(this)
            SignatureInlineImages.encodeBytes(bytes, mime)
        }
    } catch (_: Exception) {
        null
    }
}

private const val EDITOR_HEIGHT_DP = 240
private const val TOOLBAR_DIVIDER_HEIGHT_DP = 24
private const val COLOR_SWATCH_DP = 36

@Suppress("MagicNumber")
private val EDITOR_CANVAS_COLOR = ComposeColor(0xFFFFFFFF)

@Suppress("MagicNumber")
private val EDITOR_CANVAS_TEXT_COLOR = ComposeColor(0xFF1A1A1A)

// Six-character hex only — Outlook legacy renderers reject #rgb shorthand.
@Suppress("MagicNumber")
private val TEXT_COLORS = listOf(
    "#1A1A1A" to ComposeColor(0xFF1A1A1A),
    "#B3261E" to ComposeColor(0xFFB3261E),
    "#0B57D0" to ComposeColor(0xFF0B57D0),
    "#0F7B3F" to ComposeColor(0xFF0F7B3F),
    "#7B2D8E" to ComposeColor(0xFF7B2D8E),
    "#B06000" to ComposeColor(0xFFB06000),
)

// execCommand fontSize uses 1–7; map to labels users understand.
private val FONT_SIZES = listOf(
    R.string.signature_editor_font_size_small to "2",
    R.string.signature_editor_font_size_normal to "3",
    R.string.signature_editor_font_size_large to "5",
    R.string.signature_editor_font_size_huge to "6",
)

// Web-safe stacks that Gmail / Outlook / Apple Mail all honor.
private val FONT_FAMILIES = listOf(
    "Arial",
    "Helvetica",
    "Georgia",
    "Times New Roman",
    "Courier New",
    "Verdana",
)
