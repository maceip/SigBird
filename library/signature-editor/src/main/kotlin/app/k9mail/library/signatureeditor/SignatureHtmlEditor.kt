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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    controller: SignatureHtmlEditorController? = null,
    label: String = stringResource(R.string.signature_editor_label),
    bringIntoViewRequester: BringIntoViewRequester? = null,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var webView by remember { mutableStateOf<SignatureEditorWebView?>(null) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showFontFamilyDialog by remember { mutableStateOf(false) }
    var linkUrl by remember { mutableStateOf("https://") }
    var imageInsertStatus by remember { mutableStateOf<ImageInsertStatus>(ImageInsertStatus.Idle) }
    val localBringIntoView = remember { BringIntoViewRequester() }
    val effectiveBringIntoView = bringIntoViewRequester ?: localBringIntoView
    val imageHost = remember {
        SignatureImageHostClient(
            baseUrl = BuildConfig.SIGNATURE_GATEWAY_BASE,
            challengePrefix = BuildConfig.SIGNATURE_CHALLENGE_PREFIX,
        )
    }

    // GetContent is more reliable than OpenDocument for one-shot reads across OEMs.
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) {
            imageInsertStatus = ImageInsertStatus.Idle
            return@rememberLauncherForActivityResult
        }
        insertPickedImage(
            scope = scope,
            context = context,
            uri = uri,
            imageHost = imageHost,
            webViewProvider = { webView },
            bringIntoViewRequester = effectiveBringIntoView,
            onStatus = { imageInsertStatus = it },
        )
    }

    fun runCommand(command: String, value: String? = null) {
        val editor = webView ?: return
        editor.finishActiveActionMode()
        editor.requestFocus()
        val js = if (value == null) {
            "window.SignatureEditor.command(${command.toJsString()});"
        } else {
            "window.SignatureEditor.command(${command.toJsString()}, ${value.toJsString()});"
        }
        editor.evaluateJavascript(js, null)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(effectiveBringIntoView)
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
                            ).also { created ->
                                created.contentDescription = "signature_html_editor_webview"
                                created.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                created.setOnFocusChangeListener { _, hasFocus ->
                                    if (hasFocus) {
                                        scope.launch {
                                            effectiveBringIntoView.bringIntoView()
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

                ImageInsertStatusBanner(status = imageInsertStatus)

                DividerHorizontal(color = BoltTheme.colors.outlineVariant)

                SignatureFormattingToolbar(
                    onCommand = { command, value -> runCommand(command, value) },
                    onInsertLink = {
                        webView?.finishActiveActionMode()
                        showLinkDialog = true
                    },
                    onTextColor = {
                        webView?.finishActiveActionMode()
                        showColorDialog = true
                    },
                    onFontSize = {
                        webView?.finishActiveActionMode()
                        showFontSizeDialog = true
                    },
                    onFontFamily = {
                        webView?.finishActiveActionMode()
                        showFontFamilyDialog = true
                    },
                    onInsertImage = {
                        webView?.finishActiveActionMode()
                        // Dismiss IME so the picker result and status stay visible.
                        focusManager.clearFocus(force = true)
                        scope.launch { effectiveBringIntoView.bringIntoView() }
                        imagePicker.launch("image/*")
                    },
                )
            }
        }
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
        showColorDialog = showColorDialog,
        onColorPick = { hex ->
            runCommand("foreColor", hex)
            showColorDialog = false
        },
        onColorDismiss = { showColorDialog = false },
        showFontSizeDialog = showFontSizeDialog,
        onFontSizePick = { size ->
            runCommand("fontSize", size)
            showFontSizeDialog = false
        },
        onFontSizeDismiss = { showFontSizeDialog = false },
        showFontFamilyDialog = showFontFamilyDialog,
        onFontFamilyPick = { family ->
            runCommand("fontName", family)
            showFontFamilyDialog = false
        },
        onFontFamilyDismiss = { showFontFamilyDialog = false },
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
                    .fillMaxWidth()
                    .padding(
                        horizontal = BoltTheme.spacings.default,
                        vertical = BoltTheme.spacings.half,
                    )
                    .testTag("signature_editor_image_status"),
            )
        }

        ImageInsertStatus.Success -> {
            TextBodySmall(
                text = stringResource(R.string.signature_editor_image_inserted),
                color = BoltTheme.colors.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = BoltTheme.spacings.default,
                        vertical = BoltTheme.spacings.half,
                    )
                    .testTag("signature_editor_image_status"),
            )
        }

        is ImageInsertStatus.Error -> {
            TextBodySmall(
                text = status.message,
                color = BoltTheme.colors.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = BoltTheme.spacings.default,
                        vertical = BoltTheme.spacings.half,
                    )
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
    showColorDialog: Boolean,
    onColorPick: (String) -> Unit,
    onColorDismiss: () -> Unit,
    showFontSizeDialog: Boolean,
    onFontSizePick: (String) -> Unit,
    onFontSizeDismiss: () -> Unit,
    showFontFamilyDialog: Boolean,
    onFontFamilyPick: (String) -> Unit,
    onFontFamilyDismiss: () -> Unit,
) {
    if (showLinkDialog) {
        SignatureLinkDialog(
            linkUrl = linkUrl,
            onLinkUrlChange = onLinkUrlChange,
            onConfirm = onLinkConfirm,
            onDismiss = onLinkDismiss,
        )
    }
    if (showColorDialog) {
        SignatureColorDialog(onPick = onColorPick, onDismiss = onColorDismiss)
    }
    if (showFontSizeDialog) {
        SignatureFontSizeDialog(onPick = onFontSizePick, onDismiss = onFontSizeDismiss)
    }
    if (showFontFamilyDialog) {
        SignatureFontFamilyDialog(onPick = onFontFamilyPick, onDismiss = onFontFamilyDismiss)
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

private fun insertPickedImage(
    scope: CoroutineScope,
    context: android.content.Context,
    uri: android.net.Uri,
    imageHost: SignatureImageHostClient,
    webViewProvider: () -> SignatureEditorWebView?,
    bringIntoViewRequester: BringIntoViewRequester,
    onStatus: (ImageInsertStatus) -> Unit,
) {
    onStatus(ImageInsertStatus.Loading)
    scope.launch {
        bringIntoViewRequester.bringIntoView()
        val hostedResult = withContext(Dispatchers.IO) {
            runCatching { uri.toHostedSignatureImageUrl(context, imageHost) }
        }
        val publicUrl = hostedResult.getOrNull()
        if (publicUrl == null) {
            val detail = hostedResult.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
            onStatus(
                ImageInsertStatus.Error(
                    if (detail == null) {
                        context.getString(R.string.signature_editor_image_insert_failed)
                    } else {
                        context.getString(R.string.signature_editor_image_insert_failed_detail, detail)
                    },
                ),
            )
            bringIntoViewRequester.bringIntoView()
            return@launch
        }
        // Wait briefly for the editor AndroidView to re-attach after the picker.
        var editor = webViewProvider()
        if (editor == null) {
            delay(WEBVIEW_REATTACH_WAIT_MS)
            editor = webViewProvider()
        }
        if (editor == null) {
            onStatus(
                ImageInsertStatus.Error(
                    context.getString(R.string.signature_editor_image_insert_failed),
                ),
            )
            bringIntoViewRequester.bringIntoView()
            return@launch
        }
        editor.finishActiveActionMode()
        editor.requestFocus()
        editor.evaluateJavascript(
            "window.SignatureEditor.insertImage(${publicUrl.toJsString()});",
            null,
        )
        onStatus(ImageInsertStatus.Success)
        bringIntoViewRequester.bringIntoView()
        delay(IMAGE_STATUS_SUCCESS_MS)
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
            onHtmlCaptured(serializedHtml.decodeEvaluateJavascriptString() ?: currentHtml)
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

@SuppressLint("SetJavaScriptEnabled")
private fun createSignatureEditorWebView(
    context: android.content.Context,
    initialHtml: String,
    onHtmlChange: (String) -> Unit,
): SignatureEditorWebView {
    return SignatureEditorWebViewFactory.create(
        context = context,
        initialHtml = initialHtml,
        onHtmlChange = onHtmlChange,
    ).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        isFocusable = true
        isFocusableInTouchMode = true
    }
}

/**
 * Encodes the picked image to WebP ≤ 256 KiB and uploads it to the signature
 * image gateway. Falls back to a data URI if the upload fails (offline / DevX).
 */
private fun android.net.Uri.toHostedSignatureImageUrl(
    context: android.content.Context,
    hostClient: SignatureImageHostClient,
): String {
    val bytes = context.contentResolver.openInputStream(this)?.use { it.readBytes() }
        ?: error("could not read image")
    val mime = context.contentResolver.getType(this)
    val webp = SignatureInlineImages.encodeToWebp(bytes, mime)
        ?: error("could not encode image as WebP under 256 KiB")
    return runCatching { hostClient.uploadWebp(webp) }.getOrElse {
        SignatureInlineImages.encodeBytes(bytes, mime)
            ?: throw it
    }
}

private sealed interface ImageInsertStatus {
    data object Idle : ImageInsertStatus
    data object Loading : ImageInsertStatus
    data object Success : ImageInsertStatus
    data class Error(val message: String) : ImageInsertStatus
}

private const val EDITOR_HEIGHT_DP = 180
private const val WEBVIEW_REATTACH_WAIT_MS = 250L
private const val IMAGE_STATUS_SUCCESS_MS = 2_500L

@Suppress("MagicNumber")
private val EDITOR_CANVAS_COLOR = ComposeColor(0xFFFFFFFF)

@Suppress("MagicNumber")
private val EDITOR_CANVAS_TEXT_COLOR = ComposeColor(0xFF1A1A1A)
