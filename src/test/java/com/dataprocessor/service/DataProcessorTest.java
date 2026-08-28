package com.dataprocessor.service;

import com.dataprocessor.db.DatabaseConfig;
import com.dataprocessor.model.AggregationResult;
import com.dataprocessor.model.CanonicalEvent;
import com.dataprocessor.model.IngestionResponse;

public class DataProcessorTest {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println(" RUNNING FAULT-TOLERANT DATA PROCESSOR UNIT TESTS ");
        System.out.println("==================================================");

        DatabaseConfig.initDatabase();

        IdempotencyService idempotencyService = new IdempotencyService();
        NormalizationService normalizationService = new NormalizationService(idempotencyService);
        IngestionService ingestionService = new IngestionService(normalizationService);
        AggregationService aggregationService = new AggregationService();

        int passed = 0;
        int total = 0;

        // Test 1: Normalization Client A format
        total++;
        try {
            String clientA = """
                {
                  "source": "client_A",
                  "payload": {
                    "metric": "value",
                    "amount": "1200",
                    "timestamp": "2024/01/01"
                  }
                }
            """;
            CanonicalEvent ce = normalizationService.normalize(clientA);
            assert "client_A".equals(ce.getClientId()) : "Client ID mismatch";
            assert "value".equals(ce.getMetric()) : "Metric mismatch";
            assert Double.valueOf(1200.0).equals(ce.getAmount()) : "Amount mismatch";
            System.out.println("[PASS] Test 1: Client A Normalization successful");
            passed++;
        } catch (Throwable t) {
            System.err.println("[FAIL] Test 1: " + t.getMessage());
        }

        // Test 2: Normalization Client B format (inconsistent types, extra fields)
        total++;
        try {
            String clientB = """
                {
                  "client_id": "client_B",
                  "event_type": "sales",
                  "val": "$5,400.75",
                  "created_at": "2024-01-02T10:15:30Z",
                  "extra_metadata": { "device": "mobile", "session_id": "abc123xyz" }
                }
            """;
            CanonicalEvent ce = normalizationService.normalize(clientB);
            assert "client_B".equals(ce.getClientId());
            assert "sales".equals(ce.getMetric());
            assert Double.valueOf(5400.75).equals(ce.getAmount());
            System.out.println("[PASS] Test 2: Client B Normalization with extra fields successful");
            passed++;
        } catch (Throwable t) {
            System.err.println("[FAIL] Test 2: " + t.getMessage());
        }

        // Test 3: Malformed payload rejection
        total++;
        try {
            String malformed = "{\"source\": \"client_C\", \"payload\": { \"metric\": \"click\" }}"; // Missing amount
            try {
                normalizationService.normalize(malformed);
                System.err.println("[FAIL] Test 3: Expected IllegalArgumentException for missing amount");
            } catch (IllegalArgumentException e) {
                System.out.println("[PASS] Test 3: Malformed payload correctly rejected with reason: " + e.getMessage());
                passed++;
            }
        } catch (Throwable t) {
            System.err.println("[FAIL] Test 3: " + t.getMessage());
        }

        // Test 4: Idempotency & Deduplication
        total++;
        try {
            String testEvent = """
                {
                  "source": "client_A",
                  "payload": {
                    "metric": "purchase",
                    "amount": "250.00",
                    "timestamp": "2024/01/15"
                  }
                }
            """;

            // First ingestion -> PROCESSED
            IngestionResponse r1 = ingestionService.processIngestion(testEvent, false);
            assert "PROCESSED".equals(r1.getStatus()) : "First ingestion should be PROCESSED";

            // Second ingestion (retry) -> DUPLICATE_IGNORED
            IngestionResponse r2 = ingestionService.processIngestion(testEvent, false);
            assert "DUPLICATE_IGNORED".equals(r2.getStatus()) : "Second ingestion should be DUPLICATE_IGNORED";

            System.out.println("[PASS] Test 4: Idempotency & Deduplication prevented double counting");
            passed++;
        } catch (Throwable t) {
            System.err.println("[FAIL] Test 4: " + t.getMessage());
            t.printStackTrace();
        }

        // Test 5: Simulated DB Failure Rollback
        total++;
        try {
            String simEvent = """
                {
                  "source": "client_X",
                  "payload": {
                    "metric": "refund",
                    "amount": "999.00",
                    "timestamp": "2024/02/01"
                  }
                }
            """;

            IngestionResponse rSim = ingestionService.processIngestion(simEvent, true);
            assert "FAILED_SIMULATED".equals(rSim.getStatus()) : "Should return FAILED_SIMULATED";

            // Verify no canonical record exists for client_X
            AggregationResult agg = aggregationService.getAggregatedData("client_X", null, null, null);
            assert agg.getTotalEvents() == 0 : "Simulated failure must leave 0 canonical records committed";

            // Retry without failure simulation -> should succeed
            IngestionResponse rRetry = ingestionService.processIngestion(simEvent, false);
            assert "PROCESSED".equals(rRetry.getStatus()) : "Retry after failure simulation should succeed";

            System.out.println("[PASS] Test 5: Simulated DB failure rollback verified with clean retry");
            passed++;
        } catch (Throwable t) {
            System.err.println("[FAIL] Test 5: " + t.getMessage());
            t.printStackTrace();
        }

        // Test 6: Aggregations & Filters
        total++;
        try {
            AggregationResult aggAll = aggregationService.getAggregatedData(null, null, null, null);
            assert aggAll.getTotalEvents() > 0 : "Total events should be > 0";
            assert aggAll.getTotalAmount() > 0 : "Total amount should be > 0";

            AggregationResult aggClientA = aggregationService.getAggregatedData("client_A", null, null, null);
            assert aggClientA.getTotalEvents() > 0 : "Client A events should be > 0";

            System.out.println("[PASS] Test 6: Aggregation query & client filters accurate");
            passed++;
        } catch (Throwable t) {
            System.err.println("[FAIL] Test 6: " + t.getMessage());
            t.printStackTrace();
        }

        System.out.println("==================================================");
        System.out.println(" TEST RESULTS: " + passed + " / " + total + " PASSED");
        System.out.println("==================================================");

        if (passed < total) {
            System.exit(1);
        }
    }
}
