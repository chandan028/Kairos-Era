package com.kairosera.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairosera.AppContainer
import com.kairosera.KairosApp

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as KairosApp).container

@Composable
inline fun <reified VM : ViewModel> kairosViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = appContainer()
    return viewModel(key = key) { create(container) }
}
