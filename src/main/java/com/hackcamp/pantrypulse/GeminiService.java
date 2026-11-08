package com.hackcamp.pantrypulse;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class GeminiService {

    private final RestClient client;
    private final ObjectMapper mapper;

    public GeminiService(ObjectMapper mapper) {
        this.mapper = mapper;

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);

        factory.setReadTimeout(Duration.ofSeconds(60));

        this.client = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    public String generate(
            List<Map<String, Object>> parts) throws Exception {

        return generate(parts, 2048);
    }

    public String generate(
            List<Map<String, Object>> parts,
            int maxOutputTokens) throws Exception {

        if (maxOutputTokens < 1 || maxOutputTokens > 8192) {
            throw new IllegalArgumentException(
                    "Invalid output token limit");
        }
        String key = System.getenv("GEMINI_API_KEY");

        if (key == null || key.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY not set");
        }

        String model = System.getenv("GEMINI_MODEL");

        if (model == null || model.isBlank()) {
            model = "gemini-3.5-flash-lite";
        }

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", parts)),
                "generationConfig", Map.of(
                        "maxOutputTokens", maxOutputTokens,
                        "responseMimeType", "application/json"));

        System.out.println("Calling Gemini model: " + model);
        long started = System.nanoTime();

        String response = client.post()
                .uri(
                        "https://generativelanguage.googleapis.com/v1beta/models/"
                                + model + ":generateContent")
                .header("x-goog-api-key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        System.out.println(
                "Gemini responded in "
                        + Duration.ofNanos(System.nanoTime() - started).toMillis()
                        + " ms");

        if (response == null || response.isBlank()) {
            throw new IllegalStateException("Gemini returned an empty response");
        }

        JsonNode root = mapper.readTree(response);
        JsonNode candidates = root.path("candidates");

        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new IllegalStateException(
                    "The model returned no candidate response.");
        }

        JsonNode candidate = candidates.path(0);

        String finishReason = candidate.path("finishReason").asText();

        if (!"STOP".equals(finishReason)) {
            throw new IllegalStateException(
                    "Gemini did not complete the response: " + finishReason);
        }

        StringBuilder output = new StringBuilder();

        for (JsonNode part : candidate.path("content").path("parts")) {
            if (part.has("text") && !part.path("thought").asBoolean(false)) {
                output.append(part.path("text").asText());
            }
        }

        String text = output.toString().trim();

        // Remove an outer Markdown code fence if Gemini includes one.
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");

            if (firstNewline >= 0 && lastFence > firstNewline) {
                text = text.substring(firstNewline + 1, lastFence).trim();
            }
        }

        if (text.isBlank()) {
            throw new IllegalStateException("Gemini returned no text");
        }

        return text;
    }
}