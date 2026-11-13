package com.example.simplekhata.firebase;

import android.app.Activity;
import android.content.Context;
import android.os.CancellationSignal;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;

import com.example.R;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class AuthManager {
    private static final String TAG = "Auth";

    public interface AuthCallback {
        void onSuccess(FirebaseUser user);
        void onCancelled();
        void onError(String message);
    }

    public interface SignOutCallback {
        void onComplete();
    }

    private final FirebaseAuth firebaseAuth;

    public AuthManager() {
        this.firebaseAuth = FirebaseAuth.getInstance();
    }

    public FirebaseUser getCurrentUser() {
        return firebaseAuth.getCurrentUser();
    }

    public void attemptAutoSignIn(@NonNull Activity activity, @NonNull AuthCallback callback) {
        FirebaseUser existing = firebaseAuth.getCurrentUser();
        if (existing != null) {
            callback.onSuccess(existing);
            return;
        }

        String clientId = resolveWebClientId(activity);
        if (clientId == null) {
            callback.onError(activity.getString(R.string.auth_error_missing_config));
            return;
        }

        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        CredentialManager credentialManager = CredentialManager.create(activity);
        credentialManager.getCredentialAsync(
                activity,
                request,
                new CancellationSignal(),
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        handleCredentialResponse(activity, result, callback);
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        Log.d(TAG, "Silent auto sign-in unavailable: " + e.getMessage());
                        callback.onCancelled();
                    }
                }
        );
    }

    public void startInteractiveGoogleSignIn(@NonNull Activity activity, @NonNull AuthCallback callback) {
        String clientId = resolveWebClientId(activity);
        if (clientId == null) {
            callback.onError(activity.getString(R.string.auth_error_missing_config));
            return;
        }

        GetSignInWithGoogleOption signInOption = new GetSignInWithGoogleOption.Builder(clientId)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build();

        CredentialManager credentialManager = CredentialManager.create(activity);
        credentialManager.getCredentialAsync(
                activity,
                request,
                new CancellationSignal(),
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        handleCredentialResponse(activity, result, callback);
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        if (e instanceof GetCredentialCancellationException) {
                            Log.w(TAG, "Google Sign-In cancelled or dismissed: " + e.getMessage(), e);
                            callback.onCancelled();
                        } else {
                            Log.e(TAG, "Google Sign-In failed", e);
                            String msg = e.getLocalizedMessage() != null
                                    ? e.getLocalizedMessage()
                                    : activity.getString(R.string.auth_error_generic);
                            callback.onError(msg);
                        }
                    }
                }
        );
    }

    private void handleCredentialResponse(
            Context context,
            GetCredentialResponse response,
            AuthCallback callback
    ) {
        Credential credential = response.getCredential();
        if (credential instanceof CustomCredential
                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            try {
                GoogleIdTokenCredential googleCred = GoogleIdTokenCredential.createFrom(credential.getData());
                String idToken = googleCred.getIdToken();
                AuthCredential firebaseCredential = GoogleAuthProvider.getCredential(idToken, null);
                firebaseAuth.signInWithCredential(firebaseCredential)
                        .addOnSuccessListener(authResult -> {
                            FirebaseUser user = authResult.getUser();
                            if (user != null) {
                                callback.onSuccess(user);
                            } else {
                                callback.onError(context.getString(R.string.auth_error_generic));
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Firebase signInWithCredential failed", e);
                            String msg = e.getLocalizedMessage() != null
                                    ? e.getLocalizedMessage()
                                    : context.getString(R.string.auth_error_generic);
                            callback.onError(msg);
                        });
            } catch (Exception e) {
                Log.e(TAG, "Invalid Google ID token credential", e);
                callback.onError(context.getString(R.string.auth_error_generic));
            }
        } else {
            callback.onError(context.getString(R.string.auth_error_generic));
        }
    }

    public void signOut(@NonNull Context context, @NonNull SignOutCallback callback) {
        CredentialManager credentialManager = CredentialManager.create(context);
        credentialManager.clearCredentialStateAsync(
                new ClearCredentialStateRequest(),
                null,
                ContextCompat.getMainExecutor(context),
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override
                    public void onResult(Void result) {
                        firebaseAuth.signOut();
                        callback.onComplete();
                    }

                    @Override
                    public void onError(@NonNull ClearCredentialException e) {
                        Log.w(TAG, "Failed to clear credential state: " + e.getMessage());
                        firebaseAuth.signOut();
                        callback.onComplete();
                    }
                }
        );
    }

    private String resolveWebClientId(Context context) {
        try {
            String clientId = context.getString(R.string.default_web_client_id);
            return clientId.trim().isEmpty() ? null : clientId;
        } catch (Exception e) {
            return null;
        }
    }
}
