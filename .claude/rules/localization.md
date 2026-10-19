# ViFi Android — Yerelleştirme (Localization) Kuralları

> **Kapsam:** Kullanıcıya görünen tüm metinler, string kaynakları, Composable metinleri
> **Globs:** `["**/res/values*/strings.xml", "**/res/values*/plurals.xml", "**/ui/**/*.kt", "**/feature/**/*.kt", "**/*Screen.kt"]`

ViFi **Türkçe-only** bir uygulamadır: kullanıcıya görünen **her** metin Türkçedir ve **tek kaynak** olan
`res/values/strings.xml` içinde yaşar. Cihaz dili ne olursa olsun uygulama Türkçe çalışır — bu yüzden varsayılan
`values/` klasörü **doğrudan Türkçedir** (ayrı bir `values-tr/` açma; Türkçe baz dildir). Dili cihaz dilinden bağımsız
sabitlemek için `app/build.gradle`'da `resConfigs "tr"` kullan (yalnızca `tr` kaynaklarını paketle).

> **Legacy not:** Bugünkü `app/src/main/res/values/strings.xml` hâlâ Android Studio şablon artıklarını ve İngilizce
> metinleri (`"Home"`, `"Settings"`, `android.studio@android.com` …) içeriyor; kod tarafında ise metinler büyük
> ölçüde Java içine sabit gömülü. Bu, modernizasyonda temizlenecek borç. Yeni/ileriye dönük kod bu dosyadaki **hedef**
> deseni izler: tüm metin `strings.xml`'de, Türkçe, `stringResource(...)` ile. Yol haritasındaki yeri için bkz.
> `ANDROID-PARITY-ROADMAP.md` (Türkçe-only + string temizliği kalemleri).

---

## ⚠️ Zorunlu Kurallar (HERHANGİ bir metin eklerken MUTLAKA uy)

### 1. Kaynak adını özelliğe/ekrana göre ön-ekle (feature-scoped naming)

Her yeni string kaynağı, **temsil ettiği** özelliğin/ekranın ad alanına (resource-name ön-eki) girer — yalnızca o an
nerede gösterildiğine göre değil.

- ❌ Arşiv indirme bilgisini anlatan bir banner, Ana ekranda gösteriliyor diye `home_...` ön-ekine konmaz.
- ✅ Aynı banner: `exam_download_info_...` (kendi ad alanı) — Ana ekran onu sadece gösterir, onu *temsil* etmez.

Android kaynak adları `[a-z0-9_]` ve harfle başlar; **nokta, camelCase, tire yok**. iOS'un `myFeature.title` dot-notation'ı
Android'de `snake_case` ön-eke dönüşür: `exam_detail_title`. Ön-ek, özelliğin `feature/<ad>/` paketiyle hizalı olsun
(bkz. `rules/architecture.md`).

### 2. Tek kaynak: `strings.xml` — metni asla koda gömme

Kullanıcıya görünen **her** metin yalnızca `strings.xml`'dedir; Composable'a veya herhangi bir `.kt` dosyasına sabit
Türkçe/İngilizce string **gömmek YASAK**.

> **iOS farkı (teaching note):** iOS'ta `LocalizationKey` **ve** `DefaultStrings` olmak üzere iki ayrı tanımı
> elle eşlemek ve çalışma anındaki remote dict eksildiğinde boş string riskini `fallback` ile kapatmak gerekiyordu.
> Android'de bu **iki-dosya kuralı yok**: `strings.xml` hem tanım hem fallback'tir ve `R.string.x` **derleme anında**
> denetlenir. Var olmayan bir id (`R.string.typo`) uygulamayı **derletmez** — iOS'taki sessiz boş-string hatasının
> Android karşılığı derleme hatasıdır. Bu bir güvenlik artışıdır; kötüye kullanıp id'yi string olarak
> (`getIdentifier`) çözmeye kalkma (bkz. Kural 3).

