package xyz.thewind.ksoup.nodes

internal fun isFormControlEffectivelyDisabled(element: Element, stopAt: Element? = null): Boolean {
    if (element.hasAttr("disabled")) {
        return true
    }

    var current = element.parent()
    while (current != null && current !== stopAt) {
        if (current.normalName() == "fieldset" && current.hasAttr("disabled")) {
            val firstLegend = current.children().firstOrNull { it.normalName() == "legend" }
            if (firstLegend != null && isDescendantOf(element, firstLegend)) {
                current = current.parent()
                continue
            }
            return true
        }
        current = current.parent()
    }
    return false
}

private fun isDescendantOf(node: Element, ancestor: Element): Boolean {
    var current: Element? = node
    while (current != null) {
        if (current === ancestor) {
            return true
        }
        current = current.parent()
    }
    return false
}
