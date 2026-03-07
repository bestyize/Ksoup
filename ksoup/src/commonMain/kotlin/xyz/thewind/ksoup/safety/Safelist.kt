package xyz.thewind.ksoup.safety

import xyz.thewind.ksoup.nodes.Attributes
import xyz.thewind.ksoup.nodes.Element

class Safelist() {
    private val allowedTags = linkedSetOf<String>()
    private val allowedAttributes = linkedMapOf<String, LinkedHashSet<String>>()
    private val enforcedAttributes = linkedMapOf<String, LinkedHashMap<String, String>>()
    private val allowedProtocols = linkedMapOf<String, LinkedHashMap<String, LinkedHashSet<String>>>()
    private var preserveRelativeLinksValue: Boolean = false

    constructor(copy: Safelist) : this() {
        allowedTags.addAll(copy.allowedTags)
        copy.allowedAttributes.forEach { (tag, attrs) -> allowedAttributes[tag] = LinkedHashSet(attrs) }
        copy.enforcedAttributes.forEach { (tag, attrs) -> enforcedAttributes[tag] = LinkedHashMap(attrs) }
        copy.allowedProtocols.forEach { (tag, attrs) ->
            allowedProtocols[tag] = LinkedHashMap<String, LinkedHashSet<String>>().also { target ->
                attrs.forEach { (attr, protocols) -> target[attr] = LinkedHashSet(protocols) }
            }
        }
        preserveRelativeLinksValue = copy.preserveRelativeLinksValue
    }

    fun addTags(vararg tags: String): Safelist {
        tags.forEach { allowedTags.add(normalize(it)) }
        return this
    }

    fun removeTags(vararg tags: String): Safelist {
        tags.forEach { allowedTags.remove(normalize(it)) }
        return this
    }

    fun addAttributes(tag: String, vararg attributes: String): Safelist {
        val key = normalize(tag)
        val values = allowedAttributes.getOrPut(key) { linkedSetOf() }
        attributes.forEach { values.add(normalize(it)) }
        return this
    }

    fun removeAttributes(tag: String, vararg attributes: String): Safelist {
        allowedAttributes[normalize(tag)]?.removeAll(attributes.map(::normalize).toSet())
        return this
    }

    fun addEnforcedAttribute(tag: String, attribute: String, value: String): Safelist {
        enforcedAttributes.getOrPut(normalize(tag)) { linkedMapOf() }[normalize(attribute)] = value
        return this
    }

    fun removeEnforcedAttribute(tag: String, attribute: String): Safelist {
        enforcedAttributes[normalize(tag)]?.remove(normalize(attribute))
        return this
    }

    fun addProtocols(tag: String, attribute: String, vararg protocols: String): Safelist {
        val tagProtocols = allowedProtocols.getOrPut(normalize(tag)) { linkedMapOf() }
        val attrProtocols = tagProtocols.getOrPut(normalize(attribute)) { linkedSetOf() }
        protocols.forEach { attrProtocols.add(normalize(it)) }
        return this
    }

    fun removeProtocols(tag: String, attribute: String, vararg removeProtocols: String): Safelist {
        allowedProtocols[normalize(tag)]?.get(normalize(attribute))?.removeAll(removeProtocols.map(::normalize).toSet())
        return this
    }

    fun preserveRelativeLinks(): Boolean = preserveRelativeLinksValue

    fun preserveRelativeLinks(preserve: Boolean): Safelist {
        preserveRelativeLinksValue = preserve
        return this
    }

    fun getEnforcedAttributes(tagName: String): Attributes = Attributes().also { attributes ->
        enforcedAttributes[normalize(tagName)].orEmpty().forEach { (key, value) ->
            attributes.put(key, value)
        }
    }

    fun isSafeTag(tag: String): Boolean = normalize(tag) in allowedTags

    fun isSafeAttribute(tagName: String, element: Element, attribute: String, value: String): Boolean {
        val normalizedTag = normalize(tagName)
        val normalizedAttribute = normalize(attribute)
        val allowedForTag = allowedAttributes[normalizedTag].orEmpty()
        val allowedForAll = allowedAttributes[allTag].orEmpty()
        if (normalizedAttribute !in allowedForTag && normalizedAttribute !in allowedForAll) {
            return false
        }

        val protocols = allowedProtocols[normalizedTag]?.get(normalizedAttribute)
            ?: allowedProtocols[allTag]?.get(normalizedAttribute)
        if (protocols.isNullOrEmpty()) {
            return true
        }

        val candidate = if (preserveRelativeLinksValue) {
            value.trim()
        } else {
            element.absUrl(normalizedAttribute).ifEmpty { value.trim() }
        }

        return testValidProtocol(candidate, protocols)
    }

    internal fun safeAttributeValue(tagName: String, element: Element, attribute: String, value: String): String {
        val normalizedTag = normalize(tagName)
        val normalizedAttribute = normalize(attribute)
        val protocols = allowedProtocols[normalizedTag]?.get(normalizedAttribute)
            ?: allowedProtocols[allTag]?.get(normalizedAttribute)
        if (protocols.isNullOrEmpty()) {
            return value
        }
        return if (preserveRelativeLinksValue) value else element.absUrl(normalizedAttribute).ifEmpty { value }
    }

    companion object {
        private const val allTag = ":all"

        fun none(): Safelist = Safelist()

        fun simpleText(): Safelist = Safelist()
            .addTags("b", "em", "i", "strong", "u")

        fun basic(): Safelist = Safelist()
            .addTags("a", "b", "blockquote", "br", "cite", "code", "dd", "dl", "dt", "em", "i", "li", "ol", "p", "pre", "q", "small", "span", "strike", "strong", "sub", "sup", "u", "ul")
            .addAttributes("a", "href")
            .addAttributes("blockquote", "cite")
            .addAttributes("q", "cite")
            .addProtocols("a", "href", "http", "https", "ftp", "mailto")
            .addProtocols("blockquote", "cite", "http", "https")
            .addProtocols("q", "cite", "http", "https")
            .addEnforcedAttribute("a", "rel", "nofollow")

        fun basicWithImages(): Safelist = basic()
            .addTags("img")
            .addAttributes("img", "align", "alt", "height", "src", "title", "width")
            .addProtocols("img", "src", "http", "https")

        fun relaxed(): Safelist = basicWithImages()
            .removeEnforcedAttribute("a", "rel")
            .addTags("caption", "col", "colgroup", "div", "h1", "h2", "h3", "h4", "h5", "h6", "table", "tbody", "td", "tfoot", "th", "thead", "tr")
            .addAttributes("col", "span", "width")
            .addAttributes("colgroup", "span", "width")
            .addAttributes("ol", "start", "type")
            .addAttributes("q", "cite")
            .addAttributes("table", "summary", "width")
            .addAttributes("td", "abbr", "axis", "colspan", "rowspan", "width")
            .addAttributes("th", "abbr", "axis", "colspan", "rowspan", "scope", "width")
            .addAttributes("ul", "type")
    }

    private fun testValidProtocol(value: String, protocols: Set<String>): Boolean {
        val normalizedValue = normalizeProtocolValue(value)
        if (normalizedValue.isEmpty()) {
            return true
        }
        val schemeSeparator = normalizedValue.indexOf(':')
        if (schemeSeparator <= 0) {
            return true
        }
        val scheme = normalizedValue.substring(0, schemeSeparator)
        return scheme in protocols
    }

    private fun normalizeProtocolValue(value: String): String = value.trim()
        .filterNot { it <= ' ' }
        .lowercase()

    private fun normalize(value: String): String = value.lowercase()
}
