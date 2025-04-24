package com.minka.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.core.graphics.createBitmap

fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable) return this.bitmap
    val bmp = createBitmap(intrinsicWidth, intrinsicHeight)
    val canvas = Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp
}

fun Modifier.clearContentAfter(): Modifier = this.then(
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }
)
