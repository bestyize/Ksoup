package xyz.thewind.ksoup

import xyz.thewind.ksoup.helper.HttpConnection
import xyz.thewind.ksoup.nodes.Document
import xyz.thewind.ksoup.parser.HtmlParser
import xyz.thewind.ksoup.parser.Parser
import xyz.thewind.ksoup.safety.Cleaner
import xyz.thewind.ksoup.safety.Safelist

object Jsoup {
    fun parse(html: String, baseUri: String = ""): Document = HtmlParser.parse(html, baseUri)

    fun parse(html: String, baseUri: String, parser: Parser): Document = parser.parseInput(html, baseUri)

    fun parseBodyFragment(bodyHtml: String, baseUri: String = ""): Document =
        HtmlParser.parseBodyFragment(bodyHtml, baseUri)

    fun parseBodyFragment(bodyHtml: String, baseUri: String, parser: Parser): Document =
        parser.parseBodyFragment(bodyHtml, baseUri)

    fun parse(html: String, parser: Parser): Document = parser.parseInput(html)

    fun clean(bodyHtml: String, safelist: Safelist): String =
        Cleaner(safelist).clean(parseBodyFragment(bodyHtml)).body().html()

    fun clean(bodyHtml: String, safelist: Safelist, outputSettings: Document.OutputSettings): String =
        Cleaner(safelist).clean(
            parseBodyFragment(bodyHtml).outputSettings(outputSettings.clone())
        ).body().html()

    fun clean(bodyHtml: String, baseUri: String, safelist: Safelist): String =
        Cleaner(safelist).clean(parseBodyFragment(bodyHtml, baseUri)).body().html()

    fun clean(
        bodyHtml: String,
        baseUri: String,
        safelist: Safelist,
        outputSettings: Document.OutputSettings
    ): String = Cleaner(safelist).clean(
        parseBodyFragment(bodyHtml, baseUri).outputSettings(outputSettings.clone())
    ).body().html()

    fun isValid(bodyHtml: String, safelist: Safelist): Boolean =
        Cleaner(safelist).isValid(parseBodyFragment(bodyHtml))

    fun connect(url: String): Connection = HttpConnection.connect(url)

    fun newSession(): Connection = HttpConnection.newSession()
}
