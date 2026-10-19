# ViFi-Android - Test Kuralları (Testing Rules)

> **Kapsam:** Test dosyaları
> **Globs:** `["**/src/test/**/*Test.kt", "**/src/androidTest/**/*Test.kt"]`
>
> **Legacy not:** Bugünkü v2.5 kod tabanında (Java, Activity/Fragment) otomatik test
> neredeyse yoktur. Bu kural YENİ/ileriye dönük Kotlin + Compose + MVVM kodu içindir.
> Yol haritasındaki yeri için bkz. repo kökü `ANDROID-PARITY-ROADMAP.md` /
> `ANDROID-PARITY-DETAIL.md`. Yeni yazılan her ViewModel ve mapper için test zorunludur;
> legacy Java ekranlarına geriye dönük test yazılması beklenmez, onlar göç ettikçe test kazanır.

## Test Mimarisi (Testing Architecture)

Bu proje **StateFlow tabanlı MVVM ViewModel'leri** test eder ve varsayılan olarak
**delegate/mock yerine `Fake` repository** kullanır. iOS tarafındaki "delegate + manual mock"
yaklaşımının Android karşılığı budur: ViewModel bir `interface` repository'ye bağımlıdır,
teste `Fake...Repository` enjekte edilir, ViewModel'in ürettiği `StateFlow<XUiState>` doğrulanır.

İki test türü vardır:

- **Birim testleri** (`app/src/test/`, JVM üzerinde çalışır — hızlı, cihazsız):
  - **JUnit4** koşucu + assertion.
  - **kotlinx-coroutines-test** (`runTest`, `StandardTestDispatcher`/`UnconfinedTestDispatcher`) ile coroutine kontrolü.
  - **Turbine** ile `Flow`/`StateFlow` emisyonlarının doğrulanması.
  - Varsayılan sahte: elle yazılmış `Fake...Repository`. Karmaşık/az kullanılan bağımlılıklar için **MockK**.
- **UI testleri** (`app/src/androidTest/`, cihaz/emülatörde çalışır):
  - **Compose test** (`createComposeRule` / `createAndroidComposeRule`, `onNodeWithTag`, `performClick`, `assertIsDisplayed`).

Temel kural: **ViewModel, UI ve gerçek Firebase'den bağımsız test edilir.** Repository bir
`interface` arkasında olduğu için ViewModel testi ağ/dosya sistemine hiç dokunmaz.

## Bağımlılıklar (version catalog)

Test bağımlılıkları `gradle/libs.versions.toml` version catalog'una girer, modülde takma adla kullanılır.

```toml
# gradle/libs.versions.toml (ilgili satırlar)
[versions]
junit = "4.13.2"
coroutines = "1.9.0"
turbine = "1.1.0"
mockk = "1.13.12"
androidxTestExt = "1.2.1"
espresso = "3.6.1"
composeBom = "2024.09.03"

[libraries]
junit = { module = "junit:junit", version.ref = "junit" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidxTestExt" }
androidx-espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
```

```kotlin
// app/build.gradle.kts (ilgili bağımlılıklar)
dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
```

## Birim Test Yapısı (Unit Test Structure)

iOS `XCTestCase` + `setUp`/`tearDown` kalıbının karşılığı: JUnit4 sınıfı + `@Before`/`@After`,
`MainDispatcherRule` ve `runTest`. `wait(for:timeout:)` / `XCTestExpectation` yerine `runTest`
içinde Turbine kullanılır — sahte beklemeler ve `Thread.sleep` yoktur.

