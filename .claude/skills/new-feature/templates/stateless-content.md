# ViFi-Android - Content Template (stateless Composable)

⚠️ **ÖNEMLİ**: ViFi, UI'yı **stateless `Content` Composable** olarak yazar — tüm layout burada yaşar, ama state **hoist** edilir: `Content` yalnızca bir `uiState` ile event lambda'ları alır, `ViewModel`/`NavController` referansı **içermez**, Firebase'e dokunmaz. Böylece `@Preview`lenebilir ve test edilebilir olur. XML layout **yok**.

> **Legacy not:** Bugünkü karşılığı `activity_course_exam_list.xml` + `CustomAdapter.getView(...)` + `findViewById` kombinasyonudur. Compose'da layout ve "adapter" tek bir deklaratif `Content` fonksiyonunda birleşir; satır görünümü ayrı bir `{Item}ListItem` composable'ıdır. Göçteki yeri: `ANDROID-PARITY-ROADMAP.md` → Faz 1.

> ⚠️ **Typography Rule:** `Text(..., style = MaterialTheme.typography.<style>)`. Composable'a `fontSize`/elle `TextStyle` gömme — `core/designsystem/theme/Type.kt` kaynaktır. Canonical: `.claude/rules/ui-components.md`.
> ⚠️ **Color Rule:** `MaterialTheme.colorScheme.<token>` / `core/designsystem/theme/Color.kt`. `Color(0xFF...)` gömme yasak.
> ⚠️ **No-manual-scroll Rule:** Liste/çok-kartlı/dinamik içerik `LazyColumn`/`LazyVerticalGrid`. `Column` + `verticalScroll` yalnızca kısa statik form.

`{FeatureName}` → PascalCase (ör. `ExamList`); `{featureName}` → camelCase/testTag prefix.

## List Template (LazyColumn + loading/empty/error)

```kotlin
package com.salticusteam.vifi.feature.examlist

import androidx.compose.foundation.layout.*
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
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme

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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exam_list_title)) },
                navigationIcon = {
                    // Standard icon-only back button (no text back).
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

                uiState.isEmpty -> ExamListEmptyState(
                    modifier = Modifier.align(Alignment.Center),
                )

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

// Small, single-purpose sub-composables — all stateless.
@Composable
private fun ExamListEmptyState(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.exam_list_empty),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(32.dp).testTag("examList.empty"),
    )
}

@Composable
private fun ExamListError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp).testTag("examList.error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry, modifier = Modifier.testTag("examList.retryButton")) {
            Text(stringResource(R.string.action_retry))
        }
    }
}

// MARK-like: @Preview with fake/sample state — NO ViewModel.
@Preview(showBackground = true)
@Composable
private fun ExamListContentPreview() {
    ViFiTheme {
        ExamListContent(
            uiState = ExamListUiState(
                exams = listOf(
                    Exam("e1", "Vize 2023", "math101", "pdf", 2023),
                    Exam("e2", "Final 2023", "math101", "pdf", 2023, isLocked = true),
                ),
            ),
            onExamClick = {}, onRetry = {}, onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ExamListContentEmptyPreview() {
    ViFiTheme { ExamListContent(ExamListUiState(), {}, {}, {}) }
}
```

## Grid Template

```kotlin
// Multi-column grid instead of a list (iOS 2-column flow layout equivalent).
LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    modifier = Modifier.fillMaxSize().padding(innerPadding).testTag("examList.grid"),
    contentPadding = PaddingValues(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    items(items = uiState.exams, key = { it.id }) { exam ->
        ExamListItem(exam = exam, onClick = { onExamClick(exam) })
    }
}
```

