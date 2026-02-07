package com.example.demo;

import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import io.github.cdimascio.dotenv.Dotenv;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

public class MongoCacheStore {
    private static final Dotenv dotenv = Dotenv.load();
    private static final String MONGO_URI = dotenv.get("MONGO_URI");

    private static final String DB_NAME = "promptcache";
    private static final String PROMPTS = "prompts";
    private static final String OUTPUTS = "outputs";

    private final MongoCollection<Document> prompts;
    private final MongoCollection<Document> outputs;

    public MongoCacheStore() {
        MongoClient client = MongoClients.create(MONGO_URI);
        MongoDatabase db = client.getDatabase(DB_NAME);

        this.prompts = db.getCollection(PROMPTS);
        this.outputs = db.getCollection(OUTPUTS);

        // ensure indexes (safe to call multiple times)
        prompts.createIndex(Indexes.ascending("outputId"));
    }

    /**
     * Save prompt → output (many prompts can map to same output)
     * @return promptId (use this as Pinecone vector id)
     */
    public String savePromptAndOutput(String rawPrompt, String canonicalPrompt, String outputText, int tokens) {
        long now = System.currentTimeMillis();

        int tokensUsed = tokens;

        String outputHash = sha256(outputText);

        Document output = outputs.find(Filters.eq("_id", outputHash)).first();
        if (output == null) {
            output = new Document("_id", outputHash)
                    .append("text", outputText)
                    .append("tokensUsed", tokensUsed)
                    .append("createdAt", now);
            outputs.insertOne(output);
        }

        String promptId = "prompt-" + UUID.randomUUID();
        Document prompt = new Document("_id", promptId)
                .append("rawPrompt", rawPrompt)
                .append("canonicalPrompt", canonicalPrompt)
                .append("outputId", outputHash)
                .append("tokensUsed", tokensUsed)
                .append("createdAt", now);

        prompts.insertOne(prompt);

        return promptId;
    }

    public Optional<String> getPromptForPromptId(String promptId) {
        Document prompt = prompts.find(Filters.eq("_id", promptId)).first();
        if (prompt == null) return Optional.empty();

        return Optional.ofNullable(prompt.getString("rawPrompt"));
    }


    /** Given Pinecone match id → return cached output text */
    public Optional<String> getOutputForPromptId(String promptId) {
        Document prompt = prompts.find(Filters.eq("_id", promptId)).first();
        if (prompt == null) return Optional.empty();

        String outputId = prompt.getString("outputId");
        Document output = outputs.find(Filters.eq("_id", outputId)).first();
        if (output == null) return Optional.empty();

        return Optional.ofNullable(output.getString("text"));
    }
    public Optional<Integer> getTokensUsedForPromptId(String promptId) {
        Document prompt = prompts.find(Filters.eq("_id", promptId)).first();
        if (prompt == null) return Optional.empty();

        String outputId = prompt.getString("outputId");
        if (outputId == null) return Optional.empty();

        Document output = outputs.find(Filters.eq("_id", outputId)).first();
        if (output == null) return Optional.empty();

        Integer tokensUsed = output.getInteger("tokensUsed");
        return Optional.ofNullable(tokensUsed);
    }


    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
