# ViFi Android - Mimari Kuralları (Architecture Rules)

> **Kapsam:** ViFi Android'deki **tüm yeni/ileriye dönük Kotlin + Jetpack Compose kodu** (`feature/**`, `core/**`). Hedef mimari: **MVVM + Compose + Hilt + Coroutines/Flow**.
>
> **Legacy not (bütünsel):** Bugünkü uygulama hâlâ v2.5 (Java, `Activity`/`Fragment`, `com.android.support`, RTDB `Universitiess`, Parse, AdMob, e-posta/şifre giriş). Bu dosya o eski kodu **tarif etmez**; iOS v3.1.0 paritesine giden **hedef mimariyi** tarif eder (bkz. repo kökü `ANDROID-PARITY-ROADMAP.md` / `ANDROID-PARITY-DETAIL.md`, özellikle `FM-*` foundation kalemleri). Her bölümde, ilgili yerde kısa bir "Legacy not"u vardır: bugünkü Java karşılığı ve göçün yol haritasındaki yeri. Eski Java/XML dosyalarına dokunurken onları olduğu gibi bırak; **yeni ekran/özellik bu kurallara göre yazılır**, dokunulan eski kod fırsat buldukça buraya taşınır.

---

## Kod Stili Kuralları (Code Style Rules)

### Üye Gruplama ve Bölümleme (Member Grouping — iOS `// MARK:` karşılığı)

> **ZORUNLU:** Kotlin'de `// MARK: -` yoktur; bir sınıfın/dosyanın üyeleri **mantıksal gruplar hâlinde, sabit bir sırayla** dizilir. Grup başlığı gerekiyorsa tek satırlık yorum (`// --- Public API ---`) kullan ya da Android Studio'nun katlanabilir `//region ... //endregion` bloğunu tercih et. Grup başlığı ile altındaki ilk bildirim arasına **boş satır koyma** (başlık bildirime yapışır); grup başlığından **önce** bir boş satır bırak (görsel ayrım).

Bir `ViewModel`'da üyeler şu sabit sırayla gruplanır — her grup yalnızca üyesi varsa yazılır:

| Sıra | Grup | İçerir |
|-----:|------|--------|
| 1 | Bağımlılıklar (constructor) | `@Inject constructor(...)` ile gelen repository/use-case'ler |
| 2 | State backing | `private val _uiState = MutableStateFlow(...)` |
| 3 | Public state | `val uiState: StateFlow<XUiState> = _uiState.asStateFlow()` |
| 4 | One-shot events | `private val _events = Channel<XEvent>()` + `val events = _events.receiveAsFlow()` |
| 5 | Private mutable alanlar | iş akışı bayrakları (örn. `private var activeJob: Job?`) |
| 6 | init / public fonksiyonlar | ekran olaylarını karşılayan `fun onX()` metotları |
| 7 | private yardımcılar | `private fun ...` |

```kotlin
// ✅ DO — groups in a fixed order; each group heading sticks to the next declaration
@HiltViewModel
class ArchiveBrowseViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow(ArchiveBrowseUiState())
    val uiState: StateFlow<ArchiveBrowseUiState> = _uiState.asStateFlow()

    // One-shot events
    private val _events = Channel<ArchiveBrowseEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Internal state
    private var loadJob: Job? = null

    // Intents
    fun onFacultySelected(faculty: Faculty) { /* ... */ }

    // Helpers
    private fun reduceError(error: ArchiveError) { /* ... */ }
}
```

**Neden:**
- iOS `// MARK:` jump-bar içindi; Android Studio'da yapıyı okunur kılan şey tutarlı üye sırası + katlanabilir `//region`'dır.
- Sabit sıra → her `ViewModel` aynı okunur; yeni bir intent fonksiyonu hep aynı yere, yeni bir private bayrak hep aynı yere düşer; diff küçük kalır.

**Legacy not:** Eski `Activity`/`Fragment`'larda bu düzen aranmaz; yalnızca yeni `ViewModel`/`@Composable`/repository dosyalarına uygulanır.

---

### Model & Enum Eklemelerine Sürümlü Doc Comment (Versioned Doc Comments)

> **ZORUNLU:** **Var olan bir domain modeline eklenen her yeni alan** (`core/model/**` içindeki `data class`) ve **var olan bir `enum class` / `sealed interface`/`sealed class`'a eklenen her yeni case/alt-tip** tek satırlık bir doc comment taşımalıdır; hangi uygulama sürümünde geldiğini kaydeder:
>
> `// Added in <versionName> — <one-line purpose>.`

```kotlin
// ✅ DO — version + one-line purpose on every addition
enum class ExamStatus {
    PUBLISHED,
    PENDING,

    // Added in 3.0.0 — hides a submission removed by moderation without deleting the record.
    REMOVED
}

data class Exam(
    val id: String,
    val title: String,
    val courseId: String,
    val kind: String,              // "pdf" | "image"
    val year: Int? = null,

    // Added in 3.0.0 — gates the PDF behind the coin economy; false means freely viewable.
    val isLocked: Boolean = false
)

// ❌ DON'T — bare addition with an unknown origin
enum class ExamStatus { PUBLISHED, PENDING, REMOVED }
data class Exam(val id: String, val isLocked: Boolean)
```

**Kurallar:**
- `<versionName>`, değişikliğin yapıldığı andaki `app/build.gradle(.kts)` içindeki `versionName`'dir — yani eklemenin ilk çıkacağı sürüm. (Bugünkü değer `4.1.0` — iOS ile aynı seri, 2026-10-24 kararı; 2026-10-29'da REL-02 için 3.1.0 → 4.0.0, 2026-10-30'da `v-4.1.0` dalında NCT-01 ile 4.0.0 → 4.1.0. Daha önce eklenen `Added in 2.5`/`Added in 3.1.0`/`Added in 4.0.0` yorumları olduğu gibi kalır.)
- Tek satır, **İngilizce**, sürüm ile amaç arasında em dash (`—`). Amaç, alanın/case'in **ne sağladığını** söyler, implementasyon detayını değil.
- Yalnızca **var olan tiplere yapılan eklemelere** uygulanır. Sıfırdan yeni bir model/enum tek bir tip-seviyesi `// Added in <versionName> — ...` yorumu alır (üye başına değil).
- Rule'dan önceki mevcut alan/case'lere geriye dönük sürüm yorumu **ekleme** — dokunma.

**Kapsam — "backend-sözleşme tipi" nedir (ve ne DEĞİLDİR):**

Bu kural, **backend'in gönderdiği/aldığı bir değeri** taşıyan enum/modelleri hedefler — yorum "bu *değeri* hangi sürüm anlıyor?" sorusuna yanıt verir. Case'leri tamamen uygulama-içi tesisat olan tiplere uygulanmaz.

| Tip | Sürümlü yorum? | Neden |
|-----|:--------------:|-------|
| Firestore/RTDB DTO → domain model alanları (`core/model/**`, `core/data/**/dto`) | ✅ Zorunlu | Alanın değeri Android ↔ Firebase sınırını geçer. |
| Backend değeri taşıyan enum (`ExamStatus = "PUBLISHED"`, `SubmissionState`, bildirim tipi, popup/redirect tipi) | ✅ Zorunlu | Case'in kendisi backend'in yaydığı bir sözleşme değeridir. |
| Navigation route tipleri (`sealed interface ViFiRoute`), `UiState`/`UiEvent` sealed tipleri | ❌ Hariç | Bir route/olay bir **ekran kimliği/kullanıcı niyeti**dir, backend'in döndürdüğü değer değil. "Hangi sürüm bu değeri anlar?" sorusu yoktur. |
| Tamamen yerel enum'lar (UI state, segment index, feature-içi switch, backend karşılığı olmayan) | ❌ Hariç | Teli hiç geçmez — izlenecek sözleşme yoktur. |

