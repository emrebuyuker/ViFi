# ViFi Android - ViewModel Kuralları

> **Uygulanır:** `*ViewModel*.kt` dosyaları
> **Globs:** `["**/*ViewModel*.kt", "**/feature/**/*ViewModel.kt"]`

> **Legacy not:** Bugünkü v2.5 kodu Java + Activity/Fragment üzerinde, ayrı bir ViewModel katmanı yok; iş mantığı doğrudan `Activity`/`Fragment` içinde. Bu kural **yeni/ileriye dönük** Kotlin + Compose kodu içindir ve hedef mimariyi (MVVM + Hilt + StateFlow) tanımlar. Göçün yol haritasındaki yeri: bkz. repo kökü `ANDROID-PARITY-ROADMAP.md` → UI mimarisi (Compose) ve `ANDROID-PARITY-DETAIL.md` → ekran bazlı ViewModel kalemleri. Mevcut Fragment'lar birebir yeni Composable ekran + ViewModel ikilisine taşınır; yeni ekran asla Fragment olarak yazılmaz.

> ⚠️ **Section-comment kuralı (zorunlu):** Kotlin'de iOS'un `// MARK:` zorunluluğu yoktur; onun yerine her ViewModel bölümünü `// region <Section>` / `// endregion` ya da sade `// --- <Section> ---` ile ayır (ör. `State`, `Events`, `Public API`, `Private helpers`). Bölüm yorumu ile sonraki bildirim arasına **boş satır koyma**; bölüm *öncesinde* boş satır serbest. Kanonik referans: `.claude/rules/architecture.md` (Code Style → Section Comments).

> ⚠️ **Coroutine/await kuralı (zorunlu):** Firebase `Task<T>` doğrudan blocking beklenmez; `kotlinx-coroutines-play-services` `.await()` ile `suspend` içinde beklenir ve dosya `import kotlinx.coroutines.tasks.await` ister. RTDB/Firestore **dinleyicileri** (`ValueEventListener` / `SnapshotListener`) `callbackFlow { ... awaitClose { ref.removeEventListener(l) } }` ile sarmalanır — `awaitClose` olmadan listener sızar. ViewModel'e **ham Firebase tipi** (`DataSnapshot`/`DocumentSnapshot`) **ulaşmaz**: repository DTO→domain `data class` map eder. Bu çeviri `callbackFlow`'un dışında kalırsa UI ham snapshot'a bağımlı olur ve test edilemez. Kanonik referans: `.claude/rules/firebase-data.md` (Repository + callbackFlow kuralı).

## ViewModel Deseni

ViewModel iş mantığını tutar ve UI (Composable) ile **tek yönlü veri akışı (UDF)** üzerinden konuşur: aşağı doğru tek bir **immutable state**, yukarı doğru **callback (lambda)**. iOS'taki delegate protokolü + tekil callback metotları Android'de **yoktur**.

**❌ Delegate protokolü yok** — Bu projede UI ile iletişim `StateFlow<XUiState>` (sürekli durum) + `Channel`/`SharedFlow` (tek seferlik olay) ile yapılır. Composable state'i okur, kullanıcı etkileşimini ViewModel'in public fonksiyonuna (lambda olarak geçirilen) iletir.

### Zorunlu Yapı

```kotlin
// region State
// 1. UI state is collected into a SINGLE immutable data class.
data class ExamListUiState(
    val exams: List<Exam> = emptyList(),
    val selectedExamIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: String? = null
)
// endregion

// region Events
// 2. One-off events (navigate, snackbar) via Channel; not kept in state.
sealed interface ExamListEvent {
    data class OpenExam(val examId: String) : ExamListEvent
    data class ShowMessage(val message: String) : ExamListEvent
}
// endregion

// 3. ViewModel: androidx.lifecycle.ViewModel + Hilt constructor injection (NO base class).
@HiltViewModel
class ExamListViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // region State
    private val lessonId: String = checkNotNull(savedStateHandle["lessonId"])

    private val _uiState = MutableStateFlow(ExamListUiState())
    val uiState: StateFlow<ExamListUiState> = _uiState.asStateFlow()
    // endregion

    // region Events
    private val _events = Channel<ExamListEvent>(Channel.BUFFERED)
    val events: Flow<ExamListEvent> = _events.receiveAsFlow()
    // endregion

    // region Public API
    init {
        loadExams()
    }

    fun loadExams() {
        if (_uiState.value.isLoading) return  // Prevent duplicate requests

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val exams = withContext(Dispatchers.IO) {
                    archiveRepository.getExams(lessonId, page = 1)
                }
                _uiState.update {
                    it.copy(isLoading = false, exams = exams, canLoadMore = exams.isNotEmpty())
                }
            } catch (e: ArchiveError) {
                _uiState.update { it.copy(isLoading = false, error = e.userMessage) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = ArchiveError.Unknown().userMessage) }
            }
        }
    }
    // endregion
}
```

