# Play Console — Veri Güvenliği (Data Safety) Envanteri

> **Durum:** Taslak envanter (FM-20). Formu Play Console'da doldurup göndermek ürün sahibinin işidir.
> Kaynak: iOS `ViFi/Resources/PrivacyInfo.xcprivacy` (gizlilik etiketi) + Android hedef mimarisi (`core/model`).
> Özellikler eklendikçe güncellenir; aşağıdaki "Android'e özgü" satırları Play'e özeldir.

## Toplanan veriler

| Play kategorisi → veri türü | Ne / nereden | Amaç | Hesaba bağlı | Zorunlu mu |
|---|---|---|---|---|
| Kişisel bilgiler → Telefon numarası | Firebase Phone Auth (+90) giriş; profil kaydı | Uygulama işlevi (hesap) | Evet | Evet |
| Kişisel bilgiler → Ad | Profil: ad, soyad | Uygulama işlevi | Evet | Evet (profil kapısı) |
| Kişisel bilgiler → Kullanıcı kimlikleri | Firebase Auth UID | Uygulama işlevi, analiz | Evet | Evet |
| Kişisel bilgiler → Diğer bilgiler | Profil: cinsiyet, üniversite/fakülte/bölüm | Uygulama işlevi, analiz (segment) | Evet | Evet |
| Uygulama etkinliği → Uygulama etkileşimleri | Firebase Analytics olayları (göz atma, sınav açma, coin, giriş hunisi) | Analiz | Evet (UID ile) | Hayır* |
| Uygulama etkinliği → Diğer kullanıcı içeriği | Kullanıcının yüklediği sınav görselleri/PDF'leri (moderasyon için) | Uygulama işlevi | Evet | Hayır (isteğe bağlı) |
| Uygulama bilgileri ve performans → Tanılama | Giriş hatası tanılaması (`recordLoginDiagnostic`: hata kodu, aşama; numara yalnızca salted-hash + son 4) | Uygulama işlevi (destek) | Hayır | Hayır |
| Cihaz veya diğer kimlikler | FCM kayıt token'ı (bildirim), App Check / Play Integrity, Analytics uygulama örneği kimliği | Uygulama işlevi, analiz | Evet | Evet |

\* Analytics toplama kapatma seçeneği şu an yok; formda "isteğe bağlı değil" olarak işaretlenmeli ya da bir kapatma ayarı eklenmeli (karar ürün sahibinde).

## Android'e özgü notlar

- **Reklam kimliği (AD_ID):** Kullanılmıyor. Reklam SDK'sı kaldırıldı (FM-08); Analytics'in eklediği `AD_ID`,
  `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION` izinleri manifest'ten `tools:node="remove"` ile
  çıkarıldı ve `google_analytics_adid_collection_enabled=false`; ayrıca
  `google_analytics_default_allow_ad_personalization_signals=false` (reklam kişiselleştirme sinyali yok, AN-08).
  Birleşik (merged) manifest'te AD_ID izni bulunmadığı doğrulandı. Play beyanı: "reklam kimliği kullanılmıyor".
- **Paylaşım:** Veri üçüncü taraflarla paylaşılmıyor; Google (Firebase) hizmet sağlayıcı olarak işler (Play'de "paylaşım" sayılmaz).
- **Aktarımda şifreleme:** Evet (Firebase, HTTPS/TLS).
- **Silme talebi:** Uygulama içi hesap silme (iOS'ta var; Android'de Hesap alanı) — Play hesap silme URL'si de gerekli.
- **Coin bakiyesi / açılmış sınavlar:** Uygulama içi durum; satın alma yok (abonelik kapsam dışı), finansal veri sayılmaz.

## Analytics envanteri (AN-04/05/07, AN-11)

iOS 3.1.0 ile aynı adlar; kaynak `core/model/AnalyticsEvent.kt` + `AnalyticsEventMapping.kt`.

- **Olaylar (23):** `screen_view`, `browse_level`, `exam_open`, `recent_exam_open`, `unlock_sheet_shown`,
  `earn_coins_tapped`, `exam_unlock`, `exam_unlock_failed`, `exam_submit`, `exam_submit_failed`,
  `login_code_requested`, `login_success`, `login_failed`, `onboarding_complete`, `profile_completed`,
  `profile_updated`, `search_used`, `sign_out`, `account_deleted`, `update_prompt_shown`, `update_action`,
  `submission_approved`, `submission_rejected`. Parametreler yalnız düşük-kardinaliteli sabitler: ekran adı,
  katalog seviyesi, dosya biçimi (PDF/JPG), dosya sayısı, coin miktarı, tier, sabit hata `reason`/`step`,
  bayraklar. **Arşiv/ders adı, telefon, isim veya serbest metin gönderilmez.**
- **Kimlik:** `setUserId` = Firebase Auth UID (oturum açılınca; çıkışta `null`).
- **Kullanıcı özellikleri (5):** `university_id`, `faculty_id`, `department_id` (kanonik slug `[a-z0-9-]`),
  `gender` (`female`/`male`), `is_admin` (`true`/`false`). Profil yokken slug/cinsiyet temizlenir; çıkışta hepsi
  temizlenir. Segmentasyon sunucu tarafında Analytics audience ile yapılır; istemci yalnız `all` topic'ine abone
  (iOS paritesi, P18).
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
