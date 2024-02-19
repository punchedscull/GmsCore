/*
 * SPDX-FileCopyrightText: 2023 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.feedback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.google.android.gms.common.Feature
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.internal.ConnectionInfo
import com.google.android.gms.common.internal.GetServiceRequest
import com.google.android.gms.common.internal.IGmsCallbacks
import com.google.android.gms.feedback.ErrorReport
import com.google.android.gms.feedback.FeedbackOptions
import com.google.android.gms.feedback.internal.IFeedbackService
import org.microg.gms.BaseService
import org.microg.gms.common.Constants
import org.microg.gms.common.GmsService
import org.microg.gms.feedback.ui.FeedbackAlohaActivity

private const val TAG = "FeedbackService"

class FeedbackService : BaseService(TAG, GmsService.FEEDBACK) {

    override fun handleServiceRequest(callback: IGmsCallbacks, request: GetServiceRequest, service: GmsService) {
        Log.d(TAG, "handleServiceRequest start ")
        callback.onPostInitCompleteWithConnectionInfo(
            CommonStatusCodes.SUCCESS,
            FeedbackServiceImpl(this),
            ConnectionInfo().apply {
                features = arrayOf(Feature("new_send_silent_feedback", 1))
            })
    }

}

class FeedbackServiceImpl(private val context: Context) : IFeedbackService.Stub() {
    override fun startFeedbackFlow(errorReport: ErrorReport): Boolean {
        Log.d(TAG, "startFeedbackFlow: ")
        startFeedbackActivity(errorReport)
        return false
    }

    override fun silentSendFeedback(errorReport: ErrorReport): Boolean {
        Log.d(TAG, "Not impl silentSendFeedback: ")
        return false
    }

    override fun saveFeedbackDataAsync(bundle: Bundle, id: Long) {
        Log.d(TAG, "Not impl saveFeedbackDataAsync: ")
    }

    override fun saveFeedbackDataAsyncWithOption(options: FeedbackOptions, bundle: Bundle, id: Long) {
        Log.d(TAG, "Not impl saveFeedbackDataAsyncWithOption: $options")
    }

    override fun startFeedbackFlowAsync(errorReport: ErrorReport, id: Long) {
        Log.d(TAG, "startFeedbackFlowAsync errorReport:$errorReport")
        startFeedbackActivity(errorReport)
    }

    override fun isValidConfiguration(options: FeedbackOptions): Boolean {
        Log.d(TAG, "Not impl isValidConfiguration: $options")
        return false
    }

    private fun startFeedbackActivity(errorReport: ErrorReport) {
        Log.d(TAG, "startFeedbackActivity start ")
        val intent = Intent(context, FeedbackAlohaActivity::class.java).apply {
            val packages = context.packageManager.getPackagesForUid(getCallingUid())
            packages?.let { putExtra(Constants.KEY_PACKAGE_NAME, it.first()) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

}
