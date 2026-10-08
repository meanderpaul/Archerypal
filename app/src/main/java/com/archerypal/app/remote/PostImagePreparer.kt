package com.archerypal.app.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import kotlin.math.max

/**
 * Converts a gallery [Uri] into a JPEG under the server's post-image limits
 * (JPEG/PNG only, max 5 MB). Handles HEIC/WebP via the platform decoder.
 */
object PostImagePreparer {
    private const val MAX_EDGE_PX = 1920
    private const val MAX_BYTES = 4 * 1024 * 1024

    fun prepare(context: Context, uri: Uri): File? {
        val bitmap = decodeScaled(context, uri) ?: return null
        return try {
            val out = File.createTempFile("post_", ".jpg", context.cacheDir)
            var quality = 85
            while (quality >= 45) {
                out.outputStream().use { stream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) {
                        out.delete()
                        return null
                    }
                }
                if (out.length() <= MAX_BYTES) return out
                quality -= 10
            }
            if (out.length() > MAX_BYTES) {
                out.delete()
                null
            } else {
                out
            }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        val decoded = if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val longest = max(w, h).coerceAtLeast(1)
                if (longest > MAX_EDGE_PX) {
                    val scale = MAX_EDGE_PX.toFloat() / longest
                    decoder.setTargetSize(
                        (w * scale).toInt().coerceAtLeast(1),
                        (h * scale).toInt().coerceAtLeast(1)
                    )
                }
                decoder.isMutableRequired = false
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: return null
            val longest = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            var sample = 1
            while (longest / sample > MAX_EDGE_PX) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } ?: return null

        // compress() rejects HARDWARE bitmaps
        if (decoded.config == Bitmap.Config.HARDWARE) {
            val soft = decoded.copy(Bitmap.Config.ARGB_8888, false)
            decoded.recycle()
            return soft
        }
        return decoded
    }
}