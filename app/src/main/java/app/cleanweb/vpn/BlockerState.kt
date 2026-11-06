package app.cleanweb.vpn

import kotlinx.coroutines.flow.MutableStateFlow

enum class ProtectionStatus { STOPPED, STARTING, RUNNING, PAUSED }

/** Live state of the VPN service, read by the UI, the tile and the notification. */
object BlockerState {
    val status = MutableStateFlow(ProtectionStatus.STOPPED)
    val pausedUntil = MutableStateFlow(0L)
    val ruleCount = MutableStateFlow(0)
    val engineLoading = MutableStateFlow(false)
    val blockedSinceStart = MutableStateFlow(0L)

    /** String resource id of the last problem, shown on the home screen. */
    val lastError = MutableStateFlow<Int?>(null)
}
