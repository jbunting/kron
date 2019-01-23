package io.bunting.kron

import java.lang.IllegalArgumentException
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference

/**
 * A utility for testing functionality that relies on the movement of time. This allows you to manually increment the
 * time as you progress through a test. It allows resetting but otherwise does not allow backwards movement of time.
 *
 * NOTE: This implementation does NOT comply with `Clock`'s restriction that implementations be immutable. By the
 * very nature of this implementation's purpose, mutability is necessary.
 */
class ManualClock(private val zoneId: ZoneId, private val initialInstant: Instant): Clock() {
    constructor(zoneId: ZoneId, initialTime: LocalDateTime):
            this(zoneId, initialTime.toInstant(zoneId.rules.getOffset(initialTime)))
    constructor(zone: String, initialTime: String):
            this(ZoneId.of(zone), LocalDateTime.parse(initialTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME))
    constructor(initialZonedTime: ZonedDateTime):
            this(initialZonedTime.zone, initialZonedTime.toLocalDateTime())
    constructor(initialZonedTime: String):
            this(ZonedDateTime.parse(initialZonedTime, DateTimeFormatter.ISO_ZONED_DATE_TIME))


    private val atomicInstant = AtomicReference<Instant>(initialInstant)

    var currentInstant: Instant
        get() = atomicInstant.get()
        set(value) {
            atomicInstant.getAndUpdate {
                if (value.isBefore(it)) {
                    throw IllegalArgumentException("Clock cannot move backwards.")
                } else {
                    value
                }
            }
        }

    var currentTime: LocalDateTime
        get() = LocalDateTime.ofInstant(currentInstant, zoneId)
        set(value) {
            currentInstant = value.toInstant(zoneId.rules.getOffset(value))
        }

    fun reset() {
        // should be the only case in which the clock can move backwards
        atomicInstant.set(initialInstant)
    }

    override fun withZone(zone: ZoneId): Clock = ManualClock(zone, currentInstant)
    override fun getZone(): ZoneId = zoneId
    override fun instant(): Instant = currentInstant

    // helpers
    fun incMinutes(minutes: Long = 1) {
        currentTime = currentTime.plusMinutes(minutes)
    }
    fun incrementHours(hours: Long = 1) {
        currentTime = currentTime.plusHours(hours)
    }
    fun incrementDays(days: Long = 1) {
        currentTime = currentTime.plusDays(days)
    }
}
