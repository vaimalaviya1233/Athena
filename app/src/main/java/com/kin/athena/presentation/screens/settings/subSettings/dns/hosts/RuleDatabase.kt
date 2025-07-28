/* Copyright (C) 2024 Charles Lombardo <clombardo169@gmail.com>
 *
 * Derived from DNS66:
 * Copyright (C) 2016 - 2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */


package com.kin.athena.presentation.screens.settings.subSettings.dns.hosts

import androidx.collection.MutableIntSet
import androidx.collection.intSetOf
import com.kin.athena.core.logging.Logger
import com.kin.athena.core.utils.Shell
import com.kin.athena.presentation.config
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.Reader
import javax.inject.Singleton

@Singleton
class RuleDatabase {

    companion object {
        private const val IPV4_LOOPBACK = "127.0.0.1"
        private const val IPV6_LOOPBACK = "::1"
        private const val NO_ROUTE = "0.0.0.0"

        fun parseLine(line: String): String? {
            if (line.isEmpty() || line.isBlank()) {
                return null
            }

            var endOfLine = line.indexOf('#')
            if (endOfLine == -1) {
                endOfLine = line.length
            }

            if (endOfLine <= 0) {
                return null
            }

            var startOfHost = 0
            val ipv4LoopbackIndex = line.lastIndexOf(IPV4_LOOPBACK)
            if (ipv4LoopbackIndex != -1) {
                startOfHost += (ipv4LoopbackIndex + IPV4_LOOPBACK.length)
            }
            if (startOfHost == 0) {
                val ipv6LoopbackIndex = line.lastIndexOf(IPV6_LOOPBACK)
                if (ipv6LoopbackIndex != -1) {
                    startOfHost += (ipv6LoopbackIndex + IPV6_LOOPBACK.length)
                }
            }
            if (startOfHost == 0) {
                val noRouteIndex = line.lastIndexOf(NO_ROUTE)
                if (noRouteIndex != -1) {
                    startOfHost += (noRouteIndex + NO_ROUTE.length)
                }
            }

            if (startOfHost >= endOfLine) {
                return null
            }

            while (startOfHost < endOfLine && Character.isWhitespace(line[startOfHost])) {
                startOfHost++
            }
            while (startOfHost < endOfLine && Character.isWhitespace(line[endOfLine - 1])) {
                endOfLine--
            }

            val host = line.substring(startOfHost, endOfLine).lowercase()
            if (host.isEmpty() || host.any { Character.isWhitespace(it) }) {
                return null
            }
            return host
        }
    }

    var blockedHosts = MutableStateFlow(atomic(intSetOf()))

    fun isBlocked(host: String): Boolean = blockedHosts.value.value.contains(host.hashCode())


    @Synchronized
    @Throws(InterruptedException::class)
    fun initialize(rootMode: Boolean = false): List<String>? {

        val sortedHostItems = config.hosts.items
            .mapNotNull {
                if (it.state != HostState.IGNORE) it else null
            }
            .sortedBy { it.state.ordinal }

        val newHosts = MutableIntSet(sortedHostItems.size + config.hosts.exceptions.size)
        val allHosts = mutableListOf<String>()

        for (item in sortedHostItems) {
            if (Thread.interrupted()) {
                throw InterruptedException("Interrupted")
            }
            val hostsFromItem = loadItem(newHosts, item, rootMode)
            if (rootMode && hostsFromItem != null) {
                allHosts.addAll(hostsFromItem)
            }
        }

        if (rootMode) {
            return allHosts
        }

        for (exception in config.hosts.exceptions) {
            if (Thread.interrupted()) {
                throw InterruptedException("Interrupted")
            }
            addHostException(newHosts, exception)
        }

        blockedHosts.value = atomic(newHosts)
        Runtime.getRuntime().gc()
        return null
    }

    @Throws(InterruptedException::class)
    private fun loadItem(set: MutableIntSet, item: HostFile, rootMode: Boolean): List<String>? {
        if (item.state == HostState.IGNORE) {
            return null
        }

        val reader = try {
            FileHelper.openItemFile(item)
        } catch (e: FileNotFoundException) {
            Logger.error("loadItem: File not found: ${item.data}", e)
            return null
        }

        val hosts = mutableListOf<String>()

        if (reader == null) {
            val host = addHost(set, item, item.data, rootMode)
            if (rootMode && host != null) {
                hosts.add(host)
                return hosts
            }
        } else {
            val readerHosts = loadReader(set, item, reader, rootMode)
            if (rootMode && readerHosts != null) {
                hosts.addAll(readerHosts)
                return hosts
            }
        }
        return if (rootMode) hosts else null
    }

    private fun addHost(set: MutableIntSet, item: Host, host: String, rootMode: Boolean): String? {
        when (item.state) {
            HostState.ALLOW -> {
                set.remove(host.hashCode())
                return if (rootMode) host else null
            }
            HostState.DENY -> {
                if (rootMode) {
                    return host
                } else {
                    set.add(host.hashCode())
                    return null
                }
            }
            else -> return null
        }
    }

    private fun addHostException(set: MutableIntSet, exception: HostException) {
        when (exception.state) {
            HostState.ALLOW -> set.remove(exception.data.hashCode())
            HostState.DENY -> set.add(exception.data.hashCode())
            else -> return
        }
    }

    @Throws(InterruptedException::class)
    private fun loadReader(set: MutableIntSet, item: Host, reader: Reader, rootMode: Boolean): List<String>? {
        val hosts = mutableListOf<String>()
        var count = 0
        try {
            Logger.error("loadBlockedHosts: Reading: ${item.data}")
            BufferedReader(reader).use {
                var line = it.readLine()
                while (line != null) {
                    if (Thread.interrupted()) {
                        throw InterruptedException("Interrupted")
                    }

                    val host = parseLine(line)
                    if (host != null) {
                        count++
                        val result = addHost(set, item, host, rootMode)
                        if (rootMode && result != null) {
                            hosts.add(result)
                        }
                    }
                    line = it.readLine()
                }
            }
            Logger.error("loadBlockedHosts: Loaded $count hosts from ${item.data}")
            return if (rootMode) hosts else null
        } catch (e: IOException) {
            Logger.error(
                "loadBlockedHosts: Error while reading ${item.data} after $count items",
                e
            )
            return if (rootMode) hosts else null
        }
    }
}