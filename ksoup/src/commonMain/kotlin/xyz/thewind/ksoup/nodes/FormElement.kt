package xyz.thewind.ksoup.nodes

import xyz.thewind.ksoup.Connection
import xyz.thewind.ksoup.select.Elements

class FormElement(
    attributes: Attributes = Attributes()
) : Element("form", attributes) {
    fun addElement(element: Element): FormElement {
        appendChild(element)
        return this
    }

    fun elements(): Elements = Elements(
        getAllElements()
            .filter { it !== this && it.normalName() in submittableTags }
            .toMutableList()
    )

    fun formData(): List<Connection.KeyVal> {
        val data = mutableListOf<Connection.KeyVal>()
        elements().forEach { element ->
            if (!isSubmittable(element)) {
                return@forEach
            }

            when (element.normalName()) {
                "select" -> addSelectData(element, data)
                "textarea" -> data += Connection.KeyVal.create(element.attr("name"), element.text())
                "input" -> addInputData(element, data)
                else -> data += Connection.KeyVal.create(element.attr("name"), element.`val`())
            }
        }
        return data
    }

    fun submit(): Connection {
        val document = ownerDocument()
        val actionUrl = absUrl("action").ifEmpty {
            attr("action").ifEmpty { document?.location().orEmpty() }
        }
        val connection = document?.connection()?.newRequest()
            ?: throw IllegalStateException("Form submit requires an owning document")
        if (actionUrl.isNotEmpty()) {
            connection.url(actionUrl)
        }
        connection.method(
            if (attr("method").equals("post", ignoreCase = true)) Connection.Method.POST else Connection.Method.GET
        )
        connection.data(formData())
        return connection
    }

    private fun isSubmittable(element: Element): Boolean {
        val name = element.attr("name")
        if (name.isBlank() || isFormControlEffectivelyDisabled(element, this)) {
            return false
        }
        return when (element.normalName()) {
            "input" -> {
                when (element.attr("type").lowercase()) {
                    "button", "submit", "reset", "image" -> false
                    "checkbox", "radio" -> element.hasAttr("checked")
                    else -> true
                }
            }
            "select", "textarea", "button" -> true
            else -> false
        }
    }

    private fun addInputData(element: Element, data: MutableList<Connection.KeyVal>) {
        val type = element.attr("type").lowercase()
        val value = when (type) {
            "checkbox", "radio" -> element.attr("value").ifEmpty { "on" }
            else -> element.`val`()
        }
        data += Connection.KeyVal.create(element.attr("name"), value)
    }

    private fun addSelectData(element: Element, data: MutableList<Connection.KeyVal>) {
        val options = element.select("option")
        val selected = options.filter { it.hasAttr("selected") }
        val effectiveOptions = if (selected.isNotEmpty()) selected else options.take(1)
        effectiveOptions.forEach { option ->
            data += Connection.KeyVal.create(element.attr("name"), option.attr("value").ifEmpty { option.text() })
        }
    }

    private companion object {
        val submittableTags = setOf("button", "input", "keygen", "object", "select", "textarea")
    }
}
