package com.khojoai.app.update

import org.json.JSONObject

/** A build published to the beta update server (the contents of latest.json plus a short-lived download link). */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    val sha256: String,
    val sizeBytes: Long,
    /** Builds older than this must update before the app is used. 0 means no minimum. */
    val minSupportedVersionCode: Int,
    /** Download link; only valid for about 30 minutes after the check. */
    val apkUrl: String,
) {
    /** True when the installed build is too old to keep running (the server raised the minimum). */
    fun isRequiredFor(installedVersionCode: Int): Boolean = installedVersionCode < minSupportedVersionCode

    fun isNewerThan(installedVersionCode: Int): Boolean = versionCode > installedVersionCode
}

object UpdateParser {
    /** Parses the server answer. Throws if a required field is missing, so a malformed answer never looks like "up to date". */
    fun parse(json: String): UpdateInfo {
        val o = JSONObject(json)
        return UpdateInfo(
            versionCode = o.getInt("versionCode"),
            versionName = o.optString("versionName"),
            notes = o.optString("notes"),
            sha256 = o.getString("sha256").lowercase(),
            sizeBytes = o.optLong("sizeBytes"),
            minSupportedVersionCode = o.optInt("minSupportedVersionCode", 0),
            apkUrl = o.getString("apkUrl"),
        )
    }
}
