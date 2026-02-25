package com.example.universalterminal.presentation.theme.ui

import java.util.regex.Pattern

internal data class ParsedFirmware(
    val type: FirmwareType,
    val version: String
)

internal object FirmwareValidation {
    private val versionPattern = Pattern.compile(".*_(\\d+\\.\\d+\\.\\d+)\\.zip")

    fun parseFileName(fileName: String): Result<ParsedFirmware> {
        val type = when {
            fileName.startsWith("DfuAppOnline_") -> FirmwareType.NORDIC
            fileName.startsWith("AppOnline_") -> FirmwareType.WCH
            else -> return Result.failure(
                IllegalArgumentException("Unrecognized firmware file format: $fileName")
            )
        }

        val matcher = versionPattern.matcher(fileName)
        if (!matcher.find()) {
            return Result.failure(IllegalArgumentException("Invalid version format in file name: $fileName"))
        }

        val version = matcher.group(1)
            ?: return Result.failure(IllegalArgumentException("Invalid version format in file name: $fileName"))
        return Result.success(ParsedFirmware(type = type, version = version))
    }

    fun validateForDeviceModel(
        deviceModel: String?,
        firmwareType: FirmwareType,
        version: String
    ): String? {
        if (deviceModel.isNullOrBlank()) {
            return "Unknown device model: $deviceModel"
        }

        val versionParts = version.split('.')
        if (versionParts.size < 3) {
            return "Invalid version format: $version"
        }

        val major = versionParts[0].toIntOrNull() ?: return "Invalid version format: $version"
        val minor = versionParts[1].toIntOrNull() ?: return "Invalid version format: $version"
        val patch = versionParts[2].toIntOrNull() ?: return "Invalid version format: $version"

        return when {
            deviceModel.contains("WCH", ignoreCase = true) -> {
                when {
                    firmwareType != FirmwareType.WCH -> "WCH device requires WCH firmware"
                    major != 4 || minor !in 5..9 -> "WCH device requires firmware version 4.5.0 - 4.9.9"
                    else -> null
                }
            }

            deviceModel.contains("NORDIC", ignoreCase = true) -> {
                when {
                    firmwareType != FirmwareType.NORDIC -> "NORDIC device requires NORDIC firmware"
                    major != 4 || minor > 1 || (minor == 1 && patch > 9) ->
                        "NORDIC device requires firmware version 4.0.0 - 4.1.9"

                    else -> null
                }
            }

            else -> "Unknown device model: $deviceModel"
        }
    }
}
