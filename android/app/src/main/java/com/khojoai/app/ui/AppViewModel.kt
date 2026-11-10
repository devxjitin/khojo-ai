package com.khojoai.app.ui

import androidx.lifecycle.ViewModel
import com.khojoai.app.data.OnboardingStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(private val onboarding: OnboardingStore) : ViewModel() {
    /** Read once at start so the first screen is right with no flicker. */
    val startRoute: String = if (onboarding.isDone()) Routes.HOME else Routes.ONBOARDING

    fun finishOnboarding() = onboarding.markDone()
}

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
}
