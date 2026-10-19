# ViFi Android - Firebase Data Access Rules

> **Applies to:** Repositories, Data Sources, Models (domain + DTO)
> **Globs:** `["**/core/data/repository/**/*.kt", "**/*Repository.kt", "**/core/model/**/*.kt", "**/*Dto.kt"]`

> **Bağlam:** ViFi, üniversite sınav arşivi uygulaması. Kaynak iOS kuralı bir REST/HTTP istemci katmanını anlatır; ViFi-Android **REST kullanmaz** — her iki istemci de veriyi **ortak `vifi-831a8` Firebase projesinden** okur (Firestore + RTDB + Storage + Cloud Functions). Bu yüzden kaynağın "Networking" kuralı burada **"Firebase Data Access"** olarak yeniden çerçevelenir: HTTP katmanı **yoktur**; yerine **Firebase veri erişimi** (Repository arayüzü + coroutines) gelir.
>
> **Legacy not:** Mevcut v2.5, RTDB `Universitiess` ağacını doğrudan `Activity`/`Fragment` içinden Java `ValueEventListener` ile okur ve `com.google.firebase.database.*` tiplerini UI'ya kadar sızdırır. HEDEF mimari bu bölümde anlatılır; göçün adımları için bkz. yol haritası (`ANDROID-PARITY-ROADMAP.md` → `ARCH-15` / `BE-15`: RTDB→Firestore cutover).

> **Named 'vifi' Firestore DB Kuralı (Zorunlu):** Firestore erişimi **mutlaka** `FirebaseFirestore.getInstance(FirebaseApp.getInstance(), "vifi")` ile **isimli `vifi` veritabanına** yapılır. `FirebaseFirestore.getInstance()` (DEFAULT DB) sessizce **boş** sonuç döner — hata fırlatmaz, `catalogNodes`/`exams` koleksiyonları orada yoktur. Bu, yeni bir Firebase çağrısı eklerken en sık yapılan sessiz hatadır. Tek instance'ı Hilt ile sağla (`@Singleton`), çağrı yerinde elle oluşturma. Kanonik referans: `core/data/firebase/FirebaseModule.kt` → `provideFirestore()`.

> **App Check 403 Kuralı (Zorunlu — named DB kuralının kardeşi):**
> Ortak proje için App Check (Play Integrity) enforce **AÇIK**'tır. Cihaz/emülatör geçerli bir App Check token'ı üretemezse Storage/RTDB/Functions çağrıları **403 / `PERMISSION_DENIED`** döner; Firestore'da bu `FirebaseFirestoreException.Code.PERMISSION_DENIED` olarak görünür ve **güvenlik kuralı reddiyle aynı koda düşer**. Bu yüzden `PERMISSION_DENIED`'ı körü körüne "yetki yok" diye gösterme; App Check olasılığını da hesaba kat (bkz. `ArchiveError.AppCheckRejected`).
>
> - **Release:** `PlayIntegrityAppCheckProviderFactory` kurulur; cihazdaki APK **Play-imzalı** SHA-256 ile Console'da kayıtlı olmalı.
> - **Debug/emülatör:** `DebugAppCheckProviderFactory` kurulur ve Logcat'e basılan **debug token** Firebase Console > App Check'e eklenmelidir; eklenmezse okuma/indirme 403 döner.
> - App Check kurulumu veri katmanının değil, uygulama başlatmanın işidir (bkz. yol haritası `ARCH-11` / `APPCHECK-01`); ama 403'ü `ArchiveError`'a map etmek bu kuralın parçasıdır.
>
> ```kotlin
> // App init (Application.onCreate) — not the repository, but this is the source of 403.
> val factory = if (BuildConfig.DEBUG) {
>     DebugAppCheckProviderFactory.getInstance()
> } else {
>     PlayIntegrityAppCheckProviderFactory.getInstance()
> }
> FirebaseAppCheck.getInstance().installAppCheckProviderFactory(factory)
> ```

## Architecture Overview

