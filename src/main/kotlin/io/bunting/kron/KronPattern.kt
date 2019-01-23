package io.bunting.kron

import com.github.michaelbull.result.*
import java.lang.Exception
import java.lang.IllegalStateException
import java.lang.NumberFormatException
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.Month
import java.time.temporal.ChronoField

class KronPattern private constructor(
    private val matchers: FieldStructure<List<FieldMatcher>>
) {
    companion object {
        @Throws(PatternParseException::class)
        fun parse(pattern: String): KronPattern {
            return doParse(pattern).getOrElse { throw PatternParseException(it) }
        }

        private fun doParse(pattern: String): Result<KronPattern, PatternValidationFailure> {
            val parts = pattern.split(" ")
            if (parts.size != 5) {
                return Err(PatternValidationFailure(pattern, "Pattern does not have 5 space-separated sections", listOf()))
            }
            fun parsePart(field: PatternField, part: String): Result<List<FieldMatcher>, PartPatternValidationFailure> {
                fun parseField(fieldText: String): Result<FieldMatcher, FieldValidationFailure> {
                    var rest = fieldText
                    val intervalString = rest.substringAfterLast("/", "").ifBlank { null }
                    rest = rest.substringBeforeLast("/", rest)
                    val rangeString = rest.substringAfterLast("-", "").ifBlank { null }
                    rest = rest.substringBeforeLast("-", rest)

                    // TODO: parse ranges and intervals
                    return try {
                        val (value, representation) = field.parseValue(rest)
                        FieldMatcher.create(
                            field,
                            value,
                            rangeString?.let { field.parseNumber(it) },
                            intervalString?.toInt(),
                            representation)
                    } catch (e: NumberFormatException) {
                        Err(FieldValidationFailure(fieldText, listOf("Number format error: ${e.message}")))
                    }
                }
                if ("*" == part) {
                    return Ok(listOf())
                }

                val (values, errs) = part.split(",").map { parseField(it) }.partition()
                return if (errs.isNotEmpty()) {
                    Err(PartPatternValidationFailure(field, part, errs))
                } else {
                    Ok(values)
                }
            }

            return FieldStructure { parts[it.ordinal] }.map { field, part -> parsePart(field, part) }.mapEither(
                { KronPattern(it)},
                { PatternValidationFailure(pattern, "Pattern sections did not parse.", it.values.filterNotNull()) }
            )
        }

        @Throws(PatternParseException::class)
        fun build(definition: Builder.() -> Unit): KronPattern {
            return doBuild(definition).getOrElse { throw PatternParseException(it) }
        }

        private fun doBuild(definition: Builder.() -> Unit): Result<KronPattern, PatternValidationFailure> {
            val builder = Builder()
            builder.definition()
            return builder.build()
        }
    }

    fun matches(moment: LocalDateTime): Boolean {
        return matchers.pairs.all { (field, matchers) ->
            val value = field.getValue(moment)
            matchers.isEmpty() || matchers.any { it.matches(value) }
        }
    }

    fun asString(): String =
        matchers.asString {
            if (it.isEmpty()) {
                "*"
            } else {
                it.map { it.asString() }.joinToString(",")
            }
        }

    /**
     * Returns a string representation of the object.
     */
    override fun toString(): String = "KronPattern[ ${asString()} ]"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KronPattern

        if (matchers != other.matchers) return false

        return true
    }

    override fun hashCode(): Int {
        return matchers.hashCode()
    }


    class Builder internal constructor() {
        private val builders = FieldStructure { mutableListOf<FieldBuilder>() }
        fun List<FieldBuilder>.asString() = if (isEmpty()) "*" else this.map { it.asString() }.joinToString(",")

        private fun field(fieldList: MutableList<FieldBuilder>, value: Int, range: Int? = null): IntervalSpecifier =
            IntervalSpecifier(FieldBuilder(value).also { fb ->
                fb.range = range
                fieldList.add(fb)
            })

        fun minutes(value: Int, range: Int? = null) = field(builders.minutes, value, range)
        fun minutesInterval(value: Int) = field(builders.minutes, 0).interval(value)

        fun hours(value: Int, range: Int? = null) = field(builders.hours, value, range)
        fun hoursInterval(value: Int) = field(builders.hours, 0).interval(value)

        fun daysOfMonth(value: Int, range: Int? = null) = field(builders.daysOfMonth, value, range)
        fun daysOfMonthInterval(value: Int) = field(builders.daysOfMonth, 0).interval(value)

        fun months(value: Int, range: Int? = null) = field(builders.months, value, range)
        fun months(value: Month, range: Month? = null) = field(builders.months, value.value, range?.value)
        fun monthsInterval(value: Int) = field(builders.months, 0).interval(value)

        fun daysOfWeek(value: Int, range: Int? = null) = field(builders.daysOfWeek, value, range)
        fun daysOfWeek(value: DayOfWeek, range: DayOfWeek? = null) = field(builders.daysOfWeek, value.value % 7, range?.let { it.value % 7 })
        fun daysOfWeekInterval(value: Int) = field(builders.daysOfWeek, 0).interval(value)

        internal fun build(): Result<KronPattern, PatternValidationFailure> {
            fun FieldBuilder.build(field: PatternField) = FieldMatcher.create(field, value, range, interval, null)
            fun build(field: PatternField, builderList: List<FieldBuilder>): Result<List<FieldMatcher>, PartPatternValidationFailure> {
                val (values, errs) = builderList.map { it.build(field) }.partition()
                return if (errs.isNotEmpty()) {
                    Err(PartPatternValidationFailure(field, builderList.asString(), errs))
                } else {
                    Ok(values)
                }
            }

            return builders.map { field, b -> build(field, b) }.mapEither(
                { KronPattern(it) },
                { PatternValidationFailure(builders.asString { it.asString() }, "Pattern failed to validate.", it.values.filterNotNull()) }
            )
        }

        open inner class IntervalSpecifier(private val fb: FieldBuilder) {
            fun interval(value: Int) {
                fb.interval = value
            }
        }

        inner class FieldBuilder(var value: Int) {
            var range: Int? = null
            var interval: Int? = null

            fun asString(): String {
                val rangePart = range?.let { "-$it" } ?: ""
                val intervalPart = interval?.let { "/$it"} ?: ""
                return "$value$rangePart$intervalPart"
            }
        }
    }

    internal data class PatternValidationFailure(val pattern: String, val error: String, val partErrors: List<PartPatternValidationFailure>)
    internal data class PartPatternValidationFailure(val field: PatternField, val pattern: String, val fieldErrors: List<FieldValidationFailure>)
    internal data class FieldValidationFailure(val pattern: String, val errors: List<String>)

    class PatternParseException internal constructor(failure: PatternValidationFailure): Exception(failure.toString())

    private class FieldMatcher private constructor(
        val field: PatternField,
        val value: Int,
        val rangeEnd: Int?,
        val interval: Int?,
        val representation: FieldRepresentation?
    ) {
        companion object {
            fun create(field: PatternField, value: Int, rangeEnd: Int?, interval: Int?, representation: FieldRepresentation?, originalPattern: String? = null): Result<FieldMatcher, FieldValidationFailure> {

                val errors = validate(field, value, rangeEnd)
                val matcher = FieldMatcher(field, value, rangeEnd, interval, representation)
                return if (errors.isNotEmpty()) {
                    Err(FieldValidationFailure(originalPattern ?: matcher.asString(), errors))
                } else {
                    Ok(matcher)
                }
            }

            private fun validate(
                field: PatternField,
                value: Int,
                rangeEnd: Int?
            ): MutableList<String> {
                val errors = mutableListOf<String>()
                fun checkMinMax(label: String, test: Int) {
                    if (test < field.min) {
                        errors.add("$label [$value] is less than min of [${field.min}] for ${field.name} field")
                    }
                    if (test > field.max) {
                        errors.add("$label [$value] is greater than max of [${field.max}] for ${field.name} field")
                    }
                }
                checkMinMax("Value", value)
                if (rangeEnd != null) {
                    checkMinMax("Range", rangeEnd)
                }
                return errors
            }
        }

        fun matches(testValue: Int): Boolean =
            if (rangeEnd == null) {
                if (interval == null) {
                    testValue == value
                } else {
                    (testValue >= value) && (testValue % interval == value % interval)
                }
            } else {
                if (interval == null) {
                    testValue >= value && testValue <= rangeEnd
                } else {
                    (testValue % interval == value % interval) && (testValue >= value && testValue <= rangeEnd)
                }
            }

        fun asString(): String {
            val valuePart = field.asString(value, representation)
            val rangePart = rangeEnd?.let { "-$it" } ?: ""
            val intervalPart = interval?.let { "/$it"} ?: ""

            return "$valuePart$rangePart$intervalPart"
        }

        /**
         * Returns a string representation of the object.
         */
        override fun toString(): String = "${field.name}-> ${asString()}"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as FieldMatcher

            if (field != other.field) return false
            if (value != other.value) return false
            if (rangeEnd != other.rangeEnd) return false
            if (interval != other.interval) return false

            return true
        }

        override fun hashCode(): Int {
            var result = field.hashCode()
            result = 31 * result + value
            result = 31 * result + (rangeEnd ?: 0)
            result = 31 * result + (interval ?: 0)
            return result
        }
    }
}

