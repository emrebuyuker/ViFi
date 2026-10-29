# Play Console — Mağaza Girişi ve Uygulama İçeriği

> **Durum:** Metinler ürün sahibince onaylandı (REL-01, 2026-10-28); formları Play Console'da doldurup göndermek ürün
> sahibinin işidir. Veri güvenliği formu için bkz. `PLAY-DATA-SAFETY.md`.
> Görseller repo kökündeki `play-store/` klasöründe: canlı `vifi-831a8`, test hesabı, liveDebug, 28 Ekim 2026.
> **4.0.0 (LEG-03, ürün sahibince onaylandı 2026-10-29):** tam açıklama, sürüm notu ve uygulama içeriği
> cevapları Premium ve parayla tek sınav açma için güncellendi. Console'a **4.0.0 sürümüyle birlikte** girilir; o
> zamana kadar canlıda 3.1.0 metni kalır (önceki metin: `git show 7343c53:PLAY-STORE-LISTING.md`).

## 1. Yayın öncesi sıra

| # | Nerede | Ne | Neden |
|---|---|---|---|
| 1 | Firebase Console → App Check → Uygulamalar → Android (`com.salticusteam.vifi`) | Play Integrity sağlayıcısını kaydet; SHA-256 listesinde `B2:F6…:75:90` olmalı. Zaten kayıtlıysa yalnız kontrol et. | Release sürüm token'ı Play Integrity ile alır; kayıt yoksa Storage/RTDB/Auth isteği 403 döner. |
| 2 | Play Console → ViFi → Uygulama bütünlüğü → Play Integrity API | Cloud projesini bağla: `vifi-831a8` (441043948320). | Play Integrity yanıtları bu projeye bağlanır. |
| 3 | Play Console → Uygulama paketi gezgini | Play'deki en yüksek `versionCode`'u kontrol et (bizde 17). | Eşit ya da daha düşük kod yüklenemez. |
| 4 | Play Console → Uygulama içeriği | §2'deki cevaplar. | Yayın engeli. |
| 5 | Play Console → Mağaza varlığı → Ana mağaza girişi | §3 metinler + §4 görseller. | Yayın engeli. |
| 6 | 28 Ekim 19:08 TRT sonrası | AAB'yi iç teste taslak yükle → Uygulama paketi gezgini'nden Play imzalı universal APK'yı indir → `apksigner verify --print-certs` ile SHA-256'nın `B2:F6…` olduğunu teyit et → APK'yı Android geliştirici doğrulamasına yükle. | "Kayıtlı olmayan paket adı" engeli. ✔️ 28 Ekim 19:30: dahili teste yayınlandı, APK `B2:F6` doğrulandı, paket adı kayıtlı. |
| 7 | Yayın özeti | 17 (3.1.0) üretime %100 (16 (2.5) dahil edilmez) → 13 değişikliği incelemeye gönder. | ✔️ 28 Ekim'de gönderildi ve aynı akşam onaylandı; üretimde %100 yayında. |

## 2. Uygulama içeriği — cevaplar

