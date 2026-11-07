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

package com.protonvpn.android.plus.blacklist

import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlacklistCoordinator @Inject constructor(
    private val enforcer: ShizukuBlacklistEnforcer,
    private val vpnStateMonitor: VpnStateMonitor,
    private val effectiveSettings: EffectiveCurrentUserSettings,
    private val blacklistStore: BlacklistStore,
    private val dispatcherProvider: VpnDispatcherProvider,
    scope: CoroutineScope,
) {
    init {
        // Clear any stale firewall blocks from previous run or crash
        scope.launch(dispatcherProvider.infiniteIo) {
            if (blacklistStore.appliedPackages.value.isNotEmpty()) {
                ProtonLogger.logCustom(LogCategory.CONN, "BlacklistCoordinator init: cleaning up leftover blocks")
                enforcer.clear()
            }
        }

        combine(
            vpnStateMonitor.status,
            effectiveSettings.plusFeatures
        ) { status, plusFeatures ->
            val isConnected = status.state == VpnState.Connected
            val blacklistSetting = plusFeatures.internetBlacklist
            if (isConnected && blacklistSetting.isEnabled) {
                blacklistSetting.packages.toSet()
            } else {
                emptySet()
            }
        }.distinctUntilChanged()
        .onEach { desiredBlockedPackages ->
            if (desiredBlockedPackages.isNotEmpty()) {
                enforcer.apply(desiredBlockedPackages)
            } else {
                enforcer.clear()
            }
        }.launchIn(scope)
    }

    suspend fun releaseAllBlocks() {
        ProtonLogger.logCustom(LogCategory.CONN, "Explicit release of all blacklist blocks requested")
        enforcer.clear()
    }
}
