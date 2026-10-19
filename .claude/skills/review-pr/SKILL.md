---
name: review-pr
description: Bir pull request'i ViFi-Android proje standartlarına, hedef mimariye (Compose + MVVM + Hilt), performansa ve kod kalitesine göre gözden geçir. Değişen dosyaları architecture / performance / output-format checklist'lerine karşı denetler ve sabit formatlı bir review raporu üretir.
---

> **Legacy not:** Bugünkü v2.5 kodu Java + `Activity`/`Fragment` + XML layout + RTDB `Universitiess` + e-posta/şifre giriş üzerinde ve ayrı bir ViewModel katmanı yok. Bu skill **yeni/ileriye dönük** Kotlin + Compose kodunu **hedef** mimariye (Compose + MVVM + Hilt + named `vifi` Firestore) göre denetler. Legacy bir dosya (ör. `CourseExamListActivity`, `CustomAdapter`, `activity_*.xml`) PR'da değişiyorsa, onu hedef desene göre "bu dosya henüz göç etmemiş legacy" olarak işaretle ve göç kalemini `ANDROID-PARITY-ROADMAP.md`'ye bağla — legacy dosyaya hedef mimari ihlali gibi 🔴 verme, ama yeni eklenen Kotlin/Compose koduna tam checklist uygula. Göç yol haritası: repo kökü `ANDROID-PARITY-ROADMAP.md` / `ANDROID-PARITY-DETAIL.md`.

# Review PR (ViFi-Android)

PR'ı ViFi-Android hedef mimarisine göre gözden geçirir. Bu `SKILL.md` **akışı** (nasıl review yapılır) tanımlar; **ne kontrol edileceği** üç checklist'tedir:

- `checklists/architecture.md` — MVVM sınırları, Hilt, StateFlow, Repository soyutlaması, Context sızıntısı, coroutine scope, versioned doc comment, UI/stringler/tarih/erişilebilirlik.
- `checklists/performance.md` — Compose recomposition (stable/remember/key), `LazyColumn` key, ana thread'de IO yok, Coil image boyutlama, gereksiz allocation.
- `checklists/output-format.md` — Review çıktısının birebir formatı (Android dosya/satır atıfları).

## Usage

```
/review-pr [PR_NUMBER]
```

## Steps

