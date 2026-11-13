package com.lion.haberakisi.data

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

object RssParser {

    fun parse(xml: String, fallbackSource: String): List<Article> {
        val articles = mutableListOf<Article>()
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setInput(StringReader(xml))

        var inItem = false
        var title = ""; var link = ""; var desc = ""; var date = ""; var image: String? = null
        var currentTag: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase()) {
                        "item", "entry" -> { inItem = true; title=""; link=""; desc=""; date=""; image=null }
                        "title" -> if (inItem) { currentTag = "title" }
                        "link" -> if (inItem) {
                            val href = parser.getAttributeValue(null, "href")
                            if (href != null) link = href else currentTag = "link"
                        }
                        "description", "summary", "content:encoded", "content" -> if (inItem) currentTag = "desc"
                        "pubdate", "published", "updated", "dc:date" -> if (inItem) currentTag = "date"
                        "enclosure", "media:content", "media:thumbnail" -> if (inItem) {
                            parser.getAttributeValue(null, "url")?.let {
                                if (it.startsWith("http")) image = it
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> if (inItem) {
                    val text = parser.text?.trim().orEmpty()
                    when (currentTag) {
                        "title" -> title += text
                        "link" -> link += text
                        "desc" -> desc += text
                        "date" -> date += text
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name.lowercase()) {
                        "item", "entry" -> {
                            if (title.isNotBlank() && link.isNotBlank()) {
                                articles += Article(
                                    title = title.trim(),
                                    link = link.trim(),
                                    description = cleanHtml(desc).trim(),
                                    imageUrl = image ?: extractFirstImage(desc),
                                    source = fallbackSource,
                                    pubDate = date.trim(),
                                )
                            }
                            inItem = false
                        }
                    }
                    currentTag = null
                }
            }
            event = parser.next()
        }
        return articles
    }

    private fun cleanHtml(html: String): String =
        html.replace(Regex("<[^>]*>"), " ")
            .replace(Regex("&[a-zA-Z#0-9]+;"), " ")
            .replace(Regex("\\s+"), " ")
            .take(220)

    private fun extractFirstImage(html: String): String? {
        val m = Regex("<img[^>]+src=[\"']([^\"']+)[\"']").find(html) ?: return null
        val url = m.groupValues[1]
        return if (url.startsWith("http")) url else null
    }
}
