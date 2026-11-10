package com.khojoai.app.data

import android.content.Context
import com.khojoai.app.data.db.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import io.objectbox.BoxStore
import javax.inject.Inject
import javax.inject.Singleton

/** The person's choices, kept in the database as a single `UserSettings` row. */
@Singleton
class SettingsStore @Inject constructor(boxStore: BoxStore, @ApplicationContext context: Context) {
    private val box = boxStore.boxFor(UserSettings::class.java)
    private val legacy = context.getSharedPreferences("khojo_settings", Context.MODE_PRIVATE)

    private fun current(): UserSettings = box.all.firstOrNull() ?: migrateFromLegacy()

    /** Version 0.1.1 kept the consent answer in a small preferences file; carry it over once. */
    private fun migrateFromLegacy(): UserSettings {
        val row = UserSettings(
            cloudConsent = legacy.getBoolean("cloud_consent", false),
            consentAnswered = legacy.getBoolean("consent_answered", false),
        )
        box.put(row)
        legacy.edit().clear().apply()
        return row
    }

    /** True once the person has answered the consent screen (either way). */
    fun consentAnswered(): Boolean = current().consentAnswered

    /** Cloud AI is OFF unless the person turns it on. */
    fun cloudConsent(): Boolean = current().cloudConsent

    fun saveConsent(cloud: Boolean) {
        val row = current()
        row.cloudConsent = cloud
        row.consentAnswered = true
        box.put(row)
    }
}
