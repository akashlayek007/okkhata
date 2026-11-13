package com.example.simplekhata.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.simplekhata.models.Customer;
import com.example.simplekhata.models.LedgerTransaction;
import com.example.simplekhata.models.TransactionType;
import com.example.simplekhata.utils.ValidationUtils;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class LocalGuestStore extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "simple_khata_guest.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_CUSTOMERS = "customers";
    private static final String TABLE_TRANSACTIONS = "transactions";

    public LocalGuestStore(@NonNull Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_CUSTOMERS + " ("
                + "id TEXT PRIMARY KEY, "
                + "name TEXT NOT NULL, "
                + "phone TEXT, "
                + "created_at INTEGER NOT NULL, "
                + "updated_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE " + TABLE_TRANSACTIONS + " ("
                + "id TEXT PRIMARY KEY, "
                + "customer_id TEXT NOT NULL, "
                + "type TEXT NOT NULL, "
                + "amount INTEGER NOT NULL, "
                + "note TEXT, "
                + "tx_date INTEGER NOT NULL, "
                + "created_at INTEGER NOT NULL, "
                + "updated_at INTEGER NOT NULL)");

        db.execSQL("CREATE INDEX idx_tx_customer ON " + TABLE_TRANSACTIONS + "(customer_id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public synchronized boolean hasData() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_CUSTOMERS, null)) {
            if (c.moveToFirst() && c.getInt(0) > 0) {
                return true;
            }
        }
        try (Cursor t = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_TRANSACTIONS, null)) {
            return t.moveToFirst() && t.getInt(0) > 0;
        }
    }

    public synchronized List<Customer> getAllCustomers() {
        List<Customer> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_CUSTOMERS,
                new String[]{"id", "name", "phone", "created_at", "updated_at"},
                null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                String id = cursor.getString(0);
                String name = cursor.getString(1);
                String phone = cursor.isNull(2) ? "" : cursor.getString(2);
                Timestamp createdAt = new Timestamp(new Date(cursor.getLong(3)));
                Timestamp updatedAt = new Timestamp(new Date(cursor.getLong(4)));
                list.add(new Customer(id, name, phone, createdAt, updatedAt));
            }
        }
        return list;
    }

    public synchronized List<LedgerTransaction> getAllTransactions() {
        List<LedgerTransaction> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_TRANSACTIONS,
                new String[]{"id", "customer_id", "type", "amount", "note", "tx_date", "created_at", "updated_at"},
                null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                String id = cursor.getString(0);
                String customerId = cursor.getString(1);
                String type = cursor.getString(2);
                long amount = cursor.getLong(3);
                String note = cursor.isNull(4) ? "" : cursor.getString(4);
                Timestamp txDate = new Timestamp(new Date(cursor.getLong(5)));
                Timestamp createdAt = new Timestamp(new Date(cursor.getLong(6)));
                Timestamp updatedAt = new Timestamp(new Date(cursor.getLong(7)));
                list.add(new LedgerTransaction(id, customerId, type, amount, note, txDate, createdAt, updatedAt));
            }
        }
        return list;
    }

    public synchronized String createCustomer(@NonNull String name, @Nullable String phone) {
        String id = generateId("cust");
        long now = System.currentTimeMillis();
        String trimmedName = ValidationUtils.normalizeOptionalText(name);
        String trimmedPhone = ValidationUtils.normalizeOptionalText(phone);

        ContentValues cv = new ContentValues();
        cv.put("id", id);
        cv.put("name", trimmedName);
        if (!trimmedPhone.isEmpty()) {
            cv.put("phone", trimmedPhone);
        } else {
            cv.putNull("phone");
        }
        cv.put("created_at", now);
        cv.put("updated_at", now);

        SQLiteDatabase db = getWritableDatabase();
        db.insertOrThrow(TABLE_CUSTOMERS, null, cv);
        return id;
    }

    public synchronized void updateCustomer(
            @NonNull String customerId,
            @NonNull String name,
            @Nullable String phone
    ) {
        long now = System.currentTimeMillis();
        String trimmedName = ValidationUtils.normalizeOptionalText(name);
        String trimmedPhone = ValidationUtils.normalizeOptionalText(phone);

        ContentValues cv = new ContentValues();
        cv.put("name", trimmedName);
        if (!trimmedPhone.isEmpty()) {
            cv.put("phone", trimmedPhone);
        } else {
            cv.putNull("phone");
        }
        cv.put("updated_at", now);

        SQLiteDatabase db = getWritableDatabase();
        db.update(TABLE_CUSTOMERS, cv, "id = ?", new String[]{customerId});
    }

    public synchronized void deleteCustomerAndTransactions(@NonNull String customerId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_TRANSACTIONS, "customer_id = ?", new String[]{customerId});
            db.delete(TABLE_CUSTOMERS, "id = ?", new String[]{customerId});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized String createTransaction(
            @NonNull String customerId,
            @NonNull TransactionType type,
            long amountPaise,
            @Nullable String note,
            long transactionDateMillis
    ) {
        if (amountPaise <= 0L) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        String id = generateId("tx");
        long now = System.currentTimeMillis();
        String trimmedNote = ValidationUtils.normalizeOptionalText(note);

        ContentValues cv = new ContentValues();
        cv.put("id", id);
        cv.put("customer_id", customerId);
        cv.put("type", type.getFirestoreValue());
        cv.put("amount", amountPaise);
        if (!trimmedNote.isEmpty()) {
            cv.put("note", trimmedNote);
        } else {
            cv.putNull("note");
        }
        cv.put("tx_date", transactionDateMillis);
        cv.put("created_at", now);
        cv.put("updated_at", now);

        SQLiteDatabase db = getWritableDatabase();
        db.insertOrThrow(TABLE_TRANSACTIONS, null, cv);
        return id;
    }

    public synchronized void updateTransaction(
            @NonNull String transactionId,
            @NonNull TransactionType type,
            long amountPaise,
            @Nullable String note,
            long transactionDateMillis
    ) {
        if (amountPaise <= 0L) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        long now = System.currentTimeMillis();
        String trimmedNote = ValidationUtils.normalizeOptionalText(note);

        ContentValues cv = new ContentValues();
        cv.put("type", type.getFirestoreValue());
        cv.put("amount", amountPaise);
        if (!trimmedNote.isEmpty()) {
            cv.put("note", trimmedNote);
        } else {
            cv.putNull("note");
        }
        cv.put("tx_date", transactionDateMillis);
        cv.put("updated_at", now);

        SQLiteDatabase db = getWritableDatabase();
        db.update(TABLE_TRANSACTIONS, cv, "id = ?", new String[]{transactionId});
    }

    public synchronized void deleteTransaction(@NonNull String transactionId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_TRANSACTIONS, "id = ?", new String[]{transactionId});
    }

    public synchronized void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_TRANSACTIONS, null, null);
            db.delete(TABLE_CUSTOMERS, null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private String generateId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
