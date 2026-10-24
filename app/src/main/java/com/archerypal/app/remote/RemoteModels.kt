package com.archerypal.app.remote

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val friendCode: String,
    val displayName: String,
    val deviceSecret: String,
    val fcmToken: String? = null,
    val inviteCode: String? = null
)

@Serializable
data class RegisterResponse(
    val friendCode: String,
    val deviceId: String? = null
)

@Serializable
data class DeviceBindResponse(
    val deviceId: String,
    val friendCode: String? = null
)

@Serializable
data class CreatePostJsonRequest(
    val body: String,
    val isPublic: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null
)

/** Credentials for sealed HMAC REST calls. */
data class DeviceCredentials(
    val friendCode: String,
    val deviceId: String,
    val deviceSecret: String
)

@Serializable
data class PatchMeRequest(
    val displayName: String? = null,
    val fcmToken: String? = null,
    val discoverable: Boolean? = null
)

@Serializable
data class AddFriendRequest(
    val theirFriendCode: String
)

@Serializable
data class FriendResponse(
    val friend: RemoteFriend
)

@Serializable
data class RemoteFriend(
    val friendCode: String,
    val displayName: String
)

@Serializable
data class ChallengeRequest(
    val targetFriendCode: String
)

@Serializable
data class ChallengeResponse(
    val roomId: String,
    val wsUrl: String,
    val joinToken: String,
    /** Host room JWT; preferred over embedding the token in [wsUrl]. */
    val hostToken: String? = null,
    val targetDisplayName: String? = null,
    /** True when the invitee has a push token, so they were sent a notification. */
    val targetNotified: Boolean = false
)

@Serializable
data class IncomingChallenge(
    val roomId: String,
    val hostFriendCode: String,
    val hostDisplayName: String,
    val createdAt: Long = 0,
    val expiresAt: Long = 0
)

@Serializable
data class IncomingChallengesResponse(
    val challenges: List<IncomingChallenge> = emptyList()
)

@Serializable
data class ChallengeJoinResponse(
    val roomId: String,
    val wsUrl: String,
    val joinToken: String,
    val hostDisplayName: String? = null
)

@Serializable
data class HealthResponse(
    val status: String
)

@Serializable
data class LeaderboardEntry(
    val friendCode: String,
    val displayName: String,
    val matchesPlayed: Int = 0,
    val matchesWon: Int = 0,
    val totalPoints: Int = 0
)

@Serializable
data class LeaderboardResponse(
    val entries: List<LeaderboardEntry> = emptyList()
)

@Serializable
data class CompleteMatchPlayer(
    val friendCode: String,
    val displayName: String,
    val total: Int
)

@Serializable
data class CompleteMatchRequest(
    val matchId: String,
    val targetCount: Int,
    val scoringType: String,
    val completedAt: Long,
    val players: List<CompleteMatchPlayer>,
    val winnerFriendCode: String
)

@Serializable
data class FriendsListResponse(
    val friends: List<RemoteFriend> = emptyList()
)

@Serializable
data class RemoteShoot(
    val id: String,
    val title: String,
    val description: String = "",
    val locationName: String,
    val region: String = "",
    val startsAt: Long,
    val endsAt: Long? = null,
    val hostFriendCode: String? = null,
    val hostDisplayName: String? = null,
    val isCurated: Boolean = false,
    val isPublic: Boolean = false,
    val sourceUrl: String = "",
    val registrationUrl: String = "",
    val eventType: String = "",
    val status: String = "scheduled",
    val friendsGoingCount: Int = 0,
    val viewerIsGoing: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val distanceKm: Double? = null
)

@Serializable
data class ShootsListResponse(
    val shoots: List<RemoteShoot> = emptyList()
)

@Serializable
data class ShootResponse(
    val shoot: RemoteShoot
)

