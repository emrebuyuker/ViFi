# ViFi Android — 4.0.0 Versiyonunda Yapılacak İşler

> **Amaç:** `ViFi-iOS` **`v-4.0.0`** dalındaki **ViFi Premium** geliştirmesini (aylık/yıllık abonelik + parayla tek sınav
> açma) Android'de birebir hayata geçirmek. iOS paritesinin ilk turu (223 kalem) [ANDROID-PARITY-ROADMAP.md](ANDROID-PARITY-ROADMAP.md)'de
> tamamlandı; bu dosya 4.0.0'ın **tek** iş listesidir.
>
> **Dalda hazır olanlar (`v-4.0.0`):** sürüm 4.0.0 (versionCode 19; 18 dahili testte), QA-01…QA-04 düzeltmeleri, sürüm notları.
> **Yayın:** Faz 0–3 paketi canlıya göndermez. Yayın adımları **Faz 4**'tedir (`RLS-01`…`RLS-11`, 29 Ekim'de ürün
> sahibinin isteğiyle eklendi) ve ürün sahibiyle sırayla yapılır; her dış adım (release derleme/imza, Play'e yükleme,
> Console gönderimi, canlı veri yazımı) o adım için ayrıca onaylanır.

---

## 0. Durum Özeti

> **İlerleme (29 Ekim 2026):** Toplam 38 kalemin **32'si tamam, 0'ı yarım, 6'sı başlanmadı.** Faz 0 kararları, Hat A (istemci billing), Hat C (arayüz), Faz 2 (entegrasyon + testler), backend (SRV-01…03, ortak depoda, `purchaseExamUnlock` Google Play dalıyla canlıda), Hat D (yasal metinler canlıda, veri güvenliği envanteri, mağaza metni) ve sunucunun Play erişimi (OPS-02) tamam; uygulama mock'ta uçtan uca çalışıyor. Play ürünleri etkin, 18 (4.0.0) dahili testte, lisans test listesi hazır (OPS-01). Gerçek ortam doğrulaması (PQA-01) emülatörde lisans test hesabıyla yapıldı; 4 hata bulunup düzeltildi, telefon adımı ürün sahibinin kararıyla atlandı; iade/iptal etkisi (PQA-02) doğrulandı. Geliştirme ve doğrulama (Faz 0–3, 27 kalem) tamam; son derleme versionCode 19 (dalda hazır). Kalan: Faz 4 yayın adımları (`RLS-01`…`RLS-11`), önem ve uygulama sırasıyla, ürün sahibiyle.

| Faz | Ne | Nasıl ilerler | Kalem |
|---|---|---|---|
| **Faz 0** | Kararlar + Play Console / Google Cloud hazırlığı | Sıralı, ürün sahibiyle — ⚠️ onay gerektirir | 6 |
| **Faz 1** | Geliştirme | **4 paralel hat** (A istemci billing · B backend · C arayüz · D yasal/beyan) | 16 |
| **Faz 2** | Entegrasyon + testler | A ve C bitince birleşir | 3 |
| **Faz 3** | Gerçek ortam doğrulaması (lisans testi) | Sıralı; yükleme yalnız ürün sahibinin onayıyla | 2 |
| **Faz 4** | Yayın: push, release AAB, dahili test, Console beyanları, üretim, yayın sonrası | Sıralı, ürün sahibiyle — ⚠️ her dış adım ayrı onay | 11 |

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
Faz 4  RLS-01 → 02 → 03 → (04, 05, 06, 07) → 08 → 09 → 10   ·   RLS-11 bağımsız
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
- [x] **`DEC-04`** Tek sınav satın almanın sunucu doğrulaması _(P0·S)_ — Mevcut `purchaseExamUnlock`'a Google Play dalı (ortak backend `ViFi-iOS/firebase/`; CLAUDE.md "Android yeni backend yazmaz" kuralının onaylı istisnası), Play Developer API ile; sınav kimliği satın almaya `obfuscatedProfileId` ile bağlanır (PQA-01 bulgusu, 29 Ekim, ürün sahibi kararı: sınav kimlikleri katalog yolu olduğundan Play'in 64 karakter sınırını aşıyor → bağ SHA-256 özetiyle). Deploy (`SRV-03`) ayrıca onaya bağlı. ✅ _Karar (2026-10-29, ürün sahibi)._
- [x] **`OPS-01`** Play Console'da ürünler + lisans test kullanıcıları _(P0·S)_ — Abonelik + base planlar + `exam_unlock` oluşturulup etkinleştirilir; Ayarlar → Lisans testi'ne test hesapları. Ürün sahibi yapar. Play, faturalandırma izni taşıyan bir derleme yüklenmeden ürün açtırmayabilir → önce Console'da kontrol; gerekiyorsa `BIL-02` sonrası iç teste yükleme (yalnız ürün sahibi onayıyla). ⟶ `DEC-01`, `DEC-02` ✅ _Tamamlandı (2026-10-29): Console doğrulandı. Abonelikler ve Tek seferlik ürünler sayfaları "FATURALANDIRMA izni olan bir APK yükleyin" diyerek ürün açtırmıyor (Play'deki 3.1.0'da izin yok). Ürün sahibinin onayıyla 4.0.0 (18) liveRelease AAB derlendi: `0afc28b`, yükleme anahtarı `75:61…`, `com.android.vending.BILLING` var; dosya `play-store/ViFi-4.0.0-18.aab` (git dışı). Ürün sahibinin onayıyla 18 (4.0.0) dahili teste yayınlandı (29 Ekim 09:46). Test kullanıcısı tanımlı değil, üretimdeki 3.1.0 değişmedi. Taslak hâlindeki yükleme ürün açmaya yetmedi; yayından sonra açıldı. Onayla oluşturulup etkinleştirilenler: abonelik `vifi_premium` ("ViFi Premium"), temel planları `monthly` (aylık) ve `yearly` (yıllık), ikisi de otomatik yenilenen, ek süre Play varsayılanı (7/14 gün), 175 ülke/bölge; tek seferlik ürün `exam_unlock` ("Tek Sınav Aç", açıklama mağaza metninden türetildi, satın alma seçeneği `buy`, "Satın al", çoklu adet kapalı), 174 ülke/bölge. Türkiye fiyatları vergi dahil 59,99 / 399,99 / 29,99 TRY. **Tuzak:** "Set prices" girilen tutarı vergi hariç sayıyor ve Türkiye'ye %20 KDV ekliyor (71,99 / 479,99 / 35,99); Türkiye satırı elle düzeltildi. Diğer ülkeler Play'in kur çevirisinde. Lisans testi: `emrebykr66` hesabının yetkisi yetmediği için ("izne ihtiyacınız var") ürün sahibi tarayıcıda hesap sahibi `buyukeryazilim@gmail.com` ile oturum açtı. Etkin "Lisans test kullanıcıları (otomatik olarak oluşturuldu)" listesinde zaten `buyukeryazilim@gmail.com` vardı; `emrebykr66@gmail.com` eklendi (2 kullanıcı), yanıt `RESPOND_NORMALLY`. versionCode 18 kullanıldı; 4.0.0'ın kapsamı büyürse son yayın 19+ ile derlenir._
- [x] **`OPS-02`** Sunucunun Play'e erişimi _(P0·S)_ — `vifi-831a8`'de "Google Play Android Developer API" etkin; Functions servis hesabı Play Console'a sipariş görüntüleme yetkisiyle eklenir. Canlı ayar → onayla. ⟶ `DEC-04` ✅ _Tamamlandı (2026-10-29, ürün sahibinin onayıyla, tarayıcıda): API `vifi-831a8`'de etkin. `purchaseExamUnlock`'ın çalışma hesabı Cloud Run'da "Default compute service account" olarak doğrulandı: `441043948320-compute@developer.gserviceaccount.com`. Bu hesap Play Console'a yalnız ViFi için davet edildi: "Finansal verileri görüntüleme" + Play'in zorunlu tuttuğu iki salt okunur izin (uygulama bilgileri, uygulama kalitesi); durumu Etkin. İzin yayılması saatler sürebilir; o sürede Play API 401/403 → function `internal` döner._

