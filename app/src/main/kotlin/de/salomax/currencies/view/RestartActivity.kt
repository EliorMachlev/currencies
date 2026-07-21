package de.salomax.currencies.view

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Process

// Runs in a separate ":restart" process (see AndroidManifest) so it survives
// while the main process is being killed. On start it kills the caller's
// process, then fires the launcher intent — which brings the app back up as a
// fresh cold start. Works around AlarmManager-based relaunches being
// unreliable on Android 12+ due to background-activity-start restrictions.
class RestartActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target: Intent? = intent.getParcelableExtra(EXTRA_TARGET_INTENT)
        val mainPid = intent.getIntExtra(EXTRA_MAIN_PID, -1)
        if (mainPid > 0 && mainPid != Process.myPid()) {
            Process.killProcess(mainPid)
        }
        if (target != null) startActivity(target)
        finish()
        Runtime.getRuntime().exit(0)
    }

    companion object {
        const val EXTRA_TARGET_INTENT = "restart.target_intent"
        const val EXTRA_MAIN_PID = "restart.main_pid"
    }
}
