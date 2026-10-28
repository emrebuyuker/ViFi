package com.salticusteam.vifi.core.data.billing

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.AlternativeBillingOnlyAvailabilityListener
import com.android.billingclient.api.AlternativeBillingOnlyInformationDialogListener
import com.android.billingclient.api.AlternativeBillingOnlyReportingDetailsListener
import com.android.billingclient.api.BillingChoiceInfoResponseListener
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingConfigResponseListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingProgramAvailabilityListener
import com.android.billingclient.api.BillingProgramInformationDialogListener
import com.android.billingclient.api.BillingProgramInformationDialogParams
import com.android.billingclient.api.BillingProgramReportingDetailsListener
import com.android.billingclient.api.BillingProgramReportingDetailsParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ConsumeResponseListener
import com.android.billingclient.api.GetBillingChoiceInfoParams
import com.android.billingclient.api.GetBillingConfigParams
import com.android.billingclient.api.InAppMessageParams
import com.android.billingclient.api.InAppMessageResponseListener
import com.android.billingclient.api.LaunchExternalLinkParams
import com.android.billingclient.api.LaunchExternalLinkResponseListener
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.salticusteam.vifi.core.util.ForegroundActivityHolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The live connection over a fake `BillingClient`: connecting, reconnecting, backoff. */
@OptIn(ExperimentalCoroutinesApi::class)
class BillingClientConnectionTest {

    private val client = FakeBillingClient()
    private val sut = BillingClientConnection(clientFactory = { client }, activityHolder = ForegroundActivityHolder())

    @Test
    fun `a call connects first`() = runTest {
        assertTrue(sut.queryPurchases(PlayProductType.SUBSCRIPTION).isEmpty())
        assertEquals(1, client.startConnections)
        assertEquals(1, client.purchaseQueries)
    }

    @Test
    fun `a call after Play drops the service reconnects and returns the result`() = runTest {
        sut.queryPurchases(PlayProductType.SUBSCRIPTION)
        client.purchaseAnswers += BillingResponseCode.SERVICE_DISCONNECTED

        assertTrue(sut.queryPurchases(PlayProductType.ONE_TIME).isEmpty())
        assertEquals(2, client.startConnections)
        assertEquals(3, client.purchaseQueries)
        assertEquals(1_000L, currentTime)
    }

    @Test
    fun `a failing connection is retried with exponential backoff, then thrown`() = runTest {
        repeat(3) { client.setupAnswers += BillingResponseCode.SERVICE_UNAVAILABLE }

        val error = runCatching { sut.queryPurchases(PlayProductType.SUBSCRIPTION) }.exceptionOrNull()

        assertEquals(PlayBillingResponse.SERVICE_UNAVAILABLE, (error as PlayBillingException).response)
        assertEquals(3, client.startConnections)
        assertEquals(0, client.purchaseQueries)
        assertEquals(3_000L, currentTime) // waited 1 s, then 2 s
    }

    @Test
    fun `a connection that recovers within the retries returns the result`() = runTest {
        client.setupAnswers += BillingResponseCode.SERVICE_DISCONNECTED

        assertTrue(sut.queryPurchases(PlayProductType.SUBSCRIPTION).isEmpty())
        assertEquals(2, client.startConnections)
    }

    @Test
    fun `a lasting failure is thrown at once`() = runTest {
        client.setupAnswers += BillingResponseCode.BILLING_UNAVAILABLE

        val error = runCatching { sut.queryPurchases(PlayProductType.SUBSCRIPTION) }.exceptionOrNull()

        assertEquals(PlayBillingResponse.BILLING_UNAVAILABLE, (error as PlayBillingException).response)
        assertEquals(1, client.startConnections)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `concurrent calls share one connection attempt`() = runTest {
        client.answersSetupAtOnce = false

        val first = async { sut.queryPurchases(PlayProductType.SUBSCRIPTION) }
        val second = async { sut.queryPurchases(PlayProductType.ONE_TIME) }
        runCurrent()
        assertEquals(1, client.startConnections)

        client.finishSetup(BillingResponseCode.OK)
        first.await()
        second.await()
        assertEquals(1, client.startConnections)
        assertEquals(2, client.purchaseQueries)
    }

    @Test
    fun `billingResponse maps Billing's codes and treats unknown ones as errors`() {
        val map = BillingClientConnection::billingResponse
        assertEquals(PlayBillingResponse.OK, map(BillingResponseCode.OK))
        assertEquals(PlayBillingResponse.NETWORK_ERROR, map(BillingResponseCode.NETWORK_ERROR))
        assertEquals(PlayBillingResponse.ITEM_ALREADY_OWNED, map(BillingResponseCode.ITEM_ALREADY_OWNED))
        assertEquals(PlayBillingResponse.ERROR, map(999))
    }
}

/** A `BillingClient` answering connections and purchase queries with scripted codes; nothing else is used. */
private class FakeBillingClient : BillingClient() {

