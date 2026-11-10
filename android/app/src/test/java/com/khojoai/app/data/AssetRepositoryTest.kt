package com.khojoai.app.data

import com.khojoai.app.data.db.ImageAsset
import com.khojoai.app.data.db.IndexStatus
import com.khojoai.app.data.db.Model
import com.khojoai.app.data.db.MyObjectBox
import com.khojoai.app.data.db.UserSettings
import io.objectbox.BoxStore
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AssetRepositoryTest {
    private lateinit var dir: File
    private lateinit var store: BoxStore

    @Before
    fun open() {
        dir = Files.createTempDirectory("khojo-db").toFile()
        store = MyObjectBox.builder().directory(dir).build()
    }

    @After
    fun close() {
        store.close()
        BoxStore.deleteAllFiles(dir)
    }

    private fun photo(key: String) = NewAsset("content://p/$key", key, "image/jpeg", null)

    @Test
    fun newPhotosAreStored() {
        val counts = AssetRepository(store).addAll(listOf(photo("a"), photo("b")), nowMs = 1)
        assertEquals(ImportCounts(selected = 2, added = 2, alreadyImported = 0), counts)
    }

    @Test
    fun sameFingerprintIsNotStoredTwice() {
        val repo = AssetRepository(store)
        repo.addAll(listOf(photo("a")))
        val counts = repo.addAll(listOf(photo("a"), photo("b")))
        assertEquals(ImportCounts(selected = 2, added = 1, alreadyImported = 1), counts)
        assertEquals(2, repo.total())
    }

    @Test
    fun photosSurviveReopeningTheDatabase() {
        AssetRepository(store).addAll(listOf(photo("a"), photo("b")))
        store.close()
        store = MyObjectBox.builder().directory(dir).build()
        assertEquals(2, AssetRepository(store).total())
    }

    @Test
    fun vectorOfBaseSizeCanBeStored() {
        val box = store.boxFor(ImageAsset::class.java)
        val id = box.put(ImageAsset(stableKey = "a", imageVector = FloatArray(Model.DIMENSIONS.toInt()) { 0.1f }))
        assertEquals(Model.DIMENSIONS.toInt(), box.get(id).imageVector?.size)
    }

    @Test
    fun settingsStartWithCloudAiOff() {
        val box = store.boxFor(UserSettings::class.java)
        val id = box.put(UserSettings())
        assertFalse(box.get(id).cloudConsent)
        assertFalse(box.get(id).consentAnswered)
    }

    @Test
    fun planImportCountsDuplicatesInsideOnePick() {
        val plan = planImport(emptySet(), listOf(photo("a"), photo("a")))
        assertEquals(1, plan.toAdd.size)
        assertEquals(ImportCounts(selected = 2, added = 1, alreadyImported = 1), plan.counts)
    }

    @Test
    fun indexingIsSkippedOnlyForSameFileModelAndVersion() {
        val done = ImageAsset(indexStatus = IndexStatus.INDEXED, modelVersion = Model.VERSION, processingVersion = 1)
        assertFalse(needsIndexing(done, Model.VERSION, 1))
        assertTrue(needsIndexing(null, Model.VERSION, 1))
        assertTrue(needsIndexing(done, "another-model", 1))
        assertTrue(needsIndexing(done, Model.VERSION, 2))
        assertTrue(needsIndexing(ImageAsset(indexStatus = IndexStatus.FAILED), Model.VERSION, 1))
    }

    @Test
    fun fingerprintIsStableAndDependsOnTheFile() {
        val meta = PhotoMeta("IMG_1.jpg", 1000L, "image/jpeg", 5L)
        assertEquals(Fingerprint.of("u1", meta), Fingerprint.of("u2", meta))
        assertNotEquals(Fingerprint.of("u1", meta), Fingerprint.of("u1", meta.copy(sizeBytes = 1001L)))
        assertEquals("uri:u1", Fingerprint.of("u1", PhotoMeta(null, null, null, null)))
    }
}
