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
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

class Fc2(
    private val client: HttpClient,
) : WebNovelProvider {
    companion object {
        const val id = "fc2"
        const val TOC_LOOP_LIMIT = 100
        const val CHAPTER_LOOP_LIMIT = 500
    }
    override suspend fun getRank(options: Map<String, String>): Page<RemoteNovelListItem> {
        return emptyPage()
        // TODO: 增加排行获取
    }

    private suspend fun getDocument(url: String): Document {
        return client.get(url).document().apply { setBaseUri(url) }
    }

    override suspend fun getMetadata(novelId: String): RemoteNovelMetadata {
        val doc = getDocument("https://novel.fc2.com/novel.php?cnsnt=1&mode=tc&nid=$novelId")

        val title = doc
            .selectFirst(".default_page_title")!!
            .ownText()

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
            for (i in 0..TOC_LOOP_LIMIT) {
                if (i >= TOC_LOOP_LIMIT) throw RuntimeException("死循环保护：已循环${i}次，仍未获取完小说目录，停止尝试")

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
                    ?: break
                currentPage = getDocument(nextPageUrl)
            }
        }

        return RemoteNovelMetadata(
            title = title,
            authors = listOf(author),
            type = type,
            attentions = attentions,
            keywords = keywords,
            points = 0, // TODO: fc2没有点数
            totalCharacters = 0, // TODO: fc2没地方获取小说字数
            introduction = introduction,
            toc = toc
        )
    }

    override suspend fun getChapter(
        novelId: String,
        chapterId: String
    ): RemoteChapter {
        val doc = getDocument("https://novel.fc2.com/novel.php?mode=rd&nid=$novelId&pg=$chapterId&cnsnt=1")

        val paragraphs = buildList {
            var currentPage = doc

            for (i in 1..CHAPTER_LOOP_LIMIT) {
                if (i >= CHAPTER_LOOP_LIMIT) throw RuntimeException("死循环保护：已循环${i}次，仍未获取完小说内容，停止尝试")
                // 获取本页内容，如果为--- 鋭意連載中 ---，无内容，退出
                val el = currentPage
                    .selectFirst("div.novel_body")
                    ?: break

                val novelSubtitle = currentPage.selectFirst(".novel_subtitle")
                when {
                    i == 1 && novelSubtitle == null -> throw RuntimeException("第1页无标题，非章节起始页")
                    i >= 2 && novelSubtitle != null -> break // 从第2页开始，碰到标题代表章节结束，退出
                }

                val text = StringBuilder().apply {
                    el
                        .childNodes()
                        .forEach {
                            if (it is Element) {
                                if (it.tagName() == "br") {
                                    append('\n')
                                } else {
                                    append(it.text())
                                }
                            } else if (it is TextNode) {
                                append(it.text())
                            }
                        }
                }

                addAll(
                    text
                        .removePrefix(" ")
                        .removeSuffix(" ")
                        .lines()
                )

                // 去下一页，如果不存在下一页，退出
                val nextPageUrl = currentPage
                    .selectFirst(".navi_page.navi_prev_next > li.right > a")
                    ?.absUrl("href")
                    ?: break
                currentPage = getDocument(nextPageUrl)
            }
        }

        return RemoteChapter(paragraphs = paragraphs)
    }
}