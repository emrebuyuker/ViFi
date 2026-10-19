---
description: ViFi-Android konvansiyonlarına uyan eksiksiz bir feature modülü iskeleti üretir — stateful Screen + stateless Content + ViewModel + UiState + (opsiyonel) liste satırı + @Preview + birim testi.
argument-hint: "[FeatureName]  (ör. ExamList)"
---

# Command: Scaffold Feature

ViFi-Android konvansiyonlarına uyan eksiksiz bir **feature modülü** iskeleti üretir: stateful `Screen` + stateless `Content` + `ViewModel` + `UiState` + (opsiyonel) liste satırı + `@Preview` + birim testi. Komut, işi `new-feature` skill'ine devreder (`.claude/skills/new-feature/SKILL.md`); bu dosya skill'i tetikleyen, argümanları toplayan ve ne üretildiğini özetleyen ince bir orkestratördür.

Üretilen her şey **hedef** mimariye uyar: Kotlin 2.0+ / JDK 17, Jetpack Compose + Material3, MVVM (`@HiltViewModel` + `StateFlow<UiState>`), Navigation-Compose, Coroutines/Flow, Repository arayüzü + named `vifi` Firestore. Paket: `com.salticusteam.vifi`.

> **Legacy not:** Bugünkü Android (v2.5) karşılığı elde "scaffold" yoktu; yeni ekran demek `XActivity` + `activity_x.xml` + `CustomAdapter`/`ListViewAdapter` + RTDB `Universitiess` okumasını elle kopyalamaktı. Bu komut **yeni/ileriye dönük** feature'lar içindir ve **hedef** iskeleti üretir. Göçteki yeri: `ANDROID-PARITY-ROADMAP.md` → Faz 1 (mimari iskelet). Legacy `Activity`/`Fragment`/XML yeni ekranlarda kullanılmaz.

## Usage

```
/scaffold-feature
```

veya argümanla doğrudan:

```
/scaffold-feature ExamList
```

## Interactive Prompts

Komut şunları sorar (argüman verilmediyse):

1. **Feature Name** (PascalCase): ör. `ExamDetail`, `LessonList`
2. **Feature Category**: `Archive/Browse` (University/Faculty/Department/Lesson gezinme), `Exam` (sınav listesi/detay/görüntüleyici), `Auth` (Phone Auth +90), `User Account` (profil/coin), `Submission` (sınav yükleme/katkı)
3. **Has list? (LazyColumn)**: Evet → stateless `Content` içine `LazyColumn` + bir **liste satırı composable** (`<Item>ListItem.kt`) üretir
4. **Has data integration? (Firebase)**: Evet → `Repository` arayüzü + `Firebase...Repository` + `Fake...Repository` + domain `data class` üretir
5. **Has navigation? (push)**: Evet → `TopAppBar` + ikon-only geri (`navigationIcon`) + route'u (`core/navigation/<FeatureName>Navigation.kt`) üretir

> **Legacy not:** iOS'taki "CollectionView/TableView var mı?" sorusunun Android karşılığı "`LazyColumn` var mı?"dır — `UICollectionViewCell` yerine bir **liste satırı composable**'ı üretilir. "API entegrasyonu var mı?" sorusu, REST/Alamofire yerine **Firebase (named `vifi` Firestore) Repository** üretimine çevrilir; ViFi'de ayrı bir backend yoktur, Firestore + Storage kullanılır.

## What Gets Created

### Folder Structure

```
app/src/main/java/com/salticusteam/vifi/feature/<featureName>/
├── <FeatureName>Screen.kt          // stateful Composable: hiltViewModel() + state/event toplama
├── <FeatureName>Content.kt         // stateless Composable: uiState + lambda, @Preview'lenebilir
├── <FeatureName>ViewModel.kt       // @HiltViewModel + StateFlow<UiState> + Channel<Event>
├── <FeatureName>UiState.kt         // tek immutable data class + Event sealed interface
└── <Item>ListItem.kt               // (liste varsa) tek satır composable'ı
```

### Supporting Files (veri entegrasyonu varsa)

