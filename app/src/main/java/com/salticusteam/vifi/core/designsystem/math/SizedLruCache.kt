package com.salticusteam.vifi.core.designsystem.math

// Added in 4.2.0 — a small thread-safe LRU cache bounded by a weight (bytes or entries), testable on the JVM.
/**
 * `android.util.LruCache` semantics (access-ordered, evicts the eldest entries once the total [weigh]t exceeds
 * [maxWeight]) without the Android dependency, so the eviction rules are unit-testable. An entry heavier than the
 * whole budget is not stored.
 */
internal class SizedLruCache<K : Any, V : Any>(
    private val maxWeight: Long,
    private val weigh: (V) -> Long,
) {
    private val entries = LinkedHashMap<K, V>(16, 0.75f, true)
    private var weight = 0L

    @Synchronized
    fun get(key: K): V? = entries[key]

    @Synchronized
    fun put(key: K, value: V) {
        val added = weigh(value)
        entries.remove(key)?.let { weight -= weigh(it) }
        if (added > maxWeight) return
        entries[key] = value
        weight += added
        val iterator = entries.entries.iterator()
        while (weight > maxWeight && iterator.hasNext()) {
            val eldest = iterator.next()
            weight -= weigh(eldest.value)
            iterator.remove()
        }
    }

    @Synchronized
    fun remove(key: K) {
        entries.remove(key)?.let { weight -= weigh(it) }
    }

    @Synchronized
    fun clear() {
        entries.clear()
        weight = 0L
    }

    @get:Synchronized
    val size: Int get() = entries.size

    @get:Synchronized
    val totalWeight: Long get() = weight
}

// Added in 4.2.0 — key of the typeset cache: a formula at a font size in a mode (colour does not change the layout).
internal data class TypesetKey(val latex: String, val sizeBits: Int, val display: Boolean) {
    constructor(latex: String, sizePx: Float, display: Boolean) : this(latex, sizePx.toBits(), display)
}

// Added in 4.2.0 — key of the drawn-bitmap cache: the typeset key plus the colour (ARGB) the glyphs are drawn in.
internal data class BitmapKey(val typeset: TypesetKey, val argb: Int)
