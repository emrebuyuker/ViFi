# ViFi-Android — Mimari, UI, Veri Erişimi & Kod Kalitesi Checklist

> **Legacy not:** Bu checklist **hedef** mimariyi (Kotlin 2.0+, Compose + Material3, MVVM + Hilt, named `vifi` Firestore) denetler. Bugünkü v2.5 (Java + `Activity`/`Fragment` + XML + RTDB `Universitiess`) bu desenlerin çoğunu henüz karşılamaz; PR'da değişen bir legacy dosyayı hedef mimari ihlaliyle değil "henüz göç etmemiş" notuyla işaretle ve göç kalemini `ANDROID-PARITY-ROADMAP.md`'ye bağla. Yeni/ileriye dönük Kotlin + Compose koduna tam checklist uygulanır.

## Mimari Deseni

### ✅ Gerekli
- [ ] UI iki Composable'a ayrılmış: **stateful** `XScreen` + **stateless** `XContent`
- [ ] `XScreen` ViewModel'i `hiltViewModel()` ile alır, state'i `collectAsStateWithLifecycle()` ile toplar, event'leri `LaunchedEffect` ile dinler; başka iş yapmaz
- [ ] `XContent` yalnız `uiState` + `on...` lambda alır, `@Preview`lenebilir; `viewModel`/`hiltViewModel`/iş mantığı içermez
- [ ] `ViewModel` → `androidx.lifecycle.ViewModel` + `@HiltViewModel` + `@Inject constructor(...)` (base class YOK)
- [ ] UI ↔ ViewModel tek yönlü veri akışı (UDF): aşağı immutable state, yukarı lambda
- [ ] Klasör yapısı: `feature/<ad>/` (screen, content, XViewModel, XUiState, list item) + `core/` (data/repository, model, navigation, designsystem, di)
- [ ] Dosya adlandırma: `XScreen.kt`, `XContent.kt`, `XViewModel.kt`, `XUiState.kt`
- [ ] Push edilen ekranlarda geri = `TopAppBar` navigationIcon (ikon-only) — metinli/elle geri YOK (bkz. `.claude/rules/ui-components.md` → Navigation Back Button)
- [ ] Veri her zaman repository `interface` arkasından; UI/ViewModel doğrudan Firebase görmez

### ❌ Yasak
- [ ] Delegate protokolü / interface callback (iOS deseni) — UI iletişimi `StateFlow` + `Channel`/`SharedFlow`
- [ ] `BaseViewModel` / `BaseActivity` / `BaseFragment` kalıtımı
- [ ] Output enum tarzı tek kanal (UI iletişimi StateFlow + event)
- [ ] Yeni ekranda `Activity`/`Fragment`/XML layout (legacy istisnası dışında)
- [ ] Stateless content içinde `hiltViewModel()`/`viewModel`/Firebase erişimi
- [ ] Yeni ekran için `Intent`/`startActivity` gezinme (Navigation-Compose kullan)

## ViewModel