### Faz 1 — Geliştirme (4 paralel hat)

#### Hat A — İstemci billing katmanı

- [x] **`BIL-01`** Sözleşme: modeller, arayüzler, Fake'ler _(P0·S)_ — `core/model/StoreProduct.kt` (`StoreProductIds`, `SubscriptionPeriod`, `SubscriptionOption`, `PurchaseResult`), `SubscriptionError` (Türkçe `userMessage` + `analyticsReason`, `Throwable.userMessage`'a bağlı), `core/data/repository/SubscriptionService` + `ExamPurchaseService`, `fake/FakeSubscriptionService` + `FakeExamPurchaseService`. ⟶ `DEC-01`, `DEC-03` ✅ _Tamamlandı (2026-10-29): arayüzler Activity almaz (canlı uygulama ön plandaki Activity'yi kendisi alır, `BIL-02`); `StoreProductTest` (5) + `FakeSubscriptionServiceTest` (4); derleme, lint ve 3 × 574 birim testi geçti._
- [x] **`BIL-02`** Play Billing bağımlılığı + bağlantı yöneticisi _(P0·M)_ — Güncel kararlı Billing Library (+ `billing-ktx`), tek `BillingClient`, bağlan/yeniden bağlan, bekleyen tek seferlik satın almalar etkin, `PurchasesUpdatedListener` → Flow. ⟶ `BIL-01` ✅ _Tamamlandı (2026-10-29, Hat A, `c7cdd69`): Play Billing **9.1.0** (yalnız live; Play asgarisi 8); `billing-ktx` alınmadı (9.x Kotlin 2.2 stdlib istiyor, proje 2.0.21) → geri çağrılar elle coroutine'e sarıldı; tek `BillingClient`, ortak bağlantı denemesi, 1 s/2 s bekleyişle 3 deneme; Activity için mevcut `ForegroundActivityHolder`; R8 kuralı gerekmedi._
- [x] **`BIL-03`** `PlayBillingSubscriptionService` _(P0·M)_ — Ürün/plan detayları ve yerel fiyat, `isSubscribed` (SUBS sorgusu, onaylanmamışı onayla), `statusChanges()` Flow (bağlantı, güncellemeler, ön plana dönüş), `purchase`, `restore` (yeniden sorgu). ⟶ `BIL-02` ✅ _Tamamlandı (2026-10-29, Hat A): planlar aylık önce; okumada onaylanmamış satın alma onaylanır; durum açılışta, her satın alma güncellemesinde ve ön plana dönüşte (`AppForegroundEvents`) yeniden okunur; okuma hatasında son durum korunur._
- [x] **`BIL-04`** `SubscriptionStore` _(P0·S)_ — `@Singleton`, uygulama ömrü boyunca, oturumdan bağımsız; `isSubscribed: StateFlow<Boolean>` (`false` başlar), `options`, satın alma/geri yükleme sonrası yeniden okuma. ⟶ `BIL-01` ✅ _Tamamlandı (2026-10-29): `core/data/billing/SubscriptionStore.kt`, oluşturulduğu andan itibaren `@ApplicationScope`'ta durumu izler, okuma hatasında son durumu (başlangıçta kilitli) korur; `SubscriptionStoreTest` (5)._
- [x] **`BIL-05`** `PlayBillingExamPurchaseService` _(P0·M)_ — Satın almadan önce bekleyen sınav (DataStore) + `obfuscatedProfileId = sha256(examId)`; başarıda `purchaseExamUnlock` (Android dalı) → **sunucu kaydından sonra** tüket; `recoverPendingUnlock()`; PENDING; Functions hata eşlemesi. ⟶ `BIL-02`; canlı uçtan uca `SRV-03` ✅ _Tamamlandı (2026-10-29, Hat A): `obfuscatedProfileId = sha256(examId)`, `obfuscatedAccountId = sha256(uid)`, bekleyen sınav DataStore'da; `purchaseExamUnlock` `{platform: 'android', purchaseToken, productId, examId}`; yalnız sunucu başarısından sonra tüketir; tüketilmemiş eski satın almayı önce tamamlar; oturum kapalıyken ödeme alınmaz; 64 karakter sınırı. Sunucu dalı (`SRV-01`) gelene kadar canlıda `FailedVerification` döner, ödeme tüketilmediği için kaybolmaz._
- [x] **`BIL-06`** Mock flavor + `vifi_subscribed` + DI _(P1·S)_ — Live: PlayBilling*, mock: Fake* (Firebase/Billing başlamaz); `vifi_subscribed` aktif abonelikle başlatır. ⟶ `BIL-01` ✅ _Tamamlandı (2026-10-29): live `BillingModule` Play Billing uygulamalarını bağlıyor, yer tutucular silindi (Hat A). Önceki adım: mock `BillingModule` Fake'leri bağlıyor, `vifi_subscribed` (`LaunchArg.SUBSCRIBED`) eklendi. Live `BillingModule` şimdilik `UnavailableSubscriptionService` / `UnavailableExamPurchaseService` bağlıyor (Premium hiç aktif olmaz, satın alma `ProductUnavailable`) — canlı bir derleme ödemesiz Premium veremez. _

#### Hat B — Backend (ortak depo `ViFi-iOS/firebase/`) ⚠️ ONAY

- [x] **`SRV-01`** `purchaseExamUnlock` Google Play dalı _(P0·M)_ — `platform: 'android'`, `purchaseToken`, `productId`; Play Developer API `purchases.products.get` (paket `com.salticusteam.vifi`, `purchaseState` = satın alındı, ürün kimliği, `obfuscatedExternalProfileId` = `sha256(examId)`); replay anahtarı `orderId`; aynı `purchasedTransactions` + `unlocks` transaction'ı. iOS yolu değişmez. ⟶ `DEC-04` ✅ _Tamamlandı (2026-10-29, ortak depo `ViFi-iOS` `v-4.0.0`, `f0a8b67`, yayınlanmadı): `functions/googlePlay.js` (Play API `google-auth-library` ile doğrudan REST, zaten kurulu 9.15.1 açık bağımlılık yapıldı; girdi korumaları API'den önce; `orderId` yoksa `play-token-<jeton SHA-256>`); kayıt transaction'ı `functions/purchaseUnlock.js`'e taşındı ve iki platformca paylaşılıyor (iOS kayıtları birebir aynı, Android'e `store: 'google_play'`); `platform` yoksa iOS. Hata kodları: geçersiz `failed-precondition`, Play'e ulaşılamıyor `unavailable`, API erişimi yok (OPS-02) `internal`._
- [x] **`SRV-02`** Backend testleri _(P0·S)_ — `googlePlay.test.mjs`: girdi korumaları, sahte doğrulayıcıyla durum/ürün/profil eşleşmesi, replay, iOS yolunun bozulmadığı; emülatör. ⟶ `SRV-01` ✅ _Tamamlandı (2026-10-29): `tests/googlePlay.test.mjs` (15, ağsız) + `tests/purchaseExamUnlock.test.mjs` (12, Firestore emülatörü: iOS kayıt biçimi, Play etiketi, idempotent tekrar, başka sınav/kullanıcı için replay, coin'le açılmış sınav, callable'ın iki platform korumaları `onCall.run` ile); `npm test` 293 test, 0 başarısız (4 mevcut `todo`)._
- [x] **`SRV-03`** Function yayını _(P0·S)_ — `firebase deploy --only functions:purchaseExamUnlock`, **yalnız açık onayla**; ardından iOS satın almasının çalıştığı kontrol edilir. ⟶ `SRV-02`, `OPS-02` ✅ _Tamamlandı (2026-10-29, ürün sahibinin onayıyla): `ViFi-iOS` `8a1076e`'den yalnız `purchaseExamUnlock` yayınlandı (`--account emrebykr66@gmail.com`). Yayından önce `functions/.env`'deki `APP_APPLE_ID` ile canlı değerin aynı olduğu özetle doğrulandı. Yeni revizyon `purchaseexamunlock-00002-nac` trafiğin %100'ünü alıyor; geri dönüş için `00001-ray` duruyor. Canlı duman testi: oturumsuz çağrı iOS ve Android dallarında Türkçe `PERMISSION_DENIED` döndü, fonksiyon yükleniyor. **Doğrulanmadı:** gerçek iOS sandbox satın alması (iOS cihazı ve sandbox Apple Kimliği gerekiyor; iOS yolu emülatör testleriyle değişmedi) ve gerçek Android çağrısı (PQA-01'de)._

