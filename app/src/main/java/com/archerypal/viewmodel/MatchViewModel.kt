package com.archerypal.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.archerypal.data.AppConstants
import com.archerypal.data.AppJson
import com.archerypal.data.DiscoveredHost
import com.archerypal.data.LeaderboardRow
import com.archerypal.data.MatchPhase
import com.archerypal.data.MatchState
import com.archerypal.data.MessageType
import com.archerypal.data.PersistedAppData
import com.archerypal.data.PlayerInfo
import com.archerypal.data.QrPayload
import com.archerypal.data.SavedFriend
import com.archerypal.data.SavedMatchRecord
import com.archerypal.data.ScoreEntry
import com.archerypal.data.PersistenceRepository
import com.archerypal.data.generateMatchId
import com.archerypal.data.globalLeaderboardRows
import com.archerypal.data.matchLeaderboardRows
import com.archerypal.data.rankGlobalStats
import com.archerypal.data.rankMatchTotals
import com.archerypal.data.scoreKey
import com.archerypal.p2p.NearbyConnectionsManager
import com.archerypal.p2p.P2PEvent
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
    val isRematch: Boolean = false
)

class MatchViewModel(application: Application) : AndroidViewModel(application) {

    private val p2p = NearbyConnectionsManager(application)
    private val persistence = PersistenceRepository(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val endpointNames = mutableMapOf<String, String>()
    private var persistedData = PersistedAppData()

    init {
        viewModelScope.launch {
            persistence.dataFlow.collect { data ->
                persistedData = data
                _uiState.update { state ->
                    state.copy(
                        playerName = state.playerName.ifBlank { data.playerName },
                        savedFriends = data.friends,
                        lastMatchGroup = data.lastMatchGroup,
                        globalLeaderboard = globalLeaderboardRows(rankGlobalStats(data.globalStats.values))
                    )
                }
            }
        }

        viewModelScope.launch {
            p2p.events.collect { event ->
                when (event) {
                    is P2PEvent.EndpointConnected -> handleEndpointConnected(event.endpointId, event.name)
                    is P2PEvent.EndpointDisconnected -> handleEndpointDisconnected(event.endpointId)
                    is P2PEvent.MessageReceived -> handleMessage(event.endpointId, event.message)
                    is P2PEvent.HostDiscovered -> addDiscoveredHost(event.host)
                    is P2PEvent.Error -> _uiState.update { it.copy(errorMessage = event.message) }
                    P2PEvent.AdvertisingStarted -> _uiState.update {
                        it.copy(statusMessage = advertisingMessage(it))
                    }
                    P2PEvent.DiscoveryStarted -> _uiState.update {
                        it.copy(statusMessage = discoveryMessage(it))
                    }
                }
            }
        }

        viewModelScope.launch {
            p2p.connectedEndpoints.collect { endpoints ->
                _uiState.update { it.copy(connectedCount = endpoints.size) }
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
                scores = emptyList(),
                players = listOf(PlayerInfo(name, "local", isHost = true)),
                errorMessage = null,
                statusMessage = if (rematch) "Rematch — waiting for your group…" else "Starting host…"
            )
        }
        p2p.startHosting(name, matchId) {}
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
        p2p.startDiscovery()
    }

    fun connectToDiscoveredHost(host: DiscoveredHost) {
        _uiState.update {
            it.copy(
                matchId = host.matchId,
                hostEndpointId = host.endpointId,
                statusMessage = "Connecting to ${host.hostName}…"
            )
        }
        rememberFriend(host.hostName, host.hostName, host.matchId)
        p2p.connectToHost(host.endpointId, _uiState.value.playerName)
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
                it.copy(matchId = payload.matchId, statusMessage = "Match found: ${payload.hostName}")
            }
            rememberFriend(payload.hostName, payload.hostName, payload.matchId)
            if (_uiState.value.discoveredHosts.none { it.matchId == payload.matchId }) {
                addDiscoveredHost(host)
            }
        }.onFailure {
            _uiState.update { it.copy(errorMessage = "Invalid QR code") }
        }
    }

    fun buildQrContent(): String {
        val state = _uiState.value
        val payload = QrPayload(state.matchId, state.playerName)
        return AppConstants.QR_PREFIX + AppJson.encodeToString(QrPayload.serializer(), payload)
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
        broadcastMatchSetup(count)
        broadcastMatchState()
    }

    fun selectTarget(index: Int) {
        _uiState.update { it.copy(selectedTarget = index, pendingScore = "") }
    }

    fun appendDigit(digit: String) {
        val current = _uiState.value.pendingScore
        if (current.length >= 2) return
        val next = (current + digit).toIntOrNull() ?: return
        if (next > AppConstants.MAX_SCORE) return
        _uiState.update { it.copy(pendingScore = next.toString()) }
    }

    fun clearPendingScore() {
        _uiState.update { it.copy(pendingScore = "") }
    }

    fun submitScore() {
        val state = _uiState.value
        if (state.phase == MatchPhase.FINISHED) return
        val value = state.pendingScore.toIntOrNull() ?: return
        if (value !in AppConstants.MIN_SCORE..AppConstants.MAX_SCORE) return

        val entry = ScoreEntry(
            matchId = state.matchId,
            playerName = state.playerName,
            targetIndex = state.selectedTarget,
            scoreValue = value
        )

        if (state.isHost) {
            mergeScore(entry)
            broadcastMatchState()
            _uiState.update { it.copy(pendingScore = "") }
        } else {
            submitScoreToHost(entry)
            _uiState.update { it.copy(pendingScore = "", statusMessage = "Score sent") }
        }
    }

    fun finishMatch() {
        if (!_uiState.value.isHost || _uiState.value.phase == MatchPhase.FINISHED) return
        finalizeAndPersistMatch(broadcast = true)
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun leaveMatch() {
        p2p.shutdown()
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

    private fun rememberFriend(name: String, hostName: String?, matchId: String?) {
        viewModelScope.launch {
            persistence.rememberFriend(name, hostName, matchId)
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
            if (state.discoveredHosts.any { it.endpointId == host.endpointId && host.endpointId.isNotBlank() }) {
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
            p2p.sendPlayerJoin(endpointId, state.playerName, state.matchId)
            p2p.sendMessage(endpointId, MessageType.REQUEST_SYNC.name)
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

    private fun handleMessage(endpointId: String, message: com.archerypal.data.P2PMessage) {
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

    private fun handlePlayerJoin(endpointId: String, message: com.archerypal.data.P2PMessage) {
        if (!_uiState.value.isHost) return
        val payload = message.payload ?: return
        val playerName = payload["player_name"]?.jsonPrimitive?.content ?: return
        endpointNames[endpointId] = playerName
        rememberFriend(playerName, _uiState.value.playerName, _uiState.value.matchId)
        _uiState.update { state ->
            val updated = state.players.filterNot { it.endpointId == endpointId } +
                PlayerInfo(playerName, endpointId)
            state.copy(players = updated, statusMessage = "$playerName joined")
        }
        broadcastMatchState()
    }

    private fun handleMatchSetup(message: com.archerypal.data.P2PMessage) {
        val payload = message.payload ?: return
        val count = payload["target_count"]?.jsonPrimitive?.int ?: return
        _uiState.update {
            it.copy(
                targetCount = count,
                targetCountInput = count.toString(),
                phase = MatchPhase.SCORING,
                statusMessage = "Match started"
            )
        }
    }

    private fun handleScoreSubmit(endpointId: String, message: com.archerypal.data.P2PMessage) {
        if (!_uiState.value.isHost) return
        val payload = message.payload ?: return
        val entry = ScoreEntry(
            matchId = payload["match_id"]?.jsonPrimitive?.content ?: _uiState.value.matchId,
            playerName = payload["player_name"]?.jsonPrimitive?.content ?: return,
            targetIndex = payload["target_index"]?.jsonPrimitive?.int ?: return,
            scoreValue = payload["score_value"]?.jsonPrimitive?.int ?: return
        )
        mergeScore(entry)
        broadcastMatchState()
        p2p.sendMessage(
            endpointId,
            MessageType.SCORE_ACK.name,
            buildJsonObject {
                put("player_name", entry.playerName)
                put("target_index", entry.targetIndex)
            }
        )
    }

    private fun handleMatchState(message: com.archerypal.data.P2PMessage) {
        val payload = message.payload ?: return
        runCatching {
            AppJson.decodeFromJsonElement(MatchState.serializer(), payload)
        }.onSuccess { state ->
            _uiState.update {
                it.copy(
                    matchId = state.matchId,
                    phase = runCatching { MatchPhase.valueOf(state.phase) }.getOrDefault(MatchPhase.LOBBY),
                    targetCount = state.targetCount,
                    targetCountInput = if (state.targetCount > 0) state.targetCount.toString() else "",
                    players = state.players,
                    scores = state.scores
                )
            }
        }
    }

    private fun handleMatchFinished(message: com.archerypal.data.P2PMessage) {
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

    private fun handleScoreAck(message: com.archerypal.data.P2PMessage) {
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
            val filtered = state.scores.filterNot {
                scoreKey(it.playerName, it.targetIndex) == scoreKey(entry.playerName, entry.targetIndex)
            }
            state.copy(scores = filtered + entry)
        }
    }

    private fun submitScoreToHost(entry: ScoreEntry) {
        val payload = buildJsonObject {
            put("match_id", entry.matchId)
            put("player_name", entry.playerName)
            put("target_index", entry.targetIndex)
            put("score_value", entry.scoreValue)
        }
        _uiState.update { it.copy(pendingQueue = it.pendingQueue + entry) }
        val hostId = _uiState.value.hostEndpointId ?: p2p.connectedEndpoints.value.firstOrNull()
        if (hostId != null) {
            p2p.sendMessage(hostId, MessageType.SCORE_SUBMIT.name, payload)
        }
    }

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
                put("player_names", players.joinToString(","))
                put("final_scores", scoresJson)
            }
            p2p.broadcast(MessageType.MATCH_FINISHED.name, payload)
            broadcastMatchState()
        }
    }

    private fun broadcastMatchSetup(targetCount: Int) {
        val payload = buildJsonObject { put("target_count", targetCount) }
        p2p.broadcast(MessageType.MATCH_SETUP.name, payload)
    }

    private fun broadcastMatchState() {
        val state = _uiState.value
        val matchState = MatchState(
            matchId = state.matchId,
            phase = state.phase.name,
            hostName = state.playerName,
            targetCount = state.targetCount,
            players = state.players,
            scores = state.scores
        )
        val payload = AppJson.encodeToString(MatchState.serializer(), matchState)
            .let { AppJson.parseToJsonElement(it).jsonObject }
        p2p.broadcast(MessageType.MATCH_STATE.name, payload)
    }

    override fun onCleared() {
        p2p.shutdown()
        super.onCleared()
    }
}
