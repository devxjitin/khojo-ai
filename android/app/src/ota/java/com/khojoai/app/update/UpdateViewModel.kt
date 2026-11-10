package com.khojoai.app.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khojoai.app.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo, val required: Boolean) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class NeedsPermission(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

@HiltViewModel
class UpdateViewModel @Inject constructor(@ApplicationContext context: Context) : ViewModel() {
    private val updater = AppUpdater(context)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    init {
        check(manual = false)
    }

    fun check(manual: Boolean) {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        if (manual) _state.value = UpdateState.Checking
        viewModelScope.launch {
            updater.check().fold(
                onSuccess = { info ->
                    _state.value = when {
                        info != null -> UpdateState.Available(info, info.isRequiredFor(BuildConfig.VERSION_CODE))
                        manual -> UpdateState.UpToDate
                        else -> UpdateState.Idle
                    }
                },
                // A quiet check that fails says nothing; only a check the user asked for shows the error.
                onFailure = { _state.value = if (manual) UpdateState.Failed("Could not reach the update server. Try again.") else UpdateState.Idle },
            )
        }
    }

    fun download(info: UpdateInfo) {
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Downloading(info, 0f)
        viewModelScope.launch {
            updater.download(info) { progress -> _state.value = UpdateState.Downloading(info, progress) }.fold(
                onSuccess = { file -> install(info, file) },
                onFailure = { _state.value = UpdateState.Failed(it.message ?: "The download failed. Try again.") },
            )
        }
    }

    fun install(info: UpdateInfo, file: File) {
        _state.value = when (updater.install(file)) {
            AppUpdater.InstallStart.OPENED -> UpdateState.Ready(info, file)
            AppUpdater.InstallStart.NEEDS_PERMISSION -> UpdateState.NeedsPermission(info, file)
        }
    }

    fun dismiss() {
        if ((_state.value as? UpdateState.Available)?.required != true) _state.value = UpdateState.Idle
    }
}
