package com.verbilume.app.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioController(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private val _playing = MutableStateFlow<String?>(null)
    val playing: StateFlow<String?> = _playing.asStateFlow()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change <= 0) stop()
    }

    fun play(assetPath: String?, fallbackText: String? = null) {
        val path = assetPath ?: return
        stop()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        val focus = focusRequest?.let { audioManager.requestAudioFocus(it) }
        if (focus == AudioManager.AUDIOFOCUS_REQUEST_FAILED) return
        try {
            val afd = context.assets.openFd(path)
            val mp = MediaPlayer()
            afd.use {
                mp.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            mp.setAudioAttributes(attributes)
            mp.setOnCompletionListener { finished ->
                if (player === finished) {
                    _playing.value = null
                    releasePlayer()
                }
            }
            mp.setOnErrorListener { failed, _, _ ->
                if (player === failed) {
                    _playing.value = null
                    releasePlayer()
                }
                true
            }
            mp.prepare()
            mp.start()
            player = mp
            _playing.value = path
        } catch (e: Exception) {
            _playing.value = null
            releasePlayer()
        }
    }

    fun stop() {
        releasePlayer()
        _playing.value = null
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun releasePlayer() {
        player?.runCatching {
            if (isPlaying) stop()
            release()
        }
        player = null
    }

    fun release() {
        stop()
    }
}
