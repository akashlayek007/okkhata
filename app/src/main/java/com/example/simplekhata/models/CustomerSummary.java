package com.example.simplekhata.models;

public class CustomerSummary {
    private final Customer customer;
    private final long totalCreditPaise;
    private final long totalPaymentPaise;
    private final long balancePaise;
    private final int transactionCount;
    private final long lastActivityMillis;

    public CustomerSummary(
            Customer customer,
            long totalCreditPaise,
            long totalPaymentPaise,
            int transactionCount,
            long lastActivityMillis
    ) {
        this.customer = customer;
        this.totalCreditPaise = totalCreditPaise;
        this.totalPaymentPaise = totalPaymentPaise;
        this.balancePaise = totalCreditPaise - totalPaymentPaise;
        this.transactionCount = transactionCount;
        this.lastActivityMillis = lastActivityMillis;
    }

    public Customer getCustomer() {
        return customer;
    }

    public long getTotalCreditPaise() {
        return totalCreditPaise;
    }

    public long getTotalPaymentPaise() {
        return totalPaymentPaise;
    }

    public long getBalancePaise() {
        return balancePaise;
    }

    public int getTransactionCount() {
        return transactionCount;
    }

    public long getLastActivityMillis() {
        return lastActivityMillis;
    }
}
