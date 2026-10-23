package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.Announcement
import kotlinx.coroutines.flow.Flow

/** Reads the public announcements shown in the in-app inbox. */
interface AnnouncementsReader {
    /** Emits the announcements (newest first) and again whenever they change. */
    fun announcementsChanges(): Flow<List<Announcement>>
}
