# ViFi Android — 4.2.0 Versiyonunda Yapılacak İşler

> **Amaç:** `ViFi-iOS` **`v-4.2.0`** dalındaki geliştirmeyi Android'de karşılamak. Dalda `v-4.1.0`'ın üstünde 58 commit
> var (`0a6d691`…`5e39574`, 13–21 Kasım 2026). Üç büyük parça ve birkaç küçük düzeltme içeriyor:
> 1. **Soru soru sınavlar:** Yapay zekâ ile dönüştürülmüş sınav, sayfa görseli yerine soru kartlarıyla açılır. Kartta
>    Markdown + LaTeX formül, şıklar, kırpılmış şekil ve "Cevabı Göster" (cevap, adım adım çözüm, yapay zekâ notu) var.
> 2. **"Sınava gir" pratik modu:** Ders → sınav türü → kapsam (bu ders / üniversitem / Türkiye geneli) seçilir, karışık
>    sorular tek tek gelir. Ücretsiz kullanıcıya günde 5 soru, Premium sınırsız.
> 3. **Admin dönüştürme akışı:** İki aşamalı onay (1. onay coin verir ve seçilen modelle dönüştürmeyi başlatır, 2. onay
>    yayınlar), "Hazır sınavlar" incelemesi, "Yeni yapıya geçir", toplu dönüştürme, başarısız işler, ders eşleme,
>    yayındaki soruyu düzenleme.
> 4. **Küçük pariteler:** Sınav adı listesi 22 ad (K27), moderasyonda gönderen bilgisi, mükerrer gönderim uyarısı (K22),
>    gönderimden açılan katalog düğümlerinin Türkçe büyük harfle adlandırılması, arşiv satırında "N soru", son
>    görüntülenenlerde "Sorular" rozeti.
>
> Ayrıca 31 Ekim'de açılan **reCAPTCHA SMS Defense** taslağının (eski 4.2.0 listesi) kalan işleri bu listeye Hat G olarak
> taşındı. AUDIT 18 Kasım'da açıldı, Play veri güvenliği aynı gün girildi.
>
> **Backend canlıda (20 Kasım 2026, iOS tarafı, onaylı).** Kurallar, indeksler, 13 callable, `processExamConversion`
> kuyruğu ve Vertex AI yayında. **Tek istisna** Play abonelik doğrulaması: sunucu henüz Android Premium kanıtını
> doğrulamıyor (`index.js` TODO, `googlePlay.js`'te abonelik sorgusu yok). Bu yapılmadan Android Premium kullanıcısı
> pratik modunda ücretsiz sayılır, yani bu bir **yayın engelidir** (`DDEC-03`, Hat F).
>
> **Sözleşme kaynağı:** iOS deposundaki yerel `STRUCTURED-EXAMS.md` (git dışı, 472 satır; Android için yazıldı) ve
> yetkili kod `firebase/functions/conversions/schema.js`. Belge ile kod ayrışırsa **kod** doğrudur. Şüphede iOS kodu
> (`v-4.2.0`) esastır; iOS test adları Android'in kabul testleridir.
>
> **Yayın sırası (K16):** iOS 4.2.0 (13) 21 Kasım'da App Review'a **elle yayın** seçeneğiyle gönderildi. İki platform
> Android hazır olunca, ürün sahibinin kararıyla **aynı anda** yayına alınır. Eski yapının kaldırılması (iOS K16 "Faz 4":
> `minimumVersion` yükseltme, eski görüntüleyici, `imagess/`/`pdfs/` okumasının daraltılması) bu listenin kapsamı dışında.
> Ancak iki platform da mağazadayken ve ayrı onayla yapılır.
>
> **Dal:** `v-4.2.0` (bugün 4.1.0 (21) ile aynı kod). Bu dosya 4.2.0'ın **tek** iş listesidir. ID'ler önceki
> listelerle karışmasın diye `S…` / `P…` / `A…` / `R…` öneklidir. Canlı Firebase / Console / Play adımları **yalnız açık
> onayla**. Gerçek telefon adımı yoktur; doğrulama emülatör + canlı salt okumayla yapılır.

---

## 0. Durum Özeti

> **İlerleme (21 Kasım 2026):** Toplam 64 kalemin **50'si tamam** (`RSD-02`, `RLG-01` eski taslaktan;
> `DDEC-01`…`05` 21 Kasım'da ürün sahibince verildi) (`RSD-02` AUDIT açıldı, `RLG-01` veri güvenliği
> girildi; ikisi de 18 Kasım'da). Kararlar: admin **tam parite**, LaTeX **yerel AndroidMath** (`SPK-01` şartıyla),
> Play abonelik doğrulaması **ortak depoya istisna**, sürüm **4.2.0 (22)**, reCAPTCHA **açık bağımlılık**. Açık karar:
> `DDEC-06` (iOS'ta da son görüntülenen kartı kilit kontrolünden geçmiyor; iki platform eşit). Faz 0 ve Hat A, B, C, E, F
> (kod), G, H kod/belge kalemleri tamam ve `v-4.2.0`'da (F ortak depoda ayrı dalda). Hat D tamamı ve `SIT-01` (pratik oturum ekranı, Browse'da
> Sınava gir, admin bağlantıları) birleşti. `SIT-02`/`03` tamam, `SIT-04` kısmen (API 24/34 yok). `SRV-01` ortak depoda `v-4.2.0`'a
> birleşti (push/deploy yok). 21 Kasım kararları: `DDEC-06` iOS gibi; gönderen kartı profil yoksa iOS gibi not gösterir;
> `Slug` girdisi NFC'ye normalleşir; paywall'a "Sınava gir'de sınırsız soru" (Android); pratikte bildirme yolu yok
> (iOS gibi); kullanım koşulları metni ortak depoda ayrı dalda hazırlanır, yayın ayrı onay; veri güvenliği S1 evet,
> S3 "Diğer kullanıcı içeriği", S4 teyitli; mağaza: iOS sürüm notu + yeni açıklama. Yeniden üretilen yayındaki soru
> ("incelenmiştir" notu, P5): iOS ile birebir aynı kalır. Sıradaki: `SRV-02` deploy onayı
> (önce Play API izni kontrolü), Faz 3 canlı doğrulama (onaylı).

---

## 1. Analiz

### 1.1 iOS'ta ne yapıldı (`ViFi-iOS/v-4.1.0..v-4.2.0`)

| Konu | iOS (dosya) | Android'e etkisi |
|---|---|---|
| Yönlendirme | `ExamRouting` (saf): `format == "structured"` **ve** `questionCount > 0` → soru ekranı, yoksa sayfa görünümü. Browse, son görüntülenenler ve bildirim aynı kurala uyar. Özetsiz açılışta belge structured çıkarsa ekran **yer değiştirir** | Zorunlu |
| Soru okuma | `questions where examId == X order by order` (indeks `examId ASC, order ASC`). `ExamQuestionParser` bozuk dokümanı atlar (`schemaVersion > 1`, `order < 1`, boş `text` / `answer.text`). `QuestionLabel` "Soru 1)" → "1" | Zorunlu |
| Soru kartı | `QuestionCardView` + `QuestionContentView`: başlık "`<label>. Soru`", metin, şekiller, şıklar, "Cevabı Göster / Gizle", "Cevap", "Doğru seçenek: X", "Çözüm" + numaralı adımlar, not "Bu çözüm yapay zekâ ile hazırlanmış ve incelenmiştir." | Zorunlu |
| Formül | SwiftMath 1.7.3, `MathTextTokenizer` (Pandoc `$` kuralı), `LatexCompatibility` (komut eşleme tablosu), `MathLineBreaker` (sığmayan formül alt satıra geçer; 20 Kasım kararı), Markdown satır içi alt kümesi (`**`, `*`, `` ` ``, `~~`) | Zorunlu; Android'de LaTeX motoru yok (`DDEC-02`) |
| Şekil | `figures[].storagePath` ekrana gelince kimlikli indirilir (ID token + App Check). Önbellek anahtarı **yol**. Yer tutucu oranı [0,1…2]. Hata "Şekil yüklenemedi" | Zorunlu; mevcut `ExamFilesModule` ImageLoader yeterli |
| Ekran koruması | Soru içeriği kalkan içinde | Mevcut `ScreenCaptureProtection` (koşulsuz `FLAG_SECURE`) yeniden kullanılır |
| Uzaktan anahtar | **Yok** (K25). Android de uzak bayrak okumaz | Zorunlu (okuma eklenmez) |
| Ön yükleme | Sorular, havuzlar ve şekiller önceden çekilmez | `ArchivePrefetcher`'a dokunulmaz |
| Analytics | `exam_open` (`kind: structured`, `question_count`), `answer_revealed` (`question_order`), `practice_started` (`exam_type`, `scope`), `practice_answer_revealed`, `practice_limit_reached`, `practice_paywall_opened` | Aynı ad + parametre |
| Liste/son görüntülenen | Arşiv satırı "N soru" + "Sorular" rozeti (750f64a). Son görüntülenen kaydı structured bilgisini saklar, eski kayıt dosya sınavı sayılır (67889e0) | Zorunlu |
| Pratik modu | `questionPoolIndex/<subjectId>` tek okuma → düğme görünürlüğü + tür/kapsam sayıları. Sorular **yalnız** `nextPracticeQuestions` callable'ından gelir. `exclude` ≤ 300, `count` 1…10, `subscriptionProof` her çağrıda. Başlık "Soru 1, 2…", kaynak satırı "BOZOK ÜNİVERSİTESİ · 2019 FİNAL". Ön-çekme yalnız Premium. `limitReached` → paywall. Havuz bitince "Baştan başla" | Zorunlu; Android Premium kanıtı sunucuda doğrulanmıyor (`DDEC-03`) |
| Sınav adı listesi | `ExamNameCatalog` 22 ad (VİZE … ÇÖZÜMLÜ SORULAR), `ExamType` 17 tür, `isPracticeEligible`. `ExamTitleParser`: 36 tür/yıl vakası + `canonicalTitle` 32 dönüşüm + 20 doğrulama vakası (sunucuyla aynı) | Liste zorunlu (Android'de bugün 7 ad). Ayrıştırıcı pratik/admin için gerekli |
| Moderasyon | 1. onay `approve(record, provider)`: coin + dosya kopyası + `approved` + `publishPlan` + `provider`, **`exams`'e yazmaz**. Gönderen kartı (ad soyad, üniversite, fakülte, bölüm; telefon yok). Mükerrer uyarısı "Bu sınav arşivde zaten yüklü" (K22: 2. onayda mevcut sınav değiştirilir). Katalog adı Türkçe büyük harf (`CatalogName`) | Android admin bugün **doğrudan yayınlıyor** (4.1.0 yolu, `FirebaseModerationBackend.commitApproval`). Sunucu bunu tanır ama yeni istemci kullanmamalı (`DDEC-01`) |
| Admin dönüştürme | Hazır sınavlar (taslak + orijinal yan yana, düzenle, tek soruyu yeniden üret, başlık listeden, Yayınla / Tümünü yeniden üret / Sınavı iptal et), Başarısız işler, Toplu dönüştür (maliyet + 50 $ göstergesi), Ders eşleme, "Yeni yapıya geçir", yayındaki soruda "Yönetici" menüsü. Her AI işleminde model seçilir (`provider: claude | gemini`) | İsteğe bağlı (`DDEC-01`) |
| Güncelleme uyarısı | dca5c3c: SwiftUI'ye özgü "uyarı geri geliyor" hatası | Android'de yok (`AppUpdateViewModel` sürümü bellekte tutuyor). Yalnız regresyon testi eklenir |
| Hosting/gizlilik | Gizlilik, koşullar ve hesap silme sayfaları 21 Kasım'da yayında: yapay zekâ işleyicisi, pratik sayacı, abonelik bağı | Android için metin işi yok; Play beyanı gözden geçirilir |
| RTDB `Universitiess` | Kural bloğu ve okuyucu silindi (ce52823) | Android'de yalnız yorum kalmış, temizlenir |

### 1.2 Android'de bugün olan (21 Kasım 2026, kod taraması)

- **Yönlendirme:** `ViFiRoute.Exam(components, displayNames, kind)` → `ExamViewerScreen` `kind`'a göre görsel pager ya
  da `PdfRenderer` seçer. Girişler: Browse (`router.open`), son görüntülenenler (`router.openExam`, **kilit kontrolü
  yok**; iOS ile aynı mı `SQA-01`'de bakılır), bildirim sayfası "Sınava Git" (`InboxExamGate` → `ExamAccessResolver`).
  Arama ayrı ekran değil, Browse içi süzme.
- **Veri:** `FirestoreArchiveParser` `format`, `questionCount`, `subjectId` okumuyor. `ExamSummary(kind, fileCount)`,
  `ExamDocument(path, kind, fileUrls)`.
- **Görüntüleyici:** `ScreenCaptureProtection` koşulsuz `FLAG_SECURE` + sunum ekranı örtüsü + Android 14+ ekran
  görüntüsü bildirimi; `AppSwitcherCover`. Coil 2.7.0 ortak `ImageLoader` (`cacheDir/ExamFiles/Images`, kimlikli
  OkHttp, 401/403'te tek yenileme). Hesap silmede `purge()`.
- **Formül/Markdown/WebView:** Hiçbir bağımlılık yok.
- **Add Exam:** `ExamNameCatalog` 7 ad. `exam` metni `"<yıl> <AD>"`.
- **Moderasyon:** `AdminModerationService.approve` → Storage kopyası → tek `batch`'te `catalogNodes` + `exams` +
  cüzdan + defter + gönderim. Mükerrerde (`placement.examId != null`) yayın yok, düşük ödül. Gönderen gösterilmiyor.
  Katalog adı büyük harfe çevrilmiyor.
- **Admin kapısı:** `isCurrentUserAdmin()` (ID token `admin` claim). Girişler: Hesap › Moderasyon, Hesap › Bildirim
  Oluştur, Browse'da sınav silme.
- **Premium:** `PlayBillingSubscriptionService` `PlayPurchase(purchaseToken, productIds)` biliyor, abonelikte sunucu
  çağrısı yok. Ürün `vifi_premium` (`monthly`/`yearly`). Paywall `ViFiRoute.Paywall`.
- **Callable deseni:** `FirebaseNotificationAdminService.call(name, data, timeout)`; bölge `europe-west1`.
- **Analytics:** `exam_open` (`kind`) var; `answer_revealed` ve `practice_*` yok.
- **Auth:** reCAPTCHA kodları `AppVerificationFailed`'e eşli ama `FirebaseAuthErrorsTest`'te test yok. reCAPTCHA SDK
  BoM 33.5.1 ile dolaylı 18.5.1.

### 1.3 Sonuç

Android 4.2.0, 4.1.0'dan **büyük** bir istemci sürümü: yeni bir çizim katmanı (LaTeX), iki yeni kullanıcı ekranı (soru
ekranı, pratik modu), moderasyonun iki aşamaya geçmesi ve kapsamı karara bağlı bir admin paketi var. Sunucu tarafında tek
iş Play abonelik doğrulaması.

---

## 2. Paralel Çalışma Planı

```
Faz 0  DDEC-01..06 (ürün sahibi) · SOPS-01 (canlı salt okuma) · SPK-01 (LaTeX motoru denemesi)      ── paralel
       SCT-01 (sözleşme: modeller, parser'lar, arayüzler, Fake'ler, örnek veri, sürüm)             ── tek oturum, sıralı
                     │ senkron noktası 1
Faz 1  Hat A  Soru ekranı + yönlendirme        SQS-01..06
       Hat B  Formül ve zengin metin           SMT-01..04   ◄── SPK-01, DDEC-02
       Hat C  Sınava gir (veri + seçim ekranı) PRC-01..04
       Hat D  Moderasyon ve admin              ADM-01..04 → ADX-01..06 (tam parite)
       Hat E  Küçük pariteler                  SMP-01..04
       Hat F  Sunucu: Play abonelik doğrulaması SRV-01..02 (ortak depo)   ◄── DDEC-03
       Hat G  Giriş güvenliği (reCAPTCHA)      RCD-01..02
       Hat H  Belge ve beyan                   SLG-01..05
                     │ senkron noktası 2 (hatlar birleşir)
Faz 2  SIT-01 (birleştirme + pratik oturum ekranı) → SIT-02 (birim) ‖ SIT-03 (UI) → SIT-04 (tam paket)
Faz 3  SQA-01 (canlı soru ekranı) ‖ SQA-02 (canlı pratik) ‖ SQA-03 (canlı iki aşamalı onay) → SQA-04 (iOS ↔ Android)
       RSD-03 (ENFORCE sonrası kontrol, ürün sahibinin takvimine bağlı, yayından bağımsız)
Faz 4  SRL-01..07  ◄── K16: iOS ile aynı anda, her dış adım ayrı onay
```

**Hat sahipliği (iki hat aynı dosyaya dokunmaz):**

| Hat | Dokunduğu yerler | Dokunmaz |
|---|---|---|
| **A — Soru ekranı** | `feature/structuredexam/**` (yeni), `feature/examviewer/ExamViewerScreen.kt` + VM (yer değiştirme), `core/navigation/**` (yeni route), `feature/browse/BrowseScreen.kt` (`ArchiveRow`), `feature/home/HomeScreen.kt` (son görüntülenen kartı), `core/data/preferences/*RecentExams*`, `strings_structured_exam.xml` | `core/designsystem/math/**`, pratik, moderasyon |
| **B — Formül** | `core/designsystem/math/**` (yeni: `QuestionRichText`, `FormulaText`, Markdown alt kümesi, satır bölücü), `gradle/libs.versions.toml` + `app/build.gradle` (yalnız LaTeX bağımlılığı) | Ekranlar |
| **C — Sınava gir** | `feature/practice/**` (yeni), `core/data/practice/**` (yeni), `core/data/billing/PlayBillingSubscriptionService.kt` (kanıt okuma), Browse'daki ders düğmesi için yalnız `PracticeEntry` composable'ı (Browse'a bağlama `SIT-01`'de), `strings_practice.xml` | Soru kartı içi |
| **D — Moderasyon/admin** | `feature/moderation/**`, `core/data/moderation/**`, `core/data/submission/**`, `core/data/firebase/FirebaseModerationBackend.kt`, (tam kapsamda) `feature/conversionadmin/**` + `core/data/conversion/**` (yeni), `strings_moderation*.xml` | Kullanıcı ekranları |
| **E — Küçük pariteler** | `core/model/ExamNameCatalog.kt`, `feature/addexam/**` (yalnız liste kullanımı), `feature/update/**` testleri, RTDB yorum temizliği | — |
| **F — Sunucu** | **Ortak depo** `ViFi-iOS/firebase/functions/googlePlay.js`, `index.js` (pratik kanıt dalı), `firebase/tests/**` | Android deposu |
| **G — Giriş güvenliği** | `libs.versions.toml` / `app/build.gradle` (yalnız reCAPTCHA satırı; B ile aynı dosya → `SIT-01`'de birleştirme sırası: önce G, sonra B), `FirebaseAuthErrorsTest.kt` | — |
| **H — Belge** | `PLAY-DATA-SAFETY.md`, `PLAY-STORE-LISTING.md`, `PLAY-POLICY-4.2.0.md` (yeni), `CLAUDE.md`, `README.md` | Kod |

- **Senkron noktası 1:** `SCT-01` birleşmeden Faz 1 başlamaz. Ortak tipler (modeller, arayüzler, Fake'ler,
  `QuestionRichText` imzası, route) yalnız orada yazılır.
- **Hat A ↔ B:** A, kartta `QuestionRichText(text, …)` imzasını kullanır. `SCT-01` bunu düz metin gösteren geçici
  gövdeyle yazar, B gerçek gövdeyi doldurur. A, B'yi beklemez.
- **Hat C ↔ A:** Pratik **oturum** ekranı A'nın `QuestionCard`'ını kullanır. C veri katmanını, seçim ekranını ve oturum
  ViewModel'ini yazar; oturum ekranının kart bağlantısı `SIT-01`'dedir.
- **Hat F** ayrı depoda çalışır. Deploy ayrı onaydır (`SRV-02`). Android tarafı (`PRC-03`) kanıtı her durumda gönderir;
  sunucu doğrulamazsa yalnız ücretsiz kurallar uygulanır, çökme olmaz.
- Her hat ayrı worktree'de kendi dalına commit atar; `v-4.2.0`'a birleştirme `SIT-01`'de.

### 2.1 Model planı (işe göre model; kritikte Opus, en kritikte Fable ile ikinci göz)

| Model | Kalemler | Neden |
|---|---|---|
| **Opus** | `SOPS-01`, `SPK-01`, `SCT-01`, `ADM-01`…`02`, `SRV-01`, `SIT-01`, `SQA-*`, `SRL-*`, Hat A'nın yönlendirme kısmı (`SQS-01`) | Sözleşme, eşzamanlılık, para/coin, canlı ortam, yayın |
| **Fable (ikinci göz)** | `SRV-01` (abonelik doğrulama + tek hesap bağı güvenliği) ve `ADM-01` (iki aşamalı onay atomikliği) birleşmeden önce bağımsız inceleme | Güvenlik ve para akışı |
| **Sonnet** | Hat A'nın kalanı, Hat B (`SPK-01` sonrası), Hat C, `ADM-03`…`04`, `ADX-*`, Hat E, Hat G, `SIT-02`…`04` | Şablonlu Compose/test işi |
| **Haiku** | `SLG-04`, `SLG-05` | Mekanik belge güncellemesi |

---

## 3. İş Listesi

> Biçim: `- [ ] `**`ID`**` başlık _(öncelik·efor·model)_ — açıklama ⟶ bağımlılık`.

### Faz 0 — Kararlar ve Hazırlık  ⚠️ (ürün sahibi · ONAY gerektirir)

- [x] **`DDEC-01`** Android admin kapsamı _(P0·S·ürün sahibi)_ — **Karar (21 Kasım 2026): (b) tam parite.** `ADM-01`…`04` + `ADX-01`…`06` yapılır. Android admin bugün gönderimi tek adımda yayınlıyor;
  sunucunun iki aşamalı akışıyla çelişiyor (mükerrer K22, `publishPlan`, model seçimi). Seçenekler:
  (a) **Asgari** — iki aşamalı 1. onay (iki düğme "Onayla · Claude ile ilerle" / "Onayla · Gemini ile ilerle"),
  gönderen kartı, mükerrer uyarısı, büyük harf ad (`ADM-01`…`04`); taslak inceleme ve diğer dönüştürme ekranları
  yalnız iOS'ta. (b) **Tam parite** — (a) + `ADX-01`…`06`. (c) Android'den onayı kaldır, admin yalnız iOS'tan onaylar.
  **Öneri: (a).** Admin işi az kişide ve iOS'ta hazır; (b) listeyi kabaca iki katına çıkarır.
- [x] **`DDEC-02`** LaTeX motoru _(P0·S·Opus araştırır, ürün sahibi karar verir)_ — **Karar (21 Kasım 2026): (a) yerel AndroidMath**, `SPK-01` lisans/bakım/performans denemesini geçmesi şartıyla; geçmezse ürün sahibine yeniden sorulur. Seçenekler: (a) **yerel çizim**,
  iosMath'in Android portu (`AndroidMath`). SwiftMath de iosMath türevi olduğundan desteklenen komut kümesi ve görünüm
  iOS'a en yakın olandır; WebView yok, `FLAG_SECURE` doğal kapsar. Riski: bakım durumu ve lisans, `SPK-01`'de
  doğrulanır. (b) **KaTeX + WebView** — geniş komut desteği, ama `LazyColumn`'da kart başına WebView ağır, ilk çizim
  gecikmeli, yazı ölçeği ve satır içi akış zor. **Öneri: (a), `SPK-01` geçerse.** Geçmezse (b) ile yeniden karar.
- [x] **`DDEC-03`** Play abonelik doğrulaması _(P0·S·ürün sahibi)_ — **Karar (21 Kasım 2026): istisna onaylandı;** kod Android oturumunda ortak depoya yazılır, iOS yolu değişmez, deploy (`SRV-02`) ayrı onay. Sunucu Android Premium kanıtını doğrulamıyor; yapılmazsa
  Android Premium kullanıcısı pratikte günde 5 soruyla sınırlanır (yayın engeli). İş ortak depoda (`googlePlay.js`:
  `purchases.subscriptionsv2.get` + `subscriptionOwners` bağı Play sipariş kimliğiyle + 24 saat hatırlama). Android
  normalde backend yazmaz; 4.0.0'daki `DEC-04` gibi bir **istisna** gerekir. **Öneri:** istisna onaylansın, kod Android
  oturumunda ortak depoya yazılsın (iOS yolu değişmez), deploy (`SRV-02`) ayrıca onaylansın.
- [x] **`DDEC-04`** Sürüm ve yayın _(P0·S·ürün sahibi)_ — **Karar (21 Kasım 2026): öneri yönünde.** **Öneri:** 4.2.0 (22); K16 gereği iOS 4.2.0 (13) ile aynı gün
  yayın; `config/appUpdate/android` `minimumVersion` bu sürümde değişmez (eski yapının kaldırılması ayrı iş).
- [x] **`DDEC-05`** reCAPTCHA bağımlılığı _(P1·S·ürün sahibi)_ — **Karar (21 Kasım 2026): açık yazılır (18.5.1).** Eski `RDEC-02`. **Öneri:**
  `com.google.android.recaptcha:recaptcha:18.5.1` `libs.versions.toml`'a açık yazılsın (BoM değişirse alt sınır korunur);
  BoM yükseltmesi kapsam dışı.
- [x] **`DDEC-06`** Son görüntülenen kilit kontrolü _(P2·S·ürün sahibi)_ — **Karar (21 Kasım 2026): iOS gibi kalır.** iOS'ta da son görüntülenen kartı kilit kontrolünden geçmiyor (`HomeView.openRecentExam`); kod değişmez. Asıl metin: Android'de son görüntülenen kartı kilit
  denetimi yapmadan açıyor. iOS'ta aynı yol `ExamAccessResolver`'dan geçiyorsa parite için eklensin mi? `SQS-01`
  sırasında iOS davranışı kesinleştirilip ürün sahibine sunulur. **Öneri:** iOS geçiriyorsa evet.
- [x] **`SOPS-01`** Canlı salt okuma _(P0·S·Opus)_ — **Sonuç (21 Kasım 2026, yazım yok):** canlıda 2 yapılandırılmış sınav (5 + 3 soru); `questions (examId, order)` indeksi READY; şekil yolu `questionFigures/<jobId>/<nnn>-<k>.jpg`; 2 havuz özeti (`types.<tür>.{tr, u-…, c-…}`), ders düğümünde `subjectId` var; 14 fonksiyonun hepsi europe-west1'de; SMS Defense **AUDIT**, toll fraud açık. Ayrışmalar: `files[].order` 0 ya da 1 tabanlı, `label` "1" ya da "Soru 1)", `examConversions.error` düz metin, `usage.costUsd` int/double, gönderimde sağlayıcı alanı **`conversionProvider`**. Asıl metin: **Yazım yok.** Test hesabıyla emülatörden ya da gcloud ile:
  (1) yayınlanmış bir yapılandırılmış sınavın `exams` dokümanında `format`/`questionCount` (ör. "Topoloji 2 › 2017 ARA
  SINAV", "Sakarya … 2017 FİNAL"); (2) `questions` sorgusu App Check ile 200, indeks mevcut; (3) `questionFigures/`
  tek dosya okuma; (4) `questionPoolIndex` örnek doküman şekli; (5) `nextPracticeQuestions`, `listSubjects` canlıda mı;
  (6) `recaptchaConfig` ANDROID uç noktası 200 ve AUDIT durumu. Anahtar/kişisel veri rapora yazılmaz. ⟶ —
- [x] **`SPK-01`** LaTeX motoru denemesi _(P0·M·Opus)_ — **Sonuç (21 Kasım 2026): şartlı geçti.** AndroidMath `v1.1.0` (MIT, JitPack) §5.3 alt kümesini `LatexCompatibility` sonrası çiziyor; bozuk girdide çökme yok; `width/ascent/descent` ölçüsü var; 16 KB uyumlu (64 bit). Zorunlu ayarlar: test bağımlılıkları `exclude`, `READ_/WRITE_EXTERNAL_STORAGE` `tools:node="remove"`, R8 `-keep class com.pvporbit.freetype.** { *; }` (yoksa release'te native çökme), `MTMathView` yerine `MTTypesetter` + Compose `Canvas`, typeset/çizim tek iş parçacığında, bitmap önbelleği. Ayrıştırıcı ASCII dışı harfleri siliyor (`\text{hız}` → "hz"): ürün sahibinin kararıyla **uygulama içi yama** (yer tutucu + geri koyma, çatal yok). Boyut: cihaz başına ~2,5 MB. Asıl metin: Atılabilir dalda: `AndroidMath`'i Compose'a (`AndroidView`)
  bağla; `STRUCTURED-EXAMS.md` §5.3 alt kümesi (kesir, kök, üs/indis, toplam/integral/limit, Yunan, matris/`cases`,
  `\text{}`, birim) + `LatexCompatibility` tablosu sonrası örnekler; bozuk LaTeX'te çökme yok; satır içi taban çizgisi
  hizası; yazı ölçeği 1,0 / 2,0; `LazyColumn`'da 30 kartta kaydırma akıcılığı; R8 release; lisans ve son sürüm tarihi;
  16 KB sayfa uyumu (yerel kod var mı). Çıktı: bulgu notu + öneri. ⟶ —
- [x] **`SCT-01`** Sözleşme _(P0·L·Opus)_ — **Tamam (21 Kasım 2026, `4a29947`):** modeller, saf yardımcılar, parser'lar, 9 arayüz + Fake'ler, mock örnek sınav (KOCAELİ › TOPOLOJİ 1 › 2003 FİNAL, 3 soru) + havuz, route'lar, geçici `QuestionRichText`, sürüm 4.2.0 (22); iOS vakaları taşındı; `assembleDebug` + `test` + `lintLiveDebug` yeşil. Açık: `Slug` NFD girdide iOS'tan ayrışabilir (NFC aynı). Asıl metin: Tek oturum, Faz 1'in ön koşulu.
  - Modeller (`core/model/`, tip düzeyinde `// Added in 4.2.0 — …`): `ExamQuestion` (+ `QuestionChoice`,
    `QuestionFigure`, `QuestionAnswer`), `ExamType` (17 tür, `isPracticeEligible`), `AIProvider`, `PracticeScope`,
    `PoolIndex`, `PracticeBatch`; `ExamSummary`/`ExamDocument`'e `questionCount: Int?` (`// Added in 4.2.0 — …`).
  - Saf yardımcılar (`core/util/`): `ExamRouting`, `QuestionLabel`, `LatexCompatibility`, `MathTextTokenizer`
    (+ `plainText`), `ExamTitleParser` (+ `canonicalTitle`, `validate`), `Slug` kontrolü (mevcut `Slug.make` iOS ile aynı mı).
  - Parser'lar: `ExamQuestionParser`, `PoolIndexParser`, `PracticeParser`; `FirestoreArchiveParser`'a `format` +
    `questionCount`.
  - Arayüzler + Fake'ler: `ExamQuestionReader`, `PracticeService`, `SubscriptionProofProvider`, `ExamConversionAdminService` (8 admin callable) + `SubjectMappingService` (4 callable) + `ExamConversion` modelleri ve parser'ı,
    `ModerationService.approve(record, provider)` ve `duplicateExamId`, `SubmitterProfileReader`.
  - Mock örnek veri: iOS `sample-questions.json` karşılığı (KOCAELİ › … › TOPOLOJİ 1 › 2003 FİNAL, 3 soru) + örnek
    havuz özeti; mock'ta şekil `SampleFilesInterceptor`'dan.
  - `ViFiRoute.StructuredExam`, `ViFiRoute.Practice…` tanımları; `QuestionRichText(text, modifier)` geçici gövdeyle.
  - Sürüm `versionName "4.2.0"`, `versionCode 22` (`DDEC-04`).
  - Kabul: `assembleDebug` + `test` yeşil; parser vakaları iOS testleriyle aynı (`StructuredExamParserTests`,
    `ExamTitleParserTests`, `MathTextTokenizerTests`, `QuestionLabelTests`, `LatexCompatibilityTests`).
  ⟶ `DDEC-01`, `DDEC-04`

### Faz 1 — Geliştirme (8 paralel hat)

#### Hat A — Soru ekranı ve yönlendirme

- [x] **`SQS-01`** Tek yönlendirme kararı _(P0·M·Opus)_ — Browse, son görüntülenenler ve bildirim "Sınava Git"
  `ExamRouting`'den geçer. Özetsiz açılışta `ExamViewerViewModel` belge structured çıkarsa soru ekranına **yer
  değiştirir** (geri yığınında sayfa görünümü kalmaz). Kilitli sınav soru ekranına ulaşmaz (mevcut `ExamLock` /
  `ExamAccessResolver`). Uzaktan bayrak yok. ⟶ `SCT-01`
- [x] **`SQS-02`** Soru ekranı _(P0·M·Sonnet)_ — `StructuredExamScreen` + `StructuredExamViewModel` + `UiState`: yükleme,
  hata + "Tekrar dene", boş "Bu sınavda soru yok"; yeniden yükleme başarısızsa eldeki sorular kalır; orijinal sayfaya
  otomatik dönüş yok (K23). Tüm içerik `ScreenCaptureProtection` içinde. testTag `questions.*`. ⟶ `SCT-01`
- [x] **`SQS-03`** `QuestionCard` _(P0·M·Sonnet)_ — Yeniden kullanılabilir kart (pratik de kullanır): başlık parametresi
  ("`<label>. Soru`" ya da "Soru N"), isteğe bağlı kaynak satırı, metin, şekiller, şıklar (açıkken "A ✓"), "Cevabı
  Göster/Gizle", cevap bölümü, çözüm adımları, yapay zekâ notu. Cevap durumu cihazda (`rememberSaveable`), Firestore'a
  yazılmaz. Turuncu üstünde siyah metin. ⟶ `SCT-01`
- [x] **`SQS-04`** Şekiller _(P0·S·Sonnet)_ — Coil + mevcut kimlikli `ImageLoader`; anahtar = `storagePath`; yalnız
  görünür olunca iner; oran yer tutucusu [0,1…2] (bilinmiyorsa 2/3); beyaz zemin, köşe 8; hata "Şekil yüklenemedi";
  çözme boyutu en çok 1600 px. `token=` kullanılmaz, yol üretilmez. ⟶ `SCT-01`
- [x] **`SQS-05`** Erişilebilirlik ve analytics _(P1·S·Sonnet)_ — Kart başına iki semantik düğüm: soru (`speech` →
  şıklar → "Şekil var.") ve cevap (yalnız açıkken; `answer.speech` → adımlar → not). Başlık `heading()`. Analytics
  `exam_open` (`kind: structured`, `question_count`) ilk başarılı yüklemede bir kez; `answer_revealed`
  (`question_order`) soru başına bir kez. Son görüntülenene kayıt ilk başarılı yüklemede. ⟶ `SQS-02`
- [x] **`SQS-06`** Liste ve son görüntülenen _(P1·S·Sonnet)_ — `ArchiveRow`: structured sınavda "N soru" + "Sorular"
  rozeti + liste ikonu (kilit rozeti önceliği korunur). `RecentExam`'a `isStructured` (eski `recentExams.v1` kaydı
  `false` çözülür; anahtar değişmez, göç testi). Kart rozeti "Sorular". ⟶ `SCT-01`

#### Hat B — Formül ve zengin metin

- [x] **`SMT-01`** LaTeX bağımlılığı ve sarmalayıcı _(P0·S·Sonnet)_ — `DDEC-02` seçimiyle `libs.versions.toml` +
  `app/build.gradle` (+ gerekirse R8 kuralı). `FormulaView` (satır içi / ekran), yazı ölçeği (gövde ≈ 17 sp, üst sınır),
  ekran formülü ~1,1 kat; çizilemeyen formülde kaynak `$…$` metin olarak düşer, çökme yok. ⟶ `SPK-01`, `DDEC-02`
- [x] **`SMT-02`** `QuestionRichText` _(P0·M·Sonnet)_ — Tokenizer parçalarını akıtır: metin (Markdown satır içi alt
  kümesi; blok yapılar düz metin; satır sonları korunur), satır içi formül taban çizgisinde (`InlineTextContent` ya da
  özel düzen), ekran formülü kendi satırında ortalı. Her formül çizimden önce `LatexCompatibility`'den geçer. ⟶ `SMT-01`
- [x] **`SMT-03`** `MathLineBreaker` _(P1·M·Sonnet)_ — Sütundan geniş formül üst düzeyde (virgül sonrası, ilişki/ikili
  işlem işaretleri) parçalara bölünür; gruplar asla bölünmez; hâlâ sığmayan parça küçültülür. iOS `MathLineBreakerTests`
  vakalarının aynısı. ⟶ `SMT-01`
- [x] **`SMT-04`** Performans _(P1·S·Sonnet)_ — Formül görsel önbelleği (anahtar: LaTeX + boyut + renk), `LazyColumn`'da
  yeniden çizimi önleme; 30 kartlık örnekte kaydırma ölçümü (Macrobenchmark gerekmez, `JankStats`/profiler notu yeter). ⟶ `SMT-02`

#### Hat C — "Sınava gir"

- [x] **`PRC-01`** Veri katmanı _(P0·M·Sonnet)_ — `FirestorePoolIndexReader` (`questionPoolIndex/<subjectId>` tek okuma;
  yoksa düğme yok), `FirebasePracticeService.nextPracticeQuestions(...)` (callable, zaman aşımı, hata → `PracticeError`
  Türkçe `userMessage`). Ders düğümünden `subjectId` okuma (`FirestoreArchiveParser`, katalog). ⟶ `SCT-01`
- [x] **`PRC-02`** Seçim ekranı _(P0·M·Sonnet)_ — `PracticeEntry` (ders ekranında, yalnız özette soru varsa) →
  `PracticeSetupScreen`: pratik türleri (materyaller yok), her birinde sayı; kapsam Bu ders / Üniversitem / Türkiye
  geneli, sayılarla, 1 soruluk kapsam da seçilebilir (P4). testTag `practice.*`. ⟶ `PRC-01`
- [x] **`PRC-03`** Premium kanıtı _(P0·S·Sonnet)_ — `PlayBillingSubscriptionService`'e etkin aboneliğin
  `{platform: "android", purchaseToken, productId}` kanıtı; her çağrıda gönderilir; yoksa `null` (ücretsiz). Token
  günlüğe yazılmaz. ⟶ `SCT-01`
- [x] **`PRC-04`** Oturum ViewModel'i _(P0·M·Sonnet)_ — Sıralı "Soru 1, 2…"; `exclude` = son 300 id; boş dönüş = havuz
  bitti → "Baştan başla" (`exclude` temizlenir, sayaç 1'den); ön-çekme yalnız `remainingToday == null` iken;
  `limitReached` → paywall olayı; analytics `practice_started`, `practice_answer_revealed`, `practice_limit_reached`,
  `practice_paywall_opened`. iOS `PracticeSessionViewModelTests` vakaları. ⟶ `PRC-01`, `PRC-03`

#### Hat D — Moderasyon ve admin (tam parite, `DDEC-01`)

- [x] **`ADM-01`** İki aşamalı 1. onay _(P0·M·Opus; birleşmeden önce Fable incelemesi)_ — `approve(record, provider)`:
  Storage kopyası (yeni ve mükerrer için) + tek batch'te cüzdan + defter + gönderim (`approved`, `tier`, `coins`,
  `provider`, `publishPlan`); **`exams`/`catalogNodes`'e yazmaz**. Alan adları iOS `FirebaseModerationService` ve
  `onSubmissionModerated` ile birebir; mevcut yeniden giriş korumaları (pending + defter) korunur. İki düğme. ⟶ `SCT-01`, `DDEC-01`
- [x] **`ADM-02`** Mükerrer gönderim (K22) _(P0·S·Opus)_ — `duplicateExamId`; detayın başında "Bu sınav arşivde zaten
  yüklü" uyarısı (`moderation.duplicateWarning`); onay düğmeleri aynen; 1. onay mükerrerde de dönüştürme başlatır. ⟶ `ADM-01`
- [x] **`ADM-03`** Gönderen kartı _(P1·S·Sonnet)_ — RTDB `users/<uid>/profile`'dan yalnız ad soyad, üniversite,
  fakülte, bölüm (telefon ve cinsiyet okunmaz/gösterilmez); profil yoksa kart gizli. Liste ve detay. ⟶ `SCT-01`
- [x] **`ADM-04`** Katalog adı büyük harf _(P1·S·Sonnet)_ — `publishPlan`'daki yeni düğüm adları Türkçe büyük harf
  (`CatalogName`, `Locale("tr")` kuralı; iOS `CatalogNameTests` vakaları). ⟶ `ADM-01`
- [x] **`ADX-01`** Hazır sınavlar listesi + inceleme _(P1·M·Sonnet)_ — `examConversions` (`status`, `createdAt desc`
  indeksi) listesi; inceleme ekranında orijinal sayfalar + taslak sorular yan yana; düzenleme yalnız kuralın izin
  verdiği alanlar (`text, speech, choices, answer, solution, needsReview`) + `editedBy == uid`, `editedAt` sunucu zamanı. ⟶ `ADM-01`
- [x] **`ADX-02`** Taslak işlemleri _(P1·M·Sonnet)_ — Tek soruyu yeniden üret (`regenerateDraftQuestion`, kira/ilerleme +
  `regenerationError`), tümünü yeniden üret (`regenerateExamConversion`), Sınavı iptal et (`discardExamConversion`),
  başlık listeden (`ExamTitleParser.canonicalTitle` önerisi, `validate`), ders seçimi (`subjectId` / yeni ders adı),
  Yayınla (`publishExamConversion`). Her AI işleminde Claude/Gemini seçimi. ⟶ `ADX-01`
- [x] **`ADX-03`** Başarısız işler + toplu dönüştür _(P1·M·Sonnet)_ — Başarısız listesi + "Tekrar başlat"; ders/bölüm
  seçip `requestBulkConversion` (`dryRun` ile tahmini maliyet + onay); `aiUsage/<YYYY-MM>` ile 50 $ göstergesi. ⟶ `ADM-01`
- [x] **`ADX-04`** "Yeni yapıya geçir" _(P1·S·Sonnet)_ — Dönüştürülmemiş sınavın ekranında yalnız admin için düğme
  (`requestExamConversion {examId, provider}`) + iş durumu. ⟶ `SQS-01`
- [x] **`ADX-05`** Yayındaki soruda Yönetici menüsü (K15) _(P1·S·Sonnet)_ — Soru kartının üstünde (kart içeriği
  dışında) "Düzenle" (`updatePublishedQuestion`) / "Yeniden üret" (`regeneratePublishedQuestion`); değişiklik hemen
  yayında, soru id'si aynı, şekil yolu değişir. ⟶ `SQS-03`
- [x] **`ADX-06`** Ders eşleme _(P1·M·Sonnet)_ — `listSubjects`, `setCourseSubject` (tek ders taşımada onay),
  `renameSubject`, `mergeSubjects`; iOS `SubjectMappingTests` vakaları. ⟶ `SCT-01`

#### Hat E — Küçük pariteler

- [x] **`SMP-01`** Sınav adı listesi 22 ad _(P0·S·Sonnet)_ — `ExamNameCatalog` iOS sırasıyla (VİZE … ÇÖZÜMLÜ SORULAR);
  `exam` metni `"<yıl> <AD>"`; Add Exam UI testi. ⟶ `SCT-01`
- [x] **`SMP-02`** Güncelleme uyarısı regresyon testi _(P2·S·Sonnet)_ — `vifi_optional_update` ile "Daha Sonra" /
  "Bu sürümü atla" sonrası uyarı aynı oturumda geri gelmiyor (iOS dca5c3c'nin Android karşılığı; kod değişikliği
  beklenmiyor). ⟶ —
- [x] **`SMP-03`** RTDB `Universitiess` kalıntısı _(P2·S·Sonnet)_ — Live `RepositoryModule` yorumunun güncellenmesi;
  başka referans yok. ⟶ —
- [x] **`SMP-04`** Bildirim uyumluluğu _(P1·S·Sonnet)_ — Yeni admin otomatik bildirimleri (`conversionReady`,
  `conversionFailed`, `conversionBudgetExceeded`) aksiyonsuz; Android bildirim ayrıştırıcısının bunları sorunsuz
  gösterdiği örnek yüklerle test edilir. ⟶ —

#### Hat F — Sunucu: Play abonelik doğrulaması (ortak depo, `DDEC-03` "evet" ise)

- [x] **`SRV-01`** `googlePlay.js` abonelik doğrulaması _(P0·M·Opus; birleşmeden önce Fable incelemesi)_ — **Sonuç (21 Kasım 2026):** ortak depoda `android/srv-01-play-subscription` dalı (`bb6cd8b` + inceleme düzeltmeleri `6a9e67e`; push/deploy yok). `subscriptionsv2.get`; ACTIVE / IN_GRACE_PERIOD / CANCELED (süre sonuna kadar) Premium; tek hesap bağı `subscriptionOwners/android-<taban sipariş>` (transaction); 24 sa önbellek, Play kesintisinde aynı token için en çok 72 sa koruma; reddedilen token 10 dk negatif önbellek; `obfuscatedExternalAccountId` ret nedeni değil (hesap silme sözleşmesi). Fable incelemesi: kritik yok, önemli/küçük bulgular düzeltildi. `npm test` 731 test, 727 geçti, 0 başarısız (4 eski todo). Plan değişimi (`linkedPurchaseToken`) ve onaylanmamış satın alma bilinen sınır. `v-4.2.0`'a birleştirme ürün sahibinin onayıyla (ortak depo). Asıl metin:
  `purchases.subscriptionsv2.get`; etkin + paket + ürün kontrolü; `index.js` pratik kanıt dalında iOS'la aynı dönüş
  şekli; tek hesap bağı (`subscriptionOwners`, Play sipariş kimliği; `obfuscatedAccountId` mevcut sha256 düzeniyle
  uyumlu); 24 saat hatırlama; geçersiz kanıt hata değil, ücretsiz. Emulator testleri (`practice.test.mjs`). iOS yolu
  değişmez. ⟶ `DDEC-03`
- [ ] **`SRV-02`** Deploy ⚠️ _(P0·S·ürün sahibi + Opus)_ — Yalnız ilgili Function'lar, ürün sahibinin açık onayıyla;
  sonrasında `nextPracticeQuestions` canlı salt sağlık kontrolü. ⟶ `SRV-01`

#### Hat G — Giriş güvenliği (eski reCAPTCHA taslağı)

- [x] **`RCD-01`** reCAPTCHA bağımlılığını açık yaz _(P1·S·Sonnet)_ — `libs.versions.toml` + `app/build.gradle`,
  neden-yorumuyla; ağaçta `firebase-auth 23.1.0` + `recaptcha 18.5.1`, çakışma yok. ⟶ `DDEC-05`
- [x] **`RCD-02`** Hata eşlemesi testleri _(P1·S·Sonnet)_ — `FirebaseAuthErrorsTest`'e tüm reCAPTCHA kodları +
  `FirebaseAuthMissingActivityForRecaptchaException` → `AppVerificationFailed`, "Error code: 39" →
  `SmsTemporarilyUnavailable`. Uygulama kodu değişmez. ⟶ —
- [x] **`RSD-02`** AUDIT açıldı _(eski taslak)_ — 18 Kasım 2026, ürün sahibinin onayıyla.

#### Hat H — Belge ve beyan

- [x] **`RLG-01`** Veri güvenliği: reCAPTCHA SDK'sı _(eski taslak)_ — 18 Kasım 2026'da Console'a girildi
  (`PLAY-DATA-SAFETY.md` notu).
- [x] **`SLG-01`** Veri güvenliği: 4.2.0 _(P0·S·Sonnet)_ — Yeni toplanan veri: pratik günlük sayacı (sunucu, hesaba
  bağlı), abonelik–hesap bağı (sipariş kimliği), yeni analytics olayları (Uygulama etkileşimleri zaten beyanlı mı).
  Sınav sayfalarının Vertex AI'da işlenmesi kullanıcı verisi değil arşiv içeriği; gönderilen sınav dosyaları "Fotoğraflar
  / Dosyalar" beyanında üçüncü taraf işleyici notu gerekir mi. Forma etkisi tablosu. ⟶ —
- [x] **`SLG-02`** Play politikası kontrolü _(P0·S·Opus)_ — `PLAY-POLICY-4.2.0.md` (yeni): Yapay zekâ ile üretilmiş
  içerik politikası (içerik sunucuda üretiliyor ve admin incelemesinden geçiyor; kullanıcı istemle üretmiyor →
  bildirme mekanizması şartı uygulanır mı), abonelik/ücretsiz deneme sınırı açıklığı (günde 5 soru, paywall metni),
  mutlak iddia yok. ⟶ —
- [x] **`SLG-03`** Mağaza metinleri _(P1·S·Sonnet)_ — `PLAY-STORE-LISTING.md`: 4.2.0 sürüm notu (iOS What's New ile
  aynı içerik: soru soru sınavlar, yapay zekâ notu, "Sınava gir"; mutlak iddia yok), gerekirse tam açıklama. ⟶ —
- [x] **`SLG-04`** Proje belgeleri _(P2·S·Haiku)_ — `CLAUDE.md` (durum, Mevcut durum tablosu: soru ekranı, pratik,
  LaTeX kütüphanesi, yeni callable'lar, yeni launch argümanları) + `README.md`. ⟶ Faz 1 hatları
- [x] **`SLG-05`** Sözleşme belgesinin Android'e alınması _(P2·S·Haiku)_ — `STRUCTURED-EXAMS.md`'nin Android'i
  ilgilendiren özeti (kişisel veri içermeden) `ANDROID-4.2.0-DETAIL.md` olarak; iOS test adları ↔ Android test
  sınıfları eşlemesi. ⟶ `SCT-01`

### Faz 2 — Entegrasyon ve Testler

- [x] **`SIT-01`** Birleştirme _(P0·M·Opus)_ — Sıra: G → B → A → C → D → E. Pratik oturum ekranı (`PracticeSessionScreen`)
  A'nın `QuestionCard`'ıyla bağlanır; `PracticeEntry` Browse ders ekranına; paywall yönlendirmesi; ekran koruması
  pratikte de açık. `assembleDebug` + `lintLiveDebug` + `test`. ⟶ Faz 1 kod hatları
- [x] **`SIT-02`** Birim testleri _(P0·M·Sonnet)_ — iOS ile aynı vakalar: ayrıştırıcılar (36 + 32 + 20), tokenizer,
  etiket, LaTeX tablosu, satır bölücü, soru/havuz/pratik parser'ları, `ExamRouting`, ViewModel'ler (Turbine), moderasyon
  1. onay yazım şekli (Fake backend), son görüntülenen göçü. ⟶ `SIT-01`
- [x] **`SIT-03`** UI testleri _(P0·M·Sonnet)_ — Mock: soru ekranı (cevap aç/kapa, not, boş/hata, şekil hatası),
  TalkBack semantiği (kapalı cevap ağaçta yok), büyük yazı, pratik (düğme görünürlüğü, 5. sorudan sonra paywall,
  Baştan başla), moderasyon iki düğme + mükerrer uyarısı + gönderen kartı, Add Exam 22 ad. Yeni launch argümanları
  (ör. `vifi_practice_limit`) `LaunchArgs.kt`'ye. ⟶ `SIT-01`
- [ ] **`SIT-04`** Tam paket _(P0·S·Sonnet)_ — live + mock, debug + release; emülatör API 24, 34, 37; sonuçlar olduğu
  gibi raporlanır. ⟶ `SIT-02`, `SIT-03`

### Faz 3 — Gerçek Ortam Doğrulaması (canlı backend; yayın değildir)  ⚠️ (canlı yazım her biri ayrı onay)

- [ ] **`SQA-01`** Canlı soru ekranı _(P0·S·Opus)_ — Live debug, test hesabı: yapılandırılmış sınav Browse / son
  görüntülenen / bildirimden soru ekranında açılıyor; şekiller iniyor; kilitli yapılandırılmış sınav kilit sayfasında
  kalıyor; dönüştürülmemiş sınav sayfa görünümünde; emülatörde `FLAG_SECURE` (ekran görüntüsü siyah). Yazım yok. ⟶ `SIT-04`
- [ ] **`SQA-02`** Canlı pratik _(P0·S·Opus)_ — Ücretsiz test hesabıyla 5 soru + paywall (sunucu sayacına yazar → onay);
  Premium kanıtı lisanslı test hesabıyla (`SRV-02` sonrası). ⟶ `SIT-04`, `SRV-02`
- [ ] **`SQA-03`** Canlı iki aşamalı onay _(P0·M·Opus)_ — Test gönderimi → Android'den 1. onay (Gemini; Claude kotası
  açıksa Claude) → coin, iş `queued` → taslak hazır bildirimi → 2. onay Android'in Hazır sınavlar ekranından → Android'de
  soru ekranı. Mükerrer gönderim yolu. Test kayıtları sonra temizlenir. ⟶ `SIT-04`
- [ ] **`SQA-04`** iOS ↔ Android çapraz _(P1·S·Opus)_ — Aynı sınav ve aynı pratik kapsamı iki platformda aynı soru
  sayısı, aynı başlıklar, aynı formül çıktısı (göz kontrolü, ekran görüntüsü yan yana). ⟶ `SQA-01`
- [ ] **`RSD-03`** ENFORCE sonrası Android kontrolü _(P1·S·Opus)_ — Yalnız ürün sahibi ENFORCE'u açarsa: ANDROID uç
  noktası 200, test numarasıyla giriş çalışıyor, `loginDiagnostics`'te reCAPTCHA kaynaklı artış yok (salt okuma). ⟶ ENFORCE onayı

### Faz 4 — Yayın  ⚠️ (ürün sahibiyle · K16 · her dış adım ayrı onay)

- [ ] **`SRL-01`** Önkoşul _(P0·S·Opus)_ — `SRV-02` canlıda (yoksa yayın yok), Faz 3 geçti, iOS 4.2.0 (13) incelemede /
  onaylı ve elle yayın bekliyor. ⟶ Faz 3
- [ ] **`SRL-02`** Dalın push'u _(P0·S·ürün sahibi)_ — `v-4.2.0`. ⟶ `SRL-01`
- [ ] **`SRL-03`** Release AAB _(P0·S·Opus)_ — `play-store/ViFi-4.2.0-22.aab`; R8 sonrası LaTeX kütüphanesi ve yazı
  tipleri pakette; boyut farkı 21 ile karşılaştırılır. ⟶ `SRL-02`
- [ ] **`SRL-04`** Dahili test _(P0·S·ürün sahibi + Opus)_ — 22'nin dahili teste yüklenmesi, Play önizleme raporu
  (desteklenen cihaz sayısı 21 ile aynı mı). ⟶ `SRL-03`
- [ ] **`SRL-05`** Veri güvenliği + politika beyanları _(P0·S·ürün sahibi + Opus)_ — `SLG-01`/`SLG-02` sonucu Console'a.
  ⟶ `SRL-04`
- [ ] **`SRL-06`** Üretim + inceleme, iOS ile aynı anda yayın _(P0·S·ürün sahibi)_ — %100 üretim, sürüm notu `SLG-03`;
  Play onayı gelince iOS elle yayınla aynı gün açılır (gerekirse Play'de yönetilen yayın). ⟶ `SRL-05`
- [ ] **`SRL-07`** İlk gözlem _(P1·S·Opus)_ — Mağaza, politika, vitals (formül çiziminden çökme / ANR), function
  hataları (`nextPracticeQuestions`, Play doğrulama). ⟶ `SRL-06`

---

## 4. Ayrıntı

#### `SCT-01` — Kaynak dosyalar (iOS `v-4.2.0`)
`Core/Models/ExamQuestion.swift`, `Practice.swift`, `SubjectMapping.swift`, `ExamConversion*.swift`;
`Core/Services/ExamQuestionParser.swift`, `PracticeParser.swift`, `ExamConversionParser.swift`,
`FirestoreArchiveParser.swift`; `Core/Utilities/ExamTitleParser.swift`, `MathTextTokenizer.swift`,
`LatexCompatibility.swift`, `MathLineBreaker.swift`, `QuestionLabel.swift`, `CatalogName.swift`; `App/ExamRouting.swift`;
`Preview Content/SampleExams/sample-questions.json`; sunucu `firebase/functions/conversions/schema.js`.

#### `SQS-01` — Yer değiştirme
iOS'ta özetsiz açılan sınav önce sayfa görünümüyle yüklenir, belge structured çıkınca soru ekranına geçer. Android'de
`ExamViewerViewModel` belgeyi yüklediğinde `questionCount != null` ise tek seferlik olay yayar, NavHost `popUpTo`
ile `Exam` yerine `StructuredExam` koyar (çift navigasyon `launchSingleTop`).

#### `PRC-04` — Çağrı sözleşmesi
`nextPracticeQuestions {subjectId, examType, scope: course|university|tr, courseId?, universityId?, exclude ≤ 300,
count 1…10, subscriptionProof?}` → `{questions, poolSize, remainingToday | null, limitReached}`. Soru = yayınlanmış soru
+ `id`, `examTitle`, `universityName`; `updatedAt` epoch ms. Ücretsizde `count = 1`, ön-çekme yok.

#### `ADM-01` — Dikkat
Mevcut `commitApproval` batch'i `catalogNodes`/`exams`/`childCount` yazıyor; yeni akışta bunlar sunucuda
(`publishExamConversion`). `publishPlan` şekli iOS `ArchivePublishPlan.publishPlanDocument` ile birebir olmalı; aksi
halde 2. onay yanlış yere yayınlar. Mükerrerde artık düşük ödül + yayın yok değil: dönüştürme başlar, 2. onay mevcut
sınavı değiştirir (K22). Ödül kademeleri iOS ile karşılaştırılır.

---

## 5. Riskler ve Açık Noktalar

- **Play abonelik doğrulaması** yapılmazsa Android Premium kullanıcısı pratikte sınırlanır ve yayın yapılmamalı
  (`DDEC-03`).
- **LaTeX motoru** en büyük teknik risk: komut kapsamı, satır içi hizalama, performans. `SPK-01` geçmeden Hat B başlamaz.
- **İki aşamalı onayda yanlış `publishPlan`** yanlış yere yayın ya da coin tutarsızlığı demektir; Fable incelemesi +
  canlı test (`SQA-03`) şart.
- **iOS yayını bekliyor:** iOS 4.2.0 incelemede, elle yayın. Android gecikirse iOS da bekler (K16).
- **Claude Opus Vertex kotası** henüz yok (22 Kasım'dan sonra yeniden talep). `SQA-03` Gemini ile yapılabilir.
- **`questions` okuması** +90 kullanıcılara açık (bilinen sınır, iOS ile aynı); pratik sorusu da aynı belge. Kapsam dışı.
- **Eski yapının kaldırılması** (`minimumVersion`, `imagess/`/`pdfs/` daraltma) iki platform da mağazadayken ayrı iş;
  sıra bozulursa eski istemciler arşivi açamaz.
- **Ortak Console değişikliği** (SMS Defense ENFORCE) iki platformu birden etkiler; yalnız ürün sahibinin onayıyla.
