package com.archerypal.app.ui.screens.social

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.archerypal.app.data.formatDistanceAway
import com.archerypal.app.data.formatRelativeTime as formatRelativeTimeCore
import com.archerypal.app.data.formatShootDay as formatShootDayCore
import com.archerypal.app.data.formatShootWhenRange as formatShootWhenRangeCore
import com.archerypal.app.data.parseLocalDateToMillis
import com.archerypal.app.remote.RemoteShoot
import com.archerypal.app.remote.ShootAttendee
import com.archerypal.app.ui.components.PrimaryActionButton
import com.archerypal.app.ui.components.SecondaryActionButton
import com.archerypal.app.ui.theme.FieldGreen
import com.archerypal.app.ui.theme.ForestGreen
import com.archerypal.app.ui.theme.TargetGold

@Composable
fun AvatarCircle(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(FieldGreen, ForestGreen))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
fun ShootDetailSheet(
    shoot: RemoteShoot,
    attendees: List<ShootAttendee>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onToggleRsvp: () -> Unit,
    onGoToPlay: () -> Unit,
    myFriendCode: String? = null,
    requestedFriendCodes: Set<String> = emptySet(),
    onRequestFriend: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val past = shoot.isPast()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(shoot.title)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    if (shoot.eventType.isNotBlank()) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(shoot.eventType.uppercase()) }
                        )
                    }
                    if (past) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("ENDED") }
                        )
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    formatShootWhenRange(shoot.startsAt, shoot.endsAt),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(shoot.locationName, style = MaterialTheme.typography.bodyLarge)
                shoot.distanceKm?.let { dist ->
                    Text(
                        formatDistanceAway(dist),
                        style = MaterialTheme.typography.bodySmall,
                        color = TargetGold
                    )
                }
                if (shoot.region.isNotBlank()) {
                    Text("Region: ${shoot.region}", style = MaterialTheme.typography.bodySmall)
                }
                if (!shoot.hostDisplayName.isNullOrBlank()) {
                    Text("Hosted by ${shoot.hostDisplayName}", style = MaterialTheme.typography.bodyMedium)
                }
                if (shoot.description.isNotBlank()) {
                    Text(shoot.description, style = MaterialTheme.typography.bodyMedium)
                }
                if (shoot.friendsGoingCount > 0) {
                    Text(
                        "${shoot.friendsGoingCount} friend${if (shoot.friendsGoingCount == 1) "" else "s"} " +
                            if (past) "went" else "going",
                        style = MaterialTheme.typography.labelLarge,
                        color = TargetGold
                    )
                }
                Text(
                    if (past) "Who went (${attendees.size})" else "Who's going (${attendees.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else if (attendees.isEmpty()) {
                    Text(
                        if (past) "No RSVPs were recorded." else "No RSVPs yet — be the first to join.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                } else {
                    attendees.take(8).forEach { attendee ->
                        val isMe = attendee.friendCode.equals(myFriendCode, ignoreCase = true)
                        val requested = attendee.friendCode.uppercase() in requestedFriendCodes
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val suffix = when {
                                isMe -> " (you)"
                                attendee.isFriend -> " (friend)"
                                else -> ""
                            }
                            Text(
                                "• ${attendee.displayName}$suffix",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            when {
                                isMe || attendee.isFriend || onRequestFriend == null -> Unit
                                requested -> Text(
                                    "Requested",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TargetGold
                                )
                                else -> TextButton(onClick = { onRequestFriend(attendee.friendCode) }) {
                                    Text("Add")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column {
                if (!past) {
                    PrimaryActionButton(
                        text = if (shoot.viewerIsGoing) "Going — tap to cancel" else "Join this shoot",
                        onClick = onToggleRsvp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                val regUrl = shoot.registrationUrl.ifBlank { shoot.sourceUrl }
                if (regUrl.isNotBlank()) {
                    SecondaryActionButton(
                        text = if (shoot.registrationUrl.isNotBlank()) "Register on club site" else "Learn more",
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(regUrl)))
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = if (past) 0.dp else 8.dp)
                    )
                }
                SecondaryActionButton(
                    text = "Get directions",
                    onClick = {
                        val query = Uri.encode(shoot.locationName)
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$query")))
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        },
        dismissButton = {
            Row {
                if (!past) {
                    TextButton(onClick = onGoToPlay) { Text("At the range?") }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@Composable
fun CreateShootDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, Long, Long?, Boolean) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var dateText by rememberSaveable { mutableStateOf("") }
    var listPublicly by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create shoot") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = region,
                    onValueChange = { region = it },
                    label = { Text("Region (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                FilterChip(
                    selected = listPublicly,
                    onClick = { listPublicly = !listPublicly },
                    label = { Text("List publicly for everyone") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val startsAt = parseLocalDateToMillis(dateText) ?: return@TextButton
                    onCreate(title, location, region, description, startsAt, null, listPublicly)
                },
                enabled = title.isNotBlank() && location.isNotBlank() && parseLocalDateToMillis(dateText) != null
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun formatRelativeTime(createdAtMs: Long): String = formatRelativeTimeCore(createdAtMs)

fun formatShootWhenRange(startsAtMs: Long, endsAtMs: Long?): String =
    formatShootWhenRangeCore(startsAtMs, endsAtMs)

fun formatShootDay(startsAtMs: Long): String = formatShootDayCore(startsAtMs)

/** End of the event window; single-day shoots use start time. */
fun shootEffectiveEndMs(startsAt: Long, endsAt: Long?): Long =
    endsAt?.takeIf { it > startsAt } ?: startsAt

fun RemoteShoot.isPast(nowMs: Long = System.currentTimeMillis()): Boolean =
    status.equals("cancelled", ignoreCase = true) ||
        shootEffectiveEndMs(startsAt, endsAt) < nowMs

fun parseDateToMillis(raw: String): Long? = parseLocalDateToMillis(raw)
