package com.khojoai.app.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Version 0.1.1 stored picked photos as a plain list. This moves that list into the database once, then deletes it. */
@Singleton
class LegacyImports @Inject constructor(@ApplicationContext context: Context, private val assets: AssetRepository) {
    private val prefs = context.getSharedPreferences("khojo_imports", Context.MODE_PRIVATE)

    fun migrate(resolver: ContentResolver) {
        val uris = prefs.getStringSet("imported_uris", null) ?: return
        assets.addAll(uris.map { toNewAsset(resolver, it) })
        prefs.edit().clear().apply()
    }
}

fun toNewAsset(resolver: ContentResolver, uri: String): NewAsset {
    val meta = Fingerprint.read(resolver, Uri.parse(uri))
    return NewAsset(uri, Fingerprint.of(uri, meta), meta.mimeType ?: "image/jpeg", meta.takenAtMs)
}
