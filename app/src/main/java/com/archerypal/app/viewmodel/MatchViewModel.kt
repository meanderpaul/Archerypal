package com.archerypal.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.archerypal.app.billing.BillingProducts
import com.archerypal.app.billing.BillingManager
import com.archerypal.app.billing.PlusFeatures
import com.archerypal.app.billing.RevenueCatConfig
import com.revenuecat.purchases.CustomerInfo
import com.archerypal.app.data.AppConstants
import com.archerypal.app.data.AppJson
import com.archerypal.app.data.DiscoveredHost
import com.archerypal.app.data.LeaderboardRow
import com.archerypal.app.data.MatchPhase
import com.archerypal.app.data.MatchState
import com.archerypal.app.data.MessageType
import com.archerypal.app.data.PlayerGlobalStats
import com.archerypal.app.data.PlayerInfo
import com.archerypal.app.data.QrPayload
import com.archerypal.app.data.SavedFriend
import com.archerypal.app.data.SavedMatchRecord
import com.archerypal.app.data.ScoreEntry
import com.archerypal.app.data.ScoringType
import com.archerypal.app.data.PersistedAppData
import com.archerypal.app.data.PersistenceRepository
import com.archerypal.app.data.GeoPoint
import com.archerypal.app.data.LocationHelper
import com.archerypal.app.data.generateMatchId
import com.archerypal.app.data.onlineLeaderboardRows
import com.archerypal.app.data.matchLeaderboardRows
import com.archerypal.app.data.parseScoringType
import com.archerypal.app.data.rankGlobalStats
import com.archerypal.app.data.rankMatchTotals
import com.archerypal.app.data.scoreKey
import com.archerypal.app.p2p.MatchTransport
import com.archerypal.app.p2p.NearbyMatchTransport
import com.archerypal.app.p2p.P2PEvent
import com.archerypal.app.p2p.RemoteEndpoints
import com.archerypal.app.p2p.RemoteMatchTransport
import com.archerypal.app.remote.CompleteMatchPlayer
import com.archerypal.app.remote.CompleteMatchRequest
import com.archerypal.app.remote.DeviceCredentials
import com.archerypal.app.remote.MatchServerApi
import com.archerypal.app.remote.MatchServerConfig
import com.archerypal.app.remote.PostImagePreparer
import com.archerypal.app.remote.CreateShootRequest
import com.archerypal.app.remote.FeedFilter
import com.archerypal.app.remote.FeedItem
import com.archerypal.app.remote.IncomingChallenge
import com.archerypal.app.remote.MutualFriendSuggestion
import com.archerypal.app.remote.PlayerProfile
import com.archerypal.app.remote.PlayerSearchResult
import com.archerypal.app.remote.ProfileUpdateRequest
import com.archerypal.app.remote.RemoteComment
import com.archerypal.app.remote.RemoteFriend
import com.archerypal.app.remote.RemoteFriendRequest
import com.archerypal.app.remote.RemoteShoot
import com.archerypal.app.remote.ShootAttendee
import com.archerypal.app.data.globalLeaderboardRows
import com.archerypal.app.remote.RemoteIdentityService
import com.archerypal.app.remote.GoogleAuthService
import com.archerypal.app.remote.GoogleSignInResult
import com.archerypal.app.remote.SyncEntitlementRequest
import com.archerypal.app.push.PushTokenRegistrar
import com.archerypal.app.push.PushTokenStore
import android.net.Uri
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class ConnectionStatus {
    Idle,
    Discovering,
    Connecting,
    Connected,
    Failed
}

data class UiState(
    val playerName: String = "",
    val isHost: Boolean = false,
    val matchId: String = "",
    val phase: MatchPhase = MatchPhase.LOBBY,
    val targetCount: Int = 0,
    val targetCountInput: String = "",
    val scoringType: ScoringType = ScoringType.ASA,
    val players: List<PlayerInfo> = emptyList(),
    val scores: List<ScoreEntry> = emptyList(),
    val selectedTarget: Int = 1,
    val pendingScore: String = "",
    val discoveredHosts: List<DiscoveredHost> = emptyList(),
    val connectedCount: Int = 0,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Idle,
    val hostName: String = "",
    val statusMessage: String = "",
    val errorMessage: String? = null,
    val hostEndpointId: String? = null,
    val pendingQueue: List<ScoreEntry> = emptyList(),
    val savedFriends: List<SavedFriend> = emptyList(),
    val recentMatch: SavedMatchRecord? = null,
    val lastMatchGroup: List<String> = emptyList(),
    val globalLeaderboard: List<LeaderboardRow> = emptyList(),
    val resyncHostName: String? = null,
    val isRematch: Boolean = false,
    val isAdFree: Boolean = false,
    val isPlusActive: Boolean = false,
    val removeAdsPrice: String? = null,
    val plusPrice: String? = null,
    val billingReady: Boolean = false,
    val billingMessage: String? = null,
    val showPaywall: Boolean = false,
    val showCustomerCenter: Boolean = false,
    val friendCode: String? = null,
    val serverReachable: Boolean? = null,
    val isRemoteMatch: Boolean = false,
    val remoteRoomId: String? = null,
    val remoteJoinToken: String? = null,
    /** Friend this device challenged (host side). */
    val remoteOpponentName: String? = null,
    val remoteOpponentNotified: Boolean = false,
    val incomingChallenges: List<IncomingChallenge> = emptyList(),
    val matchHistory: List<SavedMatchRecord> = emptyList(),
    val remoteFriends: List<RemoteFriend> = emptyList(),
    val onlineGlobalLeaderboard: List<LeaderboardRow> = emptyList(),
    val onlineFriendsLeaderboard: List<LeaderboardRow> = emptyList(),
    val socialLoading: Boolean = false,
    val standingsMode: StandingsMode = StandingsMode.FRIENDS,
    val playerGlobalStats: PlayerGlobalStats? = null,
    val pendingInviteLink: String? = null,
    val visibleShoots: List<RemoteShoot> = emptyList(),
    val selectedShootAttendees: List<ShootAttendee> = emptyList(),
    val shootDetailShootId: String? = null,
    val shootDetailLoading: Boolean = false,
    val pendingFriendRequests: List<RemoteFriendRequest> = emptyList(),
    val sentFriendRequests: List<RemoteFriendRequest> = emptyList(),
    val mutualFriendSuggestions: List<MutualFriendSuggestion> = emptyList(),
    val friendSearchQuery: String = "",
    val friendSearchResults: List<PlayerSearchResult> = emptyList(),
    val friendSearchLoading: Boolean = false,
    val friendSearchError: String? = null,
    val pendingFriendSuggestions: List<FriendSuggestion> = emptyList(),
    val requestedFriendCodes: Set<String> = emptySet(),
    val shootFriendSuggestions: List<FriendSuggestion> = emptyList(),
    val pendingProfileFriendCode: String? = null,
    val pendingProfileFriendName: String? = null,
    val feedItems: List<FeedItem> = emptyList(),
    val feedFilter: FeedFilter = FeedFilter.FRIEND_POSTS,
    val feedLoading: Boolean = false,
    val feedNewCounts: Map<FeedFilter, Int> = emptyMap(),
    /** Loaded comment threads keyed by bare post id. */
    val postComments: Map<String, List<RemoteComment>> = emptyMap(),
    val commentsLoadingPostIds: Set<String> = emptySet(),
    val commentSendingPostIds: Set<String> = emptySet(),
    val myProfile: PlayerProfile? = null,
    val viewingFriendProfile: PlayerProfile? = null,
    val deviceLocation: GeoPoint? = null,
    val googleAccountLinked: Boolean = false,
    val googleAccountEmail: String? = null,
    val showAccountOnboarding: Boolean = false,
    val accountActionInProgress: Boolean = false
)

enum class StandingsMode {
    LOCAL, GLOBAL, FRIENDS
}

data class FriendSuggestion(
    val name: String,
    val friendCode: String
)

class MatchViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val IDENTITY_TTL_MS = 5 * 60 * 1000L
        private const val MAX_SHOOTS_FOR_SUGGESTIONS = 5
        private const val FRIEND_SEARCH_MIN_CHARS = 2
        private const val FRIEND_SEARCH_DEBOUNCE_MS = 300L
    }

    private val nearbyTransport = NearbyMatchTransport(application)
    private var matchTransport: MatchTransport = nearbyTransport
    private var transportEventsJob: Job? = null
    private var transportEndpointsJob: Job? = null
    private val persistence = PersistenceRepository(application)
    private val matchServerApi = MatchServerApi()

    private fun deviceCreds(data: PersistedAppData = persistedData): DeviceCredentials? {
        val code = data.friendCode?.takeIf { it.isNotBlank() } ?: return null
        val secret = data.deviceSecret?.takeIf { it.isNotBlank() } ?: return null
        val deviceId = data.deviceId?.takeIf { it.isNotBlank() } ?: return null
        return DeviceCredentials(friendCode = code, deviceId = deviceId, deviceSecret = secret)
    }
    private val remoteIdentity = RemoteIdentityService(persistence, matchServerApi)
    private val googleAuthService = GoogleAuthService(application)
    private val locationHelper = LocationHelper(application)
    private val billingManager = BillingManager(
        context = application,
        scope = viewModelScope,
        onAdFreeChanged = { owned -> persistence.setAdFree(owned) },
        onPlusGranted = { active, sku, token ->
            persistence.setPlusActive(active)
            if (active) syncPlusEntitlement(sku, token)
        }
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val endpointNames = mutableMapOf<String, String>()
    private val endpointFriendCodes = mutableMapOf<String, String>()
    private var connectingEndpointId: String? = null
    private var persistedData = PersistedAppData()
    private var lastSyncedPushToken: Pair<String, String>? = null
    private var socialRefreshJob: Job? = null
    private var friendSearchJob: Job? = null
    private var identityEnsureJob: Job? = null
    private var lastIdentitySuccessAtMs: Long = 0L
    private var lastGoogleStatusAtMs: Long = 0L

    init {
        billingManager.startConnection()
        PushTokenRegistrar.refresh(application)

        viewModelScope.launch {
            PushTokenStore.latestToken.collect { token ->
                if (!token.isNullOrBlank()) syncPushToken(token)
            }
        }

        viewModelScope.launch {
            billingManager.isAdFree.collect { isAdFree ->
                _uiState.update { it.copy(isAdFree = isAdFree) }
            }
        }
        viewModelScope.launch {
            billingManager.isPlusActive.collect { isPlusActive ->
                val effectivePlus = PlusFeatures.hasPlusAccess(isPlusActive)
                _uiState.update { it.copy(isPlusActive = effectivePlus) }
                // Entitlement sync only — social loads when Social/Standings opens.
                if (effectivePlus) {
                    syncPlusEntitlementFromBilling()
                }
            }
        }
        viewModelScope.launch {
            billingManager.removeAdsPrice.collect { price ->
                _uiState.update { it.copy(removeAdsPrice = price) }
            }
        }
        viewModelScope.launch {
            billingManager.plusPrice.collect { price ->
                _uiState.update { it.copy(plusPrice = price) }
            }
        }
        viewModelScope.launch {
            billingManager.billingReady.collect { ready ->
                _uiState.update { it.copy(billingReady = ready) }
            }
        }
        viewModelScope.launch {
            billingManager.billingMessage.collect { message ->
                _uiState.update { it.copy(billingMessage = message) }
            }
        }

        viewModelScope.launch {
            remoteIdentity.migrateStorageIfNeeded()
            evaluateAccountOnboarding()
        }

        viewModelScope.launch {
            persistence.dataFlow.collect { data ->
                persistedData = data
                billingManager.setCustomerDisplayName(data.playerName)
                data.friendCode?.takeIf { it.isNotBlank() }?.let { billingManager.logIn(it) }
                billingManager.setCachedAdFree(data.isAdFree)
                billingManager.setCachedPlusActive(data.isPlusActive)
                _uiState.update { state ->
                    state.copy(
                        playerName = state.playerName.ifBlank { data.playerName },
                        savedFriends = data.friends.sortedByDescending { it.lastSeenAt },
                        recentMatch = data.matchHistory.firstOrNull(),
                        matchHistory = data.matchHistory,
                        playerGlobalStats = data.globalStats[data.playerName]
                            ?: data.globalStats.values.firstOrNull {
                                it.name.equals(data.playerName, ignoreCase = true)
                            },
                        lastMatchGroup = data.lastMatchGroup,
                        globalLeaderboard = globalLeaderboardRows(rankGlobalStats(data.globalStats.values)),
                        isAdFree = billingManager.isAdFree.value,
                        isPlusActive = PlusFeatures.hasPlusAccess(data.isPlusActive || state.isPlusActive),
                        friendCode = data.friendCode ?: state.friendCode,
                        googleAccountLinked = data.googleAccountLinked,
                        googleAccountEmail = data.googleAccountEmail
                    )
                }
                evaluateAccountOnboarding()
            }
        }

        bindTransport(nearbyTransport)
    }

    private fun bindTransport(transport: MatchTransport) {
        transportEventsJob?.cancel()
        transportEndpointsJob?.cancel()
        matchTransport = transport
        transportEventsJob = viewModelScope.launch {
            transport.events.collect { event ->
                when (event) {
                    is P2PEvent.EndpointConnected -> handleEndpointConnected(event.endpointId, event.name)
                    is P2PEvent.EndpointDisconnected -> handleEndpointDisconnected(event.endpointId)
                    is P2PEvent.MessageReceived -> handleMessage(event.endpointId, event.message)
                    is P2PEvent.HostDiscovered -> addDiscoveredHost(event.host)
                    is P2PEvent.Error -> {
                        connectingEndpointId = null
                        _uiState.update {
                            it.copy(
                                errorMessage = event.message,
                                connectionStatus = if (!it.isHost && it.hostEndpointId == null) {
                                    ConnectionStatus.Failed
                                } else {
                                    it.connectionStatus
                                }
                            )
                        }
                    }
                    P2PEvent.AdvertisingStarted -> _uiState.update {
                        it.copy(
                            statusMessage = advertisingMessage(it),
                            connectionStatus = ConnectionStatus.Connected
                        )
                    }
                    P2PEvent.DiscoveryStarted -> _uiState.update {
                        it.copy(
                            statusMessage = discoveryMessage(it),
                            connectionStatus = ConnectionStatus.Discovering
                        )
                    }
                }
            }
        }
        transportEndpointsJob = viewModelScope.launch {
            transport.connectedEndpoints.collect { endpoints ->
                _uiState.update { it.copy(connectedCount = endpoints.size) }
            }
        }
    }

    fun setPlayerName(name: String) {
        val trimmed = name.trim()
        _uiState.update { it.copy(playerName = trimmed) }
        if (trimmed.isNotBlank()) {
            viewModelScope.launch {
                persistence.savePlayerName(trimmed)
                ensureServerIdentity(force = true)
            }
        }
    }

    fun setStandingsMode(mode: StandingsMode) {
        _uiState.update { it.copy(standingsMode = mode) }
    }

    fun refreshSocialData() {
        if (socialRefreshJob?.isActive == true) return
        socialRefreshJob = viewModelScope.launch {
            remoteIdentity.ensureDeviceIdBound()
            val creds = deviceCreds(persistedData) ?: return@launch
            _uiState.update { it.copy(socialLoading = true) }
            try {
                matchServerApi.listFriends(creds)
                    .onSuccess { response ->
                        _uiState.update { it.copy(remoteFriends = response.friends) }
                        response.friends.forEach { rememberRemoteFriend(it) }
                    }
                if (_uiState.value.isPlusActive) {
                    matchServerApi.globalLeaderboard(creds)
                        .onSuccess { response ->
                            _uiState.update {
                                it.copy(onlineGlobalLeaderboard = onlineLeaderboardRows(response.entries))
                            }
                        }
                }
                matchServerApi.friendsLeaderboard(creds)
                    .onSuccess { response ->
                        _uiState.update {
                            it.copy(onlineFriendsLeaderboard = onlineLeaderboardRows(response.entries))
                        }
                    }
                matchServerApi.listShoots(creds)
                    .onSuccess { response ->
                        _uiState.update { it.copy(visibleShoots = response.shoots) }
                        refreshShootFriendSuggestions(creds, response.shoots)
                    }
                matchServerApi.listFriendRequests(creds)
                    .onSuccess { response ->
                        _uiState.update { it.copy(pendingFriendRequests = response.requests) }
                    }
                matchServerApi.listOutgoingFriendRequests(creds)
                    .onSuccess { response ->
                        _uiState.update { state ->
                            state.copy(
                                sentFriendRequests = response.requests,
                                requestedFriendCodes = state.requestedFriendCodes +
                                    response.requests.map { it.toCode.uppercase() }
                            )
                        }
                    }
                matchServerApi.friendSuggestions(creds)
                    .onSuccess { response ->
                        _uiState.update { it.copy(mutualFriendSuggestions = response.suggestions) }
                    }
                matchServerApi.incomingChallenges(creds)
                    .onSuccess { response ->
                        _uiState.update { it.copy(incomingChallenges = response.challenges) }
                    }
                refreshFeedInternal()
            } finally {
                _uiState.update { it.copy(socialLoading = false) }
            }
        }
    }

    fun refreshFeed() {
        val data = persistedData
        val myCode = data.friendCode ?: return
        val secret = data.deviceSecret ?: return
        val creds = deviceCreds(data) ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(feedLoading = true) }
            updateDeviceLocation()
            refreshFeedInternal()
            _uiState.update { it.copy(feedLoading = false) }
        }
    }

    private suspend fun refreshFeedInternal() {
        val creds = deviceCreds(persistedData) ?: return
        val loc = _uiState.value.deviceLocation
        matchServerApi.getMyProfile(creds)
            .onSuccess { response ->
                _uiState.update { it.copy(myProfile = response.profile) }
            }
        val activeFilter = _uiState.value.feedFilter
        val lastSeen = persistedData.feedLastSeenAt
        val counts = mutableMapOf<FeedFilter, Int>()
        var activeItems: List<FeedItem>? = null
        // One GET per filter — reuse for both feed items and badge counts.
        FeedFilter.entries.forEach { filter ->
            matchServerApi.getFeed(
                creds = creds,
                filter = filter,
                lat = loc?.latitude,
                lng = loc?.longitude
            ).onSuccess { response ->
                if (filter == activeFilter) {
                    activeItems = response.items
                }
                val seenAt = lastSeen[filter.name] ?: 0L
                val newCount = response.items.count { it.createdAt > seenAt }
                if (filter == FeedFilter.ALL || filter == FeedFilter.FRIEND_POSTS) {
                    val requestCount = _uiState.value.pendingFriendRequests.count {
                        it.createdAtMs > seenAt
                    }
                    counts[filter] = newCount + requestCount
                } else {
                    counts[filter] = newCount
                }
            }
        }
        _uiState.update {
            it.copy(
                feedItems = activeItems ?: it.feedItems,
                feedNewCounts = counts
            )
        }
    }

    fun setFeedFilter(filter: FeedFilter) {
        _uiState.update { it.copy(feedFilter = filter) }
        viewModelScope.launch {
            persistence.updateFeedLastSeen(filter.name, System.currentTimeMillis())
            val data = persistedData
            val myCode = data.friendCode ?: return@launch
            val secret = data.deviceSecret ?: return@launch
            val creds = deviceCreds(data) ?: return@launch
            val loc = _uiState.value.deviceLocation
            matchServerApi.getFeed(
                creds = creds, 
                filter = filter,
                lat = loc?.latitude,
                lng = loc?.longitude
            ).onSuccess { response ->
                _uiState.update {
                    it.copy(
                        feedItems = response.items,
                        feedNewCounts = it.feedNewCounts + (filter to 0)
                    )
                }
            }
        }
    }

    fun updateDeviceLocation() {
        viewModelScope.launch {
            val point = locationHelper.getCurrentLocation()
            _uiState.update { it.copy(deviceLocation = point) }
        }
    }

    fun loadMyProfile() {
        val data = persistedData
        val myCode = data.friendCode ?: return
        val secret = data.deviceSecret ?: return
        val creds = deviceCreds(data) ?: return
        viewModelScope.launch {
            matchServerApi.getMyProfile(creds)
                .onSuccess { response ->
                    _uiState.update { it.copy(myProfile = response.profile) }
                }
        }
    }

    fun saveProfile(
        bio: String,
        club: String,
        bowType: String,
        homeRange: String,
        locationLabel: String
    ) {
        val data = persistedData
        val myCode = data.friendCode ?: return
        val secret = data.deviceSecret ?: return
        val creds = deviceCreds(data) ?: return
        viewModelScope.launch {
            matchServerApi.updateMyProfile(creds, 
                ProfileUpdateRequest(bio, club, bowType, homeRange, locationLabel)
            ).onSuccess { response ->
                _uiState.update {
                    it.copy(myProfile = response.profile, statusMessage = "Profile saved")
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "Could not save profile")
                }
            }
        }
    }

    fun setDiscoverable(discoverable: Boolean) {
        val creds = deviceCreds(persistedData) ?: return
        val name = _uiState.value.playerName.ifBlank { persistedData.playerName }
        if (name.isBlank()) return
        val previous = _uiState.value.myProfile
        _uiState.update { it.copy(myProfile = it.myProfile?.copy(discoverable = discoverable)) }
        viewModelScope.launch {
            matchServerApi.setDiscoverable(creds, name, discoverable)
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            myProfile = previous,
                            errorMessage = error.message ?: "Could not update search visibility"
                        )
                    }
                }
        }
    }

    fun onFriendSearchQueryChange(query: String) {
        _uiState.update { it.copy(friendSearchQuery = query, friendSearchError = null) }
        friendSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < FRIEND_SEARCH_MIN_CHARS) {
            _uiState.update { it.copy(friendSearchResults = emptyList(), friendSearchLoading = false) }
            return
        }
        friendSearchJob = viewModelScope.launch {
            delay(FRIEND_SEARCH_DEBOUNCE_MS)
            val creds = deviceCreds(persistedData)
            if (creds == null) {
                _uiState.update {
                    it.copy(friendSearchError = "Connect to the server to search", friendSearchLoading = false)
                }
                return@launch
            }
            _uiState.update { it.copy(friendSearchLoading = true) }
            matchServerApi.searchPlayers(creds, trimmed)
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(friendSearchResults = response.results, friendSearchLoading = false)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            friendSearchError = error.message ?: "Search failed",
                            friendSearchLoading = false
                        )
                    }
                }
        }
    }

    fun clearProfile() {
        val data = persistedData
        val myCode = data.friendCode ?: return
        val secret = data.deviceSecret ?: return
        val creds = deviceCreds(data) ?: return
        viewModelScope.launch {
            matchServerApi.clearMyProfile(creds)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            myProfile = it.myProfile?.copy(
                                bio = "",
                                club = "",
                                bowType = "",
                                homeRange = "",
                                locationLabel = ""
                            ),
                            statusMessage = "Profile cleared"
                        )
                    }
                    loadMyProfile()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Could not clear profile")
                    }
                }
        }
    }

    fun loadFriendProfile(friendCode: String) {
        val data = persistedData
        val myCode = data.friendCode ?: return
        val secret = data.deviceSecret ?: return
        val creds = deviceCreds(data) ?: return
        viewModelScope.launch {
            matchServerApi.getFriendProfile(creds, friendCode)
                .onSuccess { response ->
                    _uiState.update { it.copy(viewingFriendProfile = response.profile) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Could not load profile")
                    }
                }
        }
    }

    fun clearViewingFriendProfile() {
        _uiState.update { it.copy(viewingFriendProfile = null) }
    }

    fun createPost(body: String, imageUri: String?, isPublic: Boolean) {
        if (!_uiState.value.isPlusActive) {
            _uiState.update { it.copy(errorMessage = "Archerypal Plus is required to post") }
            return
        }
        if (!imageUri.isNullOrBlank() && !PlusFeatures.PHOTO_POSTS_ENABLED) {
            _uiState.update { it.copy(errorMessage = "Photo posts aren't available yet — try a text-only post.") }
            return
        }
        val data = persistedData
        val myCode = data.friendCode
        val secret = data.deviceSecret
        val creds = deviceCreds(data) ?: return
        if (myCode.isNullOrBlank() || secret.isNullOrBlank()) {
            _uiState.update {
                it.copy(errorMessage = "Connect to the server first (set your name and create an account)")
            }
            ensureServerIdentity(force = true)
            return
        }
        viewModelScope.launch {
            val imageFile = if (imageUri.isNullOrBlank()) {
                null
            } else {
                withContext(Dispatchers.IO) {
                    PostImagePreparer.prepare(getApplication(), Uri.parse(imageUri))
                }.also { prepared ->
                    if (prepared == null) {
                        _uiState.update {
                            it.copy(errorMessage = "Could not prepare photo. Try a different image.")
                        }
                    }
                }
            }
            if (!imageUri.isNullOrBlank() && imageFile == null) return@launch

            // Best-effort: push a Play-verified token before the Plus-gated create.
            syncPlusEntitlementOnServer(creds)

            val location = if (isPublic) _uiState.value.deviceLocation else null
            matchServerApi.createPost(
                creds = creds,
                body = body,
                imageFile = imageFile,
                isPublic = isPublic,
                latitude = location?.latitude,
                longitude = location?.longitude
            )
                .onSuccess {
                    val filter = _uiState.value.feedFilter
                    if (filter != FeedFilter.FRIEND_POSTS) {
                        setFeedFilter(FeedFilter.FRIEND_POSTS)
                    } else {
                        refreshFeed()
                    }
                    _uiState.update { it.copy(statusMessage = "Post shared") }
                }
                .onFailure { error ->
                    val msg = error.message.orEmpty()
                    val friendly = when {
                        msg.contains("Plus required", ignoreCase = true) ->
                            "Archerypal Plus could not be verified on the server. " +
                                "Open More → restore purchases, then try again."
                        msg.contains("photo posts", ignoreCase = true) ->
                            "Photo posts aren't available yet — try a text-only post."
                        else -> error.message ?: "Could not create post"
                    }
                    _uiState.update { it.copy(errorMessage = friendly) }
                }
            imageFile?.delete()
        }
    }

    fun loadComments(postId: String) {
        val creds = deviceCreds(persistedData) ?: return
        if (postId in _uiState.value.commentsLoadingPostIds) return
        _uiState.update { it.copy(commentsLoadingPostIds = it.commentsLoadingPostIds + postId) }
        viewModelScope.launch {
            matchServerApi.listComments(creds, postId)
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            postComments = it.postComments + (postId to response.comments),
                            feedItems = it.feedItems.withCommentCount(postId, response.comments.size)
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Could not load comments")
                    }
                }
            _uiState.update { it.copy(commentsLoadingPostIds = it.commentsLoadingPostIds - postId) }
        }
    }

    fun addComment(postId: String, body: String) {
        val text = body.trim()
        if (text.isEmpty()) return
        if (!_uiState.value.isPlusActive) {
            _uiState.update { it.copy(errorMessage = "Archerypal Plus is required to comment") }
            return
        }
        val creds = deviceCreds(persistedData)
        if (creds == null) {
            _uiState.update { it.copy(errorMessage = "Connect to the server to comment") }
            return
        }
        _uiState.update { it.copy(commentSendingPostIds = it.commentSendingPostIds + postId) }
        viewModelScope.launch {
            // Best-effort: push a Play-verified token before the Plus-gated create.
            syncPlusEntitlementOnServer(creds)
            matchServerApi.createComment(creds, postId, text)
                .onSuccess { response ->
                    _uiState.update {
                        val thread = it.postComments[postId].orEmpty() + response.comment
                        it.copy(
                            postComments = it.postComments + (postId to thread),
                            feedItems = it.feedItems.withCommentCount(postId, thread.size)
                        )
                    }
                }
                .onFailure { error ->
                    val msg = error.message.orEmpty()
                    val friendly = if (msg.contains("Plus required", ignoreCase = true)) {
                        "Archerypal Plus could not be verified on the server. " +
                            "Open More → restore purchases, then try again."
                    } else {
                        error.message ?: "Could not post comment"
                    }
                    _uiState.update { it.copy(errorMessage = friendly) }
                }
            _uiState.update { it.copy(commentSendingPostIds = it.commentSendingPostIds - postId) }
        }
    }

    fun deleteComment(postId: String, commentId: String) {
        val creds = deviceCreds(persistedData) ?: return
        val previous = _uiState.value.postComments[postId] ?: return
        val remaining = previous.filterNot { it.id == commentId }
        _uiState.update {
            it.copy(
                postComments = it.postComments + (postId to remaining),
                feedItems = it.feedItems.withCommentCount(postId, remaining.size)
            )
        }
        viewModelScope.launch {
            matchServerApi.deleteComment(creds, postId, commentId)
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            postComments = it.postComments + (postId to previous),
                            feedItems = it.feedItems.withCommentCount(postId, previous.size),
                            errorMessage = error.message ?: "Could not delete comment"
                        )
                    }
                }
        }
    }

    private fun List<FeedItem>.withCommentCount(postId: String, count: Int): List<FeedItem> =
        map { item -> if (item.postId == postId) item.copy(commentCount = count) else item }

    fun mediaAuthHeaders(): Pair<String?, String?> {
        val data = persistedData
        return data.deviceId to data.deviceSecret
    }

    fun deviceIdForMedia(): String? = persistedData.deviceId

    fun deviceSecretForMedia(): String? = persistedData.deviceSecret

    fun joinFromInviteLink(raw: String) {
        val parsed = com.archerypal.app.data.RemoteInviteLink.extractFromText(raw)
        if (parsed == null) {
            _uiState.update { it.copy(errorMessage = "Could not read that invite link or room code") }
            return
        }
        joinRemoteMatch(parsed.roomId, parsed.token)
    }

    fun buildRemoteInviteLink(): String? {
        val roomId = _uiState.value.remoteRoomId ?: return null
        return com.archerypal.app.data.RemoteInviteLink.build(roomId)
    }

    /** Lightweight refresh for the Play tab: pending challenges plus the friend list to challenge from. */
    fun refreshRemotePlayData() {
        val creds = deviceCreds() ?: return
        viewModelScope.launch {
            matchServerApi.incomingChallenges(creds).onSuccess { response ->
                _uiState.update { it.copy(incomingChallenges = response.challenges) }
            }
            if (_uiState.value.remoteFriends.isEmpty()) {
                matchServerApi.listFriends(creds).onSuccess { response ->
                    _uiState.update { it.copy(remoteFriends = response.friends) }
                }
            }
        }
    }

    private fun syncPlusEntitlement(sku: String?, purchaseToken: String?) {
        val resolvedSku = sku?.takeIf { BillingProducts.isPlusSku(it) } ?: BillingProducts.PLUS_MONTHLY
        val data = persistedData
        val creds = deviceCreds(data) ?: return
        viewModelScope.launch {
            syncPlusEntitlementOnServer(creds, resolvedSku, purchaseToken)
        }
    }

    /** Re-sync Plus after RevenueCat CustomerInfo / identity changes. */
    private fun syncPlusEntitlementFromBilling() {
        if (PlusFeatures.PLUS_PAYWALL_DISABLED_FOR_TESTING) {
            syncDevPlusEntitlementIfNeeded()
            return
        }
        val (sku, token) = billingManager.plusCredentials()
        if (sku == null && token == null) return
        syncPlusEntitlement(sku, token)
    }

    /**
     * Best-effort client mirror of Plus. Production Workers reject this unless
     * ALLOW_UNVERIFIED_ENTITLEMENT_SYNC is set; RevenueCat webhook is the source of truth.
     */
    private suspend fun syncPlusEntitlementOnServer(
        creds: DeviceCredentials,
        sku: String? = null,
        purchaseToken: String? = null
    ): Result<Unit> {
        if (PlusFeatures.PLUS_PAYWALL_DISABLED_FOR_TESTING) {
            return syncDevPlusEntitlementOnServer(creds)
        }
        val (billingSku, billingToken) = billingManager.plusCredentials()
        val resolvedToken = purchaseToken?.takeIf { it.isNotBlank() } ?: billingToken
        val resolvedSku = sku?.takeIf { BillingProducts.isPlusSku(it) }
            ?: billingSku?.takeIf { BillingProducts.isPlusSku(it) }
            ?: BillingProducts.PLUS_MONTHLY
        if (!resolvedToken.isNullOrBlank()) {
            return matchServerApi.syncEntitlement(
                creds = creds,
                body = SyncEntitlementRequest(
                    sku = resolvedSku,
                    purchaseToken = resolvedToken
                )
            ).map { }
        }
        // RevenueCat promo / entitlement without a Play purchase token (e.g. gifted lifetime).
        val marker = "rc:${creds.friendCode}:$resolvedSku"
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(marker.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val expiresAt =
            if (resolvedSku == BillingProducts.PLUS_LIFETIME) null
            else System.currentTimeMillis() / 1000 + 35L * 24 * 60 * 60
        return matchServerApi.syncEntitlement(
            creds = creds,
            body = SyncEntitlementRequest(
                sku = resolvedSku,
                purchaseTokenHash = hash,
                expiresAt = expiresAt
            )
        ).map { }
    }

    /** When the client Plus paywall is disabled for dev, mirror that on the server. */
    private fun syncDevPlusEntitlementIfNeeded() {
        if (!PlusFeatures.PLUS_PAYWALL_DISABLED_FOR_TESTING) return
        val creds = deviceCreds() ?: return
        viewModelScope.launch {
            syncDevPlusEntitlementOnServer(creds)
        }
    }

    private suspend fun syncDevPlusEntitlementOnServer(creds: DeviceCredentials): Result<Unit> {
        if (!PlusFeatures.PLUS_PAYWALL_DISABLED_FOR_TESTING) return Result.success(Unit)
        val token = "dev-plus-bypass:${creds.friendCode}"
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val expiresAt = System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000
        return matchServerApi.syncEntitlement(
            creds = creds,
            body = SyncEntitlementRequest(
                sku = BillingProducts.PLUS_MONTHLY,
                purchaseTokenHash = hash,
                expiresAt = expiresAt
            )
        ).map { }
    }

    fun consumePendingInvite() {
        _uiState.update { it.copy(pendingInviteLink = null) }
    }

    fun setPendingInviteLink(link: String?) {
        if (link.isNullOrBlank()) return
        _uiState.update { it.copy(pendingInviteLink = link) }
    }

    fun ensureServerIdentity(force: Boolean = false) {
        val name = _uiState.value.playerName.trim()
        val now = System.currentTimeMillis()
        if (!force &&
            persistedData.serverRegistered &&
            lastIdentitySuccessAtMs > 0L &&
            now - lastIdentitySuccessAtMs < IDENTITY_TTL_MS
        ) {
            _uiState.update { it.copy(serverReachable = true) }
            return
        }
        if (identityEnsureJob?.isActive == true) {
            if (!force) return
            identityEnsureJob?.cancel()
        }
        identityEnsureJob = viewModelScope.launch {
            _uiState.update { it.copy(serverReachable = null) }
            val reachable = remoteIdentity.checkServerReachable()
            _uiState.update { it.copy(serverReachable = reachable) }
            if (!reachable || name.isBlank()) return@launch
            if (!remoteIdentity.hasLocalIdentity()) return@launch
            remoteIdentity.ensureDeviceIdBound()
            remoteIdentity.ensureRegistered(name).onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "Could not register with server")
                }
            }.onSuccess { data ->
                lastIdentitySuccessAtMs = System.currentTimeMillis()
                _uiState.update { it.copy(friendCode = data.friendCode, serverReachable = true) }
                refreshAndSyncPushToken(data)
                val statusFresh = lastGoogleStatusAtMs > 0L &&
                    System.currentTimeMillis() - lastGoogleStatusAtMs < IDENTITY_TTL_MS
                if (force || !statusFresh) {
                    remoteIdentity.refreshGoogleAccountStatus().onSuccess { refreshed ->
                        lastGoogleStatusAtMs = System.currentTimeMillis()
                        _uiState.update {
                            it.copy(
                                googleAccountLinked = refreshed.googleAccountLinked,
                                googleAccountEmail = refreshed.googleAccountEmail
                            )
                        }
                    }
                }
            }
        }
    }

    fun retryServerConnection() {
        ensureServerIdentity(force = true)
    }

    private fun refreshAndSyncPushToken(identity: PersistedAppData = persistedData) {
        PushTokenRegistrar.refresh(getApplication())
        val token = PushTokenStore.token ?: return
        viewModelScope.launch {
            syncPushToken(token, identity)
        }
    }

    private suspend fun syncPushToken(
        token: String,
        identity: PersistedAppData = persistedData
    ) {
        remoteIdentity.ensureDeviceIdBound()
        val creds = deviceCreds(identity) ?: deviceCreds(persistedData) ?: return
        if (!identity.serverRegistered && !persistedData.serverRegistered) return
        val displayName = identity.playerName.trim()
            .ifBlank { _uiState.value.playerName.trim() }
        if (displayName.isBlank()) return
        val syncKey = creds.friendCode to token
        if (lastSyncedPushToken == syncKey) return

        matchServerApi.updatePlayerMe(
            creds = creds,
            displayName = displayName,
            fcmToken = token
        ).onSuccess {
            lastSyncedPushToken = syncKey
        }
    }

    fun dismissAccountOnboarding() {
        _uiState.update { it.copy(showAccountOnboarding = false) }
    }

    fun signInWithGoogleRequired() {
        val name = _uiState.value.playerName.trim().ifBlank { persistedData.playerName.trim() }
        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter your display name first") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(accountActionInProgress = true, playerName = name) }
            if (name != persistedData.playerName) {
                persistence.savePlayerName(name)
            }
            googleAuthService.signIn().fold(
                onSuccess = { result -> completeGoogleSignIn(name, result) },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            errorMessage = mapGoogleAuthError(error),
                            accountActionInProgress = false
                        )
                    }
                }
            )
        }
    }

    private suspend fun completeGoogleSignIn(displayName: String, signIn: GoogleSignInResult) {
        remoteIdentity.signInWithGoogle(displayName, signIn.idToken)
            .onSuccess { data ->
                billingManager.setCustomerEmail(data.friendCode, signIn.email)
                applyGoogleSignInSuccess(data)
                syncPlusEntitlementFromBilling()
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        errorMessage = mapGoogleAuthError(error),
                        accountActionInProgress = false
                    )
                }
            }
    }

    /** Surface APP_CLIENT_KEY / OAuth misconfig clearly — bare "unauthorized" is common after API hardening. */
    private fun mapGoogleAuthError(error: Throwable): String {
        val raw = error.message?.trim().orEmpty()
        if (raw.equals("unauthorized", ignoreCase = true) ||
            raw.contains("HTTP 401", ignoreCase = true)
        ) {
            return "Google Sign-In was rejected by the server (unauthorized). " +
                "Usually the app’s APP_CLIENT_KEY does not match the Workers secret. " +
                "Set archerypal.app.client.key in local.properties to the same value as " +
                "`wrangler secret put APP_CLIENT_KEY`, rebuild, and try again."
        }
        return raw.ifBlank { "Google Sign-In failed" }
    }

    private fun applyGoogleSignInSuccess(data: PersistedAppData) {
        billingManager.startConnection()
        lastSyncedPushToken = null
        _uiState.update {
            it.copy(
                playerName = data.playerName.ifBlank { it.playerName },
                friendCode = data.friendCode,
                googleAccountLinked = data.googleAccountLinked,
                googleAccountEmail = data.googleAccountEmail,
                serverReachable = true,
                accountActionInProgress = false,
                showAccountOnboarding = false
            )
        }
        refreshAndSyncPushToken(data)
        syncPlusEntitlementFromBilling()
        refreshSocialData()
    }

    fun createNewServerIdentity() {
        val name = _uiState.value.playerName.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter your name first") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(accountActionInProgress = true, showAccountOnboarding = false) }
            remoteIdentity.ensureRegistered(name)
                .onSuccess { data ->
                    _uiState.update {
                        it.copy(
                            friendCode = data.friendCode,
                            serverReachable = true,
                            accountActionInProgress = false
                        )
                    }
                    refreshAndSyncPushToken(data)
                    if (_uiState.value.isPlusActive) syncPlusEntitlementFromBilling()
                    refreshSocialData()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            errorMessage = error.message ?: "Could not create account",
                            accountActionInProgress = false,
                            showAccountOnboarding = true
                        )
                    }
                }
        }
    }

    fun linkGoogleAccount() {
        viewModelScope.launch {
            _uiState.update { it.copy(accountActionInProgress = true) }
            googleAuthService.signIn()
                .fold(
                    onSuccess = { signIn ->
                        remoteIdentity.linkGoogleAccount(signIn.idToken)
                            .onSuccess { data ->
                                billingManager.setCustomerEmail(data.friendCode, signIn.email)
                                _uiState.update {
                                    it.copy(
                                        googleAccountLinked = data.googleAccountLinked,
                                        googleAccountEmail = data.googleAccountEmail,
                                        accountActionInProgress = false
                                    )
                                }
                                refreshAndSyncPushToken(data)
                            }
                            .onFailure { error ->
                                _uiState.update {
                                    it.copy(
                                        errorMessage = error.message ?: "Could not link Google account",
                                        accountActionInProgress = false
                                    )
                                }
                            }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                errorMessage = error.message ?: "Google Sign-In failed",
                                accountActionInProgress = false
                            )
                        }
                    }
                )
        }
    }

    fun restoreGoogleAccount() {
        viewModelScope.launch {
            _uiState.update { it.copy(accountActionInProgress = true, showAccountOnboarding = false) }
            googleAuthService.signIn()
                .fold(
                    onSuccess = { signIn ->
                        remoteIdentity.restoreFromGoogle(signIn.idToken)
                            .onSuccess { data ->
                                billingManager.setCustomerEmail(data.friendCode, signIn.email)
                                billingManager.startConnection()
                                _uiState.update {
                                    it.copy(
                                        playerName = data.playerName,
                                        friendCode = data.friendCode,
                                        googleAccountLinked = data.googleAccountLinked,
                                        googleAccountEmail = data.googleAccountEmail,
                                        serverReachable = true,
                                        accountActionInProgress = false
                                    )
                                }
                                refreshAndSyncPushToken(data)
                                syncPlusEntitlementFromBilling()
                                refreshSocialData()
                            }
                            .onFailure { error ->
                                _uiState.update {
                                    it.copy(
                                        errorMessage = error.message ?: "Could not restore account",
                                        accountActionInProgress = false,
                                        showAccountOnboarding = true
                                    )
                                }
                            }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                errorMessage = error.message ?: "Google Sign-In failed",
                                accountActionInProgress = false,
                                showAccountOnboarding = true
                            )
                        }
                    }
                )
        }
    }

    private suspend fun evaluateAccountOnboarding() {
        _uiState.update { it.copy(showAccountOnboarding = false) }
    }

    fun addRemoteFriend(theirFriendCode: String) {
        // Instant /friends/add was removed server-side (private-feed IDOR). Send a request instead.
        requestRemoteFriend(theirFriendCode)
    }

    fun requestRemoteFriend(theirFriendCode: String) {
        addRemoteFriends(listOf(theirFriendCode))
    }

    fun addRemoteFriends(friendCodes: List<String>) {
        val codes = friendCodes
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .distinct()
        if (codes.isEmpty()) return
        val data = persistedData
        val myCode = data.friendCode
        val secret = data.deviceSecret
        val creds = deviceCreds(data) ?: return
        if (myCode.isNullOrBlank() || secret.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "Connect to the server first (check your name and internet)") }
            ensureServerIdentity(force = true)
            return
        }
        viewModelScope.launch {
            val sent = mutableListOf<String>()
            var lastError: String? = null
            for (code in codes) {
                matchServerApi.sendFriendRequest(creds, code)
                    .onSuccess { sent += code }
                    .onFailure { error ->
                        val msg = error.message.orEmpty()
                        if (msg.contains("already exists", ignoreCase = true)) {
                            sent += code
                        } else {
                            lastError = msg.ifBlank { "Could not send friend request" }
                        }
                    }
            }
            _uiState.update { state ->
                state.copy(
                    statusMessage = when {
                        sent.size > 1 -> "Sent ${sent.size} friend requests"
                        sent.size == 1 -> "Friend request sent"
                        else -> state.statusMessage
                    },
                    errorMessage = if (sent.isEmpty()) lastError else state.errorMessage,
                    requestedFriendCodes = state.requestedFriendCodes + sent,
                    friendSearchResults = state.friendSearchResults.map { result ->
                        if (result.friendCode.uppercase() in sent &&
                            result.relation == PlayerSearchResult.RELATION_NONE
                        ) {
                            result.copy(relation = PlayerSearchResult.RELATION_REQUESTED)
                        } else {
                            result
                        }
                    },
                    mutualFriendSuggestions = state.mutualFriendSuggestions.filterNot {
                        it.friendCode.uppercase() in sent
                    }
                )
            }
            if (sent.isNotEmpty()) refreshSocialData()
        }
    }

    fun acceptFriendRequest(fromFriendCode: String) {
        val data = persistedData
        val myCode = data.friendCode ?: return
        val secret = data.deviceSecret ?: return
        val creds = deviceCreds(data) ?: return
        viewModelScope.launch {
            matchServerApi.acceptFriendRequest(creds, fromFriendCode.trim().uppercase())
                .onSuccess { response ->
                    rememberRemoteFriend(response.friend)
                    refreshSocialData()
                    _uiState.update { state ->
                        state.copy(
                            statusMessage = "Added ${response.friend.displayName}",
                            pendingFriendRequests = state.pendingFriendRequests.filterNot {
                                it.fromCode.equals(fromFriendCode, ignoreCase = true)
                            },
                            friendSearchResults = state.friendSearchResults.map { result ->
                                if (result.friendCode.equals(fromFriendCode, ignoreCase = true)) {
                                    result.copy(relation = PlayerSearchResult.RELATION_FRIEND)
                                } else {
                                    result
                                }
                            }
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Could not accept request")
                    }
                }
        }
    }

    fun declineFriendRequest(fromFriendCode: String) {
        val data = persistedData
        val myCode = data.friendCode ?: return
        val secret = data.deviceSecret ?: return
        val creds = deviceCreds(data) ?: return
        viewModelScope.launch {
            matchServerApi.declineFriendRequest(creds, fromFriendCode.trim().uppercase())
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            pendingFriendRequests = state.pendingFriendRequests.filterNot {
                                it.fromCode.equals(fromFriendCode, ignoreCase = true)
                            },
                            friendSearchResults = state.friendSearchResults.map { result ->
                                if (result.friendCode.equals(fromFriendCode, ignoreCase = true)) {
                                    result.copy(relation = PlayerSearchResult.RELATION_NONE)
                                } else {
                                    result
                                }
                            }
                        )
                    }
                    refreshSocialData()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Could not decline request")
                    }
                }
        }
    }

    fun toggleShootRsvp(shootId: String, currentlyGoing: Boolean) {
        val data = persistedData
        val myCode = data.friendCode ?: return
        val secret = data.deviceSecret ?: return
        val creds = deviceCreds(data) ?: return
        viewModelScope.launch {
            val result = if (currentlyGoing) {
                matchServerApi.clearShootRsvp(creds, shootId)
            } else {
                matchServerApi.setShootRsvp(creds, shootId)
            }
            result.onSuccess {
                refreshSocialData()
                if (_uiState.value.shootDetailShootId == shootId) {
                    loadShootAttendees(shootId)
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "Could not update RSVP")
                }
            }
        }
    }

    fun loadShootAttendees(shootId: String) {
        val data = persistedData
        val myCode = data.friendCode ?: return
        val secret = data.deviceSecret ?: return
        val creds = deviceCreds(data) ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(shootDetailLoading = true, shootDetailShootId = shootId) }
            matchServerApi.listShootAttendees(creds, shootId)
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            selectedShootAttendees = response.attendees,
                            shootDetailLoading = false
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(selectedShootAttendees = emptyList(), shootDetailLoading = false)
                    }
                }
        }
    }

    fun clearShootDetail() {
        _uiState.update {
            it.copy(
                selectedShootAttendees = emptyList(),
                shootDetailShootId = null,
                shootDetailLoading = false
            )
        }
    }

    fun createShoot(
        title: String,
        locationName: String,
        region: String,
        description: String,
        startsAtMs: Long,
        endsAtMs: Long?,
        isPublic: Boolean = false
    ) {
        if (!_uiState.value.isPlusActive) {
            _uiState.update { it.copy(errorMessage = "Archerypal Plus is required to create shoots") }
            return
        }
        val data = persistedData
        val myCode = data.friendCode ?: return
        val secret = data.deviceSecret ?: return
        val creds = deviceCreds(data) ?: return
        viewModelScope.launch {
            val geo = locationHelper.geocodeLocationName(locationName.trim())
            matchServerApi.createShoot(creds, 
                CreateShootRequest(
                    title = title.trim(),
                    locationName = locationName.trim(),
                    region = region.trim(),
                    description = description.trim(),
                    startsAt = startsAtMs,
                    endsAt = endsAtMs,
                    isPublic = isPublic,
                    latitude = geo?.latitude,
                    longitude = geo?.longitude
                )
            ).onSuccess {
                refreshSocialData()
                _uiState.update { it.copy(statusMessage = "Shoot created") }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "Could not create shoot")
                }
            }
        }
    }

    fun buildFriendProfileLink(): String? {
        val code = _uiState.value.friendCode ?: return null
        return com.archerypal.app.data.FriendProfileLink.build(code)
    }

    fun handleFriendProfileLink(raw: String) {
        val parsed = com.archerypal.app.data.FriendProfileLink.extractFromText(raw) ?: run {
            _uiState.update { it.copy(errorMessage = "Could not read profile link") }
            return
        }
        val (code, name) = parsed
        if (code.equals(persistedData.friendCode, ignoreCase = true)) {
            _uiState.update { it.copy(errorMessage = "That is your own profile") }
            return
        }
        _uiState.update {
            it.copy(pendingProfileFriendCode = code, pendingProfileFriendName = name)
        }
    }

    fun confirmPendingProfileAdd() {
        val code = _uiState.value.pendingProfileFriendCode ?: return
        clearPendingProfileAdd()
        addRemoteFriend(code)
    }

    fun clearPendingProfileAdd() {
        _uiState.update { it.copy(pendingProfileFriendCode = null, pendingProfileFriendName = null) }
    }

    private suspend fun refreshShootFriendSuggestions(
        creds: DeviceCredentials,
        shoots: List<RemoteShoot>
    ) {
        val now = System.currentTimeMillis()
        val myCode = persistedData.friendCode?.uppercase()
        val friendCodes = _uiState.value.remoteFriends.map { it.friendCode.uppercase() }.toSet()
        val goingTo = shoots
            .filter { it.viewerIsGoing && (it.endsAt ?: it.startsAt) >= now }
            .sortedBy { it.startsAt }
            .take(MAX_SHOOTS_FOR_SUGGESTIONS)
        val suggestions = mutableListOf<FriendSuggestion>()
        for (shoot in goingTo) {
            matchServerApi.listShootAttendees(creds, shoot.id).onSuccess { response ->
                response.attendees
                    .filterNot { it.isFriend }
                    .filterNot { it.friendCode.uppercase() == myCode }
                    .filterNot { it.friendCode.uppercase() in friendCodes }
                    .forEach { suggestions += FriendSuggestion(it.displayName, it.friendCode) }
            }
        }
        _uiState.update {
            it.copy(shootFriendSuggestions = suggestions.distinctBy { s -> s.friendCode.uppercase() })
        }
    }

    fun clearPendingFriendSuggestions() {
        _uiState.update { it.copy(pendingFriendSuggestions = emptyList()) }
    }

    fun friendSuggestionsFromSaved(): List<FriendSuggestion> {
        val remoteCodes = _uiState.value.remoteFriends.map { it.friendCode.uppercase() }.toSet()
        val myCode = persistedData.friendCode?.uppercase()
        return persistedData.friends
            .filter { it.friendCode.isNotBlank() }
            .filterNot { it.friendCode.uppercase() == myCode }
            .filterNot { it.friendCode.uppercase() in remoteCodes }
            .map { FriendSuggestion(it.name, it.friendCode) }
    }

    fun startRemoteChallenge(targetFriendCode: String) {
        if (!_uiState.value.isPlusActive) {
            _uiState.update { it.copy(errorMessage = "Archerypal Plus is required to host remote matches") }
            return
        }
        val name = _uiState.value.playerName
        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter your name first") }
            return
        }
        val data = persistedData
        val myCode = data.friendCode
        val secret = data.deviceSecret
        val creds = deviceCreds(data) ?: return
        if (myCode.isNullOrBlank() || secret.isNullOrBlank()) {
            ensureServerIdentity(force = true)
            _uiState.update { it.copy(errorMessage = "Registering with server — try again in a moment") }
            return
        }
        viewModelScope.launch {
            remoteIdentity.ensureRegistered(name).onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Server registration failed") }
                return@launch
            }.onSuccess { data ->
                refreshAndSyncPushToken(data)
            }
            matchServerApi.challengeFriend(creds, targetFriendCode.trim().uppercase())
                .onSuccess { response ->
                    val hostToken = response.hostToken?.takeIf { it.isNotBlank() }
                        ?: response.wsUrl.substringAfter("token=", "").substringBefore('&').takeIf { it.isNotBlank() }
                        ?: response.joinToken
                    val cleanWs = response.wsUrl.substringBefore('?')
                    val opponent = response.targetDisplayName?.takeIf { it.isNotBlank() }
                        ?: _uiState.value.remoteFriends
                            .firstOrNull { it.friendCode.equals(targetFriendCode.trim(), ignoreCase = true) }
                            ?.displayName
                    startRemoteHostSession(name, response.roomId, cleanWs, hostToken, response.joinToken)
                    _uiState.update {
                        it.copy(
                            remoteOpponentName = opponent,
                            remoteOpponentNotified = response.targetNotified,
                            statusMessage = when {
                                opponent == null -> "Remote match — share the invite with your friend"
                                response.targetNotified -> "Challenge sent — waiting for $opponent to join"
                                else -> "Waiting for $opponent — share the invite so they can join"
                            }
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Could not start remote challenge")
                    }
                }
        }
    }

    /** [joinToken] is only supplied by legacy links; otherwise it is fetched for the signed-in invitee. */
    fun joinRemoteMatch(roomId: String, joinToken: String? = null) {
        // Joiners can accept invites without a Plus subscription.
        val name = _uiState.value.playerName
        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter your name first") }
            return
        }
        val room = roomId.trim()
        viewModelScope.launch {
            remoteIdentity.ensureRegistered(name).onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Server registration failed") }
                return@launch
            }.onSuccess { data ->
                refreshAndSyncPushToken(data)
            }
            val legacyToken = joinToken?.trim()?.takeIf { it.isNotBlank() }
            if (legacyToken != null) {
                startRemoteJoinSession(name, room, MatchServerConfig.roomWebSocketUrl(room), legacyToken)
                return@launch
            }
            val creds = deviceCreds() ?: run {
                _uiState.update { it.copy(errorMessage = "Registering with server — try again in a moment") }
                return@launch
            }
            matchServerApi.joinChallenge(creds, room)
                .onSuccess { response ->
                    _uiState.update { state ->
                        state.copy(
                            incomingChallenges = state.incomingChallenges.filterNot {
                                it.roomId.equals(response.roomId, ignoreCase = true)
                            }
                        )
                    }
                    startRemoteJoinSession(
                        name,
                        response.roomId,
                        response.wsUrl.substringBefore('?'),
                        response.joinToken,
                        hostName = response.hostDisplayName
                    )
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Could not join that challenge")
                    }
                }
        }
    }

    private fun startRemoteHostSession(
        name: String,
        roomId: String,
        wsUrl: String,
        hostToken: String,
        joinToken: String
    ) {
        val matchId = generateMatchId()
        val remote = RemoteMatchTransport(isHost = true, localName = name)
        bindTransport(remote)
        remote.connect(wsUrl, hostToken)
        _uiState.update {
            it.copy(
                isHost = true,
                isRemoteMatch = true,
                remoteRoomId = roomId,
                remoteJoinToken = joinToken,
                matchId = matchId,
                phase = MatchPhase.LOBBY,
                targetCount = 0,
                targetCountInput = "",
                scoringType = ScoringType.ASA,
                scores = emptyList(),
                players = listOf(PlayerInfo(name, RemoteEndpoints.HOST, isHost = true)),
                hostName = name,
                errorMessage = null,
                connectionStatus = ConnectionStatus.Connecting,
                statusMessage = "Remote match — share the invite with your friend"
            )
        }
    }

    private fun startRemoteJoinSession(
        name: String,
        roomId: String,
        wsUrl: String,
        joinToken: String,
        hostName: String? = null
    ) {
        val remote = RemoteMatchTransport(isHost = false, localName = name)
        bindTransport(remote)
        remote.connect(wsUrl, joinToken, remoteHostName = hostName?.takeIf { it.isNotBlank() } ?: "Host")
        _uiState.update {
            it.copy(
                isHost = false,
                isRemoteMatch = true,
                remoteRoomId = roomId,
                matchId = generateMatchId(),
                phase = MatchPhase.LOBBY,
                hostEndpointId = null,
                errorMessage = null,
                connectionStatus = ConnectionStatus.Connecting,
                statusMessage = "Joining remote match…"
            )
        }
    }

    private fun rememberRemoteFriend(friend: RemoteFriend) {
        viewModelScope.launch {
            persistence.update { data ->
                val existing = data.friends.firstOrNull {
                    it.friendCode.equals(friend.friendCode, ignoreCase = true)
                }
                val others = data.friends.filterNot {
                    it.friendCode.equals(friend.friendCode, ignoreCase = true)
                }
                val saved = SavedFriend(
                    friendCode = friend.friendCode,
                    name = friend.displayName,
                    lastHostName = existing?.lastHostName,
                    lastMatchId = existing?.lastMatchId,
                    lastSeenAt = System.currentTimeMillis()
                )
                data.copy(friends = listOf(saved) + others)
            }
        }
    }

    fun startHosting() = startHostingInternal(rematch = false)

    fun startRematchAsHost() = startHostingInternal(rematch = true)

    private fun startHostingInternal(rematch: Boolean) {
        bindTransport(nearbyTransport)
        val name = _uiState.value.playerName
        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter your name first") }
            return
        }
        val matchId = generateMatchId()
        _uiState.update {
            it.copy(
                isHost = true,
                isRematch = rematch,
                isRemoteMatch = false,
                remoteRoomId = null,
                remoteJoinToken = null,
                remoteOpponentName = null,
                remoteOpponentNotified = false,
                matchId = matchId,
                phase = MatchPhase.LOBBY,
                targetCount = 0,
                targetCountInput = "",
                scoringType = ScoringType.ASA,
                scores = emptyList(),
                players = listOf(PlayerInfo(name, "local", isHost = true)),
                hostName = name,
                errorMessage = null,
                connectionStatus = ConnectionStatus.Connecting,
                statusMessage = if (rematch) "Rematch — waiting for your group…" else "Starting host…"
            )
        }
        matchTransport.startHosting(name, matchId) {}
    }

    fun startJoining() {
        startJoiningInternal(resyncHostName = null)
    }

    fun resyncWithFriend(friendName: String) {
        val hostName = persistedData.friends
            .firstOrNull { it.name.equals(friendName, ignoreCase = true) }
            ?.lastHostName
            ?: friendName
        startJoiningInternal(resyncHostName = hostName)
    }

    private fun startJoiningInternal(resyncHostName: String?) {
        bindTransport(nearbyTransport)
        val name = _uiState.value.playerName
        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter your name first") }
            return
        }
        _uiState.update {
            it.copy(
                isHost = false,
                isRematch = resyncHostName != null,
                resyncHostName = resyncHostName,
                discoveredHosts = emptyList(),
                hostEndpointId = null,
                hostName = "",
                errorMessage = null,
                connectionStatus = ConnectionStatus.Connecting,
                statusMessage = if (resyncHostName != null) {
                    "Resyncing with $resyncHostName…"
                } else {
                    "Starting discovery…"
                }
            )
        }
        connectingEndpointId = null
        matchTransport.startDiscovery()
    }

    fun connectToDiscoveredHost(host: DiscoveredHost) {
        if (host.endpointId.isBlank()) return
        val state = _uiState.value
        if (state.hostEndpointId != null || connectingEndpointId != null) return

        connectingEndpointId = host.endpointId
        _uiState.update {
            it.copy(
                matchId = host.matchId,
                hostName = host.hostName,
                connectionStatus = ConnectionStatus.Connecting,
                statusMessage = "Connecting to ${host.hostName}…"
            )
        }
        rememberFriend(host.hostName, host.hostName, host.matchId)
        matchTransport.connectToHost(host.endpointId, _uiState.value.playerName)
    }

    fun onQrScanned(raw: String) {
        val json = raw.removePrefix(AppConstants.QR_PREFIX)
        runCatching {
            AppJson.decodeFromString(QrPayload.serializer(), json)
        }.onSuccess { payload ->
            val host = DiscoveredHost(
                endpointId = "",
                hostName = payload.hostName,
                matchId = payload.matchId
            )
            _uiState.update {
                it.copy(
                    matchId = payload.matchId,
                    hostName = payload.hostName,
                    statusMessage = "Match found — waiting for ${payload.hostName} to appear nearby…"
                )
            }
            rememberFriend(payload.hostName, payload.hostName, payload.matchId)
            addDiscoveredHost(host)
        }.onFailure {
            _uiState.update { it.copy(errorMessage = "Invalid QR code") }
        }
    }

    fun buildQrContent(): String {
        val state = _uiState.value
        val payload = QrPayload(state.matchId, state.playerName)
        return AppConstants.QR_PREFIX + AppJson.encodeToString(QrPayload.serializer(), payload)
    }

    fun setScoringType(type: ScoringType) {
        _uiState.update { it.copy(scoringType = type, pendingScore = "") }
    }

    fun setTargetCountInput(input: String) {
        if (input.isNotEmpty() && !input.all { it.isDigit() }) return
        val parsed = input.toIntOrNull()
        if (parsed != null && parsed > AppConstants.MAX_TARGETS) {
            _uiState.update {
                it.copy(
                    targetCountInput = AppConstants.MAX_TARGETS.toString(),
                    targetCount = AppConstants.MAX_TARGETS
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                targetCountInput = input,
                targetCount = parsed ?: 0
            )
        }
    }

    fun beginMatch() {
        if (!_uiState.value.isHost) return
        val count = _uiState.value.targetCount
        if (count < AppConstants.MIN_TARGETS) {
            _uiState.update { it.copy(errorMessage = "Set at least one target") }
            return
        }
        _uiState.update { it.copy(phase = MatchPhase.SCORING, targetCountInput = count.toString()) }
        broadcastMatchSetup(count, _uiState.value.scoringType)
        broadcastMatchState()
    }

    fun selectPresetScore(value: Int) {
        val scoringType = _uiState.value.scoringType
        if (!scoringType.isValidScore(value)) return
        _uiState.update { it.copy(pendingScore = value.toString()) }
        submitScore()
    }

    fun selectTarget(index: Int) {
        val state = _uiState.value
        val existing = scoreForPlayerTarget(state, state.playerName, index)
        _uiState.update { it.copy(selectedTarget = index, pendingScore = existing?.toString().orEmpty()) }
    }

    fun appendDigit(digit: String) {
        val state = _uiState.value
        if (state.scoringType != ScoringType.FREEFORM) return
        val current = state.pendingScore
        if (current.length >= state.scoringType.scoreInputMaxDigits()) return
        val next = (current + digit).toIntOrNull() ?: return
        if (next > AppConstants.FREEFORM_MAX_SCORE) return
        _uiState.update { it.copy(pendingScore = next.toString()) }
    }

    fun clearPendingScore() {
        _uiState.update { it.copy(pendingScore = "") }
    }

    fun submitScore() {
        val state = _uiState.value
        if (state.phase == MatchPhase.FINISHED) return
        val value = state.pendingScore.toIntOrNull() ?: return
        if (!state.scoringType.isValidScore(value)) return

        val entry = ScoreEntry(
            matchId = state.matchId,
            playerName = state.playerName,
            targetIndex = state.selectedTarget,
            scoreValue = value
        )
        val loggedTarget = state.selectedTarget
        val nextTarget = nextTargetAfterSubmit(loggedTarget, state.targetCount)

        if (state.isHost) {
            _uiState.update { current ->
                current.copy(
                    scores = replaceScore(current.scores, entry),
                    pendingScore = "",
                    selectedTarget = nextTarget,
                    statusMessage = "Target $loggedTarget: $value logged"
                )
            }
            broadcastMatchState()
        } else {
            _uiState.update { current ->
                current.copy(
                    scores = replaceScore(current.scores, entry),
                    pendingScore = "",
                    selectedTarget = nextTarget,
                    pendingQueue = current.pendingQueue + entry,
                    statusMessage = "Target $loggedTarget: $value sent"
                )
            }
            val hostId = state.hostEndpointId ?: matchTransport.connectedEndpoints.value.firstOrNull()
            if (hostId != null) {
                matchTransport.sendMessage(
                    hostId,
                    MessageType.SCORE_SUBMIT.name,
                    buildJsonObject {
                        put("match_id", entry.matchId)
                        put("player_name", entry.playerName)
                        put("target_index", entry.targetIndex)
                        put("score_value", entry.scoreValue)
                    }
                )
            }
        }
    }

    fun finishMatch() {
        if (!_uiState.value.isHost || _uiState.value.phase == MatchPhase.FINISHED) return
        finalizeAndPersistMatch(broadcast = true)
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearBillingMessage() {
        billingManager.clearBillingMessage()
    }

    fun restorePurchases() {
        val code = _uiState.value.friendCode ?: persistedData.friendCode
        billingManager.restorePurchases(friendCode = code)
    }

    fun refreshRevenueCat() {
        val code = _uiState.value.friendCode ?: persistedData.friendCode
        if (!code.isNullOrBlank()) {
            billingManager.logIn(code, forceRefresh = true)
        } else {
            billingManager.refreshCustomerInfo()
        }
    }

    fun openPaywall() {
        _uiState.update { it.copy(showPaywall = true) }
    }

    fun dismissPaywall() {
        _uiState.update { it.copy(showPaywall = false) }
    }

    fun openCustomerCenter() {
        _uiState.update { it.copy(showCustomerCenter = true) }
    }

    fun dismissCustomerCenter() {
        _uiState.update { it.copy(showCustomerCenter = false) }
    }

    fun onRevenueCatCustomerInfo(info: CustomerInfo) {
        billingManager.onCustomerInfoUpdated(info)
        if (info.entitlements[RevenueCatConfig.ENTITLEMENT_PRO]?.isActive == true) {
            dismissPaywall()
        }
    }

    fun requiresPlusForRemote(): Boolean = !uiState.value.isPlusActive

    fun leaveMatch() {
        matchTransport.shutdown()
        bindTransport(nearbyTransport)
        val name = _uiState.value.playerName
        val billing = _uiState.value
        _uiState.value = UiState(
            playerName = name,
            friendCode = billing.friendCode ?: persistedData.friendCode,
            serverReachable = billing.serverReachable,
            savedFriends = persistedData.friends.sortedByDescending { it.lastSeenAt },
            recentMatch = persistedData.matchHistory.firstOrNull(),
            matchHistory = persistedData.matchHistory,
            lastMatchGroup = persistedData.lastMatchGroup,
            globalLeaderboard = globalLeaderboardRows(rankGlobalStats(persistedData.globalStats.values)),
            playerGlobalStats = persistedData.globalStats[name]
                ?: persistedData.globalStats.values.firstOrNull { it.name.equals(name, ignoreCase = true) },
            isAdFree = billing.isAdFree,
            isPlusActive = PlusFeatures.hasPlusAccess(billing.isPlusActive),
            removeAdsPrice = billing.removeAdsPrice,
            plusPrice = billing.plusPrice,
            billingReady = billing.billingReady,
            remoteFriends = billing.remoteFriends,
            incomingChallenges = billing.incomingChallenges,
            onlineGlobalLeaderboard = billing.onlineGlobalLeaderboard,
            onlineFriendsLeaderboard = billing.onlineFriendsLeaderboard,
            standingsMode = billing.standingsMode,
            googleAccountLinked = billing.googleAccountLinked,
            googleAccountEmail = billing.googleAccountEmail,
            accountActionInProgress = billing.accountActionInProgress,
            showAccountOnboarding = billing.showAccountOnboarding
        )
        endpointNames.clear()
        endpointFriendCodes.clear()
        connectingEndpointId = null
    }

    fun matchLeaderboard(): List<LeaderboardRow> =
        matchLeaderboardRows(rankMatchTotals(currentMatchTotals()))

    fun playerTargetScores(): Map<Int, Int> {
        val name = _uiState.value.playerName
        return _uiState.value.scores
            .filter { it.playerName == name }
            .associate { it.targetIndex to it.scoreValue }
    }

    fun playerRunningTotal(): Int = playerTargetScores().values.sum()

    private fun currentMatchTotals(): Map<String, Int> {
        val totals = mutableMapOf<String, Int>()
        _uiState.value.scores.forEach { score ->
            totals[score.playerName] = (totals[score.playerName] ?: 0) + score.scoreValue
        }
        return totals
    }

    private fun advertisingMessage(state: UiState): String {
        return if (state.isRematch && state.lastMatchGroup.isNotEmpty()) {
            "Rematch live — your group can resync now"
        } else {
            "Advertising — waiting for archers"
        }
    }

    private fun discoveryMessage(state: UiState): String {
        return state.resyncHostName?.let { "Looking for $it…" }
            ?: "Searching for nearby hosts…"
    }

    private fun rememberFriend(name: String, hostName: String?, matchId: String?, friendCode: String? = null) {
        viewModelScope.launch {
            persistence.rememberFriend(name, hostName, matchId, friendCode)
        }
    }

    private fun updateFriendSuggestionsAfterMatch(players: List<String>) {
        val myName = _uiState.value.playerName
        val myCode = persistedData.friendCode?.uppercase()
        val remoteCodes = _uiState.value.remoteFriends.map { it.friendCode.uppercase() }.toSet()
        val state = _uiState.value
        val suggestions = players
            .filterNot { it.equals(myName, ignoreCase = true) }
            .mapNotNull { name ->
                val player = state.players.find { it.name.equals(name, ignoreCase = true) }
                val code = player?.endpointId?.let { endpointFriendCodes[it] }
                    ?: persistedData.friends.find { it.name.equals(name, ignoreCase = true) }?.friendCode
                code?.takeIf { it.isNotBlank() }?.let { FriendSuggestion(name, it) }
            }
            .filterNot { it.friendCode.uppercase() == myCode }
            .filterNot { it.friendCode.uppercase() in remoteCodes }
            .distinctBy { it.friendCode.uppercase() }
        if (suggestions.isNotEmpty()) {
            _uiState.update { it.copy(pendingFriendSuggestions = suggestions) }
        }
    }

    private fun addDiscoveredHost(host: DiscoveredHost) {
        val resyncName = _uiState.value.resyncHostName
        if (resyncName != null &&
            !host.hostName.equals(resyncName, ignoreCase = true) &&
            !_uiState.value.isHost
        ) {
            return
        }

        var mergedHost = host
        _uiState.update { state ->
            val updatedHosts = mergeDiscoveredHost(state.discoveredHosts, host)
            mergedHost = updatedHosts.first { it.matchId == host.matchId }
            state.copy(discoveredHosts = updatedHosts)
        }

        val state = _uiState.value
        if (!state.isHost &&
            mergedHost.endpointId.isNotBlank() &&
            state.hostEndpointId == null &&
            connectingEndpointId == null &&
            (state.matchId.isBlank() || state.matchId == mergedHost.matchId)
        ) {
            connectToDiscoveredHost(mergedHost)
        }
    }

    private fun mergeDiscoveredHost(
        hosts: List<DiscoveredHost>,
        incoming: DiscoveredHost
    ): List<DiscoveredHost> {
        val existingIndex = hosts.indexOfFirst { it.matchId == incoming.matchId }
        if (existingIndex < 0) {
            return hosts + incoming
        }
        val existing = hosts[existingIndex]
        val merged = DiscoveredHost(
            endpointId = incoming.endpointId.ifBlank { existing.endpointId },
            hostName = incoming.hostName.ifBlank { existing.hostName },
            matchId = incoming.matchId
        )
        return hosts.toMutableList().apply { set(existingIndex, merged) }
    }

    private fun handleEndpointConnected(endpointId: String, name: String) {
        endpointNames[endpointId] = name
        connectingEndpointId = null
        val state = _uiState.value
        rememberFriend(name, if (state.isHost) state.playerName else name, state.matchId)
        if (state.isHost) {
            _uiState.update { it.copy(statusMessage = "Archer connected") }
        } else {
            _uiState.update {
                it.copy(
                    hostEndpointId = endpointId,
                    hostName = name,
                    phase = MatchPhase.LOBBY,
                    connectionStatus = ConnectionStatus.Connected,
                    statusMessage = "Connected to $name — waiting for match to start"
                )
            }
            rememberFriend(state.playerName, name, state.matchId)
            matchTransport.sendPlayerJoin(
                endpointId,
                state.playerName,
                state.matchId,
                persistedData.friendCode
            )
            matchTransport.sendMessage(endpointId, MessageType.REQUEST_SYNC.name)
        }
    }

    private fun handleEndpointDisconnected(endpointId: String) {
        endpointNames.remove(endpointId)
        endpointFriendCodes.remove(endpointId)
        if (_uiState.value.isHost) {
            _uiState.update { state ->
                state.copy(players = state.players.filterNot { it.endpointId == endpointId })
            }
            broadcastMatchState()
        } else if (_uiState.value.hostEndpointId == endpointId ||
            (_uiState.value.isRemoteMatch && endpointId == RemoteEndpoints.HOST)
        ) {
            connectingEndpointId = null
            _uiState.update {
                it.copy(
                    hostEndpointId = null,
                    hostName = "",
                    connectionStatus = ConnectionStatus.Discovering,
                    statusMessage = "Disconnected from host — searching again…"
                )
            }
        }
    }

    private fun handleMessage(endpointId: String, message: com.archerypal.app.data.P2PMessage) {
        when (message.type) {
            MessageType.PLAYER_JOIN.name -> handlePlayerJoin(endpointId, message)
            MessageType.PLAYER_JOIN_REJECTED.name -> handlePlayerJoinRejected(message)
            MessageType.MATCH_SETUP.name -> handleMatchSetup(message)
            MessageType.SCORE_SUBMIT.name -> handleScoreSubmit(endpointId, message)
            MessageType.MATCH_STATE.name -> handleMatchState(message)
            MessageType.SCORE_ACK.name -> handleScoreAck(message)
            MessageType.REQUEST_SYNC.name -> if (_uiState.value.isHost) broadcastMatchState()
            MessageType.MATCH_FINISHED.name -> handleMatchFinished(message)
        }
    }

    private fun handlePlayerJoin(endpointId: String, message: com.archerypal.app.data.P2PMessage) {
        if (!_uiState.value.isHost) return
        val payload = message.payload ?: return
        val playerName = payload["player_name"]?.jsonPrimitive?.content ?: return
        val friendCode = payload["friend_code"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        if (_uiState.value.players.size >= AppConstants.MAX_PLAYERS) {
            matchTransport.sendMessage(
                endpointId,
                MessageType.PLAYER_JOIN_REJECTED.name,
                buildJsonObject { put("reason", "full") }
            )
            matchTransport.disconnectEndpoint(endpointId)
            return
        }
        endpointNames[endpointId] = playerName
        if (friendCode != null) {
            endpointFriendCodes[endpointId] = friendCode
        }
        rememberFriend(playerName, _uiState.value.playerName, _uiState.value.matchId, friendCode)
        _uiState.update { state ->
            val updated = state.players.filterNot { it.endpointId == endpointId } +
                PlayerInfo(playerName, endpointId)
            state.copy(players = updated, statusMessage = "$playerName joined")
        }
        broadcastMatchState()
    }

    private fun handlePlayerJoinRejected(message: com.archerypal.app.data.P2PMessage) {
        if (_uiState.value.isHost) return
        val hostId = _uiState.value.hostEndpointId
        if (hostId != null) {
            matchTransport.disconnectEndpoint(hostId)
        }
        connectingEndpointId = null
        _uiState.update {
            it.copy(
                hostEndpointId = null,
                hostName = "",
                connectionStatus = ConnectionStatus.Failed,
                errorMessage = "This match is full (${AppConstants.MAX_PLAYERS} archers max)"
            )
        }
    }

    private fun handleMatchSetup(message: com.archerypal.app.data.P2PMessage) {
        val payload = message.payload ?: return
        val count = payload["target_count"]?.jsonPrimitive?.int ?: return
        val scoringType = parseScoringType(payload["scoring_type"]?.jsonPrimitive?.content)
        _uiState.update {
            it.copy(
                targetCount = count,
                targetCountInput = count.toString(),
                scoringType = scoringType,
                phase = MatchPhase.SCORING,
                pendingScore = "",
                statusMessage = "Match started · ${scoringType.label}"
            )
        }
    }

    private fun handleScoreSubmit(endpointId: String, message: com.archerypal.app.data.P2PMessage) {
        if (!_uiState.value.isHost) return
        val payload = message.payload ?: return
        val entry = ScoreEntry(
            matchId = payload["match_id"]?.jsonPrimitive?.content ?: _uiState.value.matchId,
            playerName = payload["player_name"]?.jsonPrimitive?.content ?: return,
            targetIndex = payload["target_index"]?.jsonPrimitive?.int ?: return,
            scoreValue = payload["score_value"]?.jsonPrimitive?.int ?: return
        )
        if (!_uiState.value.scoringType.isValidScore(entry.scoreValue)) return
        mergeScore(entry)
        broadcastMatchState()
        matchTransport.sendMessage(
            endpointId,
            MessageType.SCORE_ACK.name,
            buildJsonObject {
                put("player_name", entry.playerName)
                put("target_index", entry.targetIndex)
            }
        )
    }

    private fun handleMatchState(message: com.archerypal.app.data.P2PMessage) {
        val payload = message.payload ?: return
        runCatching {
            AppJson.decodeFromJsonElement(MatchState.serializer(), payload)
        }.onSuccess { state ->
            val scoringType = parseScoringType(state.scoringType)
            _uiState.update {
                it.copy(
                    matchId = state.matchId,
                    phase = runCatching { MatchPhase.valueOf(state.phase) }.getOrDefault(MatchPhase.LOBBY),
                    targetCount = state.targetCount,
                    targetCountInput = if (state.targetCount > 0) state.targetCount.toString() else "",
                    scoringType = scoringType,
                    players = state.players,
                    scores = state.scores
                )
            }
        }
    }

    private fun handleMatchFinished(message: com.archerypal.app.data.P2PMessage) {
        val payload = message.payload ?: return
        val winner = payload["winner_name"]?.jsonPrimitive?.content ?: return
        val targetCount = payload["target_count"]?.jsonPrimitive?.int ?: _uiState.value.targetCount
        val matchId = payload["match_id"]?.jsonPrimitive?.content ?: _uiState.value.matchId
        val scoresObject = payload["final_scores"]?.jsonObject ?: return
        val totals = scoresObject.mapNotNull { (name, value) ->
            value.jsonPrimitive.content.toIntOrNull()?.let { name to it }
        }.toMap()
        val players = payload["player_names"]?.jsonPrimitive?.content
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: totals.keys.toList()

        viewModelScope.launch {
            persistence.recordCompletedMatch(
                SavedMatchRecord(
                    matchId = matchId,
                    completedAt = System.currentTimeMillis(),
                    targetCount = targetCount,
                    scoringType = parseScoringType(payload["scoring_type"]?.jsonPrimitive?.content).name,
                    playerNames = players,
                    winnerName = winner,
                    finalScores = totals
                ),
                players
            )
        }

        _uiState.update {
            it.copy(
                phase = MatchPhase.FINISHED,
                statusMessage = "Winner: $winner",
                targetCount = targetCount
            )
        }
        updateFriendSuggestionsAfterMatch(players)
    }

    private fun handleScoreAck(message: com.archerypal.app.data.P2PMessage) {
        val payload = message.payload ?: return
        val player = payload["player_name"]?.jsonPrimitive?.content
        val target = payload["target_index"]?.jsonPrimitive?.int
        if (player != null && target != null) {
            _uiState.update { state ->
                state.copy(
                    pendingQueue = state.pendingQueue.filterNot {
                        it.playerName == player && it.targetIndex == target
                    },
                    statusMessage = "Score confirmed"
                )
            }
        }
    }

    private fun mergeScore(entry: ScoreEntry) {
        _uiState.update { state ->
            state.copy(scores = replaceScore(state.scores, entry))
        }
    }

    private fun replaceScore(scores: List<ScoreEntry>, entry: ScoreEntry): List<ScoreEntry> {
        val filtered = scores.filterNot {
            scoreKey(it.playerName, it.targetIndex) == scoreKey(entry.playerName, entry.targetIndex)
        }
        return filtered + entry
    }

    private fun scoreForPlayerTarget(state: UiState, playerName: String, targetIndex: Int): Int? {
        return state.scores.find { it.playerName == playerName && it.targetIndex == targetIndex }?.scoreValue
            ?: state.pendingQueue.find { it.playerName == playerName && it.targetIndex == targetIndex }?.scoreValue
    }

    private fun nextTargetAfterSubmit(currentTarget: Int, targetCount: Int): Int =
        if (currentTarget < targetCount) currentTarget + 1 else currentTarget

    private fun finalizeAndPersistMatch(broadcast: Boolean) {
        val state = _uiState.value
        val totals = currentMatchTotals()
        if (totals.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "No scores to save yet") }
            return
        }
        val winner = totals.maxByOrNull { it.value }?.key ?: state.playerName
        val players = state.players.map { it.name }.distinct().ifEmpty { totals.keys.toList() }
        val record = SavedMatchRecord(
            matchId = state.matchId,
            completedAt = System.currentTimeMillis(),
            targetCount = state.targetCount,
            scoringType = state.scoringType.name,
            playerNames = players,
            winnerName = winner,
            finalScores = totals,
            isRemote = state.isRemoteMatch
        )

        viewModelScope.launch {
            persistence.recordCompletedMatch(record, players)
            if (state.isRemoteMatch && state.isHost) {
                submitRemoteMatchComplete(state, totals, winner, players)
            }
        }

        _uiState.update {
            it.copy(phase = MatchPhase.FINISHED, statusMessage = "Winner: $winner")
        }
        updateFriendSuggestionsAfterMatch(players)

        if (broadcast) {
            val scoresJson = buildJsonObject {
                totals.forEach { (name, score) -> put(name, score) }
            }
            val payload = buildJsonObject {
                put("match_id", state.matchId)
                put("winner_name", winner)
                put("target_count", state.targetCount)
                put("scoring_type", state.scoringType.name)
                put("player_names", players.joinToString(","))
                put("final_scores", scoresJson)
            }
            matchTransport.broadcast(MessageType.MATCH_FINISHED.name, payload)
            broadcastMatchState()
        }
    }

    private fun broadcastMatchSetup(targetCount: Int, scoringType: ScoringType) {
        val payload = buildJsonObject {
            put("target_count", targetCount)
            put("scoring_type", scoringType.name)
        }
        matchTransport.broadcast(MessageType.MATCH_SETUP.name, payload)
    }

    private fun broadcastMatchState() {
        val state = _uiState.value
        val matchState = MatchState(
            matchId = state.matchId,
            phase = state.phase.name,
            hostName = state.playerName,
            targetCount = state.targetCount,
            scoringType = state.scoringType.name,
            players = state.players,
            scores = state.scores
        )
        val payload = AppJson.encodeToString(MatchState.serializer(), matchState)
            .let { AppJson.parseToJsonElement(it).jsonObject }
        matchTransport.broadcast(MessageType.MATCH_STATE.name, payload)
    }

    private suspend fun submitRemoteMatchComplete(
        state: UiState,
        totals: Map<String, Int>,
        winnerName: String,
        playerNames: List<String>
    ) {
        val roomId = state.remoteRoomId ?: return
        val myCode = persistedData.friendCode ?: return
        val secret = persistedData.deviceSecret ?: return
        val creds = deviceCreds() ?: return
        val completePlayers = playerNames.mapNotNull { name ->
            val code = friendCodeForPlayerName(name) ?: return@mapNotNull null
            CompleteMatchPlayer(
                friendCode = code,
                displayName = name,
                total = totals[name] ?: 0
            )
        }
        if (completePlayers.isEmpty()) return
        val winnerCode = friendCodeForPlayerName(winnerName) ?: myCode
        matchServerApi.completeMatch(
            creds = creds, 
            roomId = roomId,
            body = CompleteMatchRequest(
                matchId = state.matchId,
                targetCount = state.targetCount,
                scoringType = state.scoringType.name,
                completedAt = System.currentTimeMillis(),
                players = completePlayers,
                winnerFriendCode = winnerCode
            )
        ).onSuccess {
            refreshSocialData()
        }.onFailure { error ->
            _uiState.update {
                it.copy(errorMessage = error.message ?: "Could not save match to server")
            }
        }
    }

    private fun friendCodeForPlayerName(name: String): String? {
        if (name == _uiState.value.playerName) {
            return persistedData.friendCode
        }
        endpointFriendCodes.values.firstOrNull()?.let { return it }
        return persistedData.friends.find { it.name.equals(name, ignoreCase = true) }?.friendCode
    }

    override fun onCleared() {
        billingManager.destroy()
        matchTransport.shutdown()
        super.onCleared()
    }
}
