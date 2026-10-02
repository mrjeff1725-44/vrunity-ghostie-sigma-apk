package com.vrunity.vrapk

import android.content.Context
import android.media.MediaPlayer
import android.util.Log

// A sound that stands somewhere in the scene. It is loud where it stands, fades out
// with distance across its range, and is heard on the side of the head it is on.
object Audio {
    private class Playing(val player: MediaPlayer, val pos: FloatArray, val range: Float, val volume: Float)

    private val sounds = ArrayList<Playing>()
    private var started = false

    // The sounds set to play on start begin here; the rest are loaded and wait.
    fun start(context: Context, list: List<Scene.Sound>) {
        if (started) return
        started = true
        for (sound in list) {
            try {
                val player = MediaPlayer()
                val file = context.assets.openFd(sound.file)
                player.setDataSource(file.fileDescriptor, file.startOffset, file.length)
                file.close()
                player.isLooping = sound.loop
                player.setVolume(sound.volume, sound.volume)
                player.prepare()
                if (sound.auto) player.start()
                sounds.add(Playing(player, sound.pos, sound.range, sound.volume))
            } catch (t: Throwable) {
                // A sound that will not open must never stop the game.
                Log.e("VRUnityXR", "Sound not available: " + sound.file + " " + t)
            }
        }
    }

    // Where the scene's sounds are around the player's head.
    fun update(listenerX: Float, listenerY: Float, listenerZ: Float, rightX: Float, rightZ: Float) {
        for (s in sounds) {
            var gain = s.volume
            var pan = 0f
            if (s.range > 0f) {
                val dx = s.pos[0] - listenerX
                val dy = s.pos[1] - listenerY
                val dz = s.pos[2] - listenerZ
                val distance = Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
                gain *= (1f - distance / s.range).coerceIn(0f, 1f)
                if (distance > 0.01f) pan = ((dx * rightX + dz * rightZ) / distance).coerceIn(-1f, 1f)
            }
            val left = (gain * (1f - 0.5f * pan.coerceAtLeast(0f))).coerceIn(0f, 1f)
            val right = (gain * (1f + 0.5f * pan.coerceAtMost(0f))).coerceIn(0f, 1f)
            try {
                s.player.setVolume(left, right)
            } catch (t: Throwable) {
                Log.e("VRUnityXR", "Sound not playing: " + t)
            }
        }
    }

    fun stop() {
        for (s in sounds) {
            try { s.player.stop() } catch (t: Throwable) {}
            try { s.player.release() } catch (t: Throwable) {}
        }
        sounds.clear()
        started = false
    }
}
