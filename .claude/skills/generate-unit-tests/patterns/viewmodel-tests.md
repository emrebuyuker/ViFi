# ViFi-Android - ViewModel Birim Test Deseni

## Genel Bakış

ViFi-Android ViewModel testleri şunları kullanır:

- **`Fake...Repository` enjeksiyonu** — yapılandırılabilir sonuç + çağrı sayımı (iOS `Mock*Delegate` karşılığı).
- **`runTest` + `StandardTestDispatcher`** — coroutine'leri deterministik kontrol (iOS `XCTestExpectation`/`wait(for:)` karşılığı).
- **Turbine** — `StateFlow<XUiState>` ve `events` Flow emisyonlarını sırayla doğrulama.
- **`MainDispatcherRule`** — `Dispatchers.Main`'i test dispatcher'a çevirir (iOS `setUp`/`tearDown` karşılığı).
- **Repository pattern VAR** — ViewModel `interface` arkasındaki repository'ye bağımlıdır (iOS'ta NetworkManager + Service vardı).
- **Düz JUnit4** — `BaseTestCase` benzeri bir temel sınıf YOK; `@Before`/`@After` + `@get:Rule`.

Temel kural: **ViewModel, UI ve gerçek Firebase'den bağımsız test edilir.** Repository bir `interface`
arkasında olduğu için ViewModel testi ağ/dosya sistemine hiç dokunmaz. IO'ya geçiş **repository'nin
içindedir** — ViewModel `withContext(Dispatchers.IO)` kullanmaz, böylece tüm iş test dispatcher'ında
kalır ve Turbine emisyon sırası deterministik olur (bkz. `../../../rules/testing.md`).

## Test Edilen ViewModel (referans)

Aşağıdaki örnekler bu ViewModel'i hedefler. Tam uygulama kılavuzu: `../../../rules/viewmodel-rule.md`.

```kotlin
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
        if (_uiState.value.isLoading) return
        currentPage = 1
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
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

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.canLoadMore) return
        _uiState.update { it.copy(isLoadingMore = true) }
        val nextPage = currentPage + 1
        viewModelScope.launch {
            try {
                val newItems = archiveRepository.getExams(lessonId, page = nextPage)
                currentPage = nextPage
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        exams = it.exams + newItems,
                        canLoadMore = newItems.isNotEmpty(),
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMore = false) } // keep list, stop spinner
            }
        }
    }

    fun onExamClick(examId: String) {
        viewModelScope.launch { _events.send(ExamListEvent.OpenExam(examId)) }
    }
    // endregion
}
```

## Test Sınıfı Yapısı

iOS `XCTestCase` + `setUp`/`tearDown` + `XCTestExpectation` kalıbının karşılığı: JUnit4 sınıfı +
`@get:Rule MainDispatcherRule` + `@Before` + `runTest` + Turbine.

```kotlin
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

    // Swaps Dispatchers.Main for a TestDispatcher before each test, resets it after.
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

        // When / Then — observe the full state transition via Turbine
        sut.uiState.test {
            assertEquals(ExamListUiState(), awaitItem())   // initial state

            sut.loadExams()

            assertTrue(awaitItem().isLoading)              // loading = true
            val loaded = awaitItem()                       // success
            assertFalse(loaded.isLoading)
            assertNull(loaded.error)
            assertEquals(1, loaded.exams.size)
            assertEquals("e1", loaded.exams.first().id)

            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, fakeRepository.getExamsCallCount)
        assertEquals("phys-101", fakeRepository.lastRequestedLessonId)
    }

    // --- Failure ---
    @Test
    fun `loadExams when network error sets userMessage`() = runTest {
        // Given
        fakeRepository.errorToThrow = ArchiveError.Offline

        // When
        sut.loadExams()
        advanceUntilIdle() // StandardTestDispatcher: run the launched coroutine before reading state

        // Then — assert the error TYPE's userMessage, not a raw Turkish literal.
        val state = sut.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.exams.isEmpty())
        assertEquals(ArchiveError.Offline.userMessage, state.error)
    }
}
```

## Test Durumu Üretim Stratejisi

Her public fonksiyon için şu kategorileri üret:

### 1. Başarı Senaryoları (Öncelik: Yüksek)

```kotlin
`{method} when success updates uiState with {state}`
`{method} when success emits {Event} event`
```

**Ne test edilir:**
- `UiState` alanları doğru güncellenir (`exams`, `error == null`).
- Repository doğru argümanla/doğru sayıda çağrılır (`Fake` sayaç).
- Varsa tek seferlik event gönderilir.

