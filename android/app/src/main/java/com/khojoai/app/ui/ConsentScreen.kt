package com.khojoai.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.khojoai.app.R

/** Asks once whether photos may be sent to the cloud for AI. Either answer continues; "no" is the safe default. */
@Composable
fun ConsentScreen(onAnswer: (cloud: Boolean) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.consent_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.consent_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Text(
            stringResource(R.string.consent_note),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp),
        )
        Button(onClick = { onAnswer(true) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.consent_allow))
        }
        OutlinedButton(onClick = { onAnswer(false) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(stringResource(R.string.consent_deny))
        }
    }
}