```
Composable (UI)
    ↓  collectAsStateWithLifecycle()
ViewModel  (viewModelScope + StateFlow<UiState>)
    ↓
ArchiveRepository  (Kotlin interface)
    ↓  Dispatchers.IO
FirestoreArchiveRepository  (live impl)        FakeArchiveRepository (preview/test)
    ↓  Task.await() / callbackFlow
Firebase SDK  (Firestore 'vifi' · RTDB · Storage · Functions)
```

**Repository pattern ZORUNLU.** Kaynak iOS kuralı merkezi bir servis+istemci katmanı kullanıp Repository desenini reddeder; **Android bunun tersidir.** Her veri kaynağı bir Kotlin `interface` arkasına konur (`ArchiveRepository`, `AuthRepository`). Bu, iOS'un `protocol + Service + Mock` üçlüsünün Android karşılığıdır: `interface + Firebase...Repository + Fake...Repository`.

> **Repository ham Firebase tipi SIZDIRMAZ.** `DocumentSnapshot`, `DataSnapshot`, `QuerySnapshot`, `StorageReference` gibi tipler repository/parser sınırının **içinde** kalır; dışarıya yalnızca plain `data class` domain modelleri çıkar. UI veya ViewModel'de `com.google.firebase.*` import'u görürsen sınır ihlali vardır.

## Repository Pattern

### Repository Arayüzü Tanımlama

Her veri kaynağı önce bir `interface` olarak tanımlanır (iOS `ServiceProtocols/AuthServicing` karşılığı):

```kotlin
// core/data/repository/ArchiveRepository.kt
package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.CatalogNode
import com.salticusteam.vifi.core.model.Exam
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Read surface of the university exam archive. The live implementation reads from
 * the Firestore 'vifi' DB; [FakeArchiveRepository] is used for test/preview.
 */
interface ArchiveRepository {

    /** One-shot read: published child nodes of the given catalog node. */
    suspend fun getCatalogNodes(parentId: String?): List<CatalogNode>

    /** One-shot read: published exams of a lesson. */
    suspend fun getExams(lessonId: String): List<Exam>

    /** One-shot read: a single exam (with its file list for the viewer). */
    suspend fun getExam(examId: String): Exam

    /** Live listening: emits whenever catalog nodes change (callbackFlow). */
    fun observeCatalogNodes(parentId: String?): Flow<List<CatalogNode>>

    /** Downloads an exam file from Storage to a local file. */
    suspend fun downloadExamFile(storagePath: String, destination: File)
}
```

### Domain Model (UI'nın gördüğü tek şey)

Domain modelleri plain `data class`'tır; Firebase'den bağımsızdır, test edilebilir:

```kotlin
// core/model/CatalogNode.kt
package com.salticusteam.vifi.core.model

/**
 * A node of the archive catalog tree (university → faculty → department → lesson).
 * iOS 'ArchivePath' equivalent: canonical slug id + separate displayName.
 */
data class CatalogNode(
    val id: String,                 // canonical slug, e.g. "itu-bilgisayar-muh"
    val parentId: String?,          // null == root (university level)
    val type: CatalogNodeType,
    val displayName: String,        // display name shown to the user, e.g. "Bilgisayar Mühendisliği"
    val status: String              // only "published" is read
)

enum class CatalogNodeType {
    UNIVERSITY, FACULTY, DEPARTMENT, LESSON;

    companion object {
        fun fromRaw(raw: String?): CatalogNodeType? = when (raw) {
            "university" -> UNIVERSITY
            "faculty" -> FACULTY
            "department" -> DEPARTMENT
            "lesson" -> LESSON
            else -> null
        }
    }
}
```

```kotlin
// core/model/Exam.kt
package com.salticusteam.vifi.core.model

/** A single exam record (Firestore 'exams' collection). */
data class Exam(
    val id: String,
    val lessonId: String,
    val title: String,
    val year: Int?,
    val term: String?,
    val kind: ExamKind,                 // JPG | PDF — viewer selection depends on this
    // Added in 3.1.0 — locked exams + multiple files, for iOS parity.
    val files: List<String>,            // Storage path list, ordered by 'order'
    val isLocked: Boolean               // true == coin lock (needs unlockExam)
)

enum class ExamKind { JPG, PDF }
```

### DTO → Domain Map

