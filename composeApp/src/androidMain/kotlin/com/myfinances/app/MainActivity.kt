package com.myfinances.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.myfinances.app.di.createAppDependencies

class MainActivity : ComponentActivity() {
    private val appDependencies by lazy {
        createAppDependencies(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App(appDependencies = appDependencies)
        }
    }
}
