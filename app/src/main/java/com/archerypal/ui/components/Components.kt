package com.archerypal.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.archerypal.data.LeaderboardRow
import com.archerypal.data.Trophy
import com.archerypal.ui.theme.CardSurface
import com.archerypal.ui.theme.FieldGreen
import com.archerypal.ui.theme.TargetGold
import com.archerypal.ui.theme.TextSecondary
import com.archerypal.ui.theme.TrophyBronze
import com.archerypal.ui.theme.TrophySilver

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
    pendingScore: String,
    onDigit: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = if (pendingScore.isBlank()) "—" else pendingScore,
            style = MaterialTheme.typography.displayMedium,
            color = TargetGold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("C", "0", "10")
        )
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { label ->
                    Button(
                        onClick = {
                            when (label) {
                                "C" -> onClear()
                                "10" -> {
                                    onClear()
                                    onDigit("1")
                                    onDigit("0")
                                }
                                else -> onDigit(label)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        PrimaryActionButton("Submit score", onSubmit, enabled = pendingScore.isNotBlank())
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
                trophy = com.archerypal.data.trophyForRank(index + 1)
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
