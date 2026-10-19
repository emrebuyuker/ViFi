---
name: generate-mocks
description: Generate test doubles for ViFi-Android ViewModel tests. Produces a hand-written Fake implementation of a repository interface (configurable returns, failure injection, call recording) as the default, and MockK (coEvery/coVerify) only when a one-off behavioural stub is needed. Follows the ViFi-Android Kotlin + Coroutines + Hilt target architecture.
---

> **Legacy not:** Bugünkü v2.5 Android kod tabanında (Java, `Activity`/`Fragment`, RTDB `Universitiess` doğrudan okuma) ne bir repository `interface`'i ne de test double'ı vardır — `ValueEventListener`'lar UI içinde canlıdır. Bu skill **yeni/ileriye dönük** test kodu içindir: ViewModel bir Kotlin `interface` repository'ye bağımlıdır ve teste `Fake...Repository` enjekte edilir. Göçteki yeri için bkz. repo kökü `ANDROID-PARITY-ROADMAP.md` / `ANDROID-PARITY-DETAIL.md` (Faz 1 mimari iskelet + `ARCH-15`/`BE-15` RTDB→Firestore cutover).

# Generate Mocks (ViFi-Android)

Bu skill, bir ViewModel'i canlı Firebase'den yalıtarak test etmek için gereken **test double**'ı üretir. iOS tarafındaki "ViewModel `delegate` protokolü + elle yazılmış `Mock...Delegate`" yaklaşımının Android karşılığı **repository `interface`'i + elle yazılmış `Fake...Repository`**'dir. iOS mock'u "hangi delegate metodu çağrıldı?" sorusunu izlerdi; Android Fake'i ise hem **sonucu yapılandırır** (başarı/hata) hem de **çağrıyı kaydeder** (iOS delegate-spy kavramının karşılığı: çağrı sayacı + son argüman).

## Usage

```
/generate-mocks [RepositoryName | interface_path]
```

**Argümanlar:**
- `RepositoryName` — bir repository arayüzü (ör. `ArchiveRepository`, `AuthRepository`, `UserPreferencesRepository`)
- `interface_path` — arayüzü içeren Kotlin dosyasının yolu (ör. `core/data/repository/ArchiveRepository.kt`)

## Neyi Üretir (What It Generates)

Bu skill iki tür double üretir; **varsayılan birincidir**:

