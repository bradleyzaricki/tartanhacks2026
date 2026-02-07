package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class PromptIndex {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static final String PINECONE_API_KEY =

    private static final String PINECONE_HOST = "https://tartanhacks-f8kuz28.svc.aped-4627-b74a.pinecone.io";

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

    /** Query by vector */
    public List<PromptMatch> query(float[] vector, int topK) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", vector);
        body.put("topK", topK);
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
        List<PromptMatch> out = new ArrayList<>();

        for (JsonNode m : matches) {
            out.add(new PromptMatch(
                    m.path("id").asText(),
                    m.path("score").asDouble(),
                    m.path("metadata")
            ));
        }
        return out;
    }

    public record PromptMatch(String id, double score, JsonNode metadata) {}

    private static String trim(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
