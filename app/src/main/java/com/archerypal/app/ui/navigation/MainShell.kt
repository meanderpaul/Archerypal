package com.archerypal.app.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Sports
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.archerypal.app.ui.components.AutoSizeText
import com.archerypal.app.ui.layout.rememberScreenMetrics

sealed class MainTab(val route: String, val label: String) {
    data object Contest : MainTab("plus_contest", "Social")
    data object Play : MainTab("play", "Shoot")
    data object Standings : MainTab("standings", "Standings")
    data object History : MainTab("history", "History")
    data object More : MainTab("more", "More")
}

private fun MainTab.icon(): ImageVector = when (this) {
    MainTab.Contest -> Icons.Outlined.Groups
    MainTab.Play -> Icons.Outlined.Sports
    MainTab.Standings -> Icons.Outlined.EmojiEvents
    MainTab.History -> Icons.Outlined.History
    MainTab.More -> Icons.Outlined.MoreHoriz
}

@Composable
fun MainShell(
    isPlusActive: Boolean,
    onEnsureServerIdentity: () -> Unit,
    contestContent: @Composable () -> Unit,
    playContent: @Composable () -> Unit,
    standingsContent: @Composable () -> Unit,
    historyContent: @Composable () -> Unit,
    moreContent: @Composable () -> Unit,
    footerContent: @Composable () -> Unit = {},
    initialTab: String? = null,
    navigateToTab: String? = null,
    onNavigateToTabConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val metrics = rememberScreenMetrics()
    val useRail = metrics.isExpandedWidth
    val tabs = listOf(MainTab.Contest, MainTab.Play, MainTab.Standings, MainTab.History, MainTab.More)
    // Keep startDestination stable. Changing it when Plus flips blanks NavHost.
    val startRoute = MainTab.Play.route

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    fun navigateTo(route: String) {
        runCatching {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    LaunchedEffect(isPlusActive) {
        onEnsureServerIdentity()
    }

    LaunchedEffect(initialTab, isPlusActive) {
        if (!initialTab.isNullOrBlank() && tabs.any { it.route == initialTab }) {
            navigateTo(initialTab)
        }
    }

    LaunchedEffect(navigateToTab, isPlusActive) {
        if (!navigateToTab.isNullOrBlank() && tabs.any { it.route == navigateToTab }) {
            navigateTo(navigateToTab)
            onNavigateToTabConsumed()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // Do not use safeDrawing here: with edge-to-edge it can consume the full
        // window as padding and leave a 0-height content area (black screen).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!useRail) {
                NavigationBar(
                    windowInsets = WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    ),
                ) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navigateTo(tab.route) },
                            icon = { Icon(tab.icon(), contentDescription = tab.label) },
                            label = { AutoSizeText(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues())
                .then(
                    if (useRail) {
                        Modifier.padding(
                            WindowInsets.safeDrawing
                                .only(WindowInsetsSides.Bottom + WindowInsetsSides.End)
                                .asPaddingValues()
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Start)
                    ) {
                        tabs.forEach { tab ->
                            NavigationRailItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateTo(tab.route) },
                                icon = { Icon(tab.icon(), contentDescription = tab.label) },
                                label = { AutoSizeText(tab.label) }
                            )
                        }
                    }
                }
                NavHost(
                    navController = navController,
                    startDestination = startRoute,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Always register routes so the graph never changes under NavHost.
                    composable(MainTab.Contest.route) { contestContent() }
                    composable(MainTab.Play.route) { playContent() }
                    composable(MainTab.Standings.route) { standingsContent() }
                    composable(MainTab.History.route) { historyContent() }
                    composable(MainTab.More.route) { moreContent() }
                }
            }
            footerContent()
        }
    }
}
