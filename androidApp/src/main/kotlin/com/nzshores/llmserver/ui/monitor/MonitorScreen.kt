package com.nzshores.llmserver.ui.monitor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.nzshores.llmserver.core.model.ActiveBackend
import com.nzshores.llmserver.ui.theme.Accent
import com.nzshores.llmserver.ui.theme.Bad
import com.nzshores.llmserver.ui.theme.Border
import com.nzshores.llmserver.ui.theme.Good
import com.nzshores.llmserver.ui.theme.Surface as SurfaceColor
import com.nzshores.llmserver.ui.theme.Surface2
import com.nzshores.llmserver.ui.theme.TextDim
import com.nzshores.llmserver.ui.theme.Warn
import org.koin.androidx.compose.koinViewModel

@Composable
fun MonitorScreen(viewModel: MonitorViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val metrics = state.metrics

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Load Monitor", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))

        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.height(140.dp)) {
            items(
                listOf(
                    Triple("Throughput", "%.1f".format(metrics.tokensPerSecond), "tok/s"),
                    Triple("Queued requests", metrics.queueDepth.toString(), ""),
                    Triple("VRAM used", "%.1f".format(metrics.vramUsedBytes / 1024.0 / 1024.0 / 1024.0), "GB"),
                    Triple("Last latency", metrics.lastLatencyMillis.toString(), "ms"),
                ),
            ) { (label, value, unit) -> StatTile(label, value, unit) }
        }

        ChartCard(title = "Tokens/s (last 30s)", trailing = "%.1f".format(metrics.tokensPerSecond)) {
            Sparkline(metrics.tokensPerSecondHistory)
        }

        ChartCard(title = "System Resources") {
            BarRow("CPU", metrics.cpuUsagePercent, Accent)
            BarRow("RAM", metrics.ramUsagePercent, Good)
            BarRow("Batt", metrics.batteryTempCelsius, Warn, unit = "°C", maxValue = 60f)
        }

        ChartCard(title = "Currently Loaded", last = true) {
            val status = state.engineStatus
            if (status.loadedModelName != null) {
                Text("${status.loadedModelName} · ${status.backend.label()} mode", style = MaterialTheme.typography.bodyMedium)
                Text("Vulkan backend · running", style = MaterialTheme.typography.labelSmall, color = TextDim)
            } else {
                Text("No model loaded", style = MaterialTheme.typography.bodyMedium, color = TextDim)
            }
        }
    }
}

private fun ActiveBackend.label() = when (this) {
    ActiveBackend.GPU -> "GPU"
    ActiveBackend.CPU -> "CPU"
    ActiveBackend.NONE -> "idle"
}

@Composable
private fun StatTile(label: String, value: String, unit: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        border = BorderStroke(1.dp, Border),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(5.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextDim)
            Row {
                Text(value, style = MaterialTheme.typography.titleLarge)
                if (unit.isNotEmpty()) Text(" $unit", style = MaterialTheme.typography.labelSmall, color = TextDim)
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, trailing: String? = null, last: Boolean = false, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        border = BorderStroke(1.dp, Border),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = if (last) 16.dp else 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.bodySmall, color = TextDim)
                trailing?.let { Text(it, color = Accent, style = MaterialTheme.typography.bodySmall) }
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun Sparkline(history: List<Float>) {
    Canvas(modifier = Modifier.fillMaxWidth().height(60.dp)) {
        if (history.size < 2) return@Canvas
        val maxValue = (history.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val stepX = size.width / (history.size - 1)
        val path = Path()
        history.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height - (value / maxValue) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = Accent, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
    }
}

@Composable
private fun BarRow(label: String, value: Float, color: Color, unit: String = "%", maxValue: Float = 100f) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextDim, modifier = Modifier.width(38.dp))
        Canvas(modifier = Modifier.weight(1f).height(8.dp)) {
            val corner = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            drawRoundRect(color = Surface2, cornerRadius = corner)
            val fraction = (value / maxValue).coerceIn(0f, 1f)
            drawRoundRect(
                color = color,
                size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height),
                cornerRadius = corner,
            )
        }
        Text("${value.toInt()}$unit", style = MaterialTheme.typography.labelSmall, color = TextDim, modifier = Modifier.width(40.dp))
    }
}
