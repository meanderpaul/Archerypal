package com.archerypal.app.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.archerypal.app.billing.BillingManager
import com.archerypal.app.data.AppConstants
import com.archerypal.app.data.AppJson
import com.archerypal.app.data.DiscoveredHost
import com.archerypal.app.data.LeaderboardRow
import com.archerypal.app.data.MatchPhase
import com.archerypal.app.data.MatchState
import com.archerypal.app.data.MessageType
import com.archerypal.app.data.PersistedAppData
import com.archerypal.app.data.PlayerInfo
import com.archerypal.app.data.QrPayload
import com.archerypal.app.data.SavedFriend
import com.archerypal.app.data.SavedMatchRecord
import com.archerypal.app.data.ScoreEntry
import com.archerypal.app.data.ScoringType
import com.archerypal.app.data.PersistenceRepository
import com.archerypal.app.data.generateMatchId
import com.archerypal.app.data.globalLeaderboardRows
import com.archerypal.app.data.matchLeaderboardRows
import com.archerypal.app.data.parseScoringType
import com.archerypal.app.data.rankGlobalStats
import com.archerypal.app.data.rankMatchTotals
import com.archerypal.app.data.scoreKey
import com.archerypal.app.p2p.HybridMatchTransport
import com.archerypal.app.p2p.P2PEvent
import com.archerypal.app.p2p.LIBP2P_HOST_ENDPOINT_ID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

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
    val statusMessage: String = "",
    val errorMessage: String? = null,
    val hostEndpointId: String? = null,
    val pendingQueue: List<ScoreEntry> = emptyList(),
    val savedFriends: List<SavedFriend> = emptyList(),
    val lastMatchGroup: List<String> = emptyList(),
    val globalLeaderboard: List<LeaderboardRow> = emptyList(),
    val resyncHostName: String? = null,
    val isRematch: Boolean = false,
    val transportLabel: String = "",
    val isAdFree: Boolean = false,
    val removeAdsPrice: String? = null,
    val billingMessage: String? = null
)

class MatchViewModel(application: Application) : AndroidViewModel(application) {