```
app/src/main/java/com/salticusteam/vifi/core/
├── data/repository/
│   ├── ArchiveRepository.kt         // interface — UI'ya ham Firebase tipi SIZDIRMAZ
│   ├── FirebaseArchiveRepository.kt // canlı impl: DTO→domain map, hata→ArchiveError, Dispatchers.IO + await()
│   └── FakeArchiveRepository.kt     // preview/test impl
├── model/
│   ├── <Item>.kt                    // plain domain data class
│   └── ArchiveError.kt              // sealed class + Türkçe userMessage
└── navigation/
    └── <FeatureName>Navigation.kt   // route + NavController/NavGraphBuilder extension

app/src/main/res/values/
└── strings.xml                      // <feature>_* kullanıcı metinleri (Türkçe, gerekirse %1$s/%1$d)
```

> **Legacy not:** iOS'un `Sources/Networking/Services/*Service.swift` + `Models/Request|Response/*` üçlüsünün karşılığı Android'de `core/data/repository/` (arayüz + Firebase impl + Fake) + `core/model/`'dir. REST request/response modeli yoktur; Firestore DTO'su repository içinde domain modele map edilir.

### Test Files (opsiyonel)

```
app/src/test/java/com/salticusteam/vifi/feature/<featureName>/
└── <FeatureName>ViewModelTest.kt    // JUnit + kotlinx-coroutines-test + Turbine, Fake repository ile

app/src/androidTest/java/com/salticusteam/vifi/feature/<featureName>/
└── <FeatureName>ScreenTest.kt       // (opsiyonel) Compose UI test (createAndroidComposeRule)
```

> **Legacy not:** iOS `*ViewModelTests.swift` + `Mock*Delegate.swift` ikilisinin karşılığı Android'de `*ViewModelTest.kt` (JUnit + Turbine) + `Fake*Repository`'dir. Mock delegate yoktur; state `StateFlow` ile Turbine üzerinden doğrulanır (bkz. `.claude/skills/generate-unit-tests/SKILL.md`).

## Process

1. Feature gereksinimlerini interaktif topla (ya da argümandan al).
2. `new-feature` skill'ini çağır (`.claude/skills/new-feature/SKILL.md`) ve aşağıdaki sırayla üret (alttan üste bağımlılık):
3. `<FeatureName>UiState.kt` — tek `data class` + `Event sealed interface` üret.
4. `<FeatureName>ViewModel.kt` — `@HiltViewModel` + `StateFlow<UiState>` + `Channel<Event>` üret.
5. `<FeatureName>Content.kt` — stateless Composable (state + lambda) + `@Preview` üret.
6. `<FeatureName>Screen.kt` — stateful Composable (`hiltViewModel()` + event toplama) üret.
7. Liste istendiyse `<Item>ListItem.kt` üret (`.claude/skills/new-list-item/SKILL.md`).
8. Veri entegrasyonu istendiyse `Repository` arayüzü + `Firebase...`/`Fake...` impl + domain model + `ArchiveError` üret/yeniden kullan.
9. Navigation istendiyse route'u üret ve parent `NavHost`'a kaydet.
10. Kullanıcı metinlerini `res/values/strings.xml`'e ekle (Türkçe; placeholder için numaralı `%1$s`/`%1$d`).
11. Birim testlerini (Fake repository ile) üret.
12. Üretilen tüm dosya yollarını ve entegrasyon adımlarını raporla.

## Generated Files Content

Aşağıda kanonik, **derlenebilir** özetler verilir (örnek feature: `ExamList`, liste öğesi `Exam`). Her dosyanın tam/ayrıntılı template'i `new-feature` skill'inin `templates/` klasöründedir: `templates/screen.md`, `templates/stateless-content.md`, `templates/viewmodel.md`, `templates/accessibility.md`. Somut `ExamList`/`Exam` adlarını kendi feature'ınla değiştirmek için en alttaki **Placeholder Replacements** tablosuna bak.

### Screen (stateful Composable)

