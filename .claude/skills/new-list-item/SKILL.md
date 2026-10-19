---
name: new-list-item
description: ViFi-Android için liste öğesi (LazyColumn/LazyRow item Composable) üretir — Material3, MaterialTheme renk/tipografi, stringResource, kararlı testTag, @Preview ve ViFi domain modeli (Exam/Lesson/University/Faculty/Department) ile. Legacy XML ekranlar için RecyclerView ViewHolder + ListAdapter/DiffUtil varyantı da sağlar.
---

Bu skill, iOS'taki **new-cell** (`UICollectionViewCell`/`UITableViewCell`) skill'inin Android karşılığıdır.
Android'de liste öğesinin **birincil (hedef)** biçimi bir **Compose item Composable**'ıdır (`LazyColumn`/
`LazyRow` içinde); **ikincil (legacy)** biçim ise yalnız XML ekranlarda kullanılan **RecyclerView
ViewHolder**'dır. iOS'taki reuse/dequeue/`prepareForReuse` döngüsünün Compose'da **karşılığı yoktur** —
recomposition bunu kendi yönetir.

> ⚠️ **Renk kuralı (zorunlu):** Renkler `MaterialTheme.colorScheme.*` (marka sabitleri `core/designsystem/theme/Color.kt`);
> hardcoded `Color(0x...)` / XML hex **YASAK** (iOS `Colors.<name>` karşılığı). Kanonik referans: `.claude/rules/ui-components.md` (Color & Theme).

> ⚠️ **Tipografi kuralı (zorunlu):** Font `MaterialTheme.typography.*` (marka tipografisi `core/designsystem/theme/Type.kt`);
> `fontSize`/`fontWeight` gömme. Yazı ölçeği Material3'te otomatik (iOS `configureScaledFont` + Dynamic Type
> karşılığı). Kanonik referans: `.claude/rules/ui-components.md` (Typography).

> ⚠️ **String kuralı (zorunlu):** Kullanıcıya görünen her metin `stringResource(R.string.x)` / `@string/...`,
> `contentDescription` dahil; koda sabit string gömme **YASAK**. Positional arg (`%1$s`). Kanonik referans: `.claude/rules/localization.md`.

> ⚠️ **testTag kuralı (zorunlu):** Her öğenin kararlı bir `testTag("<ekran>.<öğe>")`'i olsun — UI testleriyle
> **sözleşmedir** (iOS accessibility identifier karşılığı), var olanı değiştirme. Kanonik referans: `.claude/rules/ui-components.md` (Accessibility), `.claude/rules/testing.md`.

