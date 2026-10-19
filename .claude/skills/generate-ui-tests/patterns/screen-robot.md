# ViFi — Screen Robot Pattern (Compose UI testleri)

## Genel Bakış

Bu belge, Compose UI testlerinde tekrarlı node sorgularını ve çok adımlı akışları düzenlemek için
kullanılan **Screen Robot** desenini anlatır — iOS tarafındaki "Page Object kullanma, doğrudan yaz"
kararının Android'deki idiomatik karşılığı.

> **iOS → Android uyarlama kararı:** iOS eSimIO Page Object'i tamamen reddediyordu ("fazladan soyutlama
> katmanı"). Android'de ise topluluk genelinde yerleşik olan **Robot pattern** (Jake Wharton)
> kullanılır: Page Object'in ağır sürümü değil, testTag sorgularını saran **ince, akıcı (fluent)** bir
> katmandır. Yani ViFi, "soyutlama = kötü" kuralını değil, **"doğru dozda soyutlama"** kuralını
> benimser: basit durum testleri doğrudan yazılır, çok adımlı/yeniden kullanılan akışlar Robot ile.

## Robot ne zaman kullanılır, ne zaman kullanılmaz

| Durum | Yaklaşım |
|-------|----------|
| Tek ekranın tek durumunu render edip doğrulama (loading/error/empty/success) | **Doğrudan** content testi (Robot yok) |
| Tek bir hoisted lambda'nın çağrıldığını doğrulama | **Doğrudan** content testi |
| Aynı sorgu/aksiyon birçok testte tekrarlıyor | **Screen Robot** |
| Çok adımlı akış (giriş → doğrulama kodu → ana ekran) | **Screen Robot** |
| Uçtan uca (E2E) gezinme: ekran A → ekran B → geri | Ekran başına **Screen Robot** |

Kural: **Önce doğrudan content testi; tekrar/akış çıkınca Robot'a yüksel.** Robot'u erken, tek
kullanımlık node için yazma.

## Screen Robot deseni

Bir Screen Robot, bir ekranın `testTag` sorgularını ve aksiyonlarını saran, her metottan `this`
dönen (akıcı zincirleme) bir sınıftır. `ComposeTestRule`'u alır; cihaz/Hilt bilgisi içermez.

```kotlin
// app/src/androidTest/java/com/salticusteam/vifi/robot/ExamListRobot.kt
package com.salticusteam.vifi.robot

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode

// Thin wrapper over testTag queries for the Exam list screen. Fluent: each action returns `this`.
class ExamListRobot(private val composeRule: ComposeTestRule) {

    fun assertListDisplayed() = apply {
        composeRule.onNodeWithTag("examList.list").assertIsDisplayed()
    }

    fun assertExamVisible(id: String) = apply {
        composeRule.onNodeWithTag("examList.item.$id").assertIsDisplayed()
    }

    fun scrollToExam(id: String) = apply {
        composeRule.onNodeWithTag("examList.list")
            .performScrollToNode(hasTestTag("examList.item.$id"))
    }

    fun clickExam(id: String) = apply {
        composeRule.onNodeWithTag("examList.item.$id").performClick()
    }

    fun assertError() = apply {
        composeRule.onNodeWithTag("examList.errorMessage").assertIsDisplayed()
    }

    fun clickRetry() = apply {
        composeRule.onNodeWithTag("examList.retryButton").performClick()
    }

    fun clickBack() = apply {
        composeRule.onNodeWithTag("examList.backButton").performClick()
    }
}

// Scope-function entry point — reads like the user flow.
fun examListRobot(
    composeRule: ComposeTestRule,
    block: ExamListRobot.() -> Unit,
): ExamListRobot = ExamListRobot(composeRule).apply(block)
```

Kullanım — test, kullanıcı akışı gibi okunur:

```kotlin
examListRobot(composeRule) {
    assertListDisplayed()
    scrollToExam("e20")
    clickExam("e20")
}
```

## Doğrudan yaklaşım (basit test — Robot gereksiz)

Tek durumlu, tek doğrulamalı testlerde Robot katmanı gereksiz boilerplate'tir; doğrudan yaz:

```kotlin
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
```

## Karşılaştırma

| Ölçüt | Doğrudan content testi | Screen Robot |
|-------|------------------------|--------------|
| En iyi olduğu yer | Tek durum render + tek lambda | Tekrarlı sorgu, çok adımlı/E2E akış |
| Dosya sayısı | 1 (test) | 2 (test + robot) |
| Okunabilirlik | Basit testte yüksek | Akışta yüksek (kullanıcı adımları gibi) |
| Yeniden kullanım | Düşük | Yüksek (etiket sorgusu tek yerde) |
| Bakım (testTag değişince) | Her teste dağılır | Robot'ta tek noktada |

