package com.archerypal.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BillingManager(
    context: Context,
    private val scope: CoroutineScope,
    private val onAdFreeGranted: suspend () -> Unit
) : PurchasesUpdatedListener {

    private val appContext = context.applicationContext

    private val _isAdFree = MutableStateFlow(false)
    val isAdFree: StateFlow<Boolean> = _isAdFree.asStateFlow()

    private val _removeAdsPrice = MutableStateFlow<String?>(null)
    val removeAdsPrice: StateFlow<String?> = _removeAdsPrice.asStateFlow()

    private val _billingReady = MutableStateFlow(false)
    val billingReady: StateFlow<Boolean> = _billingReady.asStateFlow()

    private val _billingMessage = MutableStateFlow<String?>(null)
    val billingMessage: StateFlow<String?> = _billingMessage.asStateFlow()

    private var removeAdsProductDetails: ProductDetails? = null

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun startConnection() {
        if (billingClient.isReady) {
            onBillingReady()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    onBillingReady()
                } else {
                    _billingReady.value = false
                    _billingMessage.value = billingErrorMessage(billingResult)
                }
            }

            override fun onBillingServiceDisconnected() {
                _billingReady.value = false
            }
        })
    }

    fun setCachedAdFree(adFree: Boolean) {
        _isAdFree.value = adFree
    }

    fun clearBillingMessage() {
        _billingMessage.value = null
    }

    fun launchRemoveAdsPurchase(activity: Activity) {
        if (_isAdFree.value) return
        if (!billingClient.isReady) {
            _billingMessage.value = "Google Play billing is not ready yet. Try again in a moment."
            startConnection()
            return
        }
        val productDetails = removeAdsProductDetails
        if (productDetails == null) {
            _billingMessage.value = "Remove ads is not available yet. Check Play Console product setup."
            queryRemoveAdsProduct()
            return
        }
        val offerToken = productDetails.oneTimePurchaseOfferDetails?.offerToken
        if (offerToken == null) {
            _billingMessage.value = "Remove ads pricing is not configured yet."
            return
        }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()
        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _billingMessage.value = billingErrorMessage(result)
        }
    }

    fun destroy() {
        billingClient.endConnection()
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach(::handlePurchase)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> {
                _billingMessage.value = billingErrorMessage(billingResult)
            }
        }
    }

    private fun onBillingReady() {
        _billingReady.value = true
        queryRemoveAdsProduct()
        restorePurchases()
    }

    private fun queryRemoveAdsProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(BillingProducts.REMOVE_ADS)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        billingClient.queryProductDetailsAsync(params) { billingResult, queryProductDetailsResult ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                return@queryProductDetailsAsync
            }
            val productDetails = queryProductDetailsResult.productDetailsList.firstOrNull()
                ?: return@queryProductDetailsAsync
            removeAdsProductDetails = productDetails
            _removeAdsPrice.value = productDetails.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    private fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                return@queryPurchasesAsync
            }
            val owned = purchases.any { purchase ->
                purchase.products.contains(BillingProducts.REMOVE_ADS) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            if (owned) {
                grantAdFree()
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.products.contains(BillingProducts.REMOVE_ADS)) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    grantAdFree()
                } else {
                    _billingMessage.value = billingErrorMessage(billingResult)
                }
            }
        } else {
            grantAdFree()
        }
    }

    private fun grantAdFree() {
        if (_isAdFree.value) return
        _isAdFree.value = true
        scope.launch {
            onAdFreeGranted()
        }
    }

    private fun billingErrorMessage(billingResult: BillingResult): String {
        return when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
                "Billing is unavailable on this device."
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
                "Remove ads is not available in Play Console yet."
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED ->
                "Lost connection to Google Play. Try again."
            else -> billingResult.debugMessage.ifBlank {
                "Purchase failed (code ${billingResult.responseCode})."
            }
        }
    }
}
