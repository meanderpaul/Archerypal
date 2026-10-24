package com.archerypal.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun InviteLinkJoinCard(
    onJoin: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Got an invite?",
    hint: String = "Paste a challenge link or type the 8-character room code",
    buttonLabel: String = "Join match"
) {
    var inviteText by rememberSaveable { mutableStateOf("") }

    OutdoorCard(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        OutlinedTextField(
            value = inviteText,
            onValueChange = { inviteText = it },
            label = { Text("Link or room code") },
            singleLine = false,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        PrimaryActionButton(
            text = buttonLabel,
            onClick = {
                onJoin(inviteText.trim())
                inviteText = ""
            },
            enabled = inviteText.isNotBlank(),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
