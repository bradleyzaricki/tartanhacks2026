package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class PromptEmbedder {
    static IKeywordIdentifier keywordIdentifier = new KeywordIdentifierV1();
    static ObjectMapper mapper = new ObjectMapper();
    static HttpClient client = HttpClient.newHttpClient();
    static final double COS_ACCEPT = 0.9;
    static final double COS_REJECT = 0.8;
    static final double JACCARD_ACCEPT = 0.6;


    public static void main(String[] args) throws Exception {
        String apiKey =

        Scanner scan = new Scanner(System.in);
        System.out.println("Enter Input Prompt 1\n");
        String a = scan.nextLine();
        System.out.println("Enter Input Prompt 2\n");

        String b = scan.nextLine();

        String ca = canonicalize(a);
        String cb = canonicalize(b);

        float[] va = embed(apiKey, ca);
        float[] vb = embed(apiKey, cb);
        System.out.println(va);
        report(a, b, ca, cb, va, vb);

        PromptIndex idx = new PromptIndex();

        String idA = "prompt-" + UUID.randomUUID();
        String idB = "prompt-" + UUID.randomUUID();

        idx.upsert(idA, va, Map.of("raw_prompt", a));
        idx.upsert(idB, vb, Map.of("raw_prompt", b));

    }

    /**
     * @param xRaw raw prompt string a
     * @param yRaw raw prompt string b
     * @param xCan canonicalized string a (json format)
     * @param yCan canonicalized string b (json format)
     * @param vx embedded vector for prompt string a
     * @param vy embedded vector for prompt string b
     */
    static void report(String xRaw, String yRaw, String xCan, String yCan, float[] vx, float[] vy) {
        double cos = cosine(vx, vy);
        double jac = keywordIdentifier.jaccard(keywordIdentifier.contentWords(xCan), keywordIdentifier.contentWords(yCan));
        boolean same = isRepeat(cos, jac);

        System.out.println();
        System.out.println("X: " + xRaw + xCan);
        System.out.println("Y: " + yRaw + yCan);

        System.out.println("cosine=" + cos);
        System.out.println("jaccard=" + jac);
        System.out.println("repeat=" + same);
    }

    /**
     * Returns whether or not the prompt is deemed a repeat based on cosine and jaccard scores
     * @param cosine cosine score (0-100.0)
     * @param jaccard jaccard score (0-100.0)
     * @return true if prompt is seen as a repeat
     */
    static boolean isRepeat(double cosine, double jaccard) {
        if (cosine >= COS_ACCEPT) return true;
        if (cosine <= COS_REJECT) return false;
        return jaccard >= JACCARD_ACCEPT;
    }

    /**
     * The canonicalize function creates a formatted version of the users request prompt.
     * This will aid the vector embedder model to create more accurate and similar vectors for similar prompts.
     * @param raw - The raw prompt string provided by the user
     * @return returns formatted json data into canonicalized form
     */
    static String canonicalize(String raw) {
        String s = raw;
        s = keywordIdentifier.normalizeIntent(s);

        String task = keywordIdentifier.detectTask(s);
        String domain = keywordIdentifier.detectDomain(s);
        String output = keywordIdentifier.detectOutputFormat(s);

        Set<String> kw = keywordIdentifier.topKeywords(s, 10);
        List<String> kwList = new ArrayList<>(kw);
        Collections.sort(kwList);

        List<String> constraints = new ArrayList<>();
        if (s.contains("fast") || s.contains("quick") || s.contains("asap")) constraints.add("fast");
        if (s.contains("short") || s.contains("brief")) constraints.add("short");
        if (s.contains("step by step") || s.contains("step-by-step")) constraints.add("steps");
        if (s.contains("json")) constraints.add("json");
        if (s.contains("table")) constraints.add("table");
        if (s.contains("code")) constraints.add("code");
        Collections.sort(constraints);


        return ""
                + "task: " + task + "\n"
                + "domain: " + domain + "\n"
                + "output: " + output + "\n"
                + "constraints: " + String.join(", ", constraints) + "\n"
                + "keywords: " + String.join(", ", kwList);
    }

    /**
     * Embed the raw prompt string into a vector direction using openAI
     * @param apiKey OpenAI api key
     * @param text raw prompt string
     * @return returns the vector direction
     * @throws Exception
     */
    static float[] embed(String apiKey, String text) throws Exception {
        String body = """
                {
                  "model": "text-embedding-3-large",
                  "input": %s
                }
                """.formatted(mapper.writeValueAsString(text));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/embeddings"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode arr = mapper.readTree(response.body()).get("data").get(0).get("embedding");

        float[] vec = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) vec[i] = (float) arr.get(i).asDouble();
        return vec;
    }

    /**
     * Cosine function to get similarity between two vector directions representing prompts
     * @param a the first vector direction
     * @param b the second vector direction
     * @return the cosine similarity value (0-100.0)
     */
    static double cosine(float[] a, float[] b)
    {
        double dot = 0, na = 0, nb = 0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }



}
