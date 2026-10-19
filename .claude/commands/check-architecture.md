---
description: ViFi-Android kodunu hedef mimariye (Kotlin + Compose + MVVM + Hilt) göre denetler; ihlalleri ve anti-pattern'leri kod örnekli düzeltme önerileriyle raporlar.
argument-hint: "[path]  (ör. app/src/main/java/com/salticusteam/vifi/feature/examlist)"
---

# Command: Check Architecture

ViFi Android'in **hedef mimari** konvansiyonlarına (Kotlin + Jetpack Compose + MVVM + Hilt) göre
kodu denetler, ihlalleri ve anti-pattern'leri raporlar, kod örnekli düzeltme önerir.

> **Legacy not:** Bu komut **yeni/ileriye dönük** Kotlin/Compose kodunu hedef mimariye göre denetler.
> Mevcut v2.5 Java + Activity/Fragment ekranları ayrı bir dünyadır; onları "ihlal" diye raporlama,
> **göç adayı** olarak işaretle ve yol haritasındaki kalemine bağla (`ANDROID-PARITY-ROADMAP.md` /
> `ANDROID-PARITY-DETAIL.md`). Kanonik mimari kuralların tümü `.claude/rules/architecture.md`
> içindedir; bu komut o kuralları çalıştırılabilir bir denetime çevirir.

## Usage

```
/check-architecture [path]
```

## Arguments

- `path`: Denetlenecek dosya ya da klasör yolu (opsiyonel; verilmezse mevcut modül/değişen dosyalar).

## What This Command Does

1. Verilen yoldaki Kotlin (`*.kt`) dosyalarını (ve ilgili `strings.xml`) tarar.
2. Her dosyayı ViFi Android hedef mimari kurallarına göre analiz eder.
3. İhlalleri ve anti-pattern'leri sınıflandırır (kritik / uyarı).
4. Her bulguya ViFi alan modelinden (Archive/Exam/University/Faculty/Department/Lesson,
   `com.salticusteam.vifi`) derlenebilir kod örnekli düzeltme önerir.
5. Eski Java/Activity/Fragment dosyalarını ihlal değil, **göç adayı** olarak ayrı listeler.

## Checks Performed

### Composable Ekran Checks (stateful screen + stateless content)

iOS'un `LayoutingViewController` + `LayoutableView` ikilisinin karşılığı: **state'li ekran Composable'ı**
(`XScreen`) + **state'siz içerik Composable'ı** (`XContent`). Kanonik desen: `.claude/rules/viewmodel-rule.md`
→ "Compose Ekran Entegrasyonu".

