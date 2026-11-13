package com.example.simplekhata.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.R;
import com.example.simplekhata.firebase.AuthManager;
import com.example.simplekhata.utils.InsetsUtils;
import com.example.simplekhata.utils.SessionModeUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseUser;

public class AuthActivity extends AppCompatActivity {

    private AuthManager authManager;
    private MaterialButton btnGoogleSignIn;
    private MaterialButton btnContinueWithoutLogin;
    private CircularProgressIndicator progressAuth;
    private TextView tvAuthError;
    private boolean isInteractiveLoading = false;
    private boolean navigated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        InsetsUtils.applyEdgeToEdgeWithPadding(this, findViewById(R.id.auth_root));

        authManager = new AuthManager();
        if (authManager.getCurrentUser() != null || SessionModeUtils.isGuestMode(this)) {
            navigateToMain();
            return;
        }

        btnGoogleSignIn = findViewById(R.id.btn_google_sign_in);
        btnContinueWithoutLogin = findViewById(R.id.btn_continue_without_login);
        progressAuth = findViewById(R.id.progress_auth);
        tvAuthError = findViewById(R.id.tv_auth_error);

        btnGoogleSignIn.setOnClickListener(v -> triggerInteractiveSignIn());
        btnContinueWithoutLogin.setOnClickListener(v -> continueWithoutLogin());

        attemptSilentSignIn();
    }

    private void continueWithoutLogin() {
        if (isInteractiveLoading || navigated) {
            return;
        }
        SessionModeUtils.setGuestMode(this, true);
        navigateToMain();
    }

    private void attemptSilentSignIn() {
        setLoading(true, false);
        authManager.attemptAutoSignIn(this, new AuthManager.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                if (navigated || isFinishing()) {
                    return;
                }
                setLoading(false, false);
                SessionModeUtils.setGuestMode(AuthActivity.this, false);
                navigateToMain();
            }

            @Override
            public void onCancelled() {
                setLoading(false, false);
            }

            @Override
            public void onError(String message) {
                setLoading(false, false);
            }
        });
    }

    private void triggerInteractiveSignIn() {
        if (isInteractiveLoading || navigated) {
            return;
        }
        tvAuthError.setVisibility(View.GONE);
        setLoading(true, true);
        authManager.startInteractiveGoogleSignIn(this, new AuthManager.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                setLoading(false, false);
                SessionModeUtils.setGuestMode(AuthActivity.this, false);
                navigateToMain();
            }

            @Override
            public void onCancelled() {
                setLoading(false, false);
            }

            @Override
            public void onError(String message) {
                setLoading(false, false);
                tvAuthError.setText(message);
                tvAuthError.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setLoading(boolean loading, boolean lockGuestButton) {
        isInteractiveLoading = loading && lockGuestButton;
        btnGoogleSignIn.setEnabled(!loading);
        btnGoogleSignIn.setText(loading ? R.string.auth_signing_in : R.string.auth_sign_in_google);
        if (btnContinueWithoutLogin != null) {
            btnContinueWithoutLogin.setEnabled(!(loading && lockGuestButton));
        }
        progressAuth.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void navigateToMain() {
        if (navigated || isFinishing()) {
            return;
        }
        navigated = true;
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
