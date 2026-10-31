# ViFi Android — 4.1.0 Versiyonunda Yapılacak İşler

> **Amaç:** `ViFi-iOS` **`v-4.1.0`** dalındaki **hedefli bildirim sistemini** Android'de birebir hayata geçirmek. Dalda 27
> commit var (`36ce982`…`6e44c29`, 29–30 Ekim 2026). Sistemin parçaları: bildirimleri sunucu gönderir; tek bildirim
> sayfası; görsel ve aksiyon; izin + kampanya onayı; Hesap › Bildirim Ayarları; admin "Bildirim Gönder", geçmiş ve
> harita seçicisi. 4.0.0'ın kalan yayın adımları [ANDROID-4.0.0-ROADMAP.md](ANDROID-4.0.0-ROADMAP.md)'dedir
> (`RLS-09`…`RLS-11`). Bu dosya 4.1.0'ın **tek** iş listesidir.
>
> **Backend hazır.** Firestore/Storage kuralları, indeksler ve 13 Cloud Function 30 Ekim 2026'dan beri canlıda (iOS
> tarafında, onaylı yayın). Sözleşme platformdan bağımsız. **Android için yeni backend kodu ya da deploy gerekmez.**
> Android okur, kendi cihaz/tercih kayıtlarını yazar ve callable'ları çağırır.
>
> **Dal:** `v-4.1.0`; bugün 4.0.0 (versionCode 19) ile aynı kod. **Yayın:** Faz 0–3 canlıya göndermez. Yayın Faz 4'te,
> ürün sahibiyle yapılır; her dış adım ayrı onay ister.
>
> **Ayrıntı eki:** [ANDROID-4.1.0-DETAIL.md](ANDROID-4.1.0-DETAIL.md) iOS kaynaklarından çıkarılmış tam olgu
> listelerini içerir: metinler, erişilebilirlik kimlikleri, durum makineleri, hata eşlemeleri ve **iOS test adları**
> (= Android kabul testleri). Şüphede iOS kodu (`ViFi-iOS`, `v-4.1.0`) ve `firebase/functions/notifications/schema.js`
> esastır.

---

## 0. Durum Özeti

> **İlerleme (31 Ekim 2026):** Toplam 50 kalemin **39'u tamam, 0'ı yarım, 11'i başlanmadı.** Faz 0'ın dört kararı
> (`NDEC-01`…`04`) ürün sahibince verildi. Canlı durum salt okumayla denetlendi (`NOPS-02`): function'lar, kurallar,
> indeksler ve App Check beklendiği gibi; 3 eski Android cihaz kaydı var. Sözleşme (`NCT-01`) tamam; senkron noktası
> 1 açıldı, beş kod hattı (A…E) ve F paralel başlayabilir. Hat A (push altyapısı ve cihaz kaydı, `PSH-01`…`04`)
> tamam; Hat B (bildirim sayfası, `INB-01`…`05`) tamam; Hat C (`PRM-01`…`05`) tamam. Hat C'nin kalanı, D
> (`ADM-01`…`06`), E (`MAP-01`…`04`) ve F (`NLG-01`…`04`) 30 Ekim'de ayrı oturumlarda paralel yapıldı ve `v-4.1.0`'a
> birleştirildi; senkron noktası 2 açıldı. `NIT-01` (canlı bağlama) 31 Ekim'de tamamlandı; `NIT-02` (birim testleri),
> `NIT-03` (UI testleri, izin akışı API 32 ve 37 emülatörde) ve `NIT-04` (belgeler) aynı gün paralel yapılıp
> birleştirildi; **Faz 2 tamam.** Birleşik dalda: `assembleDebug` başarılı, `lintLiveDebug` 0 hata / 56 uyarı, birim
> testleri liveDebug 1100 / mockDebug 1084 / liveRelease 1100, tablet emülatöründe (API 37) mock 227 (226 geçti, 1
> atlandı) ve live 191 (188 geçti, 3 atlandı; atlananlar başka API düzeyine ya da Haritalar'ın kurulu olmamasına bağlı),
> başarısız test yok. Maps anahtarı (`NOPS-01`) 31 Ekim'de oluşturuldu; senkron noktası 3 açıldı. **Sıradaki iş
> `NQA-01`** (Faz 3, canlı debug ile uçtan uca; her canlı yazım onayla). Hatların birleştirilmesinde yeni UI testleri tablet
> emülatöründe koştu: mock 81/81, live 78/78. Dört test hatası düzeltildi: belirsiz metin araması, durumu
> güncellemeyen test sahnesi, `waitForIdle` eksiği; uygulama kodunda hata yoktu. Konum kararı (K1) verildi: seçici yalnız yaklaşık
> konum ister, harita bölgesi aramaya ancak admin haritayı kendisi yerleştirince eklenir. Ürün sahibini bekleyenler:
> Hat F'nin yasal/politika kararları (K2–K4, R1–R2, P1–P3;
> `PLAY-POLICY-4.1.0.md`, `PRIVACY-POLICY-4.1.0.md`).

| Faz | Ne | Nasıl ilerler | Kalem |
|---|---|---|---|
| **Faz 0** | Kararlar + Google Cloud anahtarı + canlı durum denetimi | Sıralı, ürün sahibiyle — ⚠️ onay | 6 |
| **Faz 1** | Sözleşme (ilk gün) + geliştirme | `NCT-01` sonra **5 paralel kod hattı** (A push · B bildirim sayfası · C izin/ayarlar · D admin · E harita) + **F belge/beyan** | 29 |
| **Faz 2** | Bağlama + testler | A…E bitince birleşir | 4 |
| **Faz 3** | Gerçek ortam doğrulaması (canlı backend) | Sıralı; her canlı yazım onayla | 2 |
| **Faz 4** | Yayın | Sıralı, ürün sahibiyle — ⚠️ her dış adım ayrı onay | 9 |

---

## 1. iOS'ta Ne Yapıldı

### 1.1 Sunucu (ortak depo `ViFi-iOS/firebase/`, canlıda — Android için değişiklik gerekmez)

- **Tek teslim yolu.** Bütün gönderimler `deliverNotification`'dan geçer. İki teslim biçimi var:
  - **`broadcast`:** (`general` | `system`) + herkes → `announcements/<id>` + FCM topic `all`.
  - **`fanout`:** segment, **tüm kampanyalar**, kişisel, admin ve test → alıcı başına `users/<uid>/inbox/<id>` + kayıtlı
    cihaz token'ları. Push 500'lük paketlerle gider; ölü token'ın cihaz dokümanı silinir.
- **Kampanya onayı.** `promo` yalnız `users/<uid>/settings/notifications.marketing == true` olanlara gider; onay
  vermeyen kampanyayı ne push ne sayfada görür. Konumlu bildirim sunucuda zorla `promo` yapılır.
- **Sessiz saatler.** 22:00–09:00 (İstanbul) arasında `promo` ve otomatik "yeni sınav" 09:00'a ertelenir. Kişisel
  bildirimler, admin'e gidenler ve test anında gider.
- **Function'lar:**
  - Admin callable'ları: `getNotificationAudience`, `previewNotification`, `sendNotification` (300 sn),
    `cancelNotification`.
  - +90 kullanıcı callable'ı: `trackNotificationEvent`.
  - Zamanlayıcı: `dispatchScheduledNotifications` (5 dk). Firestore `vifi` tetikleyicileri: `onDeviceWritten`,
    `onSubmissionModerated`, `onSubmissionCreated`. Auth tetikleyicisi: `onAccountDeleted` (v1).
