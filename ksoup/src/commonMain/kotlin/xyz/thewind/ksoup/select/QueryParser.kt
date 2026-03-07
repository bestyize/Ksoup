package xyz.thewind.ksoup.select

internal object QueryParser {
    fun parse(query: String): List<List<QueryStep>> {
        val groups = splitGroups(query)
        if (groups.isEmpty()) {
            return emptyList()
        }

        return groups.map(::parseGroup).filter { it.isNotEmpty() }
    }

    private fun splitGroups(query: String): List<String> {
        val groups = mutableListOf<String>()
        var bracketDepth = 0
        var parenDepth = 0
        var start = 0
        val source = query.trim()
        for (index in source.indices) {
            when (source[index]) {
                '[' -> bracketDepth++
                ']' -> bracketDepth--
                '(' -> parenDepth++
                ')' -> parenDepth--
                ',' -> if (bracketDepth == 0 && parenDepth == 0) {
                    val token = source.substring(start, index).trim()
                    if (token.isNotEmpty()) {
                        groups.add(token)
                    }
                    start = index + 1
                }
            }
        }
        val tail = source.substring(start).trim()
        if (tail.isNotEmpty()) {
            groups.add(tail)
        }
        return groups
    }

    private fun parseGroup(source: String): List<QueryStep> {
        val steps = mutableListOf<QueryStep>()
        var cursor = 0
        var combinator = Combinator.DESCENDANT

        while (cursor < source.length) {
            while (cursor < source.length && source[cursor].isWhitespace()) {
                cursor++
            }
            if (cursor >= source.length) {
                break
            }

            if (source[cursor] == '>' || source[cursor] == '+' || source[cursor] == '~') {
                combinator = when (source[cursor]) {
                    '>' -> Combinator.CHILD
                    '+' -> Combinator.ADJACENT_SIBLING
                    '~' -> Combinator.GENERAL_SIBLING
                    else -> Combinator.DESCENDANT
                }
                cursor++
                continue
            }

            val start = cursor
            var bracketDepth = 0
            var parenDepth = 0
            while (cursor < source.length) {
                val ch = source[cursor]
                if (ch == '[') {
                    bracketDepth++
                } else if (ch == ']') {
                    bracketDepth--
                } else if (ch == '(') {
                    parenDepth++
                } else if (ch == ')') {
                    parenDepth--
                } else if (bracketDepth == 0 && parenDepth == 0 && (ch.isWhitespace() || ch == '>' || ch == '+' || ch == '~')) {
                    break
                }
                cursor++
            }

            val token = source.substring(start, cursor).trim()
            if (token.isNotEmpty()) {
                steps.add(QueryStep(combinator, parseToken(token)))
            }
            combinator = Combinator.DESCENDANT
        }

        return steps
    }

    private fun parseToken(token: String): SimpleSelector {
        var cursor = 0
        var tagName: String? = null
        var id: String? = null
        val classes = mutableSetOf<String>()
        val attrConditions = mutableListOf<AttrCondition>()
        val pseudoConditions = mutableListOf<PseudoCondition>()

        fun readIdentifier(): String {
            val start = cursor
            while (cursor < token.length) {
                val ch = token[cursor]
                if (ch.isLetterOrDigit() || ch == '-' || ch == '_') {
                    cursor++
                } else {
                    break
                }
            }
            return token.substring(start, cursor)
        }

        if (cursor < token.length && (token[cursor].isLetterOrDigit() || token[cursor] == '*')) {
            val name = readIdentifier()
            if (name.isNotEmpty() && name != "*") {
                tagName = name.lowercase()
            }
        }

        while (cursor < token.length) {
            when (token[cursor]) {
                '#' -> {
                    cursor++
                    id = readIdentifier()
                }

                '.' -> {
                    cursor++
                    val className = readIdentifier()
                    if (className.isNotEmpty()) {
                        classes.add(className)
                    }
                }

                '[' -> {
                    cursor++
                    val start = cursor
                    while (cursor < token.length && token[cursor] != ']') {
                        cursor++
                    }
                    val content = token.substring(start, cursor)
                    if (cursor < token.length) {
                        cursor++
                    }
                    val condition = parseAttributeSelector(content)
                    if (condition != null) {
                        attrConditions.add(condition)
                    }
                }

                ':' -> {
                    cursor++
                    val pseudoName = readIdentifier()
                    val pseudoValue = if (cursor < token.length && token[cursor] == '(') {
                        cursor++
                        val start = cursor
                        var depth = 1
                        while (cursor < token.length && depth > 0) {
                            when (token[cursor]) {
                                '(' -> depth++
                                ')' -> depth--
                            }
                            if (depth > 0) {
                                cursor++
                            }
                        }
                        val value = token.substring(start, cursor).trim()
                        if (cursor < token.length && token[cursor] == ')') {
                            cursor++
                        }
                        value
                    } else {
                        ""
                    }
                    parsePseudoSelector(pseudoName, pseudoValue)?.let { pseudoConditions.add(it) }
                }

                else -> cursor++
            }
        }

        return SimpleSelector(
            tagName = tagName,
            id = id,
            classes = classes,
            attrConditions = attrConditions,
            pseudoConditions = pseudoConditions
        )
    }

