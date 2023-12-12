@file:OptIn(ExperimentalMaterialApi::class)

package com.m3u.features.feed

import android.Manifest
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.content.res.Configuration.UI_MODE_TYPE_APPLIANCE
import android.content.res.Configuration.UI_MODE_TYPE_CAR
import android.content.res.Configuration.UI_MODE_TYPE_DESK
import android.content.res.Configuration.UI_MODE_TYPE_MASK
import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import android.content.res.Configuration.UI_MODE_TYPE_VR_HEADSET
import android.content.res.Configuration.UI_MODE_TYPE_WATCH
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.BackdropScaffold
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.BackdropValue
import androidx.compose.material.ExperimentalMaterialApi
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.Tab
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowCircleUp
import androidx.compose.material.icons.rounded.Refresh
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.rememberBackdropScaffoldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.core.util.compose.observableStateOf
import com.m3u.core.wrapper.Event
import com.m3u.data.database.entity.Live
import com.m3u.data.database.entity.LiveHolder
import com.m3u.data.database.entity.rememberLiveHolder
import com.m3u.features.feed.components.DialogStatus
import com.m3u.features.feed.components.FeedDialog
import com.m3u.features.feed.components.LiveGallery
import com.m3u.features.feed.components.TvFeedGallery
import com.m3u.i18n.R.string
import com.m3u.material.components.Background
import com.m3u.material.components.TextField
import com.m3u.material.ktx.animateColor
import com.m3u.material.ktx.interceptVolumeEvent
import com.m3u.material.ktx.isAtTop
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.Action
import com.m3u.ui.Destination
import com.m3u.ui.EventHandler
import com.m3u.ui.Fob
import com.m3u.ui.LocalHelper
import com.m3u.ui.MessageEventHandler
import com.m3u.ui.isAtTop
import com.m3u.ui.repeatOnLifecycle
import kotlinx.coroutines.launch

internal typealias NavigateToLive = () -> Unit

private typealias OnMenu = (Live) -> Unit
private typealias OnScrollUp = () -> Unit
private typealias OnRefresh = () -> Unit

@Composable
internal fun FeedRoute(
    contentPadding: PaddingValues,
    feedUrl: String,
    navigateToLive: NavigateToLive,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val helper = LocalHelper.current
    val pref = LocalPref.current

    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var dialogStatus: DialogStatus by remember { mutableStateOf(DialogStatus.Idle) }
    val writeExternalPermissionState = rememberPermissionState(
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    MessageEventHandler(message)

    LaunchedEffect(feedUrl) {
        viewModel.onEvent(FeedEvent.Observe(feedUrl))
    }

    helper.repeatOnLifecycle {
        actions = listOf(
            Action(
                icon = Icons.Rounded.Refresh,
                contentDescription = "refresh",
                onClick = {
                    viewModel.onEvent(FeedEvent.Refresh)
                }
            )
        )
    }

    LaunchedEffect(pref.autoRefresh, state.url) {
        if (state.url.isNotEmpty() && pref.autoRefresh) {
            viewModel.onEvent(FeedEvent.Refresh)
        }
    }

    BackHandler(state.query.isNotEmpty()) {
        viewModel.onEvent(FeedEvent.Query(""))
    }
    val interceptVolumeEventModifier = remember(pref.godMode) {
        if (pref.godMode) {
            Modifier.interceptVolumeEvent { event ->
                when (event) {
                    KeyEvent.KEYCODE_VOLUME_UP -> pref.rowCount =
                        (pref.rowCount - 1).coerceAtLeast(1)

                    KeyEvent.KEYCODE_VOLUME_DOWN -> pref.rowCount =
                        (pref.rowCount + 1).coerceAtMost(3)
                }
            }
        } else Modifier
    }

    FeedScreen(
        query = state.query,
        onQuery = { viewModel.onEvent(FeedEvent.Query(it)) },
        rowCount = pref.rowCount,
        channelHolder = rememberChannelHolder(state.channels),
        scrollUp = state.scrollUp,
        refreshing = state.fetching,
        onRefresh = { viewModel.onEvent(FeedEvent.Refresh) },
        navigateToLive = navigateToLive,
        onMenu = {
            dialogStatus = DialogStatus.Selections(it)
        },
        onScrollUp = { viewModel.onEvent(FeedEvent.ScrollUp) },
        contentPadding = contentPadding,
        modifier = modifier
            .fillMaxSize()
            .then(interceptVolumeEventModifier)
    )

    FeedDialog(
        status = dialogStatus,
        onUpdate = { dialogStatus = it },
        onFavorite = { id, target -> viewModel.onEvent(FeedEvent.Favourite(id, target)) },
        onBanned = { id, target -> viewModel.onEvent(FeedEvent.Mute(id, target)) },
        onSavePicture = { id ->
            if (writeExternalPermissionState.status is PermissionStatus.Denied) {
                writeExternalPermissionState.launchPermissionRequest()
                return@FeedDialog
            }
            viewModel.onEvent(FeedEvent.SavePicture(id))
        }
    )
}

@Composable
private fun FeedScreen(
    query: String,
    onQuery: (String) -> Unit,
    rowCount: Int,
    channelHolder: ChannelHolder,
    scrollUp: Event<Unit>,
    refreshing: Boolean,
    onRefresh: OnRefresh,
    navigateToLive: NavigateToLive,
    onMenu: OnMenu,
    onScrollUp: OnScrollUp,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val helper = LocalHelper.current
    val theme = MaterialTheme.colorScheme
    val pref = LocalPref.current
    val configuration = LocalConfiguration.current
    val spacing = LocalSpacing.current
    Box(modifier) {
        val isAtTopState = remember {
            observableStateOf(true) { newValue ->
                helper.fob = if (newValue) null
                else {
                    Fob(
                        icon = Icons.Rounded.ArrowCircleUp,
                        rootDestination = Destination.Root.Main,
                        onClick = onScrollUp
                    )
                }
            }
        }

        val scaffoldState = rememberBackdropScaffoldState(BackdropValue.Concealed)
        val connection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    return if (scaffoldState.isRevealed) available
                    else Offset.Zero
                }
            }
        }
        val currentColor by animateColor("color") { theme.background }
        val currentContentColor by animateColor("color") { theme.onBackground }
        val focusManager = LocalFocusManager.current

        BackdropScaffold(
            scaffoldState = scaffoldState,
            appBar = { /*TODO*/ },
            frontLayerShape = RectangleShape,
            peekHeight = 0.dp,
            backLayerContent = {
                LaunchedEffect(scaffoldState.currentValue) {
                    if (scaffoldState.isConcealed) {
                        focusManager.clearFocus()
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(spacing.medium)
                        .fillMaxWidth()
                ) {
                    TextField(
                        text = query,
                        onValueChange = onQuery,
                        fontWeight = FontWeight.Bold,
                        placeholder = stringResource(string.feat_feed_query_placeholder).capitalize(Locale.current)
                    )
                }
            },
            frontLayerContent = {
                Background(
                    modifier = Modifier.fillMaxSize()
                ) {
                    FeedPager(channelHolder) { liveHolder ->
                        val type = configuration.uiMode and UI_MODE_TYPE_MASK
                        when {
                            !pref.useCommonUIMode && type == UI_MODE_TYPE_TELEVISION -> {
                                val state = rememberTvLazyGridState()
                                LaunchedEffect(state.isAtTop) {
                                    isAtTopState.value = state.isAtTop
                                }
                                EventHandler(scrollUp) {
                                    state.animateScrollToItem(0)
                                }
                                TvFeedGallery(
                                    state = state,
                                    rowCount = 4,
                                    liveHolder = liveHolder,
                                    play = { url ->
                                        helper.play(url)
                                        navigateToLive()
                                    },
                                    onMenu = onMenu
                                )
                            }

                            else -> {
                                val state = rememberLazyGridState()
                                LaunchedEffect(state.isAtTop) {
                                    isAtTopState.value = state.isAtTop
                                }
                                EventHandler(scrollUp) {
                                    state.animateScrollToItem(0)
                                }
                                val actualRowCount = remember(configuration.orientation, rowCount) {
                                    when (configuration.orientation) {
                                        ORIENTATION_LANDSCAPE -> rowCount + 2
                                        ORIENTATION_PORTRAIT -> rowCount
                                        else -> rowCount
                                    }
                                }
                                LiveGallery(
                                    state = state,
                                    rowCount = actualRowCount,
                                    liveHolder = liveHolder,
                                    play = { url ->
                                        helper.play(url)
                                        navigateToLive()
                                    },
                                    onMenu = onMenu,
                                    modifier = modifier
                                )
                            }
                        }
                    }
                }
            },
            backLayerBackgroundColor = currentColor,
            backLayerContentColor = currentContentColor,
            frontLayerScrimColor = currentColor.copy(alpha = 0.45f),
            modifier = Modifier
                .padding(top = contentPadding.calculateTopPadding())
                .nestedScroll(
                    connection = connection,
                )
        )
    }
}

