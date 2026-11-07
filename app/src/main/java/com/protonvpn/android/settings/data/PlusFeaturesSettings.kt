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

package com.protonvpn.android.settings.data

import kotlinx.serialization.Serializable

@Serializable
data class PlusAppListSetting(
    val isEnabled: Boolean = false,
    val packages: List<String> = emptyList()
)

@Serializable
data class PlusFeaturesSettings(
    val internetBlacklist: PlusAppListSetting = PlusAppListSetting(),
    val vpnWhitelist: PlusAppListSetting = PlusAppListSetting(),
) {
    fun isEffectivelySameAs(other: PlusFeaturesSettings): Boolean {
        val sameBlacklist = (!internetBlacklist.isEnabled && !other.internetBlacklist.isEnabled) ||
            (internetBlacklist.isEnabled == other.internetBlacklist.isEnabled &&
                internetBlacklist.packages.toSet() == other.internetBlacklist.packages.toSet())
        val sameWhitelist = (!vpnWhitelist.isEnabled && !other.vpnWhitelist.isEnabled) ||
            (vpnWhitelist.isEnabled == other.vpnWhitelist.isEnabled &&
                vpnWhitelist.packages.toSet() == other.vpnWhitelist.packages.toSet())
        return sameBlacklist && sameWhitelist
    }
}
