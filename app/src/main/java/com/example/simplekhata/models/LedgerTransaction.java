package com.example.simplekhata.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.ServerTimestamp;

@IgnoreExtraProperties
public class LedgerTransaction {
    @DocumentId
    private String id = "";
    private String customerId = "";
    private String type = TransactionType.CREDIT.getFirestoreValue();
    private long amount = 0L;
    private String note = "";
    private Timestamp transactionDate;
    @ServerTimestamp
    private Timestamp createdAt;
    @ServerTimestamp
    private Timestamp updatedAt;

    public LedgerTransaction() {
    }

    public LedgerTransaction(
            String id,
            String customerId,
            String type,
            long amount,
            String note,
            Timestamp transactionDate,
            Timestamp createdAt,
            Timestamp updatedAt
    ) {
        this.id = id != null ? id : "";
        this.customerId = customerId != null ? customerId : "";
        this.type = type != null ? type : TransactionType.CREDIT.getFirestoreValue();
        this.amount = amount;
        this.note = note != null ? note : "";
        this.transactionDate = transactionDate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id != null ? id : "";
    }

    public void setId(String id) {
        this.id = id != null ? id : "";
    }

    public String getCustomerId() {
        return customerId != null ? customerId : "";
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId != null ? customerId : "";
    }

    public String getType() {
        return type != null ? type : TransactionType.CREDIT.getFirestoreValue();
    }

    public void setType(String type) {
        this.type = type != null ? type : TransactionType.CREDIT.getFirestoreValue();
    }

    public TransactionType getTransactionType() {
        return TransactionType.fromFirestoreValue(getType());
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public String getNote() {
        return note != null ? note : "";
    }

    public void setNote(String note) {
        this.note = note != null ? note : "";
    }

    public Timestamp getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(Timestamp transactionDate) {
        this.transactionDate = transactionDate;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getEffectiveTimestampMillis() {
        if (transactionDate != null) {
            return transactionDate.toDate().getTime();
        }
        if (createdAt != null) {
            return createdAt.toDate().getTime();
        }
        return 0L;
    }
}
