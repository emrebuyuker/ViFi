# ViFi-Android — PR Review Çıktı Formatı

ViFi-Android projesindeki tüm PR review'ları için bu yapıyı birebir kullan. Dosya atıfları **Android yolları**dır (ör. `feature/examlist/ExamListViewModel.kt`, satır `N`) — `.swift` yok, `ViewController`/`View`/`SnapKit` gibi iOS terimleri yok.

```markdown
# PR Review: #{number} - {title}

## Özet
**Değişen Dosya**: {count} | **Eklenen**: {additions} | **Silinen**: {deletions}

{PR'ın ne yaptığına dair 1-3 cümle}

## Mimari Uyumu

| Kontrol | Durum | Not |
|---------|-------|-----|
| Stateful Screen + stateless Content ayrımı | ✅/⚠️/❌ | XScreen state toplar, XContent sadece state + lambda |
| @HiltViewModel ViewModel | ✅/⚠️/❌ | androidx.lifecycle.ViewModel + @Inject constructor, base class yok |
| StateFlow<UiState> + Channel event | ✅/⚠️/❌ | MutableStateFlow + asStateFlow(); delegate yok |
| Tek immutable data class UiState | ✅/⚠️/❌ | isLoading/error alanları (proje tek deseni) |
| Repository soyutlaması | ✅/⚠️/❌ | interface + Firebase...Repository + Fake...Repository |
| Ham Firebase tipi sızmıyor | ✅/⚠️/❌ | DataSnapshot/DocumentSnapshot UI'ya ulaşmıyor |
| Navigation-Compose | ✅/⚠️/❌ | NavHost + route; Intent/startActivity yok (yeni ekran) |
| Paket/klasör yapısı | ✅/⚠️/❌ | feature/<ad>/ + core/ |

## ViewModel Uyumu

| Kontrol | Durum | Not |
|---------|-------|-----|
| @HiltViewModel + constructor injection | ✅/⚠️/❌ | Hilt ile bağlı, base class yok |
| StateFlow kapsüllenmiş | ✅/⚠️/❌ | private MutableStateFlow + asStateFlow() |
| Tek seferlik olay Channel/SharedFlow | ✅/⚠️/❌ | Navigate/snackbar event; state'te değil |
| viewModelScope + Dispatchers.IO | ✅/⚠️/❌ | GlobalScope yok; IO arka planda |
| Task.await() / callbackFlow | ✅/⚠️/❌ | kotlinx-coroutines-play-services; awaitClose var |
| isLoading her dalda kapatılıyor | ✅/⚠️/❌ | Hata dalı dahil (spinner askıda kalmıyor) |
| ViewModel'de Context/UI tipi yok | ✅/⚠️/❌ | @ApplicationContext istisnası |

## UI (Compose) Uyumu

| Kontrol | Durum | Not |
|---------|-------|-----|
| Stateless Content previewlenebilir | ✅/⚠️/❌ | hiltViewModel/viewModel içermiyor |
| MaterialTheme.colorScheme.* | ✅/⚠️/❌ | Hardcoded Color(0xFF...) yok |
| MaterialTheme.typography.* | ✅/⚠️/❌ | Hardcoded fontSize/TextStyle yok |
| stringResource(R.string.x) | ✅/⚠️/❌ | Gömülü sabit string yok; Türkçe strings.xml |
| LazyColumn/LazyVerticalGrid + key | ✅/⚠️/❌ | Stabil key; uzun listede verticalScroll yok |
| Geri = TopAppBar navigationIcon | ✅/⚠️/❌ | Ikon-only; metinli/elle geri yok |
| testTag + contentDescription | ✅/⚠️/❌ | <ekran>.<öğe> deseni; Türkçe contentDescription |

## Bellek & Yaşam Döngüsü

| Kontrol | Durum | Not |
|---------|-------|-----|
| Coroutine viewModelScope'ta | ✅/⚠️/❌ | GlobalScope yok |
| callbackFlow awaitClose | ✅/⚠️/❌ | Listener kaldırılıyor |
| LaunchedEffect/DisposableEffect key | ✅/⚠️/❌ | Doğru key + onDispose temizliği |
| collectAsStateWithLifecycle | ✅/⚠️/❌ | collectAsState kullanılmamış |
| ViewModel'de Context/Activity yok | ✅/⚠️/❌ | UI referansı tutulmuyor |
| !! / unsafe cast yok | ✅/⚠️/❌ | ?./?:/checkNotNull kullanılmış |

## Veri Erişimi

| Kontrol | Durum | Not |
|---------|-------|-----|
| Repository interface | ✅/⚠️/❌ | UI/ViewModel doğrudan Firebase görmüyor |
| Firebase...Repository (canlı) | ✅/⚠️/❌ | DTO→domain map, hata→ArchiveError |
| Fake...Repository (preview/test) | ✅/⚠️/❌ | Deterministik sample data |
| Hilt @Binds/@Provides | ✅/⚠️/❌ | interface ↔ impl; named vifi Firestore |
| Türkçe userMessage (sealed error) | ✅/⚠️/❌ | UI error.userMessage gösteriyor |

## Performans

| Kontrol | Durum | Not |
|---------|-------|-----|
| Ana thread'de IO yok | ✅/⚠️/❌ | Dispatchers.IO + await()/callbackFlow |
| Recomposition kontrolü | ✅/⚠️/❌ | stable parametre / remember / derivedStateOf |
| LazyColumn key (+ contentType) | ✅/⚠️/❌ | Stabil key var |
| Coil image boyutlama | ✅/⚠️/❌ | Thumbnail için tam çözünürlük yok |
| Gereksiz allocation yok | ✅/⚠️/❌ | Hot path'te her recomposition'da yeni nesne yok |

## Test

| Kontrol | Durum | Not |
|---------|-------|-----|
| ViewModel birim testi | ✅/⚠️/❌ | runTest + StandardTestDispatcher + Turbine |
| Fake repository kullanımı | ✅/⚠️/❌ | Gerçek Firebase yok |
| Compose UI testi | ✅/⚠️/❌ | createAndroidComposeRule + onNodeWithTag |
| testTag tabanlı erişim | ✅/⚠️/❌ | onNodeWithText (lokalizasyona bağlı) yok |
| Açıklayıcı test adı | ✅/⚠️/❌ | `fun \`loadExams success ...\`` |

