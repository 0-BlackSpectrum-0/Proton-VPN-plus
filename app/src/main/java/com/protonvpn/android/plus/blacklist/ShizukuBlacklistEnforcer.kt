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
import android.content.pm.PackageManager
import android.os.Build
import com.protonvpn.android.R
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
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

enum class EnforcerMode {
    SHIZUKU_CONNECTIVITY, // Android 11+ via Shizuku shell
    SHIZUKU_ROOT,         // Shizuku running with Root UID 0 (iptables)
    DIRECT_ROOT,          // su binary available (iptables)
    UNSUPPORTED
}

@Singleton
class ShizukuBlacklistEnforcer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blacklistStore: BlacklistStore,
    private val dispatcherProvider: VpnDispatcherProvider,
    private val scope: CoroutineScope,
) : BlacklistEnforcer {

    private val _isSupported = MutableStateFlow(false)
    override val isSupported: StateFlow<Boolean> = _isSupported.asStateFlow()

    private val _statusText = MutableStateFlow(context.getString(R.string.plus_shizuku_needed))
    override val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { checkCapabilities() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        ProtonLogger.logCustom(LogCategory.CONN, "Shizuku binder died")
        scope.launch(dispatcherProvider.infiniteIo) {
            clear()
        }
        checkCapabilities()
    }
    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        checkCapabilities()
    }

    init {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        } catch (e: Exception) {
            ProtonLogger.logCustom(LogCategory.CONN, "Failed to register Shizuku listeners: ${e.message}")
        }
        checkCapabilities()
    }

    fun checkCapabilities() {
        scope.launch(dispatcherProvider.infiniteIo) {
            val mode = detectMode()
            val supported = mode != EnforcerMode.UNSUPPORTED
            _isSupported.value = supported

            _statusText.value = when (mode) {
                EnforcerMode.SHIZUKU_ROOT, EnforcerMode.DIRECT_ROOT ->
                    context.getString(R.string.plus_shizuku_root_ready)
                EnforcerMode.SHIZUKU_CONNECTIVITY ->
                    context.getString(R.string.plus_shizuku_ready)
                EnforcerMode.UNSUPPORTED -> {
                    if (isShizukuAlive() && !hasShizukuPermission()) {
                        context.getString(R.string.plus_shizuku_permission_needed)
                    } else {
                        context.getString(R.string.plus_shizuku_needed)
                    }
                }
            }
        }
    }

    private fun isShizukuAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    private fun hasShizukuPermission(): Boolean = try {
        if (!isShizukuAlive()) false
        else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    private fun detectMode(): EnforcerMode {
        if (isShizukuAlive() && hasShizukuPermission()) {
            return try {
                if (Shizuku.getUid() == 0) {
                    EnforcerMode.SHIZUKU_ROOT
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    EnforcerMode.SHIZUKU_CONNECTIVITY
                } else {
                    if (isDirectRootAvailable()) EnforcerMode.DIRECT_ROOT else EnforcerMode.UNSUPPORTED
                }
            } catch (_: Throwable) {
                EnforcerMode.UNSUPPORTED
            }
        }

        if (isDirectRootAvailable()) {
            return EnforcerMode.DIRECT_ROOT
        }

        return EnforcerMode.UNSUPPORTED
    }

    private fun isDirectRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (_: Throwable) {
            false
        }
    }

    override suspend fun apply(blockedPackages: Set<String>) = withContext(dispatcherProvider.infiniteIo) {
        val mode = detectMode()
        if (mode == EnforcerMode.UNSUPPORTED) {
            ProtonLogger.logCustom(LogCategory.CONN, "Cannot apply blacklist: no supported firewall backend")
            return@withContext
        }

        ProtonLogger.logCustom(LogCategory.CONN, "Applying blacklist (${blockedPackages.size} packages) via mode $mode")
        val previousPackages = blacklistStore.appliedPackages.value
        val toUnblock = previousPackages - blockedPackages

        when (mode) {
            EnforcerMode.SHIZUKU_CONNECTIVITY -> {
                runShizukuCommand("cmd connectivity set-chain3-enabled true")
                for (pkg in toUnblock) {
                    runShizukuCommand("cmd connectivity set-package-networking-enabled true $pkg")
                }
                for (pkg in blockedPackages) {
                    runShizukuCommand("cmd connectivity set-package-networking-enabled false $pkg")
                }
            }
            EnforcerMode.SHIZUKU_ROOT, EnforcerMode.DIRECT_ROOT -> {
                applyIptablesRules(blockedPackages, useShizuku = (mode == EnforcerMode.SHIZUKU_ROOT))
            }
            EnforcerMode.UNSUPPORTED -> { /* No-op */ }
        }

        blacklistStore.setAppliedPackages(blockedPackages)
    }

    override suspend fun clear() = withContext(dispatcherProvider.infiniteIo) {
        val applied = blacklistStore.appliedPackages.value
        if (applied.isEmpty()) return@withContext

        ProtonLogger.logCustom(LogCategory.CONN, "Clearing blacklist for ${applied.size} packages")
        val mode = detectMode()
        when (mode) {
            EnforcerMode.SHIZUKU_CONNECTIVITY -> {
                for (pkg in applied) {
                    runShizukuCommand("cmd connectivity set-package-networking-enabled true $pkg")
                }
                runShizukuCommand("cmd connectivity set-chain3-enabled false")
            }
            EnforcerMode.SHIZUKU_ROOT, EnforcerMode.DIRECT_ROOT -> {
                clearIptablesRules(useShizuku = (mode == EnforcerMode.SHIZUKU_ROOT))
            }
            EnforcerMode.UNSUPPORTED -> {
                // Best effort cleanup if Shizuku is alive
                if (isShizukuAlive() && hasShizukuPermission()) {
                    for (pkg in applied) {
                        runShizukuCommand("cmd connectivity set-package-networking-enabled true $pkg")
                    }
                    runShizukuCommand("cmd connectivity set-chain3-enabled false")
                }
            }
        }

        blacklistStore.clear()
    }

    private fun applyIptablesRules(packages: Set<String>, useShizuku: Boolean) {
        val pm = context.packageManager
        val uids = packages.mapNotNull { pkg ->
            try {
                pm.getPackageUid(pkg, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }.toSet()

        val commands = mutableListOf(
            "iptables -N PROTON_BL 2>/dev/null || true",
            "iptables -C OUTPUT -j PROTON_BL 2>/dev/null || iptables -I OUTPUT -j PROTON_BL",
            "iptables -F PROTON_BL",
            "ip6tables -N PROTON_BL 2>/dev/null || true",
            "ip6tables -C OUTPUT -j PROTON_BL 2>/dev/null || ip6tables -I OUTPUT -j PROTON_BL",
            "ip6tables -F PROTON_BL"
        )

        for (uid in uids) {
            commands.add("iptables -A PROTON_BL -m owner --uid-owner $uid -j DROP")
            commands.add("ip6tables -A PROTON_BL -m owner --uid-owner $uid -j DROP")
        }

        val fullCommand = commands.joinToString(" && ")
        if (useShizuku) {
            runShizukuCommand(fullCommand)
        } else {
            runDirectRootCommand(fullCommand)
        }
    }

    private fun clearIptablesRules(useShizuku: Boolean) {
        val command = "iptables -F PROTON_BL 2>/dev/null || true; ip6tables -F PROTON_BL 2>/dev/null || true"
        if (useShizuku) {
            runShizukuCommand(command)
        } else {
            runDirectRootCommand(command)
        }
    }

    private fun runShizukuCommand(command: String): Boolean {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as java.lang.Process
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val err = process.errorStream.bufferedReader().readText()
                ProtonLogger.logCustom(LogCategory.CONN, "Shizuku command failed ($exitCode): $command. Error: $err")
            }
            exitCode == 0
        } catch (e: Exception) {
            ProtonLogger.logCustom(LogCategory.CONN, "Exception executing Shizuku command: ${e.message}")
            false
        }
    }

    private fun runDirectRootCommand(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val err = process.errorStream.bufferedReader().readText()
                ProtonLogger.logCustom(LogCategory.CONN, "Root command failed ($exitCode): $command. Error: $err")
            }
            exitCode == 0
        } catch (e: Exception) {
            ProtonLogger.logCustom(LogCategory.CONN, "Exception executing root command: ${e.message}")
            false
        }
    }
}
