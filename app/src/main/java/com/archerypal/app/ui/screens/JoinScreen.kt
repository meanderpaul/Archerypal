package com.archerypal.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.archerypal.app.data.DiscoveredHost
import com.archerypal.app.p2p.LIBP2P_ENDPOINT_PREFIX
import com.archerypal.app.ui.components.OutdoorCard
import com.archerypal.app.ui.components.SecondaryActionButton

@Composable
fun JoinScreen(
    statusMessage: String,
    transportLabel: String,
    discoveredHosts: List<DiscoveredHost>,
    onHostSelected: (DiscoveredHost) -> Unit,
    onLeave: () -> Unit,
    scannerSlot: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Join match", style = MaterialTheme.typography.headlineMedium)
        OutdoorCard { Text(statusMessage + transportLabel) }
        scannerSlot()
        Text("Available hosts", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(discoveredHosts, key = { it.matchId }) { host ->
                val viaLibp2p = host.endpointId.startsWith(LIBP2P_ENDPOINT_PREFIX) ||
                    host.libp2pCircuitMultiaddrs.isNotEmpty() ||
                    host.libp2pMultiaddrs.isNotEmpty()
                OutdoorCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onHostSelected(host) }
                ) {
                    Text(host.hostName, style = MaterialTheme.typography.titleMedium)
                    Text("Match ${host.matchId}", style = MaterialTheme.typography.bodyMedium)
                    if (viaLibp2p) {
                        val viaRelay = host.libp2pCircuitMultiaddrs.isNotEmpty()
                        Text(
                            if (viaRelay) "Join via libp2p relay (cell / Wi‑Fi)" else "Join via libp2p (cell / Wi‑Fi)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        SecondaryActionButton("Back", onLeave)
    }
}
