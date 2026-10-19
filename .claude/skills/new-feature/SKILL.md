---
name: new-feature
description: Create a complete feature module (Screen + stateless Content + ViewModel + UiState + Repository) following the ViFi-Android Jetpack Compose + MVVM + Hilt target architecture.
---

> **Legacy not:** Bugünkü Android (v2.5) karşılığı `CourseExamListActivity` / `ExamDetailActivity` + `CustomAdapter`/`ListViewAdapter` + RTDB `Universitiess` okumaları + `activity_*.xml` layout'larıdır. Bu skill **yeni/ileriye dönük** feature'lar içindir ve **hedef** mimariyi (Compose + MVVM + Hilt + named `vifi` Firestore) üretir. Göçteki yeri: `ANDROID-PARITY-ROADMAP.md` → Faz 1 (mimari iskelet) ve `BE-15` (arşivi RTDB→Firestore'a taşı). Legacy Activity/Fragment/XML yeni ekranlarda kullanılmaz.

> ⚠️ **Typography Rule (zorunlu):** Her `Text` composable'ı `MaterialTheme.typography.<style>` kullanır (ör. `style = MaterialTheme.typography.titleMedium`). Composable içine `fontSize = 16.sp` / elle `TextStyle(...)` gömmek **yasak**; marka tipografisi `core/designsystem/theme/Type.kt`'tedir. Compose, sistem yazı ölçeğini (`fontScale`) otomatik uygular — kendi `sp` sabitini yazma. Canonical reference: `.claude/rules/ui-components.md` (Typography).

> ⚠️ **Color Rule (zorunlu):** Renkler `MaterialTheme.colorScheme.<token>` üzerinden gelir; marka renkleri `core/designsystem/theme/Color.kt`'te sabittir. `Color(0xFF...)`'i Composable içine gömmek **yasak**. Canonical reference: `.claude/rules/ui-components.md` (Theme & Color).

> ⚠️ **Background Rule (zorunlu):** Tam ekran bir feature'ın kökü `Scaffold` (ya da `Surface`) ile `MaterialTheme.colorScheme.background` kullanır. Styled bir container/kart içinde host edilen bir alt-composable, parent arka planının görünmesi için kendi background'unu set etmez (gerekliyse `Color.Transparent`), opaque overlay oluşturma. Canonical reference: `.claude/rules/ui-components.md` (Surface & Background).

> ⚠️ **Stateless Content Rule (zorunlu):** UI iki composable'a bölünür: **stateful** `<FeatureName>Screen` (ViewModel'i `hiltViewModel()` ile alır, state toplar, event dinler) ve **stateless** `<FeatureName>Content` (sadece `uiState` + lambda parametreleri alır, ViewModel/NavController referansı **içermez**, `@Preview`lenebilir). İş mantığı ve Firebase erişimi asla Composable içinde olmaz. Canonical reference: `.claude/rules/architecture.md` (State Hoisting) ve `templates/stateless-content.md`.

> ⚠️ **Back Button Rule (zorunlu):** Push edilen (geri dönülebilen) bir ekran üretiyorsan geri butonu **her zaman** Material3 `TopAppBar`'ın `navigationIcon`'u olur: `IconButton` + `Icons.AutoMirrored.Filled.ArrowBack` (ikon-only), tap → ekranın `onBack` lambda'sı → `navController.popBackStack()`. Elle/metinli ("< Geri") geri butonu veya içeriğe gömülü custom back **yasak**. **İstek/tasarım açıkça farklı bir geri davranışı belirtmedikçe sormadan bu şekilde ilerle.** İstisna: modal `ModalBottomSheet`/dialog (kapatma `IconButton` "✕" veya dışarı tıklama). Canonical reference: `.claude/rules/ui-components.md` (Navigation Back Button).

