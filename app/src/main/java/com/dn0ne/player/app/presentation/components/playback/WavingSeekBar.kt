package com.dn0ne.player.app.presentation.components.playback

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.dn0ne.player.app.presentation.components.animatable.rememberAnimatable
import kotlin.math.PI
import kotlin.math.cos

@Composable
fun WavingSeekBar(
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    onPositionChange: (Long) -> Unit,
    enableWaving: Boolean = true,
    waveHeight: Dp = 6.dp,
    waveWidth: Dp = 6.dp,
    strokeWidth: Dp = 3.dp,
    handleSize: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    val progressBarHeight by remember {
        mutableStateOf(waveHeight)
    }
    val handlePadding by remember {
        mutableStateOf(12.dp)
    }
    val handleSize by remember {
        mutableStateOf(handleSize)
    }

    var handleOffsetFraction by remember {
        mutableFloatStateOf(0f)
    }

    var handleOffset by remember {
        mutableFloatStateOf(0f)
    }

    var isHandleInDrag by remember {
        mutableStateOf(false)
    }

    var barWidth by remember {
        mutableFloatStateOf(0f)
    }

    var currentPosition by remember {
        mutableLongStateOf(position)
    }
    var currentDuration by remember {
        mutableLongStateOf(duration)
    }
    LaunchedEffect(position, duration) {
        if (!isHandleInDrag) {
            currentPosition = position
            currentDuration = duration

            handleOffsetFraction = if (duration == 0L) 0f else {
                position.toFloat() / duration
            }

            handleOffset = handleOffsetFraction * barWidth
        }
    }

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isLtr by remember {
        mutableStateOf(layoutDirection == LayoutDirection.Ltr)
    }

    Column(
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .onGloballyPositioned {
                        barWidth = it.size.width.toFloat()
                        handleOffset = barWidth * handleOffsetFraction
                    }
                    .matchParentSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                handleOffset = (if (isLtr) it.x else barWidth - it.x).coerceIn(0f, barWidth)
                                handleOffsetFraction = handleOffset / barWidth
                                onPositionChange(
                                    (handleOffsetFraction * currentDuration).toLong()
                                )
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                isHandleInDrag = true
                            },
                            onDragEnd = {
                                isHandleInDrag = false
                                onPositionChange(
                                    (handleOffsetFraction * currentDuration).toLong()
                                )
                            },
                        ) { _, dragAmount ->
                            handleOffset += if (isLtr) dragAmount.x else -dragAmount.x

                            handleOffset = handleOffset.coerceIn(0f, barWidth)
                            handleOffsetFraction = handleOffset / barWidth
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledSeekBarSegment(
                    color = MaterialTheme.colorScheme.primary,
                    enableWaving = enableWaving && isPlaying && !isHandleInDrag,
                    waveBackwards = !isLtr,
                    waveWidth = with(density) {
                        waveWidth.toPx()
                    },
                    strokeWidth = with(density) {
                        strokeWidth.toPx()
                    },
                    modifier = Modifier
                        .fillMaxWidth(
                            animateFloatAsState(
                                targetValue = handleOffsetFraction,
                                label = "filled-seek-bar-segment-animation"
                            ).value
                        )
                        .height(progressBarHeight)
                )

                RestSeekBarSegment(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    strokeWidth = with(density) {
                        strokeWidth.toPx()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(progressBarHeight)
                )
            }

            var lastAngle by remember {
                mutableFloatStateOf(0f)
            }
            val handleRotation = rememberAnimatable(initialValue = 0f)
            LaunchedEffect(isPlaying) {
                val startAngle = (lastAngle.toInt() % 90).toFloat()
                while (isPlaying) {
                    handleRotation.animateTo(
                        targetValue = startAngle + if (isLtr) 90f else -90f,
                        animationSpec = tween(durationMillis = 1000, easing = LinearEasing)
                    ) {
                        lastAngle = value
                    }
                    handleRotation.snapTo(startAngle)
                }
            }

            SeekBarHandle(
                modifier = Modifier
                    .size(handleSize + handlePadding * 2)
                    .padding(handlePadding)
                    .offset(
                        x = animateDpAsState(
                            targetValue = with(density) {
                                handleOffset.toDp() - (handleSize + handlePadding * 2) / 2
                            },
                            label = "seek-bar-handle-position-animation"
                        ).value
                    )
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                isHandleInDrag = true
                            },
                            onDragEnd = {
                                isHandleInDrag = false
                                onPositionChange(
                                    (handleOffsetFraction * currentDuration).toLong()
                                )
                            },
                        ) { _, dragAmount ->
                            handleOffset += if (isLtr) dragAmount.x else -dragAmount.x

                            handleOffset = handleOffset.coerceIn(0f, barWidth)
                            handleOffsetFraction = handleOffset / barWidth
                        }
                    }
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        rotationZ = if (isPlaying) handleRotation.value else lastAngle
                    }
                    .clip(RoundedCornerShape(handleSize / 3))
                    .background(color = MaterialTheme.colorScheme.primary)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val handlePositionHours =
                (currentDuration * handleOffsetFraction).toLong() / 1000 / 60 / 60
            val handlePositionMinutes =
                (currentDuration * handleOffsetFraction).toLong() / 1000 / 60 % 60
            val handlePositionSeconds =
                (currentDuration * handleOffsetFraction).toLong() / 1000 % 60
            val handlePositionText = buildString {
                if (handlePositionHours > 0) {
                    append("$handlePositionHours".padStart(2, '0'))
                    append(":")
                }
                append("$handlePositionMinutes".padStart(2, '0'))
                append(":")
                append("$handlePositionSeconds".padStart(2, '0'))
            }

            val durationHours = currentDuration / 1000 / 60 / 60
            val durationMinutes = currentDuration / 1000 / 60 % 60
            val durationSeconds = currentDuration / 1000 % 60
            val durationText = buildString {
                if (durationHours > 0) {
                    append("$durationHours".padStart(2, '0'))
                    append(":")
                }
                append("$durationMinutes".padStart(2, '0'))
                append(":")
                append("$durationSeconds".padStart(2, '0'))
            }

            Text(
                text = handlePositionText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = durationText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FilledSeekBarSegment(
    color: Color,
    enableWaving: Boolean = true,
    waveBackwards: Boolean = false,
    waveWidth: Float? = null,
    strokeWidth: Float = 10f,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "infinite-transition")

    var canvasHeight by remember {
        mutableFloatStateOf(0f)
    }
    val waveWidth by remember {
        derivedStateOf {
            waveWidth ?: (canvasHeight / 1.5f)
        }
    }
    val waveHeight by animateFloatAsState(
        targetValue = if (enableWaving) canvasHeight else 0f,
        label = "wave-height"
    )

    val strokeWidth by remember {
        derivedStateOf {
            strokeWidth
        }
    }

    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (if (waveBackwards) waveWidth else -waveWidth) * 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing)
        ),
        label = "wave-offset"
    )

    Canvas(modifier = modifier
        .onGloballyPositioned {
            canvasHeight = it.size.height.toFloat()
        }
    ) {

        val path = Path().apply {
            moveTo(x = 0f, y = canvasHeight / 2 + waveHeight * cos(-offset / waveWidth) / 2)
            for (i in 0..size.width.toInt()) {
                lineTo(
                    x = i.toFloat(),
                    y = canvasHeight / 2 + waveHeight * cos((i.toFloat() - offset) / waveWidth) / 2
                )
            }
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round
            )
        )
    }
}

@Composable
fun RestSeekBarSegment(
    color: Color,
    strokeWidth: Float = 10f,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(x = 0f, y = size.height / 2)
            lineTo(x = size.width, y = size.height / 2)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round
            )
        )
    }
}

@Composable
fun SeekBarHandle(modifier: Modifier = Modifier) {
    Box(modifier = modifier)
}