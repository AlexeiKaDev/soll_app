package com.soll.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

@Composable
fun RemoteLinkPreviewImage(
    url: String,
    modifier: Modifier = Modifier.size(64.dp),
) {
    val cached = remember(url) { LinkPreviewImageCache.get(url) }
    val image by produceState<ImageBitmap?>(initialValue = cached?.image, key1 = url) {
        if (cached != null) {
            return@produceState
        }
        value = LinkPreviewImageCache.getOrLoad(url)
    }
    val bitmap = image
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Icon(
            imageVector = Icons.Default.Link,
            contentDescription = null,
            modifier = Modifier.size(34.dp),
        )
    }
}

private data class LinkPreviewImageCacheEntry(val image: ImageBitmap?)

private object LinkPreviewImageCache {
    private const val MAX_ENTRIES = 48
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = mutableMapOf<String, Deferred<ImageBitmap?>>()
    private val entries = object : LinkedHashMap<String, ImageBitmap?>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>?): Boolean =
            size > MAX_ENTRIES
    }

    fun get(url: String): LinkPreviewImageCacheEntry? = synchronized(lock) {
        if (entries.containsKey(url)) LinkPreviewImageCacheEntry(entries[url]) else null
    }

    suspend fun getOrLoad(url: String): ImageBitmap? {
        get(url)?.let { cached -> return cached.image }
        var cachedHit = false
        var cachedImage: ImageBitmap? = null
        val deferred = synchronized(lock) {
            if (entries.containsKey(url)) {
                cachedHit = true
                cachedImage = entries[url]
                null
            } else {
                inFlight[url] ?: scope.async { loadPreviewImage(url) }.also { inFlight[url] = it }
            }
        }
        if (cachedHit) return cachedImage
        val loader = deferred ?: return null
        val image = loader.await()
        synchronized(lock) {
            if (inFlight[url] === loader) {
                inFlight.remove(url)
                entries[url] = image
            }
        }
        return image
    }
}

private fun loadPreviewImage(url: String): ImageBitmap? =
    runCatching {
        if (!isSafePreviewImageUrl(url)) return@runCatching null
        val connection = (URL(url).openConnection() as? HttpURLConnection)?.apply {
            instanceFollowRedirects = false
            connectTimeout = 2_500
            readTimeout = 2_500
        } ?: return@runCatching null
        try {
            val responseCode = connection.responseCode
            if (responseCode in 300..399 || responseCode !in 200..299) {
                return@runCatching null
            }
            if (!isPreviewImageContentType(connection.contentType)) {
                return@runCatching null
            }
            connection.inputStream.use { input ->
                val bytes = input.readBoundedPreviewBytes() ?: return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    return@runCatching null
                }
                val decoded = BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size,
                    BitmapFactory.Options().apply {
                        inSampleSize = previewImageSampleSize(bounds.outWidth, bounds.outHeight)
                    },
                ) ?: return@runCatching null
                decoded.asImageBitmap()
            }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

private fun isSafePreviewImageUrl(url: String): Boolean =
    runCatching {
        val parsed = URL(url.trim())
        parsed.protocol.lowercase() in PREVIEW_IMAGE_URL_SCHEMES &&
            parsed.host.isNotBlank() &&
            parsed.userInfo.isNullOrBlank() &&
            isPublicPreviewImageHost(parsed.host)
    }.getOrDefault(false)

private fun isPublicPreviewImageHost(host: String): Boolean {
    val normalized = host.trim().trim('[', ']').lowercase()
    if (normalized.isBlank()) return false
    if (normalized == "localhost" || normalized.endsWith(".localhost")) return false
    val mappedIpv4 = when {
        normalized.startsWith("::ffff:") -> normalized.removePrefix("::ffff:")
        normalized.startsWith("0:0:0:0:0:ffff:") -> normalized.removePrefix("0:0:0:0:0:ffff:")
        else -> null
    }
    if (mappedIpv4 != null) return isPublicPreviewImageHost(mappedIpv4)
    if (normalized.contains(':')) {
        if (normalized == "::" || normalized == "0:0:0:0:0:0:0:0") return false
        if (normalized == "::1" || normalized == "0:0:0:0:0:0:0:1") return false
        if (normalized.startsWith("fe80:") || normalized.startsWith("fc") || normalized.startsWith("fd")) return false
    }

    val ipv4 = normalized.split('.').mapNotNull { part ->
        part.toIntOrNull()?.takeIf { it in 0..255 }
    }
    if (ipv4.size == 4 && normalized.count { it == '.' } == 3) {
        val first = ipv4[0]
        val second = ipv4[1]
        return when {
            first == 0 -> false
            first == 10 -> false
            first == 127 -> false
            first == 169 && second == 254 -> false
            first == 172 && second in 16..31 -> false
            first == 192 && second == 168 -> false
            else -> true
        }
    }

    return true
}

private fun isPreviewImageContentType(contentType: String?): Boolean {
    val type = contentType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        .orEmpty()
    return type in PREVIEW_IMAGE_CONTENT_TYPES
}

private fun InputStream.readBoundedPreviewBytes(): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    var read = read(buffer)
    while (read >= 0) {
        total += read
        if (total > MAX_PREVIEW_IMAGE_BYTES) {
            return null
        }
        output.write(buffer, 0, read)
        read = read(buffer)
    }
    return output.toByteArray()
}

private fun previewImageSampleSize(width: Int, height: Int): Int {
    var sampleSize = 1
    while (
        width / sampleSize > PREVIEW_IMAGE_TARGET_PX ||
        height / sampleSize > PREVIEW_IMAGE_TARGET_PX
    ) {
        sampleSize *= 2
    }
    return sampleSize
}

private const val MAX_PREVIEW_IMAGE_BYTES = 2 * 1024 * 1024
private const val PREVIEW_IMAGE_TARGET_PX = 192
private val PREVIEW_IMAGE_URL_SCHEMES = setOf("http", "https")
private val PREVIEW_IMAGE_CONTENT_TYPES = setOf(
    "image/jpeg",
    "image/png",
    "image/webp",
    "image/gif",
)
