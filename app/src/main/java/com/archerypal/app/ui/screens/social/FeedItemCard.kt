package com.archerypal.app.ui.screens.social

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.archerypal.app.data.formatDistanceAway
import com.archerypal.app.remote.FeedItem
import com.archerypal.app.remote.RemoteComment
import com.archerypal.app.remote.RemoteFriendRequest
import com.archerypal.app.remote.RemoteShoot
import com.archerypal.app.ui.components.AutoSizeText
import com.archerypal.app.ui.components.OutdoorCard
import com.archerypal.app.ui.theme.CardSurface
import com.archerypal.app.ui.theme.FieldGreen
import com.archerypal.app.ui.theme.ForestGreen
import com.archerypal.app.ui.theme.SageMuted
import com.archerypal.app.ui.theme.TargetGold

/** Comment thread state for one post; [comments] is null until the thread has been fetched. */
data class PostCommentsState(
    val comments: List<RemoteComment>? = null,
    val loading: Boolean = false,
    val sending: Boolean = false,
    val canComment: Boolean = false
)

class PostCommentActions(
    val onLoad: (postId: String) -> Unit,
    val onAdd: (postId: String, body: String) -> Unit,
    val onDelete: (postId: String, commentId: String) -> Unit,
    val onSubscribePlus: () -> Unit
)

@Composable
fun FeedItemCard(
    item: FeedItem,
    deviceId: String?,
    deviceSecret: String?,
    onAuthorClick: (String) -> Unit,
    onToggleShootRsvp: (RemoteShoot) -> Unit,
    onOpenShootDetail: (RemoteShoot) -> Unit,
    modifier: Modifier = Modifier,
    myFriendCode: String? = null,
    commentsState: PostCommentsState = PostCommentsState(),
    commentActions: PostCommentActions? = null
) {
    when (item.type) {
        "post" -> PostFeedCard(
            item = item,
            deviceId = deviceId,
            deviceSecret = deviceSecret,
            onAuthorClick = onAuthorClick,
            myFriendCode = myFriendCode,
            commentsState = commentsState,
            commentActions = commentActions,
            modifier = modifier
        )
        "shoot" -> ShootFeedCard(
            shoot = item.shoot ?: return,
            category = item.category,
            onToggleRsvp = { onToggleShootRsvp(item.shoot) },
            onOpenDetail = { onOpenShootDetail(item.shoot) },
            modifier = modifier
        )
        else -> Unit
    }
}

