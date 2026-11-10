package com.khojoai.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Remembers whether the person has already seen the intro slides. */
@Singleton
class OnboardingStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("khojo_onboarding", Context.MODE_PRIVATE)

    fun isDone(): Boolean = prefs.getBoolean(KEY_DONE, false)

    fun markDone() {
        prefs.edit().putBoolean(KEY_DONE, true).apply()
    }

    private companion object {
        const val KEY_DONE = "onboarding_done"
    }
}
