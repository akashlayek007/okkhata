package com.example.simplekhata.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.R;
import com.example.simplekhata.firebase.KhataRepository;
import com.example.simplekhata.fragments.CustomersFragment;
import com.example.simplekhata.fragments.HomeFragment;
import com.example.simplekhata.fragments.SettingsFragment;
import com.example.simplekhata.utils.InsetsUtils;
import com.example.simplekhata.utils.SessionModeUtils;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    private static final String KEY_SELECTED_TAB = "selected_tab_id";

    private BottomNavigationView bottomNavigation;
    private KhataRepository repository;
    private final FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
    private int currentTabId = R.id.nav_home;

    private final FirebaseAuth.AuthStateListener authStateListener = new FirebaseAuth.AuthStateListener() {
        @Override
        public void onAuthStateChanged(@NonNull FirebaseAuth auth) {
            if (!hasActiveSession() && !isFinishing()) {
                redirectToAuth();
            }
        }
    };

    private boolean hasActiveSession() {
        return firebaseAuth.getCurrentUser() != null || SessionModeUtils.isGuestMode(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!hasActiveSession()) {
            redirectToAuth();
            return;
        }

        setContentView(R.layout.activity_main);
        InsetsUtils.applyEdgeToEdgeWithPadding(this, findViewById(R.id.main_root));

        repository = KhataRepository.getInstance(this);
        repository.startObservingAuth();

        bottomNavigation = findViewById(R.id.bottom_navigation);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == currentTabId && getSupportFragmentManager().findFragmentById(R.id.fragment_container) != null) {
                return true;
            }
            showFragmentForTab(itemId);
            return true;
        });

        if (savedInstanceState != null) {
            currentTabId = savedInstanceState.getInt(KEY_SELECTED_TAB, R.id.nav_home);
        }
        bottomNavigation.setSelectedItemId(currentTabId);
        if (getSupportFragmentManager().findFragmentById(R.id.fragment_container) == null) {
            showFragmentForTab(currentTabId);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        firebaseAuth.addAuthStateListener(authStateListener);
        if (!hasActiveSession()) {
            redirectToAuth();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        firebaseAuth.removeAuthStateListener(authStateListener);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SELECTED_TAB, currentTabId);
    }

    public void openSettingsTab() {
        if (bottomNavigation != null) {
            bottomNavigation.setSelectedItemId(R.id.nav_settings);
        }
    }

    public void openCustomersTab() {
        if (bottomNavigation != null) {
            bottomNavigation.setSelectedItemId(R.id.nav_customers);
        }
    }

    private void showFragmentForTab(int tabId) {
        currentTabId = tabId;
        Fragment fragment;
        if (tabId == R.id.nav_customers) {
            fragment = new CustomersFragment();
        } else if (tabId == R.id.nav_settings) {
            fragment = new SettingsFragment();
        } else {
            fragment = new HomeFragment();
        }
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    private void redirectToAuth() {
        if (repository != null) {
            repository.stopListeners();
        }
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