### İletişim Deseni

```
Composable → ViewModel: public fonksiyon çağrısı (Composable'a lambda olarak verilir)
ViewModel → Composable: StateFlow<UiState> (sürekli durum) + Channel/SharedFlow (tek seferlik olay)
```

iOS karşılığı: delegate'in `didSuccessLoad()`/`didFailure(message:)` çağrıları → `_uiState.update { ... }`; delegate'in `didNavigate`/`showPopup` gibi tek seferlik çağrıları → `_events.send(...)`.

### Tam Örnek

```kotlin
// region State
data class ExamListUiState(
    val exams: List<Exam> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: String? = null
)
// endregion

// region Events
sealed interface ExamListEvent {
    data class OpenExam(val examId: String) : ExamListEvent
    data class ShowMessage(val message: String) : ExamListEvent
}
// endregion

@HiltViewModel
class ExamListViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // region State
    private val lessonId: String = checkNotNull(savedStateHandle["lessonId"])

    private val _uiState = MutableStateFlow(ExamListUiState())
    val uiState: StateFlow<ExamListUiState> = _uiState.asStateFlow()

    private val _events = Channel<ExamListEvent>(Channel.BUFFERED)
    val events: Flow<ExamListEvent> = _events.receiveAsFlow()

    private var currentPage = 1
    // endregion

    // region Public API
    init {
        loadExams()
    }

    fun loadExams() {
        if (_uiState.value.isLoading) return

        currentPage = 1
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val exams = withContext(Dispatchers.IO) {
                    archiveRepository.getExams(lessonId, page = currentPage)
                }
                _uiState.update {
                    it.copy(isLoading = false, exams = exams, canLoadMore = exams.isNotEmpty())
                }
            } catch (e: ArchiveError) {
                _uiState.update { it.copy(isLoading = false, error = e.userMessage) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = ArchiveError.Unknown().userMessage) }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.canLoadMore) return

        _uiState.update { it.copy(isLoadingMore = true) }
        val nextPage = currentPage + 1

        viewModelScope.launch {
            try {
                val newItems = withContext(Dispatchers.IO) {
                    archiveRepository.getExams(lessonId, page = nextPage)
                }
                currentPage = nextPage
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        exams = it.exams + newItems,
                        canLoadMore = newItems.isNotEmpty()
                    )
                }
            } catch (e: Exception) {
                // Fail silently in pagination: keep the list, only close the spinner.
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun onExamClick(examId: String) {
        viewModelScope.launch { _events.send(ExamListEvent.OpenExam(examId)) }
    }

    /** Called after showing the UI error in a Snackbar; one-off consumption. */
    fun onErrorShown() {
        _uiState.update { it.copy(error = null) }
    }
    // endregion
}
```

### Compose Ekran Entegrasyonu

iOS'taki `ViewController` + delegate kurulumunun karşılığı: **state'li** bir ekran Composable'ı (ViewModel'i `hiltViewModel()` ile alır, state toplar, event'leri dinler) + **state'siz (stateless)** bir içerik Composable'ı (sadece `uiState` + lambda alır — preview ve test edilebilir). Delegate yerine **state aşağı, callback yukarı**.

