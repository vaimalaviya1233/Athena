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

package com.kin.athena.service.vpn.service

import android.os.Build
import android.util.Log
import com.kin.athena.core.logging.Logger
import com.kin.athena.service.firewall.handler.RuleHandler
import com.kin.athena.service.firewall.handler.filterPacket
import com.kin.athena.service.vpn.network.transport.tcp.TCPHeader
import com.kin.athena.service.vpn.network.transport.udp.UDPModel
import com.kin.athena.service.vpn.network.transport.udp.toUDPHeader
import com.kin.athena.service.vpn.network.transport.udp.extractUDPData
import com.kin.athena.service.vpn.network.transport.icmp.ICMPModel
import com.kin.athena.service.vpn.network.transport.icmp.toICMPPacket
import com.kin.athena.service.vpn.network.transport.ipv4.IPv4
import com.kin.athena.service.vpn.network.transport.ipv4.toIPv4Header
import com.kin.athena.service.vpn.network.transport.dns.DNSModel
import com.kin.athena.service.vpn.network.transport.dns.toDNSModel
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TunnelManager(
    private val ruleHandler: RuleHandler? = null,
    private val targetDnsServer: String = "9.9.9.9"
) {
    
    private var contextPtr: Long = 0


    
    fun initialize(): Boolean {
        contextPtr = jni_init(Build.VERSION.SDK_INT)
        return contextPtr != 0L
    }
    
    fun start(logLevel: Int) {
        if (contextPtr != 0L) {
            jni_start(contextPtr, logLevel)
        }
    }
    
    fun run(tunFd: Int, forwardDns: Boolean = true, rcode: Int = 3) {
        if (contextPtr != 0L) {
            jni_run(contextPtr, tunFd, forwardDns, rcode)
        }
    }
    
    fun stop() {
        if (contextPtr != 0L) {
            jni_stop(contextPtr)
        }
    }
    
    fun done() {
        if (contextPtr != 0L) {
            jni_done(contextPtr)
            contextPtr = 0L
        }
    }
    
    fun getMtu(): Int {
        return jni_get_mtu()
    }
    
    fun getProperty(name: String): String {
        return jni_getprop(name)
    }
    
    fun clearSessions() {
        try {
            if (contextPtr != 0L) {
                jni_clear_sessions(contextPtr)
            } else {
                Logger.warn("Cannot clear sessions: TunnelManager not properly initialized")
            }
        } catch (e: Exception) {
            Logger.error("Error in clearSessions: ${e.message}", e)
        }
    }

    private fun onTcpPacketReceived(data: ByteArray, length: Int, direction: String): Boolean {
        return try {
            val buffer = ByteBuffer.wrap(data, 0, length)
            buffer.order(ByteOrder.BIG_ENDIAN)
            
            val ipHeader = buffer.toIPv4Header()
            val tcpHeader = TCPHeader.fromByteBuffer(buffer.slice())

            // Use existing firewall filtering logic
            ruleHandler?.let { ruleHandler ->
                val isSynPacket = tcpHeader.flags.contains(com.kin.athena.service.vpn.network.transport.tcp.TCPFlag.SYN) && 
                                 !tcpHeader.flags.contains(com.kin.athena.service.vpn.network.transport.tcp.TCPFlag.ACK)
                val filterResult = filterPacket(tcpHeader, ipHeader, ruleHandler, bypassCheck = !isSynPacket)
                
                val allowed = filterResult.first
                
                allowed
            } ?: run {
                Log.d("PacketFilter", "[$direction] TCP: No RuleHandler, allowing packet")
                true // Allow if no rule handler
            }
        } catch (e: Exception) {
            Log.e("PacketFilter", "Error filtering TCP packet: ${e.message}")
            true // Allow packet if parsing fails
        }
    }

    private fun onUdpPacketReceived(data: ByteArray, length: Int, direction: String): Boolean {
        return try {
            val buffer = ByteBuffer.wrap(data, 0, length)
            buffer.order(ByteOrder.BIG_ENDIAN)
            
            val ipHeader = buffer.toIPv4Header()
            val udpHeaderBuffer = buffer.slice()
            val udpHeader = udpHeaderBuffer.toUDPHeader()
            
            // Use existing firewall filtering logic
            ruleHandler?.let { ruleHandler ->
                val isDnsPacket = udpHeader.destinationPort == 53
                val dnsModel: DNSModel? = if (isDnsPacket) {
                    try {
                        val udpData = udpHeader.extractUDPData(udpHeaderBuffer)
                        ByteBuffer.wrap(udpData).toDNSModel()

                    } catch (e: Exception) {
                        Log.w("PacketFilter", "Failed to parse DNS model: ${e.message}")
                        null
                    }
                } else null

                val filterResult = filterPacket(udpHeader, ipHeader, ruleHandler, dnsModel)
                
                val allowed = filterResult.first

                allowed
            } ?: run {
                true
            }
        } catch (e: Exception) {
            true
        }
    }

    private fun onIcmpPacketReceived(data: ByteArray, length: Int, direction: String): Boolean {
        return try {
            val buffer = ByteBuffer.wrap(data, 0, length)
            buffer.order(ByteOrder.BIG_ENDIAN)
            
            val ipHeader = buffer.toIPv4Header()
            val icmpPacket = buffer.slice().toICMPPacket()

            ruleHandler?.let { ruleHandler ->
                val filterResult = filterPacket(icmpPacket, ipHeader, ruleHandler)
                
                val allowed = filterResult?.first ?: true

                allowed
            } ?: run {
                Log.d("PacketFilter", "[$direction] ICMP: No RuleHandler, allowing packet")
                true
            }
        } catch (e: Exception) {
            Log.e("PacketFilter", "Error filtering ICMP packet: ${e.message}")
            true
        }
    }

    fun release() {
        if (contextPtr != 0L) {
            stop()
            done()
            contextPtr = 0
        }
    }


    private external fun jni_init(sdk: Int): Long
    private external fun jni_start(context: Long, loglevel: Int)
    private external fun jni_run(context: Long, tun: Int, fwd53: Boolean, rcode: Int)
    private external fun jni_stop(context: Long)
    private external fun jni_done(context: Long)
    private external fun jni_getprop(name: String): String
    private external fun jni_get_mtu(): Int
    private external fun jni_clear_sessions(context: Long)

    companion object {
        init {
            System.loadLibrary("athena")
        }
    }
}