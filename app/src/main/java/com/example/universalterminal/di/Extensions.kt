package com.example.universalterminal.di

import java.math.BigInteger

internal fun String.toDfuAddress(): String {
    val tokens = this.split(":")
    val token = tokens.last()
    val hex = BigInteger(token, 16)
    val new = hex.plus(BigInteger.ONE).toString(16).padStart(2, '0').takeLast(2)

    return tokens.joinToString(
        separator = ":",
        limit = tokens.size - 1,
        postfix = new,
        truncated = ""
    ).uppercase()
}