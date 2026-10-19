# ViFi-Android — RecyclerView Liste Öğesi Template'i (ikincil / legacy)

> ⚠️ **Legacy template — yalnız XML ekranlarda.** Bu template `RecyclerView` + `ViewHolder` + `ListAdapter`
> (DiffUtil) içindir ve **sadece** XML tabanlı (henüz Compose'a taşınmamış) bir ekrana öğe eklerken kullanılır.
> **Yeni/ileriye dönük ekran asla XML + RecyclerView ile yazılmaz** — orada birincil template geçerlidir:
> `.claude/skills/new-list-item/templates/compose-list-item.md` (`LazyColumn` item Composable). Mümkünse ekranı
> önce Compose'a taşı; bu dosyayı yalnız geçiş bitene kadar var olan XML ekrana dokunmak zorundaysan kullan.
>
> **Legacy not:** Bugünkü v2.5 kodu daha da eski bir desende: `ListView` + `BaseAdapter`
> (`CustomAdapter`/`ListViewAdapter` → `custom_layout.xml`), her `getView`'da `findViewById` ve reuse yok.
> `RecyclerView` + `ListAdapter`/DiffUtil + `ViewBinding` bunun **bir adım ilerisi**, nihai hedef ise Compose.
> Göçün yol haritasındaki yeri: bkz. repo kökü `ANDROID-PARITY-ROADMAP.md` (UI mimarisi).

`{Name}` → PascalCase (ör. `Exam`), `{name}` → camelCase, `{feature}` → lowercase feature. Paket
`com.salticusteam.vifi`. Renk/metin/tarih kuralları Compose'daki ile **aynıdır**: hardcoded renk/string yok,
tarih merkezi helper'dan.

## 1) Item layout (XML)

```xml
<!-- res/layout/item_exam.xml -->
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/examItemCard"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginHorizontal="16dp"
    android:layout_marginVertical="4dp"
    app:cardCornerRadius="12dp"
    app:cardElevation="2dp"
    app:cardBackgroundColor="?attr/colorSurface">

    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="12dp">

        <ImageView
            android:id="@+id/examIcon"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:importantForAccessibility="no"
            android:src="@drawable/ic_exam"
            app:tint="?attr/colorPrimary"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toTopOf="parent"
            app:layout_constraintBottom_toBottomOf="parent" />

        <TextView
            android:id="@+id/examTitle"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:maxLines="2"
            android:ellipsize="end"
            android:textAppearance="?attr/textAppearanceTitleMedium"
            android:textColor="?attr/colorOnSurface"
            app:layout_constraintStart_toEndOf="@id/examIcon"
            app:layout_constraintEnd_toStartOf="@id/examChevron"
            app:layout_constraintTop_toTopOf="parent"
            android:layout_marginStart="12dp" />

        <TextView
            android:id="@+id/examDate"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:maxLines="1"
            android:ellipsize="end"
            android:textAppearance="?attr/textAppearanceBodyMedium"
            android:textColor="?attr/colorOnSurfaceVariant"
            app:layout_constraintStart_toStartOf="@id/examTitle"
            app:layout_constraintEnd_toEndOf="@id/examTitle"
            app:layout_constraintTop_toBottomOf="@id/examTitle" />

        <ImageView
            android:id="@+id/examChevron"
            android:layout_width="20dp"
            android:layout_height="20dp"
            android:importantForAccessibility="no"
            android:src="@drawable/ic_chevron_right"
            app:tint="?attr/colorOnSurfaceVariant"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintTop_toTopOf="parent"
            app:layout_constraintBottom_toBottomOf="parent" />
    </androidx.constraintlayout.widget.ConstraintLayout>
</com.google.android.material.card.MaterialCardView>
```

> Renk: XML'de de hardcoded hex **yok** — tema attribute'ları (`?attr/colorSurface`, `?attr/colorOnSurface`)
> kullan (Compose `MaterialTheme.colorScheme` karşılığı). Tipografi: `?attr/textAppearanceTitleMedium`
> (`MaterialTheme.typography.titleMedium` karşılığı). Dekoratif ikonlar `importantForAccessibility="no"`.

## 2) ViewHolder + ListAdapter (DiffUtil + ViewBinding)

```kotlin
// feature/examlist/legacy/ExamAdapter.kt
package com.salticusteam.vifi.feature.examlist.legacy

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.model.Exam
import com.salticusteam.vifi.core.util.DateDisplayFormat
import com.salticusteam.vifi.core.util.toDisplayDate
import com.salticusteam.vifi.databinding.ItemExamBinding

/**
 * Legacy RecyclerView adapter for XML screens only. New screens use the Compose
 * list item instead (see compose-list-item.md). ListAdapter + DiffUtil is the
 * diffable equivalent of iOS's diffable data source; no manual notifyDataSetChanged().
 */
class ExamAdapter(
    private val onExamClick: (String) -> Unit
) : ListAdapter<Exam, ExamAdapter.ExamViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExamViewHolder {
        val binding = ItemExamBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ExamViewHolder(binding, onExamClick)
    }

    override fun onBindViewHolder(holder: ExamViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ExamViewHolder(
        private val binding: ItemExamBinding,
        private val onExamClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentExam: Exam? = null

        init {
            // Bind the click once in init (not per bind) to avoid leaking listeners.
            binding.root.setOnClickListener {
                currentExam?.let { onExamClick(it.id) }
            }
        }

        fun bind(exam: Exam) {
            currentExam = exam
            binding.examTitle.text = exam.title
            // Date via central helper only — never build a formatter in a ViewHolder.
            binding.examDate.text =
                exam.createdAt.toDisplayDate(DateDisplayFormat.DottedDate).orEmpty()
            // testTag equivalent for XML/Espresso: a stable contentDescription / tag on the row.
            binding.root.contentDescription =
                binding.root.context.getString(R.string.examlist_item_cd, exam.title)
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Exam>() {
            override fun areItemsTheSame(old: Exam, new: Exam) = old.id == new.id
            override fun areContentsTheSame(old: Exam, new: Exam) = old == new
        }
    }
}
```

> `ListAdapter` + `DiffUtil` **zorunlu** — eski `BaseAdapter.notifyDataSetChanged()`/elle `List` yönetimi
> **kullanılmaz**. `areItemsTheSame` kimlik (`id`), `areContentsTheSame` içerik eşitliği içindir; domain
> `data class` olduğu için içerik karşılaştırması bedavadır. Click listener `init`'te **bir kez** bağlanır,
> `bind`'da güncel item `currentExam` üzerinden okunur (her `bind`'da yeni lambda oluşturup sızdırma).

