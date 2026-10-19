---
name: generate-ui-tests
description: Generate Jetpack Compose UI test scaffolds for ViFi screens following the project's Compose testing patterns — testTag-based node queries, stateless-content tests with createComposeRule, and optional Screen Robots for multi-step flows. Use when the user wants Compose UI tests for a screen or feature.
---

> **Legacy not:** Bugünkü v2.5 kod tabanında (Java, Activity/Fragment + XML, Espresso bile yok)
> hiç UI testi yoktur. Bu skill YENİ/ileriye dönük Kotlin + Compose + MVVM ekranları içindir ve
> **Compose UI testi** üretir. iOS'taki `XCUITest`/`XCUIApplication` yaklaşımının Android karşılığı
> `androidx.compose.ui.test` (`createComposeRule`, `onNodeWithTag`, `performClick`) API'sidir.
> Yol haritasındaki yeri için bkz. repo kökü `ANDROID-PARITY-ROADMAP.md` / `ANDROID-PARITY-DETAIL.md`.
> Legacy ekranlara geriye dönük UI testi yazılmaz; her ekran Compose'a göç ettikçe testTag + UI testi kazanır.

# Generate UI Tests (ViFi — Compose)

## Usage

```
/generate-ui-tests [screen_name | file_path]
```

Örnek: `/generate-ui-tests ExamList`, `/generate-ui-tests feature/examlist/ExamListScreen.kt`.

## Overview

ViFi UI testleri **testTag tabanlı** ve **okunabilir** olur. Node'lar görünür Türkçe metinle değil,
`Modifier.testTag(...)` ile bulunur (iOS'taki zorunlu `accessibilityIdentifier`'ın karşılığı).

İki tür Compose UI testi vardır — hangisini üreteceğine ekranın test ihtiyacına göre karar ver:

1. **Content (stateless) testi — VARSAYILAN.** `createComposeRule()` ile ekranın **durumsuz**
   `XContent(uiState = ..., onX = {})` overload'u doğrudan sürülür. Hilt, gerçek Navigation ve
   cihaz başlatması gerekmez; craft edilmiş `XUiState` ile her durum (loading/error/empty/success)
   anında test edilir. Ekran mantığının çoğu buradan kapsanır (bkz. `.claude/rules/testing.md`).
2. **Uçtan uca (E2E) testi — yalnız ana akışlar.** `createAndroidComposeRule<HiltTestActivity>()`
   + Hilt test modülü ile gerçek `NavHost` + gerçek ViewModel + `Fake...Repository` çalıştırılır.
   Gezinme, geri butonu ve çok-ekranlı akışlar için; pahalı olduğundan sadece kritik yolculuklara yaz.

> İskeletin birim-test tarafı (ViewModel + mapper) ayrı bir skill'dir: `.claude/skills/generate-unit-tests/`.
> Bu skill yalnız **Compose UI** testine odaklanır.

## Steps

1. **Ekranı bul** (ad ya da dosya yolu ile; `feature/<ad>/<ScreenName>Screen.kt`).
2. **testTag'leri çıkar** — `XContent` (ve list-item Composable) içindeki `Modifier.testTag(...)`
   etiketlerini topla. Etiket yoksa placeholder üret ve `// TODO` bırak (aşağıya bkz.).
3. **Kritik kullanıcı akışlarını** `XViewModel` + `XContent`'ten belirle.
4. **Test türünü seç**: durumlar + etkileşim → content testi; gezinme/akış → E2E + Screen Robot.
5. **Test dosyasını üret** — `app/src/androidTest/java/com/salticusteam/vifi/feature/<ad>/<ScreenName>ScreenTest.kt`.
6. Tekrarlı sorgu / çok adımlı akış varsa **Screen Robot** üret (bkz. `patterns/screen-robot.md`).

## testTag Requirements (iOS accessibilityIdentifier karşılığı)

### Desen

