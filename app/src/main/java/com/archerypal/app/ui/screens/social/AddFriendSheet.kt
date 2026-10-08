package com.archerypal.app.ui.screens.social

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.archerypal.app.ui.components.PrimaryActionButton
import com.archerypal.app.ui.components.QrCodeImage
import com.archerypal.app.ui.components.QrScannerView
import com.archerypal.app.ui.components.SecondaryActionButton
import com.archerypal.app.ui.theme.TargetGold

enum class AddFriendMode { MENU, SCAN, MY_CODE, ENTER_ID }

private val FRIEND_CODE_PATTERN = Regex("^AP-[A-Z0-9]{6}$")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFriendSheet(
    friendCode: String?,
    profileShareLink: String?,
    initialMode: AddFriendMode = AddFriendMode.MENU,
    onDismiss: () -> Unit,
    onAddFriend: (String) -> Unit,
    onAddProfileLink: (String) -> Unit,
    onSearchByName: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mode by rememberSaveable { mutableStateOf(initialMode) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            when (mode) {
                AddFriendMode.MENU -> AddFriendMenu(
                    profileShareLink = profileShareLink,
                    onSelect = { mode = it },
                    onSearchByName = onSearchByName
                )
                AddFriendMode.SCAN -> {
                    Text("Scan a friend's code", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Ask them to open Social → Add friend → My code.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                    QrScannerView(
                        onQrScanned = { raw ->
                            val trimmed = raw.trim().uppercase()
                            if (FRIEND_CODE_PATTERN.matches(trimmed)) {
                                onAddFriend(trimmed)
                            } else {
                                onAddProfileLink(raw)
                            }
                            onDismiss()
                        },
                        previewHeight = 280.dp
                    )
                    SecondaryActionButton(text = "Back", onClick = { mode = AddFriendMode.MENU })
                }
                AddFriendMode.MY_CODE -> {
                    Text("My code", style = MaterialTheme.typography.titleLarge)
                    if (profileShareLink.isNullOrBlank()) {
                        Text(
                            "Connect to the server to get your Friend ID.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            QrCodeImage(content = profileShareLink, modifier = Modifier.size(220.dp))
                            friendCode?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TargetGold,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            }
                        }
                        ShareLinkButton(link = profileShareLink)
                    }
                    SecondaryActionButton(text = "Back", onClick = { mode = AddFriendMode.MENU })
                }
                AddFriendMode.ENTER_ID -> {
                    var code by rememberSaveable { mutableStateOf("") }
                    Text("Enter Friend ID", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.uppercase() },
                        label = { Text("Friend ID (AP-XXXXXX)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    PrimaryActionButton(
                        text = "Send request",
                        onClick = {
                            onAddFriend(code.trim())
                            onDismiss()
                        },
                        enabled = code.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    SecondaryActionButton(text = "Back", onClick = { mode = AddFriendMode.MENU })
                }
            }
        }
    }
}

@Composable
private fun AddFriendMenu(
    profileShareLink: String?,
    onSelect: (AddFriendMode) -> Unit,
    onSearchByName: () -> Unit
) {
    Text("Add friend", style = MaterialTheme.typography.titleLarge)
    PrimaryActionButton(
        text = "Search by name",
        onClick = onSearchByName,
        modifier = Modifier.fillMaxWidth()
    )
    SecondaryActionButton(
        text = "Scan QR code",
        onClick = { onSelect(AddFriendMode.SCAN) },
        modifier = Modifier.fillMaxWidth()
    )
    SecondaryActionButton(
        text = "Show my code",
        onClick = { onSelect(AddFriendMode.MY_CODE) },
        modifier = Modifier.fillMaxWidth()
    )
    if (!profileShareLink.isNullOrBlank()) {
        ShareLinkButton(link = profileShareLink, primary = false)
    }
    SecondaryActionButton(
        text = "Enter Friend ID",
        onClick = { onSelect(AddFriendMode.ENTER_ID) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ShareLinkButton(link: String, primary: Boolean = true) {
    val context = LocalContext.current
    if (primary) {
        PrimaryActionButton(
            text = "Share invite link",
            onClick = { shareLink(context, link) },
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        SecondaryActionButton(
            text = "Share invite link",
            onClick = { shareLink(context, link) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun shareLink(context: android.content.Context, link: String) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Add me on Archerypal: $link")
            },
            "Share invite link"
        )
    )
}
