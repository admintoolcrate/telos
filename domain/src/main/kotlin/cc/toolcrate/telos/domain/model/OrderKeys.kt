package cc.toolcrate.telos.domain.model

/**
 * Fractional indexing: string keys that sort lexicographically, with a key always available
 * between any two. Reordering writes only the moved row, which merges cleanly across devices.
 *
 * Keys use the digits 0-9A-Za-z (ASCII order) and never end in '0', so there is always room
 * between two keys.
 */
object OrderKeys {
    private const val DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private const val BASE = 62

    /** The first key in an empty list. */
    const val FIRST = "V"

    /** A key strictly between [before] and [after]; either may be null for an open end. */
    fun between(before: String?, after: String?): String {
        require(before == null || after == null || before < after) { "Keys out of order: $before >= $after" }
        before?.let { validate(it) }
        after?.let { validate(it) }
        val sb = StringBuilder()
        var i = 0
        while (true) {
            val lo = before?.getOrNull(i)?.let { DIGITS.indexOf(it) } ?: 0
            // Once we've diverged below `after`, the upper bound is open.
            val hi = if (after != null && sb.toString() == after.substring(0, minOf(i, after.length)) && i < after.length) {
                DIGITS.indexOf(after[i])
            } else {
                BASE
            }
            if (hi - lo > 1) {
                sb.append(DIGITS[(lo + hi) / 2])
                return sb.toString()
            }
            // No room at this position: copy the lower digit and go one deeper.
            sb.append(DIGITS[lo])
            i++
            if (i > 512) error("Order key too long")
        }
    }

    /** [n] evenly spread keys after [before] (or from the start). */
    fun sequence(n: Int, before: String? = null): List<String> {
        val out = ArrayList<String>(n)
        var prev = before
        repeat(n) {
            val k = between(prev, null)
            out += k
            prev = k
        }
        return out
    }

    private fun validate(key: String) {
        require(key.isNotEmpty() && key.all { DIGITS.indexOf(it) >= 0 }) { "Bad order key: $key" }
    }
}