Ham Firestore şekli ayrı bir DTO'da tutulur (tüm alanlar nullable; Firestore `toObject` için `data class` + boş ctor). `DocumentSnapshot` burada tüketilir, dışarı çıkmaz:

```kotlin
// core/data/firebase/dto/CatalogNodeDto.kt
package com.salticusteam.vifi.core.data.firebase.dto

import com.google.firebase.firestore.DocumentSnapshot
import com.salticusteam.vifi.core.model.CatalogNode
import com.salticusteam.vifi.core.model.CatalogNodeType

/** Raw shape of a Firestore 'catalogNodes' document. Fields nullable (missing-data tolerance). */
data class CatalogNodeDto(
    val parentId: String? = null,
    val type: String? = null,
    val displayName: String? = null,
    val status: String? = null
)

/** DTO → domain. A document with a missing/invalid required field is dropped (via mapNotNull). */
fun DocumentSnapshot.toCatalogNode(): CatalogNode? {
    val dto = toObject(CatalogNodeDto::class.java) ?: return null
    val type = CatalogNodeType.fromRaw(dto.type) ?: return null
    val displayName = dto.displayName?.takeIf { it.isNotBlank() } ?: return null
    return CatalogNode(
        id = id,                       // document id = canonical slug
        parentId = dto.parentId,
        type = type,
        displayName = displayName,
        status = dto.status.orEmpty()
    )
}
```

> **Tarih alanları:** Firestore `Timestamp`/RTDB epoch değerleri DTO'da ham (`com.google.firebase.Timestamp` ya da `Long`) tutulur ve domain'e map ederken dönüştürülür. `Date`/`Timestamp`'i UI'ya sızdırma, formatlamayı Composable içinde yapma — biçimlendirme kuralı için bkz. `date-handling.md`.

## Repository Kullanımı

### One-Shot Read — `Task.await()`

Firebase `Task`'ları `kotlinx-coroutines-play-services` `.await()` ile suspend fonksiyona çevrilir; IO `Dispatchers.IO`'da koşar. Callback hell yok, blocking yok:

```kotlin
// core/data/repository/FirestoreArchiveRepository.kt
package com.salticusteam.vifi.core.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.salticusteam.vifi.core.data.firebase.dto.toCatalogNode
import com.salticusteam.vifi.core.model.CatalogNode
import com.salticusteam.vifi.core.util.FirebaseConstants
import com.salticusteam.vifi.core.util.toArchiveError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FirestoreArchiveRepository @Inject constructor(
    private val firestore: FirebaseFirestore          // Hilt injects the named 'vifi' instance
) : ArchiveRepository {

    override suspend fun getCatalogNodes(parentId: String?): List<CatalogNode> =
        withContext(Dispatchers.IO) {
            try {
                val snapshot = firestore.collection(FirebaseConstants.COLLECTION_CATALOG_NODES)
                    .whereEqualTo("parentId", parentId)
                    .whereEqualTo("status", "published")
                    .get(Source.DEFAULT)              // cache → server (offline persistence on)
                    .await()
                snapshot.documents.mapNotNull { it.toCatalogNode() }
            } catch (e: Exception) {
                throw e.toArchiveError()               // raw Firebase error never reaches the UI
            }
        }

    // ... getExams / getExam follow the same pattern
}
```

> **`get(Source.DEFAULT)`** offline persistence açıkken önce cache sonra server okur; iOS'un offline keepSynced yaklaşımının karşılığıdır. Kesin tazelik gereken yerde `Source.SERVER`, yalnız cache için `Source.CACHE` kullan.

### Canlı Dinleme — `callbackFlow { awaitClose { ... } }`

RTDB/Firestore listener'ları **mutlaka** `callbackFlow` içine sarılır ve `awaitClose` içinde listener kaldırılır. Bu, iOS retain-cycle / `[weak self]` kaygısının Android karşılığıdır: `awaitClose` olmazsa listener sızar ve ekran kapanınca bile çalışmaya devam eder.

