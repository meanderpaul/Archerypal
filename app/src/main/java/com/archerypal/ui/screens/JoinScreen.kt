package com.archerypal.ui.screens

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
import com.archerypal.data.DiscoveredHost
import com.archerypal.ui.components.OutdoorCard
import com.archerypal.ui.components.SecondaryActionButton

@Composable
fun JoinScreen(
    statusMessage: String,
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
        OutdoorCard { Text(statusMessage) }
        scannerSlot()
        Text("Nearby hosts", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(discoveredHosts, key = { "${it.endpointId}-${it.matchId}" }) { host ->
                OutdoorCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = host.endpointId.isNotBlank()) {
                            onHostSelected(host)
                        }
                ) {
                    Text(host.hostName, style = MaterialTheme.typography.titleMedium)
                    Text("Match ${host.matchId}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        SecondaryActionButton("Back", onLeave)
    }
}
