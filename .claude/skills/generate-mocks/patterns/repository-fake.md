# ViFi-Android Repository Fake Pattern

> **Legacy not:** Bugünkü v2.5 kodunda repository `interface`'i yoktur; `Activity`/`Fragment` RTDB `Universitiess`'i doğrudan `ValueEventListener` ile okur, bu yüzden fake'lenecek bir sınır da yoktur. Bu desen **yeni/ileriye dönük** test kodu içindir. Göçteki yeri: `ANDROID-PARITY-ROADMAP.md` → Faz 1 (repository arayüzleri) + `ARCH-15`/`BE-15` (RTDB→Firestore).

## Genel Bakış (Overview)

ViFi-Android, ViewModel'i canlı Firebase'den yalıtmak için **repository `interface`'ini implement eden elle yazılmış bir `Fake`** kullanır. Bu, iOS'taki "ViewModel `delegate` protokolü + `Mock...Delegate`" ikilisinin karşılığıdır — ama iki fark vardır:

- iOS mock'u **sadece spy**'dı: "hangi delegate metodu çağrıldı, hangi argümanla?" (çünkü ViewModel sonucu delegate'e push ederdi).
- Android Fake'i hem **stub** hem **spy**'dır: sonucu **yapılandırır** (başarı/hata), **sonra** çağrıyı **kaydeder** (çağrı sayacı + son argüman). Çünkü ViewModel sonucu repository'den `suspend`/`Flow` ile **çeker** ve kendi `StateFlow<XUiState>`'ine yazar.

Her repository arayüzü için bir `Fake...Repository` üretilir. Aşağıda iki tam örnek vardır: `ArchiveRepository` (suspend + `Flow`) ve `AuthRepository` (`StateFlow<User?>` + telefon doğrulama).

## Desen: Arayüzü implement eden Fake (Pattern: Fake implementing an interface)

```kotlin
// Input interface (core/data/repository/ArchiveRepository.kt)
interface ArchiveRepository {
    suspend fun getCatalogNodes(parentId: String?): List<CatalogNode>
    suspend fun getExams(lessonId: String): List<Exam>
    suspend fun getExam(examId: String): Exam
    fun observeCatalogNodes(parentId: String?): Flow<List<CatalogNode>>
    suspend fun downloadExamFile(storagePath: String, destination: File)
}
```

## Üretilen Fake Şablonu — ArchiveRepository (Generated Fake Template)

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
    var catalogNodesToReturn: List<CatalogNode> = emptyList()
    var examsToReturn: List<Exam> = emptyList()
    var examToReturn: Exam? = null

    // --- Failure injection (one error shared by all calls; split if a test needs per-call errors) ---
    var errorToThrow: ArchiveError? = null

    // --- Call recording / spy (equivalent of iOS "*Called" + "*CallCount" + "last*") ---
    var getCatalogNodesCallCount = 0
        private set
    var getExamsCallCount = 0
        private set
    var getExamCallCount = 0
        private set
    var downloadExamFileCallCount = 0
        private set
    var lastRequestedParentId: String? = null
        private set
    var lastRequestedLessonId: String? = null
        private set
    var lastRequestedExamId: String? = null
        private set
    var lastDownloadStoragePath: String? = null
        private set

    override suspend fun getCatalogNodes(parentId: String?): List<CatalogNode> {
        getCatalogNodesCallCount++
        lastRequestedParentId = parentId
        errorToThrow?.let { throw it }
        return catalogNodesToReturn
    }

    override suspend fun getExams(lessonId: String): List<Exam> {
        getExamsCallCount++
        lastRequestedLessonId = lessonId
        errorToThrow?.let { throw it }
        return examsToReturn
    }

    override suspend fun getExam(examId: String): Exam {
        getExamCallCount++
        lastRequestedExamId = examId
        errorToThrow?.let { throw it }
        // Fail loudly if the test forgot to arrange the return (better than a silent NPE).
        return examToReturn ?: error("FakeArchiveRepository.examToReturn not set for getExam($examId)")
    }

    // --- Flow: drive emissions from the test body (equivalent of the iOS realtime delegate call) ---
    private val catalogNodesFlow = MutableSharedFlow<List<CatalogNode>>(replay = 1)
    override fun observeCatalogNodes(parentId: String?): Flow<List<CatalogNode>> {
        lastRequestedParentId = parentId
        return catalogNodesFlow
    }

    /** Test helper: push a new emission into [observeCatalogNodes]. */
    suspend fun emitCatalogNodes(nodes: List<CatalogNode>) = catalogNodesFlow.emit(nodes)

    override suspend fun downloadExamFile(storagePath: String, destination: File) {
        downloadExamFileCallCount++
        lastDownloadStoragePath = storagePath
        errorToThrow?.let { throw it }
    }
}
```

## Üretim Kuralları (Generation Rules)

### Her arayüz fonksiyonu için:

1. **Yapılandırılabilir dönüş** (`Unit` dışındaki her dönüş için)
   ```kotlin
   var {result}ToReturn: {ReturnType} = {reasonable default}
   ```

2. **Çağrı sayacı** (spy — `private set`)
   ```kotlin
   var {fn}CallCount = 0
       private set
   ```

3. **Son argüman kaydı** (anlamlı her argüman için, `private set`)
   ```kotlin
   var last{Arg}: {Type}? = null
       private set
   ```

4. **`override` implementasyonu** (sırası önemli: kaydet → belki hata at → dönüşü ver)
   ```kotlin
   override suspend fun {fn}({args}): {ReturnType} {
       {fn}CallCount++
       last{Arg} = {arg}
       errorToThrow?.let { throw it }
       return {result}ToReturn
   }
   ```

5. **`Flow`/`StateFlow` sürücüsü** (akış döndüren fonksiyon için)
   ```kotlin
   private val {x}Flow = MutableSharedFlow<{T}>(replay = 1)
   override fun observe{X}(...): Flow<{T}> = {x}Flow
   suspend fun emit{X}(value: {T}) = {x}Flow.emit(value)
   ```

6. **Sıfırlama yok, taze örnek var** (iOS `reset()` karşılığı)
   ```kotlin
   // New Fake in @Before for each test; state is not shared.
   @Before fun setUp() { fakeRepository = FakeArchiveRepository() }
   ```

## Argüman & Dönüş İşleme Desenleri (Argument & Return Handling Patterns)

iOS'ta parametre depolama delegate metodunun parametresini saklamaktı; Android'de hem **girdi argümanını** kaydederiz (spy) hem **çıktıyı** yapılandırırız (stub).

### Dönüşsüz (Unit) `suspend`

```kotlin
// Interface
suspend fun downloadExamFile(storagePath: String, destination: File)