## Lokalizasyon

| Kontrol | Durum | Not |
|---------|-------|-----|
| stringResource(R.string.x) | ✅/⚠️/❌ | Gömülü sabit string yok |
| Türkçe strings.xml | ✅/⚠️/❌ | Kullanıcı metni Türkçe |
| Positional format arg | ✅/⚠️/❌ | %1$s / %1$d (numaralı) |

## Dosya-Dosya İnceleme

### 🔴 Kritik Sorunlar

#### {dosya yolu}
- **Tür**: Screen / Content / ViewModel / UiState / Repository / Model / Navigation / Test / Diğer
- **Sorunlar**:
  - **Satır {N}**: {kritik sorunun açıklaması}
  - **Düzeltme**:
    ```kotlin
    // ❌ Wrong
    {mevcut kod}

    // ✅ Correct
    {önerilen kod}
    ```

### 🟡 Uyarılar

#### {dosya yolu}
- **Tür**: Screen / Content / ViewModel / Repository / Diğer
- **Sorunlar**:
  - **Satır {N}**: {uyarı açıklaması}
  - **Öneri**: {nasıl iyileştirilir}

### ✅ İyi Uygulamalar

#### {dosya yolu}
- Stateful/stateless ayrımı doğru
- StateFlow + Channel deseni doğru
- testTag sözleşmesine uygun

## Ayrıntılı Bulgular

### Mimari Sorunları

#### 🔴 Kritik
1. **{dosya}** - Satır {N}
   - **Sorun**: Delegate protokolü kullanılmış (iOS deseni); ViFi'de UI iletişimi StateFlow + Channel
   - **Etki**: Hedef mimariye aykırı, test/preview zorlaşır
   - **Düzeltme**:
     ```kotlin
     // ❌ Wrong
     interface ExamListViewModelDelegate { fun didSuccessLoad() }

     // ✅ Correct
     data class ExamListUiState(val exams: List<Exam> = emptyList(), val isLoading: Boolean = false, val error: String? = null)
     val uiState: StateFlow<ExamListUiState> = _uiState.asStateFlow()
     ```

2. **{dosya}** - Satır {N}
   - **Sorun**: Ham DocumentSnapshot UI'ya sızıyor
   - **Etki**: UI Firebase'e bağımlı, test edilemez
   - **Düzeltme**:
     ```kotlin
     // ❌ Wrong
     suspend fun getExams(lessonId: String): List<DocumentSnapshot>

     // ✅ Correct
     suspend fun getExams(lessonId: String): List<Exam> // repository maps DTO→domain
     ```

