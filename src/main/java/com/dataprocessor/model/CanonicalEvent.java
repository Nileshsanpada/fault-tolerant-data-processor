package com.dataprocessor.model;

import java.time.OffsetDateTime;

public class CanonicalEvent {
    private Long id;
    private Long rawEventId;
    private String clientId;
    private String metric;
    private Double amount;
    private OffsetDateTime timestamp;
    private String fingerprint;

    public CanonicalEvent() {}

    public CanonicalEvent(String clientId, String metric, Double amount, OffsetDateTime timestamp, String fingerprint) {
        this.clientId = clientId;
        this.metric = metric;
        this.amount = amount;
        this.timestamp = timestamp;
        this.fingerprint = fingerprint;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRawEventId() { return rawEventId; }
    public void setRawEventId(Long rawEventId) { this.rawEventId = rawEventId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public OffsetDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(OffsetDateTime timestamp) { this.timestamp = timestamp; }

    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
}
