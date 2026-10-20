package com.luma.lifeos.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Refreshes dated projections for platform events Android permits apps to observe. */
class WidgetSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_BOOT_COMPLETED
            -> {
                WidgetRefreshCoordinator.request(context)
                WidgetRefreshCoordinator.refreshQuickCapture(context)
            }
        }
    }
}
