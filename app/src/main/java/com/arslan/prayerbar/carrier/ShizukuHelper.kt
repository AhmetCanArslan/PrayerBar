package com.arslan.prayerbar.carrier

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import rikka.shizuku.Shizuku
import java.lang.reflect.Method
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The single gateway to Shizuku. Every privileged call goes through here and every failure is
 * swallowed into a false/empty result, so the app degrades cleanly when Shizuku is absent.
 */
object ShizukuHelper {

    private const val TAG = "ShizukuHelper"
    private const val PREFS = "shizuku_prefs"
    private const val KEY_REQUESTED = "shizuku_request_sent"
    const val REQUEST_CODE = 4242

    fun isShizukuAvailable(): Boolean = try {
        Shizuku.pingBinder() &&
            (Shizuku.checkSelfPermission() >= 0 || Shizuku.getVersion() > 0)
    } catch (e: Exception) {
        Log.d(TAG, "Shizuku not available", e)
        false
    }

    fun hasPermission(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == 0
    } catch (e: Exception) {
        false
    }

    /**
     * The binder is handed over asynchronously shortly after the process starts, so a write fired
     * from a boot receiver or an alarm can arrive before Shizuku is ready. Wait briefly for it.
     */
    fun awaitPermission(timeoutMillis: Long = 3_000L): Boolean {
        if (hasPermission()) return true
        val latch = CountDownLatch(1)
        val listener = Shizuku.OnBinderReceivedListener { latch.countDown() }
        return try {
            Shizuku.addBinderReceivedListenerSticky(listener)
            latch.await(timeoutMillis, TimeUnit.MILLISECONDS)
            hasPermission()
        } catch (e: Exception) {
            false
        } finally {
            runCatching { Shizuku.removeBinderReceivedListener(listener) }
        }
    }

    fun shouldShowRationale(): Boolean = try {
        Shizuku.shouldShowRequestPermissionRationale()
    } catch (e: Exception) {
        false
    }

    fun requestPermission(activity: Activity) {
        try {
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request Shizuku permission", e)
        }
    }

    fun hasBeenRequested(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_REQUESTED, false)

    fun markRequested(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_REQUESTED, true)
            .apply()
    }

    /**
     * Runs an argv command with shell identity. Uses the hidden `Shizuku.newProcess` through
     * reflection — the same approach CustomAnimator uses.
     */
    fun executeShellCommand(command: Array<String>): Boolean {
        if (!hasPermission()) {
            Log.d(TAG, "No Shizuku permission for: ${command.joinToString(" ")}")
            return false
        }
        return try {
            val newProcess: Method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            )
            newProcess.isAccessible = true
            val process = newProcess.invoke(null, command, null, null) as Any
            val exitCode = process.javaClass.getDeclaredMethod("waitFor").invoke(process) as Int
            Log.d(TAG, "cmd=${command.joinToString(" ")} exit=$exitCode")
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "Shell command failed: ${command.joinToString(" ")}", e)
            false
        }
    }

    /** True when the Shizuku server runs as root, which no shell-uid restriction applies to. */
    fun isRoot(): Boolean = try {
        Shizuku.getUid() == 0
    } catch (e: Exception) {
        false
    }

    /** Like [executeShellCommand] but returns combined stdout/stderr, or null if it never ran. */
    fun executeShellCommandForOutput(command: Array<String>, timeoutMillis: Long): String? {
        if (!hasPermission()) return null
        return try {
            val newProcess: Method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            )
            newProcess.isAccessible = true
            val process = newProcess.invoke(null, command, null, null) as Process
            val stdout = StringBuilder()
            val stderr = StringBuilder()
            val readers = listOf(
                Thread { stdout.append(process.inputStream.bufferedReader().readText()) },
                Thread { stderr.append(process.errorStream.bufferedReader().readText()) },
            )
            readers.forEach { it.start() }
            // ShizukuRemoteProcess.exitValue() throws IllegalArgumentException while running, which
            // breaks Process.waitFor(timeout)'s polling; block on waitFor() in a thread instead.
            val waiter = Thread { runCatching { process.waitFor() } }.apply { start() }
            waiter.join(timeoutMillis)
            if (waiter.isAlive) {
                process.destroy()
                Log.w(TAG, "Timed out: ${command.first()} ${command.getOrNull(1)}")
            }
            readers.forEach { it.join(1_000L) }
            "$stdout$stderr"
        } catch (e: Exception) {
            Log.e(TAG, "Shell command failed: ${command.first()}", e)
            null
        }
    }

    /** Some skins cache the carrier label until SystemUI restarts. */
    fun restartSystemUi(): Boolean =
        executeShellCommand(arrayOf("killall", "com.android.systemui")) ||
            executeShellCommand(arrayOf("am", "crash", "com.android.systemui"))

    val isSupportedAndroidVersion: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
}
