/*
 * Copyright (C) 2025 Vexzure
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package com.kin.athena.service.vpn.network.util

object NetworkConstants {
    val DNS_SERVERS: List<Pair<String, Pair<String, String>>> = listOf(
        Pair("Google", Pair("8.8.8.8", "8.8.4.4")),
        Pair("Cloudflare", Pair("1.1.1.1", "1.0.0.1")),
        Pair("OpenDNS", Pair("208.67.222.222", "208.67.220.220")),
        Pair("Quad9", Pair("9.9.9.9", "149.112.112.112")),
        Pair("Comodo Secure DNS", Pair("8.26.56.26", "8.20.247.20")),
        Pair("Level3 DNS", Pair("4.2.2.1", "4.2.2.2")),
        Pair("Verisign Public DNS", Pair("64.6.64.6", "64.6.65.6")),
        Pair("Yandex Basic", Pair("77.88.8.8", "77.88.8.1")),
        Pair("Yandex Safe", Pair("77.88.8.88", "77.88.8.2")),
        Pair("Yandex Family", Pair("77.88.8.7", "77.88.8.3")),
        Pair("CleanBrowsing Family Filter", Pair("185.228.168.168", "185.228.169.168")),
        Pair("CleanBrowsing Adult Filter", Pair("185.228.168.10", "185.228.169.11")),
        Pair("CleanBrowsing Security Filter", Pair("185.228.168.9", "185.228.169.9")),
        Pair("Neustar Recursive", Pair("156.154.70.1", "156.154.71.1")),
        Pair("Neustar Threat Protection", Pair("156.154.70.2", "156.154.71.2")),
        Pair("Neustar Family Protection", Pair("156.154.70.3", "156.154.71.3"))
    )
    const val VPN_ADDRESS: String = "10.0.10.1"
    const val ALL_TRAFFIC: String = "0.0.0.0"
    const val VPN6_ADDRESS: String = "fd00:1:fd00:1:fd00:1:fd00:1"

    const val ACTION_START_VPN = "com.kin.athena.START_VPN"
    const val ACTION_STOP_VPN = "com.kin.athena.vpn.controller.ACTION_STOP_VPN"

    const val ACTION_TOGGLE_WIFI = "com.kin.athena.vpn.controller.ACTION_TOGGLE_WIFI"
    const val ACTION_TOGGLE_CELLURAL = "com.kin.athena.vpn.controller.ACTION_TOGGLE_CELLULAR"

    const val ACTION_CLEAR_SESSIONS =  "com.kin.athena.vpn.controller.ACTION_CLEAR_SESSIONS"

    const val ACTION_START_ROOT = "com.kin.athena.START_ROOT"
    const val ACTION_STOP_ROOT = "com.kin.athena.vpn.controller.ACTION_STOP_ROOT"

    const val TCP_PROTOCOL: Byte = 6
    const val UDP_PROTOCOL: Byte = 17
    const val ICMP_PROTOCOL: Byte = 1
    const val MAX_PACKET_LEN = 8 * 1024
}
