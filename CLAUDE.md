# ViFi Android

Üniversitelerin geçmiş sınavlarını tek yerde toplayan sınav arşivi uygulamasının Android istemcisi.

> **Durum (31 Ekim 2026):** Uygulama **Kotlin + Jetpack Compose + MVVM + Hilt** üzerinde (eski Java/Activity kodu
> silindi, FM-23). **Phone Auth (+90)**, oturum + profil kapısı, **canlı arşiv** (isimli `vifi` Firestore), profil
> tamamlama/düzenleme, Hesap ekranı (çıkış + hesap silme), sınav görüntüleyicileri, coin cüzdanı + sınav kilidi, ViFi
> Premium, bildirimler (FCM + bildirim sayfası), onboarding, Hakkında, force update kapısı ve analytics hazır; ana akışlar
> canlıda emülatörde doğrulandı. ViFi 3.1.0 (17) 28 Ekim 2026'da Play'de üretime çıktı; 4.0.0 (19) 29 Ekim'de üretime
> alınıp incelemeye gönderildi, 29 Ekim 19:14'te onaylanıp %100 yayına çıktı (`RLS-08`); yayın sonrası kontroller
> temiz, yalnız gerçek telefonla deneme açık (`RLS-09`), `RLS-10`/`RLS-11` açık. iOS v3.1.0 parite yol
> haritasının 223 kalemi tamam (29 Ekim 2026).
> **4.1.0 (20)** `v-4.1.0` dalında geliştiriliyor: iOS `v-4.1.0`'ın **hedefli bildirim sistemi** (bildirimleri sunucu
> gönderir; tek bildirim sayfası, görsel + aksiyon, izin + kampanya onayı, Hesap › Bildirim Ayarları, admin "Bildirim
> Gönder" + Geçmiş + konum seçici). Sözleşme (`NCT-01`), beş kod hattı (A…E), belge/beyan hattı (F) ve canlı bağlama
> (`NIT-01`), birim ve UI testleri (`NIT-02`/`NIT-03`; izin akışı API 32 ve 37 emülatörde) ve Android Maps anahtarı
> (`NOPS-01`) tamam; canlı doğrulama (Faz 3: `NQA-01` uçtan uca, `NQA-02` iOS ↔ Android çapraz) 31 Ekim'de geçti.
> Sırada yayın (Faz 4, `NRL-01`…`NRL-09`); yalnızca ürün sahibinin açık onayıyla, her dış adım ayrı onayla yapılır.
> **4.0.0 iş listesi** (ViFi Premium paritesi, fazlı ve paralel hatlı): [`ANDROID-4.0.0-ROADMAP.md`](ANDROID-4.0.0-ROADMAP.md).
> **4.1.0 iş listesi** (iOS `v-4.1.0` hedefli bildirim sistemi paritesi, `v-4.1.0` dalı; backend canlıda, Android backend
> yazmaz): [`ANDROID-4.1.0-ROADMAP.md`](ANDROID-4.1.0-ROADMAP.md) + iOS olgu eki [`ANDROID-4.1.0-DETAIL.md`](ANDROID-4.1.0-DETAIL.md).
> **iOS v3.1.0 paritesi** hedefinde ilerleme ve kalan işler
> [`ANDROID-PARITY-ROADMAP.md`](ANDROID-PARITY-ROADMAP.md) (özet) ve [`ANDROID-PARITY-DETAIL.md`](ANDROID-PARITY-DETAIL.md)
> (ayrıntı) dosyalarındadır.

---

## ⚠️ Claude İçin Kritik Talimatlar

### HER İSTEK ÖNCESİ ZORUNLU ADIMLAR

1. **Önce `.claude/` klasörünü tara** — güncel mimari kurallar ve kod üretim şablonları oradadır.
2. **İlgili `.claude/rules/*.md` ve `.claude/skills/*/SKILL.md` dosyalarını oku.**
3. **Kuralları doğrula**, ancak bundan sonra işleme başla.

```bash
# Önce tara:
find .claude/rules .claude/skills -name "*.md"
# Ardından ilgili dosyaları oku:
cat .claude/skills/<skill>/SKILL.md
cat .claude/rules/<kural>.md
```

