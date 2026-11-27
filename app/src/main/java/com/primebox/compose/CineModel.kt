package com.primebox.compose

import com.primebox.data.Card

data class CastMember(val name: String, val role: String, val color: Long)

/** Hero model, ported from the CineGlass reference design. */
data class Movie(
    val id: Int,
    val title: String,
    val tagline: String,
    val release: String,
    val rating: Float,
    val views: String,
    val genre: String,
    val age: String,
    val quality: String,
    val story: String,
    val c1: Long,
    val c2: Long,
    val cast: List<CastMember>,
    val durationMin: Int,
    val posterUrl: String? = null,
    /** carries the provider URL so navigation back to a Card is lossless */
    val videoUrl: String
)

fun Movie.posterLarge(): String? = posterUrl?.replace("/w500/", "/w780/")

/** Provider card -> hero movie. */
fun Card.toMovie(index: Int): Movie = Movie(
    id = index,
    title = name,
    tagline = "",
    release = type ?: "",
    rating = 4.0f,
    views = "",
    genre = type ?: "Movie",
    age = "13+",
    quality = quality ?: "HD",
    story = "",
    c1 = 0xFF2A2C38,
    c2 = 0xFF101116,
    cast = emptyList(),
    durationMin = 0,
    posterUrl = poster,
    videoUrl = url
)

fun Movie.toCard(): Card = Card(
    name = title,
    url = videoUrl,
    poster = posterUrl,
    type = genre.takeIf { it.isNotBlank() },
    quality = quality,
    api = null
)
