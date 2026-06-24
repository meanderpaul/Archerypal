package com.archerypal

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.archerypal.data.MatchPhase
import com.archerypal.ui.components.QrScannerView
import com.archerypal.ui.screens.HomeScreen
import com.archerypal.ui.screens.HostScreen
import com.archerypal.ui.screens.JoinScreen
import com.archerypal.ui.screens.MatchScreen
import com.archerypal.ui.screens.SetupScreen
import com.archerypal.ui.theme.ArcherypalTheme
import com.archerypal.viewmodel.MatchViewModel

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* handled per-screen */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNearbyPermissions()

        setContent {
            ArcherypalTheme {
                ArcherypalApp()
            }
        }
    }

    private fun requestNearbyPermissions() {
        val permissions = buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }
}

@Composable
private fun ArcherypalApp(viewModel: MatchViewModel = viewModel()) {
    val navController = rememberNavController()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.phase, state.isHost, state.matchId) {
        when {
            state.isHost && state.phase == MatchPhase.LOBBY && state.matchId.isNotBlank() -> {
                if (navController.currentDestination?.route != "host") {
                    navController.navigate("host") {
                        popUpTo("home") { inclusive = false }
                    }
                }
            }
            !state.isHost && state.phase == MatchPhase.SCORING -> {
                if (navController.currentDestination?.route != "match") {
                    navController.navigate("match") {
                        popUpTo("home") { inclusive = false }
                    }
                }
            }
            state.isHost && state.phase == MatchPhase.SCORING -> {
                if (navController.currentDestination?.route != "match") {
                    navController.navigate("match") {
                        popUpTo("home") { inclusive = false }
                    }
                }
            }
            state.phase == MatchPhase.FINISHED -> {
                if (navController.currentDestination?.route != "match") {
                    navController.navigate("match") {
                        popUpTo("home") { inclusive = false }
                    }
                }
            }
        }
    }

    if (state.errorMessage != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Something went wrong") },
            text = { Text(state.errorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text("OK") }
            }
        )
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                HomeScreen(
                    playerName = state.playerName,
                    onNameChange = viewModel::setPlayerName,
                    onHost = {
                        viewModel.startHosting()
                        navController.navigate("host")
                    },
                    onJoin = {
                        viewModel.startJoining()
                        navController.navigate("join")
                    },
                    onRematchHost = {
                        viewModel.startRematchAsHost()
                        navController.navigate("host")
                    },
                    onResyncJoin = { friendName ->
                        viewModel.resyncWithFriend(friendName)
                        navController.navigate("join")
                    },
                    savedFriends = state.savedFriends,
                    lastMatchGroup = state.lastMatchGroup,
                    globalLeaderboard = state.globalLeaderboard
                )
            }
            composable("host") {
                HostScreen(
                    qrContent = viewModel.buildQrContent(),
                    playerCount = state.players.size,
                    statusMessage = state.statusMessage,
                    onContinue = { navController.navigate("setup") },
                    onLeave = {
                        viewModel.leaveMatch()
                        navController.navigate("home") { popUpTo(0) }
                    }
                )
            }
            composable("join") {
                JoinScreen(
                    statusMessage = state.statusMessage,
                    discoveredHosts = state.discoveredHosts,
                    onHostSelected = viewModel::connectToDiscoveredHost,
                    onLeave = {
                        viewModel.leaveMatch()
                        navController.navigate("home") { popUpTo(0) }
                    },
                    scannerSlot = {
                        QrScannerView(onQrScanned = viewModel::onQrScanned)
                    }
                )
            }
            composable("setup") {
                SetupScreen(
                    targetCountInput = state.targetCountInput,
                    onTargetCountInputChange = viewModel::setTargetCountInput,
                    playerCount = state.players.size,
                    onBegin = {
                        viewModel.beginMatch()
                        navController.navigate("match")
                    },
                    onLeave = {
                        viewModel.leaveMatch()
                        navController.navigate("home") { popUpTo(0) }
                    }
                )
            }
            composable("match") {
                MatchScreen(
                    targetCount = state.targetCount,
                    selectedTarget = state.selectedTarget,
                    pendingScore = state.pendingScore,
                    statusMessage = state.statusMessage,
                    phase = state.phase,
                    isHost = state.isHost,
                    matchLeaderboard = viewModel.matchLeaderboard(),
                    onTargetSelected = viewModel::selectTarget,
                    onDigit = viewModel::appendDigit,
                    onClear = viewModel::clearPendingScore,
                    onSubmit = viewModel::submitScore,
                    onFinishMatch = viewModel::finishMatch,
                    onLeave = {
                        viewModel.leaveMatch()
                        navController.navigate("home") { popUpTo(0) }
                    }
                )
            }
        }
    }
}
