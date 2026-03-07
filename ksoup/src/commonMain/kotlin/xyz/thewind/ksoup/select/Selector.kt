package xyz.thewind.ksoup.select

import xyz.thewind.ksoup.nodes.Element
import xyz.thewind.ksoup.nodes.DocumentType
import xyz.thewind.ksoup.nodes.XmlDeclaration
import xyz.thewind.ksoup.nodes.isFormControlEffectivelyDisabled

private fun matchesRegex(pattern: String, input: String): Boolean = try {
    Regex(pattern).containsMatchIn(input)
} catch (_: IllegalArgumentException) {
    false
}

object Selector {
    fun select(cssQuery: String, root: Element): Elements {
        val groups = QueryParser.parse(cssQuery)
        if (groups.isEmpty()) {
            return Elements()
        }

        val results = linkedSetOf<Element>()
        groups.forEach { steps ->
            results.addAll(selectGroup(steps, listOf(root)))
        }
        return Elements(results.toMutableList())
    }

    fun selectFirst(cssQuery: String, root: Element): Element? = select(cssQuery, root).first()

    fun selectFirst(cssQuery: String, roots: Iterable<Element>): Element? {
        roots.forEach { root ->
            selectFirst(cssQuery, root)?.let { return it }
        }
        return null
    }

    fun matches(cssQuery: String, element: Element): Boolean {
        val groups = QueryParser.parse(cssQuery)
        return groups.any { steps -> matchesGroup(steps, element) }
    }

    private fun selectGroup(steps: List<QueryStep>, roots: List<Element>): List<Element> {
        var current = roots
        for ((index, step) in steps.withIndex()) {
            val next = linkedSetOf<Element>()
            val includeSelf = index == 0
            current.forEach { element ->
                when (step.combinator) {
                    Combinator.DESCENDANT -> {
                        element.traverseElements(includeSelf = includeSelf) { candidate ->
                            if (step.selector.matches(candidate)) {
                                next.add(candidate)
                            }
                        }
                    }

                    Combinator.CHILD -> {
                        element.children().forEach { candidate ->
                            if (step.selector.matches(candidate)) {
                                next.add(candidate)
                            }
                        }
                    }

                    Combinator.ADJACENT_SIBLING -> {
                        element.nextElementSibling()?.let { candidate ->
                            if (step.selector.matches(candidate)) {
                                next.add(candidate)
                            }
                        }
                    }

                    Combinator.GENERAL_SIBLING -> {
                        element.nextElementSiblings().forEach { candidate ->
                            if (step.selector.matches(candidate)) {
                                next.add(candidate)
                            }
                        }
                    }
                }
            }
            current = next.toList()
        }
        return current
    }

    private fun matchesGroup(steps: List<QueryStep>, element: Element): Boolean {
        if (steps.isEmpty()) {
            return false
        }
        return matchesStep(steps, steps.lastIndex, element)
    }

    private fun matchesStep(steps: List<QueryStep>, stepIndex: Int, element: Element?): Boolean {
        if (element == null) {
            return false
        }
        val step = steps[stepIndex]
        if (!step.selector.matches(element)) {
            return false
        }
        if (stepIndex == 0) {
            return true
        }

        return when (step.combinator) {
            Combinator.DESCENDANT -> {
                var ancestor = element.parent()
                while (ancestor != null) {
                    if (matchesStep(steps, stepIndex - 1, ancestor)) {
                        return true
                    }
                    ancestor = ancestor.parent()
                }
                false
            }

            Combinator.CHILD -> matchesStep(steps, stepIndex - 1, element.parent())

            Combinator.ADJACENT_SIBLING -> matchesStep(steps, stepIndex - 1, element.previousElementSibling())

            Combinator.GENERAL_SIBLING -> {
                var sibling = element.previousElementSibling()
                while (sibling != null) {
                    if (matchesStep(steps, stepIndex - 1, sibling)) {
                        return true
                    }
                    sibling = sibling.previousElementSibling()
                }
                false
            }
        }
    }
}

internal enum class Combinator {
    DESCENDANT,
    CHILD,
    ADJACENT_SIBLING,
    GENERAL_SIBLING
}

internal data class QueryStep(
    val combinator: Combinator,
    val selector: SimpleSelector
)

