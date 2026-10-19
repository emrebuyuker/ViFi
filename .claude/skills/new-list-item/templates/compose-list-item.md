# ViFi-Android — Compose Liste Öğesi Template'i (birincil / hedef)

Bu, bir `LazyColumn`/`LazyRow` içinde tek bir satır/kart çizen **stateless** bir item Composable'ıdır.
iOS `CollectionViewCell`/`TableViewCell` karşılığıdır, ama Android'de item **reuse/dequeue/prepareForReuse
yoktur** — Compose recomposition'ı bunu kendi halleder. Yeni/ileriye dönük tüm ekranlarda **bu** template
kullanılır; `RecyclerView` ViewHolder'ı yalnız legacy XML ekranlar içindir (bkz.
`.claude/skills/new-list-item/templates/recyclerview-item.md`).

> ⚠️ **Tipografi kuralı (zorunlu):** Metinlerde font `MaterialTheme.typography.<stil>`'den gelir
> (marka tipografisi `core/designsystem/theme/Type.kt`). Composable'a `fontSize = 16.sp` / `fontWeight = ...` gömme.
> iOS'un `configureScaledFont` + Dynamic Type zinciri burada otomatiktir: Material3 metin stilleri
> cihaz yazı ölçeğini kendi uygular — elle ölçek kodu yazma. Kanonik referans: `.claude/rules/ui-components.md` (Typography).

> ⚠️ **Renk kuralı (zorunlu):** Renkler `MaterialTheme.colorScheme.<rol>`'den (ör. `surface`, `onSurface`,
> `primary`, `outline`), marka sabitleri `core/designsystem/theme/Color.kt`'ten gelir. Hardcoded `Color(0xFF...)` **YASAK**
> (iOS `Colors.<name>` karşılığı). Kanonik referans: `.claude/rules/ui-components.md` (Color & Theme).

> ⚠️ **Stateless/callback kuralı (zorunlu):** Item **state tutmaz**: tek bir immutable domain `data class` +
> `onClick: () -> Unit` (ve gerekiyorsa başka lambda) alır. Veri yukarıdan (`uiState`) iner, tıklama yukarı
> (ViewModel) çıkar — iOS `configure(with:)` + delegate'in yerine **state aşağı / event yukarı**. Item içinde
> `remember { mutableStateOf(...) }` ile kalıcı veri tutma; seçim/expand gibi durum `uiState`'tedir.
> Kanonik referans: `.claude/rules/viewmodel-rule.md`.

> ⚠️ **String kuralı (zorunlu):** Kullanıcıya görünen her sabit metin `stringResource(R.string.x)`; kod içine
> Türkçe/İngilizce string gömme **YASAK**. `contentDescription` dahil tüm metinler strings.xml'de, positional
> arg'larla (`%1$s`). Kanonik referans: `.claude/rules/localization.md`.

> ⚠️ **testTag kuralı (zorunlu):** Kök `Modifier`'a `testTag("<ekran>.<öğe>")` ver; bu, UI testleriyle
> **sözleşmedir** (iOS accessibility identifier karşılığı) — var olan tag'leri değiştirme. Ayrıca satırın
> anlamlı bir `contentDescription`'ı olsun (`Modifier.semantics { ... }` ya da `Text`'in doğal metni).
> Kanonik referans: `.claude/rules/ui-components.md` (Accessibility) ve `.claude/rules/testing.md`.

`{Name}` → PascalCase (ör. `Exam`), `{name}` → camelCase (ör. `exam`), `{feature}` → lowercase feature
(ör. `examlist`). Paket her zaman `com.salticusteam.vifi`.

## Template

```kotlin
// feature/examlist/ExamListItem.kt
package com.salticusteam.vifi.feature.examlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.model.Exam
import com.salticusteam.vifi.core.util.DateDisplayFormat
import com.salticusteam.vifi.core.util.toDisplayDate
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme

/**
 * Single exam row for [androidx.compose.foundation.lazy.LazyColumn].
 * Stateless: takes the domain model + an onClick callback. No reuse/prepareForReuse —
 * Compose handles recomposition. State lives in the ViewModel's UiState.
 */
@Composable
fun ExamListItem(
    exam: Exam,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Accessibility: one clear label for the whole row (iOS accessibilityLabel equivalent).
    val rowDescription = stringResource(R.string.examlist_item_cd, exam.title)

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            // testTag is the UI-test contract — keep stable, do not rename.
            .testTag("examlist.item.${exam.id}")
            .semantics { contentDescription = rowDescription },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_exam),
                contentDescription = null, // row already described; icon is decorative
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = exam.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // Date: central helper only — never build a formatter here (see date-handling.md).
                Text(
                    text = exam.createdAt.toDisplayDate(DateDisplayFormat.DottedDate).orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// MARK equivalent in Kotlin: // --- Preview --- (section comments, not // MARK:)
@Preview(showBackground = true)
@Composable
private fun ExamListItemPreview() {
    ViFiTheme {
        ExamListItem(
            exam = Exam(
                id = "1",
                title = "Analize Giriş — 2023 Final",
                courseId = "math101",
                createdAt = "2026-05-28T12:36:26+00:00"
            ),
            onClick = {}
        )
    }
}
```