1. **Elle yazılmış `Fake...Repository`** (varsayılan) — arayüzün tam, yeniden kullanılabilir bir implementasyonu:
   - **Yapılandırılabilir dönüşler** (`examsToReturn`, `userToReturn` gibi `var`'lar).
   - **Hata enjeksiyonu** (`errorToThrow: ArchiveError?`) — hata yolunu test etmek için.
   - **Çağrı kaydı / spy** (`getExamsCallCount`, `lastRequestedLessonId`) — iOS `*Called` bayrakları + `on*` closure'larının karşılığı.
   - **`Flow`/`StateFlow` sürücüsü** — gözlemlenen akışlara test gövdesinden emisyon vermek için backing `MutableSharedFlow`/`MutableStateFlow`.
2. **MockK stub** (yalnızca gerektiğinde) — tek seferlik, davranışı doğrulanacak bir bağımlılık için `coEvery`/`coVerify`.

**Kural: Önce `Fake`, gerektiğinde `MockK`.** `Fake` paylaşılır, preview'da da kullanılır ve davranışı okunur kılar; `MockK` ise tek bir testte nadir bir dalı kurmak için pratiktir.

## Desteklenen Tip (Supported Interface Pattern)

Bu skill **repository arayüzleri** için double üretir. ViFi-Android'de her veri kaynağı bir Kotlin `interface` arkasındadır (iOS `protocol + Service + Mock` üçlüsünün karşılığı: `interface + Firebase...Repository + Fake...Repository`).

### Desteklenen arayüz deseni

```kotlin
interface ArchiveRepository {
    suspend fun getExams(lessonId: String): List<Exam>          // suspend one-shot read
    suspend fun getExam(examId: String): Exam
    fun observeCatalogNodes(parentId: String?): Flow<List<CatalogNode>>  // realtime stream
    suspend fun downloadExamFile(storagePath: String, destination: File)
}
```

### Üretilmeyen / uyarlanan durumlar

- ❌ **Ham Firebase tipi döndüren arayüz** — `DocumentSnapshot`/`DataSnapshot`/`QuerySnapshot` UI'ya sızamaz; arayüz zaten plain domain `data class` döndürmelidir (bkz. `.claude/rules/firebase-data.md`). Böyle bir arayüz görürsen önce arayüzü düzelt, sonra Fake üret.
- ❌ **Somut sınıf** (`interface` değil) — test double üretilebilmesi için bağımlılık bir `interface` olmalı. `Firebase...Repository` somut implementasyonu için double üretme; onun `interface`'i için üret.
- ❌ **Composable / `@HiltViewModel` sınıfı** — bunlar test edilen taraftır, double değildir. ViewModel testinde ViewModel gerçek, repository Fake'tir.
- ⚠️ **iOS'a özgü hiçbir kavram üretilmez:** `delegate` protokolü yok, `Output` enum yok, `completion handler` yok. Android'de ViewModel durumu tek bir `StateFlow<XUiState>`'tedir; double, o durumu besleyen repository `interface`'inin implementasyonudur.

## Steps

1. **Arayüzü bul** — `RepositoryName` veya dosya yolundan `interface`'i konumla (`core/data/repository/` altında).
2. **Tipi doğrula:**
   - Bir `interface` olmalı (somut sınıf değil).
   - ViFi adlandırmasında `...Repository` ile bitmeli.
   - Ham Firebase tipi döndürmemeli (domain `data class` / `Flow<domain>` döndürmeli).
3. **`Fake...Repository` sınıfını üret** (varsayılan) — şu bölümlerle:
   - Yapılandırılabilir dönüşler (`var ...ToReturn`).
   - Hata enjeksiyonu (`var errorToThrow: XError?`).
   - Çağrı kaydı (`var ...CallCount` + `var last...`, `private set`).
   - `Flow`/`StateFlow` döndüren fonksiyonlar için backing akış + `emit...` test yardımcısı.
4. **Kaydet:** `app/src/test/java/com/salticusteam/vifi/fake/Fake{Name}.kt` (preview'da da gerekiyorsa `debug`/`testFixtures` kaynak setine; bkz. aşağıdaki **Paket / Konum**).
5. **(Opsiyonel) MockK** — tek bir testte nadir bir dal gerekiyorsa Fake yerine MockK stub örneği ver.

## Fake Üretim Deseni (Fake Generation Pattern)

### Girdi arayüzü (Input interface)

```kotlin
// core/data/repository/ArchiveRepository.kt
interface ArchiveRepository {
    suspend fun getExams(lessonId: String): List<Exam>
    fun observeCatalogNodes(parentId: String?): Flow<List<CatalogNode>>
    suspend fun downloadExamFile(storagePath: String, destination: File)
}
```

### Üretilen Fake (Generated Fake)

```kotlin
// app/src/test/java/com/salticusteam/vifi/fake/FakeArchiveRepository.kt
package com.salticusteam.vifi.fake

import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.CatalogNode
import com.salticusteam.vifi.core.model.Exam
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.File

/** Hand-written test double for [ArchiveRepository] — the Android equivalent of iOS's Mock*Delegate. */
class FakeArchiveRepository : ArchiveRepository {

    // --- Configurable returns (arrange per test) ---
    var examsToReturn: List<Exam> = emptyList()

    // --- Failure injection (replaces iOS "mock error" / delegate didFailure) ---
    var errorToThrow: ArchiveError? = null

    // --- Call recording (iOS delegate-spy: "*Called" + call counts + last args) ---
    var getExamsCallCount = 0
        private set
    var lastRequestedLessonId: String? = null
        private set
    var downloadExamFileCallCount = 0
        private set
    var lastDownloadStoragePath: String? = null
        private set

    override suspend fun getExams(lessonId: String): List<Exam> {
        getExamsCallCount++
        lastRequestedLessonId = lessonId
        errorToThrow?.let { throw it }
        return examsToReturn
    }

    // --- Flow: drive emissions from the test body (equivalent of iOS realtime delegate) ---
    private val catalogNodesFlow = MutableSharedFlow<List<CatalogNode>>(replay = 1)
    override fun observeCatalogNodes(parentId: String?): Flow<List<CatalogNode>> = catalogNodesFlow
    suspend fun emitCatalogNodes(nodes: List<CatalogNode>) = catalogNodesFlow.emit(nodes)

    override suspend fun downloadExamFile(storagePath: String, destination: File) {
        downloadExamFileCallCount++
        lastDownloadStoragePath = storagePath
        errorToThrow?.let { throw it }
    }
}
```

> Hem `ArchiveRepository` hem `AuthRepository` için **tam**, derlenebilir Fake örnekleri ve test içinde kullanım kalıpları için bkz. `patterns/repository-fake.md`.

## Üretim Kuralları (Output Rules)

1. **Dosya konumu:** `app/src/test/java/com/salticusteam/vifi/fake/Fake{Name}.kt` (paylaşılan test double'ları). Preview'da da kullanılacaksa `debug`/`testFixtures` kaynak setine koy.
2. **Adlandırma:** `class Fake{InterfaceName} : {InterfaceName}` — ör. `FakeArchiveRepository : ArchiveRepository`.
3. **Her arayüz fonksiyonu için üret:**
   - `var {result}ToReturn: {ReturnType}` — yapılandırılabilir başarı sonucu (varsayılan boş/makul).
   - `var errorToThrow: {DomainError}?` — ortak hata enjeksiyonu (tüm fonksiyonlar paylaşır; gerekirse fonksiyon-bazlı ayır).
   - `var {fn}CallCount: Int` (`private set`) — iOS `{method}CallCount` karşılığı.
   - `var last{Arg}: {Type}?` (`private set`) — her anlamlı argüman için, iOS `last{Param}` karşılığı.
4. **`Flow`/`StateFlow` döndüren fonksiyon:** backing `MutableSharedFlow`/`MutableStateFlow` + testten emisyon için `suspend fun emit{X}(...)` / `set{X}(...)` yardımcısı.
5. **`suspend` fonksiyon:** gövdede önce kaydet (`CallCount++`, `last... = arg`), sonra `errorToThrow?.let { throw it }`, sonra `return ...ToReturn`.
6. **Sıfırlama:** iOS'taki `reset()` metodunun karşılığı Android'de **her teste taze `Fake` oluşturmaktır** (`@Before { fake = FakeArchiveRepository() }`). Bu yüzden `reset()` varsayılan olarak üretilmez; durum testler arası paylaşılmaz. (Paylaşılan bir Fake'i birden çok senaryoda yeniden kullanman gerekirse `fun reset()` ekleyebilirsin.)
7. **Bölüm yorumları:** `// --- Configurable returns ---`, `// --- Failure injection ---`, `// --- Call recording ---`, `// --- Flow ---` ile düzenle (iOS `// MARK: -` karşılığı).

## Dönüş & Çağrı Kaydı Desenleri (Return & Call-Recording Patterns)

iOS'ta her delegate metodu için parametre depolama + closure üretilirdi. Android'de bunun karşılığı: dönüşü yapılandır, argümanı kaydet. Fonksiyon şekline göre:

### `suspend` — dönüşsüz (Unit)

```kotlin
// Interface
suspend fun signOut()

// Fake
var signOutCallCount = 0
    private set

override suspend fun signOut() {
    signOutCallCount++
    errorToThrow?.let { throw it }
}
```

### `suspend` — tek dönüş + tek argüman

```kotlin
// Interface
suspend fun getExams(lessonId: String): List<Exam>

// Fake
var examsToReturn: List<Exam> = emptyList()
var getExamsCallCount = 0
    private set
var lastRequestedLessonId: String? = null
    private set

override suspend fun getExams(lessonId: String): List<Exam> {
    getExamsCallCount++
    lastRequestedLessonId = lessonId
    errorToThrow?.let { throw it }
    return examsToReturn
}
```

### `suspend` — çoklu argüman (spy: hepsini kaydet)

```kotlin
// Interface
suspend fun verifyCode(verificationId: String, smsCode: String): User

// Fake
var userToReturn: User = User(id = "u1", phone = "+905551112233")
var verifyCodeCallCount = 0
    private set
var lastVerificationId: String? = null
    private set
var lastSmsCode: String? = null
    private set

override suspend fun verifyCode(verificationId: String, smsCode: String): User {
    verifyCodeCallCount++
    lastVerificationId = verificationId
    lastSmsCode = smsCode
    errorToThrow?.let { throw it }
    return userToReturn
}
```

### `Flow` döndüren (realtime dinleme)

```kotlin
// Interface
fun observeCatalogNodes(parentId: String?): Flow<List<CatalogNode>>

// Fake — drive emissions from the test body (equivalent of manually calling the iOS delegate)
private val catalogNodesFlow = MutableSharedFlow<List<CatalogNode>>(replay = 1)
override fun observeCatalogNodes(parentId: String?): Flow<List<CatalogNode>> = catalogNodesFlow
suspend fun emitCatalogNodes(nodes: List<CatalogNode>) = catalogNodesFlow.emit(nodes)
```

### `StateFlow` özelliği döndüren (gözlemlenen oturum)

```kotlin
// Interface
val currentUser: StateFlow<User?>

// Fake — the test sets/updates the current state directly
private val _currentUser = MutableStateFlow<User?>(null)
override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()
fun setSignedIn(user: User) { _currentUser.value = user }
```

### Nullable / opsiyonel dönüş

```kotlin
// Interface
suspend fun getExam(examId: String): Exam

// Fake — fail with a meaningful message if not arranged (instead of a silent NPE)
var examToReturn: Exam? = null
override suspend fun getExam(examId: String): Exam {
    errorToThrow?.let { throw it }
    return examToReturn ?: error("FakeArchiveRepository.examToReturn not set for getExam($examId)")
}
```

## MockK (gerektiğinde)

Tek bir testte, az kullanılan bir dalı kurmak veya "tam olarak bu argümanla çağrıldı mı?" doğrulaması yapmak için `Fake` yerine MockK tercih edilebilir. `suspend` fonksiyonlar için `every`/`verify` değil **`coEvery`/`coVerify`** kullanılır:

```kotlin
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

@Test
fun `loadExams calls repository once with given lessonId`() = runTest {
    val repository = mockk<ArchiveRepository>()
    coEvery { repository.getExams("blg-101") } returns listOf(sampleExam)

    val sut = ExamListViewModel(repository, SavedStateHandle(mapOf("lessonId" to "blg-101")))
    sut.loadExams()
    advanceUntilIdle()

    coVerify(exactly = 1) { repository.getExams("blg-101") }
}
```

Hata dalı MockK ile:

```kotlin
coEvery { repository.getExams(any()) } throws ArchiveError.Offline
```

> **Ne zaman hangisi?** Birden çok testte paylaşılan, okunur, preview'da da işe yarayan double → `Fake`. Tek testte nadir bir dal / sıkı çağrı doğrulaması → `MockK`. İkisini karıştırma: bir test sınıfında aynı bağımlılık için ya Fake ya MockK kullan.

## Paket / Konum (Package & Location)

iOS tarafında tüm mock'lar tek bir test "Mocks" klasörü altında düz tutulurdu. Android'de double'lar **paylaşılabilirliğe göre** yerleştirilir:

- **Yalnızca birim testte kullanılan Fake** → `app/src/test/java/com/salticusteam/vifi/fake/Fake{Name}.kt`.
- **Hem testte hem Compose `@Preview`'da kullanılan Fake** → `debug` kaynak seti veya `testFixtures` (her iki tarafın da görebilmesi için). Preview'a özel örnek verileri Fake'in `companion object`'inde tut (`SAMPLE_EXAMS` gibi; bkz. `.claude/rules/firebase-data.md`).

```
app/src/test/java/com/salticusteam/vifi/
└── fake/
    ├── FakeArchiveRepository.kt
    ├── FakeAuthRepository.kt
    └── FakeUserPreferencesRepository.kt
```

> iOS'taki "modül klasörüne göre mock" yaklaşımı Android'de yok; double'lar tek `fake/` paketinde toplanır, test tarafındaki `feature/` + `core/` aynalaması test sınıfları içindir (bkz. `.claude/rules/testing.md`).

## Yaygın Repository Desenleri (Common Repository Patterns)

Üretilecek Fake'in şekli, arayüzün rolüne göre değişir. iOS'taki delegate kalıplarının (data-loading, CRUD, pagination, selection) karşılığı:

### Data-loading repository

```kotlin
interface ArchiveRepository {
    suspend fun getExams(lessonId: String): List<Exam>
    fun observeCatalogNodes(parentId: String?): Flow<List<CatalogNode>>
}
// Fake: examsToReturn + errorToThrow + getExamsCallCount + lastRequestedLessonId + emitCatalogNodes(...)
```

### CRUD / submission repository

```kotlin
interface SubmissionRepository {
    suspend fun submitExam(submission: PendingSubmission): String   // returns new id
    suspend fun deleteSubmission(submissionId: String)
}
// Fake: submittedIdToReturn + errorToThrow + submitExamCallCount + lastSubmission + deleteSubmissionCallCount + lastDeletedId
```

### Pagination repository

```kotlin
interface ExamPageRepository {
    suspend fun getExamsPage(lessonId: String, pageToken: String?, pageSize: Int): ExamPage
}
// Fake: pagesToReturn: List<ExamPage> (return pages in order across successive calls) + lastPageToken + getExamsPageCallCount
```

### Auth repository (telefon doğrulama)

```kotlin
interface AuthRepository {
    val currentUser: StateFlow<User?>
    suspend fun sendVerificationCode(phoneNumber: String): String   // verificationId
    suspend fun verifyCode(verificationId: String, smsCode: String): User
    suspend fun signOut()
}
// Fake: verificationIdToReturn + userToReturn + errorToThrow (AuthError) + call counters + _currentUser StateFlow driver
```

> `AuthRepository` için tam Fake (`StateFlow<User?>` + telefon doğrulama akışı) `patterns/repository-fake.md`'dedir.

## Örnek Kullanım (Example Usage)

### 1. Var olan bir arayüz için Fake üret

```
/generate-mocks ArchiveRepository
→ app/src/test/java/com/salticusteam/vifi/fake/FakeArchiveRepository.kt
```

### 2. Dosya yolundan üret

```
/generate-mocks core/data/repository/AuthRepository.kt
→ AuthRepository arayüzünü bulur
→ app/src/test/java/com/salticusteam/vifi/fake/FakeAuthRepository.kt
```

### 3. Testte kullanım (Fake'i ViewModel'e enjekte et)

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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ExamListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepository: FakeArchiveRepository
    private lateinit var sut: ExamListViewModel

    private val sampleExam = Exam(id = "e1", title = "BLG 101 Vize", courseId = "blg-101", kind = "pdf", year = 2024, isLocked = false)

    @Before
    fun setUp() {
        // Fresh Fake per test — the Android replacement for iOS mock.reset().
        fakeRepository = FakeArchiveRepository()
    }

    @Test
    fun `loadExams when success updates uiState and records the call`() = runTest {
        // Given
        fakeRepository.examsToReturn = listOf(sampleExam)

        // When
        sut = ExamListViewModel(fakeRepository, savedStateFor("blg-101"))
        sut.loadExams()
        advanceUntilIdle()

        // Then — assert rendered state
        val state = sut.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(1, state.exams.size)

        // ...and the spy (equivalent of iOS "*Called" / "last*")
        assertEquals(1, fakeRepository.getExamsCallCount)
        assertEquals("blg-101", fakeRepository.lastRequestedLessonId)
    }

    @Test
    fun `loadExams when repository throws sets userMessage`() = runTest {
        // Given
        fakeRepository.errorToThrow = ArchiveError.Offline

        // When
        sut = ExamListViewModel(fakeRepository, savedStateFor("blg-101"))
        sut.loadExams()
        advanceUntilIdle()

        // Then — assert by error TYPE's userMessage, never a raw literal
        assertEquals(ArchiveError.Offline.userMessage, sut.uiState.value.error)
    }
}
```

> Tam test kalıpları (Turbine ile emisyon doğrulama, yükleme durumu geçişi, pagination, `MainDispatcherRule`) için bkz. `.claude/rules/testing.md` ve `.claude/skills/generate-unit-tests/SKILL.md`.

## Doğrulama Kuralları (Validation Rules)

Fake üretmeden önce doğrula:

1. ✅ Hedef bir **`interface`** (somut sınıf değil).
2. ✅ Adı ViFi adlandırmasında `...Repository` ile bitiyor.
3. ✅ Arayüz **domain tipi** / `Flow<domain>` döndürüyor — ham Firebase tipi (`DocumentSnapshot`/`DataSnapshot`/`QuerySnapshot`/`StorageReference`) **döndürmüyor**.
4. ✅ Hataların tiplenmiş karşılığı var (`ArchiveError`/`AuthError`) — Fake `errorToThrow` bunu atar.
5. ❌ Hedef `Firebase...Repository` somut implementasyonuysa reddet — onun `interface`'i için üret.
6. ❌ Hedef bir ViewModel / Composable ise reddet — bunlar test edilen taraftır, double değildir.
7. ❌ Üçüncü parti kütüphane tipi için double üretme; bağımlılığı kendi `interface`'inin arkasına koy, onu fake'le.

## Hata Mesajları (Error Messages)

### Arayüz yerine somut sınıf

```
❌ Hata: 'FirestoreArchiveRepository' bir interface değil, canlı implementasyon.
   ViFi-Android test double'ları interface için üretilir.
   Beklenen: 'ArchiveRepository' arayüzü için Fake üret.
```

### Ham Firebase tipi sızdıran arayüz

```
❌ Hata: 'LegacyArchiveRepository.getUniversities()' ham DataSnapshot döndürüyor.
   Repository UI'ya ham Firebase tipi SIZDIRAMAZ (bkz. firebase-data.md).
   Önce arayüzü DTO→domain map edecek şekilde düzelt (List<CatalogNode>), sonra Fake üret.
```

### Hedef test edilen taraf

```
❌ Hata: 'ExamListViewModel' bir ViewModel; test double değildir.
   ViewModel testinde ViewModel gerçektir, bağımlı olduğu repository Fake'lenir.
   Doğrusu: 'ArchiveRepository' için Fake üret, ExamListViewModel'e enjekte et.
```

## Template Structure

```kotlin
// app/src/test/java/com/salticusteam/vifi/fake/Fake{Name}.kt
package com.salticusteam.vifi.fake

import {domain + interface imports}
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
// If there is a StateFlow property: MutableStateFlow + asStateFlow

/** Hand-written test double for [{Name}] — the Android equivalent of iOS's Mock*Delegate. */
class Fake{Name} : {Name} {

    // --- Configurable returns ---
    {var ...ToReturn}

    // --- Failure injection ---
    var errorToThrow: {DomainError}? = null

    // --- Call recording (spy) ---
    {var ...CallCount; private set}
    {var last...; private set}

    // --- Flow / StateFlow drivers (if any) ---
    {private backing flow + override + emit/set helper}

    // --- Interface implementation ---
    {override funs: record args → maybe throw → return configured value}
}
```

## Kurallar (Rules)

1. ✅ **ÖNCE Fake, gerektiğinde MockK.** Paylaşılan/okunur double → `Fake`; tek seferlik nadir dal → `MockK` (`coEvery`/`coVerify`).
2. ✅ Fake **arayüzün tamamını** implement eder; dönüşleri `var ...ToReturn` ile yapılandırılabilir yapar.
3. ✅ **Hata enjeksiyonu** `var errorToThrow: XError?` ile; gövdede kayıttan sonra, dönüşten önce atılır.
4. ✅ **Çağrı kaydı** (`...CallCount` + `last...`, `private set`) her anlamlı fonksiyon/argüman için (iOS delegate-spy karşılığı).
5. ✅ `Flow` → backing `MutableSharedFlow` + `emit...` yardımcısı; `StateFlow` özelliği → `MutableStateFlow` + `set...`/`asStateFlow()`.
6. ✅ **Her teste taze Fake** (`@Before`), durum testler arası PAYLAŞILMAZ — iOS `reset()`'in karşılığı budur; `reset()` varsayılan üretilmez.
7. ✅ `suspend` fonksiyonlarda MockK ile **`coEvery`/`coVerify`** (asla `every`/`verify`).
8. ❌ Double'dan **ham Firebase tipi döndürme**; arayüz zaten domain `data class` döndürür.
9. ❌ Somut `Firebase...Repository` / ViewModel / Composable için double üretme.
10. ❌ Global mutable singleton double kullanma; double daima constructor ile enjekte edilir (iOS `AppEnvironment.mock` karşılığı Hilt test binding'idir).

---

**See Also:**
- `patterns/repository-fake.md` — `ArchiveRepository` ve `AuthRepository` için tam Fake örnekleri (suspend + `Flow`/`StateFlow`)
- `.claude/rules/testing.md` — Fake ile ViewModel birim testi (JUnit + Turbine + coroutines-test), `MainDispatcherRule`, testTag
- `.claude/rules/firebase-data.md` — Repository `interface`'leri, DTO→domain map, `ArchiveError`/`AuthError`, Hilt binding
- `.claude/rules/architecture.md` — MVVM + Hilt + `StateFlow` hedef mimari
- `.claude/skills/generate-unit-tests/SKILL.md` — Fake kullanan ViewModel/mapper birim testi üretimi
- `.claude/skills/generate-ui-tests/SKILL.md` — Compose UI testi üretimi
- `.claude/skills/new-feature/SKILL.md` — Repository + ViewModel + ekran paketi iskeleti (Fake dahil)
- `.claude/commands/generate-mocks.md` — bu skill'i çalıştıran komut
