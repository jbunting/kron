package io.bunting.kron

import java.time.Clock
import java.time.LocalDateTime

class TestLogger: KronLoggerAdapter {
    private val clock = Clock.systemDefaultZone()
    override fun isLevelEnabled(level: KronLoggerLevel): Boolean = true
    override fun log(level: KronLoggerLevel, msg: String) = System.err.println("${LocalDateTime.now(clock)} :: $msg")
}