internal enum class PatternField(val min: Int, val max: Int, private val chronoField: ChronoField, private val alphaMap: Map<String, Int>? = null) {
    MINUTE(0, 59, ChronoField.MINUTE_OF_HOUR),
    HOUR(0, 23, ChronoField.HOUR_OF_DAY),
    DAY_OF_MONTH(1, 31, ChronoField.DAY_OF_MONTH),
    MONTH(1, 12, ChronoField.MONTH_OF_YEAR, mapOf(
        "JAN" to 1,
        "FEB" to 2,
        "MAR" to 3,
        "APR" to 4,
        "MAY" to 5,
        "JUN" to 6,
        "JUL" to 7,
        "AUG" to 8,
        "SEP" to 9,
        "OCT" to 10,
        "NOV" to 11,
        "DEC" to 12
    )) ,
    DAY_OF_WEEK(0, 6, ChronoField.DAY_OF_WEEK, mapOf(
        "SUN" to 0,
        "MON" to 1,
        "TUE" to 2,
        "WED" to 3,
        "THU" to 4,
        "FRI" to 5,
        "SAT" to 6
    )) {
        override fun getValue(moment: LocalDateTime): Int =
            super.getValue(moment) % 7
    };

    open fun parseNumber(value: String): Int =
        value.toInt()

