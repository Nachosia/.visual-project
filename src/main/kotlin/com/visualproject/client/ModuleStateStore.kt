package com.visualproject.client

import java.util.concurrent.ConcurrentHashMap

object ModuleStateStore {

    private val moduleEnabledState: MutableMap<String, Boolean> = ConcurrentHashMap()
    private val moduleSettingsState: MutableMap<String, Boolean> = ConcurrentHashMap()

    fun ensureModule(id: String, defaultEnabled: Boolean = false) {
        moduleEnabledState.putIfAbsent(id, defaultEnabled)
    }

    fun isEnabled(id: String): Boolean {
        return moduleEnabledState[id] == true
    }

    fun setEnabled(id: String, enabled: Boolean) {
        moduleEnabledState[id] = enabled
    }

    fun toggleEnabled(id: String): Boolean {
        val next = !isEnabled(id)
        setEnabled(id, next)
        return next
    }

    fun ensureSetting(key: String, defaultValue: Boolean = false) {
        moduleSettingsState.putIfAbsent(key, defaultValue)
    }

    fun isSettingEnabled(key: String): Boolean {
        return moduleSettingsState[key] == true
    }

    fun setSettingEnabled(key: String, enabled: Boolean) {
        moduleSettingsState[key] = enabled
    }
}
