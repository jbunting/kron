package io.bunting.kron

import com.nhaarman.mockitokotlin2.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
internal class TaskRunnerTest {
    @Test
    fun testTaskExecuted(@Mock(name="task1") task: KronTask) {
        val underTest = TaskRunner("testTask", task, KronLogger())
        underTest.run()

        verify(task) {
            1 * { execute(any()) }
        }
    }
}