```
<ekran>.<öğe>                 // sabit node
<ekran>.item.<id>             // dinamik liste öğesi
```

### Örnekler

```
examList.list
examList.item.e1
examList.errorMessage
examList.retryButton
phoneAuth.phoneField
phoneAuth.submitButton
universityList.searchField
tabs.archive
```

### Composable'da

```kotlin
// feature/examlist/ExamListScreen.kt (inside XContent)
LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .testTag("examList.list"), // ⚠️ REQUIRED
) {
    items(uiState.exams, key = { it.id }) { exam ->
        ExamRow(
            exam = exam,
            onClick = { onExamClick(exam.id) },
            modifier = Modifier.testTag("examList.item.${exam.id}"), // ⚠️ REQUIRED
        )
    }
}

Text(
    text = uiState.error.orEmpty(),
    modifier = Modifier.testTag("examList.errorMessage"), // ⚠️ REQUIRED
)
```

> ⚠️ **testTag UI testleriyle bir SÖZLEŞMEDİR.** Var olan etiketleri yeniden adlandırma — testler
> onlara dayanır. `testTag` makineyle bulma içindir; erişilebilirlik için ayrıca `contentDescription`
> verilir (ikisi farklı amaç, bkz. `.claude/rules/ui-components.md`).

### Etiket eksikse

Placeholder üret ve `XContent`'e eklenmesi için `// TODO` bırak — testi yazarken etiketi uydurup
sessizce geçme, sözleşmeyi görünür kıl:

```kotlin
// TODO: add testTag to ExamListContent:
//   LazyColumn(... Modifier.testTag("examList.list"))
//   ExamRow(... Modifier.testTag("examList.item.${exam.id}"))
composeRule.onNodeWithTag("examList.list").assertIsDisplayed()
```

## Feature Name from Path

Kotlin dosya yolundan ekran ve `testTag`/string prefix'ini türet:

| Kaynak yolu | Screen adı | prefix |
|-------------|------------|--------|
| `feature/examlist/ExamListScreen.kt` | `ExamList` | `examList` |
| `feature/universitylist/UniversityListScreen.kt` | `UniversityList` | `universityList` |
| `feature/auth/PhoneAuthScreen.kt` | `PhoneAuth` | `phoneAuth` |
| `feature/examdetail/ExamDetailScreen.kt` | `ExamDetail` | `examDetail` |

## Generated Test Structure (content test — VARSAYILAN)

