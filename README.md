# ViFi Android

Üniversitelerin geçmiş sınavlarını tek yerde toplayan sınav arşivi uygulamasının Android istemcisi.
iOS v3.1.0 paritesi hedefiyle **Kotlin + Jetpack Compose + MVVM + Hilt** üzerine yeniden kuruluyor.

- İlerleme ve kalan işler: [`ANDROID-PARITY-ROADMAP.md`](ANDROID-PARITY-ROADMAP.md) (özet), [`ANDROID-PARITY-DETAIL.md`](ANDROID-PARITY-DETAIL.md) (ayrıntı)
- Geliştirme kuralları: [`CLAUDE.md`](CLAUDE.md) ve [`.claude/rules/`](.claude/rules/)
- Play Veri Güvenliği envanteri: [`PLAY-DATA-SAFETY.md`](PLAY-DATA-SAFETY.md)

## Gereklilikler

- Android Studio (güncel) ve Android SDK 35
- Gradle'ı çalıştırmak için **JDK 17–21** (Android Studio'nun gömülü JBR 25'i Gradle 8.9 ile çalışmaz), ör.:
  `export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"`
- `app/google-services.json` (ortak `vifi-831a8` Firebase projesi)

## Uyumluluk

Android 7.0 (API 24) ve sonrası; hedef API 35.

## Sürümler (flavor)

| Flavor | Ne | Not |
|---|---|---|
| `live` | Ortak `vifi-831a8` backend'i | Debug'da App Check **debug token**'ı ve geliştirme makinesinin debug SHA-1/SHA-256'sı Firebase Console'a eklenmeli; telefonla giriş Console'daki test numarası + `111111` ile denenir |
| `mock` | Paket içi örnek veri, Firebase hiç başlamaz | Yalnız debug; UI testleri ve demo için. Örnek kullanıcı girişli açılır. Senaryo bayrakları: `--ez vifi_signed_out true`, `vifi_profile_incomplete`, `vifi_force_update` (`core/util/LaunchArgs.kt`) |

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

## Durum

Telefonla giriş (+90), canlı arşiv, sınav görüntüleyicileri, coin kilidi, bildirimler, onboarding, profil ve Hesap ekranı (hesap silme dahil) hazır; ayrıntı ve kalan işler yol
haritasında. Google Play geliştirici hesabı Kasım 2024'ten beri kaldırılmış durumda: release imzası ve mağaza yayını
yol haritasının sonundaki `REL-01` kalemindedir.
