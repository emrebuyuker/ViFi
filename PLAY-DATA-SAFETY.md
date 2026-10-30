# Play Console — Veri Güvenliği (Data Safety) Envanteri

> **Durum:** Taslak envanter (FM-20). Formu Play Console'da doldurup göndermek ürün sahibinin işidir.
> Kaynak: iOS `ViFi/Resources/PrivacyInfo.xcprivacy` (gizlilik etiketi) + Android hedef mimarisi (`core/model`).
> Özellikler eklendikçe güncellenir; aşağıdaki "Android'e özgü" satırları Play'e özeldir.
> **4.0.0 (LEG-02):** ViFi Premium ve parayla tek sınav açma ile "Finansal bilgiler → Satın alma geçmişi" eklendi
> (iOS gizlilik manifestindeki "satın alma geçmişi"nin karşılığı). Console formu 4.0.0 sürümüyle birlikte, ürün
> sahibince güncellenir; o zamana kadar canlıdaki 3.1.0 beyanı geçerlidir.
> **4.1.0 (NLG-01, 30 Ekim 2026, ürün sahibinin onayını bekliyor):** Hedefli bildirim sistemi için tabloya "Geliştirici
> iletişimleri" ve "Reklam veya pazarlama" amaçları, kampanya onayı kaydı, okundu işareti, açılma/aksiyon istatistiği ve
> kurulum kimliği eklendi. _(4.1.0)_ işaretli hücreler yenidir. Admin harita seçicisinin (Google Maps + Places SDK) ve
> konumun etkisi §4.1.0'dadır; oradaki açık kararlar (K1…K4) verilmeden form doldurulmaz. Console formu 4.1.0 sürümüyle
> girilir (`NRL-05`); o zamana kadar yayındaki sürümün beyanı geçerlidir.

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

Admin harita seçicisinden gelebilecek satırlar (kilitlenme günlükleri, tanılama, uygulama içi arama geçmişi, konum)
karara bağlıdır; bkz. §4.1.0 → K1, K2.

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
  ⚠️ Açılma/aksiyon istatistiğinin kişi başı işaretleri (`notifications/<id>/events/<uid>_<olay>`) silinmiyor (K3).
  4.0.0 (ürün sahibi kararı, 2026-10-29): `purchasedTransactions` kayıtları tekrar kullanımı önlemek için hesap
  silinince de saklanır, hesapla bağı (UID) en geç 30 gün içinde elle kaldırılır; hesap silme ve gizlilik sayfaları
  bunu söyler (LEG-01).
- **Coin bakiyesi / açılmış sınavlar:** Uygulama içi durum; coin parayla satın alınmaz, finansal veri sayılmaz.
- **Satın almalar (4.0.0):**
  - *ViFi Premium aboneliği* cihazda Google Play Billing ile doğrulanır; sunucuya yazılmaz, bizim tarafımızda
    toplanmaz (iOS ile aynı, `DEC-03`).
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
| Enlem-boylam sınırları (bias) | Konum | Hayır | K1'e bağlı |

"İsteğe bağlı" gerekçesi: normal kullanıcıdan hiç toplanmaz; admin de bildirime konum eklemeden gönderebilir. iOS
etiketinde Maps SDK'nın kilitlenme ve performans verisi beyanlı, arama metni beyansız.

### Konum

**Ne oluyor** (`MAP-01`/`MAP-02` planı; iOS'la aynı davranış):

1. **Cihaz konumu** yalnız admin seçiciyi açınca, ön planda, bir kez sorulur. Okunan değer yalnız haritayı odaklar;
   bizim kodumuz onu saklamaz, loglamaz, sunucuya göndermez. Tek başına bu, cihazda işleme sayılır ve beyan gerekmez.
2. **Ama Places araması** görünür harita alanını (0–50 km) bias olarak Google'a gönderir. Harita cihaz konumuna
   otomatik odaklandıysa ve admin onu henüz kaydırmadıysa bu alanın merkezi cihaz konumudur. Böylece cihaz konumu
   dolaylı olarak cihazdan çıkar. Google'ın Places sayfası "enlem-boylam sınırları"nı toplanan veri olarak sayıyor.
3. **Seçilen yer** (koordinat, ad, adres, place id) bildirimin içeriğidir, bir işletmenin ya da yerin konumudur. Sunucuya
   ve alıcılara gider. Cihaz konumu değildir; admin kendi bulunduğu noktayı bilerek seçerse bu, kullanıcının başlattığı
   bir içeriktir.
4. **Alıcı tarafı:** "Konuma Git" bildirimin koordinatını Google Haritalar'a ya da `geo:`'ya verir. Alıcının konumu
   kullanılmaz.

Play'in tanımları ([Data safety][ds]): yaklaşık konum 3 km² ve üstü alandır ve `ACCESS_COARSE_LOCATION`'ın verdiğidir.
Kesin konum 3 km²'den küçük alandır ve `ACCESS_FINE_LOCATION`'ın verdiğidir. Geçici işlenen veri de forma girilir, yalnız
mağazada gösterilmez. iOS cihaz konumunu beyan etmedi (ürün sahibi kararı, 30 Ekim 2026); bias o kararda ayrıca ele
alınmamış görünüyor.

**K1 — seçenekler:**

| | Yapılacak | Veri güvenliği sonucu | Diğer etki |
|---|---|---|---|
| **A (öneri)** | Hat E yalnız `ACCESS_COARSE_LOCATION` ister. Bias yalnız admin haritayı kendisi yerleştirdikten sonra gönderilir; otomatik odakta yalnız `regionCode TR` gider | Cihaz konumu hiçbir yolla cihazdan çıkmaz → "Konum" beyan edilmez (iOS'la aynı sonuç, savunulabilir gerekçeyle) | `MAP-02`'de küçük değişiklik; ince konum beyanı gerekmez (NLG-03) |
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
7. K1'e göre: Konum yok (A) ya da Yaklaşık/Kesin konum.
8. Değişmeyenler: paylaşım yok, aktarımda şifreleme var, silme isteği var, reklam kimliği yok.

### Açık kararlar (ürün sahibi)

- **K1 Konum:** A / B / C / D (yukarıda). Öneri A; Hat E'nin `MAP-01`/`MAP-02`'sine dokunur.
- **K2 Admin harita SDK'sının verileri:** Öneri, kilitlenme günlükleri + tanılama + uygulama içi arama geçmişi
  "isteğe bağlı" beyan edilsin. Alternatif: yalnız kilitlenme/tanılama (iOS'la aynı). Alternatif: hiçbiri; gerekçe
  "admin'ler geliştirici ekibi". Bu alternatif riskli, çünkü admin de uygulamanın bir kullanıcısıdır.
- **K3 İstatistik işaretleri:** backend temizliği (öneri) ya da sayfada açıklama.
- **K4 "Geliştirici iletişimleri":** Duyurular için bu amaç eklensin mi? Öneri evet (Play'in tanımına birebir uyuyor).
- Onay kaydı kanıt işlevi de gördüğü için "Dolandırıcılığı önleme, güvenlik ve uyumluluk" amacı da işaretlenebilir;
  zorunlu değil.

[ds]: https://support.google.com/googleplay/android-developer/answer/10787469
[maps]: https://developers.google.com/maps/documentation/android-sdk/play-data-disclosure
[places]: https://developers.google.com/maps/documentation/places/android-sdk/play-data-disclosure
[gms]: https://developers.google.com/android/guides/play-data-disclosure
[gmpterms]: https://cloud.google.com/maps-platform/terms