### ✅ Gerekli
- [ ] `@HiltViewModel class XViewModel @Inject constructor(...) : ViewModel()`
- [ ] UI durumu TEK immutable `data class XUiState`
- [ ] `private val _uiState = MutableStateFlow(...)` + `val uiState = _uiState.asStateFlow()` (read-only dışa)
- [ ] Tek seferlik olaylar (navigate, snackbar) `Channel`/`SharedFlow` ile — state'te tutulmaz
- [ ] Yükleme/hata UiState alanı: `isLoading: Boolean` + `error: String?` (proje genelinde **tek** desen)
- [ ] Route arg'ları `SavedStateHandle`'dan okunur (constructor'dan UI tipi değil)
- [ ] Async iş `viewModelScope.launch`; IO/Firebase işi `Dispatchers.IO` (`withContext`)
- [ ] Firebase `Task` için `kotlinx-coroutines-play-services` `.await()`
- [ ] Hem başarı hem hata dalı ele alınır; **`isLoading` her iki dalda da kapatılır**
- [ ] Kullanıcı hatası `ArchiveError`/`AuthError` Türkçe `userMessage`'ından; state'e `error: String?`
- [ ] Duplicate istek `if (_uiState.value.isLoading) return` ile engellenir
- [ ] State değişimi `_uiState.update { it.copy(...) }` ile (atomik)

### ❌ Yasak
- [ ] Delegate protokolü / interface callback
- [ ] `MutableStateFlow`'u dışa açma (`asStateFlow()` ile kapsülle)
- [ ] ViewModel'de `Context`/`Activity`/`View`/`NavController`/`Fragment` (yalnız `@ApplicationContext`, Hilt ile)
- [ ] Ham Firebase tipini (`DataSnapshot`/`DocumentSnapshot`/`Task`) ViewModel'e sokma
- [ ] `GlobalScope` (iptal edilemez, sızar — `viewModelScope` kullan)
- [ ] Hata dalında `isLoading` kapatmayı unutma (spinner sonsuza takılır)

### Örnek Desen
```kotlin
// region State
data class ExamListUiState(
    val exams: List<Exam> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && error == null && exams.isEmpty()
}
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
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val lessonId: String = checkNotNull(savedStateHandle["lessonId"])

    private val _uiState = MutableStateFlow(ExamListUiState())
    val uiState: StateFlow<ExamListUiState> = _uiState.asStateFlow()

    private val _events = Channel<ExamListEvent>(Channel.BUFFERED)
    val events: Flow<ExamListEvent> = _events.receiveAsFlow()

    init { loadExams() }

    fun loadExams() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val exams = archiveRepository.getExams(lessonId)
                _uiState.update { it.copy(isLoading = false, exams = exams) }
            } catch (e: ArchiveError) {
                _uiState.update { it.copy(isLoading = false, error = e.userMessage) }
            }
        }
    }

    fun onExamClick(exam: Exam) {
        viewModelScope.launch { _events.send(ExamListEvent.OpenExam(exam.id)) }
    }
}
```

## Compose UI — Stateless Content

### ✅ Gerekli
- [ ] `@Composable fun XContent(uiState, on..., modifier: Modifier = Modifier)` imzası; `modifier` ilk opsiyonel parametre
- [ ] Kök `Scaffold` (tam ekran) ya da `Surface` (host edilen alt-bileşen)
- [ ] Tüm renkler `MaterialTheme.colorScheme.*` (marka: `core/designsystem/Color.kt`)
- [ ] Tüm metin stilleri `MaterialTheme.typography.*` (marka: `core/designsystem/Type.kt`)
- [ ] Tüm kullanıcı metni `stringResource(R.string.x)` (Türkçe, `strings.xml`)
- [ ] Liste/kayan içerik `LazyColumn`/`LazyVerticalGrid` + stabil `key`
- [ ] Etkileşim hoisted lambda ile yukarı taşınır (`onExamClick: (Exam) -> Unit`); `clickable { }` içinde iş mantığı yok
- [ ] `Modifier.testTag("<ekran>.<öğe>")` etkileşimli/duruma-bağlı node'larda
- [ ] İkonlarda Türkçe `contentDescription` (`strings.xml`'den)
- [ ] `@Preview` Fake data ile stateless content'i sürer (ViewModel değil)

### ❌ Yasak
- [ ] Stateless content'te `hiltViewModel()`/`viewModel`/`NavController`
- [ ] Hardcoded `Color(0xFF...)`, `fontSize = 16.sp`, elle `TextStyle(...)`
- [ ] Composable'a gömülü sabit string
- [ ] Uzun/dinamik liste için `Column` + `Modifier.verticalScroll(...)` + elle yığılmış kartlar (`LazyColumn` kullan; istisna: kısa statik form)
- [ ] `LazyColumn`/`LazyVerticalGrid`'de `key` atlamak
- [ ] Metinli/elle ("< Geri") geri butonu; `BackHandler` ile elle pop

### Örnek Desen
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamListContent(
    uiState: ExamListUiState,
    onExamClick: (Exam) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("examList.screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exam_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("examList.backButton")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).testTag("examList.loading"),
                )
                uiState.error != null -> ErrorState(
                    message = uiState.error, onRetry = onRetry,
                    modifier = Modifier.align(Alignment.Center).testTag("examList.error"),
                )
                uiState.isEmpty -> EmptyState(
                    message = stringResource(R.string.exam_list_empty),
                    modifier = Modifier.align(Alignment.Center).testTag("examList.empty"),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("examList.list"),
                ) {
                    items(items = uiState.exams, key = { it.id }) { exam ->
                        ExamListItem(exam = exam, onClick = { onExamClick(exam) })
                    }
                }
            }
        }
    }
}
```

## Stateful Screen (ViewModel ↔ UI köprüsü)

### ✅ Gerekli
- [ ] `hiltViewModel()` yalnız `XScreen`'de çağrılır
- [ ] State `collectAsStateWithLifecycle()` ile toplanır (`collectAsState` değil)
- [ ] Tek seferlik olaylar `LaunchedEffect` içinde `viewModel.events.collect { ... }` ile dinlenir
- [ ] Gezinme lambda'ları (`onNavigateTo...`, `onNavigateBack`) dışarı verilir, içeride `navController.popBackStack()`/`navigate(...)`

### ❌ Yasak
- [ ] `collectAsState` (lifecycle-aware değil)
- [ ] `XScreen` içinde layout/iş mantığı (onun yeri `XContent`)

### Örnek Desen
```kotlin
@Composable
fun ExamListScreen(
    onNavigateToExamDetail: (examId: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ExamListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ExamListEvent.OpenExam -> onNavigateToExamDetail(event.examId)
                is ExamListEvent.ShowMessage -> { /* snackbar host */ }
            }
        }
    }

    ExamListContent(
        uiState = uiState,
        onExamClick = viewModel::onExamClick,
        onRetry = viewModel::loadExams,
        onBack = onNavigateBack,
    )
}
```

## Navigation (Navigation-Compose)

### ✅ Gerekli
- [ ] Route string'leri + `NavGraphBuilder` extension (`core/navigation/`)
- [ ] `composable(route, arguments = listOf(navArgument(...) { type = NavType.StringType }))`
- [ ] Route arg'ı ViewModel'de `SavedStateHandle`'dan okunur
- [ ] Ekranlar arası yalnız **id** geçilir; detay ekranı tam nesneyi repository'den çeker
- [ ] Geri = `navController.popBackStack()` (ikon-only navigationIcon'un lambda'sına bağlı)

### ❌ Yasak
- [ ] Yeni ekranda `Intent`/`startActivity`
- [ ] Route üzerinden büyük/karmaşık nesne taşımak
- [ ] `BackHandler` ile elle pop (sistem geri Navigation-Compose'da yönetilir)

### Örnek Desen
```kotlin
const val EXAM_LIST_ROUTE = "exam_list"
private const val LESSON_ID_ARG = "lessonId"

