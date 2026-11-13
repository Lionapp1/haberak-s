package com.lion.haberakisi.data

data class Article(
    val title: String,
    val link: String,
    val description: String,
    val imageUrl: String?,
    val source: String,
    val pubDate: String,
)