> ⚠️ **Stateless kuralı (zorunlu):** Liste öğesi state tutmaz — domain `data class` + `onClick` lambda alır
> (iOS `configure(with:)` + delegate'in yerine **state aşağı / event yukarı**). Seçim/expand durumu `uiState`'te,
> item'da değil. Kanonik referans: `.claude/rules/viewmodel-rule.md`.

## Usage

```
/new-list-item <Name> [--type <compose|recyclerview>]
```

**Argümanlar**
- `Name` — öğe adı, "ListItem"/"Item" son eki olmadan (ör. `Exam`, `Lesson`, `University`, `Faculty`, `Department`)
- `--type` — `compose` (varsayılan) ya da `recyclerview` (legacy)

**Not:** ViFi yeni ekranlarda XML/RecyclerView **kullanmaz**. `--type recyclerview`'i yalnız henüz Compose'a
taşınmamış var olan bir XML ekrana öğe eklemek zorundaysan seç.

## Hangi template ne zaman?

### Compose item (varsayılan — hedef)

Birincil template: `.claude/skills/new-list-item/templates/compose-list-item.md`.
Şunlarda kullan:
- **Her yeni/ileriye dönük ekran** (istisna yok).
- `LazyColumn` dikey listeler (sınav listesi, ders listesi, duyurular).
- `LazyRow` yatay şeritler (üniversite/fakülte kartları).
- Kart tabanlı ya da özel yerleşimli satırlar.

iOS `CollectionViewCell`/`TableViewCell`'in ikisi de buraya düşer: Compose'da ayrı "collection vs table"
yoktur — tek bir stateless item Composable'ı hem dikey (`LazyColumn`) hem yatay (`LazyRow`) listede kullanılır.

### RecyclerView item (ikincil — legacy, yalnız XML ekranlar)

İkincil template: `.claude/skills/new-list-item/templates/recyclerview-item.md`.
**Yalnızca** şu durumda kullan:
- Henüz Compose'a taşınmamış **var olan bir XML ekrana** satır eklemen gerekiyor ve o ekranı şimdi
  taşıyamıyorsun.

Bu durumda bile `RecyclerView` + `ListAdapter`/`DiffUtil` + `ViewBinding` kullan (eski `ListView`/`BaseAdapter`
değil) ve ekranı ilk fırsatta Compose'a taşıyıp adapter'ı sil.

> **Legacy not:** Bugünkü v2.5 kodu `ListView` + `BaseAdapter` (`CustomAdapter`/`ListViewAdapter` →
> `custom_layout.xml`) ile çalışıyor; her `getView`'da `findViewById`, reuse yok, tarihler call-site'ta
> `SimpleDateFormat`'la çözülüyor. Hedef bu desenin tamamen Compose item Composable'ına taşınmasıdır; ara
> adım (zorunlu kalınırsa) `RecyclerView`+`ListAdapter`'dır. Göçün yol haritası: repo kökü
> `ANDROID-PARITY-ROADMAP.md` (UI mimarisi).

## Dosya organizasyonu

Önerilen paketleme `feature/<ad>/` (ekran Composable, ViewModel, UiState, liste öğesi bir arada) +
`core/` (model, repository, util, designsystem):

```
feature/{feature}/
├── {Feature}Screen.kt        // stateful + stateless content Composable
├── {Feature}ViewModel.kt
├── {Feature}UiState.kt
└── {Name}ListItem.kt         // ← bu skill'in ürettiği Compose item

core/model/
└── {Name}.kt                 // domain data class (ham Firebase tipi değil)
```

**Örnek (Compose):**
```
feature/examlist/ExamListItem.kt
core/model/Exam.kt
```

**Örnek (legacy RecyclerView — yalnız XML ekran):**
```
feature/examlist/legacy/ExamAdapter.kt
res/layout/item_exam.xml
```

## Adımlar

1. **Argümanları ayrıştır** — `Name` ve `--type` (varsayılan `compose`).
2. **Feature/modülü belirle** — öğe hangi ekrana ait (ör. examlist, lessonlist, universitylist). Brief gereği
   **soru sorma**; feature adı verilmediyse `Name`'den türet (`Exam` → `examlist`).
3. **Template seç**
   - `compose` → `templates/compose-list-item.md`
   - `recyclerview` → `templates/recyclerview-item.md` (yalnız legacy XML ekran gerekçesi varsa)
4. **Placeholder'ları değiştir** — `{Name}` (PascalCase), `{name}` (camelCase), `{feature}` (lowercase),
   paket `com.salticusteam.vifi`.
5. **Domain modelini doğrula/üret** — `core/model/{Name}.kt` plain `data class` (ham `DataSnapshot`/
   `DocumentSnapshot` değil; bkz. `.claude/rules/firebase-data.md`). Var olan modele alan eklediysen
   `// Added in <versionName> — <amaç>.` doc comment'i koy (versiyon = `app/build.gradle` `versionName`).
6. **String'leri ekle** — görünen her metin + `contentDescription` `res/values/strings.xml`'de, positional
   arg'larla (`%1$s`).
7. **testTag ver** — `"<ekran>.item.<id>"` deseni; var olan tag'leri koru.
8. **Kaydet** — Compose item'ı `feature/{feature}/{Name}ListItem.kt`; legacy adapter'ı
   `feature/{feature}/legacy/{Name}Adapter.kt` + `res/layout/item_{name}.xml`.
9. **`@Preview` ekle** (Compose) — `ViFiTheme` ile sarılı, ViFi domain örnek verisi.

## Örnekler

### Örnek 1: Compose item (varsayılan)

```bash
/new-list-item Exam --type compose
```

**Üretir:**
```
feature/examlist/ExamListItem.kt
```

**İçerir:**
- `ExamListItem(exam: Exam, onClick: () -> Unit, modifier: Modifier = Modifier)` stateless Composable
- `MaterialTheme.colorScheme` / `typography` kullanımı (hardcoded yok)
- `stringResource` + `contentDescription`
- Kararlı `testTag("examlist.item.<id>")`
- `toDisplayDate(...)` ile tarih (merkezi helper)
- `@Preview` (ViFiTheme)

### Örnek 2: Yatay kart (LazyRow)

```bash
/new-list-item University --type compose
```

**Üretir:**
```
feature/universitylist/UniversityListItem.kt
```

Aynı item Composable'ı `LazyRow`'da sabit genişlikle kullanılır (bkz. compose-list-item.md → Varyasyonlar → Yatay liste).

### Örnek 3: Legacy RecyclerView item (yalnız XML ekran)

```bash
/new-list-item Lesson --type recyclerview
```

**Üretir:**
```
feature/lessonlist/legacy/LessonAdapter.kt
res/layout/item_lesson.xml
```

**İçerir:**
- `LessonAdapter : ListAdapter<Lesson, VH>(DIFF)` + `DiffUtil.ItemCallback`
- `ViewBinding` (`ItemLessonBinding`), tema attribute renkleri, `@string/...`
- Tıklamayı ViewModel'e çıkaran `onLessonClick` lambda
- `submitList(...)` ile güncelleme (notifyDataSetChanged YOK)

## En iyi pratikler

1. ✅ **HER ZAMAN** yeni ekranda Compose item kullan (RecyclerView yalnız legacy XML ekran istisnası).
2. ✅ **HER ZAMAN** item'ı stateless tut: domain `data class` + `onClick`/lambda; `Modifier` son parametre, default `Modifier`.
3. ✅ **HER ZAMAN** `MaterialTheme.colorScheme` / `typography`, marka değeri `core/designsystem/theme/Color.kt` / `Type.kt`.
4. ✅ **HER ZAMAN** görünen metin + `contentDescription` için `stringResource` / `@string/...`.
5. ✅ **HER ZAMAN** kararlı `testTag("<ekran>.item.<id>")` ver; var olanı değiştirme.
6. ✅ **HER ZAMAN** tarihi `toDisplayDate(...)` ile çevir (item/ViewHolder içinde formatter kurma yok).
7. ✅ **HER ZAMAN** `LazyColumn`/`LazyRow`'da `items(..., key = { it.id })` kullan (legacy'de `ListAdapter`+`DiffUtil`).
8. ✅ **HER ZAMAN** tıklamayı ViewModel'e lambda ile çıkar (iş mantığı item/Fragment'ta değil).
9. ✅ **HER ZAMAN** Compose item'a `@Preview` (ViFiTheme) ekle.
10. ❌ **ASLA** hardcoded `Color(0x...)` / XML hex, `fontSize`/`fontWeight`, kod içi sabit string.
11. ❌ **ASLA** yeni ekranı XML + RecyclerView ile yazma; `ListView`/`BaseAdapter`/`notifyDataSetChanged()` kullanma.
12. ❌ **ASLA** item'da kalıcı domain state'i (`remember { mutableStateOf }`) tutma; seçim/expand `uiState`'te.
13. ❌ **ASLA** ham Firebase tipini (`DataSnapshot`/`DocumentSnapshot`) item'a sızdırma — repository domain modele map eder.

