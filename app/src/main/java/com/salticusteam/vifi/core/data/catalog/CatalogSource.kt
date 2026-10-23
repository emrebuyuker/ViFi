package com.salticusteam.vifi.core.data.catalog

/** Supplies the university / faculty / department lists behind the profile pickers. */
interface CatalogSource {
    suspend fun catalog(): UniversityCatalog
}
