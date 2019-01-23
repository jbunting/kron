package io.bunting.kron

import com.nhaarman.mockitokotlin2.*
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
internal class TaskLauncherTest {

    @Test
    fun run(@Mock(name="task1") task1: KronTask, @Mock(name="pattern1") pattern1: KronPattern,
            @Mock(name="task2") task2: KronTask, @Mock(name="pattern2") pattern2: KronPattern,
            @Mock(name="task3") task3: KronTask, @Mock(name="pattern3") pattern3: KronPattern,
            @Mock(name="taskSource") taskSource: KronTaskSource,
            @Mock(name="spawnTask") spawnTask: (String,KronTask)->Unit) {
        val launchTime = LocalDateTime.parse("2019-01-05T12:34:00")
        whenever(taskSource.scheduledTasks()).thenReturn(mapOf(
            "t1" to (pattern1 to task1),
            "t2" to (pattern2 to task2),
            "t3" to (pattern3 to task3)
        ))
        whenever(pattern1.matches(launchTime)).thenReturn(true)
        whenever(pattern2.matches(launchTime)).thenReturn(true)
        whenever(pattern3.matches(launchTime)).thenReturn(false)

        val underTest = TaskLauncher(launchTime, taskSource, KronLogger(), spawnTask)

        underTest.run()

        verify(spawnTask) {
            1 * {invoke(any(), same(task1))}
            1 * {invoke(any(), same(task2))}
        }
        verify(taskSource, atLeastOnce()).scheduledTasks()
        verifyNoMoreInteractions(spawnTask, task1, task2, task3)
    }
}