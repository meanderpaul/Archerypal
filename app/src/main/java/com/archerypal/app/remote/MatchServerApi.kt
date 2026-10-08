package com.archerypal.app.remote

import com.archerypal.app.BuildConfig



import com.archerypal.app.data.AppJson

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer

import okhttp3.MediaType.Companion.toMediaType

import okhttp3.MultipartBody

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient

import okhttp3.Request

import okhttp3.RequestBody.Companion.asRequestBody

import okhttp3.RequestBody.Companion.toRequestBody

import java.io.File

import java.io.IOException

import java.net.URLEncoder

import java.util.concurrent.TimeUnit



class MatchServerApi(

    private val client: OkHttpClient = defaultClient()

) {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()



    suspend fun healthCheck(): Result<Unit> = apiResult {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${MatchServerConfig.baseUrl}/health")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            }
        }
    }



    suspend fun register(body: RegisterRequest): Result<RegisterResponse> = postJson(
        path = "/v1/players/register",
        body = body,
        authenticated = false,
        useAppKey = true
    )

    suspend fun updatePlayerMe(creds: DeviceCredentials, displayName: String?,

        fcmToken: String?

    ): Result<RegisterResponse> = patchJson(

        path = "/v1/players/me",

        body = PatchMeRequest(displayName = displayName, fcmToken = fcmToken),

        creds = creds

    )



    suspend fun listFriends(creds: DeviceCredentials): Result<FriendsListResponse> = getJson(

        path = "/v1/friends",

        creds = creds

    )



    // Instant /friends/add is gone (410). Prefer sendFriendRequest.
    @Deprecated("Use sendFriendRequest — /v1/friends/add returns 410")
    suspend fun addFriend(creds: DeviceCredentials, theirFriendCode: String
    ): Result<OkResponse> = sendFriendRequest(creds, theirFriendCode)



    suspend fun challengeFriend(creds: DeviceCredentials, targetFriendCode: String

    ): Result<ChallengeResponse> = postJson(

        path = "/v1/matches/challenge",

        body = ChallengeRequest(targetFriendCode),

        authenticated = true,
        creds = creds

    )



    suspend fun completeMatch(creds: DeviceCredentials, roomId: String,

        body: CompleteMatchRequest

    ): Result<OkResponse> = postJson(

        path = "/v1/matches/$roomId/complete",

        body = body,

        authenticated = true,
        creds = creds

    )



    suspend fun syncEntitlement(creds: DeviceCredentials, body: SyncEntitlementRequest

    ): Result<OkResponse> = postJson(

        path = "/v1/entitlements/sync",

        body = body,

        authenticated = true,
        creds = creds

    )



    suspend fun globalLeaderboard(creds: DeviceCredentials, ifNoneMatch: String? = null
    ): Result<LeaderboardResponse> = getJson(
        path = "/v1/leaderboards/global",
        creds = creds,
        ifNoneMatch = ifNoneMatch
    )



    suspend fun friendsLeaderboard(creds: DeviceCredentials, ifNoneMatch: String? = null

    ): Result<LeaderboardResponse> = getJson(

        path = "/v1/leaderboards/friends",

        creds = creds,

        ifNoneMatch = ifNoneMatch

    )



    suspend fun listShoots(creds: DeviceCredentials, fromMs: Long? = null,

        toMs: Long? = null

    ): Result<ShootsListResponse> {

        var path = "/v1/shoots"

        val params = buildList {

            fromMs?.let { add("from=$it") }

            toMs?.let { add("to=$it") }

        }

        if (params.isNotEmpty()) path += "?" + params.joinToString("&")

        return getJson(path, creds)

    }



    suspend fun createShoot(creds: DeviceCredentials, body: CreateShootRequest

    ): Result<ShootResponse> = postJson(

        path = "/v1/shoots",

        body = body,

        authenticated = true,
        creds = creds

    )



    suspend fun setShootRsvp(creds: DeviceCredentials, shootId: String

    ): Result<OkResponse> = postJson(

        path = "/v1/shoots/$shootId/rsvp",

        body = OkResponse(),

        authenticated = true,
        creds = creds

    )



    suspend fun clearShootRsvp(creds: DeviceCredentials, shootId: String

    ): Result<OkResponse> = deleteJson(

        path = "/v1/shoots/$shootId/rsvp",

        creds = creds

    )



    suspend fun listShootAttendees(creds: DeviceCredentials, shootId: String

    ): Result<ShootAttendeesResponse> = getJson(

        path = "/v1/shoots/$shootId/attendees",

        creds = creds

    )



    suspend fun sendFriendRequest(creds: DeviceCredentials, theirFriendCode: String

    ): Result<OkResponse> = postJson(

        path = "/v1/friends/request",

        body = AddFriendRequest(theirFriendCode),

        authenticated = true,
        creds = creds

    )



    suspend fun acceptFriendRequest(creds: DeviceCredentials, fromFriendCode: String

    ): Result<FriendResponse> = postJson(

        path = "/v1/friends/accept",

        body = FriendRequestAction(fromFriendCode),

        authenticated = true,
        creds = creds

    )



    suspend fun declineFriendRequest(creds: DeviceCredentials, fromFriendCode: String

    ): Result<OkResponse> = postJson(

        path = "/v1/friends/decline",

        body = FriendRequestAction(fromFriendCode),

        authenticated = true,
        creds = creds

    )



    suspend fun listFriendRequests(creds: DeviceCredentials): Result<FriendRequestsResponse> = getJson(

        path = "/v1/friends/requests",

        creds = creds

    )

    suspend fun listOutgoingFriendRequests(creds: DeviceCredentials): Result<FriendRequestsResponse> =
        getJson(path = "/v1/friends/requests/outgoing", creds = creds)

    suspend fun friendSuggestions(creds: DeviceCredentials): Result<MutualFriendSuggestionsResponse> =
        getJson(path = "/v1/friends/suggestions", creds = creds)

    suspend fun searchPlayers(creds: DeviceCredentials, query: String): Result<PlayerSearchResponse> {
        // The query is HMAC-signed as-is; '+' would be decoded differently server-side.
        val q = URLEncoder.encode(query.trim(), Charsets.UTF_8.name()).replace("+", "%20")
        return getJson(path = "/v1/players/search?q=$q", creds = creds)
    }

    suspend fun setDiscoverable(
        creds: DeviceCredentials,
        displayName: String,
        discoverable: Boolean
    ): Result<RegisterResponse> = patchJson(
        path = "/v1/players/me",
        body = PatchMeRequest(displayName = displayName, discoverable = discoverable),
        creds = creds
    )

    suspend fun linkGoogleAccount(creds: DeviceCredentials, idToken: String
    ): Result<GoogleLinkResponse> = postJson(
        path = "/v1/auth/google/link",
        body = GoogleAuthRequest(idToken),
        authenticated = true,
        creds = creds
    )

    suspend fun restoreGoogleAccount(idToken: String): Result<GoogleRestoreResponse> = postJson(
        path = "/v1/auth/google/restore",
        body = GoogleAuthRequest(idToken),
        authenticated = false,
        useAppKey = true
    )

    suspend fun googleSignIn(
        idToken: String,
        displayName: String,
        inviteCode: String? = null
    ): Result<GoogleRestoreResponse> = postJson(
        path = "/v1/auth/google/sign-in",
        body = GoogleSignInRequest(
            idToken = idToken,
            displayName = displayName,
            inviteCode = inviteCode?.trim()?.takeIf { it.isNotBlank() }
        ),
        authenticated = false,
        useAppKey = true
    )

    suspend fun googleAccountStatus(creds: DeviceCredentials): Result<GoogleStatusResponse> = getJson(
        path = "/v1/auth/google/status",
        creds = creds
    )



    suspend fun getFeed(creds: DeviceCredentials, filter: FeedFilter = FeedFilter.ALL,
        lat: Double? = null,
        lng: Double? = null,
        radiusKm: Double? = null,
        since: Long? = null
    ): Result<FeedResponse> {
        val params = buildList {
            add("filter=${filter.apiValue}")
            radiusKm?.let { add("radiusKm=$it") }
            since?.let { add("since=$it") }
        }
        val path = "/v1/feed?" + params.joinToString("&")
        val extraHeaders = buildMap {
            lat?.let { put("X-Client-Lat", it.toString()) }
            lng?.let { put("X-Client-Lng", it.toString()) }
        }
        return getJson(path, creds, extraHeaders = extraHeaders)
    }

    suspend fun getMyProfile(creds: DeviceCredentials): Result<ProfileResponse> = getJson(
        path = "/v1/profiles/me",
        creds = creds
    )

    suspend fun getFriendProfile(creds: DeviceCredentials, theirCode: String
    ): Result<ProfileResponse> = getJson(
        path = "/v1/profiles/$theirCode",
        creds = creds
    )

    suspend fun updateMyProfile(creds: DeviceCredentials, body: ProfileUpdateRequest
    ): Result<ProfileResponse> = putJson(
        path = "/v1/profiles/me",
        body = body,
        creds = creds
    )

    suspend fun clearMyProfile(creds: DeviceCredentials): Result<OkResponse> = deleteJson(
        path = "/v1/profiles/me",
        creds = creds
    )


    suspend fun createPost(
        creds: DeviceCredentials,
        body: String,
        imageFile: File? = null,
        isPublic: Boolean = false,
        latitude: Double? = null,
        longitude: Double? = null
    ): Result<PostResponse> {
        if (imageFile == null) {
            return postJson(
                path = "/v1/posts",
                body = CreatePostJsonRequest(
                    body = body,
                    isPublic = isPublic,
                    latitude = latitude,
                    longitude = longitude
                ),
                authenticated = true,
                creds = creds
            )
        }
        return apiResult {
            withContext(Dispatchers.IO) {
                val path = "/v1/posts"
                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("body", body)
                    .addFormDataPart("isPublic", if (isPublic) "true" else "false")
                if (latitude != null && longitude != null) {
                    multipart.addFormDataPart("latitude", latitude.toString())
                    multipart.addFormDataPart("longitude", longitude.toString())
                }
                val mediaType = when {
                    imageFile.name.endsWith(".png", ignoreCase = true) -> "image/png"
                    else -> "image/jpeg"
                }.toMediaType()
                multipart.addFormDataPart(
                    "image",
                    imageFile.name,
                    imageFile.asRequestBody(mediaType)
                )
                val requestBody = multipart.build()
                val buffer = okio.Buffer()
                requestBody.writeTo(buffer)
                val bytes = buffer.readByteArray()
                val ts = System.currentTimeMillis() / 1000
                val builder = Request.Builder()
                    .url("${MatchServerConfig.baseUrl}$path")
                    .post(bytes.toRequestBody(requestBody.contentType()))
                applyAuth(builder, creds, "POST", path, bytes, ts)
                executeJson<PostResponse>(builder.build(), creds, ts)
            }
        }
    }

    suspend fun bindDeviceId(friendCode: String, deviceSecret: String): Result<DeviceBindResponse> = apiResult {
        withContext(Dispatchers.IO) {
            val path = "/v1/auth/device-bind"
            val bytes = ByteArray(0)
            val ts = System.currentTimeMillis() / 1000
            val nonce = DeviceAuth.nonceForMethod("POST")
            val signature = DeviceAuth.sign(deviceSecret, ts, "POST", path, bytes, nonce)
            val builder = Request.Builder()
                .url("${MatchServerConfig.baseUrl}$path")
                .post(bytes.toRequestBody(jsonMedia))
                .header("X-Friend-Code", friendCode)
                .header("X-Timestamp", ts.toString())
                .header("X-Device-Signature", signature)
                .header("X-Nonce", nonce)
            executeJson<DeviceBindResponse>(builder.build(), null, null)
        }
    }

    fun mediaUrl(imageKey: String): String =
        "${MatchServerConfig.baseUrl}/v1/media/$imageKey"


    private val sealedMedia = SealedPayload.CONTENT_TYPE.toMediaType()

    private suspend inline fun <reified Req, reified Res> postJson(
        path: String,
        body: Req,
        authenticated: Boolean,
        creds: DeviceCredentials? = null,
        useAppKey: Boolean = false
    ): Result<Res> = apiResult {
        val json = AppJson.encodeToString(serializer<Req>(), body)
        val plain = json.toByteArray(Charsets.UTF_8)
        val builder = Request.Builder().url("${MatchServerConfig.baseUrl}$path")
        if (useAppKey) {
            builder.header("X-App-Key", BuildConfig.APP_CLIENT_KEY)
        }
        if (authenticated) {
            requireNotNull(creds)
            val (wire, ts) = sealBody(creds, plain)
            builder.post(wire.toRequestBody(sealedMedia))
            applyAuth(builder, creds, "POST", path, wire, ts)
            executeJson<Res>(builder.build(), creds, ts)
        } else {
            builder.post(plain.toRequestBody(jsonMedia))
            executeJson<Res>(builder.build(), null, null)
        }
    }

    private suspend inline fun <reified T> getJson(
        path: String,
        creds: DeviceCredentials,
        ifNoneMatch: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): Result<T> = apiResult {
        val builder = Request.Builder()
            .url("${MatchServerConfig.baseUrl}$path")
            .get()
        if (ifNoneMatch != null) builder.header("If-None-Match", ifNoneMatch)
        extraHeaders.forEach { (name, value) -> builder.header(name, value) }
        val ts = System.currentTimeMillis() / 1000
        applyAuth(builder, creds, "GET", path, ByteArray(0), ts)
        executeJson<T>(builder.build(), creds, ts)
    }

    private suspend inline fun <reified Req, reified Res> putJson(
        path: String,
        body: Req,
        creds: DeviceCredentials
    ): Result<Res> = apiResult {
        val json = AppJson.encodeToString(serializer<Req>(), body)
        val plain = json.toByteArray(Charsets.UTF_8)
        val (wire, ts) = sealBody(creds, plain)
        val builder = Request.Builder()
            .url("${MatchServerConfig.baseUrl}$path")
            .put(wire.toRequestBody(sealedMedia))
        applyAuth(builder, creds, "PUT", path, wire, ts)
        executeJson<Res>(builder.build(), creds, ts)
    }

    private suspend inline fun <reified Req, reified Res> patchJson(
        path: String,
        body: Req,
        creds: DeviceCredentials
    ): Result<Res> = apiResult {
        val json = AppJson.encodeToString(serializer<Req>(), body)
        val plain = json.toByteArray(Charsets.UTF_8)
        val (wire, ts) = sealBody(creds, plain)
        val builder = Request.Builder()
            .url("${MatchServerConfig.baseUrl}$path")
            .patch(wire.toRequestBody(sealedMedia))
        applyAuth(builder, creds, "PATCH", path, wire, ts)
        executeJson<Res>(builder.build(), creds, ts)
    }

    private suspend inline fun <reified T> deleteJson(
        path: String,
        creds: DeviceCredentials
    ): Result<T> = apiResult {
        val builder = Request.Builder()
            .url("${MatchServerConfig.baseUrl}$path")
            .delete()
        val ts = System.currentTimeMillis() / 1000
        applyAuth(builder, creds, "DELETE", path, ByteArray(0), ts)
        executeJson<T>(builder.build(), creds, ts)
    }

    private fun sealBody(creds: DeviceCredentials, plain: ByteArray): Pair<ByteArray, Long> {
        val ts = System.currentTimeMillis() / 1000
        val wire = SealedPayload.seal(creds.deviceSecret, creds.friendCode, ts, plain)
        return wire to ts
    }

    private suspend fun <T> apiResult(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (t: kotlinx.coroutines.CancellationException) {
            throw t
        } catch (t: Throwable) {
            Result.failure(t)
        }

    private suspend inline fun <reified T> executeJson(
        request: Request,
        creds: DeviceCredentials?,
        timestamp: Long?
    ): T =
        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyText = response.body?.string()
                    throw IOException(bodyText?.takeIf { it.isNotBlank() } ?: "HTTP ${response.code}")
                }
                val raw = response.body?.bytes() ?: ByteArray(0)
                val contentType = response.header("Content-Type")
                val jsonBytes = if (
                    creds != null && timestamp != null && SealedPayload.isSealedContentType(contentType)
                ) {
                    SealedPayload.unseal(creds.deviceSecret, creds.friendCode, timestamp, raw)
                } else {
                    raw
                }
                AppJson.decodeFromString(serializer<T>(), jsonBytes.decodeToString())
            }
        }

    private fun applyAuth(
        builder: Request.Builder,
        creds: DeviceCredentials,
        method: String,
        path: String,
        body: ByteArray,
        timestamp: Long
    ) {
        val nonce = DeviceAuth.nonceForMethod(method)
        val signature = DeviceAuth.sign(creds.deviceSecret, timestamp, method, path, body, nonce)
        builder.header("X-Device-Id", creds.deviceId)
        builder.header("X-Timestamp", timestamp.toString())
        builder.header("X-Device-Signature", signature)
        if (nonce.isNotEmpty()) {
            builder.header("X-Nonce", nonce)
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
            if (!BuildConfig.DEBUG) {
                val host = MatchServerConfig.baseUrl
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .substringBefore('/')
                builder.certificatePinner(
                    CertificatePinner.Builder()
                        .add(host, "sha256/MLEr63X3eAXS1kBNVjI6f0vFrci1jLlaTBVtpyNxjYY=")
                        .add(host, "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=")
                        .add(host, "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=")
                        .build()
                )
            }
            return builder.build()
        }
    }
}

