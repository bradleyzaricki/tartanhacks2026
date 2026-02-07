package com.example.demo;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class Controller {

    @GetMapping("/api/hello")
    public String hello() {
        return "hello from spring boot";
    }

    @PostMapping("/api/newPrompt")
    public Map<String, Object> receiveMessage(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        String prompt = message;


        //todo replace this with potential match
        boolean recycled = message.toLowerCase().contains("recycle");
        //if match
        String response = recycled
                ? "Recycled Prompt"
                : "New Prompt";

        return Map.of(
                "prompt", prompt,
                "response", response,
                "recycled", recycled
        );
    }

    @PostMapping("/api/generatePrompt")
    public Map<String, Object> generatePrompt(@RequestBody Map<String, String> body) {
        String prompt = body.get("message");



        return Map.of(
                "prompt", prompt,
                "response", "aaa",
                "recycled", false
        );
    }
}