```kotlin
package com.salticusteam.vifi.feature.examlist

import app.cash.turbine.test
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.Exam
import com.salticusteam.vifi.fake.FakeArchiveRepository
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ExamListViewModelTest {

    // Replaces iOS tearDown()/setUp() for the main dispatcher: swaps Dispatchers.Main
    // for a TestDispatcher before each test and resets it afterwards.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepository: FakeArchiveRepository
    private lateinit var sut: ExamListViewModel // System Under Test

    @Before
    fun setUp() {
        fakeRepository = FakeArchiveRepository()
        sut = ExamListViewModel(fakeRepository)
    }

    @Test
    fun `loadExams when success updates uiState with exams`() = runTest {
        // Given
        fakeRepository.examsToReturn = listOf(
            Exam(id = "e1", title = "Fizik I Vize", courseId = "fiz-101", kind = "pdf", year = 2023),
        )

        // When / Then — observe the full state transition via Turbine
        sut.uiState.test {
            assertEquals(ExamListUiState(), awaitItem())      // initial state

            sut.loadExams(departmentId = "dep-1")

            assertTrue(awaitItem().isLoading)                 // loading = true
            val loaded = awaitItem()                          // success
            assertFalse(loaded.isLoading)
            assertNull(loaded.error)
            assertEquals(1, loaded.exams.size)
            assertEquals("e1", loaded.exams.first().id)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadExams when repository throws network error sets userMessage`() = runTest {
        // Given
        fakeRepository.errorToThrow = ArchiveError.Offline

        // When
        sut.loadExams(departmentId = "dep-1")
        advanceUntilIdle()

        // Then — assert the final rendered state
        val state = sut.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.exams.isEmpty())
        assertEquals(ArchiveError.Offline.userMessage, state.error)
    }
}
```

Not — `assertNull`/`assertNotNull` da `org.junit.Assert` içindedir. `StandardTestDispatcher`
kullanırken, Turbine akışı ileri taşır; gerekirse `runTest { ... }` içinde
`advanceUntilIdle()` ile tüm bekleyen coroutine'leri çalıştırabilirsin.

## MainDispatcherRule

iOS'ta `NetworkManager.shared.mockNetworkReachable` gibi global bayraklarla yapılan şey,
Android'de `Dispatchers.Main`'in test dispatcher ile değiştirilmesidir. Bunu her test sınıfında
tekrarlamamak için ortak bir JUnit `TestRule` yazılır (tek seferlik, `app/src/test/.../testing/`).

```kotlin
package com.salticusteam.vifi.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

// Swaps Dispatchers.Main for a TestDispatcher so viewModelScope runs deterministically.
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
```

`StandardTestDispatcher` = coroutine'ler elle (`advanceUntilIdle`/Turbine) ilerletilir, sıralamayı
net test etmek için iyidir. StateFlow emisyonlarını basitçe doğrulamak istediğinde kuralı
`MainDispatcherRule(UnconfinedTestDispatcher())` ile kurarsın (anında yürütme). Projede tek bir
varsayılanı tutarlı kullan (öneri: `StandardTestDispatcher`).

**Önemli test tasarımı kararı:** ViewModel, repository çağrılarını `withContext(Dispatchers.IO)`
ile sarmaz — IO'ya geçiş **repository'nin kendi içindedir** (bkz. `firebase-data.md`). Böylece
ViewModel'in tüm işi test dispatcher'ı üzerinde kalır ve testler deterministik olur. ViewModel
`viewModelScope.launch { repository.getExams(...) }` çağırır, gerçek IO dispatcher'ına hiç çıkmaz.

## Fake Repository (iOS Mock Delegate karşılığı)

iOS'taki `MockXViewModelDelegate` (çağrı bayrakları + closure'lar) yerine Android'de ViewModel'in
bağımlı olduğu repository `interface`'inin sahte implementasyonu yazılır. Fake, sonucu
yapılandırılabilir kılar ve çağrı sayımı tutar.

```kotlin
package com.salticusteam.vifi.fake

import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.model.Exam
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

// Hand-written fake: the Android equivalent of iOS's Mock*Delegate.
class FakeArchiveRepository : ArchiveRepository {

    // Configure the outcome per test (success vs. failure).
    var examsToReturn: List<Exam> = emptyList()
    var errorToThrow: ArchiveError? = null