```kotlin
//
//  ExamListScreen.kt
//  com.salticusteam.vifi.feature.examlist
//

package com.salticusteam.vifi.feature.examlist

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// Stateful host: owns the ViewModel, collects state + one-shot events.
// iOS LayoutingViewController (setupDelegates / viewDidLoad) karşılığı.
@Composable
fun ExamListScreen(
    onNavigateToExamDetail: (examId: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ExamListViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // One-shot events; fire once, never re-delivered on recomposition.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ExamListEvent.OpenExamDetail -> onNavigateToExamDetail(event.examId)
                is ExamListEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
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

### Content (stateless Composable)

```kotlin
//
//  ExamListContent.kt
//  com.salticusteam.vifi.feature.examlist
//

package com.salticusteam.vifi.feature.examlist

import androidx.compose.foundation.layout.* // Box, PaddingValues, Arrangement, fillMaxSize, padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.model.Exam

// Stateless: only uiState + lambdas. No ViewModel / NavController reference → previewable.
// Equivalent of the iOS LayoutableView (setupViews / setupLayout), but state is hoisted from outside.
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
                    // Back button = TopAppBar navigationIcon (icon-only). Equivalent of iOS safePop.
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
                    message = uiState.error,
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.Center),
                )
                uiState.isEmpty -> Text(
                    text = stringResource(R.string.exam_list_empty),
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("examList.list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Stable key → recycling + accessibility. Equivalent of iOS registerCell/dequeue.
                    items(items = uiState.exams, key = { it.id }) { exam ->
                        ExamListItem(exam = exam, onClick = { onExamClick(exam) })
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExamListContentPreview() {
    ExamListContent(
        uiState = ExamListUiState(
            exams = listOf(
                Exam("e1", "math101", "Vize 2023", 2023, "Güz", "url"),
                Exam("e2", "math101", "Final 2023", 2023, "Güz", "url"),
            ),
        ),
        onExamClick = {},
        onRetry = {},
        onBack = {},
    )
}
```

### ViewModel (`@HiltViewModel`)

```kotlin
//
//  ExamListViewModel.kt
//  com.salticusteam.vifi.feature.examlist
//

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

// iOS ViewModel + delegate protokolü → @HiltViewModel + StateFlow<UiState> + Channel<Event>.
@HiltViewModel
class ExamListViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository, // interface — constructor injection
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Route arg (exam_list/{lessonId}). iOS init(dataModel:) karşılığı.
    private val lessonId: String = checkNotNull(savedStateHandle["lessonId"])

    private val _uiState = MutableStateFlow(ExamListUiState())
    val uiState: StateFlow<ExamListUiState> = _uiState.asStateFlow()

    // One-shot events (navigate/snackbar) → Channel, NOT UiState.
    private val _events = Channel<ExamListEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        loadExams()
    }

    fun loadExams() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        // viewModelScope cancels on clear → no leak. iOS [weak self] karşılığı.
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
                _events.send(ExamListEvent.ShowMessage(/* resolve from strings.xml in Content */ ""))
            } else {
                _events.send(ExamListEvent.OpenExamDetail(exam.id))
            }
        }
    }
}
```

**UiState + Event** (`ExamListUiState.kt`):

```kotlin
package com.salticusteam.vifi.feature.examlist

import com.salticusteam.vifi.core.model.Exam

// Single immutable state. iOS delegate callbacks (didSuccessLoad/didFailure) → fields.
data class ExamListUiState(
    val isLoading: Boolean = false,
    val exams: List<Exam> = emptyList(),
    val error: String? = null,
) {
    // Derived, never stored — single source of truth.
    val isEmpty: Boolean get() = !isLoading && error == null && exams.isEmpty()
}

// One-shot events. iOS delegate'in navigate/alert çağrıları karşılığı.
sealed interface ExamListEvent {
    data class OpenExamDetail(val examId: String) : ExamListEvent
    data class ShowMessage(val message: String) : ExamListEvent
}
```

### Repository (veri entegrasyonu varsa)

```kotlin
//
//  ArchiveRepository.kt (+ FirebaseArchiveRepository / FakeArchiveRepository)
//  com.salticusteam.vifi.core.data.repository
//

package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.Exam
import kotlinx.coroutines.flow.Flow

