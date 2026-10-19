---
name: new-viewmodel
description: ViFi-Android hedef mimarisine (Kotlin + Jetpack Compose + MVVM) uygun bir ViewModel üretir — @HiltViewModel, tek immutable UiState data class + StateFlow, tek seferlik olaylar için Channel, viewModelScope. (Delegate/Output enum/base class YOK.)
---

> ⚠️ **UiState Kuralı (Zorunlu):** Her ViewModel durumunu TEK bir immutable `data class {Name}UiState` ile taşır. Dışarıya yalnızca `val uiState: StateFlow<{Name}UiState>` açılır (`private val _uiState = MutableStateFlow(...)` + `.asStateFlow()`). `MutableStateFlow`'u asla public yapma, durumu asla yerinde değiştirme — her güncelleme `_uiState.update { it.copy(...) }` ile olur. Birden çok `StateFlow<Boolean>`/`StateFlow<String?>` alanı yerine tek state kullan. Kanonik referans: `.claude/rules/viewmodel-rule.md`.

> ⚠️ **Yaşam Döngüsü & Context Kuralı (Zorunlu):** ViewModel; `Context` (yalnızca Hilt ile `@ApplicationContext` istisna), `View`, `NavController`, `Activity` veya Composable referansı TUTMAZ. Tüm asenkron iş `viewModelScope` içindedir (iptal otomatik; iOS `[weak self]`/retain-cycle derdinin Android karşılığı budur — manuel zayıf referans yok). IO/Firebase işleri `Dispatchers.IO`'da (tercihen repository içinde `withContext`). Kullanıcıya görünen metinler UI katmanında `stringResource` ile çözülür; ViewModel yalnızca `ArchiveError.userMessage` (Türkçe) iletir. Kanonik referans: `.claude/rules/architecture.md`.

# New ViewModel (ViFi-Android)

ViFi arşiv akışının (Üniversite → Fakülte → Bölüm → Ders → Sınav) bir ekranı için MVVM ViewModel oluşturur. iOS eSimIO tarafındaki delegate tabanlı ViewModel'in karşılığıdır; ancak ViFi-Android hedef mimarisi delegate/completion-handler değil, **StateFlow + Coroutines + Hilt + Repository** üzerine kuruludur.

> **Legacy not:** Bugün ekran mantığı doğrudan `UniversityFragment.java`/`CourseExamListActivity.java` gibi Activity/Fragment içinde `ValueEventListener` ile RTDB okuyup adapter'ı günceller — ayrı bir ViewModel katmanı yoktur. Bu skill yeni/ileriye dönük koddur; UiState + ViewModel + StateFlow deseninin kurulumu yol haritası `FM-13`, Hilt grafiği `FM-11`, Coroutines/Flow altyapısı `FM-05` kalemleridir. Mevcut Java ekranlarını bu skill'le yeniden yazmadan önce ilgili yol haritası kalemini teyit et.

## Usage

```
/new-viewmodel <Name> [--events <event1,event2,...>]
```

**Argümanlar**
- `Name` — "ViewModel" ekini içermeyen ad (ör. `Browse`, `ExamList`, `AddExam`, `Login`).
- `--events` — İsteğe bağlı, virgülle ayrılmış tek seferlik olay adları (ör. `navigateToExamDetail,showMessage,examUnlocked`). Bunlar `{Name}Event` sealed interface case'lerine dönüşür.

iOS'taki `--delegates` argümanının karşılığıdır: iOS'ta her sinyal bir delegate metoduydu; Android'de **sürekli durum** UiState alanı, **tek seferlik olay** ise Channel üzerinden akan `{Name}Event` olur.

## Steps

1. `Name` ve `--events` değerlerini ayrıştır.
2. Hedef paketi belirle: `com.salticusteam.vifi.feature.<name>/` (önerilen özellik paketlemesi). İçine `{Name}Screen` (Composable), `{Name}ViewModel`, `{Name}UiState` ve varsa `{Name}Event` gelir.
3. `{Name}UiState.kt` + `{Name}ViewModel.kt` üret (gerekirse olaylar aynı dosyada `{Name}Event`).
4. `--events` verildiyse her olay için `{Name}Event` sealed interface case'i ekle.
5. ViewModel'in ihtiyaç duyduğu veri kaynağını **repository arayüzü** olarak constructor'a enjekte et (ham Firebase tipi değil): `ArchiveRepository`, `AuthRepository` vb.
6. İsteğe bağlı: test/preview için `Fake{Repository}` oluştur (`.claude/skills/generate-mocks/`).

