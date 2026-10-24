# ViFi Android — UI Bileşen Kuralları (Compose)

> **Kapsam:** Composable ekranlar, yeniden kullanılabilir bileşenler, liste öğeleri, tema/tasarım sistemi.
> **Globs:** `["**/*Screen.kt", "**/ui/**/*.kt", "**/designsystem/**/*.kt", "**/feature/**/*.kt", "**/components/**/*.kt"]`
> **Paket:** `com.salticusteam.vifi`

> **Legacy not:** Bugünkü v2.5 UI'ı klasik `Activity` + `Fragment` + XML layout (`activity_*.xml`, `fragment_*.xml`) ve elle yazılmış `ListView`/`RecyclerView` adapter'larıdır (`CustomAdapter`, `ListViewAdapter`). Bu dosya **hedef** (ileriye dönük) mimariyi anlatır: **Jetpack Compose + Material3 + MVVM**. Yeni ekranlar Compose ile yazılır; XML/Fragment **yalnızca** emekliye ayrılmayı bekleyen eski ekranlarda kalır (bkz. yol haritası `FM-09`, `FM-22`, `FM-23`). Ana içerik hedef mimaridir — yeni kod yazarken buna uy.

> **Kod düzeni notu (iOS `// MARK:` karşılığı yok):** Kotlin/Compose'da `// MARK:` kullanılmaz. Onun yerine ekranı **küçük, tek-sorumluluklu Composable'lara böl** (ör. `ExamListScreen` → `ExamListContent` → `ExamListItem`), mantıksal grupları gerektiğinde düz İngilizce yorumla (`// --- State ---`) ayır. Dosya içi önerilen sıra: (1) public Composable giriş noktası, (2) stateful/stateless ayrımı, (3) private alt-Composable'lar, (4) `@Preview`. Ayrıntı: `.claude/rules/architecture.md`.

---

## Tasarım Sistemi

Proje **Material3** tasarım sistemini kullanır. Renkler `core/designsystem/theme/Color.kt` + `core/designsystem/theme/ViFiTheme.kt`'teki `MaterialTheme.colorScheme`, tipografi `core/designsystem/theme/Type.kt`'teki `MaterialTheme.typography` üzerinden gelir. (iOS'taki SwiftGen `Colors` struct'ı + `FontFamily.Inter` karşılığı.)

### Renk Kullanımı

```kotlin
// MaterialTheme.colorScheme kullan — Color(0x...) değerini ASLA koda gömme.
Surface(color = MaterialTheme.colorScheme.background) { /* ... */ }

Text(
    text = exam.title,
    color = MaterialTheme.colorScheme.onSurface,
)

Button(
    onClick = onDownloadClick,
    colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,   // ViFi marka turuncusu
    ),
) { /* ... */ }
```

Marka renk token'ları `core/designsystem/theme/Color.kt`'te tanımlanır ve `ViFiTheme.kt`'teki `lightColorScheme(...)` / `darkColorScheme(...)` içine bağlanır:

```kotlin
// core/designsystem/theme/Color.kt — brand palette as named tokens (SwiftGen Colors karşılığı).
// Marka turuncusu = legacy colors.xml'deki colorAccent2 (#FB920F).
val VifiOrange       = Color(0xFFFB920F)   // primary / brand accent
val VifiOrangeDark   = Color(0xFFCC7400)
val VifiInk          = Color(0xFF17171C)   // primary text on light
val VifiGray         = Color(0xFF828297)   // secondary text
val VifiSeparator    = Color(0xFFE7EAF4)
val VifiSurfaceCard  = Color(0xFFF5F7FA)

// Koyu modda DEĞİŞMEMESİ gereken sabitler (iOS constantWhite / constantEsimBlack karşılığı).
// Bunlar colorScheme'e bağlanmaz; doğrudan referanslanır.
val VifiAlwaysWhite  = Color(0xFFFFFFFF)
val VifiAlwaysInk    = Color(0xFF17171C)
```

> **Kural:** Composable içinde `Color(0xFF...)` **yazma**. Tema renkleri için `MaterialTheme.colorScheme.*`, marka/sabit renkler için `Color.kt`'teki adlandırılmış token'ı kullan. `dynamicColor` **kapalıdır** (Material You, marka turuncusunu cihaz duvar kağıdına göre değiştirmesin) — bkz. `Dark Mode` bölümü.

### Tipografi

