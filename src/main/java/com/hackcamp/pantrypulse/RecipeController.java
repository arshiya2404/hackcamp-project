package com.hackcamp.pantrypulse;

import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@RestController
public class RecipeController {
    private static final String PROMPT =
        "You are a helpful culinary assistant. Given the following list of available pantry ingredients, " +
        "generate 2 simple recipes using as many expiring items as possible, supplemented only by standard pantry staples " +
        "(salt, pepper, oil, flour, water, basic spices). " +
        "Return ONLY a valid JSON array of objects with keys: title, time, usedExpiringItems (array of strings), " +
        "ingredients (array of strings), steps (array of strings). No markdown formatting or extra text.";

    private final ObjectMapper mapper;
    private final RestClient client = RestClient.create();

    public RecipeController(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @PostMapping("/api/recipes")
    public List> generateRecipes(@RequestBody List<FoodItem> items) throws Exception {
        String key = System.getenv("ANTHROPIC_API_KEY");
        
        // Build inventory summary for prompt
        StringBuilder inventoryText = new StringBuilder("Current Pantry Items:\n");
        for (FoodItem item : items) {
            inventoryText.append("- ").append(item.getName())
                         .append(" (Expires in ").append(item.getDaysLeft()).append(" days)\n");
        }

        Map<String, Object> body = Map.of(
            "model", "claude-3-5-sonnet-20241022",
            "max_tokens", 1500,
            "messages", List.of(Map.of(
                "role", "user",
                "content", PROMPT + "\n\n" + inventoryText.toString()
            ))
        );

        Map<?, ?> response = client.post()
            .uri("https://api.anthropic.com/v1/messages")
            .header("x-api-key", key)
            .header("anthropic-version", "2023-06-01")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map.class);

        List<?> content = (List<?>) response.get("content");
        String text = (String) ((Map<?, ?>) content.get(0)).get("text");
        text = text.replace("```json", "").replace("```", "").trim();

        @SuppressWarnings("unchecked")
        List> recipes = mapper.readValue(text, List.class);
        return recipes;
    }
}