    // Tracking, like the iOS "*Called" flags.
    var getExamsCallCount = 0
        private set
    var lastRequestedDepartmentId: String? = null
        private set

    override suspend fun getExams(departmentId: String): List<Exam> {
        getExamsCallCount++
        lastRequestedDepartmentId = departmentId
        errorToThrow?.let { throw it }
        return examsToReturn
    }

    // For observeExams(...) tests: drive emissions manually from the test body.
    private val examsFlow = MutableSharedFlow<List<Exam>>(replay = 1)
    override fun observeExams(departmentId: String): Flow<List<Exam>> = examsFlow

    suspend fun emitExams(exams: List<Exam>) = examsFlow.emit(exams)
}
```

`Fake` yeniden kullanılabilir olduğu için `app/src/test/.../fake/` altında bir kez yazılır, tüm
ViewModel testleri paylaşır. (Compose preview'ları için de kullanışlıdır.) Tek seferlik,
davranışı doğrulanacak bir bağımlılık için `MockK` tercih edilebilir:

```kotlin
val repository = mockk<ArchiveRepository>()
coEvery { repository.getExams("dep-1") } returns listOf(sampleExam)
// ...
coVerify(exactly = 1) { repository.getExams("dep-1") }
```

Kural: **Önce `Fake`, gerektiğinde `MockK`.** Suspend fonksiyonlar için `every`/`verify`
yerine `coEvery`/`coVerify` kullanılır.

## Test Adlandırma (Test Naming Convention)

iOS `test_{method}_{condition}_{expectedResult}` kalıbı Kotlin'de backtick'li, boşluklu,
okunabilir isme çevrilir (tanımlayıcılar İngilizce):

```
`{method} when {condition} {expectedResult}`

Örnekler:
- `loadExams when success updates uiState with exams`
- `loadExams when network error sets userMessage`
- `loadMore when no more data does not change page`
- `onPhoneSubmit when number invalid emits validation error`
```

## Repository ile ViewModel Testi (Coroutines)

iOS `NetworkManager` + completion handler testinin karşılığı: ViewModel suspend repository
fonksiyonunu çağırır, sonuç `StateFlow`'a yansır. Başarı ve hata yolları ayrı ayrı test edilir.

```kotlin
@Test
fun `loadExams when success exposes exams and no error`() = runTest {
    fakeRepository.examsToReturn = listOf(
        Exam(id = "e1", title = "Kalkülüs Final", courseId = "mat-101", kind = "pdf", year = 2024),
        Exam(id = "e2", title = "Kalkülüs Vize", courseId = "mat-101", kind = "pdf", year = 2024),
    )

    sut.loadExams(departmentId = "math-101")
    advanceUntilIdle()

    val state = sut.uiState.value
    assertEquals(2, state.exams.size)
    assertNull(state.error)
    assertEquals(1, fakeRepository.getExamsCallCount)
    assertEquals("math-101", fakeRepository.lastRequestedDepartmentId)
}

