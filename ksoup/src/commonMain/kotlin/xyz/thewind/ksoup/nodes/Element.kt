package xyz.thewind.ksoup.nodes

import xyz.thewind.ksoup.parser.HtmlParser
import xyz.thewind.ksoup.parser.Parser
import xyz.thewind.ksoup.select.Elements
import xyz.thewind.ksoup.select.NodeVisitor
import xyz.thewind.ksoup.select.Selector

internal val rawTextTags = setOf("script", "style")

private val htmlVoidTags = setOf(
    "area", "base", "br", "col", "embed", "hr", "img", "input",
    "link", "meta", "param", "source", "track", "wbr"
)

private val htmlBooleanAttributes = setOf(
    "allowfullscreen", "async", "autofocus", "checked", "compact", "declare", "default", "defer",
    "disabled", "formnovalidate", "hidden", "inert", "ismap", "itemscope", "multiple", "muted",
    "nohref", "noresize", "noshade", "novalidate", "nowrap", "open", "readonly", "required",
    "reversed", "selected"
)

private val preserveWhitespaceTags = setOf("pre", "textarea", "script", "style")
private val stripLeadingNewlineTags = setOf("pre", "textarea", "listing")

private val blockFormattingTags = setOf(
    "html", "head", "body", "div", "section", "article", "aside", "nav", "header", "footer", "main",
    "p", "pre", "blockquote", "figure", "figcaption", "form", "fieldset", "legend",
    "ul", "ol", "li", "dl", "dt", "dd",
    "table", "caption", "colgroup", "thead", "tbody", "tfoot", "tr", "td", "th",
    "h1", "h2", "h3", "h4", "h5", "h6"
)

