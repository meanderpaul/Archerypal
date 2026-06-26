package com.archerypal.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.archerypal.app.data.LeaderboardRow
import com.archerypal.app.data.MatchPhase
import com.archerypal.app.data.ScoringType
import com.archerypal.app.ui.components.OutdoorCard
import com.archerypal.app.ui.components.PrimaryActionButton
import com.archerypal.app.ui.components.RankedLeaderboardList
import com.archerypal.app.ui.components.ScorePad
import com.archerypal.app.ui.components.SecondaryActionButton
import com.archerypal.app.ui.theme.TargetGold

@Composable
fun MatchScreen(
    scoringType: ScoringType,
    targetCount: Int,
    selectedTarget: Int,
    pendingScore: String,
    playerTargetScores: Map<Int, Int>,
    playerRunningTotal: Int,
    statusMessage: String,
    phase: MatchPhase,
    isHost: Boolean,
    matchLeaderboard: List<LeaderboardRow>,
    onTargetSelected: (Int) -> Unit,
    onPresetScore: (Int) -> Unit,
    onDigit: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onFinishMatch: () -> Unit,
    onLeave: () -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Score", "Leaderboard")
    val scoringDone = phase == MatchPhase.FINISHED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            if (scoringDone) "Match complete" else "Match in progress",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(statusMessage, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
        TabRow(selectedTabIndex = tabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = tabIndex == index,
                    onClick = { tabIndex = index },
                    text = { Text(title) }
                )
            }
        }
        when (tabIndex) {
            0 -> {
                if (!scoringDone) {
                    val existingScore = playerTargetScores[selectedTarget]
                    val isEditing = existingScore != null
                    OutdoorCard {
                        Text("Your scores", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Target $selectedTarget of ${targetCount.coerceAtLeast(1)} · ${scoringType.label} · Total: $playerRunningTotal",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items((1..targetCount.coerceAtLeast(1)).toList()) { target ->
                                val score = playerTargetScores[target]
                                FilterChip(
                                    selected = selectedTarget == target,
                                    onClick = { onTargetSelected(target) },
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
                        Text(
                            if (isEditing) {
                                "Tap another target to edit its score"
                            } else {
                                "Tap a logged target to edit · scores advance automatically"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                    }
                    OutdoorCard {
                        Text(
                            if (isEditing) "Editing target $selectedTarget" else "Scoring target $selectedTarget",
                            style = MaterialTheme.typography.titleMedium
                        )
                        ScorePad(
                            scoringType = scoringType,
                            pendingScore = pendingScore,
                            onPresetScore = onPresetScore,
                            onDigit = onDigit,
                            onClear = onClear,
                            onSubmit = onSubmit,
                            submitLabel = if (isEditing) "Update score" else "Submit score"
                        )
                    }
                } else {
                    OutdoorCard {
                        Text("Final standings", style = MaterialTheme.typography.titleMedium)
                        RankedLeaderboardList(rows = matchLeaderboard)
                    }
                }
            }
            1 -> OutdoorCard {
                RankedLeaderboardList(rows = matchLeaderboard)
            }
        }
        if (isHost && !scoringDone) {
            PrimaryActionButton("Finish & save match", onFinishMatch)
        }
        SecondaryActionButton(if (scoringDone) "Back home" else "Leave match", onLeave)
    }
}
