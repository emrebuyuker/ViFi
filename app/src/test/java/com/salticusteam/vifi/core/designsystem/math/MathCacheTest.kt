package com.salticusteam.vifi.core.designsystem.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The two cache layers' keys and the byte-budgeted LRU behind them (SMT-04). */
class MathCacheTest {

    // Keys

    @Test
    fun `typeset keys are equal for the same formula size and mode`() {
        assertEquals(TypesetKey("""\frac{1}{2}""", 42.5f, false), TypesetKey("""\frac{1}{2}""", 42.5f, false))
        assertEquals(
            TypesetKey("""\frac{1}{2}""", 42.5f, false).hashCode(),
            TypesetKey("""\frac{1}{2}""", 42.5f, false).hashCode(),
        )
    }

    @Test
    fun `typeset keys differ by latex size or mode`() {
        val base = TypesetKey("x^2", 40f, false)
        assertNotEquals(base, TypesetKey("x^3", 40f, false))
        assertNotEquals(base, TypesetKey("x^2", 44f, false))
        assertNotEquals(base, TypesetKey("x^2", 40f, true))
    }

    @Test
    fun `bitmap keys add the colour to the typeset key`() {
        val typeset = TypesetKey("x^2", 40f, false)
        assertEquals(BitmapKey(typeset, 0xFF112233.toInt()), BitmapKey(typeset, 0xFF112233.toInt()))
        assertNotEquals(BitmapKey(typeset, 0xFF112233.toInt()), BitmapKey(typeset, 0xFFFFFFFF.toInt()))
        assertNotEquals(BitmapKey(typeset, 1), BitmapKey(TypesetKey("x^2", 40f, true), 1))
    }

    @Test
    fun `one layout serves several colours but each colour is its own bitmap`() {
        // Dark and light theme draw the same layout: same typeset key, two bitmap keys.
        val typeset = TypesetKey("e^{i\\pi}", 51f, true)
        assertEquals(setOf(typeset), setOf(typeset, typeset))
        assertEquals(2, setOf(BitmapKey(typeset, 0xFF000000.toInt()), BitmapKey(typeset, 0xFFFFFFFF.toInt())).size)
    }

    // LRU

    @Test
    fun `entries come back until the budget is exceeded then the eldest go first`() {
        val cache = SizedLruCache<String, Int>(maxWeight = 10) { it.toLong() }
        cache.put("a", 4)
        cache.put("b", 4)
        assertEquals(4, cache.get("a"))
        cache.put("c", 4) // 12 > 10: evicts the eldest by access order, "b" (a was just read)
        assertEquals(4, cache.get("a"))
        assertNull(cache.get("b"))
        assertEquals(4, cache.get("c"))
        assertEquals(8L, cache.totalWeight)
    }

    @Test
    fun `replacing an entry updates the weight`() {
        val cache = SizedLruCache<String, Int>(maxWeight = 10) { it.toLong() }
        cache.put("a", 6)
        cache.put("a", 3)
        assertEquals(3L, cache.totalWeight)
        assertEquals(1, cache.size)
    }

    @Test
    fun `an entry heavier than the whole budget is not stored and drops the old value`() {
        val cache = SizedLruCache<String, Int>(maxWeight = 10) { it.toLong() }
        cache.put("a", 3)
        cache.put("a", 11)
        assertNull(cache.get("a"))
        assertEquals(0L, cache.totalWeight)
    }

    @Test
    fun `an entry count budget with weight one`() {
        val cache = SizedLruCache<Int, String>(maxWeight = 3) { 1L }
        (1..5).forEach { cache.put(it, "v$it") }
        assertEquals(3, cache.size)
        assertNull(cache.get(1))
        assertNull(cache.get(2))
        assertEquals("v5", cache.get(5))
    }

    @Test
    fun `remove and clear release the weight`() {
        val cache = SizedLruCache<String, Int>(maxWeight = 10) { it.toLong() }
        cache.put("a", 4)
        cache.put("b", 5)
        cache.remove("a")
        assertEquals(5L, cache.totalWeight)
        cache.clear()
        assertEquals(0L, cache.totalWeight)
        assertEquals(0, cache.size)
    }
}
