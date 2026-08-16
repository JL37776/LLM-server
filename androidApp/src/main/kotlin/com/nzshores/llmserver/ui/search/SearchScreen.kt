package com.nzshores.llmserver.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nzshores.llmserver.core.model.DownloadState
import com.nzshores.llmserver.core.model.ModelInfo
import com.nzshores.llmserver.ui.theme.Accent
import com.nzshores.llmserver.ui.theme.AccentDim
import com.nzshores.llmserver.ui.theme.Border
import com.nzshores.llmserver.ui.theme.Surface2
import com.nzshores.llmserver.ui.theme.TextDim
import org.koin.androidx.compose.koinViewModel

@Composable
fun SearchScreen(viewModel: SearchViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Model Hub", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search Hugging Face models…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = TextDim) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChipView(label = "GGUF", active = state.ggufOnly, onClick = viewModel::onToggleGgufOnly)
        }

        when {
            state.isLoading && state.results.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator(color = Accent) }

            state.error != null -> Text(state.error!!, color = com.nzshores.llmserver.ui.theme.Bad)

            state.results.isEmpty() -> Text("No models found for \"${state.query}\"", color = TextDim)

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(state.results, key = { it.id }) { model ->
                    ModelCard(
                        model = model,
                        onDownload = { viewModel.onDownload(model) },
                        onPause = { viewModel.onPauseDownload(model.id) },
                        onResume = { viewModel.onResumeDownload(model) },
                        onDelete = { viewModel.onDeleteDownload(model.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChipView(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (active) AccentDim else Surface2,
        shape = RoundedCornerShape(20.dp),
        border = if (active) androidx.compose.foundation.BorderStroke(1.dp, Accent) else androidx.compose.foundation.BorderStroke(1.dp, Border),
        onClick = onClick,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (active) Accent else TextDim,
        )
    }
}

@Composable
private fun ModelCard(
    model: ModelInfo,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = com.nzshores.llmserver.ui.theme.Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(model.name, style = MaterialTheme.typography.titleMedium)
            Text("${model.org}${model.updatedAt?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.bodySmall, color = TextDim)

            Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                model.selectedQuant?.let { Tag(it.label, highlighted = true) }
                Tag(model.parameterCount)
                model.contextLength?.let { Tag("${it / 1024}K context") }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(formatSize(model.selectedQuant?.sizeBytes), style = MaterialTheme.typography.bodySmall, color = TextDim)
                when (model.downloadState) {
                    DownloadState.DOWNLOADING -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("${model.downloadProgressPercent}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 4.dp))
                        IconButton(onClick = onPause) { Icon(Icons.Outlined.Pause, contentDescription = "Pause download", tint = TextDim) }
                    }
                    DownloadState.PAUSED -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("${model.downloadProgressPercent}% · paused", style = MaterialTheme.typography.bodySmall, color = TextDim, modifier = Modifier.padding(end = 4.dp))
                        Button(onClick = onResume, shape = RoundedCornerShape(10.dp)) { Text("Resume") }
                    }
                    DownloadState.DOWNLOADED -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Downloaded", color = com.nzshores.llmserver.ui.theme.Good, style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Delete download", tint = com.nzshores.llmserver.ui.theme.Bad) }
                    }
                    else -> Button(onClick = onDownload, shape = RoundedCornerShape(10.dp)) { Text("Download") }
                }
            }
            if (model.downloadState == DownloadState.DOWNLOADING || model.downloadState == DownloadState.PAUSED) {
                LinearProgressIndicator(
                    progress = { model.downloadProgressPercent / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(5.dp),
                    color = if (model.downloadState == DownloadState.PAUSED) TextDim else Accent,
                    trackColor = Surface2,
                )
            }
        }
    }
}

@Composable
private fun Tag(label: String, highlighted: Boolean = false) {
    Surface(
        color = if (highlighted) AccentDim else Surface2,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (highlighted) Accent else TextDim,
        )
    }
}

private fun formatSize(bytes: Long?): String {
    if (bytes == null) return "—"
    val gb = bytes / 1024.0 / 1024.0 / 1024.0
    return "%.1f GB".format(gb)
}