```kotlin
// app/src/androidTest/java/com/salticusteam/vifi/feature/examlist/ExamListScreenTest.kt
package com.salticusteam.vifi.feature.examlist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.salticusteam.vifi.core.model.Exam
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExamListScreenTest {

    // iOS XCUITest setUp()/app.launch() equivalent: each test sets up its own content; no device
    // launch / Hilt. The Compose test clock syncs automatically — no need for waitForExistence.
    @get:Rule
    val composeRule = createComposeRule()

    private val sampleExam = Exam(id = "e1", title = "Fizik I Vize", courseId = "phys101", kind = "pdf", year = 2023)

    // --- State rendering ---

    @Test
    fun examListScreen_whenExamsLoaded_showsItem() {
        // Given
        composeRule.setContent {
            ViFiTheme {
                ExamListContent(
                    uiState = ExamListUiState(exams = listOf(sampleExam)),
                    onExamClick = {},
                    onRetry = {},
                    onBack = {},
                )
            }
        }

        // Then
        composeRule.onNodeWithTag("examList.item.e1").assertIsDisplayed()
    }

    @Test
    fun examListScreen_whenLoading_showsProgressIndicator() {
        composeRule.setContent {
            ViFiTheme {
                ExamListContent(
                    uiState = ExamListUiState(isLoading = true),
                    onExamClick = {},
                    onRetry = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("examList.loading").assertIsDisplayed()
    }

    @Test
    fun examListScreen_whenError_showsErrorMessage() {
        composeRule.setContent {
            ViFiTheme {
                ExamListContent(
                    uiState = ExamListUiState(error = "İnternet bağlantısı bulunamadı."),
                    onExamClick = {},
                    onRetry = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("examList.errorMessage").assertIsDisplayed()
    }

    @Test
    fun examListScreen_whenEmpty_showsEmptyState() {
        composeRule.setContent {
            ViFiTheme {
                ExamListContent(
                    uiState = ExamListUiState(exams = emptyList()),
                    onExamClick = {},
                    onRetry = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("examList.empty").assertIsDisplayed()
    }

    // --- Interaction (hoisted lambda verification) ---

    @Test
    fun examListScreen_whenItemClicked_invokesCallbackWithId() {
        // Given
        var clickedId: String? = null
        composeRule.setContent {
            ViFiTheme {
                ExamListContent(
                    uiState = ExamListUiState(exams = listOf(sampleExam)),
                    onExamClick = { clickedId = it },
                    onRetry = {},
                    onBack = {},
                )
            }
        }

        // When
        composeRule.onNodeWithTag("examList.item.e1").performClick()

        // Then — in a stateless content test, "navigation" = the correct lambda called with the correct arg
        assertEquals("e1", clickedId)
    }

    @Test
    fun examListScreen_whenBackClicked_invokesOnBack() {
        var backInvoked = false
        composeRule.setContent {
            ViFiTheme {
                ExamListContent(
                    uiState = ExamListUiState(exams = listOf(sampleExam)),
                    onExamClick = {},
                    onRetry = {},
                    onBack = { backInvoked = true },
                )
            }
        }

        composeRule.onNodeWithTag("examList.backButton").performClick()

        assertEquals(true, backInvoked)
    }
}
```

## Test Categories

Testleri şu kategorilerde, bu öncelik sırasıyla üret (iOS kategorilerinin Compose karşılığı):

### 1. State rendering (Priority: High)

```kotlin
// Loading / error / empty / success, each with a crafted UiState
fun examListScreen_whenLoading_showsProgressIndicator()   // uiState = isLoading = true
fun examListScreen_whenError_showsErrorMessage()          // uiState = error = "..."
fun examListScreen_whenEmpty_showsEmptyState()            // uiState = exams = emptyList()
fun examListScreen_whenExamsLoaded_showsItem()            // uiState = exams = [...]
```

### 2. Interaction (Priority: High)

```kotlin
// Click/input → verify the hoisted lambda is called (content test)
fun examListScreen_whenItemClicked_invokesCallbackWithId()
fun phoneAuthScreen_whenSubmitClicked_invokesOnSubmitWithPhone()
fun examListScreen_whenRetryClicked_invokesOnRetry()
```

### 3. State changes (Priority: Medium)

```kotlin
// Enabled/disabled, text update — with two separate UiStates or after performTextInput
fun phoneAuthScreen_whenPhoneBlank_submitDisabled()
fun phoneAuthScreen_whenPhoneValid_submitEnabled()
```

### 4. Navigation flow (Priority: Medium — E2E + Robot)

```kotlin
// Real NavHost + Hilt test module; TopAppBar icon-only back button
fun examList_whenItemTapped_navigatesToExamDetail()
fun examDetail_whenBackTapped_returnsToExamList()
```

### 5. Pagination / pull-to-refresh (Priority: Low — E2E)

```kotlin
fun examList_whenScrolledToBottom_loadsMoreItems()
fun examList_whenPulledToRefresh_reloadsData()
```

## Common Compose UI Interactions

### Node bulma (metinle değil, testTag ile)

