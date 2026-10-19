---
name: new-screen
description: Scaffold a new Jetpack Compose screen for the ViFi app using the Compose + MVVM pattern — a stateful route Composable (XScreen) + stateless content (XContent) + XViewModel + XUiState, wired with Hilt and Navigation-Compose. Use when the user wants to add a new screen or feature with all core UI/state components generated together.
---

> ⚠️ **Typography Rule (Zorunlu):** Her `Text` tipografisi `MaterialTheme.typography.<style>` üzerinden gelir (ya da proje `Type.kt`'teki marka stili). Doğrudan `fontSize = 16.sp` / elle `TextStyle(...)` **yasak**.
>
> - **`textStyle`'ı metnin rolüne göre seç** — başlık için `titleLarge`/`titleMedium`, gövde için `bodyLarge`/`bodyMedium`, etiket/caption için `labelMedium`/`labelSmall`. Her yere `bodyMedium` koyma.
> - Compose dinamik yazı tipi ölçeğini (`fontScale`) **otomatik** uygular; `sp` birimini koru, sabit `dp` metin boyutu kullanma. Aşırı büyümeyi yalnızca gerçekten sabit-yükseklikli container'da sınırla (`maxLines` + `overflow`), blanket sınırlama yapma.
>
> Canonical reference: `.claude/rules/ui-components.md` (Typography).

> ⚠️ **Scaffold / Renk Kuralı (Zorunlu):** Stateless content Composable'ının kökü her zaman `Scaffold` (ekran) ya da `Surface` (host edilen alt-bileşen) olur. Tam ekran için arka plan `MaterialTheme.colorScheme.background` (Scaffold `containerColor`); styled bir container/`LazyColumn` item'ı içinde host edilen bileşende arka planı **ata<ma>** (`Color.Transparent` → parent görünür, opak overlay olmasın). Marka rengi gerektiğinde `MaterialTheme.colorScheme.*` ya da `core/designsystem/theme/Color.kt` sabiti; elle `Color(0xFF...)` **yasak**. Canonical reference: `.claude/rules/ui-components.md` (Theme & Color).

> ⚠️ **State Hoisting Kuralı (Zorunlu):** Ekranı **iki** Composable'a böl. (1) **Stateful route Composable** `XScreen(...)` — `hiltViewModel()` burada çağrılır, `uiState` burada `collectAsStateWithLifecycle()` ile toplanır, tek-seferlik event'ler burada dinlenir; başka iş yapmaz. (2) **Stateless content** `XContent(uiState, onX, modifier)` — yalnızca `uiState` alır, `on...` lambda'ları dışarı verir, `@Preview`lenebilir ve testlenebilir. `viewModel`/`hiltViewModel`/iş mantığı **stateless content'e veya list-item Composable'ına GİRMEZ**. Canonical reference: `.claude/rules/architecture.md` (State Hoisting) + `.claude/rules/viewmodel-rule.md`.

> ⚠️ **Etkileşim Kuralı (Zorunlu):** Tıklama/jest, stateless content'in **hoisted lambda** parametresiyle yukarı taşınır (`onExamClick: (Exam) -> Unit`); `Modifier.clickable { ... }` / `IconButton(onClick = ...)` içinde iş mantığı **çalıştırma**, sadece lambda'yı çağır. İş mantığı ViewModel'de yaşar. Canonical reference: `.claude/rules/ui-components.md` (Interaction).

> ⚠️ **Geri Butonu Kuralı (Zorunlu):** Push edilen (route) bir ekran üretiyorsan geri butonu **her zaman** Material3 `TopAppBar`'ın `navigationIcon`'u olan **ikon-only** geri butonudur (`Icons.AutoMirrored.Filled.ArrowBack`), tap'te `onBack()` → çağıran NavHost `navController.popBackStack()` yapar. Elle/metinli ("< Geri") geri butonu veya `BackHandler` ile elle pop **yasak** (sistem geri tuşu Navigation-Compose tarafından zaten yönetilir). **İstek/tasarım açıkça farklı bir geri butonu belirtmedikçe sormadan bu şekilde ilerle.** İstisna: modal/bottom sheet'ler (`ModalBottomSheet`, header'da `closeButton` "✕"). Canonical reference: `.claude/rules/ui-components.md` (Navigation Back Button).

> ⚠️ **String & testTag Kuralı (Zorunlu):** Kullanıcıya görünen HER metin `res/values/strings.xml`'de Türkçe tanımlı ve `stringResource(R.string.x)` ile okunur — Composable'a sabit string gömme **yasak**. Dinamik değerler positional format arg ile: strings.xml'de `%1$s` / `%1$d`, çağrıda `stringResource(R.string.x, arg)`. Ayrıca her etkileşimli/duruma-bağlı node `Modifier.testTag("<ekran>.<öğe>")` taşır; testTag'ler UI testleriyle **sözleşmedir**, mevcutları yeniden adlandırma. Canonical reference: `.claude/rules/localization.md` + `.claude/rules/testing.md`.

> **Legacy not:** Eski v2.5'te bir ekran = `Activity`/`Fragment` + XML layout + Firebase callback'leri doğrudan UI'da + `Intent`/`startActivity` gezinme (ör. `CourseExamListActivity`, `LessonFragment`, `DepartmentsFragment`). **Yeni ekranlarda Fragment/XML/Activity YOK** — her ekran bu skill'deki Compose + MVVM desenidir. Legacy ekranların Compose'a göçü yol haritasının ilgili alan kalemlerinde (çoğunlukla `archive` / Browse-Arşiv) izlenir; bkz. repo kökü `ANDROID-PARITY-ROADMAP.md`.

# New Compose Screen + ViewModel + UiState Scaffold (ViFi)

## When to use

- Kullanıcı yeni bir ekran/sayfa oluşturmak istediğinde.
- Yeni bir özellik, Screen Composable + ViewModel + UiState iskeleti gerektirdiğinde.
- Uygulamaya tam yapılı (state yönetimi + Navigation route + preview + test contract) yeni bir sahne eklerken.

## High-level workflow

1. Özelliğin alanını ve ekranın amacını netleştir (sorma, brief'teki domain'den türet).
2. Hedef modülü ve klasör yapısını `feature/<ad>/` altında belirle.
3. **UiState**'i üret (tek immutable `data class`).
4. **ViewModel**'i üret (`@HiltViewModel` + `StateFlow` + `Channel` event'ler).
5. **Screen Composable**'ı üret (stateful route `XScreen` + stateless `XContent` ayrımı).
6. Gerekirse **list-item** Composable'ı üret (bkz. skill `new-list-item`).
7. **Repository** arayüzü (+ canlı `Firebase...` impl + `Fake...`) ekle/doğrula.
8. Navigation-Compose **route**'unu `NavHost`'a kaydet.
9. `@Preview` (Fake repo ile) + `strings.xml` + testTag'leri ekle.
10. Her şeyi birbirine bağla (Hilt, kontrol listesi) ve iş mantığı için TODO bırak.

## Project conventions (ViFi)

- **Mimari**: Compose + MVVM
  - ✅ `@HiltViewModel class XViewModel @Inject constructor(...)` + tek `StateFlow<XUiState>`
  - ✅ Stateful route Composable (`XScreen`) + stateless content (`XContent`) ayrımı
  - ❌ Coordinator / Fragment / Activity (legacy istisnası dışında) YOK
  - ❌ UI'da doğrudan Firebase callback'i YOK — repository arkasında
- **UI framework**: Jetpack Compose + Material3 (Kotlin 2.0+, JDK 17)
  - ❌ XML layout (res/layout) YOK, yeni ekranda Fragment/Activity YOK
- **Listeler / kayan içerik**: `LazyColumn` / `LazyVerticalGrid` (lazy list) — liste/kart düzeni için tek önerilen kök.
  - ✅ Tek-kartlık ekran bile: içerik `Column` + `verticalScroll` ya da tek item'lık `LazyColumn`.
- **Gezinme**: Navigation-Compose (`NavHost`, route string'leri, `navController.popBackStack()`).
- **DI**: Hilt (constructor injection, `@Module`/`@Provides`/`@Binds`).
- **Async**: Kotlin Coroutines + Flow; `viewModelScope.launch`, IO işleri `Dispatchers.IO`; Firebase `Task` → `.await()` (`kotlinx-coroutines-play-services`); dinleyiciler → `callbackFlow { ... awaitClose { } }`.
- **Veri erişimi**: Her kaynak bir `interface` arkasında (`ArchiveRepository`, `AuthRepository`); canlı `Firebase...Repository`, preview/test `Fake...Repository`. Repository UI'ya ham Firebase tipi (`DataSnapshot`/`DocumentSnapshot`) **sızdırmaz** → DTO'yu domain `data class`'a map eder.
- **Hatalar**: `sealed class ArchiveError : Exception()` + Türkçe `val userMessage: String`; UI `error.userMessage` gösterir.
- **Stringler**: `res/values/strings.xml` + `stringResource(R.string.x)`; positional arg (`%1$s`/`%1$d`).
- **Renk**: `MaterialTheme.colorScheme.*`, marka renkleri `core/designsystem/theme/Color.kt`.
- **Tipografi**: `MaterialTheme.typography.*` / proje `Type.kt`.
- **Erişilebilirlik / test**: `Modifier.semantics { contentDescription = ... }` + `Modifier.testTag("<ekran>.<öğe>")`.
- **Bellek/yaşam döngüsü**: coroutine'ler `viewModelScope`'ta; `collectAsStateWithLifecycle()`; `LaunchedEffect`/`DisposableEffect` doğru key'le; Context'i ViewModel'de tutma (`@ApplicationContext` istisnası, Hilt ile).
- **Paket**: `com.salticusteam.vifi`. Önerilen: `feature/<ad>/` (screen Composable, XViewModel, XUiState, list item) + `core/` (data/repository, model, util, designsystem, navigation).

## Detailed steps

### 1. Gather requirements

En az şunları belirle (sorma, brief/alan modelinden türet):

- **Feature / modül** (ör. `archive`, `profile`, `coin`, `notifications`)
- **Screen adı** (ör. `CourseExamList`, `LessonList`, `ProfileEdit`)
- **Navigation giriş noktası** (hangi ekrandan açılıyor + route arg'ları, ör. "LessonListScreen'den `lessonId` ile")
- **Veri bağımlılıkları** (repository, domain model)
- **UI tipi** (`LazyColumn` liste, `LazyVerticalGrid`, scrollable `Column`, custom)

Ekranı şöyle temsil et:

```
Feature: <FeatureName>
Screen: <ScreenName>
Purpose: <short description>
Entry: navigated from <ParentScreen> with <args>
UI Type: <LazyColumn|LazyVerticalGrid|ScrollableColumn|Custom>
```

### 2. Generate folder structure

Özellik modülü klasörünü oluştur:

```
feature/<featurename>/
├── <ScreenName>Screen.kt        // stateful route Composable + stateless XContent + @Preview
├── <ScreenName>ViewModel.kt     // @HiltViewModel + StateFlow + Channel events
├── <ScreenName>UiState.kt       // tek immutable data class (küçükse Screen/ViewModel içinde de olabilir)
└── <Name>ListItem.kt            // (gerekliyse) list-item Composable — bkz. skill new-list-item
```

Destekleyici dosyalar (`core/` altında; yoksa oluştur, varsa yeniden kullan):

```
core/data/repository/
├── <Feature>Repository.kt            // interface
├── Firebase<Feature>Repository.kt    // canlı impl (Firestore/RTDB → domain map)
└── Fake<Feature>Repository.kt        // preview/test impl

core/model/
└── <Name>.kt                         // plain domain data class (Exam, Lesson, ...)

core/di/
└── <Feature>Module.kt                // Hilt @Binds (interface ↔ impl)

core/navigation/
└── ViFiDestinations.kt               // route string'leri + route builder'lar
```

### 3. Generate the UiState (tek immutable data class)

**File**: `feature/<featurename>/<ScreenName>UiState.kt`

```kotlin
package com.salticusteam.vifi.feature.<featurename>

import com.salticusteam.vifi.core.model.<Model>

// Single immutable UI state for the screen. Loading/error live here as flat fields
// (project-wide convention: data class + fields, not a sealed state). One source of truth.
data class <ScreenName>UiState(
    val items: List<<Model>> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null, // resolved from <Feature>Error.userMessage (Turkish)
) {
    // Derived flags stay in the state, not in the Composable.
    val isEmpty: Boolean
        get() = !isLoading && error == null && items.isEmpty()
}

// One-shot events (navigate / snackbar) — delivered via Channel, never kept in UiState.
sealed interface <ScreenName>Event {
    data class OpenDetail(val id: String) : <ScreenName>Event
    data class ShowMessage(val message: String) : <ScreenName>Event
}
```

**Replace**:
- `<ScreenName>` → ör. `CourseExamList`
- `<featurename>` → ör. `archive`
- `<Model>` → ör. `Exam`
- `<Feature>` → ör. `Archive`

### 4. Generate the ViewModel

**File**: `feature/<featurename>/<ScreenName>ViewModel.kt`

```kotlin
package com.salticusteam.vifi.feature.<featurename>

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.repository.<Feature>Repository
import com.salticusteam.vifi.core.model.<Feature>Error
import com.salticusteam.vifi.core.model.<Model>
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class <ScreenName>ViewModel @Inject constructor(
    private val <feature>Repository: <Feature>Repository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Route arg — read once from the handle (never passed through Composable params).
    private val lessonId: String = checkNotNull(savedStateHandle[<ScreenName>Route.ARG_LESSON_ID]) {
        "<ScreenName> requires a lessonId route argument"
    }

    // Single source of truth: private MutableStateFlow + read-only asStateFlow().
    private val _uiState = MutableStateFlow(<ScreenName>UiState())
    val uiState: StateFlow<<ScreenName>UiState> = _uiState.asStateFlow()

    // One-shot events (navigate / snackbar).
    private val _events = Channel<<ScreenName>Event>(Channel.BUFFERED)
    val events: Flow<<ScreenName>Event> = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }

        // viewModelScope → cancelled with the ViewModel, no leaks. Repo does its own Dispatchers.IO.
        viewModelScope.launch {
            runCatching { <feature>Repository.examsForLesson(lessonId) }
                .onSuccess { items ->
                    _uiState.update { it.copy(isLoading = false, items = items) }
                }
                .onFailure { throwable ->
                    val message = (throwable as? <Feature>Error)?.userMessage
                        ?: <Feature>Error.Unknown.userMessage
                    _uiState.update { it.copy(isLoading = false, error = message) }
                }
        }
    }

    fun onItemClick(item: <Model>) {
        viewModelScope.launch { _events.send(<ScreenName>Event.OpenDetail(item.id)) }
    }
}
```

> Not: ViewModel'in tek sorumluluğu state + event üretmektir; derinlemesine kurallar için bkz. skill `new-viewmodel` ve `.claude/rules/viewmodel-rule.md`.

**Replace**:
- `<ScreenName>` → ör. `CourseExamList`
- `<Feature>` / `<feature>` → ör. `Archive` / `archive`
- `<Model>` → ör. `Exam`

### 5. Generate the Screen Composable (stateful route + stateless content)

**File**: `feature/<featurename>/<ScreenName>Screen.kt`

```kotlin
package com.salticusteam.vifi.feature.<featurename>

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.data.repository.FakeArchiveRepository
import com.salticusteam.vifi.core.designsystem.component.EmptyState
import com.salticusteam.vifi.core.designsystem.component.ErrorState
import com.salticusteam.vifi.core.model.<Model>
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme

// 1) STATEFUL route Composable — owns the ViewModel, collects state, forwards nav lambdas up.
//    No layout logic here beyond wiring. hiltViewModel() lives ONLY here.
@Composable
fun <ScreenName>Screen(
    onOpenDetail: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: <ScreenName>ViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // One-shot events → side effects. Keyed to the ViewModel so it restarts only if that changes.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is <ScreenName>Event.OpenDetail -> onOpenDetail(event.id)
                is <ScreenName>Event.ShowMessage -> { /* TODO: snackbar host */ }
            }
        }
    }

    <ScreenName>Content(
        uiState = uiState,
        onItemClick = viewModel::onItemClick,
        onRetry = viewModel::load,
        onBack = onBack,
    )
}

// 2) STATELESS content — pure: state in, lambdas out. Previewable + testable. No ViewModel here.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <ScreenName>Content(
    uiState: <ScreenName>UiState,
    onItemClick: (<Model>) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.testTag("<feature>.screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.<feature>_title)) },
                navigationIcon = {
                    // Icon-only back button — the ONLY allowed back control on a pushed screen.
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("<feature>.backButton"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("<feature>.loading"),
                )

                uiState.error != null -> ErrorState(
                    message = uiState.error,
                    onRetry = onRetry,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("<feature>.error"),
                )

                uiState.isEmpty -> EmptyState(
                    message = stringResource(R.string.<feature>_empty),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("<feature>.empty"),
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("<feature>.list"),
                ) {
                    items(items = uiState.items, key = { it.id }) { item ->
                        // list-item Composable stays stateless too; click is a hoisted lambda.
                        <Name>ListItem(
                            item = item,
                            onClick = { onItemClick(item) },
                        )
                    }
                }
            }
        }
    }
}

// 3) Preview — drives the STATELESS content with Fake data, never the ViewModel.
@Preview(showBackground = true)
@Composable
private fun <ScreenName>ContentPreview() {
    ViFiTheme {
        <ScreenName>Content(
            uiState = <ScreenName>UiState(items = FakeArchiveRepository.sampleExams),
            onItemClick = {},
            onRetry = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun <ScreenName>ContentEmptyPreview() {
    ViFiTheme {
        <ScreenName>Content(
            uiState = <ScreenName>UiState(),
            onItemClick = {},
            onRetry = {},
            onBack = {},
        )
    }
}
```

> ⚠️ **testTag — her etkileşimli/duruma-bağlı node.** `<feature>.screen`, `<feature>.backButton`, `<feature>.loading`, `<feature>.error`, `<feature>.empty`, `<feature>.list`, ve list-item'larda `<feature>.item_<id>` (list-item Composable'ı kendi testTag'ini kendi alır — bkz. skill `new-list-item`). testTag'ler UI testleriyle sözleşmedir.

**Replace**:
- `<ScreenName>` → ör. `CourseExamList`
- `<Name>` → ör. `Exam` (list-item: `ExamListItem`)
- `<feature>` → ör. `courseExamList` (string + testTag prefix)
- `<Model>` → ör. `Exam`

### 6. Register the Navigation-Compose route

**File**: `core/navigation/ViFiDestinations.kt` — route string'i + builder.

```kotlin
package com.salticusteam.vifi.core.navigation

// Route + arg contract for the screen. The builder is the ONLY way callers construct the path.
object <ScreenName>Route {
    const val ARG_LESSON_ID = "lessonId"
    const val ROUTE = "<feature>/{$ARG_LESSON_ID}"
    fun build(lessonId: String) = "<feature>/$lessonId"
}
```

**File**: uygulama `NavHost`'u (ör. `core/navigation/ViFiNavHost.kt`) — `composable(...)` kaydı.

```kotlin
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

composable(
    route = <ScreenName>Route.ROUTE,
    arguments = listOf(
        navArgument(<ScreenName>Route.ARG_LESSON_ID) { type = NavType.StringType },
    ),
) {
    <ScreenName>Screen(
        onOpenDetail = { examId -> navController.navigate(ExamDetailRoute.build(examId)) },
        onBack = { navController.popBackStack() }, // matches the icon-only back button; no manual pop
    )
}
```

Çağıran ekrandan gezinme:

```kotlin
// e.g. on an item click inside LessonListScreen
onLessonClick = { lesson -> navController.navigate(<ScreenName>Route.build(lesson.id)) }
```

### 7. Generate the Repository (interface + Firebase impl + Fake)

Her veri kaynağı bir Kotlin `interface` arkasındadır; UI'ya ham Firebase tipi sızmaz.

**File**: `core/data/repository/<Feature>Repository.kt`

```kotlin
package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.<Model>

interface <Feature>Repository {
    // suspend: coroutine-friendly; impl wraps Firebase Task with .await() on Dispatchers.IO.
    suspend fun examsForLesson(lessonId: String): List<<Model>>
}
```

**File**: `core/data/repository/Firebase<Feature>Repository.kt` (canlı impl — DTO → domain map)

```kotlin
package com.salticusteam.vifi.core.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.salticusteam.vifi.core.model.<Feature>Error
import com.salticusteam.vifi.core.model.<Model>
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

// TARGET: the named 'vifi' Firestore (eur3). DocumentSnapshot stays at the repository BOUNDARY,
// only the domain model (data class) is exposed to the UI.
class Firebase<Feature>Repository @Inject constructor(
    private val firestore: FirebaseFirestore, // Hilt provides getInstance(app, "vifi")
) : <Feature>Repository {

    override suspend fun examsForLesson(lessonId: String): List<<Model>> =
        withContext(Dispatchers.IO) {
            runCatching {
                firestore.collection("exams")
                    .whereEqualTo("courseId", lessonId)
                    .whereEqualTo("status", "published")
                    .get()
                    .await() // kotlinx-coroutines-play-services
                    .documents
                    .mapNotNull { doc -> doc.toExamOrNull() } // DTO → domain, never leak DocumentSnapshot
            }.getOrElse { throw <Feature>Error.Unknown }
        }
}
```

> **Legacy not:** Bugünkü v2.5 bu veriyi RTDB `Universitiess` ağacından (`uniname`/`fakname`/`bolname`/`lessonname` Türkçe alanlar) doğrudan Fragment içinde `ValueEventListener` ile okur. Hedef, named `vifi` Firestore `catalogNodes`/`exams`'tir ve okuma mutlaka repository arkasına taşınır (yol haritası `archive`/`BE-15`). Yeni ekran **daima** repository interface'i çağırır, Firebase'e doğrudan değil.

**File**: `core/data/repository/Fake<Feature>Repository.kt` (preview + test)

```kotlin
package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.<Model>

// Preview + unit/UI test double. Deterministic sample data, no Firebase.
class Fake<Feature>Repository : <Feature>Repository {

    var exams: List<<Model>> = sampleExams
    var errorToThrow: Throwable? = null

    override suspend fun examsForLesson(lessonId: String): List<<Model>> {
        errorToThrow?.let { throw it }
        return exams
    }

    companion object {
        val sampleExams = listOf(
            <Model>(id = "1", title = "Vize 2023", courseId = "c1", kind = "pdf"),
            <Model>(id = "2", title = "Final 2023", courseId = "c1", kind = "image"),
        )
    }
}
```

> Mock/Fake üretimi için bkz. skill `generate-mocks` ve komut `/generate-mocks`.

**Hilt bağlama** — `core/di/<Feature>Module.kt`:

```kotlin
package com.salticusteam.vifi.core.di

import com.salticusteam.vifi.core.data.repository.<Feature>Repository
import com.salticusteam.vifi.core.data.repository.Firebase<Feature>Repository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class <Feature>Module {
    @Binds
    @Singleton
    abstract fun bind<Feature>Repository(
        impl: Firebase<Feature>Repository,
    ): <Feature>Repository
}
```

> Not: iOS `AppEnvironment(live/mock)` → Android'de Hilt modül + `interface` + `Fake...` üçlüsüdür. Preview/test `Fake...`'i doğrudan kullanır; canlı graf `@Binds` ile `Firebase...`'e bağlanır.

### 8. Generate the domain model (if needed)

**File**: `core/model/<Name>.kt` — plain domain `data class` (Firebase tipi değil).

```kotlin
package com.salticusteam.vifi.core.model

// Domain model (immutable). Firestore/RTDB DTO is mapped INTO this at the repository boundary.
data class Exam(
    val id: String,
    val title: String,
    val courseId: String,
    val kind: String, // "pdf" | "image"
    val isLocked: Boolean = false, // Added in 3.0.0 — coin-economy lock state.
)
```

> **Versioned doc comment kuralı:** Var olan bir model/`data class`'a eklenen HER alan ve enum/`sealed`'e eklenen HER case `// Added in <versionName> — <amaç>.` taşır (versiyon = `app/build.gradle` `versionName`). Canonical reference: `.claude/rules/architecture.md`.

### 9. Register strings

`res/values/strings.xml`'e ekle (hepsi Türkçe):

```xml
<!-- <ScreenName> -->
<string name="<feature>_title">Sınavlar</string>
<string name="<feature>_empty">Bu ders için henüz sınav eklenmemiş.</string>
<!-- positional format arg — use numbered args instead of %s/%d (translation safety) -->
<string name="<feature>_count">%1$d sınav bulundu</string>

<!-- Common (do not re-add if it already exists) -->
<string name="cd_navigate_back">Geri</string>
<string name="common_retry">Tekrar dene</string>
```

Kullanım:

```kotlin
Text(stringResource(R.string.<feature>_count, uiState.items.size))
```

> Yerelleştirme kuralları için bkz. `.claude/rules/localization.md`.

### 10. Wire everything together

**Checklist**:
- ✅ `XUiState` tek immutable `data class` (`isLoading`/`error` düz alan; `isEmpty` türetilmiş).
- ✅ `XViewModel` → `@HiltViewModel` + `@Inject constructor`; `private MutableStateFlow` + `asStateFlow()`.
- ✅ Tek-seferlik olaylar `Channel`/`receiveAsFlow` ile; UiState'e konmaz.
- ✅ Stateful `XScreen` → `hiltViewModel()` + `collectAsStateWithLifecycle()` + event `LaunchedEffect`.
- ✅ Stateless `XContent` → sadece state + lambda; `viewModel`/`hiltViewModel` **yok**.
- ✅ Push ekran `TopAppBar` + ikon-only `navigationIcon` → `onBack()` → `navController.popBackStack()`; elle/metinli geri butonu yok.
- ✅ Veri yalnızca `XRepository` interface'inden; canlı `Firebase...` + `Fake...`; UI'ya `DocumentSnapshot`/`DataSnapshot` sızmaz.
- ✅ Hilt `@Binds` modülü interface ↔ impl'i bağlar.
- ✅ Navigation route `NavHost`'a `composable(...)` + `navArgument(...)` ile kayıtlı; route arg `SavedStateHandle`'dan okunur.
- ✅ `@Preview` Fake repo/ Fake data ile stateless content'i sürer (ViewModel değil).
- ✅ Tüm kullanıcı metni `strings.xml` + `stringResource` (positional arg).
- ✅ Renk `MaterialTheme.colorScheme`/`Color.kt`, tipografi `MaterialTheme.typography`/`Type.kt` (hardcoded yok).
- ✅ Etkileşimli/duruma-bağlı node'larda `testTag`; coroutine'ler `viewModelScope`'ta.

## Common Patterns

### Sealed state alternatifi yerine data class (proje deseni)

ViFi projede **tek** desen kullanır: `data class` + `isLoading`/`error` alanları (yukarıdaki şablon). `sealed interface XUiState { Loading/Success/Error }` **kullanma** — tutarlılık için data class + alanlar tercih edilir. (iOS'taki durum yönetiminin Android karşılığı budur.)

### Scrollable tek-sayfa içerik (liste değil)

Liste yoksa `LazyColumn` yerine `Column` + `verticalScroll`:

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(innerPadding)
        .testTag("<feature>.content"),
) {
    // fixed sections
}
```

### Modal / bottom sheet sunumu

Push ekranın aksine modal, `ModalBottomSheet` ile sunulur ve geri kontrolü **✕ close** butonudur (geri oku değil):

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <ScreenName>Sheet(
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // closeButton "✕" in the header (IconButton + Icons.Filled.Close), testTag "<feature>.closeButton"
    }
}
```

### Route argümanı ile veri aktarımı

ViFi'de ekranlar arası veri aktarımı **constructor ile değil**, Navigation route arg + `SavedStateHandle` ile yapılır (yukarıda Adım 4 + 6). Büyük/karmaşık nesneyi route'tan geçirme — yalnızca **id** geçir, detay ekranı repository'den tam nesneyi çeker.

### Empty & Error state

`EmptyState` / `ErrorState` ortak Composable'ları `core/designsystem/component/` altındadır; yoksa oluştur:

```kotlin
@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(24.dp),
    )
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry, modifier = Modifier.testTag("<feature>.retryButton")) {
            Text(stringResource(R.string.common_retry))
        }
    }
}
```

### Pull to refresh

Material3 `PullToRefreshBox`, `uiState.isLoading` ile sürülür:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
PullToRefreshBox(
    isRefreshing = uiState.isLoading,
    onRefresh = onRetry,
    modifier = Modifier.fillMaxSize(),
) {
    LazyColumn(Modifier.fillMaxSize().testTag("<feature>.list")) {
        items(uiState.items, key = { it.id }) { item ->
            <Name>ListItem(item = item, onClick = { onItemClick(item) })
        }
    }
}
```

## Verification Checklist

Üretimden sonra doğrula:

1. ✅ Dosyalar doğru konumda: `feature/<featurename>/` (+ repo `core/data/repository/`).
2. ✅ `XScreen` (stateful) ile `XContent` (stateless) ayrı; `hiltViewModel()` yalnız `XScreen`'de.
3. ✅ `XViewModel` `@HiltViewModel`; `private MutableStateFlow` + `asStateFlow()`; event `Channel`.
4. ✅ `collectAsStateWithLifecycle()` kullanılmış (`collectAsState` değil).
5. ✅ Route `NavHost`'a `navArgument` ile kayıtlı; arg `SavedStateHandle`'dan okunuyor.
6. ✅ Geri = `TopAppBar` ikon-only `navigationIcon` → `onBack` → `popBackStack()`; elle/metinli geri yok.
7. ✅ Repository interface + `Firebase...` + `Fake...`; UI'ya ham Firebase tipi sızmıyor.
8. ✅ `@Preview`(ler) Fake data ile stateless content'i sürüyor.
9. ✅ `!!` / unsafe cast yok; nullable'lar düzgün ele alınmış.
10. ✅ Hardcoded renk/tipografi/string yok (hepsi `colorScheme`/`typography`/`strings.xml`).
11. ✅ Etkileşimli/duruma-bağlı node'larda `testTag` + gereken yerde `contentDescription`.
12. ✅ `./gradlew assembleDebug` derleniyor; `./gradlew lint` temiz.

## Example: Complete Feature

**Örnek**: Archive özelliğinde bir "Ders Sınav Listesi" ekranı — seçilen dersin yayınlanmış sınavlarını listeler.

### Requirements

```
Feature: archive
Screen: CourseExamList
Purpose: Seçilen ders (lesson) için yayınlanmış sınavları listele
Entry: LessonListScreen'den lessonId route arg'ı ile
UI Type: LazyColumn
```

### Generated Files

```
feature/archive/
├── CourseExamListScreen.kt      // CourseExamListScreen + CourseExamListContent + @Preview
├── CourseExamListViewModel.kt   // @HiltViewModel, StateFlow<CourseExamListUiState>, Channel<CourseExamListEvent>
├── CourseExamListUiState.kt     // data class + CourseExamListEvent
└── ExamListItem.kt              // bkz. skill new-list-item

core/data/repository/
├── ArchiveRepository.kt
├── FirebaseArchiveRepository.kt
└── FakeArchiveRepository.kt

core/model/Exam.kt
core/di/ArchiveModule.kt
core/navigation/ViFiDestinations.kt   // CourseExamListRoute
```

### Navigation

`LessonListScreen` içinde:

```kotlin
onLessonClick = { lesson ->
    navController.navigate(CourseExamListRoute.build(lesson.id))
}
```

## TODOs After Generation

Üretilen dosyalara şu TODO'ları ekle:

```kotlin
// ViewModel
// TODO: Pagination logic
// TODO: Search / filtering
// TODO: Offline cache (callbackFlow + Firestore persistence)

// Screen
// TODO: Snackbar host (ShowMessage event)
// TODO: Analytics screen-view event
// TODO: Locked exam UI (coin economy, unlockExam)

// Repository
// TODO: Complete Firestore DTO → domain map (toExamOrNull)
// TODO: Error mapping (FirebaseFirestoreException → ArchiveError)
```

---

**See Also:**
- `.claude/rules/architecture.md` — ViFi hedef mimari genel bakış (Compose + MVVM + Hilt)
- `.claude/rules/viewmodel-rule.md` — ViewModel / StateFlow / event kuralları
- `.claude/rules/ui-components.md` — Compose UI bileşen kuralları (tema, tipografi, geri butonu)
- `.claude/rules/firebase-data.md` — Repository, Firestore/RTDB, DTO→domain map
- `.claude/rules/localization.md` — strings.xml + stringResource + positional arg
- `.claude/rules/testing.md` — JUnit + Turbine + Compose test (testTag sözleşmesi)
- `.claude/skills/new-viewmodel/` — ViewModel iskelesi
- `.claude/skills/new-list-item/` — list-item Composable iskelesi
- `.claude/skills/generate-mocks/` — Fake repository / sample data üretimi
- `.claude/skills/generate-unit-tests/`, `.claude/skills/generate-ui-tests/` — test üretimi
