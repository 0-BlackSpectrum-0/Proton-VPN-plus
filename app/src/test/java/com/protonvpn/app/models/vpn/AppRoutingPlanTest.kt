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

package com.protonvpn.app.models.vpn

import com.protonvpn.android.models.vpn.AppRoutingPlan
import com.protonvpn.android.models.vpn.DIRECT_CONNECTIONS_EXCLUDED_APPS
import com.protonvpn.android.models.vpn.TunnelApps
import com.protonvpn.android.models.vpn.computeAppRoutingPlan
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.settings.data.PlusAppListSetting
import com.protonvpn.android.settings.data.PlusFeaturesSettings
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.settings.data.SplitTunnelingSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRoutingPlanTest {

    private val myPackage = "ch.protonvpn.android.plus"
    private val normalIntent: AnyConnectIntent = ConnectIntent.Fastest
    private val guestHoleIntent: AnyConnectIntent = AnyConnectIntent.GuestHole("test_server")

    @Test
    fun `guest hole connects only own package`() {
        val plan = computeAppRoutingPlan(
            connectIntent = guestHoleIntent,
            myPackageName = myPackage,
            legacy = SplitTunnelingSettings(),
            plus = PlusFeaturesSettings(),
            allowDirectLanConnections = false,
            isPaused = false
        )
        assertEquals(AppRoutingPlan(TunnelApps.Include(setOf(myPackage))), plan)
    }

    @Test
    fun `default settings routes all traffic to tunnel`() {
        val plan = computeAppRoutingPlan(
            connectIntent = normalIntent,
            myPackageName = myPackage,
            legacy = SplitTunnelingSettings(),
            plus = PlusFeaturesSettings(),
            allowDirectLanConnections = false,
            isPaused = false
        )
        assertEquals(AppRoutingPlan(TunnelApps.All), plan)
    }

    @Test
    fun `golden A to H scenario - whitelist active`() {
        // Whitelist: B, C. Blacklist: A, E, H.
        val plus = PlusFeaturesSettings(
            internetBlacklist = PlusAppListSetting(isEnabled = true, packages = listOf("app.A", "app.E", "app.H")),
            vpnWhitelist = PlusAppListSetting(isEnabled = true, packages = listOf("app.B", "app.C"))
        )
        val plan = computeAppRoutingPlan(
            connectIntent = normalIntent,
            myPackageName = myPackage,
            legacy = SplitTunnelingSettings(),
            plus = plus,
            allowDirectLanConnections = false,
            isPaused = false
        )
        // With Whitelist B, C, tunnel includes myPackage + B + C.
        // Apps outside whitelist (D, F, G, and Blacklist A, E, H) are not in tunnel.
        assertEquals(
            AppRoutingPlan(TunnelApps.Include(setOf(myPackage, "app.B", "app.C"))),
            plan
        )
    }

    @Test
    fun `golden A to H scenario - paused`() {
        // When paused, only myPackage is in tunnel (whitelist is dropped).
        val plus = PlusFeaturesSettings(
            internetBlacklist = PlusAppListSetting(isEnabled = true, packages = listOf("app.A", "app.E", "app.H")),
            vpnWhitelist = PlusAppListSetting(isEnabled = true, packages = listOf("app.B", "app.C"))
        )
        val plan = computeAppRoutingPlan(
            connectIntent = normalIntent,
            myPackageName = myPackage,
            legacy = SplitTunnelingSettings(),
            plus = plus,
            allowDirectLanConnections = false,
            isPaused = true
        )
        assertEquals(
            AppRoutingPlan(TunnelApps.Include(setOf(myPackage))),
            plan
        )
    }

    @Test
    fun `golden scenario under backend G - blocked apps enter tunnel`() {
        val plus = PlusFeaturesSettings(
            internetBlacklist = PlusAppListSetting(isEnabled = true, packages = listOf("app.A", "app.E", "app.H")),
            vpnWhitelist = PlusAppListSetting(isEnabled = true, packages = listOf("app.B", "app.C"))
        )
        val plan = computeAppRoutingPlan(
            connectIntent = normalIntent,
            myPackageName = myPackage,
            legacy = SplitTunnelingSettings(),
            plus = plus,
            allowDirectLanConnections = false,
            isPaused = false,
            blacklistNeedsTunnel = true
        )
        assertEquals(
            AppRoutingPlan(
                tunnel = TunnelApps.Include(setOf(myPackage, "app.B", "app.C", "app.A", "app.E", "app.H")),
                blockedInTunnel = setOf("app.A", "app.E", "app.H")
            ),
            plan
        )
    }

    @Test
    fun `golden scenario under backend G - paused keeps blocked apps in tunnel`() {
        val plus = PlusFeaturesSettings(
            internetBlacklist = PlusAppListSetting(isEnabled = true, packages = listOf("app.A", "app.E", "app.H")),
            vpnWhitelist = PlusAppListSetting(isEnabled = true, packages = listOf("app.B", "app.C"))
        )
        val plan = computeAppRoutingPlan(
            connectIntent = normalIntent,
            myPackageName = myPackage,
            legacy = SplitTunnelingSettings(),
            plus = plus,
            allowDirectLanConnections = false,
            isPaused = true,
            blacklistNeedsTunnel = true
        )
        assertEquals(
            AppRoutingPlan(
                tunnel = TunnelApps.Include(setOf(myPackage, "app.A", "app.E", "app.H")),
                blockedInTunnel = setOf("app.A", "app.E", "app.H")
            ),
            plan
        )
    }

    @Test
    fun `blacklist wins over whitelist if an app is in both`() {
        val plus = PlusFeaturesSettings(
            internetBlacklist = PlusAppListSetting(isEnabled = true, packages = listOf("app.X", "app.Y")),
            vpnWhitelist = PlusAppListSetting(isEnabled = true, packages = listOf("app.X", "app.Z"))
        )
        val plan = computeAppRoutingPlan(
            connectIntent = normalIntent,
            myPackageName = myPackage,
            legacy = SplitTunnelingSettings(),
            plus = plus,
            allowDirectLanConnections = false,
            isPaused = false
        )
        // app.X is removed from whitelist because it is in blacklist!
        assertEquals(
            AppRoutingPlan(TunnelApps.Include(setOf(myPackage, "app.Z"))),
            plan
        )
    }

    @Test
    fun `empty whitelist behaves as off and does not override legacy split tunneling`() {
        val plus = PlusFeaturesSettings(
            vpnWhitelist = PlusAppListSetting(isEnabled = true, packages = emptyList())
        )
        val legacy = SplitTunnelingSettings(
            isEnabled = true,
            mode = SplitTunnelingMode.INCLUDE_ONLY,
            includedApps = listOf("app.legacy1")
        )
        val plan = computeAppRoutingPlan(
            connectIntent = normalIntent,
            myPackageName = myPackage,
            legacy = legacy,
            plus = plus,
            allowDirectLanConnections = false,
            isPaused = false
        )
        assertEquals(
            AppRoutingPlan(TunnelApps.Include(setOf(myPackage, "app.legacy1"))),
            plan
        )
    }

    @Test
    fun `non-empty whitelist overrides legacy split tunneling`() {
        val plus = PlusFeaturesSettings(
            vpnWhitelist = PlusAppListSetting(isEnabled = true, packages = listOf("app.white1"))
        )
        val legacy = SplitTunnelingSettings(
            isEnabled = true,
            mode = SplitTunnelingMode.INCLUDE_ONLY,
            includedApps = listOf("app.legacy1")
        )
        val plan = computeAppRoutingPlan(
            connectIntent = normalIntent,
            myPackageName = myPackage,
            legacy = legacy,
            plus = plus,
            allowDirectLanConnections = false,
            isPaused = false
        )
        assertEquals(
            AppRoutingPlan(TunnelApps.Include(setOf(myPackage, "app.white1"))),
            plan
        )
    }

    @Test
    fun `own package is never in blacklist`() {
        val plus = PlusFeaturesSettings(
            internetBlacklist = PlusAppListSetting(isEnabled = true, packages = listOf(myPackage, "app.other")),
            vpnWhitelist = PlusAppListSetting(isEnabled = true, packages = listOf("app.other"))
        )
        val plan = computeAppRoutingPlan(
            connectIntent = normalIntent,
            myPackageName = myPackage,
            legacy = SplitTunnelingSettings(),
            plus = plus,
            allowDirectLanConnections = false,
            isPaused = false
        )
        // myPackage is always preserved in Include
        assertEquals(
            AppRoutingPlan(TunnelApps.Include(setOf(myPackage))),
            plan
        )
    }

    @Test
    fun `direct LAN connections excluded apps applied when enabled`() {
        val plan = computeAppRoutingPlan(
            connectIntent = normalIntent,
            myPackageName = myPackage,
            legacy = SplitTunnelingSettings(),
            plus = PlusFeaturesSettings(),
            allowDirectLanConnections = true,
            isPaused = false
        )
        assertEquals(
            AppRoutingPlan(TunnelApps.Exclude(DIRECT_CONNECTIONS_EXCLUDED_APPS.toSet())),
            plan
        )
    }

    @Test
    fun `plus features settings isEffectivelySameAs test`() {
        val a = PlusFeaturesSettings(
            internetBlacklist = PlusAppListSetting(isEnabled = true, packages = listOf("p1", "p2")),
            vpnWhitelist = PlusAppListSetting(isEnabled = false, packages = listOf("p3"))
        )
        val b = PlusFeaturesSettings(
            internetBlacklist = PlusAppListSetting(isEnabled = true, packages = listOf("p2", "p1")),
            vpnWhitelist = PlusAppListSetting(isEnabled = false, packages = emptyList())
        )
        val c = PlusFeaturesSettings(
            internetBlacklist = PlusAppListSetting(isEnabled = true, packages = listOf("p1")),
            vpnWhitelist = PlusAppListSetting(isEnabled = false, packages = emptyList())
        )

        assertTrue(a.isEffectivelySameAs(b))
        assertFalse(a.isEffectivelySameAs(c))
    }
}