> **MANDATORY:** Metin fontlarını `MaterialTheme.typography.<style>` üzerinden ver. Composable'a elle `fontSize = 16.sp` + `fontWeight = ...` gömme (`Type.kt`'te tanımlı olmayan gerçek bir istisna değilse). Compose, `sp` birimini ve kullanıcının yazı tipi ölçeğini (`fontScale`) **otomatik** uygular — iOS'taki Dynamic Type / `configureScaledFont` elle kurulumunun karşılığı burada yerleşiktir.

```kotlin
// ✅ DOĞRU — typography ölçeğinden stil seç (boyuta göre).
Text(text = exam.title, style = MaterialTheme.typography.titleMedium)
Text(text = exam.lessonName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

// ❌ KAÇIN — koda gömülü boyut/ağırlık (ölçeklenmeyi ve tutarlılığı bozar).
Text(text = exam.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
```

**iOS `baseFont size → textStyle` tablosunun Material3 karşılığı** — uygun semantik stili boyuta göre seç, her şeye `bodyLarge` deme:

| Kullanım / yaklaşık boyut | Material3 `typography` stili |
|---|---|
| Çok küçük etiket (11–12) | `labelSmall` / `labelMedium` |
| Alt başlık, ikincil metin (13–14) | `bodySmall` / `labelLarge` |
| Gövde metni (15–16) | `bodyMedium` / `bodyLarge` |
| Liste öğesi başlığı (16–18) | `titleSmall` / `titleMedium` |
| Ekran bölüm başlığı (20–22) | `titleLarge` / `headlineSmall` |
| Büyük başlık (24–28) | `headlineMedium` / `headlineLarge` |
| Dev başlık (30+) | `displaySmall`+ |

Marka fontu (iOS'taki Inter karşılığı, varsa) `Type.kt`'te `FontFamily` olarak tanımlanır ve `Typography(...)` içindeki her stile uygulanır — ekran kodunda tek tek `fontFamily = ...` verilmez:

```kotlin
// core/designsystem/theme/Type.kt
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val VifiTypography = Typography(
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    bodyMedium  = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal,   fontSize = 14.sp),
    // ... diğer stiller
)
```

> **Yazı tipi ölçeğine (fontScale) dikkat:** Compose dinamik ölçeği otomatik uygular; bu iyi. Ama aşırı büyümede taşan/kesilen düzenlerden kaçınmak için sabit yükseklikli kapsayıcılarda metni `maxLines` + `overflow = TextOverflow.Ellipsis` ile sınırla; satır sayısına bağlı kritik düzenlerde `minLines` kullan. Fontu tamamen ölçeklenmez yapma (erişilebilirliği kırar) — yalnızca taşmayı yönet.

---

## Düzen (Layout) — Compose

> iOS'taki **SnapKit** programatik kısıtlarının karşılığı Compose'da **deklaratif layout**'tur: `Column`, `Row`, `Box`, `LazyColumn` + `Modifier`. XML `ConstraintLayout`, `RelativeLayout` veya SnapKit tarzı kısıt zincirleri yeni kodda **yoktur**.

```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    Text(text = exam.title, style = MaterialTheme.typography.titleMedium)
    Text(text = exam.lessonName, style = MaterialTheme.typography.bodyMedium)
}
```

- Boşluk/aralık `Modifier.padding(...)` ve `Arrangement.spacedBy(...)` ile verilir (sabit `dp`).
- Animasyon: `animate*AsState` / `AnimatedVisibility` / `Modifier.animateContentSize()` (iOS `UIView.animate` + `updateConstraints` karşılığı). Elle `layoutIfNeeded` yok.

---

## Yeniden Kullanılabilir Composable Deseni (iOS `LayoutableView` + delegate karşılığı)

iOS'ta özel görünümler `LayoutableView`'e uyar, `setupViews()/setupLayout()/setupAccessibilityIdentifiers()` uygular ve geri çağrıları `delegate` protokolüyle iletir. **Android karşılığı:** durumu dışarıdan alan (**state hoisting**), eylemleri **lambda** ile yukarı bildiren **stateless Composable**. Delegate protokolü yoktur — yerine `onXClick: () -> Unit` parametreleri.

```kotlin
// core/designsystem/component/ExamCard.kt
// Reusable, stateless component. State is hoisted; actions come back as lambdas
// (iOS delegate protocol → lambda parameters).
@Composable
fun ExamCard(
    exam: Exam,
    onOpenClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onOpenClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("examCard.${exam.id}"),   // UI test contract (bkz. Erişilebilirlik)
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = exam.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.exam_card_year, exam.year),   // "%1$d dönemi"
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenClick) {
                Text(text = stringResource(R.string.exam_card_open))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExamCardPreview() {
    ViFiTheme {
        ExamCard(exam = Exam.preview(), onOpenClick = {})
    }
}
```

**Kurallar:**
- Bileşen **stateless** olsun: kendi `remember` state'ini saklamasın (salt-UI geçici durum hariç, ör. genişleme/daralma). Veriyi parametreyle al, eylemi lambda ile bildir.
- Her public bileşenin son parametresi `modifier: Modifier = Modifier` olsun (çağıran dıştan boyut/padding/testTag verebilsin).
- `@Preview`'u **sahte/örnek veriyle** ekle (iOS'un setup üçlüsü + mock karşılığı). Preview verisi `Exam.preview()` gibi fabrika fonksiyonundan gelsin — gerçek Firebase'e dokunma.

