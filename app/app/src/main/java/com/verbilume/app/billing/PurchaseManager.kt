package com.verbilume.app.billing

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
import com.verbilume.app.ads.AdsManager
import com.verbilume.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RemoveAdsState(
    val owned: Boolean = false,
    val priceLabel: String? = null,
    val ready: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)

class PurchaseManager(
    context: Context,
    private val settings: SettingsRepository,
    private val ads: AdsManager,
) : PurchasesUpdatedListener {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(RemoveAdsState())
    val state: StateFlow<RemoveAdsState> = _state.asStateFlow()

    private var product: ProductDetails? = null
    private var connected = false
    private var connecting = false
    private var started = false

    private val client: BillingClient by lazy {
        BillingClient.newBuilder(appContext)
            .setListener(this)
            .enableAutoServiceReconnection()
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
            )
            .build()
    }

    fun start() {
        if (started) return
        started = true
        scope.launch {
            val stored = runCatching { settings.settings.first().adsRemoved }.getOrDefault(false)
            if (stored) applyEntitlement(true, persist = false)
            connect()
        }
    }

    fun refresh() {
        if (client.isReady) {
            _state.update { it.copy(message = null) }
            loadProduct()
            refreshPurchases()
        } else {
            connect()
        }
    }

    fun purchase(activity: Activity?) {
        if (activity == null) return
        val current = _state.value
        if (current.owned || current.busy) return
        val details = product
        if (details == null) {
            refresh()
            return
        }
        _state.update { it.copy(busy = true, message = null) }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build(),
                ),
            )
            .build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.update { it.copy(busy = false, message = messageFor(result.responseCode)) }
            if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                refreshPurchases()
            }
        }
    }

    private fun connect() {
        if (connected || connecting || client.isReady) {
            if (client.isReady) refresh()
            return
        }
        connecting = true
        client.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    connecting = false
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        connected = true
                        loadProduct()
                        refreshPurchases()
                    } else {
                        connected = false
                        _state.update {
                            it.copy(
                                ready = false,
                                busy = false,
                                message = "Google Play isn't available right now.",
                            )
                        }
                    }
                }

                override fun onBillingServiceDisconnected() {
                    connecting = false
                    connected = false
                    _state.update { it.copy(ready = false, busy = false) }
                }
            },
        )
    }

    private fun loadProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(REMOVE_ADS_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(params) { result, queryResult ->
            val details = queryResult.productDetailsList.firstOrNull { it.productId == REMOVE_ADS_PRODUCT_ID }
            product = details
            _state.update {
                it.copy(
                    priceLabel = details?.oneTimePurchaseOfferDetails?.formattedPrice ?: it.priceLabel,
                    ready = details != null,
                    message = when {
                        details != null -> it.message
                        result.responseCode == BillingClient.BillingResponseCode.OK ->
                            "This offer isn't available on your account yet."
                        else -> messageFor(result.responseCode)
                    },
                )
            }
        }
    }

    private fun refreshPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val relevant = purchases.filter { it.products.contains(REMOVE_ADS_PRODUCT_ID) }
            val owned = relevant.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            applyEntitlement(owned, persist = true)
            relevant.forEach(::acknowledgeIfNeeded)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val purchase = purchases?.firstOrNull { it.products.contains(REMOVE_ADS_PRODUCT_ID) }
                if (purchase == null) {
                    _state.update { it.copy(busy = false) }
                    return
                }
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    acknowledgeIfNeeded(purchase)
                    applyEntitlement(true, persist = true)
                    _state.update {
                        it.copy(
                            owned = true,
                            busy = false,
                            message = "Purchase complete — ads are off.",
                        )
                    }
                } else {
                    _state.update {
                        it.copy(busy = false, message = "Purchase pending approval.")
                    }
                }
            }

            BillingClient.BillingResponseCode.USER_CANCELED ->
                _state.update { it.copy(busy = false) }

            else -> {
                _state.update { it.copy(busy = false, message = messageFor(result.responseCode)) }
                refreshPurchases()
            }
        }
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build(),
        ) { }
    }

    private fun applyEntitlement(owned: Boolean, persist: Boolean) {
        if (_state.value.owned != owned) _state.update { it.copy(owned = owned) }
        ads.setAdsRemoved(owned)
        if (persist) {
            scope.launch { settings.update { it.copy(adsRemoved = owned) } }
        }
    }

    private fun messageFor(responseCode: Int): String = when (responseCode) {
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
            "Google Play billing isn't available on this device."
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
            "This offer isn't available right now."
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
            "This purchase is already linked to your account."
        BillingClient.BillingResponseCode.NETWORK_ERROR,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        -> "Couldn't reach Google Play. Check your connection and try again."
        else -> "The purchase couldn't be completed. Try again."
    }

    companion object {
        const val REMOVE_ADS_PRODUCT_ID = "remove_ads"
    }
}
