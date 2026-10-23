package com.salticusteam.vifi.core.model

// Added in 2.5 — errors of opening a downloaded exam file in a viewer.
/** UI shows only [userMessage]; it is inline Turkish (the narrow exception to the strings.xml rule). */
sealed class ExamFileError(val userMessage: String) : Exception(userMessage) {

    /** The bytes are not a PDF the renderer can open. */
    data object UnreadablePdf : ExamFileError("PDF belgesi açılamadı. Dosya bozuk olabilir.")

    /** The bytes are not an image that can be decoded. */
    data object UndecodableImage : ExamFileError("Sayfa görüntüsü açılamadı. Lütfen daha sonra tekrar dene.")

    /** The downloaded file could not be written to the device. */
    data object WriteFailed : ExamFileError("Dosya cihaza kaydedilemedi. Boş alanını kontrol edip tekrar dene.")
}
