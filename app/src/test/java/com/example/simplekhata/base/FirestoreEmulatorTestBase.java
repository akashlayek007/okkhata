package com.example.simplekhata.base;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.MemoryCacheSettings;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = {35})
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
public abstract class FirestoreEmulatorTestBase {

    private static final String EMULATOR_HOST = "127.0.0.1";
    private static final int FIRESTORE_PORT = 8085;
    private static final int AUTH_PORT = 9099;
    private static final String PROJECT_ID = "demo-no-project";
    private static final String DEFAULT_PASSWORD = "password123";
    private static final long TIMEOUT_SECONDS = 8L;

    private static final String EMULATOR_APP_NAME = "emulator-test-app";

    protected FirebaseFirestore firestore;
    protected FirebaseAuth auth;
    protected String databaseId;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    @Before
    public void setUpFirebase() {
        Context context = ApplicationProvider.getApplicationContext();
        int resId = context.getResources().getIdentifier("firestore_database_id", "string", context.getPackageName());
        databaseId = resId != 0 ? context.getString(resId) : "demo-database";

        FirebaseApp app = null;
        for (FirebaseApp existing : FirebaseApp.getApps(context)) {
            if (EMULATOR_APP_NAME.equals(existing.getName())) {
                app = existing;
                break;
            }
        }

        if (app == null) {
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setApplicationId("1:123456789012:android:1234567890abcdef")
                    .setProjectId(PROJECT_ID)
                    .setApiKey("fake-api-key-for-emulator")
                    .build();
            app = FirebaseApp.initializeApp(context, options, EMULATOR_APP_NAME);

            FirebaseFirestore newFirestore = FirebaseFirestore.getInstance(app, databaseId);
            newFirestore.useEmulator(EMULATOR_HOST, FIRESTORE_PORT);
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build();
            newFirestore.setFirestoreSettings(settings);

            FirebaseAuth newAuth = FirebaseAuth.getInstance(app);
            newAuth.useEmulator(EMULATOR_HOST, AUTH_PORT);
        }

        firestore = FirebaseFirestore.getInstance(app, databaseId);
        auth = FirebaseAuth.getInstance(app);
    }

    @After
    public void tearDownFirebase() {
        auth.signOut();
        ioExecutor.shutdownNow();
    }

    protected String signInTestUser(String email) throws Exception {
        Future<String> future = ioExecutor.submit(() -> {
            AuthResult result;
            try {
                result = Tasks.await(
                        auth.createUserWithEmailAndPassword(email, DEFAULT_PASSWORD),
                        TIMEOUT_SECONDS,
                        TimeUnit.SECONDS
                );
            } catch (ExecutionException e) {
                if (e.getCause() instanceof com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                    result = Tasks.await(
                            auth.signInWithEmailAndPassword(email, DEFAULT_PASSWORD),
                            TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    );
                } else {
                    throw e;
                }
            }
            if (result.getUser() == null) {
                throw new IllegalStateException("Test user auth failed");
            }
            return result.getUser().getUid();
        });
        return future.get(TIMEOUT_SECONDS + 2, TimeUnit.SECONDS);
    }

    protected FirebaseFirestoreException unwrapFirestoreException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof FirebaseFirestoreException) {
                return (FirebaseFirestoreException) current;
            }
            current = current.getCause();
        }
        return null;
    }
}
