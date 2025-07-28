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

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kin.athena.core.logging.Logger
import com.kin.athena.presentation.config
import com.kin.athena.service.utils.manager.FirewallManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@HiltWorker
class RuleDatabaseUpdateWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val firewallManager: FirewallManager,
) : CoroutineWorker(context, params) {

    companion object {
        var lastErrors by atomic<MutableList<String>?>(null)

        private const val DATABASE_UPDATE_TIMEOUT = 3600000L

        @SuppressLint("MutableCollectionMutableState")
        var doneNames = MutableStateFlow(mutableListOf<String>())

        private val _isRefreshing = MutableStateFlow(false)
    }

    private val errors = ArrayList<String>()
    private val pending = ArrayList<String>()
    private val done = ArrayList<String>()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Logger.info("DNS Manager Starting")
        _isRefreshing.value = true
        val start = System.currentTimeMillis()
        val jobs = mutableListOf<Deferred<Unit>>()
        config.hosts.items.forEach {
            val update = RuleDatabaseItemUpdate(context, this@RuleDatabaseUpdateWorker, it)
            if (update.shouldDownload()) {
                val job = async { update.run() }
                job.start()
                jobs.add(job)
            }
        }

        releaseGarbagePermissions()

        try {
            withTimeout(DATABASE_UPDATE_TIMEOUT) {
                jobs.awaitAll()
            }
        } catch (_: TimeoutCancellationException) {
        }
        val end = System.currentTimeMillis()
        Logger.info("DNS Manager Finished (${end - start} milliseconds)")
        postExecute()
        Result.success()
    }

    private fun releaseGarbagePermissions() {
        val contentResolver = context.contentResolver
        for (permission in contentResolver.persistedUriPermissions) {
            if (isGarbage(permission.uri)) {
                Logger.info("releaseGarbagePermissions: Releasing permission for ${permission.uri}")
                contentResolver.releasePersistableUriPermission(permission.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                Logger.info("releaseGarbagePermissions: Keeping permission for ${permission.uri}")
            }
        }
    }

    private fun isGarbage(uri: Uri): Boolean {
        for (item in config.hosts.items) {
            if (Uri.parse(item.data) == uri) {
                return false
            }
        }
        return true
    }

    @Synchronized
    private fun updateProgressNotification() {
        val builder = StringBuilder()
        for (p in pending) {
            if (builder.isNotEmpty()) {
                builder.append("\n")
            }
            builder.append(p)
        }
    }

    @Synchronized
    private fun postExecute() {
        if (!errors.isEmpty()) {
            lastErrors = errors
        }
        _isRefreshing.value = false
    }

    @Synchronized
    fun addError(item: Host, message: String) {
        Logger.error("error: ${item.title}:$message")
        errors.add("${item.title}\n$message")
    }

    @Synchronized
    fun addDone(item: Host) {
        firewallManager.updateDomains()
        pending.remove(item.title)
        done.add(item.title)
        doneNames.value = doneNames.value.toMutableList().apply { add(item.title) }
        updateProgressNotification()
    }


    @Synchronized
    fun addBegin(item: Host) {
        pending.add(item.title)
        updateProgressNotification()
    }
}