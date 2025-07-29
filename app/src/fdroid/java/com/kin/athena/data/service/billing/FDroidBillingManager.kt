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

package com.kin.athena.data.service.billing

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kin.athena.BuildConfig
import com.kin.athena.core.logging.Logger
import javax.inject.Inject

class FDroidBillingManager @Inject constructor(
    private val context: Activity
) : BillingInterface {

    var showKofiDialog by mutableStateOf(false)
        private set
    private var currentOnSuccess: (() -> Unit)? = null

    override fun showPurchaseDialog(productId: String, onSuccess: () -> Unit) {
        Logger.info("F-Droid: Using Ko-fi donation for $productId")
        showKofiFallbackDialog(productId, onSuccess)
    }

    override fun isReady(): Boolean = true

    private fun showKofiFallbackDialog(productId: String, onSuccess: () -> Unit) {
        currentOnSuccess = onSuccess
        showKofiDialog = true
    }

    fun dismissKofiDialog() {
        showKofiDialog = false
        currentOnSuccess = null
    }

    fun handleKofiClick() {
        openKofiPage()
        dismissKofiDialog()
    }

    private fun openKofiPage() {
        try {
            val kofiUrl = BuildConfig.KOFI_URL
            if (kofiUrl.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(kofiUrl))
                context.startActivity(intent)
                Logger.info("F-Droid: Opened Ko-fi page: $kofiUrl")
            } else {
                Logger.error("F-Droid: Ko-fi URL not configured")
            }
        } catch (e: Exception) {
            Logger.error("F-Droid: Failed to open Ko-fi page: ${e.message}")
        }
    }
}