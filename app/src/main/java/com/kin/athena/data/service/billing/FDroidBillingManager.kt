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
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.*
import com.kin.athena.BuildConfig
import com.kin.athena.R
import com.kin.athena.core.logging.Logger
import javax.inject.Inject

class FDroidBillingManager @Inject constructor(
    private val context: Activity
) : BillingInterface, PurchasesUpdatedListener {

    private var billingClient: BillingClient? = null
    private val productDetailsMap: MutableMap<String, ProductDetails> = mutableMapOf()
    private var pendingSuccessCallback: (() -> Unit)? = null
    private var isBillingAvailable = false
    private var isInitialized = false

    var showKofiDialog by mutableStateOf(false)
        private set
    private var currentOnSuccess: (() -> Unit)? = null

    init {
        tryInitializeBilling()
    }

    private fun tryInitializeBilling() {
        try {
            billingClient = BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder()
                        .enableOneTimeProducts()
                        .build()
                )
                .build()

            startConnection()
        } catch (e: Exception) {
            Logger.error("Failed to initialize Play Billing: ${e.message}")
            isBillingAvailable = false
            isInitialized = true
        }
    }

    private fun startConnection() {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                isInitialized = true
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Logger.info("F-Droid: Play Billing setup successful")
                    isBillingAvailable = true
                    queryProducts()
                } else {
                    Logger.info("F-Droid: Play Billing not available (${billingResult.responseCode}), will use Ko-fi fallback")
                    isBillingAvailable = false
                }
            }

            override fun onBillingServiceDisconnected() {
                Logger.info("F-Droid: Play Billing service disconnected, will use Ko-fi fallback")
                isBillingAvailable = false
                isInitialized = true
            }
        })
    }

    private fun queryProducts() {
        if (!isBillingAvailable || billingClient?.isReady != true) return

        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("all_features")
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("packet_logs")
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.productDetailsList.isNotEmpty()) {
                productDetailsList.productDetailsList.forEach { productDetails ->
                    productDetailsMap[productDetails.productId] = productDetails
                    Logger.info("F-Droid: Product details retrieved: ${productDetails.productId}")
                }
            } else {
                Logger.error("F-Droid: Failed to retrieve product details: ${billingResult.responseCode}")
            }
        }
    }

    override fun showPurchaseDialog(productId: String, onSuccess: () -> Unit) {
        if (!isInitialized) {
            // Wait for initialization
            Handler(Looper.getMainLooper()).postDelayed({
                showPurchaseDialog(productId, onSuccess)
            }, 1000)
            return
        }

        if (isBillingAvailable && billingClient?.isReady == true) {
            // Try Google Play Billing first
            Logger.info("F-Droid: Attempting Play Billing for $productId")
            attemptPlayBilling(productId, onSuccess)
        } else {
            // Fallback to Ko-fi
            Logger.info("F-Droid: Using Ko-fi fallback for $productId")
            showKofiFallbackDialog(productId, onSuccess)
        }
    }

    private fun attemptPlayBilling(productId: String, onSuccess: () -> Unit) {
        pendingSuccessCallback = onSuccess

        val queryPurchasesParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient?.queryPurchasesAsync(queryPurchasesParams) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val alreadyOwned = purchases.any { purchase ->
                    purchase.products.contains(productId) &&
                            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                if (alreadyOwned) {
                    Logger.info("F-Droid: $productId already owned via Play Billing")
                    pendingSuccessCallback?.invoke()
                    pendingSuccessCallback = null
                    return@queryPurchasesAsync
                }
            }

            // Try to launch billing flow
            val productDetails = productDetailsMap[productId]
            if (productDetails != null) {
                launchBillingFlow(productDetails)
            } else {
                // Query product details if not cached, then try billing flow
                queryProduct(productId) { newDetails ->
                    if (newDetails != null) {
                        launchBillingFlow(newDetails)
                    } else {
                        // If product details not available, fallback to Ko-fi
                        Logger.info("F-Droid: Product details not available, falling back to Ko-fi")
                        showKofiFallbackDialog(productId, onSuccess)
                    }
                }
            }
        }
    }

    private fun queryProduct(productId: String, callback: (ProductDetails?) -> Unit) {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList.productDetailsList.find { it.productId == productId }
                if (productDetails != null) {
                    productDetailsMap[productId] = productDetails
                }
                callback(productDetails)
            } else {
                Logger.error("F-Droid: Failed to retrieve product details for $productId: ${billingResult.responseCode}")
                callback(null)
            }
        }
    }

    private fun launchBillingFlow(productDetails: ProductDetails) {
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val billingResult = billingClient?.launchBillingFlow(context, billingFlowParams)
        if (billingResult?.responseCode == BillingClient.BillingResponseCode.OK) {
            Logger.info("F-Droid: Play Billing flow launched successfully for ${productDetails.productId}")
        } else {
            Logger.error("F-Droid: Failed to launch Play Billing flow: ${billingResult?.responseCode}, falling back to Ko-fi")
            val callback = pendingSuccessCallback
            pendingSuccessCallback = null
            if (callback != null) {
                showKofiFallbackDialog(productDetails.productId, callback)
            }
        }
    }

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

    override fun isReady(): Boolean = isInitialized

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

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null) {
                    purchases.forEach { purchase ->
                        handlePurchase(purchase)
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Logger.info("F-Droid: User canceled the purchase")
                pendingSuccessCallback = null
            }
            else -> {
                Logger.error("F-Droid: Play Billing purchase update failed: ${billingResult.responseCode}")
                pendingSuccessCallback = null
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                Logger.info("F-Droid: Play Billing purchase successful: ${purchase.products}")

                if (!purchase.isAcknowledged) {
                    acknowledgePurchase(purchase)
                } else {
                    // Already acknowledged, grant entitlement
                    pendingSuccessCallback?.invoke()
                    pendingSuccessCallback = null
                }
            }
            Purchase.PurchaseState.PENDING -> {
                Logger.info("F-Droid: Purchase is pending: ${purchase.products}")
                // Don't grant entitlement yet - wait for purchase to complete
            }
            Purchase.PurchaseState.UNSPECIFIED_STATE -> {
                Logger.error("F-Droid: Purchase state unspecified: ${purchase.products}")
                pendingSuccessCallback = null
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient?.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Logger.info("F-Droid: Purchase acknowledged successfully")
                pendingSuccessCallback?.invoke()
                pendingSuccessCallback = null
            } else {
                Logger.error("F-Droid: Failed to acknowledge purchase: ${billingResult.responseCode}")
                pendingSuccessCallback = null
            }
        }
    }
}