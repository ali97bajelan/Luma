package com.luma.lifeos.lock

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import com.luma.lifeos.domain.lock.LockCoverage
import com.luma.lifeos.domain.lock.LockSection
import com.luma.lifeos.domain.lock.LockSnapshot

/** Applies screenshot and Recents policy to the single app activity window. */
object LockWindowPrivacy {
    fun apply(
        activity: Activity,
        snapshot: LockSnapshot,
        visibleSections: Set<LockSection>,
        alwaysSecure: Boolean,
    ) {
        val policy = snapshot.policy
        val policyReady = snapshot.loadStatus == com.luma.lifeos.domain.lock.LockLoadStatus.READY
        val enabled = policyReady &&
            policy?.enabled == true
        val protectedContentVisible = enabled && (policy?.coverage == LockCoverage.WHOLE_APP ||
            !visibleSections.intersect(policy?.sections.orEmpty()).isEmpty())
        val protectScreenshot = alwaysSecure ||
            (protectedContentVisible && policy?.screenshotsProtected == true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(policyReady && !enabled && !alwaysSecure)
        }

        // Older Android releases have no Recents-only screenshot switch. MainActivity adds
        // FLAG_SECURE as it pauses to mask the task snapshot, then this policy restores the
        // user's foreground screenshot choice when the activity resumes.
        val secure = protectScreenshot ||
            (!policyReady && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
        if (secure) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
