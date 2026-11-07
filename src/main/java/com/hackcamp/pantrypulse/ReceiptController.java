package com.hackcamp.pantrypulse;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

@RestController
public class ReceiptController {
    private static final String PROMPT =
        "This is a grocery receipt. List only the food items that were bought, " +
        "as short plain names (for example: spinach, milk, eggs), expanding any " +
        "abbreviations. Return ONLY a JSON array of strings with no other text.";

    private final ShelfLifeService shelfLife;
    private final ObjectMapper mapper;
    private final RestClient client = RestClient.create();

    public ReceiptController(ShelfLifeService shelfLife, ObjectMapper mapper) {
        this.shelfLife = shelfLife;
        this.mapper = mapper;
    }

    @PostMapping("/api/receipt")
    public List<FoodItem> scan(@RequestParam("image") MultipartFile image) throws Exception {
        String key = System.getenv("ANTHROPIC_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("ANTHROPIC_API_KEY not set");
        String b64 = Base64.getEncoder().encodeToString(image.getBytes());
        String mediaType = image.getContentType() != null ? image.getContentType() : "image/jpeg";

        Map<String, Object> body = Map.of(
            "model", "claude-sonnet-5-5",
            "max_tokens", 1000,
            "messages", List.of(Map.of(
                "role", "user",
                "content", List.of(
                    Map.of("type", "image",
                           "source", Map.of("type", "base64", "media_type", mediaType, "data", b64)),
                    Map.of("type", "text", "text", PROMPT)
                )
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

        String[] names = mapper.readValue(text, String[].class);
        List<FoodItem> items = new ArrayList<>();
        for (String name : names) {
            items.add(shelfLife.createItem(name, LocalDate.now()));
        }
        return items;
    }
}
