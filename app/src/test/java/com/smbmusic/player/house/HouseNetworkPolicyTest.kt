package com.smbmusic.player.house

import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class HouseNetworkPolicyTest {
    private fun address(value: String) = InetAddress.getByName(value)
    private fun route(value: String = "192.0.2.0", bits: Int = 24,
                      gateway: Boolean = false, unicast: Boolean = true) =
        HouseLanRoute(address(value), bits, gateway, unicast)
    private fun qualifies(routes: List<HouseLanRoute>, target: String = "192.0.2.12",
                          physical: Boolean = true, vpn: Boolean = false, notVpn: Boolean = true) =
        HouseNetworkPolicy.qualifies(physical, vpn, notVpn, address(target), routes)

    @Test fun physicalRouteQualifiesIndependentlyOfTheDefaultVpnRoute() {
        assertTrue(qualifies(listOf(route(), route("0.0.0.0", 0, true))))
        // The same route advertised by a VPN never proves home presence.
        assertFalse(qualifies(listOf(route()), vpn = true))
        assertFalse(qualifies(listOf(route()), notVpn = false))
        assertFalse(qualifies(listOf(route()), physical = false))
    }

    @Test fun anotherWifiAndGatewayReachabilityDoNotCountAsHome() {
        assertFalse(qualifies(listOf(route("198.51.100.0"), route("0.0.0.0", 0, true))))
        assertFalse(qualifies(listOf(route(gateway = true))))
        assertFalse(qualifies(listOf(route("0.0.0.0", 0))))
        assertFalse(qualifies(listOf(route(unicast = false))))
    }

    @Test fun physicalRouteRemovalRevokesQualificationEvenWhenVpnCouldStillReachThePi() {
        assertTrue(qualifies(listOf(route())))
        assertFalse(qualifies(emptyList()))
        assertFalse(qualifies(listOf(route("0.0.0.0", 0, true))))
        assertTrue(qualifies(listOf(route())))
    }

    @Test fun nonByteAlignedAndHostRoutesMatchOnlyTheirOwnSubnet() {
        assertTrue(qualifies(listOf(route("192.0.2.8", 29)), target = "192.0.2.15"))
        assertFalse(qualifies(listOf(route("192.0.2.8", 29)), target = "192.0.2.16"))
        assertTrue(qualifies(listOf(route("192.0.2.12", 32))))
        assertFalse(qualifies(listOf(route("192.0.2.13", 32))))
    }

    @Test fun ipv6UsesItsOwnPrefixAndRejectsInvalidOrLocalOnlyTargets() {
        assertTrue(qualifies(listOf(route("2001:db8:1::", 64)), target = "2001:db8:1::12"))
        assertFalse(qualifies(listOf(route("2001:db8:2::", 64)), target = "2001:db8:1::12"))
        assertFalse(qualifies(listOf(route()), target = "2001:db8:1::12"))
        assertFalse(qualifies(listOf(route(bits = 33))))
        assertFalse(qualifies(listOf(route("127.0.0.0", 8)), target = "127.0.0.1"))
        assertFalse(qualifies(listOf(route("224.0.0.0", 4)), target = "224.0.0.1"))
    }
}