// Fake
var downloadExamFileCallCount = 0
    private set
var lastDownloadStoragePath: String? = null
    private set

override suspend fun downloadExamFile(storagePath: String, destination: File) {
    downloadExamFileCallCount++
    lastDownloadStoragePath = storagePath
    errorToThrow?.let { throw it }
}
```

### Tek argüman + tek dönüş

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

### Çoklu argüman (hepsini kaydet — spy)

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

### Nullable dönüş / kurulmamış değer

```kotlin
// Interface
suspend fun getExam(examId: String): Exam

// Fake — fail with a meaningful message if the value is not arranged (instead of a silent NPE)
var examToReturn: Exam? = null
override suspend fun getExam(examId: String): Exam {
    errorToThrow?.let { throw it }
    return examToReturn ?: error("FakeArchiveRepository.examToReturn not set for getExam($examId)")
}
```

### Ardışık çağrılarda farklı dönüş (pagination)

```kotlin
// Interface
suspend fun getExamsPage(lessonId: String, pageToken: String?, pageSize: Int): ExamPage

// Fake — return the next page from the list on each call (equivalent of multiple iOS delegate calls)
var pagesToReturn: List<ExamPage> = emptyList()
var getExamsPageCallCount = 0
    private set
var lastPageToken: String? = null
    private set

