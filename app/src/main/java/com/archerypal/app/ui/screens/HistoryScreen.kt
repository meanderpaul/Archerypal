package com.archerypal.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.archerypal.app.data.PlayerGlobalStats
import com.archerypal.app.data.SavedMatchRecord
import com.archerypal.app.data.formatMatchDate
import com.archerypal.app.data.parseScoringType
import com.archerypal.app.ui.components.OutdoorCard
import com.archerypal.app.ui.components.PrimaryActionButton
import com.archerypal.app.ui.components.SecondaryActionButton
import com.archerypal.app.ui.layout.AdaptiveScreenColumn
import com.archerypal.app.ui.layout.rememberScreenMetrics
import com.archerypal.app.ui.theme.TargetGold

@Composable
fun HistoryScreen(
    playerName: String,
    playerStats: PlayerGlobalStats?,
    matchHistory: List<SavedMatchRecord>,
    onRematch: () -> Unit,
    modifier: Modifier = Modifier
) {
    AdaptiveScreenColumn(scroll = false, modifier = modifier) { _ ->
        val metrics = rememberScreenMetrics()
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = metrics.bottomNavContentGap),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                Text("History", style = MaterialTheme.typography.headlineMedium)
            }
            item {
                StatsSummaryCard(playerName = playerName, stats = playerStats)
            }
            if (matchHistory.isEmpty()) {
                item {
                    OutdoorCard {
                        Text(
                            "Completed matches appear here with full score breakdowns.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                    }
                }
            } else {
                items(matchHistory, key = { it.matchId }) { record ->
                    MatchHistoryCard(
                        record = record,
                        showRematch = record == matchHistory.firstOrNull(),
                        onRematch = onRematch
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsSummaryCard(
    playerName: String,
    stats: PlayerGlobalStats?
) {
    OutdoorCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = TargetGold)
            Text("$playerName — your stats", style = MaterialTheme.typography.titleMedium)
        }
        if (stats == null || stats.matchesPlayed == 0) {
            Text(
                "Complete a match to start tracking wins and averages.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            Text(
                "${stats.matchesWon} wins · ${stats.matchesPlayed} matches · " +
                    String.format("%.1f", stats.averagePointsPerMatch) + " avg",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "${stats.totalPoints} total points on this device",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun MatchHistoryCard(
    record: SavedMatchRecord,
    showRematch: Boolean,
    onRematch: () -> Unit
) {
    var expanded by rememberSaveable(record.matchId) { mutableStateOf(false) }
    val scoring = parseScoringType(record.scoringType)
    val remoteLabel = if (record.isRemote) "Remote" else "Local"

    OutdoorCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(formatMatchDate(record.completedAt), style = MaterialTheme.typography.labelMedium)
                Text(
                    "Winner: ${record.winnerName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    "$remoteLabel · ${record.targetCount} targets · ${scoring.label} · ${record.playerNames.size} archers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                record.finalScores.entries
                    .sortedByDescending { it.value }
                    .forEach { (name, score) ->
                        Text("$name — $score pts", style = MaterialTheme.typography.bodyLarge)
                    }
            }
        }
        if (showRematch) {
            PrimaryActionButton(
                text = "Rematch this group",
                onClick = onRematch,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}
