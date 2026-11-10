package com.khojoai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.khojoai.app.ui.AppViewModel
import com.khojoai.app.ui.ConsentScreen
import com.khojoai.app.ui.ImportScreen
import com.khojoai.app.ui.OnboardingScreen
import com.khojoai.app.ui.Routes
import com.khojoai.app.ui.theme.KhojoTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        appViewModel.migrateLegacy(contentResolver)
        setContent {
            KhojoTheme {
                Surface(Modifier.fillMaxSize()) {
                    val nav = rememberNavController()
                    NavHost(nav, startDestination = appViewModel.startRoute) {
                        composable(Routes.ONBOARDING) {
                            OnboardingScreen(onFinish = {
                                appViewModel.finishOnboarding()
                                nav.navigate(Routes.CONSENT) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                            })
                        }
                        composable(Routes.CONSENT) {
                            ConsentScreen(onAnswer = { cloud ->
                                appViewModel.saveConsent(cloud)
                                nav.navigate(Routes.HOME) { popUpTo(Routes.CONSENT) { inclusive = true } }
                            })
                        }
                        composable(Routes.HOME) { HomeScreen(onAddPhotos = { nav.navigate(Routes.IMPORT) }) }
                        composable(Routes.IMPORT) { ImportScreen(appViewModel, onBack = { nav.popBackStack() }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onAddPhotos: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyLarge)
        // The version is handy when checking that an OTA update arrived.
        Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium)
        Button(onClick = onAddPhotos, modifier = Modifier.padding(top = 24.dp)) {
            Text(stringResource(R.string.home_add_photos))
        }
        // Update prompt (the app updates itself from our AWS bucket).
        UpdateCard(Modifier.padding(top = 24.dp))
    }
}
