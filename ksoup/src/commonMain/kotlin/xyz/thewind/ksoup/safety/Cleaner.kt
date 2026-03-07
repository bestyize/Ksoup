package xyz.thewind.ksoup.safety

import xyz.thewind.ksoup.nodes.Comment
import xyz.thewind.ksoup.nodes.DataNode
import xyz.thewind.ksoup.nodes.Document
import xyz.thewind.ksoup.nodes.Element
import xyz.thewind.ksoup.nodes.Node
import xyz.thewind.ksoup.nodes.TextNode

class Cleaner(
    private val safelist: Safelist
) {
    fun clean(dirtyDocument: Document): Document {
        val clean = Document.createShell(dirtyDocument.location())
        clean.outputSettings(dirtyDocument.outputSettings().clone())
        clean.parser(dirtyDocument.parser())
        copySafeNodes(dirtyDocument.body().childNodes(), clean.body())
        return clean
    }

    fun isValid(dirtyDocument: Document): Boolean = clean(dirtyDocument).body().html() == dirtyDocument.body().html()

    private fun copySafeNodes(sourceNodes: List<Node>, destination: Element) {
        sourceNodes.forEach { node ->
            when (node) {
                is TextNode -> destination.appendChild(TextNode(node.getWholeText()))
                is Element -> copySafeElement(node, destination)
                is Comment -> Unit
                is DataNode -> Unit
                else -> Unit
            }
        }
    }

    private fun copySafeElement(source: Element, destination: Element) {
        if (!safelist.isSafeTag(source.tagName())) {
            copySafeNodes(source.childNodes(), destination)
            return
        }

        val cleanChild = Element(source.tagName())
        source.attributes().asMap().forEach { (key, value) ->
            if (safelist.isSafeAttribute(source.tagName(), source, key, value)) {
                cleanChild.attr(key, safelist.safeAttributeValue(source.tagName(), source, key, value))
            }
        }
        safelist.getEnforcedAttributes(source.tagName()).asMap().forEach { (key, value) ->
            cleanChild.attr(key, value)
        }
        destination.appendChild(cleanChild)
        copySafeNodes(source.childNodes(), cleanChild)
    }
}
