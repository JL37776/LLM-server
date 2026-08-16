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
import com.nzshores.llmserver.data.local.LocalGgufFile
import com.nzshores.llmserver.data.local.LocalModelScanner
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
    val lastLoadModelId: String? = null,
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val scanResults: List<LocalGgufFile> = emptyList(),
    val showScanDialog: Boolean = false,
)

class LibraryViewModel(
    private val repository: ModelRepository,
    private val engine: InferenceEngine,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val scanner = LocalModelScanner()

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
                    merged.copy(
                        isLoading = current.isLoading,
                        lastLoadResult = current.lastLoadResult,
                        lastLoadModelId = current.lastLoadModelId,
                        devicePreference = current.devicePreference,
                        isScanning = current.isScanning,
                        scanResults = current.scanResults,
                        showScanDialog = current.showScanDialog,
                    )
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
            _uiState.update { it.copy(isLoading = false, lastLoadResult = result, lastLoadModelId = model.id) }
        }
    }

    fun onUnload() {
        viewModelScope.launch {
            engine.unload()
            _uiState.update { it.copy(lastLoadResult = null, lastLoadModelId = null) }
        }
    }

    fun onDelete(modelId: String) {
        viewModelScope.launch { repository.deleteLocal(modelId) }
    }

    fun onScanDevice() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, showScanDialog = true) }
            val results = scanner.scan()
            val knownPaths = (_uiState.value.notLoaded + listOfNotNull(_uiState.value.loaded))
                .mapNotNull { it.localPath }
                .toSet()
            val filtered = results.filter { it.path !in knownPaths }
            _uiState.update { it.copy(isScanning = false, scanResults = filtered) }
        }
    }

    fun onDismissScanDialog() {
        _uiState.update { it.copy(showScanDialog = false, scanResults = emptyList()) }
    }

    fun onImportFile(filePath: String) {
        viewModelScope.launch {
            repository.importLocal(filePath)
            _uiState.update { state ->
                state.copy(scanResults = state.scanResults.filter { it.path != filePath })
            }
        }
    }

    fun onImportFromUri(filePath: String) {
        viewModelScope.launch { repository.importLocal(filePath) }
    }
}
