---
name: generate-unit-tests
description: ViFi-Android ViewModel, Repository ve Mapper/Util sınıfları için JUnit + runTest + Turbine + Fake repository + TestDispatcher kullanan kapsamlı birim testleri üretir. StateFlow<UiState> geçişlerini doğrular, gerçek Firebase'e dokunmaz.
---

# Birim Test Üretimi (ViFi-Android)

> **Legacy not:** Bugünkü v2.5 kod tabanı Java + Activity/Fragment üzerinde ve otomatik test
> neredeyse yok. Bu skill **yeni/ileriye dönük** Kotlin + Compose + MVVM kodu içindir: her yeni
> `ViewModel`, `Repository` ve `Mapper` için test üretir. Legacy Java ekranlarına geriye dönük test
> beklenmez — onlar Composable + ViewModel ikilisine göç ettikçe test kazanır. Göçün yol
> haritasındaki yeri için bkz. repo kökü `ANDROID-PARITY-ROADMAP.md` / `ANDROID-PARITY-DETAIL.md`.

## Kullanım

```
/generate-unit-tests [ClassName | file_path]

Örnekler:
/generate-unit-tests ExamListViewModel
/generate-unit-tests ExamMapper
/generate-unit-tests app/src/main/java/com/salticusteam/vifi/feature/examlist/ExamListViewModel.kt
```

## Genel Bakış

ViFi-Android birim testleri **`Fake...Repository` enjeksiyonu** + **`runTest`** + **Turbine** ile
`StateFlow<XUiState>` emisyonlarını doğrular. ViewModel bir repository `interface`'ine bağımlıdır;
teste gerçek Firebase yerine elle yazılmış bir `Fake` enjekte edilir. iOS'taki "delegate + manuel
mock + `XCTestExpectation`" yaklaşımının Android karşılığı budur.

- **Repository pattern VAR** — ViewModel `interface` arkasındaki repository'yi çağırır; test bu
  arayüzü sahteyle değiştirir.
- **Coroutines + Flow VAR** — async iş `viewModelScope.launch` ile; testte `runTest` +
  `StandardTestDispatcher` ile kontrol edilir, Turbine ile `StateFlow` doğrulanır.
- **Delegate mock YOK** — iOS `Mock*Delegate` (çağrı bayrakları + closure) yerine `Fake...Repository`.
- **`XCTestExpectation` / `wait(for:)` / `Thread.sleep` YOK** — async beklenti `runTest` + Turbine ile.
- **RxJava YOK** — Kotlin Coroutines + Flow.

## Adımlar

1. **Sınıfı bul ve analiz et** (isimle ya da dosya yoluyla).
2. **Bağımlılıkları belirle:**
   - ViewModel'in constructor'ına enjekte edilen repository `interface`'leri (`ArchiveRepository`, `AuthRepository`, `UserPreferencesRepository`).
   - `SavedStateHandle` ile gelen route argümanları (`lessonId` vb.).
   - Saf dönüşüm/yardımcılar (`ExamMapper`, `DateFormatter`).