internal data class SimpleSelector(
    val tagName: String? = null,
    val id: String? = null,
    val classes: Set<String> = emptySet(),
    val attrConditions: List<AttrCondition> = emptyList(),
    val pseudoConditions: List<PseudoCondition> = emptyList()
) {
    fun matches(element: Element): Boolean {
        if (tagName != null && !element.tagName().equals(tagName, ignoreCase = true)) {
            return false
        }
        if (id != null && element.id() != id) {
            return false
        }
        if (classes.any { it !in element.classNames() }) {
            return false
        }
        if (attrConditions.any { !it.matches(element) }) {
            return false
        }
        if (pseudoConditions.any { !it.matches(element) }) {
            return false
        }
        return true
    }
}

internal data class AttrCondition(
    val key: String,
    val operator: AttrOperator,
    val value: String = ""
) {
    fun matches(element: Element): Boolean {
        val attrValue = element.attr(key)
        return when (operator) {
            AttrOperator.EXISTS -> element.hasAttr(key)
            AttrOperator.EQUALS -> attrValue == value
            AttrOperator.NOT_EQUALS -> element.hasAttr(key) && attrValue != value
            AttrOperator.STARTS_WITH -> attrValue.startsWith(value)
            AttrOperator.ENDS_WITH -> attrValue.endsWith(value)
            AttrOperator.CONTAINS -> attrValue.contains(value)
            AttrOperator.MATCHES -> matchesRegex(value, attrValue)
        }
    }
}

internal enum class AttrOperator {
    EXISTS,
    EQUALS,
    NOT_EQUALS,
    STARTS_WITH,
    ENDS_WITH,
    CONTAINS,
    MATCHES
}