| Form | Cevap | Not |
|---|---|---|
| Gizlilik politikası | `https://vifi-831a8.web.app/gizlilik` | Firebase Hosting'de yayında (kaynak `ViFi-iOS/firebase/hosting/`). |
| Uygulama erişimi | "Bazı işlevler kısıtlı" → giriş talimatı ekle | Uygulamanın tamamı telefonla (+90) girişin arkasında. İnceleme için Firebase Console'daki **test numarasını ve sabit kodunu** yaz; numara bu dosyaya yazılmaz (sır kuralı). Test hesabının profili tamamlanmış olmalı. Önerilen talimat: _"Uygulama telefon numarasıyla giriş ister. Giriş ekranına \<numara\> yazıp Kod Gönder'e dokunun, kod alanına \<kod\> yazın. Bu bir test numarasıdır, SMS gönderilmez."_ |
| Reklamlar | Hayır, reklam içermiyor | AdMob tamamen kaldırıldı (FM-08). |
| İçerik derecelendirmesi | Kategori: Referans, Haber veya Eğitim. Şiddet, cinsellik, küfür, madde, kumar: Hayır. Kullanıcılar birbiriyle içerik paylaşabiliyor mu: **Evet** (sınav görselleri/PDF'leri; yayından önce incelenir). Konum paylaşımı: Hayır. Dijital satın alma: **Evet** _(4.0.0; 3.1.0'da Hayır)_. | 4.0.0: ViFi Premium aboneliği ve parayla tek sınav açma Google Play Billing ile. Coin gerçek parayla satın alınmaz ve paraya çevrilmez; kumar sayılmaz. Anket 4.0.0 sürümüyle yeniden gönderilir. |
| Hedef kitle ve içerik | 18 yaş ve üstü; uygulama çocuklara hitap etmiyor | 28 Ekim'de Console'da doğrulandı (önceden kayıtlı). 13 yaş altı seçilmemeli (Aile politikası). |
| Haber uygulaması | Hayır | |
| Devlet uygulaması | Hayır | |
| Finans özellikleri | Yok | 26 Ekim'de kaydedildi. Uygulama içi satın alma (4.0.0) finans özelliği sayılmaz; değişmez. |
| Sağlık | Yok | 26 Ekim'de kaydedildi. |
| Veri güvenliği | `PLAY-DATA-SAFETY.md`; hesap silme adresi `https://vifi-831a8.web.app/hesap-silme` | 26 Ekim'de kaydedildi, incelemeye gönderilmedi. 4.0.0 ile "Finansal bilgiler → Satın alma geçmişi" eklenir (LEG-02). |
| Reklam kimliği | Kullanılmıyor | `AD_ID` izinleri birleşik manifest'ten çıkarıldı. |

## 3. Mağaza girişi metinleri

**Uygulama adı** (en çok 30 karakter): `ViFi - Üniversite Sınav Arşivi` — kısa alternatif: `ViFi: Sınav Arşivi`

**Kategori:** Eğitim · **İletişim e-postası:** `buyukeryazilim@gmail.com`

**Kısa açıklama** (en çok 80 karakter):

```
Üniversitelerin geçmiş vize ve final sınavları tek yerde. Ders seç, sınavı aç.
```

**Tam açıklama** (4.0.0, en çok 4000 karakter):

```
ViFi, üniversitelerin geçmiş sınavlarını tek bir arşivde toplar. Üniversiteni, fakülteni, bölümünü ve dersini seç; önceki yılların vize, final ve bütünleme sınavlarını görsel ya da PDF olarak uygulama içinde incele.

SINAVLARI BUL
• Üniversite, fakülte, bölüm ve ders adımlarıyla aradığın sınava kolayca ulaş.
• Her listede arama yaparak sonuçları daralt.
• Son görüntülediğin sınavlara ana sayfadan tek dokunuşla dön.

RAHATÇA İNCELE
• Görsel ve PDF sınavlar tek yerde.
• Sayfaları yakınlaştırarak ayrıntılı incele.
• Açtığın sınavlar cihazında önbelleğe alınır; tekrar açtığında daha hızlı yüklenir.

COIN İLE KİLİT AÇ
• Her derste en eski iki sınav ücretsizdir.
• Diğer sınavlar 100 coin ile açılır ve hesabında kalıcı olarak açık kalır.
• Coin satın alınmaz; onaylanan sınav gönderimleriyle kazanılır.
• İstersen bir sınavı coin yerine tek seferlik ödemeyle de açabilirsin; o sınav da hesabında kalıcı olarak açık kalır.

ViFi PREMIUM
• Aylık ya da yıllık abonelikle arşivdeki tüm sınavlar coin gerekmeden açılır.
• Abonelik dönem sonunda otomatik olarak yenilenir; Google Play abonelik ayarlarından istediğin zaman iptal edebilirsin.

SINAV EKLE, COIN KAZAN
• Elindeki sınavları fotoğraf ya da PDF olarak gönder.
• Gönderimler yayınlanmadan önce incelenir; onaylandığında coin'lerin hesabına yüklenir.
• Gönderim arka planda sürer; sonucu bildirimle öğrenirsin.

DİĞER
• Telefon numaranla (+90) giriş.
• Duyurular için bildirimler ve uygulama içi bildirim kutusu.
• Reklam yok.
• Açık ve koyu tema desteği.

ViFi bağımsız bir uygulamadır; listelenen üniversitelerle resmi bir bağlantısı yoktur. Sınav belgelerini görüntülemek için internet bağlantısı gerekir.

Görüş ve önerilerin için: buyukeryazilim@gmail.com
```