## Liste öğesinde tarih string'leri

Backend tarihleri ISO 8601 string gelir (`"2026-05-28T12:36:26+00:00"`); **render anında** merkezi helper
ile çevrilir — item/ViewHolder içinde `SimpleDateFormat`/`DateTimeFormatter` **kurma** (bkz.
`.claude/rules/date-handling.md`):

```kotlin
// ✅ CORRECT
Text(text = exam.createdAt.toDisplayDate(DateDisplayFormat.DottedDate).orEmpty())   // "28.05.2026"

// ❌ WRONG — never build a formatter inside an item
val sdf = SimpleDateFormat("dd.MM.yyyy", Locale("tr"))   // FORBIDDEN (and not thread-safe)
```

---

**Ayrıca bkz.:**
- `.claude/skills/new-list-item/templates/compose-list-item.md` — birincil (hedef) Compose item Composable'ı
- `.claude/skills/new-list-item/templates/recyclerview-item.md` — ikincil (legacy) RecyclerView ViewHolder
- `.claude/skills/new-screen/SKILL.md` — Compose ekran + ViewModel ikilisi (item'ı `LazyColumn`'da kullanır)
- `.claude/rules/ui-components.md` — Compose/Material3, renk/tipografi/erişilebilirlik
- `.claude/rules/viewmodel-rule.md` — state aşağı / event yukarı, seçim durumu
- `.claude/rules/firebase-data.md` — DTO → domain map (item ham Firebase tipi görmez)
- `.claude/rules/localization.md` — strings.xml, `stringResource`, positional arg
- `.claude/rules/date-handling.md` — backend ISO 8601 → `toDisplayDate`
- `.claude/rules/testing.md` — Compose UI testi (`onNodeWithTag`, `performClick`), legacy Espresso
