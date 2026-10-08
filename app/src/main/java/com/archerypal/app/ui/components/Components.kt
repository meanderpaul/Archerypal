package com.archerypal.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.archerypal.app.data.LeaderboardRow
import com.archerypal.app.data.ScoringType
import com.archerypal.app.data.Trophy
import com.archerypal.app.ui.theme.CardSurface
import com.archerypal.app.ui.theme.FieldGreen
import com.archerypal.app.ui.theme.ForestGreen
import com.archerypal.app.ui.theme.OutdoorBackground
import com.archerypal.app.ui.theme.TargetGold
import com.archerypal.app.ui.theme.TextSecondary
import com.archerypal.app.ui.theme.TrophyBronze
import com.archerypal.app.ui.theme.TrophySilver
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun OutdoorCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    /** When true, the card body fills available height so weighted children work. */
    fillMaxHeight: Boolean = false,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .then(if (fillMaxHeight) Modifier.fillMaxSize() else Modifier)
                .padding(contentPadding),
            content = { content() }
        )
    }
}

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillWidth: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = if (fillWidth) modifier.fillMaxWidth() else modifier,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = FieldGreen,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = ForestGreen.copy(alpha = 0.4f)
        )
    ) {
        ButtonLabel(text, fillWidth, FontWeight.Bold)
    }
}

@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillWidth: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = if (fillWidth) modifier.fillMaxWidth() else modifier,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        ButtonLabel(text, fillWidth)
    }
}

/**
 * Full-width buttons wrap long labels between words; inline buttons sit beside other
 * content, so they stay on one line and shrink rather than wrap mid-word.
 */
@Composable
private fun ButtonLabel(text: String, fillWidth: Boolean, fontWeight: FontWeight? = null) {
    val style = MaterialTheme.typography.titleMedium
    if (fillWidth && text.contains(' ')) {
        Text(text, style = style, fontWeight = fontWeight, textAlign = TextAlign.Center)
    } else {
        AutoSizeText(text, style = style, fontWeight = fontWeight, textAlign = TextAlign.Center)
    }
}

/**
 * Score pad. When [fillHeight] is true, wraps content in [FitToHeightBox] so the
 * pad scales down uniformly if it would otherwise overflow the available height.
 */
@Composable
fun ScorePad(
    scoringType: ScoringType,
    pendingScore: String,
    onPresetScore: (Int) -> Unit,
    onDigit: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    submitLabel: String = "Submit score",
    compact: Boolean = false,
    fillHeight: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isFreeform = scoringType == ScoringType.FREEFORM
    val spacing = if (compact) 6.dp else 10.dp

    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            if (isFreeform) {
                Text(
                    text = if (pendingScore.isBlank()) "—" else pendingScore,
                    style = if (compact) {
                        MaterialTheme.typography.displaySmall
                    } else {
                        MaterialTheme.typography.displayMedium
                    },
                    color = TargetGold,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = if (compact) 2.dp else 4.dp)
                )
            }
            when (scoringType) {
                ScoringType.FREEFORM -> FreeformScorePad(
                    onDigit = onDigit,
                    onClear = onClear,
                    compact = compact
                )
                else -> PresetScorePad(
                    scores = scoringType.allowedScores.orEmpty(),
                    pendingScore = pendingScore,
                    onPresetScore = onPresetScore,
                    onClear = onClear,
                    compact = compact
                )
            }
            if (isFreeform) {
                PrimaryActionButton(submitLabel, onSubmit, enabled = pendingScore.isNotBlank())
            }
        }
    }

    if (fillHeight) {
        FitToHeightBox(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            body()
        }
    } else {
        Column(modifier = modifier.fillMaxWidth()) {
            body()
        }
    }
}

/**
 * Measures [content] at unconstrained height, then uniformly scales it down when
 * taller than the available max height so it fits without scrolling or clipping.
 */
@Composable
fun FitToHeightBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val measurable = measurables.firstOrNull()
            ?: return@Layout layout(constraints.minWidth, constraints.minHeight) {}

        val placeable = measurable.measure(
            constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        )
        val availableH = if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height
        val availableW = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width

        val scale = when {
            placeable.height <= 0 || placeable.width <= 0 -> 1f
            placeable.height <= availableH && placeable.width <= availableW -> 1f
            else -> min(
                availableH.toFloat() / placeable.height.toFloat(),
                availableW.toFloat() / placeable.width.toFloat()
            ).coerceIn(0.55f, 1f)
        }

        val scaledW = (placeable.width * scale).roundToInt().coerceAtMost(availableW)
        val scaledH = (placeable.height * scale).roundToInt().coerceAtMost(availableH)
        val width = availableW.coerceAtLeast(scaledW)
        val height = if (constraints.hasBoundedHeight) availableH else scaledH

        layout(width, height) {
            placeable.placeWithLayer(
                x = ((width - scaledW) / 2f).roundToInt(),
                y = ((height - scaledH) / 2f).roundToInt()
            ) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

@Composable
private fun PresetScorePad(
    scores: List<Int>,
    pendingScore: String,
    onPresetScore: (Int) -> Unit,
    onClear: () -> Unit,
    compact: Boolean = false
) {
    val gap = if (compact) 6.dp else 10.dp
    val rows = scores.chunked(3)
    val buttonMin = 48.dp
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { score ->
                    val selected = pendingScore == score.toString()
                    Button(
                        onClick = { onPresetScore(score) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = buttonMin),
                        shape = RoundedCornerShape(999.dp),
                        colors = if (selected) {
                            ButtonDefaults.buttonColors(
                                containerColor = TargetGold,
                                contentColor = OutdoorBackground
                            )
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        border = if (selected) {
                            null
                        } else {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        }
                    ) {
                        Text(
                            score.toString(),
                            fontWeight = FontWeight.ExtraBold,
                            style = if (compact) {
                                MaterialTheme.typography.titleLarge
                            } else {
                                MaterialTheme.typography.headlineSmall
                            }
                        )
                    }
                }
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        OutlinedButton(
            onClick = onClear,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Text("Clear", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FreeformScorePad(
    onDigit: (String) -> Unit,
    onClear: () -> Unit,
    compact: Boolean = false
) {
    val gap = if (compact) 6.dp else 10.dp
    val buttonMin = 48.dp
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("C", "0")
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { label ->
                    Button(
                        onClick = {
                            when (label) {
                                "C" -> onClear()
                                else -> onDigit(label)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = buttonMin),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (label == "C") {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            label,
                            fontWeight = FontWeight.ExtraBold,
                            style = if (compact) {
                                MaterialTheme.typography.titleLarge
                            } else {
                                MaterialTheme.typography.headlineSmall
                            }
                        )
                    }
                }
                if (row.size < 3) {
                    repeat(3 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
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
    emptyMessage: String = "No scores yet",
    onRowClick: ((LeaderboardRow) -> Unit)? = null
) {
    if (rows.isEmpty()) {
        Text(emptyMessage, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        return
    }
    rows.forEach { row ->
        LeaderboardRowItem(row, onClick = onRowClick?.let { { it(row) } })
    }
}

@Composable
private fun LeaderboardRowItem(row: LeaderboardRow, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
