---
description: ViFi-Android test sahteleri üretir — repository interface'lerinden elle yazılmış Fake impl (varsayılan) veya MockK kurulumu. Hedef mimari (Kotlin + Coroutines/Flow + Hilt + MVVM) kurallarına uyar.
argument-hint: "[RepositoryName | dosya_yolu]  (ör. ArchiveRepository | app/src/main/java/com/salticusteam/vifi/core/data/repository/ArchiveRepository.kt)"
---

# Command: Generate Mocks

ViFi-Android konvansiyonlarına uyan test sahtelerini bir repository `interface`'inden üretir: varsayılan olarak elle yazılmış, yeniden kullanılabilir bir `Fake...Repository` (çağrı sayımı + yapılandırılabilir sonuç), gerektiğinde ise tek seferlik doğrulama için `MockK` kurulumu.

> **Legacy not:** Bugünkü v2.5 kodu (Java, `Activity`/`Fragment`, doğrudan RTDB `Universitiess` okuması) interface arkasına alınmadığı için sahtelenemez — bu yüzden otomatik testi de yoktur. Bu komut **yeni/ileriye dönük** Kotlin + MVVM kodu içindir: ViewModel bir repository `interface`'ine bağımlıdır, test o interface'in `Fake` implementasyonunu enjekte eder. Göçteki yeri: `ANDROID-PARITY-ROADMAP.md` (Faz 1 — mimari iskelet + repository soyutlaması). Legacy ekranlar RTDB→Firestore göçüyle birlikte interface kazandıkça sahtelenebilir hale gelir.

> **iOS → Android eşlemesi (özet):** iOS'un `protocol + Service + Mock` üçlüsü Android'de `interface + Firebase...Repository + Fake...Repository`'dir. iOS'taki "delegate + manuel mock (çağrı bayrağı + closure)" deseninin karşılığı, ViewModel'in bağımlı olduğu repository interface'inin bir `Fake`'idir — delegate callback'lerini doğrulamak yerine `StateFlow<XUiState>` emisyonlarını doğrularız (bkz. `.claude/rules/testing.md`).

## Usage

```
/generate-mocks [RepositoryName | dosya_yolu]
```

## Arguments

- `RepositoryName`: Sahtelenecek repository interface'inin adı (ör. `ArchiveRepository`, `AuthRepository`, `UserPreferencesRepository`).
- `dosya_yolu`: Interface'i içeren dosyanın yolu (ör. `app/src/main/java/com/salticusteam/vifi/core/data/repository/ArchiveRepository.kt`).

## What This Command Does

