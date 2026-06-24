package com.archerypal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.archerypal.data.AppConstants
import com.archerypal.ui.components.OutdoorCard
import com.archerypal.ui.components.PrimaryActionButton
import com.archerypal.ui.components.SecondaryActionButton

@Composable
fun SetupScreen(
    targetCountInput: String,
    onTargetCountInputChange: (String) -> Unit,
    playerCount: Int,
    onBegin: () -> Unit,
    onLeave: () -> Unit
) {
    val sliderValue = targetCountInput.toIntOrNull()?.toFloat()
        ?: AppConstants.MIN_TARGETS.toFloat()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Match setup", style = MaterialTheme.typography.headlineMedium)
        OutdoorCard {
            Text("Archers ready: $playerCount")
            Text("Set how many targets each archer will shoot.")
        }
        OutlinedTextField(
            value = targetCountInput,
            onValueChange = onTargetCountInputChange,
            label = { Text("Number of targets") },
            placeholder = { Text("e.g. 12") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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
