package com.luma.lifeos.ui.integrations

enum class IntegrationSettingsOperation { OBSERVE, ACCESS_REFRESH, IMPORT, PREFERENCE }

data class IntegrationSettingsError(
    val operation: IntegrationSettingsOperation,
    val requestId: Long,
)