    /** Answers of the next connection attempts; OK once empty. */
    val setupAnswers = ArrayDeque<Int>()

    /** Answers of the next purchase queries; OK (no purchases) once empty. */
    val purchaseAnswers = ArrayDeque<Int>()

    /** When false, a connection attempt waits for [finishSetup]. */
    var answersSetupAtOnce = true

    var startConnections = 0
        private set
    var purchaseQueries = 0
        private set

    private var ready = false
    private var setupListener: BillingClientStateListener? = null

    override fun isReady(): Boolean = ready

    override fun getConnectionState(): Int =
        if (ready) BillingClient.ConnectionState.CONNECTED else BillingClient.ConnectionState.DISCONNECTED

    override fun startConnection(listener: BillingClientStateListener) {
        startConnections++
        setupListener = listener
        if (answersSetupAtOnce) finishSetup(setupAnswers.removeFirstOrNull() ?: BillingResponseCode.OK)
    }

    fun finishSetup(code: Int) {
        ready = code == BillingResponseCode.OK
        checkNotNull(setupListener).onBillingSetupFinished(result(code))
    }

    override fun queryPurchasesAsync(params: QueryPurchasesParams, listener: PurchasesResponseListener) {
        purchaseQueries++
        val code = purchaseAnswers.removeFirstOrNull() ?: BillingResponseCode.OK
        if (code == BillingResponseCode.SERVICE_DISCONNECTED) ready = false
        listener.onQueryPurchasesResponse(result(code), emptyList())
    }

    override fun endConnection() {
        ready = false
    }

    private fun result(code: Int): BillingResult = BillingResult.newBuilder().setResponseCode(code).build()

    // Not used by these tests.
    override fun isFeatureSupported(feature: String): BillingResult = unused()
    override fun launchBillingFlow(activity: Activity, params: BillingFlowParams): BillingResult = unused()
    override fun showAlternativeBillingOnlyInformationDialog(
        activity: Activity,
        listener: AlternativeBillingOnlyInformationDialogListener,
    ): BillingResult = unused()
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION") // Deprecated in Billing but still abstract.
    override fun showExternalOfferInformationDialog(
        activity: Activity,
        listener: com.android.billingclient.api.ExternalOfferInformationDialogListener,
    ): BillingResult = unused()
    override fun showInAppMessages(
        activity: Activity,
        params: InAppMessageParams,
        listener: InAppMessageResponseListener,
    ): BillingResult = unused()
    override fun acknowledgePurchase(params: AcknowledgePurchaseParams, listener: AcknowledgePurchaseResponseListener) =
        unused()
    override fun consumeAsync(params: ConsumeParams, listener: ConsumeResponseListener) = unused()
    override fun createAlternativeBillingOnlyReportingDetailsAsync(
        listener: AlternativeBillingOnlyReportingDetailsListener,
    ) = unused()
    override fun createBillingProgramReportingDetailsAsync(
        params: BillingProgramReportingDetailsParams,
        listener: BillingProgramReportingDetailsListener,
    ) = unused()
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION") // Deprecated in Billing but still abstract.
    override fun createExternalOfferReportingDetailsAsync(
        listener: com.android.billingclient.api.ExternalOfferReportingDetailsListener,
    ) = unused()
    override fun getBillingChoiceInfoAsync(
        params: GetBillingChoiceInfoParams,
        listener: BillingChoiceInfoResponseListener,
    ) = unused()
    override fun getBillingConfigAsync(params: GetBillingConfigParams, listener: BillingConfigResponseListener) =
        unused()
    override fun isAlternativeBillingOnlyAvailableAsync(listener: AlternativeBillingOnlyAvailabilityListener) = unused()
    override fun isBillingProgramAvailableAsync(program: Int, listener: BillingProgramAvailabilityListener) = unused()
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION") // Deprecated in Billing but still abstract.
    override fun isExternalOfferAvailableAsync(
        listener: com.android.billingclient.api.ExternalOfferAvailabilityListener,
    ) = unused()
    override fun launchExternalLink(
        activity: Activity,
        params: LaunchExternalLinkParams,
        listener: LaunchExternalLinkResponseListener,
    ) = unused()
    override fun queryProductDetailsAsync(params: QueryProductDetailsParams, listener: ProductDetailsResponseListener) =
        unused()
    override fun showBillingProgramInformationDialog(
        activity: Activity,
        params: BillingProgramInformationDialogParams,
        listener: BillingProgramInformationDialogListener,
    ) = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException("Not used by BillingClientConnectionTest")
}
