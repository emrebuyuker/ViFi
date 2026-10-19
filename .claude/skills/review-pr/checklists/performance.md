# ViFi-Android — Performans Checklist (Kritik)

Bu sorunlar doğrudan Play Store puanını, ANR/crash oranını ve kullanıcı tutundurmasını etkiler.

> **Legacy not:** Bugünkü v2.5 (Java + `Activity`/`Fragment` + `ListView`/`CustomAdapter` + RTDB `Universitiess` doğrudan UI'da) bu maddelerin çoğunu ihlal eder; PR'da değişen legacy dosyayı hedef mimari performans ihlaliyle değil göç kalemi olarak işaretle (`ANDROID-PARITY-ROADMAP.md`). Yeni Kotlin + Compose koduna tam performans denetimi uygulanır.

## Ana Thread Güvenliği

### ✅ Gerekli
- [ ] **Ana thread'de blocking IO yok**: Firestore/RTDB/Storage, dosya I/O, JSON parse, görüntü işleme `Dispatchers.IO` altında
- [ ] IO/Firebase işi repository'de `withContext(Dispatchers.IO)`; `Task` → `.await()`
- [ ] Senkron ağ çağrısı yok (coroutine + `await()`/`callbackFlow`)
- [ ] Ağır hesap ana thread'de değil; sonuç `_uiState.update { ... }` ile UI'ya taşınır
- [ ] Composable gövdesinde (recomposition'da çalışan kodda) IO/ağ/dosya işi yok — effect/ViewModel'e taşınır

### ❌ Kritik Sorunlar
```kotlin
// ❌ CRITICAL — blocking on the main thread (ANR risk)
fun loadExams() {
    val snapshot = Tasks.await(firestore.collection("exams").get()) // blocks the main thread
    _uiState.update { it.copy(exams = snapshot.toExams()) }
}

// ❌ CRITICAL — IO in the Composable body (runs on every recomposition)
@Composable
fun ExamListContent(lessonId: String) {
    val exams = runBlocking { repository.getExams(lessonId) } // wrong layer + blocking
}

// ✅ CORRECT — work in viewModelScope + Dispatchers.IO; UI only reads state
fun loadExams() {
    viewModelScope.launch {
        val exams = withContext(Dispatchers.IO) { archiveRepository.getExams(lessonId) }
        _uiState.update { it.copy(isLoading = false, exams = exams) }
    }
}
```

## Uygulama Başlangıç Performansı

### ✅ Gerekli
- [ ] `Application.onCreate()` / ilk `Activity`'de ağır başlatma yok
- [ ] İlk Composable / `init` bloğu senkron ağ çağrısı veya ağır hesap yapmaz
- [ ] Hilt singleton'lar ağır kaynağı tembel (`lazy` / ilk erişimde) oluşturur
- [ ] Firebase/analytics başlatması başlangıcı bloklamaz
- [ ] Startup için gereksiz eager init `androidx.startup` / tembelleştirme ile ertelenir

### ❌ Kritik Sorunlar
```kotlin
// ❌ CRITICAL — heavy app startup
class ViFiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        archiveRepository.preloadEverything()  // blocks startup
        imageCache.warmUpSync()                // blocks startup
    }
}

// ✅ CORRECT — minimal setup; defer / lazy-load heavy work
class ViFiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // only lightweight setup (Hilt, crash reporting init)
    }
}
```

## Compose Recomposition (Kritik)

### ✅ Gerekli
- [ ] Composable parametreleri **stable** (immutable `data class`, `List` yerine okunan tarafta stabil tip); gerekiyorsa `@Immutable`/`@Stable`
- [ ] Pahalı hesap `remember(key)` ile önbelleğe alınır; türetilmiş state `derivedStateOf` ile
- [ ] Lambda'lar gereksiz yeniden oluşturulmaz (`remember` / method reference `viewModel::onExamClick`)
- [ ] State okuma mümkün olan en dar scope'a itilir (gereksiz geniş recomposition yok)
- [ ] `collectAsStateWithLifecycle()` kullanılır (arka planda recomposition tetiklenmez)
- [ ] Değişmeyen büyük veri Composable içinde yeniden üretilmez (ör. her frame'de `SimpleDateFormat`/`List` kurma yok)

### ❌ Kritik Sorunlar
```kotlin
// ❌ CRITICAL — expensive work / new allocation on every recomposition
@Composable
fun ExamRow(exam: Exam) {
    val formatted = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) // new on every recomposition
        .format(Date(exam.uploadedAt))
    val sorted = exam.tags.sorted()                                     // new list on every recomposition
    Text(formatted)
}

// ✅ CORRECT — expensive work remembered / derived; date from the central util (see date-handling.md)
@Composable
fun ExamRow(exam: Exam) {
    val formatted = remember(exam.uploadedAt) { exam.uploadedAt.toDisplayDate() }
    val sorted = remember(exam.tags) { exam.tags.sorted() }
    Text(formatted)
}

// ❌ CRITICAL — unstable parameter forces unnecessary recomposition of the whole row
@Composable fun ExamList(exams: MutableList<Exam>) { }  // MutableList unstable

// ✅ CORRECT — immutable List + immutable data class
@Composable fun ExamList(exams: List<Exam>) { }
```

### derivedStateOf ile türetilmiş state
```kotlin
// ✅ CORRECT — "scroll to top" visibility derived from scroll position does not recompose every frame
val showScrollToTop by remember {
    derivedStateOf { listState.firstVisibleItemIndex > 5 }
}
```

## Bellek & Yaşam Döngüsü

### ✅ Gerekli
- [ ] Tüm coroutine `viewModelScope`/`rememberCoroutineScope` içinde (`GlobalScope` yok)
- [ ] `callbackFlow` dinleyicisi `awaitClose { registration.remove() }` ile kaldırılır
- [ ] `LaunchedEffect`/`DisposableEffect` doğru key'lerle; `DisposableEffect` `onDispose`'da temizler
- [ ] ViewModel'de `Context`/`Activity`/`View`/`Bitmap` tutulmaz (`@ApplicationContext` istisnası)
- [ ] Büyük nesneler (Bitmap, ByteArray) iş bitince bırakılır; Coil cache'i yönetir

### ❌ Kritik Sorunlar
```kotlin
// ❌ CRITICAL — listener leak (no awaitClose)
fun observeExams(lessonId: String): Flow<List<Exam>> = callbackFlow {
    firestore.collection("exams").addSnapshotListener { s, _ -> trySend(s.toExams()) }
    // no awaitClose → the listener stays when the flow is cancelled
}

// ✅ CORRECT
fun observeExams(lessonId: String): Flow<List<Exam>> = callbackFlow {
    val registration = firestore.collection("exams")
        .addSnapshotListener { s, _ -> trySend(s?.toExams().orEmpty()) }
    awaitClose { registration.remove() }
}.flowOn(Dispatchers.IO)

// ❌ CRITICAL — GlobalScope (non-cancelable, independent of the VM lifetime → leaks)
GlobalScope.launch { archiveRepository.getExams(lessonId) }
```

## Ağ & Önbellek

### ✅ Gerekli
- [ ] Gereksiz tekrar çağrı yok; gerektiğinde önbellek/`StateFlow` paylaşımı (`stateIn(WhileSubscribed)`)
- [ ] Büyük listeler için sayfalama (Paging 3 veya manuel `loadMore` + `canLoadMore`)
- [ ] Coil image cache (memory + disk) kullanılır
- [ ] Firestore offline persistence uygun yerde açık
- [ ] Polling yok (push / realtime `callbackFlow` tercih)

### ❌ Kritik Sorunlar
```kotlin
// ❌ CRITICAL — reload on every screen entry (no cache)
LaunchedEffect(Unit) { viewModel.loadExams() }  // full fetch on every navigation return

// ✅ CORRECT — share the realtime flow with stateIn; serve from cache instantly on re-subscription
val uiState: StateFlow<ExamListUiState> =
    archiveRepository.observeExams(lessonId)
        .map { ExamListUiState(exams = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExamListUiState(isLoading = true))
```

### Sayfalama (manuel desen)
```kotlin
// trigger pagination when nearing the end of the LazyColumn
val shouldLoadMore by remember {
    derivedStateOf {
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        last >= uiState.exams.size - 5
    }
}
LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) viewModel.loadMore() }
```

## Liste Performansı (LazyColumn / LazyVerticalGrid)

### ✅ Gerekli
- [ ] `items(items, key = { it.id })` ile **stabil key** (recycle + animasyon + state korunması)
- [ ] Heterojen listede `contentType` verilir (`items(..., contentType = { it.type })`)
- [ ] Satır Composable'ı stateless + stable parametre alır; içinde IO/ağ yok
- [ ] Satır içinde pahalı hesap `remember` ile; ağır dönüşüm ViewModel'de önceden yapılır
- [ ] Liste/kayan içerik `LazyColumn`/`LazyVerticalGrid` ile (uzun/dinamik için `Column` + `verticalScroll` yok)

### ❌ Kritik Sorunlar
```kotlin
// ❌ CRITICAL — no key (recycling breaks, state gets mixed up, animation skips)
LazyColumn { items(uiState.exams) { exam -> ExamListItem(exam) } }

// ✅ CORRECT — stable key (+ contentType for heterogeneous lists)
LazyColumn {
    items(items = uiState.exams, key = { it.id }, contentType = { it.type }) { exam ->
        ExamListItem(exam = exam, onClick = { onExamClick(exam) })
    }
}

// ❌ CRITICAL — heavy work in the row (parse/sort on every bind)
@Composable fun ExamListItem(exam: Exam) {
    val parsed = parseComplexMetadata(exam.rawJson) // heavy → do it ahead of time in the ViewModel
    Text(parsed.title)
}

// ❌ CRITICAL — long list with Column + verticalScroll (no recycling, everything is composed)
Column(Modifier.verticalScroll(rememberScrollState())) {
    uiState.exams.forEach { ExamListItem(it) }
}
```

## Görüntü Performansı (Coil)

### ✅ Gerekli
- [ ] Görüntüler Coil (`AsyncImage` / `coil-compose`) ile asenkron yüklenir
- [ ] Hedef boyuta göre yükleme: thumbnail için tam çözünürlük indirilmez (`.size(...)` / `ContentScale` + sınırlı `Modifier.size`)
- [ ] `placeholder` / `error` görseli verilir
- [ ] `contentScale` doğru (`ContentScale.Crop` kart, `Fit` önizleme)
- [ ] Senkron görüntü yükleme (ör. `BitmapFactory.decodeFile` ana thread'de) yok
- [ ] Dekod/ölçekleme Coil'e bırakılır; elle büyük Bitmap tutulmaz

### ❌ Kritik Sorunlar
```kotlin
// ❌ CRITICAL — synchronous load / full-size Bitmap on the main thread
val bitmap = BitmapFactory.decodeFile(exam.localPath) // blocks + bloats memory
Image(bitmap.asImageBitmap(), contentDescription = null)

// ✅ CORRECT — Coil, sized to the target, with a placeholder
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(exam.previewUrl)
        .size(coil.size.Size(width = 320, height = 180)) // thumbnail size
        .crossfade(true)
        .build(),
    placeholder = painterResource(R.drawable.ic_exam_placeholder),
    error = painterResource(R.drawable.ic_exam_placeholder),
    contentDescription = stringResource(R.string.cd_exam_preview),
    contentScale = ContentScale.Crop,
    modifier = Modifier.size(width = 160.dp, height = 90.dp).testTag("examList.itemImage"),
)
```

## Gereksiz Allocation

### ✅ Gerekli
- [ ] Composable gövdesinde her recomposition'da yeni `List`/`Map`/`Modifier` zinciri/`Lambda`/`Formatter` kurulmaz (gerekiyorsa `remember`)
- [ ] Sabit nesneler Composable dışında (`private val` / `companion object`) tanımlanır
- [ ] Hot path'te (liste satırı, scroll) autoboxing / gereksiz kopya yok
- [ ] `stringResource` dışında metin kurma minimumda; string birleştirme döngüde değil

### ❌ Kritik Sorunlar
```kotlin
// ❌ CRITICAL — new allocation on every recomposition
@Composable fun ExamRow(exam: Exam) {
    val colors = listOf(Color.Red, Color.Green)          // new list each time
    val modifier = Modifier.padding(8.dp).fillMaxWidth() // rebuilt each time
    Text(exam.title, modifier = modifier)
}

// ✅ CORRECT — constants outside / remember; modifier as a parameter when needed
private val rowPadding = 8.dp
@Composable fun ExamRow(exam: Exam, modifier: Modifier = Modifier) {
    Text(exam.title, modifier = modifier.padding(rowPadding).fillMaxWidth())
}
```

## Animasyon Performansı

### ✅ Gerekli
- [ ] `animate*AsState` / `AnimatedVisibility` / `Animatable` kullanılır (elle frame loop yok)
- [ ] Scroll sırasında ağır animasyon tetiklenmez
- [ ] Animasyon layout yerine çizim/`graphicsLayer` üzerinden (alpha/scale/translation)
- [ ] Süreler makul (< ~300ms) ve `MaterialTheme`/motion ile tutarlı

```kotlin
// ✅ CORRECT — cheap animation via graphicsLayer
val scale by animateFloatAsState(if (pressed) 1.05f else 1f, label = "press")
Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
```

## İzleme & Metrikler

### Araçlar
- [ ] Android Studio Profiler (CPU, Memory, Energy)
- [ ] Layout Inspector + **Recomposition Counts** (gereksiz recomposition tespiti)
- [ ] LeakCanary (bellek sızıntısı)
- [ ] `./gradlew lint` + Compose compiler metrics (stability raporu)
- [ ] Firebase Performance Monitoring / Crashlytics
- [ ] StrictMode (debug: ana thread'de disk/ağ tespiti)

### Hedef Metrikler
- **Soğuk başlangıç**: < 2 sn
- **Bellek**: tipik kullanımda < 200MB
- **Jank**: 16ms frame bütçesi (ağır işi frame dışına taşı)
- **ANR**: ana thread'de 5 sn+ blocking → asla

## Performans Testi

```kotlin
// Startup/scroll measurement with Macrobenchmark (separate :macrobenchmark module)
@Test
fun scrollExamList() = benchmarkRule.measureRepeated(
    packageName = "com.salticusteam.vifi",
    metrics = listOf(FrameTimingMetric()),
    iterations = 5,
    startupMode = StartupMode.WARM,
) {
    // scroll via testTag (contract: examList.list)
}
```

## Kritik Performans Kuralları — Özet

### 🔴 Merge Öncesi Her Zaman Düzelt
1. ✅ Ana thread'de blocking IO yok (`Dispatchers.IO` + `await()`/`callbackFlow`)
2. ✅ `callbackFlow` dinleyicisi `awaitClose` ile kaldırılır
3. ✅ Tüm coroutine `viewModelScope`/`rememberCoroutineScope`'ta (`GlobalScope` yok)
4. ✅ `LazyColumn`/`LazyVerticalGrid`'de stabil `key`
5. ✅ Composable gövdesinde pahalı iş/allocation yok (`remember`/`derivedStateOf`)
6. ✅ Stable parametre (immutable `data class` + `List`); `collectAsStateWithLifecycle()`
7. ✅ Coil ile asenkron + boyutlu görüntü yükleme
8. ✅ Büyük listeler için sayfalama

### Performans Checklist Şablonu
```markdown
## Performans Review
- [ ] Ana thread'de IO yok (Dispatchers.IO)
- [ ] callbackFlow awaitClose ile listener kaldırıyor
- [ ] GlobalScope yok (viewModelScope / rememberCoroutineScope)
- [ ] LazyColumn/LazyVerticalGrid stabil key (+ gerekliyse contentType)
- [ ] Composable'da remember/derivedStateOf (gereksiz recomposition yok)
- [ ] Stable parametreler + collectAsStateWithLifecycle
- [ ] Coil asenkron + boyutlu image (thumbnail için 4K yok)
- [ ] Gereksiz allocation yok (hot path)
- [ ] Sayfalama (büyük liste)
- [ ] LeakCanary / Profiler ile doğrulandı
```

---

**Ayrıca bkz.:**
- `checklists/architecture.md` — Tam mimari/UI/bellek/test checklist'i
- `checklists/output-format.md` — Review çıktı formatı
- `SKILL.md` — PR review akışı
- `.claude/rules/ui-components.md` — Compose UI (LazyColumn, Coil, tema)
- `.claude/rules/firebase-data.md` — Repository + callbackFlow + Dispatchers.IO
- `.claude/rules/date-handling.md` — merkezi tarih util (inline formatter yerine)