```kotlin
// region Stateful screen
@Composable
fun ExamListScreen(
    onOpenExam: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ExamListViewModel = hiltViewModel()
) {
    // Lifecycle-aware collection: the flow stops in the background (no leak).
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // One-off events: listen to the events Channel.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ExamListEvent.OpenExam -> onOpenExam(event.examId)
                is ExamListEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    // Bridge the error in state to the Snackbar, then consume it.
    LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onErrorShown()
        }
    }

    ExamListContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onExamClick = viewModel::onExamClick,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::loadExams,
        onBack = onBack
    )
}
// endregion

// region Stateless content (preview + testable)
@Composable
fun ExamListContent(
    uiState: ExamListUiState,
    snackbarHostState: SnackbarHostState,
    onExamClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exam_list_title)) },
                // iOS safePop equivalent: icon-only back button + popBackStack (NO text back).
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator(Modifier.padding(padding))
            uiState.exams.isEmpty() -> EmptyState(
                message = stringResource(R.string.exam_list_empty),
                onRetry = onRetry,
                modifier = Modifier.padding(padding)
            )
            else -> ExamList(
                exams = uiState.exams,
                isLoadingMore = uiState.isLoadingMore,
                onExamClick = onExamClick,
                onLoadMore = onLoadMore,
                modifier = Modifier
                    .padding(padding)
                    .testTag("examList.list")
            )
        }
    }
}
// endregion
```

> **Legacy not:** Bugün aynı iş `ExamListActivity`/`ExamListFragment` içinde `findViewById` + `notifyDataSetChanged()` ile yapılıyor; göçte bu ikili yukarıdaki `ExamListScreen` + `ExamListContent` + `ExamListViewModel` üçlüsüne taşınır. Liste öğesi Composable'ı için bkz. `.claude/skills/new-list-item/SKILL.md`.

### Durum Yönetimi Desenleri

#### Loading durumu

iOS'ta `willStartLoading()`/`didFinishLoading()` delegate çağrıları, burada `UiState.isLoading` alanının `copy` ile değiştirilmesidir. **Kritik:** bir `launch` içinde `isLoading = true` set edildiyse, erken `return`/hata dalında da mutlaka `false`'a çekilmeli — aksi halde spinner sonsuza takılır (iOS'taki "popup gösterilmeden loading askıda kalır" sorununun karşılığı). `try/catch`'in **her** dalı ya da `finally` bunu garanti eder.

```kotlin
fun fetchData() {
    if (_uiState.value.isLoading) return  // duplicate guard

    _uiState.update { it.copy(isLoading = true, error = null) }

    viewModelScope.launch {
        try {
            val data = withContext(Dispatchers.IO) { archiveRepository.getExams(lessonId, page = 1) }
            _uiState.update { it.copy(isLoading = false, exams = data) }
        } catch (e: ArchiveError) {
            // The error branch also CLOSES isLoading — otherwise the spinner hangs.
            _uiState.update { it.copy(isLoading = false, error = e.userMessage) }
        }
    }
}
```

#### Pagination durumu

```kotlin
private var currentPage = 1

fun loadMore() {
    val state = _uiState.value
    if (state.isLoadingMore || !state.canLoadMore) return

    _uiState.update { it.copy(isLoadingMore = true) }
    val nextPage = currentPage + 1

    viewModelScope.launch {
        try {
            val newItems = withContext(Dispatchers.IO) {
                archiveRepository.getExams(lessonId, page = nextPage)
            }
            currentPage = nextPage
            _uiState.update {
                it.copy(
                    isLoadingMore = false,
                    exams = it.exams + newItems,
                    canLoadMore = newItems.isNotEmpty()
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoadingMore = false) }  // fail silently
        }
    }
}
```

#### Selection durumu

iOS'un `Set<IndexPath>` + `didUpdateSelection()` deseni, burada state içindeki immutable `Set<String>`'tir; UI selection'ı state'ten okur, ViewModel yeni `Set` üretip `copy` eder.

```kotlin
fun toggleSelection(examId: String) {
    _uiState.update { state ->
        val selected = state.selectedExamIds.toMutableSet()
        if (!selected.add(examId)) selected.remove(examId)  // remove if present, otherwise add
        state.copy(selectedExamIds = selected)
    }
}

// isSelected on the UI side: uiState.selectedExamIds.contains(examId)
```

### Repository İstek Deseni

iOS'un `NetworkManager.shared.request(service:)` + delegate deseni, burada **repository interface'i** üzerinden `suspend` çağrısıdır. ViewModel ham Firebase tipi görmez; repository DTO→domain map eder ve hata halinde `ArchiveError` fırlatır.

```kotlin
// core/data/repository/ArchiveRepository.kt
interface ArchiveRepository {
    /** @throws ArchiveError domain error (Offline/Empty/Unknown). */
    suspend fun getExams(lessonId: String, page: Int): List<Exam>
    suspend fun submitExam(request: ExamSubmission): Exam
    fun observeExams(lessonId: String): Flow<List<Exam>>  // live listener
}

// core/model/Exam.kt — plain domain model, no Firebase types.
data class Exam(
    val id: String,
    val title: String,
    val courseId: String,
    val kind: String,              // "pdf" | "image"
    val year: Int? = null,
    val isLocked: Boolean = false
)

// core/model/ArchiveError.kt — carries Turkish userMessage; UI shows error.userMessage.
sealed class ArchiveError(open val userMessage: String) : Exception(userMessage) {

    data object Offline : ArchiveError("İnternet bağlantınızı kontrol edin.")
    data object Empty : ArchiveError("Bu ders için henüz sınav eklenmemiş.")
    data class Unknown(
        override val userMessage: String = "Bir hata oluştu, lütfen tekrar deneyin."
    ) : ArchiveError(userMessage)
}
```

ViewModel tarafında eylem (create/submit) — iOS'un `didSuccessAction`/`didFailure`/`didFailStopAnimation` üçlüsünün karşılığı: başarı → event, hata → state.error, **her iki dalda da loading kapatılır**.

```kotlin
fun submitExam(request: ExamSubmission) {
    _uiState.update { it.copy(isLoading = true, error = null) }

    viewModelScope.launch {
        try {
            val saved = withContext(Dispatchers.IO) { archiveRepository.submitExam(request) }
            _uiState.update { it.copy(isLoading = false) }
            _events.send(ExamListEvent.ShowMessage("Sınav gönderildi, onay bekliyor."))
        } catch (e: ArchiveError) {
            // Error: loading is CLOSED + userMessage is written to state (no hanging).
            _uiState.update { it.copy(isLoading = false, error = e.userMessage) }
        }
    }
}
```

**Caller (Compose) tarafında** error, state üzerinden `LaunchedEffect(uiState.error)` ile Snackbar'a köprülenir ve `onErrorShown()` ile tüketilir (yukarıdaki `ExamListScreen`'e bakınız). Delegate callback'i ya da manuel "loading'i durdur" çağrısı gerekmez — loading zaten state'in parçası.

