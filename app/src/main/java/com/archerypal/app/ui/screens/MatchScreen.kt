package com.archerypal.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.archerypal.app.data.LeaderboardRow
import com.archerypal.app.data.ScoringType
import com.archerypal.app.ui.components.AutoSizeText
import com.archerypal.app.ui.components.OutdoorCard
import com.archerypal.app.ui.components.RankedLeaderboardList
import com.archerypal.app.ui.components.ScorePad
import com.archerypal.app.ui.layout.rememberScreenMetrics
import com.archerypal.app.ui.theme.ArcherypalTheme
import com.archerypal.app.ui.theme.FieldGreen
import com.archerypal.app.ui.theme.ForestGreen
import com.archerypal.app.ui.theme.SageMuted
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
    val metrics = rememberScreenMetrics()
    val safeTargetCount = targetCount.coerceAtLeast(1)
    val existingScore = playerTargetScores[selectedTarget]
    val isEditing = existingScore != null
    val compact = metrics.isCompactHeight
    val expanded = metrics.isExpandedWidth

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = metrics.horizontalPadding,
                vertical = metrics.verticalPadding
            ),
        verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing)
    ) {
        MatchHeader(
            compact = compact,
            selectedTarget = selectedTarget,
            targetCount = safeTargetCount,
            scoringLabel = scoringType.label,
            runningTotal = playerRunningTotal,
            statusMessage = statusMessage,
            isHost = isHost,
            onFinishMatch = onFinishMatch,
            onLeave = onLeave
        )

        if (expanded) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metrics.sectionSpacing)
            ) {
                MatchScorePane(
                    scoringType = scoringType,
                    safeTargetCount = safeTargetCount,
                    selectedTarget = selectedTarget,
                    playerTargetScores = playerTargetScores,
                    playerRunningTotal = playerRunningTotal,
                    pendingScore = pendingScore,
                    isEditing = isEditing,
                    compact = compact,
                    tight = metrics.isTightHeight,
                    cardPadding = metrics.cardPadding,
                    sectionSpacing = metrics.sectionSpacing,
                    onTargetSelected = onTargetSelected,
                    onPresetScore = onPresetScore,
                    onDigit = onDigit,
                    onClear = onClear,
                    onSubmit = onSubmit,
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                )
                MatchLeaderboardPane(
                    matchLeaderboard = matchLeaderboard,
                    cardPadding = metrics.cardPadding,
                    modifier = Modifier
                        .weight(0.85f)
                        .fillMaxHeight()
                )
            }
        } else {
            SegmentedTabs(
                tabs = tabs,
                selectedIndex = tabIndex,
                onSelect = { tabIndex = it },
                compact = compact
            )

            when (tabIndex) {
                0 -> MatchScorePane(
                    scoringType = scoringType,
                    safeTargetCount = safeTargetCount,
                    selectedTarget = selectedTarget,
                    playerTargetScores = playerTargetScores,
                    playerRunningTotal = playerRunningTotal,
                    pendingScore = pendingScore,
                    isEditing = isEditing,
                    compact = compact,
                    tight = metrics.isTightHeight,
                    cardPadding = metrics.cardPadding,
                    sectionSpacing = metrics.sectionSpacing,
                    onTargetSelected = onTargetSelected,
                    onPresetScore = onPresetScore,
                    onDigit = onDigit,
                    onClear = onClear,
                    onSubmit = onSubmit,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                else -> MatchLeaderboardPane(
                    matchLeaderboard = matchLeaderboard,
                    cardPadding = metrics.cardPadding,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun MatchScorePane(
    scoringType: ScoringType,
    safeTargetCount: Int,
    selectedTarget: Int,
    playerTargetScores: Map<Int, Int>,
    playerRunningTotal: Int,
    pendingScore: String,
    isEditing: Boolean,
    compact: Boolean,
    tight: Boolean,
    cardPadding: Dp,
    sectionSpacing: Dp,
    onTargetSelected: (Int) -> Unit,
    onPresetScore: (Int) -> Unit,
    onDigit: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutdoorCard(
        modifier = modifier,
        contentPadding = cardPadding,
        fillMaxHeight = true
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing)
        ) {
            if (!compact) {
                StatStrip(
                    selectedTarget = selectedTarget,
                    targetCount = safeTargetCount,
                    scoringLabel = scoringType.label,
                    runningTotal = playerRunningTotal
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
                items((1..safeTargetCount).toList()) { target ->
                    val score = playerTargetScores[target]
                    FilterChip(
                        selected = selectedTarget == target,
                        onClick = { onTargetSelected(target) },
                        shape = RoundedCornerShape(14.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TargetGold.copy(alpha = 0.18f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedTarget == target,
                            borderColor = MaterialTheme.colorScheme.outline,
                            selectedBorderColor = TargetGold
                        ),
                        label = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "T$target",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SageMuted
                                )
                                Text(
                                    text = score?.toString() ?: "—",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        selectedTarget == target -> MaterialTheme.colorScheme.onSurface
                                        score != null -> TargetGold
                                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                    }
                                )
                            }
                        }
                    )
                }
            }
            if (!compact) {
                Text(
                    if (isEditing) {
                        "Tap another target to edit its score"
                    } else {
                        "Tap a logged target to edit · scores advance automatically"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
                Text(
                    if (isEditing) "Editing target $selectedTarget" else "Tap a score",
                    style = MaterialTheme.typography.labelMedium,
                    color = SageMuted
                )
            }
            ScorePad(
                scoringType = scoringType,
                pendingScore = pendingScore,
                onPresetScore = onPresetScore,
                onDigit = onDigit,
                onClear = onClear,
                onSubmit = onSubmit,
                submitLabel = if (isEditing) "Update score" else "Submit score",
                compact = compact || tight,
                fillHeight = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MatchLeaderboardPane(
    matchLeaderboard: List<LeaderboardRow>,
    cardPadding: Dp,
    modifier: Modifier = Modifier
) {
    OutdoorCard(
        modifier = modifier,
        contentPadding = cardPadding,
        fillMaxHeight = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Leaderboard",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            RankedLeaderboardList(rows = matchLeaderboard)
        }
    }
}

@Composable
private fun MatchHeader(
    compact: Boolean,
    selectedTarget: Int,
    targetCount: Int,
    scoringLabel: String,
    runningTotal: Int,
    statusMessage: String,
    isHost: Boolean,
    onFinishMatch: () -> Unit,
    onLeave: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            ForestGreen.copy(alpha = 0.45f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.1f)
                        )
                    )
                )
                .padding(
                    horizontal = if (compact) 10.dp else 14.dp,
                    vertical = if (compact) 8.dp else 12.dp
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (compact) {
                    Text(
                        text = buildString {
                            append("T$selectedTarget · $scoringLabel · ")
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "$runningTotal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TargetGold
                        )
                        Text(
                            statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .align(Alignment.CenterVertically)
                        )
                    }
                } else {
                    Text(
                        "LIVE MATCH",
                        style = MaterialTheme.typography.labelSmall,
                        color = SageMuted
                    )
                    Text(
                        "Scoring",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isHost) {
                    IconButton(
                        onClick = onFinishMatch,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = FieldGreen,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = "Finish and save match",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onLeave,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Leave match",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatStrip(
    selectedTarget: Int,
    targetCount: Int,
    scoringLabel: String,
    runningTotal: Int
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Target ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "$selectedTarget",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            " of $targetCount",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
        Text(
            scoringLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
        Text(
            "Total ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "$runningTotal",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TargetGold
        )
    }
}

@Composable
private fun SegmentedTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    compact: Boolean
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                val selected = index == selectedIndex
                Surface(
                    onClick = { onSelect(index) },
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    AutoSizeText(
                        title,
                        modifier = Modifier
                            .padding(horizontal = 4.dp, vertical = if (compact) 6.dp else 8.dp)
                            .fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Preview(name = "Match ASA", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun MatchScreenPreview() {
    ArcherypalTheme {
        MatchScreen(
            scoringType = ScoringType.ASA,
            targetCount = 20,
            selectedTarget = 4,
            pendingScore = "",
            playerTargetScores = mapOf(1 to 12, 2 to 10, 3 to 8),
            playerRunningTotal = 30,
            statusMessage = "Hosting · 3 archers",
            isHost = true,
            matchLeaderboard = emptyList(),
            onTargetSelected = {},
            onPresetScore = {},
            onDigit = {},
            onClear = {},
            onSubmit = {},
            onFinishMatch = {},
            onLeave = {}
        )
    }
}

@Preview(name = "Match expanded", showBackground = true, widthDp = 840, heightDp = 720)
@Composable
private fun MatchScreenExpandedPreview() {
    ArcherypalTheme {
        MatchScreen(
            scoringType = ScoringType.ASA,
            targetCount = 20,
            selectedTarget = 4,
            pendingScore = "",
            playerTargetScores = mapOf(1 to 12, 2 to 10, 3 to 8),
            playerRunningTotal = 30,
            statusMessage = "Hosting · 3 archers",
            isHost = true,
            matchLeaderboard = emptyList(),
            onTargetSelected = {},
            onPresetScore = {},
            onDigit = {},
            onClear = {},
            onSubmit = {},
            onFinishMatch = {},
            onLeave = {}
        )
    }
}

@Preview(name = "Match compact", showBackground = true, widthDp = 360, heightDp = 560)
@Composable
private fun MatchScreenCompactPreview() {
    ArcherypalTheme {
        MatchScreen(
            scoringType = ScoringType.ASA,
            targetCount = 20,
            selectedTarget = 4,
            pendingScore = "10",
            playerTargetScores = mapOf(1 to 12, 2 to 10, 3 to 8),
            playerRunningTotal = 30,
            statusMessage = "3 archers",
            isHost = true,
            matchLeaderboard = emptyList(),
            onTargetSelected = {},
            onPresetScore = {},
            onDigit = {},
            onClear = {},
            onSubmit = {},
            onFinishMatch = {},
            onLeave = {}
        )
    }
}

@Preview(name = "Match freeform short", showBackground = true, widthDp = 360, heightDp = 520)
@Composable
private fun MatchScreenFreeformShortPreview() {
    ArcherypalTheme {
        MatchScreen(
            scoringType = ScoringType.FREEFORM,
            targetCount = 12,
            selectedTarget = 2,
            pendingScore = "24",
            playerTargetScores = mapOf(1 to 18),
            playerRunningTotal = 18,
            statusMessage = "Hosting · 2 archers",
            isHost = true,
            matchLeaderboard = emptyList(),
            onTargetSelected = {},
            onPresetScore = {},
            onDigit = {},
            onClear = {},
            onSubmit = {},
            onFinishMatch = {},
            onLeave = {}
        )
    }
}
