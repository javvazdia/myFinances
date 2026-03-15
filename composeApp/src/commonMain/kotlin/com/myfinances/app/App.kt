package com.myfinances.app

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.myfinances.app.presentation.MyFinancesApp
import com.myfinances.app.theme.MyFinancesTheme

@Composable
@Preview
fun App() {
    MyFinancesTheme {
        MyFinancesApp()
    }
}
