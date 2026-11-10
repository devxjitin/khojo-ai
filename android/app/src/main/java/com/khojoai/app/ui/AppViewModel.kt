package com.khojoai.app.ui

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.khojoai.app.data.ImportCounts
import com.khojoai.app.data.ImportStore
import com.khojoai.app.data.OnboardingStore
import com.khojoai.app.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class AppViewModel @Inject constructor(
    private val onboarding: OnboardingStore,
    private val settings: SettingsStore,
    private val imports: ImportStore,
) : ViewModel() {
    /** Read once at start so the first screen is right with no flicker. */
    val startRoute: String = when {
        !onboarding.isDone() -> Routes.ONBOARDING
        !settings.consentAnswered() -> Routes.CONSENT
        else -> Routes.HOME
    }

    private val _total = MutableStateFlow(imports.total())
    val totalImported: StateFlow<Int> = _total.asStateFlow()

    private val _lastImport = MutableStateFlow<ImportCounts?>(null)
    val lastImport: StateFlow<ImportCounts?> = _lastImport.asStateFlow()

    fun finishOnboarding() = onboarding.markDone()

    fun saveConsent(cloud: Boolean) = settings.saveConsent(cloud)

    /** Keeps read access to the picked photos across restarts, then records them. */
    fun importPhotos(resolver: ContentResolver, uris: List<Uri>) {
        if (uris.isEmpty()) return
        uris.forEach { uri ->
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Not every provider allows a persistent grant; the photo is still recorded for this session.
            }
        }
        _lastImport.value = imports.add(uris.map { it.toString() })
        _total.value = imports.total()
    }
}

object Routes {
    const val ONBOARDING = "onboarding"
    const val CONSENT = "consent"
    const val HOME = "home"
    const val IMPORT = "import"
}