Tek istisna, hata metinleridir: `sealed class ArchiveError/AuthError`'ın kullanıcıya dönük `userMessage`'ı error-handling
kuralıyla tanımlanır (bkz. `rules/architecture.md`). Uygulama Türkçe-only olduğundan bu `userMessage` **inline Türkçe
string**'tir — `@StringRes` gerektirmeyen bilinçli ve **DAR** bir istisnadır (yalnızca `*Error.userMessage` için);
aşağıdaki "Hatalar ve kullanıcı metni" bölümüne bak.

### 3. Dinamik son-ek (status/unit/type ile kurulan) anahtarlar — `getIdentifier` YASAK

Son anahtar çalışma anında bir backend değeri (durum, birim, tip) ile kuruluyorsa, iOS'taki "prefix + suffix string
birleştir, `getKeyOrDefault`" desenini Android'de **asla** `resources.getIdentifier("prefix_" + suffix, "string", pkg)`
ile taklit etme. `getIdentifier` yavaştır, R8/kaynak-küçültmeyi bozar ve derleme-anı güvenliği yoktur.

Bunun yerine enum/sealed → `@StringRes` eşlemesini `when` ile yap; her bilinen değer + bir `UNKNOWN`/else dalı:

```kotlin
// ✅ DO — exhaustive when maps each status to a compile-checked string id
@StringRes
fun SubmissionStatus.labelResId(): Int = when (this) {
    SubmissionStatus.PENDING  -> R.string.submission_status_pending
    SubmissionStatus.APPROVED -> R.string.submission_status_approved
    SubmissionStatus.REJECTED -> R.string.submission_status_rejected
    SubmissionStatus.UNKNOWN  -> R.string.submission_status_unknown   // local sentinel still gets a string
}

// Call site (Composable):
Text(stringResource(status.labelResId()))
```

```kotlin
// ❌ DON'T — reflection-based lookup: slow, breaks shrinking, no compile-time safety
val resId = resources.getIdentifier("submission_status_${status.name.lowercase()}", "string", packageName)
Text(stringResource(resId))   // ❌ may resolve to 0 → crash at runtime
```

- Backend'den gelen bilinen **her** değer için bir `<string>` olsun; nil/boş için `UNKNOWN` sentinel'inin de kaynağı olsun.
- `when` dalını **exhaustive** tut (sealed/enum'da `else` yok; derleyici yeni case'i yakalar).

### 4. Parametreler POSITIONAL format args'tır — `%1$s` / `%1$d`, asla ham birleştirme

Çalışma-anı değeri araya giren her metin, **numaralı positional** format argümanı kullanır ve `strings.xml`'de tanımlanır:

```xml
<!-- ✅ DO — numbered positional args -->
<string name="faculty_exam_summary">%1$s fakültesinde %2$d sınav</string>
<string name="exam_uploaded_by">%1$s tarafından yüklendi</string>
```

```kotlin
// ✅ DO — stringResource(id, args) fills positionals in order
Text(stringResource(R.string.faculty_exam_summary, faculty.name, exam.count))
Text(stringResource(R.string.exam_uploaded_by, exam.uploaderName))
```

**ZORUNLU biçim kuralları:**

- **Numaralı kullan (`%1$s`, `%2$d`), çıplak `%s`/`%d` değil.** Numaralandırma çeviri güvenliğidir: metin yeniden
  sıralandığında argüman sırası bağımsız kalır. (iOS'un "`%d`/`%@` değil, isimli `{token}`" zorunluluğunun Android
  karşılığı: isimli token yoktur, ama **numaralı positional** aynı sıra-bağımsızlığını sağlar.)
- **Ham string birleştirme YASAK.** `"$count sınav"`, `text + " adet"`, `stringResource(a) + stringResource(b)`,
  `buildString { ... }` ile cümle kurma — hepsi yasak. Araya değer giren her cümle tek bir `<string>` + format args olur.
- **Tip:** `%1$s` String/CharSequence, `%1$d` Int/Long, `%1$f` ondalık (genelde kaçın — tarih/sayı biçimlemesi için
  bkz. `rules/date-handling.md`; biçimlenmiş sonucu `%1$s` ile geçir).