## What is created

- `{Name}UiState` — tek immutable `data class` (yükleme, veri, hata alanları).
- `{Name}Event` — (olay varsa) tek seferlik olaylar için `sealed interface`.
- `{Name}ViewModel` — `@HiltViewModel`, constructor injection, `viewModelScope`.
- İsteğe bağlı: `Fake{Repository}` (birim test + Compose `@Preview` için).

## ViFi-Android Patterns

### ✅ Gerekli desenler
- ✅ `@HiltViewModel class {Name}ViewModel @Inject constructor(...) : ViewModel()`
- ✅ Tek immutable `data class {Name}UiState` (varsayılan değerlerle)
- ✅ `private val _uiState = MutableStateFlow({Name}UiState())` + `val uiState = _uiState.asStateFlow()`
- ✅ Durum güncellemesi yalnızca `_uiState.update { it.copy(...) }`
- ✅ Tek seferlik olaylar (navigasyon, snackbar) için `Channel({Name}Event)` + `receiveAsFlow()`
- ✅ Tüm iş `viewModelScope.launch { ... }` içinde; IO repository'de `Dispatchers.IO`
- ✅ Veri erişimi **repository arayüzü** üzerinden (constructor injection); `DataSnapshot`/`DocumentSnapshot` ViewModel'e SIZMAZ
- ✅ Çift istek koruması: `if (_uiState.value.isLoading) return`
- ✅ Hatalar `ArchiveError`/`AuthError` (sealed) olarak yakalanır; UI'ya `error.userMessage` (Türkçe) iletilir
- ✅ Domain modelleri plain `data class` (`University`, `Lesson`, `Exam` ...)
- ✅ Var olan UiState'e alan eklerken `// Added in <versionName> — <amaç>.` yorumu (versionName = `app/build.gradle`)

### ❌ Yasak desenler
- ❌ Delegate protokolü / `weak var delegate` (iOS kalıntısı — StateFlow kullan)
- ❌ Output enum / `sealed class Output`
- ❌ `BaseViewModel` veya herhangi bir base class kalıtımı
- ❌ RxJava / LiveData (Flow/StateFlow kullan)
- ❌ `MutableStateFlow`'u public açmak veya birden çok ayrı akış alanı
- ❌ ViewModel içinde `Context` (`@ApplicationContext` hariç), `NavController`, `View`, Composable tutmak
- ❌ ViewModel'de `stringResource`/sabit kullanıcı metni (UI katmanında çöz)
- ❌ Ham `DataSnapshot`/`DocumentSnapshot`/`Task` tiplerinin ViewModel'e sızması
- ❌ `GlobalScope` veya `runBlocking` (her zaman `viewModelScope`)

## Generated File Structure

### Dosya: `com/salticusteam/vifi/feature/<name>/{Name}UiState.kt`

```kotlin
package com.salticusteam.vifi.feature.<name>

import com.salticusteam.vifi.core.model.{Model}

// Section marker: use `// region` or plain comments for sectioning in Kotlin.

// UI State — single immutable snapshot of the screen.
data class {Name}UiState(
    val isLoading: Boolean = false,
    val items: List<{Model}> = emptyList(),
    val error: String? = null,
)

