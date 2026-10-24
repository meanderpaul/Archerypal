package com.archerypal.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.archerypal.app.MainActivity
import com.archerypal.app.data.RemoteInviteLink
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class InviteMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] == TYPE_FRIEND_REQUEST) {
            showFriendRequest(message)
            return
        }
        if (message.data["type"] == TYPE_POST_COMMENT) {
            showPostComment(message)
            return
        }
        if (message.data["type"] == TYPE_FEED_POST) {
            showFeedPost(message)
            return
        }
        val inviteUri = message.data["inviteUri"]?.trim().orEmpty()
        val isInvite = message.data["type"] == "remote_invite" || inviteUri.isNotEmpty()
        if (!isInvite || RemoteInviteLink.parse(inviteUri) == null) return

        createNotificationChannel(this)
        if (!canNotify()) return

        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(inviteUri)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_INVITE_URI, inviteUri)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            inviteUri.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(message.notification?.title ?: "Archerypal invite")
            .setContentText(message.notification?.body ?: "Tap to join the match")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(inviteUri.hashCode(), notification)
    }

    private fun showFriendRequest(message: RemoteMessage) {
        createNotificationChannel(this)
        if (!canNotify()) return
        val fromCode = message.data["fromFriendCode"].orEmpty()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_FRIENDS, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            FRIEND_REQUEST_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, FRIEND_REQUEST_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(message.notification?.title ?: "New friend request")
            .setContentText(message.notification?.body ?: "Tap to see your friend requests")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(("friend-$fromCode").hashCode(), notification)
    }

    private fun showPostComment(message: RemoteMessage) {
        createNotificationChannel(this)
        if (!canNotify()) return
        val postId = message.data["postId"].orEmpty()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_FEED, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            POST_COMMENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, POST_COMMENT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(message.notification?.title ?: "New comment")
            .setContentText(message.notification?.body ?: "Someone commented on your post")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(("comment-$postId").hashCode(), notification)
    }

    private fun showFeedPost(message: RemoteMessage) {
        createNotificationChannel(this)
        if (!canNotify()) return
        val postId = message.data["postId"].orEmpty()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_FEED, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            FEED_POST_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, FEED_POST_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(message.notification?.title ?: "New post")
            .setContentText(message.notification?.body ?: "A friend posted in the feed")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(("post-$postId").hashCode(), notification)
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    override fun onNewToken(token: String) {
        PushTokenStore.update(token)
    }

    companion object {
        const val CHANNEL_ID = "remote_invites"
        const val FRIEND_REQUEST_CHANNEL_ID = "friend_requests"
        const val EXTRA_INVITE_URI = "inviteUri"
        const val POST_COMMENT_CHANNEL_ID = "post_comments"
        const val EXTRA_OPEN_FRIENDS = "openFriends"
        const val EXTRA_OPEN_FEED = "openFeed"
        const val FEED_POST_CHANNEL_ID = "feed_posts"
        const val TYPE_FEED_POST = "feed_post"
        private const val TYPE_FRIEND_REQUEST = "friend_request"
        private const val TYPE_POST_COMMENT = "post_comment"
        private const val FRIEND_REQUEST_REQUEST_CODE = 4201
        private const val POST_COMMENT_REQUEST_CODE = 4202
        private const val FEED_POST_REQUEST_CODE = 4203

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Remote match invites",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Invitations to join remote Archerypal matches"
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    FRIEND_REQUEST_CHANNEL_ID,
                    "Friend requests",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "When another archer wants to add you as a friend"
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    POST_COMMENT_CHANNEL_ID,
                    "Post comments",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "When someone comments on your posts"
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    FEED_POST_CHANNEL_ID,
                    "New posts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "When a friend posts in the feed"
                }
            )
        }
    }
}
