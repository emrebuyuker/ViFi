package com.salticusteam.vifi.core.model

import com.salticusteam.vifi.core.util.Slug
import kotlinx.serialization.Serializable

// Added in 2.5 — one entry of a bundled university / faculty / department list (assets/catalog/*.json).
/** A stable canonical [id] (used for notification segments / FCM topics) and the [name] shown to the user. */
@Serializable
data class CatalogItem(
    val id: String,
    val name: String,
)

// Added in 2.5 — the value a profile picker holds: a listed item or free text the user typed (iOS CatalogSelection).
data class CatalogSelection(
    val name: String,
    val id: String,
    val isCustom: Boolean,
) {
    val isEmpty: Boolean get() = name.isEmpty()

    companion object {
        val EMPTY = CatalogSelection(name = "", id = "", isCustom = false)

        /** A value picked from the bundled list (canonical id). */
        fun catalog(item: CatalogItem) = CatalogSelection(name = item.name, id = item.id, isCustom = false)

        /** A value the user typed that is not on the list (best-effort slug id, flagged custom). */
        fun custom(text: String): CatalogSelection {
            val trimmed = text.trim()
            return CatalogSelection(name = trimmed, id = Slug.make(trimmed), isCustom = true)
        }

        /** Rebuilds a selection from stored profile fields (the edit screen's prefill). */
        fun stored(name: String, id: String, isCustom: Boolean) = CatalogSelection(name, id, isCustom)
    }
}
