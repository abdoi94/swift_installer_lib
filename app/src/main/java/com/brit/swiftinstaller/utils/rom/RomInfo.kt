package com.brit.swiftinstaller.utils.rom

import android.app.ActivityManager
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Build
import android.os.Environment
import com.brit.swiftinstaller.R
import com.brit.swiftinstaller.ui.activities.MainActivity
import com.brit.swiftinstaller.utils.ShellUtils
import com.brit.swiftinstaller.utils.Utils
import com.brit.swiftinstaller.utils.addAppToInstall
import com.brit.swiftinstaller.utils.runCommand
import org.apache.commons.io.FileUtils
import java.io.File

class RomInfo internal constructor(var context: Context, var name: String,
                                   var version: String, vararg vars: String) {
    private val overlayFolder: String? = null

    var defaultAccent: Int = 0
    var overlayDirectory: String

    init {
        defaultAccent = context.getColor(R.color.minimal_blue)
        if (ShellUtils.isRootAvailable)
            overlayDirectory = context.cacheDir.absolutePath
        else
            overlayDirectory = Environment.getExternalStorageDirectory().absolutePath + ".swift-installer"
    }

    val variants = vars

    //TODO expand installer
    val isSamsung: Boolean
        get() = true

    fun preInstall(context: Context, themePackage: String) {
        //TODO
    }

    fun installOverlay(context: Context, targetPackage: String, overlayPath: String) {
        val installed = Utils.isOverlayInstalled(context, Utils.getOverlayPackageName(targetPackage))
        val script = File(Environment.getExternalStorageDirectory(), "run.sh")
        if (!ShellUtils.isRootAvailable) {
            if (script.exists())
                script.delete()
            script.createNewFile()
            var s = FileUtils.readFileToString(script)
            s += "\n"
            s += "adb shell pm install -r $overlayPath"
            s += "\n"
            s += "adb shell cmd overlay enable ${Utils.getOverlayPackageName(targetPackage)}"
            s += "\n"
            FileUtils.writeStringToFile(script, s)
            return
        }
        runCommand("pm install -r " + overlayPath, true)
        if (installed) {
            runCommand("cmd overlay enable " + Utils.getOverlayPackageName(targetPackage), true)
        } else {
            addAppToInstall(context, Utils.getOverlayPackageName(targetPackage))
        }
    }

    fun postInstall(context: Context, targetPackage: String) {
    }

    fun uninstallOverlay(context: Context, packageName: String) {
        runCommand("pm uninstall " + packageName)
    }

    fun createFinishedDialog(activity: MainActivity): Dialog {
        //TODO
        return AlertDialog.Builder(activity)
                .setMessage("Would you like to reboot?")
                .setPositiveButton(android.R.string.ok
                ) { dialog, which -> }
                .setNegativeButton("Later") { dialog, which -> }.create()
    }

    fun isOverlayCompatible(packageName: String): Boolean {
        return true
    }

    companion object {

        private val TAG = "RomInfo"

        @JvmStatic
        private var sInfo: RomInfo? = null

        @Synchronized
        @JvmStatic
        fun getRomInfo(context: Context): RomInfo {
            if (sInfo == null) {
                sInfo = RomInfo(context, "AOSP", Build.VERSION.RELEASE, "type3_Android_8_-_Dark")
            }
            return sInfo!!
        }

        private val isTouchwiz: Boolean
            get() = File("/system/framework/touchwiz.jar").exists()

        private fun isOMS(context: Context): Boolean {
            val am = context.getSystemService(ActivityManager::class.java)!!
            val services = am.getRunningServices(Integer.MAX_VALUE)
            for (info in services) {
                if (info.service.className.contains("IOverlayManager")) {
                    return true
                }
            }
            return false
        }

        fun isSupported(context: Context): Boolean {
            return true
        }
    }
}
