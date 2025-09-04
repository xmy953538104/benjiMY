package org.kenjinx.android.viewmodels

import android.annotation.SuppressLint
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.NavHostController
import com.anggrayudi.storage.extension.launchOnUiThread
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import org.kenjinx.android.GameController
import org.kenjinx.android.GameHost
import org.kenjinx.android.KenjinxNative
import org.kenjinx.android.Logging
import org.kenjinx.android.MainActivity
import org.kenjinx.android.MotionSensorManager
import org.kenjinx.android.NativeGraphicsInterop
import org.kenjinx.android.NativeHelpers
import org.kenjinx.android.PerformanceManager
import org.kenjinx.android.PhysicalControllerManager
import org.kenjinx.android.RegionCode
import org.kenjinx.android.SystemLanguage
import org.kenjinx.android.UiHandler
import java.io.File

@SuppressLint("WrongConstant")
class MainViewModel(val activity: MainActivity) {
    var physicalControllerManager: PhysicalControllerManager? = null
    var motionSensorManager: MotionSensorManager? = null
    var gameModel: GameModel? = null
    var controller: GameController? = null
    var performanceManager: PerformanceManager? = null
    var selected: GameModel? = null

    val loadGameModel: MutableState<GameModel?> = mutableStateOf<GameModel?>(null)
    val bootPath: MutableState<String?> = mutableStateOf<String?>(null)
    val forceNceAndPptc: MutableState<Boolean> = mutableStateOf(false)

    var isMiiEditorLaunched = false
    val userViewModel = UserViewModel()
    val logging = Logging(this)
    var firmwareVersion = ""

    private var gameTimeState: MutableState<Double>? = null
    private var gameFpsState: MutableState<Double>? = null
    private var fifoState: MutableState<Double>? = null
    private var usedMemState: MutableState<Int>? = null
    private var totalMemState: MutableState<Int>? = null
    private var frequenciesState: MutableList<Double>? = null
    private var progress: MutableState<String>? = null
    private var progressValue: MutableState<Float>? = null
    private var showLoading: MutableState<Boolean>? = null
    private var refreshUser: MutableState<Boolean>? = null

    var gameHost: GameHost? = null
        set(value) {
            field = value
            field?.setProgressStates(showLoading, progressValue, progress)
        }

    var navController: NavHostController? = null
    var homeViewModel: HomeViewModel = HomeViewModel(activity, this)

    init {
        performanceManager = PerformanceManager(activity)
    }

    fun refreshFirmwareVersion() {
        firmwareVersion = KenjinxNative.jnaInstance.deviceGetInstalledFirmwareVersion()
    }

    fun closeGame() {
        KenjinxNative.jnaInstance.deviceSignalEmulationClose()
        gameHost?.close()
        KenjinxNative.jnaInstance.deviceCloseEmulation()
        motionSensorManager?.unregister()
        physicalControllerManager?.disconnect()
        motionSensorManager?.setControllerId(-1)
    }

