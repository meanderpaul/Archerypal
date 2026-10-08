package com.archerypal.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.archerypal.app.data.DiscoveredHost
import com.archerypal.app.ui.components.OutdoorCard
import com.archerypal.app.ui.components.QrScannerView
import com.archerypal.app.ui.components.SecondaryActionButton
import com.archerypal.app.ui.layout.rememberScreenMetrics
import com.archerypal.app.viewmodel.ConnectionStatus

@Composable
fun JoinScreen(
    statusMessage: String,
    connectionStatus: ConnectionStatus,
    discoveredHosts: List<DiscoveredHost>,
    connectingMatchId: String?,
    onHostSelected: (DiscoveredHost) -> Unit,
    onLeave: () -> Unit,
    onQrScanned: (String) -> Unit
) {
    val metrics = rememberScreenMetrics()
    val isConnecting = connectionStatus == ConnectionStatus.Connecting

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = metrics.horizontalPadding,
                vertical = metrics.verticalPadding
            ),
        verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing)
    ) {
        Text("Join match", style = MaterialTheme.typography.headlineMedium)
        OutdoorCard { Text(statusMessage) }
        Text(
            "Scan the host QR code or tap them when they appear below",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(0.8f)
        )

        if (metrics.isExpandedWidth) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metrics.sectionSpacing)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    QrScannerView(
                        onQrScanned = onQrScanned,
                        previewHeight = metrics.cameraPreviewHeight
                    )
                }
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Text("Nearby hosts", style = MaterialTheme.typography.titleMedium)
                    JoinHostList(
                        discoveredHosts = discoveredHosts,
                        connectingMatchId = connectingMatchId,
                        isConnecting = isConnecting,
                        onHostSelected = onHostSelected,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            QrScannerView(
                onQrScanned = onQrScanned,
                previewHeight = metrics.cameraPreviewHeight
            )
            Text("Nearby hosts", style = MaterialTheme.typography.titleMedium)
            JoinHostList(
                discoveredHosts = discoveredHosts,
                connectingMatchId = connectingMatchId,
                isConnecting = isConnecting,
                onHostSelected = onHostSelected,
                modifier = Modifier.weight(1f)
            )
        }
        SecondaryActionButton("Back", onLeave)
    }
}

@Composable
private fun JoinHostList(
    discoveredHosts: List<DiscoveredHost>,
    connectingMatchId: String?,
    isConnecting: Boolean,
    onHostSelected: (DiscoveredHost) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        items(discoveredHosts, key = { it.matchId }) { host ->
            val chipLabel = when {
                isConnecting && connectingMatchId == host.matchId -> "Connecting…"
                host.endpointId.isNotBlank() -> "Tap to connect"
                else -> "Nearby"
            }
            val canTap = host.endpointId.isNotBlank() && !isConnecting
            OutdoorCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = canTap) { onHostSelected(host) }
            ) {
                Text(host.hostName, style = MaterialTheme.typography.titleMedium)
                Text("Match ${host.matchId.takeLast(8)}", style = MaterialTheme.typography.bodyMedium)
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = chipLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}