> ⚠️ **No-manual-scroll Rule (zorunlu):** Dikey/kaydırılabilir/liste/çok-kartlı içerik **her zaman** `LazyColumn` (veya grid için `LazyVerticalGrid`) ile üretilir — tek öğe olsa bile. `Column` + `Modifier.verticalScroll(...)` + elle yığılmış kartlar **kullanma**. Sebep: öğe geri dönüşümü (recycling), stabil `key`'ler, erişilebilirlik ve kod tabanı tutarlılığı. `verticalScroll` yalnızca gerçek non-liste durumlar içindir (ör. kısa statik bir form). Canonical reference: `.claude/rules/ui-components.md` (Scrollable Content).

> ⚠️ **Firebase Error Rule (zorunlu):** Repository, ham Firebase tiplerini (`DocumentSnapshot`/`DataSnapshot`/`FirebaseFirestoreException`) UI'ya **sızdırmaz**. DTO→domain `data class` map eder ve hataları `sealed class ArchiveError/AuthError` + Türkçe `userMessage`'a çevirir. ViewModel bunu yakalar ve `uiState.error`'a yazar; UI `error.userMessage` gösterir. `try/await()` + `Dispatchers.IO` zorunlu. Canonical reference: `.claude/rules/firebase-data.md` (Error Mapping).

> ⚠️ **testTag Rule (zorunlu):** Her etkileşimli/doğrulanan öğe `Modifier.testTag("<ekran>.<öğe>")` taşır (ör. `"examList.backButton"`). testTag UI testleriyle **sözleşmedir** — mevcutlarını değiştirme. İkonların `contentDescription`'ı Türkçe ve `strings.xml`'dedir. Canonical reference: `templates/accessibility.md`.

# New Feature Module (ViFi-Android)

Bu skill, tek komutla eksiksiz bir feature modülü iskeleti üretir: stateful **Screen**, stateless **Content**, **ViewModel** + **UiState**, bir **Repository arayüzü** (canlı Firebase + Fake impl) ve birim testleri. Üretilen kod **hedef** mimariye uyar: Kotlin 2.0+, Jetpack Compose + Material3, MVVM (`@HiltViewModel` + `StateFlow<UiState>`), Navigation-Compose, Coroutines/Flow, named `vifi` Firestore.

## Usage

```
/new-feature <FeatureName>
```

**Arguments:**
- `FeatureName` — PascalCase feature adı (ör. `ExamList`, `LessonList`, `ExamDetail`, `SubmissionForm`)

## Folder Structure Created

```
app/src/main/java/com/salticusteam/vifi/
├── feature/<featureName>/
│   ├── <FeatureName>Screen.kt          // stateful Composable + Hilt VM + event collection
│   ├── <FeatureName>Content.kt         // stateless Composable (state + lambda) + @Preview
│   ├── <FeatureName>ViewModel.kt       // @HiltViewModel + StateFlow<UiState> + Channel<Event>
│   ├── <FeatureName>UiState.kt         // tek immutable data class + Event sealed interface
│   └── <Item>ListItem.kt               // (opsiyonel) liste satırı composable'ı
└── core/
    ├── data/repository/
    │   ├── ArchiveRepository.kt        // interface (iOS protocol karşılığı)
    │   ├── FirebaseArchiveRepository.kt// canlı impl (DTO→domain map, hata→ArchiveError)
    │   └── FakeArchiveRepository.kt    // preview/test impl (iOS Mock karşılığı)
    ├── model/
    │   ├── <Item>.kt                   // plain domain data class
    │   └── ArchiveError.kt             // sealed class + Türkçe userMessage
    └── navigation/
        └── <FeatureName>Navigation.kt  // route + NavGraphBuilder extension

app/src/main/res/values/
└── strings.xml                         // <featureName>_* kullanıcı metinleri (+%1$s args)

app/src/test/java/com/salticusteam/vifi/feature/<featureName>/
└── <FeatureName>ViewModelTest.kt       // JUnit + coroutines-test + Turbine

app/src/androidTest/java/com/salticusteam/vifi/feature/<featureName>/
└── <FeatureName>ScreenTest.kt          // (opsiyonel) Compose UI test
```

