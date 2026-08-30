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

    fun getSimSlots(context: Context): List<SimSlot> {
        if (!hasPhonePermission(context)) return emptyList()
        val manager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
            as? SubscriptionManager ?: return emptyList()
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        return try {
            manager.activeSubscriptionInfoList.orEmpty().map { info ->
                val name = info.carrierName?.toString().takeUnless { it.isNullOrBlank() }
                    ?: telephony?.networkOperatorName.orEmpty()
                SimSlot(info.subscriptionId, info.simSlotIndex, name)
            }
        } catch (e: SecurityException) {
            emptyList()
        }
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
        if (!ShizukuHelper.hasPermission()) return CarrierResult.NoShizuku
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