fun NavController.navigateToExamList(lessonId: String) {
    navigate("$EXAM_LIST_ROUTE/$lessonId")
}

fun NavGraphBuilder.examListScreen(
    onNavigateToExamDetail: (examId: String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    composable(
        route = "$EXAM_LIST_ROUTE/{$LESSON_ID_ARG}",
        arguments = listOf(navArgument(LESSON_ID_ARG) { type = NavType.StringType }),
    ) {
        ExamListScreen(
            onNavigateToExamDetail = onNavigateToExamDetail,
            onNavigateBack = onNavigateBack,
        )
    }
}
```

## Veri Erişimi (Repository + Hilt)

### ✅ Gerekli
- [ ] Her kaynak bir Kotlin `interface` arkasında (`ArchiveRepository`, `AuthRepository`)
- [ ] Canlı impl `Firebase...Repository`; preview/test `Fake...Repository`
- [ ] Repository UI'ya ham Firebase tipi (`DataSnapshot`/`DocumentSnapshot`) **sızdırmaz** → DTO→domain `data class` map eder
- [ ] Domain modeller plain `data class` (`core/model/`)
- [ ] Hatalar `sealed class ArchiveError/AuthError : Exception()` + Türkçe `userMessage`'a çevrilir
- [ ] IO/Firebase işi `withContext(Dispatchers.IO)`; `Task` → `.await()`
- [ ] Realtime dinleyici `callbackFlow { ... awaitClose { registration.remove() } }` + `flowOn(Dispatchers.IO)`
- [ ] Hilt `@Binds`/`@Provides` ile interface ↔ impl bağlanır; named `vifi` Firestore provider
- [ ] Veri kaynağı iOS ile ortak `vifi-831a8` Firebase projesi; güvenlik kuralları/Cloud Functions Android'de değil, `ViFi-iOS/firebase/`de — Android yeniden kullanır, yeni backend yazmaz

### ❌ Yasak
- [ ] ViewModel/Composable içinde doğrudan Firebase çağrısı
- [ ] Ham `DocumentSnapshot`/`DataSnapshot` dönen/sızdıran imza
- [ ] `callbackFlow`'u `awaitClose` olmadan bırakmak (listener sızar)
- [ ] IO işini ana thread'de çalıştırmak
- [ ] `@HiltViewModel` ile kayıtlı olmayan/elle kurulan bağımlılık grafiği

### Örnek Desen
```kotlin
// interface — iOS protocol equivalent. Raw Firebase types do NOT leak.
interface ArchiveRepository {
    suspend fun getExams(lessonId: String): List<Exam>
    fun observeExams(lessonId: String): Flow<List<Exam>>
}

// Live impl — DTO→domain map, error→ArchiveError, Dispatchers.IO + await().
class FirebaseArchiveRepository @Inject constructor(
    @Named("vifi") private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context, // to resolve userMessage from strings.xml
) : ArchiveRepository {

    override suspend fun getExams(lessonId: String): List<Exam> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("exams")
                .whereEqualTo("courseId", lessonId)
                .whereEqualTo("status", "published")
                .get().await()
                .documents.mapNotNull { it.toExam() } // DTO→domain
        } catch (e: FirebaseFirestoreException) {
            throw e.toArchiveError(context)
        }
    }

    override fun observeExams(lessonId: String): Flow<List<Exam>> = callbackFlow {
        val registration = firestore.collection("exams")
            .whereEqualTo("courseId", lessonId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error.toArchiveError(context)); return@addSnapshotListener }
                trySend(snapshot?.documents?.mapNotNull { it.toExam() } ?: emptyList())
            }
        awaitClose { registration.remove() } // REQUIRED
    }.flowOn(Dispatchers.IO)
}

