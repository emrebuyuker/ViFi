package com.salticusteam.vifi.feature.home

import com.salticusteam.vifi.core.model.RecentExam

// Added in 2.5 — state of the home screen's "recently viewed" shelf (iOS HomeView / RecentExamsSection parity).
/** [recents] is newest first; the shelf is hidden when empty. [isClearConfirmationShown] drives the clear dialog. */
data class HomeUiState(
    val recents: List<RecentExam> = emptyList(),
    val isClearConfirmationShown: Boolean = false,
)
