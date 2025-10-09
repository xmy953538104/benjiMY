package org.kenjinx.android

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

object ShortcutHelper {

    /**
     * Erstellt einen Shortcut zum Starten eines Spiels.
     *
     * @param context           Context
     * @param title             Anzeigename des Shortcuts
     * @param bootPathUri       URI/String der Game-Datei
     * @param useGridIcon       Wenn true, wird das Grid-Icon bevorzugt (Bitmap oder Base64).
     * @param gridIconBitmap    Optional: direkt das Bitmap aus deinem Grid (empfohlen)
     * @param gridIconBase64    Optional: Base64-Icon (falls du das an der Stelle hast)
     */
    fun createGameShortcut(
        context: Context,
        title: String,
        bootPathUri: String,
        useGridIcon: Boolean,
        gridIconBitmap: Bitmap? = null,
        gridIconBase64: String? = null
    ) {
        val uri = runCatching { Uri.parse(bootPathUri) }.getOrNull()

        // --- Icon wählen (Grid-Bitmap > Base64 > App-Icon)
        var icon = IconCompat.createWithResource(context, R.mipmap.ic_launcher)
        if (useGridIcon) {
            val bmp = gridIconBitmap ?: decodeBase64ToBitmap(gridIconBase64)
            if (bmp != null) icon = IconCompat.createWithBitmap(bmp)
        }

        // --- EXPLIZITER Intent exakt wie im funktionierenden Wizard ---
        // ACTION_VIEW + setDataAndType + clipData + GRANT-Flags + Component auf MainActivity
        val launchIntent = Intent(Intent.ACTION_VIEW).apply {
            component = ComponentName(context, MainActivity::class.java)
            if (uri != null) {
                setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                clipData = android.content.ClipData.newUri(
                    context.contentResolver,
                    "GameUri",
                    uri
                )
            }
            putExtra("bootPath", bootPathUri)
            putExtra("forceNceAndPptc", false)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }

        // Bestmögliche Persistierung/Grants (failsafe, falls bereits persistiert → Exceptions ignorieren)
        if (uri != null) {
            val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, rw) }
            runCatching { context.grantUriPermission(context.packageName, uri, rw) }
        }

        // Stabiles ID-Schema, damit derselbe Titel/bootPath nicht zigmal dupliziert wird.
        val shortcutId = makeStableId(title, bootPathUri)

        val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
            .setShortLabel(title)
            .setLongLabel(title)
            .setIcon(icon)
            .setIntent(launchIntent)
            .build()

        // Eigene App-Meldung (wie vorher): direkt vor dem System-Pin-Dialog
        Toast.makeText(context, "Creating shortcut “$title”…", Toast.LENGTH_SHORT).show()

        val callbackIntent = ShortcutManagerCompat.createShortcutResultIntent(context, shortcut)
        val successCallback = PendingIntent.getBroadcast(
            context,
            0,
            callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Anfrage an den Launcher
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, successCallback.intentSender)
    }

    private fun makeStableId(title: String?, bootPath: String?): String {
        val safeTitle = (title ?: "").trim()
        val safeBoot = (bootPath ?: "").trim()
        return "kenjinx_${safeTitle}_${safeBoot}".take(90) // ID muss <100 Zeichen bleiben
    }

    private fun decodeBase64ToBitmap(b64: String?): Bitmap? {
        if (b64.isNullOrBlank()) return null
        return runCatching {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
}
