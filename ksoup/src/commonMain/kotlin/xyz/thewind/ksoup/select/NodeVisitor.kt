package xyz.thewind.ksoup.select

import xyz.thewind.ksoup.nodes.Node

interface NodeVisitor {
    fun head(node: Node, depth: Int)

    fun tail(node: Node, depth: Int)
}
