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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.thunderbird.components.ui.bolt.atom.DividerHorizontal
import net.thunderbird.components.ui.bolt.atom.Surface
import net.thunderbird.components.ui.bolt.atom.text.TextBodySmall
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * WYSIWYG signature editor aligned with formatting that modern Outlook (Windows),
 * Gmail, and Apple Mail reliably render: bold / italic / underline, text sizes,
 * links, and inline PNG/JPEG/GIF images re-encoded as WebP ≤ 256 KiB.
 *
 * Picked images appear in the editor immediately as a local data URI; the upload
 * to tokens.public.computer runs in the background and swaps the src to the
 * hosted URL when it lands. Navigation is blocked; hosted signature images are
 * fetched via an allow-listed intercept. All other editor network requests are
 * answered with an empty response.
 */
@Suppress("LongMethod")
@Composable
fun SignatureHtmlEditor(
    html: String,
    onHtmlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    controller: SignatureHtmlEditorController? = null,
    label: String = stringResource(R.string.signature_editor_label),
    bringIntoViewRequester: BringIntoViewRequester? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestHtml by rememberUpdatedState(html)
    var webView by remember { mutableStateOf<SignatureEditorWebView?>(null) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var linkUrl by remember { mutableStateOf("https://") }
    var activeFormats by remember { mutableStateOf(emptySet<String>()) }
    var imageInsertStatus by remember { mutableStateOf<ImageInsertStatus>(ImageInsertStatus.Idle) }
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
        insertPickedImage(
            context = context,
            uri = uri,
            imageHost = imageHost,
            webViewProvider = { webView },
            currentHtmlProvider = { latestHtml },
            onHtmlChange = onHtmlChange,
            onStatus = { imageInsertStatus = it },
        )
    }

    fun runCommand(command: String, value: String? = null, keepActionMode: Boolean = false) {
        val editor = webView ?: return
        if (!keepActionMode) {
            editor.finishActiveActionMode()
            editor.requestFocus()
        }
        val js = if (value == null) {
            "window.SignatureEditor.command(${command.toJsString()});"
        } else {
            "window.SignatureEditor.command(${command.toJsString()}, ${value.toJsString()});"
        }
        editor.evaluateJavascript(js, null)
    }

    val relocationModifier = if (bringIntoViewRequester != null) {
        Modifier.bringIntoViewRequester(bringIntoViewRequester)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(relocationModifier)
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
                // Canvas first so the system text-selection ActionMode (above the
                // caret) does not sit on top of the formatting controls.
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
                                onFormatStateChange = { activeFormats = it },
                            ).also { created ->
                                created.contentDescription = "signature_html_editor_webview"
                                created.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                // Bold / italic / underline ride inside the system
                                // text-selection popup, where the selection still exists.
                                created.formatActionHandler = { command ->
                                    created.evaluateJavascript(
                                        "window.SignatureEditor.command(${command.toJsString()});",
                                        null,
                                    )
                                }
                                if (bringIntoViewRequester != null) {
                                    created.setOnFocusChangeListener { _, hasFocus ->
                                        if (hasFocus) {
                                            scope.launch {
                                                bringIntoViewRequester.bringIntoView()
                                            }
                                        }
                                    }
                                }
                                webView = created
                                controller?.attachWebView(created)
                            }
                        },
                        update = { view ->
                            webView = view
                            controller?.attachWebView(view)
                        },
                    )
                }

                DividerHorizontal(color = BoltTheme.colors.outlineVariant)

                SignatureFormattingToolbar(
                    boldActive = "bold" in activeFormats,
                    italicActive = "italic" in activeFormats,
                    underlineActive = "underline" in activeFormats,
                    onCommand = { command, value -> runCommand(command, value) },
                    onFontSize = {
                        webView?.finishActiveActionMode()
                        showFontSizeDialog = true
                    },
                    onInsertLink = {
                        webView?.finishActiveActionMode()
                        showLinkDialog = true
                    },
                    onInsertImage = {
                        webView?.finishActiveActionMode()
                        imagePicker.launch(
                            arrayOf("image/png", "image/jpeg", "image/gif", "image/webp"),
                        )
                    },
                )
            }
        }

        ImageInsertStatusBanner(status = imageInsertStatus)
    }

    SignatureEditorDialogHost(
        showLinkDialog = showLinkDialog,
        linkUrl = linkUrl,
        onLinkUrlChange = { linkUrl = it },
        onLinkConfirm = {
            insertLinkIntoEditor(webView, linkUrl)
            showLinkDialog = false
            linkUrl = "https://"
        },
        onLinkDismiss = {
            showLinkDialog = false
            linkUrl = "https://"
        },
        showFontSizeDialog = showFontSizeDialog,
        onFontSizePick = { size ->
            runCommand("fontSize", size)
            showFontSizeDialog = false
        },
        onFontSizeDismiss = { showFontSizeDialog = false },
    )

    DisposableEffect(Unit) {
        onDispose {
            controller?.attachWebView(null)
            webView?.evaluateJavascript("window.SignatureEditor && window.SignatureEditor.flush();", null)
            webView?.destroy()
            webView = null
        }
    }
}

