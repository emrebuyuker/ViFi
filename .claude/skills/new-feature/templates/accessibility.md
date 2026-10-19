# ViFi-Android - Accessibility & testTag Rehberi

⚠️ **ÖNEMLİ**: ViFi, erişilebilirliği/test kancalarını **Composable içinde inline** verir — ayrı bir "identifiers" sabit dosyası **yok**. İki ayrı mekanizma vardır ve karıştırılmaz:
- **`Modifier.testTag("<ekran>.<öğe>")`** → yalnızca testlerin öğeyi bulması içindir (iOS `accessibilityIdentifier` karşılığı). **UI testleriyle sözleşmedir; mevcut testTag'leri değiştirme.**
- **`contentDescription`** (Icon/Image/semantics) → gerçek ekran okuyucu (TalkBack) etiketidir, kullanıcıya görünür → **Türkçe ve `strings.xml`'de**.

> **Legacy not:** Bugünkü XML'de bu `android:contentDescription` + Espresso'nun `withId(R.id.x)` kullanımıdır. Compose'da `R.id` yoktur; test erişimi `testTag`, semantik etiket `contentDescription`'tır. Göçteki yeri: `ANDROID-PARITY-ROADMAP.md` → Faz 1 (erişilebilirlik + UI test altyapısı).

## ⚠️ ZORUNLU: Her etkileşimli/doğrulanan öğe bir testTag alır — helper composable'lar dahil

Her `Button`/`IconButton`/`OutlinedTextField`/tıklanabilir `Card`/liste satırı/loading/empty/error öğesine `Modifier.testTag("<ekran>.<öğe>")` ver — **istisnasız**. En sık atlananlar top-level composable'lar değil, **bir helper fonksiyonun ürettiği alt öğelerdir** (`FeatureRow(...)`, `StatChip(...)` vb.). Helper bir `id` parametresi alır ve çocuk testTag'lerini ondan türetir.

```kotlin
// ❌ DON'T — the helper's generated elements have no testTag
@Composable
private fun FeatureRow(text: String) {
    Row {
        Icon(Icons.Default.Check, contentDescription = null) // ⚠️ no test access
        Text(text)
    }
}

// ✅ DO — the helper takes an id and derives child testTags
@Composable
private fun FeatureRow(text: String, id: String) {
    Row(modifier = Modifier.testTag("${id}.row")) {
        Icon(
            Icons.Default.Check,
            contentDescription = null, // decorative → null
            modifier = Modifier.testTag("${id}.icon"),
        )
        Text(text, modifier = Modifier.testTag("${id}.label"))
    }
}
```

## Pattern

```
<ekran>.<öğeAdı>
```
`<ekran>` = feature camelCase (`examList`), `<öğeAdı>` = camelCase öğe (`backButton`). Ayraç **nokta** (`.`).

## Content Dosyalarında