@Test
fun `loadExams when not found sets userMessage and empty list`() = runTest {
    fakeRepository.errorToThrow = ArchiveError.NotFound

    sut.loadExams(departmentId = "math-101")
    advanceUntilIdle()

    val state = sut.uiState.value
    assertTrue(state.exams.isEmpty())
    assertEquals(ArchiveError.NotFound.userMessage, state.error)
}
```

Hata yolunda, kopyaya (Türkçe metne) sıkı bağlanmamak için tercihen hata **tipini** kuran kaynağa
göre doğrula: ViewModel `error.userMessage` sakladığı için `state.error == ArchiveError.NotFound.userMessage`
karşılaştırması hem doğru metni hem doğru tipi garanti eder. Düz string literal'e karşı assert etme.

## Yükleme Durumu Testi (Testing Loading States)

iOS'ta `isLoadingPublic` ile yapılan şey, Android'de tek `UiState` içindeki `isLoading` alanıdır.
Geçişi (`false → true → false`) Turbine ile sırayla doğrula:

```kotlin
@Test
fun `loadExams toggles isLoading true then false`() = runTest {
    fakeRepository.examsToReturn = listOf(sampleExam)

    sut.uiState.test {
        assertFalse(awaitItem().isLoading)   // initial: false
        sut.loadExams(departmentId = "dep-1")
        assertTrue(awaitItem().isLoading)    // during request: true
        assertFalse(awaitItem().isLoading)   // after completion: false
        cancelAndIgnoreRemainingEvents()
    }
}
```

## Sayfalama / Daha Fazla Yükleme Testi (Pagination)

Arşiv listesi sayfalıysa, yeni sayfanın mevcut öğelere **eklendiğini** ve veri bittiğinde sayfanın
artmadığını test et (iOS `loadMore` testlerinin karşılığı):

```kotlin
@Test
fun `loadMore when has more data appends exams`() = runTest {
    fakeRepository.examsToReturn = listOf(examPage1)
    sut.loadExams(departmentId = "dep-1")
    advanceUntilIdle()
    val initialCount = sut.uiState.value.exams.size

    fakeRepository.examsToReturn = listOf(examPage2)
    sut.loadMore()
    advanceUntilIdle()

    val state = sut.uiState.value
    assertTrue(state.exams.size > initialCount)
    assertEquals(2, state.currentPage)
}

@Test
fun `loadMore when no more data does not change page`() = runTest {
    sut.setCanLoadMoreForTest(false) // or arrange state so canLoadMore == false
    val initialPage = sut.uiState.value.currentPage

    sut.loadMore()
    advanceUntilIdle()

    assertEquals(initialPage, sut.uiState.value.currentPage)
}
```

## Repository Map / Dönüşüm Testi (DTO → Domain)

Repository, UI'ya ham Firebase tipi (`DocumentSnapshot`/`DataSnapshot`) **sızdırmaz**; DTO'yu
domain modele map eder (bkz. `firebase-data.md`). Bu map saf bir fonksiyon olmalı ki birim test
edilebilsin — Firebase'e dokunmadan, hızlı. Null/eksik alan davranışını mutlaka test et.

```kotlin
package com.salticusteam.vifi.core.data

import com.salticusteam.vifi.core.model.Exam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExamMapperTest {

    @Test
    fun `toDomain maps all fields`() {
        val dto = ExamDto(id = "e1", title = "Fizik Vize", courseId = "fiz-101", kind = "pdf", year = 2023)

        val exam = dto.toDomain()

        assertEquals(Exam(id = "e1", title = "Fizik Vize", courseId = "fiz-101", kind = "pdf", year = 2023), exam)
    }

    @Test
    fun `toDomain when fields null returns safe defaults`() {
        val dto = ExamDto() // all nulls, as Firestore deserialization can produce

        val exam = dto.toDomain()

        assertEquals("", exam.id)
        assertEquals("", exam.title)
        assertEquals("", exam.courseId)
        assertEquals("", exam.kind)
        assertNull(exam.year)
    }
}
```

**Versioned doc comment bağlantısı:** Bir domain modele yeni alan eklendiğinde (alan
`// Added in <versionName> — <amaç>.` yorumu taşır, versiyon = `app/build.gradle` `versionName`),
o alanın map'lendiğini doğrulayan assertion'ı `ExamMapperTest`'e ekle. Yeni alan + test aynı PR'da gelir.

## UI Testi (Compose UI Test)

iOS `XCUITest` (`XCUIApplication`, `app.buttons["id"].tap()`) karşılığı Compose test API'sidir.
Elemanlar **metinle değil, `testTag` ile** bulunur (erişilebilirlik kimliği sözleşmesi, aşağıya bkz.).

