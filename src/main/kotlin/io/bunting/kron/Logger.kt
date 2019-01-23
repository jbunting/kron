package io.bunting.kron

import kotlin.reflect.KClass


enum class KronLoggerLevel {
    TRACE, DEBUG, INFO, WARN, ERROR
}

interface KronLoggerAdapter {
    fun isLevelEnabled(level: KronLoggerLevel): Boolean
    fun log(level: KronLoggerLevel, msg: String)
}

internal class KronLogger(adapter: KronLoggerAdapter? = null) {
    private val adapter = adapter ?: try {
            Slf4JKronLoggerAdapter(Kron::class)
        } catch (_: NoClassDefFoundError) {
            JULKronLoggerAdapter(Kron::class)
        }

    private inline fun doLog(level: KronLoggerLevel, msg: ()->String) {
        if (adapter.isLevelEnabled(level)) {
            adapter.log(level, msg())
        }
    }

    inline fun trace(msg: ()->String) = doLog(KronLoggerLevel.TRACE, msg)
    inline fun debug(msg: ()->String) = doLog(KronLoggerLevel.DEBUG, msg)
    inline fun info(msg: ()->String) = doLog(KronLoggerLevel.INFO, msg)
    inline fun warn(msg: ()->String) = doLog(KronLoggerLevel.WARN, msg)
    inline fun error(msg: ()->String) = doLog(KronLoggerLevel.ERROR, msg)
}

class JULKronLoggerAdapter(private val delegate: java.util.logging.Logger): KronLoggerAdapter {
    constructor(clazz: KClass<*>):this(java.util.logging.Logger.getLogger(clazz.qualifiedName))

    private fun convert(level: KronLoggerLevel): java.util.logging.Level =
        when(level) {
            KronLoggerLevel.TRACE -> java.util.logging.Level.FINEST
            KronLoggerLevel.DEBUG -> java.util.logging.Level.FINER
            KronLoggerLevel.INFO -> java.util.logging.Level.INFO
            KronLoggerLevel.WARN -> java.util.logging.Level.WARNING
            KronLoggerLevel.ERROR -> java.util.logging.Level.SEVERE
        }

    override fun isLevelEnabled(level: KronLoggerLevel): Boolean =
        delegate.isLoggable(convert(level))

    override fun log(level: KronLoggerLevel, msg: String) {
        delegate.log(convert(level), msg)
    }
}

class Slf4JKronLoggerAdapter(private val delegate: org.slf4j.Logger): KronLoggerAdapter {
    constructor(clazz: KClass<*>):this(org.slf4j.LoggerFactory.getLogger(clazz.java))

    override fun isLevelEnabled(level: KronLoggerLevel): Boolean =
        with(delegate) {
            when (level) {
                KronLoggerLevel.TRACE -> isTraceEnabled
                KronLoggerLevel.DEBUG -> isDebugEnabled
                KronLoggerLevel.INFO -> isInfoEnabled
                KronLoggerLevel.WARN -> isWarnEnabled
                KronLoggerLevel.ERROR -> isErrorEnabled
            }
        }

    override fun log(level: KronLoggerLevel, msg: String) =
        with(delegate) {
            when(level) {
                KronLoggerLevel.TRACE -> trace(msg)
                KronLoggerLevel.DEBUG -> debug(msg)
                KronLoggerLevel.INFO -> info(msg)
                KronLoggerLevel.WARN -> warn(msg)
                KronLoggerLevel.ERROR -> error(msg)
            }
        }
}