> **Legacy not:** Eski `CourseExamListActivity` + `activity_course_exam_list.xml` + `CustomAdapter` üçlüsü, bu yapıda `ExamListScreen` + `ExamListContent` + `LazyColumn`/`ExamListItem`'a karşılık gelir. `PostClass`/RTDB okuması → `ArchiveRepository`.

## ViFi-Android Architecture

### ✅ Kullanılan (hedef desen)
- Stateful `Screen` Composable (`viewModel: X = hiltViewModel()`)
- Stateless `Content` Composable (state + lambda, `@Preview`lenebilir)
- `@HiltViewModel` ViewModel + tek `data class XUiState` + `StateFlow` (`MutableStateFlow` + `asStateFlow()`)
- Tek seferlik olaylar: `Channel`/`SharedFlow` (navigate, snackbar) — UI state değil
- Yükleme/hata: `UiState` içinde `isLoading: Boolean` + `error: String?` (proje genelinde **tek desen**, data class + alanlar tercih edilir)
- Repository arayüzü + `Firebase...Repository` (canlı) + `Fake...Repository` (preview/test)
- Named `vifi` Firestore (`catalogNodes`/`exams`) + Storage; `callbackFlow { ... awaitClose { ... } }` veya `Task.await()`
- Navigation-Compose (`NavHost`, route'lar), geri = `TopAppBar` navigationIcon + `popBackStack()`
- **`LazyColumn`/`LazyVerticalGrid`** kök scroll/list konteyneri olarak — tek öğe olsa bile
- Tarih dönüşümü merkezi util üzerinden — Composable/ViewModel içinde inline `SimpleDateFormat` **yok** (bkz. `.claude/rules/date-handling.md`)

### ❌ Kullanılmayan
- `Column` + `Modifier.verticalScroll` + elle yığılmış kartlar (uzun/dinamik listeler için `LazyColumn` kullan). İstisna: kısa statik form.
- Yeni ekranda `Activity`/`Fragment`/XML layout (legacy istisna)
- Composable/ViewModel'e ham Firebase tipi sızdırma (`DocumentSnapshot`, `DataSnapshot`)
- Composable içine hardcoded string/renk/font
- AdMob / reklam (parite için kaldırıldı); uygulama Türkçe-only

## Steps

1. **Parse** `FeatureName` (PascalCase) → `<featureName>` (camelCase) ve testTag/route prefix.
2. **Create** `feature/<featureName>/` paketini oluştur.
3. **Generate** çekirdek dosyalar (sırasıyla — alttan üste bağımlılık):
   - `<FeatureName>UiState.kt` (state + Event) — bkz. `templates/viewmodel.md`
   - `<FeatureName>ViewModel.kt` (`@HiltViewModel`) — bkz. `templates/viewmodel.md`
   - `<FeatureName>Content.kt` (stateless) — bkz. `templates/stateless-content.md`
   - `<FeatureName>Screen.kt` (stateful) — bkz. `templates/screen.md`
4. **Generate / reuse** veri katmanı:
   - `core/data/repository/ArchiveRepository.kt` (yoksa) + Firebase impl + Fake impl
   - `core/model/<Item>.kt` ve `core/model/ArchiveError.kt`
5. **Register** navigation route'u (`core/navigation/<FeatureName>Navigation.kt`) ve parent `NavHost`'a ekle.
6. **Add** kullanıcı metinlerini `res/values/strings.xml`'e (Türkçe, gerekirse `%1$s`/`%1$d`).
7. **Generate** birim testleri (Fake repository ile) — bkz. `.claude/skills/generate-unit-tests/SKILL.md`.
8. **Wire** erişilebilirlik: testTag + contentDescription — bkz. `templates/accessibility.md`.
9. **Report** üretilen tüm dosya yollarını.

## Generated Files

Her dosyanın tam/ayrıntılı template'i `templates/` altındadır. Aşağıda kanonik, derlenebilir özetler verilir (örnek feature: `ExamList`, liste öğesi `Exam`, detay ekranı `ExamDetail`).

### 1. UiState + Event

**File**: `feature/examList/ExamListUiState.kt`

```kotlin
package com.salticusteam.vifi.feature.examlist

import com.salticusteam.vifi.core.model.Exam

// Single immutable state for the Exam List screen (iOS: delegate callbacks → fields).
data class ExamListUiState(
    val isLoading: Boolean = false,
    val exams: List<Exam> = emptyList(),
    val error: String? = null,
) {
    // Empty state is derived, never stored, to keep a single source of truth.
    val isEmpty: Boolean get() = !isLoading && error == null && exams.isEmpty()
}

// One-shot events — navigation/snackbar. NOT part of UiState (never re-delivered on recomposition).
sealed interface ExamListEvent {
    data class OpenExamDetail(val examId: String) : ExamListEvent
    data class ShowMessage(val message: String) : ExamListEvent
    // Added in 3.0.0 — locked-exam signal; the screen resolves the text from strings.xml (no copy in the ViewModel).
    data object ExamLocked : ExamListEvent
}
```

### 2. ViewModel (`@HiltViewModel`)

**File**: `feature/examList/ExamListViewModel.kt`

```kotlin
package com.salticusteam.vifi.feature.examlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.Exam
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExamListViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Route arg (exam_list/{lessonId}). iOS: equivalent of init(dataModel:).
    private val lessonId: String = checkNotNull(savedStateHandle["lessonId"])

    private val _uiState = MutableStateFlow(ExamListUiState())
    val uiState: StateFlow<ExamListUiState> = _uiState.asStateFlow()

    private val _events = Channel<ExamListEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        loadExams()
    }

    fun loadExams() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        // viewModelScope cancels on clear → no leak (iOS [weak self] equivalent).
        viewModelScope.launch {
            try {
                val exams = archiveRepository.getExams(lessonId = lessonId)
                _uiState.update { it.copy(isLoading = false, exams = exams) }
            } catch (error: ArchiveError) {
                _uiState.update { it.copy(isLoading = false, error = error.userMessage) }
            }
        }
    }

    fun onExamClick(exam: Exam) {
        viewModelScope.launch {
            if (exam.isLocked) {
                _events.send(ExamListEvent.ExamLocked) // no user-facing text held in the ViewModel
            } else {
                _events.send(ExamListEvent.OpenExamDetail(exam.id))
            }
        }
    }
}
```

> Detaylı varyantlar (pagination, CRUD, `callbackFlow` ile realtime dinleme) için: `templates/viewmodel.md`.

### 3. Stateless Content

**File**: `feature/examList/ExamListContent.kt` — tam versiyon: `templates/stateless-content.md`.

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
                uiState.error != null -> ExamListError(
                    message = uiState.error, onRetry = onRetry,
                    modifier = Modifier.align(Alignment.Center),
                )
                uiState.isEmpty -> ExamListEmptyState(modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("examList.list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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

### 4. Stateful Screen + NavHost kaydı

**File**: `feature/examList/ExamListScreen.kt` — tam versiyon: `templates/screen.md`.

```kotlin
@Composable
fun ExamListScreen(
    onNavigateToExamDetail: (examId: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ExamListViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Resolve user-facing text in the UI layer; the ViewModel only emits a semantic signal.
    val lockedMessage = stringResource(R.string.exam_locked)

    // One-shot events; keyed to lifecycle so they fire once.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ExamListEvent.OpenExamDetail -> onNavigateToExamDetail(event.examId)
                is ExamListEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is ExamListEvent.ExamLocked -> snackbarHostState.showSnackbar(lockedMessage)
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

**File**: `core/navigation/ExamListNavigation.kt`

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

### 5. Repository (iOS Service/Protocol karşılığı)

**Files**: `core/data/repository/ArchiveRepository.kt`, `FirebaseArchiveRepository.kt`, `FakeArchiveRepository.kt`

```kotlin
// ArchiveRepository.kt — never leaks raw Firebase types to the UI (iOS ArchiveServicing protocol).
interface ArchiveRepository {
    suspend fun getExams(lessonId: String): List<Exam>
    fun observeExams(lessonId: String): Flow<List<Exam>>   // realtime variant
}
```

```kotlin
// FirebaseArchiveRepository.kt — DTO→domain map, error→ArchiveError, Dispatchers.IO + await().
class FirebaseArchiveRepository @Inject constructor(
    @Named("vifi") private val firestore: FirebaseFirestore,
) : ArchiveRepository {

    override suspend fun getExams(lessonId: String): List<Exam> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("exams")
                .whereEqualTo("courseId", lessonId)
                .whereEqualTo("status", "published")
                .get()
                .await() // kotlinx-coroutines-play-services
                .documents
                .mapNotNull { it.toExam() } // extension: DTO→domain
        } catch (e: FirebaseFirestoreException) {
            throw e.toArchiveError() // sealed ArchiveError + inline Turkish userMessage
        }
    }

    override fun observeExams(lessonId: String): Flow<List<Exam>> = callbackFlow {
        val query = firestore.collection("exams")
            .whereEqualTo("courseId", lessonId)
            .whereEqualTo("status", "published")
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) { close(error.toArchiveError()); return@addSnapshotListener }
            trySend(snapshot?.documents?.mapNotNull { it.toExam() } ?: emptyList())
        }
        awaitClose { registration.remove() } // listener removed — no leak
    }.flowOn(Dispatchers.IO)
}
```

```kotlin
// FakeArchiveRepository.kt — preview & test (iOS MockArchiveService equivalent).
class FakeArchiveRepository @Inject constructor() : ArchiveRepository {
    var examsToReturn: List<Exam> = emptyList()
    var errorToThrow: ArchiveError? = null

