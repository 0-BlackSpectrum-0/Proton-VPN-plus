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

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlacklistStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _appliedPackages = MutableStateFlow(getSavedAppliedPackages())
    val appliedPackages: StateFlow<Set<String>> = _appliedPackages.asStateFlow()

    private fun getSavedAppliedPackages(): Set<String> {
        return prefs.getStringSet(KEY_APPLIED_PACKAGES, emptySet()) ?: emptySet()
    }

    fun setAppliedPackages(packages: Set<String>) {
        prefs.edit { putStringSet(KEY_APPLIED_PACKAGES, packages) }
        _appliedPackages.value = packages
    }

    fun clear() {
        prefs.edit { remove(KEY_APPLIED_PACKAGES) }
        _appliedPackages.value = emptySet()
    }

    companion object {
        private const val PREFS_NAME = "plus_blacklist_state"
        private const val KEY_APPLIED_PACKAGES = "applied_packages"
    }
}
