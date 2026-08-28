package com.dataprocessor.model;

import java.time.LocalDateTime;

public class RawEvent {
    private Long id;
    private String sourceClient;
    private String rawPayload;
    private String status; // PROCESSED, REJECTED, FAILED_SIMULATED, DUPLICATE_IGNORED
    private String errorReason;
    private LocalDateTime receivedAt;

    public RawEvent() {}

    public RawEvent(String sourceClient, String rawPayload, String status, String errorReason) {
        this.sourceClient = sourceClient;
        this.rawPayload = rawPayload;
        this.status = status;
        this.errorReason = errorReason;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSourceClient() { return sourceClient; }
    public void setSourceClient(String sourceClient) { this.sourceClient = sourceClient; }

    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorReason() { return errorReason; }
    public void setErrorReason(String errorReason) { this.errorReason = errorReason; }

    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
}