```kotlin
// ❌ DON'T — raw concatenation, hardcoded fragments, interpolation
Text("$examCount sınav")                                  // ❌ hardcoded + interpolation
Text(stringResource(R.string.count_prefix) + examCount)  // ❌ string concat
Text("${faculty.name} fakültesi")                         // ❌ hardcoded "fakültesi"
```

**Neden isimli token değil de numaralı positional:**
- Android `stringResource(id, vararg)` / `getString(id, vararg)` arka planda `String.format` çağırır; çerçeve yalnızca
  printf-stili positional destekler. İsimli `{token}` yoktur.
- **Sıra-bağımsız:** `%1$s`/`%2$d` ile cümle parçaları yer değiştirse bile doğru argüman yerleşir.
- **Lint denetimi:** `StringFormatMatches`/`StringFormatInvalid` lint kuralları, format argüman sayısı/tipi çağrı ile
  uyuşmazsa seni uyarır (`getIdentifier` tabanlı çözümde bu güvenlik kaybolur — bir sebep daha).

---

## Yerelleştirme Sistemi

Kullanıcıya görünen tüm metin `strings.xml`'de tanımlanır ve Compose'da `androidx.compose.ui.res.stringResource` ile
okunur. iOS'taki `LocalizationManager.getKey(...)` + `LocalizationKey` + `DefaultStrings` üçlüsünün Android karşılığı,
tek adımda `stringResource(R.string.x)`'tir.

### String kaynağı okuma (Compose)

```kotlin
import androidx.compose.ui.res.stringResource
import com.salticusteam.vifi.R

@Composable
fun ExamDetailHeader(exam: Exam) {
    Column {
        Text(
            text = stringResource(R.string.exam_detail_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.exam_detail_description),
            style = MaterialTheme.typography.bodyMedium,
        )
        // Interpolated — positional args, see Mandatory Rule #4
        Text(stringResource(R.string.exam_uploaded_by, exam.uploaderName))
    }
}
```

> `stringResource(...)` yalnızca `@Composable` bağlamda çağrılır. ViewModel/domain katmanında `Context` tutma
> (bkz. `rules/viewmodel-rule.md`): metni ViewModel'de çözme — ViewModel bir `@StringRes Int` veya veriyi `UiState`
> içinde yayınlar, Composable `stringResource(id, args)` ile çözer. Zorunlu durumda (ör. bildirim metni) Hilt ile
> enjekte edilmiş `@ApplicationContext Context` üzerinden `context.getString(R.string.x, args)` kullan.

---

## Yeni string ekleme

iOS'un iki-dosya kuralının aksine tek adım vardır: `res/values/strings.xml`'e kaynağı ekle, Composable'da
`stringResource(R.string.<ad>)` ile tüket. `R` yeniden üretilince id derleme-anı denetimlidir.

### `app/src/main/res/values/strings.xml`

Ad alanını **metnin temsil ettiği** özelliğe göre ön-ekle (nerede gösterildiğine göre değil).

```xml
<resources>
    <!-- ... existing strings ... -->

    <!-- Exam detail -->
    <string name="exam_detail_title">Sınav Detayı</string>
    <string name="exam_detail_description">Sınavı görüntüle veya cihazına indir.</string>
    <string name="exam_detail_download_button">İndir</string>
    <string name="exam_detail_empty">Bu derse ait sınav bulunamadı.</string>
    <string name="exam_detail_error">Sınav yüklenemedi. Lütfen tekrar deneyin.</string>

    <!-- Interpolated: positional args -->
    <string name="exam_uploaded_by">%1$s tarafından yüklendi</string>
    <string name="faculty_exam_summary">%1$s fakültesinde %2$d sınav</string>
</resources>
```

> **Sürüm notu (opsiyonel ama önerilir):** Belirli bir parite sürümüyle gelen metin grubunu XML yorumuyla işaretle:
> `<!-- Added in 3.0.0 — exam submission flow -->`. (Model/data class alanları ve enum/sealed case'lerindeki zorunlu
> `// Added in <versionName> — ...` yorumu için bkz. `rules/architecture.md`; versionName = `app/build.gradle`.)

