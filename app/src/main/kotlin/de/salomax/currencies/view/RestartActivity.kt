package de.salomax.currencies.view

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Process
import de.salomax.currencies.viewmodel.preference.applyLauncherAliasState
import de.salomax.currencies.viewmodel.preference.launcherAliasName

// Runs in a separate ":restart" process (see AndroidManifest) so it survives
// while the main process is being killed. On start it kills the caller's
// process, swaps the launcher alias, and then fires the launcher intent —
// bringing the app back up as a fresh cold start.
//
// The alias swap has to happen HERE (not in the caller) because disabling
// the alias that roots the current task can kill the main process before
// this activity gets a chance to start, despite DONT_KILL_APP. Swapping
// after killing the main process is safe — the task it rooted is gone.
//
// AlarmManager-based relaunches are unreliable on Android 12+ due to
// background-activity-start restrictions; a foreground activity in a
// separate process avoids those.
class RestartActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mainPid = intent.getIntExtra(EXTRA_MAIN_PID, -1)
        val pureBlack = intent.getBooleanExtra(EXTRA_PURE_BLACK, false)
        if (mainPid > 0 && mainPid != Process.myPid()) {
            Process.killProcess(mainPid)
        }
        applyLauncherAliasState(this, pureBlack)
        val target = Intent().apply {
            setClassName(this@RestartActivity, launcherAliasName(pureBlack))
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(target)
        finish()
        Runtime.getRuntime().exit(0)
    }

    companion object {
        const val EXTRA_PURE_BLACK = "restart.pure_black"
        const val EXTRA_MAIN_PID = "restart.main_pid"
    }
}
