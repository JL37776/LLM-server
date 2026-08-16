package com.nzshores.llmserver.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nzshores.llmserver.core.model.DevicePreference
import com.nzshores.llmserver.core.model.ModelInfo
import com.nzshores.llmserver.ui.theme.Accent
import com.nzshores.llmserver.ui.theme.AccentDim
import com.nzshores.llmserver.ui.theme.Bad
import com.nzshores.llmserver.ui.theme.Border
import com.nzshores.llmserver.ui.theme.Good
import com.nzshores.llmserver.ui.theme.Surface as SurfaceColor
import com.nzshores.llmserver.ui.theme.Surface2
import com.nzshores.llmserver.ui.theme.TextDim
import com.nzshores.llmserver.ui.theme.Warn
import org.koin.androidx.compose.koinViewModel

@Composable
fun LibraryScreen(viewModel: LibraryViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("My Models", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.loaded != null) {
                item { SectionLabel("Loaded") }
                item {
                    LoadedCard(
                        model = state.loaded!!,
                        devicePreference = state.devicePreference,
                        onSelectPreference = viewModel::onSelectDevicePreference,
                        onUnload = viewModel::onUnload,
                    )
                }
            }

            item { SectionLabel("Downloaded · Not Loaded") }

            if (state.notLoaded.isEmpty() && state.loaded == null) {
                item { Text("No models downloaded yet. Search and download one first.", color = TextDim) }
            }

            items(state.notLoaded, key = { it.id }) { model ->
                IdleCard(
                    model = model,
                    fellBackToCpu = state.lastLoadResult?.fellBackToCpu == true,
                    isLoading = state.isLoading,
                    onLoad = { viewModel.onLoad(model) },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextDim,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun LoadedCard(
    model: ModelInfo,
    devicePreference: DevicePreference,
    onSelectPreference: (DevicePreference) -> Unit,
    onUnload: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        border = BorderStroke(1.dp, Good),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${model.name} · ${model.selectedQuant?.label ?: ""}", style = MaterialTheme.typography.titleMedium)
                Badge("Running", Good)
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeviceOption("GPU first", devicePreference == DevicePreference.GPU_FIRST) { onSelectPreference(DevicePreference.GPU_FIRST) }
                DeviceOption("GPU only", devicePreference == DevicePreference.GPU_ONLY) { onSelectPreference(DevicePreference.GPU_ONLY) }
                DeviceOption("CPU only", devicePreference == DevicePreference.CPU_ONLY) { onSelectPreference(DevicePreference.CPU_ONLY) }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("VRAM used —", style = MaterialTheme.typography.bodySmall, color = TextDim)
                OutlinedButton(onClick = onUnload, border = BorderStroke(1.dp, Bad.copy(alpha = 0.4f))) {
                    Text("Unload", color = Bad)
                }
            }
        }
    }
}

@Composable
private fun IdleCard(model: ModelInfo, fellBackToCpu: Boolean, isLoading: Boolean, onLoad: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        border = BorderStroke(1.dp, Border),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${model.name} · ${model.selectedQuant?.label ?: ""}", style = MaterialTheme.typography.titleMedium)
                Badge("Idle", TextDim, background = Surface2)
            }

            if (fellBackToCpu) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).background(Warn.copy(alpha = 0.08f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = Warn, modifier = Modifier.padding(0.dp))
                    Text("Last load: out of VRAM, auto-fell back to CPU mode", style = MaterialTheme.typography.bodySmall, color = Warn)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatSize(model.selectedQuant?.sizeBytes), style = MaterialTheme.typography.bodySmall, color = TextDim)
                Button(onClick = onLoad, enabled = !isLoading, shape = RoundedCornerShape(10.dp)) { Text("Load") }
            }
        }
    }
}

@Composable
private fun RowScope.DeviceOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) AccentDim else Surface2,
        border = BorderStroke(1.dp, if (selected) Accent else Border),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.weight(1f),
        onClick = onClick,
    ) {
        Text(
            label,
            modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) Accent else TextDim,
        )
    }
}

@Composable
private fun Badge(text: String, color: androidx.compose.ui.graphics.Color, background: androidx.compose.ui.graphics.Color = color.copy(alpha = 0.15f)) {
    Surface(color = background, shape = RoundedCornerShape(8.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Medium)
    }
}

private fun formatSize(bytes: Long?): String {
    if (bytes == null) return "—"
    val gb = bytes / 1024.0 / 1024.0 / 1024.0
    return "%.1f GB".format(gb)
}
