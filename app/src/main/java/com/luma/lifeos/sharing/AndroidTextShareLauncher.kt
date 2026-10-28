package com.luma.lifeos.sharing

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent

enum class TextShareLaunchResult { OPENED, UNAVAILABLE }

class AndroidTextShareLauncher(private val activity: Activity, private val chooserTitle: String) {
    fun launch(text: String): TextShareLaunchResult {
        if (validateEntryShareText(text) != null) return TextShareLaunchResult.UNAVAILABLE
        return try {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            activity.startActivity(Intent.createChooser(send, chooserTitle))
            TextShareLaunchResult.OPENED
        } catch (_: ActivityNotFoundException) {
            TextShareLaunchResult.UNAVAILABLE
        } catch (_: SecurityException) {
            TextShareLaunchResult.UNAVAILABLE
        } catch (_: RuntimeException) {
            // Binder failures can be wrapped in a runtime exception by the framework.
            TextShareLaunchResult.UNAVAILABLE
        }
    }
}
