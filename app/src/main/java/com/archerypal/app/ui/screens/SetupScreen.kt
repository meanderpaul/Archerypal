package com.archerypal.app.ui.screens

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.archerypal.app.data.AppConstants
import com.archerypal.app.data.ScoringType
import com.archerypal.app.ui.components.OutdoorCard
import com.archerypal.app.ui.components.PrimaryActionButton
import com.archerypal.app.ui.components.SecondaryActionButton
import com.archerypal.app.ui.layout.AdaptiveScreenColumn

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(
    scoringType: ScoringType,
    onScoringTypeChange: (ScoringType) -> Unit,
    targetCountInput: String,
    onTargetCountInputChange: (String) -> Unit,
    playerCount: Int,
    onBegin: () -> Unit,
    onLeave: () -> Unit
) {
    val sliderValue = targetCountInput.toIntOrNull()?.toFloat()
        ?: AppConstants.MIN_TARGETS.toFloat()

    AdaptiveScreenColumn(scroll = true) { _ ->
        Text("Match setup", style = MaterialTheme.typography.headlineMedium)
        OutdoorCard {
            Text("Archers ready: $playerCount")
            Text("Choose a scoring format and how many targets each archer will shoot.")
        }
        Text("Scoring format", style = MaterialTheme.typography.titleMedium)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            ScoringType.entries.forEach { type ->
                FilterChip(
                    selected = scoringType == type,
                    onClick = { onScoringTypeChange(type) },
                    label = { Text(type.label) }
                )
            }
        }
        Text(
            scoringType.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        OutlinedTextField(
            value = targetCountInput,
            onValueChange = onTargetCountInputChange,
            label = { Text("Number of targets") },
            placeholder = { Text("e.g. 20") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Clear the field to type a new number, or use the slider.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Slider(
            value = sliderValue,
            onValueChange = { onTargetCountInputChange(it.toInt().toString()) },
            valueRange = AppConstants.MIN_TARGETS.toFloat()..AppConstants.MAX_TARGETS.toFloat(),
            steps = AppConstants.MAX_TARGETS - AppConstants.MIN_TARGETS - 1
        )
        PrimaryActionButton(
            text = "Start scoring",
            onClick = onBegin,
            enabled = targetCountInput.toIntOrNull()?.let { it >= AppConstants.MIN_TARGETS } == true
        )
        SecondaryActionButton("Cancel", onLeave)
    }
}
