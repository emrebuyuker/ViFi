package com.salticusteam.vifi.feature.update

import com.salticusteam.vifi.core.model.AppUpdate

/** What the update gate shows: a blocking requirement, a dismissible suggestion, or nothing. */
data class AppUpdateUiState(
    /** The update that blocks the app; while set, the force-update screen replaces all other UI. */
    val required: AppUpdate? = null,
    /** The update to suggest; `null` when there is none, a required update is active, or it was dismissed. */
    val optional: AppUpdate? = null,
)