```kotlin
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

override fun observeCatalogNodes(parentId: String?): Flow<List<CatalogNode>> = callbackFlow {
    val query = firestore.collection(FirebaseConstants.COLLECTION_CATALOG_NODES)
        .whereEqualTo("parentId", parentId)
        .whereEqualTo("status", "published")

    val registration = query.addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(error.toArchiveError())              // close the Flow with a typed error
            return@addSnapshotListener
        }
        val nodes = snapshot?.documents?.mapNotNull { it.toCatalogNode() }.orEmpty()
        trySend(nodes)
    }

    awaitClose { registration.remove() }              // ⚠️ mandatory — prevents listener leaks
}.flowOn(Dispatchers.IO)
```

**Legacy not — RTDB `Universitiess`:** Bugünkü v2.5, Firestore yerine RTDB okur. Göç bitene kadar aynı `callbackFlow + awaitClose` deseni RTDB için de geçerlidir; `removeEventListener` `awaitClose`'da çağrılır:

```kotlin
// Legacy read path — migrates to Firestore via ARCH-15 / BE-15, then RTDB "Universitiess" is removed.
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

fun observeLegacyUniversities(database: FirebaseDatabase): Flow<List<CatalogNode>> = callbackFlow {
    val ref = database.getReference(FirebaseConstants.RTDB_LEGACY_UNIVERSITIES)
    val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            trySend(snapshot.children.mapNotNull { it.toLegacyUniversity() })
        }
        override fun onCancelled(error: DatabaseError) {
            close(error.toException().toArchiveError())
        }
    }
    ref.addValueEventListener(listener)
    awaitClose { ref.removeEventListener(listener) }   // ⚠️ the RTDB listener is removed too
}.flowOn(Dispatchers.IO)
```

### Storage — Dosya İndirme

```kotlin
import com.google.firebase.storage.FirebaseStorage
import java.io.File

override suspend fun downloadExamFile(storagePath: String, destination: File) =
    withContext(Dispatchers.IO) {
        try {
            storage.getReference(storagePath)          // storagePath comes from Firestore 'exams'
                .getFile(destination)
                .await()
            Unit
        } catch (e: Exception) {
            throw e.toArchiveError()                    // App Check 403 surfaces here too
        }
    }
```

> **PDF/JPG disk cache:** İndirilen dosya per-URL yerel dosyaya yazılır (`cacheDir/exam_files/<sha256>/...`), indirme IO coroutine'de yapılır. Detay akışı için bkz. yol haritası `VIEW-09`.

## Hilt ile Firebase Sağlayıcıları

iOS'taki `AppEnvironment(live/mock)` Android'de Hilt modülü + repository arayüzü ile karşılanır. Canlı binding `@Module`'de, Fake binding test modülünde verilir:

```kotlin
// core/data/firebase/FirebaseModule.kt
package com.salticusteam.vifi.core.data.firebase

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.storage.FirebaseStorage
import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.data.repository.FirestoreArchiveRepository
import com.salticusteam.vifi.core.util.FirebaseConstants
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseProvidersModule {

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {
        // ⚠️ named 'vifi' DB — NOT the DEFAULT getInstance()
        val firestore = FirebaseFirestore.getInstance(
            FirebaseApp.getInstance(),
            FirebaseConstants.FIRESTORE_DATABASE_ID
        )
        firestore.firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings { })   // offline persistence
        }
        return firestore
    }

    @Provides
    @Singleton
    fun provideStorage(): FirebaseStorage = FirebaseStorage.getInstance()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindArchiveRepository(impl: FirestoreArchiveRepository): ArchiveRepository
}
```

## Hata Yönetimi

Kaynak iOS kuralının tüm hata eşlemesini tek bir merkezî noktada toplaması gibi, Android'de **tüm Firebase hataları tek bir `sealed class`'a map edilir** ve kullanıcıya daima Türkçe `userMessage` gösterilir. UI asla ham `Exception.message` göstermez.

### `ArchiveError` Sealed Class

