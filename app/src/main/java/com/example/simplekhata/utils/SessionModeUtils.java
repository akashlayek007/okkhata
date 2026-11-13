package com.example.simplekhata.utils;

import android.content.Context;
import android.content.SharedPreferences;

public final class SessionModeUtils {
    private static final String PREFS_NAME = "simple_khata_prefs";
    private static final String KEY_GUEST_MODE = "guest_mode_enabled";

    private SessionModeUtils() {
    }

    public static boolean isGuestMode(Context context) {
        if (context == null) {
            return false;
        }
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_GUEST_MODE, false);
    }

    public static void setGuestMode(Context context, boolean enabled) {
        if (context == null) {
            return;
        }
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_GUEST_MODE, enabled).apply();
    }
}
