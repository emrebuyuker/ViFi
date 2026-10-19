# ViFi-Android ViewModel Template

`{Name}` → ViewModel adı (PascalCase), `<name>` → küçük harf özellik paketi, `{Model}` → domain modeli.
Tüm şablonlar `com.salticusteam.vifi` paketindendir, hedef mimari: Kotlin + Jetpack Compose + MVVM + Hilt.

> **Legacy not:** Bu şablonlar yeni/ileriye dönük koddur. Bugünkü v2.5 ekranları ViewModel katmanı olmadan Activity/Fragment içinde RTDB okur; UiState + ViewModel + StateFlow deseninin kurulumu yol haritası `FM-13`, Hilt `FM-11`, Coroutines/Flow `FM-05` kalemleridir. iOS eSimIO delegate/completion-handler şablonunun karşılığıdır — ama delegate yerine StateFlow kullanılır.

## Standard ViewModel Template

```kotlin
package com.salticusteam.vifi.feature.<name>

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.model.{Model}
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// UI State — single immutable snapshot of the screen.
data class {Name}UiState(
    val isLoading: Boolean = false,
    val items: List<{Model}> = emptyList(),
    val error: String? = null,
)

// One-time events — navigation / snackbar; never kept in UiState.
sealed interface {Name}Event {
    data class ShowMessage(val message: String) : {Name}Event
    data class NavigateToDetail(val id: String) : {Name}Event
}

@HiltViewModel
class {Name}ViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow({Name}UiState())
    val uiState: StateFlow<{Name}UiState> = _uiState.asStateFlow()

    private val _events = Channel<{Name}Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun load() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val items = archiveRepository.get{Model}s()
                _uiState.update { it.copy(isLoading = false, items = items) }
            } catch (error: ArchiveError) {
                _uiState.update { it.copy(isLoading = false, error = error.userMessage) }
            }
        }
    }

    fun onItemClick(id: String) {
        viewModelScope.launch { _events.send({Name}Event.NavigateToDetail(id)) }
    }
}
```

## Template with Pagination

```kotlin
package com.salticusteam.vifi.feature.<name>

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.model.{Model}
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class {Name}UiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val items: List<{Model}> = emptyList(),
    val canLoadMore: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class {Name}ViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow({Name}UiState())
    val uiState: StateFlow<{Name}UiState> = _uiState.asStateFlow()

    // Firestore cursor — last loaded document id.
    private var lastId: String? = null

    fun load() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val page = archiveRepository.get{Model}Page(pageSize = PAGE_SIZE, after = null)
                lastId = page.items.lastOrNull()?.id
                _uiState.update {
                    it.copy(isLoading = false, items = page.items, canLoadMore = page.hasMore)
                }
            } catch (error: ArchiveError) {
                _uiState.update { it.copy(isLoading = false, error = error.userMessage) }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.canLoadMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val page = archiveRepository.get{Model}Page(pageSize = PAGE_SIZE, after = lastId)
                lastId = page.items.lastOrNull()?.id ?: lastId
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        items = it.items + page.items,
                        canLoadMore = page.hasMore,
                    )
                }
            } catch (_: ArchiveError) {
                // Pagination fails silently; main list stays intact.
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    private companion object { const val PAGE_SIZE = 20 }
}
```

## Template with CRUD Operations