- **Otomatik bildirimler** (`kind: system`):
  - Onay → sahibine "Sınavın onaylandı" (+coin, "Sınava Git") ve bölümüne "Bölümüne yeni sınav eklendi" (bölüm başına
    günde 1).
  - Ret → "Gönderin reddedildi" (sebeple).
  - Yeni gönderim → adminlere "Yeni sınav gönderimi" (gönderen başına saatte 3).
  - **Android admin onayları da tetikler:** `FirebaseModerationBackend` `publishedExamId`, `rejectionReason` ve
    `ledger/<gönderimId>.amount` alanlarını yazıyor (30 Ekim'de kontrol edildi).
- **Push payload:**
  - `notification{title, body, imageUrl?}`, `data{notificationId, kind}`.
  - Android bloğu: `android{priority: high, notification{channelId: kind, imageUrl?}}`. Kanal kimlikleri tam olarak
    `general`, `promo`, `system`.
  - Görsel, gönderim anında üretilen 7 günlük imzalı URL'dir ve yalnız payload'da durur. Firestore'da yalnız
    `image.storagePath` vardır.
- **Hesap silme temizliği sunucuda yapılır** (`onAccountDeleted`): `users/<uid>` alt ağacı, cüzdan + ledger, abonelik,
  bekleyen gönderimler + `pending/<uid>/` ve RTDB profil silinir.

### 1.2 iOS istemcisi

- **Kullanıcı tarafı:**
  - Bildirim sayfası: `announcements` (son 50) ∪ `users/<uid>/inbox` (son 100); okunmamış =
    `createdAt > settings/inbox.readAt`.
  - Push'a dokunuş → kapılardan sonra sayfa açılır ve bildirim vurgulanır.
  - Görsel ve 5 aksiyon: Konuma Git, Sınava Git (arşivin kilit kuralıyla), Premium'a Göz At, Sınav Ekle, Bağlantıyı Aç.
  - İzin akışı: provisional → 1/2 → 2/2 → bağlamsal tekrar. Hesap › Bildirim Ayarları.
  - Cihaz kaydı, ikon rozeti, açılma/aksiyon istatistiği.
- **Admin tarafı** (Hesap › "Bildirim Gönder" + "Geçmiş"):
  - Tür, başlık/mesaj, kitle (Hepsi / üniversite › fakülte › bölüm + cinsiyet; kişi sayılarıyla), görsel, aksiyon
    (arşivden sınav, haritadan konum dahil), ileri tarih + geçerlilik.
  - Önizleme + onay, "Bana test gönder".
  - Geçmiş: durum, sayılar, zamanlanmışı iptal.
  - Konum seçici: Google Maps + Places; cihaz konumu yalnız haritayı odaklar.
- **iOS'a özgü olup taşınmayanlar:**
  - Notification Service Extension + App Group rozet sayacı (→ `NDEC-04`).
  - Provisional izin (→ `NDEC-01`).
  - Apple Haritalar (→ `NDEC-03`).
  - `PrivacyInfo` manifestleri (→ Play Veri güvenliği, `NLG-01`).

### 1.3 Android'e dokunan sözleşme (Firestore `vifi` DB; kurallar canlı)

| Yol | Android ne yapar | Şekil / kural |
|---|---|---|
| `announcements/<id>` | okur (son 50, `createdAt` azalan) | `{kind, title, body, createdAt, image?{storagePath}, action?, expiresAt?}` |
| `users/<uid>/inbox/<id>` | okur (son 100) | aynı şekil |
| `users/<uid>/settings/inbox` | okur + yazar | tam olarak `{readAt == request.time}`; silinemez |
| `users/<uid>/settings/notifications` | okur + yazar | tam olarak `{marketing: bool, source: 'prompt'\|'settings', version: 1, updatedAt == request.time}`; silinemez |
| `users/<uid>/devices/<kurulumId>` | yazar / siler | tam olarak `{token (1…4096), platform: 'android', appVersion (≤32), updatedAt == request.time}`; id `^[A-Za-z0-9_-]{1,128}$` |
| `notifications/<id>` | yalnız admin okur (geçmiş) | istemci yazamaz |
| Storage `notificationImages/<id>.jpg` | admin bir kez yükler; +90 okur | `image/jpeg`, 0 < boyut ≤ 1 MiB; üzerine yazma ve silme yok |
| Callable'lar (`europe-west1`) | çağırır | yukarıdaki 5 callable |
| FCM topic `all` | abone kalır | broadcast push'u bu yoldan gelir |

**Aksiyon şekilleri:**
- `location {latitude, longitude, name ≤120, address? ≤200, placeId?}`
- `exam {examId, path[5], names[5], examKind: JPG|PDF}`; sunucu doldurur, admin yalnız `examId` gönderir.
- `paywall`, `addExam`
- `url {url: yalnız https, ≤500}`

### 1.4 Android'de bugün (4.0.0) olanlar ve boşluklar

| Parça | Bugün | 4.1.0'da |
|---|---|---|
| FCM servisi | Yalnız `title`/`body` okur. Tek kanal `announcements` (+ yerel `submissions`). Sabit bildirim id'si `0` → her push öncekinin üstüne yazar. Görsel yok. Dokunuş yalnız "inbox aç" bayrağı taşır, `notificationId` taşımaz | `PSH-01`…`03` |
| Cihaz kaydı | `users/<uid>/devices/<FCM token>` = `{platform, updatedAt}` (merge). **30 Ekim'den beri canlı kural reddediyor:** token id'de `:` içerir, `token`/`appVersion` yok; hata sessizce yutuluyor. Çıkışta ya da silmede kayıt silinmez; bellek içi tekrar koruması çıkışta sıfırlanmaz | `PSH-04`, `PRM-05` |
| Bildirim sayfası | Yalnız `announcements` (50). Okundu bilgisi yerel DataStore'da (`announcements.readIds`, cihaz bazlı, çıkışta silinmez). Görsel, aksiyon, tür ve süre yok | `INB-01`…`05` |
| İzin | Tek AlertDialog ("Bildirimleri aç", `notifications.prompted`); tanıtım turuyla aynı anda çıkabiliyor. Kampanya onayı yok. `all` aboneliği her açılışta prompt ViewModel'inden yapılıyor | `PRM-02`, `PRM-03`, `PSH-02` |
| Hesap | Bildirim satırı yok. Moderasyon satırı admin'e görünür (`isCurrentUserAdmin`, önbellekli claim) | `PRM-04`, `NIT-01` |
| Sınav kilidi | Yalnız Browse listesinde. Görüntüleyicide kontrol yok. examId'den yol çözen bir kod yok (sunucu aksiyonu 5 kimlikli yolu ve adları zaten taşıyor) | `INB-02` |
| Başlatma argümanları | `LaunchArg` yalnız boolean | `NCT-01` (metin değeri) |
| Analytics | Bildirimle ilgili özel olay yok (yalnız `Inbox` ekran adı) | `NCT-01` |

**Hazır olup yeniden kullanılacaklar:**
- Coil + kimlikli Storage istemcisi (`StorageUrl.canonical`, `StorageAuthInterceptor`).
- `SubmissionJpegEncoder`: piksel sınırı parametreli, kalite basamakları 80/60/45/30 = iOS.
- `ForegroundActivityHolder`, `AppForegroundEvents`.
- `ViFiRouter.openExam`, `ExamUnlockSheet` + `ExamUnlockViewModel`, `ExamLock`, `ExamUnlocksStore`, `SubscriptionStore`.
- Callable deseni (`FirebaseExamUnlockService`), `NotificationPermissionRequester`.

**Mevcut kayıtların etkisi yok denecek kadar az:**
- Genel duyurular `all` topic'iyle gelmeye devam ediyor.
- Sunucu `token` alanı olmayan cihaz dokümanlarını atlıyor (`loadDeviceTargets`).
- Eski dokümanlar yalnız broadcast'in "kayıtlı cihaz" sayısını biraz şişirir.

---

## 2. Paralel Çalışma Planı

```
Faz 0  NDEC-01..04 ✅ · NOPS-02 (canlı durum, salt okuma) · NOPS-01 (Maps anahtarı, ürün sahibi) ✅ ─────┐
Faz 1  NCT-01 (sözleşme, DI, rotalar, LaunchArg, analytics, sürüm — İLK GÜN, tek oturum) ─┬─► Hat A  PSH-01..04 │
                                                                                         ├─► Hat B  INB-01..05 │
                                                                                         ├─► Hat C  PRM-01..05 │
                                                                                         ├─► Hat D  ADM-01..06 │
                                                                                         └─► Hat E  MAP-01..04 ◄┘ (canlı anahtar yalnız doğrulamada)
       Hat F  NLG-01..04 (bağımsız; yalnız .md + Console hazırlığı)
Faz 2  NIT-01 (bağlama) ◄── A+B+C+D+E ──► NIT-02 (birim) · NIT-03 (UI + emülatör) · NIT-04 (belgeler)
Faz 3  NQA-01 (canlı debug) ──► NQA-02 (iOS ↔ Android çapraz)   ◄── NIT-01..03 + NOPS-01/02
Faz 4  NRL-01 (4.0.0 üretimde) → 02 → 03 → 04 → (05, 06) → 07 → 08 → 09
```

**Hat sahipliği** (çakışma olmasın diye dosya sınırları; her hat yalnız kendi yerlerine yazar):

| Hat | Dokunduğu yerler | Dokunmaz |
|---|---|---|
| **NCT-01 (sözleşme)** | `core/model/` yeni bildirim tipleri + ayrıştırıcı; tüm yeni arayüzler + `fake/`; live + mock Hilt modülleri (live'da `Unavailable*` yer tutucular); `LaunchArgs`; `ViFiRoute` + `ViFiNavHost` boş yuvalar; `AnalyticsEvent`/`AnalyticsEventMapping` + `AnalyticsLimitsTest`; `core/navigation/ModalPresence` (yeni); `app/build.gradle` sürüm | Ekranlar, canlı uygulamalar |
| **A — Push + cihaz** | `core/notifications/**`, `core/data/firebase/FirestoreDeviceRegistrar.kt` (yeni), `app/MainActivity.kt` (intent okuma), `AndroidManifest.xml` (yalnız FCM meta-data), `res/values/strings_push.xml` (yeni) | `feature/**`, `AppRoot` |
| **B — Bildirim sayfası** | `feature/inbox/**`, `core/data/inbox/**` (yeni), `core/data/archive/ExamAccessResolver.kt` (yeni), `core/data/notifications/NotificationEventTracker*` (yeni), `strings_inbox.xml` (yeni) + `strings_notifications.xml`'deki mevcut `inbox_*` anahtarları; eski `core/data/announcements/**`, `AnnouncementReadPreferences`, `FirestoreAnnouncementsReader`, `Announcement*` silinir | `AppRoot`/`ViFiNavHost`, `feature/browse/**` (yalnız okur) |
| **C — İzin / onay / ayarlar** | `feature/notifications/**`, `feature/notificationsettings/**` (yeni), `feature/account/**`, `feature/profile/ProfileCompletion*` (yalnız çıkış kancası), `core/data/notifications/settings/**` (yeni), `core/data/preferences/NotificationPromptHistory*` (yeni), `NotificationService` + `FirebaseNotificationService`, `strings_notification_settings.xml` (yeni) + mevcut `notification_prompt_*` | `core/notifications/**`, inbox |
| **D — Admin composer + geçmiş** | `feature/notificationcomposer/**` (`locationpicker/` hariç), `core/data/notifications/admin/**` (yeni), `strings_notification_composer.xml` (yeni) | Hesap satırı (→ `NIT-01`), harita |
| **E — Konum seçici** | `feature/notificationcomposer/locationpicker/**`, `app/src/live/**/maps/**`, `app/src/mock/**/maps/**`, `gradle/libs.versions.toml` + `app/build.gradle` (Maps/Places/location bağımlılıkları, anahtar yer tutucusu), `AndroidManifest.xml` (yalnız konum izinleri + Maps meta-data), `strings_location_picker.xml` (yeni) | Composer gövdesi (yalnız `LocationPicker` arayüzünü kullanır) |
| **F — Belge / beyan** | `PLAY-DATA-SAFETY.md`, `PLAY-STORE-LISTING.md`, Console hazırlığı (ürün sahibiyle) | Kod |

- **Senkron noktası 1 — `NCT-01` birleşmeden hiçbir hat başlamaz.** Modeller, arayüzler, Fake'ler, Hilt bağlaması,
  rotalar ve analytics olayları sabitlenir; ViewModel'ler ancak bağlama varken derlenir. 4.0.0'daki `BIL-01` deseni
  geçerli.
- **Senkron noktası 2 — Hatlar bitince `NIT-01`.** `AppRoot`, `ViFiNavHost` yuvaları, Hesap satırları, `SessionTeardown`
  ve live DI'da `Unavailable*` → gerçek sınıf değişimi yalnız burada yapılır. Her hat birleşmeden önce Opus incelemesinden
  geçer.
- **Senkron noktası 3 — Faz 3.** `NIT-01…03` + `NOPS-01/02` hazır olunca başlar.
- **Ortak dosya kuralları:**
  - `AndroidManifest.xml`'e iki hat dokunur (A: FCM meta-data satırı; E: konum izinleri + Maps meta-data). Farklı
    bölgeler; çakışma `NIT-01`'de çözülür.
  - Her hat kendi `strings_*.xml` dosyasına yazar (projede zaten `strings_add_exam.xml`, `strings_moderation.xml`,
    `strings_notifications.xml` deseni var).
- **Her hat için:** `./gradlew assembleDebug` + `lintLiveDebug` + **kendi** testleri; tam paket `NIT-02`'de koşar. Canlı
  Firebase'e dokunulmaz; commit yalnız kendi dalına.

### 2.1 Model planı (ürün sahibinin 30 Ekim isteği: işe göre model; kritikte Opus)

| Model | Kalemler | Neden |
|---|---|---|
| **Opus** | `NCT-01`, `NOPS-02`, `PSH-02`…`04`, `INB-01`, `INB-02`, `PRM-01`, `PRM-02`, `ADM-01`, `ADM-02`, `MAP-01`, `MAP-02`, `NLG-03`, `NIT-01`, `NQA-01`, `NQA-02`, `NRL-*`; her hattın birleşme öncesi incelemesi | Sözleşme; eşzamanlılık (dinleyiciler, okundu işareti, yönlendirme, idempotent gönderim); izin/onay (Apple 4.5.4 / Play politikası eşleniği); güvenlik (kimlikli görsel indirme, API anahtarı); canlı ortam |
| **Sonnet** | `PSH-01`, `INB-03`…`05`, `PRM-03`…`05`, `ADM-03`…`06`, `MAP-03`, `MAP-04`, `NIT-02`, `NIT-03`, `NLG-01`, `NLG-02`, `NLG-04` | Şablonlu Compose ekranları, metinler, iOS testlerinin karşılıkları |
| **Haiku** | `NIT-04` (belge güncellemesi), metin/testTag listelerinin iOS ile karşılaştırılması | Mekanik |

> Not: 24 Ekim'de ürün sahibi hata riski nedeniyle feature oturumlarında Sonnet istememişti. Bu tablo 30 Ekim isteğine
> göre bir öneridir; her oturum açılırken son söz ürün sahibinindir.

---

## 3. İş Listesi

> Her kalemin biçimi: `- [ ] `**`ID`**` başlık _(öncelik·efor·model)_ — açıklama ⟶ bağımlılık`. Ayrıntı (adımlar, kabul
> kriteri, tuzaklar) §4'te, iOS olgu listeleri ekte. ID önekleri 4.0.0'dakilerle karışmasın diye ayrıdır (`N…`).

### Faz 0 — Kararlar ve Hazırlık  ⚠️ (ürün sahibi · ONAY gerektirir)

- [x] **`NDEC-01`** İzin akışının Android eşlemesi _(P0·S)_ — iOS'a en yakın eşleme. ✅ _Karar (2026-10-30, ürün sahibi)._
  - **13+:** ilk açılışta soru yok (provisional'ın karşılığı yok). Tanıtım turundan sonra 1/2 → sistem POST_NOTIFICATIONS
    → izin verilirse 2/2.
  - **12 ve altı:** izin zaten açık → 1/2 atlanır, 2/2 bir kez sorulur.
  - **Ret sonrası:** en erken 30 gün sonra bir kez (izin: başarılı sınav gönderiminden sonra; onay: bildirim sayfası
    açılınca). Sistem diyaloğu artık gösterilemiyorsa düğme "Ayarlar'ı Aç" olur.
- [x] **`NDEC-02`** Admin kapsamı _(P0·S)_ — Tam parite: composer + geçmiş/istatistik/iptal + görsel + arşivden sınav +
  haritadan konum. Konum seçici için Google Maps + Places Android SDK, ayrı kısıtlı anahtar (`NOPS-01`) ve Veri güvenliği
  beyanı (`NLG-01`) gerekir. ✅ _Karar (2026-10-30, ürün sahibi)._
- [x] **`NDEC-03`** "Konuma Git" _(P1·S)_ — Alt sayfada üç seçenek: "Google Haritalar" (uygulama varsa uygulama, yoksa
  tarayıcı; place id ile), "Başka bir harita uygulaması" (sistem seçicisi, `geo:`) ve "Vazgeç". ✅ _Karar (2026-10-30, ürün sahibi)._
- [x] **`NDEC-04`** Rozet ve tepsi _(P2·S)_ — Sayısal ikon rozeti taklit edilmez; kanallarda sistem rozeti açık kalır.
  Uygulama içi zil sunucudaki okunmamış sayısını gösterir. Uygulama öne gelince tepsideki ViFi bildirimleri temizlenir
  (bugünkü `NotificationBadge.clear()`). ✅ _Karar (2026-10-30, ürün sahibi)._
- [x] **`NOPS-01`** Google Cloud: Android Maps/Places anahtarı _(P0·S·ürün sahibi)_ — "Maps SDK for Android" etkin
  ("Places API (New)" iOS için zaten açık). Yeni anahtar, `com.salticusteam.vifi` + üç SHA-1 (debug, yükleme, Play
  uygulama imzalama) ve bu iki API ile kısıtlı. Places kotası + bütçe uyarısı mevcut ayara eklenir. Anahtar yalnız yerel
  `local.properties`'e girer (git dışı). ⟶ `NDEC-02` ✅ _Tamamlandı (2026-10-31, ürün sahibinin isteğiyle tarayıcıdan;
  oturumu ürün sahibi açtı, proje sahibi hesabı). Gelenler:_
  - _**API'ler:** "Maps SDK for Android" etkinleştirildi; "Places API (New)" ve "Maps SDK for iOS" zaten açıktı._
  - _**Anahtar "ViFi Android Maps (admin)":** uygulama kısıtı Android uygulamaları → `com.salticusteam.vifi` + üç SHA-1
    (debug `E6:B9…:6B:AB`, yükleme `75:61…:25:D9`, Play uygulama imzalama `7F:A3…:21:9A`; `BE-01`'deki kayıtlarla aynı,
    eski `b7:6a…` eklenmedi); API kısıtı Maps SDK for Android + Places API (New). iOS'un "ViFi iOS Maps (admin)"
    anahtarının karşılığı; eski otomatik anahtarlara dokunulmadı._
  - _**Yerel:** değer pano üzerinden doğrudan `local.properties`'e (`MAPS_API_KEY`, git dışı) yazıldı; sohbete, loga ya da
    depoya girmedi, pano sonra temizlendi. `assembleLiveDebug` anahtarı `BuildConfig` ve birleşik manifeste taşıdı._
  - _**Çalışma denemesi:** Places API (New) Text Search, debug imzası başlıklarıyla 200 döndü; Android başlıkları olmadan
    403 `PERMISSION_DENIED` (uygulama kısıtı etkin). Haritanın cihazda açılması `NQA-01`'de (admin hesabıyla)._
  - _**Kota ve bütçe:** değişiklik gerekmedi. Android seçicisi yalnız Autocomplete ve GetPlace çağırıyor; ikisinin günlük
    kotası proje genelinde 300 (iOS'la ortak). Proje bütçesi (aylık ₺1.000) ve "ViFi aylık" (₺200) uyarıları %50/90/100'de
    ve tüm servisleri kapsıyor. Maps SDK for Android harita yüklemesi ücretsiz._
- [x] **`NOPS-02`** Canlı durumun salt okuma denetimi _(P1·S·Opus)_ — 13 function'ın ve kuralların canlıdaki hâli,
  `devices` kuralında `platform: 'android'`, FCM'in Android yapılandırması (`google-services.json`), emülatör için App
  Check debug anahtarı ve eski `devices/<token>` dokümanlarının sayısı. **Yazım yok.** ✅ _Tamamlandı (2026-10-30). Okuma
  yalnız proje sahibi hesabıyla (`emrebykr66`) Firebase CLI üzerinden, `GET` ve sorgu olarak yapıldı; hiçbir şey
  yazılmadı, token ya da doküman adı yazdırılmadı. Bulgular:_
  - _**Function'lar:** canlıda 13 function var, liste beklenenle birebir. Hepsi `europe-west1` / `nodejs22`; 12'si v2,
    `onAccountDeleted` v1._
  - _**Kurallar:** Firestore (`cloud.firestore/vifi`) ve Storage yayınları 30 Ekim 07:49 UTC. İçerikleri `ViFi-iOS`
    `v-4.1.0`'daki `firestore.rules` / `storage.rules` ile birebir aynı. `devices` kuralı
    `platform in ['ios', 'android']` istiyor._
  - _**İndeksler:** `notifications (status, scheduledAt)` ve `devices.token` collection-group istisnası canlıda; depo
    dosyasıyla aynı. Canlıda depoda olmayan tek ayar `loginDiagnostics.expireAt` TTL'i (60 günlük tanılama süresi, depo
    dışında açılmış). ⚠️ `firestore:indexes` depodan yayınlanırsa CLI bu TTL'i kaldırmayı önerebilir. Bu, ortak
    backend'i yayınlayan iOS tarafı için bir not; Android yayın yapmaz._
  - _**App Check:** Firestore, Storage, Realtime Database ve Authentication'ın dördü de `ENFORCED`._
  - _**Android debug anahtarları:** 4 tane var ("Emre – Medium_Phone_API_37 emülatör"…"emülatör4", 23 Ekim). 4.0.0
    PQA notundaki "PQA emülatör 2026-10-29" listede yok. `NQA-01`'de emülatörün anahtarı Logcat'ten kontrol edilir;
    eksikse eklenmesi (App Check ayarı) onay ister._
  - _**`google-services.json`:** proje numarası 441043948320 (FCM gönderici), paket `com.salticusteam.vifi`. Değişiklik
    gerekmiyor._
  - _**`config/appUpdate/android`:** `latestVersion` ve `minimumVersion` 3.1.0 (4.0.0'ın `RLS-10` adımı bekliyor)._
  - _**Cihaz kayıtları:** toplam 8 doküman. 5'i iOS ve yeni biçimde. 3'ü Android ve eski biçimde (`token`/`appVersion`
    yok, id = FCM token). Sunucu bu üçünü atlıyor; yalnız broadcast'in "kayıtlı cihaz" sayısını 3 şişiriyor. `PSH-04`
    her cihazın kendi eski kaydını siler; kalanlar zararsız._

### Faz 1 — Sözleşme (ilk gün, tek oturum)

- [x] **`NCT-01`** Sözleşme: modeller, ayrıştırıcı, arayüzler, Fake'ler, DI, rotalar, LaunchArg, analytics, sürüm
  _(P0·M·Opus)_ — `schema.js` ile alan alan aynı modeller ve saf ayrıştırıcı. Arayüzler ve Fake'ler: `InboxReader`,
  `DeviceRegistrar`, `NotificationSettingsService`, `NotificationEventReporter`, `NotificationAdminService`,
  `LocationPicker`. Mock'ta Fake, live'da `Unavailable*` bağlanır. Ayrıca yeni rotalar + boş yuvalar, `ModalPresence`,
  metin değerli LaunchArg + 4 yeni senaryo, 6 analytics olayı + 3 ekran adı, `versionName 4.1.0` / `versionCode 20`.
  ⟶ `NDEC-01…04` ✅ _Tamamlandı (2026-10-30). Gelenler:_
  - _**Modeller** (`core/model/`): `AppNotification.kt`, `NotificationAdmin.kt`, `NotificationConsent.kt`; hata tipleri
    `InboxError`, `NotificationSettingsError`, `NotificationAdminError` (`Throwable.userMessage`'a bağlı);
    `NotificationPayloadParser`._
  - _**Arayüzler ve Fake'ler** (`core/data/repository/` + `fake/`): yukarıdaki arayüzlere `DeviceRegistration`
    eklendi (çıkış kancası). Fake'ler iOS mock verisini taşıyor; örnek sınav aksiyonları Android mock arşivine
    uyarlandı._
  - _**Bağlama:** live `core/data/notifications/NotificationsModule` + `Unavailable*`; mock'ta aynı adlı modül + Fake'ler._
  - _**Navigasyon:** rotalar ve `ViFiNavHost` yuvaları; `core/navigation/ModalPresence`._
  - _**Diğer:** `LaunchArgs.value` + 4 senaryo, 6 olay + 3 ekran adı, sürüm 4.1.0 (20)._
  - _**Doğrulama:** `assembleDebug` geçti. `lintLiveDebug`: 0 hata, 56 uyarı (4.0.0 ile aynı; değişen dosyalarda uyarı
    yok). Birim testleri liveDebug 709 / mockDebug 702 / liveRelease 709, hepsi geçti (her variant'ta +55; iOS
    `NotificationPayloadParserTests` ve `NotificationDraftTests` birebir taşındı). `MockGraphTest` emülatörde 3/3
    (bildirim bağlamalarının Fake olduğu eklendi). Mock uygulama açıldı, zil Inbox'ı yeni rotayla açtı._
  - _**Not:** ilk denemede `lintAnalyze…UnitTest/AndroidTest` görevleri, aynı derlemedeki `test` görevi KSP
    dosyalarını yeniden üretirken lint'in çökmesiyle düştü; lint tek başına çalışınca temiz geçti._
  - _**Hatların bilmesi gereken kararlar** (§4'e işlendi): `Issue` metinleri modelde değil `ADM-03`'ün string
    kaynaklarında; vurgu rota argümanı (`ViFiRoute.Inbox(highlightId)`); harita seçici `LocalLocationPicker` ile
    sağlanıyor; mock izin geçmişi tohumlaması `PRM-02`'de._

### Faz 1 — Geliştirme (5 paralel kod hattı + belge hattı)

#### Hat A — Push altyapısı ve cihaz kaydı

- [x] **`PSH-01`** Bildirim kanalları + tepsi davranışı _(P0·S·Sonnet)_ — Kanallar `general` / `promo` / `system`
  (Türkçe adlarla). Eski `announcements` kanalı silinir; yerel `submissions` kalır. Manifest varsayılan kanalı `general`
  olur. Rozet/tepsi `NDEC-04`'e göre. ⟶ `NCT-01` ✅ _Tamamlandı (2026-10-30, Opus). Gelenler:_
  - _`NotificationChannels`: `GENERAL`/`PROMO`/`SYSTEM` (kimlikler `NotificationKind.raw` ile aynı) + `SUBMISSIONS`;
    `SPECS` listesi, `RETIRED = ["announcements"]` her `ensure`'da silinir. Hepsi `IMPORTANCE_DEFAULT`,
    `setShowBadge(true)`; sayı yok._
  - _Metinler yeni `strings_push.xml`'de, §4'teki ad önerileriyle; eski `notification_channel_general_*` anahtarları
    `strings.xml`/`strings_notifications.xml`'den buraya taşındı (adları aynı, değerleri yeni)._
  - _Manifest `default_notification_channel_id` → `general`. Ön plan push'u şimdilik `general`'a düşüyor; türe göre
    seçim `PSH-02`'de. `NotificationBadge` KDoc'u `NDEC-04`'e göre düzeltildi (yerel gönderim bildirimlerini de
    temizler)._
  - _**Doğrulama:** `assembleDebug` + iki androidTest APK'sı derlendi. `lintLiveDebug`: 0 hata, 56 uyarı (değişen
    dosyalarda yok). Birim testleri liveDebug 712 / mockDebug 705 / liveRelease 712, hepsi geçti (+3:
    `NotificationChannelsTest`, kimliklerin sunucu türleriyle aynı olduğunu kilitler). Tablet emülatöründe
    `NotificationChannelsDeviceTest` 2/2. NCT-01 derlemesinin `announcements` kanalı olan kurulumu yerinde
    güncellenince `dumpsys`'te dört kanal Türkçe adlarıyla, önem 3 ve rozet açık; `announcements` silinmiş._
- [x] **`PSH-02`** `ViFiMessagingService` yeniden yazımı _(P0·M·Opus)_:
  - Ön planda kanal = `data.kind` (bilinmeyen → `general`).
  - Bildirim id'si `notificationId`'ye göre (artık her push öncekini silmez).
  - Görsel varsa kısa zaman aşımıyla indirilip BigPicture gösterilir; inmezse görselsiz.
  - Dokunuş `PendingIntent`'i `notificationId` taşır.
  - `onNewToken` → cihaz kaydı + `all` aboneliği.

  ⟶ `PSH-01` ✅ _Tamamlandı (2026-10-30, Opus). Gelenler:_
  - _**`NotificationContent`** (saf): `notificationId` (sunucu desenine uymayan atılır), `kind`, `imageUrl` (yalnız
    geçerli https, imzalı URL olduğu gibi). `channelId` (bilinmeyen/eksik → `general`), `trayId` (id'nin hash'i, yoksa
    sayaç), `tapExtras()` (`notificationId`, `kind`; sistem tepsisinin başlatma intent'iyle aynı anahtarlar)._
  - _**`PushNotifier`** (yeni): ön plan gösterimi servisten buraya ayrıldı (cihazda test edilebilsin diye). Kanal türe
    göre; `TRAY_TAG = "vifi.push"` + bildirim başına id (gönderim bildirimlerinin id'leriyle çakışmaz); görsel inerse
    `BigPictureStyle` + küçük resim (FCM'in arka plan düzeni), inmezse `BigTextStyle`; `PendingIntent` request code'u
    bildirim başına, `open_inbox` + `tapExtras`. İzin yoksa (13+) ne gösterilir ne görsel indirilir._
  - _**`NotificationImageLoader`** (yeni): kendi düz OkHttp istemcisi (kimlik/App Check başlığı yok), 8 sn toplam zaman
    aşımı, https'ten düşürme yok, en çok 1 MiB (uzunluksuz gövde de sınırda kesilir), uzun kenar ≤ 1024 px'e
    örnekleme; URL imza taşıdığı için loglanmaz._
  - _**`PushTopicSubscription`** (yeni, `PushTopics` buraya taşındı): `all`'a her açılışta (`SessionViewModel`, oturumdan
    bağımsız) ve her yeni token'da katılır; hata loglanır. `NotificationPromptViewModel` artık abone olmuyor._
  - _Token hâlâ `PushTokenRegistration`'a gidiyor; `DeviceRegistrationStore`'a geçiş `PSH-04`'te. Dokunuşun okunması
    (`notificationId` → vurgu) `PSH-03`'te; bugünkü `InboxDeepLink` sayfayı açmaya devam ediyor._
  - _**Doğrulama:** `assembleDebug` + iki androidTest APK'sı; `lintLiveDebug` 0 hata, 56 uyarı (değişen dosyalarda
    yok). Birim testleri liveDebug 728 / mockDebug 721 / liveRelease 728, hepsi geçti (+16: `NotificationContentTest`
    10, `NotificationImageLoaderTest` 7 — MockWebServer ile kimliksiz istek, boyut sınırı, zaman aşımı —,
    `PushTopicSubscriptionTest` 3; prompt testleri güncellendi). Tablet emülatöründe `PushNotifierDeviceTest` 4/4
    (izinsiz gösterilmez → izinle `system` kanalında; görselli `promo` BigPicture ve istekte kimlik yok; görsel
    inmezse metin; aynı id yalnız kendini değiştirir) + `NotificationChannelsDeviceTest` 2/2. Mock uygulama açıldı,
    zil bildirim sayfasını açtı. Yeni açılan emülatör aşırı yüklüyken (yük 26) bir başlangıç ANR'ı görüldü; ana iş
    parçacığı Compose ölçümünde CPU bekliyordu, yük düşünce tekrarlanmadı._
- [x] **`PSH-03`** `NotificationRouter` + dokunuş okuma _(P0·M·Opus)_ — `InboxDeepLink`'in yerine geçer:
  - `pending(notificationId?)`, vurgu, `sessionDidChange` kuralları.
  - `MainActivity` hem kendi intent'imizden hem sistem tepsisinin başlatma intent'inden `notificationId`'yi okur
    (soğuk ve sıcak).
  - Mock'ta `vifi_open_notification <id>`.

  ⟶ `NCT-01` ✅ _Tamamlandı (2026-10-30, Opus). Ürün sahibinin kararıyla (30 Ekim) `ArchiveHost` teslimi de burada
  bağlandı (planda `NIT-01`'deydi); `InboxDeepLink` silindi. Gelenler:_
  - _**`NotificationRouter`** (`core/notifications`): `pending: StateFlow<Request?>`, `receive`, `reset`,
    `sessionDidChange` (→ `SignedOut` sıfırlar; `SignedOut → SignedIn` atar; `Unknown → SignedIn` korur),
    `start(sessions)` (oturumu `SessionViewModel` verir; enjekte edilmez, yoksa `MainActivity` mock grafını başlatma
    argümanları okunmadan kurardı), `deliveries(ready)` (en yeni dokunuş, tur bitince ve `ModalPresence` boşken;
    dokunuş ancak teslimi toplanınca düşer, iptalde kaybolmaz), `handle(intent)` + saf `tapRequest`
    (`open_inbox` ya da `google.message_id`; id sunucu desenine uymazsa vurgusuz; `kind` okunmaz)._
  - _**Vurgu durumu yönlendiricide tutulmaz:** rota argümanı taşır, "sayfa açık mı" bilgisi yalnız geri yığınıdır
    (iOS `3ec75d4` dersinin karşılığı). Zil `push(Inbox())`, kapanış geri tuşu._
  - _**`ViFiRouter.showInbox(highlightId)`:** `launchSingleTop`; üstte bildirim sayfası varsa yalnız argüman
    değişir (tek kayıt)._
  - _**`MainActivity`:** soğuk başlatmada (`savedInstanceState == null`) ve `onNewIntent`'te `handle(intent)`; son
    uygulamalardan yeniden açılış (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`) dokunuşu tekrarlamaz;
    `vifi_open_notification` (debug) soğuk başlatmada `receive` eder._
  - _**`AppRoot`:** `ArchiveHost`'ta `PushTapDelivery` → `DeepLinkViewModel.inboxOpenings` (tur durumu
    `OnboardingPreferences`'tan; yalnız `Hidden` geçirir) → `router.showInbox`._
  - _**Doğrulama:** `assembleDebug` + iki androidTest APK'sı; `lintLiveDebug` 0 hata, 56 uyarı (değişen dosyalarda
    yok). Birim testleri liveDebug 744 / mockDebug 737 / liveRelease 744, hepsi geçti (`NotificationRouterTest` 17:
    iOS'un 14 durumu + tur, modal ve iptal beklemeleri). Tablet emülatöründe `ViFiRouterTest` 9/9 (+3: tek üst kayıt,
    zil vurgusuz, geri tuşu), `PushNotifierDeviceTest` 4/4, `NotificationChannelsDeviceTest` 2/2. Mock uygulamada adb
    ile: soğuk başlatma, sıcak dokunuş (ön planda ve arka planda), sistem tepsisi biçimi, sayfa açıkken dokunuş (bir
    geri tuşuyla ana ekran), normal açılış (sayfa açılmaz), son uygulamalardan açılış (açılmaz),
    `vifi_open_notification`, tur açıkken bekleme (atlanınca açılır), oturum kapalıyken dokunuş (girişten sonra
    açılmaz) — hepsi beklendiği gibi; uygulamada ANR yok (emülatörün System UI'ı bir kez ANR verdi)._
  - _Vurgunun ekranda gösterilmesi `INB-03`'te (bugün `InboxScreen` argümanı okumuyor). Mevcut modalların
    `ModalPresence` kaydı ve izin akışının kapatılması `NIT-01`'de kalır._
- [x] **`PSH-04`** `DeviceRegistrationStore` + `FirestoreDeviceRegistrar` _(P0·M·Opus)_ — `PushTokenRegistration` /
  `FirestoreDeviceTokenStore`'un yerine geçer:
  - Kurulum başı UUID; `users/<uid>/devices/<kurulumId>` = `{token, platform: 'android', appVersion, updatedAt}`.
  - Token ve oturum birlikte olunca yazar; tekrar koruması kullanıcı değişince ya da çıkışta sıfırlanır.
  - `unregister(uid)`: en çok 3 sn, asla hata fırlatmaz.
  - Eski `devices/<token>` dokümanını bir kez siler.

  ⟶ `NCT-01` ✅ _Tamamlandı (2026-10-30, Opus). Ürün sahibinin kararıyla (30 Ekim) live bağlama da burada yapıldı
  (planda `NIT-01`'deydi). Gelenler:_
  - _**`DeviceInstallation`** (`core/data/preferences`, DataStore): `notifications.installationId.v1` = küçük harf
    UUID, desene (`^[A-Za-z0-9_-]{1,128}$`) uymayan değer yenilenir; `notifications.legacyDeviceRemoved.v1` bayrağı.
    Çıkışta silinmez (kurulum boyunca sabit)._
  - _**`FirestoreDeviceRegistrar`** (`core/data/firebase`): `set` (merge yok) tam 4 alan; `appVersion` ≤ 32;
    `updatedAt` sunucu zamanı. İlk başarılı kayıttan sonra eski `devices/<token>` bir kez silinir (kural sahibine
    silme izni veriyor; hata sessiz, sonraki kayıtta yeniden denenir). `unregister` kurulum dokümanını siler._
  - _**`DeviceRegistrationStore`** (`core/notifications`, `DeviceRegistration`'ı uygular): iOS kuralları (son gelen
    tetikler; aynı çift süreç başına bir kez; başka kullanıcı sıfırlar; çıkış yalnız bellekte sıfırlar; hata loglanır,
    sonraki tetikleyicide yeniden). `start(sessions)` açılışta token'ı okur ve oturumu izler (`SessionViewModel`);
    `onNewToken` → `tokenDidChange`. `unregister`: önce durumu sıfırlar, uçuştaki yazımı bekler (silmeden sonra
    belge yeniden oluşmasın), sonra siler; hepsi 3 sn içinde, asla fırlatmaz. Durum kilitle korunur (FCM token'ı
    kendi iş parçacığından bildirir)._
  - _**Bağlama:** live `DeviceRegistrar` → `FirestoreDeviceRegistrar`, iki flavor'da `DeviceRegistration` →
    `DeviceRegistrationStore` (mock'ta yazımlar `FakeDeviceRegistrar`'a). Silinenler: `PushTokenRegistration`(+test),
    `DeviceTokenStore`, `FirestoreDeviceTokenStore`, `FakeDeviceTokenStore`, `UnavailableDeviceRegistrar`/`…Registration`.
    Çıkış/hesap silmede `unregister` çağrısı `PRM-05`'te._
  - _**Doğrulama:** `assembleDebug` + iki androidTest APK'sı; `lintLiveDebug` 0 hata, 56 uyarı (değişen dosyalarda
    yok). Birim testleri liveDebug 759 / mockDebug 752 / liveRelease 759, hepsi geçti (−5 eski, +20:
    `DeviceRegistrationStoreTest` 13 — iOS'un 11'i + açılış/oturum ve uçuştaki yazımı bekleme —,
    `DeviceInstallationTest` 5, `FirestoreDeviceRegistrarTest` 2). Tablet emülatöründe `MockGraphTest` 3/3 (depo +
    sahte yazıcı, Firebase hiç başlamıyor) ve `ViFiRouterTest` 9/9; mock uygulama açıldı, dokunuş sayfayı açtı.
    Canlıya yazım yapılmadı; gerçek belge yazımı `NQA-01`'de (onayla)._

#### Hat B — Bildirim sayfası

- [x] **`INB-01`** `FirestoreInboxReader` + `InboxStore` _(P0·L·Opus)_:
  - İki kaynak (50 + 100) + `readAt` dinleyicisi (`includeMetadataChanges`, yalnız önbellekteki "yok"u atla).
  - Birleştirme (aynı id'de kişisel kopya kazanır), süre dolumu, sıralama.
  - Yükleniyor / hata / kısmi hata durumları; yeniden dene.
  - Okunmamış = `createdAt > readAt`; sayfa açılınca tek yazım.
  - Yeni hesapta "şimdi" bir kez yazılır; eski yerel okundu kimlikleri temizlenir.

  ⟶ `NCT-01` ✅ _Tamamlandı (2026-10-30, Opus). `PSH-03`/`PSH-04`'teki ürün sahibi kararlarıyla aynı yolda live
  bağlama ve eski duyuru sınıflarının silinmesi de burada yapıldı (planda `NIT-01`'deydi). Gelenler:_
  - _**`FirestoreInboxReader`** (`core/data/firebase`): `announcements` ve `users/<uid>/inbox`
    `orderBy(createdAt desc)` + 50/100 (süreden önce), düz dinleyici; `Timestamp` → `Instant`; ayrışmayan belge
    atılır; hata `InboxError.Unavailable` ile akışı bitirir. İşaret `settings/inbox` `MetadataChanges.INCLUDE`,
    önbellekteki "yok" atlanır, `ServerTimestampBehavior.ESTIMATE`; `markRead` tam `{readAt: serverTimestamp}`._
  - _**`InboxStore`** (`core/data/inbox`, ana iş parçacığına bağlı): `InboxState` (öğeler, `isLoading`, `error`,
    `hasPartialFailure`, `ReadMarker` Unknown/Unavailable/Known; `isUnread`, `unreadCount`) + `unreadCount` akışı.
    Birleştirme (kişisel kopya kazanır), süre filtresi (zamanlayıcı yok; `reevaluateExpiry()` sayfa açılınca),
    yeniden ile eskiden sıralama, tarihsiz sonda, eşitlikte id. `start(sessions)` (oturum açılınca gözlem, profil
    beklenmez; çıkışta sıfırlama; eski uid'nin geç teslimleri yok sayılır), `retry()` (tam yeniden başlatma),
    `markAllRead()` (en yeni okunmamışa göre tekrar koruması, hatada sıfırlanır). Yeni hesapta (sunucu "yok" dedi)
    gözlem başına bir kez cihaz saati + `markRead`; başarıda eski `announcements.readIds` silinir
    (`LegacyInboxReadState`), hatada korunur; okuma hatasında ve bekleyen işarette asla yazılmaz._
  - _**Ekran:** `InboxViewModel` artık "açılıştaki okunmamışları dondurmuyor" (nokta canlı); sayfa açılınca ve açıkken
    okunmamış > 0 oldukça `markAllRead`. `InboxBadgeViewModel` → `InboxStore.unreadCount`. Satırlar şimdilik
    başlık/metin/tarih (`InboxEntry`); görsel, eylem, vurgu ve kısmi hata bandı `INB-03`'te._
  - _**Bağlama/silinenler:** live `InboxReader` → `FirestoreInboxReader` (`UnavailableInboxReader` silindi);
    `LegacyInboxReadState` live'da DataStore, mock'ta sahte. Silinenler: `AnnouncementsStore`(+test),
    `AnnouncementsReader`, `FirestoreAnnouncementsReader`, `FakeAnnouncementsReader`, `Announcement`,
    `AnnouncementParser`(+test), `AnnouncementReadPreferences`(+DataStore/sahte). `SessionViewModel` deposu başlatıyor._
  - _**Doğrulama:** `assembleDebug` + iki androidTest APK'sı; `lintLiveDebug` 0 hata, 56 uyarı (değişen dosyalarda
    yok). Birim testleri liveDebug 781 / mockDebug 774 / liveRelease 781, hepsi geçti (`InboxStoreTest` 29 — iOS'un 29
    durumu birebir —, `InboxViewModelTest` 5). Tablet emülatöründe `MockGraphTest` 3/3; mock uygulamada zil
    "6 okunmamış", sayfa en yeniden eskiye 6 satır (süresi dolan gizli), geri dönünce sayı kalktı; ANR yok. Canlıya
    yazım yapılmadı._
- [x] **`INB-02`** `ExamAccessResolver` + sınav kapısı _(P0·M·Opus)_ — Bildirimden açılan sınav arşivin kilit kuralından
  geçer:
  - Abone ya da daha önce açılmış → açık.
  - Değilse dersin tam listesi yüklenir; `ExamLock.freeExamIds`'e göre açık ya da kilitli → mevcut kilit sheet'i (coin /
    parayla / Premium / coin kazan).
  - Bulunamazsa "Bu sınav artık arşivde bulunmuyor."
  - Çift dokunma koruması.

  ⟶ `NCT-01` ✅ _Tamamlandı (2026-10-30, Opus). Gelenler:_
  - _**`ExamAccessResolver`** (`core/data/archive`): `access(examPath, isSubscribed, unlockedIds)` → `ExamAccess`
    Open / Locked(arşivin kendi öğesi) / Unavailable. Sıra iOS'la aynı: 5 seviyeli değilse istek yok; abone ya da
    açılmışsa ders yüklenmez; değilse dersin filtresiz tam listesi, dosyasız sınavlar da ücretsiz yerleri sayar.
    Android eki: silinmiş ders (`ArchiveError.NotFound`) → Unavailable; diğer hatalar fırlar._
  - _**`InboxExamGate`** (`feature/inbox`, ViewModel'e ait): `check(target)` (açılabilir mi; çalışırken ikinci
    dokunuş yok sayılır), durum `checkingExamId` / `lockedExam` (kilit sheet'i) / `errorMessage`; `followUp(ExamUnlockEvent)`
    → `Open(target)` / `AddExam(ders)` / `Paywall`, kilitli sınav başına bir kez; `onSheetDismissed`, `onErrorShown`.
    "Bu sınav artık arşivde bulunmuyor." → `InboxError.ExamMissing`. Mevcut `ExamUnlockSheet` + `ExamUnlockViewModel`
    olduğu gibi kullanılacak._
  - _Kapının ekrana bağlanması (eylem düğmesi, sheet, "Sınav açılamadı" diyaloğu, `ViFiRouter.openExam`) `INB-03`'te;
    bugün bildirim satırında düğme yok._
  - _**Doğrulama:** `assembleDebug` + iki androidTest APK'sı; `lintLiveDebug` 0 hata, 56 uyarı (değişen dosyalarda
    yok). Birim testleri liveDebug 798 / mockDebug 791 / liveRelease 798, hepsi geçti (`ExamAccessResolverTest` 9 —
    iOS'un 8'i + silinmiş ders —, `InboxExamGateTest` 8). Ekran davranışı olmadığı için emülatör adımı `INB-03`'e
    kaldı._
- [x] **`INB-03`** Bildirim sayfası arayüzü _(P0·M·Sonnet)_:
  - Kart: tür ikonu, başlık, gövde, "Kampanya" etiketi, tarih, okunmadı noktası.
  - Kimlikli görsel; 5 aksiyon düğmesi (abone ise Premium gizli); düğme varsa kart/görsel dokunuşu aynı aksiyonu yapar.
  - Push vurgusu + kaydırma.
  - Kısmi hata şeridi, yeni boş durum metni; mevcut "izin kapalı" şeridi korunur.

  ⟶ `INB-01`, `INB-02` ✅ _Tamamlandı (2026-10-30, Opus — ürün sahibinin "doğruluk hızdan önce" tercihiyle Sonnet
  yerine). Gelenler:_
  - _**`InboxCard`** (yeni): tür rozeti (genel `Campaign`, kampanya `LocalOffer`, sistem `NotificationsActive`;
    birincil renk %15 zemin), başlık, gövde, "Kampanya" + tarih ("30 Eki 2026", iOS'taki kısa ay), okunmadı noktası
    (`inbox.unread.<id>`, "Okunmadı"). Görsel: `InboxReader.imageUrl` (klasör yeniden doğrulanır; live'da bucket
    `FirebaseStorage`'tan, mock'ta örnek görsel) + mevcut kimlikli Coil yükleyici; yükseklik 160 dp × yazı ölçeği,
    ≤ 280 dp; yer tutucu ve hata ikonu. Düğme turuncu zeminde siyah yazı, ikonlu, sınav kontrolünde ilerleme;
    düğme varsa kart/görsel/düğme aynı eylem, yoksa kart etkisiz; vurgu `primary` %18 + `selected`._
  - _**Ekran:** kısmi hata şeridi (`inbox.partialFailure`, `inbox.retry`), boş durum "Bildirim yok" / "Yeni
    bildirimler burada görünecek.", izin şeridi korundu. Vurgu rota argümanıyla (`InboxScreen(highlightId)` →
    `onHighlightRequested`; `launchSingleTop` argüman değişince de çalışır, `SavedStateHandle` güncellenmediği için
    tercih edildi); yalnız listede çözülünce gösterilir ve karta ortalanarak kaydırılır._
  - _**Eylemler** (`InboxEvent`): sınav → `InboxExamGate` → `ViFiRouter.openExam`; kilitliyse mevcut
    `ExamUnlockSheet` + `ExamUnlockViewModel`, sonucu `onUnlockEvent` ile (aç / Sınav Ekle dersle / Paywall);
    "Sınav açılamadı" diyaloğu. Paywall → `Paywall`; Sınav Ekle → `AddExam()`; bağlantı → tarayıcı
    (`ACTION_VIEW`); konum → `ChooseMapsApp` olayı (alt sayfa `INB-04`'te; o zamana kadar düğme bir şey yapmıyor).
    Abone için Premium kartı etkisiz._
  - _"İstatistik" (`opened` / `actionTapped`) `INB-05`'te bağlanacak._
  - _**Doğrulama:** `assembleDebug` + iki androidTest APK'sı; `lintLiveDebug` 0 hata, 56 uyarı (eklenen bir `UseKtx`
    uyarısı düzeltildi). Birim testleri liveDebug 803 / mockDebug 796 / liveRelease 803, hepsi geçti
    (`InboxViewModelTest` 10). Tablet emülatöründe `InboxContentTest` 6/6 + `MockGraphTest` 3/3. Mock uygulamada:
    kartlar/rozetler/etiket/görsel/düğmeler; `vifi_open_notification p-location` vurgusu; ücretsiz sınav açıldı,
    Geri dersin listesine döndü; kilitli sınavda kilit sheet'i, coin ile açınca görüntüleyici; Premium → paywall;
    `vifi_subscribed` ile Premium düğmesi yok, kilitli sınav doğrudan açıldı. ANR yok._
- [x] **`INB-04`** "Konuma Git" alt sayfası + harita bağlantıları _(P1·S·Sonnet)_ — `NDEC-03`: Google Haritalar
  (uygulama → web, `query_place_id`), sistem `geo:` seçicisi, Vazgeç. iOS `MapsLinks` sayı biçimi ve kodlama kuralları.
  ⟶ `NCT-01` ✅ _Tamamlandı (2026-10-30, Opus). Gelenler:_
  - _**`MapsLinks`** (saf): Google Haritalar `https://www.google.com/maps/search/?api=1&query=<lat>%2C<lng>`
    (+ `&query_place_id=<kodlu id>`), `geo:<lat>,<lng>?q=<lat>,<lng>(<kodlu ad>)`; sayılar 6 ondalık, sondaki
    sıfır/nokta atılır, `-0` → `0`, `Locale.ROOT`; kodlama yalnız `A-Za-z0-9-._~`'yi korur._
  - _**`LocationChoiceSheet`**: "Konumu hangi uygulamada açmak istersin?", ad (+ adres ikinci satırda), "Google
    Haritalar" (birincil), "Başka bir harita uygulaması", "Vazgeç" (`inbox.location.google|other|cancel`). Seçim
    sheet'i kapatır, "Vazgeç" sayfayı açık bırakır; `RegisterModalPresence` ile sayılır. Seçim ViewModel durumunda
    (`locationChoice`), ekran dönünce kaybolmaz._
  - _**Açılış:** Google → `setPackage("com.google.android.apps.maps")`, yoksa aynı URL tarayıcıda. Diğer →
    `geo:` + `Intent.createChooser`; işleyen uygulama yoksa Google web. Manifest `<queries>`'e `geo:` eklendi
    (Android 11+ paket görünürlüğü)._
  - _**Doğrulama:** `assembleDebug` + iki androidTest APK'sı; `lintLiveDebug` 0 hata, 56 uyarı. Birim testleri
    liveDebug 813 / mockDebug 806 / liveRelease 813, hepsi geçti (`MapsLinksTest` 9 — iOS'un 9'u, Apple yerine
    `geo:` —, `InboxViewModelTest` +1). Tablet emülatöründe `LocationChoiceContentTest` 2/2 + `InboxContentTest`
    6/6. Mock uygulamada "Konuma Git" sheet'i açıldı, "Vazgeç" kapattı; "Google Haritalar" Maps uygulamasını
    açtı; "Başka bir harita uygulaması" `geo:` ile (tek işleyen Maps olduğu için seçici atlandı) iğneyi doğru
    yere ve Türkçe adla koydu._

- [x] **`INB-05`** `NotificationEventTracker` + `trackNotificationEvent` _(P1·S·Sonnet)_:
  - `opened` yalnız push vurgusu listede çözülünce; `actionTapped` her aksiyon dokunuşunda.
  - Kullanıcı + olay + id başına bir kez (kalıcı, en çok 500). Gönder ve unut; başarısızlık hatırlanmaz.

  ⟶ `NCT-01` ✅ _Tamamlandı (2026-10-30, Opus). Gelenler:_
  - _**`FirebaseNotificationEventReporter`** (`core/data/firebase`): `trackNotificationEvent`
    `{notificationId, event}` callable'ı (europe-west1), yanıt yok sayılır; live'da `UnavailableNotificationEventReporter`
    yerine bağlandı (silindi)._
  - _**`NotificationEventTracker`** (`core/data/notifications`): anahtar `"<uid>|<event>|<id>"`; onaylananlar DataStore
    `notifications.reportedEvents.v1`'de eskiden yeniye, en yeni 500; uçuştaki anahtar ikinci kez gönderilmez;
    hatada uçuş işareti kalkar, onay hatırlanmaz (sonraki dokunuşta tekrar). `@ApplicationScope`'ta gönder ve unut._
  - _**Bağlama:** `InboxViewModel` `opened`'ı vurgu listede çözülünce (zil, süresi dolmuş ya da gelmemiş bildirim
    sayılmaz; sonra gelirse sayılır), `actionTapped`'ı eylemden önce (kilitli sınav da) gönderir; kullanıcı yoksa
    gönderilmez._
  - _**Doğrulama:** `assembleDebug` + iki androidTest APK'sı; `lintLiveDebug` 0 hata, 56 uyarı. Birim testleri
    liveDebug 826 / mockDebug 819 / liveRelease 826, hepsi geçti (`NotificationEventTrackerTest` 11 — iOS'un 11'i —,
    `InboxViewModelTest` +2). Mock uygulamada push ile açılış + eylem dokunuşu çökmeden çalıştı. Canlı callable
    çağrısı `NQA-01`'de (onayla)._

#### Hat C — İzin, kampanya onayı, Bildirim Ayarları

- [x] **`PRM-01`** `NotificationSettingsStore` + `FirestoreNotificationSettingsService` _(P0·M·Opus)_:
  - Onay dokümanının canlı dinleyicisi; yükleniyor / hazır / okunamıyor durumları.
  - Kayıt yoksa "cevaplanmadı", kampanyalar kapalı. `waitUntilLoaded`.
  - `setMarketing`: iyimser güncelleme, 4 sn'de onay gelmezse "kuyrukta" sayılır, hata gelirse eski değere döner.
  - Kullanıcı değişince temizlenir; eski kullanıcıdan gelen değer yok sayılır.

  ⟶ `NCT-01` ✅ _Tamamlandı (2026-10-30, Opus). Gelenler:_
  - _**`FirestoreNotificationSettingsService`** (`core/data/firebase`): `settings/notifications` canlı dinleyici
    (`ESTIMATE`, `includeMetadataChanges` yok); yalnız boolean `marketing` cevap sayılır; hata
    `NotificationSettingsError.Unavailable` ile akışı bitirir. Yazım merge'siz tam
    `{marketing, source, version: 1, updatedAt: serverTimestamp}`. Live'da yer tutucunun yerine bağlandı._
  - _**`NotificationSettingsStore`** (`core/data/notifications/settings`): `NotificationSettingsState`
    (LOADING / READY / UNAVAILABLE + `consent`; `isLoaded`, `hasAnswered`, `marketingEnabled`), `start(sessions)`
    (oturum açılınca gözlem, profil beklenmez; başka kullanıcı baştan; çıkışta sıfırlama; eski uid'nin geç değer ve
    hataları yok sayılır), `waitUntilLoaded(5 sn)`, `setMarketing` (iyimser, ret → eski değer + `false`, 4 sn'de onay
    yoksa kuyrukta sayılır + `true`, kullanıcı yoksa `false`). `SessionViewModel` başlatıyor._
  - _Live'daki yer tutucu dosyası artık yalnız admin servisini (`ADM-01`) içeriyor; önceki kaldırmalardan kalan sahipsiz
    açıklamalar ve importlar temizlendi._
  - _**Doğrulama:** `assembleDebug` + iki androidTest APK'sı; `lintLiveDebug` 0 hata, 56 uyarı. Birim testleri
    liveDebug 845 / mockDebug 838 / liveRelease 845, hepsi geçti (`NotificationSettingsStoreTest` 15,
    `FirestoreNotificationSettingsServiceTest` 4 — iOS'un 15 + 4'ü). Mock uygulama ana ekrana açıldı (görülen
    "yanıt vermiyor" penceresi emülatörün System UI'ına aitti). Depoyu kullanan ekranlar `PRM-02`…`04`'te; canlı
    yazım `NQA-01`'de (onayla)._
- [x] **`PRM-02`** `NotificationPromptHistory` + `NotificationPromptCoordinator` _(P0·M·Opus)_:
  - Cihaz başı geçmiş (DataStore), 30 gün kuralı, "ilk hayır kazanır".
  - Eski `notifications.prompted` → "1/2 sunuldu" eşlemesi.
  - Tetikleyiciler: ilk sunum (turdan sonra), onay yakalama (izinli ama kayıtsız; 12 ve altı dahil), sınav
    gönderiminden sonra izin tekrarı, bildirim sayfasında onay tekrarı.
  - Tek yuvalı kuyruk, modal yokken sunum. Android izin durumu + "sistem diyaloğu gösterilebilir mi".

  ⟶ `PRM-01` ✅ _Tamamlandı (2026-10-30, Opus). Gelenler:_
  - _**`NotificationPromptHistory`** (`core/data/preferences`): arayüz + `DataStoreNotificationPromptHistory`
    (iOS anahtarları; ret zamanı epoch ms; yalnız ilk "hayır"; 30 gün; vadesi gelen gösterilene kadar kalır; 4.0.0'ın
    `notifications.prompted`'ı 1/2 sunuldu sayılır) + `FakeNotificationPromptHistory` (mock'ta her açılışta
    LaunchArg'a göre tohumlanır, iOS mock paketi gibi)._
  - _**`NotificationPromptCoordinator`** (`feature/notifications`): `NotificationPrompt(kind PERMISSION|CONSENT, source,
    permission, id)`, tek yuva `pending`/`presented`, `present()`, `dismiss()`. `offerFirstRunIfNeeded()` (eski
    gösterimde izin yoksa "hayır" kaydı; sorulmadıysa 1/2; izinliyse ve onay kaydı 5 sn içinde "cevaplanmadı" ise 2/2
    bir kez; reddedildiyse hiçbir şey), `examWasSubmitted()` (vadesi gelmiş + izin yok), `inboxConsentReask()`
    (vadesi gelmiş + 2 sn içinde yüklendi + kampanya kapalı; Android'de de tek sunucu kuyruğuna girer). iOS'un sessiz
    provisional izninin karşılığı yok (`prepare()` yazılmadı)._
  - _**`NotificationService.canRequestPermission()`**: 13+'ta hiç sorulmadıysa ya da sistem gerekçe bayrağıyla bir
    kez daha sorabiliyorsa `true`; izinli, 12 ve altı ya da "bir daha sorma" → `false` ("Ayarlar'ı Aç").
    `FirebaseNotificationService` `ForegroundActivityHolder` ile, sahte servis yapılandırılabilir; sahte servis
    artık sistem gibi davranıyor (soramıyorsa istek bir şey değiştirmiyor)._
  - _**Mock senaryoları:** `vifi_notifications_prompt` (sorulmadı, boş geçmiş), `vifi_notifications_denied` (reddedildi,
    sorulamaz), `vifi_notifications_reask` (reddedildi, sorulabilir; 31 gün önce "hayır"), `vifi_consent_reask`
    (31 gün önce onay "hayır"); varsayılan izinli, iki adım da sunulmuş._
  - _Koordinatörü kullanan sayfa ve sunucu `PRM-03`'te; Sınav Ekle / bildirim sayfası tetikleyicileri ve çıkışta
    `dismiss` `NIT-01`'de._
  - _**Doğrulama:** `assembleDebug` + iki androidTest APK'sı; `lintLiveDebug` 0 hata, 56 uyarı. Birim testleri
    liveDebug 875 / mockDebug 868 / liveRelease 875, hepsi geçti (`NotificationPromptHistoryTest` 9 — iOS'un 8'i +
    4.0.0 bayrağı —, `NotificationPromptCoordinatorTest` 21 — Android izin modeline uyarlandı, provisional testleri
    "sessiz istek yok" ve "kayıt 5 sn'de gelmezse sonraya kalır" ile değişti). Tablet emülatöründe `MockGraphTest`
    3/3; dört senaryo açılışı çökmesiz ve ANR'sız._

- [x] **`PRM-03`** İzin/onay sayfaları _(P0·M·Sonnet)_ — AlertDialog'un yerine iki adımlı alt sayfa:
  - 1/2 "Hiçbir şeyi kaçırma" / tekrarda "Onaylanınca haber verelim mi?"; 2/2 "Öğrenci fırsatları".
  - Onay sayfası kaydırılarak kapatılamaz (kayıt hatası hariç); yükseklik içeriğe göre; turuncu üstünde siyah metin.
  - 6 analytics olayı (`source`).

  ⟶ `PRM-02` ✅ _Tamamlandı (2026-10-30, Opus). Gelenler:_
  - _**`NotificationPermissionFlow`** (`feature/notifications`, iOS `NotificationPermissionFlowModel`): sayfa
    PERMISSION|CONSENT, `start()` (1/2'de "sunuldu" ya da "tekrar gösterildi", 2/2'de `consentDidAppear`), "Bildirimleri
    Aç" (sistem diyaloğu → ret "hayır" + bitiş; izin + başka cihazda cevaplanmış → bitiş; değilse 2/2), "Ayarlar'ı Aç"
    (kabul sayılır), "Şimdi Değil", iki cevap da kayıt yazar (`source: prompt`), kayıt hatası sayfayı açık tutar ve
    olay göndermez, cevapsız kapanış "hayır" sayılır (onay yazılmaz). "Ayarlar'ı Aç" düğmesi `!canRequestPermission()`
    ile açılır (NDEC-01). Android ekleri: sistem diyaloğu açıkken akış kapanırsa 2/2'ye geçilmez; kaydedilen cevabın
    kendi düğmesi ilerleme gösterir (iOS yalnız "Evet"te gösteriyordu)._
  - _**Sayfalar** (`NotificationPromptPages.kt`): tek `ModalBottomSheet`'te iki sayfa (geçişte solma), yükseklik
    içeriğe göre; mesaj yalnız sığmazsa kayar, düğmeler altta sabit (en fazla 440 dp). Metinler iOS'la birebir
    (`strings_notifications.xml`, `notification_prompt_*`); "İstediğin an Hesap'tan kapat." iOS kaynağındaki gibi
    (bu belgede ve ekte "Hesap'dan" yazıyordu, düzeltildi). Tag'ler `notifications.priming(.enable|.openSettings|
    .dismiss|.deniedNote)`, `notifications.consent(.accept|.decline|.error)`._
  - _**Kapatma:** 1/2 kaydırma, perde dokunuşu ve Geri ile kapanır; 2/2 hiçbiriyle kapanmaz (kayıt hatası sonrası
    açılır). Material3 1.3.1 sheet'in Geri davranışını açıldığı anda sabitliyor (Android 13+): 1/2'de açık olan Geri
    2/2'de de kapatıyordu (emülatörde görüldü). Sheet'in kendi Geri'si kapalı tutulup sayfaya bağlı `BackHandler`
    kullanıldı; perde ve kaydırma `confirmValueChange` ile kapalı._
  - _**Sunucu** (`NotificationPromptViewModel` yeniden yazıldı + `NotificationPromptHost`): kuyruktaki akış tur
    bitince, kayıtlı modal yokken, yoldaki push dokunuşu teslim edilince ve oturum açıkken ekrana gelir; push dokunuşu
    ve çıkış akışı kapatır (cevapsız sayfa "hayır"). İlk teklif arşiv açılınca turdan sonra yapılır. `AppRoot`'ta
    `NotificationPrompt()` → `NotificationPromptHost()`; eski AlertDialog, `notificationPrompt.*` tag'leri ve
    `notification_prompt_title/message/allow/later` silindi. PSH-03/04 emsaliyle bağlama şimdi yapıldı; bildirim
    sayfasındaki onay tekrarı ve Sınav Ekle sonrası izin tekrarı tetikleyicileri `NIT-01`'de._
  - _**Doğrulama:** `assembleDebug` (iki flavor); `lintLiveDebug` 0 hata, 56 uyarı (değişen dosyalarda yok). Birim
    testleri liveDebug 899 / mockDebug 892 / liveRelease 899, hepsi geçti (`NotificationPermissionFlowTest` 18 —
    iOS'un 15'i + 3 Android —, `NotificationPromptViewModelTest` 11, `NotificationPromptCoordinatorTest` 21). Tablet
    emülatöründe `NotificationPromptContentTest` 7/7. Mock'ta `vifi_notifications_prompt`: 1/2 → "Bildirimleri Aç"
    → 2/2; 2/2 Geri, perde ve kaydırmayla kapanmadı, "Hayır, teşekkürler" kapattı; 1/2 Geri, kaydırma ve "Şimdi
    Değil" ile kapandı; `vifi_notifications_denied` ve varsayılan açılışta sayfa çıkmadı; koyu tema kontrol edildi._
- [x] **`PRM-04`** Hesap › Bildirim Ayarları _(P0·M·Sonnet)_:
  - Satır + ekran: izin durumu (Açık / Kapalı / Sorulmadı) + "Bildirimleri Aç" ya da "Ayarlar'ı Aç".
  - "Kampanya ve fırsat bildirimleri" anahtarı; okunamıyor/hata durumları.
  - Uygulama öne dönünce izin yeniden okunur; `NotificationSettings` ekran adı.

  ⟶ `PRM-01` ✅ _Tamamlandı (2026-10-30, Opus). Gelenler:_
  - _**`NotificationSettingsViewModel`** + `NotificationSettingsUiState` (`feature/notificationsettings`, iOS
    `NotificationSettingsViewModel`): izin `permission()` + `canRequestPermission()` ile okunur; ekran her öne
    gelişte (`LifecycleResumeEffect`) yeniden okur. "Bildirimleri Aç" sistem diyaloğunu açıp izni yeniden okur;
    diyalog açıkken ikinci dokunuş yok sayılır, analytics gönderilmez, izin geçmişine dokunulmaz. "Ayarlar'ı Aç"
    `PRM-03`'ün `openAppNotificationSettings()`'ini kullanır. Anahtar onay deposuna canlı bağlı; kayıt okunmadan ve
    kayıt sürerken pasif. `setMarketing(…, SETTINGS)` başarılıysa `marketing_consent_accepted|declined(settings)`;
    değilse anahtar geri döner ve hata görünür (sonraki denemede silinir). Kapatma tekrar kuralını kurmaz. Kayıt
    uygulama kapsamında sürer, ekran kapansa da olay gider (iOS'taki yapısız `Task` gibi). Ekran adı
    `NotificationSettings`._
  - _**Android eşlemesi (NDEC-01):** durum "Açık / Kapalı / Sorulmadı" (okunmadan "Yükleniyor…"); düğme
    `canRequestPermission()`'a göre seçilir. Bir kez reddedilip sistemin yeniden sorabildiği durumda ("Kapalı" +
    "Bildirimleri Aç") alt not "Bildirim izni henüz verilmedi." olur. "Ayarlar'a git" notu yalnız "Ayarlar'ı Aç"
    düğmesiyle çıkar (iOS'ta bu durum yok)._
  - _**Ekran** (`NotificationSettingsScreen` + durumsuz `NotificationSettingsContent`): `ViFiTopAppBar` + liste.
    Metinler iOS'la birebir (`strings_notification_settings.xml`; "Yükleniyor…" `common_loading`). Tag'ler
    `notificationSettings.list|permission|enable|openSettings|permissionNote|marketing|unavailable|error`._
  - _**Hesap satırı:** "Bildirim Ayarları" / "Bildirim izni ve kampanya tercihin" (`account.notificationSettings`),
    Moderasyon'dan sonra, silme sürerken pasif. `AccountScreen(onNotificationSettings = null)` varsayılanıyla gizli.
    Satırın gezinmesi ve `ViFiNavHost` yuvasına `NotificationSettingsScreen(onBack)` bağlanması `NIT-01`'de._
  - _**Doğrulama:** `assembleDebug` + iki androidTest APK'sı; `lintLiveDebug` 0 hata, 56 uyarı (değişen dosyalarda
    yok). Birim testleri liveDebug 913 / mockDebug 906 / liveRelease 913, hepsi geçti
    (`NotificationSettingsViewModelTest` 14 — iOS'un 9'u + Android: sistemin yeniden sorabildiği ret, öne dönüşte
    yeniden okuma, çift dokunma, kayıt sürerken değişiklik, başka cihazdaki değişiklik, ekran adı). Compose UI testleri
    yazıldı ve derlendi (`NotificationSettingsContentTest` 11, `AccountContentTest` +2); cihazda koşturma
    birleştirmede._
- [x] **`PRM-05`** Çıkış ve hesap silme kancası _(P0·S·Sonnet)_:
  - Hesap'tan çıkışta, profil tamamlama ekranındaki çıkışta ve hesap silmede (`deleteProfile`'dan önce) önce
    `DeviceRegistration.unregister` çalışır (≤3 sn).
  - `signOut` askıya alınır ve çift dokunma koruması alır.

  ⟶ `NCT-01` ✅ _Tamamlandı (2026-10-30, Opus). Gelenler:_
  - _**Hesap çıkışı** (`AccountViewModel.signOut`): önce `DeviceRegistration.unregister(uid)` (≤ 3 sn, asla
    fırlatmaz), sonra `authService.signOut()` + `SignedOut`. Çıkış kancayı bekler (iOS `signOut() async`). Dizi
    uygulama kapsamında koşar: temizlik sürerken Geri ile ekrandan çıkılsa da onaylanan çıkış iptal olmaz. Çift
    dokunma koruması `AccountUiState.isSigningOut`; bu sürede "Çıkış Yap" ve "Hesabı Sil" pasif, silme de yok sayılır
    (iOS yalnız ikinci çıkışı engelliyordu). Bekleme sırasında gösterge yok (iOS gibi)._
  - _**Hesap silme:** `unregister` → `deleteProfile` → `deleteAccount`. Yeniden doğrulama iptal edilirse kayıt
    silinmiş kalır; bir sonraki girişte yeniden yazılır._
  - _**Profil tamamlama "Çıkış Yap":** aynı kanca `ProfileFormViewModel.signOut()`'ta (ekran onu çağırıyor;
    `ProfileCompletionScreen` değişmedi), uygulama kapsamında ve çift dokunma korumalı. Çıkış mantığı
    `ProfileFormViewModel.kt`'te olduğu için hat tablosundaki `ProfileCompletion*` yerine bu dosyaya (yalnız çıkışa)
    dokunuldu; `feature/profile`'a başka hat yazmıyor._
  - _**Doğrulama:** `assembleDebug` + iki androidTest APK'sı; `lintLiveDebug` 0 hata, 56 uyarı (değişen dosyalarda
    yok). Birim testleri liveDebug 919 / mockDebug 912 / liveRelease 919, hepsi geçti (`AccountViewModelTest` +4:
    `signOutRunsHookFirst`, `signOutIgnoresDoubleTap`, silmede kancanın profilden önce koşması, çıkış sürerken silme;
    mevcut `signOut` ve `signOutWhileDeleting` karşılıkları kancayı da doğruluyor. iOS `signOutFailure`'ın karşılığı
    yok: Android `signOut()` hata vermiyor. `ProfileFormViewModelTest` +2). Compose UI testi `AccountContentTest` +1
    (çıkış sürerken satırlar pasif) yazıldı ve derlendi; cihazda koşturma birleştirmede._

#### Hat D — Admin "Bildirim Gönder" + Geçmiş

- [x] **`ADM-01`** `FirebaseNotificationAdminService` _(P0·M·Opus)_:
  - 4 callable; `sendNotification` zaman aşımı 300 sn (istemci varsayılanı 70 sn).
  - Geçmiş: `notifications` koleksiyonu, `createdAt` azalan, en çok 50.
  - Görsel yükleme: önce `getMetadata`, yoksa `putBytes` (`image/jpeg`).
  - iOS ile aynı hata eşleme tablosu; facets / sonuç / geçmiş ayrıştırıcıları.

  ⟶ `NCT-01` ✅ _Tamamlandı (2026-10-30, Opus). Gelenler:_
  - _**`FirebaseNotificationAdminService`** (`core/data/notifications/admin`): 4 callable (yalnız `sendNotification`
    `withTimeout(300 s)`), `audience` veri göndermeden çağrılır, `preview` kitle şeklini taslağın payload'ından alır;
    geçmiş `notifications` `createdAt` azalan, 50, `Timestamp` → `Instant`; görsel yüklemesi önce kimlik/boyut
    denetimi, sonra `getMetadata` (varsa yeniden kullanılır), yalnız `OBJECT_NOT_FOUND`'da `putBytes` (`image/jpeg`).
    Ayrıştırıcılar NCT-01'deki `NotificationPayloadParser`'dan; okunamayan yanıt `UnexpectedResponse`._
  - _**Hata eşleme** iOS tablosuyla aynı; SDK kod enum'ları JVM'de yüklenemediği için ad üzerinden ortak
    `FirebaseStatus`'a indirilip birim testleniyor. Android ekleri: Functions'ın ağ hatası (`INTERNAL` + `IOException`)
    ve Storage'ın `ERROR_UNKNOWN` + `IOException`'ı çevrimdışı sayılır; mesajsız ret ve diğerleri genel mesaja düşer
    (ham İngilizce mesaj gösterilmez)._
  - _Live DI'daki `UnavailableNotificationAdminService` → bu sınıf değişimi `NIT-01`'de. Canlı callable çağrılmadı._
  - _**Testler:** `FirebaseNotificationAdminServiceTest` 22 (iOS'un 8'i + 5 Android eşleme + 9 MockK'li çağrı
    testi: zaman aşımı, payload, sorgu şekli, görselin üzerine yazılmaması); cihazda koşacak
    `FirebaseNotificationAdminErrorMappingTest` 3 (kod adı köprüsü; yalnız derlendi)._
- [x] **`ADM-02`** Composer ViewModel + taslak kuralları _(P0·L·Opus)_:
  - Doğrulama sırası ve mesajları; karakterler **kod noktasıyla** sayılır (sunucu gibi).
  - Konumda Tür kilidi; kademeli kitle; tahmini kişi sayısı.
  - "Sessiz düşürme yok" kuralı (aksiyon seçildiyse geçerli olmalı).
  - Zamanlama +1 sa / geçerlilik +7 gün.
  - Önizleme → onay → gönder; test gönderimi; taslak id ömrü; görsel-id kuralı; sonuç işleme; çift dokunma.

  ⟶ `ADM-01` ✅ _Tamamlandı (2026-10-30, Opus). Gelenler:_
  - _**`NotificationComposerUiState`** (`feature/notificationcomposer`): formun alanları + türetilmiş değerler (taslak,
    `issue`/`visibleIssue`, `canSend`/`canSendTest`, sayaçlar `trim` + kod noktası, Türkçe sıralı seçici listeleri,
    tahmini kitle, Tür kilidi/`effectiveKind`, test taslağı). Zaman kontrolleri durumdaki `now`'a göre; her
    değişiklikte ve Gönder/test dokunuşunda yenilenir (bekleyen form geçmişe kayan zamanı dokunuşta yakalar).
    Geri bildirim metin değil tip: `ComposerError` (`Message(userMessage)` / `SendFailed`) ve `ComposerSuccess`
    (`Sent(n)` / `Scheduled(at)` / `TestSent`); metinleri ve tarih biçimi arayüzde (`ADM-03`)._
  - _**`NotificationComposerViewModel`**: iOS akışı birebir — facets açılışta bir kez (hata → "Tekrar dene"),
    kademeli kitle sıfırlaması, onay kaydı ayrı (`confirmation` + `isConfirming`), görsel yalnız onaydan sonra taslağın
    kendi id'siyle yüklenir, sonuç işleme (`sent`/`sending` → sıfırla; `scheduled` → sayısız mesaj + sıfırla;
    `failed`/`canceled` → yeni id, form kalır; hata → aynı id), test her dokunuşta yeni id + eksik segment "herkes",
    görsel-id kuralı, çift dokunma koruması, `NotificationComposer` ekran adı. Sessiz saat istemcide hesaplanmaz._
  - _**Görsel kodlayıcı** (`core/data/notifications/admin`): `NotificationImageEncoder` + `ContentResolverNotificationImageEncoder`
    (`SubmissionJpegEncoder` kuralları, 1600 px, ≤ 1 MiB, EXIF, IO'da; okunamadı/kullanılamıyor ayrımı) +
    iki flavor'ın ortak `NotificationAdminModule`'ü. VM'in Hilt grafiği için burada geldi; seçici arayüzü `ADM-04`'te._
  - _**Testler** (iOS karşılıkları + Android ekleri): `NotificationComposerViewModelTest` 13 (10 + kod noktası,
    zamanlamanın geçerliliği taşıması, ekran adı), `…SendFlowTest` 16 (15 + geçmişe kayan zaman),
    `…AttachmentTest` 16 (13 + eski kodlamanın yenisini ezmemesi, hazırlanırken gönderim kapalı, okunamayan görsel),
    `…KindLockTest` 7, `…LocationTest` 8; iOS `StubNotificationAdminService` karşılığı test çifti._
- [x] **`ADM-03`** Composer arayüzü _(P0·M·Sonnet)_ — Bölümler: Tür, İçerik, Hedef kitle, Eylem, Görsel, Zamanlama,
  geri bildirim, Gönder / Bana test gönder. Onay diyaloğu; `notificationComposer.*` testTag'leri; `NotificationComposer`
  ekran adı. ⟶ `ADM-02` ✅ _Tamamlandı (2026-10-30, Opus). Gelenler:_
  - _**`NotificationComposerScreen`** (durumlu; `onBack`, `onOpenHistory`, `locationRows`) + durumsuz
    `NotificationComposerContent(state, actions: NotificationComposerActions, …)`; üst çubukta "Geçmiş"
    (`notificationComposer.history`). Form iOS sırasıyla kart bölümler hâlinde: Tür (segment; Konum'da kilit + kilit
    notu), İçerik (sayaçlar, sınır aşılınca hata rengi), Hedef kitle (segment, yükleniyor/hata + "Tekrar dene",
    Üniversite/Fakülte/Bölüm açılır menüleri "<ad> · <n> kişi", Cinsiyet, "≈ N kişi", iki dipnot), Eylem (Buton menüsü,
    https alanı), Zamanlama (anahtarlar + gün → saat seçicileri, cihaz saat dilimi), geri bildirim (hata / başarı /
    neden), Gönder + "Bana test gönder". Onay `AlertDialog`'u ("…gönderilsin mi?" / "…zamanlansın mı?", "N kişiye…",
    zamanlıysa saat) `ModalPresence`'a kayıtlı. Metinler `strings_notification_composer.xml`'de iOS'la birebir
    (`Issue` metinleri dahil, sınırlar `%1$d`); tarih "30 Eki 2026 14:30" (iOS kısaltılmış tarih + kısa saat)._
  - _**Konum yuvası:** Konum seçiliyken Eylem bölümü `ComposerLocationRows(location, enabled, onLocationPicked)` çizer;
    satırlar ve seçicinin sunumu Hat E'nin (`MAP-03`), bağlama `NIT-01`'de (not: ortak not klasöründe
    `hat-d-adm-notes.md`). Görsel bölümü `ADM-04`'te, sınav satırı `ADM-05`'te eklendi._
  - _Tag'ler iOS kimlikleri + Android alt kimlikleri: segmentler `.kind.general|promo`,
    `.audience.everyone|segment`, `.gender.all|female|male`; menüler `<menü>.field` ve `<menü>.option.<id|ad>`._
  - _**Testler:** `NotificationComposerContentTest` 7 (gerçek VM + Fake servis: dokunulmamış form, kademeli kitle
    64 → 40 → 28 → 17 → 24 → 9, onaydan sonra gönderim, vazgeçince gönderilmemesi, Konum'da Tür kilidi; durumsuz:
    sayaç/neden, zamanlı onay diyaloğu) — yalnız derlendi; cihazda `NIT-03`'te._
- [x] **`ADM-04`** Görsel hazırlama _(P1·S·Sonnet)_ — Foto seçici (izinsiz `PickVisualMedia`). `SubmissionJpegEncoder`
  yeniden kullanılır: en uzun kenar 1600 px, ≤ 1 MiB, EXIF yönü. Ana iş parçacığı dışında çalışır; sürüm sayacıyla eski
  kodlama yeni seçimin üstüne yazılmaz. ⟶ `ADM-02` ✅ _Tamamlandı (2026-10-30, Opus). Gelenler:_
  - _**"Görsel" bölümü** (`NotificationComposerImageSection`): sistem Photo Picker'ı (`PickVisualMedia.ImageOnly`,
    izin yok), önizleme (Coil, 160 dp, 12 dp köşe), "Görsel Seç"/"Görseli Değiştir", hazırlanırken ilerleme ("Görsel
    hazırlanıyor"), "Görseli Kaldır", hata satırı (okunamadı / kullanılamıyor); iOS tag'leri `.imagePicker`,
    `.imagePreview`, `.imageRemove`, `.imageError` + Android `.imagePreparing`._
  - _Kodlayıcı (`ContentResolverNotificationImageEncoder`, ADM-02'de geldi) lint'e göre düzeltildi: `toUri()` /
    `Bitmap.scale()`. EXIF yönü, Sınav Ekle'nin kodlayıcısı gibi platform `ExifInterface`'iyle okunuyor; AndroidX
    `exifinterface` uygulamanın bağımlılığı değil, uyarı yalnız o fonksiyonda gerekçeyle susturuldu (iki kodlayıcıyı
    AndroidX'e geçirmek bir derleme değişikliği; `NIT-01`'e not)._
  - _Sürüm sayacı ve "hazırlanırken gönderim kapalı" ADM-02'nin VM testlerinde._
  - _**Testler:** cihazda `ContentResolverNotificationImageEncoderTest` 4 (2400×1200 → 1600×800 JPEG ≤ 1 MiB, küçük
    görsel büyütülmez, görsel olmayan veri, eksik dosya) ve `NotificationComposerImageSectionTest` 5 — yalnız derlendi._
- [x] **`ADM-05`** Arşivden sınav seçici _(P1·M·Sonnet)_ — Üniversite → fakülte → bölüm → ders → sınav gezinmesi
  (`ArchiveRepository`), Türkçe duyarsız arama. Gönderilen yalnız `examId`; dosyasız sınav seçilemez. ⟶ `ADM-02`
  ✅ _Tamamlandı (2026-10-30, Opus). Gelenler:_
  - _**`NotificationExamPicker`**: iOS gibi arşivin kendi `BrowseViewModel`'iyle (yeni arşiv kodu yok) iç içe bir
    NavHost'ta, uygulamanın `ViFiRoute.Home`/`Browse` hedefleriyle gezinir; seviyelerin VM'leri seçiciye ait bir
    `ViewModelStore`'da yaşar, kapanınca temizlenir. Arama seçicide yerel tutulup `BrowseUiState`'in Türkçe
    katlamasıyla süzülür (VM'in arşiv araması analytics olayı üretilmez; iOS'ta da seçici bu olayı göndermiyor).
    Dosyasız sınav pasif; sınav seçilince kapanır, gönderilen yalnız `examId` (yolun son bileşeni)._
  - _Sunum: iOS sheet'inin yerine formun yerini alan tam ekran katman (Sınav Ekle'deki seçiciler gibi); tam ekran
    `Dialog`'un Android 15'te sistem çubuğu altına kaydığı projede kayıtlı. Kökte "✕" (`.exam.cancel`) ve Geri kapatır,
    derinde Geri bir üst seviyeye döner (`.exam.back`); form kaydırma konumunu korur; `ModalPresence`'a kayıtlı.
    Başlık düğümün adı ya da "Sınav Seç"; seviye arama istemleri, boş ve "… için sonuç yok" durumları arşivle ortak
    metinler. Satırlar `notificationComposer.exam.row.<ad>`._
  - _**Eylem satırı** (`ExamRow`): "Sınav" + seçilen sınavın adı ya da "Seç" (`.exam`), altında yol (`.examPath`,
    "Üniv › Fakülte › Bölüm › Ders")._
  - _**Testler:** `NotificationExamPickerTest` 6 (kök ve Türkçe arama, dosyasız sınav, boş seviye, sonuçsuz arama,
    satırın seçimi ve yolu, seçimsiz satır) — yalnız derlendi; sınav eyleminin VM kuralları ADM-02 testlerinde. Mock
    arşivde uçtan uca gezinti (iOS `testAdminLinksAnExamPickedFromTheArchive`) `NIT-03`'te._
- [x] **`ADM-06`** Gönderim Geçmişi _(P1·M·Sonnet)_ — Satırlarda durum / tür / Test / Otomatik rozetleri, kitle etiketi,
  zaman ("(sessiz saat)") ve sayılar. Zamanlanmışta "İptal et" (iOS gibi onaysız). Yenileme ve hata durumları;
  `NotificationHistory` ekran adı. ⟶ `ADM-01` ✅ _Tamamlandı (2026-10-30, Opus). Gelenler:_
  - _**`NotificationHistoryViewModel`** (iOS birebir): açılışta bir kez yükler; aşağı çekme ve hata ekranındaki
    "Tekrar Dene" yeniler (yenileme sürerken ikinci çekme yok sayılır); başarısız yenileme satırları korur ve hatayı
    üstte gösterir; "İptal et" yalnız zamanlanmışta ve tek seferde bir tane (`cancelingId`), başarıda listeyi yeniden
    yükler, `NotCancelable`'da mesaj + yeniden yükleme, diğer hatada yalnız mesaj; `NotificationHistory` ekran adı._
  - _**`NotificationHistoryScreen`** ("Gönderim Geçmişi"): yükleniyor ("Geçmiş yükleniyor…"), ilk yükleme hatası
    (yeniden dene), boş ("Henüz bildirim yok" + açıklama), liste (`notificationComposer.history.list`): tür ikonu,
    başlık, kitle etiketi, rozetler (durum, tür, Test, Otomatik; tema konteyner renkleri, sabit renk yok; büyük yazıda
    alt satıra kayar), zaman satırı ("<zaman> için zamanlandı (sessiz saat)" / gönderim ya da oluşturma zamanı), gönderilmiş
    olanlarda iki sayı satırı (plurals). Satır `…history.row.<id>`; iOS'un kaydırma eylemi + bağlam menüsü yerine
    satırın menüsü (`.menu`) → "İptal et" (`.cancel`, onaysız)._
  - _Composer'daki "Geçmiş" düğmesi `onOpenHistory` ile verilir; `ViFiNavHost`'un `notificationComposer` /
    `notificationHistory` yuvalarına ve Hesap satırına bağlama `NIT-01`'de._
  - _**Testler:** `NotificationHistoryViewModelTest` 9 (iOS'un 7'si + aşağı çekme koruması, ekran adı);
    `NotificationHistoryContentTest` 7 (gönderilmiş satır, zamanlanmışın iptali, uçuştaki iptal, hata + satırlar, boş,
    ilk yükleme hatası, yükleniyor) — yalnız derlendi._

#### Hat E — Konum seçici (admin)

- [x] **`MAP-01`** Bağımlılıklar + anahtar yapılandırması _(P0·S·Opus)_:
  - Maps Compose + Maps + Places (New) + konum; yalnız **live** flavor'a.
  - Anahtar `local.properties` → manifest yer tutucusu; anahtar yoksa seçici "yapılandırılmamış" der ve çökmez.
  - SDK ilk açılışta başlar; mock'ta Google SDK'sı hiç başlamaz.
  - R8 ve boyut etkisi ölçülür.

  ⟶ `NCT-01`; canlı deneme `NOPS-01` ✅ _Tamamlandı (2026-10-30, Opus). Gelenler:_
  - _**Sürümler** (`liveImplementation`): `maps-compose` 6.5.3, `play-services-maps` 19.0.0, `places` 4.1.0,
    `play-services-location` 21.3.0. Projenin Kotlin 2.0.21 / Compose BoM 2024.10.01'iyle derlenmiş en yeni sürümler:
    maps-compose 6.6+ Compose'u 1.8'e, Places 4.2+ kotlin-stdlib'i 2.1'e çekiyor (6.5.3'ün BoM'u 2024.11.00, içeriği
    2024.10.01 ile birebir aynı). Places 4.1.0'da Places API (New), `CircularBounds`, `setRegionCode` ve
    `DISPLAY_NAME`/`LOCATION`/`FORMATTED_ADDRESS`/`ID` alanları var. play-services-location 21.4.0 Kotlin 2.3
    metadata'sı taşıdığı için derlenmiyor; play-services-maps maps-compose'un derlendiği 19.x'te kaldı. Dördünün "daha
    yeni sürüm var" lint uyarısı katalogda gerekçeli `#noinspection` ile susturuldu; Kotlin yükseltmesinde birlikte
    ele alınmalı._
  - _**Anahtar:** `local.properties` `MAPS_API_KEY=` → live `manifestPlaceholders.mapsApiKey` +
    `BuildConfig.MAPS_API_KEY`. `GoogleMapsConfiguration` (`app/src/live/.../core/maps/`) boş, kırpılınca boş,
    metin olmayan ya da `${`/`$(` ile başlayan değeri kullanılamaz sayar; `toString` anahtarı yazmaz.
    `GoogleMapsSdk.startIfPossible()` Places'i (New, `tr-TR`) ilk çağrıda bir kez başlatır; uygulama açılışında
    çağrılmaz. Anahtarsız seçici görünümü `LocationPickerUnavailable` ("Harita anahtarı yapılandırılmamış", "Kapat")._
  - _**Karar (2026-10-30, ürün sahibi; Hat F önerisi K1):** seçici yalnız `ACCESS_COARSE_LOCATION` ister. Play'in yeni
    kuralı FINE isteyen uygulamadan Console'da gerekçe istiyor (27 Ocak 2027'den itibaren zorunlu); harita zaten semte
    odaklanıyor. FINE, bir kütüphane eklemeye kalksa da `tools:node="remove"` ile dışarıda tutuluyor (live debug ve
    release birleşik manifestlerinde yalnız COARSE, doğrulandı). Places bias kuralı için `MAP-02` notuna bakın._
  - _**Manifest:** konum izni ve Maps meta-data'sı ana manifest yerine yeni `app/src/live/AndroidManifest.xml`'de:
    mock'un birleşik manifestinde konum izni ve harita anahtarı yok, Hat A'nın satırlarıyla çakışma da yok. Places'in
    eklediği `ACCESS_WIFI_STATE` ("mevcut yer" API'si için; kullanılmıyor) `tools:node="remove"` ile çıkarıldı.
    Play-services-maps `glEsVersion 2` özelliğini ve Google Haritalar paket sorgusunu ekliyor._
  - _**Doğrulama:** `assembleDebug` geçti (anahtarsız). `GoogleMapsConfigurationTest` 6/6 (iOS
    `GoogleMapsConfigurationTests` karşılığı; `keyName` yerine "uygulama derlemenin anahtarını okur ve yazdırmaz").
    `LocationPickerUnavailableTest` derlendi._
  - _**R8 ve boyut** (`MAP-03` ile ölçüldü; unsigned liveRelease, anahtarsız): öncesi APK 4.318.909 / AAB 9.809.477
    bayt. Bu dalda APK 5.459.438 (+1.140.529, ~%26) / AAB 11.726.756 (+1.917.279). Seçici composer'a bağlanınca
    (geçici keep kuralıyla ölçüldü, commit'lenmedi) APK 5.541.602 (+1.222.693, ~+1,17 MiB, %28) / AAB 11.949.107
    (+2.139.630). Farkın çoğu kullanılsın kullanılmasın gelir: Places 4.x–5.x'in kendi R8 kuralları tüm Places SDK'sını
    (`-keep class com.google.android.libraries.places.**`) ve tüm public Kotlin stdlib'ini (`-keep public class kotlin.**`)
    koruyor (Places 6.0.2 Kotlin kuralını kaldırmış ama yeni Kotlin istiyor); dex +811 KB, kaynaklar +270 KB. Places'in
    kullanılmayan `AutocompleteActivity`'si live manifestinden çıkarıldı (etkisi ~0). R8 uyarıları: Places/location'ın
    karartılmış Kotlin sınıflarında zararsız "companion object" uyarıları; seçici composer'a bağlanana kadar Hilt'in
    `LocationPickerViewModel` kimlik dizesi uyarısı (VM erişilebilir olunca kayboluyor, doğrulandı)._
- [x] **`MAP-02`** `LocationPickerViewModel` _(P0·M·Opus)_:
  - Arama: 3+ karakter, 350 ms bekleme, oturum jetonu yenileme, görünür alana göre bias, `TR`.
  - Öneri seçimi (yalnız 4 alan); POI dokunuşu (adres arkada gelir).
  - İğne ~1 m'den fazla kayınca `placeId` düşer; ad/adres sınırları.
  - Cihaz konumu bir kez sorulur; admin haritayı yerleştirdiyse odaklanılmaz. Konum saklanmaz, loglanmaz, gönderilmez.

  ⟶ `MAP-01` ✅ _Tamamlandı (2026-10-30, Opus). Gelenler:_
  - _**Sözleşme** (`locationpicker/`): `PlaceSearcher` (+ `PlaceSuggestion`, `SearchArea` ≤ 50 km, `PlaceDetails`,
    `PlaceSearchError` Unavailable/NotFound, iOS metinleri), `DeviceLocator` (+ `LocationAuthorization`,
    `DeviceCoordinate`), `LocationPickerUiState` (`MapCamera`, `CameraRequest`, `PlaceSearchState`, türetilmiş
    `location`/`canConfirm`/`coordinateText` `%.5f` `Locale.ROOT`), `LocationPickerMapEvent` (Settled/GestureStarted/
    PlaceTapped = iOS `GoogleMapView.Event`)._
  - _**`LocationPickerViewModel`** (`@HiltViewModel`, `initial` assisted): iOS durum makinesi birebir; ad/adres
    sınırları ve kesme kod noktasıyla (emoji bölünmez), harita etiketi tek satıra iner (Unicode boşluklar dahil).
    **Android farkı:** izin Activity ister; VM `locateDevice(yetki)` ile bir kez başlar, hiç sorulmadıysa
    `RequestLocationPermission` olayı gönderir, cevabı `onLocationPermissionResult` ile alır (istenmeyen cevap yok
    sayılır). `onDismissed()` uçuştaki arama/çözme/adres/konum işlerini durdurur._
  - _**Live** (`app/src/live/.../core/maps/`): `GooglePlacesSearcher` (autocomplete `regionCode TR` + dairesel bias,
    `fetchPlace` yalnız `ID/DISPLAY_NAME/LOCATION/FORMATTED_ADDRESS`, POI adresi yalnız `FORMATTED_ADDRESS`, her hata
    → Unavailable, logda yalnız durum kodu), `PlacesSession` (150 sn'de ve her fetch'te yeni jeton),
    `FusedDeviceLocator` (izin kontrolü, `BALANCED_POWER_ACCURACY`, 15 sn, iptalde istek de iptal, koordinat
    loglanmaz), `MapsModule` (kapsamsız: her seçicinin kendi Places oturumu). **Mock** `MapsModule` yalnız VM derlensin
    diye hiçbir şey bulmayan/konum okumayan yer tutucular bağlar._
  - _**Karar (2026-10-30, ürün sahibi; Hat F önerisi K1):** görünür alan bias'ı yalnız admin haritayı kendisi
    yerleştirdikten sonra (sürükleme/yakınlaştırma, seçilen öneri, POI dokunuşu, mevcut konumu düzenleme) gönderilir.
    Harita yalnız cihaz konumuyla odaklandıysa arama yalnız `regionCode TR` ile gider; böylece cihaz konumu dolaylı da
    olsa Google'a ulaşmaz. iOS'tan bilinçli fark._
  - _**Testler:** `LocationPickerViewModelTest` 22 (iOS 17 + 5), `LocationPickerDeviceLocationTest` 16 (iOS 13 +
    3; `withoutLocator` → "konum istenmeden hiçbir şey sorulmaz"), `LocationPickerMapPlaceTest` 16 (iOS 14 + bias
    kararının 2 testi), `PlacesSessionTest` 3 (live); liveDebug ve mockDebug'da geçti._
- [x] **`MAP-03`** Seçici arayüzü + composer konum satırları _(P0·M·Sonnet)_ — Ortada sabit iğne, arama + öneriler,
  ayrıntı kartı (koordinat, ad, adres), "Bu konumu seç" / "İptal", izin kapalı notu. `locationPicker.*` ve
  `notificationComposer.location.*` testTag'leri. ⟶ `MAP-02` ✅ _Tamamlandı (2026-10-30, Opus). Gelenler:_
  - _**`LocationPickerScreen`** (main): VM sunum başına (`rememberSaveable` anahtar + `hiltViewModel` assisted; döndürmede
    korunur, yeniden açılışta sıfırdan), izin isteği `RequestPermission` (yalnız COARSE, `MAP-01` kararı),
    yetki `checkSelfPermission` + `shouldShowRequestPermissionRationale` (bir kez reddedilen bir daha sorulmaz),
    Geri/✕ → `onDismissed` + iptal. **`LocationPickerContent`** (stateless): ✕ "İptal" üst çubuk, harita yuvası
    (`LocationPickerMap`; TalkBack'e tek öğe "Harita. Konum, haritanın ortasındaki iğnenin gösterdiği yerdir."), ortada
    sabit iğne (ucu merkezde), üstte arama paneli (Aranıyor…/Sonuç bulunamadı./hata/en çok 280 dp öneri listesi,
    temizle düğmesi), altta ayrıntı kartı (`%.5f` koordinat + "Koordinat: …", izin kapalı notu, ad/adres alanları,
    sınır hataları plurals, adres yükleniyor göstergesi, "Bu konumu seç"; klavyede `imePadding`)._
  - _**Live** `GoogleLocationPicker` (anahtar yoksa `LocationPickerUnavailable`, SDK burada ilk kez başlar) +
    `GoogleLocationMap` (maps-compose: pusula/döndürme/eğme kapalı, konum noktası ve düğmesi yalnız izinliyken, POI
    dokunuşu, durunca merkez + zoom + görünür yarıçap, yalnız jest `GestureStarted`, kamera isteği kimlikle bir kez —
    döndürmede tekrar oynamaz, simetrik dikey iç boşluk: kamera hedefi iğnenin altında kalır, konum düğmesi arama
    alanının altında kalmaz, karanlık tema sistemle)._
  - _**`LocationPickerDialog`**: `LocalLocationPicker`'ı tam ekran `Dialog`'da sunar, `RegisterModalPresence` ile
    modal sayılır, Android 15+ kenardan kenara pencere (tanıtım turuyla aynı yöntem)._
  - _**`NotificationComposerLocationRows(location, enabled, onLocationPicked)`**: Hat D'nin `ComposerLocationRows`
    yuvasıyla aynı imza; özet ("Seçilen konum: ad, adres"), "Konum seç"/"Değiştir" (gönderim sürerken pasif), kampanya
    notu; seçiciyi kendisi açar. Metin anahtarları `location_picker_composer_*` (Hat D'nin dosyasıyla çakışmasın diye)._
  - _**Testler** (derlendi, cihazda koşturulmadı): `LocationPickerContentTest` 6, `NotificationComposerLocationRowsTest`
    5. Lint 0 hata / 56 uyarı (Türkçe metin dosyası `tools:locale="tr"`, sınır metinleri plurals)._
- [x] **`MAP-04`** Mock seçici _(P1·S·Sonnet)_ — Sabit yer ("ViFi Öğrenci Kafesi", 40.9903, 29.0290). Google SDK'sına ve
  konuma dokunmaz; UI testleri için. ⟶ `NCT-01` ✅ _Tamamlandı (2026-10-30, Opus). Gelenler:_
  - _**`MockLocationPicker`** (`app/src/mock/.../core/maps/`, `@Inject`): iOS `MockLocationPicker` birebir —
    "Örnek harita", yer adı (`locationPicker.mock.name`), adres, "Bu konumu seç" (`locationPicker.confirm`), ✕ "İptal"
    (`locationPicker.cancel`); `SAMPLE_LOCATION` = "ViFi Öğrenci Kafesi", 40.9903, 29.0290, "Caferağa, Kadıköy,
    İstanbul", `placeId` yok; düzenlemede mevcut yeri sunar. Google SDK'sı ve konum yok (mock'ta bu bağımlılıklar
    hiç yok). "Örnek harita" metni ana dosyada, live'da kullanılmadığı için gerekçeli `tools:ignore`._
  - _**Bağlama `NIT-01`'de** (bu hatta bilerek yapılmadı): mock `NotificationsModule.provideLocationPicker()` →
    `MockLocationPicker()`, live'da `GoogleLocationPicker` (Hilt `@Inject`); composer'da Hat D'nin `locationRows`
    yuvasına `NotificationComposerLocationRows(location, enabled, onPick)`._
  - _**Testler:** `MockLocationPickerTest` 3 (`androidTestMock`, derlendi; iOS `LocationCampaignUITests`'in composer
    kısmı composer bağlanınca `NIT-03`'te)._

#### Hat F — Yasal metinler ve Play beyanları

- [x] **`NLG-01`** Veri güvenliği envanteri _(P0·S·Sonnet)_ — `PLAY-DATA-SAFETY.md`:
  - Cihaz kimlikleri (FCM token + kurulum id'si), kullanıcı kimliği ve profil segmentleri + onay kaydı için "Reklam veya
    pazarlama" amacı.
  - Uygulama etkinliği (okundu işareti, açılma/aksiyon istatistiği).
  - Maps/Places SDK'nın Google'ca yayımlanan beyanları.
  - Konumun toplanmadığının gerekçesi (Places bias'ı dahil değerlendirilir).

  ⟶ `NDEC-02` ✅ _Tamamlandı (2026-10-30, Opus). Form ürün sahibinin kararlarından (K2…K4) sonra doldurulur. Gelenler:_
  - _**Tablo:** UID, profil segmentleri ve cihaz kimliklerine "geliştirici iletişimleri" + "reklam veya pazarlama";
    uygulama etkileşimlerine "uygulama işlevi"; yeni satır "Diğer işlemler" (onay kaydı, isteğe bağlı). Kurulum kimliği,
    okundu işareti, istatistik, 6 olay + 3 ekran adı (29 olay, 18 ekran) işlendi. "Segmentasyon Analytics audience'la"
    notu düzeltildi: hedefleme sunucuda RTDB profilleriyle._
  - _**§4.1.0:** amaçların Play tanımlarına göre gerekçesi; bildirim verilerinin yer/yazım/okuma/silme envanteri;
    Maps ve Places SDK'nın Google beyanları (ikisi de 28 Ekim 2026 güncel) ve Play türlerine eşlemesi; konum analizi;
    Console kontrol listesi (`NRL-05`)._
  - _**Bulgular:** (1) Places bias'ı otomatik cihaz odağında cihaz konumunu dolaylı olarak Google'a gönderiyor; Google
    "enlem-boylam sınırları"nı toplanan veri sayıyor. (2) Kişi başı istatistik işaretleri
    (`notifications/<id>/events/<uid>_<olay>`) hesap silmede silinmiyor._
  - _**Kararlar:** K1 konum ✅ (ürün sahibi 30 Ekim: yalnız `COARSE` + bias yalnız admin haritayı yerleştirince; Hat E
    `7c74b21`'de uyguladı → konum beyanı yok). Açık: K2 admin SDK verileri (öneri: kilitlenme + tanılama + arama geçmişi, isteğe bağlı), K3 istatistik
    işaretlerinin silinmesi (öneri: ortak backend), K4 "geliştirici iletişimleri" amacı (öneri: evet)._
- [x] **`NLG-02`** Gizlilik politikası kontrolü _(P1·S·Sonnet)_ — Canlı `/gizlilik` kampanya bildirimlerini, onay
  kaydını, cihaz kaydını ve admin harita konumunu anıyor mu? Eksikse metin ortak depoda (`ViFi-iOS/firebase/hosting/`)
  önerilir; hosting yayını ayrıca onay ister. ⟶ `NLG-01` ✅ _Tamamlandı (2026-10-30, Opus). Öneri metni bu dalda
  [`PRIVACY-POLICY-4.1.0.md`](PRIVACY-POLICY-4.1.0.md)'de; ortak depoya ve hosting'e dokunulmadı. Gelenler:_
  - _**Kontrol:** `/gizlilik`, `/hesap-silme`, `/kullanim-kosullari` salt okundu (HTTP 200, `Last-Modified` 29 Ekim
    06:26 GMT). Üçü de `ViFi-iOS` `v-4.1.0`'daki dosyalarla bayt bayt aynı; 4.1.0 için değişiklik yok._
  - _**Eksikler:** kampanya bildirimleri ve onay kaydı yok; cihaz kaydı kısmen var; segment hedeflemesi, okundu
    işareti ve istatistik yok; admin harita ekranı ve Google Maps Platform yok. "Reklam göstermez" cümlesi kampanyalarla
    çelişebilir. Hesap silme sayfası bildirim verilerini anmıyor. Kullanım koşullarında Google Maps Platform şartlarının
    (§3.2.2) istediği Google Maps maddesi yok; bu iOS 4.1.0'ı da ilgilendiriyor._
  - _**Önerilen metin:** gizlilik tablosu (2 değişen, 3 yeni satır), "Bildirimler" ve "Yöneticilerin harita ekranı"
    bölümleri, reklam cümlesinin iki biçimi (R1'e bağlı), aktarım/saklama cümleleri, hesap silme satırları (K3'e bağlı),
    kullanım koşullarına Google Haritalar maddesi. Ek öneri: Android konum diyaloğundan önce iOS'un amaç metni
    (`MAP-02`/`MAP-03`)._
  - _**Ürün sahibine:** metin onayı (P1), Google Maps maddesi (P2), yayın zamanı (P3; iOS 4.1.0 da aynı sayfalara
    bağlı)._
- [x] **`NLG-03`** Play politikası kontrolü _(P1·S·Opus)_ — Onaylı kampanya bildirimlerinin Play kurallarına uygunluğu
  (bildirimle tanıtım, "Reklam içeriyor" beyanı gerekiyor mu?) ve yalnız ön plan konum izninin beyan gerektirip
  gerektirmediği. Sonuç ürün sahibine sorulur. ✅ _Tamamlandı (2026-10-30, Opus). Ayrıntı
  [`PLAY-POLICY-4.1.0.md`](PLAY-POLICY-4.1.0.md)'de (29 Ekim 2026 yürürlüklü politika ve Console yardım sayfaları).
  Gelenler:_
  - _**Kampanya bildirimleri:** onay, kapatma ve sessiz saat Play'e uygun. Ancak Play sistem bildirimlerini yalnız
    uygulamanın kendi özellikleri için kabul ediyor (havayolu fırsatı, oyun içi promosyon); bildirimle reklam sunmak
    ihlal örneği. Öneri (R1): bildirimde yalnız ViFi'nin kendi teklifleri ve karşılıksız öğrenci fırsatları; ücretli
    ya da sponsorlu işletme tanıtımı ve içerik derecelendirmesine aykırı kampanya (alkol, tütün, kumar) yok._
  - _**"Reklam içeriyor":** Premium ve satın alma teklifleri kapsam dışı. Sponsorlu kampanya akış içi native reklam
    sayılır. Öneri (R2): R1 kabul edilirse cevap "Hayır" kalır._
  - _**Konum:** arka plan beyanı gerekmez. Ancak yeni "en dar kapsam" kuralıyla `ACCESS_FINE_LOCATION` için Console
    beyanı Kasım 2026'da açılıyor, 27 Ocak 2027'de zorunlu oluyor. K1 ✅: yalnız `ACCESS_COARSE_LOCATION` (ürün sahibi
    onayı; Hat E `7c74b21`) → beyan gerekmez. Konum düğmesi zorunluluğu `targetSdk 37+`._
  - _**Diğer:** 2/2 onay sayfası Play'in onay kurallarını karşılıyor. Ticari statü beyanı Kasım 2026'da Console'a
    geliyor (4.1.0'dan bağımsız). Türk mevzuatı (ticari elektronik ileti) kapsam dışı bırakıldı; hukuki görüş
    önerildi._
- [x] **`NLG-04`** Mağaza girişi + 4.1.0 sürüm notu _(P1·S·Sonnet)_ — `PLAY-STORE-LISTING.md`: tam açıklamaya bildirim
  özellikleri, 4.1.0 sürüm notu (≤500). ✅ _Tamamlandı (2026-10-30, Opus). Metinler ürün sahibinin onayını bekliyor.
  Gelenler:_
  - _**Tam açıklama (4.1.0, 2188/4000):** yeni "BİLDİRİMLER" bölümü (sınav onayı ve bölüme yeni sınav; tek sayfa,
    görsel ve düğmeler; cihazlar arası okundu; onaylı kampanyalar, 22:00–09:00 yok, Hesap › Bildirim Ayarları). "DİĞER"deki
    bildirim maddesi buraya taşındı. Admin özellikleri mağaza metnine girmedi. 4.0.0 metni 4.0.0 yayını için duruyor._
  - _**Sürüm notu (4.1.0, 426/500, Türkçe).**_
  - _**Uygulama içeriği notları:** gizlilik (NLG-02), reklamlar (R1/R2), veri güvenliği (`NRL-05`). Açık konular:
    isteğe bağlı dolu Bildirimler ekran görüntüsü (`NQA-01` sonrası). "Reklam yok." cümlesi R1/R2'ye bağlı._

### Faz 2 — Entegrasyon ve Testler

- [x] **`NIT-01`** Canlı bağlama _(P0·M·Opus)_:
  - ~~`AppRoot`/`ViFiNavHost`: yönlendirici → kapılardan sonra ve modal yokken Inbox (`launchSingleTop`).~~ `PSH-03`'te
    yapıldı (30 Ekim, ürün sahibi kararı).
  - İzin akışı sunucusu `ArchiveHost`'ta (turdan sonra). Bildirim sayfasında onay tekrarı (700 ms, vurgu yoksa).
  - Sınav Ekle başarısından sonra `examWasSubmitted`. Hesap satırları ("Bildirim Ayarları", admin'e "Bildirim Gönder").
  - Mevcut modalların `ModalPresence`'a kaydı. `SessionTeardown` sıfırlamaları.
  - Live DI'da `Unavailable*` → gerçek sınıf; eski sınıfların silinmesi (cihaz kaydı `PSH-04`'te yapıldı).

  ⟶ Hat A…E ✅ _Tamamlandı (2026-10-31, Opus). Gelenler:_
  - _**Önceden yapılmış olanlar (tekrarlanmadı):** `SessionViewModel` cihaz kaydını (`PSH-04`), inbox'ı (`INB-01`) ve
    onay deposunu (`PRM-01`) oturumla başlatıyor; izin akışı sunucusu `ArchiveHost`'ta (`PRM-03`). Çıkışta
    yönlendirici, inbox ve onay deposu kendi oturum takipleriyle sıfırlanıyor, akışı sunucu kapatıyor (testleri
    hatlarda). Bunlar `SessionTeardown`'a ikinci kez eklenmedi; KDoc'u bu düzeni anlatıyor (cüzdan ve kilitler de
    aynı desende)._
  - _**Bildirim sayfasında onay tekrarı:** sayfa öne her gelişinde (`LifecycleResumeEffect`) `InboxViewModel`, sayfa
    **üstünde kayıtlı modal yokken 700 ms** öndeyse ve push vurgusu yoksa `inboxConsentReask()` çağırır (vade ve
    kampanya kontrolü koordinatörde). Sayfa duraklayınca (kapanınca, açtığı ekranın altında kalınca, uygulama arka
    plana gidince) deneme iptal olur. Android farkı: iOS tekrarı bildirim sayfasının üstünde gösteriyor; burada ortak
    sunucu gösterdiği için, sayfanın açtığı bir sheet (kilit, harita seçimi) bekleme süresini baştan başlatır.
    Emülatörde görülen hata bununla çözüldü: kilit sheet'inden Premium'a geçilince tekrar paywall'un üstüne
    çıkıyordu. Sebep, NavHost'un 700 ms'lik solma geçişinde sayfanın kompozisyonda kalmasıydı. Gösterilmeyen tekrar
    kullanılmış sayılmaz, sayfa yeniden öne gelince sunulur._
  - _**Sınav Ekle:** `AddExamScreen(onSubmitted)` eklendi (varsayılanı `onClose`). `AppRoot` önce formu kapatır, sonra
    `NotificationPromptViewModel.onExamSubmitted()` → `examWasSubmitted()` çağırır (iOS `AddExamView.onSubmitted`:
    `dismiss()` + Task). Sunucunun Activity kapsamlı VM'i `ArchiveHost`'ta bir kez alınıp iki yere veriliyor._
  - _**`ModalPresence` kayıtları:** tanıtım turu (`OnboardingScreen`; ilk açılış ve Hakkında'dan tekrar), isteğe bağlı
    güncelleme diyaloğu ve `ExamUnlockSheet` (Arşiv ve bildirim sayfası). Konum seçici `MAP-03`'te
    (`LocationPickerDialog`) zaten kayıtlıydı._
  - _**Hesap:** admin'e Moderasyon'un hemen altında "Bildirim Gönder" / "Duyuru ve kampanya gönder"
    (`account.notificationComposer`, megafon ikonu, silme sürerken pasif); "Bildirim Ayarları" herkese. Gönderim
    Geçmişi composer'daki "Geçmiş" düğmesiyle açılır (iOS'ta da Hesap'ta ayrı satırı yok)._
  - _**`ViFiNavHost` yuvaları:** `notificationSettings` → `NotificationSettingsScreen`; `notificationComposer` →
    `NotificationComposerScreen` ("Geçmiş" → `NotificationHistory`, Konum satırları
    `NotificationComposerLocationRows`); `notificationHistory` → `NotificationHistoryScreen`._
  - _**Live DI:** `NotificationAdminService` → `FirebaseNotificationAdminService`, `LocationPicker` →
    `GoogleLocationPicker` (`@Binds`). Silinenler: `UnavailableNotificationServices.kt` ve yalnız ona hizmet eden
    `NotificationAdminError.Unavailable` (iOS'ta da kullanılmıyor). `UnavailableLocationPicker` sağlayıcısız
    önizleme ve testlerin varsayılanı olarak kaldı. Mock'ta `LocationPicker` → `MockLocationPicker`, diğerleri Fake
    (`MockGraphTest` buna göre genişletildi)._
  - _**Manifest:** ana manifestte Hat A'nın FCM meta-data'sı ve servisi var; konum izni ve Maps anahtarı yalnız live
    manifestinde. Birleşik manifestler kontrol edildi: live'da yalnız `ACCESS_COARSE_LOCATION` (FINE ve
    `ACCESS_WIFI_STATE` yok), `geo.API_KEY` ve FCM satırları var. Mock'ta konum izni, anahtar ve Firebase başlatıcısı
    yok. `POST_NOTIFICATIONS` açıklaması güncellendi._
  - _**Kapsam dışı:** `ADM-04`'ün isteğe bağlı AndroidX `ExifInterface` notu (yeni bağımlılık gerektiriyor)._
  - _**Doğrulama:** `assembleDebug` + iki androidTest APK'sı; `lintLiveDebug` 0 hata, 56 uyarı (değişen dosyalarda
    yok). Birim testleri liveDebug 1080 / mockDebug 1064 / liveRelease 1080, hepsi geçti (+7:
    `InboxViewModelTest` +5 — 700 ms, vurgu, sayfadan çıkış, üstteki sheet, vade/kampanya —,
    `NotificationPromptViewModelTest` +2 — gönderim sonrası tekrar ve vadesizlik). Tablet emülatöründe (API 37) tam
    enstrümanlı paket mock 187/187, live 182/182 (`AccountContentTest` +3 admin satırları, `MockGraphTest` mock
    seçiciyi doğruluyor); son değişiklikten sonra bildirim sayfası/Hesap/graf sınıfları mock'ta 30/30. Mock
    uygulamada: tur + `vifi_notifications_prompt` (tur açıkken sayfa yok, atlanınca 1/2); `vifi_optional_update`
    (diyalog açıkken sayfa bekledi, "Daha Sonra" ile geldi); `vifi_consent_reask` (sayfa açılınca 2/2; push vurgusuyla
    açılışta gelmedi; kilit sheet'i açıkken bekledi, ✕ ile kapanınca geldi; sheet'ten Premium'a geçince paywall'da
    gelmedi, dönüşte geldi); `vifi_admin` (Hesap'ta Moderasyon › Bildirim Gönder › Bildirim Ayarları; composer, Konum
    → mock seçici → seçilen yer, Geçmiş, Bildirim Ayarları açıldı); `vifi_notifications_reask` + PDF'li gerçek
    gönderim (form kapandı, "Onaylanınca haber verelim mi?" geldi). Canlıya yazım yapılmadı; live derleme Maps
    anahtarsız (seçici "yapılandırılmamış" der; `NOPS-01`)._
- [x] **`NIT-02`** Birim testleri _(P0·M·Sonnet)_ — Hatların testleri + iOS test listelerinin (ek) tam karşılığı.
  `AnalyticsLimitsTest` (6 olay, ekran sayısı 15 → 18). Üç variant'ta tam paket. ⟶ `NIT-01` ✅ _Tamamlandı
  (2026-10-31, Opus). Gelenler:_
  - _**Eşleme:** ekteki 321 iOS birim testinin (A.11: 98, B.9: 96, C.8: 127) 312'sinin karşılığı var: 311'i JVM birim
    testi, `InboxActionButtonTests.buttons` Compose testi (`InboxContentTest`, metin eşlemesi private). Hatlar testleri
    iOS'la birebir yazmıştı. Android uyarlamaları: iki provisional testi tek "sessiz izin yok" testinde (`NDEC-01`);
    `bellAndDismiss` gezinmede (zil vurgusuz `Inbox()` açar, kapanış Back, vurgu rota argümanı). Test test tablo ortak
    not klasöründe (`nit-02-esleme.md`)._
  - _**Bilinçli eşlenmeyen 9:** `NotificationBadgeCounterTests`'in 5'i (`NDEC-04`: sayaç yok; ön plana gelişte temizleme
    `AppLifecycleHooksTest`'te); `NotificationPermissionTests` (2) ve `MapsLinksTests.googleChoice` (1) cihaza bağlı
    (`SDK_INT`/izin, Intent; JVM'de çalışmaz, `NIT-03`'e not bırakıldı); `signOutFailure` (1): Android'de
    `AuthService.signOut()` ve `DeviceRegistration.unregister` hata fırlatmaz._
  - _**Yeni 20 test (7 dosya),** iOS listesinde olmayan ve testsiz kalmış 4.1.0 sınıfları için:
    `FirestoreInboxReaderTest` (9: `announcements` 50 / `users/<uid>/inbox` 100 `createdAt` azalan, ayrıştırılamayan
    belge düşer, hata → `InboxError.Unavailable`, dinleyici hata ve iptalde sökülür, işaret `MetadataChanges.INCLUDE` +
    tahmini zaman, yalnız önbellekte eksik işaret yayılmaz, `markRead` tam olarak `{readAt: serverTimestamp}`, görsel
    URL'si yalnız `notificationImages/`), `FirebaseNotificationEventReporterTest` (2: `trackNotificationEvent` yükü,
    hata fırlatılır), `LegacyInboxReadStateTest` (2: 4.0.0 anahtarı `announcements.readIds`), `InboxDateTest` (2: "30 Eki
    2026"), `NotificationComposerTextTest` (2: "30 Eki 2026 14:30", admin yalnız Duyuru/Kampanya seçer),
    `DeepLinkViewModelTest` (2: push dokunuşu tur bitene kadar bekler), `SessionViewModelTest` (1: oturum izleyicileri
    bir kez başlar). Her biri geçici kod bozmasıyla kırılarak denendi, kod geri alındı._
  - _**`AnalyticsLimitsTest`:** `NCT-01`'de tamamlanmıştı (6 olay örneklerde, 18 ekranın hepsi listede); değişiklik
    gerekmedi. `InboxViewModelTest` (+5), `NotificationPromptViewModelTest` (+2), `AccountContentTest` (+3) `NIT-01`'den;
    boşluk yok._
  - _**Uygulama kodu değişmedi;** testler hata bulmadı._
  - _**Doğrulama:** `assembleDebug` başarılı. `lintLiveDebug` 0 hata, 56 uyarı (testlerde yok). Birim testleri liveDebug
    1080 → 1100 / mockDebug 1064 → 1084 / liveRelease 1080 → 1100 (her birinde +20), hepsi geçti. Emülatör kullanılmadı (`NIT-03`'te)._
- [x] **`NIT-03`** UI testleri + emülatör _(P0·M·Sonnet)_ — iOS `InboxUITests` (13), `NotificationPermissionUITests`
  (9), `NotificationComposerUITests` (6) ve `LocationCampaignUITests` (6) karşılıkları. Mock'ta uçtan uca; açık/koyu
  tema; **API 33+ ve API ≤32** emülatörde izin eşlemesi. ⟶ `NIT-01` ✅ _Tamamlandı (2026-10-31, Opus). Gelenler:_
  - _**iOS'un 34 UI testi birebir, mock'ta uçtan uca** (`androidTestMock`; `MainActivity` başlatma argümanlarıyla
    açılır, düğümler `testTag` ile bulunur, metinler string kaynağından doğrulanır). Mevcut içerik testleri
    (`InboxContentTest`, `NotificationComposerContentTest` …) durumsuz katmanı sürüyordu; bunlar gerçek NavHost,
    ViewModel ve mock grafiği üstünde aynı senaryoları koşar:_
    - _`InboxUiTest` 13 = `InboxUITests`. Sıra `a1`, `p-exam`, `p-paywall`, `p-exam-locked`, `a2`, `p-location`
      (listenin kendi anahtar → sıra eşlemesiyle), `a-expired` yok, `a1`'de düğme yok; sınav düğmesi ve kart sınavı
      açar; Premium; abone için Premium düğmesi yok (kart da tepkisiz); kilitli sınav kilit sheet'iyle açılır, coin
      yoksa "Sınav Ekle" dersiyle açılır, abone doğrudan açar; görsel yalnız `p-exam-locked`'ta; push `p-paywall`'u
      seçili açar, `does-not-exist` yalnız sayfayı açar; push'la açılan sayfa kapanınca Ana Sayfa, tekrar açılmaz.
      Android karşılıkları: `inbox.done` → üst çubuğun geri oku, `home.list` → `browse.list`; "sayfa kapanır" = sınav
      arşiv hiyerarşisinin üstünde açılır, geri arşivde yürür (`openExam`)._
    - _`NotificationPermissionUiTest` 9 + 2 = `NotificationPermissionUITests` (1/2 → 2/2 kabul ve ret → Bildirim
      Ayarları'nda "Açık" + anahtar; "Şimdi Değil"; 1/2 tur bitene kadar bekler; 2/2 ne kaydırmayla ne Geri ile kapanır;
      kampanya anahtarı ekrandan çıkıp girince korunur; reddedilmiş izinde "Ayarlar'ı Aç" var, "Bildirimleri Aç" yok;
      bildirim sayfasında onay tekrarı ret → bir daha sorulmaz, kabul → anahtar açık). `NDEC-01` farkı: "Şimdi Değil"den
      sonra iOS'taki "Sessiz" yerine "Sorulmadı" + "Bildirimleri Aç". NIT-01'de elle denenen iki akış da artık test:
      push vurgusuyla açılan sayfada onay tekrarı çıkmaz; `vifi_notifications_reask` + PDF'li gerçek gönderim → form
      kapanır, "Onaylanınca haber verelim mi?" (1/2) gelir._
    - _`NotificationComposerUiTest` 6 = `NotificationComposerUITests` (admin olmayana Bildirim Gönder ve Moderasyon yok;
      kitle 64 → 40 → 28 → 17 → Sakarya 24 → Kadın 9; onaylı gönderim Geçmiş'te "Gönderildi"; vazgeçince gönderim yok;
      zamanlanmış "Zamanlandı", satır menüsünden iptal → "İptal edildi" (iOS'taki kaydırma Android'de menü, `ADM-06`);
      sınav mock arşivde İTÜ › Mühendislik › Bilgisayar Mühendisliği › BLG 101 › 2020 Final yürünerek bağlanır)._
    - _`LocationCampaignUiTest` 6 = `LocationCampaignUITests` ("Konuma Git" ve kartın kendisi iki seçenek açar: Google
      Haritalar + başka harita uygulaması, `NDEC-03`; vazgeçince sayfa kalır; mock seçiciden yer → gönder ancak yer
      seçilince açılır, Geçmiş'te "Kampanya" + "Gönderildi"; Konum türü Kampanya'ya kilitler, başka eylem çözer; seçici
      iptalinde yer yok). NIT-01'in elle denenen Hesap › Bildirim Gönder › Konum › mock seçici › Geçmiş yolu bu paketin
      ilk composer testidir._
  - _**Test altyapısı** (`androidTestMock/.../testing/`, yeni bağımlılık yok): `MockApp.launch(…)` (argümanlar +
    `LaunchArgs` sıfırlama); `TestWorkManager` (test uygulaması Hilt'in `HiltTestApplication`'ı olduğu için
    WorkManager'ı kimse kurmuyordu ve Sınav Ekle açılamıyordu; süreç başına bir kez, her testin kendi Hilt işçi
    fabrikasıyla kurulur); `answerDocumentPicker` (belge seçiciye `Instrumentation.ActivityMonitor` ile örnek PDF
    döndürür, Espresso-Intents gerekmez); `assertNeverAppears` (gecikmeli bir şeyin çıkmadığını kör bekleme yerine
    yoklayarak doğrular)._
  - _**İzin eşlemesi (`NDEC-01`) cihazda:** `NotificationPermissionMappingDeviceTest` (`androidTestLive`, 5). Mock'ta
    izin sahte servisle simüle edildiği için eşleme yalnız live'daki gerçek `FirebaseNotificationService` +
    koordinatörle doğrulanabiliyor; onay kaydı ve sunum geçmişi bellekte, Firebase'e yazım yok. 13+: hiç sorulmamış →
    "Sorulmadı" ve ilk açılışta 1/2, uygulamanın sorusundan sonra → "Kapalı", izinli → "Açık" ve yalnız 2/2; **gerçek
    sistem diyaloğunda** (uygulamanın kendi istek yolu, "Reddet"e erişilebilirlik API'siyle basılır) ilk "Reddet" →
    "Kapalı" ama bir kez daha sorulabilir (`shouldShowRequestPermissionRationale`), ikinci "Reddet" → kesin, yalnız
    sistem ayarları kalır. ≤12: bildirimler açıkken "Açık", sistem hiç sorulmaz, 1/2 atlanır, yalnız 2/2 (iOS "consent
    catch-up"); **sistemin uygulama bildirim ayarlarından kapatılınca** ("Tüm ViFi bildirimleri" anahtarı test içinden
    çevrilir, sonda geri açılır) "Kapalı", yeniden sorma yolu yok, ilk açılışta hiçbir şey önerilmez. Her test yalnız
    kendi API düzeyinde koşar; 13+ testleri izni hiç verilmemiş cihaz ister (koşudan önce `adb shell pm revoke
    com.salticusteam.vifi android.permission.POST_NOTIFICATIONS`; test içinden geri almak süreci öldürür, diyalog
    cevaplarını silmek öldürmez ve test bunu kendisi yapar). NIT-02'nin cihaza bıraktığı izin kalemi ve iki açık dal
    bununla kapandı._
  - _**"Konuma Git" seçenekleri cihazda** (NIT-02'nin cihaza bıraktığı ikinci kalem, iOS `MapsLinksTests.googleChoice`):
    `MapsLaunchDeviceTest` (`androidTest`, iki flavor'da, 4). Başlatmalar dışarı çıkarken yakalanır, hiçbir şey açılmaz.
    Google Haritalar uygulamada açılır; uygulama yoksa (sistemin "böyle bir uygulama yok" cevabı canlandırılır) aynı
    bağlantı tarayıcıda; başka uygulama seçeneği `geo:` alan uygulamalara seçici açar, hiçbiri yoksa Google Haritalar
    web. Son dal cihaza bağlı (`assume`): tablette Haritalar kurulu olduğu için atlanır; bir kez Haritalar geçici olarak
    devre dışı bırakılarak (`pm disable-user`, sonra `pm enable`) koşturuldu ve geçti._
  - _**Uygulama kodu değişmedi;** testler gerçek bir hata bulmadı. `testTag` eklenmedi ya da değiştirilmedi. API 32
    koşusunun bulduğu iki test sorunu düzeltildi: (1) live paket hiç FCM jetonu almamış bir cihazda ilk testte
    çöküyordu (NIT-03'ten önce de; tablette jeton zaten vardı): live Firebase açılışta jeton alıp uygulamanın
    `ViFiMessagingService`'ine veriyor, servis `HiltTestApplication`'da Hilt bileşeni olmadan başlayamıyor. Ortak
    `HiltTestRunner` artık koşu boyunca bu servisi uygulama içinden (süreç korunarak) kapatıp sonda açar; FCM jetonu
    yine saklar, bir daha vermez. Bu, Hilt testinde servisin gerçek bağımlılıklarla canlıya cihaz kaydı yazmasını da
    önler. (2) `AccountContentTest.account_whileReverifying_showsTheCodeEntryAndError` telefon yüksekliğinde
    başarısızdı: hata satırı tembel listenin en altında, kaydırılmadan oluşturulmuyor; test önce o satıra kaydırır._
  - _**Doğrulama:** tablet emülatöründe (API 37) tam enstrümanlı paket mock 223/223 (187 + 36), live 185 (182 + 3; 184
    geçti, ≤12 testi API 37'de atlandı; "hiç sorulmamış" testi izin geri alınarak koştu ve geçti). Yeni 36 test koyu
    temada (`cmd uimode night yes`) 36/36 ve telefon yerleşiminde (1080×2400, geçici API 37 AVD; mevcut
    `Medium_Phone`'da Play'den kurulu 4.0.0 (19) olduğu için ona dokunulmadı) 36/36. `assembleDebug` + iki androidTest
    APK'sı derlendi; `lintLiveDebug` 0 hata, 56 uyarı. NIT-02 notundan sonra eklenen `MapsLaunchDeviceTest` ayrıca
    koşturuldu: mock 4 (3 geçti, 1 atlandı), Haritalar kapalıyken live 4 (3 geçti, 1 atlandı). **Son hâliyle tam
    paketler** (iki yeni izin testi ve iki test düzeltmesiyle): tablet API 37 mock 227 (226 geçti, 1 atlandı: Haritalar
    kurulu olduğu için "hiç harita uygulaması yok"), live 191 (188 geçti, 3 atlandı: ≤12'nin iki testi + aynı Haritalar
    testi); API 32 mock 227 (226 geçti, 1 atlandı), live 191 (187 geçti, 4 atlandı: 13+'nın üç testi + aynı Haritalar
    testi; uygulama verisi silinip FCM jetonu yokken koşturuldu, çökme yok). Son derleme: `assembleDebug` + iki
    androidTest APK'sı, `lintLiveDebug` 0 hata, 56 uyarı. Birim testlerine dokunulmadı. PSH-01'in kanal adları cihazda
    Türkçe görüldü ve ürün sahibine gösterildi: Duyurular, Kampanyalar ve fırsatlar, Sınav ve hesap bildirimleri (+
    yerel Sınav gönderimleri)._
  - _**API ≤32 (ürün sahibinin onayıyla, 2026-10-31):** `system-images;android-32;google_apis;arm64-v8a` r8 (Android
    12L). Bu Mac'te `cmdline-tools` yok ve ek bir şey indirilmemesi istendiği için `sdkmanager`'ın kullanacağı dosya
    (`arm64-v8a-32_r08.zip`, 1.569.899.980 bayt) aynı Google deposundan doğrudan indirildi; boyut ve SHA-1 deponun
    listesiyle aynı, lisansı (`android-sdk-arm-dbt-license`) API 37 imajıyla aynı ve bu Mac'te kabul edilmiş. AVD
    `ViFi_API_32` (telefon profili, Play Store'suz); mevcut API 37 AVD'lerine dokunulmadı. Sonuçlar Doğrulama'da._
- [x] **`NIT-04`** Belgeler _(P2·S·Haiku)_ — `CLAUDE.md` (durum, Bildirimler bölümü, LaunchArg tablosu, "Maps anahtarı
  commit'lenmez") ve `README.md`. ⟶ `NIT-01` ✅ _Tamamlandı (2026-10-31, Opus — ürün sahibinin "doğruluk hızdan önce"
  tercihiyle Haiku yerine). Kod değişmedi; olgular koddan (`app/build.gradle`, `LaunchArgs.kt`, `libs.versions.toml`,
  `feature/` paketleri, callable adları) ve bu belgeden doğrulandı. Gelenler:_
  - _**`CLAUDE.md`:** Durum bloğu (31 Ekim): 4.1.0 (20) `v-4.1.0`'da, hedefli bildirim sistemi, `NIT-02`/`NIT-03`
    sürüyor, Faz 3 `NOPS-01`'i bekliyor, yayın yalnız onayla; 4.0.0 satırı `RLS-08`'e göre düzeltildi (19 üretim
    incelemesine gönderildi, `RLS-09`…`RLS-11` açık). "Mevcut durum" tablosu: sürüm 4.1.0 / 20; "Bildirim" satırı push
    (kanallar, cihaz kaydı, tepsi, görsel, dokunuş → yönlendirici), bildirim sayfası (`announcements` ∪ `inbox`,
    `readAt`, 5 aksiyon, istatistik), izin 1/2 → 2/2 + 30 gün, Bildirim Ayarları, admin composer + Geçmiş + konum
    seçici; "Senaryo bayrakları" 15 LaunchArg'ın tamamı (metin değerli `vifi_open_notification <id>` dahil;
    `vifi_admin` artık bağlı); "Kütüphane" live'a özel Maps/Places/location sürümleri; "❌ Yeni kodda yapma" Maps
    anahtarı ve `ACCESS_FINE_LOCATION`. Kural 7'ye "Maps anahtarı commit'lenmez, yalnız `local.properties`". Firebase
    tablosu: Bildirim satırı (broadcast/fanout, `promo` süzgeci, sessiz saat, Android'in okuduğu/yazdığı yollar) ve
    Cloud Functions satırı (5 bildirim callable'ı; zamanlayıcı ve tetikleyiciler yalnız sunucuda). Proje Yapısı: tüm
    `feature/` paketleri (yeni `inbox`, `notifications`, `notificationsettings`, `notificationcomposer` +
    `locationpicker/`), `core/di`, güncel `res/`, `src/live` · `src/mock` ve Hat F belgeleri; shell komutlarına
    `MAPS_API_KEY` notu; "Son güncelleme"._
  - _Planın "Bildirimler bölümü" ve "LaunchArg tablosu" yeni başlık açılmadan mevcut "Bildirim" ve "Senaryo bayrakları"
    satırlarına işlendi (dosyaların düzeni korundu)._
  - _**`README.md`:** giriş ve bağlantılar (4.1.0 / 4.0.0 yol haritaları, Hat F belgeleri), SDK ve hedef API 36, isteğe
    bağlı `MAPS_API_KEY` (`local.properties`; değer yazılmaz, anahtarsız derleme geçer), Android 13+ ile 12 ve altı izin
    farkı, flavor tablosunda harita seçicisi ve tam senaryo listesi, `vifi_open_notification` örneği, Durum (özellikler,
    4.1.0 bildirim sistemi, yayın durumu). Eski "Play geliştirici hesabı kaldırılmış" notu kalktı._
  - _`NIT-02`/`NIT-03` test sayıları yazılmadı; birleştirmede §0 Durum Özeti'ne eklendi._
  - _**Kapsam dışı not:** `.claude/rules/architecture.md`'deki sürümlü doc comment kuralı "Bugünkü değer `4.0.0`"
    diyor; `versionName` artık 4.1.0 (yeni eklemeler `Added in 4.1.0` alıyor). Kural dosyası bu kalemde değiştirilmedi;
    birleştirmede 4.1.0'a düzeltildi._

### Faz 3 — Gerçek Ortam Doğrulaması (canlı backend; yayın değildir)

- [ ] **`NQA-01`** Canlı debug derlemesiyle uçtan uca _(P0·M·Opus)_ — Her canlı yazım **onayla**. Bakılacaklar:
  - Cihaz dokümanının şekli; eski kaydın silinmesi; `onDeviceWritten`.
  - Admin hesabıyla "Bana test gönder" (görsel + Sınav + Konum): ön planda / arka planda / kapalıyken push, kanal,
    BigPicture, dokunuş → sayfa + vurgu, geçmişte açılma/aksiyon sayısı.
  - Kampanya onayının yazımı ve `promo` süzgeci; zamanlanmış gönderim + iptal.
  - Otomatik bildirim (test gönderimi ret / onay); hesap silme sonrası sunucu temizliği.

  ⟶ `NIT-03`, `NOPS-01`, `NOPS-02`
- [ ] **`NQA-02`** iOS ↔ Android çapraz kontrol _(P1·S·Opus)_ — Android admin'in gönderdiği bildirim iOS'ta görünür (ve
  tersi). Aynı hesapla iki cihazda `readAt` senkronu ve kampanya tercihi. ⟶ `NQA-01`

### Faz 4 — Yayın  ⚠️ (ürün sahibiyle · her dış adım ayrı onay)

- [ ] **`NRL-01`** Önkoşul _(P0·S)_ — 4.0.0 (19) üretimde ve 4.0.0 `RLS-09`/`RLS-10` tamam (aynı anda iki inceleme
  açılmaz).
- [ ] **`NRL-02`** `v-4.1.0` dalının push'u _(P0·S·ürün sahibi)_ — Bu Mac'te yazma yetkili GitHub kimliği yok.
- [ ] **`NRL-03`** Release AAB 20 (4.1.0) _(P0·S·Opus)_ — `bundleLiveRelease` yükleme anahtarıyla ve **Maps anahtarıyla**
  (anahtar commit'lenmez). Kontrol edilecekler: sürüm, imza, izinler (yeni: konum ×2), R8 eşleme, dil bölmesi kapalı.
  **Onayla.** ⟶ `NRL-02`
- [ ] **`NRL-04`** Dahili test + Play'den kurulan sürümle **telefonda** duman testi _(P0·M)_ — Play Integrity / App Check,
  FCM token, push ve görsel, harita (Play imzası SHA-1'i anahtarda), satın alma yolları bozulmadı. 4.0.0'da emülatörde
  App Check 403 verdiği için **gerçek telefon** gerekir. ⟶ `NRL-03`
- [ ] **`NRL-05`** Veri güvenliği formu _(P0·S)_ — `NLG-01`'e göre Console'a girilir, üretimle incelemeye gider.
  ⟶ `NLG-01`
- [ ] **`NRL-06`** Mağaza girişi + politika beyanları _(P0·S)_ — `NLG-03`/`NLG-04` sonuçlarına göre. ⟶ `NLG-03`, `NLG-04`
- [ ] **`NRL-07`** Üretime yayın + incelemeye gönder _(P0·S)_ — %100, sürüm notu, yönetilen yayınlama kapalı. **Onayla.**
  ⟶ `NRL-04`…`06`
- [ ] **`NRL-08`** Yayın sonrası doğrulama _(P1·S)_ — Android vitals; `devices` kayıtlarının `platform: android` ile
  dolması; geçmişte Android alıcıların sayılara girmesi; `trackNotificationEvent` hataları. ⟶ `NRL-07`
- [ ] **`NRL-09`** Güncelleme önerisi _(P1·S)_ — `config/appUpdate/android.latestVersion` → `"4.1.0"`; canlı RTDB yazımı
  **onayla**, 4.1.0 %100 görünür olunca. ⟶ `NRL-08`

---

## 4. Ayrıntı

> iOS dosya yolları `ViFi-iOS/ViFi/` altındadır. Metinlerin, kimliklerin ve test adlarının tamamı ektedir; burada
> yalnız kararlar, Android'e özgü adımlar ve tuzaklar var.

#### `NDEC-01` — İzin akışının Android eşlemesi
- **iOS:** giriş sonrası ilk Home'da sessiz provisional izin; turdan sonra 1/2 (tam izin); izin varsa 2/2 (kampanya
  onayı); bağlamsal tekrarlar. Durum makinesi: `App/NotificationPromptCoordinator.swift`,
  `Core/Services/NotificationPromptHistory.swift`, `Features/Notifications/NotificationPermissionFlowModel.swift`.
- **Android eşlemesi:**
  - `prepare()` no-op'tur.
  - **13+:** "1/2 sunulmadı" + izin yok → turdan sonra 1/2. "Bildirimleri Aç" sistem diyaloğunu açar. Sonuç: izinli
    → 2/2 (onay kaydı yoksa); ret → "hayır" kaydı.
  - **12 ve altı:** `areNotificationsEnabled() == true` iOS'taki `authorized`'a karşılık gelir → 1/2 yok, onay kaydı
    yoksa 2/2 bir kez (iOS "consent catch-up"). Kullanıcı bildirimleri sistemden kapattıysa izin `DENIED` sayılır.
- **"Sistem diyaloğu gösterilebilir mi":** 13+'ta hiç istenmediyse ya da `shouldShowRequestPermissionRationale`
  true ise evet. Aksi hâlde (iki ret / "bir daha sorma" / 12 ve altında kapalı) düğme "Ayarlar'ı Aç" olur
  (`Settings.ACTION_APP_NOTIFICATION_SETTINGS`). Etkinliği mevcut `ForegroundActivityHolder` verir.
- **Ayarlar'daki durum metni:** "Sessiz" Android'de yoktur. `AUTHORIZED` → "Açık", `DENIED` → "Kapalı", `NOT_DETERMINED`
  → "Sorulmadı".

#### `NDEC-02` — Admin kapsamı
- Tam parite. Admin, iOS'taki gibi `admin: true` claim'iyle belirlenir; mevcut `AuthService.isCurrentUserAdmin()`
  önbellekli token okur, yeni claim yenileme ister. Sunucu her callable'da claim'i yeniden doğrular.
- Hesap'ta "Moderasyon"un yanına "Bildirim Gönder" (`account.notificationComposer`); "Geçmiş"e composer'ın üst
  çubuğundan girilir.

#### `NDEC-03` — "Konuma Git"
- **Google Haritalar:** `https://www.google.com/maps/search/?api=1&query=<lat>%2C<lng>[&query_place_id=<kodlu id>]`.
  Önce `setPackage("com.google.android.apps.maps")` ile denenir; `ActivityNotFoundException` olursa aynı URL tarayıcıda
  açılır.
- **Başka uygulama:** `geo:<lat>,<lng>?q=<lat>,<lng>(<kodlu ad>)` + `Intent.createChooser`. Hiç uygulama yoksa Google web.
- **Sayı biçimi:** iOS `MapsLinks` ile aynı. 6 ondalık, sondaki sıfırlar ve nokta atılır, `-0` → `0`, yerel ayara göre
  gruplama yok. Kodlama yalnız `A-Za-z0-9-._~`'yi korur.

#### `NDEC-04` — Rozet ve tepsi
- `NotificationBadge.clear()` her ön plana gelişte çalışmaya devam eder. KDoc düzeltilir: yerel `submissions`
  bildirimlerini de temizler; bu kabul edilen davranış.
- Kanallar `setShowBadge(true)`. `setNumber` ve üçüncü taraf rozet kütüphaneleri kullanılmaz.

#### `NOPS-01` — Google Cloud anahtarı
- **Adımlar** (`vifi-831a8`, proje no 441043948320):
  1. API'ler → "Maps SDK for Android"ı etkinleştir ("Places API (New)" zaten açık).
  2. Kimlik bilgileri → yeni API anahtarı. Uygulama kısıtı: Android uygulamaları → `com.salticusteam.vifi` + üç SHA-1
     (bu Mac'in debug'ı, yükleme anahtarı, Play uygulama imzalama — `BE-01`'de Firebase'e kayıtlı olanlar). API kısıtı:
     Maps SDK for Android + Places API (New).
  3. Places günlük kotası ve bütçe uyarısı iOS'takiyle aynı yaklaşım (iOS'ta günlük 300).
- **Tuzak:** Android anahtarı APK'nın içine gömülür ve görülebilir. Koruma kısıtlardır; anahtar depoya, loga ya da
  belgeye yazılmaz.

#### `NOPS-02` — Canlı durumun salt okuma denetimi
- Bakılacaklar (yazım yok):
  - `firebase functions:list` (13 function, europe-west1).
  - Kuralların depodaki `ViFi-iOS` `v-4.1.0` ile aynı olduğu.
  - Firestore'da `collectionGroup('devices')` içinde `token` alanı olmayan eski Android kayıtlarının sayısı.
  - `config/appUpdate/android`.
  - Emülatörün App Check debug anahtarının kayıtlı olduğu.
- **⚠️ Uyarı (4.0.0 §6'daki gibi):** `ViFi-iOS` master'da bildirim function'ları ve `purchaseExamUnlock` yok. Android
  tarafı deploy etmez; denetim yalnız okur.

#### `NCT-01` — Sözleşme
- **Sürüm:** `versionName "4.1.0"`, `versionCode 20` (ilk iş). Sürümlü doc comment'ler `// Added in 4.1.0 — …` olur.
- **Modeller:**
  - `core/model/AppNotification.kt` (tip düzeyinde tek yorum):
    - `NotificationKind {GENERAL, PROMO, SYSTEM}`; bilinmeyen değer → `GENERAL`.
    - `InboxItem(id, source: BROADCAST|PERSONAL, kind, title, body, imageStoragePath?, action?, createdAt: Instant?, expiresAt: Instant?)`;
      `isExpired(now)` sınır anını da süresi dolmuş sayar (`expiresAt <= now`).
    - `sealed interface NotificationAction` (`Location`, `Exam(examId, path: ArchivePath, kind: ExamKind)`, `Paywall`,
      `AddExam`, `Url`), `NotificationLocation`, `NotificationImage` (desen
      `^notificationImages/[A-Za-z0-9_-]{1,128}\.jpg$`).
  - `core/model/NotificationAdmin.kt`:
    - `NotificationDraft` + `Issue`, `NotificationAudience` (`All`, `Segment`).
    - `AudienceFacets` ağacı (`{total, universities[{id, name, count, female, male, faculties[...departments]}]}`).
    - `NotificationPreview`, `NotificationSendResult`, `NotificationStatus`, `NotificationStats`, `SentNotification`.
  - `core/model/NotificationConsent.kt`: `marketing`, kaynak `PROMPT|SETTINGS`, `CURRENT_VERSION = 1`. Analytics kaynağı
    `prompt|reask|settings` ayrı tutulur.
  - Hata tipleri (Türkçe `userMessage`): `InboxError`, `NotificationAdminError`, `NotificationSettingsError`.
- **Ayrıştırıcı:** `NotificationPayloadParser`, saf ve JVM'de test edilir. iOS `Core/Services/NotificationPayloadParser.swift`
  ile aynı:
  - Geçersiz id ya da boş başlık → doküman atlanır.
  - Bilinmeyen tür → `general`; bilinmeyen ya da bozuk aksiyon → aksiyonsuz kart.
  - `exam` aksiyonu: 5 elemanlı yol, son eleman `examId`, `examKind` `JPG`|`PDF`; `names` yalnız 5 elemansa kullanılır.
  - `url` yalnız https + host. Bool'lar sayı kabul edilmez. Tarih: `Timestamp`/`Date` ya da epoch milisaniye.
  - Push extras'tan `notificationId` (desen doğrulamalı).
- **Arayüzler + Fake'ler** (`core/data/repository/` + `fake/`):
  - `InboxReader`: `broadcastChanges()`, `personalChanges(uid)`, `readMarkerChanges(uid)`, `markRead(uid)`.
  - `DeviceRegistrar`: `register(uid, token)`, `unregister(uid)`. `DeviceRegistration` arayüzü (`unregister(uid)`) Hat C
    içindir.
  - `NotificationSettingsService`: `consentChanges(uid)`, `setMarketing(uid, enabled, source)`.
  - `NotificationEventReporter`: `report(event, notificationId)`.
  - `NotificationAdminService`: `audience()`, `preview(kind, audience)`, `send(draft)`, `cancel(id)`, `history()`,
    `uploadImage(jpeg, notificationId)`.
  - `LocationPicker`: `@Composable fun Content(initial, onPick, onCancel)`; flavor başına bağlanır, mock Google'a
    dokunmaz.
- **DI:** mock → Fake'ler (`MockGraphTest` Firebase'in başlamadığını doğrulamaya devam eder); live → `Unavailable*` yer
  tutucular (4.0.0 `BIL-06` deseni). Gerçek sınıflar `NIT-01`'de bağlanır.
- **Rotalar:** `ViFiRoute.NotificationSettings`, `NotificationComposer`, `NotificationHistory` + `ViFiNavHost`'ta boş
  yuvalar. Konum seçici tam ekran bir diyalogdur, rota değildir.
- **`ModalPresence`** (`core/navigation/`): iOS `ModalPresentation` karşılığı. Açık modalların sayacıdır; bir modal
  `DisposableEffect` ile kaydolur. `NotificationRouter` ve izin akışı sunucusu modal yokken sunar.
- **LaunchArg:**
  - `LaunchArgs.value(arg): String?` (metin extra'sı) + `OPEN_NOTIFICATION("vifi_open_notification")`.
  - Yeni boolean'lar: `NOTIFICATIONS_DENIED`, `NOTIFICATIONS_REASK`, `CONSENT_REASK`.
  - `NOTIFICATIONS_PROMPT` anlamı "taze kurulum" olur (izin sorulmamış + boş geçmiş).
  - Bayraklar burada tanımlanır; izin geçmişinin 31 gün önceki retle tohumlanması ve izin durumu önceliği
    (Prompt > Denied > Reask) geçmişin ve `NotificationService`'in sahibi olan `PRM-02`'de bağlanır.
- **Analytics:**
  - Olaylar: `notification_priming_shown|accepted|declined`, `marketing_consent_shown|accepted|declined`; tek parametre
    `source`.
  - Ekranlar: `NotificationSettings`, `NotificationComposer`, `NotificationHistory`.
  - `AnalyticsEventMapping` (exhaustive `when`) ve `AnalyticsLimitsTest` (indeksler, 18 ekran) birlikte güncellenir.
- **Metinler:** her hat kendi `strings_*.xml` dosyasını açar (§2). `NCT-01` metin eklemez.
- **Kabul:** her şey derlenir; hatlar Fake'lerle ekran yazabilir; `test` + `lintLiveDebug` + `MockGraphTest` geçer.
- **Uygulanan kararlar (2026-10-30; hatlar buna göre çalışır):**
  - **`NotificationDraft.Issue`** yalnız bir enum'dur. Türkçe metinler (iOS `Issue.message`) `ADM-03`'te
    `strings_notification_composer.xml`'e girer; sınır sayıları `%1$d` ile verilir. Yerelleştirme kuralı gereği:
    kullanıcı metni yalnız `*Error.userMessage`'ta koda gömülebilir.
  - **Akışlar:** Kotlin `Flow` hata durumunda iOS'taki `Result.failure` yerine `InboxError` /
    `NotificationSettingsError` fırlatır ve biter (mevcut `AnnouncementsReader` deseni). `readMarkerChanges`'te `null`
    yalnız sunucunun onayladığı "işaret yok" demektir.
  - **Vurgu:** vurgulanacak bildirim rota argümanıyla taşınır: `ViFiRoute.Inbox(highlightId: String? = null)`;
    `ViFiNavHost`'un `inbox` yuvası `highlightId` alır (mimari kural: alt durum olayla değil, nav arg ile). Mevcut
    çağrılar `Inbox()` oldu.
  - **Harita seçici:** `LocationPicker` (`@Composable Content`) Hilt'le bağlanır ve `MainActivity` tarafından
    `LocalLocationPicker` olarak sağlanır. İki flavor'da şimdilik `UnavailableLocationPicker` var (hemen iptal eder);
    `MAP-01…04` gerçek seçicileri bağlar.
  - **`ModalPresence`:** `MainActivity` tarafından `LocalModalPresence` olarak sağlanır. Bir modal
    `RegisterModalPresence()` çağırarak sayılır; `awaitNoModal()` sunumu bekletir. `NIT-01`'de mevcut modallar
    kaydolur.
  - **`DeviceRegistration`** (`unregister`, asla fırlatmaz) live'da şimdilik no-op'tur (bugün de kayıt silinmiyor);
    `PSH-04`'ün deposu bunu uygular.
  - **Analytics:** `NotificationPromptSource` (`prompt|reask|settings`) parametreli 6 ayrı olay.
  - **Değişmeyenler:** mevcut `NotificationService`, `FakeNotificationService` ve `notifications.prompted` mock
    davranışı `PRM-02`'ye kadar aynen kalıyor; yeni başlatma bayrakları yalnız tanımlı.

#### `PSH-01` — Kanallar + tepsi
- **Kanallar** (ad önerisi; `NIT-03`'te ürün sahibine gösterilir):

  | Kimlik | Ad | Açıklama |
  |---|---|---|
  | `general` | "Duyurular" | "Genel duyurular" |
  | `promo` | "Kampanyalar ve fırsatlar" | "Onay verdiğin öğrenci kampanyaları" |
  | `system` | "Sınav ve hesap bildirimleri" | "Sınavının onayı, reddi ve bölümüne eklenen yeni sınavlar" |

  Hepsi `IMPORTANCE_DEFAULT`, `showBadge = true`.
- `deleteNotificationChannel("announcements")`. Manifest
  `com.google.firebase.messaging.default_notification_channel_id` → `general`. Yerel `submissions` kanalı aynen kalır.
- **Tuzak:** Kanal kimliği bir kez oluşunca değişmez; ad ve açıklama sonradan güncellenebilir. Kullanıcı bir kanalı
  sistemden kapatabilir. Bu Android'e özgü bir yoldur: `promo`'yu sistemden kapatmak sunucudaki onayı değiştirmez, push
  gelmez ama kampanya sayfada görünür.

#### `PSH-02` — `ViFiMessagingService`
- **Ön plan** (`onMessageReceived`):
  - `notificationId` ve `kind` `message.data`'dan; `title` ve `body` `message.notification`'dan okunur. Kanal = `kind`,
    bilinmeyen → `general`.
  - Görsel `message.notification?.imageUrl` (imzalı V4 URL). **Kimliksiz** düz OkHttp istemcisiyle kısa zaman aşımıyla
    (≈8 sn) indirilir. Asla `StorageAuthInterceptor`'lı istemciden geçmez; kimlik bilgisi yabancı host'a gitmez. İnerse
    `BigPictureStyle`, inmezse `BigTextStyle`.
  - Bildirim id'si = `notificationId.hashCode()` (yoksa artan sayaç). `PendingIntent` `notificationId` extra'sı taşır;
    request code bildirim başına ayrı; `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`; `SINGLE_TOP | CLEAR_TOP`.
- **Arka plan:** sistem gösterir (kanal, görsel ve ikon manifestten). Dokunuş launcher intent'ini `data` anahtarlarıyla
  açar (`notificationId`, `kind`, `google.message_id`).
- **Token:** `onNewToken` → `DeviceRegistrationStore.tokenDidChange` + `subscribeToTopic("all")`. `all` aboneliği her
  açılışta da tekrarlanır (bugün `NotificationPromptViewModel` yapıyor; o sınıf `PRM-03`'te değişeceği için buraya
  taşınır). Abonelik izin ve oturumdan bağımsızdır.
- **Kabul:** `NotificationContent`/payload oluşturucu birim testleri (kanal seçimi, id, extras); 13+'ta izin yoksa
  sessizce gösterilmez.

#### `PSH-03` — `NotificationRouter` + dokunuş okuma
- **iOS:** `App/NotificationRouter.swift`, `HomeView.showPendingNotification`, `RootView` (`sessionDidChange`). Tüm
  kurallar ekte (§A.6).
- **Durum:** `pending: Request(notificationId?)?`, `highlightedId`. Yeni dokunuş bekleyeni değiştirir.
- **Oturum geçişleri:**
  - `→ SignedOut`: hepsi sıfırlanır.
  - `SignedOut → SignedIn`: bekleyen atılır (oturum yokken yapılan dokunuş hiçbir hesaba ait değildir).
  - `Unknown → SignedIn`: korunur (soğuk başlatma).
- **Teslim:** `ArchiveHost` var + tanıtım turu kapalı + `ModalPresence.awaitNoModal()` →
  `ViFiRoute.Inbox(highlightId = notificationId)` (`launchSingleTop`). Inbox zaten üstteyse yalnız vurgu taşınır:
  aynı hedefin yeni argümanla tek üst girdiye dönüştüğü emülatörde doğrulanır, gerekirse giriş değiştirilir. İzin akışı sayfası açıksa kapatılır (yanıtsız sayfa "hayır" sayılır); diğer
  modallar (kilit sheet'i, güncelleme diyaloğu) **kapatılmaz**, beklenir.
- **`MainActivity`:**
  - `onCreate` (`savedInstanceState == null`) ve `onNewIntent`'te `NotificationPayloadParser.notificationId(extras)`;
    `open_inbox` ya da `google.message_id` varsa id'siz de sayfa açılır.
  - Zil `openInbox()` vurgusuzdur; sayfa kapanınca vurgu temizlenir.
- **iOS `3ec75d4` hatasının dersi:** sayfa gerçekten gösterilmeden "açık" işaretlenmez, bekleyen istek askıda kalmaz.
- **Kabul:** iOS `NotificationRouterTests`'in 14 durumu; dokunuşun soğuk / sıcak / açıkken davranışı emülatörde.

#### `PSH-04` — Cihaz kaydı
- **iOS:** `Core/Services/DeviceRegistrationStore.swift`, `FirebaseDeviceRegistrar.swift`.
- **Kurulum id'si:** `UUID().toString().lowercase()`, DataStore `notifications.installationId.v1`. Desene uymazsa
  yeniden üretilir.
- **Yazım:** `set` (merge yok) tam 4 alan; `appVersion = BuildConfig.VERSION_NAME.take(32)`,
  `updatedAt = FieldValue.serverTimestamp()`.
  - Token ve uid birlikte olunca yazılır. Aynı `(token, uid)` çifti bellekte onaylıysa ya da uçuştaysa atlanır.
  - Kullanıcı değişince ve çıkışta sıfırlanır (bugünkü hatanın düzeltmesi).
  - Hata loglanır; sonraki tetikleyicide (token yenilemesi, giriş, açılış) yeniden denenir.
  - Her soğuk açılış yeniden yazar; `appVersion` güncellemesi böyle yakalanır.
- **`unregister(uid)`:** önce yerel durum sıfırlanır (uçuştaki token yenilemesi yeniden kaydetmesin). Ardından silme
  3 sn ile yarışır; asla hata fırlatmaz.
- **Eski kayıt:** `devices/<mevcutToken>` bir kez silinir (kural sahibine silme izni veriyor). DataStore bayrağı;
  başarısızlık sessizdir ve bir sonraki açılışta denenir. Eski token'lara ait diğer kayıtlar zararsızdır, sunucu atlar.
- **Kayıt izinden ve onaydan bağımsızdır:** FCM token izin olmadan da vardır; `promo` süzgeci sunucuda.
- **Kabul:** iOS `DeviceRegistrationStoreTests` (11) + `FirebaseDeviceRegistrarTests` (6) karşılıkları.

#### `INB-01` — `InboxStore`
- **iOS:** `Core/Services/InboxStore.swift`, `FirebaseInboxReader.swift`. 29 test ekte (§A.11).
- **Okuma:**
  - `announcements` `orderBy(createdAt, DESC).limit(50)` ve `users/<uid>/inbox` `limit(100)`, düz dinleyici. Sınırlar
    süre filtresinden önce uygulanır.
  - Hata: kaynak bir kez `failed` olur ve akış biter; kurtarma `retry()` ile.
  - Durum: iki kaynak da bekliyorsa `loading`; ikisi de hatalıysa `failed("Bildirimler yüklenemedi. Bağlantını kontrol
    edip tekrar dene.")`; aksi `loaded` + `hasPartialFailure` (tam olarak biri hatalı).
  - Kaynaklardan biri hatalıyken diğeri hâlâ bekliyorsa `loading`.
- **Okundu işareti:**
  - `settings/inbox` dinleyicisi `MetadataChanges.INCLUDE`. `isFromCache && !exists` atlanır.
  - Değer `ServerTimestampBehavior.ESTIMATE` ile okunur; yazım bekliyorken sayı yanıp sönmez.
  - Okunmamış yalnız işaret biliniyorsa ve `createdAt > readAt` (eşit = okundu). Tarihsiz öğe hiç okunmamış sayılmaz.
    Okuma hatasında 0.
  - **Yeni hesap:** sunucu "yok" diyorsa gözlem başına bir kez yerel `now` + `markRead` (sunucu zamanı). Başarılıysa
    eski `announcements.readIds` DataStore anahtarı silinir; hata olursa korunur. Okuma hatasında ve bekleyen işarette
    asla yazılmaz.
- **`markAllRead`:**
  - Koşul: kullanıcı var + işaret biliniyor + tarihli okunmamış var. En yeni `createdAt`'e göre tekrar koruması;
    başarısızlıkta koruma sıfırlanır.
  - Sayfa açılışında ve sayfa açıkken okunmamış sayısı > 0'a her çıkışta çağrılır.
- **Yaşam döngüsü:** oturum açılınca gözlem başlar (profil tamamlanmadan da); çıkışta `reset`; eski uid'nin değerleri
  yok sayılır (`uid` koruması).
- **Not:** bugünkü `InboxViewModel`'in "açılıştaki okunmamışları dondurma" davranışı kalkar; iOS'ta nokta canlı
  hesaplanır.

#### `INB-02` — Sınav kapısı
- **iOS:** `Features/Browse/ExamAccess.swift` (`ExamAccessResolver`), `Features/Notifications/InboxExamGate.swift`.
- **Karar sırası:**
  1. Yol 5 seviyeli değil → `unavailable`, istek yapılmaz.
  2. Abone ya da `unlockedIds` içinde → `open`, ders yüklenmez. Görüntüleyici silinmiş sınavı kendisi ele alır.
  3. Değilse `archiveRepository.items(dersYolu)`; `path` eşleşen ve dosyalı öğe yoksa `unavailable`.
  4. `ExamLock.freeExamIds` **filtresiz tam liste** üzerinden hesaplanır (dosyasız sınavlar da sayılır) →
     `locked(item)` / `open`.
- **Tepkiler:**
  - Kilitli → mevcut `ExamUnlockSheet`. Sonraki adım (aç / Sınav Ekle / Paywall) sheet kapandıktan sonra tek kez
    çalışır.
  - Bulunamadı → "Sınav açılamadı" başlıklı diyalog + "Bu sınav artık arşivde bulunmuyor." + "Tamam".
  - Hata → `userMessage`.
  - Aynı examId için ikinci dokunuş yok sayılır; düğmede yükleniyor göstergesi.
- **Açılış:** sayfa kapanır → `ViFiRouter.openExam(path, kind)` (yığını ata listeleriyle kurar, Geri arşivde yukarı
  çıkar).
- **Kabul:** `ExamAccessResolverTests` (8) + `InboxExamGateTests` (8) karşılıkları.

#### `INB-03` — Bildirim sayfası arayüzü
- **Metinler** (tamamı ekte, §A.4):

  | Yer | Metin |
  |---|---|
  | Başlık | "Bildirimler" (mevcut `ViFiTopAppBar` + geri oku kalır; iOS'taki "Bitti" Android'de yok) |
  | Boş durum | "Bildirim yok" / "Yeni bildirimler burada görünecek." |
  | Hata | `ErrorView` + "Tekrar Dene" |
  | Kısmi hata şeridi | "Bazı bildirimler yüklenemedi." + "Tekrar Dene" |
  | Düğmeler | "Sınava Git", "Premium'a Göz At", "Sınav Ekle", "Bağlantıyı Aç", "Konuma Git" |
  | `promo` etiketi | "Kampanya" |
  | Okunmadı noktası | `contentDescription` "Okunmadı" |
  | Görsel | `contentDescription` "Bildirim görseli" |

- **Kart:**
  - Görsel `StorageUrl.canonical(storagePath, bucket)` + mevcut Coil `ImageLoader` (kimlikli) ile yüklenir; yol yeniden
    doğrulanır. Yer tutucu ve hata ikonu var; yüksekliği ölçekli, ≤ 280 dp.
  - Düğme turuncu zeminde **siyah** metin (kontrast).
  - Düğme varsa kart, görsel ve düğme aynı aksiyonu yapar; yoksa kart etkisizdir. Abone için Premium düğmesi gizlenir ve
    kart etkisizleşir.
- **Vurgu:** `highlightId` rota argümanından gelir (`ViFiRoute.Inbox`, ViewModel'de `SavedStateHandle`). Listede
  çözülürse satır arka planı `primary` %18 olur, `animateScrollToItem` ile ortalanır ve `opened` bildirilir.
- **Aksiyonlar:** `paywall` → `Paywall` rotası; `addExam` → `AddExam` rotası (profilden ön-dolu); `url` → dış tarayıcı
  (`ACTION_VIEW`); `location` → `INB-04`; `exam` → `INB-02`.
- **Korunur:** mevcut "Bildirimler kapalı" izin şeridi (`inbox.permissionBanner`) ve zildeki sayı.
- **testTag'ler:** mevcut `inbox.list`, `inbox.row.<id>`, `toolbar.inbox`, `state.*` korunur. Yeniler:
  `inbox.row.<id>.action`, `.image`, `inbox.partialFailure`, `inbox.retry`, `inbox.location.google|other|cancel`.
  `inbox.unread.<id>` okunmadı noktasına taşınır.

#### `INB-04` — "Konuma Git"
- **Alt sayfa:** başlık "Konumu hangi uygulamada açmak istersin?". Altında ad (adres varsa ikinci satırda). Seçenekler:
  "Google Haritalar" (birincil), "Başka bir harita uygulaması", "Vazgeç". Yükseklik içeriğe göre; seçim sheet'i kapatır,
  "Vazgeç" sayfayı açık bırakır.
- **Kabul:** iOS `MapsLinksTests` (9) Android URL'lerine uyarlanır (Apple yerine `geo:`).

#### `INB-05` — İstatistik
- **iOS:** `Core/Services/NotificationEventTracker.swift`. Tekrar koruması anahtarı `"<uid>|<event>|<id>"`. Onaylanan
  anahtarlar DataStore'da (`notifications.reportedEvents.v1`) en çok 500 tutulur; uçuştaki anahtar tekrar gönderilmez.
  `@ApplicationScope`'ta gönder ve unut. Callable `trackNotificationEvent {notificationId, event}`; yanıt yok sayılır.
- **Kabul:** `NotificationEventTrackerTests` (11).

#### `PRM-01` — Onay deposu
- **iOS:** `Core/Services/NotificationSettingsStore.swift`, `FirebaseNotificationSettingsService.swift`.
- **Yazım:** `set` (merge yok) `{marketing, source, version: 1, updatedAt: serverTimestamp}`. İstemci tarihi kural
  tarafından reddedilir; kayıt silinemez.
- **Okuma:**
  - Canlı dinleyici, `ESTIMATE`; `includeMetadataChanges` yok.
  - Yalnız `marketing` bool ise cevap sayılır. Kayıt yoksa "cevaplanmadı" ve kampanyalar kapalı.
  - Hata → `unavailable`, otomatik yeniden başlatma yok.
- **`setMarketing`:** kullanıcı yoksa `false` döner. Değer anında gösterilir. Yazım 4 sn ile yarışır: hata → eski değer
  geri gelir, `false`; zaman aşımı → kaydedildi sayılır (yazım kuyrukta, yerel önbellekte var).
- **Kabul:** `NotificationSettingsStoreTests` (15) + `FirebaseNotificationSettingsServiceTests` (4).

#### `PRM-02` — Geçmiş + koordinatör
- **Geçmiş anahtarları** (DataStore, cihaz başı; çıkışta silinmez — iOS ile aynı kabul):
  - `notifications.primingOffered.v1`, `permissionDeclinedAt.v1`, `permissionReaskShown.v1`, `consentOffered.v1`,
    `consentDeclinedAt.v1`, `consentReaskShown.v1`.
  - Tekrar gecikmesi 30 gün. Yalnız ilk "hayır" saklanır. Tekrar, "gösterildi" bayrağı false + ret kaydı var +
    ≥30 gün olunca "vadesi gelmiş" olur ve gösterilene kadar öyle kalır.
- **4.0.0 göçü:** `notifications.prompted == true` → `primingOffered = true`. İzin yoksa `permissionDeclinedAt = şimdi`
  (iOS legacy back-fill: 30 gün 4.1.0'ın ilk açılışında başlar).
- **Tetikleyiciler** (iOS `NotificationPromptCoordinator`):
  - İlk sunum turdan sonra.
  - Onay yakalama: izinli + `!consentOffered` + kayıt 5 sn içinde yüklendi + cevaplanmadı.
  - `examWasSubmitted()`: vadesi gelmiş + izin yok.
  - `inboxConsentReask()`: vadesi gelmiş + 2 sn içinde yüklendi + kampanya kapalı; işletim sistemi izni kontrol edilmez.
  - Kuyruk tek yuvalıdır; yeni istek bekleyeni değiştirir.
- **Kabul:** `NotificationPromptCoordinatorTests` (21) + `NotificationPromptHistoryTests` (8). Sağlayıcı gerektiren
  durumlar Android izin modeline uyarlanır.

#### `PRM-03` — İzin/onay sayfaları
- **Akış:** `ModalBottomSheet`, iki sayfa aynı sheet'te. Ekranlar, metinler ve kimlikler ekte (§B.2–B.3); özet:
  - **1/2:**
    - Başlık "Hiçbir şeyi kaçırma" (tekrarda "Onaylanınca haber verelim mi?").
    - 3 fayda satırı: "Bölümüne yeni sınav gelince ilk sen haberdar ol", "Gönderdiğin sınav onaylanınca coin'in
      yüklendiğini gör", "Önemli duyurular".
    - Düğmeler "Bildirimleri Aç" / "Ayarlar'ı Aç" ve "Şimdi Değil".
    - Kaydırarak kapatma "hayır" sayılır; analytics gönderilmez.
  - **2/2:**
    - Başlık "Öğrenci fırsatları"; gövde ve iki satır ("Gece 22:00–09:00 arası asla.", "İstediğin an Hesap'tan kapat.").
    - Düğmeler: "Evet, fırsatları bildir" / "Hayır, teşekkürler". Hiçbiri önceden seçili değildir ve iki cevap da kayıt
      yazar.
    - Kaydırma ve geri tuşuyla kapatma **kapalı** (`skipPartiallyExpanded`, `confirmValueChange = false`,
      `BackHandler`); yalnız kayıt hatasından sonra açılır.
    - Hata metni: "Tercihin kaydedilemedi. Bağlantını kontrol edip tekrar dene."
- **Yerleşim:** mesajın tamamı varsayılan yazı ölçeğinde kaydırmadan görünür (iOS `5fa1d3c` dersi); büyük yazı
  ölçeğinde gövde kayar, düğmeler altta sabit kalır.
- **Analytics:** hangi olayın ne zaman gönderileceği ekteki tabloda (§B.6).
- **Kaldırılanlar:** mevcut `NotificationPrompt` AlertDialog'u ve `notificationPrompt.*` tag'leri. Yeni tag'ler
  `notifications.priming.*`, `notifications.consent.*`.
- **Kabul:** `NotificationPermissionFlowModelTests` (15).

#### `PRM-04` — Bildirim Ayarları
- **Satır:** "Bildirim Ayarları" / "Bildirim izni ve kampanya tercihin" (`account.notificationSettings`). Silme sürerken
  pasif.
- **Ekran:**
  - Satır "Bildirim izni" + durum metni.
  - Altta not: kapalıysa "Bildirimler kapalı. Açmak için Ayarlar'a git.", sorulmadıysa "Bildirim izni henüz verilmedi."
  - Düğme "Bildirimleri Aç" (analytics ve geçmiş yok) ya da "Ayarlar'ı Aç".
  - Anahtar "Kampanya ve fırsat bildirimleri"; altındaki açıklama ekte.
  - Okunamıyorsa: "Tercihin şu an okunamıyor. Bağlantını kontrol edip bu sayfayı tekrar aç."
  - Kayıt hatasında anahtar geri döner ve hata gösterilir.
- **Analytics:** anahtar değişimi `marketing_consent_accepted|declined(source: settings)`; ret burada tekrar kuralını
  **kurmaz**.
- **İzin tazeleme:** `LifecycleResumeEffect` ile.
- **Kabul:** `NotificationSettingsViewModelTests` (9).

#### `PRM-05` — Çıkış kancası
- `AccountViewModel.signOut()` askıya alınır ve `isSigningOut` koruması alır. Sıra: `unregister` → `authService.signOut()`.
- Silme akışı: `unregister` → `deleteProfile` → `deleteAccount`. Yeniden doğrulama iptal edilirse kayıt silinmiş kalır;
  bir sonraki girişte yeniden yazılır.
- `ProfileCompletionScreen`'deki "Çıkış Yap" da aynı kancayı kullanır.
- **Kabul:** iOS `AccountSignOutCleanupTests` karşılıkları (`signOutRunsHookFirst`, `signOutIgnoresDoubleTap`,
  `signOutWhileDeleting`).

#### `ADM-01` — Admin servisi
- **iOS:** `Core/Services/FirebaseNotificationAdminService.swift`. Callable gövdeleri ve sonuç şekilleri ekte (§C.2–C.3).
- **Zaman aşımı:** `getHttpsCallable("sendNotification").withTimeout(300, SECONDS)`. Büyük fan-out'ta 70 sn'lik istemci
  varsayılanı `deadline-exceeded` verir. Aynı id'yle yeniden deneme sunucudaki mevcut sonucu döner (idempotent).
- **Görsel:** yol `notificationImages/<id>.jpg`. Önce `getMetadata()`; nesne varsa yükleme atlanır (üzerine yazma
  yasak; yeniden denemeyi güvenli yapan budur). Yalnız `OBJECT_NOT_FOUND`'da `putBytes(bytes, contentType image/jpeg)`.
- **Hata eşleme** (iOS tablosu, ek §C.3.9): `permission-denied`/`unauthenticated` → "Bu işlem için yetkin yok.";
  iptalde `failed-precondition` → "Bu bildirim gönderildiği için artık iptal edilemez."; diğer `invalid-argument` /
  `failed-precondition` → sunucunun Türkçe mesajı olduğu gibi; ağ → çevrimdışı mesajı; Storage hatası → "Görsel
  yüklenemedi; bildirim gönderilmedi. Tekrar dene."

#### `ADM-02` — Composer ViewModel
- **Kurallar** ek §C.2–C.3'te. Android'e özgü noktalar:
  - Sayaç ve sınırlar `trim()` sonrası `codePointCount` ile hesaplanır (iOS grafem sayar; sunucu kod noktası sayar).
  - Doğrulama sırası: başlık, mesaj, kitle, konum, url, sınav, görsel, zaman, geçerlilik. Dokunulmamış formda boş
    başlık/mesaj uyarısı gösterilmez.
- **Taslak id'si:**
  - Hata ve görsel yükleme hatasında korunur.
  - Başarıda, `failed`/`canceled` sonucunda ve yükleme denemesinden sonra görsel değişince/kaldırılınca yenilenir.
  - Test gönderimi her dokunuşta kendi id'sini alır.
- **Sonuç:** `sent`/`sending` → "Bildirim gönderildi. Alıcı sayısı: N." + form sıfırlanır. `scheduled` → "Bildirim
  <zaman> için zamanlandı." (sayı yok). `failed` → "Bildirim gönderilemedi. Ayrıntı için geçmişe bak." + yeni id.
- **Sessiz saat istemcide hesaplanmaz;** sunucunun `scheduled` yanıtı gösterilir.
- **Kabul:** `NotificationDraftTests` (9), `NotificationComposerViewModelTests` (10), `SendFlowTests` (15),
  `AttachmentTests` (13), `KindLockTests` (7), `LocationTests` (8).

#### `ADM-03`…`ADM-06` — Composer arayüzü, görsel, sınav seçici, geçmiş
- **Bileşenler:** Material3 `SingleChoiceSegmentedButtonRow` (Tür, Kitle, Cinsiyet), `ExposedDropdownMenuBox` (Üniversite
  / Fakülte / Bölüm; seçenek "<ad> · <n> kişi"), `DatePickerDialog` + `TimePicker` (cihaz saat dilimi; sunucuya epoch ms),
  `AlertDialog` onayı.
- **Onay diyaloğu:** başlık "Bildirim gönderilsin mi?" ya da "Bildirim zamanlansın mı?"; gövde "N kişiye gönderilecek;
  M cihaza anlık bildirim (push) iletilecek."; düğmeler "Gönder"/"Zamanla" ve "Vazgeç".
- **Sınav seçici:** mevcut Browse bileşenleri/`ArchiveRepository` yeniden kullanılır, yeni arşiv kodu yazılmaz. Satır
  kimlikleri `notificationComposer.exam.row.<ad>`.
- **Geçmiş:** satır menüsünden "İptal et" (onay yok, iOS paritesi); satır başına bir iptal uçuşta olabilir.
  `notCancelable` → mesaj + listeyi yenile.
- **Geçmiş durum rozetleri:** "Zamanlandı", "Gönderiliyor", "Gönderildi", "Başarısız", "İptal edildi". Renkler tema
  token'larından gelir (sabit renk yok).
- **Kabul:** `NotificationHistoryViewModelTests` (7), `FirebaseNotificationAdminServiceTests` (8); UI testleri
  `NIT-03`'te.

#### `MAP-01` — Bağımlılıklar + anahtar
- **Kütüphaneler:** `com.google.maps.android:maps-compose`, `com.google.android.gms:play-services-maps`,
  `com.google.android.libraries.places:places` (`Places.initializeWithNewPlacesApiEnabled`),
  `com.google.android.gms:play-services-location`. Hepsi `liveImplementation` (4.0.0 Billing gibi).
- **⚠️ Sürüm uyumu MAP-01'de doğrulanır.** Proje Kotlin 2.0.21 + Compose BoM 2024.10.01; 4.0.0'da `billing-ktx` 9.x Kotlin
  2.2 stdlib istediği için alınamamıştı. Aynı kontrol burada da yapılır; gerekirse uyumlu eski sürüm seçilir.
- **Anahtar:**
  - `local.properties` `MAPS_API_KEY=` → live `manifestPlaceholders.mapsApiKey` →
    `<meta-data android:name="com.google.android.geo.API_KEY">`, ayrıca `buildConfigField` (Places için).
  - Değer boşsa, kırpılmamışsa ya da `${` ile başlıyorsa kullanılamaz → seçici "Harita anahtarı yapılandırılmamış"
    görünümüne geçer ve harita hiç oluşturulmaz (iOS `GoogleMapsConfiguration`).
- **SDK başlatma:** seçici ilk açıldığında; uygulama açılışında değil.
- **Manifest:** ~~`ACCESS_COARSE_LOCATION` + `ACCESS_FINE_LOCATION`~~ → yalnız `ACCESS_COARSE_LOCATION` (yalnız ön plan;
  arka plan izni yok). _Karar 2026-10-30, ürün sahibi (Hat F K1): Play'in FINE gerekçe beyanı gerekmesin._
- **Kabul:** anahtarsız live debug çökmez; R8'li release'te harita açılır; APK/AAB boyut farkı not edilir;
  `GoogleMapsConfigurationTests` (6) karşılığı.

#### `MAP-02` — Seçici ViewModel
- **iOS:** `Features/NotificationComposer/LocationPicker/LocationPickerViewModel.swift`. 44 test ekte (§C.8).
- **Kamera:** varsayılan Türkiye (39.0, 35.2, zoom 5); düzenlemede mevcut konum (zoom 16).
- **Arama:**
  - Autocomplete: `regionCode "TR"`. Görünür yarıçap 0–50 km ise dairesel **bias** (filtre değil). _Karar 2026-10-30,
    ürün sahibi (Hat F K1): bias yalnız admin haritayı kendisi yerleştirdikten sonra; cihaz konumuyla otomatik odakta
    yalnız `regionCode`._
  - Oturum jetonu 150 sn'de ve her yer getirmeden sonra yenilenir.
  - Yer getirme alanları yalnız `DISPLAY_NAME`, `LOCATION`, `FORMATTED_ADDRESS`, `ID` (fazlası daha pahalı ücret
    katmanı).
- **POI dokunuşu:** ad tek satıra indirilir ve 120'ye kesilir; adres yalnız `FORMATTED_ADDRESS` ile arkada gelir; yeni
  seçim eskisini ezer; yazılmış adres kazanır.
- **Cihaz konumu:**
  - Seçici açılınca, izin hiç sorulmadıysa **bir kez** istenir.
  - İzin varsa `FusedLocationProviderClient.getCurrentLocation`. Doğruluk > 1000 m ise zoom 12, değilse 16.
  - Admin haritayı yerleştirdiyse (sürükleme, arama, POI, düzenleme) odaklanılmaz.
  - Reddedildiyse not: "Konum izni kapalı; harita bulunduğun yere odaklanmaz."
  - Konum **saklanmaz, loglanmaz, sunucuya gitmez**.

#### `MAP-03` / `MAP-04` — Seçici arayüzü ve mock
- **Arayüz:** `GoogleMap` (pusula, döndürme ve eğme kapalı; konum noktası izin varsa) + ortada sabit iğne. Kamera
  hareketi bitince merkez = seçim. POI dokunuşu `onPOIClick`.
- **Mock:** sabit yer ve "Bu konumu seç" / "İptal" (`locationPicker.confirm|cancel`, `locationPicker.mock.name`).

#### `NLG-01`…`NLG-04` — Belge ve beyanlar
- **Veri güvenliği:** iOS gizlilik etiketinin karşılığı (ek §B.8):
  - Kullanıcı kimliği, cihaz kimlikleri ve "diğer" (profil segmentleri, onay kaydı) → uygulama işlevi + analytics +
    **reklam veya pazarlama** (kendi kullanıcılarımıza, üçüncü tarafla paylaşım yok).
  - Uygulama etkinliği (okundu işareti, istatistik) → uygulama işlevi + analytics.
- **Konum:** yalnız cihazda kullanılır, gönderilmez. Ancak Places isteği görünür harita alanını bias olarak Google'a
  gönderir; bunun "toplama" sayılıp sayılmadığı Google'ın Places/Maps SDK beyan rehberine göre değerlendirilir. Karar
  ürün sahibinindir.
- **Politika:** onaylı, kapatılabilir kampanya bildirimleri; uygulama içi tanıtım yok. "Reklam içeriyor" beyanının
  gerekip gerekmediği Play'in güncel tanımına göre kontrol edilip ürün sahibine sunulur.

#### `NIT-01` — Canlı bağlama
- **`AppRoot`:**
  - `SessionViewModel` cihaz kaydını ve inbox/onay gözlemini oturumla başlatır.
  - `ArchiveHost` izin akışı sunucusunu (turdan sonra, `ModalPresence` boşken) barındırır; yönlendirici teslimi
    (`PushTapDelivery`) `PSH-03`'te, sunucu (`NotificationPromptHost`; push dokunuşu ve çıkış akışı kapatır, izin
    sheet'i `ModalPresence`'a kayıtlı) `PRM-03`'te bağlandı.
  - Inbox'a onay tekrarı tetikleyicisi eklenir (700 ms sonra, vurgu yokken `coordinator.inboxConsentReask()`).
  - Sınav Ekle başarısı → `coordinator.examWasSubmitted()`.
- **Mevcut modalların `ModalPresence` kaydı:** tanıtım turu `Dialog`'u, isteğe bağlı güncelleme diyaloğu,
  `ExamUnlockSheet`, konum seçici. (İzin sheet'i `PRM-03`'te, "Konuma Git" sheet'i `INB-04`'te kaydedildi.)
- **Hesap:** "Bildirim Ayarları" satırı herkese; "Bildirim Gönder" yalnız admin'e (Moderasyon'un yanında).
- **`SessionTeardown`:** yönlendirici, inbox deposu ve onay deposu sıfırlanır; izin akışı kapatılır (sunucu çıkışta
  zaten kapatıyor, `PRM-03`). _NIT-01'de doğrulandı: depolar kendi oturum takipleriyle sıfırlanıyor; tekrar
  eklenmedi._
- **Silinenler:** — (`NotificationPrompt` AlertDialog'u `PRM-03`'te silindi, VM'i sunucuya dönüştü; `InboxDeepLink` `PSH-03`'te;
  `PushTokenRegistration`, `DeviceTokenStore`, `FirestoreDeviceTokenStore` `PSH-04`'te; `Announcement*`,
  `AnnouncementReadPreferences` `INB-01`'de silindi.)

#### `NIT-02` / `NIT-03` — Testler
- iOS test adları ve kapsamları ekte (A.11, B.9, C.8); her biri Android karşılığıyla eşlenir.
- UI testleri mock'ta, `testTag` ile yazılır. Gerekli senaryolar: `vifi_open_notification p-paywall` / `a1` /
  `does-not-exist`, `vifi_notifications_prompt` + `vifi_show_onboarding`, `vifi_notifications_denied`,
  `vifi_notifications_reask`, `vifi_consent_reask`, `vifi_admin`, `vifi_subscribed`, `vifi_no_coins`.
- **Mock örnek bildirimleri** iOS'takiyle aynı kimliklerle: `a1`, `a2`, `a-expired`, `p-exam`, `p-paywall`,
  `p-exam-locked`, `p-location`. Görünür sıra `a1`, `p-exam`, `p-paywall`, `p-exam-locked`, `a2`, `p-location`.

#### `NQA-01` / `NQA-02` — Gerçek ortam
- **Canlı yazım örnekleri** (her biri onayla): test bildirimi (`notifications/<id>` + kendine push), onay kaydı, cihaz
  kaydı.
- **Bir gönderimi onaylamak arşive sınav ekler;** bu nedenle otomatik bildirim yolu önce **ret** ile denenir. Onay testi
  ancak ürün sahibinin seçtiği bir gönderimle yapılır.
- **Hesap silme denemesi** yalnız bir test hesabıyla yapılır (test numarası depoya yazılmaz).

#### `NRL-*` — Yayın
- **`NRL-01`:** 4.0.0 incelemesi sürerken 4.1.0 gönderilmez; aynı anda iki inceleme açılmaz.
- **`NRL-04`:** 4.0.0'da atlanan "Play'den kurulan sürümle telefonda deneme" bu sürümde zorunlu. Bildirim yolu ancak
  gerçek FCM + Play Integrity ile doğrulanır.
- **`NRL-09`:** erken yazılırsa güncellemeyi henüz Play'de göremeyen kullanıcılara boşuna öneri çıkar.

---

## 5. Riskler ve Açık Noktalar

1. **Yayındaki Android sürümleri (3.1.0 / 4.0.0) kişisel bildirim almaz.**
   - Cihaz kayıtları 30 Ekim'den beri reddediliyor ve uygulama `users/<uid>/inbox`'ı okumuyor.
   - Genel duyurular topic'le gelmeye devam ediyor.
   - Segment, kişisel ve kampanya bildirimleri Android kullanıcısına 4.1.0'a kadar ulaşmaz. Sınav onay/ret bildirimleri
     de buna dahil; Android 4.0.0'da bunlar zaten yoktu.
2. **Push dokunuşu ve intent.** Arka planda sistemin gösterdiği bildirime dokunuşta `data` anahtarlarının launcher
   intent'ine gelmesi FCM davranışıdır. Soğuk açılış, sıcak açılış (`onNewIntent`) ve görev yığını cihazda
   doğrulanmalı (`PSH-03`, `NQA-01`).
3. **Android 13+ izin diyaloğu en çok iki kez çıkar.** 30 günlük tekrar sistem diyaloğunu açamayabilir → "Ayarlar'ı Aç"
   (`NDEC-01`).
4. **Kanal ile onay arasındaki fark.** Kullanıcı `promo` kanalını sistemden kapatabilir: push gelmez ama sayfada
   kampanya görünür. Tek otorite sunucudaki onaydır; kanal kapalıyken ayarlar ekranında not gösterilebilir (opsiyonel).
5. **Maps anahtarı ve maliyet.** Anahtar APK'da görünür; koruma paket + SHA-1 + API kısıtıdır. Places kotası iOS ile
   ortaktır ve yalnız adminler kullanır.
6. **Veri güvenliği ve politika.** Kampanya hedeflemesi "reklam veya pazarlama" amacını gerektirir; Maps SDK
   beyanları; Places bias'ı (`NLG-01`, `NLG-03`).
7. **Büyük fan-out zaman aşımı.** `sendNotification` 300 sn sürebilir; istemci zaman aşımı yükseltilir, yeniden deneme
   idempotent (`ADM-01`).
8. **Cihaz başı izin geçmişi.** Aynı telefonda ikinci hesap ilk sunumu görmez (iOS ile aynı kabul).
9. **Bildirimden açılan sınav.** `ExamViewer` kilit kontrolü yapmaz; kapı her zaman navigasyondan **önce** uygulanmalı
   (`INB-02`).
10. **Ortak backend.** Android deploy etmez. `ViFi-iOS` master'dan functions deploy edilirse bildirim function'ları ve
    `purchaseExamUnlock` silinmeye önerilir (4.0.0 §6 uyarısı genişledi).
