package org.kenjinx.android.viewmodels

import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.anggrayudi.storage.callback.FileCallback
import com.anggrayudi.storage.file.FileFullPath
import com.anggrayudi.storage.file.copyFileTo
import com.anggrayudi.storage.file.extension
import com.anggrayudi.storage.file.getAbsolutePath
import com.anggrayudi.storage.file.openInputStream
import net.lingala.zip4j.io.inputstream.ZipInputStream
import org.kenjinx.android.LogLevel
import org.kenjinx.android.MainActivity
import org.kenjinx.android.KenjinxNative
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

class SettingsViewModel(val activity: MainActivity) {
    var selectedFirmwareVersion: String = ""
    private var previousFileCallback: ((requestCode: Int, files: List<DocumentFile>) -> Unit)?
    private var previousFolderCallback: ((requestCode: Int, folder: DocumentFile) -> Unit)?
    private var sharedPref: SharedPreferences
    var selectedKeyFile: DocumentFile? = null
    var selectedFirmwareFile: DocumentFile? = null

    init {
        sharedPref = getPreferences()
        previousFolderCallback = activity.storageHelper!!.onFolderSelected
        previousFileCallback = activity.storageHelper!!.onFileSelected

        // Speichere ausgewählten Spieleordner in Preferences
        activity.storageHelper!!.onFolderSelected = { _, folder ->
            val p = folder.getAbsolutePath(activity)
            sharedPref.edit().putString("gameFolder", p).apply()
        }
    }

