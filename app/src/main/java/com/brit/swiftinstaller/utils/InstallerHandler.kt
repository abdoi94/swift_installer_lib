package com.brit.swiftinstaller.utils

import android.content.Context
import android.content.Intent
import android.support.v4.content.LocalBroadcastManager
import com.brit.swiftinstaller.IInstallerCallback

class InstallerHandler(context: Context) : IInstallerCallback.Stub() {

    private val mContext = context

    companion object {
        const val INSTALL_PROGRESS = "com.brit.INSTALL_PROGRESS"
        const val INSTALL_STARTED = "com.brit.INSTALL_STARTED"
        const val INSTALL_COMPLETE = "com.brit.INSTALL_COMPLETE"
        const val INSTALL_FAILED = "com.brit.INSTALL_FAILED"
    }

    override fun installStarted() {
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(Intent(INSTALL_STARTED))
    }

    override fun progressUpdate(label: String?, progress: Int, max: Int, uninstall: Boolean) {
        val intent = Intent(INSTALL_PROGRESS)
        intent.putExtra("label", label)
        intent.putExtra("progress", progress)
        intent.putExtra("max", max)
        intent.putExtra("uninstall", uninstall)
        LocalBroadcastManager.getInstance(mContext).sendBroadcastSync(intent)
    }

    override fun installComplete(uninstall: Boolean) {
        val intent = Intent(INSTALL_COMPLETE)
        intent.putExtra("uninstall", uninstall)
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent)
    }

    override fun installFailed(errorLog: String, packageName: String) {
        val intent = Intent(INSTALL_FAILED)
        intent.putExtra("errorLog", errorLog)
        intent.putExtra("packageName", packageName)
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent)
    }

}