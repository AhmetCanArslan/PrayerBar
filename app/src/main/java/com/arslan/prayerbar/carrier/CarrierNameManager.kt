package com.arslan.prayerbar.carrier

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

data class SimSlot(
    val subId: Int,
    val slotIndex: Int,
    val carrierName: String,
)

/** Why a carrier write did or did not land — the UI needs more than a bare boolean. */
sealed interface CarrierResult {
    data object Ok : CarrierResult
    data object NoShizuku : CarrierResult
    data object Unsupported : CarrierResult
    data object NoSim : CarrierResult
    data object NoPhonePermission : CarrierResult
    data class TransactionFailed(val reason: String) : CarrierResult

    val isOk: Boolean get() = this is Ok
}

/**
 * Overrides the status bar carrier label by pushing a carrier-config override through the
 * `carrier_config` system service. The binder is wrapped by Shizuku so the transaction runs with
 * shell identity, which holds `MODIFY_PHONE_STATE`.
 *
 * Transaction ordinals are positional in `ICarrierConfigLoader`; txn 6 is transacted first as a
 * probe so an OEM with a reordered interface fails safely instead of firing a wrong transaction.
 */
object CarrierNameManager {

    private const val TAG = "CarrierNameManager"
    private const val CARRIER_CONFIG_SERVICE = "carrier_config"
    private const val DESCRIPTOR = "com.android.internal.telephony.ICarrierConfigLoader"
    private const val TRANSACTION_OVERRIDE_CONFIG = 3
    private const val TRANSACTION_GET_DEFAULT_PACKAGE = 6

    /** SystemUI truncates long labels; keep the rendered text within a sane width. */
    const val MAX_LABEL_LENGTH = 32

    fun hasPhonePermission(context: Context): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_PHONE_STATE,
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Active SIMs, best effort. `activeSubscriptionInfoList` needs READ_PHONE_STATE and returns
     * null/empty on plenty of OEM builds even with a live SIM, so an empty list from it is not
     * proof of "no SIM": fall back to the default subscription id, which needs no permission.
     */
    fun getSimSlots(context: Context): List<SimSlot> {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val fromSubscriptions = if (hasPhonePermission(context)) {
            val manager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                as? SubscriptionManager
            try {
                manager?.activeSubscriptionInfoList.orEmpty().map { info ->
                    val name = info.carrierName?.toString().takeUnless { it.isNullOrBlank() }
                        ?: telephony?.networkOperatorName.orEmpty()
                    SimSlot(info.subscriptionId, info.simSlotIndex, name)
                }
            } catch (e: Exception) {
                Log.w(TAG, "activeSubscriptionInfoList failed", e)
                emptyList()
            }
        } else {
            emptyList()
        }
        if (fromSubscriptions.isNotEmpty()) return fromSubscriptions
        return defaultSlot(telephony)
    }

    /** True when telephony reports a card present, regardless of READ_PHONE_STATE. */
    fun hasSimPresent(context: Context): Boolean {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        return telephony?.simState == TelephonyManager.SIM_STATE_READY
    }

    private fun defaultSlot(telephony: TelephonyManager?): List<SimSlot> {
        if (telephony != null && telephony.simState != TelephonyManager.SIM_STATE_READY) {
            return emptyList()
        }
        val subId = defaultSubId()
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return emptyList()
        return listOf(SimSlot(subId, 0, telephony?.networkOperatorName.orEmpty()))
    }

    private fun defaultSubId(): Int = try {
        val default = SubscriptionManager.getDefaultSubscriptionId()
        if (default != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            default
        } else {
            SubscriptionManager.getDefaultDataSubscriptionId()
        }
    } catch (e: Exception) {
        SubscriptionManager.INVALID_SUBSCRIPTION_ID
    }

    /**
     * @param persistent true writes the override to disk so it survives a reboot. The scheduled
     * per-prayer updates pass false; only the manual apply persists.
     */
    fun setCarrierName(subId: Int, name: String, persistent: Boolean = false): CarrierResult {
        val bundle = PersistableBundle().apply {
            putBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, true)
            putString(CarrierConfigManager.KEY_CARRIER_NAME_STRING, name.take(MAX_LABEL_LENGTH))
        }
        return overrideConfig(subId, bundle, persistent)
    }

    fun resetCarrierName(subId: Int): CarrierResult {
        val bundle = PersistableBundle().apply {
            putBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, false)
            putString(CarrierConfigManager.KEY_CARRIER_NAME_STRING, "")
        }
        val cleared = overrideConfig(subId, bundle, persistent = true)
        val nulled = overrideConfig(subId, null, persistent = true)
        return if (nulled.isOk) nulled else cleared
    }

    private fun carrierConfigBinder(): IBinder? {
        val service = SystemServiceHelper.getSystemService(CARRIER_CONFIG_SERVICE) ?: return null
        return ShizukuBinderWrapper(service)
    }

    private fun defaultCarrierServicePackage(binder: IBinder): String? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            binder.transact(TRANSACTION_GET_DEFAULT_PACKAGE, data, reply, 0)
            reply.readException()
            reply.readString()
        } catch (e: Exception) {
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun overrideConfig(
        subId: Int,
        bundle: PersistableBundle?,
        persistent: Boolean,
    ): CarrierResult {
        if (!ShizukuHelper.isSupportedAndroidVersion) return CarrierResult.Unsupported
        if (!ShizukuHelper.awaitPermission()) return CarrierResult.NoShizuku
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return CarrierResult.NoSim
        val binder = carrierConfigBinder()
            ?: return CarrierResult.TransactionFailed("carrier_config service unavailable")
        if (defaultCarrierServicePackage(binder).isNullOrEmpty()) {
            return CarrierResult.TransactionFailed("ICarrierConfigLoader probe failed")
        }

        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(subId)
            if (bundle != null) {
                data.writeInt(1)
                bundle.writeToParcel(data, 0)
            } else {
                data.writeInt(0)
            }
            data.writeInt(if (persistent) 1 else 0)
            binder.transact(TRANSACTION_OVERRIDE_CONFIG, data, reply, 0)
            reply.readException()
            CarrierResult.Ok
        } catch (e: Exception) {
            CarrierResult.TransactionFailed(e.message ?: e.javaClass.simpleName)
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
}
