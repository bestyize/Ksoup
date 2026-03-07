package xyz.thewind.ksoup.select

import xyz.thewind.ksoup.nodes.Element
import xyz.thewind.ksoup.nodes.FormElement

class Elements(
    private val backing: MutableList<Element> = mutableListOf()
) : MutableList<Element> by backing {
    fun first(): Element? = backing.firstOrNull()

    fun last(): Element? = backing.lastOrNull()

    fun text(): String = backing.joinToString(" ") { it.text() }.trim()

    fun eachText(): List<String> = backing.map { it.text() }

    fun clone(): Elements = Elements(backing.mapTo(mutableListOf()) { it.clone() as Element })

    fun forms(): List<FormElement> = backing.filterIsInstance<FormElement>()

    fun hasText(): Boolean = backing.any { it.hasText() }

    fun `val`(): String = first()?.`val`().orEmpty()

    fun `val`(value: String): Elements {
        backing.forEach { it.`val`(value) }
        return this
    }

    fun attr(attributeKey: String): String = backing.firstOrNull { it.hasAttr(attributeKey) }?.attr(attributeKey).orEmpty()

    fun attr(attributeKey: String, attributeValue: String): Elements {
        backing.forEach { it.attr(attributeKey, attributeValue) }
        return this
    }

    fun hasAttr(attributeKey: String): Boolean = backing.any { it.hasAttr(attributeKey) }

    fun removeAttr(attributeKey: String): Elements {
        backing.forEach { it.removeAttr(attributeKey) }
        return this
    }

    fun addClass(className: String): Elements {
        backing.forEach { it.addClass(className) }
        return this
    }

    fun hasClass(className: String): Boolean = backing.any { it.hasClass(className) }

    fun removeClass(className: String): Elements {
        backing.forEach { it.removeClass(className) }
        return this
    }

    fun toggleClass(className: String): Elements {
        backing.forEach { it.toggleClass(className) }
        return this
    }

    fun html(): String = backing.joinToString("\n") { it.html() }

    fun outerHtml(): String = backing.joinToString("\n") { it.outerHtml() }

    fun eachAttr(attributeKey: String): List<String> = backing.filter { it.hasAttr(attributeKey) }.map { it.attr(attributeKey) }

    fun `is`(cssQuery: String): Boolean = backing.any { it.`is`(cssQuery) }

    fun traverse(visitor: NodeVisitor): Elements {
        NodeTraversor.traverse(visitor, this)
        return this
    }

    fun forEachNode(consumer: (xyz.thewind.ksoup.nodes.Node) -> Unit): Elements {
        backing.forEach { it.forEachNode(consumer) }
        return this
    }

    fun filter(filter: NodeFilter): Elements {
        NodeTraversor.filter(filter, this)
        return this
    }

    fun selectFirst(cssQuery: String): Element? = Selector.selectFirst(cssQuery, backing)

    fun expectFirst(cssQuery: String): Element =
        selectFirst(cssQuery) ?: throw IllegalArgumentException("No elements matched the query '$cssQuery'")

    fun select(cssQuery: String): Elements {
        val results = linkedSetOf<Element>()
        backing.forEach { element ->
            results.addAll(Selector.select(cssQuery, element))
        }
        return Elements(results.toMutableList())
    }

    fun not(cssQuery: String): Elements =
        Elements(backing.filterNot { it.`is`(cssQuery) }.toMutableList())

    fun parents(): Elements {
        val results = linkedSetOf<Element>()
        backing.forEach { element ->
            results.addAll(element.parents())
        }
        return Elements(results.toMutableList())
    }

    fun next(): Elements = Elements(
        backing.mapNotNullTo(linkedSetOf()) { it.nextElementSibling() }.toMutableList()
    )

    fun prev(): Elements = Elements(
        backing.mapNotNullTo(linkedSetOf()) { it.previousElementSibling() }.toMutableList()
    )

    fun nextAll(): Elements {
        val results = linkedSetOf<Element>()
        backing.forEach { element ->
            results.addAll(element.nextElementSiblings())
        }
        return Elements(results.toMutableList())
    }

    fun prevAll(): Elements {
        val results = linkedSetOf<Element>()
        backing.forEach { element ->
            results.addAll(element.previousElementSiblings())
        }
        return Elements(results.toMutableList())
    }

    fun siblings(): Elements {
        val results = linkedSetOf<Element>()
        backing.forEach { element ->
            results.addAll(element.siblingElements())
        }
        return Elements(results.toMutableList())
    }

    fun before(html: String): Elements {
        backing.forEach { it.before(html) }
        return this
    }

    fun after(html: String): Elements {
        backing.forEach { it.after(html) }
        return this
    }

    fun append(html: String): Elements {
        backing.forEach { it.append(html) }
        return this
    }

    fun prepend(html: String): Elements {
        backing.forEach { it.prepend(html) }
        return this
    }

    fun html(html: String): Elements {
        backing.forEach { it.html(html) }
        return this
    }

    fun text(text: String): Elements {
        backing.forEach { it.text(text) }
        return this
    }

    fun tagName(tagName: String): Elements {
        backing.forEach { it.tagName(tagName) }
        return this
    }

    fun wrap(html: String): Elements {
        backing.toList().forEach { it.wrap(html) }
        return this
    }

    fun unwrap(): Elements {
        backing.toList().forEach { it.unwrap() }
        return this
    }

    fun remove(): Elements {
        backing.forEach { it.remove() }
        return this
    }

    fun empty(): Elements {
        backing.forEach { it.empty() }
        return this
    }

    fun eq(index: Int): Elements = backing.getOrNull(index)?.let { Elements(mutableListOf(it)) } ?: Elements()

    fun deselect(index: Int): Element = backing.removeAt(index)

    override fun removeAt(index: Int): Element {
        val removed = backing.removeAt(index)
        removed.remove()
        return removed
    }
}
