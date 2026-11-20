package com.bolt.pdf

import android.graphics.Bitmap

object NativeZeroCopy {
    init {
        System.loadLibrary("pdfium")
        System.loadLibrary("pdf_core")
    }

    external fun renderPageDirect(
        enginePtr: Long,
        pageIndex: Int,
        bitmap: Bitmap,
        manualRotationOrdinal: Int,
        darkMode: Boolean
    ): Boolean

    external fun renderZoomTileDirect(
        enginePtr: Long,
        pageIndex: Int,
        fullScaledWidthPx: Int,
        fullScaledHeightPx: Int,
        visibleXPx: Int,
        visibleYPx: Int,
        bitmap: Bitmap,
        manualRotationOrdinal: Int,
        darkMode: Boolean
    ): Boolean
}

