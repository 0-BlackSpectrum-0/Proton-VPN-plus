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

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AppEntry(
    val packageName: String,
    val label: String,
    val searchKey: String,
    val isSystem: Boolean,
    val lastUpdateTime: Long,
)

@Serializable
private data class CacheWrapper(
    val version: Int,
    val locale: String,
    val apps: List<AppEntry>,
)

@Singleton
class InstalledAppsCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: VpnDispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val iconCache = LruCache<String, Drawable>(150)
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile = File(context.cacheDir, "plus_apps_cache.json")

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            scope.launch { reload() }
        }
    }

    init {
        // Load cached snapshot immediately
        loadFromDisk()

        // Register package updates
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(packageReceiver, filter)

        // Asynchronously warm/refresh
        scope.launch { reload() }
    }

    private fun loadFromDisk() {
        try {
            if (cacheFile.exists()) {
                val content = cacheFile.readText()
                val wrapper = json.decodeFromString<CacheWrapper>(content)
                val currentLocale = Locale.getDefault().toLanguageTag()
                if (wrapper.version == CACHE_VERSION && wrapper.locale == currentLocale) {
                    _apps.value = wrapper.apps
                }
            }
        } catch (e: Exception) {
            ProtonLogger.logCustom(LogCategory.APP, "Failed to load cached apps: ${e.message}")
        }
    }

    suspend fun reload() = withContext(dispatcherProvider.infiniteIo) {
        if (_isLoading.value) return@withContext
        _isLoading.value = true
        try {
            val pm = context.packageManager
            val myPackage = context.packageName
            val installedPackages = pm.getInstalledPackages(0)

            val list = mutableListOf<AppEntry>()
            for (pkgInfo in installedPackages) {
                val pkgName = pkgInfo.packageName
                if (pkgName == myPackage) continue

                val hasInternet = pm.checkPermission(Manifest.permission.INTERNET, pkgName) == PackageManager.PERMISSION_GRANTED
                if (!hasInternet) continue

                val appInfo = pkgInfo.applicationInfo ?: continue
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val label = try {
                    appInfo.loadLabel(pm).toString()
                } catch (_: Exception) {
                    pkgName
                }
                val searchKey = (label + " " + pkgName).lowercase()
                val lastUpdateTime = pkgInfo.lastUpdateTime

                list.add(
                    AppEntry(
                        packageName = pkgName,
                        label = label,
                        searchKey = searchKey,
                        isSystem = isSystem,
                        lastUpdateTime = lastUpdateTime
                    )
                )
            }

            list.sortBy { it.label.lowercase() }
            _apps.value = list

            // Persist to disk
            val currentLocale = Locale.getDefault().toLanguageTag()
            val wrapper = CacheWrapper(CACHE_VERSION, currentLocale, list)
            cacheFile.writeText(json.encodeToString(wrapper))
        } catch (e: Exception) {
            ProtonLogger.logCustom(LogCategory.APP, "Failed to refresh installed apps: ${e.message}")
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun getAppIcon(packageName: String): Drawable? = withContext(dispatcherProvider.infiniteIo) {
        iconCache.get(packageName)?.let { return@withContext it }
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            iconCache.put(packageName, drawable)
            drawable
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val CACHE_VERSION = 1
    }
}
