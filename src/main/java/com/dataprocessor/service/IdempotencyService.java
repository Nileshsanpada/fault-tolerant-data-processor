package com.dataprocessor.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public class IdempotencyService {

    // Computes deterministic fingerprint hash based on canonical fields
    public String generateFingerprint(String clientId, String metric, double amount, OffsetDateTime timestamp) {
        String formattedAmount = String.format("%.4f", amount);
        String formattedTs = timestamp.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        
        String key = clientId.trim().toLowerCase() + ":" + 
                     metric.trim().toLowerCase() + ":" + 
                     formattedAmount + ":" + 
                     formattedTs;

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 hashing error", e);
        }
    }
}
