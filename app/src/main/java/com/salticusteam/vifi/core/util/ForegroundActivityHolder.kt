package com.salticusteam.vifi.core.util

import android.app.Activity
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single host Activity, held weakly, for the few SDK calls that need one (phone verification's
 * reCAPTCHA fallback, `PhoneAuthOptions.setActivity`). MainActivity attaches itself on create and detaches on
 * destroy, so nothing here outlives it; ViewModels never see the Activity.
 */
@Singleton
class ForegroundActivityHolder @Inject constructor() {

    @Volatile
    private var reference: WeakReference<Activity>? = null

    /** The attached Activity, or `null` when there is none (or it was collected). */
    val activity: Activity? get() = reference?.get()

    fun attach(activity: Activity) {
        reference = WeakReference(activity)
    }

    /** Detaches [activity] only if it is still the attached one (a recreated Activity may have attached first). */
    fun detach(activity: Activity) {
        if (reference?.get() === activity) reference = null
    }
}