```kotlin
// core/model/ArchiveError.kt
package com.salticusteam.vifi.core.model

/** Typed error of the archive data layer. UI shows only [userMessage]. */
sealed class ArchiveError(open val userMessage: String) : Exception(userMessage) {

    data object Offline : ArchiveError(
        "İnternet bağlantısı yok. Lütfen bağlantınızı kontrol edip tekrar deneyin."
    )

    /** App Check rejection or security-rule rejection — both map to PERMISSION_DENIED. */
    data object PermissionDenied : ArchiveError(
        "Bu içeriğe erişim izniniz yok."
    )

    /** When an App Check token cannot be produced (403 / enforce + invalid token). */
    data object AppCheckRejected : ArchiveError(
        "Uygulama doğrulaması başarısız oldu. Lütfen uygulamayı güncelleyip tekrar deneyin."
    )

    data object NotFound : ArchiveError(
        "Aradığınız içerik bulunamadı."
    )

    data class Unknown(
        override val userMessage: String = "Beklenmeyen bir hata oluştu. Lütfen tekrar deneyin."
    ) : ArchiveError(userMessage)
}
```

> `AuthError` (telefon doğrulama, oturum, App Check) aynı desende ayrı dosyada tutulur; detay için bkz. `architecture.md`.

### Firebase Hatası → `ArchiveError` Map (tek nokta)

iOS'ta status code → mesaj eşlemesi tek bir extension'da yaşar; Android'de bunun karşılığı tek bir `Throwable.toArchiveError()` extension'ıdır. Yeni bir hata türü eklenecekse **önce burayı** güncelle, çağrı yerinde `if/when` dallanması yapma:

```kotlin
// core/util/ArchiveErrorMapper.kt
package com.salticusteam.vifi.core.util

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import com.salticusteam.vifi.core.model.ArchiveError

fun Throwable.toArchiveError(): ArchiveError = when (this) {
    is ArchiveError -> this                             // already mapped, pass through

    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.UNAVAILABLE -> ArchiveError.Offline
        // ⚠️ both App Check and rule rejection land here — PermissionDenied when indistinguishable
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> ArchiveError.PermissionDenied
        FirebaseFirestoreException.Code.NOT_FOUND -> ArchiveError.NotFound
        else -> ArchiveError.Unknown()
    }

    is StorageException -> when (errorCode) {
        StorageException.ERROR_NOT_AUTHENTICATED,
        StorageException.ERROR_NOT_AUTHORIZED -> ArchiveError.PermissionDenied   // usually App Check 403
        StorageException.ERROR_OBJECT_NOT_FOUND -> ArchiveError.NotFound
        else -> ArchiveError.Unknown()
    }

    else -> ArchiveError.Unknown()
}
```

**Neden tek `toArchiveError()`?**
- Ham `FirebaseFirestoreException.message` / `StorageException.message` lokalizasyonsuz, teknik ve İngilizce'dir; kullanıcı için anlamsızdır.
- App Check 403, offline ve kural reddi gibi durumlar tek noktada ayrılır — her repository'de tekrar yazılmaz.
- Backend davranışı (yeni kural, yeni Functions hatası) değişince yalnız bu dosya güncellenir; çağrı tarafı dokunmaz.

### ViewModel'de Hata → `UiState.error`

ViewModel `error.userMessage`'ı UiState'e yazar; Composable `error`'ı gösterir (`stringResource` değil, çünkü mesaj zaten Türkçe userMessage'tır — statik UI metinleri ise `strings.xml`'de, bkz. `localization.md`):

```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.model.CatalogNode
import com.salticusteam.vifi.core.util.toArchiveError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArchiveListViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArchiveListUiState())
    val uiState: StateFlow<ArchiveListUiState> = _uiState.asStateFlow()

    fun loadNodes(parentId: String?) {
        viewModelScope.launch {                           // viewModelScope → no leak
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { archiveRepository.getCatalogNodes(parentId) }
                .onSuccess { nodes ->
                    _uiState.update { it.copy(isLoading = false, nodes = nodes) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.toArchiveError().userMessage) }
                }
        }
    }
}

/** Single, immutable UI state (iOS Loadable<Value> equivalent — data class + fields pattern). */
data class ArchiveListUiState(
    val isLoading: Boolean = false,
    val nodes: List<CatalogNode> = emptyList(),
    val error: String? = null
)
```

> ViewModel + UiState kalıbının tam kuralı için bkz. `viewmodel-rule.md`. Yeni ekran iskeletini `skills/new-screen/` ve `skills/new-viewmodel/` üretir.