## Form Template (kısa statik form — Column + verticalScroll)

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmissionFormContent(
    uiState: SubmissionFormUiState,
    onTitleChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("submissionForm.screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.submission_form_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("submissionForm.backButton")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_navigate_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()) // fixed, short form → no LazyColumn needed
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = uiState.title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.submission_form_title_label)) },
                singleLine = true,
                isError = uiState.titleError != null,
                supportingText = uiState.titleError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth().testTag("submissionForm.titleField"),
            )
            Button(
                onClick = onSubmit,
                enabled = uiState.isSubmitEnabled && !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().testTag("submissionForm.submitButton"),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.action_submit))
                }
            }
        }
    }
}
```

## Custom Layout Template (header kart + içerik)

```kotlin
Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
    // Card-style header — content hosted inside a styled container does not set its own background.
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("examDetail.headerCard"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(uiState.title, style = MaterialTheme.typography.titleLarge)
            Text(uiState.subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    // ... content ...
}
```

## Placeholder Replacements

| Placeholder | Replace With | Example |
|-------------|--------------|---------|
| `{FeatureName}` | Feature adı (PascalCase) | `ExamList`, `SubmissionForm` |
| `{featureName}` | Feature adı (camelCase) / testTag prefix | `examList`, `submissionForm` |
| `{Item}` | Liste öğesi modeli | `Exam`, `Lesson` |

## Key Requirements

### ✅ Zorunlu Öğeler

1. **Stateless imza** — sadece `uiState: XUiState` + event lambda'ları + `modifier: Modifier = Modifier`. `ViewModel`/`NavController`/repository **parametresi yok**.
2. **State hoisting** — `remember` yalnızca saf-UI durumu için (scroll pozisyonu, genişlet/daralt). İş durumu `uiState`'ten gelir.
3. **Üç durum ele alınır** — `isLoading`, `error`, `isEmpty` ve içerik; `when {}` ile.
4. **Tema tokenleri** — `MaterialTheme.colorScheme.*` + `MaterialTheme.typography.*`.
5. **testTag** — her etkileşimli/doğrulanan öğede `Modifier.testTag("{featureName}.{element}")`.
6. **Türkçe metin** — `stringResource(R.string.x)`; positional arg gerekirse `stringResource(R.string.x, arg)`.
7. **`@Preview`** — en az bir dolu + bir boş/hata state preview'ı, `ViFiTheme` ile sarılı.

### ❌ Yasak Desenler

- ❌ `Content` içinde `hiltViewModel()` / `viewModel` parametresi
- ❌ `Content` içinde Firebase / repository / coroutine çağrısı
- ❌ Hardcoded renk (`Color(0xFF...)`), font (`fontSize = 16.sp`), string (`"Sınavlar"`)
- ❌ Uzun/dinamik liste için `Column` + `verticalScroll`
- ❌ XML layout (Activity/Fragment)
- ❌ `Content` içinde elle `NavController.navigate(...)`

## Screen İçinde Kullanım

```kotlin
@Composable
fun ExamListScreen(
    onNavigateToExamDetail: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ExamListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // ... events ...
    ExamListContent(
        uiState = uiState,
        onExamClick = viewModel::onExamClick,
        onRetry = viewModel::loadExams,
        onBack = onNavigateBack,
    )
}
```

## Yaygın UI Öğeleri

### Butonlar
```kotlin
Button(onClick = onPrimary, modifier = Modifier.testTag("x.primaryButton")) {
    Text(stringResource(R.string.action_continue))
}
OutlinedButton(onClick = onSecondary, modifier = Modifier.testTag("x.secondaryButton")) {
    Text(stringResource(R.string.action_cancel))
}
```

### Text Field
```kotlin
OutlinedTextField(
    value = uiState.query,
    onValueChange = onQueryChange,
    label = { Text(stringResource(R.string.search_hint)) },
    singleLine = true,
    modifier = Modifier.fillMaxWidth().testTag("x.searchField"),
)
```

### Görsel (Coil)
```kotlin
AsyncImage(
    model = exam.thumbnailUrl,
    contentDescription = null, // decorative → null; if meaningful, stringResource(...)
    contentScale = ContentScale.Crop,
    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
)
```

### Metin (label)
```kotlin
Text(text = exam.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
Text(text = exam.kind, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
```

### Kart / Container
```kotlin
Card(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
) { /* content */ }
```

## Checklist

Bir `Content` üretince doğrula:
- [ ] İmza stateless: `uiState` + lambda'lar + `modifier`, `viewModel`/`NavController` yok
- [ ] `isLoading` / `error` / `isEmpty` / içerik durumları ele alınmış
- [ ] Liste `LazyColumn`/`LazyVerticalGrid` + stabil `key`; kısa form için `verticalScroll` makul
- [ ] Renk `MaterialTheme.colorScheme`, tipografi `MaterialTheme.typography`
- [ ] Metin `stringResource(...)`; hardcoded string yok
- [ ] Her etkileşimli öğede `Modifier.testTag("{featureName}.{element}")`
- [ ] İkonlarda Türkçe `contentDescription`; dekoratif görselde `null`
- [ ] Geri = `TopAppBar` navigationIcon (ikon-only); içeriğe gömülü custom back yok
- [ ] En az bir dolu + bir boş/hata `@Preview` (`ViFiTheme` ile)
- [ ] Firebase/repository/coroutine çağrısı yok

---

**See Also:**
- `screen.md` — stateful Screen (state buraya bu dosyadan verilir)
- `viewmodel.md` — `uiState` tipini üretir
- `accessibility.md` — testTag / semantics / contentDescription / dinamik yazı ölçeği
- `.claude/skills/new-list-item/SKILL.md` — `{Item}ListItem` satır composable'ı
- `.claude/rules/ui-components.md` — UI bileşen rehberi
- `.claude/rules/localization.md` — strings.xml + positional args
