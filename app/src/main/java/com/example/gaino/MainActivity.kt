package com.example.gaino

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.gaino.auth.Scopes
import com.example.gaino.portfolio.PortfolioUiState
import com.example.gaino.portfolio.PortfolioViewModel
import com.example.gaino.ui.theme.GainoTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var gso: GoogleSignInOptions
    private val vm: PortfolioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Google Sign-In with drive.appdata scope
        gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scopes.DRIVE_APPDATA)
            .build()

        // Default screen (pre sign-in)
        setContent {
            GainoTheme {
                SignInScreen(
                    isSignedIn = GoogleSignIn.getLastSignedInAccount(this) != null,
                    onSignIn = { launchSignIn() },
                    onSignOut = { signOut() }
                )
            }
        }

        // If already signed in, switch to portfolio and load
        GoogleSignIn.getLastSignedInAccount(this)?.let { onSignedIn(it) }
    }

    // --- Sign-in flow ---

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

    private fun onSignedIn(account: GoogleSignInAccount?) {
        // Swap to Portfolio screen
        setContent {
            GainoTheme {
                PortfolioScreen(
                    state = vm,
                    onAddLot = { vm.addLot("NSE:INFY", 1.0, 100.0) }, // temp stub; editable UI next step
                    onSignOut = { signOut() }
                )
            }
        }

        // Kick off a load (remote→cache or cache fallback)
        vm.load()

        // (Optional) Keep your previous ensureAndRead log for sanity once:
        lifecycleScope.launch {
            try {
                val store = com.example.gaino.drive.PortfolioStore(this@MainActivity)
                val json = store.load() // returns Portfolio; safe to log
                Log.d("Gaino", "Loaded portfolio (once): $json")
            } catch (t: Throwable) {
                Log.e("Gaino", "Drive bootstrap failed", t)
                Toast.makeText(
                    this@MainActivity,
                    "Drive init failed: ${t.javaClass.simpleName}: ${t.message ?: ""}",
                    Toast.LENGTH_LONG
                ).show()
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
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
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

@Composable
private fun PortfolioScreen(
    state: PortfolioViewModel,
    onAddLot: () -> Unit,
    onSignOut: () -> Unit
) {
    val ui: PortfolioUiState by state.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Gaino — Portfolio", modifier = Modifier.padding(bottom = 12.dp))

            when {
                ui.isLoading -> {
                    Text("Loading…")
                }
                ui.error != null -> {
                    Text("Error: ${ui.error}")
                }
                ui.holdings.isEmpty() -> {
                    Text("No holdings yet. Add one!")
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(ui.holdings) { h ->
                            Column {
                                Text(h.symbol)
                                Text("Qty: ${"%.2f".format(h.qty)}")
                                Text("Avg: ${"%.2f".format(h.avgCost)}   Last: ${"%.2f".format(h.lastPrice)}")
                                Text(
                                    "P&L: ${"%.2f".format(h.pnlAbs)}  (${String.format("%.2f", h.pnlPct)}%)",
                                    textAlign = TextAlign.Start
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = { state.load() }) { Text("Refresh prices") }

            Spacer(Modifier.height(12.dp))
            Button(onClick = onAddLot) { Text("Add Lot (INFY x1 @ 100)") }

            Spacer(Modifier.height(8.dp))
            Button(onClick = onSignOut) { Text("Sign out") }
        }
    }
}