ViewModel'den bağımsız, hızlı UI testi için ekranın **durumsuz (stateless)** overload'u test edilir —
`createComposeRule()` yeter, Activity gerekmez:

```kotlin
package com.salticusteam.vifi.feature.examlist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.salticusteam.vifi.core.model.Exam
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExamListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleExam = Exam(id = "e1", title = "Fizik I Vize", courseId = "fiz-101", kind = "pdf", year = 2023)

    @Test
    fun examListScreen_whenExamsLoaded_showsItem() {
        composeRule.setContent {
            ViFiTheme {
                ExamListContent(
                    uiState = ExamListUiState(exams = listOf(sampleExam)),
                    onExamClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("examList.item.e1").assertIsDisplayed()
    }

    @Test
    fun examListScreen_whenItemClicked_invokesCallbackWithId() {
        var clickedId: String? = null
        composeRule.setContent {
            ViFiTheme {
                ExamListContent(
                    uiState = ExamListUiState(exams = listOf(sampleExam)),
                    onExamClick = { clickedId = it },
                )
            }
        }

        composeRule.onNodeWithTag("examList.item.e1").performClick()

        assertEquals("e1", clickedId)
    }

    @Test
    fun examListScreen_whenError_showsErrorText() {
        composeRule.setContent {
            ViFiTheme {
                ExamListContent(
                    uiState = ExamListUiState(error = "İnternet bağlantısı bulunamadı."),
                    onExamClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("examList.errorMessage").assertIsDisplayed()
    }
}
```

Gerçek navigasyonu/Hilt'i baştan sona (uçtan uca) test etmek gerektiğinde `createAndroidComposeRule<ComponentActivity>()`
ya da Hilt test Activity'si kullanılır; ancak çoğu ekran mantığı durumsuz content testiyle ve
ViewModel birim testiyle kapsanmalı — pahalı uçtan uca testleri yalnızca ana kullanıcı akışları için yaz.

## Yerel Kalıcılık Testi (iOS LocalStore karşılığı)

iOS `LocalStore` testlerinin karşılığı: yerel kalıcılık da bir repository `interface`'i arkasındadır
(örn. oturum/kullanıcı tercihi için DataStore tabanlı `UserPreferencesRepository`). Birim testinde
sahte ya da bellek-içi implementasyon kullanılır; her testte durum sıfırlanır (iOS `setUp`/`tearDown`
içindeki temizliğin karşılığı). DataStore'un kendi davranışını test etmek gerekiyorsa `androidTest`
altında geçici dosyayla instrumentation testi yazılır.

```kotlin
@Test
fun `saveUser then read returns same user`() = runTest {
    val repository = FakeUserPreferencesRepository()
    val user = User(id = "123", phone = "+905551112233")

    repository.saveUser(user)

    assertEquals(user, repository.observeUser().first())
}

@Test
fun `clearUser removes stored user`() = runTest {
    val repository = FakeUserPreferencesRepository()
    repository.saveUser(User(id = "123", phone = "+905551112233"))

    repository.clearUser()

    assertNull(repository.observeUser().first())
}
```

## Test Dosya Organizasyonu (Test File Organization)

Kaynak kodun `feature/` + `core/` paketlemesini test tarafında da aynala. Birim testleri
`app/src/test/`, UI testleri `app/src/androidTest/` altında.

```
app/src/test/java/com/salticusteam/vifi/
├── feature/
│   ├── examlist/
│   │   └── ExamListViewModelTest.kt
│   ├── universitylist/
│   │   └── UniversityListViewModelTest.kt
│   └── auth/
│       └── PhoneAuthViewModelTest.kt
├── core/
│   ├── data/
│   │   ├── ExamMapperTest.kt
│   │   └── repository/
│   │       └── FirebaseArchiveRepositoryTest.kt   # map/hata yolu, gerekirse MockK ile
│   └── util/
│       └── DateFormatterTest.kt
├── fake/
│   ├── FakeArchiveRepository.kt
│   ├── FakeAuthRepository.kt
│   └── FakeUserPreferencesRepository.kt
└── testing/
    └── MainDispatcherRule.kt

app/src/androidTest/java/com/salticusteam/vifi/
├── feature/
│   ├── examlist/
│   │   └── ExamListScreenTest.kt
│   └── auth/
│       └── PhoneAuthScreenTest.kt
└── util/
    └── ComposeTestExtensions.kt
```

