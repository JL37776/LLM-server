package com.nzshores.llmserver.ui.library

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nzshores.llmserver.core.model.DevicePreference
import com.nzshores.llmserver.core.model.ModelInfo
import com.nzshores.llmserver.data.local.LocalGgufFile
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
    val context = LocalContext.current

    var hasStoragePermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true
            }
        )
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        if (hasStoragePermission) viewModel.onScanDevice()
    }

    val legacyPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasStoragePermission = granted
        if (granted) viewModel.onScanDevice()
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("My Models", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))

        SectionLabel("Device preference")
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DeviceOption("GPU first", state.devicePreference == DevicePreference.GPU_FIRST) { viewModel.onSelectDevicePreference(DevicePreference.GPU_FIRST) }
            DeviceOption("GPU only", state.devicePreference == DevicePreference.GPU_ONLY) { viewModel.onSelectDevicePreference(DevicePreference.GPU_ONLY) }
            DeviceOption("CPU only", state.devicePreference == DevicePreference.CPU_ONLY) { viewModel.onSelectDevicePreference(DevicePreference.CPU_ONLY) }
        }

        OutlinedButton(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        hasStoragePermission = true
                        viewModel.onScanDevice()
                    } else {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:${context.packageName}")
                        manageStorageLauncher.launch(intent)
                    }
                } else {
                    legacyPermLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            },
            border = BorderStroke(1.dp, Accent),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = Accent, modifier = Modifier.padding(end = 6.dp))
            Text("Scan device for GGUF files", color = Accent)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.loaded != null) {
                item { SectionLabel("Loaded") }
                item {
                    LoadedCard(
                        model = state.loaded!!,
                        onUnload = viewModel::onUnload,
                    )
                }
            }

            item { SectionLabel("Downloaded") }

            if (state.notLoaded.isEmpty() && state.loaded == null) {
                item { Text("No models yet. Search & download, or scan device for existing GGUF files.", color = TextDim) }
            }

            items(state.notLoaded, key = { it.id }) { model ->
                val showFallbackWarning = state.lastLoadResult?.fellBackToCpu == true && state.lastLoadModelId == model.id
                IdleCard(
                    model = model,
                    fellBackToCpu = showFallbackWarning,
                    isLoading = state.isLoading,
                    onLoad = { viewModel.onLoad(model) },
                    onDelete = { viewModel.onDelete(model.id) },
                )
            }
        }
    }

    if (state.showScanDialog) {
        ScanResultsDialog(
            isScanning = state.isScanning,
            results = state.scanResults,
            onImport = viewModel::onImportFile,
            onDismiss = viewModel::onDismissScanDialog,
        )
    }
}

@Composable
private fun ScanResultsDialog(
    isScanning: Boolean,
    results: List<LocalGgufFile>,
    onImport: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        title = { Text("GGUF files on device") },
        text = {
            if (isScanning) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
                    Text("Scanning...", color = TextDim)
                }
            } else if (results.isEmpty()) {
                Text("No new GGUF files found on this device.", color = TextDim)
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(results, key = { it.path }) { file ->
                        ScanResultItem(file = file, onImport = { onImport(file.path) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = Accent) }
        },
    )
}

@Composable
private fun ScanResultItem(file: LocalGgufFile, onImport: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface2),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${formatSize(file.sizeBytes)} · ${shortenPath(file.path)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(onClick = onImport, shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Import")
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
                Text("${model.name} · ${model.selectedQuant?.label ?: ""}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Badge("Running", Good)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onUnload, border = BorderStroke(1.dp, Bad.copy(alpha = 0.4f))) {
                    Text("Unload", color = Bad)
                }
            }
        }
    }
}

@Composable
private fun IdleCard(model: ModelInfo, fellBackToCpu: Boolean, isLoading: Boolean, onLoad: () -> Unit, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        border = BorderStroke(1.dp, Border),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${model.name} · ${model.selectedQuant?.label ?: ""}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Bad) }
                    Button(onClick = onLoad, enabled = !isLoading, shape = RoundedCornerShape(10.dp)) { Text("Load") }
                }
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
    if (bytes == null || bytes == 0L) return "-"
    val gb = bytes / 1024.0 / 1024.0 / 1024.0
    return if (gb >= 1.0) "%.1f GB".format(gb) else "%.0f MB".format(bytes / 1024.0 / 1024.0)
}

private fun shortenPath(path: String): String {
    val prefix = "/storage/emulated/0/"
    return if (path.startsWith(prefix)) path.removePrefix(prefix) else path
}
