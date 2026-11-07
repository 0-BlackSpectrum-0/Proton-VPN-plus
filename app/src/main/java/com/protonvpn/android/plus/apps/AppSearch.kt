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

package com.protonvpn.android.plus.apps

object AppSearch {
    fun filter(
        apps: List<AppEntry>,
        query: String,
        showSystemApps: Boolean,
        selectedPackages: Set<String>
    ): Pair<List<AppEntry>, List<AppEntry>> {
        val trimmed = query.trim().lowercase()

        val matching = apps.filter { app ->
            val matchesQuery = trimmed.isEmpty() || app.searchKey.contains(trimmed)
            val isSelected = app.packageName in selectedPackages
            // Always show selected apps regardless of system app toggle;
            // for unselected apps, honor showSystemApps
            matchesQuery && (isSelected || showSystemApps || !app.isSystem)
        }

        val selected = matching.filter { it.packageName in selectedPackages }
        val unselected = matching.filter { it.packageName !in selectedPackages }

        return selected to unselected
    }
}
