package com.dataprocessor;

import com.dataprocessor.controller.EventController;
import com.dataprocessor.db.DatabaseConfig;
import com.dataprocessor.service.AggregationService;
import com.dataprocessor.service.IdempotencyService;
import com.dataprocessor.service.IngestionService;
import com.dataprocessor.service.NormalizationService;

public class App {

    public static void main(String[] args) {
        int port = 8080;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port argument, using default 8080");
            }
        }

        System.out.println("==================================================");
        System.out.println("   FAULT-TOLERANT DATA PROCESSING PIPELINE        ");
        System.out.println("==================================================");
        
        try {
            System.out.println("[INFO] Setting up database tables...");
            DatabaseConfig.initDatabase();

            IdempotencyService idempotencyService = new IdempotencyService();
            NormalizationService normalizationService = new NormalizationService(idempotencyService);
            IngestionService ingestionService = new IngestionService(normalizationService);
            AggregationService aggregationService = new AggregationService();

            EventController controller = new EventController(ingestionService, aggregationService);
            controller.start(port);

            System.out.println("[READY] Service listening at http://localhost:" + port);
            System.out.println("==================================================");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[SHUTDOWN] Stopping server gracefully...");
                controller.stop();
            }));

        } catch (Exception e) {
            System.err.println("[FATAL] App startup failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