1. Belirtilen repository `interface`'ini/`interface`'lerini bulur.
2. Interface gereksinimlerini analiz eder (`suspend fun`, `Flow<T>` dönen fonksiyonlar, parametreler).
3. Çağrı sayımı + yakalanan argüman + yapılandırılabilir sonuç içeren bir `Fake...Repository` implementasyonu üretir.
4. Test yardımcıları ekler (`Flow` için elle emisyon fonksiyonu, isteğe bağlı `reset()`).
5. Dosyayı `app/src/test/java/com/salticusteam/vifi/fake/` dizinine kaydeder (preview'larda da yeniden kullanılabilir).
6. Ayrıntılı desen ve karar ağacı için `.claude/skills/generate-mocks/SKILL.md`'e (repository-fake pattern) ve `.claude/rules/testing.md`'e dayanır.

## Mock Generation Rules

### 1. Repository Interface için `Fake` (iOS ViewModel Delegate mock karşılığı)

Ana desen budur. iOS'ta `MockFeedViewModelDelegate` delegate callback'lerini (`didSuccessLoad`/`didFailure`) bayrak + closure ile izlerdi; Android'de bunun yerine ViewModel'in bağımlı olduğu repository `interface`'inin sonucunu yapılandırırız ve çağrı sayımını tutarız.

```kotlin
// Kaynak interface (core/data/repository/ArchiveRepository.kt)
interface ArchiveRepository {
    suspend fun getExams(departmentId: String): List<Exam>
    fun observeExams(departmentId: String): Flow<List<Exam>>
}
```

```kotlin
// Üretilen Fake (app/src/test/java/com/salticusteam/vifi/fake/FakeArchiveRepository.kt)
package com.salticusteam.vifi.fake

import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.Exam
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

// Hand-written fake: the Android equivalent of iOS's Mock*Delegate.
class FakeArchiveRepository : ArchiveRepository {

    // --- Configurable Outcome (iOS mockResult karşılığı) ---
    var examsToReturn: List<Exam> = emptyList()
    var errorToThrow: ArchiveError? = null

    // --- Call Tracking (iOS "*Called" / "*CallCount" karşılığı) ---
    var getExamsCallCount = 0
        private set

    // --- Captured Arguments (iOS capturedArgument karşılığı) ---
    var lastRequestedDepartmentId: String? = null
        private set

    override suspend fun getExams(departmentId: String): List<Exam> {
        getExamsCallCount++
        lastRequestedDepartmentId = departmentId
        errorToThrow?.let { throw it }
        return examsToReturn
    }

    // --- Flow helpers: drive emissions manually from the test body ---
    private val examsFlow = MutableSharedFlow<List<Exam>>(replay = 1)
    override fun observeExams(departmentId: String): Flow<List<Exam>> = examsFlow

    suspend fun emitExams(exams: List<Exam>) = examsFlow.emit(exams)

    // --- Optional reset: only for a shared Fake; prefer a fresh instance per @Before ---
    fun reset() {
        examsToReturn = emptyList()
        errorToThrow = null
        getExamsCallCount = 0
        lastRequestedDepartmentId = null
    }
}
```

**Testte kullanım (iOS `waitForExpectations` + closure yerine Turbine + `runTest`):**
```kotlin
@Test
fun `loadExams when success updates uiState with exams`() = runTest {
    // Given
    fakeRepository.examsToReturn = listOf(
        Exam(id = "e1", title = "Fizik I Vize", year = 2023, fileUrl = "https://x/e1.pdf"),
    )

    // When / Then — observe the state transition via Turbine (no sleep, no expectation)
    sut.uiState.test {
        assertEquals(ExamListUiState(), awaitItem())   // initial
        sut.loadExams(departmentId = "dep-1")
        assertTrue(awaitItem().isLoading)              // loading = true
        val loaded = awaitItem()                       // success
        assertFalse(loaded.isLoading)
        assertEquals(1, loaded.exams.size)
        cancelAndIgnoreRemainingEvents()
    }

    assertEquals(1, fakeRepository.getExamsCallCount)
    assertEquals("dep-1", fakeRepository.lastRequestedDepartmentId)
}
```

### 2. Dönüş değerli / çok fonksiyonlu Repository için `Fake` (iOS Manager Protocol karşılığı)

iOS'taki `NetworkManagerProtocol` (completion handler + `Result<T, AFError>` + senkron `Bool` dönen fonksiyon) karşılığı, Android'de `suspend` sonuç dönen ve/veya senkron bir bayrak dönen bir repository'dir. Completion handler + `Result` yerine `suspend` + exception (coroutine) kullanılır; hata, ham Firebase tipi değil `sealed class AuthError`'dır.

```kotlin
// Kaynak interface (core/data/repository/AuthRepository.kt)
interface AuthRepository {
    suspend fun sendVerificationCode(phoneNumber: String): VerificationId   // +90 Phone Auth
    suspend fun verifyCode(id: VerificationId, code: String): AuthUser
    fun isSignedIn(): Boolean
}
```

```kotlin
// Üretilen Fake (app/src/test/java/com/salticusteam/vifi/fake/FakeAuthRepository.kt)
package com.salticusteam.vifi.fake

import com.salticusteam.vifi.core.data.repository.AuthRepository
import com.salticusteam.vifi.core.model.AuthError
import com.salticusteam.vifi.core.model.AuthUser
import com.salticusteam.vifi.core.model.VerificationId

class FakeAuthRepository : AuthRepository {

    // --- Configurable Outcome ---
    var verificationIdToReturn: VerificationId = VerificationId("vid-test")
    var userToReturn: AuthUser = AuthUser(id = "u1", phone = "+905551112233")
    var errorToThrow: AuthError? = null
    var signedIn: Boolean = false            // synchronous flag (iOS mockIsNetworkReachable karşılığı)

    // --- Call Tracking + Captured Arguments ---
    var sendVerificationCodeCallCount = 0
        private set
    var lastPhoneNumber: String? = null
        private set
    var lastSubmittedCode: String? = null
        private set

    override suspend fun sendVerificationCode(phoneNumber: String): VerificationId {
        sendVerificationCodeCallCount++
        lastPhoneNumber = phoneNumber
        errorToThrow?.let { throw it }       // suspend + throw, iOS completion(.failure) karşılığı
        return verificationIdToReturn
    }

    override suspend fun verifyCode(id: VerificationId, code: String): AuthUser {
        lastSubmittedCode = code
        errorToThrow?.let { throw it }
        signedIn = true
        return userToReturn
    }

    override fun isSignedIn(): Boolean = signedIn
}
```

> Not: iOS'taki `DispatchQueue.main.asyncAfter` ile sahte gecikme **yoktur**. ViewModel IO'ya geçmez (IO repository'nin içindedir), test `StandardTestDispatcher` + `runTest`/Turbine ile deterministik ilerler (bkz. `.claude/rules/testing.md` → MainDispatcherRule).

### 3. Yerel kalıcılık Repository için `Fake` (iOS LocalStore mock karşılığı)

iOS'taki `MockLocalStore` (bellek-içi `storage` sözlüğü) karşılığı, DataStore tabanlı bir `UserPreferencesRepository` interface'inin bellek-içi `Fake`'idir. Oturum/kullanıcı tercihi gibi yerel durumu `Flow` ile yayınlar.

```kotlin
// Kaynak interface (core/data/repository/UserPreferencesRepository.kt)
interface UserPreferencesRepository {
    fun observeUser(): Flow<User?>
    suspend fun saveUser(user: User)
    suspend fun clearUser()
}
```

```kotlin
// Üretilen Fake (app/src/test/java/com/salticusteam/vifi/fake/FakeUserPreferencesRepository.kt)
package com.salticusteam.vifi.fake

import com.salticusteam.vifi.core.data.repository.UserPreferencesRepository
import com.salticusteam.vifi.core.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeUserPreferencesRepository : UserPreferencesRepository {

    // In-memory backing store (iOS storage dictionary karşılığı).
    private val userState = MutableStateFlow<User?>(null)

    // --- Call Tracking ---
    var saveCallCount = 0
        private set
    var clearCallCount = 0
        private set

    override fun observeUser(): Flow<User?> = userState.asStateFlow()

    override suspend fun saveUser(user: User) {
        saveCallCount++
        userState.value = user
    }

    override suspend fun clearUser() {
        clearCallCount++
        userState.value = null
    }
}
```

## Mock File Organization

iOS `eSimIOTests/Mocks/` dizininin karşılığı, test kaynak setindeki `fake/` paketidir. Fake'ler tek bir yerde toplanır ve tüm ViewModel testleri (ve istenirse Compose preview'ları) paylaşır.

```
app/src/test/java/com/salticusteam/vifi/
├── fake/
│   ├── FakeArchiveRepository.kt
│   ├── FakeAuthRepository.kt
│   └── FakeUserPreferencesRepository.kt
└── testing/
    └── MainDispatcherRule.kt     # Dispatchers.Main → TestDispatcher (bkz. testing.md)
```

**Adlandırma kuralı:**
- Dosya: `Fake<RepositoryName>.kt`
- Sınıf: `Fake<RepositoryName>` (örn. `FakeArchiveRepository`)

> İstisna — Hilt UI/entegrasyon testi için Fake'in DI'a enjekte edilmesi gerekiyorsa, canlı `RepositoryModule`'ü değiştiren bir test modülü `app/src/androidTest/.../di/` altına konur (`@TestInstallIn(components = [SingletonComponent::class], replaces = [RepositoryModule::class])`). Çoğu ViewModel testi Fake'i doğrudan constructor'a verir, Hilt'e gerek kalmaz. Hilt binding detayı: `.claude/rules/firebase-data.md` (Hilt Sağlayıcıları).

## Mock Generation Template

Yeni bir repository interface'inden `Fake` üretirken kullanılan iskelet. `<Repository>`, `<method>`, `<Arg>`, `<Return>` ve `<Item>` yer tutucularını interface'e göre doldur.

```kotlin
package com.salticusteam.vifi.fake

import com.salticusteam.vifi.core.data.repository.<Repository>
import com.salticusteam.vifi.core.model.<Item>
import com.salticusteam.vifi.core.model.<Error>           // ArchiveError / AuthError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

// Generated fake for <Repository>. Default test double (prefer over MockK).
class Fake<Repository> : <Repository> {

    // --- Configurable Outcome (per test) ---
    var <item>ToReturn: List<<Item>> = emptyList()
    var errorToThrow: <Error>? = null

    // --- Call Tracking ---
    var <method>CallCount = 0
        private set

    // --- Captured Arguments ---
    var lastRequested<Arg>: String? = null
        private set

    override suspend fun <method>(<arg>: String): List<<Item>> {
        <method>CallCount++
        lastRequested<Arg> = <arg>
        errorToThrow?.let { throw it }
        return <item>ToReturn
    }

    // --- Flow-returning members: emit manually from the test body ---
    private val <item>Flow = MutableSharedFlow<List<<Item>>>(replay = 1)
    override fun observe<Item>s(<arg>: String): Flow<List<<Item>>> = <item>Flow
    suspend fun emit<Item>s(items: List<<Item>>) = <item>Flow.emit(items)

    // --- Optional reset (only if the Fake is shared between tests) ---
    fun reset() {
        <item>ToReturn = emptyList()
        errorToThrow = null
        <method>CallCount = 0
        lastRequested<Arg> = null
    }
}
```

## Output Example

```
User: /generate-mocks ArchiveRepository

Output:
✅ Üretildi: app/src/test/java/com/salticusteam/vifi/fake/FakeArchiveRepository.kt

İçerik Önizlemesi:
class FakeArchiveRepository : ArchiveRepository {

    var examsToReturn: List<Exam> = emptyList()
    var errorToThrow: ArchiveError? = null

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

    private val examsFlow = MutableSharedFlow<List<Exam>>(replay = 1)
    override fun observeExams(departmentId: String): Flow<List<Exam>> = examsFlow
    suspend fun emitExams(exams: List<Exam>) = examsFlow.emit(exams)
}

Kullanım Örneği:
@Test
fun `loadExams when offline error sets userMessage`() = runTest {
    fakeRepository.errorToThrow = ArchiveError.Offline

    sut.loadExams(departmentId = "dep-1")
    advanceUntilIdle() // StandardTestDispatcher: let the launched coroutine run before reading state

    val state = sut.uiState.value
    assertTrue(state.exams.isEmpty())
    assertEquals(ArchiveError.Offline.userMessage, state.error)   // assert via the type, not a raw Turkish literal
}
```

## Batch Generation

Bir dosyadaki tüm interface'ler için Fake üret:

```
User: /generate-mocks app/src/main/java/com/salticusteam/vifi/core/data/repository/ArchiveRepository.kt

Output:
Dosyada 1 interface bulundu:
- ArchiveRepository

✅ Üretildi: app/src/test/java/com/salticusteam/vifi/fake/FakeArchiveRepository.kt
```

Bir paketteki tüm repository'ler için Fake üret:

```
User: /generate-mocks app/src/main/java/com/salticusteam/vifi/core/data/repository/

Output:
2 interface bulundu:
- ArchiveRepository
- AuthRepository

✅ Üretildi:
   - app/src/test/java/com/salticusteam/vifi/fake/FakeArchiveRepository.kt
   - app/src/test/java/com/salticusteam/vifi/fake/FakeAuthRepository.kt
```

## Mock Best Practices

### 1. Çağrıları say
```kotlin
var getExamsCallCount = 0
    private set
```

### 2. Argümanları yakala
```kotlin
var lastRequestedDepartmentId: String? = null
    private set
```

### 3. Sonucu yapılandırılabilir kıl (başarı vs. hata)
```kotlin
var examsToReturn: List<Exam> = emptyList()
var errorToThrow: ArchiveError? = null   // set edilirse suspend fonksiyon throw eder
```

### 4. `Flow` için elle emisyon
```kotlin
private val examsFlow = MutableSharedFlow<List<Exam>>(replay = 1)
suspend fun emitExams(exams: List<Exam>) = examsFlow.emit(exams)
```
`callbackFlow`/`observe...` dönen üyeler için test gövdesinden `emit...(...)` ile emisyonu sürer; Turbine ile `awaitItem()` doğrular.

### 5. Her testte taze Fake (iOS `reset()`/`tearDown` karşılığı)
Varsayılan: her testte `@Before` içinde yeni bir Fake kur — paylaşılan durum yok. `reset()` yalnızca Fake zorunlu olarak paylaşıldığında eklenir.
```kotlin
@Before fun setUp() { fakeRepository = FakeArchiveRepository(); sut = ExamListViewModel(fakeRepository) }
```

### 6. Varsayılan dönüş değerleri
```kotlin
var signedIn: Boolean = false
override fun isSignedIn(): Boolean = signedIn
```

### 7. Tek seferlik doğrulama için MockK (Fake aşırı geldiğinde)
Yeniden kullanılmayacak, yalnızca davranışı doğrulanacak bir bağımlılık için elle Fake yerine `MockK` tercih edilebilir. `suspend` fonksiyonlarda `coEvery`/`coVerify` kullanılır:
```kotlin
val repository = mockk<ArchiveRepository>()
coEvery { repository.getExams("dep-1") } returns listOf(sampleExam)
// ...
coVerify(exactly = 1) { repository.getExams("dep-1") }
```
Kural: **Önce `Fake`, gerektiğinde `MockK`.** (bkz. `.claude/rules/testing.md`)

## Integration with Tests

iOS'taki `XCTestCase` + `setUp`/`tearDown` + delegate mock kalıbının karşılığı: JUnit4 sınıfı + `@Before`, `MainDispatcherRule` ve constructor'a enjekte edilen `Fake`.

```kotlin
package com.salticusteam.vifi.feature.examlist

import com.salticusteam.vifi.fake.FakeArchiveRepository
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ExamListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()   // Dispatchers.Main → TestDispatcher

    private lateinit var fakeRepository: FakeArchiveRepository
    private lateinit var sut: ExamListViewModel     // System Under Test

    @Before
    fun setUp() {
        fakeRepository = FakeArchiveRepository()     // fresh fake per test (no shared state)
        sut = ExamListViewModel(fakeRepository)
    }

    @Test
    fun `loadExams when success exposes exams and records call`() = runTest {
        fakeRepository.examsToReturn = listOf(
            Exam(id = "e1", title = "Kalkülüs Final", year = 2024, fileUrl = "https://x/e1.pdf"),
        )

        sut.loadExams(departmentId = "math-101")

        val state = sut.uiState.value
        assertEquals(1, state.exams.size)
        assertEquals(1, fakeRepository.getExamsCallCount)
        assertEquals("math-101", fakeRepository.lastRequestedDepartmentId)
    }
}
```

Testleri çalıştır:
```bash
./gradlew :app:testDebugUnitTest          # birim (JUnit + Turbine + Fake)
```

## Related Commands
- `/scaffold-feature` — Fake'i de içeren tam feature iskeletini üretir (`.claude/commands/scaffold-feature.md`).
- `/check-architecture` — Repository interface + Fake sözleşmesini doğrular (`.claude/commands/check-architecture.md`).
- `/document-module` — Repository interface kontratını belgeler (`.claude/commands/document-module.md`).

## Related Skills
- `.claude/skills/generate-mocks/SKILL.md` — Ayrıntılı Fake/MockK desenleri ve repository-fake karar ağacı (bu komutun dayandığı skill).
- `.claude/skills/generate-unit-tests/SKILL.md` — Üretilen Fake'lerle ViewModel/mapper birim testi yazımı.
- `.claude/skills/new-feature/SKILL.md` — Fake ile birlikte feature + repository üretimi.

## Related Rules
- `.claude/rules/testing.md` — Fake repository deseni, MainDispatcherRule, Turbine, MockK, test organizasyonu (kanonik kaynak).
- `.claude/rules/firebase-data.md` — Repository `interface`'leri, `Firebase...Repository` canlı impl, Hilt binding, `sealed class ArchiveError`/`AuthError`.
- `.claude/rules/architecture.md` — MVVM + Hilt + repository soyutlaması genel bakış.

---
