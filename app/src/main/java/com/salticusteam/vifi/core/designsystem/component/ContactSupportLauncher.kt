package com.salticusteam.vifi.core.designsystem.component

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import com.salticusteam.vifi.BuildConfig
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.util.ContactSupport

/**
 * Opens the prefilled "Bize Ulaş" e-mail (iOS `contactSupport` modifier). Shared by every entry point (login,
 * About, the exam viewers); when no mail app exists, [showNoMailDialog] turns on and [ContactSupportDialog]
 * offers to copy the address instead.
 */
class ContactSupportLauncher internal constructor(
    private val context: Context,
    private val noMailDialog: MutableState<Boolean>,
) {
    var showNoMailDialog: Boolean
        get() = noMailDialog.value
        private set(value) {
            noMailDialog.value = value
        }

    /** Opens the mail app; [supportContext] is the line prefilled above the app/device footer (e.g. the exam). */
    fun open(supportContext: String? = null) {
        val uri = ContactSupport.mailtoUri(
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            androidVersion = "${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}",
            context = supportContext,
        )
        try {
            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse(uri)))
        } catch (e: ActivityNotFoundException) {
            showNoMailDialog = true
        }
    }

    internal fun dismissNoMailDialog() {
        showNoMailDialog = false
    }
}

/** Remembers a [ContactSupportLauncher]; render [ContactSupportDialog] once next to it. */
@Composable
fun rememberContactSupportLauncher(): ContactSupportLauncher {
    val context = LocalContext.current
    val noMailDialog = rememberSaveable { mutableStateOf(false) }
    return remember(context, noMailDialog) { ContactSupportLauncher(context, noMailDialog) }
}

/** Shown when no app can open the support e-mail: the address to write to, with a copy action. */
@Composable
fun ContactSupportDialog(launcher: ContactSupportLauncher) {
    if (!launcher.showNoMailDialog) return
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = launcher::dismissNoMailDialog,
        title = { Text(stringResource(R.string.contact_support_no_mail_title)) },
        text = { Text(stringResource(R.string.contact_support_no_mail_message, ContactSupport.EMAIL)) },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(ContactSupport.EMAIL))
                    launcher.dismissNoMailDialog()
                },
                modifier = Modifier.testTag("contactSupport.copyAddress"),
            ) {
                Text(stringResource(R.string.contact_support_copy_address_button))
            }
        },
        dismissButton = {
            TextButton(onClick = launcher::dismissNoMailDialog) { Text(stringResource(R.string.common_ok)) }
        },
    )
}