3. **`/generate-mocks` çalıştır** — gerekli `Fake...Repository`'leri üret (bkz. `../generate-mocks/SKILL.md`).
4. **Test dosyasını üret:**
   - Başarı senaryoları (UiState dolar, `error == null`).
   - Hata senaryoları (`ArchiveError`/`AuthError` → doğru `userMessage`).
   - Yükleme durumu geçişi (`isLoading` false → true → false, Turbine ile).
   - Sayfalama (append + veri bitince sayfa artmaz).
   - Tek seferlik olaylar (`Channel`/`SharedFlow` → `events` Flow'u Turbine ile).
5. **Kaydet:** `app/src/test/java/com/salticusteam/vifi/...` (JVM birim testi; cihazsız, hızlı).

## Desteklenen Sınıf Türleri

### Destekleniyor

- **ViewModel** — `@HiltViewModel` iş mantığı; `StateFlow<UiState>` + `Channel` event.
- **Repository** — DTO→domain map ve `ArchiveError` çevirisi (saf kısım; gerekirse MockK ile Firebase sahtelenir).
- **Mapper** — `ExamDto.toDomain()` gibi saf dönüşümler (null/eksik alan dahil).
- **Util/Helper** — `DateFormatter`, biçimlendirme/doğrulama gibi saf fonksiyonlar.
- **Yerel kalıcılık** — `UserPreferencesRepository` (DataStore `interface` arkasında); sahte/bellek-içi impl ile.

### Desteklenmiyor (bu skill'in kapsamı dışında)

- **Delegate-mock testleri** — ViFi delegate protokolü kullanmaz; `Fake...Repository` kullan.
- **`XCTestExpectation` / `Thread.sleep` tabanlı beklemeler** — `runTest` + Turbine kullan.
- **RxJava testleri** — Coroutines + Flow.
- **Compose UI / Espresso (instrumentation)** — bunlar `app/src/androidTest/`'e aittir; bkz. `../generate-ui-tests/SKILL.md`.

## Sınıf Türüne Göre Çıktı Klasörü

Kaynağın `feature/` + `core/` paketlemesini test tarafında da aynala (bkz. `../../rules/architecture.md`).

| Sınıf Türü | Çıktı Klasörü | Örnek |
|------------|---------------|-------|
| ViewModel | `app/src/test/.../feature/<ad>/` | `ExamListViewModelTest.kt` |
| Repository | `app/src/test/.../core/data/repository/` | `FirebaseArchiveRepositoryTest.kt` |
| Mapper | `app/src/test/.../core/data/` | `ExamMapperTest.kt` |
| Util/Helper | `app/src/test/.../core/util/` | `DateFormatterTest.kt` |
| Yerel kalıcılık | `app/src/test/.../core/data/` | `UserPreferencesRepositoryTest.kt` |
| Fake'ler (paylaşılan) | `app/src/test/.../fake/` | `FakeArchiveRepository.kt` |
| Test altyapısı | `app/src/test/.../testing/` | `MainDispatcherRule.kt` |

## Test Adlandırma Deseni

iOS `test_{method}_{condition}_{expectedResult}` kalıbı Kotlin'de **backtick'li, boşluklu,
okunabilir** isme çevrilir. Tanımlayıcılar ve kod yorumları İngilizce:

```
`{method} when {condition} {expectedResult}`

Örnekler:
- `loadExams when success updates uiState with exams`
- `loadExams when network error sets userMessage`
- `loadMore when no more data does not change page`
- `onExamClick emits OpenExam event`
- `onPhoneSubmit when number invalid emits validation error`
```

## ViewModel Test Deseni

### Girdi: ViewModel sınıfı

iOS `FeedViewModel` + delegate + `NetworkManager.shared` deseninin karşılığı: `@HiltViewModel`,
constructor injection ile repository, tek `StateFlow<UiState>`. IO'ya geçiş **repository'nin
içindedir** (ViewModel `withContext(Dispatchers.IO)` kullanmaz) — böylece testte tüm iş test
dispatcher'ında kalır ve deterministik olur (bkz. `../../rules/testing.md` → MainDispatcherRule).

```kotlin
// app/src/main/java/com/salticusteam/vifi/feature/examlist/ExamListViewModel.kt
package com.salticusteam.vifi.feature.examlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.model.Exam
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

// region State
data class ExamListUiState(
    val exams: List<Exam> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: String? = null,
)
// endregion

// region Events
sealed interface ExamListEvent {
    data class OpenExam(val examId: String) : ExamListEvent
}
// endregion

@HiltViewModel
class ExamListViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // region State
    private val lessonId: String = checkNotNull(savedStateHandle["lessonId"])

    private val _uiState = MutableStateFlow(ExamListUiState())
    val uiState: StateFlow<ExamListUiState> = _uiState.asStateFlow()

    private val _events = Channel<ExamListEvent>(Channel.BUFFERED)
    val events: Flow<ExamListEvent> = _events.receiveAsFlow()

    private var currentPage = 1
    // endregion

    // region Public API
    fun loadExams() {
        if (_uiState.value.isLoading) return // Prevent duplicate requests
        currentPage = 1
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                // No withContext(Dispatchers.IO) here: the repository does IO internally,
                // so the test dispatcher stays in control and Turbine stays deterministic.
                val exams = archiveRepository.getExams(lessonId, page = currentPage)
                _uiState.update {
                    it.copy(isLoading = false, exams = exams, canLoadMore = exams.isNotEmpty())
                }
            } catch (e: ArchiveError) {
                _uiState.update { it.copy(isLoading = false, error = e.userMessage) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = ArchiveError.Unknown.userMessage) }
            }
        }
    }

    fun onExamClick(examId: String) {
        viewModelScope.launch { _events.send(ExamListEvent.OpenExam(examId)) }
    }
    // endregion
}
```

### Üretilen: Fake Repository (iOS Mock Delegate karşılığı)

iOS'taki `MockFeedViewModelDelegate` (çağrı bayrakları + closure'lar) yerine, ViewModel'in bağımlı
olduğu `ArchiveRepository` arayüzünün elle yazılmış sahtesini üret. Sonucu yapılandırılabilir kıl ve
çağrı sayımı tut. `Fake` paylaşılan olduğu için `app/src/test/.../fake/`'de **bir kez** yazılır.

