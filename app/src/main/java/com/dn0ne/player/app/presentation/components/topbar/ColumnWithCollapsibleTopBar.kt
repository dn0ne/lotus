package com.dn0ne.player.app.presentation.components.topbar

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.dn0ne.player.app.presentation.components.animatable.rememberAnimatable
import kotlinx.coroutines.launch

@Composable
fun ColumnWithCollapsibleTopBar(
    topBarContent: @Composable BoxScope.() -> Unit,
    minTopBarHeight: Dp = 60.dp,
    maxTopBarHeight: Dp = 250.dp,
    collapsedByDefault: Boolean = false,
    collapseFraction: (Float) -> Unit = {},
    contentScrollState: ScrollState = rememberScrollState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    contentHorizontalAlignment: Alignment.Horizontal = Alignment.Start,
    contentVerticalArrangement: Arrangement.Vertical = Arrangement.Top,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val minTopBarHeight = remember { with(density) { minTopBarHeight.toPx() } }
    val maxTopBarHeight = remember { with(density) { maxTopBarHeight.toPx() } }
    val topBarHeight = rememberAnimatable(
        initialValue = if (collapsedByDefault) {
            minTopBarHeight
        } else maxTopBarHeight
    )

    LaunchedEffect(topBarHeight.value) {
        collapseFraction(
            (topBarHeight.value - minTopBarHeight) / (maxTopBarHeight - minTopBarHeight)
        )
    }

    val topBarScrollConnection = remember {
        return@remember object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val previousHeight = topBarHeight.value
                val newHeight =
                    (previousHeight + available.y - contentScrollState.value).coerceIn(
                        minTopBarHeight,
                        maxTopBarHeight
                    )
                coroutineScope.launch {
                    topBarHeight.snapTo(newHeight)
                }
                return Offset(0f, newHeight - previousHeight)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                coroutineScope.launch {
                    val threshold = (maxTopBarHeight - minTopBarHeight)
                    topBarHeight.animateTo(
                        targetValue = if (topBarHeight.value < threshold) minTopBarHeight else maxTopBarHeight,
                        animationSpec = spring(stiffness = Spring.StiffnessLow)
                    )
                }

                return super.onPostFling(consumed, available)
            }
        }
    }

    Box(
        modifier = modifier
            .nestedScroll(topBarScrollConnection)
    ) {
        Column {
            Spacer(
                modifier = Modifier
                    .height(with(density) { topBarHeight.value.toDp() })
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(contentScrollState)
                    .padding(contentPadding),
                horizontalAlignment = contentHorizontalAlignment,
                verticalArrangement = contentVerticalArrangement
            ) {
                content()

                Spacer(modifier = Modifier.height(100.dp))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { topBarHeight.value.toDp() }),
        ) {
            topBarContent()
        }
    }
}

@Composable
fun LazyColumnWithCollapsibleTopBar(
    topBarContent: @Composable BoxScope.() -> Unit,
    minTopBarHeight: Dp = 60.dp,
    maxTopBarHeight: Dp = 250.dp,
    collapsedByDefault: Boolean = false,
    collapseFraction: (Float) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    contentHorizontalAlignment: Alignment.Horizontal = Alignment.Start,
    contentVerticalArrangement: Arrangement.Vertical = Arrangement.Top,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val minTopBarHeight = remember { with(density) { minTopBarHeight.toPx() } }
    val maxTopBarHeight = remember { with(density) { maxTopBarHeight.toPx() } }
    val topBarHeight = rememberAnimatable(
        initialValue = if (collapsedByDefault) {
            minTopBarHeight
        } else maxTopBarHeight
    )

    LaunchedEffect(topBarHeight.value) {
        collapseFraction(
            (topBarHeight.value - minTopBarHeight) / (maxTopBarHeight - minTopBarHeight)
        )
    }

    val topBarScrollConnection = remember {
        return@remember object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val previousHeight = topBarHeight.value
                val newHeight = if (listState.firstVisibleItemIndex >= 0 && available.y < 0) {
                    (previousHeight + available.y).coerceIn(
                        minTopBarHeight,
                        maxTopBarHeight
                    )
                } else if (listState.firstVisibleItemIndex == 0) {
                    (previousHeight + available.y).coerceIn(
                        minTopBarHeight,
                        maxTopBarHeight
                    )
                } else previousHeight
                /*val newHeight =
                    (previousHeight + if (listState.firstVisibleItemIndex == 0) available.y else 0f).coerceIn(
                        minTopBarHeight,
                        maxTopBarHeight
                    )*/
                coroutineScope.launch {
                    topBarHeight.snapTo(newHeight)
                }
                return Offset(0f, newHeight - previousHeight)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                coroutineScope.launch {
                    val threshold = (maxTopBarHeight - minTopBarHeight)
                    topBarHeight.animateTo(
                        targetValue = if (topBarHeight.value < threshold) minTopBarHeight else maxTopBarHeight,
                        animationSpec = spring(stiffness = Spring.StiffnessLow)
                    )
                }

                return super.onPostFling(consumed, available)
            }
        }
    }

    Box(
        modifier = modifier
            .nestedScroll(topBarScrollConnection)
    ) {
        Column {
            Spacer(
                modifier = Modifier
                    .height(with(density) { topBarHeight.value.toDp() })
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                horizontalAlignment = contentHorizontalAlignment,
                verticalArrangement = contentVerticalArrangement
            ) {
                item(key = "spacer") {
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp))
                }

                content()

                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { topBarHeight.value.toDp() }),
        ) {
            topBarContent()
        }
    }
}