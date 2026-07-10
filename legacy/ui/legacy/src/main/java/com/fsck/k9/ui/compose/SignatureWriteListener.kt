package com.fsck.k9.ui.compose

fun interface SignatureWriteListener {
    fun onSignatureWrite(signature: String, changed: Boolean)
}