internal data class PseudoCondition(
    val operator: PseudoOperator,
    val value: String = ""
) {
    fun matches(element: Element): Boolean = when (operator) {
        PseudoOperator.CONTAINS -> element.text().contains(value, ignoreCase = true)
        PseudoOperator.CONTAINS_OWN -> element.ownText().contains(value, ignoreCase = true)
        PseudoOperator.CONTAINS_WHOLE_TEXT -> element.wholeText().contains(value)
        PseudoOperator.CONTAINS_WHOLE_OWN_TEXT -> element.wholeOwnText().contains(value)
        PseudoOperator.CONTAINS_DATA -> element.data().contains(value, ignoreCase = true)
        PseudoOperator.PARENT -> hasParentContent(element)
        PseudoOperator.HEADER -> element.normalName() in headerTags
        PseudoOperator.LANG -> matchesLang(element, value)
        PseudoOperator.FIRST -> element.elementSiblingIndex() == 0
        PseudoOperator.LAST -> element.parent()?.childrenSize()?.let { element.elementSiblingIndex() == it - 1 } ?: true
        PseudoOperator.EVEN -> element.elementSiblingIndex() % 2 == 0
        PseudoOperator.ODD -> element.elementSiblingIndex() % 2 == 1
        PseudoOperator.LT -> element.elementSiblingIndex() < value.toIntOrNull().orZero()
        PseudoOperator.GT -> element.elementSiblingIndex() > value.toIntOrNull().orZero()
        PseudoOperator.EQ -> element.elementSiblingIndex() == value.toIntOrNull().orZero()
        PseudoOperator.HAS -> element.select(value).isNotEmpty()
        PseudoOperator.HAS_TEXT -> element.hasText()
        PseudoOperator.CHECKED -> (
            element.normalName() == "option" && element.hasAttr("selected")
            ) || (
            element.normalName() == "input" && (
                element.hasAttr("checked") || element.attr("type").equals("checkbox", ignoreCase = true) && element.hasAttr("checked") ||
                    element.attr("type").equals("radio", ignoreCase = true) && element.hasAttr("checked")
                )
            )
        PseudoOperator.SELECTED -> element.normalName() == "option" && element.hasAttr("selected")
        PseudoOperator.DISABLED -> element.normalName() in formControlTags && isFormControlEffectivelyDisabled(element)
        PseudoOperator.ENABLED -> element.normalName() in formControlTags && !isFormControlEffectivelyDisabled(element)
        PseudoOperator.REQUIRED -> element.normalName() in requireableTags && element.hasAttr("required")
        PseudoOperator.OPTIONAL -> element.normalName() in requireableTags && !element.hasAttr("required")
        PseudoOperator.READ_ONLY -> isReadOnly(element)
        PseudoOperator.READ_WRITE -> isReadWritable(element)
        PseudoOperator.INPUT -> element.normalName() in inputLikeTags
        PseudoOperator.BUTTON -> (
            element.normalName() == "button" ||
                element.normalName() == "input" && buttonInputTypes.contains(normalizedType(element))
            )
        PseudoOperator.TEXT -> element.normalName() == "input" && normalizedType(element) in setOf("", "text")
        PseudoOperator.RADIO -> element.normalName() == "input" && normalizedType(element) == "radio"
        PseudoOperator.CHECKBOX -> element.normalName() == "input" && normalizedType(element) == "checkbox"
        PseudoOperator.FILE -> element.normalName() == "input" && normalizedType(element) == "file"
        PseudoOperator.PASSWORD -> element.normalName() == "input" && normalizedType(element) == "password"
        PseudoOperator.IMAGE -> element.normalName() == "input" && normalizedType(element) == "image"
        PseudoOperator.SUBMIT -> (
            element.normalName() == "button" && normalizedButtonType(element) == "submit"
            ) || (
            element.normalName() == "input" && normalizedType(element) == "submit"
            )
        PseudoOperator.RESET -> (
            element.normalName() == "button" && normalizedButtonType(element) == "reset"
            ) || (
            element.normalName() == "input" && normalizedType(element) == "reset"
            )
        PseudoOperator.IS -> Selector.matches(value, element)
        PseudoOperator.WHERE -> Selector.matches(value, element)
        PseudoOperator.NOT -> !Selector.matches(value, element)
        PseudoOperator.EMPTY -> element.childNodes().all { child ->
            when (child) {
                is xyz.thewind.ksoup.nodes.Comment -> true
                is xyz.thewind.ksoup.nodes.TextNode -> child.isBlank()
                is DocumentType -> true
                is XmlDeclaration -> true
                else -> false
            }
        }
        PseudoOperator.ROOT -> element.ownerDocument()?.documentElement() === element
        PseudoOperator.FIRST_CHILD -> element.isFirstChild()
        PseudoOperator.LAST_CHILD -> element.isLastChild()
        PseudoOperator.FIRST_OF_TYPE -> element.isFirstOfType()
        PseudoOperator.LAST_OF_TYPE -> element.isLastOfType()
        PseudoOperator.ONLY_CHILD -> element.isOnlyChild()
        PseudoOperator.ONLY_OF_TYPE -> element.isOnlyOfType()
        PseudoOperator.NTH_CHILD -> matchesNth(value, element.elementSiblingIndex() + 1)
        PseudoOperator.NTH_LAST_CHILD -> matchesNth(value, element.parent()?.childrenSize()?.minus(element.elementSiblingIndex()) ?: 0)
        PseudoOperator.NTH_OF_TYPE -> matchesNth(value, element.elementSiblingIndexOfType() + 1)
        PseudoOperator.NTH_LAST_OF_TYPE -> matchesNth(value, element.reverseElementSiblingIndexOfType() + 1)
        PseudoOperator.MATCHES -> matchesRegex(value, element.text())
        PseudoOperator.MATCHES_OWN -> matchesRegex(value, element.ownText())
        PseudoOperator.MATCHES_WHOLE_TEXT -> matchesRegex(value, element.wholeText())
        PseudoOperator.MATCHES_WHOLE_OWN_TEXT -> matchesRegex(value, element.wholeOwnText())
    }

    private fun Int?.orZero(): Int = this ?: 0

    private fun matchesNth(expression: String, index: Int): Boolean {
        if (index <= 0) {
            return false
        }

        val normalized = expression.trim().lowercase().replace(" ", "")
        if (normalized.isEmpty()) {
            return false
        }
        return when (normalized) {
            "odd" -> index % 2 == 1
            "even" -> index % 2 == 0
            else -> parseNthExpression(normalized)?.matches(index) ?: false
        }
    }

    private fun parseNthExpression(expression: String): NthExpression? {
        if ('n' !in expression) {
            return expression.toIntOrNull()?.let { NthExpression(0, it) }
        }

        val nIndex = expression.indexOf('n')
        val aPart = expression.substring(0, nIndex)
        val bPart = expression.substring(nIndex + 1)
        val a = when (aPart) {
            "", "+" -> 1
            "-" -> -1
            else -> aPart.toIntOrNull() ?: return null
        }
        val b = when {
            bPart.isEmpty() -> 0
            else -> bPart.toIntOrNull() ?: return null
        }
        return NthExpression(a, b)
    }
}

