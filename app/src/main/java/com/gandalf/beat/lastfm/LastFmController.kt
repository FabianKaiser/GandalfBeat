package com.gandalf.beat.lastfm

import android.util.Log
import com.gandalf.beat.Config
import com.gandalf.beat.spotify.SpotifyController
import com.gandalf.beat.spotify.SpotifyTrack
import kotlinx.coroutines.*

class LastFmController(
    private val bpmProvider: LastFmBpmProvider,
    private val onBpmUpdated: (Float) -> Unit,
    private val spotifyController: SpotifyController
) {
    private var pollingJob: Job? = null
    private var lastProcessedTrack: SpotifyTrack? = null

    suspend fun fetchBpm(track: SpotifyTrack?) {
        if (track == null) {
            Log.d("LastFmController", "SpotifyTrack not provided.")
            return
        }

        val artist = track.artist
        val title = track.title

        Log.d("LastFmController", "Fetching BPM from Last.fm for: $artist – $title")

        val bpm = bpmProvider.fetchBpm(artist, title)
        if (bpm != null) {
            Log.d("LastFmController", "🎶 Last.fm BPM: $bpm")
            onBpmUpdated(bpm.toFloat())
        } else {
            Log.w("LastFmController", "No BPM found for $artist – $title")
        }
    }

    fun startPolling(intervalMs: Long = Config.LASTFM_TRACK_BPM_MS) {
        if (pollingJob != null) return

        pollingJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                val currentTrack = spotifyController.getCurrentTrack()
                if (currentTrack != null && currentTrack != lastProcessedTrack) {
                    Log.d("LastFmController", "🎧 Detected new track: ${currentTrack.title}")
                    fetchBpm(currentTrack)
                    lastProcessedTrack = currentTrack
                }
                delay(intervalMs)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        Log.d("LastFmController", "🛑 Polling stopped")
    }
}
