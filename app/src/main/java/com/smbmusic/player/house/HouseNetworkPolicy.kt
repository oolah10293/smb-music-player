package com.smbmusic.player.house

import java.net.InetAddress

/** A route on a physical interface, never a route supplied by the VPN. */
data class HouseLanRoute(val address: InetAddress, val prefixLength: Int,
                         val hasGateway: Boolean, val unicast: Boolean)

object HouseNetworkPolicy {
    /** A directly connected Android route has no gateway, or the all-zero address. */
    fun hasGateway(gateway: InetAddress?): Boolean = gateway != null && !gateway.isAnyLocalAddress

    /** Older Android cannot expose route type; require the interface's own local prefix. */
    fun hasMatchingLinkPrefix(routeAddress: InetAddress, routePrefixLength: Int,
                              linkAddress: InetAddress, linkPrefixLength: Int): Boolean =
        routePrefixLength == linkPrefixLength &&
            prefixContains(routeAddress, routePrefixLength, linkAddress)

    fun qualifies(wifiOrEthernet: Boolean, vpn: Boolean, notVpn: Boolean,
                  destination: InetAddress, routes: List<HouseLanRoute>): Boolean {
        if (!wifiOrEthernet || vpn || !notVpn || destination.isAnyLocalAddress ||
            destination.isLoopbackAddress || destination.isMulticastAddress) return false
        return routes.any { route ->
            !route.hasGateway && route.unicast &&
                prefixContains(route.address, route.prefixLength, destination)
        }
    }

    private fun prefixContains(address: InetAddress, bits: Int, destination: InetAddress): Boolean {
        val prefix = address.address
        val target = destination.address
        if (bits <= 0 || prefix.size != target.size || bits > prefix.size * 8) return false
        val fullBytes = bits / 8
        val remainingBits = bits % 8
        return (0 until fullBytes).all { prefix[it] == target[it] } &&
            (remainingBits == 0 || ((prefix[fullBytes].toInt() xor target[fullBytes].toInt()) and
                (0xff shl (8 - remainingBits))) == 0)
    }
}