#### Hat C — Arayüz (BIL-01'deki Fake'lerle)

- [x] **`UI-01`** Paywall ekranı _(P0·M)_ — `PaywallScreen` + `PaywallViewModel` + `PaywallUiState`; iOS'taki tüm durumlar; Google Play otomatik yenileme/iptal metni; yasal bağlantılar; `paywall.*` testTag'leri; `screen_view` "Paywall". ⟶ `BIL-01` ✅ _Tamamlandı (2026-10-29, Hat C, `1a2fcf4`): `feature/premium/` (`PaywallScreen`, `PaywallViewModel`, `PaywallUiState`, `PremiumLinks`); otomatik yenileme metni Google Play'e uyarlandı (iOS'taki "24 saat önce" kuralı Apple'a özgü); onay işaretleri tema turuncusu; `AnalyticsScreen.PAYWALL`._
- [x] **`UI-02`** Kilit sheet'i: parayla aç + Premium'a geç _(P0·M)_ — "veya" + "Parayla bu sınavı aç", "Tek tek açmak yerine Premium'a geç"; sonuç modeli (açıldı / iptal / hata); meşgulken kilitli. ⟶ `BIL-01` ✅ _Tamamlandı (2026-10-29, Hat C): "veya" + "Parayla bu sınavı aç" + "Tek tek açmak yerine Premium'a geç"; sheet'in kapatma kilidindeki eski hata (ilk durumu okuyordu) düzeltildi._
- [x] **`UI-03`** Aboneliğin etkileri + Hesap Premium bölümü _(P0·M)_ — Aboneyken arşivde kilit yok, coin rozeti ve Hesap'taki coin satırı gizli; Hesap'ta "ViFi Premium'a Geç" / aktif durum + "Aboneliği Yönet" (Play abonelik sayfası). ⟶ `BIL-01` ✅ _Tamamlandı (2026-10-29, Hat C): aboneyken kilitsiz arşiv, gizli coin rozeti ve Hesap coin satırı; Hesap'ta Premium bölümü + "Aboneliği Yönet"._
- [x] **`UI-04`** Metinler ve erişilebilirlik _(P1·S)_ — iOS'taki 30 metnin Türkçe karşılıkları (App Store → Google Play), `cd_` metinleri; `strings.xml` yalnız bu hatta. ⟶ `UI-01`…`UI-03` ✅ _Tamamlandı (2026-10-29, Hat C): 30 metin; testTag'ler iOS kimlikleriyle aynı._

#### Hat D — Yasal metinler ve Play beyanları

