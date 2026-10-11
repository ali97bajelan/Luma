package com.luma.lifeos.data.mapping

import org.json.JSONObject

internal fun String.usageLabel(): String? = runCatching { JSONObject(this).optString("app_label") }
    .getOrNull()?.takeIf { it.isNotBlank() && it.length <= 200 }
