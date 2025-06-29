package com.gandalf.beat

import android.content.Context
import java.util.Properties

object Config {
    // ✅ Static constants
    const val ENABLE_SPOTIFY_INTEGRATION = true
    const val REDIRECT_URI = "gandalfbeat://callback"
    const val SPOTIFY_RETRY_MS: Long = 5000
    const val SPOTIFY_TRACK_MS: Long = 5000
    const val LASTFM_TRACK_BPM_MS: Long = 5000

    // 🔐 Dynamic secrets from properties
    private val props = Properties()

    fun load(context: Context) {
        try {
            context.assets.open("password.properties").use {
                props.load(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val LASTFM_API_KEY: String
        get() = props.getProperty("LASTFM_API_KEY", "")

    val SONG_BPM_API_KEY: String
        get() = props.getProperty("SONG_BPM_API_KEY", "")

    val SPOTIFY_CLIENT_ID: String
        get() = props.getProperty("SPOTIFY_CLIENT_ID", "")
}