- [x] **`LEG-01`** Hosting metinlerinin son kontrolü _(P2·S)_ — Kullanım koşulları + gizlilik zaten mağazadan bağımsız ve canlıda; paywall bağlantıları aynı adresler. ✅ _Tamamlandı (2026-10-29): canlı `/gizlilik` ve `/kullanim-kosullari` depodaki `ViFi-iOS/firebase/hosting/` ile birebir aynıydı, ikisi de Google Play'i anıyor; paywall, Hakkında ve Sınav Ekle aynı `LegalLinks` adreslerini açıyor. İki bulgu ürün sahibinin kararıyla depoda düzeltildi: (1) kullanım koşulları Apple'ın "dönem bitiminden en az 24 saat önce" yenileme kuralını Google Play için de söylüyordu → platforma göre ayrıldı (Android: dönem bitmeden iptal edilmezse yenilenir, ücret yeni dönemin başında). (2) Hesap silme ve gizlilik sayfaları sunucudaki satın alma kayıtlarını (`purchasedTransactions`: UID + sınav) anmıyordu → kayıt tekrar kullanımı önlemek için saklanır, hesapla bağı en geç 30 gün içinde (elle) kaldırılır. Yürürlük/güncelleme tarihleri 29 Ekim 2026 (`ViFi-iOS` `8a1076e`). Ürün sahibinin onayıyla `firebase deploy --only hosting` (29 Ekim 2026); canlı üç sayfanın depodaki metinle birebir aynı olduğu doğrulandı._
- [x] **`LEG-02`** Veri güvenliği beyanı _(P1·S)_ — "Satın alma geçmişi" (hesaba bağlı, uygulama işlevi, paylaşılmıyor): `PLAY-DATA-SAFETY.md` + Console formu (ürün sahibiyle). ⟶ `DEC-04` ✅ _Tamamlandı (2026-10-29): envantere "Finansal bilgiler → Satın alma geçmişi" (zorunlu değil, yalnız satın alan) eklendi. Abonelik cihazda doğrulandığı için toplanmıyor, ödeme bilgisi Google Play'de kalıyor, Play'e giden `obfuscated*` kimlikler not edildi. 4.0.0'da yeni analytics olayı yok (yalnız `Paywall` ekran adı). Console formu 4.0.0 yayınıyla ürün sahibince; silme satırı LEG-01 bulgusu (2)'ye bağlı._
- [x] **`LEG-03`** Mağaza girişi ve 4.0.0 sürüm notları _(P1·S)_ — Premium + parayla tek sınav açma; `PLAY-STORE-LISTING.md` (500 karakter). ⟶ `DEC-02` ✅ _Tamamlandı (2026-10-29, ürün sahibince onaylandı). Tam açıklamada "Uygulamada satın alma yoktur" cümlesi kaldırıldı; tek seferlik ödeme maddesi ve "ViFi PREMIUM" bölümü eklendi (fiyat yazılmadı; 1716/4000). 4.0.0 sürüm notu yazıldı (343/500). Uygulama içeriğinde dijital satın alma "Evet" oldu (içerik derecelendirme anketi yeniden gönderilir). Console'a 4.0.0 sürümüyle girilir._

### Faz 2 — Entegrasyon ve Testler

- [x] **`INT-01`** Canlı bağlama _(P0·M)_ — `AppRoot`/`ViFiNavHost`: abonelik durumunu uygulama ömrü boyunca izle, oturum açıkken açılışta `recoverPendingUnlock()` → açılan sınavı işaretle, Paywall rotası, Hesap ve kilit sheet'i bağlantıları. ⟶ `BIL-03`…`BIL-06`, `UI-01`…`UI-03` ✅ _Tamamlandı (2026-10-29): Hat A (`refs/heads/claude/angry-khorana-ecff03`, `c7cdd69`) ileri sarıldı, Hat C (`refs/heads/claude/ecstatic-bhaskara-2a8636`, `1a2fcf4`) `270c256` ile birleştirildi (çakışma yok). `ViFiRoute.Paywall` + NavHost yuvası; kilit sheet'i (Browse) ve Hesap'tan Paywall'a geçiş; `PaidUnlockRecoveryViewModel` oturum açıkken açılışta ve her ön plana dönüşte `recoverPendingUnlock()` → `ExamUnlocksStore.markUnlocked`; mock'a `AppForegroundEvents` bağlandı. Home'da kilitli sınav listelenmediği için Paywall bağlantısı yalnız Browse ve Hesap'ta._
- [x] **`INT-02`** Birim testleri _(P0·M)_ — Store, servisler (sahte BillingClient sarmalayıcısıyla), ViewModel'ler, hata eşleme; `AnalyticsLimitsTest`. ⟶ `INT-01` ✅ _Tamamlandı (2026-10-29): hatların testleri (billing 54, ViewModel/Store) + `PaidUnlockRecoveryViewModelTest` (2); birleşik dalda birim testleri liveDebug 654, liveRelease 654, mockDebug 647 — hepsi geçti._
- [x] **`INT-03`** UI testleri ve emülatör doğrulaması _(P0·M)_ — iOS `PremiumUITests`/`ExamUnlockUITests` karşılıkları; mock'ta uçtan uca, açık/koyu tema. ⟶ `INT-01` ✅ _Tamamlandı (2026-10-29): UI testleri mock 98/98, live 97/97. Mock'ta emülatörde (API 37) uçtan uca: kilit sheet'i → "Premium'a geç" → Paywall (yıllık varsayılan) → abone ol → Paywall kapanır, kilitler ve coin rozeti kalkar; Hesap aboneyken "ViFi Premium · Tüm sınavlar açık" + "Aboneliği Yönet" (coin satırı yok), abone değilken "ViFi Premium'a Geç" + coin; "Parayla bu sınavı aç" sınavı açar. Koyu tema Hat C'de kontrol edildi._

### Faz 3 — Gerçek Ortam Doğrulaması (yayın değildir)

