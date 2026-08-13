package com.dn0ne.player.app.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayNextTargetIndexTest {

    /** Runs one "play next" pick on a queue whose playing track is named "current". */
    private fun playNext(queue: List<String>, track: String, anchor: String? = null): List<String> {
        val afterIndex = playNextAfterIndex(
            currentTrackIndex = queue.indexOf("current"),
            anchorIndex = anchor?.let { queue.indexOf(it) } ?: -1
        )
        val trackIndex = queue.indexOf(track)

        return queue.toMutableList().apply {
            add(playNextTargetIndex(trackIndex, afterIndex), removeAt(trackIndex))
        }
    }

    @Test
    fun `track queued after the current one lands right after it`() {
        assertEquals(
            listOf("a", "current", "target", "b"),
            playNext(listOf("a", "current", "b", "target"), track = "target")
        )
    }

    @Test
    fun `track queued before the current one lands right after it`() {
        assertEquals(
            listOf("a", "current", "target", "b"),
            playNext(listOf("target", "a", "current", "b"), track = "target")
        )
    }

    @Test
    fun `successive picks play in the order they were added`() {
        val queue = listOf("current", "a", "first", "second")
        val afterFirst = playNext(queue, track = "first")

        assertEquals(
            listOf("current", "first", "second", "a"),
            playNext(afterFirst, track = "second", anchor = "first")
        )
    }

    @Test
    fun `pick starts a fresh block once the previous one has been played`() {
        // "played" sits behind the playing track, so it is no longer a pending pick
        assertEquals(
            listOf("played", "current", "target", "a"),
            playNext(listOf("played", "current", "a", "target"), track = "target", anchor = "played")
        )
    }
}
