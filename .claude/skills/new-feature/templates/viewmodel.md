# ViFi-Android - ViewModel Template (yeni feature için)

⚠️ **ÖNEMLİ**: ViFi, **`@HiltViewModel` + tek immutable `data class {FeatureName}UiState` + `StateFlow`** desenini kullanır — iOS'un delegate/Output deseni **yok**. Delegate callback'leri (didSuccessLoad / willStartLoading / didFinishLoading / didFailure) tek bir `UiState`'in alanlarına (`isLoading`, `exams`, `error`) dönüşür. Tek seferlik olaylar (navigate/snackbar) `Channel`/`SharedFlow` ile akar.

> **Legacy not:** Bugün iş mantığı Activity/Fragment içinde, RTDB `ValueEventListener` callback'leri `runOnUiThread` ile UI'ı doğrudan günceller (`CourseExamListActivity`). Hedefte tüm mantık test edilebilir bir `ViewModel`'e taşınır; Firebase erişimi `ArchiveRepository` arkasındadır. Göçteki yeri: `ANDROID-PARITY-ROADMAP.md` → Faz 1 + `BE-15`.

⚠️ **ZORUNLU — Repository/Firebase istek pattern'i:**
- Veri erişimi `ArchiveRepository` **arayüzü** üzerinden; ham `DocumentSnapshot`/`DataSnapshot`/`FirebaseFirestoreException` ViewModel'e **girmez** (repository DTO→domain map eder).
- Firebase hataları `sealed class ArchiveError` + Türkçe `userMessage`'a çevrilir (repository'de). ViewModel `catch (e: ArchiveError)` ile yakalar, `uiState.error = e.userMessage` yazar.
- Asenkron iş `viewModelScope.launch` içinde; Firebase/IO `Dispatchers.IO`'da (repository'de `withContext(Dispatchers.IO)` / `flowOn`). `viewModelScope` clear'da iptal eder → sızıntı yok (iOS `[weak self]` karşılığı).
- `Task` → `.await()` (`kotlinx-coroutines-play-services`). Realtime dinleme → repository `callbackFlow { ... awaitClose { registration.remove() } }`.
- Çift istek koruması: `if (_uiState.value.isLoading) return`.
- `error.userMessage` **boş bırakılamaz** — her ArchiveError Türkçe bir `userMessage` taşır (inline Türkçe).

`{FeatureName}` → PascalCase (ör. `ExamList`); `{Item}` → domain model (`Exam`).

## Standart ViewModel + UiState Template

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

// MARK-like section: UI State ---------------------------------------------
data class ExamListUiState(
    val isLoading: Boolean = false,
    val exams: List<Exam> = emptyList(),
    val error: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && error == null && exams.isEmpty()
}

// One-shot events — NOT UI state (never re-triggered on recomposition).
sealed interface ExamListEvent {
    data class OpenExamDetail(val examId: String) : ExamListEvent
    data class ShowMessage(val message: String) : ExamListEvent
}

// ViewModel ----------------------------------------------------------------
@HiltViewModel
class ExamListViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

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
        viewModelScope.launch { _events.send(ExamListEvent.OpenExamDetail(exam.id)) }
    }
}
```

## Template — Realtime (callbackFlow + stateIn)

Sürekli dinlenen bir koleksiyon için `UiState`'i repository Flow'undan türet. Listener, scope iptal olunca `awaitClose`'da kaldırılır.

```kotlin
@HiltViewModel
class ExamListViewModel @Inject constructor(
    archiveRepository: ArchiveRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val lessonId: String = checkNotNull(savedStateHandle["lessonId"])

    val uiState: StateFlow<ExamListUiState> =
        archiveRepository.observeExams(lessonId)                       // callbackFlow
            .map { exams -> ExamListUiState(isLoading = false, exams = exams) }
            .catch { error ->
                emit(ExamListUiState(isLoading = false, error = (error as? ArchiveError)?.userMessage
                    ?: ArchiveError.Unknown.userMessage))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000), // stops 5s after the last subscriber leaves
                initialValue = ExamListUiState(isLoading = true),
            )
}
```

## Template — Pagination

```kotlin
@HiltViewModel
class ExamListViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val lessonId: String = checkNotNull(savedStateHandle["lessonId"])
    private var lastExamId: String? = null
    private var canLoadMore = true

    private val _uiState = MutableStateFlow(ExamListUiState())
    val uiState: StateFlow<ExamListUiState> = _uiState.asStateFlow()

    init { loadFirstPage() }

    fun loadFirstPage() {
        if (_uiState.value.isLoading) return
        lastExamId = null; canLoadMore = true
        _uiState.update { it.copy(isLoading = true, error = null, exams = emptyList()) }
        viewModelScope.launch {
            try {
                val page = archiveRepository.getExams(lessonId, startAfterId = null)
                lastExamId = page.lastOrNull()?.id
                canLoadMore = page.isNotEmpty()
                _uiState.update { it.copy(isLoading = false, exams = page) }
            } catch (error: ArchiveError) {
                _uiState.update { it.copy(isLoading = false, error = error.userMessage) }
            }
        }
    }

    fun loadMore() {
        if (_uiState.value.isLoading || !canLoadMore) return
        viewModelScope.launch {
            try {
                val next = archiveRepository.getExams(lessonId, startAfterId = lastExamId)
                lastExamId = next.lastOrNull()?.id ?: lastExamId
                canLoadMore = next.isNotEmpty()
                _uiState.update { it.copy(exams = it.exams + next) }
            } catch (_: ArchiveError) {
                // Pagination fails silently; the current list is kept.
            }
        }
    }
}
```

## Template — CRUD (gönderim/submission)

```kotlin
data class SubmissionFormUiState(
    val title: String = "",
    val titleError: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val isSubmitEnabled: Boolean get() = title.isNotBlank()
}

