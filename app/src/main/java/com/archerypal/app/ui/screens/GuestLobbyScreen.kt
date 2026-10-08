package com.archerypal.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.archerypal.app.data.AppConstants
import com.archerypal.app.data.PlayerInfo
import com.archerypal.app.ui.components.OutdoorCard
import com.archerypal.app.ui.components.SecondaryActionButton
import com.archerypal.app.ui.layout.AdaptiveScreenColumn
import com.archerypal.app.ui.theme.FieldGreen

@Composable
fun GuestLobbyScreen(
    hostName: String,
    matchId: String,
    players: List<PlayerInfo>,
    statusMessage: String,
    onLeave: () -> Unit
) {
    AdaptiveScreenColumn(scroll = true) { _ ->
        Text("Guest lobby", style = MaterialTheme.typography.headlineMedium)
        OutdoorCard {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text(
                    text = "Waiting on host",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text("Host: $hostName", style = MaterialTheme.typography.titleMedium)
            Text(
                "Match ${matchId.takeLast(8)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            if (statusMessage.isNotBlank()) {
                Text(
                    statusMessage,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        OutdoorCard {
            Text(
                "The host is setting targets. Scoring starts when they open the match.",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "Nearby archers can still scan the host QR to join this lobby.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
        Text(
            "Archers (${players.size} / ${AppConstants.MAX_PLAYERS})",
            style = MaterialTheme.typography.titleMedium
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            players.forEach { player ->
                OutdoorCard(modifier = Modifier.fillMaxWidth()) {
                    val label = if (player.isHost) "${player.name} (host)" else player.name
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (player.isHost) FieldGreen else MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
        SecondaryActionButton("Leave match", onLeave)
    }
}
