package com.salticusteam.vifi.feature.profile

import app.cash.turbine.test
import com.salticusteam.vifi.core.data.catalog.CatalogSource
import com.salticusteam.vifi.core.data.catalog.UniversityCatalog
import com.salticusteam.vifi.core.data.profile.ProfileState
import com.salticusteam.vifi.core.data.profile.ProfileStore
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeProfileService
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.CatalogItem
import com.salticusteam.vifi.core.model.CatalogSelection
import com.salticusteam.vifi.core.model.Gender
import com.salticusteam.vifi.core.model.ProfileError
import com.salticusteam.vifi.core.model.UserProfile
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileFormViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val user = FakeAuthService.DEFAULT_USER
    private val university = CatalogItem(id = "ege-universitesi", name = "Ege Üniversitesi")
    private val faculty = CatalogItem(id = "muhendislik-fakultesi", name = "Mühendislik Fakültesi")
    private val department = CatalogItem(id = "bilgisayar-muhendisligi", name = "Bilgisayar Mühendisliği")
    private val catalog = UniversityCatalog(listOf(university), listOf(faculty), listOf(department))

    private lateinit var profileService: FakeProfileService
    private lateinit var profileStore: ProfileStore
    private lateinit var authService: FakeAuthService
    private lateinit var analytics: FakeAnalyticsTracker

    @Before
    fun setUp() {
        profileService = FakeProfileService()
        profileStore = ProfileStore(profileService)
        authService = FakeAuthService(isSignedIn = true)
        analytics = FakeAnalyticsTracker()
    }

    private fun viewModel(existing: UserProfile? = null) = ProfileFormViewModel(
        user = user,
        existing = existing,
        profileService = profileService,
        profileStore = profileStore,
        authService = authService,
        analytics = analytics,
        catalogSource = object : CatalogSource {
            override suspend fun catalog() = catalog
        },
    )

    private fun ProfileFormViewModel.fillIn() {
        onNameChange("Ayşe")
        onSurnameChange("Yılmaz")
        onPickerOpen(CatalogField.UNIVERSITY)
        onCatalogSelected(CatalogSelection.catalog(university))
        onPickerOpen(CatalogField.FACULTY)
        onCatalogSelected(CatalogSelection.catalog(faculty))
        onPickerOpen(CatalogField.DEPARTMENT)
        onCatalogSelected(CatalogSelection.catalog(department))
        onGenderSelected(Gender.FEMALE)
    }

    @Test
    fun `init loads the catalog and takes the phone number from the user`() = runTest {
        val sut = viewModel()
        advanceUntilIdle()

        val state = sut.uiState.value
        assertEquals(catalog, state.catalog)
        assertEquals(user.phoneNumber, state.phoneNumber)
        assertFalse(state.canSave)
    }

    @Test
    fun `init when editing prefills the existing profile`() = runTest {
        val sut = viewModel(existing = FakeProfileService.SAMPLE_PROFILE)

        val state = sut.uiState.value
        assertEquals(FakeProfileService.SAMPLE_PROFILE, state.builtProfile)
        assertTrue(state.canSave)
    }

    @Test
    fun `onCatalogSelected sets the open picker's field and closes it`() = runTest {
        val sut = viewModel()

        sut.onPickerOpen(CatalogField.FACULTY)
        assertEquals(CatalogField.FACULTY, sut.uiState.value.picker)
        sut.onCatalogSelected(CatalogSelection.custom("Diş Hekimliği Fakültesi"))

        val state = sut.uiState.value
        assertNull(state.picker)
        assertEquals("dis-hekimligi-fakultesi", state.faculty.id)
        assertTrue(state.faculty.isCustom)
        assertTrue(state.university.isEmpty)
    }

    @Test
    fun `save when complete stores the profile, opens the gate and emits Saved`() = runTest {
        val sut = viewModel()
        sut.fillIn()

        sut.events.test {
            sut.save()
            assertTrue(sut.uiState.value.isWorking)
            assertEquals(ProfileFormEvent.Saved, awaitItem())
        }

        val saved = profileService.storedProfile(user.id)
        assertEquals("Ayşe", saved?.name)
        assertEquals("ege-universitesi", saved?.universityId)
        assertEquals(ProfileState.Present(checkNotNull(saved)), profileStore.state.value)
        assertEquals(listOf(AnalyticsEvent.ProfileCompleted), analytics.events)
    }

    @Test
    fun `save when editing tracks a profile update`() = runTest {
        val sut = viewModel(existing = FakeProfileService.SAMPLE_PROFILE)
        sut.onNameChange("Zeynep")

        sut.save()
        advanceUntilIdle()

        assertEquals("Zeynep", profileService.storedProfile(user.id)?.name)
        assertEquals(listOf(AnalyticsEvent.ProfileUpdated), analytics.events)
    }

    @Test
    fun `save when the service fails shows its message and allows another try`() = runTest {
        profileService.failure = ProfileError.Offline
        val sut = viewModel()
        sut.fillIn()

        sut.save()
        advanceUntilIdle()

        val state = sut.uiState.value
        assertFalse(state.isWorking)
        assertEquals(ProfileError.Offline.userMessage, state.error)
        assertTrue(state.canSave)
        assertEquals(ProfileState.Loading, profileStore.state.value)
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun `save when incomplete does nothing`() = runTest {
        val sut = viewModel()
        sut.onNameChange("Ayşe")

        sut.save()
        advanceUntilIdle()

        assertTrue(profileService.calls.isEmpty())
        assertFalse(sut.uiState.value.isWorking)
    }

    @Test
    fun `save while a save is running ignores the second tap and field edits`() = runTest {
        val gate = CompletableDeferred<Unit>()
        profileService.gate = gate
        val sut = viewModel()
        sut.fillIn()

        sut.save()
        advanceUntilIdle()
        sut.save()
        sut.onNameChange("Başka")
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, profileService.calls.size)
        assertEquals("Ayşe", profileService.storedProfile(user.id)?.name)
    }

    @Test
    fun `signOut signs the user out`() = runTest {
        val sut = viewModel()

        sut.signOut()

        assertNull(authService.currentUser)
    }

    @Test
    fun `onScreenShown reports the completion or edit screen`() = runTest {
        viewModel().onScreenShown()
        viewModel(existing = FakeProfileService.SAMPLE_PROFILE).onScreenShown()

        assertEquals(
            listOf(
                AnalyticsEvent.ScreenView(AnalyticsScreen.PROFILE_COMPLETION),
                AnalyticsEvent.ScreenView(AnalyticsScreen.EDIT_PROFILE),
            ),
            analytics.events,
        )
    }
}
