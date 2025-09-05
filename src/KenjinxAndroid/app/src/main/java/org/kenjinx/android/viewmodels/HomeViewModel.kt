package org.kenjinx.android.viewmodels

import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.DocumentFileType
import com.anggrayudi.storage.file.extension
import com.anggrayudi.storage.file.search
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.kenjinx.android.MainActivity
import java.util.Locale
import kotlin.concurrent.thread

class HomeViewModel(
    val activity: MainActivity? = null,
    val mainViewModel: MainViewModel? = null
) {
    private var shouldReload: Boolean = false
    private var savedFolder: String = ""
    private var loadedCache: MutableList<GameModel> = mutableListOf()
    private var gameFolderPath: DocumentFile? = null
    private var sharedPref: SharedPreferences? = null
    val gameList: SnapshotStateList<GameModel> = SnapshotStateList()
    val isLoading: MutableState<Boolean> = mutableStateOf(false)

    init {
        if (activity != null) {
            sharedPref = PreferenceManager.getDefaultSharedPreferences(activity)
        }
    }

    fun ensureReloadIfNecessary() {
        val oldFolder = savedFolder
        savedFolder = sharedPref?.getString("gameFolder", "") ?: ""

        if (savedFolder.isNotEmpty() && (shouldReload || savedFolder != oldFolder)) {
            gameFolderPath = DocumentFileCompat.fromFullPath(
                mainViewModel?.activity!!,
                savedFolder,
                documentType = DocumentFileType.FOLDER,
                requiresWriteAccess = true
            )

            reloadGameList()
        }
    }

    fun filter(query: String) {
        gameList.clear()
        gameList.addAll(loadedCache.filter {
            it.titleName != null && it.titleName!!.isNotEmpty() && (query.trim()
                .isEmpty() || it.titleName!!.lowercase(Locale.getDefault())
                .contains(query))
        })
    }

    fun requestReload() {
        shouldReload = true
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun reloadGameList() {
        activity?.storageHelper ?: return
        val folder = gameFolderPath ?: return

        shouldReload = false
        if (isLoading.value)
            return

        gameList.clear()
        loadedCache.clear()
        isLoading.value = true

        thread {
            try {
                for (file in folder.search(true, DocumentFileType.FILE)) {
                    if (file.extension == "xci" || file.extension == "nsp" || file.extension == "nro")
                        activity.let {
                            loadedCache.add(GameModel(file, it))
                        }
                }

                loadedCache.sortWith { itemA, itemB ->
                    val strA = itemA.fileBaseName.toString().lowercase(Locale.ROOT)
                    val strB = itemB.fileBaseName.toString().lowercase(Locale.ROOT)
                    val minLength = minOf(strA.length, strB.length)

                    for (i in 0 until minLength) {
                        val charA = strA[i]
                        val charB = strB[i]

                        if (charA != charB) {
                            return@sortWith charA.code - charB.code
                        }
                    }

                    return@sortWith strA.length - strB.length
                }

                for(game in loadedCache)
                {
                    game.getGameInfo()
                }
            } finally {
                isLoading.value = false
                GlobalScope.launch(Dispatchers.Main){
                    filter("")
                }
            }
        }
    }
}
