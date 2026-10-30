package com.salticusteam.vifi.feature.inbox

import androidx.lifecycle.ViewModel
import com.salticusteam.vifi.core.data.inbox.InboxStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** The unread count behind the toolbar bell: server-side, the same on every device. */
@HiltViewModel
class InboxBadgeViewModel @Inject constructor(store: InboxStore) : ViewModel() {
    val unreadCount: StateFlow<Int> = store.unreadCount
}