// Hilt binding
@Module
@InstallIn(SingletonComponent::class)
abstract class ArchiveModule {
    @Binds @Singleton
    abstract fun bindArchiveRepository(impl: FirebaseArchiveRepository): ArchiveRepository
}
```

> Not: iOS `AppEnvironment(live/mock)` → Android'de Hilt modül + `interface` + `Fake...` üçlüsüdür.

## UI & Styling

### ✅ Gerekli
- [ ] `MaterialTheme.colorScheme.*` tüm renklerde; marka renkleri `core/designsystem/Color.kt`
- [ ] `MaterialTheme.typography.*` tüm metinlerde; marka tipografisi `core/designsystem/Type.kt`
- [ ] `textStyle` metnin rolüne göre seçilir (başlık `titleLarge/Medium`, gövde `bodyLarge/Medium`, etiket `labelMedium/Small`)
- [ ] `sp` birimi korunur (dinamik yazı ölçeği otomatik); sabit `dp` metin boyutu yok
- [ ] `Modifier.testTag("<ekran>.<öğe>")` etkileşimli/duruma-bağlı node'larda
- [ ] `{ekran}.{öğe}` testTag deseni (ör. `examList.backButton`)

### ❌ Yasak
- [ ] Hardcoded `Color(0xFF...)`
- [ ] Hardcoded `fontSize` / elle `TextStyle(...)`
- [ ] Eksik testTag / `contentDescription`

### Renk Örnekleri
```kotlin
// ✅ Correct
containerColor = MaterialTheme.colorScheme.background
Text(..., color = MaterialTheme.colorScheme.onSurface)
// use a core/designsystem/Color.kt constant if a brand color is needed

