package com.salticusteam.vifi.core.di

import com.salticusteam.vifi.core.data.catalog.BundledUniversityCatalog
import com.salticusteam.vifi.core.data.catalog.CatalogSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The bundled catalog is Firebase-free, so both flavors share this binding. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CatalogModule {

    @Binds
    abstract fun bindCatalogSource(impl: BundledUniversityCatalog): CatalogSource
}
