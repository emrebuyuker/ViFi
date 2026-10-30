# Gizlilik Politikası ve Yasal Sayfalar — 4.1.0 Kontrolü (NLG-02)

> **Durum (30 Ekim 2026):** Kontrol ve öneri metni; **ürün sahibinin onayını bekliyor.** Sayfaların kaynağı ortak depoda
> (`ViFi-iOS/firebase/hosting/`). Değişikliği iOS tarafı yapar, hosting yayını ayrıca onay ister. Android bu dosyaları
> yazmaz ve yayınlamaz. Sayfalar Android ile iOS'ta ortak; iOS 4.1.0 da şu anki metinlerle App Store incelemesinde.
> Veri envanteri: [`PLAY-DATA-SAFETY.md`](PLAY-DATA-SAFETY.md) §4.1.0. Politika kontrolü: [`PLAY-POLICY-4.1.0.md`](PLAY-POLICY-4.1.0.md).

## 1. Nasıl kontrol edildi

- 30 Ekim 2026, 22:59 TRT'de üç sayfa salt okunarak indirildi: `https://vifi-831a8.web.app/gizlilik`, `/hesap-silme`,
  `/kullanim-kosullari`. Üçü de HTTP 200 döndü; `Last-Modified` 29 Ekim 2026 06:26 GMT.
- Üçü de `ViFi-iOS` `v-4.1.0` dalındaki dosyalarla bayt bayt aynı (SHA-256 eşleşti). Yani canlı metin depodaki
  metindir. Sayfalarda son değişiklik 4.0.0 satın alma metniydi (`8a1076e`). 4.1.0 için değişiklik yok.
- Ölçüt Play'in Kullanıcı Verileri politikası: gizlilik politikası, uygulama içi açıklamalarla birlikte kişisel verinin
  erişimini, toplanmasını, kullanımını ve paylaşımını kapsamlı anlatmalıdır. Veri güvenliği formunda yazanlarla sınırlı
  değildir ([Geliştirici Programı Politikası][ddp]).
- Ek ölçüt [Google Maps Platform şartları][gmp] (konum seçicisi Maps/Places SDK kullandığı için):
  - §3.2.2(a)(i): uygulamanın **kullanım koşulları**, uygulamada Google Maps özellik ve içeriği olduğunu bildirmeli.
    Bunların kullanımının Google Maps Ek Hizmet Şartları'na ve Google Gizlilik Politikası'na tabi olduğunu söylemeli.
  - §4.4(c)(iii): toplanacak veri türleri kullanıcıya önceden bildirilmeli. Konum ancak açık, önceden verilmiş ve geri
    alınabilir onayla alınabilir.

## 2. Bulgular

| # | Konu | Canlıda | Not |
|---|---|---|---|
| G1 | Kampanya (pazarlama) bildirimleri: onaya bağlı, varsayılan kapalı, Hesap'tan kapatılır, 22:00–09:00 gönderilmez | **Yok** | Profil satırı yalnız "sana uygun içeriğin gösterilmesi" diyor |
| G2 | Kampanya onayı kaydı: cevap, kaynak, zaman; hesapla saklanır | **Yok** | |
| G3 | Cihaz kaydı: FCM token, kurulum kimliği, platform, uygulama sürümü; hesaba bağlı | Kısmen | Satır şöyle: "Bildirim anahtarı, uygulama ve cihaz doğrulama bilgileri — Bildirim göndermek ve uygulamayı kötüye kullanıma karşı korumak için." Hesaba bağlanma, kurulum kimliği ve çıkışta silme anlatılmıyor |
| G4 | Profil bilgileriyle (üniversite, fakülte, bölüm, cinsiyet) bildirim hedefleme | **Yok** | |
| G5 | Okundu işareti, açılma/aksiyon istatistiği | **Yok** | "Uygulama kullanım olayları" satırı yalnız Google Analytics'i anlatıyor |
| G6 | Admin harita ekranı: Google Maps/Places; cihaz konumu yalnız haritayı odaklar | **Yok** | "Verilerin aktarılması" bölümü yalnız Firebase'i sayıyor, Google Maps Platform'u anmıyor. Uygulama bu sürümle konum izni istiyor |
| G7 | "ViFi reklam göstermez, reklam kimliğini kullanmaz ve verilerini satmaz." | Var | Kampanya bildirimleriyle çelişebilir; [`PLAY-POLICY-4.1.0.md`](PLAY-POLICY-4.1.0.md) R1 kararına bağlı |
| G8 | Hesap silme sayfası: bildirim verileri | **Yok** | Sunucu bu verileri siliyor (`onAccountDeleted`), sayfa söylemiyor. Kişi başı istatistik işaretleri silinmiyor (K3) |
| G9 | Kullanım koşulları: Google Maps bildirimi | **Yok** | Google Maps Platform şartları §3.2.2 bunu istiyor. iOS 4.1.0 da Google Maps kullanıyor |
| G10 | Yürürlük ve güncelleme tarihleri | 29 Ekim 2026 | Değişiklikle güncellenir |

