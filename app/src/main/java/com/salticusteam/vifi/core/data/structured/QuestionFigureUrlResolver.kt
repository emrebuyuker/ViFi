package com.salticusteam.vifi.core.data.structured

import coil.ImageLoader
import com.salticusteam.vifi.core.model.QuestionFigure
import com.salticusteam.vifi.core.util.StorageUrl
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Turns a question figure into the URL its image is requested from (4.2.0, iOS `AppEnvironment.figureURL`).
 *
 * The figure's `storagePath` is used as stored, never built by the app; the URL is the canonical, token-free Storage
 * download URL, and the shared exam [ImageLoader]'s client adds the user's ID token and App Check header (no
 * `token=`). `null` when the figure cannot be addressed: the card then shows "Şekil yüklenemedi".
 */
fun interface QuestionFigureUrlResolver {
    fun url(figure: QuestionFigure): String?
}

/** Canonical download URLs in [bucket] (live: the app's bucket; mock: the sample bucket its interceptor answers). */
class StorageQuestionFigureUrlResolver(private val bucket: String) : QuestionFigureUrlResolver {
    override fun url(figure: QuestionFigure): String? = StorageUrl.canonical(figure.storagePath, bucket)
}

/** Composables reach the figure URLs and the shared exam image loader through this (no constructor to inject into). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface QuestionFigureEntryPoint {
    fun questionFigureUrlResolver(): QuestionFigureUrlResolver

    fun examImageLoader(): ImageLoader
}
