package com.dn0ne.player.app.domain.playback

import androidx.media3.common.Player

val Player.playbackMode: PlaybackMode
    get() = when {
        shuffleModeEnabled -> PlaybackMode.Shuffle
        repeatMode == Player.REPEAT_MODE_ONE -> PlaybackMode.RepeatOne
        else -> PlaybackMode.Repeat
    }

fun Player.setPlaybackMode(playbackMode: PlaybackMode) {
    when (playbackMode) {
        PlaybackMode.Repeat -> {
            repeatMode = Player.REPEAT_MODE_ALL
            shuffleModeEnabled = false
        }

        PlaybackMode.RepeatOne -> {
            repeatMode = Player.REPEAT_MODE_ONE
            shuffleModeEnabled = false
        }

        PlaybackMode.Shuffle -> {
            repeatMode = Player.REPEAT_MODE_ALL
            shuffleModeEnabled = true
        }
    }
}

fun PlaybackMode.next(): PlaybackMode = PlaybackMode.entries[(ordinal + 1) % PlaybackMode.entries.size]
