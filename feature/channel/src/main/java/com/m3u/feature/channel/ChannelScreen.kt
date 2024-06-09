package com.m3u.feature.channel

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.core.util.basic.isNotEmpty
import com.m3u.core.util.basic.title
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Channel
import com.m3u.feature.channel.components.CoverPlaceholder
import com.m3u.feature.channel.components.DlnaDevicesBottomSheet
import com.m3u.feature.channel.components.FormatsBottomSheet
import com.m3u.feature.channel.components.PlayerPanel
import com.m3u.feature.channel.components.VerticalGestureArea
import com.m3u.i18n.R.string
import com.m3u.material.components.Background
import com.m3u.material.components.PullPanelLayout
import com.m3u.material.components.PullPanelLayoutValue
import com.m3u.material.components.mask.MaskInterceptor
import com.m3u.material.components.mask.MaskState
import com.m3u.material.components.mask.rememberMaskState
import com.m3u.material.components.rememberPullPanelLayoutState
import com.m3u.material.ktx.plus
import com.m3u.ui.Player
import com.m3u.ui.helper.LocalHelper
import com.m3u.ui.helper.OnPipModeChanged
import com.m3u.ui.rememberPlayerState
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ChannelRoute(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit,
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    val openInExternalPlayerString = stringResource(string.feat_channel_open_in_external_app)

    val helper = LocalHelper.current
    val preferences = hiltPreferences()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val playerState: PlayerState by viewModel.playerState.collectAsStateWithLifecycle()
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val devices = viewModel.devices
    val isDevicesVisible by viewModel.isDevicesVisible.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()

    val tracks by viewModel.tracks.collectAsStateWithLifecycle(emptyMap())
    val selectedFormats by viewModel.currentTracks.collectAsStateWithLifecycle(emptyMap())

    val volume by viewModel.volume.collectAsStateWithLifecycle()
    val isSeriesPlaylist by viewModel.isSeriesPlaylist.collectAsStateWithLifecycle(false)
    val isProgrammeSupported by viewModel.isProgrammeSupported.collectAsStateWithLifecycle(
        initialValue = false
    )

    val channels = viewModel.channels.collectAsLazyPagingItems()
    val programmes = viewModel.programmes.collectAsLazyPagingItems()
    val programmeRange by viewModel.programmeRange.collectAsStateWithLifecycle()

    var brightness by remember { mutableFloatStateOf(helper.brightness) }
    var isPipMode by remember { mutableStateOf(false) }
    var isAutoZappingMode by remember { mutableStateOf(true) }
    var choosing by remember { mutableStateOf(false) }

    val isPanelGestureSupported = configuration.screenWidthDp < configuration.screenHeightDp
    val isPanelEnabled = preferences.panel

    val maskState = rememberMaskState()
    val pullPanelLayoutState = rememberPullPanelLayoutState()

    val isPanelExpanded = pullPanelLayoutState.value == PullPanelLayoutValue.EXPANDED

    LifecycleResumeEffect(Unit) {
        with(helper) {
            isSystemBarUseDarkMode = true
            statusBarVisibility = false
            navigationBarVisibility = false
            onPipModeChanged = OnPipModeChanged { info ->
                isPipMode = info.isInPictureInPictureMode
                if (!isPipMode) {
                    maskState.wake()
                    isAutoZappingMode = false
                }
            }
        }
        onPauseOrDispose {
            viewModel.closeDlnaDevices()
        }
    }

    LaunchedEffect(preferences.zappingMode, playerState.videoSize) {
        val videoSize = playerState.videoSize
        if (isAutoZappingMode && preferences.zappingMode && !isPipMode) {
            maskState.sleep()
            val rect = if (videoSize.isNotEmpty) videoSize
            else Rect(0, 0, 1920, 1080)
            helper.enterPipMode(rect)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { brightness }
            .drop(1)
            .onEach { helper.brightness = it }
            .launchIn(this)

        snapshotFlow { maskState.visible }
            .onEach { visible ->
                helper.statusBarVisibility = visible
                helper.navigationBarVisibility = false
            }
            .launchIn(this)
    }

    DisposableEffect(Unit) {
        val prev = helper.brightness
        onDispose {
            helper.brightness = prev
        }
    }

    LaunchedEffect(isPipMode) {
        val interceptor: MaskInterceptor? = if (isPipMode) { _ -> false } else null
        maskState.intercept(interceptor)
    }

    Background(
        color = Color.Black,
        contentColor = Color.White
    ) {
        PullPanelLayout(
            state = pullPanelLayoutState,
            enabled = isPanelGestureSupported && isPanelEnabled,
            onValueChanged = { state ->
                when (state) {
                    PullPanelLayoutValue.EXPANDED -> {
                        maskState.lock(PullPanelLayoutValue.EXPANDED)
                    }

                    PullPanelLayoutValue.COLLAPSED -> {
                        maskState.unlock(PullPanelLayoutValue.EXPANDED, 1800.milliseconds)
                    }
                }
            },
            panel = {
                PlayerPanel(
                    title = channel?.title.orEmpty(),
                    playlistTitle = playlist?.title.orEmpty(),
                    channelId = channel?.id ?: -1,
                    isPanelExpanded = isPanelExpanded,
                    isChannelsSupported = !isSeriesPlaylist,
                    isProgrammeSupported = isProgrammeSupported,
                    channels = channels,
                    programmes = programmes,
                    programmeRange = programmeRange
                )
            },
            content = {
                ChannelPlayer(
                    isSeriesPlaylist = isSeriesPlaylist,
                    openDlnaDevices = {
                        viewModel.openDlnaDevices()
                        pullPanelLayoutState.collapse()
                    },
                    openChooseFormat = {
                        choosing = true
                        pullPanelLayoutState.collapse()
                    },
                    openOrClosePanel = {
                        if (isPanelExpanded) {
                            pullPanelLayoutState.collapse()
                        } else {
                            pullPanelLayoutState.expand()
                        }
                    },
                    onFavourite = viewModel::onFavourite,
                    onBackPressed = onBackPressed,
                    maskState = maskState,
                    playerState = playerState,
                    playlist = playlist,
                    channel = channel,
                    hasTrack = tracks.isNotEmpty(),
                    isPanelExpanded = isPanelExpanded,
                    volume = volume,
                    onVolume = viewModel::onVolume,
                    brightness = brightness,
                    onBrightness = { brightness = it },
                    onEnterPipMode = {
                        helper.enterPipMode(playerState.videoSize)
                        maskState.unlockAll()
                        pullPanelLayoutState.collapse()
                    },
                    modifier = modifier
                )
            }
        )
    }

    DlnaDevicesBottomSheet(
        maskState = maskState,
        searching = searching,
        isDevicesVisible = isDevicesVisible,
        devices = devices,
        connectDlnaDevice = { viewModel.connectDlnaDevice(it) },
        openInExternalPlayer = {
            val channelUrl = channel?.url ?: return@DlnaDevicesBottomSheet
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(channelUrl), "video/*")
                }.let { Intent.createChooser(it, openInExternalPlayerString.title()) }
            )
        },
        onDismiss = { viewModel.closeDlnaDevices() }
    )

    FormatsBottomSheet(
        visible = choosing,
        formats = tracks,
        selectedFormats = selectedFormats,
        maskState = maskState,
        onDismiss = { choosing = false },
        onChooseTrack = { type, format ->
            viewModel.chooseTrack(type, format)
        },
        onClearTrack = { type ->
            viewModel.clearTrack(type)
        }
    )
}