override suspend fun getExamsPage(lessonId: String, pageToken: String?, pageSize: Int): ExamPage {
    lastPageToken = pageToken
    errorToThrow?.let { throw it }
    val page = pagesToReturn.getOrNull(getExamsPageCallCount)
        ?: error("FakeArchiveRepository.pagesToReturn exhausted at call #$getExamsPageCallCount")
    getExamsPageCallCount++
    return page
}
```

## İkinci Tam Örnek — AuthRepository (StateFlow + telefon doğrulama)

iOS'ta `currentUser` bir publisher'dı ve telefon doğrulama delegate callback'leriyle ilerlerdi. Android'de `currentUser` bir `StateFlow<User?>`'dur; Fake onu doğrudan sürer.

```kotlin
// Input interface (core/data/repository/AuthRepository.kt)
interface AuthRepository {
    /** The signed-in user; null == signed out. Hot stream (equivalent of the iOS currentUser publisher). */
    val currentUser: StateFlow<User?>

    /** Sends an SMS verification code to the +90 phone number; returns the verificationId used in the next step. */
    suspend fun sendVerificationCode(phoneNumber: String): String

    /** Verifies the verificationId + the code entered by the user; on success opens the session and returns User. */
    suspend fun verifyCode(verificationId: String, smsCode: String): User

    /** Signs out; currentUser becomes null. */
    suspend fun signOut()
}
```

```kotlin
// app/src/test/java/com/salticusteam/vifi/fake/FakeAuthRepository.kt
package com.salticusteam.vifi.fake

import com.salticusteam.vifi.core.data.repository.AuthRepository
import com.salticusteam.vifi.core.model.AuthError
import com.salticusteam.vifi.core.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Hand-written test double for [AuthRepository] — drives the session StateFlow directly. */
class FakeAuthRepository : AuthRepository {

    // --- Configurable returns ---
    var verificationIdToReturn: String = "fake-verification-id"
    var userToReturn: User = User(id = "u1", phone = "+905551112233")

    // --- Failure injection ---
    var errorToThrow: AuthError? = null

    // --- Call recording / spy ---
    var sendVerificationCodeCallCount = 0
        private set
    var verifyCodeCallCount = 0
        private set
    var signOutCallCount = 0
        private set
    var lastPhoneNumber: String? = null
        private set
    var lastVerificationId: String? = null
        private set
    var lastSmsCode: String? = null
        private set

    // --- Observable session (equivalent of the iOS currentUser publisher) ---
    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    /** Test helper: arrange an already-signed-in session before the act. */
    fun setSignedIn(user: User) {
        _currentUser.value = user
    }

    override suspend fun sendVerificationCode(phoneNumber: String): String {
        sendVerificationCodeCallCount++
        lastPhoneNumber = phoneNumber
        errorToThrow?.let { throw it }
        return verificationIdToReturn
    }

    override suspend fun verifyCode(verificationId: String, smsCode: String): User {
        verifyCodeCallCount++
        lastVerificationId = verificationId
        lastSmsCode = smsCode
        errorToThrow?.let { throw it }
        // Success also opens the session, so a collector of currentUser observes the transition.
        return userToReturn.also { _currentUser.value = it }
    }

    override suspend fun signOut() {
        signOutCallCount++
        _currentUser.value = null
    }
}
```

> `User` domain modeli plain bir `data class`'tır (`core/model/User.kt`): `data class User(val id: String, val phone: String)`. `AuthError` sealed class'ı Türkçe `userMessage` taşır (bkz. `.claude/rules/firebase-data.md`).

## Testte Kullanım (Usage in Tests)

### Temel test (başarı + spy doğrulama)

```kotlin
package com.salticusteam.vifi.feature.examlist

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

    private val sampleExam = Exam(
        id = "e1", title = "BLG 101 Vize", courseId = "blg-101",
        kind = "pdf", year = 2024, isLocked = false,
    )

    @Before
    fun setUp() {
        // Fresh Fake per test — the Android replacement for iOS mock.reset().
        fakeRepository = FakeArchiveRepository()
    }

    @Test
    fun `loadExams when success exposes exams and records the call`() = runTest {
        // Given
        fakeRepository.examsToReturn = listOf(sampleExam)
        val sut = ExamListViewModel(fakeRepository, savedStateFor("blg-101"))

        // When
        sut.loadExams()
        advanceUntilIdle()

        // Then — rendered state
        val state = sut.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(1, state.exams.size)

        // ...and the spy (equivalent of iOS "*Called" / "last*")
        assertEquals(1, fakeRepository.getExamsCallCount)
        assertEquals("blg-101", fakeRepository.lastRequestedLessonId)
    }
}
```

### Hata yolu (errorToThrow)

```kotlin
import com.salticusteam.vifi.core.model.ArchiveError

