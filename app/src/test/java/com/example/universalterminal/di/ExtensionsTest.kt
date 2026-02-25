package com.example.universalterminal.di

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtensionsTest {

    @Test
    fun toDfuAddress_incrementsLastToken() {
        val result = "AA:BB:CC:DD:EE:0F".toDfuAddress()
        assertEquals("AA:BB:CC:DD:EE:10", result)
    }

    @Test
    fun toDfuAddress_wrapsOnOverflow() {
        val result = "AA:BB:CC:DD:EE:FF".toDfuAddress()
        assertEquals("AA:BB:CC:DD:EE:00", result)
    }
}
