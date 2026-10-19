---
name: figma-to-feature
description: Figma REST API üzerinden bir frame'i okuyup ViFi-Android hedef mimarisine (stateful Screen + stateless Content + @HiltViewModel + StateFlow<UiState> + Navigation-Compose + LazyColumn, Jetpack Compose + Material3) uygun yeni bir feature modülü olarak feature/<ad>/ altında oluşturur. Figma Dev Mode MCP gerektirmez — Figma free planında çalışır, yalnızca Personal Access Token ister. Kullanıcı "Figma'dan oluştur", "Figma URL'inden ekran yap", "şu Figma frame'ini Compose'a çevir" gibi şeyler dediğinde tetiklenir.
---

> **Legacy not:** Bugünkü Android (v2.5) karşılığı bir ekran = `Activity`/`Fragment` + `activity_*.xml`/`fragment_*.xml` layout'u + elle yazılmış `CustomAdapter`/`ListViewAdapter` + Fragment içinde doğrudan RTDB `Universitiess` okumasıdır (ör. `CourseExamListActivity`, `LessonFragment`). Bu skill **yeni/ileriye dönük** ekranlar içindir ve **hedef** mimariyi (Compose + MVVM + Hilt + named `vifi` Firestore) üretir. Figma'dan çevrilen hiçbir ekran `Activity`/`Fragment`/XML olmaz. Legacy ekranların Compose'a göçü yol haritasında izlenir; bkz. repo kökü `ANDROID-PARITY-ROADMAP.md`.

> ⚠️ **Typography Rule (Zorunlu):** Figma'dan koda çevirirken her `Text` composable'ının fontunu `style = MaterialTheme.typography.<style>` ile ata. **Asla** `fontSize = 16.sp` / elle `TextStyle(fontSize = ...)` Composable içine gömme.
>
> - **`textStyle`'ı Figma `style.fontSize`'a göre seç** — `labelSmall/labelMedium` (~11–12), `bodySmall/labelLarge` (~13–14), `bodyMedium/bodyLarge` (~15–16), `titleSmall/titleMedium` (~16–18), `titleLarge/headlineSmall` (~20–22), `headlineMedium/headlineLarge` (~24–28), `displaySmall`+ (30+). Her şeye `bodyMedium` yazma.
> - **Compose yazı tipi ölçeğini (`fontScale`) otomatik uygular** — iOS'taki `configureScaledFont(maximumPointSize:)` elle kurulumunun karşılığı burada yerleşiktir; kendi `sp` sabitini yazma. Figma boyutu hiçbir Material3 stiline oturmuyorsa (nadir): `core/designsystem/Type.kt`'e yeni bir stil ekle, Composable içine inline `sp` gömme.
>
> Canonical reference: `.claude/rules/ui-components.md` (Tipografi).

> ⚠️ **Background Rule (Zorunlu):** Tam ekran bir feature'ın kökü `Scaffold(containerColor = MaterialTheme.colorScheme.background)` (ya da `Surface`) kullanır — iOS'taki "`setupViews()` ilk satırı `backgroundColor`" kuralının karşılığı. Styled bir kart/`LazyColumn` item'ı içinde host edilen bir alt-composable, parent arka planının görünmesi için kendi background'unu **set etmez** (gerekliyse `Color.Transparent`), opak overlay oluşturma. Canonical reference: `.claude/rules/ui-components.md` (Composable Yaşam Döngüsü & Durum).

> ⚠️ **Stateless Content Rule (Zorunlu):** iOS'taki "subview ownership / `addSubview` nerede" kuralının Compose karşılığı **Composable ayrıştırmasıdır**. UI iki katmana bölünür: **stateful** `<FeatureName>Screen` (`hiltViewModel()` burada çağrılır, state burada toplanır, event burada dinlenir) ve **stateless** `<FeatureName>Content` (sadece `uiState` + lambda parametreleri alır, `@Preview`lenebilir). Ekranı küçük, tek-sorumluluklu alt-Composable'lara böl (`...Content` → `...Section` → `...ListItem`); `ViewModel`/`NavController`/iş mantığı stateless content'e **girmez**. Canonical reference: `.claude/rules/architecture.md` (State Hoisting), `.claude/skills/new-feature/` (Stateless Content Rule).

> ⚠️ **Interaction Rule (Zorunlu):** iOS'taki `UITapGestureRecognizer` kurulumunun Compose karşılığı **hoisted lambda**'dır. Tıklama/jest, stateless content'in dışarı verdiği `onXClick: () -> Unit` parametresiyle yukarı taşınır: kartta `Card(onClick = onXClick)` veya `Modifier.clickable { onXClick() }`, ikonda `IconButton(onClick = onXClick)`. Lambda gövdesinde iş mantığı **çalıştırma** — mantık ViewModel'dedir. Ayrı bir gesture nesnesi / "recognizer" oluşturma. Canonical reference: `.claude/rules/ui-components.md` (Dokunma Hedefleri).