    open fun parseValue(value: String): Pair<Int, FieldRepresentation> =
        if (value == "*") {
            0 to FieldRepresentation.STAR
        } else if (alphaMap != null && value in alphaMap.keys) {
            alphaMap.getValue(value) to FieldRepresentation.ALPHA
        } else {
            parseNumber(value) to FieldRepresentation.NUMERICAL
        }

    open fun getValue(moment: LocalDateTime): Int = moment.get(chronoField)
    open fun asString(value: Int, numerical: FieldRepresentation?): String =
            when (numerical) {
                FieldRepresentation.STAR -> "*"
                FieldRepresentation.NUMERICAL -> value.toString()
                FieldRepresentation.ALPHA -> alphaMap?.entries?.let {
                    it.firstOrNull { it.value == value }?.key
                } ?: throw IllegalStateException("No Alpha Representation of $name")
                null -> if (alphaMap == null) {
                    asString(value, FieldRepresentation.NUMERICAL)
                } else {
                    asString(value, FieldRepresentation.ALPHA)
                }
            }
}

private class FieldStructure<T>(factory: (PatternField) -> T) {
    val minutes = factory(PatternField.MINUTE)
    val hours = factory(PatternField.HOUR)
    val daysOfMonth = factory(PatternField.DAY_OF_MONTH)
    val months = factory(PatternField.MONTH)
    val daysOfWeek = factory(PatternField.DAY_OF_WEEK)

    val map = linkedMapOf(
        PatternField.MINUTE to minutes,
        PatternField.HOUR to hours,
        PatternField.DAY_OF_MONTH to daysOfMonth,
        PatternField.MONTH to months,
        PatternField.DAY_OF_WEEK to daysOfWeek
    )

    fun get(field: PatternField): T {
        return map.getValue(field)
    }

    val pairs= map.toList()
    val values = pairs.map { (_, v) -> v }

    fun <U, E> map(transform: (PatternField, T) -> Result<U, E>): Result<FieldStructure<U>, FieldStructure<E?>> {
        val resultStructure = FieldStructure { f -> transform(f, this.map.getValue(f)) }
        return if (resultStructure.values.any { it is Err }) {
            Err(FieldStructure { f -> resultStructure.get(f).getError() })
        } else {
            Ok(FieldStructure { f -> resultStructure.get(f).expect { "a transform logic failed" }})
        }
    }

    fun asString(convert: (T)->String): String = values.map(convert).joinToString(" ")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FieldStructure<*>

        if (minutes != other.minutes) return false
        if (hours != other.hours) return false
        if (daysOfMonth != other.daysOfMonth) return false
        if (months != other.months) return false
        if (daysOfWeek != other.daysOfWeek) return false

        return true
    }

    override fun hashCode(): Int {
        var result = minutes?.hashCode() ?: 0
        result = 31 * result + (hours?.hashCode() ?: 0)
        result = 31 * result + (daysOfMonth?.hashCode() ?: 0)
        result = 31 * result + (months?.hashCode() ?: 0)
        result = 31 * result + (daysOfWeek?.hashCode() ?: 0)
        return result
    }


}

internal enum class FieldRepresentation {
    STAR, NUMERICAL, ALPHA
}