```kotlin
// app/src/test/java/com/salticusteam/vifi/fake/FakeArchiveRepository.kt
package com.salticusteam.vifi.fake

import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.model.Exam
import com.salticusteam.vifi.core.model.ExamSubmission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

// Hand-written fake: the Android equivalent of iOS's Mock*Delegate.
class FakeArchiveRepository : ArchiveRepository {

    // Configure the outcome per test (success vs. failure).
    var examsToReturn: List<Exam> = emptyList()
    var errorToThrow: ArchiveError? = null

    // Tracking, like the iOS "*Called" flags and call counts.
    var getExamsCallCount = 0
        private set
    var lastRequestedLessonId: String? = null
        private set
    var lastRequestedPage: Int? = null
        private set

    override suspend fun getExams(lessonId: String, page: Int): List<Exam> {
        getExamsCallCount++
        lastRequestedLessonId = lessonId
        lastRequestedPage = page
        errorToThrow?.let { throw it }
        return examsToReturn
    }

    override suspend fun submitExam(request: ExamSubmission): Exam {
        errorToThrow?.let { throw it }
        return Exam(
            id = "generated",
            title = request.title,
            courseId = request.courseId,
            kind = "pdf",
            year = request.year,
            isLocked = false,
        )
    }

    // For observeExams(...) tests: drive emissions manually from the test body.
    private val examsFlow = MutableSharedFlow<List<Exam>>(replay = 1)
    override fun observeExams(lessonId: String): Flow<List<Exam>> = examsFlow

    suspend fun emitExams(exams: List<Exam>) = examsFlow.emit(exams)
}
```

### Üretilen: Birim Testleri

