@file:Suppress("unused")

package com.brit.swiftinstaller.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.system.Os
import android.util.Log
import com.android.apksig.ApkSigner
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import java.io.*
import java.lang.reflect.InvocationTargetException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.*

@Suppress("unused", "MemberVisibilityCanBePrivate")
object ShellUtils {

    private val TAG = ShellUtils::class.java.simpleName

    val isRootAvailable: Boolean
        get() {
            return Shell.rootAccess()
        }

    fun listFiles(path: String): List<String> {
        val f = SuFile(path)
        return f.list().toList()
    }

    fun inputStreamToString(`is`: InputStream?): String {
        val s = java.util.Scanner(`is`!!).useDelimiter("\\A")
        return if (s.hasNext()) s.next() else ""
    }

    fun copyFile(path: String, output: String): Boolean {
        return SuFile(path).renameTo(SuFile(output))
    }

    fun mkdir(path: String): Boolean {
        return SuFile(path).mkdirs()
    }

    fun setPermissions(perms: Int, path: String) {
        runCommand("chmod $perms $path", true)
    }

    fun compileOverlay(context: Context, themePackage: String, res: String?, manifest: String,
                       overlayPath: String, assetPath: String?, targetInfo: ApplicationInfo?): CommandOutput {
        val overlay = File(overlayPath)
        @Suppress("LocalVariableName")
        val unsigned_unaligned = File(overlay.parent, "unsigned_unaligned" + overlay.name)
        val unsigned = File(overlay.parent, "unsigned_${overlay.name}")
        val overlayFolder = File(context.cacheDir.toString() + "/" + themePackage + "/overlays")
        if (!overlayFolder.exists()) {
            if (!overlayFolder.mkdirs()) {
                Log.e(TAG, "Unable to create " + overlayFolder.absolutePath)
            }
        }

        if (fileExists(unsigned_unaligned.absolutePath) && !deleteFileShell(unsigned_unaligned.absolutePath)) {
            Log.e(TAG, "Unable to delete " + unsigned_unaligned.absolutePath)
        }
        if (fileExists(unsigned.absolutePath) && !deleteFileShell(unsigned.absolutePath)) {
            Log.e(TAG, "Unable to delete " + unsigned.absolutePath)
        }
        if (fileExists(overlay.absolutePath) && !deleteFileShell(overlay.absolutePath)) {
            Log.e(TAG, "Unable to delete " + overlay.absolutePath)
        }

        val cmd = StringBuilder()
        cmd.append(getAapt(context))
        cmd.append(" p")
        cmd.append(" -M ").append(manifest)
        if (res != null) {
            cmd.append(" -S ").append(res)
        }
        if (assetPath != null) {
            cmd.append(" -A ").append(assetPath)
        }
        cmd.append(" -I ").append("/system/framework/framework-res.apk")
        if (targetInfo != null && targetInfo.packageName != "android") {
            cmd.append(" -I ").append(targetInfo.sourceDir)
        }
        cmd.append(" -F ").append(unsigned_unaligned.absolutePath)
        var result = Shell.sh(cmd.toString()).exec()

        // Zipalign
        if (unsigned_unaligned.exists()) {
            val zipalign = StringBuilder()
            zipalign.append(getZipalign(context))
            zipalign.append(" 4")
            zipalign.append(" ${unsigned_unaligned.absolutePath}")
            zipalign.append(" ${unsigned.absolutePath}")
            result = Shell.sh(zipalign.toString()).exec()
        }

        if (unsigned.exists()) {
            runCommand("chmod 777 $unsigned", false)
            val key = File(context.dataDir, "/signing-key")
            val keyPass = "overlay".toCharArray()
            if (key.exists()) {
                key.delete()
            }
            //Utils.makeKey(key)

            val ks = KeyStore.getInstance(KeyStore.getDefaultType())
            ks.load(context.assets.open("signing-key"), keyPass)
            val pk = ks.getKey("key", keyPass) as PrivateKey
            val certs = ArrayList<X509Certificate>()
            certs.add(ks.getCertificateChain("key")[0] as X509Certificate)
            val signConfig = ApkSigner.SignerConfig.Builder("overlay", pk, certs).build()
            val signConfigs = ArrayList<ApkSigner.SignerConfig>()
            signConfigs.add(signConfig)
            val signer = ApkSigner.Builder(signConfigs)
            signer.setV1SigningEnabled(false)
                    .setV2SigningEnabled(true)
                    .setInputApk(unsigned)
                    .setOutputApk(overlay)
                    .setMinSdkVersion(Build.VERSION.SDK_INT)
                    .build()
                    .sign()
        }
        return resultToOutput(result)
    }

