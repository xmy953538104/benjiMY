package org.kenjinx.android

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import android.content.pm.ActivityInfo
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import org.kenjinx.android.viewmodels.GameInfo

class ShortcutWizardActivity : Activity() {

    private val REQ_PICK_GAME = 1
    private val REQ_PICK_ICON = 2

    private var pickedGameUri: Uri? = null
    private var pendingLabel: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        requestGameFile()
    }

    private fun requestGameFile() {
        val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        startActivityForResult(pick, REQ_PICK_GAME)
    }

    @Deprecated("simple flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQ_PICK_GAME) {
            if (resultCode != RESULT_OK) { finish(); return }
            val uri = data?.data ?: run { finish(); return }
            pickedGameUri = uri
            ShortcutUtils.persistReadWrite(this, uri)

            val suggested = ShortcutUtils.suggestLabelFromUri(uri)
            val input = EditText(this).apply { setText(suggested) }

            AlertDialog.Builder(this)
                .setTitle("Shortcut name")
                .setView(input)
                .setPositiveButton("Next (Icon)") { _, _ ->
                    val label = input.text?.toString()?.takeIf { it.isNotBlank() } ?: suggested
                    pendingLabel = label
                    requestIconImage()
                }
                .setNeutralButton("Use app icon") { _, _ ->
                    val label = input.text?.toString()?.takeIf { it.isNotBlank() } ?: suggested
                    val bmp = pickedGameUri?.let { loadGridIconBitmap(it) }
                    createShortcut(label, bmp)
                }
                .setNegativeButton("Cancel") { _, _ -> finish() }
                .setCancelable(false)
                .show()
            return
        }

        if (requestCode == REQ_PICK_ICON) {
            if (resultCode != RESULT_OK) {
                val label = pendingLabel ?: "Start Game"
                createShortcut(label, null)
                return
            }
            val imageUri = data?.data
            val bmp = imageUri?.let { loadBitmap(it) }
            val label = pendingLabel ?: "Start Game"
            createShortcut(label, bmp)
            return
        }

        finish()
    }

    private fun requestIconImage() {
        val pickIcon = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        startActivityForResult(Intent.createChooser(pickIcon, "Select icon"), REQ_PICK_ICON)
    }

    private fun loadBitmap(uri: Uri): Bitmap? =
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        }

    private fun loadGridIconBitmap(gameUri: Uri): Bitmap? {
        return try {
            val doc = DocumentFile.fromSingleUri(this, gameUri)
            val name = doc?.name ?: return null
            val ext = name.substringAfterLast('.', "").lowercase()

            val pfd = contentResolver.openFileDescriptor(gameUri, "r") ?: return null
            pfd.use {
                val info = GameInfo()
                KenjinxNative.deviceGetGameInfo(it.fd, ext, info)
                val b64 = info.Icon ?: return null
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun createShortcut(label: String, bmp: Bitmap?) {
        val gameUri = pickedGameUri
        if (gameUri == null) { finish(); return }

        val ok = ShortcutUtils.pinShortcutForGame(
            activity = this,
            gameUri = gameUri,
            label = label,
            iconBitmap = bmp
        ) {
            finish()
        }

        Toast.makeText(
            this,
            if (ok) "Shortcut “$label” created." else "Shortcut failed.",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onResume() {
        super.onResume()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}