// One-time events (navigation, snackbar) — never kept in UiState.
sealed interface {Name}Event {
    data class ShowMessage(val message: String) : {Name}Event
    data class NavigateToDetail(val id: String) : {Name}Event
}
```

### Dosya: `com/salticusteam/vifi/feature/<name>/{Name}ViewModel.kt`

```kotlin
package com.salticusteam.vifi.feature.<name>

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.model.ArchiveError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class {Name}ViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository,
) : ViewModel() {

    // State — exposed as read-only StateFlow.
    private val _uiState = MutableStateFlow({Name}UiState())
    val uiState: StateFlow<{Name}UiState> = _uiState.asStateFlow()

    // One-time events — consumed exactly once by the screen.
    private val _events = Channel<{Name}Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Public API
    fun load() {
        // Prevent duplicate in-flight requests (equivalent of iOS `guard !isLoading`).
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Repository switches to Dispatchers.IO internally and returns domain models.
                val items = archiveRepository.get{Model}s()
                _uiState.update { it.copy(isLoading = false, items = items) }
            } catch (error: ArchiveError) {
                // No reachability check in the ViewModel: the repository throws
                // ArchiveError.Offline with a Turkish userMessage when offline.
                _uiState.update { it.copy(isLoading = false, error = error.userMessage) }
            }
        }
    }

    fun onItemClick(id: String) {
        viewModelScope.launch { _events.send({Name}Event.NavigateToDetail(id)) }
    }
}
```

**Yer tutucular:**
- `{Name}` → ViewModel adı (ör. `Browse`, `ExamList`)
- `{Model}` → domain modeli (ör. `University`, `Lesson`, `Exam`)
- `<name>` → küçük harf özellik paketi (ör. `browse`, `examlist`)

> Not: Compose, listeyi doğrudan `uiState.items` üzerinden `LazyColumn`'da dolaşır; iOS'taki `numberOfItems()`/`item(at:)` gibi `UITableView` dataSource yardımcılarının Android karşılığı YOKTUR — ham indeksleme yerine immutable listeyi `key` ile render et.

## Common UiState Fields and Events

iOS'ta her sinyal bir delegate metoduydu (`didSuccessLoad`, `willStartLoading`...). Android'de bunları ikiye ayır: **sürekli durum** → UiState alanı, **tek seferlik olay** → `{Name}Event`.

### Standart durum alanları (UiState)

```kotlin
data class ExamListUiState(
    val isLoading: Boolean = false,        // equivalent of willStartLoading / didFinishLoading
    val isLoadingMore: Boolean = false,    // pagination indicator
    val exams: List<Exam> = emptyList(),   // didSuccessLoad data
    val canLoadMore: Boolean = true,       // pagination gate
    val selectedExamId: String? = null,    // equivalent of didUpdateSelection
    val error: String? = null,             // equivalent of didFailure(message:)
)
```

### Standart olaylar (Event)

```kotlin
sealed interface ExamListEvent {
    data class ShowMessage(val message: String) : ExamListEvent       // didFailure popup
    data class NavigateToExamDetail(val examId: String) : ExamListEvent
    data object ExamCreated : ExamListEvent                           // didSuccessCreate
    data object ExamDeleted : ExamListEvent                           // didSuccessDelete
}
```

### Domaine özgü olaylar

İhtiyaç duyulan ekrana özel case ekle (iOS'taki `PaymentViewModelDelegate` özel metotlarının karşılığı):

```kotlin
sealed interface ExamListEvent {
    data class NavigateToExamDetail(val examId: String) : ExamListEvent
    // ViFi-specific: locked exam → unlock-with-coin flow (T4/T5/T10)
    data class ShowUnlockSheet(val examId: String, val coinCost: Int) : ExamListEvent
    data object ExamUnlocked : ExamListEvent
}
```

## Common Patterns

### 1. Temel Veri Yükleme (tek seferlik)

```kotlin
@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    fun loadUniversities() {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val universities = archiveRepository.getUniversities()
                _uiState.update { it.copy(isLoading = false, universities = universities) }
            } catch (error: ArchiveError) {
                _uiState.update { it.copy(isLoading = false, error = error.userMessage) }
            }
        }
    }
}

data class BrowseUiState(
    val isLoading: Boolean = false,
    val universities: List<University> = emptyList(),
    val error: String? = null,
)
```

> **Legacy not:** `BrowseViewModel` yol haritası `ARCH-13` kalemidir (Loadable durum + load/refresh/remove + Türkçe arama katlaması). Bugünkü `UniversityFragment.java`'nın yerini alır.

### 2. Gerçek Zamanlı Dinleme (RTDB/Firestore listener → callbackFlow)

Canlı güncelleme gereken ekranlarda tek seferlik `get()` yerine repository'nin `Flow`'unu dinle. Firebase listener'ı `callbackFlow` içinde kurulur ve `awaitClose` ile kaldırılır (listener sızıntısına karşı — iOS'taki observer kaldırma mantığının karşılığı).

```kotlin
// Repository (core/data/repository): wraps the Firebase listener in a Flow.
override fun observeUniversities(): Flow<List<University>> = callbackFlow {
    val ref = database.getReference("Universitiess")
    val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            // DTO -> domain map: the raw DataSnapshot never leaks out.
            trySend(snapshot.children.mapNotNull { it.toUniversityOrNull() })
        }
        override fun onCancelled(error: DatabaseError) {
            close(error.toArchiveError())
        }
    }
    ref.addValueEventListener(listener)
    awaitClose { ref.removeEventListener(listener) }
}.flowOn(Dispatchers.IO)