#### Canlı dinleyici (RTDB/Firestore) — `callbackFlow`

iOS'un Combine/listener aboneliğinin karşılığı; listener `awaitClose`'da **mutlaka** kaldırılır (retain cycle / listener sızıntısı karşılığı). Bu kod repository'de yaşar, ViewModel'de değil.

```kotlin
// Inside FirebaseArchiveRepository:
override fun observeExams(lessonId: String): Flow<List<Exam>> = callbackFlow {
    val ref = firestore.collection("exams").whereEqualTo("lessonId", lessonId)
    val registration = ref.addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(ArchiveError.Unknown())
            return@addSnapshotListener
        }
        // DTO→domain map; raw DocumentSnapshot does NOT leak out.
        val exams = snapshot?.documents?.mapNotNull { it.toExamOrNull() }.orEmpty()
        trySend(exams)
    }
    awaitClose { registration.remove() }  // MANDATORY: remove the listener when the flow is cancelled.
}
```

ViewModel'de bu akış `stateIn` ile state'e bağlanır (coroutine `viewModelScope`'ta, sızıntı yok):

```kotlin
val uiState: StateFlow<ExamListUiState> =
    archiveRepository.observeExams(lessonId)
        .map { exams -> ExamListUiState(exams = exams, isLoading = false) }
        .catch { emit(ExamListUiState(error = (it as? ArchiveError)?.userMessage ?: ArchiveError.Unknown().userMessage)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ExamListUiState(isLoading = true)
        )
```

### Yerel Kalıcılık Entegrasyonu

iOS'un `LocalStore`/`EsimSingleton` kullanımının karşılığı: yerel veri de bir repository interface'i arkasındadır (`UserRepository` → DataStore/Room impl). ViewModel `suspend` fonksiyonları çağırır, singleton'a doğrudan dokunmaz.

```kotlin
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    data class ProfileUiState(
        val user: User? = null,
        val isLoading: Boolean = false,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun loadUser() {
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()  // reads from DataStore
            _uiState.update {
                if (user != null) it.copy(user = user, error = null)
                else it.copy(error = "Kullanıcı bulunamadı.")
            }
        }
    }

    fun updateDisplayName(name: String) {
        val current = _uiState.value.user ?: return
        viewModelScope.launch {
            try {
                val updated = current.copy(displayName = name)
                userRepository.save(updated)  // writes to DataStore
                _uiState.update { it.copy(user = updated) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Kaydedilemedi, tekrar deneyin.") }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            userRepository.clear()
            _uiState.update { it.copy(user = null) }
            _events.send(ProfileEvent.NavigateToLogin)
        }
    }

    private val _events = Channel<ProfileEvent>(Channel.BUFFERED)
    val events: Flow<ProfileEvent> = _events.receiveAsFlow()
}

sealed interface ProfileEvent {
    data object NavigateToLogin : ProfileEvent
}
```

### Veri Erişim Yardımcıları

iOS'ta `numberOfItems()`/`item(at:)` gibi `IndexPath` tabanlı yardımcılar gerekiyordu çünkü `UICollectionViewDataSource` indeksle çalışır. Compose'da `LazyColumn` doğrudan listeyi aldığı için bu yardımcılara **gerek yoktur** — UI `uiState.exams`'ı `items(...)` ile dolaşır. İhtiyaç olursa türetilmiş değerler state'te saf dönüşümle verilir:

```kotlin
// Derived counters/search via state on the UI side; don't keep an index API in the ViewModel.
val selectedCount: Int get() = _uiState.value.selectedExamIds.size

fun examById(id: String): Exam? =
    _uiState.value.exams.firstOrNull { it.id == id }
```

### Doğrulama (Validation)

```kotlin
fun submit(title: String, year: String) {
    val validationError = validate(title, year)
    if (validationError != null) {
        _uiState.update { it.copy(error = validationError) }
        return
    }

    val request = ExamSubmission(lessonId = lessonId, title = title, year = year.toInt())
    submitExam(request)
}

private fun validate(title: String, year: String): String? = when {
    title.isBlank() -> "Başlık boş olamaz."
    year.toIntOrNull() == null -> "Yıl geçerli bir sayı olmalı."
    else -> null
}
```

### Kurallar

1. ❌ **ASLA** bir `BaseViewModel`'den türetme (ViFi'de yok); `androidx.lifecycle.ViewModel` + `@HiltViewModel` yeterli.
2. ❌ **ASLA** delegate protokolü/interface callback'i kullanma — UI iletişimi `StateFlow` + `Channel`/`SharedFlow`.
3. ❌ **ASLA** ViewModel'de `Context`, `Activity`, `View`, `NavController` ya da herhangi bir Android UI tipi tutma (yalnızca `@ApplicationContext`, Hilt ile inject edilirse istisna).
4. ❌ **ASLA** ham Firebase tipini (`DataSnapshot`/`DocumentSnapshot`/`Task`) UI'ya sızdırma — repository domain `data class`'a map eder.
5. ❌ **ASLA** callbackFlow'u `awaitClose { ... }` olmadan bırakma (listener sızar).
6. ✅ **HER ZAMAN** state'i TEK `data class XUiState` + `private MutableStateFlow` + `asStateFlow()` olarak ver.
7. ✅ **HER ZAMAN** async işi `viewModelScope.launch` içinde başlat; IO/Firebase işini `Dispatchers.IO` (`withContext`) altında çalıştır.
8. ✅ **HER ZAMAN** Firebase `Task` için `kotlinx-coroutines-play-services` `.await()` kullan.
9. ✅ **HER ZAMAN** hem başarı hem hata dalını ele al; **`isLoading`'i her iki dalda da kapat** (askıda kalma yok).
10. ✅ **HER ZAMAN** kullanıcıya gösterilecek hatayı `ArchiveError`/`AuthError`'ın Türkçe `userMessage`'ından al; state'e `error: String?` olarak yaz.
11. ✅ **HER ZAMAN** tek seferlik olayları (navigate, snackbar) `Channel`/`SharedFlow` ile ver, state'te tutma.
12. ✅ **HER ZAMAN** duplicate isteği `isLoading` guard'ı ile engelle.
13. ✅ **TERCİH ET** state değişimini `_uiState.update { it.copy(...) }` ile (atomik, thread-safe).
14. ✅ **TERCİH ET** tutarlılık için projede TEK desen: `data class + isLoading/error alanları` (sealed Loading/Success/Error yerine).
15. ViewModel **UI olmadan test edilebilir** olmalı (repository yerine `Fake...Repository`).
16. ✅ **ZORUNLU** versioned doc comment: var olan `UiState`/model'e eklenen her alan ve `sealed interface` event'ine eklenen her case `// Added in <versionName> — <amaç>.` taşır (versiyon = `app/build.gradle` `versionName`).