```kotlin
// via testTag (PREFERRED)
composeRule.onNodeWithTag("examList.list").assertIsDisplayed()
composeRule.onNodeWithTag("examList.item.e1")

// Multiple nodes (list item count)
val count = composeRule.onAllNodesWithTag("examList.row", useUnmergedTree = true)
    .fetchSemanticsNodes().size

// Verify via visible text (not for finding) — don't embed literals; read from the string resource
val context = InstrumentationRegistry.getInstrumentation().targetContext
val expected = context.getString(R.string.examList_empty)
composeRule.onNodeWithText(expected).assertIsDisplayed()

// via contentDescription (accessibility verification)
composeRule.onNodeWithContentDescription(context.getString(R.string.cd_navigate_back)).assertExists()
```

### Etkileşimler

```kotlin
// Click
composeRule.onNodeWithTag("examList.item.e1").performClick()

// Enter / clear text
composeRule.onNodeWithTag("phoneAuth.phoneField").performTextInput("5551112233")
composeRule.onNodeWithTag("phoneAuth.phoneField").performTextClearance()

// Scroll within a lazy list (iOS swipeUp / scrollToBottom equivalent)
composeRule.onNodeWithTag("examList.list")
    .performScrollToNode(hasTestTag("examList.item.e20"))

// Pull to refresh (iOS press-drag equivalent)
composeRule.onNodeWithTag("examList.list").performTouchInput { swipeDown() }
```

### Doğrulamalar

```kotlin
// Existence / visibility
composeRule.onNodeWithTag("examList.list").assertIsDisplayed()
composeRule.onNodeWithTag("examList.loading").assertExists()
composeRule.onNodeWithTag("examList.errorMessage").assertDoesNotExist()

// Enabled / selected
composeRule.onNodeWithTag("phoneAuth.submitButton").assertIsEnabled()
composeRule.onNodeWithTag("phoneAuth.submitButton").assertIsNotEnabled()
composeRule.onNodeWithTag("tabs.archive").assertIsSelected()

// Text content (from the string resource; not a plain literal)
composeRule.onNodeWithTag("examList.title").assertTextEquals(context.getString(R.string.examList_title))
```

## Senkronizasyon (iOS waitForExistence karşılığı)

Compose test clock, **content testlerinde** UI'yı otomatik senkronize eder — `waitForExistence(timeout:)`
ya da `Thread.sleep`'e **gerek yoktur**; `setContent` sonrası node hemen sorgulanabilir. Bu, iOS'taki
elle timeout yönetiminin yerine geçen en önemli farktır.

Gerçek async olan **E2E testlerinde** (Fake repository suspend fonksiyonu, callbackFlow) sonucu
`waitUntil` ile bekle — sabit `sleep` yine YASAK:

```kotlin
// Wait until a specific node appears (end-to-end async)
composeRule.waitUntil(timeoutMillis = 3_000) {
    composeRule.onAllNodesWithTag("examList.item.e1").fetchSemanticsNodes().isNotEmpty()
}
composeRule.onNodeWithTag("examList.item.e1").assertIsDisplayed()
```

> `mainClock.autoAdvance = false` + `mainClock.advanceTimeBy(...)` ile animasyon/zamanlamayı elle de
> sürebilirsin; çoğu testte gerek olmaz. Ortak bekleme yardımcısı için bkz. `ComposeTestExtensions.kt`.

## "Launch arguments" karşılığı — durumu enjekte et

iOS'ta durum `app.launchArguments.append("--network-offline")` gibi başlatma argümanlarıyla
kurulurdu. Android'de bunun karşılığı **durumu doğrudan enjekte etmektir**:

- **Content testi (varsayılan):** craft edilmiş `XUiState` geç — ağ kapalı = `error = "..."`,
  boş = `exams = emptyList()`, yükleniyor = `isLoading = true`. Başlatma argümanı gerekmez.
- **E2E testi:** Hilt test modülü ile gerçek repository yerine yapılandırılabilir `Fake...Repository`
  bağla (`@TestInstallIn` / `@UninstallModules`). Hata yolu = `fakeRepository.errorToThrow = ArchiveError.Offline`.

