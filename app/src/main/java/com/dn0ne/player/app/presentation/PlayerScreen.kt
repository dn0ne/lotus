package com.dn0ne.player.app.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.AddToQueue
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dn0ne.player.R
import com.dn0ne.player.app.domain.sort.PlaylistSort
import com.dn0ne.player.app.domain.sort.SortOrder
import com.dn0ne.player.app.domain.sort.TrackSort
import com.dn0ne.player.app.domain.track.Playlist
import com.dn0ne.player.app.domain.track.Track
import com.dn0ne.player.app.domain.track.filterPlaylists
import com.dn0ne.player.app.domain.track.filterTracks
import com.dn0ne.player.app.presentation.components.PlaylistSortButton
import com.dn0ne.player.app.presentation.components.TrackSortButton
import com.dn0ne.player.app.presentation.components.playback.PlayerSheet
import com.dn0ne.player.app.presentation.components.playlist.AddToOrCreatePlaylistBottomSheet
import com.dn0ne.player.app.presentation.components.playlist.DeletePlaylistDialog
import com.dn0ne.player.app.presentation.components.playlist.MutablePlaylist
import com.dn0ne.player.app.presentation.components.playlist.Playlist
import com.dn0ne.player.app.presentation.components.playlist.RenamePlaylistBottomSheet
import com.dn0ne.player.app.presentation.components.playlist.playlistCards
import com.dn0ne.player.app.presentation.components.playlist.playlistRows
import com.dn0ne.player.app.presentation.components.selection.selectionCards
import com.dn0ne.player.app.presentation.components.selection.selectionList
import com.dn0ne.player.app.presentation.components.selection.selectionRows
import com.dn0ne.player.app.presentation.components.settings.SettingsSheet
import com.dn0ne.player.app.presentation.components.settings.Theme
import com.dn0ne.player.app.presentation.components.topbar.LazyGridWithCollapsibleTabsTopBar
import com.dn0ne.player.app.presentation.components.topbar.Tab
import com.dn0ne.player.app.presentation.components.topbar.TopBarContent
import com.dn0ne.player.app.presentation.components.trackList
import com.dn0ne.player.app.presentation.components.trackinfo.SearchField
import com.dn0ne.player.app.presentation.components.trackinfo.TrackInfoSheet
import com.kmpalette.color
import com.kmpalette.rememberDominantColorState
import com.materialkolor.DynamicMaterialTheme
import com.materialkolor.PaletteStyle
import com.materialkolor.ktx.toHct
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onCoverArtPick: () -> Unit,
    onFolderPick: (scan: Boolean) -> Unit,
    onLyricsPick: () -> Unit,
    onPlaylistPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val useDynamicColor by viewModel.settings.useDynamicColor.collectAsState()
    val useAlbumArtColor by viewModel.settings.useAlbumArtColor.collectAsState()
    val dominantColorState = rememberDominantColorState()
    var coverArtBitmap by remember {
        mutableStateOf<ImageBitmap?>(null)
    }
    val colorToApply by remember(coverArtBitmap, useAlbumArtColor, useDynamicColor) {
        derivedStateOf {
            if (useAlbumArtColor && coverArtBitmap != null) {
                dominantColorState.result
                    ?.paletteOrNull
                    ?.swatches
                    ?.sortedByDescending { it.population }
                    ?.let { swatches ->
                        val firstSwatch = swatches.firstOrNull() ?: return@let null
                        val firstSwatchColorHct = firstSwatch.color.toHct()
                        val firstSwatchPopulation = firstSwatch.population
                        val moreChromatic = swatches.fastFirstOrNull {
                            it.color.toHct().chroma - firstSwatchColorHct.chroma >= 30 &&
                                    it.population.toFloat() / firstSwatchPopulation >= .1f
                        }
                        moreChromatic?.color ?: firstSwatch.color
                    } ?: dominantColorState.color
            } else dominantColorState.color
        }
    }

    LaunchedEffect(useAlbumArtColor, useDynamicColor) {
        if (useAlbumArtColor) {
            coverArtBitmap?.let {
                dominantColorState.updateFrom(it)
            }
        } else dominantColorState.reset()
    }

    val appearance by viewModel.settings.appearance.collectAsState()
    val amoledDarkTheme by viewModel.settings.amoledDarkTheme.collectAsState()
    val paletteStyle by viewModel.settings.paletteStyle.collectAsState()
    DynamicMaterialTheme(
        seedColor = colorToApply,
        primary = colorToApply.takeIf { it.toHct().chroma <= 20 },
        useDarkTheme = when (appearance) {
            Theme.Appearance.System -> isSystemInDarkTheme()
            Theme.Appearance.Light -> false
            Theme.Appearance.Dark -> true
        },
        withAmoled = amoledDarkTheme,
        style = when (paletteStyle) {
            Theme.PaletteStyle.TonalSpot -> PaletteStyle.TonalSpot
            Theme.PaletteStyle.Neutral -> PaletteStyle.Neutral
            Theme.PaletteStyle.Vibrant -> PaletteStyle.Vibrant
            Theme.PaletteStyle.Expressive -> PaletteStyle.Expressive
            Theme.PaletteStyle.Rainbow -> PaletteStyle.Rainbow
            Theme.PaletteStyle.FruitSalad -> PaletteStyle.FruitSalad
            Theme.PaletteStyle.Monochrome -> PaletteStyle.Monochrome
            Theme.PaletteStyle.Fidelity -> PaletteStyle.Fidelity
            Theme.PaletteStyle.Content -> PaletteStyle.Content
        },
        animationSpec = tween(300, 200),
        animate = true
    ) {
        val rippleColor = MaterialTheme.colorScheme.primaryContainer
        val ripple = remember(rippleColor) {
            ripple(color = rippleColor)
        }
        val rippleConfiguration = remember(rippleColor) {
            RippleConfiguration(color = rippleColor)
        }
        CompositionLocalProvider(
            LocalIndication provides ripple,
            LocalRippleConfiguration provides rippleConfiguration,
            LocalContentColor provides MaterialTheme.colorScheme.onSurface
        ) {

            Box(
                modifier = modifier
                    .background(color = MaterialTheme.colorScheme.background)
            ) {
                val context = LocalContext.current
                val playbackState by viewModel.playbackState.collectAsState()
                val currentTrack by remember {
                    derivedStateOf {
                        playbackState.currentTrack
                    }
                }

                LaunchedEffect(currentTrack) {
                    if (currentTrack == null) {
                        coverArtBitmap = null
                        dominantColorState.reset()
                    }
                }

                val trackSort by viewModel.trackSort.collectAsState()
                val trackSortOrder by viewModel.trackSortOrder.collectAsState()
                val playlistSort by viewModel.playlistSort.collectAsState()
                val playlistSortOrder by viewModel.playlistSortOrder.collectAsState()

                val replaceSearchWithFilter by viewModel.settings
                    .replaceSearchWithFilter.collectAsState()

                var showAddToOrCreatePlaylistSheet by rememberSaveable {
                    mutableStateOf(false)
                }
                var showCreatePlaylistOnly by rememberSaveable {
                    mutableStateOf(false)
                }
                var tracksToAddToPlaylist by remember {
                    mutableStateOf<List<Track>?>(null)
                }

                val navController = rememberNavController()

                var showScrollToTopButton by remember {
                    mutableStateOf(false)
                }
                var onScrollToTopClick by remember {
                    mutableStateOf(suspend {})
                }
                var showLocateButton by remember {
                    mutableStateOf(false)
                }
                var onLocateClick by remember {
                    mutableStateOf(suspend {})
                }

                NavHost(
                    navController = navController,
                    enterTransition = {
                        fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                                slideInVertically(initialOffsetY = { it / 10 })
                    },
                    exitTransition = {
                        fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                                slideOutVertically(targetOffsetY = { -it / 10 })
                    },
                    popEnterTransition = {
                        fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                                slideInVertically(initialOffsetY = { -it / 10 })
                    },
                    popExitTransition = {
                        fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                                slideOutVertically(targetOffsetY = { it / 10 })
                    },
                    startDestination = PlayerRoutes.Main
                ) {
                    composable<PlayerRoutes.Main> {
                        val trackList by viewModel.trackList.collectAsState()
                        val playlists by viewModel.playlists.collectAsState()
                        val albumPlaylists by viewModel.albumPlaylists.collectAsState()
                        val artistPlaylists by viewModel.artistPlaylists.collectAsState()
                        val genrePlaylists by viewModel.genrePlaylists.collectAsState()
                        val folderPlaylists by viewModel.folderPlaylists.collectAsState()

                        val gridState = rememberLazyGridState()
                        val gridPlaylists by viewModel.settings.gridPlaylists.collectAsState()

                        val tabs by viewModel.settings.tabs.collectAsState()
                        var selectedTab by rememberSaveable {
                            mutableStateOf(viewModel.settings.defaultTab)
                        }

                        val shouldShowLocateButton by remember(currentTrack, trackList) {
                            derivedStateOf {
                                selectedTab == Tab.Tracks &&
                                        currentTrack != null &&
                                        gridState.layoutInfo.visibleItemsInfo.fastFirstOrNull {
                                            it.index == trackList.indexOf(currentTrack)
                                        } == null
                            }
                        }
                        onLocateClick = remember(currentTrack, trackList) {
                            {
                                val currentTrackIndex = trackList.indexOf(currentTrack)
                                val preAnimateItemIndex = if (
                                    gridState.firstVisibleItemIndex < currentTrackIndex
                                ) {
                                    (currentTrackIndex - 5).coerceAtLeast(0)
                                } else currentTrackIndex + 5
                                gridState.scrollToItem(preAnimateItemIndex)
                                gridState.animateScrollToItem(currentTrackIndex)
                            }
                        }
                        LaunchedEffect(shouldShowLocateButton) {
                            showLocateButton = shouldShowLocateButton
                        }

                        val isScrolledEnough by remember {
                            derivedStateOf {
                                gridState.firstVisibleItemIndex >= 5
                            }
                        }
                        onScrollToTopClick = {
                            gridState.scrollToItem(5)
                            gridState.animateScrollToItem(0)
                        }

                        LaunchedEffect(isScrolledEnough) {
                            showScrollToTopButton = isScrolledEnough
                        }

                        MainPlayerScreen(
                            gridState = gridState,
                            topBarTabs = tabs,
                            defaultTab = viewModel.settings.defaultTab,
                            onTabChange = {
                                selectedTab = it
                            },
                            trackList = trackList,
                            currentTrack = currentTrack,
                            onTrackClick = { track, playlist ->
                                viewModel.onEvent(
                                    PlayerScreenEvent.OnTrackClick(
                                        track = track,
                                        playlist = playlist
                                    )
                                )
                            },
                            onPlayNextClick = {
                                viewModel.onEvent(PlayerScreenEvent.OnPlayNextClick(it))
                            },
                            onAddToQueueClick = {
                                viewModel.onEvent(PlayerScreenEvent.OnAddToQueueClick(it))
                            },
                            onAddToPlaylistClick = {
                                showAddToOrCreatePlaylistSheet = true
                                showCreatePlaylistOnly = false
                                tracksToAddToPlaylist = it
                            },
                            onViewTrackInfoClick = {
                                viewModel.onEvent(PlayerScreenEvent.OnViewTrackInfoClick(it))
                            },
                            onGoToAlbumClick = {
                                viewModel.onEvent(PlayerScreenEvent.OnGoToAlbumClick(it))
                                navController.popBackStack(PlayerRoutes.Main, false)
                                navController.navigate(PlayerRoutes.Playlist)
                            },
                            onGoToArtistClick = {
                                viewModel.onEvent(PlayerScreenEvent.OnGoToArtistClick(it))
                                navController.popBackStack(PlayerRoutes.Main, false)
                                navController.navigate(PlayerRoutes.Playlist)
                            },
                            playlists = playlists,
                            albumPlaylists = albumPlaylists,
                            artistPlaylists = artistPlaylists,
                            genrePlaylists = genrePlaylists,
                            folderPlaylists = folderPlaylists,
                            trackSort = trackSort,
                            trackSortOrder = trackSortOrder,
                            playlistSort = playlistSort,
                            playlistSortOrder = playlistSortOrder,
                            onTrackSortChange = { sort, order ->
                                viewModel.onEvent(PlayerScreenEvent.OnTrackSortChange(sort, order))
                            },
                            onPlaylistSortChange = { sort, order ->
                                viewModel.onEvent(
                                    PlayerScreenEvent.OnPlaylistSortChange(
                                        sort,
                                        order
                                    )
                                )
                            },
                            onPlaylistSelection = { playlist ->
                                viewModel.onEvent(
                                    PlayerScreenEvent.OnPlaylistSelection(
                                        playlist.copy(
                                            name = playlist.name
                                                ?: context.resources.getString(R.string.unknown)
                                        )
                                    )
                                )
                                navController.navigate(PlayerRoutes.MutablePlaylist)
                            },
                            onAlbumPlaylistSelection = { playlist ->
                                viewModel.onEvent(
                                    PlayerScreenEvent.OnPlaylistSelection(
                                        playlist.copy(
                                            name = playlist.name
                                                ?: context.resources.getString(R.string.unknown_album)
                                        )
                                    )
                                )
                                navController.navigate(PlayerRoutes.Playlist)
                            },
                            onArtistPlaylistSelection = { playlist ->
                                viewModel.onEvent(
                                    PlayerScreenEvent.OnPlaylistSelection(
                                        playlist.copy(
                                            name = playlist.name
                                                ?: context.resources.getString(R.string.unknown_artist)
                                        )
                                    )
                                )
                                navController.navigate(PlayerRoutes.Playlist)
                            },
                            onGenrePlaylistSelection = { playlist ->
                                viewModel.onEvent(
                                    PlayerScreenEvent.OnPlaylistSelection(
                                        playlist.copy(
                                            name = playlist.name
                                                ?: context.resources.getString(R.string.unknown_genre)
                                        )
                                    )
                                )
                                navController.navigate(PlayerRoutes.Playlist)
                            },
                            onFolderPlaylistSelection = { playlist ->
                                viewModel.onEvent(
                                    PlayerScreenEvent.OnPlaylistSelection(
                                        playlist
                                    )
                                )
                                navController.navigate(PlayerRoutes.Playlist)
                            },
                            onSettingsClick = {
                                viewModel.onEvent(PlayerScreenEvent.OnSettingsClick)
                            },
                            onRefreshMediaStoreClick = {
                                viewModel.onEvent(PlayerScreenEvent.OnRefreshMediaStoreClick)
                            },
                            replaceSearchWithFilter = replaceSearchWithFilter,
                            gridPlaylists = gridPlaylists,
                            onGridPlaylistsClick = {
                                viewModel.settings.updateGridPlaylists(
                                    !gridPlaylists
                                )
                            }
                        )
                    }

                    composable<PlayerRoutes.Playlist> {
                        val listState = rememberLazyListState()
                        val isScrolledEnough by remember {
                            derivedStateOf {
                                listState.firstVisibleItemIndex >= 5
                            }
                        }
                        onScrollToTopClick = {
                            listState.scrollToItem(5)
                            listState.animateScrollToItem(0)
                        }

                        LaunchedEffect(isScrolledEnough) {
                            showScrollToTopButton = isScrolledEnough
                        }

                        val playlist by viewModel.selectedPlaylist.collectAsState()
                        playlist?.let { playlist ->
                            val shouldShowLocateButton by remember(currentTrack, playlist) {
                                derivedStateOf {
                                    val index = playlist.trackList.indexOf(currentTrack)
                                    currentTrack != null &&
                                            index >= 0 &&
                                            listState.layoutInfo.visibleItemsInfo.fastFirstOrNull {
                                                it.index == index
                                            } == null
                                }
                            }
                            onLocateClick = remember(currentTrack, playlist) {
                                {
                                    val currentTrackIndex = playlist.trackList.indexOf(currentTrack)
                                    val preAnimateItemIndex = if (
                                        listState.firstVisibleItemIndex < currentTrackIndex
                                    ) {
                                        (currentTrackIndex - 5).coerceAtLeast(0)
                                    } else currentTrackIndex + 5
                                    listState.scrollToItem(preAnimateItemIndex)
                                    listState.animateScrollToItem(currentTrackIndex)
                                }
                            }
                            LaunchedEffect(shouldShowLocateButton) {
                                showLocateButton = shouldShowLocateButton
                            }

                            Playlist(
                                listState = listState,
                                playlist = playlist,
                                currentTrack = currentTrack,
                                onTrackClick = { track, playlist ->
                                    viewModel.onEvent(
                                        PlayerScreenEvent.OnTrackClick(
                                            track,
                                            playlist
                                        )
                                    )
                                },
                                onPlayNextClick = {
                                    viewModel.onEvent(PlayerScreenEvent.OnPlayNextClick(it))
                                },
                                onAddToQueueClick = {
                                    viewModel.onEvent(PlayerScreenEvent.OnAddToQueueClick(it))
                                },
                                onAddToPlaylistClick = {
                                    showAddToOrCreatePlaylistSheet = true
                                    showCreatePlaylistOnly = false
                                    tracksToAddToPlaylist = it
                                },
                                onViewTrackInfoClick = {
                                    viewModel.onEvent(PlayerScreenEvent.OnViewTrackInfoClick(it))
                                },
                                onGoToAlbumClick = {
                                    viewModel.onEvent(PlayerScreenEvent.OnGoToAlbumClick(it))
                                    navController.popBackStack(PlayerRoutes.Main, false)
                                    navController.navigate(PlayerRoutes.Playlist)
                                },
                                onGoToArtistClick = {
                                    viewModel.onEvent(PlayerScreenEvent.OnGoToArtistClick(it))
                                    navController.popBackStack(PlayerRoutes.Main, false)
                                    navController.navigate(PlayerRoutes.Playlist)
                                },
                                trackSort = trackSort,
                                trackSortOrder = trackSortOrder,
                                onTrackSortChange = { sort, order ->
                                    viewModel.onEvent(
                                        PlayerScreenEvent.OnTrackSortChange(
                                            sort,
                                            order
                                        )
                                    )
                                },
                                onBackClick = {
                                    navController.navigateUp()
                                },
                                replaceSearchWithFilter = replaceSearchWithFilter
                            )
                        }
                    }

                    composable<PlayerRoutes.MutablePlaylist> {
                        var showRenameSheet by remember {
                            mutableStateOf(false)
                        }
                        var showDeleteDialog by remember {
                            mutableStateOf(false)
                        }

                        val listState = rememberLazyListState()
                        val isScrolledEnough by remember {
                            derivedStateOf {
                                listState.firstVisibleItemIndex >= 5
                            }
                        }
                        onScrollToTopClick = {
                            listState.scrollToItem(5)
                            listState.animateScrollToItem(0)
                        }

                        LaunchedEffect(isScrolledEnough) {
                            showScrollToTopButton = isScrolledEnough
                        }

                        val playlists by viewModel.playlists.collectAsState()
                        val playlist by viewModel.selectedPlaylist.collectAsState()
                        playlist?.let { playlist ->
                            var changedTrackList by remember {
                                mutableStateOf(playlist.trackList)
                            }
                            val shouldShowLocateButton by remember(currentTrack, changedTrackList) {
                                derivedStateOf {
                                    val index = changedTrackList.indexOf(currentTrack)
                                    currentTrack != null &&
                                            index >= 0 &&
                                            listState.layoutInfo.visibleItemsInfo.fastFirstOrNull {
                                                it.index == index
                                            } == null
                                }
                            }
                            onLocateClick = remember(currentTrack, changedTrackList) {
                                {
                                    val currentTrackIndex = changedTrackList.indexOf(currentTrack)
                                    val preAnimateItemIndex = if (
                                        listState.firstVisibleItemIndex < currentTrackIndex
                                    ) {
                                        (currentTrackIndex - 5).coerceAtLeast(0)
                                    } else currentTrackIndex + 5
                                    listState.scrollToItem(preAnimateItemIndex)
                                    listState.animateScrollToItem(currentTrackIndex)
                                }
                            }
                            LaunchedEffect(shouldShowLocateButton) {
                                showLocateButton = shouldShowLocateButton
                            }

                            MutablePlaylist(
                                listState = listState,
                                playlist = playlist,
                                currentTrack = currentTrack,
                                onRenamePlaylistClick = {
                                    showRenameSheet = true
                                },
                                onDeletePlaylistClick = {
                                    showDeleteDialog = true
                                },
                                onTrackClick = { track, playlist ->
                                    viewModel.onEvent(
                                        PlayerScreenEvent.OnTrackClick(
                                            track,
                                            playlist
                                        )
                                    )
                                },
                                onPlayNextClick = {
                                    viewModel.onEvent(PlayerScreenEvent.OnPlayNextClick(it))
                                },
                                onAddToQueueClick = {
                                    viewModel.onEvent(PlayerScreenEvent.OnAddToQueueClick(it))
                                },
                                onAddToPlaylistClick = {
                                    showAddToOrCreatePlaylistSheet = true
                                    showCreatePlaylistOnly = false
                                    tracksToAddToPlaylist = it
                                },
                                onRemoveFromPlaylistClick = {
                                    viewModel.onEvent(
                                        PlayerScreenEvent.OnRemoveFromPlaylist(
                                            it,
                                            playlist
                                        )
                                    )
                                },
                                onViewTrackInfoClick = {
                                    viewModel.onEvent(PlayerScreenEvent.OnViewTrackInfoClick(it))
                                },
                                onGoToAlbumClick = {
                                    viewModel.onEvent(PlayerScreenEvent.OnGoToAlbumClick(it))
                                    navController.popBackStack(PlayerRoutes.Main, false)
                                    navController.navigate(PlayerRoutes.Playlist)
                                },
                                onGoToArtistClick = {
                                    viewModel.onEvent(PlayerScreenEvent.OnGoToArtistClick(it))
                                    navController.popBackStack(PlayerRoutes.Main, false)
                                    navController.navigate(PlayerRoutes.Playlist)
                                },
                                onTrackListReorder = {
                                    viewModel.onEvent(
                                        PlayerScreenEvent.OnPlaylistReorder(
                                            it,
                                            playlist
                                        )
                                    )
                                    changedTrackList = it
                                },
                                onBackClick = {
                                    navController.navigateUp()
                                },
                                replaceSearchWithFilter = replaceSearchWithFilter
                            )

                            if (showRenameSheet) {
                                RenamePlaylistBottomSheet(
                                    playlists = playlists,
                                    initialName = playlist.name ?: "",
                                    onRenameClick = {
                                        viewModel.onEvent(
                                            PlayerScreenEvent.OnRenamePlaylistClick(
                                                it,
                                                playlist
                                            )
                                        )
                                    },
                                    onDismissRequest = {
                                        showRenameSheet = false
                                    }
                                )
                            }

                            if (showDeleteDialog) {
                                DeletePlaylistDialog(
                                    onConfirm = {
                                        showDeleteDialog = false
                                        navController.navigateUp()
                                        viewModel.onEvent(
                                            PlayerScreenEvent.OnDeletePlaylistClick(
                                                playlist
                                            )
                                        )
                                    },
                                    onDismissRequest = {
                                        showDeleteDialog = false
                                    }
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                ) {
                    val isPlayerExpanded by remember {
                        derivedStateOf { playbackState.isPlayerExpanded }
                    }
                    if (!isPlayerExpanded) {
                        ScrollToTopAndLocateButtons(
                            showScrollToTopButton = showScrollToTopButton,
                            onScrollToTopClick = onScrollToTopClick,
                            showLocateButton = showLocateButton,
                            onLocateClick = onLocateClick,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }

                    AnimatedVisibility(
                        visible = currentTrack != null,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it }),
                        modifier = Modifier
                            .align(alignment = Alignment.CenterHorizontally)
                    ) {
                        currentTrack?.let {

                            if (useAlbumArtColor) {
                                LaunchedEffect(coverArtBitmap) {
                                    coverArtBitmap?.let {
                                        dominantColorState.updateFrom(it)
                                    }
                                }
                            }

                            PlayerSheet(
                                playbackStateFlow = viewModel.playbackState,
                                onPlayerExpandedChange = {
                                    viewModel.onEvent(PlayerScreenEvent.OnPlayerExpandedChange(it))
                                },
                                onPlayClick = {
                                    viewModel.onEvent(PlayerScreenEvent.OnPlayClick)
                                },
                                onPauseClick = {
                                    viewModel.onEvent(PlayerScreenEvent.OnPauseClick)
                                },
                                onSeekToNextClick = {
                                    viewModel.onEvent(PlayerScreenEvent.OnSeekToNextClick)
                                },
                                onSeekToPreviousClick = {
                                    viewModel.onEvent(PlayerScreenEvent.OnSeekToPreviousClick)
                                },
                                onSeekTo = {
                                    viewModel.onEvent(PlayerScreenEvent.OnSeekTo(it))
                                },
                                onReset = {
                                    viewModel.onEvent(PlayerScreenEvent.OnResetPlayback)
                                },
                                onPlaybackModeClick = {
                                    viewModel.onEvent(PlayerScreenEvent.OnPlaybackModeClick)
                                },
                                onCoverArtLoaded = {
                                    coverArtBitmap = it
                                },
                                onPlayNextClick = {
                                    viewModel.onEvent(PlayerScreenEvent.OnPlayNextClick(it))
                                },
                                onAddToQueueClick = {
                                    viewModel.onEvent(
                                        PlayerScreenEvent.OnAddToQueueClick(
                                            listOf(it)
                                        )
                                    )
                                },
                                onAddToPlaylistClick = {
                                    showAddToOrCreatePlaylistSheet = true
                                    showCreatePlaylistOnly = false
                                    tracksToAddToPlaylist = listOf(it)
                                },
                                onViewTrackInfoClick = {
                                    viewModel.onEvent(
                                        PlayerScreenEvent.OnViewTrackInfoClick(it)
                                    )
                                },
                                onGoToAlbumClick = {
                                    viewModel.onEvent(PlayerScreenEvent.OnGoToAlbumClick(it))
                                    viewModel.onEvent(PlayerScreenEvent.OnPlayerExpandedChange(false))
                                    navController.popBackStack(PlayerRoutes.Main, false)
                                    navController.navigate(PlayerRoutes.Playlist)
                                },
                                onGoToArtistClick = {
                                    viewModel.onEvent(PlayerScreenEvent.OnGoToArtistClick(it))
                                    viewModel.onEvent(PlayerScreenEvent.OnPlayerExpandedChange(false))
                                    navController.popBackStack(PlayerRoutes.Main, false)
                                    navController.navigate(PlayerRoutes.Playlist)
                                },
                                onLyricsSheetExpandedChange = {
                                    viewModel.onEvent(
                                        PlayerScreenEvent.OnLyricsSheetExpandedChange(it)
                                    )
                                },
                                onLyricsClick = {
                                    viewModel.onEvent(PlayerScreenEvent.OnLyricsClick)
                                },
                                settings = viewModel.settings,
                                onRemoveFromQueueClick = {
                                    viewModel.onEvent(PlayerScreenEvent.OnRemoveFromQueueClick(it))
                                },
                                onReorderingQueue = { from, to ->
                                    viewModel.onEvent(PlayerScreenEvent.OnReorderingQueue(from, to))
                                },
                                onShuffleQueueClick = {
                                    viewModel.onEvent(PlayerScreenEvent.OnShuffleQueueClick)
                                },
                                onTrackClick = { track, playlist ->
                                    viewModel.onEvent(
                                        PlayerScreenEvent.OnTrackClick(
                                            track = track,
                                            playlist = playlist
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .align(alignment = Alignment.CenterHorizontally)
                                    .fillMaxWidth()
                            )
                        }
                    }
                }

                val trackInfoSheetState by viewModel.trackInfoSheetState.collectAsState()
                TrackInfoSheet(
                    state = trackInfoSheetState,
                    onCloseClick = {
                        viewModel.onEvent(PlayerScreenEvent.OnCloseTrackInfoSheetClick)
                    },
                    onSearchInfo = { query ->
                        viewModel.onEvent(PlayerScreenEvent.OnSearchInfo(query))
                    },
                    onSearchResultClick = {
                        viewModel.onEvent(PlayerScreenEvent.OnMetadataSearchResultPick(it))
                    },
                    onOverwriteMetadataClick = {
                        viewModel.onEvent(PlayerScreenEvent.OnOverwriteMetadataClick(it))
                    },
                    onPickCoverArtClick = onCoverArtPick,
                    onRestoreCoverArtClick = {
                        viewModel.onEvent(PlayerScreenEvent.OnRestoreCoverArtClick)
                    },
                    onConfirmMetadataEditClick = {
                        viewModel.onEvent(PlayerScreenEvent.OnConfirmMetadataEditClick(it))
                    },
                    onRisksOfMetadataEditingAccept = {
                        viewModel.onEvent(PlayerScreenEvent.OnAcceptingRisksOfMetadataEditing)
                    },
                    onLyricsControlClick = {
                        viewModel.onEvent(PlayerScreenEvent.OnLyricsControlClick)
                    },
                    onPickLyricsClick = onLyricsPick,
                    onDeleteLyricsClick = {
                        viewModel.onEvent(PlayerScreenEvent.OnDeleteLyricsClick)
                    },
                    onFetchLyricsFromRemoteClick = {
                        viewModel.onEvent(PlayerScreenEvent.OnFetchLyricsFromRemoteClick)
                    },
                    onCopyLyricsFromTagClick = {
                        viewModel.onEvent(PlayerScreenEvent.OnCopyLyricsFromTagClick)
                    },
                    onWriteLyricsToTagClick = {
                        viewModel.onEvent(PlayerScreenEvent.OnWriteLyricsToTagClick)
                    },
                    onPublishLyricsOnRemoteClick = {
                        viewModel.onEvent(PlayerScreenEvent.OnPublishLyricsOnRemoteClick)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                )

                if (showAddToOrCreatePlaylistSheet) {
                    val playlists by viewModel.playlists.collectAsState()
                    AddToOrCreatePlaylistBottomSheet(
                        playlists = playlists,
                        createOnly = showCreatePlaylistOnly,
                        onDismissRequest = {
                            showAddToOrCreatePlaylistSheet = false
                        },
                        onCreateClick = {
                            viewModel.onEvent(PlayerScreenEvent.OnCreatePlaylistClick(it))
                        },
                        onPlaylistSelection = { playlist ->
                            tracksToAddToPlaylist?.let { tracks ->
                                viewModel.onEvent(
                                    PlayerScreenEvent.OnAddToPlaylist(
                                        tracks = tracks,
                                        playlist = playlist
                                    )
                                )
                            }
                        }
                    )
                }

                val settingsSheetState by viewModel.settingsSheetState.collectAsState()
                SettingsSheet(
                    state = settingsSheetState,
                    onFolderPick = onFolderPick,
                    onPlaylistPick = onPlaylistPick,
                    onScanFoldersClick = {
                        viewModel.onEvent(PlayerScreenEvent.OnScanFoldersClick)
                    },
                    onCloseClick = {
                        viewModel.onEvent(PlayerScreenEvent.OnCloseSettingsClick)
                    },
                    dominantColorState = dominantColorState,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun MainPlayerScreen(
    gridState: LazyGridState = rememberLazyGridState(),
    topBarTabs: List<Tab>,
    defaultTab: Tab,
    onTabChange: (Tab) -> Unit = {},
    trackList: List<Track>,
    currentTrack: Track?,
    onTrackClick: (Track, Playlist) -> Unit,
    onPlayNextClick: (Track) -> Unit,
    onAddToQueueClick: (List<Track>) -> Unit,
    onAddToPlaylistClick: (List<Track>) -> Unit,
    onViewTrackInfoClick: (Track) -> Unit,
    onGoToAlbumClick: (Track) -> Unit,
    onGoToArtistClick: (Track) -> Unit,
    playlists: List<Playlist>,
    albumPlaylists: List<Playlist>,
    artistPlaylists: List<Playlist>,
    genrePlaylists: List<Playlist>,
    folderPlaylists: List<Playlist>,
    trackSort: TrackSort,
    trackSortOrder: SortOrder,
    playlistSort: PlaylistSort,
    playlistSortOrder: SortOrder,
    onTrackSortChange: (TrackSort?, SortOrder?) -> Unit,
    onPlaylistSortChange: (PlaylistSort?, SortOrder?) -> Unit,
    onPlaylistSelection: (Playlist) -> Unit,
    onAlbumPlaylistSelection: (Playlist) -> Unit,
    onArtistPlaylistSelection: (Playlist) -> Unit,
    onGenrePlaylistSelection: (Playlist) -> Unit,
    onFolderPlaylistSelection: (Playlist) -> Unit,
    replaceSearchWithFilter: Boolean,
    gridPlaylists: Boolean,
    onGridPlaylistsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRefreshMediaStoreClick: () -> Unit
) {
    val context = LocalContext.current

    var collapseFraction by remember {
        mutableFloatStateOf(0f)
    }

    var searchFieldValue by rememberSaveable {
        mutableStateOf("")
    }
    var showSearchField by rememberSaveable {
        mutableStateOf(false)
    }

    var isInSelectionMode: Boolean by remember {
        mutableStateOf(false)
    }
    val selectedTracks = remember {
        mutableStateListOf<Track>()
    }
    val selectedPlaylists = remember {
        mutableStateListOf<Playlist>()
    }

    val topBarContent by remember {
        derivedStateOf {
            when {
                showSearchField && isInSelectionMode -> TopBarContent.Search
                showSearchField -> TopBarContent.Search
                isInSelectionMode -> TopBarContent.Selection
                else -> TopBarContent.Default
            }
        }
    }

    LazyGridWithCollapsibleTabsTopBar(
        gridState = gridState,
        topBarTabs = topBarTabs,
        defaultSelectedTab = defaultTab,
        onTabChange = {
            showSearchField = false
            searchFieldValue = ""

            isInSelectionMode = false
            selectedTracks.clear()
            selectedPlaylists.clear()

            onTabChange(it)
        },
        tabTitleTextStyle = MaterialTheme.typography.titleLarge.copy(
            fontSize = lerp(
                MaterialTheme.typography.titleLarge.fontSize,
                MaterialTheme.typography.displaySmall.fontSize,
                collapseFraction
            ),
            fontWeight = FontWeight.Bold
        ),
        topBarButtons = { tab ->
            AnimatedContent(
                targetState = topBarContent,
                label = "top-bar-search-bar-animation",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .align(Alignment.BottomCenter)
            ) { state ->
                when (state) {
                    TopBarContent.Default -> {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row {
                                IconButton(
                                    onClick = onSettingsClick
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Settings,
                                        contentDescription = context.resources.getString(
                                            R.string.settings
                                        )
                                    )
                                }

                                if (tab == Tab.Tracks) {
                                    TrackSortButton(
                                        sort = trackSort,
                                        order = trackSortOrder,
                                        onSortChange = {
                                            onTrackSortChange(it, null)
                                        },
                                        onSortOrderChange = {
                                            onTrackSortChange(null, it)
                                        }
                                    )
                                } else {
                                    PlaylistSortButton(
                                        sort = playlistSort,
                                        order = playlistSortOrder,
                                        onSortChange = {
                                            onPlaylistSortChange(it, null)
                                        },
                                        onSortOrderChange = {
                                            onPlaylistSortChange(null, it)
                                        }
                                    )
                                }
                            }

                            Row {
                                if (tab != Tab.Tracks) {
                                    IconButton(
                                        onClick = onGridPlaylistsClick
                                    ) {
                                        Icon(
                                            imageVector = if (gridPlaylists) {
                                                Icons.Rounded.GridView
                                            } else Icons.AutoMirrored.Rounded.ViewList,
                                            contentDescription = context.resources.getString(
                                                if (gridPlaylists) {
                                                    R.string.enable_list_view
                                                } else R.string.enable_grid_view
                                            )
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = onRefreshMediaStoreClick
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Radar,
                                        contentDescription = context.resources.getString(
                                            R.string.refresh
                                        )
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        showSearchField = true
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (replaceSearchWithFilter && tab == Tab.Tracks) {
                                            Icons.Rounded.FilterList
                                        } else Icons.Rounded.Search,
                                        contentDescription = context.resources.getString(
                                            R.string.track_search
                                        )
                                    )
                                }
                            }
                        }
                    }

                    TopBarContent.Search -> {
                        BackHandler {
                            showSearchField = false
                            searchFieldValue = ""
                        }
                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val focusRequester = remember {
                                FocusRequester()
                            }
                            SearchField(
                                value = searchFieldValue,
                                onValueChange = {
                                    searchFieldValue = it.trimStart()
                                },
                                icon = if (replaceSearchWithFilter && tab == Tab.Tracks) {
                                    Icons.Rounded.FilterList
                                } else Icons.Rounded.Search,
                                placeholder = if (replaceSearchWithFilter && tab == Tab.Tracks) {
                                    context.resources.getString(R.string.filter)
                                } else context.resources.getString(R.string.search),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 48.dp)
                                    .align(Alignment.Center)
                                    .focusRequester(focusRequester)
                            )

                            LaunchedEffect(Unit) {
                                focusRequester.requestFocus()
                            }

                            IconButton(
                                onClick = {
                                    showSearchField = false
                                    searchFieldValue = ""
                                },
                                modifier = Modifier.align(Alignment.CenterEnd)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = context.resources.getString(
                                        R.string.close_track_search
                                    )
                                )
                            }
                        }
                    }

                    TopBarContent.Selection -> {
                        BackHandler {
                            isInSelectionMode = false
                            selectedTracks.clear()
                            selectedPlaylists.clear()
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.background(color = MaterialTheme.colorScheme.background)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        isInSelectionMode = false
                                        selectedTracks.clear()
                                        selectedPlaylists.clear()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = context.resources.getString(R.string.back)
                                    )
                                }

                                Text(
                                    text = (selectedTracks.size + selectedPlaylists.size).toString(),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            Row {
                                if (tab == Tab.Tracks && selectedTracks.size < trackList.size) {
                                    IconButton(
                                        onClick = {
                                            selectedTracks.clear()
                                            selectedTracks.addAll(trackList)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.SelectAll,
                                            contentDescription = context.resources.getString(R.string.select_all)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        if (selectedTracks.isNotEmpty()) {
                                            onAddToQueueClick(selectedTracks.toList())
                                        } else if (selectedPlaylists.isNotEmpty()) {
                                            onAddToQueueClick(
                                                selectedPlaylists.flatMap {
                                                    it.trackList
                                                }.distinct()
                                            )
                                        }
                                        isInSelectionMode = false
                                        selectedTracks.clear()
                                        selectedPlaylists.clear()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AddToQueue,
                                        contentDescription = context.resources.getString(R.string.add_to_queue)
                                    )
                                }

                                if (tab == Tab.Tracks) {
                                    IconButton(
                                        onClick = {
                                            onAddToPlaylistClick(selectedTracks.toList())
                                            isInSelectionMode = false
                                            selectedTracks.clear()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                            contentDescription = context.resources.getString(R.string.add_to_playlist)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        showSearchField = true
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (replaceSearchWithFilter && tab == Tab.Tracks) {
                                            Icons.Rounded.FilterList
                                        } else Icons.Rounded.Search,
                                        contentDescription = context.resources.getString(
                                            R.string.track_search
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        collapseFraction = {
            collapseFraction = it
        },
        contentHorizontalArrangement = Arrangement.spacedBy(
            16.dp,
            alignment = Alignment.CenterHorizontally
        ),
        contentVerticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        gridCells = {
            if (it == Tab.Tracks || !gridPlaylists) GridCells.Fixed(1) else {
                GridCells.Adaptive(150.dp)
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) { tab ->
        when (tab) {
            Tab.Tracks -> {
                if (!isInSelectionMode) {
                    trackList(
                        trackList = trackList.filterTracks(searchFieldValue),
                        currentTrack = currentTrack,
                        onTrackClick = {
                            onTrackClick(
                                it,
                                Playlist(
                                    name = null,
                                    trackList = if (replaceSearchWithFilter) {
                                        trackList.filterTracks(searchFieldValue)
                                    } else trackList
                                )
                            )
                        },
                        onPlayNextClick = onPlayNextClick,
                        onAddToQueueClick = {
                            onAddToQueueClick(listOf(it))
                        },
                        onAddToPlaylistClick = {
                            onAddToPlaylistClick(listOf(it))
                        },
                        onViewTrackInfoClick = onViewTrackInfoClick,
                        onGoToAlbumClick = onGoToAlbumClick,
                        onGoToArtistClick = onGoToArtistClick,
                        onLongClick = {
                            isInSelectionMode = true
                            selectedTracks.add(it)
                        }
                    )
                } else {
                    selectionList(
                        trackList = trackList.filterTracks(searchFieldValue),
                        selectedTracks = selectedTracks,
                        onTrackClick = {
                            if (it in selectedTracks) {
                                selectedTracks.remove(it)
                            } else selectedTracks.add(it)

                            if (selectedTracks.isEmpty()) {
                                isInSelectionMode = false
                            }
                        }
                    )
                }
            }

            Tab.Playlists -> {
                if (gridPlaylists) {
                    if (!isInSelectionMode) {
                        playlistCards(
                            playlists = playlists.filterPlaylists(searchFieldValue),
                            sort = playlistSort,
                            sortOrder = playlistSortOrder,
                            fallbackPlaylistTitle = context.resources.getString(R.string.unknown),
                            showSinglePreview = false,
                            onCardClick = onPlaylistSelection,
                            onLongClick = {
                                isInSelectionMode = true
                                selectedPlaylists.add(it)
                            }
                        )
                    } else {
                        selectionCards(
                            playlists = playlists.filterPlaylists(searchFieldValue),
                            selectedPlaylists = selectedPlaylists,
                            sort = playlistSort,
                            sortOrder = playlistSortOrder,
                            fallbackPlaylistTitle = context.resources.getString(R.string.unknown),
                            showSinglePreview = false,
                            onCardClick = {
                                if (it in selectedPlaylists) {
                                    selectedPlaylists.remove(it)
                                } else selectedPlaylists.add(it)

                                if (selectedPlaylists.isEmpty()) {
                                    isInSelectionMode = false
                                }
                            }
                        )
                    }
                } else {
                    if (!isInSelectionMode) {
                        playlistRows(
                            playlists = playlists.filterPlaylists(searchFieldValue),
                            sort = playlistSort,
                            sortOrder = playlistSortOrder,
                            fallbackPlaylistTitle = context.resources.getString(R.string.unknown),
                            showSinglePreview = false,
                            onRowClick = onPlaylistSelection,
                            onLongClick = {
                                isInSelectionMode = true
                                selectedPlaylists.add(it)
                            }
                        )
                    } else {
                        selectionRows(
                            playlists = playlists.filterPlaylists(searchFieldValue),
                            selectedPlaylists = selectedPlaylists,
                            sort = playlistSort,
                            sortOrder = playlistSortOrder,
                            fallbackPlaylistTitle = context.resources.getString(R.string.unknown),
                            showSinglePreview = false,
                            onRowClick = {
                                if (it in selectedPlaylists) {
                                    selectedPlaylists.remove(it)
                                } else selectedPlaylists.add(it)

                                if (selectedPlaylists.isEmpty()) {
                                    isInSelectionMode = false
                                }
                            }
                        )
                    }
                }
            }

            Tab.Albums -> {
                if (gridPlaylists) {
                    if (!isInSelectionMode) {
                        playlistCards(
                            playlists = albumPlaylists.filterPlaylists(searchFieldValue),
                            sort = playlistSort,
                            sortOrder = playlistSortOrder,
                            fallbackPlaylistTitle = context.resources.getString(R.string.unknown_album),
                            showSinglePreview = true,
                            onCardClick = onAlbumPlaylistSelection,
                            onLongClick = {
                                isInSelectionMode = true
                                selectedPlaylists.add(it)
                            }
                        )
                    } else {
                        selectionCards(
                            playlists = albumPlaylists.filterPlaylists(searchFieldValue),
                            selectedPlaylists = selectedPlaylists,
                            sort = playlistSort,
                            sortOrder = playlistSortOrder,
                            fallbackPlaylistTitle = context.resources.getString(R.string.unknown_album),
                            showSinglePreview = true,
                            onCardClick = {
                                if (it in selectedPlaylists) {
                                    selectedPlaylists.remove(it)
                                } else selectedPlaylists.add(it)

                                if (selectedPlaylists.isEmpty()) {
                                    isInSelectionMode = false
                                }
                            }
                        )
                    }
                } else {
                    if (!isInSelectionMode) {
                        playlistRows(
                            playlists = albumPlaylists.filterPlaylists(searchFieldValue),
                            sort = playlistSort,
                            sortOrder = playlistSortOrder,
                            fallbackPlaylistTitle = context.resources.getString(R.string.unknown_album),
                            showSinglePreview = true,
                            onRowClick = onAlbumPlaylistSelection,
                            onLongClick = {
                                isInSelectionMode = true
                                selectedPlaylists.add(it)
                            }
                        )
                    } else {
                        selectionRows(
                            playlists = albumPlaylists.filterPlaylists(searchFieldValue),
                            selectedPlaylists = selectedPlaylists,
                            sort = playlistSort,
                            sortOrder = playlistSortOrder,
                            fallbackPlaylistTitle = context.resources.getString(R.string.unknown_album),
                            showSinglePreview = true,
                            onRowClick = {
                                if (it in selectedPlaylists) {
                                    selectedPlaylists.remove(it)
                                } else selectedPlaylists.add(it)

                                if (selectedPlaylists.isEmpty()) {
                                    isInSelectionMode = false
                                }
                            }
                        )
                    }
                }
            }

            Tab.Artists -> {
                if (gridPlaylists) {
                    if (!isInSelectionMode) {
                        playlistCards(
                            playlists = artistPlaylists.filterPlaylists(searchFieldValue),
                            sort = playlistSort,
                            sortOrder = playlistSortOrder,
                            fallbackPlaylistTitle = context.resources.getString(R.string.unknown_artist),
                            onCardClick = onArtistPlaylistSelection,
                            onLongClick = {
                                isInSelectionMode = true
                                selectedPlaylists.add(it)
                            }
                        )
                    } else {
                        selectionCards(
                            playlists = artistPlaylists.filterPlaylists(searchFieldValue),
                            selectedPlaylists = selectedPlaylists,
                            sort = playlistSort,
                            sortOrder = playlistSortOrder,
                            fallbackPlaylistTitle = context.resources.getString(R.string.unknown_artist),
                            onCardClick = {
                                if (it in selectedPlaylists) {
                                    selectedPlaylists.remove(it)
                                } else selectedPlaylists.add(it)

                                if (selectedPlaylists.isEmpty()) {
                                    isInSelectionMode = false
                                }
                            }
                        )
                    }
                } else {
                    if (!isInSelectionMode) {
                        playlistRows(
                            playlists = artistPlaylists.filterPlaylists(searchFieldValue),
                            sort = playlistSort,
                            sortOrder = playlistSortOrder,
                            fallbackPlaylistTitle = context.resources.getString(R.string.unknown_artist),
                            onRowClick = onArtistPlaylistSelection,
                            onLongClick = {
                                isInSelectionMode = true
                                selectedPlaylists.add(it)
                            }
                        )
                    } else {
                        selectionRows(
                            playlists = artistPlaylists.filterPlaylists(searchFieldValue),
                            selectedPlaylists = selectedPlaylists,
                            sort = playlistSort,
                            sortOrder = playlistSortOrder,
                            fallbackPlaylistTitle = context.resources.getString(R.string.unknown_artist),
                            onRowClick = {
                                if (it in selectedPlaylists) {
                                    selectedPlaylists.remove(it)
                                } else selectedPlaylists.add(it)

                                if (selectedPlaylists.isEmpty()) {
                                    isInSelectionMode = false
                                }
                            }
                        )
                    }
                }
            }

            Tab.Genres -> {
                if (gridPlaylists) {
                    if (!isInSelectionMode) {
                        playlistCards(
                            playlists = genrePlaylists.filterPlaylists(searchFieldValue),
                            sort = playlistSort,
                            sortOrder = playlistSortOrder,
                            fallbackPlaylistTitle = context.resources.getString(R.string.unknown_genre),
                            onCardClick = onGenrePlaylistSelection,
                            onLongClick = {
                                isInSelectionMode = true
                                selectedPlaylists.add(it)
                            }
                        )
                    } else {
                        selectionCards(
                            playlists = genrePlaylists.filterPlaylists(searchFieldValue),
                            selectedPlaylists = selectedPlaylists,
                            sort = playlistSort,
                            sortOrder = playlistSortOrder,
                            fallbackPlaylistTitle = context.resources.getString(R.string.unknown_genre),
                            onCardClick = {
                                if (it in selectedPlaylists) {
                                    selectedPlaylists.remove(it)
                                } else selectedPlaylists.add(it)

                                if (selectedPlaylists.isEmpty()) {
                                    isInSelectionMode = false
                                }
                            }
                        )
                    }
                } else {
                    if (!isInSelectionMode) {
                        playlistRows(
                            playlists = genrePlaylists.filterPlaylists(searchFieldValue),
                            sort = playlistSort,
                            sortOrder = playlistSortOrder,
                            fallbackPlaylistTitle = context.resources.getString(R.string.unknown_genre),
                            onRowClick = onGenrePlaylistSelection,
                            onLongClick = {
                                isInSelectionMode = true
                                selectedPlaylists.add(it)
                            }
                        )
                    } else {
                        selectionRows(
                            playlists = genrePlaylists.filterPlaylists(searchFieldValue),
                            selectedPlaylists = selectedPlaylists,
                            sort = playlistSort,
                            sortOrder = playlistSortOrder,
                            fallbackPlaylistTitle = context.resources.getString(R.string.unknown_genre),
                            onRowClick = {
                                if (it in selectedPlaylists) {
                                    selectedPlaylists.remove(it)
                                } else selectedPlaylists.add(it)

                                if (selectedPlaylists.isEmpty()) {
                                    isInSelectionMode = false
                                }
                            }
                        )
                    }
                }
            }

            Tab.Folders -> {
                if (gridPlaylists) {
                    if (!isInSelectionMode) {
                        playlistCards(
                            playlists = folderPlaylists.filterPlaylists(searchFieldValue),
                            sort = playlistSort,
                            sortOrder = playlistSortOrder,
                            fallbackPlaylistTitle = context.resources.getString(R.string.unknown_folder),
                            onCardClick = onFolderPlaylistSelection,
                            onLongClick = {
                                isInSelectionMode = true
                                selectedPlaylists.add(it)
                            }
                        )
                    } else {
                        selectionCards(
                            playlists = folderPlaylists.filterPlaylists(searchFieldValue),
                            selectedPlaylists = selectedPlaylists,
                            sort = playlistSort,
                            sortOrder = playlistSortOrder,
                            fallbackPlaylistTitle = context.resources.getString(R.string.unknown_folder),
                            onCardClick = {
                                if (it in selectedPlaylists) {
                                    selectedPlaylists.remove(it)
                                } else selectedPlaylists.add(it)

                                if (selectedPlaylists.isEmpty()) {
                                    isInSelectionMode = false
                                }
                            }
                        )
                    }
                } else {
                    if (!isInSelectionMode) {
                        playlistRows(
                            playlists = folderPlaylists.filterPlaylists(searchFieldValue),
                            sort = playlistSort,
                            sortOrder = playlistSortOrder,
                            fallbackPlaylistTitle = context.resources.getString(R.string.unknown_folder),
                            onRowClick = onFolderPlaylistSelection,
                            onLongClick = {
                                isInSelectionMode = true
                                selectedPlaylists.add(it)
                            }
                        )
                    } else {
                        selectionRows(
                            playlists = folderPlaylists.filterPlaylists(searchFieldValue),
                            selectedPlaylists = selectedPlaylists,
                            sort = playlistSort,
                            sortOrder = playlistSortOrder,
                            fallbackPlaylistTitle = context.resources.getString(R.string.unknown_folder),
                            onRowClick = {
                                if (it in selectedPlaylists) {
                                    selectedPlaylists.remove(it)
                                } else selectedPlaylists.add(it)

                                if (selectedPlaylists.isEmpty()) {
                                    isInSelectionMode = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScrollToTopAndLocateButtons(
    showScrollToTopButton: Boolean,
    onScrollToTopClick: suspend () -> Unit,
    showLocateButton: Boolean,
    onLocateClick: suspend () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize(),
        horizontalArrangement = Arrangement.End
    ) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        AnimatedVisibility(
            visible = showLocateButton,
            enter = expandHorizontally() + fadeIn(),
            exit = shrinkHorizontally() + fadeOut(),
        ) {
            FilledTonalIconButton(
                onClick = {
                    coroutineScope.launch {
                        onLocateClick()
                    }
                },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.MyLocation,
                    contentDescription = context.resources.getString(R.string.scroll_to_current_track)
                )
            }
        }

        AnimatedVisibility(
            visible = showScrollToTopButton,
            enter = expandHorizontally() + fadeIn(),
            exit = shrinkHorizontally() + fadeOut(),
        ) {
            FilledTonalIconButton(
                onClick = {
                    coroutineScope.launch {
                        onScrollToTopClick()
                    }
                },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = context.resources.getString(R.string.scroll_to_top)
                )
            }
        }
    }
}

@Serializable
private sealed interface PlayerRoutes {
    @Serializable
    data object Main : PlayerRoutes

    @Serializable
    data object Playlist : PlayerRoutes

    @Serializable
    data object MutablePlaylist : PlayerRoutes
}
