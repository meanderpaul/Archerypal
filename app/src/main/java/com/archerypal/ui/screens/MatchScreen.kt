package com.archerypal.ui.screens

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.archerypal.data.LeaderboardRow
import com.archerypal.data.MatchPhase
import com.archerypal.ui.components.OutdoorCard
import com.archerypal.ui.components.PrimaryActionButton
import com.archerypal.ui.components.RankedLeaderboardList
import com.archerypal.ui.components.ScorePad
import com.archerypal.ui.components.SecondaryActionButton

@Composable
fun MatchScreen(
    targetCount: Int,
    selectedTarget: Int,
    pendingScore: String,
    statusMessage: String,
    phase: MatchPhase,
    isHost: Boolean,
    matchLeaderboard: List<LeaderboardRow>,
    onTargetSelected: (Int) -> Unit,
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
                    OutdoorCard {
                        Text("Target", style = MaterialTheme.typography.titleMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items((1..targetCount.coerceAtLeast(1)).toList()) { target ->
                                FilterChip(
                                    selected = selectedTarget == target,
                                    onClick = { onTargetSelected(target) },
                                    label = { Text(target.toString()) }
                                )
                            }
                        }
                    }
                    OutdoorCard {
                        ScorePad(
                            pendingScore = pendingScore,
                            onDigit = onDigit,
                            onClear = onClear,
                            onSubmit = onSubmit
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
