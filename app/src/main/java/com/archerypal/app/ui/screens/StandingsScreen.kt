package com.archerypal.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.archerypal.app.data.LeaderboardRow
import com.archerypal.app.ui.components.OutdoorCard
import com.archerypal.app.ui.components.PrimaryActionButton
import com.archerypal.app.ui.components.RankedLeaderboardList
import com.archerypal.app.ui.components.SecondaryActionButton
import com.archerypal.app.ui.layout.AdaptiveScreenColumn
import com.archerypal.app.viewmodel.StandingsMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StandingsScreen(
    isPlusActive: Boolean,
    standingsMode: StandingsMode,
    localLeaderboard: List<LeaderboardRow>,
    onlineGlobalLeaderboard: List<LeaderboardRow>,
    onlineFriendsLeaderboard: List<LeaderboardRow>,
    socialLoading: Boolean,
    myFriendCode: String?,
    remoteFriendCodes: Set<String>,
    onStandingsModeChange: (StandingsMode) -> Unit,
    onRefresh: () -> Unit,
    onSubscribePlus: () -> Unit,
    onRequestFriend: (String) -> Unit,
    onChallengeFriend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedRow by remember { mutableStateOf<LeaderboardRow?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    AdaptiveScreenColumn(scroll = true, modifier = modifier) { metrics ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Standings",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (socialLoading) {
                CircularProgressIndicator()
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = standingsMode == StandingsMode.GLOBAL,
                onClick = { onStandingsModeChange(StandingsMode.GLOBAL) },
                label = { Text(if (isPlusActive) "Global" else "Global (Plus)", maxLines = 1, softWrap = false) }
            )
            FilterChip(
                selected = standingsMode == StandingsMode.FRIENDS,
                onClick = { onStandingsModeChange(StandingsMode.FRIENDS) },
                label = { Text("Friends", maxLines = 1, softWrap = false) }
            )
            FilterChip(
                selected = standingsMode == StandingsMode.LOCAL,
                onClick = { onStandingsModeChange(StandingsMode.LOCAL) },
                label = { Text("Device", maxLines = 1, softWrap = false) }
            )
        }
        SecondaryActionButton(
            text = "Refresh",
            onClick = onRefresh,
            modifier = Modifier.padding(top = 4.dp)
        )

        OutdoorCard {
            when {
                !isPlusActive && standingsMode == StandingsMode.GLOBAL -> {
                    Text("Online — global", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "See how you rank against every Archerypal archer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    SecondaryActionButton(
                        text = "Unlock global standings with Plus",
                        onClick = onSubscribePlus,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                standingsMode == StandingsMode.GLOBAL -> {
                    Text("Online — global", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tap an archer to add or challenge",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    RankedLeaderboardList(
                        rows = onlineGlobalLeaderboard,
                        emptyMessage = "No online scores yet. Complete remote matches to rank up.",
                        onRowClick = { row ->
                            if (!row.friendCode.isNullOrBlank() &&
                                !row.friendCode.equals(myFriendCode, ignoreCase = true)
                            ) {
                                selectedRow = row
                            }
                        }
                    )
                }
                standingsMode == StandingsMode.FRIENDS -> {
                    Text("Online — friends", style = MaterialTheme.typography.titleMedium)
                    RankedLeaderboardList(
                        rows = onlineFriendsLeaderboard,
                        emptyMessage = "Add friends from the Social tab to fill this board.",
                        onRowClick = { row ->
                            if (!row.friendCode.isNullOrBlank() &&
                                !row.friendCode.equals(myFriendCode, ignoreCase = true)
                            ) {
                                selectedRow = row
                            }
                        }
                    )
                }
                else -> {
                    Text("On this device", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Top archers from matches scored on this phone",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    RankedLeaderboardList(
                        rows = localLeaderboard,
                        emptyMessage = "Complete a match to start the board."
                    )
                }
            }
        }
    }

    selectedRow?.let { row ->
        val code = row.friendCode ?: return@let
        val alreadyFriend = remoteFriendCodes.contains(code.uppercase())
        ModalBottomSheet(
            onDismissRequest = { selectedRow = null },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(row.name, style = MaterialTheme.typography.titleLarge)
                Text(code, style = MaterialTheme.typography.bodyMedium)
                if (!alreadyFriend) {
                    PrimaryActionButton(
                        text = "Send friend request",
                        onClick = {
                            onRequestFriend(code)
                            selectedRow = null
                        }
                    )
                }
                SecondaryActionButton(
                    text = "Challenge",
                    onClick = {
                        onChallengeFriend(code)
                        selectedRow = null
                    }
                )
            }
        }
    }
}