```kotlin
// app/src/test/java/com/salticusteam/vifi/feature/examlist/ExamListViewModelTest.kt
package com.salticusteam.vifi.feature.examlist

import androidx.lifecycle.SavedStateHandle
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

    // Replaces iOS setUp()/tearDown() for the main dispatcher: swaps Dispatchers.Main
    // for a TestDispatcher before each test and resets it afterwards.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepository: FakeArchiveRepository
    private lateinit var sut: ExamListViewModel // System Under Test

    private val sampleExam = Exam(
        id = "e1", title = "Fizik I Vize", courseId = "phys-101",
        kind = "pdf", year = 2023, isLocked = false,
    )

    @Before
    fun setUp() {
        fakeRepository = FakeArchiveRepository()
        sut = ExamListViewModel(
            archiveRepository = fakeRepository,
            savedStateHandle = SavedStateHandle(mapOf("lessonId" to "phys-101")),
        )
    }

    // --- Success ---
    @Test
    fun `loadExams when success updates uiState with exams`() = runTest {
        // Given
        fakeRepository.examsToReturn = listOf(sampleExam)

        // When
        sut.loadExams()
        advanceUntilIdle() // StandardTestDispatcher: run the launched coroutine before reading state

        // Then
        val state = sut.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(1, state.exams.size)
        assertEquals("e1", state.exams.first().id)
        assertEquals(1, fakeRepository.getExamsCallCount)
        assertEquals("phys-101", fakeRepository.lastRequestedLessonId)
    }

    // --- Failure (Android equivalent of iOS "Network Reachability": the fake throws) ---
    @Test
    fun `loadExams when network error sets userMessage`() = runTest {
        // Given
        fakeRepository.errorToThrow = ArchiveError.Offline

        // When
        sut.loadExams()
        advanceUntilIdle() // StandardTestDispatcher: run the launched coroutine before reading state

        // Then — assert the error TYPE's userMessage, not a raw string literal.
        val state = sut.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.exams.isEmpty())
        assertEquals(ArchiveError.Offline.userMessage, state.error)
    }

    // --- Loading state transition (Turbine) ---
    @Test
    fun `loadExams toggles isLoading true then false`() = runTest {
        fakeRepository.examsToReturn = listOf(sampleExam)

        sut.uiState.test {
            assertFalse(awaitItem().isLoading)   // initial state
            sut.loadExams()
            assertTrue(awaitItem().isLoading)    // during request
            assertFalse(awaitItem().isLoading)   // after completion
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- One-time event (Channel) ---
    @Test
    fun `onExamClick emits OpenExam event`() = runTest {
        sut.events.test {
            sut.onExamClick("e1")
            assertEquals(ExamListEvent.OpenExam("e1"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Edge case: duplicate request guard ---
    @Test
    fun `loadExams when called twice while loading requests only once`() = runTest {
        fakeRepository.examsToReturn = listOf(sampleExam)

        sut.loadExams()
        sut.loadExams() // second call hits the isLoading guard and is ignored
        advanceUntilIdle() // StandardTestDispatcher: run the launched coroutine before reading the call count

        assertEquals(1, fakeRepository.getExamsCallCount)
    }
}
```

> **Not:** Yukarıdaki ViewModel init içinde otomatik yükleme yapmıyor; test `sut.loadExams()`'i
> açıkça çağırıyor ve ilk Turbine emisyonu varsayılan `ExamListUiState()`'tir. Gerçek ViewModel'in
> `init { loadExams() }` ile açılışta yükleme yaptığı varyant için bkz. `patterns/viewmodel-tests.md`
> → "Açılışta (init) yükleme yapan ViewModel'i test etme".

## Test Kapsam Kategorileri

Her public fonksiyon için şu senaryoları üret:

### 1. Başarı Senaryoları (Öncelik: Yüksek)

```kotlin
`loadExams when success updates uiState with exams`      // UiState.exams filled, error null
`submitExam when success emits ShowMessage event`        // CRUD success → event
```

### 2. Hata Senaryoları (Öncelik: Yüksek)

iOS'un "Network Reachability" testi Android'de **`Fake.errorToThrow` ile hata fırlatma**ya dönüşür.
`NetworkManager.shared.mockNetworkReachable` gibi global bayrak YOKTUR; hata domain tipi olarak gelir.

```kotlin
`loadExams when network error sets userMessage`          // ArchiveError.Offline → userMessage
`loadExams when empty result exposes empty list`         // ArchiveError.Empty or empty list
`submitExam when invalid input sets error`               // validation error
```

### 3. Yükleme Durumu (Öncelik: Orta)

