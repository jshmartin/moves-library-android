package com.example.myapplication.ui.videos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

object VideoRoutes {
    const val NO_ID = -1L
    const val ARG_VIDEO_ID = "videoId"
    const val ARG_SET_ID = "setId"
    const val PLAYER = "player?$ARG_VIDEO_ID={$ARG_VIDEO_ID}&$ARG_SET_ID={$ARG_SET_ID}"

    fun video(id: Long) = "player?$ARG_VIDEO_ID=$id&$ARG_SET_ID=$NO_ID"
    fun combo(setId: Long) = "player?$ARG_VIDEO_ID=$NO_ID&$ARG_SET_ID=$setId"
}

fun NavGraphBuilder.videoPlayer(onBack: () -> Unit) {
    composable(
        VideoRoutes.PLAYER,
        arguments = listOf(VideoRoutes.ARG_VIDEO_ID, VideoRoutes.ARG_SET_ID).map { name ->
            navArgument(name) {
                type = NavType.LongType
                defaultValue = VideoRoutes.NO_ID
            }
        },
    ) {
        VideoPlayerScreen(onBack = onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    onBack: () -> Unit,
    viewModel: VideoPlayerViewModel = viewModel(factory = VideoPlayerViewModel.Factory),
) {
    val context = LocalContext.current
    val clips = viewModel.clips
    val player = remember { ExoPlayer.Builder(context).build() }
    var currentIndex by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(emptyList<String>()) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = player.currentMediaItemIndex
            }

            override fun onPlayerError(playbackError: PlaybackException) {
                viewModel.clips.getOrNull(player.currentMediaItemIndex)?.let { failed = (failed + it.title).distinct() }
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                }
            }
        }
        player.addListener(listener)
        onDispose { player.release() }
    }

    LaunchedEffect(clips) {
        if (clips.isEmpty()) return@LaunchedEffect
        player.setMediaItems(clips.map { MediaItem.fromUri(it.uri) })
        player.prepare()
        player.playWhenReady = true
    }

    LifecycleStartEffect(player) {
        onStopOrDispose { player.pause() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    val clip = clips.getOrNull(currentIndex)
                    if (clip != null) {
                        Text(if (clips.size > 1) "${clip.title} · ${currentIndex + 1} of ${clips.size}" else clip.title, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            if (viewModel.loaded && clips.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("There's nothing to play here right now.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                AndroidView(
                    factory = { PlayerView(it).apply { this.player = player } },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
            val playbackNotice = failed.takeIf { it.isNotEmpty() }?.let { "Couldn't play: ${it.joinToString(", ")}" }
            (viewModel.notices + listOfNotNull(playbackNotice)).forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}
