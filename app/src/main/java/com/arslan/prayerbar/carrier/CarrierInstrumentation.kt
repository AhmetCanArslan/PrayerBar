package com.arslan.prayerbar.carrier

import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Base64
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Broker for builds that refuse `overrideConfig` from the shell uid.
 *
 * Shizuku (as shell) starts this instrumentation with `am instrument`. The instrumentation runs in
 * the app's own uid, adopts the shell's permissions through UiAutomation, and transacts the
 * override itself. The service then sees our uid, not 2000, while the MODIFY_PHONE_STATE check
 * still passes through the adopted identity.
 *
 * It runs in a dedicated `:carrier` process (see the manifest `targetProcesses`) and is started with
 * `--no-restart`, so the main process — which is blocked waiting for the result — is not killed.
 */
class CarrierInstrumentation : Instrumentation() {

    private var arguments: Bundle = Bundle.EMPTY

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        this.arguments = arguments ?: Bundle.EMPTY
        start()
    }

    override fun onStart() {
        val results = Bundle()
        val result = try {
            execute()
        } catch (e: Throwable) {
            Log.e(TAG, "broker failed", e)
            CarrierResult.TransactionFailed(e.message ?: e.javaClass.simpleName)
        }
        results.putString(KEY_OK, result.isOk.toString())
        if (result is CarrierResult.TransactionFailed) {
            // `am instrument` prints values on one line; keep it that way.
            results.putString(KEY_ERROR, result.reason.replace('\n', ' '))
        }
        finish(Activity.RESULT_OK, results)
    }

    private fun execute(): CarrierResult {
        val subId = arguments.getString(ARG_SUB_ID)?.toIntOrNull()
            ?: return CarrierResult.TransactionFailed("broker: missing subId")
        val persistent = arguments.getString(ARG_PERSISTENT).toBoolean()
        val bundle = arguments.getString(ARG_BUNDLE)?.let(::decodeBundle)

        val automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.adoptShellPermissionIdentity()
        return try {
            val binder = CarrierNameManager.rawCarrierConfigBinder()
                ?: return CarrierResult.TransactionFailed("carrier_config service unavailable")
            CarrierNameManager.transactOverride(binder, subId, bundle, persistent)
        } finally {
            runCatching { automation.dropShellPermissionIdentity() }
        }
    }

    companion object {
        private const val TAG = "CarrierInstrumentation"
        private const val ARG_SUB_ID = "subId"
        private const val ARG_PERSISTENT = "persistent"
        private const val ARG_BUNDLE = "bundle"
        private const val KEY_OK = "ok"
        private const val KEY_ERROR = "error"
        private const val TIMEOUT_MILLIS = 15_000L

        /** `am instrument` addresses the installed package; applicationId has no suffixes. */
        private const val PACKAGE = "com.arslan.prayerbar"

        /**
         * Launches the broker through Shizuku and waits for its verdict. Serialized: the service,
         * the alarm and the UI can all apply at once, and overlapping instrumentations of the same
         * process would cancel each other.
         */
        @Synchronized
        fun run(subId: Int, bundle: PersistableBundle?, persistent: Boolean): CarrierResult {
            // Without --no-restart (Android 12+) `am instrument` force-stops the whole package,
            // which would kill the very process waiting on the result.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return CarrierResult.TransactionFailed("overrideConfig blocked for shell")
            }
            val component = "$PACKAGE/${CarrierInstrumentation::class.java.name}"
            val command = buildList {
                add("am")
                add("instrument")
                add("-w")
                add("--no-restart")
                add("--no-hidden-api-checks")
                add("-e"); add(ARG_SUB_ID); add(subId.toString())
                add("-e"); add(ARG_PERSISTENT); add(persistent.toString())
                if (bundle != null) {
                    add("-e"); add(ARG_BUNDLE); add(encodeBundle(bundle))
                }
                add(component)
            }.toTypedArray()

            val output = ShizukuHelper.executeShellCommandForOutput(command, TIMEOUT_MILLIS)
                ?: return CarrierResult.TransactionFailed("broker: am instrument failed")
            Log.d(TAG, "broker output: $output")
            return parse(output)
        }

        internal fun parse(output: String): CarrierResult {
            val values = output.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("INSTRUMENTATION_RESULT:") }
                .map { it.removePrefix("INSTRUMENTATION_RESULT:").trim() }
                .mapNotNull { line ->
                    val eq = line.indexOf('=')
                    if (eq <= 0) null else line.substring(0, eq) to line.substring(eq + 1)
                }
                .toMap()
            return when {
                values[KEY_OK] == "true" -> CarrierResult.Ok
                values[KEY_ERROR] != null -> CarrierResult.TransactionFailed(values.getValue(KEY_ERROR))
                else -> CarrierResult.TransactionFailed(
                    "broker: " + output.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                )
            }
        }

        // The label can hold spaces and emoji; base64 keeps the argv round trip lossless.
        private fun encodeBundle(bundle: PersistableBundle): String {
            val out = ByteArrayOutputStream()
            bundle.writeToStream(out)
            return Base64.encodeToString(out.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
        }

        private fun decodeBundle(encoded: String): PersistableBundle =
            PersistableBundle.readFromStream(
                ByteArrayInputStream(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP)),
            )
    }
}
