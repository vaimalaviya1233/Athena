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

package com.kin.athena.service.vpn.network.transport.udp

import com.kin.athena.core.logging.Logger
import java.nio.ByteBuffer


fun ByteBuffer.toUDPHeader(): UDPModel {
    if ((remaining()) < 8) {
        Logger.error("Minimum UDP header is 8 bytes.")
    }

    val srcPort = getShort().toInt() and 0xffff
    val destPort = getShort().toInt() and 0xffff
    val length = getShort().toInt() and 0xffff
    val checksum = getShort().toInt()

    return UDPModel(
        srcPort,
        destPort,
        length,
        checksum
    )
}

fun UDPModel.extractUDPData(buffer: ByteBuffer): ByteArray {
    val dataLength = this.length - 8
    val data = ByteArray(dataLength)
    buffer.get(data, 0, dataLength)

    return data
}
