# ViFi Android — 4.2.0 Ayrıntı Eki (yapılandırılmış sınavlar ve sözleşme özeti)

> Bu ek [ANDROID-4.2.0-ROADMAP.md](ANDROID-4.2.0-ROADMAP.md)'nin kalemlerine kaynaklık eder. İçerik 21 Kasım 2026'da
> iOS sözleşme belgesi (`ViFi-iOS/STRUCTURED-EXAMS.md`, yerel ve git dışı; yetkili kaynak `firebase/functions/conversions/schema.js`)
> salt okunarak özetlenerek çıkarıldı. Android kodu (`v-4.2.0`, SCT-01 `4a29947`) yalnız test eşlemesi için tarandı.
>
> - **Esas olan kaynaktır.** Bir maddeyle kod çelişirse iOS kodu ve `schema.js` geçerlidir. Belge ile kod ayrışırsa kod doğrudur;
>   ayrışma iOS deposuna bildirilir. Kalemi uygulamadan önce ilgili iOS dosyasını aç.
> - **Gizlilik:** bu ekte kullanıcı kimliği, telefon, e-posta, token, anahtar, sunucu sırrı yoktur. Örnekler genel sınav adlarıdır.
> - **Dil:** olgular Türkçe özet, teknik adlar (koleksiyon, alan, callable, sınıf) orijinal haliyle. Kullanıcıya görünen metinler
>   iOS'taki birebir Türkçe metinlerdir ve `strings_*.xml`'e aynen taşınır.
> - **Test eşlemesi kabul kriteridir:** her iOS test vakası, Android'de aynı davranışı doğrulayan bir testle eşlenir (bkz. §9).
>   Henüz yazılmamış vakalar "Hat X'te yazılacak" diye işaretlidir.

| Bölüm | Konu | Kullanan kalemler |
|---|---|---|
| §1 | Yönlendirme kuralı ve yapılandırılmış sınavın özeti | `SQS-01`, `SQS-02`, `ArchiveRow` |
| §2 | Firestore koleksiyonları ve alan şemaları | `SQS-*`, `PRC-01`, `ADM-*`, `ADX-*`, `SMP-*` |
| §3 | Kimlik ve Storage yol biçimleri | `SQS-04`, `SMT-04` |
| §4 | Sınav adı ayrıştırıcısı ve Add Exam listesi | `SMP-01`, `ADX-*` |
| §5 | Markdown + LaTeX alt kümesi ve çizim kuralları | `SMT-01`…`SMT-04`, `SQS-02` |
| §6 | Callable sözleşmeleri | `PRC-01`, `PRC-04`, `ADM-*`, `ADX-*` |
| §7 | Pratik modu (Sınava gir) | `PRC-01`…`PRC-04`, `SIT-01` |
| §8 | Erişilebilirlik, şekiller, ekran koruması, analytics | `SQS-04`, `SQS-05`, `SQS-01` |
| §9 | iOS test ↔ Android test eşlemesi | `SIT-02`, `SIT-03` |
| §10 | Canlı ortam ayrışmaları (SOPS-01) | `SQS-*`, `PRC-*`, `ADM-01` |
| §11 | Açık noktalar | — |

---

## 1. Yönlendirme ve veri modeli özeti

- **Tek karar:** bir sınav `exams/<id>` dokümanında `format == "structured"` **ve** `questionCount > 0` ise soru ekranı açılır;
  başka koşul yoktur. Diğer her durumda sayfa görüntüleyici (dosya görünümü) açılır.
- **Uzaktan anahtar yok (sahip kararı K25):** Android bu kararı uzak yapılandırmayla değiştirmez; `config/...` okunmaz.
- **`questionCount` okuma kuralı:** `format` `structured` değilse `null`; değilse ve değer int ve > 0 ise o değer; aksi halde `null`.
- **Yer değiştirme (SQS-01):** Özetsiz açılan sınav (son görüntülenenler, bildirim) önce sayfa görünümüyle yüklenirse ve belge
  `structured` çıkarsa soru ekranına yer değiştirilir. Orijinal sayfalara otomatik dönüş yoktur (K23).
- **Yapılandırılmış sınavın `files` listesi silinmez.** Eski istemciler (iOS 4.1.0, eski Android) bunu kullanır.
- **Kilit kuralı değişmedi.** Kilitli sınav (coin / Premium / satın alma) soru ekranına ulaşmaz. "Cevabı Göster" ek kilit taşımaz (K4).
- **Android yalnız okur** (kullanıcı tarafı). `exams`, `questions`, `questionPoolIndex`, `subjects`, `examConversions` istemciden yazılmaz.

---

## 2. Firestore koleksiyonları (isimli `vifi` veritabanı)