@Composable
private fun ImageInsertStatusBanner(status: ImageInsertStatus) {
    when (status) {
        ImageInsertStatus.Idle -> Unit

        ImageInsertStatus.Loading -> {
            TextBodySmall(
                text = stringResource(R.string.signature_editor_image_uploading),
                color = BoltTheme.colors.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = BoltTheme.spacings.double)
                    .testTag("signature_editor_image_status"),
            )
        }

        is ImageInsertStatus.Error -> {
            TextBodySmall(
                text = status.message,
                color = BoltTheme.colors.error,
                modifier = Modifier
                    .padding(horizontal = BoltTheme.spacings.double)
                    .testTag("signature_editor_image_status"),
            )
        }
    }
}

@Composable
private fun SignatureEditorDialogHost(
    showLinkDialog: Boolean,
    linkUrl: String,
    onLinkUrlChange: (String) -> Unit,
    onLinkConfirm: () -> Unit,
    onLinkDismiss: () -> Unit,
    showFontSizeDialog: Boolean,
    onFontSizePick: (String) -> Unit,
    onFontSizeDismiss: () -> Unit,
) {
    if (showLinkDialog) {
        SignatureLinkDialog(
            linkUrl = linkUrl,
            onLinkUrlChange = onLinkUrlChange,
            onConfirm = onLinkConfirm,
            onDismiss = onLinkDismiss,
        )
    }
    if (showFontSizeDialog) {
        SignatureFontSizeDialog(onPick = onFontSizePick, onDismiss = onFontSizeDismiss)
    }
}

private fun insertLinkIntoEditor(webView: SignatureEditorWebView?, linkUrl: String) {
    val safeUrl = linkUrl.trim()
    if (
        !safeUrl.startsWith("https://") &&
        !safeUrl.startsWith("http://") &&
        !safeUrl.startsWith("mailto:")
    ) {
        return
    }
    webView?.finishActiveActionMode()
    webView?.requestFocus()
    webView?.evaluateJavascript(
        "window.SignatureEditor.insertLink(${safeUrl.toJsString()});",
        null,
    )
}

/**
 * WYSIWYG-first image insert: the picked image lands in the editor right away
 * as a local data URI, then the hosted upload runs in the background and swaps
 * the src to the public URL when it succeeds. If the upload fails the local
 * copy simply stays — the user already saw their image appear.
 */
private fun insertPickedImage(
    context: android.content.Context,
    uri: android.net.Uri,
    imageHost: SignatureImageHostClient,
    webViewProvider: () -> SignatureEditorWebView?,
    currentHtmlProvider: () -> String,
    onHtmlChange: (String) -> Unit,
    onStatus: (ImageInsertStatus) -> Unit,
) {
    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
        val webp = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("could not read image")
                SignatureInlineImages.encodeToWebp(bytes, context.contentResolver.getType(uri))
            }.getOrNull()
        }
        val editor = webViewProvider()
        if (webp == null || editor == null) {
            onStatus(
                ImageInsertStatus.Error(
                    context.getString(R.string.signature_editor_image_insert_failed),
                ),
            )
            return@launch
        }

        val sigId = UUID.randomUUID().toString()
        val dataUri = SignatureInlineImages.toDataUri(webp)
        editor.finishActiveActionMode()
        editor.evaluateJavascript(
            "window.SignatureEditor.insertImage(${dataUri.toJsString()}, ${sigId.toJsString()});",
            null,
        )

        onStatus(ImageInsertStatus.Loading)
        val publicUrl = withContext(Dispatchers.IO) {
            runCatching { imageHost.uploadWebp(webp) }.getOrNull()
        }

        // Hosted swap is an optimization — the data URI stays if the upload failed.
        val currentEditor = webViewProvider()
        if (currentEditor != null) {
            val call = if (publicUrl != null) {
                "window.SignatureEditor.swapImageSrc(${sigId.toJsString()}, ${publicUrl.toJsString()});"
            } else {
                "window.SignatureEditor.commitPendingImage(${sigId.toJsString()});"
            }
            currentEditor.evaluateJavascript(call, null)
        } else {
            val resolvedSrc = publicUrl ?: dataUri
            onHtmlChange(resolvePendingSignatureImageHtml(currentHtmlProvider(), sigId, resolvedSrc))
        }
        onStatus(ImageInsertStatus.Idle)
    }
}