**Örnek:**
```kotlin
@Test
fun `loadExams when success updates uiState with exams`() = runTest {
    fakeRepository.examsToReturn = listOf(sampleExam)

    sut.loadExams()
    advanceUntilIdle() // StandardTestDispatcher: run the launched coroutine before reading state

    val state = sut.uiState.value
    assertEquals(1, state.exams.size)
    assertNull(state.error)
    assertEquals(1, fakeRepository.getExamsCallCount)
}
```

### 2. Hata Senaryoları (Öncelik: Yüksek)

iOS'un "Network Reachability" testi Android'de **`Fake.errorToThrow` ile hata fırlatma**ya dönüşür.
`NetworkManager.shared.mockNetworkReachable` benzeri global bayrak YOKTUR.

```kotlin
`{method} when network error sets userMessage`
`{method} when empty exposes empty list without error`
`{method} when invalid input sets error`
```

**Ne test edilir:**
- `UiState.error`, hata **tipinin** `userMessage`'ına eşit (düz string literal'e değil).
- `isLoading` kapatıldı (askıda kalma yok).
- Liste/durum beklendiği gibi (boş kaldı ya da korundu).

**Örnek:**
```kotlin
@Test
fun `loadExams when network error sets userMessage`() = runTest {
    fakeRepository.errorToThrow = ArchiveError.Offline

    sut.loadExams()
    advanceUntilIdle() // StandardTestDispatcher: run the launched coroutine before reading state

    val state = sut.uiState.value
    assertEquals(ArchiveError.Offline.userMessage, state.error)
    assertFalse(state.isLoading)
}
```

### 3. Yükleme Durumu Yönetimi (Öncelik: Orta) — Turbine ile geçiş doğrulama

iOS'un `willStartLoading()`/`didFinishLoading()` delegate çağrıları, tek `UiState` içindeki
`isLoading` alanıdır. **Bu skill'in çekirdeği:** geçişi (`false → true → false`) Turbine ile sırayla
doğrula.

```kotlin
`{method} toggles isLoading true then false`
`{method} closes isLoading on error path`
```

**Ne test edilir:**
- İlk emisyon: `isLoading == false` (başlangıç).
- İstek sırasında: `isLoading == true`.
- Tamamlanınca: `isLoading == false`.

**Örnek:**
```kotlin
@Test
fun `loadExams toggles isLoading true then false`() = runTest {
    fakeRepository.examsToReturn = listOf(sampleExam)

    sut.uiState.test {
        assertFalse(awaitItem().isLoading)   // initial: false
        sut.loadExams()
        assertTrue(awaitItem().isLoading)    // during request: true
        assertFalse(awaitItem().isLoading)   // after completion: false
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun `loadExams closes isLoading on error path`() = runTest {
    fakeRepository.errorToThrow = ArchiveError.Offline

    sut.uiState.test {
        assertFalse(awaitItem().isLoading)
        sut.loadExams()
        assertTrue(awaitItem().isLoading)
        val errored = awaitItem()
        assertFalse(errored.isLoading)       // error branch still closes loading
        assertEquals(ArchiveError.Offline.userMessage, errored.error)
        cancelAndIgnoreRemainingEvents()
    }
}
```

### 4. Sayfalama (Öncelik: Orta)

```kotlin
`loadMore when has more data appends exams`
`loadMore when no more data does not change page`
`loadMore when already loading does not request`
```

**Ne test edilir:**
- Yeni sayfa mevcut listeye **eklenir** (üzerine yazmaz).
- `canLoadMore == false` iken sayfa artmaz, repository çağrılmaz.
- `isLoadingMore` guard tekrarı engeller.

**Örnek:**
```kotlin
@Test
fun `loadMore when has more data appends exams`() = runTest {
    val page1 = sampleExam
    val page2 = sampleExam.copy(id = "e2", title = "Fizik I Final")

    fakeRepository.examsToReturn = listOf(page1)
    sut.loadExams()
    advanceUntilIdle() // StandardTestDispatcher: finish the first load before asserting
    assertEquals(1, sut.uiState.value.exams.size)

    fakeRepository.examsToReturn = listOf(page2)
    sut.loadMore()
    advanceUntilIdle() // StandardTestDispatcher: finish the append before reading state

    val state = sut.uiState.value
    assertEquals(2, state.exams.size)                 // appended, not replaced
    assertEquals(listOf("e1", "e2"), state.exams.map { it.id })
    assertEquals(2, fakeRepository.lastRequestedPage)
}

@Test
fun `loadMore when no more data does not change page`() = runTest {
    fakeRepository.examsToReturn = emptyList() // canLoadMore becomes false after first load
    sut.loadExams()
    advanceUntilIdle() // StandardTestDispatcher: finish the first load before reading the call count
    val callsAfterLoad = fakeRepository.getExamsCallCount

    sut.loadMore()
    advanceUntilIdle() // StandardTestDispatcher: drain any pending work before asserting

    assertEquals(callsAfterLoad, fakeRepository.getExamsCallCount) // no extra request
}
```

