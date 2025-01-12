package infra.provider.providers

import infra.web.datasource.WebNovelHttpDataSource
import infra.web.datasource.providers.Fc2Novel
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import koinExtensions
import org.koin.test.KoinTest
import org.koin.test.inject

class Fc2NovelTest : DescribeSpec(), KoinTest {
    override fun extensions() = koinExtensions()
    private val dataSource by inject<WebNovelHttpDataSource>()
    private val provider get() = dataSource.providers[Fc2Novel.id]!!

    init {
        describe("getMetadata") {
            it("常规") {
                // https://novel.fc2.com/novel.php?mode=tc&nid=13762
                val metadata = provider.getMetadata("13762")
                metadata.title.shouldBe("僕が生まれるために・・・")
                metadata.authors.first().name.shouldBe("とらんぷヒロシ")
                metadata.authors.first().link.shouldBe("https://novel.fc2.com/user/3813980/")
                metadata.attentions.shouldBeEmpty()
                metadata.keywords.shouldContain("ファンタジー")
                metadata.introduction.shouldStartWith("大人向け")
                metadata.introduction.shouldEndWith("社内で人気者の部長。")
                metadata.toc[0].title.shouldBe("「おぎゃ～」・・・そして数年後")
                metadata.toc[0].chapterId.shouldBe("1")
                metadata.toc[56].title.shouldBe("魔法の先生")
                metadata.toc[56].chapterId.shouldBe("295")
            }
            it("R18") {
                // https://novel.fc2.com/novel.php?mode=tc&nid=133185
                val metadata = provider.getMetadata("133185")
                metadata.title.shouldBe("好き好き大好き☆お兄ちゃん❤…て、小悪魔な弟が言うんだもん")
            }
        }
    }
}