package com.hackcamp.pantrypulse;

import java.util.List;

public record ShelfLifeEntry(
        String name,
        String storage,
        int days,
        String tip,
        List<String> aliases) {
}