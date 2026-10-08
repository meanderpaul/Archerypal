package com.archerypal.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.archerypal.app.ui.components.OutdoorCard
import com.archerypal.app.ui.components.PrimaryActionButton
import com.archerypal.app.ui.components.SecondaryActionButton
import com.archerypal.app.ui.layout.AdaptiveScreenColumn

@Composable
fun GoogleSignInScreen(
    playerName: String,
    onNameChange: (String) -> Unit,
    inProgress: Boolean,
    onSignInWithGoogle: () -> Unit,
    modifier: Modifier = Modifier,
    headline: String = "Archerypal",
    subtitle: String = "Sign in for Social, remote matches, and account backup",
    onDismiss: (() -> Unit)? = null
) {
    AdaptiveScreenColumn(
        scroll = true,
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) { _ ->
        Text(
            headline,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )
        OutdoorCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Nearby scoring works without Google. Sign in to use Social, remote invites, and restore your Friend ID on a new phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            OutlinedTextField(
                value = playerName,
                onValueChange = onNameChange,
                label = { Text("Display name") },
                singleLine = true,
                enabled = !inProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            )
            PrimaryActionButton(
                text = if (inProgress) "Signing in…" else "Continue with Google",
                onClick = onSignInWithGoogle,
                enabled = !inProgress && playerName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            )
            if (onDismiss != null) {
                SecondaryActionButton(
                    text = "Not now",
                    onClick = onDismiss,
                    enabled = !inProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(
                "Returning archers: sign in to restore your existing Friend ID.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
            Text(
                "New archers: we will create a Friend ID and link it to Google.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
        }
    }
}
