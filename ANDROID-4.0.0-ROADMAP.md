# ViFi Android — 4.0.0 Versiyonunda Yapılacak İşler

> **Amaç:** `ViFi-iOS` **`v-4.0.0`** dalındaki **ViFi Premium** geliştirmesini (aylık/yıllık abonelik + parayla tek sınav
> açma) Android'de birebir hayata geçirmek. iOS paritesinin ilk turu (223 kalem) [ANDROID-PARITY-ROADMAP.md](ANDROID-PARITY-ROADMAP.md)'de
> tamamlandı; bu dosya 4.0.0'ın **tek** iş listesidir.
>
> **Dalda hazır olanlar (`v-4.0.0`):** sürüm 4.0.0 (versionCode 18), QA-01…QA-04 düzeltmeleri, ilk sürüm notları.
> **Yayın:** bu listedeki hiçbir kalem paketi canlıya göndermez; release yalnızca ürün sahibi "release çıkalım" dediğinde.

---

## 0. Durum Özeti

> **İlerleme (29 Ekim 2026):** Toplam 27 kalemin **19'u tamam, 0'ı yarım, 8'i başlanmadı.** Faz 0 kararları, Hat A (istemci billing), Hat C (arayüz), Faz 2 (entegrasyon + testler) ve backend kodu + testleri (SRV-01/02, ortak depoda, yayınlanmadı) tamam; uygulama mock'ta uçtan uca çalışıyor. Kalan: Play Console / Google Cloud hazırlığı (OPS-01/02), function yayını (SRV-03, onaylı), yasal/beyan (LEG-01…03), gerçek ortam doğrulaması (PQA-01/02). Canlıda tek sınav satın alma SRV-03 yayını ve OPS-02 erişimi olmadan doğrulanamaz.

| Faz | Ne | Nasıl ilerler | Kalem |
|---|---|---|---|
| **Faz 0** | Kararlar + Play Console / Google Cloud hazırlığı | Sıralı, ürün sahibiyle — ⚠️ onay gerektirir | 6 |
| **Faz 1** | Geliştirme | **4 paralel hat** (A istemci billing · B backend · C arayüz · D yasal/beyan) | 16 |
| **Faz 2** | Entegrasyon + testler | A ve C bitince birleşir | 3 |
| **Faz 3** | Gerçek ortam doğrulaması (lisans testi) | Sıralı; yükleme yalnız ürün sahibinin onayıyla | 2 |

---

## 1. iOS'ta Ne Yapıldı (`v-4.0.0`, commit `7639530` + `05c15c3`, `3286098`, `1b60a95`)

**Ürünler** (StoreKit 2; yerel `ViFi.storekit` test fiyatları — App Store Connect'teki gerçek fiyatlar ayrıca teyit edilecek):

| iOS ürün kimliği | Tür | Yerel test fiyatı | İşlev |
|---|---|---|---|
| `com.BuyukerYazilim.ViFi2.premium.monthly` | Otomatik yenilenen abonelik (P1M) | 59,99 ₺ | Tüm arşiv açık, coin gizli |
| `com.BuyukerYazilim.ViFi2.premium.yearly` | Otomatik yenilenen abonelik (P1Y) | 399,99 ₺ | Aynı; paywall'da varsayılan, "En avantajlı" |
| `com.BuyukerYazilim.ViFi2.exam.unlock` | Tüketilebilir (consumable) | 29,99 ₺ | Tek sınavı coin yerine parayla açar |

**Mimari:**
- **Abonelik hakkı yalnız cihazda doğrulanır** (`Transaction.currentEntitlements` + `Transaction.updates`, StoreKit imza kontrolü). Sunucuya yazılmaz; Apple Kimliği'ne bağlıdır, Firebase oturumundan bağımsızdır (çıkışta sıfırlanmaz). `SubscriptionStore` uygulama ömrü boyunca `isSubscribed`'ı izler; `false` başlar (kilitli güvenli varsayılan).
- **Tek sınav satın alma sunucuda doğrulanır:** imzalı işlem (JWS) `purchaseExamUnlock` (europe-west1) callable'ına gider → Apple imzası `@apple/app-store-server-library` ile doğrulanır → `purchasedTransactions/<transactionId>` (replay koruması: bir işlem yalnız bir sınavı bir kez açar) + `users/<uid>/unlocks/<examId>` (`source: 'purchase'`). İşlem **sunucu kaydından sonra** `finish` edilir; sınav kimliği işlemde taşınmadığı için satın almadan önce yerelde saklanır ve açılışta `recoverPendingUnlock()` ile kurtarılır (ağ kopması, Ask to Buy).
- **Güvenlik modeli (bilinçli, iOS ile aynı):** arşiv dosyaları (`imagess/`, `pdfs/`) +90 numarayla giriş yapmış her kullanıcıya okunur; kilit (coin, açılan sınav, Premium) istemci tarafında uygulanır. Firestore'da `subscriptions/{uid}` (yalnız sunucu yazar) hazır ama 4.0.0'da kullanılmıyor.

**Arayüz:**
- **Paywall** (sheet): taç rozeti, "Tüm sınavlara sınırsız eriş", 4 fayda satırı, plan kartları (yıllık varsayılan), yükleme/hata/"Tekrar Dene", otomatik yenileme metni, Kullanım Koşulları + Gizlilik bağlantıları, alt çubukta "Abone Ol · fiyat" ve "Satın alımları geri yükle"; bekleyen satın alma mesajı; abonelik aktif olunca kendiliğinden kapanır; `screen_view` "Paywall".
- **Kilit sheet'i:** coin seçeneğinin altına "veya" + **"Parayla bu sınavı aç"** (her zaman) ve **"Tek tek açmak yerine Premium'a geç"** (paywall'ı açar); herhangi bir işlem sürerken diğerleri ve kapatma kilitli.
- **Aboneyken:** arşivde kilit yok, araç çubuğundaki coin rozeti gizli, Hesap'ta coin bölümü gizli.
- **Hesap:** "ViFi Premium'a Geç" satırı ya da aktifse "ViFi Premium · Tüm sınavlar açık" + "Aboneliği Yönet".
- **Metinler:** 30 yeni Türkçe metin; hata mesajları (`SubscriptionError`: ürün alınamıyor, mağazaya ulaşılamıyor, doğrulanamadı).

**Diğer:** gizlilik + kullanım koşulları sayfaları abonelik/satın alma bölümleriyle **mağazadan bağımsız** hale getirildi ve canlıda Google Play'i de kapsıyor (29 Ekim'de doğrulandı); gizlilik manifestine "satın alma geçmişi"; mock'ta `-ViFiSubscribed`; testler `SubscriptionTests`, `PremiumUITests`, `ExamUnlockUITests` (+parayla açma), `appStore.test.mjs`. Satın alma analytics olayı eklenmedi (yalnız paywall `screen_view`).

