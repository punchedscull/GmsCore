package org.microg.vending

import android.accounts.AccountManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import org.microg.vending.ui.VendingActivity

class WorkAccountChangedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val accountManager = AccountManager.get(context)
        val hasWorkAccounts = accountManager.getAccountsByType("com.google.work").isNotEmpty()


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Log.d(TAG, "setting VendingActivity state to enabled = $hasWorkAccounts")

            val componentName = ComponentName(
                context,
                VendingActivity::class.java
            )
            context.packageManager.setComponentEnabledSetting(
                componentName,
                if (hasWorkAccounts) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                0
            )
        }
    }

    companion object {
        const val TAG = "GmsVendingWorkAccRcvr"
    }
}