open class Element(
    private var tagNameValue: String,
    private val attributesValue: Attributes = Attributes()
) : Node(tagNameValue.lowercase()), Iterable<Element> {
    private val childNodes = mutableListOf<Node>()

    override fun nodeName(): String = tagNameValue

    fun tagName(): String = tagNameValue

    fun normalName(): String = tagNameValue.lowercase()

    fun tagName(tagName: String): Element {
        tagNameValue = tagName
        return this
    }

    fun childNodeSize(): Int = childNodes.size

    override fun childNodes(): List<Node> = childNodes.toList()

    fun children(): Elements = Elements(childNodes.filterIsInstance<Element>().toMutableList())

    fun childrenSize(): Int = children().size

    fun child(index: Int): Element = children()[index]

    fun appendTo(parent: Element): Element {
        parent.appendChild(this)
        return this
    }

    fun appendChild(child: Node): Element {
        insertChildren(childNodes.size, listOf(child))
        return this
    }

    fun prependChild(child: Node): Element {
        insertChildren(0, listOf(child))
        return this
    }

    fun appendText(text: String): Element {
        if (text.isNotEmpty()) {
            appendChild(TextNode(text))
        }
        return this
    }

    fun prependText(text: String): Element {
        if (text.isNotEmpty()) {
            prependChild(TextNode(text))
        }
        return this
    }

    fun appendElement(tagName: String): Element = Element(tagName).also { appendChild(it) }

    fun prependElement(tagName: String): Element = Element(tagName).also { prependChild(it) }

    fun append(html: String): Element {
        val parser = ownerDocument()?.parser() ?: Parser.htmlParser()
        insertChildren(childNodes.size, parser.parseFragmentNodes(html, baseUri()))
        return this
    }

    fun prepend(html: String): Element {
        val parser = ownerDocument()?.parser() ?: Parser.htmlParser()
        insertChildren(0, parser.parseFragmentNodes(html, baseUri()))
        return this
    }

    fun html(html: String): Element {
        val parser = ownerDocument()?.parser() ?: Parser.htmlParser()
        replaceChildren(parser.parseFragmentNodes(html, baseUri()))
        return this
    }

    fun text(text: String): Element {
        val replacement = if (tagName().lowercase() in rawTextTags) DataNode(text) else TextNode(text)
        replaceChildren(listOf(replacement))
        return this
    }

    fun empty(): Element {
        replaceChildren(emptyList())
        return this
    }

    fun insertChildren(index: Int, children: Collection<Node>): Element {
        require(index in 0..childNodes.size) { "Insert position $index out of bounds for child node size ${childNodes.size}" }
        var offset = index
        children.forEach { child ->
            child.parentNodeRef?.removeChild(child)
            child.parentNodeRef = this
            childNodes.add(offset, child)
            offset++
        }
        return this
    }

    fun appendChildren(children: Collection<Node>): Element = insertChildren(childNodes.size, children)

    fun prependChildren(children: Collection<Node>): Element = insertChildren(0, children)

    internal fun removeChild(child: Node) {
        childNodes.remove(child)
        child.parentNodeRef = null
    }

    internal fun replaceChild(out: Node, input: Node) {
        val index = childNodes.indexOf(out)
        if (index == -1) {
            return
        }
        out.parentNodeRef = null
        input.parentNodeRef?.removeChild(input)
        input.parentNodeRef = this
        childNodes[index] = input
    }

    override fun attr(attributeKey: String): String {
        if (attributeKey.startsWith("abs:")) {
            return absUrl(attributeKey.removePrefix("abs:"))
        }
        return attributesValue[normalizeAttrKey(attributeKey)]
    }

    override fun attr(attributeKey: String, attributeValue: String): Element {
        attributesValue[normalizeAttrKey(attributeKey)] = attributeValue
        return this
    }

    override fun hasAttr(attributeKey: String): Boolean {
        if (attributeKey.startsWith("abs:")) {
            val key = attributeKey.removePrefix("abs:")
            return hasAttr(key) && absUrl(key).isNotEmpty()
        }
        return attributesValue.hasKey(normalizeAttrKey(attributeKey))
    }

    override fun removeAttr(attributeKey: String): Element {
        attributesValue.remove(normalizeAttrKey(attributeKey))
        return this
    }

    override fun attributesSize(): Int = attributesValue.size()

    fun attributes(): Attributes = attributesValue

    fun id(): String = attr("id")

    fun id(id: String): Element = attr("id", id)

    fun className(): String = attr("class")

    fun className(className: String): Element = attr("class", className)

    fun lang(): String {
        var current: Element? = this
        while (current != null) {
            val direct = current.attr("lang").ifEmpty { current.attr("xml:lang") }
            if (direct.isNotEmpty()) {
                return direct
            }
            current = current.parent()
        }
        return ""
    }

    fun classNames(): Set<String> = className()
        .splitToSequence(' ', '\t', '\n', '\r')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    fun classNames(classNames: Set<String>): Element = attr("class", classNames.joinToString(" "))

    fun addClass(className: String): Element {
        val classes = classNames().toMutableSet()
        classes.add(className)
        return classNames(classes)
    }

    fun hasClass(className: String): Boolean = className in classNames()

    fun removeClass(className: String): Element {
        val classes = classNames().toMutableSet()
        classes.remove(className)
        return classNames(classes)
    }

    fun toggleClass(className: String): Element {
        return if (hasClass(className)) removeClass(className) else addClass(className)
    }

    fun clearAttributes(): Element {
        attributesValue.clear()
        return this
    }

    fun dataset(): Map<String, String> = attributesValue.dataset()

    fun `val`(): String = when (tagName().lowercase()) {
        "textarea" -> text()
        "option" -> attr("value").ifEmpty { text() }
        "select" -> children()
            .flatMap { child ->
                when {
                    child.tagName().equals("option", ignoreCase = true) -> listOf(child)
                    child.tagName().equals("optgroup", ignoreCase = true) -> child.select("option").toList()
                    else -> emptyList()
                }
            }
            .firstOrNull { it.hasAttr("selected") }
            ?.let { if (it.hasAttr("value")) it.attr("value") else it.text() }
            .orEmpty()
        else -> attr("value")
    }

    fun `val`(value: String): Element {
        when (tagName().lowercase()) {
            "textarea" -> text(value)
            "option" -> attr("value", value)
            "select" -> {
                val options = children().flatMap { child ->
                    when {
                        child.tagName().equals("option", ignoreCase = true) -> listOf(child)
                        child.tagName().equals("optgroup", ignoreCase = true) -> child.select("option").toList()
                        else -> emptyList()
                    }
                }
                options.forEach { option ->
                    val optionValue = option.attr("value").ifEmpty { option.text() }
                    if (optionValue == value) {
                        option.attr("selected", "selected")
                    } else {
                        option.removeAttr("selected")
                    }
                }
            }
            else -> attr("value", value)
        }
        return this
    }

    override fun text(): String {
        val chunks = mutableListOf<String>()
        collectText(this, chunks)
        return chunks.joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun wholeText(): String = buildString { collectWholeText(this@Element, this) }

    fun wholeOwnText(): String = childNodes
        .filterIsInstance<TextNode>()
        .joinToString(separator = "") { it.getWholeText() }

    fun textNodes(): List<TextNode> = childNodes.filterIsInstance<TextNode>()

    fun dataNodes(): List<DataNode> = childNodes.filterIsInstance<DataNode>()

    fun data(): String = buildString { collectData(this@Element, this) }

    fun ownText(): String = childNodes
        .filterIsInstance<TextNode>()
        .joinToString("") { it.text() }
        .replace(Regex("\\s+"), " ")
        .trim()

    open fun html(): String = buildString {
        renderElementChildren(this@Element, this, depth = -1, addLeadingIndent = false)
    }

    override fun outerHtml(): String = buildString {
        renderNodeHtml(this@Element, this, depth = 0)
    }

    fun getElementById(id: String): Element? {
        if (id() == id) {
            return this
        }

        for (child in children()) {
            val match = child.getElementById(id)
            if (match != null) {
                return match
            }
        }

        return null
    }

    fun getAllElements(): Elements {
        val result = mutableListOf<Element>()
        traverseElements(includeSelf = true) { result.add(it) }
        return Elements(result)
    }

    fun getElementsByTag(tagName: String): Elements {
        val result = mutableListOf<Element>()
        traverseElements(includeSelf = true) { element ->
            if (element.tagName().equals(tagName, ignoreCase = true)) {
                result.add(element)
            }
        }
        return Elements(result)
    }

    fun getElementsByClass(className: String): Elements {
        val result = mutableListOf<Element>()
        traverseElements(includeSelf = true) { element ->
            if (element.hasClass(className)) {
                result.add(element)
            }
        }
        return Elements(result)
    }

    fun getElementsByAttribute(attributeKey: String): Elements {
        val key = normalizeAttrKey(attributeKey)
        val result = mutableListOf<Element>()
        traverseElements(includeSelf = true) { element ->
            if (element.hasAttr(key)) {
                result.add(element)
            }
        }
        return Elements(result)
    }

    fun getElementsByAttributeStarting(attributePrefix: String): Elements {
        val prefix = normalizeAttrKey(attributePrefix)
        val result = mutableListOf<Element>()
        traverseElements(includeSelf = true) { element ->
            if (element.attributes().asMap().keys.any { it.startsWith(prefix) }) {
                result.add(element)
            }
        }
        return Elements(result)
    }

    fun getElementsByAttributeValue(attributeKey: String, value: String): Elements {
        val key = normalizeAttrKey(attributeKey)
        val result = mutableListOf<Element>()
        traverseElements(includeSelf = true) { element ->
            if (element.attr(key) == value) {
                result.add(element)
            }
        }
        return Elements(result)
    }

    fun getElementsByAttributeValueContaining(attributeKey: String, match: String): Elements {
        val key = normalizeAttrKey(attributeKey)
        val result = mutableListOf<Element>()
        traverseElements(includeSelf = true) { element ->
            if (element.attr(key).contains(match)) {
                result.add(element)
            }
        }
        return Elements(result)
    }

    fun getElementsByAttributeValueStarting(attributeKey: String, prefix: String): Elements {
        val key = normalizeAttrKey(attributeKey)
        val result = mutableListOf<Element>()
        traverseElements(includeSelf = true) { element ->
            if (element.attr(key).startsWith(prefix)) {
                result.add(element)
            }
        }
        return Elements(result)
    }

    fun getElementsByAttributeValueEnding(attributeKey: String, suffix: String): Elements {
        val key = normalizeAttrKey(attributeKey)
        val result = mutableListOf<Element>()
        traverseElements(includeSelf = true) { element ->
            if (element.attr(key).endsWith(suffix)) {
                result.add(element)
            }
        }
        return Elements(result)
    }

    fun getElementsByAttributeValueNot(attributeKey: String, value: String): Elements {
        val key = normalizeAttrKey(attributeKey)
        val result = mutableListOf<Element>()
        traverseElements(includeSelf = true) { element ->
            if (element.hasAttr(key) && element.attr(key) != value) {
                result.add(element)
            }
        }
        return Elements(result)
    }

    fun getElementsByAttributeValueMatching(attributeKey: String, pattern: String): Elements {
        val regex = runCatching { Regex(pattern) }.getOrElse { return Elements() }
        return getElementsByAttributeValueMatching(attributeKey, regex)
    }

    fun getElementsByAttributeValueMatching(attributeKey: String, regex: Regex): Elements {
        val key = normalizeAttrKey(attributeKey)
        val result = mutableListOf<Element>()
        traverseElements(includeSelf = true) { element ->
            if (element.hasAttr(key) && regex.containsMatchIn(element.attr(key))) {
                result.add(element)
            }
        }
        return Elements(result)
    }

    fun getElementsByIndexLessThan(index: Int): Elements {
        val result = mutableListOf<Element>()
        traverseElements(includeSelf = true) { element ->
            if (element.elementSiblingIndex() < index) {
                result.add(element)
            }
        }
        return Elements(result)
    }

    fun getElementsByIndexGreaterThan(index: Int): Elements {
        val result = mutableListOf<Element>()
        traverseElements(includeSelf = true) { element ->
            if (element.elementSiblingIndex() > index) {
                result.add(element)
            }
        }
        return Elements(result)
    }

    fun getElementsByIndexEquals(index: Int): Elements {
        val result = mutableListOf<Element>()
        traverseElements(includeSelf = true) { element ->
            if (element.elementSiblingIndex() == index) {
                result.add(element)
            }
        }
        return Elements(result)
    }

    fun getElementsContainingText(searchText: String): Elements {
        val needle = searchText.lowercase()
        val result = mutableListOf<Element>()
        traverseElements(includeSelf = true) { element ->
            if (element.text().lowercase().contains(needle)) {
                result.add(element)
            }
        }
        return Elements(result)
    }

    fun getElementsContainingOwnText(searchText: String): Elements {
        val needle = searchText.lowercase()
        val result = mutableListOf<Element>()
        traverseElements(includeSelf = true) { element ->
            if (element.ownText().lowercase().contains(needle)) {
                result.add(element)
            }
        }
        return Elements(result)
    }

    fun getElementsContainingData(searchText: String): Elements {
        val needle = searchText.lowercase()
        val result = mutableListOf<Element>()
        traverseElements(includeSelf = true) { element ->
            if (element.data().lowercase().contains(needle)) {
                result.add(element)
            }
        }
        return Elements(result)
    }

    fun getElementsContainingWholeText(searchText: String): Elements {
        val result = mutableListOf<Element>()
        traverseElements(includeSelf = true) { element ->
            if (element.wholeText().contains(searchText)) {
                result.add(element)
            }
        }
        return Elements(result)
    }

    fun getElementsContainingWholeOwnText(searchText: String): Elements {
        val result = mutableListOf<Element>()
        traverseElements(includeSelf = true) { element ->
            if (element.wholeOwnText().contains(searchText)) {
                result.add(element)
            }
        }
        return Elements(result)
    }

    fun getElementsMatchingText(pattern: String): Elements {
        val regex = runCatching { Regex(pattern) }.getOrElse { return Elements() }
        return getElementsMatchingText(regex)
    }

    fun getElementsMatchingText(pattern: Regex): Elements {
        val result = mutableListOf<Element>()
        traverseElements(includeSelf = true) { element ->
            if (pattern.containsMatchIn(element.text())) {
                result.add(element)
            }
        }
        return Elements(result)
    }

    fun getElementsMatchingOwnText(pattern: String): Elements {
        val regex = runCatching { Regex(pattern) }.getOrElse { return Elements() }
        return getElementsMatchingOwnText(regex)
    }

    fun getElementsMatchingOwnText(pattern: Regex): Elements {
        val result = mutableListOf<Element>()
        traverseElements(includeSelf = true) { element ->
            if (pattern.containsMatchIn(element.ownText())) {
                result.add(element)
            }
        }
        return Elements(result)
    }

    fun getElementsMatchingWholeText(pattern: String): Elements {
        val regex = runCatching { Regex(pattern) }.getOrElse { return Elements() }
        return getElementsMatchingWholeText(regex)
    }

    fun getElementsMatchingWholeText(pattern: Regex): Elements {
        val result = mutableListOf<Element>()
        traverseElements(includeSelf = true) { element ->
            if (pattern.containsMatchIn(element.wholeText())) {
                result.add(element)
            }
        }
        return Elements(result)
    }

    fun getElementsMatchingWholeOwnText(pattern: String): Elements {
        val regex = runCatching { Regex(pattern) }.getOrElse { return Elements() }
        return getElementsMatchingWholeOwnText(regex)
    }

    fun getElementsMatchingWholeOwnText(pattern: Regex): Elements {
        val result = mutableListOf<Element>()
        traverseElements(includeSelf = true) { element ->
            if (pattern.containsMatchIn(element.wholeOwnText())) {
                result.add(element)
            }
        }
        return Elements(result)
    }

    fun select(cssQuery: String): Elements = Selector.select(cssQuery, this)

    fun selectFirst(cssQuery: String): Element? = Selector.selectFirst(cssQuery, this)

    fun expectFirst(cssQuery: String): Element =
        selectFirst(cssQuery) ?: throw IllegalArgumentException("No elements matched the query '$cssQuery'")

    fun `is`(cssQuery: String): Boolean = Selector.matches(cssQuery, this)

    fun isSelector(cssQuery: String): Boolean = `is`(cssQuery)

    fun closest(cssQuery: String): Element? {
        var current: Element? = this
        while (current != null) {
            if (current.`is`(cssQuery)) {
                return current
            }
            current = current.parent()
        }
        return null
    }

    fun form(): FormElement? {
        var current = parent()
        while (current != null) {
            if (current is FormElement) {
                return current
            }
            current = current.parent()
        }
        return null
    }

    fun cssSelector(): String {
        if (id().isNotEmpty()) {
            return "${tagName()}#${id()}"
        }

        val parent = parent()
        if (parent == null || parent is Document) {
            return tagName()
        }

        val selector = buildString {
            append(tagName())
            val sameTypeSiblings = parent.children().filter { it.tagName().equals(tagName(), ignoreCase = true) }
            if (sameTypeSiblings.size > 1) {
                append(":nth-of-type(")
                append(elementSiblingIndexOfType() + 1)
                append(')')
            }
        }
        return "${parent.cssSelector()} > $selector"
    }

    override fun parent(): Element? = parentNode()

    fun parentElement(): Element? = parent()

    fun parents(): Elements {
        val result = mutableListOf<Element>()
        var current = parent()
        while (current != null && current !is Document) {
            result.add(current)
            current = current.parent()
        }
        return Elements(result)
    }

    fun siblingElements(): Elements {
        val siblings = parent()?.children() ?: return Elements()
        return Elements(siblings.filter { it !== this }.toMutableList())
    }

    fun elementSiblingIndex(): Int = siblingElementsWithSelf().indexOf(this)

    fun firstElementChild(): Element? = children().firstOrNull()

    fun lastElementChild(): Element? = children().lastOrNull()

    fun nextElementSibling(): Element? {
        val siblings = siblingElementsWithSelf()
        return siblings.getOrNull(siblings.indexOf(this) + 1)
    }

    fun previousElementSibling(): Element? {
        val siblings = siblingElementsWithSelf()
        return siblings.getOrNull(siblings.indexOf(this) - 1)
    }

    fun nextElementSiblings(): Elements {
        val siblings = siblingElementsWithSelf()
        val index = siblings.indexOf(this)
        return Elements(siblings.drop(index + 1).toMutableList())
    }

    fun previousElementSiblings(): Elements {
        val siblings = siblingElementsWithSelf()
        val index = siblings.indexOf(this)
        return Elements(siblings.take(index).toMutableList())
    }

    fun firstElementSibling(): Element? = siblingElementsWithSelf().firstOrNull()

    fun lastElementSibling(): Element? = siblingElementsWithSelf().lastOrNull()

    internal fun isFirstChild(): Boolean = parent()?.children()?.firstOrNull() === this

    internal fun isLastChild(): Boolean = parent()?.children()?.lastOrNull() === this

    internal fun isOnlyChild(): Boolean = parent()?.childrenSize() == 1

    internal fun isFirstOfType(): Boolean = siblingsOfSameType().firstOrNull() === this

    internal fun isLastOfType(): Boolean = siblingsOfSameType().lastOrNull() === this

    internal fun isOnlyOfType(): Boolean = siblingsOfSameType().size == 1

    internal fun elementSiblingIndexOfType(): Int = siblingsOfSameType().indexOf(this)

    internal fun reverseElementSiblingIndexOfType(): Int {
        val siblings = siblingsOfSameType()
        val index = siblings.indexOf(this)
        return if (index == -1) -1 else siblings.lastIndex - index
    }

    fun hasText(): Boolean = text().isNotBlank()

    fun hasOwnText(): Boolean = ownText().isNotBlank()

    override fun baseUri(): String = super.baseUri()

    override fun remove(): Element {
        super.remove()
        return this
    }

    override fun iterator(): Iterator<Element> = getAllElements().iterator()

    internal fun traverseElements(includeSelf: Boolean, visitor: (Element) -> Unit) {
        if (includeSelf) {
            visitor(this)
        }

        for (child in children()) {
            child.traverseElements(includeSelf = true, visitor = visitor)
        }
    }

    private fun collectText(element: Element, chunks: MutableList<String>) {
        for (child in element.childNodes()) {
            when (child) {
                is TextNode -> {
                    val normalized = child.text().replace(Regex("\\s+"), " ").trim()
                    if (normalized.isNotEmpty()) {
                        chunks.add(normalized)
                    }
                }

                is Element -> collectText(child, chunks)
            }
        }
    }

    private fun collectWholeText(element: Element, builder: StringBuilder) {
        for (child in element.childNodes()) {
            when (child) {
                is TextNode -> builder.append(child.getWholeText())
                is Element -> collectWholeText(child, builder)
            }
        }
    }

    private fun collectData(element: Element, builder: StringBuilder) {
        for (child in element.childNodes()) {
            when (child) {
                is DataNode -> builder.append(child.getWholeData())
                is CDataNode -> builder.append(child.getWholeText())
                is Comment -> builder.append(child.getData())
                is Element -> collectData(child, builder)
            }
        }
    }

    private fun normalizeAttrKey(key: String): String = key.lowercase()

    private fun siblingElementsWithSelf(): List<Element> = parent()?.children() ?: listOf(this)

    private fun siblingsOfSameType(): List<Element> = siblingElementsWithSelf()
        .filter { it.tagName().equals(tagName(), ignoreCase = true) }

    internal fun appendParsedText(text: String): Element {
        if (text.isEmpty()) {
            return this
        }
        val resolvedText = if (childNodes.isEmpty() && tagName().lowercase() in stripLeadingNewlineTags) {
            text.removeLeadingParserNewline()
        } else {
            text
        }
        if (resolvedText.isEmpty()) {
            return this
        }
        if (tagName().lowercase() in rawTextTags) {
            appendChild(DataNode(resolvedText))
        } else {
            appendChild(TextNode(resolvedText))
        }
        return this
    }
}

private fun String.removeLeadingParserNewline(): String = when {
    startsWith("\r\n") -> substring(2)
    startsWith('\n') || startsWith('\r') -> substring(1)
    else -> this
}

internal fun renderNodeHtml(node: Node, builder: StringBuilder, depth: Int = 0) {
    when (node) {
        is Document -> renderDocumentNode(node, builder)
        is Element -> renderElementNode(node, builder, depth)
        else -> builder.append(node.outerHtml())
    }
}

private fun renderDocumentNode(document: Document, builder: StringBuilder) {
    val settings = document.outputSettings()
    val children = document.childNodes().filterRenderable(settings.prettyPrint())
    val multiline = settings.prettyPrint() && (
        settings.outline() ||
            children.size > 1 ||
            children.any {
                it is Element && (it.shouldFormatAsBlock(settings) || it.requiresPrettyPrintFormatting(settings))
            }
    )

    children.forEachIndexed { index, child ->
        if (multiline && index > 0) {
            builder.append('\n')
        }
        renderNodeHtml(child, builder, depth = 0)
    }
}

private fun renderElementNode(element: Element, builder: StringBuilder, depth: Int) {
    if (element.nodeName().startsWith("#")) {
        renderElementChildren(element, builder, depth - 1, addLeadingIndent = false)
        return
    }

    val settings = element.renderingSettings()
    val attrs = element.attributes().asMap().entries.joinToString(separator = "") { (key, value) ->
        val isBooleanHtmlAttribute = settings.syntax() == Document.OutputSettings.Syntax.HTML &&
            key in htmlBooleanAttributes &&
            (value.isEmpty() || value.equals(key, ignoreCase = true))
        if (value.isEmpty() || isBooleanHtmlAttribute) {
            " $key"
        } else {
            " $key=\"${element.escapeHtml(value, inAttribute = true)}\""
        }
    }
    val isVoidTag = settings.syntax() == Document.OutputSettings.Syntax.HTML &&
        element.tagName().lowercase() in htmlVoidTags
    val isXmlEmpty = settings.syntax() == Document.OutputSettings.Syntax.XML && element.childNodes().isEmpty()

    builder.append('<')
    builder.append(element.tagName())
    builder.append(attrs)
    if (isXmlEmpty) {
        builder.append(" />")
        return
    }
    builder.append('>')
    if (isVoidTag) {
        return
    }

    val multiline = renderElementChildren(element, builder, depth, addLeadingIndent = true)
    if (multiline) {
        builder.append('\n')
        appendIndent(builder, depth, settings)
    }

    builder.append("</")
    builder.append(element.tagName())
    builder.append('>')
}

private fun renderElementChildren(
    element: Element,
    builder: StringBuilder,
    depth: Int,
    addLeadingIndent: Boolean
): Boolean {
    val settings = element.renderingSettings()
    val preserveWhitespace = element.tagName().lowercase() in preserveWhitespaceTags
    val children = element.childNodes().filterRenderable(settings.prettyPrint() && !preserveWhitespace)
    val multiline = !preserveWhitespace && element.requiresPrettyPrintFormatting(settings)

    children.forEachIndexed { index, child ->
        if (multiline && (index > 0 || addLeadingIndent)) {
            builder.append('\n')
            appendIndent(builder, depth + 1, settings)
        }
        renderNodeHtml(child, builder, if (multiline) depth + 1 else depth)
    }

    return multiline && children.isNotEmpty()
}

private fun Element.requiresPrettyPrintFormatting(settings: Document.OutputSettings): Boolean {
    if (!settings.prettyPrint() || tagName().lowercase() in preserveWhitespaceTags) {
        return false
    }
    val children = childNodes().filterRenderable(prettyPrint = true)
    if (children.isEmpty()) {
        return false
    }
    if (settings.outline()) {
        return true
    }
    if (children.size > 1 && children.any { it is Element && it.shouldFormatAsBlock(settings) }) {
        return true
    }
    return children.any { child ->
        when (child) {
            is Element -> child.requiresPrettyPrintFormatting(settings)
            is Comment, is DocumentType, is XmlDeclaration -> true
            else -> false
        }
    }
}

private fun Element.shouldFormatAsBlock(settings: Document.OutputSettings): Boolean =
    settings.outline() || tagName().lowercase() in blockFormattingTags

private fun List<Node>.filterRenderable(prettyPrint: Boolean): List<Node> =
    if (!prettyPrint) this else filterNot { it is TextNode && it.isBlank() }

private fun appendIndent(builder: StringBuilder, depth: Int, settings: Document.OutputSettings) {
    val level = depth.coerceAtLeast(0)
    val padding = (level * settings.indentAmount()).coerceAtMost(settings.maxPaddingWidth())
    repeat(padding) {
        builder.append(' ')
    }
}