> Örnek: `sealed interface ViFiRoute`'a yeni bir `ExamDetail` route eklemek sürüm yorumu **gerektirmez** — bu bir navigasyon hedefi, backend değeri değil. O ekranın Firestore'dan decode ettiği `ExamDetailDto`/`Exam` modeli ise sözleşmedir; sıfırdan yeni böyle bir model tek tip-seviyesi `// Added in <versionName> — …` yorumu alır.

**Neden:**
- Backend-güdümlü enum ve modeller sürümden sürüme evrilir; yorum "bu değeri hangi sürüm anlıyor?" sorusunu `git blame` kazmadan yanıtlar.
- Backend/QA, minimum uygulama sürümlerine göre payload hizalayabilir (popup, redirect, feature flag) — yorum Android ↔ backend sözleşme tarihçesi olarak ikili iş görür.
- Bir route/UiState case'i bunların hiçbirine yanıt vermez — teli geçmez — bu yüzden onda yorum saf gürültüdür.

**İlgili:** Model alan katalogu ve DTO→domain map kuralları `firebase-data.md`'de.

---

### ViewModel Üye Bloğu — Grup İçi Sıra (Kind bazında)

> **ZORUNLU:** Bir `ViewModel`'ın üst kısmındaki saklı üyeler *kind* bazında ve **her zaman aynı sırada** gruplanır (yukarıdaki "Üye Gruplama" tablosu). iOS'un "ViewController Stored-Member Grouping" kuralının karşılığıdır; orada `let`/`var`/`lazy var` ayrımı vardı, Android'de `val` (immutable) → `var` (mutable internal state) ayrımı geçerli: önce `val` bağımlılıklar ve state akışları, sonra `private var` iş bayrakları.

```kotlin
// ❌ DON'T — dependency, state and flag interleaved
@HiltViewModel
class ExamDetailViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository
) : ViewModel() {
    private var isUnlocking = false
    private val _uiState = MutableStateFlow(ExamDetailUiState())
    private val coinRepository: CoinRepository get() = TODO()  // ⚠️ dependency outside the constructor
    val uiState = _uiState.asStateFlow()
}

// ✅ DO — grouped by kind, fixed order
@HiltViewModel
class ExamDetailViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository,
    private val coinRepository: CoinRepository
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow(ExamDetailUiState())
    val uiState: StateFlow<ExamDetailUiState> = _uiState.asStateFlow()

    // Internal state
    private var isUnlocking = false
}
```

**Kurallar:**
- Tüm bağımlılıklar **constructor injection** ile gelir (`@Inject constructor`), gövdede `get()`/`by lazy` servis locator **yok**. Sıra sabittir — state backing → public state → events → private bayraklar.
- Doc-comment'li bir alan `///`/`//` yorumunu kendi grubunun içinde, bildirimin hemen üstünde tutar.
- Bu kural `ViewModel`'ın üye bloğuyla ilgilidir. `@Composable`'lar kendi `// --- UI ---` düzenini (`ui-components.md`), repository'ler kendi düzenini korur.