iOS'un `willStartLoading()`/`didFinishLoading()` delegate çağrıları, tek `UiState` içindeki
`isLoading` alanıdır. Geçişi Turbine ile sırayla doğrula:

```kotlin
`loadExams toggles isLoading true then false`
`submitExam closes isLoading on both success and error paths`
```

### 4. Sayfalama (Öncelik: Orta)

```kotlin
`loadMore when has more data appends exams`              // new page appended to the existing list
`loadMore when no more data does not change page`        // canLoadMore == false → page does not advance
`loadMore when already loading does not request`         // isLoadingMore guard
```

### 5. Tek Seferlik Olaylar (Öncelik: Orta)

iOS'un tek seferlik delegate çağrıları (`didNavigate`, `showPopup`) `Channel`/`SharedFlow` event'idir.
`events` Flow'unu Turbine ile doğrula:

```kotlin
`onExamClick emits OpenExam event`
`logout emits NavigateToLogin event`
```

### 6. Doğrulama (Öncelik: Düşük)

```kotlin
`onPhoneSubmit when number invalid emits validation error`   // +90 phone validation
`submit when title blank sets error`
```

### 7. Sınır Durumları (Öncelik: Düşük)

```kotlin
`loadExams when called twice while loading requests only once`  // duplicate guard
`loadExams when result empty exposes empty list without error`
```

## Repository Test Deseni (iOS Manager Test karşılığı)

iOS'ta singleton `AuthManager` doğrudan test ediliyordu. Android'de bunun unit-test edilebilir
karşılığı: repository'nin **DTO→domain map**'i ve **`ArchiveError` çevirisi**. Map saf fonksiyondur,
Firebase'e dokunmadan test edilir (bkz. `../../rules/firebase-data.md`). Repository'nin kendi hata
yolunu test etmek için Firebase API'si **MockK** ile sahtelenir.

```kotlin
// app/src/test/java/com/salticusteam/vifi/core/data/ExamMapperTest.kt
package com.salticusteam.vifi.core.data

import com.salticusteam.vifi.core.model.Exam
import org.junit.Assert.assertEquals
import org.junit.Test

class ExamMapperTest {

    @Test
    fun `toDomain maps all fields`() {
        val dto = ExamDto(
            id = "e1", title = "Fizik Vize", courseId = "phys-101",
            kind = "pdf", year = 2023, isLocked = false,
        )

        val exam = dto.toDomain()

        assertEquals(
            Exam(
                id = "e1", title = "Fizik Vize", courseId = "phys-101",
                kind = "pdf", year = 2023, isLocked = false,
            ),
            exam,
        )
    }

    @Test
    fun `toDomain when fields null returns safe defaults`() {
        val dto = ExamDto() // all nulls, as Firestore deserialization can produce

        val exam = dto.toDomain()

        assertEquals("", exam.id)
        assertEquals("", exam.title)
        assertEquals("", exam.courseId)
        assertEquals("", exam.kind)
    }
}
```

> **Versioned doc comment bağlantısı:** Bir domain modele yeni alan eklendiğinde (alan
> `// Added in <versionName> — <amaç>.` taşır, versiyon = `app/build.gradle` `versionName`), o alanın
> map'lendiğini doğrulayan assertion'ı `ExamMapperTest`'e ekle. Yeni alan + test aynı PR'da gelir.

## Util/Helper Test Deseni (iOS Helper Test karşılığı)

iOS `DeviceIdHelper` gibi saf yardımcıların karşılığı: `core/util/` altındaki saf Kotlin fonksiyonları.
Bağımlılık yok, hızlı, deterministik. Tarih/saat için bkz. `../../rules/date-handling.md`.

