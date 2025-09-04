package org.kenjinx.android

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity

class OrientationBridgeActivity : ComponentActivity() {

    companion object {
        const val EXTRA_INITIAL_URI = "extra_initial_uri"
        private const val REQ_OPEN_DOC = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Frei drehbar
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/octet-stream", // NSP/NSZ/XCI
                "application/x-nintendo-switch-xci",
                "application/x-nsp",
                "application/zip",
                "application/x-zip-compressed"
            ))

            // Optionaler Startordner
            (intent?.getParcelableExtra(EXTRA_INITIAL_URI) as? Uri)?.let { init ->
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, init)
            }

            addFlags(
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }

        startActivityForResult(pick, REQ_OPEN_DOC)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_OPEN_DOC) {
            if (resultCode == Activity.RESULT_OK && data?.data != null) {
                val uri = data.data!!
                // Rechte gleich persistieren, damit Shortcut später stabil ist
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: SecurityException) {}

                setResult(Activity.RESULT_OK, Intent().setData(uri))
            } else {
                setResult(Activity.RESULT_CANCELED)
            }
            finish()
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
