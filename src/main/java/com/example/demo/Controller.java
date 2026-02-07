package com.example.demo;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class Controller {

    private static final Dotenv dotenv = Dotenv.load();
    private static final String openAIapi = dotenv.get("OPENAI_API");
    private static final String Dedalusapi = dotenv.get("DEDALUS_API");
    @PostMapping("/api/receiveMessage")
    public Map<String, Object> receiveMessage(@RequestBody Map<String, Object> body) {
        try {
            String prompt = String.valueOf(body.getOrDefault("message", ""));
            boolean force = Boolean.TRUE.equals(body.get("force"));

            PromptEmbedder promptEmbed = new PromptEmbedder();
            PromptIndex idx = new PromptIndex();
            MongoCacheStore mongo = new MongoCacheStore();

            String promptNormalized = promptEmbed.canonicalize(prompt);

            float[] vector = promptEmbed.embed(openAIapi, promptNormalized);

            boolean recycled = false;
            String out;
            String promptId;
            int savedTokens = 0;

            if (force) {
                Dedalus dedalus = new Dedalus(Dedalusapi);
                DedalusResult result = dedalus.generateDedalusResponse(prompt);
                out = result.text();
                promptId = mongo.savePromptAndOutput(prompt, promptNormalized, out, result.totalTokens());
            } else {
                PromptIndex.PromptMatch match = idx.query(vector);

                if (match != null && match.score() >= 0.7) {
                    String cached = mongo.getOutputForPromptId(match.id()).orElse(null);
                    String storedPrompt = mongo.getPromptForPromptId(match.id()).orElse(null);

                    if (cached != null) {
                        out = cached;
                        recycled = true;
                        promptId = match.id();
                        if (storedPrompt != null) prompt = storedPrompt;
                        savedTokens = mongo.getTokensUsedForPromptId(promptId).orElse(0);
                    } else {
                        Dedalus dedalus = new Dedalus(Dedalusapi);
                        DedalusResult result = dedalus.generateDedalusResponse(prompt);
                        out = result.text();
                        promptId = mongo.savePromptAndOutput(prompt, promptNormalized, out, result.totalTokens());
                    }
                } else {
                    Dedalus dedalus = new Dedalus(Dedalusapi);
                    DedalusResult result = dedalus.generateDedalusResponse(prompt);
                    out = result.text();
                    promptId = mongo.savePromptAndOutput(prompt, promptNormalized, out, result.totalTokens());
                }
            }

            idx.upsert(promptId, vector, Map.of("raw_prompt", prompt));

            return Map.of(
                    "prompt", prompt,
                    "output", out,
                    "recycled", recycled,
                    "tokensSaved", savedTokens
            );
        } catch (Exception e) {
            return Map.of(
                    "prompt", String.valueOf(body.getOrDefault("message", "")),
                    "output", "Failed: " + e.getMessage(),
                    "recycled", false
            );
        }
    }

}
