package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Dedalus {

    private final String apiKey;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public Dedalus(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) throw new RuntimeException("DEDALUS_API_KEY not set");
        this.apiKey = apiKey;
    }

    /**
     *
     * @param prompt the prompt the user passes through
     * @return returns a custom record containing both "text" llm output and "totalTokens" prompt tokens used
     * @throws Exception
     */
    public DedalusResult generateDedalusResponse(String prompt) throws Exception {
        String body = """
        {
          "model": "openai/gpt-4.1",
          "messages": [
            { "role": "user", "content": "%s" }
          ]
        }
        """.formatted(prompt.replace("\\", "\\\\").replace("\"", "\\\""));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.dedaluslabs.ai/v1/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode root = mapper.readTree(response.body());
        JsonNode usage = root.path("usage");

        String output = root.path("choices").path(0).path("message").path("content").asText();
        int totalTokens = usage.path("total_tokens").asInt();

        return new DedalusResult(output, totalTokens);
    }
}

record DedalusResult(String text, int totalTokens) {}
