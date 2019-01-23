package io.bunting.kron

import org.junit.jupiter.api.Test
import org.mockito.Mock
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class KronTest {
    @Test
    fun testStartStop() {
        val clock = ManualClock("America/New_York", "2018-06-06T12:34:56")
        val executorService = Executors.newCachedThreadPool()
        val kron = SimpleKron(executorService, clock)
        kron.start()
        kron.stop()
    }
}