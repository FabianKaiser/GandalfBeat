package com.gandalf.beat.spotify

import android.content.Context
import android.util.Log
import com.gandalf.beat.Config
import com.spotify.android.appremote.api.*
import kotlin.concurrent.thread

class SpotifyController(
    private val context: Context,
) {
    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var pollingThread: Thread? = null
    private var currentTrack: SpotifyTrack? = null
    private var accessToken: String? = null

    fun connect() {
        val params = ConnectionParams.Builder(Config.SPOTIFY_CLIENT_ID)
            .setRedirectUri(Config.REDIRECT_URI)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(context, params, object : Connector.ConnectionListener {
            override fun onConnected(remote: SpotifyAppRemote) {
                spotifyAppRemote = remote
                startPolling()
            }

            override fun onFailure(error: Throwable) {
                Log.e("SpotifyController", "Connection failed: ${error.message}")
            }
        })
    }

    fun disconnect() {
        pollingThread?.interrupt()
        SpotifyAppRemote.disconnect(spotifyAppRemote)
    }

    fun hasValidAccessToken(): Boolean {
        return accessToken != null
    }

    fun setAccessToken(token: String) {
        accessToken = token
    }

    private fun startPolling() {
        pollingThread = thread {
            while (!Thread.interrupted()) {
                try {
                    spotifyAppRemote?.playerApi?.playerState?.setResultCallback { state ->


                        val trackId = state.track.uri.substringAfterLast(":")
                        if (trackId != currentTrack?.trackId) {
                            Log.d("SpotifyController", "Current track: ${state.track.name}")
                            Log.d("SpotifyController", "Current artist: ${state.track.artist.name}")
                            currentTrack = SpotifyTrack(
                                title = state.track.name,
                                artist = state.track.artist.name,
                                trackId = trackId
                            )
                        }
                    }
                    Thread.sleep(Config.SPOTIFY_TRACK_MS)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    fun getCurrentTrack(): SpotifyTrack? {
        return currentTrack
    }
}