1. `gh pr view {number} --json files,additions,deletions,title,body`
2. `gh pr diff {number}`
3. Değişen her dosyayı ViFi-Android checklist'lerine karşı denetle:
   - **Mimari**: Stateful `Screen` + stateless `Content` + `ViewModel` + `UiState` (UDF — state aşağı, lambda yukarı)
   - **ViewModel**: `@HiltViewModel` + `StateFlow<UiState>` + `Channel`/`SharedFlow` event; delegate protokolü YOK, base class YOK
   - **UI (Compose)**: Material3 Composable, `MaterialTheme.colorScheme.*`, `MaterialTheme.typography.*`, `stringResource`, testTag; XML/Fragment/Activity YOK (legacy istisnası)
   - **Veri erişimi**: Repository `interface` + `Firebase...Repository` + `Fake...Repository`; UI'ya ham Firebase tipi (`DataSnapshot`/`DocumentSnapshot`) sızmaz
   - **Navigation**: Navigation-Compose (`NavHost`, route), geri = `TopAppBar` navigationIcon (ikon-only) + `popBackStack()`
   - **Erişilebilirlik**: `Modifier.testTag("<ekran>.<öğe>")` + `contentDescription` (Türkçe, `strings.xml`'den)
   - **Performans**: ana thread'de IO yok, recomposition kontrolü, `LazyColumn` key, Coil boyutlama
   - **Bellek/yaşam döngüsü**: coroutine'ler `viewModelScope`'ta, `callbackFlow`'da `awaitClose`, `LaunchedEffect`/`DisposableEffect` doğru key'le, ViewModel'de `Context` yok
4. Çıktıyı `checklists/output-format.md`'deki yapıyla, dosya + satır atıfları ve düzeltme önerileriyle biçimlendir.

## Quick Reference — ViFi-Android Key Checks

| Alan | ✅ Gerekli | ❌ Yasak |
|------|-----------|----------|
| **Mimari** | Stateful `Screen` + stateless `Content` + `ViewModel` + `UiState` (UDF) | Delegate protokolü, `BaseViewModel`, yeni ekranda Activity/Fragment/XML |
| **ViewModel** | `@HiltViewModel class X @Inject constructor(...)`, `StateFlow<UiState>` (`MutableStateFlow` + `asStateFlow()`), `Channel`/`SharedFlow` event | Delegate/interface callback, Output enum, `Context`/`View`/`NavController` tutma |
| **State** | Tek immutable `data class XUiState` + `isLoading`/`error` alanları (proje TEK deseni) | `sealed Loading/Success/Error` karışık kullanımı, `didSet`/observer tarzı |
| **UI** | `@Composable`, Material3, `MaterialTheme.colorScheme.*`, `MaterialTheme.typography.*` | XML layout, `Color(0xFF...)`, `fontSize = 16.sp` / elle `TextStyle(...)` |
| **Listeler** | `LazyColumn`/`LazyVerticalGrid` + stabil `key` | `Column` + `verticalScroll` + elle yığılmış kartlar (uzun/dinamik liste) |
| **Veri erişimi** | Repository `interface` + `Firebase...Repository` + `Fake...Repository` | UI/ViewModel'de doğrudan Firebase, ham `DocumentSnapshot`/`DataSnapshot` sızıntısı |
| **Async** | `viewModelScope.launch`, `Dispatchers.IO`, `Task.await()`, `callbackFlow { awaitClose { } }` | Ana thread'de blocking IO, `GlobalScope`, `awaitClose`'suz `callbackFlow` |
| **Navigation** | `NavHost` + route, `navController.popBackStack()` | Elle/metinli ("< Geri") geri butonu, `Intent`/`startActivity` (yeni ekran) |
| **Geri butonu** | `TopAppBar` navigationIcon — `IconButton` + `Icons.AutoMirrored.Filled.ArrowBack` | `BackHandler` ile elle pop, metin tabanlı geri |
| **Renk** | `MaterialTheme.colorScheme.*`, `core/designsystem/Color.kt` | Hardcoded `Color(0xFF...)` |
| **Tipografi** | `MaterialTheme.typography.*`, `core/designsystem/Type.kt` | Hardcoded `fontSize`/`TextStyle` |
| **Stringler** | `stringResource(R.string.x)` + `strings.xml` (Türkçe, `%1$s`/`%1$d`) | Composable'a gömülü sabit string, `%s`/`%d` (numarasız) |
| **Tarih** | Merkezi tarih util (bkz. `.claude/rules/date-handling.md`) | Inline `SimpleDateFormat`, Composable/ViewModel içinde formatlama |
| **Bellek** | `viewModelScope`, `callbackFlow` + `awaitClose`, doğru `LaunchedEffect` key | ViewModel'de `Context`/`Activity`, listener sızıntısı, `GlobalScope` |
| **Erişilebilirlik** | `Modifier.testTag("<ekran>.<öğe>")` + `contentDescription` | Eksik testTag, metin tabanlı testler |
| **Performans** | Ana thread'de IO yok, stable/`remember`/`key`, `LazyColumn` key, Coil boyutlama | Ana thread blocking, her recomposition'da allocation, key'siz lazy list |
| **Test** | Fake repository + Turbine + `runTest`; Compose `onNodeWithTag` | MockK yerine gerçek Firebase, text tabanlı selector |

## Detailed Checklist

> Aşağısı hızlı tarama içindir; her maddenin tam gerekçesi, ✅/❌ örnekleri ve düzeltmeleri `checklists/architecture.md` (mimari/UI/bellek/test) ve `checklists/performance.md` (performans) içindedir.

### 1. Mimari Deseni

**✅ Kontrol et:**
- [ ] UI iki Composable: **stateful** `XScreen` (ViewModel `hiltViewModel()` ile burada alınır, state `collectAsStateWithLifecycle()` ile burada toplanır, event'ler burada dinlenir) + **stateless** `XContent` (sadece `uiState` + lambda; `@Preview`lenebilir)
- [ ] `ViewModel` → `androidx.lifecycle.ViewModel` + `@HiltViewModel`; UI ile UDF (state aşağı, lambda yukarı)
- [ ] Klasör yapısı: `feature/<ad>/` (screen, content, XViewModel, XUiState) + `core/` (data/repository, model, navigation, designsystem)
- [ ] Dosya adlandırma: `XScreen.kt`, `XContent.kt`, `XViewModel.kt`, `XUiState.kt`

**❌ Varsa işaretle:**
- [ ] Delegate protokolü / interface callback (UI iletişimi `StateFlow` + `Channel` olmalı)
- [ ] `BaseViewModel` / `BaseActivity` kalıtımı
- [ ] Yeni ekranda `Activity`/`Fragment`/XML layout (legacy istisnası dışında)
- [ ] Stateless content içinde `hiltViewModel()`/`viewModel`/iş mantığı

### 2. ViewModel

**✅ Kontrol et:**
```kotlin
// region State
data class ExamListUiState(
    val exams: List<Exam> = emptyList(),
    val isLoading: Boolean = false,       // willStart/didFinishLoading equivalent (single field)
    val error: String? = null,            // didFailure(message:) → error.userMessage equivalent
)
// endregion

// region Events
sealed interface ExamListEvent {          // one-time: navigate / snackbar
    data class OpenExam(val examId: String) : ExamListEvent
    data class ShowMessage(val message: String) : ExamListEvent
}
// endregion

@HiltViewModel                            // no base class
class ExamListViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository,  // interface, not Firebase
    savedStateHandle: SavedStateHandle,                // route arg read here
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExamListUiState())
    val uiState: StateFlow<ExamListUiState> = _uiState.asStateFlow()  // read-only outward

    private val _events = Channel<ExamListEvent>(Channel.BUFFERED)
    val events: Flow<ExamListEvent> = _events.receiveAsFlow()

    fun loadExams() {
        if (_uiState.value.isLoading) return                 // duplicate guard
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {                              // no leak; cancelled on VM clear
            try {
                val exams = archiveRepository.getExams(lessonId)  // repo uses Dispatchers.IO
                _uiState.update { it.copy(isLoading = false, exams = exams) }
            } catch (e: ArchiveError) {
                // Error branch also clears isLoading (no hang).
                _uiState.update { it.copy(isLoading = false, error = e.userMessage) }
            }
        }
    }
}
```

**❌ Varsa işaretle:**
```kotlin
// ❌ Delegate protocol (iOS pattern) — in ViFi, UI communication is StateFlow + Channel.
interface ExamListViewModelDelegate { fun didSuccessLoad() }

// ❌ MutableStateFlow exposed (no encapsulation)
val uiState = MutableStateFlow(ExamListUiState())  // should be exposed read-only via asStateFlow()

// ❌ isLoading not cleared in the error branch → spinner hangs forever
catch (e: ArchiveError) { _uiState.update { it.copy(error = e.userMessage) } }

// ❌ Android UI type in the ViewModel
class ExamListViewModel(private val context: Context, private val nav: NavController)

// ❌ GlobalScope instead of viewModelScope (leak / non-cancelable work)
GlobalScope.launch { archiveRepository.getExams(lessonId) }
```

### 3. Compose UI (Content)

**✅ Kontrol et:**
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamListContent(
    uiState: ExamListUiState,              // state down
    onExamClick: (Exam) -> Unit,          // lambda up
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("examList.screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exam_list_title)) },  // strings.xml
                navigationIcon = {
                    // Icon-only back button — the only allowed back control on a pushed screen.
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).testTag("examList.list"),
        ) {
            items(items = uiState.exams, key = { it.id }) { exam ->  // stable key
                ExamListItem(exam = exam, onClick = { onExamClick(exam) })
            }
        }
    }
}
```

**❌ Varsa işaretle:**
```kotlin
// ❌ Hardcoded color / typography / string
Text("Sınavlar", color = Color(0xFF2571F9), fontSize = 20.sp)
// ✅ Correct: stringResource + colorScheme + typography
Text(stringResource(R.string.exam_list_title), style = MaterialTheme.typography.titleLarge)

// ❌ Manual/text back button
Text("< Geri", modifier = Modifier.clickable { navController.popBackStack() })

// ❌ ViewModel inside stateless content
@Composable fun ExamListContent(viewModel: ExamListViewModel = hiltViewModel()) { }

// ❌ LazyColumn without a key (breaks recomposition/animation)
items(uiState.exams) { exam -> ExamListItem(exam) }

// ❌ Column + verticalScroll for a long/dynamic list (no recycling)
Column(Modifier.verticalScroll(rememberScrollState())) { uiState.exams.forEach { ExamListItem(it) } }
```

### 4. Stateful Screen + Navigation

**✅ Kontrol et:**
```kotlin
@Composable
fun ExamListScreen(
    onNavigateToExamDetail: (examId: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ExamListViewModel = hiltViewModel(),   // hiltViewModel() ONLY here
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()  // lifecycle-aware

    LaunchedEffect(Unit) {                             // one-time events
        viewModel.events.collect { event ->
            when (event) {
                is ExamListEvent.OpenExamDetail -> onNavigateToExamDetail(event.examId)
                is ExamListEvent.ShowMessage -> { /* snackbar */ }
            }
        }
    }

    ExamListContent(
        uiState = uiState,
        onExamClick = viewModel::onExamClick,
        onRetry = viewModel::loadExams,
        onBack = onNavigateBack,                 // → navController.popBackStack()
    )
}
```

**❌ Varsa işaretle:**
```kotlin
// ❌ collectAsState (not lifecycle-aware) → use collectAsStateWithLifecycle
val uiState by viewModel.uiState.collectAsState()

// ❌ Intent/startActivity navigation for a new screen
context.startActivity(Intent(context, ExamDetailActivity::class.java))

// ❌ Manual pop with BackHandler (system back is already handled by Navigation-Compose)
BackHandler { navController.popBackStack() }
```

### 5. Veri Erişimi (Repository)

**✅ Kontrol et:**
```kotlin
// interface — does NOT leak raw Firebase types to the UI (iOS protocol equivalent).
interface ArchiveRepository {
    suspend fun getExams(lessonId: String): List<Exam>
    fun observeExams(lessonId: String): Flow<List<Exam>>
}

// Live impl — DTO→domain map, error→ArchiveError, Dispatchers.IO + await().
class FirebaseArchiveRepository @Inject constructor(
    @Named("vifi") private val firestore: FirebaseFirestore,
) : ArchiveRepository {
    override suspend fun getExams(lessonId: String): List<Exam> = withContext(Dispatchers.IO) {
        firestore.collection("exams").whereEqualTo("courseId", lessonId)
            .get().await()                                 // kotlinx-coroutines-play-services
            .documents.mapNotNull { it.toExamOrNull() }    // DocumentSnapshot does not leak out
    }
}
```

**❌ Varsa işaretle:**
```kotlin
// ❌ Direct Firebase inside the ViewModel/Composable (no repository)
firestore.collection("exams").get().addOnSuccessListener { /* in UI */ }

// ❌ Exposing a raw Firebase type
suspend fun getExams(lessonId: String): List<DocumentSnapshot>

// ❌ callbackFlow without awaitClose (listener leaks)
callbackFlow { ref.addSnapshotListener { s, _ -> trySend(map(s)) } }  // no awaitClose
```

### 6. Bellek & Yaşam Döngüsü

**✅ Kontrol et:**
- [ ] Tüm async iş `viewModelScope.launch` içinde (VM clear'da iptal; iOS `[weak self]` karşılığı)
- [ ] `callbackFlow { ... awaitClose { registration.remove() } }` ile listener kaldırılır
- [ ] `LaunchedEffect`/`DisposableEffect` doğru key'lerle; `DisposableEffect` `onDispose`'da temizler
- [ ] ViewModel'de `Context`/`Activity`/`View`/`NavController` yok (`@ApplicationContext` istisnası, Hilt ile)

**❌ Varsa işaretle:**
```kotlin
// ❌ GlobalScope (non-cancelable, leaks)       → viewModelScope.launch
// ❌ callbackFlow without awaitClose (listener leak)
// ❌ LaunchedEffect(Unit) when the effect should depend on changing data → give the correct key
// ❌ Context/Activity in the ViewModel constructor → move it behind the repository
```

### 7. UI & Styling

**✅ Kontrol et:**
- [ ] `MaterialTheme.colorScheme.*` tüm renklerde; marka renkleri `core/designsystem/Color.kt`
- [ ] `MaterialTheme.typography.*` tüm metinlerde; marka tipografisi `core/designsystem/Type.kt`
- [ ] `stringResource(R.string.x)` tüm kullanıcı metinlerinde (`strings.xml`, Türkçe)
- [ ] `Modifier.testTag("<ekran>.<öğe>")` etkileşimli/duruma-bağlı node'larda
- [ ] İkonlarda Türkçe `contentDescription` (`strings.xml`'den)

**❌ Varsa işaretle:**
```kotlin
// ❌ Color(0xFF...) / fontSize = 16.sp / "hardcoded text"
// ❌ Missing testTag or contentDescription
```

### 7a. Tarih / Zaman Dönüşümü

Firestore `Timestamp` / backend ISO 8601 string'leri **merkezi** tarih util'inden geçer; Composable/ViewModel içinde inline formatlama yoktur.

**✅ Kontrol et:**
- [ ] Görüntüleme: merkezi util (`exam.uploadedAt.toDisplayDate()` gibi), Composable içinde değil
- [ ] Domain model tarih alanları plain tip (`Long`/`String`), repository map'inde çözülür

**❌ Varsa işaretle:**
```kotlin
// ❌ Inline SimpleDateFormat (in a Composable/ViewModel)
val text = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(exam.uploadedAt))
```
Referans: `.claude/rules/date-handling.md`.

### 8. Performans

**✅ Kontrol et:**
- [ ] Ağır iş arka planda (`Dispatchers.IO`); ana thread'de blocking IO yok
- [ ] Recomposition güvenli: stable parametreler, pahalı hesap `remember`, `derivedStateOf`
- [ ] `LazyColumn`/`LazyVerticalGrid`'de stabil `key`
- [ ] Coil image boyutlama (`AsyncImage` + `SubcomposeAsyncImage`/`ContentScale`), thumbnail için 4K indirme yok
- [ ] Her recomposition'da gereksiz allocation yok (lambda/list/formatter)

Tam performans listesi ve ❌ örnekleri: `checklists/performance.md`.

### 9. Test

**✅ Kontrol et:**
- [ ] ViewModel birim testi: `runTest` + `StandardTestDispatcher` + Turbine; repository yerine `Fake...Repository`
- [ ] Compose UI testi: `createAndroidComposeRule` + `onNodeWithTag(...)` + `performClick()`
- [ ] testTag tabanlı erişim (text tabanlı selector değil)
- [ ] Test adlandırma: backtick'li açıklayıcı isim (`` `loadExams success emits exams` ``)

**❌ Varsa işaretle:**
```kotlin
// ❌ Testing with real Firebase (use a Fake repository)
// ❌ onNodeWithText("Sınavlar") (localization-fragile) → onNodeWithTag("examList.screen")
```

### 10. Kod Kalitesi

**✅ Kontrol et:**
- [ ] Kod yorumları İngilizce; kullanıcı metni Türkçe (`strings.xml`)
- [ ] Section yorumları `// region ... // endregion` veya `// --- Section ---`
- [ ] `!!` / unsafe cast yok; nullable'lar `?.`/`?:`/`checkNotNull` ile
- [ ] `Log`/`println` debug bırakma yok; büyülü sayı yerine isimli sabit
- [ ] Versioned doc comment: var olan model/`UiState`'e eklenen her alan + `sealed` event'e eklenen her case `// Added in <versionName> — <amaç>.`

**❌ Varsa işaretle:**
```kotlin
// ❌ val exam = exams.first()!!      // → firstOrNull() + guard
// ❌ Magic number: Modifier.height(327.dp)  // → named constant
// ❌ println("user: $user")          // → remove / proper logger
```

## Output Format

Review çıktısı **her zaman** `checklists/output-format.md`'deki yapıyı kullanır (uyum tabloları, dosya-dosya bulgular, 🔴/🟡/✅ seviyeleri, onay durumu). Dosya atıfları Android yollarıdır (ör. `feature/examlist/ExamListViewModel.kt` — satır `N`), `.swift` değil.

## Example

```
/review-pr 42
```

---

**Ayrıca bkz.:**
- `checklists/architecture.md` — Mimari / UI / bellek / test denetim listesi (tam)
- `checklists/performance.md` — Performans denetim listesi (Compose recomposition, LazyColumn key, IO, Coil)
- `checklists/output-format.md` — Review çıktı formatı (Android dosya/satır atıfları)
- `.claude/rules/architecture.md` — ViFi hedef mimari genel bakış (Compose + MVVM + Hilt)
- `.claude/rules/viewmodel-rule.md` — ViewModel / StateFlow / event kuralları
- `.claude/rules/ui-components.md` — Compose UI bileşen kuralları (tema, tipografi, geri butonu)
- `.claude/rules/firebase-data.md` — Repository, Firestore/RTDB, DTO→domain map
- `.claude/rules/testing.md` — JUnit + Turbine + Compose test (testTag sözleşmesi)
- `.claude/rules/localization.md` — strings.xml + stringResource + positional arg
- `.claude/rules/date-handling.md` — tarih dönüşümü (ISO 8601 / Firestore Timestamp)
- `.claude/commands/check-architecture.md` — tek dosya/diff mimari denetimi komutu
