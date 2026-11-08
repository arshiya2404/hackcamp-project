package com.hackcamp.pantrypulse;

import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PantryController {
    private final ShelfLifeService shelfLife;

    public PantryController(ShelfLifeService shelfLife) {
        this.shelfLife = shelfLife;
    }

    @GetMapping("/api/pantry/sample")
    public List<FoodItem> sample() {
        LocalDate today = LocalDate.now();
        return List.of(
                shelfLife.createItem("Spinach", today.minusDays(4)),
                shelfLife.createItem("Milk", today.minusDays(1)),
                shelfLife.createItem("Eggs", today),
                shelfLife.createItem("Dragonfruit", today));
    }
}