@Test
fun `loadExams when offline sets userMessage and no exams`() = runTest {
    // Given
    fakeRepository.errorToThrow = ArchiveError.Offline
    val sut = ExamListViewModel(fakeRepository, savedStateFor("blg-101"))

    // When
    sut.loadExams()
    advanceUntilIdle()

    // Then — assert by the error TYPE's userMessage, never a raw Turkish literal
    val state = sut.uiState.value
    assertTrue(state.exams.isEmpty())
    assertEquals(ArchiveError.Offline.userMessage, state.error)
}
```

### `Flow` emisyonunu Turbine ile doğrulama

```kotlin
import app.cash.turbine.test

@Test
fun `observeCatalogNodes emits nodes pushed from the fake`() = runTest {
    // Given
    val sut = CatalogViewModel(fakeRepository)   // collects observeCatalogNodes(null) in init

    sut.uiState.test {
        assertEquals(emptyList<CatalogNode>(), awaitItem().nodes)   // initial

        // When — drive an emission from the test body (equivalent of manually calling the iOS delegate)
        fakeRepository.emitCatalogNodes(listOf(sampleNode))

        // Then
        assertEquals(1, awaitItem().nodes.size)
        cancelAndIgnoreRemainingEvents()
    }
}
```

### Çağrı sayacı (iOS CallCount karşılığı)

```kotlin
@Test
fun `refresh twice calls repository twice`() = runTest {
    val sut = ExamListViewModel(fakeRepository, savedStateFor("blg-101"))

    sut.loadExams()
    sut.loadExams()
    advanceUntilIdle()

    assertEquals(2, fakeRepository.getExamsCallCount)
}
```

### AuthRepository: StateFlow oturum geçişi

```kotlin
import com.salticusteam.vifi.fake.FakeAuthRepository

@Test
fun `verifyCode when success opens session and records args`() = runTest {
    // Given
    val fakeAuth = FakeAuthRepository().apply {
        userToReturn = User(id = "u42", phone = "+905551112233")
    }
    val sut = PhoneAuthViewModel(fakeAuth)

    // When
    sut.onCodeSubmit(verificationId = "vid-1", code = "123456")
    advanceUntilIdle()

    // Then — session opened (StateFlow) + call recorded (spy)
    assertEquals("u42", fakeAuth.currentUser.value?.id)
    assertEquals(1, fakeAuth.verifyCodeCallCount)
    assertEquals("vid-1", fakeAuth.lastVerificationId)
    assertEquals("123456", fakeAuth.lastSmsCode)
}

@Test
fun `verifyCode when code invalid surfaces AuthError userMessage`() = runTest {
    val fakeAuth = FakeAuthRepository().apply { errorToThrow = AuthError.InvalidCode }
    val sut = PhoneAuthViewModel(fakeAuth)

    sut.onCodeSubmit(verificationId = "vid-1", code = "000000")
    advanceUntilIdle()

    assertEquals(AuthError.InvalidCode.userMessage, sut.uiState.value.error)
    assertNull(fakeAuth.currentUser.value)   // session not opened
}
```

### "Taze örnek" ile temiz durum (iOS reset() karşılığı)

```kotlin
@Test
fun `each test starts from a clean fake`() = runTest {
    // A new FakeArchiveRepository is created in setUp(), so the counter starts at 0.
    assertEquals(0, fakeRepository.getExamsCallCount)

    val sut = ExamListViewModel(fakeRepository, savedStateFor("blg-101"))
    sut.loadExams()
    advanceUntilIdle()

    assertEquals(1, fakeRepository.getExamsCallCount)   // isolated, unaffected by the previous test
}
```

## MockK Alternatifi (gerektiğinde)

Tek bir testte nadir bir dalı kurmak veya sıkı çağrı doğrulaması yapmak istersen, Fake yerine MockK kullan. `suspend` için `coEvery`/`coVerify`:

```kotlin
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

