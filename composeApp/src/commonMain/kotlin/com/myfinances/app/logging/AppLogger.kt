package com.myfinances.app.logging

object AppLogger {
    var isEnabled: Boolean = true

    fun debug(
        tag: String,
        message: String,
    ) {
        if (!isEnabled) return
        println("[myFinances][$tag] $message")
    }

    fun error(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        if (!isEnabled) return
        println("[myFinances][$tag][ERROR] $message")
        throwable?.printStackTrace()
    }
}