    fun loadGame(
        game: GameModel,
        overrideSettings: Boolean? = false,
        forceNceAndPptc: Boolean? = false
    ): Int {
        KenjinxNative.jnaInstance.deviceReinitEmulation()
        MainActivity.mainViewModel?.activity?.uiHandler = UiHandler()

        val descriptor = game.open()
        if (descriptor == 0) return 0

        val update = game.openUpdate()
        if (update == -2) {
            return -2
        }

        gameModel = game
        isMiiEditorLaunched = false

        val settings = QuickSettings(activity)
        if (overrideSettings == true) {
            settings.overrideSettings(forceNceAndPptc)
        }

        var success = KenjinxNative.jnaInstance.graphicsInitialize(
            enableMacroHLE = settings.enableMacroHLE,
            enableShaderCache = settings.enableShaderCache,
            enableTextureRecompression = settings.enableTextureRecompression,
            rescale = settings.resScale,
            maxAnisotropy = settings.maxAnisotropy,
            backendThreading = org.kenjinx.android.BackendThreading.Auto.ordinal
        )
        if (!success) return 0

        val nativeHelpers = NativeHelpers.instance
        val nativeInterop = NativeGraphicsInterop().apply {
            VkRequiredExtensions = arrayOf("VK_KHR_surface", "VK_KHR_android_surface")
            VkCreateSurface = nativeHelpers.getCreateSurfacePtr()
            SurfaceHandle = 0
        }

        val driverViewModel = VulkanDriverViewModel(activity)
        val drivers = driverViewModel.getAvailableDrivers()
        var driverHandle = 0L

        if (driverViewModel.selected.isNotEmpty()) {
            val metaData = drivers.find { it.driverPath == driverViewModel.selected }
            metaData?.apply {
                val privatePath = activity.filesDir
                val privateDriverPath = privatePath.canonicalPath + "/driver/"
                val pD = File(privateDriverPath)
                if (pD.exists()) pD.deleteRecursively()
                pD.mkdirs()

                val driver = File(driverViewModel.selected)
                val parent = driver.parentFile
                if (parent != null) {
                    for (file in parent.walkTopDown()) {
                        if (file.absolutePath == parent.absolutePath) continue
                        file.copyTo(File(privateDriverPath + file.name), true)
                    }
                }

                driverHandle = NativeHelpers.instance.loadDriver(
                    activity.applicationInfo.nativeLibraryDir!! + "/",
                    privateDriverPath,
                    this.libraryName
                )
            }
        }

        val extensions = nativeInterop.VkRequiredExtensions
        success = KenjinxNative.jnaInstance.graphicsInitializeRenderer(
            extensions!!,
            extensions.size,
            driverHandle
        )
        if (!success) return 0

        val semaphore = Semaphore(1, 0)
        runBlocking {
            semaphore.acquire()
            launchOnUiThread {
                // Emulation-Kontext muss im Main-Thread initialisiert werden
                success = KenjinxNative.jnaInstance.deviceInitialize(
                    settings.memoryManagerMode.ordinal,
                    settings.useNce,
                    settings.memoryConfiguration.ordinal,
                    SystemLanguage.AmericanEnglish.ordinal,
                    RegionCode.USA.ordinal,
                    settings.vSyncMode.ordinal,
                    settings.enableDocked,
                    settings.enablePptc,
                    /* enableLowPowerPptc */ false,
                    settings.enableFsIntegrityChecks,
                    settings.fsGlobalAccessLogMode,
                    "UTC",
                    settings.ignoreMissingServices
                )
                semaphore.release()
            }
            semaphore.acquire()
            semaphore.release()
        }
        if (!success) return 0

        success = KenjinxNative.jnaInstance.deviceLoadDescriptor(descriptor, game.type.ordinal, update)
        return if (success) 1 else 0
    }

    fun loadMiiEditor(): Boolean {
        gameModel = null
        isMiiEditorLaunched = true

        val settings = QuickSettings(activity)
        var success = KenjinxNative.jnaInstance.graphicsInitialize(
            enableMacroHLE = settings.enableMacroHLE,
            enableShaderCache = settings.enableShaderCache,
            enableTextureRecompression = settings.enableTextureRecompression,
            rescale = settings.resScale,
            maxAnisotropy = settings.maxAnisotropy,
            backendThreading = org.kenjinx.android.BackendThreading.Auto.ordinal
        )
        if (!success) return false

        val nativeHelpers = NativeHelpers.instance
        val nativeInterop = NativeGraphicsInterop().apply {
            VkRequiredExtensions = arrayOf("VK_KHR_surface", "VK_KHR_android_surface")
            VkCreateSurface = nativeHelpers.getCreateSurfacePtr()
            SurfaceHandle = 0
        }

        val driverViewModel = VulkanDriverViewModel(activity)
        val drivers = driverViewModel.getAvailableDrivers()
        var driverHandle = 0L

        if (driverViewModel.selected.isNotEmpty()) {
            val metaData = drivers.find { it.driverPath == driverViewModel.selected }
            metaData?.apply {
                val privatePath = activity.filesDir
                val privateDriverPath = privatePath.canonicalPath + "/driver/"
                val pD = File(privateDriverPath)
                if (pD.exists()) pD.deleteRecursively()
                pD.mkdirs()

                val driver = File(driverViewModel.selected)
                val parent = driver.parentFile
                if (parent != null) {
                    for (file in parent.walkTopDown()) {
                        if (file.absolutePath == parent.absolutePath) continue
                        file.copyTo(File(privateDriverPath + file.name), true)
                    }
                }

                driverHandle = NativeHelpers.instance.loadDriver(
                    activity.applicationInfo.nativeLibraryDir!! + "/",
                    privateDriverPath,
                    this.libraryName
                )
            }
        }

        val extensions = nativeInterop.VkRequiredExtensions
        success = KenjinxNative.jnaInstance.graphicsInitializeRenderer(
            extensions!!,
            extensions.size,
            driverHandle
        )
        if (!success) return false

        val semaphore = Semaphore(1, 0)
        runBlocking {
            semaphore.acquire()
            launchOnUiThread {
                success = KenjinxNative.jnaInstance.deviceInitialize(
                    settings.memoryManagerMode.ordinal,
                    settings.useNce,
                    settings.memoryConfiguration.ordinal,
                    SystemLanguage.AmericanEnglish.ordinal,
                    RegionCode.USA.ordinal,
                    settings.vSyncMode.ordinal,
                    settings.enableDocked,
                    settings.enablePptc,
                    /* enableLowPowerPptc */ false,
                    settings.enableFsIntegrityChecks,
                    settings.fsGlobalAccessLogMode,
                    "UTC",
                    settings.ignoreMissingServices
                )
                semaphore.release()
            }
            semaphore.acquire()
            semaphore.release()
        }
        if (!success) return false

        return KenjinxNative.jnaInstance.deviceLaunchMiiEditor()
    }

