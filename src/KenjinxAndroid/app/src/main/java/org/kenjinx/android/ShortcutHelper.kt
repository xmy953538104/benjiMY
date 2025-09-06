package org.kenjinx.android

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

object ShortcutHelper {

    /**
     * Erstellt einen Shortcut zum Starten eines Spiels.
     *
     * @param context           Context
     * @param title             Anzeigename des Shortcuts
     * @param bootPathUri       URI/String, den deine MainActivity beim Intent auswertet
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
        // Intent, der dein Spiel startet (du wertest ACTION + Extras bereits in MainActivity.handleIntent() aus)
        val launchIntent = Intent("org.kenjinx.android.LAUNCH_GAME").apply {
            setPackage(context.packageName)
            putExtra("bootPath", bootPathUri)
            putExtra("forceNceAndPptc", false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        // Fallback: App-Icon
        var icon = IconCompat.createWithResource(context, R.mipmap.ic_launcher)

        // Falls gewünscht und vorhanden: Grid-Icon benutzen
        if (useGridIcon) {
            val bmp = gridIconBitmap ?: decodeBase64ToBitmap(gridIconBase64)
            if (bmp != null) {
                icon = IconCompat.createWithBitmap(bmp)
            }
        }

        // Shortcut bauen
        val shortcut = ShortcutInfoCompat.Builder(context, makeStableId(title, bootPathUri))
            .setShortLabel(title)
            .setLongLabel(title)
            .setIcon(icon)
            .setIntent(launchIntent)
            .build()

        // Optional: Callback wenn das Pinnen abgeschlossen ist (keine Pflicht)
        val callbackIntent = ShortcutManagerCompat.createShortcutResultIntent(context, shortcut)
        val successCallback = PendingIntent.getBroadcast(
            context,
            0,
            callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        ShortcutManagerCompat.requestPinShortcut(context, shortcut, successCallback.intentSender)
    }

    /**
     * Stabiles ID-Schema, damit derselbe Titel/bootPath nicht zigmal dupliziert wird.
     */
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
