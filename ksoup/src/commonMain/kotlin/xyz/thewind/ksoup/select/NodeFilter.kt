package xyz.thewind.ksoup.select

import xyz.thewind.ksoup.nodes.Node

interface NodeFilter {
    enum class FilterResult {
        CONTINUE,
        SKIP_CHILDREN,
        SKIP_ENTIRELY,
        REMOVE,
        STOP
    }

    fun head(node: Node, depth: Int): FilterResult

    fun tail(node: Node, depth: Int): FilterResult
}