### 5. Tek Seferlik Olaylar (Öncelik: Orta)

iOS'un tek seferlik delegate çağrıları (`didNavigate`, `showPopup`) `Channel`/`SharedFlow` event'idir.
`events` Flow'unu Turbine ile doğrula.

```kotlin
`onExamClick emits OpenExam event`
`logout emits NavigateToLogin event`
```

**Ne test edilir:**
- Doğru event tipi + payload gönderildi.
- Event state'te tutulmaz (tek seferlik tüketim).

**Örnek:**
```kotlin
@Test
fun `onExamClick emits OpenExam event`() = runTest {
    sut.events.test {
        sut.onExamClick("e1")
        assertEquals(ExamListEvent.OpenExam("e1"), awaitItem())
        cancelAndIgnoreRemainingEvents()
    }
}
```

### 6. Sınır Durumları (Öncelik: Düşük)

```kotlin
`{method} when called twice while loading requests only once`
`{method} when result empty exposes empty list without error`
```

**Ne test edilir:**
- Duplicate istek guard'ı (`isLoading` kontrolü).
- Boş veri güvenli ele alınır (liste boş, error null).

**Örnek:**
```kotlin
@Test
fun `loadExams when called twice while loading requests only once`() = runTest {
    fakeRepository.examsToReturn = listOf(sampleExam)

    sut.loadExams()
    sut.loadExams() // hits the isLoading guard
    advanceUntilIdle() // StandardTestDispatcher: run the launched coroutine before reading the call count

    assertEquals(1, fakeRepository.getExamsCallCount)
}
```

### 7. Doğrulama (Öncelik: Düşük)

```kotlin
`onPhoneSubmit when number invalid emits validation error`
`submit when title blank sets error`
```

**Örnek:**
```kotlin
@Test
fun `onPhoneSubmit when number invalid sets validation error`() = runTest {
    sut.onPhoneSubmit(phone = "123") // not a valid +90 number

    assertEquals(AuthError.InvalidPhone.userMessage, sut.uiState.value.error)
}
```

## StandardTestDispatcher vs. UnconfinedTestDispatcher

`MainDispatcherRule` varsayılan olarak `StandardTestDispatcher` kullanır: coroutine'ler **otomatik
çalışmaz**, Turbine `awaitItem()` ya da `advanceUntilIdle()` onları ilerletir. Bu, geçiş sırasını
(loading → success) net test etmek için idealdir.

Sadece **son durumu** doğrulamak istiyorsan `advanceUntilIdle()` ile tüm bekleyen coroutine'leri
çalıştır, sonra `uiState.value`'yu oku:

```kotlin
@Test
fun `loadExams final state has exams`() = runTest {
    fakeRepository.examsToReturn = listOf(sampleExam)

    sut.loadExams()
    advanceUntilIdle() // run all pending coroutines on the test dispatcher

    assertEquals(1, sut.uiState.value.exams.size)
}
```

Emisyonları tek tek sıralamak yerine anında yürütme istiyorsan kuralı
`MainDispatcherRule(UnconfinedTestDispatcher())` ile kur. Projede **tek bir varsayılanı tutarlı**
kullan (öneri: `StandardTestDispatcher`).

## Açılışta (init) Yükleme Yapan ViewModel'i Test Etme

Gerçek `ExamListViewModel` `init { loadExams() }` ile açılışta yükleme yapabilir. Bu durumda
`StandardTestDispatcher` ile constructor, senkron `_uiState.update { isLoading = true }`'yi hemen
çalıştırır; `viewModelScope.launch` gövdesi ise dispatcher ilerleyene kadar beklemede kalır. Testte:

```kotlin
@Test
fun `init triggers load and exposes exams`() = runTest {
    fakeRepository.examsToReturn = listOf(sampleExam)
    // init already set isLoading = true synchronously during construction.
    val sut = ExamListViewModel(fakeRepository, SavedStateHandle(mapOf("lessonId" to "phys-101")))

    sut.uiState.test {
        assertTrue(awaitItem().isLoading)   // init's synchronous isLoading = true
        val loaded = awaitItem()            // launched body runs under runTest
        assertFalse(loaded.isLoading)
        assertEquals(1, loaded.exams.size)
        cancelAndIgnoreRemainingEvents()
    }
}
```

> **İpucu:** `Fake`'i constructor'dan **önce** yapılandır (`examsToReturn`/`errorToThrow`), çünkü init
> hemen yüklemeyi tetikler. `@Before`'da ViewModel'i kurmak yerine, init-yükleme testlerinde
> ViewModel'i test gövdesinde kur ki Fake önceden hazır olsun.

