package com.example.gandalf

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.gandalf.beat.BeatSyncPlayer
import com.example.gandalf.lastfm.*
import com.example.gandalf.spotify.*
import kotlinx.coroutines.launch
import kotlin.concurrent.thread

class MainActivity() : AppCompatActivity(), TextureView.SurfaceTextureListener {

    private lateinit var textureView: TextureView
    private lateinit var beatPlayer: BeatSyncPlayer
    private lateinit var videoUri: Uri
    private lateinit var spotifyController: SpotifyController
    private lateinit var spotifyAuthManager: SpotifyAuthManager
    private lateinit var spotifyAuthLauncher: ActivityResultLauncher<Intent>
    private lateinit var lastFmController: LastFmController
    private var authFlowInProgress = false
    private var spotifyInstalled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 🎥 Video setup
        textureView = findViewById(R.id.texture_view)
        textureView.surfaceTextureListener = this

        videoUri = "android.resource://$packageName/${R.raw.gandalfloop}".toUri()
        beatPlayer = BeatSyncPlayer(this, videoUri)

        // 🟢 Spotify integration check
        spotifyInstalled = Config.ENABLE_SPOTIFY_INTEGRATION && isSpotifyInstalled(this)

        if (spotifyInstalled) {
            // 🎫 1. Spotify auth launcher
            spotifyAuthLauncher = registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                spotifyAuthManager.handleAuthResult(result.resultCode, result.data)
            }

            // 🧠 2. Spotify controller (initialized before it's needed in auth manager)
            spotifyController = SpotifyController(this)

            // 🛡️ 3. Auth manager depends on launcher + controller
            spotifyAuthManager = SpotifyAuthManager(
                context = this,
                clientId = Config.SPOTIFY_CLIENT_ID,
                redirectUri = Config.REDIRECT_URI,
                launcher = spotifyAuthLauncher,
                onTokenReceived = { token ->
                    spotifyController.setAccessToken(token)
                }
            )

            // 🎼 4. Last.fm BPM provider
            val bpmProvider = LastFmBpmProvider(Config.LASTFM_API_KEY)

            // 🧮 5. Last.fm controller with polling and UI BPM updates
            lastFmController = LastFmController(
                bpmProvider = bpmProvider,
                onBpmUpdated = { bpm -> beatPlayer.setBpm(bpm) },
                spotifyController = spotifyController
            )

            // 🚀 6. begin polling
            lifecycleScope.launch {
                lastFmController.startPolling()
            }
        }
    }


    override fun onStart() {
        super.onStart()
        if (spotifyInstalled) {
            retrySpotifyLoginUntilToken()
        }
    }

    override fun onStop() {
        super.onStop()
        if (spotifyInstalled) {
            spotifyController.disconnect()
            lastFmController.stopPolling()
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        beatPlayer.start(Surface(surface))
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        beatPlayer.stop()
        return true
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    fun isSpotifyInstalled(context: Activity): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.spotify.music", 0)
            Log.d("SpotifyInstalled", "Spotify is installed")
            true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.d("SpotifyInstalled", "Spotify is not installed")
            false
        }
    }

    private fun retrySpotifyLoginUntilToken(retries: Int = 5) {
        thread {
            var attempt = 0
            while (attempt < retries) {
                if (authFlowInProgress) {
                    Log.d("SpotifyAuthRetry", "⏸ Waiting, auth is in progress...")
                    Thread.sleep(Config.SPOTIFY_RETRY_MS)
                    continue
                }

                Log.d("SpotifyAuthRetry", "🔁 Attempt ${attempt + 1} to start login flow...")

                runOnUiThread {
                    authFlowInProgress = true
                    spotifyAuthManager.launchLoginFlow()
                }

                repeat(5) {
                    Thread.sleep(Config.SPOTIFY_RETRY_MS)
                    if (spotifyController.hasValidAccessToken()) {
                        Log.d("SpotifyAuthRetry", "✅ Token received, stopping retry loop")
                        runOnUiThread { spotifyController.connect() }
                        return@thread
                    }
                }

                authFlowInProgress = false
                attempt++
            }
            Log.e("SpotifyAuthRetry", "❌ Failed to get token after $retries attempts")
        }
    }
}
