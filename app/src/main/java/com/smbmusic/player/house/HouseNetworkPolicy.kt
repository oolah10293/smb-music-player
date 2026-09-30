package com.smbmusic.player.house

import java.net.InetAddress

/** A route on a physical interface, never a route supplied by the VPN. */
data class HouseLanRoute(val address: InetAddress, val prefixLength: Int,
                         val hasGateway: Boolean, val unicast: Boolean)

object HouseNetworkPolicy {
    fun qualifies(wifiOrEthernet: Boolean, vpn: Boolean, notVpn: Boolean,
                  destination: InetAddress, routes: List<HouseLanRoute>): Boolean {
        if (!wifiOrEthernet || vpn || !notVpn || destination.isAnyLocalAddress ||
            destination.isLoopbackAddress || destination.isMulticastAddress) return false
        val target = destination.address
        return routes.any { route ->
            val prefix = route.address.address
            if (route.hasGateway || !route.unicast || route.prefixLength <= 0 ||
                prefix.size != target.size || route.prefixLength > prefix.size * 8) false
            else {
                val fullBytes = route.prefixLength / 8
                val remainingBits = route.prefixLength % 8
                (0 until fullBytes).all { prefix[it] == target[it] } &&
                    (remainingBits == 0 || ((prefix[fullBytes].toInt() xor target[fullBytes].toInt()) and
                        (0xff shl (8 - remainingBits))) == 0)
            }
        }
    }
}
