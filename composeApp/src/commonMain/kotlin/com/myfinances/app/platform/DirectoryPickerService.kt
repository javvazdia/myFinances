package com.myfinances.app.platform

interface DirectoryPickerService {
    val isSupported: Boolean

    suspend fun pickDirectory(initialPath: String? = null): String?
}

object UnsupportedDirectoryPickerService : DirectoryPickerService {
    override val isSupported: Boolean = false

    override suspend fun pickDirectory(initialPath: String?): String? = null
}
