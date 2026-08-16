package com.nzshores.llmserver.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzshores.llmserver.core.engine.InferenceEngine
import com.nzshores.llmserver.core.model.DevicePreference
import com.nzshores.llmserver.core.model.DownloadState
import com.nzshores.llmserver.core.model.EngineStatus
import com.nzshores.llmserver.core.model.LoadResult
import com.nzshores.llmserver.core.model.ModelInfo
import com.nzshores.llmserver.core.repository.ModelRepository
import com.nzshores.llmserver.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val loaded: ModelInfo? = null,
    val notLoaded: List<ModelInfo> = emptyList(),
    val engineStatus: EngineStatus? = null,
    val devicePreference: DevicePreference = DevicePreference.GPU_FIRST,
    val lastLoadResult: LoadResult? = null,
    val isLoading: Boolean = false,
)

class LibraryViewModel(
    private val repository: ModelRepository,
    private val engine: InferenceEngine,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { _uiState.update { it.copy(devicePreference = settingsRepository.loadDevicePreference()) } }
        viewModelScope.launch {
            combine(repository.library(), engine.status) { library, status ->
                val downloaded = library.filter { it.downloadState == DownloadState.DOWNLOADED }
                val loaded = downloaded.find { it.id == status.loadedModelId }
                LibraryUiState(
                    loaded = loaded,
                    notLoaded = downloaded.filterNot { it.id == status.loadedModelId },
                    engineStatus = status,
                )
            }.collect { merged ->
                _uiState.update { current ->
                    merged.copy(isLoading = current.isLoading, lastLoadResult = current.lastLoadResult, devicePreference = current.devicePreference)
                }
            }
        }
    }

    fun onSelectDevicePreference(preference: DevicePreference) {
        _uiState.update { it.copy(devicePreference = preference) }
        viewModelScope.launch { settingsRepository.saveDevicePreference(preference) }
    }

    fun onLoad(model: ModelInfo) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = engine.load(model, _uiState.value.devicePreference)
            _uiState.update { it.copy(isLoading = false, lastLoadResult = result) }
        }
    }

    fun onUnload() {
        viewModelScope.launch { engine.unload() }
    }

    fun onDelete(modelId: String) {
        viewModelScope.launch { repository.deleteLocal(modelId) }
    }
}
