package com.myfinances.app.platform

actual object Platform {
    actual val name: String = System.getProperty("os.name") ?: "Desktop"
}

