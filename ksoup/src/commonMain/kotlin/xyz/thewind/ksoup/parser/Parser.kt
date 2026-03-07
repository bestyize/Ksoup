package xyz.thewind.ksoup.parser

import xyz.thewind.ksoup.nodes.Document

class Parser private constructor(
    private val mode: Mode
) {
    enum class Mode {
        HTML,
        XML
    }

    fun parseInput(html: String, baseUri: String = ""): Document = when (mode) {
        Mode.HTML -> HtmlParser.parse(html, baseUri)
        Mode.XML -> XmlParser.parse(html, baseUri)
    }

    fun parseBodyFragment(bodyHtml: String, baseUri: String = ""): Document = when (mode) {
        Mode.HTML -> HtmlParser.parseBodyFragment(bodyHtml, baseUri)
        Mode.XML -> XmlParser.parse(bodyHtml, baseUri)
    }

    internal fun parseFragmentNodes(fragment: String, baseUri: String = "") = when (mode) {
        Mode.HTML -> HtmlParser.parseFragment(fragment, baseUri)
        Mode.XML -> XmlParser.parseFragment(fragment, baseUri)
    }

    internal fun mode(): Mode = mode

    companion object {
        fun htmlParser(): Parser = Parser(Mode.HTML)

        fun xmlParser(): Parser = Parser(Mode.XML)

        fun unescapeEntities(string: String, inAttribute: Boolean = false): String {
            return MarkupParserSupport.decodeHtml(string)
        }
    }
}