## Fake Repository (Preview / Test)

Her repository arayüzünün bir `Fake...` implementasyonu olur (iOS `Mock` karşılığı). Preview'de ve birim testinde canlı Firebase yerine bu kullanılır — gerçek ağ yok, deterministik:

```kotlin
// core/data/repository/FakeArchiveRepository.kt (testFixtures or debug source set)
package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.CatalogNode
import com.salticusteam.vifi.core.model.CatalogNodeType
import com.salticusteam.vifi.core.model.Exam
import com.salticusteam.vifi.core.model.ExamKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.io.File

class FakeArchiveRepository(
    private val nodes: List<CatalogNode> = SAMPLE_NODES,
    private val exams: List<Exam> = SAMPLE_EXAMS,
    private val error: Throwable? = null              // for testing the error scenario
) : ArchiveRepository {

    override suspend fun getCatalogNodes(parentId: String?): List<CatalogNode> {
        error?.let { throw it }
        return nodes.filter { it.parentId == parentId }
    }

    override suspend fun getExams(lessonId: String): List<Exam> {
        error?.let { throw it }
        return exams.filter { it.lessonId == lessonId }
    }

    override suspend fun getExam(examId: String): Exam =
        exams.first { it.id == examId }

    override fun observeCatalogNodes(parentId: String?): Flow<List<CatalogNode>> =
        flowOf(nodes.filter { it.parentId == parentId })

    override suspend fun downloadExamFile(storagePath: String, destination: File) = Unit

    companion object {
        val SAMPLE_NODES = listOf(
            CatalogNode("itu", null, CatalogNodeType.UNIVERSITY, "İstanbul Teknik Üniversitesi", "published"),
            CatalogNode("itu-bilgisayar", "itu", CatalogNodeType.DEPARTMENT, "Bilgisayar Mühendisliği", "published")
        )
        val SAMPLE_EXAMS = listOf(
            Exam("e1", "itu-blg101", "BLG 101 Vize", 2025, "Güz", ExamKind.PDF, listOf("exams/e1/1.pdf"), isLocked = false)
        )
    }
}
```

> Fake üretimini otomatikleştirmek için bkz. `skills/generate-mocks/` ve `commands/generate-mocks.md`. Birim/UI testi kuralları: `testing.md`.

## Sabitler — Hardcoded URL/Değer YASAK

iOS `AppConstant` karşılığı Android'de `BuildConfig` (build-time) + bir `Constants`/`FirebaseConstants` nesnesidir. Koleksiyon adı, bölge, path prefix gibi string'ler çağrı yerine gömülmez:

```kotlin
// core/util/FirebaseConstants.kt
package com.salticusteam.vifi.core.util

object FirebaseConstants {
    const val FIRESTORE_DATABASE_ID = "vifi"            // ⚠️ named DB — not DEFAULT
    const val COLLECTION_CATALOG_NODES = "catalogNodes"
    const val COLLECTION_EXAMS = "exams"
    const val COLLECTION_SUBMISSIONS = "submissions"
    const val FUNCTIONS_REGION = "europe-west1"         // Cloud Functions: unlockExam, recordLoginDiagnostic
    const val STORAGE_PENDING_PREFIX = "pending"        // pending/<uid>/<submissionId>/...

    // Legacy — removed after the Firestore cutover (ARCH-15 / BE-15)
    const val RTDB_LEGACY_UNIVERSITIES = "Universitiess"
}
```

Ortama göre değişen değerler (ör. enforce bayrakları, log seviyesi) `build.gradle`'da `buildConfigField` ile `BuildConfig`'e verilir; kaynağa sabit yazılmaz.

## Ortak Backend — Yeni Backend Yazma