> ⚠️ **Code-layout Rule (Zorunlu):** Kotlin/Compose'da `// MARK:` **yoktur** — iOS'taki MARK bölümlemesinin karşılığı, ekranı **küçük Composable'lara bölmektir**. Mantıksal grupları gerektiğinde düz İngilizce yorumla (`// --- State ---`) ayır. Dosya içi önerilen sıra: (1) public `<FeatureName>Screen` (stateful), (2) `<FeatureName>Content` (stateless), (3) private alt-Composable'lar, (4) `@Preview`. Canonical reference: `.claude/rules/ui-components.md` (Kod düzeni notu).

> ⚠️ **No-manual-scroll Rule (Zorunlu):** Kaydırılabilir / dikey içerik için **default tercih `LazyColumn`** (ızgara için `LazyVerticalGrid`) — tek kart, çoklu kart, karışık içerik dahil. iOS'taki "No-UIScrollView / tek-cell CollectionView" kuralının karşılığıdır. `Column` + `Modifier.verticalScroll(...)` + elle yığılmış kartlar **kullanma**. Tek öğelik bir sayfa olsa bile `items(...)` ile tek item olarak bağla; ileride state genişlerse (empty → loaded) aynı yapıyı genişletirsin. `verticalScroll` yalnızca gerçek non-liste istisnalar içindir (kısa statik form). **Neden:** öğe geri dönüşümü, stabil `key`, erişilebilirlik ve kod tabanı tutarlılığı. Canonical reference: `.claude/rules/ui-components.md` (Listeler — `LazyColumn`).

> ⚠️ **No-Extra-UI Rule (Zorunlu):** Kullanıcının verdiği Figma frame'inde / talebinde **olmayan** bir UI elementini ASLA "yardımcı olur" diye ekleme — ek chip, button, ikon, açıklama, label, decorative shape vs. dahil. "Figma'da chip var ama bence yapma" / "Figma'da yok ama ekleyeyim" — ikisi de kullanıcıya sormadan yapılırsa hata. Tasarımdan emin değilsen **önce sor**, eklenmesi gerekiyorsa kullanıcı söyler. Kanonik referans: aşağıdaki "Sık Yapılan Hatalar" bölümünün 6. maddesi.

# Figma → ViFi-Android Feature (REST API)

Bu skill **Figma Dev Mode MCP'si yerine Figma REST API'sini** kullanır. Free hesapta çalışır, sadece Personal Access Token gerekir. Çıktı daima **Jetpack Compose + MVVM** feature modülüdür (stateful `Screen` + stateless `Content` + `@HiltViewModel` + `StateFlow<UiState>` + Navigation-Compose route).

> **MCP notu:** Ortamda bir Figma Dev Mode MCP aracı tanımlıysa, aşağıdaki Adım 2'deki manuel `curl` çağrıları yerine o aracın "get frame / get variables / get image" adımlarını kullanabilirsin — **ama token eşleme (Adım 4), iskele (Adım 5) ve doğrulama (Adım 9) aynen geçerlidir ve üretilen kod yine Compose'dur.** MCP yoksa (varsayılan) REST API yolunu izle.

## Ön Koşullar

Kullanıcının elinde olması gereken:
1. **Figma Personal Access Token** — `$FIGMA_TOKEN` env var olarak set edilmiş olmalı
   - Figma → Settings → Security → Personal access tokens → Generate
   - Scope: "File content (read)" + "Variables (read)"
2. **Figma URL** — frame'in `node-id` parametresini içeren URL
   - Örnek: `https://www.figma.com/design/ABC123/MyFile?node-id=12-345`

Eğer `$FIGMA_TOKEN` boşsa veya 401/403 alırsan, kullanıcıya:
- Token'ı `~/.zshrc` veya `.claude/settings.local.json`'un `env` field'ına eklemesini söyle
- `.claude/settings.local.json` ÖRNEĞİ:
  ```json
  { "env": { "FIGMA_TOKEN": "figd_xxxxx" } }
  ```

## Adım 1 — URL'den File Key + Node ID'yi Çıkar

Kullanıcının verdiği URL'i parse et:
- File key: `figma.com/(file|design)/<FILE_KEY>/...`
- Node ID: query string `?node-id=12-345` → API formatı `12:345` (tire → iki nokta)

## Adım 2 — Frame Verilerini Çek

Bash ile REST API çağrıları yap. Token'ı **hiçbir zaman log'a yazma, dosyaya commit etme.**

### 2a. Frame node bilgisi
```bash
curl -s -H "X-Figma-Token: $FIGMA_TOKEN" \
  "https://api.figma.com/v1/files/<FILE_KEY>/nodes?ids=<NODE_ID>&geometry=paths"
```
Bu cevap içinde frame'in tam ağacı (children, fills, strokes, effects, characters, textStyles, layoutMode, padding, itemSpacing, vb.) gelir. Bunu JSON olarak parse et.

### 2b. File-level styles ve variables (token mapping için)
```bash
curl -s -H "X-Figma-Token: $FIGMA_TOKEN" \
  "https://api.figma.com/v1/files/<FILE_KEY>/styles"
```
```bash
# Variables (color/spacing tokens) — sadece Pro+ planda var, free planda 404 dönebilir, sessizce geç
curl -s -H "X-Figma-Token: $FIGMA_TOKEN" \
  "https://api.figma.com/v1/files/<FILE_KEY>/variables/local"
```

