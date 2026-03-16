package com.iotmonitor.reverselink.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.iotmonitor.R;
import com.iotmonitor.filetransfer.EmailAuthClient;

/**
 * Screen: "Connect to PC via Email"
 *
 * - User types the email address they're signed in with on both devices.
 * - App broadcasts UDP on WiFi to find the PC automatically.
 * - Calls /reverselink/discover to get a JWT token.
 * - Stores baseUrl + token in SharedPreferences for use by ReverseLinkClient.
 * - Navigates to the file browser on success.
 */
public class PairWithEmailActivity extends AppCompatActivity {

    public static final String PREFS_NAME   = "reverselink_prefs";
    public static final String KEY_BASE_URL = "base_url";
    public static final String KEY_TOKEN    = "jwt_token";

    private EditText  emailInput;
    private Button    connectButton;
    private ProgressBar progressBar;
    private TextView  statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pair_with_email);

        emailInput    = findViewById(R.id.emailInput);
        connectButton = findViewById(R.id.connectButton);
        progressBar   = findViewById(R.id.progressBar);
        statusText    = findViewById(R.id.statusText);

        // Pre-fill if previously connected
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedEmail = prefs.getString("last_email", "");
        if (!savedEmail.isEmpty()) {
            emailInput.setText(savedEmail);
        }

        connectButton.setOnClickListener(v -> startDiscovery());
    }

    private void startDiscovery() {
        String email = emailInput.getText().toString().trim();
        if (email.isEmpty() || !email.contains("@")) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading state
        connectButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText("Searching for PC on your WiFi network…");
        statusText.setVisibility(View.VISIBLE);

        // Run network IO on background thread (never on main thread)
        new Thread(() -> {
            EmailAuthClient client = new EmailAuthClient();
            boolean success = client.discoverAndConnect(email);

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                connectButton.setEnabled(true);

                if (success) {
                    // Save connection details for future use
                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    prefs.edit()
                            .putString(KEY_BASE_URL, client.getBaseUrl())
                            .putString(KEY_TOKEN, client.getToken())
                            .putString("last_email", email)
                            .apply();

                    statusText.setText("✓ Connected! Opening file browser…");
                    Toast.makeText(this, "Connected to PC successfully!", Toast.LENGTH_SHORT).show();

                    // TODO: Replace with your actual file browser Activity class
                    // Intent intent = new Intent(this, FileBrowserActivity.class);
                    // startActivity(intent);
                    // finish();

                } else {
                    statusText.setText("Could not find PC on this network.\n" +
                            "Make sure both devices are on the same WiFi\n" +
                            "and the backend is running with your email configured.");
                    Toast.makeText(this,
                            "No PC found — check WiFi and backend config",
                            Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }
}
