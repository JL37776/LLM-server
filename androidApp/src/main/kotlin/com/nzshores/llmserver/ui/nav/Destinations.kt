package com.nzshores.llmserver.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.ui.graphics.vector.ImageVector

enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    Search("search", "Search", Icons.Outlined.Search),
    Library("library", "Library", Icons.Outlined.Dashboard),
    Server("server", "Server", Icons.Outlined.Storage),
    Monitor("monitor", "Monitor", Icons.Outlined.Timeline),
}
