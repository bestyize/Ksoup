package xyz.thewind.ksoup.nodes

internal fun createElementForTag(tagName: String, attributes: Attributes = Attributes()): Element =
    if (tagName.equals("form", ignoreCase = true)) FormElement(attributes) else Element(tagName, attributes)