- [ ] Ekran **state'li + state'siz** ikiliye ayrılmış (`ExamListScreen` + `ExamListContent`); içerik Composable'ı yalnız `uiState` + lambda alır (preview/test edilebilir)
- [ ] ViewModel `hiltViewModel()` ile alınır (elle `new`/factory YOK)
- [ ] `uiState` `collectAsStateWithLifecycle()` ile toplanır (`collectAsState()` değil — arka planda sızıntı)
- [ ] Tek seferlik olaylar `LaunchedEffect` içinde `viewModel.events.collect { ... }` ile dinlenir
- [ ] Navigasyon üstten lambda ile (`onOpenExam: (String) -> Unit`); `NavController` Composable'a gömülü DEĞİL
- [ ] Geri `TopAppBar` + `navigationIcon` (ikon-only, `popBackStack()`); elle/metinli geri butonu YOK
- [ ] İş mantığı / Firebase / repository çağrısı Composable'da YOK (ViewModel'de)
- [ ] Kullanıcıya görünen metin `stringResource(R.string.x)` (hardcoded string YOK)
- [ ] Renk `MaterialTheme.colorScheme` / `core/designsystem/Color.kt` (hardcoded `Color(0x...)` YOK)
- [ ] Tipografi `MaterialTheme.typography` / `Type.kt` (sistem fontu YOK)
- [ ] Etkileşimli öğelerde `Modifier.testTag("<ekran>.<öğe>")` + gerekli `semantics { contentDescription = ... }`
- [ ] Yeni ekran Fragment/XML DEĞİL (legacy istisna)
- [ ] İngilizce yorum (kullanıcı metni Türkçe ve `strings.xml`'de)

### ViewModel Checks

- [ ] `@HiltViewModel class XViewModel @Inject constructor(...)` + `androidx.lifecycle.ViewModel` (`BaseViewModel` YOK)
- [ ] UI durumu TEK `data class XUiState` + `private val _uiState = MutableStateFlow(...)` + `val uiState = _uiState.asStateFlow()`
- [ ] Tek seferlik olay `Channel`/`SharedFlow` (navigate/snackbar state'te TUTULMAZ)
- [ ] Delegate protokolü / interface callback YOK (iletişim `StateFlow` + `Channel`)
- [ ] `Context`/`Activity`/`View`/`NavController`/`Fragment` tutulmaz (`@ApplicationContext` Hilt ile istisna)
- [ ] Ham Firebase tipi (`DataSnapshot`/`DocumentSnapshot`/`Task`) UI'ya sızmaz — repository domain `data class` döndürür
- [ ] Async iş `viewModelScope.launch` içinde; IO/Firebase `Dispatchers.IO` (`withContext`) altında
- [ ] Firebase `Task` `kotlinx-coroutines-play-services` `.await()` ile beklenir (blocking `Tasks.await()` YOK)
- [ ] `isLoading` **her** dalda (başarı + hata + erken return) kapatılır — spinner askıda kalmaz
- [ ] Kullanıcı hatası `ArchiveError`/`AuthError`'ın Türkçe `userMessage`'ından `error: String?` alanına
- [ ] Veri erişimi repository interface'i arkasından (`archiveRepository.getExams(...)`); doğrudan Firebase SDK YOK
- [ ] Duplicate istek `isLoading` guard'ı ile engellenir
- [ ] State değişimi `_uiState.update { it.copy(...) }` (atomik)
- [ ] Yeni `UiState` alanı / yeni event case `// Added in <versionName> — <amaç>.` taşır

### List Item (Compose) Checks

iOS'un `LayoutableCollectionViewCell`/`LayoutableTableViewCell` karşılığı: `LazyColumn`/`LazyVerticalGrid`
`items(...)` + state'siz öğe Composable'ı. Kanonik desen: `.claude/skills/new-list-item/SKILL.md`.

- [ ] Liste `LazyColumn`/`LazyVerticalGrid` + `items(list, key = { it.id })` ile çizilir (`key` ile stabil kimlik)
- [ ] Öğe Composable'ı state'siz: yalnız domain model + lambda alır (ViewModel öğeye gömülü DEĞİL)
- [ ] Tıklama vb. etkileşim üst Composable'a lambda ile iletilir (`onExamClick: (String) -> Unit`)
- [ ] Renk `MaterialTheme.colorScheme` / tipografi `MaterialTheme.typography`
- [ ] Metin `stringResource(...)`; görsel yükleme (Coil) placeholder + `contentDescription`'lı
- [ ] `Modifier.testTag("<ekran>.item.<alan>")` + anlamlı `contentDescription`

### Repository Checks

iOS'un `APIService` enum + `NetworkManager` + `LocalStore` üçlüsünün karşılığı: her kaynak bir Kotlin
`interface` arkasında, canlı `Firebase…Repository` + `Fake…Repository`. Kanonik referans:
`.claude/rules/firebase-data.md`.

- [ ] Kaynak bir `interface` arkasında (`ArchiveRepository`, `AuthRepository`); impl `FirestoreArchiveRepository` / `FakeArchiveRepository`
- [ ] Firestore erişimi **named `vifi` DB** ile: `FirebaseFirestore.getInstance(FirebaseApp.getInstance(), "vifi")` (DEFAULT DB sessizce boş döner)
- [ ] DTO→domain map var; ham `DataSnapshot`/`DocumentSnapshot` dışarı SIZMAZ (model plain `data class`)
- [ ] `suspend` fonksiyonlar `Dispatchers.IO` altında; Firebase `Task` `.await()` ile
- [ ] Canlı dinleyici `callbackFlow { ... awaitClose { registration.remove() } }` (listener sızmaz)
- [ ] Hata domain `ArchiveError`/`AuthError`'a map edilir; `PERMISSION_DENIED` körü körüne "yetki yok" değil (App Check 403 olasılığı → `ArchiveError.AppCheckRejected`)
- [ ] Repository'de UI tipi (`Context`/Compose/`View`) YOK

### Hilt / DI Module Checks

iOS'un `static let shared` singleton + `private init()` deseninin karşılığı: Hilt modülleri +
constructor injection. Kanonik referans: `.claude/rules/architecture.md` → "DI (Hilt)".

- [ ] Bağımlılıklar `@Module @InstallIn(...)` + `@Provides`/`@Binds` ile sağlanır (manuel `Object` singleton YOK)
- [ ] Interface→impl bağlama `@Binds` (ör. `ArchiveRepository` → `FirestoreArchiveRepository`)
- [ ] Tek örnek gereken sağlayıcılar `@Singleton` (named `vifi` Firestore, App Check, auth burada)
- [ ] Sınıflar constructor injection kullanır (`@Inject constructor(...)`); global erişim/`companion object` state YOK
- [ ] `@ApplicationContext` dışında `Context` enjekte edilip saklanmaz

### General Code Quality

- [ ] Yeni ekranda Fragment/XML/`findViewById`/`notifyDataSetChanged` YOK (Compose kullan)
- [ ] Firebase SDK doğrudan UI/ViewModel'de değil, **repository arkasında**
- [ ] Yeni kodda RxJava/`LiveData`/delegate YOK (UI iletişimi `StateFlow` + `Channel`/`SharedFlow`)
- [ ] `com.android.support` / `jcenter()` yeni bağımlılık YOK (AndroidX + `mavenCentral()`)
- [ ] Yeni AdMob yüzeyi YOK (reklam parite için kaldırılıyor)
- [ ] Hardcoded string YOK → `stringResource(R.string.x)`; placeholder positional (`%1$s` / `%1$d`)
- [ ] Hardcoded renk/font YOK → `MaterialTheme.colorScheme` / `typography`, marka `Color.kt` / `Type.kt`
- [ ] Force unwrap `!!` YOK (`?.` / `?: return` / `checkNotNull` / `requireNotNull`)
- [ ] Blocking `Tasks.await()` / `.getResult()` YOK → coroutine `.await()`
- [ ] `callbackFlow` daima `awaitClose { ... }` ile listener'ı kaldırır
- [ ] `viewModelScope`/`lifecycleScope` dışında yaşayan coroutine (`GlobalScope`) YOK
- [ ] `Context` ViewModel'de tutulmaz; `Composable`'da yan etki `LaunchedEffect`/`DisposableEffect` ile doğru key'lerde
- [ ] İngilizce yorum; versioned doc comment yeni alan/case'te
- [ ] Önerilen satır uzunluğu ≈ 140 karakter, dosya ≈ 500 satır (detekt eşiği; aşılırsa böl)

## Output Format

```markdown
## Mimari Denetim Raporu

### Özet
- Taranan dosya: {count}
- İhlal: {count}
- Uyarı: {count}
- Göç adayı (legacy): {count}
- Uyumluluk skoru: {percentage}%

### Kategoriye Göre İhlaller

#### Kritik (Düzeltilmeli) 🔴
| Dosya | Satır | Sorun | Düzeltme |
|-------|-------|-------|----------|
| {file} | {line} | {issue} | {suggestion} |

#### Uyarı (Düzeltilmesi iyi olur) ⚠️
| Dosya | Satır | Sorun | Öneri |
|-------|-------|-------|-------|
| {file} | {line} | {issue} | {suggestion} |

#### Göç Adayı (legacy — ihlal değil) 🔁
| Dosya | Tür | Yol haritası kalemi |
|-------|-----|---------------------|
| {file} | Activity/Fragment/Java | {ör. ARCH-15 / BE-15} |

### Ayrıntılı Analiz

#### {filename}
**Tür**: Composable Ekran / ViewModel / Repository / List Item / Hilt Module
**Uyumluluk**: {percentage}%

Bulgular:
1. **Satır 45**: Ekran tek parça, state'li/state'siz ayrımı yok
   ```kotlin
   // ❌ Mevcut (yanlış) — tek parça, VM içeride, state ayrı değil
   @Composable
   fun ExamListScreen(viewModel: ExamListViewModel = hiltViewModel()) {
       val uiState by viewModel.uiState.collectAsState()  // lifecycle-aware değil
       Column { /* UI doğrudan burada, preview edilemez */ }
   }

   // ✅ Olması gereken — state'li ekran + state'siz içerik
   @Composable
   fun ExamListScreen(
       onOpenExam: (String) -> Unit,
       onBack: () -> Unit,
       viewModel: ExamListViewModel = hiltViewModel()
   ) {
       val uiState by viewModel.uiState.collectAsStateWithLifecycle()
       LaunchedEffect(Unit) {
           viewModel.events.collect { event ->
               when (event) {
                   is ExamListEvent.OpenExam -> onOpenExam(event.examId)
                   is ExamListEvent.ShowMessage -> { /* snackbar */ }
               }
           }
       }
       ExamListContent(uiState = uiState, onExamClick = viewModel::onExamClick, onBack = onBack)
   }
   ```

2. **Satır 78**: Navigasyon Composable'a gömülü (geri butonu elle metinli)
   ```kotlin
   // ❌ Mevcut (yanlış)
   TextButton(onClick = { navController.navigate("examDetail/$id") }) { Text("Geri") }

   // ✅ Expected — navigation hoisted as a lambda; icon-only back + popBackStack
   TopAppBar(
       title = { Text(stringResource(R.string.exam_list_title)) },
       navigationIcon = {
           IconButton(onClick = onBack) {  // onBack calls navController.popBackStack() at the caller
               Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_navigate_back))
           }
       }
   )
   ```

3. **Satır 102**: Hardcoded string (strings.xml kullanılmalı)
   ```kotlin
   // ❌ Mevcut (yanlış)
   Text(text = "Sınavlar")

   // ✅ Olması gereken
   Text(text = stringResource(R.string.exam_list_title))
   // res/values/strings.xml: <string name="exam_list_title">Sınavlar</string>
   ```

4. **Satır 135**: ViewModel'de ham Firebase tipi / delegate / Context
   ```kotlin
   // ❌ Mevcut (yanlış) — DocumentSnapshot sızıyor, Context tutuluyor, delegate var
   class ExamListViewModel(private val context: Context) : ViewModel() {
       var delegate: ExamListDelegate? = null
       fun load() {
           firestore.collection("exams").get().addOnSuccessListener { snap ->
               delegate?.didLoad(snap.documents)  // ham snapshot UI'ya gidiyor
           }
       }
   }

   // ✅ Olması gereken — repository + StateFlow, domain model, Context yok
   @HiltViewModel
   class ExamListViewModel @Inject constructor(
       private val archiveRepository: ArchiveRepository,
       savedStateHandle: SavedStateHandle
   ) : ViewModel() {
       private val lessonId: String = checkNotNull(savedStateHandle["lessonId"])
       private val _uiState = MutableStateFlow(ExamListUiState())
       val uiState: StateFlow<ExamListUiState> = _uiState.asStateFlow()

       fun load() {
           _uiState.update { it.copy(isLoading = true, error = null) }
           viewModelScope.launch {
               try {
                   val exams = withContext(Dispatchers.IO) { archiveRepository.getExams(lessonId, page = 1) }
                   _uiState.update { it.copy(isLoading = false, exams = exams) }
               } catch (e: ArchiveError) {
                   // Hata dalı da isLoading'i KAPATIR — spinner askıda kalmaz.
                   _uiState.update { it.copy(isLoading = false, error = e.userMessage) }
               }
           }
       }
   }
   ```

5. **Satır 156**: Hardcoded renk (tema kullanılmalı)
   ```kotlin
   // ❌ Mevcut (yanlış)
   Surface(color = Color(0xFFF0F2F7)) { /* ... */ }

   // ✅ Olması gereken
   Surface(color = MaterialTheme.colorScheme.background) { /* ... */ }
   // Marka rengi gerekiyorsa core/designsystem/Color.kt üzerinden tema token'ına bağla.
   ```

### Öneriler

#### Yüksek öncelik (Düzeltilmeli)
1. **State'li/state'siz Composable ayrımı**: Her ekran `XScreen` (VM + state toplama + event) + `XContent` (yalnız `uiState` + lambda).
2. **Delegate/RxJava/LiveData'yı kaldır**: UI iletişimi `StateFlow<UiState>` + `Channel`/`SharedFlow`.
3. **Firebase'i repository arkasına al**: `interface ArchiveRepository` + `Firestore…Repository`/`Fake…Repository`; ham snapshot sızdırma.
4. **ViewModel'den `Context`/`NavController`'ı çıkar**: navigasyon üstten lambda; `@ApplicationContext` dışında Context tutma.

#### Orta öncelik (Düzeltilmesi iyi olur)
1. Tüm kullanıcı metni `stringResource(R.string.x)`; placeholder positional (`%1$s`).
2. Renk/font `MaterialTheme.colorScheme` / `typography`, marka `Color.kt` / `Type.kt`.
3. `isLoading`'i başarı + hata **her** dalda kapat; `Task` için `.await()`.
4. Etkileşimli öğelere `testTag` + `contentDescription`.

#### Düşük öncelik (İyileştirme)
1. Uzun Composable'ları (>50 satır) daha küçük state'siz parçalara böl.
2. `// region`/`// endregion` ile bölümle (iOS `// MARK:` karşılığı).
3. Eski inline yorumları İngilizce'ye çek; versioned doc comment ekle.