### 2c. Görsel referans (PNG render)
```bash
curl -s -H "X-Figma-Token: $FIGMA_TOKEN" \
  "https://api.figma.com/v1/images/<FILE_KEY>?ids=<NODE_ID>&format=png&scale=2"
```
Cevaptaki `images.<NODE_ID>` URL'sini açıp görsel olarak doğrula (Read tool ile imajı oku — multimodal).

## Adım 3 — Çıkarılması Gereken Bilgiler

Frame JSON'dan şunları topla:
- **Hierarchy**: parent-child ilişkisi (hangi alt Composable'ların olacağını belirler — her mantıksal grup bir alt-Composable)
- **Frame türü**: `FRAME` / `INSTANCE` / `COMPONENT` — auto-layout var mı (`layoutMode: VERTICAL/HORIZONTAL` → `Column`/`Row`)
- **Renkler**: `fills[].color` → RGB (0-1 normalize) → hex'e çevir
- **Fontlar**: `style.fontFamily`, `style.fontWeight`, `style.fontSize`
- **Boyutlar**: `absoluteBoundingBox.width/height`
- **Spacing**: `paddingLeft`, `paddingTop`, `itemSpacing`
- **Corner radius**: `cornerRadius` veya `rectangleCornerRadii`
- **Stroke**: `strokes[].color`, `strokeWeight`
- **Shadow**: `effects[]` (DROP_SHADOW)
- **Text content**: TEXT node'larında `characters` field'ı
- **Image fills**: `fills[].type === "IMAGE"` → `imageRef` (asset gerekiyor demek)

## Adım 4 — Token Eşleme

Figma'dan gelen ham değerleri proje token'larına çevir. **Önce `core/designsystem/Color.kt` ve `core/designsystem/Type.kt` (+ `Theme.kt`) dosyalarını oku** ki var olan token'ları bilesin. (iOS'taki `Colors.swift`/`Fonts.swift` okumasının karşılığı.)

| Figma | ViFi-Android karşılığı |
|-------|------------------|
| `#FFFFFF` (rgb 1,1,1), koyu modda sabit | `VifiAlwaysWhite` (`Color.kt`), tema değilse |
| Ekran arka planı | `MaterialTheme.colorScheme.background` |
| Marka turuncusu (`#FB920F`) | `MaterialTheme.colorScheme.primary` / `VifiOrange` |
| `#17171C` birincil metin | `MaterialTheme.colorScheme.onSurface` / `VifiInk` |
| İkincil/gri metin | `MaterialTheme.colorScheme.onSurfaceVariant` / `VifiGray` |
| Kart yüzeyi | `MaterialTheme.colorScheme.surface` / `VifiSurfaceCard` |
| Inter Medium/SemiBold | `Type.kt`'teki `FontFamily` zaten stile bağlı → `MaterialTheme.typography.<style>` |
| Auto-layout `VERTICAL` + `itemSpacing: 12` | `Column(verticalArrangement = Arrangement.spacedBy(12.dp))` |
| Auto-layout `HORIZONTAL` + `itemSpacing: 8` | `Row(horizontalArrangement = Arrangement.spacedBy(8.dp))` |
| `cornerRadius: 12` | `RoundedCornerShape(12.dp)` |
| Capsule (`cornerRadius` ≈ height/2 veya ≥256) | `CircleShape` (ya da `RoundedCornerShape(50)`) |

**Eşleşme yoksa:**
- Renk: kullanıcıya sor → ya `core/designsystem/Color.kt`'e yeni token ekleyip `Theme.kt`'teki `lightColorScheme/darkColorScheme`'e bağla, ya da en yakın token'ı öner. ASLA `Color(0xFF...)`'i Composable içine gömme.
- Font: en yakın `MaterialTheme.typography.<style>`'ı öner; Figma boyutu hiçbir stile oturmuyorsa `Type.kt`'e yeni stil ekle (Inter dışı font varsa kullanıcıya bildir). Composable'a `sp` gömme.
- Text: `res/values/strings.xml`'e yeni key ekle (Türkçe) — bkz. Adım 7.

### ⚠️ Kritik: Boyutlar Birebir Alınmalı (dp)

