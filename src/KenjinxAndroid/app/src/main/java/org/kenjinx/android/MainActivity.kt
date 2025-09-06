package org.kenjinx.android

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.anggrayudi.storage.SimpleStorageHelper
import com.sun.jna.JNIEnv
import org.kenjinx.android.ui.theme.KenjinxAndroidTheme
import org.kenjinx.android.viewmodels.MainViewModel
import org.kenjinx.android.viewmodels.QuickSettings
import org.kenjinx.android.viewmodels.GameModel
import org.kenjinx.android.views.MainView

class MainActivity : BaseActivity() {
    private var physicalControllerManager: PhysicalControllerManager =
        PhysicalControllerManager(this)
    private lateinit var motionSensorManager: MotionSensorManager
    private var _isInit: Boolean = false
    private val handler = Handler(Looper.getMainLooper())
    private val delayedHandleIntent = object : Runnable {
        override fun run() {
            handleIntent()
        }
    }
    var storedIntent: Intent = Intent()
    var isGameRunning = false
    var isActive = false
    var storageHelper: SimpleStorageHelper? = null
    lateinit var uiHandler: UiHandler

    companion object {
        var mainViewModel: MainViewModel? = null
        var AppPath: String = ""
        var StorageHelper: SimpleStorageHelper? = null

        @JvmStatic
        fun frameEnded() {
            mainViewModel?.activity?.apply {
                if (isActive && QuickSettings(this).enablePerformanceMode) {
                    mainViewModel?.performanceManager?.setTurboMode(true)
                }
            }
            mainViewModel?.gameHost?.hideProgressIndicator()
        }

        // <<< NEU: wird von der Native/Lib-Seite aufgerufen, um den Ladefortschritt zu setzen
        @JvmStatic
        fun updateProgress(info: String, percent: Float) {
            mainViewModel?.gameHost?.setProgress(info, percent)
        }
    }

    init {
        storageHelper = SimpleStorageHelper(this)
        StorageHelper = storageHelper
        System.loadLibrary("kenjinxjni")
        initVm()
    }

    private external fun initVm()