#### 🟡 Uyarılar
1. **{dosya}** - Satır {N}
   - **Sorun**: Eksik testTag
   - **Etki**: Compose UI testi node'u bulamaz
   - **Düzeltme**:
     ```kotlin
     IconButton(onClick = onBack, modifier = Modifier.testTag("examList.backButton")) { ... }
     ```

### UI Sorunları

#### 🔴 Kritik
1. **{dosya}** - Satır {N}
   - **Sorun**: Hardcoded renk/tipografi/string
   - **Etki**: Tema/dark mode tutarsızlığı, çeviri eksikliği
   - **Düzeltme**:
     ```kotlin
     // ❌ Wrong
     Text("Sınavlar", color = Color(0xFF2571F9), fontSize = 20.sp)

     // ✅ Correct
     Text(stringResource(R.string.exam_list_title), style = MaterialTheme.typography.titleLarge)
     ```

### Bellek Sorunları

#### 🔴 Kritik
1. **{dosya}** - Satır {N}
   - **Sorun**: callbackFlow awaitClose'suz (listener sızar) / GlobalScope
   - **Etki**: Bellek sızıntısı, iptal edilemez iş
   - **Düzeltme**:
     ```kotlin
     // ✅ Correct
     callbackFlow {
         val registration = ref.addSnapshotListener { s, _ -> trySend(s?.toExams().orEmpty()) }
         awaitClose { registration.remove() }
     }.flowOn(Dispatchers.IO)
     ```

### Performans Sorunları

#### 🔴 Kritik
1. **{dosya}** - Satır {N}
   - **Sorun**: LazyColumn key yok / Composable'da her recomposition'da pahalı iş
   - **Etki**: Jank, bozuk recycle/animasyon
   - **Düzeltme**:
     ```kotlin
     // ❌ Wrong
     items(uiState.exams) { exam -> ExamListItem(exam) }

     // ✅ Correct
     items(items = uiState.exams, key = { it.id }) { exam -> ExamListItem(exam, onClick = { onExamClick(exam) }) }
     ```

## Genel Değerlendirme

### Onay Durumu
- [ ] ✅ **Onaylandı** - Merge'e hazır
- [ ] ⚠️ **Yorumlarla onaylandı** - Küçük sorunlar; düzeltildikten sonra merge edilebilir
- [ ] ❌ **Değişiklik istendi** - Kritik sorunlar düzeltilmeli

### Öncelikli Sorunlar (Merge Öncesi Düzeltilmeli)

1. **Bellek Sızıntıları** ({count} örnek)
   - awaitClose'suz callbackFlow / GlobalScope
   - Dosyalar: {liste}

2. **Mimari İhlalleri** ({count} örnek)
   - Delegate protokolü / ham Firebase sızıntısı / stateless content'te ViewModel
   - Dosyalar: {liste}

3. **Hardcoded Değerler** ({count} örnek)
   - Color(0xFF...) / fontSize / gömülü string
   - Dosyalar: {liste}

4. **Eksik testTag/Erişilebilirlik** ({count} örnek)
   - testTag / contentDescription eksik
   - Dosyalar: {liste}

### Küçük Öneriler (İyi Olur)

1. **Test Kapsamı**
   - ViewModel için Fake repository + Turbine testi
   - Kritik akışlar için Compose UI testi

2. **Dokümantasyon**
   - `// region` section yorumları
   - Karmaşık iş mantığına açıklama

3. **Kod Kalitesi**
   - Magic number → isimli sabit
   - Yorumlanmış kodu kaldır
   - Versioned doc comment (`// Added in <versionName> — ...`)

## Sonraki Adımlar

### Yazar İçin
1. Tüm 🔴 **Kritik** sorunları düzelt
2. Mümkünse 🟡 **Uyarıları** gider
3. **Küçük Önerileri** değerlendir
4. Değişiklik sonrası yeniden review iste

### Reviewer İçin
1. Kritik sorunların çözüldüğünü doğrula
2. Yeni sorun girmiş mi kontrol et
3. Onayla veya ek değişiklik iste

## Öneriler

### Hemen Yapılacaklar
- [ ] Bellek sızıntılarını düzelt (awaitClose / viewModelScope)
- [ ] Delegate/ham Firebase sızıntısını StateFlow + Repository ile değiştir
- [ ] Hardcoded renk/tipografi/string'i colorScheme/typography/strings.xml ile değiştir
- [ ] Eksik testTag/contentDescription ekle