Figma'daki **width / height / corner radius / spacing / padding / icon size / opacity** değerleri **AYNEN** koda taşınmalı — Compose'da `dp` (ve opacity'de `alpha` float) olarak. "Yaklaşık", "benzeri olan bileşen", "alışkanlıktan gelen değer" YASAK.

> **İstisna — font boyutu:** Tek birebir-taşınmayan değer `style.fontSize`'dır. Onu `dp`/`sp` olarak gömmek **yasak** (Typography Rule); Figma boyutunu **en yakın `MaterialTheme.typography` stiline** eşle (yukarıdaki tablo). Figma boyutu ile seçtiğin stilin boyutu belirgin farklıysa raporda not düş veya `Type.kt`'e yeni stil ekle. Diğer tüm ölçüler (width/height/radius/spacing/padding/icon/opacity) birebir `dp`/`alpha`'dır.

**Kontrol listesi (her UI element için):**
- [ ] **Button height** → `absoluteBoundingBox.height` ne ise `Modifier.height(<exact>.dp)` aynı (ör. Figma 48 ise 48.dp, 56 değil)
- [ ] **Button corner radius / shape** → `cornerRadius` field'ı → `shape = RoundedCornerShape(<exact>.dp)`; capsule ise `shape = CircleShape`. Material3 `Button`'ın default shape'ine güvenme, Figma değerini **açıkça** ver.
- [ ] **Icon / image size** → Figma'daki `width x height` ne ise `Modifier.size(<exact>.dp)` (24, 48, vb.)
- [ ] **Circle / Ellipse** → Figma'daki dairelerin width/height değerleri birebir `Modifier.size(144.dp)`, şekil `CircleShape`
- [ ] **Spacing** → `itemSpacing` → `Arrangement.spacedBy(<exact>.dp)`; aralardaki offset'ler `Spacer(Modifier.height(<exact>.dp))`
- [ ] **Padding / inset** → `paddingLeft/Right/Top/Bottom` → `Modifier.padding(...)` (ya da `LazyColumn` `contentPadding`) birebir
- [ ] **Font size** → `style.fontSize` → **en yakın `typography` stili** (yukarıdaki istisna), inline `sp` değil
- [ ] **Opacity** → element `opacity` field'ı (0.04, 0.08 vb.) → `Modifier.alpha(<exact>f)` veya renkte `.copy(alpha = <exact>f)`

**Önce JSON'dan oku, sonra yaz.** Frame ağacında her UI elementinin `absoluteBoundingBox.width/height`, `cornerRadius`, `opacity`, `itemSpacing`, `paddingLeft/Right/Top/Bottom` değerlerini topla; `Modifier`/`shape`/`Arrangement` değerlerini bu değerlerden **direkt** üret. Mevcut bir bileşenin (örn. `VifiPrimaryButton`) "default" boyutunu varsayma — Figma değerini override et.

### ⚠️ Sık Yapılan Hatalar (Tekrar Düşmeden Önce Oku)

Bu hatalar iOS tarafındaki **gerçek hatalardan** çıkarıldı ve Compose karşılıklarıyla verildi — her yeni feature başlamadan önce bu listeyi tara:

1. **Capsule CTA button → `shape` MUTLAKA elle set edilmeli**
   - Material3 `Button` default'ta tam yuvarlak bir shape kullanır, ama Figma `radius=256` (capsule) veya belirli bir radius (ör. 24) gördüğünde `shape`'i **açıkça** ver; "default güzel durur" diye bırakma.
   - Yanlış: `Button(onClick = ...) { Text(...) }` → shape'i şansa bırakır
   - Doğru: `Button(onClick = ..., shape = CircleShape) { Text(...) }` (capsule) ya da `shape = RoundedCornerShape(24.dp)` (Figma height 48 → radius 24)

2. **Sayfa kenar boşluğu (side inset) Figma'dan oku — varsayılan 16, asla "alışkanlıktan 24" yazma**
   - Figma'daki "Container" / outer frame'in `paddingLeft/paddingRight` ne ise koda o gider.
   - Çoğu zaman 16 olur (375 ekran − 343 content = 32 / 2 = 16). 24 yazmak için Figma'da explicit `paddingLeft = 24` görmen lazım.
   - Yanlış: `Modifier.padding(horizontal = 20.dp)` / `.padding(horizontal = 24.dp)` "biraz boşluk olsun" diye
   - `LazyColumn` kök ise inset'i `contentPadding = PaddingValues(horizontal = 16.dp, vertical = ...)` ile ver.

3. **Modal `ModalBottomSheet` header top padding'i Figma'da 16 → header 16 olmalı (8 değil)**
   - Modal sheet'lerde Figma genelde `Head` frame'ine `paddingTop = 16` koyar; sheet'in drag handle'ından sonra header padding'ini Figma değerine eşitle.
   - Yanlış: `.padding(top = 8.dp)` (close button içeriğe yapışır)
   - Doğru: `.padding(top = 16.dp)`

4. **Title font size'ı Figma'dan oku — 18 ise `titleMedium`, 22'ye (`titleLarge`) yükseltme**
   - Modal title genelde Inter/700/18 olur. Figma 18pt → `titleMedium` (≈18); 22pt → `titleLarge`/`headlineSmall`. Proje konvansiyonu daha büyük olsa bile Figma boyutunun eşlendiği stili seç.
   - Inline `fontSize = 18.sp` yazma — stili seç.

5. **Inner card padding'i de Figma'dan oku**
   - Card içindeki padding genelde 16, container padding genelde 16 — ikisini karıştırma.
   - Card 343 wide olsa bile içindeki text 311 wide olabilir → card iç padding 16 demek (`Column(Modifier.padding(16.dp))`).

6. **🚫 ASLA istenmeden / tasarımda olmayan UI elementi EKLEME**
   - Kullanıcı bir frame veriyorsa **sadece o frame'deki elementleri** koda taşı. "Daha kullanışlı olur", "böyle daha iyi durur", "Figma'da var ama sade görünmez" gibi düşüncelerle ek button/chip/label/icon EKLEME.
   - Figma'da olan ama o akışta gereksiz/yanlış görünen bir element varsa → **kullanıcıya sor**.
   - Tasarımda **olmayan** bir şeyi hayatta ekleme: ekstra "Şimdi Ekle" chip, fazladan "Daha Fazla" CTA, yardımcı subtitle, decorative ikon, vs.

7. **CTA button'da icon Figma'da varsa MUTLAKA ekle, yoksa MUTLAKA EKLEME**
   - Figma'da button içinde `Icon`/`Arrow`/`share` instance'ı görüyorsan → asset'i bul, button içine `Icon(...)` + `Spacer(Modifier.width(8.dp))` + `Text(...)` olarak ekle (`Row` hizası ortalı).
   - Figma'da icon yoksa kendi başına ekleme. Button sade kalsın.
   - Tint: button background dinamik bir renkse (ör. `primary`) tint için `MaterialTheme.colorScheme.onPrimary`, koyu modda sabit kalması gerekiyorsa `VifiAlwaysWhite` kullan. Dekoratif olmayan ikonda `contentDescription`'ı Türkçe ve `strings.xml`'den ver; saf dekoratifse `null`.

**Yazmadan önce şu adımları sırasıyla yap:**

1. Frame için JSON'da `paddingLeft/paddingRight/paddingTop/paddingBottom` değerlerini bul → side inset'ler için bu değerleri kullan.
2. CTA button için `cornerRadius` field'ı veya `radius=256` (capsule) gördüysen → `shape = RoundedCornerShape(<exact>.dp)` / `CircleShape` ata.
3. Title TEXT node'u için `style.fontSize` değerini bul → en yakın `typography` stiline eşle.
4. Modal'larda `Head`/`TopBar` frame'inin top padding'ine bak → header padding'ini ona eşitle.

**Örnek (yanlış vs doğru):**
```kotlin
// ❌ WRONG — default/habitual height
Button(
    onClick = onAddCardClick,
    modifier = Modifier.fillMaxWidth().height(56.dp),   // 48 in Figma
) { Text(stringResource(R.string.exam_add_button)) }

// ❌ WRONG — leaving a capsule CTA without a shape
Button(onClick = onStartClick) {                        // Figma capsule (radius 256), shape left to chance
    Text(stringResource(R.string.onboarding_start))
}

// ❌ WRONG — writing the page side inset as 24 "out of habit"
LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp)) { /* ... */ }  // Figma Container pad=16

// ❌ WRONG — title with inline sp
Text(stringResource(R.string.exam_detail_title), fontSize = 18.sp)           // don't embed sp

// ✅ CORRECT — exactly from Figma
Button(
    onClick = onAddCardClick,
    modifier = Modifier.fillMaxWidth().height(48.dp),   // absoluteBoundingBox.height = 48
    shape = RoundedCornerShape(24.dp),                  // capsule: height / 2 = 24
) {
    Text(stringResource(R.string.exam_add_button))
}

LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) { /* ... */ } // Figma Container pad=16

Text(
    text = stringResource(R.string.exam_detail_title),
    style = MaterialTheme.typography.titleMedium,       // Figma 18pt → titleMedium
)
```

Eğer Figma değeri proje konvansiyonuna ters görünse bile (örn. tüm projedeki butonlar 56, ama bu frame'de 48), **Figma'ya uy** ve raporda "Figma'da bu değer X, projedeki diğer butonlardan farklı" diye not düş — kullanıcı karar versin.

## Adım 5 — Feature İskelesini Oluştur

`new-feature` skill'inin ürettiği yapının aynısı (Compose + MVVM + Hilt). Detaylı template'ler için `.claude/skills/new-feature/` ve `.claude/skills/new-screen/`'e bak; aşağıda yapı özetlenir:

```
app/src/main/java/com/salticusteam/vifi/
├── feature/<featureName>/
│   ├── <FeatureName>Screen.kt       // stateful route Composable + event collection (hiltViewModel BURADA)
│   ├── <FeatureName>Content.kt      // stateless Composable (uiState + lambda) + @Preview
│   ├── <FeatureName>ViewModel.kt    // @HiltViewModel + StateFlow<UiState> + Channel<Event>
│   ├── <FeatureName>UiState.kt      // tek immutable data class + Event sealed interface
│   └── <Item>ListItem.kt            // (frame'de liste/grid varsa) satır Composable'ı — bkz. skill new-list-item
└── core/
    ├── data/repository/             // (frame'de dinamik veri varsa — bkz. Adım 6)
    ├── model/
    └── navigation/<FeatureName>Navigation.kt
```

### Stateful `Screen` kuralları
- `@Composable fun <FeatureName>Screen(onNavigate..., onNavigateBack, viewModel = hiltViewModel())`
- `collectAsStateWithLifecycle()` ile state topla; `LaunchedEffect` ile tek-seferlik event'leri dinle.
- `hiltViewModel()` **yalnız burada** çağrılır; sonra stateless `<FeatureName>Content`'e devreder.

### Stateless `Content` kuralları
- `@Composable fun <FeatureName>Content(uiState, onX: () -> Unit, modifier: Modifier = Modifier)`
- Kök `Scaffold(containerColor = MaterialTheme.colorScheme.background)` + `ViFiTopAppBar` (push ekran ise ikon-only geri).
- Düzeni Figma auto-layout'tan üret: `VERTICAL` → `Column`, `HORIZONTAL` → `Row`, kaydırılabilir/liste → `LazyColumn`/`LazyVerticalGrid` (No-manual-scroll Rule).
- `@Preview`'leri **Fake data ile** stateless content üzerinde yaz — ViewModel'i değil.
- **⚠️ testTag ZORUNLU — her etkileşimli/duruma-bağlı node'a.** `Modifier.testTag("<ekran>.<öğe>")` (ör. `"examList.backButton"`). Bu sadece top-level öğeleri değil, factory/helper Composable'larda (`ExamListItem`, `makeRow`) üretilen alt öğeleri de kapsar: liste satırları, buton içi ikonlar, container'lar. Helper bir `testTagPrefix`/`id` parametresi alıp türetilmiş tag'ler atasın (`"$prefix.row"`, `"$prefix.icon"`). İkon-only butonlarda `contentDescription` Türkçe ve `strings.xml`'den. testTag'ler UI testleriyle **sözleşmedir** — mevcutlarını değiştirme.

### `ViewModel` kuralları
- `@HiltViewModel class <FeatureName>ViewModel @Inject constructor(private val <x>Repository: ..., savedStateHandle: SavedStateHandle) : ViewModel()`
- Tek `private MutableStateFlow(<FeatureName>UiState())` + `asStateFlow()`; tek-seferlik olaylar `Channel`/`receiveAsFlow`.
- İş/IO `viewModelScope.launch` içinde; Context'i ViewModel'de tutma.
- Ayrıntılı desenler için: `.claude/rules/viewmodel-rule.md` ve `.claude/skills/new-viewmodel/`.

> **Legacy not:** Eski `CourseExamListActivity` + `activity_course_exam_list.xml` + `CustomAdapter` üçlüsü, bu yapıda `ExamListScreen` + `ExamListContent` + `LazyColumn`/`ExamListItem`'a karşılık gelir.

## Adım 6 — Repository (Tasarımda Dinamik Veri Varsa)

Frame'de dinamik veri (liste, detay, vb.) varsa `.claude/rules/firebase-data.md`'ye göre veri katmanını üret/yeniden kullan. Her kaynak bir `interface` arkasındadır; UI'ya ham Firebase tipi (`DocumentSnapshot`/`DataSnapshot`) **sızmaz** — DTO→domain `data class` map edilir, hatalar `sealed class ArchiveError`/`AuthError` + Türkçe `userMessage`'a çevrilir.

```
core/data/repository/
├── <Feature>Repository.kt          // interface (iOS protocol karşılığı)
├── Firebase<Feature>Repository.kt  // canlı impl (DTO→domain, Task.await() + Dispatchers.IO; dinleyici → callbackFlow { awaitClose { ... } })
└── Fake<Feature>Repository.kt      // preview/test impl (iOS Mock karşılığı)
core/model/<Item>.kt                // plain domain data class
```

Preview ve birim testlerinde `Fake<Feature>Repository` kullanılır; canlı graf Hilt `@Binds` ile `Firebase<Feature>Repository`'ye bağlanır. Fake/mock üretimi: `.claude/skills/generate-mocks/` (veya `commands/generate-mocks.md`). Dinamik veri yoksa bu adımı atla.

## Adım 7 — Lokalizasyon (strings.xml)

Frame'deki TEXT node'larında bulduğun her `characters` değeri için **önce mevcut key var mı diye kontrol et, ASLA önce yeni key eklemeye atlama**.

> **iOS farkı (teaching note):** iOS'ta `LocalizationKey` **ve** `DefaultStrings` olmak üzere iki ayrı dosyayı elle eşlemek gerekiyordu. Android'de bu **iki-dosya kuralı yoktur**: `res/values/strings.xml` hem tanım hem fallback'tir ve `R.string.x` **derleme anında** denetlenir — var olmayan bir id uygulamayı derletmez. Tek adım: `strings.xml`'e ekle, `stringResource(R.string.x)` ile tüket. Ayrıntı: `.claude/rules/localization.md`.

### 7a. ZORUNLU: Mevcut key taraması (önce bunu yap)

Her TEXT değeri için `res/values/strings.xml`'i tara. Tasarımdaki Türkçe metnin **anlamsal** karşılığını hem metinle hem anahtar kelimeyle ara:
```bash
STR="app/src/main/res/values/strings.xml"
# Tasarımdaki "Yeni Kart Ekle" / "Sınav Ekle" → anahtar kelime + metinle ara
grep -niE "add|ekle|new|yeni" "$STR"
# Tasarımdaki "Kayıtlı Sınav Bulunmamaktadır" → "empty"/"bulunamadı"/"yok" ile ara
grep -niE "empty|bulunamadı|yok|no_" "$STR"
# Geri / İptal / Tamam gibi ortaklar zaten common_* altında olabilir
grep -niE "common_(back|cancel|retry|ok|close)" "$STR"
```
Ortak UI metinleri (geri, iptal, tekrar dene) `common_*` altında zaten vardır — tekrar ekleme.

### 7b. Karar matrisi

| Durum | Aksiyon |
|-------|---------|
| `strings.xml`'de anlamsal eşi var | **Mevcut key'i kullan** — `stringResource(R.string.<ad>)` |
| `strings.xml`'de yok | Özelliğin ön-ekiyle yeni `<string>` ekle (Türkçe), `stringResource` ile tüket |

### 7c. Yeni key ekleme (gerekliyse)

1. Ad alanını **metnin temsil ettiği** özelliğe göre `snake_case` ön-ekle (nerede göründüğüne göre değil): `exam_detail_title`, `archive_empty`, `onboarding_welcome_title`. iOS dot-notation (`myFeature.title`) → Android `snake_case` ön-ek.
2. Dinamik değer araya giriyorsa **numaralı positional** arg kullan: `strings.xml`'de `%1$s`/`%1$d`, çağrıda `stringResource(R.string.x, arg)`. Çıplak `%s`/`%d` veya ham string birleştirme **YASAK** (çeviri güvenliği).
3. Sayıya bağlı metinde `res/values/plurals.xml` + `pluralStringResource` (`one` + `other`).
4. Türkçe kesme işaretini **kaçır**: `Üniversite\'nin` veya çift tırnak içine al; format arg varken literal yüzde `%%`.
5. İkon `contentDescription`'ları da yerelleştirilir (`..._cd` son-eki), `testTag` **çevrilmez ve değişmez**.
6. Kullanıcıya raporda eklediğin yeni key'leri belirt; backend karşılığı yoksa boş çıkar uyarısı yap (dinamik metinler için).

```xml
<!-- Example: additions to strings.xml (Turkish, feature-prefixed) -->
<string name="exam_detail_title">Sınav Detayı</string>
<string name="exam_detail_download_button">İndir</string>
<string name="exam_uploaded_by">%1$s tarafından yüklendi</string>   <!-- positional arg -->
```
```kotlin
Text(stringResource(R.string.exam_detail_title))
Text(stringResource(R.string.exam_uploaded_by, exam.uploaderName))
```

## Adım 8 — Asset'ler (Resim/Icon Varsa)

Figma'da image fill veya export setting'i olan node varsa URL'leri çek:
```bash
# Vektör ikonlar için SVG tercih (Android Studio "Vector Asset" ile importEdilir)
curl -s -H "X-Figma-Token: $FIGMA_TOKEN" \
  "https://api.figma.com/v1/images/<FILE_KEY>?ids=<NODE_IDS>&format=svg"
# Bitmap için PNG (yoğunluk bucket'ları: mdpi=1x, xhdpi=2x, xxhdpi=3x)
curl -s -H "X-Figma-Token: $FIGMA_TOKEN" \
  "https://api.figma.com/v1/images/<FILE_KEY>?ids=<NODE_IDS>&format=png&scale=2"
```
URL'leri kullanıcıya ver, **otomatik indirme yapma** — ikonlar `res/drawable/`'a vector drawable olarak (Android Studio "Vector Asset" veya SVG→XML), bitmap'ler ilgili `res/mipmap-*`/`drawable-*` bucket'larına manuel eklenir. Hangi node'un hangi asset'e (ve dosya adına, ör. `ic_exam_download.xml`) karşılık geldiğini listele. Compose'da `painterResource(R.drawable.ic_...)` ile tüketilir.

## Adım 9 — Doğrulama Raporu

Raporu yazmadan **ÖNCE** üç doğrulama yap:

**1) Boyut doğrulaması (Adım 4'teki kontrol listesi):**
- Yazdığın Content dosyasındaki TÜM `.height(...)`, `.width(...)`, `.size(...)`, `RoundedCornerShape(...)`, `Arrangement.spacedBy(...)`, `.padding(...)`, `PaddingValues(...)`, `.alpha(...)` çağrılarını gözden geçir.
- Her birinin Figma JSON'daki karşılığını (absoluteBoundingBox, cornerRadius, opacity, itemSpacing, padding) bul.
- Uyuşmayan varsa düzelt — "yaklaşık"/"alışkanlık"/"default" değerler YASAK.

**2) Tema/hardcode doğrulaması (grep ile — çıktı boş olmalı):**
```bash
DIR="app/src/main/java/com/salticusteam/vifi/feature/<featureName>"
grep -rnE "Color\(0x" "$DIR"           # hardcoded renk → MaterialTheme.colorScheme / Color.kt token olmalı
grep -rnE "[0-9]+\.sp" "$DIR"          # inline font size → MaterialTheme.typography stili olmalı
grep -rnE "fontSize|fontWeight *=" "$DIR"  # elle font → typography stili olmalı
```

**3) String & testTag doğrulaması:**
- Kullanıcıya görünen her metin `stringResource(R.string.x)`'ten geliyor mu? Composable'da sabit Türkçe/İngilizce string (`Text("...")`) var mı? Olmamalı.
- Her etkileşimli/duruma-bağlı node'da `Modifier.testTag("<ekran>.<öğe>")` var mı? İkon-only butonlarda Türkçe `contentDescription` var mı?