### Composable'da tüket

```kotlin
Text(stringResource(R.string.exam_detail_title))
Button(onClick = onDownload) {
    Text(stringResource(R.string.exam_detail_download_button))
}
```

---

## Dinamik son-ek anahtarları (status / unit / type)

iOS'ta prefix + backend değeri string olarak birleştirilip `getKeyOrDefault` ile çözülüyordu. Android'de bunun idiomatik
karşılığı, enum/sealed → `@StringRes` **`when` eşlemesidir** (Kural 3). Prefix için `R.string` sabiti **oluşturma**;
eşlemeyi domain tipine bir extension olarak yaz, her bilinen değer + `UNKNOWN` için `<string>` sağla.

```kotlin
// Domain enum (plain Kotlin)
enum class SubmissionStatus { PENDING, APPROVED, REJECTED, UNKNOWN }

// Mapping extension — one @StringRes per known value + sentinel
@StringRes
fun SubmissionStatus.labelResId(): Int = when (this) {
    SubmissionStatus.PENDING  -> R.string.submission_status_pending
    SubmissionStatus.APPROVED -> R.string.submission_status_approved
    SubmissionStatus.REJECTED -> R.string.submission_status_rejected
    SubmissionStatus.UNKNOWN  -> R.string.submission_status_unknown
}
```

```xml
<!-- strings.xml — one entry per status, sentinel included -->
<string name="submission_status_pending">Onay bekliyor</string>
<string name="submission_status_approved">Onaylandı</string>
<string name="submission_status_rejected">Reddedildi</string>
<string name="submission_status_unknown">Bilinmiyor</string>
```

- Backend'den gelebilecek **her** değeri karşıla; map'i backend ile birebir tut.
- DTO→domain map sırasında bilinmeyen/boş backend değerini `UNKNOWN`'a düşür (ham Firebase tipini UI'ya sızdırma —
  bkz. `rules/firebase-data.md`).
- `else` dalı yerine exhaustive `when` kullan; backend yeni bir durum eklediğinde derleyici seni yeni case'e zorlar.

---

## Çoğul (Plurals / quantity strings)

Sayıya göre değişen metinlerde çıplak `%1$d sınav` **yetmez** — dil bilgisi doğru çoğul için `res/values/plurals.xml` +
`androidx.compose.ui.res.pluralStringResource` kullan. ViFi Türkçe-only olduğundan CLDR Türkçe kategorileri **`one`**
(tam 1) ve **`other`** (geri kalan) sağlanır.

```xml
<!-- res/values/plurals.xml -->
<resources>
    <plurals name="exam_count">
        <item quantity="one">%1$d sınav</item>
        <item quantity="other">%1$d sınav</item>
    </plurals>

    <plurals name="university_faculty_count">
        <item quantity="one">%1$d fakülte</item>
        <item quantity="other">%1$d fakülte</item>
    </plurals>
</resources>
```

```kotlin
import androidx.compose.ui.res.pluralStringResource

// count: hem miktar seçimi hem de %1$d argümanı için iki kez geçilir
Text(pluralStringResource(R.plurals.exam_count, examCount, examCount))
```

- **`other` zorunlu**, `one`'ı da her zaman ver (Türkçe grameri tam 1 için ayrı ifade kullanabilir).
- `pluralStringResource(id, count, vararg args)` — `count` kategori seçer; görünen sayı yine `args`'tan gelir, bu yüzden
  `count` genelde hem ikinci hem argüman olarak geçilir.
- Sabit (sayısız) metin için `plurals` kullanma; `plurals` yalnızca gerçekten sayıya bağlı cümleler içindir.
- `plurals`'ı da asla ham birleştirmeyle taklit etme (`if (n == 1) "..." else "..."` YASAK).

---

## Adlandırma Konvansiyonu

Kaynak adları `snake_case`, hiyerarşi **ön-ek** ile kurulur (iOS dot-notation yerine):

