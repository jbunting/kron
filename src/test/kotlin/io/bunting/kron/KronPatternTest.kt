package io.bunting.kron

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows
import org.opentest4j.TestAbortedException
import java.lang.Exception
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.Month

internal class KronPatternTest {


    @TestFactory
    fun matches(): Iterable<DynamicTest> = PatternTestBuilder {

        pattern("* 4 * * *") {
            builder {
                hours(4)
            }
            y(hour = 4, minute = 15)
            y(hour = 4, minute = 53)
            n(hour = 5, minute = 53)
        }

        pattern("12 * * * *") {
            builder {
                minutes(12)
            }
            y(hour = 5, minute = 12)
            y(hour = 4, minute = 12)
            n(hour = 5, minute = 53)
        }

        pattern("12,22 4,18 * * *") {
            builder {
                minutes(12)
                minutes(22)
                hours(4)
                hours(18)
            }
            listOf(4, 18).forEach { hour ->
                with(hour = hour) {
                    y(minute = 12)
                    y(minute = 22)
                    n(minute = 32)
                }
            }
            n(hour = 5, minute = 53)
        }

        pattern("*/15 4,18 * * *") {
            builder {
                minutesInterval(15)
                hours(4)
                hours(18)
            }
            listOf(4, 18).forEach { hour ->
                with(hour = hour) {
                    y(minute = 0)
                    y(minute = 15)
                    y(minute = 30)
                    y(minute = 45)
                }
            }
            n(hour = 5, minute = 53)
        }

        pattern("12/10 4,18 * * *") {
            builder {
                minutes(12).interval(10)
                hours(4)
                hours(18)
            }
            listOf(4, 18).forEach { hour ->
                with(hour = hour) {
                    y(minute = 12)
                    y(minute = 22)
                    y(minute = 32)
                }
            }
            n(hour = 5, minute = 53)
        }

        pattern("12-15 4,18 * * *") {
            builder {
                minutes(12, 15)
                hours(4)
                hours(18)
            }
            listOf(4, 18).forEach { hour ->
                with(hour = hour) {
                    y(minute = 12)
                    y(minute = 13)
                    y(minute = 14)
                    y(minute = 15)
                    n(minute = 16)
                    n(minute = 32)
                }
            }
            with(hour = 5) {
                n(minute = 12)
                n(minute = 14)
            }
        }
        pattern("12-25/4 4,18 * * *") {
            builder {
                minutes(12, 25).interval(4)
                hours(4)
                hours(18)
            }
            listOf(4, 18).forEach { hour ->
                with(hour = hour) {
                    y(minute = 12)
                    n(minute = 13)
                    n(minute = 14)
                    n(minute = 15)
                    y(minute = 16)
                    y(minute = 24)
                    n(minute = 28)
                    n(minute = 32)
                }
            }
            with(hour = 5) {
                n(minute = 12)
                n(minute = 24)
            }
        }

        pattern("* * 2 * *") {
            builder {
                daysOfMonth(2)
            }
            y(day = 2)
            y(day = 2, month = 6)
            n(day = 12, month = 6)
        }

        pattern("* * * * *") {
            builder {}
            y()
            y(1, 1, 1, 1, 1)
            y(2, 2, 2, 2, 2)
            y(2020, 12, 31, 23, 59)
        }

        listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC").forEachIndexed { idx, month ->
            pattern("* * * $month *") {
                builder {
                    months(idx + 1)
                }
                builder {
                    months(Month.of(idx + 1))
                }
                y(month = idx + 1)
                n(month = 12 - idx)
            }
        }

        listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT").forEachIndexed { idx, day ->
            pattern("* * * * $day") {
                builder {
                    daysOfWeek(idx)
                }
                builder {
                    daysOfWeek(DayOfWeek.of(if(idx == 0) 7 else idx))
                }
                y(year = 2019, month = 1, day = 20 + idx)
                n(year = 2019, month = 1, day = 21 + idx)
            }
            pattern("* * * * $idx") {
                builder {
                    daysOfWeek(idx)
                }
                builder {
                    daysOfWeek(DayOfWeek.of(if(idx == 0) 7 else idx))
                }
                y(year = 2019, month = 1, day = 20 + idx)
                n(year = 2019, month = 1, day = 21 + idx)
            }
        }
    }

    @TestFactory
    fun parseErrors() = PatternTestBuilder {
        patternErr("* * *")
        patternErr("61 * * * *")
        patternErr("* * * A *")
        patternErr("* * 0 * *")
    }

    @TestFactory
    fun builderErrors() = PatternTestBuilder {
        builderErr("minutes too high") {
            minutes(60)
        }
    }
}

class PatternTestBuilder private constructor(val testCases: MutableList<DynamicTest> = mutableListOf()): Iterable<DynamicTest> by testCases {
    constructor(tests: PatternTestBuilder.() -> Unit): this() {
        this.tests()
    }

    fun pattern(pattern: String, matchers: PatternMatches.() -> Unit) {
        testCases.add(DynamicTest.dynamicTest("'$pattern' parses") {
            assertDoesNotThrow<KronPattern> { KronPattern.parse(pattern) }
        })
        testCases.add(DynamicTest.dynamicTest("'$pattern' matches asString() output") {
            assertEquals(pattern, parsePattern(pattern).asString())

        })
        val matches = PatternMatches(pattern)
        matches.matchers()
    }

    fun patternErr(pattern: String) {
        PatternMatches(pattern).parseErr()
    }

    fun builderErr(label: String, definition: KronPattern.Builder.() -> Unit) {
        testCases.add(DynamicTest.dynamicTest("'$label' builder should fail") {
            assertThrows<KronPattern.PatternParseException> {
                KronPattern.build(definition)
            }
        })

    }



    private fun parsePattern(pattern: String): KronPattern =
        try {
            KronPattern.parse(pattern)
        } catch (e: Exception) {
            throw TestAbortedException("Pattern does not parse.")
        }

    inner class PatternMatches(private val pattern: String,
                               private val defaultYear: Int = 2019,
                               private val defaultMonth: Int = 1,
                               private val defaultDay: Int = 20,
                               private val defaultHour: Int = 1,
                               private val defaultMinute: Int = 10) {
        fun y(year: Int = defaultYear, month: Int = defaultMonth, day: Int = defaultDay, hour: Int = defaultHour, minute: Int = defaultMinute) {
            buildMatch(LocalDateTime.of(year, month, day, hour, minute), true)
        }

        fun n(year: Int = defaultYear, month: Int = defaultMonth, day: Int = defaultDay, hour: Int = defaultHour, minute: Int = defaultMinute) {
            buildMatch(LocalDateTime.of(year, month, day, hour, minute), false)
        }

        fun builder(definition: KronPattern.Builder.() -> Unit) {
            testCases.add(DynamicTest.dynamicTest("'$pattern' is produced by builder") {
                assertEquals(parsePattern(pattern), KronPattern.build(definition))
            })
        }

        fun parseErr() {
            testCases.add(DynamicTest.dynamicTest("'$pattern' should fail to parse") {
                assertThrows<KronPattern.PatternParseException> {
                    KronPattern.parse(pattern)
                }
            })
        }

        fun with(year: Int = defaultYear, month: Int = defaultMonth, day: Int = defaultDay, hour: Int = defaultHour, minute: Int = defaultMinute, matchers: PatternMatches.() -> Unit) {
            val nested = PatternMatches(pattern, year, month, day, hour, minute)
            nested.matchers()
        }

        private fun buildMatch(dateTime: LocalDateTime, result: Boolean) {
            val verb = if (result) "matches" else "doesn't match"
            testCases.add(DynamicTest.dynamicTest("'$pattern' $verb '$dateTime'") {
                assertEquals(result, parsePattern(pattern).matches(dateTime)) {
                    if (result) {
                        "$pattern should match $dateTime but doesn't."
                    } else {
                        "$pattern should not match $dateTime but it does."
                    }
                }
            })
        }
    }
}