// ❌ Wrong
Text(..., color = Color(0xFF2571F9))
Box(Modifier.background(Color(0xFFFFFFFF)))
```

### Tipografi Örnekleri
```kotlin
// ✅ Correct
Text(stringResource(R.string.exam_list_title), style = MaterialTheme.typography.titleLarge)

// ❌ Wrong
Text("Sınavlar", fontSize = 20.sp, fontWeight = FontWeight.Bold)
```

## Bellek & Yaşam Döngüsü

### ✅ Gerekli
- [ ] Tüm async iş `viewModelScope.launch` içinde (VM clear'da iptal; iOS `[weak self]` karşılığı)
- [ ] `callbackFlow { ... awaitClose { registration.remove() } }` ile listener kaldırılır
- [ ] `LaunchedEffect`/`DisposableEffect` doğru key'lerle; `DisposableEffect` `onDispose`'da temizler
- [ ] `collectAsStateWithLifecycle()` ile arka planda akış durur
- [ ] ViewModel'de `Context`/`Activity`/`View`/`NavController` yok (`@ApplicationContext` Hilt istisnası)

### ❌ Yasak
- [ ] `GlobalScope`
- [ ] `awaitClose`'suz `callbackFlow`
- [ ] Yanlış/eksik `LaunchedEffect` key (ör. değişen veriye bağlı effect'te `Unit` key)
- [ ] ViewModel'de UI/Context referansı tutmak

### Örnek Desen
```kotlin
// ✅ Correct
viewModelScope.launch { archiveRepository.getExams(lessonId) }

callbackFlow {
    val registration = query.addSnapshotListener { s, _ -> trySend(map(s)) }
    awaitClose { registration.remove() }
}

DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, _ -> /* ... */ }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}

// ❌ Wrong
GlobalScope.launch { archiveRepository.getExams(lessonId) }       // non-cancelable / leaks
callbackFlow { query.addSnapshotListener { s, _ -> trySend(map(s)) } } // no awaitClose
```

## Lokalizasyon

### ✅ Gerekli
- [ ] Tüm kullanıcı metni `stringResource(R.string.x)` (`res/values/strings.xml`, Türkçe)
- [ ] Dinamik değer positional format arg ile: `strings.xml`'de `%1$s`/`%1$d`, çağrıda `stringResource(R.string.x, arg)`
- [ ] İkon `contentDescription` Türkçe ve `strings.xml`'de
- [ ] Hata metni repository'de `Context.getString(...)` ile çözülüp `userMessage`'a yazılır

### ❌ Yasak
- [ ] Composable/koda gömülü sabit string
- [ ] `%s`/`%d` (numarasız) — çeviri güvenliği için numaralı (`%1$s`) kullan

### Örnek Desen
```xml
<!-- strings.xml -->
<string name="exam_list_title">Sınavlar</string>
<string name="exam_list_empty">Bu ders için henüz sınav eklenmemiş.</string>
<string name="exam_list_count">%1$d sınav bulundu</string>
<string name="cd_navigate_back">Geri</string>
```
```kotlin
// ✅ Correct
Text(stringResource(R.string.exam_list_count, uiState.exams.size))

// ❌ Wrong
Text("Sınavlar")
```
Referans: `.claude/rules/localization.md`.

## Tarih / Zaman Dönüşümü

Firestore `Timestamp` / backend ISO 8601 string'leri **merkezi** tarih util'inden geçer; Composable/ViewModel içinde inline formatlama yoktur.

### ✅ Gerekli
- [ ] Görüntüleme merkezi util üzerinden (ör. extension `Long.toDisplayDate()` / util fonksiyonu)
- [ ] Domain model tarih alanı plain tip (`Long`/`String`); repository map'inde çözülür

### ❌ Yasak
```kotlin
// ❌ Inline SimpleDateFormat (in a Composable/ViewModel)
val text = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(exam.uploadedAt))

