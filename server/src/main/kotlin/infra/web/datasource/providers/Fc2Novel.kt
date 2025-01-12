package infra.web.datasource.providers

import infra.common.Page
import infra.common.emptyPage
import infra.web.WebNovelAttention
import infra.web.WebNovelAuthor
import infra.web.WebNovelType
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.jsoup.nodes.Document

class Fc2Novel(
    private val client: HttpClient,
) : WebNovelProvider {
    companion object {
        const val id = "fc2novel"
        const val LOOP_LIMIT = 100
    }
    override suspend fun getRank(options: Map<String, String>): Page<RemoteNovelListItem> {
        return emptyPage()
        // TODO: 增加排行获取
    }

    private suspend fun getDocument(url: String): Document {
        return client.get(url).document().also { it.setBaseUri(url) }
    }

    override suspend fun getMetadata(novelId: String): RemoteNovelMetadata {
        val metadataUrl = "https://novel.fc2.com/novel.php?mode=tc&nid=$novelId"
        val doc = getDocument(metadataUrl)

        val title = doc
            .selectFirst(".default_page_title")!!
            .text()

        val author = doc
            .selectFirst(".username > a")!!
            .let {
                WebNovelAuthor(
                    name = it.text(),
                    link = it.absUrl("href")
                )
            }

        val row = { label: String -> doc
            .selectFirst("th:matches(^$label\$)")!!
            .nextElementSibling()!!
            .text()
        }

        val type = row("状態")
            .let {
                when {
                    it.startsWith("完成") -> WebNovelType.已完结
                    it.startsWith("連載中") -> WebNovelType.连载中
                    else -> throw RuntimeException("无法解析的小说类型:$it")
                }
            }

        val attentions = buildList {
            if (doc.select("span.adultred").isNotEmpty()) {
                add(WebNovelAttention.R18)
            }
        }

        val keywords = row("ジャンル")
            .split(',')
            .map(String::trim)

        val introduction = doc
            .selectFirst("p.novel_comment")!!
            .text()

        val toc = buildList {
            var currentPage = doc
            for (i in 0..LOOP_LIMIT) {
                if (i >= LOOP_LIMIT) throw RuntimeException("已循环${i}次，仍未获取完小说目录，可能陷入了死循环")

                currentPage.select("li.novel_subtitle > a").forEach { el ->
                    add(
                        RemoteNovelMetadata.TocItem(
                            title = el.text(),
                            chapterId = el.attr("href").parseUrlEncodedParameters()["pg"]
                            // TODO: add createAt
                        )
                    )
                }
                val nextPageUrl = currentPage
                    .selectFirst(".navi_page.navi_prev_next > li.right > a")
                    ?.absUrl("href")
                if (nextPageUrl == null) break

                currentPage = getDocument(nextPageUrl)
            }
        }

        return RemoteNovelMetadata(
            title = title,
            authors = listOf(author),
            type = type,
            attentions = attentions,
            keywords = keywords,
            points = 0, // TODO: fc2 novel 没有点数
            totalCharacters = 0, // TODO: fc2 novel 没地方能获取
            introduction = introduction,
            toc = toc
        )
    }

    override suspend fun getChapter(
        novelId: String,
        chapterId: String
    ): RemoteChapter {
        TODO("Not yet implemented")
    }
}