**Sürüm notları** (4.0.0, en çok 500 karakter):

```
ViFi Premium geldi: aylık ya da yıllık abonelikle arşivdeki tüm sınavlar coin gerekmeden açık. İstersen tek bir sınavı coin yerine tek seferlik ödemeyle de açabilirsin. Ayrıca tanıtım turundaki buton artık gezinme çubuğunun altında kalmıyor, arşivde geri dönünce üstteki konum çubuğu bulunduğun yeri gösteriyor ve açık tema renkleri yenilendi.
```

**Sürüm notları** (3.1.0, en çok 500 karakter):

```
ViFi yenilendi: yeni tasarım, telefon numarasıyla giriş, coin ile kilitli sınavları açma, sınav ekleyip coin kazanma, bildirimler ve koyu tema.
```

## 4. Görseller (`play-store/`)

| Dosya | Play alanı | Ölçü / biçim |
|---|---|---|
| `icon-512.png` | Uygulama simgesi | 512×512, 32-bit PNG (iOS 1024 simgesinden; köşeler turuncu, maskeyi Play uygular) |
| `feature-graphic-1024x500.png` | Öne çıkan görsel | 1024×500, 24-bit PNG |
| `01-ana-sayfa.png` | Telefon ekran görüntüsü 1 | 1080×1920 (9:16), 24-bit PNG |
| `02-dersler.png` | Telefon ekran görüntüsü 2 | aynı |
| `03-sinavlar.png` | Telefon ekran görüntüsü 3 | aynı |
| `04-kilit-acma.png` | Telefon ekran görüntüsü 4 | aynı (kilit sayfası; sınav açılmadı) |
| `05-sinav-ekle.png` | Telefon ekran görüntüsü 5 | aynı |
| `06-tanitim.png` | Telefon ekran görüntüsü 6 | aynı (tanıtım turu ilk sayfası) |

Çekim notları:
- Emülatör ekranı çekim için `wm size 1080x1920` yapıldı (1080×2400 ekran Play'in "uzun kenar kısa kenarın en çok 2 katı" sınırını aşıyor), sonra `wm size reset`. Durum çubuğu SystemUI demo moduyla 12:00, tam pil.
- Sınav görüntüleyici ekran koruması (`FLAG_SECURE`) yüzünden ekran görüntüsünde siyah çıkıyor; listede yok.
- Bildirimler ekranı canlıda duyuru olmadığı için boş görünüyor; listede yok.
- Hesap ekranı telefon numarası gösterdiği için çekilmedi.

## 5. Açık kararlar ve riskler

- **Kullanıcı içeriği (UGC) politikası:** ✔️ 28 Ekim 2026'da çözüldü. Kullanım koşulları
  `https://vifi-831a8.web.app/kullanim-kosullari` adresinde yayında (kaynak `ViFi-iOS/firebase/hosting/`). Sınav Ekle'de
  zorunlu onay kutusu var; işaretlenmeden Gönder açılmaz. Hakkında'da Kullanım Koşulları ve Gizlilik Politikası
  bağlantıları var. Gönderimler yayından önce yönetici onayından geçiyor; görüntüleyicideki "Bize Ulaş" şikâyet yolu.
  **Kalan:** aynı onay kutusu iOS'ta yok (parite işi).
- **"Bağımsız uygulama" cümlesi:** Tam açıklamadaki üniversitelerle resmi bağlantı olmadığı cümlesinin doğruluğunu ürün
  sahibi teyit etmeli; üniversite adları geçtiği için yanıltıcı içerik politikası açısından önerilir.