### Pattern Göç Kılavuzu

**Fragment/Activity → Composable ekran + ViewModel:**
```kotlin
// ❌ Önce (legacy Fragment)
class ExamListFragment : Fragment() {
    override fun onViewCreated(view: View, s: Bundle?) {
        adapter = ExamAdapter()
        recyclerView.adapter = adapter
        firestore.collection("exams").get().addOnSuccessListener { adapter.submit(it.documents) }
    }
}

// ✅ Sonra — ekran Composable + ViewModel (bkz. .claude/skills/new-screen/SKILL.md)
@Composable
fun ExamListScreen(onOpenExam: (String) -> Unit, onBack: () -> Unit,
                   viewModel: ExamListViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ExamListContent(uiState = uiState, onExamClick = onOpenExam, onBack = onBack)
}
```

**Delegate → StateFlow + Channel:**
```kotlin
// ❌ Önce (delegate)
protocolOf.didSuccessLoad()        // VM, UI'ya delegate ile haber verir

// ✅ Sonra — sürekli durum StateFlow, tek seferlik olay Channel
_uiState.update { it.copy(isLoading = false, exams = exams) }   // didSuccessLoad karşılığı
_events.send(ExamListEvent.OpenExam(examId))                    // navigate karşılığı
```

**Activity içinde doğrudan Firebase → Repository:**
```kotlin
// ❌ Önce (Activity/Fragment içinde ValueEventListener, ham DataSnapshot)
database.getReference("Universitiess").addValueEventListener(object : ValueEventListener {
    override fun onDataChange(snapshot: DataSnapshot) { /* UI'yı doldur */ }
    override fun onCancelled(error: DatabaseError) {}
})

// ✅ Sonra — repository interface + callbackFlow + domain model
interface ArchiveRepository {
    fun observeExams(lessonId: String): Flow<List<Exam>>
}
override fun observeExams(lessonId: String): Flow<List<Exam>> = callbackFlow {
    val ref = firestore.collection("exams").whereEqualTo("lessonId", lessonId)
    val reg = ref.addSnapshotListener { snap, err ->
        if (err != null) { close(ArchiveError.Unknown); return@addSnapshotListener }
        trySend(snap?.documents?.mapNotNull { it.toExamOrNull() }.orEmpty())  // DTO→domain map
    }
    awaitClose { reg.remove() }  // ZORUNLU: listener'ı kaldır (sızıntı yok)
}
```

