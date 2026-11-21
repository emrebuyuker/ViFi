# Play Politika Kontrolü — 4.2.0 (SLG-02)

> **Durum (21 Kasım 2026):** Kontrol ve öneri; Console'a hiçbir şey girilmedi. Beyanlar `SRL-05`'te ürün sahibiyle
> verilir. Okunan kaynaklar: Play Geliştirici Programı Politikası, "Yapay zekâ ile üretilmiş içerik" politikası ve
> açıklama sayfası, Kullanıcı tarafından oluşturulan içerik ve Abonelikler politikaları, Data safety yardım sayfası
> (hepsi 21 Kasım 2026'da okundu; bağlantılar en altta). iOS tarafında okunan: `v-4.2.0` dalı (`STRUCTURED-EXAMS.md`
> §6, §14; `PracticeSessionView`, `StructuredExamView`, `PaywallView`), ortak hosting sayfalarının 21 Kasım sürümü
> (`5e39574`). Bu belge hukuki görüş değildir.
> İlgili belgeler: [`PLAY-DATA-SAFETY.md`](PLAY-DATA-SAFETY.md) §4.2.0 (S1…S6),
> [`PLAY-STORE-LISTING.md`](PLAY-STORE-LISTING.md) (4.2.0 sürüm notu), [`PLAY-POLICY-4.1.0.md`](PLAY-POLICY-4.1.0.md).
> Android'in 4.2.0 kodu henüz yazılmadı (Faz 1); uygulama içi değerlendirmeler yol haritasına ve iOS'a dayanır.

## 1. Özet

| Soru | Sonuç | Açık nokta |
|---|---|---|
| "Yapay zekâ ile üretilmiş içerik" politikası uygulanır mı; kullanıcı bildirme özelliği şart mı? | **Büyük olasılıkla kapsam dışı.** Politika içeriği "kullanıcı istemlerine göre üretilen" içerik olarak tanımlıyor; ViFi'de kullanıcı istem girmiyor, sorular sunucuda admin eylemiyle üretilip incelendikten sonra yayınlanıyor. Google'ın açıklama sayfası yalnız yapay zekâ içeriğini **barındıran** uygulamaları kapsam dışı sayıyor ve onları Kullanıcı içeriği politikasına yönlendiriyor. Yine de uygulama içinde bir bildirme yolu tutmak ihtiyatlı; bugünkü sayfa görüntüleyicide var, Android'in yeni soru ekranında ve pratik oturumunda planlanmamış | **P1**, **P2** |
| Abonelik ve ücretsiz kullanım sınırı yeterince açık mı? | **Kısmen.** Pratik oturumu kalan hakkı ve sınır ekranını gösteriyor (iOS). Ancak paywall Premium'un faydaları arasında sınırsız pratiği saymıyor; kullanım koşullarının abonelik bölümü pratik modunu ve "bir abonelik bir hesap" kuralını anmıyor | **P3**, **P4** |
| Play abonelik doğrulaması olmadan yayın? | **Yapılmamalı.** Doğrulama canlıda değilken Android Premium kullanıcısı pratikte günde 5 soruyla sınırlanır; satın alınan aboneliğin vaat edileni vermemesi Abonelikler politikası açısından da risk. Yol haritasında zaten yayın engeli (`DDEC-03`, `SRL-01`) | — |
| Mutlak iddia var mı? | **Yok** (önerilen metinlerde). Her cevabın altındaki not "yapay zekâ ile hazırlanmış ve incelenmiştir", koşullar ve sürüm notu "hata içerebilir" diyor. "İncelenmiştir" ifadesinin her yayın yolunda doğru kalması gerekir | **P5** |
| Veri güvenliği değişir mi? | Zorunlu değişiklik yok; "Satın alma geçmişi"nin kapsamı genişliyor (`PLAY-DATA-SAFETY.md` §4.2.0) | S1…S6 |

## 2. Yapay zekâ ile üretilmiş içerik

