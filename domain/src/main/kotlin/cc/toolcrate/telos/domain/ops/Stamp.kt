package cc.toolcrate.telos.domain.ops

import cc.toolcrate.telos.domain.Ids
import java.time.Clock

/** Who and when, for every write: fills the [cc.toolcrate.telos.domain.model.Syncable] columns. */
class Stamp(val clock: Clock, val deviceId: String) {
    fun now(): Long = clock.millis()
    fun newId(): String = Ids.newId(clock)
}
