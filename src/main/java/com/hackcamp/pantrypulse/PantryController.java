package com.hackcamp.pantrypulse;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PantryController {
    private final ShelfLifeService shelfLife;

    public PantryController(ShelfLifeService shelfLife) {
        this.shelfLife = shelfLife;
    }

    public record StoredItem(
            String name,
            LocalDate purchaseDate) {
    }

    @PostMapping("/api/pantry/recheck")
    public List<FoodItem> recheck(
            @RequestBody List<StoredItem> items) {

        if (items == null || items.size() > 300) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid pantry: maximum 300 items.");
        }

        for (StoredItem item : items) {
            if (item == null
                    || item.name() == null
                    || item.name().isBlank()
                    || item.name().length() > 200
                    || item.purchaseDate() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Each item needs a name and purchase date.");
            }
        }

        return items.stream()
                .map(item -> shelfLife.createItem(
                        item.name(),
                        item.purchaseDate()))
                .toList();
    }
}