### Play ne diyor

- **Tanım:** Yapay zekâ ile üretilmiş içerik, üretken yapay zekâ modellerinin **kullanıcı istemlerine göre** ürettiği
  içeriktir. Politikanın örnekleri: sohbetin ana özellik olduğu metin sohbet botları ve metin, görsel ya da ses
  istemiyle üretilen görsel/video. ([Politika][ddp], [AI içerik politikası][aigc])
- **Bildirme şartı:** Yapay zekâ ile içerik üreten uygulamalar, kullanıcıların rahatsız edici içeriği uygulamadan
  çıkmadan geliştiriciye **bildirmesini ya da işaretlemesini** sağlayan bir uygulama içi özellik içermelidir;
  geliştirici bu bildirimleri içerik süzme ve moderasyonda kullanmalıdır. ([AI içerik politikası][aigc])
- **Kapsam dışı örnekler** ([Politikanın açıklaması][aigcfaq]):
  - Yalnız yapay zekâ içeriğini **barındıran** uygulamalar (ör. üretme özelliği olmayan sosyal medya); bunlar
    Kullanıcı içeriği politikasına tabidir.
  - Tek özelliği yapay zekâ ile üretilmemiş içeriği özetlemek olan uygulamalar.
  - Var olan bir özelliği yapay zekâ ile iyileştiren üretkenlik uygulamaları.
- **Genel kural:** Yapay zekâ ile içerik üreten uygulamalar diğer politikalara (kısıtlı içerik, aldatıcı davranış)
  uymak zorundadır; üçüncü taraf yapay zekâ entegrasyonunda sorumluluk geliştiricidedir. ([Politika][ddp])

### ViFi'de 4.2.0'da ne var

- Sorular, cevaplar ve çözümler **sunucuda** Vertex AI üzerinden (Claude ya da Gemini) üretilir. Üretimi yalnız
  **admin** başlatır (1. onay, "Yeni yapıya geçir", toplu dönüştürme, tek soruyu yeniden üret). Kullanıcı istem
  yazmaz, modelle etkileşmez, kendi isteğiyle içerik ürettiremez.
- Üretilen içerik **yayından önce admin incelemesinden** geçer ("Hazır sınavlar": taslak orijinal sayfayla yan yana,
  düzenle, yayınla). Yayındaki soru da admin tarafından düzeltilebilir.
- Kullanıcıya gösterilen içerik bir sınav arşivinin soru metni ve çözümüdür; her açılan cevabın altında "Bu çözüm
  yapay zekâ ile hazırlanmış ve incelenmiştir." notu durur (`STRUCTURED-EXAMS.md` §6, K10). Kullanım koşulları
  çözümlerin hatalı olabileceğini söyler.
- "Hata bildir" düğmesi **bilinçli olarak yok** (iOS kararı K10).

### Bugünkü bildirme yolları (kod taraması)

`grep -ri "bildir\|report" app/src/main/res/values` ve ilgili Kotlin dosyaları:

| Yer | Durum |
|---|---|
| Sayfa görüntüleyici (`ExamViewerScreen`) | **Var:** "Bize Ulaş" (zarf) düğmesi (`cd_exam_viewer_contact_support`), sınav bağlamıyla önceden doldurulmuş e-posta (`ContactSupport`). Kullanım koşullarının "Uygunsuz içeriği bildirme" bölümü bu düğmeyi ve e-posta adresini gösteriyor |
| Hakkında, giriş ekranı | "Bize Ulaş" var (genel destek) |
| Android soru ekranı (`StructuredExamScreen`, `SQS-02`) | **Henüz yok.** Ekran yazılmadı; yol haritasındaki `SQS-02` tanımı bir "Bize Ulaş" düğmesi saymıyor. iOS'un soru ekranında (`StructuredExamView`) sınav bağlamıyla "Bize Ulaş" düğmesi **var** |
| Pratik oturumu (`PracticeSession…`, `PRC-04`) | **Yok,** iOS'ta da yok. Oturumdaki soru farklı sınavlardan gelir; kullanıcı soruyu bildirmek için o sınavı ayrıca açmalı ya da Hakkında'dan e-posta yazmalı |
| `strings.xml` içinde "bildir"/"report" | Yalnız bildirim (push) metinleri; içerik bildirme metni yok |

