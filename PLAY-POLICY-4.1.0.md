# Play Politika Kontrolü — 4.1.0 (NLG-03)

> **Durum (30 Ekim 2026):** Kontrol ve öneri; **kararlar ürün sahibinin.** Okunan kaynaklar: Play Geliştirici Programı
> Politikası (29 Ekim 2026'dan itibaren yürürlükteki metin), Play Console yardım sayfaları ve 29 Ekim 2026 politika
> duyurusu. Bağlantılar en altta. Bu belge hukuki görüş değildir.
> İlgili belgeler: [`PLAY-DATA-SAFETY.md`](PLAY-DATA-SAFETY.md) §4.1.0 (K1…K4),
> [`PRIVACY-POLICY-4.1.0.md`](PRIVACY-POLICY-4.1.0.md), [`PLAY-STORE-LISTING.md`](PLAY-STORE-LISTING.md).

## 1. Özet

| Soru | Sonuç | Karar |
|---|---|---|
| Onaylı ve kapatılabilir kampanya bildirimleri Play kurallarına uygun mu? | **Evet, bir koşulla.** Onay, kapatma ve sessiz saat kuralları Play'in spam ve onay beklentisini karşılıyor. Ancak Play sistem bildirimlerini yalnız uygulamanın **kendi özellikleri** için kullanmaya izin veriyor; örnekleri havayolunun fırsat bildirimi ve oyun içi promosyon. Bildirimle **reklam sunmak** açıkça ihlal örneği. | **R1** |
| "Reklam içeriyor" beyanı gerekiyor mu? | **R1'e bağlı.** Premium ve uygulama içi satın alma teklifleri beyan kapsamında değil. Ücretli ya da sponsorlu bir işletme kampanyası, bildirim akışında "native reklam" sayılır → "Evet". Karşılıksız öğrenci fırsatları için beyan gerekmez görünüyor. | **R2** |
| Yalnız ön plan konum izni beyan gerektiriyor mu? | **Bugün hayır, ama yeni kural geliyor.** Arka plan konum beyanı yalnız `ACCESS_BACKGROUND_LOCATION` içindir; biz istemiyoruz. Yeni "en dar kapsam" kuralına göre ise `ACCESS_FINE_LOCATION` isteyen her uygulama Console'da beyan verecek: beyan Kasım 2026'da açılıyor, 27 Ocak 2027'den itibaren zorunlu. Yalnız `ACCESS_COARSE_LOCATION` istenirse beyan gerekmez. | **K1 ✅** yalnız COARSE (uygulandı) |

## 2. Kampanya bildirimleri

### Play ne diyor

- **Sistem işlevinin izinsiz kullanımı** (Mobil İstenmeyen Yazılım politikası içinde): Sistem bildirimleri yalnız uygulamanın
  ayrılmaz özellikleri için kullanılabilir. Politikanın örnekleri, fırsatları bildiren bir havayolu uygulaması ve oyun
  içi promosyonları bildiren bir oyun. Yaygın ihlal örneği, sistem bildirimiyle reklam sunmak. ([Politika][ddp])
- **Reklamlar → Rahatsız edici reklamlar:** Reklamlar yalnız onları sunan uygulamanın içinde gösterilebilir.
  **Aldatıcı reklamlar:** Reklam bildirim gibi sistem öğelerini taklit edemez. ([Reklamlar][ads])
- **Reklam içeriği:** Reklamlar ve teklifleri uygulamanın parçası sayılır. Kısıtlı içerik politikalarına uymalı ve
  uygulamanın içerik derecelendirmesine uygun olmalıdır. ([Reklamlar][ads])
- **Spam:** İstenmemiş mesaj gönderen uygulamalara izin verilmez. ([Spam][spam])
- **Veri güvenliği:** Play'in "Reklam veya pazarlama" amacına verdiği örneklerden biri, başka ürün ya da hizmetleri
  tanıtan push bildirimleri ([Data safety][ds]). Bu da beyan edilmiş pazarlama bildiriminin olağan bir kullanım
  sayıldığını gösteriyor.

### ViFi'de bugün ne var

- **Onay:** Ayrı bir 2/2 sayfası. Varsayılan kapalı, önceden işaretli seçenek yok, karşılığında ödül yok. Kaydırma ya
  da Geri ile kapatılamaz; cevapsız kapanış "hayır" sayılır (`PRM-03`).
- **Kapatma ve süzme:** Hesap › Bildirim Ayarları'ndan her an kapatılır (`PRM-04`). Sunucu onay vermeyene kampanyayı
  ne push ne sayfa olarak gönderir. 22:00–09:00 arası gönderim yok.
- **Ayırt edilebilirlik:** Ayrı `promo` kanalı ("Kampanyalar ve fırsatlar") var; kullanıcı sistemden de kapatabilir.
  Sayfada "Kampanya" etiketi görünür. Bildirim ViFi'nin simgesi ve adıyla gelir, sistem uyarısı taklidi yoktur.
- **İçerik:** Onay sayfası kampanyaları "kampüsüne yakın indirimler, öğrenciye özel kampanyalar ve Premium fırsatları"
  olarak tanıtıyor. Konumlu bildirim sunucuda zorla kampanya yapılıyor; yani kampüs çevresindeki işletmeler öngörülüyor.

### Değerlendirme

| Kampanya içeriği | Bildirimle gönderilebilir mi | Gerekçe |
|---|---|---|
| Premium, coin, uygulama özellikleri | Evet | Oyun içi promosyon örneğine denk |
| ViFi'nin öğrencilere sunduğu, işletmeden ücret, komisyon ya da başka karşılık alınmayan fırsat | Büyük olasılıkla evet | Havayolu fırsat örneğine yakın; onay sayfası bunu uygulamanın özelliği olarak sunuyor ve mağaza açıklaması da anmalı (NLG-04) |
| İşletmenin ücret ya da karşılık vererek yaptırdığı tanıtım (sponsorlu) | **Hayır (ihlal riski)** | Sistem bildirimiyle reklam sunmak; reklamlar uygulamanın dışında gösterilemez |

**Öneri (R1):**
- Kampanya bildirimleri yalnız ilk iki satırdaki içeriği taşısın; ücretli/sponsorlu işletme tanıtımı bildirimle
  gönderilmesin.
- İleride sponsorlu kampanya istenirse, sistem bildirimi dışında, uygulama içinde "Sponsorlu" etiketiyle yeniden
  değerlendirilir. Bu durumda "Reklam içeriyor" beyanı da gerekir.
- Kampanya içeriği uygulamanın içerik derecelendirmesine uymalı. Bugünkü ankette alkol, tütün, kumar ve madde için
  "Hayır" var; bu türden işletme kampanyası gönderilmemeli. Bu kural admin'ler için bir yayın kuralı olarak yazılabilir.

## 3. "Reklam içeriyor" beyanı

Play Console'un "Uygulamayı incelemeye hazırlama → Reklamlar" sayfası ([Reklam beyanı][adsdecl]) şunları söylüyor:
- Üçüncü taraf reklam SDK'larıyla, görüntülü, native ya da banner biçiminde gösterilen reklamlar beyan edilir.
- Etiket, ücretli ürün yerleştirmesi ya da uygulama içi satın alma/yükseltme teklifi gibi diğer ticari içerikleri
  kapsamak için değildir.
- "Evet" denmesi gereken örnekler:
  - Banner ve geçiş reklamları (kendi ürününü tanıtmak için kullanılsa bile).
  - Native reklamlar, örneğin sponsorlu makaleler ya da akış içindeki reklamlar.
  - Kendi diğer uygulamalarını tanıtan "house ad"ler.
- Beyanın yanlış verilmesi ihlaldir ve Google beyanı her an doğrulayabilir.

| Kampanya türü | Beyan |
|---|---|
| Premium / uygulama içi satın alma teklifi | Hayır (Google'ın notu bunu kapsam dışı sayıyor) |
| ViFi'nin başka uygulamalarının tanıtımı | Evet olurdu; ViFi'nin başka uygulaması yok, uygulanmaz |
| Karşılıksız öğrenci fırsatı | Hayır (reklam geliri yok; sayfada "Kampanya" etiketi korunur) |
| Ücretli/sponsorlu işletme kampanyası | **Evet** (bildirim akışında sponsorlu içerik = native reklam); ayrıca §2'deki risk |

**Öneri (R2):** R1 önerisi kabul edilirse bugünkü cevap ("Hayır, reklam içermiyor") kalır. Ücretli kampanya varsa
"Evet" olur, gizlilik sayfasındaki reklam cümlesi de değişir (`PRIVACY-POLICY-4.1.0.md` §3.4).

## 4. Konum izni (admin harita seçicisi)

- **Arka plan konumu:** Beyan formu ve video yalnız `ACCESS_BACKGROUND_LOCATION` için ([Arka plan konumu][bgloc]).
  Seçici yalnız ön planda çalışıyor, bu izin istenmiyor → gerekmez.
- **Konum izinleri politikası** ([Politika][ddp]):
  - İzin yalnız mevcut bir özellik için istenir; reklam ya da analiz için istenmez.
  - En dar kapsam seçilir: kesin yerine yaklaşık, arka plan yerine ön plan.
  - Bizde kullanım tek: haritayı bir kez odaklamak. Kampanyaların hedeflemesi cihaz konumuyla değil, profildeki
    üniversite/fakülte/bölümle yapılıyor.
- **Yeni "en dar kapsam" kuralı** ([Ön plan konumu ve konum düğmesi][minscope]; [29 Ekim 2026 duyurusu][ann]):
  - **Takvim:** 15 Nisan 2026'da duyuruldu. Beyan Kasım 2026'da açılıyor; 27 Ocak 2027'den itibaren zorunlu (30 günlük
    öz uzatma var).
  - **İnce konum beyanı:** `ACCESS_FINE_LOCATION` isteyen her uygulama, konum düğmesinin ya da kaba konumun neden
    yetmediğini Console'da gerekçelendirir.
  - **Konum düğmesi:** Android 17 (API 37) ve üstünü hedefleyen uygulamalarda, adres seçme gibi tek seferlik kesin konum
    ihtiyaçları için zorunlu. Bugün `targetSdk 36` olduğundan şimdilik uygulanmaz; 37'ye geçişte yeniden bakılır.
  - Yaklaşık konumun yettiği örnekler arasında mağaza bulucu gibi "yakındakini göster" kullanımları var.
- **Değerlendirme:** Haritayı odaklamak için yaklaşık konum yeter. `MAP-02` doğruluk 1000 m'den kötüyse zaten 12
  yakınlaştırmasını kullanıyor. Kesin konum, "neden yetmedi" sorusuna iyi bir cevap bulamaz.
- **K1 — ✅ karar ve uygulama:** Ürün sahibi 30 Ekim 2026'da onayladı. Hat E `7c74b21`'de manifeste yalnız
  `ACCESS_COARSE_LOCATION` koydu (`ACCESS_FINE_LOCATION` ve `ACCESS_WIFI_STATE` `tools:node="remove"`). Bias da yalnız
  admin haritayı yerleştirdikten sonra gidiyor. Sonuç: ince konum beyanı gerekmez; Veri güvenliğinde konum beyan edilmez
  (`PLAY-DATA-SAFETY.md` K1). `NRL-03`'te release AAB'nin izin listesi yeniden kontrol edilir.
- **Belirgin açıklama:** Admin'in haritayı açması bağlamı belli, ama Android diyaloğu amaç göstermiyor. Diyalogdan
  önce iOS'un amaç metni kısa bir not olarak gösterilmeli (`PRIVACY-POLICY-4.1.0.md` §4).
- **İçerik derecelendirmesi:** "Kullanıcıların fiziksel konumunu başka kullanıcılarla paylaşma" sorusunun cevabı "Hayır"
  kalır. Admin'in seçtiği yer bir mekânın konumudur, kişinin anlık konumu değildir.

## 5. İzin ve onay akışının diğer kurallarla uyumu

Play'in Kullanıcı Verileri politikasındaki onay kuralları ([Politika][ddp]) ve 2/2 sayfasının karşılığı:

| Kural | `PRM-03` |
|---|---|
| Onay açık ve tek anlamlı sorulmalı | "Evet, fırsatları bildir" / "Hayır, teşekkürler" |
| Olumlu bir kullanıcı eylemi gerekir | Önceden seçili seçenek yok; iki düğmeden biri seçilir |
| Sayfadan çıkmak (dışarı dokunma, Geri, Ana ekran) onay sayılmaz | Kaydırma ve Geri kapalı; cevapsız kapanış "hayır" |
| Kendiliğinden kapanan ya da süreli mesajla onay alınmaz | Yok |
| Kullanıcıyı onaya yönlendirecek manipülasyon olmamalı | Onaya ödül yok; ret için ceza yok |

`POST_NOTIFICATIONS` için Console beyanı yoktur. İzin, 1/2 sayfasındaki açıklamadan sonra sistem diyaloğuyla istenir.

## 6. 29 Ekim 2026 duyurusundaki diğer maddeler (bilgi)

- **Ticari statü (trader) beyanı:** Kasım 2026'dan itibaren Console'da istenecek. 4.1.0'dan bağımsız bir hesap işi.
- **Uygunluğa dayalı indirimli abonelikler** (ör. öğrenci planı): uygunluk koşulları açıkça yazılmalı. ViFi Premium'da
  öğrenci indirimi yok → uygulanmaz. Bir gün "öğrenci fiyatı" düşünülürse geçerli olur.
- Yerel ağ, kişiler, AppFunctions ve SMS maddeleri ViFi'yi ilgilendirmiyor.

## 7. Ürün sahibine sorular

- **R1:** Kampanya bildirimleri ücretli ya da sponsorlu bir işletme tanıtımı içerecek mi? Öneri: hayır. Bildirimde
  yalnız ViFi'nin kendi teklifleri ve karşılıksız öğrenci fırsatları olsun; alkol, tütün, kumar içeriği olmasın.
- **R2:** "Reklam içeriyor" beyanı. Öneri: R1 "hayır" ise bugünkü "Hayır" kalsın.
- ~~**K1:**~~ ✅ Yalnız `ACCESS_COARSE_LOCATION`; ürün sahibi onayladı, Hat E uyguladı (`7c74b21`).
- **R3 (bilgi):** Ticari statü beyanı Kasım 2026'da Console'a geliyor; 4.1.0'dan bağımsız.
- **Kapsam dışı:** Kampanya push'larının Türk mevzuatındaki ticari elektronik ileti ve onay kuralları karşısındaki
  durumu bu belgede değerlendirilmedi; hukuki görüş önerilir.

[ddp]: https://support.google.com/googleplay/android-developer/answer/18258653
[ads]: https://support.google.com/googleplay/android-developer/answer/9857753
[adsdecl]: https://support.google.com/googleplay/android-developer/answer/9859455
[spam]: https://support.google.com/googleplay/android-developer/answer/9899034
[ds]: https://support.google.com/googleplay/android-developer/answer/10787469
[bgloc]: https://support.google.com/googleplay/android-developer/answer/9799150
[minscope]: https://support.google.com/googleplay/android-developer/answer/17033915
[ann]: https://support.google.com/googleplay/android-developer/answer/18100766