    private fun parsePseudoSelector(name: String, value: String): PseudoCondition? = when (name) {
        "contains" -> PseudoCondition(PseudoOperator.CONTAINS, value)
        "containsOwn" -> PseudoCondition(PseudoOperator.CONTAINS_OWN, value)
        "containsWholeText" -> PseudoCondition(PseudoOperator.CONTAINS_WHOLE_TEXT, value)
        "containsWholeOwnText" -> PseudoCondition(PseudoOperator.CONTAINS_WHOLE_OWN_TEXT, value)
        "containsData" -> PseudoCondition(PseudoOperator.CONTAINS_DATA, value)
        "parent" -> PseudoCondition(PseudoOperator.PARENT)
        "header" -> PseudoCondition(PseudoOperator.HEADER)
        "lang" -> PseudoCondition(PseudoOperator.LANG, value)
        "first" -> PseudoCondition(PseudoOperator.FIRST)
        "last" -> PseudoCondition(PseudoOperator.LAST)
        "even" -> PseudoCondition(PseudoOperator.EVEN)
        "odd" -> PseudoCondition(PseudoOperator.ODD)
        "lt" -> PseudoCondition(PseudoOperator.LT, value)
        "gt" -> PseudoCondition(PseudoOperator.GT, value)
        "eq" -> PseudoCondition(PseudoOperator.EQ, value)
        "has" -> PseudoCondition(PseudoOperator.HAS, value)
        "hasText" -> PseudoCondition(PseudoOperator.HAS_TEXT)
        "checked" -> PseudoCondition(PseudoOperator.CHECKED)
        "selected" -> PseudoCondition(PseudoOperator.SELECTED)
        "disabled" -> PseudoCondition(PseudoOperator.DISABLED)
        "enabled" -> PseudoCondition(PseudoOperator.ENABLED)
        "required" -> PseudoCondition(PseudoOperator.REQUIRED)
        "optional" -> PseudoCondition(PseudoOperator.OPTIONAL)
        "readOnly" -> PseudoCondition(PseudoOperator.READ_ONLY)
        "readWrite" -> PseudoCondition(PseudoOperator.READ_WRITE)
        "input" -> PseudoCondition(PseudoOperator.INPUT)
        "button" -> PseudoCondition(PseudoOperator.BUTTON)
        "text" -> PseudoCondition(PseudoOperator.TEXT)
        "radio" -> PseudoCondition(PseudoOperator.RADIO)
        "checkbox" -> PseudoCondition(PseudoOperator.CHECKBOX)
        "file" -> PseudoCondition(PseudoOperator.FILE)
        "password" -> PseudoCondition(PseudoOperator.PASSWORD)
        "image" -> PseudoCondition(PseudoOperator.IMAGE)
        "submit" -> PseudoCondition(PseudoOperator.SUBMIT)
        "reset" -> PseudoCondition(PseudoOperator.RESET)
        "is" -> PseudoCondition(PseudoOperator.IS, value)
        "where" -> PseudoCondition(PseudoOperator.WHERE, value)
        "not" -> PseudoCondition(PseudoOperator.NOT, value)
        "empty" -> PseudoCondition(PseudoOperator.EMPTY)
        "root" -> PseudoCondition(PseudoOperator.ROOT)
        "first-child" -> PseudoCondition(PseudoOperator.FIRST_CHILD)
        "last-child" -> PseudoCondition(PseudoOperator.LAST_CHILD)
        "first-of-type" -> PseudoCondition(PseudoOperator.FIRST_OF_TYPE)
        "last-of-type" -> PseudoCondition(PseudoOperator.LAST_OF_TYPE)
        "only-child" -> PseudoCondition(PseudoOperator.ONLY_CHILD)
        "only-of-type" -> PseudoCondition(PseudoOperator.ONLY_OF_TYPE)
        "nth-child" -> PseudoCondition(PseudoOperator.NTH_CHILD, value)
        "nth-last-child" -> PseudoCondition(PseudoOperator.NTH_LAST_CHILD, value)
        "nth-of-type" -> PseudoCondition(PseudoOperator.NTH_OF_TYPE, value)
        "nth-last-of-type" -> PseudoCondition(PseudoOperator.NTH_LAST_OF_TYPE, value)
        "matches" -> PseudoCondition(PseudoOperator.MATCHES, value)
        "matchesOwn" -> PseudoCondition(PseudoOperator.MATCHES_OWN, value)
        "matchesWholeText" -> PseudoCondition(PseudoOperator.MATCHES_WHOLE_TEXT, value)
        "matchesWholeOwnText" -> PseudoCondition(PseudoOperator.MATCHES_WHOLE_OWN_TEXT, value)
        else -> null
    }

    private fun parseAttributeSelector(content: String): AttrCondition? {
        val operators = listOf("!=", "^=", "$=", "*=", "~=", "=")
        val trimmed = content.trim()
        operators.forEach { operator ->
            val index = trimmed.indexOf(operator)
            if (index >= 0) {
                val key = trimmed.substring(0, index).trim().lowercase()
                val value = trimmed.substring(index + operator.length).trim().trim('"', '\'')
                if (key.isEmpty()) {
                    return null
                }
                return AttrCondition(
                    key = key,
                    operator = when (operator) {
                        "=" -> AttrOperator.EQUALS
                        "!=" -> AttrOperator.NOT_EQUALS
                        "^=" -> AttrOperator.STARTS_WITH
                        "$=" -> AttrOperator.ENDS_WITH
                        "*=" -> AttrOperator.CONTAINS
                        "~=" -> AttrOperator.MATCHES
                        else -> AttrOperator.EXISTS
                    },
                    value = value
                )
            }
        }

        val key = trimmed.lowercase()
        if (key.isEmpty()) {
            return null
        }
        return AttrCondition(key = key, operator = AttrOperator.EXISTS)
    }
}
