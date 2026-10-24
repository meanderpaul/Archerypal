package com.archerypal.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.archerypal.app.data.AppConstants
import com.archerypal.app.data.PlayerInfo
import com.archerypal.app.ui.components.OutdoorCard
import com.archerypal.app.ui.components.PrimaryActionButton
import com.archerypal.app.ui.components.QrCodeImage
import com.archerypal.app.ui.components.SecondaryActionButton
import com.archerypal.app.ui.layout.AdaptiveScreenColumn

@Composable
fun HostScreen(
    qrContent: String,
    players: List<PlayerInfo>,
    connectedCount: Int,
    statusMessage: String,
    isRemoteMatch: Boolean = false,
    remoteRoomId: String? = null,
    remoteInviteLink: String? = null,
    remoteOpponentName: String? = null,
    remoteOpponentNotified: Boolean = false,
    hostName: String = "",
    onContinue: () -> Unit,
    onLeave: () -> Unit
) {
    val joinerCount = players.count { !it.isHost }
    val hostPlayer = players.firstOrNull { it.isHost }
    val context = LocalContext.current
    val onShare: (String) -> Unit = { message ->
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        context.startActivity(Intent.createChooser(share, "Share invite"))
    }
    val onCopy: (String) -> Unit = { link ->
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Archerypal invite", link))
        Toast.makeText(context, "Invite link copied", Toast.LENGTH_SHORT).show()
    }
    val shareCard: @Composable (Dp) -> Unit = { qrCodeSize ->
        HostShareOrQr(
            isRemoteMatch = isRemoteMatch,
            remoteInviteLink = remoteInviteLink,
            remoteRoomId = remoteRoomId,
            opponentName = remoteOpponentName,
            opponentNotified = remoteOpponentNotified,
            hostName = hostName,
            qrContent = qrContent,
            qrCodeSize = qrCodeSize,
            onShare = onShare,
            onCopy = onCopy
        )
    }

    AdaptiveScreenColumn(
        scroll = true,
        horizontalAlignment = Alignment.CenterHorizontally
    ) { metrics ->
        Text("Hosting", style = MaterialTheme.typography.headlineMedium)

        if (metrics.isExpandedWidth) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metrics.sectionSpacing)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing)
                ) {
                    shareCard(metrics.qrCodeSize)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing)
                ) {
                    HostStatusCard(
                        isRemoteMatch = isRemoteMatch,
                        statusMessage = statusMessage,
                        joinerCount = joinerCount,
                        connectedCount = connectedCount
                    )
                    HostLobbyCard(hostPlayer = hostPlayer, joinerCount = joinerCount, players = players)
                }
            }
        } else {
            HostStatusCard(
                isRemoteMatch = isRemoteMatch,
                statusMessage = statusMessage,
                joinerCount = joinerCount,
                connectedCount = connectedCount
            )
            HostLobbyCard(hostPlayer = hostPlayer, joinerCount = joinerCount, players = players)
            shareCard(metrics.qrCodeSize)
        }

        PrimaryActionButton("Set up targets", onContinue)
        Text(
            "Continue to match setup — you can start with or without other archers",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        SecondaryActionButton("Leave match", onLeave)
    }
}

@Composable
private fun HostStatusCard(
    isRemoteMatch: Boolean,
    statusMessage: String,
    joinerCount: Int,
    connectedCount: Int
) {
    OutdoorCard {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Text(
                text = if (isRemoteMatch) "Remote match" else "Advertising",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Text(statusMessage, style = MaterialTheme.typography.bodyLarge)
        Text(
            "$joinerCount / ${AppConstants.MAX_PLAYERS - 1} archers connected",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.titleMedium
        )
        if (connectedCount > joinerCount) {
            Text(
                "$connectedCount device(s) connected — waiting for archer names…",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
        Text(
            "Up to ${AppConstants.MAX_PLAYERS} archers per match",
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun HostLobbyCard(
    hostPlayer: PlayerInfo?,
    joinerCount: Int,
    players: List<PlayerInfo>
) {
    OutdoorCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Lobby", style = MaterialTheme.typography.titleMedium)
            hostPlayer?.let {
                Text("You (host) — ${it.name}", style = MaterialTheme.typography.bodyLarge)
            }
            if (joinerCount == 0) {
                Text(
                    "No other archers yet — you can start solo or wait for joiners",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            } else {
                players.filter { !it.isHost }.forEach { player ->
                    Text("✓ ${player.name}", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun HostShareOrQr(
    isRemoteMatch: Boolean,
    remoteInviteLink: String?,
    remoteRoomId: String?,
    opponentName: String?,
    opponentNotified: Boolean,
    hostName: String,
    qrContent: String,
    qrCodeSize: Dp,
    onShare: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    if (isRemoteMatch && !remoteInviteLink.isNullOrBlank()) {
        OutdoorCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                if (opponentName != null) "Challenge $opponentName" else "Share invite",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                when {
                    opponentName != null && opponentNotified ->
                        "$opponentName got a notification and will see this challenge on their Play tab. " +
                            "You can also send the link yourself."
                    opponentName != null ->
                        "$opponentName will see this challenge on their Play tab. Send the link to give them a nudge."
                    else -> "Send this link — your friend taps to join in Archerypal"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            if (!remoteRoomId.isNullOrBlank()) {
                Text("Room code", style = MaterialTheme.typography.labelMedium)
                Text(
                    remoteRoomId,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryActionButton(
                    text = "Share link",
                    onClick = {
                        onShare("${hostName.ifBlank { "Your friend" }} challenged you to an Archerypal match! $remoteInviteLink")
                    },
                    modifier = Modifier.weight(1f)
                )
                SecondaryActionButton(
                    text = "Copy link",
                    onClick = { onCopy(remoteInviteLink) },
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                remoteInviteLink,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    } else if (!isRemoteMatch) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            QrCodeImage(content = qrContent, size = qrCodeSize)
            Text(
                "Ask joiners to scan this code, or pick you from Nearby hosts",
                color = MaterialTheme.colorScheme.onBackground.copy(0.8f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