    override suspend fun getExams(lessonId: String): List<Exam> {
        errorToThrow?.let { throw it }
        return examsToReturn
    }

    override fun observeExams(lessonId: String): Flow<List<Exam>> = flowOf(examsToReturn)
}
```

> Repository'nin Hilt'e bağlanması (`@Binds`/`@Provides`, named `vifi` Firestore provider) `.claude/rules/firebase-data.md` ve `.claude/skills/generate-mocks/SKILL.md`'de anlatılır.

### 6. Domain model + Error

**Files**: `core/model/Exam.kt`, `core/model/ArchiveError.kt`

```kotlin
// Exam.kt — plain domain model (not a Firebase type).
data class Exam(
    val id: String,
    val title: String,
    val courseId: String,
    val kind: String, // "pdf" | "image"
    val year: Int? = null,
    val isLocked: Boolean = false, // Added in 3.0.0 — exam locked behind the coin economy.
)
```

```kotlin
// ArchiveError.kt — UI shows 'userMessage'. Turkish-only app → inline Turkish text
// (deliberate, narrow exception that does not require @StringRes).
sealed class ArchiveError(val userMessage: String) : Exception() {
    data object Offline : ArchiveError("İnternet bağlantısı yok. Lütfen bağlantını kontrol et.")
    data object NotFound : ArchiveError("İçerik bulunamadı.")
    data object Empty : ArchiveError("Burada henüz içerik yok.")
    data object PermissionDenied : ArchiveError("Bu içeriğe erişim izniniz yok.")
    data object AppCheckRejected : ArchiveError("Güvenlik doğrulaması başarısız. Lütfen tekrar dene.")
    data object Unknown : ArchiveError("Bir şeyler ters gitti. Lütfen tekrar dene.")
}