**Canlı backend durumu (29 Ekim, salt okuma):** `purchaseExamUnlock`, `unlockExam`, `recordLoginDiagnostic` yayında. `purchaseExamUnlock` **yalnız Apple** işlemlerini doğruluyor → Android için Google Play dalı gerekli.

---

## 2. Paralel Çalışma Planı

```
Faz 0  DEC-01..04 (kararlar) ──► OPS-01 (Play ürünleri)  ──────────────┐
                              └► OPS-02 (API + servis hesabı) ──┐       │
Faz 1  BIL-01 (sözleşme, ilk gün) ─┬─► Hat A: BIL-02..06        │       │
                                   ├─► Hat C: UI-01..04          │       │
       Hat B: SRV-01..02 (ayrı depo, DEC-04 sonrası) ◄───────────┘       │
       Hat D: LEG-01..03 (bağımsız)                                       │
Faz 2  INT-01..03  ◄── Hat A + Hat C                                      │
       SRV-03 (deploy, onaylı) ◄── SRV-01..02 + OPS-02                     │
Faz 3  PQA-01..02  ◄── INT + SRV-03 + OPS-01 ◄────────────────────────────┘
```

**Hat sahipliği (çakışma olmasın diye dosya sınırları):**

| Hat | Dokunduğu yerler | Dokunmaz |
|---|---|---|
| **A — İstemci billing** | `core/data/billing/**` (canlı Play Billing), `core/data/repository/{SubscriptionService,ExamPurchaseService}.kt` + `fake/` karşılıkları, `core/model/` yeni tipler, Hilt modülleri, `app/src/mock/**`, `LaunchArgs`, `gradle/libs.versions.toml`, `app/build.gradle` | `feature/**`, `strings.xml`, `AppRoot` |
| **B — Backend** | `ViFi-iOS/firebase/functions/**`, `firebase/tests/**`, `firebase/README.md` (ortak depo) | Android deposu |
| **C — Arayüz** | `feature/premium/**`, `feature/browse/ExamUnlock*`, `feature/account/**`, `CoinBalanceBadge`/`ViFiTopAppBar`, `strings.xml`, `ViFiRoute` (Paywall rotası) | DI modülleri, `core/data/**` (yalnız BIL-01 arayüzlerini ve Fake'leri kullanır) |
| **D — Yasal/beyan** | `PLAY-DATA-SAFETY.md`, `PLAY-STORE-LISTING.md`, Play Console formları (ürün sahibiyle) | Kod |

- **Senkron noktası 1:** BIL-01 + BIL-04 + BIL-06'nın DI kısmı birleşmeden Hat C başlamaz (arayüzler, Fake'ler, `SubscriptionStore` ve Hilt bağlaması sabitlenir — ViewModel'ler ancak bağlama varken derlenir). ✅ 29 Ekim'de hazır.
- **Senkron noktası 2:** Hat A + Hat C birleşince INT-01 (`AppRoot`, `ViFiNavHost` bağlama yalnız burada).
- **Senkron noktası 3:** SRV-03 yayında + OPS-01/02 hazır → Faz 3.

---

## 3. İş Listesi

> Her kalem: `- [ ] `**`ID`**` başlık _(öncelik·efor)_ — açıklama ⟶ bağımlılık`. Ayrıntı (adımlar, kabul kriteri, tuzaklar) §4'te.

### Faz 0 — Kararlar ve Hazırlık  ⚠️ (ürün sahibi · ONAY gerektirir)

- [x] **`DEC-01`** Play ürün yapısı ve kimlikleri _(P0·S)_ — Tek abonelik ürünü `vifi_premium` + iki base plan `monthly` (P1M) / `yearly` (P1Y); tüketilebilir tek seferlik ürün `exam_unlock`. ✅ _Karar (2026-10-29, ürün sahibi)._
- [x] **`DEC-02`** Fiyatlar ve teklifler _(P0·S)_ — iOS ile aynı: aylık 59,99 ₺, yıllık 399,99 ₺, tek sınav 29,99 ₺ (App Store Connect'teki gerçek fiyat farklıysa onunla eşitlenir); ücretsiz deneme / tanıtım teklifi yok. ⟶ `DEC-01` ✅ _Karar (2026-10-29, ürün sahibi)._
- [x] **`DEC-03`** Abonelik hakkının kapsamı _(P0·S)_ — iOS gibi: Google hesabına bağlı, cihazda doğrulanır, iOS ile paylaşılmaz, çıkışta sıfırlanmaz; `subscriptions/{uid}` 4.0.0'da kullanılmaz. ✅ _Karar (2026-10-29, ürün sahibi)._
- [x] **`DEC-04`** Tek sınav satın almanın sunucu doğrulaması _(P0·S)_ — Mevcut `purchaseExamUnlock`'a Google Play dalı (ortak backend `ViFi-iOS/firebase/`; CLAUDE.md "Android yeni backend yazmaz" kuralının onaylı istisnası), Play Developer API ile; sınav kimliği satın almaya `obfuscatedProfileId` ile bağlanır. Deploy (`SRV-03`) ayrıca onaya bağlı. ✅ _Karar (2026-10-29, ürün sahibi)._
- [ ] **`OPS-01`** Play Console'da ürünler + lisans test kullanıcıları _(P0·S)_ — Abonelik + base planlar + `exam_unlock` oluşturulup etkinleştirilir; Ayarlar → Lisans testi'ne test hesapları. Ürün sahibi yapar. Play, faturalandırma izni taşıyan bir derleme yüklenmeden ürün açtırmayabilir → önce Console'da kontrol; gerekiyorsa `BIL-02` sonrası iç teste yükleme (yalnız ürün sahibi onayıyla). ⟶ `DEC-01`, `DEC-02`
- [ ] **`OPS-02`** Sunucunun Play'e erişimi _(P0·S)_ — `vifi-831a8`'de "Google Play Android Developer API" etkin; Functions servis hesabı Play Console'a sipariş görüntüleme yetkisiyle eklenir. Canlı ayar → onayla. ⟶ `DEC-04`

### Faz 1 — Geliştirme (4 paralel hat)

#### Hat A — İstemci billing katmanı

- [x] **`BIL-01`** Sözleşme: modeller, arayüzler, Fake'ler _(P0·S)_ — `core/model/StoreProduct.kt` (`StoreProductIds`, `SubscriptionPeriod`, `SubscriptionOption`, `PurchaseResult`), `SubscriptionError` (Türkçe `userMessage` + `analyticsReason`, `Throwable.userMessage`'a bağlı), `core/data/repository/SubscriptionService` + `ExamPurchaseService`, `fake/FakeSubscriptionService` + `FakeExamPurchaseService`. ⟶ `DEC-01`, `DEC-03` ✅ _Tamamlandı (2026-10-29): arayüzler Activity almaz (canlı uygulama ön plandaki Activity'yi kendisi alır, `BIL-02`); `StoreProductTest` (5) + `FakeSubscriptionServiceTest` (4); derleme, lint ve 3 × 574 birim testi geçti._
- [x] **`BIL-02`** Play Billing bağımlılığı + bağlantı yöneticisi _(P0·M)_ — Güncel kararlı Billing Library (+ `billing-ktx`), tek `BillingClient`, bağlan/yeniden bağlan, bekleyen tek seferlik satın almalar etkin, `PurchasesUpdatedListener` → Flow. ⟶ `BIL-01` ✅ _Tamamlandı (2026-10-29, Hat A, `c7cdd69`): Play Billing **9.1.0** (yalnız live; Play asgarisi 8); `billing-ktx` alınmadı (9.x Kotlin 2.2 stdlib istiyor, proje 2.0.21) → geri çağrılar elle coroutine'e sarıldı; tek `BillingClient`, ortak bağlantı denemesi, 1 s/2 s bekleyişle 3 deneme; Activity için mevcut `ForegroundActivityHolder`; R8 kuralı gerekmedi._
- [x] **`BIL-03`** `PlayBillingSubscriptionService` _(P0·M)_ — Ürün/plan detayları ve yerel fiyat, `isSubscribed` (SUBS sorgusu, onaylanmamışı onayla), `statusChanges()` Flow (bağlantı, güncellemeler, ön plana dönüş), `purchase`, `restore` (yeniden sorgu). ⟶ `BIL-02` ✅ _Tamamlandı (2026-10-29, Hat A): planlar aylık önce; okumada onaylanmamış satın alma onaylanır; durum açılışta, her satın alma güncellemesinde ve ön plana dönüşte (`AppForegroundEvents`) yeniden okunur; okuma hatasında son durum korunur._
- [x] **`BIL-04`** `SubscriptionStore` _(P0·S)_ — `@Singleton`, uygulama ömrü boyunca, oturumdan bağımsız; `isSubscribed: StateFlow<Boolean>` (`false` başlar), `options`, satın alma/geri yükleme sonrası yeniden okuma. ⟶ `BIL-01` ✅ _Tamamlandı (2026-10-29): `core/data/billing/SubscriptionStore.kt`, oluşturulduğu andan itibaren `@ApplicationScope`'ta durumu izler, okuma hatasında son durumu (başlangıçta kilitli) korur; `SubscriptionStoreTest` (5)._
- [x] **`BIL-05`** `PlayBillingExamPurchaseService` _(P0·M)_ — Satın almadan önce bekleyen sınav (DataStore) + `obfuscatedProfileId = examId`; başarıda `purchaseExamUnlock` (Android dalı) → **sunucu kaydından sonra** tüket; `recoverPendingUnlock()`; PENDING; Functions hata eşlemesi. ⟶ `BIL-02`; canlı uçtan uca `SRV-03` ✅ _Tamamlandı (2026-10-29, Hat A): `obfuscatedProfileId = examId`, `obfuscatedAccountId = sha256(uid)`, bekleyen sınav DataStore'da; `purchaseExamUnlock` `{platform: 'android', purchaseToken, productId, examId}`; yalnız sunucu başarısından sonra tüketir; tüketilmemiş eski satın almayı önce tamamlar; oturum kapalıyken ödeme alınmaz; 64 karakter sınırı. Sunucu dalı (`SRV-01`) gelene kadar canlıda `FailedVerification` döner, ödeme tüketilmediği için kaybolmaz._
- [x] **`BIL-06`** Mock flavor + `vifi_subscribed` + DI _(P1·S)_ — Live: PlayBilling*, mock: Fake* (Firebase/Billing başlamaz); `vifi_subscribed` aktif abonelikle başlatır. ⟶ `BIL-01` ✅ _Tamamlandı (2026-10-29): live `BillingModule` Play Billing uygulamalarını bağlıyor, yer tutucular silindi (Hat A). Önceki adım: mock `BillingModule` Fake'leri bağlıyor, `vifi_subscribed` (`LaunchArg.SUBSCRIBED`) eklendi. Live `BillingModule` şimdilik `UnavailableSubscriptionService` / `UnavailableExamPurchaseService` bağlıyor (Premium hiç aktif olmaz, satın alma `ProductUnavailable`) — canlı bir derleme ödemesiz Premium veremez. _

#### Hat B — Backend (ortak depo `ViFi-iOS/firebase/`) ⚠️ ONAY

- [x] **`SRV-01`** `purchaseExamUnlock` Google Play dalı _(P0·M)_ — `platform: 'android'`, `purchaseToken`, `productId`; Play Developer API `purchases.products.get` (paket `com.salticusteam.vifi`, `purchaseState` = satın alındı, ürün kimliği, `obfuscatedExternalProfileId` = `examId`); replay anahtarı `orderId`; aynı `purchasedTransactions` + `unlocks` transaction'ı. iOS yolu değişmez. ⟶ `DEC-04` ✅ _Tamamlandı (2026-10-29, ortak depo `ViFi-iOS` `v-4.0.0`, `f0a8b67`, yayınlanmadı): `functions/googlePlay.js` (Play API `google-auth-library` ile doğrudan REST, zaten kurulu 9.15.1 açık bağımlılık yapıldı; girdi korumaları API'den önce; `orderId` yoksa `play-token-<jeton SHA-256>`); kayıt transaction'ı `functions/purchaseUnlock.js`'e taşındı ve iki platformca paylaşılıyor (iOS kayıtları birebir aynı, Android'e `store: 'google_play'`); `platform` yoksa iOS. Hata kodları: geçersiz `failed-precondition`, Play'e ulaşılamıyor `unavailable`, API erişimi yok (OPS-02) `internal`._
- [x] **`SRV-02`** Backend testleri _(P0·S)_ — `googlePlay.test.mjs`: girdi korumaları, sahte doğrulayıcıyla durum/ürün/profil eşleşmesi, replay, iOS yolunun bozulmadığı; emülatör. ⟶ `SRV-01` ✅ _Tamamlandı (2026-10-29): `tests/googlePlay.test.mjs` (15, ağsız) + `tests/purchaseExamUnlock.test.mjs` (12, Firestore emülatörü: iOS kayıt biçimi, Play etiketi, idempotent tekrar, başka sınav/kullanıcı için replay, coin'le açılmış sınav, callable'ın iki platform korumaları `onCall.run` ile); `npm test` 293 test, 0 başarısız (4 mevcut `todo`)._
- [ ] **`SRV-03`** Function yayını _(P0·S)_ — `firebase deploy --only functions:purchaseExamUnlock`, **yalnız açık onayla**; ardından iOS satın almasının çalıştığı kontrol edilir. ⟶ `SRV-02`, `OPS-02`

#### Hat C — Arayüz (BIL-01'deki Fake'lerle)

- [x] **`UI-01`** Paywall ekranı _(P0·M)_ — `PaywallScreen` + `PaywallViewModel` + `PaywallUiState`; iOS'taki tüm durumlar; Google Play otomatik yenileme/iptal metni; yasal bağlantılar; `paywall.*` testTag'leri; `screen_view` "Paywall". ⟶ `BIL-01` ✅ _Tamamlandı (2026-10-29, Hat C, `1a2fcf4`): `feature/premium/` (`PaywallScreen`, `PaywallViewModel`, `PaywallUiState`, `PremiumLinks`); otomatik yenileme metni Google Play'e uyarlandı (iOS'taki "24 saat önce" kuralı Apple'a özgü); onay işaretleri tema turuncusu; `AnalyticsScreen.PAYWALL`._
- [x] **`UI-02`** Kilit sheet'i: parayla aç + Premium'a geç _(P0·M)_ — "veya" + "Parayla bu sınavı aç", "Tek tek açmak yerine Premium'a geç"; sonuç modeli (açıldı / iptal / hata); meşgulken kilitli. ⟶ `BIL-01` ✅ _Tamamlandı (2026-10-29, Hat C): "veya" + "Parayla bu sınavı aç" + "Tek tek açmak yerine Premium'a geç"; sheet'in kapatma kilidindeki eski hata (ilk durumu okuyordu) düzeltildi._
- [x] **`UI-03`** Aboneliğin etkileri + Hesap Premium bölümü _(P0·M)_ — Aboneyken arşivde kilit yok, coin rozeti ve Hesap'taki coin satırı gizli; Hesap'ta "ViFi Premium'a Geç" / aktif durum + "Aboneliği Yönet" (Play abonelik sayfası). ⟶ `BIL-01` ✅ _Tamamlandı (2026-10-29, Hat C): aboneyken kilitsiz arşiv, gizli coin rozeti ve Hesap coin satırı; Hesap'ta Premium bölümü + "Aboneliği Yönet"._
- [x] **`UI-04`** Metinler ve erişilebilirlik _(P1·S)_ — iOS'taki 30 metnin Türkçe karşılıkları (App Store → Google Play), `cd_` metinleri; `strings.xml` yalnız bu hatta. ⟶ `UI-01`…`UI-03` ✅ _Tamamlandı (2026-10-29, Hat C): 30 metin; testTag'ler iOS kimlikleriyle aynı._

#### Hat D — Yasal metinler ve Play beyanları

- [ ] **`LEG-01`** Hosting metinlerinin son kontrolü _(P2·S)_ — Kullanım koşulları + gizlilik zaten mağazadan bağımsız ve canlıda; paywall bağlantıları aynı adresler.
- [ ] **`LEG-02`** Veri güvenliği beyanı _(P1·S)_ — "Satın alma geçmişi" (hesaba bağlı, uygulama işlevi, paylaşılmıyor): `PLAY-DATA-SAFETY.md` + Console formu (ürün sahibiyle). ⟶ `DEC-04`
- [ ] **`LEG-03`** Mağaza girişi ve 4.0.0 sürüm notları _(P1·S)_ — Premium + parayla tek sınav açma; `PLAY-STORE-LISTING.md` (500 karakter). ⟶ `DEC-02`

### Faz 2 — Entegrasyon ve Testler

- [x] **`INT-01`** Canlı bağlama _(P0·M)_ — `AppRoot`/`ViFiNavHost`: abonelik durumunu uygulama ömrü boyunca izle, oturum açıkken açılışta `recoverPendingUnlock()` → açılan sınavı işaretle, Paywall rotası, Hesap ve kilit sheet'i bağlantıları. ⟶ `BIL-03`…`BIL-06`, `UI-01`…`UI-03` ✅ _Tamamlandı (2026-10-29): Hat A (`refs/heads/claude/angry-khorana-ecff03`, `c7cdd69`) ileri sarıldı, Hat C (`refs/heads/claude/ecstatic-bhaskara-2a8636`, `1a2fcf4`) `270c256` ile birleştirildi (çakışma yok). `ViFiRoute.Paywall` + NavHost yuvası; kilit sheet'i (Browse) ve Hesap'tan Paywall'a geçiş; `PaidUnlockRecoveryViewModel` oturum açıkken açılışta ve her ön plana dönüşte `recoverPendingUnlock()` → `ExamUnlocksStore.markUnlocked`; mock'a `AppForegroundEvents` bağlandı. Home'da kilitli sınav listelenmediği için Paywall bağlantısı yalnız Browse ve Hesap'ta._
- [x] **`INT-02`** Birim testleri _(P0·M)_ — Store, servisler (sahte BillingClient sarmalayıcısıyla), ViewModel'ler, hata eşleme; `AnalyticsLimitsTest`. ⟶ `INT-01` ✅ _Tamamlandı (2026-10-29): hatların testleri (billing 54, ViewModel/Store) + `PaidUnlockRecoveryViewModelTest` (2); birleşik dalda birim testleri liveDebug 654, liveRelease 654, mockDebug 647 — hepsi geçti._
- [x] **`INT-03`** UI testleri ve emülatör doğrulaması _(P0·M)_ — iOS `PremiumUITests`/`ExamUnlockUITests` karşılıkları; mock'ta uçtan uca, açık/koyu tema. ⟶ `INT-01` ✅ _Tamamlandı (2026-10-29): UI testleri mock 98/98, live 97/97. Mock'ta emülatörde (API 37) uçtan uca: kilit sheet'i → "Premium'a geç" → Paywall (yıllık varsayılan) → abone ol → Paywall kapanır, kilitler ve coin rozeti kalkar; Hesap aboneyken "ViFi Premium · Tüm sınavlar açık" + "Aboneliği Yönet" (coin satırı yok), abone değilken "ViFi Premium'a Geç" + coin; "Parayla bu sınavı aç" sınavı açar. Koyu tema Hat C'de kontrol edildi._

### Faz 3 — Gerçek Ortam Doğrulaması (yayın değildir)

- [ ] **`PQA-01`** Lisans test hesabıyla satın alma senaryoları _(P0·M)_ — Abonelik al / yenile / iptal / süre dolumu, geri yükleme (yeniden kurulum), tek sınav satın al → sunucu kaydı → tüket, ağ kopunca kurtarma, bekleyen ödeme. Test kanalına yükleme **yalnız ürün sahibi onayıyla**. ⟶ `INT-03`, `SRV-03`, `OPS-01`
- [ ] **`PQA-02`** İade ve iptal etkisi _(P1·S)_ — İade/iptal sonrası abonelik hakkı düşer; tüketilmiş tek sınav kilidi kalır (iOS ile aynı kabul). ⟶ `PQA-01`

---

## 4. Ayrıntı

#### `DEC-01` — Play ürün yapısı ve kimlikleri
- **Karar (2026-10-29):** Seçenek (a) — abonelik `vifi_premium` (base planlar `monthly`, `yearly`), tek seferlik `exam_unlock`.
- **Neden:** iOS kimlikleri (`com.BuyukerYazilim.ViFi2.*`) Apple'a özgü; Play kimlikleri küçük harf, rakam, `_` ve `.` ile sınırlı ve bir kez kullanılınca değişmez. Play'in abonelik modelinde bir ürün birden çok **base plan** taşıyabilir.
- **Seçenekler:** (a) `vifi_premium` + base plan `monthly` / `yearly` (önerilen: tek ürün, Play'de yönetimi kolay); (b) iOS'a birebir iki ürün `premium_monthly`, `premium_yearly`. Tek sınav: `exam_unlock` (tüketilebilir, tek seferlik ürün).
- **Kabul kriteri:** Kimlikler bu dosyaya ve `StoreProductIds`'e yazılı; sunucudaki ürün kimliğiyle aynı.

#### `DEC-02` — Fiyatlar ve teklifler
- **Karar (2026-10-29):** iOS ile aynı — 59,99 ₺ / 399,99 ₺ / 29,99 ₺; App Store Connect'teki gerçek fiyat farklıysa Play'de onunla eşitlenir (`OPS-01`'de ürünler girilirken teyit). Deneme ve tanıtım teklifi yok.
- iOS'taki fiyatlar yalnız yerel test yapılandırmasından okunabildi; App Store Connect'teki gerçek fiyatlar ürün sahibince teyit edilmeli. Play'de fiyat TRY olarak girilir, diğer ülkeler için otomatik dönüştürme kapatılabilir (uygulama Türkiye odaklı).
- **Kabul kriteri:** Üç ürünün TRY fiyatı ve "deneme yok" kararı yazılı.

#### `DEC-03` — Abonelik hakkının kapsamı
- **Karar (2026-10-29):** iOS paritesi — cihazda doğrulanan, Google hesabına bağlı hak; platformlar arası paylaşım yok.
- iOS hakkı yalnız cihazda ve Apple Kimliği'ne bağlı tutuyor. Android karşılığı: Play satın alması Google hesabına bağlı; `queryPurchasesAsync(SUBS)` ile okunur. Sonuç: iOS'ta abone olan Android'de (ve tersi) Premium görmez — iOS'taki davranışla tutarlı.
- **Tuzak:** Aynı cihazda farklı ViFi hesabına geçilse de Premium kalır (iOS'ta da öyle). Sunucuda hak tutmak (`subscriptions/{uid}` + Real-time Developer Notifications) 4.0.0 kapsamı dışında.

#### `DEC-04` — Tek sınav satın almanın sunucu doğrulaması
- **Karar (2026-10-29):** `purchaseExamUnlock`'a Google Play dalı onaylandı; kod ortak depoda (`ViFi-iOS/firebase/`), deploy `SRV-03`'te ayrıca onaylanır.
- Kilit kaydı `users/<uid>/unlocks` yalnız sunucu tarafından yazılabildiği için (Firestore kuralı `write: false`) sunucu şart. Google Play satın alma jetonu sunucuda Play Developer API ile doğrulanır.
- **Öneri:** Sınav kimliği `BillingFlowParams.setObfuscatedAccountId/ProfileId` ile satın almaya bağlanır; sunucu `obfuscatedExternalProfileId`'yi kontrol eder — istemcinin "şu sınav için ödedim" iddiasına güvenilmez (iOS'ta bu bağ yalnız yerel kayıtla kuruluyor). Yerel bekleyen sınav kaydı kurtarma için yine tutulur.
- **Onay:** Ortak backend'e yazılacak ilk Android dalı; deploy ayrıca onaylanır (SRV-03).

#### `OPS-01` — Play Console'da ürünler + lisans test kullanıcıları
- **Adımlar:** Para kazanma → Abonelikler: ürün + base planlar (otomatik yenilenen, TRY fiyat) → etkinleştir; Uygulama içi ürünler → `exam_unlock` → etkinleştir; Ayarlar → Lisans testi: test hesapları.
- **Tuzak:** Ürünler, faturalandırma izni (`com.android.vending.BILLING`, Billing Library manifestinde gelir) içeren bir derleme Play'e en az bir kez yüklenmeden etkinleştirilemeyebilir; gerekirse bu yükleme ürün sahibinin onayıyla iç teste yapılır.

#### `OPS-02` — Sunucunun Play'e erişimi
- **Adımlar:** Google Cloud (`vifi-831a8`, proje no 441043948320) → API'ler → Google Play Android Developer API'yi etkinleştir; Play Console → Kullanıcılar ve izinler → Functions çalışma servis hesabını davet et (uygulama: ViFi; izin: sipariş/finansal veri görüntüleme).
- **Tuzak:** İzinlerin yayılması saatler sürebilir; 401/403 bu yüzden olabilir.

#### `BIL-01` — Sözleşme
- **Adımlar:** `core/model/`: `StoreProductIds` (DEC-01), `SubscriptionPeriod { MONTHLY, YEARLY }`, `SubscriptionOption(id, basePlanId, offerToken, displayName, displayPrice, period)`, `PurchaseResult { SUCCESS, CANCELLED, PENDING }`, `sealed class SubscriptionError(userMessage)` (iOS'taki üç mesajın Google Play uyarlaması + `Unknown`). `core/data/billing/`: `SubscriptionService` (`subscriptionOptions()`, `isSubscribed()`, `statusChanges(): Flow<Boolean>`, `purchase(activity, option)`, `restore()`), `ExamPurchaseService` (`purchaseUnlock(activity, examId)`, `recoverPendingUnlock(): String?`), Fake'ler.
- **Karar (uygulandı):** Satın alma akışı bir `Activity` ister ama arayüzler Activity **almaz**; ViewModel'ler Activity tutmaz. Canlı uygulama (`BIL-02`) ön plandaki Activity'yi `ActivityLifecycleCallbacks` + zayıf referansla tutan tekil bir sağlayıcıdan alır (tek `MainActivity`).
- **Kabul kriteri:** Arayüzler + Fake'ler derleniyor; Hat C bunlarla ekran yazabiliyor.

#### `BIL-02` — Play Billing bağımlılığı + bağlantı yöneticisi
- **Adımlar:** Billing Library'nin güncel kararlı sürümünü (Play'in zorunlu tuttuğu en düşük sürümün üstünde; başlarken doğrulanır) `libs.versions.toml`'a ekle; ön plandaki Activity'yi zayıf referansla tutan sağlayıcı (satın alma ekranı için); `BillingClientConnection` (`@Singleton`): `startConnection`, kopunca üstel bekleme ile yeniden bağlanma, `enablePendingPurchases` (tek seferlik), güncellemeleri `SharedFlow` olarak yayma; R8 kuralı gerekip gerekmediği kontrolü.
- **Kabul kriteri:** Bağlantı koparsa çağrılar yeniden bağlanıp sonuç döndürüyor; çağrılar ana iş parçacığını bloklamıyor.

#### `BIL-03` — `PlayBillingSubscriptionService`
- **Adımlar:** `queryProductDetails(SUBS)` → base plan + offer token + `formattedPrice` → `SubscriptionOption` (aylık önce); `isSubscribed` = `queryPurchasesAsync(SUBS)` içinde `PURCHASED` durumlu, bizim ürün kimliğimizle bir satın alma; onaylanmamışsa `acknowledgePurchase` (3 gün içinde onaylanmayan satın alma Google tarafından iade edilir); `statusChanges` = bağlantıda + her `PurchasesUpdated`'da + uygulama ön plana döndüğünde yeniden türet; `restore` = yeniden sorgu.
- **Tuzak:** iOS'taki "doğrudan satın alma updates'e düşmez" sorunu Play'de yok, ama satın almadan sonra hak yine açıkça yeniden okunmalı (Store tek kaynak).
- **Kabul kriteri:** Satın alma, iptal, süre dolumu ve başka cihazdaki satın alma uygulama açıkken / ön plana dönünce yansıyor.

#### `BIL-04` — `SubscriptionStore`
- iOS `SubscriptionStore` karşılığı: `@Singleton`, `@ApplicationScope`'ta `statusChanges()` toplar; `isSubscribed` `false` başlar; `loadOptions()` tembel; `purchase`/`restore` sonrası hak yeniden okunur. Çıkışta sıfırlanmaz.

#### `BIL-05` — `PlayBillingExamPurchaseService`
- **Adımlar:** `exam_unlock` ürün detayları → bekleyen sınavı DataStore'a yaz → `launchBillingFlow` (`obfuscatedProfileId = examId`, `obfuscatedAccountId` = uid'nin özeti) → `PURCHASED` ise `purchaseExamUnlock(platform = android, purchaseToken, productId, examId)` → başarıyla `consumeAsync` → bekleyeni sil → `SUCCESS`; `PENDING` → bekleyen kalır; iptal → bekleyen silinir. `recoverPendingUnlock`: `queryPurchasesAsync(INAPP)` içinde tüketilmemiş `exam_unlock` → sunucu → tüket → sınav kimliğini döndür.
- **Tuzak:** Tüketme **yalnız** sunucu kaydından sonra; aksi halde ödeme alınır ama sınav açılmaz. Tüketilmemiş satın alma aynı ürünün yeniden alınmasını engeller — kurtarma bu yüzden açılışta şart.
- **Kabul kriteri:** Ağ satın almadan sonra koparsa bir sonraki açılışta sınav açılıyor; aynı jeton iki kez kullanılamıyor.

#### `BIL-06` — Mock flavor + `vifi_subscribed` + DI
- Live Hilt modülü PlayBilling*'i, mock modülü Fake*'i bağlar (mock'ta Billing de başlamaz, `BackendStartup` deseni). `LaunchArg.SUBSCRIBED("vifi_subscribed")`.
- **Durum (2026-10-29):** `app/src/{live,mock}/…/core/data/billing/BillingModule.kt` ayrı modüller (`RepositoryModule`'e dokunulmadı); live şimdilik `UnavailableBilling.kt` yer tutucularını bağlıyor. Hat A, `BIL-03`/`BIL-05` bitince yalnız live `BillingModule`'ü değiştirir ve `UnavailableBilling.kt`'yi siler; Hat C bu dosyalara dokunmaz.

#### `SRV-01` — `purchaseExamUnlock` Google Play dalı
- **Adımlar:** `data.platform === 'android'` dalı: `googleapis` (veya `@googleapis/androidpublisher`) ile `purchases.products.get({ packageName, productId, token })`; kontroller: `purchaseState === 0`, `productId === 'exam_unlock'`, `obfuscatedExternalProfileId === examId`; `transactionId = orderId`; mevcut transaction bloğu (`purchasedTransactions`, `unlocks` + `store: 'google_play'`). Hata kodları iOS ile aynı (`failed-precondition` doğrulama, `not-found` sınav, `permission-denied` oturum).
- **Tuzak:** Test satın almalarında `orderId` boş olabilir → yedek anahtar: jetonun SHA-256 özeti. Sunucu tüketmez (istemci tüketir); tüketilmiş jetonla tekrar çağrı idempotent kalmalı.
- **Kabul kriteri:** Geçerli jeton sınavı bir kez açar; başka sınav için aynı jeton reddedilir; iOS yolu değişmez.

#### `SRV-02` — Backend testleri
- Play API çağrısı enjekte edilebilir bir doğrulayıcı arkasında; testler sahte yanıtlarla: geçersiz girdi, yanlış ürün, yanlış profil (sınav), iptal edilmiş/bekleyen durum, replay, aynı (uid, sınav) idempotent.

#### `SRV-03` — Function yayını
- Yalnız ürün sahibinin açık onayıyla; yayından sonra iOS sandbox satın alması ile `purchaseExamUnlock`'ın hâlâ çalıştığı ve Android çağrısının 401/403 almadığı kontrol edilir.

#### `UI-01` — Paywall ekranı
- **Adımlar:** `feature/premium/`: iOS'taki içerik ve davranış birebir (başlık, fayda satırları, plan kartları — yıllık varsayılan + "En avantajlı", yükleme / "Abonelik planları yüklenemedi." + "Tekrar Dene", "Abone Ol · fiyat", "Satın alımları geri yükle", bekleyen ve "geri yüklenecek abonelik bulunamadı" mesajları, abonelik aktif olunca kapanma). Yasal metin: otomatik yenileme ve iptalin **Google Play abonelik ayarlarından** yapıldığı. Rota `ViFiRoute.Paywall` (kapat "✕").
- **Tuzak:** Play abonelik politikası fiyatın, dönemin ve otomatik yenilemenin açıkça görünmesini ister. Satın alma butonu satın alma sürerken devre dışı kalırsa turuncu dolgu ve gösterge kaybolmamalı (iOS'taki koyu mod düzeltmesinin karşılığı).
- **Kabul kriteri:** Fake'le tüm durumlar önizleme ve UI testinde görülüyor; açık/koyu temada okunur.

#### `UI-02` — Kilit sheet'i
- **Adımlar:** `ExamUnlockSheet`'e "veya" + "Parayla bu sınavı aç" (her zaman, ilerleme göstergeli) ve ayraç + "Tek tek açmak yerine Premium'a geç"; `ExamUnlockViewModel`'e satın alma niyeti + `ExamPurchaseOutcome` (açıldı → sınavı aç / iptal → kal / hata → mesaj); `ExamUnlockEvent.GoPremium`; meşgulken (coin veya para) diğer eylemler ve kapatma kilitli.
- **Kabul kriteri:** iOS `testBuyingALockedExamWithMoneyOpensIt` karşılığı geçiyor.

#### `UI-03` — Aboneliğin etkileri + Hesap Premium bölümü
- **Adımlar:** `ExamUnlockUiState.lockedPaths` aboneyken boş; `CoinBalanceBadge` aboneyken çizilmez; Hesap: aboneyken coin satırı yok, Premium bölümü ("ViFi Premium'a Geç · Tüm sınavlara sınırsız eriş" → Paywall / "ViFi Premium · Tüm sınavlar açık" + "Aboneliği Yönet" → `https://play.google.com/store/account/subscriptions?sku=vifi_premium&package=com.salticusteam.vifi`).
- **Kabul kriteri:** iOS `testSubscriberHasNoLockedExamsAndNoCoinBadge`, `testAccountShowsActivePremiumAndHidesCoins`, `testUpgradingFromAccountOpensPaywallAndSubscribes` karşılıkları geçiyor.

#### `UI-04` — Metinler
- iOS `Localizable.xcstrings`'teki 30 yeni anahtar; "App Store" geçenler Google Play'e uyarlanır ("Aboneliğini Google Play ayarlarından yönetebilir veya iptal edebilirsin.", "Google Play'e ulaşılamıyor…"). Positional args (`Abone Ol · %1$s`). `ViFiRoute`/UiState tipleri sürüm yorumu gerektirmez; yeni domain modelleri tip seviyesinde `// Added in 4.0.0 — …` alır.

#### `LEG-01` — Hosting metinleri
- 29 Ekim'de canlı sayfalarda Google Play ifadeleri doğrulandı (`/kullanim-kosullari`, `/gizlilik`). Ürün kimlikleri / fiyat değişirse metin değişmez (fiyat içermiyor).

#### `LEG-02` — Veri güvenliği beyanı
- iOS gizlilik manifestine "satın alma geçmişi (hesaba bağlı, uygulama işlevi)" eklendi; Play karşılığı: Finansal bilgiler → Satın alma geçmişi — toplanır, paylaşılmaz, zorunlu değil (yalnız satın alan). `PLAY-DATA-SAFETY.md` güncellenir; Console formu ürün sahibiyle gönderilir.

#### `LEG-03` — Mağaza girişi ve sürüm notları
- Sürüm notları (500 karakter) Premium ve tek sınav satın almayı + QA düzeltmelerini kapsayacak şekilde yeniden yazılır; uzun açıklamaya Premium bölümü.

#### `INT-01` — Canlı bağlama
- `AppRoot`: abonelik gözlemi `SessionRoot` üstünde (oturumdan bağımsız); `ArchiveHost` içinde `LaunchedEffect(user.id)` ile `recoverPendingUnlock()` → `ExamUnlocksStore.markUnlocked`; Paywall'a Hesap'tan ve kilit sheet'inden geçiş; satın alma köprüsü (Activity) burada bağlanır.

#### `INT-02` / `INT-03` — Testler
- Birim: `SubscriptionStore` (başlangıç `false`, akış güncellemesi, satın alma sonrası yeniden okuma), repository'ler (sahte bağlantıyla: onaylama, tüketme sırası, kurtarma, PENDING), ViewModel'ler, hata eşleme. UI: Paywall, kilit sheet'i, Hesap, aboneyken kilitsiz arşiv; mock'ta emülatörde uçtan uca, açık + koyu tema; `./gradlew assembleDebug lintLiveDebug test connected*AndroidTest`.

#### `PQA-01` / `PQA-02` — Gerçek ortam doğrulaması
- Lisans test hesabında satın alma ücretsizdir ve abonelik yenilemeleri kısaltılmış sürelerle olur (ör. aylık plan dakikalar içinde yenilenir); Play'in test ödeme yöntemleriyle onay / ret / gecikmeli ödeme denenir. Test kanalına derleme yüklemek bir release adımıdır → **yalnız ürün sahibi açıkça istediğinde**.

---

## 5. Riskler ve Açık Noktalar

1. **Backend sahipliği:** İlk kez Android için ortak backend'e kod yazılıyor (`DEC-04`, `SRV-*`); iOS satın alma yolu kesinlikle değişmemeli, deploy onaylı.
2. **Cihazda doğrulanan abonelik:** iOS ile aynı kabul; root'lu cihazda atlatılabilir. Dosyalar zaten +90 kullanıcılara açık olduğu için ek risk getirmez.
3. **Ürün etkinleştirme sırası:** Play ürünleri, faturalandırma izni olan bir derleme yüklenmeden etkinleşmeyebilir (`OPS-01`) → test kanalına erken bir yükleme gerekebilir (ürün sahibi onayı).
4. **Platformlar arası hak yok:** iOS'ta abone olan kullanıcı Android'de Premium görmez; destek metni/SSS buna hazır olmalı.
5. **Fiyat teyidi:** iOS fiyatları yalnız yerel test dosyasından okunabildi (`DEC-02`).