**Sonuç:** Gizlilik, hesap silme ve kullanım koşulları sayfaları, Android 4.1.0 incelemeye gitmeden önce
güncellenmeli (`NRL-06`). iOS 4.1.0 da aynı sayfalara bağlı olduğundan, güncellemenin olabildiğince erken yapılması
önerilir.

## 3. Önerilen metin

Köşeli parantezli `[…]` kısımlar ürün sahibinin kararlarına bağlıdır (R1, K3; K1 karara bağlandı). Biçim, sayfaların bugünkü
"sen" diliyle aynıdır.

### 3.1 `gizlilik.html` — "Toplanan veriler" tablosu

**Değişen satırlar:**

| Veri | Neden? |
|---|---|
| Ad, soyad, cinsiyet, üniversite, fakülte, bölüm | Profilinin oluşturulması, sana uygun içeriğin gösterilmesi ve bildirimlerin ilgili öğrencilere (ör. yalnızca bir bölüme) gönderilebilmesi için. |
| Bildirim anahtarı ve cihaz kaydı (uygulamanın bu kurulumuna özgü kimlik, platform, uygulama sürümü) | Bildirimleri hesabına kayıtlı cihazlarına iletebilmek için. Çıkış yaptığında ya da hesabını sildiğinde o cihazın kaydı silinir. |
| Uygulama ve cihaz doğrulama bilgileri | Uygulamayı kötüye kullanıma karşı korumak için. |

**Yeni satırlar:**

| Veri | Neden? |
|---|---|
| Kampanya bildirimi tercihin (verdiğin cevap, ne zaman ve nereden verdiğin) | Kampanya bildirimlerini yalnızca onay verdiysen göndermek ve tercihini kayıt altında tutmak için. |
| Bildirim etkileşimleri (Bildirimler sayfasını en son ne zaman açtığın; bir bildirimi açtığın ya da düğmesine dokunduğun) | Okunmamış bildirim sayısını cihazlarında doğru göstermek ve bildirimlerin kaç kişi tarafından açıldığını ölçmek için. |

### 3.2 `gizlilik.html` — yeni bölüm "Bildirimler" ("Toplanan veriler"den sonra)