### Değerlendirme

- **Kapsam:** ViFi'nin durumu politikanın tanımına (kullanıcı istemi) uymuyor; en yakın kalıp "yapay zekâ içeriğini
  barındıran uygulama". Bu kalıp için Google Kullanıcı içeriği politikasını işaret ediyor. ViFi o politikanın
  şartlarını zaten karşılıyor: Sınav Ekle'de koşulların zorunlu kabulü, yayından önce inceleme, görüntüleyicide
  "Bize Ulaş" ile bildirme ([UGC][ugc]; `PLAY-STORE-LISTING.md` §5). Sonuç: **bildirme özelliği şartı büyük olasılıkla
  doğrudan uygulanmaz.**
- **Belirsizlik:** Farklı olarak, içeriği üreten model **bizim** sunucumuzda, bizim hesabımızla çalışıyor; yani uygulama
  "yapay zekâ ile içerik üreten" bir hizmetin parçası olarak da okunabilir. Politika metni geliştiricinin istemle
  ürettiği içeriği açıkça kapsam içine ya da dışına koymuyor. İncelemede böyle okunursa bildirme özelliği istenir.
- **İhtiyatlı yol:** Her iki okumada da yeten şey, yapay zekâ içeriğinin gösterildiği her ekranda uygulamadan
  çıkmadan ulaşılan bir bildirme yolu. "Bize Ulaş" e-postası bir mail uygulaması açtığı için "uygulamadan çıkmadan"
  ölçütünü tartışmalı karşılar; bugün sayfa görüntüleyici için bu yol kabul edilmiş durumda (4.1.0 ve öncesi
  incelemelerden geçti). Bu belge kod önermez; karar ve kapsam P1/P2'de ürün sahibinindir.

## 3. Abonelik ve ücretsiz kullanım sınırı

### Play ne diyor

- Teklif koşulları, fiyat, faturalama sıklığı, otomatik yenileme ve **uygulamayı kullanmak için abonelik gerekip
  gerekmediği** açıkça gösterilmeli; abonelik olmadan içeriğe erişilebiliyorsa bu da görünmeli (genelde görünür bir
  kapatma seçeneğiyle). Kullanıcıyı neyin ücretsiz neyin ücretli olduğu konusunda yanıltan sunumlar ihlaldir; ilk
  dönemden sonra faydaları ciddi azaltan abonelik ihlal örneğidir. ([Abonelikler][subs])
- Politika kullanım sınırlarını (günlük kota) ayrıca düzenlemiyor; genel "açık ve doğru teklif" kuralı geçerli.

### ViFi'de ne var / olacak

| Yer | Metin / davranış | Kaynak |
|---|---|---|
| Ders ekranı | "Sınava gir" satırı: "Bu dersin soruları arasından soru çöz"; havuzda soru yoksa görünmez | iOS `PracticeFlowView`, Android `PRC-02` |
| Pratik oturumu (ücretsiz) | Her soruda "Bugün kalan: N soru" | iOS `PracticeSessionView`, Android `PRC-04` |
| Sınır ekranı | Başlık "Bugünlük ücretsiz soru hakkın bitti", metin "Ücretsiz soru hakkı her gün yenilenir. Premium ile istediğin kadar soru çözebilirsin.", düğme "Premium'a Göz At" → paywall | iOS, Android `PRC-04` |
| Paywall (Android bugün) | Başlık "Tüm sınavlara sınırsız eriş"; faydalar "Her dersin tüm sınavları açık", "Kilitli sınav kalmaz, coin gerekmez", "İstediğin kadar görüntüle ve indir", "İstediğin zaman iptal et"; otomatik yenileme notu, Koşullar ve Gizlilik bağlantıları, Kapat | `strings.xml` `paywall_*` |
| Mağaza | Önerilen sürüm notu ve tam açıklama "ücretsiz günde 5 soru, Premium'da sınırsız" der | `PLAY-STORE-LISTING.md` |
| Kullanım koşulları "Abonelik ve satın almalar" | Premium = "tüm sınavlar coin gerekmeden açılır"; pratik modu ve "bir abonelik bir hesap" geçmiyor | Hosting 21 Kasım |
| Gizlilik politikası | Pratik sayacı ve "bir abonelik yalnızca bir ViFi hesabında kullanılabilir" geçiyor | Hosting 21 Kasım |