## "Tekrarlı sorgum var" senaryosu

**Doğrudan (tekrarlıysa dağınık):**

```kotlin
composeRule.onNodeWithTag("examList.list").assertIsDisplayed()   // test 1
composeRule.onNodeWithTag("examList.list").assertIsDisplayed()   // test 2
composeRule.onNodeWithTag("examList.list").assertIsDisplayed()   // test 3 ...
```

**Robot (tek noktada):**

```kotlin
examListRobot(composeRule) { assertListDisplayed() }
```

## "Karmaşık gezinme akışım var" senaryosu — E2E + çoklu Robot

Birden çok ekrandan geçen uçtan uca testlerde her ekranın kendi Robot'u olur; test, ekranlar arası
geçişi akıcı okur. Geri butonu **her zaman** `TopAppBar` ikon-only `navigationIcon`'u olduğundan
Robot'ta `clickBack()` = `onNodeWithTag("<ekran>.backButton").performClick()` (elle/metinli geri yok,
bkz. `.claude/rules/ui-components.md`).

```kotlin
@HiltAndroidTest
class ExamBrowseFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.setContent {
            ViFiTheme { ViFiNavHost() } // real NavHost + Hilt test module (Fake repository)
        }
    }

    @Test
    fun `exam list when item tapped navigates to detail then back returns to list`() {
        // Given / When / Then — the flow reads across screens via Robots
        examListRobot(composeRule) {
            assertListDisplayed()
            clickExam("e1")
        }
        examDetailRobot(composeRule) {
            assertDisplayed()
            clickBack()
        }
        examListRobot(composeRule) {
            assertListDisplayed()
        }
    }
}
```

## "Ortak aksiyonları paylaşmak istiyorum" senaryosu

Birden çok Robot'ta tekrar eden küçük aksiyonlar (bekleme, geri) için Robot'lar `ComposeTestRule`
uzantılarını (`ComposeTestExtensions.kt`) kullanır; ayrıca bir taban Robot sınıfı yazılabilir:

```kotlin
abstract class BaseScreenRobot(protected val composeRule: ComposeTestRule) {
    fun clickBack(screen: String) = apply {
        composeRule.onNodeWithTag("$screen.backButton").performClick()
    }
}
```

## Doğrudan testten Robot'a taşıma (migration)

### Önce (dağınık, tekrarlı doğrudan)

```kotlin
@Test
fun flow() {
    composeRule.onNodeWithTag("examList.item.e1").assertIsDisplayed()
    composeRule.onNodeWithTag("examList.item.e1").performClick()
    composeRule.onNodeWithTag("examDetail.screen").assertIsDisplayed()
    composeRule.onNodeWithTag("examDetail.backButton").performClick()
}
```

### Sonra (Robot ile akıcı)

```kotlin
@Test
fun `exam list to detail and back`() {
    examListRobot(composeRule) {
        assertExamVisible("e1")
        clickExam("e1")
    }
    examDetailRobot(composeRule) {
        assertDisplayed()
        clickBack()
    }
}
```

## Özet

### ✅ Screen Robot kullan (akış/tekrar için)

- `app/src/androidTest/.../robot/` altında ekran başına Robot sınıfı
- Her aksiyon `apply`/`this` döner (akıcı zincir)
- Node sorgusu yalnız Robot'ta — testTag değişince tek yerde güncellenir
- E2E gezinme akışlarını ekranlar arası Robot'larla oku

### ✅ Doğrudan content testi yaz (basit için)

- Tek durum render + tek lambda doğrulaması
- `createComposeRule()` + craft `XUiState`
- Robot katmanı eklemeden `onNodeWithTag(...)`

### Temel ilkeler

1. **Doğru dozda soyutlama** — ne sıfır (dağınık tekrar) ne aşırı (gereksiz Page Object).
2. **Önce doğrudan, tekrar/akış çıkınca Robot.**
3. **testTag sözleşmesi tek noktada** (Robot'ta) — bakım kolaylığı.
4. **Robot, test clock'u ve Hilt'i bilmez** — yalnız `ComposeTestRule` + testTag.

---

**Ayrıca Bkz (See Also):**
- `../SKILL.md` — Compose UI testi üretme kılavuzu (content + E2E, testTag)
- `test-structure.md` — Given/When/Then Compose test yapısı ve kategorileri
- `.claude/rules/testing.md` — ViFi test kuralları (testTag sözleşmesi, Fake repository)
- `.claude/rules/ui-components.md` — geri butonu (`TopAppBar` ikon-only) ve testTag kuralı
- Android resmi [Compose test rehberi](https://developer.android.com/jetpack/compose/testing)
