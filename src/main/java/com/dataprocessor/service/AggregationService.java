package com.dataprocessor.service;

import com.dataprocessor.db.DatabaseConfig;
import com.dataprocessor.model.AggregationResult;
import com.dataprocessor.model.CanonicalEvent;
import com.dataprocessor.model.RawEvent;

import java.sql.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AggregationService {

    public AggregationResult getAggregatedData(String clientIdFilter, String metricFilter, String startDateStr, String endDateStr) {
        StringBuilder whereClause = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (clientIdFilter != null && !clientIdFilter.trim().isEmpty()) {
            whereClause.append(" AND LOWER(client_id) = LOWER(?) ");
            params.add(clientIdFilter.trim());
        }

        if (metricFilter != null && !metricFilter.trim().isEmpty()) {
            whereClause.append(" AND LOWER(metric) = LOWER(?) ");
            params.add(metricFilter.trim());
        }

        if (startDateStr != null && !startDateStr.trim().isEmpty()) {
            try {
                OffsetDateTime start = OffsetDateTime.parse(startDateStr.trim());
                whereClause.append(" AND timestamp >= ? ");
                params.add(start);
            } catch (Exception ignored) {}
        }

        if (endDateStr != null && !endDateStr.trim().isEmpty()) {
            try {
                OffsetDateTime end = OffsetDateTime.parse(endDateStr.trim());
                whereClause.append(" AND timestamp <= ? ");
                params.add(end);
            } catch (Exception ignored) {}
        }

        long totalEvents = 0;
        double totalAmount = 0.0;
        Map<String, AggregationResult.MetricSummary> metricsMap = new HashMap<>();
        Map<String, AggregationResult.ClientSummary> clientMap = new HashMap<>();

        try (Connection conn = DatabaseConfig.getConnection()) {
            // 1. Overall Aggregates
            String overallSql = "SELECT COUNT(*), COALESCE(SUM(amount), 0.0) FROM canonical_events " + whereClause;
            try (PreparedStatement pstmt = conn.prepareStatement(overallSql)) {
                setParameters(pstmt, params);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        totalEvents = rs.getLong(1);
                        totalAmount = rs.getDouble(2);
                    }
                }
            }

            // 2. Metrics Breakdown
            String metricSql = "SELECT metric, COUNT(*), SUM(amount) FROM canonical_events " + whereClause + " GROUP BY metric";
            try (PreparedStatement pstmt = conn.prepareStatement(metricSql)) {
                setParameters(pstmt, params);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        String m = rs.getString(1);
                        long c = rs.getLong(2);
                        double s = rs.getDouble(3);
                        metricsMap.put(m, new AggregationResult.MetricSummary(c, s));
                    }
                }
            }

            // 3. Client Breakdown
            String clientSql = "SELECT client_id, COUNT(*), SUM(amount) FROM canonical_events " + whereClause + " GROUP BY client_id";
            try (PreparedStatement pstmt = conn.prepareStatement(clientSql)) {
                setParameters(pstmt, params);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        String cl = rs.getString(1);
                        long c = rs.getLong(2);
                        double s = rs.getDouble(3);
                        clientMap.put(cl, new AggregationResult.ClientSummary(c, s));
                    }
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error executing aggregation query", e);
        }

        double avgAmount = totalEvents > 0 ? totalAmount / totalEvents : 0.0;
        return new AggregationResult(totalEvents, totalAmount, avgAmount, metricsMap, clientMap);
    }

    public List<CanonicalEvent> getProcessedEvents() {
        List<CanonicalEvent> list = new ArrayList<>();
        String sql = "SELECT id, raw_event_id, client_id, metric, amount, timestamp, event_fingerprint FROM canonical_events ORDER BY id DESC LIMIT 100";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                CanonicalEvent ce = new CanonicalEvent(
                        rs.getString("client_id"),
                        rs.getString("metric"),
                        rs.getDouble("amount"),
                        rs.getObject("timestamp", OffsetDateTime.class),
                        rs.getString("event_fingerprint")
                );
                ce.setId(rs.getLong("id"));
                ce.setRawEventId(rs.getLong("raw_event_id"));
                list.add(ce);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<RawEvent> getFailedOrRejectedEvents() {
        List<RawEvent> list = new ArrayList<>();
        String sql = "SELECT id, source_client, raw_payload, status, error_reason, received_at FROM raw_events WHERE status IN ('REJECTED', 'FAILED_SIMULATED', 'DUPLICATE_IGNORED') ORDER BY id DESC LIMIT 100";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                RawEvent re = new RawEvent(
                        rs.getString("source_client"),
                        rs.getString("raw_payload"),
                        rs.getString("status"),
                        rs.getString("error_reason")
                );
                re.setId(rs.getLong("id"));
                Timestamp ts = rs.getTimestamp("received_at");
                if (ts != null) re.setReceivedAt(ts.toLocalDateTime());
                list.add(re);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    private void setParameters(PreparedStatement pstmt, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object p = params.get(i);
            if (p instanceof OffsetDateTime odt) {
                pstmt.setObject(i + 1, odt);
            } else {
                pstmt.setObject(i + 1, p);
            }
        }
    }
}
