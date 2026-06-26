package com.archerypal.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.archerypal.app.ui.components.OutdoorCard
import com.archerypal.app.ui.components.PrimaryActionButton
import com.archerypal.app.ui.components.QrCodeImage
import com.archerypal.app.ui.components.SecondaryActionButton

@Composable
fun HostScreen(
    qrContent: String,
    playerCount: Int,
    statusMessage: String,
    transportLabel: String,
    onContinue: () -> Unit,
    onLeave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Hosting", style = MaterialTheme.typography.headlineMedium)
        OutdoorCard {
            Text(statusMessage + transportLabel, style = MaterialTheme.typography.bodyLarge)
            Text("Connected archers: $playerCount", modifier = Modifier.padding(top = 8.dp))
        }
        QrCodeImage(qrContent)
        Text("Ask joiners to scan this code — nearby or libp2p relay over cell", color = MaterialTheme.colorScheme.onBackground.copy(0.8f))
        PrimaryActionButton("Set up targets", onContinue)
        SecondaryActionButton("Leave match", onLeave)
    }
}
