package com.primalsword.priore

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging

class PrioreApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeFirebase()
    }

    private fun initializeFirebase() {
        if (!firebaseConfigReady()) {
            Log.w(TAG, "Firebase configuration is missing; push notifications are disabled")
            return
        }

        if (FirebaseApp.getApps(this).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                .build()
            FirebaseApp.initializeApp(this, options)
        }

        FirebaseMessaging.getInstance()
            .subscribeToTopic(BuildConfig.FIREBASE_TOPIC)
            .addOnSuccessListener { Log.i(TAG, "Subscribed to ${BuildConfig.FIREBASE_TOPIC}") }
            .addOnFailureListener { Log.e(TAG, "FCM topic subscription failed", it) }
    }

    companion object {
        private const val TAG = "PrioreApp"

        fun firebaseConfigReady(): Boolean = listOf(
            BuildConfig.FIREBASE_APP_ID,
            BuildConfig.FIREBASE_API_KEY,
            BuildConfig.FIREBASE_PROJECT_ID,
            BuildConfig.FIREBASE_SENDER_ID,
        ).all { it.isNotBlank() }
    }
}
