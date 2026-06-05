package com.lastasylum.alliance.ui.util

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun rememberClipboardHasTextState(): Boolean {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val hasText = remember(revision) {
        readClipboardPlainText(context) != null
    }
    fun refresh() {
        revision++
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipListener = ClipboardManager.OnPrimaryClipChangedListener { refresh() }
        clipboard?.addPrimaryClipChangedListener(clipListener)
        refresh()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            clipboard?.removePrimaryClipChangedListener(clipListener)
        }
    }
    return hasText
}

/** @see rememberClipboardHasTextState */
@Composable
fun rememberComposerClipboardHasText(): Boolean = rememberClipboardHasTextState()
