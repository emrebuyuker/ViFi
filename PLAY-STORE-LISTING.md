# Play Console — Mağaza Girişi ve Uygulama İçeriği

> **Durum:** Metinler ürün sahibince onaylandı (REL-01, 2026-10-28); formları Play Console'da doldurup göndermek ürün
> sahibinin işidir. Veri güvenliği formu için bkz. `PLAY-DATA-SAFETY.md`.
> Görseller repo kökündeki `play-store/` klasöründe: canlı `vifi-831a8`, test hesabı, liveDebug, 28 Ekim 2026.

## 1. Yayın öncesi sıra

| # | Nerede | Ne | Neden |
|---|---|---|---|
| 1 | Firebase Console → App Check → Uygulamalar → Android (`com.salticusteam.vifi`) | Play Integrity sağlayıcısını kaydet; SHA-256 listesinde `B2:F6…:75:90` olmalı. Zaten kayıtlıysa yalnız kontrol et. | Release sürüm token'ı Play Integrity ile alır; kayıt yoksa Storage/RTDB/Auth isteği 403 döner. |
| 2 | Play Console → ViFi → Uygulama bütünlüğü → Play Integrity API | Cloud projesini bağla: `vifi-831a8` (441043948320). | Play Integrity yanıtları bu projeye bağlanır. |
| 3 | Play Console → Uygulama paketi gezgini | Play'deki en yüksek `versionCode`'u kontrol et (bizde 17). | Eşit ya da daha düşük kod yüklenemez. |
| 4 | Play Console → Uygulama içeriği | §2'deki cevaplar. | Yayın engeli. |
| 5 | Play Console → Mağaza varlığı → Ana mağaza girişi | §3 metinler + §4 görseller. | Yayın engeli. |
| 6 | 28 Ekim 19:08 TRT sonrası | AAB'yi iç teste taslak yükle → Uygulama paketi gezgini'nden Play imzalı universal APK'yı indir → `apksigner verify --print-certs` ile SHA-256'nın `B2:F6…` olduğunu teyit et → APK'yı Android geliştirici doğrulamasına yükle. | "Kayıtlı olmayan paket adı" engeli. |

## 2. Uygulama içeriği — cevaplar

| Form | Cevap | Not |
|---|---|---|
| Gizlilik politikası | `https://vifi-831a8.web.app/gizlilik` | Firebase Hosting'de yayında (kaynak `ViFi-iOS/firebase/hosting/`). |
| Uygulama erişimi | "Bazı işlevler kısıtlı" → giriş talimatı ekle | Uygulamanın tamamı telefonla (+90) girişin arkasında. İnceleme için Firebase Console'daki **test numarasını ve sabit kodunu** yaz; numara bu dosyaya yazılmaz (sır kuralı). Test hesabının profili tamamlanmış olmalı. Önerilen talimat: _"Uygulama telefon numarasıyla giriş ister. Giriş ekranına \<numara\> yazıp Kod Gönder'e dokunun, kod alanına \<kod\> yazın. Bu bir test numarasıdır, SMS gönderilmez."_ |
| Reklamlar | Hayır, reklam içermiyor | AdMob tamamen kaldırıldı (FM-08). |
| İçerik derecelendirmesi | Kategori: Referans, Haber veya Eğitim. Şiddet, cinsellik, küfür, madde, kumar: Hayır. Kullanıcılar birbiriyle içerik paylaşabiliyor mu: **Evet** (sınav görselleri/PDF'leri; yayından önce incelenir). Konum paylaşımı: Hayır. Dijital satın alma: Hayır. | Coin gerçek parayla satın alınmaz ve paraya çevrilmez; kumar sayılmaz. |
| Hedef kitle ve içerik | **Karar bekliyor.** Öneri: 18 ve üzeri. | Hedef üniversite öğrencileri. 13 yaş altı seçilmemeli (Aile politikası). |
| Haber uygulaması | Hayır | |
| Devlet uygulaması | Hayır | |
| Finans özellikleri | Yok | 26 Ekim'de kaydedildi. |
| Sağlık | Yok | 26 Ekim'de kaydedildi. |
| Veri güvenliği | `PLAY-DATA-SAFETY.md`; hesap silme adresi `https://vifi-831a8.web.app/hesap-silme` | 26 Ekim'de kaydedildi, incelemeye gönderilmedi. |
| Reklam kimliği | Kullanılmıyor | `AD_ID` izinleri birleşik manifest'ten çıkarıldı. |

## 3. Mağaza girişi metinleri

**Uygulama adı** (en çok 30 karakter): `ViFi - Üniversite Sınav Arşivi` — kısa alternatif: `ViFi: Sınav Arşivi`

**Kategori:** Eğitim · **İletişim e-postası:** `buyukeryazilim@gmail.com`

**Kısa açıklama** (en çok 80 karakter):

```
Üniversitelerin geçmiş vize ve final sınavları tek yerde. Ders seç, sınavı aç.
```

**Tam açıklama** (en çok 4000 karakter):

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
• Uygulamada satın alma yoktur; coin'ler onaylanan sınav gönderimleriyle kazanılır.

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

- **Kullanıcı içeriği (UGC) politikası:** Play, kullanıcıların içerik yüklediği uygulamalarda yüklemeden önce kullanım
  koşullarının kabul edilmesini, uygunsuz içeriğin tanımlanmasını ve uygulama içi şikâyet yolunu ister. ViFi'de
  gönderimler yayından önce yönetici onayından geçiyor (MOD-*). Sınav görüntüleyicideki "Bize Ulaş" düğmesi sınavın
  konumunu dolduran bir e-posta açıyor; bu bir şikâyet yolu sayılabilir. **Eksik:** Sınav Ekle'de kullanım koşullarını
  kabul adımı ve yayınlanmış bir kullanım koşulları sayfası yok (iOS'ta da yok). Karar ürün sahibinde.
- **Hedef kitle:** §2'deki yaş grubu seçimi.
- **"Bağımsız uygulama" cümlesi:** Tam açıklamadaki üniversitelerle resmi bağlantı olmadığı cümlesinin doğruluğunu ürün
  sahibi teyit etmeli; üniversite adları geçtiği için yanıltıcı içerik politikası açısından önerilir.