// iOS ArchiveServicing protokolü karşılığı. UI'ya ham Firebase tipi (DocumentSnapshot) SIZDIRMAZ.
interface ArchiveRepository {
    suspend fun getExams(lessonId: String): List<Exam>
    fun observeExams(lessonId: String): Flow<List<Exam>>   // realtime varyant (callbackFlow)
}
```

```kotlin
// FirebaseArchiveRepository.kt — canlı impl. iOS FirebaseArchiveService karşılığı.
class FirebaseArchiveRepository @Inject constructor(
    @Named("vifi") private val firestore: FirebaseFirestore, // named vifi Firestore
) : ArchiveRepository {

    override suspend fun getExams(lessonId: String): List<Exam> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("exams")
                .whereEqualTo("courseId", lessonId)
                .whereEqualTo("status", "published")
                .get()
                .await() // kotlinx-coroutines-play-services — iOS async/await karşılığı
                .documents
                .mapNotNull { it.toExam() } // DTO→domain map (extension)
        } catch (e: FirebaseFirestoreException) {
            throw e.toArchiveError() // sealed ArchiveError + inline Türkçe userMessage
        }
    }

    override fun observeExams(lessonId: String): Flow<List<Exam>> = callbackFlow {
        val registration = firestore.collection("exams")
            .whereEqualTo("courseId", lessonId)
            .whereEqualTo("status", "published")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error.toArchiveError()); return@addSnapshotListener }
                trySend(snapshot?.documents?.mapNotNull { it.toExam() } ?: emptyList())
            }
        awaitClose { registration.remove() } // listener kaldırılır — sızıntı yok
    }.flowOn(Dispatchers.IO)
}
```

```kotlin
// FakeArchiveRepository.kt — preview & test. iOS MockArchiveService karşılığı.
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

Domain model + hata (`core/model/`):

```kotlin
// Exam.kt — plain domain model (Firebase tipi değil).
data class Exam(
    val id: String,
    val lessonId: String,
    val title: String,
    val year: Int,
    val term: String,
    val fileUrl: String,
    val isLocked: Boolean = false, // Added in 3.0.0 — coin ekonomisi ile kilitli sınav.
)

// ArchiveError.kt — UI shows 'userMessage'; inline Turkish string (narrow, deliberate exception to strings.xml).
sealed class ArchiveError(val userMessage: String) : Exception() {
    data object Offline : ArchiveError("İnternet bağlantısı yok. Lütfen bağlantınızı kontrol edip tekrar deneyin.")
    data object NotFound : ArchiveError("Aradığınız içerik bulunamadı.")
    data object Empty : ArchiveError("Burada henüz içerik yok.")
    data object PermissionDenied : ArchiveError("Bu içeriğe erişim izniniz yok.")
    data object AppCheckRejected : ArchiveError("Uygulama doğrulaması başarısız oldu. Lütfen uygulamayı güncelleyip tekrar deneyin.")
    data object Unknown : ArchiveError("Beklenmeyen bir hata oluştu. Lütfen tekrar deneyin.")
}
```

> **Legacy not:** Güvenlik kuralları ve Cloud Functions Android'de **değil**, `Esim-iOS/firebase/`de tanımlıdır; Android bu backend'i **yeniden kullanır**, yeni backend yazmaz. Repository sadece named `vifi` Firestore + Storage'a bağlanır. Repository'nin Hilt'e bağlanması (`@Binds`/`@Provides`, named `vifi` Firestore provider) `.claude/rules/firebase-data.md` ve `.claude/skills/generate-mocks/SKILL.md`'de anlatılır.

### List Item (liste varsa)

```kotlin
//
//  ExamListItem.kt
//  com.salticusteam.vifi.feature.examlist
//

package com.salticusteam.vifi.feature.examlist

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.model.Exam

// iOS ExamCollectionViewCell karşılığı — tek liste satırı composable'ı (stateless).
@Composable
fun ExamListItem(
    exam: Exam,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("examList.item.${exam.id}"), // testTag UI testleriyle sözleşme
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Tipografi MaterialTheme.typography'den; renk MaterialTheme.colorScheme'den — hardcoded yok.
            Text(text = exam.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.exam_list_item_subtitle, exam.term, exam.year),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

> Tam varyant (RecyclerView legacy köprüsü dahil) için: `.claude/skills/new-list-item/SKILL.md`.

### Unit Test (Fake repository + Turbine)

```kotlin
//
//  ExamListViewModelTest.kt
//  app/src/test/java/com/salticusteam/vifi/feature/examlist
//

