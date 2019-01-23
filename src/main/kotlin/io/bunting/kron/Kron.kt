package io.bunting.kron

import java.lang.IllegalStateException
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ExecutorService
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

open class Kron(private val executorService: ExecutorService,
           private val clock: Clock,
           loggerAdapter: KronLoggerAdapter?,
           vararg taskSources: KronTaskSource) {
    constructor(executorService: ExecutorService, clock: Clock, vararg taskSources: KronTaskSource):
            this(executorService, clock, null, *taskSources)
    private val _startStopMonitor = ReentrantLock()
    private val taskSource: KronTaskSource = aggregate(*taskSources)
    private var timerThread: TimerThread? = null
    private val logger = KronLogger(loggerAdapter)

    fun start() {
        _startStopMonitor.withLock {
            if (timerThread != null) {
                throw IllegalStateException("Kron is already started.")
            }
            timerThread = TimerThread(clock, logger, this::spawnLauncher).apply {
                start()
            }
            logger.info { "Kron started." }
        }
    }

    fun stop() {
        _startStopMonitor.withLock {
            val stoppingThread = timerThread ?: throw IllegalStateException("Kron is not started.")
            this.timerThread = null
            stoppingThread.requestStop()
            stoppingThread.join()
            logger.info { "Kron stopped." }
        }
    }

    private fun spawnLauncher(launchTime: LocalDateTime) {
        logger.trace { "Spawning task launcher $launchTime" }
        executorService.submit(TaskLauncher(launchTime, taskSource, logger, this::spawnTask))
    }

    private fun spawnTask(taskId: String, task: KronTask) {
        logger.debug { "Spawning task $taskId" }
        executorService.submit(TaskRunner(taskId, task, logger))
    }
}

class SimpleKron private constructor(executorService: ExecutorService, clock: Clock, loggerAdapter: KronLoggerAdapter?, private val embeddedTaskSource: SimpleKronTaskSource) : Kron(executorService, clock, loggerAdapter, embeddedTaskSource) {
    constructor(executorService: ExecutorService, clock: Clock): this(executorService, clock, null, SimpleKronTaskSource())
    constructor(executorService: ExecutorService, clock: Clock, loggerAdapter: KronLoggerAdapter): this(executorService, clock, loggerAdapter, SimpleKronTaskSource())
    private val logger = KronLogger(loggerAdapter)

    fun addTask(pattern: String, task: ()->Unit): String {
        return this.addTask(pattern, KronTask.simple(task))
    }

    fun addTask(pattern: KronPattern, task: ()->Unit): String {
        return this.addTask(pattern, KronTask.simple(task))
    }

    fun addTask(pattern: String, task: KronTask): String {
        return this.addTask(KronPattern.parse(pattern), task)
    }

    fun addTask(pattern: KronPattern, task: KronTask): String {
        val taskId = embeddedTaskSource.add(pattern, task)
        logger.trace { "Added task $taskId with pattern $pattern" }
        return taskId
    }

    fun removeTask(taskId: String): Boolean {
        val result = embeddedTaskSource.remove(taskId)
        logger.trace {
            if (result) {
                "Removed task $taskId"
            } else {
                "Task $taskId did not exist, could not remove"
            }
        }
        return result
    }
}

internal class TimerThread(val clock: Clock,
                           val logger: KronLogger,
                           val spawnLauncher: (LocalDateTime)->Unit,
                           // these last two parameters are provided primarily for testing
                           val customIdealSleepTime: ((LocalDateTime)->Long)? = null): Thread("kron-timer") {
    @Volatile
    private var stopRequested = false

    internal fun requestStop() {
        logger.debug { "Timer thread stop requested." }
        stopRequested = true
    }

    private fun nowMinutes(): LocalDateTime = now().truncatedTo(ChronoUnit.MINUTES)

    override fun run() {
        logger.debug { "Timer thread starting." }
        var lastLaunched = nowMinutes().apply {
            logger.trace { "Launched for $this"}
            spawnLauncher.invoke(this)
        }
        while (true) {
            logger.trace { "Tick." }
            if (stopRequested) {
                logger.debug { "Timer thread stopping." }
                break
            }
            val idealSleepTime = idealSleepTime(lastLaunched)
            if (idealSleepTime > 0) {
                logger.trace { "Timer thread stopping for $idealSleepTime ms."}
                Thread.sleep(idealSleepTime)
            }
            val current = nowMinutes()
            logger.trace { "Current: $current, Last Launched: $lastLaunched"}
            while (current != lastLaunched) {
                logger.trace { "Current: $current, Last Launched: $lastLaunched, Launching..."}
                lastLaunched = lastLaunched.plusMinutes(1).apply {
                    logger.trace { "Launched for $this"}
                    spawnLauncher.invoke(this)
                }
            }
        }
    }

    private fun now(): LocalDateTime = LocalDateTime.now(clock)

    private fun idealSleepTime(lastLaunched: LocalDateTime) =
        customIdealSleepTime?.invoke(lastLaunched) ?:
        now().until(lastLaunched.plusMinutes(1), ChronoUnit.MILLIS)
}

internal class TaskLauncher(private val launchTime: LocalDateTime,
                            private val taskSource: KronTaskSource,
                            private val logger: KronLogger,
                            private val spawnTask: (String, KronTask)->Unit): Runnable {
    override fun run() {
        for ((taskId, pair) in taskSource.scheduledTasks().entries) {
            val (pattern, task) = pair
            if (pattern.matches(launchTime)) {
                spawnTask(taskId, task)
            }
        }
    }
}

internal class TaskRunner(private val taskId: String, private val task: KronTask,
                          private val logger: KronLogger
): Runnable {
    override fun run() {
        val context = object: KronTask.ExecutionContext {
            override fun hasStopBeenRequested(): Boolean = false
            override fun pauseIfRequested() {
                // do nothing
            }

            override fun updateCompleteness(completeness: Double) {
                // do nothing
            }

            override fun updateStatus(msg: String) {
                // do nothing
            }

        }
        try {
            task.execute(context)
        } catch (thrown: Throwable) {
            // it failed
        }
    }
}