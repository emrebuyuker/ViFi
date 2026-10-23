package com.salticusteam.vifi.app

import androidx.lifecycle.ViewModel
import com.salticusteam.vifi.core.notifications.InboxDeepLink
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Hands the singleton [InboxDeepLink] to composables. */
@HiltViewModel
class DeepLinkViewModel @Inject constructor(val inboxDeepLink: InboxDeepLink) : ViewModel()
