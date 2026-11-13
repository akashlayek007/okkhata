package com.example.simplekhata.models;

public enum TransactionType {
    CREDIT("CREDIT"),
    PAYMENT("PAYMENT");

    private final String firestoreValue;

    TransactionType(String firestoreValue) {
        this.firestoreValue = firestoreValue;
    }

    public String getFirestoreValue() {
        return firestoreValue;
    }

    public static TransactionType fromFirestoreValue(String value) {
        if (PAYMENT.firestoreValue.equals(value)) {
            return PAYMENT;
        }
        return CREDIT;
    }
}