**İlgili:** `viewmodel-rule.md` (ViewModel'ın tam sözleşmesi).

---

### Compose/ViewModel Yaşam Döngüsü — Yan Etkiyi Doğru Efekt API'siyle Bağla (iOS "Lifecycle Methods" karşılığı)

> **ZORUNLU:** iOS'ta yaşam döngüsü override'ları (`viewDidLoad`/`viewDidAppear`) adlandırılmış fonksiyonlara delege edilir, içine `guard`/early-return/iş mantığı konmazdı. Android karşılığı: **yan etkiler `@Composable` içinde çıplak çağrı olarak değil, doğru efekt API'si + doğru key ile** tetiklenir; iş mantığı `ViewModel`'da adlandırılmış bir `fun onX()`'tedir, Composable yalnızca onu **doğru efektten** çağırır.

```kotlin
// ❌ DON'T — call the side effect bare in the composition body (re-runs on every recomposition)
@Composable
fun ExamDetailScreen(viewModel: ExamDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    viewModel.loadExam()   // ⚠️ reloads on every recomposition — the Compose equivalent of unguarded logic in iOS viewDidLoad
    ExamDetailContent(state)
}

// ✅ DO — side effect via the right effect + key; one-shot events collected separately
@Composable
fun ExamDetailScreen(
    examId: String,
    viewModel: ExamDetailViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // "did load" equivalent: load once when examId changes
    LaunchedEffect(examId) {
        viewModel.onScreenLoaded(examId)
    }

    // one-shot events (navigate/snackbar) — from the Channel, not UiState
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ExamDetailEvent.NavigateBack -> onBack()
                is ExamDetailEvent.ShowMessage -> { /* snackbar */ }
            }
        }
    }

    ExamDetailContent(state)
}
```

**Kurallar:**
- "Bir kez çalışacak" kurulum → `LaunchedEffect(key)`; key, tekrar çalışmayı tetiklemesi gereken argümandır (`examId`), tek sefer için `Unit`.
- Abonelik + temizlik gereken kaynak (listener, callback) → `DisposableEffect(key) { ... onDispose { ... } }` (iOS `deinit`/`removeObserver` karşılığı).
- Precondition kontrolü (`guard ... else return`) **`ViewModel`'ın `onX()` fonksiyonunun içinde** olur; `@Composable` gövdesinde dallanan iş mantığı **yok**.
- Composition gövdesi recomposition'da defalarca koşar — oraya asla veri yükleme/mutasyon koyma; bu, iOS'un `viewDidAppear`'ına guard'sız iş mantığı koymanın birebir eşdeğeri hatasıdır.

**Neden:**
- Composition gövdesi saf olmalı (yalnızca state okur, UI yayar). Yan etki = efekt API'si; aksi her recomposition'da yeniden koşar (gereksiz ağ/IO, sonsuz döngü riski).
- Yan etkiyi adlandırılmış `onX()`'e delege etmek → test edilebilir, yeniden sıralanabilir, ve bir faz-davranış diff'i tek fonksiyon + tek çağrı satırına dokunur (iOS kuralındaki fayda birebir).

**İlgili:** Ekran iskeleti ve efekt kalıpları `ui-components.md` + skill `skills/new-screen/`.

---

### Akışı Kapatan (Flow-Gating) Olay — Modellenir, Opsiyonel Bırakılmaz

> **ZORUNLU:** iOS'ta "akışı kapatan" bir delegate metodu `@objc optional` değil `required` olmalıydı; eksik implementasyon kullanıcıyı sessiz çıkmaza sokardı. Android MVVM'de delegate yoktur; karşılığı: **bir akışın tek devam yolu olan sonuç, `UiState`/one-shot `Event` olarak AÇIKÇA modellenir** ve Composable `when` ile **her dalı kapatmak zorundadır** (`sealed` + exhaustive `when`, `else` yok). Böylece bir olayı ele almayı unutmak **derleme hatası** olur, sessiz çıkmaz değil.

```kotlin
// ❌ DON'T — nullable/optional callback; if one is skipped the flow dies silently
class ExamDetailViewModel(/* ... */) {
    var onUnlockSucceeded: (() -> Unit)? = null   // ⚠️ if left null the coin is spent but the PDF never opens
    var onUnlockFailed: (() -> Unit)? = null
}

// ✅ DO — flow-gating results are a sealed Event; when is exhaustive (no else)
sealed interface ExamDetailEvent {
    data object UnlockSucceeded : ExamDetailEvent          // the only continuation: open the PDF
    data class UnlockFailed(val message: String) : ExamDetailEvent
    data object NavigateBack : ExamDetailEvent
}

// Composable: handling every branch is MANDATORY — adding a new Event stops this from compiling
LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
        when (event) {
            ExamDetailEvent.UnlockSucceeded -> openPdf()
            is ExamDetailEvent.UnlockFailed -> showError(event.message)
            ExamDetailEvent.NavigateBack -> onBack()
        }   // ⚠️ do NOT add 'else' — the exhaustive when is the compiler's guarantee
    }
}
```

**Kurallar:**
- Akışın tek devam yolu olan sonuçlar `sealed interface`/`sealed class` ile modellenir; Composable `when`'i `else` **kullanmaz** ki yeni bir case eklenince eksik ele alma derleme hatası olsun.
- Gerçekten atlanabilir olaylar (analytics hook, opsiyonel UI ince ayarı) ayrı tutulabilir; akışı kapatan olay asla nullable lambda'ya bağlanmaz.
- One-shot olaylar (`navigate`, `snackbar`, `unlock sonucu`) `Channel`/`SharedFlow` ile; kalıcı ekran durumu `UiState` ile taşınır — ikisi karıştırılmaz.

**Neden:**
- Nullable callback, compile-time güvenliği esnekliğe takas eder; akışın tamamlanması için *koşması gereken* bir geri çağrıda bu yanlış takastır — "implemente etmeyi unuttum" hatasını runtime çıkmaza çevirir. `sealed` + exhaustive `when` bunu derleme hatasına geri çevirir.

---

### Interface Default Metotları — Yalnızca Çok-Implementasyonlu Bir Köprü Aracı

> **ZORUNLU:** Bir Kotlin `interface`'ine gövdeli default metot (`fun x() { }`) eklemenin **tek** meşru sebebi: aynı arayüzü **birden çok sınıf** zaten implemente ederken, yeni bir metot eklendiğinde build'i yeşil tutmak. **Tek-implementasyonlu** bir arayüzde default gövde **ekleme**: metodu gövdesiz (abstract) bırak ki eksik/yanlış implementasyon **derleme hatası** olsun. Tek-implementasyonlu arayüzdeki boş default gövde, zorunlu bir çağrıyı sessizce atlanabilire çeviren ölü koddur — en çok da bir async akışın tek devamı olan bir callback'te acıtır.

```kotlin
// ❌ DON'T — single-implementation interface + empty default body
interface ArchiveRepository {
    suspend fun catalogChildren(parentId: String): List<CatalogNode>
    fun observeExam(examId: String): Flow<Exam> = emptyFlow()  // only one impl; empty default is a silent no-op
}

// ✅ DO — single implementation → abstract; a missing override won't compile
interface ArchiveRepository {
    suspend fun catalogChildren(parentId: String): List<CatalogNode>
    fun observeExam(examId: String): Flow<Exam>   // no body
}

// ✅ DO — 2+ implementations → a default is legitimate only on a method some of them need
interface AnalyticsSink {
    fun logScreen(name: String)
    fun logUnlock(examId: String) {}   // no-op for most sinks; only the interested one overrides
}
```

**Kurallar:**
- **Implementasyon sayısı karar verir.** 1 implementasyon → abstract, default yok. 2+ implementasyon → yalnızca bazılarının gerektirdiği metotlarda default serbest; herkesin ele alması gereken metotlar çok-implementasyonlu arayüzde bile abstract kalır.
- Default gövde bir **build aracıdır**, yazım kolaylığı değil. Sırf tek implementasyonda stub yazmamak için boş gövde ekleme — yasak durum budur.
- Akışı sürükleyen callback en yüksek riskli durumdur: async akışı devam ettiren/render eden tek yol buysa, sessiz default no-op tıpkı opsiyonel callback gibi runtime çıkmazdır.

**Neden:**
- Default gövde metodu *her* implementasyon için opsiyonel yapar; tek implementasyonla bu hiçbir şey kazandırmaz ve derleyicinin "implemente etmeyi unuttun" güvencesini sessizce kaldırır.

---

### `@Suppress` / Lint `disable` Direktifleri — Yalnızca Gerçek İhlalde, Doğru Yerde, Bayatlayınca Silinir

> **ZORUNLU:** Bir `@Suppress("...")` ya da ktlint/detekt baseline girdisi **yalnızca** somut bir satır gerçekten o kuralı ihlal ederken var olabilir. Eklemeden/korumadan önce üç şeyi doğrula: (1) bir satır kuralı **gerçekten** ihlal ediyor; (2) suppress **tam o öğeyi/bloğu** kapsıyor (mümkün olan en dar scope — tüm dosya değil, tek fonksiyon/ifade); (3) ihlal gidince **kaldırılıyor**. En sık kırılma: uzun bir çağrı çok satıra bölününce ya da kod refactor edilince, artık ölü olan `@Suppress` geride bırakılır ve hiçbir şey susturmaz.

```kotlin
// ❌ DON'T — dead directive after a refactor; it no longer guards any violation
@Suppress("LongMethod")   // ⚠️ the function is now split into small pieces; the directive is dead
fun loadCatalog(parentId: String) {
    archiveRepository.catalogChildren(parentId)
}

// ✅ DO — if truly needed, at the narrowest scope, with a rationale
fun pdfPageCount(doc: PdfDocument): Int {
    // Third-party pdfium API is a single unbreakable call; name shadowing is intentional here.
    @Suppress("NAME_SHADOWING")
    val doc = doc
    return doc.pageCount
}
```

**Kurallar:**
- **Susturmaktansa düzelt.** Uzun satır → çok satıra böl; karmaşık fonksiyon → private yardımcılara ayır. `@Suppress`'a yalnızca satır/öğe gerçekten düzeltilemezken (unbreakable third-party API, generated kod) başvur.
- **En dar scope.** Mümkünse ifade/fonksiyon seviyesinde; dosya-başı `@file:Suppress` yalnızca generated dosyalarda.
- **Refactor'da sil.** Bir satırı kısalttığında/böldüğünde direktifi aynı değişiklikte kaldır — artık ihlal korumayan direktif ölü koddur; sonraki okuru yanıltır ve o öğedeki *gelecekteki* gerçek ihlali gizler.

**Neden:**
- Geride kalan direktif sessiz çürümedir: "burada ele alınmış bir sorun var" der ama yoktur; kapsadığı öğeyi önceden susturur, böylece sonradan o öğeyi gerçekten ihlal eden bir edit lint uyarısı olmadan geçer.

**İlgili:** Lint/detekt konfig ve baseline kuralları `commands/check-architecture.md`.

---

## Mimari Desen: Compose + ViewModel + Repository (Tek Yönlü Veri Akışı)

Bu proje **MVVM** kullanır: **state aşağı, olay yukarı** (unidirectional data flow). iOS'un "ViewController + View + ViewModel (Delegate)" üçlüsü Android'de **Screen `@Composable` + `ViewModel` + `Repository`** olur; delegate pattern yerine **`StateFlow` (durum) + `Channel`/`SharedFlow` (olay)** geçer.

### Katman Sorumlulukları

| Katman | Sorumluluk | Temel tip |
|--------|------------|-----------|
| **UI (Screen Composable)** | Durumu çizer, kullanıcı olaylarını ViewModel'a iletir, navigasyonu tetikler | `@Composable fun XScreen(...)` |
| **ViewModel** | İş mantığı, durum yönetimi, tek yönlü akış | `@HiltViewModel class XViewModel : ViewModel()` |
| **UiState** | Ekranın tek immutable durumu | `data class XUiState(...)` |
| **Repository (interface)** | Veri erişimi soyutlaması; DTO→domain map | `interface XRepository` |
| **Repository (impl)** | Firebase ile konuşan canlı implementasyon | `Firebase...Repository` |
| **Model** | Saf domain modeli (Firebase tipi sızmaz) | `data class Exam/University/...` |
| **DI Module** | Bağımlılık grafiği (live/fake binding) | `@Module @InstallIn(...)` |

**Legacy not:** Bugünkü uygulamada veri erişimi `Activity`/`AsyncTask` içinde doğrudan `FirebaseDatabase.getReference("Universitiess")` çağrılarıyla yapılır; UI ile veri tek sınıfta iç içedir. Hedef: her kaynak bir `interface` arkasında, UI'ya yalnızca domain modeli ulaşır (roadmap `FM-12`, `FM-13`, `BE-15`).

### Tek Yönlü Veri Akışı (State Down, Events Up)

```
┌─────────────────────────────────────────────────────────────┐
│  Screen @Composable                                          │
│   - collectAsStateWithLifecycle() ile UiState'i OKUR (down)  │
│   - kullanıcı etkileşimini viewModel.onX() ile İLETİR (up)   │
│   - events Flow'unu LaunchedEffect'te toplar (navigate/snack)│
└───────────────▲───────────────────────────┬─────────────────┘
       state (StateFlow)              intent (fun onX)
┌───────────────┴───────────────────────────▼─────────────────┐
│  ViewModel (@HiltViewModel)                                  │
│   - _uiState: MutableStateFlow  → uiState: StateFlow         │
│   - _events: Channel            → events: Flow               │
│   - viewModelScope.launch { repository... }                  │
└───────────────▲───────────────────────────┬─────────────────┘
          domain model / throws      suspend fun / Flow
┌───────────────┴───────────────────────────▼─────────────────┐
│  Repository (interface) → Firebase...Repository (impl)      │
│   - Firestore/RTDB/Storage ile konuşur (Dispatchers.IO)     │
│   - DTO → domain model map eder (ham tip SIZMAZ)            │
└──────────────────────────────────────────────────────────────┘
```

**Kural:** Veri yalnızca aşağıdan yukarı tek bir `StateFlow` ile akar; UI onu değiştiremez, yalnızca `onX()` çağırarak ViewModel'dan değişiklik *ister*. ViewModel durumu `_uiState.update { it.copy(...) }` ile değiştirir. Composable'dan Composable'a mutable state elden ele geçirilmez.

### Modül Yapısı

Her feature modülü şu paketleme desenini izler (paket-bazlı; Gradle çok-modül opsiyonel — bkz. roadmap `FM-21`):

```
com.salticusteam.vifi/
├── feature/
│   ├── archivebrowse/
│   │   ├── ArchiveBrowseScreen.kt        # @Composable (UI)
│   │   ├── ArchiveBrowseViewModel.kt     # @HiltViewModel
│   │   ├── ArchiveBrowseUiState.kt       # data class + (varsa) sealed Event
│   │   └── CatalogNodeItem.kt            # (liste varsa) tek öğe Composable'ı
│   ├── examdetail/
│   │   ├── ExamDetailScreen.kt
│   │   ├── ExamDetailViewModel.kt
│   │   └── ExamDetailUiState.kt
│   └── addexam/ ...
└── core/
    ├── data/
    │   ├── repository/
    │   │   ├── ArchiveRepository.kt          # interface
    │   │   ├── FirebaseArchiveRepository.kt  # canlı impl
    │   │   └── FakeArchiveRepository.kt       # preview/test impl
    │   └── dto/
    │       └── ExamDto.kt                      # Firestore @DocumentId tipleri
    ├── model/
    │   ├── Exam.kt  University.kt  Faculty.kt  Department.kt  Lesson.kt  CatalogNode.kt
    │   └── ArchiveError.kt                     # sealed hata tipi
    ├── designsystem/                           # Color.kt Type.kt Theme.kt bileşenler
    ├── navigation/                             # ViFiRoute + NavHost
    └── util/
```

### Alan (Domain) Organizasyonu

Feature'lar ViFi domain'ine göre gruplanır:
- **Browse/Arşiv:** `archivebrowse` (University → Faculty → Department → Lesson katalog gezinmesi), `examdetail` (sınav PDF + coin kilidi)
- **Katkı:** `addexam` (kullanıcı sınav gönderimi + moderasyon)
- **Kimlik:** `auth` (telefon girişi +90), `onboarding` (profil tamamlama kapısı)
- **Kullanıcı:** `profile`, `coins` (coin ekonomisi), `notifications` (bildirim merkezi)

### Zorunlu Desenler

#### Screen Composable Deseni

```kotlin
// Stateful entry — takes the Hilt ViewModel, collects state, exposes navigation
@Composable
fun ArchiveBrowseScreen(
    onExamClick: (examId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: ArchiveBrowseViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()   // ⚠️ lifecycle-aware collect

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ArchiveBrowseEvent.OpenExam -> onExamClick(event.examId)
            }
        }
    }

    ArchiveBrowseContent(
        state = state,
        onNodeClick = viewModel::onNodeSelected,   // events up
        onRetry = viewModel::onRetry,
        onBack = onBack
    )
}

// Stateless content — takes only state + lambdas; @Preview feeds from here, tests drive this
@Composable
private fun ArchiveBrowseContent(
    state: ArchiveBrowseUiState,
    onNodeClick: (CatalogNode) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.archive_browse_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_navigate_back))
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingView(Modifier.padding(padding))
            state.error != null -> ErrorView(message = state.error, onRetry = onRetry, modifier = Modifier.padding(padding))
            else -> CatalogList(nodes = state.nodes, onNodeClick = onNodeClick, modifier = Modifier.padding(padding))
        }
    }
}
```

**Kurallar:**
- Ekran **ikiye** bölünür: *stateful* giriş (ViewModel + state toplama + navigasyon lambda'ları) ve *stateless* içerik (yalnızca `state` + lambda). `@Preview` ve Compose testleri stateless içeriği sürer — bu, iOS'un `LayoutableView.create()` ayrımının karşılığıdır (UI kurulumu VC'den ayrı).
- Navigasyon **ekranın içinde yapılmaz**; `onX: () -> Unit` lambda'ları ile dışarı verilir, `NavHost` bağlar (bkz. Navigasyon Deseni).
- Renkler `MaterialTheme.colorScheme`/`core/designsystem/Color.kt`'ten; metinler `stringResource(R.string.x)`'ten (hardcoded yok — `localization.md`, `ui-components.md`).

#### UiState Deseni

```kotlin
// The screen's SINGLE immutable state — fields + isLoading + error (the one consistent project pattern)
data class ArchiveBrowseUiState(
    val isLoading: Boolean = false,
    val nodes: List<CatalogNode> = emptyList(),
    val error: String? = null
)

// One-shot events are a separate sealed type
sealed interface ArchiveBrowseEvent {
    data class OpenExam(val examId: String) : ArchiveBrowseEvent
}
```

> **Tek desen kuralı:** Yükleme/hata için projede **tek** bir desen tutarlı kullanılır: `data class` + `isLoading: Boolean` + `error: String?` (tercih edilen). Bir ekranda `data class + alanlar`, başka ekranda `sealed interface Loading/Success/Error` karıştırma — proje genelinde `data class + alanlar` seçildi.

#### ViewModel Deseni

```kotlin
@HiltViewModel
class ArchiveBrowseViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository   // ⚠️ constructor injection, depends on the interface
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow(ArchiveBrowseUiState())
    val uiState: StateFlow<ArchiveBrowseUiState> = _uiState.asStateFlow()

    // One-shot events
    private val _events = Channel<ArchiveBrowseEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Internal state
    private var loadJob: Job? = null

    fun onNodeSelected(node: CatalogNode) {
        if (node.hasChildren) loadChildren(node.id) else emitOpenExam(node)
    }

    fun onRetry() = loadChildren(currentParentId)

    private fun loadChildren(parentId: String) {
        loadJob?.cancel()                                     // cancel the previous request (no retain/leak)
        loadJob = viewModelScope.launch {                    // ⚠️ viewModelScope — cancelled when the VM dies
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { archiveRepository.catalogChildren(parentId) }
                .onSuccess { nodes -> _uiState.update { it.copy(isLoading = false, nodes = nodes) } }
                .onFailure { t ->
                    _uiState.update {
                        it.copy(isLoading = false, error = (t as? ArchiveError)?.userMessage ?: ArchiveError.Unknown.userMessage)
                    }
                }
        }
    }

    private fun emitOpenExam(node: CatalogNode) {
        viewModelScope.launch { _events.send(ArchiveBrowseEvent.OpenExam(node.examId)) }
    }
}
```

**Kurallar:**
- İş `viewModelScope.launch`'ta; IO/Firebase `Dispatchers.IO`'da (repository içinde). `_uiState.update { it.copy(...) }` ile atomik güncelle.
- Durum güncellemesi delegate/callback ile değil, `StateFlow` emit ile olur. Tek seferlik yönlendirme/mesaj `Channel`'a `send` edilir.
- `Context` ViewModel'da tutulmaz (ApplicationContext istisna, Hilt `@ApplicationContext` ile). Tam kurallar `viewmodel-rule.md`'de.

#### Hilt DI / Modül Deseni (iOS `AppEnvironment(live/mock)` karşılığı)

> **ZORUNLU:** iOS'ta `AppEnvironment` live/mock servisleri seçerdi. Android'de karşılığı **Hilt modülleri + repository arayüzleri**: canlı grafik bir `@Module`'da `@Binds`/`@Provides` ile bağlanır; test/preview grafiği `@TestInstallIn` ile Fake'leri bağlar.

```kotlin
// Live graph
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindArchiveRepository(impl: FirebaseArchiveRepository): ArchiveRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository
}

// Firebase providers — named 'vifi' Firestore (eur3), europe-west1 Functions
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore =
        FirebaseFirestore.getInstance(FirebaseApp.getInstance(), "vifi")   // ⚠️ named DB, not DEFAULT

    @Provides
    @Singleton
    fun provideFunctions(): FirebaseFunctions =
        FirebaseFunctions.getInstance(FirebaseApp.getInstance(), "europe-west1")
}
```

**Kurallar:**
- Bağımlılıklar **constructor injection** ile; servis locator / singleton erişimi (`FirebaseDatabase.getInstance()` call-site'ta) **yok**, `FirebaseModule`'dan gelir.
- Canlı implementasyon `@Binds` ile arayüze bağlanır; preview/test `FakeArchiveRepository`'yi doğrudan constructor'a verir (Compose `@Preview`) ya da `@TestInstallIn` ile değiştirir.
- Firebase ortak `vifi-831a8` projesidir; named `vifi` Firestore + `europe-west1` Functions sabittir (roadmap `FM-15`, `FM-16`, `BE-05`). Güvenlik kuralları/Cloud Functions Android'de **yazılmaz** — `ViFi-iOS/firebase/`'de durur, Android yeniden kullanır.

**İlgili:** DI/mock kurulumu `skills/generate-mocks/`, `commands/generate-mocks.md`; repository sözleşmeleri `firebase-data.md`.

### İsimlendirme Kuralları

| Tip | Desen | Örnek |
|-----|-------|-------|
| Screen Composable | `<Name>Screen.kt` (`fun <Name>Screen`) | `ArchiveBrowseScreen.kt` |
| ViewModel | `<Name>ViewModel.kt` | `ArchiveBrowseViewModel.kt` |
| UiState | `<Name>UiState.kt` (`data class <Name>UiState`) | `ArchiveBrowseUiState.kt` |
| Event | `sealed interface <Name>Event` | `ArchiveBrowseEvent` |
| Liste öğesi Composable | `<Name>Item.kt` (`fun <Name>Item`) | `CatalogNodeItem.kt` |
| Repository arayüzü | `<Name>Repository.kt` | `ArchiveRepository.kt` |
| Canlı impl | `Firebase<Name>Repository.kt` | `FirebaseArchiveRepository.kt` |
| Fake impl | `Fake<Name>Repository.kt` | `FakeArchiveRepository.kt` |
| DTO | `<Name>Dto.kt` | `ExamDto.kt` |
| Domain model | `<Name>.kt` (`data class`) | `Exam.kt`, `University.kt` |
| Hata tipi | `<Name>Error.kt` (`sealed class ... : Exception()`) | `ArchiveError.kt` |
| Hilt modül | `<Name>Module.kt` | `RepositoryModule.kt` |

### Dosya Organizasyonu

```
com.salticusteam.vifi/
├── VifiApplication.kt          # @HiltAndroidApp; App Check + Firebase init
├── MainActivity.kt             # tek ComponentActivity + setContent + enableEdgeToEdge
├── feature/<ad>/               # ekran başına: Screen, ViewModel, UiState, [Item]
├── core/
│   ├── data/
│   │   ├── repository/         # interface + Firebase*Impl + Fake*
│   │   ├── dto/                # Firestore/RTDB DTO'ları
│   │   └── remote/             # Firebase provider wiring (FirebaseModule)
│   ├── model/                  # saf domain data class + sealed hata tipleri
│   ├── designsystem/           # theme/ (Color.kt, Type.kt, ViFiTheme.kt) + component/ (ortak bileşenler)
│   ├── navigation/             # ViFiRoute, ViFiNavHost
│   ├── di/                     # Hilt modülleri
│   └── util/                   # tarih (date-handling.md), uzantılar
└── (legacy)                    # eski Java Activity/Fragment — emekliye ayrılıyor (FM-23)
```

> Strings `res/values/strings.xml`'de (Türkçe-only), tema renkleri `core/designsystem/Color.kt`'te — koda gömülmez (`localization.md`, `ui-components.md`). Dokümantasyon `.md` dosyaları `.claude/` altında.

### Yasak Desenler

❌ **ASLA kullanma:**
- Yeni ekranlarda `Fragment` / XML layout / `findViewById` / `ViewBinding` — tüm yeni UI Compose'dur (legacy istisnası hariç)
- `AsyncTask` / çıplak `Thread` / `Handler(Looper...)` arka plan işi — Coroutines + `Dispatchers.IO` kullan
- RxJava — Coroutines + Flow kullan
- Call-site'ta doğrudan `FirebaseDatabase.getInstance()` / `FirebaseFirestore.getInstance()` — Hilt ile enjekte edilen repository arayüzünden geç
- UI'ya ham Firebase tipi sızdırmak (`DataSnapshot`, `DocumentSnapshot`, `QueryDocumentSnapshot`) — repository DTO→domain map eder
- Hardcoded renk (`Color(0xFF...)` çağrı yerinde) — `MaterialTheme.colorScheme` / `core/designsystem/Color.kt`
- Hardcoded kullanıcı-görünür string (Composable'a gömülü `"..."`) — `stringResource(R.string.x)`; Türkçe-only
- `!!` (not-null assertion) — `?.`/`requireNotNull` gerekçeli / `checkNotNull`
- Inline `SimpleDateFormat` / `DateTimeFormatter` backend tarihleri için — `core/util` tarih yardımcıları (`date-handling.md`)
- `GlobalScope.launch` — `viewModelScope` / lifecycle scope kullan
- `Context`/`Activity`/`View` referansını ViewModel'da saklamak (ApplicationContext istisna, `@ApplicationContext` ile)
- Composition gövdesinde veri yükleme/mutasyon — `LaunchedEffect`/`DisposableEffect` kullan (yukarı bkz.)
- Elle/metinli geri butonu ("Geri" metni, custom back) — `TopAppBar` + `navigationIcon` (ikon-only) + `navController.popBackStack()`
- **AdMob / `play-services-ads`** — parite için tamamen kaldırılıyor (reklam yok; roadmap `FM-08`, `BE-12`)
- Parse SDK — native FCM'e geçildi (roadmap `FM-07`, `BE-11`)
- `else` ile kapatılan `when` (akış-kapatan sealed olay/durumda) — exhaustive `when` derleyici güvencesidir

### Zorunlu Kullanım

✅ **HER ZAMAN kullan:**
- `@Composable` ekranlar + `@HiltViewModel` ViewModel + `interface` repository
- `StateFlow<XUiState>` (durum) + `Channel`/`SharedFlow` (one-shot olay); UI `collectAsStateWithLifecycle()` ile toplar
- Constructor injection (Hilt) — her kaynak `interface` arkasında, canlı `Firebase*Repository` / test `Fake*Repository`
- `viewModelScope.launch` + `Dispatchers.IO` (repository'de); Firebase `Task` → `.await()` (`kotlinx-coroutines-play-services`); listener → `callbackFlow { ... awaitClose { ref.removeEventListener(l) } }`
- DTO → domain model map (ham Firebase tipi sızmaz); modeller saf `data class`
- `MaterialTheme.colorScheme` / `core/designsystem/Color.kt` renk için, `MaterialTheme.typography` / `Type.kt` tipografi için
- `stringResource(R.string.x)` tüm kullanıcı metni için (Türkçe-only, positional format args `%1$s`/`%1$d`)
- `TopAppBar` + `navigationIcon` (ikon-only geri) + `navController.popBackStack()` navigasyonda
- `Modifier.semantics { contentDescription = ... }` + `Modifier.testTag("<ekran>.<öğe>")` erişilebilirlik/test için (testTag'ler sözleşmedir — değiştirme)
- `sealed class XError(val userMessage: String) : Exception()` (inline Türkçe) hatalarda; ViewModel `runCatching` ile yakalayıp `userMessage`'ı `UiState.error: String?`'a koyar, UI o String'i gösterir

### Bellek & Yaşam Döngüsü Güvenliği (Memory Safety)

⚠️ **KRİTİK** — iOS'ta kaygı retain cycle / `[weak self]` idi. Android'de kaynak sızıntısının yeri farklı ama risk aynı; kurallar:
- **Coroutine'ler doğru scope'ta.** İş `viewModelScope`'ta (ViewModel temizlenince otomatik iptal) — `GlobalScope` asla. Composable içindeki yan etki `rememberCoroutineScope()`/`LaunchedEffect` (composition'dan çıkınca iptal).
- **Her listener sökülür.** RTDB/Firestore/callback dinleyicileri `callbackFlow { ... awaitClose { ref.removeEventListener(l) } }` içinde; `awaitClose` olmadan dinleyici (ve bağladığı her şey) asılı kalır — iOS'un `deinit`'te `removeObserver` çağrısının birebir karşılığıdır.
- **Compose'da efekt + doğru key.** Abonelik gereken yan etki `DisposableEffect(key) { onDispose { ... } }`; tek seferlik iş `LaunchedEffect(key)`. Yanlış key → ya hiç çalışmaz ya her recomposition'da yeniden.
- **Context'i ViewModel'da tutma.** `Activity`/`View`/`Context` referansı ViewModel'ı yaşam döngüsü ötesine pinler (sızıntı) — ApplicationContext istisna, Hilt `@ApplicationContext` ile.
- **Retain cycle kontrolü:** Repository → ViewModel geri referansı tutmaz (akış tek yön: ViewModel repository'yi çağırır, repository `suspend`/`Flow` döndürür).

#### Saklı Callback / Lambda — iOS "Stored Closure Properties" Karşılığı

> **ZORUNLU:** Bir **uzun ömürlü nesneye (repository, manager, singleton) saklanan** bir callback/lambda, dış bir `Context`/`View`/`ViewModel`'ı **yakalamamalıdır**. iOS'ta tehlike `clickedOkButton = { self... }` saklı closure'un VC'yi pinlemesiydi; Android'de karşılığı: bir `var onX: (() -> Unit)?` alanına atanan ya da bir Firebase listener'ına kapatılan lambda, bir `Activity`/`View`/kısa ömürlü scope'u yakalarsa o nesne listener yaşadıkça sızar. Derleyici burada **uyarı vermez** — en çok kaçırılan nokta budur.

```kotlin
// ❌ DON'T — the RTDB listener outlives the call; the lambda captures the ViewModel (and everything it reaches),
//            no awaitClose → the callback stays attached even after the VM is cleared
fun observeExam(examId: String): Flow<Exam> = callbackFlow {
    val listener = examRef(examId).addValueEventListener(object : ValueEventListener {
        override fun onDataChange(snap: DataSnapshot) {
            trySend(snap.toExam())
        }
        override fun onCancelled(error: DatabaseError) { close(error.toException()) }
    })
    // ⚠️ no awaitClose → the listener is never removed
}

// ✅ DO — callbackFlow + awaitClose; the listener is removed for sure when the flow is cancelled
fun observeExam(examId: String): Flow<Exam> = callbackFlow {
    val ref = examRef(examId)
    val listener = object : ValueEventListener {
        override fun onDataChange(snap: DataSnapshot) { trySend(snap.toExam()) }
        override fun onCancelled(error: DatabaseError) { close(error.toException()) }
    }
    ref.addValueEventListener(listener)
    awaitClose { ref.removeEventListener(listener) }   // ⚠️ the iOS deinit removeObserver equivalent
}.flowOn(Dispatchers.IO)
```

**Neden:**
- Firebase listener'ı eklendiği `ref`'te saklanır; lambda (ve yakaladığı her şey) `ref` canlı olduğu sürece yaşar — call'ın ömrü kadar değil. `awaitClose` olmadan collect iptal edilse bile dinleyici kalır.
- Aynı kural saklı `var onUpdate: ((T) -> Unit)?` manager callback'leri için: callback `Activity`/`View` yakalamamalı; ViewModel/collector onu `viewModelScope`'ta toplamalı. Tek bir güçlü yakalama bile nesneyi pinlemeye yeter.
- Gerçek risk noktası: bir listener ekleyip `awaitClose`/`removeEventListener` yazmayı unutmak — PR review'da en sık yakalanan sızıntı; baştan `callbackFlow + awaitClose` ile yaz.

**İlgili:** Tüm realtime akış + `.await()` kalıpları `firebase-data.md`'de.

#### StateFlow / Flow Besleme Döngüleri (iOS "EventBus Feedback Loops" karşılığı)

> **ZORUNLU:** Bir `Flow`/`StateFlow`'u topladığın (`collect`) yerde, **downstream yan etkisi aynı akışı yeniden yayınlayan** bir metot çağırma. Collector yeniden tetiklenir, metodu tekrar çağırır ve uygulama ana thread'de sonsuz döngüye girer (pil tüketir, UI donar, çöker).

```kotlin
// ❌ WRONG — infinite loop: we listen to the profile stream and write the profile inside it
viewModelScope.launch {
    profileRepository.observeProfile().collect { profile ->
        profileRepository.touchLastSeen(profile.uid)   // this write re-triggers observeProfile → infinite
    }
}

// ✅ CORRECT — the collector is read-only; writes happen separately, from a one-shot trigger
viewModelScope.launch {
    profileRepository.observeProfile().collect { profile ->
        _uiState.update { it.copy(profile = profile) }   // only reflect into state; no writes
    }
}
// If a write is needed, do it OUTSIDE the stream, from a one-shot event (button/onResume):
fun onScreenResumed() = viewModelScope.launch { profileRepository.touchLastSeen(uid) }
```

**Nasıl uygulanır:**

| Taraf | Handler'da ne serbest |
|-------|------------------------|
| Yazan (state'i değiştiren) | Repository'ye yaz, ardından akış doğal olarak yeni değeri yayar. |
| Dinleyen (state'i çizen) | Salt-okuma: `_uiState.update`, UI güncelleme. Aynı kaynağa yazan (dolaylı bile) metot **çağırma**. |

**Zihinsel kontrol:** Collector içinde çağırdığın metodun her downstream dalını izle; herhangi biri bu akışı besleyen repository'ye yazıyorsa döngü olur. Şüphedeyken salt-okuma varyantı seç ya da sadece `_uiState.update`.

#### Immutable State Güncelleme (iOS "Mutating Optional/Nested Storage" karşılığı)

> **ZORUNLU:** iOS'ta aynı optional yolu hem okuyup hem yazarken exclusivity hatası çıkardı; Android'de `MutableStateFlow`/`data class` **immutable kopya** ile güncellenir — iç içe alan asla yerinde mutate edilmez. `_uiState.update { it.copy(...) }` tek atomik yazımdır.

```kotlin
// ❌ DON'T — read state, mutate, write back (not atomic; a concurrent update is lost)
val current = _uiState.value
current.exams.toMutableList().removeIf { it.id == examId }   // ⚠️ not a copy, race risk
_uiState.value = current

// ✅ DO — immutable copy in a single update block; the nested list is rebuilt via copy
_uiState.update { state ->
    state.copy(exams = state.exams.filterNot { it.id == examId })
}
```

**Neden:**
- `_uiState.update { }` okuma→dönüştürme→yazmayı atomik yapar; `value = value.copy()` iki ayrı adımdır ve eşzamanlı güncellemeyi ezer. İç içe `List`/`Map` her zaman `copy` + saf dönüşüm (`map`/`filter`) ile yeniden üretilir, yerinde `add`/`removeIf` ile değil.

---

### Navigasyon Deseni (Navigation-Compose)

> iOS'un `Router` + `safePush`/`safePop` + tutarlı geri butonu deseninin karşılığı: **Navigation-Compose** (`NavHost`, type-safe route'lar) + `TopAppBar` `navigationIcon` + `navController.popBackStack()`.

```kotlin
// Routes — type-safe (kotlinx.serialization)
sealed interface ViFiRoute {
    @Serializable data object ArchiveBrowse : ViFiRoute
    @Serializable data class ExamDetail(val examId: String) : ViFiRoute
}

@Composable
fun ViFiNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = ViFiRoute.ArchiveBrowse) {
        composable<ViFiRoute.ArchiveBrowse> {
            ArchiveBrowseScreen(
                onExamClick = { examId -> navController.navigate(ViFiRoute.ExamDetail(examId)) },
                onBack = { navController.popBackStack() }
            )
        }
        composable<ViFiRoute.ExamDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<ViFiRoute.ExamDetail>()
            ExamDetailScreen(
                examId = route.examId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
```

**Kurallar:**
- Navigasyon `NavHost`'ta merkezîdir; ekran Composable'ları `onX: () -> Unit` lambda'ları ile *niyet bildirir*, `navController`'a doğrudan erişmez (test edilebilir, önizlenebilir kalır).
- Çift-tap guard (iOS `isNavigating`) gerekmez — Navigation-Compose aynı hedefe hızlı tekrar navigasyonu doğası gereği idempotent işler; gereken yerde `launchSingleTop = true`.
- Argümanlar type-safe route alanlarıyla taşınır (`ExamDetail(examId)`); PII asla route string'ine gömülmez.

#### Geri Butonu — `TopAppBar` `navigationIcon` (Mandatory)

> **ZORUNLU:** Geri gidilebilen her ekranda geri affordance'ı **Material3 `TopAppBar`'ın `navigationIcon`'u** (ikon-only, `Icons.AutoMirrored.Filled.ArrowBack`) olur ve `navController.popBackStack()`'i tetikler. **Elle/metinli geri butonu** ("Geri" metni, custom composable back) kullanılmaz. İstek/tasarım açıkça farklı bir geri affordance'ı belirtmiyorsa varsayılan budur — sormadan böyle ilerle.

```kotlin
// ✅ DO — icon-only back, contentDescription from a string resource
TopAppBar(
    title = { Text(stringResource(R.string.exam_detail_title)) },
    navigationIcon = {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_navigate_back)
            )
        }
    }
)
```

İstisnalar (ikon-only geri zorlanmaz): bottom sheet / dialog (kapatma "✕"), ve tasarımın **açıkça** başka bir geri affordance'ı tanımladığı ekranlar. Tam kural ve bileşen `ui-components.md` → "Navigation Back Button".

#### Belirli Alt-Duruma (Segment/Sekme) Yönlendirme — Tek Kaynak, Olay Değil

> **ZORUNLU:** Bir sekmeye/ekrana **belirli bir alt-durum seçili** gitmek için (örn. Arşiv'i belirli bir Fakülte seçili açmak, Profil'i belirli bir sekmede açmak), o seçimi **her zaman navigation argümanı** ya da `SavedStateHandle` ile taşı; global bir olay/`SharedFlow` **fırlatıp** hedef ekranın onu yakalamasını bekleme. (iOS'ta bu `LocalStore.myEsimNavigationModel` tek-kaynağıydı, `EventBus` notification'ı değil — Android karşılığı nav arg / `SavedStateHandle`.)

```kotlin
// ❌ WRONG — forcing the segment selection via a global event (timing-dependent, two conflicting signals)
appEventBus.emit(AppEvent.SelectArchiveFaculty(facultyId))   // lost if the destination isn't on the NavHost yet

// ✅ CORRECT — carry the selection via a route argument; the target ViewModel reads it from SavedStateHandle
navController.navigate(ViFiRoute.ArchiveBrowse(preselectFacultyId = facultyId))

// Target ViewModel — single source of truth: SavedStateHandle
@HiltViewModel
class ArchiveBrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val archiveRepository: ArchiveRepository
) : ViewModel() {
    private val preselectFacultyId: String? = savedStateHandle.toRoute<ViFiRoute.ArchiveBrowse>().preselectFacultyId
    // ... in init, if preselectFacultyId is set, load that faculty preselected
}
```

**Neden:**
- **Tek source of truth:** Seçim yalnızca route/`SavedStateHandle`'da; olay + state birlikte kullanılırsa iki çelişen sinyal yarış (race) yaratır.
- **Timing-bağımsız:** Argüman hedef Composition'a oturduğunda okunur; global olay hedef henüz NavHost'ta değilken emit edilirse boşa gider (iOS'ta notification'ın `DispatchQueue.main.async` ile sarmalanma zorunluluğunun kırılganlığı).
- **Proje tutarlılığı:** Tüm derin yönlendirmeler (deep link, bildirim tıklaması, sınav yükleme sonrası) bu deseni kullanır.

**İlgili:** Deep link + bildirim yönlendirmesi `ui-components.md` + `firebase-data.md`.

---

### Veri Katmanı (Repository Pattern — iOS'tan AYRIŞAN Karar)

> ⚠️ **iOS'tan ayrışan bilinçli karar:** iOS kuralı Repository pattern'i **yasaklıyordu** ("Services + LocalStore + Managers kullan"). Android hedef mimarisi **Repository pattern'i ZORUNLU kılar** — bu, platform idiomatiğinin dayattığı bir uyarlamadır (Hilt + Coroutines + test edilebilirlik). iOS'un `protocol + Service + Mock` üçlüsü Android'de `interface + Firebase*Repository + Fake*Repository` olur.

```kotlin
// Interface — UI/ViewModel depends only on this
interface ArchiveRepository {
    suspend fun catalogChildren(parentId: String): List<CatalogNode>   // throws ArchiveError on failure
    suspend fun exam(examId: String): Exam                             // throws ArchiveError on failure
    fun observeExam(examId: String): Flow<Exam>
}

// Live impl — talks to Firebase, maps DTO→domain, never leaks the raw type
class FirebaseArchiveRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) : ArchiveRepository {

    override suspend fun catalogChildren(parentId: String): List<CatalogNode> =
        try {
            firestore.collection("catalogNodes")
                .whereEqualTo("parentId", parentId)
                .whereEqualTo("status", "published")
                .get()
                .await()                                   // Task → suspend
                .documents.mapNotNull { it.toObject<CatalogNodeDto>()?.toDomain() }
        } catch (e: FirebaseFirestoreException) {
            throw e.toArchiveError()                        // map the Firebase failure → domain ArchiveError, then throw
        }
}

// Fake — preview/test; no Firebase, deterministic
class FakeArchiveRepository : ArchiveRepository {
    override suspend fun catalogChildren(parentId: String): List<CatalogNode> =
        listOf(CatalogNode(id = "eng", title = "Mühendislik", hasChildren = true))
    override suspend fun exam(examId: String): Exam = SampleExam.default
    override fun observeExam(examId: String) = flowOf(SampleExam.default)
}
```

**Kurallar:**
- Her veri kaynağı bir `interface` arkasında; canlı `Firebase*Repository`, preview/test `Fake*Repository`. ViewModel yalnızca arayüze bağımlı.
- Repository ham Firebase tipini (`DataSnapshot`/`DocumentSnapshot`) **UI'ya sızdırmaz**: DTO→domain map eder, domain modelleri saf `data class`.
- Hata **exception olarak fırlatılır**: repository Firebase exception'ını domain `ArchiveError`'a map edip `throw` eder (aşağı bkz.), ViewModel `runCatching`/`try-catch` ile yakalar — `try/catch` repository sınırında kalır, exception UI'ya çıplak sızmaz.
- Firebase ortak `vifi-831a8`: MEVCUT durum RTDB `Universitiess`; HEDEF named `vifi` Firestore (`catalogNodes`/`exams`) + Storage (roadmap `BE-15`). Repository arayüzü değişmeden impl RTDB→Firestore'a göç edebilir — soyutlamanın asıl faydası budur.

**İlgili:** Firestore/RTDB şeması, DTO map, realtime akış, `.await()`, offline persistence → `firebase-data.md`.

### Hata Modeli

> **ZORUNLU:** Her veri kaynağı için `sealed class <Name>Error(val userMessage: String) : Exception()` (paket `core/model/`), `userMessage` **inline Türkçe**. ViewModel yakaladığı hatayı `(throwable as? ArchiveError)?.userMessage ?: ArchiveError.Unknown.userMessage` ile `UiState.error: String?`'a koyar; UI yalnızca bu String'i gösterir, ham exception göstermez. (iOS'un Türkçe `userMessage`'lı error enum'unun karşılığı.)

```kotlin
package com.salticusteam.vifi.core.model

// userMessage is inline Turkish — a deliberate, narrow exception to the strings.xml rule
sealed class ArchiveError(val userMessage: String) : Exception() {
    data object Offline : ArchiveError("İnternet bağlantısı yok. Lütfen bağlantını kontrol et.")
    data object NotFound : ArchiveError("Aradığın sınav bulunamadı.")
    data object Empty : ArchiveError("Burada henüz içerik yok.")
    data object PermissionDenied : ArchiveError("Bu içeriğe erişim yetkin yok.")
    data object AppCheckRejected : ArchiveError("Doğrulama başarısız. Lütfen tekrar dene.")
    data object Unknown : ArchiveError("Bir şeyler ters gitti. Lütfen tekrar deneyin.")
}
```

`userMessage` **inline Türkçe string**'tir — uygulama Türkçe-only olduğundan `@StringRes` gerektirmeyen bilinçli ve **DAR** bir istisnadır; başka hiçbir kullanıcı metni koda gömülmez (tam kural `localization.md`'de).

---

## Test (kısa)

- **Birim test:** JUnit + `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`) + Turbine (Flow doğrulama). Repository yerine `Fake*Repository`; gerektiğinde MockK. (iOS `XCTest` karşılığı.)
- **UI test:** Compose test (`createAndroidComposeRule`, `onNodeWithTag`, `performClick`); `testTag`'ler UI testleriyle sözleşmedir — mevcutları değiştirme. (iOS `XCUITest` karşılığı.)

Tam kurallar ve şablonlar: `testing.md`, `skills/generate-unit-tests/`, `skills/generate-ui-tests/`.

---

## Build (kısa)

- Gradle (hedef: KTS + `gradle/libs.versions.toml` version catalog, Firebase BoM, AndroidX, `mavenCentral()`). iOS XcodeGen/pbxproj sorunlarının Android karşılığı **yoktur** — onun yerine Gradle/catalog kuralları geçerli (roadmap `FM-01`, `FM-02`, `FM-06`).
- Dil: Kotlin 2.0+, JDK 17. `applicationId` / paket: `com.salticusteam.vifi` (korunur — Firebase/Play kaydı buna bağlı).
- `versionName`: sürümlü doc comment'lerin referansı (yukarı bkz.); roadmap "versionName hizalama" kararına göre `2.5` → `3.x`.

---

**Ayrıca bkz.:**
- `firebase-data.md` — Firestore/RTDB şeması, DTO→domain map, realtime akış (`callbackFlow`/`.await()`), offline persistence
- `ui-components.md` — Compose bileşen konvansiyonları, tema, geri butonu, erişilebilirlik/testTag
- `viewmodel-rule.md` — ViewModel'ın tam sözleşmesi (StateFlow/Channel, scope, Context yasağı)
- `testing.md` — birim/UI test kuralları (runTest, Turbine, Compose test)
- `localization.md` — strings.xml, Türkçe-only, positional format args (`%1$s`)
- `date-handling.md` — backend tarih string dönüşümü
- `skills/new-screen/`, `skills/new-viewmodel/`, `skills/new-feature/`, `skills/new-list-item/` — scaffolding skill'leri
- `skills/generate-unit-tests/`, `skills/generate-ui-tests/`, `skills/generate-mocks/`, `skills/review-pr/`, `skills/figma-to-feature/`
- `commands/check-architecture.md`, `commands/document-module.md`, `commands/generate-mocks.md`, `commands/scaffold-feature.md`

---
