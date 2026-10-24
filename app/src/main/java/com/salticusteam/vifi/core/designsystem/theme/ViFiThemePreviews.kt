package com.salticusteam.vifi.core.designsystem.theme

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Light + dark preview of a screen (the app follows the system theme). The dark background matches
 * VifiDarkColors.background so the preview canvas does not show through as white.
 */
@Preview(name = "Açık", showBackground = true)
@Preview(
    name = "Koyu",
    showBackground = true,
    backgroundColor = 0xFF141311,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
annotation class ViFiThemePreviews
