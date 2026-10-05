package com.saagarsoni.dialer

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager

object DefaultDialer {

    const val REQ = 77

    fun isDefault(ctx: Context): Boolean {
        return try {
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.defaultDialerPackage == ctx.packageName
        } catch (e: Exception) {
            false
        }
    }

    fun request(act: Activity) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val rm = act.getSystemService(Context.ROLE_SERVICE) as RoleManager
                if (rm.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    act.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER), REQ)
                } else {
                    act.toast("Is phone mein dialer role available nahi hai")
                }
            } else {
                val i = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                i.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, act.packageName)
                act.startActivityForResult(i, REQ)
            }
        } catch (e: Exception) {
            act.toast("Default dialer set nahi ho paya")
        }
    }
}
