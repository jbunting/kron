package io.bunting.kron

interface KronTask {
    companion object {
        fun fromRunnable(runnable: Runnable): KronTask = RunnableKronTask(runnable::run)

        fun simple(runnable: () -> Unit): KronTask = RunnableKronTask(runnable)
    }

    val canBeStopped: Boolean
    val canBePaused: Boolean
    val supportsStatusTracking: Boolean
    val supportsCompletenessTracking: Boolean

    fun execute(ctx: ExecutionContext)

    interface ExecutionContext {
        fun hasStopBeenRequested(): Boolean
        fun pauseIfRequested()
        fun updateCompleteness(completeness: Double)
        fun updateStatus(msg: String)
    }
}

private class RunnableKronTask(private val runnable: () -> Unit): KronTask {
    override val canBeStopped = false
    override val canBePaused = false
    override val supportsStatusTracking = false
    override val supportsCompletenessTracking = false

    override fun execute(ctx: KronTask.ExecutionContext) {
        runnable.invoke()
    }
}