**Object/companion singleton → Hilt @Module/@Provides:**
```kotlin
// ❌ Önce (global singleton)
object ArchiveManager { fun getExams() { /* ... */ } }

// ✅ Sonra — Hilt modülü + constructor injection
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds @Singleton
    abstract fun bindArchiveRepository(impl: FirestoreArchiveRepository): ArchiveRepository
}
```
```

## Example Output

```
Kullanıcı: /check-architecture app/src/main/java/com/salticusteam/vifi/feature/examlist/

Çıktı:
## Mimari Denetim Raporu

### Özet
- Taranan dosya: 4
- İhlal: 3
- Uyarı: 7
- Göç adayı (legacy): 1
- Uyumluluk skoru: 68%

### Kategoriye Göre İhlaller

#### Kritik (Düzeltilmeli) 🔴
| Dosya | Satır | Sorun | Düzeltme |
|-------|-------|-------|----------|
| ExamListScreen.kt | 23 | Ekran tek parça, state'li/state'siz ayrımı yok | XScreen + XContent'e böl |
| ExamListViewModel.kt | 31 | ViewModel'de `Context` tutuluyor | Kaldır; navigasyon üstten lambda |
| FirestoreArchiveRepository.kt | 58 | `callbackFlow` `awaitClose` yok | Listener'ı `awaitClose`'da kaldır |