Erişim her zaman `FirebaseFirestore.getInstance(app, "vifi")` ile yapılır (DEFAULT DB'de koleksiyonlar yoktur).

### 2.1 `exams/<id>` (var olan doküman; yapılandırılmış sınavda eklenen alanlar)

| Alan | Tür | Anlam |
|---|---|---|
| `format` | string | `"structured"` ise sorular vardır; yoksa ya da başka değerse yalnız sayfa görünümü |
| `questionCount` | int | Yayınlanmış soru sayısı (> 0 olmalı) |
| `structuredAt` | timestamp | Yapılandırıldığı an |
| `conversionJobId` | string | `exam-<examId>` ya da `sub-<submissionId>`; yalnız admin işine bağlar, istemci kullanmaz |
| `files` | `[{storagePath, order}]` | Korunur (eski istemciler için) |

### 2.2 `questions/<examId>-<nnn>` (yayınlanmış soru; düz koleksiyon)

- **Okuma:** +90 telefonla girişli kullanıcılar ve admin (`canReadArchive()`). **Yazma:** kimse (yalnız sunucu).
- `<examId>`: `^[A-Za-z0-9_-]{1,700}$`. `<nnn>`: 1 tabanlı sıra, 3 haneye sıfırla (`001`…`999`). Kimlik kalıcıdır.
- **Tek sorgu:** `questions where examId == <examId> order by order`; bileşik indeks `questions (examId ASC, order ASC)` (canlıda READY, SOPS-01).
- Sınav başına ~10–30 okuma. Sorular, havuzlar ve şekiller **önceden çekilmez** (arşiv ön ısıtması soru indirmez).

| Alan | Tür | Sınır / anlam |
|---|---|---|
| `schemaVersion` | int | `1`. `> 1` ise soru **atlanır** |
| `examId` | string | Sınav kimliği |
| `order` | int | 1…999; sıralama anahtarı |
| `label` | string | ≤ 8 karakter; kâğıttaki numara ("1", "2a"). Yoksa `order` |
| `text` | string | ≤ 8000; Markdown + LaTeX (§5). Boşsa soru atlanır |
| `speech` | string | ≤ 8000; ekran okuyucu metni (§8). Yoksa `text`'in LaTeX'siz hâli |
| `choices` | `[{label, text}]` | ≤ 10; `label` ≤ 4; `text` ≤ 2000. Çoktan seçmeli değilse `[]`. Boş `label`/`text` girdisi atlanır |
| `figures` | `[{storagePath, width, height}]` | ≤ 6; `width`/`height` px (int) ya da `null` |
| `answer` | map | `{text, choice?, speech?}`: `text` ≤ 4000 (boşsa soru atlanır); `choice` ≤ 4 ve `choices` içinde olmalı; `speech` ≤ 8000 |
| `solution` | `[string]` | ≤ 30 adım; adım ≤ 2000 |
| `topics` | `[string]` | ≤ 8 slug, her biri ≤ 60; 4.2.0'da gösterilmez |
| `difficulty` | int | 1…3; aralık dışı kıstırılır; yoksa 2 |
| `hasFigure` | bool | `figures` boş değilse true |
| `subjectId`, `courseId`, `universityId`, `facultyId`, `departmentId` | string \| null | Sorgu/havuz anahtarları; 4.2.0 istemcisi kullanmaz |
| `examType` | string \| null | 17 tür (§4); yoksa `null` |
| `year` | int \| null | Sınav adından (§4) |
| `updatedAt` | timestamp | Admin düzeltince değişir; id değişmez |

**Atlama ve yumuşak düşme kuralları:**
- Doküman atlanır: `schemaVersion > 1`, `order` yok ya da < 1, `text` boş, `answer.text` boş.
- Yumuşak düşme: `label` yok → `order`; `difficulty` yok → 2; bozuk `choices` girdisi → yalnız o girdi atlanır.
- Sıralama `order`'a göre, eşitlikte `id`'ye göre.

### 2.3 `questionPoolIndex/<subjectId>` (istemci okur: tek doküman)

`{subjectId, name, types: {<pratik türü>: {tr: n, "u-<universityId>": n, "c-<courseId>": n}}, updatedAt}`.
Pratik keşfi için **tek okuma**. Yalnız pratiğe uygun türler listelenir (§4 kural 5). Doküman yoksa ya da hiçbir kapsamda soru yoksa
"Sınava gir" düğmesi görünmez.

> **Ayrım:** `questionPools/<subjectId>__<examType>__<tr|u-…|c-…>[__pN]` = `{ids[], count, updatedAt}` **id listeleridir**; istemci
> okumaz (ileride "Sınava gir" modu için; parça başı ≤ ~15 bin id). `questionPoolIndex` ise yalnız sayım özetidir. İki koleksiyon
> karıştırılmaz.

### 2.4 `subjects/<subjectId>` ve ders düğümü

- `subjects/<subjectId>` = `{name, aliases[]}`: ülke geneli ders kimliği.
- `catalogNodes/<course>.subjectId` bu kimliğe bağlanır. Ders düğümünde `subjectId` yoksa pratik düğmesi gösterilmez.

### 2.5 Admin ve yalnız-sunucu koleksiyonları (istemci okumaz ya da yazmaz)

- **`examConversions/<jobId>`** (yalnız `isAdmin()` okur): `jobId` = `exam-<examId>` ya da `sub-<submissionId>`
  (`^(exam|sub)-[A-Za-z0-9_-]{1,700}$`). Alanlar: `source{type: legacy|submission, examId?, submissionId?}`,
  `placement{examId, universityId, facultyId, departmentId, courseId, names[5], subjectId?, subjectName?, originalTitle, suggestedTitle}`,
  `files[{storagePath, order}]`, `kind` (`JPG`|`PDF`), `status`, `attempts`, `error`, `model{provider, id}`,
  `usage{inputTokens, outputTokens, costUsd}`, `requestedBy`, `reviewedBy`, `createdAt`, `requestedAt`, `readyAt`, `publishedAt`,
  `questionCount`, `needsReviewCount`, `unreadable[{page, reason}]`, `budget?` (`oneTime`), `promptVersion`, `subjectMove?`.
  Durumlar: `queued` → `processing` → `ready` → `published`; `failed`; `discarded`. `published` ve `discarded` terminaldir.
- **Taslak sorular `examConversions/<jobId>/questions/<nnn>`:** yayınlanmış soru şekli + `confidence` (0…1), `needsReview`,
  `provider`, `modelId`, `promptVersion`, `generatedAt`, `editedBy`, `editedAt`, `pendingFigures[]`, `figureBoxes`,
  `regenerationLeaseUntil` (tek soru yeniden üretilirken kira bitişi), `regenerationError`. Admin yalnız
  `text, speech, choices, answer, solution, needsReview, editedBy, editedAt` alanlarını günceller
  (`editedBy == auth.uid`, `editedAt == request.time`).
- **Gönderim dokümanı (`submissions`):** iki aşamalı onayda 1. onay coin + dosya kopyası + `publishPlan` yazar ve **arşive
  yayınlamaz**. Gönderimde sağlayıcı alanı **`conversionProvider`** (SOPS-01 canlı gözlemi). `publishPlan`'ın alan şekli
  iOS `ArchivePublishPlan.publishPlanDocument` ile birebir olmalıdır; bu belgede alan listesi yoktur, `ADM-01` öncesi iOS
  kodundan okunacaktır.
- **`aiUsage/<YYYY-MM>`:** aylık yapay zekâ harcaması (aylık 50 $ sınır); yalnız admin okur. Ay anahtarı Europe/Istanbul takvim ayıdır.
- **`users/<uid>/practice/daily`:** pratik günlük sayacı; sahibi okur, istemci yazmaz (sunucu yazar).

---

## 3. Kimlik ve Storage yol biçimleri

| Ne | Biçim |
|---|---|
| Yayınlanmış soru | `<examId>-<nnn>` (`nnn` = 001…999) |
| Taslak soru | `<nnn>` (iş altında) |
| Dönüştürme işi | `exam-<examId>` / `sub-<submissionId>` |
| Şekil dosyası | `questionFigures/<jobId>/<nnn>-<k>.jpg` (`k` = 1…6); yeniden üretimde `…/<nnn>-<k>-r<rev>.jpg` (`rev` ≥ 2) |
| Havuz | `<subjectId>__<examType>__<tr\|u-<id>\|c-<id>>` (+ `__p2`…) |

- Şekil yolunu istemci **üretmez**; `figures[].storagePath` aynen kullanılır.
- Önbellek anahtarı olarak **yol** kullanılır, soru kimliği değil (yeniden üretimde yol değişir, eski görsel gösterilmez).
- Storage kuralı: `questionFigures/{iş}/{dosya}` yalnız **tek dosya okuma** (`get`), +90 kullanıcı ve admin. Listeleme ve yazma yasak.

---

## 4. Sınav adı ayrıştırıcısı ve Add Exam listesi

### 4.1 Slug ve ayrıştırma kuralları

1. **Slug:** Türkçe harfler ASCII'ye (`İ I ı→i`, `Ğ ğ→g`, `Ü ü→u`, `Ş ş→s`, `Ö ö→o`, `Ç ç→c`), sonra küçük harf. **Yalnız ASCII `a-z0-9`**
   korunur; diğer her karakter ayırıcıdır; ardışık ayırıcılar tek `-`; baş/son `-` yok; boşsa `unknown`. Sunucu girdiyi Unicode NFC'ye çevirir.
2. **Yıl:** slug `-` ile bölünür; `19` ya da `20` ile başlayan 4 haneli ilk sayı → `year`. Hemen ardından bir yıl daha geliyorsa
   (akademik yıl aralığı) **ikinci yıl** alınır ("2017-2018" → 2018). Yoksa `null`.
3. **Tür:** `-<slug>-` içinde aşağıdaki iğneler **bu sırayla** aranır, ilk bulunan kazanır. İğne `-<iğne>-` olarak aranır (kelime sınırı):

   | Sıra | Tür | İğneler |
   |---|---|---|
   | 1 | `calisma-sorulari` | `calisma-sorulari` |
   | 2 | `cozumlu-sorular` | `cozumlu-sorular` |
   | 3 | `laboratuvar` | `laboratuvar`, `laboratuar`, `lab` |
   | 4 | `odev` | `odev`, `odevi`, `odevler` |
   | 5 | `proje` | `proje`, `projesi` |
   | 6 | `butunleme` | `butunleme` |
   | 7 | `mazeret` | `mazeret` |
   | 8 | `tek-ders` | `tek-ders` |
   | 9 | `ek-sinav` | `ek-sinav`, `ek-sinavi` |
   | 10 | `muafiyet` | `muafiyet` |
   | 11 | `seviye-tespit` | `seviye-tespit` |
   | 12 | `uygulama` | `uygulama-sinavi`, `uygulama-sinav` (tek başına "UYGULAMA" tür değildir) |
   | 13 | `kisa-sinav` | `kisa-sinav`, `kisa-sinavlari`, `quiz` |
   | 14 | `ara-sinav` | `ara-sinav`, `arasinav` |
   | 15 | `vize` | `vize` |
   | 16 | `final` | `final` |
   | 17 | `yaz-okulu` | `yaz-okulu` (yalnız eski arşiv; yaz okulu vize/ara/final temel türüne ayrışır) |

   Hiçbiri yoksa `examType = null`. Materyal iğneleri sınav iğnelerinden önce gelir ("FİNAL ÇALIŞMA SORULARI" materyaldir).
4. **Tür kümesi** (sıralı): `vize, final, butunleme, mazeret, kisa-sinav, yaz-okulu, ara-sinav, tek-ders, ek-sinav, muafiyet,
   seviye-tespit, uygulama, laboratuvar, odev, proje, calisma-sorulari, cozumlu-sorular`.
5. **Pratiğe uygunluk (K27):** materyaller (`laboratuvar`, `odev`, `proje`, `calisma-sorulari`, `cozumlu-sorular`) dışındaki her tür
   pratiğe uygundur (`ExamType.isPracticeEligible`). Materyaller arşivde kalır, havuz kaydı almaz.

**Ortak test vakaları:** iOS 36 başlık vakası (`ExamTitleParserTests`, `conversionSchema.test.mjs` ile aynı). Örnekler:
`2019 FİNAL` → final/2019; `2017-2018 VİZE` → vize/2018; `2019 2020 FİNAL` → final/2020; `KISA SINAV 3` → kisa-sinav/null;
`2020 YEDEK SINAV` → null/2020; `2019 VİZE YAZ OKULU` → vize/2019; `2023 YAZ OKULU` → yaz-okulu/2023; `QUIZ` → kisa-sinav/null;
`FİNAL ÇALIŞMA SORULARI` → calisma-sorulari/null; `DERS NOTU` → null/null.

> İstemci bu ayrıştırıcıyı yayınlanmış sorular için **kullanmaz**; alanlar sunucuda dolar. Android ayrıştırıcısı (`ExamTitleParser`)
> yalnız liste başlıklarını doğrulamak ve ileride yerel süzme için gerekir.

### 4.2 Yeni yapıya geçen sınavın başlığı (K28, K29)

- Başlık biçimi: **`[<yıl> ]<liste adı>[ <sıra no>]`**. Sınav kimliği değişmez; gönderimin kendi `exam` metni aynen kalır.
- **Yıl:** tek 4 haneli yıl ya da yok; akademik yıl aralığında ikinci yıl.
- **Ad:** §4.1'deki sırayla tür bulunur ve listedeki ada çevrilir (LAB → LABORATUVAR, QUIZ → KISA SINAV, ÖDEVLER → ÖDEV). Başlıkta "yaz okulu"
  geçiyorsa vize/ara sınav/final → YAZ OKULU VİZE / ARA SINAV / FİNAL. Yalnız "YAZ OKULU" yazan başlık tanınmaz.
- **Sıra no:** tür kelimesinin hemen ardındaki 1–99 sayı ("QUIZ 5" → "KISA SINAV 5"). Diğer kelimeler atılır.
- **Tanınmayan ad** ("DERS NOTU", "SİSTEM", "POSTER", "2019 TEK") → öneri yok; admin listeden ad seçmeden yayınlayamaz.
- **Admin değiştirebilir:** iş `placement.originalTitle` ve `placement.suggestedTitle` (`null` = öneri yok) taşır;
  `publishExamConversion { …, examTitle? }` yalnız liste biçimini kabul eder (`validateExamTitle`: Türkçe büyük harf, yıl tek 4 hane,
  sıra no 1–99 baştaki sıfırsız).

| Başlık | Liste adı |
|---|---|
| `2019 KISA SINAV 2` | 2019 KISA SINAV 2 |
| `2017-2018 VİZE` | 2018 VİZE |
| `2019 FİZİK LAB` | 2019 LABORATUVAR |
| `2019 VİZE YAZ OKULU` | 2019 YAZ OKULU VİZE |
| `QUIZ 3` / `QUIZ 5` | KISA SINAV 3 / KISA SINAV 5 |
| `2023 YAZ OKULU`, `DERS NOTU`, `2022 UYGULAMA` | (öneri yok) |

### 4.3 Add Exam sabit listesi (22 ad, iOS `ExamNameCatalog` ile aynı sıra)

Gönderilen `exam` metni `"<yıl> <AD>"`; serbest metin yok. Android `ExamNameCatalog.entries` (SCT-01) bu 22 adı taşır; liste
`SMP-01`'de son kez doğrulanır.

| Sıra | Ad | `id` | Tür |
|---|---|---|---|
| 1 | VİZE | `vize` | vize |
| 2 | ARA SINAV | `ara-sinav` | ara-sinav |
| 3 | FİNAL | `final` | final |
| 4 | BÜTÜNLEME | `butunleme` | butunleme |
| 5 | MAZERET | `mazeret` | mazeret |
| 6 | TEK DERS | `tek-ders` | tek-ders |
| 7 | EK SINAV | `ek-sinav` | ek-sinav |
| 8 | YAZ OKULU VİZE | `yaz-okulu-vize` | vize |
| 9 | YAZ OKULU ARA SINAV | `yaz-okulu-ara-sinav` | ara-sinav |
| 10 | YAZ OKULU FİNAL | `yaz-okulu-final` | final |
| 11 | KISA SINAV | `kisa-sinav` | kisa-sinav |
| 12 | KISA SINAV 1 | `kisa-sinav-1` | kisa-sinav |
| 13 | KISA SINAV 2 | `kisa-sinav-2` | kisa-sinav |
| 14 | KISA SINAV 3 | `kisa-sinav-3` | kisa-sinav |
| 15 | MUAFİYET SINAVI | `muafiyet` | muafiyet |
| 16 | SEVİYE TESPİT SINAVI | `seviye-tespit` | seviye-tespit |
| 17 | UYGULAMA SINAVI | `uygulama` | uygulama |
| 18 | LABORATUVAR | `laboratuvar` | laboratuvar |
| 19 | ÖDEV | `odev` | odev |
| 20 | PROJE | `proje` | proje |
| 21 | ÇALIŞMA SORULARI | `calisma-sorulari` | calisma-sorulari |
| 22 | ÇÖZÜMLÜ SORULAR | `cozumlu-sorular` | cozumlu-sorular |

QUIZ ayrı bir ad değildir; arşivdeki "QUIZ" başlıkları `kisa-sinav`'a ayrışır.

---

## 5. Markdown + LaTeX alt kümesi ve çizim

`text`, `choices[].text`, `answer.text` ve `solution[]` aynı biçimdedir: **satır içi Markdown + LaTeX**.

### 5.1 Ayırıcı (tokenizer) kuralları (Pandoc `$` kuralı)

Çıktı sıralı parçalar: `text(s)`, `inlineMath(latex)`, `displayMath(latex)`. Hata fırlatmaz; okunamayan her şey `text` olur.

- `\$` → düz metinde tek `$` (formül başlatmaz/bitirmez; formül kaynağında `\$` korunur).
- `$$ … $$` → `displayMath` (kendi satırında). Baş/son boşluk-satır sonu kırpılır; kırpınca boşsa parça atılır. Kapanış yoksa `$$` metindir.
- `$ … $` → `inlineMath`. Açılıştan sonra boşluk ve `$` olmamalı; kapanıştan önce boşluk olmamalı ve kapanıştan sonra ASCII rakam olmamalı.
  `$5 ve $6 tutar` ve `Maliyet $ 10 $ olur` metindir; `$x$.` formüldür.
- Satır içi formül paragraf sınırını (`\n\n`) aşmaz; kapanışsız `$` metindir.
- Ters bölü sonrası karakter atlanır (`\x`). Ardışık formüller sırasını korur.

| Girdi | Parçalar |
|---|---|
| `Bir soru` | text("Bir soru") |
| `$x^2$ ifadesi` | inline("x^2"), text(" ifadesi") |
| `Fiyat \$5 ve $x$` | text("Fiyat $5 ve "), inline("x") |
| `$a\n\nb$` | text("$a\n\nb$") |
| `$\frac{1}{$` (bozuk) | inline("\frac{1}{") — çizimde kaynak metin |
| `önce $$  $$ sonra` | text("önce  sonra") |

`plainText(girdi)`: ayırıcılar atılır, formül kaynağı korunur; ekran okuyucu için son çare (§8).

### 5.2 Metin parçaları (Markdown alt kümesi)

- Yalnız **satır içi** Markdown: `**kalın**`, `*italik*`, `` `kod` `` (tek aralıklı, ~%92 boyut), `~~üstü çizili~~`.
- Boşluklar korunur. Ayrıştırma başarısızsa düz metin gösterilir.
- Başlık, liste, tablo, bağlantı gibi blok yapıları **yorumlanmaz** (düz metin görünür). Satır sonları paragraf içinde korunur.

### 5.3 Formül çizimi

- iOS: SwiftMath 1.7.3. Satır içi formül metnin taban çizgisinde akar; ekran formülü kendi satırında ortalanır ve metin boyutunun ~1,1 katıyla çizilir.
- **Sığmayan formül alt satıra geçer, yana kaymaz (20 Kasım 2026, sahip kararı).** Bölme kuralı (`MathLineBreaker`, iOS `MathLineBreakerTests`, 6 vaka):
  yalnız üst düzeyde; `{…}` grupları, `\left…\right`, `\begin…\end` ve `(…)` / `[…]` içi asla bölünmez; `\{…\}` engel değildir.
  Kırılma noktaları: virgülden sonra; ilişki (`= < > \leq \geq \neq \in \subset \to \Rightarrow \iff` …) ve ikili işlem (`+ - \times \cdot \cup \cap \pm \setminus` …)
  işareti kendi başına parça olur. `+`/`-` yalnız bir işlenenden sonra sayılır; işaret sayısından ayrılmaz. Parçalar kırpılır, metin boşluğuyla birleşir.
  Bölünemeyen parça satır genişliğine küçültülür. Sığan formül tek parça çizilir.
- **Boyut:** yazı ölçeğini izler (iOS: gövde stili, en çok 34 pt). Android'de gövde ≈ 17 sp; ölçek formül boyutuna uygulanır.
- **Çizilemeyen formülde çökme yoktur:** kaynak satır içinde `$…$` metin olarak gösterilir.
- Yalnız `$…$` ve `$$…$$` kullanılır; `\(…\)` ve `\[…\]` sunucuda üretilmez.

**Android motoru (SPK-01, şartlı geçti):** AndroidMath `v1.1.0` (MIT, JitPack) `MTTypesetter` + Compose `Canvas` ile; typeset ve çizim tek iş parçacığında;
bitmap önbelleği; test bağımlılıkları `exclude`; `READ_/WRITE_EXTERNAL_STORAGE` `tools:node="remove"`; R8 için
`-keep class com.pvporbit.freetype.** { *; }` (yoksa release'te native çökme). Ayrıştırıcı ASCII dışı harfleri siler (`\text{hız}` → "hz");
ürün sahibinin kararıyla uygulama içi yama (yer tutucu + geri koyma). Ayrıntı ve risk: roadmap `SPK-01` notu.

### 5.4 LaTeX uyumluluk tablosu (`LATEX_REPLACEMENTS`, sunucu ve iOS `LatexCompatibility` aynı)

Sunucu yazılan her metne bu tabloyu uygular; uygulamalar her formülü çizmeden önce aynı tabloyu uygular.

| Komut | Yerine | Komut | Yerine |
|---|---|---|---|
| `\varnothing` | `\emptyset` | `\lvert`, `\rvert` | ASCII dikey çizgi |
| `\dfrac`, `\tfrac` | `\frac` | `\lVert`, `\rVert` | `\Vert` |
| `\dbinom`, `\tbinom` | `\binom` | `\operatorname` | `\mathrm` |
| `\leqslant` / `\geqslant` | `\leq` / `\geq` | `\coloneqq` | `:=` |
| `\implies` / `\impliedby` | `\Longrightarrow` / `\Longleftarrow` | `\bigl \bigr \Bigl \Bigr \biggl \biggr \Biggl \Biggr \big \Big \bigg \Bigg` | (silinir) |
| `\dots` | `\ldots` | `\nonumber` | (silinir) |

Eşleşme kuralı: regex `\\\\|\\([A-Za-z]+)`, soldan sağa. Önce kaçışlı ters bölü `\\` olduğu gibi geçer; sonra `\` + harflerin **tamamı** komut adıdır.
`\big`, `\bigcup`'a dokunmaz. İşlem idempotenttir. Vakalar: iOS `LatexCompatibilityTests` (4) ↔ Android `LatexCompatibilityTest` (4).

### 5.5 Kart düzeni (sıra)

Başlık "`<label>. Soru`" → soru metni (paragraflar + ekran formülleri) → şekiller (sırayla) → şıklar (`A`, `B` …; cevap açıkken doğru şık işaretlenir)
→ "Cevabı Göster". Cevap açılınca: ayırıcı çizgi → "Cevap" → (şıklıysa) "Doğru seçenek: <harf>" → cevap metni → (varsa) "Çözüm" ve numaralı adımlar (`1.`, `2.` …)
→ yapay zekâ notu (§8). Cevap durumu ekran içidir; Firestore'a yazılmaz. Turuncu üstünde **siyah** metin.

**Soru etiketi (21 Kasım):** "Soru 1)" gibi etiketler çıplak numaraya indirilir (`normalizeQuestionLabel`; iOS `QuestionLabel`, 3 vaka).
Örnekler: "Soru 1)"→"1", "(3)"→"3", "S.1"→"1", "Soru 2a)"→"2a", "2-b"→"2b", "3A"→"3A", "Ek Soru"→"Ek Soru".

---

## 6. Callable sözleşmeleri (europe-west1)

### 6.1 Pratik

`nextPracticeQuestions` (ayrıntı §7):
- **Girdi:** `{subjectId, examType, scope: course|university|tr, courseId? (course için zorunlu), universityId? (university için zorunlu), exclude: [≤ 300 id], count: 1…10, subscriptionProof?}`.
- **Çıktı:** `{questions, poolSize, remainingToday | null, limitReached}`.
- Soru şekli = yayınlanmış soru (§2.2) + `id`, `examTitle`, `universityName`; `updatedAt` epoch ms.

### 6.2 Admin dönüştürme (Android admin ekranı yazılırsa gerekir; kullanıcı uygulaması kullanmaz)

Her AI işleminde `provider` (`claude` | `gemini`) zorunludur; istemcide varsayılan model yoktur.

| Callable | Girdi |
|---|---|
| `requestExamConversion` | `{examId, provider}` |
| `requestBulkConversion` | `{courseId \| departmentId, provider, dryRun}` |
| `regenerateExamConversion` | `{jobId, provider}` |
| `regenerateDraftQuestion` | `{jobId, order, provider}` |
| `publishExamConversion` | `{jobId, subjectId?, newSubjectName?, examTitle?}` |
| `discardExamConversion` | `{jobId}` |
| `updatePublishedQuestion` | `{questionId, text?, speech?, choices?, answer?, solution?}` |
| `regeneratePublishedQuestion` | `{questionId, provider}` |

Admin claim (`admin: true`) gerekir. Bu sekiz callable iOS'ta kullanılır; Android'de `ADX-*` hattı karşılar.

### 6.3 Ders eşleme (admin, isteğe bağlı ekran)

| Callable | Girdi | Not |
|---|---|---|
| `listSubjects` | — | Ders listesi |
| `setCourseSubject` | `{courseId, subjectId \| newSubjectName}` | Taşıma K21 yolundan geçer, havuz özetini günceller |
| `renameSubject` | `{subjectId, name}` | Eski ad takma ad olur |
| `mergeSubjects` | `{fromSubjectId, intoSubjectId}` | Kendine birleştirme reddedilir |

### 6.4 Sunucu tetikleyicileri (istemci çağırmaz)

Bildirim ve dönüştürme tetikleyicileri (ör. `conversionReady`) sunucuda çalışır; Android yalnız bildirimi alır (`SMP-04`).

---

## 7. Pratik modu ("Sınava gir", P1–P5, 14 Kasım sahip kararları)

**Keşif:** ders düğümünün `subjectId`'si varsa `questionPoolIndex/<subjectId>` tek okunur (§2.3). Kapsamlar: **Bu ders** (`c-<courseId>`),
**Üniversitem** (`u-<universityId>`, kullanıcının dersinin üniversitesi), **Türkiye geneli** (`tr`). 1 soruluk kapsam da seçilebilir; sayı yanında yazar.

**Erişim (P1):**
- Ücretsiz: İstanbul günü (00:00 Europe/Istanbul) başına **5 yeni soru**. Sayaç sunucuda; aynı gün tekrar gelen soru hak düşürmez.
- `limitReached == true` → paywall. Premium: `remainingToday == null`, sınırsız.
- **Abonelik kanıtı:** iOS StoreKit 2 JWS; Android `{platform: 'android', purchaseToken, productId}`. Sunucu doğrulaması `SRV-01` ile
  hazırlandı (ortak depo dalı; birleştirme ve deploy `SRV-02`'de ürün sahibi onayıyla). Doğrulanana kadar Android Premium kullanıcısı
  ücretsiz sayılır; geçersiz ya da eksik kanıt hata değildir, ücretsiz kurallar uygulanır.
- Sunucu doğrulanmış aboneliği en çok 24 saat hatırlar; istemci kanıtı **her çağrıda** gönderir.
- Bir abonelik tek hesaba bağlanır; başka hesapla kullanan ücretsiz kurala düşer.

**Oturum (P2, P3):**
- Sınırsız uzunluk; kullanıcı istediği an bitirir. Doğru/yanlış takibi, bitiş özeti ve "Yanlışlarım" **yoktur**; yalnız "Cevabı Göster".
- Kart başlığı orijinal numara değil oturum sırasıdır: **"Soru 1", "Soru 2"…**. "Baştan başla" 1'den başlar.
- Üstte kaynak satırı: "BOZOK ÜNİVERSİTESİ · 2019 FİNAL" biçimi (`universityName · examTitle`).
- `exclude` = oturumda görülen en yeni 300 id. Boş dönen cevap = havuz bitti → "Baştan başla" `exclude`'u temizler.
- **Ön-çekme yalnız Premium'da.** Sunucu hakkı soruyu verirken düşer; ücretsiz kullanıcıda önceden istenip görülmeyen soru hakkı boşa harcar.

**Admin ders eşleme (P5):** §6.3. Android admin ekranı isteğe bağlıdır (`ADX-06`).

**Analytics:** `practice_started` (`exam_type`, `scope`), `practice_answer_revealed`, `practice_limit_reached`, `practice_paywall_opened`.

---

## 8. Erişilebilirlik, şekiller, ekran koruması, analytics

### 8.1 Erişilebilirlik (TalkBack)

Formüller LaTeX olarak **okunmaz**. Kart iki semantik düğüm taşır:
1. **Soru:** `speech` (boşsa `plainText(text)`) → şıklar ("A: …") → şekil varsa "Şekil var.".
2. **Cevap** (açıkken): `answer.speech` (yoksa `plainText(answer.text)`) → çözüm adımları → yapay zekâ notu.

Cevap kapalıyken ikinci düğüm erişilebilirlik ağacında **yoktur** (cevap sızmaz). Başlık "`<label>. Soru`" başlık olarak işaretlenir.
Yazı ölçeği büyürse kart uzar; metin üst sınırla ölçeklenir.

### 8.2 Şekiller

- `figures[]` sırasıyla, soru metninden sonra ve şıklardan önce çizilir.
- Yer tutucu oranı korur (bilinmiyorsa yükseklik = genişliğin 2/3'ü); oran 0,1–2 aralığına kıstırılır, indirme bitince kart zıplamaz. Beyaz zemin, köşe yarıçapı 8, `scaleAspectFit`.
- **Görünür olunca indirilir.** İndirilemezse "Şekil yüklenemedi"; kart çökmez. `storagePath` çözülemezse "kullanılamıyor".
- İndirme **kimlikli**: `Authorization: Firebase <ID token>` ve `X-Firebase-AppCheck: <token>` (`RemoteFileLoader` ile aynı yol).
  **`token=` parametresi kullanılmaz** (tüm indirme token'ları iptal edildi). Kimlik bilgisi yalnız uygulamanın kendi bucket'ına gönderilir.
- Dosya: JPEG, en uzun kenar ≲ 1200 px; sunucu `Cache-Control: private, max-age=31536000` koyar. Disk önbelleği anahtarı **yol**dur.
  Çözme ekran boyutuna göre küçültülür (en çok 1600 px).

### 8.3 Yapay zekâ notu (K10)

Açılan her cevabın altında, ikincil renkte, küçük yazıyla:

> Bu çözüm yapay zekâ ile hazırlanmış ve incelenmiştir.

"Hata bildir" düğmesi **yoktur** (K10). Arayüz ve mağaza metinlerinde "hatasız", "yakalanamaz" gibi mutlak ifadeler kullanılmaz.

### 8.4 Soru ekranı metinleri (iOS birebir)

"Cevabı Göster", "Cevabı Gizle", "Cevap", "Çözüm", "Doğru seçenek: X", "Şekil yüklenemedi", "Şekil var.", "Tekrar dene",
"Bu sınavda soru yok".

### 8.5 Ekran koruması

- Soru ekranı penceresine `FLAG_SECURE` (sayfa görüntüleyicilerle aynı politika). Soru metni, şık, şekil, cevap ve çözüm korunan kabın içindedir.
- Koruma **en iyi çaba**dır; "ekran görüntüsü alınamaz" gibi mutlak iddia kullanılmaz. Kalkan kurulamazsa içerik gösterilir (fail-open) ve olay günlüğe yazılır.
- İçerik bitmap'e düzleştirilip koruma dışında gösterilmez; paylaş, kopyala, sürükle, yazdır eklenmez.
- Cihazda doğrulanmadan "korunuyor" denmez; her büyük Android sürümünde deneme yapılır.

### 8.6 Analytics

- `exam_open` (`kind: structured`, `question_count`): ilk başarılı yüklemeden sonra bir kez (en az 1 soru varsa).
- `answer_revealed` (`question_order`): cevap ilk kez açılınca soru başına bir kez.
- Pratik olayları §7. Ad ve parametreler iOS ile aynı olmalıdır; böylece raporlar birleşir.

### 8.7 Son görüntülenenler ve bildirim

Son görüntülenenlere kayıt ilk başarılı yüklemeden sonra bir kez yapılır. Bildirimden açılan sınav kilit kuralından geçer; açıldıktan sonra
yapılandırılmışsa soru ekranına yönlendirilir.

---

## 9. iOS test ↔ Android test eşlemesi

**Kapsam:** iOS `ViFiTests` (Swift Testing; `@Test("…")` başlıkları) ile Android `app/src/test` (JUnit4 + backtick adları).
Sayılar `@Test` işaretlerine göredir; parametrik tablo vakaları (ör. ExamTitleParser 36/32/20) ayrı sayılmaz.
"Tamam" = iOS vakalarının tamamı bir Android testine karşılık gelir (SCT-01 ile). "Kısmi" = bazı vakalar henüz yok; eksik vaka
hattı yazılırken eklenir. "Yok" = Android'de henüz test yok.

### 9.1 Saf yardımcılar, parser'lar, modeller

| iOS dosyası (suite) | iOS | Android sınıfı | Android | Durum |
|---|:-:|---|:-:|---|
| `StructuredExamParserTests.swift` (ExamQuestionParser) | 10 | `core/data/firebase/ExamQuestionParserTest.kt` (6) + `core/data/conversion/ExamConversionParserTest.kt` (6, iş belgesi vakaları dahil) | 12 | Tamam (soru + iş belgesi) |
| `PracticeParserTests.swift` | 14 | `core/data/practice/PracticeParserTest.kt` | 15 | Tamam (ek: token yazdırılmaz vakası) |
| `ExamTitleParserTests.swift` | 7 | `core/util/ExamTitleParserTest.kt` | 6 | Tamam; "Add Exam listesi görünümü" vakası `SMP-01` (Hat E) UI testinde |
| `QuestionLabelTests.swift` | 3 | `core/util/QuestionLabelTest.kt` | 3 | Tamam |
| `MathTextTokenizerTests.swift` | 11 | `core/util/MathTextTokenizerTest.kt` | 11 | Tamam |
| `LatexCompatibilityTests.swift` | 4 | `core/util/LatexCompatibilityTest.kt` | 4 | Tamam |
| `CatalogNameTests.swift` | 2 | `core/util/CatalogNameTest.kt` | 1 | Kısmi: "son sınav soru ekranında açılmış mı" (recents) vakası Hat A (`SQS-06`) |
| `SlugTests.swift` | 3 | `core/util/SlugParityTest.kt` | 3 | Tamam (açık: NFD girdi, §11) |
| `ExamRoutingTests.swift` | 8 | `core/util/ExamRoutingTest.kt` | 5 | Kısmi: yönlendirici yığını, dosya görünümü VM'i ziyaret kaydı (2 vaka) Hat A (`SQS-01`) |
| `SubjectMappingTests.swift` (SubjectMappingViewModel) | 16 | `core/data/conversion/SubjectMappingTest.kt` | 7 | Kısmi: 7 saf vaka tamam; 9 ViewModel vakası (yükleme, arama, değiştirme, yeniden adlandırma, birleştirme, hata) Hat D (`ADX-06`) |
| `ExamConversionCallsTests.swift` | 16 | `core/data/conversion/ExamConversionCallsTest.kt` | 7 | Kısmi: 7 çağrı vakası tamam; 9 vaka (taslak geçerliliği, boş metin, şık seçimi, yarım şık, yeni sınav/çoğaltma planı, mevcut ebeveyn artırımı, kopya planı) Hat D (`ADM-01`, `ADX-02`) |
| `ArchivePublishPlanTests.swift` | 6 | `core/data/submission/ArchivePublishPlanTest.kt` (4.1 öncesi, 8 vaka) | 8 | Eşdeğer vakalar mevcut; 4.2 iki aşamalı `publishPlan` şekli Hat D (`ADM-01`) |
| `ExamSubmissionTests.swift` | 13 | `core/model/ExamSubmissionTest.kt` (4.1 öncesi) | — | Gönderim doğrulaması 4.1'den; 4.2'de değişmez (regresyon) |
| `SubmitterProfileTests.swift` | 6 | `core/data/moderation/SubmitterInfoParserTest.kt` (2) | 2 | Kısmi: önbellek ve okuma davranışı (4 vaka) Hat D (`ADM-03`) |
| `FormulaRenderingTests.swift` (SwiftMath) | 7 | — | 0 | Yok: AndroidMath çizimi Hat B (`SMT-02`, `SMT-03`) |
| `MathLineBreakerTests.swift` | 6 | — | 0 | Yok: Hat B (`SMT-03`); `MathLineBreaker.kt` SCT-01'de yoktur |
| `ExamRouting` / yer değiştirme (ViewModel) | — | `core/util/ExamRoutingTest.kt` | — | Hat A (`SQS-01`) |
| Android'e özgü | — | `core/data/repository/fake/StructuredSampleDataTest.kt` | 5 | Mock örnek sınav ve pratik Fake'i (iOS karşılığı yok) |

### 9.2 Hâlâ yazılacak (ViewModel, Compose, UI)

| iOS dosyası | Yazılacak Android testi | Hat |
|---|---|---|
| `ViFiUITests/SubjectMappingUITests.swift` | `androidTest` ders eşleme ekranı | `ADX-06` / `SIT-03` |
| `LessonPickerViewModelTests.swift`, `AddExamViewModelTests.swift` | Sınav Ekle listesi (22 ad, `SMP-01`) | Hat E (`SMP-01`) |
| `ProfileFormViewModelTests.swift` | Değişiklik yok (4.1'de tamam) | — |
| Soru ekranı (iOS: `StructuredExamView`, `QuestionContentView`) | `StructuredExamViewModelTest`, `QuestionCardTest` (Compose: cevap aç/kapa, not, boş/hata, şekil hatası) | `SQS-02`…`SQS-05` (Hat A), `SIT-03` |
| Pratik (iOS: `Features/Practice/*`) | `PracticeSessionViewModelTest` (sıralı "Soru N", `exclude`, boş dönüş, paywall) | `PRC-02`…`PRC-04` (Hat C) |
| Formül (`FormulaText`, `QuestionContentView`) | `QuestionRichTextTest`, `FormulaRenderingTest` (örnek formüllerin çizimi, bozuk LaTeX'te çökme yok) | `SMT-02`…`SMT-04` (Hat B) |
| Moderasyon (iOS: `ConversionReviewViewModel`, `ConversionTitleSection`) | `ConversionReviewViewModelTest`, başlık doğrulama | `ADX-01`…`ADX-05` (Hat D) |

Not: her ViewModel testi `runTest` + Turbine ve `FakeXxxService` ile yazılır (`testing.md`). Compose testleri `testTag` ile
(`questions.*`, `practice.*`, `subjectMapping.*`) bulunur.

---

## 10. Canlı ortam ayrışmaları (SOPS-01, 21 Kasım 2026, yazım yok)

Canlı salt okuma sonucu (özet; anahtar ve kişisel veri yok):
- Canlıda 2 yapılandırılmış sınav var (5 ve 3 soru). `questions (examId, order)` indeksi READY.
- Şekil yolu `questionFigures/<jobId>/<nnn>-<k>.jpg` biçiminde (§3).
- 2 havuz özeti (`types.<tür>.{tr, u-…, c-…}`); ders düğümünde `subjectId` var.
- 14 fonksiyonun tamamı europe-west1'de.
- `recaptchaConfig` ANDROID uç noktası ve SMS Defense durumu: **AUDIT**; toll fraud koruması açık (ürün sahibi ve `RCD-*` hattında izlenir).

**Canlı veri ile belgenin ayrıştığı noktalar** (Android parser'ları bunları tolere eder):

| Konu | Belge | Canlı gözlem | Android'de |
|---|---|---|---|
| `files[].order` | 1 tabanlı | 0 ya da 1 tabanlı | Tam sayı olarak okunur (`ExamConversionParser`); sıralama göreli yapılır |
| `label` | "1" | "1" ya da "Soru 1)" | `QuestionLabel.normalize` ile çıplak numaraya indirilir (`ExamQuestionParser`) |
| `examConversions.error` | (tip belirtilmemiş) | düz metin | String olarak okunur; boşsa `null` (`ExamConversionParser`) |
| `usage.costUsd` | double | int ya da double | Her iki tür de sayı olarak okunur (`RawValues.double`) |
| Gönderimde sağlayıcı alanı | (`provider`) | **`conversionProvider`** | Canlı alan adı; Android'de okuma `ADM-01` hattında doğrulanacak |

---

## 11. Açık noktalar

- **Slug (SCT-01):** NFD (ayrıştırılmış Unicode) girdi iOS'tan ayrışabilir; NFC aynı sonucu verir. Sunucu NFC'ye çevirir; Android
  `Slug` de girdiyi NFC'ye çevirmelidir. `SlugParityTest` NFD vakasını kapsamaz.
- **Eksik ek:** belge soru şeklini "Ek A" olarak anar; bu belgede Ek A yoktur. Şekil §2.2'deki tabloyla tanımlanmıştır.
- **`publishPlan` alan şeması** bu belgede yoktur; `ADM-01` öncesi iOS `ArchivePublishPlan.publishPlanDocument` okunacaktır.
- **`questionPools` ve `questionPoolIndex`** ayrı koleksiyonlardır (§2.3); karıştırılmamalıdır.
- **Add Exam listesi:** `ExamNameCatalog` 22 adla SCT-01'de hazırdır; ekrandaki sıralama ve ekleme davranışı `SMP-01`'de doğrulanır.
- **Yayın sırası (K16):** iOS 4.2.0 ile Android 4.2.0 aynı anda yayına alınır; `minimumVersion` yükseltmesi yalnız ikisi de mağazada olunca.
- **Canlı açık riskler:** SMS Defense AUDIT ve toll fraud (SOPS-01); Play abonelik doğrulaması deploy edilene kadar Android Premium sınırlıdır (`SRV-02`, `DDEC-03`).
- **Claude Opus Vertex kotası** henüz yok (roadmap 5. bölüm); `SQA-03` Gemini ile yapılabilir.