    private fun initialize() {
        if (_isInit)
            return

        val appPath: String = AppPath

        var quickSettings = QuickSettings(this)
        KenjinxNative.loggingSetEnabled(
            LogLevel.Info,
            quickSettings.enableInfoLogs
        )
        KenjinxNative.loggingSetEnabled(
            LogLevel.Stub,
            quickSettings.enableStubLogs
        )
        KenjinxNative.loggingSetEnabled(
            LogLevel.Warning,
            quickSettings.enableWarningLogs
        )
        KenjinxNative.loggingSetEnabled(
            LogLevel.Error,
            quickSettings.enableErrorLogs
        )
        KenjinxNative.loggingSetEnabled(
            LogLevel.AccessLog,
            quickSettings.enableFsAccessLogs
        )
        KenjinxNative.loggingSetEnabled(
            LogLevel.Guest,
            quickSettings.enableGuestLogs
        )
        KenjinxNative.loggingSetEnabled(
            LogLevel.Trace,
            quickSettings.enableTraceLogs
        )
        KenjinxNative.loggingSetEnabled(
            LogLevel.Debug,
            quickSettings.enableDebugLogs
        )
        KenjinxNative.loggingEnabledGraphicsLog(
            quickSettings.enableGraphicsLogs
        )

        _isInit = KenjinxNative.javaInitialize(appPath, JNIEnv.CURRENT)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        motionSensorManager = MotionSensorManager(this)
        Thread.setDefaultUncaughtExceptionHandler(crashHandler)

        if (!Environment.isExternalStorageManager()) {
            storageHelper?.storage?.requestFullStorageAccess()
        }

        AppPath = this.getExternalFilesDir(null)!!.absolutePath

        initialize()

        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // >>> Wichtig: UI-Handler initialisieren (für Software-Keyboard/Dialog)
        uiHandler = UiHandler()

        mainViewModel = MainViewModel(this)
        mainViewModel!!.physicalControllerManager = physicalControllerManager
        mainViewModel!!.motionSensorManager = motionSensorManager

        mainViewModel!!.refreshFirmwareVersion()

        mainViewModel?.apply {
            setContent {
                KenjinxAndroidTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainView.Main(mainViewModel = this)
                    }
                }
            }
        }

        storedIntent = intent
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        storedIntent = intent
    }

    override fun onSaveInstanceState(outState: Bundle) {
        storageHelper?.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        storageHelper?.onRestoreInstanceState(savedInstanceState)
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        event.apply {
            if (physicalControllerManager.onKeyEvent(this))
                return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent?): Boolean {
        ev?.apply {
            physicalControllerManager.onMotionEvent(this)
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun onStop() {
        super.onStop()
        isActive = false

        if (isGameRunning) {
            mainViewModel?.performanceManager?.setTurboMode(false)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(delayedHandleIntent, 10)
        isActive = true

        if (isGameRunning) {
            if (QuickSettings(this).enableMotion)
                motionSensorManager.register()
        }
    }

    override fun onPause() {
        super.onPause()
        isActive = false

        if (isGameRunning) {
            mainViewModel?.performanceManager?.setTurboMode(false)
        }

        motionSensorManager.unregister()
    }

    private fun handleIntent() {
        when (storedIntent.action) {
            Intent.ACTION_VIEW, "org.kenjinx.android.LAUNCH_GAME" -> {
                val bootPath = storedIntent.getStringExtra("bootPath")
                val forceNceAndPptc = storedIntent.getBooleanExtra("forceNceAndPptc", false)

                // NEU: Optional mitgeben – hilft beim Fallback, wenn bootPath nicht mehr gilt
                val extraTitleId = storedIntent.getStringExtra("titleId")?.lowercase()
                val extraTitleName = storedIntent.getStringExtra("titleName")

                // 1) Normaler Weg: direkter URI
                var documentFile: DocumentFile? = null
                if (bootPath != null) {
                    val uri = bootPath.toUri()
                    documentFile = DocumentFile.fromSingleUri(this, uri)
                }

                // 2) Fallback: Wenn der URI nicht (mehr) geht, suche per TitleId/Name im Spiele-Ordner
                if (documentFile == null || !documentFile.exists()) {
                    documentFile = resolveGameByTitleIdOrName(extraTitleId, extraTitleName)
                }

                if (documentFile != null && documentFile.exists()) {
                    val gameModel = GameModel(documentFile, this)
                    gameModel.getGameInfo()
                    mainViewModel?.loadGameModel?.value = gameModel
                    mainViewModel?.bootPath?.value = "gameItem_${gameModel.titleName}"
                    mainViewModel?.forceNceAndPptc?.value = forceNceAndPptc
                }
            }
        }
    }

    // --- NEU: Hilfsfunktionen für Shortcut-Fallback ---

    private fun resolveGameByTitleIdOrName(titleIdHex: String?, displayName: String?): DocumentFile? {
        val gamesRoot = getDefaultGamesTree() ?: return null
        // Flache Suche (Top-Level). Wenn du Unterordner hast, könntest du hier rekursiv werden.
        for (child in gamesRoot.listFiles()) {
            if (!child.isFile) continue
            // Schneller Name-Check
            if (!displayName.isNullOrBlank()) {
                val n = child.name ?: ""
                if (n.contains(displayName, ignoreCase = true)) {
                    return child
                }
            }
            // Verlässlicher: TitleId auslesen
            if (!titleIdHex.isNullOrBlank()) {
                val tid = getTitleIdFast(child)
                if (tid != null && tid.equals(titleIdHex, ignoreCase = true)) {
                    return child
                }
            }
        }
        // Zweite Runde: erst TitleId prüfen (falls im ersten Durchlauf keins passte), dann Name
        if (!titleIdHex.isNullOrBlank()) {
            for (child in gamesRoot.listFiles()) {
                if (!child.isFile) continue
                val tid = getTitleIdFast(child)
                if (tid != null && tid.equals(titleIdHex, ignoreCase = true)) {
                    return child
                }
            }
        }
        return null
    }

    private fun getDefaultGamesTree(): DocumentFile? {
        // bevorzugt die in den Settings gemerkte SAF-URI
        val vm = mainViewModel
        if (vm?.defaultGameFolderUri != null) {
            return DocumentFile.fromTreeUri(this, vm.defaultGameFolderUri!!)
        }
        // Fallback: alter Legacy-Pfad in den Preferences (falls noch gesetzt)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val legacyPath = prefs.getString("gameFolder", null)
        if (!legacyPath.isNullOrEmpty()) {
            // Es gibt hier keine direkte Baum-URI – ohne SAF-URI kann Android den Ordner i.d.R. nicht listen
            // => Gib null zurück, damit wir nicht in eine Sackgasse laufen
        }
        return null
    }

    private fun getTitleIdFast(file: DocumentFile): String? {
        val name = file.name ?: return null
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot >= name.length - 1) return null
        val ext = name.substring(dot + 1).lowercase()
        return try {
            contentResolver.openFileDescriptor(file.uri, "r")?.use { pfd ->
                val info = org.kenjinx.android.viewmodels.GameInfo() // JNA-Struktur (Java)
                KenjinxNative.deviceGetGameInfo(pfd.fd, ext, info)
                info.TitleId?.lowercase()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun shutdownAndRestart() {
        // Create an intent to restart the app
        val packageManager = packageManager
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val componentName = intent?.component
        val restartIntent = Intent.makeRestartActivityTask(componentName)

        // Clean up resources if needed
        mainViewModel?.let {
            it.performanceManager?.setTurboMode(false)
        }

        // Start the new activity directly
        startActivity(restartIntent)

        // Force immediate process termination
        Runtime.getRuntime().exit(0)
    }
}
