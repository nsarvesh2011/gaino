package com.example.gaino

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gaino.auth.Scopes
import com.example.gaino.ui.theme.GainoTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException

class MainActivity : ComponentActivity() {

    private lateinit var gso: GoogleSignInOptions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Google Sign-In with drive.appdata scope
        gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scopes.DRIVE_APPDATA)
            .build()

        setContent {
            GainoTheme {
                SignInScreen(
                    isSignedIn = GoogleSignIn.getLastSignedInAccount(this) != null,
                    onSignIn = { launchSignIn() },
                    onSignOut = { signOut() }
                )
            }
        }

        GoogleSignIn.getLastSignedInAccount(this)?.let { onSignedIn(it) }
    }

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            onSignedIn(account)
            Toast.makeText(this, "Sign-in success", Toast.LENGTH_SHORT).show()
        } catch (e: ApiException) {
            val code = e.statusCode
            val name = GoogleSignInStatusCodes.getStatusCodeString(code)
            Log.e("GainoSignIn", "Sign-in failed: $code ($name)", e)
            Toast.makeText(this, "Sign-in failed: $code ($name)", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchSignIn() {
        val client = GoogleSignIn.getClient(this, gso)
        signInLauncher.launch(client.signInIntent)
    }

    private fun onSignedIn(account: GoogleSignInAccount?) {
        // We’ll fetch an access token & call Drive later.
        // For now, simply recompose the UI to show “Signed in”.
        setContent {
            GainoTheme {
                SignInScreen(
                    isSignedIn = account != null,
                    onSignIn = { launchSignIn() },
                    onSignOut = { signOut() }
                )
            }
        }
    }

    private fun signOut() {
        val client = GoogleSignIn.getClient(this, gso)
        client.signOut().addOnCompleteListener {
            setContent {
                GainoTheme {
                    SignInScreen(
                        isSignedIn = false,
                        onSignIn = { launchSignIn() },
                        onSignOut = { signOut() }
                    )
                }
            }
        }
    }
}

@Composable
private fun SignInScreen(
    isSignedIn: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isSignedIn) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Signed in ✅")
                Spacer(Modifier.height(12.dp))
                Button(onClick = onSignOut) { Text("Sign out") }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Gaino")
                Spacer(Modifier.height(12.dp))
                Button(onClick = onSignIn) { Text("Sign in with Google") }
            }
        }
    }
}
