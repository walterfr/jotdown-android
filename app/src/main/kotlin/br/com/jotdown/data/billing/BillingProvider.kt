package br.com.jotdown.data.billing

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/** Product ID of the single one-time Pro unlock, configured in Play Console. */
const val PRO_PRODUCT_ID = "pro_unlock"

interface BillingProvider {
    /** True once a completed, acknowledged purchase of [PRO_PRODUCT_ID] is on record. */
    val isPro: StateFlow<Boolean>
    /** Localized price (e.g. "R$ 24,90"), null until Play returns product details. */
    val proPrice: StateFlow<String?>
    /** Opens Play's purchase sheet for [PRO_PRODUCT_ID]. No-op if product details aren't loaded yet. */
    fun launchPurchase(activity: Activity)
    fun destroy()
}
