package com.salticusteam.vifi.core.appcheck

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release builds attest with Play Integrity. Requires the app's SHA-256 (upload key and, with
 * Play App Signing, the app-signing key) in Firebase Console and the Play Integrity API enabled.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