```kotlin
// app/src/test/java/com/salticusteam/vifi/core/util/DateFormatterTest.kt
package com.salticusteam.vifi.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DateFormatterTest {

    @Test
    fun `formatUploadDate returns Turkish day-month-year`() {
        // Fixed epoch millis → deterministic output (no real clock).
        val formatted = DateFormatter.formatUploadDate(epochMillis = 1_700_000_000_000L)

        assertEquals("14 Kasım 2023", formatted)
    }

    @Test
    fun `formatUploadDate when zero returns empty string`() {
        assertEquals("", DateFormatter.formatUploadDate(epochMillis = 0L))
    }
}
```

## Yerel Kalıcılık Test Deseni (iOS LocalStore Test karşılığı)

iOS `LocalStore` testlerinin karşılığı: yerel kalıcılık da bir repository `interface`'i arkasındadır
(oturum/kullanıcı tercihi için DataStore tabanlı `UserPreferencesRepository`). Birim testte
sahte/bellek-içi impl kullanılır; her testte durum `@Before`'da sıfırlanır (iOS `setUp`/`tearDown`
temizliğinin karşılığı). DataStore'un kendi davranışı test edilecekse `androidTest`'te geçici
dosyayla instrumentation testi yazılır.

```kotlin
// app/src/test/java/com/salticusteam/vifi/core/data/UserPreferencesRepositoryTest.kt
package com.salticusteam.vifi.core.data

import com.salticusteam.vifi.core.model.User
import com.salticusteam.vifi.fake.FakeUserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class UserPreferencesRepositoryTest {

    private lateinit var repository: FakeUserPreferencesRepository

    @Before
    fun setUp() {
        repository = FakeUserPreferencesRepository() // fresh state per test
    }

    @Test
    fun `saveUser then observeUser returns same user`() = runTest {
        val user = User(id = "123", phone = "+905551112233")

        repository.saveUser(user)

        assertEquals(user, repository.observeUser().first())
    }

    @Test
    fun `clearUser removes stored user`() = runTest {
        repository.saveUser(User(id = "123", phone = "+905551112233"))

        repository.clearUser()

        assertNull(repository.observeUser().first())
    }
}
```

## Örnek: Tam Üretim Akışı

```
/generate-unit-tests ExamListViewModel

→ Adım 1: ExamListViewModel analizi
  - Bağımlılık: ArchiveRepository (interface), SavedStateHandle (lessonId)
  - Public fonksiyonlar: loadExams(), loadMore(), onExamClick(examId)
  - State: ExamListUiState (exams, isLoading, isLoadingMore, canLoadMore, error)
  - Event: ExamListEvent.OpenExam

→ Adım 2: Fake üret (yoksa /generate-mocks)
  - app/src/test/.../fake/FakeArchiveRepository.kt

→ Adım 3: Test üret
  - app/src/test/.../feature/examlist/ExamListViewModelTest.kt

  Üretilen 6 test:
  ✓ loadExams when success updates uiState with exams
  ✓ loadExams when network error sets userMessage
  ✓ loadExams when empty result exposes empty list without error
  ✓ loadExams toggles isLoading true then false
  ✓ onExamClick emits OpenExam event
  ✓ loadExams when called twice while loading requests only once
```

## Anti-Desenler (BUNLARI YAPMA)

### Delegate-mock testi (iOS deseni)

```kotlin
// DON'T — ViFi-Android does not use the delegate protocol; use a Fake repository.
private lateinit var mockDelegate: MockExamListViewModelDelegate
// ...
assertTrue(mockDelegate.didSuccessLoadCalled)
```

### XCTestExpectation / Thread.sleep tabanlı bekleme

```kotlin
// DON'T — no fake waiting. runTest + Turbine are deterministic.
sut.loadExams()
Thread.sleep(2000) // ❌ flaky, slow
assertTrue(sut.uiState.value.exams.isNotEmpty())
```

### Gerçek Firebase'e / ağa çıkmak

```kotlin
// DON'T — a unit test never touches real Firebase. Inject a Fake/MockK.
val repository = FirebaseArchiveRepository(FirebaseFirestore.getInstance()) // ❌
```

