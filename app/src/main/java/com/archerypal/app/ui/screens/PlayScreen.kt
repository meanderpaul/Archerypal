package com.archerypal.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.archerypal.app.data.SavedFriend
import com.archerypal.app.ui.components.InviteLinkJoinCard
import com.archerypal.app.ui.components.OutdoorCard
import com.archerypal.app.ui.components.PrimaryActionButton
import com.archerypal.app.ui.components.SecondaryActionButton
import com.archerypal.app.ui.layout.AdaptiveScreenColumn
import com.archerypal.app.ui.theme.FieldGreen

@Composable
fun PlayScreen(
    playerName: String,
    onNameChange: (String) -> Unit,
    isPlusActive: Boolean,
    googleAccountLinked: Boolean,
    serverReachable: Boolean?,
    savedFriends: List<SavedFriend>,
    remoteFriendCodes: Set<String>,
    onHost: () -> Unit,
    onJoin: () -> Unit,
    onResyncJoin: (String) -> Unit,
    onAddFriend: (String) -> Unit,
    onJoinInvite: (String) -> Unit,
    onRequestSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = playerName.isNotBlank()

    AdaptiveScreenColumn(scroll = true, modifier = modifier) { metrics ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (metrics.isExpandedWidth) {
                        Modifier
                            .widthIn(max = 720.dp)
                            .align(Alignment.CenterHorizontally)
                    } else {
                        Modifier
                    }
                ),
            verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing)
        ) {
            Text(
                if (isPlusActive) "Shoot at the range" else "Shoot",
                style = MaterialTheme.typography.headlineMedium
            )
            if (isPlusActive) {
                Text(
                    "Score nearby with any archer — Plus not required for guests.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            OutlinedTextField(
                value = playerName,
                onValueChange = onNameChange,
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PrimaryActionButton(
                    text = "Host",
                    onClick = onHost,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
                SecondaryActionButton(
                    text = "Join",
                    onClick = onJoin,
                    modifier = Modifier.weight(1f)
                )
            }
            RecentArchersRow(
                savedFriends = savedFriends,
                remoteFriendCodes = remoteFriendCodes,
                onResyncJoin = onResyncJoin,
                onAddFriend = onAddFriend
            )
            when {
                !googleAccountLinked -> {
                    OutdoorCard {
                        Text("Remote invite", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Sign in with Google to join remote challenge links. Nearby Host and Join work offline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )
                        PrimaryActionButton(
                            text = "Sign in with Google",
                            onClick = onRequestSignIn
                        )
                    }
                }
                serverReachable == false -> {
                    OutdoorCard {
                        Text("Remote invite", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Server unreachable — remote invites are unavailable. Nearby Host and Join still work.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                else -> InviteLinkJoinCard(onJoin = onJoinInvite)
            }
        }
    }
}

@Composable
private fun RecentArchersRow(
    savedFriends: List<SavedFriend>,
    remoteFriendCodes: Set<String>,
    onResyncJoin: (String) -> Unit,
    onAddFriend: (String) -> Unit
) {
    OutdoorCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Outlined.Groups, contentDescription = null, tint = FieldGreen)
            Text("Recent archers", style = MaterialTheme.typography.titleMedium)
        }
        if (savedFriends.isEmpty()) {
            Text(
                "Archers you have played with show up here — tap to reconnect.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            LazyRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(savedFriends.take(12), key = { it.name }) { friend ->
                    val canAdd = friend.friendCode.isNotBlank() &&
                        !remoteFriendCodes.contains(friend.friendCode.uppercase())
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AssistChip(
                            onClick = { onResyncJoin(friend.name) },
                            label = { Text(friend.name) }
                        )
                        if (canAdd) {
                            SecondaryActionButton(
                                text = "Add friend",
                                onClick = { onAddFriend(friend.friendCode) },
                                fillWidth = false,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
