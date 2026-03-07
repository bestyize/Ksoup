package xyz.thewind.ksoup.parser

import xyz.thewind.ksoup.nodes.CDataNode
import xyz.thewind.ksoup.nodes.Comment
import xyz.thewind.ksoup.nodes.Document
import xyz.thewind.ksoup.nodes.DocumentType
import xyz.thewind.ksoup.nodes.Element
import xyz.thewind.ksoup.nodes.Node
import xyz.thewind.ksoup.nodes.TextNode
import xyz.thewind.ksoup.nodes.XmlDeclaration
import xyz.thewind.ksoup.parser.Parser

internal object XmlParser {
    fun parse(xml: String, baseUri: String): Document {
        val document = Document(baseUri, useShell = false)
            .parser(Parser.xmlParser())
        val stack = mutableListOf<Element>()
        var index = 0

        while (index < xml.length) {
            val current = xml[index]
            if (current != '<') {
                val nextTag = xml.indexOf('<', startIndex = index).let { if (it == -1) xml.length else it }
                appendTextNode(document, stack, MarkupParserSupport.decodeHtml(xml.substring(index, nextTag)))
                index = nextTag
                continue
            }

            when {
                xml.startsWith("<![CDATA[", index) -> {
                    val cdataEnd = xml.indexOf("]]>", startIndex = index + 9)
                    val contentEnd = if (cdataEnd == -1) xml.length else cdataEnd
                    appendNode(document, stack, CDataNode(xml.substring(index + 9, contentEnd)))
                    index = if (cdataEnd == -1) xml.length else cdataEnd + 3
                }

                xml.startsWith("<!--", index) -> {
                    val commentEnd = xml.indexOf("-->", startIndex = index + 4)
                    val contentEnd = if (commentEnd == -1) xml.length else commentEnd
                    appendNode(document, stack, Comment(xml.substring(index + 4, contentEnd)))
                    index = if (commentEnd == -1) xml.length else commentEnd + 3
                }

                xml.startsWithIgnoreCase("<!DOCTYPE", index) -> {
                    val declarationEnd = xml.indexOf('>', startIndex = index + 2)
                    if (declarationEnd == -1) {
                        break
                    }
                    document.appendChild(parseDocumentType(xml.substring(index + 2, declarationEnd)))
                    index = declarationEnd + 1
                }

                xml.startsWith("</", index) -> {
                    val closeEnd = xml.indexOf('>', startIndex = index + 2)
                    if (closeEnd == -1) {
                        break
                    }
                    closeElement(stack, xml.substring(index + 2, closeEnd).trim())
                    index = closeEnd + 1
                }

                xml.startsWith("<?", index) -> {
                    val declarationEnd = xml.indexOf("?>", startIndex = index + 2).let {
                        if (it == -1) xml.indexOf('>', startIndex = index + 2) else it
                    }
                    if (declarationEnd == -1) {
                        break
                    }
                    appendNode(document, stack, parseXmlDeclaration(xml.substring(index + 2, declarationEnd)))
                    index = declarationEnd + if (xml.startsWith("?>", declarationEnd)) 2 else 1
                }

                xml.startsWith("<!", index) -> {
                    index = xml.indexOf('>', startIndex = index + 2).let { if (it == -1) xml.length else it + 1 }
                }

                else -> {
                    val closeEnd = xml.indexOf('>', startIndex = index + 1)
                    if (closeEnd == -1) {
                        appendTextNode(document, stack, MarkupParserSupport.decodeHtml(xml.substring(index)))
                        break
                    }

                    val parsed = MarkupParserSupport.parseStartTag(xml.substring(index + 1, closeEnd))
                    stack.lastOrNull()?.appendChild(parsed.element) ?: document.appendChild(parsed.element)
                    if (!parsed.selfClosing) {
                        stack.add(parsed.element)
                    }
                    index = closeEnd + 1
                }
            }
        }

        return document
    }

    fun parseFragment(fragmentXml: String, baseUri: String): List<Node> =
        parse(fragmentXml, baseUri).childNodes().toList()

    private fun appendTextNode(document: Document, stack: List<Element>, text: String) {
        if (text.isEmpty()) {
            return
        }
        stack.lastOrNull()?.appendChild(TextNode(text)) ?: document.appendChild(TextNode(text))
    }

    private fun appendNode(document: Document, stack: List<Element>, node: Node) {
        stack.lastOrNull()?.appendChild(node) ?: document.appendChild(node)
    }

    private fun closeElement(stack: MutableList<Element>, tagName: String) {
        for (i in stack.lastIndex downTo 0) {
            val element = stack[i]
            stack.removeAt(i)
            if (element.tagName() == tagName) {
                return
            }
        }
    }

    private fun parseDocumentType(rawDeclaration: String): DocumentType {
        val content = rawDeclaration.removePrefix("!").trim()
        val body = if (content.length >= 7 && content.substring(0, 7).equals("DOCTYPE", ignoreCase = true)) {
            content.substring(7).trim()
        } else {
            content
        }
        val tokens = MarkupParserSupport.tokenizeMarkup(body)
        val name = tokens.getOrNull(0).orEmpty()
        val pubSysKey = tokens.getOrNull(1)?.takeIf {
            it.equals("PUBLIC", ignoreCase = true) || it.equals("SYSTEM", ignoreCase = true)
        }?.uppercase()
        val publicId = if (pubSysKey == "PUBLIC") tokens.getOrNull(2).orEmpty() else ""
        val systemId = when (pubSysKey) {
            "PUBLIC" -> tokens.getOrNull(3).orEmpty()
            "SYSTEM" -> tokens.getOrNull(2).orEmpty()
            else -> ""
        }
        return DocumentType(name, publicId, systemId, pubSysKey)
    }

    private fun parseXmlDeclaration(rawDeclaration: String): XmlDeclaration {
        val parsed = MarkupParserSupport.parseStartTag(rawDeclaration.trim())
        val declaration = XmlDeclaration(parsed.name)
        parsed.element.attributes().asMap().forEach { (key, value) ->
            declaration.attr(key, value)
        }
        return declaration
    }

    private fun String.startsWithIgnoreCase(prefix: String, startIndex: Int): Boolean {
        if (startIndex < 0 || startIndex + prefix.length > length) {
            return false
        }
        return regionMatches(startIndex, prefix, 0, prefix.length, ignoreCase = true)
    }
}