### Yaygın Event'ler ve State Alanları

iOS'un "Common Delegate Methods" listesinin karşılığı. Delegate metotları iki kategoriye ayrışır: **sürekli durum** → `UiState` alanı, **tek seferlik** → event.

```kotlin
// UiState fields (continuous state — iOS didSuccessLoad/willStartLoading/isSelected equivalent)
data class FeatureUiState(
    val items: List<Exam> = emptyList(),   // didSuccessLoad → exams
    val isLoading: Boolean = false,        // willStart/didFinishLoading
    val isLoadingMore: Boolean = false,    // pagination spinner
    val error: String? = null,             // didFailure(message:) → error.userMessage
    val selectedIds: Set<String> = emptySet(), // didUpdateSelection
    val title: String = ""                 // didUpdateTitle(_:)
)

// Events (one-off — navigate / snackbar / CRUD result)
sealed interface FeatureEvent {
    data class OpenExam(val examId: String) : FeatureEvent   // navigate
    data class ShowMessage(val message: String) : FeatureEvent // snackbar
    data object Created : FeatureEvent                        // didSuccessCreate
    data object Updated : FeatureEvent                        // didSuccessUpdate
    data object Deleted : FeatureEvent                        // didSuccessDelete
}
```

### Test Değerlendirmeleri

