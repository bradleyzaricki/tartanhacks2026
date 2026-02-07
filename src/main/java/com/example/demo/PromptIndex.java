package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class PromptIndex {
    private static final Dotenv dotenv = Dotenv.load();
    private static final String pineconeApi = dotenv.get("PINECONE_API");
    private static final String pineconehostApi = dotenv.get("PINECONE_HOST");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static final String PINECONE_API_KEY = pineconeApi;


    private static final String PINECONE_HOST = pineconehostApi;

    private static final String NAMESPACE = "dev";

    public void upsert(String id, float[] values, Map<String, Object> metadata) throws Exception {
        Map<String, Object> vector = new LinkedHashMap<>();
        vector.put("id", id);
        vector.put("values", values);
        if (metadata != null && !metadata.isEmpty()) {
            vector.put("metadata", metadata);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vectors", List.of(vector));
        body.put("namespace", NAMESPACE);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(trim(PINECONE_HOST) + "/vectors/upsert"))
                .header("Api-Key", PINECONE_API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new RuntimeException("Upsert failed: " + res.body());
        }
    }

    public PromptMatch query(float[] vector) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", vector);
        body.put("topK", 1);
        body.put("includeMetadata", true);
        body.put("namespace", NAMESPACE);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(trim(PINECONE_HOST) + "/query"))
                .header("Api-Key", PINECONE_API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new RuntimeException("Query failed: " + res.body());
        }

        JsonNode matches = MAPPER.readTree(res.body()).path("matches");
        if (!matches.isArray() || matches.isEmpty()) return null;

        JsonNode m = matches.get(0);
        double score = m.path("score").asDouble();

        if (score >= 0.75) {
            return new PromptMatch(
                    m.path("id").asText(),
                    score,
                    m.path("metadata")
            );
        }

        return null;
    }

    public record PromptMatch(String id, double score, JsonNode metadata) {}

    private static String trim(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
