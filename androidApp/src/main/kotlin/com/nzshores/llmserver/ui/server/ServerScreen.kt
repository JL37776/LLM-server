package com.nzshores.llmserver.ui.server

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nzshores.llmserver.ui.theme.Accent
import com.nzshores.llmserver.ui.theme.Border
import com.nzshores.llmserver.ui.theme.Good
import com.nzshores.llmserver.ui.theme.Surface as SurfaceColor
import com.nzshores.llmserver.ui.theme.Surface2
import com.nzshores.llmserver.ui.theme.TextDim
import org.koin.androidx.compose.koinViewModel

@Composable
fun ServerScreen(viewModel: ServerViewModel = koinViewModel()) {
    val state by viewModel.runtimeInfo.collectAsState()
    val endpoint = "http://${state.lanIpAddress ?: "—"}:${state.config.port}/v1"

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Server Console", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))

        Button(
            onClick = viewModel::onToggleServer,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = if (state.isRunning) Good else Surface2),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(if (state.isRunning) "● Server running · tap to stop" else "○ Server stopped · tap to start")
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceColor),
            border = BorderStroke(1.dp, Border),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LAN access address", style = MaterialTheme.typography.bodySmall, color = TextDim)
                if (state.isRunning) {
                    val qr = remember(endpoint) { generateQrCode(endpoint) }
                    Surface(color = androidx.compose.ui.graphics.Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(vertical = 12.dp).size(140.dp)) {
                        Image(bitmap = qr, contentDescription = "QR code for $endpoint", modifier = Modifier.padding(10.dp))
                    }
                }
                Surface(color = Surface2, shape = RoundedCornerShape(8.dp)) {
                    Text(endpoint, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), color = Accent, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Text("OPENAI-COMPATIBLE API", style = MaterialTheme.typography.labelSmall, color = TextDim, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceColor),
            border = BorderStroke(1.dp, Border),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                SettingRow(title = "Listen port", desc = "Default 8080") {
                    Stepper(value = state.config.port.toString(), onMinus = { viewModel.onPortChange(-1) }, onPlus = { viewModel.onPortChange(1) })
                }
                HorizontalDivider(color = Border)
                SettingRow(title = "Require API key", desc = "Other LAN devices must send the key") {
                    Switch(checked = state.config.requireApiKey, onCheckedChange = { viewModel.onToggleRequireApiKey() }, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
                }
                HorizontalDivider(color = Border)
                SettingRow(title = "Restrict to current Wi-Fi subnet", desc = "Blocks access from other subnets") {
                    Switch(checked = state.config.restrictToLanSubnet, onCheckedChange = { viewModel.onToggleSubnetRestriction() }, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
                }
                HorizontalDivider(color = Border)
                SettingRow(title = "Max concurrent requests", desc = "Extra requests are queued") {
                    Stepper(value = state.config.maxConcurrentRequests.toString(), onMinus = { viewModel.onMaxConcurrentChange(-1) }, onPlus = { viewModel.onMaxConcurrentChange(1) })
                }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, desc: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(desc, style = MaterialTheme.typography.labelSmall, color = TextDim)
        }
        trailing()
    }
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StepperButton("–", onMinus)
        Text(value, style = MaterialTheme.typography.bodyMedium)
        StepperButton("+", onPlus)
    }
}

@Composable
private fun StepperButton(symbol: String, onClick: () -> Unit) {
    Surface(color = Surface2, border = BorderStroke(1.dp, Border), shape = RoundedCornerShape(6.dp), onClick = onClick) {
        Text(symbol, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}