@Composable
private fun ChannelPlayer(
    maskState: MaskState,
    playerState: PlayerState,
    playlist: Playlist?,
    channel: Channel?,
    isSeriesPlaylist: Boolean,
    hasTrack: Boolean,
    isPanelExpanded: Boolean,
    volume: Float,
    brightness: Float,
    onFavourite: () -> Unit,
    onBackPressed: () -> Unit,
    openDlnaDevices: () -> Unit,
    openChooseFormat: () -> Unit,
    openOrClosePanel: () -> Unit,
    onVolume: (Float) -> Unit,
    onBrightness: (Float) -> Unit,
    onEnterPipMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = channel?.title ?: "--"
    val cover = channel?.cover.orEmpty()
    val playlistTitle = playlist?.title ?: "--"
    val favourite = channel?.favourite ?: false

    val preferences = hiltPreferences()
    var gesture: MaskGesture? by remember { mutableStateOf(null) }

    // because they will be updated frequently,
    // they must be wrapped with rememberUpdatedState when using them.
    val currentVolume by rememberUpdatedState(volume)
    val currentBrightness by rememberUpdatedState(brightness)

    Background(
        color = Color.Black,
        contentColor = Color.White,
    ) {
        Box(modifier) {
            val state = rememberPlayerState(
                player = playerState.player,
                clipMode = preferences.clipMode
            )

            Player(
                state = state,
                modifier = Modifier.fillMaxSize()
            )

            val shouldShowPlaceholder =
                !preferences.noPictureMode && cover.isNotEmpty() && playerState.videoSize.isEmpty

            CoverPlaceholder(
                visible = shouldShowPlaceholder,
                cover = cover,
                modifier = Modifier.align(Alignment.Center)
            )

            ChannelMask(
                cover = cover,
                title = title,
                playlistTitle = playlistTitle,
                playerState = playerState,
                volume = volume,
                brightness = brightness,
                gesture = gesture,
                maskState = maskState,
                favourite = favourite,
                isSeriesPlaylist = isSeriesPlaylist,
                hasTrack = hasTrack,
                isPanelExpanded = isPanelExpanded,
                onFavourite = onFavourite,
                onBackPressed = onBackPressed,
                openDlnaDevices = openDlnaDevices,
                openChooseFormat = openChooseFormat,
                openOrClosePanel = openOrClosePanel,
                onVolume = onVolume,
                onEnterPipMode = onEnterPipMode,
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        WindowInsets.systemBarsIgnoringVisibility.asPaddingValues()
                                + PaddingValues(vertical = 56.dp)
                    )
            ) {
                VerticalGestureArea(
                    percent = currentBrightness,
                    onDragStart = {
                        maskState.lock(MaskGesture.BRIGHTNESS)
                        gesture = MaskGesture.BRIGHTNESS
                    },
                    onDragEnd = {
                        maskState.unlock(MaskGesture.BRIGHTNESS, 400.milliseconds)
                        gesture = null
                    },
                    onDrag = onBrightness,
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.18f)
                )
                VerticalGestureArea(
                    percent = currentVolume,
                    onDragStart = {
                        maskState.lock(MaskGesture.VOLUME)
                        gesture = MaskGesture.VOLUME
                    },
                    onDragEnd = {
                        maskState.unlock(MaskGesture.VOLUME, 400.milliseconds)
                        gesture = null
                    },
                    onDrag = onVolume,
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.18f)
                )
            }

            LaunchedEffect(playerState.playerError) {
                if (playerState.playerError != null) {
                    maskState.wake()
                }
            }
        }
    }
}
