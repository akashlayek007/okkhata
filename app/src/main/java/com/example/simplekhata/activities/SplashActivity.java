package com.example.simplekhata.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.example.R;
import com.example.simplekhata.utils.InsetsUtils;
import com.example.simplekhata.utils.SessionModeUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {
    private static final long SPLASH_DELAY_MS = 450L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean navigated = false;

    private final Runnable navigateRunnable = this::navigateNext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        InsetsUtils.applyEdgeToEdgeWithPadding(this, findViewById(R.id.splash_root));

        handler.postDelayed(navigateRunnable, SPLASH_DELAY_MS);
    }

    private void navigateNext() {
        if (navigated || isFinishing()) {
            return;
        }
        navigated = true;
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        boolean isAuthorizedOrGuest = currentUser != null || SessionModeUtils.isGuestMode(this);
        Intent intent = new Intent(
                this,
                isAuthorizedOrGuest ? MainActivity.class : AuthActivity.class
        );
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(navigateRunnable);
    }
}
