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

- **Reklam kimliği (AD_ID):** Reklam SDK'sı kaldırıldı (FM-08), fakat `firebase-analytics`
  `play-services-ads-identifier`'ı getiriyor; release APK'da `AD_ID`, `ACCESS_ADSERVICES_AD_ID` ve `ACCESS_ADSERVICES_ATTRIBUTION` izinleri var (aapt2 ile doğrulandı).
  Reklam yapılmıyor; Play beyanında "reklam kimliği kullanılmıyor" denecekse izin manifest'ten
  `tools:node="remove"` ile kaldırılmalı ve Analytics'te reklam kimliği toplama kapatılmalı. **Karar gerekli.**
- **Paylaşım:** Veri üçüncü taraflarla paylaşılmıyor; Google (Firebase) hizmet sağlayıcı olarak işler (Play'de "paylaşım" sayılmaz).
- **Aktarımda şifreleme:** Evet (Firebase, HTTPS/TLS).
- **Silme talebi:** Uygulama içi hesap silme (iOS'ta var; Android'de Hesap alanı) — Play hesap silme URL'si de gerekli.
- **Coin bakiyesi / açılmış sınavlar:** Uygulama içi durum; satın alma yok (abonelik kapsam dışı), finansal veri sayılmaz.

## Release küçültme (R8) ile ilgili

- `release`: `minifyEnabled true`, `shrinkResources true`, `proguard-android-optimize.txt` + `app/proguard-rules.pro`.
- İmzalama anahtarları repoda yok: `keystore.properties` (git'e girmez) veya CI secret.
- Mock örnek verisi release'e girmez (`mockRelease` variant'ı yok — FM-18).