class SignatureHtmlEditorController {
    private var webView: WebView? = null

    fun captureHtml(currentHtml: String, onHtmlCaptured: (String) -> Unit) {
        val currentWebView = webView
        if (currentWebView == null) {
            onHtmlCaptured(currentHtml)
            return
        }

        currentWebView.evaluateJavascript(
            "(window.SignatureEditor && window.SignatureEditor.getHtml()) || null",
        ) { serializedHtml ->
            val capturedHtml = serializedHtml.decodeEvaluateJavascriptString() ?: currentHtml
            val htmlToPersist = if (
                containsPendingSignatureUpload(capturedHtml) &&
                !containsPendingSignatureUpload(currentHtml)
            ) {
                currentHtml
            } else {
                capturedHtml
            }
            onHtmlCaptured(htmlToPersist)
        }
    }

    internal fun attachWebView(webView: WebView?) {
        this.webView = webView
    }
}

@Composable
fun rememberSignatureHtmlEditorController(): SignatureHtmlEditorController {
    return remember { SignatureHtmlEditorController() }
}

internal fun String?.decodeEvaluateJavascriptString(): String? {
    if (this == null || this == "null") return null

    return runCatching {
        org.json.JSONTokener(this).nextValue() as? String
    }.getOrNull()
}

internal fun resolvePendingSignatureImageHtml(html: String, sigId: String, resolvedSrc: String): String {
    val match = PENDING_SIGNATURE_IMAGE_REGEX.findAll(html)
        .firstOrNull { it.groupValues[1] == sigId }
        ?: return html

    val originalTag = match.value
    val tagWithoutMarker = PENDING_SIGNATURE_ID_ATTRIBUTE_REGEX.replace(originalTag, "")
    val escapedSrc = resolvedSrc.escapeHtmlAttribute()
    val updatedTag = if (IMG_SRC_ATTRIBUTE_REGEX.containsMatchIn(tagWithoutMarker)) {
        IMG_SRC_ATTRIBUTE_REGEX.replace(tagWithoutMarker, """ src="$escapedSrc"""")
    } else {
        tagWithoutMarker.replace(">", """ src="$escapedSrc">""")
    }

    return html.substring(0, match.range.first) + updatedTag + html.substring(match.range.last + 1)
}

private fun containsPendingSignatureUpload(html: String): Boolean {
    return "data-sig-id" in html
}

private fun String.escapeHtmlAttribute(): String {
    return buildString {
        for (char in this@escapeHtmlAttribute) {
            when (char) {
                '&' -> append("&amp;")
                '"' -> append("&quot;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(char)
            }
        }
    }
}

private val PENDING_SIGNATURE_IMAGE_REGEX = Regex(
    pattern = """<img\b[^>]*\bdata-sig-id\s*=\s*"([^"]+)"[^>]*>""",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

private val PENDING_SIGNATURE_ID_ATTRIBUTE_REGEX = Regex(
    pattern = "\\sdata-sig-id\\s*=\\s*\"[^\"]*\"",
    options = setOf(RegexOption.IGNORE_CASE),
)

private val IMG_SRC_ATTRIBUTE_REGEX = Regex(
    pattern = "\\ssrc\\s*=\\s*\"[^\"]*\"",
    options = setOf(RegexOption.IGNORE_CASE),
)

@SuppressLint("SetJavaScriptEnabled")
private fun createSignatureEditorWebView(
    context: android.content.Context,
    initialHtml: String,
    onHtmlChange: (String) -> Unit,
    onFormatStateChange: (Set<String>) -> Unit,
): SignatureEditorWebView {
    return SignatureEditorWebViewFactory.create(
        context = context,
        initialHtml = initialHtml,
        onHtmlChange = onHtmlChange,
        onFormatStateChange = onFormatStateChange,
    ).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        isFocusable = true
        isFocusableInTouchMode = true
    }
}

private sealed interface ImageInsertStatus {
    data object Idle : ImageInsertStatus
    data object Loading : ImageInsertStatus
    data class Error(val message: String) : ImageInsertStatus
}

private const val EDITOR_HEIGHT_DP = 240

@Suppress("MagicNumber")
private val EDITOR_CANVAS_COLOR = ComposeColor(0xFFFFFFFF)

@Suppress("MagicNumber")
private val EDITOR_CANVAS_TEXT_COLOR = ComposeColor(0xFF1A1A1A)
