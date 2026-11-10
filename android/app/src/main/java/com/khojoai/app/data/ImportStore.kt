package com.khojoai.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** What one import changed. */
data class ImportCounts(val selected: Int, val added: Int, val alreadyImported: Int)

/** Pure merge step, kept apart so it can be unit tested. */
fun mergeImports(existing: Set<String>, picked: List<String>): Pair<Set<String>, ImportCounts> {
    val fresh = picked.distinct().filterNot { it in existing }
    return (existing + fresh) to ImportCounts(
        selected = picked.size,
        added = fresh.size,
        alreadyImported = picked.size - fresh.size,
    )
}

/** Remembers which photos were imported. A plain list for now; Sprint 3C replaces it with ObjectBox `ImageAsset`. */
@Singleton
class ImportStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("khojo_imports", Context.MODE_PRIVATE)

    fun all(): Set<String> = prefs.getStringSet(KEY_URIS, emptySet()).orEmpty().toSet()

    fun total(): Int = all().size

    fun add(picked: List<String>): ImportCounts {
        val (merged, counts) = mergeImports(all(), picked)
        prefs.edit().putStringSet(KEY_URIS, merged).apply()
        return counts
    }

    private companion object {
        const val KEY_URIS = "imported_uris"
    }
}
