package com.khojoai.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.khojoai.app.update.UpdateState
import com.khojoai.app.update.UpdateViewModel

/** Beta build only: shows "update available" with the notes, downloads the build and starts Android's installer. */
@Composable
fun UpdateCard(modifier: Modifier = Modifier) {
    val vm: UpdateViewModel = viewModel()
    val state by vm.state.collectAsState()
    if (state is UpdateState.Idle) return

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (val s = state) {
                UpdateState.Idle -> Unit
                UpdateState.Checking -> Text("Checking for updates...")
                UpdateState.UpToDate -> Text("You have the latest version.")
                is UpdateState.Available -> {
                    Text(
                        if (s.required) "Update required: ${s.info.versionName}" else "Update available: ${s.info.versionName}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (s.info.notes.isNotBlank()) Text(s.info.notes, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.download(s.info) }) { Text("Update") }
                        if (!s.required) TextButton(onClick = vm::dismiss) { Text("Later") }
                    }
                }
                is UpdateState.Downloading -> {
                    Text("Downloading ${s.info.versionName}...")
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                }
                is UpdateState.Ready -> {
                    Text("Ready to install.")
                    Button(onClick = { vm.install(s.info, s.file) }) { Text("Install") }
                }
                is UpdateState.NeedsPermission -> {
                    Text("Allow Khojo AI to install updates in Settings, then come back and tap Install.")
                    Button(onClick = { vm.install(s.info, s.file) }) { Text("Install") }
                }
                is UpdateState.Failed -> {
                    Text(s.message)
                    TextButton(onClick = { vm.check(manual = true) }) { Text("Try again") }
                }
            }
        }
    }
}
