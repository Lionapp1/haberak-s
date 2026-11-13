package com.lion.haberakisi.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class NewsSource(val name: String, val url: String)

object NewsRepository {

    val sources = listOf(
        NewsSource("Gündem", "https://www.ntv.com.tr/gundem.rss"),
        NewsSource("Son Dakika", "https://www.ntv.com.tr/son-dakika.rss"),
        NewsSource("Spor", "https://www.ntv.com.tr/spor.rss"),
        NewsSource("Teknoloji", "https://www.ntv.com.tr/teknoloji.rss"),
        NewsSource("BBC Türkçe", "https://feeds.bbci.co.uk/turkce/rss.xml"),
    )

    suspend fun fetchAll(): Result<List<Article>> = withContext(Dispatchers.IO) {
        coroutineScope {
            val results = sources.map { source ->
                async {
                    runCatching {
                        val xml = httpGet(source.url)
                        RssParser.parse(xml, source.name)
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten()

            if (results.isEmpty()) Result.failure(IllegalStateException("Hiç haber alınamadı"))
            else Result.success(results.distinctBy { it.link })
        }
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 12_000
        conn.readTimeout = 12_000
        conn.setRequestProperty("User-Agent", "HaberAkisi/1.0 (Android)")
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