@Composable
private fun UnsupportedUIModeContent(
    type: Int,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val spacing = LocalSpacing.current

    val device = remember(type) {
        when (type) {
            UI_MODE_TYPE_NORMAL -> "Normal"
            UI_MODE_TYPE_DESK -> "Desk"
            UI_MODE_TYPE_CAR -> "Car"
            UI_MODE_TYPE_TELEVISION -> "Television"
            UI_MODE_TYPE_APPLIANCE -> "Appliance"
            UI_MODE_TYPE_WATCH -> "Watch"
            UI_MODE_TYPE_VR_HEADSET -> "VR-Headset"
            else -> "Device Type $type"
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            spacing.medium,
            Alignment.CenterVertically
        ),
        modifier = modifier.fillMaxSize()
    ) {
        Text("Unsupported UI Mode: $device")
        if (description != null) {
            Text(description)
        }
    }
}

@Composable
private fun FeedPager(
    channelHolder: ChannelHolder,
    modifier: Modifier = Modifier,
    content: @Composable (liveHolder: LiveHolder) -> Unit,
) {
    val theme = MaterialTheme.colorScheme
    Column(modifier) {
        val channels = channelHolder.channels
        val pagerState = rememberPagerState { channels.size }
        val coroutineScope = rememberCoroutineScope()
        if (channels.size > 1) {
            PrimaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                indicator = { tabPositions ->
                    val index = pagerState.currentPage
                    with(TabRowDefaults) {
                        Modifier.tabIndicatorOffset(
                            currentTabPosition = tabPositions[index]
                        )
                    }
                },
                tabs = {
                    val keys = remember(channels) { channels.map { it.title } }
                    keys.forEachIndexed { index, title ->
                        val selected = pagerState.currentPage == index
                        Tab(
                            selected = selected,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (selected) theme.onBackground else Color.Unspecified
                                )
                            },
                            icon = null
                        )
                    }
                },
                divider = {},
                modifier = Modifier.fillMaxWidth()
            )
        }
        val holders = List(channels.size) { rememberLiveHolder(channels[it].lives) }
        HorizontalPager(
            state = pagerState
        ) { pager ->
            content(holders[pager])
        }
    }
}