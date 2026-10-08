package com.archerypal.app.ui.screens.social

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.archerypal.app.remote.MutualFriendSuggestion
import com.archerypal.app.remote.PlayerSearchResult
import com.archerypal.app.remote.RemoteFriend
import com.archerypal.app.remote.RemoteFriendRequest
import com.archerypal.app.ui.components.AutoSizeText
import com.archerypal.app.ui.components.OutdoorCard
import com.archerypal.app.ui.components.PrimaryActionButton
import com.archerypal.app.ui.theme.FieldGreen
import com.archerypal.app.ui.theme.SageMuted
import com.archerypal.app.ui.theme.TargetGold
import com.archerypal.app.viewmodel.FriendSuggestion

private const val SEARCH_MIN_CHARS = 2

fun LazyListScope.friendsHubItems(
    remoteFriends: List<RemoteFriend>,
    pendingFriendRequests: List<RemoteFriendRequest>,
    sentFriendRequests: List<RemoteFriendRequest>,
    mutualFriendSuggestions: List<MutualFriendSuggestion>,
    playedTogether: List<FriendSuggestion>,
    sameShoot: List<FriendSuggestion>,
    requestedFriendCodes: Set<String>,
    socialLoading: Boolean,
    searchQuery: String,
    searchResults: List<PlayerSearchResult>,
    searchLoading: Boolean,
    searchError: String?,
    searchFocusRequest: Int,
    onSearchQueryChange: (String) -> Unit,
    onOpenAddFriend: () -> Unit,
    onAddFriend: (String) -> Unit,
    onAcceptFriendRequest: (String) -> Unit,
    onDeclineFriendRequest: (String) -> Unit,
    onChallengeFriend: (String) -> Unit,
    onViewProfile: (String) -> Unit,
    showTitle: Boolean = true
) {
    if (showTitle) {
        item { Text("Friends", style = MaterialTheme.typography.headlineSmall) }
    }

    item(key = "friend-search") {
        FriendSearchField(
            query = searchQuery,
            loading = searchLoading,
            focusRequest = searchFocusRequest,
            onQueryChange = onSearchQueryChange
        )
    }

    if (searchQuery.isNotBlank()) {
        searchResultItems(
            query = searchQuery,
            results = searchResults,
            loading = searchLoading,
            error = searchError,
            onAddFriend = onAddFriend,
            onAcceptFriendRequest = onAcceptFriendRequest,
            onViewProfile = onViewProfile
        )
        return
    }

    item(key = "requests-header") {
        SectionHeader(
            title = "Friend requests",
            count = pendingFriendRequests.size,
            highlight = pendingFriendRequests.isNotEmpty()
        )
    }
    if (pendingFriendRequests.isEmpty()) {
        item(key = "requests-empty") {
            EmptyHint("No pending requests. When someone adds you, you can accept or decline here.")
        }
    } else {
        items(pendingFriendRequests, key = { "req-${it.fromCode}" }) { request ->
            IncomingRequestCard(
                request = request,
                onAccept = { onAcceptFriendRequest(request.fromCode) },
                onDecline = { onDeclineFriendRequest(request.fromCode) }
            )
        }
    }

    if (sentFriendRequests.isNotEmpty()) {
        item(key = "sent-header") {
            SectionHeader(title = "Sent requests", count = sentFriendRequests.size)
        }
        items(sentFriendRequests, key = { "sent-${it.toCode}" }) { request ->
            PersonRow(
                name = request.toDisplayName.ifBlank { request.toCode },
                subtitle = "${request.toCode} · sent ${formatRelativeTime(request.createdAtMs)}"
            ) {
                StatusLabel("Pending")
            }
        }
    }

    item(key = "add-friend") {
        PrimaryActionButton(
            text = "Add friend",
            onClick = onOpenAddFriend,
            modifier = Modifier.fillMaxWidth()
        )
    }

    item(key = "friends-header") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader(
                title = "Your friends",
                count = remoteFriends.size,
                modifier = Modifier.weight(1f)
            )
            if (socialLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(18.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }

    if (remoteFriends.isEmpty()) {
        item(key = "friends-empty") {
            EmptyHint(
                "Search for archers by name above, scan their code at the range, " +
                    "share your invite link, or add them after a match."
            )
        }
    } else {
        items(remoteFriends, key = { "friend-${it.friendCode}" }) { friend ->
            PersonRow(
                name = friend.displayName,
                subtitle = friend.friendCode,
                onClick = { onViewProfile(friend.friendCode) }
            ) {
                CompactPrimaryButton(text = "Challenge", onClick = { onChallengeFriend(friend.friendCode) })
            }
        }
    }

    val mutualCodes = mutualFriendSuggestions.map { it.friendCode.uppercase() }.toSet()
    if (mutualFriendSuggestions.isNotEmpty()) {
        item(key = "mutual-header") {
            SectionHeader(title = "People you may know")
        }
        items(mutualFriendSuggestions, key = { "mutual-${it.friendCode}" }) { suggestion ->
            val requested = suggestion.friendCode.uppercase() in requestedFriendCodes
            PersonRow(
                name = suggestion.displayName,
                subtitle = mutualLabel(suggestion.mutualCount)
            ) {
                if (requested) StatusLabel("Requested")
                else CompactOutlinedButton(text = "Request", onClick = { onAddFriend(suggestion.friendCode) })
            }
        }
    }

    val friendCodes = remoteFriends.map { it.friendCode.uppercase() }.toSet()
    val filteredPlayed = playedTogether.filterNot {
        it.friendCode.uppercase() in friendCodes || it.friendCode.uppercase() in mutualCodes
    }
    suggestionSection(
        title = "Played together",
        keyPrefix = "played",
        suggestions = filteredPlayed,
        requestedFriendCodes = requestedFriendCodes,
        onAddFriend = onAddFriend
    )
    suggestionSection(
        title = "Going to the same shoot",
        keyPrefix = "shoot",
        suggestions = sameShoot.filterNot { s ->
            val code = s.friendCode.uppercase()
            code in friendCodes || code in mutualCodes ||
                filteredPlayed.any { it.friendCode.equals(s.friendCode, ignoreCase = true) }
        },
        requestedFriendCodes = requestedFriendCodes,
        onAddFriend = onAddFriend
    )
}

private fun LazyListScope.searchResultItems(
    query: String,
    results: List<PlayerSearchResult>,
    loading: Boolean,
    error: String?,
    onAddFriend: (String) -> Unit,
    onAcceptFriendRequest: (String) -> Unit,
    onViewProfile: (String) -> Unit
) {
    when {
        query.trim().length < SEARCH_MIN_CHARS -> item(key = "search-hint") {
            EmptyHint("Type at least $SEARCH_MIN_CHARS letters of their name.")
        }
        error != null -> item(key = "search-error") { EmptyHint(error) }
        results.isEmpty() && !loading -> item(key = "search-empty") {
            EmptyHint(
                "No archers found for \"${query.trim()}\". They may have turned off name search. " +
                    "Try their Friend ID or invite link instead."
            )
        }
        else -> items(results, key = { "search-${it.friendCode}" }) { result ->
            val isFriend = result.relation == PlayerSearchResult.RELATION_FRIEND
            PersonRow(
                name = result.displayName,
                subtitle = result.friendCode,
                onClick = if (isFriend) ({ onViewProfile(result.friendCode) }) else null
            ) {
                when (result.relation) {
                    PlayerSearchResult.RELATION_FRIEND -> StatusLabel("Friends", color = FieldGreen)
                    PlayerSearchResult.RELATION_REQUESTED -> StatusLabel("Requested")
                    PlayerSearchResult.RELATION_INCOMING -> CompactPrimaryButton(
                        text = "Accept",
                        onClick = { onAcceptFriendRequest(result.friendCode) }
                    )
                    else -> CompactOutlinedButton(
                        text = "Request",
                        onClick = { onAddFriend(result.friendCode) }
                    )
                }
            }
        }
    }
}

private fun LazyListScope.suggestionSection(
    title: String,
    keyPrefix: String,
    suggestions: List<FriendSuggestion>,
    requestedFriendCodes: Set<String>,
    onAddFriend: (String) -> Unit
) {
    if (suggestions.isEmpty()) return
    item(key = "$keyPrefix-header") {
        SectionHeader(title = title)
    }
    items(suggestions, key = { "$keyPrefix-${it.friendCode}" }) { suggestion ->
        val requested = suggestion.friendCode.uppercase() in requestedFriendCodes
        PersonRow(name = suggestion.name, subtitle = null) {
            if (requested) StatusLabel("Requested")
            else CompactOutlinedButton(text = "Request", onClick = { onAddFriend(suggestion.friendCode) })
        }
    }
}

private fun mutualLabel(count: Int): String =
    if (count == 1) "1 mutual friend" else "$count mutual friends"

@Composable
private fun FriendSearchField(
    query: String,
    loading: Boolean,
    focusRequest: Int,
    onQueryChange: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequest) {
        if (focusRequest > 0) focusRequester.requestFocus()
    }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        placeholder = { Text("Search archers by name") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                query.isNotEmpty() -> IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(999.dp)
    )
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    highlight: Boolean = false
) {
    Text(
        text = if (count != null) "$title ($count)" else title,
        style = MaterialTheme.typography.titleMedium,
        color = if (highlight) TargetGold else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(top = 4.dp)
    )
}

@Composable
private fun EmptyHint(text: String) {
    OutdoorCard {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        )
    }
}

@Composable
private fun IncomingRequestCard(
    request: RemoteFriendRequest,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    OutdoorCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarCircle(request.fromDisplayName)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    request.fromDisplayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${request.fromCode} · ${formatRelativeTime(request.createdAtMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SageMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Button(
                onClick = onAccept,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FieldGreen,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                AutoSizeText("Accept", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onDecline,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                AutoSizeText("Decline")
            }
        }
    }
}

@Composable
private fun PersonRow(
    name: String,
    subtitle: String?,
    onClick: (() -> Unit)? = null,
    action: @Composable RowScope.() -> Unit
) {
    OutdoorCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarCircle(name)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            action()
        }
    }
}

private val CompactButtonPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)

@Composable
private fun CompactPrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        contentPadding = CompactButtonPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = FieldGreen,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun CompactOutlinedButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        contentPadding = CompactButtonPadding,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun StatusLabel(text: String, color: Color = TargetGold) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, softWrap = false)
}
