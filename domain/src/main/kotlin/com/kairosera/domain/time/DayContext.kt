package com.kairosera.domain.time

import java.time.LocalTime

enum class DayPart { MORNING, AFTERNOON, EVENING, NIGHT }

object Greeting {
    fun partOf(time: LocalTime): DayPart = when (time.hour) {
        in 5..11 -> DayPart.MORNING
        in 12..16 -> DayPart.AFTERNOON
        in 17..21 -> DayPart.EVENING
        else -> DayPart.NIGHT
    }
}