// Firebase exception → ArchiveError mapper.
fun FirebaseFirestoreException.toArchiveError(): ArchiveError = when (code) {
    FirebaseFirestoreException.Code.UNAVAILABLE -> ArchiveError.Offline
    FirebaseFirestoreException.Code.NOT_FOUND -> ArchiveError.NotFound
    FirebaseFirestoreException.Code.PERMISSION_DENIED -> ArchiveError.PermissionDenied
    else -> ArchiveError.Unknown
}
```

### 7. Unit Tests (Fake repository + Turbine)

**File**: `app/src/test/java/com/salticusteam/vifi/feature/examlist/ExamListViewModelTest.kt`

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class ExamListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeArchiveRepository

    private fun savedState() = SavedStateHandle(mapOf("lessonId" to "math101"))
    private fun sampleExam() = Exam("e1", "Vize 2023", "math101", "pdf", 2023)

    @Before fun setUp() { Dispatchers.setMain(testDispatcher); repository = FakeArchiveRepository() }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `loadExams success emits loading then exams`() = runTest {
        repository.examsToReturn = listOf(sampleExam())
        val viewModel = ExamListViewModel(repository, savedState())

        viewModel.uiState.test {
            assertEquals(true, awaitItem().isLoading) // init → loading
            val loaded = awaitItem()
            assertEquals(false, loaded.isLoading)
            assertEquals(1, loaded.exams.size)
            assertNull(loaded.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadExams failure emits error message`() = runTest {
        repository.errorToThrow = ArchiveError.Offline
        val viewModel = ExamListViewModel(repository, savedState())

        viewModel.uiState.test {
            skipItems(1) // loading
            val errored = awaitItem()
            assertEquals(ArchiveError.Offline.userMessage, errored.error)
            assertEquals(false, errored.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

## Placeholder Replacements

| Placeholder | Replace With | Example |
|-------------|--------------|---------|
| `<FeatureName>` | Feature adı (PascalCase) | `ExamList`, `LessonList`, `SubmissionForm` |
| `<featureName>` | Feature adı (camelCase) — paket + testTag prefix | `examList`, `lessonList` |
| `<feature>` | strings/route prefix (snake_case) | `exam_list`, `lesson_list` |
| `<Item>` | Liste öğesi modeli (PascalCase) | `Exam`, `Lesson`, `Department` |
| `<Detail>` | Detay ekranı | `ExamDetail`, `LessonDetail` |

## Post-Generation Tasks

1. **strings.xml kayıtları** (Türkçe, kullanıcıya görünen her metin; placeholder için numaralı `%1$s`):
   ```xml
   <string name="exam_list_title">Sınavlar</string>
   <string name="exam_list_empty">Bu ders için henüz sınav yok.</string>
   <string name="exam_list_count">%1$d sınav</string>
   <string name="exam_locked">Bu sınav kilitli.</string>
   <string name="cd_navigate_back">Geri</string>
   <string name="action_retry">Tekrar dene</string>
   <string name="error_network">İnternet bağlantısı yok. Lütfen bağlantını kontrol et.</string>
   <string name="error_not_found">İçerik bulunamadı.</string>
   <string name="error_permission">Bu içeriğe erişim izniniz yok.</string>
   <string name="error_general">Bir şeyler ters gitti. Lütfen tekrar dene.</string>
   ```
   Compose'da: `stringResource(R.string.exam_list_count, exams.size)`. Detay: `.claude/rules/localization.md`.

2. **Repository'yi Hilt'e bağla** (yeni kaynak eklediysen): `@Module` içinde `@Binds abstract fun bindArchiveRepository(impl: FirebaseArchiveRepository): ArchiveRepository` + named `vifi` Firestore `@Provides`.

3. **NavHost'a ekle** (parent navigation graph):
   ```kotlin
   NavHost(navController, startDestination = /* ... */) {
       examListScreen(
           onNavigateToExamDetail = { examId -> navController.navigateToExamDetail(examId) },
           onNavigateBack = { navController.popBackStack() },
       )
       // ...
   }
   ```

4. **Testleri çalıştır**:
   ```bash
   ./gradlew :app:testDebugUnitTest          # birim (JUnit + Turbine)
   ./gradlew :app:connectedDebugAndroidTest  # Compose UI testleri
   ```

## Example

```
/new-feature LessonList
```
**Creates**:
```
feature/lessonList/
├── LessonListScreen.kt
├── LessonListContent.kt
├── LessonListViewModel.kt
├── LessonListUiState.kt
└── LessonListItem.kt
core/navigation/LessonListNavigation.kt
app/src/test/java/.../lessonlist/LessonListViewModelTest.kt
```
(Varsa `ArchiveRepository` + `core/model/Lesson.kt` yeniden kullanılır, yoksa üretilir.)

## Verification Checklist

Üretimden sonra doğrula:
- [ ] `Screen` stateful: `hiltViewModel()` alır, `collectAsStateWithLifecycle()` + `LaunchedEffect` ile event dinler
- [ ] `Content` stateless: sadece `uiState` + lambda alır, ViewModel/NavController referansı **yok**, `@Preview`lenebilir
- [ ] `@HiltViewModel class XViewModel @Inject constructor(...)` + `SavedStateHandle` route arg'ları için
- [ ] Tek `data class XUiState` + `StateFlow` (`MutableStateFlow` + `asStateFlow()`); `isLoading`/`error` deseni
- [ ] Tek seferlik olaylar `Channel`/`SharedFlow` ile (UI state'te değil)
- [ ] `viewModelScope.launch` + Firebase/IO `Dispatchers.IO`; `Task.await()` veya `callbackFlow { awaitClose { ... } }`
- [ ] Repository arayüz arkasında; `Firebase...Repository` + `Fake...Repository`; ham Firebase tipi sızmıyor
- [ ] DTO→domain map; domain modeller plain `data class`; eklenen alanda `// Added in <versionName> — ...`
- [ ] Hatalar `sealed class ArchiveError` + inline Türkçe `userMessage` (kanonik case kümesi)
- [ ] Kullanıcı metinleri `strings.xml`'de + `stringResource(...)`; hardcoded string yok
- [ ] Renk `MaterialTheme.colorScheme`, tipografi `MaterialTheme.typography`; hardcoded yok
- [ ] Geri = `TopAppBar` navigationIcon (ikon-only) + `popBackStack()`; metinli geri yok
- [ ] Liste `LazyColumn`/`LazyVerticalGrid` + stabil `key`
- [ ] Her etkileşimli öğede `Modifier.testTag("<ekran>.<öğe>")`; ikonlarda Türkçe `contentDescription`
- [ ] NavHost route'u kaydedildi; birim testleri Fake ile yazıldı

---

**See Also:**
- `templates/screen.md` — stateful Screen + NavHost kaydı
- `templates/stateless-content.md` — stateless Content + `@Preview`
- `templates/viewmodel.md` — ViewModel + UiState desenleri (pagination, CRUD, realtime)
- `templates/accessibility.md` — testTag / semantics / dinamik yazı ölçeği
- `.claude/skills/new-screen/SKILL.md` — tek ekran üretimi
- `.claude/skills/new-viewmodel/SKILL.md` — tek ViewModel üretimi
- `.claude/skills/new-list-item/SKILL.md` — liste satırı composable'ı
- `.claude/skills/generate-unit-tests/SKILL.md` — birim test üretimi
- `.claude/skills/generate-ui-tests/SKILL.md` — Compose UI test üretimi
- `.claude/skills/generate-mocks/SKILL.md` — Fake/mock üretimi (repository, Hilt binding)
- `.claude/rules/architecture.md` — ViFi hedef mimari genel bakış
- `.claude/rules/ui-components.md` — Compose UI desenleri
- `.claude/rules/firebase-data.md` — Firestore/RTDB erişimi + hata eşleme
- `.claude/rules/viewmodel-rule.md` — ViewModel/UiState kuralları
- `.claude/rules/localization.md` — strings.xml + positional args
- `.claude/rules/date-handling.md` — tarih dönüşümü (ISO 8601 / Firestore Timestamp)