    private fun getPreferences(): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(activity)
    }

    fun initializeState(
        memoryManagerMode: MutableState<MemoryManagerMode>,
        useNce: MutableState<Boolean>,
        memoryConfiguration: MutableState<MemoryConfiguration>,
        vSyncMode: MutableState<VSyncMode>,
        enableDocked: MutableState<Boolean>,
        enablePptc: MutableState<Boolean>,
        enableFsIntegrityChecks: MutableState<Boolean>,
        fsGlobalAccessLogMode: MutableState<Int>,
        ignoreMissingServices: MutableState<Boolean>,
        enableShaderCache: MutableState<Boolean>,
        enableTextureRecompression: MutableState<Boolean>,
        enableMacroHLE: MutableState<Boolean>,
        resScale: MutableState<Float>,
        maxAnisotropy: MutableState<Float>,
        useVirtualController: MutableState<Boolean>,
        isGrid: MutableState<Boolean>,
        useSwitchLayout: MutableState<Boolean>,
        enableMotion: MutableState<Boolean>,
        enablePerformanceMode: MutableState<Boolean>,
        controllerStickSensitivity: MutableState<Float>,
        enableStubLogs: MutableState<Boolean>,
        enableInfoLogs: MutableState<Boolean>,
        enableWarningLogs: MutableState<Boolean>,
        enableErrorLogs: MutableState<Boolean>,
        enableGuestLogs: MutableState<Boolean>,
        enableFsAccessLogs: MutableState<Boolean>,
        enableTraceLogs: MutableState<Boolean>,
        enableDebugLogs: MutableState<Boolean>,
        enableGraphicsLogs: MutableState<Boolean>
    ) {
        memoryManagerMode.value =
            MemoryManagerMode.values()[sharedPref.getInt("memoryManagerMode", MemoryManagerMode.HostMappedUnsafe.ordinal)]
        useNce.value = sharedPref.getBoolean("useNce", true)
        memoryConfiguration.value =
            MemoryConfiguration.values()[sharedPref.getInt("memoryConfiguration", MemoryConfiguration.MemoryConfiguration4GiB.ordinal)]
        vSyncMode.value = VSyncMode.values()[sharedPref.getInt("vSyncMode", VSyncMode.Switch.ordinal)]
        enableDocked.value = sharedPref.getBoolean("enableDocked", true)
        enablePptc.value = sharedPref.getBoolean("enablePptc", true)
        enableFsIntegrityChecks.value = sharedPref.getBoolean("enableFsIntegrityChecks", false)
        fsGlobalAccessLogMode.value = sharedPref.getInt("fsGlobalAccessLogMode", 0)
        ignoreMissingServices.value = sharedPref.getBoolean("ignoreMissingServices", false)
        enableShaderCache.value = sharedPref.getBoolean("enableShaderCache", true)
        enableTextureRecompression.value = sharedPref.getBoolean("enableTextureRecompression", false)
        enableMacroHLE.value = sharedPref.getBoolean("enableMacroHLE", false)
        resScale.value = sharedPref.getFloat("resScale", 1f)
        maxAnisotropy.value = sharedPref.getFloat("maxAnisotropy", 0f)
        useVirtualController.value = sharedPref.getBoolean("useVirtualController", true)
        isGrid.value = sharedPref.getBoolean("isGrid", true)
        useSwitchLayout.value = sharedPref.getBoolean("useSwitchLayout", true)
        enableMotion.value = sharedPref.getBoolean("enableMotion", true)
        enablePerformanceMode.value = sharedPref.getBoolean("enablePerformanceMode", false)
        controllerStickSensitivity.value = sharedPref.getFloat("controllerStickSensitivity", 1.0f)

        enableStubLogs.value = sharedPref.getBoolean("enableStubLogs", false)
        enableInfoLogs.value = sharedPref.getBoolean("enableInfoLogs", true)
        enableWarningLogs.value = sharedPref.getBoolean("enableWarningLogs", true)
        enableErrorLogs.value = sharedPref.getBoolean("enableErrorLogs", true)
        enableGuestLogs.value = sharedPref.getBoolean("enableGuestLogs", true)
        enableFsAccessLogs.value = sharedPref.getBoolean("enableFsAccessLogs", false)
        // BUGFIX: richtiger Key für Trace-Logs
        enableTraceLogs.value = sharedPref.getBoolean("enableTraceLogs", false)
        enableDebugLogs.value = sharedPref.getBoolean("enableDebugLogs", false)
        enableGraphicsLogs.value = sharedPref.getBoolean("enableGraphicsLogs", false)
    }

    fun save(
        memoryManagerMode: MutableState<MemoryManagerMode>,
        useNce: MutableState<Boolean>,
        memoryConfiguration: MutableState<MemoryConfiguration>,
        vSyncMode: MutableState<VSyncMode>,
        enableDocked: MutableState<Boolean>,
        enablePptc: MutableState<Boolean>,
        enableFsIntegrityChecks: MutableState<Boolean>,
        fsGlobalAccessLogMode: MutableState<Int>,
        ignoreMissingServices: MutableState<Boolean>,
        enableShaderCache: MutableState<Boolean>,
        enableTextureRecompression: MutableState<Boolean>,
        enableMacroHLE: MutableState<Boolean>,
        resScale: MutableState<Float>,
        maxAnisotropy: MutableState<Float>,
        useVirtualController: MutableState<Boolean>,
        isGrid: MutableState<Boolean>,
        useSwitchLayout: MutableState<Boolean>,
        enableMotion: MutableState<Boolean>,
        enablePerformanceMode: MutableState<Boolean>,
        controllerStickSensitivity: MutableState<Float>,
        enableStubLogs: MutableState<Boolean>,
        enableInfoLogs: MutableState<Boolean>,
        enableWarningLogs: MutableState<Boolean>,
        enableErrorLogs: MutableState<Boolean>,
        enableGuestLogs: MutableState<Boolean>,
        enableFsAccessLogs: MutableState<Boolean>,
        enableTraceLogs: MutableState<Boolean>,
        enableDebugLogs: MutableState<Boolean>,
        enableGraphicsLogs: MutableState<Boolean>
    ) {
        sharedPref.edit()
            .putInt("memoryManagerMode", memoryManagerMode.value.ordinal)
            .putBoolean("useNce", useNce.value)
            .putInt("memoryConfiguration", memoryConfiguration.value.ordinal)
            .putInt("vSyncMode", vSyncMode.value.ordinal)
            .putBoolean("enableDocked", enableDocked.value)
            .putBoolean("enablePptc", enablePptc.value)
            .putBoolean("enableFsIntegrityChecks", enableFsIntegrityChecks.value)
            .putInt("fsGlobalAccessLogMode", fsGlobalAccessLogMode.value)
            .putBoolean("ignoreMissingServices", ignoreMissingServices.value)
            .putBoolean("enableShaderCache", enableShaderCache.value)
            .putBoolean("enableTextureRecompression", enableTextureRecompression.value)
            .putBoolean("enableMacroHLE", enableMacroHLE.value)
            .putFloat("resScale", resScale.value)
            .putFloat("maxAnisotropy", maxAnisotropy.value)
            .putBoolean("useVirtualController", useVirtualController.value)
            .putBoolean("isGrid", isGrid.value)
            .putBoolean("useSwitchLayout", useSwitchLayout.value)
            .putBoolean("enableMotion", enableMotion.value)
            .putBoolean("enablePerformanceMode", enablePerformanceMode.value)
            .putFloat("controllerStickSensitivity", controllerStickSensitivity.value)
            .putBoolean("enableStubLogs", enableStubLogs.value)
            .putBoolean("enableInfoLogs", enableInfoLogs.value)
            .putBoolean("enableWarningLogs", enableWarningLogs.value)
            .putBoolean("enableErrorLogs", enableErrorLogs.value)
            .putBoolean("enableGuestLogs", enableGuestLogs.value)
            .putBoolean("enableFsAccessLogs", enableFsAccessLogs.value)
            .putBoolean("enableTraceLogs", enableTraceLogs.value)
            .putBoolean("enableDebugLogs", enableDebugLogs.value)
            .putBoolean("enableGraphicsLogs", enableGraphicsLogs.value)
            .apply()

        // ursprünglichen Folder-Callback wiederherstellen (optional)
        activity.storageHelper!!.onFolderSelected = previousFolderCallback

        // Logger-Flags an Native weitergeben
        KenjinxNative.jnaInstance.loggingSetEnabled(LogLevel.Info.ordinal, enableInfoLogs.value)
        KenjinxNative.jnaInstance.loggingSetEnabled(LogLevel.Stub.ordinal, enableStubLogs.value)
        KenjinxNative.jnaInstance.loggingSetEnabled(LogLevel.Warning.ordinal, enableWarningLogs.value)
        KenjinxNative.jnaInstance.loggingSetEnabled(LogLevel.Error.ordinal, enableErrorLogs.value)
        KenjinxNative.jnaInstance.loggingSetEnabled(LogLevel.AccessLog.ordinal, enableFsAccessLogs.value)
        KenjinxNative.jnaInstance.loggingSetEnabled(LogLevel.Guest.ordinal, enableGuestLogs.value)
        KenjinxNative.jnaInstance.loggingSetEnabled(LogLevel.Trace.ordinal, enableTraceLogs.value)
        KenjinxNative.jnaInstance.loggingSetEnabled(LogLevel.Debug.ordinal, enableDebugLogs.value)
        KenjinxNative.jnaInstance.loggingEnabledGraphicsLog(enableGraphicsLogs.value)
    }

    fun openGameFolder() {
        val path = sharedPref.getString("gameFolder", "") ?: ""
        if (path.isEmpty()) {
            activity.storageHelper?.storage?.openFolderPicker()
        } else {
            activity.storageHelper?.storage?.openFolderPicker(
                activity.storageHelper!!.storage.requestCodeFolderPicker,
                FileFullPath(activity, path)
            )
        }
    }

    fun selectKey(installState: MutableState<KeyInstallState>) {
        if (installState.value != KeyInstallState.None) return

        activity.storageHelper!!.onFileSelected = { _, files ->
            activity.storageHelper!!.onFileSelected = previousFileCallback
            val file = files.firstOrNull()
            file?.apply {
                if (name == "prod.keys") {
                    selectedKeyFile = file
                    installState.value = KeyInstallState.Query
                } else {
                    installState.value = KeyInstallState.Cancelled
                }
            }
        }
        activity.storageHelper?.storage?.openFilePicker()
    }

    fun installKey(installState: MutableState<KeyInstallState>) {
        if (installState.value != KeyInstallState.Query) return
        if (selectedKeyFile == null) {
            installState.value = KeyInstallState.None
            return
        }
        selectedKeyFile?.apply {
            val outputFolder = File(MainActivity.AppPath + "/system")
            val outputFile = File(MainActivity.AppPath + "/system/" + name)
            outputFile.delete()
            installState.value = KeyInstallState.Install
            thread {
                Thread.sleep(1000)
                this.copyFileTo(
                    activity,
                    outputFolder,
                    callback = object : FileCallback() {
                        override fun onCompleted(result: Any) {
                            KenjinxNative.jnaInstance.deviceReloadFilesystem()
                            installState.value = KeyInstallState.Done
                        }
                    }
                )
            }
        }
    }

    fun clearKeySelection(installState: MutableState<KeyInstallState>) {
        selectedKeyFile = null
        installState.value = KeyInstallState.None
    }

    fun selectFirmware(installState: MutableState<FirmwareInstallState>) {
        if (installState.value != FirmwareInstallState.None) return

        activity.storageHelper!!.onFileSelected = { _, files ->
            activity.storageHelper!!.onFileSelected = previousFileCallback
            val file = files.firstOrNull()
            file?.apply {
                if (extension == "xci" || extension == "zip") {
                    installState.value = FirmwareInstallState.Verifying
                    thread {
                        Thread.sleep(1000)
                        val descriptor = activity.contentResolver.openFileDescriptor(file.uri, "rw")
                        descriptor?.use { d ->
                            selectedFirmwareVersion =
                                KenjinxNative.jnaInstance.deviceVerifyFirmware(d.fd, extension == "xci")
                            selectedFirmwareFile = file
                            installState.value =
                                if (selectedFirmwareVersion.isNotEmpty()) FirmwareInstallState.Query
                                else FirmwareInstallState.Cancelled
                        }
                    }
                } else {
                    installState.value = FirmwareInstallState.Cancelled
                }
            }
        }
        activity.storageHelper?.storage?.openFilePicker()
    }

    fun installFirmware(installState: MutableState<FirmwareInstallState>) {
        if (installState.value != FirmwareInstallState.Query) return
        if (selectedFirmwareFile == null) {
            installState.value = FirmwareInstallState.None
            return
        }
        selectedFirmwareFile?.apply {
            val descriptor = activity.contentResolver.openFileDescriptor(uri, "rw")
            descriptor?.let {
                installState.value = FirmwareInstallState.Install
                thread {
                    Thread.sleep(1000)
                    try {
                        KenjinxNative.jnaInstance.deviceInstallFirmware(it.fd, extension == "xci")
                    } finally {
                        MainActivity.mainViewModel?.refreshFirmwareVersion()
                        installState.value = FirmwareInstallState.Done
                    }
                }
            }
        }
    }

    fun clearFirmwareSelection(installState: MutableState<FirmwareInstallState>) {
        selectedFirmwareFile = null
        selectedFirmwareVersion = ""
        installState.value = FirmwareInstallState.None
    }

    fun importAppData(
        file: DocumentFile,
        dataImportState: MutableState<DataImportState>
    ) {
        dataImportState.value = DataImportState.Import
        try {
            MainActivity.StorageHelper?.apply {
                val stream = file.openInputStream(storage.context)
                stream?.apply {
                    val folders = listOf("bis", "games", "profiles", "system")
                    for (f in folders) {
                        val dir = File(MainActivity.AppPath + "${File.separator}$f")
                        if (dir.exists()) dir.deleteRecursively()
                        dir.mkdirs()
                    }
                    ZipInputStream(stream).use { zip ->
                        while (true) {
                            val header = zip.nextEntry ?: break
                            if (!folders.any { header.fileName.startsWith(it) }) continue
                            val filePath = MainActivity.AppPath + File.separator + header.fileName
                            if (!header.isDirectory) {
                                val bos = BufferedOutputStream(FileOutputStream(filePath))
                                val bytesIn = ByteArray(4096)
                                var read: Int
                                while (zip.read(bytesIn).also { read = it } > 0) {
                                    bos.write(bytesIn, 0, read)
                                }
                                bos.close()
                            } else {
                                File(filePath).mkdir()
                            }
                        }
                    }
                    stream.close()
                }
            }
        } finally {
            dataImportState.value = DataImportState.Done
            KenjinxNative.jnaInstance.deviceReloadFilesystem()
            MainActivity.mainViewModel?.refreshFirmwareVersion()
        }
    }
}

enum class KeyInstallState {
    None,
    Cancelled,
    Query,
    Install,
    Done
}

enum class FirmwareInstallState {
    None,
    Cancelled,
    Verifying,
    Query,
    Install,
    Done
}

enum class DataImportState {
    None,
    Query,
    Import,
    Done
}
