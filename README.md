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
| `live` | Ortak `vifi-831a8` backend'i | Debug'da App Check **debug token**'ı Firebase Console'a eklenmeli (Logcat: `DebugAppCheckProvider`) |
| `mock` | Paket içi örnek veri, Firebase hiç başlamaz | Yalnız debug; UI testleri ve demo için. Senaryo bayrakları: `--ez vifi_no_coins true` vb. (`core/util/LaunchArgs.kt`) |

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
