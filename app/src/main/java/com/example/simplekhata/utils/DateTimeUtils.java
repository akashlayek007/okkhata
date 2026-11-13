package com.example.simplekhata.utils;

import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DateTimeUtils {
    private DateTimeUtils() {
    }

    public static String formatDate(Timestamp timestamp) {
        if (timestamp == null) {
            return formatDateMillis(System.currentTimeMillis());
        }
        return formatDateMillis(timestamp.toDate().getTime());
    }

    public static String formatDateMillis(long millis) {
        if (millis <= 0L) {
            return "";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        return sdf.format(new Date(millis));
    }

    public static String formatDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return formatDateTimeMillis(System.currentTimeMillis());
        }
        return formatDateTimeMillis(timestamp.toDate().getTime());
    }

    public static String formatDateTimeMillis(long millis) {
        if (millis <= 0L) {
            return "";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        return sdf.format(new Date(millis));
    }
}