## 3) Kullanım (Fragment/Activity — legacy XML ekran)

```kotlin
// In a legacy XML screen (e.g. CourseExamListFragment):
private val examAdapter = ExamAdapter(onExamClick = { examId ->
    viewModel.onExamClick(examId)   // MVVM: click bubbles up to the ViewModel (business logic not in the Fragment)
})

override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding.examRecyclerView.apply {
        layoutManager = LinearLayoutManager(requireContext())
        adapter = examAdapter
        // Optional: setHasFixedSize(true) if the row height is fixed.
    }

    // Collect the StateFlow lifecycle-aware; hand it to DiffUtil via submitList (no notifyDataSetChanged).
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiState.collect { state ->
                examAdapter.submitList(state.exams)
            }
        }
    }
}
```

> Legacy ekranda bile **ViewModel + StateFlow** hedef mimariye bağlı kalır: liste `submitList(...)` ile
> güncellenir, iş mantığı Fragment'ta tutulmaz. Bu, ekranı daha sonra Compose'a taşımayı kolaylaştırır —
> ViewModel değişmeden kalır, yalnız view katmanı `ExamListScreen` + `ExamListItem`'a döner. Aynı
> `ExamAdapter`'ın iki kopyası projede bulunmaz; Compose'a taşındıktan sonra bu adapter **silinir**.

## Önemli notlar

⚠️ **YAPMA:**
- Yeni/ileriye dönük ekranı XML + RecyclerView ile yazma (Compose kullan).
- `BaseAdapter`/`ListView` ya da elle `notifyDataSetChanged()` (legacy v2.5 deseni — kullanma, `ListAdapter`+DiffUtil kullan).
- Her `onBindViewHolder`'da yeni click lambda'sı oluşturma (listener sızıntısı); `init`'te bağla.
- ViewHolder'da `SimpleDateFormat`/`DateTimeFormatter` kurma (merkezi helper; bkz. `date-handling.md`).
- XML'de hardcoded hex renk / sabit metin; tema attribute + `@string/...` kullan.

✅ **YAP:**
- `RecyclerView` + `ListAdapter<T, VH>` + `DiffUtil.ItemCallback` + `ViewBinding`.
- Tıklamayı ViewModel'e lambda ile çıkar (MVVM); state'i `submitList` ile ver.
- `@string/...` + `contentDescription`, tema attribute renkleri.
- Tarihi `toDisplayDate(...)` ile çevir.
- İlk fırsatta ekranı Compose'a taşıyıp bu adapter'ı sil.

---

**Ayrıca bkz.:**
- `.claude/skills/new-list-item/SKILL.md` — liste öğesi oluşturma akışı ve hangi template ne zaman
- `.claude/skills/new-list-item/templates/compose-list-item.md` — birincil (hedef) Compose item Composable'ı
- `.claude/rules/ui-components.md` — renk/tipografi/erişilebilirlik (XML attribute ↔ Compose eşleştirmesi)
- `.claude/rules/viewmodel-rule.md` — legacy ekranda bile ViewModel + StateFlow
- `.claude/rules/date-handling.md` — backend ISO 8601 → `toDisplayDate`
- `.claude/rules/testing.md` — RecyclerView UI testi (Espresso) ve göç sonrası Compose testi