> **Güvenlik kuralları ve Cloud Functions Android'de DEĞİLDİR.** Her iki istemci de **ortak `vifi-831a8`** projesini paylaşır. Firestore/Storage/RTDB kuralları ve Cloud Functions (`unlockExam`, `recordLoginDiagnostic`, `europe-west1`) **`ViFi-iOS` deposundaki `firebase/` altında** yaşar ve platformdan bağımsızdır. Android bunları **yeniden kullanır, yeni backend yazmaz.**
>
> - Android'e özgü backend işleri yalnızca: App Check (Play Integrity) kaydı, `config/appUpdate/android` düğümü, `google-services.json` yenileme, SHA parmak izleri (bkz. `BE-01`..`BE-03`).
> - Canlı veriye yazma/silme, kural yayını (`firebase deploy`), App Check/Auth ayarı değişikliği **yalnız kullanıcı onayıyla** (bkz. `CLAUDE.md`).

Cloud Function çağrısı (callable) client tarafında da `.await()` ile tüketilir:

```kotlin
import com.google.firebase.functions.FirebaseFunctions
import com.salticusteam.vifi.core.util.FirebaseConstants

suspend fun unlockExam(examId: String): Boolean = withContext(Dispatchers.IO) {
    try {
        FirebaseFunctions.getInstance(FirebaseConstants.FUNCTIONS_REGION)
            .getHttpsCallable("unlockExam")
            .call(mapOf("examId" to examId))
            .await()
        true
    } catch (e: Exception) {
        throw e.toArchiveError()
    }
}
```

## Dosya Organizasyonu

```
app/src/main/java/com/salticusteam/vifi/
├── core/
│   ├── data/
│   │   ├── firebase/
│   │   │   ├── FirebaseModule.kt            // Hilt: named 'vifi' Firestore, Storage
│   │   │   └── dto/
│   │   │       ├── CatalogNodeDto.kt        // DTO + DocumentSnapshot → domain
│   │   │       └── ExamDto.kt
│   │   └── repository/
│   │       ├── ArchiveRepository.kt         // interface
│   │       ├── FirestoreArchiveRepository.kt// live impl
│   │       ├── FakeArchiveRepository.kt     // preview/test
│   │       ├── AuthRepository.kt
│   │       └── FirebaseAuthRepository.kt
│   ├── model/
│   │   ├── CatalogNode.kt                   // domain data class
│   │   ├── Exam.kt
│   │   └── ArchiveError.kt                  // sealed class + userMessage
│   └── util/
│       ├── FirebaseConstants.kt
│       └── ArchiveErrorMapper.kt            // Throwable.toArchiveError()
└── feature/
    └── archive/
        ├── ArchiveListScreen.kt             // Composable
        ├── ArchiveListViewModel.kt
        └── ArchiveListUiState.kt
```

## Rules

1. ✅ **ALWAYS** her veri kaynağını bir Kotlin `interface` arkasına koy; canlı `Firebase...Repository`, test/preview için `Fake...Repository`.
2. ❌ **NEVER** repository'den ham Firebase tipi (`DocumentSnapshot`/`DataSnapshot`/`QuerySnapshot`/`StorageReference`) SIZDIR — DTO→domain map et, domain plain `data class`.
3. ✅ **ALWAYS** Firebase `Task`'ı `.await()` (`kotlinx-coroutines-play-services`) ile suspend yap; RTDB/Firestore dinleyicisini `callbackFlow { ... awaitClose { listener kaldır } }` ile sar.
4. ✅ **ALWAYS** Firebase/IO işini `Dispatchers.IO`'da çalıştır (`withContext` / `flowOn`); UI thread'i bloklama.
5. ✅ **MANDATORY** Firestore için `FirebaseFirestore.getInstance(app, "vifi")` — **named 'vifi' DB**; `getInstance()` (DEFAULT) sessizce boş döner.
6. ✅ **MANDATORY** Tüm hataları tek `Throwable.toArchiveError()` ile `sealed class ArchiveError`/`AuthError`'a map et; UI yalnız `error.userMessage` (Türkçe) gösterir. Ham `Exception.message` **YASAK**.
7. ✅ **MANDATORY** App Check enforce AÇIK — release'de `PlayIntegrityAppCheckProviderFactory`, debug'ta `DebugAppCheckProviderFactory` + Console'a debug token. `PERMISSION_DENIED`/403'ü App Check olasılığıyla birlikte değerlendir.
8. ✅ **ALWAYS** koleksiyon adı/bölge/path/URL gibi değerleri `FirebaseConstants`/`BuildConfig`'ten al; çağrı yerine sabit gömme.
9. ❌ **NEVER** yeni backend (kural/Cloud Function) yaz — ortak `ViFi-iOS/firebase/`'i yeniden kullan; canlı veri/kural değişikliği yalnız kullanıcı onayıyla.
10. ❌ **NEVER** `Composable`/`Activity`/`Fragment` içinden Firebase çağır — daima ViewModel → Repository.
11. ✅ **ALWAYS** coroutine'leri `viewModelScope`'ta başlat (sızıntı yok); `callbackFlow`'da `awaitClose` ile listener'ı kaldır (iOS retain-cycle karşılığı).
12. ✅ **USE** offline için `persistentCacheSettings` + `Source.DEFAULT`; tarih alanlarını DTO→domain map'inde dönüştür (bkz. `date-handling.md`).
13. ❌ **NEVER** RxJava/callback-hell kullan; proje Coroutines + Flow kullanır. Legacy RTDB okuması dahi `callbackFlow`'a sarılır.
14. ✅ **MANDATORY** Var olan bir domain `data class`'a **yeni alan** veya enum/sealed'e **yeni case** eklerken üstüne `// Added in <versionName> — <tek satır amaç>.` yorumu ekle (versiyon: `app/build.gradle` → `versionName`). Yepyeni bir modelde tek type-level yorum yeterlidir.

