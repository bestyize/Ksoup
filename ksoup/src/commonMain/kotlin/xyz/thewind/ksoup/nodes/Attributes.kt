package xyz.thewind.ksoup.nodes

class Attributes internal constructor(
    private val backing: LinkedHashMap<String, String> = linkedMapOf()
) : Iterable<Map.Entry<String, String>> {
    private val datasetView: MutableMap<String, String> = DatasetView()

    fun size(): Int = backing.size

    fun isEmpty(): Boolean = backing.isEmpty()

    fun hasKey(key: String): Boolean = normalizeKey(key) in backing

    operator fun get(key: String): String = backing[normalizeKey(key)].orEmpty()

    operator fun set(key: String, value: String) {
        backing[normalizeKey(key)] = value
    }

    fun put(key: String, value: String): Attributes {
        this[key] = value
        return this
    }

    fun remove(key: String): Attributes {
        backing.remove(normalizeKey(key))
        return this
    }

    fun clear() {
        backing.clear()
    }

    fun asMap(): Map<String, String> = backing.toMap()

    fun dataset(): MutableMap<String, String> = datasetView

    override fun iterator(): Iterator<Map.Entry<String, String>> = backing.entries.iterator()

    internal fun putAll(values: Map<String, String>) {
        values.forEach { (key, value) -> backing[normalizeKey(key)] = value }
    }

    internal fun copy(): Attributes = Attributes(LinkedHashMap(backing))

    private fun normalizeKey(key: String): String = key.lowercase()

    private inner class DatasetView : MutableMap<String, String> {
        override val size: Int
            get() = backing.keys.count { it.startsWith("data-") }

        override fun isEmpty(): Boolean = backing.keys.none { it.startsWith("data-") }

        override fun containsKey(key: String): Boolean = backing.containsKey(dataKey(key))

        override fun containsValue(value: String): Boolean =
            backing.any { (key, current) -> key.startsWith("data-") && current == value }

        override fun get(key: String): String? = backing[dataKey(key)]

        override fun clear() {
            backing.keys.filter { it.startsWith("data-") }.toList().forEach(backing::remove)
        }

        override fun put(key: String, value: String): String? = backing.put(dataKey(key), value)

        override fun putAll(from: Map<out String, String>) {
            from.forEach { (key, value) -> put(key, value) }
        }

        override fun remove(key: String): String? = backing.remove(dataKey(key))

        override val entries: MutableSet<MutableMap.MutableEntry<String, String>>
            get() = backing.entries
                .filter { it.key.startsWith("data-") }
                .mapTo(linkedSetOf()) { DatasetEntry(it) }

        override val keys: MutableSet<String>
            get() = backing.keys
                .filter { it.startsWith("data-") }
                .mapTo(linkedSetOf()) { it.removePrefix("data-") }

        override val values: MutableCollection<String>
            get() = backing.entries
                .filter { it.key.startsWith("data-") }
                .mapTo(mutableListOf()) { it.value }

        private fun dataKey(key: String): String = "data-${normalizeKey(key)}"
    }

    private inner class DatasetEntry(
        private val entry: MutableMap.MutableEntry<String, String>
    ) : MutableMap.MutableEntry<String, String> {
        override val key: String
            get() = entry.key.removePrefix("data-")

        override val value: String
            get() = entry.value

        override fun setValue(newValue: String): String = entry.setValue(newValue)
    }
}
