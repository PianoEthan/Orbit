package com.qx.orbit.bili.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.ColorScheme

internal class PageColorSchemeViewModel : ViewModel() {
    private val schemes = mutableMapOf<String, MutableState<ColorScheme?>>()

    fun stateFor(identity: String): MutableState<ColorScheme?> =
        schemes.getOrPut(identity) { mutableStateOf(null) }
}

@Composable
internal fun rememberPageColorScheme(identity: String): MutableState<ColorScheme?> {
    val model: PageColorSchemeViewModel = viewModel()
    return model.stateFor(identity)
}
