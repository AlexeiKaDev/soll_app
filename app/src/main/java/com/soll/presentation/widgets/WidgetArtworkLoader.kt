package com.soll.presentation.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.RemoteViews
import java.io.File
import kotlin.math.max

object WidgetArtworkLoader {
    private const val WIDGET_ARTWORK_SIZE_PX = 192

    fun setAudioArtwork(
        context: Context,
        views: RemoteViews,
        viewId: Int,
        trackUri: String?,
        fallbackResId: Int,
    ) {
        val artwork = decodeAudioArtwork(context, trackUri)
        if (artwork != null) {
            views.setImageViewBitmap(viewId, artwork)
        } else {
            views.setImageViewResource(viewId, fallbackResId)
        }
    }

    fun setFileArtwork(
        views: RemoteViews,
        viewId: Int,
        filePath: String?,
        fallbackResId: Int,
    ) {
        val artwork = decodeFileArtwork(filePath)
        if (artwork != null) {
            views.setImageViewBitmap(viewId, artwork)
        } else {
            views.setImageViewResource(viewId, fallbackResId)
        }
    }

    fun decodeFileArtwork(filePath: String?): Bitmap? {
        val path = filePath?.takeIf { it.isNotBlank() } ?: return null
        val file = File(path)
        if (!file.isFile || !file.canRead()) return null

        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, WIDGET_ARTWORK_SIZE_PX)
            }
            BitmapFactory.decodeFile(path, options)?.scaleDown(WIDGET_ARTWORK_SIZE_PX)
        }.getOrNull()
    }

    private fun decodeAudioArtwork(context: Context, trackUri: String?): Bitmap? {
        val uri = trackUri?.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture
                ?.takeIf { it.isNotEmpty() }
                ?.let { decodeByteArrayArtwork(it) }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeByteArrayArtwork(data: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, WIDGET_ARTWORK_SIZE_PX)
        }
        return BitmapFactory.decodeByteArray(data, 0, data.size, options)
            ?.scaleDown(WIDGET_ARTWORK_SIZE_PX)
    }

    private fun sampleSize(width: Int, height: Int, maxSize: Int): Int {
        var sample = 1
        while (width / sample > maxSize * 2 || height / sample > maxSize * 2) {
            sample *= 2
        }
        return sample
    }

    private fun Bitmap.scaleDown(maxSize: Int): Bitmap {
        val longest = max(width, height)
        if (longest <= maxSize) return this
        val scale = maxSize.toFloat() / longest.toFloat()
        return Bitmap.createScaledBitmap(
            this,
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }
}
