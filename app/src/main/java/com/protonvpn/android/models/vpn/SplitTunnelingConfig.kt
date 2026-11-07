/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.android.models.vpn

import androidx.annotation.VisibleForTesting
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.settings.data.PlusFeaturesSettings
import com.protonvpn.android.settings.data.SplitTunnelingSettings

@VisibleForTesting
val DIRECT_CONNECTIONS_EXCLUDED_APPS = listOf(
    "com.google.android.projection.gearhead", // Android Auto
)

interface SplitTunnelAppsConfigurator {
    fun includeApplications(packageNames: List<String>)
    fun excludeApplications(packageNames: List<String>)
}

fun applyAppsSplitTunneling(
    configurator: SplitTunnelAppsConfigurator,
    connectIntent: AnyConnectIntent,
    myPackageName: String,
    splitTunneling: SplitTunnelingSettings,
    allowDirectLanConnections: Boolean,
    plusFeatures: PlusFeaturesSettings = PlusFeaturesSettings(),
    isPaused: Boolean = false,
    blacklistNeedsTunnel: Boolean = false,
) {
    val plan = computeAppRoutingPlan(
        connectIntent = connectIntent,
        myPackageName = myPackageName,
        legacy = splitTunneling,
        plus = plusFeatures,
        allowDirectLanConnections = allowDirectLanConnections,
        isPaused = isPaused,
        blacklistNeedsTunnel = blacklistNeedsTunnel,
    )
    when (val tunnel = plan.tunnel) {
        is TunnelApps.Include -> configurator.includeApplications(tunnel.packages.toList())
        is TunnelApps.Exclude -> configurator.excludeApplications(tunnel.packages.toList())
        is TunnelApps.All -> {
            // When all apps are tunneled, no specific include or exclude list is applied
        }
    }
}