Bittikten sonra kullanıcıya:
- ✅ Oluşan dosyalar (tam yollar)
- ✅ Eklenen `strings.xml` key'leri (+ positional arg kullananlar)
- ✅ **Boyut tablosu**: Figma'daki kritik ölçüler (button height, icon size, corner radius, spacing) ve koddaki `dp` karşılıkları yan yana — kullanıcı hızlıca doğrulayabilsin
- ⚠️ Eşleşemeyen renk/font (varsa) ve nereye token/stil eklendiği
- ⚠️ Figma değeri proje konvansiyonundan farklıysa not (örn. "diğer butonlar 56, bu Figma 48 — Figma'ya uydum")
- ⚠️ İndirilmesi gereken asset'ler + URL'leri + hedef `res/drawable` adları
- ℹ️ Bu ekrana nasıl navigate edileceği (`NavHost`'a `composable(...)` kaydı + çağıran ekrandan `navController.navigate(...)`) — kullanıcının bağlaması gereken adım

## Yasaklar

- ❌ `Activity`/`Fragment`/XML layout (`res/layout`), `findViewById`, elle `RecyclerView.Adapter` (legacy istisna)
- ❌ Composable içine hardcoded `Color(0xFF...)` / inline `fontSize = ...sp` / elle `TextStyle(...)` (her şey `MaterialTheme.colorScheme.*` / `MaterialTheme.typography.*` / `Color.kt` / `Type.kt`)
- ❌ `Column` + `Modifier.verticalScroll` + elle yığılmış kartlar — `LazyColumn`/`LazyVerticalGrid` kullan (No-manual-scroll Rule)
- ❌ Hardcoded string (her şey `strings.xml` + `stringResource`); ham string birleştirme / çıplak `%s`
- ❌ Stateless `Content` içinde `viewModel`/`hiltViewModel`/`NavController`/iş mantığı
- ❌ UI'ya ham Firebase tipi (`DocumentSnapshot`/`DataSnapshot`) sızdırma
- ❌ Metinli/elle geri butonu (`< Geri`) — `ViFiTopAppBar` ikon-only geri + `popBackStack()` (modal'da kapat "✕")
- ❌ AdMob / reklam (parite için kaldırıldı); uygulama Türkçe-only
- ❌ `!!` / güvensiz cast / `lateinit` kötüye kullanımı
- ❌ Token'ı log'a yazmak veya bir dosyaya commit etmek

## Hata Durumları

| Hata | Çözüm |
|------|-------|
| `$FIGMA_TOKEN` boş | Kullanıcıya token alıp env'e eklemesini söyle, dur |
| HTTP 403 | Token yanlış veya scope yetersiz |
| HTTP 404 (nodes/files) | File key yanlış veya dosyaya erişim yok (private team file) |
| HTTP 429 | Rate limit, 1 dakika bekle ve tekrar dene |
| Variables endpoint 404 | Free planda yok, sessizce geç (styles endpoint'i yeterli) |

## Örnek Tetikleyiciler

- "Figma'dan ExamDetail ekranını oluştur: <URL>"
- "Şu Figma frame'ini Compose'a çevir: <URL>"
- "Figma URL'inden yeni feature oluştur"

---

**See Also:**
- `.claude/skills/new-feature/` — Tam feature iskelesi (Screen + Content + ViewModel + UiState + Repository + test)
- `.claude/skills/new-screen/` — Tek Compose ekranı iskelesi
- `.claude/skills/new-viewmodel/` — ViewModel + UiState iskelesi
- `.claude/skills/new-list-item/` — Liste satırı Composable'ı (frame'de liste varsa)
- `.claude/skills/generate-mocks/` — Fake repository / sample data üretimi (preview için)
- `.claude/skills/generate-unit-tests/`, `.claude/skills/generate-ui-tests/` — birim + Compose UI testleri
- `.claude/skills/review-pr/` — üretilen feature'ı PR öncesi gözden geçir
- `.claude/commands/scaffold-feature.md` — komutla feature iskelesi · `.claude/commands/check-architecture.md` — hardcode/string/mimari denetimi
- `.claude/rules/ui-components.md` — Tema, tipografi, `ViFiTopAppBar` ikon-only geri, `LazyColumn`, testTag/contentDescription
- `.claude/rules/architecture.md` — Compose + MVVM + Hilt, State Hoisting, versioned doc comment
- `.claude/rules/viewmodel-rule.md` — `@HiltViewModel` + `StateFlow<UiState>` + `Channel` event deseni
- `.claude/rules/firebase-data.md` — Repository + DTO→domain map + `ArchiveError` (ham Firebase tipi sızmaz)
- `.claude/rules/localization.md` — `strings.xml` + `stringResource` + positional args (`%1$s`)
- `.claude/rules/testing.md` — `testTag` sözleşmesi + Compose UI testi
- `core/designsystem/Color.kt` · `core/designsystem/Type.kt` · `core/designsystem/Theme.kt` — marka renk token'ları, tipografi ölçeği, `ViFiTheme`
