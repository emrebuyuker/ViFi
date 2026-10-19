# ViFi-Android - Screen Template (stateful Composable)

⚠️ **ÖNEMLİ**: ViFi, **stateful `Screen` Composable + `hiltViewModel()` + NavHost route** desenini kullanır — `Activity`/`Fragment`/Coordinator **yok**. `Screen`, ekranın "controller"ıdır: ViewModel'i enjekte eder, `uiState`'i toplar, tek seferlik event'leri navigasyona/snackbar'a bağlar ve stateless `Content`'i çağırır. İçinde layout/UI mantığı **olmaz** (o `Content`'tedir).

> **Legacy not:** Bu dosyanın bugünkü karşılığı `CourseExamListActivity`/`ExamDetailActivity`'dir (`setContentView` + `findViewById` + manuel intent navigasyonu). Hedefte `Activity` sadece tek `setContent { ViFiTheme { AppNavHost() } }` host'u olur; her ekran bir `@Composable` `Screen`'dir. Göçteki yeri: `ANDROID-PARITY-ROADMAP.md` → Faz 1 (Navigation-Compose iskeleti).

`{FeatureName}` → PascalCase (ör. `ExamList`, `ExamDetail`, `SubmissionForm`); `{featureName}` → camelCase (testTag/route prefix türetilir).

## Standart Template (liste ekranı)

```kotlin
package com.salticusteam.vifi.feature.examlist

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// Stateful "controller" composable: binds the ViewModel to the stateless Content.
@Composable
fun ExamListScreen(
    onNavigateToExamDetail: (examId: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ExamListViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    // Lifecycle-aware collection: no updates while the screen is in the background.
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // One-shot events (navigate / snackbar). Collected once, tied to the composition's lifecycle.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ExamListEvent.OpenExamDetail -> onNavigateToExamDetail(event.examId)
                is ExamListEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    // Method references keep the Screen thin; all logic lives in the ViewModel.
    ExamListContent(
        uiState = uiState,
        onExamClick = viewModel::onExamClick,
        onRetry = viewModel::loadExams,
        onBack = onNavigateBack,
    )
}
```

### NavHost kaydı (route)

`core/navigation/ExamListNavigation.kt` — route sabiti, type-safe navigate helper'ı ve `NavGraphBuilder` extension'ı. iOS `safePush` + argüman geçişinin karşılığıdır (route arg → `SavedStateHandle`).

```kotlin
package com.salticusteam.vifi.core.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.salticusteam.vifi.feature.examlist.ExamListScreen

const val EXAM_LIST_ROUTE = "exam_list"
private const val LESSON_ID_ARG = "lessonId"

// Type-safe navigation entry point (iOS: equivalent of navigateToExamList(lesson)).
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

Parent `NavHost`'ta:

```kotlin
NavHost(navController = navController, startDestination = "home") {
    // ...
    examListScreen(
        onNavigateToExamDetail = { examId -> navController.navigateToExamDetail(examId) },
        onNavigateBack = { navController.popBackStack() }, // the icon-only back button binds to this
    )
}
```

## Grid Template (çok sütunlu liste)

`Content` tarafında `LazyColumn` yerine `LazyVerticalGrid(columns = GridCells.Fixed(2))` kullanılır; `Screen` aynı kalır. iOS `UICollectionViewDelegateFlowLayout.sizeForItemAt` karşılığı Compose'da `GridCells.Fixed(n)` / `GridCells.Adaptive(minSize)`'dır — elle boyut hesabı yok.

```kotlin
// Inside Content:
LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    modifier = Modifier.fillMaxSize().testTag("examList.grid"),
    contentPadding = PaddingValues(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    items(items = uiState.exams, key = { it.id }) { exam ->
        ExamListItem(exam = exam, onClick = { onExamClick(exam) })
    }
}
```

## Veri geçişli Template (route argümanı)

iOS'taki `init(dataModel:)` yerine, veri `NavController.navigate("route/$id")` ile route argümanı olarak gelir ve ViewModel'de `SavedStateHandle`'dan okunur. `Screen` argümanı bilmez; sadece lambda'ları bağlar.

```kotlin
// ViewModel (feature paketinde):
@HiltViewModel
class ExamDetailViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val examId: String = checkNotNull(savedStateHandle["examId"])
    // init { loadExam() } ...
}

// Navigation:
const val EXAM_DETAIL_ROUTE = "exam_detail"
fun NavController.navigateToExamDetail(examId: String) = navigate("$EXAM_DETAIL_ROUTE/$examId")

