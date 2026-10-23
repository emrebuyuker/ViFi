package com.salticusteam.vifi.core.appcheck

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds (emulators included) can't produce Play Integrity verdicts. The debug provider
 * logs a per-install secret to Logcat (tag "DebugAppCheckProvider"); register it in
 * Firebase Console › App Check › Apps › Manage debug tokens. Never commit that secret.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
