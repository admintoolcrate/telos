package cc.toolcrate.telos.domain.model

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class OrderKeysTest {
    @Test
    fun `a key fits between any two`() {
        val keys = mutableListOf(OrderKeys.between(null, null))
        val rnd = Random(7)
        repeat(2000) {
            val i = rnd.nextInt(keys.size + 1)
            val before = keys.getOrNull(i - 1)
            val after = keys.getOrNull(i)
            val k = OrderKeys.between(before, after)
            before?.let { assertTrue(it < k, "$it < $k") }
            after?.let { assertTrue(k < it, "$k < $it") }
            assertTrue(!k.endsWith("0"))
            keys.add(i, k)
        }
        assertTrue(keys == keys.sorted())
        assertTrue(keys.maxOf { it.length } < 40)
    }

    @Test
    fun `repeatedly inserting at the front and back`() {
        var first = OrderKeys.FIRST
        var last = OrderKeys.FIRST
        repeat(300) {
            val f = OrderKeys.between(null, first)
            assertTrue(f < first)
            first = f
            val l = OrderKeys.between(last, null)
            assertTrue(l > last)
            last = l
        }
    }

    @Test
    fun `sequence is increasing`() {
        val s = OrderKeys.sequence(100)
        assertTrue(s == s.sorted() && s.toSet().size == 100)
    }
}
