package com.archerypal.app.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.archerypal.app.remote.DeviceAuth
import com.archerypal.app.remote.MatchServerConfig
import com.archerypal.app.ui.theme.CardSurface
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private val sharedAuthenticatedClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(Interceptor { chain ->
            val original = chain.request()
            val deviceId = original.header("X-Device-Id")
            val deviceSecret = original.header("X-Device-Secret")
            if (deviceId.isNullOrBlank() || deviceSecret.isNullOrBlank()) {
                return@Interceptor chain.proceed(original)
            }
            val path = original.url.encodedPath
            val timestamp = System.currentTimeMillis() / 1000
            val signature = DeviceAuth.sign(deviceSecret, timestamp, "GET", path, ByteArray(0))
            val signed = original.newBuilder()
                .removeHeader("X-Device-Secret")
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp.toString())
                .header("X-Device-Signature", signature)
                .build()
            chain.proceed(signed)
        })
        .build()
}

@Composable
fun AuthenticatedMediaImage(
    imageKey: String,
    deviceId: String,
    deviceSecret: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current
    val loader = remember {
        ImageLoader.Builder(context)
            .okHttpClient(sharedAuthenticatedClient)
            .crossfade(true)
            .build()
    }
    val url = "${MatchServerConfig.baseUrl}/v1/media/$imageKey"
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .addHeader("X-Device-Id", deviceId)
            .addHeader("X-Device-Secret", deviceSecret)
            .memoryCacheKey(imageKey)
            .diskCacheKey(imageKey)
            .build(),
        contentDescription = contentDescription,
        imageLoader = loader,
        modifier = modifier,
        contentScale = contentScale
    ) {
        when (painter.state) {
            is AsyncImagePainter.State.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CardSurface),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            }
            is AsyncImagePainter.State.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CardSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Image unavailable",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> SubcomposeAsyncImageContent()
        }
    }
}
