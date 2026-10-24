package com.salticusteam.vifi.core.data.submission

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.salticusteam.vifi.core.notifications.SubmissionNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs one attempt of a queued submit ([SubmissionUploader]) and reports the outcome: the failure reason goes to
 * the output data (the open form shows it), and a notification tells a user who is not watching the form.
 */
@HiltWorker
class SubmissionUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val uploader: SubmissionUploader,
    private val notifier: SubmissionNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val draftId = inputData.getString(KEY_DRAFT_ID) ?: return Result.failure()
        return when (val outcome = uploader.upload(draftId, isLastAttempt = runAttemptCount + 1 >= MAX_ATTEMPTS)) {
            SubmissionUploadOutcome.Succeeded -> {
                notifier.notifySent(draftId)
                Result.success()
            }
            SubmissionUploadOutcome.Retry -> Result.retry()
            is SubmissionUploadOutcome.Failed -> {
                notifier.notifyFailed(draftId, outcome.error)
                Result.failure(workDataOf(KEY_FAILURE_REASON to outcome.error.analyticsReason))
            }
        }
    }

    /** Below Android 12 an expedited job runs as a foreground service, which needs this ongoing notification. */
    override suspend fun getForegroundInfo(): ForegroundInfo = notifier.progressForegroundInfo()

    internal companion object {
        const val KEY_DRAFT_ID = "draftId"
        const val KEY_FAILURE_REASON = "failureReason"

        /** Attempts (stops included) before a retryable failure is given up: about an hour of exponential backoff. */
        const val MAX_ATTEMPTS = 8
    }
}
