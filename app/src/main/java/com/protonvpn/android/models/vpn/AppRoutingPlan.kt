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

package com.protonvpn.android.models.vpn

import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.settings.data.PlusFeaturesSettings
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.settings.data.SplitTunnelingSettings

sealed interface TunnelApps {
    data object All : TunnelApps
    data class Include(val packages: Set<String>) : TunnelApps
    data class Exclude(val packages: Set<String>) : TunnelApps
}

data class AppRoutingPlan(
    val tunnel: TunnelApps,
    val blockedInTunnel: Set<String> = emptySet(),
)

fun computeAppRoutingPlan(
    connectIntent: AnyConnectIntent,
    myPackageName: String,
    legacy: SplitTunnelingSettings,
    plus: PlusFeaturesSettings,
    allowDirectLanConnections: Boolean,
    isPaused: Boolean,
    blacklistNeedsTunnel: Boolean = false,
): AppRoutingPlan {
    // 1. Guest hole: unchanged: Include({me})
    if (connectIntent is AnyConnectIntent.GuestHole) {
        return AppRoutingPlan(TunnelApps.Include(setOf(myPackageName)))
    }

    val directConnectionsExcludedApps =
        if (allowDirectLanConnections) DIRECT_CONNECTIONS_EXCLUDED_APPS.toSet()
        else emptySet()

    // 2. B = blacklist packages if the Blacklist is enabled, minus me
    val B = if (plus.internetBlacklist.isEnabled) {
        plus.internetBlacklist.packages.toSet() - myPackageName
    } else {
        emptySet()
    }
    val bIfG = if (blacklistNeedsTunnel) B else emptySet()

    // 3. Paused: Include({me} U B_if_G). Nothing else is tunneled.
    if (isPaused) {
        return AppRoutingPlan(
            tunnel = TunnelApps.Include(setOf(myPackageName) + bIfG),
            blockedInTunnel = bIfG,
        )
    }

    // 4. W = whitelist packages if enabled and non-empty, minus B, minus me.
    // Blacklist wins if a package is in both. If W exists: Include({me} U W U B_if_G).
    // Plus overrides legacy split-tunneling app lists.
    val isWhitelistActive = plus.vpnWhitelist.isEnabled && plus.vpnWhitelist.packages.isNotEmpty()
    if (isWhitelistActive) {
        val W = plus.vpnWhitelist.packages.toSet() - B - myPackageName
        return AppRoutingPlan(
            tunnel = TunnelApps.Include(setOf(myPackageName) + W + bIfG),
            blockedInTunnel = bIfG,
        )
    }

    // 5. Else legacy INCLUDE_ONLY with apps: Include({me} U legacy.included U B_if_G)
    if (legacy.isEnabled && legacy.mode == SplitTunnelingMode.INCLUDE_ONLY && legacy.includedApps.isNotEmpty()) {
        return AppRoutingPlan(
            tunnel = TunnelApps.Include(setOf(myPackageName) + legacy.includedApps.toSet() + bIfG),
            blockedInTunnel = bIfG,
        )
    }

    // 6. Else legacy EXCLUDE_ONLY with apps: Exclude((legacy.excluded + LAN-direct apps) - me - B_if_G)
    if (legacy.isEnabled && legacy.mode == SplitTunnelingMode.EXCLUDE_ONLY && legacy.excludedApps.isNotEmpty()) {
        val excluded = (legacy.excludedApps.toSet() + directConnectionsExcludedApps) - myPackageName - bIfG
        return AppRoutingPlan(
            tunnel = TunnelApps.Exclude(excluded),
            blockedInTunnel = bIfG,
        )
    }

    // 7. Else: All, or Exclude(LAN-direct apps - B_if_G) when direct LAN is allowed
    val effectiveLanExcluded = directConnectionsExcludedApps - bIfG
    if (effectiveLanExcluded.isNotEmpty()) {
        return AppRoutingPlan(
            tunnel = TunnelApps.Exclude(effectiveLanExcluded),
            blockedInTunnel = bIfG,
        )
    }

    return AppRoutingPlan(
        tunnel = TunnelApps.All,
        blockedInTunnel = bIfG,
    )
}