@OptIn(ExperimentalCoroutinesApi::class)
class ExamListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeArchiveRepository

    private fun savedState() = SavedStateHandle(mapOf("lessonId" to "math101"))
    private fun sampleExam() = Exam("e1", "math101", "Vize 2023", 2023, "Güz", "url")

    @Before fun setUp() { Dispatchers.setMain(testDispatcher); repository = FakeArchiveRepository() }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `loadExams success emits loading then exams`() = runTest {
        repository.examsToReturn = listOf(sampleExam())
        val viewModel = ExamListViewModel(repository, savedState())

        viewModel.uiState.test { // Turbine
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
            assertEquals(ArchiveError.Offline.userMessage, awaitItem().error)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

## Placeholder Replacements

| Placeholder | Replace With | Example |
|-------------|--------------|---------|
| `<FeatureName>` | Feature adı (PascalCase) | `ExamList`, `LessonList`, `ExamDetail` |
| `<featureName>` | Feature adı (camelCase) — paket + testTag prefix | `examList`, `lessonList` |
| `<feature>` | strings/route prefix (snake_case) | `exam_list`, `lesson_list` |
| `<Item>` | Liste öğesi modeli (PascalCase) | `Exam`, `Lesson`, `Department` |

## Output Example

```
User: /scaffold-feature

Claude: Feature adı? (PascalCase)
User: LessonList

Claude: Hangi kategori?
1. Archive/Browse
2. Exam
3. Auth
4. User Account
5. Submission
User: 1 (Archive/Browse)

Claude: Liste var mı? (LazyColumn)
User: Evet

Claude: Firebase veri entegrasyonu var mı?
User: Evet

Claude: Navigation (push) var mı?
User: Evet

Claude: Birim testleri üretilsin mi?
User: Evet

Claude: LessonList feature'ı üretiliyor... (new-feature skill'i)

Üretilen dosyalar:

Feature katmanı:
  feature/lessonList/LessonListScreen.kt
  feature/lessonList/LessonListContent.kt      (+ @Preview)
  feature/lessonList/LessonListViewModel.kt
  feature/lessonList/LessonListUiState.kt
  feature/lessonList/LessonListItem.kt

Core (veri):
  core/data/repository/ArchiveRepository.kt     (yeniden kullanıldı)
  core/model/Lesson.kt
  core/navigation/LessonListNavigation.kt

Kaynak:
  res/values/strings.xml                        (lesson_list_* eklendi)

Test:
  app/src/test/java/.../lessonlist/LessonListViewModelTest.kt

Entegrasyon rehberi:

1. NavHost'a kaydet (parent navigation graph):
   lessonListScreen(
       onNavigateToExamList = { lessonId -> navController.navigateToExamList(lessonId) },
       onNavigateBack = { navController.popBackStack() },
   )

2. Repository'yi Hilt'e bağla (yeni kaynak eklendiyse):
   @Binds abstract fun bindArchiveRepository(impl: FirebaseArchiveRepository): ArchiveRepository
   + named "vifi" Firestore @Provides

3. Backend: güvenlik kuralları/Functions Esim-iOS/firebase/'de — Android yeniden kullanır, yeni backend yazmaz.

4. Testleri çalıştır:
   ./gradlew :app:testDebugUnitTest

Feature iskeleti hazır. Toplam dosya: 9
```

## Checklist

İskeleden sonra doğrula:

- [ ] `Screen` **stateful**: `hiltViewModel()` alır, `collectAsStateWithLifecycle()` + `LaunchedEffect` ile event dinler
- [ ] `Content` **stateless**: sadece `uiState` + lambda alır, ViewModel/NavController referansı **yok**, `@Preview`lenebilir
- [ ] `@HiltViewModel class XViewModel @Inject constructor(...)` + route arg'ları için `SavedStateHandle`
- [ ] Tek `data class XUiState` + `StateFlow` (`MutableStateFlow` + `asStateFlow()`); `isLoading`/`error` deseni
- [ ] Tek seferlik olaylar `Channel`/`SharedFlow` ile (UI state'te değil)
- [ ] `viewModelScope.launch` + Firebase/IO `Dispatchers.IO`; `Task.await()` veya `callbackFlow { awaitClose { ... } }`
- [ ] Repository bir `interface` arkasında; `Firebase...Repository` + `Fake...Repository`; ham Firebase tipi sızmıyor
- [ ] DTO→domain map; domain modeller plain `data class`; eklenen alanda `// Added in <versionName> — ...`
- [ ] Hatalar `sealed class ArchiveError` + inline Türkçe `userMessage` (dar, bilinçli istisna)
- [ ] Kullanıcıya görünen her metin `strings.xml`'de + `stringResource(...)`; hardcoded string **yok** (placeholder = numaralı `%1$s`)
- [ ] Renk `MaterialTheme.colorScheme`, tipografi `MaterialTheme.typography`; hardcoded `Color(0x...)`/`fontSize` **yok**
- [ ] Geri = `TopAppBar` navigationIcon (ikon-only) + `popBackStack()`; metinli/custom geri **yok**
- [ ] Liste `LazyColumn`/`LazyVerticalGrid` + stabil `key`
- [ ] Her etkileşimli öğede `Modifier.testTag("<ekran>.<öğe>")`; ikonlarda Türkçe `contentDescription`
- [ ] NavHost route'u kaydedildi; birim testleri Fake repository ile yazıldı (JUnit + coroutines-test + Turbine)
- [ ] Reklam/AdMob **yok**; uygulama Türkçe-only

## Advanced Options

### Testleri atla

```
/scaffold-feature ExamDetail --no-tests
```

### Minimal (liste yok, veri yok)

```
/scaffold-feature ProfileSettings --minimal
```
Sadece `Screen` + `Content` + `ViewModel` + `UiState` üretir (Repository/liste satırı/test yok).

### Birden çok ekran

```
Claude: Bu feature'da kaç ekran var?
User: 3

Claude: Ekran adları? (virgülle)
User: LessonList, ExamList, ExamDetail

Üretir:
- feature/lessonList/   (Screen + Content + ViewModel + UiState + LessonListItem)
- feature/examList/     (Screen + Content + ViewModel + UiState + ExamListItem)
- feature/examDetail/   (Screen + Content + ViewModel + UiState)
(Ortak `ArchiveRepository` + `core/model/*` yeniden kullanılır.)
```

## Related Commands

- `/check-architecture` — üretilen dosyaları hedef mimariye göre doğrula (`.claude/commands/check-architecture.md`)
- `/generate-mocks` — ek Fake/mock + Hilt binding üret (`.claude/commands/generate-mocks.md`)
- `/document-module` — modül dokümantasyonu üret (`.claude/commands/document-module.md`)

## Related Skills

- `.claude/skills/new-feature/SKILL.md` — bu komutun devrettiği ana feature üretim skill'i
- `.claude/skills/new-screen/SKILL.md` — tek ekran (Screen + Content) üretimi
- `.claude/skills/new-viewmodel/SKILL.md` — tek ViewModel + UiState üretimi
- `.claude/skills/new-list-item/SKILL.md` — liste satırı composable'ı (iOS cell karşılığı)
- `.claude/skills/generate-unit-tests/SKILL.md` — birim test üretimi (Turbine)
- `.claude/skills/generate-ui-tests/SKILL.md` — Compose UI test üretimi
- `.claude/skills/generate-mocks/SKILL.md` — Fake/mock + Hilt binding üretimi

## Related Rules

- `.claude/rules/architecture.md` — ViFi hedef mimari genel bakış (master rehber)
- `.claude/rules/ui-components.md` — Compose UI / Material3 desenleri
- `.claude/rules/firebase-data.md` — named `vifi` Firestore erişimi + hata eşleme
- `.claude/rules/viewmodel-rule.md` — ViewModel/UiState/StateFlow kuralları
- `.claude/rules/testing.md` — test stratejisi (JUnit + coroutines-test + Turbine + Compose UI)
- `.claude/rules/localization.md` — strings.xml + positional args
- `.claude/rules/date-handling.md` — tarih dönüşümü (ISO 8601 / Firestore Timestamp)

---
