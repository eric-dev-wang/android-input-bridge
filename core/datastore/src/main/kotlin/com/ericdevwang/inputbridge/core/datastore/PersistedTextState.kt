package com.ericdevwang.inputbridge.core.datastore

data class PersistedTextState(
    val text: String,
    val version: Long,
    val updatedAt: Long,
)
