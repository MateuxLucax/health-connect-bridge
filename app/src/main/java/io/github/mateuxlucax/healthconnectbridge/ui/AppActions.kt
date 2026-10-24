package io.github.mateuxlucax.healthconnectbridge.ui

/** Actions that need the activity. */
class AppActions(
    val requestHealthPermissions: () -> Unit,
    val requestBatteryExemption: () -> Unit,
    /** Asks for the local network permission (or opens the app's settings once it was denied for good). */
    val requestLocalNetwork: suspend () -> Boolean,
    val openAppSettings: () -> Unit,
)
