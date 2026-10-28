# ViFi Android — iOS 3.1.0 Parite Yol Haritası (Detay)

> Master özet: **[ANDROID-PARITY-ROADMAP.md](ANDROID-PARITY-ROADMAP.md)**. Bu dosya her iş kaleminin tam adımlarını, teknolojisini, bağımlılıklarını, tuzaklarını ve kabul kriterini içerir.

## İçindekiler
- Firebase Backend, Android Kayıt & Config Deltaları (`backend-ops`)
- Temel & Modernizasyon (Mimari İskelet) (`foundation`)
- Telefon Girişi + App Check + Login Diagnostics (`auth`)
- Profil (zorunlu tamamlama kapısı + düzenleme + katalog) (`profile`)
- Arşiv/Gezinme (RTDB→Firestore göçü + offline önbellek + dosya indirme) (`archive`)
- Sınav Görüntüleyiciler (Görsel + PDF) + Ekran Koruması (`examviewers`)
- Coin Ekonomisi + Sınav Kilitleme/Açma (`coin`)
- Sınav Ekleme (kullanıcı gönderimleri) (`addexam`)
- Moderasyon (admin onay/yayın + coin kredisi + arşiv silme) (`moderation`)
- Bildirimler (FCM + izin + duyuru kutusu) (`notifications`)
- Zorunlu/Opsiyonel Güncelleme (force update) (`forceupdate`)
- Onboarding (ilk açılış tanıtım turu) (`onboarding`)
- Analytics (genişletilmiş event + user segment) (`analytics`)
- Hesap + Yerelleştirme + Hakkında + Tasarım Sistemi (`account`)


---

## Firebase Backend, Android Kayıt & Config Deltaları  ·  `backend-ops`

**Özet:** iOS (v3.1.0) ile Android aynı Firebase projesini (vifi-831a8 / 441043948320) paylaşıyor; backend sözleşmesi ORTAK ve platformdan bağımsız. iOS; Phone Auth (+90) + App Check (App Attest) + named 'vifi' Firestore (eur3) + RTDB config + europe-west1 Cloud Functions üzerine kurulu. Android v2.5 ise email/parola auth, App Check YOK, Firestore YOK, eski RTDB 'Universitiess' ağacını okuyor, Parse-FCM + AdMob içeriyor ve 2018 tarihli AndroidX-öncesi Firebase SDK'ları kullanıyor. Bu alandaki iş: Android'i vifi-831a8'de Play Integrity App Check için kaydetmek, Phone Auth'un ürettiği auth.token.phone_number'a uyumlu hale getirmek, named 'vifi' Firestore + europe-west1 Functions client bağlantılarını kurmak, RTDB'ye config/appUpdate/android (+ database.rules.json validasyonu) ve config/rewards/android kararını eklemek, mevcut index'lerin yeterli olduğunu doğrulamak ve App Check enforce sırasını yönetmek. Firestore index'leri ve Cloud Functions YENİDEN KULLANILIR (yeni backend yazılmaz); yalnız RTDB android config node'ları + kurallar backend tarafında eklenir ve bunlar KULLANICI ONAYI gerektirir.

**Android mevcut durum:** build.gradle: compileSdk/targetSdk 28, minSdk 19, versionName 2.5 (code 16); com.android.support (AndroidX öncesi); firebase-database 16.1.0, firebase-storage 16.1.0, firebase-auth 16.2.0, firebase-core 16.0.8, firebase-messaging 17.4.0 (2018); play-services-ads 17.2.0; parse-android + parse-fcm-android 1.17.3; picasso. Kök build.gradle: AGP 3.4.2, google-services 4.2.0, repo jcenter() (ÖLÜ) + google(). google-services.json: Firestore/App Check için ekstra alan yok (zaten taşımaz), ads_service status 2, tek SHA-1 certificate_hash b76a1b58... (SHA-256 YOK). LoginActivity: FirebaseAuth.signInWithEmailAndPassword / createUserWithEmailAndPassword (phone auth YOK → auth.token.phone_number üretmez → tüm ortak Firestore/Storage/RTDB kuralları Android'i REDDEDER). Firestore hiç kullanılmıyor. RTDB: FirebaseDatabase.getInstance() (default, firebase_url doğru) ile 'Universitiess' ve 'Announcement…

**Dokunulan backend sözleşmesi:** Yeniden kullanılan (değiştirilmeyen): firestore.rules (sadece +90 phone + admin claim bakar), storage.rules, firestore.indexes.json (3 index zaten deploy), Cloud Functions unlockExam + recordLoginDiagnostic (europe-west1), named 'vifi' Firestore (eur3). Backend tarafında EKLENEN (onay gerektirir): database.rules.json içine config/appUpdate/android node + validasyonu (ios bloğunun birebir eşi, storeURL=Play Store), olası config/rewards/android node, RTDB config/appUpdate/android ve config/rewards/* değerlerinin Console'dan yazımı. App Check Console: Play Integrity provider etkinleştirme + debug token + Firestore enforce zamanlaması.

**Android'e özgü riskler:**
- App Check Auth/Storage/RTDB için zaten ENFORCE: Android'e Play Integrity provider + Console'da kayıtlı debug token eklenmeden hiçbir Firebase çağrısı (giriş dahil) geçmez; mevcut v2.5 bu yüzden zaten kısmen kırık olabilir.
- Phone Auth Android'de otomatik/sessiz doğrulama için Play Integrity (eski SafetyNet kaldırıldı) + reCAPTCHA fallback ister; Console'da SHA-256 kayıtlı değilse ve Play Integrity bağlı değilse SMS gönderimi reclaim/hata verir.
- targetSdk 28, Google Play'in 2025 yükleme şartlarının (targetSdk 34/35) altında; Play Integrity API ve modern Firebase SDK'ları targetSdk yükseltmeyi ve AndroidX geçişini zorunlu kılar.
- Firestore DEFAULT instance Android'de yanlışlıkla kullanılırsa legacy Datastore-US DB'ye düşer ve veri gelmez; MUTLAKA getInstance(app,'vifi') kullanılmalı.
- jcenter() ölü, Parse 1.17.3 oradan çözülüyor; mavenCentral geçişinde Parse zaten kaldırılacağı için sorun yok ama repo değişimi şart.
- RTDB 'Universitiess' hâlâ +90 okuma açık (rollback için) ama Firestore cutover sonrası silinecek; Android buna bağımlı kalırsa arşiv birden boşalır.
- database.rules.json'da config altında yalnız appUpdate.ios admin-write tanımlı; config/rewards yazımı root .write=false ile reddedilir (Console/Admin SDK bypass eder) — rewards/android client'tan yazılamaz, Console'dan girilir.
- FLAG_SECURE / ekran koruması ve R8/ProGuard Firebase+Play Integrity kurallarını gerektirir (minify açılırsa keep kuralları eklenmeli).
- POST_NOTIFICATIONS izni (Android 13+) FCM bildirimleri için runtime istenmeli; aksi halde duyurular görünmez.

### İş Kalemleri

#### `BE-00` — Firebase/Gradle yığınını modernize et (BoM, AndroidX, mavenCentral) — tüm backend işlerinin ön koşulu  _(P0 · efor: L)_
- **iOS davranışı:** iOS modern Firebase iOS SDK (SPM) kullanır; Android karşılığı güncel BoM + AndroidX.
- **iOS karşılığı/kaynak:** Yok (platforma özgü altyapı).
- **Android mevcut:** AGP 3.4.2, google-services 4.2.0, jcenter() (ölü), com.android.support, firebase-* 16.x (2018), compileSdk/targetSdk 28.
- **Android teknoloji:** Firebase Android BoM (com.google.firebase:firebase-bom), AndroidX (androidx.appcompat vb.), mavenCentral(), google-services plugin 4.4.x, AGP güncel, R8.
- **Adımlar:**
  - Kök build.gradle: jcenter() kaldır, mavenCentral() + google() bırak; AGP'yi güncel sürüme, com.google.gms:google-services'i 4.4.2+'ya çıkar.
  - gradle.properties: android.useAndroidX=true, android.enableJetifier=true ekle; tüm com.android.support.* bağımlılıklarını androidx.* karşılıklarına taşı.
  - app/build.gradle'a Firebase BoM ekle: implementation platform('com.google.firebase:firebase-bom:33.x') ve alt modülleri sürümsüz yaz.
  - compileSdk/targetSdk'i 34/35'e, minSdk'i en az 21'e çıkar (Play Integrity ve Play yükleme şartı); multidex zaten açık.
  - versionCode/versionName şemasını koru (android kendi versiyonu).
- **Gradle/bağımlılık:** platform('com.google.firebase:firebase-bom'), com.google.gms:google-services:4.4.2, mavenCentral, AndroidX migration.
- **Bağımlılık:** Build/Altyapı alanı (AndroidX + targetSdk geçişi)
- **Tuzaklar:** AndroidX geçişi tüm modülleri kırar; Parse/AdMob/Picasso kaldırımıyla birlikte tek PR'da yapılması pratik. Jetifier eski Parse/pdf-viewer'ı dönüştürmeye çalışır.
- **Kabul kriteri:** Proje mavenCentral+BoM ile derlenir; hiçbir com.android.support bağımlılığı kalmaz; targetSdk>=34; firebase-bom ile Firestore/Functions/AppCheck modülleri çözülür.

#### `BE-01` — Android'i Console'da App Check + Phone Auth için kaydet: SHA-256/SHA-1 parmak izleri + google-services.json yenileme  _(P0 · efor: S)_
- **iOS davranışı:** iOS App Attest/DeviceCheck ile App Check'e kayıtlı; Phone Auth APNs ile doğrulanır.
- **iOS karşılığı/kaynak:** ViFiAppCheckProviderFactory; GoogleService-Info.plist.
- **Android mevcut:** google-services.json'da tek SHA-1 (certificate_hash b76a1b58...), SHA-256 yok; Play Integrity/Phone Auth için yetersiz.
- **Android teknoloji:** Firebase Console > Project Settings > Android app; gradle signingReport; google-services.json.
- **Adımlar:**
  - `./gradlew signingReport` ile debug ve release keystore'ların SHA-1 + SHA-256'sını al; Play App Signing kullanılıyorsa Play Console > App integrity'den app signing + upload SHA-256'larını da al.
  - Firebase Console > vifi-831a8 > Project Settings > com.salticusteam.vifi uygulamasına tüm SHA-1 ve SHA-256 parmak izlerini ekle.
  - Güncel google-services.json'u indir ve app/ altına koy (Phone Auth reCAPTCHA/oauth client ve SHA alanlarını senkronlar). Not: google-services.json Firestore için ayrı alan TAŞIMAZ; Firestore project_id ile çalışır.
  - Authentication > Sign-in method'da Phone sağlayıcısının açık olduğunu doğrula.
- **Gradle/bağımlılık:** Yok (Console + google-services.json).
- **Bağımlılık:** Auth/Giriş alanı (Phone Auth)
- **Tuzaklar:** SHA-256 eksikse Play Integrity App Check token üretemez ve enforce'lu Auth/Storage/RTDB çağrıları reddedilir. Play App Signing'de üç ayrı sertifika (upload, app signing, debug) olabilir — hepsi eklenmeli.
- **Kabul kriteri:** Console'da Android uygulamasında SHA-256 görünür; güncel google-services.json repoda; debug/release derleme Phone Auth kod gönderimini başlatabiliyor.

#### `BE-02` — App Check — Play Integrity provider entegrasyonu (release) + Debug provider (debug) kodu  _(P0 · efor: M)_
- **iOS davranışı:** Release App Attest/DeviceCheck, DEBUG/simülatör AppCheckDebugProvider; factory FirebaseApp.configure()'dan önce kurulur.
- **iOS karşılığı/kaynak:** ViFiAppCheckProviderFactory + AppEnvironment.live() AppCheck.setAppCheckProviderFactory.
- **Android mevcut:** App Check hiç yok.
- **Android teknoloji:** FirebaseAppCheck.getInstance().installAppCheckProviderFactory(...), PlayIntegrityAppCheckProviderFactory.getInstance(), DebugAppCheckProviderFactory.getInstance(); Application sınıfı.
- **Adımlar:**
  - app/build.gradle: implementation 'com.google.firebase:firebase-appcheck-playintegrity' (release) ve debugImplementation 'com.google.firebase:firebase-appcheck-debug'.
  - Bir Application alt sınıfı oluştur (veya mevcut başlangıç noktasında): FirebaseApp.initializeApp(this) sonrası FirebaseAppCheck.getInstance().installAppCheckProviderFactory(...) çağır — release'te PlayIntegrityAppCheckProviderFactory.getInstance(), debug'ta DebugAppCheckProviderFactory.getInstance() (BuildConfig.DEBUG ile ayır).
  - AndroidManifest application android:name'ini bu Application sınıfına ayarla.
  - Debug çalıştırmada Logcat'teki App Check debug token'ını Console > App Check > Apps > Manage debug tokens'a ekle.
  - İstersen setTokenAutoRefreshEnabled(true) ile token'ı canlı tut.
- **Gradle/bağımlılık:** firebase-appcheck-playintegrity (release), firebase-appcheck-debug (debug).
- **Bağımlılık:** BE-00, BE-01
- **Tuzaklar:** Provider FirebaseApp başlatıldıktan sonra ama ilk Firestore/Functions/Storage erişiminden önce kurulmalı. Emülatör Play Integrity üretemez → debug provider + token şart. R8 açılırsa Play Integrity sınıfları için keep kuralları gerekir.
- **Kabul kriteri:** Debug'ta debug token'la, release'te Play Integrity ile App Check token üretiliyor; enforce'lu Storage/RTDB okumaları 403 almıyor.

#### `BE-03` — App Check Console: Play Integrity etkinleştir + enforce sırasını yönet (Firestore en son)  _(P0 · efor: S)_
- **Durum (2026-10-28):** ✔️ Tamamlandı. Firestore metrikleri (son 7 gün): %94 doğrulanmış, eski sürüm 0, bilinmeyen kaynak 0, geçersiz token %6 → Firestore ~23:00'te Enforce'a alındı; emülatörde (debug token) arşiv, sınav listesi ve cüzdan okundu. Dört servis (Storage, RTDB, Auth, Firestore) enforce. Cloud Functions'ta `enforceAppCheck` kapalı (`unlockExam`, `purchaseExamUnlock`; `recordLoginDiagnostic` bilinçli olarak kapalı) — ortak backend, iOS deposunda.
- **iOS davranışı:** firestore.rules yorumu: Firestore App Check enforce'u yalnızca Firestore okuyan sürüm çıktıktan SONRA açılır; Storage/RTDB/Auth zaten enforce.
- **iOS karşılığı/kaynak:** Console yapılandırması (kodda değil).
- **Android mevcut:** Android App Check'e hiç dahil değil.
- **Android teknoloji:** Firebase Console > App Check (Play Integrity provider, enforcement toggles).
- **Adımlar:**
  - Console > App Check > APIs: Android uygulaması için Play Integrity'yi etkinleştir.
  - Storage/RTDB/Auth enforce'u iOS için zaten açık olduğundan, Android provider'ı yayınlanmadan önce debug token + Play Integrity'nin çalıştığını metriklerden doğrula (eski Android trafiği enforce'ta reddedilir).
  - Firestore için: Android'in named 'vifi' Firestore okuyan sürümü Play'de yeterli adopsiyona ulaşana kadar Firestore App Check'i 'Monitoring'de bırak; sonra iOS adopsiyonuyla birlikte 'Enforce'a al.
  - App Check metriklerinde 'verified vs outdated client' oranını Android sürümü çıktıktan sonra izle.
- **Gradle/bağımlılık:** Yok (Console).
- **Bağımlılık:** BE-02, Browse/Arşiv alanı (Firestore cutover)
- **Tuzaklar:** Firestore enforce'u erken açmak iOS dahil tüm eski istemcileri kırabilir; sıralamaya uyulmalı. Enforce anahtarı servis bazındadır (uygulama bazında değil): açıldığında iOS ve Android birlikte etkilenir. Play Integrity sağlayıcı kaydı ve SHA izleri ise yalnız Android uygulama kaydına aittir; iOS'ta değişiklik gerektirmez (2026-10-28 kontrolü: ViFi-iOS'ta Android imzasına bağlı kod/doküman yok).
- **Kabul kriteri:** Play Integrity Android için aktif; Firestore enforce'u yalnız Firestore okuyan sürümler yaygınlaşınca açılıyor.

#### `BE-04` — Phone Auth (+90) client akışı — ortak kuralların beklediği auth.token.phone_number'ı üret  _(P0 · efor: M)_
- **iOS davranışı:** Sadece +90 (^\+90[0-9]{10}$); tüm Firestore/Storage/RTDB kuralları isTurkishPhoneUser()/phone_number regex'ine bakar.
- **iOS karşılığı/kaynak:** FirebaseAuthService (phone sign-in), LoginDiagnostics, SessionStore.
- **Android mevcut:** LoginActivity email/parola (signInWithEmailAndPassword) → phone_number claim ÜRETMEZ → ortak backend Android'i reddeder.
- **Android teknoloji:** PhoneAuthProvider.verifyPhoneNumber(PhoneAuthOptions), PhoneAuthProvider.OnVerificationStateChangedCallbacks, PhoneAuthCredential, FirebaseAuth.signInWithCredential.
- **Adımlar:**
  - Email/parola akışını (signUp/signIn) kaldır; +90 giriş ekranı kur, girişi ^\+90[0-9]{10}$ ile doğrula.
  - PhoneAuthOptions.newBuilder(auth).setPhoneNumber('+90...').setTimeout(60, SECONDS).setActivity(this).setCallbacks(callbacks) ile PhoneAuthProvider.verifyPhoneNumber çağır.
  - OnVerificationStateChangedCallbacks: onCodeSent(verificationId) → 6 haneli kod ekranı; onVerificationCompleted(credential) → signInWithCredential; onVerificationFailed(FirebaseException) → hata.
  - Kod ekranında PhoneAuthProvider.getCredential(verificationId, code) ile signInWithCredential.
  - Backend doğrulaması: giriş sonrası basit bir Firestore/RTDB okumasıyla phone_number claim'inin oluştuğunu doğrula.
- **Gradle/bağımlılık:** firebase-auth (BoM); Play Integrity ile çalışır.
- **Bağımlılık:** BE-01, BE-02, Auth/Giriş alanı (UI)
- **Tuzaklar:** App Check Auth'ta enforce olduğundan verifyPhoneNumber Play Integrity token'ı ister; emülatörde debug token + reCAPTCHA fallback gerekir. Backend 'Error code: 39' (SMS anti-abuse) senaryosu BE-09'da raporlanır.
- **Kabul kriteri:** +90 numarayla SMS kodu gelir, signInWithCredential başarılı; FirebaseUser.getPhoneNumber() +90 döner; korumalı Firestore okuması geçer.

#### `BE-05` — Named 'vifi' Firestore client bağlantısı (eur3) — DEFAULT DB kullanma  _(P0 · efor: S)_
- **iOS davranışı:** ViFiFirestore.shared = Firestore.firestore(database:'vifi') + PersistentCacheSettings; (default) DB legacy Datastore-US, kullanılmaz.
- **iOS karşılığı/kaynak:** FirebaseFirestoreProvider.swift.
- **Android mevcut:** Firestore hiç kullanılmıyor.
- **Android teknoloji:** FirebaseFirestore.getInstance(FirebaseApp.getInstance(), "vifi"), FirebaseFirestoreSettings + PersistentCacheSettings.newBuilder().
- **Adımlar:**
  - app/build.gradle: implementation 'com.google.firebase:firebase-firestore' (BoM).
  - Tekil bir sağlayıcı yaz: val db = FirebaseFirestore.getInstance(FirebaseApp.getInstance(), "vifi"); ayarları ilk erişimden önce uygula: setLocalCacheSettings(PersistentCacheSettings.newBuilder().build()).
  - Bu instance'ı tüm Firestore okumaları için tek noktadan paylaş (settings iki kez atanırsa çöker).
  - DEFAULT getInstance() ÇAĞRISINI hiçbir yerde kullanma (legacy Datastore-US'a düşer).
- **Gradle/bağımlılık:** firebase-firestore (BoM).
- **Bağımlılık:** BE-00, Browse/Arşiv alanı
- **Tuzaklar:** named database overload (getInstance(app, databaseId)) güncel SDK gerektirir — BE-00 şart. Persistence varsayılan açık; cache ayarı ilk sorgudan önce verilmeli.
- **Kabul kriteri:** Android named 'vifi' DB'den catalogNodes/exams okur; (default) DB'ye hiç dokunmaz; offline cache çalışır.

#### `BE-06` — RTDB config/appUpdate/android node + database.rules.json validasyonu (zorunlu/opsiyonel güncelleme)  _(P1 · efor: M)_
- **iOS davranışı:** config/appUpdate/ios {minimumVersion, latestVersion, storeURL, message}; minimumVersion>installed=zorunlu (latch), latestVersion>installed=opsiyonel; storeURL yoksa fallback. .write admin, version regex, storeURL https, message<=300, $other=false.
- **iOS karşılığı/kaynak:** FirebaseRemoteConfigReader (observe .value), RemoteAppUpdateChecker, database.rules.json config/appUpdate/ios.
- **Android mevcut:** RTDB'de yalnız config/appUpdate/ios var; android kardeşi YOK. Android force-update'i GooglePlayAppVersion ile Play Store HTML scrape ediyor (kırılgan).
- **Android teknoloji:** FirebaseDatabase.getInstance().getReference('config/appUpdate/android'), ValueEventListener, keepSynced(true); database.rules.json.
- **Adımlar:**
  - database.rules.json > config/appUpdate altına 'ios' bloğunun birebir eşi 'android' bloğu ekle: .write admin, .validate newData.hasChildren(), minimumVersion/latestVersion regex ^[0-9]+(\.[0-9]+){0,3}$, storeURL isString && length<=500 && beginsWith('https://'), message length<=300, $other .validate false.
  - Kuralları deploy et (firebase deploy --only database) — ONAY gerektirir.
  - Console/Admin SDK ile config/appUpdate/android değerlerini yaz: storeURL='https://play.google.com/store/apps/details?id=com.salticusteam.vifi', minimumVersion/latestVersion android şemasına göre, message (TR).
  - Android client: FirebaseRemoteConfigReader karşılığı config/appUpdate/android'i ValueEventListener + keepSynced(true) ile dinle; minimumVersion>versionName=zorunlu (latch'li; okunamayan değer gate'i AÇMASIN), latestVersion>versionName=opsiyonel; AppVersion karşılaştırmasını iOS AppVersion.isVersion mantığıyla eşle.
  - GooglePlayAppVersion HTML scrape'ini kaldır (Play artık bu HTML'i kararlı vermiyor).
- **Gradle/bağımlılık:** firebase-database (BoM, mevcut).
- **Bağımlılık:** Update/Güncelleme alanı (UI gate)
- **Tuzaklar:** storeURL Play Store linki https ile başladığından mevcut .validate'e uyar. android node ios'tan bağımsız versiyonlanır (android versionName 2.5 vs iOS 3.1.0). Latch davranışı (unreadable okuma gate'i açmaz) client'ta birebir uygulanmalı.
- **Kabul kriteri:** config/appUpdate/android node'u admin dışı yazımı reddeder; geçersiz storeURL/versiyon validasyondan geçmez; Android eski sürümde zorunlu gate'i gösterir, güncel sürümde geçer.

#### `BE-07` — config/rewards: Android için 'android' node mu yoksa 'ios' reuse mu — karar + (gerekirse) kural  _(P2 · efor: S)_
- **iOS davranışı:** FirebaseRewardsConfigReader config/rewards/ios {examUploadCoins:int>0, tiers: SubmissionTier→int}; coin ödül kademeleri Console'dan.
- **iOS karşılığı/kaynak:** FirebaseRewardsConfigReader.
- **Android mevcut:** Android coin ekonomisi/rewards yok; config/rewards/android da yok.
- **Android teknoloji:** FirebaseDatabase getReference('config/rewards/android' | 'config/rewards/ios'), ValueEventListener.
- **Adımlar:**
  - Kararı netleştir: (A) Android client config/rewards/ios'u okur (tek kaynak, ekstra Console bakımı yok) veya (B) config/rewards/android node'u açılır (platform ayrımı net).
  - (B) seçilirse database.rules.json config altında rewards için admin-write/validate gerekmez çünkü Console/Admin SDK yazar ve root .write=false client yazımını zaten engeller; yalnız değerleri Console'dan gir.
  - Android rewards reader'ı seçilen path'e göre ValueEventListener ile bağla; examUploadCoins>0 ve tiers (SubmissionTier.rawValue→int) parse et.
  - UNLOCK_COST'un client tarafında 100 (ExamLock.unlockCost ile aynı) sabitlendiğini doğrula — Cloud Function'daki 100 ile eşleşmeli.
- **Gradle/bağımlılık:** firebase-database (mevcut).
- **Bağımlılık:** Coin ekonomisi alanı, AddExam/Sınav Yükleme alanı
- **Tuzaklar:** rewards node'u client'tan yazılamaz; yanlışlıkla android altına yazma denemesi rules tarafından reddedilir.
- **Kabul kriteri:** Android seçilen rewards path'inden examUploadCoins ve tiers'ı okur; UNLOCK_COST=100 ile tutarlı.

#### `BE-08` — unlockExam Cloud Function'ını Android'den çağır (europe-west1) — coin ile sınav açma  _(P1 · efor: M)_
- **iOS davranışı:** httpsCallable('unlockExam').call(['examId':id]) => {alreadyUnlocked:Bool, balance:Int}; UNLOCK_COST=100; hata: failedPrecondition=yetersiz coin, notFound, permissionDenied/unauthenticated=giriş yok, unavailable/deadlineExceeded=offline.
- **iOS karşılığı/kaynak:** FirebaseExamUnlockService.
- **Android mevcut:** Functions client yok; coin/unlock akışı yok.
- **Android teknoloji:** FirebaseFunctions.getInstance("europe-west1"), getHttpsCallable("unlockExam").call(mapOf("examId" to id)), FirebaseFunctionsException.Code.
- **Adımlar:**
  - app/build.gradle: implementation 'com.google.firebase:firebase-functions' (BoM).
  - val functions = FirebaseFunctions.getInstance("europe-west1"); functions.getHttpsCallable("unlockExam").call(mapOf("examId" to examId)).
  - Yanıtı parse et: result.data as Map → alreadyUnlocked:Boolean, balance:Number(Int).
  - Hata eşleme: FirebaseFunctionsException.code == FAILED_PRECONDITION → yetersiz coin, NOT_FOUND → sınav yok, PERMISSION_DENIED/UNAUTHENTICATED → giriş gerekli, UNAVAILABLE/DEADLINE_EXCEEDED → offline; diğer → bilinmeyen.
  - İdempotent: tekrar çağrı alreadyUnlocked=true döner, coin düşmez — UI buna göre davransın.
- **Gradle/bağımlılık:** firebase-functions (BoM).
- **Bağımlılık:** Coin ekonomisi alanı, Browse/Arşiv alanı (kilitli sınav UI)
- **Tuzaklar:** Region MUTLAKA 'europe-west1'; varsayılan us-central1 çağrısı işlevi bulamaz. App Check Functions'ta enforce ise (unlockExam enforceAppCheck belirtmemiş = varsayılan açık olabilir) token şart; recordLoginDiagnostic'ten farklı olarak unlockExam App Check'e tabi.
- **Kabul kriteri:** Yeterli bakiyeyle çağrı unlocks + spend ledger yazar ve yeni balance döner; yetersiz bakiyede FAILED_PRECONDITION; ikinci çağrı no-op (alreadyUnlocked).

#### `BE-09` — recordLoginDiagnostic'i Android'den çağır (App Check enforce KAPALI) — başarısız giriş raporu  _(P1 · efor: M)_
- **iOS davranışı:** enforceAppCheck=false; payload reason, step(send_code|verify), phoneNumber(+90), appCheckStatus(client probe: valid/error/unavailable), backendCode(39), appVersion, osVersion, deviceModel, occurredAt(ISO8601); 60s signature(phone|reason|stage) throttle; client App Check probe appCheck.token(forcingRefresh:false).
- **iOS karşılığı/kaynak:** FirebaseLoginDiagnosticsService + LoginDiagnostics (throttle).
- **Android mevcut:** Giriş hata raporlama yok.
- **Android teknoloji:** FirebaseFunctions.getInstance("europe-west1").getHttpsCallable("recordLoginDiagnostic"), FirebaseAppCheck.getInstance().getAppCheckToken(false), Build.MODEL, Build.VERSION.RELEASE, BuildConfig.VERSION_NAME.
- **Adımlar:**
  - Giriş başarısızlığında (onVerificationFailed veya kod doğrulama hatası) LoginFailureInfo benzeri bir model kur: reason (stabil analytics sebebi), backendCode (örn. 39), stage (send_code|verify), phoneNumber (+90 veya null).
  - Client App Check probe: FirebaseAppCheck.getInstance().getAppCheckToken(false) başarılı → 'valid', hata → 'error', provider yoksa 'unavailable'.
  - Payload: reason, step, phoneNumber, appCheckStatus, appVersion='<versionName> (<versionCode>)', osVersion=Build.VERSION.RELEASE, deviceModel=Build.MODEL (veya Build.MANUFACTURER+MODEL), occurredAt=ISO8601 (OffsetDateTime.now()), backendCode (varsa).
  - 60 sn signature throttle uygula (signature = phone|reason|stage); aynı imza penceresi içinde tekrar gönderme — fire-and-forget, giriş akışını bloklamasın ve hatayı kullanıcıya yansıtmasın.
  - getHttpsCallable('recordLoginDiagnostic').call(payload) çağır; çağrı App Check enforce etmediğinden token mint edilemese bile çalışır.
- **Gradle/bağımlılık:** firebase-functions, firebase-appcheck (BoM).
- **Bağımlılık:** Auth/Giriş alanı, BE-08 (functions client)
- **Tuzaklar:** Fonksiyon numarayı ham saklamaz (salted SHA-256 + son 4 hane) — client ham +90 gönderir, sunucu hash'ler. Backend 'Error code: 39' Android'de FirebaseAuthException/error mesajından çıkarılmalı. recordLoginDiagnostic App Check'e tabi DEĞİL, unlockExam'den farklı davranır.
- **Kabul kriteri:** Başarısız giriş Console'daki loginDiagnostics koleksiyonuna düşer; appCheckClient/appCheckServer, backendCode, stage alanları dolu; aynı hata 60 sn içinde tek kayıt üretir.

#### `BE-10` — Firestore index'lerini DOĞRULA (yeni index deploy ETME) — mevcut 3 index yeterli  _(P2 · efor: S)_
- **iOS davranışı:** catalogNodes sorgusu parentId==X && status=='published' (orderBy YOK); exams sorgusu courseId==X && status=='published' (orderBy YOK) — ikisi de yalnız eşitlik → otomatik tekil index + index merge ile sunulur, composite index gerekmez. submissions(ownerId, createdAt DESC) ve submissions(status, createdAt ASC) + exams(courseId, sortKey DESC) zaten firestore.indexes.json'da.
- **iOS karşılığı/kaynak:** FirestoreArchiveRepository (whereField eşitlikleri), firestore.indexes.json.
- **Android mevcut:** Firestore kullanılmadığından index ihtiyacı yok.
- **Android teknoloji:** Firestore composite index (gerekirse Logcat'teki oluşturma linki).
- **Adımlar:**
  - Android'in catalogNodes/exams sorgularını iOS ile BİREBİR aynı kur (whereEqualTo(parentId)+whereEqualTo(status) ve whereEqualTo(courseId)+whereEqualTo(status), orderBy yok).
  - submissions owner listesini whereEqualTo('ownerId', uid).orderBy('createdAt', DESCENDING) ile sorgula — mevcut (ownerId ASC, createdAt DESC) index'i karşılar.
  - firestore.indexes.json'u DEĞİŞTİRME/yeniden deploy ETME; index'ler platformdan bağımsız ve zaten yayında.
  - İlk çalıştırmada Firestore 'FAILED_PRECONDITION: requires an index' hatası verirse, Logcat'teki hazır linkle index oluştur — ama eşitlik-yalnız sorgularda bu beklenmiyor.
- **Gradle/bağımlılık:** Yok.
- **Bağımlılık:** BE-05, Browse/Arşiv alanı
- **Tuzaklar:** Görevde istenen 'catalogNodes parentId+status' ve 'exams courseId' composite index'leri aslında GEREKMEZ (iOS de tanımlamamış); gereksiz index deploy'u ortak backend'i kirletir. Yanlışlıkla orderBy eklenirse composite index gerekir.
- **Kabul kriteri:** Android arşiv sorguları mevcut index setiyle hatasız döner; firestore.indexes.json'da Android için ek değişiklik yok.

#### `BE-11` — Parse SDK + Parse-FCM kaldır, native FCM'e geç (onNewToken, topic 'all', POST_NOTIFICATIONS)  _(P1 · efor: M)_
- **iOS davranışı:** Her cihaz FCM topic 'all'e subscribe (broadcast duyuru); swizzling kapalı; Parse yok.
- **iOS karşılığı/kaynak:** AppDelegate (Messaging subscribe toTopic 'all').
- **Android mevcut:** parse-android + parse-fcm-android 1.17.3; MyFirebaseInstanceIDService (kullanımdan kaldırılmış FirebaseInstanceIdService) boş registerToken; MyFirebaseMessagingService data['message'] gösterir.
- **Android teknoloji:** FirebaseMessagingService.onNewToken, FirebaseMessaging.getInstance().subscribeToTopic("all"), POST_NOTIFICATIONS izni, NotificationChannel.
- **Adımlar:**
  - app/build.gradle: parse-android ve parse-fcm-android bağımlılıklarını ve ext.parseVersion'ı kaldır.
  - MyFirebaseInstanceIDService'i sil; token yenilemeyi MyFirebaseMessagingService.onNewToken(token) içine taşı.
  - Uygulama açılışında/girişte FirebaseMessaging.getInstance().subscribeToTopic("all") çağır (idempotent).
  - Android 13+ için POST_NOTIFICATIONS iznini manifest'e ekle ve runtime'da iste; Android 8+ için NotificationChannel oluştur.
  - MyFirebaseMessagingService bildirim gösterimini duyuru payload'ına göre düzelt (başlık/metin sabit 'FCM Test' yerine gerçek içerik).
- **Gradle/bağımlılık:** parse-android + parse-fcm-android KALDIR; firebase-messaging (BoM).
- **Bağımlılık:** Bildirimler alanı
- **Tuzaklar:** FirebaseInstanceIdService ve INSTANCE_ID_EVENT intent-filter kaldırılmalı (artık yok). jcenter kapalı olduğundan Parse zaten çözülemez — kaldırım BE-00 ile uyumlu. Duyuru segmentleri (universityId/facultyId topic'leri) ileride profil slug'larından türetilir.
- **Kabul kriteri:** Parse bağımlılıkları yok; onNewToken çalışıyor; cihaz 'all' topic'ine abone; Console broadcast'i Android'de bildirim olarak görünüyor; Android 13'te izin isteniyor.

#### `BE-12` — AdMob'u tamamen kaldır (gradle + manifest + google-services ads) — iOS ile parite (reklam yok)  _(P1 · efor: S)_
- **iOS davranışı:** iOS'ta reklam YOK (IS_ADS_ENABLED=false); monetizasyon coin ekonomisi.
- **iOS karşılığı/kaynak:** Yok.
- **Android mevcut:** play-services-ads 17.2.0; manifest'te com.google.android.gms.ads.APPLICATION_ID meta-data; google-services.json ads_service status 2; banner/interstitial/rewarded kullanımı.
- **Android teknoloji:** AndroidManifest, build.gradle.
- **Adımlar:**
  - app/build.gradle: implementation 'com.google.android.gms:play-services-ads' satırını kaldır.
  - AndroidManifest'ten com.google.android.gms.ads.APPLICATION_ID meta-data'sını ve reklamla ilgili activity/izinleri sil.
  - Banner/Interstitial/Rewarded kullanan tüm kod yollarını kaldır (AdView, InterstitialAd, RewardedAd).
  - google-services.json'daki ads_service bilgisi otomatik gelse de reklam SDK'sı olmadan etkisiz; manuel temizlik gerekmez.
- **Gradle/bağımlılık:** play-services-ads KALDIR.
- **Bağımlılık:** Tüm reklam gösteren feature alanları
- **Tuzaklar:** Rewarded ad ile coin verme mantığı varsa coin ekonomisine (upload reward / unlock) taşınmalı; reklam kaldırımı gelir akışını coin'e çevirir.
- **Kabul kriteri:** APK'da reklam SDK'sı yok; manifest'te ads APPLICATION_ID yok; hiçbir ekranda reklam görünmüyor.

#### `BE-13` — RTDB profil yazımı (users/$uid/profile) — katı kurallara uyum (ServerValue.TIMESTAMP, phoneNumber==auth, slug *Id)  _(P1 · efor: M)_
- **iOS davranışı:** users/$uid/profile owner(+90) read/write; .validate updatedAt===now, phoneNumber===auth.token.phone_number && +90 regex; *Id slug [a-z0-9-]; name/surname<=60, university/faculty/department<=120; gender female|male; $other=false.
- **iOS karşılığı/kaynak:** ProfileStore / FirebaseProfileService; database.rules.json users/$uid/profile.
- **Android mevcut:** Profil yazımı yok; AddLessonActivity Universitiess'e doğrudan yazıyor (artık admin-only, +90 user reddedilir).
- **Android teknoloji:** FirebaseDatabase getReference('users/'+uid+'/profile'), ServerValue.TIMESTAMP, Slug üretimi.
- **Adımlar:**
  - Profil kaydını tek setValue(map) ile yaz: name, surname, university, universityId, universityIsCustom, faculty, facultyId, facultyIsCustom, department, departmentId, departmentIsCustom, gender, phoneNumber, updatedAt.
  - updatedAt = ServerValue.TIMESTAMP (kural newData.val()===now ister; client saati reddedilir).
  - phoneNumber = FirebaseAuth.getCurrentUser().getPhoneNumber() (auth.token.phone_number ile birebir, +90 regex).
  - *Id alanları: iOS Slug mantığıyla aynı kanonik slug [a-z0-9-] üret (FCM topic/segment uyumu); *IsCustom boolean.
  - Kural ihlalini önlemek için tüm 13 anahtarı eksiksiz yaz ($other validate false).
- **Gradle/bağımlılık:** firebase-database (mevcut).
- **Bağımlılık:** Profil alanı, Onboarding/Profil tamamlama gate alanı
- **Tuzaklar:** updatedAt için System.currentTimeMillis() kullanmak kuralı KIRAR — ServerValue.TIMESTAMP şart. Eksik tek alan tüm yazıyı reddeder. Slug üretimi iOS ile ayrışırsa segment topic'leri tutmaz.
- **Kabul kriteri:** Android profil yazımı kurallardan geçer; yanlış phoneNumber/updatedAt veya fazladan alan reddedilir; *Id'ler slug formatında.

#### `BE-14` — Kullanıcı sınav gönderimi: Firestore submissions + Storage pending upload sözleşmesine uyum (eski admin yazımını kaldır)  _(P1 · efor: L)_
- **iOS davranışı:** submissions create: TAM 10 anahtar {ownerId==uid, university, faculty, department, lesson, exam, kind(JPG|PDF), status:'pending', createdAt(serverTimestamp), files[1..50 {storagePath,order}]}; metin 1..120. Storage upload yalnız pending/$uid/$submissionId/$file, image/jpeg|application/pdf, <=15 MiB, overwrite yok.
- **iOS karşılığı/kaynak:** ExamSubmission servisleri; firestore.rules submissions; storage.rules pending.
- **Android mevcut:** AddLessonActivity doğrudan imagess/ Storage + Universitiess RTDB'ye yazıyor — bu yollar artık admin-only, +90 user REDDEDİLİR.
- **Android teknoloji:** FirebaseFirestore(named 'vifi').collection('submissions'), FieldValue.serverTimestamp(), FirebaseStorage.getReference('pending/'+uid+'/'+submissionId+'/'+file), putFile metadata contentType.
- **Adımlar:**
  - Eski admin yazım yollarını kaldır: AddLessonActivity'nin imagess/ + Universitiess doğrudan yazımları (rules bunları +90 user'a kapatıyor).
  - Dosyaları önce Storage'a yükle: pending/<uid>/<submissionId>/<dosya>, setContentType('image/jpeg') veya 'application/pdf', <=15 MiB, overwrite deneme (create-only).
  - submissions dokümanını named 'vifi' Firestore'a yaz: ownerId=uid, görünür-ad metinleri (university/faculty/department/lesson/exam, courseId YOK; yıl exam'e katlanır '<yıl> <ad>'), kind JPG|PDF, status='pending', createdAt=FieldValue.serverTimestamp(), files=[{storagePath,order}] (1..50).
  - Metin alanlarını 1..120 ile doğrula (kural submissionText).
  - Owner akışları: kendi pending gönderisini okuma (whereEqualTo('ownerId',uid)) ve pending iken silme (delete) — moderasyon admin tarafında.
- **Gradle/bağımlılık:** firebase-firestore, firebase-storage (BoM).
- **Bağımlılık:** AddExam/Sınav Yükleme alanı, BE-05
- **Tuzaklar:** createdAt client değil FieldValue.serverTimestamp() olmalı (idempotent ledger submissionId=entryId ile çift-krediyi önler). storagePath prefix tam 'pending/<uid>/<submissionId>/<dosya>' olmalı; aksi halde hem Storage hem (RTDB eşdeğeri) kural reddeder. Storage listeleme yok; overwrite yasak.
- **Kabul kriteri:** +90 kullanıcı pending'e dosya yükleyip submissions dokümanı oluşturabilir; eksik/fazla anahtar veya yanlış kind reddedilir; owner kendi gönderisini görür ve pending iken siler.

#### `BE-15` — Arşivi RTDB 'Universitiess'ten named 'vifi' Firestore catalogNodes/exams'e taşı (client okuma yolu)  _(P1 · efor: L)_
- **iOS davranışı:** catalogNodes/<id> {kind: university|faculty|department|course, parentId zinciri, status:'published'} + exams/<id> {courseId, kind:JPG|PDF, status:'published', files:[{storagePath,order}]}; RTDB Universitiess rollback için duruyor, cutover sonrası silinecek.
- **iOS karşılığı/kaynak:** FirestoreArchiveRepository; FirebaseArchiveRepository(RTDB rollback).
- **Android mevcut:** MainActivity/UniversityFragment/DepartmentsFragment/ExamDetail* RTDB getReference('Universitiess')... zincirini okuyor.
- **Android teknoloji:** FirebaseFirestore(named 'vifi') collection('catalogNodes').whereEqualTo('parentId',..).whereEqualTo('status','published'); exams.whereEqualTo('courseId',..).whereEqualTo('status','published'); Storage imagess/<uuid>.jpg, pdfs/<uuid>.pdf okuma.
- **Adımlar:**
  - Arşiv gezinmesini RTDB zincirinden Firestore catalogNodes parentId sorgularına çevir (kök için rootParentId).
  - Sınav listesini exams courseId+status sorgusuyla al; dosyaları files[].storagePath üzerinden Storage'tan tek tek getir (listeleme yok).
  - RTDB 'Universitiess' okuma yollarını kaldır (cutover sonrası node silinecek).
  - Resim yükleme için Picasso yerine (BE-00 ile) Coil/Glide gibi AndroidX uyumlu bir kütüphaneye geç; PDF için android-pdf-viewer (AndroidPdfViewer) koru/güncelle.
- **Gradle/bağımlılık:** firebase-firestore, firebase-storage (BoM); Coil/Glide; AndroidPdfViewer.
- **Bağımlılık:** Browse/Arşiv alanı, BE-05
- **Tuzaklar:** Storage'tan okuma için +90 (App Check'li) oturum şart; 'imagess/<fileName>' get kuralı canReadArchive. Universitiess silinmeden önce Android'in Firestore'a tam geçtiğinden emin ol, yoksa arşiv boşalır. Storage listeleme yasak — yol files[].storagePath'ten gelmeli.
- **Kabul kriteri:** Android arşiv/sınavları named 'vifi' Firestore'dan okur; RTDB Universitiess'e bağımlılık kalmaz; dosyalar storagePath ile açılır.


---

## Temel & Modernizasyon (Mimari İskelet)  ·  `foundation`

**Özet:** iOS tarafı modern bir SwiftUI/MVVM(@Observable) uygulaması: DI için AppEnvironment (live/mock), Router+NavigationStack ile gezinme, her servis bir protokol arkasında (ServiceProtocols/AuthServicing), Loadable<Value> ekran durumu, isimli 'vifi' Firestore DB (eur3) + RTDB provider singletonları, europe-west1 Cloud Functions, App Attest/DeviceCheck/Debug App Check, -ViFiMockData ile Firebase'siz örnek veri, OSLog tabanlı loglama, Material-benzeri DesignSystem (IconBadge, durum görünümleri, CoinBalanceBadge, siyah-üstü-turuncu kontrast kuralı). Android ise 2018 seviyesinde: saf Java, Activity/Fragment, com.android.support (AndroidX öncesi), AGP 3.4.2 + Gradle 5.1.1 + jcenter(ölü), compileSdk/targetSdk 28, Firebase 16.x sabit sürümler, Parse SDK, AdMob (banner/interstitial/rewarded), Picasso, barteksc PDF, Play Store HTML kazıyan GooglePlayAppVersion AsyncTask, e-posta/şifre girişi, RTDB 'Universitiess' ağacı. Bu alan iOS mimari iskeletinin Android karşılığını kurar: AGP 8 + Gradle 8.9 + JDK 17 + Version Catalog, AndroidX, Kotlin, Firebase BoM, Jetpack Compose + Material3 + Navigation Compose (Router karşılığı), Hilt (AppEnvironment karşılığı), repository arayüz deseni, UiState/ViewModel/Flow (Loadable/@Observable karşılığı), isimli 'vifi' DB + Functions region + Play Integrity App Check provider wiring, mock/preview altyapısı ve launch-argument karşılığı, R8/ProGuard, paket yapısı; Parse ve AdMob kaldırma; eski Activity/Fragment/Adapter iskeletinin emekliye ayrılması. Diğer özellik alanları (Auth, Coin ekonomisi, Bildirimler, Gönderim/AddExam, Moderasyon, Profil, Arşiv okuma, Zorunlu güncelleme, Onboarding, Analytics, Ekran koruması) bu iskelet üzerine inşa edilir.

**Android mevcut durum:** Saf Java, Activity/Fragment mimarisi (HomeActivity, MainActivity, LoginActivity, UniversityFragment/FacultiesFragment/DepartmentsFragment/LessonFragment, CustomAdapter/ListViewAdapter/CustomAdapter). com.android.support 27/28 (AndroidX ÖNCESİ). AGP 3.4.2, Gradle wrapper 5.1.1, jcenter() (kapatıldı/ölü), buildscript bloğu, compileSdkVersion 28 / targetSdkVersion 28 / minSdkVersion 19, multiDexEnabled true + com.android.support:multidex:1.0.3, versionCode 16 / versionName 2.5. Firebase sabit eski sürümler: firebase-database 16.1.0, firebase-storage 16.1.0, firebase-auth 16.2.0, firebase-core 16.0.8, firebase-messaging 17.4.0 (BoM yok). Parse SDK (parse-android + parse-fcm-android 1.17.3). AdMob (play-services-ads 17.2.0, MobileAds.initialize + AdView, manifest'te APPLICATION_ID meta-data). Picasso 2.71828 (görsel). barteksc android-pdf-viewer 2.3.0 (jcenter, terk edilmiş). commons-io 1.3.…

**Dokunulan backend sözleşmesi:** Bu alan ortak backend sözleşmesine YENİ alan eklemez; yalnızca Android istemcisini sözleşmeye uyacak şekilde bağlar. Kritik wiring noktaları: (1) Firestore İSİMLİ veritabanı 'vifi' — Android'de FirebaseFirestore.getInstance(FirebaseApp.getInstance(), "vifi") ile açılmalı; (default) DB'ye bağlanmak yanlış/boş veri demektir. (2) Cloud Functions bölgesi europe-west1 — FirebaseFunctions.getInstance(FirebaseApp.getInstance(), "europe-west1"). (3) App Check — Storage/RTDB/Auth için enforce AÇIK; Android Play Integrity provider ile token üretmezse 403 alır; Firestore şu an monitoring. (4) RTDB config/appUpdate için 'android' kardeşi backend'de HENÜZ YOK — bu, Zorunlu Güncelleme alanının işi; bu alan sadece RTDB okuma iskeletini (ViFiDatabase singleton, persistence) hazırlar. Firestore kuralları platformdan bağımsız (sadece +90 phone auth + admin claim bakar), bu yüzden mimari değişiklik kural…

### İş Kalemleri

#### `FM-01` — Gradle/AGP/JDK zincirini modernize et (AGP 8.7 + Gradle 8.9 + JDK 17 + namespace)  _(P0 · efor: M)_
- **iOS davranışı:** Modern toolchain, deterministik build (project.yml tek kaynak). Android'de Gradle/AGP güncel olmadan Compose/Firebase BoM/Play Integrity hiçbiri çalışmaz.
- **iOS karşılığı/kaynak:** iOS'ta proje XcodeGen/project.yml ile üretilir (SWIFT_VERSION 6.0, deploymentTarget 17.0). Android'de muadil Gradle + AGP yükseltmesidir.
- **Android mevcut:** AGP 3.4.2, Gradle wrapper 5.1.1, buildscript bloğu, JDK 8 varsayımı, namespace yok (AndroidManifest package=com.salticusteam.vifi), BuildConfig AGP 3'te otomatik açık.
- **Android teknoloji:** Android Gradle Plugin 8.7.2, Gradle 8.9, JDK 17, plugins DSL, android.namespace, buildFeatures.buildConfig
- **Adımlar:**
  - gradle/wrapper/gradle-wrapper.properties: distributionUrl'ı gradle-8.9-bin.zip yap.
  - Üst düzey build.gradle'daki buildscript{} + jcenter() + allprojects{} bloklarını SİL; plugins DSL'e geç: plugins { id 'com.android.application' version '8.7.2' apply false; id 'org.jetbrains.kotlin.android' version '2.0.21' apply false; id 'org.jetbrains.kotlin.plugin.compose' version '2.0.21' apply false; id 'com.google.gms.google-services' version '4.4.2' apply false; id 'com.google.dagger.hilt.android' version '2.52' apply false; id 'com.google.devtools.ksp' version '2.0.21-1.0.25' apply false }
  - Üst düzeydeki 'task clean(type: Delete)' bloğunu kaldır (AGP 8 ile gereksiz/çakışır).
  - app/build.gradle'a android { namespace 'com.salticusteam.vifi'; compileSdk 35 } ekle; AndroidManifest.xml'den package="com.salticusteam.vifi" özniteliğini kaldır.
  - compileOptions { sourceCompatibility JavaVersion.VERSION_17; targetCompatibility JavaVersion.VERSION_17 } ve Kotlin için kotlin { jvmToolchain(17) } (veya kotlinOptions.jvmTarget '17') ekle.
  - buildFeatures { buildConfig true } ekle (BuildConfig.VERSION_NAME/DEBUG kullanıldığı için).
  - Android Studio'nun gömülü JDK 17'sini (JBR) kullan; gradle.properties'teki org.gradle.java.home JDK 8'e sabitliyse kaldır.
  - gradle.properties: org.gradle.jvmargs=-Xmx4096m, android.useAndroidX=true, android.nonTransitiveRClass=true ekle.
- **Gradle/bağımlılık:** gradle-wrapper 8.9; AGP 8.7.2; Kotlin 2.0.21; KSP 2.0.21-1.0.25; google-services 4.4.2; Hilt 2.52 (plugins bloğunda apply false).
- **Tuzaklar:** AGP 8 JDK 17 ZORUNLU. namespace eklenmezse sync kırılır. BuildConfig varsayılan kapalı. jcenter satırları kalırsa bazı çözümlemeler asılı kalır/kırılır.
- **Kabul kriteri:** ./gradlew :app:assembleDebug JDK 17 ile başarılı; BuildConfig erişilebilir; projede jcenter referansı kalmadı; namespace tanımlı.

#### `FM-02` — Depoları jcenter→google+mavenCentral yap ve Version Catalog (libs.versions.toml) kur  _(P0 · efor: S)_
- **iOS davranışı:** Tek kaynaktan sürüm yönetimi; tekrar yok.
- **iOS karşılığı/kaynak:** iOS SwiftPM + Firebase 12 BoM-benzeri 'from: 12.0.0' tek kaynak sürüm. Android muadili Version Catalog + settings dependencyResolutionManagement.
- **Android mevcut:** jcenter() (ölü) + google() + maven.google.com çoğaltması; tüm sürümler app/build.gradle'da sabit string.
- **Android teknoloji:** settings.gradle pluginManagement + dependencyResolutionManagement, gradle/libs.versions.toml, repositoriesMode.FAIL_ON_PROJECT_REPOS
- **Adımlar:**
  - settings.gradle'ı yeniden yaz: pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } } ve dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral(); maven { url 'https://jitpack.io' } } }.
  - jcenter()'ı tüm dosyalardan tamamen kaldır.
  - gradle/libs.versions.toml oluştur; [versions] [libraries] [plugins] [bundles] tablolarıyla tüm bağımlılık ve eklenti sürümlerini buraya taşı.
  - app/build.gradle ve üst build.gradle'daki sabit sürümleri libs.* referanslarıyla değiştir.
  - JitPack'i yalnızca gerçekten gereken artefakt (ör. android-pdf-viewer fork) için ekle.
- **Gradle/bağımlılık:** Version Catalog (libs.versions.toml); JitPack maven repo (sadece gerekli artefaktlar için).
- **Bağımlılık:** FM-01
- **Tuzaklar:** FAIL_ON_PROJECT_REPOS açıkken modül içi repositories{} bloğu hata verir — hepsi settings'e taşınmalı. JitPack bağımlılıkları yavaş/çözümlenemeyebilir, sürüm sabitlenmeli.
- **Kabul kriteri:** Projede jcenter yok; ./gradlew :app:dependencies çözülüyor; tüm sürümler libs.versions.toml'dan geliyor.

#### `FM-03` — SDK seviyelerini yükselt (compileSdk/targetSdk 35, minSdk 24) ve multidex'i sadeleştir  _(P0 · efor: S)_
- **iOS davranışı:** Modern API tabanı; eski platform tuzakları yok.
- **iOS karşılığı/kaynak:** iOS deploymentTarget 17.0 (modern taban). Android muadili güncel compile/target + makul minSdk tabanı.
- **Android mevcut:** compileSdkVersion 28, targetSdkVersion 28, minSdkVersion 19, multiDexEnabled true + com.android.support:multidex:1.0.3, vectorDrawables.useSupportLibrary.
- **Android teknoloji:** compileSdk 35, targetSdk 35, minSdk 24, androidx.multidex (minSdk>=21'de otomatik), vectorDrawables.useSupportLibrary false
- **Adımlar:**
  - defaultConfig: compileSdk 35, targetSdk 35, minSdk 24 (ürün kararı — openQuestions).
  - multiDexEnabled satırını kaldır (minSdk>=21'de D8 otomatik multidex yapar) ve com.android.support:multidex bağımlılığını sil.
  - vectorDrawables.useSupportLibrary = true'yu kaldır (AndroidX/modern platformda gereksiz).
  - versionCode'u Play'deki son yayından büyük bir değere ayarla (ör. 17+); versionName'i 3.x yap (iOS ile uyumlu numara stratejisi için Zorunlu Güncelleme alanıyla koordine et).
  - testInstrumentationRunner'ı androidx.test.runner.AndroidJUnitRunner (veya Hilt testleri için custom runner) yap.
- **Gradle/bağımlılık:** com.android.support:multidex kaldırıldı; androidx.test runner.
- **Bağımlılık:** FM-01
- **Tuzaklar:** targetSdk 35 Play için ZORUNLU (aksi halde güncelleme reddedilir). targetSdk 35 edge-to-edge'i zorlar (FM-22). minSdk 19'dan 24'e atlama cihaz segmentini etkiler — ürün onayı gerek. READ_EXTERNAL_STORAGE 33+'ta Photo Picker'a devredilmeli (AddExam alanı).
- **Kabul kriteri:** assembleDebug 35'e karşı derlenir; targetSdk 35; minSdk 24; multidex uyarısı yok.

#### `FM-04` — AndroidX'e geç (com.android.support → androidx) ve Jetifier köprüsünü kaldır  _(P0 · efor: M)_
- **iOS davranışı:** Yok (platform modernizasyonu).
- **iOS karşılığı/kaynak:** Android-only kavram; iOS karşılığı yok. Compose rewrite ile çoğu View bağımlılığı tamamen düşer.
- **Android mevcut:** com.android.support:appcompat-v7 27.1.0, design 28.0.0, recyclerview-v7, constraint-layout 1.1.3, support-vector-drawable; tüm import'lar android.support.*.
- **Android teknoloji:** androidx.appcompat:appcompat 1.7.0, com.google.android.material:material 1.12.0, androidx.constraintlayout, androidx.recyclerview, AndroidX Migration
- **Adımlar:**
  - gradle.properties: android.useAndroidX=true; geçiş süresince android.enableJetifier=true (3. parti AndroidX öncesi artefakt kalmayınca kapat).
  - com.android.support:* bağımlılıklarını androidx muadilleriyle değiştir: appcompat→androidx.appcompat:appcompat:1.7.0, design→com.google.android.material:material:1.12.0, constraint-layout→androidx.constraintlayout:constraintlayout:2.2.0, recyclerview→androidx.recyclerview:recyclerview:1.3.2.
  - Kalan Java/XML'de android.support.* import'larını androidx.* ile değiştir (Android Studio 'Migrate to AndroidX' yardımcısı + elle).
  - Not: FM-09 (Compose) tam rewrite yapıldığında appcompat/constraintlayout/recyclerview büyük ölçüde kaldırılır; bu adım yalnızca geçiş köprüsüdür veya kalan View ekranları içindir.
  - Geçiş bitince android.enableJetifier'ı kaldır (build hızlanır).
- **Gradle/bağımlılık:** androidx.appcompat 1.7.0, material 1.12.0, constraintlayout 2.2.0, recyclerview 1.3.2; Jetifier (geçici).
- **Bağımlılık:** FM-01, FM-02
- **Tuzaklar:** Jetifier AGP 8'de deprecate yolunda; mümkünse hiç kullanmadan doğrudan androidx bağımlılıklarına geç. barteksc PDF viewer AndroidX öncesi — FM fork (FM-10/Exam alanı) ile çöz.
- **Kabul kriteri:** Proje android.support içermez; useAndroidX=true; derleme ve temel akış çalışır.

#### `FM-05` — Kotlin'e geç ve Coroutines/Flow altyapısını kur (async/await + AsyncStream karşılığı)  _(P0 · efor: L)_
- **iOS davranışı:** .task ekran göründüğünde yükler; AsyncStream canlı değişiklikleri yayar; MainActor UI güvenliği. Android muadili: ViewModel + viewModelScope + Flow/StateFlow; callbackFlow ile RTDB/Firestore dinleyicileri Flow'a sarılır; Dispatchers.IO ağır iş.
- **iOS karşılığı/kaynak:** iOS: Swift 6 strict concurrency, async/await, AsyncStream (userChanges/statusChanges/archiveChanges), MainActor izolasyonu, @concurrent nonisolated ağır iş.
- **Android mevcut:** Saf Java; AsyncTask (GooglePlayAppVersion), callback tabanlı Firebase dinleyicileri, eşzamanlılık soyutlaması yok.
- **Android teknoloji:** Kotlin 2.0.21, kotlinx-coroutines-android 1.9.0, kotlinx-coroutines-play-services (Task.await), Flow/StateFlow/callbackFlow, Dispatchers.IO
- **Adımlar:**
  - org.jetbrains.kotlin.android eklentisini uygula; src/main/java'yı Kotlin ile doldur (kademeli Java→Kotlin dönüşümü, Android Studio 'Convert to Kotlin').
  - kotlinx-coroutines-android ve kotlinx-coroutines-play-services bağımlılıklarını ekle (Firebase Task.await() için).
  - AsyncStream karşılığı olarak RTDB ValueEventListener / Firestore addSnapshotListener / FirebaseAuth AuthStateListener'ları callbackFlow{} ile Flow'a sar (awaitClose ile dinleyiciyi kaldır — iOS'un stream iptali karşılığı).
  - async tek-atış çağrılar için Task<T>.await() kullan (ör. functions.getHttpsCallable(...).call(data).await()).
  - Ağır/IO işleri withContext(Dispatchers.IO) içine al; UI state güncellemeleri ana thread'te (StateFlow Compose tarafından toplanır).
- **Gradle/bağımlılık:** org.jetbrains.kotlin.android 2.0.21; kotlinx-coroutines-android 1.9.0; kotlinx-coroutines-play-services 1.9.0.
- **Bağımlılık:** FM-01, FM-02
- **Tuzaklar:** callbackFlow'da awaitClose unutulursa dinleyici sızar (iOS stream iptalinin karşılığı şarttır). RTDB callback'leri arka plan thread'inde gelir — Flow toplanırken Dispatcher'a dikkat. Firebase-ktx .await() uzantıları play-services-tasks sürümüyle uyumlu olmalı.
- **Kabul kriteri:** Kotlin derlenir; bir RTDB/Firestore dinleyici callbackFlow ile Flow döndürür ve iptal edilince awaitClose dinleyiciyi kaldırır; bir Function çağrısı .await() ile suspend çalışır.

#### `FM-06` — Firebase BoM'a geç ve gerekli SDK setini bağla (Parse/core olmadan)  _(P0 · efor: S)_
- **iOS davranışı:** Tüm Firebase alanları tek sürüm hattından; sürüm çakışması yok.
- **iOS karşılığı/kaynak:** iOS: Firebase 12 SwiftPM; FirebaseFirestore, FirebaseDatabase, FirebaseAuth, FirebaseAppCheck, FirebaseAnalyticsCore, FirebaseStorage, FirebaseFunctions, FirebaseMessaging ürünleri.
- **Android mevcut:** firebase-database 16.1.0, firebase-storage 16.1.0, firebase-auth 16.2.0, firebase-core 16.0.8, firebase-messaging 17.4.0 — hepsi sabit eski sürüm, BoM yok, Firestore/Functions/AppCheck/Analytics yok.
- **Android teknoloji:** com.google.firebase:firebase-bom 33.5.1, firebase-auth, firebase-firestore, firebase-database, firebase-storage, firebase-functions, firebase-messaging, firebase-analytics, firebase-appcheck-playintegrity, firebase-appcheck-debug
- **Adımlar:**
  - implementation platform('com.google.firebase:firebase-bom:33.5.1') ekle; tüm Firebase bağımlılıklarından sürüm numarasını kaldır.
  - Bağımlılıklar: firebase-auth, firebase-firestore, firebase-database, firebase-storage, firebase-functions, firebase-messaging, firebase-analytics, firebase-appcheck-playintegrity, firebase-appcheck-debug (debug için).
  - firebase-core'u KALDIR (deprecate; analytics firebase-analytics'e taşındı).
  - Eski 16.x/17.x sabit sürüm satırlarını sil.
  - google-services eklentisini app modülünde plugins { id 'com.google.gms.google-services' } ile uygula; app/google-services.json'ın com.salticusteam.vifi'yi içerdiğini doğrula.
  - Kotlin kullanıldığı için KTX uzantıları BoM 33+ ile ana artefaktlara taşındı (ayrı -ktx gerekmez), yine de firestore/database/auth için Kotlin uzantılarını kullan.
- **Gradle/bağımlılık:** firebase-bom 33.5.1; yukarıdaki Firebase artefaktları (sürümsüz); google-services 4.4.2 eklentisi.
- **Bağımlılık:** FM-01, FM-02, FM-05
- **Tuzaklar:** firebase-firestore mutlaka isimli 'vifi' DB ile açılmalı (FM-15). firebase-core kalırsa derleme uyarısı/çakışma. BoM 33+ KTX'i ana artefakta katladı — eski -ktx satırları sürüm çakışması yapar.
- **Kabul kriteri:** BoM ile tek sürüm hattı; FirebaseApp başlar; firebase-core yok; gerekli 10 artefakt çözülür.

#### `FM-07` — Parse SDK'yı tamamen kaldır  _(P1 · efor: S)_
- **iOS davranışı:** Tek push yolu (FCM), fazladan SDK yok.
- **iOS karşılığı/kaynak:** iOS'ta Parse yok; push native FCM/APNs ile. Android zaten native FCM'e sahip, Parse gereksiz katman.
- **Android mevcut:** parse-android 1.17.3 + parse-fcm-android 1.17.3; muhtemel Parse başlatma + push kaydı; MyFirebaseInstanceIDService eski InstanceId API'siyle.
- **Android teknoloji:** Parse kaldırma, FirebaseMessaging.getInstance().token (eski InstanceId yerine)
- **Adımlar:**
  - com.parse:parse-android ve com.parse:parse-fcm-android bağımlılıklarını sil.
  - Parse.initialize ve Parse push kayıt kodunu kaldır (Application sınıfı ve servislerden).
  - MyFirebaseInstanceIDService'i tamamen sil (FirebaseInstanceId deprecate — token artık FirebaseMessaging.getInstance().getToken()); AndroidManifest'ten INSTANCE_ID_EVENT service'ini kaldır.
  - Parse'a ait manifest meta-data/izin/receiver varsa temizle.
  - Token depolama/akışı Bildirimler alanına devredilir (users/<uid>/devices/<token>); bu alan sadece Parse'ı söker.
- **Gradle/bağımlılık:** parse-android, parse-fcm-android KALDIRILDI.
- **Bağımlılık:** FM-06
- **Tuzaklar:** Parse ve FCM aynı anda push receiver'a yazıyorsa çıkarınca push akışı değişir; Bildirimler alanıyla koordine et. MyFirebaseInstanceIDService referansları başka sınıflarda kalmış olabilir.
- **Kabul kriteri:** Projede com.parse yok; MyFirebaseInstanceIDService ve INSTANCE_ID_EVENT service'i manifest'te yok; derleme temiz.

#### `FM-08` — AdMob'u tamamen kaldır (iOS'ta reklam yok — parite için)  _(P1 · efor: S)_
- **iOS davranışı:** Reklamsız deneyim; ekranlarda banner/interstitial/rewarded yok.
- **iOS karşılığı/kaynak:** iOS'ta HİÇ reklam yok; monetizasyon coin ekonomisiyle. Android'de reklam kaldırılarak parite sağlanır.
- **Android mevcut:** play-services-ads 17.2.0; HomeActivity'de MobileAds.initialize + AdView.loadAd; AndroidManifest'te com.google.android.gms.ads.APPLICATION_ID meta-data; layout'larda adView.
- **Android teknoloji:** play-services-ads kaldırma, manifest ads meta-data kaldırma
- **Adımlar:**
  - com.google.android.gms:play-services-ads bağımlılığını sil.
  - MobileAds.initialize, AdView, AdRequest, InterstitialAd, RewardedAd kullanan tüm kodu kaldır (HomeActivity vb.).
  - AndroidManifest.xml'den com.google.android.gms.ads.APPLICATION_ID meta-data'sını kaldır.
  - Layout XML'lerindeki <com.google.android.gms.ads.AdView> öğelerini ve ilgili ID referanslarını kaldır (Compose rewrite'ta zaten gider).
  - Reklam SDK'sına bağlı izin/receiver kalıntısı varsa temizle.
- **Gradle/bağımlılık:** play-services-ads KALDIRILDI.
- **Tuzaklar:** Rewarded ad coin verme akışıyla bağlıysa (son commit 'Added Rewarded Ad'), bu mantık coin ekonomisi alanına taşınır — reklam ödülü yerine sunucu-taraflı coin kredisi geçerli. Reklam ID meta-data'sı kalırsa Play policy/boş init uyarısı.
- **Kabul kriteri:** Projede play-services-ads yok; ads APPLICATION_ID meta-data yok; hiçbir ekranda banner/interstitial/rewarded yok.

#### `FM-09` — Jetpack Compose + Material3 UI altyapısını kur (SwiftUI karşılığı)  _(P0 · efor: L)_
- **iOS davranışı:** Bildirimsel UI, reaktif state, önizlemeler. Android'de en yakın karşılık Jetpack Compose.
- **iOS karşılığı/kaynak:** iOS UI tamamen SwiftUI; her ekran XView (DI alır) + XScreen (ViewModel sahiplenir) deseni; #Preview her ekranda.
- **Android mevcut:** XML layout + Activity/Fragment; Theme.AppCompat.Light.DarkActionBar; Compose yok.
- **Android teknoloji:** androidx.compose:compose-bom 2024.10.01, androidx.compose.material3, androidx.activity:activity-compose 1.9.3, org.jetbrains.kotlin.plugin.compose, @Preview
- **Adımlar:**
  - org.jetbrains.kotlin.plugin.compose eklentisini uygula (Kotlin 2.0 ile composeOptions yerine bu kullanılır).
  - buildFeatures { compose true } ekle.
  - implementation platform('androidx.compose:compose-bom:2024.10.01'); ui, ui-graphics, ui-tooling-preview, material3, material-icons-extended ekle; debugImplementation ui-tooling + ui-test-manifest.
  - androidx.activity:activity-compose:1.9.3 ekle.
  - Tek bir ComponentActivity (FM-22) kur ve setContent { ViFiTheme { AppRoot() } } ile Compose ağacını mount et.
  - Ekran deseni: her feature için <Feature>Route (Hilt viewModel alır) composable + <Feature>Screen (saf state/callback) composable + @Preview; iOS XView/XScreen ayrımının karşılığı.
  - Alternatif (openQuestions): Compose yerine Views + ViewBinding + Fragment + Navigation Component korunursa iOS paritesi ve bakım zorlaşır — Compose önerilir.
- **Gradle/bağımlılık:** compose-bom 2024.10.01; material3; activity-compose 1.9.3; material-icons-extended; compose kotlin eklentisi 2.0.21.
- **Bağımlılık:** FM-01, FM-02, FM-05
- **Tuzaklar:** Kotlin 2.0'da composeOptions{kotlinCompilerExtensionVersion} KULLANILMAZ — ayrı compose eklentisi şart. ui-tooling sadece debug'ta (release APK şişmesin). Material3 edge-to-edge varsayar (FM-22).
- **Kabul kriteri:** setContent ile boş bir Material3 ekran render olur; @Preview Android Studio'da görünür; release APK'da ui-tooling yok.

#### `FM-10` — Navigation Compose ile Router/NavigationStack karşılığını kur  _(P0 · efor: M)_
- **iOS davranışı:** Tek stack; breadcrumb'a atlama ataları korur; openExam derin link'te tüm hiyerarşiyi kurar, Geri arşivde yukarı yürür. signedOut olunca popToRoot.
- **iOS karşılığı/kaynak:** iOS Router @Observable: path:[Route]; push/popToRoot/popTo(ArchivePath breadcrumb)/openExam (tüm ataları stack'e koyar). NavigationStack(path:$router.path) + navigationDestination(for:Route.self). Route enum: .browse(ArchivePath), .exam(ArchivePath, ExamKind).
- **Android mevcut:** Intent + startActivity ile Activity-to-Activity geçiş (HomeActivity→CourseExamListActivity→ExamDetailActivity...); geri yığını Activity task'ına bağlı, breadcrumb yok.
- **Android teknoloji:** androidx.navigation:navigation-compose 2.8.3, NavHost, NavController, type-safe routes (@Serializable + Kotlin serialization), navController.popBackStack/navigate
- **Adımlar:**
  - androidx.navigation:navigation-compose:2.8.3 ekle; Kotlin Serialization eklentisi + kotlinx-serialization-json (type-safe routes için).
  - Route'ları @Serializable sealed interface/data class olarak tanımla: Home, Browse(path: ArchivePath), Exam(path: ArchivePath, kind: ExamKind) — iOS Route enum karşılığı (ArchivePath serileştirilebilir olmalı).
  - NavHost(navController, startDestination = Home) { composable<Home>{...}; composable<Browse>{...}; composable<Exam>{...} } kur.
  - Router sarmalayıcı sınıf/holder yaz: push=navigate, popToRoot=popBackStack(Home, inclusive=false), popTo(breadcrumb)=belirli route'a kadar pop, openExam=hiyerarşiyi sırayla navigate (ataları ekle). iOS Router.openExam'in 'ataları stack'e koy' davranışını birebir kopyala.
  - Oturum kapanınca (SessionStore state=signedOut) navController.popBackStack(Home, inclusive=false) ile köke dön (RootView.onChange karşılığı).
  - ArchivePath'i NavType olarak serileştir (custom NavType veya String encode/decode) — breadcrumb derinliği prefix(depth) mantığını koru.
- **Gradle/bağımlılık:** navigation-compose 2.8.3; org.jetbrains.kotlin.plugin.serialization 2.0.21; kotlinx-serialization-json 1.7.3.
- **Bağımlılık:** FM-09
- **Tuzaklar:** ArchivePath gibi composite argümanlar type-safe nav'da @Serializable olmalı veya custom NavType gerekir. openExam'in 'tüm ataları stack'e koy' davranışı atlanırsa Geri tuşu arşivde yukarı yürümez (iOS paritesi bozulur). Derin link (bildirimden exam açma) Bildirimler alanıyla koordine.
- **Kabul kriteri:** Home→Browse→Exam gezinme çalışır; breadcrumb'a atlama ataları korur; exam'den Geri arşivde yukarı yürür; signedOut köke döner.

#### `FM-11` — Hilt DI grafiğini kur (AppEnvironment live()/mock() karşılığı)  _(P0 · efor: L)_
- **iOS davranışı:** Tek grafik, protokol-tabanlı, test/mock ile değiştirilebilir. Init sırası hatası phone-auth'u bozar (iOS notu).
- **iOS karşılığı/kaynak:** iOS AppEnvironment @Observable: tüm servisler protokol tipinde (any ArchiveRepository, any AuthServicing...); live() Firebase'i, mock() örnek veriyi inşa eder; .environment(_:) ile tüm ağaca enjekte; AppDelegate.makeDefault() -ViFiMockData'ya göre live/mock seçer. Firebase init sırası: AppCheck factory ÖNCE, sonra configure, UIApplication sonrası.
- **Android mevcut:** DI yok; servisler Activity içinde doğrudan FirebaseDatabase.getInstance() vb. ile oluşturuluyor; test edilemez, mock yok.
- **Android teknoloji:** Hilt 2.52 (@HiltAndroidApp, @Module, @InstallIn, @Binds, @Provides, @HiltViewModel, @Inject), KSP, hilt-navigation-compose 1.2.0
- **Adımlar:**
  - Application sınıfı oluştur (ViFiApplication) @HiltAndroidApp ile; AndroidManifest application android:name=".ViFiApplication".
  - Hilt eklentisi (com.google.dagger.hilt.android) + KSP; implementation hilt-android, ksp hilt-compiler, implementation androidx.hilt:hilt-navigation-compose:1.2.0.
  - @InstallIn(SingletonComponent::class) modüllerde servis arayüzlerini (FM-12) somut Firebase impl'lerine @Binds/@Provides ile bağla: ArchiveRepository→FirestoreArchiveRepository, AuthService→FirebaseAuthService, ProfileService→FirebaseProfileService vb. (AppEnvironment.live() içeriğinin birebir karşılığı).
  - Firebase başlatma sırasını ViFiApplication.onCreate içinde kur: ÖNCE FirebaseAppCheck.getInstance().installAppCheckProviderFactory(...) (FM-17), SONRA FirebaseApp zaten google-services ile otomatik init olur — sıra iOS notundaki 'AppCheck ÖNCE' kuralına uymalı; gerekiyorsa FirebaseApp.initializeApp'i manuel çağır.
  - Mock/örnek veri grafiği: -ViFiMockData karşılığı (FM-18) için ayrı Hilt modülü veya 'mock' product flavor + test modülü; BuildConfig.DEBUG && mockFlag iken fake impl'ler enjekte edilsin, Firebase HİÇ çağrılmasın (iOS'un 'mock modda Firebase configure edilmez' kuralı).
  - @HiltViewModel ile ViewModel'lere bağımlılıklar @Inject constructor ile verilsin; Compose'da hiltViewModel() ile alınsın.
- **Gradle/bağımlılık:** Hilt 2.52 (hilt-android + hilt-compiler via KSP); androidx.hilt:hilt-navigation-compose 1.2.0; KSP eklentisi.
- **Bağımlılık:** FM-05, FM-06, FM-12, FM-17
- **Tuzaklar:** Hilt KSP ile (kapt değil) kullan — build hızı. Mock modda Firebase'e DOKUNULMAMALI (iOS kuralı) — fake grafiği gerçek Firebase provider'larını içe aktarmamalı. App Check factory FirebaseApp init'ten önce kurulmalı (iOS sırası). hilt-navigation-compose olmadan Compose'da ViewModel enjeksiyonu zahmetli.
- **Kabul kriteri:** @HiltAndroidApp başlar; bir ekran hiltViewModel() ile enjekte edilmiş repo alır; mock flavor/flag ile fake repo enjekte olur ve Firebase init edilmez.

#### `FM-12` — Servis arayüzü desenini kur (ServiceProtocols → repository interface'leri)  _(P0 · efor: M)_
- **iOS davranışı:** Her özellik protokol tipinde bağımlılık alır; live Firebase impl, mock örnek veri, test double aynı arayüzü uygular. Hatalar kullanıcıya Türkçe mesajla döner.
- **iOS karşılığı/kaynak:** iOS: her servis bir protokol arkasında — ArchiveRepository, RemoteFileLoading, RequestAuthorizing, AppUpdateChecking, AnalyticsTracking, AuthServicing, ProfileServicing, ExamSubmissionServicing, RewardsConfigReading, WalletReading, ModerationServicing, ExamUnlockServicing, UnlocksReading, NotificationServicing, LoginDiagnosticsReporting, AnnouncementsReading. Model tipleri nonisolated struct/enum: Sendable; hatalar ArchiveError/AuthError (Türkçe errorDescription, userMessage).
- **Android mevcut:** Arayüz soyutlaması yok; Activity'ler Firebase API'lerini doğrudan çağırıyor; sözleşme/test double yok.
- **Android teknoloji:** Kotlin interface + sealed class/data class model + sealed class Error; suspend fun / Flow imzaları
- **Adımlar:**
  - core/data katmanında Kotlin interface'leri tanımla (iOS protokol listesinin birebir karşılığı): ArchiveRepository, RemoteFileLoader, RequestAuthorizer, AppUpdateChecker, AnalyticsTracker, AuthService, ProfileService, ExamSubmissionService, RewardsConfigReader, WalletReader, ModerationService, ExamUnlockService, UnlocksReader, NotificationService, LoginDiagnosticsReporter, AnnouncementsReader.
  - Metot imzalarını suspend fun (async/await karşılığı) veya fun ...(): Flow<T> (AsyncStream karşılığı) olarak tasarla; ör. suspend fun items(path: ArchivePath): List<ArchiveItem>; fun archiveChanges(): Flow<Unit>; fun statusChanges(): Flow<AppUpdateStatus>.
  - Model tiplerini immutable Kotlin data class/enum/sealed olarak core/model'de tanımla (ArchivePath, ArchiveItem, ExamDocument, ExamKind, UserProfile+Gender, AppUpdate, AppUpdateStatus, AnalyticsEvent sealed, AnalyticsUser, CoinWallet, ExamLock, SubmissionTier...). Bu modeller Firebase'den bağımsız olmalı (saf, test edilebilir).
  - Hata tiplerini sealed class olarak modelle: ArchiveError (notFound/permissionDenied/offline/fileUnavailable/unknown) + userMessage: String Türkçe; AuthError benzer. iOS errorDescription metinlerini aynen kullan.
  - Her interface'in iki implementasyonu olacak: Firebase* (core/data/firebase) ve Fake*/Mock* (örnek veri, FM-18). Bu alan SADECE arayüz + model + hata iskeletini kurar; somut Firebase impl'leri ilgili özellik alanlarının işidir (deps).
  - Firestore/RTDB snapshot→model dönüşümünü saf parser fonksiyonlarına ayır (iOS FirestoreArchiveParser gibi, Firestore'suz test edilebilir).
- **Gradle/bağımlılık:** Ek bağımlılık yok (saf Kotlin); modeller Firebase'e bağımlı olmamalı.
- **Bağımlılık:** FM-05
- **Tuzaklar:** Modeller Firebase tiplerini (DocumentSnapshot vb.) SIZDIRMAMALI — saf kalmalı (test + mock için). suspend vs Flow seçimi iOS async vs AsyncStream ayrımını izlemeli. Hata mesajları Türkçe ve kullanıcı-dostu (iOS userMessage ile birebir).
- **Kabul kriteri:** Tüm servis arayüzleri + saf modeller + sealed hata tipleri tanımlı; bir arayüzün hem Fake hem (iskelet) Firebase impl'i derlenir; parser saf fonksiyon olarak Firebase'siz test edilebilir.

#### `FM-13` — UiState/Loadable + ViewModel + StateFlow desenini kur (@Observable karşılığı)  _(P0 · efor: M)_
- **iOS davranışı:** Her ekranda tek durum makinesi; yükleme tekilleştirme; retry. ViewModel view tarafından @State ile sahiplenilir (tek sahip).
- **iOS karşılığı/kaynak:** iOS: ekran durumu Loadable<Value> enum (loading/loaded(Value)/failed(message:)). ViewModel @Observable final class; bağımlılıklar @ObservationIgnored private let protokol tipinde; .task ile yükle; isFetching/isWorking ile çift-tetik/yarış koruması; ekranlar durumu switch'leyip LoadingStateView/content/ErrorStateView gösterir.
- **Android mevcut:** Durum yönetimi yok; Activity doğrudan View'ı günceller; yükleme/hata/boş durumları ad-hoc.
- **Android teknoloji:** androidx.lifecycle:lifecycle-viewmodel-compose + lifecycle-runtime-compose 2.8.6, StateFlow, sealed interface UiState, collectAsStateWithLifecycle, @HiltViewModel
- **Adımlar:**
  - Generic durum tipi tanımla: sealed interface UiState<out T> { object Loading; data class Loaded<T>(val value: T); data class Failed(val message: String) } — iOS Loadable karşılığı.
  - Her feature için @HiltViewModel class <Feature>ViewModel @Inject constructor(private val repo: ...Repository): ViewModel(); private val _state = MutableStateFlow<UiState<...>>(Loading); val state = _state.asStateFlow().
  - Yükleme fonksiyonu viewModelScope.launch içinde; isFetching Boolean guard ile çift-tetik önle (iOS isFetching). Hata yakalanınca Failed(error.userMessage).
  - Compose ekranda val state by viewModel.state.collectAsStateWithLifecycle(); when(state) { Loading->LoadingStateView; Loaded->content; Failed->ErrorStateView(onRetry) } (iOS switch karşılığı).
  - ViewModel'i hiltViewModel() ile ekran-kapsamında sahiplen (iOS'un @State ownership karşılığı; config değişiminde korunur).
  - İş-sürüyor (isWorking) bayraklarını buton tıklamalarında da uygula (iOS çift-dokunma koruması).
- **Gradle/bağımlılık:** androidx.lifecycle lifecycle-viewmodel-compose 2.8.6, lifecycle-runtime-compose 2.8.6 (collectAsStateWithLifecycle).
- **Bağımlılık:** FM-09, FM-11, FM-12
- **Tuzaklar:** collectAsStateWithLifecycle kullan (collectAsState değil) — arka planda gereksiz toplama önlenir. ViewModel config-change'de korunur; recompose'da yeniden yükleme yapma (iOS .task yarım-kalanı yeniden başlatır mantığını izle). StateFlow başlangıç değeri Loading olmalı.
- **Kabul kriteri:** Bir ekran Loading→Loaded/Failed durumlarını gösterir; retry çalışır; ekran döndürmede durum korunur; çift tıklama tekil iş tetikler.

#### `FM-14` — DesignSystem/Tema kur (Material3 tema, renk tokenları, durum görünümleri, kontrast kuralı)  _(P0 · efor: M)_
- **iOS davranışı:** Tutarlı ikon/durum/rozet bileşenleri; marka turuncu; erişilebilir kontrast; dark mode (systemBackground). CoinBalanceBadge toolbar'da canlı bakiye.
- **iOS karşılığı/kaynak:** iOS DesignSystem: IconBadge (SF Symbol, yuvarlak köşeli yumuşak zemin), LoadingStateView/ErrorStateView(retry)/EmptyStateView (ContentUnavailableView), CoinBalanceBadge (turuncu pil, numericText animasyonu, bitcoinsign ikon), ArchiveLevel/ExamKind için symbolName+başlık+sayaç metni. KONTRAST KURALI: turuncu zeminde SİYAH metin (beyaz 3:1 altında). AccentColor marka turuncu.
- **Android mevcut:** Theme.AppCompat.Light.DarkActionBar; colors.xml: colorPrimary/Dark/Accent #464646, ikincil turuncu #FB920F; dark mode yok; tasarım sistemi yok.
- **Android teknoloji:** Material3 MaterialTheme (ColorScheme light/dark, Typography, Shapes), dynamicColor kapalı (marka turuncu sabit), Material Icons, androidx.compose.animation
- **Adımlar:**
  - ViFiTheme composable kur: lightColorScheme/darkColorScheme ile marka turuncu (primary ~ #FB920F) + nötr yüzeyler; dynamicColor=false (marka kimliği sabit kalsın).
  - Renk tokenlarını Color.kt'de tanımla; iOS AccentColor turuncu + siyah-üstü-turuncu kontrast kuralını buton/zemin kombinasyonlarında uygula (onPrimary = Black).
  - Durum görünümleri: LoadingState (CircularProgressIndicator + metin), ErrorState (ikon + mesaj + 'Tekrar Dene' butonu, onClick retry), EmptyState — iOS Components.swift karşılığı; test/erişilebilirlik için testTag/contentDescription ('state.loading'/'state.error'/'state.retry'/'state.empty').
  - IconBadge composable: yuvarlak köşeli (RoundedCornerShape) yumuşak zeminli ikon; ArchiveLevel/ExamKind için Material ikon + listTitle + childCountText('5 fakülte' vb., Türkçe) eşlemesi (LevelStyle.swift karşılığı).
  - CoinBalanceBadge composable: turuncu capsule pil, coin ikon, animateIntAsState ile sayı animasyonu (numericText karşılığı); üst bar'da gösterilir, CoinWalletStore/StateFlow'dan canlı okur (Coin alanı sağlar).
  - Typography ve Shapes'i tanımla; Dynamic Type karşılığı olarak fontScale'e saygı (sp birimleri).
- **Gradle/bağımlılık:** compose material3, androidx.compose.material:material-icons-extended, androidx.compose.animation (compose-bom içinde).
- **Bağımlılık:** FM-09
- **Tuzaklar:** dynamicColor (Material You) marka turuncuyu ezer — kapatılmalı. onPrimary=Black kontrast kuralı (iOS: turuncuda siyah). Dark mode renkleri eksikse targetSdk 35'te sistem dark'ta okunmaz UI. Erişilebilirlik testTag'leri UI testleriyle sözleşme (iOS accessibilityIdentifier karşılığı) — tutarlı adlandır.
- **Kabul kriteri:** ViFiTheme light+dark render olur; marka turuncu sabit; Loading/Error/Empty + IconBadge + CoinBalanceBadge composable'ları önizlenir; turuncu butonda siyah metin.

#### `FM-15` — İsimli 'vifi' Firestore DB + RTDB provider singletonlarını kur (offline persistence)  _(P0 · efor: S)_
- **iOS davranışı:** Tek, paylaşımlı, offline-önbellekli örnekler; ayarlar iki kez uygulanmaz (çökme önlenir). İsimli 'vifi' DB eur3.
- **iOS karşılığı/kaynak:** iOS ViFiFirestore.shared: Firestore.firestore(database: 'vifi') + PersistentCacheSettings, process başına BİR kez (ayarlar ilk erişimden önce). ViFiDatabase.shared: Database.database() + isPersistenceEnabled + 50MB cache + özel callback queue, tek sefer.
- **Android mevcut:** FirebaseDatabase.getInstance() ile (default) RTDB; Firestore hiç yok; persistence/cache ayarı dağınık.
- **Android teknoloji:** FirebaseFirestore.getInstance(FirebaseApp.getInstance(), "vifi"), FirebaseFirestoreSettings (PersistentCacheSettings), FirebaseDatabase.getInstance() setPersistenceEnabled + setPersistenceCacheSizeBytes
- **Adımlar:**
  - Firestore provider: @Provides @Singleton FirebaseFirestore = FirebaseFirestore.getInstance(FirebaseApp.getInstance(), "vifi") — İSİMLİ DB (default DEĞİL); settings = firestoreSettings { setLocalCacheSettings(persistentCacheSettings{}) } ilk erişimden önce uygula.
  - RTDB provider: @Provides @Singleton FirebaseDatabase = FirebaseDatabase.getInstance().apply { setPersistenceEnabled(true); setPersistenceCacheSizeBytes(50L*1024*1024) } — ilk referanstan önce (Hilt Singleton garanti eder).
  - Her iki provider'ı Hilt SingletonComponent modülünde ver; tüm repository'ler bu örnekleri @Inject ile alsın (doğrudan getInstance() çağrısı YASAK — iOS tek-örnek kuralı).
  - Mock modda (FM-18) bu provider'lar HİÇ çağrılmamalı (iOS: mock'ta Firebase'e dokunma).
  - RTDB 'Universitiess' eski ağacını okuma iskeleti (rollback) ile Firestore catalogNodes/exams okuma iskeleti ayrı repository'lerde (Arşiv alanı dolduracak); bu alan sadece provider'ları kurar.
- **Gradle/bağımlılık:** firebase-firestore, firebase-database (BoM'dan, FM-06).
- **Bağımlılık:** FM-06, FM-11
- **Tuzaklar:** getInstance() (default) DB BOŞ döner — mutlaka (app, 'vifi'). setPersistenceEnabled İLK referanstan önce çağrılmalı yoksa RuntimeException (iki kez uygulanırsa çökme — iOS ile aynı tuzak). Firestore setLocalCacheSettings ilk işlemden önce. App Check Firestore enforce şu an kapalı (monitoring) ama Storage/RTDB enforce açık.
- **Kabul kriteri:** Firestore 'vifi' DB'den bir doküman okunur (default değil); RTDB persistence açık ve tek örnek; iki kez init çökmesi yok; mock modda provider çağrılmaz.

#### `FM-16` — Cloud Functions bölgesini europe-west1'e sabitle (callable wiring)  _(P1 · efor: S)_
- **iOS davranışı:** unlockExam ve recordLoginDiagnostic callable'ları europe-west1'de; yanlış bölge 'not-found' verir. Hata kodları kullanıcı mesajına eşlenir.
- **iOS karşılığı/kaynak:** iOS FirebaseExamUnlockService: Functions.functions(region: 'europe-west1').httpsCallable('unlockExam').call([examId:...]); FunctionsErrorCode→ExamUnlockError eşlemesi (failedPrecondition=insufficientCoins, notFound, permissionDenied/unauthenticated=notSignedIn, unavailable/deadlineExceeded=offline).
- **Android mevcut:** Functions hiç kullanılmıyor (firebase-functions yok).
- **Android teknoloji:** FirebaseFunctions.getInstance(FirebaseApp.getInstance(), "europe-west1"), getHttpsCallable(name).call(data), FirebaseFunctionsException.Code
- **Adımlar:**
  - @Provides @Singleton FirebaseFunctions = FirebaseFunctions.getInstance(FirebaseApp.getInstance(), "europe-west1").
  - Callable çağrı yardımcı deseni: functions.getHttpsCallable("unlockExam").call(mapOf("examId" to id)).await() (kotlinx-coroutines-play-services).
  - FirebaseFunctionsException.Code → domain hata eşlemesi: FAILED_PRECONDITION→insufficientCoins, NOT_FOUND→notFound, PERMISSION_DENIED/UNAUTHENTICATED→notSignedIn, UNAVAILABLE/DEADLINE_EXCEEDED→offline (iOS FirebaseExamUnlockService.unlockError karşılığı).
  - Bu alan Functions provider + hata eşleme iskeletini kurar; unlockExam (Coin alanı) ve recordLoginDiagnostic (Auth/Diagnostics alanı) çağrıları ilgili alanlara devredilir (deps).
  - App Check token'ı callable'a otomatik eklenir (Play Integrity provider kurulu olmalı, FM-17).
- **Gradle/bağımlılık:** firebase-functions (BoM); kotlinx-coroutines-play-services (.await()).
- **Bağımlılık:** FM-06, FM-11, FM-17
- **Tuzaklar:** Bölge 'europe-west1' birebir olmalı (yanlışsa NOT_FOUND). .call() App Check token ekler — provider yoksa UNAUTHENTICATED. unlockExam idempotent ve UNLOCK_COST=100 (client ExamLock.unlockCost ile aynı olmalı — Coin alanı).
- **Kabul kriteri:** FirebaseFunctions europe-west1'de; bir callable çağrısı .await() ile döner; FunctionsException kodları domain hatalarına eşlenir.

#### `FM-17` — App Check provider factory kur (Play Integrity + Debug)  _(P0 · efor: S)_
- **iOS davranışı:** Release'te cihaz bütünlüğü kanıtı; debug'ta debug token (Console'a eklenmeli). Factory configure'dan önce kurulur.
- **iOS karşılığı/kaynak:** iOS ViFiAppCheckProviderFactory: Debug/simülatör→AppCheckDebugProvider; Release→App Attest (DCAppAttestService destekliyse) yoksa DeviceCheck. Kurulum: AppCheck.setAppCheckProviderFactory HER ZAMAN FirebaseApp.configure()'dan ÖNCE.
- **Android mevcut:** App Check yok; Storage/RTDB/Auth enforce açık olduğundan modern backend'e hiçbir çağrı geçmez.
- **Android teknoloji:** FirebaseAppCheck.getInstance().installAppCheckProviderFactory(...), PlayIntegrityAppCheckProviderFactory, DebugAppCheckProviderFactory
- **Adımlar:**
  - firebase-appcheck-playintegrity (release) + firebase-appcheck-debug (debug) bağımlılıkları (FM-06).
  - ViFiApplication.onCreate içinde, FirebaseApp init'ten ÖNCE (iOS sırası): val factory = if (BuildConfig.DEBUG) DebugAppCheckProviderFactory.getInstance() else PlayIntegrityAppCheckProviderFactory.getInstance(); FirebaseAppCheck.getInstance().installAppCheckProviderFactory(factory).
  - Debug build'de logcat'te basılan debug token'ı Firebase Console › App Check › Debug tokens'a ekle (her yeni emülatör/cihaz için).
  - Play Integrity için app'in SHA-256 parmak izi Firebase Console'a eklenmeli; Google Cloud'da Play Integrity API etkin olmalı.
  - Mock modda App Check kurulumu atlanır (Firebase hiç init edilmez — FM-18/FM-11).
  - iOS App Attest→Android Play Integrity eşlemesi; enforce ayarları backend tarafında (App Check alanı/backend).
- **Gradle/bağımlılık:** firebase-appcheck-playintegrity, firebase-appcheck-debug (BoM).
- **Bağımlılık:** FM-06, FM-11
- **Tuzaklar:** installAppCheckProviderFactory FirebaseApp init'ten ÖNCE (iOS kuralı) — google-services otomatik init zamanlamasına dikkat; gerekiyorsa FirebaseApp.initializeApp manuel. Debug token Console'a eklenmezse debug build'de tüm Storage/RTDB/Auth 403. Release'te SHA-256 yoksa Play Integrity başarısız. SafetyNet provider DEPRECATED — Play Integrity kullan.
- **Kabul kriteri:** Debug build logcat'e debug token basar ve Console'a eklenince Storage/RTDB okur; release Play Integrity ile token üretir; factory FirebaseApp init'ten önce kurulur.

#### `FM-18` — Mock/preview altyapısı ve launch-argument karşılığını kur (-ViFiMockData ve deterministik durumlar)  _(P1 · efor: M)_
- **iOS davranışı:** Firebase'siz, ağ'sız, tekrarlanabilir UI testleri ve demolar; önizlemeler. Mock modda gerçek Firebase'e dokunmak çökme.
- **iOS karşılığı/kaynak:** iOS: -ViFiMockData ile Firebase HİÇ configure edilmez, paket içi örnek arşivle çalışır; AppEnvironment.mock() + MockServices (MockAuthService, örnek veri). Ek argümanlar: -ViFiSignedOut, -ViFiPhoneAuthTesting, -ViFiForceUpdate, -ViFiProfileIncomplete, -ViFiAdmin, -ViFiNoCoins, -ViFiShowOnboarding, -ViFiNotificationsPrompt — her biri deterministik bir başlangıç durumu (UI testleri). Her ekranda #Preview AppEnvironment.mock() kullanır.
- **Android mevcut:** Mock/örnek veri yok; tüm ekranlar canlı Firebase'e bağlı; UI testi/önizleme altyapısı yok.
- **Android teknoloji:** BuildConfig flag / Intent extra / 'mock' product flavor, Hilt test modülü (@TestInstallIn), Compose @Preview, androidx.test espresso/compose-ui-test
- **Adımlar:**
  - Mock modu seç (openQuestions): önerilen = Intent extra (ör. intent.getBooleanExtra("vifi_mock_data", false)) + BuildConfig.DEBUG guard, VEYA ayrı 'mock' product flavor. Instrumented testler için Hilt @TestInstallIn ile fake modül değişimi en temizi.
  - LaunchArgs karşılığı bir enum/konstant seti tanımla: MOCK_DATA, SIGNED_OUT, PHONE_AUTH_TESTING, FORCE_UPDATE, PROFILE_INCOMPLETE, ADMIN, NO_COINS, SHOW_ONBOARDING, NOTIFICATIONS_PROMPT — Intent extra anahtarları (iOS launch argümanları karşılığı). Her biri fake grafiğin davranışını belirler.
  - Fake*/Mock* servis impl'leri yaz (FM-12 arayüzlerini uygular): FakeArchiveRepository (örnek üniv/fakülte/ders/sınav), FakeAuthService (kod '111111' veya signedOut), FakeProfileService, FakeWalletReader (noCoins), FakeModeration (admin) vb. iOS MockServices karşılığı. Örnek sınav görselleri/pdf'i src/debug veya 'mock' flavor assets'ine koy (release'e GİRMEMELİ — iOS EXCLUDED_SOURCE_FILE_NAMES karşılığı).
  - Mock modda Firebase provider'ları (FM-15/FM-16/FM-17) HİÇ oluşturulmamalı — Hilt grafiği mock dalında yalnızca Fake'leri sağlar; FirebaseApp init guard'la (iOS kuralı).
  - Her Compose ekranına @Preview ekle (fake state ile); PreviewParameterProvider ile farklı UiState'ler (Loading/Loaded/Failed) önizlensin (iOS #Preview karşılığı).
  - Instrumented UI testleri (androidTest) fake grafiği + launch-arg karşılığı extra'larla deterministik senaryoları sürsün (ForceUpdate gate, ProfileIncomplete gate, NoCoins kilit vb.).
- **Gradle/bağımlılık:** androidx.test:core/runner/rules, androidx.compose.ui:ui-test-junit4 (androidTest), Hilt testing (hilt-android-testing + hilt-compiler androidTest), opsiyonel 'mock' product flavor.
- **Bağımlılık:** FM-11, FM-12, FM-09
- **Tuzaklar:** Örnek sınav dosyaları RELEASE APK'ya girmemeli (src/debug veya mock flavor; iOS EXCLUDED_SOURCE_FILE_NAMES). Mock dalı gerçek Firebase provider'ını import ederse mock modda Firebase init olur (iOS kuralı ihlali → çökme riski). Intent extra ile mock üretimde güvenlik: sadece BuildConfig.DEBUG'ta aktif olmalı.
- **Kabul kriteri:** Debug'da mock extra ile uygulama Firebase'siz, örnek arşivle açılır; en az bir UI testi fake grafikle deterministik çalışır; ekranlar @Preview'da Loading/Loaded/Failed gösterir; release APK örnek dosya içermez.

#### `FM-19` — Yapılandırılmış loglama altyapısı kur (OSLog kategorileri → Timber)  _(P2 · efor: S)_
- **iOS davranışı:** Kategorili, gizlilik-farkında loglama; release'te hassas veri sızmaz.
- **iOS karşılığı/kaynak:** iOS: OSLog Logger kategorileri — Logger.archive, Logger.files, Logger.app; privacy niteleyicileri (.public/.private); print YASAK.
- **Android mevcut:** android.util.Log dağınık (örn. Log.d); kategori yok; release'te log sızıntısı.
- **Android teknoloji:** Timber 5.0.1 (DebugTree debug'ta, release'te noop/crashlytics tree); tag/kategori ağaçları
- **Adımlar:**
  - Timber 5.0.1 ekle; ViFiApplication.onCreate: if (BuildConfig.DEBUG) Timber.plant(DebugTree()) else Timber.plant(ReleaseTree) (release'te sadece error/warn, hassas veri yok).
  - Kategori eşdeğeri için sabit tag'ler/yardımcılar tanımla: Archive, Files, App (iOS kategorileri). Timber.tag("Archive").d(...).
  - android.util.Log çağrılarını Timber'a taşı; hassas veri (telefon numarası, token) loglanmamalı (iOS .private karşılığı — release'te maskele/atla).
  - Opsiyonel: Crashlytics ağacı (firebase-crashlytics) ileride; bu alan Timber iskeletini kurar.
- **Gradle/bağımlılık:** com.jakewharton.timber:timber:5.0.1.
- **Bağımlılık:** FM-05, FM-11
- **Tuzaklar:** Release'te DebugTree bırakılırsa log sızar. Telefon numarası/token asla açık loglanmamalı (loginDiagnostics salted-hash+last4 kuralı — Diagnostics alanı). ProGuard loglama satırlarını release'te strip edebilir (opsiyonel).
- **Kabul kriteri:** Timber kurulu; debug'ta kategorili log, release'te hassas veri loglanmıyor; android.util.Log doğrudan kullanımı kalmadı.

#### `FM-20` — R8/ProGuard, release küçültme, imzalama ve Play Data Safety iskeletini kur  _(P1 · efor: M)_
- **iOS davranışı:** Shipped build küçültülmüş, hassas/örnek içerik hariç; gizlilik beyanı güncel.
- **iOS karşılığı/kaynak:** iOS karşılığı: PrivacyInfo.xcprivacy + App Store gizlilik etiketi (atlanacak iOS-özel); Release'te örnek dosya hariç tutma. Android muadili R8/ProGuard + Play Data Safety formu.
- **Android mevcut:** release { minifyEnabled false; proguardFiles ... } — küçültme KAPALI; imza yapılandırması yok; PrivacyInfo/Data Safety yok.
- **Android teknoloji:** R8 (AGP 8 varsayılan), proguard-rules.pro, signingConfigs, Play Console Data Safety
- **Adımlar:**
  - release buildType: minifyEnabled true, shrinkResources true, proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'.
  - proguard-rules.pro'ya keep kuralları: Firestore POJO/data class deserializasyonu (-keepclassmembers class ...model.** { <init>(...); *; } veya @Keep), FirebaseFunctions callable model'leri, Kotlin serialization (@Serializable route'lar için -keep kuralları), Hilt/Compose zaten AGP ile korunur.
  - signingConfigs { release { ... } } iskeleti (anahtarlar REPOYA GİRMEZ — gradle.properties/keystore.properties .gitignore'da; CI secret). versionCode/Name release'e bağla.
  - Play Console Data Safety formu için toplanan veri envanteri çıkar: telefon numarası (auth), kullanıcı profili (RTDB), FCM token, analytics segmentleri, coin bakiyesi — iOS gizlilik etiketi karşılığı (bu alan envanteri hazırlar, form submit ürün sahibinin).
  - R8 sonrası smoke test: Firestore okuma, callable çağrı, auth akışı release build'de bozulmamalı (reflection keep doğrula).
- **Gradle/bağımlılık:** R8 (AGP gömülü); keystore.properties (.gitignore); proguard-android-optimize.txt.
- **Bağımlılık:** FM-06, FM-12
- **Tuzaklar:** R8 tam-mod Firestore/Functions model'lerini sıyırabilir → runtime deserializasyon null/çökme; keep kuralları şart. Kotlin serialization route'ları için keep. Keystore/parolalar ASLA commit'lenmez (iOS CLAUDE.md sır kuralı). shrinkResources, kullanılmayan XML/asset'leri atar — mock örnek dosyaları zaten release'te olmamalı (FM-18).
- **Kabul kriteri:** Release build minify+shrink ile üretilir; Firestore okuma/callable/auth release'te çalışır (keep doğru); keystore repoda yok; Data Safety veri envanteri hazır.

#### `FM-21` — Paket/modül yapısını yeniden düzenle (iOS katman mimarisi karşılığı)  _(P1 · efor: M)_
- **iOS davranışı:** Net katman ayrımı; özellikler izole; test edilebilir çekirdek.
- **iOS karşılığı/kaynak:** iOS yapısı: App/ (ViFiApp, AppDelegate, AppEnvironment, RootView, Router), Core/Models, Core/Services (protokoller+Firebase impl), Core/Utilities (Loadable, Logger, Slug, StorageURL...), DesignSystem/, Features/ (Home, Browse, Exam, AddExam, Moderation, Auth, Profile, Account, Notifications, Onboarding, Update).
- **Android mevcut:** Tüm sınıflar tek paket com.salticusteam.vifi altında düz (Activity/Fragment/Adapter karışık); katman yok.
- **Android teknoloji:** Paket-bazlı katmanlama (tek modül) veya çok-modül (core/data, core/model, core/designsystem, feature/*); Gradle modülleri opsiyonel
- **Adımlar:**
  - Paket iskeletini kur (tek modül, paket-bazlı — başlangıç için yeterli): com.salticusteam.vifi.core.model, core.data (interface'ler), core.data.firebase (impl'ler), core.data.fake, core.designsystem, core.util, feature.home, feature.browse, feature.exam, feature.addexam, feature.moderation, feature.auth, feature.profile, feature.account, feature.notifications, feature.onboarding, feature.update, app (ViFiApplication, MainActivity, navigation Router, DI modülleri).
  - Utility karşılıkları: core.util'e Slug (kanonik slug [a-z0-9-], FCM topic/segment için — iOS Slug.swift), StorageURL (storagePath→indirme URL kanonik), AppVersion (semver karşılaştırma), Loadable/UiState (FM-13), ContactSupport (mailto 'Bize Ulaş' — iOS ContactSupport) ekle.
  - Opsiyonel: ilerde Gradle çok-modül (:core:data, :core:designsystem, :feature:*) — büyük efor; başlangıçta paket-bazlı yeterli, build hızı gerekince modülerleş.
  - iOS Features/ dizinlerinin birebir karşılığı feature.* paketleri; her feature Route + Screen + ViewModel + Preview içerir.
- **Gradle/bağımlılık:** (Tek modül için ek bağımlılık yok; çok-modül seçilirse Gradle modül yapılandırması.)
- **Bağımlılık:** FM-05
- **Tuzaklar:** android.nonTransitiveRClass=true ile paket R sınıfları — kaynak referansları doğru paketlenmeli. Çok-modüle erken geçiş gereksiz karmaşa; önce paket-bazlı. Slug üretimi backend kanonik id'leriyle (profil *Id slug'ları) tutarlı olmalı (profil/segment alanları).
- **Kabul kriteri:** Katmanlı paket yapısı kurulu; core util (Slug/StorageURL/AppVersion/UiState) mevcut; her feature kendi paketinde; derleme temiz.

#### `FM-22` — Tek-Activity Compose host + edge-to-edge + yaşam döngüsü kancalarını kur (RootView/scenePhase karşılığı)  _(P0 · efor: M)_
- **iOS davranışı:** Tek pencere, katmanlı kapılar, yaşam döngüsü merkezi. Uygulama aktif değilken içerik app switcher'da görünmez.
- **iOS karşılığı/kaynak:** iOS RootView tek giriş: ForceUpdate gate (tüm UI'yı değiştirir, kapatılamaz) → oturum (Login/ProfileGate/Archive) → NavigationStack. scenePhase: inactive→AppSwitcherCover (gizlilik örtüsü), active→badge temizle, background→disk cache budama. onOpenURL (reCAPTCHA dönüşü). ViFiApp @main WindowGroup.
- **Android mevcut:** Çok sayıda Activity (HomeActivity, MainActivity, LoginActivity, ExamDetailActivity...); her biri setContentView(XML); yaşam döngüsü dağınık.
- **Android teknoloji:** Tek ComponentActivity + setContent, enableEdgeToEdge(), Lifecycle.Event/LifecycleEventObserver, Material3 Scaffold, WindowInsets
- **Adımlar:**
  - Tek MainActivity : ComponentActivity; onCreate: enableEdgeToEdge(); setContent { ViFiTheme { AppRoot() } }. Diğer Activity'ler emekliye (FM-23).
  - AppRoot composable = iOS RootView karşılığı: katmanlı when — ForceUpdate gate (tam ekran, geri tuşu engelli — BackHandler ile) → SessionState (Loading/Login/ProfileGate→NavHost). Gate'ler Compose katmanı olarak (iOS ZStack/if karşılığı).
  - Yaşam döngüsü: LifecycleEventObserver veya LocalLifecycleOwner ile ON_START/ON_STOP; app arka plana geçince (ON_STOP) fileLoader.pruneDiskCache (Files/RemoteFileLoader alanı), öne gelince bildirim badge temizle (Bildirimler alanı). iOS onChange(scenePhase) karşılığı.
  - Gizlilik örtüsü (iOS AppSwitcherCover): Recent apps önizlemesinde içerik gizleme — bu Ekran Koruması alanının işi (FLAG_SECURE pencere bayrağı); bu alan tek-activity host'u ve kanca noktasını sağlar, FLAG_SECURE'u o alan ekler.
  - Deep link (bildirimden exam açma, reCAPTCHA/phone-auth dönüşü) için MainActivity intent-filter + NavController deep link iskeleti (iOS onOpenURL karşılığı; Auth/Bildirim alanlarıyla koordine).
  - signedOut olunca Router.popToRoot + ilgili store reset (profile/wallet/unlocks/recents/announcements/analytics.setUser(null)) — iOS RootView.onChange(session.state) karşılığı (store'lar ilgili alanlardan).
- **Gradle/bağımlılık:** activity-compose (enableEdgeToEdge), lifecycle-runtime-compose (FM-13).
- **Bağımlılık:** FM-09, FM-10, FM-14
- **Tuzaklar:** targetSdk 35 edge-to-edge'i ZORLAR — Scaffold + WindowInsets ile content system bar altında kalmasın. ForceUpdate gate'te BackHandler(enabled=true){} ile geri tuşu yutulmalı (kapatılamaz — iOS kuralı). Tek-activity'ye geçişte eski Activity intent-filter'ları (LAUNCHER) MainActivity'ye taşınmalı. FLAG_SECURE ayrı alan ama host burada.
- **Kabul kriteri:** Tek MainActivity Compose host; edge-to-edge düzgün; ForceUpdate gate geri tuşunu yutar; arka plan/ön plan kancaları tetiklenir; signedOut köke döner ve store'lar sıfırlanır.

#### `FM-23` — Eski Activity/Fragment/Adapter iskeletini ve köhne yolları emekliye ayır  _(P1 · efor: M)_
- **iOS davranışı:** Modern, tek-giriş, phone-auth, remote-config güncelleme.
- **iOS karşılığı/kaynak:** iOS'ta bunların hiçbiri yok — tek RootView + NavigationStack + feature ekranları. E-posta/şifre girişi YOK (sadece +90 phone auth). Güncelleme Play HTML kazımayla değil RTDB config/appUpdate ile.
- **Android mevcut:** HomeActivity, MainActivity, LoginActivity (e-posta/şifre!), AddLessonActivity, AddPdfLessonActivity, CourseExamListActivity, ExamDetailActivity, ExamDetailPdfActivity, ImageZoomActivity, AddAnnouncements, AnnouncementsActivity; UniversityFragment/FacultiesFragment/DepartmentsFragment/LessonFragment; CustomAdapter/ListViewAdapter/ListViewItem*; GooglePlayAppVersion (Play Store HTML kazıyan AsyncTask); PostClass; MyFirebaseInstanceIDService.
- **Android teknoloji:** Legacy teardown; Activity→Compose ekran; AsyncTask→RTDB RemoteConfigReader (Zorunlu Güncelleme alanı)
- **Adımlar:**
  - Tüm eski Activity'leri AndroidManifest'ten kaldır (MainActivity Compose host hariç); XML layout'ları ve ilgili R referanslarını temizle (Compose ekranlarına taşındıkça).
  - LoginActivity'nin e-posta/şifre akışını (createUserWithEmailAndPassword/signIn) TAMAMEN kaldır — iOS yalnızca +90 Phone Auth kullanır; gerçek giriş Auth alanı tarafından Compose + PhoneAuthProvider.verifyPhoneNumber ile yeniden yazılır (dep).
  - GooglePlayAppVersion AsyncTask'ı sil — güncelleme kontrolü RTDB config/appUpdate/android okunarak yapılır (Zorunlu Güncelleme alanı); Play HTML kazıma kaldırılır.
  - Fragment'leri (UniversityFragment vb.) ve Adapter'ları (CustomAdapter/ListViewAdapter) emekliye ayır — Browse/Home Compose ekranlarıyla değişir (Arşiv/Browse alanı).
  - PostClass, ListViewItem*, AddAnnouncements/AnnouncementsActivity gibi eski modeller/ekranları ilgili yeni feature'lara taşındıkça sil.
  - Bu alan teardown'u koordine eder ve iskelet boşluğunu bırakır; her ekranın yeni Compose implementasyonu ilgili feature alanının işidir (deps).
- **Gradle/bağımlılık:** (Kaldırma; yeni bağımlılık yok.)
- **Bağımlılık:** FM-22, FM-10
- **Tuzaklar:** LAUNCHER intent-filter mutlaka yeni MainActivity'ye taşınmalı yoksa uygulama açılmaz. E-posta/şifre auth kaldırılınca mevcut test hesapları geçersiz — phone-auth test numaralarıyla (Console) devam. Eski Activity'lere başka sınıflardan Intent referansı kalmış olabilir (derleme kırılır). Teardown feature rewrite'larla eşzamanlı yürütülmeli (büyük patlama değil, kademeli).
- **Kabul kriteri:** Manifest'te yalnızca tek MainActivity (+ FCM service); e-posta/şifre auth yok; GooglePlayAppVersion yok; eski Fragment/Adapter kalıntıları temizlendi veya yeni ekranlara devredildi; uygulama LAUNCHER'dan açılır.

#### `FM-24` — DataStore/SharedPreferences kalıcı tercih katmanını kur (UserDefaults karşılığı)  _(P2 · efor: S)_
- **iOS davranışı:** Cihaz-başı kalıcı küçük durum: onboarding görüldü, atlanan opsiyonel güncelleme sürümü, bildirim/prefetch bayrakları.
- **iOS karşılığı/kaynak:** iOS: UserDefaults ile cihaz-başı bayraklar — OnboardingStore (ilk-açılış turu görüldü), UpdatePromptDefaults (skippedUpdateVersion), prefetch/announcements defaults. @Observable store'lar bunları sarar.
- **Android mevcut:** Kalıcı tercih katmanı yok (SharedPreferences dağınık kullanılıyor olabilir).
- **Android teknoloji:** androidx.datastore:datastore-preferences 1.1.1 (Preferences DataStore), Flow<Preferences>
- **Adımlar:**
  - androidx.datastore:datastore-preferences:1.1.1 ekle; @Singleton bir ViFiPreferences sarmalayıcı (Hilt ile verilir).
  - Anahtar iskeleti: onboardingSeen (Boolean), skippedUpdateVersion (String), notificationsPrompted (Boolean), prefetchDone vb. — iOS OnboardingStore/UpdatePromptDefaults karşılıkları (değerleri ilgili alanlar yazar).
  - Okuma Flow<T> (reaktif, iOS @Observable store karşılığı); yazma suspend edit{}.
  - Bu alan DataStore iskeletini kurar; her bayrağın semantiği ilgili feature alanının işidir (Onboarding, Zorunlu Güncelleme, Bildirimler — deps).
- **Gradle/bağımlılık:** androidx.datastore:datastore-preferences 1.1.1.
- **Bağımlılık:** FM-05, FM-11
- **Tuzaklar:** DataStore async (Flow/suspend) — senkron SharedPreferences bekleyen eski kodla karışmasın. Hassas veri (token) DataStore'a düz yazılmamalı. Oturum kapanınca kullanıcı-özel bayraklar temizlenmeli mi (iOS: onboarding cihaz-başı, kalır) — semantiği ilgili alanla netleştir.
- **Kabul kriteri:** DataStore kurulu; bir bayrak (ör. onboardingSeen) yazılıp Flow ile okunur; uygulama yeniden açılışta korunur.


---

## Telefon Girişi + App Check + Login Diagnostics  ·  `auth`

**Özet:** iOS v3.1.0 tüm uygulamayı +90 telefon doğrulaması arkasına alır (SMS kod, reCAPTCHA/App Attest ile app verification, 60 sn resend cooldown, Error 39 SMS anti-abuse açıklaması, giriş ekranında "Bize Ulaş" mailto, başarısız girişlerin recordLoginDiagnostic callable'ına raporlanması, SessionStore oturum kapısı). Android v2.5 ise SADECE yöneticiye yönelik bir e-posta/şifre LoginActivity'ye sahip; normal kullanıcı için HİÇBİR kimlik doğrulama kapısı yok ve uygulama RTDB'yi anonim okuyor. Ortak Firestore kuralları artık read için +90|admin istediğinden, telefon auth kapısı eklenmezse Android'de tüm okumalar permission-denied olur: bu alan Android için P0 bloklayıcıdır. Aşağıdaki kalemler iOS'taki her davranışı (otomatik kod gönderimi/instant verification gibi Android'e özgü ekler dahil) birebir kapsar. E-posta/şifre auth tamamen kaldırılır; AdMob bu akıştan çıkarılır.

**Android mevcut durum:** LoginActivity.java yalnızca mAuth.createUserWithEmailAndPassword / signInWithEmailAndPassword kullanan, başarıda AddLessonActivity'ye (admin ekran) giden bir ekran. activity_login.xml iki EditText (e-mail/password, inputType yanlış: textPersonName) + görünmez SIGN UP + SIGN IN butonu. Telefon auth, SMS kod, App Check, login diagnostics, resend cooldown, "Bize Ulaş", SessionStore KARŞILIĞI YOK. Firebase SDK'lar 2018 sürümü (firebase-auth:16.2.0, firebase-core:16.0.8), AndroidX öncesi (android.support.*), AGP 3.4.2, Gradle 5.1.1, jcenter (ölü), compileSdk/targetSdk 28, minSdk 19. App Check bağımlılığı yok. Firebase Functions SDK yok. google-services.json doğru (vifi-831a8, com.salticusteam.vifi, mobilesdk_app_id 1:441043948320:android:44ac92bbd108f62e) fakat SHA-1/SHA-256 parmak izlerinin Console'da kayıtlı olması telefon auth için zorunlu ve doğrulanmalı.

**Dokunulan backend sözleşmesi:** recordLoginDiagnostic (onCall, europe-west1, enforceAppCheck=FALSE): Android AYNEN yeniden kullanır. Payload alan adları birebir: {reason(<=64), step('send_code'|'verify'), phoneNumber(+90, server salted-sha256+last4'e çevirir), appCheckStatus('valid'|'error'|'unavailable'), backendCode(int, ops), appVersion('<name> (<code>)'), osVersion, deviceModel, occurredAt(ISO8601)}. Server appCheckServer'ı (valid/invalid/absent) ham x-firebase-appcheck header'ından kendi hesaplar, createdAt serverTimestamp, expireAt 60 gün TTL. lookup-login-diagnostics.mjs scripti platform-bağımsızdır, Android kayıtlarını da bulur (numberHash aynı +90 E.164 üzerinden). Firebase Phone Auth, Firestore 'vifi' veritabanı, Storage ve RTDB kuralları değişmez; Android yalnızca bunlara UYAR. YENİ backend yazılmaz. (Android'e eklenecek tek config boşlukları bu alanın DIŞINDA: RTDB config/appUpdate/android dalı update alan…

**Android'e özgü riskler:**
- Google Play artık güncellemeler için targetSdk 35 zorunlu kılıyor (Ağu 2025); targetSdk 28'de kalınırsa yeni sürüm yayınlanamaz. compileSdk/targetSdk 35'e çıkarılmalı.
- Güncel Firebase BoM (33.x) ve Play Integrity minSdk 21 ister; minSdk 19 -> 21'e çıkarılmalı (19/20'de pazar payı ihmal edilebilir).
- Android Phone Auth iOS'ta OLMAYAN instant verification / auto-retrieval (onVerificationCompleted) üretebilir: kullanıcı kod GİRMEDEN oturum açılabilir. Bu callback mutlaka ele alınıp otomatik signInWithCredential yapılmalı, yoksa kod ekranında takılı kalır.
- PhoneAuthProvider.verifyPhoneNumber Android'de bir Activity referansı ister (reCAPTCHA fallback için setActivity). Compose kullanılırsa host Activity'nin ViewModel'e güvenli şekilde verilmesi gerekir (bellek sızıntısına dikkat).
- Resend için iOS'ta olmayan PhoneAuthProvider.ForceResendingToken saklanmalı; onCodeSent ile gelen token olmadan 'tekrar gönder' yeni SMS göndermez.
- Error code 39 (SMS anti-abuse) Android'de iOS'taki FIRAuthErrorUserInfoDeserializedResponseKey gibi yapılandırılmış gövdeyle GELMEZ; onVerificationFailed(FirebaseException) mesajında düz metin olarak ('Error code: 39' / 503) gelir. getMessage() parse edilmeli veya FirebaseTooManyRequestsException ayırt edilmeli.
- App Check sağlayıcı fabrikası FirebaseApp başlatıldıktan HEMEN sonra (Application.onCreate) kurulmalı; debug build'de DebugAppCheckProviderFactory + Logcat'teki debug token Console'a eklenmeli yoksa emülatörde Auth/Storage/Firestore/RTDB enforce reddeder.
- R8/ProGuard release'de Firebase/Functions/Play Integrity sınıflarını kırpabilir; minifyEnabled açılırsa keep kuralları gerekir.
- FirebaseAppDelegateProxyEnabled=NO ve APNs forwarding iOS'a özgüdür; Android'de karşılığı YOKTUR (app verification Play Integrity/reCAPTCHA ile otomatik). Bunun için kalem yazılmadı; sadece SMS auto-retrieval ele alınır.
- POST_NOTIFICATIONS (API 33+) bu alanın parçası değil ama SMS auto-retrieval için RECEIVE_SMS GEREKMEZ (Firebase play-services-auth SMS Retriever'ı imza-tabanlı kullanır, izinsiz).

### İş Kalemleri

#### `AUTH-00` — Modern Firebase/AppCheck/Functions bağımlılıkları + AndroidX/SDK taban yükseltmesi (bu alanın ön koşulu)  _(P0 · efor: L)_
- **iOS davranışı:** iOS SPM ile FirebaseAuth, FirebaseAppCheck (App Attest/DeviceCheck), FirebaseFunctions kullanır; App Check fabrikası FirebaseApp.configure()'dan önce kurulur.
- **Android mevcut:** firebase-auth:16.2.0 + firebase-core:16.0.8 (2018), App Check yok, Functions yok, android.support.* (AndroidX öncesi), AGP 3.4.2, Gradle 5.1.1, jcenter, compileSdk/targetSdk 28, minSdk 19.
- **Android teknoloji:** Firebase BoM (com.google.firebase:firebase-bom:33.x), firebase-auth, firebase-appcheck-playintegrity, firebase-appcheck-debug, firebase-functions, com.google.android.gms:play-services-auth (SMS auto-retrieval/reCAPTCHA), AndroidX (androidx.appcompat, lifecycle-viewmodel, activity-compose/compose-bom opsiyonel).
- **Adımlar:**
  - Proje build.gradle: jcenter() kaldır, mavenCentral() ekle; AGP'yi 8.x'e, google-services plugin'i 4.4.x'e, Gradle wrapper'ı 8.x'e yükselt.
  - gradle.properties: android.useAndroidX=true, android.enableJetifier=true ekle; eski com.android.support:* bağımlılıklarını AndroidX eşleniklerine taşı (appcompat, constraintlayout, recyclerview, material).
  - app/build.gradle: compileSdk 35, targetSdk 35, minSdk 21, multiDex (minSdk21 ile gereksizleşebilir) gözden geçir.
  - dependencies: platform('com.google.firebase:firebase-bom:33.x') + firebase-auth + firebase-appcheck-playintegrity + 'debugImplementation' firebase-appcheck-debug + firebase-functions + play-services-auth ekle; firebase-core'u kaldır (artık analytics ayrı).
  - Firebase Console'da bu uygulamaya debug keystore ve Play App Signing release SHA-1/SHA-256 parmak izlerinin ekli olduğunu doğrula (telefon auth ön koşulu).
  - R8/ProGuard: release'de minifyEnabled açılacaksa Firebase/Functions/PlayIntegrity için -keep kuralları ekle (consumer rules çoğunu kapsar, doğrula).
- **Gradle/bağımlılık:** firebase-bom:33.x, firebase-auth, firebase-appcheck-playintegrity, firebase-appcheck-debug (debug), firebase-functions, play-services-auth; AGP 8.x, Gradle 8.x, mavenCentral, AndroidX, compile/target 35, min 21.
- **Bağımlılık:** Ortak altyapı alanı (AndroidX/Gradle migrasyonu) ile koordineli; eğer ayrı bir migrasyon kalemi varsa bu kalem onunla birleştirilir.
- **Tuzaklar:** Play targetSdk 35 zorunlu; min 21 Play Integrity/BoM şartı; Jetifier eski Parse/pdf-viewer/picasso bağımlılıklarını da dönüştürmeli, çakışma çıkarsa ilgili kütüphaneler güncellenmeli. Bu kalem diğer tüm alanların da ortak altyapısıdır.
- **Kabul kriteri:** Uygulama yeni Firebase BoM, AppCheck ve Functions SDK'larıyla derlenir; mavenCentral'dan çözülür; targetSdk 35; debug build firebase-appcheck-debug içerir.

#### `AUTH-01` — PhoneNumber + VerificationCode yardımcıları (Türk cep no parse/format) Kotlin'e port  _(P0 · efor: M)_
- **iOS davranışı:** PhoneNumber.swift: countryCode='+90', nationalLength=10, '5' ile başlayan 10 ASCII rakam; init(input:) +90/0090/90/0 öneklerini ve ayıraçları temizler; e164, formatted ('+90 5XX XXX XX XX', 3-3-2-2), formattedInput (canlı biçimleme). VerificationCode.length=6, digits(from:) sadece rakam ilk 6.
- **Android mevcut:** Yok; mevcut ekran e-posta kabul ediyor.
- **Android teknoloji:** Saf Kotlin object/data class (Firebase'den bağımsız, birim test edilebilir); regex ^\+90[0-9]{10}$ kontrolü.
- **Adımlar:**
  - PhoneNumber data class: nationalNumber (10 hane, '5' ile başlar); fromNationalNumber, fromInput (digits filtrele, 0090/90/0 önek soy, ilk 10 hane), fromE164 ('+90' önekini soy) fabrikaları.
  - e164 ('+90'+national) ve formatted ('+90 '+grup(3,3,2,2)) hesap özellikleri.
  - formattedInput(input) ve formatNational(digits): kısmi girişi de gruplar (canlı biçimleme için).
  - VerificationCode: length=6, digitsFrom(input)=sadece rakam ilk 6.
  - Birim testler: '0532 123 45 67', '+905321234567', '905321234567', '5321234567' -> hepsi +905321234567; geçersizler null.
- **Gradle/bağımlılık:** Ek bağımlılık yok.
- **Tuzaklar:** isASCIIDigit mantığı: Türkçe/Arapça rakamları değil yalnızca 0-9 kabul edilmeli (iOS isASCII && isWholeNumber). Kotlin'de Char.isDigit() Unicode rakamlarını da kabul eder; c in '0'..'9' kullan.
- **Kabul kriteri:** Girdi normalleştirme ve biçimleme testleri iOS PhoneNumberTests ile aynı vakaları geçer; sadece +90 5xx kabul edilir.

#### `AUTH-02` — AuthService: Firebase Phone Auth (+90) sarmalayıcı (verify/signIn/reauth/signOut/delete/admin)  _(P0 · efor: L)_
- **iOS davranışı:** FirebaseAuthService: auth.languageCode='tr'; sendVerificationCode -> verifyPhoneNumber(uiDelegate:nil) verificationID döner; signIn -> PhoneAuthProvider.credential(withVerificationID:verificationCode:) + auth.signIn(with:); reauthenticate -> user.reauthenticate(with:); signOut -> auth.signOut(); deleteAccount -> user.delete() (requiresRecentLogin atabilir); isCurrentUserAdmin -> getIDTokenResult claims['admin']==true; userChanges -> addStateDidChangeListener.
- **Android mevcut:** LoginActivity doğrudan createUserWithEmailAndPassword/signInWithEmailAndPassword kullanıyor; telefon, admin claim, state listener yok.
- **Android teknoloji:** FirebaseAuth.getInstance(); firebaseAuth.setLanguageCode("tr"); PhoneAuthProvider.verifyPhoneNumber(PhoneAuthOptions.newBuilder(auth).setPhoneNumber(e164).setTimeout(60L, TimeUnit.SECONDS).setActivity(activity).setCallbacks(callbacks).build()); PhoneAuthProvider.getCredential(verificationId, code); auth.signInWithCredential(credential); user.reauthenticate(credential); user.delete(); user.getIdToken(false).result.claims; FirebaseAuth.AuthStateListener.
- **Adımlar:**
  - AuthRepository arayüzü + FirebaseAuthRepository impl oluştur (interface'i test için mock'la).
  - verifyPhoneNumber çağrısını OnVerificationStateChangedCallbacks ile sar: onCodeSent(verificationId, forceResendingToken) -> verificationId+token sakla; onVerificationCompleted(credential) -> OTOMATİK signInWithCredential (instant/auto-retrieval, iOS'ta yok); onVerificationFailed(FirebaseException) -> hata haritasına ver.
  - signInWithCredential ve reauthenticate'i Kotlin coroutine (await()) ile sarmala; sonuç AuthUser(uid, phoneNumber).
  - signOut() ve deleteAccount() (FirebaseAuthRecentLoginRequiredException -> requiresRecentLogin).
  - isCurrentUserAdmin(): user.getIdToken(false).await().claims['admin']==true, hata/çıkışta false.
  - authStateFlow(): callbackFlow içinde AuthStateListener ekle/çıkar (SessionStore için).
  - Debug/test: BuildConfig.DEBUG && test bayrağı ise firebaseAuth.getFirebaseAuthSettings().setAppVerificationDisabledForTesting(true) (Console kurgusal test numaraları ile).
- **Gradle/bağımlılık:** firebase-auth (AUTH-00).
- **Bağımlılık:** AUTH-00, AUTH-01
- **Tuzaklar:** setActivity zorunlu (reCAPTCHA fallback); ForceResendingToken'ı resend için sakla; onVerificationCompleted ana iş parçacığında gelebilir ve kullanıcı henüz kod girmemişken oturum açar -> UI buna hazır olmalı. getIdToken forceRefresh=false (iOS ile aynı; claim oturum içi verildiyse bir kez true refresh gerekebilir - admin alanı notu).
- **Kabul kriteri:** +90 numarasıyla SMS alınır, 6 haneli kodla signInWithCredential başarılı; admin claim'li kullanıcıda isCurrentUserAdmin true; signOut/deleteAccount çalışır; auto-retrieval'de kod girilmeden oturum açılır.

#### `AUTH-03` — AuthError eşlemesi + Error code 39 (SMS anti-abuse) tespiti ve Türkçe mesajlar  _(P0 · efor: M)_
- **iOS davranışı:** AuthError enum + authError(from:NSError): invalidPhoneNumber, invalidCode, codeExpired, tooManyRequests, smsTemporarilyUnavailable(Error 39), offline, verificationCancelled, appVerificationFailed, requiresRecentLogin, notSignedIn, accountDisabled, unknown. analyticsReason stabil düşük-kardinalite string. Error 39: internalError(17999) içinde FIRAuthErrorUserInfoDeserializedResponseKey gövdesinden NSUnderlyingError zinciri (cap 8) gezilerek 'Error code: 39' ayrıştırılır; backendCode=39.
- **Android mevcut:** e.getLocalizedMessage() Toast; sınıflandırma yok; Error 39 açıklaması yok.
- **Android teknoloji:** FirebaseAuthException.getErrorCode() string'leri (ERROR_INVALID_VERIFICATION_CODE, ERROR_SESSION_EXPIRED, ERROR_INVALID_PHONE_NUMBER, ERROR_INVALID_VERIFICATION_ID, ERROR_MISSING_VERIFICATION_CODE, ERROR_USER_DISABLED, ERROR_REQUIRES_RECENT_LOGIN, ERROR_USER_TOKEN_EXPIRED...), FirebaseTooManyRequestsException, FirebaseNetworkException, e.getMessage() metin ayrıştırma.
- **Adımlar:**
  - AuthError sealed class (veya enum) + Türkçe mesajlar: iOS Localizable metinlerini birebir kopyala (ör. 'SMS servisinde geçici bir sorun...bizimle iletişime geç.').
  - mapException(Throwable): FirebaseTooManyRequestsException -> tooManyRequests; FirebaseNetworkException -> offline; FirebaseAuthException errorCode'a göre eşle.
  - Error 39 tespiti: onVerificationFailed'daki FirebaseException/FirebaseAuthException mesajında 'error code: 39' (case-insensitive) veya 503/anti-abuse imzası aranır -> smsTemporarilyUnavailable, backendCode=39. (Android yapılandırılmış gövde vermez; getMessage parse + FirebaseTooManyRequests ayrımı.)
  - analyticsReason map: invalid_phone_number, invalid_code, code_expired, too_many_requests, sms_temporarily_unavailable, offline, verification_cancelled, app_verification_failed, requires_recent_login, not_signed_in, account_disabled, unknown (iOS ile birebir aynı string'ler).
  - backendCode(for error): smsTemporarilyUnavailable -> 39, diğer -> null.
  - Birim test: sahte FirebaseAuthException'lar doğru AuthError'a eşlenir; 'Error code: 39' içeren mesaj smsTemporarilyUnavailable.
- **Gradle/bağımlılık:** firebase-auth.
- **Bağımlılık:** AUTH-02
- **Tuzaklar:** Android error kodları iOS NSError kodlarından farklı STRING'ler; bire bir sayısal eşleme yapma. Error 39 metni Firebase sürümüne göre değişebilir; hem FirebaseTooManyRequestsException hem 'error code: 39' metnini yakala, ikisi de sms_temporarily_unavailable göstermeli. analyticsReason string'leri diagnostics/lookup scripti ile uyum için DEĞİŞTİRİLMEMELİ.
- **Kabul kriteri:** Her Firebase hata tipi doğru Türkçe mesaja ve stabil analyticsReason'a eşlenir; Error 39 kullanıcıya 'geçici SMS sorunu, iletişime geç' olarak gösterilir ve backendCode=39 raporlanır.

#### `AUTH-04` — LoginViewModel: iki adım (telefon/kod), 60 sn resend cooldown, oto-doğrulama, durum yönetimi  _(P0 · efor: L)_
- **iOS davranışı:** LoginViewModel: step(phone/code), resendCooldown=60, phoneNumber geçerli -> canSendCode; code 6 hane && verificationID!=null -> canVerify; updateCode 6. hanede oto verifyCode; sendCode aynı numarada cooldown içinde yeni SMS yerine kod ekranını yeniden gösterir; resendCode cooldown bitince; changeNumber phone'a döner, lastSentCode korunur; fail() errorMessage + LoginFailureInfo + analytics + diagnostics.report; invalidCode->kod temizle, codeExpired->kod+resend+lastSentCode sıfırla; resendCooldownRemaining sayaç.
- **Android mevcut:** ViewModel yok; tüm mantık Activity'de ve e-posta tabanlı.
- **Android teknoloji:** androidx.lifecycle.ViewModel + StateFlow<LoginUiState> (veya Compose State); viewModelScope coroutine; System clock enjeksiyonu (test için); PhoneAuthProvider.ForceResendingToken saklama.
- **Adımlar:**
  - LoginUiState: step(Phone/Code(phoneNumber)), phoneText, codeText, isWorking, errorMessage, resendAvailableAt.
  - updatePhoneText -> PhoneNumber.formattedInput; updateCode -> VerificationCode.digitsFrom, 6. hanede verifyCode() tetikle.
  - sendCode(): numara geçerli + (lastSentCode aynı numara && now<resendAvailableAt) ise yeni SMS göndermeden code adımını göster; değilse requestCode(isResend=false).
  - resendCode(): sadece code adımında, cooldown 0 iken, saklanan ForceResendingToken ile requestCode(isResend=true).
  - onCodeSent geldiğinde resendAvailableAt=now+60sn, step=Code; verifyCode() signInWithCredential.
  - changeNumber(): step=Phone, verificationId=null, codeText='', lastSentCode korunur (cooldown bypass engeli).
  - fail(error, stage): isWorking=false, errorMessage, LoginFailureInfo üret, analytics.loginFailed, diagnostics.report; invalidCode->codeText=''; codeExpired->codeText='' + resendAvailableAt=null + lastSentCode=null.
  - resendCooldownRemaining(now): max(0, ceil(resendAvailableAt-now)) saniye; UI'da 1 sn'de bir güncellenen sayaç (Compose: LaunchedEffect/ticker, View: CountDownTimer).
- **Gradle/bağımlılık:** androidx.lifecycle-viewmodel-ktx.
- **Bağımlılık:** AUTH-01, AUTH-02, AUTH-03, AUTH-07, DIAG-01
- **Tuzaklar:** onVerificationCompleted (auto-retrieval) code adımına geçmeden gelebilir; ViewModel bu durumda doğrudan signIn yapmalı ve isWorking yönetmeli. Activity yeniden yaratılırsa (rotasyon) verificationId/forceResendingToken ViewModel'de yaşadığı için korunur (SavedStateHandle gerekmez ama Activity referansı yeniden bağlanmalı). Cooldown saati test için enjekte edilmeli.
- **Kabul kriteri:** Numara->Kod Gönder->60 sn sayaç; aynı numarada tekrar Kod Gönder SMS göndermez; 6. hanede otomatik doğrulama; Numarayı değiştir cooldown'ı atlatmaz; hatalarda kod alanı iOS kurallarına göre temizlenir.

#### `AUTH-05` — Telefon giriş ekranı UI (phone adımı): +90 önek, numara alanı, Kod Gönder, SMS ücret notu  _(P0 · efor: M)_
- **iOS davranışı:** LoginView phoneStep: logo+başlık ('Giriş Yap' / açıklama), '+90' sabit önek + divider + PhoneNumberTextField (numberPad, telephoneNumber contentType, canlı 5XX XXX XX XX biçimleme, caret korumalı, backspace ayıraç üstünde rakamı siler), 'Kod Gönder' (canSendCode disable), SMS ücret notu, hata satırı (kırmızı, exclamationmark ikon).
- **Android mevcut:** activity_login.xml: iki EditText (e-mail/password) + görünmez SIGN UP + SIGN IN; logo/başlık/biçimleme yok; inputType yanlış.
- **Android teknoloji:** Jetpack Compose (önerilir: OutlinedTextField + VisualTransformation ile canlı biçimleme, KeyboardType.Phone) VEYA View+TextWatcher. Türkçe string'ler strings.xml.
- **Adımlar:**
  - Compose LoginScreen phone bölümü: logo (Coil/Glide değil; yerel drawable), '+90' Text önek + dikey ayıraç + telefon alanı.
  - Telefon alanı: KeyboardType.Phone; her değişimde PhoneNumber.formattedInput ile biçimle; caret'i son rakamdan sonra tut (Compose'da TextFieldValue.selection yönetimi veya VisualTransformation kullan).
  - 'Kod Gönder' primary buton: isWorking'de spinner, !canSendCode'da disable; tıklayınca klavye kapat + viewModel.sendCode().
  - SMS ücret notu Text: 'Numarana tek kullanımlık bir doğrulama kodu SMS ile gönderilir. Standart SMS ücretleri uygulanabilir.'
  - Hata satırı: errorMessage varsa kırmızı + uyarı ikonu; erişilebilirlik için contentDescription.
  - Mevcut AdMob banner/MobileAds bu ekranda KULLANILMAZ (parite: reklam yok).
- **Gradle/bağımlılık:** compose-bom (AUTH-00) veya mevcut View sistemi; material3.
- **Bağımlılık:** AUTH-01, AUTH-04
- **Tuzaklar:** Compose'da canlı telefon biçimlemede caret kayması klasik tuzak (iOS PhoneNumberEdit bunu özel çözüyor); VisualTransformation + offset mapping ile veya her zaman sona yaz. Autofill telephone ipucu verilebilir. RTL supportsRtl açık olsa da Türkçe LTR; '+90' önek LTR kalmalı.
- **Kabul kriteri:** Kullanıcı numarayı yazarken 5XX XXX XX XX biçimlenir, yapıştırılan +90/0 numaralar normalleşir; geçersiz numarada Kod Gönder pasif; reklam görünmez.

#### `AUTH-06` — SMS kod giriş ekranı UI (code adımı): 6 haneli OTP alanı, oto-doldurma, Doğrula, tekrar gönder sayacı, Numarayı değiştir  _(P0 · efor: M)_
- **iOS davranışı:** LoginView codeStep: başlık ('Kodu Gir' / '<numara>...6 haneli kod'), tek 6 haneli alan (numberPad, textContentType oneTimeCode=SMS autofill, kerning 8, ortalı), 'Doğrula' (canVerify disable), 'Kodu tekrar gönder (N sn)' sayaçlı (cooldown bitince aktif), 'Numarayı değiştir'. 6. hanede oto submit; doğrulanırken alan disable, hatadan sonra tekrar odak.
- **Android mevcut:** Yok.
- **Android teknoloji:** Compose OTP alanı veya tek EditText (inputType=number, maxLength 6, autofillHints=SmsOtpCode API 26+ / Google SMS Retriever otomatik). play-services-auth SMS User Consent/Retriever onVerificationCompleted ile zaten otomatik kodu getirebilir.
- **Adımlar:**
  - Kod bölümü: başlıkta hedef numara formatted gösterilir.
  - OTP alanı: 6 rakam, KeyboardType.NumberPassword/Number; autofillHints = AUTOFILL_HINT_SMS_OTP (API 26+); değişimde VerificationCode.digitsFrom, 6. hanede viewModel.verifyCode().
  - 'Doğrula' butonu: isWorking spinner, !canVerify disable.
  - 'Kodu tekrar gönder' butonu: resendCooldownRemaining>0 iken '(N sn)' metni ve disable; sayaç 1 sn'de bir güncellenir; 0'da aktif -> viewModel.resendCode().
  - 'Numarayı değiştir' -> viewModel.changeNumber(), odak numara alanına.
  - Auto-retrieval: onVerificationCompleted ile gelen kod varsa alanı doldur/otomatik doğrula (kullanıcı kod girmeden geçiş).
- **Gradle/bağımlılık:** play-services-auth (SMS auto-retrieval), compose/material3.
- **Bağımlılık:** AUTH-01, AUTH-04
- **Tuzaklar:** Firebase phone auth zaten SMS auto-retrieval'ı imza-tabanlı yapar; RECEIVE_SMS izni GEREKMEZ. oneTimeCode autofill Android'de SMS Retriever ile çakışmamalı. Sayaç TimelineView karşılığı: Compose'da LaunchedEffect+delay(1000) döngüsü veya rememberCoroutineScope ticker.
- **Kabul kriteri:** SMS gelince kod otomatik dolabilir; 6. hanede otomatik doğrulama; tekrar gönder 60 sn boyunca sayar; Numarayı değiştir telefon adımına döner.

#### `AUTH-07` — Giriş ekranında 'Bize Ulaş' (mailto) + ContactSupport yardımcısı  _(P1 · efor: S)_
- **iOS davranışı:** LoginView'de 'Giriş yapamıyor musun? Bize Ulaş' butonu; ContactSupport.mailtoURL(context: viewModel.supportContext) açar; context son hata detayı (stage/reason/backendCode) veya numara; mail uygulaması yoksa 'Adresi Kopyala' alert. email=buyukeryazilim@gmail.com, subject='ViFi — Bize Ulaş', body'de bağlam + '—' + 'ViFi <sürüm> · iOS <os>'.
- **Android mevcut:** Yok.
- **Android teknoloji:** Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")) + putExtra(EXTRA_EMAIL/SUBJECT/TEXT); packageManager.resolveActivity ile mail client kontrolü; yoksa ClipboardManager ile adresi kopyala + Toast/Snackbar.
- **Adımlar:**
  - ContactSupport object: email, subject sabitleri; buildBody(context): bağlam satırı + '—' + 'ViFi <versionName> (<versionCode>) · Android <Build.VERSION.RELEASE>'.
  - openSupportEmail(activity, context): ACTION_SENDTO mailto intent oluştur (subject+body extra), resolveActivity null değilse startActivity.
  - Mail client yoksa: adresi panoya kopyala + 'Mail uygulaması bulunamadı, <adres> adresine yazabilirsin' Snackbar (iOS fallback alert paritesi).
  - supportContext ViewModel'den: son LoginFailureInfo.supportContext (Giriş sorunu bildirimi / Numara / Aşama / Sebep(+Error code)) yoksa 'Giriş sorunu bildirimi\nNumara: <e164>'.
  - Butonu phone adımına yerleştir; metin 'Giriş yapamıyor musun? Bize Ulaş'.
- **Gradle/bağımlılık:** Ek bağımlılık yok (About ekranı ve exam viewer'larda da yeniden kullanılacak - ortak util).
- **Bağımlılık:** DIAG-01
- **Tuzaklar:** Android 11+ paket görünürlüğü: mailto resolve için <queries> veya ACTION_SENDTO yeterli (mailto intent görünürlük istisnası). supportContext kişisel veri (tam numara) içerir ama kullanıcı maili kendisi gönderdiği için iOS ile aynı mantık - sorun değil. versionCode PackageInfo.longVersionCode (API 28+).
- **Kabul kriteri:** Butona basınca konu/gövdesi dolu mail taslağı açılır; son hata detayları gövdede; mail yoksa adres kopyalanır.

#### `AUTH-08` — SessionStore + oturum kapısı (unknown/signedOut/signedIn) ve uygulama genel gate'i  _(P0 · efor: L)_
- **iOS davranışı:** SessionStore: state unknown/signedOut/signedIn(AuthUser); observeUserChanges() auth.userChanges() döngüsü. RootView gate: unknown->SessionLoadingView, signedOut->LoginView, signedIn->ProfileGate->arşiv. signedOut'a geçişte router.popToRoot + profile/wallet/unlocks/recents/announcements/analytics reset.
- **Android mevcut:** HiçBİR oturum kapısı yok; HomeActivity doğrudan açılıyor, RTDB anonim okunuyor. LoginActivity sadece admin e-posta akışı. Ortak Firestore kuralları artık read için +90|admin istediğinden bu HALİYLE tüm okumalar reddolur.
- **Android teknoloji:** FirebaseAuth.AuthStateListener -> StateFlow<SessionState>; tek giriş Activity (Splash/RootActivity) veya Navigation Compose ile başlangıç yönlendirmesi; navigate(Login) vs navigate(Home).
- **Adımlar:**
  - SessionState sealed: Unknown, SignedOut, SignedIn(AuthUser).
  - SessionRepository: AuthStateListener'ı dinle, başlangıçta currentUser varsa SignedIn aksi halde Unknown->SignedOut.
  - Uygulama başlangıcı: RootActivity/NavHost; Unknown'da splash/loading, SignedOut'ta LoginScreen, SignedIn'de Home.
  - HomeActivity/MainActivity giriş noktaları: currentUser null ise LoginScreen'e yönlendir (iOS gate paritesi).
  - signOut akışı: auth.signOut() -> SignedOut -> Login'e dön + kullanıcıya özel durumları (recents, profil cache, cüzdan) temizle (ilgili alanların reset'leri; bu alan yalnızca sinyali yayar).
  - Navigasyon geri yığınını temizle (iOS popToRoot karşılığı: Intent FLAG_ACTIVITY_CLEAR_TASK veya NavController popUpTo(0)).
- **Gradle/bağımlılık:** firebase-auth; navigation-compose (Compose seçilirse).
- **Bağımlılık:** AUTH-02, PROFILE-GATE (profil alanı)
- **Tuzaklar:** Bu, Android'in en büyük davranış değişikliği: tüm uygulamayı auth arkasına almak. Mevcut v2.5 kullanıcıları güncelleyince ilk kez telefon doğrulaması isteyecek. Profil kapısı (users/<uid>/profile) iOS'ta ikinci kapı; bu alanın değil ama SignedIn tetikleyicisi burada. AuthStateListener onVerificationCompleted auto sign-in'i de yakalar.
- **Kabul kriteri:** Uygulama açılışında oturum yoksa giriş ekranı zorunlu gelir; başarılı girişte Home açılır ve Firestore okumaları +90 kuralından geçer; signOut girişe döner ve kullanıcı durumu temizlenir.

#### `AUTH-09` — Eski e-posta/şifre kimlik doğrulamanın tamamen kaldırılması  _(P0 · efor: S)_
- **iOS davranışı:** iOS'ta hiç e-posta/şifre auth yok; yalnızca telefon.
- **Android mevcut:** LoginActivity.signUp/signIn createUserWithEmailAndPassword & signInWithEmailAndPassword; activity_login.xml iki EditText + SIGN UP/SIGN IN; signIn başarıda AddLessonActivity'ye gidiyor.
- **Android teknoloji:** Kod ve layout silme; Firebase Console'da Email/Password sağlayıcısını devre dışı bırakma (opsiyonel, admin kararı).
- **Adımlar:**
  - LoginActivity.java içindeki createUserWithEmailAndPassword/signInWithEmailAndPassword ve ilgili onClick (signUp/signIn) metotlarını kaldır; ekranı yeni telefon auth ekranıyla değiştir.
  - activity_login.xml'deki emailText/passwordText/button4/button5'i kaldır (yeni Compose/layout ile değiştir).
  - AddLessonActivity'ye e-posta login üzerinden geçişi kaldır; admin akışı artık telefon auth + admin claim (isCurrentUserAdmin) üzerinden gate'lenir.
  - Firebase Console: Email/Password provider'ın kapatılmasını admin ile netleştir (artık kullanılmıyor).
  - Kodda kalan referansları (R.id.emailText vb.) temizle; derleme hatası bırakma.
- **Gradle/bağımlılık:** Yok.
- **Bağımlılık:** AUTH-08
- **Tuzaklar:** AddLessonActivity ve admin ekranları artık admin claim'e göre açılmalı; e-posta login kaldırılınca admin erişimi telefon+claim'e bağlanır. Eski kullanıcıların e-posta hesapları Firestore kurallarında zaten +90 istediği için işlevsiz; migrasyon gereği yok.
- **Kabul kriteri:** Projede e-posta/şifre auth çağrısı kalmaz; derleme temiz; admin yalnızca telefon + admin claim ile moderasyon ekranlarına ulaşır.

#### `APPCHECK-01` — Firebase App Check (Play Integrity + debug provider) kurulumu ve enforce stratejisi  _(P0 · efor: M)_
- **iOS davranışı:** ViFiAppCheckProviderFactory: DEBUG/simulator -> AppCheckDebugProvider (token konsola basılır, Console'a eklenir); release -> App Attest/DeviceCheck. AppCheck.setAppCheckProviderFactory(...) FirebaseApp.configure()'dan ÖNCE. Storage/RTDB/Auth enforce, Firestore monitor->enforce. İstek yetkilendiricisi X-Firebase-AppCheck başlığını ekler, asla force refresh yapmaz (kota).
- **Android mevcut:** App Check YOK; hiçbir provider kurulu değil. Enforce açılırsa mevcut app tüm backend'den reddedilir.
- **Android teknoloji:** FirebaseAppCheck.getInstance(); installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance()) release; DebugAppCheckProviderFactory.getInstance() debug; Application.onCreate içinde FirebaseApp.initializeApp(this) SONRASI kur.
- **Adımlar:**
  - Application sınıfı oluştur (AndroidManifest application android:name) ve onCreate'te FirebaseApp.initializeApp(this) sonrası FirebaseAppCheck.getInstance().installAppCheckProviderFactory(...) çağır.
  - BuildConfig.DEBUG ise DebugAppCheckProviderFactory, değilse PlayIntegrityAppCheckProviderFactory kur.
  - Debug token: ilk çalıştırmada Logcat'teki App Check debug token'ı Firebase Console > App Check > Apps > bu uygulama > Manage debug tokens'a ekle (her geliştirici/CI cihazı için).
  - Google Play Console'da Play Integrity API'nin bu uygulama için etkin ve Firebase ile bağlı olduğunu doğrula.
  - Console App Check: başlangıçta Auth, Storage, Realtime Database, Cloud Firestore için 'monitor' (unenforced) aç; yeterli Android trafiği oluşunca enforce'a geç (erken enforce v2.5'i kilitler).
  - İndirmelerde App Check token'ının SDK tarafından otomatik eklendiğini doğrula (iOS'taki manuel X-Firebase-AppCheck başlığına Android'de gerek yok; SDK halleder).
- **Gradle/bağımlılık:** firebase-appcheck-playintegrity, debugImplementation firebase-appcheck-debug (AUTH-00).
- **Bağımlılık:** AUTH-00
- **Tuzaklar:** Provider FirebaseApp init'ten SONRA ama ilk backend çağrısından ÖNCE kurulmalı (Application.onCreate ideal). Debug provider token'ı eklenmezse emülatörde Auth phone/Storage/Firestore enforce hata verir. Enforce'u Console'dan erken açmak canlı kullanıcıyı keser - monitor ile başla. Play Integrity rate limiting (kota) var; forceRefresh gereksiz yapılmamalı (iOS notu ile aynı).
- **Kabul kriteri:** Release build Play Integrity ile geçerli App Check token üretir; debug build debug token ile çalışır; phone auth App Check korumalı akışta başarılı; Console'da monitor metrikleri görünür.

#### `DIAG-01` — Login Diagnostics raporlama: recordLoginDiagnostic callable + App Check durum probu + 60 sn throttle  _(P1 · efor: M)_
- **iOS davranışı:** FirebaseLoginDiagnosticsService: Functions(region 'europe-west1').httpsCallable('recordLoginDiagnostic'); App Check probu appCheck.token(forcingRefresh:false) -> 'valid'/'error'/'unavailable' (appCheckStatus); payload {reason, step, phoneNumber(e164), appCheckStatus, appVersion '<name> (<build>)', osVersion, deviceModel(hw id), occurredAt ISO8601, backendCode?}; LoginDiagnosticThrottle 60 sn signature 'phone|reason|stage' dedup; fire-and-forget, hataları yut, kullanıcıya hiç gösterme.
- **Android mevcut:** Yok; Functions SDK bile yok.
- **Android teknoloji:** FirebaseFunctions.getInstance("europe-west1").getHttpsCallable("recordLoginDiagnostic").call(map); FirebaseAppCheck.getInstance().getAppCheckToken(false) (durum probu); Build.MODEL/Build.MANUFACTURER, Build.VERSION.RELEASE, BuildConfig.VERSION_NAME/VERSION_CODE.
- **Adımlar:**
  - LoginDiagnosticsReporter arayüzü + FirebaseLoginDiagnosticsReporter + NoopReporter (test/mock).
  - report(info): signature 'phone|reason|stage'; 60 sn throttle içinde aynıysa atla.
  - App Check probu: getAppCheckToken(false) başarılı -> 'valid', hata -> 'error', AppCheck kurulu değilse 'unavailable'.
  - Payload map (alan adları SUNUCU ile birebir): reason, step('send_code'|'verify'), phoneNumber(e164), appCheckStatus, backendCode(varsa), appVersion ('<VERSION_NAME> (<VERSION_CODE>)'), osVersion (Build.VERSION.RELEASE), deviceModel (Build.MANUFACTURER+' '+Build.MODEL veya Build.DEVICE), occurredAt (ISO8601, Instant/SimpleDateFormat UTC).
  - getHttpsCallable('recordLoginDiagnostic').call(payload) - viewModelScope'tan bağımsız (uygulama scope) fire-and-forget; onFailure sadece logla, UI'a yansıtma.
  - LoginViewModel.fail() içinden report(LoginFailureInfo) çağır (AUTH-04).
- **Gradle/bağımlılık:** firebase-functions (AUTH-00).
- **Bağımlılık:** AUTH-00, APPCHECK-01, AUTH-03
- **Tuzaklar:** Function enforceAppCheck=FALSE olduğu için App Check mint edilemese bile çağrı gider ve sunucu appCheckServer'ı ham header'dan hesaplar - Android SDK token'ı otomatik ekler, ayrıca manuel header EKLEME. occurredAt sunucuda clampString(40) ve string saklanır; ISO8601 UTC gönder. deviceModel iOS 'iPhone16,2' gibi hw id; Android karşılığı Build.MODEL/Build.DEVICE. Çağrı asla login akışını bloklamamalı veya hata göstermemeli.
- **Kabul kriteri:** Başarısız giriş sonrası loginDiagnostics koleksiyonuna doğru alanlarla kayıt düşer (numberHash+last4 sunucuda), aynı hata 60 sn içinde tekrar raporlanmaz, kullanıcı hiçbir şey görmez; lookup-login-diagnostics.mjs Android kaydını listeler.

#### `DIAG-02` — LoginFailureInfo / LoginFailureStage / throttle modellerinin portu  _(P1 · efor: S)_
- **iOS davranışı:** LoginFailureStage: send_code|verify (+ turkishLabel 'Kod gönderme'/'Kod doğrulama'). LoginFailureInfo: reason, backendCode?, stage, phoneNumber(e164)?, occurredAt; supportContext ('Giriş sorunu bildirimi' / Numara / Aşama / Sebep(+Error code)). LoginDiagnosticThrottle: 60 sn pencere, signature farklıysa veya pencere dolduysa gönder.
- **Android mevcut:** Yok.
- **Android teknoloji:** Saf Kotlin data class/enum (Firebase'den bağımsız, birim test edilebilir).
- **Adımlar:**
  - LoginFailureStage enum: SEND_CODE('send_code'), VERIFY('verify') + turkishLabel.
  - LoginFailureInfo data class: reason, backendCode, stage, phoneNumber, occurredAt; supportContext üretimi (iOS metin formatı birebir).
  - LoginDiagnosticThrottle: window=60sn, lastSignature/lastSentAt; shouldSend(signature, now) saf fonksiyon.
  - Birim test: aynı signature 60 sn içinde false, farklı signature veya 60 sn sonra true (iOS LoginDiagnosticThrottle testleri).
- **Gradle/bağımlılık:** Yok.
- **Tuzaklar:** raw string değerleri ('send_code','verify') sunucu ve analytics ile eşleşmek için DEĞİŞTİRİLMEMELİ. occurredAt zaman kaynağı test için enjekte edilebilir olmalı.
- **Kabul kriteri:** Throttle ve supportContext birim testleri iOS davranışıyla aynı; stage raw değerleri sunucu sözleşmesiyle uyumlu.

#### `AUTH-10` — Login analytics olayları (code requested / succeeded / failed) + screen view  _(P2 · efor: S)_
- **iOS davranışı:** LoginView onAppear screenView('Login'); requestCode -> loginCodeRequested(isResend); verifyCode başarı -> loginSucceeded; fail -> loginFailed(reason, step). (Genişletilmiş analytics ayrı alan ama bu login olayları burada tetiklenir.)
- **Android mevcut:** Login analytics yok (firebase-core 16.0.8 var ama kullanılmıyor).
- **Android teknoloji:** FirebaseAnalytics (firebase-analytics) logEvent; ortak AnalyticsTracker arayüzü (analytics alanı tanımlar).
- **Adımlar:**
  - AnalyticsTracker arayüzünde login olayları: screenView(name), loginCodeRequested(isResend), loginSucceeded, loginFailed(reason, step).
  - LoginScreen görünür olunca screenView('Login').
  - requestCode (sendCode/resendCode) -> loginCodeRequested(isResend).
  - signIn başarı -> loginSucceeded; fail() -> loginFailed(reason=analyticsReason, step=stage.raw).
  - Olay/param adlarını iOS ile aynı tut (düşük kardinalite reason).
- **Gradle/bağımlılık:** firebase-analytics (AUTH-00 BoM).
- **Bağımlılık:** AUTH-04, Genişletilmiş Analytics alanı
- **Tuzaklar:** Asıl AnalyticsTracker implementasyonu genişletilmiş analytics alanında; bu kalem yalnızca login tetikleyicilerini bağlar. reason string'leri analyticsReason ile birebir (DIAG uyumu).
- **Kabul kriteri:** Login ekranı görünümü, kod istekleri, başarı ve başarısızlıklar analytics'e düşer; reason/step değerleri diagnostics ile tutarlı.

#### `AUTH-11` — Test numaraları + instrumentation için app verification devre dışı yapılandırması  _(P2 · efor: S)_
- **iOS davranışı:** DEBUG + -ViFiPhoneAuthTesting -> auth.settings.isAppVerificationDisabledForTesting=true; Console'daki kurgusal test numaralarıyla APNs/reCAPTCHA atlanır.
- **Android mevcut:** Yok.
- **Android teknoloji:** firebaseAuth.getFirebaseAuthSettings().setAppVerificationDisabledForTesting(true); Firebase Console > Authentication > Phone > fictional test phone numbers.
- **Adımlar:**
  - Debug/instrumentation build'de (BuildConfig.DEBUG + bir flag/BuildConfig alanı) setAppVerificationDisabledForTesting(true) çağır.
  - Firebase Console'da kurgusal test numaraları + sabit kodları tanımla (ör. +90 500 000 00 00 / 123456).
  - Espresso/UI testlerinde bu numara+kod ile uçtan uca giriş akışını test et (gerçek SMS göndermeden).
  - Release build'de bu ayarın ASLA açılmadığından emin ol (yalnızca DEBUG).
- **Gradle/bağımlılık:** androidx.test (instrumentation), firebase-auth.
- **Bağımlılık:** AUTH-02, APPCHECK-01
- **Tuzaklar:** setAppVerificationDisabledForTesting yalnızca Console'da tanımlı kurgusal numaralarla çalışır; gerçek numarada çalışmaz. Release'e sızmamalı (ProGuard değil, build type guard). Play Integrity emülatörde çalışmayabilir -> test için bu bayrak + debug App Check token gerekir.
- **Kabul kriteri:** Debug'da kurgusal numarayla SMS göndermeden giriş testi geçer; release'de bayrak kapalı.


---

## Profil (zorunlu tamamlama kapısı + düzenleme + katalog)  ·  `profile`

**Özet:** iOS'ta (v3.1.0) Profil alani su parcalardan olusur: (1) giristen sonra ZORUNLU "Profilini Tamamla" kapisi, (2) Hesap ekranindan "Profili Duzenle", (3) ad/soyad/universite/fakulte/bolum/cinsiyet alanli ortak form + salt-okunur telefon, (4) listeden-sec-veya-serbest-metin yazan, Turkce-duyarli aramali CatalogPicker, (5) gomulu Turkiye universite/fakulte/bolum JSON listeleri (iOS'ta 3 dosya: 414 universite, ~102 fakulte, ~394 bolum), (6) kanonik slug id uretimi (Turkce harf eslemeli, [a-z0-9-]) ve *IsCustom bayragi, (7) RTDB users/<uid>/profile'a TAM 14 anahtarli yazma (13 alan + updatedAt=server now), (8) gate durum makinesi (loading/absent/present/failed) ve giris/cikis entegrasyonu, (9) profilden tureyen analytics user-property segmentleri. Android'de karsiligi SIFIR: mevcut uygulama LoginActivity -> HomeActivity akisiyla dogrudan arsive gider, profil kavrami yoktur. Backend (RTDB kurallari) profil semasini zaten dogruluyor ve platformdan bagimsiz; Android bu sozlesmeye BIREBIR uymali, yeni backend yazmamali. Asagidaki 20 is kalemi iOS'taki her davranisi ve kenar durumunu Android'e tasir. Onerilen yigin: Jetpack Compose + Navigation Compose + ViewModel + StateFlow + Hilt + Firebase RTDB (dogru europe-west instance) + App Check (Play Integrity) + assets JSON + kotlinx.serialization. Reklam (AdMob) parite icin kaldirilir; abonelik kapsam disidir.

**Android mevcut durum:** Android v2.5 tamamen legacy: salt Java, Activity/Fragment, com.android.support (AndroidX oncesi), AGP 3.4.2, olu jcenter, Firebase SDK 2016-2018 (database 16.1.0, auth 16.2.0), Parse SDK, AdMob. Launcher HomeActivity; MainActivity/LoginActivity ile giris sonrasi DOGRUDAN HomeActivity acilir. Profil kavrami, zorunlu tamamlama kapisi, profil duzenleme, katalog secici, slug/id uretimi, users/<uid>/profile okuma/yazma, cinsiyet alani, analytics user-property segmentleri HICBIRI YOK. Universite/fakulte/bolum verisi su an RTDB 'Universitiess' eski agacindan fragment'larda okunuyor (arsiv alani; profil pickerlari BUNU KULLANMAZ, gomulu JSON kullanir). targetSdk 28, minSdk 19, multiDex acik.

**Dokunulan backend sözleşmesi:** Profil alani YENI backend GEREKTIRMEZ. users/<uid>/profile RTDB dugumu ve .validate kurallari (14 anahtar; *Id regex ^[a-z0-9-]+$ max 140; name/surname max 60, university/faculty/department max 120, yasak karakter [.$#[]/], updatedAt===now, phoneNumber===auth.token.phone_number) iOS tarafindan yazilmis ve PLATFORMDAN BAGIMSIZ. Android yalnizca bu sozlesmeye birebir uyan istemci yazar. Android-ozgu tek backend boslugu bu alanin DISINDA (config/appUpdate/android siblingi -> Update alani). App Check enforce RTDB icin acik oldugundan Android'in Play Integrity saglayicisi kurulmadan hicbir profil okuma/yazma gecmez (altyapi kalemi, kural degisikligi degil).

**Android'e özgü riskler:**
- res/raw tire ve buyuk harf KABUL ETMEZ: 'turkish-universities.json' res/raw'da gecersiz. JSON'lari assets/ altina koy (context.assets.open) veya dosyalari turkish_universities.json'a yeniden adlandir. assets/ onerilir (iOS dosya adlariyla birebir kalir).
- Turkce locale kucuk harf tuzagi: Locale.getDefault() yerine Locale('tr','TR') kullanilmali; aksi halde 'I'->'i' (noktali) olur ve arama iOS ile uyusmaz. Slug ve arama-normalizasyonu AYRI fonksiyonlar: Slug ı->i esler (kanonik id), arama-normalizasyonu tr-lowercase + Normalizer.NFD + combining-mark strip yapar ama ı->i ESLEMEZ (iOS folding davranisini aynen tutmak icin). Her ikisi de birim testiyle iOS beklentilerine kilitlenmeli.
- RTDB bolge/URL: profil (default) DEGIL, europe-west RTDB instance'inda. FirebaseDatabase.getInstance(app, '<databaseURL>') dogru instance URL'i ile cagrilmali (orn. https://vifi-831a8-default-rtdb.europe-west1.firebasedatabase.app veya isimli instance). Yanlis/varsayilan us-central URL sessizce bos okur. 'Updated Fire Base url' commit'indeki mevcut URL dogrulanmali.
- App Check enforce RTDB/Auth icin ACIK. PlayIntegrityAppCheckProviderFactory kurulmadan tum profil okuma/yazma permission_denied doner. Debug/emulator icin DebugAppCheckProviderFactory + Firebase Console'a debug token eklenmeli; Play Console SHA-256 + Play Integrity API acik olmali.
- Firebase Analytics user-property siniri: property ADI <=24, DEGER <=36 karakter; isimler university_id/faculty_id/department_id/gender/is_admin, degerler 36'ya clamp. Cikista setUserProperty(name, null) ile temizlenmeli.
- jcenter olu + android-pdf-viewer/Parse jcenter'da: Profil alani bunlari kullanmaz ama ayni build.gradle modernizasyonunda (mavenCentral, Firebase BoM, AndroidX) birlikte tasinmali, aksi halde modul derlenmez.
- Compose icin minSdk 21 + AGP 8 + Kotlin gerekir; mevcut minSdk 19/AGP 3.4.2'den yukseltme sart. targetSdk Play icin >=34 (2025) gerekir; profil alani yeni runtime izni getirmez (POST_NOTIFICATIONS bildirim alaninda).
- Serbest metin id cakismasi: custom bir adin slug'i listedeki bir id ile cakisirsa ayni segmente duser; iOS bunu kasitli yapar (lockstep). Kotlin Slug cikti birimi JSON id'leriyle BIREBIR ayni olmali yoksa ayni ad farkli id uretip segmentleri boler.

### İş Kalemleri

#### `P1` — Profil alani icin modern Android altyapi/bagimlilik temeli  _(P0 · efor: L)_
- **iOS davranışı:** Profil servisleri DI ile saglanir; gercek implementasyon signed-in +90 kullanici ve App Check gerektirir.
- **iOS karşılığı/kaynak:** iOS'ta Swift Package Manager + AppEnvironment DI; Profil servisleri AppEnvironment uzerinden enjekte edilir.
- **Android mevcut:** AGP 3.4.2, jcenter, com.android.support, Firebase 2018, manuel DI yok, Compose yok, DataStore yok, App Check yok.
- **Android teknoloji:** Jetpack Compose (BOM) + Navigation Compose, ViewModel+StateFlow, Hilt, Firebase BoM (RTDB + Analytics + App Check Play Integrity), kotlinx.serialization (JSON), coroutines.
- **Adımlar:**
  - build.gradle'leri AGP 8.x + Kotlin + mavenCentral()'a tasi, jcenter'i kaldir, com.android.support bagimliliklarini AndroidX'e gecir (gerekiyorsa android.useAndroidX=true).
  - Firebase BoM ekle: com.google.firebase:firebase-bom (en guncel kararli), ardindan firebase-database-ktx, firebase-auth-ktx, firebase-analytics-ktx, firebase-appcheck-playintegrity, firebase-appcheck-debug (debug flavor).
  - Compose BOM + androidx.navigation:navigation-compose + androidx.lifecycle:lifecycle-viewmodel-compose + lifecycle-runtime-compose ekle.
  - Hilt (com.google.dagger:hilt-android + kapt/ksp) ve Application sinifinda @HiltAndroidApp; FirebaseAppCheck.getInstance().installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance()) kur (debug'da DebugAppCheckProviderFactory).
  - org.jetbrains.kotlinx:kotlinx-serialization-json ve androidx.datastore:datastore-preferences ekle (DataStore sonraki alanlar icin de).
  - AdMob (play-services-ads) ve Parse bagimliliklarini KALDIR (parite karari: iOS'ta reklam yok).
- **Gradle/bağımlılık:** firebase-bom, firebase-database-ktx, firebase-auth-ktx, firebase-analytics-ktx, firebase-appcheck-playintegrity, firebase-appcheck-debug, compose-bom, navigation-compose, lifecycle-viewmodel-compose, hilt-android, kotlinx-serialization-json, datastore-preferences. KALDIR: play-services-ads, parse-android, parse-fcm-android.
- **Tuzaklar:** jcenter olu -> android-pdf-viewer/Parse birlikte tasinmali (bu alan disi ama ayni dosya). Compose minSdk 21 gerektirir; mevcut 19'dan yukselt. App Check enforce acik oldugundan saglayici kurulmazsa tum profil I/O permission_denied.
- **Kabul kriteri:** Modul AGP 8 + AndroidX + Compose ile derlenir; FirebaseDatabase ve App Check Play Integrity baslatilir; debug build'de App Check debug token ile RTDB okunabilir; AdMob/Parse bagimliliklari kaldirilmistir.

#### `P2` — UserProfile ve Gender Kotlin modelleri  _(P0 · efor: S)_
- **iOS davranışı:** Saf deger tipi, Firebase'den bagimsiz; form ve validasyon test edilebilir.
- **iOS karşılığı/kaynak:** UserProfile.swift: 13 alanli immutable struct (name, surname, university, universityId, universityIsCustom, faculty, facultyId, facultyIsCustom, department, departmentId, departmentIsCustom, gender, phoneNumber), maxNameLength=60, maxFieldLength=120, fullName='Ad Soyad'. Gender enum: female|male, label 'Kadin'/'Erkek'.
- **Android mevcut:** yok
- **Android teknoloji:** Kotlin data class + enum class, kotlinx.serialization opsiyonel.
- **Adımlar:**
  - data class UserProfile(val name, surname, university, universityId, val universityIsCustom: Boolean, faculty, facultyId, facultyIsCustom, department, departmentId, departmentIsCustom, val gender: Gender, val phoneNumber: String) olustur.
  - companion object'e const MAX_NAME_LENGTH=60, MAX_FIELD_LENGTH=120 ve val fullName get()='name surname' ekle.
  - enum class Gender(val raw: String) { FEMALE('female'), MALE('male') }; label() -> R.string.gender_female/'Kadin', R.string.gender_male/'Erkek'; fromRaw(String?) yardimcisi.
  - maxFieldLength=120 metin alanlari icin; slug id'ler kuralda <=140 oldugundan id uzunlugu ayrica kontrol edilir (P6/P9).
- **Tuzaklar:** Alan sirasi ve isimleri RTDB anahtarlariyla BIREBIR; phoneNumber E.164 olmali.
- **Kabul kriteri:** UserProfile ve Gender birim testlerinde olusturulabilir; gender.raw degerleri 'female'/'male'; label'lar Turkce; fullName dogru.

#### `P3` — Slug yardimcisi (Turkce-duyarli, [a-z0-9-], iOS ile lockstep)  _(P0 · efor: S)_
- **iOS davranışı:** Serbest yazilan ad, listedeki bir adla eslesirse AYNI id'yi uretmeli; id'ler FCM topic adlarinda dogrudan kullanilabilir olmali.
- **iOS karşılığı/kaynak:** Slug.swift: turkishMap ile İ/I/ı->i, Ğ/ğ->g, Ü/ü->u, Ş/ş->s, Ö/ö->o, Ç/ç->c; sonra lowercased(); ASCII harf/rakam tutulur, digerleri tek '-'e indirgenir (bastan/sondan tire yok, tire sadece sonraki tutulan karakterden hemen once flushlanir); bos ise 'unknown'.
- **Android mevcut:** yok
- **Android teknoloji:** Saf Kotlin object, dis bagimlilik yok.
- **Adımlar:**
  - object Slug { fun make(input: String): String } olustur; turkishMap aynen Swift'teki gibi (6 harf cifti + I/ı/İ ucgeni).
  - Karakterleri map'ten gecir, sonra lowercase (ASCII); pendingDash mantigini Swift'teki ile birebir uygula (Character.isLetterOrDigit && code < 128).
  - Sonuc bos ise 'unknown' don.
  - JSON id'leriyle lockstep dogrulamak icin: bundled universite/fakulte/bolum adlarini Slug.make'ten gecirip JSON'daki id ile karsilastiran birim test yaz (P20).
- **Tuzaklar:** Kotlin String.lowercase() varsayilan locale'e duyarli OLABILIR; Slug'da harfler zaten ASCII'ye eslenmis oldugundan lowercase(Locale.ROOT) kullan. Arama-normalizasyonu (P5) ile KARISTIRMA: Slug ı->i esler, arama ESLEMEZ.
- **Kabul kriteri:** Slug.make('Bogazici Universitesi')=='bogazici-universitesi'; make('İşletme')=='isletme'; make('  ')=='unknown'; make(' A.B#C ')=='a-b-c'. Tum bundled adlar JSON id'lerini uretir (test gecer).

#### `P4` — CatalogItem ve CatalogSelection modelleri  _(P0 · efor: S)_
- **iOS davranışı:** custom() trimlenmis metinden slug uretir; stored() edit ekraninin prefill'i icin saklanan alanlardan geri kurar.
- **iOS karşılığı/kaynak:** CatalogItem(id, name): Identifiable/Codable. CatalogSelection(name, id, isCustom): empty, catalog(item)->isCustom=false, custom(text)->trim+Slug.make+isCustom=true, stored(name,id,isCustom); isEmpty=name.isEmpty.
- **Android mevcut:** yok
- **Android teknoloji:** Kotlin data class + @Serializable.
- **Adımlar:**
  - @Serializable data class CatalogItem(val id: String, val name: String).
  - data class CatalogSelection(val name: String, val id: String, val isCustom: Boolean) olustur; companion: EMPTY, catalog(item), custom(text)=trim edip Slug.make(trim) ve isCustom=true, stored(name,id,isCustom).
  - val isEmpty get() = name.isEmpty() ekle.
- **Bağımlılık:** P3
- **Tuzaklar:** custom() icinde trim SONRASI slug; name alaninda ham (trimlenmis) metin saklanir, id slug.
- **Kabul kriteri:** catalog(item) isCustom=false ve id=item.id; custom('Yeni Uni') id=slug, isCustom=true; EMPTY.isEmpty==true.

#### `P5` — UniversityCatalog: gomulu JSON yukleme + Turkce-duyarli filtreleme + siralama  _(P0 · efor: M)_
- **iOS davranışı:** Bos/eksik liste sadece rahatligi dusurur, kullaniciyi engellemez (serbest metin hep var). 'isl' -> 'İşletme', 'bogazici' -> 'Boğaziçi' bulur; bos query tum liste.
- **iOS karşılığı/kaynak:** UniversityCatalog.swift: bundled() ile turkish-universities/faculties/departments.json yuklenir, ArchiveParser.compare ile ada gore siralanir; filtered(items, query): normalized(query) bos ise tum liste, degilse normalized(name).contains(needle). normalized = trim + lowercased(tr_TR) + folding(.diacriticInsensitive, tr_TR).
- **Android mevcut:** yok (mevcut fragmentlar RTDB 'Universitiess' okur; profil pickerlari BUNU KULLANMAZ)
- **Android teknoloji:** assets/ altinda 3 JSON, context.assets.open + kotlinx.serialization.decodeFromStream; java.text.Normalizer; Locale('tr','TR'); Collator(tr) ile siralama.
- **Adımlar:**
  - iOS'taki uc JSON dosyasini (turkish-universities.json ~414 kayit, turkish-faculties.json, turkish-departments.json) assets/catalog/ altina BIREBIR kopyala (dosya adlari tireyle kalsin -> res/raw DEGIL, assets).
  - class UniversityCatalog(val universities, faculties, departments: List<CatalogItem>); load(assetName) = assets.open -> Json.decodeFromStream<List<CatalogItem>>; hata/eksik dosyada bos liste + Log.e (pickerlar serbest metne duser).
  - Yuklemeden sonra Collator(Locale('tr','TR')) veya iOS ArchiveParser.compare esdegeri ile ada gore sirala.
  - fun filtered(items, query): normalized(query) bos ise items; degilse items.filter { normalized(it.name).contains(needle) }.
  - private fun normalized(s): s.trim().lowercase(Locale('tr','TR')) sonra Normalizer.normalize(NFD) + '\\p{Mn}+'.toRegex() ile combining mark strip (diakritik-duyarsiz). ı->i ESLEME (iOS folding davranisini koru).
  - Hilt @Provides @Singleton ile UniversityCatalog saglayici (uygulama baslarken bir kez yuklensin; buyuk IO'yu arka planda, StateFlow/lazy ile).
- **Bağımlılık:** P1, P4
- **Tuzaklar:** res/raw tire kabul etmez -> assets kullan. lowercase mutlaka Locale('tr') ile. Buyuk JSON'u ana thread'de parse etme (ANR); Dispatchers.IO. Siralama iOS compare ile ayni sonucu vermeli (Collator tr).
- **Kabul kriteri:** universities ~414 eleman yuklenir ve TR'ye gore sirali; filtered(universities,'bogazici') Boğaziçi'yi dondurur; filtered(faculties,'muh') Muhendislik eslesmelerini dondurur; bos query tum liste; eksik dosyada bos liste + log, cokme yok.

#### `P6` — UserProfileParser (RTDB snapshot <-> UserProfile)  _(P0 · efor: S)_
- **iOS davranışı:** Herhangi bir zorunlu alan eksik/bozuksa null -> profil 'absent' sayilir ve tamamlama kapisi acilir. updatedAt yazilan alanlarin parcasi DEGIL.
- **iOS karşılığı/kaynak:** UserProfileParser.swift: profile(from value): tum zorunlu string alanlar + gender('female'|'male') varsa UserProfile, *IsCustom icin bool() toleransli (eksikse false); fields(of): updatedAt HARIC 13 anahtarli sozluk (updatedAt'i servis ekler).
- **Android mevcut:** yok
- **Android teknoloji:** DataSnapshot.getValue / Map okuma; saf Kotlin, Firebase'siz test icin Map<String,Any?> alan versiyon.
- **Adımlar:**
  - object UserProfileParser { fun profile(value: Map<String, Any?>?): UserProfile? } yaz: zorunlu bos-olmayan string alanlari (name, surname, university, universityId, faculty, facultyId, department, departmentId, phoneNumber) ve gender raw -> Gender.fromRaw cek; biri eksik/bos ise null.
  - *IsCustom alanlarini toleransli oku: (value as? Boolean) ?: false.
  - fun fields(profile: UserProfile): Map<String, Any> = 13 anahtar (updatedAt HARIC); updatedAt'i repository ekleyecek (ServerValue.TIMESTAMP).
  - DataSnapshot -> Map donusumu: snapshot.value as? Map<String, Any?> (RTDB tum dugumu Map doner); testler duz Map ile calisir.
- **Bağımlılık:** P2
- **Tuzaklar:** RTDB sayilari Long/Double dondurur ama profil alanlari string; gender string. snapshot.getValue(genericTypeIndicator) yerine manuel Map okumak toleransli bool/eksik alan icin daha guvenli.
- **Kabul kriteri:** Tam gecerli Map -> UserProfile; eksik 'gender' -> null; *IsCustom eksik -> false; fields() 13 anahtar icerir ve updatedAt ICERMEZ.

#### `P7` — ProfileRepository (RTDB users/<uid>/profile read/write/delete)  _(P0 · efor: M)_
- **iOS davranışı:** uid, signed-in kullanici olmali (kural da zorlar ama erken basarisizlik round-trip'i onler). Offline'da disk cache'ten okunabilir (RTDB persistence). updatedAt server zamani (kural: ===now).
- **iOS karşılığı/kaynak:** FirebaseProfileService.swift: root=users; load=child(uid)/profile observeSingleEvent(.value) -> UserProfileParser; save=updateChildValues(fields + updatedAt=ServerValue.timestamp()) uid guard (auth.currentUser.id==uid yoksa notSignedIn); delete=removeValue; 15 sn timeout -> offline; hata eslemesi permission_denied->permissionDenied, NSURLError->offline. ProfileError: notSignedIn/permissionDenied/offline/unknown Turkce mesajlarla.
- **Android mevcut:** yok
- **Android teknoloji:** FirebaseDatabase.getInstance(app,'<europe-west-url>').getReference('users').child(uid).child('profile'); addListenerForSingleValueEvent / updateChildren / removeValue; coroutines (kotlinx-coroutines-play-services await() veya suspendCancellableCoroutine); withTimeout(15_000).
- **Adımlar:**
  - interface ProfileRepository { suspend fun load(uid): UserProfile?; suspend fun save(profile, uid); suspend fun delete(uid) } + sealed class ProfileError (NotSignedIn, PermissionDenied, Offline, Unknown(msg)) Turkce mesajlarla.
  - FirebaseProfileRepository: database = FirebaseDatabase.getInstance(app, databaseURL) (dogru europe-west instance); ref = db.getReference('users').child(uid).child('profile').
  - load(): withTimeout(15s) { suspendCancellableCoroutine { ref.addListenerForSingleValueEvent(...) } }; onDataChange -> UserProfileParser.profile(snapshot.value as Map); onCancelled -> mapError(error).
  - save(): once auth.currentUser?.uid==uid degilse ProfileError.NotSignedIn firlat; val values = UserProfileParser.fields(profile).toMutableMap(); values['updatedAt']=ServerValue.TIMESTAMP; ref.updateChildren(values).await().
  - delete(): uid guard sonrasi ref.removeValue().await() (hesap silme sirasinda, hala signed-in iken).
  - mapError(DatabaseError): PERMISSION_DENIED -> PermissionDenied; network/DISCONNECTED -> Offline; diger -> Unknown; timeout -> Offline. Logla (CancellationException haric).
- **Bağımlılık:** P1, P2, P6
- **Tuzaklar:** FirebaseDatabase.getInstance() varsayilan us-central'a baglanir; MUTLAKA dogru databaseURL ile cagir. setPersistenceEnabled(true) (App baslatmada, ilk referanstan once) offline okuma icin. App Check kurulu degilse save/load permission_denied. ServerValue.TIMESTAMP yerine System.currentTimeMillis() kullanirsan kural (===now) reddeder.
- **Kabul kriteri:** save() 14 anahtarin (13+updatedAt) hepsini yazar; updatedAt ServerValue.TIMESTAMP; yanlis uid ile save -> NotSignedIn; izin reddi -> PermissionDenied (Turkce); 15 sn yanit yoksa Offline; load eksik node'da null doner.

#### `P8` — ProfileStore / gate durum makinesi (loading/absent/present/failed)  _(P0 · efor: M)_
- **iOS davranışı:** Gate flicker yapmamali; ayni kullanici icin yeniden okuma eski profili gorunur tutar; okuma hatasi kullaniciyi profil tekrar girmeye ZORLAMAZ, retry sunar.
- **iOS karşılığı/kaynak:** ProfileStore.swift: State loading/absent/present(UserProfile)/failed(String); load(uid) ayni uid+profil varsa spinner flash etmez; CancellationError durumu birakir; failed error.userMessage saklar; update(profile)=present (reload'suz gate acar); reset() cikista temizler; profile computed.
- **Android mevcut:** yok
- **Android teknoloji:** @Singleton Hilt sinifi, MutableStateFlow<ProfileState>; viewModelScope/applicationScope + Job iptal.
- **Adımlar:**
  - sealed interface ProfileState { Loading; Absent; Present(profile); Failed(message) } ve class ProfileStore(repo) { val state: StateFlow<ProfileState> }.
  - suspend fun load(uid): eger loadedUID==uid && state is Present ise state'i Loading'e CEKME (flicker yok); try { repo.load -> Present veya Absent } catch CancellationException { birak/yut } catch e { loadedUID=uid; Failed(e.message) }.
  - fun update(profile) { _state.value = Present(profile) } (kaydetme sonrasi reload'suz gate acar).
  - fun reset() { _state.value = Loading; loadedUID = null } (cikista).
  - val profile: UserProfile? = (state as? Present)?.profile.
- **Bağımlılık:** P7
- **Tuzaklar:** StateFlow iptal: yeni load eskisini iptal etmeli (collectLatest veya Job.cancel). Hilt singleton olmali ki gate ve Account ayni durumu paylassin.
- **Kabul kriteri:** Ilk load Loading->Present/Absent; ayni uid ikinci load'da Present korunur (Loading'e dusmez); hata -> Failed(mesaj); update() aninda Present; reset() Loading+loadedUID temizler.

#### `P9` — ProfileFormViewModel (alanlar + validasyon + kaydetme)  _(P0 · efor: M)_
- **iOS davranışı:** Kaydet butonu builtProfile!=null olana dek disabled; save calisirken tekrar save yok; hata gosterilir, alanlar korunur; basaride onSaved (completion: gate ac; edit: geri+analytics.profileUpdated).
- **iOS karşılığı/kaynak:** ProfileFormViewModel.swift: name/surname/university/faculty/department(CatalogSelection)/gender(Gender?); isWorking, errorMessage; canSave=!isWorking && builtProfile!=null; builtProfile: gender + validField(name,60) + validField(surname,60) + validSelection(uni/fac/dep); validSelection: validField(name,120) + id(custom?Slug.make:selection.id) bos degil; validField: trim + ' +'->' ' + bos/uzun/yasak karakter([.$#[]/]+control+newline) kontrolu; save() coroutine, basaride onSaved, hatada fail(error.userMessage); phoneNumber=user.phoneNumber ?? existing.phoneNumber.
- **Android mevcut:** yok
- **Android teknoloji:** ViewModel + MutableStateFlow form state; viewModelScope.launch; AssistedInject (Hilt) ile user/existing/onSaved parametreleri.
- **Adımlar:**
  - @HiltViewModel (AssistedInject ile user: AuthUser, existing: UserProfile?, onSaved:(UserProfile)->Unit) ProfileFormViewModel; data class FormUiState(name, surname, university, faculty, department: CatalogSelection, gender: Gender?, isWorking, errorMessage).
  - init: existing varsa alanlari doldur (university = CatalogSelection.stored(existing.university, existing.universityId, existing.universityIsCustom) vb.), gender=existing?.gender, phoneNumber=user.phoneNumber ?: existing?.phoneNumber.
  - private fun validField(input, max): input.trim().replace(Regex(" +")," "); bos || length>max || Regex("[.$#\\[\\]/]").containsMatchIn || kontrol/newline karakteri -> null.
  - private fun validSelection(sel): name=validField(sel.name,120)?:return null; id = if(sel.isCustom) Slug.make(name) else sel.id; if(id.isBlank()) null else CatalogSelection(name,id,sel.isCustom).
  - builtProfile(): gender + validField(name,60) + validField(surname,60) + validSelection(uni/fac/dep) hepsi gecerliyse UserProfile(...) yoksa null; canSave = !isWorking && builtProfile!=null.
  - fun save(): if(isWorking||builtProfile==null) return; isWorking=true; viewModelScope.launch { try { repo.save(profile, uid); onSaved(profile) } catch e { fail(e) } }; fail: isWorking=false, CancellationException yut, errorMessage=mapMessage(e).
  - formattedPhoneNumber: PhoneNumber.fromE164(phoneNumber)?.formatted ?: phoneNumber ?: 'Telefon numarasi yok' (P15).
- **Bağımlılık:** P2, P3, P4, P7, P15
- **Tuzaklar:** Regex kacislari: Android'de yasak karakter seti [.$#[]/] + Character.isISOControl + newline. ' +'->' ' regex yalniz COKLU boslugu indirger. gender null baslar (segmented'da secim yok) ve canSave'i engeller.
- **Kabul kriteri:** Bos alanla canSave=false; ad 61 karakter -> gecersiz; 'A.B' ad -> yasak karakter -> gecersiz; coklu bosluk tek bosluga iner; custom secimde id=slug; gecerli formda save -> repo.save cagrilir ve onSaved tetiklenir; izin hatasi Turkce mesaj.

#### `P10` — ProfileFormView (ortak form UI: ad/soyad/katalog/cinsiyet/telefon)  _(P0 · efor: M)_
- **iOS davranışı:** Form hem tamamlama hem edit ekraninda AYNI; katalog pickerlari cevreleyen navigasyon yiginina push edilir.
- **iOS karşılığı/kaynak:** ProfileFormView.swift: Section 'Kisisel Bilgiler' (Ad/Soyad TextField, autocorrect kapali), 'Egitim' (3 catalogRow -> CatalogPicker'a push, bos ise 'Sec' gri), 'Cinsiyet' (segmented Picker Gender.allCases), salt-okunur Telefon (footer: 'Numaran giris sirasinda dogrulandi ve degistirilemez.'). isWorking'de tum alanlar disabled.
- **Android mevcut:** yok
- **Android teknoloji:** Compose: OutlinedTextField (ad/soyad), satir tiklaninca Navigation Compose ile CatalogPicker rotasina git, SingleChoiceSegmentedButtonRow (Material3) cinsiyet, salt-okunur Text telefon. (Alternatif: XML + ViewBinding Fragment.)
- **Adımlar:**
  - @Composable ProfileFormView(state, onNameChange, onSurnameChange, onPickCatalog(kind), onGenderChange): basliklandirilmis bolumler.
  - Kisisel Bilgiler: iki OutlinedTextField (Ad/Soyad), keyboardOptions autoCorrect=false, enabled=!isWorking, testTag('profile.name'/'profile.surname').
  - Egitim: uc 'catalog row' (ListItem/Row) -> tiklaninca onPickCatalog(University/Faculty/Department); deger bossa 'Sec' (gri), doluysa name goster; testTag('profile.university' vb.).
  - Cinsiyet: SingleChoiceSegmentedButtonRow iki segment (Kadin/Erkek), secili=gender; secim yoksa hicbiri secili; testTag('profile.gender').
  - Telefon: salt-okunur Row (formattedPhoneNumber, monospace), altinda yardim metni 'Numaran giris sirasinda dogrulandi ve degistirilemez.'
  - isWorking iken tum alanlar/satirlar disabled.
- **Bağımlılık:** P9, P11, P15
- **Tuzaklar:** Compose'da catalog secimini geri almak icin Navigation Compose SavedStateHandle veya ViewModel paylasimi (picker ayni ProfileFormViewModel'e yazmali). Segmented'da baslangicta secim YOK durumu desteklenmeli (gender null).
- **Kabul kriteri:** Form her iki ekranda ayni; ad/soyad yazilabilir; 3 satir CatalogPicker acar ve secilen deger geri yansir; cinsiyet secilebilir; telefon degistirilemez; isWorking'de alanlar kilitli.

#### `P11` — CatalogPickerView (aranabilir liste + serbest metin + secim)  _(P0 · efor: M)_
- **iOS davranışı:** Serbest metin yalniz query bos degil ve listede birebir ad eslesmesi yoksa sunulur; allowsCustom=false (add-exam) serbest metni kapatir; secili id checkmark ile isaretli; secim sonrasi geri doner.
- **iOS karşılığı/kaynak:** CatalogPickerView.swift: arama alani (magnifyingglass + 'Ara'), showsCustomOption = allowsCustom && trimmedQuery boş değil && options'ta birebir ad (case-insensitive) yok -> '"<query>" kullan' butonu + footer ipucu; matches=UniversityCatalog.filtered(options,query); her satir Button, secili id'de checkmark; secince binding set + dismiss. allowsCustom=false listeyle sinirlar.
- **Android mevcut:** yok
- **Android teknoloji:** Compose: ayri Navigation rota (catalog/{kind}); Scaffold + OutlinedTextField (arama) + LazyColumn; derive filtered list + custom row; secimde navController.popBackStack ve sonucu ViewModel'e yaz.
- **Adımlar:**
  - Navigation rota: composable('catalog/{kind}') veya ayri ekran; parametre olarak title, options, accessibilityPrefix, allowsCustom.
  - Ustte arama OutlinedTextField (placeholder 'Ara', leading magnifier), query state.
  - custom gosterim kosulu: allowsCustom && query.trim().isNotEmpty() && options.none { it.name.equals(query.trim(), ignoreCase=true) } -> '"${query.trim()}" kullan' ListItem + altinda 'Aradigin listede yoksa, yazdigin metni oldugu gibi kullanabilirsin.'
  - LazyColumn(UniversityCatalog.filtered(options, query)) -> her item ListItem, secili (item.id==selection.id) ise trailing checkmark.
  - Tiklama: custom -> CatalogSelection.custom(query); item -> CatalogSelection.catalog(item); secimi ProfileFormViewModel'e yaz (setUniversity/Faculty/Department) ve popBackStack.
  - testTag: '<prefix>.search', '<prefix>.custom', '<prefix>.option'.
- **Bağımlılık:** P5, P4, P9
- **Tuzaklar:** Filtreleme her tus vurusunda; buyuk liste (414) icin derivedStateOf/remember ile recomposition sinirla. Picker ayni ViewModel instance'ina yazmali (Navigation'da SavedStateHandle ile parent'a sonuc dondur veya Hilt-scoped ViewModel paylas).
- **Kabul kriteri:** Arama yazinca liste TR-duyarli filtrelenir; listede olmayan metin icin '"X" kullan' cikar ve secilince isCustom=true id=slug uretir; listedeki secim isCustom=false id=JSON id; birebir ad eslesmesinde custom cikmaz; allowsCustom=false'ta custom hic cikmaz; secili satirda checkmark.

#### `P12` — ProfileCompletionView (ZORUNLU tamamlama kapisi)  _(P0 · efor: M)_
- **iOS davranışı:** Arsiv profil olmadan acilmaz; kullanici sadece cikis yapabilir (kimse kapana kisilmaz); kaydetme ikinci okuma yapmadan gate'i acar; profile_completed olayi loglanir.
- **iOS karşılığı/kaynak:** ProfileCompletionView.swift: NavigationStack+Form; header 'Son bir adim' + 'Arsive ulasmak icin birkac bilgini tamamla...'; ProfileFormView; hata bolumu; toolbar: cancellationAction 'Cikis Yap' (auth.signOut, isWorking'de disabled), confirmationAction 'Kaydet' (viewModel.save, !canSave'de disabled); onSaved -> profile.update + analytics.profileCompleted; onAppear screenView 'ProfileCompletion'.
- **Android mevcut:** yok (giris sonrasi dogrudan HomeActivity aciliyor)
- **Android teknoloji:** Compose ekran (NavHost icinde bir destination) veya interstitial; ProfileFormViewModel (existing=null); TopAppBar aksiyonlari.
- **Adımlar:**
  - @Composable ProfileCompletionScreen(user): ProfileFormViewModel(existing=null, onSaved={ profileStore.update(it); analytics.logEvent('profile_completed'); }).
  - Ust kisim: baslik 'Son bir adim' + aciklama 'Arsive ulasmak icin birkac bilgini tamamla. Bu bilgiler profilinde saklanir.'
  - ProfileFormView(state) goster; altinda errorMessage varsa kirmizi hata satiri (testTag 'profile.error').
  - TopAppBar: sol 'Cikis Yap' (auth.signOut(), isWorking'de disabled, testTag 'profile.signOut'), sag 'Kaydet' (viewModel.save(), !canSave'de disabled, testTag 'profile.save').
  - Ekran acilisinda analytics.logEvent screen_view (screen_name='ProfileCompletion'); BackHandler ile geri tusunu YUT (kapi atlanamaz).
  - Kaydet basarisinda ProfileStore.update -> gate otomatik Present'e gecer ve arsiv acilir.
- **Bağımlılık:** P8, P9, P10
- **Tuzaklar:** BackHandler(enabled=true){} ile geri atlamasini engelle. Compose NavHost'ta bu destination'a startDestination gate durumuna gore karar vermeli (P14). screen_view olayi icin FirebaseAnalytics.Param.SCREEN_NAME.
- **Kabul kriteri:** Profili olmayan kullanici giriste bu ekrana zorlanir; geri tusu/geri jesti arsive gecirmez; sadece Cikis Yap ile cikilir; gecerli form + Kaydet -> profil yazilir, gate acilir, profile_completed loglanir; hata Turkce gosterilir.

#### `P13` — EditProfileView (Hesap'tan profil duzenleme)  _(P1 · efor: S)_
- **iOS davranışı:** Mevcut profil alanlari prefill edilir; kaydetme profili gunceller, gate'i Present tutar, ekrani kapatir; profile_updated loglanir.
- **iOS karşılığı/kaynak:** EditProfileView.swift: Form + ProfileFormView(existing=profile); toolbar confirmationAction 'Kaydet' (!canSave disabled); onSaved -> profile.update + analytics.profileUpdated + dismiss (geri pop); onAppear screenView 'EditProfile'. Baslik 'Profili Duzenle'.
- **Android mevcut:** yok
- **Android teknoloji:** Compose destination 'editProfile'; ProfileFormViewModel(existing=current profile); TopAppBar Kaydet; popBackStack.
- **Adımlar:**
  - @Composable EditProfileScreen(user): ProfileFormViewModel(existing=profileStore.profile, onSaved={ profileStore.update(it); analytics.logEvent('profile_updated'); navController.popBackStack() }).
  - Baslik 'Profili Duzenle'; ProfileFormView + errorMessage satiri (testTag 'editProfile.error').
  - TopAppBar Kaydet (testTag 'editProfile.save', !canSave disabled).
  - Acilista screen_view 'EditProfile'.
- **Bağımlılık:** P8, P9, P10, P16
- **Tuzaklar:** existing prefill'de *IsCustom bayraklari korunmali (stored() ile). Kaydetme sonrasi ayni profil store'a update edilir, reload yok.
- **Kabul kriteri:** Edit ekrani mevcut degerlerle acilir; degisiklik + Kaydet -> RTDB guncellenir, store Present kalir, ekran kapanir, profile_updated loglanir.

#### `P14` — Navigasyon gate entegrasyonu (login -> profil kapisi -> arsiv)  _(P0 · efor: M)_
- **iOS davranışı:** Arsiv YALNIZCA profil Present iken gorunur; okuma suresi kisa bir loading gosterir; hata retry sunar; cikista durum temizlenir.
- **iOS karşılığı/kaynak:** RootView.swift ProfileGate: session.signedIn olunca profile.load(uid) (.task id:user.id); durum .loading->SessionLoadingView, .absent->ProfileCompletionView, .present->arsiv, .failed->ErrorStateView+retry. Cikista profile.reset. Ayrica wallet/unlocks/announcements observe ve analytics identify profil segmentleriyle.
- **Android mevcut:** LoginActivity/MainActivity giris sonrasi DOGRUDAN HomeActivity'yi acar; ikinci bir profil kapisi yok.
- **Android teknoloji:** Tek-Activity + Navigation Compose NavHost; ust seviye gate composable ProfileStore.state + Auth state'i gozler; veya mevcut Activity yapisinda bir ProfileGateActivity interstitial.
- **Adımlar:**
  - Giris basarili olunca HomeActivity'ye dogrudan gitme; once profil durumunu cozen bir gate ekle: @Composable AppGate() { auth signedIn -> LaunchedEffect(uid){ profileStore.load(uid) }; when(profileStore.state){ Loading->Splash/Progress; Absent->ProfileCompletionScreen; Present->HomeGraph; Failed->ErrorScreen(retry=load(uid)) } }.
  - Mevcut Activity mimarisi korunacaksa: LoginActivity basarili dogrulama sonrasi HomeActivity yerine profil durumunu kontrol eden bir yonlendirici (ProfileGateActivity) ac; Absent ise ProfileCompletionActivity, Present ise HomeActivity.
  - Failed durumunda ErrorState + 'Tekrar dene' (profileStore.load(uid) yeniden).
  - Cikista profileStore.reset() cagir (P16/Auth).
  - Not: iOS'ta gate ayni anda wallet/unlocks/announcements observe ve analytics identify de baslatir (bunlar ilgili alanlarda; burada sadece analytics identify P17'ye baglanir).
- **Bağımlılık:** P8, P12, P17
- **Tuzaklar:** Tek-Activity Compose'a gecis buyuk; eger mevcut Activity yapisi korunacaksa gate'i interstitial Activity ile cozup profileStore'u process-genelinde (Hilt singleton) paylas. Gate ile Update force-gate (baska alan) sirasi: once force-update, sonra signin, sonra profil kapisi.
- **Kabul kriteri:** Profili olmayan kullanici giriste tamamlama ekranina; profili olan dogrudan arsive gider; okuma hatasinda retry'li hata ekrani; profil kaydedilince spinner'siz arsive gecis; cikis -> tekrar giriste temiz okuma.

#### `P15` — PhoneNumber bicimlendirme (salt-okunur telefon gosterimi)  _(P1 · efor: S)_
- **iOS davranışı:** Telefon kullanici tarafindan girilmez; auth token'daki dogrulanmis numara aynen gosterilir ve yazarken degistirilemez.
- **iOS karşılığı/kaynak:** PhoneNumber.swift: e164('+905321234567') parse; formatted='+90 5XX XXX XX XX' (gruplar 3-3-2-2); countryCode='+90', nationalLength=10, national '5' ile baslayan 10 hane.
- **Android mevcut:** yok (profil icin); mevcut login akisinda numara formatlama farkli olabilir
- **Android teknoloji:** Saf Kotlin object/class; profil icin sadece E.164 -> display gerekli (giris numarasi dogrulamasi Auth alaninda).
- **Adımlar:**
  - object/class PhoneNumber: fromE164(e164): '+90' ile basliyorsa kalan 10 haneyi al, '5' ile baslayan 10 ASCII hane dogrula, aksi null.
  - formatted(): '+90 ' + national'i 3-3-2-2 gruplara ayirarak bosluklu birlestir ('+90 532 123 45 67').
  - ProfileFormViewModel.formattedPhoneNumber bunu kullanir; parse edilemezse ham E.164 doner, numara yoksa 'Telefon numarasi yok'.
- **Tuzaklar:** Profil WRITE'inda telefon ham E.164 (auth.currentUser.phoneNumber) yazilmali (kural ===auth.token.phone_number); bicimlendirme SADECE gosterim icin.
- **Kabul kriteri:** fromE164('+905321234567').formatted()=='+90 532 123 45 67'; ABD numarasi ('+1...') null; profil formunda telefon salt-okunur dogru bicimde gorunur.

#### `P16` — Hesap ekrani Profil satiri + edit girisi + hesap silmede deleteProfile  _(P1 · efor: M)_
- **iOS davranışı:** Silinen hesap arkada profil birakmaz; profil, recent-login prompt'tan ONCE silinir (sonra gerekirse reauth).
- **iOS karşılığı/kaynak:** AccountView.swift profileSection: NavigationLink -> EditProfileView, baslik 'Profil' + profilName (fullName) veya 'Profili Duzenle'. AccountViewModel.deleteAccount: once deleteUserData() (profileService.deleteProfile, hala signed-in iken) sonra auth.deleteAccount; reauth gerekirse SMS ile yeniden dogrula.
- **Android mevcut:** yok (Hesap ekrani kavrami yok)
- **Android teknoloji:** Compose Account ekrani (veya mevcut cizim); profil satiri -> navController.navigate('editProfile'); hesap silme akisinda ProfileRepository.delete(uid) cagrisi.
- **Adımlar:**
  - Account ekranina 'Profil' satiri ekle: ProfileStore.profile?.fullName varsa goster, yoksa 'Profili Duzenle'; tiklama -> editProfile rotasi (testTag 'account.editProfile').
  - Hesap silme akisinda (Auth/Account alani) silmeden ONCE profileRepository.delete(uid) cagir (kullanici hala signed-in iken; owner-only kural izin verir), sonra FirebaseUser.delete().
  - Cikis/silme sonrasi profileStore.reset() ve analytics user-property temizligi (P17).
  - Not: hesap silmenin tam reauth akisi Auth alaninda; burada yalnizca profil-silme kancasini ve profil satirini sagla.
- **Bağımlılık:** P7, P8, P13
- **Tuzaklar:** deleteProfile MUTLAKA FirebaseUser.delete()'ten once (signed-in iken); aksi halde owner-only kural reddeder. Account ekrani/silme akisi buyuk olcude Auth alani; cakismayi deps ile isaretle.
- **Kabul kriteri:** Account'ta Profil satiri profil adini gosterir ve edit ekranina gider; hesap silindiginde users/<uid>/profile da silinir; cikista analytics user-property'ler temizlenir.

#### `P17` — Profil tureli Analytics user-property segmentleri  _(P1 · efor: S)_
- **iOS davranışı:** Kimlik id + admin ile baslar, profil yuklenince segmentlerle zenginlesir; her degisimde yeniden uygulanir; cikista temizlenir.
- **iOS karşılığı/kaynak:** AnalyticsUser(id, profile, isAdmin): university_id/faculty_id/department_id/gender/is_admin; properties 36 karaktere clamp; setUser -> her property set/clear; RootView profil yuklenince/degisince identifyUser, cikista setUser(nil). FirebaseAnalyticsTracker.setUser her propertyName icin Analytics.setUserProperty.
- **Android mevcut:** yok
- **Android teknoloji:** FirebaseAnalytics.setUserProperty(name, value) ve setUserId; profilStore.state degisimini gozle.
- **Adımlar:**
  - profileStore.state Present olunca FirebaseAnalytics.setUserId(uid) + setUserProperty('university_id', profile.universityId.take(36)), 'faculty_id', 'department_id', 'gender'(profile.gender.raw), 'is_admin'.
  - Profil yoksa (Absent) bu property'leri null ile temizle; is_admin yine set edilebilir.
  - Cikista setUserId(null) + tum property'leri setUserProperty(name, null) ile temizle (ProfileStore.reset ile birlikte).
  - Property isimleri iOS ile ayni: university_id, faculty_id, department_id, gender, is_admin.
- **Bağımlılık:** P8
- **Tuzaklar:** Analytics property ADI max 24 karakter (hepsi uyuyor), DEGER max 36 (clamp sart). Audience/segmentasyon SUNUCU tarafinda (Analytics audiences) kurulur; istemci yalniz property'leri besler.
- **Kabul kriteri:** Profil Present oldugunda 5 user-property dogru adlarla ve 36 karaktere clamp'li set edilir; cikista hepsi null; Firebase DebugView'da gorulur.

#### `P18` — Bildirim segment hazirligi (slug id'lerin kanonik kullanimi)  _(P2 · efor: S)_
- **iOS davranışı:** Slug'lar kanonik ve topic-guvenli uretilir; fiili segment hedefleme sunucu tarafinda (iOS istemci abone olmaz).
- **iOS karşılığı/kaynak:** iOS istemcisi profil *Id slug'larini notification segmentleri/FCM topic'leri icin KANONIK id olarak uretir (UserProfile dokumantasyonu, Slug FCM-safe). Ancak iOS master istemci-tarafi topic aboneligi YAPMAZ; segmentasyon Analytics audience -> FCM campaign uzerinden sunucu-taraflidir.
- **Android mevcut:** yok
- **Android teknoloji:** Opsiyonel FirebaseMessaging.getInstance().subscribeToTopic(slug) yolu; veya (tercih) yalniz Analytics audience'lara guven.
- **Adımlar:**
  - KARAR GEREKIR (openQuestion): segmentasyon Analytics audience ile mi (iOS gibi, ek is YOK, P17 yeterli) yoksa istemci topic aboneligi ile mi yapilacak.
  - Eger istemci topic yolu secilirse: profil Present olunca subscribeToTopic('uni_'+universityId), 'fac_'+facultyId, 'dep_'+departmentId, 'gender_'+gender.raw; profil degisince eski topic'lerden unsubscribeFromTopic; cikista hepsinden cik.
  - Slug [a-z0-9-] FCM topic regex ([a-zA-Z0-9-_.~%]) ile uyumlu; ek prefix ekle (topic ciplak id olmasin).
  - Not: iOS paritesi icin VARSAYILAN oneri Analytics audience (P17) + sunucu kampanyasi; istemci topic aboneligi yalniz urun karari netlesirse yazilir.
- **Bağımlılık:** P17, P3
- **Tuzaklar:** iOS'u birebir takip etmek icin istemci topic aboneligi GEREKMEYEBILIR; gereksiz topic aboneligi cift-segmentasyon yaratir. Once urun karari netlesmeli.
- **Kabul kriteri:** Slug id'ler FCM topic adi olarak gecerli; secilen yola gore ya Analytics audience hazirligi tamam (P17) ya da profil degisiminde dogru topic abone/abonelik-iptali yapilir.

#### `P19` — Backend sozlesme uyumu + RTDB kural dogrulama + App Check (profil I/O)  _(P0 · efor: S)_
- **iOS davranışı:** Gecersiz sema/yasak karakter/yanlis telefon sunucuda reddedilir; istemci zaten gecerli veri uretir.
- **iOS karşılığı/kaynak:** users/<uid>/profile .validate: 14 anahtar zorunlu; *Id ^[a-z0-9-]+$ max 140; name/surname max 60, university/faculty/department max 120, yasak [.$#[]/] ve en az bir bosluk-disi; gender female|male; phoneNumber===auth.token.phone_number ve ^\+90[0-9]{10}$; updatedAt===now; $other reddedilir. Kurallar PLATFORMDAN BAGIMSIZ (iOS'ta yazili, degistirilmez).
- **Android mevcut:** yok; mevcut uygulama App Check kullanmaz, profil dugumune yazmaz
- **Android teknoloji:** Android istemci bu kurallara BIREBIR uyar; App Check Play Integrity enforce RTDB icin acik.
- **Adımlar:**
  - Android write'inin TAM 14 anahtar gonderdigini dogrula (fazladan alan -> $other reddi). updatedAt=ServerValue.TIMESTAMP, phoneNumber=auth.currentUser.phoneNumber (ham E.164).
  - Id'lerin ^[a-z0-9-]+$ ve <=140 oldugunu istemcide de kontrol et (Slug zaten uretir); isim alanlarini 60/120 ve yasak karaktere gore P9'da dogrula.
  - App Check: PlayIntegrityAppCheckProviderFactory kurulumu (P1) + Play Console SHA-256 + Firebase Console'da Play Integrity saglayici + debug build icin DebugAppCheckProviderFactory ve Console'a debug token eklenmesi.
  - Firestore/RTDB kural dosyasina (database.rules.json) PROFIL icin DEGISIKLIK YAPMA (zaten platform-bagimsiz dogru). Entegrasyon testinde emulator/gercek kurala karsi gecerli ve gecersiz yazim dene.
  - Not: config/appUpdate/android siblingi ve RTDB android validasyonu PROFIL ALANI DISI (Update alani); burada yazma.
- **Bağımlılık:** P1, P6, P7, P9
- **Tuzaklar:** App Check enforce acikken saglayici yoksa TUM profil I/O permission_denied. updatedAt System.currentTimeMillis ile yazilirsa (===now) reddi. Fazladan bir anahtar (orn. eski bir alan) $other kuralina takilir.
- **Kabul kriteri:** Android'in yazdigi profil kurali gecer (reddedilmez); eksik anahtar/yanlis telefon/buyuk harf id iceren bir yazim kuralca reddedilir (test ile dogrulandi); App Check kurulu ve profil I/O gecer.

#### `P20` — Birim/entegrasyon testleri + testTag paritesi  _(P1 · efor: M)_
- **iOS davranışı:** Saf cekirdek mantik Firebase'siz test edilebilir; UI testleri stabil identifier'lara dayanir.
- **iOS karşılığı/kaynak:** iOS saf deger tipleri (Slug, UniversityCatalog.filtered, UserProfileParser, ProfileFormViewModel.validField/validSelection, ProfileStore) birim testlidir; accessibilityIdentifier'lar (profile.name, profile.save, profile.university.option/custom, account.editProfile, editProfile.save) UI test icindir.
- **Android mevcut:** yok (test altyapisi devre disi birakilmis)
- **Android teknoloji:** JUnit + kotlinx-coroutines-test (runTest) + Turbine (StateFlow); Compose UI test (createComposeRule, testTag); Firebase Emulator Suite (RTDB kural testi).
- **Adımlar:**
  - Slug: tum bundled universite/fakulte/bolum adlarini Slug.make'ten gecirip JSON id ile karsilastiran test (lockstep garanti).
  - UniversityCatalog.filtered: TR-duyarli ornekler (isl->İşletme, bogazici->Boğaziçi, bos query tum liste).
  - UserProfileParser: tam/eksik/bool-eksik Map senaryolari; fields() 13 anahtar ve updatedAt yok.
  - ProfileFormViewModel: validField (trim, coklu bosluk, 60/120 sinir, yasak karakter), validSelection (custom->slug), canSave gecisleri, save basari/hata (sahte repo).
  - ProfileStore: ayni uid flicker yok, failed+retry, update() aninda Present, reset().
  - Compose UI testleri testTag'larla (profile.name/surname/university/faculty/department/gender/save/signOut, editProfile.save, account.editProfile).
  - RTDB kural testi: Firebase Emulator ile gecerli yazim gecer, eksik anahtar/yanlis telefon/buyuk harf id reddedilir.
- **Bağımlılık:** P3, P5, P6, P8, P9
- **Tuzaklar:** Test altyapisi build.gradle'de yorumda; JUnit+coroutines-test+Turbine+compose-ui-test-junit4 eklenmeli. Emulator kural testi ayri setup (firebase.json emulators).
- **Kabul kriteri:** Slug lockstep testi gecer; filtreleme/parser/validasyon/store testleri yesil; Compose testleri testTag'lari bulur; emulator kural testleri gecerli/gecersiz senaryolari dogrular.


---

## Arşiv/Gezinme (RTDB→Firestore göçü + offline önbellek + dosya indirme)  ·  `archive`

**Özet:** iOS master (v3.1.0) arşiv katmanını tamamen RTDB `Universitiess` ağacından isimli `vifi` Firestore veritabanına (catalogNodes + exams, status==published, +90/admin kuralı) taşıdı; ad=anahtar modelini kanonik-slug-id + ayrı displayName modeline (`ArchivePath`) çevirdi; offline için keepSynced yerine giriş-sonrası tam-arşiv prefetch (`ArchivePrefetcher`, Firestore kalıcı disk önbelleği) kullandı; dosyaları artık download-token'lı URL yerine `storagePath`'ten kurulan kanonik URL + kullanıcı ID token + App Check başlığı ile indiriyor (`StorageURL`/`RemoteFileLoader`/`FirebaseRequestAuthorizer`); gezinme üniversite→fakülte→bölüm→ders→sınav drill-down + breadcrumb + son görüntülenenler (`RecentExamsStore`) + Türkçe collation sıralama + Türkçe arama katlaması içeriyor. Mevcut Android v2.5 ise hâlâ AndroidX-öncesi (com.android.support), AGP 3.4.2, jcenter (ölü), 2018 Firebase SDK ile RTDB `Universitiess`'i okuyan Spinner/Fragment/ListView yapısı; Picasso ve ham HttpURLConnection ile token'lı download-URL indiriyor; sınav erişimini AdMob reklam izletme ile gate'liyor. Bu liste, iOS arşiv alanının HER davranışını Android'de birebir yeniden kurmak için gerçek sınıf/API adlarıyla somut iş kalemleri verir. Not: kilit/coin ekonomisi, sınav yükleme (AddExam), sınav görüntüleyici (PDF/image render), profil, auth, bildirim ayrı alanların işidir; buraya yalnızca bağımlılık olarak referans verildi.

**Android mevcut durum:** AndroidX-öncesi (android.support.*), AGP 3.4.2 + Gradle 5.1.1, compileSdk/targetSdk 28, minSdk 19, jcenter() (kapandı). Mimari: salt Java, Activity + support-v4 Fragment + ListView/ArrayAdapter + Spinner. Arşiv okuma: `FirebaseDatabase.getInstance().getReference("Universitiess")` üzerinde `addValueEventListener` ile 4 ayrı ekran (UniversityFragment/FacultiesFragment/DepartmentsFragment/LessonFragment) ve CourseExamListActivity; her düğümden `uniname/fakname/bolname/lessonname/imagename` string-leaf'leri okunup diğer string-leaf anahtarlar atlanıyor (ad=anahtar modeli). Gezinme Bundle extra'larıyla ("uniName","facName"...) yapılıyor, breadcrumb yok, son görüntülenenler yok, offline yok, Türkçe sıralama/arama yok (Firebase insertion order). Sınav dosyaları: ExamDetailActivity görsel URL'lerini ListView'e koyup ImageZoomActivity'de `Picasso.get().load(downloadURL)` ile; ExamDetailPdfActivi…

**Dokunulan backend sözleşmesi:** Ortak backend değişmiyor; Android yalnızca OKUYAN taraf olarak uymalı: (1) isimli `vifi` Firestore DB (eur3/europe-west), `catalogNodes/<id>` (kind, parentId zinciri, status:'published', name, childCount) ve `exams/<id>` (courseId, title, kind JPG|PDF, status:'published', files:[{storagePath,order}]) — read: +90|admin. Kök üniversiteler parentId=="" ile listelenir. (2) Storage get: `imagess/<uuid>.jpg` ve `pdfs/<uuid>.pdf` yalnızca +90|admin, App Check enforce; indirme download-token'ına GÜVENMEZ, `storagePath`'ten kanonik URL kurar + ID token + App Check başlığı gönderir. (3) App Check: Storage+RTDB+Auth ENFORCE, Firestore monitoring→enforce; Android = Play Integrity provider (+ debug build için debug provider ve Console'a debug token). Yeni backend yazılmaz. Kapsam DIŞI backend boşlukları (başka alanların işi): RTDB `config/appUpdate/android` kardeşi eklenmeli (force-update alanı), `c…

**Android'e özgü riskler:**
- Play Store artık targetSdk 35 (Android 15) zorunlu kılıyor; mevcut target 28 ile yeni sürüm YÜKLENEMEZ. AndroidX migrasyonu (android.support.*→androidx.*) tüm dosyaları etkiler ve bu alanın ön koşuludur.
- jcenter() kapandı; barteksc `com.github.barteksc:android-pdf-viewer:2.3.0` artık çözülemez. PDF için ya JitPack üzerinden ara ya da yerleşik `android.graphics.pdf.PdfRenderer`'a geç (indirme bu alanda, render Exam alanında).
- Firestore çoklu-veritabanı API'si `FirebaseFirestore.getInstance(FirebaseApp, "vifi")` yalnızca yeni SDK'da var (Firebase BoM >= 32.x / firebase-firestore >= 24.9). 2018 SDK (16.x) bunu ve PersistentCacheSettings'i desteklemez; BoM'a geçiş şart.
- App Check Play Integrity: uygulamanın Play Console'da kayıtlı olması, imza SHA-256'larının eklenmesi ve Play Integrity API'nin etkin olması gerekir; debug/emulator buildleri `DebugAppCheckProviderFactory` + Console'a debug token ister, yoksa Storage/Firestore okumaları enforce sonrası 403 döner.
- Storage App Check + rules enforce edilince mevcut `Picasso.get().load(publicDownloadURL)` ve ham HttpURLConnection indirmeleri 403 alır; kimlik-doğrulamalı indiriciye (ID token + X-Firebase-AppCheck başlığı) geçilmeden görüntüleyici bozulur.
- Firestore Türkçe collation yapamaz; taksonomi istemcide `java.text.Collator(new Locale("tr","TR"))` ile sıralanmalı, sınavlar ters (yeni→eski). Ayrıca arama katlaması (ı→i, aksan katlama, tr lowercase) istemcide yapılmalı.
- minSdk 19 Coil/Compose/yeni kütüphaneler için düşük; minSdk 24'e çıkarılmalı (Firestore 24.x zaten minSdk 21+ ister).
- Offline için Firestore'da keepSynced yoktur; ziyaret edilmemiş dal çevrimdışı BOŞ döner. Giriş-sonrası `Source.SERVER` ile tam-arşiv prefetch yapılmazsa offline liste yanlış 'boş' gösterir.
- R8/ProGuard: Firestore `toObject()`/POJO kullanılırsa model sınıfları için keep kuralı; release'te `minifyEnabled true` açılması önerilir (şu an false).
- ArchivePath eşitliği yalnız id'ye dayanır; canlı rename (aynı id yeni ad) liste/başlığı bayat bırakabilir — displayName'i görünüm-değerine kat veya resume'da yeniden oku.

### İş Kalemleri

#### `ARCH-00` — Build/bağımlılık modernizasyonu: AndroidX + AGP/Gradle + Firebase BoM + Firestore/Storage/App Check (bu alanın ön koşulu)  _(P0 · efor: XL)_
- **iOS davranışı:** iOS SwiftUI + MVVM + FirebaseFirestore SPM; isimli vifi DB + App Check App Attest.
- **iOS karşılığı/kaynak:** ViFiApp/AppDelegate Firebase configure; FirebaseFirestoreProvider + ViFiAppCheckProviderFactory.
- **Android mevcut:** com.android.support:*, AGP 3.4.2, Gradle 5.1.1, compile/targetSdk 28, minSdk 19, jcenter(), 2018 Firebase (firebase-database:16.1.0, firebase-storage:16.1.0, firebase-core:16.0.8). Firestore/App Check yok.
- **Android teknoloji:** AndroidX, Gradle 8.x, AGP 8.5, Firebase BoM 33.x, mavenCentral
- **Adımlar:**
  - settings.gradle + build.gradle'de jcenter()'ı kaldır, google() + mavenCentral() (+ gereği PDF lib için maven { url 'https://jitpack.io' }) ekle.
  - AGP'yi en az 8.5.x'e, Gradle wrapper'ı 8.7+'ya yükselt; `gradle.properties`'e `android.useAndroidX=true` ve `android.enableJetifier=true` ekle.
  - Tüm `android.support.*` importlarını `androidx.*`'e çevir (appcompat→androidx.appcompat, support.v4.app.Fragment→androidx.fragment.app.Fragment, design.widget→com.google.android.material, constraint→androidx.constraintlayout). Android Studio 'Migrate to AndroidX' + elle düzeltme.
  - compileSdk=35, targetSdk=35, minSdk=24, versionCode/versionName'i bir sonraki sürüme çıkar (parite v3.x).
  - Firebase BoM'a geç: `platform('com.google.firebase:firebase-bom:33.5.1')` + `firebase-firestore`, `firebase-storage`, `firebase-auth`, `firebase-messaging`, `firebase-analytics`, `firebase-appcheck-playintegrity`, `firebase-appcheck-debug` (debug).
  - Google services plugin'i 4.4.2'ye yükselt; google-services.json zaten vifi-831a8 / com.salticusteam.vifi için doğru (storage_bucket=vifi-831a8.appspot.com).
  - Release'te `minifyEnabled true` + proguard; Firestore POJO kullanılacaksa keep kuralları ekle.
- **Gradle/bağımlılık:** platform('com.google.firebase:firebase-bom:33.5.1'); firebase-firestore; firebase-storage; firebase-auth; firebase-appcheck-playintegrity; firebase-appcheck-debug; com.android.tools.build:gradle:8.5.x; google-services:4.4.2
- **Bağımlılık:** TEMEL: tüm diğer arşiv kalemleri buna bağlı, Diğer alanlar (auth, coin, upload, bildirim) da bu temeli paylaşır
- **Tuzaklar:** Bu migrasyon tüm alanların ortak temeli; tek PR'da yapılması diğer alanları bloklar. Jetifier eski Parse/Picasso/barteksc'i geçici köprüler ama uzun vadede bunlar da değişecek (Parse→FCM native, Picasso→Coil, barteksc→PdfRenderer).
- **Kabul kriteri:** Proje AGP 8.x + AndroidX ile derleniyor; targetSdk 35; Firebase BoM ile firebase-firestore ve firebase-appcheck sınıfları çözülüyor; jcenter'a hiçbir bağımlılık kalmadı.

#### `ARCH-01` — Çekirdek arşiv domain modelleri: ArchiveLevel, ArchivePath (id+displayName), ExamKind, ExamSummary, ArchiveItem, ExamDocument  _(P0 · efor: M)_
- **iOS davranışı:** Kanonik-id modeli: ArchivePath `components`(id/slug) + `displayNames` ayrı; eşitlik/hash yalnız id; rename-güvenli. ExamKind JPG=images, PDF=pdf.
- **iOS karşılığı/kaynak:** Core/Models/ArchiveItem.swift, ArchivePath.swift; ArchiveLevel raw value=derinlik (university=1..exam=5).
- **Android mevcut:** Model yok; her şey ArrayList<String> ad listesi ve Bundle extra'ları (uniName/facName...) ile taşınıyor.
- **Android teknoloji:** Kotlin data class, kotlinx.serialization veya Parcelable
- **Adımlar:**
  - `ArchiveLevel` enum: UNIVERSITY(1), FACULTY(2), DEPARTMENT(3), LESSON(4), EXAM(5); `fromDepth(int)` ve `rawDepth` alanı.
  - `ArchivePath` data class: `List<String> components` (stable id'ler), `List<String> displayNames` (aynı uzunluk; mismatch'te components'e düşer). `equals`/`hashCode` YALNIZ components (rename-güvenli). Yardımcılar: `depth`, `isRoot`, `displayName`(=last), `level`(=fromDepth), `childLevel`(=fromDepth(depth+1)), `parent`, `prefix(n)`, `appending(id,displayName)`, `root`.
  - `@Serializable`/Parcelable yap (recents kalıcılığı + fragment args/navigation için). Geriye-uyum: displayNames eksik decode edilirse components'i kullan (iOS Codable init davranışı).
  - `ExamKind` enum: IMAGES("JPG"), PDF("PDF") + `fromRaw(String)`.
  - `ExamSummary(kind, fileCount)`; `ArchiveItem(path, level, childCount:Int?, exam:ExamSummary?)` + `name`(=path.displayName). `ExamDocument(path, kind, fileURLs:List<String/Uri>)` + `title`.
  - Türkçe anlamlı semboller/ikonlar için level→ikon eşlemesi (üniversite/fakülte/bölüm/ders/sınav).
- **Gradle/bağımlılık:** org.jetbrains.kotlin.plugin.serialization + kotlinx-serialization-json:1.7.x (recents JSON için)
- **Bağımlılık:** ARCH-00
- **Tuzaklar:** ArchivePath eşitliğini yalnız components'e dayandır; aksi halde Firestore id'leriyle recents/route eşleşmez. displayNames uzunluğu components ile eşit tutulmalı.
- **Kabul kriteri:** ArchivePath root/child/appending/prefix birim testleri geçiyor; iki farklı displayName ama aynı id eşit sayılıyor; EXAM derinliği 5.

#### `ARCH-02` — ViFiFirestore sağlayıcısı: isimli 'vifi' DB + kalıcı offline önbellek (tek instance)  _(P0 · efor: S)_
- **iOS davranışı:** Tüm uygulama tek Firestore instance; vifi Native DB eur3; PersistentCacheSettings; App Check token otomatik.
- **iOS karşılığı/kaynak:** FirebaseFirestoreProvider.swift: Firestore.firestore(database:"vifi") + FirestoreSettings.cacheSettings = PersistentCacheSettings().
- **Android mevcut:** Sadece FirebaseDatabase.getInstance() (RTDB). Firestore yok.
- **Android teknoloji:** com.google.firebase.firestore.FirebaseFirestore, PersistentCacheSettings, Hilt @Singleton
- **Adımlar:**
  - Tek sorumlu sağlayıcı sınıf/object (ör. `ViFiFirestore`): `FirebaseFirestore.getInstance(FirebaseApp.getInstance(), "vifi")` ile isimli DB'yi al.
  - `FirebaseFirestoreSettings.Builder().setLocalCacheSettings(PersistentCacheSettings.newBuilder().build()).build()` ile kalıcı disk önbelleğini ilk erişimden ÖNCE ayarla (`db.firestoreSettings = ...`).
  - Instance'ı process başına bir kez oluştur (Hilt @Singleton veya object lazy); ayarların iki kez uygulanmasını önle.
  - Bucket'ı FirebaseApp options'tan al: `FirebaseApp.getInstance().options.storageBucket` (StorageURL için).
- **Gradle/bağımlılık:** firebase-firestore (BoM); dagger.hilt (opsiyonel DI)
- **Bağımlılık:** ARCH-00
- **Tuzaklar:** getInstance(app, "vifi") yeni SDK gerektirir (ARCH-00). (default) DB Datastore-mode/US olduğundan ASLA default'a bağlanma — isim 'vifi' zorunlu. Ayarlar ilk sorgudan önce verilmeli yoksa çöker.
- **Kabul kriteri:** Uygulama vifi DB'ye bağlanıyor; cihazda `.../firestore/.../vifi-831a8.vifi/` LevelDB önbelleği oluşuyor; ayar iki kez verilince çökme yok.

#### `ARCH-03` — FirestoreArchiveParser: DocumentSnapshot → model (saf, birim-testlenebilir)  _(P0 · efor: M)_
- **iOS davranışı:** Saf/Firestore-bağımsız dönüştürücü; catalog node→name/parentId/status/childCount; exam→title/courseId/kind/files[{storagePath,order}].
- **iOS karşılığı/kaynak:** FirestoreArchiveParser.swift: displayName(exam=title, diğer=name); fileEntries boş storagePath eler, order'a göre stable sort; examSummary fileCount>0 ister.
- **Android mevcut:** Parser yok; HashMap.get("uniname"/"downloadURL") ile satır-içi, string-leaf atlamalı.
- **Android teknoloji:** Kotlin, saf fonksiyon, JUnit test
- **Adımlar:**
  - Saf sınıf (Firestore import YOK, Map<String,Object> alır) `FirestoreArchiveParser` yaz — birim testlenebilir olsun.
  - Alan sabitleri: name, title, parentId, courseId, status, childCount, kind, files, storagePath, order; `publishedStatus="published"`, `rootParentId=""`.
  - `items(docs: List<Pair<id,Map>>, path): List<ArchiveItem>` — childLevel==EXAM ise examSummary üret (yoksa öğeyi ele? Hayır: iOS'ta summary null ise öğe atlanır; AMA document ekranında files=0 'Dosya bulunamadı' gösterilir — liste parser'ında `examSummary==null` → öğeyi atla kuralını iOS'tan birebir kopyala). Diğer seviyelerde childCount oku (max(0,count)).
  - displayName: EXAM için `title`, diğerleri için `name`; boş/null ise öğeyi atla.
  - `document(data, path, bucket): ExamDocument?` — kind yoksa null; fileEntries boş storagePath'leri eler, `order`(Int) yoksa index ile stable sıralar; URL'ler StorageURL.canonical ile kurulur; url listesi boşsa null.
  - childPath = path.appending(id=docId, displayName=displayName) — Firestore id ≠ ad.
- **Gradle/bağımlılık:** test: junit:4.13.2 / kotlin-test
- **Bağımlılık:** ARCH-01, ARCH-06
- **Tuzaklar:** Firestore Int'leri Long döner; `(order as? Number)?.toInt()`. files elemanları `List<Map<String,Any?>>`. iOS'ta liste parser'ında files=0 olan sınav atlanır AMA document(at:) 'Dosya bulunamadı' gösterir — tutarlılık için liste tarafında da summary==null öğeyi gösterme (iOS davranışı: ArchiveItem oluşturulur ama examSummary null ise return nil → atlanır). Davranışı iOS ile birebir eşle.
- **Kabul kriteri:** Plain Map girdileriyle birim testler: published filtre, boş storagePath elenmesi, order sıralaması, title vs name, childCount max(0,..), kind JPG/PDF parse.

#### `ARCH-04` — FirestoreArchiveRepository: items/document/prefetch + offline semantiği  _(P0 · efor: L)_
- **iOS davranışı:** Seviye = tek sorgu (parentId veya courseId, +status==published); document examId ile; cache-miss isFromCache→offline; prefetch Source.server; archiveChanges pasif (no-op).
- **iOS karşılığı/kaynak:** FirestoreArchiveRepository.swift: catalogDocuments/examDocuments getDocuments; document(at:) getDocument + snapshot.metadata.isFromCache; prefetchPublished getDocuments(source:.server); archiveError mapping.
- **Android mevcut:** Her ekran kendi RTDB addValueEventListener'ı; repository soyutlaması yok; offline yok.
- **Android teknoloji:** FirebaseFirestore Query.get(Source), Task.await(), kotlinx-coroutines, sealed class ArchiveError
- **Adımlar:**
  - `ArchiveRepository` arayüzü: `suspend items(path): List<ArchiveItem>`, `suspend document(path): ExamDocument`, `suspend prefetchArchive()`, `fun archiveChanges(): Flow<Unit>` (pasif → emptyFlow).
  - `FirestoreArchiveRepository(db, bucket)` yaz. items: childLevel==EXAM ise `db.collection("exams").whereEqualTo("courseId", path.components.last).whereEqualTo("status","published")`; değilse `db.collection("catalogNodes").whereEqualTo("parentId", path.isRoot? "" : path.components.last).whereEqualTo("status","published")`; `.get().await()` → docs → FirestoreArchiveParser.items → ARCH-05 ile sırala.
  - document: `db.collection("exams").document(examId).get().await()`; `!snapshot.exists()` ise `snapshot.metadata.isFromCache ? ArchiveError.Offline : ArchiveError.NotFound` (iOS birebir); exists ise parser.document, null→NotFound.
  - prefetchArchive: `db.collection("catalogNodes").whereEqualTo("status","published").get(Source.SERVER).await()` ve aynısını `exams` için; sonuç atılır (yalnız önbellek yan etkisi); hata (offline) yukarı fırlar (prefetcher yutar).
  - Hata eşleme: `FirebaseFirestoreException.code` → PERMISSION_DENIED→PermissionDenied, UNAVAILABLE/DEADLINE_EXCEEDED→Offline, NOT_FOUND→NotFound, diğer→Unknown(message). Logla.
  - Kotlin Coroutines: `kotlinx-coroutines-play-services` ile `Task.await()`; IO dispatcher'da çalıştır.
  - archiveChanges() = `emptyFlow()` (iOS pasif kararı); liste resume + pull-to-refresh ile yenilenir.
- **Gradle/bağımlılık:** org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.x; kotlinx-coroutines-android
- **Bağımlılık:** ARCH-02, ARCH-03, ARCH-05, ARCH-06
- **Tuzaklar:** İki eşitlik filtresi → bileşik index GEREKMEZ (iOS notu). Source.SERVER offline'da exception atar — prefetcher bunu yutmalı, yoksa warm 'tamamlandı' yazılmaz. getDocument offline'da exists()=false + isFromCache=true döner; bunu NotFound ile karıştırma (recents yanlış silinir).
- **Kabul kriteri:** 4 seviye parentId gezinme + sınav listesi çalışıyor; çevrimiçi girişten sonra uçak modunda ziyaret-edilmemiş dal önbellekten geliyor; silinmiş exam NotFound, önbellek-miss Offline veriyor.

#### `ARCH-05` — Türkçe collation sıralama + numeric-aware (katalog artan, sınav ters)  _(P0 · efor: S)_
- **iOS davranışı:** Türkçe alfabetik (Ç>C, Ş>S), numeric-aware ('2. Sınıf'<'10. Sınıf'); sınavlar ters (yeni→eski).
- **iOS karşılığı/kaynak:** ArchiveParser.sorted/compare: lhs.compare(rhs, [.caseInsensitive,.numeric], locale tr_TR); exam → reversed().
- **Android mevcut:** Sıralama yok; Firebase insertion/push-id order.
- **Android teknoloji:** java.text.Collator(Locale tr), özel natural-order comparator
- **Adımlar:**
  - `java.text.Collator.getInstance(new Locale("tr","TR"))` oluştur; `setStrength(Collator.SECONDARY)` (case-insensitive; diacritic/aksan bilinçli AÇIK bırakılır — iOS .diacriticInsensitive KULLANMIYOR).
  - Numeric-aware için ad'ları 'naturel sort' karşılaştırıcıyla işle: sayı bloklarını ayrıştırıp sayısal karşılaştır, metin bloklarını Collator ile ('2. Sınıf'<'10. Sınıf'). Basit yol: regex ile tokenize edip harf blokları Collator, rakam blokları Long karşılaştır.
  - Katalog seviyeleri artan; EXAM seviyesinde sıralı listeyi `reversed()` (yeni→eski).
  - Bu karşılaştırıcıyı FirestoreArchiveParser.items çıktısına uygula (repository'de değil parser'da — iOS gibi).
- **Gradle/bağımlılık:** —
- **Bağımlılık:** ARCH-03
- **Tuzaklar:** Android Collator numeric desteği yoktur; sayı-farkındalığı elle yazılmalı. .diacriticInsensitive EKLEME (iOS bilinçli hariç tuttu: 'Ç'→'C' tutarsızlığı). Sınav ters sıralaması serbest-exam kuralını besler (en eski 2 ücretsiz).
- **Kabul kriteri:** Birim test: ['10. Sınıf','2. Sınıf','Çankaya','Cumhuriyet'] → tr sırası doğru; sınav ['2002 VİZE','2019 FİNAL'] → ['2019 FİNAL','2002 VİZE'].

#### `ARCH-06` — StorageURL: storagePath → kanonik Firebase Storage indirme URL'i  _(P0 · efor: S)_
- **iOS davranışı:** storagePath'ten kanonik URL kurar (download token'a güvenmez); tüm yol tek segment percent-encode (/ → %2F), ?alt=media.
- **iOS karşılığı/kaynak:** StorageURL.swift: https://firebasestorage.googleapis.com/v0/b/<bucket>/o/<encodedPath>?alt=media; allowed set = RFC3986 unreserved (A-Za-z0-9-._~).
- **Android mevcut:** storagePath yok; RTDB'de hazır downloadURL string'leri saklanıyor ve doğrudan kullanılıyor.
- **Android teknoloji:** Kotlin, elle percent-encoding (RFC3986 unreserved)
- **Adımlar:**
  - Saf util `StorageUrl.canonical(storagePath: String, bucket: String): String?` yaz.
  - storagePath veya bucket boşsa null döndür.
  - Tüm storagePath'i TEK segment olarak percent-encode et: izinli karakterler yalnız `A-Za-z0-9-._~`; '/' dahil her şey %2F/%20/UTF-8 olarak kaçışlanır. `java.net.URLEncoder` KULLANMA (boşluğu + yapar, / kaçışlamaz); elle byte-byte percent-encode yaz veya `Uri.encode(path, null)` ile allowed=null verip sonra kontrol et.
  - Sonuç: `https://firebasestorage.googleapis.com/v0/b/{bucket}/o/{encoded}?alt=media`.
  - Birim testlerle iOS StorageURL testlerini birebir kopyala (ör. `imagess/<uuid>.jpg`, boşluklu ad, Türkçe karakter).
- **Gradle/bağımlılık:** —
- **Bağımlılık:** ARCH-01
- **Tuzaklar:** URLEncoder.encode form-encoding yapar (boşluk→'+', '/'→korunmaz) → YANLIŞ. '/' mutlaka %2F olmalı (Firebase object adı tek segment). bucket = vifi-831a8.appspot.com. Host RemoteFileLoader ile aynı (firebasestorage.googleapis.com).
- **Kabul kriteri:** Birim test: 'imagess/a b.jpg' → '.../o/imagess%2Fa%20b.jpg?alt=media'; Türkçe karakterli yol UTF-8 percent-encode; boş girdi null.

#### `ARCH-07` — İstek yetkilendirici: ID token (Authorization: Firebase) + App Check (X-Firebase-AppCheck) başlıkları  _(P0 · efor: M)_
- **iOS davranışı:** Storage SDK gibi: ID token 'Authorization: Firebase <token>', App Check 'X-Firebase-AppCheck'; yalnız app bucket'ına kimlik gönderir; 401/403'te ID token zorla yenile.
- **iOS karşılığı/kaynak:** FirebaseRequestAuthorizer.swift: auth.currentUser.getIDToken(forcingRefresh:); appCheck.token(forcingRefresh:false); isObject(url,inBucket) kontrolü.
- **Android mevcut:** Yok; Picasso/HttpURLConnection token'lı public URL'i doğrudan indiriyor, başlık eklemiyor.
- **Android teknoloji:** FirebaseAuth.getIdToken, FirebaseAppCheck.getAppCheckToken, OkHttp Headers
- **Adımlar:**
  - `RequestAuthorizer` arayüzü: `suspend authorizedRequest(url, forceRefresh): Request/Headers`.
  - URL'in app bucket'ında `/v0/b/<bucket>/o/` ile başladığını doğrula; değilse PermissionDenied (başka bucket'a token gönderme — iOS güvenlik).
  - `FirebaseAuth.getInstance().currentUser` null ise PermissionDenied. `user.getIdToken(forceRefresh).await().token` al; hata ağ ise Offline değilse PermissionDenied.
  - App Check token: `FirebaseAppCheck.getInstance().getAppCheckToken(false).await().token` (hata/null ise başlıksız gönder, iOS gibi yut ve logla — zorla yenileme YAPMA, attestation kotası).
  - Başlıklar: `Authorization: Firebase <idToken>`, varsa `X-Firebase-AppCheck: <appCheckToken>`.
  - Bu yetkilendiriciyi ARCH-08 (RemoteFileLoader) içine enjekte et.
- **Gradle/bağımlılık:** firebase-auth, firebase-appcheck (BoM)
- **Bağımlılık:** ARCH-00, ARCH-11
- **Tuzaklar:** App Check token'ı force-refresh ETME (her zorlama bir Play Integrity round-trip + kota). 401/403'te yalnız ID token'ı yenile (ARCH-08 retry). Yalnız kendi bucket'ına kimlik gönder.
- **Kabul kriteri:** İndirme isteği Authorization + X-Firebase-AppCheck başlıkları taşıyor; oturum yokken PermissionDenied; farklı bucket URL'i reddediliyor.

#### `ARCH-08` — RemoteFileLoader karşılığı: kimlik-doğrulamalı dosya indirme + disk/bellek önbelleği + 401/403 retry + görsel downsample + PDF yerel kopya  _(P0 · efor: XL)_
- **iOS davranışı:** Dosyayı bir kez indirir, bellek+disk önbellekten servis eder; kanonik URL ile kimlikler (token= query atılır); 401/403'te ID token yenileyip bir kez retry; eşzamanlı aynı-URL indirmesi paylaşılır; görsel off-main decode+downsample; PDF yerel dosyaya yazılır; prune(bütçe)+purge(hesap silme).
- **iOS karşılığı/kaynak:** RemoteFileLoader.swift: URLCache + NSCache; canonicalURL(token drop); authorizedRequest; fetch 2xx kontrol; CGImageSource downsample; localFileURL=sha256(url)/sanitizedName.
- **Android mevcut:** ImageZoomActivity `Picasso.get().load(downloadURL)`; ExamDetailPdfActivity ham `HttpURLConnection` ile stream; auth/App Check/önbellek/prune yok; enforce sonrası 403 olur.
- **Android teknoloji:** OkHttp 4.12 (Cache + Interceptor), Coil 2.6 (image) veya elle BitmapFactory inSampleSize, android.graphics.pdf.PdfRenderer (PDF render — Exam alanı), kotlinx-coroutines
- **Adımlar:**
  - `RemoteFileLoader` sınıfı (OkHttpClient + Cache). OkHttp Cache dizini app cache (`context.cacheDir/ExamFiles/Responses`), ~512MB disk / ~16MB bellek benzeri bütçe.
  - `canonicalUrl(url)`: Firebase Storage URL'inde `token` query parametresini at (alt=media ve diğerlerini koru) — önbellek/indirme kimliği bu. iOS canonicalURL birebir.
  - OkHttp Interceptor ile her Storage isteğine ARCH-07 başlıklarını ekle; önbelleği kanonik URL (token'sız) ile anahtarla.
  - `suspend fun image(url, maxPixelSize): Bitmap` — Coil `ImageLoader` + `ImageRequest` ile OkHttp'yi paylaştır; `size(maxPixelSize)` ile downsample; bellek+disk önbelleği Coil yönetir. Alternatif: elle `BitmapFactory` `inSampleSize` hesapla (off-main).
  - `suspend fun localFile(url, fileName): File` — PDF için indir, `cacheDir/ExamFiles/Files/<sha256(url)>/<sanitizedName>` altına atomik yaz; varsa yeniden indirme; iOS sanitizedFileName (yasak karakter → '-', 100-char base limiti) ve sha256 klasör şemasını kopyala.
  - 2xx olmayan yanıt → FileUnavailable(statusCode); 401/403 → ID token forceRefresh ile BİR kez retry (ARCH-07), yine 401/403 → PermissionDenied. Ağ hataları → Offline.
  - Eşzamanlı aynı-URL indirmesini tek coroutine/Deferred ile paylaş (iOS downloads[url]).
  - `pruneDiskCache()` (bütçe aşınca en eski klasörleri sil, son 60sn dokunulanları koru) ve `purge()` (hesap silme: tüm indirilmiş dosya/önbellek/bitmap temizle).
  - ExamDetail/ImageZoom/PDF görüntüleyicileri bu loader'ı kullanacak şekilde bağla (render Exam alanı; indirme burada).
- **Gradle/bağımlılık:** com.squareup.okhttp3:okhttp:4.12.0; io.coil-kt:coil:2.6.0 (+coil-base, OkHttp paylaşımı); (Picasso KALDIRILIR)
- **Bağımlılık:** ARCH-06, ARCH-07, ARCH-00
- **Tuzaklar:** Storage App Check enforce sonrası plain Picasso/HttpURLConnection 403 alır — bu kalem olmadan görüntüleyici bozulur. token= query'sini at yoksa önbellek aynı dosyayı farklı token'larla mükerrer tutar. PdfRenderer ParcelFileDescriptor ister → PDF'i önce yerel dosyaya indir (stream ile render etme). Coil minSdk 21+. Büyük PDF için resource timeout'u cömert tut.
- **Kabul kriteri:** Görsel sınav App Check enforce'ta ID token+App Check başlığıyla iniyor ve downsample ediliyor; aynı sınav ikinci açılışta ağ olmadan (önbellekten) açılıyor; PDF yerel dosyaya inip açılıyor; 401 sonrası token yenilenip retry başarılı; hesap silmede purge dosyaları siliyor.

#### `ARCH-09` — ArchivePrefetcher: giriş-sonrası tam-arşiv offline warm (throttle 12s/12h, best-effort, in-flight guard)  _(P1 · efor: M)_
- **iOS davranışı:** Giriş sonrası tüm published catalogNodes+exams Source.server ile çekilip Firestore disk önbelleğine yazılır; başarılı warm 12 saat tekrarlanmaz; hata (offline) yutulur, sonraki açılışta retry; sign-out'ta iptal; in-flight guard.
- **iOS karşılığı/kaynak:** ArchivePrefetcher.swift: prefetchIfNeeded() isStale kontrolü (UserDefaults lastPrefetchKey), repository.prefetchArchive(); RootView .task tetikler.
- **Android mevcut:** Offline yok; her ekran canlı RTDB okur, uçak modunda boş.
- **Android teknoloji:** androidx.datastore (timestamp), kotlinx-coroutines, (ops. WorkManager)
- **Adımlar:**
  - `ArchivePrefetcher(repository, dataStore, clock)` sınıfı: `suspend prefetchIfNeeded()`.
  - Bayatlık: DataStore'da `archivePrefetchedAt` (epoch) oku; `now - last >= throttle(12h)` veya hiç yoksa stale. in-flight guard (AtomicBoolean/Mutex).
  - stale + çalışmıyor ise `repository.prefetchArchive()` çağır; başarıda timestamp yaz; `CancellationException` → timestamp YAZMA (sign-out); diğer hata → yut + logla (sonraki açılış retry).
  - Tetikleme: Kullanıcı profil tamamlayıp arşive ulaştığında (RootView .task karşılığı) — Home ekranı açılışında `lifecycleScope`/ViewModel'de çağır; sign-out'ta scope iptal olmalı. (WorkManager alternatifi: expedited one-time job; ama iOS yaşam-döngüsü-bağlı kullanıyor — openQuestions).
  - Mock/test için izole DataStore (ayrı dosya adı).
- **Gradle/bağımlılık:** androidx.datastore:datastore-preferences:1.1.1; androidx.work:work-runtime-ktx:2.9.1 (WorkManager seçilirse)
- **Bağımlılık:** ARCH-04
- **Tuzaklar:** Source.SERVER offline'da exception atar — bunu yut, timestamp yazma (yoksa warm tamamlanmadan 'tamamlandı' işaretlenir; iOS kalıntı notu). Arşiv ~344KB, küçük; 12 saatte bir çekmek ucuz. Sign-out'ta iptal edilmeli; coroutine scope auth durumuna bağlanmalı.
- **Kabul kriteri:** Çevrimiçi girişten sonra warm çalışıp uçak modunda ziyaret-edilmemiş dal önbellekten geliyor; 12 saat içinde ikinci açılışta tekrar çekmiyor; offline açılışta warm sessizce atlanıyor.

#### `ARCH-10` — RecentExamsStore: son görüntülenen sınavlar (DataStore, max 10, şema versiyonu, en öne taşıma)  _(P1 · efor: M)_
- **iOS davranışı:** Açılan sınavları kalıcı sakla (max 10); record en öne taşır (aynı path varsa çıkarıp başa ekler); remove/clear; şema versiyon anahtarı (uyumsuzlukta boş başla); decode hatasında boş.
- **iOS karşılığı/kaynak:** RecentExamsStore.swift: UserDefaults 'recentExams.v1' JSON; RecentExam{path,kind,openedAt}; subtitle 'Üniversite › Ders'.
- **Android mevcut:** Son görüntülenenler özelliği YOK.
- **Android teknoloji:** androidx.datastore + kotlinx.serialization, StateFlow
- **Adımlar:**
  - `RecentExam(path: ArchivePath, kind: ExamKind, openedAt: Long)` + `title`(=path.displayName), `subtitle`(=ilk displayName + son-bir-önceki displayName, ' › ' ile).
  - `RecentExamsStore` (DataStore Preferences veya tek JSON dosyası, kotlinx.serialization). Anahtar `recentExams.v1` (şema değişince suffix artır → eski yok sayılır).
  - `record(path, kind, at=now)`: listeyi aynı path'ten temizle, başa ekle, ilk 10'a kırp, kaydet. `remove(path)`/`remove(exam)`/`clear()`. Değişiklik yoksa yazma.
  - Yükleme: decode hatasında boş liste (bozuk blob'u taşıma — iOS davranışı).
  - Durumu Flow/StateFlow ile yayınla (Home ekranı gözlemler).
- **Gradle/bağımlılık:** androidx.datastore:datastore-preferences:1.1.1; kotlinx-serialization-json:1.7.x
- **Bağımlılık:** ARCH-01
- **Tuzaklar:** path ArchivePath olarak serialize edilmeli (id + displayNames). Göç-öncesi eski RTDB-ad-tabanlı recents olmayacak (yeni özellik) ama ileride şema değişirse versiyon suffix mantığı korunmalı. openedAt relative gösterim (DateUtils.getRelativeTimeSpanString).
- **Kabul kriteri:** Sınav açınca recents başına geliyor; max 10; aynı sınav tekrar açılınca başa taşınıyor; temizle boşaltıyor; bozuk kayıt boş liste veriyor; süreç yeniden başlatında kalıcı.

#### `ARCH-11` — App Check: Play Integrity provider (+ debug provider) kurulumu  _(P0 · efor: M)_
- **iOS davranışı:** App Check App Attest/DeviceCheck; Storage/RTDB/Auth enforce; Firestore monitoring→enforce.
- **iOS karşılığı/kaynak:** ViFiAppCheckProviderFactory.swift; AppDelegate'te install.
- **Android mevcut:** App Check YOK; enforce açılınca tüm Storage/Firestore/RTDB okumaları 403.
- **Android teknoloji:** com.google.firebase.appcheck.FirebaseAppCheck, PlayIntegrityAppCheckProviderFactory, DebugAppCheckProviderFactory
- **Adımlar:**
  - Application.onCreate'te FirebaseApp init'ten SONRA: release → `FirebaseAppCheck.getInstance().installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())`.
  - Debug/emulator build → `DebugAppCheckProviderFactory.getInstance()`; Logcat'teki debug token'ı Firebase Console → App Check → Debug tokens'a ekle (yoksa enforce'ta okuma 403).
  - Play Console'da uygulamayı App Check'e kaydet; imza SHA-256'ları ekle; Google Cloud'da Play Integrity API'yi etkinleştir.
  - BuildConfig.DEBUG ile provider seçimini ayır (debug factory release'e sızmamalı).
  - Enforce sırası: önce Firestore-okuyan sürümü yayınla → Console App Check monitoring'de 'verified' trafiği gör → SONRA Storage/Firestore enforce (kullanıcı adımı; erken enforce tüm kullanıcıları kilitler).
- **Gradle/bağımlılık:** firebase-appcheck-playintegrity; firebase-appcheck-debug (debugImplementation) (BoM)
- **Bağımlılık:** ARCH-00
- **Tuzaklar:** Debug token Console'a eklenmezse geliştirme sırasında Storage/Firestore 403. Enforce'u sürüm sahada 'verified' GÖRÜLMEDEN açma (iOS cutover notu). Play Integrity gerçek cihaz/Play dağıtımı ister; CI/emulator debug provider kullanır.
- **Kabul kriteri:** Debug build debug-token ile okuyor; release build Play Integrity ile App Check token üretiyor; Console monitoring'de verified trafik görünüyor; ARCH-07 başlıkları geçerli App Check token taşıyor.

#### `ARCH-12` — Gezinme altyapısı: Router/NavGraph (browse/exam rotaları, popToRoot, popTo-breadcrumb, openExam ata yığını)  _(P0 · efor: L)_
- **iOS davranışı:** NavigationStack + Route(browse(path)/exam(path,kind)); popToRoot; popTo(ancestor) breadcrumb; openExam atalarını yığına koyar (Back arşivi tırmanır).
- **iOS karşılığı/kaynak:** Router.swift: path:[Route]; popTo prefix; openExam = ancestors(1..<depth) + exam.
- **Android mevcut:** Fragment replace + addToBackStack + Bundle extra'ları; CourseExamListActivity/ExamDetail ayrı Activity'ler; breadcrumb/deeplink yok.
- **Android teknoloji:** androidx.navigation:navigation-compose veya navigation-fragment-ktx; Safe Args
- **Adımlar:**
  - Tercih: Jetpack Compose + Navigation-Compose. Tek `NavHost`; rotalar `home` (root=üniversite listesi), `browse/{pathArg}` (ArchivePath serialize), `exam/{pathArg}/{kind}`.
  - ArchivePath'i rota argümanı olarak taşı (kotlinx.serialization JSON + Uri.encode veya `SavedStateHandle`/typed nav). displayNames'i de taşı (breadcrumb için).
  - `popToRoot()` = `navController.popBackStack(home, inclusive=false)`. Breadcrumb `popTo(ancestor)` = yığını ata derinliğine kırp (prefix(index)); Navigation'da hedef rotaya `popUpTo` ile dön.
  - `openExam(path, kind)`: önce ataları (browse prefix 1..depth-1) yığına it, sonra exam — Back arşivi tırmanır. (iOS openExam birebir; Navigation'da ara destinasyonları ekleyerek veya back-stack manipülasyonu ile.)
  - Alternatif (Compose istenmezse): tek Activity + Fragment + `androidx.navigation` (nav graph XML) + ViewModel; Bundle yerine typed Safe Args; aynı rota mantığı.
  - Toolbar 'Ana Sayfa' aksiyonu popToRoot; sistem Back doğal yığın.
- **Gradle/bağımlılık:** androidx.navigation:navigation-compose:2.7.7 (Compose) VEYA navigation-fragment-ktx:2.7.7 + Safe Args plugin
- **Bağımlılık:** ARCH-01
- **Tuzaklar:** ArchivePath rota argümanında Türkçe/boşluklu displayName var → Uri encode zorunlu. openExam'da ara browse destinasyonlarını yığına koymak Navigation-Compose'da elle back-stack kurmayı gerektirir. Compose'a geçiş tüm UI alanlarının ortak kararı (openQuestions).
- **Kabul kriteri:** Üniversite→...→sınav drill-down çalışıyor; breadcrumb'tan ataya dönüş yığını doğru kırpıyor; sınav son görüntülenenlerden açılınca Back arşivi tırmanıyor; 'Ana Sayfa' köke dönüyor.

#### `ARCH-13` — BrowseViewModel: Loadable durum + load/refresh/remove + Türkçe arama katlaması  _(P0 · efor: M)_
- **iOS davranışı:** Loadable<[ArchiveItem]> (loading/loaded/failed); load (loading göster), refresh (öğeler ekranda kalır, boşsa error'a döner), remove (admin silme sonrası), isFiltering/visibleItems (arama), cancellation.
- **iOS karşılığı/kaynak:** BrowseViewModel.swift: searchKey(tr lowercase, ı→i, diacritic fold); isFetching guard; refreshOnArchiveChanges.
- **Android mevcut:** ViewModel yok; her Fragment kendi listener+ArrayList; durum/arama/refresh yok.
- **Android teknoloji:** androidx.lifecycle ViewModel + StateFlow, java.text.Normalizer, Locale("tr")
- **Adımlar:**
  - `BrowseViewModel(path, repository)` (AndroidX ViewModel + SavedState). `Loadable` sealed class: Loading/Loaded(items)/Failed(message). `StateFlow<Loadable>`.
  - `load()`: isFetching guard; state=Loading; `repository.items(path)` → Loaded; iptal ise Loading'te kal (ekran dönüşünde retry); hata → Failed(userMessage).
  - `refresh()` (pull-to-refresh): öğeler ekranda kalır; hata'da yalnız `state.value==null` ise Failed; aksi halde mevcut liste korunur.
  - `remove(item)`: admin silme sonrası listeden çıkar (refetch önbellekten silineni getirebilir).
  - Arama: `searchText` StateFlow; `visibleItems` = ad'ı aramaya uyanlar; `searchKey(text)` = `text.lowercase(Locale("tr")).replace('ı','i')` + aksan katlama (`java.text.Normalizer.normalize(NFD)` ile combining marks sil). iOS searchKey birebir.
  - `refreshOnArchiveChanges()` = repository.archiveChanges() (pasif → akmaz); resume'da/`onStart`'ta load-if-needed.
- **Gradle/bağımlılık:** androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.x
- **Bağımlılık:** ARCH-04
- **Tuzaklar:** Türkçe arama: sadece lowercase(tr) yetmez; 'İ'→'I' sonra tr casing 'ı' ile eşler → iOS gibi ı→i replace + aksan katlama şart ('isletme' → 'İŞLETME'). Normalizer NFD + \p{Mn} silme aksanları katlar. Cancellation (CoroutineScope iptali) Failed'a düşürmemeli.
- **Kabul kriteri:** 'kocaeli' → 'KOCAELİ', 'dumlupinar' → 'DUMLUPINAR', 'isletme' → 'İŞLETME' buluyor; pull-to-refresh hatada listeyi koruyor; boş ekran hatada error gösteriyor.

#### `ARCH-14` — Browse ekranı UI: liste + durum katmanları + admin swipe-sil + pull-to-refresh + arama + 'Ana Sayfa'  _(P0 · efor: L)_
- **iOS davranışı:** Breadcrumb + bölüm başlığı (seviye + sayı); loading/error(retry)/empty(seviyeye özel)/search-empty; admin exam'de swipe-sil + onay dialogu; refreshable; searchable; toolbar 'Ana Sayfa' + CoinBalanceBadge; kilitli sınavda unlock sheet (coin alanı).
- **iOS karşılığı/kaynak:** BrowseView.swift: List+Section; stateOverlay; swipeActions admin; emptyListTitle/searchPrompt seviyeye göre.
- **Android mevcut:** ListView + ListViewAdapter; progress bar; durum ekranları/arama/refresh/swipe yok; reklam gate'li.
- **Android teknoloji:** Jetpack Compose (LazyColumn, PullToRefreshBox, SwipeToDismissBox) veya RecyclerView+SwipeRefreshLayout+ItemTouchHelper
- **Adımlar:**
  - Compose `LazyColumn` (veya RecyclerView): üstte Breadcrumb (ARCH-15), altında bölüm başlığı `seviye.listTitle` + öğe sayısı.
  - Durumlar: Loading (indicator), Failed (mesaj + 'Tekrar dene' → load), Loaded&boş (seviyeye özel boş başlık + 'Yeni içerik eklendiğinde burada görünecek.'), arama-boş ('Sonuç yok').
  - Seviyeye özel Türkçe metinler (iOS birebir): searchPrompt ('Üniversite ara'/'Fakültelerde ara'/.../'Sınavlarda ara'), emptyListTitle ('Henüz üniversite yok'/'Bu üniversitede henüz fakülte yok'/...).
  - Pull-to-refresh: `PullToRefreshBox` (Compose) veya `SwipeRefreshLayout` → viewModel.refresh().
  - Arama: `SearchBar`/`TextField` → viewModel.searchText; klavye dismiss.
  - Admin exam satırında swipe-to-delete (`SwipeToDismiss`) → onay dialogu ('Sınav silinsin mi? ... geri alınamaz') → moderationService.deleteExam (moderasyon alanı) → viewModel.remove; hata → 'Silinemedi' dialog. Admin kontrolü auth alanından (isCurrentUserAdmin).
  - Toolbar: 'Ana Sayfa' (popToRoot) + CoinBalanceBadge (coin alanı).
  - Kilitli sınav satırına tık → unlock sheet (coin alanı); kilitli değilse route'a git.
- **Gradle/bağımlılık:** androidx.compose.material3; androidx.compose.material:material-icons-extended VEYA androidx.swiperefreshlayout + recyclerview
- **Bağımlılık:** ARCH-13, ARCH-15, ARCH-16, ARCH-12
- **Tuzaklar:** Admin swipe-sil ve unlock sheet başka alanların servislerine (moderation/coin) bağlı — arayüz burada, servis orada; stub/arayüz ile ilerle. Seviye-özel Türkçe kopya iOS ile birebir eşlenmeli. Reklam-gate tamamen kaldırılır (ARCH-20).
- **Kabul kriteri:** 4 seviyede liste + sayı başlığı görünüyor; loading/error/empty/search-empty doğru; pull-to-refresh çalışıyor; admin exam swipe-sil onay+hata akışı; kilitli sınav tık unlock sheet'i açıyor (coin alanı entegre olunca).

#### `ARCH-15` — BreadcrumbBar: yatay kaydırmalı konum izi (prefix chip'leri, ataya dönüş)  _(P1 · efor: M)_
- **iOS davranışı:** Yatay kaydırmalı iz 'Üniversiteler › … › current'; chip i = prefix(i), chip 0='Üniversiteler'; son chip seçili+tıklanamaz; tık ataya döner; sona kaydırılmış başlar; displayNames gösterir.
- **iOS karşılığı/kaynak:** BreadcrumbBar.swift: ForEach(0...depth); chip(at:) isCurrent; onSelect(path.prefix(index)).
- **Android mevcut:** Breadcrumb yok; konum bilgisi Bundle extra'larında gizli.
- **Android teknoloji:** Compose LazyRow + rememberLazyListState veya HorizontalScrollView
- **Adımlar:**
  - Yatay kaydırılabilir satır (Compose `LazyRow` / `Row` + `horizontalScroll`, veya HorizontalScrollView).
  - `0..depth` arası chip; aralara 'chevron' ikonu. index 0 başlık 'Üniversiteler' (+ üniversite ikonu); index>0 → `path.displayNames[index-1]`.
  - Son chip (index==depth) seçili: vurgulu kapsül, tıklanamaz. Diğerleri buton → `onSelect(path.prefix(index))` → Router.popTo.
  - Başlangıçta sona kaydır (son chip görünür) — `LazyListState` ile `scrollToItem(last)` ilk kompozisyonda.
  - Kapsül stil: seçili accent arka plan + kenarlık; diğeri nötr.
- **Gradle/bağımlılık:** —
- **Bağımlılık:** ARCH-01, ARCH-12
- **Tuzaklar:** displayNames gösterilir (components/id değil). Uzun Türkçe adlar tek satır ellipsize. Sona kaydırma ilk layout'tan sonra yapılmalı (iOS defaultScrollAnchor(.trailing) karşılığı).
- **Kabul kriteri:** Her seviyede iz doğru; 'Üniversiteler' kökü gösteriyor; ara chip'e tık o seviyeye dönüyor; son chip tıklanamaz; uzun izlerde sona kaydırılmış açılıyor.

#### `ARCH-16` — ArchiveRow / navigasyon satırı: ikon + ad + alt-başlık + kind/kilit rozeti + 'Dosya bulunamadı' dimmed satır  _(P1 · efor: M)_
- **iOS davranışı:** İkon rozeti + ad (2 satır) + alt-başlık (childCount metni / kind içerik metni / 'Dosya bulunamadı'); sınav kind rozeti ('Görsel'/'PDF'); kilitli sınavda kilit+maliyet rozeti; dosyasız sınav dimmed+disabled; navigasyon vs locked-tap vs disabled.
- **iOS karşılığı/kaynak:** ArchiveRow.swift + ArchiveNavigationRow.swift: symbolName; subtitle; LockBadge/ExamKindBadge; route (exam→exam(path,kind), diğer→browse).
- **Android mevcut:** ListViewAdapter tek satır metni; alt-başlık/rozet/kilit/dimmed yok; ilk 3 sınav 'reklamı izleyiniz' metni.
- **Android teknoloji:** Compose Row/Card veya RecyclerView ViewHolder
- **Adımlar:**
  - Satır composable/ViewHolder: sol ikon (seviye/kind sembolü), ad (en çok 2 satır, erişilebilirlik boyutunda tam), alt-başlık, sağ rozet.
  - Alt-başlık: sınavda `kind.contentText(fileCount)` ('3 sayfa'/'1 PDF' gibi — iOS metinlerini birebir); sınav ama exam==null → 'Dosya bulunamadı'; diğer seviyelerde `childCountText(childCount)` ('12 fakülte' vb.).
  - Rozet: kilitli sınav → kilit ikonu + maliyet (ExamLock.unlockCost=100, coin alanı); değilse kind rozeti ('Görsel'/'PDF').
  - Dosyasız sınav (exam==null): ikon+ad ikincil renk (dimmed), satır disabled (tıklanamaz) ama erişilebilirlik 'var ama açılamaz' der.
  - Tık yönlendirme: kilitli → onLockedTap (unlock sheet, coin alanı); route varsa → Router.push (exam→exam viewer, diğer→browse); route yoksa (dosyasız) → disabled.
  - Türkçe kind metinleri/badge iOS ile birebir: kind.badge ('Görsel'/'PDF'), symbolName.
- **Gradle/bağımlılık:** —
- **Bağımlılık:** ARCH-01, ARCH-14
- **Tuzaklar:** files=0 sınav liste parser'ında atlanıyorsa (iOS summary==null→nil) bu satır hiç gelmez; ama iOS ArchiveRow 'Dosya bulunamadı' durumunu da çiziyor — davranışı ARCH-03 ile hizala (iOS liste parser'ı dosyasız sınavı atlar, yani pratikte dimmed satır yalnız mock/edge). Kilit rozeti coin alanına bağlı; arayüzü hazırla.
- **Kabul kriteri:** Üniversite/fakülte satırı childCount alt-başlık; sınav satırı kind rozeti + sayfa sayısı; kilitli sınav kilit+100 rozeti; dosyasız sınav dimmed+disabled; tık doğru hedefe gidiyor.

#### `ARCH-17` — Home ekranı: kök üniversite listesi + hero başlık + 'Sınav Ekle' CTA + son görüntülenenler rafı  _(P1 · efor: L)_
- **iOS davranışı:** Kök ekran = üniversite listesi (path==root, BrowseViewModel ile); hero (logo+slogan); 'Sınav Ekle' CTA (coin kazan); son görüntülenenler rafı; arama; refreshable.
- **iOS karşılığı/kaynak:** HomeView.swift: HomeScreen(path:.root); HomeHeroHeader; addExamSection; RecentExamsSection; universitiesSection.
- **Android mevcut:** HomeActivity: UniversityFragment (RTDB üniversite ListView) + banner reklam + drawer; recents/hero/CTA yok.
- **Android teknoloji:** Compose veya Fragment; aynı BrowseViewModel
- **Adımlar:**
  - Home ekranı `BrowseViewModel(path=ArchivePath.root)` ile üniversiteleri listeler (aynı VM/UI, path kök).
  - Hero başlık: logo + 'Sınav Arşivi' + 'Üniversitelerin geçmiş sınavlarına hızlıca ulaş.' (iOS metinleri).
  - 'Sınav Ekle' CTA bölümü → AddExam akışı (upload alanı); 'Arşive sınav ekle, onaylanınca coin kazan.' alt-metin.
  - Son görüntülenenler rafı (recents boş değilse, ARCH-18).
  - Üniversiteler bölümü başlığı + sayı; loading/error/empty aynı durum katmanları.
  - Arama aktifken yalnız eşleşen üniversiteler (hero/CTA/recents gizlenir — iOS isFiltering).
  - Toolbar: CoinBalanceBadge (coin), bildirim zili (bildirim alanı), Hakkında, Hesap. Pull-to-refresh.
  - Mevcut drawer/BottomNavigationView'i iOS tek-stack gezinmeye göre sadeleştir (parite: drawer yok).
- **Gradle/bağımlılık:** —
- **Bağımlılık:** ARCH-13, ARCH-16, ARCH-18
- **Tuzaklar:** Home ve Browse aynı liste/VM'i paylaşır (iOS BrowseViewModel her ikisini sürer). Bottom nav/drawer iOS'ta yok; parite için kaldır ama bu Home/Shell alanıyla koordine. Banner reklam kaldırılır (ARCH-20).
- **Kabul kriteri:** Home üniversiteleri listeliyor; hero+CTA+recents görünüyor; arama hero/CTA/recents'i gizleyip üniversiteleri filtreliyor; pull-to-refresh çalışıyor.

#### `ARCH-18` — Son görüntülenenler rafı + sınav açılışında kayıt + NotFound'da silme  _(P1 · efor: M)_
- **iOS davranışı:** Yatay kart rafı 'Son Görüntülenenler'; kart = kind/ad/konum/açılma zamanı; uzun-bas menü 'Listeden Kaldır'; 'Temizle' (onaylı). Sınav ilk başarılı yüklemede recents'e yazılır (bir kez); kesin NotFound'da silinir (offline/permission'da kalır).
- **iOS karşılığı/kaynak:** RecentExamsSection.swift + ExamDocumentViewModel.recordVisitIfNeeded / handleLoadFailure (notFound→remove).
- **Android mevcut:** Yok.
- **Android teknoloji:** Compose LazyRow, DateUtils.getRelativeTimeSpanString
- **Adımlar:**
  - Yatay kart rafı (Compose `LazyRow`): her kart kind ikonu+rozet, sınav adı (2 satır), alt-başlık (konum ' › '), açılma zamanı (relative).
  - Kart uzun-bas/context menü → 'Listeden Kaldır' (store.remove). Raf başlığında 'Temizle' → onay dialogu ('Son görüntülenenler temizlensin mi? ... yalnızca bu liste boşaltılır.').
  - Kart tık → Router.openExam(path, kind) (ata yığını).
  - Sınav görüntüleyici VM'inde (ExamDocumentViewModel karşılığı, Exam alanı) ilk başarılı yüklemede `recents.record(path, kind)` (bir kez, hasRecordedVisit guard) + analytics examOpened.
  - Yükleme hatası: yalnız kesin NotFound'da `recents.remove(path)` (offline/permission/server hatasında KALIR). Göç-öncesi/silinmiş kart zarif 'İçerik bulunamadı' + otomatik silme, çökme yok.
  - Store Flow'unu Home gözlemler (ARCH-17).
- **Gradle/bağımlılık:** —
- **Bağımlılık:** ARCH-10, ARCH-12
- **Tuzaklar:** record/remove tetikleyicisi sınav görüntüleyici (Exam alanı) içinde; bu kalem store+raf+tetik sözleşmesini tanımlar, Exam alanı çağırır. NotFound ile Offline'ı karıştırma (ARCH-04 isFromCache) yoksa offline'da recents yanlış silinir.
- **Kabul kriteri:** Sınav açınca recents rafında görünüyor; kart konum+zaman gösteriyor; 'Listeden Kaldır' tek kartı, 'Temizle' hepsini (onaylı) siliyor; silinmiş sınav açılınca kart otomatik kalkıyor, çökme yok.

#### `ARCH-20` — AdMob'u arşiv/home/sınav-liste ekranlarından kaldır + reklam-ile-erişim gate'ini sök  _(P0 · efor: M)_
- **iOS davranışı:** iOS'ta HİÇ reklam yok; monetizasyon coin ekonomisi.
- **iOS karşılığı/kaynak:** —
- **Android mevcut:** CourseExamListActivity ilk 3 sınavı 'reklamı izleyiniz' yapıp RewardedVideoAd/InterstitialAd; Home/Main/ExamDetail/ExamDetailPdf/ImageZoom banner (AdView); MainActivity interstitial.
- **Android teknoloji:** —
- **Adımlar:**
  - CourseExamListActivity'deki tüm reklam mantığını kaldır: MobileAds.initialize, RewardedVideoAd, InterstitialAd, `rewardedAdMethod`, `loadRewardedVideoAd`, RewardedVideoAdListener implementasyonu, 'Görmek için lütfen reklamı izleyiniz.' ile ilk-3-sınav maskeleme. Sınav listesi doğrudan gösterilir (kilit/coin modeli coin alanında).
  - HomeActivity/ExamDetailActivity/ExamDetailPdfActivity/ImageZoomActivity/MainActivity'den AdView banner + AdRequest + MobileAds.initialize çağrılarını kaldır; ilgili layout'lardan `<com.google.android.gms.ads.AdView>` öğelerini sil.
  - MainActivity interstitial (mInterstitialAd) ve onLoginButtonClick içindeki reklam-koşullu gezinmeyi kaldır.
  - AndroidManifest'ten `com.google.android.gms.ads.APPLICATION_ID` meta-data'sını kaldır.
  - build.gradle'den `com.google.android.gms:play-services-ads` bağımlılığını kaldır.
  - Eski 'ilk 3 ücretsiz, gerisi reklam' kuralı → iOS ExamLock (en eski 2 ücretsiz, gerisi 100 coin) ile değişir (coin alanı); bu kalem yalnız reklam kaldırımı + listeyi doğrudan gösterme.
- **Gradle/bağımlılık:** KALDIR: com.google.android.gms:play-services-ads:17.2.0; manifest ads APPLICATION_ID meta-data
- **Bağımlılık:** ARCH-00
- **Tuzaklar:** Reklam-gate kaldırılınca erişim kontrolü coin-kilit modeline devredilir (coin alanı) — ikisi koordine edilmeli yoksa tüm sınavlar bir süre tamamen açık kalır. Play Data Safety formu reklam SDK'sı kalkınca güncellenmeli.
- **Kabul kriteri:** Sınav listesi reklamsız doğrudan açılıyor; hiçbir ekranda banner/interstitial/rewarded yok; play-services-ads bağımlılığı ve manifest ads meta-data kalmadı; derleme temiz.

#### `ARCH-21` — Eski RTDB arşiv-okuma kodu + Spinner akışı + sınav paylaşımı temizliği  _(P1 · efor: M)_
- **iOS davranışı:** iOS Firestore cutover'da RTDB arşiv-okuma kodu kaldırıldı (FirebaseArchiveRepository rollback için kısa süre duruyor ama live() Firestore); 'exam sharing' kaldırıldı.
- **iOS karşılığı/kaynak:** AppEnvironment.live() → FirestoreArchiveRepository; eski Universitiess okuması terk edildi.
- **Android mevcut:** MainActivity (3 Spinner RTDB), UniversityFragment/FacultiesFragment/DepartmentsFragment/LessonFragment (RTDB listener), CourseExamListActivity/ExamDetail*/ImageZoom (RTDB okuma); 'uniname/fakname/bolname/lessonname/imagename/downloadURL' string-leaf modeli; Main/Home'da app-link paylaş butonu.
- **Android teknoloji:** —
- **Adımlar:**
  - RTDB `Universitiess` okuyan tüm akışı Firestore repository'ye taşıdıktan SONRA sil/terk et: MainActivity Spinner getDataFirebase/getDataSpinnerFak/getDataSpinnerBol; UniversityFragment/FacultiesFragment/DepartmentsFragment/LessonFragment getDataFirebase; CourseExamListActivity getDataFirebaseMain6Activity/lessonType; ExamDetailActivity/ExamDetailPdfActivity RTDB okuma (downloadURL leaf) → ExamDocument/RemoteFileLoader'a çevir.
  - 'uniname/fakname/bolname/lessonname/imagename' string-leaf atlama mantığını tamamen kaldır (Firestore'da ad=anahtar yok, kanonik id var).
  - Sınav/dosya paylaşımı: mevcut sınav içeriği paylaşımı yok; app-tanıtım 'Uygulama linkini paylaş' (MainActivity btnShare, HomeActivity nav_share/shareMyMessage) iOS'ta yok — parite için kaldır (veya Home/About alanıyla koordine; openQuestions). Sınav dosyası export/paylaşım EKLENMEZ (parite: ekran koruma başka alan).
  - Kullanılmayan RTDB-only sınıfları gözden geçir (ListViewAdapter/ListViewItemHomeActivity/PostClass Compose/RecyclerView'e geçince) ve ölü kodu temizle.
  - RTDB `Universitiess` ağacı yalnız rollback için canlıda kalır (silme kullanıcı/Console işi, kapsam dışı) — Android kodu artık OKUMAZ.
- **Gradle/bağımlılık:** RTDB okuması kalkınca firebase-database yalnız başka alanlar (config/appUpdate, profil) kullanıyorsa kalır; arşiv için kaldırılır.
- **Bağımlılık:** ARCH-04, ARCH-08, ARCH-14, ARCH-17
- **Tuzaklar:** Cutover sırası: Firestore repository çalışır + App Check doğrulanmadan RTDB okumayı silme (rollback güvenliği). 'exam sharing' Android'de zaten yok; yanlışlıkla dosya-paylaş eklenmemeli. App-link paylaş butonu app-promo, sınav paylaşımı değil — kaldırma kararı Home alanıyla ortak.
- **Kabul kriteri:** Arşiv tamamen Firestore'dan okunuyor; hiçbir ekran `Universitiess` RTDB düğümünü okumuyor; string-leaf atlama kodu yok; sınav içeriği paylaşım yolu yok; ölü RTDB kodu temizlendi.


---

## Sınav Görüntüleyiciler (Görsel + PDF) + Ekran Koruması  ·  `examviewers`

**Özet:** iOS'ta bu alan tamamen yeniden yazılmış (SwiftUI): (1) Görsel sınav — dikey kart listesi (her sayfa satır içinde downsample edilmiş, "Sayfa X/Y" başlıklı, A4 placeholder + satır içi retry) + dokununca tam ekran yatay pager (pinch 1–5x, double-tap 2.5x nokta hedefli, tap ile kontrol gizle/göster, sayfa sayacı, siyah arka plan, immersive). (2) PDF sınav — yerel dosyaya indirip sürekli dikey kaydırma + pinch zoom + otomatik solan "3/12" sayfa göstergesi + çok dosyalı seçici. (3) Ekran koruması — her görüntüleyici penceresine FLAG_SECURE (ekran görüntüsü/kayıt engeli + app-switcher otomatik örtü), kayıt sırasında opak örtü, ekran görüntüsünde uyarı banner'ı. (4) "Bize Ulaş" mailto her görüntüleyicide. (5) Komşu sayfa ön-yüklemesi yalnızca ücretsiz ağda. iOS'ta HİÇ reklam yok; Android'deki 3 görüntüleyicide bulunan AdMob banner'lar parite için KALDIRILMALI. Mevcut Android çok ilkel: ImageZoomActivity bozuk matrix zoom (MIN=MAX=1), PDF tek dosya ham HttpURLConnection+AsyncTask, pager/koruma/contact yok. Bu alan, Compose+Kotlin+AndroidX+Coil+Firestore/Storage+App Check temel geçişine bağımlıdır (ayrı alan); iş kalemleri bu temelin kurulduğu varsayımıyla somut Compose/kütüphane adımları verir, aksi halde View+ViewModel alternatifi belirtilir.

**Dokunulan backend sözleşmesi:** Bu alan backend'e yeni yazma yapmaz; ortak sözleşmeyi tüketir. Görüntüleyici veri kaynağı: Firestore (isimli 'vifi' db, europe-west) exams/<id> → {kind: JPG|PDF, status:'published', files:[{storagePath,order}]}; dosyalar 'order'a göre sıralı okunur, kind görüntüleyici tipini seçer. Dosya byte'ları Firebase Storage'tan imagess/<uuid>.jpg ve pdfs/<uuid>.pdf (get: +90|admin, listeleme yok). Storage+App Check ENFORCE olduğundan indirmeler App Check token + kimlik taşımalı (Play Integrity provider; debug build için debug provider + Console debug token). Android şu an RTDB 'Universitiess' (eski arşiv) okuyor; Firestore 'exams'e geçiş AYRI ALAN (Arşiv migrasyonu) — bu alan yalnız 'kind + sıralı dosya listesi'ni tüketir, o katmana bağımlıdır. Coin ile kilitli sınav (users/<uid>/unlocks/<examId>, unlockExam onCall, UNLOCK_COST=100) kapısı Coin Ekonomisi alanıdır; görüntüleyici yalnız kilidi açık…

**Android'e özgü riskler:**
- targetSdk şu an 28; Play Store yeni sürüm için targetSdk 34/35 zorunlu — tüm görüntüleyici davranışları (özellikle Android 14 ScreenCaptureCallback) buna bağlı. Bu, temel geçiş alanının işi ama bu alanın ön koşulu.
- FLAG_SECURE ekran kaydı sırasında içeriği OTOMATİK siyaha çevirir; dolayısıyla iOS'taki açıklamalı opak örtü metni kayıt sırasında kendi başına görünmez. Açıklama örtüsünü göstermek için ayrıca kayıt/mirroring tespiti (DisplayManager.DisplayListener) gerekir; aksi halde kullanıcı neden siyah gördüğünü anlamaz.
- Ekran görüntüsü tespiti yalnız Android 14+ (API 34) Activity.registerScreenCaptureCallback + DETECT_SCREEN_CAPTURE izni ile güvenilir. Android 13 ve altında güvenilir herkese açık API yok (MediaStore ContentObserver güvenilmez ve depolama izni ister). iOS'taki anlık uyarı banner'ı yalnız 14+ sağlanabilir; altı sürümlerde FLAG_SECURE koruması yine çalışır ama banner çıkmaz.
- android.graphics.pdf.PdfRenderer thread-safe değildir, aynı anda tek sayfa açılır, ParcelFileDescriptor ile SEEKABLE yerel dosya ister (ağdan stream edilemez) — iOS'taki 'önce yerel dosyaya indir' davranışı burada zorunlu. Büyük sayfaları ARGB_8888 bitmap'e yüksek ölçekte render etmek OOM riskidir; ekran yoğunluğuna göre downsample ve inBitmap/geri dönüşüm gerekir.
- Coil disk cache + Firebase Storage App Check: düz download URL (token'lı) Coil'e verilince App Check enforce'ta reddedilebilir; indirmeler App Check token taşımalı (FirebaseStorage.getReference(storagePath).stream / getBytes App Check'i otomatik ekler, ya da OkHttp interceptor ile App Check+Auth header). Yanlış kurulumda tüm görseller/PDF'ler 403 döner.
- Compose'da FLAG_SECURE pencere seviyesindedir; Dialog/Popup (ör. tam ekran ayrı pencere) kendi flag'ini ister. Tam ekran görüntüleyici ayrı Activity ise ona da FLAG_SECURE ayrıca eklenmeli.
- barteksc:android-pdf-viewer:2.3.0 jcenter'da ve terk edilmiş; jcenter ölü. Bağımlılık korunacaksa mhiew fork'u (JitPack) ya da framework PdfRenderer'a geçiş şart.
- play-services-ads kaldırılınca manifest'teki ads APPLICATION_ID meta-data da kaldırılmalı; kalırsa (yeni sürüm SDK ile) uygulama açılışta çökebilir veya lint hatası verir.
- READ_EXTERNAL_STORAGE izni artık gereksiz (indirmeler getCacheDir'e); scoped storage uyumu için kaldırılmalı.
- Picasso (mevcut) downsample ve bellek maliyet limiti için Coil kadar esnek değil; parite için Coil'e geçiş önerilir, Picasso bırakılırsa 4096px tam ekran decode ve cost-limit elle yönetilmeli.

### İş Kalemleri

#### `VIEW-00` — Üç sınav görüntüleyiciden AdMob'u tamamen kaldır (iOS'ta reklam yok — parite)  _(P0 · efor: 0.5 gün)_
- **iOS davranışı:** iOS'ta hiçbir yerde reklam yok; monetizasyon coin ekonomisi. Görüntüleyiciler reklamsız, kenardan kenara içerik.
- **iOS karşılığı/kaynak:** Features/Exam/* — hiçbir dosyada AdMob referansı yok.
- **Android mevcut:** ExamDetailActivity, ExamDetailPdfActivity, ImageZoomActivity → her birinde MobileAds.initialize(...) + AdView.loadAd(AdRequest). Layout'larda (activity_exam_detail.xml, activity_exam_detail_pdf.xml, activity_image_zoom.xml) <com.google.android.gms.ads.AdView> + ad unit id'ler. Manifest'te ads APPLICATION_ID meta-data. Gradle'da play-services-ads:17.2.0.
- **Android teknoloji:** AdMob kaldırma: com.google.android.gms.ads.* importları, MobileAds, AdView, AdRequest; layout AdView elemanları ve onların constraint referansları (ör. ListView'ın app:layout_constraintBottom_toTopOf='@+id/adView' → parent'a bağla); manifest meta-data; build.gradle play-services-ads satırı.
- **Adımlar:**
  - ExamDetailActivity/ExamDetailPdfActivity/ImageZoomActivity içindeki MobileAds.initialize ve AdView.loadAd çağrılarını ve ads importlarını sil (bu dosyalar zaten yeni görüntüleyicilerle değişecek; ara durumda da reklam kalmasın).
  - activity_exam_detail.xml, activity_exam_detail_pdf.xml, activity_image_zoom.xml içinden <com.google.android.gms.ads.AdView> elemanlarını kaldır; AdView'a bağlı layout_constraintBottom_toTopOf referanslarını parent alt kenarına yeniden bağla.
  - AndroidManifest.xml'den <meta-data android:name='com.google.android.gms.ads.APPLICATION_ID' .../> satırını kaldır.
  - app/build.gradle'dan implementation 'com.google.android.gms:play-services-ads:17.2.0' bağımlılığını kaldır.
  - Proje genelinde kalan ads referanslarını ara (grep 'gms.ads', 'AdView', 'AdRequest', 'MobileAds') ve temizle; RewardedAd/interstitial varsa (commit geçmişinde 'Added Rewarded Ad') onları da kaldır.
- **Gradle/bağımlılık:** KALDIR: com.google.android.gms:play-services-ads:17.2.0. Manifest ads meta-data kaldır.
- **Tuzaklar:** Meta-data kalırsa yeni GMS ile açılışta çökme riski. AdView'ı kaldırınca constraint zincirini yeniden bağlamazsan liste/PDF/görsel bileşeni ekranı doldurmaz.
- **Kabul kriteri:** APK'da play-services-ads sınıfı yok; 3 görüntüleyici ekranında banner alanı ve boşluğu yok; manifest'te ads meta-data yok; grep 'gms.ads' 0 sonuç; uygulama bu ekranlarda reklam izni/istek yapmıyor.

#### `VIEW-01` — Görsel sınav sayfa listesi ekranı (ExamImages) — dikey kart listesi, bağlam başlığı, 'Sayfa X/Y', progressive downsample, A4 placeholder, satır içi retry, durum ekranları  _(P0 · efor: 2 gün)_
- **iOS davranışı:** LazyVStack: üstte 'ÜNİ › DERS · N sayfa' bağlam başlığı (IconBadge). Her sayfa kağıt-benzeri kart: A4 (1/√2) placeholder + ProgressView yüklenirken; görüntü kart genişliğine göre downsample edilir (keskinse yeniden decode etmez; rotasyon/resize'da placeholder'sız keskinleştirir); altında 'Sayfa 1 / 3' (monospace). Hata → kart içi 'Sayfa yüklenemedi' + mesaj + 'Tekrar Dene' (kart = retry düğmesi). Kart dokunuşu tam ekran açar (hatada retry). Durumlar: loading, failed+retry, boş 'Bu sınavda sayfa yok'.
- **iOS karşılığı/kaynak:** ExamImagesView.swift (ExamImagesScreen.pageList, ExamContextHeader), ExamPageView.swift (Phase: loading/loaded(pixelSize)/failed; targetPixelSize 256-step; loadImage upgrade mantığı).
- **Android mevcut:** ExamDetailActivity: RTDB downloadURL listesini düz ListView'da (PostClass adaptör) gösterir; satır içi sayfa önizleme yok, placeholder/retry/durum yok, tıkla → ImageZoomActivity.
- **Android teknoloji:** Jetpack Compose: LazyColumn + item'lar; her kart Card + Coil coil.compose.AsyncImage (ImageRequest.Builder().size(kart px).crossfade). Placeholder/error için AsyncImage painter state (AsyncImagePainter.State.Loading/Error) → A4 aspectRatio(1f/1.4142f) kutusu + CircularProgressIndicator / hata UI. Alternatif: RecyclerView + Coil ImageView extension. Context header: Row + ikon + Text. 'Sayfa x / y' Text(fontFamily monospace).
- **Adımlar:**
  - ExamImagesScreen composable: viewModel'den Loadable<ExamDocument> (loading/failed/loaded) state'ini observe et; loading→CircularProgressIndicator, failed→hata+Tekrar Dene (loadAttempt++ ile retry), loaded&boş→'Bu sınavda sayfa yok'.
  - Üst bağlam başlığı: 'ÜNİ › DERS' (path.displayNames ilk + sondan bir önceki) + '· N sayfa'; ikon rozeti.
  - Her sayfa için Card (yuvarlak köşe 12dp, hafif gölge/0.5dp kenar) içinde Coil AsyncImage; ImageRequest.size() = kartın ölçülen genişliği (BoxWithConstraints/onSizeChanged ile) → Coil otomatik downsample eder; A4 oranlı Box placeholder (Modifier.aspectRatio(1f/sqrt(2))).
  - Yükleniyor state'inde ProgressIndicator; Error state'inde kart içi 'Sayfa yüklenemedi' + mesaj + 'Tekrar Dene' (o öğeyi yeniden iste: ImageRequest key'ini attempt ile değiştir ya da memoryCacheKey invalidate).
  - Kart altına 'Sayfa ${i+1} / $count' (monospace). Kart clickable → VIEW-02 tam ekran pager'ı bu index'ten aç; hata durumunda tıklama retry yapsın.
  - Scroll performansı: Coil memoryCache + LazyColumn item key = sayfa index; görünmeyen satırın bellek baskısını Coil yönetir.
- **Gradle/bağımlılık:** EKLE: io.coil-kt:coil-compose:2.7.0; androidx.compose BOM (androidx.compose:compose-bom:2024.09.03), androidx.activity:activity-compose:1.9.2, foundation/material3.
- **Bağımlılık:** VIEW-06 (veri kaynağı: kind+sıralı dosya listesi), VIEW-02 (tam ekran pager), VIEW-05 (Coil image loader/downsample yapılandırması), Temel: Compose/Kotlin/AndroidX/Coil
- **Tuzaklar:** Coil ImageRequest.size() verilmezse tam çözünürlük decode → büyük JPG'lerde OOM/jank; mutlaka ölçülen genişliği ver. placeholderMemoryCacheKey ile tam ekran handoff'u (VIEW-05) hazırlanmalı.
- **Kabul kriteri:** Çok sayfalı görsel sınav açıldığında her sayfa satır içinde net görünür, 'Sayfa X/Y' doğru; yavaş ağda A4 placeholder + spinner görünür; bir sayfa hata verirse kart içi retry ile yeniden yüklenir; boş sınavda 'Bu sınavda sayfa yok'; karta dokununca ilgili index'ten tam ekran açılır.

#### `VIEW-02` — Tam ekran yatay sayfa pager (FullScreenPagerView) — HorizontalPager, sayfa sayacı, tap ile kontrol gizle/göster, kapat, siyah + immersive, dokunulan index'ten açılış  _(P0 · efor: 2 gün)_
- **iOS davranışı:** .fullScreenCover, siyah arka plan, TabView(.page indexDisplayMode:.never) yatay sayfalama; her sayfa zoomable. Üst bar: sol 'X' kapat (44pt cam yuvarlak), ortada 'N / M' sayacı (monospace, numericText geçiş). Tek dokunuş chrome'u gizle/göster (0.2s), VoiceOver'da chrome hep açık. status bar gizli, chrome gizliyken persistentSystemOverlays gizli; dark şema zorlanır. Açılış index'i dokunulan sayfa (0..last clamp).
- **iOS karşılığı/kaynak:** FullScreenPagerView.swift (selection, isChromeHidden, showsChrome, topBar, pageCounter, toggleChrome).
- **Android mevcut:** Yok. ImageZoomActivity tek bir görseli açar (listedeki satırdan), sayfalar arası geçiş yok, sayaç yok, kontrol yok, immersive yok.
- **Android teknoloji:** Compose androidx.compose.foundation.pager.HorizontalPager(rememberPagerState(initialPage=index)); arka plan Color.Black. Immersive: WindowInsetsController (WindowCompat.getInsetsController) ile systemBars gizle (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE). Üst bar Box/Row + IconButton(Close) + 'currentPage+1 / count' Text(monospace). Tap-to-toggle: her sayfada pointerInput/tapGestures → chrome state toggle (animateVisibility). TalkBack: AccessibilityManager.isTouchExplorationEnabled → chrome'u zorla göster.
- **Adımlar:**
  - Tam ekran görüntüleyiciyi Activity (FLAG_SECURE/immersive yönetimi kolay) veya Compose destination olarak kur; initialPage = dokunulan index, pageCount = dosya sayısı.
  - HorizontalPager ile sayfalar; her sayfa VIEW-03 zoomable görsel; arka plan tam siyah, Modifier.fillMaxSize + ignore system insets.
  - WindowInsetsControllerCompat ile status+navigation bar gizle; chrome gizliyken tamamen immersive, görünürken geçici göster.
  - Üst bar: sol IconButton 'Kapat' (Icons.Default.Close), orta 'N / M' sayacı (pagerState.currentPage+1); monospace + değişimde küçük animasyon.
  - Tek dokunuşta chrome görünürlüğünü toggle et (AnimatedVisibility, 200ms); double-tap zoom ile çakışmaması için tap gesture'ı zoomable'ın single-tap callback'ine bağla (VIEW-03).
  - TalkBack açıkken chrome'u her zaman göster (kapat düğmesi erişilebilir kalsın); geri tuşu/escape ile kapanış.
  - screenView('ExamViewer') analytics olayını tetikle (Analytics alanına bağlı).
- **Gradle/bağımlılık:** androidx.compose.foundation (HorizontalPager) BOM ile; androidx.core:core-ktx (WindowCompat/WindowInsetsControllerCompat).
- **Bağımlılık:** VIEW-03 (zoomable sayfa), VIEW-04 (komşu prefetch), VIEW-05 (decode), VIEW-07 (FLAG_SECURE tam ekrana), VIEW-01 (önizleme handoff)
- **Tuzaklar:** Pager swipe gesture'ı ile zoomable'ın pan gesture'ı çakışır: zoom 1x iken pager kaydırmalı, zoom>1x iken pan içeride kalmalı — Telephoto bunu HorizontalPager ile entegre çözer (aşağıda). Immersive bar'lar Activity ayrıysa her açılışta yeniden uygulanmalı.
- **Kabul kriteri:** Listeden bir sayfaya dokununca tam ekran o sayfadan açılır; sola/sağa kaydırarak sayfalar arası geçilir; sayaç 'N / M' doğru; tek dokunuş kontrol çubuğunu gizler/gösterir; sistem çubukları gizli (immersive); kapat düğmesi ekranı kapatır; TalkBack'te kontroller gizlenmez.

#### `VIEW-03` — Pinch/double-tap zoomable görsel sayfa (ZoomableImageView) — 1–5× pinch, double-tap 2.5×/fill nokta hedefli, double-tap reset, ortalama, sayfa pasifken reset  _(P0 · efor: 1.5 gün)_
- **iOS davranışı:** UIScrollview tabanlı: minZoom=1, maxZoom=5, aspect-fit yerleşim; double-tap: 1x iken 2.5× (veya ekranı dolduran fill-scale) dokunulan noktaya zoom, >1x iken min'e reset; görüntü bounds'tan küçükken ortalanır, büyükken içerikten kayar; sayfa aktif değilken (swipe ile uzaklaşınca) zoom resetlenir; aynı en-boy oranlı daha keskin görüntü mevcut zoom'u korur.
- **iOS karşılığı/kaynak:** ZoomableImageView.swift (ZoomableImageScrollView: fitImage, centerImage, handleDoubleTap fillScale=max(bounds/imageBounds), doubleTapZoomScale=2.5, resetZoom, hasSameAspectRatio).
- **Android mevcut:** ImageZoomActivity: elle Matrix + OnTouch; MIN_ZOOM=MAX_ZOOM=1f (zoom sınırı yok/bozuk), double-tap yok, ortalama yok, momentum yok, sınır kontrolü yok.
- **Android teknoloji:** ÖNERİ: me.saket.telephoto:zoomable-image-coil (Telephoto) — ZoomableImage/ZoomableAsyncImage; HorizontalPager ile entegre, pinch+pan+double-tap, maxZoomFactor, sınır/ortalama hazır, Coil placeholder desteği. ZoomableState ile maxZoomFactor=5f; onClick (single tap) → chrome toggle; double-tap davranışı yerleşik. Alternatif: net.engawapg.lib:zoomable, ya da elle Modifier.graphicsLayer + detectTransformGestures/detectTapGestures (double-tap).
- **Adımlar:**
  - Telephoto ZoomableImageSource.coil(ImageRequest) ile her pager sayfasını zoomable yap; ZoomableState(maxZoomFactor = 5f, 1x taban aspect-fit).
  - Double-tap: Telephoto varsayılanı noktaya zoom/geri reset; hedef oranı ~2.5× veya ekranı dolduran fill-scale olacak şekilde ayarla (zoomSpec).
  - single-tap (double-tap'ten ayrık) → VIEW-02 chrome toggle callback'i (onClick).
  - Sayfa pasifleşince (pagerState.currentPage != bu index) zoomable state'i resetZoom(); Telephoto'da state'i key'le veya LaunchedEffect(isCurrent){ if(!isCurrent) state.resetZoom() }.
  - Görüntü bounds'tan küçükken ortalı (Telephoto/alignment Center); görsel önizlemeden keskinine geçişte (aynı oran) zoom korunur — aynı ZoomableState örneğini koru, sadece ImageSource'u keskinle değiştir.
  - Telephoto kullanılmazsa: graphicsLayer(scaleX/Y, translation) + detectTransformGestures (pinch/pan, sınır clamp 1–5) + detectTapGestures(onDoubleTap = nokta hedefli zoom/reset).
- **Gradle/bağımlılık:** EKLE: me.saket.telephoto:zoomable-image-coil:0.13.0 (transitif zoomable). Alternatif: net.engawapg.lib:zoomable:1.6.2.
- **Bağımlılık:** VIEW-02 (pager), VIEW-05 (keskin decode)
- **Tuzaklar:** Pager + zoom gesture çakışması Telephoto'nun çözdüğü ana sorundur; elle yazarsan pager'ın userScrollEnabled'ını zoom>1x iken kapatman gerekir. maxZoomFactor'ü görüntünün intrinsic çözünürlüğüne göre sınırlamazsan aşırı zoom'da bulanıklaşır (VIEW-05 keskin decode şart).
- **Kabul kriteri:** Tam ekranda iki parmakla 1×–5× arası zoom yapılır (sınır dışına taşmaz); çift dokunuş dokunulan noktaya ~2.5×/fill zoom, tekrar çift dokunuş 1×'e döner; 1×'te pan yerine pager kaydırır, zoom'da pan içeride kalır; başka sayfaya geçip dönünce zoom sıfırlanmış; küçük görsel ortalı.

#### `VIEW-04` — Komşu sayfa speculative ön-yükleme + ağ farkındalığı (yalnız ücretsiz ağda prefetch)  _(P1 · efor: 1 gün)_
- **iOS davranışı:** Tam ekran pager görünen sayfanın komşularını speculative yükler; speculative yalnız unmetered/kısıtsız ağda yapılır (cellular/Low Data Mode'da fail-fast, veri harcanmaz), sayfa swipe edildiğinde gerçek (non-speculative) yüklenir. isCurrent task id'nin parçası olduğundan atlanan komşu, aktif sayfa olunca gerçekten yüklenir.
- **iOS karşılığı/kaynak:** FullScreenPagerView.swift (ZoomablePage.loadImage speculative:!isCurrent, LoadRequest.isCurrent), RemoteFileLoader.swift (applyNetworkPolicy: allowsConstrainedNetworkAccess=false, allowsExpensiveNetworkAccess=false; speculative tracked değil).
- **Android mevcut:** Yok; her görsel ayrı açıldığından prefetch kavramı yok.
- **Android teknoloji:** ConnectivityManager.getNetworkCapabilities(activeNetwork) → NetworkCapabilities.NET_CAPABILITY_NOT_METERED (ve TEMPORARILY_NOT_METERED) kontrolü; metered ise prefetch atla. Prefetch: Coil ImageLoader.enqueue(ImageRequest.Builder().data(komşu url).size(fullscreen px).build()) — sadece memory/disk cache'i ısıtır (hedef View yok). Aktif sayfa her zaman normal AsyncImage ile yüklenir.
- **Adımlar:**
  - pagerState.currentPage değişince currentPage±1 komşu sayfaların URL'lerini belirle.
  - ConnectivityManager ile aktif ağ metered mı kontrol et; NET_CAPABILITY_NOT_METERED yoksa prefetch yapma.
  - Ücretsiz ağda komşular için Coil imageLoader.enqueue(tam ekran boyutunda ImageRequest) ile cache ısıt (sonucu bir View'a bağlama).
  - Kullanıcı o komşuya swipe edince normal (zorunlu) yükleme yapılır; prefetch atlanmışsa burada ilk kez gerçek indirilir (isCurrent → zorunlu).
  - Prefetch istekleri cache'i doldurmasın diye Coil memory/disk cache sınırlarına güven (VIEW-05); gereksiz paralel indirmeyi sınırla.
- **Gradle/bağımlılık:** Coil (mevcut). ACCESS_NETWORK_STATE izni zaten manifest'te var.
- **Bağımlılık:** VIEW-02, VIEW-05
- **Tuzaklar:** İzin ACCESS_NETWORK_STATE gerekir (mevcut). getActiveNetwork null olabilir (uçak modu) → prefetch'i güvenli atla. iOS 'Low Data Mode' karşılığı Android 'Data Saver' (ConnectivityManager.getRestrictBackgroundStatus) ve metered bayrağıdır; ikisini de dikkate al.
- **Kabul kriteri:** Wi-Fi'de bir sonraki sayfaya geçiş anında (önceden ısıtılmış) görünür; hücresel veride komşu prefetch yapılmaz (veri harcanmaz) ama swipe edilen sayfa yine de yüklenir; metered→unmetered geçişinde prefetch devreye girer.

#### `VIEW-05` — Görsel decode/downsample + bellek/disk cache + release + önizleme handoff (RemoteFileLoader image yolu → Coil ImageLoader)  _(P0 · efor: 1.5 gün)_
- **iOS davranışı:** Liste: kart genişliğine göre downsample (256-step yuvarlama; keskinse yeniden decode yok). Tam ekran: 2×ekran-en-uzun-kenar×scale, 4096px tavan. Decode EXIF oryantasyonlu, tam boy materyalize edilmeden thumbnail. Bellek cache (url#size key, cost limit 128MB), disk response cache 512MB; decode edilemezse cache'ten düşülür. Tam ekran sayfası scroll-off'ta büyük bitmap releaseImage ile paylaşımlı cache'ten düşülür (küçük thumbnail'ları ezmesin). Liste önizlemesi tam ekrana devredilir (anında göster, sonra keskinini yükle).
- **iOS karşılığı/kaynak:** RemoteFileLoader.swift (image/releaseImage, decodeImage CGImageSourceCreateThumbnail maxPixelSize, imageCacheKey url#size, decodedImageCostLimit); FullScreenPagerView maxPixelSize 4096 cap + releaseImage onDisappear; ExamImagesView pagePreviews handoff.
- **Android mevcut:** Picasso düz load; downsample hedefi yok, tam ekran için ayrı keskin boyut yok, bellek maliyet limiti/release yok.
- **Android teknoloji:** Coil ImageLoader singleton: .memoryCache(MemoryCache.Builder(ctx).maxSizePercent(~0.25).build()), .diskCache(DiskCache.Builder().directory(cacheDir/'exam_images').maxSizeBytes(512L*1024*1024).build()), .respectCacheHeaders(false) (dosyalar değişmez). ImageRequest.size(): liste=kart px, tam ekran=min(2*maxEkranKenar*density, 4096). placeholderMemoryCacheKey(listeKey) ile handoff. Coil EXIF'i otomatik uygular. Release: Coil LRU otomatik; ayrıca memoryCache.remove(key) ile tam ekran kapanınca büyük bitmap'i düş.
- **Adımlar:**
  - Uygulama genelinde tek Coil ImageLoader kur (Hilt @Provides veya ImageLoaderFactory): memoryCache maxSizePercent, diskCache cacheDir altında 512MB, crossfade.
  - Liste ImageRequest.size(kart px); tam ekran ImageRequest.size(min(2*max(ekranW,ekranH)*density, 4096)) — rotasyonda en uzun kenar değişmediği için yeniden decode olmaz.
  - Önizleme handoff: liste isteğinde memoryCacheKey(stableKey) ver; tam ekran isteğinde placeholderMemoryCacheKey(aynı stableKey) → liste görüntüsü anında görünür, keskin arkada yüklenir.
  - Decode edilemeyen/bozuk byte'larda ilgili diskCache/memoryCache anahtarını invalidate et (tekrar bozuk servis etme).
  - Tam ekran sayfa ekrandan çıkınca (onDispose) büyük bitmap memoryCache.remove(key) ile düşülsün (küçük thumbnail'ları koru).
  - Firebase Storage App Check: ImageRequest'lere App Check+Auth token ekleyen OkHttp interceptor ya da FirebaseStorage stream kullan (VIEW-06 gotcha).
- **Gradle/bağımlılık:** EKLE: io.coil-kt:coil-compose:2.7.0 (+ coil-base). KALDIR (opsiyonel): com.squareup.picasso:picasso (Coil'e geçişle gereksiz).
- **Bağımlılık:** VIEW-01, VIEW-02, VIEW-06 (Storage auth)
- **Tuzaklar:** Coil disk cache header'lara saygı duyarsa (respectCacheHeaders=true) ve Storage no-cache dönerse disk'e yazmaz → her açılışta indirir; false yap. placeholderMemoryCacheKey liste ve tam ekran boyut anahtarları farklı olduğundan sadece hızlı placeholder içindir, keskin yine yüklenir.
- **Kabul kriteri:** Liste sayfaları kart boyutunda net ve hızlı (OOM yok); tam ekranda zoom'da metin keskin (yüksek çözünürlük, ≤4096); aynı sayfa tam ekrana geçince önce liste önizlemesi anında görünüp sonra keskinleşir; rotasyonda yeniden indirme/decode olmaz; tam ekran kapanınca bellek düşer; çok sayfalı sınavda bellek sabit kalır (thrash yok).

#### `VIEW-06` — Görüntüleyici veri kaynağı — kind + sıralı dosya listesi (Firestore 'exams' / Storage) ve kind'e göre görüntüleyici seçimi/yönlendirme  _(P0 · efor: 1 gün (bu alan payı; asıl migrasyon ayrı alan))_
- **iOS davranışı:** ExamDocument {kind, fileURLs[]}; repository.document(at:path) ile yüklenir. ExamImagesView ve PDFExamView kind'i kontrol eder; kind değişmişse (liste özetiyle gelen kind ≠ gerçek) doğru görüntüleyiciye yönlendirir. Dosya adı disk için okunur üretilir.
- **iOS karşılığı/kaynak:** ExamDocumentViewModel.swift (load, document.kind, redirectIfKindChanged), ExamDocument.fileURLs/pdfFileName.
- **Android mevcut:** ExamDetailActivity/ExamDetailPdfActivity RTDB 'Universitiess/.../type' altından downloadURL'leri okur; sıralama yok, kind Intent 'type' extra'sından gelir, tek PDF dosyası (index 0).
- **Android teknoloji:** Firestore getFirestore(app, 'vifi').collection('exams').document(id).get() → kind(JPG|PDF), files: order'a göre sıralı storagePath listesi. Storage: FirebaseStorage.getInstance(app).getReference(storagePath). Download URL yerine doğrudan StorageReference.getStream()/getBytes() (App Check otomatik) ya da getDownloadUrl(). ViewModel (Kotlin, StateFlow<Loadable<ExamDocument>>). Router/Nav: kind'e göre ExamImages veya ExamPdf destination; yanlışsa yönlendir.
- **Adımlar:**
  - ExamDocument modeli (kind enum JPG/PDF, orderlı file listesi) ve ViewModel: Firestore 'exams/<id>' oku (named db 'vifi'), files'i 'order'a göre sırala.
  - Her file'ın storagePath'ini FirebaseStorage.getReference(storagePath) ile StorageReference'a çevir; görüntüleyicilere bu referansları/akışları ver (download URL token'ı yerine App Check'li erişim).
  - kind'e göre görüntüleyici seç: JPG → VIEW-01 liste + VIEW-02 pager; PDF → VIEW-07..09 PDF viewer. Navigation route'u kind taşısın.
  - kind beklenenden farklıysa (liste özeti eski) doğru görüntüleyiciye yönlendir (iOS redirectIfKindChanged pariteli).
  - Yükleme/başarısız/boş durumlarını ViewModel state'i ile görüntüleyicilere aktar; recents/analytics kaydı (Analytics/Recents alanına bağlı).
  - NOT: RTDB 'Universitiess' → Firestore 'exams' tam migrasyonu Arşiv alanıdır; bu kalem yalnız görüntüleyicinin tüketeceği arayüzü (kind+sıralı dosya) sağlamlaştırır.
- **Gradle/bağımlılık:** EKLE: com.google.firebase:firebase-bom:33.5.1 → firebase-firestore-ktx, firebase-storage-ktx, firebase-appcheck-playintegrity; FirebaseFirestore.getInstance(app,'vifi').
- **Bağımlılık:** Arşiv migrasyonu (RTDB→Firestore 'exams'), App Check (Play Integrity) + Storage auth entegrasyonu, Coin kilidi kapısı (kilitli sınav erişimi) — Coin alanı
- **Tuzaklar:** Firestore named db 'vifi' (default DEĞİL) — getFirestore(app,'vifi') kullanılmazsa boş/yanlış db okunur. Storage get kuralı +90|admin + App Check enforce; App Check sağlayıcısı kurulmadan tüm dosya erişimi reddedilir.
- **Kabul kriteri:** exams/<id> JPG ise görsel liste+pager açılır, PDF ise PDF viewer açılır; dosyalar 'order' alanına göre doğru sırada; yanlış kind'le girilse bile doğru görüntüleyiciye yönlenir; Storage dosyaları App Check enforce altında 200 ile yüklenir (403 yok).

#### `VIEW-07` — PDF sınav görüntüleyici (PDFExamView/PDFKitView) — yerel dosyaya indir, sürekli dikey kaydırma, fit-width, pinch zoom, durum ekranları  _(P0 · efor: 2.5 gün)_
- **iOS davranışı:** Seçili PDF yerel dosyaya indirilir (cache), PDFKit singlePageContinuous + vertical + autoScales (fit width) + pinch zoom + pageBreakMargins/shadow. Durumlar: 'Belge indiriliyor…', hata+Tekrar Dene, boş 'Bu sınavda dosya yok', açılamaz 'PDF belgesi açılamadı. Dosya bozuk olabilir.'. PDF ayrıştırma off-main.
- **iOS karşılığı/kaynak:** PDFExamView.swift (fileContent, loadFile localFile indir + PDFDocumentOpener.open off-main, PDFExamError.unreadable), PDFKitView.swift (displayMode .singlePageContinuous, autoScales).
- **Android mevcut:** ExamDetailPdfActivity: ham HttpURLConnection + AsyncTask ile InputStream'i barteksc PDFView 2.3.0'a stream eder; sadece ilk dosya; disk cache yok; loading/error/empty/retry yok; AdMob var.
- **Android teknoloji:** ÖNERİ: framework android.graphics.pdf.PdfRenderer (API21+) + Compose LazyColumn: her sayfa ParcelFileDescriptor(MODE_READ_ONLY) → PdfRenderer.openPage(i).render(bitmap, ...RENDER_MODE_FOR_DISPLAY); bitmap'i ekran genişliğine göre ölçekle; dikey liste = sürekli kaydırma; pinch zoom için Telephoto zoomable veya graphicsLayer. Alternatif: com.github.mhiew:android-pdf-viewer:3.2.0-beta.3 (barteksc fork, JitPack) fromFile(local).enableSwipe(false, dikey continuous).
- **Adımlar:**
  - Seçili PDF'i getCacheDir altındaki per-URL dosyaya indir (VIEW-09); indirme/açma IO thread (Dispatchers.IO / coroutine).
  - PdfRenderer'ı ParcelFileDescriptor(local file, MODE_READ_ONLY) ile aç; pageCount al; LazyColumn'da her sayfayı lazy render et (render anında bitmap, ekran genişliğine downsample, ARGB_8888).
  - fit-width: bitmap genişliği = görünüm genişliği; sürekli dikey kaydırma (sayfalar arası küçük boşluk/gölge).
  - Pinch zoom: tüm liste için ya Telephoto zoomable sarmalayıcı ya da her sayfa graphicsLayer; iOS autoScales fit-width tabanıyla uyumlu.
  - Durumlar: indirilirken 'Belge indiriliyor…'; indirme hatası → hata+Tekrar Dene (fileAttempt++); PdfRenderer açılamazsa 'PDF belgesi açılamadı. Dosya bozuk olabilir.'; dosya yoksa 'Bu sınavda dosya yok'.
  - Bellek: aynı anda tek PdfRenderer.Page açık (thread-safe değil), render sonrası page.close(); görünmeyen sayfa bitmap'lerini geri dönüştür (recycle/Coil benzeri cache).
- **Gradle/bağımlılık:** FRAMEWORK PdfRenderer (dep yok) ÖNERİLİR; VEYA EKLE com.github.mhiew:android-pdf-viewer:3.2.0-beta.3 (+ JitPack repo). KALDIR: com.github.barteksc:android-pdf-viewer:2.3.0 (jcenter/ölü).
- **Bağımlılık:** VIEW-06 (dosya kaynağı), VIEW-09 (disk cache), VIEW-07 FLAG_SECURE (VIEW-07b)
- **Tuzaklar:** PdfRenderer thread-safe değil (senkronize eriş), sayfa ağdan stream edilemez → mutlaka yerel seekable dosya. Render ölçeği çok yüksekse OOM; density tabanlı ölçekle + inSampleSize. barteksc 2.3.0 jcenter'da; jcenter kapandığı için build kırılır → kaldır.
- **Kabul kriteri:** PDF sınav açılınca önce 'Belge indiriliyor…', sonra sürekli dikey kaydırmalı sayfalar fit-width görünür; pinch ile yakınlaştırılır; metin net (uygun render ölçeği); bozuk/erişilemez PDF'de doğru hata + retry; OOM yok (çok sayfalı büyük PDF akıcı); indirme ağdan stream değil yerel dosyadan.

#### `VIEW-08` — PDF çok dosyalı seçici + kayan otomatik-solan sayfa göstergesi ('3 / 12')  _(P1 · efor: 1 gün)_
- **iOS davranışı:** Sınavda >1 PDF varsa toolbar'da Menu/Picker 'Dosya 1 / Dosya 2…'; seçince o dosya yüklenir (sayfa 0'a döner). Kayan 'K / M' sayfa göstergesi (pageCount>1): sayfa değişince görünür, 2sn değişmezse solar (0.4s), değişince tekrar döner; VoiceOver'da hep görünür.
- **iOS karşılığı/kaynak:** PDFExamView.swift (filePicker Menu/Picker 'Dosya \(i+1)', selectedFile; pageIndicator currentPage/pageCount, showPageIndicatorBriefly Task.sleep 2s).
- **Android mevcut:** Yok; yalnız ilk dosya (index 0) gösterilir, dosya seçimi ve sayfa göstergesi yok.
- **Android teknoloji:** Compose: üst bar IconButton + DropdownMenu (DropdownMenuItem 'Dosya 1/2…') selectedFile state; seçimde PDF yeniden yükle, scroll 0'a. Sayfa göstergesi: LazyColumn firstVisibleItemIndex (snapshotFlow) → 'index+1 / pageCount' Box(surface, capsule); LaunchedEffect(currentPage){ visible=true; delay(2000); visible=false } (AnimatedVisibility fade). TalkBack'te hep görünür.
- **Adımlar:**
  - Dosya sayısı >1 ise üst bar'a dosya seçici (DropdownMenu) ekle; 'Dosya ${i+1}' öğeleri; seçince selectedFile değiş, ilgili PDF'i (VIEW-07) yükle, listeyi başa sar.
  - LazyListState.firstVisibleItemIndex'ten currentPage türet; pageCount>1 ise kayan '${currentPage+1} / $pageCount' göstergesi (alt-orta, yarı saydam kapsül).
  - Sayfa değişince göstergeyi göster; 2sn değişiklik olmazsa AnimatedVisibility ile solar; yeni değişiklikte tekrar görünür.
  - TalkBack (touch exploration) açıkken göstergeyi kalıcı göster.
  - Dosya değişiminde sayfa göstergesi yeni dosyanın sayfa sayısını yansıtsın.
- **Gradle/bağımlılık:** —
- **Bağımlılık:** VIEW-07
- **Tuzaklar:** snapshotFlow{ firstVisibleItemIndex }'i debounce etmezsen çok sık recomposition olur. Dosya değişiminde eski PdfRenderer kapatılmalı (kaynak sızıntısı).
- **Kabul kriteri:** Tek dosyalı PDF'de seçici görünmez; çok dosyalı sınavda 'Dosya 1/2…' seçiciyle dosyalar arası geçilir (seçilen dosya başa sarılı açılır); kaydırırken 'K / M' göstergesi görünüp 2sn sonra solar, kaydırınca tekrar belirir.

#### `VIEW-09` — PDF disk cache (per-URL yerel dosya) + off-main indirme + oldest-first budget prune  _(P1 · efor: 1 gün)_
- **iOS davranışı:** PDF yerel kopyası tek kalıcı depo (response cache'te duplicate tutulmaz); per-URL klasör (URL SHA256) + okunur dosya adı; disk prune 256MB oldest-first, son 60sn dokunulan dosyalar korunur; hesap silinince purge. Aynı dosya tekrar açılınca indirmeden açılır.
- **iOS karşılığı/kaynak:** RemoteFileLoader.swift (localFile: SHA256 folder + sanitizedFileName, pruneFiles budget 256MB keepNewerThan 60s, purge).
- **Android mevcut:** Yok; her açılışta ham HttpURLConnection ile yeniden stream, disk cache ve prune yok.
- **Android teknoloji:** getCacheDir()/'exam_pdfs'/<sha256(url)>/<sanitized ad>.pdf; indirme OkHttp (App Check/Auth header) veya FirebaseStorage.getFile(localFile) IO coroutine ile; var olan dosya varsa yeniden indirme. Prune: WorkManager (OneTimeWorkRequest, app background/launch) veya basit coroutine; File.lastModified() ile oldest-first, toplam>budget iken sil, son 60sn korunur.
- **Adımlar:**
  - PDF indirince getCacheDir altında per-URL (SHA256) klasöre okunur adla yaz; dosya zaten varsa doğrudan onu kullan (indirme yok).
  - İndirme ve PdfRenderer açma IO thread'de; indirme sırasında UI 'Belge indiriliyor…'.
  - Disk budget (256MB) prune: oldest-first (lastModified), son 60sn dokunulanları koru; uygulama launch/background'da çalıştır (WorkManager veya ViewModel scope).
  - Hesap silme/çıkışta PDF cache klasörünü purge et (Hesap alanıyla koordine).
  - MODE_READ_ONLY ParcelFileDescriptor için tam yerel dosya (stream değil) garanti et.
- **Gradle/bağımlılık:** EKLE (opsiyonel prune için): androidx.work:work-runtime-ktx:2.9.1. OkHttp (Firebase transitif) veya FirebaseStorage.getFile.
- **Bağımlılık:** VIEW-07, VIEW-06 (Storage auth)
- **Tuzaklar:** getDownloadUrl token'ı değişebilir; dosya kimliği olarak storagePath/canonical URL kullan (iOS token'ı stripler). READ_EXTERNAL_STORAGE gerekmez (app cache). Prune in-flight indirmeyi silmemeli (son 60sn koruması).
- **Kabul kriteri:** Aynı PDF ikinci açılışta anında (indirmeden) açılır; cache 256MB'ı aşınca eski dosyalar silinir, az önce açılan dosya korunur; çıkışta cache temizlenir; indirme sırasında ekran boş kalmaz.

#### `VIEW-10` — Ekran koruması — tüm görüntüleyici pencerelerine FLAG_SECURE (ekran görüntüsü/kayıt engeli + app-switcher otomatik örtü)  _(P0 · efor: 0.5 gün)_
- **iOS davranışı:** Sınav içeriği güvenli canvas içinde render edilir → ekran görüntüsü ve kayıtta görünmez (best-effort). Hem görsel liste hem tam ekran pager hem PDF içerik korunur.
- **iOS karşılığı/kaynak:** SecureHostingView.swift (secure UITextField canvas), ScreenCaptureProtection liste/pager/PDF'e uygulanır.
- **Android mevcut:** Hiçbir ekran korumasız; ekran görüntüsü ve kayıt serbest, recents'ta içerik görünür.
- **Android teknoloji:** window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, FLAG_SECURE) — Android karşılığı: ekran görüntüsü engeller, ekran kaydında içerik siyah, recents/app-switcher önizlemesinde otomatik boş (ayrı örtü gerekmez). Activity.onCreate'te eklenir. Compose destination ise onCreate'te Activity penceresine; ayrı tam ekran Dialog/Activity varsa ona da ayrıca.
- **Adımlar:**
  - Görsel liste, tam ekran pager ve PDF görüntüleyiciyi barındıran Activity(ler)in onCreate'inde getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE).
  - Tam ekran pager ayrı Activity/Dialog ise onun penceresine de FLAG_SECURE ekle (pencere başına flag).
  - Bu ekranlardan çıkınca (yalnız bu ekranlar güvenli olacaksa) gerekiyorsa clearFlags; ya da tüm sınav akışını güvenli tut.
  - app-switcher örtüsü FLAG_SECURE ile otomatik sağlandığından ayrı overlay'e gerek yok — doğrula.
  - Test: bu ekranlarda ekran görüntüsü 'uygulama engelledi' uyarısı verir / siyah çıkar.
- **Gradle/bağımlılık:** —
- **Bağımlılık:** VIEW-01, VIEW-02, VIEW-07
- **Tuzaklar:** FLAG_SECURE pencere seviyesi; Compose Popup/Dialog kendi penceresini açarsa flag taşımaz. Ayrı tam ekran Activity açılıyorsa ona da ayrıca eklenmeli. Emülatör/bazı cihazlarda FLAG_SECURE harici ekran mirroring'i de engeller (beklenen).
- **Kabul kriteri:** Görsel liste, tam ekran ve PDF ekranlarında sistem ekran görüntüsü alınamaz (siyah/engel), ekran kaydında içerik görünmez, recents önizlemesinde içerik boş; diğer (korumasız) ekranlar etkilenmez.

#### `VIEW-11` — Ekran kaydı/mirroring sırasında açıklamalı opak örtü (iOS CaptureCover pariteli)  _(P2 · efor: 1 gün)_
- **iOS davranışı:** sceneCaptureState aktifken (kayıt/mirroring) sınav üzerine opak örtü: 'Ekran kaydı sırasında sınav içeriği gizlenir' + 'Kaydı durdurduğunda içerik yeniden görünür.' + eye.slash ikonu; kayıt bitince içerik döner.
- **iOS karşılığı/kaynak:** ScreenCaptureProtection.swift (CaptureStateReader UITraitSceneCaptureState, CaptureCoverView).
- **Android mevcut:** Yok.
- **Android teknoloji:** Android'de FLAG_SECURE zaten kayıtta içeriği siyaha çevirir; açıklayıcı metni göstermek için kayıt/mirroring tespiti gerekir: DisplayManager.registerDisplayListener + getDisplays(CATEGORY_PRESENTATION)/mirrored display tespiti; MediaProjection doğrudan tespit edilemez. Örtü: bu ekranlarda ek bir ekran (mirrored) bağlıyken Compose overlay (opak Box + ikon + metin) göster.
- **Adımlar:**
  - DisplayManager.DisplayListener ile ekran ekleme/çıkarma dinle; birincil olmayan/mirrored ekran varsa 'kayıt/yansıtma' varsay.
  - Bu durumda içeriğin üzerine opak örtü (Box fillMaxSize + Icons eye-off + 'Ekran kaydı sırasında sınav içeriği gizlenir' / 'Kaydı durdurduğunda içerik yeniden görünür.') göster.
  - Yansıtma/kayıt bitince örtüyü kaldır (DisplayListener onDisplayRemoved).
  - NOT: Yerel ekran kaydı (MediaProjection) güvenilir tespit edilemez → o senaryoda FLAG_SECURE'in otomatik siyahı devrededir; örtü yalnız mirroring için best-effort.
  - Metin ve davranışı iOS ile birebir eşle.
- **Gradle/bağımlılık:** —
- **Bağımlılık:** VIEW-10
- **Tuzaklar:** MediaProjection tabanlı ekran kaydı herkese açık API ile tespit edilemez; iOS'un sceneCaptureState'inin tam karşılığı yok — bu kalem best-effort (mirroring). FLAG_SECURE zaten koruduğu için düşük öncelik.
- **Kabul kriteri:** Harici ekrana yansıtma/mirroring başlayınca sınav içeriği üzerinde açıklamalı opak örtü belirir; yansıtma bitince içerik döner; yerel kayıtta en azından FLAG_SECURE ile içerik görünmez.

#### `VIEW-12` — Ekran görüntüsü uyarı banner'ı (Android 14 ScreenCaptureCallback) — 'Sınav içeriği korumalıdır'  _(P2 · efor: 0.5 gün)_
- **iOS davranışı:** userDidTakeScreenshot bildiriminde 2.5sn üstte 'Sınav içeriği korumalıdır' (lock ikonu) banner flash eder (içerik zaten korumalı, bu yalnız bilgilendirir).
- **iOS karşılığı/kaynak:** ScreenCaptureProtection.swift (userDidTakeScreenshotNotification, ScreenshotNoticeView, flash 2.5s).
- **Android mevcut:** Yok.
- **Android teknoloji:** Android 14 (API 34): Activity.registerScreenCaptureCallback(executor, ScreenCaptureCallback) + manifest izni android.permission.DETECT_SCREEN_CAPTURE. Callback'te Compose state ile üstte 2.5sn Snackbar/banner ('Sınav içeriği korumalıdır', lock ikonu) göster. Pre-34: güvenilir API yok → banner atlanır (FLAG_SECURE yine korur).
- **Adımlar:**
  - Manifest'e <uses-permission android:name='android.permission.DETECT_SCREEN_CAPTURE'/> ekle (API 34+).
  - Görüntüleyici Activity onStart'ta registerScreenCaptureCallback, onStop'ta unregister.
  - Callback tetiklenince üstte 2.5sn 'Sınav içeriği korumalıdır' banner (lock ikonu, koyu kapsül) göster, sonra gizle.
  - Build.VERSION.SDK_INT < 34 ise sessizce atla (FLAG_SECURE koruması yeterli).
  - Metin/ikon/süreyi iOS ile eşle.
- **Gradle/bağımlılık:** Manifest: DETECT_SCREEN_CAPTURE izni. targetSdk 34+ gerekir.
- **Bağımlılık:** VIEW-10
- **Tuzaklar:** ScreenCaptureCallback yalnız API 34+. İzin eklenmezse callback hiç tetiklenmez. FLAG_SECURE nedeniyle görüntü zaten boş çıkar; banner yalnız kullanıcıyı bilgilendirir.
- **Kabul kriteri:** Android 14+ cihazda bu ekranlarda ekran görüntüsü denemesinde 2.5sn uyarı banner'ı görünür; <14'te çökme yok (banner yok ama FLAG_SECURE korur).

#### `VIEW-13` — 'Bize Ulaş' (mailto) — her görüntüleyicide zarf aksiyonu, önceden doldurulmuş e-posta + sürüm altbilgisi, pano fallback  _(P1 · efor: 0.5 gün)_
- **iOS davranışı:** Görsel ve PDF görüntüleyici toolbar'ında zarf düğmesi → mailto buyukeryazilim@gmail.com, subject 'ViFi — Bize Ulaş', body = bağlam satırı 'Sınav: ÜNİ › … › 2019 FİNAL' (boş satır) + '—' + 'ViFi <sürüm> (<build>) · iOS <ver>'; kişisel veri yok. Mail istemcisi yoksa 'Mail uygulaması bulunamadı' + 'Adresi Kopyala'.
- **iOS karşılığı/kaynak:** ContactSupport.swift (email, subject, body context+version footer, mailtoURL, contactSupport modifier, pano fallback).
- **Android mevcut:** Yok.
- **Android teknoloji:** Intent(Intent.ACTION_SENDTO, Uri.parse('mailto:')) + putExtra(Intent.EXTRA_EMAIL, arrayOf('buyukeryazilim@gmail.com')), EXTRA_SUBJECT, EXTRA_TEXT (body). Sürüm: BuildConfig.VERSION_NAME + Build.VERSION.RELEASE (ör. 'ViFi 3.1.0 · Android 14'). Fallback: ActivityNotFoundException veya resolveActivity==null → ClipboardManager ile adresi kopyala + bilgi (Dialog/Snackbar). Paylaşılan ContactSupport util (object).
- **Adımlar:**
  - ContactSupport util (object/singleton): email sabiti, subject 'ViFi — Bize Ulaş', body(context) = bağlam satırı + '—' + 'ViFi ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE}'.
  - Görsel ve PDF görüntüleyici üst bar'ına zarf IconButton; supportContext = 'Sınav: ' + path.displayNames.join(' › ').
  - Tıklayınca ACTION_SENDTO mailto Intent'i başlat (EXTRA_EMAIL/SUBJECT/TEXT).
  - Mail istemcisi yoksa (ActivityNotFoundException) ClipboardManager ile e-posta adresini kopyala + 'Mail uygulaması bulunamadı, adres kopyalandı' bilgisi.
  - Kişisel veri (telefon/uid/ad) gövdeye koyma — iOS gibi yalnız sınav bağlamı + sürüm.
- **Gradle/bağımlılık:** —
- **Bağımlılık:** VIEW-01, VIEW-07, VIEW-06 (path/displayNames bağlamı)
- **Tuzaklar:** Android 11+ paket görünürlüğü: ACTION_SENDTO için manifest <queries> gerekebilir (resolveActivity null dönmesin). Intent.ACTION_SEND yerine ACTION_SENDTO kullan ki sadece mail uygulamaları çıksın (yoksa her uygulama listelenir).
- **Kabul kriteri:** İki görüntüleyicide de zarf düğmesi mail istemcisini alıcı/konu/gövde (sınav bağlamı + sürüm) dolu açar; mail istemcisi yoksa adres panoya kopyalanıp kullanıcı bilgilendirilir; gövdede kişisel veri yok.


---

## Coin Ekonomisi + Sınav Kilitleme/Açma  ·  `coin`

**Özet:** iOS master'da her dersin EN ESKİ 2 sınavı herkese açık; gerisi KİLİTLİ ve 100 coin ile (unlockExam callable, europe-west1, idempotent, sunucu-otoriter) kalıcı olarak açılıyor. Cüzdan (wallets/<uid>) canlı okunuyor, açılmış sınavlar (users/<uid>/unlocks) yükleniyor, her ekranda coin rozeti var, kilitli sınava dokununca açma sayfası (yeterli coin varsa "Aç (100 coin)", yoksa "Sınav Ekle") çıkıyor. Android şu an BUNLARIN HİÇBİRİNE sahip değil: CourseExamListActivity ilk 3 sınavı "Görmek için reklamı izleyiniz" ile AdMob rewarded/interstitial'a kilitliyor; cüzdan/coin/Firestore/Functions/AppCheck yok, RTDB 'Universitiess' ağacını okuyor, pre-AndroidX (com.android.support), AGP 3.4.2, Firebase SDK 2018. Bu alan birebir sıfırdan yazılacak; mevcut AdMob kilidi tamamen kaldırılacak (parite: monetizasyon coin ekonomisi, reklam YOK). Not: coin KAZANMA (crediting) admin moderasyonu tarafında; buradaki odak coin HARCAMA (unlock) ve cüzdan OKUMA.

**Android mevcut durum:** CourseExamListActivity.java (323 satır): RTDB getReference("Universitiess").child(uni/fak/bol/lesson) okuyor, ListViewAdapter+ListView ile gösteriyor. KİLİT MODELİ = REKLAM: imageNamesFB.size()<=3 ise TÜM satırları, değilse İLK 3 satırı "Görmek için lütfen reklamı izleyiniz." metniyle değiştiriyor (newPosition<3 → rewarded video zorunlu; >=3 → interstitial). Banner (AdView R.id.adView), InterstitialAd, RewardedVideoAd mevcut. Cüzdan/coin/unlock/Firestore/Functions/AppCheck/analytics-event YOK. build.gradle: compileSdk/targetSdk 28, minSdk 19, com.android.support:appcompat 27.1.0, firebase-database 16.1.0, firebase-storage/auth 16.x, play-services-ads 17.2.0, parse 1.17.3, android-pdf-viewer 2.3.0, picasso. AndroidX YOK, Firebase BoM YOK, Firestore YOK, Functions YOK, Compose YOK, Kotlin YOK, coroutines YOK. google-services plugin uygulanıyor; paket com.salticusteam.vifi, Firebase projes…

**Dokunulan backend sözleşmesi:** Bu alan ORTAK backend'e UYAR, yeni backend YAZMAZ. Okunan/çağrılan sözleşme: Firestore isimli 'vifi' DB (bölge eur3) → wallets/<uid> {balance:int>=0, updatedAt} (read: owner|admin, write: admin — client ASLA yazmaz); users/<uid>/unlocks/<examId> (server-yazar, read: owner|admin, write:false); exams/<id> {status:'published', kind}. Cloud Function unlockExam (onCall, europe-west1): +90 doğrular → exams/<id> published mı bakar → transaction: unlock varsa idempotent no-op {alreadyUnlocked:true,balance}; yoksa balance<100 ise failed-precondition, değilse 100 düş + unlocks yaz + 'spend' ledger ekle → {alreadyUnlocked:false,balance:newBalance}. UNLOCK_COST=100 (client ExamLock.unlockCost ile eşit). App Check: Firestore okumaları (wallet/unlocks) için enforce → Android Play Integrity provider ŞART (unlockExam callable'ın kendisi enforceAppCheck:true AYARLAMAMIŞ, yani App Check zorlamıyor; ama w…

**Android'e özgü riskler:**
- Firestore İSİMLİ veritabanı: FirebaseFirestore.getInstance(FirebaseApp.getInstance(), "vifi") yalnızca modern SDK'de var (firestore >= 24.10, ~Firebase BoM 32.1.0+). Eski SDK sadece (default) DB'ye bağlanır → wallets/unlocks okumaları yanlış DB'ye gider ve kurallar reddeder. SESSİZ hata riski.
- Callable BÖLGESİ: FirebaseFunctions.getInstance() varsayılan us-central1'e gider → unlockExam 404/NOT_FOUND. Mutlaka FirebaseFunctions.getInstance("europe-west1") kullanılmalı.
- App Check zorlaması: wallet/unlocks Firestore okumaları App Check enforce. PlayIntegrityAppCheckProviderFactory kurulmazsa okumalar PERMISSION_DENIED döner ve cüzdan hep 0 görünür. Play Console'da uygulama + Firebase'de Play Integrity yapılandırması + debug build için DebugAppCheckProviderFactory debug token şart.
- Pre-AndroidX + ölü jcenter: modern Firebase BoM/Firestore/Functions/AppCheck AndroidX gerektirir. com.android.support ile birlikte derlenmez → tam AndroidX migrasyonu (başka alanın işi) bu alanın SERT ön koşulu.
- targetSdk 28 → Play artık targetSdk 34+ zorunlu; Play Integrity ve modern Firebase de güncel Play Services ister. compile/target SDK yükseltilmeli.
- Task→coroutine köprüsü: kotlinx-coroutines-play-services .await() yoksa callback cehennemi; saf Java kalınırsa addOnSuccessListener/addOnFailureListener ile yazılmalı.
- ListenerRegistration sızıntısı: wallet snapshot listener onCleared()/onStop'ta registration.remove() ile kaldırılmazsa sızar ve sign-out sonrası eski kullanıcının cüzdanını yayınlar.
- R8/ProGuard: callable payload/response Map<String,Object> olarak okunduğundan POJO keep kuralı gerekmez; ama Firebase/Functions için varsayılan consumer ProGuard kuralları korunmalı (minify açılırsa).
- Serbest/kilit hesabı: Firestore exams sorgusu client tarafında NEWEST-FIRST sıralanmazsa (iOS: Türkçe+numeric artan sonra ters) yanlış 2 sınav ücretsiz olur. Sıralama exams listeleme (Browse/Archive migrasyonu) alanıyla TUTARLI olmalı.
- Exam id bağımlılığı: unlockExam'e geçen id Firestore exams/<id> doküman id'si olmalı; mevcut RTDB düğüm adı (imagename) DEĞİL. Arşiv Firestore'a taşınmadan unlock çalışmaz.

### İş Kalemleri

#### `T1` — Firestore(vifi)+Functions(europe-west1)+App Check bağımlılıklarını ekle; AdMob'u kaldır (Gradle)  _(P0 · efor: Orta)_
- **iOS davranışı:** Backend tek noktadan: ViFiFirestore.shared (named 'vifi'), Functions.functions(region:'europe-west1'); reklam yok
- **iOS karşılığı/kaynak:** FirebaseWalletReader.swift (ViFiFirestore.shared), FirebaseExamUnlockService.swift (region='europe-west1')
- **Android mevcut:** firebase-database 16.1.0 + play-services-ads 17.2.0; Firestore/Functions/AppCheck/BoM YOK; AGP 3.4.2, targetSdk 28, jcenter
- **Android teknoloji:** com.google.firebase:firebase-bom, firebase-firestore, firebase-functions, firebase-appcheck-playintegrity/-debug, kotlinx-coroutines-play-services, FirebaseFirestore.getInstance(app,"vifi"), FirebaseFunctions.getInstance("europe-west1")
- **Adımlar:**
  - app/build.gradle'a Firebase BoM ekle: implementation platform('com.google.firebase:firebase-bom:33.5.1') (veya güncel) ve altında sürümsüz: firebase-firestore, firebase-functions, firebase-appcheck-playintegrity, firebase-appcheck-debug (debug flavor), firebase-auth, firebase-analytics.
  - Kotlin seçildiyse: implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0' (Task.await() için) ve kotlinx-coroutines-android.
  - AdMob'u kaldır: play-services-ads 17.2.0 bağımlılığını sil; AndroidManifest'ten APPLICATION_ID meta-data ve AdActivity varsa temizle; libs/ içinde ads ile ilgili artefakt kalmasın.
  - compileSdkVersion ve targetSdkVersion'ı 34+ yap; minSdk 19→21 (Firestore/modern Firebase min 21).
  - settings.gradle/google-services plugin'i modern sürüme çek (com.google.gms:google-services 4.4.x); jcenter() kalıntılarını google()+mavenCentral() ile değiştir.
  - Named DB + callable region yardımcıları için tek erişim noktası oluştur: object Backend { val db = Firebase.firestore(FirebaseApp.getInstance(), "vifi"); val functions = FirebaseFunctions.getInstance("europe-west1") } (iOS ViFiFirestore.shared karşılığı).
- **Gradle/bağımlılık:** Firebase BoM 33.x + firestore/functions/appcheck; play-services-ads KALDIR; google-services 4.4.x; targetSdk 34+, minSdk 21
- **Bağımlılık:** AndroidX migrasyonu (platform modernizasyon alanı) — SERT ön koşul, App Check Play Integrity kurulumu T13
- **Tuzaklar:** getInstance(app,"vifi") sadece firestore>=24.10'da var — BoM yeterince yeni olmalı. play-services-ads kaldırınca CourseExamListActivity derleme hataları çıkar (T11 ile birlikte yapılmalı). AndroidX olmadan modern Firebase derlenmez.
- **Kabul kriteri:** Proje modern Firebase BoM ile derleniyor; FirebaseFirestore'un named-DB getInstance(app,String) imzası çözülüyor; FirebaseFunctions.getInstance("europe-west1") erişilebilir; AdMob bağımlılığı ve manifest meta-data'sı kalmadı; debug build'de debug App Check provider aktif.

#### `T2` — CoinWallet modeli + cüzdan canlı okuma (wallets/<uid> snapshot listener)  _(P0 · efor: Orta)_
- **iOS davranışı:** wallets/<uid> canlı snapshot; missing/negatif→0; her değişimde yayınla; hata→.empty; task iptalinde listener remove
- **iOS karşılığı/kaynak:** FirebaseWalletReader.swift (walletChanges AsyncStream + parse, max(0,balance)), CoinWallet.swift
- **Android mevcut:** Cüzdan/coin kavramı YOK; sadece RTDB ValueEventListener örüntüsü var
- **Android teknoloji:** FirebaseFirestore DocumentReference.addSnapshotListener, ListenerRegistration, kotlinx-coroutines callbackFlow / LiveData, com.google.firebase.Timestamp
- **Adımlar:**
  - data class CoinWallet(val balance: Int, val updatedAt: Date?) ve companion EMPTY = CoinWallet(0, null) oluştur (iOS CoinWallet + .empty).
  - WalletRepository/WalletReader arayüzü: fun walletChanges(uid: String): Flow<CoinWallet> (iOS WalletReading protokolü).
  - FirestoreWalletReader: Backend.db.collection("wallets").document(uid).addSnapshotListener((snap,err)->...) içinde parse; Flow için callbackFlow{...; awaitClose{ registration.remove() }} kullan.
  - parse(data): balance = (data["balance"] as? Number)?.toInt() ?: 0; updatedAt = (data["updatedAt"] as? Timestamp)?.toDate(); return CoinWallet(max(0,balance), updatedAt). Doküman yok/err → CoinWallet.EMPTY emit et (iOS ile birebir).
  - Java+Views alternatifi: ListenerRegistration tut, MutableLiveData<CoinWallet> yayınla, onError'da EMPTY postValue; Activity/Fragment onDestroy'da registration.remove().
- **Gradle/bağımlılık:** firebase-firestore (T1)
- **Bağımlılık:** T1 (Firestore), T13 (App Check)
- **Tuzaklar:** App Check enforce ise provider kurulmadan okuma PERMISSION_DENIED → EMPTY yayılır ve balance hep 0 görünür (T13 bağımlılığı). Firestore Number Long gelebilir → toInt(). callbackFlow bufferingNewest(1) davranışı için Channel.CONFLATED kullan.
- **Kabul kriteri:** Sign-in sonrası cüzdan balance anlık okunuyor; admin bir onay ile balance değişince Flow yeni değeri yayıyor; doküman yokken 0 görünüyor; negatif/eksik alan 0'a clamp ediliyor; listener awaitClose/onDestroy'da kaldırılıyor (sızıntı yok).

#### `T3` — CoinWalletStore eşdeğeri: uygulama-ömrü cüzdan durumu + sign-in/sign-out yaşam döngüsü  _(P0 · efor: Orta)_
- **iOS davranışı:** RootView .task(id:uid) wallet.observe(uid); sign-out'ta wallet.reset(); rozet her yerde aynı anda güncellenir
- **iOS karşılığı/kaynak:** CoinWalletStore.swift, RootView.swift (satır 49 reset, 220 observe)
- **Android mevcut:** Uygulama-ömürlü paylaşılan kullanıcı durumu YOK; her Activity kendi RTDB sorgusunu yapıyor
- **Android teknoloji:** StateFlow/MutableStateFlow, Hilt veya manuel DI (AppContainer), FirebaseAuth.AuthStateListener, lifecycleScope, collectAsStateWithLifecycle
- **Adımlar:**
  - Uygulama-ömürlü tek cüzdan durumu kaynağı oluştur: WalletStore (singleton veya Hilt @Singleton) — val wallet: StateFlow<CoinWallet>; val balance: Int get()=wallet.value.balance (iOS CoinWalletStore).
  - fun observe(uid): collectlerken reader.walletChanges(uid)'i StateFlow'a aktar; auth kullanıcısı değişince eski collect iptal (viewModelScope / lifecycle-aware; iOS .task(id:uid) karşılığı).
  - fun reset(): wallet.value = CoinWallet.EMPTY (sign-out).
  - Auth state dinleyicisine bağla: FirebaseAuth.getInstance().addAuthStateListener; kullanıcı null→reset(); yeni uid→observe(uid). Compose'da bunu Application/AppContainer seviyesinde başlat, ekranlar collectAsStateWithLifecycle ile tüketsin.
  - Java+Views alternatifi: WalletStore'u Application sınıfında başlat, LiveData expose et; LoginActivity/HomeActivity auth değişiminde observe/reset çağır.
- **Gradle/bağımlılık:** androidx.lifecycle (viewModel, lifecycle-runtime-compose)
- **Bağımlılık:** T2 (WalletReader), Auth/oturum alanı (uid kaynağı)
- **Tuzaklar:** AuthStateListener birden çok kez tetiklenebilir; aynı uid için tekrar observe başlatma (job iptal/yeniden başlat). reset() mutlaka observe iptali ile birlikte, yoksa eski Flow EMPTY üzerine yazar.
- **Kabul kriteri:** Sign-in olunca balance tüm ekranlarda aynı değeri gösteriyor; sign-out olunca anında 0'a dönüyor; kullanıcı A→B geçişinde A'nın bakiyesi görünmüyor; süreç boyunca tek listener açık.

#### `T4` — ExamLock kuralı: freeCount=2, unlockCost=100, newest-first serbest-hesap + isAccessible (saf util + unit test)  _(P0 · efor: Küçük)_
- **iOS davranışı:** En ESKİ 2 ücretsiz (liste newest-first, suffix(2)); accessible=free||unlocked; sadece dosyalı sınav kilitlenir; non-exam her zaman açık
- **iOS karşılığı/kaynak:** ExamLock.swift (freeCount=2, unlockCost=100, freeExamIds, isAccessible), BrowseView.isLocked (satır 138)
- **Android mevcut:** Serbest/kilit kuralı YOK; mevcut kural 'ilk 3 reklamla' (ters mantık, en yeniler kilitli)
- **Android teknoloji:** Saf Kotlin object, JUnit4/5 (test/ kaynak seti), kotlin takeLast
- **Adımlar:**
  - object ExamLock { const val FREE_COUNT = 2; const val UNLOCK_COST = 100 } oluştur (Firebase'den bağımsız, saf).
  - fun freeExamIds(orderedExams: List<ArchiveItem>): Set<String> = orderedExams.takeLast(FREE_COUNT).mapNotNull { it.examId }.toSet() — liste NEWEST-FIRST olduğundan en eski 2 = son 2 (iOS suffix(2)).
  - fun isAccessible(exam, freeIds, unlockedIds): if (exam.level != EXAM || exam.examId == null) return true; return freeIds.contains(id) || unlockedIds.contains(id).
  - fun isLocked(exam, freeIds, unlockedIds): exam.level==EXAM && exam.hasFiles && !isAccessible(...) (BrowseView.isLocked: dosyasız sınav kilitli değil).
  - JUnit testleri: 5 sınavlık newest-first listede son 2 serbest; 1 sınav → o serbest; 3 sınav → ilk 1 kilitli; unlockedIds içeren kilitliyi açık say; sınav-dışı/dosyasız öğe her zaman accessible.
- **Gradle/bağımlılık:** Yok (saf), test için junit
- **Bağımlılık:** ArchiveItem modeli (Arşiv/Browse alanı) — examId ve level alanları
- **Tuzaklar:** Eski Android 'ilk 3 kilitli' mantığı YANLIŞ yöne kilitliyordu; yeni kuralda en yeniler açık, en eskiler ücretsiz değil — ürün beklentisi iOS ile aynı olmalı. examId listenin doğru sıralanmasına bağlı (T10/Arşiv migrasyonu).
- **Kabul kriteri:** Unit testler yeşil; UNLOCK_COST=100 ve sunucu UNLOCK_COST ile birebir; ücretsiz olanlar listenin EN ESKİ 2'si (en yeniler kilitli DEĞİL — eski AdMob mantığının TERSİ düzeltilmiş); dosyasız sınav kilitli sayılmıyor.

#### `T5` — ExamUnlocksStore: users/<uid>/unlocks okuma + optimistik markUnlocked + reset  _(P0 · efor: Küçük)_
- **iOS davranışı:** users/<uid>/unlocks tek-seferlik yükleme; başarısızlık→küme boş (daha fazla kilit güvenli); açılan sınav optimistik eklenir (re-read yok); sign-out'ta reset
- **iOS karşılığı/kaynak:** ExamUnlocksStore.swift, FirebaseUnlocksReader.swift (documentID set), UnlocksReading.swift
- **Android mevcut:** Açılmış sınav kavramı YOK
- **Android teknoloji:** Firestore CollectionReference.get() + Task.await(), MutableStateFlow, kotlinx-coroutines
- **Adımlar:**
  - UnlocksReader arayüzü: suspend fun unlockedExamIds(uid): Set<String> (iOS UnlocksReading).
  - FirestoreUnlocksReader: Backend.db.collection("users").document(uid).collection("unlocks").get().await() → snapshot.documents.map { it.id }.toSet() (doküman id = exam id).
  - UnlocksStore (singleton): private val _unlocked = MutableStateFlow<Set<String>>(emptySet()); fun isUnlocked(id)= _unlocked.value.contains(id); suspend fun load(uid){ try{ _unlocked.value = reader.unlockedExamIds(uid) } catch(CancellationException){throw it} catch(e){ log; küme BOŞ kalır } }; fun markUnlocked(id){ _unlocked.update{ it+id } }; fun reset(){ _unlocked.value=emptySet() }.
  - Yaşam döngüsü: T3 ile aynı auth akışında sign-in'de load(uid), sign-out'ta reset().
  - Java+Views alternatifi: Set<String> + MutableLiveData; get() addOnSuccessListener ile; hata→set boş bırak.
- **Gradle/bağımlılık:** firebase-firestore (T1)
- **Bağımlılık:** T1 (Firestore), T13 (App Check), Auth alanı (uid)
- **Tuzaklar:** CancellationException yeniden fırlatılmalı (sign-out/yeni yükleme eski yüklemeyi geçersiz kıldığında). App Check enforce ise unlocks okuması da provider ister (T13).
- **Kabul kriteri:** Sign-in sonrası daha önce açılmış sınavlar kilitsiz görünüyor; bir sınav açıldığında re-read olmadan markUnlocked ile satır anında açılıyor; load hatası kümeyi boş bırakıyor (hiçbir şey yanlışlıkla açık görünmüyor); sign-out'ta küme temizleniyor.

#### `T6` — ExamUnlockService: unlockExam callable çağrısı + Türkçe hata eşleme  _(P0 · efor: Orta)_
- **iOS davranışı:** callable {examId} gönderir → {balance, alreadyUnlocked}; idempotent; hata kodları Türkçe mesaja eşlenir; analyticsReason düşük kardinalite
- **iOS karşılığı/kaynak:** FirebaseExamUnlockService.swift (region='europe-west1', name='unlockExam', unlockError mapping), ExamUnlockServicing.swift (ExamUnlockError, analyticsReason), index.js (unlockExam)
- **Android mevcut:** Cloud Functions çağrısı YOK; coin harcama kavramı YOK (reklam izleme vardı)
- **Android teknoloji:** FirebaseFunctions.getInstance("europe-west1").getHttpsCallable("unlockExam").call(Map), HttpsCallableResult.getData(), FirebaseFunctionsException.Code, Task.await()
- **Adımlar:**
  - data class ExamUnlockResult(val balance: Int, val alreadyUnlocked: Boolean) (iOS karşılığı).
  - sealed class ExamUnlockError(val userMessage: String, val analyticsReason: String): InsufficientCoins("Bu sınavı açmak için yeterli coin yok.","insufficient_coins"), NotFound("Sınav bulunamadı.","not_found"), NotSignedIn("Oturumun sona erdi. Lütfen yeniden giriş yap.","not_signed_in"), Offline("İnternet bağlantısı yok. Bağlantını kontrol edip tekrar dene.","offline"), Unknown(message).
  - ExamUnlockService.unlock(examId): val result = Backend.functions.getHttpsCallable("unlockExam").call(mapOf("examId" to examId)).await(); val data = result.data as? Map<*,*> ?: emptyMap; balance = (data["balance"] as? Number)?.toInt() ?: 0; alreadyUnlocked = data["alreadyUnlocked"] as? Boolean ?: false.
  - Hata eşleme: catch(e: FirebaseFunctionsException){ when(e.code){ FAILED_PRECONDITION->InsufficientCoins; NOT_FOUND->NotFound; PERMISSION_DENIED,UNAUTHENTICATED->NotSignedIn; UNAVAILABLE,DEADLINE_EXCEEDED->Offline; else->Unknown(e.message) } } catch(e: IOException){ Offline } (iOS: FunctionsErrorCode + NSURLErrorDomain).
  - Java+Views alternatifi: getHttpsCallable(...).call(data).addOnSuccessListener/.addOnFailureListener; failure'da ((FirebaseFunctionsException)e).getCode() ile eşle.
- **Gradle/bağımlılık:** firebase-functions (T1)
- **Bağımlılık:** T1 (Functions), Auth +90 oturumu, Arşiv Firestore exams/<id> id kaynağı (T10)
- **Tuzaklar:** BÖLGE: getInstance() (bölgesiz) us-central1'e gider → NOT_FOUND yanılgısı; mutlaka "europe-west1". result.data Map<String,Object>, Long gelebilir → Number.toInt(). examId Firestore exams/<id> id'si olmalı. Callable App Check zorlamıyor ama Auth +90 şart (aksi halde PERMISSION_DENIED).
- **Kabul kriteri:** Yeterli coin ile çağrı 100 düşürüp {alreadyUnlocked:false,balance:yeni} dönüyor; tekrar çağrıldığında {alreadyUnlocked:true} coin düşmeden dönüyor; yetersiz coinde InsufficientCoins (failed-precondition); yanlış id'de NotFound; çevrimdışında Offline; her hata doğru Türkçe mesaj + sabit analyticsReason veriyor.

#### `T7` — CoinBalanceBadge: her ekranın toolbar'ında canlı coin rozeti  _(P1 · efor: Küçük)_
- **iOS davranışı:** Her ekran toolbar'ında canlı balance; admin kredi verince her yerde anında güncellenir; sayı animasyonlu; intrinsic genişlik korunur
- **iOS karşılığı/kaynak:** CoinBalanceBadge.swift, BrowseView.swift toolbar (ToolbarItem topBarLeading CoinBalanceBadge)
- **Android mevcut:** Coin rozeti YOK; toolbar'larda sadece başlık/AdView var
- **Android teknoloji:** Jetpack Compose material3 (Scaffold/TopAppBar/Row/Icon/AnimatedContent), collectAsStateWithLifecycle; alt: Toolbar actionView + LiveData
- **Adımlar:**
  - Compose: @Composable fun CoinBalanceBadge(balance: Int) — Row(capsule/RoundedCornerShape(50), turuncu %14 arka plan) { Icon(coin, tint=orange); AnimatedContent/Crossfade ile Text(balance, monospace) }. Sayı değişiminde yumuşak animasyon (iOS contentTransition(.numericText)).
  - balance'ı WalletStore.wallet.collectAsStateWithLifecycle().value.balance'tan oku; her ekranın Scaffold TopAppBar actions = { CoinBalanceBadge(balance) }.
  - contentDescription="Coin bakiyen" + değeri (iOS accessibilityLabel/Value); test tag "coin.balance".
  - Coin ikonu: Material Symbols'da birebir yok → vektör drawable (ic_coin, turuncu daire içinde ₿/coin) ekle; iOS bitcoinsign.circle.fill benzeri.
  - Java+Views alternatifi: menu/actionLayout olarak custom TextView+ImageView; her Activity toolbar'ına include; WalletStore LiveData observe edip setText; ValueAnimator ile sayı animasyonu.
- **Gradle/bağımlılık:** Compose BOM (androidx.compose:compose-bom) veya mevcut Views
- **Bağımlılık:** T3 (WalletStore)
- **Tuzaklar:** Toolbar genişliği sayıyı sıkıştırmasın (iOS fixedSize notu) — min genişlik ver. Rozet shared state'ten beslenmeli, her ekran kendi sorgusunu AÇMAMALI.
- **Kabul kriteri:** İlgili tüm ekranların toolbar'ında güncel bakiye görünüyor; bakiye değişince tüm açık ekranlarda aynı anda güncelleniyor; sayı yumuşak animasyonla değişiyor; TalkBack 'Coin bakiyen, X' okuyor.

#### `T8` — Sınav satırında kilit rozeti (LockBadge) + kilitli satır tıklama davranışı  _(P1 · efor: Küçük)_
- **iOS davranışı:** Kilitli sınav satırı format rozeti yerine lock+100 gösterir; dokununca gezinmez, açma sheet'i açılır
- **iOS karşılığı/kaynak:** ArchiveRow.swift (LockBadge, badge(for:) isLocked dalı), ArchiveNavigationRow (isLocked→Button onLockedTap)
- **Android mevcut:** ListViewAdapter satırları; ilk 3'ü 'Görmek için reklamı izleyiniz' metniyle; kilit rozeti/ikonu YOK
- **Android teknoloji:** Compose Row/Icon/Text + Modifier.clickable; alt: RecyclerView.Adapter onBindViewHolder, ImageView/TextView
- **Adımlar:**
  - Compose exam satırı: format rozeti yerine kilitliyse LockBadge göster: Row { Icon(lock, tint=orange); Text("100", monospace) } turuncu %14 kapsül (iOS LockBadge cost=100).
  - Satır tıklama: isLocked ise NAVIGATE ETME → onLockedTap(exam) çağır (açma sheet'ini aç). Kilitli değilse normal gezinme/sınav açma.
  - contentDescription "Kilitli, 100 coin" + "Kilitli; açmak için dokun" hint (iOS accessibilityHint).
  - Dosyasız sınav (hasFiles=false): kilitli değil ama açılamaz (disabled görünüm, 'Dosya bulunamadı' alt metni) — iOS ArchiveNavigationRow disabled dalı.
  - Java+Views alternatifi: ListViewAdapter/RecyclerView.Adapter'da getView/onBind içinde isLocked'a göre rozet drawable'ı ve onClick davranışını ayır; kilitli öğede item click → sheet; aksi halde mevcut akış.
- **Gradle/bağımlılık:** Yok
- **Bağımlılık:** T4 (ExamLock), T5 (unlocks), T10 (liste entegrasyonu)
- **Tuzaklar:** isLocked hesabı TAM yüklenmiş liste üzerinden (arama filtresi üzerinden değil). Kilitli öğeyi yanlışlıkla NavigationLink'e bağlama — ayrı clickable dalı olmalı.
- **Kabul kriteri:** Kilitli sınavlar satırda kilit+100 rozetiyle görünüyor; kilitli satıra dokununca viewer açılmıyor, açma sheet'i açılıyor; ücretsiz/açık sınavlar normal gezinme yapıyor; dosyasız sınav devre dışı ve 'Dosya bulunamadı' gösteriyor.

#### `T9` — ExamUnlockSheet: ModalBottomSheet (afford/earn dalları, progress, hata, success→aç)  _(P0 · efor: Orta)_
- **iOS davranışı:** Kilitli dokunuşta sheet; afford→'Aç (100 coin)'+bakiye+kalıcılık notu; yetersiz→'Sınav Ekle'; progress; hata kırmızı; başarıda kapan+aç; alreadyUnlocked de başarı
- **iOS karşılığı/kaynak:** ExamUnlockSheet.swift (affordContent/earnContent/performUnlock), BrowseView.swift sheet(item:) wiring
- **Android mevcut:** Açma sayfası/sheet YOK; kilit akışı = rewarded video dialog
- **Android teknoloji:** androidx.compose.material3 ModalBottomSheet/Button/CircularProgressIndicator/SheetState; alt: BottomSheetDialogFragment
- **Adımlar:**
  - Compose: ModalBottomSheet (material3), orta yükseklik; içerik: kilit ikonu (44dp, orange), examName (headline, ortalı), 'Bu sınav kilitli.' (secondary).
  - canAfford = balance >= ExamLock.UNLOCK_COST. Afford dalı: Label 'coin ikonu 100 coin · Bakiyen: {balance}', dolgulu buton 'Aç (100 coin)' (işlerken CircularProgressIndicator), altında 'Açtığın sınav hesabında kalıcı olur, bir daha kilitlenmez.'
  - Earn dalı (coin yetersiz): metin 'Coin kazanmak için uygulamada olmayan sınavları ekleyebilirsin.' + dolgulu buton 'Sınav Ekle' → onEarnCoins().
  - Hata durumu: başarısız unlock mesajını kırmızı alt metinde göster (test tag exam.unlock.error); işlem sürerken sheet dışarı-dokunuşla kapanmasın (interactiveDismissDisabled karşılığı: sheet dismissOnClickOutside=false while working).
  - Buton aksiyonu: isWorking=true → unlock() çağır → başarıda sheet'i kapat + onUnlocked(); hatada mesajı göster, isWorking=false (iOS performUnlock).
  - Java+Views alternatifi: BottomSheetDialogFragment + özel layout; ProgressBar görünürlüğü; setCancelable(false) while working.
- **Gradle/bağımlılık:** Compose material3 (veya Material Components BottomSheetDialogFragment)
- **Bağımlılık:** T6 (unlock service), T5 (markUnlocked), T13? (App Check okuma), AddExam akışı (onEarnCoins hedefi)
- **Tuzaklar:** Başarı callback'i unlock DÖNÜŞÜNDEN sonra markUnlocked + open sırasıyla olmalı (T10). alreadyUnlocked=true hata değil, başarı yolu. Sheet durumu config değişiminde (rotation) korunmalı (rememberSaveable/ViewModel).
- **Kabul kriteri:** Yeterli coinli kullanıcı 'Aç (100 coin)' ile sınavı açıyor, sheet kapanıp sınav açılıyor, bakiye 100 azalıyor; yetersiz coinli kullanıcı 'Sınav Ekle' görüyor; işlem sürerken buton progress gösterip sheet kapanmıyor; hata Türkçe mesajla kırmızı görünüyor; kalıcılık notu görünüyor.

#### `T10` — CourseExamList'i yeniden yaz: kilit durumunu listeye entegre et (serbest hesap, isLocked, sheet, başarı sonrası aç)  _(P0 · efor: Büyük)_
- **iOS davranışı:** Browse exam listesi: freeExamIds tam listeden; isLocked; kilitli→sheet; başarıda markUnlocked+open; earn→AddExam; her adımda analytics
- **iOS karşılığı/kaynak:** BrowseView.swift (freeExamIds/isLocked/presentUnlockSheet/unlock/open), ArchiveRow.swift
- **Android mevcut:** CourseExamListActivity RTDB 'Universitiess' okuyor, ilk 3'ü reklamla gizliyor, tıklamada rewarded/interstitial gösteriyor
- **Android teknoloji:** Firestore Query where/get veya snapshot, Jetpack Navigation/Compose, ViewModel+StateFlow, mevcut ExamDetailActivity/ExamDetailPdfActivity
- **Adımlar:**
  - CourseExamListActivity'i (veya Compose ExamListScreen'i) RTDB yerine Firestore exams sorgusuna bağla: where(courseId==X, status=='published'), client tarafında NEWEST-FIRST sırala (Türkçe+numeric artan sonra ters; iOS ArchiveParser.sorted exam reversed). [Not: sorgu/sıralama Arşiv migrasyonu alanıyla paylaşılır].
  - freeExamIds = ExamLock.freeExamIds(orderedExams) — TAM liste üzerinden (arama filtresi değil).
  - Her satır için isLocked = ExamLock.isLocked(item, freeIds, WalletStore/UnlocksStore). Satırı T8 kilit davranışıyla çiz.
  - Kilitli dokunuşta: analytics unlock_sheet_shown{can_afford = balance>=100}; ExamUnlockSheet'i examName+balance ile aç.
  - unlock lambdası: examId = item.examId; service.unlock(examId); başarıda UnlocksStore.markUnlocked(examId) + analytics exam_unlock{kind,coins_spent=100,already_unlocked}; hata→analytics exam_unlock_failed{reason} + mesaj döndür.
  - onUnlocked: sınav viewer'ını aç (kind'e göre ExamDetailActivity/ExamDetailPdfActivity veya Compose viewer). onEarnCoins: analytics earn_coins_tapped + AddExam akışını aç.
  - ViewModel: ExamListViewModel (StateFlow<state>), wallet+unlocks state'lerini birleştir; Activity/Composable collectAsStateWithLifecycle.
- **Gradle/bağımlılık:** firebase-firestore, Navigation, ViewModel
- **Bağımlılık:** T4, T5, T6, T8, T9, T12, Arşiv Firestore migrasyonu (Browse alanı), AddExam akışı (T13 earn hedefi)
- **Tuzaklar:** Bu kalem Arşiv migrasyonuna (Firestore exams + courseId + doc id) SIKI bağımlı — o tamamlanmadan examId ve liste gelmez. Sıralama iOS ile tutarsızsa yanlış sınavlar ücretsiz olur. Mevcut 'Universitiess' RTDB okuma kodu tamamen çıkarılacak.
- **Kabul kriteri:** Ekranda en eski 2 sınav ücretsiz açılıyor, gerisi kilitli; kilitliye dokunup açma akışı tamamlanınca sınav açılıyor ve satır kalıcı kilitsiz oluyor; coin bakiyesi rozet+sheette doğru; reklam HİÇ gösterilmiyor; başarı/başarısızlık/earn analytics olayları atılıyor.

#### `T11` — AdMob'u bu akıştan tamamen kaldır (banner/interstitial/rewarded + reklam-kapısı)  _(P0 · efor: Orta)_
- **iOS davranışı:** iOS'ta reklam HİÇ yok; monetizasyon tamamen coin ekonomisi
- **iOS karşılığı/kaynak:** (iOS'ta karşılık yok — parite 'reklam yok')
- **Android mevcut:** Banner+Interstitial+Rewarded aktif; sınav erişimi reklam izlemeye kilitli (newPosition<3 rewarded, >=3 interstitial)
- **Android teknoloji:** play-services-ads kaldırma (T1 ile), AndroidManifest, layout XML
- **Adımlar:**
  - CourseExamListActivity'den tüm AdMob'u sök: MobileAds.initialize, AdView/R.id.adView, InterstitialAd, RewardedVideoAd, RewardedVideoAdListener implements, requestNewInterstitial/loadRewardedVideoAd/rewardedAdMethod ve onRewarded* override'ları.
  - 'imageNamesFB.size()<=3 → Görmek için lütfen reklamı izleyiniz' kapısını SİL; satırlar gerçek sınav adlarını göstersin (kilit durumu T8/T10'dan).
  - activity_course_exam_list.xml ve diğer layout'lardan <com.google.android.gms.ads.AdView> ve ad container'larını kaldır.
  - AndroidManifest'ten AdMob APPLICATION_ID meta-data ve com.google.android.gms.ads.AdActivity'yi sil; INTERNET dışı reklam izinleri varsa temizle.
  - Diğer Activity'lerde (Home/ExamDetail vb.) kalan AdMob referanslarını da ara-temizle (grep play-services-ads/MobileAds/AdView/Interstitial/Rewarded).
- **Gradle/bağımlılık:** play-services-ads bağımlılığını kaldır (T1)
- **Bağımlılık:** T1 (gradle), T10 (liste yeniden yazımı)
- **Tuzaklar:** T1'de bağımlılık kaldırılınca CourseExamListActivity derlenmez — bu iki kalem birlikte yapılmalı. AdMob APPLICATION_ID meta-data kalırsa modern Play Services başlatma hatası verebilir.
- **Kabul kriteri:** Uygulamada hiçbir banner/interstitial/rewarded gösterilmiyor; sınav açmak için video izleme yok; derleme play-services-ads olmadan geçiyor; manifest/layout'ta AdMob kalıntısı yok; sınav erişimi yalnız coin kilidine bağlı.

#### `T12` — Coin/unlock analitik olayları (unlock_sheet_shown, earn_coins_tapped, exam_unlock, exam_unlock_failed)  _(P1 · efor: Küçük)_
- **iOS davranışı:** 4 olay sabit adlar+düşük kardinalite paramlarla; reason asla lokalize mesaj değil
- **iOS karşılığı/kaynak:** FirebaseAnalyticsTracker.swift (satır 33-36, 63-66, 85-87), ServiceProtocols.swift (108-111), BrowseView.swift analytics çağrıları
- **Android mevcut:** Bu domain olayları YOK (firebase-core 16.0.8 var ama coin olayı yok)
- **Android teknoloji:** FirebaseAnalytics.logEvent(name, Bundle), com.google.firebase:firebase-analytics
- **Adımlar:**
  - FirebaseAnalytics.getInstance(context) ile olayları logla (firebase-analytics, T1).
  - unlock_sheet_shown: param can_afford(Boolean) — kilitli dokunuşta (T10).
  - earn_coins_tapped: parametresiz — 'Sınav Ekle' tıklanınca.
  - exam_unlock: exam_kind("JPG"/"PDF"), coins_spent(100), already_unlocked(Boolean) — başarıda.
  - exam_unlock_failed: reason(düşük kardinalite: insufficient_coins/not_found/not_signed_in/offline/unknown) — hatada (ExamUnlockError.analyticsReason).
  - Olay adlarını ve param anahtarlarını iOS FirebaseAnalyticsTracker ile BİREBİR tut (unlock_sheet_shown, earn_coins_tapped, exam_unlock, exam_unlock_failed; exam_kind/coins_spent/already_unlocked/reason).
- **Gradle/bağımlılık:** firebase-analytics (T1)
- **Bağımlılık:** T6 (analyticsReason), T9/T10 (tetikleme noktaları)
- **Tuzaklar:** Olay/param adları iOS'tan sapmamalı yoksa Firebase konsolunda ayrı metrikler oluşur. reason'a localizedDescription koyma (kardinalite patlar).
- **Kabul kriteri:** DebugView'da 4 olay doğru ad ve paramlarla görünüyor; reason düşük-kardinalite sabit değerler; param adları iOS ile aynı (cross-platform rapor birleşir); coins_spent=100.

#### `T13` — App Check (Play Integrity) kurulumu — wallet/unlocks Firestore okumaları için  _(P0 · efor: Orta)_
- **iOS davranışı:** App Check Storage/RTDB/Auth enforce; Firestore monitoring→enforce; iOS App Attest/DeviceCheck kullanır
- **iOS karşılığı/kaynak:** ViFiAppCheckProviderFactory (iOS), index.js unlockExam (callable App Check zorlamıyor)
- **Android mevcut:** App Check YOK; Firestore YOK
- **Android teknoloji:** FirebaseAppCheck, PlayIntegrityAppCheckProviderFactory, DebugAppCheckProviderFactory, firebase-appcheck-playintegrity/-debug
- **Adımlar:**
  - Application.onCreate'te: FirebaseApp.initializeApp(this); val appCheck = FirebaseAppCheck.getInstance(); release → appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance()); debug → DebugAppCheckProviderFactory.getInstance().
  - Debug token'ı Logcat'ten alıp Firebase Console > App Check > Android app > Manage debug tokens'a ekle (geliştirme cihazları için).
  - Play Console'da uygulamayı yayınla/iç test; Firebase Console'da Play Integrity sağlayıcısını etkinleştir; SHA-256 parmak izlerini Firebase'e ekle.
  - App Check'in Firestore okumalarından ÖNCE kurulduğundan emin ol (ilk wallet/unlocks okumasından önce Application seviyesinde).
  - Not: unlockExam callable App Check ZORLAMIYOR (enforceAppCheck:false ayarlı değil ama default v2 onCall da zorlamaz); yine de wallet/unlocks Firestore reads App Check monitoring→enforce kapsamında olduğundan provider şart.
- **Gradle/bağımlılık:** firebase-appcheck-playintegrity + firebase-appcheck-debug (T1)
- **Bağımlılık:** T1 (gradle), Phone Auth + App Check alanı (koordinasyon)
- **Tuzaklar:** Provider ilk Firestore okumasından önce kurulmazsa cüzdan hep 0 görünür (sessiz hata). Debug build Play Integrity ile çalışmaz → debug provider + konsola debug token şart. Bu alan Phone Auth+App Check alanıyla ORTAK; tek sefer kurulur.
- **Kabul kriteri:** Gerçek/Play-dağıtımlı cihazda wallet ve unlocks okumaları başarılı (balance>0 görünüyor); debug cihazda eklenen debug token ile okumalar çalışıyor; App Check kurulmadan okumaların reddedildiği (0 balance) doğrulanmış.


---

## Sınav Ekleme (kullanıcı gönderimleri)  ·  `addexam`

**Özet:** iOS'ta (v3.1.0) "Sınav Ekle" akışı, kullanıcının profilinden ön-dolu üniversite/fakülte/bölüm (salt-liste, serbest metin YOK), arşiv derslerinden ad-eşlemeli + serbest ders seçimi, sabit sınav-adı listesi, yıl ve tür (JPG/PDF) ile bir gönderim toplayıp; görselleri JPEG'e kodlayıp (<=15 MiB), önce Storage'a 'pending/<uid>/<submissionId>/<order>.<ext>' yolu altına yükleyip, sonra isimli 'vifi' Firestore veritabanına 'submissions/<id>' dokümanını TAM 10 anahtarla (status='pending', createdAt=serverTimestamp, exam='<yıl> <ad>', files=[{storagePath,order}]) yazan tam bir moderasyon-gönderim hattıdır. Ayrıca RTDB config/rewards üzerinden kademe-bazlı (tier) coin tahmini gösterir. Android'de bu akış HİÇ YOK; mevcut AddLessonActivity/AddPdfLessonActivity aslında eski ADMIN araçlarıdır (RTDB 'Universitiess' yayın ağacına doğrudan yazar, moderasyon/pending/submissions şeması yok, imagess//pdfs/ köküne downloadURL ile yükler, JPEG transcode yok, App Check/Firestore yok). Bu alan Android'de sıfırdan, iOS davranışına birebir uygun biçimde inşa edilmeli; eski admin yazma yolu parite için emekliye ayrılmalı. Önkoşul olarak AndroidX/Gradle/Firebase BoM modernizasyonu, isimli Firestore 'vifi' veritabanı, App Check (Play Integrity), paketli katalog varlıkları ve profil kaynağı gerekir (bunlar diğer alanlarla paylaşılan bağımlılıklardır).

**Android mevcut durum:** AddLessonActivity.java ve AddPdfLessonActivity.java mevcut ama bunlar kullanıcı gönderim akışı DEĞİL; admin yayınlama araçları: EditText'lerden okunan ham metinlerle RTDB 'Universitiess/<uni>/<fak>/<bol>/<ders>/<imageName>/<type>/<uuid>/downloadURL' yayın ağacına doğrudan setValue yapar, moderasyon/pending/submissions kavramı yoktur, görseli imagess/<uuid>.jpg (PDF: pdfs/<uuid>.pdf) KÖKÜNE putFile ile yükler ve public downloadURL saklar. Kaçırılan her şey: Firestore (projede firebase-firestore bağımlılığı bile yok), isimli 'vifi' veritabanı, App Check, paketli üniversite/fakülte/bölüm katalogları, profilden ön-dolum, ad-eşlemeli ders seçici, sabit sınav-adı listesi, yıl, görsel->JPEG downsample/transcode, 10-anahtarlı submissions şeması, pending/<uid>/<id>/ storage yolu, kademe-tahmini ve coin notu. Proje: com.android.support (AndroidX öncesi), AGP 3.4.2, jcenter (ölü), compileSdk/targe…

**Dokunulan backend sözleşmesi:** Ortak backend (vifi-831a8) — Android YENİ backend yazmamalı, sözleşmeye uymalı. Dokunulan/okunan: Firestore isimli 'vifi' veritabanı (eur3), submissions/<id> create (TAM 10 anahtar, status='pending', createdAt=serverTimestamp, exam='<yıl> <ad>', files 1..50, metin alanları 1..120) — kurallar platformdan bağımsız, sadece +90 phone auth bakar. Storage: pending/<uid>/<submissionId>/<order>.<ext> (image/jpeg|application/pdf, <=15 MiB, overwrite yok, sadece contentType metadata). RTDB: config/rewards (tiers + examUploadCoins) OKUMA; users/<uid>/profile OKUMA (ön-dolum). catalogNodes/exams OKUMA (ders seçici + tier). App Check ENFORCE (Storage/RTDB/Auth; Firestore monitoring->enforce). ANDROID-ÖZGÜ BACKEND BOŞLUKLARI (admin/konsol tarafı karar): config/rewards altında 'android' kardeşi mi yoksa 'ios' reuse mu; moderasyon (approve->wallet/ledger) admin uygulamasında, kapsam dışı. Bu alan submi…

**Android'e özgü riskler:**
- Play zorunluluğu: targetSdk en az 34 (2024+ Play Store). Photo Picker ve scoped storage'a geçiş şart; READ_EXTERNAL_STORAGE kaldırılmalı.
- Firestore isimli veritabanı API'si: FirebaseFirestore.getInstance(FirebaseApp, "vifi") yalnız firebase-firestore 24.10.0+ (BoM 33.x) sürümlerinde var; eski SDK isimli DB desteklemez. BoM modernizasyonu önkoşul.
- App Check Play Integrity: Play Console kaydı + SHA-256 + Firebase Console enforcement gerekir; debug/emulator'da DebugAppCheckProviderFactory + Console'a debug token eklenmezse Storage/RTDB/Firestore 403 döner (gönderim sessizce permissionDenied'a düşer).
- jcenter() ölü: android-pdf-viewer, commons-io, parse mavenCentral()'a taşınmalı; AndroidX öncesi kütüphaneler Jetifier gerektirir.
- Türkçe harf tuzağı: toLowerCase() default locale 'I'->'ı' hatası yapar; İ/ı/I eşlemesi için Locale("tr","TR") + açık İ/I/ı->i map (iOS Slug/ArchiveNameMatch ile birebir) gerekir, yoksa ders/katalog ad-eşleşmesi bozulur.
- Büyük kamera görselleri: BitmapFactory ile tam çözünürlük OOM riski; inSampleSize ile downsample + ExifInterface oryantasyon + arka iş parçacığı (coroutine Dispatchers.Default) zorunlu. 50 görselin ardışık transcode'u ANR yapmamalı.
- Storage kuralı özel metadata'yı reddeder: StorageMetadata'ya SADECE setContentType verilmeli; putFile(Uri) yerine putBytes(byte[]) tercih (JPEG zaten bellekte).
- Firestore submissions create kuralı createdAt'in timestamp olmasını ister: FieldValue.serverTimestamp() şart; client Date/long reddedilir.
- Parse SDK ve AdMob kaldırılınca push/monetizasyon kırılmasın: FCM native'e tam geçiş ve AdMob temizliği koordineli olmalı.
- R8/ProGuard release'de Firestore/Gson reflection'ı bozabilir; -keep kuralları veya kotlinx.serialization gerekir (mevcut minifyEnabled=false, release için açılmalı).
- Photo Picker maxItems sistemce sınırlı olabilir (MediaStore.getPickImagesMaxLimit()); 50 istensin ama cihaz sınırı altında kalınabilir.

### İş Kalemleri

#### `AE-00` — Önkoşul: AndroidX + Gradle + Firebase BoM + isimli Firestore 'vifi' + App Check altyapısı  _(P0 · efor: L (paylaşılan önkoşul))_
- **iOS davranışı:** Tüm gönderim hattı isimli 'vifi' Firestore (eur3/Native) + App Check ile korunan Storage/RTDB/Firestore üzerine kurulu.
- **iOS karşılığı/kaynak:** ViFiFirestore (databaseID='vifi', PersistentCacheSettings), ViFiAppCheckProviderFactory (App Attest).
- **Android mevcut:** com.android.support, AGP 3.4.2, jcenter (ölü), compile/target SDK 28, Firebase 2018 SDK'leri, firebase-firestore YOK, App Check YOK, isimli DB desteği yok.
- **Android teknoloji:** Firestore isimli DB: FirebaseFirestore.getInstance(FirebaseApp.getInstance(), "vifi").
- **Adımlar:**
  - gradle.properties: android.useAndroidX=true, android.enableJetifier=true ekle; tüm com.android.support.* referanslarını androidx.* karşılıklarına çevir.
  - Proje build.gradle: AGP 8.x + google-services 4.4.x; settings.gradle: pluginManagement + dependencyResolutionManagement(mavenCentral(), google()); jcenter()'ı tamamen kaldır.
  - app/build.gradle: compileSdk/targetSdk 34, minSdk 23 öneri; Firebase BoM 33.x (platform(...)) + firebase-firestore, firebase-storage, firebase-auth, firebase-database, firebase-analytics, firebase-appcheck-playintegrity, firebase-appcheck-debug; kotlinx-coroutines-play-services ekle.
  - ViFiFirestore singleton (Kotlin object/Hilt @Provides): FirebaseFirestore.getInstance(FirebaseApp.getInstance(), "vifi") + setFirestoreSettings(FirebaseFirestoreSettings.Builder().setLocalCacheSettings(PersistentCacheSettings.newBuilder().build()).build()).
  - Application.onCreate: FirebaseAppCheck.getInstance().installAppCheckProviderFactory(release: PlayIntegrityAppCheckProviderFactory.getInstance(); debug: DebugAppCheckProviderFactory.getInstance()).
  - Play Console'da uygulamayı kaydet, SHA-256 ekle; Firebase Console'da App Check enforcement'ı aç ve debug token'ı ekle; enforced ortamda test okuması yap.
- **Gradle/bağımlılık:** platform('com.google.firebase:firebase-bom:33.5.1') + firebase-firestore/storage/auth/database/analytics/appcheck-playintegrity/appcheck-debug; AGP 8.x, google-services 4.4.x; compileSdk/targetSdk 34+.
- **Tuzaklar:** İsimli DB için BoM 33.x şart. App Check enforced + debug token yoksa 403. Jetifier eski support kalıntılarını çevirir ama android-pdf-viewer exclude'ları gözden geçirilmeli.
- **Kabul kriteri:** Uygulama AndroidX + Firebase BoM 33.x ile derlenir; getInstance(app,"vifi") ile submissions koleksiyonuna erişilir; App Check Play Integrity (release) ve Debug (debug) kurulu, enforced ortamda test okuması 403 almaz.

#### `AE-01` — Paketli katalog varlıkları (üniversite/fakülte/bölüm JSON) + UniversityCatalog yükleyici + Türkçe-duyarsız filtre + Slug  _(P0 · efor: M)_
- **iOS davranışı:** Üç picker paketli JSON listelerden beslenir ({id,name}); Türkçe-duyarsız filtre; serbest metin için Slug ile id üretimi.
- **iOS karşılığı/kaynak:** UniversityCatalog.swift (bundled/filtered/normalized), CatalogItem {id,name}, Slug.swift.
- **Android mevcut:** Yok; katalog yok, EditText ile serbest metin.
- **Android teknoloji:** assets.open(...) + kotlinx.serialization; filtre Locale(tr,TR) + Normalizer NFD diacritic strip + ı->i; Slug Kotlin portu.
- **Adımlar:**
  - iOS'taki turkish-universities.json / turkish-faculties.json / turkish-departments.json dosyalarını app/src/main/assets/ altına birebir kopyala.
  - CatalogItem(val id:String, val name:String) data class + @Serializable; yükleyici context.assets.open(name).bufferedReader().use { Json.decodeFromString(...) }.
  - Türkçe-duyarsız normalize fonksiyonu: trim + lowercase(Locale("tr","TR")) + Normalizer.normalize(Form.NFD).replace(Regex("\\p{M}+"),"") + 'ı'->'i'; UniversityCatalog.filtered(items, query) contains araması.
  - Slug.make() iOS ile birebir port: İ/I/ı->i, Ğ/ğ->g, Ü/ü->u, Ş/ş->s, Ö/ö->o, Ç/ç->c; sonra [a-z0-9] tut, kalan runları tek '-'e indir, baş/son dash yok, boşsa 'unknown'.
  - Listeleri Collator.getInstance(Locale("tr","TR")) ile ada göre sırala; okuma hatasında boş liste + log.
- **Gradle/bağımlılık:** kotlinx-serialization-json (veya Moshi) + kotlin-serialization plugin.
- **Bağımlılık:** AE-00
- **Tuzaklar:** toLowerCase() default locale KULLANMA (I->ı). Submission id SAKLAMAZ; Slug yalnız segment/eşleşme için.
- **Kabul kriteri:** Üç katalog assets'ten {id,name} tr_TR sıralı yüklenir; 'isl'->İşletme, 'bogazici'->Boğaziçi bulunur; Slug.make("Boğaziçi Üniversitesi")=="bogazici-universitesi" iOS ile aynı.

#### `AE-02` — Profilden ön-dolum kaynağı (users/<uid>/profile RTDB okuma)  _(P0 · efor: S)_
- **iOS davranışı:** Form açılışında üniversite/fakülte/bölüm profilden (name,id,isCustom) ön-doldurulur.
- **iOS karşılığı/kaynak:** AddExamViewModel.init profile'dan .stored(...). UserProfile {university,universityId,universityIsCustom,...}.
- **Android mevcut:** Profil sistemi yok; üst alanlar boş EditText.
- **Android teknoloji:** FirebaseDatabase.getInstance().getReference("users").child(uid).child("profile") okuması.
- **Adımlar:**
  - uid = FirebaseAuth.getInstance().currentUser?.uid al.
  - RTDB users/<uid>/profile tek get() (veya ValueEventListener) ile oku; university/universityId/universityIsCustom, faculty..., department... alanlarını eşle.
  - Her alanı CatalogSelection(name,id,isCustom) olarak ViewModel başlangıç state'ine koy; profil yoksa CatalogSelection.empty ile başla.
- **Gradle/bağımlılık:** firebase-database (AE-00).
- **Bağımlılık:** AE-00
- **Tuzaklar:** Bu, Profil alanının çıktısıdır (bağımlılık). Profil yoksa ön-dolum atlanır, akış kırılmaz. Yalnız OKUMA; profile YAZMA bu alana ait değil. *Id alanları slug [a-z0-9-].
- **Kabul kriteri:** Profili olan kullanıcıda üç üst alan profildeki adlarla ön-dolu gelir; profili yoksa boş gelir ve akış çalışır.

#### `AE-03` — Giriş kapısı + 'Sınav Ekle' ekran iskeleti (Compose + ViewModel) + screenView analitiği  _(P0 · efor: M)_
- **iOS davranışı:** Home'dan sheet; NavigationStack + Form; başlık 'Sınav Ekle'; açılışta screenView('AddExam'); çalışırken kaçış devre dışı.
- **iOS karşılığı/kaynak:** AddExamView.swift (sheet), AddExamScreen (toolbar Vazgeç+Gönder).
- **Android mevcut:** HomeActivity var ama kullanıcı 'Sınav Ekle' akışı yok.
- **Android teknoloji:** Jetpack Compose + Navigation Compose; AddExamViewModel (StateFlow); TopAppBar Vazgeç/Gönder.
- **Adımlar:**
  - AddExamViewModel : androidx.lifecycle.ViewModel; UI state'i tek data class + MutableStateFlow ile tut.
  - AddExamScreen composable: Scaffold + TopAppBar('Sınav Ekle', navigationIcon=Vazgeç, actions=Gönder veya CircularProgressIndicator while isWorking) + LazyColumn ile beş bölüm (Okul/Ders/Sınav/Dosyalar/Coin + Hata).
  - Home'a 'Sınav Ekle' aksiyonu ekle; ModalBottomSheet veya tam-ekran destinasyona götür.
  - Ekran ilk göründüğünde FirebaseAnalytics screen_view (SCREEN_NAME="AddExam") logla.
  - isWorking iken BackHandler(enabled=true){} ile geri/kapatmayı engelle (iOS interactiveDismissDisabled).
- **Gradle/bağımlılık:** compose BOM, navigation-compose, lifecycle-viewmodel-compose, activity-compose; Hilt önerilir.
- **Bağımlılık:** AE-00
- **Tuzaklar:** Beş iOS Form bölümü LazyColumn + başlıklara çevrilmeli. screenView adı 'AddExam' birebir korunmalı.
- **Kabul kriteri:** Home'dan açılır, beş bölüm görünür, Vazgeç kapatır, Gönder başlangıçta pasif; açılışta AddExam ekran görüntüleme kaydı düşer; gönderim sırasında geri/kapat devre dışı.

#### `AE-04` — Okul bölümü: üniversite/fakülte/bölüm salt-liste seçicileri (serbest metin YOK) + profil ön-dolum  _(P0 · efor: M)_
- **iOS davranışı:** Üç satır CatalogPickerView'e (allowsCustom:false) push eder; boşsa 'Seç'; çalışırken disabled; profilden ön-dolu.
- **iOS karşılığı/kaynak:** catalogRow(allowsCustom:false) + CatalogPickerView (custom kapalı).
- **Android mevcut:** Yok; EditText serbest giriş (admin).
- **Android teknoloji:** CatalogPickerScreen composable (arama + liste); seçim savedStateHandle ile geri döner.
- **Adımlar:**
  - CatalogPickerScreen composable yaz: OutlinedTextField (arama) + LazyColumn (UniversityCatalog.filtered); parametre allowsCustom:Boolean (AddExam için false).
  - Her okul satırını (üniversite/fakülte/bölüm) bu ekrana götür (route + argüman); allowsCustom=false ile 'kullan' serbest satırı gösterme.
  - Seçili öğede Icons.Default.Check göster; seçim ViewModel'in ilgili StateFlow'unu günceller ve popBackStack(savedStateHandle) ile döner.
  - Satır değeri boşsa 'Seç', doluysa seçilen adı göster; isWorking iken satırları disabled yap; bölüm boşken ders satırını pasifleştir.
- **Gradle/bağımlılık:** AE-01 katalog.
- **Bağımlılık:** AE-01, AE-02, AE-03
- **Tuzaklar:** AddExam'de üç üst alan SALT-LİSTE (Profil ekranı serbest metne izin verir); aynı composable'ı allowsCustom ile paylaş. CatalogSelection.id submission'a YAZILMAZ.
- **Kabul kriteri:** Üç satır katalogdan salt-listeyle seçtirir (serbest metin yok), arama Türkçe-duyarsız, seçilen değer görünür, profil varsa ön-dolu, çalışırken pasif.

#### `AE-05` — Ders seçici: arşiv derslerini ad-eşlemeli listele + 'Farklı ders ekle' serbest metin + arama + durumlar  _(P0 · efor: L)_
- **iOS davranışı:** Bölüm seçilince LessonPickerView; seçilen üniversitenin eşleşen bölümü arşivde ad-eşlemeyle (tüm fakülteler taranarak) çözülür, dersleri listelenir; arama; 'Farklı ders ekle' (custom); loading/failed(retry)/loaded durumları.
- **iOS karşılığı/kaynak:** LessonPickerView.swift + LessonPickerViewModel (resolveDepartmentPath), ArchiveNameMatch.
- **Android mevcut:** Yok; ders admin EditText'inden serbest metin.
- **Android teknoloji:** Compose LessonPickerScreen + ViewModel; Firestore catalogNodes parentId sorguları; ArchiveNameMatch Kotlin portu.
- **Adımlar:**
  - ArchiveNameMatch Kotlin portu: matches (tr fold eşitlik), matchesDepartment (sondaki ' bölümü'/' bölüm' tam kelimesini atıp eşitlik), contains (fold substring).
  - LessonPickerViewModel.resolveDepartmentPath: catalogNodes'ta universities = whereEqualTo('parentId', rootParentId).whereEqualTo('status','published'); üniversiteyi ArchiveNameMatch.matches ile bul; altındaki fakülteleri (parentId=universityId) çek; her fakülte altında departmanları çek ve ilk matchesDepartment eşleşmesini al.
  - Eşleşen departmanın derslerini (parentId=departmentId, status=published) çek; Loadable<Loading/Failed/Loaded> state yay.
  - LessonPickerScreen: arama OutlinedTextField (contains fold), LazyColumn ile dersler + seçili checkmark; 'Farklı ders ekle' için AlertDialog + TextField -> CatalogSelection.custom(ad).
  - Durum metinleri iOS ile aynı: yükleniyor, 'Tekrar dene', 'Bu bölüm için arşivde kayıtlı ders yok...', 'Aramanla eşleşen ders yok.'
- **Gradle/bağımlılık:** firebase-firestore (AE-00).
- **Bağımlılık:** AE-00, AE-01, AE-04, AE-11
- **Tuzaklar:** BAĞIMLILIK: gerçek dersler için Firestore arşiv repository (catalogNodes migrasyonu - Browse alanı) hazır olmalı; yoksa ilk sürümde yalnız 'Farklı ders ekle' sunulup liste boş gösterilebilir. Bölüm TÜM fakülteler altında aranmalı (fakülte adı arşivle eşleşmeyebilir). toLowerCase default locale kullanma.
- **Kabul kriteri:** Bölüm seçili iken o bölümün arşiv dersleri ad-eşlemeyle görünür, arama süzer, listede yoksa serbest ders eklenir, yükleme/hata/boş durumları iOS metinleriyle gösterilir, hatada tekrar-dene çalışır.

#### `AE-06` — Sınav bölümü: sabit sınav-adı listesi (ExamNameCatalog) + yıl seçici + tür (JPG/PDF) + tür değişince dosya temizleme  _(P0 · efor: S)_
- **iOS davranışı:** Sınav adı sabit listeden (7 öğe), salt-liste; Yıl 2000..buYıl ters; Tür segmented (Görsel/PDF); tür değişince dosyalar temizlenir + hata sıfırlanır.
- **iOS karşılığı/kaynak:** ExamNameCatalog.swift, examSection, AddExamViewModel.kind.didSet.
- **Android mevcut:** Yok; tür/ad admin EditText'leri.
- **Android teknoloji:** ExamNameCatalog object + ExamKind enum (JPG/PDF); yıl dropdown; Material3 SegmentedButton.
- **Adımlar:**
  - ExamNameCatalog Kotlin object: 7 CatalogItem, id'ler birebir vize/final/butunleme/mazeret/kisa-sinav/yaz-okulu/ara-sinav, adlar VİZE/FİNAL/BÜTÜNLEME/MAZERET/KISA SINAV/YAZ OKULU/ARA SINAV.
  - ExamKind enum: IMAGES(raw="JPG"), PDF(raw="PDF") + ext (jpg/pdf) + contentType (image/jpeg, application/pdf).
  - Sınav adı satırı: CatalogPickerScreen(allowsCustom=false) ile ExamNameCatalog.names'ten seçtir.
  - Yıl: ExposedDropdownMenuBox (veya NumberPicker) 2000..Calendar.getInstance().get(Calendar.YEAR) ters sıralı.
  - Tür: Material3 SegmentedButton (Görsel/PDF); kind set edilince ViewModel'de files.clear() + errorMessage=null.
- **Gradle/bağımlılık:** compose-material3 1.2+ (SegmentedButton).
- **Bağımlılık:** AE-01, AE-03
- **Tuzaklar:** ExamKind.raw ('JPG'/'PDF') backend'e aynen yazılır. UI yılı 2000..buYıl olsa da model doğrulaması 1980..2100 kabul eder. Tür değişince önceki dosyalar temizlenmeli.
- **Kabul kriteri:** Sınav adı 7-öğelik sabit listeden seçilir, yıl 2000..buYıl ters, tür geçişinde dosyalar ve hata temizlenir, ExamKind değerleri 'JPG'/'PDF'.

#### `AE-07` — Dosyalar (görsel): Photo Picker ile çoklu görsel (maks 50) + 'hazırlanıyor' durumu + özet/temizle  _(P0 · efor: M)_
- **iOS davranışı:** Tür=Görsel iken PhotosPicker (max 50); seçilenler arka planda JPEG'e kodlanır; 'Dosyalar hazırlanıyor…'; özet 'N sayfa seçildi' + 'Temizle'.
- **iOS karşılığı/kaynak:** filesSection (PhotosPicker) + loadImages -> addImages (transcode+renumber+maxFileCount).
- **Android mevcut:** ACTION_PICK + READ_EXTERNAL_STORAGE ile TEK görsel; transcode yok.
- **Android teknoloji:** PickMultipleVisualMedia(50) + ImageOnly; ContentResolver.openInputStream -> AE-09 encoder (coroutine).
- **Adımlar:**
  - rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems=50)); PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly) ile başlat.
  - Dönen URI listesini isPreparingFiles=true yapıp viewModelScope(Dispatchers.Default) içinde her birini ContentResolver.openInputStream ile oku ve AE-09 encoder'a ver.
  - Ekleme sırasında files.count+added < 50 kontrol et; aşılırsa 'En fazla 50 sayfa ekleyebilirsin.' hatası + döngüyü kır; kodlanamayan görseli atla + 'Bir görsel işlenemedi...' hatası.
  - Ekleme bitince 1-tabanlı order yeniden numaralandır (contiguous).
  - Özet 'N sayfa seçildi' + 'Temizle' (files.clear) göster; footer 'Sınavın sayfalarını sırayla ekle. En fazla 50 görsel.'
  - AndroidManifest'ten READ_EXTERNAL_STORAGE iznini kaldır (Photo Picker izin istemez).
- **Gradle/bağımlılık:** androidx.activity:activity-compose 1.8+ (Photo Picker).
- **Bağımlılık:** AE-00, AE-06, AE-09
- **Tuzaklar:** Photo Picker izinsiz çalışır; READ_EXTERNAL_STORAGE kaldırılmalı (parite + Play Data Safety). maxItems sistemce sınırlı olabilir (getPickImagesMaxLimit). 50 görsel transcode ANR yapmamalı.
- **Kabul kriteri:** Görsel türünde çoklu görsel (maks 50) seçilir, sıra korunur ve 1..N numaralanır, transcode sırasında 'hazırlanıyor' görünür, özet 'N sayfa seçildi' + Temizle çalışır, 50 üstü uyarır, izin istenmez.

#### `AE-08` — Dosyalar (PDF): tek PDF seçimi (OpenDocument) + boş/boyut doğrulaması + değiştir  _(P0 · efor: S)_
- **iOS davranışı:** Tür=PDF iken fileImporter(.pdf); boş veya >15 MiB reddedilir; tek dosya (order=1); 'PDF seç'/'PDF'i değiştir' + 'PDF seçildi'.
- **iOS karşılığı/kaynak:** importPDF + viewModel.setPDF.
- **Android mevcut:** ACTION_GET_CONTENT application/pdf; putFile ile köke yükler; boyut/boşluk kontrolü yok.
- **Android teknoloji:** OpenDocument() + application/pdf; openInputStream -> byte[]; boyut/boşluk kontrol.
- **Adımlar:**
  - rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) ile arrayOf("application/pdf") seçtir.
  - Önce ContentResolver query (OpenableColumns.SIZE) ile boyutu öğren; >15*1024*1024 ise okumadan 'PDF 15 MB'tan büyük olamaz.' hatası ver.
  - openInputStream ile byte[]'a oku; boşsa 'Dosya boş görünüyor...'; okuma başarısızsa 'Dosya okunamadı. Lütfen tekrar dene.'
  - Geçerliyse files = listOf(SubmissionFile(data=bytes, order=1)); buton etiketi boşken 'PDF seç', doluyken 'PDF'i değiştir'; özet 'PDF seçildi'.
- **Gradle/bağımlılık:** — (platform API).
- **Bağımlılık:** AE-00, AE-06, AE-10
- **Tuzaklar:** 15 MiB belleğe almak büyük PDF'te sorun olabilir; önce SIZE ile reddet. contentType application/pdf. Dosya adı <order>.pdf benzersiz (overwrite yasak).
- **Kabul kriteri:** PDF türünde tek PDF seçilir, boş/15 MiB üstü doğru Türkçe mesajla reddedilir, 'PDF seçildi' + değiştir çalışır, okunamayan dosyada 'Dosya okunamadı'.

#### `AE-09` — Görsel->JPEG kodlayıcı (longest-side 3000px downsample + kalite adımları + EXIF) arka iş parçacığında  _(P0 · efor: M)_
- **iOS davranışı:** Keyfi görsel (HEIC/PNG/büyük JPEG) en uzun kenar 3000px'e düşürülür, kalite [0.8,0.6,0.45,0.3] sırayla denenip <=15 MiB olan ilk JPEG seçilir, EXIF oryantasyonu uygulanır, ana iş parçacığı dışında; başarısızsa nil.
- **iOS karşılığı/kaynak:** SubmissionFileEncoder.swift (maxPixelSize=3000, qualitySteps).
- **Android mevcut:** Yok; görsel ham Uri olarak yükleniyor.
- **Android teknoloji:** BitmapFactory inSampleSize + ExifInterface + Bitmap.compress(JPEG, q).
- **Adımlar:**
  - suspend fun jpeg(bytes/inputStream, maxBytes): ByteArray? — withContext(Dispatchers.Default) içinde çalıştır.
  - Önce BitmapFactory.Options(inJustDecodeBounds=true) ile boyutu öğren; en uzun kenar <=3000 olacak inSampleSize hesapla; sonra decode et (gerekirse Bitmap.createScaledBitmap ile tam 3000'e indir).
  - ExifInterface(inputStream).getAttributeInt(TAG_ORIENTATION) oku; Matrix ile rotate/flip uygulayarak oryantasyonu düzelt.
  - Kalite [80,60,45,30] sırayla Bitmap.compress(JPEG, q, ByteArrayOutputStream); ilk <=maxBytes olanı döndür; hiçbiri sığmazsa null.
  - Bitmap'leri recycle et; decode edilemeyen girdide null dön (görsel atlanır).
- **Gradle/bağımlılık:** androidx.exifinterface:exifinterface.
- **Bağımlılık:** AE-00, AE-07
- **Tuzaklar:** Tam çözünürlük decode etmeden inJustDecodeBounds ile boyutu öğren (OOM). HEIC decode API<28'de kısıtlı; ImageDecoder (API28+) fallback. EXIF oryantasyonu bazı cihazlarda şart. maxPixelSize=3000 ve kalite adımları iOS ile birebir.
- **Kabul kriteri:** HEIC/PNG/10MB JPEG girdileri <=15 MiB JPEG'e döner, en uzun kenar <=3000px, EXIF oryantasyonu doğru, işlem ana iş parçacığını bloke etmez, kodlanamayan görsel null döner.

#### `AE-10` — ExamSubmission modeli + doğrulama (sunucu sözleşmesini birebir yansıt) + examText (yıl katlama)  _(P0 · efor: M)_
- **iOS davranışı:** 5 metin alanı (university/faculty/department/lesson + examText='<yıl> <ad>') 1..120, yıl 1980..2100, dosya 1..50, PDF tam 1, her dosya boş değil ve <=15 MiB; validName trim+boşluk-sıkıştırma.
- **iOS karşılığı/kaynak:** ExamSubmission.swift (validationError/isValid, examText), AddExamViewModel.validName/builtSubmission.
- **Android mevcut:** Yok; doğrulama yok, ham EditText RTDB'ye yazılıyor.
- **Android teknoloji:** Kotlin data class + sealed validation error.
- **Adımlar:**
  - SubmissionFile(val data: ByteArray, val order: Int); ExamSubmission(university,faculty,department,lesson,examName,year,kind,files).
  - Sabitler: MAX_TEXT_LENGTH=120, MAX_FILE_COUNT=50, MAX_FILE_BYTES=15*1024*1024, YEAR_RANGE=1980..2100.
  - examText(name,year) = "$year $name"; validName(input) = trim + Regex(" +").replace(" "), boş değil ve <=120 ise döndür.
  - validationError(): 5 alan (examText dahil) 1..120 + examName boş değil -> invalidText; yıl !in 1980..2100 -> invalidYear; files boş -> noFiles; >50 -> tooManyFiles; kind=PDF && files.size!=1 -> pdfMustBeSingleFile; her dosya boş -> emptyFile; >15 MiB -> fileTooLarge.
  - builtSubmission(): ownerId boş değil + 5 validName geçer + files boş değil olduğunda ExamSubmission üret ve isValid ise döndür.
- **Gradle/bağımlılık:** —
- **Bağımlılık:** AE-06, AE-07, AE-08, AE-09
- **Tuzaklar:** examText 120 sınırına TABİ (uzun ad + yıl taşabilir). ByteArray equals referans karşılaştırır; gerekirse contentEquals. examName boş olamaz.
- **Kabul kriteri:** Birim testlerde 120+ examText, yıl 1980 altı/2100 üstü, 51 görsel, PDF'te 2 dosya, boş/15MiB+ dosya reddedilir; examText("VİZE",2023)=="2023 VİZE"; geçerli gönderimde isValid=true.

#### `AE-11` — Gönderim servisi: Firestore submissions/<id> (10 anahtar) + Storage pending yükleme + temizleme + hata eşleme  _(P0 · efor: L)_
- **iOS davranışı:** auth.uid==ownerId doğrular; doküman id'si üretir; dosyaları SIRAYLA pending/<uid>/<id>/<order>.<ext> yoluna SADECE contentType ile yükler; hepsi bitince 10-anahtarlı dokümanı (createdAt=serverTimestamp) yazar; patlarsa yüklenenleri best-effort siler; hataları eşler.
- **iOS karşılığı/kaynak:** FirebaseExamSubmissionService.swift + ExamSubmissionPayload.swift.
- **Android mevcut:** AddLesson/AddPdf RTDB 'Universitiess' yayın ağacına + imagess//pdfs/ köküne yazar (moderasyon yok). Yanlış hedef.
- **Android teknoloji:** FirebaseFirestore(app,"vifi") + FirebaseStorage putBytes(bytes, StorageMetadata contentType); kotlinx-coroutines await.
- **Adımlar:**
  - suspend fun submit(submission, ownerId): uid=FirebaseAuth.currentUser?.uid; uid!=ownerId -> NotSignedIn; !submission.isValid -> InvalidSubmission.
  - doc = db.collection("submissions").document(); id = doc.id; payload: dosyaları order'a göre sırala, fileName="$order.${kind.ext}", storagePath="pending/$ownerId/$id/$fileName", contentType=kind.contentType.
  - Her dosya için: StorageMetadata meta = StorageMetadata.Builder().setContentType(ct).build(); FirebaseStorage.getInstance().reference.child(storagePath).putBytes(bytes, meta).await(); yüklenen path'leri biriktir.
  - Tüm yüklemeler bitince map yaz: ownerId, university, faculty, department, lesson, exam(=examText), kind(JPG/PDF), status="pending", files=listOf(mapOf("storagePath" to p, "order" to o)), createdAt=FieldValue.serverTimestamp(); doc.set(map).await().
  - catch: biriken path'leri storage.reference.child(path).delete() ile best-effort sil; hatayı eşle: FirebaseFirestoreException PERMISSION_DENIED->permissionDenied, UNAVAILABLE/DEADLINE_EXCEEDED->offline; StorageException ERROR_NOT_AUTHORIZED/UNAUTHENTICATED->permissionDenied, ERROR_RETRY_LIMIT_EXCEEDED->offline; IOException/ağ->offline; diğer->unknown.
  - Hata türlerini Türkçe kullanıcı mesajlarına eşle (iOS ExamSubmissionError metinleri).
- **Gradle/bağımlılık:** firebase-firestore/storage/auth + kotlinx-coroutines-play-services.
- **Bağımlılık:** AE-00, AE-10
- **Tuzaklar:** createdAt MUTLAKA FieldValue.serverTimestamp() (kural timestamp ister). StorageMetadata'ya SADECE contentType. Dosya adı benzersiz (<order>.<ext>, overwrite yasak). Dokümanı YÜKLEMELERDEN SONRA yaz. App Check token yoksa permissionDenied'a düşer.
- **Kabul kriteri:** Geçerli gönderimde pending/<uid>/<id>/1.jpg.. yüklenir, submissions/<id> tam 10 anahtarla (status=pending, sunucu zamanı, exam='<yıl> <ad>', files sıralı) yazılır; başka uid ownerId reddedilir; ortadaki hata yüklenenleri temizler; offline/izin hataları doğru Türkçe mesajlara eşlenir.

#### `AE-12` — Kademe (tier) çözümleyici: arşiv yerleşimine göre novelty tier tahmini (bağlayıcı değil, cancellable)  _(P1 · efor: L)_
- **iOS davranışı:** Yerleşim tamamsa arşive ad-eşlemeyle çözülür: newUniversity..duplicate; üniversite kök, bölüm tüm fakülteler altında, ders ve exam ('<yıl> <ad>') eşleşmesi; her alan değişiminde yeniden hesap, öncekini iptal; tier null iken coin notu generic.
- **iOS karşılığı/kaynak:** SubmissionTierResolver.swift, SubmissionTier.swift, SubmissionLocation.
- **Android mevcut:** Yok.
- **Android teknoloji:** Firestore catalogNodes/exams sorguları + ArchiveNameMatch; viewModelScope Job iptal.
- **Adımlar:**
  - SubmissionTier enum (rawValue'lar iOS birebir: newUniversity, newFaculty, newDepartment, newCourse, newExam, duplicate) + label (Türkçe) + classify(...).
  - SubmissionLocation(university,faculty,department,lesson,examText) data class + examText=examText(name,year).
  - SubmissionTierResolver(archiveRepo): placement(location) algoritmasını iOS birebir port et — üniversite kök eşleşmesi, bölüm tüm fakülteler altında (matchesDepartment), ders (matches), exam (matches examText); eksik ilk seviye tier'i belirler.
  - ViewModel'de estimateKey (üniversite/fakülte/bölüm/ders/sınav adı/yıl) değişince eski Job iptal + yeni viewModelScope.launch ile tier hesapla; CancellationException yut; yerleşim eksikse tier=null.
  - Hesap bağlayıcı değil (gerçek tier onayda); hata/okunamazsa tier=null.
- **Gradle/bağımlılık:** firebase-firestore (AE-00).
- **Bağımlılık:** AE-00, AE-05, AE-10
- **Tuzaklar:** BAĞIMLILIK: Firestore arşiv repository (Browse/migrasyon) hazır olmalı; yoksa tier hesaplanamaz ve coin notu generic'e düşer. Bölüm TÜM fakülteler altında aranmalı. Ad eşleme tr fold + ' bölümü' eki iOS ile aynı.
- **Kabul kriteri:** Yerleşim tamamlandığında tier arşive göre doğru hesaplanır, alan değişiminde güncellenir, arşiv okunamazsa/tier yoksa coin notu generic'e düşer, hesap gönderimi bloke etmez.

#### `AE-13` — Ödül yapılandırması okuyucu (RTDB config/rewards) + android/ios düğüm kararı  _(P1 · efor: S)_
- **iOS davranışı:** RTDB config/rewards/ios canlı dinlenir (keepSynced); examUploadCoins (pozitif int) + tiers map (SubmissionTier.rawValue, pozitif int); eksik/bozuksa null.
- **iOS karşılığı/kaynak:** FirebaseRewardsConfigReader.swift, RewardsConfig {examUploadCoins,tiers,coins(for:)}.
- **Android mevcut:** Yok.
- **Android teknoloji:** FirebaseDatabase config/rewards ValueEventListener -> callbackFlow<RewardsConfig?>.
- **Adımlar:**
  - RewardsConfig(val examUploadCoins:Int?, val tiers:Map<SubmissionTier,Int>) + coins(tier)=tiers[tier] ?: examUploadCoins.
  - FirebaseDatabase.getInstance().getReference("config/rewards/android").apply{ keepSynced(true) } üzerine ValueEventListener; callbackFlow ile StateFlow'a dönüştür (awaitClose'da removeEventListener).
  - parse: examUploadCoins pozitif int (Long/Double güvenli çevir); tiers child'ını SubmissionTier.rawValue->pozitif int map'e çevir, bilinmeyen anahtar/negatifi at.
  - Düğüm yoksa (android) fallback: config/rewards/ios oku (karara göre); hiçbiri yoksa null yay.
- **Gradle/bağımlılık:** firebase-database (AE-00).
- **Bağımlılık:** AE-00, AE-12
- **Tuzaklar:** KARAR: android düğümünü kim dolduracak? Öneri fallback android->ios. RTDB sayıları Long/Double gelebilir. App Check RTDB'de enforced — token yoksa listener cancel (null yay).
- **Kabul kriteri:** config/rewards okunur ve değişiklikte canlı güncellenir, tiers ve examUploadCoins doğru ayrıştırılır, eksik/bozukta null döner, android düğümü yoksa ios fallback (karara göre) çalışır.

#### `AE-14` — Coin notu arayüzü: tahmini tier + coin miktarı gösterimi (yaklaşık)  _(P1 · efor: S)_
- **iOS davranışı:** tier+config varsa 'Onaylanırsa yaklaşık N coin kazanırsın (<tier>); coinlerin hesabına yüklenir.'; yalnız flat varsa flat metin; hiçbiri yoksa generic. Para ikonu.
- **iOS karşılığı/kaynak:** coinNoteSection / coinNoteText.
- **Android mevcut:** Yok.
- **Android teknoloji:** Compose Row (Icon + Text); metin AE-12 tier + AE-13 coins(tier)'den.
- **Adımlar:**
  - SubmissionTier.label Türkçe metinleri iOS ile birebir (Yeni üniversite/Yeni fakülte/Yeni bölüm/Yeni ders/Yeni sınav/Mevcut sınav).
  - Compose coin notu satırı: para ikonu (turuncu) + Text; üç durumlu metin: estimatedTier!=null && coins!=null -> 'Onaylanırsa yaklaşık $coins coin kazanırsın (${tier.label}); coinlerin hesabına yüklenir.'
  - examUploadCoins!=null -> 'Onaylanırsa $coins coin kazanırsın; coinlerin hesabına yüklenir.'; aksi halde generic 'Eklediğin sınav incelenip onaylandığında coin kazanırsın; coinlerin hesabına yüklenir.'
  - tier/config değişince metni recompose et.
- **Gradle/bağımlılık:** —
- **Bağımlılık:** AE-12, AE-13
- **Tuzaklar:** 'yaklaşık' vurgusu korunmalı (gerçek miktar onayda). label metinleri iOS ile aynı.
- **Kabul kriteri:** Yerleşim+config tamsa 'yaklaşık N coin (<tier>)', yalnız flat varsa flat metin, hiçbiri yoksa generic; tier değişince miktar/etiket güncellenir.

#### `AE-15` — Analitik olayları: exam_submitted / exam_submit_failed (Firebase Analytics)  _(P1 · efor: S)_
- **iOS davranışı:** Başarılı gönderimde examSubmitted(kind,fileCount,tier); hatada examSubmitFailed(reason=düşük-kardinalite, lokalize DEĞİL); açılışta screenView('AddExam').
- **iOS karşılığı/kaynak:** AddExamViewModel.submit/fail analytics.track.
- **Android mevcut:** Yok (bu akış için).
- **Android teknoloji:** FirebaseAnalytics.logEvent snake_case + bundleOf.
- **Adımlar:**
  - Başarıda FirebaseAnalytics.logEvent("exam_submitted", bundleOf("kind" to kind.raw, "file_count" to n, "tier" to (tier?.raw ?: "unknown"))).
  - Hatada FirebaseAnalytics.logEvent("exam_submit_failed", bundleOf("reason" to reason)); reason düşük-kardinalite: not_signed_in/invalid_submission/permission_denied/offline/unknown (iOS analyticsReason).
  - Ekran açılışında screen_view (FirebaseAnalytics.Event.SCREEN_VIEW, Param.SCREEN_NAME="AddExam").
- **Gradle/bağımlılık:** firebase-analytics (AE-00).
- **Bağımlılık:** AE-00, AE-11
- **Tuzaklar:** reason ASLA lokalize/kullanıcı mesajı olmamalı (kardinalite). Olay/parametre adları snake_case.
- **Kabul kriteri:** Başarıda exam_submitted (kind/file_count/tier), hatada exam_submit_failed sabit reason, açılışta AddExam screen_view düşer.

#### `AE-16` — Parite: eski admin AddLesson/AddPdfLesson yazma yolunu emekliye ayır + AdMob'u bu akıştan kaldır  _(P2 · efor: M)_
- **iOS davranışı:** iOS'ta kullanıcı yayınlamaz — yalnız 'pending' submission; iOS'ta HİÇ reklam yok, monetizasyon coin.
- **iOS karşılığı/kaynak:** Yayın yalnız Admin SDK/Moderation; Add akışı sadece submissions create.
- **Android mevcut:** AddLessonActivity/AddPdfLessonActivity RTDB 'Universitiess' ağacına doğrudan yazar; manifestte AdMob APPLICATION_ID + play-services-ads; Parse SDK.
- **Android teknoloji:** Manifest activity/meta-data + gradle bağımlılık temizliği.
- **Adımlar:**
  - AddLessonActivity/AddPdfLessonActivity'nin kullanıcıya açık doğrudan-yayın yolunu kaldır (manifest activity kaydını sil veya admin-only'a taşı) ve yeni 'Sınav Ekle' akışına yönlendir.
  - AndroidManifest'ten com.google.android.gms.ads APPLICATION_ID meta-data'sını kaldır; banner/interstitial/rewarded kodlarını temizle.
  - build.gradle'den play-services-ads bağımlılığını kaldır (parite: iOS'ta reklam yok).
  - Parse SDK (parse-android/parse-fcm) bağımlılıklarını kaldır ve FCM native'e tam geçiş yapıldığını doğrula.
- **Gradle/bağımlılık:** build.gradle: play-services-ads ve com.parse:* satırlarını SİL.
- **Bağımlılık:** AE-11
- **Tuzaklar:** AdMob/Parse kaldırma push/monetizasyonu etkiler; koordineli yapılmalı. 'Universitiess' RTDB ağacı rollback için duruyor (cutover sonrası silinecek) — okuyan yerler Firestore'a taşınana kadar silme. Eski admin aracı tamamen silinmeyecekse yetkiyle gizlenmeli.
- **Kabul kriteri:** Kullanıcı artık doğrudan 'Universitiess' yayın ağacına yazamaz; yeni akış yalnız pending submission üretir; 'Sınav Ekle' akışında hiç reklam gösterilmez ve AdMob meta-data/bağımlılığı bu yoldan kaldırılmıştır.


---

## Moderasyon (admin onay/yayın + coin kredisi + arşiv silme)  ·  `moderation`

**Özet:** iOS master'da Moderasyon alanı tamamen "serverless + admin-cihazı" mimarisidir: admin:true claim'li kullanıcı Hesap > Moderasyon panelinde bekleyen submissions/<id> gönderimlerini listeler, dosyalarını (pending/<uid>/...) önizler, Reddet veya "Onayla ve Yayınla" der. Onay tek bir atomik Firestore WriteBatch ile çalışır: SubmissionTierResolver gönderimi canlı arşive (catalogNodes) AD eşleştirerek çözer ve yenilik kademesini (6 kademe) + mevcut node id'lerini bulur; ArchivePublishPlan eksik catalogNodes'ları (slug tabanlı id şeması) ve exams/<id> dokümanını üretir; dosyalar pending'den imagess/pdfs'e kopyalanır; wallets/<uid> balance FieldValue.increment ile artırılır ve ledger/<submissionId> earn kaydı yazılır (entryId=submissionId => çift kredi imkânsız); submission status approved'a döner. idempotency iki kapıyla korunur: commit öncesi status==pending re-read + (coins>0 ise) ledger kaydı yoklama. Android şu an bu alanın HİÇBİRİNE sahip değil: Firestore yok, submission/coin/moderasyon kavramı yok, admin claim kontrolü yok, eski AddLessonActivity doğrudan RTDB Universitiess (silinecek legacy ağaç) ağacına admin kısıtı OLMADAN yazıyor. Ortak backend (Firestore kuralları, wallets/ledger/submissions/catalogNodes/exams şemaları, config/rewards) zaten yayında ve platform-bağımsız; Android yeni backend yazmamalı, mevcut sözleşmeye uymalı. Monetizasyon coin ekonomisidir; AdMob paritesi için kaldırılmalı, eski doğrudan-yazan admin akışı submission-moderasyon ile değiştirilmeli.

**Android mevcut durum:** (yukarıda)

**Dokunulan backend sözleşmesi:** Yeni backend YAZILMAYACAK — moderasyon tamamen serverless, admin cihazından Firestore/Storage'a yazar. Okunan/yazılan mevcut sözleşme: Firestore(named 'vifi') submissions/<id> (read admin; update admin => status=approved|rejected, tier, coins, reviewedAt, publishedExamId, rejectionReason), catalogNodes/<id> (admin set/increment childCount; alanlar level,name,parentId,status:published,childCount,slug,source:submission), exams/<id> (admin set; ArchivePublishPlan.examDocument şekli), wallets/<uid> (admin create/update, yalnız balance:int>=0 + updatedAt), wallets/<uid>/ledger/<submissionId> (admin create immutable; type:earn,amount,submissionId,tier,createdAt). Storage: pending/<uid>/<submissionId>/<dosya> admin read; imagess/<uuid>.jpg & pdfs/<uuid>.pdf admin write (App Check enforce). RTDB: config/rewards/ios (tiers + examUploadCoins) okunur. Cloud Function KULLANILMAZ (approve admin ciha…

**Android'e özgü riskler:**
- Named Firestore veritabanı 'vifi' (bölge eur3) zorunlu — (default) KULLANILMAMALI. FirebaseFirestore.getInstance(app,"vifi") overload'u yalnız yeni SDK'da var (firebase-firestore >= 24.10, ~firebase-bom 32.6.0+). Eski BoM ile derlenmez/yanlış DB'ye yazar.
- App Check ENFORCE: Storage + RTDB + Auth zorlamada. Admin cihazın putBytes/getBytes (pending->imagess/pdfs) ve RTDB okuması Play Integrity App Check token'ı olmadan reddedilir. Debug build için debug provider + Console'a debug token eklenmezse geliştirme kırılır. (App Check kurulumu başka alanın bağımlılığı.)
- Bellek/OOM: copyFiles bir submission'da 50 adede kadar 15 MiB dosyayı tek tek indirip (getBytes byte[] olarak RAM'e) yeniden yükler. Ardışık yapılmalı (paralel değil), büyük görsellerde OutOfMemoryError riski; largeHeap veya stream tabanlı alternatif düşünülmeli.
- Firestore Tasks ana thread'de await edilemez; kotlinx-coroutines-play-services (Tasks.await) + Dispatchers.IO ya da WorkManager/arka plan şart. WriteBatch.commit() ve Storage işlemleri uzun sürer.
- Jetpack Compose + Navigation için AndroidX + AGP 7+/8+ + compileSdk 34+ + minSdk 21 taşınması ÖN KOŞUL. Pre-AndroidX (com.android.support) ve jcenter(ölü) ile Compose derlenmez (ayrı/temel alan bağımlılığı).
- Firestore güvenlik kuralları zaten platform-bağımsız ve yayında (submissions 10 anahtar + 1..120 metin + 1..50 dosya; wallets balance int>=0; ledger immutable type-whitelist amount!=0; catalogNodes/exams admin-write). Android yazdığı dokümanları BİREBİR bu şemaya uydurmalı; fazladan alan eklerse kural reddeder (ör. wallet yalnız balance+updatedAt; ledger hasOnly whitelist).
- Scoped storage (targetSdk 29+): dosya önizleme/paylaşım değil ama gönderim oluşturma (AddExam alanı) MediaStore/SAF gerektirir; moderasyon yalnız Storage'tan okuyup yazdığı için yerel depolama izni gerektirmez — yine de pending önizleme için RemoteFileLoader eşdeğeri Storage getBytes kullanmalı, yerel dosya izni DEĞİL.
- R8/ProGuard: Firestore model sınıfları (toObject/data-class) reflection kullanır; -keep kuralları ve @Keep gerekmezse release build'de alanlar kaybolur. Moderasyon modelleri için proguard-rules.pro eklenmeli.
- config/rewards/ios yol adı 'ios' içeriyor ama değerler platform-bağımsız coin ekonomisidir; Android'in bu yolu mu okuyacağı yoksa config/rewards/android mı kullanılacağı KARAR gerektirir (aşağıda açık soru). Yanlış yol => coins hep 0 ve sessizce yayınlanır (hata vermez).

### İş Kalemleri

#### `MOD-01` — Moderasyon saf (Firestore-bağımsız) modelleri ve yardımcılarını port et (unit-test edilebilir çekirdek)  _(P0 · efor: M)_
- **iOS davranışı:** iOS'ta SubmissionTier (6 case: newUniversity/newFaculty/newDepartment/newCourse/newExam/duplicate, rawValue camelCase), ExamSubmissionRecord + SubmissionFileRef + SubmissionRecordParser (zorunlu alan yoksa null, files order'a göre sıralı), SubmissionLocation, ResolvedPlacement, ApprovalResult, CatalogNodeWrite + ArchivePublishPlan.make/examDocument, Slug.make (Türkçe harf->ASCII, [a-z0-9-], boşsa 'unknown'), ArchiveNameMatch (tr_TR lowercased + ı->i + diacriticInsensitive fold; matchesDepartment ' bölümü'/' bölüm' sonekini atar), RewardsConfig (coins(tier)=tiers[tier] ?? examUploadCoins) hepsi saf değer tipleri.
- **iOS karşılığı/kaynak:** Swift: SubmissionTier.swift, ExamSubmissionRecord.swift (SubmissionRecordParser), SubmissionTierResolver.swift (SubmissionLocation/ResolvedPlacement), ArchivePublishPlan.swift (CatalogNodeWrite), Slug.swift, LessonPickerView.swift (ArchiveNameMatch), RewardsConfigReading.swift (RewardsConfig).
- **Android mevcut:** Android'de bu modellerin ve yardımcıların hiçbiri yok; Slug/ArchiveNameMatch/tier kavramı mevcut değil.
- **Android teknoloji:** Kotlin data class/enum + saf object'ler (Firebase bağımlılığı YOK). SubmissionTier: enum class SubmissionTier(val raw:String){ NEW_UNIVERSITY("newUniversity"),... } — raw string'ler tiers anahtarları ve ledger 'tier' alanıyla BİREBİR aynı olmalı. Slug: Türkçe harf haritası (İ/I/ı->i, Ğ/ğ->g, Ü/ü->u, Ş/ş->s, Ö/ö->o, Ç/ç->c) + Locale('tr','TR') lowercase + regex ile [a-z0-9]+ dışını '-' ile birleştir, baş/son '-' yok, boşsa 'unknown'. ArchiveNameMatch: key(s)=s.lowercase(Locale('tr')).replace('ı','i') + Normalizer.normalize(NFD) ile diakritik temizle; departmentKey ' bolumu'/' bolum' sonekini (tam kelime) at. ArchivePublishPlan ve SubmissionRecordParser saf Kotlin; Firestore Timestamp yerine createdAt:Long?/Date? parametre olarak alınır.
- **Adımlar:**
  - Kotlin'e geç (veya Java) ve SubmissionTier enum'ını camelCase raw string'lerle oluştur; SubmissionTier.classify(5 bool) ve Türkçe label ekle.
  - ExamSubmissionRecord + SubmissionFileRef data class + SubmissionRecordParser.parse(id, Map<String,Any?>, createdAt) — zorunlu alanlar yoksa null, files order'a göre stabil sıralama (order yoksa index).
  - SubmissionLocation + ResolvedPlacement + ApprovalResult data class'ları.
  - CatalogNodeWrite (fields Map: level,name,parentId,status=published,childCount,slug=Slug.make(name),source=submission) + ArchivePublishPlan.make(record,placement) ve examDocument(record,files) saf fonksiyonlarını port et (id şeması: universityId=slug; alt=parentId__slug).
  - Slug object: Türkçe harf haritası + Locale('tr','TR') lowercase + ASCII [a-z0-9] koru, diğerlerini tek '-' ile birleştir, boşsa 'unknown'.
  - ArchiveNameMatch object: key/departmentKey/matches/matchesDepartment/contains; Normalizer NFD + diakritik regex, 'ı'->'i'.
  - RewardsConfig data class + coins(tier) fallback mantığı.
  - Her biri için JUnit birim testleri yaz (iOS'taki kenar durumları kapsa).
- **Gradle/bağımlılık:** Ek bağımlılık gerekmez (saf Kotlin). test: junit + truth/kotlin-test ile birim test.
- **Bağımlılık:** (bağımsız — diğer tüm MOD kalemlerinin temeli)
- **Tuzaklar:** Slug ve ArchiveNameMatch bundled katalog id'leri ve FCM topic'leriyle BİREBİR aynı sonucu üretmeli (farklı normalize => yanlış node id => çift node). Normalizer.Form.NFD + \p{Mn} regex ile diakritik temizliği iOS folding'iyle eşleşmeli; 'ı' özel olarak önce 'i'ye map edilmeli (NFD 'ı'yı ayrıştırmaz). SubmissionTier.raw değerleri değiştirilemez (backend sözleşmesi).
- **Kabul kriteri:** Birim testler: Slug.make('İSTANBUL TEKNİK ÜNİVERSİTESİ') beklenen slug; ArchiveNameMatch.matches ve matchesDepartment ('MATEMATİK BÖLÜMÜ' ~ 'Matematik' eşleşir, 'MATEMATİK MÜHENDİSLİĞİ' eşleşmez); ArchivePublishPlan.make duplicate'te examId=null & node/increment boş; yeni-üniversite senaryosunda 4 node + examId üretir; SubmissionRecordParser eksik alanlı dokümanı null döner, files order'a göre sıralar. RewardsConfig.coins fallback (tier yoksa examUploadCoins) doğru.

#### `MOD-02` — SubmissionTierResolver: canlı arşive (Firestore catalogNodes) AD eşleştirerek kademe + mevcut node id çözümü  _(P0 · efor: M)_
- **iOS davranışı:** placement(location): repository.items(.root) üzerinden üniversiteyi ArchiveNameMatch.matches ile bul (yoksa newUniversity). Fakülteleri çek; bölümü TÜM fakülteler altında matchesDepartment ile ara; ilk eşleşen fakülte+bölümü sabitle; bulunamazsa fakülte-adı eşleşmesi varsa newDepartment (facultyId set) yoksa newFaculty. Dersi matches ile ara (yoksa newCourse). Sınavı examText ile ara (varsa duplicate+examId, yoksa newExam). Her kademede mevcut node'un id'si (path.components.last) placement'a konur ki publisher yeniden kullansın.
- **iOS karşılığı/kaynak:** SubmissionTierResolver.swift; ArchiveRepository.items(at: ArchivePath) üzerinden çalışır.
- **Android mevcut:** Android'de arşiv Firestore okuyucusu yok (eski app RTDB Universitiess okuyor). Kademe/placement kavramı yok.
- **Android teknoloji:** class SubmissionTierResolver(private val archiveRepo: ArchiveRepository). ArchiveRepository eşdeğeri Browse/Archive alanından gelir (catalogNodes'u parentId ile sorgulayan Firestore reader: collection('catalogNodes').whereEqualTo('parentId', id).whereEqualTo('status','published').get()). Kök için parentId=='' sorgusu üniversiteleri verir. suspend fun placement(location): ResolvedPlacement; Tasks.await ile ardışık okumalar (Dispatchers.IO). Node id = doküman id'si.
- **Adımlar:**
  - ArchiveRepository arayüzünü tanımla/ortak alandan al: suspend fun items(parentId): List<ArchiveItem> (id,name,level,childCount).
  - SubmissionTierResolver.placement(location) port et: kök üniversiteleri çek, matches ile eşleştir.
  - Fakülteleri çek, her fakülte altındaki bölümleri matchesDepartment ile tara; ilk eşleşmede fakülte+bölüm sabitle, break.
  - Bulunamayınca fakülte-adı eşleşmesine göre newDepartment/newFaculty ayır.
  - Ders ve sınav seviyelerini matches/examText ile çöz; mevcut node id'lerini placement'a doldur.
  - tier(location)=placement(location).tier kısayolu ekle (AddExam tahmini için ortak).
  - Fake repo ile 6 kademeyi kapsayan birim test; gerçek 'vifi' DB ile bir entegrasyon testi.
- **Gradle/bağımlılık:** firebase-firestore (BoM). Bağımlılık: ArchiveRepository (Browse/Archive alanı).
- **Bağımlılık:** MOD-01, DEP: Browse/Archive alanı — ArchiveRepository (catalogNodes Firestore reader, named 'vifi' DB)
- **Tuzaklar:** Bölüm araması tüm fakülteleri gezer => N fakülte için N sorgu; büyük üniversitede yavaş olabilir, ama iOS de aynısını yapar (offline cache yardımcı olur, keepSynced/persistence aç). matchesDepartment yalnızca bölüm bulunamadığında fakülte-adı eşleşmesine düşülmeli (yeni fakülte vs yeni bölüm ayrımı). İstemci tarafı name eşleşmesi tolerant olmalı (Türkçe fold). Cancellation(coroutine iptali) propagate edilmeli.
- **Kabul kriteri:** Emülatör/entegrasyon: bilinen bir üniversite+fakülte+bölüm+ders için eklenen yeni sınav 'newExam' + doğru universityId/facultyId/departmentId/courseId döner; hiç olmayan üniversite 'newUniversity' döner; var olan sınav 'duplicate' + examId döner. Birim test: fake ArchiveRepository ile 6 kademenin hepsi.

#### `MOD-03` — RewardsConfig okuyucu: RTDB config/rewards/ios (tiers + examUploadCoins) canlı/tek-sefer okuma  _(P0 · efor: S)_
- **iOS davranışı:** config/rewards/ios node'unu canlı dinler (keepSynced(true)); value => RewardsConfig(examUploadCoins=pozitif int, tiers=SubmissionTier.rawValue anahtarlı pozitif int map). Approve sırasında stream'in İLK değeri okunur (tek-sefer). Node yoksa/çözülemezse nil (=> coins 0).
- **iOS karşılığı/kaynak:** FirebaseRewardsConfigReader.swift + RewardsConfigReading.swift.
- **Android mevcut:** Android'de rewards/coin kavramı yok; RTDB yalnız eski arşiv ve FCM için kullanılıyor.
- **Android teknoloji:** FirebaseDatabase.getInstance() (URL https://vifi-831a8.firebaseio.com — google-services.json'dan). ref=db.getReference('config/rewards/ios'); ref.keepSynced(true). Canlı için addValueEventListener -> Kotlin Flow (callbackFlow) veya LiveData. Approve'un tek-sefer okuması için ref.get() (Task) await, veya addListenerForSingleValueEvent. Parse: snapshot.child('examUploadCoins').getValue(Long::class) pozitifse al; tiers child'ını döngüyle SubmissionTier.raw -> pozitif int map'e çevir, bilinmeyen anahtar/negatif atla.
- **Adımlar:**
  - RewardsConfigReader sınıfı: path sabiti 'config/rewards/ios', keepSynced(true).
  - currentRewards(): suspend fun — ref.get().await() ile tek-sefer oku, parse et (approve bunu kullanır).
  - İsteğe bağlı rewardsFlow(): callbackFlow ile canlı (AddExam coin tahmini UI'si için).
  - parse(DataSnapshot) saf fonksiyona delege et (MOD-01 RewardsConfig/pozitif-int mantığı).
  - config yolu için tek bir sabit/merkezi config (ios vs android kararı tek yerden değişsin).
- **Gradle/bağımlılık:** firebase-database (zaten var, BoM'a taşı). kotlinx-coroutines-play-services (Task.await).
- **Bağımlılık:** MOD-01, DEP: App Check (Play Integrity) kurulumu — ayrı temel alan
- **Tuzaklar:** RTDB sayıları Long/Double gelebilir => getValue(Long)/(Double) kontrolü; pozitif değilse at. App Check RTDB'de enforce => Play Integrity token gerek (okuma reddi => null => coins 0, sessiz). config/rewards/ios yolu KARAR gerektiriyor (reuse-ios önerilir, açık soru). keepSynced açık kalırsa approve anında güncel değer hazır olur.
- **Kabul kriteri:** Console'da config/rewards/ios/tiers dolu iken approve doğru coin verir; node silinince approve coins=0 ile yine onaylar (çökme yok). Birim test: parse(Map) iOS ile aynı (negatif/bilinmeyen anahtar atlanır, fallback examUploadCoins).

#### `MOD-04` — Moderasyon veri servisi arayüzü + pendingSubmissions() sorgusu + hata eşleme (ModerationError)  _(P0 · efor: M)_
- **iOS davranışı:** pendingSubmissions(): submissions where status==pending getDocuments -> SubmissionRecordParser ile parse (bozuk atla) -> createdAt desc istemci-sıralaması. Hata eşleme: Firestore permissionDenied->permissionDenied; unavailable/deadlineExceeded->offline; NSURLError->offline; diğer->unknown(message). ModerationError Türkçe mesajları: notPending 'Bu gönderim artık beklemede değil; listeyi yenile.', alreadyCredited 'Bu gönderim için ödül zaten verilmiş.', offline 'İnternet bağlantısı yok...', permissionDenied 'Bu işlem için yetkin yok.'
- **iOS karşılığı/kaynak:** FirebaseModerationService.pendingSubmissions + moderationError(from:); ModerationServicing.swift (protocol + ModerationError).
- **Android mevcut:** Yok.
- **Android teknoloji:** interface ModerationService (suspend fun pendingSubmissions(): List<ExamSubmissionRecord>; approve; reject; deleteExam). FirebaseModerationService(firestore=FirebaseFirestore.getInstance(FirebaseApp.getInstance(),"vifi"), storage, resolver, rewards). pendingSubmissions: firestore.collection('submissions').whereEqualTo('status','pending').get().await(); documents.mapNotNull { SubmissionRecordParser.parse(it.id, it.data, it.getTimestamp('createdAt')?.toDate()) }.sortedByDescending { it.createdAt }. sealed class ModerationException(notPending/alreadyCredited/offline/permissionDenied/unknown(msg)) + mapException(FirebaseFirestoreException.code: PERMISSION_DENIED/UNAVAILABLE/DEADLINE_EXCEEDED) -> Türkçe mesajlar (strings.xml).
- **Adımlar:**
  - ModerationService arayüzünü ve ModerationException sealed class'ını tanımla.
  - FirebaseModerationService iskeletini kur (firestore 'vifi', storage, resolver, rewards enjekte).
  - pendingSubmissions(): whereEqualTo status==pending, get().await(), mapNotNull parse, createdAt desc sırala.
  - mapException(): FirebaseFirestoreException.Code -> ModerationException; ağ hatası -> offline.
  - Türkçe mesajları strings.xml'e ekle (ModerationError metinleri birebir).
- **Gradle/bağımlılık:** firebase-firestore (named DB 'vifi'), kotlinx-coroutines-play-services.
- **Bağımlılık:** MOD-01, MOD-02, MOD-03, DEP: Firebase/DI temel alanı — getInstance(app,"vifi") sağlayıcısı
- **Tuzaklar:** named DB getInstance(app,"vifi") kullan; (default) ASLA. orderBy EKLEME (index gerekir) — istemci sırala. Firestore offline persistence açıksa (önerilir) cache'ten eski liste gelebilir; pull-to-refresh server'a gitmeli (Source.SERVER opsiyonu düşünülebilir). Türkçe mesajlar strings.xml'de, localization 'tr' tek dil.
- **Kabul kriteri:** Admin olarak pending liste newest-first gelir; bozuk doküman atlanır; ağ yokken offline mesajı; admin olmayanda permissionDenied mesajı (kural reddi).

#### `MOD-05` — approve(): dosya kopyalama (pending->imagess/pdfs) + atomik WriteBatch yayın + coin kredi + idempotency  _(P0 · efor: L)_
- **iOS davranışı:** guard status==pending; placement=resolver.placement(location); coins=max(0, rewards.coins(tier) ?? 0); plan=ArchivePublishPlan.make. Dosya kopyalama SADECE plan.examId!=null iken (duplicate'te atlanır): her dosya getBytes(15 MiB) -> imagess/<uuid>.jpg|pdfs/<uuid>.pdf putData(contentType), order korunur. ensureStillCreditable: submission re-read pending değilse notPending; coins>0 && ledger/<submissionId> varsa alreadyCredited. commit (TEK batch): nodesToCreate set + childCountIncrements FieldValue.increment(1) + (examId varsa) exams/<id> set(examDocument) + (coins>0) wallets/<owner> set({balance:increment(coins), updatedAt:serverTimestamp}, merge) & ledger/<submissionId> set({type:earn,amount:coins,submissionId,tier:raw,createdAt:serverTimestamp}) + submissions/<id> update(status=approved,tier,coins,reviewedAt,publishedExamId?). return ApprovalResult(tier,coins,examId).
- **iOS karşılığı/kaynak:** FirebaseModerationService.approve/copyFiles/ensureStillCreditable/commit.
- **Android mevcut:** Yok; eski AddLessonActivity doğrudan tek görsel yükleyip RTDB legacy ağacına yazıyor (atomik değil, coin yok, kademe yok, idempotency yok).
- **Android teknoloji:** suspend fun approve(record): ApprovalResult. Dosya: storage.getReference(file.storagePath).getBytes(15L*1024*1024).await(); dest="$folder/${UUID.randomUUID()}.${kind.ext}"; meta=StorageMetadata.Builder().setContentType(kind.contentType).build(); storage.getReference(dest).putBytes(bytes, meta).await(). Batch: val b=firestore.batch(); b.set(catalog.document(node.id), node.fields); b.update(catalog.document(id),'childCount',FieldValue.increment(1)); b.set(exams.document(examId), examDocument); b.set(walletRef, mapOf('balance' to FieldValue.increment(coins.toLong()),'updatedAt' to FieldValue.serverTimestamp()), SetOptions.merge()); b.set(walletRef.collection('ledger').document(record.id), mapOf('type' to 'earn','amount' to coins,'submissionId' to record.id,'tier' to tier.raw,'createdAt' to FieldValue.serverTimestamp())); b.update(submissions.document(record.id), submissionUpdateMap); b.commit().await(). Tümü Dispatchers.IO; UI'de loading.
- **Adımlar:**
  - approve guard: record.status=='pending' değilse ModerationException.NotPending.
  - placement=resolver.placement(record.location); coins=max(0, rewards.currentRewards()?.coins(tier) ?: 0); plan=ArchivePublishPlan.make.
  - copyFiles(): examId!=null ise her dosyayı getBytes(15MiB)->yeni UUID ile imagess/pdfs'e putBytes(contentType), order koru; ardışık, IO dispatcher.
  - ensureStillCreditable(): submission re-read pending mi; coins>0 ise ledger/<id> yok mu kontrol et.
  - commit(): tek WriteBatch — nodesToCreate set, increments update, exams set, (coins>0) wallet merge + ledger set, submission update; commit().await().
  - ApprovalResult(tier, coins, publishedExamId) döndür.
  - Tüm uç durumlar (duplicate, coins=0) için entegrasyon testleri (Firestore+Storage emülatörü).
- **Gradle/bağımlılık:** firebase-firestore, firebase-storage, kotlinx-coroutines-play-services. (Opsiyon: WorkManager uzun kopya işleri için.)
- **Bağımlılık:** MOD-01, MOD-02, MOD-03, MOD-04, DEP: App Check Play Integrity (Storage/Firestore write)
- **Tuzaklar:** coins=0 ise wallet/ledger YAZMA (koşul), yine de publish+submission update yap. duplicate (examId=null) ise dosya kopyalama/node/exam YAZMA ama coins>0 ise wallet/ledger+submission update yap. ensureStillCreditable commit'ten HEMEN önce (yarış/çift-admin). ledger entryId=submissionId (çift kredi imkânsız). Bellek/OOM: dosyaları ardışık kopyala (paralel değil); 50x15MiB RAM riski (largeHeap/stream). Storage putBytes App Check token ister (enforce). Named DB 'vifi'. WriteBatch 500 op limiti (burada ~12, sorun yok ama bil). wallet'a FAZLADAN alan ekleme (kural hasOnly balance+updatedAt). FieldValue.increment(Long) kullan (Int overload yok). Kopya başarılı ama batch commit fail ederse: imagess/pdfs'te orphan dosya kalır (iOS de aynı; best-effort temizlik eklenebilir ama paritede zorunlu değil).
- **Kabul kriteri:** Yeni-üniversite gönderimi onaylanınca: 4 catalogNode + 1 exams dokümanı oluşur, dosyalar imagess/pdfs'e kopyalanır, wallet balance tier coin kadar artar, ledger/<submissionId> earn kaydı yazılır, submission approved olur, dönen ApprovalResult doğru tier+coins+examId. Aynı gönderimi ikinci kez onaylama denemesi alreadyCredited/notPending ile reddedilir (çift node/çift coin YOK). duplicate gönderim: dosya/exam yazılmaz, (config'e göre) 10 coin verilir. coins=0 iken wallet/ledger yazılmaz ama yayın olur.

#### `MOD-06` — reject(): gönderimi reddet (opsiyonel sebep, coin/yayın yok)  _(P0 · efor: S)_
- **iOS davranışı:** guard status==pending; submissions/<id> update: status=rejected, reviewedAt=serverTimestamp, (sebep boş değilse) rejectionReason=reason.prefix(120). Coin/arşiv değişimi YOK.
- **iOS karşılığı/kaynak:** FirebaseModerationService.reject.
- **Android mevcut:** Yok (eski app'te gönderim/red kavramı yok).
- **Android teknoloji:** suspend fun reject(record, reason:String?): guard pending; val data=hashMapOf('status' to 'rejected','reviewedAt' to FieldValue.serverTimestamp()); reason?.takeIf{it.isNotEmpty()}?.let{ data['rejectionReason']=it.take(120) }; firestore.collection('submissions').document(record.id).update(data).await(); mapException ile sar.
- **Adımlar:**
  - reject(record,reason) guard pending kontrolü.
  - update map'i oluştur (status, reviewedAt, opsiyonel rejectionReason<=120).
  - submissions/<id>.update().await(); hata eşlemesiyle sar.
- **Gradle/bağımlılık:** firebase-firestore.
- **Bağımlılık:** MOD-01, MOD-04
- **Tuzaklar:** reason ilk 120 karaktere kırpılmalı (ExamSubmission.maxTextLength; kural update'te alan doğrulamıyor ama tutarlılık için). update() (set değil) — mevcut alanları korur. reddedilen gönderime tekrar red denenirse notPending.
- **Kabul kriteri:** pending gönderim reddedilince status=rejected + reviewedAt yazılır, sebep verilirse rejectionReason (<=120) kaydedilir; coin/exam/catalogNode değişmez; liste öğesi kaldırılır.

#### `MOD-07` — deleteExam(): admin arşiv sınav silme (Browse swipe-sil) + childCount azaltma + best-effort Storage temizliği  _(P1 · efor: M)_
- **iOS davranışı:** path.components.last=examId, path.parent.components.last=courseId (yoksa 'Silinecek sınav bulunamadı'). exams/<examId> oku, files[].storagePath topla. Batch: exams/<examId> sil + catalogNodes/<courseId>.childCount increment(-1); commit. Sonra her storagePath için best-effort delete (hata yoksayılır).
- **iOS karşılığı/kaynak:** FirebaseModerationService.deleteExam; Browse tarafı BrowseView.swipeActions + performDelete (yalnız isAdmin && level==exam, onay alert 'Sınav silinsin mi?').
- **Android mevcut:** Yok; eski app'te admin sınav silme yok.
- **Android teknoloji:** suspend fun deleteExam(path/ArchivePath). Browse alanında: RecyclerView ItemTouchHelper swipe VEYA Compose SwipeToDismiss ile 'Sil' aksiyonu, AlertDialog onayı. Servis: examRef=exams.document(examId); snap=examRef.get().await(); storagePaths=(snap.get('files') as? List<Map>)?.mapNotNull{it['storagePath']}; val b=batch(); b.delete(examRef); b.update(catalog.document(courseId),'childCount',FieldValue.increment(-1L)); b.commit().await(); storagePaths.forEach{ runCatching{ storage.getReference(it).delete().await() } }.
- **Adımlar:**
  - deleteExam(path): examId/courseId türet (path yoksa exams dokümanı courseId fallback).
  - exams/<examId> oku, files storagePath listesini çıkar.
  - WriteBatch: exams sil + catalogNodes/<courseId> childCount increment(-1), commit.
  - storagePaths için best-effort storage delete (runCatching).
  - Browse ekranında swipe-sil aksiyonu + onay dialogu (yalnız admin & exam seviyesi).
- **Gradle/bağımlılık:** firebase-firestore, firebase-storage.
- **Bağımlılık:** MOD-01, MOD-04, MOD-09, DEP: Browse/Archive alanı — ArchivePath/ArchiveItem + liste ekranı
- **Tuzaklar:** courseId Browse ArchivePath'inden gelir; Android Browse ArchivePath zincirini sağlamıyorsa fallback: exams dokümanındaki 'courseId' alanından türet. Storage silme App Check ister; hata yutulur (orphan kalabilir, iOS de böyle). Yalnız admin & exam seviyesinde göster (MOD-09 gating). Silme geri alınamaz => onay dialogu zorunlu.
- **Kabul kriteri:** Admin, Browse'da ders altındaki bir sınavı sağa kaydırıp onaylayınca: exams dokümanı silinir, course childCount 1 azalır, liste öğesi kaybolur, Storage dosyaları (varsa) silinir; admin olmayan kullanıcı swipe-sil aksiyonunu görmez.

#### `MOD-08` — Moderasyon kuyruk ekranı (ModerationView + ViewModel) — Compose  _(P0 · efor: M)_
- **iOS davranışı:** Bekleyen gönderimler listesi; satır: kind ikonu (görsel/PDF), exam başlığı, 'ders · bölüm', üniversite, sağda 'N görsel'/'N PDF'. Boş durum 'Bekleyen gönderim yok', loading 'Gönderimler yükleniyor…', hata+retry, pull-to-refresh, footer 'N gönderim onay bekliyor.'. loadIfNeeded (ekran başına bir kez; iptal edilirse tekrar denenir). screenView('Moderation') analytics. approve/reject başarıda öğe listeden kaldırılır. Yalnız admin erişir.
- **iOS karşılığı/kaynak:** ModerationView.swift + ModerationViewModel.swift (Loadable state, CancellationError'da hasLoaded reset).
- **Android mevcut:** Yok.
- **Android teknoloji:** Jetpack Compose: ModerationScreen + ModerationViewModel(androidx.lifecycle.ViewModel) + StateFlow<Loadable<List<ExamSubmissionRecord>>>. LazyColumn satırları; PullRefresh (androidx.compose.material pullRefresh / material3). Boş/loading/hata state composable'ları. Navigation Compose ile detay rotası. viewModelScope.launch; iptalde CancellationException yakalanıp hasLoaded reset. (Alternatif: Views+ViewModel+RecyclerView+SwipeRefreshLayout.) Analytics screen_view('Moderation').
- **Adımlar:**
  - ModerationViewModel: StateFlow<Loadable<List>>, loadIfNeeded/refresh/approve/reject, iptal yönetimi.
  - ModerationScreen LazyColumn + satır composable (ikon, exam, ders·bölüm, üniversite, N dosya).
  - Boş/loading/hata/pull-refresh durumları + footer sayaç.
  - Navigation rotası + Hesap ekranından giriş (MOD-09).
  - screen_view('Moderation') analytics.
- **Gradle/bağımlılık:** androidx.compose.* (BOM), androidx.navigation:navigation-compose, androidx.lifecycle:lifecycle-viewmodel-compose, material3.
- **Bağımlılık:** MOD-04, MOD-09, DEP: Analytics temel alanı, DEP: Compose/AndroidX temel alanı
- **Tuzaklar:** Compose ön koşulu AndroidX+AGP8+compileSdk34. Loadable sealed state (Loading/Loaded/Failed) iOS Loadable ile eşleşmeli. approve/reject sonrası optimistic remove. Türkçe metinler strings.xml.
- **Kabul kriteri:** Admin ekranı açınca pending liste yüklenir ve satırlar iOS ile aynı bilgiyi gösterir; boş/loading/hata/pull-refresh çalışır; footer sayacı doğru; onay/red sonrası öğe anında kaybolur; screen_view atılır.

#### `MOD-09` — Moderasyon detay ekranı: onayla/reddet akışı + sonuç diyaloğu + analytics  _(P0 · efor: M)_
- **iOS davranışı:** Detay alanları (Üniversite/Fakülte/Bölüm/Ders/Sınav/Tür/Tarih), 'Dosyaları incele (N görsel/PDF)' linki, 'Onayla ve Yayınla' ve 'Reddet' butonları. Reddet: alert + opsiyonel sebep TextField ('Bu gönderim arşive eklenmez ve coin verilmez.'). Onay başarı: 'Onaylandı' alert, mesaj coins>0 ise '<tier.label>: gönderene <coins> coin verildi.' değilse 'Gönderim onaylandı.' İşlem sırasında butonlar/dismiss kilitli, ProgressView. Footer: 'Onaylarsan sınav arşive eklenir ve gönderene yenilik kademesine göre coin verilir.' Analytics: submission_approved(tier=raw), submission_rejected.
- **iOS karşılığı/kaynak:** ModerationDetailView.swift.
- **Android mevcut:** Yok.
- **Android teknoloji:** Compose detay ekranı; approve/reject viewModel çağrıları; isWorking ile buton disable + CircularProgressIndicator; AlertDialog (reddet sebebi için OutlinedTextField) ve sonuç AlertDialog. interactiveDismiss kilidi (BackHandler + navigation guard). Analytics: FirebaseAnalytics.logEvent('submission_approved', bundleOf('tier' to tier.raw)) ve 'submission_rejected'.
- **Adımlar:**
  - Detay composable: alanlar + 'Dosyaları incele' linki (MOD-10) + onayla/reddet butonları + footer.
  - Reddet AlertDialog (opsiyonel sebep OutlinedTextField + 'coin verilmez' mesajı).
  - Onay akışı: isWorking, approve çağrısı, sonuç AlertDialog (coins>0'a göre mesaj), dismiss.
  - İşlem sırasında geri/çıkış kilidi + ProgressIndicator.
  - submission_approved(tier)/submission_rejected analytics eventleri.
- **Gradle/bağımlılık:** compose material3, firebase-analytics.
- **Bağımlılık:** MOD-05, MOD-06, MOD-08, MOD-10
- **Tuzaklar:** Onay uzun sürer (dosya kopya + batch) => kesinlikle arka plan + loading; işlem sırasında geri tuşu/çıkış engellenmeli. Hata mesajı ModerationException Türkçe mesajı. Onay sonrası ekranı kapat (dismiss) ve kuyruktan düş.
- **Kabul kriteri:** Onayla'ya basınca loading görünür, başarı diyaloğunda doğru tier+coin mesajı; Reddet'te sebep girilebilir ve 'coin verilmez' uyarısı görünür; işlem sırasında butonlar kilitli; submission_approved(tier) ve submission_rejected eventleri atılır.

#### `MOD-10` — Gönderim dosya önizleme ekranı: görsel (dikey scroll) + PDF render (pending/ yetkili indirme)  _(P0 · efor: M)_
- **iOS davranışı:** Görsel kind: pending dosyaları order'a göre dikey LazyVStack, her sayfa yetkili indirme + decode (maxPixel 2200), loading/placeholder/'Görsel yüklenemedi'. PDF kind: ilk dosyayı yerele indirip scrolling PDF view, 'PDF yüklenemedi'. İndirme RemoteFileLoader + StorageURL.canonical ile (admin pending/ okuyabilir). Boş: 'Dosya yok'.
- **iOS karşılığı/kaynak:** SubmissionFilePreview.swift (RemoteFileImage, PDFFilePreview) + StorageURL + RemoteFileLoader.
- **Android mevcut:** Eski app Picasso ile görsel, barteksc android-pdf-viewer 2.3.0 (pre-AndroidX) ile PDF gösteriyor ama pending/ submission önizleme kavramı yok.
- **Android teknoloji:** Görsel: Firebase Storage referansından indirip Coil ile göster — io.coil-kt:coil-compose + coil-base ile FirebaseImageLoader benzeri veya storageRef.getBytes()/getFile() -> model. (Glide için FirebaseImageLoader com.firebaseui:firebase-ui-storage mevcut ama AndroidX fork gerekli.) Önerilen: storage.getReference(path).getBytes(limit).await() -> ImageBitmap, LazyColumn dikey. PDF: storage.getReference(path).getFile(tempFile).await() sonra AndroidPdfViewer (AndroidX fork com.github.mhiew:android-pdf-viewer:3.2.0-beta.3) VEYA native android.graphics.pdf.PdfRenderer (API21+, ek bağımlılık yok) ile sayfa sayfa render. order'a göre sırala; loading/hata durumları.
- **Adımlar:**
  - Dosyaları order'a göre sırala; Storage referanslarını path'ten üret.
  - Görsel: her dosyayı getBytes(downsample) ile indir, Coil/ImageBitmap ile dikey LazyColumn, loading/hata state.
  - PDF: ilk dosyayı cacheDir'e getFile ile indir, mhiew android-pdf-viewer veya PdfRenderer ile göster.
  - Boş/hata durumları ('Dosya yok', 'Görsel/PDF yüklenemedi').
  - PDF kütüphanesini AndroidX fork'a taşı / gradle bağımlılığını ekle (jitpack).
- **Gradle/bağımlılık:** io.coil-kt:coil-compose (veya Glide); PDF için com.github.mhiew:android-pdf-viewer (AndroidX, jitpack) ya da native PdfRenderer; firebase-storage.
- **Bağımlılık:** MOD-01, MOD-09, DEP: Storage yetkili indirme yardımcı (RemoteFileLoader eşdeğeri) — ortak alan
- **Tuzaklar:** Eski barteksc:2.3.0 pre-AndroidX; AndroidX'e geçişte mhiew fork'a veya PdfRenderer'a taşı (jcenter ölü, jitpack kullan). pending/ okuması admin claim + App Check gerektirir. Büyük görsellerde inSampleSize/maxPixel (2200) ile downsample => OOM önle. PDF geçici dosyası cacheDir'e indirilip sonra temizlenmeli. getBytes limitini 15 MiB'den büyük tut (indirme limiti).
- **Kabul kriteri:** Görsel gönderimde tüm sayfalar order'da dikey kayarak yüklenir, yükleme/placeholder/hata durumları çalışır; PDF gönderimde PDF kaydırmalı gösterilir; yetkisiz/ağ hatasında uygun 'yüklenemedi' mesajı; boş dosyada 'Dosya yok'.

#### `MOD-11` — Admin yetki kapısı: isCurrentUserAdmin() (custom claim) + Hesap'ta Moderasyon girişi + Browse swipe-sil görünürlüğü  _(P0 · efor: S)_
- **iOS davranışı:** isCurrentUserAdmin(): currentUser.getIDTokenResult().claims['admin']==true. Hesap ekranında moderationSection yalnız admin ise görünür (NavigationLink 'Moderasyon', 'Bekleyen gönderimleri incele ve onayla'). Browse'da swipe-sil yalnız isAdmin && level==exam.
- **iOS karşılığı/kaynak:** FirebaseAuthService.isCurrentUserAdmin; AccountView.moderationSection; BrowseView.canDeleteExams.
- **Android mevcut:** Android'de admin claim kontrolü hiç yok; eski AddLessonActivity'ye yetki kapısı olmadan ulaşılabiliyor.
- **Android teknoloji:** suspend fun isCurrentUserAdmin(): Boolean = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.claims?.get('admin') == true (hata/null => false). Hesap ekranında (Compose) LaunchedEffect ile isAdmin çöz, true ise Moderasyon giriş kartı göster. Browse'da aynı bayrakla swipe-sil aksiyonunu koşullu göster.
- **Adımlar:**
  - AuthService.isCurrentUserAdmin() suspend fonksiyonu (getIdToken claims['admin']).
  - Hesap ekranında admin bayrağını çöz ve Moderasyon giriş kartını koşullu göster.
  - Browse'da aynı bayrakla swipe-sil görünürlüğünü koşulla (level==exam).
  - Hata/null durumunda admin=false fallback.
- **Gradle/bağımlılık:** firebase-auth (zaten var), kotlinx-coroutines-play-services.
- **Bağımlılık:** DEP: Auth temel alanı (Phone Auth + session), MOD-08
- **Tuzaklar:** Claim sadece UI gizleme içindir; gerçek yetki Firestore/Storage kurallarındadır (isAdmin). getIdToken(false) cache'ten okur; oturum içinde verilen claim için forcingRefresh gerekebilir (iOS forcingRefresh:false kullanıyor — paritede false). Admin olmayan Moderasyon ekranına deep-link ile ulaşsa bile kurallar yazmayı reddeder (permissionDenied mesajı).
- **Kabul kriteri:** admin:true kullanıcıda Hesap'ta Moderasyon kartı ve Browse'da sınav swipe-sil görünür; normal kullanıcıda ikisi de gizli; claim okuma hatasında güvenli tarafta (admin değil) kalınır.

#### `MOD-12` — Eski doğrudan-yazan admin akışını kaldır + AdMob paritesini temizle + ortak kuralların parite doğrulaması  _(P1 · efor: M)_
- **iOS davranışı:** iOS'ta admin doğrudan sınav ekleme YOK (yalnız submission-moderasyon) ve reklam HİÇ yok; monetizasyon coin ekonomisi. Arşiv yazımı yalnız onay akışıyla catalogNodes/exams'e gider; legacy RTDB Universitiess kullanılmaz (cutover sonrası silinecek).
- **iOS karşılığı/kaynak:** (iOS'ta karşılığı olmayan Android artıklarının temizliği.)
- **Android mevcut:** AddLessonActivity.java + AddPdfLessonActivity.java doğrudan Storage'a yükleyip RTDB Universitiess legacy ağacına yazıyor (yetki kapısı yok, atomik değil, coin yok). build.gradle'da play-services-ads:17.2.0 ve kodda banner/interstitial/rewarded AdMob var. Parse SDK hâlâ bağlı.
- **Android teknoloji:** AddLessonActivity/AddPdfLessonActivity doğrudan-yazma yollarını kaldır; admin'in içerik eklemesi yalnız submission-moderasyon (ve gerekirse admin 'hızlı ekle' de aynı approve/publish servisini kullanmalı, RTDB Universitiess'e ASLA yazmamalı). AdMob: play-services-ads bağımlılığını ve banner/interstitial/rewarded (AdView, InterstitialAd, RewardedAd) kullanımlarını tüm ekranlardan kaldır (bu alanın kapsamında moderasyon/admin ekranlarındaki reklamlar). AndroidManifest APPLICATION_ID meta-data ve reklam izinleri temizle.
- **Adımlar:**
  - AddLessonActivity/AddPdfLessonActivity doğrudan RTDB Universitiess yazma kodunu kaldır (veya ekranları emekliye ayır).
  - play-services-ads bağımlılığını build.gradle'dan çıkar; AdView/InterstitialAd/RewardedAd + MobileAds.initialize + manifest meta-data'yı sil.
  - Admin içerik eklemenin yalnız approve/publish servisi üzerinden gittiğini doğrula.
  - Firebase emülatör kural testleriyle catalogNodes/exams/wallets/ledger/submissions yazımlarının kurala uyduğunu doğrula.
- **Gradle/bağımlılık:** KALDIR: com.google.android.gms:play-services-ads. (Parse kaldırımı ayrı alan ama not düş.) Firestore kuralları zaten yayında — DEĞİŞTİRME gerekmez; yalnız Android yazdığı doküman şekillerinin kurala uyduğunu emülatörle doğrula.
- **Bağımlılık:** MOD-05, MOD-07, MOD-11
- **Tuzaklar:** Legacy RTDB Universitiess okuması Browse alanında Firestore'a taşınacak (başka alan); burada yalnız YAZMA yollarını kapat. AdMob kaldırırken MobileAds.initialize ve manifest meta-data'yı da temizle yoksa crash. Firestore kuralları submissions update'i admin'e açık (alan doğrulaması yok) ama catalogNodes/exams/wallets/ledger için şekil doğrulaması var — Android yazımları (MOD-05) birebir uymalı; aksi halde PERMISSION_DENIED.
- **Kabul kriteri:** Eski doğrudan RTDB Universitiess yazma yolları kaldırılmış; projede play-services-ads bağımlılığı ve moderasyon/admin ekran reklamları yok; admin içerik ekleme/yayınlama yalnız onay-servisi üzerinden Firestore'a gidiyor; emülatör kural testinde MOD-05/06/07 yazımlarının tümü kuralları geçiyor.


---

## Bildirimler (FCM + izin + duyuru kutusu)  ·  `notifications`

**Özet:** iOS master (v3.1.0), bildirimleri üç parçaya ayırıyor: (1) FCM altyapısı — tek "all" broadcast topic'ine idempotent abonelik, APNs↔FCM köprüsü (Android'de gerek yok, native FCM), ön-plan/arka-plan sunumu ve dokunma işleme; (2) İzin akışı — profil tamamlandıktan ve onboarding turundan sonra, cihaz başına BİR kez gösterilen ön-izin (priming) ekranı, yalnız "Aç" deyince sistem prompt'u; (3) Duyuru kutusu — Firestore 'announcements' koleksiyonunu (isimli 'vifi' DB, createdAt desc, limit 50) canlı dinleyen, okundu durumunu CİHAZDA yerel tutan inbox, Home toolbar'da zil+rozet, ve uygulama açılınca ikon rozetini sıfırlama. Android tarafı çok eski (v2.5): MyFirebaseMessagingService hardcoded "FCM Test" başlık + yanlış ikon + kanal YOK; MyFirebaseInstanceIDService kaldırılmış FirebaseInstanceIdService API'sini kullanıyor (derlenmez), topic aboneliği yok; duyurular RTDB 'Announcements' düğümünden (kitap/fiyat alanlı eski arşiv şeması) okunuyor; AdMob + Parse mevcut (parite için kaldırılacak); POST_NOTIFICATIONS izni, kanal, App Check, DataStore yok. targetSdk 28, pre-AndroidX, jcenter (ölü). Aşağıdaki 13 kalem bu alandaki HER iOS davranışını Android native API'leriyle birebir karşılıyor.

**Android mevcut durum:** Yukarıdaki androidCurrentState alanında detaylandırıldı.

**Dokunulan backend sözleşmesi:** OKUR (yeni backend yazılmaz — iOS ile ORTAK sözleşme): Firestore isimli 'vifi' DB → announcements/<id> {title, body, createdAt(Timestamp)} read:+90|admin, write:false (Console/Admin yazar); getInstance(app,"vifi") ile erişilir. YENİ ANDROID YAZIMI (opsiyonel, sözleşmede owner read/write): users/<uid>/devices/<token> FCM token deposu {updatedAt, platform:"android"}. FCM topic 'all' (iOS ile aynı isim — değiştirilmez). App Check ENFORCE: Firestore okuması ve FCM için Play Integrity provider gerekir (Auth/güvenlik alanının deps'i). KALDIRILIR: RTDB 'Announcements' düğümü okuması/yazımı (eski arşiv, iOS cutover sonrası silinecek) — client artık yazmaz. Bu alan RTDB config/appUpdate, rewards, wallets vb. diğer düğümlere DOKUNMAZ.

**Android'e özgü riskler:**
- targetSdk 33+ olmadan POST_NOTIFICATIONS runtime prompt'u çalışmaz: targetSdk<33 iken Android 13 cihazda izin install-time verilir gibi davranır ama ilk kanal oluşturulduğunda sistem kendi prompt'unu gösterir (priming kontrolümüzü bozar). Priming akışı için AndroidX+targetSdk 34 migrasyonu ön koşuldur (deps).
- NotificationChannel olmadan Android 8 (API 26)+ cihazlarda bildirim SESSİZCE düşer — mevcut kod bu yüzden O+ cihazlarda çalışmıyor. Kanal ilk bildirimden ÖNCE (Application.onCreate) oluşturulmalı.
- PendingIntent API 31 (S)+ için FLAG_IMMUTABLE (veya FLAG_MUTABLE) ZORUNLU; mevcut kod yalnız FLAG_UPDATE_CURRENT kullanıyor → S+ cihazda IllegalArgumentException/çökme.
- Console'dan 'notification' payload'ı ile gönderilen broadcast'ler: uygulama ÖN PLANDA iken onMessageReceived hem notification hem data alır; ARKA PLANDA iken sistem tepsisi notification'ı otomatik gösterir, onMessageReceived yalnız data ile çağrılır (notification düşmez). Rozet/kanal kontrolü için data-only tercih edilir ama Console broadcast notification payload gönderir → her iki yol da ele alınmalı; aksi halde ön planda özel sunum, arka planda default kanal/ikon kullanılır.
- FirebaseInstanceIdService modern SDK'da yok; MyFirebaseInstanceIDService ve manifest INSTANCE_ID_EVENT kaydı silinmeli yoksa derleme/kayıt hatası. Token artık onNewToken(String) ile gelir.
- Named Firestore ('vifi') erişimi yalnız yeni Firestore SDK'da (BoM 32+, getInstance(app,"vifi")) var; eski firebase-database/firestore sürümünde yok — Firebase BoM migrasyonu ön koşul (deps).
- App Check ENFORCE: Play Integrity provider kurulmadan Firestore announcements okuması ve FCM aboneliği reddedilir; debug build için DebugAppCheckProviderFactory + Console'a debug token eklenmeli (Auth alanının deps'i).
- Android launcher ikon rozeti OEM'e göre değişir: standart Android'de rozet 'aktif bildirim noktası'na bağlı → cancelAll ile temizlenir; sayı-rozeti kullanan OEM launcher'lar (Samsung/Xiaomi) ShortcutBadger gerektirir (parite için standart davranış yeterli, OEM'ler not).
- R8/ProGuard: Firestore model sınıfı (Announcement) için reflection/serialization kuralları; -keepclassmembers ve @Keep gerekebilir. Ayrıca FCM payload anahtarları (title/body/message) obfuscation'dan etkilenmez ama model alanları etkilenir.
- Firestore getInstance(app,"vifi") yanlış DB'ye (default) düşerse announcements boş gelir — kurallar 'vifi' DB'de; sessiz boş liste hatası riski.
- DataStore readIds yazımı suspend/async; markAllRead inbox açılışında yazılırken config değişimi/süreç ölümüne dayanıklı olmalı. SharedPreferences senkron alternatif ama DataStore tercih edilir.

### İş Kalemleri

#### `N1` — MyFirebaseMessagingService'i modern FCM + bildirim kanalı ile yeniden yaz (ön/arka plan sunum + dokunma)  _(P0 · efor: M)_
- **iOS davranışı:** AppDelegate willPresent → ön planda [.banner,.list,.sound] gösterir; didReceive(response) → dokunma uygulamayı açar (duyurular in-app yaşar), Messaging.appDidReceiveMessage ile teslim metriği raporlanır. Payload içeriği inbox'ta değil, Firestore'dan gelir; push yalnız tetikleyici/haberdar edici.
- **iOS karşılığı/kaynak:** AppDelegate.swift: userNotificationCenter willPresent & didReceive; Messaging.appDidReceiveMessage(userInfo).
- **Android mevcut:** onMessageReceived yalnız getData().get('message') okur, başlık hardcoded 'FCM Test', ikon yanlış (common_google_signin_btn_icon_dark), kanal yok (O+ sessiz düşer), PendingIntent FLAG_IMMUTABLE yok (S+ çöker), getNotification() hiç ele alınmaz.
- **Android teknoloji:** com.google.firebase.messaging.FirebaseMessagingService, RemoteMessage.getNotification()/getData(), NotificationCompat.Builder, NotificationManagerCompat, android.app.PendingIntent (FLAG_IMMUTABLE), androidx.core.app
- **Adımlar:**
  - MyFirebaseMessagingService.java'yı (veya .kt) yeniden yaz: onMessageReceived içinde önce remoteMessage.getNotification() (title/body) varsa onu, yoksa remoteMessage.getData() içindeki 'title'/'body'/'message' anahtarlarını kullan (mevcut sadece 'message' okuyan davranışı geriye-uyumlu koru).
  - Bildirimi NotificationCompat.Builder(context, CHANNEL_ID_ANNOUNCEMENTS) ile kur: setContentTitle(title ?: 'ViFi'), setContentText(body), setSmallIcon(R.drawable.ic_notification) (yeni monokrom beyaz ikon — common_google_signin_btn_icon_dark KALDIR), setAutoCancel(true), setPriority(NotificationCompat.PRIORITY_HIGH), setColor(brand turuncu).
  - Dokunma için PendingIntent: Intent(this, HomeActivity.class) + extra 'open_inbox'=true + FLAG_ACTIVITY_CLEAR_TOP|SINGLE_TOP; PendingIntent.getActivity(ctx, reqCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE).
  - NotificationManagerCompat.from(context).notify(NOTIF_ID_ANNOUNCEMENTS, builder.build()) — sabit bir id/tag kullan (rozet temizliği N9 ile uyumlu olsun).
  - HomeActivity.onCreate/onNewIntent içinde getIntent().getBooleanExtra('open_inbox', false) ise Inbox ekranını aç (N7).
- **Gradle/bağımlılık:** Firebase BoM (com.google.firebase:firebase-bom) + firebase-messaging-ktx (17.4.0 → BoM sürümü). androidx.core:core-ktx (NotificationCompat/NotificationManagerCompat için). android.support.v4 referansı kaldırılır.
- **Bağımlılık:** Firebase BoM + AndroidX migrasyonu (altyapı alanı), N12 (kanal + default meta-data + ikon), HomeActivity Inbox açma (N7)
- **Tuzaklar:** O+ için kanal ZORUNLU (N12). S+ için PendingIntent FLAG_IMMUTABLE ZORUNLU. Console 'notification' payload'ı arka planda onMessageReceived'i ÇAĞIRMAZ (sistem tepsisi otomatik gösterir) — bu durumda default_notification_channel_id/icon/color meta-data (N12) devreye girer; data-only payload tercih edilirse tüm kontrol onMessageReceived'de kalır.
- **Kabul kriteri:** Android 8–14 cihazlarda Console'dan 'all' topic'ine gönderilen bildirim hem ön planda (uygulama açık) hem arka planda görünür; doğru başlık/gövde ve monokrom ikon ile; dokunınca uygulama açılıp Inbox'a gider; S+ cihazda çökme yok.

#### `N2` — onNewToken + 'all' topic aboneliği; kaldırılmış MyFirebaseInstanceIDService'i sil  _(P0 · efor: S)_
- **iOS davranışı:** AppDelegate messaging(_:didReceiveRegistrationToken:) her FCM token geldiğinde messaging.subscribe(toTopic: 'all') çağırır (idempotent, her açılışta güvenli). PushTopic.broadcast = 'all'.
- **iOS karşılığı/kaynak:** AppDelegate.swift MessagingDelegate extension; PushTopic enum.
- **Android mevcut:** MyFirebaseInstanceIDService com.google.firebase.iid.FirebaseInstanceIdService + onTokenRefresh() kullanır (modern firebase-messaging ile derlenmez), registerToken() boş, hiçbir topic'e abone olmaz.
- **Android teknoloji:** com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic(String), onNewToken(String), Task addOnCompleteListener/addOnFailureListener
- **Adımlar:**
  - MyFirebaseInstanceIDService.java'yı SİL ve AndroidManifest'ten INSTANCE_ID_EVENT service kaydını kaldır (FirebaseInstanceIdService modern SDK'da yok, derlenmez).
  - MyFirebaseMessagingService içine onNewToken(String token) override et: FirebaseMessaging.getInstance().subscribeToTopic(PushTopic.BROADCAST) çağır (sabit 'all').
  - iOS her token callback'inde abone olduğu için: Application.onCreate (veya uygulama açılışı/oturum sonrası) içinde de FirebaseMessaging.getInstance().subscribeToTopic('all') çağır — onNewToken her açılışta tetiklenmeyebilir; subscribeToTopic idempotent.
  - PushTopic sabitini tek yerde tanımla: object PushTopic { const val BROADCAST = "all" } (iOS PushTopic.broadcast ile BİREBİR aynı string).
  - addOnFailureListener ile abonelik hatasını logla (iOS'taki 'Broadcast topic subscribe failed' logunun karşılığı).
- **Gradle/bağımlılık:** firebase-messaging (BoM). firebase-iid bağımlılığı (varsa) kaldırılır.
- **Bağımlılık:** N1 (aynı servis sınıfı), App Check Play Integrity (güvenlik alanı)
- **Tuzaklar:** Topic adı MUTLAK 'all' olmalı (iOS ile eşleşmezse broadcast iki platforma birden gitmez). subscribeToTopic App Check FCM enforce ile korunuyorsa Play Integrity gerekir. onNewToken oturum açılmadan da tetiklenebilir — token deposu (N11) oturum durumuna göre yazılmalı.
- **Kabul kriteri:** Uygulama ilk açılışta ve token yenilendiğinde 'all' topic'ine abone olur; Firebase Console → Cloud Messaging topic 'all' broadcast'i cihaza ulaşır; INSTANCE_ID_EVENT service kalıntısı yok; derleme temiz.

#### `N3` — POST_NOTIFICATIONS izni + profil/onboarding sonrası cihaz-başına tek seferlik priming ekranı  _(P0 · efor: M)_
- **iOS davranışı:** NotificationPrimingView cihaz başına BİR kez gösterilir: profil (Home) + onboarding turu bitince, UserDefaults 'notifications.primingOffered.v1' false ve OS izni .notDetermined iken; markOffered hemen set edilir; 'Aç'→requestAuthorization([.alert,.badge,.sound]) + grant'te registerForRemoteNotifications; 'Şimdi Değil' sistem prompt'unu harcamaz.
- **iOS karşılığı/kaynak:** NotificationPrimingView.swift + NotificationPrimingDefaults; HomeView.offerNotificationsIfNeeded; SystemNotificationService.requestAuthorization.
- **Android mevcut:** POST_NOTIFICATIONS izni yok, priming ekranı yok, DataStore yok; bildirim izni hiç yönetilmiyor.
- **Android teknoloji:** Manifest.permission.POST_NOTIFICATIONS, androidx.activity.result.ActivityResultContracts.RequestPermission, ContextCompat.checkSelfPermission, androidx.datastore:datastore-preferences, Build.VERSION.SDK_INT (TIRAMISU)
- **Adımlar:**
  - AndroidManifest'e <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/> ekle (API 33).
  - NotificationPrimingView'in Android karşılığını kur: Compose ise NotificationPrimingScreen composable (zil ikonu, 'Bildirimlere izin ver' başlığı, 'Yeni sınavlar eklendiğinde ve önemli duyurularda haberdar ol.' açıklaması, 'Bildirimleri Aç' primary + 'Şimdi Değil' secondary). Views alternatifi: BottomSheetDialogFragment.
  - İzin isteği: Activity/Fragment'te registerForActivityResult(ActivityResultContracts.RequestPermission()) ile Manifest.permission.POST_NOTIFICATIONS iste; 'Bildirimleri Aç'→launcher.launch(...), 'Şimdi Değil'→kapat (prompt harcanmaz).
  - Tek-seferlik bayrak: DataStore Preferences booleanPreferencesKey('notifications_priming_offered_v1'); gösterilmeden önce true yap (iOS markOffered mantığı).
  - Gösterim koşulu (iOS offerNotificationsIfNeeded birebir): onboarding turu kapalı VE profil tamamlanmış VE bayrak false VE Build.VERSION.SDK_INT>=33 VE ContextCompat.checkSelfPermission(POST_NOTIFICATIONS)!=GRANTED. Android<13'te izin install-time verilir → priming atlanır, doğrudan kanal oluşturulup topic'e abone olunur.
  - İzin verilince FirebaseMessaging topic aboneliğini teyit et ve (varsa) token'ı yaz (N11).
- **Gradle/bağımlılık:** androidx.datastore:datastore-preferences:1.1.1, androidx.activity:activity-ktx (veya activity-compose), targetSdk 33+ (tercihen 34/35).
- **Bağımlılık:** Profil tamamlama gate'i (profil alanı), Onboarding turu (onboarding alanı), AndroidX + targetSdk 33+ migrasyonu (altyapı), N6 (abonelik teyidi)
- **Tuzaklar:** Runtime prompt yalnız targetSdk>=33 iken çıkar; targetSdk<33 iken Android 13 cihaz ilk kanal oluşturulduğunda kendi prompt'unu gösterir ve priming kontrolümüzü bozar → AndroidX+targetSdk 34 migrasyonu ÖN KOŞUL. Priming tetikleyicisi profil+onboarding durumuna bağlı (deps). DataStore yazımı suspend.
- **Kabul kriteri:** Android 13+ cihazda: profil tamamlanıp onboarding bitince priming BİR kez açılır; 'Aç' sistem izin diyaloğunu tetikler, verilince bildirim gelir; 'Şimdi Değil' diyaloğu açmaz ve tekrar gösterilmez; Android 12-'de priming gösterilmeden abonelik/kanal kurulur; app silinip yeniden kurulmadıkça priming tekrar çıkmaz.

#### `N4` — İzin reddedilince/kalıcı reddedilince uygulama bildirim ayarlarına yönlendirme  _(P2 · efor: S)_
- **iOS davranışı:** iOS'ta priming yalnız bir kez teklif eder; sistem izni reddedildiğinde uygulama içi ek ayar yönlendirmesi master'da yoktur (OS Ayarlar'ına bırakılır). Android'de kalıcı red daha katı olduğundan Ayarlar deep-link'i Android'e özgü, parite-koruyucu bir eklentidir.
- **iOS karşılığı/kaynak:** NotificationServicing.permission() == .denied (davranışsal referans).
- **Android mevcut:** Yok.
- **Android teknoloji:** android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS / EXTRA_APP_PACKAGE, shouldShowRequestPermissionRationale, Intent, Snackbar (Material)
- **Adımlar:**
  - RequestPermission sonucunda isGranted=false ise ve shouldShowRequestPermissionRationale false dönerse (kalıcı red) kullanıcıya küçük bir uyarı/snackbar göster: 'Bildirimleri Ayarlar'dan açabilirsin'.
  - Ayarlar deep-link: Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()) (API 26+); eski sürümler için Settings.ACTION_APPLICATION_DETAILS_SETTINGS + Uri.fromParts('package', pkg, null) fallback.
  - Bu yolu Hesap/Ayarlar ekranından da erişilebilir yap (kullanıcı sonradan açmak isterse).
- **Gradle/bağımlılık:** Ek bağımlılık yok (Material snackbar zaten gelir).
- **Bağımlılık:** N3
- **Tuzaklar:** shouldShowRequestPermissionRationale yalnız Activity'de çalışır. ACTION_APP_NOTIFICATION_SETTINGS API<26'da yok → fallback gerekir.
- **Kabul kriteri:** İzin kalıcı reddedildiğinde kullanıcı tek dokunışla uygulamanın sistem bildirim ayarlarına gidebilir; verince dönüşte abonelik/kanal aktif.

#### `N5` — Announcement modeli + Firestore 'announcements' canlı okuyucu (isimli 'vifi' DB, createdAt desc, limit 50)  _(P0 · efor: M)_
- **iOS davranışı:** FirebaseAnnouncementsReader: collection 'announcements', order createdAt DESC, limit 50; addSnapshotListener; parse title zorunlu non-empty (trim), body+createdAt opsiyonel; hata→yield([]) + log. Non-sendable snapshot callback içinde parse edilir.
- **iOS karşılığı/kaynak:** FirebaseAnnouncementsReader.swift, Announcement.swift, AnnouncementsReading.swift.
- **Android mevcut:** Duyurular RTDB 'Announcements' düğümünden okunuyor (title/bookName/acıklama/lessonname/contact şeması); Firestore named DB erişimi yok.
- **Android teknoloji:** FirebaseFirestore.getInstance(FirebaseApp, "vifi"), com.google.firebase.firestore.Query (orderBy/limit/addSnapshotListener), ListenerRegistration, kotlinx.coroutines.flow.callbackFlow/awaitClose, com.google.firebase.Timestamp
- **Adımlar:**
  - data class Announcement(val id: String, val title: String, val body: String, val createdAt: Date?) tanımla (iOS Announcement birebir).
  - AnnouncementsRepository kur: val db = Firebase.firestore(FirebaseApp.getInstance(), "vifi") (ya da FirebaseFirestore.getInstance(app, "vifi")) — İSİMLİ DB, (default) DEĞİL.
  - Sorgu: db.collection("announcements").orderBy("createdAt", Query.Direction.DESCENDING).limit(50).
  - Canlı dinleyici için callbackFlow { val reg = query.addSnapshotListener { snap, err -> if (err!=null) { log; trySend(emptyList()) } else trySend(snap.documents.mapNotNull { parse(it) }) }; awaitClose { reg.remove() } } → Flow<List<Announcement>>.
  - parse(doc): title = doc.getString("title")?.trim(); if title boş → null (döküman atlanır); body = doc.getString("body")?.trim() ?: ""; createdAt = doc.getTimestamp("createdAt")?.toDate(); Announcement(doc.id, title, body, createdAt).
  - Hata/erişim reddi durumunda emptyList emit et + Logcat'e yaz (iOS 'Announcements listener failed' karşılığı).
- **Gradle/bağımlılık:** Firebase BoM + com.google.firebase:firebase-firestore-ktx (named DB için güncel sürüm). kotlinx-coroutines-play-services (await) opsiyonel.
- **Bağımlılık:** Firebase BoM + named Firestore altyapısı (backend/altyapı alanı), App Check Play Integrity (güvenlik alanı)
- **Tuzaklar:** getInstance(app, "vifi") kullanılmazsa (default) DB'ye düşer → kurallar orada yok, liste boş gelir (sessiz hata). Named DB desteği yalnız yeni Firestore SDK'da (BoM 32+). App Check enforce → okuma Play Integrity token'ı ister. Firestore kuralları read:+90|admin → anonim/çıkış durumunda okuma reddedilir. R8 için Announcement @Keep veya custom parse (getString ile manuel parse zaten reflection kullanmaz, güvenli).
- **Kabul kriteri:** Signed-in (+90) kullanıcıda Firestore 'vifi' DB'deki announcements canlı olarak Flow'tan gelir; createdAt'e göre en yeni önce, en fazla 50; title'ı olmayan döküman atlanır; body/createdAt opsiyonel; okuma hatası uygulamayı çökertmez, boş liste döner; Console'dan yeni duyuru eklenince liste anında güncellenir.

#### `N6` — AnnouncementsStore karşılığı: uygulama-kapsamlı ViewModel/holder + okundu durumu yerel (DataStore) + unreadCount  _(P0 · efor: M)_
- **iOS davranışı:** AnnouncementsStore: announcements + readIds(Set, UserDefaults 'announcements.readIds.v1'); unreadCount = okunmamış sayısı; markAllRead current'ları union sonra current ile intersect; reset() listeyi temizler; observe() signed-in boyunca reader.announcementsChanges()'i dinler.
- **iOS karşılığı/kaynak:** AnnouncementsStore.swift; RootView ProfileGate .task(id:user){ observe() }; RootView sign-out → announcements.reset().
- **Android mevcut:** Yok — RTDB okuması doğrudan Activity'de, okundu durumu/rozet kavramı yok.
- **Android teknoloji:** androidx.lifecycle.ViewModel/StateFlow, kotlinx.coroutines.flow (combine/stateIn/collectLatest), androidx.datastore:datastore-preferences (stringSetPreferencesKey, edit), viewModelScope/Application CoroutineScope, Hilt @Singleton (veya manuel DI)
- **Adımlar:**
  - Tekil (uygulama-kapsamlı) AnnouncementsStore kur (Hilt @Singleton veya Application-scoped) — hem Home toolbar (zil/rozet) hem Inbox aynı örneği paylaşmalı.
  - StateFlow<List<Announcement>> announcements: repository Flow'unu collect et; signed-in süresince canlı; sign-out'ta reset() → emptyList.
  - Okundu durumu: DataStore Preferences stringSetPreferencesKey('announcements_read_ids_v1') → StateFlow<Set<String>> readIds.
  - unreadCount: combine(announcements, readIds) { a, r -> a.count { it.id !in r } } → StateFlow<Int>.
  - markAllRead(): current = announcements.map{id}.toSet(); eğer current ⊄ readIds ise → yeni = (readIds ∪ current) ∩ current (iOS formUnion sonra formIntersection: eski/rotasyona uğramış id'leri budar, set sınırlı kalır); DataStore'a yaz.
  - reset(): yalnız listeyi temizle (readIds cihazda kalır, iOS reset() gibi).
  - Oturum değişiminde (sign-out) collect job'ını iptal et + reset (iOS announcements.reset + task(id:user)).
- **Gradle/bağımlılık:** androidx.datastore:datastore-preferences:1.1.1, androidx.lifecycle:lifecycle-viewmodel-ktx, (Hilt kullanılırsa) com.google.dagger:hilt-android.
- **Bağımlılık:** N5 (repository Flow), Oturum/SessionStore (auth alanı — sign-in/out akışı)
- **Tuzaklar:** Örnek Activity-scoped olursa rozet/okundu durumu tutarsızlaşır → mutlaka Application/Singleton scope. DataStore yazımı suspend; markAllRead inbox açılışında çağrılır, süreç ölümüne dayanıklı olmalı. combine/stateIn için SharingStarted.WhileSubscribed uygun.
- **Kabul kriteri:** Tek örnek üzerinden hem Home rozeti hem Inbox aynı veriyi görür; okunmamış sayısı doğru; Inbox açılıp markAllRead çağrılınca unreadCount 0 olur ve uygulama yeniden açılınca 0 kalır; duyurular rotasyona uğrayınca readIds seti şişmez (yalnız mevcut id'ler tutulur); sign-out'ta liste temizlenir, readIds cihazda korunur; config değişiminde (rotasyon) durum kaybolmaz.

#### `N7` — Inbox ekranı (InboxView paritesi): newest-first liste, satır tasarımı, boş durum, açılışta markAllRead + screen_view  _(P0 · efor: M)_
- **iOS davranışı:** InboxView: NavigationStack, başlık 'Bildirimler' inline, 'Bitti'. Liste insetGrouped; InboxRow = bell.fill badge + title(headline) + body(subheadline, boş değilse) + tarih(gün/ay/yıl) + okunmamışsa accent nokta ('Okunmadı' erişilebilirlik etiketi). Boş: 'Bildirim yok' / 'Yeni duyurular burada görünecek.' bell görseli. onAppear: screenView 'Inbox' + markAllRead.
- **iOS karşılığı/kaynak:** InboxView.swift; HomeView .sheet(isShowingInbox){ InboxView() }.
- **Android mevcut:** AnnouncementsActivity RTDB 'Announcements'ı CustomAdapter+ListViewItem ile ListView'e basar; okundu durumu/boş durum/analytics yok; 'duyuru ekle' butonu Instagram Toast'u gösterir.
- **Android teknoloji:** androidx.compose.foundation.lazy.LazyColumn + Material3 ListItem/Card (veya androidx.recyclerview.widget.RecyclerView + ListAdapter/DiffUtil), Material3 TopAppBar, java.text.SimpleDateFormat/java.time.format.DateTimeFormatter, FirebaseAnalytics.logEvent
- **Adımlar:**
  - InboxScreen (Compose: LazyColumn) veya InboxFragment (Views: RecyclerView). Eski AnnouncementsActivity'nin yerini alır.
  - Satır: başta zil ikonu (ic_bell, IconBadge benzeri daire arkaplan), VStack: title (titleMedium/headline, bold), body (bodyMedium/subheadline, boş değilse göster), tarih (labelSmall/caption, tertiary); sağda okunmamışsa accent renkli 8dp nokta (readIds.contains(id) değilse).
  - Tarih formatı: SimpleDateFormat("d MMMM yyyy", Locale("tr")) veya DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("tr")) — iOS .day().month().year().
  - Boş durum: ic_bell + 'Bildirim yok' başlık + 'Yeni duyurular burada görünecek.' (iOS EmptyStateView).
  - Ekran açılışında (LaunchedEffect/onResume): analytics.track(screen_view, screen_name='Inbox') + store.markAllRead().
  - Üst bar: başlık 'Bildirimler', kapat/geri ('Bitti' karşılığı) → finish/popBackStack.
  - N1'deki PendingIntent extra 'open_inbox' ile de bu ekrana girilebilsin.
- **Gradle/bağımlılık:** Compose BOM + material3 (veya recyclerview). firebase-analytics-ktx. Coil/Glide gerekmiyor (ikonlar yerel drawable).
- **Bağımlılık:** N6 (store), N5 (veri), N8 (toolbar açış), Analytics altyapısı (analytics alanı)
- **Tuzaklar:** markAllRead async DataStore yazımı — ekran kapanırken iptal edilmesin (uygulama-scope store'da yap). Tarih locale 'tr' olmalı yoksa 'October' çıkar. RecyclerView yolunda DiffUtil ile canlı güncelleme.
- **Kabul kriteri:** Inbox en yeni önce duyuruları gösterir; satırda başlık/gövde/tarih doğru, Türkçe tarih; okunmamış duyurularda nokta görünür, açılınca kaybolur; liste boşken boş durum metni görünür; açılışta Firebase Analytics'e screen_view(Inbox) düşer; eski Instagram 'duyuru ekle' butonu yok.

#### `N8` — Home toolbar zil ikonu + okunmamış rozeti; tap ile Inbox açma  _(P1 · efor: S)_
- **iOS davranışı:** HomeView toolbar trailing: Button{ isShowingInbox=true } label Label('Bildirimler', systemImage: unreadCount>0 ? 'bell.badge.fill' : 'bell'). Tap InboxView sheet açar.
- **iOS karşılığı/kaynak:** HomeView.swift toolbar ToolbarItem(.topBarTrailing).
- **Android mevcut:** HomeActivity toolbar'ında zil/rozet yok; duyurulara yalnız navigation drawer 'nav_slideshow' → AnnouncementsActivity ile gidiliyor (bu yol N10'da Inbox'a taşınacak).
- **Android teknoloji:** androidx.compose.material3.BadgedBox/Badge/TopAppBar (veya com.google.android.material.badge.BadgeDrawable + BadgeUtils), collectAsStateWithLifecycle, Navigation-Compose/startActivity
- **Adımlar:**
  - Compose: Home TopAppBar actions içinde IconButton{ BadgedBox(badge={ if(unread>0) Badge{ Text(unread.toString()) } }){ Icon(ic_bell) } } — unread>0 iken rozet görünür (iOS bell.badge.fill).
  - Views alternatifi: res/menu/home.xml içinde action_inbox item; MenuItem'a com.google.android.material.badge.BadgeDrawable/BadgeUtils.attachBadgeDrawable ile rozet; onPrepareOptionsMenu/observe ile sayıyı güncelle.
  - unreadCount StateFlow'unu collectAsStateWithLifecycle (veya LiveData observe) ile dinle; 0 iken rozet gizle, >0 iken göster.
  - Tıklanınca Inbox'ı aç (Compose navigation navigate('inbox') veya startActivity(InboxActivity)).
  - İkonu Home scaffold'una ekle — CoinBalanceBadge (sol) ve Hesap/Hakkında (sağ) ile aynı toolbar (Home alanıyla hizala).
- **Gradle/bağımlılık:** Compose material3 (BadgedBox) veya com.google.android.material:material (BadgeDrawable).
- **Bağımlılık:** N6 (unreadCount), N7 (Inbox hedefi), Home scaffold/toolbar (home alanı)
- **Tuzaklar:** Material BadgeDrawable Views yolunda API/ölçü hassas; Compose BadgedBox daha temiz. Rozet güncellemesi lifecycle-aware collect ile yapılmalı (leak önleme).
- **Kabul kriteri:** Home'da zil ikonu görünür; okunmamış duyuru varken rozet/badge görünür ve sayıyı yansıtır, hepsi okununca kaybolur; zile dokununca Inbox açılır.

#### `N9` — Uygulama ikon rozetini aktif olunca (ve açılışta) sıfırla  _(P1 · efor: S)_
- **iOS davranışı:** RootView.clearNotificationBadge() = UNUserNotificationCenter.setBadgeCount(0); scenePhase .active olunca VE cold-start task'ında çağrılır — bildirimden açılan uygulamada rozet takılı kalmasın.
- **iOS karşılığı/kaynak:** RootView.swift clearNotificationBadge; .onChange(of: scenePhase) .active + .task launch.
- **Android mevcut:** Yok — bildirim rozeti/temizliği kavramı yok.
- **Android teknoloji:** androidx.lifecycle.ProcessLifecycleOwner + DefaultLifecycleObserver (onStart), NotificationManagerCompat.cancel/cancelAll, (ops) me.leolin:ShortcutBadger
- **Adımlar:**
  - ProcessLifecycleOwner.get().lifecycle'a DefaultLifecycleObserver ekle; onStart/onResume (uygulama foreground'a gelince) clearNotificationBadge() çağır.
  - clearNotificationBadge(): NotificationManagerCompat.from(context).cancel(NOTIF_ID_ANNOUNCEMENTS) — N1'deki sabit id/tag ile yalnız duyuru bildirimlerini iptal et (launcher nokta-rozeti bu aktif bildirime bağlı olduğundan temizlenir). Tümünü temizlemek gerekirse cancelAll().
  - Cold-start için de çağır (iOS'un launch task'ı gibi): Application.onCreate veya HomeActivity.onCreate.
  - OEM sayı-rozeti (Samsung/Xiaomi) desteği istenirse ShortcutBadger.removeCount(context) ekle — opsiyonel, standart Android için gerekmez.
- **Gradle/bağımlılık:** androidx.lifecycle:lifecycle-process, androidx.core:core-ktx. (ops) me.leolin:ShortcutBadger:1.1.22.
- **Bağımlılık:** N1 (NOTIF_ID), Application sınıfı (altyapı)
- **Tuzaklar:** cancelAll() phone-auth bildirimini silebilir ama sessiz push görünür bildirim üretmez → güvenli; yine de dedicated NOTIF_ID ile cancel tercih. OEM launcher'ları standart cancel'a uymaz (ShortcutBadger notu). ProcessLifecycle gözlemcisi Application.onCreate'te bir kez eklenmeli.
- **Kabul kriteri:** Bildirim gelip uygulama arka plandayken launcher ikonunda nokta/sayı belirir; kullanıcı uygulamayı açınca (cold veya resume) rozet kaybolur; phone-auth sessiz push'ları görünür bildirim üretmediği için yanlışlıkla iptal edilmez (dedicated id kullanıldığından).

#### `N10` — Eski RTDB duyuru akışını (AnnouncementsActivity + AddAnnouncements) kaldır; AdMob ve Parse'ı parite için temizle  _(P1 · efor: M)_
- **iOS davranışı:** iOS master'da: 'Remove exam sharing', coin ekonomisine geçiş, reklam HİÇ yok; duyurular Firestore 'announcements' + in-app inbox. Eski RTDB 'Universitiess'/'Announcements' arşivi yalnız rollback için duruyor, cutover sonrası silinecek — client bunları okumaz/yazmaz.
- **iOS karşılığı/kaynak:** Tüm bildirim/duyuru akışı Firestore 'announcements' + AnnouncementsStore/InboxView üzerinden (yukarıdaki dosyalar).
- **Android mevcut:** AnnouncementsActivity RTDB 'Announcements' okur, AddAnnouncements RTDB'ye yazar; HomeActivity AdMob gösterir ve drawer'dan AnnouncementsActivity açar; build.gradle Parse + AdMob içerir.
- **Android teknoloji:** Manifest/layout temizliği; build.gradle bağımlılık kaldırma; com.google.android.gms.ads.* ve com.parse.* referanslarının silinmesi
- **Adımlar:**
  - AnnouncementsActivity.java, AddAnnouncements.java ve ilgili layout'ları (activity_announcements.xml, activity_add_announcements.xml) SİL; manifest'ten <activity> kayıtlarını kaldır.
  - HomeActivity nav_slideshow (drawer) → AnnouncementsActivity yönlendirmesini KALDIR; duyuru erişimi artık Home toolbar zil ikonu (N8) + Inbox (N7) üzerinden. Drawer item'ı Inbox'a bağla veya kaldır.
  - RTDB 'Announcements' yazımı client'tan tamamen kalkar (ortak sözleşmede announcements write:false, Console/Admin SDK yazar) — admin duyuru ekleme ekranı client'ta YOK.
  - AdMob'u kaldır (parite: iOS'ta reklam yok): HomeActivity MobileAds.initialize/AdView/loadAd, layout'taki <com.google.android.gms.ads.AdView>, manifest APPLICATION_ID meta-data, build.gradle play-services-ads:17.2.0 bağımlılığı.
  - Parse SDK'yı kaldır: build.gradle parse-android + parse-fcm-android:1.17.3 bağımlılıkları, Application init (Parse.initialize) ve manifest kayıtları — FCM artık native (N1/N2) ile yönetiliyor.
  - Kullanılmayan CustomAdapter/ListViewItem/ListViewAdapter (yalnız eski duyuru ListView'i içindi) ölü kodunu temizle.
- **Gradle/bağımlılık:** SİL: com.google.android.gms:play-services-ads:17.2.0, com.parse:parse-android:1.17.3, com.parse:parse-fcm-android:1.17.3, firebase-core (deprecated — firebase-analytics'e geç).
- **Bağımlılık:** N7 (yeni Inbox hazır olmalı), N8 (yeni erişim noktası), Monetizasyon/reklam kaldırma kararı (ürün)
- **Tuzaklar:** Parse FCM kaldırılınca FCM kaydının google-services + native FirebaseMessagingService ile sürdüğünden emin ol (N2). AdMob kaldırılması monetizasyon kararıyla çakışırsa koordine et (açık soru). Manifest'ten AdMob APPLICATION_ID silinmezse ve play-services-ads kalırsa derleme uyarısı/çökme olabilir — ikisini birlikte kaldır.
- **Kabul kriteri:** Uygulamada RTDB 'Announcements' okuması/yazması kalmaz; duyurular yalnız Firestore 'vifi' DB inbox'ından gelir; AdMob banner/interstitial/rewarded hiçbir ekranda görünmez; Parse ve AdMob bağımlılıkları build.gradle'da yok; drawer'daki eski duyuru girişi Inbox'a bağlı veya kaldırılmış; ölü adapter sınıfları yok.

#### `N11` — users/<uid>/devices/<token> FCM token deposu (opsiyonel, ileride kişiye-özel bildirim için)  _(P3 · efor: S)_
- **iOS davranışı:** iOS master'da bu yol 'ops' olarak işaretli (aktif kullanılmıyor, gelecekteki kişiye-özel/segment bildirimler için altyapı). Şimdilik yalnız 'all' topic aboneliği aktif.
- **iOS karşılığı/kaynak:** AppDelegate token akışı (devices alt-koleksiyonu iOS master'da henüz yazılmıyor — gelecek işi).
- **Android mevcut:** registerToken() boş; token hiçbir yere yazılmıyor.
- **Android teknoloji:** FirebaseFirestore (named 'vifi'), FieldValue.serverTimestamp(), SetOptions.merge(), FirebaseAuth.getInstance().currentUser, FirebaseMessaging.getInstance().token (ilk token için)
- **Adımlar:**
  - onNewToken(token) içinde ve oturum açıldıktan sonra: eğer FirebaseAuth.currentUser != null ise token'ı yaz.
  - Firebase.firestore(app, "vifi").collection("users").document(uid).collection("devices").document(token).set(mapOf("updatedAt" to FieldValue.serverTimestamp(), "platform" to "android"), SetOptions.merge()).
  - onNewToken oturumdan önce tetiklenirse token'ı DataStore'a sakla, oturum açılınca yaz (pending-token deseni).
  - Sign-out'ta ilgili token dökümanını silmeyi düşün (opsiyonel; eski token zaten sahip-okur kuralıyla korunur).
- **Gradle/bağımlılık:** firebase-firestore, firebase-auth (BoM).
- **Bağımlılık:** N2 (onNewToken), N5 (named DB client'ı), Oturum durumu (auth alanı)
- **Tuzaklar:** Firestore kuralı owner read/write → yalnız signed-in yazılmalı yoksa PERMISSION_DENIED. Named 'vifi' DB kullan. iOS master henüz yazmadığı için bu, backend sözleşmesindeki izinli ama kullanılmayan yolu doldurur — ileri uyumluluk; MVP'de atlanabilir.
- **Kabul kriteri:** Signed-in kullanıcıda FCM token'ı users/<uid>/devices/<token> altına {updatedAt, platform:android} ile yazılır; token yenilenince güncellenir; oturum yokken yazılmaya çalışılmaz (kural reddini önler).

#### `N12` — Bildirim kanalı + default_notification meta-data + monokrom bildirim ikonu/renk kaynakları  _(P0 · efor: S)_
- **iOS davranışı:** iOS'ta kanal kavramı yok; eşdeğeri UNAuthorizationOptions ([.alert,.badge,.sound]) ve sistem sunumu. Android kanalı bu sunum ayarlarının (ses/önem/rozet) muadilidir.
- **iOS karşılığı/kaynak:** SystemNotificationService.requestAuthorization options; AppDelegate presentation options.
- **Android mevcut:** Kanal yok (O+ bildirimler sessiz düşer); default_notification meta-data yok; bildirim ikonu yanlış (renkli Google sign-in ikonu).
- **Android teknoloji:** android.app.NotificationChannel / androidx.core.app.NotificationChannelCompat, NotificationManagerCompat.createNotificationChannel, NotificationManager.IMPORTANCE_HIGH, manifest meta-data (default_notification_*), vector drawable
- **Adımlar:**
  - Application.onCreate içinde kanalı oluştur (ilk bildirimden ÖNCE): NotificationChannel(CHANNEL_ID_ANNOUNCEMENTS='announcements', 'Duyurular', NotificationManager.IMPORTANCE_HIGH) + açıklama; NotificationManagerCompat.createNotificationChannel(...). Build.VERSION.SDK_INT>=O guard (NotificationChannel O+). NotificationChannelCompat.Builder ile AndroidX yolu tercih.
  - res/drawable/ic_notification.xml: monokrom (yalnız beyaz alfa) vektör ikon ekle — Android status bar ikonu silüet olmalı (renkli ikon beyaz kare görünür). Mevcut common_google_signin_btn_icon_dark'ı KULLANMA.
  - Manifest <application> içine Firebase default meta-data: com.google.firebase.messaging.default_notification_icon=@drawable/ic_notification, default_notification_color=@color/brandOrange, default_notification_channel_id=@string/default_notification_channel_id ('announcements').
  - Arka planda Console 'notification' payload'ı geldiğinde (onMessageReceived çağrılmaz) bu meta-data kullanılır — tutarlı ikon/renk/kanal sağlar.
- **Gradle/bağımlılık:** androidx.core:core-ktx (NotificationChannelCompat). Ek bağımlılık yok.
- **Bağımlılık:** Application sınıfı (altyapı), N1 (kanal id ortak)
- **Tuzaklar:** Kanal oluşturulmadan notify → O+ cihazda bildirim görünmez. Renkli ikon status bar'da beyaz kare olur → monokrom şart. default_notification_channel_id meta-data yoksa O+ arka-plan notification'ı 'Miscellaneous' fallback kanalına düşer.
- **Kabul kriteri:** Android 8–14'te duyuru bildirimleri sesli/önemli olarak görünür; status bar'da düzgün monokrom ikon ve marka rengi; uygulama arka plandayken Console notification-payload'ı da aynı kanal/ikon/renkle gelir; kanal ilk bildirimden önce mevcut.

#### `N13` — Bildirim alanı analytics olayları (Inbox screen_view) ve opsiyonel izin-sonucu/dokunma olayları  _(P2 · efor: S)_
- **iOS davranışı:** InboxView.onAppear environment.analytics.track(.screenView(name: 'Inbox')). AnalyticsEvent enum'da bildirime özel ayrı domain event yok (screenView yeterli). Tap, Messaging.appDidReceiveMessage ile teslim metriği için raporlanır.
- **iOS karşılığı/kaynak:** ServiceProtocols.swift AnalyticsEvent.screenView; FirebaseAnalyticsTracker.swift; InboxView.swift onAppear.
- **Android mevcut:** Bu alanda hiç analytics yok.
- **Android teknoloji:** com.google.firebase.analytics.FirebaseAnalytics (firebase-analytics-ktx), FirebaseAnalytics.Event.SCREEN_VIEW / Param.SCREEN_NAME, androidx.core.os.bundleOf
- **Adımlar:**
  - Inbox açılışında FirebaseAnalytics.getInstance(ctx).logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundleOf(SCREEN_NAME to 'Inbox')) — iOS .screenView(name:'Inbox') birebir.
  - (Ops) İzin sonucu: priming 'Aç' sonrası granted/denied'i domain event olarak logla (örn. 'notification_permission_result', param granted) — iOS master bu alanda yalnız screenView tutuyor; bu Android-ozel ek analitik, analytics alanıyla hizalanmalı.
  - (Ops) Bildirim dokunması: push'tan Inbox açıldığında bir 'notification_opened' event'i (teslim/etkileşim metriği) — iOS Messaging.appDidReceiveMessage ile FCM'e raporlar; Android notification-mesajlarında bunu FCM otomatik yapar, ekstra gerekmez.
  - Analytics çağrılarını merkezi AnalyticsTracker arayüzü üzerinden yap (iOS FirebaseAnalyticsTracker deseni) — doğrudan Activity'den değil.
- **Gradle/bağımlılık:** com.google.firebase:firebase-analytics-ktx (BoM); firebase-core kaldır.
- **Bağımlılık:** Analytics altyapısı/tracker arayüzü (analytics alanı), N7 (Inbox), N3 (izin sonucu)
- **Tuzaklar:** firebase-core deprecated → firebase-analytics-ktx (BoM) kullan. SCREEN_VIEW otomatik screen tracking ile çakışabilir; manuel logEvent yeterli. Domain event'leri çoğaltmak analytics alanının kapsamı — burada minimum (Inbox screen_view) tut.
- **Kabul kriteri:** Inbox açıldığında Firebase Analytics'e screen_view(screen_name=Inbox) olayı düşer; (varsa) izin sonucu/bildirim açılışı olayları merkezi tracker üzerinden ölçülür; doğrudan Activity içine gömülü analytics çağrısı yok.


---

## Zorunlu/Opsiyonel Güncelleme (force update)  ·  `forceupdate`

**Özet:** iOS master'da güncelleme kapısı tamamen RTDB config/appUpdate/ios düğümüne dayanır; App Store listesini SCRAPE ETMEZ. İki katmanlı çalışır: minimumVersion'ın altındaki sürümler KAPATILAMAZ tam ekran ForceUpdateView görür, latestVersion'dan eski ama minimum'u karşılayan sürümler KAPATILABILIR öneri görür. Canlı bir RTDB dinleyicisi (observe(.value)) ve bir LATCH mantığı vardır: kapı bir kez 'zorunlu' olunca, sonradan gelen okunamaz (nil) değer kapıyı AÇMAZ; kapı yalnızca kurulu sürümün artık şartı sağladığını kanıtlayan BAŞARILI bir okumada kalkar. Giriş ekranında (auth öncesi) de çalışır, keepSynced(true) ile ilk açılıştan itibaren çevrimdışı kullanılabilir. Android'de şu an bu mantığın yerine deprecated AsyncTask ile Play Store HTML'i kazıyan ve yıllardır kırık olan GooglePlayAppVersion.java var; bunun tamamen kaldırılıp RTDB config/appUpdate/android tabanlı Jetpack Compose kapısıyla değiştirilmesi gerekiyor. Backend tarafında ortak Firebase projesine android kardeş düğümü + database.rules.json validasyonu eklenmeli (ops onayı gerektirir).

**Android mevcut durum:** app/src/main/java/com/salticusteam/vifi/GooglePlayAppVersion.java: AsyncTask<String,Void,String> ile https://play.google.com/store/apps/details?id=... sayfasını indirip 'htlgb'>X.X.X</span' regex'i ile sürüm kazıyor. HomeActivity.onCreate (satır 40-51) bunu çağırıp version != BuildConfig.VERSION_NAME ise iptal edilemez boş bir AppCompat AlertDialog ('Error' başlıklı, 'Güncelle' butonu YOK, mağazaya yönlendirme YOK) gösteriyor. Sorunlar: (1) AsyncTask API 30'da deprecated; (2) Play Store HTML yapısı yıllar önce değişti, 'htlgb' CSS sınıfı artık yok -> regex hiçbir zaman eşleşmiyor -> kontrol pratikte ÖLÜ; (3) ağ çağrısı ana thread'den tetikleniyor, çevrimdışı/offline parite yok; (4) minimumVersion/latestVersion ayrımı, latch, canlı dinleme, mağazaya gidiş, iptal edilemez tam ekran kapı YOK. Proje tümüyle eski: pre-AndroidX (com.android.support), AGP 3.4.2, jcenter (ölü), Firebase 2018 SD…

**Dokunulan backend sözleşmesi:** RTDB (ortak vifi-831a8 projesi, realtime db). iOS yalnızca config/appUpdate/ios okur; config/appUpdate altında 'android' KARDEŞİ YOK ve database.rules.json'da android validasyon bloğu YOK. Bu alanda iki Android-özgü backend boşluğu doldurulmalı: (1) RTDB'ye config/appUpdate/android/{minimumVersion, latestVersion, storeURL(Play Store), message} düğümü (admin yazar, Firebase Console veya Admin SDK ile); (2) firebase/database.rules.json içine ios bloğunun birebir aynısı 'android' bloğu (read: config parent'tan herkes zaten true; write: auth.token.admin===true; minimumVersion/latestVersion regex ^[0-9]+(\.[0-9]+){0,3}$; storeURL isString & length<=500 & beginsWith 'https://'; message isString & length<=300; $other validate:false; hasChildren zorunlu). Bu değişiklik ORTAK backend'i etkiler (iOS prod'u kırmamak için yalnızca EKLEME; ios bloğuna dokunma) ve firebase deploy --only database gere…

**Android'e özgü riskler:**
- AsyncTask API 30'da deprecated; GooglePlayAppVersion AsyncTask'i tamamen kaldirilmali, Coroutine Flow'a tasinmali.
- Mevcut Play HTML scrape (htlgb regex) yillardir KIRIK -> uretimde guncelleme kapisi fiilen calismiyor; bu yuzden 'parite' bozulmus durumda, tam yeniden yazim sart.
- RTDB okumalari App Check ile ENFORCE ediliyor; PlayIntegrityAppCheckProviderFactory kurulmadan config/appUpdate/android okumasi onCancelled ile reddedilir -> reader her zaman nil doner -> ilk kurulumda kapi hic devreye girmez (latch guvenli ama kapi islevsiz). App Check init'i reader'dan ONCE olmali.
- AGP 8 ile BuildConfig.VERSION_NAME icin android.buildFeatures.buildConfig=true gerekir; aksi halde BuildConfig uretilmez.
- market://details?id=... intenti Play Servisi olmayan cihazlarda ActivityNotFoundException firlatir -> https://play.google.com fallback sart.
- Play Store policy: targetSdk 34/35 zorunlulugu; mevcut targetSdk 28 ile yeni yayin reddedilir (bu temel modernizasyon bagimligi).
- RTDB offline persistence (setPersistenceEnabled(true)) tum app icin TEK SEFER ve herhangi bir DB kullanimindan ONCE cagrilmali; yoksa keepSynced offline-first parite calismaz. Uygulama genelinde karar gerektirir.
- Kapi app-scoped olmali (Activity yeniden olusturmada/rotasyonda kaybolmamali); checker Flow'u singleton repository/Hilt @Singleton olarak tutulup app-level state'ten toplanmali, Activity.onCreate'te one-shot cagrilmamali.
- Compose'da geri tusu: ForceUpdateScreen'de BackHandler(enabled=true){} ile geri tiklama tuketilmeli; yoksa kullanici kapiyi geri tusuyla atlatir. Views alternatifinde onBackPressed override + setCancelable(false).
- config/appUpdate parent .read: true oldugundan android dugumu de giris ekraninda okunabilir; ancak App Check client token'i auth'tan bagimsiz saglanmali (anonim de olsa Play Integrity token uretir).
- R8/minify acildiginda (parite icin onerilir) RTDB'ye map'lenen model siniflari obfuscate edilmemeli; DataSnapshot.getValue(Class) yerine alan-alan manuel parse edilerek bu risk tamamen bertaraf edilebilir (iOS de manuel parse ediyor).

### İş Kalemleri

#### `UPD-01` — Backend: RTDB config/appUpdate/android dugumu olustur + database.rules.json'a android validasyonu ekle (ops onayi)  _(P0 · efor: S)_
- **iOS davranışı:** iOS config/appUpdate/ios okur; android kardesi ve kurali yok. Kurallar: ios blogu read=config parent'tan herkes true, write=admin, alan regex/uzunluk validasyonlari.
- **iOS karşılığı/kaynak:** firebase/database.rules.json satir 5-28 (config > appUpdate > ios).
- **Android mevcut:** config/appUpdate/android dugumu ve kurali YOK; Android RTDB config'i hic okumuyor (sadece Play HTML kaziyor).
- **Android teknoloji:** Firebase Realtime Database Security Rules (database.rules.json), firebase CLI (firebase deploy --only database), Firebase Console.
- **Adımlar:**
  - firebase/database.rules.json icinde config > appUpdate altina, mevcut 'ios' blogunun BIREBIR AYNISI olan 'android' kardes blogunu ekle (ios blogunu DEGISTIRME).
  - android blogu: ".write": "auth != null && auth.token.admin === true", ".validate": "newData.hasChildren()".
  - minimumVersion ve latestVersion icin: ".validate": "newData.isString() && newData.val().matches(/^[0-9]+(\\.[0-9]+){0,3}$/)".
  - storeURL icin: ".validate": "newData.isString() && newData.val().length <= 500 && newData.val().beginsWith('https://')" (market:// RTDB'ye YAZILMAZ; client'ta donusturulur).
  - message icin: ".validate": "newData.isString() && newData.val().length <= 300"; ayrica "$other": { ".validate": false }.
  - Firebase Console RTDB veya Admin SDK ile config/appUpdate/android dugumune baslangic degerlerini yaz: minimumVersion, latestVersion, storeURL=https://play.google.com/store/apps/details?id=com.salticusteam.vifi, message (ops).
  - Kullanici/ops onayi alindiktan sonra: firebase deploy --only database ile kurallari yayinla.
  - iOS prod'un kirilmadigini dogrula: ios dugumu okunabilir ve yazilabilir kalmali.
- **Gradle/bağımlılık:** Gradle degisikligi yok; firebase-tools CLI gerekir.
- **Bağımlılık:** Firebase proje admin erisimi / ops onayi
- **Tuzaklar:** ORTAK prod backend; yalnizca EKLEME yap, ios'a dokunma. Deploy ops onayi gerektirir. storeURL market:// olamaz (beginsWith https:// validasyonu reddeder).
- **Kabul kriteri:** config/appUpdate/android dugumu okunabiliyor; admin olmayan yazim reddediliyor; gecersiz minimumVersion (orn '3.x') kural tarafindan reddediliyor; storeURL https:// ile baslamayan deger reddediliyor; iOS tarafi etkilenmiyor.

#### `UPD-02` — AppUpdate domain modelleri: AppUpdateConfig, AppUpdate, AppUpdateStatus  _(P0 · efor: S)_
- **iOS davranışı:** AppUpdateConfig tum alanlar optional; eksik minimumVersion hard gate'i, eksik latestVersion optional prompt'u, eksik storeURL fallback'i tetikler. AppUpdateStatus = required/optional/upToDate.
- **iOS karşılığı/kaynak:** AppConfig.swift (AppUpdateConfig, AppConfigReading, AppStoreLink), RemoteAppUpdateChecker.swift (AppUpdateStatus, AppUpdate).
- **Android mevcut:** Hicbir model yok.
- **Android teknoloji:** Kotlin data class / sealed interface, android.net.Uri, kotlinx.coroutines.flow.Flow.
- **Adımlar:**
  - Kotlin data class AppUpdateConfig(val minimumVersion: String?, val latestVersion: String?, val storeUrl: android.net.Uri?, val message: String?) olustur (tum alanlar nullable).
  - Kotlin data class AppUpdate(val version: String, val storeUrl: Uri, val message: String?) olustur.
  - sealed interface AppUpdateStatus { data class Required(val update: AppUpdate): AppUpdateStatus; data class Optional(val update: AppUpdate): AppUpdateStatus; data object UpToDate: AppUpdateStatus } olustur.
  - interface AppConfigReading { fun configChanges(): Flow<AppUpdateConfig?> } olustur (nil=okunamaz/eksik/reddedildi).
  - object PlayStoreLink { fun fallbackUrl(packageName: String): Uri = Uri.parse("https://play.google.com/store/apps/details?id=$packageName") } (iOS AppStoreLink.productURL karsiligi).
- **Gradle/bağımlılık:** Kotlin + kotlinx-coroutines-core (bkz UPD-10).
- **Bağımlılık:** Kotlin + Coroutines kurulumu (UPD-10)
- **Tuzaklar:** Uri parse hatasinda null birakilmali (iOS URL.init(string:) gibi). data object Kotlin 1.9+ gerektirir; eski Kotlin'de object kullan.
- **Kabul kriteri:** Modeller derleniyor; Uri null olabildigi dogrulaniyor; AppUpdateStatus uc durumu kapsiyor.

#### `UPD-03` — AppVersion karsilastirici util (noktali sayisal, iOS semantigi birebir)  _(P0 · efor: S)_
- **iOS davranışı:** Noktali sayisal karsilastirma; eksik sonda=0; non-numeric baştaki rakam yoksa 0; 4 bilesene kadar; strictly newer.
- **iOS karşılığı/kaynak:** AppVersion.swift (isVersion(_:newerThan:), numericComponents(of:)).
- **Android mevcut:** Yalnizca version.equals(BuildConfig.VERSION_NAME) string esitligi var (HomeActivity:43) -> '2.10' < '2.9' gibi hatali davranis.
- **Android teknoloji:** Kotlin, toIntOrNull, takeWhile(Char::isDigit).
- **Adımlar:**
  - object AppVersion { fun isVersionNewer(candidate: String, than other: String): Boolean } olustur.
  - numericComponents(version): version'u once trim et, '.' ile BOS parcalari KORUYARAK bol (split('.', limit=-1) / omittingEmptySubsequences=false davranisi), her parcadan BASTAKI ASCII rakamlari al, yoksa 0 ('1-beta'->1, 'x'->0).
  - Karsilastirma: max(count) kadar dongu; eksik sonda bilesen=0 ('3.0'=='3.0.0'); ilk farkli bilesende candidate>other ise true; esitse false (strictly newer).
  - NAIVE String.split('.').map{ it.toInt() } KULLANMA -> non-numeric parcada NumberFormatException firlatir; iOS crash etmiyor, bu yuzden toIntOrNull + prefix-digit mantigi sart.
- **Gradle/bağımlılık:** Yok.
- **Tuzaklar:** Baslangic/sondaki bosluklar trim edilmeli. 4'ten fazla bilesen de guvenli calismali.
- **Kabul kriteri:** Birim testleri: isVersionNewer('3.1.0','3.0.9')==true; ('3.0','3.0.0')==false; ('2.10','2.9')==true; ('1-beta','1')==false; ('x','0')==false; ('2.5.0.1','2.5')==true.

#### `UPD-04` — RtdbAppConfigReader: config/appUpdate/android canli dinleyici (keepSynced + callbackFlow)  _(P0 · efor: M)_
- **iOS davranışı:** RTDB observe(.value) canli dinleyici; keepSynced(true); auth gerektirmez; withCancel/onCancelled -> nil (latch korunur); parse yalnizca iyi bicimli string tutar; bufferingNewest(1).
- **iOS karşılığı/kaynak:** FirebaseRemoteConfigReader.swift (path, keepSynced, observe(.value), withCancel->nil, parse).
- **Android mevcut:** RTDB config okumasi YOK; sadece Play HTML scrape (GooglePlayAppVersion).
- **Android teknoloji:** com.google.firebase.database.FirebaseDatabase, DatabaseReference, ValueEventListener, keepSynced(true), kotlinx.coroutines.flow.callbackFlow + awaitClose, trySend.
- **Adımlar:**
  - class RtdbAppConfigReader(private val db: FirebaseDatabase): AppConfigReading olustur; ref = db.getReference("config/appUpdate/android").
  - init veya ilk kullanimda ref.keepSynced(true) cagir (tek kucuk dugum, ilk acilistan offline — iOS keepSynced(true) paritesi).
  - configChanges(): Flow = callbackFlow { val listener = object: ValueEventListener { onDataChange(snap){ trySend(parse(snap)) } onCancelled(err){ Log.e(...); trySend(null) } }; ref.addValueEventListener(listener); awaitClose { ref.removeEventListener(listener) } }.
  - parse(snapshot): sadece iyi bicimli String alanlari tut; snapshot.child('minimumVersion').getValue(String::class.java)?.takeIf{ it.isNotEmpty() } sablonu; storeURL icin Uri.parse'i try/catch veya Uri.parse + scheme kontrolu ile guvene al, gecersizse null.
  - Tum alanlar null ise (dugum yok/bos) AppUpdateConfig yerine null don (iOS parse nil).
  - App Check: okuma oncesi FirebaseAppCheck init edilmis olmali (UPD-09 bagimligi); auth GEREKTIRME — giris ekraninda da calismali.
  - Flow'u .buffer(Channel.CONFLATED) veya callbackFlow'da tek-son-deger ile sinirla (iOS bufferingNewest(1) paritesi).
- **Gradle/bağımlılık:** firebase-database(-ktx) BoM uzerinden; kotlinx-coroutines-play-services opsiyonel (callback kullandigimiz icin sart degil).
- **Bağımlılık:** UPD-02, UPD-09 (App Check), UPD-11 (Firebase/RTDB kurulumu)
- **Tuzaklar:** onCancelled'de trySend(null) ZORUNLU (latch'i besler). getValue(Class) yerine alan-alan parse (R8 obfuscation ve tip guvenligi). keepSynced + offline persistence (UPD-11) birlikte calismali. addValueEventListener'i awaitClose'da removeEventListener ile MUTLAKA sok; aksi halde leak.
- **Kabul kriteri:** Dugum guncellenince Flow yeni AppUpdateConfig yayinliyor; dugum silinince null; okuma reddedilince (App Check yok) null yayinlaniyor; cikisi iptal edince listener removeEventListener ile kaldiriliyor; giris yapilmamisken de okuyabiliyor.

#### `UPD-05` — RemoteAppUpdateChecker: latch + required>optional oncelikli durum makinesi  _(P0 · efor: M)_
- **iOS davranışı:** statusChanges latch'li AsyncStream; required bir kez set olunca nil okumalar kapiyi korur; basarili sart-yok okuma latch'i temizler; minimumVersion required'i, latestVersion optional'i verir; required optional'i ezer; destinasyon yoksa upToDate.
- **iOS karşılığı/kaynak:** RemoteAppUpdateChecker.swift (statusChanges() latch dongusu, static status(for:installedVersion:fallbackStoreURL:)).
- **Android mevcut:** Yok; HomeActivity string-esitlik + tek-seferlik dialog.
- **Android teknoloji:** kotlinx.coroutines.flow (transform/scan/stateIn), BuildConfig.VERSION_NAME, android.net.Uri, Hilt @Singleton veya manuel singleton.
- **Adımlar:**
  - class RemoteAppUpdateChecker(reader: AppConfigReading, installedVersion: String = BuildConfig.VERSION_NAME, fallbackStoreUrl: Uri) olustur.
  - fun statusChanges(): Flow<AppUpdateStatus> = flow/transform: reader.configChanges()'i map ederken 'latched: AppUpdate?' degiskenini KORU (scan/transform ile stateful).
  - Saf fonksiyon statusFor(config, installedVersion, fallbackStoreUrl): storeUrl = config.storeUrl ?: fallbackStoreUrl; her ikisi de null ise UpToDate (iOS: cikissiz kapi gosterme — Android'de fallback hep var).
  - statusFor icinde: minimumVersion != null && AppVersion.isVersionNewer(minimumVersion, installedVersion) -> Required(AppUpdate(minimumVersion, storeUrl, message)); else latestVersion != null && isVersionNewer(latestVersion, installedVersion) -> Optional(...); else UpToDate. (required optional'i ezer).
  - Latch mantigi (iOS birebir): config != null ise status = statusFor(...); config == null ise status = latched?.let{ Required(it) } ?: UpToDate. Sonra: status Required ise latched = update; else if config != null -> latched = null (basarili okuma + sart yok -> latch temizle). config==null & Required degilse latch KORUNUR.
  - Flow'u app-scope'ta (singleton/Hilt @Singleton) tut; .distinctUntilChanged() + .stateIn(appScope, SharingStarted.Eagerly, UpToDate) ile paylasilan bir StateFlow uret.
- **Gradle/bağımlılık:** coroutines; app genelinde CoroutineScope (orn @ApplicationContext bagli SupervisorJob).
- **Bağımlılık:** UPD-02, UPD-03, UPD-04
- **Tuzaklar:** Latch mantigi stateful; basit map yetmez (transform ya da scan ile mutable latch tut). installedVersion'i test icin enjekte edilebilir birak. BuildConfig.VERSION_NAME icin buildConfig=true (AGP8). StateFlow app-scoped olmali ki Activity recreate'te latch kaybolmasin.
- **Kabul kriteri:** Birim testleri: config{min:3.2} & installed 3.1 -> Required; {latest:3.2} & 3.1 -> Optional; {min:3.2,latest:3.3} & 3.1 -> Required; once Required sonra null okuma -> hala Required (latch); once Required sonra {min:1.0} (sart yok) -> UpToDate (latch temizlendi); config null & hic Required olmamis -> UpToDate.

#### `UPD-06` — ForceUpdateScreen (Jetpack Compose): iptal edilemez tam ekran zorunlu kapi + root gate  _(P0 · efor: M)_
- **iOS davranışı:** Tam ekran, kapatilamaz, modal degil icerik YERINE basilir; Logo + 'Guncelleme Gerekli' + message + 'Guncelle' -> openURL(storeURL); sart kalkinca kendiliginden kalkar.
- **iOS karşılığı/kaynak:** ForceUpdateView.swift; RootView required durumunda bunu icerigin yerine render eder.
- **Android mevcut:** Iptal edilemez ama BOS bir AlertDialog ('Error', buton yok, magazaya gidis yok) HomeActivity.onCreate'te.
- **Android teknoloji:** Jetpack Compose (Material3), androidx.activity.compose.BackHandler, collectAsStateWithLifecycle (lifecycle-runtime-compose), Navigation-Compose veya kosullu root Composable.
- **Adımlar:**
  - @Composable ForceUpdateScreen(update: AppUpdate, onUpdate: () -> Unit) olustur: tam ekran Column, uygulama logosu (R.drawable/mipmap, Image), 'Guncelleme Gerekli' (headlineLarge, bold), message ?: 'Uygulamayi kullanmaya devam etmek icin en son surume guncellemen gerekiyor.' (bodyLarge, secondary), altta tam genislik 'Guncelle' Button -> onUpdate().
  - BackHandler(enabled = true) {} ekle — geri tusunu/gesture'i TUKET, kapi kapatilamaz olsun (iOS non-dismissible paritesi).
  - Root navigation/Scaffold seviyesinde: appState.updateStatus.collectAsStateWithLifecycle() ile dinle; status is Required -> ForceUpdateScreen'i TUM icerigin YERINE goster (modal/dialog degil, NavHost'u tamamen degistir — iOS RootView davranisi). Arka plandaki ekranlar interaktif KALMAMALI.
  - Status Required'dan cikinca (latch temizlenince) kapi otomatik kalkar, normal icerik geri doner (iOS 'gate lifts on its own').
  - Views+ViewModel alternatifi: tam ekran bir ForceUpdateActivity (android:launchMode singleTask, onBackPressed override edilerek bos, FLAG kullanarak disari cikilamaz) + LiveData<AppUpdateStatus> gozlemi; ancak Compose onerilir.
  - Butona basildiginda UPD-08'deki openStore(update.storeUrl) cagrilir.
- **Gradle/bağımlılık:** Compose BOM, material3, activity-compose, navigation-compose, lifecycle-runtime-compose (bkz UPD-10).
- **Bağımlılık:** UPD-05, UPD-08
- **Tuzaklar:** BackHandler olmadan kullanici geri tusuyla kapiyi atlatir. Kapi app-level state'ten surulmeli, tek Activity'de one-shot degil. Logo asset'i mevcut mipmap'ten alinabilir. Erisilebilirlik: baslik heading, buton contentDescription.
- **Kabul kriteri:** min>installed iken uygulama acilisinda tam ekran kapi; geri tusu/gesture kapiyi kapatmiyor; arka icerik erisilemez; config duzeltilip sart kalkinca kapi otomatik kalkiyor; 'Guncelle' Play Store'u aciyor; message null iken varsayilan metin gosteriliyor.

#### `UPD-07` — Opsiyonel (kapatilabilir) guncelleme onerisi — dialog/bottom sheet + tekrar-gosterme kontrolu  _(P1 · efor: M)_
- **iOS davranışı:** latestVersion'dan eski ama minimum'u karsilayan surum .optional dondurur; kapatilabilir oneri; required optional'i ezer.
- **iOS karşılığı/kaynak:** RemoteAppUpdateChecker.swift .optional(AppUpdate...) durumu (optional UI RootView tarafinda; iOS master'daki optional view davranisi dogrulanmali).
- **Android mevcut:** Opsiyonel oneri kavrami YOK.
- **Android teknoloji:** Compose Material3 AlertDialog/ModalBottomSheet, androidx.datastore:datastore-preferences, collectAsStateWithLifecycle.
- **Adımlar:**
  - Status Optional(update) iken kapatilabilir bir Material3 AlertDialog (veya ModalBottomSheet) goster: baslik 'Guncelleme Mevcut', metin update.message ?: varsayilan, onaylama 'Guncelle' -> openStore, ikincil 'Daha Sonra' -> kapat.
  - Tekrar-nag'i engelle: DataStore (Preferences) 'dismissed_optional_version' anahtarina kullanicinin reddettigi latestVersion'i yaz; ayni surum icin tekrar gosterme (acik soru UPD: ayni-surum-bir-kez varsayimi).
  - Optional dialog uygulamanin ana icerigini BLOKLAMAZ (iptal edilebilir); Required'dan farkli olarak BackHandler kullanilmaz, disari dokunarak/geri tusuyla kapanir.
  - Gosterimi uygulama ana ekrani (Home) ilk acildiginda tetikle; giris/onboarding akisi sirasinda gosterme (UX karari).
  - Required aktifken Optional ASLA gosterilmez (durum makinesi zaten Required donuyor).
- **Gradle/bağımlılık:** androidx.datastore:datastore-preferences; Compose Material3.
- **Bağımlılık:** UPD-05, UPD-08
- **Tuzaklar:** Ayni latestVersion icin surekli nag ETME; DataStore okuma async (suspend) — ilk frame'de gostermeden once oku. iOS'un optional UI detayi (banner mi dialog mi, ne siklikta) RootView okunarak netlestirilmeli (acik soru).
- **Kabul kriteri:** latest>installed & min<=installed iken kapatilabilir oneri cikiyor; 'Daha Sonra' ile kapaninca ayni surum icin tekrar cikmiyor; yeni bir latestVersion gelince tekrar cikiyor; Required aktifken oneri gosterilmiyor.

#### `UPD-08` — Play Store yonlendirme: market:// intent + https fallback  _(P0 · efor: S)_
- **iOS davranışı:** openURL(update.storeURL); storeURL config'ten, yoksa AppStoreLink.productURL fallback.
- **iOS karşılığı/kaynak:** ForceUpdateView.openStore() -> openURL(update.storeURL); AppConfig.swift AppStoreLink.productURL.
- **Android mevcut:** Mevcut dialog'da magazaya gidis butonu/aksiyonu HIC YOK.
- **Android teknoloji:** Intent(Intent.ACTION_VIEW), Uri.parse, market:// scheme, FLAG_ACTIVITY_NEW_TASK, ActivityNotFoundException.
- **Adımlar:**
  - fun openStore(context: Context, storeUrl: Uri) yardimci fonksiyonu olustur.
  - Once market://details?id=com.salticusteam.vifi intentini dene: startActivity(Intent(ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}")).addFlags(FLAG_ACTIVITY_NEW_TASK)).
  - ActivityNotFoundException yakala -> config'ten gelen https storeUrl (veya PlayStoreLink.fallbackUrl(packageName)) ile ACTION_VIEW ac.
  - storeUrl config'ten geliyorsa onu BIRINCIL kaynak yap (iOS: config.storeURL ?? fallback); market:// yalnizca Play uygulamasini acmak icin tercih, basarisizsa https.
  - Logo/paket adi guncel (com.salticusteam.vifi).
- **Gradle/bağımlılık:** Yok.
- **Bağımlılık:** UPD-02
- **Tuzaklar:** Play Servisi olmayan cihazda market:// patlar -> https sart. Android 11+ package visibility: ACTION_VIEW http(s)/market icin queries gerekmez ama emin olmak icin <intent> ile tarayici gorunurlugu test edilmeli. storeUrl null ise fallbackUrl(packageName) kullan.
- **Kabul kriteri:** Play uygulamasi olan cihazda 'Guncelle' Play Store uygulamasini dogru sayfada aciyor; Play'siz cihazda/emulatorde tarayicida https sayfasi aciliyor; crash yok.

#### `UPD-09` — App Check (Play Integrity) — RTDB config okumasi icin on kosul; reader'in zarif degradasyonu  _(P0 · efor: M)_
- **iOS davranışı:** iOS App Attest/DeviceCheck ile App Check; RTDB/Storage/Auth enforce; config okumalari App Check korumali ama auth gerektirmez.
- **iOS karşılığı/kaynak:** ViFiAppCheckProviderFactory (iOS Core/Services); FirebaseRemoteConfigReader yorumu 'App Check still guards them'.
- **Android mevcut:** App Check HIC YOK; firebase-appcheck bagimliligi yok.
- **Android teknoloji:** com.google.firebase:firebase-appcheck-playintegrity, com.google.firebase:firebase-appcheck-debug, FirebaseAppCheck, PlayIntegrityAppCheckProviderFactory, DebugAppCheckProviderFactory.
- **Adımlar:**
  - Application.onCreate'te FirebaseApp.initializeApp sonrasi: FirebaseAppCheck.getInstance().installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance()).
  - Debug build'lerde: DebugAppCheckProviderFactory.getInstance() kullan ve uretilen debug token'i Firebase Console > App Check'e kaydet (aksi halde RTDB okumalari reddedilir).
  - App Check init'inin RtdbAppConfigReader herhangi bir okuma yapmadan ONCE tamamlandigindan emin ol (init sirasi).
  - Reader zaten onCancelled->null dondugunden, App Check gecici reddinde kapi latch ile guvenli kalir; ancak ILK kurulumda token yoksa kapi hic devreye girmez — bunu logla ve izle (loginDiagnostics kapsami baska ajan).
  - Not: Bu is kalemi App Check'in GENEL kurulumuyla ortustur; sahiplik baska alanla paylasiliyorsa (Auth/App Check ajani) bu alt-adimi ORA ile koordine et, burada yalnizca force-update reader'in App Check'e bagimli oldugunu ve init sirasini garanti et.
- **Gradle/bağımlılık:** firebase-appcheck-playintegrity + firebase-appcheck-debug (debugImplementation) BoM uzerinden; Play Integrity API Google Cloud Console'da etkin olmali.
- **Bağımlılık:** UPD-11, Auth/App Check genel kurulumu (baska alan)
- **Tuzaklar:** Debug token Console'a eklenmezse geliştirmede RTDB okumalari reddedilir -> kapi test edilemez. Enforce ACILMADAN once monitoring modda dogrula. Bu alan Auth/App Check ajaniyla CAKISABILIR — cift kurulumdan kacin, koordine et.
- **Kabul kriteri:** Release'de Play Integrity ile, debug'da debug token ile config/appUpdate/android basariyla okunuyor (onDataChange tetikleniyor, onCancelled degil); App Check eksikken reader null donuyor ve app crash etmiyor.

#### `UPD-10` — Eski GooglePlayAppVersion.java ve HomeActivity guncelleme-dialog kodunu KALDIR  _(P0 · efor: S)_
- **iOS davranışı:** iOS App Store scrape YAPMAZ; karar remote config'ten. Android'in Play-scrape yaklasimi iOS'ta hic yok.
- **iOS karşılığı/kaynak:** RemoteAppUpdateChecker.swift yorumu: 'driven entirely by config/appUpdate/ios, not the App Store listing'.
- **Android mevcut:** GooglePlayAppVersion.java (AsyncTask Play HTML scrape, htlgb regex KIRIK) + HomeActivity.onCreate bos AlertDialog.
- **Android teknoloji:** Dosya silme; Java->Kotlin gecisinde HomeActivity ilgili blok temizligi.
- **Adımlar:**
  - app/src/main/java/com/salticusteam/vifi/GooglePlayAppVersion.java dosyasini TAMAMEN sil.
  - HomeActivity.java onCreate (satir 40-51) icindeki new GooglePlayAppVersion(...).execute() blogunu ve ilgili import'lari kaldir.
  - Kaldirilan kontrolun yerine yeni root-level update gate'in (UPD-06) devreye girdigini dogrula; HomeActivity artik guncelleme kontrolu yapmamali (sorumluluk app-level checker'da).
  - Kullanilmayan BuildConfig import'u / AlertDialog kalintilarini temizle.
  - Not (parite — reklam kaldirma): HomeActivity ve diger Activity'lerdeki MobileAds.initialize/AdView (satir 18-20, 55-60) iOS'ta reklam olmadigi icin ayri bir reklam-kaldirma is kaleminde temizlenmeli; force-update refactor'u sirasinda bu satirlarla cakisma olacagini not et (ayri alan).
- **Gradle/bağımlılık:** Yok (AsyncTask zaten platform; bagimlilik eklenmez, cikarilir).
- **Bağımlılık:** UPD-06
- **Tuzaklar:** HomeActivity reklam ve guncelleme kodu ic ice; reklam kaldirma ayri alanda ama ayni dosyada — merge cakismasina dikkat. Scrape kaldirilinca internet izni hala Firebase icin gerekli (manifest'te kalmali).
- **Kabul kriteri:** GooglePlayAppVersion.java repo'da yok; derleme hatasi yok; HomeActivity artik Play scrape yapmiyor; guncelleme kapisi yalnizca RTDB checker'dan geliyor.

#### `UPD-11` — Bu alan icin Gradle/bagimlilik ve Firebase RTDB kurulumu  _(P0 · efor: M)_
- **iOS davranışı:** FirebaseDatabase + App Check; tek kucuk dugum keepSynced; offline ilk acilistan.
- **iOS karşılığı/kaynak:** FirebaseRemoteConfigReader.swift (ViFiDatabase.shared, keepSynced).
- **Android mevcut:** firebase-database 16.1.0 (2018), AGP 3.4.2, jcenter (olu), Compose/Coroutines/DataStore/App Check YOK, targetSdk 28, buildConfig default.
- **Android teknoloji:** Firebase BoM, firebase-database-ktx, firebase-appcheck-playintegrity, kotlinx-coroutines, Compose BOM, navigation-compose, lifecycle-runtime-compose, datastore-preferences, Gradle/AGP 8.
- **Adımlar:**
  - Firebase BoM'a gec: implementation platform('com.google.firebase:firebase-bom:33.x') + firebase-database(-ktx) + firebase-appcheck-playintegrity (+ debugImplementation firebase-appcheck-debug). jcenter() yerine mavenCentral() (jcenter OLU).
  - Coroutines: implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.x'. Kotlin plugin + Compose BOM + activity-compose + material3 + navigation-compose + androidx.lifecycle:lifecycle-runtime-compose (collectAsStateWithLifecycle).
  - androidx.datastore:datastore-preferences (optional update dismiss durumu).
  - app/build.gradle: android.buildFeatures { buildConfig true; compose true } (AGP 8); compileSdk/targetSdk 34+ (Play zorunlulugu); versionName/versionCode guncelle.
  - Uygulama acilisinda RTDB offline persistence: FirebaseDatabase.getInstance().setPersistenceEnabled(true) TEK SEFER, herhangi bir DB kullanimindan ONCE (keepSynced offline-first paritesi icin).
  - Named Firestore NOTU: Bu alan RTDB kullanir (Firestore DEGIL); getFirestore(app,'vifi') bu is kaleminde gerekmez — RTDB getInstance yeterli.
  - R8/minify acilirsa (parite onerisi) RTDB modelleri icin kural; manuel parse ile obfuscation riski zaten yok.
- **Gradle/bağımlılık:** Buyuk olcude TEMEL modernizasyon (AndroidX + AGP8 + BoM) ile ortusur; bu alan o modernizasyona BAGIMLIDIR. jcenter -> mavenCentral; setPersistenceEnabled app-genel karar.
- **Bağımlılık:** Temel AndroidX/AGP8/Compose modernizasyonu (baska alan)
- **Tuzaklar:** setPersistenceEnabled yalnizca 1 kez ve DB erisiminden once; aksi halde IllegalStateException. buildConfig=true olmazsa BuildConfig.VERSION_NAME derlenmez. targetSdk 28 ile yeni Play yayini reddedilir. Firebase BoM'suz surum cakismalari.
- **Kabul kriteri:** Proje mavenCentral + Firebase BoM ile derleniyor; BuildConfig.VERSION_NAME erisilebilir; Compose + coroutines + App Check + datastore bagimliliklari cozuluyor; RTDB persistence tek sefer etkinlestiriliyor.

#### `UPD-12` — Birim ve entegrasyon testleri (AppVersion, latch, durum makinesi, parse)  _(P1 · efor: M)_
- **iOS davranışı:** iOS testleri ayni mantigi dogrular (latch, status, version compare).
- **iOS karşılığı/kaynak:** iOS test hedefleri (RemoteAppUpdateChecker/AppVersion davranisi).
- **Android mevcut:** Test YOK (build.gradle'da test bagimliliklari yorumlu).
- **Android teknoloji:** JUnit4, kotlinx-coroutines-test (runTest), app.cash.turbine:turbine, (ops) Firebase Emulator Suite database emulator.
- **Adımlar:**
  - AppVersion testleri (UPD-03 kabul kriterleri): trailing-zero, non-numeric, 2.10>2.9, 4 bilesen.
  - RemoteAppUpdateChecker testleri: sahte AppConfigReading Flow ile required/optional/upToDate; required>optional onceligi; LATCH (Required sonra null -> hala Required; Required sonra sart-yok basarili -> UpToDate); destinasyon yoksa UpToDate.
  - RtdbAppConfigReader.parse testleri: eksik/bos alan dusme; gecersiz Uri dusme; tum alanlar yok -> null; onCancelled -> null.
  - Turbine (app.cash.turbine) ile Flow emisyonlarini dogrula; coroutines-test (runTest, StandardTestDispatcher).
  - Opsiyonel: Firebase Emulator Suite (database emulator) ile reader entegrasyon testi (keepSynced, canli guncelleme).
- **Gradle/bağımlılık:** testImplementation junit, kotlinx-coroutines-test, turbine.
- **Bağımlılık:** UPD-03, UPD-04, UPD-05
- **Tuzaklar:** Flow latch stateful oldugundan emisyon sirasi onemli; Turbine ile sirali dogrula. Emulator testi CI'da opsiyonel.
- **Kabul kriteri:** Tum birim testleri gecer; latch senaryolari (Required->null->Required, Required->clear->UpToDate) dogrulanir; version compare kenar durumlari gecer.


---

## Onboarding (ilk açılış tanıtım turu)  ·  `onboarding`

**Özet:** iOS'ta Onboarding, giriş + profil tamamlama kapısından sonra Ana Ekran (archive) üzerine fullScreenCover olarak açılan, atlanabilir 4 sayfalık bir tanıtım turudur (Hoş geldin / Sınavları bul / Kilitli sınavları 100 coin ile aç / Soru ekle-coin kazan). Cihaz-bazlı "görüldü" bayrağı UserDefaults'ta "onboarding.tour.v1" anahtarında tutulur (profilden ve çıkış sıfırlamasından bağımsız). Hakkında ekranından "Tanıtım turunu tekrar izle" ile source="replay" olarak yeniden oynatılır ve bu bayrağı DEĞİŞTİRMEZ. Açılışta screen_view (screen_name=Onboarding), bitişte onboarding_complete (method=skipped|finished, source=first_launch|replay) analytics olayları gönderilir. Android'de bu özellik tamamen YOKTUR ve sıfırdan yazılacaktır. Önerilen Android yaklaşımı: Jetpack Compose (HorizontalPager) + DataStore Preferences; alternatif Views+ViewModel (ViewPager2+TabLayout). Backend'e hiç dokunmaz — tamamen istemci tarafı, yalnızca Firebase Analytics olayları. Mevcut AdMob tur altında görünmemeli (reklam kaldırma başka alanın işi). En kritik blokaj: tüm özellik AndroidX/Compose/Gradle-AGP modernizasyonu (jcenter→mavenCentral, com.android.support→AndroidX) tamamlanmadan derlenemez.

**Android mevcut durum:** Onboarding/tanıtım turu Android'de YOK. "Hakkında" (About) ekranı YOK — dolayısıyla "turu tekrar izle" girişi de yok. FirebaseAnalytics KULLANILMIYOR (app/build.gradle'da yalnızca deprecated com.google.firebase:firebase-core:16.0.8 var; firebase-analytics bağımlılığı ve hiçbir logEvent çağrısı yok — grep ile doğrulandı). DataStore YOK; kalıcılık için SharedPreferences bile kullanılmıyor. Proje tamamen pre-AndroidX: com.android.support:appcompat-v7:27.1.0, AGP 3.4.2, compileSdk/targetSdk 28, jcenter() (kapalı), salt Java, Activity/Fragment mimarisi (HomeActivity + UniversityFragment vb.), Jetpack Compose / Navigation / ViewModel / Coroutines YOK. HomeActivity mevcut AdMob banner (AdView) + GooglePlayAppVersion sürüm uyarısı içeriyor. Yani onboarding sıfırdan, modern yığında kurulmalı.

**Dokunulan backend sözleşmesi:** Yok. Onboarding tamamen istemci tarafıdır: Firestore/RTDB/Storage/Cloud Functions'a DOKUNMAZ, ortak backend sözleşmesini etkilemez. Kalıcılık cihaz-yereldir (DataStore Preferences, iOS'taki UserDefaults karşılığı). Tek "dış" temas noktası Firebase Analytics olaylarıdır: SCREEN_VIEW (screen_name="Onboarding") ve custom event "onboarding_complete" {method, source}. Not: Analytics olay adları iOS ile BİREBİR aynı olmalı (iOS firebaseName = "onboarding_complete" — "onboarding_completed" DEĞİL) ki ortak Firebase projesi (vifi-831a8) raporlarında platformlar tek metrikte birleşsin.

**Android'e özgü riskler:**
- BLOKAJ: Compose, DataStore, firebase-analytics-ktx, navigation-compose hepsi AndroidX gerektirir; mevcut com.android.support + AGP 3.4.2 + jcenter() ile DERLENMEZ. Onboarding'den önce global AndroidX/Gradle-AGP 8.x/mavenCentral modernizasyonu + compileSdk/targetSdk 34+ şart (ayrı foundation alanı). Bu tamamlanmadan hiçbir onboarding kalemi başlayamaz.
- DataStore ASENKRONdur (Flow + coroutines). iOS senkron UserDefaults.bool okur; Android'de bayrak yüklenene kadar turu göstermek/göstermemek kararını geciktirmek gerekir — aksi halde zaten 'görüldü' işaretli turun bir an yanıp sönmesi (flash) olur. Home'u Loadable/collectAsStateWithLifecycle ile kapıla.
- Cihaz-bazlı kalıcılık: DataStore dosyası çıkış (sign-out) akışında SİLİNMEMELİ. iOS bunu profil ve çıkış sıfırlamasının dışında tutar; Android'de de SessionStore/çıkış temizliği bu DataStore'a dokunmamalı.
- Sistem geri tuşu (predictive back dahil): iOS'ta geri yok. Android'de tam ekran tur sırasında geri tuşu davranışı tanımlanmalı (önerilen: geri = Atla, method=skipped) — aksi halde kullanıcı turu arkadaki Home'a düşerek markSeen çağrılmadan çıkabilir.
- Compose foundation HorizontalPager stabil fakat resmi NOKTA GÖSTERGESİ (indicator) YOK (eski Accompanist Pager deprecated). Noktaları elle çizmek gerekir (pagerState.currentPage / pageCount).
- Tam ekran sunum: WindowInsets/edge-to-edge, status/navigation bar, çentik (notch) ele alınmalı; iOS fullScreenCover karşılığı Compose'da bir route/Dialog ya da ayrı OnboardingActivity (NoActionBar, Fullscreen tema).
- Reklam parite: HomeActivity'deki AdView tur altında kalır; reklamların kaldırılması (ayrı alan) tamamlanmazsa tur ardında banner görünür — risk/çirkinlik. deps'e eklendi.
- Marka turuncusu üzerine siyah metin kontrastı (WCAG 3:1) korunmalı; Material3 varsayılan primary üzerine beyaz onPrimary kullanırsa kontrast düşer — containerColor=marka turuncusu, contentColor=Color.Black elle verilmeli.
- targetSdk 34+ geçişinde POST_NOTIFICATIONS vb. izinler gündeme gelir ancak onboarding bunları kullanmaz (bildirim alanı ayrı); onboarding izin İSTEMEZ.
- R8/ProGuard: DataStore ve Compose için tutma kuralları release'de gerekebilir; coin tutarları (100 coin, 'en eski iki sınav ücretsiz') metni Coin ekonomisi alanıyla senkron kalmalı.

### İş Kalemleri

#### `OB-1` — Onboarding build önkoşulları: AndroidX + Compose + DataStore + Analytics bağımlılıkları  _(P0 · efor: M)_
- **iOS davranışı:** iOS'ta SwiftUI+UserDefaults+FirebaseAnalytics zaten mevcut; Android karşılıklarının bağımlılık olarak kurulması gerekiyor.
- **iOS karşılığı/kaynak:** SPM/Firebase SDK; UserDefaults.standard; FirebaseAnalytics.
- **Android mevcut:** Hiçbiri yok; app/build.gradle pre-AndroidX, jcenter, AGP 3.4.2, Compose/DataStore/Analytics yok.
- **Android teknoloji:** Jetpack Compose BoM, DataStore Preferences, Navigation-Compose, Firebase Analytics KTX, Kotlin Coroutines
- **Adımlar:**
  - Global AndroidX/Gradle modernizasyonunun (jcenter()→mavenCentral(), com.android.support→AndroidX, AGP 3.4.2→8.x, compileSdk/targetSdk 34+, Java→Kotlin+Coroutines) tamamlandığını doğrula; onboarding bunun ÜZERİNE kurulur (deps).
  - app/build.gradle'a (yeni Kotlin DSL ya da Groovy) onboarding'in ihtiyaç duyduğu bağımlılıkları ekle: androidx.datastore:datastore-preferences (ör. 1.1.1); Compose BoM (androidx.compose:compose-bom, ör. 2024.09.xx) + androidx.compose.ui:ui, androidx.compose.material3:material3, androidx.compose.foundation:foundation (HorizontalPager/PagerState), androidx.compose.material:material-icons-extended; androidx.activity:activity-compose; androidx.navigation:navigation-compose; androidx.lifecycle:lifecycle-viewmodel-compose + lifecycle-runtime-compose (collectAsStateWithLifecycle).
  - Firebase Analytics için BoM ekle (com.google.firebase:firebase-bom, ör. 33.x) ve com.google.firebase:firebase-analytics-ktx; deprecated firebase-core'u kaldır.
  - android{} içinde buildFeatures { compose true } ve Kotlin compilerOptions/composeOptions'ı (veya org.jetbrains.kotlin.plugin.compose eklentisini) etkinleştir.
  - Compose + DataStore için R8/ProGuard tutma kurallarının gerekiyorsa proguard-rules.pro'ya eklendiğini doğrula.
- **Gradle/bağımlılık:** Ekle: androidx.datastore:datastore-preferences, compose-bom + material3/foundation/material-icons-extended/activity-compose/navigation-compose, lifecycle-*-compose, firebase-bom + firebase-analytics-ktx. Kaldır: firebase-core. Etkinleştir: buildFeatures.compose. Kaldır (ayrı alan): play-services-ads.
- **Bağımlılık:** Foundation (global): AndroidX migration + AGP 8.x + mavenCentral + Kotlin/Coroutines + Compose altyapısı, Analytics foundation: firebase-analytics-ktx entegrasyonu (OB-7 ile paylaşılır)
- **Tuzaklar:** Bu kalem bitmeden OB-2..OB-10 derlenemez. Compose Kotlin derleyici eklentisi sürümü Kotlin sürümüyle uyumlu olmalı. material-icons-extended APK boyutunu şişirir — yalnızca gereken ikonlar için alternatif: özel VectorDrawable.
- **Kabul kriteri:** Proje mavenCentral/google ile senkronize olup Compose+DataStore+firebase-analytics-ktx içeren boş bir @Composable ve bir DataStore okuması derlenip çalışır; jcenter ve firebase-core kaldırılmıştır.

#### `OB-2` — OnboardingPreferences: cihaz-bazlı 'görüldü' bayrağı (DataStore, OnboardingStore karşılığı)  _(P0 · efor: S)_
- **iOS davranışı:** isPresenting fresh install'da true; markSeen() ile kalıcı true yazılır ve kapanır. Cihaz-bazlı, profile/çıkış sıfırlamasından bağımsız. Anahtar 'onboarding.tour.v1', sürüm soneki ile bir-kez-gösterim.
- **iOS karşılığı/kaynak:** OnboardingStore (@Observable): UserDefaults.standard.bool(forKey:"onboarding.tour.v1"); markSeen set true + isPresenting=false; DEBUG seed(seen:into:).
- **Android mevcut:** Yok — hiçbir kalıcılık katmanı mevcut değil.
- **Android teknoloji:** DataStore Preferences (preferencesDataStore, booleanPreferencesKey, edit, Flow.map), Kotlin Coroutines
- **Adımlar:**
  - Preferences DataStore örneği oluştur: private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding").
  - Anahtar tanımla: val SEEN = booleanPreferencesKey("onboarding.tour.v1") — iOS ile aynı versiyon soneki; büyük yeniden tasarımda v2'ye çıkarılır.
  - class/singleton OnboardingPreferences(context): val isPresenting: Flow<Boolean> = dataStore.data.map { !(it[SEEN] ?: false) } (taze kurulumda true).
  - suspend fun markSeen() { dataStore.edit { it[SEEN] = true } } — yazıldıktan sonra isPresenting akışı false yayar.
  - İlk render'da yanıp sönmeyi önlemek için ham Boolean yerine 'yüklenmedi/presenting/gizli' durumunu veren bir Loadable/sealed state sun (iOS senkron okur; Android asenkron).
  - Çıkış (sign-out) akışının bu DataStore'u TEMİZLEMEDİĞİNDEN emin ol — cihaz-bazlı, kullanıcıdan bağımsız.
  - DEBUG/test için seed yardımcıları: suspend fun seed(seen: Boolean) (iOS OnboardingStore.seed karşılığı).
- **Gradle/bağımlılık:** androidx.datastore:datastore-preferences
- **Bağımlılık:** OB-1 (DataStore bağımlılığı)
- **Tuzaklar:** Asenkron okuma: UI, bayrak yüklenene kadar turu göstermemeli. DataStore dosyasının silinmesi (uygulama verisi temizliği) turu yeniden gösterir — iOS'taki UserDefaults ile aynı semantik, kabul edilebilir. runBlocking ile ana iş parçacığında bloklamaktan kaçın.
- **Kabul kriteri:** Taze kurulumda isPresenting=true; markSeen() sonrası ve uygulama yeniden başlatılınca false; çıkış yapılıp tekrar girilince yine false (cihaz-bazlı korunur); profil güncelleme bayrağı etkilemez.

#### `OB-3` — Tur içerik modeli: 4 sayfa (Türkçe metin + ikon + renk tonu)  _(P0 · efor: S)_
- **iOS davranışı:** 4 sabit sayfa, her biri systemImage + tint + title + message; metinler Türkçe ve sabit.
- **iOS karşılığı/kaynak:** OnboardingPage.tour: [graduationcap.fill/accent, books.vertical.fill/accent, lock.fill/.orange, plus.circle.fill/.orange].
- **Android mevcut:** Yok.
- **Android teknoloji:** Compose ImageVector / Icons.Filled.* ya da VectorDrawable, string resources, colors.xml
- **Adımlar:**
  - data class OnboardingPage(id:Int, icon:ImageVector veya @DrawableRes, tint:Color, @StringRes title, @StringRes message) tanımla.
  - iOS sırasını birebir kur: [0] School/graduationcap, marka turuncusu; [1] AutoStories veya LibraryBooks/books.vertical, marka turuncusu; [2] Lock/lock.fill, turuncu; [3] AddCircle/plus.circle.fill, turuncu.
  - Türkçe metinleri strings.xml'e gir (İngilizce values-en YOK — Türkçe-tek): onboarding_p1_title/message ... onboarding_p4_title/message; iOS metinleriyle birebir aynı (ör. '…100 coin ile kalıcı olarak açılır.', 'gönderdiğin sınavlar onaylandığında coin'lerin hesabına yüklenir.').
  - Buton/aksiyon metinleri: onboarding_skip='Atla', onboarding_next='İleri', onboarding_start='Başla'.
  - Coin tutarı ve 'en eski iki sınav ücretsiz' ifadelerini Coin ekonomisi/ExamLock alanıyla senkron tut (UNLOCK_COST=100).
  - Marka turuncusunu colors.xml'de token olarak tanımla (brand_orange) ve buton için siyah metin (onPrimary=Black) ile kullan.
- **Gradle/bağımlılık:** material-icons-extended (ImageVector yolu seçilirse) veya özel VectorDrawable asset'leri
- **Bağımlılık:** OB-1, Coin ekonomisi alanı (100 coin ve ücretsiz-sınav sayısı metin tutarlılığı için)
- **Tuzaklar:** material-icons-extended büyük; tek kullanım için APK şişebilir — 4 özel vektör daha ince olabilir. SF Symbol'lerin birebir Material karşılığı yok; marka tutarlılığı için ikon seçimi onaylanmalı (openQuestion).
- **Kabul kriteri:** 4 sayfa doğru sıra/ikon/ton/metinle tanımlı; tüm metinler strings.xml'den geliyor; coin ifadeleri ekonomi alanıyla aynı.

#### `OB-4` — Onboarding ekranı (Compose): pager + nokta göstergesi + Atla + birincil buton + sayfa düzeni  _(P0 · efor: L)_
- **iOS davranışı:** VStack: skipBar(44) + TabView(.page indexDisplayMode:.always) + primaryButton. Atla son sayfa değilken; buton 'İleri'→'Başla'; İleri animasyonla ilerler; son sayfada Başla=finished; arka plan systemGroupedBackground; hero ScaledMetric 96/maks 140.
- **iOS karşılığı/kaynak:** OnboardingScreen/OnboardingPageView (SwiftUI); IconBadge; borderedProminent siyah metin.
- **Android mevcut:** Yok.
- **Android teknoloji:** Compose HorizontalPager, rememberPagerState, animateScrollToPage, Material3 Button/TextButton; alternatif ViewPager2 + TabLayoutMediator
- **Adımlar:**
  - @Composable OnboardingScreen(pages, source, onFinish): Scaffold/Column ile üç bölge — üst skip barı, orta HorizontalPager, alt birincil buton.
  - val pagerState = rememberPagerState(pageCount = { pages.size }); HorizontalPager(state = pagerState) { page -> OnboardingPageContent(pages[page]) }.
  - Nokta göstergesi: pagerState.currentPage'e göre elle Row{ repeat(pageCount) { dot } } çiz (foundation'da hazır indicator yok).
  - Üst bar: sağa hizalı 'Atla' TextButton, SADECE pagerState.currentPage < lastIndex iken görünür (isLastPage değilken); tıklama → finish(method="skipped").
  - Birincil buton: metin isLastPage ? 'Başla' : 'İleri'; Button(colors = buttonColors(containerColor = brandOrange, contentColor = Color.Black)); tıklama: isLastPage ? finish("finished") : coroutineScope.launch { pagerState.animateScrollToPage(current+1) }.
  - OnboardingPageContent: ortalanmış Column — hero ikon (Material tonu ile daireli badge, boyut dp clamp min(scaledSize,140.dp) — iOS ScaledMetric 96/maks 140 karşılığı), başlık (headlineLarge/bold, semantics heading), gövde (bodyLarge, onSurfaceVariant). Arka plan MaterialTheme surface/ 'grouped' eşdeğeri.
  - finish(method): OB-7 analytics onboarding_complete(method, source) çağır, sonra onFinish().
  - LaunchedEffect(Unit) ile açılışta OB-7 screen_view("Onboarding") gönder.
  - (Alternatif Views yolu): OnboardingFragment + ViewPager2 + FragmentStateAdapter(4 sayfa) + TabLayout/TabLayoutMediator (noktalar); Atla/İleri butonları onPageSelected ile yönetilir; markSeen ViewModel üzerinden.
- **Gradle/bağımlılık:** compose foundation (HorizontalPager/PagerState), material3; (Views alternatifi) androidx.viewpager2:viewpager2 + com.google.android.material:material
- **Bağımlılık:** OB-1, OB-3, OB-7 (analytics çağrıları)
- **Tuzaklar:** foundation'da resmi pager indicator yok → noktaları elle çiz. Buton kontrastı: Material3 varsayılan onPrimary beyaz olabilir, elle siyah ver. maxWidth 440dp benzeri genişlik sınırı tablet'te uygulanmalı. Dinamik yazı boyutu Compose'da otomatik; hero ikon clamp'i unutma.
- **Kabul kriteri:** 4 sayfa kaydırılabilir; nokta göstergesi aktif sayfayı yansıtır; Atla yalnızca son sayfa öncesinde görünür; buton metni son sayfada 'Başla', öncesinde 'İleri'; İleri animasyonla ilerletir; turuncu buton üzerinde siyah metin (kontrast ≥3:1); açılışta screen_view, bitişte doğru method ile onboarding_complete gönderilir.

#### `OB-5` — İlk açılış sunumu: giriş+profil kapısı sonrası Home üzerinde bir kez gösterim ve markSeen  _(P0 · efor: M)_
- **iOS davranışı:** RootView: signedIn→ProfileGate{archive}; archive=NavigationStack{HomeView}.task{prefetch}.fullScreenCover(isPresenting){OnboardingView(onFinish:markSeen)}. Tur giriş+profil sonrası Home üzerinde, cihaz başına bir kez.
- **iOS karşılığı/kaynak:** fullScreenCover isPresented: $onboarding.isPresenting; onFinish environment.onboarding.markSeen().
- **Android mevcut:** Yok; HomeActivity doğrudan açılıyor, profil/oturum kapısı yok, tur yok.
- **Android teknoloji:** Navigation-Compose / full-screen Dialog / OnboardingActivity, BackHandler, collectAsStateWithLifecycle
- **Adımlar:**
  - Kök/Home akışında (RootView karşılığı) oturum durumu signedIn VE profil tamamlanmış (ProfileGate karşılığı) olduğunda Home gösterildikten SONRA onboarding'i koşullu sun.
  - OnboardingPreferences.isPresenting akışını collectAsStateWithLifecycle ile topla; true ise onboarding'i Home ÜZERİNDE tam ekran göster (Navigation-Compose route 'onboarding' / tam ekran Dialog composable / veya ayrı OnboardingActivity-NoActionBar). Home altta yüklenmeye devam etsin (prefetch gibi).
  - onFinish = { scope.launch { onboardingPreferences.markSeen() } } → bayrak kalıcılaşır, akış false yayar, tur kapanır; cihaz başına yalnız bir kez.
  - source = "first_launch" (varsayılan).
  - Bayrak YÜKLENMEDEN turu gösterme (flash önleme): isPresenting Loadable 'loading' iken Home'u göster, tur gösterme.
  - Sistem geri tuşu: tur açıkken geri = Atla davranışı (BackHandler { finish("skipped") }) — kullanıcının markSeen'siz Home'a düşmesini engelle (openQuestion ile onaylanacak).
  - Mevcut AdMob banner'ın tur altında görünmemesi için reklam kaldırma alanıyla koordine ol (deps).
- **Gradle/bağımlılık:** navigation-compose (route yolu seçilirse) veya ayrı Activity (Views)
- **Bağımlılık:** OB-2, OB-4, Auth alanı (oturum durumu / signedIn), Profile alanı (profil tamamlama kapısı — tur bundan SONRA), Home alanı (sunum kabı/ana ekran), Reklam kaldırma alanı (banner'ın tur altında kalmaması)
- **Tuzaklar:** Tur Home'un ÜSTÜNDE olmalı (Home altta yüklenir). Asenkron bayrak → flash riski. Geri tuşu/predictive back markSeen'i atlayabilir. Oturum+profil kapısı henüz Android'de yok; bu alanlar tamamlanana kadar sunum entegrasyonu askıda.
- **Kabul kriteri:** Giriş+profil tamamlanmış taze cihazda Home açılır açılmaz tur bir kez görünür; Atla/Başla sonrası bir daha ASLA görünmez (uygulama yeniden başlatılsa da); çıkış/tekrar girişte görünmez; profil tamamlanmadan tur açılmaz; geri tuşu markSeen ile kapatır; tur altında reklam yok.

#### `OB-6` — Hakkında'dan yeniden oynatma: 'Tanıtım turunu tekrar izle' (source=replay, markSeen YOK)  _(P1 · efor: S)_
- **iOS davranışı:** AboutView: 'Tanıtım turunu tekrar izle' (play.circle) fullScreenCover ile OnboardingView(onFinish:{dismiss}, source:"replay") sunar; per-device 'seen' bayrağına DOKUNMAZ.
- **iOS karşılığı/kaynak:** @State isShowingTour; .fullScreenCover { OnboardingView(onFinish:{isShowingTour=false}, source:"replay") }.
- **Android mevcut:** Yok — Android'de Hakkında ekranı yok.
- **Android teknoloji:** Compose navigation/Dialog veya Activity yeniden kullanımı; aynı OnboardingScreen bileşeni source parametresiyle
- **Adımlar:**
  - Hakkında (About) ekranında (ayrı alan tarafından kuruluyor) bir satır ekle: ikon play_circle, metin 'Tanıtım turunu tekrar izle', testTag 'about.replayOnboarding'.
  - Tıklama → OnboardingScreen'i source="replay" ile tam ekran sun; onFinish = sadece kapat (DataStore markSeen ÇAĞIRMA).
  - Tekrar oynatma bayrağı değiştirmediğinden, ilk-açılış mantığını etkilemez (zaten görüldü işaretliyse öyle kalır).
  - finish(method) replay'de de analytics onboarding_complete(method, source="replay") gönderir (method skipped|finished).
- **Gradle/bağımlılık:** —
- **Bağımlılık:** OB-4, Home/Account alanı (Hakkında ekranının kendisi)
- **Tuzaklar:** onFinish'in markSeen çağırmadığından emin ol (ilk-açılış ile paylaşılan bileşen; davranış parametreyle ayrılmalı). Hakkında ekranı başka alanın işi — satır eklemek için onunla koordine (deps).
- **Kabul kriteri:** Hakkında'daki satıra basınca tur source=replay ile açılır; Atla/Başla ile kapanır ve DataStore 'seen' bayrağı DEĞİŞMEZ; onboarding_complete olayı source=replay ile gönderilir.

#### `OB-7` — Analytics: screen_view(Onboarding) + onboarding_complete(method, source)  _(P0 · efor: S)_
- **iOS davranışı:** onAppear → screen_view(name="Onboarding") (AnalyticsEventScreenView + screen_name). finish → onboarding_complete {method, source}.
- **iOS karşılığı/kaynak:** FirebaseAnalyticsTracker: firebaseName 'onboarding_complete'; params [method, source]; screenView→AnalyticsEventScreenView + AnalyticsParameterScreenName.
- **Android mevcut:** Yok — hiç FirebaseAnalytics kullanımı yok.
- **Android teknoloji:** FirebaseAnalytics KTX logEvent, FirebaseAnalytics.Event.SCREEN_VIEW, Param.SCREEN_NAME, bundleOf
- **Adımlar:**
  - Firebase Analytics örneği: Firebase.analytics (firebase-analytics-ktx) veya FirebaseAnalytics.getInstance(context).
  - Açılışta: analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundleOf(FirebaseAnalytics.Param.SCREEN_NAME to "Onboarding")) — OnboardingScreen LaunchedEffect içinde.
  - Bitişte: analytics.logEvent("onboarding_complete", bundleOf("method" to method, "source" to source)) — olay adı iOS ile BİREBİR 'onboarding_complete'.
  - method değerleri: 'skipped' (Atla) ve 'finished' (son sayfada Başla); source: 'first_launch' | 'replay'.
  - Olayları ince bir AnalyticsTracker soyutlaması arkasına koy (iOS AnalyticsTracking karşılığı) ki test edilebilsin ve diğer alanların olaylarıyla tutarlı olsun.
- **Gradle/bağımlılık:** com.google.firebase:firebase-analytics-ktx (BoM ile)
- **Bağımlılık:** OB-1 (firebase-analytics-ktx), Analytics foundation alanı (ortak tracker soyutlaması, olay adı sözlüğü)
- **Tuzaklar:** Olay adı 'onboarding_complete' (SONU 'd' YOK) — iOS ile aynı olmalı, aksi halde ortak Firebase projesinde iki ayrı metrik oluşur. Firebase param anahtar adları (screen_name, method, source) birebir korunmalı.
- **Kabul kriteri:** DebugView/logcat'te onboarding açılışında screen_view(screen_name=Onboarding), Atla'da onboarding_complete(method=skipped,...), son sayfa Başla'da method=finished, replay'de source=replay olayları görünür; adlar iOS ile aynı.

#### `OB-8` — Erişilebilirlik (TalkBack) + test etiketleri (iOS a11y kimlikleriyle parite)  _(P1 · efor: S)_
- **iOS davranışı:** accessibilityIdentifier: onboarding.pager/page.N/skip/next/start, about.replayOnboarding; başlıkta isHeader; sayfa combine; logo/hero accessibilityHidden.
- **iOS karşılığı/kaynak:** SwiftUI .accessibilityIdentifier / .accessibilityAddTraits(.isHeader) / .accessibilityElement(children:.combine).
- **Android mevcut:** Yok.
- **Android teknoloji:** Compose Modifier.semantics/testTag/heading/mergeDescendants; Views contentDescription/importantForAccessibility
- **Adımlar:**
  - Test etiketleri (Modifier.testTag) / Views resource-id'leri iOS accessibilityIdentifier'larıyla eşleştir: 'onboarding.pager', 'onboarding.page.0..3', 'onboarding.skip', 'onboarding.next', 'onboarding.start', 'about.replayOnboarding'.
  - Sayfa başlığına Modifier.semantics { heading() } ver (iOS .isHeader karşılığı).
  - Her sayfayı tek a11y düğümünde birleştir: Modifier.semantics(mergeDescendants = true) (iOS .accessibilityElement(children:.combine)).
  - Hero ikonu dekoratif işaretle (contentDescription = null) — iOS accessibilityHidden karşılığı.
  - Atla/İleri/Başla butonlarına anlamlı contentDescription/metin; TalkBack sırası üst-bar→içerik→buton olacak şekilde.
- **Gradle/bağımlılık:** —
- **Bağımlılık:** OB-4
- **Tuzaklar:** testTag varsayılan olarak erişilebilirlik ağacına çıkmaz; UI testleri için useUnmergedTree gerekebilir. Birleştirilmiş düğüm TalkBack'te uzun okuma yapabilir — başlık+gövde birleşimi iOS ile aynı.
- **Kabul kriteri:** TalkBack her sayfada başlık+mesajı okur, hero ikonu atlar; tüm test etiketleri iOS kimlikleriyle birebir; butonlar erişilebilir.

#### `OB-9` — Android'e özgü kenar durumları: tam ekran/insets, karanlık mod, dinamik yazı, geri tuşu  _(P1 · efor: S)_
- **iOS davranışı:** fullScreenCover güvenli alanları otomatik yönetir; dynamic type ScaledMetric; light/dark otomatik; geri yok.
- **iOS karşılığı/kaynak:** SwiftUI safe area + ScaledMetric + systemGroupedBackground; TabView durumu @State.
- **Android mevcut:** Yok; uygulama eski tema, insets/karanlık mod/compose durumu yönetimi yok.
- **Android teknoloji:** WindowInsets/systemBarsPadding, enableEdgeToEdge, BackHandler, MaterialTheme dark color scheme, rememberSaveable/PagerState
- **Adımlar:**
  - Tam ekran sunum için edge-to-edge/WindowInsets ele al (status/navigation bar, çentik); içerik güvenli alanlarda (Scaffold/innerPadding veya systemBarsPadding).
  - Karanlık mod: renkleri MaterialTheme token'larından al (hard-coded beyaz/siyah yerine), marka turuncusu + siyah buton metni her iki temada kontrastı korusun; iOS systemGroupedBackground'a eşdeğer yüzey.
  - Dinamik yazı boyutu (büyük font): metinler sp ile ölçeklensin; hero ikon boyutu min(ölçekli, 140.dp) ile sınırlansın (iOS ScaledMetric parite).
  - Sistem geri tuşu (predictive back dahil): BackHandler ile Atla (method=skipped) → markSeen; kullanıcının markSeen'siz turdan düşmesini önle.
  - Tablet/geniş ekran: içerik genişliğini ~440dp ile sınırla ve ortala (iOS maxWidth 440).
  - Yapılandırma değişikliği (rotasyon): pager mevcut sayfası ve durum korunsun (rememberPagerState / ViewModel).
- **Gradle/bağımlılık:** androidx.core:core (WindowInsets) — zaten AndroidX ile gelir
- **Bağımlılık:** OB-4
- **Tuzaklar:** Edge-to-edge targetSdk 35'te varsayılan; insets unutulursa buton navigasyon çubuğunun altında kalır. Geri tuşu en büyük parite tuzağı — iOS'ta yok. Rotasyonda pager sıfırlanmamalı.
- **Kabul kriteri:** Tur çentikli/edge-to-edge cihazda kırpılmadan görünür; karanlık modda okunur ve kontrast korunur; büyük yazı ayarında taşma yok, hero clamp'lenir; geri tuşu turu markSeen ile kapatır; rotasyonda sayfa korunur.

#### `OB-10` — Enstrümantasyon UI testleri (Compose/Espresso) — iOS onboarding testleriyle parite  _(P2 · efor: M)_
- **iOS davranışı:** iOS'ta accessibilityIdentifier'lar UI testleri içindir; markSeen/replay/skip davranışları test edilebilir biçimde ayrılmış.
- **iOS karşılığı/kaynak:** OnboardingStore.seed, accessibilityIdentifier tabanlı XCUITest noktaları.
- **Android mevcut:** Yok; yalnızca iskelet ExampleInstrumentedTest/ExampleUnitTest var.
- **Android teknoloji:** Compose UI Test (onNodeWithTag, performClick, assertIsDisplayed), fake DataStore/Analytics, JUnit4
- **Adımlar:**
  - Compose UI test (createComposeRule) ya da Espresso ile senaryolar yaz: pager 4 sayfa; İleri ile ilerleme; son sayfada buton 'Başla'; Atla yalnızca son sayfa öncesinde görünür.
  - markSeen davranışı: tur bitince/atlanınca OnboardingPreferences.seen=true; yeniden sunumda tur gösterilmez (fake/in-memory DataStore veya seed ile).
  - Replay: source=replay ile açılıp kapanınca 'seen' bayrağının DEĞİŞMEDİĞİ doğrulanır.
  - Analytics: tracker soyutlamasını fake ile enjekte edip screen_view ve onboarding_complete(method/source) çağrılarının doğru parametrelerle gittiğini doğrula.
  - Test etiketleriyle (onboarding.skip/next/start/pager/page.N) öğeleri bul — iOS UI testleriyle aynı kimlikler.
- **Gradle/bağımlılık:** androidx.compose.ui:ui-test-junit4 + ui-test-manifest (debugImplementation); (Views) androidx.test.espresso:espresso-core
- **Bağımlılık:** OB-2, OB-4, OB-7
- **Tuzaklar:** DataStore gerçek dosya tutar — testte in-memory/temp dosya veya fake repository enjekte et. testTag unmergedTree gerektirebilir. Animasyonlu pager testte idling gerektirebilir.
- **Kabul kriteri:** CI'de testler yeşil: pager/skip-görünürlüğü/buton-metni/markSeen/replay-no-markSeen/analytics-parametreleri doğrulanır.


---

## Analytics (genişletilmiş event + user segment)  ·  `analytics`

**Özet:** iOS master (v3.1.0) olgun bir analitik katmanına sahip: AnalyticsTracking protokolü + FirebaseAnalyticsTracker ile 23 domain event, elle screen_view, 5 user property (university_id/faculty_id/department_id/gender/is_admin) ve setUserId. Tüm isimler düşük-kardinaliteli, stabil snake_case, PII yok. Android'de ŞU AN HİÇBİR analitik yok (ne FirebaseAnalytics kullanımı, ne firebase-analytics bağımlılığı - yalnız eski firebase-core:16.0.8), proje AndroidX öncesi, AGP 3.4.2, targetSdk 28, AdMob mevcut (parite için başka alanda kaldırılacak). Bu alan iOS'un AnalyticsEvent/AnalyticsUser davranışını birebir yeniden kurar. Kritik Android tuzağı: iOS Bool parametreleri (can_afford/already_unlocked/resend/zero_results - NSNumber 1/0) Firebase Android Bundle'da desteklenmez, long 1/0'a maplenmeli. iOS'ta IDFA/ATT olmadığı için Android'de AD_ID toplama kapatılmalı ve Play Data Safety beyanı yazılmalı. Tüm event/param/user-property isim ve stabil değerleri (reason/stage/tier/level enumları) iOS ile bire bir eşlenmeli ki ortak Firebase projesinde (vifi-831a8) iOS ve Android raporları aynı boyutlarda birleşsin.

**Android mevcut durum:** Android tarafında genişletilmiş analitik YOK. app/build.gradle yalnız 'com.google.firebase:firebase-core:16.0.8' (2018 eski birleşik SDK) içerir; 'com.google.firebase:firebase-analytics' yok, hiçbir kaynakta FirebaseAnalytics/logEvent çağrısı yok (grep boş). Proje AndroidX öncesi (com.android.support, gradle.properties'te android.useAndroidX yok), AGP 3.4.2, google-services 4.2.0, jcenter (ölü), compileSdk/targetSdk 28, minSdk 19, versionCode 16 / versionName 2.5. Mimari: salt Java, Activity/Fragment, DI yok. AdMob (play-services-ads:17.2.0, banner/interstitial/rewarded) mevcut ve parite için kaldırılacak; AdMob kaldırılınca AD_ID zaten düşer ama analytics-measurement yine AD_ID talep edebilir, açıkça kapatılmalı.

**Dokunulan backend sözleşmesi:** Analytics için ayrı bir backend sözleşmesi YOK: Firestore kuralları / RTDB / Cloud Functions'a dokunmaz. Yalnız GA4 şeması (event adları, param adları, user property adları ve stabil değerleri) iOS ile kanonik eşlenmeli; çünkü iOS ve Android AYNI Firebase projesi (vifi-831a8, project_number 441043948320) altında rapor üretir, isimler ayrışırsa cross-platform segmentasyon bozulur. Analitiğe beslenen değerler başka sözleşmelerden gelir: is_admin = auth admin custom claim; gender = RTDB users/uid/profile.gender (female|male); university_id/faculty_id/department_id = RTDB profile *Id slug alanları ([a-z0-9-]); setUserId = Firebase Auth uid.

**Android'e özgü riskler:**
- iOS Bool parametreleri (can_afford, already_unlocked, resend, zero_results) Firebase Android Bundle'da desteklenmez: Bundle yalnız String/long/double kabul eder. iOS Bool->NSNumber 1/0 bridge olduğundan Android'de de long 1/0 yazılmalı (putLong), string 'true'/'false' DEĞİL; aksi halde GA4'te iOS ve Android ayrışır.
- targetSdk 33+ hedeflenince play-services-measurement otomatik com.google.android.gms.permission.AD_ID izni manifest merge ile eklenir ve reklam kimliği toplanır. iOS'ta IDFA yok; parite için AD_ID izni manifestte kaldırılmalı ve adid toplama kapatılmalı, yoksa Play Data Safety beyanı ile çelişir.
- Android Firebase Analytics varsayılan olarak otomatik screen_view toplar (ENABLE_AUTO_SCREEN_REPORTING, Activity sınıf adıyla). iOS elle temiz adlar (Home/Browse...) gönderir. Çift sayımı önlemek için otomatik ekran raporlaması manifest meta-data ile kapatılmalı ve elle screen_view loglanmalı.
- GA4 limitleri: event adı <=40, param adı <=40, string param değeri <=100, user property adı <=24, değeri <=36, event başına <=25 param, <=25 user property. iOS zaten 36'ya kırpar (user property limitiyle uyumlu). firebase_/google_/ga_ prefiksleri rezerve, kullanılamaz.
- R8/ProGuard: firebase-analytics için özel keep kuralı gerekmez ama AnalyticsEvent sealed sınıfı reflection ile kullanılmadığından sorun yok; yine de release build'de logEvent'in çalıştığı DebugView ile doğrulanmalı.
- PII sızıntısı riski: param değerlerine ASLA serbest metin (üniversite/ders/dosya adı, telefon, isim) konmamalı. iOS yalnız kind/level/tier/reason/slug gönderir. university_id vb. slug olmalı, görünen ad değil.

### İş Kalemleri

#### `AN-01` — firebase-analytics bağımlılığını ekle ve Firebase BoM'a geç  _(P0 · efor: S)_
- **iOS davranışı:** iOS SPM ile FirebaseAnalytics'i çeker; FirebaseAnalyticsTracker import FirebaseAnalytics.
- **iOS karşılığı/kaynak:** import FirebaseAnalytics; Analytics.logEvent / setUserID / setUserProperty.
- **Android mevcut:** Yalnız eski 'com.google.firebase:firebase-core:16.0.8' var; firebase-analytics yok, BoM yok, sürümler elle pinli (database/storage/auth/messaging 16-17.x, 2018). google-services 4.2.0, jcenter aktif.
- **Android teknoloji:** com.google.firebase:firebase-bom, com.google.firebase:firebase-analytics, FirebaseAnalytics.getInstance(Context), google-services plugin.
- **Adımlar:**
  - app/build.gradle'a Firebase BoM ekle (platform('com.google.firebase:firebase-bom:33.5.1')) ve 'com.google.firebase:firebase-analytics' bağımlılığını ekle (sürümü BoM yönetsin).
  - Eski 'com.google.firebase:firebase-core:16.0.8' satırını kaldır (artık firebase-analytics'e katlandı).
  - Mümkünse diğer Firebase bağımlılıklarını da (database/storage/auth/messaging) BoM'a bağla; en azından analytics BoM'dan gelsin.
  - Kotlin'e geçiliyorsa firebase-analytics'in KTX API'si (com.google.firebase:firebase-analytics-ktx, Firebase.analytics, logEvent{param(...)}) kullanılabilir; salt Java kalınırsa FirebaseAnalytics.getInstance(context) + Bundle.
  - google-services plugin ve google-services.json'ın app paketiyle (com.salticusteam.vifi) ve proje vifi-831a8 ile eşleştiğini doğrula.
- **Gradle/bağımlılık:** platform('com.google.firebase:firebase-bom:33.5.1'); implementation 'com.google.firebase:firebase-analytics' (KTX istenirse firebase-analytics-ktx). firebase-core kaldırılır. NOT: BoM 33.x AndroidX ve en az compileSdk 34, AGP 8.x gerektirir -> bu todo foundation migration'a bağımlı (AndroidX + AGP + jcenter kaldırma).
- **Bağımlılık:** FOUNDATION: AndroidX + AGP 8.x + compileSdk 34 + jcenter kaldırma migrasyonu (BoM 33.x bunu zorunlu kılar)
- **Tuzaklar:** BoM 33.x min AGP/SDK yükseltmesi ister; foundation migration tamamlanmadan analytics BoM eklenemez. Geçici olarak AndroidX-öncesi bir ara çözüm gerekirse eski uyumlu firebase-analytics:17.x pinlenebilir ama kalıcı hedef BoM'dur.
- **Kabul kriteri:** Proje firebase-analytics ile derlenir; uygulama açılışında GA4 DebugView'de otomatik olmasa bile FirebaseAnalytics örneği alınabilir; firebase-core referansı kalmaz.

#### `AN-02` — AnalyticsTracker soyutlaması (interface + Firebase implementasyonu)  _(P0 · efor: M)_
- **iOS davranışı:** AnalyticsTracking protokolü track(event) ve setUser(user?) sunar; FirebaseAnalyticsTracker ikisini Analytics API'sine çevirir.
- **iOS karşılığı/kaynak:** ServiceProtocols.swift: protocol AnalyticsTracking; FirebaseAnalyticsTracker.swift: Analytics.logEvent(event.firebaseName, parameters: event.firebaseParameters); setUser -> setUserID + propertyNames döngüsü.
- **Android mevcut:** Hiç analitik soyutlaması yok; DI yok. Activity'ler doğrudan Firebase'i çağırıyor (ör. auth/db).
- **Android teknoloji:** FirebaseAnalytics.getInstance, FirebaseAnalytics.setUserId(String), FirebaseAnalytics.setUserProperty(String,String), FirebaseAnalytics.logEvent(String, Bundle).
- **Adımlar:**
  - iOS AnalyticsTracking protokolünü birebir karşılayan bir arayüz tanımla: interface AnalyticsTracker { void track(AnalyticsEvent event); void setUser(AnalyticsUser user); } (setUser(null) kimliği temizler).
  - FirebaseAnalyticsTracker implementasyonu yaz: track -> firebaseAnalytics.logEvent(event.getFirebaseName(), event.getFirebaseParameters()); setUser -> firebaseAnalytics.setUserId(user?.id) + her user property için setUserProperty(name, value) (value yoksa null ile temizle).
  - Test/no-op için NoOpAnalyticsTracker (ör. debug veya unit test) ekle; iOS'un MockServices karşılığı.
  - Tracker'ı uygulama ömrü boyunca tek örnek (singleton) tut: Application sınıfında ya da DI graph'ta.
  - logEvent çağrılarının main thread'i bloklamadığını doğrula (Firebase Analytics zaten asenkron; ek thread gerekmez).
- **Gradle/bağımlılık:** Ek bağımlılık yok (AN-01 yeterli). DI tercih edilirse AN-10.
- **Bağımlılık:** AN-01
- **Tuzaklar:** setUser(null) çağrıldığında setUserId(null) + her property setUserProperty(name, null) yapılmalı; yoksa önceki kullanıcının segmenti bir sonrakine taşar (iOS bunu açıkça yapıyor).
- **Kabul kriteri:** AnalyticsTracker arayüzü üzerinden tek bir track/setUser girişi vardır; call-site'lar Firebase'i doğrudan çağırmaz; NoOp implementasyonu testte event sızdırmaz.

#### `AN-03` — AnalyticsEvent domain modeli (23 event) - stabil isim ve parametre eşlemesi  _(P0 · efor: L)_
- **iOS davranışı:** AnalyticsEvent enum'u 23 case; firebaseName ve firebaseParameters hesaplanmış özellikleri event'i isim+Bundle'a çevirir. ArchiveLevel.analyticsName stabil İngilizce adlar üretir.
- **iOS karşılığı/kaynak:** FirebaseAnalyticsTracker.swift private extension AnalyticsEvent.firebaseName / firebaseParameters; private extension ArchiveLevel.analyticsName.
- **Android mevcut:** Event modeli yok.
- **Android teknoloji:** sealed class + when (Kotlin) ya da sınıf hiyerarşisi (Java); Bundle (putString/putLong), FirebaseAnalytics.Event.SCREEN_VIEW, FirebaseAnalytics.Param.SCREEN_NAME.
- **Adımlar:**
  - Kotlin'de sealed class AnalyticsEvent (ya da Java'da tip-güvenli sınıf hiyerarşisi/enum+payload) tanımla; iOS AnalyticsEvent enum'unun 23 case'ini birebir kapsa.
  - Her event için firebaseName döndüren eşleme yaz (iOS firebaseName switch'iyle AYNI string'ler): screen_view (FirebaseAnalytics.Event.SCREEN_VIEW), browse_level, exam_open, recent_exam_open, unlock_sheet_shown, earn_coins_tapped, exam_unlock, exam_unlock_failed, exam_submit, exam_submit_failed, login_code_requested, login_success, login_failed, onboarding_complete, profile_completed, profile_updated, search_used, sign_out, account_deleted, update_prompt_shown, update_action, submission_approved, submission_rejected.
  - Her event için Bundle üret (iOS firebaseParameters ile AYNI anahtar ve değerler): screen_view{screen_name} (FirebaseAnalytics.Param.SCREEN_NAME), browse_level{level}, exam_open{kind}, unlock_sheet_shown{can_afford:long 0/1}, exam_unlock{exam_kind, coins_spent:long, already_unlocked:long 0/1}, exam_submit{exam_kind, file_count:long, estimated_tier? opsiyonel}, exam_unlock_failed{reason}, exam_submit_failed{reason}, login_code_requested{resend:long 0/1}, login_failed{reason, step}, onboarding_complete{method, source}, search_used{level, zero_results:long 0/1}, update_prompt_shown{type:'required'|'optional'}, update_action{action}, submission_approved{tier}. Parametresiz event'lerde null/boş Bundle.
  - Stabil enum değer eşlemelerini TEK yerde sabitle: ArchiveLevel->level (university|faculty|department|lesson|exam), ExamKind->kind (JPG|PDF), SubmissionTier->tier (newUniversity|newFaculty|newDepartment|newCourse|newExam|duplicate). Bu değerler lokalize edilmeyen, dilden bağımsız sabitlerdir.
  - Boolean parametreleri long 1/0 olarak yaz (iOS NSNumber 1/0 paritesi).
- **Gradle/bağımlılık:** Ek yok.
- **Bağımlılık:** AN-02
- **Tuzaklar:** estimated_tier yalnız tier null değilken eklenir (iOS: if let tier). coins_spent her zaman 100 (ExamLock.unlockCost) - sabiti UNLOCK_COST ile paylaş. Event/param adları 40 karakteri aşmamalı (hepsi uyuyor).
- **Kabul kriteri:** 23 event'in her biri iOS ile AYNI firebaseName ve AYNI parametre anahtar/değerlerini üretir; boolean'lar long 1/0; enum değerleri stabil İngilizce sabitler; birim testle (iOS testlerinin karşılığı) her event'in adı ve Bundle içeriği doğrulanır.

#### `AN-04` — AnalyticsUser + setUser: user property segmentleri ve setUserId  _(P0 · efor: M)_
- **iOS davranışı:** AnalyticsUser id + 4 opsiyonel slug + isAdmin tutar; properties map'i her isme değer (ya da yoklukla temizleme) verir, 36'ya kırpar. setUser(nil) tüm identity+property'yi temizler.
- **iOS karşılığı/kaynak:** ServiceProtocols.swift AnalyticsUser struct (propertyNames, properties, clamped); FirebaseAnalyticsTracker.setUser döngüsü.
- **Android mevcut:** User property / setUserId kullanımı yok.
- **Android teknoloji:** FirebaseAnalytics.setUserId, FirebaseAnalytics.setUserProperty (null ile temizleme), String.take(36)/substring.
- **Adımlar:**
  - AnalyticsUser veri sınıfı: id (uid), universityId?, facultyId?, departmentId?, gender? (female|male), isAdmin:Boolean. iOS init(id, profile:UserProfile?, isAdmin) paritesiyle profilden türet.
  - propertyNames sabitini iOS ile AYNI sırayla tanımla: ['university_id','faculty_id','department_id','gender','is_admin'].
  - properties() üret: is_admin -> 'true'/'false'; university_id/faculty_id/department_id/gender -> slug değeri; her değer 36 karaktere kırpılır (iOS clamped). Değer yoksa map'te bulunmaz ve set sırasında null ile temizlenir.
  - FirebaseAnalyticsTracker.setUser(user): setUserId(user?.id); propertyNames üzerinde dön, setUserProperty(name, user?.properties[name]) - değer yoksa null geçerek temizle.
  - gender RTDB profile.gender (female|male) enum raw değeriyle; *_id değerleri RTDB profile.*Id slug'larıyla ([a-z0-9-]) birebir aynı olmalı.
- **Gradle/bağımlılık:** Ek yok.
- **Bağımlılık:** AN-02, PROFILE: RTDB profile modeli ve *Id slug alanları, AUTH: admin custom claim okuma (isCurrentUserAdmin)
- **Tuzaklar:** is_admin asla null bırakılmamalı; iOS 'true'/'false' string gönderir. gender yalnız female|male; başka değer sızarsa GA4 segmenti kirlenir. 36 karakter sınırı user property'de ZORUNLU (GA4 reddeder).
- **Kabul kriteri:** Oturum açılınca setUserId(uid) ve 5 property set edilir; profil yokken *_id/gender null ile temizlenir, is_admin 'false' yazılır; sign-out'ta tüm property null olur; değerler 36 karaktere kırpılır; GA4 DebugView'de user property'ler görünür.

#### `AN-05` — Kimlik yaşam döngüsü: oturum/profil değişiminde identify, çıkışta temizle  _(P0 · efor: M)_
- **iOS davranışı:** RootView: user.id değişince isAdmin alınır ve identifyUser(); profile.state değişince tekrar identify. session.state==signedOut olunca setUser(nil) + diğer store reset'leri.
- **iOS karşılığı/kaynak:** App/RootView.swift satır 54 (setUser(nil)) ve 235 (identifyUser).
- **Android mevcut:** Kimlik yaşam döngüsü yok.
- **Android teknoloji:** FirebaseAuth.AuthStateListener ya da ViewModel/StateFlow, getIdTokenResult (admin claim), analyticsTracker.setUser.
- **Adımlar:**
  - Oturum açık kullanıcı belirlendiğinde (uid hazır) identifyUser() çağır: isAdmin = auth admin claim; setUser(AnalyticsUser(uid, profile, isAdmin)).
  - Profil yüklendiğinde ve profil her değiştiğinde identifyUser()'i tekrar çağırarak segmentleri zenginleştir (iOS .task(id:user.id) + onChange(profile.state)).
  - Sign-out gözlemlendiğinde setUser(null) çağır; ayrıca iOS RootView davranışı: önceki kullanıcının recents/wallet/unlocks/profile durumunu sıfırla (bu temizlikler ilgili alanların sorumluluğu, analitik yalnız setUser(null)).
  - Hesap silmede account_deleted event'i + setUser(null) (identity temizliği).
  - Uygulama mimarisi Compose/ViewModel ise bu akış bir AppState/SessionObserver'da; Activity tabanlı kalınırsa Application düzeyinde bir auth state listener'da toplanmalı.
- **Gradle/bağımlılık:** Ek yok.
- **Bağımlılık:** AN-04, AUTH: oturum durumu / admin claim, PROFILE: profil yükleme durumu
- **Tuzaklar:** admin claim async; identify profil+claim gelmeden erken çağrılırsa is_admin yanlış olabilir - iOS claim'i await edip sonra identify ediyor, Android'de de claim geldikten sonra set et.
- **Kabul kriteri:** Giriş sonrası user property'ler set, profil yüklenince *_id/gender dolu; başka kullanıcıya geçiş/çıkışta önceki segment taşınmaz (DebugView ile doğrulanır).

#### `AN-06` — Elle screen_view + otomatik ekran raporlamasını kapat (14 ekran)  _(P1 · efor: M)_
- **iOS davranışı:** Her View .onAppear'da track(.screenView(name: '...')); screenView {AnalyticsParameterScreenName: name}.
- **iOS karşılığı/kaynak:** HomeView(219/224/247), LoginView(13), AddExamView(24), EditProfileView(22), ProfileCompletionView(20), ExamImagesView(59), PDFExamView(67), FullScreenPagerView(53), BrowseView(243), InboxView(21), OnboardingView(95), ModerationView(10).
- **Android mevcut:** Firebase Analytics eklenince otomatik screen_view (Activity sınıf adıyla) toplanır; iOS temiz adlar gönderir.
- **Android teknoloji:** google_analytics_automatic_screen_reporting_enabled meta-data, FirebaseAnalytics.Event.SCREEN_VIEW, FirebaseAnalytics.Param.SCREEN_NAME.
- **Adımlar:**
  - AndroidManifest.xml application altına meta-data ekle: name='google_analytics_automatic_screen_reporting_enabled' value='false' - böylece çift sayım olmaz.
  - iOS'un .onAppear { track(.screenView(name:)) } karşılığını her ekran girişinde elle logla: screen_view {screen_name}. Activity ise onResume, Compose ise LaunchedEffect/Navigation destination listener.
  - Ekran adlarını iOS ile AYNI stabil string'lerle ver (14): Home, About, Account, Login, AddExam, EditProfile, ProfileCompletion, ExamImages, ExamPDF, ExamViewer, Browse, Inbox, Onboarding, Moderation.
  - screen_name param anahtarı FirebaseAnalytics.Param.SCREEN_NAME ('screen_name'); event adı FirebaseAnalytics.Event.SCREEN_VIEW ('screen_view').
- **Gradle/bağımlılık:** Ek yok.
- **Bağımlılık:** AN-02, AN-03
- **Tuzaklar:** Otomatik raporlama kapatılmazsa hem otomatik (MainActivity gibi) hem elle screen_view toplanır - raporda çift ekran. iOS ExamImages/ExamPDF/ExamViewer üç ayrı ekran - Android karşılıklarında aynı ayrımı koru.
- **Kabul kriteri:** Her ekranda tek bir screen_view (Activity sınıf adıyla değil, iOS'la aynı okunur adla) üretilir; otomatik screen_view yoktur; DebugView'de 14 ekran adı görünür.

#### `AN-07` — 23 event'i tüm call-site'larda tetikle (coin, submit, login, onboarding, profile, update, moderation, browse/search)  _(P1 · efor: L)_
- **iOS davranışı:** track call-site'ları: RootView(update), HomeView(recent/screen), LoginViewModel(code/success/failed), AddExamViewModel(submit/fail), EditProfile/ProfileCompletion, ExamDocumentViewModel(open), BrowseView(earn/sheet/unlock/search/browse), AccountView(delete/signout), OnboardingView(complete), ModerationDetailView(approved/rejected).
- **iOS karşılığı/kaynak:** grep sonucu 30+ track() call-site (RootView, HomeView, LoginViewModel, AddExamViewModel, BrowseView, AccountView, OnboardingView, ModerationDetailView, Exam*View, Profile*View).
- **Android mevcut:** Hiçbir event tetiklenmiyor.
- **Android teknoloji:** analyticsTracker.track(...) ViewModel/Activity call-site'ları; coins_spent için UNLOCK_COST=100 sabiti.
- **Adımlar:**
  - Coin: unlock sheet açılınca unlock_sheet_shown{can_afford}; coin kazan tıklanınca earn_coins_tapped; unlock başarılı exam_unlock{exam_kind, coins_spent=100, already_unlocked}; unlock hata exam_unlock_failed{reason}.
  - Arz: gönderim başarılı exam_submit{exam_kind, file_count, estimated_tier?}; hata exam_submit_failed{reason=unlock/submit hata eşlemesi}.
  - Giriş: kod istenince login_code_requested{resend}; başarı login_success; hata login_failed{reason, step=send_code|verify}.
  - Aktivasyon: onboarding bitince onboarding_complete{method=finished|skipped, source=first_launch}; profil ilk tamamlanınca profile_completed; düzenlenince profile_updated; arama yapılınca search_used{level, zero_results}.
  - Yaşam döngüsü/op: çıkış sign_out; hesap silme account_deleted; güncelleme sorulunca update_prompt_shown{type}; güncelleme aksiyonu update_action{action=update|skip|later}; moderasyon onay submission_approved{tier}; red submission_rejected.
  - exam_open{kind} sınav açılınca; recent_exam_open son sınav açılınca; browse_level{level} bir seviye listelenince.
  - Hata reason değerlerini iOS analyticsReason sabitleriyle eşle (AN-09).
- **Gradle/bağımlılık:** Ek yok.
- **Bağımlılık:** AN-03, AN-09, COIN: unlock akışı, AUTH: login akışı, ADDEXAM: gönderim akışı, UPDATE: force-update prompt, ONBOARDING: tur, PROFILE: tamamlama/düzenleme, MODERATION: admin onay/red, BROWSE: arama/gezinme
- **Tuzaklar:** Event'ler ilgili feature kodu yazıldıkça eklenir; bu todo o alanlara bağımlı. iOS'ta exam_submit başarıdan SONRA, exam_submit_failed CancellationError hariç atılır - iptal durumunda event atma. update_action iki ekrandan (update/skip/later) gelir.
- **Kabul kriteri:** Her kullanıcı akışında iOS'la aynı event aynı parametrelerle atılır; 23 event'in tamamı en az bir call-site'tan tetiklenir; DebugView'de doğrulanır; parametresiz event'ler parametresiz atılır.

#### `AN-08` — AD_ID toplamayı kapat (IDFA/ATT paritesi) ve reklam sinyallerini devre dışı bırak  _(P0 · efor: S)_
- **iOS davranışı:** iOS reklam/IDFA kullanmaz, ATT istemez; Analytics IDFA'sız çalışır.
- **iOS karşılığı/kaynak:** Projede IDFA/ATT kullanımı yok (reklam yok).
- **Android mevcut:** AdMob (play-services-ads) ve firebase-measurement AD_ID toplayabilir; iOS'ta IDFA yok.
- **Android teknoloji:** tools:node='remove' manifest merge, google_analytics_adid_collection_enabled meta-data, FirebaseAnalytics.setAnalyticsCollectionEnabled.
- **Adımlar:**
  - AndroidManifest.xml'e AD_ID iznini kaldıran merge kuralı ekle: uses-permission name='com.google.android.gms.permission.AD_ID' tools:node='remove' (targetSdk 33+ otomatik eklenmesini engeller).
  - AndroidManifest.xml application altına meta-data: name='google_analytics_adid_collection_enabled' value='false' (reklam kimliği toplama kapalı).
  - Analytics toplamanın açık kaldığını doğrula: setAnalyticsCollectionEnabled(true) (yalnız adid kapalı).
  - AdMob kaldırma (başka alan) tamamlanınca play-services-ads kaynaklı AD_ID talebinin de düştüğünü doğrula.
  - İstenirse google_analytics_default_allow_ad_personalization_signals=false meta-data ile reklam kişiselleştirme sinyalini de kapat.
- **Gradle/bağımlılık:** Ek bağımlılık yok; manifest meta-data + izin merge kuralı.
- **Bağımlılık:** AN-01, AD-REMOVAL: AdMob kaldırma (play-services-ads)
- **Tuzaklar:** AD_ID izni kaldırılmazsa Play Data Safety'de 'reklam kimliği topluyor' beyan etmek gerekir - iOS paritesini bozar. meta-data adını birebir doğru yaz (google_analytics_adid_collection_enabled).
- **Kabul kriteri:** APK'da AD_ID izni YOK (merged manifest kontrolü); DebugView/GA4'te reklam kimliği toplanmıyor; analytics event'ler yine akıyor; Play Data Safety ile tutarlı.

#### `AN-09` — Stabil reason/stage/tier/level sabitlerini iOS ile birebir eşle  _(P1 · efor: M)_
- **iOS davranışı:** AuthError/ExamSubmissionError/ExamUnlockError .analyticsReason stabil düşük-kardinaliteli string döndürür (asla lokalize mesaj); LoginFailureStage raw send_code/verify.
- **iOS karşılığı/kaynak:** AuthServicing.swift:112, LoginDiagnostics.swift:5, ExamSubmissionServicing.swift:43, ExamUnlockServicing.swift:43, SubmissionTier.swift, ArchiveItem.swift ExamKind.
- **Android mevcut:** Hata/enum eşleme sabitleri yok.
- **Android teknoloji:** enum/sabit sınıf; FirebaseAuthException.getErrorCode / PhoneAuthProvider hata eşlemesi; FirebaseFunctionsException kodları.
- **Adımlar:**
  - AuthError.analyticsReason paritesi: invalid_phone_number, invalid_code, code_expired, too_many_requests, sms_temporarily_unavailable, offline, verification_cancelled, app_verification_failed, requires_recent_login, not_signed_in, account_disabled, unknown.
  - LoginFailureStage paritesi: send_code, verify.
  - ExamSubmissionError.analyticsReason: not_signed_in, invalid_submission, permission_denied, offline, unknown.
  - ExamUnlockError.analyticsReason: insufficient_coins, not_found, not_signed_in, offline, unknown.
  - SubmissionTier: newUniversity, newFaculty, newDepartment, newCourse, newExam, duplicate.
  - ArchiveLevel: university, faculty, department, lesson, exam. ExamKind: JPG, PDF.
  - Bu değerleri tek bir sabit kaynakta tut; Firebase PhoneAuthProvider hata kodlarını bu stabil reason'lara çevir (ör. code 39 / SMS anti-abuse -> sms_temporarily_unavailable; FirebaseAuthInvalidCredentialsException -> invalid_code; vs.). Bilinmeyen -> unknown.
- **Gradle/bağımlılık:** Ek yok.
- **Bağımlılık:** AN-03, AUTH: hata tipleri, ADDEXAM: hata tipleri, COIN: unlock hata tipleri
- **Tuzaklar:** Firebase Android hata kodları iOS'tan farklı isimli; eşlemeyi elle yapmalı. 'unknown' fallback'i her zaman bulunmalı. Error code 39 (SMS anti-abuse) -> sms_temporarily_unavailable paritesi login diagnostics alanıyla paylaşılır.
- **Kabul kriteri:** login_failed/exam_submit_failed/exam_unlock_failed/submission_approved/browse_level/exam_open event parametreleri yalnız bu stabil sabitleri içerir; GA4'te iOS ve Android aynı reason/tier/level değerlerinde birleşir.

#### `AN-10` — AnalyticsTracker'ı DI ile bağla (tek örnek, test edilebilir)  _(P2 · efor: M)_
- **iOS davranışı:** AppEnvironment analytics servisini tutar; View'lar environment.analytics üzerinden erişir.
- **iOS karşılığı/kaynak:** App/AppEnvironment.swift, RootView environment.analytics.
- **Android mevcut:** DI yok; bağımlılıklar elle. iOS AppEnvironment üzerinden enjekte ediyor.
- **Android teknoloji:** Hilt (@Singleton, @Module, @Inject) ya da manuel AppContainer/ServiceLocator.
- **Adımlar:**
  - Hilt tercih edilirse: @Module ile FirebaseAnalytics ve AnalyticsTracker sağla (@Singleton); @HiltAndroidApp Application; call-site'lara @Inject.
  - Hilt istenmezse manuel DI: Application'da bir ServiceLocator/AppContainer içinde tek AnalyticsTracker örneği (iOS AppEnvironment paritesi).
  - Test için NoOpAnalyticsTracker ya da fake ile değiştirilebilir bağla (iOS MockServices paritesi).
  - ViewModel'lere (Compose) ya da Activity'lere tracker'ı constructor/alan üzerinden geçir; global singleton'a doğrudan erişimi en aza indir.
- **Gradle/bağımlılık:** Hilt seçilirse: com.google.dagger:hilt-android + hilt-compiler (kapt/ksp), plugin 'com.google.dagger.hilt.android'. Manuel DI'de ek bağımlılık yok.
- **Bağımlılık:** AN-02
- **Tuzaklar:** UI mimarisi (Compose+ViewModel vs Activity/Fragment) kararına bağlı. Hilt AndroidX ve AGP 8 gerektirir (foundation migration'a bağımlı).
- **Kabul kriteri:** Tüm call-site'lar tek AnalyticsTracker örneğini kullanır; testte NoOp/fake ile değiştirilebilir; Firebase örneği bir kez oluşturulur.

#### `AN-11` — Play Data Safety beyanı + QA (DebugView, R8, isim/limit doğrulama)  _(P2 · efor: S)_
- **Durum (2026-10-29):** ✔️ Kapatıldı (ürün sahibi kararı). Ad/parametre limitleri birim testte, Data Safety beyanı Play'de onaylı, DebugView'da 12 ekran ve oturumsuz olaylar doğrulandı; Play'den inen 3.1.0 (17) GA4'te olay gönderiyor (28 Eki: `first_open`, `session_start`, `screen_view`, `browse_level`, `exam_open`, `onboarding_complete`, `unlock_sheet_shown`). İşleme gecikmesi nedeniyle `login_success`, `profile_completed`, `earn_coins_tapped` kapanışta henüz raporda değildi; kodda engel yok.
- **iOS davranışı:** iOS yalnız ekran adı/level/format gönderir, kullanıcıyı tanımlayan hiçbir şey göndermez (dosya başı yorum: 'never archive names or anything that could identify the user').
- **iOS karşılığı/kaynak:** FirebaseAnalyticsTracker.swift başlık yorumu; AnalyticsUser yalnız slug+flag.
- **Android mevcut:** Data Safety formu analitik toplamayı yansıtmıyor; DebugView/QA süreci yok.
- **Android teknoloji:** Play Console Data Safety, DebugView (debug.firebase.analytics.app), R8/ProGuard.
- **Adımlar:**
  - Play Console Data Safety formunu güncelle: 'App activity' / 'App interactions' topluyor (analytics event'leri), amaç Analytics; 'Device or other IDs' TOPLANMIYOR (AD_ID kapalı, AN-08); veriler şifreli taşınıyor; kullanıcı silme talebi akışı (hesap silme) belirt.
  - Hiçbir event/user-property'nin PII (telefon, isim, serbest metin) içermediğini kod incelemesiyle doğrula; yalnız kind/level/tier/reason/slug.
  - QA: adb shell setprop debug.firebase.analytics.app com.salticusteam.vifi ile DebugView'de 23 event + 5 user property + screen_view'leri doğrula (parametre adları/değerleri iOS ile aynı).
  - Release (R8/minify) build'de event'lerin hala atıldığını doğrula; gerekiyorsa AnalyticsEvent model sınıfları için keep kuralı ekle (reflection yoksa gerekmez).
  - GA4 limitlerini doğrula: event adı<=40, param adı<=40, string değer<=100, user property adı<=24/değer<=36, event başına<=25 param.
- **Gradle/bağımlılık:** R8 için proguard-rules.pro (gerekirse). minifyEnabled release'de açılmalı (şu an false).
- **Bağımlılık:** AN-07, AN-08
- **Tuzaklar:** Data Safety'de 'Device or other IDs' AD_ID kapalıyken işaretlenmemeli; işaretlenirse iOS paritesi ve kullanıcı güveni bozulur. minifyEnabled şu an false; açılırsa release QA şart.
- **Kabul kriteri:** Data Safety formu analitik toplamayı ve AD_ID TOPLAMADIĞINI doğru yansıtır; DebugView'de 23 event + screen_view + 5 user property iOS'la aynı adlarla görünür; release build'de event'ler akar; hiçbir parametre PII içermez.


---

## Hesap + Yerelleştirme + Hakkında + Tasarım Sistemi  ·  `account`

**Özet:** iOS master (3.1.0, SwiftUI/MVVM) ile Android (2.5, salt Java, pre-AndroidX, AGP 3.4.2, jcenter) arasında bu alanda devasa bir uçurum var. Android'de Hesap ekranı, Hakkında ekranı, "Bize Ulaş" mailto akışı, tasarım sistemi (durum görünümleri, coin rozeti, seviye stilleri, turuncu-zemin/siyah-metin kontrastı), Son Görüntülenenler rafı ve merkezî string kaynakları HİÇ YOK. Mevcut LoginActivity e-posta/şifre ile çalışıyor (iOS telefon auth'a tamamen aykırı), strings.xml çoğunlukla Android Studio şablon artığı + kod içine gömülü Türkçe metinler, HomeActivity drawer + AdMob banner içeriyor. Bu alandaki tüm iOS davranışlarını Android'de yeniden kurmak için 21 somut iş kalemi üretildi. Önerilen hedef: Jetpack Compose + Material3 + Navigation Compose (Kotlin); alternatif Views+ViewModel (Java) her kalemde belirtildi. AdMob bu alanla kesişen tüm yerlerden kaldırılacak (iOS'ta reklam yok). Temel altyapı (AndroidX migrasyonu + Compose + modern Gradle + Firebase BoM) bir ön-koşul kalemi olarak eklendi; UI kalemleri buna bağımlı.

**Android mevcut durum:** app/build.gradle: compileSdk/targetSdk 28, minSdk 19, versionCode 16/versionName "2.5", AGP 3.4.2, jcenter (ölü), com.android.support 27/28 (pre-AndroidX), Firebase 16.x, Parse SDK, play-services-ads 17.2.0, android-pdf-viewer 2.3.0, Picasso. Gradle wrapper 5.1.1. Salt Java, Activity/Fragment. HomeActivity: DrawerLayout + NavigationView + BottomNavigationView + AdView banner + GooglePlayAppVersion (Play Store HTML scrape ile sürüm kontrolü, kırılgan/bozuk). LoginActivity: e-posta/şifre auth (iOS phone auth'a tamamen aykırı, kullanılmıyor gibi). strings.xml: Android Studio şablon artıkları ("Android Studio", "android.studio@android.com", "Settings", "Home/Dashboard/Notifications"), gerçek UI metinleri kod içine ve menü XML'lerine gömülü ("Add Lesson", "Kullanıcı İlanları", "Uygulamayı Paylaş"). colors.xml: colorAccent=#464646 (gri), colorAccent2=#FB920F (turuncu, kullanılmıyor). styles.x…

**Dokunulan backend sözleşmesi:** Bu alan ortak backend sözleşmesine UYAR, yeni backend yazmaz. Dokunduğu yerler: (1) Hesap silme — FirebaseUser.delete() + RTDB users/<uid>/profile silme (deleteUserData karşılığı; owner write yetkisi var); (2) Çıkış/silme reauth — Phone Auth +90 akışı (App Check/Play Integrity enforce edilir); (3) Coin bakiyesi okuma — wallets/<uid> read owner|admin (gösterim; yazma yok); (4) Admin kontrolü — custom claim 'admin' (getIdTokenResult). Android-özgü config boşluğu: RTDB config/appUpdate altına 'android' kardeşi eklenmesi (sürümleme kalemi, storeURL=Play Store) — bu backend/RTDB kuralları kalemi Update/force-update alanıyla paylaşımlı, burada yalnızca About'taki sürüm gösterimi ve AppVersion karşılaştırma yardımcısı kapsanır.

**Android'e özgü riskler:**
- Play Store targetSdk zorunluluğu: 28 -> 34/35 çıkılmalı (2024+ yeni uygulama/güncelleme şartı). Bu scoped storage, runtime izinler, exact alarm vb. pek çok davranışı tetikler.
- Dil kararı: iOS parite için önerilen Jetpack Compose KOTLIN gerektirir; proje 'salt Java'. Compose'a geçiş Kotlin ekosistemi + KSP + Compose Compiler plugin demektir. Alternatif Views+ViewModel (Java) ile devam edilebilir ama parite/maliyet dengesi değişir — KARAR GEREKİR (openQuestions).
- jcenter() kapandı -> mavenCentral()/google(); android-pdf-viewer artık JitPack'te. AndroidX migrasyonu (android.useAndroidX=true, enableJetifier=true ya da tam port) tüm com.android.support importlarını kırar.
- Android 11+ (API 30) paket görünürlüğü: mailto Intent.resolveActivity() null döner — manifest'e <queries><intent><action ACTION_SENDTO data mailto/> eklenmezse 'Bize Ulaş' sessizce başarısız olur.
- Android 13+ per-app language: AppCompatDelegate.setApplicationLocales + res/xml/locales_config.xml + manifest android:localeConfig gerekir; yalnız resConfigs 'tr' bazı cihazlarda sistem dilini ezmeye yetmez.
- Hesap silme reauth: FirebaseUser.delete() recent login yoksa FirebaseAuthRecentLoginRequiredException atar; Play Integrity App Check enforce edildiği için SMS akışında App Check token gerekir (debug build'de debug provider + Console'a debug token). reCAPTCHA fallback SafetyNet değil Play Integrity.
- AdMob kaldırılırken manifest'teki com.google.android.gms.ads.APPLICATION_ID meta-data da silinmezse MobileAds olmadan uygulama çökebilir (missing app id init crash).
- R8/ProGuard: minifyEnabled şu an false; release'te açılırsa Firebase/Firestore model sınıfları ve Compose için keep kuralları gerekir.
- Turuncu (#FB920F) zemin üstünde BEYAZ metin WCAG 3:1 altında kalır; iOS bilerek siyah metin kullanıyor — Material3 onPrimary=Black olmalı, aksi halde kontrast hatası.

### İş Kalemleri

#### `FND-00` — Temel altyapı ön-koşulu: AndroidX migrasyonu + Jetpack Compose + modern Gradle/Firebase BoM (bu alanın tüm UI kalemlerinin bağımlılığı)  _(P0 · efor: XL)_
- **iOS davranışı:** iOS zaten SwiftUI + modern araç zinciri. Android'in bu alandaki hiçbir ekranı mevcut pre-AndroidX/Java altyapıda iOS paritesinde kurulamaz.
- **iOS karşılığı/kaynak:** ViFiApp/AppEnvironment/AppDelegate; Swift Package Manager ile Firebase.
- **Android mevcut:** AGP 3.4.2, Gradle 5.1.1, jcenter (ölü), compileSdk/targetSdk 28, minSdk 19, com.android.support 27/28, Firebase 16.x, multidex. Compose/AndroidX yok.
- **Android teknoloji:** AGP 8.5+, Gradle 8.7+, Kotlin 2.0+, Jetpack Compose (BOM), Material3, Navigation Compose, Firebase BoM 33.x, Hilt/manuel DI, KSP
- **Adımlar:**
  - gradle/wrapper: Gradle 8.7+; AGP 8.5+'a yükselt (com.android.application).
  - settings.gradle(.kts): dependencyResolutionManagement { repositories { google(); mavenCentral() } } — jcenter'ı kaldır.
  - gradle.properties: android.useAndroidX=true, android.enableJetifier=true (veya tam AndroidX portu).
  - app/build.gradle: compileSdk 35, targetSdk 35, minSdk 24 (önerilen; 19/21 Compose'u desteklemez), versionCode/versionName güncelle; namespace 'com.salticusteam.vifi'.
  - Kotlin + Compose etkinleştir: org.jetbrains.kotlin.android plugin, org.jetbrains.kotlin.plugin.compose (Kotlin 2.0+ Compose Compiler Gradle plugin), buildFeatures { compose true }.
  - Firebase BoM'a geç: platform('com.google.firebase:firebase-bom:33.x') + firebase-auth, firebase-firestore, firebase-database, firebase-storage, firebase-messaging, firebase-analytics, firebase-appcheck-playintegrity.
  - com.android.support.* -> androidx.* (appcompat, constraintlayout, recyclerview, core-ktx) veya Compose'a taşı; com.google.android.material:material ekle.
  - Compose BOM: androidx.compose:compose-bom + material3, ui, ui-tooling; androidx.activity:activity-compose; androidx.navigation:navigation-compose; androidx.lifecycle:lifecycle-viewmodel-compose + lifecycle-runtime-compose (collectAsStateWithLifecycle).
  - DI: Hilt (com.google.dagger:hilt-android + hilt-compiler, KSP) veya manuel DI (iOS AppEnvironment eşdeğeri).
  - R8: release'te minifyEnabled true + Firebase/Compose keep kuralları.
- **Gradle/bağımlılık:** Tüm build.gradle + settings.gradle + gradle.properties + wrapper yeniden yazılır; jcenter kaldırılır, Firebase BoM, Compose BOM, Kotlin+Compose plugin eklenir.
- **Tuzaklar:** Bu kalem bu alana özgü değil, tüm projeyi etkiler — muhtemelen bir mimari/temel alt-ajanla paylaşımlı. Burada bağımlılık olarak listelendi; sahiplik netleşmeli. minSdk 24 kararı analytics/feature matrisini etkiler.
- **Kabul kriteri:** Proje AGP 8.5+/Gradle 8.7+ ile derlenir, jcenter içermez, androidx.* kullanır, Firebase BoM ile tek sürümde çözülür ve boş bir Compose @Composable ekran cihazda açılır.

#### `ACC-01` — Hesap ekranı (AccountScreen) — Compose ekran iskeleti ve bölümler  _(P0 · efor: L)_
- **iOS davranışı:** AccountView sheet olarak açılır; List içinde profileSection, coinSection, moderationSection (yalnız admin), phoneSection, signOutSection, deleteSection; navigationTitle 'Hesap', sağda 'Bitti' (isWorking iken disabled), interactiveDismiss isWorking iken kapalı. isAdmin appear'da auth.isCurrentUserAdmin() ile çözülür ve .animation ile açığa çıkar.
- **iOS karşılığı/kaynak:** ViFi/Features/Account/AccountView.swift (AccountScreen struct)
- **Android mevcut:** Hesap ekranı yok; HomeActivity drawer menüsünde yalnız 'Kullanıcı İlanları' ve 'Uygulamayı Paylaş' var.
- **Android teknoloji:** Jetpack Compose Material3 (Scaffold, TopAppBar, LazyColumn, ListItem, ModalBottomSheet), Navigation Compose, lifecycle-viewmodel-compose, FirebaseAuth getIdToken/claims
- **Adımlar:**
  - AccountScreen @Composable + AccountViewModel (androidx.lifecycle.ViewModel) oluştur; bir ModalBottomSheet ya da tam ekran Route (Navigation Compose) olarak Home'dan aç.
  - Scaffold + TopAppBar(title='Hesap', actions={ TextButton('Bitti') enabled=!isWorking }); LazyColumn ile bölümleri grupla (Material3 ListItem/Card).
  - Bölümler: ProfilRow (ad veya 'Profili Düzenle' -> EditProfile rotası), CoinRow (bakiye), ModerationRow (yalnız isAdmin), PhoneRow (salt okunur), ÇıkışButonu, SilButonu, hata alanı.
  - isAdmin'i ViewModel'de FirebaseAuth.getInstance().currentUser?.getIdToken(false) -> getClaims()['admin']==true ile çöz; state'e yaz.
  - Analytics: ekran görünümünde track screenView('Account').
  - testTag ile her satıra kimlik ver (account.editProfile, account.coinBalance, account.phone, account.signOut, account.delete, account.done) — iOS accessibilityIdentifier paritesi, Espresso/Compose UI testleri için.
- **Bağımlılık:** FND-00, Profil alanı (EditProfile), Coin ekonomisi alanı (wallet bakiyesi), Moderasyon alanı, Auth alanı (admin claim), Analytics alanı
- **Tuzaklar:** ModalBottomSheet'te geri/dışa tıklama isWorking iken engellenmeli (iOS interactiveDismissDisabled). Admin claim istemcide yalnız UI göstermek için; gerçek yetki Firestore kurallarında.
- **Kabul kriteri:** Hesap ekranı Home'dan açılır; profil/coin/telefon/çıkış/sil bölümleri görünür; admin kullanıcıda moderasyon satırı belirir, normal kullanıcıda görünmez; işlem sürerken 'Bitti' ve kapatma devre dışı.

#### `ACC-02` — Çıkış yapma (sign out) + onay diyaloğu + yerel temizlik  _(P0 · efor: M)_
- **iOS davranışı:** signOutSection butonu confirmationDialog açar ('Çıkış yapılsın mı?' / mesaj: 'Arşive tekrar ulaşmak için numaranı yeniden doğrulaman gerekir.'); onay 'Çıkış Yap' (destructive). viewModel.signOut() auth.signOut() çağırır; hata yoksa analytics.track(.signedOut). Oturum kapanınca session gate login ekranını gösterir (sheet dahil tüm arşiv değişir).
- **iOS karşılığı/kaynak:** AccountViewModel.signOut(); AccountView confirmationDialog
- **Android mevcut:** Çıkış işlevi yok (zaten gerçek bir oturum yönetimi yok).
- **Android teknoloji:** FirebaseAuth.signOut(), Compose AlertDialog, FirebaseAuth.AuthStateListener / Navigation Compose popUpTo
- **Adımlar:**
  - ViewModel.signOut(): FirebaseAuth.getInstance().signOut() (gerekirse önce Firestore/RTDB dinleyicilerini kaldır).
  - Compose AlertDialog onay: başlık 'Çıkış yapılsın mı?', metin 'Arşive tekrar ulaşmak için numaranı yeniden doğrulaman gerekir.', onayConfirm 'Çıkış Yap' (hata rengi), dismiss 'Vazgeç'.
  - Başarılıysa analytics.track(signedOut); SessionStore/auth state observer (FirebaseAuth.AuthStateListener veya Compose'da collectAsStateWithLifecycle) login rotasına yönlendirir ve back-stack temizler (navController.navigate(login){ popUpTo(0) }).
  - Çıkışta per-viewer geçici veriler (DataStore'daki taslaklar vb.) KALIR; recents arşivde kalır (iOS'ta çıkış recents'ı silmez).
- **Bağımlılık:** ACC-01, Auth/Session alanı, Analytics alanı
- **Tuzaklar:** signOut() exception atmaz ama Firestore kalıcı dinleyiciler açık kalırsa permission-denied log'u üretir; önce dinleyicileri sök.
- **Kabul kriteri:** 'Çıkış Yap' onaylanınca FirebaseAuth oturumu kapanır, kullanıcı login ekranına döner ve geri tuşu Hesap'a dönmez; analytics signedOut olayı atılır.

#### `ACC-03` — Hesap silme + yeniden doğrulama (reauth) durum makinesi — iOS AccountViewModel paritesi  _(P0 · efor: XL)_
- **iOS davranışı:** deleteAccount(): önce deleteUserData() (profil, signed-in iken) sonra auth.deleteAccount(); sonra onDeleted() (cihaz önbelleği purge) + analytics.accountDeleted. Firebase requiresRecentLogin atarsa aynı numaraya SMS gönderilir (sendVerificationCode) -> isReverifying=true, 6 haneli kod alanı açılır; updateCode digits-only max 6, 6'ya ulaşınca otomatik confirmReverification() -> reauthenticate(verificationID,code) -> auth.deleteAccount() -> onDeleted(). resendCode cooldown (LoginViewModel.resendCooldown) + 'Kodu tekrar gönder (N sn)'. invalidCode/codeExpired -> codeText temizlenir (codeExpired'da resendAvailableAt da sıfırlanır). cancelReverification state'i sıfırlar. Tüm akış boyunca isWorking ProgressView gösterir, alanlar disabled.
- **iOS karşılığı/kaynak:** ViFi/Features/Account/AccountViewModel.swift (deleteAccount, confirmReverification, resendCode, updateCode, cancelReverification, fail)
- **Android mevcut:** Hesap silme yok. Mevcut auth e-posta/şifre; telefon reauth altyapısı yok.
- **Android teknoloji:** FirebaseUser.delete()/reauthenticate(), PhoneAuthProvider.verifyPhoneNumber + PhoneAuthOptions + OnVerificationStateChangedCallbacks, PhoneAuthProvider.getCredential, FirebaseAuthRecentLoginRequiredException, FirebaseAuthInvalidCredentialsException, kotlinx-coroutines-play-services (Task.await()), StateFlow, DataStore/File cache temizliği, Coil diskCache
- **Adımlar:**
  - AccountViewModel'de StateFlow<AccountUiState>(isWorking, errorMessage, isReverifying, codeText, resendAvailableAt) kur; iOS @Observable paritesi.
  - deleteAccount(): viewModelScope.launch { deleteUserData(); user.delete().await(); onDeleted() }. deleteUserData = FirebaseDatabase RTDB users/<uid>/profile removeValue().await() (owner write yetkisi var).
  - FirebaseUser.delete() recent-login hatası: catch (FirebaseAuthRecentLoginRequiredException) -> sendReverificationCode().
  - sendReverificationCode(): PhoneAuthProvider.verifyPhoneNumber( PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance()).setPhoneNumber(user.phoneNumber).setTimeout(60L, SECONDS).setActivity(activity).setCallbacks(OnVerificationStateChangedCallbacks{ onCodeSent(verificationId, token)-> isReverifying=true; onVerificationCompleted(cred)-> doğrudan reauth+delete; onVerificationFailed(e)-> fail }) ).
  - confirmReverification(): PhoneAuthCredential cred = PhoneAuthProvider.getCredential(verificationId, code); user.reauthenticate(cred).await(); user.delete().await(); onDeleted().
  - updateCode(input): yalnız rakam, max 6; 6 haneye ulaşınca otomatik confirmReverification() (iOS autofill paritesi); OTP otomatik doldurma için SMS Retriever/Autofill (opsiyonel).
  - resend cooldown: System clock + 60sn (LoginViewModel.resendCooldown değerini Auth alanından al); geri sayım Compose'da LaunchedEffect/rememberCoroutineScope ile saniyelik güncelle; buton 'Kodu tekrar gönder (N sn)'.
  - Hata eşlemesi: FirebaseAuthInvalidCredentialsException (kod hatalı) -> codeText temizle; kod/oturum süresi dolması -> codeText temizle + resendAvailableAt sıfırla; Cancellation/iptal sessiz.
  - onDeleted(): cihaz önbelleğini sil — Coil ImageLoader.diskCache?.clear() + PDF/görsel cache dosyaları (filesDir/cacheDir) sil; analytics.accountDeleted.
  - UI: isWorking iken CircularProgressIndicator, tüm butonlar/alanlar disabled, 'Doğrula ve Hesabı Sil' butonu canConfirm (6 hane + verificationId + !isWorking) ile etkin; 'Vazgeç' cancelReverification().
- **Gradle/bağımlılık:** org.jetbrains.kotlinx:kotlinx-coroutines-play-services (Task.await); firebase-auth (BoM); App Check Play Integrity (reauth SMS'i App Check enforce'tan geçer).
- **Bağımlılık:** ACC-01, Auth alanı (telefon doğrulama), Profil alanı (profil silme), App Check/Play Integrity alanı, Analytics alanı
- **Tuzaklar:** verifyPhoneNumber Activity gerektirir — Compose'da LocalContext as Activity ya da reauth'u Activity-bağlamlı bir koordinatörle yürüt. Play Integrity App Check enforce edildiğinden debug build'de debug provider token Console'a eklenmezse SMS gitmez. onVerificationCompleted bazı cihazlarda anında tetiklenir (instant verification) — bu durumda kod ekranını atlayıp doğrudan sil. user null olabilir (oturum düşmüş) -> 'Oturumun sona erdi' mesajı.
- **Kabul kriteri:** Hesabı Sil akışı: recent-login gerektiğinde numaraya SMS gelir, 6 hane girilince otomatik doğrulanıp hesap + RTDB profili silinir, önbellek temizlenir, login ekranına dönülür; yanlış kodda alan temizlenir ve hata gösterilir; resend butonu cooldown sayacını gösterir.

#### `ACC-04` — Telefon numarası biçimlendirme ve salt-okunur gösterimi  _(P1 · efor: S)_
- **iOS davranışı:** formattedPhoneNumber: user.formattedPhoneNumber ?? 'Telefon numarası yok'; '+90 532 123 45 67' formatı; monospacedDigit; değiştirilemez (Numaran giriş sırasında doğrulandı ve değiştirilemez).
- **iOS karşılığı/kaynak:** AccountViewModel.formattedPhoneNumber; AuthUser.formattedPhoneNumber
- **Android mevcut:** Yok.
- **Android teknoloji:** FirebaseUser.phoneNumber, libphonenumber (opsiyonel), Compose Text FontFamily.Monospace
- **Adımlar:**
  - FirebaseAuth.getInstance().currentUser?.phoneNumber (E.164, ör. +905321234567) al.
  - Biçimlendir: libphonenumber (com.googlecode.libphonenumber:libphonenumber) PhoneNumberUtil.format(..., INTERNATIONAL) ile '+90 532 123 45 67'; veya basit kendi formatlayıcın (iOS ile birebir gruplama +90 XXX XXX XX XX).
  - null ise strings.xml 'account_phone_missing' = 'Telefon numarası yok'.
  - Text'i monospace rakam stiliyle göster (FontFamily.Monospace veya tabular figür); salt okunur, düzenleme yok.
- **Bağımlılık:** ACC-01
- **Tuzaklar:** iOS ve Android biçimlendirmesi birebir aynı görünmeli (gruplama). libphonenumber ~1.5MB; istenmezse elle formatla.
- **Kabul kriteri:** Hesap ekranında kullanıcının numarası '+90 5XX XXX XX XX' olarak, monospace ve salt okunur gösterilir.

#### `ABT-01` — Hakkında ekranı (AboutScreen) — logo, sürüm, açıklama, Öne Çıkanlar, Veri Kaynağı, tanıtım turu, Bize Ulaş  _(P1 · efor: L)_
- **iOS davranışı:** AboutView: identitySection (Logo, 'ViFi' largeTitle, 'Sınav Arşivi' headline, 'Sürüm X (build)' kapsül); açıklama paragrafı; 'Öne Çıkanlar' (Görsel ve PDF sınavlar tek yerde / Yakınlaştırarak ayrıntılı inceleme / Son görüntülenen sınavlara hızlı erişim); 'Tanıtım turunu tekrar izle' (OnboardingView source:'replay', seen flag'e dokunmaz); 'Veri Kaynağı' (çevrim içi arşiv + önbellek notu, icloud ikonu); 'İletişim' -> 'Bize Ulaş' + footer 'Görüş, öneri ve sorularını bize e-posta ile iletebilirsin.'; TopBar 'Hakkında' + 'Bitti'.
- **iOS karşılığı/kaynak:** ViFi/Features/Home/AboutView.swift
- **Android mevcut:** Hakkında ekranı yok.
- **Android teknoloji:** Compose Material3 (Scaffold, LazyColumn, ListItem), BuildConfig.VERSION_NAME/VERSION_CODE, Material Icons Extended, Navigation Compose
- **Adımlar:**
  - AboutScreen @Composable: Scaffold + TopAppBar('Hakkında', 'Bitti'); LazyColumn bölümleri.
  - Kimlik bölümü: R.mipmap/drawable logo (Image, rounded clip), 'ViFi' (headlineLarge bold), 'Sınav Arşivi' (titleMedium secondary), sürüm kapsülü.
  - Sürüm metni: 'Sürüm %1$s (%2$s)' = BuildConfig.VERSION_NAME + BuildConfig.VERSION_CODE (iOS marketingVersion/buildNumber paritesi) — strings.xml plural/format kaynağından.
  - Açıklama + 'Öne Çıkanlar' satırları: ListItem + leadingIcon (Material Icons: Icons.Outlined.Description, ZoomIn, History) — iOS SF Symbols karşılıkları.
  - 'Tanıtım turunu tekrar izle': Onboarding alanının replay API'sini çağır (seen flag DataStore'a dokunmaz).
  - 'Veri Kaynağı' bölümü: strings.xml açıklama + bulut ikonu.
  - 'Bize Ulaş' satırı -> CON-01 (ContactSupport.sendEmail()).
  - Tüm metinler strings.xml'den; testTag about.version/about.contact/about.replayOnboarding.
- **Bağımlılık:** FND-00, CON-01 (Bize Ulaş), Onboarding alanı (tanıtım turu), DS-02 (durum/satır bileşenleri)
- **Tuzaklar:** BuildConfig paketini doğru import et (com.salticusteam.vifi.BuildConfig). Logo mipmap yerine vektör/webp drawable tercih et. Replay turu tanıtımın 'görüldü' bayrağını DEĞİŞTİRMEMELİ.
- **Kabul kriteri:** Hakkında ekranı logo, 'ViFi', 'Sınav Arşivi', doğru 'Sürüm X (build)', açıklama, 3 öne çıkan özellik, veri kaynağı notu, tanıtım turu tekrar butonu ve çalışan 'Bize Ulaş' satırını gösterir.

#### `CON-01` — Bize Ulaş (mailto) yardımcı akışı — ContactSupport paritesi + no-mail fallback  _(P1 · efor: M)_
- **iOS davranışı:** ContactSupport: email buyukeryazilim@gmail.com; subject 'ViFi — Bize Ulaş'; body = (varsa context satırı + boş satır) + boş alan + '—' + 'ViFi <sürüm> (<build>) · iOS <os>' (ad/telefon/uid gibi PII YOK). openURL mailto; canOpenURL false ise 'Mail uygulaması bulunamadı' alert + 'Adresi Kopyala' (UIPasteboard) + 'Tamam'. Context opsiyonel (ör. hangi sınav). Her tıklamada yeni ContactRequest.
- **iOS karşılığı/kaynak:** ViFi/Core/Utilities/ContactSupport.swift
- **Android mevcut:** Yok; HomeActivity yalnızca Play Store linkini ACTION_SEND ile paylaşıyor.
- **Android teknoloji:** Intent.ACTION_SENDTO + Uri 'mailto:', Intent.EXTRA_EMAIL/EXTRA_SUBJECT/EXTRA_TEXT, Intent.createChooser, ClipboardManager, manifest <queries>, BuildConfig + Build.VERSION.RELEASE/Build.MODEL
- **Adımlar:**
  - ContactSupport util (object/singleton): email='buyukeryazilim@gmail.com', subject='ViFi — Bize Ulaş'.
  - body(context): satırlar = (context varsa context + boş) + boş + '—' + 'ViFi ' + BuildConfig.VERSION_NAME + ' (' + BuildConfig.VERSION_CODE + ') · Android ' + Build.VERSION.RELEASE; satırları \n ile birleştir. PII ekleme.
  - sendEmail(context, examContext?): Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse('mailto:')); intent.putExtra(Intent.EXTRA_EMAIL, new String[]{email}); putExtra(EXTRA_SUBJECT, subject); putExtra(EXTRA_TEXT, body); try startActivity(Intent.createChooser(intent, ...)) catch(ActivityNotFoundException) -> fallback.
  - Fallback: ClipboardManager ile adresi kopyala + Snackbar/Compose SnackbarHost ya da AlertDialog 'Mail uygulaması bulunamadı' / 'Adresi Kopyala' / 'Tamam' (iOS alert paritesi).
  - MANIFEST: Android 11+ paket görünürlüğü için <queries><intent><action android:name='android.intent.action.SENDTO'/><data android:scheme='mailto'/></intent></queries> ekle — yoksa resolveActivity null döner.
  - Çağrı noktaları: About (CON kullanılır), giriş numara ekranı ('Giriş yapamıyor musun? Bize Ulaş' — Auth alanıyla paylaşımlı dep), sınav görüntüleyiciler (Exam alanı dep) — hepsi aynı util'i çağırır, context parametresiyle.
- **Bağımlılık:** FND-00
- **Tuzaklar:** mailto: gövdesinde \n'lerin yüzde-kodlanması Uri.parse('mailto:') + extras ile gerekmez (extras düz metin). <queries> eklenmezse Android 11+'ta sessiz başarısızlık — mutlaka ekle. ACTION_SEND değil ACTION_SENDTO kullan (sadece e-posta istemcileri çıksın).
- **Kabul kriteri:** 'Bize Ulaş' tıklanınca e-posta istemcisi alıcı/konu/gövde (sürüm+Android sürümü footer'ı, PII yok) dolu açılır; istemci yoksa adres kopyalanır ve kullanıcı bilgilendirilir.

#### `VER-01` — Sürümleme — AppVersion karşılaştırma yardımcısı + About sürüm gösterimi + eski Play Store scrape'in kaldırılması  _(P1 · efor: M)_
- **iOS davranışı:** AppVersion: noktalı sürüm karşılaştırması ('2.0.10' > '2.0.9'), eksik bileşen=0, non-numeric'te öndeki rakamlar. AboutView sürüm gösterimi marketingVersion (CFBundleShortVersionString) + buildNumber (CFBundleVersion). Force-update RTDB config/appUpdate üzerinden (RemoteAppUpdateChecker) — bu kalemde yalnız karşılaştırma yardımcısı + gösterim kapsanır.
- **iOS karşılığı/kaynak:** ViFi/Core/Utilities/AppVersion.swift; AboutView Bundle.marketingVersion/buildNumber
- **Android mevcut:** GooglePlayAppVersion Play Store HTML'ini scrape ederek VERSION_NAME ile string-eşitlik karşılaştırıyor (kırılgan, bozuk, no-newer mantığı yok) ve HomeActivity'de 'Error' başlıklı İngilizce+Türkçe karışık dialog gösteriyor.
- **Android teknoloji:** BuildConfig.VERSION_NAME/VERSION_CODE, saf Kotlin util; (eski) AsyncTask + HttpURLConnection kaldırılır
- **Adımlar:**
  - GooglePlayAppVersion AsyncTask'i ve HomeActivity'deki scrape+dialog bloğunu tamamen kaldır (AsyncTask zaten deprecated; HTML scrape çalışmıyor).
  - AppVersion util (Kotlin object): isVersionNewerThan(candidate, other) — '.' ile böl, bileşenleri Int'e çevir (öndeki rakamlar, yoksa 0), eksik bileşen 0; iOS AppVersion.swift birebir portu.
  - About sürüm: BuildConfig.VERSION_NAME + BuildConfig.VERSION_CODE.
  - versionName'i iOS serisiyle hizala (karar: openQuestions) ve versionCode'u artır.
  - Not: Gerçek force-update (RTDB config/appUpdate/android/{minimumVersion,latestVersion,storeURL,message}) Update alanında; bu util onun da kullanacağı karşılaştırma mantığını sağlar.
- **Bağımlılık:** ABT-01, Update/force-update alanı (RTDB config/appUpdate/android)
- **Tuzaklar:** Play Store HTML scrape'i kesinlikle bırak — Google sayfa yapısı değişti, çalışmıyor ve ANR riski taşıyor. Karşılaştırma yalnız '>' (strictly newer) olmalı, string eşitlik değil.
- **Kabul kriteri:** HomeActivity'deki scrape tabanlı sürüm uyarısı kaldırılmış; AppVersion.isVersionNewerThan birim testlerle doğru ('2.0.10'>'2.0.9', '3.0'=='3.0.0'); About doğru sürüm/build gösteriyor.

#### `LOC-01` — Türkçe-only yerelleştirme — tüm metinleri strings.xml'e taşı, İngilizce/şablon artıklarını temizle, dili kilitle  _(P0 · efor: L)_
- **iOS davranışı:** Uygulama tamamen Türkçe (İngilizce düşürüldü). Tüm kullanıcı metinleri Localizable.xcstrings'te merkezî, sourceLanguage 'tr'. Kod içinde gömülü metin yok.
- **iOS karşılığı/kaynak:** ViFi/Resources/Localizable.xcstrings (sourceLanguage 'tr')
- **Android mevcut:** strings.xml çoğunlukla Android Studio şablonu ('Android Studio', 'android.studio@android.com', 'Settings', 'Home/Dashboard/Notifications', 'Main2Activity'); gerçek UI metinleri Java/XML içine gömülü ('Add Lesson', 'Error', 'Kullanıcı İlanları', 'Uygulamayı Paylaş', dialog mesajları).
- **Android teknoloji:** res/values/strings.xml, resConfigs 'tr', AppCompatDelegate.setApplicationLocales + LocaleListCompat, res/xml/locales_config.xml, android:localeConfig
- **Adımlar:**
  - res/values/strings.xml'i Türkçe uygulama metinleriyle yeniden yaz; iOS Localizable.xcstrings'teki tüm anahtarları karşıla (Hesap, Çıkış Yap, Hesabı Sil, Doğrula ve Hesabı Sil, Hakkında, Bize Ulaş, Öne Çıkanlar, Veri Kaynağı, Son Görüntülenenler, Sınav Ekle, Tekrar Dene, Yükleniyor…, Bir sorun oluştu, vb.).
  - Şablon/İngilizce artıkları sil: nav_header_title/subtitle, action_settings, title_home/dashboard/notifications, title_activity_main2/3; menü XML'lerindeki gömülü İngilizce 'Add Lesson' -> string kaynağı; dialog 'Error' -> Türkçe.
  - Kod içi tüm hardcoded Türkçe/İngilizce string'leri getString(R.string.*) ile değiştir (HomeActivity, LoginActivity Toast'ları vb.).
  - Dili kilitle: app/build.gradle defaultConfig { resConfigs 'tr' } (diğer dil kaynaklarını strip et).
  - Android 13+ per-app language: res/xml/locales_config.xml (tr) + manifest android:localeConfig + uygulama açılışında AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags('tr')).
  - RTL supportsRtl zaten true; Türkçe LTR olduğundan sorun yok, bırak.
  - %s/%d argümanlarını konumlu yap (%1$s, %2$d) — iOS'taki %1$@/%2$lld paritesi.
- **Bağımlılık:** FND-00
- **Tuzaklar:** Compose'da stringResource(R.string.*) kullan; hardcoded metin Compose lint'i 'HardcodedText' uyarır. resConfigs tek başına sistem dilini ezmez — Android 13 altı cihazlarda da Türkçe için uygulama açılışında locale set et. Argüman sırası konumlu olmalı yoksa çeviri/format hataları.
- **Kabul kriteri:** Tüm görünür metinler strings.xml'den gelir, kodda/menülerde gömülü string kalmaz; şablon ve İngilizce artıklar silinmiş; uygulama cihaz dili ne olursa olsun Türkçe açılır.

#### `LOC-02` — Çoğul (plurals) ve sayılı metinler — '%d fakülte/bölüm/ders/sınav/sayfa' kaynakları  _(P2 · efor: S)_
- **iOS davranışı:** LevelStyle.childCountText ve ExamKind.contentText: '\(count) fakülte', '\(count) bölüm', '\(count) ders', '\(count) sınav', '\(fileCount) sayfa', 'PDF belge'. Türkçe tekil/çoğul ayrımı yapmaz (tek biçim).
- **iOS karşılığı/kaynak:** ViFi/DesignSystem/LevelStyle.swift (childCountText, contentText, badge)
- **Android mevcut:** Yok; sayılar kod içinde string birleştirme ile üretiliyor.
- **Android teknoloji:** res/values <plurals>, resources.getQuantityString
- **Adımlar:**
  - res/values/strings.xml içine <plurals> tanımla (faculty_count, department_count, course_count, exam_count, page_count); Türkçe için yalnız 'other' niceliği yeterli (TR'de tekil/çoğul morfolojisi sayıdan bağımsız).
  - resources.getQuantityString(R.plurals.faculty_count, count, count) ile kullan.
  - iOS ile birebir metin: '%d fakülte', '%d bölüm', '%d ders', '%d sınav', '%d sayfa'.
  - PDF için düz string 'PDF belge'; format rozetleri 'Görsel'/'PDF'.
- **Bağımlılık:** LOC-01, DS-04 (LevelStyle)
- **Tuzaklar:** Türkçe locale'de ICU yalnız 'other' kullanır; 'one' tanımlamak gereksiz ama zarar vermez. String birleştirme yerine plurals kullan (çeviri güvenliği).
- **Kabul kriteri:** Liste alt başlıkları '%d fakülte/bölüm/ders/sınav' ve sayfa sayıları plurals üzerinden, iOS ile aynı metinle gösterilir.

#### `DS-01` — Tema ve renk sistemi — Material3 + turuncu birincil/siyah metin kontrastı  _(P0 · efor: M)_
- **iOS davranışı:** Marka turuncusu (#FB920F benzeri .orange). ErrorStateView 'Tekrar Dene' butonu: borderedProminent (turuncu zemin) üstünde SİYAH metin — yorum: 'Black on the brand orange: white text would fall below 3:1 contrast'. CoinBalanceBadge turuncu .14 opak kapsül, turuncu ikon.
- **iOS karşılığı/kaynak:** ViFi/DesignSystem/Components.swift (ErrorStateView), CoinBalanceBadge.swift
- **Android mevcut:** colors.xml: colorPrimary/Dark/Accent = #464646 (gri), colorAccent2 = #FB920F kullanılmıyor. styles.xml Theme.AppCompat.Light.DarkActionBar (eski).
- **Android teknoloji:** Compose Material3 lightColorScheme/darkColorScheme, ButtonDefaults.buttonColors, (Views için Theme.Material3.DayNight)
- **Adımlar:**
  - colors.xml'i marka paletiyle yeniden kur: brand_orange = #FB920F, onBrand = #000000 (siyah), background/surface tonları.
  - Material3 teması: androidx.compose.material3 lightColorScheme(primary=BrandOrange, onPrimary=Black, ...); dynamicColor KAPALI (marka tutarlılığı). Dark mode için dark scheme tanımla ya da iOS gibi tek tema + sistemle uyum.
  - Buton stili: Button(colors = ButtonDefaults.buttonColors(containerColor=BrandOrange, contentColor=Black)) — turuncu üstünde siyah metin (WCAG 3:1 için).
  - XML Views alternatifinde: Theme.Material3.* + ?attr/colorPrimary = turuncu, colorOnPrimary = siyah.
  - styles.xml eski AppCompat temasını Material3'e taşı; status bar/nav bar renkleri.
- **Bağımlılık:** FND-00
- **Tuzaklar:** Turuncu (#FB920F) üstünde beyaz metin kontrast ~1.9:1 (WCAG altı); mutlaka siyah onPrimary. Material3 dynamicColor (Android 12+) marka turuncusunu ezer — kapat. Dark mode'da turuncu zemin yine siyah metinle okunur kalmalı.
- **Kabul kriteri:** Tüm birincil butonlar marka turuncusu zemin + siyah metin; dynamicColor kapalı; tema Material3; kontrast denetimi 3:1 üzerini geçer.

#### `DS-02` — Durum görünümleri — Loading / Error(Tekrar Dene) / Empty bileşenleri  _(P0 · efor: M)_
- **iOS davranışı:** LoadingStateView (ProgressView büyük + 'Yükleniyor…', ortalı, state.loading); ErrorStateView (ContentUnavailableView 'Bir sorun oluştu' + mesaj + 'Tekrar Dene' siyah metin turuncu buton, retry closure, state.error/state.retry); EmptyStateView (ikon + başlık + opsiyonel mesaj, state.empty). Loadable<Value> (loading/loaded/failed) enum ile sürülür.
- **iOS karşılığı/kaynak:** ViFi/Core/Utilities/Loadable.swift; ViFi/DesignSystem/Components.swift
- **Android mevcut:** Yok; mevcut fragment'larda yükleme/hata/boş durum yönetimi yok.
- **Android teknoloji:** Compose Material3 CircularProgressIndicator/Button, sealed interface Loadable, Material Icons
- **Adımlar:**
  - Loadable<T> sealed interface (Loading/Loaded(value)/Failed(message)) — iOS Loadable paritesi; isLoading/value yardımcıları.
  - LoadingState @Composable: Box(fillMaxSize, Center) { CircularProgressIndicator + Text('Yükleniyor…') }.
  - ErrorState @Composable: ikon (Icons.Outlined.Warning) + 'Bir sorun oluştu' + message + Button('Tekrar Dene', onClick=retry) turuncu/siyah; testTag state.error/state.retry.
  - EmptyState @Composable: ikon + başlık + opsiyonel mesaj (Material3 boş durum deseni).
  - Bu bileşenleri Home üniversite listesi, recents, moderasyon, inbox vb. tüm async ekranlarda yeniden kullan.
  - Views alternatifi: include'lanan layout + ViewSwitcher/isVisible ile göster/gizle.
- **Bağımlılık:** FND-00, DS-01
- **Tuzaklar:** Error mesajı data katmanından userMessage paritesiyle (Firebase exception -> Türkçe kullanıcı mesajı eşlemesi) gelmeli; ham exception mesajı gösterme. Retry butonu iş sürerken disabled olabilmeli.
- **Kabul kriteri:** Üç durum bileşeni mevcut ve en az bir async ekranda (Home üniversite listesi) yükleme/hata(retry çalışır)/boş hallerini iOS ile aynı metin ve kontrastla gösterir.

#### `DS-03` — IconBadge ve CoinBalanceBadge bileşenleri  _(P1 · efor: M)_
- **iOS davranışı:** IconBadge: SF Symbol, yumuşak köşeli kare (corner ~0.28*size) üstünde tint ikon, satır/kart baş ikonu, accessibilityHidden. CoinBalanceBadge: toolbar kapsülü, bitcoinsign.circle.fill turuncu + bakiye (subheadline semibold, monospacedDigit, numericText geçişi), turuncu .14 kapsül; wallet.balance canlı; accessibilityLabel 'Coin bakiyen', id coin.balance.
- **iOS karşılığı/kaynak:** ViFi/DesignSystem/Components.swift (IconBadge), CoinBalanceBadge.swift
- **Android mevcut:** Yok.
- **Android teknoloji:** Compose Box/Row/Icon/Text, RoundedCornerShape, animateIntAsState/AnimatedContent, collectAsStateWithLifecycle
- **Adımlar:**
  - IconBadge @Composable(icon: ImageVector, tint=BrandOrange, size=40.dp): Box(size, background=tint.copy(alpha=0.15f), RoundedCornerShape(size*0.28), contentAlignment=Center){ Icon(icon, tint=tint, contentDescription=null) }.
  - CoinBalanceBadge @Composable: Row içinde Icon(coin, tint=BrandOrange) + Text(balance monospace semibold); background BrandOrange.copy(alpha=0.14f) + CircleShape/RoundedCornerShape; balance CoinWalletStore'dan collectAsStateWithLifecycle (Coin alanı).
  - Sayı değişiminde animasyon: AnimatedContent / animateIntAsState (iOS numericText paritesi).
  - Erişilebilirlik: Modifier.semantics { contentDescription = 'Coin bakiyen: N' }; testTag coin.balance.
  - Coin ikonu için drawable/Material simge (bitcoin yerine marka coin ikonu kullanılabilir).
- **Bağımlılık:** DS-01, Coin ekonomisi alanı (wallet bakiyesi)
- **Tuzaklar:** Coin bakiyesi wallets/<uid> read owner|admin — yalnız gösterim; App Check enforce. Bakiye yoksa 0 göster. Bitcoin sembolü yerine ürün coin simgesi tercih edilebilir (iOS placeholder SF Symbol kullanıyor).
- **Kabul kriteri:** IconBadge satır/kart ikonlarında kullanılıyor; CoinBalanceBadge Home/Hesap'ta canlı bakiyeyi turuncu kapsülde, değişimde animasyonlu gösteriyor ve TalkBack 'Coin bakiyen' okuyor.

#### `DS-04` — LevelStyle — arşiv seviyesi başlık/ikon/çocuk-sayısı ve sınav türü rozet/metinleri  _(P2 · efor: S)_
- **iOS davranışı:** ArchiveLevel: listTitle (Üniversiteler/Fakülteler/Bölümler/Dersler/Sınavlar), symbolName (building.columns/graduationcap/books.vertical/book.closed/doc.text), childCountText('%d fakülte' vb). ExamKind: symbolName (photo/doc.richtext), badge ('Görsel'/'PDF'), contentText ('%d sayfa'/'PDF belge').
- **iOS karşılığı/kaynak:** ViFi/DesignSystem/LevelStyle.swift
- **Android mevcut:** Yok; seviye başlıkları ve ikonlar kod/menu XML'inde gömülü (drawable/university_24dp vb. var).
- **Android teknoloji:** Kotlin enum + when, stringResource/getQuantityString, mevcut vector drawable'lar
- **Adımlar:**
  - ArchiveLevel enum (Kotlin) üzerine extension/when ile listTitle (stringResource), iconRes (mevcut drawable/university_24dp, faculty_24dp, deparment_24dp, lesson_24dp + exam için doc ikonu), childCountText (plurals, LOC-02).
  - ExamKind enum: iconRes (görsel/pdf), badge ('Görsel'/'PDF'), contentText (plurals '%d sayfa' / 'PDF belge').
  - Mevcut vektör drawable'ları (res/drawable/*_24dp) yeniden kullan; eksikse Material simge ekle.
  - Material Icons ya da mevcut vektörlerle iOS SF Symbol karşılıklarını eşle.
- **Bağımlılık:** DS-01, LOC-02 (plurals), Browse/Arşiv alanı (ArchiveLevel/ExamKind modelleri)
- **Tuzaklar:** Seviye ikonları zaten drawable olarak var (university_24dp vb.); yenisini üretmeden yeniden kullan. 'exam' seviyesinin childCountText'i boş.
- **Kabul kriteri:** Her arşiv seviyesi için başlık, ikon ve çocuk sayısı metni; her sınav türü için ikon/rozet/içerik metni iOS ile birebir aynı değerleri döndürür.

#### `HOME-01` — Home navigasyon ve toolbar yeniden yapısı — Coin rozeti + Bildirimler + Hakkında + Hesap, drawer kaldırma  _(P1 · efor: L)_
- **iOS davranışı:** HomeView toolbar: topBarLeading CoinBalanceBadge; topBarTrailing Bildirimler (bell / bell.badge.fill okunmamışsa, inbox sheet), Hakkında (info.circle, About sheet), Hesap (person.crop.circle, Account sheet). Ekranlar sheet olarak açılır; kapanınca Home screen-view tekrar track edilir. Büyük başlık 'ViFi'. Search bar (üniversite arama). Pull-to-refresh.
- **iOS karşılığı/kaynak:** ViFi/Features/Home/HomeView.swift
- **Android mevcut:** HomeActivity: DrawerLayout + NavigationView (Kullanıcı İlanları/Uygulamayı Paylaş) + BottomNavigationView + AdView banner + Toolbar(başlık boş) + UniversityFragment. Hesap/Hakkında/Bildirim toolbar aksiyonu yok.
- **Android teknoloji:** Compose Material3 Scaffold/TopAppBar/SearchBar/PullToRefreshBox, Navigation Compose, Material Icons, collectAsStateWithLifecycle
- **Adımlar:**
  - HomeScreen @Composable: Scaffold + TopAppBar(title='ViFi', navigationIcon/actions). Drawer'ı kaldır (iOS'ta drawer yok).
  - TopAppBar actions: CoinBalanceBadge (lead/sol) + IconButton Bildirimler (Icons.Outlined.Notifications / badge'li varyant okunmamış sayısına göre) + IconButton Hakkında (Icons.Outlined.Info) + IconButton Hesap (Icons.Outlined.AccountCircle).
  - Navigasyon: Navigation Compose rotaları (home, about, account, inbox, addExam, editProfile...) ya da Compose ModalBottomSheet; her açılış/kapanışta analytics screenView.
  - İçerik: hero header + 'Sınav Ekle' kartı + Son Görüntülenenler (HOME-02) + üniversite listesi (DS-02 durumları).
  - Arama: TextField/SearchBar (Material3 SearchBar) üniversite filtreleme.
  - Pull-to-refresh: androidx.compose.material3 pullRefresh / PullToRefreshBox.
  - Eski DrawerLayout/NavigationView/BottomNavigationView/menu kaynakları (activity_home_drawer.xml, navigation.xml) kaldır; 'Uygulamayı Paylaş' istenirse Hakkında/overflow'a taşınabilir (iOS'ta yok).
- **Bağımlılık:** ACC-01, ABT-01, DS-03, Bildirimler/Inbox alanı
- **Tuzaklar:** Bildirim rozeti announcements.unreadCount'a bağlı (Bildirim alanı dep). Drawer kaldırılınca geri tuşu davranışı Navigation Compose back-stack'e devredilmeli. 'Uygulama paylaş' iOS paritesinde yok — kaldırmak ya da taşımak karar.
- **Kabul kriteri:** Home ekranı 'ViFi' başlığı, solda coin rozeti, sağda Bildirimler/Hakkında/Hesap aksiyonlarıyla açılır; drawer yok; her aksiyon doğru ekrana gider ve screen-view analytics atılır.

#### `HOME-02` — Son Görüntülenenler (recents) rafı + yerel depolama + kaldır/temizle  _(P1 · efor: L)_
- **iOS davranışı:** RecentExamsSection: 'Son Görüntülenenler' başlık + 'Temizle' (confirmationDialog 'Son görüntülenenler temizlensin mi?' / 'Sınavlar arşivde kalmaya devam eder; yalnızca bu liste boşaltılır.' / 'Tümünü Temizle'); yatay kaydırılan kartlar (RecentExamCard: tür ikonu+rozet, başlık, konum altyazı, açılma zamanı relative); kart context menu 'Listeden Kaldır'. RecentExamsStore kalıcı; boşsa bölüm gizli. Açınca router.openExam + analytics.recentExamOpened.
- **iOS karşılığı/kaynak:** ViFi/Features/Home/RecentExamsSection.swift; Core/Services/RecentExamsStore
- **Android mevcut:** Yok; son görüntülenen kavramı yok.
- **Android teknoloji:** DataStore (androidx.datastore:datastore-preferences) veya Room, Compose LazyRow/Card, AlertDialog, DateUtils.getRelativeTimeSpanString, kotlinx.serialization (path serileştirme)
- **Adımlar:**
  - RecentExam veri modeli (path bileşenleri, kind, openedAt) + RecentExamsStore: DataStore(Preferences ya da Proto)/Room ile cihazda sakla (en çok N kayıt, en yeni önce).
  - record(path,kind,at) sınav açılışında çağrılır (Exam alanı dep); remove(exam); clear().
  - Home'da recents boş değilse LazyRow ile kartları göster (DS-03 IconBadge + tür rozeti); kart tıklanınca sınavı aç + analytics recentExamOpened.
  - 'Temizle': Compose AlertDialog onay ('Son görüntülenenler temizlensin mi?' / mesaj / 'Tümünü Temizle' hata rengi).
  - Kart uzun bas/overflow: 'Listeden Kaldır' (iOS context menu paritesi); erişilebilirlik için custom action.
  - Açılma zamanı: DateUtils.getRelativeTimeSpanString ile 'relative' metin (iOS relative(named) paritesi).
  - Çıkış/silme recents'ı silmez (iOS davranışı) — yalnız hesap silmede onDeleted ile cache temizlenir; recents ayrı karar (openQuestions).
- **Bağımlılık:** HOME-01, DS-03, Exam alanı (sınav açma/RecentExam modeli)
- **Tuzaklar:** DataStore IO main thread'i bloke etmemeli (suspend). Path bileşenlerini (üniversite/fakülte/.../sınav) serileştir; yeni Firestore catalog modeliyle uyumlu olmalı (eski RTDB Universitiess değil). Relative zaman TalkBack'te de okunmalı.
- **Kabul kriteri:** Sınav açıldıkça recents rafına eklenir ve cihaz yeniden başlasa da kalır; kart tıklanınca sınav açılır; 'Temizle' onayla listeyi boşaltır (arşivi etkilemez); boşken raf gizli.

#### `HOME-03` — Home hero header + 'Sınav Ekle' çağrı kartı  _(P2 · efor: S)_
- **iOS davranışı:** HomeHeroHeader: Logo + 'Sınav Arşivi' (title2 bold) + 'Üniversitelerin geçmiş sınavlarına hızlıca ulaş.' altyazı. addExamSection: IconBadge(plus.rectangle.on.rectangle) + 'Sınav Ekle' + 'Arşive sınav ekle, onaylanınca coin kazan.' + chevron -> AddExamView.
- **iOS karşılığı/kaynak:** ViFi/Features/Home/HomeView.swift (HomeHeroHeader, addExamSection)
- **Android mevcut:** Yok; sadece üniversite listesi fragment'ı.
- **Android teknoloji:** Compose Material3 Row/Card/ListItem, IconBadge (DS-03)
- **Adımlar:**
  - HeroHeader @Composable: Row(Logo + Column('Sınav Arşivi' titleLarge bold + altyazı secondary)).
  - AddExamCard @Composable: ListItem/Card(IconBadge + 'Sınav Ekle' + açıklama + trailing chevron) -> AddExam rotası (AddExam alanı).
  - Metinler strings.xml'den; testTag home.addExam.
- **Bağımlılık:** HOME-01, DS-03, AddExam alanı
- **Tuzaklar:** AddExam akışı başka alan; burada yalnız kart + navigasyon. Logo drawable/webp olmalı.
- **Kabul kriteri:** Home üstünde logo+tagline hero ve 'Sınav Ekle' kartı görünür; kart AddExam ekranını açar.

#### `ADS-01` — AdMob'u bu alanın kesiştiği yerlerden kaldır (iOS'ta reklam yok — parite)  _(P1 · efor: M)_
- **iOS davranışı:** iOS'ta hiç reklam yok; monetizasyon coin ekonomisi. Parite için Android'deki AdMob kaldırılmalı.
- **iOS karşılığı/kaynak:** (yok — iOS'ta reklam hiç yok)
- **Android mevcut:** HomeActivity: MobileAds.initialize + AdView banner (loadAd). build.gradle: play-services-ads 17.2.0. Manifest: com.google.android.gms.ads.APPLICATION_ID meta-data. Layout'larda AdView (adView) ögeleri.
- **Android teknoloji:** play-services-ads kaldırma; manifest/layout/kod temizliği
- **Adımlar:**
  - HomeActivity/HomeScreen'den MobileAds.initialize ve AdView/AdRequest kodunu kaldır.
  - Layout XML'lerindeki <com.google.android.gms.ads.AdView ...> (activity_home.xml vb.) ögelerini kaldır.
  - AndroidManifest'ten com.google.android.gms.ads.APPLICATION_ID meta-data'sını kaldır (bırakılırsa SDK yoksa init crash).
  - build.gradle'dan implementation 'com.google.android.gms:play-services-ads' bağımlılığını sil.
  - Rewarded/interstitial varsa ilgili alanlarda da kaldır (bu alanda banner odaklı; diğer reklam türleri ExamDetail vb. alanlarında ayrı kalemler).
- **Gradle/bağımlılık:** app/build.gradle: play-services-ads implementation satırı silinir; manifest ads APPLICATION_ID meta-data silinir.
- **Bağımlılık:** HOME-01
- **Tuzaklar:** APPLICATION_ID meta-data'sını manifest'te bırakıp SDK'yı kaldırmak çalışma zamanı crash üretir — ikisini birlikte kaldır. R8 sonrası ads sınıf referansı kalmamalı.
- **Kabul kriteri:** Uygulamada hiçbir banner/reklam gösterilmez, play-services-ads bağımlılığı ve ads meta-data kaldırılmış, uygulama reklam SDK'sı olmadan sorunsuz açılır.

#### `A11Y-01` — Erişilebilirlik — contentDescription, testTag kimlikleri, dokunma hedefleri, TalkBack  _(P2 · efor: M)_
- **Durum (2026-10-28):** ✔️ Kapatıldı (ürün sahibi kararı). Etiketler, başlıklar, gruplama, ≥48dp hedefler ve testTag'ler emülatörde erişilebilirlik ağacıyla doğrulandı (2026-10-24); gerçek cihazda insanla sesli TalkBack turu yapılmadı — sorun görülürse yeni kalem açılır.
- **iOS davranışı:** Her anlamlı öge accessibilityIdentifier taşır (account.sheet, account.editProfile, account.coinBalance, account.phone, account.signOut, account.delete, account.reauth.code/verify/resend/cancel, about.sheet/version/contact/replayOnboarding, state.loading/error/retry/empty, coin.balance, home.list/recents/addExam...). accessibilityLabel/Value/Hint, combine, isHeader, decorative ikonlar hidden, custom action 'Listeden Kaldır'.
- **iOS karşılığı/kaynak:** iOS View'larındaki .accessibilityIdentifier/Label/Value/Hint/addTraits/accessibilityAction
- **Android mevcut:** Yok; erişilebilirlik etiketleri/kimlikleri tanımsız.
- **Android teknoloji:** Compose Modifier.semantics/contentDescription/heading/mergeDescendants, testTag, minimumInteractiveComponentSize, TalkBack
- **Adımlar:**
  - Compose'da Modifier.semantics/contentDescription ile anlamlı etiketler; dekoratif ikonlarda contentDescription=null.
  - testTag ile iOS accessibilityIdentifier paritesi (Espresso/Compose UI test için aynı kimlikler).
  - Dokunma hedefleri min 48x48dp (Material3 varsayılanı); küçük ikon butonlarını minimumInteractiveComponentSize ile garanti et.
  - Başlıklar için semantics { heading() }; birleşik kartlar için mergeDescendants.
  - Coin rozeti/telefon gibi birleşik ögelerde tek okunur etiket ('Coin bakiyen: N', 'Telefon numarası, +90...').
  - Recents kartına custom accessibility action 'Listeden Kaldır'.
  - Dinamik yazı boyutu (fontScale) ve yüksek kontrast ile düzenin bozulmadığını doğrula.
- **Bağımlılık:** ACC-01, ABT-01, DS-02, DS-03, HOME-01, HOME-02
- **Tuzaklar:** testTag'ler release'te de kalmalı (UI testleri için) ya da ayrı kimlik stratejisi. Türkçe etiketler TalkBack'te doğru okunmalı. Büyük yazı boyutunda recents kartı iOS gibi tüm metni göstermeli.
- **Kabul kriteri:** TalkBack ile Hesap/Hakkında/Home gezilebilir; tüm aksiyon ögeleri etiketli ve ≥48dp; UI testleri iOS kimlik paritesindeki testTag'lerle ögeleri bulabiliyor.

---

## Google Play hesabı ve yayın  ·  `release`

#### `REL-01` — Play Store sorunlarını çöz ve uygulamayı Play Store'da yayınla  _(P0 · efor: L)_
- **Durum (2026-10-29):** ✔️ Kapatıldı (ürün sahibi kararı). Hesap aktif, paket adı `com.salticusteam.vifi` korunarak kayıtlı, 17 (3.1.0) üretimde %100 yayında ve mağaza sürümü gerçek cihazda doğrulandı. Politika durumunda kalan 3 uyarı (hedef API 35, hedef API 36, 16 KB) bu sürümle karşılanıyor; Google'ın listeden düşürmesi beklenmeden kapatıldı (düşmezse kanallarda eski etkin sürüm aranır).
- **Durum (2026-10-24):** `buyukeryazilim` geliştirici hesabı ve ViFi dahil tüm uygulamalar 19 Kasım 2024'te Google Play'den kaldırıldı (hesap doğrulaması son tarihe kadar tamamlanmadı; Play Console "İşlem gerekli"). ViFi üretimde etkin değil, yeni sürüm yayınlanamıyor.
- **Durum (2026-10-26):** Kimlik ve telefon doğrulaması tamamlandı, hesap kısıtlaması kalktı. Play App Signing etkin (paket adı korunur); yükleme anahtarı sıfırlandı, yeni anahtar 28 Ekim 2026 16:08 UTC'de geçerli. Beyanlar kaydedildi; kalan engel Android geliştirici doğrulamasında paket adı kaydı (Play imzalı APK gerekiyor). Ayrıntılı ilerleme ROADMAP'teki REL-01 notunda.
- **Durum (2026-10-28):** Uygulama imzalama ve yükleme anahtarının SHA-1/SHA-256'ları Firebase Android uygulamasına eklendi (BE-01 tamam). `google-services.json` yenilenmedi: tazesi işlevsel olarak aynı (yalnız kullanılmayan OAuth istemcileri düşüyor). Play Integrity API bağlandı ve App Check'te sağlayıcı kayıtlı. 19:30'da 17 (3.1.0) dahili teste yayınlandı; Play imzalı APK `apksigner` ile `B2:F6…:75:90` doğrulandı; paket adı Android geliştirici doğrulamasında kayıtlı. Aynı sürüm üretime %100 eklendi ve beyanlar + mağaza girişiyle birlikte 13 değişiklik incelemeye gönderildi (yönetilen yayınlama kapalı); aynı akşam onaylandı, üretimde %100 yayında. Kalan: politika durumundaki eski sorunların temizlenmesi, mağazadan inen sürümde giriş/App Check denemesi, BE-03 enforce sırası.
- **Durum (2026-10-28, smoke test):** Play'den inen 17 (3.1.0) gerçek cihazda denendi: giriş, profil tamamlama, tanıtım, arşiv, kilit sheet'i, Sınav Ekle formu, görsel sınav ve ekran koruması çalıştı; enforce açık Auth/RTDB/Storage geçtiği için Play Integrity sahada geçerli. Kabul kriterindeki "mağazadan indirilen sürümde giriş, arşiv ve App Check" karşılandı. Bulgular QA-01…QA-04, düzeltme sürümü REL-02. Kalan: politika rozeti, BE-03.
- **Neden ayrı kalem:** Release imzası, Play App Signing ve Play Integrity mağaza dağıtımına bağlı. Bu işler hesap durumu çözülmeden yapılamaz; geliştirme ve testler debug imzayla (BE-01 debug izleri kayıtlı) sürer.
- **Adımlar:**
  - Play Console'da hesap doğrulamasını tamamla ve kaldırılan uygulamaların geri alınması için başvur. Olmazsa yeni hesap / paket adı kararını ver (`com.salticusteam.vifi` eski hesaba bağlı kalabilir; Firebase kaydı ve iOS ile paylaşılan proje bu karardan etkilenir).
  - Play App Signing'in kullanılıp kullanılmadığını App integrity sayfasından netleştir; App signing key ve upload key SHA-1/SHA-256'larını Firebase Android uygulamasına ekle (BE-01'in release kısmı). Eski `b7:6a:1b:58…` SHA-1'in hangi anahtar olduğunu doğrula.
  - Güncel `google-services.json`'ı indir, `app/google-services.json` ile değiştir, build + testlerle doğrula.
  - App Check: Play Integrity sağlayıcısını kaydet (App signing SHA-256), Play Integrity API'nin açık olduğunu doğrula; enforce sırasını BE-03'e göre yönet (Firestore en son).
  - Release imza yapılandırması: `keystore.properties` (git-ignored) veya CI sırları; anahtar repoya girmez.
  - `versionName` hizalama kararı (2.5 → 3.x) ve `config/appUpdate/android` başlangıç değerleri (BE-06 / UPD-01).
  - Data Safety formu (FM-20 envanteri), mağaza girişi, kapalı test → üretim yayını.
- **Bağımlılık:** BE-01, BE-03, FM-20
- **Tuzaklar:** Upload key yerine yalnız app signing key (veya tersi) eklenirse mağazadan inen sürümde telefonla giriş çalışmaz. Firestore App Check enforce, Firestore okuyan Android sürümü dağıtılmadan açılmamalı.
- **Kabul kriteri:** Play hesabı aktif; ViFi kapalı testte ve ardından üretimde yayında; mağazadan indirilen sürümde telefonla giriş, arşiv ve App Check sorunsuz.

## Mağaza sürümü saha testi bulguları  ·  `qa`

> 28 Ekim 2026'da Play'den inen 17 (3.1.0) gerçek cihazda baştan sona denendi (REL-01 smoke testi). Ana akışlar çalıştı; aşağıdakiler o turda görülen üç hata, bir iyileştirme ve düzeltme sürümü.

#### `QA-01` — Tanıtım turu sistem çubuklarıyla çakışıyor ("İleri"/"Başla" gezinme çubuğunun altında, üstte gri şerit)  _(P1 · efor: S)_
- **Durum (2026-10-29, tamam):** Planlanan `decorFitsSystemWindows = false` Compose 1.7.5'te tek başına yetmedi: API 31+'da `DialogWrapper` uygulamanın kayan diyalog temasını kullanıyor, pencere `fitInsetsTypes` ile çubukların içine sığdırılıyor; Android 15+'ta (`targetSdk` 35+) `DialogLayout` içeriği yapılandırmadaki tam ekran yüksekliğiyle (2400 px) ölçüp pencereyi o boyuta ayarlıyor, çerçeve ise 63–2337 px — içerik 63 px kayıp gezinme çubuğunun altına taşıyor. Çözüm `EdgeToEdgeDialogWindow`: Android 15+'ta `decorFitsSystemWindows = false`, `fitInsetsTypes = 0`, kesik için `ALWAYS`, `FLAG_DIM_BEHIND` kapalı; ikon rengi tur zeminine göre. Android 15 öncesinde içerik çubuklar arasına göre ölçüldüğünden pencere ayarlarına dokunulmadı (API 24–30'da bayrak Compose 1.7'yi ortalanmış, kayan olmayan pencereye geçirirdi) (buton görünür; durum çubuğu altında karartma kalabilir — cihazda denenmedi). Compose 1.7 pencere genişliğini 1079 px'e yuvarladığı için son sütunda alttaki ekran seçilebilir; büyütmeden görünmüyor.
- **Gözlem:** Gerçek cihazda (hareketle gezinme) ve emülatörde (API 37) aynı: tur açıkken durum çubuğunun altı karartılmış gri bir şerit kalıyor; alttaki birincil butonun metni gezinme tutamacıyla üst üste biniyor, butonun alt kenarı ekrandan taşıyor. İlk açılışta ve Hakkında → "Tanıtım turunu tekrar izle"de görülür.
- **Neden:** `OnboardingScreen` turu `Dialog(usePlatformDefaultWidth = false)` penceresinde gösteriyor. Dialog penceresi `decorFitsSystemWindows = true` (varsayılan) ile sistem çubuklarına göre yerleşmiyor; içerideki `safeDrawingPadding()` boşluk vermiyor.
- **Adımlar:**
  - `DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false, decorFitsSystemWindows = false)`: pencere tam ekran çizilsin, `Surface` durum çubuğunun altını da kaplasın (iOS `fullScreenCover`), mevcut `safeDrawingPadding()` gerçek boşlukları uygulasın.
  - Dialog penceresinde (`LocalView.current.parent as DialogWindowProvider`) `WindowInsetsControllerCompat.isAppearanceLightStatusBars/NavigationBars = !darkTheme`: açık temada çubuk ikonları koyu, karanlıkta açık kalsın.
  - Erişilebilirlik ve geri tuşu davranışı (`onDismissRequest` → Atla) değişmesin; ayrı pencere olarak kalması TalkBack'in arkadaki arşive gitmesini engeller.
- **Bağımlılık:** OB-9
- **Tuzaklar:** `decorFitsSystemWindows = false` iken ikon rengi ayarlanmazsa açık temada beyaz durum çubuğu ikonları beyaz zeminde kaybolur. Overlay'e (Dialog'suz) geçmek insets'i çözer ama arkadaki içerik semantics ağacında kalır.
- **Kabul kriteri:** Hareketle ve 3 tuşlu gezinmede, açık ve karanlık temada buton gezinme çubuğunun üstünde ve tam görünür; üstte gri şerit yok, durum çubuğu ikonları okunur; geri tuşu turu Atla olarak kapatır; ilk açılış ve tekrar izleme aynı.

#### `QA-02` — Breadcrumb ekrana geri dönünce güncel çipi göstermiyor  _(P1 · efor: S)_
- **Durum (2026-10-29, tamam):** `scrollToEndOnceMeasured()` + `BreadcrumbScrollTest` (JVM; korumasız sürümde `expected:<700> but was:<-2147483588>`) + `BreadcrumbBarTest`'e dar iz ve `StateRestorationTester` UI testleri. Simülasyon: 1–3000 px arası geri yüklenen değerlerin 1529'u bozuluyor; emülatörde mock izin değeri (~965 px) güvenli aralıkta olduğundan saha hatası orada yeniden üretilemedi, UI testleri de efekt sırası nedeniyle düzeltmesiz geçiyor — regresyonu JVM testi yakalıyor.
- **Gözlem:** Sınavlar listesinde (5 çipli iz) "Sınav Ekle"ye gidip kapatınca iz artık sonda değil: güncel çip ("DİFERANSİYEL DENKLEMLER") sağda kesik. İlk girişte doğru.
- **Neden:** `BreadcrumbBar`'daki `LaunchedEffect(path, scrollState.maxValue) { scrollState.scrollTo(scrollState.maxValue) }` ilk çizimden önce koşuyor; o anda `maxValue` henüz `Int.MAX_VALUE`. Ekran geri yüklendiğinde `rememberScrollState()` sıfırdan büyük kayıtlı değerle başlar ve `ScrollState`'in `value + round(delta)` toplamı tamsayı taşar (Compose foundation 1.7.5 bytecode'u + JVM simülasyonuyla doğrulandı: kayıtlı 700 → 59). Sonraki `scrollTo(max)` float hassasiyetiyle rastgele bir konuma düşer. İlk girişte değer 0 olduğu için taşma yok.
- **Adımlar:**
  - Kaydırmayı yalnız `maxValue` bilindiğinde yap: `if (scrollState.maxValue != Int.MAX_VALUE) scrollState.scrollTo(scrollState.maxValue)` (ya da `snapshotFlow { maxValue }.first { it != Int.MAX_VALUE }`).
  - Compose UI testi: dar genişlikte derin yol → son çip görünür; `StateRestorationTester.emulateSavedInstanceStateRestore()` sonrası son çip hâlâ görünür.
- **Bağımlılık:** ARCH-15
- **Tuzaklar:** `LaunchedEffect`'in anahtarından `maxValue`'yu çıkarmak ilk girişte izi baştan bırakır (genişlik layout'tan sonra bilinir). Aynı desen başka yatay kaydırmalarda kullanılırsa aynı düzeltme gerekir (şu an yalnız burada).
- **Kabul kriteri:** Sınav Ekle, görüntüleyici, Hesap ve döndürmeden sonra iz her zaman sonda; güncel çip tam görünür; UI testi geçer.

#### `QA-03` — Açık tema: Material3 varsayılan mor nötr/konteyner tonlarını marka uyumlu tam açık şemayla değiştir  _(P2 · efor: M)_
- **Durum (2026-10-29, tamam):** `VifiLightColors` tam şema (beyaz zemin, sıcak nötr kart/sheet, turuncu tonlu konteynerler), `window_background_light` + `values/styles.xml` `windowBackground`; `VifiColorContrastTest` açık tema AA çiftleri, outline ≥ 3:1 ve "mavi baskın ton yok" testi. Emülatörde açık temada Home, breadcrumb, kilit sheet'i, Sınav Ekle, Profil ve Hesap gözden geçirildi.
- **Gözlem:** Açık temada seçili segment (Sınav Ekle "Görsel/PDF", profil "Kadın/Erkek") lila, Sınav Ekle kartı ve breadcrumb çipleri lavanta-gri, kilit sheet'i lavanta, zemin hafif pembe. Turuncu marka rengiyle uyumsuz; karanlık tema bu sorunu taşımıyor.
- **Neden:** `VifiLightColors` yalnız `primary/onPrimary/secondary/onSecondary` tanımlıyor; geri kalan roller Material3 baseline (mor tonlu) değerlerinde kalıyor (`background`, `surfaceVariant`, `secondaryContainer`, `surfaceContainer*`, `outline*`, `tertiary*`).
- **Adımlar:**
  - `VifiDarkColors` gibi tüm rolleri tanımlayan bir açık şema: beyaz/çok açık nötr zemin (iOS `systemBackground`), nötr gri kart dolgusu (iOS `secondarySystemBackground` karşılığı `surfaceVariant`/`surfaceContainer*`), turuncu tonlu `primaryContainer`/`secondaryContainer`, sıcak nötr `outline`/`outlineVariant`.
  - `res/values/styles.xml` `window_background` ve splash zeminini yeni `background` ile hizala (açılışta renk atlaması olmasın).
  - `VifiColorContrastTest`'e açık tema metin/zemin çiftlerini ekle (gövde ≥ 4.5:1, outline ≥ 3:1).
  - Etkilenen ekranları açık temada gözden geçir: Home kartı, breadcrumb, segmentli butonlar, kilit sheet'i, Sınav Ekle, profil, Hesap, Inbox.
- **Bağımlılık:** DS-01
- **Tuzaklar:** `secondaryContainer` seçili segmentin zemini; turuncu tonlu yapılırsa üstündeki metin `onSecondaryContainer` ile kontrastlı kalmalı. `dynamicColor` kapalı kalır. Turuncu üstünde siyah metin kuralı değişmez.
- **Kabul kriteri:** Açık temada mor/lila/pembe ton yok; tüm metin/zemin çiftleri AA; açılışta renk atlaması yok; karanlık tema değişmedi.

#### `QA-04` — Sınav Ekle'yi bir dersten (kilit sheet'i) açınca okul + ders bağlamını ön-doldur  _(P3 · efor: M)_
- **Durum (2026-10-29, tamam):** Ürün sahibi Android'de yapılmasına karar verdi (iOS'ta yok). Rota `AddExam(ids, names)`; kilit sheet'i `EarnCoins(lesson = exam.path.parent)` gönderiyor. Ders arşivden hemen, okul satırları paketli liste okununca (`withArchiveSchool`) yalnız boşsa doluyor: üniversite `ArchiveNameMatch.matches`, fakülte "fakültesi" eki tolere edilerek, bölüm `matchesDepartment` ("BÖLÜMÜ" eki). Eşleşmeyen seviye boş; profil yedeği yalnız bağlamsız (ana ekran) açılışta. Saha örneği (SAKARYA ÜNİVERSİTESİ › MÜHENDİSLİK FAKÜLTESİ › ELEKTRİK ELEKTRONİK MÜHENDİSLİĞİ) paketli listede birebir karşılığını buluyor.
- **Gözlem:** Sakarya › Mühendislik › Elektrik Elektronik › Diferansiyel Denklemler'deki kilit sheet'inden "Sınav Ekle"ye gelince form profildeki okulla (başka üniversite/fakülte/bölüm) doluyor, ders boş.
- **Durum:** Hata değil; iOS da aynı (`AddExamView()` argümansız, `AddExamViewModel` profilden dolduruyor). İyileştirme önerisi.
- **Adımlar:**
  - `ViFiRoute.AddExam`'a opsiyonel bağlam argümanı (arşiv yolu) ekle; kilit sheet'i ve Browse'dan açılınca doldur, Home'dan açılınca profil yedeği.
  - Arşiv düğümünü (catalogNodes slug + ad) paketli kataloğa (AE-01) ad-eşlemeyle bağla; eşleşmeyen seviyede profil/boş seçiciye düş.
  - Ders, arşiv dersiyle doğrudan seçili gelsin (AE-05 ad-eşleme).
- **Bağımlılık:** AE-02, AE-04, AE-05
- **Tuzaklar:** Arşiv adları ile paketli katalog adları birebir aynı olmayabilir (büyük harf, "ÜNİVERSİTESİ" ekleri). Parite: iOS'ta yok, iOS'la birlikte karar verilmeli.
- **Kabul kriteri:** Bir dersten açılan form o okul/fakülte/bölüm/dersle dolu; Home'dan açılan form profille dolu; eşleşmeyen ad güvenli şekilde boş kalır.

#### `REL-02` — Düzeltme sürümü (versionCode 18)  _(P1 · efor: S)_
- **Durum (2026-10-29, kapandı):** ✔️ Kapatıldı (ürün sahibi kararı). 4.0.0 (18) `v-4.0.0` dalında QA-01…QA-04 ile hazır ama yayınlanmadı: 4.0.0'a başka değişiklikler de girecek. Yayın (release AAB, iç test → üretim, mağaza sürümünde kontrol) yalnız ürün sahibi "release çıkalım" dediğinde yapılır; sürüm notları kapsam kesinleşince güncellenir.
- **Durum (2026-10-29):** Sürüm adı **4.0.0** (iOS ile aynı seri), `versionCode 18`; `v-4.0.0` dalında QA-01…QA-03 ile birlikte. Sürüm notları `PLAY-STORE-LISTING.md`'de (240/500 karakter). Kalan: release AAB, iç test → üretim, mağaza sürümünde kontrol.
- **Kapsam:** QA-01, QA-02, QA-03, QA-04 (4.0.0'a başka değişiklikler de girecek). Sürüm adı iOS serisine göre seçilir (iOS 4.0.0'a geçiyor; CLAUDE.md "iOS ile aynı seri").
- **Adımlar:**
  - `versionCode 18` + sürüm adı; derleme, lint, birim + UI testleri.
  - Release AAB'yi yükleme anahtarıyla imzala, iç teste yükle, ardından üretime gönder.
  - Mağazadan inen sürümde tanıtım butonu, breadcrumb dönüşü ve açık tema yeniden kontrol edilir.
- **Bağımlılık:** QA-01, QA-02, QA-03, REL-01
- **Kabul kriteri:** Düzeltme sürümü üretimde; mağaza sürümünde üç bulgu yok.
