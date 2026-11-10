package com.khojoai.app.data.db

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.VectorDistanceType

/**
 * The model that makes the vectors. Chosen model decides the vector size (ADR-003: Base = 768, So400m = 1,152).
 * Working default is Base until the model decision is made. Changing it means a new vector property and a full reindex,
 * never padding or truncating.
 */
object Model {
    const val VERSION = "siglip2-base-p16-384-v1"
    const val DIMENSIONS = 768L
}

object IndexStatus {
    const val PENDING = "PENDING"
    const val INDEXED = "INDEXED"
    const val FAILED = "FAILED"
    const val STALE = "STALE"
}

/** One photo the person added. The file itself stays where it is; we keep only a link and the search data. */
@Entity
class ImageAsset(
    @Id var id: Long = 0,
    /** Fingerprint of the file (see [Fingerprint]). Same key means same photo. */
    @Index var stableKey: String = "",
    /** The content link the person authorized through the Photo Picker. */
    var uri: String = "",
    var mimeType: String = "image/jpeg",
    var addedAtMs: Long = 0,
    var takenAtMs: Long? = null,
    var ocrText: String? = null,
    var modelVersion: String = Model.VERSION,
    var processingVersion: Int = 1,
    var indexStatus: String = IndexStatus.PENDING,
    var errorCode: String? = null,
    @HnswIndex(dimensions = 768, distanceType = VectorDistanceType.COSINE)
    var imageVector: FloatArray? = null,
)

/** Work still to do for a photo (embedding, OCR). Used from Sprint 4. */
@Entity
class IndexJob(
    @Id var id: Long = 0,
    @Index var assetId: Long = 0,
    var pipelineType: String = "IMAGE_EMBEDDING",
    var attempts: Int = 0,
    var state: String = "PENDING",
    var updatedAtMs: Long = 0,
    var errorCode: String? = null,
)

/** The person's choices. A single row. */
@Entity
class UserSettings(
    @Id var id: Long = 0,
    /** Cloud AI is off unless the person turns it on. */
    var cloudConsent: Boolean = false,
    /** True once the consent screen has been answered, either way. */
    var consentAnswered: Boolean = false,
    /** OCR runs on the phone, nothing leaves it. */
    var ocrEnabled: Boolean = true,
    var language: String = "auto",
    /** How long a processing copy may stay in the cloud, in hours (blueprint: at most 24). */
    var retentionHours: Int = 24,
    var wifiOnly: Boolean = true,
)

/** Remembers the vector of a recent search so the same query is not computed twice. */
@Entity
class QueryCache(
    @Id var id: Long = 0,
    @Index var normalizedQuery: String = "",
    var modelVersion: String = Model.VERSION,
    var vector: FloatArray? = null,
    var createdAtMs: Long = 0,
)
