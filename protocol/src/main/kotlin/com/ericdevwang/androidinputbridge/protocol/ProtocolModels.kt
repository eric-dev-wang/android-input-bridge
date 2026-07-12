package com.ericdevwang.androidinputbridge.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class HealthResponse(
    val status: String,
    val appVersion: String,
    val protocolVersion: Int,
    val serverTime: Long,
)

@Serializable
data class TextResponse(
    val text: String,
    val version: Long,
    val updatedAt: Long,
)

@Serializable
data class ClearResponse(
    val clearedVersion: Long,
    val newVersion: Long,
)

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
    val details: JsonElement = JsonObject(emptyMap()),
)
