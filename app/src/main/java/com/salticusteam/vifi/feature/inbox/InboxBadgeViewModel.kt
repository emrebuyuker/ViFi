package com.salticusteam.vifi.feature.inbox

import androidx.lifecycle.ViewModel
import com.salticusteam.vifi.core.data.announcements.AnnouncementsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** The unread count behind the toolbar bell. */
@HiltViewModel
class InboxBadgeViewModel @Inject constructor(store: AnnouncementsStore) : ViewModel() {
    val unreadCount: StateFlow<Int> = store.unreadCount
}
