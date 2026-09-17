package com.behradhz.meowzix.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatterTest {
    @Test
    fun formatsMinutesAndSeconds() {
        assertEquals("3:07", formatTrackDuration(187_000))
    }

    @Test
    fun formatsHoursWhenNeeded() {
        assertEquals("1:02:03", formatTrackDuration(3_723_000))
    }

    @Test
    fun clampsNegativeDurations() {
        assertEquals("0:00", formatTrackDuration(-1))
    }
}