### Gelecek İyileştirmeler
- [ ] ViewModel birim testleri (Turbine)
- [ ] Kritik akışlar için Compose UI testleri
- [ ] Hata eşlemeyi iyileştir (FirebaseFirestoreException → ArchiveError)
- [ ] Versioned doc comment'leri tamamla

## Kod Örnekleri Özeti

### ✅ Bu PR'daki İyi Örnekler
```kotlin
// Good StateFlow + event pattern
private val _uiState = MutableStateFlow(ExamListUiState())
val uiState: StateFlow<ExamListUiState> = _uiState.asStateFlow()

// Good memory management
callbackFlow {
    val registration = ref.addSnapshotListener { s, _ -> trySend(s?.toExams().orEmpty()) }
    awaitClose { registration.remove() }
}.flowOn(Dispatchers.IO)

// Good UI style
Text(stringResource(R.string.exam_list_title), style = MaterialTheme.typography.titleLarge)
```

### ❌ Bu PR'daki Sorunlar
```kotlin
// Problem: delegate protocol (iOS pattern)
interface ExamListViewModelDelegate { fun didSuccessLoad() }

// Problem: callbackFlow without awaitClose (leak)
callbackFlow { ref.addSnapshotListener { s, _ -> trySend(map(s)) } }

// Problem: hardcoded values
Text("Sınavlar", color = Color(0xFF2571F9), fontSize = 20.sp)
```

---

**Review Tamamlandı**: {date}
**Reviewer**: {name}
**Sonraki Adım**: {Yazar, yeniden review öncesi kritik sorunları gidermeli}
```

## Durum Emoji Kılavuzu

| Emoji | Anlam | Gereken Aksiyon |
|-------|-------|-----------------|
| ✅ | Uyumlu | Yok - hazır |
| ⚠️ | Küçük sorun / öneri | Opsiyonel - düzeltmek iyi olur |
| ❌ | İhlal — düzeltilmeli | **Gerekli** - bloklayan sorun |
| N/A | Bu PR'a uygulanmaz | Yok |
| 🔴 | Kritik sorun | **Gerekli** - merge öncesi düzelt |
| 🟡 | Uyarı | Düzeltilmesi önerilir |

## Sorun Şiddet Seviyeleri

### 🔴 Kritik (Düzeltilmeli)
- Bellek sızıntıları (awaitClose'suz callbackFlow, GlobalScope)
- `!!` / unsafe cast production kodunda
- Mimari ihlalleri (delegate protokolü, ham Firebase sızıntısı, stateless content'te ViewModel)
- Hardcoded renk/tipografi/string
- Eksik testTag/contentDescription
- Ana thread'de blocking IO
- LazyColumn key eksikliği / Composable'da ağır iş

### 🟡 Uyarı (Düzeltilmeli)
- Eksik testler
- Yorumlanmış kod
- Magic number
- Eksik dokümantasyon / section yorumu
- Eksik versioned doc comment
- Optimize edilmemiş performans

### ✅ İyi Uygulama (Aksiyon Yok)
- ViFi hedef desenlerine uygun
- Doğru bellek/coroutine yönetimi
- İyi adlandırma (`XScreen`/`XContent`/`XViewModel`/`XUiState`)
- Tam test kapsamı

## Reviewer İçin Hızlı Kontrol Listesi

Onaylamadan önce doğrula:
- [ ] Delegate protokolü yok (StateFlow + Channel)
- [ ] BaseViewModel/BaseActivity yok
- [ ] Repository soyutlaması var; ham Firebase tipi sızmıyor
- [ ] Coroutine viewModelScope'ta; callbackFlow awaitClose
- [ ] collectAsStateWithLifecycle; LaunchedEffect doğru key
- [ ] Geri = TopAppBar navigationIcon + popBackStack
- [ ] MaterialTheme.colorScheme.* ve typography.* kullanılmış
- [ ] testTag + contentDescription var
- [ ] Gömülü string yok (stringResource + Türkçe strings.xml)
- [ ] !! / unsafe cast yok
- [ ] LazyColumn/LazyVerticalGrid stabil key
- [ ] Ana thread'de IO yok (Dispatchers.IO)

---

**Ayrıca bkz.:**
- `SKILL.md` — PR review akışı
- `checklists/architecture.md` — Tam mimari/UI/bellek/test checklist'i
- `checklists/performance.md` — Performans checklist'i
- `.claude/rules/architecture.md` — ViFi hedef mimari genel bakış
