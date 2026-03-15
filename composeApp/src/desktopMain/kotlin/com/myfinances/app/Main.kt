package com.myfinances.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.myfinances.app.di.createAppDependencies

fun main() = application {
    val appDependencies = createAppDependencies()

    Window(
        onCloseRequest = ::exitApplication,
        title = "myFinances",
    ) {
        App(appDependencies = appDependencies)
    }
}