## Anti-Desenler (BUNLARI YAPMA)

### Thread.sleep / sahte bekleme

```kotlin
// DON'T — flaky and slow. runTest + Turbine are deterministic.
sut.loadExams()
Thread.sleep(1000)
assertTrue(sut.uiState.value.exams.isNotEmpty())
```

### Delegate mock (iOS deseni)

```kotlin
// DON'T — ViFi does not use delegates.
private lateinit var mockDelegate: MockExamListViewModelDelegate
assertTrue(mockDelegate.didSuccessLoadCalled)
```

### Gerçek Firebase / ağ

```kotlin
// DON'T — a unit test never touches real Firebase; inject a Fake/MockK.
val repository = FirebaseArchiveRepository(FirebaseFirestore.getInstance())
```

### Düz Türkçe string literal'ine assert

```kotlin
// DON'T
assertEquals("İnternet bağlantınızı kontrol edin.", state.error)
// DO
assertEquals(ArchiveError.Offline.userMessage, state.error)
```

### ViewModel içinde withContext(Dispatchers.IO) + testte IO'ya çıkmak

```kotlin
// DON'T — if the ViewModel switches to IO it loses test-dispatcher control (Turbine ordering becomes undefined).
// IO lives inside the repository; the ViewModel calls the repository directly.
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
        sut.uiState.test {
            assertEquals(ExamListUiState(), awaitItem())
            sut.loadExams()
            assertTrue(awaitItem().isLoading)
            assertEquals(1, awaitItem().exams.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

## Test Kontrol Listesi

Bir ViewModel için test yazarken:

- [ ] Başarı senaryosu (UiState.exams dolar, error null)
- [ ] Hata senaryosu (`ArchiveError`/`AuthError` tipinin `userMessage`'ı)
- [ ] Yükleme durumu geçişi (`isLoading` false → true → false, Turbine)
- [ ] Hata yolunda `isLoading` kapanıyor
- [ ] Durum güncellemesi doğrulandı (tek UiState alanları)
- [ ] Sayfalama (varsa: append + veri bitince sayfa artmaz)
- [ ] Tek seferlik event (`events` Flow'u Turbine ile)
- [ ] Repository doğru argüman/sayıda çağrıldı (Fake sayaç / `coVerify`)
- [ ] Sınır durumu: duplicate istek guard'ı
- [ ] Sınır durumu: boş sonuç
- [ ] `Fake...Repository` üretildi
- [ ] Tüm public fonksiyonlar kapsandı

## Özet

### Temel İlkeler

1. **`runTest` + Turbine** — tüm async işler için; `Thread.sleep` YASAK.
2. **`Fake...Repository`** — yapılandırılabilir sonuç + çağrı sayımı (iOS Mock delegate karşılığı).
3. **Given-When-Then** — net test yapısı.
4. **Hata = tip** — `error`'ı hata tipinin `userMessage`'ına göre assert et.
5. **Yükleme geçişi** — Turbine ile `false → true → false`.
6. **Repository pattern** — ViewModel `interface` arkasındaki repository'yi çağırır.
7. **Coroutines + Flow** — RxJava/delegate/completion handler YOK.
8. **`MainDispatcherRule`** — `Dispatchers.Main`'i test dispatcher'a çevir; IO repository'de kalsın.

### Test Şablonu

```kotlin
@Test
fun `{method} when {condition} {expectedResult}`() = runTest {
    // Given
    fakeRepository.examsToReturn = listOf(sampleExam) // or errorToThrow = ArchiveError.Offline

    // When / Then
    sut.uiState.test {
        assertEquals(ExamListUiState(), awaitItem())  // initial
        sut.method()
        assertTrue(awaitItem().isLoading)             // loading
        val result = awaitItem()                      // final
        // assert result fields...
        cancelAndIgnoreRemainingEvents()
    }
}
```

---

**Ayrıca Bkz (See Also):**
- `../SKILL.md` — Birim test üretim skill'i (adımlar, çıktı klasörleri, kapsam kategorileri)
- `../../../rules/testing.md` — `MainDispatcherRule`, `FakeArchiveRepository`, Fake vs. MockK, testTag
- `../../../rules/viewmodel-rule.md` — ViewModel + UiState + StateFlow + Channel event uygulaması
- `../../../rules/firebase-data.md` — Repository `interface`, DTO → domain map, IO dispatching
- `../../generate-mocks/SKILL.md` — `Fake...Repository` üretme
- `../../generate-ui-tests/SKILL.md` — Compose UI testi (`androidTest`)
