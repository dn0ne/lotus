package com.dn0ne.player.app.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayNextTargetIndexTest {

    private fun move(queue: List<String>, from: Int, to: Int) =
        queue.toMutableList().apply { add(to, removeAt(from)) }

    @Test
    fun `track queued after the current one lands right after it`() {
        val queue = listOf("a", "current", "b", "target")
        val moved = move(queue, 3, playNextTargetIndex(trackIndex = 3, currentTrackIndex = 1))

        assertEquals(listOf("a", "current", "target", "b"), moved)
    }

    @Test
    fun `track queued before the current one lands right after it`() {
        val queue = listOf("target", "a", "current", "b")
        val moved = move(queue, 0, playNextTargetIndex(trackIndex = 0, currentTrackIndex = 2))

        assertEquals(listOf("a", "current", "target", "b"), moved)
    }
}
