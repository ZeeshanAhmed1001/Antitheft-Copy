package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform

/**
 * Handles Google User Messaging Platform (UMP) consent information and forms.
 * Compliant with GDPR, CCPA, and Google Play policies.
 */
object ConsentManager {

    private const val TAG = "ConsentManager"

    private var consentInformation: ConsentInformation? = null

    /**
     * Helper variable to check if ads can be requested based on consent status.
     */
    fun canRequestAds(context: Context): Boolean {
        val consentInfo = consentInformation ?: UserMessagingPlatform.getConsentInformation(context)
        return consentInfo.canRequestAds()
    }

    /**
     * Checks if privacy options (e.g., Privacy / Consent settings button in menu) are required.
     */
    fun isPrivacyOptionsRequired(context: Context): Boolean {
        val consentInfo = consentInformation ?: UserMessagingPlatform.getConsentInformation(context)
        return consentInfo.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    /**
     * Gathers consent from the user if required.
     */
    fun gatherConsent(
        activity: Activity,
        onConsentGatheringComplete: (error: FormError?) -> Unit
    ) {
        val consentInfo = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation = consentInfo

        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInfo.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error: ${formError.errorCode} - ${formError.message}")
                    } else {
                        Log.d(TAG, "Consent gathering completed successfully")
                    }
                    onConsentGatheringComplete(formError)
                }
            },
            { requestConsentError ->
                Log.w(TAG, "Consent info update failed: ${requestConsentError.errorCode} - ${requestConsentError.message}")
                onConsentGatheringComplete(requestConsentError)
            }
        )
    }

    /**
     * Shows the privacy options form so users can update their consent choices.
     */
    fun showPrivacyOptionsForm(
        activity: Activity,
        onComplete: (error: FormError?) -> Unit
    ) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) {
                Log.w(TAG, "Privacy options form error: ${error.message}")
            }
            onComplete(error)
        }
    }
}
