package com.khojoai.app.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.security.MessageDigest

/** What we can learn about a picked photo without reading its bytes. */
data class PhotoMeta(val name: String?, val sizeBytes: Long?, val mimeType: String?, val takenAtMs: Long?)

object Fingerprint {
    /**
     * A quick fingerprint from file name, size, type and date. Two copies of the same file give the same key.
     * Without a name and size there is nothing stable to hash, so the link itself is used.
     */
    fun of(uri: String, meta: PhotoMeta): String {
        if (meta.name == null && meta.sizeBytes == null) return "uri:$uri"
        val text = listOf(meta.name, meta.sizeBytes, meta.mimeType, meta.takenAtMs).joinToString("|") { it?.toString().orEmpty() }
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /** Reads the metadata the system already holds. Any failure just means less information. */
    fun read(resolver: ContentResolver, uri: Uri): PhotoMeta {
        var name: String? = null
        var size: Long? = null
        var taken: Long? = null
        try {
            resolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
                    c.getColumnIndex("datetaken").takeIf { it >= 0 && !c.isNull(it) }?.let { taken = c.getLong(it) }
                }
            }
        } catch (_: Exception) {
            // Keep what we have.
        }
        val mime = try { resolver.getType(uri) } catch (_: Exception) { null }
        return PhotoMeta(name, size, mime, taken)
    }
}
