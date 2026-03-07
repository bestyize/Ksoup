package xyz.thewind.ksoup.select

import xyz.thewind.ksoup.nodes.Node

object NodeTraversor {
    fun traverse(visitor: NodeVisitor, root: Node) {
        traverseNode(visitor, root, depth = 0)
    }

    fun traverse(visitor: NodeVisitor, roots: Elements) {
        roots.forEach { root -> traverse(visitor, root) }
    }

    private fun traverseNode(visitor: NodeVisitor, node: Node, depth: Int) {
        visitor.head(node, depth)
        node.childNodes().forEach { child ->
            traverseNode(visitor, child, depth + 1)
        }
        visitor.tail(node, depth)
    }

    fun filter(filter: NodeFilter, root: Node): NodeFilter.FilterResult =
        filterNode(filter, root, depth = 0)

    fun filter(filter: NodeFilter, roots: Elements): NodeFilter.FilterResult {
        roots.toList().forEach { root ->
            if (filter(filter, root) == NodeFilter.FilterResult.STOP) {
                return NodeFilter.FilterResult.STOP
            }
        }
        return NodeFilter.FilterResult.CONTINUE
    }

    private fun filterNode(filter: NodeFilter, node: Node, depth: Int): NodeFilter.FilterResult {
        return when (val headResult = filter.head(node, depth)) {
            NodeFilter.FilterResult.STOP -> NodeFilter.FilterResult.STOP
            NodeFilter.FilterResult.REMOVE -> {
                node.remove()
                NodeFilter.FilterResult.CONTINUE
            }
            NodeFilter.FilterResult.SKIP_ENTIRELY -> NodeFilter.FilterResult.CONTINUE
            NodeFilter.FilterResult.SKIP_CHILDREN -> applyTail(filter, node, depth)
            NodeFilter.FilterResult.CONTINUE -> {
                node.childNodes().toList().forEach { child ->
                    if (filterNode(filter, child, depth + 1) == NodeFilter.FilterResult.STOP) {
                        return NodeFilter.FilterResult.STOP
                    }
                }
                applyTail(filter, node, depth)
            }
        }
    }

    private fun applyTail(filter: NodeFilter, node: Node, depth: Int): NodeFilter.FilterResult =
        when (filter.tail(node, depth)) {
            NodeFilter.FilterResult.STOP -> NodeFilter.FilterResult.STOP
            NodeFilter.FilterResult.REMOVE -> {
                node.remove()
                NodeFilter.FilterResult.CONTINUE
            }
            else -> NodeFilter.FilterResult.CONTINUE
        }
}
