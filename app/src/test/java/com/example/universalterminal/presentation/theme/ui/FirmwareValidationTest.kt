package com.example.universalterminal.presentation.theme.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirmwareValidationTest {

    @Test
    fun parseFileName_parsesNordicFirmware() {
        val result = FirmwareValidation.parseFileName("DfuAppOnline_4.1.9.zip")
        assertTrue(result.isSuccess)
        val parsed = result.getOrThrow()
        assertEquals(FirmwareType.NORDIC, parsed.type)
        assertEquals("4.1.9", parsed.version)
    }

    @Test
    fun parseFileName_rejectsUnknownPattern() {
        val result = FirmwareValidation.parseFileName("firmware_4.1.9.zip")
        assertTrue(result.isFailure)
    }

    @Test
    fun validateForDeviceModel_acceptsWchRange() {
        val error = FirmwareValidation.validateForDeviceModel(
            deviceModel = "WCH",
            firmwareType = FirmwareType.WCH,
            version = "4.6.1"
        )
        assertNull(error)
    }

    @Test
    fun validateForDeviceModel_rejectsTypeMismatch() {
        val error = FirmwareValidation.validateForDeviceModel(
            deviceModel = "NORDIC",
            firmwareType = FirmwareType.WCH,
            version = "4.1.5"
        )
        assertEquals("NORDIC device requires NORDIC firmware", error)
    }

    @Test
    fun validateForDeviceModel_rejectsNordicOutOfRangeVersion() {
        val error = FirmwareValidation.validateForDeviceModel(
            deviceModel = "NORDIC",
            firmwareType = FirmwareType.NORDIC,
            version = "4.2.0"
        )
        assertEquals("NORDIC device requires firmware version 4.0.0 - 4.1.9", error)
    }
}