@Serializable
data class CreateShootRequest(
    val title: String,
    val description: String = "",
    val locationName: String,
    val region: String = "",
    val startsAt: Long,
    val endsAt: Long? = null,
    val isPublic: Boolean = false,
    val sourceUrl: String = "",
    val registrationUrl: String = "",
    val eventType: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Serializable
data class ShootAttendee(
    val friendCode: String,
    val displayName: String,
    val isFriend: Boolean = false
)

@Serializable
data class ShootAttendeesResponse(
    val attendees: List<ShootAttendee> = emptyList()
)

@Serializable
data class FriendRequestAction(
    val fromFriendCode: String
)

@Serializable
data class RemoteFriendRequest(
    val fromCode: String,
    val fromDisplayName: String,
    val toCode: String,
    /** Only set on outgoing requests. */
    val toDisplayName: String = "",
    val status: String = "pending",
    /** Epoch seconds from the worker; older servers sent milliseconds. */
    val createdAt: Long = 0
) {
    val createdAtMs: Long
        get() = if (createdAt in 1 until 100_000_000_000L) createdAt * 1000 else createdAt
}

@Serializable
data class FriendRequestsResponse(
    val requests: List<RemoteFriendRequest> = emptyList()
)

@Serializable
data class PlayerSearchResult(
    val friendCode: String,
    val displayName: String,
    /** One of friend, requested, incoming, none. */
    val relation: String = RELATION_NONE
) {
    companion object {
        const val RELATION_FRIEND = "friend"
        const val RELATION_REQUESTED = "requested"
        const val RELATION_INCOMING = "incoming"
        const val RELATION_NONE = "none"
    }
}

@Serializable
data class PlayerSearchResponse(
    val results: List<PlayerSearchResult> = emptyList()
)

@Serializable
data class MutualFriendSuggestion(
    val friendCode: String,
    val displayName: String,
    val mutualCount: Int = 0
)

@Serializable
data class MutualFriendSuggestionsResponse(
    val suggestions: List<MutualFriendSuggestion> = emptyList()
)

@Serializable
data class SyncEntitlementRequest(
    val sku: String,
    /** Raw Play Billing purchase token — required when server Play verification is enabled. */
    val purchaseToken: String? = null,
    /** SHA-256 hex of the token — testing-only unverified sync path. */
    val purchaseTokenHash: String? = null,
    val expiresAt: Long? = null
)

@Serializable
data class OkResponse(
    val ok: Boolean = true
)

@Serializable
data class GoogleAuthRequest(
    val idToken: String
)

@Serializable
data class GoogleSignInRequest(
    val idToken: String,
    val displayName: String,
    val inviteCode: String? = null
)

@Serializable
data class GoogleLinkResponse(
    val linked: Boolean = false,
    val email: String? = null
)

@Serializable
data class GoogleRestoreResponse(
    val friendCode: String,
    val deviceSecret: String,
    val displayName: String,
    val email: String? = null,
    val created: Boolean = false,
    val deviceId: String? = null
)

@Serializable
data class GoogleStatusResponse(
    val linked: Boolean = false,
    val email: String? = null
)

enum class FeedFilter(val apiValue: String) {
    ALL("all"),
    NEARBY("nearby"),
    FRIENDS("friends"),
    GLOBAL("global"),
    FRIEND_POSTS("posts_friends"),
    REGIONAL_POSTS("posts_regional");

    val isPostFilter: Boolean
        get() = this == FRIEND_POSTS || this == REGIONAL_POSTS

    companion object {
        fun fromApi(value: String?): FeedFilter =
            entries.firstOrNull { it.apiValue == value?.lowercase() } ?: ALL
    }
}

@Serializable
data class FeedAuthor(
    val friendCode: String,
    val displayName: String
)

@Serializable
data class FeedItem(
    val type: String,
    val id: String,
    val createdAt: Long,
    val category: String = "",
    val author: FeedAuthor? = null,
    val body: String = "",
    val imageKey: String = "",
    val commentCount: Int = 0,
    val shoot: RemoteShoot? = null
) {
    /** Feed ids are prefixed ("post-<id>"); comment endpoints take the bare post id. */
    val postId: String?
        get() = if (type == "post") id.removePrefix("post-") else null
}

@Serializable
data class FeedResponse(
    val items: List<FeedItem> = emptyList()
)

@Serializable
data class PlayerProfile(
    val friendCode: String,
    val displayName: String,
    val bio: String = "",
    val club: String = "",
    val bowType: String = "",
    val homeRange: String = "",
    val locationLabel: String = "",
    val updatedAt: Long = 0,
    val discoverable: Boolean = true
)

@Serializable
data class ProfileResponse(
    val profile: PlayerProfile
)

@Serializable
data class ProfileUpdateRequest(
    val bio: String = "",
    val club: String = "",
    val bowType: String = "",
    val homeRange: String = "",
    val locationLabel: String = ""
)

@Serializable
data class RemotePost(
    val id: String,
    val authorCode: String,
    val authorName: String,
    val body: String,
    val imageKey: String = "",
    val isPublic: Boolean = false,
    val createdAt: Long = 0
)

@Serializable
data class PostResponse(
    val post: RemotePost
)

@Serializable
data class RemoteComment(
    val id: String,
    val postId: String,
    val authorCode: String,
    val authorName: String,
    val body: String,
    val createdAt: Long = 0
)

@Serializable
data class CommentsResponse(
    val comments: List<RemoteComment> = emptyList()
)

@Serializable
data class CommentResponse(
    val comment: RemoteComment
)

@Serializable
data class CreateCommentRequest(
    val body: String
)
