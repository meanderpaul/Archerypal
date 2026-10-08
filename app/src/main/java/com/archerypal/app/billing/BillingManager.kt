package com.archerypal.app.billing

import android.content.Context
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.logInWith
import com.revenuecat.purchases.restorePurchasesWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * RevenueCat billing aligned with Play products:
 * - `1plus` → [RevenueCatConfig.ENTITLEMENT_PRO]
 * - `1.adfree` → [RevenueCatConfig.ENTITLEMENT_AD_FREE]
 * - `lifetime` → Pro via promotional grant only (not sold in Paywall)
 *
 * Purchases are presented via RevenueCat Paywall UI; this class owns CustomerInfo.
 */
class BillingManager(
    context: Context,
    private val scope: CoroutineScope,
    private val onAdFreeChanged: suspend (owned: Boolean) -> Unit,
    private val onPlusGranted: suspend (active: Boolean, sku: String?, purchaseToken: String?) -> Unit
) : UpdatedCustomerInfoListener {

    private val appContext = context.applicationContext

    private val _isAdFree = MutableStateFlow(false)
    val isAdFree: StateFlow<Boolean> = _isAdFree.asStateFlow()

    private val _isPlusActive = MutableStateFlow(false)
    val isPlusActive: StateFlow<Boolean> = _isPlusActive.asStateFlow()

    private val _removeAdsPrice = MutableStateFlow<String?>(null)
    val removeAdsPrice: StateFlow<String?> = _removeAdsPrice.asStateFlow()

    private val _plusPrice = MutableStateFlow<String?>(null)
    val plusPrice: StateFlow<String?> = _plusPrice.asStateFlow()

    private val _billingReady = MutableStateFlow(false)
    val billingReady: StateFlow<Boolean> = _billingReady.asStateFlow()

    private val _billingMessage = MutableStateFlow<String?>(null)
    val billingMessage: StateFlow<String?> = _billingMessage.asStateFlow()

    private var lastPlusSku: String? = null
    private var loggedInFriendCode: String? = null
    private var desiredDisplayName: String? = null
    private var syncedDisplayName: Pair<String, String?>? = null
    private var pendingEmail: Pair<String, String>? = null

    fun plusCredentials(): Pair<String?, String?> = lastPlusSku to null

    fun startConnection() {
        RevenueCatConfig.configure(appContext)
        if (!RevenueCatConfig.isConfigured() || !Purchases.isConfigured) {
            _billingReady.value = false
            _billingMessage.value =
                "RevenueCat API key missing. Add revenuecat.api.key to local.properties."
            return
        }
        Purchases.sharedInstance.updatedCustomerInfoListener = this
        _billingReady.value = true
        refreshOfferings()
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                applyCustomerInfo(customerInfo)
            }

            override fun onError(error: PurchasesError) {
                _billingMessage.value = error.message
            }
        })
    }

    fun logIn(friendCode: String, forceRefresh: Boolean = false) {
        val code = friendCode.trim()
        if (code.isEmpty() || !Purchases.isConfigured) return
        // Already identified — skip network churn on every DataStore emit.
        // CustomerInfo still refreshes via startConnection, purchases, and explicit refresh.
        if (!forceRefresh && loggedInFriendCode == code) {
            syncDisplayName()
            return
        }
        Purchases.sharedInstance.logInWith(
            appUserID = code,
            onError = { error -> _billingMessage.value = error.message },
            onSuccess = { info, _ ->
                loggedInFriendCode = code
                syncDisplayName()
                applyCustomerInfo(info)
            }
        )
    }

    /** Shown on the RevenueCat customer page so support can find players by name, not just Friend Code. */
    fun setCustomerDisplayName(displayName: String?) {
        desiredDisplayName = displayName?.trim()?.takeIf { it.isNotEmpty() }
        syncDisplayName()
    }

    /**
     * Google account email for support lookup in RevenueCat. Only available at sign-in time,
     * so it is held in memory until [friendCode]'s login completes, never persisted.
     */
    fun setCustomerEmail(friendCode: String?, email: String?) {
        val code = friendCode?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val trimmed = email?.trim()?.takeIf { it.isNotEmpty() } ?: return
        pendingEmail = code to trimmed
        syncDisplayName()
    }

    /** Must run after logIn — attributes set earlier stay on the previous RC user. */
    private fun syncDisplayName() {
        val code = loggedInFriendCode ?: return
        if (!Purchases.isConfigured) return
        pendingEmail?.takeIf { it.first == code }?.let { (_, email) ->
            Purchases.sharedInstance.setEmail(email)
            pendingEmail = null
        }
        val target = code to desiredDisplayName
        if (syncedDisplayName == target) return
        Purchases.sharedInstance.setDisplayName(desiredDisplayName)
        syncedDisplayName = target
    }

    fun refreshCustomerInfo() {
        if (!Purchases.isConfigured) return
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                applyCustomerInfo(customerInfo)
            }

            override fun onError(error: PurchasesError) {
                _billingMessage.value = error.message
            }
        })
    }

    fun setCachedAdFree(adFree: Boolean) {
        if (!_isAdFree.value) _isAdFree.value = adFree
    }

    fun setCachedPlusActive(active: Boolean) {
        if (!_isPlusActive.value) _isPlusActive.value = active
    }

    fun clearBillingMessage() {
        _billingMessage.value = null
    }

    /**
     * Sync Play purchases onto the current RC app user, then refresh CustomerInfo.
     * Promotional grants (dashboard gifts) are not returned by Play restore — they
     * appear after [logIn] / [refreshCustomerInfo] for the Friend Code app user id.
     */
    fun restorePurchases(friendCode: String? = null) {
        if (!Purchases.isConfigured) {
            _billingMessage.value = "Billing is not ready."
            return
        }
        val code = friendCode?.trim().orEmpty()
        if (code.isNotEmpty()) {
            // Ensure we are the Friend Code customer before restore/refresh so promo
            // grants attached in the dashboard land on this device.
            Purchases.sharedInstance.logInWith(
                appUserID = code,
                onError = { error -> _billingMessage.value = error.message },
                onSuccess = { _, _ ->
                    loggedInFriendCode = code
                    syncDisplayName()
                    runRestoreAndRefresh()
                }
            )
        } else {
            runRestoreAndRefresh()
        }
    }

    private fun runRestoreAndRefresh() {
        Purchases.sharedInstance.restorePurchasesWith(
            onError = { _billingMessage.value = it.message },
            onSuccess = { restored ->
                applyCustomerInfo(restored)
                // Promo / dashboard grants often need a second CustomerInfo fetch.
                Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
                    override fun onReceived(customerInfo: CustomerInfo) {
                        applyCustomerInfo(customerInfo)
                        val pro = customerInfo.entitlements[RevenueCatConfig.ENTITLEMENT_PRO]
                        _billingMessage.value = if (pro?.isActive == true) {
                            "Purchases restored — Plus is active."
                        } else {
                            "No active Plus found for this account. " +
                                "If you were gifted Plus, confirm the Friend Code matches " +
                                "the RevenueCat customer that received the grant."
                        }
                    }

                    override fun onError(error: PurchasesError) {
                        _billingMessage.value = error.message
                    }
                })
            }
        )
    }

    fun destroy() {
        if (Purchases.isConfigured) {
            Purchases.sharedInstance.updatedCustomerInfoListener = null
        }
    }

    override fun onReceived(customerInfo: CustomerInfo) {
        applyCustomerInfo(customerInfo)
    }

    fun onCustomerInfoUpdated(info: CustomerInfo) {
        applyCustomerInfo(info)
    }

    private fun refreshOfferings() {
        if (!Purchases.isConfigured) return
        Purchases.sharedInstance.getOfferingsWith(
            onError = { /* keep last prices */ },
            onSuccess = { offerings ->
                val packages = offerings.current?.availablePackages.orEmpty()
                for (pkg in packages) {
                    val id = pkg.product.id
                    val price = pkg.product.price.formatted
                    when {
                        id == BillingProducts.REMOVE_ADS ||
                            id.contains("adfree", ignoreCase = true) -> {
                            _removeAdsPrice.value = price
                        }
                        id == BillingProducts.PLUS_MONTHLY ||
                            id.contains("1plus") ||
                            pkg.packageType == PackageType.MONTHLY ||
                            pkg.packageType == PackageType.ANNUAL -> {
                            if (!id.contains("lifetime", ignoreCase = true) &&
                                !id.startsWith("rc_promo")
                            ) {
                                _plusPrice.value = price
                            }
                        }
                    }
                }
            }
        )
    }

    private fun applyCustomerInfo(info: CustomerInfo) {
        val proEnt = info.entitlements[RevenueCatConfig.ENTITLEMENT_PRO]
        val adFreeEnt = info.entitlements[RevenueCatConfig.ENTITLEMENT_AD_FREE]
        val plusActive = proEnt?.isActive == true
        val adFreeActive = adFreeEnt?.isActive == true || plusActive

        val productId = proEnt?.productIdentifier.orEmpty()
        lastPlusSku = when {
            !plusActive -> null
            productId.contains("lifetime", ignoreCase = true) ||
                productId.startsWith("rc_promo") -> BillingProducts.PLUS_LIFETIME
            else -> BillingProducts.PLUS_MONTHLY
        }

        val plusChanged = _isPlusActive.value != plusActive
        val adChanged = _isAdFree.value != adFreeActive
        _isPlusActive.value = plusActive
        _isAdFree.value = adFreeActive

        scope.launch {
            if (adChanged) onAdFreeChanged(adFreeActive)
            if (plusChanged || plusActive) {
                onPlusGranted(plusActive, lastPlusSku, null)
            }
        }
    }
}