---

## Ekranı Kurmak (Screen + ViewModel)

iOS'taki `LayoutingViewController` + `ViewType.create()` karşılığı: bir `@Composable fun XScreen(...)` giriş noktası, state'i `XViewModel`'den `collectAsStateWithLifecycle()` ile toplar, `Scaffold` + `TopAppBar`'ı kurar ve stateless içeriğe (`XContent`) devreder. (MVVM ayrıntısı: `.claude/rules/viewmodel-rule.md`.)

```kotlin
// feature/examlist/ExamListScreen.kt
@Composable
fun ExamListScreen(
    onBack: () -> Unit,
    onExamClick: (examId: String) -> Unit,
    viewModel: ExamListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,   // iOS "setupViews backgroundColor first" karşılığı
        topBar = {
            ViFiTopAppBar(
                title = stringResource(R.string.exam_list_title),
                onBack = onBack,                                  // ikon-only geri (bkz. Navigasyon Geri Butonu)
            )
        },
    ) { padding ->
        ExamListContent(
            uiState = uiState,
            onExamClick = onExamClick,
            onRetry = viewModel::retry,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

// Stateless content — previewlenebilir, test edilebilir (iOS View + delegate karşılığı).
@Composable
private fun ExamListContent(
    uiState: ExamListUiState,
    onExamClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        uiState.isLoading -> LoadingView(modifier)
        uiState.error != null -> ErrorView(message = uiState.error, onRetry = onRetry, modifier = modifier)
        uiState.exams.isEmpty() -> EmptyView(message = stringResource(R.string.exam_list_empty), modifier = modifier)
        else -> ExamList(exams = uiState.exams, onExamClick = onExamClick, modifier = modifier)
    }
}
```

> `uiState` TEK bir immutable `data class ExamListUiState(val isLoading: Boolean, val error: String?, val exams: List<Exam>)`'tir ve `ViewModel`'de `StateFlow` olarak yaşar. `error` alanı, `sealed class ArchiveError`'ın Türkçe `userMessage`'ından doldurulur (bkz. `.claude/rules/architecture.md` → Hatalar). Ekran yalnızca `error.userMessage`'ı gösterir.

---

## Listeler — `LazyColumn` (iOS CollectionView/Cell karşılığı)

iOS'ta kaydırılabilir içerik **her zaman** `UICollectionView` + hücredir ve hücre tipleri elle **register** edilir. **Android karşılığı:** `LazyColumn` (dikey liste) veya `LazyVerticalGrid` (ızgara). **Kayıt (register/dequeue) yoktur** — `items(...)` DSL'i reuse'u ve geri dönüşümü otomatik yapar. Liste öğesi, kendi dosyasında yeniden kullanılabilir bir Composable'dır (iOS Cell karşılığı; bkz. skill `new-list-item`).

```kotlin
@Composable
private fun ExamList(
    exams: List<Exam>,
    onExamClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.testTag("examList.list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = exams,
            key = { exam -> exam.id },        // kararlı key = iOS reuse identifier + diffing karşılığı
        ) { exam ->
            ExamCard(exam = exam, onOpenClick = { onExamClick(exam.id) })
        }
    }
}
```