#### Uyarı (Düzeltilmesi iyi olur) ⚠️
| Dosya | Satır | Sorun | Öneri |
|-------|-------|-------|-------|
| ExamListScreen.kt | 89 | Hardcoded string "Sınavlar" | `stringResource(R.string.exam_list_title)` |
| ExamListScreen.kt | 112 | `collectAsState()` lifecycle-aware değil | `collectAsStateWithLifecycle()` |
| ExamListContent.kt | 34 | Hardcoded renk `Color(0xFF...)` | `MaterialTheme.colorScheme.*` |
| ExamListViewModel.kt | 67 | Hata dalında `isLoading` kapanmıyor | `catch`'te `isLoading = false` |
| ExamListViewModel.kt | 90 | `Task` blocking `Tasks.await()` | coroutine `.await()` |
| ExamItem.kt | 12 | Etkileşim öğesinde `testTag` yok | `Modifier.testTag("examList.item")` |
| ExamItem.kt | 20 | Görselde `contentDescription` yok | Coil `contentDescription` ekle |

#### Göç Adayı (legacy — ihlal değil) 🔁
| Dosya | Tür | Yol haritası kalemi |
|-------|-----|---------------------|
| CourseExamListActivity.java | Activity (Java) | ARCH-15 / BE-15 (RTDB→Firestore, Compose ekran) |

