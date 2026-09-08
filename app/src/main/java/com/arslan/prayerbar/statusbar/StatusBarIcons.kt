package com.arslan.prayerbar.statusbar

import android.content.Context
import android.util.Log
import com.arslan.prayerbar.carrier.ShizukuHelper

/** Why a status bar write did or did not land. */
sealed interface StatusBarResult {
    data object Ok : StatusBarResult
    data object Off : StatusBarResult
    data object NoShizuku : StatusBarResult
    data object NoLocation : StatusBarResult
    data object WriteFailed : StatusBarResult

    val isOk: Boolean get() = this is Ok
}

/**
 * Puts the label into the status bar as real SystemUI icons, one slot per character.
 *
 * SystemUI accepts a slot name it has never heard of and loads the drawable out of this package,
 * which is what makes an app-drawn label possible at all; what it will not accept is a bitmap
 * built at runtime, so the text is spelled from the pre-generated glyphs in [Glyphs].
 *
 * Every call hands the whole label to one [StatusBarIconInjector] process: a write per slot
 * would mean a process per slot.
 */
object StatusBarIcons {

    private const val TAG = "StatusBarIcons"
    private const val SLOT_PREFIX = "prayerbar_"

    fun apply(context: Context, text: String, description: String): Boolean =
        run(context, description, operations(StatusBarText.segments(text)))

    fun clear(context: Context): Boolean = run(context, "", operations(emptyList()))

    /**
     * Always every slot, highest index first, whatever the label is.
     *
     * SystemUI puts a slot it has not seen at the head of its list, so where a character lands is
     * decided by the order the slots were first written and never revisited. Writing only the
     * slots this label happens to need leaves a longer label later inserting its tail to the left
     * of the slots already standing, which spells it inside out. Touching all of them every time,
     * from the last to the first, fixes the whole run in one pass — and it costs nothing, because
     * removing a slot reserves its position while drawing no icon and taking no width.
     */
    private fun operations(segments: List<Int>): List<String> =
        (StatusBarText.MAX_SLOTS - 1 downTo 0).map { index ->
            val segment = segments.getOrNull(index)
            if (segment == null) {
                slotName(index)
            } else {
                "${slotName(index)}=${Integer.toHexString(segment)}"
            }
        }

    private fun run(context: Context, description: String, tokens: List<String>): Boolean {
        if (tokens.isEmpty()) return true
        // `env` rather than a bare exec: app_process needs CLASSPATH set for it, and the runtime
        // roots it inherits from the shell environment to start at all.
        val command = arrayOf(
            "env",
            "CLASSPATH=${context.applicationInfo.sourceDir}",
            "app_process",
            "/",
            StatusBarIconInjector::class.java.name,
            context.packageName,
            description,
        ) + tokens
        val applied = ShizukuHelper.executeShellCommand(command)
        Log.d(TAG, "apply tokens=${tokens.size} applied=$applied")
        return applied
    }

    private fun slotName(index: Int): String = SLOT_PREFIX + index
}