    fun clearPptcCache(titleId: String) {
        if (titleId.isNotEmpty()) {
            val basePath = MainActivity.AppPath + "/games/$titleId/cache/cpu"
            if (File(basePath).exists()) {
                val caches = mutableListOf<String>()
                val mainCache = basePath + "${File.separator}0"
                File(mainCache).listFiles()?.forEach {
                    if (it.isFile && it.name.endsWith(".cache")) caches.add(it.absolutePath)
                }
                val backupCache = basePath + "${File.separator}1"
                File(backupCache).listFiles()?.forEach {
                    if (it.isFile && it.name.endsWith(".cache")) caches.add(it.absolutePath)
                }
                for (path in caches) File(path).delete()
            }
        }
    }

    fun purgeShaderCache(titleId: String) {
        if (titleId.isNotEmpty()) {
            val basePath = MainActivity.AppPath + "/games/$titleId/cache/shader"
            if (File(basePath).exists()) {
                val caches = mutableListOf<String>()
                File(basePath).listFiles()?.forEach {
                    if (!it.isFile) it.delete()
                    else if (it.name.endsWith(".toc") || it.name.endsWith(".data")) {
                        caches.add(it.absolutePath)
                    }
                }
                for (path in caches) File(path).delete()
            }
        }
    }

    fun deleteCache(titleId: String) {
        fun deleteDirectory(directory: File) {
            if (directory.exists() && directory.isDirectory) {
                directory.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        deleteDirectory(file)
                    } else {
                        file.delete()
                    }
                }
                directory.delete()
            }
        }
        if (titleId.isNotEmpty()) {
            val basePath = MainActivity.AppPath + "/games/$titleId/cache"
            if (File(basePath).exists()) {
                deleteDirectory(File(basePath))
            }
        }
    }

    fun setStatStates(
        fifo: MutableState<Double>,
        gameFps: MutableState<Double>,
        gameTime: MutableState<Double>,
        usedMem: MutableState<Int>,
        totalMem: MutableState<Int>,
        frequencies: MutableList<Double>
    ) {
        fifoState = fifo
        gameFpsState = gameFps
        gameTimeState = gameTime
        usedMemState = usedMem
        totalMemState = totalMem
        frequenciesState = frequencies
    }

    fun updateStats(
        fifo: Double,
        gameFps: Double,
        gameTime: Double
    ) {
        fifoState?.let { it.value = fifo }
        gameFpsState?.let { it.value = gameFps }
        gameTimeState?.let { it.value = gameTime }

        usedMemState?.let { usedMem ->
            totalMemState?.let { totalMem ->
                MainActivity.performanceMonitor.getMemoryUsage(usedMem, totalMem)
            }
        }
        frequenciesState?.let { MainActivity.performanceMonitor.getFrequencies(it) }
    }

    fun setGameController(controller: GameController) {
        this.controller = controller
    }

    fun navigateToGame() {
        navController?.navigate("game")
        activity.isGameRunning = true
        if (QuickSettings(activity).enableMotion) {
            motionSensorManager?.register()
        }
    }

    fun setProgressStates(
        showLoading: MutableState<Boolean>,
        progressValue: MutableState<Float>,
        progress: MutableState<String>
    ) {
        this.showLoading = showLoading
        this.progressValue = progressValue
        this.progress = progress
        gameHost?.setProgressStates(showLoading, progressValue, progress)
    }
}
