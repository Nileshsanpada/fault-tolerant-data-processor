package com.dataprocessor.model;

import java.util.Map;

public class AggregationResult {
    private long totalEvents;
    private double totalAmount;
    private double averageAmount;
    private Map<String, MetricSummary> metricsBreakdown;
    private Map<String, ClientSummary> clientBreakdown;

    public AggregationResult() {}

    public AggregationResult(long totalEvents, double totalAmount, double averageAmount,
                             Map<String, MetricSummary> metricsBreakdown,
                             Map<String, ClientSummary> clientBreakdown) {
        this.totalEvents = totalEvents;
        this.totalAmount = totalAmount;
        this.averageAmount = averageAmount;
        this.metricsBreakdown = metricsBreakdown;
        this.clientBreakdown = clientBreakdown;
    }

    public long getTotalEvents() { return totalEvents; }
    public double getTotalAmount() { return totalAmount; }
    public double getAverageAmount() { return averageAmount; }
    public Map<String, MetricSummary> getMetricsBreakdown() { return metricsBreakdown; }
    public Map<String, ClientSummary> getClientBreakdown() { return clientBreakdown; }

    public static class MetricSummary {
        private long count;
        private double sum;

        public MetricSummary(long count, double sum) {
            this.count = count;
            this.sum = sum;
        }

        public long getCount() { return count; }
        public double getSum() { return sum; }
    }

    public static class ClientSummary {
        private long count;
        private double sum;

        public ClientSummary(long count, double sum) {
            this.count = count;
            this.sum = sum;
        }

        public long getCount() { return count; }
        public double getSum() { return sum; }
    }
}
