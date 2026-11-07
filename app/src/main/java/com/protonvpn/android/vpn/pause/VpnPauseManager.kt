/*
 * Copyright (c) 2026. Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.android.vpn.pause

import android.os.SystemClock
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PauseState {
    data object Active : PauseState
    data class Paused(val sinceElapsedMs: Long) : PauseState
}

@Singleton
class VpnPauseManager @Inject constructor(
    private val vpnConnectionManager: Lazy<VpnConnectionManager>,
    private val vpnStateMonitor: VpnStateMonitor,
    mainScope: CoroutineScope,
) {
    private val _state = MutableStateFlow<PauseState>(PauseState.Active)
    val state: StateFlow<PauseState> = _state.asStateFlow()

    val isPaused: Boolean get() = _state.value is PauseState.Paused

    @Volatile
    var isPauseRefreshInFlight: Boolean = false
        private set

    init {
        mainScope.launch {
            vpnStateMonitor.status.collect { status ->
                if (status.state != VpnState.Connected && !isPauseRefreshInFlight) {
                    if (_state.value !is PauseState.Active) {
                        ProtonLogger.logCustom(LogCategory.CONN, "Resetting pause state on VPN non-connected state: ${status.state}")
                        _state.value = PauseState.Active
                    }
                }
            }
        }
    }

    suspend fun pause() {
        if (isPaused) {
            ProtonLogger.logCustom(LogCategory.CONN, "VpnPauseManager.pause() ignored: already paused")
            return
        }
        if (vpnStateMonitor.status.value.state != VpnState.Connected) {
            ProtonLogger.logCustom(LogCategory.CONN, "VpnPauseManager.pause() ignored: VPN is not connected")
            return
        }
        ProtonLogger.logCustom(LogCategory.CONN, "Pausing VPN tunnel traffic")
        _state.value = PauseState.Paused(SystemClock.elapsedRealtime())
        isPauseRefreshInFlight = true
        try {
            vpnConnectionManager.get().refreshTunnel("pause")
        } catch (e: Exception) {
            ProtonLogger.logCustom(LogCategory.CONN, "VpnPauseManager.pause() failed: ${e.message}")
            _state.value = PauseState.Active
            throw e
        } finally {
            isPauseRefreshInFlight = false
        }
    }

    suspend fun resume() {
        if (!isPaused) {
            ProtonLogger.logCustom(LogCategory.CONN, "VpnPauseManager.resume() ignored: not paused")
            return
        }
        ProtonLogger.logCustom(LogCategory.CONN, "Resuming VPN tunnel traffic")
        _state.value = PauseState.Active
        isPauseRefreshInFlight = true
        try {
            vpnConnectionManager.get().refreshTunnel("resume")
        } catch (e: Exception) {
            ProtonLogger.logCustom(LogCategory.CONN, "VpnPauseManager.resume() failed: ${e.message}")
            throw e
        } finally {
            isPauseRefreshInFlight = false
        }
    }
}