ViewModel'ler UI olmadan test edilebilir olmalı. Birim test: JUnit + `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`) + Turbine (Flow doğrulama); repository yerine `Fake...Repository`. Detay için bkz. `.claude/rules/testing.md` ve `.claude/skills/generate-unit-tests/SKILL.md`.

```kotlin
@Test
fun `loadExams success emits exams and clears loading`() = runTest {
    val fakeRepo = FakeArchiveRepository(
        exams = listOf(Exam(id = "1", title = "Final 2023", courseId = "math101", kind = "pdf", year = 2023))
    )
    val viewModel = ExamListViewModel(fakeRepo, SavedStateHandle(mapOf("lessonId" to "math101")))

    viewModel.uiState.test {  // Turbine
        // init triggers loadExams; verify the streamed states.
        assertTrue(awaitItem().isLoading)          // initial/loading
        val loaded = awaitItem()
        assertFalse(loaded.isLoading)
        assertEquals(1, loaded.exams.size)
        cancelAndIgnoreRemainingEvents()
    }
}
```

> **Not:** `#if DEBUG` ile internal state açma (iOS) gerekmez — Kotlin'de `private val _uiState` zaten `uiState` ile dışa açık ve test doğrudan `uiState`'i gözler.

---

**Ayrıca bkz.:**
- `.claude/rules/architecture.md` — Proje mimarisi, paketleme (`feature/`, `core/`), section-comment kuralı
- `.claude/rules/firebase-data.md` — Repository + DTO→domain map + `callbackFlow`/`.await()` kuralı
- `.claude/rules/ui-components.md` — Compose/Material3, TopAppBar + navigationIcon, testTag
- `.claude/rules/testing.md` — ViewModel birim testi (runTest/Turbine), UI testi
- `.claude/rules/localization.md` — strings.xml, `stringResource`, positional format args
- `.claude/skills/new-viewmodel/SKILL.md` — ViewModel oluşturma akışı
- `.claude/skills/new-screen/SKILL.md` — Compose ekran + ViewModel ikilisi
