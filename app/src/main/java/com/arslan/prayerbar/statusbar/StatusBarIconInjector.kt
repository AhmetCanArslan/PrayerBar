package com.arslan.prayerbar.statusbar

import android.annotation.SuppressLint
import android.os.IBinder
import kotlin.system.exitProcess

/**
 * Writes the glyph slots into SystemUI's status bar.
 *
 * This runs in a process of its own, started with shell identity by [StatusBarIcons] through
 * `CLASSPATH=<apk> app_process`. It cannot run inside the app. `setIcon` lives on the hidden
 * `IStatusBarService`, reflecting on a non-SDK interface from an app process is blocked, and the
 * call is guarded by `STATUS_BAR`, which shell holds and the app does not. A process started by
 * `app_process` never installs the hidden-API enforcement, so the same reflection is plain Java
 * there — and going through the framework's own proxy means the transaction ordinals are always
 * the ones this build of Android uses, rather than a number guessed per version.
 *
 * Arguments: the package the drawables live in, the content description for the leftmost icon,
 * then one token per slot — `slot=hexResourceId` sets an icon, a bare `slot` removes one.
 * Removals come first. The writes that follow run right to left, because SystemUI puts a slot it
 * does not know at the head of its list, so the one written last ends up leftmost.
 */
object StatusBarIconInjector {

    private const val SERVICE = "statusbar"
    private const val INTERFACE = "com.android.internal.statusbar.IStatusBarService"

    @JvmStatic
    fun main(args: Array<String>) {
        val applied = runCatching { apply(args) }
            .onFailure { System.err.println("prayerbar: status bar write failed: $it") }
            .getOrDefault(false)
        exitProcess(if (applied) 0 else 1)
    }

    @SuppressLint("PrivateApi")
    private fun apply(args: Array<String>): Boolean {
        if (args.size < 3) {
            System.err.println("prayerbar: usage <package> <description> <slot[=hexResId]>...")
            return false
        }
        val iconPackage = args[0]
        val description = args[1]
        val tokens = args.drop(2)

        val service = statusBarService()
        val iface = Class.forName(INTERFACE)
        val setIcon = iface.getMethod(
            "setIcon",
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java,
        )
        val removeIcon = iface.getMethod("removeIcon", String::class.java)

        // Only the leftmost icon carries the description, so a screen reader announces the label
        // once instead of once per character.
        val lastWrite = tokens.indexOfLast { it.contains('=') }
        tokens.forEachIndexed { index, token ->
            val separator = token.indexOf('=')
            if (separator < 0) {
                removeIcon.invoke(service, token)
            } else {
                val slot = token.substring(0, separator)
                val resourceId = token.substring(separator + 1).toLong(16).toInt()
                val spoken = if (index == lastWrite) description else ""
                setIcon.invoke(service, slot, iconPackage, resourceId, 0, spoken)
            }
        }
        return true
    }

    @SuppressLint("PrivateApi")
    private fun statusBarService(): Any {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val binder = serviceManager.getMethod("getService", String::class.java)
            .invoke(null, SERVICE) as IBinder?
            ?: error("the statusbar service is not published")
        val stub = Class.forName("$INTERFACE\$Stub")
        return stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            ?: error("statusbar binder did not yield an interface")
    }
}
