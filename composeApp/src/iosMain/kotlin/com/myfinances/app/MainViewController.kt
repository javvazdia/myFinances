package com.myfinances.app

import androidx.compose.ui.window.ComposeUIViewController
import com.myfinances.app.di.createAppDependencies

fun MainViewController() = ComposeUIViewController {
    App(appDependencies = createAppDependencies())
}
