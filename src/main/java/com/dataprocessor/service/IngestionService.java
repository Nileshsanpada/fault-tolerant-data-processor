package com.dataprocessor.service;

import com.dataprocessor.db.DatabaseConfig;
import com.dataprocessor.model.CanonicalEvent;
import com.dataprocessor.model.IngestionResponse;

import java.sql.*;
import java.time.OffsetDateTime;

public class IngestionService {

    private final NormalizationService normalizationService;

    public IngestionService(NormalizationService normalizationService) {
        this.normalizationService = normalizationService;
    }

    public IngestionResponse processIngestion(String rawJson, boolean simulateFailure) {
        Connection conn = null;
        Long rawEventId = null;

        try {
            conn = DatabaseConfig.getConnection();
            conn.setAutoCommit(false); // Explicit transaction boundary

            // Log raw incoming payload
            String rawSql = "INSERT INTO raw_events (source_client, raw_payload, status) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(rawSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, quickExtractSource(rawJson));
                ps.setString(2, rawJson);
                ps.setString(3, "PENDING");
                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        rawEventId = keys.getLong(1);
                    }
                }
            }

            // Normalization step
            CanonicalEvent canonical;
            try {
                canonical = normalizationService.normalize(rawJson);
                if (rawEventId != null) {
                    canonical.setRawEventId(rawEventId);
                }
            } catch (IllegalArgumentException valEx) {
                // Store rejection status and commit raw event log
                updateRawEventStatus(conn, rawEventId, "REJECTED", valEx.getMessage());
                conn.commit();
                return new IngestionResponse(false, "REJECTED", "Validation failed: " + valEx.getMessage(), null, null);
            }

            // Check duplicate fingerprint in DB
            String dupSql = "SELECT id, client_id, metric, amount, timestamp, event_fingerprint FROM canonical_events WHERE event_fingerprint = ?";
            try (PreparedStatement ps = conn.prepareStatement(dupSql)) {
                ps.setString(1, canonical.getFingerprint());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        updateRawEventStatus(conn, rawEventId, "DUPLICATE_IGNORED", "Event fingerprint already exists");
                        conn.commit();

                        CanonicalEvent existing = new CanonicalEvent(
                                rs.getString("client_id"),
                                rs.getString("metric"),
                                rs.getDouble("amount"),
                                rs.getObject("timestamp", OffsetDateTime.class),
                                rs.getString("event_fingerprint")
                        );
                        existing.setId(rs.getLong("id"));

                        return new IngestionResponse(true, "DUPLICATE_IGNORED",
                                "Duplicate event detected. Double counting prevented.",
                                canonical.getFingerprint(), existing);
                    }
                }
            }

            // Failure simulation check
            if (simulateFailure) {
                conn.rollback(); // Undo transaction
                saveFailureLog(rawJson, canonical.getFingerprint());

                return new IngestionResponse(false, "FAILED_SIMULATED",
                        "Simulated database failure triggered. Transaction rolled back cleanly.",
                        canonical.getFingerprint(), null);
            }

            // Insert normalized canonical record
            String insertSql = """
                INSERT INTO canonical_events (raw_event_id, client_id, metric, amount, timestamp, event_fingerprint)
                VALUES (?, ?, ?, ?, ?, ?)
            """;
            try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, rawEventId);
                ps.setString(2, canonical.getClientId());
                ps.setString(3, canonical.getMetric());
                ps.setDouble(4, canonical.getAmount());
                ps.setObject(5, canonical.getTimestamp());
                ps.setString(6, canonical.getFingerprint());
                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        canonical.setId(keys.getLong(1));
                    }
                }
            }

            updateRawEventStatus(conn, rawEventId, "PROCESSED", null);
            conn.commit(); // Transaction success

            return new IngestionResponse(true, "PROCESSED",
                    "Event normalized and processed successfully.",
                    canonical.getFingerprint(), canonical);

        } catch (Exception ex) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            return new IngestionResponse(false, "FAILED_ERROR",
                    "System processing error: " + ex.getMessage(), null, null);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    private void updateRawEventStatus(Connection conn, Long id, String status, String reason) throws SQLException {
        if (id == null) return;
        String sql = "UPDATE raw_events SET status = ?, error_reason = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, reason);
            ps.setLong(3, id);
            ps.executeUpdate();
        }
    }

    private void saveFailureLog(String rawJson, String fingerprint) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            String sql = "INSERT INTO raw_events (source_client, raw_payload, status, error_reason) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, quickExtractSource(rawJson));
                ps.setString(2, rawJson);
                ps.setString(3, "FAILED_SIMULATED");
                ps.setString(4, "Simulated DB write failure (Fingerprint: " + fingerprint + ")");
                ps.executeUpdate();
            }
        } catch (Exception ignored) {}
    }

    private String quickExtractSource(String rawJson) {
        if (rawJson == null) return "unknown";
        if (rawJson.contains("\"source\"")) {
            int idx = rawJson.indexOf("\"source\"");
            int start = rawJson.indexOf("\"", idx + 8);
            if (start != -1) {
                int end = rawJson.indexOf("\"", start + 1);
                if (end != -1) return rawJson.substring(start + 1, end);
            }
        }
        return "unknown";
    }
}
