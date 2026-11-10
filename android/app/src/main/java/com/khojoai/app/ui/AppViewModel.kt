package com.khojoai.app.ui

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khojoai.app.data.AssetRepository
import com.khojoai.app.data.ImportCounts
import com.khojoai.app.data.LegacyImports
import com.khojoai.app.data.OnboardingStore
import com.khojoai.app.data.SettingsStore
import com.khojoai.app.data.toNewAsset
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AppViewModel @Inject constructor(
    private val onboarding: OnboardingStore,
    private val settings: SettingsStore,
    private val assets: AssetRepository,
    private val legacyImports: LegacyImports,
) : ViewModel() {
    /** Read once at start so the first screen is right with no flicker. */
    val startRoute: String = when {
        !onboarding.isDone() -> Routes.ONBOARDING
        !settings.consentAnswered() -> Routes.CONSENT
        else -> Routes.HOME
    }

    private val _total = MutableStateFlow(assets.total())
    val totalImported: StateFlow<Int> = _total.asStateFlow()

    private val _lastImport = MutableStateFlow<ImportCounts?>(null)
    val lastImport: StateFlow<ImportCounts?> = _lastImport.asStateFlow()

    fun finishOnboarding() = onboarding.markDone()

    fun saveConsent(cloud: Boolean) = settings.saveConsent(cloud)

    /** Moves photos added in version 0.1.1 into the database (does nothing if there are none). */
    fun migrateLegacy(resolver: ContentResolver) {
        viewModelScope.launch(Dispatchers.IO) {
            legacyImports.migrate(resolver)
            _total.value = assets.total()
        }
    }

    /** Keeps read access to the picked photos across restarts, then records them. */
    fun importPhotos(resolver: ContentResolver, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            uris.forEach { uri ->
                try {
                    resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                    // Not every provider allows a persistent grant; the photo is still recorded for this session.
                }
            }
            _lastImport.value = assets.addAll(uris.map { toNewAsset(resolver, it.toString()) })
            _total.value = assets.total()
        }
    }
}

object Routes {
    const val ONBOARDING = "onboarding"
    const val CONSENT = "consent"
    const val HOME = "home"
    const val IMPORT = "import"
}
