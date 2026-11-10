package com.khojoai.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** User choices that must survive restarts. Moves into ObjectBox (`UserSettings`) in Sprint 3C. */
@Singleton
class SettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("khojo_settings", Context.MODE_PRIVATE)

    /** True once the person has answered the consent screen (either way). */
    fun consentAnswered(): Boolean = prefs.getBoolean(KEY_ANSWERED, false)

    /** Cloud AI is OFF unless the person turns it on. */
    fun cloudConsent(): Boolean = prefs.getBoolean(KEY_CLOUD, false)

    fun saveConsent(cloud: Boolean) {
        prefs.edit().putBoolean(KEY_CLOUD, cloud).putBoolean(KEY_ANSWERED, true).apply()
    }

    private companion object {
        const val KEY_CLOUD = "cloud_consent"
        const val KEY_ANSWERED = "consent_answered"
    }
}
