package com.salticusteam.vifi.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.salticusteam.vifi.core.model.OnboardingState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

@Singleton
class DataStoreOnboardingPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : OnboardingPreferences {

    override val state: Flow<OnboardingState> = dataStore.data
        .map { if (it[SEEN] == true) OnboardingState.Hidden else OnboardingState.Presenting }
        .distinctUntilChanged()
        .onStart { emit(OnboardingState.Loading) }

    override suspend fun markSeen() = seed(seen = true)

    override suspend fun seed(seen: Boolean) {
        dataStore.edit { it[SEEN] = seen }
    }

    private companion object {
        // Version suffix: bump to ".v2" for a redesigned tour that should show again.
        val SEEN = booleanPreferencesKey("onboarding.tour.v1")
    }
}
