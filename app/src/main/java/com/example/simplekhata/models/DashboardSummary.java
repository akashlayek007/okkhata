package com.example.simplekhata.models;

import java.util.Collections;
import java.util.List;

public class DashboardSummary {
    private final long totalReceivablePaise;
    private final long totalReceivedPaise;
    private final long netBalancePaise;
    private final int totalCustomers;
    private final List<RecentActivityItem> recentActivities;

    public DashboardSummary(
            long totalReceivablePaise,
            long totalReceivedPaise,
            int totalCustomers,
            List<RecentActivityItem> recentActivities
    ) {
        this(totalReceivablePaise, totalReceivedPaise, totalReceivablePaise, totalCustomers, recentActivities);
    }

    public DashboardSummary(
            long totalReceivablePaise,
            long totalReceivedPaise,
            long netBalancePaise,
            int totalCustomers,
            List<RecentActivityItem> recentActivities
    ) {
        this.totalReceivablePaise = totalReceivablePaise;
        this.totalReceivedPaise = totalReceivedPaise;
        this.netBalancePaise = netBalancePaise;
        this.totalCustomers = totalCustomers;
        this.recentActivities = recentActivities != null ? recentActivities : Collections.emptyList();
    }

    public long getTotalReceivablePaise() {
        return totalReceivablePaise;
    }

    public long getTotalReceivedPaise() {
        return totalReceivedPaise;
    }

    public long getNetBalancePaise() {
        return netBalancePaise;
    }

    public int getTotalCustomers() {
        return totalCustomers;
    }

    public List<RecentActivityItem> getRecentActivities() {
        return recentActivities;
    }
}
