package com.salticusteam.vifi.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * Whether an app modal (dialog, bottom sheet, full-screen picker) is on screen, so app-level presentations wait for
 * it instead of stacking on top or being lost (iOS `ModalPresentation`): a tapped push opens the inbox, and the
 * notification permission flow is shown, only once nothing is in the way. A modal joins by calling
 * [RegisterModalPresence] while it is shown. Provided as [LocalModalPresence] by MainActivity.
 */
@Singleton
class ModalPresence @Inject constructor() {

    private val openCount = MutableStateFlow(0)

    /** How many registered modals are currently shown. */
    val count: StateFlow<Int> = openCount.asStateFlow()

    val isShowing: Boolean get() = openCount.value > 0

    fun opened() {
        openCount.update { it + 1 }
    }

    /** Never goes below zero, so an unbalanced close cannot hide a modal that is still shown. */
    fun closed() {
        openCount.update { (it - 1).coerceAtLeast(0) }
    }

    /** Suspends until no registered modal is shown (returns at once when none is). */
    suspend fun awaitNoModal() {
        openCount.first { it == 0 }
    }
}

/** The app's [ModalPresence]; `null` in previews and tests without a provider, where registering does nothing. */
val LocalModalPresence = staticCompositionLocalOf<ModalPresence?> { null }

/** Counts the calling modal as shown for as long as it stays in the composition. */
@Composable
fun RegisterModalPresence() {
    val presence = LocalModalPresence.current ?: return
    DisposableEffect(presence) {
        presence.opened()
        onDispose { presence.closed() }
    }
}