sealed interface SubmissionFormEvent {
    data object SubmitSuccess : SubmissionFormEvent
    data class ShowMessage(val message: String) : SubmissionFormEvent
}

@HiltViewModel
class SubmissionFormViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubmissionFormUiState())
    val uiState: StateFlow<SubmissionFormUiState> = _uiState.asStateFlow()

    private val _events = Channel<SubmissionFormEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onTitleChange(value: String) {
        _uiState.update { it.copy(title = value, titleError = null) }
    }

    fun submit() {
        if (_uiState.value.isLoading || !_uiState.value.isSubmitEnabled) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                archiveRepository.submitExam(title = _uiState.value.title)
                _uiState.update { it.copy(isLoading = false) }
                _events.send(SubmissionFormEvent.SubmitSuccess)
            } catch (error: ArchiveError) {
                _uiState.update { it.copy(isLoading = false, error = error.userMessage) }
            }
        }
    }
}
```

## Placeholder Replacements

| Placeholder | Replace With | Example |
|-------------|--------------|---------|
| `{FeatureName}` | Feature adı (PascalCase) | `ExamList`, `SubmissionForm` |
| `{featureName}` | Feature adı (camelCase) / paket | `examList`, `submissionForm` |
| `{Item}` | Domain model | `Exam`, `Lesson` |

## ✅ Doğru Desen (ViFi)

```kotlin
// ✅ DO THIS — single UiState, StateFlow, Hilt constructor injection, repository interface
@HiltViewModel
class LessonListViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val departmentId: String = checkNotNull(savedStateHandle["departmentId"])

    private val _uiState = MutableStateFlow(LessonListUiState())
    val uiState: StateFlow<LessonListUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val lessons = archiveRepository.getLessons(departmentId)
                _uiState.update { it.copy(isLoading = false, lessons = lessons) }
            } catch (error: ArchiveError) {
                _uiState.update { it.copy(isLoading = false, error = error.userMessage) }
            }
        }
    }
}
```

## Screen ile Entegrasyon

```kotlin
@Composable
fun LessonListScreen(
    onNavigateToExams: (lessonId: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: LessonListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) { is LessonListEvent.OpenExams -> onNavigateToExams(event.lessonId) }
        }
    }
    LessonListContent(
        uiState = uiState,
        onLessonClick = viewModel::onLessonClick,
        onRetry = viewModel::load,
        onBack = onNavigateBack,
    )
}
```

## Yaygın UiState Alanları / Events

### Standart UiState alanları (her zaman)
```kotlin
val isLoading: Boolean = false
val error: String? = null          // ArchiveError.userMessage (inline Turkish)
// + feature data: val exams: List<Exam> = emptyList()
```

### Opsiyonel alanlar
```kotlin
val isRefreshing: Boolean = false  // pull-to-refresh (separate from isLoading)
val isLoadingMore: Boolean = false // pagination
val selectedId: String? = null     // selection
```

### Yaygın Events (Channel/SharedFlow)
```kotlin
data class OpenDetail(val id: String) : XEvent   // navigate
data class ShowMessage(val message: String) : XEvent // snackbar
data object Submitted : XEvent
data object NavigateBack : XEvent
```

## Versioned doc comment

Var olan bir `UiState`/domain `data class`'a eklenen her alan ve `sealed interface`'e eklenen her case şunu taşır (versiyon = `app/build.gradle` `versionName`):
```kotlin
val isLocked: Boolean = false, // Added in 3.0.0 — exam locked behind the coin economy.
```

## Checklist

Bir ViewModel üretince doğrula:
- [ ] `@HiltViewModel class XViewModel @Inject constructor(...)` — constructor injection
- [ ] Repository **arayüz** tipiyle enjekte (`ArchiveRepository`, concrete değil)
- [ ] Route argümanları `SavedStateHandle`'dan (`checkNotNull(savedStateHandle["..."])`)
- [ ] Tek `data class XUiState`; `private val _uiState = MutableStateFlow(...)` + `val uiState = _uiState.asStateFlow()`
- [ ] `isLoading` + `error: String?` deseni (proje genelinde tutarlı)
- [ ] Tek seferlik olaylar `Channel`/`SharedFlow` ile (UI state'te değil)
- [ ] `viewModelScope.launch`; IO repository'de `Dispatchers.IO`; `Task.await()` / `callbackFlow`
- [ ] `catch (e: ArchiveError)` → `uiState.error = e.userMessage`; ham Firebase tipi yok
- [ ] Çift istek koruması (`if (_uiState.value.isLoading) return`)
- [ ] Composable/`Context`/`View` tutulmuyor (ApplicationContext istisna, Hilt ile)
- [ ] Eklenen alan/case'te `// Added in <versionName> — ...` yorumu

---

**See Also:**
- `screen.md` — ViewModel'i bağlayan stateful Screen
- `stateless-content.md` — `uiState`'i tüketen stateless Content
- `accessibility.md` — testTag / semantics
- `.claude/skills/new-viewmodel/SKILL.md` — tek ViewModel üretim rehberi
- `.claude/skills/generate-unit-tests/SKILL.md` — Turbine + coroutines-test ile ViewModel testleri
- `.claude/rules/viewmodel-rule.md` — ViewModel/UiState kuralları
- `.claude/rules/firebase-data.md` — repository + hata eşleme