İlgili domain model `core/model/`'de yaşar (ham Firebase tipi değil; bkz. `.claude/rules/firebase-data.md`):

```kotlin
// core/model/Exam.kt
package com.salticusteam.vifi.core.model

/** Plain domain model. Repository maps DTO (DataSnapshot/DocumentSnapshot) -> this. */
data class Exam(
    val id: String,
    val title: String,
    val courseId: String,
    val createdAt: String,          // ISO 8601 backend timestamp (raw string in the model)
    val updatedAt: String? = null,  // Added in 3.1.0 — for "last updated" ordering.
)
```

## Temel Özellikler

### ✅ ViFi Compose desenleri

1. **Stateless item** — domain `data class` + `onClick` lambda (reuse/dequeue YOK, `prepareForReuse` YOK).
2. **Material3 `Card(onClick = ...)`** — tıklama ripple + erişilebilirlik dahili (elle `clickable` + `role` gerekmez).
3. **Renk** — `MaterialTheme.colorScheme.*` / `core/designsystem/theme/Color.kt` (hardcoded `Color(0x...)` yok).
4. **Tipografi** — `MaterialTheme.typography.*` / `core/designsystem/theme/Type.kt` (ölçek otomatik).
5. **String** — `stringResource(R.string.*)`, `contentDescription` dahil.
6. **testTag** — `"<ekran>.item.<id>"` UI testi sözleşmesi.
7. **`Modifier` parametresi** — çağıran dışarıdan padding/tag verebilsin diye **son parametre**, default `Modifier`.
8. **Tarih** — `toDisplayDate(...)` merkezi helper'ı (item içinde formatter kurma yok; bkz. `date-handling.md`).
9. **`@Preview`** — `ViFiTheme` ile sarılı, ViFi domain örnek verisi (iOS'ta olmayan ama Compose'da zorunlu say).

## Tasarım Rehberi

### Renkler

```kotlin
// Card surface + text roles — from colorScheme
containerColor = MaterialTheme.colorScheme.surface
color = MaterialTheme.colorScheme.onSurface            // title
color = MaterialTheme.colorScheme.onSurfaceVariant     // subtitle / chevron
tint = MaterialTheme.colorScheme.primary               // icon accent

// For a brand-specific color, from core/designsystem/theme/Color.kt (not hardcoded):
// import com.salticusteam.vifi.core.designsystem.theme.ViFiBrandGreen
```

### Tipografi

```kotlin
style = MaterialTheme.typography.titleMedium   // title (~16sp equivalent)
style = MaterialTheme.typography.bodyMedium    // subtitle (~14sp)
style = MaterialTheme.typography.labelSmall    // badge / small label (~12sp)
```

### Elevation / köşe / şekil

```kotlin
elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
shape = MaterialTheme.shapes.medium            // corner radius from theme (iOS cornerRadius equivalent)
```

## testTag (UI testi sözleşmesi)

Desen: `"<ekran>.<öğe>"` ya da satır başına `"<ekran>.item.<id>"`. iOS accessibility identifier'ının
birebir karşılığı; UI testleri bu tag'lere bağlıdır — **var olanı değiştirme**.

```
examlist.item.<id>     // tek satır
examlist.item.title    // (tek öğe test ediliyorsa) başlık metni
examlist.list          // LazyColumn'un kendisi (content Composable'da verilir)
```

```kotlin
// In a UI test (createAndroidComposeRule + onNodeWithTag):
composeRule.onNodeWithTag("examlist.item.1").performClick()
```

## Kullanım — `LazyColumn` içinde

Item, ekranın **stateless content** Composable'ı içinde `items(...)` ile çizilir. Liste state'i
ViewModel'den gelir; tıklama ViewModel'in public fonksiyonuna çıkar (bkz. `.claude/rules/viewmodel-rule.md`,
`.claude/skills/new-screen/SKILL.md`).

```kotlin
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

@Composable
fun ExamList(
    exams: List<Exam>,
    onExamClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.testTag("examlist.list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // key = exam.id -> stable identity (iOS diffable data source equivalent; prevents needless recomposition).
        items(items = exams, key = { it.id }) { exam ->
            ExamListItem(
                exam = exam,
                onClick = { onExamClick(exam.id) }
            )
        }
    }
}
```

