package com.cesenahome.ui.common

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

fun applySystemBarsInsets(
    root: View,
    onInsetsApplied: ((WindowInsetsCompat) -> Unit)? = null,
) {
    val initialTop = root.paddingTop
    val initialBottom = root.paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(
            top = initialTop + systemBars.top,
            bottom = initialBottom + systemBars.bottom,
        )
        onInsetsApplied?.invoke(insets)
        insets
    }
    ViewCompat.requestApplyInsets(root)
}