package com.archerypal.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.archerypal.app.ads.AdIds
import com.archerypal.app.billing.BillingEntitlements
import com.archerypal.app.billing.RevenueCatConfig
import com.archerypal.app.data.MatchPhase
import com.archerypal.app.data.FriendProfileLink
import com.archerypal.app.data.RemoteInviteLink
import com.archerypal.app.push.InviteMessagingService
import com.archerypal.app.ui.components.BannerAdView
import com.archerypal.app.ui.layout.SafeAreaScreen
import com.archerypal.app.ui.navigation.MainShell
import com.archerypal.app.ui.navigation.MainTab
import com.archerypal.app.ui.screens.social.SocialScreen
import com.archerypal.app.ui.screens.GuestLobbyScreen
import com.archerypal.app.ui.screens.HistoryScreen
import com.archerypal.app.ui.screens.HostScreen
import com.archerypal.app.ui.screens.JoinScreen
import com.archerypal.app.ui.screens.MatchResultsScreen
import com.archerypal.app.ui.screens.MatchScreen
import com.archerypal.app.ui.screens.MoreScreen
import com.archerypal.app.ui.screens.GoogleSignInScreen
import com.archerypal.app.ui.screens.PlayScreen
import com.archerypal.app.ui.screens.SetupScreen
import com.archerypal.app.ui.screens.StandingsScreen
import com.archerypal.app.ui.theme.ArcherypalTheme
import com.archerypal.app.viewmodel.ConnectionStatus
import com.archerypal.app.viewmodel.MatchViewModel
import com.google.android.gms.ads.MobileAds
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.models.StoreTransaction
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialog
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialogOptions
import com.revenuecat.purchases.ui.revenuecatui.PaywallListener
import com.revenuecat.purchases.ui.revenuecatui.customercenter.CustomerCenter

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* handled per-screen */ }

    private val matchViewModel: MatchViewModel by viewModels()

    var pendingInviteLink: String? = null
        private set
    var pendingProfileLink: String? = null
        private set
    var pendingOpenFriends: Boolean = false
        private set
    var inviteRevision by mutableIntStateOf(0)
        private set

    fun consumeOpenFriends() {
        pendingOpenFriends = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingInviteLink = extractInviteFromIntent(intent)
        pendingProfileLink = extractProfileFromIntent(intent)
        pendingOpenFriends = isFriendRequestIntent(intent)

        setContent {
            ArcherypalTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ArcherypalApp(viewModel = matchViewModel)
                }
            }
        }
        // activity-compose attaches ComposeView as WRAP_CONTENT; force full window.
        findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0)?.let { child ->
            child.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            (child as? ComposeView)?.requestLayout()
        }

        MobileAds.initialize(this)
        InviteMessagingService.createNotificationChannel(this)
        window.decorView.post { requestNearbyPermissions() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingInviteLink = extractInviteFromIntent(intent)
        pendingProfileLink = extractProfileFromIntent(intent)
        pendingOpenFriends = isFriendRequestIntent(intent)
        inviteRevision++
    }

    // Background FCM notifications are shown by the system and deliver `data` as extras.
    private fun isFriendRequestIntent(intent: Intent?): Boolean =
        intent?.getBooleanExtra(InviteMessagingService.EXTRA_OPEN_FRIENDS, false) == true ||
            intent?.getStringExtra("type") == "friend_request"

    private fun extractInviteFromIntent(intent: Intent?): String? {
        val data = intent?.data?.toString()
        if (data != null && RemoteInviteLink.parse(data) != null) return data
        val inviteUri = intent?.getStringExtra(InviteMessagingService.EXTRA_INVITE_URI)
            ?: return null
        return inviteUri.takeIf { RemoteInviteLink.parse(it) != null }
    }

    private fun extractProfileFromIntent(intent: Intent?): String? {
        val data = intent?.data?.toString() ?: return null
        return data.takeIf { FriendProfileLink.parse(it) != null }
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
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }
}