private data class NthExpression(
    val a: Int,
    val b: Int
) {
    fun matches(index: Int): Boolean {
        if (a == 0) {
            return index == b
        }
        val delta = index - b
        if (a > 0 && delta < 0) {
            return false
        }
        if (a < 0 && delta > 0) {
            return false
        }
        return delta % a == 0
    }
}

internal enum class PseudoOperator {
    CONTAINS,
    CONTAINS_OWN,
    CONTAINS_WHOLE_TEXT,
    CONTAINS_WHOLE_OWN_TEXT,
    CONTAINS_DATA,
    PARENT,
    HEADER,
    LANG,
    FIRST,
    LAST,
    EVEN,
    ODD,
    LT,
    GT,
    EQ,
    HAS,
    HAS_TEXT,
    CHECKED,
    SELECTED,
    DISABLED,
    ENABLED,
    REQUIRED,
    OPTIONAL,
    READ_ONLY,
    READ_WRITE,
    INPUT,
    BUTTON,
    TEXT,
    RADIO,
    CHECKBOX,
    FILE,
    PASSWORD,
    IMAGE,
    SUBMIT,
    RESET,
    IS,
    WHERE,
    NOT,
    EMPTY,
    ROOT,
    FIRST_CHILD,
    LAST_CHILD,
    FIRST_OF_TYPE,
    LAST_OF_TYPE,
    ONLY_CHILD,
    ONLY_OF_TYPE,
    NTH_CHILD,
    NTH_LAST_CHILD,
    NTH_OF_TYPE,
    NTH_LAST_OF_TYPE,
    MATCHES,
    MATCHES_OWN,
    MATCHES_WHOLE_TEXT,
    MATCHES_WHOLE_OWN_TEXT
}

private val formControlTags = setOf(
    "button", "fieldset", "input", "keygen", "object", "option", "optgroup", "select", "textarea"
)

private val headerTags = setOf("h1", "h2", "h3", "h4", "h5", "h6")
private val requireableTags = setOf("input", "select", "textarea")
private val inputLikeTags = setOf("button", "input", "keygen", "object", "select", "textarea")
private val buttonInputTypes = setOf("button", "submit", "reset")
private val readOnlyInputTypes = setOf(
    "", "text", "search", "url", "tel", "email", "password", "date", "month", "week",
    "time", "datetime-local", "number"
)

private fun normalizedType(element: Element): String = element.attr("type").trim().lowercase()

private fun normalizedButtonType(element: Element): String = element.attr("type").trim().lowercase().ifEmpty { "submit" }

private fun hasParentContent(element: Element): Boolean = element.childNodes().any { child ->
    when (child) {
        is xyz.thewind.ksoup.nodes.Comment -> false
        is xyz.thewind.ksoup.nodes.TextNode -> !child.isBlank()
        else -> true
    }
}

private fun matchesLang(element: Element, value: String): Boolean {
    val requested = value.trim().lowercase()
    if (requested.isEmpty()) {
        return false
    }
    val lang = element.lang().trim().lowercase()
    if (lang.isEmpty()) {
        return false
    }
    return lang == requested || lang.startsWith("$requested-")
}

private fun isReadOnly(element: Element): Boolean = when (element.normalName()) {
    "textarea" -> element.hasAttr("readonly")
    "input" -> normalizedType(element) in readOnlyInputTypes && element.hasAttr("readonly")
    else -> false
}

private fun isReadWritable(element: Element): Boolean = when (element.normalName()) {
    "textarea" -> !element.hasAttr("readonly") && !isFormControlEffectivelyDisabled(element)
    "input" -> normalizedType(element) in readOnlyInputTypes &&
        !element.hasAttr("readonly") &&
        !isFormControlEffectivelyDisabled(element)
    else -> false
}
