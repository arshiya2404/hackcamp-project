package com.hackcamp.pantrypulse;

import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ShelfLifeService {
    private final Map<String, ShelfLifeEntry> table = new HashMap<>();

    public ShelfLifeService(ObjectMapper mapper) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/shelf-life.json")) {
            ShelfLifeEntry[] entries = mapper.readValue(in, ShelfLifeEntry[].class);
            for (ShelfLifeEntry e : entries) {
                table.put(e.name().toLowerCase(), e);
            }
        }
    }
    
    public FoodItem createItem(String name, LocalDate purchaseDate) {
        String query = name.toLowerCase().trim();
        ShelfLifeEntry e = table.get(query);
        
        if (e == null) {
            // Simple keyword matching fallback
            for (Map.Entry<String, ShelfLifeEntry> entry : table.entrySet()) {
                if (query.contains(entry.getKey())) {
                    e = entry.getValue();
                    break;
                }
            }
        }

        if (e == null) {
            return new FoodItem(name, purchaseDate, 5, "fridge",
                "General estimate. Check the item and any printed date.", true);
        }

        return new FoodItem(name, purchaseDate, e.days(), e.storage(), e.tip(), false);
    }
}