// ViewModel: collect the Flow in viewModelScope.
class BrowseViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    init { observeUniversities() }

    private fun observeUniversities() {
        archiveRepository.observeUniversities()
            .onStart { _uiState.update { it.copy(isLoading = true, error = null) } }
            .onEach { list -> _uiState.update { it.copy(isLoading = false, universities = list) } }
            .catch { e ->
                val message = (e as? ArchiveError)?.userMessage ?: ArchiveError.Unknown.userMessage
                _uiState.update { it.copy(isLoading = false, error = message) }
            }
            .launchIn(viewModelScope)
    }
}
```

### 3. Sayfalama (Firestore cursor)

```kotlin
@HiltViewModel
class ExamListViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExamListUiState())
    val uiState: StateFlow<ExamListUiState> = _uiState.asStateFlow()

    private var lastExamId: String? = null

    fun load(lessonId: String) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val page = archiveRepository.getExams(lessonId = lessonId, pageSize = PAGE_SIZE, after = null)
                lastExamId = page.items.lastOrNull()?.id
                _uiState.update {
                    it.copy(isLoading = false, exams = page.items, canLoadMore = page.hasMore)
                }
            } catch (error: ArchiveError) {
                _uiState.update { it.copy(isLoading = false, error = error.userMessage) }
            }
        }
    }

    fun loadMore(lessonId: String) {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.canLoadMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val page = archiveRepository.getExams(lessonId, PAGE_SIZE, after = lastExamId)
                lastExamId = page.items.lastOrNull()?.id ?: lastExamId
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        exams = it.exams + page.items,
                        canLoadMore = page.hasMore,
                    )
                }
            } catch (_: ArchiveError) {
                // Pagination fails silently (equivalent of iOS "silently fail").
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    private companion object { const val PAGE_SIZE = 20 }
}
```

### 4. CRUD İşlemleri (sınav ekle/sil)

```kotlin
@HiltViewModel
class AddExamViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddExamUiState())
    val uiState: StateFlow<AddExamUiState> = _uiState.asStateFlow()

    private val _events = Channel<AddExamEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun submit(draft: ExamDraft) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                archiveRepository.addExam(draft)
                _uiState.update { it.copy(isSaving = false) }
                _events.send(AddExamEvent.ExamCreated)
            } catch (error: ArchiveError) {
                _uiState.update { it.copy(isSaving = false, error = error.userMessage) }
            }
        }
    }

    fun deleteExam(examId: String) {
        viewModelScope.launch {
            try {
                archiveRepository.deleteExam(examId)
                _events.send(AddExamEvent.ExamDeleted)
            } catch (error: ArchiveError) {
                _events.send(AddExamEvent.ShowMessage(error.userMessage))
            }
        }
    }
}

data class AddExamUiState(
    val isSaving: Boolean = false,
    val error: String? = null,
)

sealed interface AddExamEvent {
    data object ExamCreated : AddExamEvent
    data object ExamDeleted : AddExamEvent
    data class ShowMessage(val message: String) : AddExamEvent
}
```

> **Legacy not:** `AddExamViewModel` yol haritası `AE-03` kalemidir ve bugünkü `AddLessonActivity.java` + `AddPdfLessonActivity.java`'nın yerini alır (Compose + ViewModel, TopAppBar Vazgeç/Gönder).

### 5. Yerel Oturum / DataStore Entegrasyonu

iOS'taki `LocalStore.userModel` karşılığı: oturum/kullanıcı durumu bir repository (DataStore veya `FirebaseAuth` sarmalı) arkasında. Context ViewModel'e sızmaz.

```kotlin
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _events = Channel<ProfileEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        // Observe session state as a Flow (AuthStateListener -> StateFlow, AUTH-08).
        authRepository.currentUser
            .onEach { user -> _uiState.update { it.copy(user = user) } }
            .launchIn(viewModelScope)
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _events.send(ProfileEvent.SignedOut)
        }
    }
}

data class ProfileUiState(val user: UserProfile? = null)
sealed interface ProfileEvent { data object SignedOut : ProfileEvent }
```

### 6. Girdi Doğrulama

```kotlin
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onPhoneChange(value: String) {
        _uiState.update { it.copy(phone = value, error = null) }
    }

    fun submit() {
        val phone = _uiState.value.phone.trim()
        // Validation errors are also shown uniformly via ArchiveError/AuthError.userMessage (Turkish).
        if (phone.isBlank()) {
            _uiState.update { it.copy(error = AuthError.EmptyPhone.userMessage) }
            return
        }
        if (!phone.startsWith("+90") || phone.length < 13) {
            _uiState.update { it.copy(error = AuthError.InvalidPhone.userMessage) }
            return
        }
        requestCode(phone)
    }

    private fun requestCode(phone: String) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                authRepository.sendVerificationCode(phone)
                _uiState.update { it.copy(isLoading = false, codeSent = true) }
            } catch (error: AuthError) {
                _uiState.update { it.copy(isLoading = false, error = error.userMessage) }
            }
        }
    }
}