### Düz Türkçe string literal'ine assert etmek

```kotlin
// DON'T — don't couple tightly to the copy; assert on the error TYPE's userMessage.
assertEquals("İnternet bağlantınızı kontrol edin.", state.error) // ❌
// DO:
assertEquals(ArchiveError.Offline.userMessage, state.error)      // ✅
```

### Doğru ViFi-Android deseni

```kotlin
// DO
class ExamListViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private lateinit var fakeRepository: FakeArchiveRepository
    private lateinit var sut: ExamListViewModel

    @Before fun setUp() {
        fakeRepository = FakeArchiveRepository()
        sut = ExamListViewModel(fakeRepository, SavedStateHandle(mapOf("lessonId" to "phys-101")))
    }

    @Test fun `loadExams when success updates uiState with exams`() = runTest {
        fakeRepository.examsToReturn = listOf(sampleExam)
        sut.loadExams()
        advanceUntilIdle() // StandardTestDispatcher: run the launched coroutine before reading state
        assertEquals(1, sut.uiState.value.exams.size)
    }
}
```

## En İyi Uygulamalar

1. **HER ZAMAN** async için `runTest` + Turbine; `Thread.sleep`/sahte bekleme YASAK.
2. **KULLAN** okunabilir backtick isim: `` `method when condition expectedResult` ``.
3. **FAKE** repository ile ViewModel'i yalıt (varsayılan); gerektiğinde **MockK** (`coEvery`/`coVerify`).
4. **TEST ET** hem başarı hem hata yolunu; hatayı **tipin** `userMessage`'ına göre assert et.
5. **DOĞRULA** `isLoading` geçişini Turbine ile (false → true → false).
6. **TEMİZLE** durumu `@Before`'da (taze `Fake`/`sut`); testler arası durum paylaşma.
7. **UYGULA** Given-When-Then düzenini.
8. **KUR** `Dispatchers.Main`'i `MainDispatcherRule` ile test dispatcher'a çevir; projede tek desen.
9. **TEST ET** davranışı (UiState alanları + event'ler), iç implementasyonu değil.
10. **ASLA** `!!` (force unwrap) kullanma; `assertNotNull` sonrası güvenli eriş.

## Test Kontrol Listesi

Bir ViewModel için test üretirken:

- [ ] Başarılı yükleme (UiState.exams dolar, error null)
- [ ] Hata yolu (`ArchiveError.Offline`/`.Empty` → doğru `userMessage`)
- [ ] Boş durum (liste boş, error null)
- [ ] Yükleme durumu geçişi (`isLoading` false → true → false, Turbine)
- [ ] Sayfalama (varsa: append + veri bitince sayfa artmaz)
- [ ] Tek seferlik olay (`events` Flow'u Turbine ile)
- [ ] Repository doğru argümanla/doğru sayıda çağrıldı (Fake sayaç / `coVerify`)
- [ ] Mapper testi (DTO → domain, null/eksik alan dahil); yeni `// Added in <versionName>` alanı için assertion
- [ ] Duplicate istek guard'ı
- [ ] `Fake...Repository` üretildi (bkz. `../generate-mocks/SKILL.md`)

---

**Ayrıca Bkz (See Also):**
- `patterns/viewmodel-tests.md` — StateFlow geçişlerini Turbine ile doğrulayan tam ViewModel test örneği
- `../../rules/testing.md` — Test mimarisi, `MainDispatcherRule`, Fake vs. MockK, testTag sözleşmesi
- `../../rules/viewmodel-rule.md` — ViewModel + UiState + StateFlow + Channel event uygulama kılavuzu
- `../../rules/firebase-data.md` — Repository `interface`'leri, DTO → domain map, IO dispatching
- `../generate-mocks/SKILL.md` — `Fake...Repository` üretme
- `../generate-ui-tests/SKILL.md` — Compose UI testi üretme (`androidTest`)