```kotlin
package com.salticusteam.vifi.feature.<name>

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.model.{Model}
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class {Name}UiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val items: List<{Model}> = emptyList(),
    val error: String? = null,
)

sealed interface {Name}Event {
    data object Created : {Name}Event
    data object Updated : {Name}Event
    data object Deleted : {Name}Event
    data class ShowMessage(val message: String) : {Name}Event
}

@HiltViewModel
class {Name}ViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow({Name}UiState())
    val uiState: StateFlow<{Name}UiState> = _uiState.asStateFlow()

    private val _events = Channel<{Name}Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun load() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val items = archiveRepository.get{Model}s()
                _uiState.update { it.copy(isLoading = false, items = items) }
            } catch (error: ArchiveError) {
                _uiState.update { it.copy(isLoading = false, error = error.userMessage) }
            }
        }
    }

    fun create(draft: {Model}Draft) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                archiveRepository.add{Model}(draft)
                _uiState.update { it.copy(isSaving = false) }
                _events.send({Name}Event.Created)
            } catch (error: ArchiveError) {
                _uiState.update { it.copy(isSaving = false, error = error.userMessage) }
            }
        }
    }

    fun update(id: String, draft: {Model}Draft) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                archiveRepository.update{Model}(id, draft)
                _uiState.update { it.copy(isSaving = false) }
                _events.send({Name}Event.Updated)
            } catch (error: ArchiveError) {
                _uiState.update { it.copy(isSaving = false, error = error.userMessage) }
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            try {
                archiveRepository.delete{Model}(id)
                // Optimistic local removal.
                _uiState.update { state -> state.copy(items = state.items.filterNot { it.id == id }) }
                _events.send({Name}Event.Deleted)
            } catch (error: ArchiveError) {
                _events.send({Name}Event.ShowMessage(error.userMessage))
            }
        }
    }
}
```

## Template with Local Session / DataStore Integration

iOS `LocalStore.userModel` karşılığı: oturum/kullanıcı durumu bir repository (DataStore veya `FirebaseAuth` sarmalı) arkasında `Flow` olarak gelir. `Context` ViewModel'e sızmaz.

```kotlin
package com.salticusteam.vifi.feature.<name>

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.model.AuthError
import com.salticusteam.vifi.core.data.repository.AuthRepository
import com.salticusteam.vifi.core.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class {Name}UiState(
    val user: UserProfile? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
)

sealed interface {Name}Event {
    data object Saved : {Name}Event
    data object SignedOut : {Name}Event
}

@HiltViewModel
class {Name}ViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow({Name}UiState())
    val uiState: StateFlow<{Name}UiState> = _uiState.asStateFlow()

    private val _events = Channel<{Name}Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        // Session as a stream (AuthStateListener -> StateFlow, roadmap AUTH-08).
        authRepository.currentUser
            .onEach { user -> _uiState.update { it.copy(user = user) } }
            .launchIn(viewModelScope)
    }

    fun updateProfile(name: String) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                authRepository.updateDisplayName(name)
                _uiState.update { it.copy(isSaving = false) }
                _events.send({Name}Event.Saved)
            } catch (error: AuthError) {
                _uiState.update { it.copy(isSaving = false, error = error.userMessage) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _events.send({Name}Event.SignedOut)
        }
    }
}
```

## Fake Repository Template (for Testing)

iOS "Mock delegate + çağrıldı-mı bayrakları" karşılığı. Android'de delegate olmadığı için **Fake repository** enjekte edilir; ViewModel davranışı `uiState`/`events` emisyonları üzerinden (Turbine ile) doğrulanır.

```kotlin
package com.salticusteam.vifi.fake

import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.model.{Model}
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

class Fake{Repository} : ArchiveRepository {

    // Configure per test. errorToThrow (when set) wins over the returned list, so the
    // repository throws the canonical ArchiveError just like the live impl.
    var {model}sToReturn: List<{Model}> = emptyList()
    var errorToThrow: Throwable? = null
    var get{Model}sCallCount: Int = 0

    override suspend fun get{Model}s(): List<{Model}> {
        get{Model}sCallCount++
        errorToThrow?.let { throw it }
        return {model}sToReturn
    }

    override fun observe{Model}s(): Flow<List<{Model}>> =
        errorToThrow?.let { error -> flow { throw error } } ?: flowOf({model}sToReturn)

    override suspend fun add{Model}(draft: {Model}Draft): {Model} =
        TODO("Fill in as the test requires")

    override suspend fun delete{Model}(id: String) = Unit
}
```

### Eşlik eden birim testi (JUnit + kotlinx-coroutines-test + Turbine)