@Composable
private fun ArcherypalApp(
    viewModel: MatchViewModel
) {
    val navController = rememberNavController()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current as MainActivity
    var mainInitialTab by rememberSaveable { mutableStateOf<String?>(null) }
    var tabNavigationRequest by rememberSaveable { mutableStateOf<String?>(null) }
    var socialHubRequest by rememberSaveable { mutableStateOf<String?>(null) }
    var showGoogleSignInPrompt by rememberSaveable { mutableStateOf(false) }
    val showAds = BillingEntitlements.shouldShowBannerAds(state.isAdFree, state.isPlusActive)
    val remoteFriendCodes = remember(state.remoteFriends) {
        state.remoteFriends.map { it.friendCode.uppercase() }.toSet()
    }

    fun requireGoogleAccount(onReady: () -> Unit) {
        if (state.googleAccountLinked) {
            onReady()
        } else {
            showGoogleSignInPrompt = true
        }
    }

    LaunchedEffect(activity.inviteRevision, state.googleAccountLinked) {
        activity.pendingInviteLink?.let { link ->
            if (state.googleAccountLinked) {
                viewModel.setPendingInviteLink(link)
            } else {
                viewModel.setPendingInviteLink(link)
                showGoogleSignInPrompt = true
            }
        }
        if (activity.pendingOpenFriends) {
            activity.consumeOpenFriends()
            tabNavigationRequest = MainTab.Contest.route
            socialHubRequest = "FRIENDS"
            viewModel.refreshSocialData()
        }
        activity.pendingProfileLink?.let { link ->
            if (state.googleAccountLinked) {
                mainInitialTab = MainTab.Contest.route
                viewModel.handleFriendProfileLink(link)
            } else {
                showGoogleSignInPrompt = true
            }
        }
    }

    LaunchedEffect(state.pendingInviteLink, state.googleAccountLinked) {
        if (!state.googleAccountLinked) return@LaunchedEffect
        val link = state.pendingInviteLink ?: return@LaunchedEffect
        mainInitialTab = MainTab.Play.route
        viewModel.joinFromInviteLink(link)
        viewModel.consumePendingInvite()
        navController.navigate("guest_lobby") {
            popUpTo("main") { inclusive = false }
        }
    }

    LaunchedEffect(state.googleAccountLinked) {
        if (state.googleAccountLinked) {
            showGoogleSignInPrompt = false
        }
    }

    LaunchedEffect(state.phase, state.isHost, state.matchId, state.hostEndpointId) {
        // Only auto-navigate while an actual match session is active.
        val inActiveMatch = state.matchId.isNotBlank() || state.hostEndpointId != null
        if (!inActiveMatch) return@LaunchedEffect
        when {
            state.isHost && state.phase == MatchPhase.LOBBY && state.matchId.isNotBlank() -> {
                val route = navController.currentDestination?.route
                if (route != "host" && route != "setup") {
                    navController.navigate("host") {
                        popUpTo("main") { inclusive = false }
                    }
                }
            }
            !state.isHost && state.hostEndpointId != null && state.phase == MatchPhase.LOBBY -> {
                if (navController.currentDestination?.route != "guest_lobby") {
                    navController.navigate("guest_lobby") {
                        popUpTo("main") { inclusive = false }
                    }
                }
            }
            !state.isHost && state.hostEndpointId == null &&
                navController.currentDestination?.route == "guest_lobby" -> {
                navController.navigate("join") {
                    popUpTo("main") { inclusive = false }
                }
            }
            !state.isHost && state.phase == MatchPhase.SCORING -> {
                if (navController.currentDestination?.route != "match") {
                    navController.navigate("match") {
                        popUpTo("main") { inclusive = false }
                    }
                }
            }
            state.isHost && state.phase == MatchPhase.SCORING -> {
                if (navController.currentDestination?.route != "match") {
                    navController.navigate("match") {
                        popUpTo("main") { inclusive = false }
                    }
                }
            }
            state.phase == MatchPhase.FINISHED -> {
                if (navController.currentDestination?.route != "match_results") {
                    navController.navigate("match_results") {
                        popUpTo("main") { inclusive = false }
                    }
                }
            }
        }
    }

    if (state.billingMessage != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearBillingMessage,
            title = { Text("Purchase") },
            text = { Text(state.billingMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = viewModel::clearBillingMessage) { Text("OK") }
            }
        )
    }

    if (state.pendingProfileFriendCode != null) {
        val name = state.pendingProfileFriendName ?: state.pendingProfileFriendCode.orEmpty()
        AlertDialog(
            onDismissRequest = viewModel::clearPendingProfileAdd,
            title = { Text("Add friend?") },
            text = { Text("Add $name as a remote friend?") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmPendingProfileAdd) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::clearPendingProfileAdd) { Text("Cancel") }
            }
        )
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

    if (showGoogleSignInPrompt && !state.googleAccountLinked) {
        Dialog(
            onDismissRequest = { showGoogleSignInPrompt = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                GoogleSignInScreen(
                    playerName = state.playerName,
                    onNameChange = viewModel::setPlayerName,
                    inProgress = state.accountActionInProgress,
                    onSignInWithGoogle = viewModel::signInWithGoogleRequired,
                    headline = "Sign in required",
                    subtitle = "Google Sign-In unlocks Social, remote invites, and Friend ID backup",
                    onDismiss = { showGoogleSignInPrompt = false },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (state.showPaywall) {
        val paywallListener = remember {
            object : PaywallListener {
                override fun onPurchaseCompleted(
                    customerInfo: CustomerInfo,
                    storeTransaction: StoreTransaction
                ) {
                    viewModel.onRevenueCatCustomerInfo(customerInfo)
                }

                override fun onRestoreCompleted(customerInfo: CustomerInfo) {
                    viewModel.onRevenueCatCustomerInfo(customerInfo)
                }
            }
        }
        PaywallDialog(
            PaywallDialogOptions.Builder()
                .setRequiredEntitlementIdentifier(RevenueCatConfig.ENTITLEMENT_PRO)
                .setListener(paywallListener)
                .setDismissRequest { viewModel.dismissPaywall() }
                .build()
        )
    }

    if (state.showCustomerCenter) {
        Dialog(
            onDismissRequest = { viewModel.dismissCustomerCenter() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            CustomerCenter(
                modifier = Modifier.fillMaxSize(),
                onDismiss = { viewModel.dismissCustomerCenter() }
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "main",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            composable("main") {
                MainShell(
                    isPlusActive = state.isPlusActive,
                    onEnsureServerIdentity = viewModel::ensureServerIdentity,
                    initialTab = mainInitialTab,
                    navigateToTab = tabNavigationRequest,
                    onNavigateToTabConsumed = { tabNavigationRequest = null },
                    modifier = Modifier.fillMaxSize(),
                    footerContent = {
                        if (showAds) {
                            BannerAdView(
                                adUnitId = AdIds.bannerUnitId,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        }
                    },
                    contestContent = {
                        SocialScreen(
                            playerName = state.playerName,
                            friendCode = state.friendCode,
                            deviceId = viewModel.deviceIdForMedia(),
                            deviceSecret = viewModel.deviceSecretForMedia(),
                            profileShareLink = viewModel.buildFriendProfileLink(),
                            googleAccountLinked = state.googleAccountLinked,
                            isPlusActive = state.isPlusActive,
                            serverReachable = state.serverReachable,
                            remoteFriends = state.remoteFriends,
                            feedItems = state.feedItems,
                            feedFilter = state.feedFilter,
                            feedNewCounts = state.feedNewCounts,
                            feedLoading = state.feedLoading,
                            socialLoading = state.socialLoading,
                            myProfile = state.myProfile,
                            viewingFriendProfile = state.viewingFriendProfile,
                            deviceLocation = state.deviceLocation,
                            visibleShoots = state.visibleShoots,
                            shootAttendees = state.selectedShootAttendees,
                            shootDetailLoading = state.shootDetailLoading,
                            pendingFriendRequests = state.pendingFriendRequests,
                            sentFriendRequests = state.sentFriendRequests,
                            mutualFriendSuggestions = state.mutualFriendSuggestions,
                            friendSearchQuery = state.friendSearchQuery,
                            friendSearchResults = state.friendSearchResults,
                            friendSearchLoading = state.friendSearchLoading,
                            friendSearchError = state.friendSearchError,
                            onFriendSearchQueryChange = viewModel::onFriendSearchQueryChange,
                            onSetDiscoverable = viewModel::setDiscoverable,
                            playedTogetherSuggestions = viewModel.friendSuggestionsFromSaved(),
                            sameShootSuggestions = state.shootFriendSuggestions,
                            requestedFriendCodes = state.requestedFriendCodes,
                            requestedHub = socialHubRequest,
                            onRequestedHubConsumed = { socialHubRequest = null },
                            onRefresh = {
                                viewModel.updateDeviceLocation()
                                viewModel.refreshSocialData()
                            },
                            onSetFeedFilter = viewModel::setFeedFilter,
                            onAddFriend = { code ->
                                requireGoogleAccount { viewModel.addRemoteFriend(code) }
                            },
                            onAcceptFriendRequest = { code ->
                                requireGoogleAccount { viewModel.acceptFriendRequest(code) }
                            },
                            onDeclineFriendRequest = { code ->
                                requireGoogleAccount { viewModel.declineFriendRequest(code) }
                            },
                            onChallengeFriend = { code ->
                                requireGoogleAccount {
                                    viewModel.startRemoteChallenge(code)
                                    navController.navigate("host")
                                }
                            },
                            onToggleShootRsvp = { id, attending ->
                                requireGoogleAccount { viewModel.toggleShootRsvp(id, attending) }
                            },
                            onLoadShootDetail = viewModel::loadShootAttendees,
                            onClearShootDetail = viewModel::clearShootDetail,
                            onCreateShoot = { title, location, region, description, start, end, public ->
                                requireGoogleAccount {
                                    viewModel.createShoot(
                                        title,
                                        location,
                                        region,
                                        description,
                                        start,
                                        end,
                                        public
                                    )
                                }
                            },
                            onCreatePost = { body, image, public ->
                                requireGoogleAccount { viewModel.createPost(body, image, public) }
                            },
                            onSaveProfile = { bio, club, bow, homeRange, location ->
                                requireGoogleAccount {
                                    viewModel.saveProfile(bio, club, bow, homeRange, location)
                                }
                            },
                            onClearProfile = viewModel::clearProfile,
                            onLoadFriendProfile = viewModel::loadFriendProfile,
                            onClearViewingFriendProfile = viewModel::clearViewingFriendProfile,
                            onAddProfileLink = { link ->
                                requireGoogleAccount { viewModel.handleFriendProfileLink(link) }
                            },
                            onNavigateToPlay = { tabNavigationRequest = MainTab.Play.route },
                            onRetryServerConnection = viewModel::retryServerConnection,
                            onRequestSignIn = { showGoogleSignInPrompt = true },
                            onSubscribePlus = { viewModel.openPaywall() }
                        )
                    },
                    playContent = {
                        PlayScreen(
                            playerName = state.playerName,
                            onNameChange = viewModel::setPlayerName,
                            isPlusActive = state.isPlusActive,
                            googleAccountLinked = state.googleAccountLinked,
                            serverReachable = state.serverReachable,
                            savedFriends = state.savedFriends,
                            remoteFriendCodes = remoteFriendCodes,
                            onHost = {
                                viewModel.startHosting()
                                navController.navigate("host")
                            },
                            onJoin = {
                                viewModel.startJoining()
                                navController.navigate("join")
                            },
                            onResyncJoin = { friendName ->
                                viewModel.resyncWithFriend(friendName)
                                navController.navigate("join")
                            },
                            onAddFriend = { code ->
                                requireGoogleAccount { viewModel.addRemoteFriend(code) }
                            },
                            onJoinInvite = { link ->
                                requireGoogleAccount {
                                    viewModel.joinFromInviteLink(link)
                                    navController.navigate("guest_lobby")
                                }
                            },
                            onRequestSignIn = { showGoogleSignInPrompt = true }
                        )
                    },
                    standingsContent = {
                        StandingsScreen(
                            isPlusActive = state.isPlusActive,
                            standingsMode = state.standingsMode,
                            localLeaderboard = state.globalLeaderboard,
                            onlineGlobalLeaderboard = state.onlineGlobalLeaderboard,
                            onlineFriendsLeaderboard = state.onlineFriendsLeaderboard,
                            socialLoading = state.socialLoading,
                            myFriendCode = state.friendCode,
                            remoteFriendCodes = remoteFriendCodes,
                            onStandingsModeChange = viewModel::setStandingsMode,
                            onRefresh = {
                                requireGoogleAccount { viewModel.refreshSocialData() }
                            },
                            onSubscribePlus = { viewModel.openPaywall() },
                            onRequestFriend = { code ->
                                requireGoogleAccount { viewModel.requestRemoteFriend(code) }
                            },
                            onChallengeFriend = { code ->
                                requireGoogleAccount {
                                    viewModel.startRemoteChallenge(code)
                                    navController.navigate("host")
                                }
                            }
                        )
                    },
                    historyContent = {
                        HistoryScreen(
                            playerName = state.playerName,
                            playerStats = state.playerGlobalStats,
                            matchHistory = state.matchHistory,
                            onRematch = {
                                viewModel.startRematchAsHost()
                                navController.navigate("host")
                            }
                        )
                    },
                    moreContent = {
                        MoreScreen(
                            friendCode = state.friendCode,
                            serverReachable = state.serverReachable,
                            removeAdsPrice = state.removeAdsPrice,
                            plusPrice = state.plusPrice,
                            isAdFree = state.isAdFree,
                            isPlusActive = state.isPlusActive,
                            billingReady = state.billingReady,
                            googleAccountLinked = state.googleAccountLinked,
                            googleAccountEmail = state.googleAccountEmail,
                            accountActionInProgress = state.accountActionInProgress,
                            onOpenPaywall = { viewModel.openPaywall() },
                            onOpenCustomerCenter = { viewModel.openCustomerCenter() },
                            onRestorePurchases = {
                                // logIn(Friend Code) → Play restore → CustomerInfo refresh (incl. RC promo grants)
                                viewModel.restorePurchases()
                            },
                            onJoinInvite = { link ->
                                requireGoogleAccount {
                                    viewModel.joinFromInviteLink(link)
                                    navController.navigate("guest_lobby")
                                }
                            },
                            onRetryServerConnection = viewModel::retryServerConnection,
                            onLinkGoogleAccount = viewModel::signInWithGoogleRequired
                        )
                    }
                )
            }
            composable("host") { SafeAreaScreen {
                HostScreen(
                    qrContent = viewModel.buildQrContent(),
                    players = state.players,
                    connectedCount = state.connectedCount,
                    statusMessage = state.statusMessage,
                    isRemoteMatch = state.isRemoteMatch,
                    remoteRoomId = state.remoteRoomId,
                    remoteJoinToken = state.remoteJoinToken,
                    remoteInviteLink = viewModel.buildRemoteInviteLink(),
                    hostName = state.playerName,
                    onContinue = { navController.navigate("setup") },
                    onLeave = {
                        viewModel.leaveMatch()
                        navController.navigate("main") { popUpTo(0) }
                    }
                )
            } }
            composable("join") { SafeAreaScreen {
                JoinScreen(
                    statusMessage = state.statusMessage,
                    connectionStatus = state.connectionStatus,
                    discoveredHosts = state.discoveredHosts,
                    connectingMatchId = if (state.connectionStatus == ConnectionStatus.Connecting) {
                        state.matchId
                    } else {
                        null
                    },
                    onHostSelected = viewModel::connectToDiscoveredHost,
                    onLeave = {
                        viewModel.leaveMatch()
                        navController.navigate("main") { popUpTo(0) }
                    },
                    onQrScanned = viewModel::onQrScanned
                )
            } }
            composable("guest_lobby") { SafeAreaScreen {
                GuestLobbyScreen(
                    hostName = state.hostName,
                    matchId = state.matchId,
                    players = state.players,
                    statusMessage = state.statusMessage,
                    onLeave = {
                        viewModel.leaveMatch()
                        navController.navigate("main") { popUpTo(0) }
                    }
                )
            } }
            composable("setup") { SafeAreaScreen {
                SetupScreen(
                    scoringType = state.scoringType,
                    onScoringTypeChange = viewModel::setScoringType,
                    targetCountInput = state.targetCountInput,
                    onTargetCountInputChange = viewModel::setTargetCountInput,
                    playerCount = state.players.size,
                    onBegin = {
                        viewModel.beginMatch()
                        navController.navigate("match")
                    },
                    onLeave = {
                        viewModel.leaveMatch()
                        navController.navigate("main") { popUpTo(0) }
                    }
                )
            } }
            composable("match") { SafeAreaScreen {
                MatchScreen(
                    scoringType = state.scoringType,
                    targetCount = state.targetCount,
                    selectedTarget = state.selectedTarget,
                    pendingScore = state.pendingScore,
                    playerTargetScores = viewModel.playerTargetScores(),
                    playerRunningTotal = viewModel.playerRunningTotal(),
                    statusMessage = state.statusMessage,
                    isHost = state.isHost,
                    matchLeaderboard = viewModel.matchLeaderboard(),
                    onTargetSelected = viewModel::selectTarget,
                    onPresetScore = viewModel::selectPresetScore,
                    onDigit = viewModel::appendDigit,
                    onClear = viewModel::clearPendingScore,
                    onSubmit = viewModel::submitScore,
                    onFinishMatch = viewModel::finishMatch,
                    onLeave = {
                        viewModel.leaveMatch()
                        navController.navigate("main") { popUpTo(0) }
                    }
                )
            } }
            composable("match_results") { SafeAreaScreen {
                MatchResultsScreen(
                    playerName = state.playerName,
                    scoringType = state.scoringType,
                    targetCount = state.targetCount,
                    playerTargetScores = viewModel.playerTargetScores(),
                    playerRunningTotal = viewModel.playerRunningTotal(),
                    matchLeaderboard = viewModel.matchLeaderboard(),
                    friendSuggestions = state.pendingFriendSuggestions,
                    requestedFriendCodes = state.requestedFriendCodes,
                    onRequestFriends = { codes ->
                        requireGoogleAccount { viewModel.addRemoteFriends(codes) }
                    },
                    onBackHome = {
                        viewModel.clearPendingFriendSuggestions()
                        viewModel.leaveMatch()
                        viewModel.refreshSocialData()
                        navController.navigate("main") { popUpTo(0) }
                    }
                )
            } }
        }
    }
}
