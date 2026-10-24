package com.archerypal.app.ui.screens.social

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.archerypal.app.data.GeoPoint
import com.archerypal.app.remote.FeedFilter
import com.archerypal.app.remote.FeedItem
import com.archerypal.app.remote.MutualFriendSuggestion
import com.archerypal.app.remote.PlayerProfile
import com.archerypal.app.remote.PlayerSearchResult
import com.archerypal.app.remote.RemoteComment
import com.archerypal.app.remote.RemoteFriend
import com.archerypal.app.remote.RemoteFriendRequest
import com.archerypal.app.remote.RemoteShoot
import com.archerypal.app.remote.ShootAttendee
import com.archerypal.app.ui.components.AutoSizeText
import com.archerypal.app.ui.components.OutdoorCard
import com.archerypal.app.ui.components.PrimaryActionButton
import com.archerypal.app.ui.components.SecondaryActionButton
import com.archerypal.app.ui.layout.rememberScreenMetrics
import com.archerypal.app.ui.theme.FieldGreen
import com.archerypal.app.ui.theme.ForestGreen
import com.archerypal.app.ui.theme.OutdoorBackground
import com.archerypal.app.ui.theme.SageMuted
import com.archerypal.app.ui.theme.TargetGold
import com.archerypal.app.viewmodel.FriendSuggestion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialScreen(
    playerName: String,
    friendCode: String?,
    deviceId: String?,
    deviceSecret: String?,
    profileShareLink: String?,
    googleAccountLinked: Boolean,
    isPlusActive: Boolean,
    serverReachable: Boolean?,
    remoteFriends: List<RemoteFriend>,
    feedItems: List<FeedItem>,
    feedFilter: FeedFilter,
    feedNewCounts: Map<FeedFilter, Int>,
    feedLoading: Boolean,
    socialLoading: Boolean,
    postComments: Map<String, List<RemoteComment>>,
    commentsLoadingPostIds: Set<String>,
    commentSendingPostIds: Set<String>,
    commentActions: PostCommentActions,
    myProfile: PlayerProfile?,
    viewingFriendProfile: PlayerProfile?,
    deviceLocation: GeoPoint?,
    visibleShoots: List<RemoteShoot> = emptyList(),
    shootAttendees: List<ShootAttendee>,
    shootDetailLoading: Boolean,
    pendingFriendRequests: List<RemoteFriendRequest>,
    sentFriendRequests: List<RemoteFriendRequest>,
    mutualFriendSuggestions: List<MutualFriendSuggestion>,
    friendSearchQuery: String,
    friendSearchResults: List<PlayerSearchResult>,
    friendSearchLoading: Boolean,
    friendSearchError: String?,
    onFriendSearchQueryChange: (String) -> Unit,
    onSetDiscoverable: (Boolean) -> Unit,
    playedTogetherSuggestions: List<FriendSuggestion>,
    sameShootSuggestions: List<FriendSuggestion>,
    requestedFriendCodes: Set<String>,
    requestedHub: String?,
    onRequestedHubConsumed: () -> Unit,
    onRefresh: () -> Unit,
    onSetFeedFilter: (FeedFilter) -> Unit,
    onAddFriend: (String) -> Unit,
    onAcceptFriendRequest: (String) -> Unit,
    onDeclineFriendRequest: (String) -> Unit,
    onChallengeFriend: (String) -> Unit,
    onToggleShootRsvp: (String, Boolean) -> Unit,
    onLoadShootDetail: (String) -> Unit,
    onClearShootDetail: () -> Unit,
    onCreateShoot: (String, String, String, String, Long, Long?, Boolean) -> Unit,
    onCreatePost: (String, String?, Boolean) -> Unit,
    onSaveProfile: (String, String, String, String, String) -> Unit,
    onClearProfile: () -> Unit,
    onLoadFriendProfile: (String) -> Unit,
    onClearViewingFriendProfile: () -> Unit,
    onAddProfileLink: (String) -> Unit,
    onNavigateToPlay: () -> Unit,
    onRetryServerConnection: () -> Unit,
    onRequestSignIn: () -> Unit,
    onSubscribePlus: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateShoot by rememberSaveable { mutableStateOf(false) }
    var showProfileEditor by rememberSaveable { mutableStateOf(false) }
    var addFriendMode by rememberSaveable { mutableStateOf<AddFriendMode?>(null) }
    var selectedShootId by rememberSaveable { mutableStateOf<String?>(null) }
    var showPastShoots by rememberSaveable { mutableStateOf(false) }
    var lastEventsFilter by rememberSaveable { mutableStateOf(FeedFilter.ALL.name) }
    var lastPostsFilter by rememberSaveable { mutableStateOf(FeedFilter.FRIEND_POSTS.name) }
    var selectedHub by rememberSaveable { mutableStateOf(SocialHub.FEED.name) }
    var searchFocusRequest by remember { mutableStateOf(0) }

    val hub = SocialHub.entries.find { it.name == selectedHub } ?: SocialHub.FEED

    val openFriendsHub: () -> Unit = {
        selectedHub = SocialHub.FRIENDS.name
        onRefresh()
    }

    val challengeFriend: (String) -> Unit = { code ->
        if (isPlusActive) onChallengeFriend(code) else onSubscribePlus()
    }

    LaunchedEffect(requestedHub) {
        val target = SocialHub.entries.find { it.name == requestedHub } ?: return@LaunchedEffect
        selectedHub = target.name
        if (target == SocialHub.FRIENDS) onRefresh()
        onRequestedHubConsumed()
    }

    val selectedShoot = selectedShootId?.let { id ->
        feedItems.firstNotNullOfOrNull { it.shoot?.takeIf { sh -> sh.id == id } }
            ?: visibleShoots.firstOrNull { it.id == id }
    }

    val feedSections = remember(feedItems) {
        partitionSocialFeed(feedItems)
    }
    val pastExpanded = feedSections.upcomingShoots.isEmpty() || showPastShoots

    LaunchedEffect(googleAccountLinked) {
        if (googleAccountLinked) onRefresh()
    }

    LaunchedEffect(feedFilter) {
        if (feedFilter.isPostFilter) {
            lastPostsFilter = feedFilter.name
        } else {
            lastEventsFilter = feedFilter.name
        }
    }

    val metrics = rememberScreenMetrics()

    if (!googleAccountLinked) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = metrics.horizontalPadding, vertical = metrics.verticalPadding)
                .then(
                    if (metrics.isExpandedWidth) Modifier.widthIn(max = 720.dp)
                    else Modifier
                ),
            verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing)
        ) {
            Text("Social", style = MaterialTheme.typography.headlineMedium)
            OutdoorCard {
                Text("Sign in to use Social", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Google Sign-In unlocks your feed, friends, and events. Nearby scoring on the Shoot tab works offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                )
                PrimaryActionButton(text = "Sign in with Google", onClick = onRequestSignIn)
                SecondaryActionButton(
                    text = "Go to Shoot",
                    onClick = onNavigateToPlay,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        return
    }

    Scaffold(
        modifier = modifier,
        // MainShell already pads for the status bar; default Scaffold insets would double it.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (hub == SocialHub.EVENTS) {
                FloatingActionButton(
                    onClick = {
                        if (isPlusActive) showCreateShoot = true else onSubscribePlus()
                    },
                    shape = CircleShape,
                    containerColor = FieldGreen,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Create shoot")
                }
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = feedLoading || socialLoading,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 0.dp,
                        bottom = metrics.bottomNavContentGap +
                            if (hub == SocialHub.EVENTS) 80.dp else 16.dp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (metrics.isExpandedWidth) Modifier.widthIn(max = 720.dp) else Modifier
                        )
                        .align(
                            if (metrics.isExpandedWidth) Alignment.TopCenter else Alignment.TopStart
                        )
                ) {
                    item {
                        SocialHeader(
                            playerName = playerName,
                            friendCode = friendCode,
                            serverReachable = serverReachable,
                            deviceLocation = deviceLocation,
                            onOpenProfile = { showProfileEditor = true },
                            onOpenFriends = openFriendsHub,
                            onShareProfile = { addFriendMode = AddFriendMode.MY_CODE },
                            onRetryServerConnection = onRetryServerConnection,
                            hasProfileLink = !profileShareLink.isNullOrBlank(),
                            friendsCount = remoteFriends.size,
                            pendingRequestsCount = pendingFriendRequests.size
                        )
                    }

                    item {
                        SocialHubBar(
                            hub = hub,
                            feedNewCount = postFilters.sumOf { feedNewCounts[it] ?: 0 },
                            eventsNewCount = eventFilters.sumOf { feedNewCounts[it] ?: 0 },
                            friendsNewCount = pendingFriendRequests.size,
                            onSelect = { next ->
                                selectedHub = next.name
                                when (next) {
                                    SocialHub.FEED -> onSetFeedFilter(
                                        FeedFilter.entries.find { it.name == lastPostsFilter }
                                            ?: FeedFilter.FRIEND_POSTS
                                    )
                                    SocialHub.EVENTS -> onSetFeedFilter(
                                        FeedFilter.entries.find { it.name == lastEventsFilter }
                                            ?: FeedFilter.ALL
                                    )
                                    SocialHub.FRIENDS -> onRefresh()
                                }
                            }
                        )
                    }

                    when (hub) {
                        SocialHub.FEED -> {
                        item(key = "post-filters") {
                            PostFilterBar(
                                selected = if (feedFilter.isPostFilter) feedFilter else FeedFilter.FRIEND_POSTS,
                                newCounts = feedNewCounts,
                                onSelect = onSetFeedFilter
                            )
                        }

                        item(key = "composer") {
                            if (isPlusActive) {
                                ComposePostCard(
                                    playerName = playerName,
                                    onPost = onCreatePost
                                )
                            } else {
                                PlusPostingCard(onSubscribePlus = onSubscribePlus)
                            }
                        }

                        if (pendingFriendRequests.isNotEmpty()) {
                            items(pendingFriendRequests, key = { "feed-req-${it.fromCode}" }) { request ->
                                FriendRequestFeedCard(
                                    request = request,
                                    onAccept = { onAcceptFriendRequest(request.fromCode) },
                                    onDecline = { onDeclineFriendRequest(request.fromCode) }
                                )
                            }
                        }

                        val posts = feedSections.posts
                        if (posts.isEmpty() && !feedLoading) {
                            item {
                                OutdoorCard {
                                    Text(
                                        when {
                                            feedFilter != FeedFilter.REGIONAL_POSTS ->
                                                "No posts from you or your friends yet. Share something from the range!"
                                            deviceLocation == null ->
                                                "Turn on location to see public posts from archers in your region."
                                            else ->
                                                "No public posts from archers in your region yet."
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                    )
                                }
                            }
                        } else {
                            items(posts, key = { it.id }) { item ->
                                val postId = item.postId
                                FeedItemCard(
                                    item = item,
                                    deviceId = deviceId,
                                    deviceSecret = deviceSecret,
                                    onAuthorClick = onLoadFriendProfile,
                                    onToggleShootRsvp = {},
                                    onOpenShootDetail = {},
                                    myFriendCode = friendCode,
                                    commentsState = PostCommentsState(
                                        comments = postId?.let { postComments[it] },
                                        loading = postId in commentsLoadingPostIds,
                                        sending = postId in commentSendingPostIds,
                                        canComment = isPlusActive
                                    ),
                                    commentActions = commentActions
                                )
                            }
                        }
                        }

                        SocialHub.EVENTS -> {
                        item {
                            EventFilterBar(
                                selected = feedFilter,
                                newCounts = feedNewCounts,
                                onSelect = onSetFeedFilter
                            )
                        }

                        val hasEvents = feedSections.hasVisibleContent()
                        if (!hasEvents && !feedLoading) {
                            item {
                                OutdoorCard {
                                    Text(
                                        when (feedFilter) {
                                            FeedFilter.NEARBY ->
                                                "No nearby events with location data. Try Regional or create a shoot with a mappable location."
                                            FeedFilter.FRIENDS ->
                                                "No friend shoots yet. When friends host or join shoots, they'll appear here."
                                            FeedFilter.GLOBAL ->
                                                if (deviceLocation == null) {
                                                    "Turn on location to see shoots in your region."
                                                } else {
                                                    "No public shoots in your region right now."
                                                }
                                            else ->
                                                "No events in this view. Create a shoot or check back later."
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                    )
                                }
                            }
                        } else {
                            if (feedSections.upcomingShoots.isNotEmpty()) {
                                item(key = "upcoming-header") {
                                    FeedSectionHeader(
                                        title = "Upcoming",
                                        count = feedSections.upcomingShoots.size
                                    )
                                }
                                items(feedSections.upcomingShoots, key = { it.id }) { item ->
                                    val shoot = item.shoot ?: return@items
                                    ShootFeedCard(
                                        shoot = shoot,
                                        category = item.category,
                                        compactPast = false,
                                        onToggleRsvp = {
                                            onToggleShootRsvp(shoot.id, shoot.viewerIsGoing)
                                        },
                                        onOpenDetail = {
                                            selectedShootId = shoot.id
                                            onLoadShootDetail(shoot.id)
                                        }
                                    )
                                }
                            } else if (feedSections.pastShoots.isNotEmpty()) {
                                item(key = "no-upcoming") {
                                    Text(
                                        "No upcoming shoots in this view.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SageMuted,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                    )
                                }
                            }

                            if (feedSections.pastShoots.isNotEmpty()) {
                                item(key = "past-header") {
                                    PastShootsHeader(
                                        count = feedSections.pastShoots.size,
                                        expanded = pastExpanded,
                                        canCollapse = feedSections.upcomingShoots.isNotEmpty(),
                                        onToggle = { showPastShoots = !showPastShoots }
                                    )
                                }
                                if (pastExpanded) {
                                    items(feedSections.pastShoots, key = { it.id }) { item ->
                                        val shoot = item.shoot ?: return@items
                                        ShootFeedCard(
                                            shoot = shoot,
                                            category = item.category,
                                            compactPast = true,
                                            onToggleRsvp = {},
                                            onOpenDetail = {
                                                selectedShootId = shoot.id
                                                onLoadShootDetail(shoot.id)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        }

                        SocialHub.FRIENDS -> {
                            friendsHubItems(
                                remoteFriends = remoteFriends,
                                pendingFriendRequests = pendingFriendRequests,
                                sentFriendRequests = sentFriendRequests,
                                mutualFriendSuggestions = mutualFriendSuggestions,
                                playedTogether = playedTogetherSuggestions,
                                sameShoot = sameShootSuggestions,
                                requestedFriendCodes = requestedFriendCodes,
                                socialLoading = socialLoading,
                                searchQuery = friendSearchQuery,
                                searchResults = friendSearchResults,
                                searchLoading = friendSearchLoading,
                                searchError = friendSearchError,
                                searchFocusRequest = searchFocusRequest,
                                onSearchQueryChange = onFriendSearchQueryChange,
                                onOpenAddFriend = { addFriendMode = AddFriendMode.MENU },
                                onAddFriend = onAddFriend,
                                onAcceptFriendRequest = onAcceptFriendRequest,
                                onDeclineFriendRequest = onDeclineFriendRequest,
                                onChallengeFriend = challengeFriend,
                                onViewProfile = onLoadFriendProfile,
                                showTitle = false
                            )
                        }
                    }

                    if (feedLoading && feedItems.isEmpty() &&
                        (hub == SocialHub.EVENTS ||
                            (hub == SocialHub.FEED && feedSections.posts.isEmpty()))
                    ) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateShoot) {
        CreateShootDialog(
            onDismiss = { showCreateShoot = false },
            onCreate = { title, location, region, description, startsAt, endsAt, isPublic ->
                onCreateShoot(title, location, region, description, startsAt, endsAt, isPublic)
                showCreateShoot = false
            }
        )
    }

    if (showProfileEditor) {
        ProfileEditorSheet(
            playerName = playerName,
            friendCode = friendCode,
            profile = myProfile,
            onDismiss = { showProfileEditor = false },
            onSetDiscoverable = onSetDiscoverable,
            onSave = { bio, club, bow, range, loc ->
                onSaveProfile(bio, club, bow, range, loc)
                showProfileEditor = false
            },
            onClear = onClearProfile
        )
    }

    viewingFriendProfile?.let { profile ->
        FriendProfileSheet(
            profile = profile,
            onDismiss = onClearViewingFriendProfile,
            onChallenge = {
                challengeFriend(profile.friendCode)
                onClearViewingFriendProfile()
            }
        )
    }

    selectedShoot?.let { shoot ->
        ShootDetailSheet(
            shoot = shoot,
            attendees = shootAttendees,
            loading = shootDetailLoading,
            onDismiss = {
                selectedShootId = null
                onClearShootDetail()
            },
            onToggleRsvp = { onToggleShootRsvp(shoot.id, shoot.viewerIsGoing) },
            onGoToPlay = onNavigateToPlay,
            myFriendCode = friendCode,
            requestedFriendCodes = requestedFriendCodes,
            onRequestFriend = onAddFriend
        )
    }

    addFriendMode?.let { mode ->
        AddFriendSheet(
            friendCode = friendCode,
            profileShareLink = profileShareLink,
            initialMode = mode,
            onDismiss = { addFriendMode = null },
            onAddFriend = onAddFriend,
            onAddProfileLink = onAddProfileLink,
            onSearchByName = {
                addFriendMode = null
                selectedHub = SocialHub.FRIENDS.name
                searchFocusRequest++
            }
        )
    }
}

@Composable
private fun ComposePostCard(
    playerName: String,
    onPost: (body: String, imageUri: String?, isPublic: Boolean) -> Unit
) {
    var body by rememberSaveable { mutableStateOf("") }
    var isPublic by rememberSaveable { mutableStateOf(false) }
    val canPost = body.isNotBlank()

    OutdoorCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                AvatarCircle(playerName, size = 40.dp)
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("What's happening at the range?") },
                    minLines = 2,
                    maxLines = 6
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = isPublic,
                        onValueChange = { isPublic = it },
                        role = Role.Checkbox
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = isPublic,
                    onCheckedChange = null
                )
                Text(
                    "Public — show to archers in my region",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        onPost(body.trim(), null, isPublic)
                        body = ""
                        isPublic = false
                    },
                    enabled = canPost
                ) {
                    Text("Post")
                }
            }
        }
    }
}

@Composable
private fun PlusPostingCard(onSubscribePlus: () -> Unit) {
    OutdoorCard(modifier = Modifier.fillMaxWidth()) {
        Text("Posting is part of Plus", style = MaterialTheme.typography.titleMedium)
        Text(
            "Read the feed, add friends, and RSVP to shoots for free. " +
                "Plus lets you share your own posts with friends or everyone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
        )
        SecondaryActionButton(text = "See Plus", onClick = onSubscribePlus)
    }
}

@Composable
private fun FeedSectionHeader(
    title: String,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium,
            color = SageMuted
        )
    }
}

@Composable
private fun PastShootsHeader(
    count: Int,
    expanded: Boolean,
    canCollapse: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp)
            .then(
                if (canCollapse) Modifier.clickable(onClick = onToggle) else Modifier
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Past",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = SageMuted
        )
        Text(
            when {
                !canCollapse -> "$count"
                expanded -> "Hide · $count"
                else -> "Show · $count"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (canCollapse) TargetGold else SageMuted
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SocialHeader(
    playerName: String,
    friendCode: String?,
    serverReachable: Boolean?,
    deviceLocation: GeoPoint?,
    onOpenProfile: () -> Unit,
    onOpenFriends: () -> Unit,
    onShareProfile: () -> Unit,
    onRetryServerConnection: () -> Unit,
    hasProfileLink: Boolean,
    friendsCount: Int,
    pendingRequestsCount: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                ForestGreen.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.15f)
                            )
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenProfile)
                ) {
                    AvatarCircle(playerName, size = 44.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        AutoSizeText(
                            "Social",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        val separatorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        Text(
                            buildAnnotatedString {
                                append(playerName)
                                friendCode?.let {
                                    withStyle(SpanStyle(color = separatorColor)) { append(" · ") }
                                    withStyle(SpanStyle(color = TargetGold)) { append(it) }
                                }
                                withStyle(SpanStyle(color = separatorColor)) { append(" · ") }
                                append("$friendsCount\u00A0friend${if (friendsCount == 1) "" else "s"}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box {
                    IconButton(
                        onClick = onOpenFriends,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.Outlined.Groups, contentDescription = "Friends")
                    }
                    if (pendingRequestsCount > 0) {
                        Surface(
                            color = TargetGold,
                            shape = CircleShape,
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Text(
                                "$pendingRequestsCount",
                                color = OutdoorBackground,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ServerStatusPill(
                serverReachable = serverReachable,
                onRetry = onRetryServerConnection
            )
            deviceLocation?.let {
                Text(
                    "Location on",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }

        if (hasProfileLink) {
            SecondaryActionButton(
                text = "My code",
                onClick = onShareProfile
            )
        }
    }
}

@Composable
private fun ServerStatusPill(
    serverReachable: Boolean?,
    onRetry: () -> Unit
) {
    val (label, color) = when (serverReachable) {
        true -> "● Server connected" to FieldGreen
        false -> "● Server unreachable — tap to retry" to MaterialTheme.colorScheme.error
        null -> "● Checking server…" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = Modifier.clickable(enabled = serverReachable == false) { onRetry() }
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private data class SocialFeedSections(
    val posts: List<FeedItem>,
    val upcomingShoots: List<FeedItem>,
    val pastShoots: List<FeedItem>
) {
    fun hasVisibleContent(): Boolean =
        posts.isNotEmpty() || upcomingShoots.isNotEmpty() || pastShoots.isNotEmpty()
}

private fun partitionSocialFeed(feedItems: List<FeedItem>): SocialFeedSections {
    val posts = mutableListOf<FeedItem>()
    val upcoming = mutableListOf<FeedItem>()
    val past = mutableListOf<FeedItem>()
    for (item in feedItems) {
        when (item.type) {
            "post" -> posts += item
            "shoot" -> {
                val shoot = item.shoot ?: continue
                if (shoot.isPast()) past += item else upcoming += item
            }
        }
    }
    posts.sortByDescending { it.createdAt }
    upcoming.sortBy { it.shoot?.startsAt ?: Long.MAX_VALUE }
    past.sortByDescending { it.shoot?.startsAt ?: 0L }
    return SocialFeedSections(posts, upcoming, past)
}