## Common Patterns

### Yükleme Durumu Yönetimi

```kotlin
fun refresh(parentId: String?) {
    if (_uiState.value.isLoading) return                 // duplicate-request guard
    viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, error = null) }
        runCatching { archiveRepository.getCatalogNodes(parentId) }
            .onSuccess { nodes -> _uiState.update { it.copy(isLoading = false, nodes = nodes) } }
            .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.toArchiveError().userMessage) } }
    }
}
```

### Sayfalama (Firestore cursor)

```kotlin
// Repository
suspend fun getExamsPage(lessonId: String, after: DocumentSnapshot?, pageSize: Long): QueryResult {
    var query = firestore.collection(FirebaseConstants.COLLECTION_EXAMS)
        .whereEqualTo("lessonId", lessonId)
        .whereEqualTo("status", "published")
        .orderBy("year")
        .limit(pageSize)
    if (after != null) query = query.startAfter(after)
    val snapshot = query.get(Source.DEFAULT).await()
    // In QueryResult the last document is carried behind the domain boundary as a cursor (opaque token).
    return QueryResult(
        items = snapshot.documents.mapNotNull { it.toExam() },
        hasMore = snapshot.size().toLong() == pageSize
    )
}
```

> Not: cursor (`DocumentSnapshot`) UI'ya sızmamalı; repository içinde opaque bir sayfa token'ı olarak sakla ya da son öğenin alan değerleriyle `startAfter(...)` kullan.

### Canlı Akışı UI'da Toplama

```kotlin
// ViewModel — bind the observeCatalogNodes Flow to UiState
init {
    viewModelScope.launch {
        archiveRepository.observeCatalogNodes(parentId = null)
            .catch { e -> _uiState.update { it.copy(error = e.toArchiveError().userMessage) } }
            .collect { nodes -> _uiState.update { it.copy(isLoading = false, nodes = nodes) } }
    }
}
```

---

**See Also:**
- `architecture.md` - Proje mimarisi, Hilt DI, `AuthError`, paket düzeni
- `viewmodel-rule.md` - ViewModel + `UiState` + `StateFlow` kalıbı
- `date-handling.md` - Firestore `Timestamp`/epoch → domain tarih dönüşümü
- `testing.md` - Fake repository ile birim test (JUnit + Turbine + coroutines-test), Compose UI testi
- `localization.md` - Kullanıcıya görünen metin `strings.xml` + `stringResource`
- `skills/new-viewmodel/` - ViewModel iskeleti üretimi
- `skills/generate-mocks/` ve `commands/generate-mocks.md` - Fake repository üretimi
- `skills/new-feature/` ve `commands/scaffold-feature.md` - Repository + ViewModel + ekran paketi iskeleti
- `commands/check-architecture.md` - Sınır ihlali (ham Firebase tipi sızıntısı) taraması
