package com.gandalf.beat.spotify

data class SpotifyTrack(
    val title: String,
    val artist: String,
    val trackId: String? = null,
)
