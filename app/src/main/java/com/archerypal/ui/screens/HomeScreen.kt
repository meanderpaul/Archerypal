package com.archerypal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.archerypal.data.LeaderboardRow
import com.archerypal.data.SavedFriend
import com.archerypal.ui.components.OutdoorCard
import com.archerypal.ui.components.PrimaryActionButton
import com.archerypal.ui.components.RankedLeaderboardList
import com.archerypal.ui.components.SecondaryActionButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    playerName: String,
    onNameChange: (String) -> Unit,
    onHost: () -> Unit,
    onJoin: () -> Unit,
    onRematchHost: () -> Unit,
    onResyncJoin: (String) -> Unit,
    savedFriends: List<SavedFriend>,
    lastMatchGroup: List<String>,
    globalLeaderboard: List<LeaderboardRow>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Archerypal", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Offline score tracking for the field",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )
        OutlinedTextField(
            value = playerName,
            onValueChange = onNameChange,
            label = { Text("Your name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        PrimaryActionButton("Host match", onHost, enabled = playerName.isNotBlank())
        SecondaryActionButton("Join match", onJoin)

        if (lastMatchGroup.isNotEmpty()) {
            OutdoorCard {
                Text("Last group", style = MaterialTheme.typography.titleMedium)
                Text(lastMatchGroup.joinToString(", "), modifier = Modifier.padding(top = 4.dp))
                PrimaryActionButton(
                    text = "Rematch with same group",
                    onClick = onRematchHost,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }

        if (savedFriends.isNotEmpty()) {
            OutdoorCard {
                Text("Remembered archers", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tap a name to resync and join their next match.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    savedFriends.take(8).forEach { friend ->
                        AssistChip(
                            onClick = { onResyncJoin(friend.name) },
                            label = { Text(friend.name) }
                        )
                    }
                }
            }
        }

        OutdoorCard {
            Text("Global leaderboard", style = MaterialTheme.typography.titleMedium)
            Text(
                "Ranked by wins, then average score per match.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            RankedLeaderboardList(
                rows = globalLeaderboard,
                emptyMessage = "Complete a match to start the board."
            )
        }
    }
}
