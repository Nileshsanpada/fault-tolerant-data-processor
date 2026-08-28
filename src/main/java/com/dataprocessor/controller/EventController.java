package com.dataprocessor.controller;

import com.dataprocessor.model.AggregationResult;
import com.dataprocessor.model.IngestionResponse;
import com.dataprocessor.service.AggregationService;
import com.dataprocessor.service.IngestionService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

public class EventController {

    private final IngestionService ingestionService;
    private final AggregationService aggregationService;
    private final Gson gson;
    private HttpServer server;

    public EventController(IngestionService ingestionService, AggregationService aggregationService) {
        this.ingestionService = ingestionService;
        this.aggregationService = aggregationService;
        this.gson = new GsonBuilder()
                .registerTypeAdapter(OffsetDateTime.class, (com.google.gson.JsonSerializer<OffsetDateTime>) 
                        (src, typeOfSrc, context) -> new com.google.gson.JsonPrimitive(src.toString()))
                .setPrettyPrinting()
                .create();
    }

    public void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/api/events/ingest", new IngestHandler());
        server.createContext("/api/events/aggregations", new AggregationHandler());
        server.createContext("/api/events/processed", new ProcessedHandler());
        server.createContext("/api/events/failed", new FailedHandler());

        // Serve Frontend UI files if present
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
        server.start();
        System.out.println("Fault-Tolerant Data Processor Java Backend started on http://localhost:" + port);
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    private class IngestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, Map.of("error", "Method Not Allowed"));
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());

            String simHeader = exchange.getRequestHeaders().getFirst("X-Simulate-Failure");
            boolean simulateFailure = "true".equalsIgnoreCase(simHeader) 
                    || "true".equalsIgnoreCase(queryParams.get("simulateFailure"));

            IngestionResponse response = ingestionService.processIngestion(body, simulateFailure);

            int statusCode = 200;
            if (!response.isSuccess()) {
                if ("REJECTED".equals(response.getStatus())) {
                    statusCode = 400;
                } else {
                    statusCode = 500;
                }
            }

            sendJsonResponse(exchange, statusCode, response);
        }
    }

    private class AggregationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, Map.of("error", "Method Not Allowed"));
                return;
            }

            Map<String, String> q = parseQueryParams(exchange.getRequestURI().getQuery());
            AggregationResult result = aggregationService.getAggregatedData(
                    q.get("client_id"),
                    q.get("metric"),
                    q.get("start_date"),
                    q.get("end_date")
            );

            sendJsonResponse(exchange, 200, result);
        }
    }

    private class ProcessedHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            sendJsonResponse(exchange, 200, aggregationService.getProcessedEvents());
        }
    }

    private class FailedHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            sendJsonResponse(exchange, 200, aggregationService.getFailedOrRejectedEvents());
        }
    }

    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path) || path.isEmpty()) {
                path = "/index.html";
            }

            File file = new File("static" + path);
            if (!file.exists() || file.isDirectory()) {
                file = new File("frontend/dist" + path);
            }

            if (!file.exists()) {
                sendJsonResponse(exchange, 404, Map.of("error", "Resource not found"));
                return;
            }

            String contentType = "text/html";
            if (path.endsWith(".js")) contentType = "application/javascript";
            else if (path.endsWith(".css")) contentType = "text/css";
            else if (path.endsWith(".json")) contentType = "application/json";

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, file.length());

            try (OutputStream os = exchange.getResponseBody();
                 FileInputStream fis = new FileInputStream(file)) {
                fis.transferTo(os);
            }
        }
    }

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Simulate-Failure");
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        byte[] bytes = gson.toJson(data).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length > 1) {
                map.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            } else if (pair.length == 1) {
                map.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), "");
            }
        }
        return map;
    }
}
