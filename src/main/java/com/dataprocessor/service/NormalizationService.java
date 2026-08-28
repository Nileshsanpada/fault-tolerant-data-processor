package com.dataprocessor.service;

import com.dataprocessor.model.CanonicalEvent;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class NormalizationService {

    private final IdempotencyService idempotencyService;

    public NormalizationService(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    public CanonicalEvent normalize(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty or null JSON payload");
        }

        JsonObject root;
        try {
            root = JsonParser.parseString(rawJson).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed JSON payload: " + e.getMessage());
        }

        // Extract client ID
        String clientId = getFirstString(root, List.of("source", "client_id", "clientId", "client"));
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing client identifier");
        }

        JsonObject payload = root.has("payload") && root.get("payload").isJsonObject()
                ? root.getAsJsonObject("payload")
                : root;

        // Extract metric name
        String metric = getFirstString(payload, List.of("metric", "metric_name", "event_type", "type", "name"));
        if (metric == null || metric.trim().isEmpty()) {
            metric = getFirstString(root, List.of("metric", "metric_name", "event_type", "type", "name"));
        }
        if (metric == null || metric.trim().isEmpty()) {
            metric = "unknown_metric";
        }

        // Extract amount
        Double amount = getFirstDouble(payload, List.of("amount", "val", "value", "total", "count", "qty"));
        if (amount == null) {
            amount = getFirstDouble(root, List.of("amount", "val", "value", "total", "count", "qty"));
        }
        if (amount == null) {
            throw new IllegalArgumentException("Missing or unparseable numeric amount field");
        }

        // Extract timestamp
        String rawTs = getFirstString(payload, List.of("timestamp", "time", "date", "created_at", "ts"));
        if (rawTs == null) {
            rawTs = getFirstString(root, List.of("timestamp", "time", "date", "created_at", "ts"));
        }

        OffsetDateTime timestamp = parseTimestamp(rawTs);

        // Generate SHA-256 fingerprint for deduplication
        String fingerprint = idempotencyService.generateFingerprint(clientId, metric, amount, timestamp);

        return new CanonicalEvent(clientId.trim(), metric.trim(), amount, timestamp, fingerprint);
    }

    private String getFirstString(JsonObject json, List<String> fields) {
        for (String field : fields) {
            if (json.has(field) && !json.get(field).isJsonNull()) {
                JsonElement elem = json.get(field);
                if (elem.isJsonPrimitive()) {
                    return elem.getAsString().trim();
                }
            }
        }
        return null;
    }

    private Double getFirstDouble(JsonObject json, List<String> fields) {
        for (String field : fields) {
            if (json.has(field) && !json.get(field).isJsonNull()) {
                JsonElement elem = json.get(field);
                if (elem.isJsonPrimitive()) {
                    try {
                        if (elem.getAsJsonPrimitive().isNumber()) {
                            return elem.getAsDouble();
                        }
                        String str = elem.getAsString().replaceAll("[^0-9.-]", "");
                        if (!str.isEmpty()) {
                            return Double.parseDouble(str);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return null;
    }

    public OffsetDateTime parseTimestamp(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        String clean = raw.trim();

        // Unix Epoch check
        try {
            long epoch = Long.parseLong(clean);
            if (epoch > 1_000_000_000_000L) {
                return Instant.ofEpochMilli(epoch).atOffset(ZoneOffset.UTC);
            } else if (epoch > 1_000_000_000L) {
                return Instant.ofEpochSecond(epoch).atOffset(ZoneOffset.UTC);
            }
        } catch (NumberFormatException ignored) {}

        // Standard ISO-8601
        try {
            return OffsetDateTime.parse(clean);
        } catch (Exception ignored) {}

        // Common formats (slash, dash)
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd")
        );

        for (DateTimeFormatter dtf : formatters) {
            try {
                if (clean.contains(" ")) {
                    return LocalDateTime.parse(clean, dtf).atOffset(ZoneOffset.UTC);
                } else {
                    return LocalDate.parse(clean, dtf).atStartOfDay().atOffset(ZoneOffset.UTC);
                }
            } catch (Exception ignored) {}
        }

        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