```
<özellik>_<alt>_<öğe>[_<tip>]

Örnekler:
- archive_title
- exam_detail_download_button
- university_list_empty
- common_data_unit_mb
- auth_phone_error_invalid_number
```

| Tip | Son-ek / desen | Örnek |
|-----|----------------|-------|
| Ekran başlığı | `_title` | `exam_detail_title` |
| TopAppBar başlığı | `_app_bar_title` | `archive_app_bar_title` |
| Buton metni | `_button` / eyleme özgü | `exam_detail_download_button` |
| Açıklama | `_description` / `_desc` | `exam_detail_description` |
| Boş durum | `_empty` | `university_list_empty` |
| Hata | `_error` / `error_<neden>` | `exam_detail_error`, `error_network` |
| Dialog başlığı | `_dialog_title` | `exam_delete_dialog_title` |
| Dialog mesajı | `_dialog_message` | `exam_delete_dialog_message` |
| İçerik açıklaması (a11y) | `cd_` (ön-ek) | `cd_navigate_back`, `cd_exam_detail_download` |

> **Erişilebilirlik metni de yerelleştirilir.** `Modifier.semantics { contentDescription = ... }` ve ikon-only
> butonların `contentDescription`'ı `stringResource(R.string.cd_...)` ile gelir — sabit string gömme. İçerik-açıklaması
> anahtarları `cd_` ön-ekiyle yazılır (ortak geri butonu kanonik olarak `cd_navigate_back`). `testTag` ise
> **çevrilmez ve asla değişmez** (UI testleriyle sözleşmedir; bkz. `rules/testing.md`, `skills/generate-ui-tests`).
> `contentDescription` kullanıcı-görünür → Türkçe + strings.xml; `testTag` koda özel kimlik → değiştirme.

---

## Mevcut ad alanları (ViFi alan modeli)

Proje kaynaklarını şu ön-eklerde topla (eksikse yenisini aç):

| Ön-ek | Amaç | Örnek |
|-------|------|-------|
| `common_` | Ortak UI öğeleri (buton, birim, hata) | `common_cancel`, `common_try_again` |
| `archive_` | Arşiv/gezinme (üniversite→fakülte→bölüm→ders) | `archive_title`, `archive_empty` |
| `university_` / `faculty_` / `department_` / `lesson_` | Katalog düğümleri | `faculty_exam_summary` |
| `exam_` | Sınav detay / indirme / görüntüleme | `exam_detail_download_button` |
| `submission_` | Sınav gönderimi + moderasyon | `submission_status_pending` |
| `auth_` | Phone Auth (+90), OTP | `auth_phone_error_invalid_number` |
| `profile_` | Profil kapısı | `profile_edit_title` |
| `notification_` | Bildirim merkezi | `notification_center_empty` |
| `onboarding_` | İlk açılış akışı | `onboarding_welcome_title` |
| `error_` | Paylaşılan hata metinleri | `error_network`, `error_unknown` |

> **Ad alanını özelliğe göre seç, görsel yerleşime göre değil.** Ana ekranda *görünen* ama sınav indirmeyi *temsil eden*
> bir metin `exam_...`'e girer, `home_...`'a değil.

---

## Composable entegrasyonu

Ekrana metin bağlamanın Android yolu: metinleri doğrudan Composable içinde `stringResource` ile okumak. iOS'taki gibi
ayrı bir "localization kurulum" adımı (ekran kurulumunda toplu metin atama) yok — her metin yerinde okunur.

```kotlin
@Composable
fun ExamDetailScreen(
    state: ExamDetailUiState,
    onDownload: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exam_detail_app_bar_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            // Icon-only back button: contentDescription is localized, no text label
                            contentDescription = stringResource(R.string.cd_navigate_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Text(stringResource(R.string.exam_detail_title))
            Text(stringResource(R.string.exam_uploaded_by, state.uploaderName))
            Button(
                onClick = onDownload,
                modifier = Modifier.testTag("examDetail.downloadButton"),   // testTag: NOT localized, stable contract
            ) {
                Text(stringResource(R.string.exam_detail_download_button))
            }
        }
    }
}
```