    private fun getAapt(context: Context): String? {
        val aapt = File(context.cacheDir, "aapt")
        if (aapt.exists()) return aapt.absolutePath
        if (!AssetHelper.copyAsset(context.assets, "aapt${getArchString()}", aapt.absolutePath, null)) {
            return null
        }
        Os.chmod(aapt.absolutePath, 755)
        return aapt.absolutePath
    }

    private fun getZipalign(context: Context): String? {
        val zipalign = File(context.cacheDir, "zipalign")
        if (zipalign.exists()) return zipalign.absolutePath
        if (!AssetHelper.copyAsset(context.assets, "zipalign${getArchString()}", zipalign.absolutePath, null)) {
            return null
        }
        Os.chmod(zipalign.absolutePath, 755)
        return zipalign.absolutePath
    }

    private fun getArchString(): String {
        if (Arrays.toString(Build.SUPPORTED_ABIS).contains("86")) {
            return "86"
        } else {
            if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) {
                return "64"
            }
        }
        return ""
    }
}

fun runCommand(cmd: String): CommandOutput {
    return runCommand(cmd, false)
}

fun runCommand(cmd: String, root: Boolean): CommandOutput {

    val result = if (root) {
        Shell.su(cmd).exec()
    } else {
        Shell.sh(cmd).exec()
    }
    return resultToOutput(result)
}

fun fileExists(path: String): Boolean {
    val output = runCommand("ls $path")
    return output.exitCode == 0
}

fun remountRW(path: String): Boolean {
    return remount(path, "rw")
}

fun remountRO(path: String): Boolean {
    return remount(path, "ro")
}

private fun remount(path: String, type: String): Boolean {
    val readlink = runCommand("readlink $(which mount)")
    val mount: CommandOutput
    mount = if (readlink.output!!.contains("toolbox")) {
        runCommand("mount -o remount,$type $path")
    } else {
        runCommand("mount -o $type,remount $path")
    }
    return mount.exitCode == 0
}

fun deleteFileShell(path: String): Boolean {
    val output = runCommand("rm -rf $path", false)
    return output.exitCode == 0
}

fun deleteFileRoot(path: String): Boolean {
    val output = runCommand("rm -rf $path", true)
    return output.exitCode == 0
}

fun fileExistsRoot(path: String) : Boolean {
    val output = runCommand("test -f $path", true)
    return output.exitCode == 0
}

@SuppressLint("PrivateApi")
fun getProperty(name: String): String? {
    try {
        @SuppressLint("PrivateApi") val clazz = Class.forName("android.os.SystemProperties")
        val m = clazz.getDeclaredMethod("get", String::class.java)
        return m.invoke(null, name) as String
    } catch (e: ClassNotFoundException) {
        e.printStackTrace()
        return null
    } catch (e: NoSuchMethodException) {
        e.printStackTrace()
        return null
    } catch (e: InvocationTargetException) {
        e.printStackTrace()
        return null
    } catch (e: IllegalAccessException) {
        e.printStackTrace()
        return null
    }
}

fun getProperty(name: String, def: String): String {
    val value = getProperty(name)
    return if (value.isNullOrEmpty()) { def } else { value!! }
}

private fun resultToOutput(result: Shell.Result) : CommandOutput {
    var out = ""
    for (r in result.out) {
        out += "${r.trim()}\n"
    }
    var err = ""
    for (r in result.err) {
        err += "${r.trim()}\n"
    }
    return CommandOutput(out, err, result.code)
}
