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

    fun scheduledTasks(): Map<String, Pair<KronPattern, KronTask>>
}

class SimpleKronTaskSource(private val name: String = "<simple>"): KronTaskSource {

    private val tasks = mutableMapOf<String, Pair<KronPattern, KronTask>>()

    override fun name(): String = name

    override fun size(): Int = tasks.size

    override fun scheduledTasks(): Map<String, Pair<KronPattern, KronTask>> =
        object: Map<String, Pair<KronPattern, KronTask>> by tasks {}

    fun add(pattern: KronPattern, task: KronTask): String {
        val id = name() + UUID.randomUUID().toString()
        tasks.put(id, pattern to task)
        return id
    }

    fun remove(id: String): Boolean = tasks.remove(id) != null
}

class AggregateKronTaskSource(private val taskSources: Iterable<KronTaskSource>, nameBase: String = "Aggregate"): KronTaskSource {
    private val name = "$nameBase [${taskSources.map { it.name() }.joinToString(",")}]"

    override fun name(): String = name

    override fun size(): Int = taskSources.map { it.size() }.sum()

    override fun scheduledTasks(): Map<String, Pair<KronPattern, KronTask>> = taskSources
        .flatMap { it.scheduledTasks().toList() }
        .toMap()

}