package io.bunting.kron

import com.nhaarman.mockitokotlin2.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit
import java.util.concurrent.Phaser

@ExtendWith(MockitoExtension::class)
internal class TimerThreadTest {
    @Test
    fun testLogic(@Mock(name="launcher") launcher: (LocalDateTime)->Unit) {
        val initialTime = LocalDateTime.parse("2018-06-06T12:34:56")
        val initialTrunc = initialTime.truncatedTo(ChronoUnit.MINUTES)

        val phaser = Phaser()
        val clock = ManualClock(ZoneId.of("America/New_York"), initialTime)
        phaser.register()
        val underTest = TimerThread(clock, KronLogger(TestLogger()), launcher, {5})

        fun expectLaunch(dateTime: LocalDateTime, times: Int = 1) {
            verify(launcher, timeout(1000).times(times)).invoke(dateTime)
            reset(launcher)
        }

        underTest.start()

        expectLaunch(initialTrunc)

        clock.incMinutes(1)
        System.err.println(">>> Incremented to: ${clock.currentInstant}")

        expectLaunch(initialTrunc.plusMinutes(1))

        underTest.requestStop()
        assertTimeout(Duration.ofSeconds(5)) {
            underTest.join()
        }

        clock.incMinutes(1)
        System.err.println(">>> Incremented to: ${clock.currentInstant}")
        // give it a sec
        Thread.sleep(250)
        verifyNoMoreInteractions(launcher)
    }

    @Test
    fun idealSleepTime() {

    }

    @Test
    fun understandPhaser() {
        val phaser = object : Phaser() {
            override fun onAdvance(phase: Int, registeredParties: Int): Boolean {
                println("Advanced: $phase / $registeredParties")
                return false
            }
        }

        println("register: ${phaser.register()}")
        println("arrive and deregister: ${phaser.arriveAndDeregister()}")
        println("register: ${phaser.register()}")
        println("arrive and deregister: ${phaser.arriveAndDeregister()}")
    }

}