package com.archerypal.app.ui.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

@Immutable
data class ScreenMetrics(
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val bottomNavContentGap: Dp,
    val sectionSpacing: Dp,
    val cardPadding: Dp,
    val qrCodeSize: Dp,
    val cameraPreviewHeight: Dp,
    val isCompactHeight: Boolean,
    val isTightHeight: Boolean,
    val isCompactWidth: Boolean,
    val isExpandedWidth: Boolean,
    val headlineSize: TextUnit,
    val titleSize: TextUnit,
    val bodySize: TextUnit,
    val useScrollFallback: Boolean
)

@Composable
fun rememberScreenMetrics(): ScreenMetrics {
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    // On targetSdk 35+ the configuration size includes system bars and cutouts.
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val insetWidthDp = with(density) {
        (insets.getLeft(this, layoutDirection) + insets.getRight(this, layoutDirection)).toDp().value
    }
    val insetHeightDp = with(density) {
        (insets.getTop(this) + insets.getBottom(this)).toDp().value
    }
    val widthDp = (config.screenWidthDp - insetWidthDp.toInt()).coerceAtLeast(1)
    val heightDp = (config.screenHeightDp - insetHeightDp.toInt()).coerceAtLeast(1)
    return remember(widthDp, heightDp) {
        val compactHeight = heightDp < 640
        val tightHeight = heightDp < 720
        val compactWidth = widthDp < 360
        val expandedWidth = widthDp >= 600
        val horizontal = when {
            compactWidth -> 12.dp
            !expandedWidth -> 16.dp
            else -> 20.dp
        }
        val vertical = when {
            compactHeight -> 6.dp
            tightHeight -> 8.dp
            else -> 12.dp
        }
        val spacing = when {
            compactHeight -> 6.dp
            tightHeight -> 8.dp
            else -> 12.dp
        }
        val cardPad = when {
            compactHeight -> 10.dp
            tightHeight -> 12.dp
            else -> 16.dp
        }
        val qrMax = if (expandedWidth) 320f else 260f
        val cameraMax = if (expandedWidth) 320f else 240f
        val qrSize = min(widthDp * (if (expandedWidth) 0.36f else 0.52f), heightDp * 0.28f)
            .coerceIn(140f, qrMax).dp
        val cameraHeight = (heightDp * (if (expandedWidth) 0.36f else 0.24f))
            .coerceIn(140f, cameraMax).dp
        val scale = when {
            compactHeight -> 0.88f
            tightHeight -> 0.94f
            else -> 1f
        }
        ScreenMetrics(
            horizontalPadding = horizontal,
            verticalPadding = vertical,
            bottomNavContentGap = if (expandedWidth) 8.dp else 20.dp,
            sectionSpacing = spacing,
            cardPadding = cardPad,
            qrCodeSize = qrSize,
            cameraPreviewHeight = cameraHeight,
            isCompactHeight = compactHeight,
            isTightHeight = tightHeight,
            isCompactWidth = compactWidth,
            isExpandedWidth = expandedWidth,
            headlineSize = (26f * scale).sp,
            titleSize = (17f * scale).sp,
            bodySize = (15f * scale).sp,
            useScrollFallback = false
        )
    }
}

@Composable
fun AdaptiveScreenColumn(
    modifier: Modifier = Modifier,
    scroll: Boolean = false,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.(ScreenMetrics) -> Unit
) {
    val metrics = rememberScreenMetrics()
    var columnModifier = modifier
        .fillMaxSize()
        .padding(
            start = metrics.horizontalPadding,
            end = metrics.horizontalPadding,
            top = metrics.verticalPadding,
            bottom = metrics.verticalPadding + metrics.bottomNavContentGap
        )
    // Only scroll when the screen explicitly opts in (lists / long forms).
    if (scroll) {
        columnModifier = columnModifier.verticalScroll(rememberScrollState())
    }
    Column(
        modifier = columnModifier,
        verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing),
        horizontalAlignment = horizontalAlignment,
        content = { content(metrics) }
    )
}

/**
 * Home screen: fixed header/footer with a weighted, optionally scrollable middle.
 */
@Composable
fun AdaptiveHomeLayout(
    modifier: Modifier = Modifier,
    header: @Composable ColumnScope.(ScreenMetrics) -> Unit,
    footer: @Composable ColumnScope.(ScreenMetrics) -> Unit,
    content: @Composable ColumnScope.(ScreenMetrics) -> Unit
) {
    val metrics = rememberScreenMetrics()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = metrics.horizontalPadding,
                vertical = metrics.verticalPadding
            ),
        verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing)
    ) {
        header(metrics)
        val middleModifier = Modifier.weight(1f)
        Column(
            modifier = middleModifier,
            verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing)
        ) {
            content(metrics)
        }
        footer(metrics)
    }
}

/**
 * Full-screen routes outside [com.archerypal.app.ui.navigation.MainShell] draw edge-to-edge,
 * so they must pad for the status bar, display cutout, navigation bar, and IME themselves.
 */
@Composable
fun SafeAreaScreen(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        content()
    }
}

@Composable
fun AdaptiveScreenBox(
    modifier: Modifier = Modifier,
    content: @Composable (ScreenMetrics) -> Unit
) {
    val metrics = rememberScreenMetrics()
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = metrics.horizontalPadding,
                vertical = metrics.verticalPadding
            )
    ) {
        content(metrics)
    }
}
