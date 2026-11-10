package com.khojoai.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportMergeTest {
    @Test
    fun newPhotosAreAdded() {
        val (merged, counts) = mergeImports(emptySet(), listOf("a", "b"))
        assertEquals(setOf("a", "b"), merged)
        assertEquals(ImportCounts(selected = 2, added = 2, alreadyImported = 0), counts)
    }

    @Test
    fun alreadyImportedPhotosAreCountedNotAddedTwice() {
        val (merged, counts) = mergeImports(setOf("a"), listOf("a", "b"))
        assertEquals(setOf("a", "b"), merged)
        assertEquals(ImportCounts(selected = 2, added = 1, alreadyImported = 1), counts)
    }

    @Test
    fun duplicatesInsideOnePickCountOnce() {
        val (merged, counts) = mergeImports(emptySet(), listOf("a", "a"))
        assertEquals(setOf("a"), merged)
        assertEquals(ImportCounts(selected = 2, added = 1, alreadyImported = 1), counts)
    }
}
