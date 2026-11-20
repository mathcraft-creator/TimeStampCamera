package com.mathcraft.timestampcamera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

class GalleryStamperTest {

    private val seoul = TimeZone.getTimeZone("Asia/Seoul")

    @Test
    fun parsesExifDateInDefaultZone() {
        val date = GalleryStamper.parseExifDateTime("2024:08:10 15:30:20", null, seoul)
        // 2024-08-10 15:30:20 KST == 06:30:20 UTC
        assertEquals(1723271420000L, date?.time)
    }

    @Test
    fun offsetOverridesDefaultZone() {
        val date = GalleryStamper.parseExifDateTime("2024:08:10 15:30:20", "+00:00", seoul)
        assertEquals(1723303820000L, date?.time)
    }

    @Test
    fun ignoresMalformedOffset() {
        val date = GalleryStamper.parseExifDateTime("2024:08:10 15:30:20", "junk", seoul)
        assertEquals(1723271420000L, date?.time)
    }

    @Test
    fun emptyOrZeroDatesAreNull() {
        assertNull(GalleryStamper.parseExifDateTime(null, null, seoul))
        assertNull(GalleryStamper.parseExifDateTime("", null, seoul))
        assertNull(GalleryStamper.parseExifDateTime("0000:00:00 00:00:00", null, seoul))
        assertNull(GalleryStamper.parseExifDateTime("not a date", null, seoul))
    }
}