**Neden:** Proje standartları `.claude/rules/` ve `.claude/skills/` içinde yaşar ve sürekli güncellenir. Eski bilgiye
göre (ör. eski Java deseni ya da iOS alışkanlığı) kod üretmek hataya yol açar. Yeni kod **hedef mimariye** (Kotlin/Compose/MVVM)
göre yazılır; eski Java/Activity ekranlar yol haritasına göre göç ettirilir.

### Daima geçerli kurallar

1. **Türkçe cevap ver.** İş bittiğinde ne yapıldığını ve nasıl doğrulandığını kısa bir "bitti" özetiyle bildir.
2. **Belirsizlikte sor, varsayma.** İstenmeyen değişiklik yapma, mevcut yapıyı bozma.
3. **Bu depo göç halinde.** Büyük bir göç adımına (AndroidX geçişi, Firestore cutover, auth değişikliği) tek başına
   girişme — önce ilgili yol haritası kalemini ve bağımlılıklarını teyit et.
4. **Dosya oluşturmadan önce kontrol et** (`find app/src -name '<Ad>.*'`); kopya oluşturma. Yeni `Activity` eklersen
   `AndroidManifest.xml`'e kaydet.
5. **Her değişiklikten sonra doğrula:** `./gradlew assembleDebug`, `./gradlew lint`, ilgili testler (`./gradlew test`);
   sonucu olduğu gibi raporla.
6. **Canlı Firebase'e dokunmadan önce onay al.** Android, iOS ile **aynı** `vifi-831a8` projesini paylaşır. Kurallar ve
   Cloud Functions **ortaktır ve `ViFi-iOS/firebase/` altında yaşar**; Android yeni backend yazmaz, yeniden kullanır.
   **Onaylı istisna (4.0.0, `DEC-04`):** `purchaseExamUnlock`'a Google Play dalı ortak depoda yazılır (iOS yolu
   değişmez); deploy'u (`SRV-03`) ayrıca açık onay ister.
   Kural yayını / canlı veri yazma-silme / App Check / Auth ayarı / `google-services.json` yenileme yalnızca açık onayla.
7. **Sır ve kişisel veri yazma.** Test hesapları/numaraları, App Check debug token'ları, kişisel e-postalar, servis
   hesabı anahtarları ve imzalama keystore'ları commit'e girmez. Yeni sır ekleme; `local.properties`/keystore commit'leme.
   **Maps anahtarı commit'lenmez:** admin konum seçicisinin Google Maps/Places anahtarı yalnız yerel `local.properties`'e
   (`MAPS_API_KEY=`, git dışı) girer; değeri depodaki hiçbir dosyaya, commit'e, loga ya da rapora yazılmaz.
8. **Modeli işe göre seç.** Mekanik işler için hafif modeller; güvenlik kuralları, auth/App Check, eşzamanlılık,
   mimari ve canlı ortam işleri için Opus.

---

## Hızlı Referans

### Hedef mimari (yeni kod)

