# Play Console — Veri Güvenliği (Data Safety) Envanteri

> **Durum:** Taslak envanter (FM-20). Formu Play Console'da doldurup göndermek ürün sahibinin işidir.
> Kaynak: iOS `ViFi/Resources/PrivacyInfo.xcprivacy` (gizlilik etiketi) + Android hedef mimarisi (`core/model`).
> Özellikler eklendikçe güncellenir; aşağıdaki "Android'e özgü" satırları Play'e özeldir.
> **4.0.0 (LEG-02):** ViFi Premium ve parayla tek sınav açma ile "Finansal bilgiler → Satın alma geçmişi" eklendi
> (iOS gizlilik manifestindeki "satın alma geçmişi"nin karşılığı). Console formu 4.0.0 sürümüyle birlikte, ürün
> sahibince güncellenir; o zamana kadar canlıdaki 3.1.0 beyanı geçerlidir.
> **4.1.0 (NLG-01, 30 Ekim 2026; ürün sahibi 31 Ekim'de onayladı, Console'a girildi — `NRL-05`):** Hedefli bildirim sistemi için tabloya "Geliştirici
> iletişimleri" ve "Reklam veya pazarlama" amaçları, kampanya onayı kaydı, okundu işareti, açılma/aksiyon istatistiği ve
> kurulum kimliği eklendi. _(4.1.0)_ işaretli hücreler yenidir. Admin harita seçicisinin (Google Maps + Places SDK) ve
> konumun etkisi §4.1.0'dadır. K1 (konum) karara bağlandı ve uygulandı; K2…K4 31 Ekim 2026'da ürün sahibince öneri
> yönünde karara bağlandı. Console formu 4.1.0 sürümüyle
> girilir (`NRL-05`); o zamana kadar yayındaki sürümün beyanı geçerlidir.
> **4.2.0 (SLG-01, 21 Kasım 2026; öneri, Console'a girilmedi):** Soru soru sınavlar, "Sınava gir" pratik modu ve
> Premium'un sunucuda doğrulanması için envanter §4.2.0'da. Kısa sonuç: yeni veri türü yok; "Satın alma geçmişi"nin
> kapsamı aboneliği de içine alacak şekilde genişliyor (amaç eklemesi açık nokta), diğer satırlar değişmiyor. Console
> formu `SRL-05`'te ürün sahibiyle gözden geçirilir; o zamana kadar yayındaki 4.1.0 beyanı geçerlidir.

> **SMS Defense / reCAPTCHA SDK (18 Kasım 2026, ürün sahibinin onayıyla Console'a girildi ve kaydedildi):** Firebase BoM 33.5.1 reCAPTCHA Android SDK 18.5.1'i dolaylı getiriyor; SMS Defense AUDIT 18 Kasım'da açıldı. Google'ın beyan sayfasına göre SDK IP adresi, SDK sürümü, paket adı, kullanıcı eylemi, cihaz bütünlüğü yanıtı, uygulama etkileşimleri, yüklü uygulamalar ve cihaz kimliği toplar ([kaynak](https://docs.cloud.google.com/recaptcha/docs/disclosure-requirements-android)). Forma: **Yüklü uygulamalar** (yeni; toplanıyor, paylaşılmıyor, kısa süreli değil, zorunlu, amaç *Sahtekarlığı önleme, güvenlik ve kanunlara uygunluk*); **Uygulama işlemleri** ve **Teşhisler**'e aynı amaç eklendi; **Cihaz veya diğer kimlikler**'de amaç zaten vardı; IP adresi Play'de ayrı tür olmadığından beyan edilmez. Değişiklik aynı gün Yayın özeti'nden ürün sahibinin onayıyla incelemeye gönderildi.

## Toplanan veriler

| Play kategorisi → veri türü | Ne / nereden | Amaç | Hesaba bağlı | Zorunlu mu |
|---|---|---|---|---|
| Kişisel bilgiler → Telefon numarası | Firebase Phone Auth (+90) giriş; profil kaydı | Uygulama işlevi (hesap) | Evet | Evet |
| Kişisel bilgiler → Ad | Profil: ad, soyad | Uygulama işlevi | Evet | Evet (profil kapısı) |
| Kişisel bilgiler → Kullanıcı kimlikleri | Firebase Auth UID. _(4.1.0)_ Kişisel, segment ve kampanya bildirimleri UID'ye bağlı bildirim kutusuna (`users/<uid>/inbox`) yazılır; onay kaydı, okundu işareti ve cihaz kaydı UID altında tutulur | Uygulama işlevi, analiz, _(4.1.0)_ **geliştirici iletişimleri**, **reklam veya pazarlama** | Evet | Evet |
| Kişisel bilgiler → Diğer bilgiler | Profil: cinsiyet, üniversite/fakülte/bölüm. _(4.1.0)_ Sunucu bildirimin alıcılarını bu alanlarla seçer: admin'in "Hedef kitle"si (üniversite › fakülte › bölüm + cinsiyet) ve otomatik "Bölümüne yeni sınav eklendi" | Uygulama işlevi, analiz (segment), _(4.1.0)_ **geliştirici iletişimleri**, **reklam veya pazarlama** | Evet | Evet |
| Uygulama etkinliği → Uygulama etkileşimleri | Firebase Analytics olayları (göz atma, sınav açma, coin, giriş hunisi). _(4.1.0)_ Bildirim izni ve kampanya onayı olayları (6); bildirim sayfasının okundu işareti (`users/<uid>/settings/inbox.readAt`: okunmamış sayısı cihazlar arasında aynı kalır); açılma/aksiyon istatistiği (`trackNotificationEvent` → `notifications/<id>/events/<uid>_<olay>` + sayaç) | Analiz, _(4.1.0)_ **uygulama işlevi** | Evet (UID ile) | Hayır* |
| Uygulama etkinliği → Diğer işlemler _(4.1.0)_ | Kampanya onayı kaydı `users/<uid>/settings/notifications` = `{marketing, source, version, updatedAt}`. "Evet" ve "Hayır" cevaplarının ikisi de yazılır; Hesap › Bildirim Ayarları'ndan her an değişir | Uygulama işlevi, reklam veya pazarlama | Evet | Hayır (isteğe bağlı) |
| Uygulama etkinliği → Diğer kullanıcı içeriği | Kullanıcının yüklediği sınav görselleri/PDF'leri (moderasyon için) | Uygulama işlevi | Evet | Hayır (isteğe bağlı) |
| Uygulama bilgileri ve performans → Tanılama | Giriş hatası tanılaması (`recordLoginDiagnostic`: hata kodu, aşama; numara yalnızca salted-hash + son 4) | Uygulama işlevi (destek) | Hayır | Hayır |
| Cihaz veya diğer kimlikler | FCM kayıt token'ı (bildirim), App Check / Play Integrity, Analytics uygulama örneği kimliği. _(4.1.0)_ Kurulum kimliği (kurulum başına UUID) + token: `users/<uid>/devices/<kurulumId>` = `{token, platform, appVersion, updatedAt}`; kampanya push'u da bu token'a gider | Uygulama işlevi, analiz, _(4.1.0)_ **geliştirici iletişimleri**, **reklam veya pazarlama** | Evet | Evet |
| Finansal bilgiler → Satın alma geçmişi _(4.0.0)_ | Parayla tek sınav açma: Google Play sipariş kimliği (yoksa satın alma jetonunun özeti) + UID + sınav kimliği, sunucuda `purchasedTransactions` ve `users/<uid>/unlocks` (`purchaseExamUnlock`) | Uygulama işlevi | Evet | Hayır (yalnız satın alan) |

\* Analytics toplama kapatma seçeneği şu an yok; formda "isteğe bağlı değil" olarak işaretlenmeli ya da bir kapatma ayarı eklenmeli (karar ürün sahibinde). 4.1.0'ın okundu işareti ve istatistiği de otomatiktir, kapatılamaz.

Admin harita seçicisinden gelebilecek satırlar (kilitlenme günlükleri, tanılama, uygulama içi arama geçmişi) K2'ye
bağlıdır. Konum beyan edilmez (K1, uygulandı); bkz. §4.1.0.

## Android'e özgü notlar

- **Reklam kimliği (AD_ID):** Kullanılmıyor. Reklam SDK'sı kaldırıldı (FM-08); Analytics'in eklediği `AD_ID`,
  `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION` izinleri manifest'ten `tools:node="remove"` ile
  çıkarıldı ve `google_analytics_adid_collection_enabled=false`; ayrıca
  `google_analytics_default_allow_ad_personalization_signals=false` (reklam kişiselleştirme sinyali yok, AN-08).
  Birleşik (merged) manifest'te AD_ID izni bulunmadığı doğrulandı. Play beyanı: "reklam kimliği kullanılmıyor".
- **Paylaşım:** Veri üçüncü taraflarla paylaşılmıyor; Google (Firebase) hizmet sağlayıcı olarak işler (Play'de "paylaşım" sayılmaz).
- **Aktarımda şifreleme:** Evet (Firebase, HTTPS/TLS).
- **Silme talebi:** Uygulama içi hesap silme (iOS'ta var; Android'de Hesap alanı) — Play hesap silme URL'si de gerekli.
  4.1.0: hesap silinince sunucu (`onAccountDeleted`) `users/<uid>` altını siler: bildirim kutusu, cihaz kayıtları,
  onay kaydı ve okundu işareti. Uygulama cihaz kaydını çıkıştan ve silmeden önce kendisi de siler (`PRM-05`).
  Açılma/aksiyon istatistiğinin kişi başı işaretleri (`notifications/<id>/events/<uid>_<olay>`) de silinir; anonim
  sayaçlar kalır (K3, ortak depo `ab53aa2`, 31 Ekim'de canlıda).
  4.0.0 (ürün sahibi kararı, 2026-10-29): `purchasedTransactions` kayıtları tekrar kullanımı önlemek için hesap
  silinince de saklanır, hesapla bağı (UID) en geç 30 gün içinde elle kaldırılır; hesap silme ve gizlilik sayfaları
  bunu söyler (LEG-01).
- **Coin bakiyesi / açılmış sınavlar:** Uygulama içi durum; coin parayla satın alınmaz, finansal veri sayılmaz.
- **Satın almalar (4.0.0):**
  - *ViFi Premium aboneliği* cihazda Google Play Billing ile doğrulanır; sunucuya yazılmaz, bizim tarafımızda
    toplanmaz (iOS ile aynı, `DEC-03`). _4.2.0'da değişir:_ "Sınava gir"de Premium'u kanıtlamak için satın alma
    jetonu sunucuya gider ve abonelik hesaba bağlanır (§4.2.0).
  - *Parayla tek sınav açma:* satın alma jetonu `purchaseExamUnlock`'a gider, Play Developer API ile doğrulanır;
    sunucuda yukarıdaki "Satın alma geçmişi" tutulur.
  - *Ödeme bilgileri* (kart, banka) Google Play'de kalır, uygulamaya gelmez → "Finansal bilgiler → Ödeme bilgileri"
    beyan edilmez.
  - Google Play'e `obfuscatedAccountId` olarak UID'nin SHA-256 özeti, `obfuscatedProfileId` olarak sınav kimliğinin SHA-256 özeti
    iletilir (dolandırıcılık sinyali ve sınav eşleşmesi). Google ödeme hizmet sağlayıcısı olduğundan paylaşım
    sayılmaz.
  - Satın alma için ayrı bir analytics olayı yok; yalnız paywall'ın `screen_view` (`Paywall`) kaydı var.

## Analytics envanteri (AN-04/05/07, AN-11)

iOS 3.1.0 ile aynı adlar; kaynak `core/model/AnalyticsEvent.kt` + `AnalyticsEventMapping.kt`.

- **Olaylar (29):** `screen_view`, `browse_level`, `exam_open`, `recent_exam_open`, `unlock_sheet_shown`,
  `earn_coins_tapped`, `exam_unlock`, `exam_unlock_failed`, `exam_submit`, `exam_submit_failed`,
  `login_code_requested`, `login_success`, `login_failed`, `onboarding_complete`, `profile_completed`,
  `profile_updated`, `search_used`, `sign_out`, `account_deleted`, `update_prompt_shown`, `update_action`,
  `submission_approved`, `submission_rejected` (4.0.0'da yeni olay yok; `screen_view` ekran adlarına `Paywall`
  eklendi). _4.1.0:_ `notification_priming_shown|accepted|declined`, `marketing_consent_shown|accepted|declined`
  (tek parametre `source`: `prompt`|`reask`|`settings`); ekran adlarına `NotificationSettings`, `NotificationComposer`,
  `NotificationHistory` eklendi (18 ekran). Parametreler yalnız düşük-kardinaliteli sabitler: ekran adı,
  katalog seviyesi, dosya biçimi (PDF/JPG), dosya sayısı, coin miktarı, tier, sabit hata `reason`/`step`,
  bayraklar. **Arşiv/ders adı, telefon, isim veya serbest metin gönderilmez.**
- **Kimlik:** `setUserId` = Firebase Auth UID (oturum açılınca; çıkışta `null`).
- **Kullanıcı özellikleri (5):** `university_id`, `faculty_id`, `department_id` (kanonik slug `[a-z0-9-]`),
  `gender` (`female`/`male`), `is_admin` (`true`/`false`). Profil yokken slug/cinsiyet temizlenir; çıkışta hepsi
  temizlenir. Analytics kullanıcı özellikleri yalnız analiz içindir. _4.1.0:_ bildirim hedeflemesi Analytics audience
  ile değil, sunucuda RTDB profilleriyle yapılır (`getNotificationAudience`, `deliverNotification`); istemci `all`
  topic'ine abone kalır (genel duyurular) ve cihazını `users/<uid>/devices`'a kaydeder (kişi başı teslim).
- **Limitler:** olay/parametre adı ≤40, string değer ≤100, olay başına ≤25 parametre, özellik adı ≤24 / değeri ≤36
  (kırpılır) — `AnalyticsLimitsTest` birim testiyle doğrulanır.
- **QA (canlı, ürün sahibi):** `adb shell setprop debug.firebase.analytics.app com.salticusteam.vifi` ile
  DebugView'de olaylar + 5 özellik kontrol edilir (bu repo canlı DebugView'e bakmaz).

## Release küçültme (R8) ile ilgili

- `release`: `minifyEnabled true`, `shrinkResources true`, `proguard-android-optimize.txt` + `app/proguard-rules.pro`.
- İmzalama anahtarları repoda yok: `keystore.properties` (git'e girmez) veya CI secret.
- Analytics olay/özellik adları derleme anı sabit string'lerdir (`firebaseName` / `firebaseParameters`), reflection
  yok; R8 için ek keep kuralı gerekmez.
- Mock örnek verisi release'e girmez (`mockRelease` variant'ı yok — FM-18).

## 4.1.0 — Bildirim sistemi (NLG-01)

> Kaynaklar: iOS `v-4.1.0` gizlilik manifesti (`ANDROID-4.1.0-DETAIL.md` §B.8), yol haritası (`PSH-04`, `INB-01`,
> `INB-05`, `PRM-01`, `MAP-01`, `MAP-02`), ortak backend (`ViFi-iOS/firebase/functions/notifications/`, salt okundu) ve
> aşağıdaki resmi Google sayfaları (30 Ekim 2026'da okundu). Bu bölüm öneridir; form ürün sahibinin onayıyla doldurulur.

### Amaçların gerekçesi

Play formundaki amaç tanımları ([Data safety yardım sayfası][ds]) ile 4.1.0'daki kullanım:

| Play amacı | Play'in tanımı (özet) | 4.1.0'daki karşılığı |
|---|---|---|
| Uygulama işlevi | Uygulamadaki özellikleri çalıştırmak | `system` bildirimleri (sınavın onaylandı/reddedildi, bölümüne yeni sınav), bildirim sayfası ve okundu işareti, cihaz kaydı |
| Geliştirici iletişimleri | Uygulama ya da geliştirici hakkında haber ve bildirim göndermek. Play'in örneği bir push bildirimi | `general` duyurular: `all` topic'iyle herkese ya da admin'in seçtiği segmente |
| Reklam veya pazarlama | Pazarlama iletişimini göstermek ya da hedeflemek. Play'in örneklerinden biri başka ürün veya hizmetleri tanıtan push bildirimleri | `promo` (kampanya) bildirimleri. Yalnız onay verenlere gider, UID'ye bağlı bildirim kutusuna yazılır, kayıtlı cihaz token'ına push olur, alıcılar profil segmentleriyle seçilir |
| Analiz | Uygulamanın nasıl kullanıldığını ölçmek | Bildirim izni/onay olayları (Firebase Analytics), admin geçmişindeki açılma/aksiyon sayıları |

- **Hesap verisi bildirim için de kullanılıyorsa** o amaç ayrıca işaretlenir; Play'in SSS'i, hesaba eklenen bir bilginin
  bildirim göndermek için kullanılması örneğini veriyor ([Data safety, SSS][ds]). Bu yüzden profil segmentleri ve UID'ye
  iki yeni amaç eklendi.
- **Paylaşım yok.** Kampanyalar başka işletmelerin ürünlerini tanıtsa bile kullanıcı verisi o işletmelere gitmez; hedefleme
  bizim sunucumuzda yapılır. Firebase (Google) hizmet sağlayıcıdır; Play hizmet sağlayıcıya aktarımı "paylaşım" saymaz.
- **iOS karşılığı:** iOS etiketi User ID, Device ID ve "Other Data Types" (profil segmentleri, onay) için "Developer's
  Advertising or Marketing", Product Interaction için "App Functionality" ekledi. "Geliştirici iletişimleri" Play'e
  özgü bir amaçtır; Apple'da karşılığı yok (K4).

### Bildirim verilerinin ayrıntılı envanteri

| Veri | Nerede | Ne zaman yazılır | Kim okur | Ne zaman silinir | Play türü |
|---|---|---|---|---|---|
| Cihaz kaydı: FCM token + kurulum kimliği (UUID) + `platform: android` + `appVersion` + `updatedAt` | Firestore `users/<uid>/devices/<kurulumId>` | Oturum ve token birlikteyken, her soğuk açılışta ve token yenilenince (`PSH-04`) | Sunucu (teslim) | Çıkış ve hesap silmeden önce uygulama siler (≤3 sn); aynı token başka hesapta görülünce sunucu eskisini siler; ölü token'ın kaydı teslimde silinir; hesap silinince sunucu siler | Cihaz veya diğer kimlikler |
| `all` topic aboneliği | FCM | Her açılışta ve token yenilenince; oturumdan bağımsız | FCM | Uygulama kaldırılınca | Cihaz veya diğer kimlikler (aynı token) |
| Bildirim kutusu kopyası | `users/<uid>/inbox/<id>` | Sunucu, kişi başı teslimde | Kullanıcının kendisi | Hesap silinince | Kullanıcı kimlikleri (içerik admin'in, kullanıcı verisi değil) |
| Genel duyuru | `announcements/<id>` | Sunucu | Herkes | Süresi dolunca listede görünmez | — (kullanıcı verisi yok) |
| Kampanya onayı | `users/<uid>/settings/notifications` | 2/2 sayfasında "Evet"/"Hayır" ve Hesap › Bildirim Ayarları anahtarı (`PRM-01`, `PRM-03`, `PRM-04`) | Kullanıcı + sunucu (kampanya süzgeci) | İstemci silemez (kural); hesap silinince sunucu siler | Uygulama etkinliği → Diğer işlemler |
| Okundu işareti `readAt` | `users/<uid>/settings/inbox` | Bildirim sayfası açılınca, tek yazım (`INB-01`) | Kullanıcının cihazları | Hesap silinince | Uygulama etkileşimleri |
| Açılma/aksiyon istatistiği | `notifications/<id>/events/<uid>_<olay>` = `{uid, event, createdAt}` + `notifications/<id>.stats` sayacı | Push'tan açılınca (`opened`) ve aksiyon düğmesine/kartına dokununca (`actionTapped`); kullanıcı + olay + bildirim başına bir kez (`INB-05`) | Yalnız admin (sayılar) | ⚠️ Hesap silinince **silinmiyor** (K3) | Uygulama etkileşimleri |
| Analytics olayları (6) + ekran adları (3) | Firebase Analytics | İzin/onay sayfalarında ve ekran açılışında | Geliştirici | Analytics saklama süresi (en çok 14 ay) | Uygulama etkileşimleri |

**Cihazda kalanlar (toplama sayılmaz, beyan gerekmez):** izin/onay sorma geçmişi (`notifications.primingOffered.v1` ve
diğerleri), gönderilen istatistik anahtarları (`notifications.reportedEvents.v1`, en çok 500), kurulum kimliğinin yerel
kopyası, tepsideki bildirimler, push görseli (bellekte; imzalı URL loglanmaz).

**Admin'e özgü içerik:** bildirim taslağı ve geçmişi (`notifications/<id>`, `createdBy` = admin UID), bildirim görseli
(`notificationImages/<id>.jpg`). Bunlar geliştirici ekibinin ürettiği içeriktir, son kullanıcı verisi değildir; ayrıca
beyan edilmesi önerilmez. Admin UID'si zaten "Kullanıcı kimlikleri"nde var.

### Google Maps / Places SDK (admin harita seçicisi)

Seçici yalnız **live** derlemede ve yalnız `admin: true` hesabı "Bildirim Gönder"de konum eklerken açılır. SDK'lar
uygulama açılışında değil, seçici ilk açıldığında başlar; normal kullanıcıda ve mock'ta hiç başlamaz (`MAP-01`).
Google'ın yayımladığı beyanlar (iki sayfa da 28 Ekim 2026'da güncellenmiş; Google beyanın SDK'nın son sürümü için
geçerli olduğunu, formun sorumluluğunun geliştiricide olduğunu yazıyor):

| SDK | Her zaman toplanan | Kullanıma göre toplanan | Bizim kullanımımızda |
|---|---|---|---|
| [Maps SDK for Android][maps] | İstek üst verisi (cihaz işletim sistemi, model, marka, form faktörü; SDK sürümü; dahili kullanım kimliği), kilitlenme yığınları ve ölçümleri, IP adresi, Maps SDK'ya özgü takma kimlik | Harita etkileşimleri (kaydırma, yakınlaştırma; kamera API'leri kullanılınca) | Hepsi var; kamera API'si kullanılıyor (`MAP-03`) |
| [Places SDK for Android][places] | İstek üst verisi, kilitlenme yığınları ve ölçümleri, IP adresi, Google Maps Platform kimliği | Ülke kodu ve/veya enlem-boylam sınırları (uygulama ayarlarsa); cihazın bulunduğu yerdeki mekânları bulma özelliğinde koordinat + Wi-Fi erişim noktaları; hazır Autocomplete bileşeninde etkileşim; Places API giriş parametreleri (arama metni) | `regionCode TR` + görünür harita alanından dairesel bias **var**; arama metni **var**; "bulunduğun yer" özelliği **yok**; hazır bileşen **yok** (arayüz bizim) |
| `play-services-location` (cihaz konumu) | Google ayrı bir beyan sayfası yayımlamıyor (30 Ekim'de arandı; [Google Play services beyanı][gms] yalnız base/basement/tasks/oss-licenses'ı kapsıyor ve onlar veri toplamıyor) | — | `getCurrentLocation` bir kez; sonuç yalnız haritayı odaklar, cihazda kalır (`MAP-02`) |

Google iki SDK için de verinin HTTPS ile şifrelendiğini, Google hizmetleri dışında üçüncü taraflara aktarılmadığını ve
Google hesabıyla ilişkilendirilmediğini yazıyor. [Google Maps Platform şartları][gmpterms] (§4.4) Google'ın arama
metnini, IP adresini ve enlem-boylam koordinatlarını alıp kendi ürünlerini geliştirmek için kullanabileceğini de söylüyor.
Play, SDK'ların cihazdan çıkardığı veriyi, hangi sunucuya gittiğinden bağımsız olarak "toplama" sayıyor ([Data
safety][ds]).

**Play türlerine eşleme (öneri, K2):**

| SDK verisi | Play türü | Formda var mı? | Öneri |
|---|---|---|---|
| Kilitlenme yığınları ve ölçümleri | Uygulama bilgileri ve performans → Kilitlenme günlükleri | Hayır | Ekle: toplanıyor, paylaşılmıyor, isteğe bağlı, Analiz |
| İstek üst verisi (cihaz modeli, SDK sürümü, sonuç sayısı) | Uygulama bilgileri ve performans → Tanılama | Var (giriş tanılaması) | Amaçlara Analiz eklenir |
| Maps SDK kimliği, Google Maps Platform kimliği | Cihaz veya diğer kimlikler | Var | Değişmez; not olarak eklenir |
| Harita etkileşimleri | Uygulama etkileşimleri | Var | Değişmez |
| Places arama metni | Uygulama etkinliği → Uygulama içi arama geçmişi | Hayır | Ekle: toplanıyor, paylaşılmıyor, isteğe bağlı, Uygulama işlevi |
| IP adresi | Play'de ayrı tür yok; yalnız konum çıkarmak için kullanılırsa konum sayılır | — | Google kullanım ölçümü için aldığını yazıyor → ayrı beyan yok |
| Enlem-boylam sınırları (bias) | Konum | Hayır | Beyan yok: bias yalnız admin'in yerleştirdiği alanı taşır, cihaz konumunu değil (K1 uygulandı) |

"İsteğe bağlı" gerekçesi: normal kullanıcıdan hiç toplanmaz; admin de bildirime konum eklemeden gönderebilir. iOS
etiketinde Maps SDK'nın kilitlenme ve performans verisi beyanlı, arama metni beyansız.

### Konum

**Ne oluyor** (K1-A uygulandıktan sonraki hâli; Hat E `7c74b21`):

1. **Cihaz konumu** yalnız admin seçiciyi açınca, ön planda, bir kez ve yalnız **yaklaşık** olarak
   (`ACCESS_COARSE_LOCATION`) sorulur. Okunan değer yalnız haritayı semt düzeyinde odaklar; bizim kodumuz onu saklamaz,
   loglamaz, sunucuya göndermez. Bu, cihazda işleme sayılır ve beyan gerekmez.
2. **Places araması** görünür harita alanını (0–50 km) bias olarak Google'a gönderir. Google'ın Places sayfası
   "enlem-boylam sınırları"nı toplanan veri olarak sayıyor. Ancak bias yalnız admin haritayı **kendisi**
   yerleştirdikten sonra gider: sürükleme, seçilen öneri, POI dokunuşu ya da mevcut konumu düzenleme. Harita cihaz
   konumuna otomatik odaklıyken arama yalnız `regionCode TR` taşır. Böylece cihaz konumu dolaylı yoldan da cihazdan
   çıkmaz. Admin'in yerleştirdiği alan, kişinin fiziksel konumu değil, aradığı bölgedir.
3. **Seçilen yer** (koordinat, ad, adres, place id) bildirimin içeriğidir, bir işletmenin ya da yerin konumudur. Sunucuya
   ve alıcılara gider. Cihaz konumu değildir; admin kendi bulunduğu noktayı bilerek seçerse bu, kullanıcının başlattığı
   bir içeriktir.
4. **Alıcı tarafı:** "Konuma Git" bildirimin koordinatını Google Haritalar'a ya da `geo:`'ya verir. Alıcının konumu
   kullanılmaz.

Play'in tanımları ([Data safety][ds]): yaklaşık konum 3 km² ve üstü alandır ve `ACCESS_COARSE_LOCATION`'ın verdiğidir.
Kesin konum 3 km²'den küçük alandır ve `ACCESS_FINE_LOCATION`'ın verdiğidir. Geçici işlenen veri de forma girilir, yalnız
mağazada gösterilmez. iOS cihaz konumunu beyan etmedi (ürün sahibi kararı, 30 Ekim 2026).

**K1 — ✅ karar: A (ürün sahibi, 30 Ekim 2026; Hat E oturumunda onaylandı).** Hat E `7c74b21` commit'inde uyguladı
(dal `claude/4-1-0-hat-e-map-4ba310`, henüz `v-4.1.0`'da değil). Live manifestte yalnız `ACCESS_COARSE_LOCATION` var.
`ACCESS_FINE_LOCATION` ve Places'ın eklediği `ACCESS_WIFI_STATE` `tools:node="remove"` ile dışarıda; Hat E'ye göre
birleşik live debug/release manifestlerinde yalnız COARSE var. Sonuç: **"Konum" beyan edilmez.** `NRL-03`'te release
AAB'nin izin listesinde FINE ve `ACCESS_WIFI_STATE` olmadığı yeniden kontrol edilir.

**iOS farkı:** iOS otomatik odakta da görünür alanı bias olarak gönderiyor ve App Store etiketinde konum yok. Bu,
iOS tarafında ayrıca değerlendirilmeli; Android'in kapsamı dışında.

Değerlendirilen seçenekler (kayıt için):

| | Yapılacak | Veri güvenliği sonucu | Diğer etki |
|---|---|---|---|
| **A (seçildi)** | Hat E yalnız `ACCESS_COARSE_LOCATION` ister. Bias yalnız admin haritayı kendisi yerleştirdikten sonra gönderilir; otomatik odakta yalnız `regionCode TR` gider | Cihaz konumu hiçbir yolla cihazdan çıkmaz → "Konum" beyan edilmez (iOS'la aynı sonuç, savunulabilir gerekçeyle) | `MAP-02`'de küçük değişiklik; ince konum beyanı gerekmez (NLG-03) |
| B | Yalnız `ACCESS_COARSE_LOCATION`, bias plandaki gibi | "Konum → Yaklaşık konum": toplanıyor, paylaşılmıyor, isteğe bağlı, Uygulama işlevi | Mağaza sayfasında "Konum" görünür (yalnız admin'den toplansa da) |
| C | Plandaki gibi `COARSE` + `FINE`, bias plandaki gibi | "Konum → Kesin konum" (ve yaklaşık) beyanı | `ACCESS_FINE_LOCATION` için Play Console beyanı gerekir (Kasım 2026'da açılıyor, 27 Ocak 2027'den itibaren zorunlu) |
| D | Plandaki gibi, beyan yok (iOS gibi) | — | Önerilmez: Google'ın kendi sayfası bias'ı toplanan veri sayıyor, geliştirici "tam ve doğru" beyandan sorumlu |

### Hesap silme ve istatistik işaretleri (K3)

Hesap silinince `onAccountDeleted` `users/<uid>` altını (bildirim kutusu, cihazlar, onay, okundu işareti) siler.
Açılma/aksiyon istatistiğinin kişi başı işareti `notifications/<id>/events/<uid>_<olay>` ise UID ile birlikte kalır
(`accountCleanup.js` bu yolu kapsamıyor). Sayaçlar anonimdir, işaretler değildir. Seçenekler:
- **Öneri:** Ortak backend'de hesap silme temizliğine bu işaretlerin silinmesi (ya da UID'nin kaldırılması) eklenir.
  Bu iOS tarafının deposunda ve deploy'u onaylıdır; Android yazmaz.
- Olmazsa hesap silme ve gizlilik sayfaları bunu söyler (NLG-02'deki metin).

Play'deki "kullanıcılar verilerinin silinmesini isteyebilir" cevabı her iki durumda da "Evet" kalır.

### Console formunda 4.1.0 ile değişecekler (`NRL-05` kontrol listesi)

1. Kullanıcı kimlikleri: + Geliştirici iletişimleri, + Reklam veya pazarlama.
2. Diğer bilgiler (kişisel): + Geliştirici iletişimleri, + Reklam veya pazarlama.
3. Cihaz veya diğer kimlikler: + Geliştirici iletişimleri, + Reklam veya pazarlama.
4. Uygulama etkileşimleri: + Uygulama işlevi.
5. Diğer işlemler (yeni): toplanıyor, paylaşılmıyor, geçici değil, isteğe bağlı; Uygulama işlevi + Reklam veya pazarlama.
6. K2'ye göre: Kilitlenme günlükleri (yeni), Tanılama (+ Analiz), Uygulama içi arama geçmişi (yeni).
7. Konum: beyan yok (K1-A uygulandı). Release AAB'de yalnız `ACCESS_COARSE_LOCATION` olduğu `NRL-03`'te doğrulanır.
8. Değişmeyenler: paylaşım yok, aktarımda şifreleme var, silme isteği var, reklam kimliği yok.

**✅ 31 Ekim 2026'da Console'a girildi** (ürün sahibinin onayıyla). 1–8 aynen uygulandı. Ek düzeltme (ürün sahibinin
onayıyla): "Kullanıcı kimlikleri"ne **Analiz** eklendi. Analytics'e UID `setUserId` ile gidiyordu, 4.0.0 beyanında bu
amaç eksikti. Yeni türlerin cevapları:

| Tür | Kısa süreli | Kullanıcının seçimi | Amaç |
|---|---|---|---|
| Uygulama içi arama geçmişi | Hayır | İsteğe bağlı | Uygulama işlevselliği |
| Diğer işlemler | Hayır | İsteğe bağlı | Uygulama işlevselliği, Reklam veya pazarlama |
| Kilitlenme günlükleri | Hayır | İsteğe bağlı | Analiz |

Hepsi "toplanıyor, paylaşılmıyor". Önizleme listeyle birebir uyuştu. Form kaydedildi, Yayın özeti'nde "henüz
incelemeye gönderilmeyen 1 değişiklik" olarak bekliyor; incelemeye `NRL-07`'de üretim sürümüyle birlikte gider.
4.0.0'dan kalan ve dokunulmayan farklar:
- Telefon numarasında "Analiz" var. Fazla beyan zararsız.
- Teşhislerin amacı yalnız "Analiz" ve tür zorunlu işaretli.

### Açık kararlar (ürün sahibi)

- ~~**K1 Konum**~~ ✅ A seçildi ve Hat E'de uygulandı (`7c74b21`); konum beyan edilmez.
- ✅ **K2 Admin harita SDK'sının verileri** (31 Ekim: öneri kabul — üçü de "isteğe bağlı"): Öneri, kilitlenme günlükleri + tanılama + uygulama içi arama geçmişi
  "isteğe bağlı" beyan edilsin. Alternatif: yalnız kilitlenme/tanılama (iOS'la aynı). Alternatif: hiçbiri; gerekçe
  "admin'ler geliştirici ekibi". Bu alternatif riskli, çünkü admin de uygulamanın bir kullanıcısıdır.
- ✅ **K3 İstatistik işaretleri:** backend temizliği (öneri) ya da sayfada açıklama. 31 Ekim: backend temizliği
  seçildi ve aynı gün uygulandı: ortak depoda `ab53aa2` (`onAccountDeleted` işaretleri koleksiyon grubu sorgusuyla siler,
  sayaçlar kalır; `events.uid` index izni), ürün sahibinin onayıyla yayınlandı.
- ✅ **K4 "Geliştirici iletişimleri":** Duyurular için bu amaç eklensin mi? Öneri evet (Play'in tanımına birebir uyuyor).
  31 Ekim: evet.
- Onay kaydı kanıt işlevi de gördüğü için "Dolandırıcılığı önleme, güvenlik ve uyumluluk" amacı da işaretlenebilir;
  zorunlu değil.

## 4.2.0 — Soru soru sınavlar ve "Sınava gir" (SLG-01)

> Kaynaklar: yol haritası [`ANDROID-4.2.0-ROADMAP.md`](ANDROID-4.2.0-ROADMAP.md) (§1, Hat C, Hat F); iOS `v-4.2.0`
> sözleşme belgesi `STRUCTURED-EXAMS.md` §6, §9, §14 ve sunucu kodu `firebase/functions/conversions/schema.js`
> (PRACTICE MODE bloğu), `conversions/practice.js`, `accountCleanup.js` (salt okundu); ortak hosting'deki gizlilik,
> hesap silme ve kullanım koşulları sayfalarının 21 Kasım 2026 sürümü (`5e39574`); [Data safety][ds] yardım sayfası
> (21 Kasım 2026'da okundu). Bu bölüm öneridir; form `SRL-05`'te ürün sahibinin onayıyla doldurulur. Android'in bu
> sürümdeki kodu henüz yazılmadı (Faz 1); aşağıdaki "Android ne gönderir" satırları yol haritasındaki tasarımdır,
> `SRL-05`'ten önce koda karşı yeniden kontrol edilir.

### Yeni veri akışları

| Veri | Nerede / nasıl | Android ne gönderir | Ne zaman silinir | Play türü | Formda var mı? |
|---|---|---|---|---|---|
| "Sınava gir" günlük sayacı | Sunucu `users/<uid>/practice/daily` = `{day, count, seenToday (günün son ≤ 50 soru kimliği), updatedAt}`. Yalnız sunucu yazar (`nextPracticeQuestions`), sahibi okur. Ücretsiz kullanıcıda her verilen yeni soruda artar, İstanbul gününde sıfırlanır. Premium'da sayaç tutulmaz | Çağrı parametreleri: ders (`subjectId`), sınav türü, kapsam, ders/üniversite kimliği, oturumda görülen ≤ 300 soru kimliği, istenen sayı. Hepsi arşiv kimliği; serbest metin yok | Hesap silinince (`users/<uid>` ile birlikte; hesap silme sayfası "en geç 30 gün") | Uygulama etkinliği → **Uygulama etkileşimleri** (öneri; gerekçe aşağıda) | Var; amaçlarda "Uygulama işlevi" 4.1.0'dan beri var |
| Premium kanıtı (abonelik doğrulaması) | Her pratik çağrısında `subscriptionProof = {platform: "android", purchaseToken, productId}`. Sunucu Play Developer API ile doğrular (`SRV-01`), sonucu `subscriptions/<uid>` = `{platform, productId, originalTransactionId (Android'de Play sipariş kimliği), expiresAt, verifiedAt}` olarak en çok 24 saat hatırlar | Satın alma jetonu + ürün kimliği (`PRC-03`); jeton günlüğe yazılmaz | Hesap silinince (`accountCleanup.js`; sayfa "en geç 30 gün") | Finansal bilgiler → **Satın alma geçmişi** | Var (4.0.0); kapsam genişler |
| Abonelik–hesap bağı | `subscriptionOwners/android-<sipariş kimliği>` = `{uid, platform, originalTransactionId, boundAt}`. Yalnız sunucu; istemci erişemez. Bir abonelik ilk doğrulandığı hesaba bağlanır, başka hesapta ücretsiz kurallar uygulanır | — (sunucu kanıttan üretir) | Hesap silinince bağ silinir, abonelik yeni hesapta kullanılabilir | Finansal bilgiler → **Satın alma geçmişi** | Var; amaç eklenir (S1 kararı: evet) |
| Yeni analytics olayları | `answer_revealed` (`question_order`), `practice_started` (`exam_type`, `scope`), `practice_answer_revealed`, `practice_limit_reached`, `practice_paywall_opened`; `exam_open`'a `kind: structured` + `question_count`. Yeni ekran adları `StructuredExam`, `PracticeSetup`, `PracticeSession` (iOS ile aynı) | Düşük kardinaliteli sabitler: soru sırası, sınav türü slug'ı, kapsam (`course`/`university`/`tr`), soru sayısı. Ders adı, soru metni, serbest metin yok | Analytics saklama süresi (en çok 14 ay) | Uygulama etkinliği → **Uygulama etkileşimleri** | Var ("Analiz" amacıyla, 3.1.0'dan beri) |
| Soru ve şekil okuma | `questions` (yayınlanmış sorular) ve Storage `questionFigures/…` okunur; cevap aç/kapa durumu cihazda (`rememberSaveable`), Firestore'a yazılmaz | Yalnız okuma isteği (kimlik doğrulamalı, App Check'li) | — | — (kullanıcı verisi değil) | — |
| Son görüntülenen kaydına `isStructured` | Cihazda (`recentExams.v1`) | Hiçbir şey; cihazda kalır | Uygulama verisi silinince / hesap silinince `purge` | — (cihazda kalır, toplama sayılmaz) | — |

**Sayaç neden "Uygulama etkileşimleri":** Play bu türü uygulamayla nasıl etkileşildiği bilgisi olarak tanımlıyor
(sayfa ziyaretleri, dokunulan bölümler); "Diğer işlemler" ise başka yerde sayılmayan uygulama içi etkinlik (oyun
ilerlemesi, beğeniler). Sayaç "bugün kaç pratik sorusu görüldü" bilgisidir; ikisine de sığar. "Uygulama etkileşimleri"
önerilir, çünkü tür zaten beyanlı ve 4.1.0'dan beri "Uygulama işlevi" amacını taşıyor; yeni bir satır açmak gerekmez.
"Diğer işlemler" seçilirse de yeni satır gerekmez (4.1.0'da açıldı, "Uygulama işlevi" var). Seçim formun sonucunu
değiştirmez; karar S2: "Uygulama etkileşimleri" (21 Kasım 2026).

**Abonelik neden "Satın alma geçmişi":** Play'in tanımı "kullanıcının yaptığı satın alma ya da işlemler hakkında
bilgi". Ürün kimliği, sipariş kimliği ve bitiş tarihi bunun içinde. Ödeme bilgisi (kart, banka) yine Google Play'de
kalır → "Ödeme bilgileri" beyan edilmez. Bu satır bugün "isteğe bağlı" (yalnız satın alan); 4.2.0'da da yalnız
Premium kullanıcısı "Sınava gir"i açınca toplanır → "isteğe bağlı" kalır.

### Vertex AI'da sınav sayfalarının işlenmesi

**Ne oluyor:** Sınav sayfaları (görsel/PDF) sunucuda Google Cloud **Vertex AI** üzerinden yapay zekâ modellerine
(Anthropic Claude ve Google Gemini) gönderilir; modeller soruları metne çevirir ve çözer, sonuç admin incelemesinden
sonra yayınlanır. İş yalnız admin eylemiyle başlar: gönderimin 1. onayı, "Yeni yapıya geçir" ya da toplu dönüştürme.
İstemci (Android dahil) Vertex AI'ya hiçbir şey göndermez; telefon, ad ya da hesap bilgisi modele gitmez (gizlilik
sayfası "Yapay zekâ ile soru hazırlama" bölümü). Sayfalar iki kaynaktan gelir:

1. **Arşivde zaten yayında olan sınavlar** ("Yeni yapıya geçir", toplu dönüştürme): uygulamanın kendi içeriği.
   Yayından önce kullanıcıdan alınmış olsalar da artık herkese açık arşivin parçası; yeni bir toplama yok.
2. **Kullanıcının gönderdiği sınav dosyaları** (1. onayla dönüştürme başlar): bunlar bugün formda beyanlı
   (aşağıda). Yeni olan, aynı verinin bir işleyicide yeni bir işlemden geçmesi.

**Play'e göre "paylaşım" mı?** Hayır (öneri). Gerekçe:
- Play paylaşımı "toplanan kullanıcı verisinin üçüncü tarafa aktarılması" olarak tanımlıyor ve **hizmet
  sağlayıcıya** aktarımı istisna sayıyor: geliştirici adına, talimatıyla işleyen taraf. "Üçüncü taraf", birinci taraf
  ve hizmet sağlayıcıları dışındaki herkes ([Data safety][ds]).
- Vertex AI, Google Cloud'un kurumsal hizmeti; işlem Google Cloud koşulları ve Veri İşleme Eki'ne göre yapılıyor.
  Google'ın Vertex AI veri yönetişimi sayfası, müşteri verisinin izin ya da talimat olmadan model eğitiminde
  kullanılmayacağını yazıyor ([Vertex AI veri yönetişimi][vxgov]). Yani veri bizim talimatımızla, bizim amacımız
  için (soruları hazırlamak) işleniyor: hizmet sağlayıcı tanımına uyuyor.
- Kullanım koşulları gönderen kişinin bu işleme izin verdiğini de yazıyor ("sorularının yapay zekâ ile metne çevrilip
  çözülmesi"); ancak "paylaşım değil" sonucu buna değil, hizmet sağlayıcı istisnasına dayanır.
- Firebase için bugün kullanılan gerekçenin aynısı ("Google hizmet sağlayıcı olarak işler; paylaşım sayılmaz").

**Formda hangi tür?** Bu belgenin envanteri gönderimleri **"Uygulama etkinliği → Diğer kullanıcı içeriği"** olarak
listeliyor (amaç Uygulama işlevi, isteğe bağlı). Play'in tanımına göre kullanıcının fotoğrafları "Fotoğraflar",
dosyaları "Dosyalar ve dokümanlar" türüne de sığabilir. Console'da hangisinin işaretli olduğu bu depodan
görülemiyor; ürün sahibi "Diğer kullanıcı içeriği" olduğunu teyit etti (S3, 21 Kasım 2026). Hangisi olursa olsun 4.2.0 yeni bir tür ya da amaç gerektirmez: veri zaten
"toplanıyor, Uygulama işlevi"; soruları hazırlamak arşive ekleme işlevinin parçası. Play formunda "üçüncü taraf
işleyici notu" diye bir alan yoktur; işleyici bilgisi gizlilik politikasında yer alır ve 21 Kasım sürümü bunu
söylüyor ("Sınav sayfaları, soruların hazırlanması için Google Cloud Vertex AI hizmetinde işlenir").

**Dikkat:** Sınav kâğıdında yazılı ad ya da öğrenci numarası görüntüde kalmış olabilir (koşullar bunları göndermeyi
yasaklıyor; gizlilik sayfası olasılığı söylüyor). Bu veri ayrı bir "Ad" toplaması sayılmaz: uygulama onu alan olarak
istemiyor, çıkarmıyor, saklamıyor; görüntünün içeriğidir ve görüntü zaten beyanlı.

### Diğer 4.2.0 değişiklikleri (beyan etkisi yok)

- **Moderasyonda gönderen kartı:** Admin'e gönderenin adı soyadı, üniversitesi, fakültesi, bölümü gösterilir (telefon
  yok). Admin'ler geliştirici ekibidir; ekip içi kullanım paylaşım değildir. "Ad" ve "Diğer bilgiler" satırlarında
  "Uygulama işlevi" zaten var. Gizlilik sayfası 21 Kasım'da bunu ekledi.
- **Admin dönüştürme ekranları** (`DDEC-01` tam parite): taslak sorular, model seçimi, maliyet göstergesi, ders eşleme.
  Geliştirici içeriği; son kullanıcı verisi değil.
- **"Bize Ulaş" e-postası:** Kullanıcının kendi başlattığı e-posta; içerik uygulamaya değil, e-posta sağlayıcısına
  gider. Değişmez.
- **Yeni bağımlılık:** LaTeX çizim kütüphanesi (`DDEC-02`, `SPK-01`) yerel çizim yapar, ağ erişimi beklenmez.
  `SPK-01` kütüphanenin veri toplamadığını (ağ izni, analitik) doğrulamalı; topluyorsa bu tablo yeniden açılır.
- **reCAPTCHA SDK'sının açık yazılması** (`RCD-01`, 18.5.1): sürüm aynı, 18 Kasım beyanı geçerli.

### Hesap silme

Hesap silinince sunucu (`onAccountDeleted` → `accountCleanup.js`) günlük sayacı (`users/<uid>/practice/*`),
`subscriptions/<uid>` kaydını ve hesabın `subscriptionOwners` bağlarını da siler; abonelik yeni bir hesapta
kullanılabilir. Hesap silme sayfası 21 Kasım'da bunu ekledi ("en geç 30 gün"). Play'deki "kullanıcılar verilerinin
silinmesini isteyebilir" cevabı "Evet" kalır.

### Forma etkisi (`SRL-05` kontrol listesi)

| Form satırı | 4.2.0 etkisi | Neden |
|---|---|---|
| Finansal bilgiler → Satın alma geçmişi | **Değişir (kapsam):** abonelik kanıtı ve hesap bağı eklenir. Toplanıyor, paylaşılmıyor, kısa süreli değil, isteğe bağlı, Uygulama işlevi aynı kalır. Amaçlara **"Dolandırıcılığı önleme, güvenlik ve uyumluluk"** eklenir (S1 kararı: evet, `SRL-05`'te Console'a) | Bir aboneliğin tek hesapta kullanılması kötüye kullanımı önler |
| Uygulama etkinliği → Uygulama etkileşimleri | Değişmez | Yeni olaylar ve sayaç bu türde; Analiz + Uygulama işlevi zaten var |
| Uygulama etkinliği → Diğer işlemler | Değişmez | (Sayaç buraya konursa da Uygulama işlevi var) |
| Gönderilen sınav dosyaları (Console'da "Diğer kullanıcı içeriği", S3) | Değişmez | Vertex AI hizmet sağlayıcı; paylaşım yok; amaç aynı |
| Ad, Diğer bilgiler (kişisel) | Değişmez | Gönderen kartı ekip içi, Uygulama işlevi var |
| Kullanıcı kimlikleri | Değişmez | Sayaç ve bağ UID'ye bağlı; Uygulama işlevi var |
| Cihaz veya diğer kimlikler, Tanılama, Kilitlenme günlükleri, Uygulama içi arama geçmişi, Yüklü uygulamalar, Telefon numarası | Değişmez | 4.2.0'da yeni kullanım yok |
| Paylaşım | Değişmez ("paylaşılmıyor") | Vertex AI (Google Cloud) hizmet sağlayıcı |
| Aktarımda şifreleme, silme isteği, reklam kimliği | Değişmez | — |
| Konum | Değişmez (beyan yok) | — |

Sonuç: tek form değişikliği, "Satın alma geçmişi" satırına "Dolandırıcılığı önleme, güvenlik ve uyumluluk" amacını
eklemektir (S1 kararı, 21 Kasım 2026); diğer cevaplar aynı kalır. Analytics envanterindeki olay sayısı 29'dan 34'e, ekran adı 18'den 21'e
çıkar (yukarıdaki "Analytics envanteri" bölümü 4.2.0 kodu yazılınca `SLG-04` ile güncellenir).

### Açık noktalar / ürün sahibine sorular

- **S1 — Satın alma geçmişine amaç eklenmesi:** Abonelik–hesap bağı kötüye kullanımı önlemek içindir. "Dolandırıcılığı
  önleme, güvenlik ve uyumluluk" amacı eklensin mi? **Karar (21 Kasım 2026): Evet.** Amaç `SRL-05`'te Console'da
  "Satın alma geçmişi" satırına eklenir.
- **S2 — Sayacın türü:** "Uygulama etkileşimleri" (öneri) mi, "Diğer işlemler" mi? **Karar (21 Kasım 2026):** Öneri
  yönünde, "Uygulama etkileşimleri".
- **S3 — Gönderimlerin Console'daki türü:** Formda gönderilen sınav dosyaları hangi türde işaretli? **Karar (21 Kasım
  2026):** Console'da "Diğer kullanıcı içeriği" olarak işaretli; belgeyle aynı, hizalama gerekmez.
- **S4 — Claude'un Vertex AI üzerinden işlenmesi:** **Karar (21 Kasım 2026): Teyit edildi.** Claude, Google Cloud
  Vertex AI üzerinden, Google Cloud koşulları ve Veri İşleme Eki kapsamında kullanılıyor; "hizmet sağlayıcı, paylaşım
  değil" gerekçesi geçerli.
- **S5 — Android kodunun doğrulanması:** **Doğrulandı (21 Kasım 2026, `v-4.2.0` `e0e4f38`); tabloda düzeltme
  gerekmedi.** Bulgular:
  - (a) *Jeton günlüğe yazılmıyor.* `core/data/billing` ve `core/data/practice` altında `Timber`/`Log` çağrısı yok
    (`grep` boş); uygulamanın tamamında jeton/kanıt içeren tek günlük satırı yok (`FirebaseNotificationService.kt:62`
    yalnız FCM jetonu hatasını yazar, istisnayla). `SubscriptionProof.toString()` jetonu `…` ile gizliyor
    (`core/model/Practice.kt:130`); kanıt yalnız `PracticeCalls.kt:32` içinde `payload()` ile çağrı gövdesine girer.
    Not: `PlayPurchase` (`core/data/billing/PlayBillingClient.kt:70`) bir `data class` olduğundan otomatik `toString`'i
    jetonu içerir; ancak hiçbir yerde günlüğe ya da metne yazılmıyor (`$purchase`/`toString` kullanımı yok). Bugün
    sorun değil; ileride günlüğe yazılırsa jeton sızar.
  - (b) *Analytics adları ve parametreleri tabloyla birebir* (`core/model/AnalyticsEventMapping.kt`): `exam_open` +
    `kind: structured` + `question_count` (satır 12, 51–54, 170); `answer_revealed` + `question_order` (13, 55);
    `practice_started` + `exam_type`, `scope` (39, 82); `practice_answer_revealed` (40), `practice_limit_reached`
    (41), `practice_paywall_opened` (42); bu üçünün parametresi yok (satır 91–93). Ekran adları `PracticeSetup`,
    `PracticeSession`, `StructuredExam` (143, 146, 149). Ders adı, soru metni ya da serbest metin parametresi yok.
  - (c) *AndroidMath veri toplamıyor.* `AndroidMath-v1.1.0.aar` (Gradle önbelleği) açıldı: yalnız 96 sınıf, 3 yazı
    tipi, `libmain.so`/`libc++_shared.so`; `classes.jar` içinde `java/net`, `HttpURLConnection`, `okhttp`, `firebase`,
    `analytics`, `android/net` ya da herhangi bir `http` dizesi yok; yerel kitaplıkta ağ/soket dizesi yok; POM
    bağımlılıkları yalnız Kotlin stdlib, appcompat, annotation (test: junit, runner, rules). Kütüphane manifesti
    `WRITE/READ_EXTERNAL_STORAGE` istiyor, bizim `app/src/main/AndroidManifest.xml` bunları `tools:node="remove"` ile
    kaldırıyor (satır 15–20); birleşik manifestte depolama izni kalmaz.
- **S6 — Abonelikte `obfuscatedAccountId`:** **Karar (21 Kasım 2026):** Abonelik satın alımına `obfuscatedAccountId`
  **eklenmedi** (sunucu bunu ret nedeni saymıyor; iOS'ta da yok). Google'a UID özeti gitmiyor; beyan değişmez.

[ds]: https://support.google.com/googleplay/android-developer/answer/10787469
[maps]: https://developers.google.com/maps/documentation/android-sdk/play-data-disclosure
[places]: https://developers.google.com/maps/documentation/places/android-sdk/play-data-disclosure
[gms]: https://developers.google.com/android/guides/play-data-disclosure
[gmpterms]: https://cloud.google.com/maps-platform/terms
[vxgov]: https://docs.cloud.google.com/vertex-ai/generative-ai/docs/data-governance
