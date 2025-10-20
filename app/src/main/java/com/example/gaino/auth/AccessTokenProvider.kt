package com.example.gaino.auth

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AccessTokenProvider {
    private const val TAG = "AccessTokenProvider"
    private const val SCOPE = "oauth2:https://www.googleapis.com/auth/drive.appdata"

    /** Suspends on IO; returns an access token or null on failure. */
    suspend fun getAccessToken(context: Context): String? = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext null
        val acct = account.account ?: return@withContext null
        try {
            GoogleAuthUtil.getToken(context, acct, SCOPE)
        } catch (e: UserRecoverableAuthException) {
            Log.e(TAG, "User action required for token", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get access token", e)
            null
        }
    }
}
