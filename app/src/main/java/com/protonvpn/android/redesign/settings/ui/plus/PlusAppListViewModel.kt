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

package com.protonvpn.android.redesign.settings.ui.plus

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.plus.apps.AppEntry
import com.protonvpn.android.plus.apps.AppSearch
import com.protonvpn.android.plus.apps.InstalledAppsCache
import com.protonvpn.android.plus.blacklist.BlacklistCoordinator
import com.protonvpn.android.plus.blacklist.ShizukuBlacklistEnforcer
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PlusAppListType {
    Blacklist,
    Whitelist
}

@HiltViewModel
class PlusAppListViewModel @Inject constructor(
    private val userSettingsManager: CurrentUserLocalSettingsManager,
    private val installedAppsCache: InstalledAppsCache,
    private val blacklistEnforcer: ShizukuBlacklistEnforcer,
    private val blacklistCoordinator: BlacklistCoordinator,
) : ViewModel() {

    private var listType: PlusAppListType = PlusAppListType.Blacklist
    private var initialEnabled: Boolean = false
    private var initialPackages: Set<String> = emptySet()

    val isLoading = installedAppsCache.isLoading
    val allApps = installedAppsCache.apps

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _showSystemApps = MutableStateFlow(false)
    val showSystemApps: StateFlow<Boolean> = _showSystemApps.asStateFlow()

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
    val selectedPackages: StateFlow<Set<String>> = _selectedPackages.asStateFlow()

    private val _otherListPackages = MutableStateFlow<Set<String>>(emptySet())
    val otherListPackages: StateFlow<Set<String>> = _otherListPackages.asStateFlow()

    val enforcerStatusText = blacklistEnforcer.statusText
    val isEnforcerSupported = blacklistEnforcer.isSupported

    val filteredApps: StateFlow<Pair<List<AppEntry>, List<AppEntry>>> = combine(
        allApps,
        query,
        showSystemApps,
        selectedPackages
    ) { apps, q, showSys, selected ->
        AppSearch.filter(apps, q, showSys, selected)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList<AppEntry>() to emptyList())

    val hasChanges: StateFlow<Boolean> = combine(
        isEnabled,
        selectedPackages
    ) { enabled, selected ->
        enabled != initialEnabled || selected != initialPackages
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun initType(type: PlusAppListType) {
        listType = type
        viewModelScope.launch {
            val settings = userSettingsManager.rawCurrentUserSettingsFlow.first().plusFeatures
            when (type) {
                PlusAppListType.Blacklist -> {
                    initialEnabled = settings.internetBlacklist.isEnabled
                    initialPackages = settings.internetBlacklist.packages.toSet()
                    _otherListPackages.value = settings.vpnWhitelist.packages.toSet()
                }
                PlusAppListType.Whitelist -> {
                    initialEnabled = settings.vpnWhitelist.isEnabled
                    initialPackages = settings.vpnWhitelist.packages.toSet()
                    _otherListPackages.value = settings.internetBlacklist.packages.toSet()
                }
            }
            _isEnabled.value = initialEnabled
            _selectedPackages.value = initialPackages
        }
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    fun toggleShowSystemApps() {
        _showSystemApps.value = !_showSystemApps.value
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
    }

    fun toggleApp(packageName: String) {
        val current = _selectedPackages.value.toMutableSet()
        if (packageName in current) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        _selectedPackages.value = current
    }

    suspend fun save(): Boolean {
        val packagesList = _selectedPackages.value.toList()
        val enabled = _isEnabled.value

        when (listType) {
            PlusAppListType.Blacklist -> {
                userSettingsManager.setPlusBlacklistEnabled(enabled)
                userSettingsManager.updatePlusBlacklistApps(packagesList)
            }
            PlusAppListType.Whitelist -> {
                userSettingsManager.setPlusWhitelistEnabled(enabled)
                userSettingsManager.updatePlusWhitelistApps(packagesList)
            }
        }
        initialEnabled = enabled
        initialPackages = _selectedPackages.value
        return true
    }

    fun discard() {
        _isEnabled.value = initialEnabled
        _selectedPackages.value = initialPackages
    }

    fun releaseAllBlocks() {
        viewModelScope.launch {
            blacklistCoordinator.releaseAllBlocks()
        }
    }

    suspend fun getIcon(packageName: String): Drawable? {
        return installedAppsCache.getAppIcon(packageName)
    }
}
