package com.myfinances.app.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser

class DesktopDirectoryPickerService : DirectoryPickerService {
    override val isSupported: Boolean = true

    override suspend fun pickDirectory(initialPath: String?): String? =
        withContext(Dispatchers.Swing) {
            val chooser = JFileChooser().apply {
                dialogTitle = "Choose the downloads folder to watch"
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                isAcceptAllFileFilterUsed = false
                initialPath
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::File)
                    ?.takeIf(File::exists)
                    ?.also { currentDirectory = it }
            }

            val result = chooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile?.absolutePath
            } else {
                null
            }
        }
}
