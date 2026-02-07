package com.example.demo;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class Controller {

    @GetMapping("/api/hello")
    public String hello() {
        return "hello from spring boot";
    }

    @PostMapping("/api/receiveMessage")
    public Map<String, Object> receiveMessage(@RequestBody Map<String, String> body) {
        try {
            String prompt = body.getOrDefault("message", "");

            PromptEmbedder promptEmbed = new PromptEmbedder();
            PromptIndex idx = new PromptIndex();
            MongoCacheStore mongo = new MongoCacheStore();

            String promptNormalized = promptEmbed.canonicalize(prompt);

            float[] vector = promptEmbed.embed(
                    "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",
                    promptNormalized
            );

            PromptIndex.PromptMatch match = idx.query(vector);

            boolean recycled = false;
            String out;
            String promptId;

            if (match != null && match.score() >= 0.8f) {
                String cached = mongo.getOutputForPromptId(match.id()).orElse(null);
                if (cached != null) {
                    out = cached;
                    recycled = true;
                    promptId = match.id();
                } else {
                    Dedalus dedalus = new Dedalus("dsk-test-874a4fde022d-c6a8731e899a179186d7088ee6c7757e");
                    DedalusResult result = dedalus.generateDedalusResponse(prompt);
                    out = result.text();
                    promptId = mongo.savePromptAndOutput(prompt, promptNormalized, out,result.totalTokens());
                }
            } else {
                Dedalus dedalus = new Dedalus("dsk-test-874a4fde022d-c6a8731e899a179186d7088ee6c7757e");
                DedalusResult result = dedalus.generateDedalusResponse(prompt);
                out = result.text();
                promptId = mongo.savePromptAndOutput(prompt, promptNormalized, out, result.totalTokens());
            }

            idx.upsert(promptId, vector, Map.of("raw_prompt", prompt));

            return Map.of(
                    "prompt", prompt,
                    "output", out,
                    "recycled", recycled
            );
        } catch (Exception e) {
            return Map.of(
                    "prompt", body.getOrDefault("message", ""),
                    "output", "Failed: " + e.getMessage(),
                    "recycled", false
            );
        }
    }
}