> Material3 `TopAppBar` + `navigationIcon` (ikon-only geri) + `navController.popBackStack()` kullan; elle/metinli geri
> butonu yok (bkz. `rules/ui-components.md`, `rules/architecture.md`). Geri ikonunun `contentDescription`'ı yine
> yerelleştirilir.

---

## Dialog yerelleştirme

Dialoglar Material3 `AlertDialog` ile kurulur; tüm metinler `stringResource` ile, standart eylemler (iptal/tamam)
`common_*` kaynaklarıyla gelir.

```kotlin
@Composable
fun DeleteExamDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exam_delete_dialog_title)) },
        text = { Text(stringResource(R.string.exam_delete_dialog_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.exam_delete_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                // Reuse the shared cancel string instead of duplicating it
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
```

---

## Hatalar ve kullanıcı metni

ViFi'de hata tipi `sealed class ArchiveError/AuthError(val userMessage: String) : Exception()`'tır ve kullanıcıya dönük
bir mesaj taşır (error-handling kuralı; bkz. `rules/architecture.md`, `rules/firebase-data.md`). **Yerelleştirme
açısından** bu `userMessage`, projenin geri kalanının aksine `strings.xml`'e **bağlanmaz**: doğrudan tipin kendi
tanımında **inline Türkçe string** olarak durur. Uygulama Türkçe-only olduğundan bu, `@StringRes` gerektirmeyen bilinçli
ve **DAR** bir istisnadır — yalnızca `*Error` sealed sınıflarının `userMessage`'ı içindir; başka hiçbir kullanıcı metni
koda gömülmez. Hata tipleri `core/model/`'de yaşar, böylece domain katmanı `Context` taşımaz (bkz. `rules/viewmodel-rule.md`).

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

ViewModel, repository'den gelen hatayı `runCatching` ile yakalar ve `userMessage`'ı `UiState.error: String?`'a koyar;
Composable yalnızca bu hazır String'i çizer (metin zaten Türkçe olduğundan `stringResource`'a gerek yoktur):

```kotlin
// ViewModel maps the caught error to UiState.error (inline Turkish, no Context needed)
runCatching { archiveRepository.catalogChildren(parentId) }
    .onSuccess { nodes -> _uiState.update { it.copy(isLoading = false, nodes = nodes) } }
    .onFailure { t ->
        _uiState.update {
            it.copy(isLoading = false, error = (t as? ArchiveError)?.userMessage ?: ArchiveError.Unknown.userMessage)
        }
    }
```

```kotlin
// UI just renders the already-localized String held in state
@Composable
fun ErrorBanner(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error)
}
```

> iOS'taki landing/`getLandingKey` gibi ayrı yerelleştirme kanalı Android ViFi'de **yoktur**: düzenli metin için tek
> kanal `strings.xml` + `stringResource`, hata `userMessage`'ları için ise yukarıdaki inline-Türkçe DAR istisnadır.

---

## Ortak yeniden kullanılabilir kaynaklar

Standart UI öğeleri için kopya oluşturma; `common_*` kaynaklarını kullan:

```xml
<!-- Buttons -->
<string name="common_cancel">İptal</string>
<string name="common_done">Tamam</string>
<string name="common_ok">Tamam</string>
<string name="cd_navigate_back">Geri</string>   <!-- icon-only back button contentDescription -->
<string name="common_try_again">Tekrar Dene</string>

<!-- Data units -->
<string name="common_data_unit_mb">MB</string>
<string name="common_data_unit_kb">KB</string>

<!-- Shared errors -->
<string name="common_error_title">Hata</string>
<string name="common_error_general">Bir şeyler ters gitti.</string>
<string name="common_error_network">İnternet bağlantını kontrol et.</string>
```

```kotlin
Text(stringResource(R.string.common_cancel))
Text(stringResource(R.string.common_try_again))
```

---

## XML kaçış (escaping) — Android tuzakları

Türkçe metinde kesme işareti çok sık geçer; `strings.xml`'de **kaçış zorunludur**, yoksa derleme hatası veya kırpılmış
metin alırsın:

| Karakter | Yanlış | Doğru |
|----------|--------|-------|
| Kesme işareti `'` | `Üniversite'nin` | `Üniversite\'nin` veya `"Üniversite'nin"` (çift tırnak içine al) |
| `&` | `Soru & Cevap` | `Soru &amp; Cevap` |
| `<` `>` | `a < b` | `a &lt; b` |
| Literal yüzde (format arg varken) | `%50 indirim` | `%%50 indirim` |
| Baştaki `@` / `?` | `@kullanici` | `\@kullanici` |
| Satır sonu/çoklu boşluk | — | `\n`, çok satır için `<![CDATA[...]]>` |

```xml
<!-- ✅ apostrophe escaped; format arg present so literal % is doubled -->
<string name="exam_discount_note">%1$s\'nin sınavlarında %%20 indirim</string>
```

---

## Kurallar (özet)

1. Kullanıcıya görünen metni **ASLA** koda gömme.
2. Tüm metin `strings.xml`'de, Türkçe; `stringResource(R.string.x)` ile oku.
3. Kaynak adı `snake_case` + özellik ön-eki (dot-notation yok).
4. Araya değer giren metinde **numaralı positional** args (`%1$s`/`%1$d`); ham birleştirme **YASAK**.
5. Sayıya bağlı metinde `plurals` + `pluralStringResource` (`one` + `other`).
6. Dinamik son-ek: enum/sealed → `@StringRes when`; `getIdentifier` **YASAK**.
7. Ortak metinleri `common_*`'tan yeniden kullan.
8. Hata metinleri (`*Error.userMessage`) inline Türkçe — tek DAR istisna; ViewModel/domain'de `Context` tutma.
9. `contentDescription` yerelleştirilir; `testTag` yerelleştirilmez ve değiştirilmez.
10. XML kaçışına uy (özellikle Türkçe kesme işareti).

---

## En iyi uygulamalar

1. **Önce var olana bak:** Yeni kaynak eklemeden `common_*` ve ilgili özellik ön-ekinde benzer var mı kontrol et.
2. **Tutarlı adlandırma:** Mevcut ön-ek/son-ek desenlerini izle.
3. **Anlamlı adlar:** `exam_detail_download_button` iyi; `button2` kötü.
4. **UI testinde metne değil `testTag`'e assert et:** Türkçe metin değişebilir; test kırılmaz
   (bkz. `rules/testing.md`, `skills/generate-ui-tests`).
5. **Yeni ekran/özellik eklerken stringleri birlikte ekle:** `skills/new-screen`, `skills/new-feature`,
   `skills/figma-to-feature` iskelesi string kaynaklarını da üretir.
6. **Mimari denetimi:** Sabit gömülü string / ham birleştirme taraması için `commands/check-architecture.md`;
   lint (`StringFormatMatches`, `MissingTranslation`) uyarılarını temiz tut.

---

## İlgili dosyalar

- `rules/architecture.md` — MVVM, UiState, sealed hata tipleri, inline Türkçe `userMessage`, versioned doc comment.
- `rules/viewmodel-rule.md` — ViewModel'de `Context` tutmama; `@StringRes`/veri yayınlama, Composable'da çözme.
- `rules/ui-components.md` — Material3 bileşenleri, `TopAppBar` + ikon-only geri, `stringResource` kullanımı.
- `rules/firebase-data.md` — DTO→domain map; ham Firebase tipini UI'ya sızdırmama (durum değerleri buradan gelir).
- `rules/date-handling.md` — tarih/sayı biçimleme; biçimlenmiş sonucu `%1$s` ile metne geçir.
- `rules/testing.md` — `testTag` sözleşmesi; metne değil tag'e assert.
- `skills/new-screen`, `skills/new-feature`, `skills/figma-to-feature` — ekran iskelesi + string kaynakları.
- `skills/generate-ui-tests` — Compose UI testlerinde `onNodeWithTag` ile `testTag` kullanımı.
- `commands/check-architecture.md` — sabit string / ham birleştirme / lint denetimi.