| Özellik | Değer |
|---------|-------|
| **Dil** | Kotlin 2.0+, JDK 17 |
| **UI** | Jetpack Compose + Material3 (yeni ekranlarda XML/Fragment yok) |
| **Mimari** | MVVM — `@HiltViewModel` + tek `data class XUiState` + `StateFlow<XUiState>` |
| **DI** | Hilt (constructor injection) |
| **Gezinme** | Navigation-Compose (`NavHost`, route'lar) |
| **Async** | Coroutines + Flow (`viewModelScope`, `Dispatchers.IO`, Task→`.await()`, RTDB→`callbackFlow`) |
| **Veri** | Firebase bir `interface Repository` arkasında: `Firebase…Repository` (canlı) / `Fake…Repository` (preview/test) |
| **Hatalar** | `sealed class …Error : Exception()` + Türkçe `userMessage` |
| **Metin** | `strings.xml` + `stringResource`; positional format args (`%1$s`); Türkçe-only |
| **Tema** | `MaterialTheme.colorScheme` / `typography` (hardcoded renk/font yok) |
| **Test** | JUnit + `coroutines-test` + Turbine (birim), Compose UI test (arayüz), Fake tercih |

### Mevcut durum

| Konu | Değer |
|---|---|
| **Dil/UI** | Kotlin + Compose (eski Java/XML ekranlar FM-23'te silindi); tek `app/MainActivity` + `AppRoot` + `ViFiNavHost` |
| **Uygulama** | `com.salticusteam.vifi`, `versionName 4.1.0` (iOS ile aynı seri), `versionCode 20`, `compileSdk/targetSdk 36`, `minSdk 24` |
| **Flavor** | `live` (canlı vifi-831a8) · `mock` (örnek veri, Firebase başlamaz, yalnız debug) |
| **Veri (arşiv)** | Live `FirestoreArchiveRepository` → named `vifi` Firestore `catalogNodes`/`exams` (`BE-15`); mock örnek veri |
| **Sınav dosyaları** | `RemoteFileLoader` (OkHttp + ID token/App Check) → `cacheDir/ExamFiles` (Files + Coil Images); görsel pager + platform `PdfRenderer`; `FLAG_SECURE` ekran koruması; hesap silmede `purge()` |
| **Coin** | `wallets/<uid>` + `users/<uid>/unlocks` canlı okuma, `ExamLock` (en eski 2 sınav ücretsiz, 100 coin), `unlockExam` callable (europe-west1), `config/rewards` android→ios fallback |
| **Bildirim** | **Push:** native FCM, topic `all` + cihaz kaydı `users/<uid>/devices/<kurulumId>` (`{token, platform: 'android', appVersion, updatedAt}`; çıkış ve hesap silmede silinir); kanallar `general`/`promo`/`system` (= sunucunun `kind`'ı) + yerel `submissions`; bildirim başına tepsi kaydı, görsel (BigPicture), dokunuş (`FLAG_IMMUTABLE`) → `NotificationRouter` → kapılardan sonra bildirim sayfası + vurgu. **Bildirim sayfası:** `announcements` (son 50) ∪ `users/<uid>/inbox` (son 100), okundu = `settings/inbox.readAt` (cihazlar arası); görsel + 5 aksiyon (Sınava Git — arşivin kilit kuralıyla, Premium'a Göz At, Sınav Ekle, Bağlantıyı Aç, Konuma Git); açılma/aksiyon istatistiği. **İzin:** turdan sonra 1/2 → `POST_NOTIFICATIONS` → 2/2 kampanya onayı (`settings/notifications`); 12 ve altında yalnız 2/2; retten en erken 30 gün sonra bir kez tekrar (izin: sınav gönderiminden sonra, onay: bildirim sayfasında). Hesap › Bildirim Ayarları (izin durumu + kampanya anahtarı). **Admin:** Hesap › "Bildirim Gönder" (tür, kitle, görsel, aksiyon, zamanlama, önizleme + onay, "Bana test gönder") + Gönderim Geçmişi (zamanlanmışı iptal) + konum seçici (Google Maps/Places yalnız live'da, yalnız yaklaşık konum; mock'ta sabit örnek yer) |
| **Giriş** | Phone Auth `+90` (`BE-04`) → `SessionStore` → `ProfileGate` (`ProfileStore`, RTDB `users/<uid>/profile`) → arşiv |
| **Senaryo bayrakları** | Debug intent extra'ları (`core/util/LaunchArgs.kt`). Boolean (`--ez <ad> true`), mock'ta: `vifi_signed_out`, `vifi_profile_incomplete`, `vifi_force_update`, `vifi_optional_update`, `vifi_delete_requires_reauth`, `vifi_no_coins`, `vifi_show_onboarding`, `vifi_subscribed`, `vifi_admin` (Moderasyon + Bildirim Gönder); bildirim izni: `vifi_notifications_prompt` (hiç sorulmadı → 1/2), `vifi_notifications_denied` (reddedildi, sorulamaz → "Ayarlar'ı Aç"), `vifi_notifications_reask` (31 gün önce ret → sınav gönderiminden sonra tekrar), `vifi_consent_reask` (31 gün önce onay reddi → bildirim sayfasında tekrar). Metin değerli (`--es <ad> <değer>`): `vifi_open_notification <id>` (o bildirimin push'una dokunulmuş gibi açılır; mock örnekleri ör. `p-paywall`, `p-location`, `p-exam-locked`). Live debug'da `vifi_phone_auth_testing` (yalnız Console test numaralarıyla) |
| **Kütüphane** | AndroidX, AGP 8.13.2 + Gradle 8.14.3, `libs.versions.toml`, Kotlin 2.0.21 + Coroutines, Hilt 2.52, Compose BoM 2024.10.01, Navigation 2.8.3, Firebase BoM 33.5.1, Timber; yalnız live: Maps Compose 6.5.3, Play Services Maps 19.0.0, Places 4.1.0 (Places API New), Play Services Location 21.3.0 (Kotlin 2.0 / Compose BoM 2024.10.01 ile derlenen en yeni sürümlere sabit; Kotlin yükseltmesinde birlikte ele alınır) |
| **Build** | Gradle Groovy DSL (plugins DSL), `google-services` 4.4.2, Java 17 hedefi + desugaring; Gradle'ı JDK 17–21 ile çalıştır (Android Studio JBR 25 desteklenmez); release R8 küçültmeli |
| **❌ Yeni kodda yapma** | `com.android.support`/`jcenter`, AdMob, XML ekran (Compose kullan), sır/keystore commit'i, Maps anahtarı commit'i (yalnız `local.properties`), mock'a canlı Firebase ya da Google Maps binding'i, `ACCESS_FINE_LOCATION` (seçici yalnız yaklaşık konum ister, K1 kararı) |

---

## Proje Yapısı

```
ViFi-Android/
├── CLAUDE.md                     # Bu dosya (Claude talimatları)
├── ANDROID-PARITY-ROADMAP.md     # iOS 3.1.0 parite — özet master liste
├── ANDROID-PARITY-DETAIL.md      # Aynı kalemlerin adım/kabul-kriteri/gotcha ayrıntısı
├── ANDROID-4.0.0-ROADMAP.md      # 4.0.0 iş listesi (ViFi Premium) — fazlar, paralel hatlar, ayrıntı
├── ANDROID-4.1.0-ROADMAP.md      # 4.1.0 iş listesi (hedefli bildirim sistemi) — fazlar, paralel hatlar, ayrıntı
├── ANDROID-4.1.0-DETAIL.md       # 4.1.0 eki: iOS v-4.1.0 olgu listeleri (metinler, kimlikler, kurallar, test adları)
├── PLAY-DATA-SAFETY.md           # Play Veri güvenliği envanteri (4.1.0 bildirim verileri dahil)
├── PLAY-STORE-LISTING.md         # Mağaza metinleri ve sürüm notları
├── PLAY-POLICY-4.1.0.md          # 4.1.0 Play politikası kontrolü (kampanya bildirimleri, konum)
├── PRIVACY-POLICY-4.1.0.md       # 4.1.0 gizlilik / hesap silme / kullanım koşulları metin önerisi
├── .claude/                      # ⚠️ Claude yapılandırması & kod üretim bilgisi
│   ├── settings.json             # İzinler
│   ├── rules/                    # Proje kuralları (okuma zorunlu — aşağıdaki tablo)
│   ├── skills/                   # Kod üretim şablonları (SKILL.md + templates/patterns/checklists)
│   └── commands/                 # Slash komutları
├── app/
│   ├── build.gradle              # Modül build (SDK, bağımlılıklar)
│   ├── google-services.json      # Firebase yapılandırması (vifi-831a8)
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/salticusteam/vifi/   # app/ (MainActivity, AppRoot) · core/ (model, data, di, designsystem,
│   │   │   # navigation, util, notifications) · feature/<ad>/ (home, browse, examviewer, addexam, moderation,
│   │   │   # premium, login, profile, account, about, onboarding, update, inbox, notifications,
│   │   │   # notificationsettings, notificationcomposer [+ locationpicker/] — Screen, ViewModel, UiState)
│   │   └── res/                  # drawable/, mipmap-*/, values/ (strings_*.xml özellik başına), values-night/, xml/
│   └── src/live/ · src/mock/     # Flavor bağlamaları; live: Google Maps/Places seçici + konum izni
│                                 # (src/live/AndroidManifest.xml), mock: sahte servisler, mock seçici, örnek veri
└── gradle/ + gradlew             # Gradle wrapper
```

**Hedef paketleme (yeni Kotlin kodu):** `feature/<ad>/` (screen Composable, `XViewModel`, `XUiState`, liste öğesi) +
`core/` (data/repository, model, util, designsystem, navigation).

> Güvenlik kuralları, emulator testleri ve Cloud Functions bu depoda **değil**; ortak backend `ViFi-iOS/firebase/`.

---

## Kurallar (`.claude/rules/`)

| Kural | Konu |
|-------|------|
| `architecture.md` | MVVM katmanları, tek yön veri akışı, Hilt, yaşam döngüsü/bellek güvenliği, versioned doc comment |
| `firebase-data.md` | Firebase veri erişimi: Repository deseni, `await()`/`callbackFlow`, DTO→model, ortak backend, App Check |
| `ui-components.md` | Compose/Material3 bileşenleri, tutarlı geri butonu, durum görünümleri, tema, semantics/testTag |
| `viewmodel-rule.md` | ViewModel kuralları: `@HiltViewModel`, `StateFlow<UiState>`, event kanalı, Context tutma yasağı |
| `testing.md` | Birim (JUnit + coroutines-test + Turbine) ve Compose UI testi kuralları |
| `localization.md` | `strings.xml`, `stringResource`, positional format args, Türkçe-only |
| `date-handling.md` | Backend ISO 8601; `java.time` + merkezi helper; call-site'ta formatter kurma yasağı |

## Skill'ler (`.claude/skills/`)

| Task | Skill |
|------|-------|
| Yeni ekran oluştur (Compose + VM + UiState) | `new-screen/SKILL.md` |
| ViewModel ekle | `new-viewmodel/SKILL.md` |
| Tam feature modülü scaffold | `new-feature/SKILL.md` |
| Liste öğesi (Compose item / RecyclerView) | `new-list-item/SKILL.md` |
| Birim test yaz | `generate-unit-tests/SKILL.md` |
| Compose UI testi yaz | `generate-ui-tests/SKILL.md` |
| Fake/mock üret | `generate-mocks/SKILL.md` |
| PR review yap | `review-pr/SKILL.md` |
| Figma tasarımından ekran | `figma-to-feature/SKILL.md` |

## Komutlar (`.claude/commands/`)

`check-architecture` · `document-module` · `generate-mocks` · `scaffold-feature`

---

## Komutlar (shell)

```bash
# Flavor'lar: live (canlı vifi-831a8) · mock (örnek veri, Firebase hiç başlamaz; yalnız debug — iOS -ViFiMockData)
# Gradle'ı JDK 17–21 ile çalıştır: export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
# Admin konum seçicisi (live): local.properties'e MAPS_API_KEY= (git dışı, commit'lenmez); anahtarsız derleme de geçer,
# seçici "Harita anahtarı yapılandırılmamış" der
./gradlew assembleLiveDebug        # Canlı debug APK (assembleDebug her iki flavor'ı derler)
./gradlew assembleMockDebug        # Mock debug APK
./gradlew lintLiveDebug            # Lint (rapor: app/build/reports/lint-results-liveDebug.html)
./gradlew test                     # Birim testler (tüm variant'lar)
./gradlew connectedLiveDebugAndroidTest connectedMockDebugAndroidTest   # Enstrümanlı testler
./gradlew signingReport            # SHA-1/SHA-256 (App Check / Phone Auth kaydı için)

# Kural yayını — YALNIZCA onayla; ORTAK backend, ViFi-iOS/firebase/ içinde:
cd ../ViFi-iOS/firebase && firebase deploy --only database,storage --project vifi-831a8 --account <proje-sahibi>
```

---

## Firebase

Android ve iOS **aynı** `vifi-831a8` projesini paylaşır (ayrıntı: `.claude/rules/firebase-data.md` ve `ViFi-iOS/firebase/`).

| Konu | Değer |
|---|---|
| **Proje** | `vifi-831a8` (proje no `441043948320`, Blaze; bucket `vifi-831a8.appspot.com`) |
| **Android kaydı** | `com.salticusteam.vifi` — debug (bu Mac), Play uygulama imzalama ve yükleme anahtarının SHA-1/SHA-256'ları kayıtlı (`BE-01`, 28 Ekim 2026); Play Integrity sağlayıcı kaydı `REL-01`'de |
| **Arşiv** | named `vifi` Firestore `catalogNodes`/`exams` (`BE-05`, `BE-15`); RTDB `Universitiess` artık okunmuyor |
| **Giriş** | Phone Auth `+90` (`BE-04`); canlı test için Console'daki test numarası + `111111` |
| **App Check** | Storage/RTDB/Auth/Firestore enforce **AÇIK** (Firestore 28 Ekim 2026, `BE-03`) → geçerli token yoksa **403** / `PERMISSION_DENIED`; Cloud Functions'ta enforce kapalı |
| **Bildirim** | Native FCM (Parse kaldırıldı, `BE-11`), `POST_NOTIFICATIONS`. Gönderimi sunucu yapar (`deliverNotification`): **broadcast** (`general`/`system`, herkes) → `announcements/<id>` + topic `all`; **fanout** (segment, tüm kampanyalar, kişisel, admin, test) → `users/<uid>/inbox/<id>` + kayıtlı cihaz token'ları. `promo` yalnız `settings/notifications.marketing == true` olanlara gider; sessiz saatler 22:00–09:00 (İstanbul). Android (isimli `vifi`) okur: `announcements`, `users/<uid>/inbox`; yazar: `users/<uid>/settings/inbox` (`readAt`), `users/<uid>/settings/notifications` (onay), `users/<uid>/devices/<kurulumId>`; admin okur: `notifications` (geçmiş), yükler: Storage `notificationImages/<id>.jpg` (JPEG ≤ 1 MiB). Kurallar 30 Ekim 2026'dan beri canlıda (ortak depo) |
| **Cloud Functions (ortak, europe-west1)** | Android **çağırır** (`BE-08/09`), yeni function yazmaz: `unlockExam`, `recordLoginDiagnostic`, `purchaseExamUnlock`; 4.1.0 bildirim callable'ları — admin: `getNotificationAudience`, `previewNotification`, `sendNotification` (istemci zaman aşımı 300 sn), `cancelNotification`; +90 kullanıcı: `trackNotificationEvent` (açılma/aksiyon). İstisna: `purchaseExamUnlock` Google Play dalı (4.0.0 `DEC-04`/`SRV-01`). Bildirim zamanlayıcısı ve tetikleyicileri (`dispatchScheduledNotifications`, `onDeviceWritten`, `onSubmissionModerated`, `onSubmissionCreated`, `onAccountDeleted`) yalnız sunucuda |

---

## 💬 Claude'a Nasıl Prompt Yazmalı?

### ✅ Doğru prompt

```
Hesap ekranı için yeni bir Compose ekranı oluştur.
NOT: Önce .claude/rules/ ve .claude/skills/new-screen/SKILL.md dosyalarını oku, kurallara uy;
testTag ver, birim/UI testi ekle, ./gradlew assembleDebug ile derle.
```

### ✅ Yol haritası kalemiyle

```
ANDROID-PARITY-ROADMAP.md'deki BE-11'i uygula (Parse kaldır, native FCM).
Önce .claude/ ve ANDROID-PARITY-DETAIL.md'deki adımları özetle, belirsizse sor, sonra yap.
```

### ❌ Kaçınılacak prompt

```
Projeyi modernize et / Firestore'a taşı.
```
*(Çok geniş ve kırıcı; fazlı göç gerektirir. Yol haritasından tek kalem seç, bağımlılıklarını teyit et, küçük adımlarla ilerle.)*

---

**Son güncelleme:** 31 Ekim 2026
**Proje:** ViFi Android (`com.salticusteam.vifi`, v4.1.0 (20) geliştirmede — hedefli bildirim sistemi; giriş, arşiv, görüntüleyiciler, coin, Premium, bildirim, profil ve Hesap hazır)