testTag ilgili öğenin `Modifier`'ına inline eklenir:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamListContent(
    uiState: ExamListUiState,
    onExamClick: (Exam) -> Unit,
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
                            contentDescription = stringResource(R.string.cd_navigate_back), // Turkish, strings.xml
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).testTag("examList.list"),
        ) {
            items(uiState.exams, key = { it.id }) { exam ->
                ExamListItem(exam = exam, onClick = { onExamClick(exam) })
            }
        }
    }
}
```

## Naming Convention
### Pattern: `<ekran>.<öğeAdı>`

| Öğe Tipi | Örnek testTag |
|----------|---------------|
| Ekran kökü | `examList.screen` |
| Liste (LazyColumn) | `examList.list` |
| Grid | `examList.grid` |
| Buton | `submissionForm.submitButton` |
| Geri (IconButton) | `examList.backButton` |
| TextField | `login.phoneField` |
| Liste satırı | `examList.examCard` |
| Loading | `examList.loading` |
| Empty state | `examList.empty` |
| Error state | `examList.error` |

### Feature örnekleri

**ExamList**:
```kotlin
Modifier.testTag("examList.screen")
Modifier.testTag("examList.list")
Modifier.testTag("examList.backButton")
Modifier.testTag("examList.retryButton")
```

**Login (Phone Auth +90)**:
```kotlin
Modifier.testTag("login.phoneField")
Modifier.testTag("login.sendCodeButton")
Modifier.testTag("login.otpField")
Modifier.testTag("login.verifyButton")
```

## Liste Satırı testTag

Liste satırı composable'ı da testTag alır; birden çok satır arasında ayrışması için `key`/`id` ekleyebilirsin:

```kotlin
@Composable
fun ExamListItem(exam: Exam, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        // Make the row a single a11y node: TalkBack reads the whole card as one.
        modifier = modifier
            .fillMaxWidth()
            .testTag("examList.examCard")
            .semantics(mergeDescendants = true) { /* contentDescription here if needed */ },
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(exam.title, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("examList.examCard.title"))
            Text("${exam.year} · ${exam.kind}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (exam.isLocked) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = stringResource(R.string.cd_exam_locked), // meaningful → Turkish label
                    modifier = Modifier.testTag("examList.examCard.lockIcon"),
                )
            }
        }
    }
}
```

## contentDescription kuralları (TalkBack)

- **Dekoratif görsel/ikon** (metin zaten yanında): `contentDescription = null`.
- **Anlamlı/etkileşimli ikon** (geri, kilit, menü): `contentDescription = stringResource(R.string.cd_...)` — **Türkçe, strings.xml**.
- **AsyncImage** (Coil): içerik bilgi taşıyorsa `stringResource(...)`, dekoratifse `null`.
- **Kart/bileşik satır**: `Modifier.semantics(mergeDescendants = true)` ile tek düğüm; gerekirse `clearAndSetSemantics { contentDescription = ... }` ile tek, temiz bir okuma ver.
- `cd_` prefix'i, görünür metin string'lerinden ayırmak için önerilir (`cd_navigate_back`, `cd_exam_locked`).

## Dinamik Yazı Ölçeği (Dynamic Type karşılığı)

Compose, sistem yazı ölçeğini (`fontScale`) `MaterialTheme.typography` ile **otomatik** uygular — iOS'taki `configureScaledFont(...)` gibi elle bir şey yazmana gerek yok. Kurallar:
- `Text(style = MaterialTheme.typography.<style>)` kullan; `fontSize = 16.sp` sabiti gömme (ölçeklenmeyi bozar).
- **Sabit yükseklikli container'dan kaçın** (`Modifier.height(48.dp)` içine çok satırlı metin → büyük ölçekte kırpılır). Yüksekliği içeriğe bırak veya `Modifier.heightIn(min = ...)` kullan.
- Metinleri `maxLines` + `TextOverflow.Ellipsis` ile sınırlarken, erişilebilirliği koru (kritik bilgi kesilmesin).
- Aşırı büyümeyi gerçekten sınırlaman gerekiyorsa, dikkatle `LocalDensity` üzerinden `fontScale`'i clamp'le — ama bu son çaredir; önce esnek layout tercih et:
  ```kotlin
  // Last resort: cap the scale (e.g. 1.5x). Prefer a flexible layout first.
  val clamped = LocalDensity.current.fontScale.coerceAtMost(1.5f)
  CompositionLocalProvider(
      LocalDensity provides Density(LocalDensity.current.density, clamped)
  ) { /* content */ }
  ```
- Dokunma hedefleri en az `48.dp` (`Modifier.minimumInteractiveComponentSize()` Material3'te varsayılan gelir).

## UI Testlerinde Kullanım

```kotlin
// app/src/androidTest/java/com/salticusteam/vifi/feature/examlist/ExamListScreenTest.kt
@RunWith(AndroidJUnit4::class)
class ExamListScreenTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun examCard_click_navigatesToDetail() {
        var navigatedId: String? = null
        composeRule.setContent {
            ViFiTheme {
                ExamListContent(
                    uiState = ExamListUiState(exams = listOf(Exam("e1", "Vize", "math101", "pdf", 2023))),
                    onExamClick = { navigatedId = it.id },
                    onRetry = {}, onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("examList.list").assertExists()
        composeRule.onNodeWithTag("examList.examCard").performClick()
        assertEquals("e1", navigatedId)
    }

    @Test
    fun emptyState_isShown_whenNoExams() {
        composeRule.setContent { ViFiTheme { ExamListContent(ExamListUiState(), {}, {}, {}) } }
        composeRule.onNodeWithTag("examList.empty").assertIsDisplayed()
    }
}
```

## Yaygın Hatalar

### ❌ Yanlış: ayrı sabit dosyası
```kotlin
object ExamListTags { const val screen = "examList.screen" } // ViFi does not use this
```

### ✅ Doğru: inline testTag
```kotlin
Modifier.testTag("examList.screen")
```

### ❌ Yanlış: contentDescription'a hardcoded Türkçe
```kotlin
Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri") // hardcoded string
```

### ✅ Doğru: strings.xml'den
```kotlin
Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_navigate_back))
```

### ❌ Yanlış: contentDescription'ı test id gibi kullanmak
contentDescription TalkBack içindir, test bulma için **testTag** kullan.

## testTag / contentDescription Checklist

Her `Content` için:
- [ ] Ekran kökünde `testTag("<ekran>.screen")`
- [ ] Tüm butonlarda (`Button`/`IconButton`) testTag
- [ ] Tüm text field'larda testTag
- [ ] Liste/grid'de testTag + satır composable'ında testTag
- [ ] Loading / empty / error öğelerinde testTag
- [ ] Helper composable'ların ürettiği öğelerde testTag (`id` parametresinden türetilmiş)
- [ ] Anlamlı/etkileşimli ikon & görsellerde Türkçe `contentDescription` (strings.xml, `cd_` prefix)
- [ ] Dekoratif görsellerde `contentDescription = null`
- [ ] Bileşik satırda `semantics(mergeDescendants = true)`
- [ ] Hardcoded `sp`/sabit yükseklik yok → dinamik yazı ölçeği korunuyor
- [ ] Dokunma hedefleri ≥ 48.dp
- [ ] **Mevcut testTag'ler değiştirilmedi** (UI testleriyle sözleşme)

## Quick Reference

| Öğe | testTag örneği | Test erişimi |
|-----|----------------|--------------|
| Button | `"submissionForm.submitButton"` | `onNodeWithTag("submissionForm.submitButton")` |
| TextField | `"login.phoneField"` | `onNodeWithTag("login.phoneField").performTextInput(...)` |
| Text | `"examDetail.titleLabel"` | `onNodeWithTag("examDetail.titleLabel").assertTextEquals(...)` |
| LazyColumn | `"examList.list"` | `onNodeWithTag("examList.list").assertExists()` |
| Card/satır | `"examList.examCard"` | `onNodeWithTag("examList.examCard").performClick()` |
| Icon (IconButton) | `"examList.backButton"` | `onNodeWithTag("examList.backButton").performClick()` |

---

**See Also:**
- `stateless-content.md` — testTag'lerin yaşadığı Content
- `screen.md` — stateful Screen
- `.claude/skills/generate-ui-tests/SKILL.md` — Compose UI test üretimi
- `.claude/rules/ui-components.md` — UI bileşen rehberi
- `.claude/rules/localization.md` — contentDescription metinleri (strings.xml)
- Android [Compose accessibility](https://developer.android.com/jetpack/compose/accessibility)