fun NavGraphBuilder.examDetailScreen(onNavigateBack: () -> Unit) {
    composable(
        route = "$EXAM_DETAIL_ROUTE/{examId}",
        arguments = listOf(navArgument("examId") { type = NavType.StringType }),
    ) {
        ExamDetailScreen(onNavigateBack = onNavigateBack)
    }
}
```

## ✅ Doğru Desen (ViFi)

```kotlin
// ✅ DO THIS — stateful Screen + hiltViewModel + stateless Content
@Composable
fun HomeScreen(
    onNavigateToLesson: (lessonId: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.OpenLesson -> onNavigateToLesson(event.lessonId)
            }
        }
    }

    HomeContent(
        uiState = uiState,
        onLessonClick = viewModel::onLessonClick,
        onRefresh = viewModel::refresh,
    )
}
```

## Yaygın Desenler

### Modal / Bottom Sheet (iOS present karşılığı)

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    onDismiss: () -> Unit,
    viewModel: FilterViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        FilterContent(uiState = uiState, onApply = viewModel::apply, onClose = onDismiss)
        // In a modal it's dismiss, not back: "✕" IconButton or tap-outside.
    }
}
```

### Event dinleme (iOS EventBus karşılığı — SharedFlow)

Uygulama geneli olaylar (ör. "arşiv güncellendi") için bir `SharedFlow` enjekte edilir; `Screen` `LaunchedEffect` içinde dinler.

```kotlin
LaunchedEffect(Unit) {
    appEventBus.events.collect { event ->
        if (event is AppEvent.ArchiveUpdated) viewModel.refresh()
    }
}
```

### Pull to Refresh (iOS UIRefreshControl karşılığı)

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamListContent(
    uiState: ExamListUiState,
    onRefresh: () -> Unit,
    /* ... */
) {
    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(/* ... */) { /* items */ }
    }
}
```

### Pagination (iOS willDisplay karşılığı — LazyListState)

```kotlin
val listState = rememberLazyListState()
LaunchedEffect(listState) {
    snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
        .collect { lastVisible ->
            if (lastVisible != null && lastVisible >= uiState.exams.size - 5) {
                viewModel.loadMore()
            }
        }
}
LazyColumn(state = listState) { /* items */ }
```

## Placeholder Replacements

| Placeholder | Replace With | Example |
|-------------|--------------|---------|
| `{FeatureName}` | Feature adı (PascalCase) | `ExamList`, `ExamDetail`, `Home` |
| `{featureName}` | Feature adı (camelCase) | `examList`, `examDetail`, `home` |
| `{Item}` | Liste öğesi modeli | `Exam`, `Lesson` |
| `{Detail}` | Detay ekranı | `ExamDetail` |

## Checklist

Bir `Screen` üretince doğrula:
- [ ] `@Composable fun {FeatureName}Screen(...)` imzası; `viewModel: X = hiltViewModel()` default param
- [ ] `collectAsStateWithLifecycle()` ile state toplanıyor (düz `collectAsState()` değil)
- [ ] Tek seferlik event'ler `LaunchedEffect` + `events.collect {}` ile navigasyona/snackbar'a bağlı
- [ ] `Screen` içinde layout/UI yok — stateless `Content`'e delege ediyor
- [ ] Navigasyon lambda ile yukarı veriliyor (`onNavigateToX`, `onNavigateBack`); `Screen` `NavController` tutmuyor
- [ ] NavHost route'u `NavGraphBuilder` extension'ı + type-safe `navigateToX` helper'ı ile kayıtlı
- [ ] Route argümanları ViewModel'de `SavedStateHandle`'dan okunuyor (Screen'de değil)
- [ ] Geri = parent'ta `popBackStack()`; `Content`'teki `TopAppBar` navigationIcon buna bağlı
- [ ] `Activity`/`Fragment`/XML yok; Coordinator yok

---

**See Also:**
- `stateless-content.md` — stateless Content template (layout burada)
- `viewmodel.md` — ViewModel + UiState + Event template
- `accessibility.md` — testTag / semantics / dinamik yazı ölçeği
- `SKILL.md` — tam feature iskeleti
- `.claude/skills/new-screen/SKILL.md` — tek ekran üretim rehberi
- `.claude/rules/architecture.md` — ViFi hedef mimari
- `.claude/rules/ui-components.md` — Navigation & UI desenleri
