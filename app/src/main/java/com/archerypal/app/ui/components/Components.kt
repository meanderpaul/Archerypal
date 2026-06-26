package com.archerypal.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.archerypal.app.data.LeaderboardRow
import com.archerypal.app.data.ScoringType
import com.archerypal.app.data.Trophy
import com.archerypal.app.ui.theme.CardSurface
import com.archerypal.app.ui.theme.FieldGreen
import com.archerypal.app.ui.theme.TargetGold
import com.archerypal.app.ui.theme.TextSecondary
import com.archerypal.app.ui.theme.TrophyBronze
import com.archerypal.app.ui.theme.TrophySilver

@Composable
fun OutdoorCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = { content() })
    }
}

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = FieldGreen)
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun ScorePad(
    scoringType: ScoringType,
    pendingScore: String,
    onPresetScore: (Int) -> Unit,
    onDigit: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    submitLabel: String = "Submit score"
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = if (pendingScore.isBlank()) "—" else pendingScore,
            style = MaterialTheme.typography.displayMedium,
            color = TargetGold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        when (scoringType) {
            ScoringType.FREEFORM -> FreeformScorePad(
                pendingScore = pendingScore,
                onDigit = onDigit,
                onClear = onClear
            )
            else -> PresetScorePad(
                scores = scoringType.allowedScores.orEmpty(),
                pendingScore = pendingScore,
                onPresetScore = onPresetScore,
                onClear = onClear
            )
        }
        PrimaryActionButton(submitLabel, onSubmit, enabled = pendingScore.isNotBlank())
    }
}

@Composable
private fun PresetScorePad(
    scores: List<Int>,
    pendingScore: String,
    onPresetScore: (Int) -> Unit,
    onClear: () -> Unit
) {
    val rows = scores.chunked(3)
    rows.forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { score ->
                val selected = pendingScore == score.toString()
                Button(
                    onClick = { onPresetScore(score) },
                    modifier = Modifier.weight(1f),
                    colors = if (selected) {
                        ButtonDefaults.buttonColors(containerColor = TargetGold)
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Text(score.toString(), fontWeight = FontWeight.Bold)
                }
            }
            repeat(3 - row.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
    OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
        Text("Clear", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FreeformScorePad(
    pendingScore: String,
    onDigit: (String) -> Unit,
    onClear: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("C", "0")
    )
    rows.forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { label ->
                Button(
                    onClick = {
                        when (label) {
                            "C" -> onClear()
                            else -> onDigit(label)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(label, fontWeight = FontWeight.Bold)
                }
            }
            if (row.size < 3) {
                repeat(3 - row.size) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun LeaderboardList(entries: List<Pair<String, Int>>) {
    RankedLeaderboardList(
        rows = entries.mapIndexed { index, (name, total) ->
            LeaderboardRow(
                rank = index + 1,
                name = name,
                primaryLabel = "$total pts",
                trophy = com.archerypal.app.data.trophyForRank(index + 1)
            )
        }
    )
}

@Composable
fun RankedLeaderboardList(
    rows: List<LeaderboardRow>,
    emptyMessage: String = "No scores yet"
) {
    if (rows.isEmpty()) {
        Text(emptyMessage, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        return
    }
    rows.forEach { row ->
        LeaderboardRowItem(row)
    }
}

@Composable
private fun LeaderboardRowItem(row: LeaderboardRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = trophyEmoji(row.trophy) ?: "${row.rank}.",
                style = MaterialTheme.typography.titleLarge,
                color = trophyColor(row.trophy) ?: TextSecondary
            )
            Column {
                Text(row.name, style = MaterialTheme.typography.titleMedium)
                row.secondaryLabel?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
        Text(
            row.primaryLabel,
            style = MaterialTheme.typography.titleMedium,
            color = TargetGold,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun trophyEmoji(trophy: Trophy?): String? = when (trophy) {
    Trophy.GOLD -> "🥇"
    Trophy.SILVER -> "🥈"
    Trophy.BRONZE -> "🥉"
    null -> null
}

@Composable
private fun trophyColor(trophy: Trophy?) = when (trophy) {
    Trophy.GOLD -> TargetGold
    Trophy.SILVER -> TrophySilver
    Trophy.BRONZE -> TrophyBronze
    null -> null
}
