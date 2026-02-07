package com.example.demo;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class Controller {

    @GetMapping("/api/hello")
    public String hello() {
        return "hello from spring boot";
    }

    @PostMapping("/api/message")
    public Map<String, Object> receiveMessage(@RequestBody Map<String, String> body) {
        String message = body.get("message");

        //todo replace this with potential match
        boolean recycled = message.toLowerCase().contains("recycle");

        String response = recycled
                ? "Recycled Prompt"
                : "New Prompt";

        return Map.of(
                "response", response,
                "recycled", recycled
        );
    }
}