**Kurallar / `Column` ne zaman?**
- **Değişken uzunlukta / dinamik liste** → her zaman `LazyColumn` / `LazyVerticalGrid` (geri dönüşüm + performans; iOS CollectionView zorunluluğunun karşılığı).
- `items(...)`'a **her zaman kararlı `key`** ver (genelde domain `id`). Key'siz liste yeniden sıralamada/animasyonda hatalı davranır — iOS'taki yanlış reuse hatalarının Android karşılığı.
- **Kısa, sabit sayıda (1–3) kart** içeren ekran → `Column(Modifier.verticalScroll(rememberScrollState()))` yeterli; tek öğeyi `LazyColumn`'a sarmaya gerek yok. Liste büyüyebilecekse `LazyColumn` seç.
- Liste öğesi mantığı (tıklama, görsel yükleme) öğenin **kendi Composable'ında** yaşar; ekran yalnızca veriyi ve `onClick` lambda'sını geçer. (iOS "hücre kaydı View'de, davranış VC'de" ayrımının karşılığı: öğe Composable'ı sunumu, ViewModel davranışı tutar.)

---

## Durum Görünümleri — Loading / Error / Empty

Her veri çeken ekran üç durumu (loading/error/empty) tutarlı gösterir (iOS network/empty/error desenlerinin karşılığı). Bu durumları yeniden kullanılabilir bileşenlerle göstermek **tercih edilir**; skill şablonları netlik için inline gösterebilir. Bu bileşenler `core/designsystem/component/`'de tek yerde tanımlanır.

```kotlin
// core/designsystem/component/StateViews.kt
@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier = modifier.testTag("state.loading"), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun ErrorView(
    message: String,                 // ArchiveError/AuthError.userMessage (zaten Türkçe)
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("state.error"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Button(onClick = onRetry) {
            Text(text = stringResource(R.string.common_retry))
        }
    }
}

@Composable
fun EmptyView(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("state.empty"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
```

> **Kural:** `ErrorView`'a asla ham `Exception.message` verme — `UiState.error` içindeki Türkçe `userMessage`'ı geç. Hata metni de dahil kullanıcıya görünen HER metin `strings.xml`'den gelir (`common_retry` vb.); bkz. `.claude/rules/localization.md`.

---

## Erişilebilirlik — `semantics` + `testTag`

iOS'ta her UI öğesi `accessibilityIdentifier` taşır. **Android'de iki ayrı kavram:**
- **`Modifier.testTag("...")`** → UI testleri için kararlı tanıtıcı (iOS accessibility identifier karşılığı). **UI testleriyle SÖZLEŞMEDİR** — var olan testTag'leri değiştirme/silme, testler kırılır.
- **`contentDescription`** (`Icon`/`Image` parametresi veya `Modifier.semantics { contentDescription = ... }`) → ekran okuyucu (TalkBack) için anlamlı Türkçe açıklama. Dekoratif öğelerde `contentDescription = null`.

```kotlin
// testTag adlandırma deseni: "<ekran>.<öğe>"
IconButton(
    onClick = onBack,
    modifier = Modifier.testTag("examList.backButton"),
) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = stringResource(R.string.cd_navigate_back),   // TalkBack için Türkçe
    )
}

// Dekoratif görsel — ekran okuyucuya bilgi taşımaz.
Image(painter = painterResource(R.drawable.ic_badge), contentDescription = null)
```

> **Kural:** Metin/eylem içeren her etkileşimli öğeye `testTag` + anlamlı `contentDescription` ver. İkonlu butonlarda `contentDescription` zorunlu (metin yok, TalkBack sadece bunu okur). testTag'ler `"<ekran>.<öğe>"` (örn. `"examDetail.downloadButton"`) biçiminde — ayrı bir "accessibility" klasörü **açma**, tanıtıcılar Composable'ın içinde yaşar.

---

## Dokunma Hedefleri (Touch Targets)

Her tıklanabilir öğenin dokunma alanı **en az 48dp × 48dp** olmalı (Material erişilebilirlik minimumu; iOS 44pt'nin karşılığı). Material3 `Button`/`IconButton` bunu zaten sağlar; özel tıklanabilir alanlarda elle garantile:

```kotlin
// ✅ Küçük ikon ama 48dp dokunma hedefi — IconButton bunu garanti eder.
IconButton(onClick = onFavoriteClick) {
    Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.exam_favorite))
}

// ✅ Özel clickable alan — minimum boyutu açıkça zorla.
Text(
    text = stringResource(R.string.exam_show_more),
    modifier = Modifier
        .clickable(onClick = onShowMore)
        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        .wrapContentSize(Alignment.Center),
)
```

---

## Sık Kullanılan UI Desenleri

### Gölge / Yükseklik (iOS shadow karşılığı)
```kotlin
// Kart gölgesi için Card elevation tercih edilir.
Card(elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) { /* ... */ }

// Kart dışı bir öğeye gölge:
Box(modifier = Modifier.shadow(elevation = 4.dp, shape = RoundedCornerShape(12.dp))) { /* ... */ }
```

### Köşe Yarıçapı + Kenarlık (iOS cornerRadius + border karşılığı)
```kotlin
Box(
    modifier = Modifier
        .clip(RoundedCornerShape(12.dp))
        .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = RoundedCornerShape(12.dp)),
) { /* ... */ }
```

### Gradyan Arka Plan (iOS CAGradientLayer karşılığı)
```kotlin
Box(
    modifier = Modifier.background(
        brush = Brush.linearGradient(
            colors = listOf(MaterialTheme.colorScheme.primary, VifiOrangeDark),
        ),
    ),
) { /* ... */ }
```

### Yükleme Göstergesi (iOS UIActivityIndicatorView karşılığı)
```kotlin
CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)   // tam ekran kullanımı için LoadingView
```

---

## Alt-Sabitli Eylem Butonu — Klavye Yönetimi (Mandatory)

> **MANDATORY:** Birincil eylem butonu (Devam / Kaydet / Gönder…) **alta sabitli** olan ve **metin girişi** (telefon no, SMS kodu, arama) içeren her ekran, bu butonu **klavyenin üstünde** tutmalıdır. iOS'taki `KeyboardObserving` + `keyboardWillChangeFrame` elle kurulumunun Android karşılığı çok daha basittir: tek Activity `android:windowSoftInputMode="adjustResize"` ile çalışır ve Compose'da **`Modifier.imePadding()`** IME yüksekliğini otomatik ekler. Klavye altında kalan buton = sevkiyat hatasıdır (kullanıcı doldurduğu formu gönderemez — iOS VerifyNumber bulgusunun karşılığı).

```kotlin
// AndroidManifest.xml — tek Activity:
// <activity android:name=".MainActivity" android:windowSoftInputMode="adjustResize" />

@Composable
fun VerifyNumberScreen(/* ... */) {
    Scaffold(/* topBar = ... */) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()                      // klavye açılınca içeriği yukarı iter
                .padding(horizontal = 20.dp),
        ) {
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                label = { Text(stringResource(R.string.verify_code_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("verifyNumber.codeField"),
            )

            Spacer(Modifier.weight(1f))            // butonu en alta iter

            Button(
                onClick = onContinueClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("verifyNumber.continueButton"),
            ) {
                Text(stringResource(R.string.common_continue))
            }
        }
    }
}
```

**Kurallar:**
- Edge-to-edge (`enableEdgeToEdge()`) kullanılıyorsa IME insets'ini `Modifier.imePadding()` ile yönet; `Scaffold`'ın `contentWindowInsets`'ini de hesaba kat.
- Butonu `Spacer(Modifier.weight(1f))` ile en alta sabitle; klavye açılınca `imePadding()` tüm bloğu birlikte yukarı kaydırır (iOS'ta footer + butonu birlikte kaydırmanın karşılığı).
- Bu, girişi `OutlinedTextField` olmayan ekranlara da uygulanır (OTP kutuları, arama çubukları).

> **Kanonik referanslar:** `VerifyNumberScreen` (SMS kod + Devam), giriş akışı ekranları (bkz. yol haritası `AUTH-05`/`AUTH-06`).

---

## Navigasyon Geri Butonu — `TopAppBar` navigationIcon, Asla Metinli/Elle Geri (Mandatory)

> **MANDATORY:** Geri gidilebilen (bir başka ekrandan `navigate` ile açılmış) her ekran, standart geri butonunu kullanır: **Material3 `TopAppBar`'ın `navigationIcon`'u içinde ikon-only geri oku** (`Icons.AutoMirrored.Filled.ArrowBack`) + `navController.popBackStack()`. **Metinli geri butonu (`< Geri`), `TextButton` ile elle geri, veya özel geri affordance'ı YASAK** (tasarım açıkça farklısını istemedikçe). iOS'taki `GlassBackBarButtonView` (ikon-only, hidesBackButton, `safePop`) kuralının bire bir karşılığıdır. **Bu varsayılandır; onay istemeden bağla.**

```kotlin
// core/designsystem/component/ViFiTopAppBar.kt
// Reusable top bar with the app's standard icon-only back button.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViFiTopAppBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("topBar.backButton"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,   // RTL'de otomatik döner
                    contentDescription = stringResource(R.string.cd_navigate_back),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    )
}
```

Geri eylemi NavHost seviyesinde `popBackStack()`'e bağlanır — ekran, geriyi lambda olarak alır (navigasyonu bilmez, test edilebilir kalır):

```kotlin
// core/navigation/ViFiNavHost.kt
composable(route = "examList") {
    ExamListScreen(
        onBack = { navController.popBackStack() },                 // metinli/elle geri yok
        onExamClick = { examId -> navController.navigate("examDetail/$examId") },
    )
}
```

**Kurallar:**
- Üst bar bileşeni (`ViFiTopAppBar`) `core/designsystem/component/`'de **tek yerde** yaşar; ekranların onu kullanması **tercih edilir** (skill şablonları netlik için inline `TopAppBar` gösterebilir) — tutarlılık ve RTL/koyu mod/erişilebilirlik tek noktadan gelir (iOS'ta hand-rolled butonun dört özelliği kaybetmesinin karşılığı).
- Geri oku `Icons.AutoMirrored.Filled.ArrowBack` olsun (RTL dilde otomatik ters döner). `contentDescription` Türkçe (`R.string.cd_navigate_back`).
- **Modal / bottom sheet** (`ModalBottomSheet`) ekranda geri oku değil, başlıkta **kapat "✕"** (`Icons.Filled.Close`, `onDismiss`) kullanılır — sheet'in doğal kapatma affordance'ı budur (iOS `closeButton` karşılığı):

```kotlin
IconButton(onClick = onDismiss, modifier = Modifier.testTag("sheet.closeButton")) {
    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close))
}
```

- **Çift navigasyon koruması:** Hızlı çift tıklamada iki kez pop/navigate olmasın. `popBackStack()`'i doğrudan çağır (idempotent'e yakındır) veya navigasyonu `navController.currentBackStackEntry?.lifecycleIsResumed()` ile gard et. Navigasyon deseni ayrıntısı: `.claude/rules/architecture.md` → Navigation.
- **Meşru istisnalar:** Bir sekme/kök (root) ekranı (geri oku yok — `TopAppBar` navigationIcon'suz), bir modal/sheet (kapat "✕"), veya tasarımın **açıkça** başka affordance belirttiği ekran. Bunların dışında: ikon-only geri oku.

**Neden:**
- Geri butonu uygulamanın en çok dokunulan kontrolü; bir ekranın farklı/metinli geri butonu kullanması anında tutarsızlık olarak görülür (iOS'ta aynı bulgu raporlandı).
- Merkezi `ViFiTopAppBar`; RTL ok çevirme, koyu mod renkleri, Türkçe `cd_navigate_back` erişilebilirlik etiketini tek yerde taşır — elle buton bunların hepsini sessizce kaybeder.

> **Kanonik referanslar:** `core/designsystem/component/ViFiTopAppBar.kt`, `core/navigation/ViFiNavHost.kt`. Yeni ekran iskeleti için skill `new-screen`.

---

## Composable Yaşam Döngüsü & Durum

> **MANDATORY:** Ekranın en dış kapsayıcısı arka plan rengini açıkça belirtir (iOS "setupViews ilk satırı backgroundColor" kuralının karşılığı). Varsayılan `Scaffold(containerColor = MaterialTheme.colorScheme.background)`; stilli bir kart/kapsayıcı içinde yaşayan bileşen arka planı **`Color.Transparent`** bırakır (altındaki kart rengi görünsün, köşe/gölge doğru olsun).

> **MANDATORY:** Yan etkileri (veri çekme, dinleyici, snackbar) doğru **key**'li `LaunchedEffect`/`DisposableEffect` içinde çalıştır — `Composable` gövdesinde serbest yan etki **yok** (recomposition'da tekrar çalışır). Kaynak gerektiren dinleyiciyi `DisposableEffect`'in `onDispose`'unda temizle (iOS retain-cycle/`[weak self]` hijyeninin karşılığı).

```kotlin
// ❌ YAPMA — gövdede serbest yan etki (her recomposition'da tetiklenir).
@Composable
fun ExamDetailScreen(viewModel: ExamDetailViewModel) {
    viewModel.load()        // yanlış
}

// ✅ YAP — key'li LaunchedEffect (ekran ilk göründüğünde / id değiştiğinde bir kez).
@Composable
fun ExamDetailScreen(examId: String, viewModel: ExamDetailViewModel = hiltViewModel()) {
    LaunchedEffect(examId) {
        viewModel.load(examId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // ...
}

// ✅ Tek seferlik olay (navigate/snackbar) — Channel/SharedFlow'u güvenli topla.
LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
        when (event) {
            is ExamDetailEvent.NavigateBack -> onBack()
            is ExamDetailEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
        }
    }
}
```

**Bellek/yaşam döngüsü kuralları (iOS `[weak self]` / retain cycle karşılığı):**
- Uzun süren işler `viewModelScope`'ta başlar (ViewModel ölünce iptal; sızıntı yok). Composable içinde başlatılan iş `LaunchedEffect`'in coroutine scope'undadır (composable ekrandan çıkınca iptal).
- Firebase RTDB/Firestore dinleyicileri repository'de `callbackFlow { ... awaitClose { ref.removeEventListener(l) } }` ile kurulur — listener `awaitClose`'da kaldırılır (bkz. `.claude/rules/firebase-data.md`). Composable bu Flow'u `collectAsStateWithLifecycle()` ile toplar; ekran durunca toplama durur.
- **`Context`'i ViewModel'de tutma** (Activity sızıntısı). Gereken `ApplicationContext` Hilt ile `@ApplicationContext` olarak enjekte edilir.
- `collectAsStateWithLifecycle()` kullan (`collectAsState` değil) — arka planda gereksiz toplamayı durdurur.

```kotlin
// Önerilen Composable iskeleti:
@Composable
fun ExamListScreen(/* nav lambdas */, viewModel: ExamListViewModel = hiltViewModel()) {
    // 1) State'i yaşam-döngüsü-farkında topla
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // 2) Yan etkiler (yükleme, olaylar) key'li effect'lerde
    LaunchedEffect(Unit) { /* events */ }
    // 3) Scaffold + arka plan + TopAppBar
    // 4) Stateless content'e devret
}
```

---

## Koyu Mod (Dark Mode)

```kotlin
// core/designsystem/theme/ViFiTheme.kt — tek tema girişi; light/dark colorScheme, dynamicColor KAPALI.
@Composable
fun ViFiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) VifiDarkColors else VifiLightColors   // dynamicColor kullanma
    MaterialTheme(
        colorScheme = colorScheme,
        typography = VifiTypography,
        content = content,
    )
}
```

> **Karar (2026-10-24):** Uygulama **hem açık hem karanlık temayı** tam destekler ve **sistem ayarını izler** (`isSystemInDarkTheme()`; uygulama içi tema seçici yok). iOS karanlık temayı kaldırmıştı; Android bilinçli olarak ayrışır.

- Tema renklerini (`MaterialTheme.colorScheme.*`) kullanan her şey koyu moda otomatik uyum sağlar. Yeni ekran/bileşen **her iki temada** okunur olmalı; ana ekranlar `@ViFiThemePreviews` (açık + koyu önizleme, `core/designsystem/theme/ViFiThemePreviews.kt`) kullanır.
- `VifiDarkColors` **tam** tanımlıdır: Material3 baseline'ın mor nötrleri/konteynerleri yerine sıcak nötr yüzeyler (`surfaceContainer*` rampası) ve turuncu tonlu konteynerler. Rol eklerken/değiştirirken WCAG AA'yı koru — `VifiColorContrastTest` (birim test) metin/arka plan çiftlerini ≥ 4.5:1 doğrular.
- Kart dolgusu `surfaceVariant` / `surfaceContainer*`; ayırıcı `outlineVariant`; ikincil metin `onSurfaceVariant`. Alfa ile türetilmiş tint (`primary.copy(alpha = 0.14f)`) serbest — tema rengi üzerinden olduğu için iki temada da çalışır.
- Koyu modda **değişmemesi** gereken öğeler için `Color.kt`'teki sabit token'ı doğrudan kullan (`VifiAlwaysBlack`, `VifiAlwaysWhite`) — colorScheme üzerinden değil (iOS `constantWhite` karşılığı). Bilinçli sabitler: tam ekran görüntüleyici (siyah zemin + beyaz kontroller), PDF sayfası "kâğıdı" (beyaz), turuncu üstündeki siyah metin.
- Pencere teması da temayı izler: `res/values/styles.xml` açık, `res/values-night/styles.xml` koyu (`window_background_dark` = `VifiDarkColors.background`) — Android 12+ splash ve Compose öncesi pencere beyaz parlamaz. Sistem çubuğu ikonları `enableEdgeToEdge()` (auto) ile sistem temasını izler.
- `dynamicColor` **kapalı** tutulur: marka turuncusu (`VifiOrange` açık / `VifiOrangeOnDark` koyu) her cihazda sabit kalmalı.

---

## Kurallar Özeti

1. ✅ **KULLAN** `MaterialTheme.colorScheme.*` (tema) / `Color.kt` token'ları (marka/sabit) — `Color(0xFF...)` koda gömme.
2. ✅ **KULLAN** `MaterialTheme.typography.<style>` — elle `fontSize`/`fontWeight` gömme.
3. ✅ **KUR** düzeni `Column`/`Row`/`Box`/`LazyColumn` + `Modifier` ile (SnapKit/XML kısıt zinciri yok).
4. ✅ **YAZ** bileşenleri **stateless + state hoisting**; eylemleri `onXClick: () -> Unit` lambda'larıyla bildir (delegate yok).
5. ✅ **EKLE** her public Composable'a son parametre `modifier: Modifier = Modifier`.
6. ✅ **KULLAN** değişken listeler için `LazyColumn`/`LazyVerticalGrid`; `items(...)`'a **kararlı `key`** ver.
7. ✅ **GÖSTER** Loading/Error/Empty durumlarını tutarlı; yeniden kullanılabilir `LoadingView`/`ErrorView`/`EmptyView` **tercih edilir** (skill şablonları netlik için inline gösterebilir); `ErrorView` metni `error.userMessage` (Türkçe).
8. ✅ **VER** her etkileşimli öğeye `testTag("<ekran>.<öğe>")` + anlamlı Türkçe `contentDescription`.
9. ✅ **KORU** mevcut `testTag`'leri (UI testleriyle sözleşme) — değiştirme/silme.
10. ✅ **GARANTİLE** en az 48dp dokunma hedefi (`IconButton`/`Button` veya `sizeIn(minWidth=48.dp, minHeight=48.dp)`).
11. ✅ **TUT** alt-sabitli eylem butonunu klavye üstünde (`Modifier.imePadding()` + `adjustResize`).
12. ✅ **KULLAN** geri butonu için ikon-only geri oku + `navController.popBackStack()` (`ViFiTopAppBar` **tercih edilir**; skill şablonları netlik için inline gösterebilir) — metinli/elle geri YOK; modal/sheet'te kapat "✕".
13. ✅ **ÇALIŞTIR** yan etkileri key'li `LaunchedEffect`/`DisposableEffect`'te; dinleyiciyi `onDispose`/`awaitClose`'da temizle.
14. ✅ **TOPLA** state'i `collectAsStateWithLifecycle()` ile; `Context`'i ViewModel'de tutma.
15. ✅ **AL** kullanıcıya görünen HER metni `stringResource(R.string.x)`'ten; sabit string gömme (bkz. `localization.md`).
16. ✅ **BELİRT** en dış kapsayıcının arka plan rengini (`Scaffold containerColor` ya da `Modifier.background`); stilli kapsayıcı içinde `Color.Transparent`.
17. ❌ **ASLA** yeni ekranda Fragment/XML layout/`findViewById`/elle `RecyclerView.Adapter` kullanma (legacy istisna).
18. ❌ **ASLA** `dynamicColor` açma (marka turuncusu sabit) — ve koyu modda değişmemesi gereken renge `colorScheme` kullanma. Uygulama açık + karanlık temayı sistemle birlikte destekler; yeni UI her iki temada doğrulanır.
19. ❌ **ASLA** `DateFormatter` karşılığını Composable içinde kurma; tarih biçimleme `.claude/rules/date-handling.md`'deki yardımcılardan gelir.
20. 📝 **VERSİYONLU DOC YORUMU:** Var olan bir model/`data class`'a eklenen her alan ve `enum`/`sealed`'e eklenen her case `// Added in <versionName> — <amaç>.` taşır (versiyon = `app/build.gradle` `versionName`), ör. `// Added in 3.0.0 — exam favorite toggle için.`

---

**Ayrıca bakınız:**
- `.claude/rules/architecture.md` — Genel mimari, MVVM, navigasyon, hata deseni (`ArchiveError`/`AuthError`)
- `.claude/rules/viewmodel-rule.md` — `ViewModel` + `UiState` + `StateFlow` deseni
- `.claude/rules/firebase-data.md` — Repository + `callbackFlow` + DTO→domain map (ham Firebase tipi UI'ya sızmaz)
- `.claude/rules/localization.md` — `strings.xml`, `stringResource`, positional format args (`%1$s`)
- `.claude/rules/date-handling.md` — Backend tarih string dönüşümü
- `.claude/rules/testing.md` — Compose UI testi (`createAndroidComposeRule`, `onNodeWithTag`) + birim test
- `core/designsystem/theme/Color.kt` — Marka ve sabit renk token'ları
- `core/designsystem/theme/Type.kt` — Tipografi ölçeği
- `core/designsystem/theme/ViFiTheme.kt` — `ViFiTheme`, light/dark colorScheme
- Skill `new-screen` — Yeni Compose ekranı iskeleti · Skill `new-list-item` — Liste öğesi Composable'ı · Command `scaffold-feature` — Özellik iskeleti
