package org.kenjinx.android

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

object ShortcutUtils {

    fun suggestLabelFromUri(uri: Uri): String {
        val last = uri.lastPathSegment ?: return "Start Game"
        val raw = last.substringAfterLast("%2F").substringAfterLast("/")
        val decoded = Uri.decode(raw)
        return decoded.substringBeforeLast('.').ifBlank { "Start Game" }
    }

    fun persistReadWrite(activity: Activity, uri: Uri) {
        val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try { activity.contentResolver.takePersistableUriPermission(uri, rw) } catch (_: Exception) {}
        try { activity.grantUriPermission(activity.packageName, uri, rw) } catch (_: Exception) {}
        try { activity.grantUriPermission("org.kenjinx.android", uri, rw) } catch (_: Exception) {}
    }

    /**
     * Portrait während des System-Pin-Dialogs erzwingen und erst danach auf LANDSCAPE zurück.
     * Warten auf IntentSender-Callback, sonst Fallback: erster Touch (+2s) oder 15s Timeout.
     * Nach dem Restore optional onCompleted() ausführen (z.B. Activity.finish()).
     */
    fun pinShortcutForGame(
        activity: Activity,
        gameUri: Uri,
        label: String,
        iconBitmap: Bitmap? = null,
        onCompleted: (() -> Unit)? = null
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val sm = activity.getSystemService(ShortcutManager::class.java) ?: return false

        val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val mime = activity.contentResolver.getType(gameUri) ?: "*/*"
        val clip = ClipData.newUri(activity.contentResolver, "GameUri", gameUri)

        val launchIntent = Intent(activity, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(gameUri, mime)
            clipData = clip
            putExtra("bootPath", gameUri.toString())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or rw)
        }

        val icon = iconBitmap?.let { Icon.createWithBitmap(it) }
            ?: Icon.createWithResource(activity, R.mipmap.ic_launcher)

        val shortcut = ShortcutInfo.Builder(activity, "kenji_game_${gameUri.hashCode()}")
            .setShortLabel(label.take(24))
            .setLongLabel(label)
            .setIcon(icon)
            .setIntent(launchIntent)
            .build()

        // ---- Portrait erzwingen ----
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val mainHandler = Handler(Looper.getMainLooper())
        val decorView = activity.window?.decorView
        var restored = false
        var touchScheduled = false
        var pinResultReceiver: BroadcastReceiver? = null

        fun restoreOrientation(@Suppress("UNUSED_PARAMETER") reason: String) {
            if (restored) return
            restored = true
            try { decorView?.setOnTouchListener(null) } catch (_: Exception) {}
            try { pinResultReceiver?.let { activity.unregisterReceiver(it) } } catch (_: Exception) {}
            mainHandler.removeCallbacksAndMessages(null)

            // → explizit auf LANDSCAPE zurück (MainActivity ist Landscape-locked)
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

            // jetzt darf die Wizard-Activity zu, wenn gewünscht
            onCompleted?.invoke()
        }

        //      Old Shortcut Settings, can delete later
        // Touch-Fallback: erster Touch → 2s später
        //val touchListener = View.OnTouchListener { _: View, _: MotionEvent ->
        //    if (!restored && !touchScheduled) {
        //        touchScheduled = true
        //        mainHandler.postDelayed({ restoreOrientation("touch+delay") }, 2000)
        //    }
        //    false
        //}
        //decorView?.setOnTouchListener(touchListener)

        // Harte Obergrenze: 15s
        //mainHandler.postDelayed({ restoreOrientation("timeout15s") }, 15_000)

        // BroadcastReceiver für den IntentSender-Callback
        val ACTION_PIN_RESULT = "${activity.packageName}.PIN_SHORTCUT_RESULT"
        pinResultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                restoreOrientation("intent-callback")
            }
        }
        val filter = IntentFilter(ACTION_PIN_RESULT)
        if (Build.VERSION.SDK_INT >= 33) {
            activity.registerReceiver(pinResultReceiver!!, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            activity.registerReceiver(pinResultReceiver, filter)
        }

        // Anfrage an den Launcher
        if (sm.isRequestPinShortcutSupported) {
            val successIntent = sm.createShortcutResultIntent(shortcut).apply {
                action = ACTION_PIN_RESULT
            }
            val sender = PendingIntent.getBroadcast(
                activity,
                0,
                successIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ).intentSender

            sm.requestPinShortcut(shortcut, sender)
            // Kein sofortiges Restore: wir warten auf Callback/Touch/Timeout
            return true
        } else {
            // Fallback: kein Systemdialog → dynamisch hinzufügen und sofort zurückstellen
            sm.addDynamicShortcuts(listOf(shortcut))
            restoreOrientation("no-pin-support")
            return true
        }
    }
}
