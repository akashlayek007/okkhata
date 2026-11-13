package com.example.simplekhata.firebase;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.firestore.FirebaseFirestoreException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class FirestoreErrorHandler {
    private static final String TAG = "FirestoreError";

    public enum OperationType {
        CREATE("create"),
        UPDATE("update"),
        DELETE("delete"),
        LIST("list"),
        GET("get"),
        WRITE("write");

        private final String value;

        OperationType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    private FirestoreErrorHandler() {
    }

    public static String handleFirestoreError(
            Throwable exception,
            OperationType operationType,
            String path
    ) {
        return handleFirestoreError(exception, operationType, path, FirebaseAuth.getInstance());
    }

    public static String handleFirestoreError(
            Throwable exception,
            OperationType operationType,
            String path,
            FirebaseAuth auth
    ) {
        Throwable rootError = findRootError(exception);
        FirebaseUser currentUser = auth != null ? auth.getCurrentUser() : null;

        JSONArray providerInfoArray = new JSONArray();
        if (currentUser != null) {
            for (UserInfo provider : currentUser.getProviderData()) {
                try {
                    JSONObject providerJson = new JSONObject();
                    providerJson.put("providerId", provider.getProviderId());
                    providerJson.put("email", provider.getEmail() != null ? provider.getEmail() : JSONObject.NULL);
                    providerInfoArray.put(providerJson);
                } catch (JSONException ignored) {
                }
            }
        }

        JSONObject authInfoJson = new JSONObject();
        try {
            if (currentUser != null) {
                authInfoJson.put("userId", currentUser.getUid());
                authInfoJson.put("email", currentUser.getEmail() != null ? currentUser.getEmail() : JSONObject.NULL);
                authInfoJson.put("emailVerified", currentUser.isEmailVerified());
                authInfoJson.put("tenantId", currentUser.getTenantId() != null ? currentUser.getTenantId() : JSONObject.NULL);
            } else {
                authInfoJson.put("userId", JSONObject.NULL);
                authInfoJson.put("email", JSONObject.NULL);
                authInfoJson.put("emailVerified", JSONObject.NULL);
                authInfoJson.put("tenantId", JSONObject.NULL);
            }
            authInfoJson.put("providerInfo", providerInfoArray);
        } catch (JSONException ignored) {
        }

        JSONObject errorInfoJson = new JSONObject();
        try {
            String message = rootError != null && rootError.getMessage() != null
                    ? rootError.getMessage()
                    : String.valueOf(rootError);
            errorInfoJson.put("error", message);
            errorInfoJson.put("operationType", operationType != null ? operationType.getValue() : "write");
            errorInfoJson.put("path", path != null ? path : JSONObject.NULL);
            errorInfoJson.put("authInfo", authInfoJson);
        } catch (JSONException ignored) {
        }

        String jsonString = errorInfoJson.toString();
        Log.e(TAG, "Firestore Error: " + jsonString);
        return jsonString;
    }

    private static Throwable findRootError(Throwable exception) {
        Throwable current = exception;
        Throwable firstNonNull = exception;
        while (current != null) {
            if (current instanceof FirebaseFirestoreException) {
                return current;
            }
            current = current.getCause();
        }
        return firstNonNull;
    }
}