## testTag En İyi Uygulamaları (iOS Accessibility Identifiers karşılığı)

iOS'taki zorunlu `accessibilityIdentifier`'ın Android karşılığı `Modifier.testTag(...)`'dir.
UI testleriyle yazılan node sorguları bu etiketlere dayanır — **testTag UI testleriyle bir
SÖZLEŞMEDİR, mevcut etiketleri değiştirme.** (Erişilebilirlik için ayrıca `contentDescription`
verilir; `testTag` makineyle bulma içindir, bkz. `ui-components.md`.)

```kotlin
// ExamListContent içinde
LazyColumn {
    items(uiState.exams, key = { it.id }) { exam ->
        ExamRow(
            exam = exam,
            onClick = { onExamClick(exam.id) },
            modifier = Modifier.testTag("examList.item.${exam.id}"), // ⚠️ ZORUNLU
        )
    }
}

// Hata metni
Text(
    text = uiState.error.orEmpty(),
    modifier = Modifier.testTag("examList.errorMessage"),           // ⚠️ ZORUNLU
)
```

Adlandırma deseni: `"<ekran>.<öğe>"` (dinamik liste öğesinde `"<ekran>.item.<id>"`).
Örnekler: `examList.item.e1`, `examList.errorMessage`, `phoneAuth.submitButton`, `universityList.searchField`.

## Kurallar (Rules)

1. ✅ **HER ZAMAN** okunabilir backtick isim: `` `method when condition expectedResult` ``.
2. ✅ **Given-When-Then** düzenini uygula.
3. ✅ **FAKE** repository ile ViewModel'i yalıt (varsayılan); gerektiğinde MockK (`coEvery`/`coVerify`).
4. ✅ **ViewModel'i** UI ve gerçek Firebase'den **bağımsız** test et.
5. ✅ **testTag** ile UI node'u bul (ASLA görünür metin/etiketle arama) ve mevcut tag'leri değiştirme.
6. ✅ **TEMİZLE**: `@Before` içinde taze `Fake`/`sut`; gereken yerde `@After`. Testler arası durum PAYLAŞMA.
7. ✅ **ASYNC** için `runTest` + Turbine kullan — `Thread.sleep`/sahte bekleme YASAK.
8. ✅ **TEK UiState** alanlarını doğrula (`isLoading`, `error`, liste) — iç implementasyonu değil **davranışı** test et.
9. ✅ **MainDispatcherRule** ile `Dispatchers.Main`'i test dispatcher'a çevir; projede tek dispatcher desenini tut.
10. ✅ **HATA yolunu** hata tipinin `userMessage`'ına göre assert et, düz Türkçe literal'e bağlanma.
11. ✅ **MAPPER'ları** saf fonksiyon olarak ayrı test et; null/eksik alanı kapsa.
12. ❌ **ASLA** gerçek ağ/Firebase'e birim testinde çıkma; ViewModel repository çağrısını `Dispatchers.IO` ile sarmasın.
13. ❌ **ASLA** `!!` (force unwrap) kullanma; `assertNotNull` sonrası güvenli eriş.
14. ❌ **ASLA** test içinde global mutable singleton durumu kullanma; bağımlılığı enjekte et.
15. ✅ **Compose testlerini** çoğunlukla durumsuz `content` overload'u + `createComposeRule()` ile yaz; uçtan uca testi sadece ana akışlara ayır.

## Test Kontrol Listesi (Testing Checklist)

Yeni bir ViewModel için test yazarken:

