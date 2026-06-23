package com.actioncut.core.common.time

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatterTest {

    @Test
    fun clock_formatsMinutesAndSeconds() {
        assertEquals("00:00", TimeFormatter.clock(0))
        assertEquals("00:01", TimeFormatter.clock(1_000))
        assertEquals("01:01", TimeFormatter.clock(61_000))
    }

    @Test
    fun clock_includesHoursWhenNeeded() {
        assertEquals("1:01:01", TimeFormatter.clock(3_661_000))
    }

    @Test
    fun precise_showsTenths() {
        assertEquals("00:01.5", TimeFormatter.precise(1_500))
        assertEquals("01:00.0", TimeFormatter.precise(60_000))
    }

    @Test
    fun short_isHumanReadable() {
        assertEquals("0s", TimeFormatter.short(0))
        assertEquals("5s", TimeFormatter.short(5_000))
        assertEquals("1m 5s", TimeFormatter.short(65_000))
    }
}