@Test
fun `loadExams calls repository exactly once with the route lessonId`() = runTest {
    val repository = mockk<ArchiveRepository>()
    coEvery { repository.getExams("blg-101") } returns listOf(sampleExam)

    val sut = ExamListViewModel(repository, savedStateFor("blg-101"))
    sut.loadExams()
    advanceUntilIdle()

    coVerify(exactly = 1) { repository.getExams("blg-101") }
}
```

## Kullanılmayan Desenler (Anti-Patterns — NOT Used in ViFi-Android)

### ❌ Ham Firebase tipi döndüren Fake

```kotlin
// ❌ DON'T — the repository must not leak a raw Firebase type to the UI
class FakeArchiveRepository {
    fun getExams(lessonId: String): QuerySnapshot { /* ... */ }   // ❌ QuerySnapshot
}

// ✅ Correct — the interface returns a domain data class, and so does the Fake
class FakeArchiveRepository : ArchiveRepository {
    override suspend fun getExams(lessonId: String): List<Exam> = examsToReturn   // ✅
}
```

### ❌ `reset()`'e bağlı paylaşılan durum

```kotlin
// ❌ DON'T — a Fake shared across tests + manual reset (copying the iOS pattern verbatim)
companion object { val sharedFake = FakeArchiveRepository() }   // ❌ global mutable

// ✅ Correct — a fresh Fake in @Before for each test, injected
@Before fun setUp() { fakeRepository = FakeArchiveRepository() }   // ✅
```

### ❌ Hata yolunu düz Türkçe literal'e bağlama

```kotlin
// ❌ DON'T — the test breaks when the copy changes
assertEquals("İnternet bağlantısı yok. Lütfen bağlantınızı kontrol edip tekrar deneyin.", state.error)

// ✅ Correct — assert against the error type's userMessage
assertEquals(ArchiveError.Offline.userMessage, state.error)
```

### ❌ `suspend` fonksiyonda `every`/`verify`

```kotlin
// ❌ DON'T
every { repository.getExams(any()) } returns listOf(sampleExam)   // does not compile / wrong for suspend

// ✅ Correct
coEvery { repository.getExams(any()) } returns listOf(sampleExam)
coVerify { repository.getExams("blg-101") }
```

## Dosya Şablonu (File Template)

```kotlin
// app/src/test/java/com/salticusteam/vifi/fake/Fake{Name}.kt
package com.salticusteam.vifi.fake

import {domain + interface imports}
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
// If there is a StateFlow property: MutableStateFlow, StateFlow, asStateFlow

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

---

**See Also:**
- `../SKILL.md` — Fake/MockK üretim kuralları, doğrulama, hata mesajları
- `.claude/rules/testing.md` — Fake ile birim test (JUnit + Turbine + coroutines-test), `MainDispatcherRule`, testTag
- `.claude/rules/firebase-data.md` — Repository `interface`'leri, `ArchiveRepository`/`AuthRepository`, `ArchiveError`/`AuthError`, DTO→domain map
- `.claude/rules/architecture.md` — MVVM + Hilt + `StateFlow` hedef mimari
- `.claude/skills/generate-unit-tests/SKILL.md` — bu Fake'leri kullanan ViewModel birim testi üretimi
- `.claude/skills/new-feature/SKILL.md` — Repository + ViewModel + ekran paketi iskeleti