> **MANDATORY:** `items(..., key = { it.id })` daima kararlı bir kimlikle verilir — iOS'ta cell register/dequeue
> neyse, Compose'da `key` odur: listede ekleme/çıkarma/yeniden sıralamada doğru item'ı korur ve gereksiz
> yeniden çizimi engeller.

## Varyasyonlar

### İkonsuz (sade satır)

`Icon`'u kaldır, `Column`'u doğrudan `Row`'un tek çocuğu yap (`horizontalArrangement` kalkar).

### Seçim (çoklu seçim / checkbox)

Seçim durumu **item'da tutulmaz** — `uiState.selectedExamIds: Set<String>`'ten iner, toggle yukarı çıkar
(bkz. `viewmodel-rule.md` → Selection durumu).

```kotlin
@Composable
fun ExamListItem(
    exam: Exam,
    isSelected: Boolean,            // Added in 3.1.0 — multi-select mode.
    onClick: () -> Unit,
    onToggleSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("examlist.item.${exam.id}"),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Row(/* ... */) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier.testTag("examlist.item.${exam.id}.checkbox")
            )
            // ... content
        }
    }
}
```

### Rozet (durum etiketi)

```kotlin
// Small label for a pending-approval exam, etc.:
Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    shape = MaterialTheme.shapes.small
) {
    Text(
        text = stringResource(R.string.examlist_item_badge_pending),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
```

### Yatay liste (`LazyRow`)

Aynı item Composable'ı `LazyRow`'da da kullanılır (ör. üniversite/fakülte kartları yatay şerit). Yalnız
genişliği sabitle:

```kotlin
LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    items(universities, key = { it.id }) { university ->
        UniversityListItem(
            university = university,
            onClick = { onUniversityClick(university.id) },
            modifier = Modifier.width(160.dp)   // fixed width in a horizontal list
        )
    }
}
```

## Liste öğesinde tarih string'leri

Backend tarihleri item'a ISO 8601 string olarak gelir; **render anında** merkezi helper ile çevrilir —
item içinde `SimpleDateFormat`/`DateTimeFormatter` **kurma** (bkz. `.claude/rules/date-handling.md`).

```kotlin
// ✅ CORRECT
Text(text = exam.createdAt.toDisplayDate(DateDisplayFormat.DottedDate).orEmpty())  // "28.05.2026"
Text(text = exam.createdAt.toDisplayDate(DateDisplayFormat.TimeOnly).orEmpty())    // "12:36"

// ❌ WRONG — never build a formatter inside an item
val sdf = SimpleDateFormat("dd.MM.yyyy", Locale("tr"))   // FORBIDDEN
```

## Önemli notlar

⚠️ **YAPMA:**
- Item'da `remember { mutableStateOf(...) }` ile kalıcı domain verisi tutma (state ViewModel'de).
- Hardcoded `Color(0x...)`, `fontSize`/`fontWeight` ya da kod içi sabit string.
- Elle `Modifier.clickable` + manuel `Role` — `Card(onClick=)` / `ListItem`'in dahili erişilebilirliğini kullan.
- `items(...)`'ı `key` olmadan bırakma.

✅ **YAP:**
- Stateless item: `data class` + lambda; `Modifier` son parametre, default `Modifier`.
- `MaterialTheme.colorScheme` / `typography`, `core/designsystem/theme/Color.kt` / `Type.kt`.
- `stringResource` + `contentDescription`, kararlı `testTag`.
- Tarihi `toDisplayDate(...)` ile çevir.
- `@Preview`'i `ViFiTheme` ile sar.

---

**Ayrıca bkz.:**
- `.claude/skills/new-list-item/SKILL.md` — liste öğesi oluşturma akışı ve hangi template ne zaman
- `.claude/skills/new-list-item/templates/recyclerview-item.md` — legacy RecyclerView ViewHolder (yalnız XML ekranlar)
- `.claude/rules/ui-components.md` — Compose/Material3, renk/tipografi/erişilebilirlik kuralları
- `.claude/rules/viewmodel-rule.md` — state aşağı / event yukarı, seçim durumu
- `.claude/rules/firebase-data.md` — DTO → domain map (item ham Firebase tipi görmez)
- `.claude/rules/localization.md` — strings.xml, `stringResource`, positional arg
- `.claude/rules/date-handling.md` — backend ISO 8601 → `toDisplayDate`
- `.claude/rules/testing.md` — Compose UI testi (`onNodeWithTag`, `performClick`)
