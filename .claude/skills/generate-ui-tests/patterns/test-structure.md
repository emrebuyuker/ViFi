# ViFi — Compose UI Test Yapısı (Given/When/Then)

## Genel Bakış

ViFi Compose UI testleri **Given-When-Then** düzeninde ve testTag tabanlı yazılır. İki rule türü
vardır (iOS'taki tek `XCUIApplication.launch()` yaklaşımının Android karşılığı):

- **Content testi (varsayılan):** `createComposeRule()` + durumsuz `XContent(uiState = ..., onX = {})`.
  Cihaz başlatması/Hilt yok; craft edilmiş `XUiState` ile her durum anında test edilir. Compose test
  clock otomatik senkronize eder — `waitForExistence`/`sleep` yok.
- **Uçtan uca (E2E) testi (yalnız ana akışlar):** `createAndroidComposeRule<HiltTestActivity>()` +
  Hilt test modülü (Fake repository) + gerçek `NavHost`. Gerçek async için `waitUntil`.

## Test sınıfı şablonu (content testi)

```kotlin
// app/src/androidTest/java/com/salticusteam/vifi/feature/<ad>/<ScreenName>ScreenTest.kt
package com.salticusteam.vifi.feature.<ad>

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class <ScreenName>ScreenTest {

    // iOS setUp()/app.launch() equivalent — but no device/Hilt; each test sets up its own content.
    @get:Rule
    val composeRule = createComposeRule()

    // --- State rendering ---
    @Test
    fun <screen>Screen_when<Condition>_shows<Element>() {
        // Given
        composeRule.setContent {
            ViFiTheme {
                <ScreenName>Content(
                    uiState = <ScreenName>UiState(/* crafted state */),
                    onItemClick = {},
                    onRetry = {},
                    onBack = {},
                )
            }
        }

        // Then
        composeRule.onNodeWithTag("<screen>.<element>").assertIsDisplayed()
    }

    // --- Interaction ---
    @Test
    fun <screen>Screen_when<Action>_invokes<Callback>() {
        // Given
        var invokedArg: String? = null
        composeRule.setContent {
            ViFiTheme {
                <ScreenName>Content(
                    uiState = <ScreenName>UiState(/* ... */),
                    onItemClick = { invokedArg = it },
                    onRetry = {},
                    onBack = {},
                )
            }
        }

        // When
        composeRule.onNodeWithTag("<screen>.item.id1").performClick()

        // Then
        assertEquals("id1", invokedArg)
    }
}
```

## Test üretim stratejisi

Testleri şu kategorilerde, bu sırayla üret:

### 1. State rendering (Öncelik: Yüksek)

Durumsuz content'e her `UiState` varyantını geçir; ilgili `testTag`'in göründüğünü doğrula.

```kotlin
// Loading
@Test
fun examListScreen_whenLoading_showsProgressIndicator() {
    composeRule.setContent {
        ViFiTheme {
            ExamListContent(ExamListUiState(isLoading = true), {}, {}, {})
        }
    }
    composeRule.onNodeWithTag("examList.loading").assertIsDisplayed()
}

// Success
@Test
fun examListScreen_whenExamsLoaded_showsItem() {
    composeRule.setContent {
        ViFiTheme {
            ExamListContent(
                ExamListUiState(exams = listOf(Exam("e1", "Fizik I Vize", "phys101", "pdf", 2023))),
                {}, {}, {},
            )
        }
    }
    composeRule.onNodeWithTag("examList.item.e1").assertIsDisplayed()
}
```

### 2. Birincil kullanıcı aksiyonları (Öncelik: Yüksek)

Content testinde "aksiyon" = **hoisted lambda'nın doğru arg'la çağrıldığını** doğrulamak.

```kotlin
// List item click (iOS tapCell → navigate equivalent; lambda verification in content)
@Test
fun examListScreen_whenItemClicked_invokesCallbackWithId() {
    var clickedId: String? = null
    composeRule.setContent {
        ViFiTheme {
            ExamListContent(
                ExamListUiState(exams = listOf(Exam("e1", "Fizik I Vize", "phys101", "pdf", 2023))),
                onExamClick = { clickedId = it }, onRetry = {}, onBack = {},
            )
        }
    }

    composeRule.onNodeWithTag("examList.item.e1").performClick()

    assertEquals("e1", clickedId)
}

// Form input + submit
@Test
fun phoneAuthScreen_whenSubmitClicked_invokesOnSubmit() {
    var submitted = false
    composeRule.setContent {
        ViFiTheme {
            PhoneAuthContent(
                PhoneAuthUiState(phone = "5551112233", isPhoneValid = true),
                onPhoneChange = {}, onSubmit = { submitted = true }, onBack = {},
            )
        }
    }

    composeRule.onNodeWithTag("phoneAuth.submitButton").performClick()

    assertEquals(true, submitted)
}
```

### 3. Durum değişiklikleri (Öncelik: Orta)

```kotlin
// Enabled/disabled — with two separate UiStates
@Test
fun phoneAuthScreen_whenPhoneBlank_submitDisabled() {
    composeRule.setContent {
        ViFiTheme { PhoneAuthContent(PhoneAuthUiState(phone = ""), {}, {}, {}) }
    }
    composeRule.onNodeWithTag("phoneAuth.submitButton").assertIsNotEnabled()
}

// Callback after text input
@Test
fun universityListScreen_whenSearchTyped_invokesOnQueryChange() {
    var query: String? = null
    composeRule.setContent {
        ViFiTheme {
            UniversityListContent(
                UniversityListUiState(), onQueryChange = { query = it },
                onUniversityClick = {}, onBack = {},
            )
        }
    }

    composeRule.onNodeWithTag("universityList.searchField").performTextInput("Boğaziçi")

    assertEquals("Boğaziçi", query)
}
```

### 4. Hata durumları (Öncelik: Orta)

iOS launch-arg (`--network-offline`, `--empty-data`) yerine **durumu enjekte et**: content testinde
craft `UiState`, E2E'de yapılandırılmış `Fake...Repository`. Hata metnini düz literal yerine hata
tipinin `userMessage`'ından ya da string kaynağından doğrula.

```kotlin
// Empty state
@Test
fun examListScreen_whenNoData_showsEmptyState() {
    composeRule.setContent {
        ViFiTheme { ExamListContent(ExamListUiState(exams = emptyList()), {}, {}, {}) }
    }
    composeRule.onNodeWithTag("examList.empty").assertIsDisplayed()
}

// Network error (via the error type's userMessage)
@Test
fun examListScreen_whenNetworkError_showsErrorMessage() {
    composeRule.setContent {
        ViFiTheme {
            ExamListContent(
                ExamListUiState(error = ArchiveError.Offline.userMessage), {}, {}, {},
            )
        }
    }
    composeRule.onNodeWithTag("examList.errorMessage").assertIsDisplayed()
}

// Validation error
@Test
fun phoneAuthScreen_whenInvalidPhone_showsValidationError() {
    composeRule.setContent {
        ViFiTheme {
            PhoneAuthContent(
                PhoneAuthUiState(phone = "123", error = AuthError.InvalidPhone.userMessage),
                {}, {}, {},
            )
        }
    }
    composeRule.onNodeWithTag("phoneAuth.errorMessage").assertIsDisplayed()
}
```

### 5. Gezinme / Sayfalama (Öncelik: Düşük — E2E)

Gerçek gezinme ve sayfalama cihazda, Hilt + Fake repository ile test edilir; ekran başına Screen
Robot kullanılır (bkz. `screen-robot.md`).

```kotlin
// Scroll down in a lazy list to load more (iOS scrollToBottom equivalent)
@Test
fun `exam list when scrolled to bottom loads more items`() {
    examListRobot(composeRule) {
        assertListDisplayed()
        scrollToExam("e20")
        assertExamVisible("e20")
    }
}

// Pull to refresh
@Test
fun `exam list when pulled to refresh reloads data`() {
    composeRule.onNodeWithTag("examList.list").performTouchInput { swipeDown() }
    composeRule.waitForTag("examList.item.e1")
    composeRule.onNodeWithTag("examList.item.e1").assertIsDisplayed()
}
```

## Anti-desenler (YAPMA)

### ❌ Görünür metinle node bulma

```kotlin
// DON'T — depends on Turkish copy and translation; fragile
composeRule.onNodeWithText("Sınavlar").performClick()
```

```kotlin
// DO — testTag (contract). Use text only for VERIFICATION, read from the string resource.
composeRule.onNodeWithTag("examList.title").performClick()
```

### ❌ Thread.sleep ile bekleme

```kotlin
// DON'T
Thread.sleep(2000)
composeRule.onNodeWithTag("examList.item.e1").assertIsDisplayed()
```

```kotlin
// DO — clock is automatic in a content test; use waitUntil in E2E
composeRule.waitForTag("examList.item.e1")
composeRule.onNodeWithTag("examList.item.e1").assertIsDisplayed()
```

### ❌ Her şeyi E2E yazmak

```kotlin
// DON'T — unnecessarily expensive E2E for a single-state render (Hilt + NavHost + device)
```

```kotlin
// DO — state render + lambda verification with a content test; E2E only for the main flow
```

### ❌ ViFiTheme olmadan render

```kotlin
// DON'T — theme/color/typography won't resolve, diverges from the real app
composeRule.setContent { ExamListContent(/* ... */) }
```

```kotlin
// DO
composeRule.setContent { ViFiTheme { ExamListContent(/* ... */) } }
```

### ❌ İç implementasyonu test etmek

Testler **davranışı** (görünen durum + çağrılan lambda) doğrular; ViewModel iç alanlarını ya da
özel fonksiyonları değil. ViewModel mantığı birim testiyle kapsanır (`generate-unit-tests`).

## Senkronizasyon kılavuzu (iOS timeout kılavuzunun karşılığı)

```kotlin
// Content test: node is ready right after setContent — NO waiting
composeRule.onNodeWithTag("examList.item.e1").assertIsDisplayed()

// E2E async (result of suspend/callbackFlow): waitUntil
composeRule.waitUntil(timeoutMillis = 3_000) {
    composeRule.onAllNodesWithTag("examList.item.e1").fetchSemanticsNodes().isNotEmpty()
}

// If you need to drive animation/timing manually (rarely):
composeRule.mainClock.autoAdvance = false
composeRule.mainClock.advanceTimeBy(500)

// Thread.sleep / fixed delay: ALWAYS FORBIDDEN
```

## Yardımcılar ve Robot

Tekrarlı gezinme/kurulum için **Screen Robot** (bkz. `screen-robot.md`); ortak düşük seviye aksiyonlar
için `ComposeTestExtensions.kt` (`waitForTag`, `clearAndType`). E2E kurulumu tek `@Before`'da toplanır.

```kotlin
@HiltAndroidTest
class ExamBrowseFlowTest {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.setContent { ViFiTheme { ViFiNavHost() } }
    }

    @Test
    fun `exam list when item tapped navigates to detail`() {
        examListRobot(composeRule) {
            assertListDisplayed()
            clickExam("e1")
        }
        examDetailRobot(composeRule) {
            assertDisplayed()
        }
    }
}
```

## Test adlandırma deseni

iOS `test_{action}_{condition}_{expectedResult}` deseni iki geçerli Compose stiline çevrilir
(projede tutarlı ol):

```
// Stil A — content testi (prefix'li camel):
<screen>Screen_when<Condition>_<expectedResult>()
  examListScreen_whenExamsLoaded_showsItem
  examListScreen_whenItemClicked_invokesCallbackWithId
  phoneAuthScreen_whenInvalidPhone_showsValidationError

// Stil B — E2E/akış (backtick, okunabilir):
`exam list when item tapped navigates to detail`()
```

## Tam örnek

```kotlin
// app/src/androidTest/java/com/salticusteam/vifi/feature/universitylist/UniversityListScreenTest.kt
package com.salticusteam.vifi.feature.universitylist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.University
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UniversityListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sample = University(id = "u1", name = "Boğaziçi Üniversitesi", city = "İstanbul")

    // --- State rendering ---

    @Test
    fun universityListScreen_whenLoaded_showsItem() {
        composeRule.setContent {
            ViFiTheme {
                UniversityListContent(
                    uiState = UniversityListUiState(universities = listOf(sample)),
                    onQueryChange = {}, onUniversityClick = {}, onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("universityList.item.u1").assertIsDisplayed()
    }

    @Test
    fun universityListScreen_whenError_showsErrorMessage() {
        composeRule.setContent {
            ViFiTheme {
                UniversityListContent(
                    uiState = UniversityListUiState(error = ArchiveError.Offline.userMessage),
                    onQueryChange = {}, onUniversityClick = {}, onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("universityList.errorMessage").assertIsDisplayed()
    }

    @Test
    fun universityListScreen_whenEmpty_showsEmptyState() {
        composeRule.setContent {
            ViFiTheme {
                UniversityListContent(
                    uiState = UniversityListUiState(universities = emptyList()),
                    onQueryChange = {}, onUniversityClick = {}, onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("universityList.empty").assertIsDisplayed()
    }

    // --- Interaction ---

    @Test
    fun universityListScreen_whenItemClicked_invokesCallbackWithId() {
        var clickedId: String? = null
        composeRule.setContent {
            ViFiTheme {
                UniversityListContent(
                    uiState = UniversityListUiState(universities = listOf(sample)),
                    onQueryChange = {}, onUniversityClick = { clickedId = it }, onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("universityList.item.u1").performClick()

        assertEquals("u1", clickedId)
    }

    @Test
    fun universityListScreen_whenSearchTyped_invokesOnQueryChange() {
        var query: String? = null
        composeRule.setContent {
            ViFiTheme {
                UniversityListContent(
                    uiState = UniversityListUiState(),
                    onQueryChange = { query = it }, onUniversityClick = {}, onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("universityList.searchField").performTextInput("Boğaziçi")

        assertEquals("Boğaziçi", query)
    }

    @Test
    fun universityListScreen_whenBackClicked_invokesOnBack() {
        var back = false
        composeRule.setContent {
            ViFiTheme {
                UniversityListContent(
                    uiState = UniversityListUiState(universities = listOf(sample)),
                    onQueryChange = {}, onUniversityClick = {}, onBack = { back = true },
                )
            }
        }

        composeRule.onNodeWithTag("universityList.backButton").performClick()

        assertEquals(true, back)
    }
}
```

---

**Ayrıca Bkz (See Also):**
- `../SKILL.md` — Compose UI testi üretme kılavuzu (content + E2E, testTag, kategoriler)
- `screen-robot.md` — Screen Robot deseni (akış/tekrar için; iOS "no page objects" karşılığı)
- `.claude/rules/testing.md` — ViFi test kuralları (JUnit + Turbine + Compose, testTag sözleşmesi)
- `.claude/rules/ui-components.md` — geri butonu (`TopAppBar` ikon-only), tema/tipografi, testTag
- `.claude/rules/localization.md` — strings.xml + stringResource (metni kaynaktan doğrula)
- Android resmi [Compose test rehberi](https://developer.android.com/jetpack/compose/testing)
