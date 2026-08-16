package com.nzshores.llmserver.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzshores.llmserver.core.model.ModelInfo
import com.nzshores.llmserver.core.repository.ModelRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "qwen2.5 7b",
    val ggufOnly: Boolean = true,
    val results: List<ModelInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class SearchViewModel(private val repository: ModelRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        runSearch(_uiState.value.query)
        viewModelScope.launch {
            repository.library().collect { library ->
                _uiState.update { state ->
                    state.copy(results = mergeDownloadState(state.results, library))
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            runSearch(query)
        }
    }

    fun onToggleGgufOnly() {
        _uiState.update { it.copy(ggufOnly = !it.ggufOnly) }
        runSearch(_uiState.value.query)
    }

    fun onDownload(model: ModelInfo) {
        viewModelScope.launch { repository.download(model) }
    }

    private fun runSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.search(query, _uiState.value.ggufOnly).fold(
                onSuccess = { results ->
                    _uiState.update { it.copy(results = results, isLoading = false) }
                },
                onFailure = { throwable ->
                    _uiState.update { it.copy(isLoading = false, error = throwable.message ?: "Search failed") }
                },
            )
        }
    }

    private fun mergeDownloadState(results: List<ModelInfo>, library: List<ModelInfo>): List<ModelInfo> {
        val byId = library.associateBy { it.id }
        return results.map { result -> byId[result.id] ?: result }
    }
}
