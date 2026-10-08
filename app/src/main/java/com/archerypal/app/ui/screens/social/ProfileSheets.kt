package com.archerypal.app.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.archerypal.app.remote.PlayerProfile
import com.archerypal.app.ui.components.SecondaryActionButton

@Composable
fun ProfileEditorSheet(
    playerName: String,
    friendCode: String?,
    profile: PlayerProfile?,
    onDismiss: () -> Unit,
    onSetDiscoverable: (Boolean) -> Unit,
    onSave: (String, String, String, String, String) -> Unit,
    onClear: () -> Unit
) {
    var bio by rememberSaveable { mutableStateOf(profile?.bio.orEmpty()) }
    var club by rememberSaveable { mutableStateOf(profile?.club.orEmpty()) }
    var bowType by rememberSaveable { mutableStateOf(profile?.bowType.orEmpty()) }
    var homeRange by rememberSaveable { mutableStateOf(profile?.homeRange.orEmpty()) }
    var locationLabel by rememberSaveable { mutableStateOf(profile?.locationLabel.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your profile") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Optional details friends can view. Leave blank or clear anytime.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    AvatarCircle(playerName)
                    Column {
                        Text(playerName, style = MaterialTheme.typography.titleMedium)
                        friendCode?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = profile?.discoverable ?: true,
                            enabled = profile != null,
                            role = Role.Switch,
                            onValueChange = onSetDiscoverable
                        )
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Let others find me by name", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "When off, people can still add you with your Friend ID, QR code, or invite link.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Switch(
                        checked = profile?.discoverable ?: true,
                        onCheckedChange = null,
                        enabled = profile != null
                    )
                }
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Bio") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = club,
                    onValueChange = { club = it },
                    label = { Text("Club / team") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bowType,
                    onValueChange = { bowType = it },
                    label = { Text("Bow type") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = homeRange,
                    onValueChange = { homeRange = it },
                    label = { Text("Home range") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = locationLabel,
                    onValueChange = { locationLabel = it },
                    label = { Text("Location label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                SecondaryActionButton(
                    text = "Clear all details",
                    onClick = onClear,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(bio, club, bowType, homeRange, locationLabel) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun FriendProfileSheet(
    profile: PlayerProfile,
    onDismiss: () -> Unit,
    onChallenge: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                AvatarCircle(profile.displayName)
                Column {
                    Text(profile.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(profile.friendCode, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                profileField("Bio", profile.bio)
                profileField("Club", profile.club)
                profileField("Bow", profile.bowType)
                profileField("Home range", profile.homeRange)
                profileField("Location", profile.locationLabel)
                if (profile.bio.isBlank() && profile.club.isBlank() && profile.bowType.isBlank() &&
                    profile.homeRange.isBlank() && profile.locationLabel.isBlank()
                ) {
                    Text(
                        "No profile details yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onChallenge) { Text("Challenge") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun profileField(label: String, value: String) {
    if (value.isNotBlank()) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
