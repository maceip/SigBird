package app.k9mail.library.signatureeditor

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.thunderbird.components.ui.bolt.atom.DividerHorizontal
import net.thunderbird.components.ui.bolt.atom.Surface
import net.thunderbird.components.ui.bolt.atom.text.TextBodySmall
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * WYSIWYG signature editor aligned with formatting that modern Outlook (Windows),
 * Gmail, and Apple Mail reliably render: text styles, colors, sizes, web-safe fonts,
 * lists, alignment, links, horizontal rules, and inline PNG/JPEG/GIF images
 * re-encoded as WebP ≤ 256 KiB and hosted at tokens.public.computer.
 * Navigation is blocked; hosted signature images are fetched via an allow-listed
 * intercept. Network loads are otherwise blocked inside the editor WebView.
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
    val scope = rememberCoroutineScope()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showFontFamilyDialog by remember { mutableStateOf(false) }
    var linkUrl by remember { mutableStateOf("https://") }
    val imageHost = remember {
        SignatureImageHostClient(
            baseUrl = BuildConfig.SIGNATURE_GATEWAY_BASE,
            challengePrefix = BuildConfig.SIGNATURE_CHALLENGE_PREFIX,
        )
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val target = webView
        scope.launch {
            val publicUrl = withContext(Dispatchers.IO) {
                uri.toHostedSignatureImageUrl(context, imageHost)
            } ?: return@launch
            target?.evaluateJavascript(
                "window.SignatureEditor.insertImage(${publicUrl.toJsString()});",
                null,
            )
        }
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
                SignatureFormattingToolbar(
                    onCommand = { command, value -> runCommand(command, value) },
                    onInsertLink = { showLinkDialog = true },
                    onTextColor = { showColorDialog = true },
                    onFontSize = { showFontSizeDialog = true },
                    onFontFamily = { showFontFamilyDialog = true },
                    onInsertImage = {
                        imagePicker.launch(
                            arrayOf("image/png", "image/jpeg", "image/gif", "image/webp"),
                        )
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
        SignatureLinkDialog(
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
        SignatureColorDialog(
            onPick = { hex ->
                runCommand("foreColor", hex)
                showColorDialog = false
            },
            onDismiss = { showColorDialog = false },
        )
    }

    if (showFontSizeDialog) {
        SignatureFontSizeDialog(
            onPick = { size ->
                runCommand("fontSize", size)
                showFontSizeDialog = false
            },
            onDismiss = { showFontSizeDialog = false },
        )
    }

    if (showFontFamilyDialog) {
        SignatureFontFamilyDialog(
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

/**
 * Encodes the picked image to WebP ≤ 256 KiB and uploads it to the signature
 * image gateway. Falls back to a data URI if the upload fails (offline / DevX).
 */
private fun android.net.Uri.toHostedSignatureImageUrl(
    context: android.content.Context,
    hostClient: SignatureImageHostClient,
): String? {
    return try {
        context.contentResolver.openInputStream(this)?.use { input ->
            val bytes = input.readBytes()
            val mime = context.contentResolver.getType(this)
            val webp = SignatureInlineImages.encodeToWebp(bytes, mime) ?: return null
            runCatching { hostClient.uploadWebp(webp) }.getOrElse {
                SignatureInlineImages.encodeBytes(bytes, mime)
            }
        }
    } catch (_: Exception) {
        null
    }
}

private const val EDITOR_HEIGHT_DP = 240

@Suppress("MagicNumber")
private val EDITOR_CANVAS_COLOR = ComposeColor(0xFFFFFFFF)

@Suppress("MagicNumber")
private val EDITOR_CANVAS_TEXT_COLOR = ComposeColor(0xFF1A1A1A)