- [ ] Başarılı veri yükleme (UiState.exams dolar, error null)
- [ ] Hata yolu (`ArchiveError.Offline` / `.NotFound` → doğru `userMessage`)
- [ ] Boş durum (liste boş, error null)
- [ ] Yükleme durumu geçişi (`isLoading` false → true → false, Turbine ile)
- [ ] Sayfalama (varsa: ekleme + veri bitince sayfa artmaz)
- [ ] Repository'nin doğru argümanla ve doğru sayıda çağrıldığı (Fake sayaç / `coVerify`)
- [ ] Mapper testi (DTO → domain, null/eksik alan dahil); yeni `// Added in <versionName>` alanı için assertion
- [ ] Ekranın `testTag`'leri eklendi ve UI testinde kullanıldı
- [ ] Ana kullanıcı akışı için Compose UI testi

## Örnek: Tam ViewModel Test Paketi (Complete ViewModel Test Suite)

```kotlin
package com.salticusteam.vifi.feature.examlist

import app.cash.turbine.test
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.Exam
import com.salticusteam.vifi.fake.FakeArchiveRepository
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ExamListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepository: FakeArchiveRepository
    private lateinit var sut: ExamListViewModel

    private val sampleExam = Exam(id = "e1", title = "Fizik I Vize", courseId = "fiz-101", kind = "pdf", year = 2023)

    @Before
    fun setUp() {
        fakeRepository = FakeArchiveRepository()
        sut = ExamListViewModel(fakeRepository)
    }

    // --- loadExams ---

    @Test
    fun `loadExams when success updates exams and clears error`() = runTest {
        fakeRepository.examsToReturn = listOf(sampleExam)

        sut.loadExams(departmentId = "dep-1")
        advanceUntilIdle()

        val state = sut.uiState.value
        assertEquals(1, state.exams.size)
        assertNull(state.error)
        assertFalse(state.isLoading)
        assertEquals("dep-1", fakeRepository.lastRequestedDepartmentId)
    }

    @Test
    fun `loadExams when network error sets userMessage`() = runTest {
        fakeRepository.errorToThrow = ArchiveError.Offline

        sut.loadExams(departmentId = "dep-1")
        advanceUntilIdle()

        val state = sut.uiState.value
        assertTrue(state.exams.isEmpty())
        assertEquals(ArchiveError.Offline.userMessage, state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `loadExams when empty result exposes empty list without error`() = runTest {
        fakeRepository.examsToReturn = emptyList()

        sut.loadExams(departmentId = "dep-1")

        val state = sut.uiState.value
        assertTrue(state.exams.isEmpty())
        assertNull(state.error)
    }

    // --- loading state ---

    @Test
    fun `loadExams toggles isLoading true then false`() = runTest {
        fakeRepository.examsToReturn = listOf(sampleExam)

        sut.uiState.test {
            assertFalse(awaitItem().isLoading)
            sut.loadExams(departmentId = "dep-1")
            assertTrue(awaitItem().isLoading)
            assertFalse(awaitItem().isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

---

**Ayrıca Bkz (See Also):**
- `architecture.md` — Proje mimarisi ve desenleri (MVVM, StateFlow, repository)
- `viewmodel-rule.md` — ViewModel + UiState + StateFlow uygulama kılavuzu
- `firebase-data.md` — Repository `interface`'leri, DTO → domain map, IO dispatching
- `ui-components.md` — Compose bileşenleri, `testTag` ve `contentDescription` sözleşmesi
- `../skills/generate-unit-tests/` — ViewModel/mapper birim testi üretme skill'i
- `../skills/generate-ui-tests/` — Compose UI testi üretme skill'i
- `../skills/generate-mocks/` — `Fake...Repository` üretme skill'i
- Android resmi [Test rehberi](https://developer.android.com/training/testing) ve
  [Compose test rehberi](https://developer.android.com/jetpack/compose/testing)
