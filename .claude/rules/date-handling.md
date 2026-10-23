# ViFi-Android - Tarih & Saat İşleme Kuralları

> **Kapsam:** `String` ↔ tarih tipleri arasında dönüşüm yapan her kod; özellikle backend payload'ı tüketen ya da UI'da tarih gösteren yerler.
> **Globs:** `["**/*.kt"]`

Backend zaman damgaları **ISO 8601 + offset** biçimindedir: `"2026-05-28T12:36:26+00:00"`.
Android tarafında tarih tipi `java.time` (minSdk 24 olduğu için core library desugaring açık — `app/build.gradle` `coreLibraryDesugaringEnabled`): parse/aritmetik için
`OffsetDateTime` / `Instant`, yalnız-gün değerleri için `LocalDate`. `java.util.Date` ve `Calendar`
yeni kodda KULLANILMAZ.

> **Legacy not:** Bugünkü v2.5 Java kodu tarihleri `CustomAdapter`/`ExamDetailActivity` gibi
> call-site'larda elle kurulan `SimpleDateFormat` ile çeviriyor (hem thread-safe değil, hem her
> satırda yeni allocation). Göç planında bu dağınık kullanım merkezi `DateFormats` helper'ına
> toplanır (bkz. repo kökü `ANDROID-PARITY-DETAIL.md`). Bu dosya HEDEF kuralı anlatır; yeni/ileriye
> dönük kodda aşağıdaki kanonik hat zorunludur.

## Kanonik Hat (Canonical Pipeline)

Backend tarih string'leri YALNIZCA `core/util/DateFormats.kt` içindeki merkezi helper ve
`core/util/DateExt.kt` içindeki `String` extension'ları üzerinden çözülür:

| API | Döner | Ne zaman |
|-----|-------|----------|
| `String?.toDisplayDate(format: DateDisplayFormat): String?` | `String?` | Tarihi kullanıcıya gösterirken (label, liste öğesi, özet). |
| `String?.toBackendDateTime(): OffsetDateTime?` | `OffsetDateTime?` | Aritmetik, karşılaştırma, takvim matematiği için bir tarih tipine ihtiyaç duyduğunda. |
| `String?.toInstantOrNull(): Instant?` | `Instant?` | Zaman dilimi önemsizken salt anlık (epoch) karşılaştırması gerektiğinde. |

Parser giriş biçimini kendi otomatik algılar ve kademeli olarak geriye düşer —
**backend'in hangi formatı gönderdiğini bilmek zorunda değilsin.**

