package com.archerypal.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.archerypal.app.data.LeaderboardRow
import com.archerypal.app.data.ScoringType
import com.archerypal.app.ui.components.OutdoorCard
import com.archerypal.app.ui.components.PrimaryActionButton
import com.archerypal.app.ui.components.RankedLeaderboardList
import com.archerypal.app.ui.components.SecondaryActionButton
import com.archerypal.app.ui.layout.rememberScreenMetrics
import com.archerypal.app.ui.theme.TargetGold
import com.archerypal.app.viewmodel.FriendSuggestion

@Composable
fun MatchResultsScreen(
    playerName: String,
    scoringType: ScoringType,
    targetCount: Int,
    playerTargetScores: Map<Int, Int>,
    playerRunningTotal: Int,
    matchLeaderboard: List<LeaderboardRow>,
    friendSuggestions: List<FriendSuggestion>,
    requestedFriendCodes: Set<String>,
    onRequestFriends: (List<String>) -> Unit,
    onBackHome: () -> Unit
) {
    val metrics = rememberScreenMetrics()
    val safeTargetCount = targetCount.coerceAtLeast(1)
    val playerRow = matchLeaderboard.find { it.name == playerName }
    val rankLabel = playerRow?.let { "#${it.rank}" } ?: "—"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = metrics.horizontalPadding,
                vertical = metrics.verticalPadding
            ),
        verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing)
    ) {
        Text(
            "Match complete",
            style = if (metrics.isCompactHeight) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.headlineSmall
            }
        )
        Text(
            "$playerName · $playerRunningTotal pts · Rank $rankLabel · ${scoringType.label}",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing)
        ) {
            OutdoorCard {
                Text("Final standings", style = MaterialTheme.typography.titleMedium)
                RankedLeaderboardList(rows = matchLeaderboard)
            }
            if (friendSuggestions.isNotEmpty()) {
                ShotWithCard(
                    suggestions = friendSuggestions,
                    requestedFriendCodes = requestedFriendCodes,
                    onRequestFriends = onRequestFriends
                )
            }
            if (playerTargetScores.isNotEmpty()) {
                OutdoorCard {
                    Text("Your scorecard", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items((1..safeTargetCount).toList()) { target ->
                            val score = playerTargetScores[target]
                            FilterChip(
                                selected = false,
                                onClick = {},
                                enabled = false,
                                label = {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("T$target", style = MaterialTheme.typography.labelMedium)
                                        Text(
                                            text = score?.toString() ?: "—",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (score != null) {
                                                TargetGold
                                            } else {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        PrimaryActionButton("Back home", onBackHome)
    }
}

@Composable
private fun ShotWithCard(
    suggestions: List<FriendSuggestion>,
    requestedFriendCodes: Set<String>,
    onRequestFriends: (List<String>) -> Unit
) {
    val pending = suggestions.filterNot { it.friendCode.uppercase() in requestedFriendCodes }
    OutdoorCard {
        Text("Archers you shot with", style = MaterialTheme.typography.titleMedium)
        Text(
            "Add them as friends to see their shoots and compare on the friends board.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { suggestion ->
                val requested = suggestion.friendCode.uppercase() in requestedFriendCodes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        suggestion.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (requested) {
                        Text(
                            "Requested",
                            maxLines = 1,
                            softWrap = false,
                            style = MaterialTheme.typography.labelLarge,
                            color = TargetGold
                        )
                    } else {
                        SecondaryActionButton(
                            text = "Request",
                            onClick = { onRequestFriends(listOf(suggestion.friendCode)) },
                            fillWidth = false,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
        if (pending.size > 1) {
            PrimaryActionButton(
                text = "Add all (${pending.size})",
                onClick = { onRequestFriends(pending.map { it.friendCode }) },
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}
