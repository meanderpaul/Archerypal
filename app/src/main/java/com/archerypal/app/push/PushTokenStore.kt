package com.archerypal.app.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PushTokenStore {
    private val _latestToken = MutableStateFlow<String?>(null)

    val latestToken: StateFlow<String?> = _latestToken.asStateFlow()
    val token: String?
        get() = _latestToken.value

    fun update(token: String) {
        _latestToken.value = token.trim().takeIf { it.isNotEmpty() }
    }

    fun clear() {
        _latestToken.value = null
    }
}
