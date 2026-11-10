package com.khojoai.app.data

import com.khojoai.app.data.db.ImageAsset
import com.khojoai.app.data.db.ImageAsset_
import com.khojoai.app.data.db.IndexStatus
import com.khojoai.app.data.db.Model
import io.objectbox.BoxStore
import javax.inject.Inject
import javax.inject.Singleton

/** What one import changed. */
data class ImportCounts(val selected: Int, val added: Int, val alreadyImported: Int)

/** A picked photo, ready to be stored. */
data class NewAsset(val uri: String, val stableKey: String, val mimeType: String, val takenAtMs: Long?)

/** What to store for one import and what to tell the person. Pure, so it can be unit tested without a database. */
data class ImportPlan(val toAdd: List<NewAsset>, val counts: ImportCounts)

fun planImport(existingKeys: Set<String>, picked: List<NewAsset>): ImportPlan {
    val seen = existingKeys.toMutableSet()
    val fresh = picked.filter { seen.add(it.stableKey) }
    return ImportPlan(fresh, ImportCounts(picked.size, fresh.size, picked.size - fresh.size))
}

/** A photo needs (re)indexing unless the same file was already indexed with the same model and processing version. */
fun needsIndexing(existing: ImageAsset?, modelVersion: String, processingVersion: Int): Boolean =
    existing == null ||
        existing.indexStatus != IndexStatus.INDEXED ||
        existing.modelVersion != modelVersion ||
        existing.processingVersion != processingVersion

@Singleton
class AssetRepository @Inject constructor(boxStore: BoxStore) {
    private val box = boxStore.boxFor(ImageAsset::class.java)

    fun total(): Int = box.count().toInt()

    fun addAll(picked: List<NewAsset>, nowMs: Long = System.currentTimeMillis()): ImportCounts {
        val existingKeys = box.query().build().use { q -> q.property(ImageAsset_.stableKey).findStrings().orEmpty().toSet() }
        val plan = planImport(existingKeys, picked)
        box.put(
            plan.toAdd.map {
                ImageAsset(
                    stableKey = it.stableKey,
                    uri = it.uri,
                    mimeType = it.mimeType,
                    addedAtMs = nowMs,
                    takenAtMs = it.takenAtMs,
                    modelVersion = Model.VERSION,
                )
            },
        )
        return plan.counts
    }
}