- [x] **`PQA-01`** Lisans test hesabıyla satın alma senaryoları _(P0·M)_ — Abonelik al / yenile / iptal / süre dolumu, geri yükleme (yeniden kurulum), tek sınav satın al → sunucu kaydı → tüket, ağ kopunca kurtarma, bekleyen ödeme. Test kanalına yükleme **yalnız ürün sahibi onayıyla**. ⟶ `INT-03`, `SRV-03`, `OPS-01` ✅ _Tamamlandı (2026-10-29, gerçek cihaz adımı ürün sahibinin kararıyla atlandı): hazırlık — 18 (4.0.0) dahili testte. Kanalın test kullanıcısı listesi, ürün sahibinin onayıyla, iki hesabı içeren "Lisans test kullanıcıları" e-posta listesi yapıldı; kanal Etkin. Katılım bağlantısı: `https://play.google.com/apps/internaltest/4697407634647288328` (yalnız listedeki hesaplar katılabilir). Ürün sahibinin onayıyla emülatörde yürütülüyor (`Medium_Phone_API_37`, Play Store'da lisans test hesabı, sideload liveDebug — Google lisans test hesaplarına buna izin veriyor; App Check debug anahtarı "PQA emülatör 2026-10-29"). **Geçenler:** aylık abonelik satın alma (Play test kartı, ücretsiz) → Paywall kapanır, Hesap'ta Premium + "Aboneliği Yönet", coin rozeti ve kilitler kalkar; 5 dakikalık test yenilemesi gerçekleşti; "Aboneliği Yönet" Play abonelik sayfasını açıyor; iptal → dönem sonunda uygulama öne gelince Premium kapandı, kilitler ve coin geri geldi. **Bulgu 1:** tek sınav satın alma hiç başlamıyordu (`The exam id is too long to bind to a Google Play purchase`): sınav kimlikleri 64 karakterden uzun → istemci + sunucu SHA-256 bağına geçirildi (DEC-04 notu; `ViFi-iOS` `a5819a1`, onaylı yeniden yayın `purchaseexamunlock-00003-nih`). **Geçenler (düzeltmeden sonra):** tek sınav satın alma → Play API doğrulaması (OPS-02 erişimi çalışıyor) → `purchasedTransactions/GPA.…` + `users/<uid>/unlocks/<sınav>` (`store: google_play`) → sınav açıldı ve yeniden başlatmada da açık; yavaş test kartıyla bekleyen ödeme → "Satın alma onay bekliyor" → onaylanınca uygulama öne geldiğinde kurtarma kaydı yaptı, sınav açıldı. **Bulgu 2 (ürün sahibinin onayıyla düzeltildi):** Play tüketmeyi `ERROR (6)` ile reddedince (1) hatırlanan sınav siliniyordu → özet bağı yüzünden satın alma sahipsiz kalıyordu, (2) sınav tüketme denemeleri yüzünden ~85 sn geç açılıyordu, (3) `BillingClientConnection` aynı yanıtı iki kez `resume` ediyordu → sınav tüketilene kadar hatırlanır ve tüketilmemiş kilit varken yeni alım başlatılmaz, kayıttan sonra sınav hemen açılır ve tüketme arka planda yapılır, Play geri çağrıları tek sefer `resume` edilir. **Gözlemler:** emülatör ağı App Check token'ı alırken takılınca sunucu çağrısı ~90 sn gecikti (uygulama hatası değil); güncelleme sonrası sistemin "Updating…" ekranından dönülen listede açılmış sınav bir kez kilitli göründü, temiz açılışta tekrarlanmadı; bekleyen ödeme onaylanınca açık kalan kilit penceresi "onay bekliyor" yazmaya devam ediyordu → ürün sahibinin onayıyla düzeltildi: kurtarma sınavı açınca pencere kapanır ve sınav açılır (emülatörde yavaş test kartıyla doğrulandı). **Atlanan (ürün sahibi kararı, 29 Ekim):** Play'den kurulan imzalı sürümle telefonda duman testi; Play imzası + Play Integrity / App Check yolu bu turda denenmedi (aynı App Check kurulumu 3.1.0'da üretimde çalışıyor). Bu turdaki düzeltmeler dahili testteki 18'de yok; son 4.0.0 yayını 19+ ile derlenir._
- [x] **`PQA-02`** İade ve iptal etkisi _(P1·S)_ — İade/iptal sonrası abonelik hakkı düşer; tüketilmiş tek sınav kilidi kalır (iOS ile aynı kabul). ⟶ `PQA-01` ✅ _Tamamlandı (2026-10-29, emülatörde, ürün sahibinin onayıyla Play Console sipariş yönetiminden): yeni aylık test aboneliği (`GPA.3361-…`) "iade + yararlanma hakkını kaldır" ile iade edildi → uygulama öne gelince Premium düştü (Hesap'ta "ViFi Premium'a Geç" + coin), satın alınmamış sınavlar yeniden kilitlendi; tüketilmiş tek sınav siparişi (`GPA.3385-…`, "2009 YAZ OKULU FİNAL") yalnız iade edildi → sınav açık kaldı (sunucu iadeyi dinlemiyor, kabul edilen davranış). İptal ve süre dolumu PQA-01'de görülmüştü._

### Faz 4 — Yayın  ⚠️ (ürün sahibiyle · her dış adım ayrı onay)

> Önem ve uygulama sırasıyla. P0 = yayın engeli, P1 = yayınla birlikte yapılmalı, P2 = isteğe bağlı. Ayrıntı §4'te.

- [x] **`RLS-01`** `v-4.0.0` dallarının push'u _(P0·S)_ — ViFi-Android (yayın hazırlığı commit'leri) + ViFi-iOS (`a5819a1` = canlı `purchaseExamUnlock`'ın kodu). Bu Mac'te depolara yazma yetkili GitHub kimliği yok → ürün sahibi yapar. Yayınlanan derleme ve canlı function kodu uzak depoda güvende olmalı. ✅ _Tamamlandı (2026-10-29): push'u ürün sahibi yaptı; GitHub'da ViFi-Android `v-4.0.0` → `6c8e7a8`, ViFi-iOS `v-4.0.0` → `a5819a1` (`gh api` ile doğrulandı). Canlı `purchaseExamUnlock`'ın kodu artık uzak depoda; `ViFi-iOS` master'da `purchaseExamUnlock` hâlâ yok (§6 uyarısı geçerli)._
- [x] **`RLS-02`** Release AAB 19 (4.0.0) derle ve imzala _(P0·S)_ — `bundleLiveRelease` yükleme anahtarıyla; kontrol: versionCode 19 / 4.0.0, imza SHA-1 `75:61…`, `com.android.vending.BILLING`, dil bölmesi kapalı, R8 eşleme dosyası pakette. Dosya `play-store/ViFi-4.0.0-19.aab` (git dışı). **Yalnız bu adım için açık onayla.** ⟶ `RLS-01` ✅ _Tamamlandı (2026-10-29, ürün sahibinin onayıyla): `bundleLiveRelease`, kaynak `b712704` (uygulama kodu GitHub'daki `6c8e7a8` ile aynı, sonrası yalnız belge). İmza: yükleme anahtarı SHA-1 `75:61…:25:D9`, `jarsigner` doğruladı. Manifest: `com.salticusteam.vifi`, versionCode 19, 4.0.0, min 24 / target 36, debuggable değil, `allowBackup=false`; izinler 18 ile aynı, 17'ye (3.1.0) göre tek yeni izin `com.android.vending.BILLING`. `BundleConfig`: `LANGUAGE` bölmesi kapalı. Pakette R8 eşleme dosyası (`proguard.map`) ve Baseline Profile var. Dosya `play-store/ViFi-4.0.0-19.aab` (9,6 MB, git dışı, SHA-256 `a5c2da3f7466b171…`). Play'e yüklenmedi (`RLS-03`)._
- [x] **`RLS-03`** Dahili teste yükle + Play'den kurulan sürümle duman testi _(P0·M)_ — 19'u dahili teste yükle ve yayınla (onayla); gerçek telefonda ya da Play Store'lu emülatörde Play'den kur: giriş (Play Integrity / App Check), arşiv, sınav açma, aylık abonelik + tek sınav satın alma (lisans test hesabı, test kartı), geri yükleme, İngilizce telefonda Türkçe kilidi (LOC-01). PQA-01'de atlanan adım. ⟶ `RLS-02` ✅ _Kapatıldı (2026-10-29, ürün sahibi kararı; telefon yok, Play derlemesinde giriş/satın alma `RLS-09`'a kaldı): ürün sahibi AAB'yi kütüphaneye yükledi; taslağa 19 eklendi (18 dışarıda), sürüm adı "19 (4.0.0)", 4.0.0 sürüm notu (tr-TR). Önizleme: hata yok, desteklenen cihaz kaybı 0 (12.540 telefon), yeni kurulum 4,05 MB / güncelleme 508 KB; tek uyarı kütüphanelerin yerel kodu için hata ayıklama sembolü yok (engel değil). Ürün sahibinin onayıyla 29 Ekim 17:32'de dahili teste yayınlandı (son sürüm 19 (4.0.0)); üretim 17 (3.1.0) değişmedi. Emülatör denemesi (telefon yok): `buyukeryazilim` dahili teste katıldı, 19 Play'den kuruldu (yükleyen Play Store, imza `b2:f6…:75:90`, debuggable değil, parçalar base + arm64 + xxhdpi, dil parçası yok, Türkçe kaynaklar base'de); uygulama çökmeden açıldı, İngilizce cihazda giriş ekranı Türkçe. Giriş başarısız: App Check `403 App attestation failed` (emülatörde Play Integrity uygulama bütünlüğünü değerlendirmiyor) → giriş, arşiv ve satın alma Play derlemesinde denenemedi. Lansman öncesi rapor dahili test sürümü için oluşmadı. Yan bulgu: paket listesinde 10 (2.0) hâlâ "Etkin" (eski bir test kanalı) → `RLS-09`'da politika durumuyla birlikte bakılır._
- [x] **`RLS-04`** Veri güvenliği formu: "Satın alma geçmişi" _(P0·S)_ — Finansal bilgiler → Satın alma geçmişi: toplanır, paylaşılmaz, isteğe bağlı, uygulama işlevi (`PLAY-DATA-SAFETY.md`). Kaydedilir, üretim sürümüyle incelemeye gider. ⟶ `LEG-02` ✅ _Tamamlandı (2026-10-29): Console → Uygulama içeriği → Veri güvenliği; 1–2. adımlar değişmedi. Veri türleri: Finansal bilgiler → "İşlem geçmişi" (Türkçe Console'da "Purchase history"nin karşılığı: "Kullanıcının alışveriş veya ödeme işlemleri ile ilgili bilgiler") işaretlendi; diğer finansal türler boş. Cevaplar: toplandı, paylaşılmadı, kısa süreli işlenmiyor, kullanıcı seçebilir (isteğe bağlı), amaç uygulama işlevselliği. Önizleme: üçüncü taraflarla paylaşım yok. Kaydedildi → Yayınlama özetinde bekliyor, incelemeye gönderilmedi (`RLS-08`)._
- [x] **`RLS-05`** İçerik derecelendirme anketi _(P0·S)_ — "Dijital satın alma: Evet" ile yeniden doldurulup gönderilir (`PLAY-STORE-LISTING.md` §2). ⟶ `LEG-03` ✅ _Tamamlandı (2026-10-29): yeni anket önceki cevapları taşımadığı için 28 Ekim'de gönderilen anketin özetine göre baştan dolduruldu (kategori "Diğer Tüm Uygulama Türleri", IARC iletişim e-postası aynı). Tek fark: Çeşitli → dijital ürün satın alma **Evet**, ek soru "şansa dayalı satın alma (ganimet kutusu)" Hayır; nakit/NFT ödülü Hayır (coin paraya çevrilmez). Derecelendirmeler değişmedi (ClassInd tüm yaşlar, ESRB 10+, PEGI ebeveyn rehberliği, USK 6, IARC 12+); etkileşimli öğelere "Uygulama İçi Satın Alma İşlemleri" eklendi. Kaydedildi → Yayınlama özetinde, incelemeye gönderilmedi (`RLS-08`)._
- [ ] **`RLS-06`** Mağaza girişi 4.0.0 _(P0·S)_ — Tam açıklama (Premium + parayla tek sınav; "Uygulamada satın alma yoktur" kalkar), gerekirse kısa açıklama (`PLAY-STORE-LISTING.md` §3). Sürüm notu `RLS-08`'de girilir. ⟶ `LEG-03`
- [ ] **`RLS-07`** %15 hizmet ücreti programına kayıt _(P1·S)_ — Ödeme profili → hesap grubu oluştur + 15% hizmet ücreti şartlarını kabul et. Kayıt olmazsa `exam_unlock` satışlarında kesinti %30 (abonelikler zaten %15). Sözleşme kabulü → ürün sahibi. İlk satıştan önce.
- [ ] **`RLS-08`** Üretime yayın + incelemeye gönder _(P0·S)_ — 19'u üretime al (%100; kademeli yayın yok), 4.0.0 sürüm notu (`PLAY-STORE-LISTING.md`), Yayın özeti'nden `RLS-04`…`RLS-06` ile birlikte incelemeye gönder; yönetilen yayınlama kapalı → onayda kendiliğinden yayında. **Onayla.** ⟶ `RLS-03`…`RLS-06`
- [ ] **`RLS-09`** Yayın sonrası doğrulama _(P1·S)_ — İnceleme onayı; gerçek telefonda Play'den inen 4.0.0 ile giriş, arşiv, sınav açma, abonelik ve tek sınav satın alma (`RLS-03`'te emülatörde App Check yüzünden yapılamadı), QA-01…QA-04 (REL-02); Politika durumu, Android vitals (çökme/ANR), ilk gerçek siparişler ve `purchaseExamUnlock` hataları. ⟶ `RLS-08`
- [ ] **`RLS-10`** Güncelleme önerisi _(P1·S)_ — `config/appUpdate/android` → `latestVersion: "4.0.0"` (3.1.0 kullanıcılarına kapatılabilir öneri; `minimumVersion` 3.1.0 kalır). Canlı RTDB yazımı → **onayla**, 4.0.0 üretimde %100 görününce. ⟶ `RLS-09`
- [ ] **`RLS-11`** ABD vergi formu (W-8BEN) _(P2·S)_ — Ödeme profili → ABD vergi bilgileri. Verilmezse yalnız ABD'li alıcıların kazancından stopaj kesilebilir; giriş +90 olduğu için etkisi pratikte yok. Ürün sahibi; bağımsız.

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
- **Adımlar:** `exam_unlock` ürün detayları → bekleyen sınavı DataStore'a yaz → `launchBillingFlow` (`obfuscatedProfileId = sha256(examId)`, `obfuscatedAccountId` = uid'nin özeti) → `PURCHASED` ise `purchaseExamUnlock(platform = android, purchaseToken, productId, examId)` → başarıyla `consumeAsync` → bekleyeni sil → `SUCCESS`; `PENDING` → bekleyen kalır; iptal → bekleyen silinir. `recoverPendingUnlock`: `queryPurchasesAsync(INAPP)` içinde tüketilmemiş `exam_unlock` → sunucu → tüket → sınav kimliğini döndür.
- **Tuzak:** Tüketme **yalnız** sunucu kaydından sonra; aksi halde ödeme alınır ama sınav açılmaz. Tüketilmemiş satın alma aynı ürünün yeniden alınmasını engeller — kurtarma bu yüzden açılışta şart.
- **Kabul kriteri:** Ağ satın almadan sonra koparsa bir sonraki açılışta sınav açılıyor; aynı jeton iki kez kullanılamıyor.

#### `BIL-06` — Mock flavor + `vifi_subscribed` + DI
- Live Hilt modülü PlayBilling*'i, mock modülü Fake*'i bağlar (mock'ta Billing de başlamaz, `BackendStartup` deseni). `LaunchArg.SUBSCRIBED("vifi_subscribed")`.
- **Durum (2026-10-29):** `app/src/{live,mock}/…/core/data/billing/BillingModule.kt` ayrı modüller (`RepositoryModule`'e dokunulmadı); live şimdilik `UnavailableBilling.kt` yer tutucularını bağlıyor. Hat A, `BIL-03`/`BIL-05` bitince yalnız live `BillingModule`'ü değiştirir ve `UnavailableBilling.kt`'yi siler; Hat C bu dosyalara dokunmaz.

#### `SRV-01` — `purchaseExamUnlock` Google Play dalı
- **Adımlar:** `data.platform === 'android'` dalı: `googleapis` (veya `@googleapis/androidpublisher`) ile `purchases.products.get({ packageName, productId, token })`; kontroller: `purchaseState === 0`, `productId === 'exam_unlock'`, `obfuscatedExternalProfileId === sha256(examId)`; `transactionId = orderId`; mevcut transaction bloğu (`purchasedTransactions`, `unlocks` + `store: 'google_play'`). Hata kodları iOS ile aynı (`failed-precondition` doğrulama, `not-found` sınav, `permission-denied` oturum).
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


#### `RLS-01`…`RLS-11` — Yayın
- **Sıra:** `RLS-01` → `RLS-02` → `RLS-03` → `RLS-04`…`RLS-07` (birbirinden bağımsız, Console'da) → `RLS-08` → `RLS-09` → `RLS-10`; `RLS-11` istendiği zaman.
- **`RLS-02` kontrolleri:** `keytool -printcert -jarfile <aab>` ile yükleme anahtarı (SHA-1 `75:61…`); `aapt2`/bundle manifestinde versionCode 19, `BILLING` izni; `BundleConfig.pb`'de `LANGUAGE` bölmesi kapalı; derleme, lint ve testler temiz.
- **`RLS-03` ortamı:** Firebase App Check'te (Android, Play Integrity → Gelişmiş ayarlar) yalnız `PLAY_RECOGNIZED` zorunlu; `LICENSED` ve cihaz bütünlüğü istenmiyor ("Don't explicitly check device integrity level", 29 Ekim'de görüldü). Buna rağmen emülatörde App Check token'ı alınamıyor (29 Ekim denemesi: Play'den kurulan 19, `403 App attestation failed`, Phone Auth "Firebase App Check token is invalid"): Play Integrity güvenilir bulmadığı cihazda uygulama bütünlüğünü değerlendirmiyor, `PLAY_RECOGNIZED` şartı karşılanmıyor. Release derlemesinde debug sağlayıcı yok. Giriş ve satın alma Play derlemesinde yalnız gerçek telefonda denenebilir; App Check ayarını gevşetmek çözüm değildir (canlı güvenlik). Telefondaki Google hesabı lisans test listesinde olmalı (yoksa satın alma gerçek ücretlenir). Telefonda ViFi 3.1.0 Play'den kuruluysa dahili test bağlantısıyla 19'a güncellenir.
- **`RLS-04`…`RLS-06`:** Metinler ürün sahibince onaylı ve hazır (`PLAY-DATA-SAFETY.md`, `PLAY-STORE-LISTING.md`); Console'da kaydedilir, gönderim `RLS-08`'de tek seferde.
- **`RLS-08`:** Üretimdeki 17 (3.1.0) yerine 19; 18 yalnız dahili testte kalır. İnceleme süresi belirsiz (3.1.0'da aynı akşam onaylandı).
- **`RLS-10`:** Erken yazılırsa güncellemeyi Play'de henüz göremeyen kullanıcılara boşuna öneri çıkar.
---

## 5. Riskler ve Açık Noktalar

1. **Backend sahipliği:** İlk kez Android için ortak backend'e kod yazılıyor (`DEC-04`, `SRV-*`); iOS satın alma yolu kesinlikle değişmemeli, deploy onaylı.
2. **Cihazda doğrulanan abonelik:** iOS ile aynı kabul; root'lu cihazda atlatılabilir. Dosyalar zaten +90 kullanıcılara açık olduğu için ek risk getirmez.
3. **Ürün etkinleştirme sırası:** Play ürünleri, faturalandırma izni olan bir derleme yüklenmeden etkinleşmeyebilir (`OPS-01`) → test kanalına erken bir yükleme gerekebilir (ürün sahibi onayı).
4. **Platformlar arası hak yok:** iOS'ta abone olan kullanıcı Android'de Premium görmez; destek metni/SSS buna hazır olmalı.
5. **Fiyat teyidi:** iOS fiyatları yalnız yerel test dosyasından okunabildi (`DEC-02`).

## 6. Yayın Kontrol Listesi (29 Ekim 2026 denetimi)

Uçtan uca denetimde kod (derleme, lint, R8, birim + UI testleri), backend (3 function, Hosting, SHA'lar, güncelleme
politikası) ve Play temeli (ürünler, dahili test, politika durumu) hazır bulundu. Bu bölüm denetim notlarıdır; yapılacak
yayın işleri §3 Faz 4'e (`RLS-01`…`RLS-11`) taşındı.

### 6.1 Yayından önce

| # | Ne | Durum |
|---|---|---|
| Y1 | `v-4.0.0` dallarının push'u: ViFi-Android + ViFi-iOS (canlı `purchaseExamUnlock`'ın kodu `ViFi-iOS` `a5819a1`) | → `RLS-01` |
| Y2 | versionCode 19 (18 dahili testte kullanıldı; PQA düzeltmeleri 18'de yok) | ✅ 29 Ekim |
| Y3 | Veri güvenliği formu: "Finansal bilgiler → Satın alma geçmişi" eklenir (`PLAY-DATA-SAFETY.md`) | → `RLS-04` |
| Y4 | İçerik derecelendirme anketi: dijital satın alma "Evet" ile yeniden gönderilir (`PLAY-STORE-LISTING.md` §2) | → `RLS-05` |
| Y5 | Mağaza girişi: 4.0.0 tam açıklama + sürüm notu; canlıdaki "Uygulamada satın alma yoktur" kalkar (`PLAY-STORE-LISTING.md` §3) | → `RLS-06`, `RLS-08` |
| Y6 | Öneri: 19 önce dahili teste → Play'den kurulan imzalı sürümle duman testi (Play imzası + Play Integrity + R8 + satın alma; PQA'da atlanan adım) → üretim | → `RLS-03`, `RLS-08` |

> ⚠️ `ViFi-iOS` master'da `purchaseExamUnlock` hiç yok (Premium commit'i `7639530` yalnız `v-4.0.0`'da). Master'dan
> tüm functions deploy edilirse CLI canlıdaki `purchaseExamUnlock`'ı silmeyi önerir; kabul edilirse iOS ve Android'de
> parayla sınav açma durur. Dal birleşene kadar functions deploy'u yalnız `v-4.0.0`'dan ya da tek fonksiyonla
> (`--only functions:<ad>`) yapılır.

### 6.2 Bilgi notları (engel değil, tek tek kontrol edilecek)

| # | Konu | Ne yapılacak | Ne zaman / kim | Durum |
|---|---|---|---|---|
| B1 | Güncelleme önerisi | `config/appUpdate/android` → `latestVersion: "4.0.0"` (3.1.0 kullanıcılarına kapatılabilir güncelleme önerisi); `minimumVersion` 3.1.0 kalır. Canlı RTDB yazımı → ayrıca açık onay. | 4.0.0 üretimde %100 görününce | → `RLS-10`. Kontrol edildi (29 Ekim): canlıda `latestVersion` ve `minimumVersion` 3.1.0, `storeURL` Play sayfası; uygulama anahtarları doğru okuyor. Yazım yayından sonra. |
| B2 | iOS sandbox satın alması | Gerçek iPhone + sandbox hesabıyla tek sınav satın alma → canlı `purchaseExamUnlock` (`00003-nih`) Apple yolu → `purchasedTransactions/<Apple işlem no>` (`store` alanı yok) + `unlocks` kaydı. Simülatörde olmaz (yerel `.storekit` işlemleri Apple imzalı değil). Önkoşul: iOS debug derlemesi için App Check debug anahtarı, sandbox hesabına girişi ürün sahibi yapar. Android'i etkilemez. | iOS 4.0.0 yayınından önce, iOS tarafı | Kod tarafı kontrol edildi (29 Ekim): iOS'ta sandbox testi 3.1.0 döneminde gerçek cihazda geçmişti; o günden beri `appStore.js` ve iOS istemcisi değişmedi (aynı kütüphane 3.1.0, önce Production sonra Sandbox → App Review işlemleri de doğrulanır); `index.js`'teki değişiklik Apple yolunu yalnız ayrı fonksiyona taşıdı, kayıt şekli aynı; satın alma testleri 31/31. Kalan: canlı revizyonda gerçek Apple işlemiyle tek deneme. |
| B3 | Ödeme profili | Play Console → Hesabı yönetme → Ödeme profili: banka hesabı, vergi bilgileri, hizmet ücreti programı. Sözleşme kabulü ve vergi formu ürün sahibinin işidir. | Yayından önce, ürün sahibi | → `RLS-07`, `RLS-11`. Kontrol edildi (29 Ekim): banka hesabı (TR) tanımlı, hesap türü Bireysel, ad ve adres doğrulanmış, ekstre adı ve destek e-postası dolu, uyarı yok. **Açık 1:** %15 hizmet ücreti programına kayıt yok (hesap grubu oluşturma + şartları kabul); kayıt olmazsa tek seferlik ürünlerde (`exam_unlock`) kesinti %30, abonelikler zaten %15. **Açık 2:** ABD vergi bilgisi (W-8BEN) boş; verilmezse yalnız ABD'li alıcılardan gelen kazançtan stopaj kesilebilir (giriş +90 olduğu için etkisi pratikte yok). Türkiye vergi bilgisi alanı Google hizmeti satın alırken istenir, satış için gerekmiyor. |
| B4 | Lint uyarıları | 56 uyarı (0 hata) gözden geçirilir; gerçek sorun düzeltilir, sürüm yükseltmeleri 4.0.0 sonrasına. | Kod tarafı | Kontrol edildi, 1 düzeltme (29 Ekim): 38'i kütüphane/Gradle sürüm bildirimi (Kotlin 2.0→2.4, Compose BoM, Firebase BoM 33→35, AGP 8→9, OkHttp 4→5 gibi büyük atlamalar) → 4.0.0 sonrasına ayrı yükseltme işi. **Gerçek sorun (1):** `AppBundleLocaleChanges` — App Bundle dile göre bölünüyor; kütüphanelerin Türkçe kaynakları `tr` parçasına gider ve Play onu yalnız dil listesinde Türkçe olan cihaza kurar → İngilizce cihazda LOC-01'in Türkçe kilidi Play kurulumunda kütüphane metinlerini Türkçe gösteremez (sideload/emülatör testleri bunu yakalamaz). **Düzeltildi (29 Ekim):** `app/build.gradle`'a `bundle { language { enableSplit = false } }` (yalnız `tr` paketlendiği için boyut etkisi yok); debug AAB'nin `BundleConfig.pb`'sinde `LANGUAGE` bölmesi kapalı (`negate`) doğrulandı. Lint uyarıyı vermeye devam ediyor (dedektör Groovy ayarını okumuyor; yanlış alarm). **Gerek yok:** `MissingKeepAnnotation` (`ExamKind` için `proguard-rules.pro`'da `-keepnames` zaten var), `PluralsCandidate` ×2 (Türkçede tekil/çoğul aynı), `UseKtx` ×7 (yazım tercihi), `IconDuplicates` ×5 (`icon.png` uygulama içi logo, zararsız), `ExifInterface` ×2 (minSdk 24'te platform sürümü yeterli; androidx'e geçiş yükseltme işiyle). |