> ViFi sana üç tür bildirim gönderebilir: duyurular; sınav ve hesap bildirimleri (ör. gönderdiğin sınavın onaylanması);
> kampanya bildirimleri. Bir bildirimin kime gideceği profilindeki üniversite, fakülte, bölüm ve cinsiyet bilgisine göre
> seçilebilir.
>
> Kampanya bildirimleri öğrencilere özel indirimler, kampüsüne yakın fırsatlar ve Premium teklifleri içerir. Bu
> bildirimler yalnızca açıkça onay verdiysen gönderilir. Onay vermezsen kampanyaları ne bildirim olarak ne de Bildirimler
> sayfasında görürsün. Kampanya bildirimleri gece 22:00–09:00 arasında gönderilmez. Onayını Hesap › Bildirim Ayarları'ndan
> istediğin an geri alabilirsin; bildirim iznini cihazının ayarlarından kapatabilirsin. Onay vermenin ya da vermemenin
> karşılığında hiçbir ödül ya da kısıtlama yoktur.
>
> [R1: Kampanyalarda başka işletmelerin ürün ve hizmetleri tanıtılabilir. Bu işletmelerle kişisel verilerin
> paylaşılmaz; kimin bildirim alacağı yalnızca ViFi'nin sunucularında belirlenir.]

### 3.3 `gizlilik.html` — yeni bölüm "Yöneticilerin harita ekranı"

> ViFi yöneticileri bir bildirime yer eklerken Google Haritalar ve Google Places hizmetlerini kullanır. Bu ekran
> yalnızca yöneticilere açıktır. Yönetici izin verirse harita, cihazının konumuna (Android'de yaklaşık konumuna) odaklanır. Bu
> konum ViFi tarafından saklanmaz ve sunucularımıza gönderilmez. Harita ve yer araması sırasında Google; cihaz
> bilgilerini, IP adresini, arama metnini ve haritada gösterilen bölgeyi kendi gizlilik politikasına göre işler
> (https://policies.google.com/privacy). Bildirime eklenen yer, örneğin bir kafenin adı ve adresi, bildirimi alanlara
> gösterilir.

### 3.4 `gizlilik.html` — reklam cümlesi (G7)

- **R1 = kampanyalar ücretli/sponsorlu değil:** "ViFi uygulama içinde reklam göstermez; kampanya bildirimlerini yalnızca
  onay verenlere gönderir. Reklam kimliğini kullanmaz ve verilerini satmaz."
- **R1 = ücretli/sponsorlu kampanya var:** Cümle "ViFi, onay verenlere işletmelerin kampanyalarını bildirim olarak
  gönderebilir. Reklam kimliğini kullanmaz ve verilerini satmaz." olur. Ancak bu durumda önce
  [`PLAY-POLICY-4.1.0.md`](PLAY-POLICY-4.1.0.md) §2'deki politika riski çözülmelidir.

### 3.5 `gizlilik.html` — "Verilerin aktarılması"na eklenecek cümle

> Yöneticilerin bildirim hazırlarken kullandığı harita ekranında Google Haritalar ve Google Places hizmetleri kullanılır.

### 3.6 `gizlilik.html` — "Saklama süresi"ne eklenecek cümle

> Bildirim tercihin, bildirim kutun ve cihaz kayıtların hesabın açık kaldığı sürece saklanır; hesabını sildiğinde silinir.

K3 backend'de çözülmezse şu cümle de gerekir: "Bir bildirimi açtığın ya da düğmesine dokunduğun bilgisi, bildirim
istatistikleri için kullanıcı kimliğinle birlikte saklanır." Bu cümle için bir saklama süresinin de belirlenmesi
gerekir. Öneri backend çözümüdür.

### 3.7 `hesap-silme.html` — "Hangi veriler silinir?" tablosu

| Veri | Ne olur? |
|---|---|
| Bildirim kutusu, bildirim tercihin ve kayıtlı cihazların | En geç 30 gün içinde silinir. |
| [K3 backend'de çözülürse:] Bildirim istatistiklerindeki kişisel kayıtlar | En geç 30 gün içinde silinir; toplam sayılar anonim olarak kalır. |

### 3.8 `kullanim-kosullari.html` — yeni madde "Google Haritalar" (G9)

> ViFi, yöneticilerin bildirime yer eklediği ekranda Google Haritalar özelliklerini ve içeriğini kullanır. Google
> Haritalar özelliklerinin ve içeriğinin kullanımı, Google Haritalar/Google Earth Ek Hizmet Şartları'nın
> (https://maps.google.com/help/terms_maps/) ve Google Gizlilik Politikası'nın (https://policies.google.com/privacy)
> güncel sürümlerine tabidir.

### 3.9 Tarihler

Üç sayfanın da "Yürürlük tarihi" / "Son güncelleme" satırı yayın günüyle değişir.

## 4. Sayfa dışında gereken uygulama içi açıklama (Hat E'ye öneri)

Android'in konum izni diyaloğu, iOS'taki `NSLocationWhenInUseUsageDescription` gibi bir amaç metni göstermez. Google
Maps Platform şartlarının §4.4(c)(iii) maddesi veri türünün önceden bildirilmesini istiyor. Play'in "belirgin açıklama"
kuralı da beklenmeyen kullanımlarda izinden hemen önce bir açıklama istiyor. Admin'in haritayı açması bağlamı zaten
açık; yine de seçici, sistem diyaloğundan önce iOS'un amaç metnini kısa bir not olarak göstermeli:

> "Bildirim için konum seçerken haritayı bulunduğun yere odaklamak için konumun kullanılır. Konumun kaydedilmez ve
> paylaşılmaz."

Bu `MAP-02`/`MAP-03` kapsamındadır; Hat F kod yazmaz.

## 5. Yayın sırası (öneri)

1. Ürün sahibi R1 ve K3'ü karara bağlar ve metni onaylar (K1 verildi: yalnız yaklaşık konum).
2. iOS tarafı `ViFi-iOS/firebase/hosting/` altındaki üç dosyayı günceller.
3. Hosting yayını ayrı onayla yapılır: `firebase deploy --only hosting`.
4. Android 4.1.0 incelemeye gitmeden önce canlı sayfa yeniden salt okunur ve bu belgeyle karşılaştırılır (`NRL-06`).

## 6. Ürün sahibine sorular

- **P1:** §3'teki metinler onaylanıyor mu (G1–G10)?
- **P2:** Kullanım koşullarına Google Haritalar maddesi eklensin mi? Öneri: evet; şartların gereği ve iOS'u da kapsıyor.
- **P3:** Sayfalar iOS 4.1.0 yayına girmeden mi güncellensin, yoksa Android 4.1.0 incelemesinden önce mi? Öneri: en erken
  fırsatta. iOS 4.1.0 da kampanya bildirimi ve harita içeriyor.
- R1 ve K3 için [`PLAY-POLICY-4.1.0.md`](PLAY-POLICY-4.1.0.md) ile [`PLAY-DATA-SAFETY.md`](PLAY-DATA-SAFETY.md) §4.1.0'a bakın.

[ddp]: https://support.google.com/googleplay/android-developer/answer/18258653
[gmp]: https://cloud.google.com/maps-platform/terms
