package xyz.thewind.ksoup.parser

import xyz.thewind.ksoup.nodes.Element
import xyz.thewind.ksoup.nodes.createElementForTag

internal object MarkupParserSupport {
    fun parseStartTag(rawTag: String): ParsedTag {
        var cursor = 0
        val trimmed = rawTag.trim()
        val selfClosing = trimmed.endsWith("/")
        val source = if (selfClosing) trimmed.dropLast(1).trimEnd() else trimmed

        while (cursor < source.length && !source[cursor].isWhitespace()) {
            cursor++
        }

        val tagName = source.substring(0, cursor)
        val element = createElementForTag(tagName)

        while (cursor < source.length) {
            while (cursor < source.length && source[cursor].isWhitespace()) {
                cursor++
            }
            if (cursor >= source.length) {
                break
            }

            val keyStart = cursor
            while (cursor < source.length && !source[cursor].isWhitespace() && source[cursor] != '=') {
                cursor++
            }
            val key = source.substring(keyStart, cursor)

            while (cursor < source.length && source[cursor].isWhitespace()) {
                cursor++
            }

            var value = ""
            if (cursor < source.length && source[cursor] == '=') {
                cursor++
                while (cursor < source.length && source[cursor].isWhitespace()) {
                    cursor++
                }
                if (cursor < source.length && (source[cursor] == '"' || source[cursor] == '\'')) {
                    val quote = source[cursor]
                    cursor++
                    val valueStart = cursor
                    while (cursor < source.length && source[cursor] != quote) {
                        cursor++
                    }
                    value = source.substring(valueStart, cursor)
                    if (cursor < source.length) {
                        cursor++
                    }
                } else {
                    val valueStart = cursor
                    while (cursor < source.length && !source[cursor].isWhitespace()) {
                        cursor++
                    }
                    value = source.substring(valueStart, cursor)
                }
            }

            if (key.isNotEmpty()) {
                element.attr(key, decodeHtml(value))
            }
        }

        return ParsedTag(tagName, element, selfClosing)
    }

    fun decodeHtml(value: String): String {
        if ('&' !in value) {
            return value
        }

        return buildString {
            var index = 0
            while (index < value.length) {
                val ch = value[index]
                if (ch != '&') {
                    append(ch)
                    index++
                    continue
                }

                val semi = value.indexOf(';', startIndex = index + 1)
                if (semi == -1) {
                    append(ch)
                    index++
                    continue
                }

                val entity = value.substring(index + 1, semi)
                val decoded = when {
                    entity == "amp" -> "&"
                    entity == "lt" -> "<"
                    entity == "gt" -> ">"
                    entity == "quot" -> "\""
                    entity == "nbsp" -> "\u00A0"
                    entity == "#39" || entity == "apos" -> "'"
                    entity.startsWith("#x", ignoreCase = true) -> entity.substring(2).toIntOrNull(16)?.toChar()?.toString()
                    entity.startsWith("#") -> entity.substring(1).toIntOrNull()?.toChar()?.toString()
                    else -> null
                }

                if (decoded == null) {
                    append('&')
                    index++
                } else {
                    append(decoded)
                    index = semi + 1
                }
            }
        }
    }

    fun tokenizeMarkup(source: String): List<String> {
        val tokens = mutableListOf<String>()
        var cursor = 0
        while (cursor < source.length) {
            while (cursor < source.length && source[cursor].isWhitespace()) {
                cursor++
            }
            if (cursor >= source.length) {
                break
            }

            if (source[cursor] == '"' || source[cursor] == '\'') {
                val quote = source[cursor]
                cursor++
                val start = cursor
                while (cursor < source.length && source[cursor] != quote) {
                    cursor++
                }
                tokens.add(source.substring(start, cursor))
                if (cursor < source.length) {
                    cursor++
                }
                continue
            }

            val start = cursor
            while (cursor < source.length && !source[cursor].isWhitespace()) {
                cursor++
            }
            tokens.add(source.substring(start, cursor))
        }
        return tokens
    }
}

internal data class ParsedTag(
    val name: String,
    val element: Element,
    val selfClosing: Boolean
)