### Ayrıntılı Analiz

#### ExamListScreen.kt
**Tür**: Composable Ekran
**Uyumluluk**: 60%

Bulgular:
1. **Satır 23**: Tek parça ekran; `ExamListScreen` + `ExamListContent` ayrımı yok (preview/test edilemez)
2. **Satır 89**: Hardcoded string "Sınavlar" yerine `stringResource(R.string.exam_list_title)`
3. **Satır 112**: `collectAsState()` yerine `collectAsStateWithLifecycle()`
4. **Satır 134**: Geri için metinli `TextButton`; `TopAppBar` + ikon-only `navigationIcon` + `popBackStack()` olmalı

#### ExamListViewModel.kt
**Tür**: ViewModel
**Uyumluluk**: 70%

Bulgular:
1. **Satır 31**: `Context` constructor'da tutuluyor (`@ApplicationContext` dışında yasak)
2. **Satır 67**: `catch` dalında `isLoading = false` yok — spinner askıda kalır
3. **Satır 90**: `Tasks.await()` (blocking) yerine `kotlinx-coroutines-play-services` `.await()`

#### FirestoreArchiveRepository.kt
**Tür**: Repository
**Uyumluluk**: 65%

Bulgular:
1. **Satır 40**: `FirebaseFirestore.getInstance()` (DEFAULT DB) — named `vifi` DB olmalı, yoksa sessizce boş döner
2. **Satır 58**: `callbackFlow` içinde `awaitClose { registration.remove() }` yok — listener sızar
3. **Satır 73**: `DocumentSnapshot` dışarı veriliyor — DTO→domain `Exam` map edilmeli

#### ExamItem.kt
**Tür**: List Item
**Uyumluluk**: 55%

Bulgular:
1. **Satır 12**: `Modifier.testTag("examList.item.title")` yok (UI testiyle sözleşme)
2. **Satır 20**: Coil görselinde `contentDescription` yok (erişilebilirlik)
3. **Satır 28**: Tıklama lambda ile üst Composable'a iletilmiyor; öğe içinde `navController` çağrılıyor
```

---

**Ayrıca bkz.:**
- `.claude/rules/architecture.md` — Mimari kuralların kanonik kaynağı (MVVM katmanları, Hilt, paketleme, yaşam döngüsü/bellek, versioned doc comment)
- `.claude/rules/viewmodel-rule.md` — `@HiltViewModel` + `StateFlow<UiState>` + event kanalı, state'li/state'siz Composable
- `.claude/rules/firebase-data.md` — Repository + DTO→domain map, named `vifi` DB, `callbackFlow`/`.await()`, App Check 403
- `.claude/rules/ui-components.md` — Compose/Material3, `TopAppBar` + `navigationIcon`, durum görünümleri, `semantics`/`testTag`
- `.claude/rules/localization.md` — `strings.xml`, `stringResource`, positional format args, Türkçe-only
- `.claude/rules/testing.md` — Birim (JUnit + `coroutines-test` + Turbine) ve Compose UI testi kuralları
- `.claude/rules/date-handling.md` — ISO 8601 + `java.time` + merkezi helper
- `.claude/skills/new-screen/SKILL.md` — Compose ekran + ViewModel oluşturma akışı
- `.claude/skills/new-viewmodel/SKILL.md` — ViewModel oluşturma akışı
- `.claude/skills/new-feature/SKILL.md` — Tam feature modülü scaffold
- `.claude/skills/new-list-item/SKILL.md` — Liste öğesi (Compose item / RecyclerView göç)
- `.claude/skills/review-pr/SKILL.md` — PR review kontrol listesi (bu denetimin manuel karşılığı)
- `.claude/commands/scaffold-feature.md` — Uyumlu feature iskeleti üretme komutu

---
