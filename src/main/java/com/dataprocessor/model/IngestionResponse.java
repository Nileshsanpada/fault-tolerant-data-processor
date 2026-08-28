package com.dataprocessor.model;

public class IngestionResponse {
    private boolean success;
    private String status; // PROCESSED, DUPLICATE_IGNORED, REJECTED, FAILED_SIMULATED
    private String message;
    private String fingerprint;
    private CanonicalEvent normalizedData;

    public IngestionResponse() {}

    public IngestionResponse(boolean success, String status, String message, String fingerprint, CanonicalEvent normalizedData) {
        this.success = success;
        this.status = status;
        this.message = message;
        this.fingerprint = fingerprint;
        this.normalizedData = normalizedData;
    }

    public boolean isSuccess() { return success; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public String getFingerprint() { return fingerprint; }
    public CanonicalEvent getNormalizedData() { return normalizedData; }
}
