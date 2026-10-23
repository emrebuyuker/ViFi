# ViFi Android

Üniversitelerin geçmiş sınavlarını tek yerde toplayan sınav arşivi uygulamasının Android istemcisi.

> **Durum (24 Ekim 2026):** Uygulama **Kotlin + Jetpack Compose + MVVM + Hilt** üzerinde (eski Java/Activity kodu
> silindi, FM-23). **Phone Auth (+90)**, oturum + profil kapısı, **canlı arşiv** (isimli `vifi` Firestore), profil
> tamamlama/düzenleme, Hesap ekranı (çıkış + hesap silme), sınav görüntüleyicileri, coin cüzdanı + sınav kilidi, bildirimler
> (FCM + Inbox), onboarding, Hakkında, force update kapısı ve analytics hazır; ana akışlar canlıda emülatörde doğrulandı.
> Google Play geliştirici hesabı Kasım 2024'ten beri kaldırılmış durumda — release/mağaza işleri `REL-01`'de.
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
   Kural yayını / canlı veri yazma-silme / App Check / Auth ayarı / `google-services.json` yenileme yalnızca açık onayla.
7. **Sır ve kişisel veri yazma.** Test hesapları/numaraları, App Check debug token'ları, kişisel e-postalar, servis
   hesabı anahtarları ve imzalama keystore'ları commit'e girmez. Yeni sır ekleme; `local.properties`/keystore commit'leme.
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
| **Uygulama** | `com.salticusteam.vifi`, `versionName 2.5`, `versionCode 16`, `compileSdk/targetSdk 35`, `minSdk 24` |
| **Flavor** | `live` (canlı vifi-831a8) · `mock` (örnek veri, Firebase başlamaz, yalnız debug) |
| **Veri (arşiv)** | Live `FirestoreArchiveRepository` → named `vifi` Firestore `catalogNodes`/`exams` (`BE-15`); mock örnek veri |
| **Sınav dosyaları** | `RemoteFileLoader` (OkHttp + ID token/App Check) → `cacheDir/ExamFiles` (Files + Coil Images); görsel pager + platform `PdfRenderer`; `FLAG_SECURE` ekran koruması; hesap silmede `purge()` |
| **Coin** | `wallets/<uid>` + `users/<uid>/unlocks` canlı okuma, `ExamLock` (en eski 2 sınav ücretsiz, 100 coin), `unlockExam` callable (europe-west1), `config/rewards` android→ios fallback |
| **Bildirim** | Native FCM (`all` topic, kanal, `FLAG_IMMUTABLE` dokunma yönlendirmesi), `POST_NOTIFICATIONS` izin akışı, `announcements` → Inbox |
| **Giriş** | Phone Auth `+90` (`BE-04`) → `SessionStore` → `ProfileGate` (`ProfileStore`, RTDB `users/<uid>/profile`) → arşiv |
| **Senaryo bayrakları** | Debug intent extra'ları (`core/util/LaunchArgs.kt`); bağlı olanlar: mock'ta `vifi_signed_out`, `vifi_profile_incomplete`, `vifi_force_update`, `vifi_delete_requires_reauth`, `vifi_no_coins`, `vifi_show_onboarding`, `vifi_notifications_prompt` (`vifi_admin` tanımlı, moderasyonla bağlanacak); live debug'da `vifi_phone_auth_testing` (yalnız Console test numaralarıyla) |
| **Kütüphane** | AndroidX, AGP 8.7.2 + Gradle 8.9, `libs.versions.toml`, Kotlin 2.0.21 + Coroutines, Hilt 2.52, Compose BoM 2024.10.01, Navigation 2.8.3, Firebase BoM 33.5.1, Timber |
| **Build** | Gradle Groovy DSL (plugins DSL), `google-services` 4.4.2, Java 17 hedefi + desugaring; Gradle'ı JDK 17–21 ile çalıştır (Android Studio JBR 25 desteklenmez); release R8 küçültmeli |
| **❌ Yeni kodda yapma** | `com.android.support`/`jcenter`, AdMob, XML ekran (Compose kullan), sır/keystore commit'i, mock'a canlı Firebase binding'i |

---

## Proje Yapısı

```
ViFi-Android/
├── CLAUDE.md                     # Bu dosya (Claude talimatları)
├── ANDROID-PARITY-ROADMAP.md     # iOS 3.1.0 parite — özet master liste
├── ANDROID-PARITY-DETAIL.md      # Aynı kalemlerin adım/kabul-kriteri/gotcha ayrıntısı
├── .claude/                      # ⚠️ Claude yapılandırması & kod üretim bilgisi
│   ├── settings.json             # İzinler
│   ├── rules/                    # Proje kuralları (okuma zorunlu — aşağıdaki tablo)
│   ├── skills/                   # Kod üretim şablonları (SKILL.md + templates/patterns/checklists)
│   └── commands/                 # Slash komutları
├── app/
│   ├── build.gradle              # Modül build (SDK, bağımlılıklar)
│   ├── google-services.json      # Firebase yapılandırması (vifi-831a8)
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/salticusteam/vifi/   # app/ (MainActivity, AppRoot) · core/ (model, data, designsystem,
│       │   # navigation, util, notifications) · feature/<ad>/ (browse, examviewer, login, profile, account,
│       │   # about, onboarding, inbox, notifications, update — Screen, ViewModel, UiState)
│       └── res/                  # layout/, drawable/, mipmap/, values/, menu/
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
| **Android kaydı** | `com.salticusteam.vifi` — bu geliştirme Mac'inin debug SHA-1/SHA-256'sı kayıtlı (`BE-01`); release izleri + `google-services.json` yenileme `REL-01`'de |
| **Arşiv** | named `vifi` Firestore `catalogNodes`/`exams` (`BE-05`, `BE-15`); RTDB `Universitiess` artık okunmuyor |
| **Giriş** | Phone Auth `+90` (`BE-04`); canlı test için Console'daki test numarası + `111111` |
| **App Check** | Storage/RTDB/Auth enforce **AÇIK** → Play Integrity kurulmadan **403**; Firestore enforce en son açılır (`BE-02/03`) |
| **Bildirim** | Native FCM (Parse kaldırıldı, `BE-11`), topic `all`, `POST_NOTIFICATIONS`, `announcements` (isimli `vifi`) → Inbox |
| **Cloud Functions (ortak, europe-west1)** | `unlockExam`, `recordLoginDiagnostic` — Android **çağırır** (`BE-08/09`), yeni function yazmaz |

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

**Son güncelleme:** 24 Ekim 2026
**Proje:** ViFi Android (`com.salticusteam.vifi`, v2.5 → iOS 3.1.0 parite hedefinde; giriş, arşiv, görüntüleyiciler, coin, bildirim, profil ve Hesap hazır)