// ❌ static/property formatter on a Composable/Cell
```
Referans: `.claude/rules/date-handling.md`.

## Test

### ✅ Gerekli
- [ ] ViewModel birim testi: JUnit + `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`) + Turbine
- [ ] Repository yerine `Fake...Repository`; gerektiğinde MockK
- [ ] Compose UI testi: `createAndroidComposeRule` + `onNodeWithTag(...)` + `performClick()`
- [ ] testTag tabanlı erişim (text tabanlı selector değil)
- [ ] Açıklayıcı backtick test adı (`` `loadExams success emits exams` ``)

### ❌ Yasak
- [ ] Gerçek Firebase ile test
- [ ] `onNodeWithText(...)` ile lokalizasyona bağlı kırılgan test
- [ ] Implementation detayını test etmek

### Örnek Desen
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class ExamListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeArchiveRepository

    @Before fun setUp() { Dispatchers.setMain(testDispatcher); repository = FakeArchiveRepository() }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `loadExams success emits loading then exams`() = runTest {
        repository.examsToReturn = listOf(Exam("e1", "Vize 2023", "math101", "pdf", 2023))
        val viewModel = ExamListViewModel(repository, SavedStateHandle(mapOf("lessonId" to "math101")))

        viewModel.uiState.test { // Turbine
            assertTrue(awaitItem().isLoading)
            val loaded = awaitItem()
            assertFalse(loaded.isLoading)
            assertEquals(1, loaded.exams.size)
            assertNull(loaded.error)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

## Kod Kalitesi

### ✅ Gerekli
- [ ] Adlandırma: `XScreen`, `XContent`, `XViewModel`, `XUiState`, `XRepository`
- [ ] Kod yorumları İngilizce; kullanıcı metni Türkçe (`strings.xml`)
- [ ] Section yorumları `// region ... // endregion` veya `// --- Section ---` (bölüm yorumu ile sonraki bildirim arası boş satır yok)
- [ ] TODO'suz yorumlanmış kod yok; `Log`/`println` debug bırakma yok
- [ ] Büyülü sayı yerine isimli sabit
- [ ] Versioned doc comment: var olan model/`UiState`'e eklenen her alan + `sealed` event'e eklenen her case `// Added in <versionName> — <amaç>.` (versiyon = `app/build.gradle` `versionName`)

### ❌ Yasak
- [ ] `!!` / unsafe cast (`as`) — `?.`/`?:`/`checkNotNull`/`as?` kullan
- [ ] Türkçe kod yorumu
- [ ] `println` / debug `Log`
- [ ] Magic number
- [ ] TODO'suz yorum satırı bırakılmış kod

## Performans

Performans denetimi ayrı dosyadadır: ana thread'de IO yok, Compose recomposition (stable/`remember`/`key`), `LazyColumn` key, Coil image boyutlama, gereksiz allocation. Bkz. `checklists/performance.md`.

---

**Ayrıca bkz.:**
- `SKILL.md` — PR review akışı (bu checklist'leri kullanır)
- `checklists/performance.md` — Performans denetim listesi
- `checklists/output-format.md` — Review çıktı formatı
- `.claude/rules/architecture.md` — ViFi hedef mimari genel bakış
- `.claude/rules/viewmodel-rule.md` — ViewModel / StateFlow / event kuralları
- `.claude/rules/ui-components.md` — Compose UI bileşen kuralları
- `.claude/rules/firebase-data.md` — Repository + DTO→domain map + callbackFlow
- `.claude/rules/testing.md` — Birim + Compose UI test
- `.claude/rules/localization.md` — strings.xml + stringResource
- `.claude/rules/date-handling.md` — tarih dönüşümü
