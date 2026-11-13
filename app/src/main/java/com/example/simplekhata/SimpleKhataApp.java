package com.example.simplekhata;

import android.app.Application;

import com.example.R;
import com.example.simplekhata.utils.ThemeUtils;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.PersistentCacheSettings;

public class SimpleKhataApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ThemeUtils.applySavedTheme(this);
        FirebaseApp.initializeApp(this);
        configureFirestoreOfflinePersistence();
    }

    private void configureFirestoreOfflinePersistence() {
        try {
            String databaseId = getString(R.string.firestore_database_id);
            FirebaseFirestore db = FirebaseFirestore.getInstance(databaseId);
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                    .build();
            db.setFirestoreSettings(settings);
        } catch (IllegalStateException ignored) {
            // Firestore settings already applied
        }
    }
}
