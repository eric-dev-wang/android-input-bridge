package com.ericdevwang.inputbridge.protocol

import kotlinx.serialization.json.Json

object ProtocolJson {
    val default: Json = Json {
        classDiscriminator = "type"
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }
}
