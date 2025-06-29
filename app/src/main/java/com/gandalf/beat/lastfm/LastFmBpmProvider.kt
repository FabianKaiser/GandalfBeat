package com.gandalf.beat.lastfm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl
import org.json.JSONObject

class LastFmBpmProvider(private val apiKey: String) {

    private val client = OkHttpClient()

    suspend fun fetchBpm(artist: String, track: String): Int? = withContext(Dispatchers.IO) {
        fetchFromTopTags(artist, track) ?: fetchFromTrackInfo(artist, track)
    }

    private fun extractBpmFromText(text: String): Int? {
        Log.d("LastFmBpmProvider", "Extracting BPM from text: $text")
        val match = Regex("""(\d{2,3})\s?(bpm|beats per minute)""", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    private suspend fun fetchFromTopTags(artist: String, track: String): Int? = withContext(Dispatchers.IO) {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("ws.audioscrobbler.com")
            .addPathSegment("2.0")
            .addQueryParameter("method", "track.getTopTags")
            .addQueryParameter("artist", artist)
            .addQueryParameter("track", track)
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("format", "json")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "GandalfBeat/1.0")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                Log.d("LastFmBpmProvider", "getTopTags response code: ${response.code} $responseBody")
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(responseBody ?: return@withContext null)
                val tags = json.optJSONObject("toptags")?.optJSONArray("tag") ?: return@withContext null

                for (i in 0 until tags.length()) {
                    val tag = tags.getJSONObject(i).optString("name")
                    extractBpmFromText(tag)?.let { return@withContext it }
                }
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun fetchFromTrackInfo(artist: String, track: String): Int? = withContext(Dispatchers.IO) {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("ws.audioscrobbler.com")
            .addPathSegment("2.0")
            .addQueryParameter("method", "track.getInfo")
            .addQueryParameter("artist", artist)
            .addQueryParameter("track", track)
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("format", "json")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "GandalfBeat/1.0")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                Log.d("LastFmBpmProvider", "getInfo response code: ${response.code} $responseBody")
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(responseBody ?: return@withContext null)
                val wiki = json.optJSONObject("track")?.optJSONObject("wiki")
                val summary = wiki?.optString("summary").orEmpty()
                val content = wiki?.optString("content").orEmpty()
                extractBpmFromText(summary) ?: extractBpmFromText(content)
            }
        } catch (e: Exception) {
            Log.e("LastFmBpmProvider", "Error fetching BPM from Last.fm: ${e.message}")
            null
        }
    }
}