### Değerlendirme

- **Ücretsiz/ücretli ayrımı:** Ücretsiz hak, kalan sayı ve sınır uygulamada görünür; sınır ekranı Premium'un sınırsız
  olduğunu söyler. Ücretsiz kullanıcının mevcut hakları azalmıyor (pratik modu yeni bir özellik; sınav görüntüleme
  kuralı değişmedi). Premium'a fayda **ekleniyor**, azalmıyor.
- **Eksik 1 (paywall):** Sınır ekranından açılan paywall, kullanıcıyı getiren faydayı (sınırsız pratik) listelemiyor.
  Teklif yanlış değil ama eksik; Abonelikler politikasının "teklif açık olmalı" beklentisine daha iyi uyması için
  paywall'da bu faydanın anılması düşünülebilir. iOS'ta da aynı eksik var (parite). → P3
- **Eksik 2 (koşullar):** Kullanım koşullarının abonelik bölümü Premium'un kapsamını yalnız sınav kilidiyle tarif
  ediyor; pratik sınırı ve aboneliğin ilk doğrulandığı hesaba bağlanması orada yok (gizlilik sayfasında var). Kural
  aboneliğin işleyişini etkileyen bir koşul olduğu için koşullarda da yazması tutarlı olur. Sayfa ortak depoda
  (iOS), Android yazmaz. → P4
- **Sayı tutarlılığı:** "5" yalnız mağaza metninde ve sunucuda (`PRACTICE_FREE_DAILY_LIMIT = 5`) sabit; uygulama
  sayıyı oturumdaki "Bugün kalan" ile dolaylı gösteriyor. Sunucu sınırı değiştirilirse mağaza metni de değişmeli.

## 4. Mutlak iddia ve yanıltıcı ifade

- Kullanılan ifadeler: "yapay zekâ ile hazırlanmış ve incelenmiştir" (uygulama), "hata içerebilir" (sürüm notu,
  önerilen açıklama), "doğrulukları garanti edilmez … hatalı olabilir" (koşullar). "Doğru", "hatasız", "garantili",
  "%100" gibi ifade yok. Ekran koruması için "korumanın kapsamında" deniyor, "ekran görüntüsü alınamaz" denmiyor.
- "İncelenmiştir" bir olgu iddiasıdır: her yayın yolu (1. ve 2. onay, "Yeni yapıya geçir", toplu dönüştürme, tek
  soruyu yeniden üretme, yayındaki soruyu düzenleme) yayından önce bir admin incelemesinden geçtiği sürece doğrudur.
  Sunucu tasarımı buna göre (2. onay = yayın). İnceleme atlanarak yayın yapılabilen bir yol eklenirse not metni de
  gözden geçirilmeli. → P5
- "Bağımsız uygulama" cümlesi ve üniversite adları için 4.1.0'daki not geçerli (`PLAY-STORE-LISTING.md` §5).

## 5. Diğer beyanlar

