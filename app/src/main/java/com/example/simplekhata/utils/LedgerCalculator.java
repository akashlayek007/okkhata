package com.example.simplekhata.utils;

import com.example.simplekhata.models.Customer;
import com.example.simplekhata.models.CustomerSummary;
import com.example.simplekhata.models.DashboardSummary;
import com.example.simplekhata.models.LedgerTransaction;
import com.example.simplekhata.models.RecentActivityItem;
import com.example.simplekhata.models.TransactionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LedgerCalculator {
    private static final int MAX_RECENT_ITEMS = 12;

    private LedgerCalculator() {
    }

    public static List<CustomerSummary> buildCustomerSummaries(
            List<Customer> customers,
            List<LedgerTransaction> transactions
    ) {
        Map<String, long[]> totalsByCustomer = new HashMap<>();
        Map<String, Integer> countByCustomer = new HashMap<>();
        Map<String, Long> lastActivityByCustomer = new HashMap<>();

        if (transactions != null) {
            for (LedgerTransaction tx : transactions) {
                String cid = tx.getCustomerId();
                long[] totals = totalsByCustomer.get(cid);
                if (totals == null) {
                    totals = new long[]{0L, 0L};
                    totalsByCustomer.put(cid, totals);
                }
                if (tx.getTransactionType() == TransactionType.PAYMENT) {
                    totals[1] += tx.getAmount();
                } else {
                    totals[0] += tx.getAmount();
                }
                Integer count = countByCustomer.get(cid);
                countByCustomer.put(cid, count == null ? 1 : count + 1);

                long txTime = tx.getEffectiveTimestampMillis();
                Long prevMax = lastActivityByCustomer.get(cid);
                if (prevMax == null || txTime > prevMax) {
                    lastActivityByCustomer.put(cid, txTime);
                }
            }
        }

        List<CustomerSummary> result = new ArrayList<>();
        if (customers != null) {
            for (Customer customer : customers) {
                String cid = customer.getId();
                long[] totals = totalsByCustomer.get(cid);
                long credit = totals != null ? totals[0] : 0L;
                long payment = totals != null ? totals[1] : 0L;
                Integer count = countByCustomer.get(cid);
                int txCount = count != null ? count : 0;
                Long lastTxTime = lastActivityByCustomer.get(cid);
                long createdMillis = customer.getCreatedAt() != null
                        ? customer.getCreatedAt().toDate().getTime()
                        : 0L;
                long lastActivity = lastTxTime != null ? Math.max(lastTxTime, createdMillis) : createdMillis;
                result.add(new CustomerSummary(customer, credit, payment, txCount, lastActivity));
            }
        }

        Collections.sort(result, new Comparator<CustomerSummary>() {
            @Override
            public int compare(CustomerSummary a, CustomerSummary b) {
                int timeCmp = Long.compare(b.getLastActivityMillis(), a.getLastActivityMillis());
                if (timeCmp != 0) {
                    return timeCmp;
                }
                return a.getCustomer().getName().compareToIgnoreCase(b.getCustomer().getName());
            }
        });
        return result;
    }

    public static CustomerSummary buildSingleCustomerSummary(
            Customer customer,
            List<LedgerTransaction> customerTransactions
    ) {
        long credit = 0L;
        long payment = 0L;
        long lastActivity = customer.getCreatedAt() != null
                ? customer.getCreatedAt().toDate().getTime()
                : 0L;
        int count = 0;

        if (customerTransactions != null) {
            count = customerTransactions.size();
            for (LedgerTransaction tx : customerTransactions) {
                if (tx.getTransactionType() == TransactionType.PAYMENT) {
                    payment += tx.getAmount();
                } else {
                    credit += tx.getAmount();
                }
                long txTime = tx.getEffectiveTimestampMillis();
                if (txTime > lastActivity) {
                    lastActivity = txTime;
                }
            }
        }
        return new CustomerSummary(customer, credit, payment, count, lastActivity);
    }

    public static DashboardSummary buildDashboardSummary(
            List<Customer> customers,
            List<LedgerTransaction> transactions
    ) {
        List<CustomerSummary> customerSummaries = buildCustomerSummaries(customers, transactions);
        long totalReceivable = 0L;
        long netBalance = 0L;
        Map<String, String> customerNamesById = new HashMap<>();

        for (CustomerSummary cs : customerSummaries) {
            customerNamesById.put(cs.getCustomer().getId(), cs.getCustomer().getName());
            netBalance += cs.getBalancePaise();
            if (cs.getBalancePaise() > 0L) {
                totalReceivable += cs.getBalancePaise();
            }
        }

        long totalReceived = 0L;
        List<LedgerTransaction> sortedTx = new ArrayList<>();
        if (transactions != null) {
            for (LedgerTransaction tx : transactions) {
                if (tx.getTransactionType() == TransactionType.PAYMENT) {
                    totalReceived += tx.getAmount();
                }
                if (customerNamesById.containsKey(tx.getCustomerId())) {
                    sortedTx.add(tx);
                }
            }
        }

        Collections.sort(sortedTx, new Comparator<LedgerTransaction>() {
            @Override
            public int compare(LedgerTransaction a, LedgerTransaction b) {
                int dateCmp = Long.compare(b.getEffectiveTimestampMillis(), a.getEffectiveTimestampMillis());
                if (dateCmp != 0) {
                    return dateCmp;
                }
                long aCreated = a.getCreatedAt() != null ? a.getCreatedAt().toDate().getTime() : 0L;
                long bCreated = b.getCreatedAt() != null ? b.getCreatedAt().toDate().getTime() : 0L;
                return Long.compare(bCreated, aCreated);
            }
        });

        List<RecentActivityItem> recent = new ArrayList<>();
        int limit = Math.min(MAX_RECENT_ITEMS, sortedTx.size());
        for (int i = 0; i < limit; i++) {
            LedgerTransaction tx = sortedTx.get(i);
            String name = customerNamesById.get(tx.getCustomerId());
            recent.add(new RecentActivityItem(tx, name != null ? name : ""));
        }

        return new DashboardSummary(
                totalReceivable,
                totalReceived,
                netBalance,
                customers != null ? customers.size() : 0,
                recent
        );
    }

    public static List<LedgerTransaction> filterAndSortCustomerTransactions(
            String customerId,
            List<LedgerTransaction> allTransactions
    ) {
        List<LedgerTransaction> result = new ArrayList<>();
        if (customerId == null || allTransactions == null) {
            return result;
        }
        for (LedgerTransaction tx : allTransactions) {
            if (customerId.equals(tx.getCustomerId())) {
                result.add(tx);
            }
        }
        Collections.sort(result, new Comparator<LedgerTransaction>() {
            @Override
            public int compare(LedgerTransaction a, LedgerTransaction b) {
                int dateCmp = Long.compare(b.getEffectiveTimestampMillis(), a.getEffectiveTimestampMillis());
                if (dateCmp != 0) {
                    return dateCmp;
                }
                long aCreated = a.getCreatedAt() != null ? a.getCreatedAt().toDate().getTime() : 0L;
                long bCreated = b.getCreatedAt() != null ? b.getCreatedAt().toDate().getTime() : 0L;
                return Long.compare(bCreated, aCreated);
            }
        });
        return result;
    }
}