```kotlin
package com.salticusteam.vifi.feature.<name>

import app.cash.turbine.test
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.{Model}
import com.salticusteam.vifi.fake.Fake{Repository}
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class {Name}ViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: Fake{Repository}

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = Fake{Repository}()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `load - basarili - items yayinlanir`() = runTest {
        repository.{model}sToReturn = listOf(/* sample {Model} */)
        val viewModel = {Name}ViewModel(repository)

        viewModel.uiState.test {
            assertEquals({Name}UiState(), awaitItem())
            viewModel.load()
            assertTrue(awaitItem().isLoading)
            val loaded = awaitItem()
            assertFalse(loaded.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load - hata - error userMessage yayinlanir`() = runTest {
        repository.errorToThrow = ArchiveError.Offline
        val viewModel = {Name}ViewModel(repository)

        viewModel.uiState.test {
            awaitItem()
            viewModel.load()
            awaitItem() // isLoading = true
            assertEquals(ArchiveError.Offline.userMessage, awaitItem().error)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

## Placeholder Replacements

| Yer tutucu | Açıklama | Örnek |
|-------------|-------------|---------|
| `{Name}` | ViewModel adı (PascalCase) | `Browse`, `ExamList`, `AddExam` |
| `<name>` | Küçük harf özellik paketi | `browse`, `examlist`, `addexam` |
| `{Model}` | Domain modeli | `University`, `Lesson`, `Exam` |
| `{Model}Draft` | Oluşturma/güncelleme girdi modeli | `ExamDraft` |
| `{Repository}` | Fake repository adı | `FakeArchiveRepository` |
| `{model}` | Alan adı (camelCase) | `university`, `exam` |

## ViFi-Android Patterns

### ✅ Gerekli
- Tek immutable `data class {Name}UiState` + `StateFlow` (public `asStateFlow()`)
- `@HiltViewModel` + constructor injection; repository **arayüzü** enjekte edilir
- `_uiState.update { it.copy(...) }` ile güncelleme
- Tek seferlik olaylar için `Channel` + `receiveAsFlow()`
- `viewModelScope`; IO/Firebase işleri repository içinde `Dispatchers.IO`
- Hatalar `ArchiveError`/`AuthError.userMessage` (Türkçe) ile UI'ya iletilir
- Domain modelleri plain `data class`; ham `DataSnapshot`/`DocumentSnapshot` sızmaz
- Var olan UiState'e alan eklerken `// Added in <versionName> — <amaç>.`

### ❌ Yasak
- Delegate protokolü / `weak var delegate` (StateFlow kullan)
- Output enum, `BaseViewModel`, ViewModelProtocol kalıtımı
- LiveData / RxJava
- `MutableStateFlow`'u public açmak; birden çok ayrı akış alanı
- ViewModel'de `Context` (`@ApplicationContext` hariç), `NavController`, `View`, Composable
- ViewModel'de sabit kullanıcı metni (UI'da `stringResource`)
- `GlobalScope` / `runBlocking`

## Usage Example

```kotlin
// In the Compose screen (equivalent of iOS ViewController + delegate setup)
@Composable
fun {Name}Screen(
    onNavigateToDetail: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: {Name}ViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is {Name}Event.NavigateToDetail -> onNavigateToDetail(event.id)
                is {Name}Event.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.{name}_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_navigate_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.error != null -> ErrorView(message = uiState.error!!, onRetry = viewModel::load)
                else -> LazyColumn(Modifier.testTag("{name}.list")) {
                    items(uiState.items, key = { it.id }) { item ->
                        // Row composable
                    }
                }
            }
        }
    }
}
```

---

**Ayrıca bakınız:**
- `.claude/skills/new-viewmodel/SKILL.md` — ViewModel oluşturma skill'i (adımlar + örnekler)
- `.claude/rules/viewmodel-rule.md` — ViewModel/UiState/StateFlow kuralları
- `.claude/rules/firebase-data.md` — Repository, DTO→domain map, callbackFlow
- `.claude/rules/testing.md` — runTest/Turbine/Fake test desenleri
- `.claude/skills/generate-mocks/` — Fake repository üretimi