```kotlin
// E2E: network error scenario — iOS "--network-offline" equivalent
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ArchiveModule::class])
object FakeArchiveModule {
    @Provides
    @Singleton
    fun provideArchiveRepository(): ArchiveRepository =
        FakeArchiveRepository().apply { errorToThrow = ArchiveError.Offline }
}
```

## File Organization

```
app/src/androidTest/java/com/salticusteam/vifi/
├── feature/
│   ├── examlist/
│   │   └── ExamListScreenTest.kt
│   ├── universitylist/
│   │   └── UniversityListScreenTest.kt
│   └── auth/
│       └── PhoneAuthScreenTest.kt
├── robot/
│   ├── ExamListRobot.kt
│   ├── UniversityListRobot.kt
│   └── PhoneAuthRobot.kt
└── util/
    ├── ComposeTestExtensions.kt
    └── HiltTestActivity.kt      // E2E için boş @AndroidEntryPoint ComponentActivity
```

> Bu düzen `.claude/rules/testing.md`'deki androidTest ağacıyla aynı hizadadır (`feature/` +
> `util/`), ek olarak `robot/` klasörünü ekler.

### Screen Robot (iOS "no page objects" kararının Android karşılığı)

iOS tarafı Page Object'i reddediyordu; Android'de bunun idiomatik karşılığı **Screen Robot**'tur:
testTag sorgularını saran, fluent (`apply`/`this` dönen) ince bir sınıf. Basit content testleri
**doğrudan** yazılır (Robot gerekmez); Robot, tekrarlı sorgular ve çok-ekranlı akışlar içindir.
Tam desen ve ne zaman kullanılacağı: `patterns/screen-robot.md`.

```kotlin
// A flow test read via a Robot — details in patterns/screen-robot.md
examListRobot(composeRule) {
    assertExamVisible("e1")
    clickExam("e1")
}
examDetailRobot(composeRule) {
    assertDisplayed()
    clickBack()
}
```

## Test Naming Convention

iOS `test_{action}_{condition}_{expectedResult}` deseni Compose testlerinde `@Test` + okunabilir
fonksiyon adına çevrilir. İki geçerli stil vardır; projede tutarlı ol:

```
// Stil A — content testi (kebab-camel, prefix'li):
<screen>_when<Condition>_<expectedResult>()
  examListScreen_whenExamsLoaded_showsItem
  examListScreen_whenItemClicked_invokesCallbackWithId
  phoneAuthScreen_whenPhoneValid_submitEnabled

// Stil B — birim test tarzı backtick (E2E/Robot testlerinde de uygun):
`exam list when item tapped navigates to detail`()
```

## Best Practices

1. ✅ **testTag ile bul** — ASLA görünür Türkçe metinle node arama (metin çeviriyle değişir, tag sözleşme).
2. ✅ **Varsayılan: content testi** — `createComposeRule()` + craft `XUiState`; hızlı, cihaz/Hilt yok.
3. ✅ **E2E'yi ana akışlara ayır** — gezinme/geri butonu/çok-ekran için `createAndroidComposeRule` + Hilt.
4. ✅ **Compose clock'a güven** — content testinde `waitForExistence`/`sleep` yok; E2E'de `waitUntil`.
5. ✅ **Her testi yalıt** — her `@Test` kendi `setContent`'ini kurar; testler arası durum paylaşma.
6. ✅ **Given-When-Then** düzenini uygula (bkz. `patterns/test-structure.md`).
7. ✅ **İçeriği `ViFiTheme` ile sar** — tema/renk/tipografi gerçek uygulamadaki gibi çözülür.
8. ✅ **Metni string kaynağından doğrula** — `context.getString(R.string.x)`; düz Türkçe literal gömme.
9. ✅ **Durumu enjekte et** — iOS launch-arg yerine `UiState` ya da `Fake...Repository` yapılandır.
10. ❌ **ASLA** `Thread.sleep` / sabit gecikme; **ASLA** implementasyon detayını (iç state) test etme.
11. ❌ **ASLA** var olan `testTag`'i yeniden adlandırma (UI testleriyle sözleşme).

