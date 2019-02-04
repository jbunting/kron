package io.bunting.kron

import java.util.*

fun aggregate(vararg taskSources: KronTaskSource): KronTaskSource =
    if (taskSources.size == 1) {
        taskSources[0]
    } else {
        AggregateKronTaskSource(taskSources.asIterable())
    }

interface KronTaskSource {
    fun name(): String

    fun size(): Int

    fun scheduledTasks(): Iterable<TaskDefinition>
}

class TaskDefinition(val id: String, val pattern: KronPattern, val task: KronTask) {
    operator fun component1() = id
    operator fun component2() = pattern
    operator fun component3() = task
}

class SimpleKronTaskSource(private val name: String = "<simple>"): KronTaskSource {

    private val tasks = mutableMapOf<String, TaskDefinition>()

    override fun name(): String = name

    override fun size(): Int = tasks.size

    override fun scheduledTasks(): Iterable<TaskDefinition> =
        object: Iterable<TaskDefinition> by tasks.values {}

    fun add(pattern: KronPattern, task: KronTask): String {
        val id = name() + UUID.randomUUID().toString()
        tasks.put(id, TaskDefinition(id, pattern, task))
        return id
    }

    fun remove(id: String): Boolean = tasks.remove(id) != null
}

class AggregateKronTaskSource(private val taskSources: Iterable<KronTaskSource>, nameBase: String = "Aggregate"): KronTaskSource {
    private val name = "$nameBase [${taskSources.map { it.name() }.joinToString(",")}]"

    override fun name(): String = name

    override fun size(): Int = taskSources.map { it.size() }.sum()

    override fun scheduledTasks(): Iterable<TaskDefinition> = taskSources
        .flatMap { it.scheduledTasks() }

}