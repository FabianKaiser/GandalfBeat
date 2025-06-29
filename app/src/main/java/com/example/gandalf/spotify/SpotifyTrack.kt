package com.example.gandalf.spotify

data class SpotifyTrack(
    val title: String,
    val artist: String,
    val trackId: String? = null,
)