@Composable
fun FriendRequestFeedCard(
    request: RemoteFriendRequest,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutdoorCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(FieldGreen.copy(alpha = 0.1f), CardSurface)
                    )
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarCircle(request.fromDisplayName)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "FRIEND REQUEST",
                        style = MaterialTheme.typography.labelSmall,
                        color = SageMuted
                    )
                    Text(request.fromDisplayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        formatRelativeTime(request.createdAtMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
}

@Composable
private fun PostFeedCard(
    item: FeedItem,
    deviceId: String?,
    deviceSecret: String?,
    onAuthorClick: (String) -> Unit,
    myFriendCode: String?,
    commentsState: PostCommentsState,
    commentActions: PostCommentActions?,
    modifier: Modifier = Modifier
) {
    val author = item.author ?: return
    val postId = item.postId
    var commentsExpanded by rememberSaveable(item.id) { mutableStateOf(false) }
    OutdoorCard(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAuthorClick(author.friendCode) }
        ) {
            AvatarCircle(author.displayName, size = 32.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    author.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    formatRelativeTime(item.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            item.body,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 10.dp)
        )
        if (item.imageKey.isNotBlank() && deviceId != null && deviceSecret != null) {
            AuthenticatedMediaImage(
                imageKey = item.imageKey,
                deviceId = deviceId,
                deviceSecret = deviceSecret,
                contentDescription = "Post image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        }
        if (postId != null && commentActions != null) {
            CommentToggleRow(
                count = commentsState.comments?.size ?: item.commentCount,
                expanded = commentsExpanded,
                onToggle = {
                    commentsExpanded = !commentsExpanded
                    if (commentsExpanded) commentActions.onLoad(postId)
                }
            )
            if (commentsExpanded) {
                CommentThread(
                    postAuthorCode = author.friendCode,
                    myFriendCode = myFriendCode,
                    state = commentsState,
                    onAuthorClick = onAuthorClick,
                    onSend = { body -> commentActions.onAdd(postId, body) },
                    onDelete = { commentId -> commentActions.onDelete(postId, commentId) },
                    onSubscribePlus = commentActions.onSubscribePlus
                )
            }
        }
    }
}

@Composable
private fun CommentToggleRow(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.ChatBubbleOutline,
            contentDescription = null,
            tint = SageMuted,
            modifier = Modifier.size(18.dp)
        )
        Text(
            when {
                count == 0 -> "Comment"
                count == 1 -> "1 comment"
                else -> "$count comments"
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (expanded) TargetGold else SageMuted
        )
    }
}

@Composable
private fun CommentThread(
    postAuthorCode: String,
    myFriendCode: String?,
    state: PostCommentsState,
    onAuthorClick: (String) -> Unit,
    onSend: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSubscribePlus: () -> Unit
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val comments = state.comments
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        when {
            comments == null && state.loading -> CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.CenterHorizontally)
            )
            comments.isNullOrEmpty() -> Text(
                "No comments yet. Start the conversation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> comments.orEmpty().forEach { comment ->
                val canDelete = myFriendCode != null &&
                    (comment.authorCode == myFriendCode || postAuthorCode == myFriendCode)
                CommentRow(
                    comment = comment,
                    canDelete = canDelete,
                    onAuthorClick = onAuthorClick,
                    onDelete = { onDelete(comment.id) }
                )
            }
        }
        if (!state.canComment) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSubscribePlus)
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Commenting is part of Plus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "See Plus",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = TargetGold
                )
            }
            return@Column
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= MAX_COMMENT_CHARS) draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Add a comment…") },
                textStyle = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                shape = RoundedCornerShape(20.dp)
            )
            IconButton(
                onClick = {
                    onSend(draft.trim())
                    draft = ""
                },
                enabled = draft.isNotBlank() && !state.sending
            ) {
                if (state.sending) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Icon(
                        Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Send comment",
                        tint = if (draft.isNotBlank()) FieldGreen else SageMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: RemoteComment,
    canDelete: Boolean,
    onAuthorClick: (String) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        AvatarCircle(
            comment.authorName,
            size = 26.dp,
            modifier = Modifier.clickable { onAuthorClick(comment.authorCode) }
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    comment.authorName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable { onAuthorClick(comment.authorCode) }
                )
                Text(
                    formatRelativeTime(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(comment.body, style = MaterialTheme.typography.bodyMedium)
        }
        if (canDelete) {
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete comment",
                    tint = SageMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private const val MAX_COMMENT_CHARS = 500

@Composable
fun ShootFeedCard(
    shoot: RemoteShoot,
    category: String,
    onToggleRsvp: () -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier,
    compactPast: Boolean = shoot.isPast()
) {
    if (compactPast) {
        PastShootFeedCard(
            shoot = shoot,
            category = category,
            onOpenDetail = onOpenDetail,
            modifier = modifier
        )
    } else {
        UpcomingShootFeedCard(
            shoot = shoot,
            category = category,
            onToggleRsvp = onToggleRsvp,
            onOpenDetail = onOpenDetail,
            modifier = modifier
        )
    }
}

@Composable
private fun UpcomingShootFeedCard(
    shoot: RemoteShoot,
    category: String,
    onToggleRsvp: () -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val badge = shootCategoryBadge(shoot, category)
    val eventLabel = shoot.eventType.ifBlank { "Shoot" }.uppercase()

    OutdoorCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            ForestGreen.copy(alpha = 0.55f),
                            TargetGold.copy(alpha = 0.12f)
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$eventLabel · $badge".uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                formatShootWhenRange(shoot.startsAt, shoot.endsAt),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = TargetGold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Column(modifier = Modifier.padding(top = 10.dp)) {
            Text(shoot.title, style = MaterialTheme.typography.titleMedium)
            Text(
                shoot.locationName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            shoot.distanceKm?.let { dist ->
                Text(
                    formatDistanceAway(dist),
                    style = MaterialTheme.typography.labelMedium,
                    color = TargetGold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    shoot.hostDisplayName?.takeIf { it.isNotBlank() }?.let { host ->
                        Text(
                            "Hosted by $host",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (shoot.friendsGoingCount > 0) {
                        Text(
                            "${shoot.friendsGoingCount} friend${if (shoot.friendsGoingCount == 1) "" else "s"} going",
                            style = MaterialTheme.typography.labelMedium,
                            color = TargetGold
                        )
                    }
                }
                Button(
                    onClick = onToggleRsvp,
                    shape = RoundedCornerShape(999.dp),
                    colors = if (shoot.viewerIsGoing) {
                        ButtonDefaults.buttonColors(
                            containerColor = FieldGreen.copy(alpha = 0.22f),
                            contentColor = FieldGreen
                        )
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = FieldGreen,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    },
                    border = if (shoot.viewerIsGoing) BorderStroke(1.dp, FieldGreen) else null
                ) {
                    Text(
                        if (shoot.viewerIsGoing) "Going ✓" else "Join",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun PastShootFeedCard(
    shoot: RemoteShoot,
    category: String,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val badge = shootCategoryBadge(shoot, category)
    OutdoorCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "ENDED · ${formatShootDay(shoot.startsAt)} · $badge".uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SageMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    shoot.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    shoot.locationName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (shoot.viewerIsGoing) {
                Text(
                    "Went",
                    maxLines = 1,
                    softWrap = false,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SageMuted
                )
            }
        }
    }
}

private fun shootCategoryBadge(shoot: RemoteShoot, category: String): String =
    when (category) {
        "global" -> if (shoot.isCurated) "Regional" else "Public"
        "nearby" -> "Nearby"
        else -> shoot.hostDisplayName ?: "Community"
    }
