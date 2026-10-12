package com.luma.lifeos.integration.health

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient

fun openHealthSettings(context: Context): Boolean = open(context, Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
fun openHealthProviderInstall(context: Context): Boolean = open(context,
    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")))
private fun open(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
} catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }
