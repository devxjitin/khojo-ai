package com.khojoai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { HomeScreen() }
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyLarge)
        // Shows which distribution this build is (aws-ota or google-play) and its version, handy when checking OTA updates.
        Text("v${BuildConfig.VERSION_NAME} - ${BuildConfig.UPDATE_CHANNEL}", style = MaterialTheme.typography.labelMedium)
        // Beta (ota) build: update prompt. The Play build shows nothing here.
        UpdateCard(Modifier.padding(top = 24.dp))
    }
}
