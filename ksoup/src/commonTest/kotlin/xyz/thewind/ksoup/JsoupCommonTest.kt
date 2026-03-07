package xyz.thewind.ksoup

import xyz.thewind.ksoup.helper.KsoupTransport
import xyz.thewind.ksoup.helper.KsoupTransportExecutor
import xyz.thewind.ksoup.helper.RequestData
import xyz.thewind.ksoup.helper.ResponseData
import xyz.thewind.ksoup.helper.TransportResponse
import xyz.thewind.ksoup.nodes.Attributes
import xyz.thewind.ksoup.nodes.CDataNode
import xyz.thewind.ksoup.nodes.Document
import xyz.thewind.ksoup.nodes.DocumentType
import xyz.thewind.ksoup.nodes.Element
import xyz.thewind.ksoup.nodes.FormElement
import xyz.thewind.ksoup.nodes.Comment
import xyz.thewind.ksoup.nodes.DataNode
import xyz.thewind.ksoup.nodes.TextNode
import xyz.thewind.ksoup.nodes.XmlDeclaration
import xyz.thewind.ksoup.parser.Parser
import xyz.thewind.ksoup.safety.Cleaner
import xyz.thewind.ksoup.safety.Safelist
import xyz.thewind.ksoup.select.NodeFilter
import xyz.thewind.ksoup.select.NodeTraversor
import xyz.thewind.ksoup.select.NodeVisitor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class JsoupCommonTest {
    @Test
    fun parseBuildsDocumentTreeAndTitle() {
        val document = Jsoup.parse(
            """
            <html>
              <head><title>Ksoup</title></head>
              <body><div id="main"><p class="message">Hello <b>world</b></p></div></body>
            </html>
            """.trimIndent()
        )

        assertEquals("Ksoup", document.title())
        assertEquals("Hello world", document.body().text())
        assertEquals("main", document.select("div").first()?.id())
    }

    @Test
    fun htmlParserHandlesRawTextRcDataAndOptionalEndTags() {
        val document = Jsoup.parse(
            """
            <title>A &amp; B</title>
            <script>if (a < b) { window.x = '</div>'; }</script>
            <style>.card > .title { color: red; }</style>
            <textarea>1 &lt; 2</textarea>
            <ul><li>One<li>Two</ul>
            <p>First<div>Block</div><p>Second
            """.trimIndent()
        )

        assertEquals("A & B", document.title())
        assertEquals("if (a < b) { window.x = '</div>'; }", document.selectFirst("script")?.data())
        assertEquals(".card > .title { color: red; }", document.selectFirst("style")?.data())
        assertEquals("1 < 2", document.selectFirst("textarea")?.text())
        assertEquals(listOf("One", "Two"), document.select("ul > li").eachText())
        assertEquals("First", document.select("p").first()?.text())
        assertEquals("Block", document.select("div").first()?.text())
        assertEquals("Second", document.select("p").last()?.text())
    }

    @Test
    fun htmlParserHandlesExtendedRawTextAndPlaintextTags() {
        val document = Jsoup.parse("<div id='host'><xmp><span>raw</span></xmp><plaintext><b>still text</b></div>")
        val host = assertNotNull(document.getElementById("host"))

        assertEquals("<span>raw</span>", host.selectFirst("xmp")?.text())
        assertEquals(0, host.select("xmp span").size)
        assertTrue(host.selectFirst("plaintext")?.text()?.contains("<b>still text</b></div>") == true)
        assertEquals(0, host.select("plaintext b").size)
    }

    @Test
    fun htmlParserStripsLeadingNewlineForPreTextareaAndListing() {
        val document = Jsoup.parse(
            "<pre>\nLine</pre><textarea>\r\nValue</textarea><listing>\nCode</listing>"
        )

        assertEquals("Line", document.selectFirst("pre")?.wholeText())
        assertEquals("Value", document.selectFirst("textarea")?.wholeText())
        assertEquals("Code", document.selectFirst("listing")?.wholeText())
    }

    @Test
    fun htmlParserPlacesFramesetAndFrameOutsideBodyFlow() {
        val document = Jsoup.parse("<frameset cols='50%,50%'><frame src='a.html'><frame src='b.html'></frameset>")

        assertEquals(1, document.select("html > frameset").size)
        assertEquals(listOf("a.html", "b.html"), document.select("frameset > frame").eachAttr("src"))
        assertEquals("", document.body().text())
    }

    @Test
    fun htmlParserInsertsCommonTableContainers() {
        val document = Jsoup.parse("<table><tr><td>One<td>Two</table><table><col><tr><th>Head</table>")

        assertEquals(2, document.select("table").size)
        assertEquals(2, document.select("table").first()?.select("tbody > tr > td")?.size)
        assertEquals("One", document.selectFirst("table tbody tr > td")?.text())
        assertEquals(1, document.select("table").last()?.select("colgroup > col")?.size)
        assertEquals("Head", document.select("table").last()?.selectFirst("tbody > tr > th")?.text())
    }

    @Test
    fun htmlParserRepairsCommonTableSectionOrdering() {
        val document = Jsoup.parse("<table><col><caption>Cap</caption><tr><td>One</td></tr><tfoot><tr><td>Foot</td></tr></tfoot></table>")
        val table = assertNotNull(document.selectFirst("table"))

        assertEquals("Cap", table.selectFirst("caption")?.text())
        assertEquals(1, table.select("colgroup > col").size)
        assertEquals("One", table.selectFirst("tbody > tr > td")?.text())
        assertEquals("Foot", table.selectFirst("tfoot > tr > td")?.text())
    }

    @Test
    fun htmlParserFosterParentsTableMisplacedContent() {
        val document = Jsoup.parse("<div id='wrap'><table><tr><td>One</td></tr>tail<p>after</p></table></div>")
        val wrap = assertNotNull(document.getElementById("wrap"))

        assertEquals("tail", wrap.textNodes().firstOrNull()?.text())
        assertEquals("after", wrap.selectFirst("p")?.text())
        assertEquals("One", wrap.selectFirst("table tbody tr td")?.text())
    }

    @Test
    fun htmlParserFosterParentsFormattingContentAroundTables() {
        val document = Jsoup.parse("<div id='wrap'><table><b>bold</b><i>tail</i><tr><td>One</td></tr></table></div>")
        val wrap = assertNotNull(document.getElementById("wrap"))

        assertEquals("bold", wrap.selectFirst("> b")?.text())
        assertEquals("tail", wrap.selectFirst("> i")?.text())
        assertEquals("One", wrap.selectFirst("table tbody tr td")?.text())
    }

    @Test
    fun htmlParserClosesOptionsAndOptgroupsImplicitly() {
        val document = Jsoup.parse("<select><option>One<option>Two<optgroup label='g'><option>A<option>B</select>")

        assertEquals(listOf("One", "Two", "A", "B"), document.select("select option").eachText())
        assertEquals(1, document.select("select optgroup").size)
        assertEquals(listOf("A", "B"), document.select("select optgroup option").eachText())
    }

    @Test
    fun htmlParserRepairsNestedFormattingAndNestedForms() {
        val document = Jsoup.parse(
            "<div><a id='one'>One<span>!</span><a id='two'>Two</a><button>First<button>Second</button>" +
                "<form id='outer'><input name='a'><form id='inner'><input name='b'></form></form></div>"
        )

        val links = document.select("div > a")
        val buttons = document.select("div > button")
        val forms = document.select("form")

        assertEquals(2, links.size)
        assertEquals("One !", links[0].text())
        assertEquals("Two", links[1].text())
        assertEquals(2, buttons.size)
        assertEquals("First", buttons[0].text())
        assertEquals("Second", buttons[1].text())
        assertEquals(1, forms.size)
        assertEquals("outer", forms[0].id())
        assertEquals(listOf("a", "b"), forms[0].select("input").eachAttr("name"))
        assertNull(document.getElementById("inner"))
    }

    @Test
    fun htmlParserClosesParagraphBeforeTableStructuralTags() {
        val document = Jsoup.parse("<div><p>Before<table><tr><td>Cell</td></tr></table><p>After<tbody><tr><td>Loose</td></tr></tbody></div>")
        val div = assertNotNull(document.selectFirst("div"))

        assertEquals(listOf("Before", "After"), div.select("> p").eachText())
        assertEquals("Cell", div.selectFirst("table tbody tr td")?.text())
        assertEquals("Loose", div.select("tbody tr td").last()?.text())
    }

    @Test
    fun htmlParserCreatesImpliedTableForBareTableStructuralTags() {
        val document = Jsoup.parse("<div id='host'><tr><td>Cell</td></tr></div><section id='next'><caption>Cap</caption></section>")
        val host = assertNotNull(document.getElementById("host"))
        val next = assertNotNull(document.getElementById("next"))

        assertEquals("Cell", host.selectFirst("table tbody tr td")?.text())
        assertEquals(1, host.select("> table").size)
        assertEquals("Cap", next.selectFirst("table > caption")?.text())
    }

    @Test
    fun htmlParserIgnoresMisplacedDuplicateHtmlHeadAndBodyContexts() {
        val document = Jsoup.parse(
            "<html lang='en'><body id='early'><div>Start<head data-x='1'><title>Late</title></head>" +
                "<body class='late'><p>End</p></body></div></body></html>"
        )

        assertEquals("Late", document.title())
        assertEquals("en", document.documentElement().attr("lang"))
        assertEquals("early", document.body().id())
        assertEquals("late", document.body().className())
        assertEquals("Start End", document.body().text())
        assertEquals(1, document.select("head").size)
        assertEquals(1, document.select("body").size)
    }

    @Test
    fun htmlParserCreatesImpliedListContainersForBareItems() {
        val document = Jsoup.parse("<div id='host'><li>One<li>Two<dt>Term<dd>Def</div>")
        val host = assertNotNull(document.getElementById("host"))

        assertEquals(listOf("One", "Two"), host.select("> ul > li").eachText())
        assertEquals(listOf("Term", "Def"), host.select("> dl > *").eachText())
        assertEquals("ul", host.child(0).tagName())
        assertEquals("dl", host.child(1).tagName())
    }

    @Test
    fun htmlParserClosesHeadingTagsImplicitly() {
        val document = Jsoup.parse("<div id='host'><h1>One<h2>Two<h3>Three</div>")
        val host = assertNotNull(document.getElementById("host"))

        assertEquals(listOf("One", "Two", "Three"), host.children().eachText())
        assertEquals(listOf("h1", "h2", "h3"), host.children().map { it.tagName() })
    }

    @Test
    fun htmlParserCreatesImpliedSelectForBareOptions() {
        val document = Jsoup.parse("<div id='host'><option>One<option>Two<optgroup label='g'><option>Three</div>")
        val host = assertNotNull(document.getElementById("host"))

        assertEquals(1, host.select("> select").size)
        assertEquals(listOf("One", "Two", "Three"), host.select("select option").eachText())
        assertEquals("g", host.selectFirst("select > optgroup")?.attr("label"))
        assertEquals(listOf("Three"), host.select("select > optgroup > option").eachText())
    }

    @Test
    fun selectSupportsTagIdClassAttrAndCombinators() {
        val document = Jsoup.parse(
            """
            <section id="feed">
              <article class="card featured" data-kind="news">
                <h2>First</h2>
              </article>
              <article class="card" data-kind="note">
                <h2>Second</h2>
              </article>
            </section>
            """.trimIndent()
        )

        assertEquals(2, document.select("#feed article.card").size)
        assertEquals(1, document.select("section > article.featured[data-kind=news]").size)
        assertEquals("First", document.select("article.featured > h2").text())
    }

    @Test
    fun parseBodyFragmentLoadsIntoBody() {
        val document = Jsoup.parseBodyFragment("<p data-id='7'>Hi &amp; welcome</p>")

        val paragraph = assertNotNull(document.select("p[data-id=7]").first())
        assertEquals("Hi & welcome", paragraph.text())
        assertEquals("<p data-id=\"7\">Hi &amp; welcome</p>", paragraph.outerHtml())
    }

    @Test
    fun elementApiAlignsWithCommonJsoupOperations() {
        val document = Jsoup.parse(
            """
            <div id="root" class="container">
              <p class="lead">Hello <span>world</span></p>
              <p data-kind="note">Second</p>
            </div>
            """.trimIndent(),
            "https://example.com/docs/page.html"
        )

        val root = assertNotNull(document.getElementById("root"))
        val firstParagraph = assertNotNull(root.selectFirst("p.lead"))
        val secondParagraph = assertNotNull(root.selectFirst("p[data-kind=note]"))

        assertTrue(root.hasClass("container"))
        root.addClass("active").removeClass("container").toggleClass("active")
        assertFalse(root.hasClass("container"))
        assertFalse(root.hasClass("active"))

        firstParagraph.before("<p class='before'>Before</p>")
        secondParagraph.after(Element("p").text("After"))
        assertEquals(listOf("Before", "Hello world", "Second", "After"), root.children().eachText())

        firstParagraph.append("<strong>!</strong>")
        assertEquals("Hello world !", firstParagraph.text())

        secondParagraph.attr("data-url", "/guide/start")
        assertEquals("https://example.com/guide/start", secondParagraph.absUrl("data-url"))
        assertEquals(root, secondParagraph.closest("#root"))
    }

    @Test
    fun selectorSupportsGroupsAndAttributeOperators() {
        val document = Jsoup.parse(
            """
            <section>
              <article class="card featured" data-kind="release" data-id="abc-1" data-title="Ksoup release"></article>
              <article class="card" data-kind="note" data-id="abc-2" data-title="Migration notes"></article>
              <aside class="card" data-kind="sidebar" data-id="side-1"></aside>
            </section>
            """.trimIndent()
        )

        assertEquals(3, document.select("article.card, aside.card").size)
        assertEquals(2, document.select("[data-id^=abc-]").size)
        assertEquals(1, document.select("[data-id$=c-1]").size)
        assertEquals(2, document.select("[data-title*=Ksoup], [data-kind!=sidebar]").size)
    }

    @Test
    fun elementsApiSupportsBatchOperations() {
        val document = Jsoup.parse(
            """
            <ul>
              <li class="item" data-id="1">One</li>
              <li class="item" data-id="2">Two</li>
              <li class="item" data-id="3">Three</li>
            </ul>
            """.trimIndent()
        )

        val items = document.select("li.item")
        assertEquals("1", items.attr("data-id"))
        assertEquals(listOf("1", "2", "3"), items.eachAttr("data-id"))
        assertEquals("Two", items.eq(1).text())

        items.addClass("ready").attr("data-state", "ok")
        assertEquals(3, document.select("li.ready[data-state=ok]").size)

        items.eq(2).remove()
        assertEquals(2, document.select("li").size)
    }

    @Test
    fun documentCreateShellAndMutationMethodsWork() {
        val document = Document.createShell("https://example.com")

        document.title("Ksoup")
        document.body()
            .appendElement("main")
            .id("app")
            .appendElement("p")
            .text("Hello")

        assertEquals("Ksoup", document.title())
        assertEquals("Hello", document.selectFirst("main#app > p")?.text())
        assertTrue(document.outerHtml().contains("<title>Ksoup</title>"))
    }

    @Test
    fun documentSupportsCharsetAndNormalise() {
        val document = Jsoup.parse("<html><head><title>A</title></head>pre<body><p>One</p></body><body class='x'><p>Two</p></body><meta charset='utf-8'><div>Tail</div>post</html>")

        document.charset("US-ASCII")
        document.normalise()

        assertEquals("US-ASCII", document.charset())
        assertEquals("x", document.body().className())
        assertEquals(2, document.select("body > p").size)
        assertEquals("utf-8", document.selectFirst("head > meta")?.attr("charset"))
        assertTrue(document.body().text().contains("pre"))
        assertTrue(document.body().text().contains("Tail"))
        assertTrue(document.body().text().contains("post"))
    }

    @Test
    fun documentFormsAndRegexTextLookupApisWork() {
        val document = Jsoup.parse(
            """
            <main>
              <form id="login"><label>User</label><input name="user"></form>
              <form id="search"><label>Find 42</label><input name="q"></form>
              <section><p>Code 123</p><p><span>Ref 456</span></p></section>
            </main>
            """.trimIndent()
        )

        assertEquals(listOf("login", "search"), document.forms().map { it.id() })
        assertIs<FormElement>(document.forms().first())
        assertTrue(document.getElementsMatchingText("\\d+").any { it.tagName() == "main" })
        assertEquals(
            listOf("p", "span"),
            document.getElementsMatchingOwnText("^((Code 123)|(Ref 456))$").map { it.tagName() }
        )
    }

    @Test
    fun attributePrefixRegexAndElementsHasTextApisWork() {
        val document = Jsoup.parse(
            """
            <div id="root">
              <a data-id="post-42" data-kind="news" href="/post/42">Read</a>
              <a data-id="draft" aria-label="Draft"></a>
              <span title="note">Memo</span>
            </div>
            """.trimIndent()
        )
        val regexDocument = Jsoup.parse("<div><p>Alpha 1</p><p><span>Beta 2</span></p></div>")

        assertEquals(2, document.getElementsByAttributeStarting("data-").size)
        assertEquals(listOf("/post/42"), document.getElementsByAttributeValueMatching("href", "/post/\\d+").eachAttr("href"))
        assertEquals(listOf("post-42"), document.getElementsByAttributeValueMatching("data-id", Regex("\\d+$")).eachAttr("data-id"))
        assertTrue(document.select("a, span").hasText())
        assertFalse(document.select("a[aria-label=Draft]").hasText())
        assertTrue(document.select("a, span").`is`("[title], [href]"))
        assertEquals(
            listOf("p", "span"),
            regexDocument.getElementsMatchingOwnText(Regex("^[A-Z][a-z]+ \\d$")).map { it.tagName() }
        )
    }

    @Test
    fun wholeTextOwnTextAndSplitTextApisWork() {
        val document = Jsoup.parse("<div id='root'>A <span>Beta</span>\nGamma<p>Own <b>Inner</b></p></div>")
        val root = assertNotNull(document.getElementById("root"))
        val paragraph = assertNotNull(document.selectFirst("p"))
        val textNode = assertIs<TextNode>(paragraph.childNode(0))

        assertTrue(root.getElementsContainingWholeText("Beta\nGamma").any { it.id() == "root" })
        assertEquals(listOf("p"), root.getElementsContainingWholeOwnText("Own ").map { it.tagName() })
        assertTrue(root.getElementsMatchingWholeText(Regex("A\\s+Beta\\s+Gamma")).any { it.id() == "root" })
        assertEquals(listOf("p"), root.getElementsMatchingWholeOwnText("^Own\\s*$").map { it.tagName() })
        assertTrue(paragraph.hasOwnText())

        val tail = textNode.splitText(2)
        assertEquals("Ow", textNode.getWholeText())
        assertEquals("n ", tail.getWholeText())
        assertEquals(listOf("Ow", "n "), paragraph.textNodes().map { it.getWholeText() })
    }

    @Test
    fun dataSearchAndTraverseApisWork() {
        val document = Jsoup.parse("<div id='root'><script>alpha()</script><!--Beta--><p>Text</p></div>")
        val root = assertNotNull(document.getElementById("root"))
        val visited = mutableListOf<String>()
        val elementNames = mutableListOf<String>()

        assertEquals(listOf("div", "script"), root.getElementsContainingData("alpha").map { it.tagName() })
        assertEquals(listOf("div"), root.getElementsContainingData("beta").map { it.tagName() })

        root.traverse(object : NodeVisitor {
            override fun head(node: xyz.thewind.ksoup.nodes.Node, depth: Int) {
                visited += "${node.nodeName()}:$depth"
            }

            override fun tail(node: xyz.thewind.ksoup.nodes.Node, depth: Int) = Unit
        })

        document.select("script, p").traverse(object : NodeVisitor {
            override fun head(node: xyz.thewind.ksoup.nodes.Node, depth: Int) {
                if (node is Element) {
                    elementNames += node.tagName()
                }
            }

            override fun tail(node: xyz.thewind.ksoup.nodes.Node, depth: Int) = Unit
        })

        assertTrue(visited.any { it == "div:0" })
        assertTrue(visited.any { it == "#data:2" || it == "#comment:1" })
        assertEquals(listOf("script", "p"), elementNames.filter { it == "script" || it == "p" })
    }

    @Test
    fun nodeShallowCloneAndEncodedNodeFactoriesWork() {
        val document = Jsoup.parse("<div id='root'><p class='lead'>Hello <b>world</b></p></div>")
        val paragraph = assertNotNull(document.selectFirst("p"))

        val shallowParagraph = assertIs<Element>(paragraph.shallowClone())
        val shallowDocument = assertIs<Document>(document.shallowClone())
        val encodedText = TextNode.createFromEncoded("Tom &amp; Jerry")
        val encodedData = DataNode.createFromEncoded("1 &lt; 2")

        assertEquals("p", shallowParagraph.tagName())
        assertEquals("lead", shallowParagraph.className())
        assertEquals(0, shallowParagraph.childNodeSize())
        assertEquals(0, shallowDocument.body().childrenSize())
        assertEquals("Tom & Jerry", encodedText.text())
        assertEquals("1 < 2", encodedData.getWholeData())
    }

    @Test
    fun commentXmlDeclarationApisWork() {
        val comment = Comment("?xml version=\"1.0\" encoding=\"UTF-8\"?")
        val declaration = assertNotNull(comment.asXmlDeclaration())
        val nonDeclaration = Comment("plain comment")

        assertTrue(comment.isXmlDeclaration())
        assertEquals("xml", declaration.name())
        assertEquals("1.0", declaration.attr("version"))
        assertEquals("UTF-8", declaration.attr("encoding"))
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>", declaration.outerHtml())
        assertFalse(nonDeclaration.isXmlDeclaration())
        assertNull(nonDeclaration.asXmlDeclaration())
    }

    @Test
    fun nodeLevelTraverseApisWork() {
        val document = Jsoup.parse("<div id='root'><p>A<b>B</b></p><p>C</p></div>")
        val root = assertNotNull(document.getElementById("root"))
        val walked = mutableListOf<String>()
        val fromElements = mutableListOf<String>()

        document.forEachNode { walked += it.nodeName() }
        root.traverse(object : NodeVisitor {
            override fun head(node: xyz.thewind.ksoup.nodes.Node, depth: Int) {
                walked += "${node.nodeName()}@$depth"
            }

            override fun tail(node: xyz.thewind.ksoup.nodes.Node, depth: Int) = Unit
        })
        document.select("p").forEachNode { fromElements += it.nodeName() }

        assertTrue(walked.contains("#root"))
        assertTrue(walked.any { it == "div@0" })
        assertEquals(listOf("p", "#text", "b", "#text", "p", "#text"), fromElements)
    }

    @Test
    fun nodeHasSameValueComparesSerializedNodeValue() {
        val left = Jsoup.parse("<div><p class='a'>Hi</p></div>").selectFirst("p")!!
        val right = Jsoup.parse("<section><p class='a'>Hi</p></section>").selectFirst("p")!!
        val different = Jsoup.parse("<div><p class='b'>Hi</p></div>").selectFirst("p")!!

        assertTrue(left.hasSameValue(right))
        assertFalse(left.hasSameValue(different))
        assertFalse(left.hasSameValue("not-a-node"))
    }

    @Test
    fun nodeFilterCanRemoveSkipAndStopTraversal() {
        val document = Jsoup.parse("<div id='root'><p><span>A</span><em>B</em></p><p class='drop'>C</p><p>D</p></div>")
        val root = assertNotNull(document.getElementById("root"))
        val visited = mutableListOf<String>()

        root.filter(object : NodeFilter {
            override fun head(node: xyz.thewind.ksoup.nodes.Node, depth: Int): NodeFilter.FilterResult {
                visited += node.nodeName()
                return when (node) {
                    is Element -> when {
                        node.hasClass("drop") -> NodeFilter.FilterResult.REMOVE
                        node.tagName() == "p" && node.text() == "A B" -> NodeFilter.FilterResult.SKIP_CHILDREN
                        node.tagName() == "p" && node.text() == "D" -> NodeFilter.FilterResult.STOP
                        else -> NodeFilter.FilterResult.CONTINUE
                    }
                    else -> NodeFilter.FilterResult.CONTINUE
                }
            }

            override fun tail(node: xyz.thewind.ksoup.nodes.Node, depth: Int): NodeFilter.FilterResult =
                NodeFilter.FilterResult.CONTINUE
        })

        assertEquals(listOf("A B", "D"), root.select("> p").eachText())
        assertFalse(visited.contains("span"))
        assertFalse(visited.contains("em"))
        assertEquals(2, root.select("> p").size)
    }

    @Test
    fun baseUriAndDynamicNodeNameApisWork() {
        val document = Jsoup.parse("<section id='root'><a href='docs/start'>Guide</a><div><img src='img.png'></div></section>", "https://a.example/base")
        val root = assertNotNull(document.getElementById("root"))
        val link = assertNotNull(document.selectFirst("a"))
        val image = assertNotNull(document.selectFirst("img"))

        root.setBaseUri("https://b.example/docs/")
        link.tagName("nav")

        assertEquals("https://b.example/docs/", root.baseUri())
        assertEquals("https://b.example/docs/docs/start", link.absUrl("href"))
        assertEquals("https://b.example/docs/img.png", image.absUrl("src"))
        assertEquals("nav", link.nodeName())
        assertEquals("nav", link.tagName())
        assertEquals("section", root.nodeName())
    }

    @Test
    fun langApisWorkWithInheritanceAndSelectorMatching() {
        val document = Jsoup.parse(
            """
            <main lang="en-US">
              <section id="intro"><p id="p1">Hello</p></section>
              <section xml:lang="fr-CA"><p id="p2">Bonjour</p></section>
              <section lang="zh"><p id="p3">Nihao</p></section>
            </main>
            """.trimIndent()
        )

        assertEquals("en-US", document.getElementById("p1")?.lang())
        assertEquals("fr-CA", document.getElementById("p2")?.lang())
        assertEquals("zh", document.getElementById("p3")?.lang())
        assertEquals(setOf("intro", "p1"), document.select(":lang(en)").mapNotNull { it.id().ifEmpty { null } }.toSet())
        assertEquals(setOf("p2"), document.select(":lang(fr)").mapNotNull { it.id().ifEmpty { null } }.toSet())
        assertEquals(setOf("p3"), document.select(":lang(zh)").mapNotNull { it.id().ifEmpty { null } }.toSet())
    }

    @Test
    fun elementsFilterAppliesAcrossRoots() {
        val document = Jsoup.parse("<section><p class='keep'>A</p><p class='drop'>B</p><div><p class='drop'>C</p></div></section>")

        document.select("p").filter(object : NodeFilter {
            override fun head(node: xyz.thewind.ksoup.nodes.Node, depth: Int): NodeFilter.FilterResult =
                if (node is Element && node.hasClass("drop")) NodeFilter.FilterResult.REMOVE else NodeFilter.FilterResult.CONTINUE

            override fun tail(node: xyz.thewind.ksoup.nodes.Node, depth: Int): NodeFilter.FilterResult =
                NodeFilter.FilterResult.CONTINUE
        })

        assertEquals(listOf("A"), document.select("p").eachText())
    }

    @Test
    fun supportsAbsPrefixAndTextDataNodeApis() {
        val document = Jsoup.parse(
            """
            <div id="root">
              before
              <a href="/docs/start">Start</a>
              <script>console.log("x")</script>
              <!--marker-->
            </div>
            """.trimIndent(),
            "https://example.com/guide/page.html"
        )

        val root = assertNotNull(document.getElementById("root"))
        val link = assertNotNull(root.selectFirst("a"))
        val script = assertNotNull(root.selectFirst("script"))
        val comment = assertIs<Comment>(root.childNodes().filterIsInstance<Comment>().first())

        assertEquals("https://example.com/docs/start", link.attr("abs:href"))
        assertTrue(link.hasAttr("abs:href"))
        assertIs<TextNode>(root.textNodes().first())
        assertIs<DataNode>(script.dataNodes().first())
        assertEquals("console.log(\"x\")", script.data())
        assertEquals("marker", comment.getData())
        assertTrue(root.wholeText().contains("before"))
    }

    @Test
    fun supportsContainsIndexAndNodeMutationApis() {
        val document = Jsoup.parse(
            """
            <ul id="list">
              <li>zero</li>
              <li><span>one</span></li>
              <li>two</li>
            </ul>
            """.trimIndent()
        )

        val list = assertNotNull(document.getElementById("list"))
        val middle = list.children()[1]
        val span = assertNotNull(middle.selectFirst("span"))

        assertEquals(2, list.getElementsByIndexLessThan(2).count { it.tagName() == "li" })
        assertEquals(1, list.getElementsByIndexEquals(1).count { it.tagName() == "li" })
        assertTrue(list.getElementsContainingText("one").any { it.tagName() == "span" })
        assertEquals(1, list.getElementsContainingOwnText("two").size)

        span.replaceWith(Element("strong").text("ONE"))
        assertEquals("ONE", middle.selectFirst("strong")?.text())

        middle.unwrap()
        assertEquals(null, middle.parent())
        assertEquals("strong", list.children()[1].tagName())
        assertTrue(list.text().contains("ONE"))
    }

    @Test
    fun parsesCommentsAsNodes() {
        val document = Jsoup.parse("<div><!--hello--><p>text</p></div>")
        val div = assertNotNull(document.selectFirst("div"))
        val comment = assertIs<Comment>(div.childNode(0))
        assertEquals("hello", comment.getData())
        assertEquals("<!--hello-->", comment.outerHtml())
    }

    @Test
    fun parsesDocumentPrologAndCDataNodes() {
        val document = Jsoup.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
            <html><body><script><![CDATA[var answer = 40 + 2;]]></script></body></html>
            """.trimIndent()
        )

        val declaration = assertIs<XmlDeclaration>(document.childNode(0))
        val documentType = assertIs<DocumentType>(document.childNode(1))
        val script = assertNotNull(document.selectFirst("script"))
        val scriptChild = script.childNode(0)

        assertEquals("xml", declaration.name())
        assertEquals("1.0", declaration.attr("version"))
        assertEquals("html", documentType.name())
        assertEquals("PUBLIC", documentType.pubSysKey())
        assertTrue(document.outerHtml().contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(document.outerHtml().contains("<!doctype html PUBLIC"))
        assertTrue(scriptChild is CDataNode || scriptChild is DataNode)
        assertTrue(script.data().contains("var answer = 40 + 2;"))
    }

    @Test
    fun parserSupportsXmlModeWithoutHtmlShell() {
        val document = Jsoup.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <Feed><Item id="7">Value &amp; more</Item></Feed>
            """.trimIndent(),
            "",
            Parser.xmlParser()
        )

        assertIs<XmlDeclaration>(document.childNode(0))
        assertEquals("Feed", document.documentElement().tagName())
        assertEquals("Item", document.documentElement().child(0).tagName())
        assertEquals("Value & more", document.selectFirst("Item")?.text())
        assertFalse(document.outerHtml().contains("<html>"))
    }

    @Test
    fun parserSupportsXmlFragmentAndEntityUnescape() {
        val parser = Parser.xmlParser()
        val document = Jsoup.parseBodyFragment("<Node data-id='1'/>", "", parser)

        assertEquals("Node", document.documentElement().tagName())
        assertEquals("1", document.documentElement().attr("data-id"))
        assertEquals("<tag attr=\"x\">", Parser.unescapeEntities("&lt;tag attr=&quot;x&quot;&gt;", inAttribute = true))
        assertEquals("<tag>", Parser.unescapeEntities("&lt;tag&gt;"))
    }

    @Test
    fun xmlParserIsPreservedForDomMutationAndSerializationSettings() {
        val document = Jsoup.parse("<Feed/>", "", Parser.xmlParser())
        val feed = assertNotNull(document.selectFirst("Feed"))

        feed.append("<Item code='A'/>")
        document.outputSettings()
            .syntax(Document.OutputSettings.Syntax.XML)
            .charset("US-ASCII")
            .escapeMode(Document.OutputSettings.EscapeMode.XHTML)

        assertEquals("<Feed><Item code=\"A\" /></Feed>", document.outerHtml())

        val htmlDocument = Jsoup.parse("<p>Hi&nbsp;中</p>")
        htmlDocument.outputSettings().charset("US-ASCII")
        assertEquals("<p>Hi&nbsp;&#x4e2d;</p>", htmlDocument.body().html())
    }

    @Test
    fun outputSettingsSupportPrettyPrintOutlineAndBooleanHtmlAttributes() {
        val document = Jsoup.parseBodyFragment("<div><p>One</p><p>Two</p></div>")
        val div = assertNotNull(document.body().child(0))

        document.outputSettings().indentAmount(2).maxPaddingWidth(3)
        assertEquals("<div>\n  <p>One</p>\n  <p>Two</p>\n</div>", div.outerHtml())

        document.outputSettings().prettyPrint(false)
        assertEquals("<div><p>One</p><p>Two</p></div>", div.outerHtml())

        document.outputSettings().prettyPrint(true).outline(true).indentAmount(2)
        assertTrue(div.outerHtml().contains("\n  <p>\n"))

        val input = Element("input")
            .attr("checked", "checked")
            .attr("disabled", "")
            .attr("value", "ok")
        assertEquals("<input checked disabled value=\"ok\">", input.outerHtml())
    }

    @Test
    fun cleanerAndSafelistSupportCommonSanitizationFlow() {
        val cleaned = Jsoup.clean(
            """
            <p>Hello <a href="/docs" onclick="alert(1)">docs</a> <script>alert(1)</script></p>
            """.trimIndent(),
            "https://example.com",
            Safelist.basic()
        )

        assertEquals("<p>Hello <a href=\"https://example.com/docs\" rel=\"nofollow\">docs</a></p>", cleaned)
        assertFalse(Jsoup.isValid("<a href=\"javascript:alert(1)\">bad</a>", Safelist.basic()))
        assertTrue(Jsoup.isValid("<p><a href=\"https://example.com\">ok</a></p>", Safelist.relaxed()))
    }

    @Test
    fun cleanerSupportsOutputSettingsOverrides() {
        val outputSettings = Document.OutputSettings()
            .prettyPrint(false)

        val cleaned = Jsoup.clean(
            "<p>One</p><p>Two</p>",
            "",
            Safelist.relaxed(),
            outputSettings
        )

        assertEquals("<p>One</p><p>Two</p>", cleaned)
    }

    @Test
    fun safelistSupportsRelativeLinksAndNoneMode() {
        val preserveRelative = Safelist.basic().preserveRelativeLinks(true)
        val cleanedRelative = Jsoup.clean("<a href=\"/guide\">guide</a>", "https://example.com/docs", preserveRelative)
        val cleanedText = Jsoup.clean("<p>Hello <b>world</b></p>", Safelist.none())

        assertEquals("<a href=\"/guide\" rel=\"nofollow\">guide</a>", cleanedRelative)
        assertEquals("Hello world", cleanedText)
    }

    @Test
    fun cleanerSupportsDirectDocumentCleaning() {
        val dirty = Jsoup.parseBodyFragment("<p><img src=\"http://example.com/a.png\" onerror=\"x\"></p>")
        val cleaned = Cleaner(Safelist.basicWithImages()).clean(dirty)

        assertEquals("<p><img src=\"http://example.com/a.png\"></p>", cleaned.body().html())
    }

    @Test
    fun documentProvidesDefaultConnectionAndElementsCloneDeepCopies() {
        val document = Jsoup.parse("<div><p class='lead'>Hello</p><p>World</p></div>", "https://example.com/base")
        val connection = document.connection()
        val cloned = document.select("p").clone()

        cloned.first()?.text("Changed")

        assertEquals("https://example.com/base", connection.request().url())
        assertEquals(listOf("Hello", "World"), document.select("p").eachText())
        assertEquals(listOf("Changed", "World"), cloned.eachText())
        assertEquals("lead", cloned.first()?.className())
    }

    @Test
    fun connectBuildsRequestAndParsesHtmlResponse() {
        withFakeTransport(
            responder = { captured ->
                ResponseData.fromTransport(
                    TransportResponse(
                        url = "https://example.com/search?q=ksoup",
                        statusCode = 200,
                        statusMessage = "OK",
                        contentType = "text/html; charset=UTF-8",
                        headers = mapOf("Content-Type" to "text/html; charset=UTF-8"),
                        cookies = emptyMap(),
                        body = "<html><body><p>done</p></body></html>",
                        bodyBytes = "<html><body><p>done</p></body></html>".encodeToByteArray()
                    ),
                    captured
                )
            },
            assertions = { capturedRequests ->
            val document = Jsoup.connect("https://example.com/search")
                .userAgent("KsoupTest/1.0")
                .referrer("https://example.com")
                .data("q", "ksoup")
                .get()

            val request = capturedRequests.single()
            assertEquals(Connection.Method.GET, request.method())
            assertEquals("KsoupTest/1.0", request.header("User-Agent"))
            assertEquals("https://example.com", request.header("Referer"))
            assertEquals("q", request.data().single().key())
            assertEquals("ksoup", request.data().single().value())
            assertEquals("done", document.selectFirst("p")?.text())
            }
        )
    }

    @Test
    fun newSessionCarriesCookiesAcrossRequests() {
        var call = 0
        withFakeTransport(
            responder = { captured ->
            call += 1
            if (call == 1) {
                ResponseData.fromTransport(
                    TransportResponse(
                        url = "https://example.com/login",
                        statusCode = 200,
                        statusMessage = "OK",
                        contentType = "text/html",
                        headers = emptyMap(),
                        cookies = mapOf("sid" to "abc"),
                        body = "<html><body>login</body></html>",
                        bodyBytes = "<html><body>login</body></html>".encodeToByteArray()
                    ),
                    captured
                )
            } else {
                ResponseData.fromTransport(
                    TransportResponse(
                        url = "https://example.com/home",
                        statusCode = 200,
                        statusMessage = "OK",
                        contentType = "text/html",
                        headers = emptyMap(),
                        cookies = emptyMap(),
                        body = "<html><body>home</body></html>",
                        bodyBytes = "<html><body>home</body></html>".encodeToByteArray()
                    ),
                    captured
                )
            }
            },
            assertions = { capturedRequests ->
            val session = Jsoup.newSession()
            session.url("https://example.com/login").execute()
            session.newRequest("https://example.com/home").execute()

            assertEquals(2, capturedRequests.size)
            assertEquals("abc", capturedRequests[1].cookie("sid"))
            }
        )
    }

    @Test
    fun connectThrowsForHttpErrorsAndUnsupportedMimeUnlessIgnored() {
        withFakeTransport(
            responder = { captured ->
                ResponseData.fromTransport(
                    TransportResponse(
                        url = captured.url(),
                        statusCode = 500,
                        statusMessage = "Server Error",
                        contentType = "text/html",
                        headers = emptyMap(),
                        cookies = emptyMap(),
                        body = "<html><body>error</body></html>",
                        bodyBytes = "<html><body>error</body></html>".encodeToByteArray()
                    ),
                    captured
                )
            },
            assertions = {
            assertFailsWith<HttpStatusException> {
                Jsoup.connect("https://example.com/error").execute()
            }
            }
        )

        withFakeTransport(
            responder = { captured ->
                ResponseData.fromTransport(
                    TransportResponse(
                        url = captured.url(),
                        statusCode = 200,
                        statusMessage = "OK",
                        contentType = "application/json",
                        headers = mapOf("Content-Type" to "application/json"),
                        cookies = emptyMap(),
                        body = """{"ok":true}""",
                        bodyBytes = """{"ok":true}""".encodeToByteArray()
                    ),
                    captured
                )
            },
            assertions = {
            assertFailsWith<UnsupportedMimeTypeException> {
                Jsoup.connect("https://example.com/json").get()
            }

            val document = Jsoup.connect("https://example.com/json")
                .ignoreContentType(true)
                .get()
            assertTrue(document.body().text().contains("{\"ok\":true}"))
            }
        )
    }

    @Test
    fun connectSupportsPostBodiesAndFormData() {
        withFakeTransport(
            responder = { captured ->
                ResponseData.fromTransport(
                    TransportResponse(
                        url = captured.url(),
                        statusCode = 200,
                        statusMessage = "OK",
                        contentType = "text/html",
                        headers = emptyMap(),
                        cookies = emptyMap(),
                        body = "<html><body>posted</body></html>",
                        bodyBytes = "<html><body>posted</body></html>".encodeToByteArray()
                    ),
                    captured
                )
            },
            assertions = { capturedRequests ->
            Jsoup.connect("https://example.com/form")
                .method(Connection.Method.POST)
                .data("a", "1")
                .data("b", "2")
                .execute()

            Jsoup.connect("https://example.com/raw")
                .method(Connection.Method.POST)
                .requestBody("""{"x":1}""")
                .header("Content-Type", "application/json")
                .execute()

            assertEquals(Connection.Method.POST, capturedRequests[0].method())
            assertEquals(2, capturedRequests[0].data().size)
            assertEquals("""{"x":1}""", capturedRequests[1].requestBody())
            }
        )
    }

    @Test
    fun connectSupportsPostDataCharsetFluentApi() {
        withFakeTransport(
            responder = { captured ->
                ResponseData.fromTransport(
                    TransportResponse(
                        url = captured.url(),
                        statusCode = 200,
                        statusMessage = "OK",
                        contentType = "text/html",
                        headers = emptyMap(),
                        cookies = emptyMap(),
                        body = "<html><body>ok</body></html>",
                        bodyBytes = "<html><body>ok</body></html>".encodeToByteArray()
                    ),
                    captured
                )
            },
            assertions = { capturedRequests ->
                Jsoup.connect("https://example.com/form")
                    .method(Connection.Method.POST)
                    .data("q", "你好")
                    .postDataCharset("GBK")
                    .execute()

                assertEquals("GBK", capturedRequests.single().postDataCharset())
                assertEquals("你好", capturedRequests.single().data("q")?.value())
            }
        )
    }

    @Test
    fun connectSupportsCustomParserForXmlResponses() {
        withFakeTransport(
            responder = { captured ->
                ResponseData.fromTransport(
                    TransportResponse(
                        url = captured.url(),
                        statusCode = 200,
                        statusMessage = "OK",
                        contentType = "application/xml; charset=UTF-8",
                        headers = mapOf("Content-Type" to "application/xml; charset=UTF-8"),
                        cookies = emptyMap(),
                        body = "<?xml version=\"1.0\"?><feed><item>ok</item></feed>",
                        bodyBytes = "<?xml version=\"1.0\"?><feed><item>ok</item></feed>".encodeToByteArray()
                    ),
                    captured
                )
            },
            assertions = {
                val document = Jsoup.connect("https://example.com/feed.xml")
                    .parser(Parser.xmlParser())
                    .get()

                assertEquals("feed", document.documentElement().tagName())
                assertFalse(document.outerHtml().contains("<html>"))
                assertEquals("ok", document.selectFirst("item")?.text())
            }
        )
    }

    @Test
    fun parsedResponseDocumentsRetainConnectionContext() {
        withFakeTransport(
            responder = { captured ->
                ResponseData.fromTransport(
                    TransportResponse(
                        url = "https://example.com/list?page=2",
                        statusCode = 200,
                        statusMessage = "OK",
                        contentType = "text/html; charset=UTF-8",
                        headers = mapOf("Content-Type" to "text/html; charset=UTF-8"),
                        cookies = mapOf("sid" to "xyz"),
                        body = "<html><body><p>done</p></body></html>",
                        bodyBytes = "<html><body><p>done</p></body></html>".encodeToByteArray()
                    ),
                    captured
                )
            },
            assertions = {
                val document = Jsoup.connect("https://example.com/list")
                    .cookie("client", "abc")
                    .data("page", "2")
                    .get()

                val connection = document.connection()
                assertEquals("https://example.com/list", connection.request().url())
                assertEquals("abc", connection.request().cookie("client"))
                assertEquals("xyz", connection.response()?.cookie("sid"))
                assertEquals("done", document.selectFirst("p")?.text())
            }
        )
    }

    @Test
    fun formElementCollectsDataAndSubmitsThroughConnection() {
        withFakeTransport(
            responder = { captured ->
                ResponseData.fromTransport(
                    TransportResponse(
                        url = captured.url(),
                        statusCode = 200,
                        statusMessage = "OK",
                        contentType = "text/html",
                        headers = emptyMap(),
                        cookies = emptyMap(),
                        body = "<html><body>ok</body></html>",
                        bodyBytes = "<html><body>ok</body></html>".encodeToByteArray()
                    ),
                    captured
                )
            },
            assertions = { capturedRequests ->
                val document = Jsoup.parse(
                    """
                    <form action="/submit" method="post">
                      <input name="user" value="neo">
                      <input type="checkbox" name="active" checked>
                      <textarea name="bio">hi</textarea>
                      <select name="role" multiple>
                        <option value="user" selected>User</option>
                        <option value="admin" selected>Admin</option>
                      </select>
                      <input type="submit" name="send" value="Send">
                    </form>
                    """.trimIndent(),
                    "https://example.com/account"
                )
                val form = assertIs<FormElement>(document.forms().single())

                assertEquals(listOf("user", "active", "bio", "role", "role"), form.formData().map { it.key() })
                assertEquals(listOf("neo", "on", "hi", "user", "admin"), form.formData().map { it.value() })

                form.submit().execute()

                val request = capturedRequests.single()
                assertEquals("https://example.com/submit", request.url())
                assertEquals(Connection.Method.POST, request.method())
                assertEquals(listOf("user", "active", "bio", "role", "role"), request.data().map { it.key() })
            }
        )
    }

    @Test
    fun formElementSkipsControlsInsideDisabledFieldset() {
        val document = Jsoup.parse(
            """
            <form>
              <input name="active" value="1">
              <fieldset disabled>
                <input name="skipped" value="2">
                <textarea name="skippedArea">3</textarea>
              </fieldset>
            </form>
            """.trimIndent()
        )
        val form = assertIs<FormElement>(document.forms().single())

        assertEquals(listOf("active"), form.formData().map { it.key() })
        assertEquals(listOf("1"), form.formData().map { it.value() })
    }

    @Test
    fun formElementRespectsFirstLegendExceptionInsideDisabledFieldset() {
        val document = Jsoup.parse(
            """
            <form>
              <fieldset disabled>
                <legend><input name="legendInput" value="ok"></legend>
                <input name="blocked" value="no">
              </fieldset>
            </form>
            """.trimIndent()
        )
        val form = assertIs<FormElement>(document.forms().single())

        assertEquals(listOf("legendInput"), form.formData().map { it.key() })
        assertEquals(listOf("ok"), form.formData().map { it.value() })
    }

    @Test
    fun createElementAndClonePreserveFormElementType() {
        val document = Jsoup.parse("<form id='login'><input name='user'></form>")
        val created = document.createElement("form")
        val parsed = assertIs<FormElement>(document.forms().single())
        val cloned = assertIs<FormElement>(parsed.clone())

        assertIs<FormElement>(created)
        assertEquals("login", cloned.id())
        assertEquals(1, cloned.elements().size)
    }

    @Test
    fun formConvenienceApisWorkTogether() {
        val document = Jsoup.parse("<section><form id='login'><input name='user'></form></section>")
        val form = document.expectForm("form#login")
        val field = assertNotNull(form.selectFirst("input"))
        val extra = document.createElement("input").attr("name", "token")

        form.addElement(extra)

        assertEquals(listOf("login"), document.select("form").forms().map { it.id() })
        assertEquals("login", field.form()?.id())
        assertEquals("login", extra.form()?.id())
        assertEquals(listOf("user", "token"), form.elements().eachAttr("name"))
        assertEquals(listOf("user", "token"), form.formData().map { it.key() })
    }

    @Test
    fun selectorSupportsFormStatePseudos() {
        val document = Jsoup.parse(
            """
            <form>
              <input type="checkbox" name="a" checked>
              <input type="radio" name="b">
              <input type="text" name="c" disabled>
              <select name="role">
                <option value="user">User</option>
                <option value="admin" selected>Admin</option>
              </select>
            </form>
            """.trimIndent()
        )

        assertEquals(2, document.select(":checked").size)
        assertEquals(listOf("admin"), document.select(":selected").eachAttr("value"))
        assertEquals(listOf("c"), document.select(":disabled").eachAttr("name"))
        assertEquals(setOf("a", "b", "role"), document.select(":enabled").eachAttr("name").toSet())
    }

    @Test
    fun selectorSupportsFormTypePseudos() {
        val document = Jsoup.parse(
            """
            <form>
              <input name="plain">
              <input type="text" name="textual">
              <input type="radio" name="pick">
              <input type="checkbox" name="flag">
              <input type="file" name="upload">
              <input type="password" name="secret">
              <input type="image" name="imgBtn">
              <input type="submit" name="send">
              <input type="reset" name="wipe">
              <button name="btn">Default Submit</button>
              <button type="reset" name="btnReset">Reset</button>
              <textarea name="bio"></textarea>
              <select name="role"></select>
            </form>
            """.trimIndent()
        )

        assertEquals(13, document.select(":input").size)
        assertEquals(setOf("plain", "textual"), document.select(":text").eachAttr("name").toSet())
        assertEquals(listOf("pick"), document.select(":radio").eachAttr("name"))
        assertEquals(listOf("flag"), document.select(":checkbox").eachAttr("name"))
        assertEquals(listOf("upload"), document.select(":file").eachAttr("name"))
        assertEquals(listOf("secret"), document.select(":password").eachAttr("name"))
        assertEquals(listOf("imgBtn"), document.select(":image").eachAttr("name"))
        assertEquals(setOf("send", "btn"), document.select(":submit").eachAttr("name").toSet())
        assertEquals(setOf("wipe", "btnReset"), document.select(":reset").eachAttr("name").toSet())
        assertEquals(setOf("send", "wipe", "btn", "btnReset"), document.select(":button").eachAttr("name").toSet())
    }

    @Test
    fun selectorSupportsRequiredAndReadStatePseudos() {
        val document = Jsoup.parse(
            """
            <form>
              <input name="requiredText" required>
              <input name="readonlyText" readonly>
              <input name="disabledText" disabled readonly>
              <textarea name="requiredArea" required></textarea>
              <textarea name="editableArea"></textarea>
              <select name="requiredSelect" required></select>
              <input type="checkbox" name="flag" required>
              <input type="hidden" name="hiddenField" readonly>
            </form>
            """.trimIndent()
        )

        assertEquals(setOf("requiredText", "requiredArea", "requiredSelect", "flag"), document.select(":required").eachAttr("name").toSet())
        assertEquals(setOf("readonlyText", "disabledText"), document.select(":readOnly").eachAttr("name").toSet())
        assertEquals(setOf("requiredText", "requiredArea", "editableArea"), document.select(":readWrite").eachAttr("name").toSet())
        assertTrue(document.select(":optional").eachAttr("name").containsAll(listOf("readonlyText", "editableArea", "hiddenField")))
    }

    @Test
    fun selectorSupportsHeaderParentAndFieldsetDisabledSemantics() {
        val document = Jsoup.parse(
            """
            <section>
              <h1>Main</h1>
              <div id="with-child"><span>Kid</span></div>
              <div id="with-text"> text </div>
              <div id="empty"><!--note--></div>
              <form>
                <fieldset disabled>
                  <legend><input name="legendInput"></legend>
                  <input name="nestedInput">
                </fieldset>
              </form>
            </section>
            """.trimIndent()
        )

        assertEquals(listOf("h1"), document.select(":header").map { it.tagName() })
        assertEquals(setOf("with-child", "with-text"), document.select("div:parent").eachAttr("id").toSet())
        assertEquals(listOf("nestedInput"), document.select(":disabled").eachAttr("name"))
        assertFalse(document.select(":enabled").eachAttr("name").contains("nestedInput"))
        assertTrue(document.select(":enabled").eachAttr("name").contains("legendInput"))
    }

    @Test
    fun selectorSupportsCommonPseudos() {
        val document = Jsoup.parse(
            """
            <section>
              <article class="card">Alpha beta</article>
              <article class="card"><span>Child text</span></article>
              <article class="card" data-kind="json"><script>{"ok":true}</script></article>
              <article class="card empty"><!--only comment--></article>
            </section>
            """.trimIndent()
        )

        assertEquals(1, document.select("article:containsOwn(Alpha)").size)
        assertEquals(1, document.select("article:contains(Child text)").size)
        assertEquals(1, document.select("article:containsData({\"ok\":true})").size)
        assertEquals(1, document.select("article:eq(1)").size)
        assertEquals(2, document.select("article:gt(1)").size)
        assertEquals(1, document.select("article:lt(1)").size)
        assertEquals(1, document.select("article:has(span)").size)
        assertEquals(3, document.select("article:not(.empty)").size)
        assertEquals(1, document.select("article:empty").size)
    }

    @Test
    fun selectorSupportsWholeTextRegexAndAttributeRegex() {
        val document = Jsoup.parse(
            """
            <section>
              <article data-id="post-7">Alpha<span>Beta</span></article>
              <article data-id="note-2">Gamma</article>
            </section>
            """.trimIndent()
        )

        assertEquals(1, document.select("article[data-id~=post-\\d]").size)
        assertEquals(1, document.select("article:containsWholeText(AlphaBeta)").size)
        assertEquals(1, document.select("article:matchesWholeText(AlphaBeta)").size)
        assertEquals(1, document.select("article:matchesWholeOwnText(^Gamma$)").size)
        assertEquals(2, document.select("article:hasText").size)
        assertEquals(1, document.select("article:first").size)
        assertEquals(1, document.select("article:last").size)
        assertEquals(1, document.select("article:even").size)
        assertEquals(1, document.select("article:odd").size)
    }

    @Test
    fun selectorSupportsSiblingCombinatorsAndStructuralPseudos() {
        val document = Jsoup.parse(
            """
            <section id="layout">
              <p id="p1" class="lead">Alpha</p>
              <p id="p2"><span>Beta</span></p>
              <div id="box"><em id="only">Only</em></div>
              <p id="p3">Gamma</p>
              <span id="tail">Tail</span>
            </section>
            """.trimIndent()
        )

        assertEquals("p2", document.select("p.lead + p").first()?.id())
        assertEquals("tail", document.select("p.lead ~ span").first()?.id())
        assertEquals("html", document.select(":root").first()?.tagName())
        assertEquals("p1", document.select("section > :first-child").first()?.id())
        assertEquals("tail", document.select("section > :last-child").first()?.id())
        assertEquals("p1", document.select("section > p:first-of-type").first()?.id())
        assertEquals("p3", document.select("section > p:last-of-type").first()?.id())
        assertEquals(1, document.select("div > em:only-child").size)
        assertEquals(1, document.select("div > em:only-of-type").size)
        assertEquals("p2", document.select("section > p:nth-child(2)").first()?.id())
        assertEquals("p2", document.select("section > p:nth-of-type(2)").first()?.id())
        assertEquals("p3", document.select("section > p:nth-last-of-type(1)").first()?.id())
        assertEquals("p3", document.select("section > p:nth-last-child(2)").first()?.id())
        assertEquals(1, document.select("p:matches(^Alpha$)").size)
        assertEquals(1, document.select("p:matchesOwn(^Gamma$)").size)
    }

    @Test
    fun selectorMatchesSupportsFullQueriesAndNotPseudo() {
        val document = Jsoup.parse(
            """
            <section id="host">
              <article class="card"><p id="a">Alpha</p></article>
              <article class="card empty"><p id="b">Beta</p></article>
            </section>
            """.trimIndent()
        )

        val alpha = assertNotNull(document.getElementById("a"))
        val beta = assertNotNull(document.getElementById("b"))

        assertTrue(alpha.`is`("section > article.card > p#a"))
        assertTrue(beta.`is`("article.empty > p"))
        assertEquals(1, document.select("p:not(article.empty > p)").size)
        assertEquals("a", document.select("p:not(article.empty > p)").first()?.id())
        assertEquals("a", document.select("p:is(article.card > p)").first()?.id())
        assertEquals(2, document.select("p:where(article > p)").size)
    }

    @Test
    fun elementsSupportParentsAndNotQueries() {
        val document = Jsoup.parse(
            """
            <main id="app">
              <section class="group">
                <article class="card featured"><p id="a">Alpha</p></article>
                <article class="card"><p id="b">Beta</p></article>
              </section>
            </main>
            """.trimIndent()
        )

        val paragraphs = document.select("article > p")

        assertEquals(listOf("a"), paragraphs.not("article.card > p:not(#a)").eachAttr("id"))
        assertEquals(
            listOf("article", "section", "main", "body", "html"),
            paragraphs.parents().map { it.tagName() }.distinct()
        )
    }

    @Test
    fun elementsSupportBatchDomMutationApis() {
        val document = Jsoup.parse("<section><article class='card'><p>A</p></article><article class='card'><p>B</p></article></section>")
        val articles = document.select("article.card")

        articles.before("<hr>")
        articles.after("<footer>tail</footer>")
        articles.append("<span class='x'>!</span>")
        articles.prepend("<header>top</header>")
        articles.tagName("section")
        articles.select("p").text("Changed")

        assertEquals(2, document.select("hr + section.card").size)
        assertEquals(2, document.select("section.card > header").size)
        assertEquals(listOf("Changed", "Changed"), document.select("section.card > p").eachText())
        assertEquals(2, document.select("section.card > span.x").size)
        assertEquals(2, document.select("section.card + footer").size)

        document.select("section.card > header").unwrap()
        assertEquals(0, document.select("section.card > header").size)
        assertEquals(2, document.select("section.card").size)
    }

    @Test
    fun elementAndElementsSupportValApi() {
        val document = Jsoup.parse(
            """
            <form>
              <input id="name" value="Alpha">
              <textarea id="bio">Line one</textarea>
              <select id="kind">
                <option value="a">A</option>
                <option selected value="b">B</option>
              </select>
            </form>
            """.trimIndent()
        )

        val input = assertNotNull(document.getElementById("name"))
        val textarea = assertNotNull(document.getElementById("bio"))
        val select = assertNotNull(document.getElementById("kind"))

        assertEquals("Alpha", input.`val`())
        assertEquals("Line one", textarea.`val`())
        assertEquals("b", select.`val`())

        input.`val`("Beta")
        textarea.`val`("Line two")
        select.`val`("a")

        assertEquals("Beta", input.attr("value"))
        assertEquals("Line two", textarea.text())
        assertEquals("a", select.`val`())
        assertEquals("a", document.select("select option[selected]").`val`())

        document.select("input, textarea").`val`("Shared")
        assertEquals("Shared", document.select("input").`val`())
        assertEquals("Shared", document.select("textarea").`val`())
    }

    @Test
    fun elementSupportsCssSelectorGeneration() {
        val document = Jsoup.parse(
            """
            <main id="app">
              <section>
                <article><p>One</p></article>
                <article><p id="target">Two</p></article>
              </section>
            </main>
            """.trimIndent()
        )

        val target = assertNotNull(document.getElementById("target"))
        val firstParagraph = assertNotNull(document.select("article > p").first())

        assertEquals("p#target", target.cssSelector())
        assertEquals("main#app > section > article:nth-of-type(1) > p", firstParagraph.cssSelector())
        assertEquals("target", document.selectFirst(target.cssSelector())?.id())
        assertEquals("One", document.selectFirst(firstParagraph.cssSelector())?.text())
    }

    @Test
    fun nodeAndElementsSupportWrapAndNormalName() {
        val document = Jsoup.parse("<div id='host'><p class='a'>One</p><p class='b'>Two</p></div>")
        val host = assertNotNull(document.getElementById("host"))
        val first = assertNotNull(host.selectFirst("p.a"))

        assertEquals("p", first.normalName())

        first.wrap("<section class='outer'><div class='inner'></div></section>")
        document.select("p.b").wrap("<article class='card'></article>")

        assertEquals("section", host.child(0).tagName())
        assertEquals("p", host.selectFirst("section.outer > div.inner > p")?.normalName())
        assertEquals("One", host.selectFirst("section.outer > div.inner > p")?.text())
        assertEquals("article", host.selectFirst("article.card")?.tagName())
        assertEquals("Two", host.selectFirst("article.card > p")?.text())
    }

    @Test
    fun elementsSupportSiblingTraversalApis() {
        val document = Jsoup.parse(
            """
            <section>
              <p id="a">A</p>
              <p id="b">B</p>
              <p id="c">C</p>
              <span id="d">D</span>
            </section>
            """.trimIndent()
        )

        val middle = document.select("#b, #c")

        assertEquals(listOf("c", "d"), middle.next().eachAttr("id"))
        assertEquals(listOf("a", "b"), middle.prev().eachAttr("id"))
        assertEquals(listOf("c", "d"), document.select("#b").nextAll().eachAttr("id"))
        assertEquals(listOf("a", "b"), document.select("#c").prevAll().eachAttr("id"))
        assertEquals(listOf("a", "c", "d", "b"), middle.siblings().eachAttr("id"))
    }

    @Test
    fun attributesAndDatasetWorkLikeACompatibilityLayer() {
        val element = Element("div")
            .attr("id", "root")
            .attr("data-id", "7")
            .attr("data-kind", "demo")

        val attributes: Attributes = element.attributes()
        assertEquals(3, attributes.size())
        assertTrue(attributes.hasKey("ID"))
        assertEquals("root", attributes["id"])
        assertEquals(mapOf("id" to "7", "kind" to "demo"), attributes.dataset())
        assertEquals(mapOf("id" to "7", "kind" to "demo"), element.dataset())

        val dataset = attributes.dataset()
        dataset["state"] = "ready"
        dataset["kind"] = "sample"
        dataset.remove("id")

        assertEquals("", attributes["data-id"])
        assertEquals("sample", element.attr("data-kind"))
        assertEquals("ready", element.attr("data-state"))
        assertEquals(mapOf("kind" to "sample", "state" to "ready"), element.dataset())
    }

    @Test
    fun connectSupportsBulkHeadersCookiesAndMaxBodySize() {
        withFakeTransport(
            responder = { captured ->
                ResponseData.fromTransport(
                    TransportResponse(
                        url = captured.url(),
                        statusCode = 200,
                        statusMessage = "OK",
                        contentType = "text/html",
                        headers = emptyMap(),
                        cookies = emptyMap(),
                        body = "abcdefghijklmnopqrstuvwxyz".take(captured.maxBodySize()),
                        bodyBytes = "abcdefghijklmnopqrstuvwxyz".encodeToByteArray().copyOf(captured.maxBodySize())
                    ),
                    captured
                )
            },
            assertions = { capturedRequests ->
                val response = Jsoup.connect("https://example.com/alpha")
                    .headers(mapOf("X-One" to "1", "X-Two" to "2"))
                    .cookies(mapOf("a" to "b"))
                    .data(listOf(Connection.KeyVal.create("q", "v")))
                    .maxBodySize(5)
                    .execute()

                assertEquals("abcde", response.body())
                assertEquals(5, response.bodyAsBytes().size)
                assertEquals("1", capturedRequests.single().header("X-One"))
                assertEquals("b", capturedRequests.single().cookie("a"))
                assertEquals("v", capturedRequests.single().data("q")?.value())
            }
        )
    }

    @Test
    fun connectSupportsProxyAuthAndMultipartState() {
        withFakeTransport(
            responder = { captured ->
                ResponseData.fromTransport(
                    TransportResponse(
                        url = captured.url(),
                        statusCode = 200,
                        statusMessage = "OK",
                        contentType = "text/html",
                        headers = emptyMap(),
                        cookies = emptyMap(),
                        body = "<html><body>ok</body></html>",
                        bodyBytes = "<html><body>ok</body></html>".encodeToByteArray()
                    ),
                    captured
                )
            },
            assertions = { capturedRequests ->
                Jsoup.connect("https://example.com/upload")
                    .proxy("127.0.0.1", 8888)
                    .auth(
                        Connection.RequestAuthenticator { request ->
                            if (request.isProxy) {
                                Connection.Credentials("proxy-user", "proxy-pass")
                            } else {
                                Connection.Credentials("app-user", "app-pass")
                            }
                        }
                    )
                    .method(Connection.Method.POST)
                    .data("file", "a.txt", "payload".encodeToByteArray(), "text/plain")
                    .execute()

                val request = capturedRequests.single()
                assertEquals("127.0.0.1", request.proxy()?.host)
                assertEquals(8888, request.proxy()?.port)
                assertEquals("a.txt", request.data().single().fileName())
                assertEquals("text/plain", request.data().single().contentType())
                assertEquals("payload", request.data().single().input()?.decodeToString())
                assertEquals("app-user", request.auth()?.authenticate(Connection.AuthRequest(request.url(), false))?.username)
                assertEquals("proxy-user", request.auth()?.authenticate(Connection.AuthRequest(request.url(), true))?.username)
            }
        )
    }

    @Test
    fun connectPreservesMultipartKeyValWhenUsingCollectionData() {
        withFakeTransport(
            responder = { captured ->
                ResponseData.fromTransport(
                    TransportResponse(
                        url = captured.url(),
                        statusCode = 200,
                        statusMessage = "OK",
                        contentType = "text/html",
                        headers = emptyMap(),
                        cookies = emptyMap(),
                        body = "<html><body>ok</body></html>",
                        bodyBytes = "<html><body>ok</body></html>".encodeToByteArray()
                    ),
                    captured
                )
            },
            assertions = { capturedRequests ->
                Jsoup.connect("https://example.com/upload")
                    .method(Connection.Method.POST)
                    .data(
                        listOf(
                            Connection.KeyVal.create("file", "demo.txt", "payload".encodeToByteArray(), "text/plain")
                        )
                    )
                    .execute()

                val keyVal = capturedRequests.single().data().single()
                assertEquals("demo.txt", keyVal.fileName())
                assertEquals("text/plain", keyVal.contentType())
                assertEquals("payload", keyVal.input()?.decodeToString())
            }
        )
    }

    @Test
    fun connectTracksTlsValidationPreference() {
        withFakeTransport(
            responder = { captured ->
                ResponseData.fromTransport(
                    TransportResponse(
                        url = captured.url(),
                        statusCode = 200,
                        statusMessage = "OK",
                        contentType = "text/html",
                        headers = emptyMap(),
                        cookies = emptyMap(),
                        body = "<html><body>ok</body></html>",
                        bodyBytes = "<html><body>ok</body></html>".encodeToByteArray()
                    ),
                    captured
                )
            },
            assertions = { capturedRequests ->
                Jsoup.connect("https://example.com/tls")
                    .validateTLSCertificates(false)
                    .execute()

                assertFalse(capturedRequests.single().validateTLSCertificates())
            }
        )
    }

    @Test
    fun supportsCloneAndNodeTraversal() {
        val document = Jsoup.parse("<div id='root'><p>One <b>Two</b></p><!--tail--></div>")
        val root = assertNotNull(document.getElementById("root"))
        val visited = mutableListOf<String>()

        NodeTraversor.traverse(
            object : NodeVisitor {
                override fun head(node: xyz.thewind.ksoup.nodes.Node, depth: Int) {
                    visited.add("head:$depth:${node.nodeName()}")
                }

                override fun tail(node: xyz.thewind.ksoup.nodes.Node, depth: Int) {
                    visited.add("tail:$depth:${node.nodeName()}")
                }
            },
            root
        )

        val cloned = assertIs<Element>(root.clone())
        cloned.selectFirst("p")?.text("Changed")

        assertEquals("One Two", root.text())
        assertEquals("Changed", cloned.text())
        assertTrue(visited.first() == "head:0:div")
        assertTrue(visited.contains("head:2:#text"))
        assertTrue(visited.last() == "tail:0:div")
    }

    private fun withFakeTransport(
        responder: (RequestData) -> ResponseData,
        assertions: (MutableList<RequestData>) -> Unit
    ) {
        val previous = KsoupTransport.executor
        val captured = mutableListOf<RequestData>()
        KsoupTransport.executor = KsoupTransportExecutor { request ->
            val snapshot = request.copyDeep()
            captured.add(snapshot)
            responder(snapshot)
        }
        try {
            assertions(captured)
        } finally {
            KsoupTransport.executor = previous
        }
    }
}