## Helper Extensions

```kotlin
// app/src/androidTest/java/com/salticusteam/vifi/util/ComposeTestExtensions.kt
package com.salticusteam.vifi.util

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput

// iOS XCUIElement.waitForExistence(timeout:) equivalent — for E2E async.
fun ComposeTestRule.waitForTag(tag: String, timeoutMillis: Long = 3_000) {
    waitUntil(timeoutMillis) {
        onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
}

// iOS XCUIElement.clearAndType(_:) equivalent.
fun SemanticsNodeInteraction.clearAndType(text: String): SemanticsNodeInteraction = apply {
    performTextClearance()
    performTextInput(text)
}
```

```kotlin
// app/src/androidTest/java/com/salticusteam/vifi/util/HiltTestActivity.kt
package com.salticusteam.vifi.util

import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

// Empty Hilt entry-point Activity for createAndroidComposeRule in E2E tests.
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity()
```

## Example: Complete UI Test File (PhoneAuth — content + state change)

```kotlin
// app/src/androidTest/java/com/salticusteam/vifi/feature/auth/PhoneAuthScreenTest.kt
package com.salticusteam.vifi.feature.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PhoneAuthScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun phoneAuthScreen_whenPhoneBlank_submitDisabled() {
        composeRule.setContent {
            ViFiTheme {
                PhoneAuthContent(
                    uiState = PhoneAuthUiState(phone = ""),
                    onPhoneChange = {},
                    onSubmit = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("phoneAuth.submitButton").assertIsNotEnabled()
    }

    @Test
    fun phoneAuthScreen_whenPhoneValid_submitEnabled() {
        composeRule.setContent {
            ViFiTheme {
                PhoneAuthContent(
                    uiState = PhoneAuthUiState(phone = "5551112233", isPhoneValid = true),
                    onPhoneChange = {},
                    onSubmit = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("phoneAuth.submitButton").assertIsEnabled()
    }

    @Test
    fun phoneAuthScreen_whenPhoneTyped_invokesOnPhoneChange() {
        // Given
        var typed: String? = null
        composeRule.setContent {
            ViFiTheme {
                PhoneAuthContent(
                    uiState = PhoneAuthUiState(),
                    onPhoneChange = { typed = it },
                    onSubmit = {},
                    onBack = {},
                )
            }
        }

        // When
        composeRule.onNodeWithTag("phoneAuth.phoneField").performTextInput("5551112233")

        // Then
        assertEquals("5551112233", typed)
    }

    @Test
    fun phoneAuthScreen_whenError_showsErrorMessage() {
        composeRule.setContent {
            ViFiTheme {
                PhoneAuthContent(
                    uiState = PhoneAuthUiState(error = "Geçersiz telefon numarası."),
                    onPhoneChange = {},
                    onSubmit = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("phoneAuth.errorMessage").assertIsDisplayed()
    }
}
```

---

**See Also:**
- `patterns/screen-robot.md` — Screen Robot deseni (iOS "no page objects" kararının Android karşılığı)
- `patterns/test-structure.md` — Given/When/Then Compose test yapısı ve kategoriler
- `.claude/rules/testing.md` — ViFi test kuralları (JUnit + Turbine + Compose, testTag sözleşmesi)
- `.claude/rules/ui-components.md` — `testTag` + `contentDescription` sözleşmesi, tema/tipografi
- `.claude/rules/localization.md` — strings.xml + stringResource (metni literal yerine kaynaktan doğrula)
- `.claude/skills/new-screen/` — testTag'leri üreten ekran iskelesi
- `.claude/skills/generate-unit-tests/` — ViewModel/mapper birim testi üretimi
- `.claude/skills/generate-mocks/` — `Fake...Repository` üretimi (content + E2E testlerinde kullanılır)
- Android resmi [Compose test rehberi](https://developer.android.com/jetpack/compose/testing)
