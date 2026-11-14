package io.legado.app.utils

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class CronSchedule private constructor(
    private val minutes: BooleanArray,
    private val hours: BooleanArray,
    private val daysOfMonth: BooleanArray,
    private val months: BooleanArray,
    private val daysOfWeek: BooleanArray,
    private val domAny: Boolean,
    private val dowAny: Boolean
) {

    fun nextTimeAfter(fromEpochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long? {
        var time = Instant.ofEpochMilli(fromEpochMs)
            .atZone(zoneId)
            .withSecond(0)
            .withNano(0)
            .plusMinutes(1)
        repeat(MAX_MINUTES) {
            if (matches(time)) {
                return time.toInstant().toEpochMilli()
            }
            time = time.plusMinutes(1)
        }
        return null
    }

    private fun matches(time: ZonedDateTime): Boolean {
        if (!months[time.monthValue]) return false
        if (!hours[time.hour]) return false
        if (!minutes[time.minute]) return false
        val domMatch = daysOfMonth[time.dayOfMonth]
        val dow = time.dayOfWeek.value % 7
        val dowMatch = daysOfWeek[dow]
        val dayMatch = when {
            domAny && dowAny -> true
            domAny -> dowMatch
            dowAny -> domMatch
            else -> domMatch || dowMatch
        }
        return dayMatch
    }

    companion object {
        private const val MAX_MINUTES = 366 * 24 * 60

        fun parse(expression: String): CronSchedule? {
            val parts = expression.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (parts.size != 5) return null
            val minute = parseField(parts[0], 0, 59) ?: return null
            val hour = parseField(parts[1], 0, 23) ?: return null
            val dom = parseField(parts[2], 1, 31) ?: return null
            val month = parseField(parts[3], 1, 12) ?: return null
            val dow = parseField(parts[4], 0, 7, mapSundayToZero = true) ?: return null
            return CronSchedule(
                minute.allowed,
                hour.allowed,
                dom.allowed,
                month.allowed,
                dow.allowed,
                dom.any,
                dow.any
            )
        }

        private data class Field(val allowed: BooleanArray, val any: Boolean)

        private fun parseField(
            field: String,
            min: Int,
            max: Int,
            mapSundayToZero: Boolean = false
        ): Field? {
            val text = field.trim()
            if (text.isEmpty()) return null
            if (text == "*" || text == "?") {
                val allowed = BooleanArray(max + 1) { idx -> idx in min..max }
                return Field(allowed, true)
            }
            val allowed = BooleanArray(max + 1)
            val parts = text.split(",")
            for (part in parts) {
                if (part.isBlank()) return null
                val stepSplit = part.split("/", limit = 2)
                val base = stepSplit[0]
                val step = if (stepSplit.size == 2) stepSplit[1].toIntOrNull() else 1
                if (step == null || step <= 0) return null
                val range = when {
                    base == "*" || base == "?" -> min..max
                    base.contains("-") -> {
                        val rangeSplit = base.split("-", limit = 2)
                        val start = rangeSplit[0].toIntOrNull() ?: return null
                        val end = rangeSplit[1].toIntOrNull() ?: return null
                        if (start > end) return null
                        start..end
                    }
                    else -> {
                        val value = base.toIntOrNull() ?: return null
                        value..value
                    }
                }
                for (value in range step step) {
                    var v = value
                    if (mapSundayToZero && v == 7) v = 0
                    if (v < min || v > max) return null
                    allowed[v] = true
                }
            }
            if (allowed.none { it }) return null
            return Field(allowed, false)
        }
    }
}
