# ViFi Android

Üniversitelerin geçmiş sınavlarını tek yerde toplayan sınav arşivi uygulamasının Android istemcisi.
**Kotlin + Jetpack Compose + MVVM + Hilt** üzerinde; sürüm serisi iOS ile aynıdır. Bugünkü geliştirme **4.1.0 (20)**,
`v-4.1.0` dalında: iOS `v-4.1.0`'ın hedefli bildirim sistemi.

- 4.1.0 iş listesi (hedefli bildirim sistemi): [`ANDROID-4.1.0-ROADMAP.md`](ANDROID-4.1.0-ROADMAP.md), iOS olgu eki [`ANDROID-4.1.0-DETAIL.md`](ANDROID-4.1.0-DETAIL.md)
- 4.0.0 iş listesi (ViFi Premium): [`ANDROID-4.0.0-ROADMAP.md`](ANDROID-4.0.0-ROADMAP.md)
- iOS v3.1.0 paritesi (tamam): [`ANDROID-PARITY-ROADMAP.md`](ANDROID-PARITY-ROADMAP.md) (özet), [`ANDROID-PARITY-DETAIL.md`](ANDROID-PARITY-DETAIL.md) (ayrıntı)
- Geliştirme kuralları: [`CLAUDE.md`](CLAUDE.md) ve [`.claude/rules/`](.claude/rules/)
- Play Veri Güvenliği envanteri: [`PLAY-DATA-SAFETY.md`](PLAY-DATA-SAFETY.md); mağaza metinleri: [`PLAY-STORE-LISTING.md`](PLAY-STORE-LISTING.md)
- 4.1.0 Play politikası kontrolü ve gizlilik metni önerisi: [`PLAY-POLICY-4.1.0.md`](PLAY-POLICY-4.1.0.md), [`PRIVACY-POLICY-4.1.0.md`](PRIVACY-POLICY-4.1.0.md)

## Gereklilikler

- Android Studio (güncel) ve Android SDK 36
- Gradle'ı çalıştırmak için **JDK 17–21** (Android Studio'nun gömülü JBR 25'i Gradle 8.14 ile çalışmaz), ör.:
  `export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"`
- `app/google-services.json` (ortak `vifi-831a8` Firebase projesi)
- Yalnız `live` ve isteğe bağlı: admin konum seçicisi için Google Maps/Places anahtarı. Proje kökündeki
  `local.properties`'e `MAPS_API_KEY=<anahtar>` satırı olarak girer (git dışı); anahtarın değeri depoya, commit'e ya da
  loga yazılmaz. Anahtarsız derleme de geçer; seçici "Harita anahtarı yapılandırılmamış" der. Anahtarı ürün sahibi
  Google Cloud'da paket adı ve SHA-1'lerle kısıtlı oluşturur (`NOPS-01`).

## Uyumluluk

Android 7.0 (API 24) ve sonrası; hedef API 36. Bildirim izni (`POST_NOTIFICATIONS`) Android 13 ve sonrasında uygulama
içinden sorulur; 12 ve altında izin sistemde açık geldiği için yalnız kampanya onayı sorulur.

## Sürümler (flavor)

| Flavor | Ne | Not |
|---|---|---|
| `live` | Ortak `vifi-831a8` backend'i | Debug'da App Check **debug token**'ı ve geliştirme makinesinin debug SHA-1/SHA-256'sı Firebase Console'a eklenmeli; telefonla giriş Console'daki test numarası + `111111` ile denenir. Admin konum seçicisi (Google Maps/Places) yalnız bu flavor'da; `MAPS_API_KEY` ister |
| `mock` | Paket içi örnek veri; Firebase ve Google Maps SDK'sı hiç başlamaz | Yalnız debug; UI testleri ve demo için. Örnek kullanıcı girişli açılır; konum seçici sabit bir örnek yer döner. Senaryo bayrakları (`core/util/LaunchArgs.kt`): `--ez <ad> true` ile `vifi_signed_out`, `vifi_profile_incomplete`, `vifi_force_update`, `vifi_optional_update`, `vifi_delete_requires_reauth`, `vifi_no_coins`, `vifi_show_onboarding`, `vifi_subscribed`, `vifi_admin`, `vifi_notifications_prompt`, `vifi_notifications_denied`, `vifi_notifications_reask`, `vifi_consent_reask`; metin değerli `--es vifi_open_notification <id>` (ör. `p-paywall`) |

## Komutlar

```bash
./gradlew assembleLiveDebug                 # canlı debug APK
./gradlew assembleMockDebug                 # örnek verili debug APK
./gradlew assembleLiveRelease               # R8 ile küçültülmüş release (imza: keystore.properties, repoya girmez)
./gradlew test                              # birim testler
./gradlew lintLiveDebug lintMockDebug       # lint
./gradlew connectedLiveDebugAndroidTest connectedMockDebugAndroidTest   # cihaz/emülatör testleri
```

> Cihaz testleri bittikten sonra uygulama cihazda bırakılır (`gradle.properties`): kaldırılırsa App Check debug
> token'ı silinir ve Console'a yenisinin eklenmesi gerekir.

```bash
# Mock'ta bir senaryoyla aç (ör. profil tamamlama ekranı)
adb shell am start -n com.salticusteam.vifi/.app.MainActivity --ez vifi_profile_incomplete true
```

```bash
# Mock'ta bir bildirimin push'una dokunulmuş gibi aç (bildirim sayfası + vurgu)
adb shell am start -n com.salticusteam.vifi/.app.MainActivity --es vifi_open_notification p-location
```

## Durum

- **Hazır:** telefonla giriş (+90), profil tamamlama/düzenleme, canlı arşiv (isimli `vifi` Firestore), sınav
  görüntüleyicileri, Sınav Ekle + moderasyon, coin kilidi, ViFi Premium (Play Billing), onboarding, Hakkında,
  güncelleme kapısı ve Hesap ekranı (hesap silme dahil).
- **4.1.0 hedefli bildirim sistemi** (`v-4.1.0`; backend ortak ve canlıda, Android yeni backend yazmaz):
  - Push: `general` / `promo` / `system` kanalları, görselli bildirim, dokunuşla bildirim sayfasının açılıp bildirimin
    vurgulanması; cihaz kaydı `users/<uid>/devices/<kurulumId>`, çıkışta ve hesap silmede silinir.
  - Bildirim sayfası: genel duyurular + kişiye özel bildirimler, cihazlar arası okundu bilgisi, görsel ve 5 aksiyon
    (Sınava Git, Premium'a Göz At, Sınav Ekle, Bağlantıyı Aç, Konuma Git).
  - İzin (1/2) ve kampanya onayı (2/2) sayfaları, retten en erken 30 gün sonra bir kez tekrar; Hesap › Bildirim
    Ayarları.
  - Admin: Hesap › Bildirim Gönder (kitle, görsel, aksiyon, zamanlama, "Bana test gönder"), Gönderim Geçmişi
    (zamanlanmışı iptal) ve haritadan konum seçici.
- **Yayın:** 3.1.0 (17) 28 Ekim 2026'da Play'de üretime çıktı; 4.0.0 (19) 29 Ekim'de onaylanıp %100 yayına çıktı.
  4.1.0'da geliştirme, testler, Maps anahtarı ve canlı doğrulama (`NQA-01`, `NQA-02`) tamam; yayında (Faz 4) önkoşul (`NRL-01`) kapandı, sırada dalın push'u (`NRL-02`).
  Yayın yalnız ürün sahibinin onayıyla. Ayrıntı ve kalan işler yol haritalarında.