data class LoginUiState(
    val phone: String = "",
    val isLoading: Boolean = false,
    val codeSent: Boolean = false,
    val error: String? = null,
)
```

> **Legacy not:** `LoginViewModel` yol haritası `AUTH-04` kalemidir (telefon/kod iki adımı, 60 sn resend cooldown). Bugünkü `LoginActivity.java`'nın e-posta/şifre akışının yerini `+90` Phone Auth alır.

## Compose Screen Integration

iOS'taki `ViewController` + delegate kurulumunun karşılığı: Composable, `uiState`'i `collectAsStateWithLifecycle` ile dinler; tek seferlik olayları `LaunchedEffect` içinde toplar. Elle/metinli geri butonu yok — `TopAppBar` + `navigationIcon` (ikon-only) + `navController.popBackStack()`.

```kotlin
@Composable
fun BrowseScreen(
    onUniversityClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // One-time events (navigation, snackbar).
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is BrowseEvent.NavigateToDetail -> onUniversityClick(event.id)
                is BrowseEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.browse_title)) },
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
                uiState.error != null -> ErrorView(message = uiState.error!!, onRetry = viewModel::loadUniversities)
                else -> LazyColumn(Modifier.testTag("browse.list")) {
                    items(uiState.universities, key = { it.id }) { university ->
                        UniversityRow(
                            university = university,
                            onClick = { viewModel.onItemClick(university.id) },
                        )
                    }
                }
            }
        }
    }
}
```

## Testing Support

iOS'taki "Mock delegate + çağrıldı-mı bayrakları" yerine Android'de: **Fake repository + StateFlow'u Turbine ile doğrulama**. Çağrı sayacı tutmak yerine state emisyonlarını assert et.

### Fake Repository

**Dosya**: `src/test/.../fake/Fake{Repository}.kt`

```kotlin
class FakeArchiveRepository : ArchiveRepository {

    // Configure per test. errorToThrow (when set) wins over the returned list, so the
    // repository throws the canonical ArchiveError just like the live impl.
    var universitiesToReturn: List<University> = emptyList()
    var errorToThrow: Throwable? = null
    var getUniversitiesCallCount: Int = 0

    override suspend fun getUniversities(): List<University> {
        getUniversitiesCallCount++
        errorToThrow?.let { throw it }
        return universitiesToReturn
    }

    override fun observeUniversities(): Flow<List<University>> =
        errorToThrow?.let { error -> flow { throw error } } ?: flowOf(universitiesToReturn)