    private val transport = HybridMatchTransport(application)
    private val persistence = PersistenceRepository(application)
    private val billingManager = BillingManager(
        context = application,
        scope = viewModelScope,
        onAdFreeGranted = { persistence.setAdFree(true) }
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val endpointNames = mutableMapOf<String, String>()
    private var persistedData = PersistedAppData()

    init {
        billingManager.startConnection()

        viewModelScope.launch {
            billingManager.isAdFree.collect { isAdFree ->
                _uiState.update { it.copy(isAdFree = isAdFree) }
            }
        }
        viewModelScope.launch {
            billingManager.removeAdsPrice.collect { price ->
                _uiState.update { it.copy(removeAdsPrice = price) }
            }
        }
        viewModelScope.launch {
            billingManager.billingMessage.collect { message ->
                _uiState.update { it.copy(billingMessage = message) }
            }
        }

        viewModelScope.launch {
            persistence.dataFlow.collect { data ->
                persistedData = data
                billingManager.setCachedAdFree(data.isAdFree)
                _uiState.update { state ->
                    state.copy(
                        playerName = state.playerName.ifBlank { data.playerName },
                        savedFriends = data.friends,
                        lastMatchGroup = data.lastMatchGroup,
                        globalLeaderboard = globalLeaderboardRows(rankGlobalStats(data.globalStats.values)),
                        isAdFree = data.isAdFree || state.isAdFree
                    )
                }
            }
        }

        viewModelScope.launch {
            transport.events.collect { event ->
                when (event) {
                    is P2PEvent.EndpointConnected -> handleEndpointConnected(event.endpointId, event.name)
                    is P2PEvent.EndpointDisconnected -> handleEndpointDisconnected(event.endpointId)
                    is P2PEvent.MessageReceived -> handleMessage(event.endpointId, event.message)
                    is P2PEvent.HostDiscovered -> addDiscoveredHost(event.host)
                    is P2PEvent.Error -> _uiState.update { it.copy(errorMessage = event.message) }
                    P2PEvent.AdvertisingStarted -> _uiState.update {
                        it.copy(
                            statusMessage = advertisingMessage(it),
                            transportLabel = transport.statusSuffix()
                        )
                    }
                    is P2PEvent.RelayStatusChanged -> _uiState.update {
                        it.copy(transportLabel = transport.statusSuffix())
                    }
                    P2PEvent.DiscoveryStarted -> _uiState.update {
                        it.copy(
                            statusMessage = discoveryMessage(it),
                            transportLabel = transport.statusSuffix()
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            transport.connectedEndpoints.collect { endpoints ->
                _uiState.update {
                    it.copy(
                        connectedCount = endpoints.size,
                        transportLabel = transport.statusSuffix()
                    )
                }
            }
        }

        viewModelScope.launch {
            transport.activePaths.collect {
                _uiState.update { state ->
                    state.copy(transportLabel = transport.statusSuffix())
                }
            }
        }
    }

    fun setPlayerName(name: String) {
        val trimmed = name.trim()
        _uiState.update { it.copy(playerName = trimmed) }
        if (trimmed.isNotBlank()) {
            viewModelScope.launch { persistence.savePlayerName(trimmed) }
        }
    }

    fun startHosting() = startHostingInternal(rematch = false)

    fun startRematchAsHost() = startHostingInternal(rematch = true)

    private fun startHostingInternal(rematch: Boolean) {
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
                matchId = matchId,
                phase = MatchPhase.LOBBY,
                targetCount = 0,
                targetCountInput = "",
                scoringType = ScoringType.ASA,
                scores = emptyList(),
                players = listOf(PlayerInfo(name, "local", isHost = true)),
                errorMessage = null,
                statusMessage = if (rematch) "Rematch — waiting for your group…" else "Starting host…"
            )
        }
        transport.startHosting(name, matchId) {}
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
                errorMessage = null,
                statusMessage = if (resyncHostName != null) {
                    "Resyncing with $resyncHostName…"
                } else {
                    "Starting discovery…"
                }
            )
        }
        transport.startDiscovery(resyncHostName, name)
        if (resyncHostName != null) {
            val friend = persistedData.friends.firstOrNull {
                it.name.equals(resyncHostName, ignoreCase = true) ||
                    it.lastHostName?.equals(resyncHostName, ignoreCase = true) == true
            }
            if (friend != null &&
                !friend.lastLibp2pPeerId.isNullOrBlank() &&
                (friend.lastLibp2pCircuitMultiaddrs.isNotEmpty() || friend.lastLibp2pMultiaddrs.isNotEmpty())
            ) {
                transport.connectToHost(
                    DiscoveredHost(
                        endpointId = LIBP2P_HOST_ENDPOINT_ID,
                        hostName = resyncHostName,
                        matchId = friend.lastMatchId.orEmpty(),
                        libp2pPeerId = friend.lastLibp2pPeerId,
                        libp2pCircuitMultiaddrs = friend.lastLibp2pCircuitMultiaddrs,
                        libp2pMultiaddrs = friend.lastLibp2pMultiaddrs
                    ),
                    name
                )
            }
        }
    }

    fun connectToDiscoveredHost(host: DiscoveredHost) {
        _uiState.update {
            it.copy(
                matchId = host.matchId,
                hostEndpointId = host.endpointId,
                statusMessage = "Connecting to ${host.hostName}…"
            )
        }
        rememberFriend(
            host.hostName,
            host.hostName,
            host.matchId,
            host.libp2pPeerId,
            host.libp2pCircuitMultiaddrs,
            host.libp2pMultiaddrs
        )
        transport.connectToHost(host, _uiState.value.playerName)
    }

    fun onQrScanned(raw: String) {
        val json = raw.removePrefix(AppConstants.QR_PREFIX)
        runCatching {
            AppJson.decodeFromString(QrPayload.serializer(), json)
        }.onSuccess { payload ->
            val host = DiscoveredHost(
                endpointId = LIBP2P_HOST_ENDPOINT_ID,
                hostName = payload.hostName,
                matchId = payload.matchId,
                libp2pPeerId = payload.libp2pPeerId,
                libp2pCircuitMultiaddrs = payload.libp2pCircuitMultiaddrs,
                libp2pMultiaddrs = payload.libp2pMultiaddrs
            )
            _uiState.update {
                it.copy(matchId = payload.matchId, statusMessage = "Match found: ${payload.hostName}")
            }
            rememberFriend(
                payload.hostName,
                payload.hostName,
                payload.matchId,
                payload.libp2pPeerId,
                payload.libp2pCircuitMultiaddrs,
                payload.libp2pMultiaddrs
            )
            if (_uiState.value.discoveredHosts.none { it.matchId == payload.matchId }) {
                addDiscoveredHost(host)
            }
        }.onFailure {
            _uiState.update { it.copy(errorMessage = "Invalid QR code") }
        }
    }

    fun buildQrContent(): String {
        val state = _uiState.value
        val ad = transport.libp2pAdvertisement()
        val payload = QrPayload(
            matchId = state.matchId,
            hostName = state.playerName,
            libp2pPeerId = ad.peerId,
            libp2pCircuitMultiaddrs = ad.circuitMultiaddrs,
            libp2pMultiaddrs = ad.directMultiaddrs
        )
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
            val hostId = state.hostEndpointId ?: transport.connectedEndpoints.value.firstOrNull()
            if (hostId != null) {
                transport.sendMessage(
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

    fun launchRemoveAdsPurchase(activity: Activity) {
        billingManager.launchRemoveAdsPurchase(activity)
    }

    fun leaveMatch() {
        transport.shutdown()
        val name = _uiState.value.playerName
        _uiState.value = UiState(
            playerName = name,
            savedFriends = persistedData.friends,
            lastMatchGroup = persistedData.lastMatchGroup,
            globalLeaderboard = globalLeaderboardRows(rankGlobalStats(persistedData.globalStats.values))
        )
        endpointNames.clear()
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

    private fun libp2pPeerId(): String? = transport.libp2pAdvertisement().peerId
    private fun libp2pCircuitMultiaddrs(): List<String> = transport.libp2pAdvertisement().circuitMultiaddrs
    private fun libp2pMultiaddrs(): List<String> = transport.libp2pAdvertisement().directMultiaddrs

    private fun rememberFriend(
        name: String,
        hostName: String?,
        matchId: String?,
        libp2pPeerId: String? = null,
        libp2pCircuitMultiaddrs: List<String> = emptyList(),
        libp2pMultiaddrs: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            persistence.rememberFriend(
                name,
                hostName,
                matchId,
                libp2pPeerId,
                libp2pCircuitMultiaddrs,
                libp2pMultiaddrs
            )
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

        _uiState.update { state ->
            if (state.discoveredHosts.any { it.matchId == host.matchId }) {
                state
            } else {
                state.copy(discoveredHosts = state.discoveredHosts + host)
            }
        }
        val state = _uiState.value
        if (!state.isHost && host.endpointId.isNotBlank() &&
            (state.matchId.isBlank() || state.matchId == host.matchId)
        ) {
            connectToDiscoveredHost(host)
        }
    }

    private fun handleEndpointConnected(endpointId: String, name: String) {
        endpointNames[endpointId] = name
        val state = _uiState.value
        rememberFriend(name, if (state.isHost) state.playerName else name, state.matchId)
        if (state.isHost) {
            _uiState.update { it.copy(statusMessage = "Archer connected") }
        } else {
            _uiState.update { it.copy(hostEndpointId = endpointId, phase = MatchPhase.LOBBY) }
            rememberFriend(state.playerName, name, state.matchId)
            transport.sendPlayerJoin(endpointId, state.playerName, state.matchId)
            transport.sendMessage(endpointId, MessageType.REQUEST_SYNC.name)
        }
    }

    private fun handleEndpointDisconnected(endpointId: String) {
        endpointNames.remove(endpointId)
        if (_uiState.value.isHost) {
            _uiState.update { state ->
                state.copy(players = state.players.filterNot { it.endpointId == endpointId })
            }
            broadcastMatchState()
        }
    }

    private fun handleMessage(endpointId: String, message: com.archerypal.app.data.P2PMessage) {
        when (message.type) {
            MessageType.PLAYER_JOIN.name -> handlePlayerJoin(endpointId, message)
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
        endpointNames[endpointId] = playerName
        rememberFriend(
            playerName,
            _uiState.value.playerName,
            _uiState.value.matchId,
            libp2pPeerId(),
            libp2pCircuitMultiaddrs(),
            libp2pMultiaddrs()
        )
        _uiState.update { state ->
            val updated = state.players.filterNot { it.endpointId == endpointId } +
                PlayerInfo(playerName, endpointId)
            state.copy(players = updated, statusMessage = "$playerName joined")
        }
        broadcastMatchState()
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
        transport.sendMessage(
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
            finalScores = totals
        )

        viewModelScope.launch {
            persistence.recordCompletedMatch(record, players)
        }

        _uiState.update {
            it.copy(phase = MatchPhase.FINISHED, statusMessage = "Winner: $winner")
        }

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
            transport.broadcast(MessageType.MATCH_FINISHED.name, payload)
            broadcastMatchState()
        }
    }

    private fun broadcastMatchSetup(targetCount: Int, scoringType: ScoringType) {
        val payload = buildJsonObject {
            put("target_count", targetCount)
            put("scoring_type", scoringType.name)
        }
        transport.broadcast(MessageType.MATCH_SETUP.name, payload)
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
        transport.broadcast(MessageType.MATCH_STATE.name, payload)
    }

    override fun onCleared() {
        billingManager.destroy()
        transport.release()
        super.onCleared()
    }
}
