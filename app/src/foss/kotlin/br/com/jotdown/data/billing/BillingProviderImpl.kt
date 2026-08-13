package br.com.jotdown.data.billing

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** FOSS ships no Play Billing — proprietary, and there's nothing to unlock here anyway. */
class BillingProviderImpl(context: Context) : BillingProvider {
    override val isPro: StateFlow<Boolean> = MutableStateFlow(false)
    override val proPrice: StateFlow<String?> = MutableStateFlow(null)
    override fun launchPurchase(activity: Activity) { /* no-op */ }
    override fun destroy() {}
}