Sıra:
1. ISO 8601 offset ile (`DateTimeFormatter.ISO_OFFSET_DATE_TIME`) — `"...+00:00"` / `"...Z"`
2. ISO 8601 kesirli saniye ile (`ISO_OFFSET_DATE_TIME` opsiyonel fraction'ı da yakalar)
3. Legacy `yyyy-MM-dd HH:mm:ss` (yalnız geriye dönük uyumluluk için, UTC varsayılır)

## DateDisplayFormat preset'leri

`core/util/DateDisplayFormat.kt` içinde tanımlı. iOS enum'undaki `.custom(String)` karşılığı
Kotlin'de bir `Custom` alt tipidir (enum runtime string taşıyamaz → `sealed interface`):

| Alt tip | Pattern | Örnek |
|---------|---------|-------|
| `DateDisplayFormat.DayMonthYear` | `d MMMM yyyy` | `28 Mayıs 2026` |
| `DateDisplayFormat.DottedDate` | `dd.MM.yyyy` | `28.05.2026` |
| `DateDisplayFormat.SlashDate` | `dd/MM/yyyy` | `28/05/2026` |
| `DateDisplayFormat.DashDate` | `yyyy-MM-dd` | `2026-05-28` |
| `DateDisplayFormat.DayMonthYearTime` | `d MMMM yyyy, HH:mm` | `28 Mayıs 2026, 12:36` |
| `DateDisplayFormat.TimeOnly` | `HH:mm` | `12:36` |
| `DateDisplayFormat.DashDateTime` | `yyyy-MM-dd HH:mm:ss` | `2026-05-28 12:36:26` |
| `DateDisplayFormat.Custom(pattern)` | herhangi bir pattern | `"yyyy-MM"` vb. |

Ay adları Türkçe çıksın diye display formatter'ları `Locale("tr", "TR")` ile kurulur
(bkz. `localization.md`). Yeni bir preset'i enum'a yalnızca **birden fazla yerde** tekrar
kullanılacaksa ekle; tek seferlik biçimler `Custom("...")` kullanır.

```kotlin
// core/util/DateDisplayFormat.kt
package com.salticusteam.vifi.core.util

/** Display format presets. Backend <-> display ayrımı: bunlar yalnız GÖSTERİM içindir. */
sealed interface DateDisplayFormat {
    val pattern: String

    data object DayMonthYear : DateDisplayFormat { override val pattern = "d MMMM yyyy" }        // 28 Mayıs 2026
    data object DottedDate : DateDisplayFormat { override val pattern = "dd.MM.yyyy" }            // 28.05.2026
    data object SlashDate : DateDisplayFormat { override val pattern = "dd/MM/yyyy" }             // 28/05/2026
    data object DashDate : DateDisplayFormat { override val pattern = "yyyy-MM-dd" }              // 2026-05-28
    data object DayMonthYearTime : DateDisplayFormat { override val pattern = "d MMMM yyyy, HH:mm" } // 28 Mayıs 2026, 12:36
    data object TimeOnly : DateDisplayFormat { override val pattern = "HH:mm" }                   // 12:36
    data object DashDateTime : DateDisplayFormat { override val pattern = "yyyy-MM-dd HH:mm:ss" } // 2026-05-28 12:36:26
    data class Custom(override val pattern: String) : DateDisplayFormat                           // one-off pattern
}
```

## Merkezi helper (implementation)

Tüm formatter'lar helper içinde **bir kez** kurulur ve yeniden kullanılır. `java.time`
formatter'ları immutable ve thread-safe'tir; `Custom` pattern'leri bile call-site'ta değil,
helper'ın içindeki cache üzerinden üretilir.

```kotlin
// core/util/DateFormats.kt
package com.salticusteam.vifi.core.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Single source of truth for String <-> date conversion.
 *
 * Backend timestamps are ISO 8601 with offset ("2026-05-28T12:36:26+00:00").
 * Never build a SimpleDateFormat / DateTimeFormatter at a call site — go through here.
 */
object DateFormats {

    // Turkish month names ("28 Mayıs 2026"); app is Türkçe-only.
    private val displayLocale = Locale("tr", "TR")

    // Render in the device's local zone; backend keeps UTC/offset.
    private val displayZone: ZoneId = ZoneId.systemDefault()

    // Legacy input format, assumed UTC. Kept only for backwards compatibility.
    private val legacyDateTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    // Display formatters cached per pattern so Custom(...) never re-allocates per call.
    private val displayCache = ConcurrentHashMap<String, DateTimeFormatter>()

    private fun displayFormatter(pattern: String): DateTimeFormatter =
        displayCache.getOrPut(pattern) {
            DateTimeFormatter.ofPattern(pattern, displayLocale).withZone(displayZone)
        }

    /** ISO 8601 (offset or Z) -> OffsetDateTime, with legacy fallback. Null on failure. */
    fun parseBackend(raw: String?): OffsetDateTime? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null

        // 1 + 2: ISO 8601 with offset ("Z" and fractional seconds included).
        runCatching { return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME) }
        // 2b: bare instant literal just in case.
        runCatching { return Instant.parse(value).atOffset(ZoneOffset.UTC) }
        // 3: legacy "yyyy-MM-dd HH:mm:ss", assumed UTC.
        runCatching { return LocalDateTime.parse(value, legacyDateTime).atOffset(ZoneOffset.UTC) }

        return null
    }

    /** Backend ISO string -> user-facing label, converted to the device zone. */
    fun format(raw: String?, format: DateDisplayFormat): String? =
        parseBackend(raw)?.let { displayFormatter(format.pattern).format(it) }
}
```

```kotlin
// core/util/DateExt.kt
package com.salticusteam.vifi.core.util

import java.time.Instant
import java.time.OffsetDateTime

/** Display: ISO string -> user-facing label. */
fun String?.toDisplayDate(format: DateDisplayFormat): String? =
    DateFormats.format(this, format)

/** Arithmetic/compare: ISO string -> OffsetDateTime. */
fun String?.toBackendDateTime(): OffsetDateTime? =
    DateFormats.parseBackend(this)

/** Instant-only compare (zone-agnostic): ISO string -> Instant. */
fun String?.toInstantOrNull(): Instant? =
    DateFormats.parseBackend(this)?.toInstant()
```

## Zorunlu kullanım

ViFi alan modelinden (`Exam`), repository → UiState → Composable hattında:

```kotlin
// core/model/Exam.kt
data class Exam(
    val id: String,
    val title: String,
    val lessonId: String,
    val createdAt: String,          // ISO 8601 backend timestamp (raw string in the model)
    val updatedAt: String? = null,  // Added in 3.1.0 — "son güncellenen" sıralaması için düzenleme zamanı.
)
```

```kotlin
// ✅ Display: ISO string -> user-facing label (Composable içinde)
Text(text = exam.createdAt.toDisplayDate(DateDisplayFormat.DottedDate).orEmpty())

// ✅ Aritmetik: ISO string -> OffsetDateTime ile karşılaştırma/fark
val createdAt = exam.createdAt.toBackendDateTime() ?: return
val isRecent = createdAt.isAfter(OffsetDateTime.now().minusDays(7))

// ✅ Tek seferlik format (preset gerekmez)
val yearMonth = exam.createdAt.toDisplayDate(DateDisplayFormat.Custom("yyyy-MM"))
```

ViewModel tarafında aritmetik `viewModelScope` içinde kalır; formatter nesneleri helper'dadır,
Composable ya da ViewModel alanında `DateTimeFormatter`/`SimpleDateFormat` TUTULMAZ (bkz.
`viewmodel-rule.md`).

## Yasak desenler

```kotlin
// ❌ Backend string'i çözmek için call-site'ta ASLA SimpleDateFormat kurma
val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
val date = sdf.parse(iso)                                   // YASAK (üstelik thread-safe değil)

// ❌ Backend parse'ı için call-site'ta ASLA DateTimeFormatter kurma
val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
val odt = OffsetDateTime.parse(iso, fmt)                    // YASAK

// ❌ Composable / ViewModel / RecyclerView adapter'ında formatter ALANI tutma
private val formatter = SimpleDateFormat("dd.MM.yyyy", Locale("tr"))  // YASAK

// ❌ Yeni kodda java.util.Date / Calendar ile elle hesap
val cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, -7)   // YASAK (java.time kullan)
```

## Firebase zaman damgaları (write vs read)

ISO string'ler okuma hattının normalidir. Fakat sunucu zamanını backend'e YAZARKEN string değil,
Firebase sentinel'leri kullanılır; bu değerler okunurken ISO değildir ve dönüşüm yalnız
repository'de yapılır (UI ham Firebase tipini GÖRMEZ, bkz. `firebase-data.md`):

```kotlin
// Yazarken — sunucu zamanı (saat kaymasına güvenmeyiz):
// RTDB (legacy 'Universitiess' hattı):
ref.child("createdAt").setValue(ServerValue.TIMESTAMP)
// Firestore (hedef named 'vifi' katalog hattı):
examDoc.set(mapOf("createdAt" to FieldValue.serverTimestamp()), SetOptions.merge())
```

```kotlin
// Okurken repository map'ler — RTDB epoch millis (Long), Firestore com.google.firebase.Timestamp:
fun Long?.epochMillisToInstant(): Instant? = this?.let(Instant::ofEpochMilli)
fun com.google.firebase.Timestamp.toInstant(): Instant = toDate().toInstant()
```

> Model alanı ISO string ise `toBackendDateTime()`/`toDisplayDate()` kullan; alan sunucu
> timestamp'inden geliyorsa repository `Instant`e map eder ve domain modele yazar. Her iki yolda
> da parse/format call-site'ta değil, merkezi helper'da/repository'de olur.

## Yeni bir display formatı gerektiğinde

1. Mevcut bir `DateDisplayFormat` alt tipi ihtiyacı karşılıyor mu, bak.
2. Yeni biçim **2+ yerde** kullanılacaksa `DateDisplayFormat`'a yeni bir `data object` ekle
   (preset'lerle gruplayarak) ve yukarıdaki tabloyu güncelle.
3. Gerçekten tek seferlikse `Custom("…")` kullan.

## Yeni bir giriş (parse) formatı gerektiğinde

Backend merkezi parser'ın çözemediği yeni bir tarih string'i getirirse:

1. Yeni deneme biçimini `DateFormats.parseBackend()` içine ekle.
2. Yeni denemeyi ISO 8601 denemelerinden **sonra** koy ki yaygın yol hızlı kalsın.
3. Call-site'ta format tahmini ASLA yapma.

## Test

Helper'lar saf fonksiyondur, mock gerektirmez. Birim testinde gerçek backend string'i besle
(JUnit + `kotlinx-coroutines-test` gerekirse; bkz. `testing.md`):

```kotlin
@Test
fun `backend iso formats to dotted date`() {
    val iso = "2026-05-28T12:36:26+00:00"
    assertEquals("28.05.2026", iso.toDisplayDate(DateDisplayFormat.DottedDate))
}

@Test
fun `blank or malformed string returns null`() {
    assertNull("".toBackendDateTime())
    assertNull("not-a-date".toDisplayDate(DateDisplayFormat.DottedDate))
}
```

> Not: Display çıktısı cihaz zaman dilimine çevrilir. Saate duyarlı (saat/gün kenarındaki)
> assert'lerde zamanı sabitle — testte `ZoneId`'yi kontrol et ya da `TimeOnly`/`DayMonthYearTime`
> yerine gün bazlı preset doğrula. Öğlen UTC örneği (`12:36:26+00:00`) Türkiye saatinde (+03:00)
> güvenle aynı güne düşer.

## Neden önemli

- Backend tüm zaman damgası alanlarını ISO 8601'e taşıdı; legacy Java'daki her dağınık
  `SimpleDateFormat` yeni biçimde sessizce `null`/`ParseException` üretiyordu.
- `SimpleDateFormat` thread-safe değil; `java.time` formatter'ları immutable ve paylaşılabilir —
  merkezileştirmek hem hata hem per-item allocation'ı (liste/`LazyColumn`) ortadan kaldırır.
- Tek doğruluk kaynağı → backend formatı değişirse **tek** kod değişikliği.

---

**Ayrıca bkz.:**
- `core/util/DateFormats.kt`, `core/util/DateDisplayFormat.kt`, `core/util/DateExt.kt` — implementation
- `architecture.md` — genel proje mimarisi
- `firebase-data.md` — DTO → domain map (modeller ham tarihi string tutar, asla tipli `Date` değil) ve Firebase timestamp dönüşümü
- `localization.md` — Türkçe locale ve kullanıcıya görünen metin kuralları
- `viewmodel-rule.md` — ViewModel'de formatter/context alanı tutma yasağı