| Beyan | 4.2.0 | Not |
|---|---|---|
| Veri güvenliği | Zorunlu değişiklik yok | `PLAY-DATA-SAFETY.md` §4.2.0; S1 kabul edilirse "Satın alma geçmişi"ne amaç eklenir |
| Reklamlar | "Hayır" kalır | Yeni reklam yok |
| İçerik derecelendirmesi | Değişmez görünüyor | Kullanıcı içeriği paylaşımı "Evet", dijital satın alma "Evet" zaten var. Anket `SRL-05`'te açılırsa yapay zekâ içeriğine dair yeni bir soru olup olmadığına bakılır |
| Mağaza öğelerinde yapay zekâ beyanı | Değişmez | 31 Ekim kararı "Öğeleri etiketleme". 4.2.0 için yeni görsel planlanmadı; yapay zekâyla üretilmiş yeni bir mağaza görseli ya da metni eklenirse beyan yeniden değerlendirilir. Önerilen sürüm notu ve açıklama elle yazıldı |
| Hedef kitle | 18+ kalır | — |
| Abonelik ürünleri | Değişmez | `vifi_premium` (`monthly`/`yearly`); fiyat ve yenileme koşulu aynı |

## 6. Açık noktalar / ürün sahibine sorular

- **P1 — Soru ekranında bildirme yolu:** iOS'un soru ekranında sınav bağlamlı "Bize Ulaş" düğmesi var; Android'in
  `SQS-02` tanımında yok. Hem Kullanıcı içeriği politikasının bildirme beklentisi hem de olası "yapay zekâ içeriği"
  okuması için soru ekranında da bir bildirme yolu bulunsun mu (iOS paritesi)? Karar ana oturumun yol haritası
  işaretlemesine ve ürün sahibine bırakıldı; bu belge kod önermez.
- **P2 — Pratik oturumunda bildirme yolu:** iOS'ta da yok. Yapay zekâ ile hazırlanmış çözümler orada da gösteriliyor.
  Bildirme yolu gerekli görülürse iki platformda birlikte ele alınmalı; "Hata bildir" düğmesinin olmaması iOS kararı
  (K10) olduğundan bu bir ürün kararıdır.
- **P3 — Paywall'da sınırsız pratik:** Paywall'ın fayda listesine "Sınava gir'de sınırsız soru" eklensin mi (iki
  platformda)? Öneri: evet; teklifin açıklığı için. Zorunlu bir politika maddesine dayanmıyor.
- **P4 — Kullanım koşulları:** Abonelik bölümüne pratik modunun ücretsiz sınırı ve "abonelik ilk doğrulandığı hesaba
  bağlanır" kuralı eklensin mi? Sayfa ortak depoda; yayını ayrı onay ister.
- **P5 — "İncelenmiştir" ifadesi:** Her yayın yolunun admin incelemesinden geçtiği iOS/sunucu tarafında teyit edilsin
  (özellikle toplu dönüştürme ve "Tümünü yeniden üret" sonrası yayın).
- **P6 — Yapay zekâ politikasının okunuşu:** Bu belge "büyük olasılıkla kapsam dışı" diyor; resmî metin
  geliştiricinin ürettiği içeriği açıkça düzenlemiyor. Ürün sahibi farklı bir okumayı tercih ederse (kapsam içi) P1 ve
  P2 zorunlu hâle gelir.
- **Kapsam dışı:** Yapay zekâ ile üretilmiş içeriğe ilişkin Türk ve AB mevzuatı (ör. AB Yapay Zekâ Yasası'nın şeffaflık
  yükümlülükleri) bu belgede değerlendirilmedi; hukuki görüş önerilir.

[ddp]: https://support.google.com/googleplay/android-developer/answer/18258653
[aigc]: https://support.google.com/googleplay/android-developer/answer/13985936
[aigcfaq]: https://support.google.com/googleplay/android-developer/answer/14094294
[ugc]: https://support.google.com/googleplay/android-developer/answer/9876937
[subs]: https://support.google.com/googleplay/android-developer/answer/9900533
[ds]: https://support.google.com/googleplay/android-developer/answer/10787469
