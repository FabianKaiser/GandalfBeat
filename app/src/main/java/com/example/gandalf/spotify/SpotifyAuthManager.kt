package com.example.gandalf.spotify

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse

class SpotifyAuthManager(
    private val context: Activity,
    private val clientId: String,
    private val redirectUri: String,
    private val launcher: ActivityResultLauncher<Intent>,
    private val onTokenReceived: (String) -> Unit
) {
    fun launchLoginFlow() {
        val request = AuthorizationRequest.Builder(
            clientId,
            AuthorizationResponse.Type.TOKEN,
            redirectUri
        )
            .setScopes(arrayOf("user-read-currently-playing", "user-read-playback-state"))
            .build()

        val intent = AuthorizationClient.createLoginActivityIntent(context, request)
        launcher.launch(intent)
    }

    fun handleAuthResult(resultCode: Int, data: Intent?) {
        val response = AuthorizationClient.getResponse(resultCode, data)
        when (response.type) {
            AuthorizationResponse.Type.TOKEN -> {
                Log.d("SpotifyAuth", "🎫 Access Token received")
                onTokenReceived(response.accessToken)
            }
            AuthorizationResponse.Type.ERROR -> {
                Log.e("SpotifyAuth", "❌ Error: ${response.error}")
            }
            else -> {
                Log.w("SpotifyAuth", "⚠️ Auth canceled or unknown result")
            }
        }
    }
}