    // Fill in other methods as the test requires / leave a TODO.
}
```

### Birim Testi (JUnit + kotlinx-coroutines-test + Turbine)

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class BrowseViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeArchiveRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeArchiveRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `load - basarili - state universiteleri yayar`() = runTest {
        repository.universitiesToReturn =
            listOf(University(id = "1", name = "Boğaziçi Üniversitesi"))
        val viewModel = BrowseViewModel(repository)

        viewModel.uiState.test {
            assertEquals(BrowseUiState(), awaitItem())      // initial state
            viewModel.loadUniversities()
            assertTrue(awaitItem().isLoading)               // loading
            val loaded = awaitItem()                        // result
            assertFalse(loaded.isLoading)
            assertEquals(1, loaded.universities.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load - baglanti yok - state hata mesaji yayar`() = runTest {
        repository.errorToThrow = ArchiveError.Offline
        val viewModel = BrowseViewModel(repository)

        viewModel.uiState.test {
            awaitItem()                                     // initial state
            viewModel.loadUniversities()
            awaitItem()                                     // isLoading = true
            val errorState = awaitItem()
            assertFalse(errorState.isLoading)
            assertEquals(ArchiveError.Offline.userMessage, errorState.error)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

> **Legacy not:** Test altyapısı (runTest + Turbine + Compose UI test) yol haritası `P20`/`FM-18` kalemleridir; `.claude/skills/generate-unit-tests/` ve `.claude/skills/generate-ui-tests/` ile üretilir.

## Example: Complete ViewModel

```
/new-viewmodel ExamList --events navigateToExamDetail,showUnlockSheet,examUnlocked
```

**Üretilen**: `com/salticusteam/vifi/feature/examlist/ExamListViewModel.kt`

```kotlin
package com.salticusteam.vifi.feature.examlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.model.Exam
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// UI State
data class ExamListUiState(
    val isLoading: Boolean = false,
    val exams: List<Exam> = emptyList(),
    val error: String? = null,
)

// One-time events
sealed interface ExamListEvent {
    data class NavigateToExamDetail(val examId: String) : ExamListEvent
    data class ShowUnlockSheet(val examId: String, val coinCost: Int) : ExamListEvent
    data object ExamUnlocked : ExamListEvent
}

@HiltViewModel
class ExamListViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExamListUiState())
    val uiState: StateFlow<ExamListUiState> = _uiState.asStateFlow()

    private val _events = Channel<ExamListEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun load(lessonId: String) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val exams = archiveRepository.getExams(lessonId)
                _uiState.update { it.copy(isLoading = false, exams = exams) }
            } catch (error: ArchiveError) {
                _uiState.update { it.copy(isLoading = false, error = error.userMessage) }
            }
        }
    }

    fun onExamClick(exam: Exam) {
        viewModelScope.launch {
            if (exam.isLocked) {
                _events.send(ExamListEvent.ShowUnlockSheet(exam.id, exam.coinCost))
            } else {
                _events.send(ExamListEvent.NavigateToExamDetail(exam.id))
            }
        }
    }

    fun onUnlockSuccess(examId: String) {
        _uiState.update { state ->
            state.copy(exams = state.exams.map { if (it.id == examId) it.copy(isLocked = false) else it })
        }
        viewModelScope.launch { _events.send(ExamListEvent.ExamUnlocked) }
    }
}
```

> **Legacy not:** Kilit durumu + coin ile açma akışı yol haritası `T10` kalemidir; bugünkü `CourseExamListActivity.java`'nın yerini alır.

## Checklist

ViewModel üretildikten sonra doğrula:

- [ ] `@HiltViewModel` + `@Inject constructor` kullanıldı
- [ ] Durum TEK immutable `data class {Name}UiState` ile taşınıyor
- [ ] Dışarıya yalnızca `val uiState: StateFlow<...>` açıldı (`MutableStateFlow` private)
- [ ] Güncellemeler `_uiState.update { it.copy(...) }` ile
- [ ] Tek seferlik olaylar için `Channel` + `receiveAsFlow()` (UiState'te olay yok)
- [ ] Tüm iş `viewModelScope` içinde; IO repository'de `Dispatchers.IO`
- [ ] Veri erişimi repository arayüzü üzerinden; ham `DataSnapshot`/`DocumentSnapshot` sızmıyor
- [ ] Çift istek koruması (`if (_uiState.value.isLoading) return`)
- [ ] Hatalar `ArchiveError`/`AuthError` olarak yakalanıp `error.userMessage` (Türkçe) iletiliyor
- [ ] ViewModel'de `Context` (`@ApplicationContext` hariç), `NavController`, `View`, Composable yok
- [ ] ViewModel'de sabit kullanıcı metni yok (UI'da `stringResource`)
- [ ] NO delegate / NO Output enum / NO base class / NO LiveData/RxJava
- [ ] Var olan UiState'e alan eklendiyse `// Added in <versionName> — <amaç>.` yorumu var
- [ ] Test için `Fake{Repository}` ve Turbine'li birim testi hazır

---

**Ayrıca bakınız:**
- `.claude/rules/viewmodel-rule.md` — ViewModel/UiState/StateFlow kuralları
- `.claude/rules/architecture.md` — ViFi-Android hedef mimarisi (MVVM + Hilt + Repository)
- `.claude/rules/firebase-data.md` — Repository, DTO→domain map, callbackFlow desenleri
- `.claude/rules/ui-components.md` — Compose/Material3 ekran bileşenleri
- `.claude/rules/localization.md` — strings.xml, `stringResource`, hata mesajları
- `.claude/rules/testing.md` — runTest/Turbine/Fake test desenleri
- `.claude/skills/new-screen/` — ViewModel'i tüketen Compose ekranı oluşturma
- `.claude/skills/new-feature/` — tam özellik (ekran + ViewModel + repository) oluşturma
- `.claude/skills/generate-mocks/` — Fake repository üretimi
- `.claude/skills/new-viewmodel/templates/viewmodel.md` — kopyala-yapıştır ViewModel şablonları
