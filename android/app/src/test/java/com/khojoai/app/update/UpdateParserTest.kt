package com.khojoai.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateParserTest {
    private val full = """
        {"versionCode": 29881440, "versionName": "0.0.3", "notes": "Added: updater", "sha256": "ABCDEF12",
         "sizeBytes": 2232216, "apkKey": "apk/khojo-29881440.apk", "rolloutPercent": 100,
         "minSupportedVersionCode": 29000000, "apkUrl": "https://signed.example/apk"}
    """.trimIndent()

    @Test
    fun parsesAllFieldsAndLowercasesTheHash() {
        val info = UpdateParser.parse(full)
        assertEquals(29881440, info.versionCode)
        assertEquals("0.0.3", info.versionName)
        assertEquals("abcdef12", info.sha256)
        assertEquals(2232216L, info.sizeBytes)
        assertEquals(29000000, info.minSupportedVersionCode)
        assertEquals("https://signed.example/apk", info.apkUrl)
    }

    @Test
    fun missingOptionalFieldsDefault() {
        val info = UpdateParser.parse("""{"versionCode": 5, "sha256": "aa", "apkUrl": "u"}""")
        assertEquals(0, info.minSupportedVersionCode)
        assertEquals("", info.notes)
    }

    @Test(expected = Exception::class)
    fun missingHashIsAnErrorNotUpToDate() {
        UpdateParser.parse("""{"versionCode": 5, "apkUrl": "u"}""")
    }

    @Test
    fun newerAndRequiredChecks() {
        val info = UpdateParser.parse(full)
        assertTrue(info.isNewerThan(29881439))
        assertFalse(info.isNewerThan(29881440))
        assertTrue(info.isRequiredFor(28999999))
        assertFalse(info.isRequiredFor(29000000))
    }

    @Test
    fun noMinimumMeansNeverRequired() {
        val info = UpdateParser.parse("""{"versionCode": 5, "sha256": "aa", "apkUrl": "u"}""")
        assertFalse(info.isRequiredFor(1))
    }

    @Test
    fun hexEncodingMatchesSha256Format() {
        assertEquals("00ff10", AppUpdater.toHex(byteArrayOf(0, -1, 16)))
    }
}
