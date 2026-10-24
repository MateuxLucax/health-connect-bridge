package io.github.mateuxlucax.healthconnectbridge.ui

import kotlinx.coroutines.flow.SharingStarted

/**
 * How ViewModels share their UI state: while the screen collects it, plus 5 seconds so a configuration change
 * doesn't restart the upstream flows (and their SharedPreferences listeners).
 */
internal val WhileUiSubscribed: SharingStarted = SharingStarted.WhileSubscribed(5_000)
