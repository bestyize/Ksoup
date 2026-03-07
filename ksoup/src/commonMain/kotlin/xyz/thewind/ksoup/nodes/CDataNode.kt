package xyz.thewind.ksoup.nodes

class CDataNode(text: String) : TextNode(text) {
    override fun outerHtml(): String = "<![CDATA[${getWholeText()}]]>"
}
