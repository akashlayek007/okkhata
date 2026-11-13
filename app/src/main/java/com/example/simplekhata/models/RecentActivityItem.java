package com.example.simplekhata.models;

public class RecentActivityItem {
    private final LedgerTransaction transaction;
    private final String customerName;

    public RecentActivityItem(LedgerTransaction transaction, String customerName) {
        this.transaction = transaction;
        this.customerName = customerName != null ? customerName : "";
    }

    public LedgerTransaction getTransaction() {
        return transaction;
    }

    public String getCustomerName() {
        return customerName;
    }
}
