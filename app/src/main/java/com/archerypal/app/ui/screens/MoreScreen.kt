package com.archerypal.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.archerypal.app.billing.PlusFeatures
import com.archerypal.app.remote.maskEmail
import com.archerypal.app.ui.components.OutdoorCard
import com.archerypal.app.ui.components.PrimaryActionButton
import com.archerypal.app.ui.components.SecondaryActionButton
import com.archerypal.app.ui.layout.AdaptiveScreenColumn

@Composable
fun MoreScreen(
    friendCode: String?,
    serverReachable: Boolean?,
    removeAdsPrice: String?,
    plusPrice: String?,
    isAdFree: Boolean,
    isPlusActive: Boolean,
    billingReady: Boolean,
    googleAccountLinked: Boolean,
    googleAccountEmail: String?,
    accountActionInProgress: Boolean,
    onOpenPaywall: () -> Unit,
    onOpenCustomerCenter: () -> Unit,
    onRestorePurchases: () -> Unit,
    onJoinInvite: (String) -> Unit,
    onRetryServerConnection: () -> Unit,
    onLinkGoogleAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    AdaptiveScreenColumn(scroll = true, modifier = modifier) { _ ->
        Text("More", style = MaterialTheme.typography.headlineMedium)
        OutdoorCard(modifier = Modifier.padding(top = 8.dp)) {
            Text("Your profile", style = MaterialTheme.typography.titleMedium)
            if (!friendCode.isNullOrBlank()) {
                Text(
                    "Friend ID: $friendCode",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            val serverLabel = when (serverReachable) {
                true -> "Server connected"
                false -> "Server unreachable"
                null -> "Checking server…"
            }
            Text(
                serverLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp)
            )
            if (serverReachable == false) {
                SecondaryActionButton(
                    text = "Retry server",
                    onClick = onRetryServerConnection,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        OutdoorCard {
            Text("Google account", style = MaterialTheme.typography.titleMedium)
            if (googleAccountLinked) {
                Text(
                    "Signed in as ${maskEmail(googleAccountEmail)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    "Your Friend ID is backed up to this Google account. Sign in on a new phone to restore it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Text(
                    "Sign in to back up your Friend ID and use Social: friends, the feed, and shoots. Nearby scoring works offline without Google.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp)
                )
                PrimaryActionButton(
                    text = if (accountActionInProgress) "Connecting…" else "Sign in with Google",
                    onClick = onLinkGoogleAccount,
                    enabled = !accountActionInProgress,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
        OutdoorCard {
            Text("Archerypal Store", style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    if (isPlusActive) "Your Plus includes:" else "Plus adds:",
                    style = MaterialTheme.typography.bodyMedium
                )
                PlusFeatures.VALUE_POINTS.forEach { point ->
                    Text(
                        "• $point",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
                Text(
                    "Free always includes nearby scoring, friends, the feed, and shoot RSVPs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
                if (!isPlusActive) {
                    PrimaryActionButton(
                        text = when {
                            !billingReady -> "Connecting to store…"
                            plusPrice != null -> "Get Plus · from $plusPrice"
                            else -> "Get Plus"
                        },
                        onClick = onOpenPaywall,
                        enabled = billingReady
                    )
                    if (!isAdFree && removeAdsPrice != null) {
                        Text(
                            "Remove ads ($removeAdsPrice) is also available in the store offering when configured.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    Text("Plus is active", style = MaterialTheme.typography.bodyMedium)
                    SecondaryActionButton(
                        text = "Manage subscription",
                        onClick = onOpenCustomerCenter,
                        enabled = billingReady
                    )
                }
                SecondaryActionButton(
                    text = "Restore / refresh purchases",
                    onClick = onRestorePurchases,
                    enabled = billingReady
                )
            }
        }
    }
}
