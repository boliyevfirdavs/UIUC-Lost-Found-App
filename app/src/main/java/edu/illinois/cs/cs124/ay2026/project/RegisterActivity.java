package edu.illinois.cs.cs124.ay2026.project;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText nameField;
    private TextInputEditText emailField;
    private TextInputEditText passwordField;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        nameField = findViewById(R.id.name_field);
        emailField = findViewById(R.id.email_field);
        passwordField = findViewById(R.id.password_field);

        Button registerButton = findViewById(R.id.register_button);
        TextView loginLink = findViewById(R.id.login_link);

        registerButton.setOnClickListener(v -> attemptRegister());
        loginLink.setOnClickListener(v -> finish());
    }

    private void attemptRegister() {
        String name = nameField.getText() != null
                ? nameField.getText().toString().trim() : "";
        String email = emailField.getText() != null
                ? emailField.getText().toString().trim() : "";
        String password = passwordField.getText() != null
                ? passwordField.getText().toString() : "";

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!email.endsWith("@illinois.edu")) {
            emailField.setError("Use your @illinois.edu email");
            return;
        }
        if (password.length() < 6) {
            passwordField.setError("Password must be at least 6 characters");
            return;
        }

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    // Set display name on the Firebase Auth user profile
                    UserProfileChangeRequest update = new UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build();
                    result.getUser().updateProfile(update);

                    // Save user document to Firestore so we can look up names later
                    Map<String, Object> userDoc = new HashMap<>();
                    userDoc.put("name", name);
                    userDoc.put("email", email);
                    userDoc.put("contactInfo", email);
                    userDoc.put("createdAt", Timestamp.now());
                    db.collection("users").document(result.getUser().getUid()).set(userDoc);

                    // Send verification email and sign out until they verify
                    result.getUser().sendEmailVerification();
                    auth.signOut();

                    Toast.makeText(this,
                            "Account created! Check your inbox to verify.",
                            Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Registration failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }
}