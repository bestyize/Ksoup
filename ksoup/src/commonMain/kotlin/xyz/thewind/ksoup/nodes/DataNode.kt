package xyz.thewind.ksoup.nodes

import xyz.thewind.ksoup.parser.Parser

class DataNode(private var data: String) : Node("#data") {
    fun getWholeData(): String = data

    fun setWholeData(data: String): DataNode {
        this.data = data
        return this
    }

    override fun text(): String = ""

    override fun outerHtml(): String = data

    companion object {
        fun createFromEncoded(encodedData: String): DataNode =
            DataNode(Parser.unescapeEntities(encodedData))
    }
}
